package ast;

import java.util.List;

// represents a typecast expression ((int) x).
public final class TypecastExpr extends Expr {
  // The type to cast to
  public final Type type;
  // The expression being cast
  public final Expr expr;

  public TypecastExpr(Type type, Expr expr) {
    this.type = type;
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(type, expr);
  }
}
