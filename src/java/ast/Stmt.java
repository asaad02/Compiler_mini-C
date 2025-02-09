package ast;

/**
 * stmt represents all statement types in the AST.
 */
public sealed abstract class Stmt implements ASTNode
        permits Block, While, If, Return, Continue, Break, ExprStmt {}

