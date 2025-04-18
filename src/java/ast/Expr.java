package ast;

/** represents all expression types in the AST. */
/*
* Expressions
* Expr       ::= IntLiteral | StrLiteral | ChrLiteral |
* VarExpr | FunCallExpr | BinOp | ArrayAccessExpr |
*  FieldAccessExpr | ValueAtExpr | AddressOfExpr |
*  SizeOfExpr | TypecastExpr | Assign |
*  InstanceFunCallExpr | NewInstance // Part V

*/
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
        Assign,
        NewInstance,
        InstanceFunCallExpr {
  // Expr       ::= IntLiteral | StrLiteral | ChrLiteral | VarExpr | FunCallExpr | BinOp |
  // ArrayAccessExpr | FieldAccessExpr | ValueAtExpr | AddressOfExpr | SizeOfExpr | TypecastExpr |
  // Assign
  public Type type; // Type to be filled in by the type analyser
}
