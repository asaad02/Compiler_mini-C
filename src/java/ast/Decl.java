package ast;

/*
 * Declarations/definitions
 * Decl       ::= ClassDecl | StructTypeDecl | VarDecl | FunDecl | FunDef // Part V
 */
public abstract sealed class Decl implements ASTNode
    permits FunDecl, FunDef, StructTypeDecl, VarDecl, ClassDecl {

  public Type type;
  public String name;
}
