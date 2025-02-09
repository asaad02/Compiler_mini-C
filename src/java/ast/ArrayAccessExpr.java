package ast;

import java.util.List;

// an array access expression (arr[index])
public final class ArrayAccessExpr extends Expr {
  // The array being accessed
  public final Expr array;
  // The index expression
  public final Expr index;

  public ArrayAccessExpr(Expr array, Expr index) {
    this.array = array;
    this.index = index;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(array, index);
  }
}
