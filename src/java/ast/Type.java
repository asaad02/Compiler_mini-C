package ast;

public sealed interface Type extends ASTNode permits BaseType, PoInterType, StructType, ArrayType {}
