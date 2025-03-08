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
   * Generates assembly code for a function definition. Emitting a global label for main ,
   * Generating a function prologue stack frame setup , Generating code for the function body , and
   * Generating the function epilogue stack frame cleanup
   */
  void visit(FunDef fd) {
    System.out.println("[FunCodeGen] Generating function: " + fd.name);

    // create a new text section for the function
    AssemblyProgram.TextSection textSection = asmProg.emitNewTextSection();

    // mark main as a global entry point
    if (fd.name.equals("main")) {
      textSection.emit(new Directive("globl main"));
    }

    // emit function label
    textSection.emit(Label.get(fd.name));

    // function Prologue (Stack Frame Setup)
    System.out.println("[FunCodeGen] Generating prologue for function: " + fd.name);

    int frameSize = allocator.getFrameSize(fd);
    frameSize += 8; // Space for $RA and $FP
    frameSize = (frameSize + 15) & ~15; // ✅ Ensure stack alignment to 16 bytes

    // allocate stack space
    textSection.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -frameSize);

    // save return address ($RA) and old frame pointer ($FP)
    textSection.emit(OpCode.SW, Register.Arch.ra, Register.Arch.sp, frameSize - 4);
    textSection.emit(OpCode.SW, Register.Arch.fp, Register.Arch.sp, frameSize - 8);

    // set new frame pointer ($FP = $SP)
    textSection.emit(OpCode.ADDU, Register.Arch.fp, Register.Arch.sp, Register.Arch.zero);

    // save registers used in the function
    textSection.emit(OpCode.PUSH_REGISTERS);

    // parameter Handling (Save function arguments)
    System.out.println("[FunCodeGen] Saving function parameters for: " + fd.name);
    for (int i = 0; i < fd.params.size(); i++) {
      VarDecl param = fd.params.get(i);
      int offset = allocator.getLocalOffset(param);
      if (i < 4) {
        textSection.emit(OpCode.SW, getArgumentRegister(i), Register.Arch.fp, offset);
      } else {
        int stackOffset = (i - 4) * 4;
        textSection.emit(OpCode.LW, Register.Arch.t0, Register.Arch.fp, stackOffset + 8);
        textSection.emit(OpCode.SW, Register.Arch.t0, Register.Arch.fp, offset);
      }
    }

    // generate Function Body
    System.out.println("[FunCodeGen] Generating function body for: " + fd.name);
    new StmtCodeGen(asmProg, allocator, fd).visit(fd.block);

    // function Epilogue (Stack Cleanup & Return)
    System.out.println("[FunCodeGen] Generating epilogue for function: " + fd.name);

    // If function returns a value, move it to $v0 before returning
    if (!fd.type.equals(BaseType.VOID)) {
      textSection.emit(OpCode.LW, Register.Arch.v0, Register.Arch.fp, -4);
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
      textSection.emit(OpCode.LI, Register.Arch.v0, 10); // Exit syscall
      textSection.emit(OpCode.SYSCALL);
    } else {
      textSection.emit(OpCode.JR, Register.Arch.ra);
    }

    System.out.println("[FunCodeGen] Finished generating function: " + fd.name);
  }

  /** returns the appropriate MIPS argument register ($a0 - $a3) for a given index. */
  private Register getArgumentRegister(int index) {
    return switch (index) {
      case 0 -> Register.Arch.a0;
      case 1 -> Register.Arch.a1;
      case 2 -> Register.Arch.a2;
      case 3 -> Register.Arch.a3;
      default ->
          throw new IllegalArgumentException(
              "[FunCodeGen] ERROR: Invalid argument index: " + index);
    };
  }
}
