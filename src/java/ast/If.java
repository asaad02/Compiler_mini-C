package ast;

import java.util.ArrayList;
import java.util.List;

// if-statement with optional else-branch.
public final class If extends Stmt {
  //  condition to evaluate
  public final Expr condition;
  //  statement executed if the condition is true
  public final Stmt thenBranch;
  //  optional statement executed if the condition is false
  public final Stmt elseBranch;

  public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
    this.condition = condition;
    this.thenBranch = thenBranch;
    this.elseBranch = elseBranch;
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>();
    children.add(condition);
    children.add(thenBranch);
    if (elseBranch != null) {
      children.add(elseBranch);
    }
    return children;
  }
}
