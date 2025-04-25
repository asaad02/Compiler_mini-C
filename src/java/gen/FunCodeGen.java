package gen;

import ast.*;
import gen.asm.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A visitor that produces code for a single function declaration */
public class FunCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  // list of defined functions
  private final List<String> definedFunctions;
  private final String currentClass;

  public FunCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, Set<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = new ArrayList<>(definedFunctions);
    this.currentClass = null;
  }

  public FunCodeGen(
      AssemblyProgram asmProg,
      MemAllocCodeGen allocator,
      Set<String> definedFunctions,
      String currentClass) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = new ArrayList<>(definedFunctions);
    this.currentClass = currentClass;
  }

  void visit(FunDef fd) {
    AssemblyProgram.TextSection textSection = asmProg.emitNewTextSection();

    // pick the label for this function or method
    String functionLabel =
        (currentClass != null)
            ? CodeGenContext.getMethodLabels().get(currentClass).get(fd.name)
            : getUniqueFunctionName(fd);
    textSection.emit(Label.get(functionLabel));

    if (functionLabel.equals("main")) {
      textSection.emit(new Directive("globl main"));
    }

    System.out.println("[FunCodeGen] Generating function: " + functionLabel);

    int frameSize = allocator.alignTo16(allocator.getFrameSize(fd) + 16);

    generateFunctionPrologue(textSection, frameSize);
    saveFunctionParameters(fd, textSection, frameSize);

    // Generate the body
    new StmtCodeGen(asmProg, allocator, fd, definedFunctions, currentClass).visit(fd.block);

    // single, unique epilogue per mangled label
    generateFunctionEpilogue(functionLabel, textSection, frameSize);
  }

  /** Generates function prologue. */
  private void generateFunctionPrologue(AssemblyProgram.TextSection textSection, int frameSize) {
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -frameSize);
    textSection.emit(OpCode.SW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.SW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDU, Register.Arch.fp, Register.Arch.sp, Register.Arch.zero);
    textSection.emit(OpCode.PUSH_REGISTERS);
  }

  // Saves parameters
  private void saveFunctionParameters(
      FunDef fd, AssemblyProgram.TextSection textSection, int frameSize) {
    if (currentClass != null) {
      // methods $a0 is ‘this’, real args in $a1–$a3
      for (int i = 0; i < fd.params.size(); i++) {
        VarDecl param = fd.params.get(i);
        int localOffset = allocator.getLocalOffset(param);
        Register src = getArgReg(i + 1);
        textSection.emit(OpCode.SW, src, Register.Arch.fp, localOffset);
      }
      textSection.emit(OpCode.ADDU, Register.Arch.s0, Register.Arch.a0, Register.Arch.zero);
    } else {
      int paramStackOffset = frameSize;
      for (int i = 0; i < fd.params.size(); i++) {
        VarDecl param = fd.params.get(i);
        int localOffset = allocator.getLocalOffset(param.name);
        Type paramType = param.type;

        if (paramType instanceof StructType) {
          int structSize = allocator.alignTo(allocator.computeSize(paramType), 8);
          for (int w = 0; w < structSize; w += 4) {
            Register tmp = Register.Virtual.create();
            textSection.emit(OpCode.LW, tmp, Register.Arch.fp, paramStackOffset + w);
            textSection.emit(OpCode.SW, tmp, Register.Arch.fp, localOffset + w);
          }
          paramStackOffset += allocator.alignTo(structSize, 8);

        } else if (paramType instanceof ArrayType) {
          Register tmp = Register.Virtual.create();
          textSection.emit(OpCode.LW, tmp, Register.Arch.fp, paramStackOffset);
          textSection.emit(OpCode.SW, tmp, Register.Arch.fp, localOffset);
          paramStackOffset += 4;

        } else {
          Register tmp = Register.Virtual.create();
          textSection.emit(OpCode.LW, tmp, Register.Arch.fp, paramStackOffset);
          textSection.emit(OpCode.SW, tmp, Register.Arch.fp, localOffset);
          paramStackOffset += 4;
        }
      }
    }
  }

  /** Generates function epilogue under a unique mangled label. */
  private void generateFunctionEpilogue(
      String functionLabel, AssemblyProgram.TextSection textSection, int frameSize) {
    System.out.println("[FunCodeGen] Generating epilogue for " + functionLabel);
    Label epilogueLabel = Label.get(functionLabel + "_epilogue");
    textSection.emit(epilogueLabel);

    textSection.emit(OpCode.POP_REGISTERS);
    textSection.emit(OpCode.LW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.LW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, frameSize);

    if (functionLabel.equals("main")) {
      textSection.emit(OpCode.LI, Register.Arch.v0, 10);
      textSection.emit(OpCode.SYSCALL);
    } else {
      textSection.emit(OpCode.JR, Register.Arch.ra);
    }
  }

  private Register getArgReg(int index) {
    return switch (index) {
      case 0 -> Register.Arch.a0;
      case 1 -> Register.Arch.a1;
      case 2 -> Register.Arch.a2;
      case 3 -> Register.Arch.a3;
      default -> throw new IllegalArgumentException("Invalid argument index");
    };
  }

  private String getUniqueFunctionName(FunDef fd) {
    return fd.name.equals("main") ? "main" : fd.name + "_" + fd.params.size();
  }
}
