package ast;

import java.util.List;

/*
 * // Class instantiation expression : ClassType() (e.g. new Course() )
 * NewInstance ::= ClassType // Part V
 */
/** Expression for instantiating a new object: new class Foo(). */
public final class NewInstance extends Expr {
  public final String className;

  public NewInstance(String className) {
    this.className = className;
  }

  @Override
  public List<ASTNode> children() {
    // No sub_expressions
    return List.of();
  }
}
