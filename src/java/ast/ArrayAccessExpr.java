package ast;

import java.util.List;

// an array access expression (arr[index])
public final class ArrayAccessExpr extends Expr {
  // The array being accessed
  public final Expr array;
  public final List<Expr> indices; // The list of indices (for multi-dimensional access)
  // The index expression
  public final Expr index;

  public ArrayAccessExpr(Expr array, List<Expr> indices, Expr index) {
    this.array = array;
    this.indices = indices;
    this.index = index;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(array, index);
  }
}
