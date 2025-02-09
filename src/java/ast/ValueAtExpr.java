package ast;

import java.util.List;

// represents a dereference expression (*ptr).
public final class ValueAtExpr extends Expr {
  // The expression to dereference
  public final Expr expr;

  public ValueAtExpr(Expr expr) {
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(expr);
  }
}
