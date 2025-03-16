package ast;

import java.util.ArrayList;
import java.util.List;

// an array type with a  element type and size.
public final class ArrayType implements Type {
  // type of elements in the array
  public final Type elementType;
  // Dimensions of the array
  public final List<Integer> dimensions;

  // Size of the array
  public final int size;

  public ArrayType(Type elementType, List<Integer> dimensions, int size) {
    this.elementType = elementType;
    this.size = size;
    this.dimensions = new ArrayList<>(dimensions);
  }

  public int getNumDimensions() {
    return dimensions.size();
  }

  public int getDimensionSize(int i) {
    if (i < 0 || i >= dimensions.size()) {
      throw new IndexOutOfBoundsException("[ArrayType] ERROR: Invalid dimension index " + i);
    }
    return dimensions.get(i);
  }

  @Override
  public List<ASTNode> children() {
    return List.of(elementType);
  }
}
