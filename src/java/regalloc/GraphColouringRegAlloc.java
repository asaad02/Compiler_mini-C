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

  private static final Register SPILL_TEMP_1 = Register.Arch.s6;
  private static final Register SPILL_TEMP_2 = Register.Arch.s7;
  private static final Register SPILL_TEMP_3 = Register.Arch.t3;

  private static int spillLabelCounter = 0;

  private final Map<Register.Virtual, Label> spillLabelMap = new HashMap<>();
  private final Set<Label> alignedSpillLabels = new HashSet<>();

  private GraphColouringRegAlloc() {}

  @Override
  public AssemblyProgram apply(AssemblyProgram program) {
    System.out.println("[GraphColouringRegAlloc] START");
    AssemblyProgram outProg = new AssemblyProgram();

    program.dataSection.items.forEach(item -> outProg.dataSection.emit(item));

    program.textSections.forEach(
        oldSection -> {
          System.out.println("\n[GraphColouringRegAlloc] Processing a function text section...");
          CFG cfg = buildCFG(oldSection);
          System.out.println("  -> CFG built with " + cfg.nodes.size() + " nodes.");
          doLiveness(cfg);
          System.out.println("  -> Liveness analysis complete.");
          debugPrintLiveness("Liveness (post-analysis)", cfg);
          InterferenceGraph ig = buildInterferenceGraph(cfg);
          System.out.println("  -> Interference graph has " + ig.edges.size() + " vreg nodes.");
          debugPrintInterference(ig);
          ColorResult colorResult = chaitinColor(ig, ALLOCATABLE);
          System.out.println(
              "  -> Coloring complete. Spilled count = " + colorResult.spilled.size());
          debugPrintColorResult(colorResult);
          AssemblyProgram.TextSection newSection = rewriteSection(oldSection, colorResult);
          outProg.emitTextSection(newSection);
        });

    System.out.println("Emitting spill slots in the data section...");
    spillLabelMap.forEach(
        (vr, spillLbl) -> {
          if (!alignedSpillLabels.contains(spillLbl)) {
            outProg.dataSection.emit(new Directive("align 2"));
            outProg.dataSection.emit(spillLbl);
            outProg.dataSection.emit(new Directive("space 4"));
            alignedSpillLabels.add(spillLbl);
          }
        });

    System.out.println("[GraphColouringRegAlloc] END\n");
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
  }

  private CFG buildCFG(AssemblyProgram.TextSection section) {
    System.out.println("    [buildCFG] Gathering instructions and labels...");
    CFG cfg = new CFG();
    List<Instruction> insnList = new ArrayList<>();
    section.items.forEach(
        item -> {
          switch (item) {
            case Label lbl -> cfg.labelToIndex.put(lbl, insnList.size());
            case Instruction insn -> insnList.add(insn);
            default -> {}
          }
        });
    insnList.forEach(insn -> cfg.nodes.add(new CFGNode(insn)));
    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      Instruction insn = node.insn;
      boolean unconditional = false;
      switch (insn.opcode.kind()) {
        case JUMP -> {
          unconditional = true;
          if (insn instanceof Instruction.Jump jump) {
            Integer targetIndex = cfg.labelToIndex.get(jump.label);
            if (targetIndex != null) {
              node.successors.add(cfg.nodes.get(targetIndex));
              System.out.println("      [CFG] Node " + i + " JUMP to " + jump.label);
            }
          }
        }
        case JUMP_REGISTER -> {
          unconditional = true;
          System.out.println("      [CFG] Node " + i + " is JUMP_REGISTER");
        }
        case BINARY_BRANCH -> {
          if (insn instanceof Instruction.BinaryBranch bbr) {
            Integer targetIndex = cfg.labelToIndex.get(bbr.label);
            if (targetIndex != null) {
              node.successors.add(cfg.nodes.get(targetIndex));
              System.out.println("      [CFG] Node " + i + " BINARY_BRANCH to " + bbr.label);
            }
          }
        }
        case UNARY_BRANCH -> {
          if (insn instanceof Instruction.UnaryBranch ubr) {
            Integer targetIndex = cfg.labelToIndex.get(ubr.label);
            if (targetIndex != null) {
              node.successors.add(cfg.nodes.get(targetIndex));
              System.out.println("      [CFG] Node " + i + " UNARY_BRANCH to " + ubr.label);
            }
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
    System.out.println("    [doLiveness] Running liveness analysis...");
    boolean changed;
    int iterations = 0;
    do {
      iterations++;
      changed = false;
      for (int i = cfg.nodes.size() - 1; i >= 0; i--) {
        CFGNode node = cfg.nodes.get(i);
        Set<Register.Virtual> oldIn = new HashSet<>(node.liveIn);
        Set<Register.Virtual> oldOut = new HashSet<>(node.liveOut);
        Set<Register.Virtual> newOut = new HashSet<>();
        node.successors.forEach(succ -> newOut.addAll(succ.liveIn));
        node.liveOut = newOut;
        Set<Register.Virtual> newIn = new HashSet<>(uses(node.insn));
        Set<Register.Virtual> outMinusDef = new HashSet<>(node.liveOut);
        outMinusDef.removeAll(def(node.insn));
        newIn.addAll(outMinusDef);
        node.liveIn = newIn;
        if (!node.liveIn.equals(oldIn) || !node.liveOut.equals(oldOut)) {
          changed = true;
        }
      }
    } while (changed);
    System.out.println("    [doLiveness] Converged after " + iterations + " iterations.");
  }

  private Set<Register.Virtual> uses(Instruction insn) {
    Set<Register.Virtual> s = new HashSet<>();
    insn.uses()
        .forEach(
            r -> {
              if (r instanceof Register.Virtual vr) {
                s.add(vr);
              }
            });
    return s;
  }

  private Set<Register.Virtual> def(Instruction insn) {
    Set<Register.Virtual> s = new HashSet<>();
    if (insn.def() instanceof Register.Virtual dv) {
      s.add(dv);
    }
    return s;
  }

  private static class InterferenceGraph {
    Map<Register.Virtual, Set<Register.Virtual>> edges = new HashMap<>();
  }

  private InterferenceGraph buildInterferenceGraph(CFG cfg) {
    System.out.println("    [buildInterferenceGraph] Initializing virtual registers...");
    InterferenceGraph ig = new InterferenceGraph();
    cfg.nodes.forEach(
        node -> {
          node.insn
              .registers()
              .forEach(
                  r -> {
                    if (r instanceof Register.Virtual vr) {
                      ig.edges.putIfAbsent(vr, new HashSet<>());
                    }
                  });
        });
    System.out.println("    [buildInterferenceGraph] Adding interference edges...");
    cfg.nodes.forEach(
        node -> {
          List<Register.Virtual> live = new ArrayList<>(node.liveOut);
          for (int i = 0; i < live.size(); i++) {
            for (int j = i + 1; j < live.size(); j++) {
              Register.Virtual a = live.get(i);
              Register.Virtual b = live.get(j);
              if (!a.equals(b)) {
                ig.edges.get(a).add(b);
                ig.edges.get(b).add(a);
              }
            }
          }
          Set<Register.Virtual> defs = def(node.insn);
          defs.forEach(
              d ->
                  node.liveOut.forEach(
                      o -> {
                        if (!d.equals(o)) {
                          ig.edges.get(d).add(o);
                          ig.edges.get(o).add(d);
                        }
                      }));
        });
    return ig;
  }

  private static class ColorResult {
    Map<Register.Virtual, Register> colorMap = new HashMap<>();
    Set<Register.Virtual> spilled = new HashSet<>();
  }

  private ColorResult chaitinColor(InterferenceGraph ig, List<Register> allowed) {
    System.out.println("    [chaitinColor] Starting simplify phase...");
    ColorResult cr = new ColorResult();
    int k = allowed.size();
    Set<Register.Virtual> removed = new HashSet<>();
    Stack<Register.Virtual> stack = new Stack<>();
    boolean progress = true;
    while (true) {
      progress = false;
      for (Register.Virtual v : ig.edges.keySet()) {
        if (!removed.contains(v) && !cr.spilled.contains(v)) {
          long degree =
              ig.edges.get(v).stream()
                  .filter(n -> !removed.contains(n) && !cr.spilled.contains(n))
                  .count();
          if (degree < k) {
            stack.push(v);
            removed.add(v);
            progress = true;
            System.out.println("      [Simplify] Pushed " + v + " (degree=" + degree + ")");
            break;
          }
        }
      }
      if (!progress) {
        Optional<Register.Virtual> candidate =
            ig.edges.keySet().stream()
                .filter(v -> !removed.contains(v) && !cr.spilled.contains(v))
                .findAny();
        if (candidate.isPresent()) {
          Register.Virtual toSpill = candidate.get();
          cr.spilled.add(toSpill);
          removed.add(toSpill);
          System.out.println(
              "      [Simplify] Spilling " + toSpill + " (unable to simplify further)");
          continue;
        } else {
          break;
        }
      }
    }
    System.out.println("    [chaitinColor] Assignment phase...");
    while (!stack.isEmpty()) {
      Register.Virtual v = stack.pop();
      Set<Register> used = new HashSet<>();
      ig.edges
          .get(v)
          .forEach(
              nb -> {
                if (cr.colorMap.containsKey(nb)) {
                  used.add(cr.colorMap.get(nb));
                }
              });
      Register chosen = null;
      for (Register r : allowed) {
        if (!used.contains(r)) {
          chosen = r;
          break;
        }
      }
      if (chosen == null) {
        cr.spilled.add(v);
        System.out.println("      [Assign] Could not color " + v + "; marking as spilled.");
      } else {
        cr.colorMap.put(v, chosen);
        System.out.println("      [Assign] " + v + " assigned " + chosen);
      }
    }
    return cr;
  }

  private AssemblyProgram.TextSection rewriteSection(
      AssemblyProgram.TextSection oldSec, ColorResult cr) {
    System.out.println("    [rewriteSection] Rewriting instructions...");
    AssemblyProgram.TextSection newSec = new AssemblyProgram.TextSection();
    oldSec.items.forEach(
        item -> {
          switch (item) {
            case Instruction insn -> {
              if (insn == Instruction.Nullary.pushRegisters) {
                expandPush(newSec, cr.spilled);
              } else if (insn == Instruction.Nullary.popRegisters) {
                expandPop(newSec, cr.spilled);
              } else {
                rewriteInstruction(newSec, insn, cr);
              }
            }
            case AssemblyTextItem ati -> newSec.emit(ati);
            default -> {}
          }
        });
    return newSec;
  }

  private void expandPush(AssemblyProgram.TextSection out, Set<Register.Virtual> spilled) {
    System.out.println("      [expandPush] Expanding pushRegisters for spilled vregs: " + spilled);
    List<Register.Virtual> sorted = new ArrayList<>(spilled);
    sorted.sort(Comparator.comparing(v -> v.name));
    sorted.forEach(
        vr -> {
          Label spillLbl = getSpillLabel(vr);
          out.emit(OpCode.LA, Register.Arch.t0, spillLbl);
          out.emit(OpCode.LW, Register.Arch.t0, Register.Arch.t0, 0);
          out.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4);
          out.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, 0);
          System.out.println("        Pushed spilled " + vr);
        });
  }

  private void expandPop(AssemblyProgram.TextSection out, Set<Register.Virtual> spilled) {
    System.out.println("      [expandPop] Expanding popRegisters for spilled vregs: " + spilled);
    List<Register.Virtual> sorted = new ArrayList<>(spilled);
    sorted.sort(Comparator.comparing(v -> v.name));
    Collections.reverse(sorted);
    sorted.forEach(
        vr -> {
          Label spillLbl = getSpillLabel(vr);
          out.emit(OpCode.LW, Register.Arch.t0, Register.Arch.sp, 0);
          out.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, 4);
          out.emit(OpCode.LA, Register.Arch.t1, spillLbl);
          out.emit(OpCode.SW, Register.Arch.t0, Register.Arch.t1, 0);
          System.out.println("        Popped spilled " + vr);
        });
  }

  private void rewriteInstruction(
      AssemblyProgram.TextSection out, Instruction insn, ColorResult cr) {
    List<Register> ephemerals = new ArrayList<>(List.of(SPILL_TEMP_1, SPILL_TEMP_2, SPILL_TEMP_3));
    Map<Register, Register> regMap = new HashMap<>();
    insn.registers()
        .forEach(
            r -> {
              if (r instanceof Register.Virtual vr && !cr.spilled.contains(vr)) {
                Register color = cr.colorMap.get(vr);
                if (color == null) {
                  throw new RuntimeException("No physical register assigned for " + vr);
                }
                regMap.put(r, color);
              }
            });
    insn.uses()
        .forEach(
            r -> {
              if (r instanceof Register.Virtual vr && cr.spilled.contains(vr)) {
                if (ephemerals.isEmpty()) {
                  throw new RuntimeException(
                      "[rewriteInstruction] Not enough ephemeral registers for spilled use " + vr);
                }
                Register tmp = ephemerals.remove(0);
                loadSpill(out, vr, tmp);
                regMap.put(r, tmp);
              }
            });
    Register.Virtual defVr = null;
    if (insn.def() instanceof Register.Virtual vr && cr.spilled.contains(vr)) {
      defVr = vr;
      if (ephemerals.isEmpty()) {
        throw new RuntimeException(
            "[rewriteInstruction] Not enough ephemeral registers for spilled def " + vr);
      }
      Register tmp = ephemerals.remove(0);
      regMap.put(vr, tmp);
    }
    Instruction newInsn = insn.rebuild(regMap);
    if (newInsn instanceof Instruction.LoadAddress la) {
      if (la.label.name.startsWith("v")) {
        newInsn =
            new Instruction.TernaryArithmetic(
                OpCode.ADDU, la.dst, Register.Arch.zero, Register.Arch.zero);
      }
    }
    out.emit(newInsn);
    System.out.println("      [rewrite] Emitted: " + newInsn);
    if (defVr != null) {
      Register tmp = regMap.get(defVr);
      storeSpill(out, defVr, tmp);
    }
  }

  private void loadSpill(AssemblyProgram.TextSection out, Register.Virtual vr, Register dest) {
    Label slot = getSpillLabel(vr);
    out.emit(OpCode.LA, dest, slot);
    out.emit(OpCode.LW, dest, dest, 0);
    System.out.println("        Loaded spill for " + vr + " into " + dest);
  }

  private void storeSpill(AssemblyProgram.TextSection out, Register.Virtual vr, Register src) {
    Label slot = getSpillLabel(vr);
    out.emit(OpCode.LA, Register.Arch.t1, slot);
    out.emit(OpCode.SW, src, Register.Arch.t1, 0);
    System.out.println("        Stored spill for " + vr + " from " + src);
  }

  private Label getSpillLabel(Register.Virtual vr) {
    return spillLabelMap.computeIfAbsent(
        vr, v -> Label.get("spill_" + v.name + "_" + (spillLabelCounter++)));
  }

  private void debugPrintLiveness(String title, CFG cfg) {
    System.out.println("DEBUG: " + title);
    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      System.out.println("  Node[" + i + "]: " + node.insn);
      System.out.println("    liveIn: " + node.liveIn);
      System.out.println("    liveOut: " + node.liveOut);
    }
  }

  private void debugPrintInterference(InterferenceGraph ig) {
    System.out.println("DEBUG: Interference Graph");
    ig.edges.forEach((vr, set) -> System.out.println("  " + vr + " -> " + set));
  }

  private void debugPrintColorResult(ColorResult cr) {
    System.out.println("DEBUG: Color Result");
    System.out.println("  Assignments:");
    cr.colorMap.forEach((vr, reg) -> System.out.println("    " + vr + " -> " + reg));
    System.out.println("  Spilled: " + cr.spilled);
  }
}
