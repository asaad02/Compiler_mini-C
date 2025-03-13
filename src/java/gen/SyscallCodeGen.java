package gen;

import gen.asm.*;
import java.util.Set;

/** Generates assembly code for system calls in the Mini-C language. */
public class SyscallCodeGen {
  private static final Set<String> SYSCALLS =
      Set.of("print_i", "print_c", "print_s", "read_i", "read_c", "mcmalloc");

  /** Checks if a function is a valid system call. */
  public static boolean isSyscall(String name) {
    return SYSCALLS.contains(name);
  }

  /**
   * Generates the appropriate syscall assembly instructions.
   *
   * @param text The assembly text section where the syscall should be emitted.
   * @param syscall The name of the syscall (e.g., "print_i").
   * @param arg The register containing the syscall argument (if applicable).
   */
  public static void generateSyscall(
      AssemblyProgram.TextSection text, String syscall, Register arg) {
    if (!SYSCALLS.contains(syscall)) {
      throw new UnsupportedOperationException("[SyscallCodeGen] Unsupported syscall: " + syscall);
    }

    // Ensure the argument is in the correct register ($a0) if needed
    if (arg != null) {
      text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    }

    switch (syscall) {
      case "print_i" -> {
        ensureArgNotNull(syscall, arg);
        text.emit(OpCode.LI, Register.Arch.v0, 1); // Syscall code for print integer
        text.emit(OpCode.SYSCALL);
      }
      case "print_c" -> {
        ensureArgNotNull(syscall, arg);
        text.emit(OpCode.LI, Register.Arch.v0, 11); // Syscall code for print char
        text.emit(OpCode.SYSCALL);
      }
      case "print_s" -> {
        ensureArgNotNull(syscall, arg);
        text.emit(OpCode.LI, Register.Arch.v0, 4); // Syscall code for print string
        text.emit(OpCode.SYSCALL);
      }
      case "read_i" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 5); // Syscall code for read integer
        text.emit(OpCode.SYSCALL);
        // Return value is stored in $v0
      }
      case "read_c" -> {
        text.emit(OpCode.LI, Register.Arch.v0, 12); // Syscall code for read char
        text.emit(OpCode.SYSCALL);
      }
      case "mcmalloc" -> {
        ensureArgNotNull(syscall, arg);
        text.emit(OpCode.LI, Register.Arch.v0, 9); // Syscall code for memory allocation
        text.emit(OpCode.SYSCALL);
        // The allocated memory address is stored in $v0
      }
    }
  }

  /** Ensures that syscalls requiring an argument are not called with a null argument. */
  private static void ensureArgNotNull(String syscall, Register arg) {
    if (arg == null) {
      throw new IllegalArgumentException(
          "[SyscallCodeGen] ERROR: Missing argument for syscall: " + syscall);
    }
  }
}
