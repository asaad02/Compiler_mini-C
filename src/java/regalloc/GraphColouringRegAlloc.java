package regalloc;

import gen.asm.*;
import java.util.*;
import java.util.stream.Collectors;

public class GraphColouringRegAlloc implements AssemblyPass {

  public static final GraphColouringRegAlloc INSTANCE = new GraphColouringRegAlloc();

  // Allocatable registers and reserve t8 and t9 for spills
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
          Register.Arch.s0,
          Register.Arch.s1,
          Register.Arch.s2,
          Register.Arch.s3,
          Register.Arch.s4,
          Register.Arch.s5,
          Register.Arch.s6,
          Register.Arch.s7);

  // Dedicated registers for spill operations.
  private static final Register SPILL_LOAD_REG = Register.Arch.t8;
  private static final Register SPILL_STORE_REG = Register.Arch.t9;

  // For each text section we compute the total spill size and the offset for each
  private final Map<AssemblyProgram.TextSection, Integer> spillSizes = new HashMap<>();
  private final Map<AssemblyProgram.TextSection, Map<Register.Virtual, Integer>> spillOffsets =
      new HashMap<>();

  // Precolored mappings for promoted registers
  private static final Map<Register.Virtual, Register> precoloredMapping = new HashMap<>();
  private static int nextPrecoloredIndex = 0;

  // Unique label mapping per text section.
  // generate labels using the virtual register's name with a prefix unique to the section.
  private final Map<Register.Virtual, Label> vregLabelMap = new HashMap<>();
  // Global set to keep track of all virtual register labels (for emitting into the data section)
  private final Set<Label> globalVregLabels = new HashSet<>();

  private GraphColouringRegAlloc() {}

  /**
   * Generates a unique label for a given virtual register in the context of a specific text
   * section. Uses the absolute value of the section's hash code to avoid dashes.
   */
  private Label getUniqueLabelForVreg(Register.Virtual vr, AssemblyProgram.TextSection section) {
    String uniquePrefix = "fun" + Math.abs(section.hashCode()) + "_";
    return Label.get(uniquePrefix + vr.toString());
  }

  /** Populate the mapping for all virtual registers found in a text section. */
  private void collectVirtualRegisters(AssemblyProgram.TextSection section) {
    for (AssemblyItem item : section.items) {
      if (item instanceof Instruction insn) {
        for (Register r : insn.registers()) {
          if (r instanceof Register.Virtual vr) {
            vregLabelMap.putIfAbsent(vr, getUniqueLabelForVreg(vr, section));
          }
        }
      }
    }
    globalVregLabels.addAll(vregLabelMap.values());
  }

  @Override
  public AssemblyProgram apply(AssemblyProgram program) {
    AssemblyProgram outProg = new AssemblyProgram();
    // Copy data section as is.
    outProg.dataSection.items.addAll(program.dataSection.items);

    // Process each text section (function) separately.
    for (AssemblyProgram.TextSection section : program.textSections) {
      // Clear the virtual-register mapping for this section.
      vregLabelMap.clear();
      collectVirtualRegisters(section);

      // Build CFG and compute liveness.
      CFG cfg = buildCFG(section);
      doLiveness(cfg);
      Set<Instruction> deadInsts = computeDeadInstructions(cfg);
      Map<Register.Virtual, Integer> frequency = computeFrequency(cfg);
      InterferenceGraph ig = buildInterferenceGraph(cfg);

      // Apply Chaitin's algorithm.
      ColorResult cr = chaitinColor(ig, ALLOCATABLE, frequency);

      // Compute spill offsets for spilled registers (each occupies 4 bytes).
      int spillSize = cr.spilled.size() * 4;
      spillSizes.put(section, spillSize);
      Map<Register.Virtual, Integer> offsets = new HashMap<>();
      int offset = 0;
      for (Register.Virtual vr : cr.spilled) {
        offsets.put(vr, offset);
        offset += 4;
      }
      spillOffsets.put(section, offsets);

      // Rewrite instructions: insert spill loads/stores and replace virtual registers.
      AssemblyProgram.TextSection newSection = rewriteSection(section, cr, deadInsts);
      outProg.emitTextSection(newSection);
    }

    // Emit labels for spilled registers and all virtual registers in the data section.
    for (Label lbl : globalVregLabels) {
      outProg.dataSection.emit(new Directive("align 2"));
      outProg.dataSection.emit(lbl);
      outProg.dataSection.emit(new Directive("space 4"));
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
    Map<Label, Integer> labelMap = new HashMap<>();
  }

  private CFG buildCFG(AssemblyProgram.TextSection section) {
    CFG cfg = new CFG();
    List<Instruction> instructions = new ArrayList<>();
    // Build list of instructions and record labels.
    for (AssemblyItem item : section.items) {
      if (item instanceof Label lbl) {
        cfg.labelMap.put(lbl, instructions.size());
      } else if (item instanceof Instruction insn) {
        instructions.add(insn);
      }
    }
    // Create CFG nodes.
    for (Instruction insn : instructions) {
      cfg.nodes.add(new CFGNode(insn));
    }
    // Connect successors.
    for (int i = 0; i < cfg.nodes.size(); i++) {
      CFGNode node = cfg.nodes.get(i);
      Instruction insn = node.insn;
      // Default fall-through.
      if (i + 1 < cfg.nodes.size()) {
        node.successors.add(cfg.nodes.get(i + 1));
      }
      // Handle jumps and branches.
      if (insn instanceof Instruction.Jump j) {
        Integer target = cfg.labelMap.get(j.label);
        if (target != null) {
          node.successors.clear();
          node.successors.add(cfg.nodes.get(target));
        }
      } else if (insn instanceof Instruction.BinaryBranch bb) {
        Integer target = cfg.labelMap.get(bb.label);
        if (target != null) {
          node.successors.add(cfg.nodes.get(target));
        }
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
        Set<Register.Virtual> newOut = new HashSet<>();
        for (CFGNode succ : node.successors) {
          newOut.addAll(succ.liveIn);
        }
        Set<Register.Virtual> newIn = new HashSet<>(getUses(node.insn));
        Register def = node.insn.def();
        newIn.addAll(
            newOut.stream()
                .filter(v -> !(def != null && def.equals(v)))
                .collect(Collectors.toSet()));
        if (!newIn.equals(node.liveIn) || !newOut.equals(node.liveOut)) {
          node.liveIn = newIn;
          node.liveOut = newOut;
          changed = true;
        }
      }
    } while (changed);
  }

  private Set<Register.Virtual> getUses(Instruction insn) {
    return insn.uses().stream()
        .filter(r -> r instanceof Register.Virtual)
        .map(r -> (Register.Virtual) r)
        .collect(Collectors.toSet());
  }

  private static class InterferenceGraph {
    Map<Register.Virtual, Set<Register.Virtual>> edges = new HashMap<>();

    void addEdge(Register.Virtual a, Register.Virtual b) {
      edges.computeIfAbsent(a, k -> new HashSet<>()).add(b);
      edges.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }
  }

  private InterferenceGraph buildInterferenceGraph(CFG cfg) {
    InterferenceGraph ig = new InterferenceGraph();
    for (CFGNode node : cfg.nodes) {
      for (Register.Virtual a : node.liveOut) {
        for (Register.Virtual b : node.liveOut) {
          if (!a.equals(b)) {
            ig.addEdge(a, b);
          }
        }
      }
      Register def = node.insn.def();
      if (def instanceof Register.Virtual dv) {
        for (Register.Virtual live : node.liveOut) {
          ig.addEdge(dv, live);
        }
      }
    }
    return ig;
  }

  private static class ColorResult {
    Map<Register.Virtual, Register> colorMap = new HashMap<>();
    Set<Register.Virtual> spilled = new HashSet<>();
  }

  private ColorResult chaitinColor(
      InterferenceGraph ig, List<Register> colors, Map<Register.Virtual, Integer> freq) {
    ColorResult cr = new ColorResult();
    Stack<Register.Virtual> stack = new Stack<>();
    Set<Register.Virtual> removed = new HashSet<>();
    int K = colors.size();

    while (true) {
      Optional<Register.Virtual> candidate =
          ig.edges.keySet().stream()
              .filter(v -> !removed.contains(v) && !cr.spilled.contains(v))
              .filter(
                  v ->
                      ig.edges.get(v).stream()
                              .filter(n -> !removed.contains(n) && !cr.spilled.contains(n))
                              .count()
                          < K)
              .findFirst();
      if (candidate.isPresent()) {
        Register.Virtual v = candidate.get();
        stack.push(v);
        removed.add(v);
      } else {
        Optional<Register.Virtual> spillCandidate =
            ig.edges.keySet().stream()
                .filter(v -> !removed.contains(v) && !cr.spilled.contains(v))
                .max(
                    Comparator.comparingDouble(
                        v -> freq.getOrDefault(v, 1) / (ig.edges.get(v).size() + 1.0)));
        if (spillCandidate.isPresent()) {
          Register.Virtual v = spillCandidate.get();
          cr.spilled.add(v);
          stack.push(v);
          removed.add(v);
        } else {
          break;
        }
      }
    }

    while (!stack.isEmpty()) {
      Register.Virtual v = stack.pop();
      Set<Register> forbidden =
          ig.edges.get(v).stream()
              .map(cr.colorMap::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
      Optional<Register> chosen = colors.stream().filter(c -> !forbidden.contains(c)).findFirst();
      if (chosen.isPresent()) {
        cr.colorMap.put(v, chosen.get());
      } else {
        cr.spilled.add(v);
      }
    }
    return cr;
  }

  private AssemblyProgram.TextSection rewriteSection(
      AssemblyProgram.TextSection section, ColorResult cr, Set<Instruction> deadInsts) {
    AssemblyProgram.TextSection newSec = new AssemblyProgram.TextSection();
    int spillSize = spillSizes.get(section);
    Map<Register.Virtual, Integer> offsets = spillOffsets.get(section);

    // Prologue: adjust the stack pointer if there are spills.
    if (spillSize > 0) {
      newSec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -spillSize);
    }

    for (AssemblyItem item : section.items) {
      if (item instanceof Instruction insn && !deadInsts.contains(insn)) {
        if (insn == Instruction.Nullary.pushRegisters) {
          expandPushRegisters(newSec, cr.colorMap.values());
        } else if (insn == Instruction.Nullary.popRegisters) {
          expandPopRegisters(newSec, cr.colorMap.values());
        } else {
          rewriteInstruction(newSec, insn, cr, offsets);
        }
      } else if (!(item instanceof Instruction)) {
        newSec.emit((AssemblyTextItem) item);
      }
    }

    // Epilogue: restore the stack pointer.
    if (spillSize > 0) {
      newSec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, spillSize);
    }
    return newSec;
  }

  private void rewriteInstruction(
      AssemblyProgram.TextSection sec,
      Instruction insn,
      ColorResult cr,
      Map<Register.Virtual, Integer> offsets) {
    Map<Register, Register> mapping = new HashMap<>();

    for (Register r : insn.uses()) {
      if (r instanceof Register.Virtual vr) {
        if (cr.spilled.contains(vr)) {
          Register tmp = getSpillTemp(vr, mapping);
          loadFromStack(sec, vr, tmp, offsets.get(vr));
          mapping.put(vr, tmp);
        } else {
          mapping.put(vr, cr.colorMap.getOrDefault(vr, getPrecolored(vr)));
        }
      }
    }
    if (insn.def() instanceof Register.Virtual dv) {
      if (cr.spilled.contains(dv)) {
        Register tmp = getSpillTemp(dv, mapping);
        mapping.put(dv, tmp);
      } else {
        mapping.put(dv, cr.colorMap.getOrDefault(dv, getPrecolored(dv)));
      }
    }
    sec.emit(insn.rebuild(mapping));
    if (insn.def() instanceof Register.Virtual dv && cr.spilled.contains(dv)) {
      storeToStack(sec, dv, mapping.get(dv), offsets.get(dv));
    }
  }

  private Register getSpillTemp(Register.Virtual vr, Map<Register, Register> mapping) {
    return mapping.containsKey(vr)
        ? mapping.get(vr)
        : (mapping.size() % 2 == 0 ? SPILL_LOAD_REG : SPILL_STORE_REG);
  }

  private void loadFromStack(
      AssemblyProgram.TextSection sec, Register.Virtual vr, Register tmp, int offset) {
    sec.emit(OpCode.LW, tmp, Register.Arch.sp, offset);
  }

  private void storeToStack(
      AssemblyProgram.TextSection sec, Register.Virtual vr, Register tmp, int offset) {
    sec.emit(OpCode.SW, tmp, Register.Arch.sp, offset);
  }

  private void expandPushRegisters(AssemblyProgram.TextSection sec, Collection<Register> regs) {
    sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -regs.size() * 4);
    int offset = 0;
    for (Register r : regs) {
      sec.emit(OpCode.SW, r, Register.Arch.sp, offset);
      offset += 4;
    }
  }

  private void expandPopRegisters(AssemblyProgram.TextSection sec, Collection<Register> regs) {
    int offset = 0;
    for (Register r : regs) {
      sec.emit(OpCode.LW, r, Register.Arch.sp, offset);
      offset += 4;
    }
    sec.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, regs.size() * 4);
  }

  private Register getPrecolored(Register.Virtual vr) {
    return precoloredMapping.computeIfAbsent(
        vr, v -> ALLOCATABLE.get(nextPrecoloredIndex++ % ALLOCATABLE.size()));
  }

  private Set<Instruction> computeDeadInstructions(CFG cfg) {
    Set<Instruction> dead = new HashSet<>();
    for (CFGNode node : cfg.nodes) {
      Register def = node.insn.def();
      if (def instanceof Register.Virtual dv && !node.liveOut.contains(dv)) {
        dead.add(node.insn);
      }
    }
    return dead;
  }

  private Map<Register.Virtual, Integer> computeFrequency(CFG cfg) {
    Map<Register.Virtual, Integer> freq = new HashMap<>();
    for (CFGNode node : cfg.nodes) {
      for (Register r : node.insn.uses()) {
        if (r instanceof Register.Virtual vr) {
          freq.put(vr, freq.getOrDefault(vr, 0) + 1);
        }
      }
    }
    return freq;
  }
}
