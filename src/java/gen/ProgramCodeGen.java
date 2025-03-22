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

    //  pass to allocate all declared global functions
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd) {
        System.out.println("[ProgramCodeGen] Allocating function: " + fd.name);
        allocator.allocateFunction(fd);
      }
    }
    // boolean if param has pointer type
    boolean hasPointerType = false;
    // Generate all function code (but skip main for now)
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd) {
        allocator.visit(fd); // Visit all to ensure frame setup is done
        if (fd.name.equals("main")) {
          mainFunction = fd; // Save main for later
        } else {
          // System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
          // generate function if doesnt have pointer type
          for (VarDecl vd : fd.params) {
            if (vd.type instanceof PointerType) {
              // skip function if it has pointer type
              hasPointerType = true;
            }
          }

          if (!hasPointerType) {
            new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
          }
        }
      } else if (d instanceof VarDecl vd) {
        allocator.allocateGlobalVariable(vd);
      }
    }

    for (Decl d : p.decls) {
      boolean hasPointerType2 = false;
      if (d instanceof FunDef fd && !fd.name.equals("main")) {
        for (VarDecl vd : fd.params) {
          if (vd.type instanceof PointerType) {
            // skip function if it has pointer type
            hasPointerType2 = true;
          }
        }
        if (hasPointerType2) {
          new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
        }
      }
    }

    // Finally generate code for main (last in assembly, first to execute)
    if (mainFunction != null) {
      System.out.println("[ProgramCodeGen] Generating code for main()");

      new FunCodeGen(asmProg, allocator, definedFunctions).visit(mainFunction);
    }

    // Print Assembly Sections and Debug Table
    // printAssemblySections();
    // allocator.printAllMemory();
    System.out.println("[ProgramCodeGen] Program generation completed successfully.");
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
