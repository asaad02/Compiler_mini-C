package ast;

/**
 * represents all expression types in the AST.
 */
public sealed abstract class Expr implements ASTNode
        permits IntLiteral, StrLiteral, ChrLiteral, VarExpr, FunCallExpr, BinOp, ArrayAccessExpr, FieldAccessExpr, ValueAtExpr, AddressOfExpr, SizeOfExpr, TypecastExpr, Assign ,UnaryOp  {

    public Type type; // Type to be filled in by the type analyser
}
