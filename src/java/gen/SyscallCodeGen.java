package gen;

import gen.asm.*;
import java.util.Set;

public class SyscallCodeGen {
  private static final Set<String> SYSCALLS =
      Set.of("print_i", "print_c", "print_s", "read_i", "read_c", "mcmalloc");

  // checks if a function is a system call
  public static boolean isSyscall(String name) {
    return SYSCALLS.contains(name);
  }

  // generates the correct syscall assembly instructions
  public static void generateSyscall(
      AssemblyProgram.TextSection text, String syscall, Register arg) {

    // Ensure $a0 contains the actual value, not an address
    if (arg != null) {
      text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    }

    switch (syscall) {
      case "print_i" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 1);
        text.emit(OpCode.SYSCALL);
      }
      case "print_c" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 11);
        text.emit(OpCode.SYSCALL);
      }
      case "print_s" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 4);
        text.emit(OpCode.SYSCALL);
      }
      case "read_i" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 5);
        text.emit(OpCode.SYSCALL);
      }
      case "read_c" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 12);
        text.emit(OpCode.SYSCALL);
      }
      case "mcmalloc" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 9);
        text.emit(OpCode.SYSCALL);
      }
      default -> throw new UnsupportedOperationException("Unsupported syscall: " + syscall);
    }
  }
}
