package ast;

/** represents all expression types in the AST. */
public abstract sealed class Expr implements ASTNode
    permits IntLiteral,
        StrLiteral,
        ChrLiteral,
        VarExpr,
        FunCallExpr,
        BinOp,
        ArrayAccessExpr,
        FieldAccessExpr,
        ValueAtExpr,
        AddressOfExpr,
        SizeOfExpr,
        TypecastExpr,
        Assign {
  // Expr       ::= IntLiteral | StrLiteral | ChrLiteral | VarExpr | FunCallExpr | BinOp |
  // ArrayAccessExpr | FieldAccessExpr | ValueAtExpr | AddressOfExpr | SizeOfExpr | TypecastExpr |
  // Assign
  public Type type; // Type to be filled in by the type analyser
}
