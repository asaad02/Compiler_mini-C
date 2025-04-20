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

    // Label Creation

    VirtualLabelGen lblGen = new VirtualLabelGen();
    lblGen.collect(p);
    // methodLabels now in CodeGenContext

    // Ancestor Collection

    AncestorCollector.collect(p);
    // classAncestors now in CodeGenContext

    // Build Virtual Tables

    VirtualTableGen vtGen = new VirtualTableGen();
    vtGen.build(p);
    // vtables now in CodeGenContext
    System.out.println("[ProgramCodeGen] Verifying virtual tables...");
    for (var entry : CodeGenContext.getVTables().entrySet()) {
      String cls = entry.getKey();
      System.out.println("VTable for " + cls + ": " + entry.getValue());
    }

    // Prepare memory allocator

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

    // Emit vtables in the .data section ────────────────────────

    for (var entry : CodeGenContext.getVTables().entrySet()) {
      String cls = entry.getKey();
      // Label for the start of this class's vtable
      asmProg.dataSection.emit(Label.get("vtable_" + cls));
      // Each method label in order
      for (String methodLabel : entry.getValue().values()) {
        asmProg.dataSection.emit(new Directive("word " + Label.get(methodLabel)));
      }
    }

    // allocate functions with array or struct parameters first
    // collect all toplevel and class method names
    for (Decl d : p.decls) {
      if (d instanceof FunDef fd
          && !fd.name.equals("main")
          && (arrayParams(fd) || structParams(fd))) {
        allocator.visit(fd);
        System.out.println("[ProgramCodeGen] Generating code for function: " + fd.name);
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      } else if (d instanceof ClassDecl cd) {
        // register each method under ClassName_method_arity
        for (FunDef m : cd.methods) {
          String methodName = cd.name + "_" + m.name + "_" + m.params.size();
          definedFunctions.add(methodName);
        }
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
        // free‐standing functions use the 3‑arg ctor (no class context)
        new FunCodeGen(asmProg, allocator, definedFunctions).visit(fd);
      }
    }
    // ─── now generate code for class methods ───────────────────
    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        for (FunDef m : cd.methods) {
          System.out.println(
              "[ProgramCodeGen] Generating code for method: " + cd.name + "." + m.name);
          allocator.visit(m);
          // now supply cd.name so the code‐gens know which class’s fields to use
          new FunCodeGen(asmProg, allocator, definedFunctions, cd.name).visit(m);
        }
      }
    }

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

  // check if the function has a struct parameter
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
