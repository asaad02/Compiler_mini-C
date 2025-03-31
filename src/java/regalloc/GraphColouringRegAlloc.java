package regalloc;

import gen.asm.*;
import java.util.*;

public class GraphColouringRegAlloc implements AssemblyPass {

  public static final GraphColouringRegAlloc INSTANCE = new GraphColouringRegAlloc();

  /** The set of hardware registers we can assign. $t0–$t9 plus $s0–$s7 => 19 total. */
  private static final List<Register> ALLOCATABLE =
      List.of(
          Register.Arch.t0,
          Register.Arch.t1,
          Register.Arch.t2,
          Register.Arch.t3,
          Register.Arch.t4,
          Register.Arch.t5,
          Register.Arch.t6,
          Register.Arch.t7,
          Register.Arch.t8,
          Register.Arch.t9,
          Register.Arch.s0,
          Register.Arch.s1,
          Register.Arch.s2,
          Register.Arch.s3,
          Register.Arch.s4,
          Register.Arch.s5,
          Register.Arch.s6,
          Register.Arch.s7);

  private static int spillLabelCounter = 0;
  private final Map<Register.Virtual, Label> spillLabelMap = new HashMap<>();
  private final Set<Label> alreadyEmittedSpillLabels = new HashSet<>();

  private GraphColouringRegAlloc() {}

  @Override
  public AssemblyProgram apply(AssemblyProgram program) {
    AssemblyProgram outProg = new AssemblyProgram();

    for (AssemblyTextItem dataItem : program.dataSection.items) {
      outProg.dataSection.emit(dataItem);
    }

    for (AssemblyProgram.TextSection oldSection : program.textSections) {
      CFG cfg = buildCFG(oldSection);
      doLiveness(cfg);
      InterferenceGraph ig = buildInterferenceGraph(cfg);
      ColorResult colorResult = chaitinColor(ig, ALLOCATABLE);

      AssemblyProgram.TextSection newSection = rewriteSection(oldSection, cfg, colorResult);
      outProg.emitTextSection(newSection);
    }

    for (var entry : spillLabelMap.entrySet()) {
      Label lbl = entry.getValue();
      if (!alreadyEmittedSpillLabels.contains(lbl)) {
        outProg.dataSection.emit(new Directive("align 2"));
        outProg.dataSection.emit(lbl);
        outProg.dataSection.emit(new Directive("space 4"));
        alreadyEmittedSpillLabels.add(lbl);
      }
    }

    return outProg;
  }

  private static class CFGNode {
    Instruction insn;
    List<CFGNode> successors = new ArrayList<>();
    Set<Register.Virtual> liveIn = new HashSet<>();
    Set<Register.Virtual> liveOut = new HashSet<>();

    CFGNode(Instruction insn) {
      this.insn = insn;
    }
  }

  private static class CFG {
    List<CFGNode> nodes = new ArrayList<>();
    Map<Label, Integer> labelToIndex = new HashMap<>();
    Map<Instruction, CFGNode> insnNodeMap = new HashMap<>();
  }

  private CFG buildCFG(AssemblyProgram.TextSection section) {
    CFG cfg = new CFG();
    List<Instruction> insns = new ArrayList<>();

    // Identify where labels occur
    for (AssemblyItem item : section.items) {
      if (item instanceof Label lbl) {
        cfg.labelToIndex.put(lbl, insns.size());
      } else if (item instanceof Instruction i) {
        insns.add(i);
      }
    }

    // Make a node per instruction
    for (Instruction insn : insns) {
      CFGNode node = new CFGNode(insn);
      cfg.nodes.add(node);
      cfg.insnNodeMap.put(insn, node);
    }

    // Link successors
    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      Instruction insn = node.insn;
      boolean isUncond = false;

      switch (insn.opcode.kind()) {
        case JUMP -> {
          isUncond = true;
          if (insn instanceof Instruction.Jump j) {
            Integer target = cfg.labelToIndex.get(j.label);
            if (target != null) node.successors.add(cfg.nodes.get(target));
          }
        }
        case JUMP_REGISTER -> {
          isUncond = true;
        }
        case BINARY_BRANCH -> {
          if (insn instanceof Instruction.BinaryBranch bb) {
            Integer t = cfg.labelToIndex.get(bb.label);
            if (t != null) node.successors.add(cfg.nodes.get(t));
          }
        }
        case UNARY_BRANCH -> {
          if (insn instanceof Instruction.UnaryBranch ub) {
            Integer t = cfg.labelToIndex.get(ub.label);
            if (t != null) node.successors.add(cfg.nodes.get(t));
          }
        }
        default -> {}
      }

      // Fallthrough if not unconditional
      if (!isUncond && i + 1 < cfg.nodes.size()) {
        node.successors.add(cfg.nodes.get(i + 1));
      }
    }

    return cfg;
  }

  private void doLiveness(CFG cfg) {
    boolean changed;
    do {
      changed = false;
      for (int i = cfg.nodes.size() - 1; i >= 0; i--) {
        CFGNode node = cfg.nodes.get(i);
        Set<Register.Virtual> oldIn = new HashSet<>(node.liveIn);
        Set<Register.Virtual> oldOut = new HashSet<>(node.liveOut);

        Set<Register.Virtual> newOut = new HashSet<>();
        for (CFGNode succ : node.successors) {
          newOut.addAll(succ.liveIn);
        }
        node.liveOut = newOut;

        Set<Register.Virtual> newIn = new HashSet<>(uses(node.insn));
        Set<Register.Virtual> outMinusDef = new HashSet<>(node.liveOut);
        outMinusDef.removeAll(def(node.insn));
        newIn.addAll(outMinusDef);
        node.liveIn = newIn;

        if (!oldIn.equals(node.liveIn) || !oldOut.equals(node.liveOut)) {
          changed = true;
        }
      }
    } while (changed);
  }

  private Set<Register.Virtual> uses(Instruction insn) {
    Set<Register.Virtual> ret = new HashSet<>();
    for (Register r : insn.uses()) {
      if (r instanceof Register.Virtual vr) ret.add(vr);
    }
    return ret;
  }

  private Set<Register.Virtual> def(Instruction insn) {
    Set<Register.Virtual> ret = new HashSet<>();
    if (insn.def() instanceof Register.Virtual dv) {
      ret.add(dv);
    }
    return ret;
  }

  private static class InterferenceGraph {
    Map<Register.Virtual, Set<Register.Virtual>> edges = new HashMap<>();
  }

  private InterferenceGraph buildInterferenceGraph(CFG cfg) {
    InterferenceGraph ig = new InterferenceGraph();

    for (CFGNode node : cfg.nodes) {
      for (Register r : node.insn.registers()) {
        if (r instanceof Register.Virtual vr) {
          ig.edges.putIfAbsent(vr, new HashSet<>());
        }
      }
    }

    for (CFGNode node : cfg.nodes) {
      List<Register.Virtual> outList = new ArrayList<>(node.liveOut);
      for (int i = 0; i < outList.size(); i++) {
        for (int j = i + 1; j < outList.size(); j++) {
          Register.Virtual a = outList.get(i);
          Register.Virtual b = outList.get(j);
          ig.edges.get(a).add(b);
          ig.edges.get(b).add(a);
        }
      }
      for (Register.Virtual d : def(node.insn)) {
        for (Register.Virtual o : node.liveOut) {
          if (!d.equals(o)) {
            ig.edges.get(d).add(o);
            ig.edges.get(o).add(d);
          }
        }
      }
    }

    return ig;
  }

  private static class ColorResult {
    Map<Register.Virtual, Register> colorMap = new HashMap<>();
    Set<Register.Virtual> spilled = new HashSet<>();
  }

  private ColorResult chaitinColor(InterferenceGraph ig, List<Register> allowed) {
    ColorResult cr = new ColorResult();
    Set<Register.Virtual> removed = new HashSet<>();
    Stack<Register.Virtual> stack = new Stack<>();
    int K = allowed.size();

    boolean progress = true;
    while (progress) {
      progress = false;

      for (Register.Virtual v : ig.edges.keySet()) {
        if (removed.contains(v) || cr.spilled.contains(v)) continue;
        long deg =
            ig.edges.get(v).stream()
                .filter(x -> !removed.contains(x) && !cr.spilled.contains(x))
                .count();
        if (deg < K) {
          stack.push(v);
          removed.add(v);
          progress = true;
          break;
        }
      }
      // If none found, pick one to spill
      if (!progress) {
        Optional<Register.Virtual> candidate =
            ig.edges.keySet().stream()
                .filter(x -> !removed.contains(x) && !cr.spilled.contains(x))
                .findAny();
        if (candidate.isPresent()) {
          cr.spilled.add(candidate.get());
          progress = true;
        }
      }
    }

    while (!stack.isEmpty()) {
      Register.Virtual v = stack.pop();
      Set<Register> usedColors = new HashSet<>();
      for (Register.Virtual neigh : ig.edges.get(v)) {
        Register c = cr.colorMap.get(neigh);
        if (c != null) usedColors.add(c);
      }
      Register chosen = null;
      for (Register r : allowed) {
        if (!usedColors.contains(r)) {
          chosen = r;
          break;
        }
      }
      if (chosen == null) {
        cr.spilled.add(v);
      } else {
        cr.colorMap.put(v, chosen);
      }
    }
    return cr;
  }

  private AssemblyProgram.TextSection rewriteSection(
      AssemblyProgram.TextSection oldSec, CFG cfg, ColorResult cr) {
    AssemblyProgram.TextSection newSec = new AssemblyProgram.TextSection();

    for (AssemblyItem item : oldSec.items) {
      if (item instanceof Instruction insn) {
        // Expand push/pop
        if (insn == Instruction.Nullary.pushRegisters) {
          expandPushRegisters(newSec, cr);
          continue;
        }
        if (insn == Instruction.Nullary.popRegisters) {
          expandPopRegisters(newSec, cr);
          continue;
        }
        // Remove dead def
        CFGNode node = cfg.insnNodeMap.get(insn);
        if (isDeadDefinition(node)) {
          continue;
        }
        // Otherwise rewrite
        rewriteInstr(newSec, insn, node, cr);

      } else if (item instanceof AssemblyTextItem ati) {
        newSec.emit(ati);
      }
    }
    return newSec;
  }

  private boolean isDeadDefinition(CFGNode node) {
    Instruction insn = node.insn;
    if (insn.def() == null) return false;
    if (!(insn.def() instanceof Register.Virtual dv)) return false;
    if (node.liveOut.contains(dv)) return false;
    // assume no side effect => remove
    return true;
  }

  private void expandPushRegisters(AssemblyProgram.TextSection newSec, ColorResult cr) {
    newSec.emit("Original instruction: pushRegisters");

    Set<Register.Virtual> allVregs = new HashSet<>(cr.colorMap.keySet());
    allVregs.addAll(cr.spilled);
    List<Register.Virtual> sorted = new ArrayList<>(allVregs);
    sorted.sort(Comparator.comparing(r -> r.name));

    for (Register.Virtual vr : sorted) {
      loadVregInto(newSec, vr, Register.Arch.t0, cr);
      newSec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4);
      newSec.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, 0);
    }
  }

  private void expandPopRegisters(AssemblyProgram.TextSection newSec, ColorResult cr) {
    newSec.emit("Original instruction: popRegisters");

    Set<Register.Virtual> allVregs = new HashSet<>(cr.colorMap.keySet());
    allVregs.addAll(cr.spilled);
    List<Register.Virtual> sorted = new ArrayList<>(allVregs);
    sorted.sort(Comparator.comparing(r -> r.name));
    Collections.reverse(sorted);

    for (Register.Virtual vr : sorted) {
      newSec.emit(OpCode.LW, Register.Arch.t0, Register.Arch.sp, 0);
      newSec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, 4);
      // force storing to memory or mapped register
      storeRegToVreg(newSec, Register.Arch.t0, vr, cr, true);
    }
  }

  private void rewriteInstr(
      AssemblyProgram.TextSection newSec, Instruction insn, CFGNode node, ColorResult cr) {
    newSec.emit("Original instruction: " + insn);

    Stack<Register> ephem = new Stack<>();
    ephem.push(Register.Arch.t1);
    ephem.push(Register.Arch.t0);

    Map<Register.Virtual, Register> loadedMap = new HashMap<>();
    Map<Register, Register> regMap = new HashMap<>();

    // load uses
    for (Register rUse : insn.uses()) {
      if (rUse instanceof Register.Virtual vr) {
        if (cr.spilled.contains(vr)) {
          Register tmp;
          if (loadedMap.containsKey(vr)) {
            tmp = loadedMap.get(vr);
          } else {
            if (ephem.isEmpty()) {
              throw new RuntimeException("Not enough ephemeral regs for uses!");
            }
            tmp = ephem.pop();
            loadSpill(newSec, vr, tmp);
            loadedMap.put(vr, tmp);
          }
          regMap.put(vr, tmp);
        } else {
          regMap.put(vr, cr.colorMap.get(vr));
        }
      }
    }

    Register.Virtual defV = null;
    if (insn.def() instanceof Register.Virtual dv) {
      defV = dv;
      if (cr.spilled.contains(dv)) {
        if (ephem.isEmpty()) {
          throw new RuntimeException("No ephemeral reg left for def!");
        }
        Register tmp = ephem.pop();
        regMap.put(dv, tmp);
      } else {
        regMap.put(dv, cr.colorMap.get(dv));
      }
    }

    Instruction rebuilt = insn.rebuild(regMap);
    newSec.emit(rebuilt);

    if (defV != null && cr.spilled.contains(defV)) {
      if (node.liveOut.contains(defV)) {
        Register ephemeral = regMap.get(defV);
        storeSpill(newSec, defV, ephemeral);
      }
    }
  }

  private void loadVregInto(
      AssemblyProgram.TextSection sec, Register.Virtual vr, Register dest, ColorResult cr) {
    if (cr.spilled.contains(vr)) {
      loadSpill(sec, vr, dest);
    } else {
      Register c = cr.colorMap.get(vr);
      if (c == null) throw new RuntimeException("No color for " + vr);
      if (dest != c) {
        sec.emit(OpCode.ADDU, dest, c, Register.Arch.zero);
      }
    }
  }

  private void loadSpill(AssemblyProgram.TextSection sec, Register.Virtual vr, Register dest) {
    Label lbl = getSpillLabel(vr);
    sec.emit(OpCode.LA, dest, lbl);
    sec.emit(OpCode.LW, dest, dest, 0);
  }

  private void storeRegToVreg(
      AssemblyProgram.TextSection sec,
      Register src,
      Register.Virtual vr,
      ColorResult cr,
      boolean forceStore) {
    if (cr.spilled.contains(vr)) {
      storeSpill(sec, vr, src);
    } else {
      Register c = cr.colorMap.get(vr);
      if (c != null && c != src) {
        sec.emit(OpCode.ADDU, c, src, Register.Arch.zero);
      }
    }
  }

  private void storeSpill(AssemblyProgram.TextSection sec, Register.Virtual vr, Register src) {
    Label lbl = getSpillLabel(vr);
    sec.emit(OpCode.LA, Register.Arch.t1, lbl);
    sec.emit(OpCode.SW, src, Register.Arch.t1, 0);
  }

  private Label getSpillLabel(Register.Virtual vr) {
    return spillLabelMap.computeIfAbsent(
        vr, v -> Label.get("spill_" + v.name + "_" + spillLabelCounter++));
  }
}
