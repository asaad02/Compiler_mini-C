package ast;

import java.util.ArrayList;
import java.util.List;

// a function call expression (func(arg1, arg2)).
public final class FunCallExpr extends Expr {
  // Function name
  public final String name;
  // List of arguments passed to the function
  public final List<Expr> args;

  public FunCallExpr(String name, List<Expr> args) {
    this.name = name;
    this.args = args;
  }

  @Override
  public List<ASTNode> children() {
    return new ArrayList<>(args);
  }
}
