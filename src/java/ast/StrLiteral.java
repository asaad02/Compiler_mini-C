package ast;

import java.util.List;

// represents a string literal ("Hello, World!")
public final class StrLiteral extends Expr {
  // The string literal value
  public final String value;

  public StrLiteral(String value) {
    this.value = value;
  }

  @Override
  public List<ASTNode> children() {
    return List.of();
  }
}
