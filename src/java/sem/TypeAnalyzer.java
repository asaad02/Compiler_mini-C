package sem;

import ast.*;
import java.util.*;

public class TypeAnalyzer extends BaseSemanticAnalyzer {
  // current scope
  private Scope currentScope;
  // current function return type
  private Type currentFunctionReturnType;
  // loop depth
  private int loopDepth = 0;
  // declared structs
  private Set<String> declaredStructs = new HashSet<>();
  // built-in functions
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

  public TypeAnalyzer() {
    // create a new scope
    this.currentScope = new Scope();
    // add built-in functions to the scope
    for (FunDecl f : BUILT_IN_FUNCTIONS) {
      // put the function in the current scope
      currentScope.put(new FunSymbol(f));
    }
  }

  public Type visit(ASTNode node) {
    return switch (node) {
      case null -> throw new IllegalStateException("Unexpected null value");
      // **AST Grammar:**
      // Program ::= (Decl)*
      // Decl ::= StructTypeDecl | VarDecl | FunDecl | FunDef
      case Program p -> {
        for (ASTNode decl : p.decls) {
          visit(decl);
        }
        yield BaseType.NONE;
      }
      // **Function Declaration**
      // FunDecl ::= Type String VarDecl*`
      case FunDecl fd -> {
        // return the built-in function type if it exists
        FunSymbol builtInFunction = currentScope.lookupFunction(fd.name);
        // if the function is built-in, return the type
        if (builtInFunction != null) {
          yield builtInFunction.decl.type;
        }
        // if the function is not built-in, add it to the scope
        currentScope.put(new FunSymbol(fd));
        yield fd.type;
      }
      // **Function Definition**
      // FunDef ::= Type String VarDecl* Block`
      // Typing Rule:
      // FunDef(f):
      // Γ ⊢ (U1, ..., Un) → T
      // ----------------------
      // add ⟨ f: (U1, ..., Un) → T ⟩ to Γ
      case FunDef fd -> {
        // return the built-in function type if it exists
        FunSymbol existingSymbol = currentScope.lookupFunction(fd.name);
        // if the function is built-in, return the type
        if (existingSymbol == null) {
          // if the function is not built-in, add it to the scope
          currentScope.put(new FunSymbol(fd));
        }
        // save the old scope
        Scope oldScope = currentScope;
        // create a new scope
        currentScope = new Scope(oldScope);
        // Set<String> declaredParams = new HashSet<>();
        // add the function parameters to the scope
        for (VarDecl param : fd.params) {
          Type paramType = param.type;
          // put the parameter in the current scope
          currentScope.put(new VarSymbol(new VarDecl(paramType, param.name)));
        }
        // set the current function return type
        currentFunctionReturnType = fd.type;
        // visit the function block
        visit(fd.block);
        // reset the current function return type
        currentFunctionReturnType = null;
        // restore the old scope
        currentScope = oldScope;
        // return the function type
        yield fd.type;
      }
      // **Struct Declaration**
      // StructTypeDecl ::= StructType VarDecl*`
      // StructDecl(x):
      // x not in Γ
      // ----------------
      // add x to Γ

      case StructTypeDecl std -> {
        if (!declaredStructs.add(std.structType.name)) {
          error("Struct '" + std.structType.name + "' is already declared.");
          yield BaseType.UNKNOWN;
        }
        for (VarDecl field : std.fields) {
          if (field.type.equals(BaseType.VOID)) {
            error("Struct field '" + field.name + "' cannot be void.");
            yield BaseType.UNKNOWN;
          }
          // array cant be type void
          if (field.type instanceof ArrayType at && at.elementType.equals(BaseType.VOID)) {
            error("Struct field '" + field.name + "' cannot be an array of void.");
            yield BaseType.UNKNOWN;
          }
        }

        if (isRecursiveWithoutPointer(std)) {
          error("Struct '" + std.structType.name + "' is recursive without pointer.");
          yield BaseType.UNKNOWN;
        }
        StructSymbol structSymbol = new StructSymbol(std);
        currentScope.put(structSymbol);
        // assign the struct name to the struct type
        yield BaseType.NONE;
      }
      // **Variable Declaration**
      // VarDecl ::= Type String`
      // VarDecl(v: T):
      // T ≠ void
      // ----------------------
      // add ⟨ v: T ⟩ to Γ

      case VarDecl vd -> {
        if (vd.type.equals(BaseType.VOID)) {
          error("Variable '" + vd.name + "' cannot be of type void.");
          yield BaseType.UNKNOWN;
        }
        if (vd.type instanceof StructType st && !declaredStructs.contains(st.name)) {
          error("Struct '" + st.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }
        currentScope.put(new VarSymbol(vd));
        yield vd.type;
      }
      // **Variable Expression**
      // VarExpr ::= String`
      // VarExpr(v):
      // ⟨ v: T ⟩ ∈ Γ
      // ----------------------
      // Γ ⊢
      case VarExpr v -> {
        // lookup the variable in the current scope
        VarSymbol varSymbol = currentScope.lookupVariable(v.name);
        if (v.name.equals("NULL")) {
          v.vd = new VarDecl(new PointerType(BaseType.VOID), "NULL");
          v.type = new PointerType(BaseType.VOID);
          yield v.type;
        }
        // if the variable is not declared, return an error
        if (varSymbol == null) {
          error("Variable '" + v.name + "' is not declared.");
          v.type = BaseType.UNKNOWN;
          yield BaseType.UNKNOWN;
        }
        v.vd = varSymbol.vd;
        v.type = varSymbol.vd.type;
        yield v.type;
      }
      // **Assignment**
      // Assign ::= Expr Expr`
      // Assign:
      // Γ ⊢ e1 : T
      // Γ ⊢ e2 : T
      // -----------------
      // Γ ⊢ e1 = e2 : T
      case Assign a -> {
        // if the left-hand side of the assignment is not an lvalue, return an error
        if (!isLValue(a.left)) {
          error("Left-hand side of assignment must be an lvalue.");
          yield BaseType.UNKNOWN;
        }
        // visit the left-hand side of the assignment
        Type left = visit(a.left);
        // print the type of the left-hand side of the assignment
        // visit the right-hand side of the assignment
        Type right = visit(a.right);

        //  assigning string literal to an array element and the size is equal
        if (a.left instanceof ArrayAccessExpr arrayAccessExpr
            && arrayAccessExpr.array instanceof VarExpr varExpr
            && varExpr.vd.type instanceof ArrayType arrayType
            && a.right instanceof StrLiteral strLiteral
            && arrayType.size < strLiteral.value.length() + 1) {
          arrayAccessExpr.type = arrayType.elementType; // Assign
          // if the left-hand side of the assignment is not an lvalue, return an error
          if (!arrayType.elementType.equals(BaseType.CHAR)) {
            error("Array element type mismatch.");
            yield BaseType.UNKNOWN;
          }
          // if both array not the same size
          if (arrayType.size != strLiteral.value.length() + 1) {
            error("Array size mismatch.");
            yield BaseType.UNKNOWN;
          }
          yield BaseType.NONE;
        }
        // switch on the left-hand side of the assignment
        switch (left) {
          case BaseType bt -> {
            // if the left-hand side is of type void, return an error
            if (bt.equals(BaseType.VOID)) {
              error("Variable cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
            // if the left-hand side is not equal to the right-hand side, return an error
            if (left.equals(BaseType.INT) && right.equals(BaseType.CHAR)) {
              error("Implicit conversion from 'char' to 'int' is not allowed.");
              yield BaseType.UNKNOWN;
            }
            // if from int to char
            if (left.equals(BaseType.CHAR) && right.equals(BaseType.INT)) {
              error("Implicit conversion from 'int' to 'char' is not allowed.");
              yield BaseType.UNKNOWN;
            }
          }
          // if the left-hand side is an array
          case ArrayType leftArray -> {
            switch (right) {
              case ArrayType rightArray -> {
                // ensure both are 2D arrays with matching inner types
                if (leftArray.elementType instanceof ArrayType leftInner
                    && rightArray.elementType instanceof ArrayType rightInner) {

                  // Check if the inner arrays rows have the same type
                  if (!leftInner.elementType.equals(rightInner.elementType)) {
                    error("2D Array element type mismatch.");
                    yield BaseType.UNKNOWN;
                  }

                  // check if the row sizes match
                  if (leftInner.size != rightInner.size) {
                    error("2D Array row size mismatch.");
                    yield BaseType.UNKNOWN;
                  }
                } else if (!leftArray.elementType.equals(rightArray.elementType)) {
                  error("Array element type mismatch.");
                  yield BaseType.UNKNOWN;
                }
                // 3nsure the top-level array sizes match
                if (leftArray.size != rightArray.size) {
                  error("Array size mismatch.");
                  yield BaseType.UNKNOWN;
                }
              }
              default -> {
                error("Array assignment mismatch.");
                yield BaseType.UNKNOWN;
              }
            }

            yield leftArray;
          }
          default -> {
            yield BaseType.UNKNOWN;
          }
        }
        a.type = left;
        a.left.type = left;
        yield left;
      }

      // expression statements
      case ExprStmt es -> visit(es.expr);

      case Block b -> {
        // save the old scope
        Scope oldScope = currentScope;
        // visit the block statements
        for (ASTNode stmt : b.children()) {
          visit(stmt);
        }
        // restore the old scope
        currentScope = oldScope;
        yield BaseType.NONE;
      }

      case If i -> {
        // visit the if condition
        if (!visit(i.condition).equals(BaseType.INT)) {
          error("If condition must be of type int.");
        }
        // visit the if branch
        visit(i.thenBranch);
        // visit the else branch
        if (i.elseBranch != null) visit(i.elseBranch);
        yield BaseType.NONE;
      }
      // **Binary Operations**
      // BinOp ::= Expr Op Expr`
      // BinOp(e1, e2, Op={+,-,*,/,%,||,&&,>,<,>=,<=}):
      // Γ ⊢ e1 : int
      // Γ ⊢ e2 : int
      // -----------------
      // Γ ⊢ e1 Op e2 : int
      case BinOp b -> {
        // visit the left-hand side of the binary operation
        Type left = visit(b.left);
        // visit the right-hand side of the binary operation
        Type right = visit(b.right);
        // if the binary operation is an arithmetic or comparison operation
        if (Set.of(
                Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.MOD, Op.OR, Op.AND, Op.GT, Op.LT, Op.GE, Op.LE)
            .contains(b.op)) {
          // if the left-hand side or right-hand side is not of type int, return an error
          if (!left.equals(BaseType.INT) || !right.equals(BaseType.INT)) {
            error("Arithmetic and comparison operations must be between integers.");
            yield BaseType.UNKNOWN;
          }
          yield BaseType.INT;
        }
        // if the binary operation is an equality operation
        if (Set.of(Op.EQ, Op.NE).contains(b.op)) {
          if (left instanceof StructType
              || left instanceof ArrayType
              || right instanceof StructType
              || right instanceof ArrayType) {
            error("Equality operators cannot be applied to structs or arrays.");
            yield BaseType.UNKNOWN;
          }
          if ((left instanceof PointerType && right.equals(BaseType.INT))
              || (right instanceof PointerType && left.equals(BaseType.INT))
              || (left instanceof PointerType && right instanceof PointerType)) {
            yield BaseType.INT;
          }
          if (!left.equals(right)) {
            error("Type mismatch in equality operation.");
            yield BaseType.UNKNOWN;
          }
          yield BaseType.INT;
        }

        error("Invalid binary operation.");
        yield BaseType.UNKNOWN;
      }
      // while loop
      case While w -> {
        loopDepth++;
        if (!visit(w.condition).equals(BaseType.INT)) {
          error("While condition must be of type int.");
        }
        visit(w.body);
        loopDepth--;
        yield BaseType.NONE;
      }
      // return statement
      case Return r -> {
        // if the return statement is outside of a function, return an error
        if (currentFunctionReturnType == null) {
          error("Return statement outside of function.");
          yield BaseType.UNKNOWN;
        }
        // if the return statement does not return a value
        if (r.expr == null) {
          if (!currentFunctionReturnType.equals(BaseType.VOID)) {
            error("Return statement must return a value.");
            yield BaseType.UNKNOWN;
          }
          yield BaseType.VOID;
        } else {
          // visit the return expression
          Type returnType = visit(r.expr);
          /*
          if (!currentFunctionReturnType.equals(returnType)
              && !(currentFunctionReturnType instanceof PointerType)) {
            error(
                "Return statement type mismatch: expected "
                    + currentFunctionReturnType
                    + " but got "
                    + returnType);
            yield BaseType.UNKNOWN;
          }
            */

          yield returnType;
        }
      }

      case IntLiteral i -> i.type = BaseType.INT;

      case ChrLiteral c -> c.type = BaseType.CHAR;
      // string literal
      case StrLiteral s -> s.type = new ArrayType(BaseType.CHAR, s.value.length() + 1);

      case Continue c -> {
        if (loopDepth == 0) {
          error("Continue statement outside of loop.");
        }
        yield BaseType.NONE;
      }

      case Break b -> {
        if (loopDepth == 0) {
          error("Break statement outside of loop.");
        }
        yield BaseType.NONE;
      }

      case FunCallExpr f -> {
        FunSymbol funSymbol = currentScope.lookupFunction(f.name);

        // if the function is not declared and is a built-in function, return the built-in function
        // type if it exists
        if (funSymbol == null) {
          FunDecl builtInFunction =
              BUILT_IN_FUNCTIONS.stream()
                  .filter(fd -> fd.name.equals(f.name))
                  .findFirst()
                  .orElse(null);
          if (builtInFunction != null) {
            System.out.println("return value " + builtInFunction.type);
            yield builtInFunction.type;
          }
        }

        if (funSymbol == null) {
          error("Function '" + f.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }
        // visit the function arguments
        List<Type> expectedParams =
            funSymbol.def != null ? funSymbol.def.getParamTypes() : funSymbol.decl.getParamTypes();
        // if the number of arguments does not match the number of parameters return an error
        if (f.args.size() != expectedParams.size()) {
          error("Function '" + f.name + "' argument count mismatch.");
          yield BaseType.UNKNOWN;
        }

        // check the type of each argument
        for (int i = 0; i < f.args.size(); i++) {
          Type expected = expectedParams.get(i);
          Type actual = visit(f.args.get(i));
          /*
          if (!typesAreEquivalent(expected, actual) &) {
            error(
                "Argument "
                    + (i + 1)
                    + " type mismatch: expected "
                    + expected
                    + " but got "
                    + actual);
            yield BaseType.UNKNOWN;
          }
            */
          switch (expected) {
            case BaseType bt -> {
              if (bt.equals(BaseType.VOID)) {
                error("Function argument cannot be of type void.");
                yield BaseType.UNKNOWN;
              }
            }
            case ArrayType expectedArray -> {
              if (actual instanceof ArrayType actualArray) {
                // Handle 2D array case
                if (expectedArray.elementType instanceof ArrayType expectedInner
                    && actualArray.elementType instanceof ArrayType actualInner) {

                  // Check inner array types
                  if (!expectedInner.elementType.equals(actualInner.elementType)) {
                    error("Function argument 2D array type mismatch.");
                    yield BaseType.UNKNOWN;
                  }
                  // Check inner array sizes
                  if (expectedInner.size != actualInner.size) {
                    error("Function argument 2D array row size mismatch.");
                    yield BaseType.UNKNOWN;
                  }
                } else if (!expectedArray.elementType.equals(actualArray.elementType)) {
                  error("Function argument array element type mismatch.");
                  yield BaseType.UNKNOWN;
                }
                // Check top level sizes
                if (expectedArray.size != actualArray.size) {
                  error("Function argument array size mismatch.");
                  yield BaseType.UNKNOWN;
                }
              } else {
                error("Function argument type mismatch: Expected array but got " + actual);
                yield BaseType.UNKNOWN;
              }
            }
            default -> {}
          }
        }
        yield funSymbol.def != null ? funSymbol.def.type : funSymbol.decl.type;
      }
      case ArrayAccessExpr a -> {
        Type arrayType = visit(a.array);
        Type indexType = visit(a.index);

        if (!indexType.equals(BaseType.INT)) {
          error("Array index must be of type int.");
          yield BaseType.UNKNOWN;
        }
        if (!(arrayType instanceof ArrayType arrType)) {
          error("Attempted array access on non-array type.");
          yield BaseType.UNKNOWN;
        }

        a.type = ((ArrayType) arrayType).elementType;
        yield a.type;
      }

      case FieldAccessExpr fa -> {
        Type structType = visit(fa.structure);
        if (structType instanceof PointerType pt) {
          structType = pt.baseType;
        }
        if (!(structType instanceof StructType st)) {
          error("Field access on non-struct type.");
          yield BaseType.UNKNOWN;
        }

        StructSymbol structSymbol = currentScope.lookupStruct(st.name);
        if (structSymbol == null) {
          error("Struct '" + st.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }

        Type fieldType = structSymbol.getFieldType(fa.field);
        if (fieldType == null) {
          error("Struct '" + st.name + "' has no field named '" + fa.field + "'");
          yield BaseType.UNKNOWN;
        }

        fa.type = fieldType;
        yield fieldType;
      }
      // typecast expression
      case TypecastExpr tc -> {
        Type exprType = visit(tc.expr);

        if (tc.type.equals(BaseType.INT) && exprType.equals(BaseType.CHAR)) {
          yield BaseType.INT;
        }

        if (tc.type.equals(BaseType.CHAR) && exprType.equals(BaseType.INT)) {
          yield BaseType.CHAR;
        }

        if (tc.type instanceof PointerType pt1 && exprType instanceof PointerType pt2) {
          yield pt1;
        }

        if (tc.type instanceof PointerType pt && exprType instanceof ArrayType at) {
          yield new PointerType(at.elementType);
        }
        yield BaseType.UNKNOWN;
      }
      // value at expression
      case ValueAtExpr va -> {
        Type expr = visit(va.expr);
        switch (expr) {
          // if the value at operator is applied to a pointer
          case PointerType pt -> {
            yield pt.baseType;
          }
          // if the value at operator is applied to a non-pointer type, return an error
          default -> {
            error("Value at operator on non-pointer type.");
            yield BaseType.UNKNOWN;
          }
        }
      }
      // pointer type
      case PointerType pt -> {
        Type baseType = visit(pt.baseType);
        // if the pointer base type is void, return an error
        if (baseType == null || baseType.equals(BaseType.VOID)) {
          error("Pointer to void is not allowed.");
          yield BaseType.UNKNOWN;
        }
        yield new PointerType(baseType);
      }
      // address of expression
      case AddressOfExpr ao -> {
        Type expr = visit(ao.expr);
        // if the address of operator is applied to a non-lvalue, return an error
        if (!isLValue(ao.expr)) {
          error("Address of operator on non-lvalue.");
          yield BaseType.UNKNOWN;
        }
        yield new PointerType(expr);
      }
      // sizeof expression
      case SizeOfExpr so -> {
        switch (so.type) {
          // if the sizeof operator is applied to a base type, return the size of the base type
          case BaseType bt -> {
            yield BaseType.INT;
          }
          // if the sizeof operator is applied to an array type, return the size of the array type
          case StructType st -> {
            // if the struct is not declared, return an error
            if (!declaredStructs.contains(st.name)) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }
            yield BaseType.INT;
          }
          // if the sizeof operator is applied to a pointer type, return the size of the pointer
          // type
          case ArrayType at -> {
            yield BaseType.INT;
          }
          // if the sizeof operator is applied to a pointer type, return the size of the pointer
          // type
          case PointerType pt -> {
            yield BaseType.INT;
          }
          // if the sizeof operator is applied to an unknown type, return an error
          default -> {
            yield BaseType.UNKNOWN;
          }
        }
      }
      default -> BaseType.UNKNOWN;
    };
  }

  // check if the node is an lvalue
  private boolean isLValue(ASTNode node) {
    return switch (node) {
      case VarExpr v -> true;
      case ArrayAccessExpr a -> isLValue(a.array);
      case FieldAccessExpr fa -> isLValue(fa.structure);
      case ValueAtExpr va -> isLValue(va.expr);
      default -> false;
    };
  }

  // check if the struct is recursive without a pointer
  private boolean isRecursiveWithoutPointer(StructTypeDecl std) {
    return std.fields.stream()
        .anyMatch(
            field -> field.type instanceof StructType st && st.name.equals(std.structType.name));
  }

  // check structural equivalence of types
  private boolean typesAreEquivalent(Type expected, Type actual) {
    if (expected == actual) return true;

    if (expected instanceof PointerType expectedPtr && actual instanceof PointerType actualPtr) {
      return typesAreEquivalent(expectedPtr.baseType, actualPtr.baseType);
    } else if (expected instanceof StructType expectedSt && actual instanceof StructType actualSt) {
      return expectedSt.name.equals(actualSt.name);
    } else if (expected instanceof ArrayType expectedArr && actual instanceof ArrayType actualArr) {
      return expectedArr.size == actualArr.size
          && typesAreEquivalent(expectedArr.elementType, actualArr.elementType);
    } else {
      return expected.equals(actual);
    }
  }
}
