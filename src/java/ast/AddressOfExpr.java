package ast;

import java.util.List;

// the address-of operator in expressions (&x).
public final class AddressOfExpr extends Expr {
  // The expression to get the address of
  public final Expr expr;

  public AddressOfExpr(Expr expr) {
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(expr);
  }
}
