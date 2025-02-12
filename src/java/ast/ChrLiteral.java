package ast;

import java.util.List;

// a character literal ('a').
public final class ChrLiteral extends Expr {
  // The character literal value
  public final String value;

  public ChrLiteral(String value) {
    this.value = value;
  }

  @Override
  public List<ASTNode> children() {
    // there are no children for a character literal
    return List.of();
  }
}
