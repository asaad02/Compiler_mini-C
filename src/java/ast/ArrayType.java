package ast;

import java.util.List;

// an array type with a  element type and size.
public final class ArrayType implements Type {
  // type of elements in the array
  public final Type elementType;
  // Size of the array
  public final int size;

  public ArrayType(Type elementType, int size) {
    this.elementType = elementType;
    this.size = size;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(elementType);
  }
}
