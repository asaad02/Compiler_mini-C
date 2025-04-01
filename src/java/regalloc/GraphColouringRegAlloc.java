package regalloc;

import gen.asm.*;
import java.util.*;

public class GraphColouringRegAlloc implements AssemblyPass {

  public static final GraphColouringRegAlloc INSTANCE = new GraphColouringRegAlloc();

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
    Map<Instruction, CFGNode> nodeOf = new HashMap<>();
  }

  private CFG buildCFG(AssemblyProgram.TextSection section) {
    CFG cfg = new CFG();
    List<Instruction> insnList = new ArrayList<>();

    for (AssemblyItem item : section.items) {
      if (item instanceof Label lbl) {
        cfg.labelToIndex.put(lbl, insnList.size());
      } else if (item instanceof Instruction ins) {
        insnList.add(ins);
      }
    }

    for (Instruction ins : insnList) {
      CFGNode node = new CFGNode(ins);
      cfg.nodes.add(node);
      cfg.nodeOf.put(ins, node);
    }

    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      Instruction ins = node.insn;
      boolean isUncond = false;

      switch (ins.opcode.kind()) {
        case JUMP -> {
          isUncond = true;
          if (ins instanceof Instruction.Jump j) {
            Integer target = cfg.labelToIndex.get(j.label);
            if (target != null) {
              node.successors.add(cfg.nodes.get(target));
            }
          }
        }
        case JUMP_REGISTER -> {
          isUncond = true;
        }
        case BINARY_BRANCH -> {
          if (ins instanceof Instruction.BinaryBranch bb) {
            Integer t = cfg.labelToIndex.get(bb.label);
            if (t != null) node.successors.add(cfg.nodes.get(t));
          }
        }
        case UNARY_BRANCH -> {
          if (ins instanceof Instruction.UnaryBranch ub) {
            Integer t = cfg.labelToIndex.get(ub.label);
            if (t != null) node.successors.add(cfg.nodes.get(t));
          }
        }
        default -> {}
      }
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

  private Set<Register.Virtual> uses(Instruction ins) {
    Set<Register.Virtual> ret = new HashSet<>();
    for (Register r : ins.uses()) {
      if (r instanceof Register.Virtual vr) {
        ret.add(vr);
      }
    }
    return ret;
  }

  private Set<Register.Virtual> def(Instruction ins) {
    Set<Register.Virtual> ret = new HashSet<>();
    if (ins.def() instanceof Register.Virtual dv) {
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
      Set<Register.Virtual> defs = def(node.insn);
      for (Register.Virtual dv : defs) {
        for (Register.Virtual o : node.liveOut) {
          if (!dv.equals(o)) {
            ig.edges.get(dv).add(o);
            ig.edges.get(o).add(dv);
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

      for (Register.Virtual vr : ig.edges.keySet()) {
        if (removed.contains(vr) || cr.spilled.contains(vr)) continue;

        long deg =
            ig.edges.get(vr).stream()
                .filter(n -> !removed.contains(n) && !cr.spilled.contains(n))
                .count();
        if (deg < K) {
          stack.push(vr);
          removed.add(vr);
          progress = true;
          break;
        }
      }

      if (!progress) {
        Optional<Register.Virtual> candidate = pickHighestDegreeNode(ig, removed, cr.spilled);
        if (candidate.isPresent()) {
          cr.spilled.add(candidate.get());
          progress = true;
        }
      }
    }

    while (!stack.isEmpty()) {
      Register.Virtual vr = stack.pop();
      Set<Register> usedColors = new HashSet<>();
      for (Register.Virtual neigh : ig.edges.get(vr)) {
        if (cr.colorMap.containsKey(neigh)) {
          usedColors.add(cr.colorMap.get(neigh));
        }
      }
      Register chosen = null;
      for (Register r : allowed) {
        if (!usedColors.contains(r)) {
          chosen = r;
          break;
        }
      }
      if (chosen == null) {
        cr.spilled.add(vr);
      } else {
        cr.colorMap.put(vr, chosen);
      }
    }

    return cr;
  }

  private Optional<Register.Virtual> pickHighestDegreeNode(
      InterferenceGraph ig, Set<Register.Virtual> removed, Set<Register.Virtual> spilled) {
    Register.Virtual best = null;
    int bestDegree = -1;
    for (Register.Virtual vr : ig.edges.keySet()) {
      if (removed.contains(vr) || spilled.contains(vr)) {
        continue;
      }
      long deg =
          ig.edges.get(vr).stream()
              .filter(x -> !removed.contains(x) && !spilled.contains(x))
              .count();
      if (deg > bestDegree) {
        bestDegree = (int) deg;
        best = vr;
      }
    }
    return Optional.ofNullable(best);
  }

  private AssemblyProgram.TextSection rewriteSection(
      AssemblyProgram.TextSection oldSec, CFG cfg, ColorResult cr) {
    AssemblyProgram.TextSection newSec = new AssemblyProgram.TextSection();

    for (AssemblyItem item : oldSec.items) {
      if (item instanceof Instruction insn) {

        if (insn == Instruction.Nullary.pushRegisters) {
          expandPushRegisters(newSec, cr);
          continue;
        }
        if (insn == Instruction.Nullary.popRegisters) {
          expandPopRegisters(newSec, cr);
          continue;
        }

        CFGNode node = cfg.nodeOf.get(insn);
        if (isDeadDefinition(node)) {
          continue;
        }

        rewriteInstr(newSec, insn, node, cr);

      } else if (item instanceof AssemblyTextItem ati) {
        newSec.emit(ati);
      }
    }
    return newSec;
  }

  private boolean isDeadDefinition(CFGNode node) {
    Instruction insn = node.insn;
    Register defR = insn.def();
    if (defR == null || !(defR instanceof Register.Virtual dv)) {
      return false;
    }
    if (node.liveOut.contains(dv)) {
      return false;
    }
    return !mightHaveSideEffect(insn.opcode);
  }

  private boolean mightHaveSideEffect(OpCode op) {
    String m = op.mnemonic;
    switch (m) {
      case "addu":
      case "subu":
      case "and":
      case "or":
      case "xor":
      case "nor":
      case "sll":
      case "srl":
      case "sra":
        return false;
      default:
        return true;
    }
  }

  private void expandPushRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    sec.emit("Original instruction: pushRegisters");
    // gather all VRs that exist (colored or spilled)
    Set<Register.Virtual> allVRs = new HashSet<>(cr.colorMap.keySet());
    allVRs.addAll(cr.spilled);

    List<Register.Virtual> sorted = new ArrayList<>(allVRs);
    sorted.sort(Comparator.comparing(v -> v.name));

    for (Register.Virtual vr : sorted) {
      loadVregInto(sec, vr, Register.Arch.t0, cr);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4);
      sec.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, 0);
    }
  }

  private void expandPopRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    sec.emit("Original instruction: popRegisters");

    Set<Register.Virtual> allVRs = new HashSet<>(cr.colorMap.keySet());
    allVRs.addAll(cr.spilled);

    List<Register.Virtual> sorted = new ArrayList<>(allVRs);
    sorted.sort(Comparator.comparing(v -> v.name));

    Collections.reverse(sorted);

    for (Register.Virtual vr : sorted) {

      sec.emit(OpCode.LW, Register.Arch.t0, Register.Arch.sp, 0);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, 4);

      storeRegToVreg(sec, Register.Arch.t0, vr, cr, /* forceStore= */ true);
    }
  }

  private void rewriteInstr(
      AssemblyProgram.TextSection sec, Instruction insn, CFGNode node, ColorResult cr) {
    sec.emit("Original instruction: " + insn);

    Deque<Register> ephemeral = new ArrayDeque<>();
    ephemeral.push(Register.Arch.t3);
    ephemeral.push(Register.Arch.t2);
    ephemeral.push(Register.Arch.t1);
    ephemeral.push(Register.Arch.t0);

    Map<Register.Virtual, Register> loadedOnce = new HashMap<>();
    Map<Register, Register> regMap = new HashMap<>();

    for (Register rUse : insn.uses()) {
      if (rUse instanceof Register.Virtual vr) {
        if (cr.spilled.contains(vr)) {
          if (!loadedOnce.containsKey(vr)) {
            if (ephemeral.isEmpty()) {
              throw new RuntimeException(
                  "No ephemeral registers left for spilled use: " + vr + " in " + insn);
            }
            Register ephemReg = ephemeral.pop();
            loadSpill(sec, vr, ephemReg);
            loadedOnce.put(vr, ephemReg);
          }
          regMap.put(vr, loadedOnce.get(vr));
        } else {

          regMap.put(vr, cr.colorMap.get(vr));
        }
      }
    }

    Register.Virtual defV = null;
    if (insn.def() instanceof Register.Virtual dv) {
      defV = dv;
      if (cr.spilled.contains(dv)) {
        if (ephemeral.isEmpty()) {

          throw new RuntimeException("No ephemeral reg left for spilled def in: " + insn);
        }
        Register ephemReg = ephemeral.pop();
        regMap.put(dv, ephemReg);
      } else {
        regMap.put(dv, cr.colorMap.get(dv));
      }
    }

    Instruction newInsn = insn.rebuild(regMap);
    sec.emit(newInsn);

    if (defV != null && cr.spilled.contains(defV)) {
      if (node.liveOut.contains(defV)) {
        Register ephemeralReg = regMap.get(defV);
        storeSpill(sec, defV, ephemeralReg);
      }
    }
  }

  private void loadVregInto(
      AssemblyProgram.TextSection sec, Register.Virtual vr, Register dest, ColorResult cr) {
    if (cr.spilled.contains(vr)) {
      loadSpill(sec, vr, dest);
    } else {
      Register color = cr.colorMap.get(vr);
      if (color == null) {
        throw new RuntimeException("No color assigned to " + vr);
      }
      if (dest != color) {
        sec.emit(OpCode.ADDU, dest, color, Register.Arch.zero);
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
      // colored
      Register color = cr.colorMap.get(vr);
      if (color == null) {
        throw new RuntimeException("No color for " + vr);
      }
      if (color != src) {
        sec.emit(OpCode.ADDU, color, src, Register.Arch.zero);
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
        vr, v -> Label.get("spill_" + v.name + "_" + (spillLabelCounter++)));
  }
}
