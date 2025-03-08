package gen;

import ast.FunDef;
import ast.Program;
import gen.asm.AssemblyProgram;

/** This visitor should produce a program. */
public class ProgramCodeGen extends CodeGen {
  public ProgramCodeGen(AssemblyProgram asmProg) {
    this.asmProg = asmProg;
  }

  void generate(Program p) {
    System.out.println("[ProgramCodeGen] Starting program generation...");

    // allocate all variables
    MemAllocCodeGen allocator = new MemAllocCodeGen(asmProg);
    allocator.visit(p);

    // generate the code for each function
    p.decls.forEach(
        (d) -> {
          switch (d) {
            case FunDef fd -> {
              System.out.println("[ProgramCodeGen] Processing function: " + fd.name);
              FunCodeGen fcg = new FunCodeGen(asmProg, allocator);
              fcg.visit(fd);
            }
            default -> {} // nothing to do
          }
          System.out.println("\n\n[ProgramCodeGen] Current data section:\n\n");
          // print the current data section
          System.out.println(asmProg.dataSection);

          // print the current text section
          System.out.println(asmProg.getCurrentTextSection());
        });
    System.out.println("[ProgramCodeGen] Program generation completed successfully.");
  }
}
