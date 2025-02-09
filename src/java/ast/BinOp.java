package ast;

import java.util.List;

// a binary operation (a + b).
public final class BinOp extends Expr {
  // Left operand
  public final Expr left;
  // Operator
  public final Op op;
  // Right operand
  public final Expr right;

  public BinOp(Expr left, Op op, Expr right) {
    this.left = left;
    this.op = op;
    this.right = right;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(left, right);
  }
}
