package ast;

import java.util.List;

// sizeof expression to get the size of a type.
public final class SizeOfExpr extends Expr {
  // The type whose size is being queried
  public final Type type;

  public SizeOfExpr(Type type) {
    this.type = type;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(type);
  }
}
