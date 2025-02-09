package ast;

import java.util.ArrayList;
import java.util.List;

// represents unary operations like +, -, *, & address of
public final class UnaryOp extends Expr {
  // Operator (+, -, *, &)
  public final Op op;
  // Operand
  public final Expr expr;

  public UnaryOp(Op op, Expr expr) {
    this.op = op;
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>();
    children.add(expr);
    return children;
  }
}
