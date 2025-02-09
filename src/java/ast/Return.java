package ast;

import java.util.List;

// a return statement in a function.
public final class Return extends Stmt {
  // The expression to return optional
  public final Expr expr;

  public Return(Expr expr) {
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    // return the expression if it is not null, otherwise return an empty list
    return expr != null ? List.of(expr) : List.of();
  }
}
