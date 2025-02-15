package sem;

import ast.*;
import java.util.*;

/**
 * NameAnalyzer ensures correct scoping and visibility rules: variables and functions must be
 * declared before use. No duplicate variable or function declarations in the same scope. Functions
 * must have at most one declaration and one definition. Structs must be declared before use and
 * cannot have duplicate fields. and Shadowing.
 */
public class NameAnalyzer extends BaseSemanticAnalyzer {

  // Tracks the current scope during analysis
  private Scope currentScope;

  // List of built-in functions that will be valid
  /*
  * This will be always valid
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
    // Initialize the global scope and register built in functions in the symbol table
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

      // visit the declarations in order
      case Program p -> {
        for (ASTNode decl : p.decls) {
          visit(decl);
        }
      }
      // Function declaration
      case FunDecl fd -> {
        // Check if the function is a built-in function
        // check if built-in functions contain the function declaration
        if (BUILT_IN_FUNCTIONS.stream().anyMatch(f -> f.name.equals(fd.name))) {
          return;
        }

        // if the function is not a built-in function
        if (currentScope.lookupCurrent(fd.name) != null) {
          error("Function " + fd.name + " already declared.");
          return;
        }
        // System.out.println("declaring function: " + fd.name);
        // add the function to the current scope
        currentScope.put(new FunSymbol(fd));
        // track the declaration of the function
        currentScope.trackDeclaration(fd.name);
      }

      // Function definition
      case FunDef fd -> {
        // System.out.println("Defining function: " + fd.name);

        // look for a prior function declaration
        FunSymbol existingSymbol = currentScope.lookupFunction(fd.name);

        // if there is no existing declaration and treat this definition as both a declaration &
        // definition
        if (existingSymbol == null) {
          FunSymbol newSymbol = new FunSymbol(fd);
          currentScope.put(newSymbol);
          currentScope.trackDeclaration(fd.name);
        } else {
          // ensure no duplicate function definition
          if (existingSymbol.def != null) {
            error("Function " + fd.name + " is already defined.");
            return;
          }

          // ensure the function declaration and definition match in return type and the parameters
          if (existingSymbol.decl != null) {
            if (!fd.type.equals(existingSymbol.decl.type)) {
              error(
                  "Function "
                      + fd.name
                      + " definition does not match declaration: Return types do not match.");
              return;
            }
            // check if the number of parameters match
            if (fd.params.size() != existingSymbol.decl.params.size()) {
              error(
                  "Function "
                      + fd.name
                      + " definition does not match declaration: Parameter count does not match.");
              return;
            }
          }

          // associate the function definition with its existing declaration
          existingSymbol.setDefinition(fd);
        }

        // process function parameters in a new scope
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);
        // visit all the parameters of the function
        for (VarDecl param : fd.params) {
          visit(param);
        }
        // visit the block of the function
        visit(fd.block);
        // return to the old scope
        currentScope = oldScope;
      }

      // Function calls check declaration before call
      case FunCallExpr fc -> {
        // System.out.println("Checking function call: " + fc.name);

        // Look up function in the current scope
        FunSymbol fs = currentScope.lookupFunction(fc.name);

        // function must be declared or defined before use
        if (fs == null) {
          error("Function " + fc.name + " must be declared before use.");
          return;
        }

        // ensure function call matches declaration or definition
        int expectedParams = -1;
        int providedArgs = fc.args.size();

        if (fs.def != null) {
          expectedParams = fs.def.params.size();
        } else if (fs.decl != null) {
          expectedParams = fs.decl.params.size();
        }

        // argument count mismatch
        if (expectedParams != -1 && expectedParams != providedArgs) {
          error(
              "Function "
                  + fc.name
                  + " called with incorrect number of arguments. Expected: "
                  + expectedParams
                  + ", Provided: "
                  + providedArgs);
          return;
        }

        // link function call to definition or declaration
        if (fs.def != null) {
          fc.def = fs.def;
        } else if (fs.decl != null) {
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
        // dont use instanceof here
        switch (vd.type) {
          case StructType st -> {
            if (!currentScope.isDeclaredBeforeUse(st.name)) {
              error("Struct " + st.name + " must be declared before use.");
              return;
            }
          }
          default -> {}
        }

        // if the variable is already declared in the current scope
        if (currentScope.lookupCurrent(vd.name) != null) {
          error("Variable " + vd.name + " already declared in this scope.");
          return;
        }
        // if the variable is shadowed in the current scope
        if (currentScope.isShadowed(vd.name)) {
          System.out.println("shadowing detected: " + vd.name);
        }
        // Ensure the variable does not shadow a function name
        if (currentScope.lookupFunction(vd.name) != null) {
          error("Variable " + vd.name + " cannot shadow a function name.");
          return;
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

      case StructTypeDecl std -> {
        System.out.println("[LOG] Defining struct: " + std.structType.name);

        // ensure the struct itself is not already declared
        if (currentScope.lookupCurrent(std.structType.name) != null) {
          error("Struct " + std.structType.name + " is already declared.");
          return;
        }

        // validate field names within the struct
        Set<String> fieldNames = new HashSet<>();
        for (VarDecl field : std.fields) {
          if (fieldNames.contains(field.name)) {
            error("Duplicate field '" + field.name + "' in struct " + std.structType.name);
            return;
          }
          fieldNames.add(field.name);
        }

        // register the struct before function processing
        currentScope.put(new StructSymbol(std));
      }

      // expression statements
      case ExprStmt es -> visit(es.expr);

      // return statements
      case Return rs -> {
        if (rs.expr != null) {
          visit(rs.expr);
        }
      }

      // Visit the left and right expressions of an assignment
      case Assign a -> {
        visit(a.left);
        visit(a.right);
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
