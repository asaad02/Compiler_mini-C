package regalloc;

import gen.asm.*;
import java.util.*;

public class GraphColouringRegAlloc implements AssemblyPass {

  public static final GraphColouringRegAlloc INSTANCE = new GraphColouringRegAlloc();

  private static final List<Register> ALL_COLORABLE =
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
          Register.Arch.s5);

  private static final Register SPILL_TEMP_1 = Register.Arch.s6;
  private static final Register SPILL_TEMP_2 = Register.Arch.s7;

  private final Map<Register.Virtual, Label> spillLabels = new HashMap<>();

  private final Set<Label> alreadyEmittedSpillLabels = new HashSet<>();
  private static int nextSpillLabelId = 0;

  private GraphColouringRegAlloc() {}

  @Override
  public AssemblyProgram apply(AssemblyProgram program) {
    System.out.println("[GraphColourAlloc] START ----------");
    AssemblyProgram outProg = new AssemblyProgram();

    for (AssemblyTextItem dataItem : program.dataSection.items) {
      outProg.dataSection.emit(dataItem);
    }

    int functionIndex = 0;
    for (AssemblyProgram.TextSection oldSection : program.textSections) {
      functionIndex++;
      System.out.println("\n=== Processing function #" + functionIndex + " ===");
      System.out.println("Original text section has " + oldSection.items.size() + " items.");

      System.out.println("  Step1: Building CFG...");
      CFG cfg = buildCFG(oldSection);

      System.out.println("  Step2: Doing Liveness...");
      doLiveness(cfg);
      debugPrintLiveness("Liveness: After initial liveness", cfg);

      System.out.println("  Step3: Finding truly dead instructions...");
      Set<Instruction> dead = findTrulyDead(cfg);
      removeDead(cfg, dead);

      doLiveness(cfg);
      debugPrintLiveness("Liveness: After removing dead instructions & re-liveness", cfg);

      System.out.println("  Step4: Building Interference Graph...");
      InterferenceGraph ig = buildInterferenceGraph(cfg);
      debugPrintInterference(ig);

      System.out.println("  Step5: VR frequencies:");
      Map<Register.Virtual, Integer> freq = computeFrequency(cfg);
      for (Register.Virtual vr : freq.keySet()) {
        System.out.println("    freq(" + vr + ")=" + freq.get(vr));
      }

      System.out.println("  Step6: Chaitin coloring...");
      ColorResult colorRes = doColor(ig, freq);
      debugPrintColorResult(colorRes);

      System.out.println("  Step7: Rewriting instructions...");
      AssemblyProgram.TextSection newSec = rewriteSection(oldSection, colorRes, dead);

      System.out.println("  Step8: Checking jr $ra presence...");
      ensureJrRa(newSec);

      outProg.emitTextSection(newSec);
      System.out.println("RegAlloc succeeded for: (some function).");
    }

    System.out.println("Emitting any needed spill labels in data section...");
    for (Map.Entry<Register.Virtual, Label> e : spillLabels.entrySet()) {
      Label lbl = e.getValue();
      if (!alreadyEmittedSpillLabels.contains(lbl)) {
        outProg.dataSection.emit(new Directive("align 2"));
        outProg.dataSection.emit(lbl);
        outProg.dataSection.emit(new Directive("space 4"));
        alreadyEmittedSpillLabels.add(lbl);
      }
    }

    System.out.println("[GraphColourAlloc] DONE ----------");
    return outProg;
  }

  private static class CFG {
    final List<CFGNode> nodes = new ArrayList<>();
    final Map<Label, Integer> labelToIndex = new HashMap<>();
  }

  private static class CFGNode {
    Instruction insn;
    List<CFGNode> successors = new ArrayList<>();
    Set<Register.Virtual> liveIn = new HashSet<>();
    Set<Register.Virtual> liveOut = new HashSet<>();

    CFGNode(Instruction i) {
      this.insn = i;
    }
  }

  private CFG buildCFG(AssemblyProgram.TextSection sec) {
    CFG cfg = new CFG();
    List<Instruction> instructions = new ArrayList<>();

    int index = 0;
    for (AssemblyItem item : sec.items) {
      if (item instanceof Label lbl) {
        cfg.labelToIndex.put(lbl, instructions.size());
      } else if (item instanceof Instruction insn) {
        instructions.add(insn);
      }
    }

    for (Instruction insn : instructions) {
      cfg.nodes.add(new CFGNode(insn));
    }

    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      Instruction insn = node.insn;
      boolean unconditional = false;
      switch (insn.opcode.kind()) {
        case JUMP -> {
          unconditional = true;
          if (insn instanceof Instruction.Jump j) {
            Integer t = cfg.labelToIndex.get(j.label);
            if (t != null) node.successors.add(cfg.nodes.get(t));
            if (j.opcode == OpCode.JAL && i + 1 < cfg.nodes.size()) {
              node.successors.add(cfg.nodes.get(i + 1));
            }
          }
        }
        case JUMP_REGISTER -> {
          unconditional = true;
          if (insn instanceof Instruction.JumpRegister jr && !jr.address.equals(Register.Arch.ra)) {
            // link to all for safety
            for (int k = 0; k < cfg.nodes.size(); k++) {
              if (k != i) node.successors.add(cfg.nodes.get(k));
            }
          }
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

      if (!unconditional && i + 1 < cfg.nodes.size()) {
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
        CFGNode n = cfg.nodes.get(i);
        Set<Register.Virtual> oldIn = new HashSet<>(n.liveIn);
        Set<Register.Virtual> oldOut = new HashSet<>(n.liveOut);

        // out = union of succ.in
        Set<Register.Virtual> newOut = new HashSet<>();
        for (CFGNode s : n.successors) {
          newOut.addAll(s.liveIn);
        }
        n.liveOut = newOut;

        Set<Register.Virtual> useset = uses(n.insn);
        Set<Register.Virtual> defset = def(n.insn);
        Set<Register.Virtual> outMinusDef = new HashSet<>(n.liveOut);
        outMinusDef.removeAll(defset);
        Set<Register.Virtual> newIn = new HashSet<>(useset);
        newIn.addAll(outMinusDef);
        n.liveIn = newIn;

        if (!oldIn.equals(n.liveIn) || !oldOut.equals(n.liveOut)) {
          changed = true;
        }
      }
    } while (changed);
  }

  private Set<Register.Virtual> uses(Instruction i) {
    Set<Register.Virtual> ret = new HashSet<>();
    for (Register r : i.uses()) {
      if (r.isVirtual()) {
        ret.add((Register.Virtual) r);
      }
    }
    return ret;
  }

  private Set<Register.Virtual> def(Instruction i) {
    Set<Register.Virtual> ret = new HashSet<>();
    Register d = i.def();
    if (d != null && d.isVirtual()) ret.add((Register.Virtual) d);
    return ret;
  }

  private Set<Instruction> findTrulyDead(CFG cfg) {
    Set<Instruction> dead = new HashSet<>();
    for (CFGNode node : cfg.nodes) {
      Instruction insn = node.insn;
      Set<Register.Virtual> d = def(insn);
      if (d.size() == 1) {
        Register.Virtual dv = d.iterator().next();
        if (!node.liveOut.contains(dv) && !hasSideEffect(insn)) {
          dead.add(insn);
        }
      }
    }
    return dead;
  }

  private boolean hasSideEffect(Instruction i) {
    switch (i.opcode.kind()) {
      case STORE, JUMP, JUMP_REGISTER, BINARY_BRANCH, UNARY_BRANCH, NULLARY -> {
        return true;
      }
      default -> {
        return false;
      }
    }
  }

  private void removeDead(CFG cfg, Set<Instruction> dead) {
    cfg.nodes.removeIf(n -> dead.contains(n.insn));
  }

  private InterferenceGraph buildInterferenceGraph(CFG cfg) {
    InterferenceGraph ig = new InterferenceGraph();
    // initialize
    for (CFGNode n : cfg.nodes) {
      for (Register r : n.insn.registers()) {
        if (r.isVirtual()) {
          ig.edges.putIfAbsent((Register.Virtual) r, new HashSet<>());
        }
      }
    }

    for (CFGNode n : cfg.nodes) {
      List<Register.Virtual> outlist = new ArrayList<>(n.liveOut);
      for (int i = 0; i < outlist.size(); i++) {
        for (int j = i + 1; j < outlist.size(); j++) {
          Register.Virtual v1 = outlist.get(i);
          Register.Virtual v2 = outlist.get(j);
          ig.addEdge(v1, v2);
        }
      }

      Set<Register.Virtual> d = def(n.insn);
      for (Register.Virtual dv : d) {
        for (Register.Virtual ov : n.liveOut) {
          if (!dv.equals(ov)) {
            ig.addEdge(dv, ov);
          }
        }
      }
    }
    return ig;
  }

  private Map<Register.Virtual, Integer> computeFrequency(CFG cfg) {
    Map<Register.Virtual, Integer> freq = new HashMap<>();
    for (CFGNode n : cfg.nodes) {
      for (Register r : n.insn.uses()) {
        if (r.isVirtual()) {
          freq.put((Register.Virtual) r, freq.getOrDefault(r, 0) + 1);
        }
      }
    }
    return freq;
  }

  private static class ColorResult {
    public final Map<Register.Virtual, Register> colorMap = new HashMap<>();
    public final Set<Register.Virtual> spilled = new HashSet<>();
  }

  private ColorResult doColor(InterferenceGraph ig, Map<Register.Virtual, Integer> freq) {
    ColorResult cr = new ColorResult();

    Set<Register.Virtual> removed = new HashSet<>();
    Stack<Register.Virtual> stack = new Stack<>();
    boolean progress = true;
    int K = ALL_COLORABLE.size();
    while (progress) {
      progress = false;

      for (Register.Virtual v : ig.edges.keySet()) {
        if (removed.contains(v) || cr.spilled.contains(v)) continue;
        long deg =
            ig.edges.get(v).stream()
                .filter(o -> !removed.contains(o) && !cr.spilled.contains(o))
                .count();
        if (deg < K) {
          stack.push(v);
          removed.add(v);
          progress = true;
          break;
        }
      }
      if (!progress) {

        Optional<Register.Virtual> cand =
            ig.edges.keySet().stream()
                .filter(v -> !removed.contains(v) && !cr.spilled.contains(v))
                .min(
                    Comparator.comparingDouble(
                        v -> {
                          long deg =
                              ig.edges.get(v).stream()
                                  .filter(o -> !removed.contains(o) && !cr.spilled.contains(o))
                                  .count();
                          int f = freq.getOrDefault(v, 0);
                          return (deg + 1.0) / (f + 1.0);
                        }));
        if (cand.isPresent()) {
          cr.spilled.add(cand.get());
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
      for (Register r : ALL_COLORABLE) {
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
      AssemblyProgram.TextSection oldSec, ColorResult cr, Set<Instruction> dead) {
    AssemblyProgram.TextSection outSec = new AssemblyProgram.TextSection();
    for (AssemblyItem item : oldSec.items) {
      if (item instanceof Instruction insn) {
        if (dead.contains(insn)) {
          System.out.println("  [Rewrite] Skipping dead: " + insn);
          continue;
        }
        if (insn == Instruction.Nullary.pushRegisters) {
          expandPushRegisters(outSec, cr);
        } else if (insn == Instruction.Nullary.popRegisters) {
          expandPopRegisters(outSec, cr);
        } else {
          rewriteOneInstruction(outSec, insn, cr);
        }
      } else {
        if (item instanceof Instruction insn) {
          outSec.emit(insn);
        } else if (item instanceof AssemblyTextItem ati) {
          outSec.emit(ati);
        }
      }
    }
    return outSec;
  }

  private void expandPushRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    System.out.println("  [Rewrite] Expanding pushRegisters");
    // gather all VR
    Set<Register.Virtual> allVr = new HashSet<>(cr.colorMap.keySet());
    allVr.addAll(cr.spilled);
    if (allVr.isEmpty()) return;
    sec.emit("Original instruction: pushRegisters");
    List<Register.Virtual> sorted = new ArrayList<>(allVr);
    sorted.sort(Comparator.comparing(v -> v.name));
    for (Register.Virtual vr : sorted) {
      loadVregInto(sec, vr, Register.Arch.t0, cr);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4); // push
      sec.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, 0);
    }
  }

  private void expandPopRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    System.out.println("  [Rewrite] Expanding popRegisters");
    Set<Register.Virtual> allVr = new HashSet<>(cr.colorMap.keySet());
    allVr.addAll(cr.spilled);
    if (allVr.isEmpty()) return;
    sec.emit("Original instruction: popRegisters");
    List<Register.Virtual> sorted = new ArrayList<>(allVr);
    sorted.sort(Comparator.comparing(v -> v.name));
    Collections.reverse(sorted);
    for (Register.Virtual vr : sorted) {
      sec.emit(OpCode.LW, Register.Arch.t0, Register.Arch.sp, 0);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, 4);
      storeRegIntoVR(sec, Register.Arch.t0, vr, cr);
    }
  }

  private void rewriteOneInstruction(
      AssemblyProgram.TextSection sec, Instruction insn, ColorResult cr) {
    // ephemeral regs for spilled VR
    Stack<Register> ephemeral = new Stack<>();

    ephemeral.push(SPILL_TEMP_2);
    ephemeral.push(SPILL_TEMP_1);

    Map<Register, Register> mapping = new HashMap<>();

    for (Register u : insn.uses()) {
      if (!u.isVirtual()) continue;
      Register.Virtual vr = (Register.Virtual) u;
      if (cr.spilled.contains(vr)) {
        if (ephemeral.isEmpty()) {
          throw new RuntimeException("No ephemeral regs left to load spill for " + vr);
        }
        Register temp = ephemeral.pop();
        loadSpill(sec, vr, temp);
        mapping.put(vr, temp);
      } else {
        Register color = cr.colorMap.get(vr);
        if (color == null) {
          throw new RuntimeException("No color for VR " + vr);
        }
        mapping.put(vr, color);
      }
    }

    Register.Virtual dv = null;
    if (insn.def() != null && insn.def().isVirtual()) {
      dv = (Register.Virtual) insn.def();
      if (cr.spilled.contains(dv)) {
        if (ephemeral.isEmpty()) {
          throw new RuntimeException("No ephemeral regs left to store spill for " + dv);
        }
        Register temp = ephemeral.pop();
        mapping.put(dv, temp);
      } else {
        Register color = cr.colorMap.get(dv);
        if (color == null) {
          throw new RuntimeException("No color for VR " + dv);
        }
        mapping.put(dv, color);
      }
    }

    Instruction replaced = insn.rebuild(mapping);

    if (isRedundantAddu(replaced)) {
      System.out.println("  [Rewrite] Skipping redundant: " + replaced);
    } else {
      sec.emit(replaced);
    }

    if (dv != null && cr.spilled.contains(dv)) {
      Register ephemeralReg = mapping.get(dv);
      storeSpill(sec, dv, ephemeralReg);
    }
  }

  private boolean isRedundantAddu(Instruction i) {
    if (i instanceof Instruction.TernaryArithmetic ta) {
      if (ta.opcode == OpCode.ADDU) {

        if (ta.dst.equals(ta.src1) && ta.src2.equals(Register.Arch.zero)) return true;

        if (ta.dst.equals(ta.src2) && ta.src1.equals(Register.Arch.zero)) return true;
      }
    }
    return false;
  }

  private void loadVregInto(
      AssemblyProgram.TextSection sec, Register.Virtual vr, Register arch, ColorResult cr) {
    if (!cr.spilled.contains(vr)) {
      Register c = cr.colorMap.get(vr);
      if (c == null) throw new RuntimeException("No color for " + vr);
      if (!arch.equals(c)) {
        sec.emit(OpCode.ADDU, arch, c, Register.Arch.zero);
      }
    } else {
      loadSpill(sec, vr, arch);
    }
  }

  private void storeRegIntoVR(
      AssemblyProgram.TextSection sec, Register archSrc, Register.Virtual vr, ColorResult cr) {
    if (!cr.spilled.contains(vr)) {
      Register c = cr.colorMap.get(vr);
      if (!archSrc.equals(c)) {
        sec.emit(OpCode.ADDU, c, archSrc, Register.Arch.zero);
      }
    } else {
      storeSpill(sec, vr, archSrc);
    }
  }

  private Label getSpillLabel(Register.Virtual vr) {
    return spillLabels.computeIfAbsent(
        vr, x -> Label.get("spill_" + x.name + "_" + (nextSpillLabelId++)));
  }

  private void loadSpill(AssemblyProgram.TextSection sec, Register.Virtual vr, Register dest) {
    Label slot = getSpillLabel(vr);
    sec.emit(OpCode.LA, dest, slot);
    sec.emit(OpCode.LW, dest, dest, 0);
  }

  private void storeSpill(AssemblyProgram.TextSection sec, Register.Virtual vr, Register src) {
    Label slot = getSpillLabel(vr);
    // la $t1, slot
    sec.emit(OpCode.LA, SPILL_TEMP_2, slot);
    // sw $src,0($t1)
    sec.emit(OpCode.SW, src, SPILL_TEMP_2, 0);
  }

  private void ensureJrRa(AssemblyProgram.TextSection sec) {
    boolean found = false;
    for (AssemblyItem item : sec.items) {
      if (item instanceof Instruction.JumpRegister jr) {
        if (jr.opcode == OpCode.JR && jr.address.equals(Register.Arch.ra)) {
          found = true;
          break;
        }
      }
    }
    if (!found) {
      sec.emit(OpCode.JR, Register.Arch.ra);
    }
  }

  private void debugPrintLiveness(String title, CFG cfg) {
    System.out.println("  " + title);
    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode n = cfg.nodes.get(i);
      System.out.println("  node[" + i + "] " + n.insn);
      System.out.println("    in=" + n.liveIn);
      System.out.println("    out=" + n.liveOut);
    }
  }

  private void debugPrintInterference(InterferenceGraph ig) {
    System.out.println("Interference Graph:");
    for (Register.Virtual v : ig.edges.keySet()) {
      System.out.println("  " + v + " => " + ig.edges.get(v));
    }
  }

  private void debugPrintColorResult(ColorResult cr) {
    System.out.println("Coloring Result:");
    System.out.println("  Colored VRegs:");
    for (Register.Virtual v : cr.colorMap.keySet()) {
      System.out.println("    " + v + " => " + cr.colorMap.get(v));
    }
    System.out.println("  Spilled: " + cr.spilled);
  }
}
