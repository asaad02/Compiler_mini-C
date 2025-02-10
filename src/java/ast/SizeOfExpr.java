package ast;

import java.util.List;

// sizeof expression to get the size of a type.
public final class SizeOfExpr extends Expr {
  // The type whose size is being queried
  public Type type;
  public Expr expr;

  public SizeOfExpr(Type type) {
    this.type = type;
  }

  public SizeOfExpr(Expr expr) {
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(type);
  }
}
