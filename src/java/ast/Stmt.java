package ast;

/** stmt represents all statement types in the AST. */
public abstract sealed class Stmt implements ASTNode
    permits Block, While, If, Return, Continue, Break, ExprStmt {
  // the type of the statement
  public BaseType type = BaseType.NONE;
}
