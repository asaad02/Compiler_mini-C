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

    // emit function label
    textSection.emit(Label.get(functionLabel));

    // emit function label for main
    if (fd.name.equals("main")) {
      textSection.emit(new Directive("globl main"));
    }

    System.out.println("[FunCodeGen] Generating function: " + functionLabel);

    int frameSize = allocator.alignTo16(allocator.getFrameSize(fd) + 8);

    // print table of memory allocations
    allocator.printMemoryTable(fd);

    // function prologue
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -frameSize);
    textSection.emit(OpCode.SW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.SW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDU, Register.Arch.fp, Register.Arch.sp, Register.Arch.zero);
    textSection.emit(OpCode.PUSH_REGISTERS);

    // Save function parameters in the stack frame
    saveFunctionParameters(fd, textSection);

    // Generate function body
    new StmtCodeGen(asmProg, allocator, fd, definedFunctions).visit(fd.block);

    // print table of memory allocations

    allocator.printMemoryTable(fd);

    // function epilogue
    generateFunctionEpilogue(fd, textSection, frameSize);
  }

  private void saveFunctionParameters(FunDef fd, AssemblyProgram.TextSection textSection) {
    for (int i = 0; i < fd.params.size(); i++) {
      VarDecl param = fd.params.get(i);
      int offset = allocator.getLocalOffset(param);
      offset = (offset / 4) * 4;
      Type paramType = param.type;

      if (paramType instanceof StructType) {

        int structSize = allocator.computeSize(paramType);
        offset = allocator.alignTo(offset, 8);
        structSize = allocator.alignTo(structSize, 8);

        int paramOffset = allocator.getLocalOffset(param);
        for (int word = 0; word < structSize; word += 4) {
          Register temp = Register.Virtual.create();
          textSection.emit(OpCode.LW, temp, Register.Arch.sp, paramOffset + word);
          textSection.emit(OpCode.SW, temp, Register.Arch.fp, offset + word);
        }
      } else {
        if (i < 4) {
          textSection.emit(OpCode.SW, getArgReg(i), Register.Arch.fp, offset);
        } else {
          int stackOffset = allocator.alignTo16((i - 4) * 4);
          Register temp = Register.Virtual.create();
          textSection.emit(OpCode.LW, temp, Register.Arch.sp, stackOffset);
          textSection.emit(OpCode.SW, temp, Register.Arch.fp, offset);
        }
      }
    }
  }

  //
  private void generateFunctionEpilogue(
      FunDef fd, AssemblyProgram.TextSection textSection, int frameSize) {
    System.out.println("[FunCodeGen] Generating function epilogue for: " + fd.name);

    Label epilogueLabel = Label.get("func_epilogue_" + fd.name);
    textSection.emit(epilogueLabel);

    textSection.emit(OpCode.POP_REGISTERS);
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

  // Get the register for the argument at the given index
  private Register getArgReg(int index) {
    return switch (index) {
      case 0 -> Register.Arch.a0;
      case 1 -> Register.Arch.a1;
      case 2 -> Register.Arch.a2;
      case 3 -> Register.Arch.a3;
      default -> throw new IllegalArgumentException("Invalid argument index");
    };
  }

  // Generate a unique function name
  private String getUniqueFunctionName(FunDef fd) {
    return fd.name.equals("main") ? "main" : fd.name + "_" + fd.params.size();
  }
}
