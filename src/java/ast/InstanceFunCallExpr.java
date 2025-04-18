package ast;

import java.util.List;

/*
 * // Class function call expression (e.g. Course.hasExam() )
 * InstanceFunCallExpr ::= Expr FunCallExpr
 * Part V - the Expr represents the instance of a class
 */

/** Expression for calling a method on an instance: target.method(args). */
public final class InstanceFunCallExpr extends Expr {
  public final Expr target;
  public final FunCallExpr call;

  public InstanceFunCallExpr(Expr target, FunCallExpr call) {
    this.target = target;
    this.call = call;
  }

  @Override
  public List<ASTNode> children() {
    // Target object and then the function call AST
    return List.of(target, call);
  }
}
