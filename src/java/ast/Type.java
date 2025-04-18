package ast;

/*
 * Type ::= BaseType | PointerType | StructType | ArrayType | ClassType
 */
public sealed interface Type extends ASTNode
    permits BaseType, PointerType, StructType, ArrayType, ClassType {}
