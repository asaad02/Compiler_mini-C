package sem;

import ast.*;
import java.util.*;

/**
 * Function and variable declarations before use. proper function definitions matching prior
 * declarations. no duplicate declarations in the same scope. shadowing detection. valid function
 * calls.
 */
public class NameAnalyzer extends BaseSemanticAnalyzer {

  // Current scope being analyzed
  private Scope currentScope;

  // List of built-in functions that will be valid
  /*
  * void print_s(char* s);
  * void print_i(int i);
  * void print_c(char c);
  * char read_c();
  * int read_i();
  * void* mcmalloc(int size);

  */
  private static final List<FunDecl> BUILT_IN_FUNCTIONS =
      List.of(
          new FunDecl(
              BaseType.VOID, "print_s", List.of(new VarDecl(new PointerType(BaseType.CHAR), "s"))),
          new FunDecl(BaseType.VOID, "print_i", List.of(new VarDecl(BaseType.INT, "i"))),
          new FunDecl(BaseType.VOID, "print_c", List.of(new VarDecl(BaseType.CHAR, "c"))),
          new FunDecl(BaseType.CHAR, "read_c", List.of()),
          new FunDecl(BaseType.INT, "read_i", List.of()),
          new FunDecl(
              new PointerType(BaseType.VOID),
              "mcmalloc",
              List.of(new VarDecl(BaseType.INT, "size"))));

  public NameAnalyzer() {
    // Initialize the global scope and register built-in functions
    this.currentScope = new Scope(null);
    for (FunDecl f : BUILT_IN_FUNCTIONS) {
      currentScope.put(new FunSymbol(f));
    }
  }

  /** visits AST nodes and ensures correct name analysis. */
  public void visit(ASTNode node) {
    switch (node) {
      // if the node is null, throw an exception
      case null -> throw new IllegalStateException("Unexpected null value");

      // if the node is a program, visit the declarations in order
      case Program p -> {
        // Visit each declaration in the program
        for (ASTNode decl : p.decls) {
          if (decl instanceof FunDecl) {
            visit(decl);
          }
        }
        for (ASTNode decl : p.decls) {
          if (decl instanceof FunDef) {
            visit(decl);
          }
        }
        for (ASTNode decl : p.decls) {
          if (!(decl instanceof FunDecl || decl instanceof FunDef)) {
            visit(decl);
          }
        }
      }

      // Function declaration
      case FunDecl fd -> {
        // Check if the function is a built-in function and return
        // check if built-in functions contain the function declaration
        if (BUILT_IN_FUNCTIONS.stream().anyMatch(f -> f.name.equals(fd.name))) {
          return;
        }

        // if the function is not a built-in function
        if (currentScope.lookupCurrent(fd.name) != null) {
          error("Function " + fd.name + " already declared.");
        }
        // System.out.println("declaring function: " + fd.name);
        // add the function to the current scope
        currentScope.put(new FunSymbol(fd));
        // track the declaration of the function
        currentScope.trackDeclaration(fd.name);
      }

      // Function definition
      case FunDef fd -> {
        // System.out.println("defining function: " + fd.name);

        // check if it's a main function
        if (fd.name.equals("main")) {
          FunSymbol mainSymbol = new FunSymbol(fd);
          // add the main function to the current scope
          currentScope.put(mainSymbol);
          // track the declaration of the main function
          currentScope.trackDeclaration(fd.name);
        } else {
          // check if the function in current scope
          FunSymbol existingSymbol = currentScope.lookupFunction(fd.name);

          // ensure a function declaration exists before definition
          if (existingSymbol == null) {
            // System.out.println("function " + fd.name + " is defined without prior declaration.");
            error("Function " + fd.name + " must be declared before definition.");
            return;
          }
          // assign the function definition
          existingSymbol.setDefinition(fd);
        }

        // create a new scope for function parameters
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);
        // visit the function parameters
        for (VarDecl param : fd.params) {
          visit(param);
        }
        // visit the function block
        visit(fd.block);
        // current scope is now the old scope
        currentScope = oldScope;
      }

      // Function calls check declaration before call
      case FunCallExpr fc -> {
        // System.out.println("checking function call: " + fc.name);
        // check if the function is in the current scope
        FunSymbol fs = currentScope.lookupFunction(fc.name);
        // if the function is not in the current scope
        if (fs == null) {
          // System.out.println("Function " + fc.name + " is not declared before use.");
          error("Function " + fc.name + " must be declared before use.");
          return;
        }
        // if the function is declared before use
        if (!currentScope.isDeclaredBeforeUse(fc.name)) {
          // System.out.println("Function " + fc.name + " is used before declaration.");
          error("Function " + fc.name + " is used before declaration.");
          return;
        }
        if (fs.def != null && fs.def.params.size() != fc.args.size()) {
          error("Function " + fc.name + " called with incorrect number of arguments.");
          return;
        }
        if (fs.decl != null && fs.decl.params.size() != fc.args.size()) {
          error("Function " + fc.name + " called with incorrect number of arguments.");
          return;
        }
        // if the function is declared , assign the function definition
        if (fs.def != null) {
          // assign the function definition to the function call
          fc.def = fs.def;
        }
        // if the function is declared , assign the function declaration
        else if (fs.decl != null) {
          // assign the function declaration to the function call
          fc.decl = fs.decl;
        }
      }

      // Block scope
      case Block b -> {
        // System.out.println("entering block scope");
        // create a new scope for the block
        Scope oldScope = currentScope;
        // current scope is now the new scope
        currentScope = new Scope(oldScope);
        // visit all the elements in the block
        for (ASTNode elem : b.children()) {
          visit(elem);
        }
        // current scope is now the old scope
        currentScope = oldScope;
        // System.out.println("exiting block scope");
      }

      // Variable declaration
      case VarDecl vd -> {
        // if the variable is already declared in the current scope
        if (currentScope.lookupCurrent(vd.name) != null) {
          error("Variable " + vd.name + " already declared in this scope.");
        }
        // if the variable is shadowed in the current scope
        if (currentScope.isShadowed(vd.name)) {
          System.out.println("shadowing detected: " + vd.name);
        }
        // put the variable in the current scope
        currentScope.put(new VarSymbol(vd));
      }

      // Variable expression
      case VarExpr v -> {
        // System.out.println("checking usage of variable: " + v.name);
        // check if the variable is declared in the current scope
        VarSymbol vs = currentScope.lookupVariable(v.name);
        // if the variable is not declared in the current scope
        if (vs == null) {
          error("Variable " + v.name + " is not declared.");
        }
        // else store the variable declaration
        else {
          v.vd = vs.vd;
        }
      }

      // expression statements
      case ExprStmt es -> visit(es.expr);

      // return statements
      case Return rs -> {
        if (rs.expr != null) {
          visit(rs.expr);
        }
      }

      // binary operations
      case BinOp b -> {
        visit(b.left);
        visit(b.right);
      }

      // defualt case
      default -> {
        if (!node.children().isEmpty()) {
          visit(node.children().get(0));
        }
      }
    }
  }
}
