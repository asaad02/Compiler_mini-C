package gen;

import ast.*;
import gen.asm.*;
import java.util.HashSet;
import java.util.Set;

/** This visitor should produce a program. */
public class ProgramCodeGen extends CodeGen {
  private final Set<String> definedFunctions = new HashSet<>();
  // Store main function if found
  private FunDef mainFunction = null;

  public ProgramCodeGen(AssemblyProgram asmProg) {
    this.asmProg = asmProg;
  }

  // Generates the assembly program from the given AST program. */
  void generate(Program p) {
    System.out.println("[ProgramCodeGen] Starting program generation...");

    MemAllocCodeGen allocator = new MemAllocCodeGen(asmProg);

    // pass to collect function names
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd) {
        String uniqueFunctionName = getUniqueFunctionName(fd);
        definedFunctions.add(uniqueFunctionName);
      }
    }

    // register struct declarations properly
    for (Decl d : p.decls) {
      if (d instanceof StructTypeDecl std) {
        if (std.structType.name == null) {
          throw new IllegalStateException(
              "[ProgramCodeGen] ERROR: Struct declaration has null name!");
        }
        System.out.println("[ProgramCodeGen] Registering struct: " + std.structType.name);
        allocator.registerStructDeclaration(std);
      }
    }

    // alocate function has array parameters
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd
          && !fd.name.equals("main")
          && (arrayParams(fd) || structParams(fd))) {
        allocator.visit(fd);
        System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      }
    }
    for (Decl d : p.decls) {
      if (d instanceof VarDecl vd) {
        allocator.allocateGlobalVariable(vd);
      }
      if (d instanceof FunDef fd) {
        System.out.println("[ProgramCodeGen] Calling MemAllocCodeGen for function: " + fd.name);
        allocator.visit(fd);
        if (fd.name.equals("main")) {
          mainFunction = fd;
          System.out.println("[ProgramCodeGen] Found main function: " + fd.name);
          System.out.println("[ProgramCodeGen] Generating code for main()");
          new FunCodeGen(asmProg, allocator, definedFunctions).visit(mainFunction);
        }
      }
    }

    // pass to Generate function code but not main
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd
          && !fd.name.equals("main")
          && !arrayParams(fd)
          && !structParams(fd)) {
        System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      }
    }

    // Print Assembly Sections and Debug Table
    // printAssemblySections();
    // allocator.printAllMemory();
    System.out.println("[ProgramCodeGen] Program generation completed successfully.");
  }

  // check if the parameter is a array type
  private boolean arrayParams(FunDef fd) {
    for (VarDecl vd : fd.params) {
      if (vd.type instanceof ArrayType) {
        return true;
      }
    }
    return false;
  }

  // check if a struct is passed as a parameter
  private boolean structParams(FunDef fd) {
    for (VarDecl vd : fd.params) {
      if (vd.type instanceof StructType) {
        return true;
      }
    }
    return false;
  }

  // function names are uniquely identified in the symbol table.
  private String getUniqueFunctionName(FunDef fd) {
    return fd.name.equals("main") ? "main" : fd.name + "_" + fd.params.size();
  }

  // Prints the generated assembly program sections for debugging.
  private void printAssemblySections() {
    System.out.println("\n[ProgramCodeGen] Current data section:\n");
    System.out.println(asmProg.dataSection);

    System.out.println("\n[ProgramCodeGen] Current text sections:\n");
    for (AssemblyProgram.TextSection section : asmProg.textSections) {
      System.out.println(section);
    }
  }
}
