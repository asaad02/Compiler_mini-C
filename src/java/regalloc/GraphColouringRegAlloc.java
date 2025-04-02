package regalloc;

import gen.asm.*;
import gen.asm.Instruction.*;
import gen.asm.Register.Virtual;
import java.util.*;

public class GraphColouringRegAlloc implements AssemblyPass {

  public static final GraphColouringRegAlloc INSTANCE = new GraphColouringRegAlloc();

  private static final List<Register> ALL_COLORABLE =
      List.of(
          Register.Arch.t0,
          Register.Arch.t1,
          Register.Arch.t2,
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
  private static final Register SPILL_TEMP_3 = Register.Arch.t3;

  private final Map<Virtual, Label> spillLabels = new HashMap<>();
  private final Set<Label> alreadyEmittedSpillLabels = new HashSet<>();
  private static int nextSpillLabelId = 0;

  private GraphColouringRegAlloc() {}

  @Override
  public AssemblyProgram apply(AssemblyProgram prog) {
    System.out.println("[GraphColourAlloc] START ----------");
    AssemblyProgram newProg = new AssemblyProgram();
    newProg.dataSection.items.addAll(prog.dataSection.items);

    int functionIndex = 0;
    for (AssemblyProgram.Section sec : prog.textSections) {
      if (!(sec instanceof AssemblyProgram.TextSection textSec)) continue;
      functionIndex++;
      System.out.println("\n=== Processing function #" + functionIndex + " ===");
      System.out.println("Original text section has " + textSec.items.size() + " items.");

      System.out.println("  Step1: Building CFG...");
      List<Node> cfg = buildCFG(textSec);

      System.out.println("  Step2: Performing liveness analysis...");
      doLiveness(cfg);
      debugPrintLiveness("Liveness (after initial pass)", cfg);

      System.out.println("  Step3: Finding truly dead instructions...");
      Set<Instruction> dead = findTrulyDead(cfg);
      removeDead(cfg, dead);
      doLiveness(cfg);
      debugPrintLiveness("Liveness (after dead-code removal)", cfg);

      System.out.println("  Step4: Building interference graph...");
      InterferenceGraph ig = buildInterferenceGraph(cfg);
      debugPrintInterference(ig);

      System.out.println("  Step5: Computing virtual register frequencies...");
      Map<Virtual, Integer> freq = computeFrequency(cfg);
      for (Virtual vr : freq.keySet()) {
        System.out.println("    freq(" + vr + ") = " + freq.get(vr));
      }

      System.out.println("  Step6: Running Chaitin coloring...");
      ColorResult colorRes = doColor(ig, freq);
      debugPrintColorResult(colorRes);

      System.out.println("  Step7: Rewriting instructions...");
      AssemblyProgram.TextSection newText = rewriteSection(textSec, colorRes, dead);

      System.out.println("  Step7.5: Running final optimization pass...");
      finalOptimizationPass(newText);

      System.out.println("  Step8: Ensuring presence of 'jr $ra'...");
      ensureJrRa(newText);

      newProg.emitTextSection(newText);
      System.out.println("RegAlloc succeeded for function #" + functionIndex);
    }

    System.out.println("Emitting spill labels into data section...");
    for (Map.Entry<Virtual, Label> e : spillLabels.entrySet()) {
      Label lbl = e.getValue();
      if (!alreadyEmittedSpillLabels.contains(lbl)) {
        newProg.dataSection.emit(new Directive("align 2"));
        newProg.dataSection.emit(lbl);
        newProg.dataSection.emit(new Directive("space 4"));
        alreadyEmittedSpillLabels.add(lbl);
      }
    }

    System.out.println("[GraphColourAlloc] DONE ----------");
    return newProg;
  }

  public static class Node {
    public AssemblyItem item;
    public Instruction instruction;
    public Label label;
    public List<Register> uses = new ArrayList<>();
    public Register def;
    public Set<Virtual> liveIn = new HashSet<>();
    public Set<Virtual> liveOut = new HashSet<>();
    public List<Node> succs = new ArrayList<>();

    public Node(Label lbl) {
      this.item = lbl;
      this.label = lbl;
    }

    public Node(Instruction insn) {
      this.item = insn;
      this.instruction = insn;
    }

    public void addSucc(Node n) {
      succs.add(n);
    }
  }

  public static List<Node> buildCFG(AssemblyProgram.TextSection sec) {
    List<Node> cfg = new ArrayList<>();
    Map<String, Integer> labelToIndex = new HashMap<>();
    List<Instruction> instructions = new ArrayList<>();
    for (AssemblyItem item : sec.items) {
      if (item instanceof Comment) continue;
      else if (item instanceof Label lbl) {
        labelToIndex.put(lbl.toString(), instructions.size());
      } else if (item instanceof Directive) continue;
      else if (item instanceof Instruction insn) {
        instructions.add(insn);
      }
    }

    for (Instruction insn : instructions) {
      Node node = new Node(insn);
      if (insn.def() != null && insn.def().isVirtual()) node.def = insn.def();
      for (Register r : insn.uses()) {
        if (r != null && r.isVirtual()) node.uses.add(r);
      }
      cfg.add(node);
    }
    for (int i = 0; i < cfg.size() - 1; i++) {
      cfg.get(i).addSucc(cfg.get(i + 1));
    }
    for (int i = 0; i < cfg.size(); i++) {
      Node node = cfg.get(i);
      Instruction insn = node.instruction;
      boolean unconditional = false;
      switch (insn.opcode.kind()) {
        case JUMP -> {
          unconditional = true;
          if (insn instanceof Jump j) {
            Integer t = labelToIndex.get(j.label.toString());
            if (t != null) node.addSucc(cfg.get(t));
            else System.out.println("WARNING: Label not found in CFG: " + j.label);
            if (j.opcode == OpCode.JAL && i + 1 < cfg.size()) node.addSucc(cfg.get(i + 1));
          }
        }
        case JUMP_REGISTER -> {
          unconditional = true;
          if (insn instanceof JumpRegister jr && !jr.address.equals(Register.Arch.ra)) {
            for (int k = 0; k < cfg.size(); k++) {
              if (k != i) node.addSucc(cfg.get(k));
            }
          }
        }
        case BINARY_BRANCH -> {
          if (insn instanceof BinaryBranch bb) {
            Integer t = labelToIndex.get(bb.label.toString());
            if (t != null) node.addSucc(cfg.get(t));
            else
              System.out.println("WARNING: Label not found in CFG for binary branch: " + bb.label);
          }
        }
        case UNARY_BRANCH -> {
          if (insn instanceof UnaryBranch ub) {
            Integer t = labelToIndex.get(ub.label.toString());
            if (t != null) node.addSucc(cfg.get(t));
            else
              System.out.println("WARNING: Label not found in CFG for unary branch: " + ub.label);
          }
        }
        default -> {}
      }
      if (!unconditional && i + 1 < cfg.size()) {
        node.addSucc(cfg.get(i + 1));
      }
    }
    return cfg;
  }

  private void doLiveness(List<Node> cfg) {
    boolean changed;
    do {
      changed = false;
      for (int i = cfg.size() - 1; i >= 0; i--) {
        Node n = cfg.get(i);
        Set<Virtual> oldIn = new HashSet<>(n.liveIn);
        Set<Virtual> oldOut = new HashSet<>(n.liveOut);
        Set<Virtual> newOut = new HashSet<>();
        for (Node succ : n.succs) {
          newOut.addAll(succ.liveIn);
        }
        n.liveOut = newOut;
        Set<Virtual> useSet = new HashSet<>();
        for (Register r : n.uses) {
          useSet.add((Virtual) r);
        }
        Set<Virtual> defSet = new HashSet<>();
        if (n.def != null && n.def.isVirtual()) defSet.add((Virtual) n.def);
        Set<Virtual> outMinusDef = new HashSet<>(n.liveOut);
        outMinusDef.removeAll(defSet);
        Set<Virtual> newIn = new HashSet<>(useSet);
        newIn.addAll(outMinusDef);
        n.liveIn = newIn;
        if (!oldIn.equals(n.liveIn) || !oldOut.equals(n.liveOut)) changed = true;
      }
    } while (changed);
  }

  private Set<Instruction> findTrulyDead(List<Node> cfg) {
    Set<Instruction> deadInsns = new HashSet<>();
    for (Node n : cfg) {
      if (n.instruction != null) {
        Set<Virtual> defs = new HashSet<>();
        if (n.def != null && n.def.isVirtual()) defs.add((Virtual) n.def);
        if (defs.size() == 1) {
          Virtual dv = defs.iterator().next();
          if (!n.liveOut.contains(dv) && !hasSideEffect(n.instruction)) {
            System.out.println("  [Liveness] Found dead instruction: " + n.instruction);
            deadInsns.add(n.instruction);
          }
        }
      }
    }
    return deadInsns;
  }

  private boolean hasSideEffect(Instruction i) {
    switch (i.opcode.kind()) {
      case STORE:
      case JUMP:
      case JUMP_REGISTER:
      case BINARY_BRANCH:
      case UNARY_BRANCH:
      case NULLARY:
        return true;
      default:
        return false;
    }
  }

  private void removeDead(List<Node> cfg, Set<Instruction> dead) {
    cfg.removeIf(n -> dead.contains(n.instruction));
  }

  private static class InterferenceGraph {
    final Map<Virtual, Set<Virtual>> edges = new HashMap<>();

    void addEdge(Virtual v1, Virtual v2) {
      edges.computeIfAbsent(v1, k -> new HashSet<>()).add(v2);
      edges.computeIfAbsent(v2, k -> new HashSet<>()).add(v1);
    }
  }

  private InterferenceGraph buildInterferenceGraph(List<Node> cfg) {
    InterferenceGraph ig = new InterferenceGraph();
    // Ensure every virtual register gets an entry.
    for (Node n : cfg) {
      for (Register r : n.uses) {
        if (r.isVirtual()) ig.edges.putIfAbsent((Virtual) r, new HashSet<>());
      }
      if (n.def != null && n.def.isVirtual())
        ig.edges.putIfAbsent((Virtual) n.def, new HashSet<>());
    }
    for (Node n : cfg) {
      List<Virtual> outList = new ArrayList<>(n.liveOut);
      for (int i = 0; i < outList.size(); i++) {
        for (int j = i + 1; j < outList.size(); j++) {
          ig.addEdge(outList.get(i), outList.get(j));
        }
      }
      Set<Virtual> defs = new HashSet<>();
      if (n.def != null && n.def.isVirtual()) defs.add((Virtual) n.def);
      for (Virtual dv : defs) {
        for (Virtual ov : n.liveOut) {
          if (!dv.equals(ov)) ig.addEdge(dv, ov);
        }
      }
    }
    return ig;
  }

  private Map<Virtual, Integer> computeFrequency(List<Node> cfg) {
    Map<Virtual, Integer> freq = new HashMap<>();
    for (Node n : cfg) {
      for (Register r : n.uses) {
        if (r.isVirtual()) {
          Virtual vr = (Virtual) r;
          freq.put(vr, freq.getOrDefault(vr, 0) + 1);
        }
      }
    }
    return freq;
  }

  private static class ColorResult {
    final Map<Virtual, Register> colorMap = new HashMap<>();
    final Set<Virtual> spilled = new HashSet<>();
  }

  private ColorResult doColor(InterferenceGraph ig, Map<Virtual, Integer> freq) {
    ColorResult cr = new ColorResult();
    Set<Virtual> removed = new HashSet<>();
    Stack<Virtual> stack = new Stack<>();
    int K = ALL_COLORABLE.size();
    boolean progress;
    while (true) {
      progress = false;
      for (Virtual v : ig.edges.keySet()) {
        if (removed.contains(v) || cr.spilled.contains(v)) continue;
        long deg =
            ig.edges.get(v).stream()
                .filter(o -> !removed.contains(o) && !cr.spilled.contains(o))
                .count();
        if (deg < K) {
          stack.push(v);
          removed.add(v);
          System.out.println("  [Coloring] Pushed " + v + " onto stack (deg=" + deg + ")");
          progress = true;
          break;
        }
      }
      Set<Virtual> remaining = new HashSet<>();
      for (Virtual v : ig.edges.keySet()) {
        if (!removed.contains(v) && !cr.spilled.contains(v)) remaining.add(v);
      }
      if (!remaining.isEmpty()) {
        if (!progress) {
          Optional<Virtual> cand =
              remaining.stream()
                  .min(
                      Comparator.comparingDouble(
                          v -> {
                            long deg =
                                ig.edges.get(v).stream()
                                    .filter(o -> !removed.contains(o) && !cr.spilled.contains(o))
                                    .count();
                            int f = freq.getOrDefault(v, 0);
                            double ratio = (deg + 1.0) / (2.0 * (f + 1.0));
                            System.out.println("  [Coloring] Ratio for " + v + " = " + ratio);
                            return ratio;
                          }));
          if (cand.isPresent()) {
            System.out.println("  [Coloring] Spilling " + cand.get());
            cr.spilled.add(cand.get());
            continue;
          } else {
            throw new RuntimeException("Coloring: no candidate found for spilling");
          }
        }
      } else {
        break;
      }
    }
    // Assignment phase.
    while (!stack.isEmpty()) {
      Virtual v = stack.pop();
      Set<Register> usedColors = new HashSet<>();
      for (Virtual neigh : ig.edges.get(v)) {
        Register col = cr.colorMap.get(neigh);
        if (col != null) usedColors.add(col);
      }
      Register chosen = null;
      for (Register r : ALL_COLORABLE) {
        if (!usedColors.contains(r)) {
          chosen = r;
          break;
        }
      }
      if (chosen == null) {
        System.out.println("  [Coloring] Could not find color for " + v + "; marking as spilled");
        cr.spilled.add(v);
      } else {
        System.out.println("  [Coloring] Coloring " + v + " with " + chosen);
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
          System.out.println("  [Rewrite] Skipping dead instruction: " + insn);
          continue;
        }
        if (insn == Instruction.Nullary.pushRegisters) {
          expandPushRegisters(outSec, cr);
        } else if (insn == Instruction.Nullary.popRegisters) {
          expandPopRegisters(outSec, cr);
        } else {
          rewriteOneInstruction(outSec, insn, cr);
        }
      } else if (item instanceof AssemblyTextItem) {
        outSec.emit((AssemblyTextItem) item);
      } else {
        throw new RuntimeException("Unknown assembly item type: " + item);
      }
    }
    return outSec;
  }

  private void expandPushRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    System.out.println("  [Rewrite] Expanding pushRegisters");
    Set<Virtual> allVr = new HashSet<>(cr.colorMap.keySet());
    allVr.addAll(cr.spilled);
    if (allVr.isEmpty()) return;
    sec.emit("Original instruction: pushRegisters");
    List<Virtual> sorted = new ArrayList<>(allVr);
    sorted.sort(Comparator.comparing(Object::toString));
    for (Virtual vr : sorted) {
      loadVregInto(sec, vr, Register.Arch.t0, cr);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4);
      sec.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, 0);
    }
  }

  private void expandPopRegisters(AssemblyProgram.TextSection sec, ColorResult cr) {
    System.out.println("  [Rewrite] Expanding popRegisters");
    Set<Virtual> allVr = new HashSet<>(cr.colorMap.keySet());
    allVr.addAll(cr.spilled);
    if (allVr.isEmpty()) return;
    sec.emit("Original instruction: popRegisters");
    List<Virtual> sorted = new ArrayList<>(allVr);
    sorted.sort(Comparator.comparing(Object::toString));
    Collections.reverse(sorted);
    for (Virtual vr : sorted) {
      sec.emit(OpCode.LW, Register.Arch.t0, Register.Arch.sp, 0);
      sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, 4);
      storeRegIntoVR(sec, Register.Arch.t0, vr, cr);
    }
  }

  private void rewriteOneInstruction(
      AssemblyProgram.TextSection sec, Instruction insn, ColorResult cr) {
    if (insn instanceof TernaryArithmetic ta) {
      boolean dstSpilled = ta.dst.isVirtual() && cr.spilled.contains((Virtual) ta.dst);
      boolean src1Spilled = ta.src1.isVirtual() && cr.spilled.contains((Virtual) ta.src1);
      boolean src2Spilled = ta.src2.isVirtual() && cr.spilled.contains((Virtual) ta.src2);
      if (dstSpilled && src1Spilled && src2Spilled) {
        System.out.println("  [Rewrite] Special-case: All operands spilled in " + insn);
        loadSpill(sec, (Virtual) ta.src1, SPILL_TEMP_1);
        loadSpill(sec, (Virtual) ta.src2, SPILL_TEMP_2);
        TernaryArithmetic newInstr =
            new TernaryArithmetic(
                (OpCode.TernaryArithmetic) ta.opcode, SPILL_TEMP_1, SPILL_TEMP_1, SPILL_TEMP_2);
        sec.emit(newInstr);
        storeSpill(sec, (Virtual) ta.dst, SPILL_TEMP_1);
        return;
      }
    }
    // Standard rewriting.
    Stack<Register> ephemeral = new Stack<>();
    ephemeral.push(SPILL_TEMP_3);
    ephemeral.push(SPILL_TEMP_2);
    ephemeral.push(SPILL_TEMP_1);
    Map<Register, Register> mapping = new HashMap<>();
    for (Register r : insn.uses()) {
      if (r == null || !r.isVirtual()) continue;
      Virtual vr = (Virtual) r;
      if (cr.spilled.contains(vr)) {
        if (ephemeral.isEmpty())
          throw new RuntimeException("No ephemeral registers left to load spill for " + vr);
        Register temp = ephemeral.pop();
        loadSpill(sec, vr, temp);
        mapping.put(vr, temp);
      } else {
        Register color = cr.colorMap.get(vr);
        if (color == null)
          throw new RuntimeException("No color assigned to virtual register " + vr);
        mapping.put(vr, color);
      }
    }
    Virtual dv = null;
    if (insn.def() != null && insn.def().isVirtual()) {
      dv = (Virtual) insn.def();
      if (cr.spilled.contains(dv)) {
        if (ephemeral.isEmpty())
          throw new RuntimeException("No ephemeral registers left to store spill for " + dv);
        Register temp = ephemeral.pop();
        mapping.put(dv, temp);
      } else {
        Register color = cr.colorMap.get(dv);
        if (color == null)
          throw new RuntimeException("No color assigned to virtual register " + dv);
        mapping.put(dv, color);
      }
    }
    Instruction replaced = insn.rebuild(mapping);
    if (isRedundantAddu(replaced)) {
      System.out.println("  [Rewrite] Skipping redundant instruction: " + replaced);
    } else {
      sec.emit(replaced);
    }
    if (dv != null && cr.spilled.contains(dv)) {
      Register tmp = mapping.get(dv);
      storeSpill(sec, dv, tmp);
    }
  }

  private boolean isRedundantAddu(Instruction i) {
    if (i instanceof TernaryArithmetic ta) {
      if (ta.opcode == OpCode.ADDU) {
        if (ta.dst.equals(ta.src1) && ta.src2.equals(Register.Arch.zero)) return true;
        if (ta.dst.equals(ta.src2) && ta.src1.equals(Register.Arch.zero)) return true;
      }
    }
    return false;
  }

  private void loadVregInto(
      AssemblyProgram.TextSection sec, Virtual vr, Register arch, ColorResult cr) {
    if (!cr.spilled.contains(vr)) {
      Register c = cr.colorMap.get(vr);
      if (c == null) throw new RuntimeException("No color for virtual register " + vr);
      if (!arch.equals(c)) {
        sec.emit(OpCode.ADDU, arch, c, Register.Arch.zero);
      }
    } else {
      loadSpill(sec, vr, arch);
    }
  }

  private void storeRegIntoVR(
      AssemblyProgram.TextSection sec, Register archSrc, Virtual vr, ColorResult cr) {
    if (!cr.spilled.contains(vr)) {
      Register c = cr.colorMap.get(vr);
      if (!archSrc.equals(c)) {
        sec.emit(OpCode.ADDU, c, archSrc, Register.Arch.zero);
      }
    } else {
      storeSpill(sec, vr, archSrc);
    }
  }

  private Label getSpillLabel(Virtual vr) {
    return spillLabels.computeIfAbsent(
        vr, x -> Label.get("spill_" + x.toString() + "_" + (nextSpillLabelId++)));
  }

  private void loadSpill(AssemblyProgram.TextSection sec, Virtual vr, Register dest) {
    Label slot = getSpillLabel(vr);
    sec.emit(OpCode.LA, dest, slot);
    sec.emit(OpCode.LW, dest, dest, 0);
  }

  private void storeSpill(AssemblyProgram.TextSection sec, Virtual vr, Register src) {
    Label slot = getSpillLabel(vr);
    sec.emit(OpCode.LA, SPILL_TEMP_2, slot);
    sec.emit(OpCode.SW, src, SPILL_TEMP_2, 0);
  }

  private void ensureJrRa(AssemblyProgram.TextSection sec) {
    boolean found = false;
    for (AssemblyItem item : sec.items) {
      if (item instanceof JumpRegister jr) {
        if (jr.opcode == OpCode.JR && jr.address.equals(Register.Arch.ra)) {
          found = true;
          break;
        }
      }
    }
    if (!found) {
      System.out.println("  [Rewrite] 'jr $ra' not found; emitting it at the end.");
      sec.emit(OpCode.JR, Register.Arch.ra);
    }
  }

  private void finalOptimizationPass(AssemblyProgram.TextSection sec) {
    List<AssemblyItem> optimized = new ArrayList<>();
    Map<Register, Register> copyMap = new HashMap<>();
    for (AssemblyItem item : sec.items) {
      if (item instanceof TernaryArithmetic ta && ta.opcode == OpCode.ADDU) {
        if (ta.dst.equals(ta.src1) && ta.src2.equals(Register.Arch.zero)) {
          copyMap.put(ta.dst, ta.src1);
          continue;
        } else if (ta.dst.equals(ta.src2) && ta.src1.equals(Register.Arch.zero)) {
          copyMap.put(ta.dst, ta.src2);
          continue;
        }
      }
      if (item instanceof Instruction insn) {
        Map<Register, Register> mapping = new HashMap<>();
        for (Register r : insn.registers()) {
          if (copyMap.containsKey(r)) {
            mapping.put(r, copyMap.get(r));
          }
        }
        optimized.add(insn.rebuild(mapping));
      } else {
        optimized.add(item);
      }
    }
    sec.items.clear();
    sec.items.addAll(optimized);
  }

  private void debugPrintLiveness(String title, List<Node> cfg) {
    System.out.println("DEBUG: " + title);
    for (int i = 0; i < cfg.size(); i++) {
      Node n = cfg.get(i);
      String desc = (n.instruction != null) ? n.instruction.toString() : n.label.toString();
      System.out.println("  node[" + i + "]: " + desc);
      System.out.println("    liveIn: " + n.liveIn);
      System.out.println("    liveOut: " + n.liveOut);
    }
  }

  private void debugPrintInterference(InterferenceGraph ig) {
    System.out.println("DEBUG: Interference Graph");
    for (Virtual v : ig.edges.keySet()) {
      System.out.println("  " + v + " interferes with " + ig.edges.get(v));
    }
  }

  private void debugPrintColorResult(ColorResult cr) {
    System.out.println("DEBUG: Coloring Result");
    System.out.println("  Colored virtual registers:");
    for (Virtual v : cr.colorMap.keySet()) {
      System.out.println("    " + v + " -> " + cr.colorMap.get(v));
    }
    System.out.println("  Spilled registers: " + cr.spilled);
  }
}
