package ast;

import java.util.List;

// an assignment expression (a = b).
public final class Assign extends Expr {
  // Left-hand side of the assignment
  public final Expr left;
  // Right-hand side of the assignment
  public final Expr right;

  public Assign(Expr left, Expr right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(left, right);
  }
}
