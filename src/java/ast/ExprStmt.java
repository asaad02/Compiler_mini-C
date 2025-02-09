package ast;

import java.util.List;

// an expression statement (x + 1;).
public final class ExprStmt extends Stmt {
  // The expression within the statement
  public final Expr expr;

  public ExprStmt(Expr expr) {
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(expr);
  }
}
