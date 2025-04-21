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
      // Class declaration register class symbol and fields anmd methods and link to parent
      case ClassDecl cd -> {
        // check if the class already exists
        if (currentScope.lookupCurrent(cd.name) != null) {
          error("Class " + cd.name + " is already declared.");
          return;
        }
        // add the class to the current scope
        ClassSymbol cs = new ClassSymbol(cd.name, cd.parent);
        currentScope.put(cs);
        // add variables and methods to the class symbol
        for (VarDecl f : cd.fields) {
          // check if the field is already declared
          if (cs.fields.containsKey(f.name)) {
            error("Field override: " + f.name + " in class " + cd.name);
          }
          cs.addField(f.name, f.type);
        }
        for (FunDef m : cd.methods) {
          Set<String> parameterseen = new HashSet<>();
          // prvenet class method redefined
          if (cs.methods.containsKey(m.name)) {
            error("Method " + m.name + " is already defined in class " + cd.name);
          }
          for (VarDecl param : m.params) {
            if (!parameterseen.add(param.name)) {
              error(
                  "Method '"
                      + m.name
                      + "' in class '"
                      + cd.name
                      + "' has duplicate parameter '"
                      + param.name
                      + "'.");
            }
          }
          FunSymbol fm = new FunSymbol(m);
          cs.addMethod(fm);
        }
        if (cd.parent != null) {
          ClassSymbol parent = currentScope.lookupClass(cd.parent);
          // check if filed override and report an error
          if (parent != null) {
            for (VarDecl f : cd.fields) {
              if (parent.fields.containsKey(f.name)) {
                error("Field override: " + f.name + " in class " + cd.name);
              }
            }
          }
          if (parent == null) {
            // report an error if the parent class is not declared
            error("Unknown parent class: " + cd.parent);
          } else {
            cs.parent = parent;
          }
        }
      }

      // ensure class exists
      case NewInstance ni -> {
        // check if the class is declared
        ClassSymbol cs = currentScope.lookupClass(ni.className);
        // report an error if the class is not declared
        if (cs == null) {
          error("Class " + ni.className + " must be declared before instantiation.");
        }
      }
      // Function declaration
      case FunDecl fd -> {
        // Check if the function is a built-in function
        // check if built-in functions contain the function declaration
        if (BUILT_IN_FUNCTIONS.stream().anyMatch(f -> f.name.equals(fd.name))) {
          return;
        }

        if (currentScope.lookupFunction(fd.name) != null) {
          error("Function " + fd.name + " is already declared.");
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

        if (existingSymbol == null) {
          // No previous declaration so treat as a new function definition
          FunSymbol newSymbol = new FunSymbol(fd);
          currentScope.put(newSymbol);
          currentScope.trackDeclaration(fd.name);
        } else {
          // ensure no duplicate function definition
          if (existingSymbol.def != null) {
            error("Function " + fd.name + " is already defined.");
            return;
          }
          // function declaration matches definition
          if (existingSymbol.decl != null && !(existingSymbol.decl.type instanceof PointerType)) {

            if (!fd.type.equals(existingSymbol.decl.type)) {
              error(
                  "Function "
                      + fd.name
                      + " definition does not match declaration: Return types do not match.");
              return;
            }

            if (fd.params.size() != existingSymbol.decl.params.size()) {
              error(
                  "Function "
                      + fd.name
                      + " definition does not match declaration: Parameter count does not match.");
              return;
            }
            // check if the parameters match
            for (int i = 0; i < fd.params.size(); i++) {
              if (!fd.params.get(i).type.equals(existingSymbol.decl.params.get(i).type)
                  && !(existingSymbol.decl.params.get(i).type instanceof PointerType)) {
                error(
                    "Function "
                        + fd.name
                        + " definition does not match declaration: Parameter types do not match.");
                return;
              }
            }
          }

          // Link the definition to the previously declared function
          existingSymbol.setDefinition(fd);
        }

        // Process function parameters in a new local scope
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);

        // Track already declared parameters to detect duplicates
        Set<String> declaredParams = new HashSet<>();

        for (VarDecl param : fd.params) {
          // Ensure parameter does not shadow a global variable
          if (oldScope.lookupVariable(param.name) != null) {
            System.out.println(
                "Shadowing detected: Function parameter '"
                    + param.name
                    + "' shadows a global variable.");
          }

          // Ensure parameter is not already declared in the function local scope
          if (declaredParams.contains(param.name)) {
            error("Function parameter '" + param.name + "' is already declared in this function.");
            return;
          }
          declaredParams.add(param.name);

          // Insert parameter into local scope
          currentScope.put(new VarSymbol(param));
        }

        // visit function body to check for undeclared parameter usage
        visit(fd.block);

        // return to outer scope
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

        // return the type of the function call
        // System.out.println("Function call type: " + fs.type);
        // name
        // System.out.println("Function call name: " + fs.name);
        fc.type = fs.type;
      }

      // Block scope
      case Block b -> {
        // System.out.println("entering block scope");
        // create a new scope for the block
        Scope oldScope = currentScope;
        // current scope is now the new scope
        currentScope = new Scope(oldScope);
        // visit all the elements in the block
        // for (ASTNode elem : b.children()) {
        // visit(elem);
        // }

        for (VarDecl vd : b.vds) {
          visit(vd);
        }
        for (Stmt stmt : b.stmts) {
          visit(stmt);
        }
        // current scope is now the old scope
        currentScope = oldScope;
        // System.out.println("exiting block scope");
      }

      // Variable declaration
      case VarDecl vd -> {
        // System.out.println("Declaring variable: " + vd.name);
        if (currentScope.lookupCurrent(vd.name) != null
            && currentScope.lookupVariable(vd.name) != null) {
          VarSymbol vs = currentScope.lookupVariable(vd.name);
          if (vs.vd.type.equals(vd.type)) {
            error("Variable " + vd.name + " is already declared with a same type.");
            return;
          }
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
        if (v.name.equals("NULL")) {
          v.vd = new VarDecl(new PointerType(BaseType.VOID), "NULL");
          return;
        }
        if (vs == null) {
          error("Variable " + v.name + " must be declared before use.");
          return;
        }
        // else store the variable declaration
        else {
          v.vd = vs.vd;
        }
      }

      case StructTypeDecl std -> {
        // System.out.println("Defining struct: " + std.structType.name);

        // ensure the struct itself is not already declared
        if (currentScope.lookupCurrent(std.structType.name) != null) {
          error("Struct " + std.structType.name + " is already declared.");
          return;
        }
        // register the struct before function processing
        currentScope.put(new StructSymbol(std));
        // validate field names within the struct
        Set<String> fieldNames = new HashSet<>();
        for (VarDecl field : std.fields) {
          if (fieldNames.contains(field.name)) {
            error("Duplicate field '" + field.name + "' in struct " + std.structType.name);
            return;
          }
          fieldNames.add(field.name);
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
