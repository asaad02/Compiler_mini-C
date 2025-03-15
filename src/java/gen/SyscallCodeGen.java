package gen;

import gen.asm.*;
import java.util.Set;

// Generates assembly code for system calls
public class SyscallCodeGen {
  private static final Set<String> SYSCALLS =
      Set.of("print_i", "print_c", "print_s", "read_i", "read_c", "mcmalloc");

  /** Checks if a function is a valid system call. */
  public static boolean isSyscall(String name) {
    return SYSCALLS.contains(name);
  }

  public static void generateSyscall(
      AssemblyProgram.TextSection text, String syscall, Register arg) {
    if (!SYSCALLS.contains(syscall)) {
      throw new UnsupportedOperationException(
          "[SyscallCodeGen] ERROR: Unsupported syscall: " + syscall);
    }

    switch (syscall) {
      case "print_i" -> handlePrintInteger(text, arg);
      case "print_c" -> handlePrintChar(text, arg);
      case "print_s" -> handlePrintString(text, arg);
      case "read_i" -> handleReadInteger(text);
      case "read_c" -> handleReadChar(text);
      case "mcmalloc" -> handleMemoryAlloc(text, arg);
    }
  }

  private static void handlePrintInteger(AssemblyProgram.TextSection text, Register arg) {
    ensureArgNotNull("print_i", arg);
    text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    text.emit(OpCode.LI, Register.Arch.v0, 1); // Syscall code for print integer
    text.emit(OpCode.SYSCALL);
  }

  private static void handlePrintChar(AssemblyProgram.TextSection text, Register arg) {
    ensureArgNotNull("print_c", arg);
    text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    text.emit(OpCode.LI, Register.Arch.v0, 11); // Syscall code for print char
    text.emit(OpCode.SYSCALL);
  }

  private static void handlePrintString(AssemblyProgram.TextSection text, Register arg) {
    ensureArgNotNull("print_s", arg);
    text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    text.emit(OpCode.LI, Register.Arch.v0, 4); // Syscall code for print string
    text.emit(OpCode.SYSCALL);
  }

  private static void handleReadInteger(AssemblyProgram.TextSection text) {
    text.emit(OpCode.LI, Register.Arch.v0, 5); // Syscall code for read integer
    text.emit(OpCode.SYSCALL);

    // Ensure valid input
    Label validInput = Label.create();
    Label endLabel = Label.create();

    text.emit(OpCode.BNEZ, Register.Arch.v0, validInput); // If input is valid, skip setting default
    text.emit(OpCode.LI, Register.Arch.v0, 0); // Default to 0 if input is invalid
    text.emit(OpCode.J, endLabel);

    text.emit(validInput);
    text.emit(endLabel);
  }

  private static void handleReadChar(AssemblyProgram.TextSection text) {
    text.emit(OpCode.LI, Register.Arch.v0, 12); // Syscall code for read char
    text.emit(OpCode.SYSCALL);
  }

  private static void handleMemoryAlloc(AssemblyProgram.TextSection text, Register arg) {
    ensureArgNotNull("mcmalloc", arg);
    text.emit(OpCode.ADDU, Register.Arch.a0, arg, Register.Arch.zero);
    text.emit(OpCode.LI, Register.Arch.v0, 9); // Syscall code for memory allocation
    text.emit(OpCode.SYSCALL);

    // Ensure memory allocation succeeded
    Label validAlloc = Label.create();
    Label endLabel = Label.create();

    text.emit(OpCode.BNEZ, Register.Arch.v0, validAlloc); // If allocation is valid, skip default
    text.emit(OpCode.LI, Register.Arch.v0, 0); // Set to NULL (0) if allocation fails
    text.emit(OpCode.J, endLabel);

    text.emit(validAlloc);
    text.emit(endLabel);
  }

  private static void ensureArgNotNull(String syscall, Register arg) {
    if (arg == null) {
      throw new IllegalArgumentException(
          "[SyscallCodeGen] ERROR: Missing argument for syscall: " + syscall);
    }
  }
}
