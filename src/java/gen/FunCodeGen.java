package gen;

import ast.*;
import gen.asm.*;

/** A visitor that produces code for a single function declaration */
public class FunCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;

  public FunCodeGen(AssemblyProgram asmProg, MemAllocCodeGen allocator) {
    this.asmProg = asmProg;
    this.allocator = allocator;
  }

  /**
   * Generates assembly code for a function definition. Emitting a global label for main Generating
   * a function prologue stack frame setup Generating code for the function body Generating the
   * function epilogue stack frame cleanup
   */
  void visit(FunDef fd) {
    // create a new text section for the function
    AssemblyProgram.TextSection textSection = asmProg.emitNewTextSection();

    // Function prologue
    textSection.emit(new Directive("globl " + fd.name));

    // emit function label
    textSection.emit(Label.get(fd.name));

    // function Prologue (Stack Frame Setup)
    System.out.println("[FunCodeGen] Generating prologue for function: " + fd.name);

    int frameSize = allocator.getFrameSize(fd);
    // Space for $RA and $FP
    frameSize += 8;
    // ensure stack alignment to 16 bytes
    frameSize = (frameSize + 15) & ~15;

    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -frameSize);
    textSection.emit(OpCode.SW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.SW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.ADDU, Register.Arch.fp, Register.Arch.sp, Register.Arch.zero);

    // Save registers
    textSection.emit(OpCode.PUSH_REGISTERS);

    System.out.println("[FunCodeGen] Saving function parameters for: " + fd.name);
    for (int i = 0; i < fd.params.size(); i++) {
      VarDecl param = fd.params.get(i);
      int offset = allocator.getLocalOffset(param);

      if (param.type instanceof StructType || param.type instanceof ArrayType) {
        int size = allocator.computeSizeWithMask(param.type);
        for (int j = 0; j < size; j += 4) {
          // Parameters start at $fp + 8 + (i * 4)
          int srcOffset = 8 + i * 4 + j;
          textSection.emit(OpCode.LW, Register.Arch.t0, Register.Arch.fp, srcOffset);
          // Destination Local variable offset
          textSection.emit(OpCode.SW, Register.Arch.t0, Register.Arch.fp, offset + j);
        }
      } else if (i < 4) {
        textSection.emit(OpCode.SW, getArgReg(i), Register.Arch.fp, offset);
      } else {
        int stackOffset = 8 + (i - 4) * 4;
        textSection.emit(OpCode.LW, Register.Arch.t0, Register.Arch.fp, stackOffset);
        textSection.emit(OpCode.SW, Register.Arch.t0, Register.Arch.fp, offset);
      }
    }
    // generate Function Body
    System.out.println("[FunCodeGen] Generating function body for: " + fd.name);
    new StmtCodeGen(asmProg, allocator, fd).visit(fd.block);

    // print all scopes
    allocator.printScope();
    allocator.exitScope(); // Exit local variables scope
    allocator.exitScope(); // Exit parameters scope

    // function Epilogue (Stack Cleanup & Return)
    System.out.println("[FunCodeGen] Generating epilogue for function: " + fd.name);

    // If function returns a value, move it to $v0 before returning
    if (!fd.type.equals(BaseType.VOID)) {
      if (fd.type instanceof StructType) {
        int size = allocator.computeSizeWithMask(fd.type);
        for (int i = 0; i < size; i += 4) {
          textSection.emit(OpCode.LW, Register.Arch.t0, Register.Arch.fp, -size + i);
          textSection.emit(OpCode.SW, Register.Arch.t0, Register.Arch.sp, i);
        }
      }
    }

    // restore registers before function returns
    textSection.emit(OpCode.POP_REGISTERS);

    // restore frame pointer and return address
    textSection.emit(OpCode.LW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);
    textSection.emit(OpCode.LW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);

    // restore stack pointer (must be last)
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, frameSize);

    // correct return handling
    if (fd.name.equals("main")) {
      textSection.emit(OpCode.LI, Register.Arch.v0, 10);
      textSection.emit(OpCode.SYSCALL);
    } else {
      textSection.emit(OpCode.JR, Register.Arch.ra);
    }

    System.out.println("[FunCodeGen] Finished generating function: " + fd.name);
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
}
