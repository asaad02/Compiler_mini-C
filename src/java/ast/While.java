package ast;

import java.util.List;

// represents a while loop statement.
public final class While extends Stmt {
  // The condition to check before each loop iteration
  public final Expr condition;
  // The body of the loop
  public final Stmt body;

  public While(Expr condition, Stmt body) {
    this.condition = condition;
    this.body = body;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(condition, body);
  }
}
