package sem;

import ast.*;
import java.util.*;

public class TypeAnalyzer extends BaseSemanticAnalyzer {

  private Scope currentScope;
  private Type currentFunctionReturnType;
  private int loopDepth = 0;
  private Set<String> declaredStructs = new HashSet<>();

  public TypeAnalyzer() {
    this.currentScope = new Scope();
  }

  // visits AST nodes and performs type analysis
  public Type visit(ASTNode node) {
    return switch (node) {
      case null -> throw new IllegalStateException("Unexpected null value");

      case Program p -> {
        // System.out.println("Starting Type Analysis for program.");
        for (ASTNode decl : p.decls) {
          visit(decl);
        }
        yield BaseType.NONE;
      }

      case FunDecl fd -> {
        // System.out.println("Processing function declaration: " + fd.name);
        if (currentScope.lookupCurrent(fd.name) != null) {
          error("Function '" + fd.name + "' is already declared in this scope.");
          yield BaseType.UNKNOWN;
        }
        currentScope.put(new FunSymbol(fd));
        yield fd.type;
      }

      case FunDef fd -> {
        System.out.println("[LOG] Processing function definition: " + fd.name);

        FunSymbol existingSymbol = currentScope.lookupFunction(fd.name);
        if (existingSymbol == null) {
          // first time seeing this function, declare it
          FunSymbol newSymbol = new FunSymbol(fd);
          currentScope.put(newSymbol);
        } else {
          // ensure it's not already defined
          if (existingSymbol.def != null) {
            error("Function '" + fd.name + "' is already defined.");
            yield BaseType.UNKNOWN;
          }
          // ensure the declaration matches the definition
          validateFunctionSignature(fd, existingSymbol.decl);
          existingSymbol.setDefinition(fd);
        }

        // function scope
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);
        Set<String> declaredParams = new HashSet<>();

        for (VarDecl param : fd.params) {
          if (declaredParams.contains(param.name)) {
            error("Duplicate parameter name '" + param.name + "' in function '" + fd.name + "'.");
            yield BaseType.UNKNOWN;
          }
          declaredParams.add(param.name);
          currentScope.put(new VarSymbol(param));
        }

        // visit function body
        currentFunctionReturnType = fd.type;
        visit(fd.block);
        currentFunctionReturnType = null;
        currentScope = oldScope;
        yield fd.type;
      }

      case VarDecl vd -> {
        if (vd.type.equals(BaseType.VOID)) {
          error("Variable '" + vd.name + "' cannot be of type void.");
          yield BaseType.UNKNOWN;
        }
        if (vd.type instanceof StructType st && !declaredStructs.contains(st.name)) {
          error("Struct type '" + st.name + "' must be declared before use.");
          yield BaseType.UNKNOWN;
        }
        if (currentScope.lookupCurrent(vd.name) != null) {
          error("Variable '" + vd.name + "' is already declared in this scope.");
          yield BaseType.UNKNOWN;
        }
        currentScope.put(new VarSymbol(vd));
        yield vd.type;
      }

      case StructTypeDecl std -> {
        System.out.println("Checking struct declaration: " + std.structType.name);
        if (declaredStructs.contains(std.structType.name)) {
          error("Struct type '" + std.structType.name + "' is already declared.");
          yield BaseType.UNKNOWN;
        }
        declaredStructs.add(std.structType.name);
        validateStructFields(std);
        yield BaseType.NONE;
      }

      case Block b -> {
        System.out.println("Entering block");
        Scope oldScope = currentScope;
        currentScope = new Scope(oldScope);
        for (ASTNode elem : b.children()) {
          visit(elem);
        }
        currentScope = oldScope;
        System.out.println("Exiting block");
        yield BaseType.NONE;
      }

      case VarExpr v -> {
        System.out.println("Checking variable expression: " + v.name);
        VarSymbol vs = currentScope.lookupVariable(v.name);
        if (vs == null) {
          error("Variable '" + v.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }
        yield vs.vd.type;
      }

      // ===================== FUNCTION CALL =====================
      case FunCallExpr fc -> {
        System.out.println("[LOG] Checking function call: " + fc.name);
        FunSymbol fs = currentScope.lookupFunction(fc.name);
        if (fs == null) {
          error("Function '" + fc.name + "' is not declared.");
          yield BaseType.UNKNOWN;
        }
        // validateFunctionCall(fc, fs);
        yield fs.def != null ? fs.def.type : fs.decl.type;
      }

      case ArrayAccessExpr a -> {
        System.out.println("[LOG] Checking array access");
        Type arrayType = visit(a.array);
        Type indexType = visit(a.index);
        if (!(arrayType instanceof ArrayType at)) {
          error("Attempted array access on non-array type: " + arrayType);
          yield BaseType.UNKNOWN;
        }
        if (!indexType.equals(BaseType.INT)) {
          error("Array index must be of type int.");
          yield BaseType.UNKNOWN;
        }
        yield ((ArrayType) arrayType).elementType;
      }

      case StrLiteral s -> {
        System.out.println("Checking string literal");
        yield new ArrayType(BaseType.CHAR, s.value.length() + 1);
      }

      case BinOp b -> {
        System.out.println("Checking binary operation: " + b.op);
        Type left = visit(b.left);
        Type right = visit(b.right);
        validateBinaryOperation(b, left, right);
        yield BaseType.INT;
      }

      case Assign a -> {
        System.out.println("Checking assignment");
        Type left = visit(a.left);
        Type right = visit(a.right);
        if (!isLValue(a.left)) {
          error("Left-hand side of assignment must be an lvalue.");
        }
        if (!left.equals(right)) {
          error("Type mismatch in assignment.");
        }
        yield left;
      }

      case Return r -> {
        System.out.println("[LOG] Checking return statement");
        if (currentFunctionReturnType == null) {
          error("Return statement outside of function.");
          yield BaseType.UNKNOWN;
        }
        Type returnType = r.expr != null ? visit(r.expr) : BaseType.VOID;
        if (!returnType.equals(currentFunctionReturnType)) {
          error("Return type mismatch.");
        }
        yield returnType;
      }

      default -> {
        System.out.println("[LOG] Skipping node: " + node);
        yield BaseType.UNKNOWN;
      }
    };
  }

  private void validateFunctionSignature(FunDef fd, FunDecl declaredFunction) {
    if (declaredFunction == null) return;
    if (!fd.type.equals(declaredFunction.type)) {
      error("Function '" + fd.name + "' return type mismatch.");
    }
    if (fd.params.size() != declaredFunction.params.size()) {
      error("Function '" + fd.name + "' parameter count mismatch.");
    }
  }

  private void validateStructFields(StructTypeDecl std) {
    for (VarDecl field : std.fields) {
      if (field.type.equals(BaseType.VOID)) {
        error("Field '" + field.name + "' in struct '" + std.structType.name + "' cannot be void.");
      }
    }
  }

  private void validateBinaryOperation(BinOp b, Type left, Type right) {
    if (!left.equals(BaseType.INT) || !right.equals(BaseType.INT)) {
      error("Binary operator '" + b.op + "' requires integer operands.");
    }
  }

  private boolean isLValue(Expr e) {
    return switch (e) {
      case VarExpr v -> true;
      case ArrayAccessExpr a -> isLValue(a.array);
      default -> false;
    };
  }
}
