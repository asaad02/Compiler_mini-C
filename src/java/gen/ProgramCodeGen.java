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

    // Pass to collect function names.
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd) {
        String uniqueFunctionName = getUniqueFunctionName(fd);
        definedFunctions.add(uniqueFunctionName);
      }
    }

    // Register struct declarations properly.
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

    // Run a preliminary pass to promote eligible variables for each function.
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd) {
        promoteVariables(fd);
      }
    }

    // Allocate for functions with array or struct parameters.
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd
          && !fd.name.equals("main")
          && (arrayParams(fd) || structParams(fd))) {
        allocator.visit(fd);
        System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      }
    }
    // Allocate globals and then process each function.
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

    // Generate code for remaining functions.
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd
          && !fd.name.equals("main")
          && !arrayParams(fd)
          && !structParams(fd)) {
        System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      }
    }

    // printAssemblySections();
    System.out.println("[ProgramCodeGen] Program generation completed successfully.");
  }

  private void promoteVariables(FunDef fd) {
    // Process local variable declarations in the function's block.
    for (VarDecl vd : fd.block.vds) {
      if (!(vd.type instanceof ArrayType)
          && !(vd.type instanceof StructType)
          && !(vd.type instanceof PointerType)) {
        for (Stmt e : fd.block.stmts) {
          if (e instanceof ExprStmt es && es.expr instanceof VarExpr ve) {
            if (ve.name.equals(vd.name)) {
              System.out.println(
                  "[Promotion] Variable '" + vd.name + "' is used with address-of operator.");
              return;
            }
          }
        }
        vd.promoteToRegister = true;
        System.out.println(
            "[Promotion] Marking variable '" + vd.name + "' for register promotion.");
      }
    }
  }

  // check if the parameter is an array type
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
