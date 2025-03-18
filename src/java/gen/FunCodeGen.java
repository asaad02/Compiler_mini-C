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

  public FunCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, Set<String> definedFunctions) {
    this.asmProg = asmProg;
    // Store allocator
    this.allocator = allocator;
    // convert set to list
    this.definedFunctions = new ArrayList<>(definedFunctions);
  }

  void visit(FunDef fd) {
    AssemblyProgram.TextSection textSection = asmProg.emitNewTextSection();

    // create a unique function label
    String functionLabel = getUniqueFunctionName(fd);
    textSection.emit(Label.get(functionLabel));

    if (fd.name.equals("main")) {
      textSection.emit(new Directive("globl main"));
    }

    System.out.println("[FunCodeGen] Generating function: " + functionLabel);

    int frameSize = allocator.alignTo16(allocator.getFrameSize(fd) + 16);

    generateFunctionPrologue(textSection, frameSize);
    saveFunctionParameters(fd, textSection, frameSize);

    // Generate function body
    new StmtCodeGen(asmProg, allocator, fd, definedFunctions).visit(fd.block);
    // Generate function epilogue
    generateFunctionEpilogue(fd, textSection, frameSize);
  }

  /** Generates function prologue */
  private void generateFunctionPrologue(AssemblyProgram.TextSection textSection, int frameSize) {
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -frameSize);
    textSection.emit(OpCode.SW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.SW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDU, Register.Arch.fp, Register.Arch.sp, Register.Arch.zero);
    textSection.emit(OpCode.PUSH_REGISTERS);
    for (int i = 0; i < 4; i++) {
      textSection.emit(OpCode.SW, getArgReg(i), Register.Arch.sp, frameSize - (12 + (i * 4)));
    }
  }

  private void saveFunctionParameters(
      FunDef fd, AssemblyProgram.TextSection textSection, int frameSize) {
    int paramStackOffset = frameSize;

    for (int i = 0; i < fd.params.size(); i++) {
      VarDecl param = fd.params.get(i);
      int localOffset = allocator.getLocalOffset(param.name);
      Type paramType = param.type;

      if (paramType instanceof StructType) {
        // Structs are passed by copy, copy each word manually
        int structSize = allocator.computeSize(paramType);
        for (int word = 0; word < structSize; word += 4) {
          Register temp = Register.Virtual.create();
          textSection.emit(OpCode.LW, temp, Register.Arch.fp, paramStackOffset + word);
          textSection.emit(OpCode.SW, temp, Register.Arch.fp, localOffset + word);
        }
        paramStackOffset += allocator.alignTo(structSize, 8);
      } else if (paramType instanceof ArrayType) {
        // For array parameters store the argument register value
        if (i < 4) {
          textSection.emit(OpCode.SW, getArgReg(i), Register.Arch.fp, localOffset);
        } else {
          Register temp = Register.Virtual.create();
          textSection.emit(OpCode.LW, temp, Register.Arch.fp, paramStackOffset);
          textSection.emit(OpCode.SW, temp, Register.Arch.fp, localOffset);
          paramStackOffset += 4;
        }
        // Store array dimensions
        int strideOffset = 4;
        int stride = 1;
        ArrayType at = (ArrayType) paramType;
        for (int dim = at.dimensions.size() - 1; dim >= 0; dim--) {
          stride *= at.dimensions.get(dim);
          Register strideReg = Register.Virtual.create();
          textSection.emit(OpCode.LI, strideReg, stride);
          textSection.emit(OpCode.SW, strideReg, Register.Arch.fp, localOffset + strideOffset);
          strideOffset += 4;
        }
      } else {
        if (i < 4) {
          textSection.emit(OpCode.SW, getArgReg(i), Register.Arch.fp, localOffset);
        } else {
          Register temp = Register.Virtual.create();
          textSection.emit(OpCode.LW, temp, Register.Arch.fp, paramStackOffset);
          textSection.emit(OpCode.SW, temp, Register.Arch.fp, localOffset);
          paramStackOffset += 4;
        }
      }
    }
  }

  /** Generates function epilogue */
  private void generateFunctionEpilogue(
      FunDef fd, AssemblyProgram.TextSection textSection, int frameSize) {
    System.out.println("[FunCodeGen] Generating function epilogue for: " + fd.name);

    Label epilogueLabel = Label.get("func_epilogue_" + fd.name);
    textSection.emit(epilogueLabel);

    textSection.emit(OpCode.POP_REGISTERS);
    for (int i = 0; i < 4; i++) {
      textSection.emit(OpCode.LW, getArgReg(i), Register.Arch.fp, -12 - (i * 4));
    }
    textSection.emit(OpCode.LW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.LW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, frameSize);

    if (fd.name.equals("main")) {
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
