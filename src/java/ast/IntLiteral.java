package ast;

import java.util.List;

// an integer literal (such as 42).
public final class IntLiteral extends Expr {
  // The integer value
  public final int value;

  public IntLiteral(int value) {
    this.value = value;
  }

  @Override
  public List<ASTNode> children() {
    return List.of();
  }
}
