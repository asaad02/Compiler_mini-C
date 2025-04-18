package ast;

import java.util.List;

/*
 * // Typecast expression : (Type)Expr (e.g. (int*) malloc(4))
 * Part V - Type can be an ancestor's type of Expr's type if we are dealing with classes
 * TypecastExpr ::= Type Expr
 */
// represents a typecast expression ((int) x).
public final class TypecastExpr extends Expr {
  // The type to cast to
  public final Type type;
  // The expression being cast
  public final Expr expr;

  public TypecastExpr(Type type, Expr expr) {
    this.type = type;
    this.expr = expr;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(type, expr);
  }
}
