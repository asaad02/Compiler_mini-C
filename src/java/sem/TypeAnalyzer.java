package sem;

import ast.*;
import java.util.*;

public class TypeAnalyzer extends BaseSemanticAnalyzer {

  private Scope currentScope;
  private Type currentFunctionReturnType;
  private int loopDepth = 0;
  private Set<String> declaredStructs = new HashSet<>();
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
    this.currentScope = new Scope();
    for (FunDecl f : BUILT_IN_FUNCTIONS) {
      currentScope.put(new FunSymbol(f));
    }
  }

  public Type visit(ASTNode node) {
    return switch (node) {
      case null -> throw new IllegalStateException("Unexpected null value");

      case Program p -> {
        for (ASTNode decl : p.decls) {
          visit(decl);
        }
        yield BaseType.NONE;
      }

      case FunDecl fd -> {
        // return the built-in function type if it exists
        FunSymbol builtInFunction = currentScope.lookupFunction(fd.name);
        if (builtInFunction != null) {
          yield builtInFunction.decl.type;
        }
        currentScope.put(new FunSymbol(fd));
        yield fd.type;
      }

      case FunDef fd -> {
        FunSymbol existingSymbol = currentScope.lookupFunction(fd.name);
        if (existingSymbol == null) {
          // if function wasn't declared, store it
          currentScope.put(new FunSymbol(fd));
        } else {
          // ensure the definition matches the declaration
          if (!existingSymbol.decl.type.equals(fd.type)) {
            error("Function '" + fd.name + "' return type mismatch.");
            yield BaseType.UNKNOWN;
          }
        }
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);
        Set<String> declaredParams = new HashSet<>();

        for (VarDecl param : fd.params) {
          if (!declaredParams.add(param.name)) {
            error("Duplicate parameter '" + param.name + "'");
            yield BaseType.UNKNOWN;
          }
          currentScope.put(new VarSymbol(param));
        }

        currentFunctionReturnType = fd.type;
        visit(fd.block);
        currentFunctionReturnType = null;
        // restore outer scope after function body processing
        currentScope = oldScope;
        yield fd.type;
      }
      // expression statements
      case ExprStmt es -> visit(es.expr);

      case VarDecl vd -> {
        switch (vd.type) {
          case BaseType bt -> {
            if (bt.equals(BaseType.VOID)) {
              error("Variable '" + vd.name + "' cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
          }
          case StructType st -> {
            if (!declaredStructs.contains(st.name)) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }
          }
          case ArrayType at -> {
            if (at.elementType.equals(BaseType.VOID)) {
              error("Array '" + vd.name + "' cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
          }
          case PointerType pt -> {
            if (pt.baseType.equals(BaseType.VOID)) {
              error("Pointer '" + vd.name + "' cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
          }
          default -> {
            yield BaseType.UNKNOWN;
          }
        }
        currentScope.put(new VarSymbol(vd));
        yield vd.type;
      }

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
          if (field.type instanceof StructType) {
            StructType structFieldType = (StructType) field.type;
            if (structFieldType.name.equals(std.name) && !(field.type instanceof PointerType)) {
              error(
                  "Struct '"
                      + std.name
                      + "' cannot contain itself as a field unless it's a pointer.");
              yield BaseType.UNKNOWN;
            }
          }
        }
        if (isRecursiveWithoutPointer(std)) {
          error("Struct '" + std.structType.name + "' is recursive without pointer.");
        }
        currentScope.put(new StructSymbol(std));
        yield BaseType.NONE;
      }
      case Assign a -> {
        if (!isLValue(a.left)) {
          error("Left-hand side of assignment must be an lvalue.");
          yield BaseType.UNKNOWN;
        }
        Type left = visit(a.left);
        Type right = visit(a.right);
        switch (left) {
          case BaseType bt -> {
            if (bt.equals(BaseType.VOID)) {
              error("Variable cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
            if (left.equals(BaseType.INT) && right.equals(BaseType.CHAR)) {
              error("Implicit conversion from 'char' to 'int' is not allowed.");
              yield BaseType.UNKNOWN;
            }
          }
          case StructType st -> {
            if (!declaredStructs.contains(st.name)) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }

            if (!st.name.equals(((StructType) right).name)) {
              error(
                  "Struct assignment mismatch: '"
                      + st.name
                      + "' != '"
                      + ((StructType) right).name
                      + "'");
              yield BaseType.UNKNOWN;
            }
          }

          case ArrayType leftArray -> {
            switch (right) {
              case ArrayType rightArray -> {
                if (!leftArray.elementType.equals(rightArray.elementType)) {
                  error(
                      "Array element type mismatch: '"
                          + leftArray.elementType
                          + "' != '"
                          + rightArray.elementType
                          + "'");
                  yield BaseType.UNKNOWN;
                }
                if (leftArray.size != rightArray.size) {
                  error(
                      "Array size mismatch: '" + leftArray.size + "' != '" + rightArray.size + "'");
                  yield BaseType.UNKNOWN;
                }
              }
              default -> {
                error("Array assignment mismatch: '" + leftArray + "' != '" + right + "'");
                yield BaseType.UNKNOWN;
              }
            }
            yield leftArray;
          }
          case PointerType ptLeft -> {
            // check the right side of the pointer to which type is point to
            switch (right) {
              case PointerType ptRight -> {
                if (!ptLeft.baseType.equals(ptRight.baseType)) {
                  error(
                      "Pointer assignment mismatch: '"
                          + ptLeft.baseType
                          + "' != '"
                          + ptRight.baseType
                          + "'");
                  yield BaseType.UNKNOWN;
                }
              }
              default -> {
                error("Pointer assignment mismatch: '" + ptLeft + "' != '" + right + "'");
                yield BaseType.UNKNOWN;
              }
            }
          }

          default -> {
            yield BaseType.UNKNOWN;
          }
        }

        yield left;
      }

      case Block b -> {

        // save the old scope
        Scope oldScope = currentScope;
        for (ASTNode stmt : b.children()) {
          visit(stmt);
        }
        currentScope = oldScope;

        yield BaseType.NONE;
      }

      case If i -> {
        if (!visit(i.condition).equals(BaseType.INT)) {
          error("If condition must be of type int.");
        }
        visit(i.thenBranch);
        if (i.elseBranch != null) visit(i.elseBranch);
        yield BaseType.NONE;
      }
      case BinOp b -> {
        Type left = visit(b.left);
        Type right = visit(b.right);

        if (left.equals(BaseType.UNKNOWN) || right.equals(BaseType.UNKNOWN)) {
          error("Binary operation contains an unknown type.");
          yield BaseType.UNKNOWN;
        }

        if (Set.of(Op.ADD, Op.SUB, Op.MUL, Op.DIV, Op.MOD).contains(b.op)) {
          if (!left.equals(BaseType.INT) || !right.equals(BaseType.INT)) {
            error(
                "Arithmetic operations must be between integers. Found: " + left + " and " + right);
            yield BaseType.UNKNOWN;
          }
          yield BaseType.INT;
        }

        if (Set.of(Op.GT, Op.LT, Op.GE, Op.LE, Op.EQ, Op.NE).contains(b.op)) {
          if (!left.equals(BaseType.INT) || !right.equals(BaseType.INT)) {
            error(
                "Comparison operations must be between integers. Found: " + left + " and " + right);
            yield BaseType.UNKNOWN;
          }
          yield BaseType.INT;
        }

        error("Invalid binary operation.");
        yield BaseType.UNKNOWN;
      }

      case While w -> {
        loopDepth++;
        if (!visit(w.condition).equals(BaseType.INT)) {
          error("While condition must be of type int.");
        }
        visit(w.body);
        loopDepth--;
        yield BaseType.NONE;
      }

      case Return r -> {
        if (currentFunctionReturnType == null) {
          error("Return statement outside of function.");
          yield BaseType.UNKNOWN;
        }
        if (r.expr == null) {
          if (!currentFunctionReturnType.equals(BaseType.VOID)) {
            error("Return statement must return a value.");
            yield BaseType.UNKNOWN;
          }
          yield BaseType.VOID;
        } else {
          Type returnType = visit(r.expr);
          if (!currentFunctionReturnType.equals(returnType)) {
            error(
                "Return statement type mismatch: expected "
                    + currentFunctionReturnType
                    + " but got "
                    + returnType);
            yield BaseType.UNKNOWN;
          }

          yield returnType;
        }
      }
      case VarExpr v -> {
        VarSymbol varSymbol = currentScope.lookupVariable(v.name);
        if (varSymbol == null) {
          error("Variable '" + v.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }

        yield varSymbol.vd.type;
      }

      case IntLiteral i -> i.type = BaseType.INT;

      case ChrLiteral c -> c.type = BaseType.CHAR;

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
        if (funSymbol == null) {
          error("Function '" + f.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }
        List<Type> expectedParams;
        if (funSymbol.def != null) {
          expectedParams = funSymbol.def.getParamTypes();
        } else {
          expectedParams = funSymbol.decl.getParamTypes();
        }
        if (f.args.size() != expectedParams.size()) {
          error("Function '" + f.name + "' argument count mismatch.");
          yield BaseType.UNKNOWN;
        }

        for (int i = 0; i < f.args.size(); i++) {
          Type expected = expectedParams.get(i);
          Type actual = visit(f.args.get(i));

          if (!expected.equals(actual)) {
            error(
                "Function '"
                    + f.name
                    + "' argument "
                    + (i + 1)
                    + " type mismatch: expected "
                    + expected
                    + " but got "
                    + actual);
            yield BaseType.UNKNOWN;
          }
          // ensure array sizes match
          switch (expected) {
            case ArrayType expectedArray -> {
              if (actual instanceof ArrayType actualArray) {
                if (expectedArray.size != actualArray.size) {
                  error(
                      "Function '"
                          + f.name
                          + "' argument "
                          + (i + 1)
                          + " array size mismatch: expected size "
                          + expectedArray.size
                          + " but got size "
                          + actualArray.size);
                  yield BaseType.UNKNOWN;
                }
              } else {
                error("Expected an array argument but got " + actual);
                yield BaseType.UNKNOWN;
              }
            }
            default -> {}
          }
        }

        yield funSymbol.def != null ? funSymbol.def.type : funSymbol.decl.type;
      }

      case ArrayAccessExpr a -> {
        Type array = visit(a.array);
        if (!(array instanceof ArrayType)) {
          error("Array access on non-array type.");
          yield BaseType.UNKNOWN;
        }
        Type index = visit(a.index);
        if (!index.equals(BaseType.INT)) {
          error("Array index must be of type int.");
          yield BaseType.UNKNOWN;
        }
        yield ((ArrayType) array).elementType;
      }

      case FieldAccessExpr fa -> {
        switch (visit(fa.structure)) {
          case StructType st -> {
            StructSymbol structSymbol = currentScope.lookupStruct(st.name);
            if (structSymbol == null) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }
            Type fieldType = structSymbol.getFieldType(fa.field);
            if (fieldType == null) {
              error("Field '" + fa.field + "' is not declared in struct '" + st.name + "'.");
              yield BaseType.UNKNOWN;
            }
            yield fieldType;
          }
          default -> {
            error("Field access on non-struct type.");
            yield BaseType.UNKNOWN;
          }
        }
      }
      case TypecastExpr tc -> {
        Type expr = visit(tc.expr);
        switch (tc.type) {
          case BaseType bt -> {
            if (bt.equals(BaseType.VOID)) {
              error("Typecast to void is not allowed.");
              yield BaseType.UNKNOWN;
            }
            if (expr.equals(BaseType.VOID)) {
              error("Typecast from void is not allowed.");
              yield BaseType.UNKNOWN;
            }
          }
          case StructType st -> {
            if (!declaredStructs.contains(st.name)) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }
            if (expr.equals(BaseType.VOID)) {
              error("Typecast from void is not allowed.");
              yield BaseType.UNKNOWN;
            }
          }
          case ArrayType at -> {
            if (at.elementType.equals(BaseType.VOID)) {
              error("Array cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
            if (expr.equals(BaseType.VOID)) {
              error("Typecast from void is not allowed.");
              yield BaseType.UNKNOWN;
            }
            yield at;
          }
          case PointerType pt -> {
            if (pt.baseType.equals(BaseType.VOID)) {
              error("Pointer cannot be of type void.");
              yield BaseType.UNKNOWN;
            }
            if (expr.equals(BaseType.VOID)) {
              error("Typecast from void is not allowed.");
              yield BaseType.UNKNOWN;
            }
          }
          default -> {
            yield BaseType.UNKNOWN;
          }
        }
        yield tc.type;
      }

      case ValueAtExpr va -> {
        Type expr = visit(va.expr);
        switch (expr) {
          case PointerType pt -> {
            yield pt.baseType;
          }
          default -> {
            error("Value at operator on non-pointer type.");
            yield BaseType.UNKNOWN;
          }
        }
      }
      case PointerType pt -> {
        Type baseType = visit(pt.baseType);
        if (baseType == null || baseType.equals(BaseType.VOID)) {
          error("Pointer to void is not allowed.");
          yield BaseType.UNKNOWN;
        }
        yield new PointerType(baseType);
      }

      case SizeOfExpr so -> {
        switch (so.type) {
          case BaseType bt -> {
            yield BaseType.INT;
          }
          case StructType st -> {
            if (!declaredStructs.contains(st.name)) {
              error("Struct '" + st.name + "' is not declared.");
              yield BaseType.UNKNOWN;
            }
            yield BaseType.INT;
          }
          case ArrayType at -> {
            yield BaseType.INT;
          }
          default -> {
            yield BaseType.UNKNOWN;
          }
        }
      }
      case AddressOfExpr ao -> {
        Type expr = visit(ao.expr);
        if (!isLValue(ao.expr)) {
          error("Address of operator on non-lvalue.");
          yield BaseType.UNKNOWN;
        }
        yield new PointerType(expr);
      }

      default -> BaseType.UNKNOWN;
    };
  }

  private boolean isLValue(ASTNode node) {
    return switch (node) {
      case VarExpr v -> true;
      case ArrayAccessExpr a -> isLValue(a.array);
      case FieldAccessExpr fa -> isLValue(fa.structure);
      case ValueAtExpr va -> isLValue(va.expr);
      default -> false;
    };
  }

  private boolean isRecursiveWithoutPointer(StructTypeDecl std) {
    return std.fields.stream()
        .anyMatch(
            field -> field.type instanceof StructType st && st.name.equals(std.structType.name));
  }
}
