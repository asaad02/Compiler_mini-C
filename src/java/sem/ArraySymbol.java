package sem;

import ast.*;

public class ArraySymbol extends Symbol {
  public final ArrayType arrayType;

  public ArraySymbol(String name, ArrayType arrayType) {
    super(name);
    this.arrayType = arrayType;
  }

  public Type getElementType() {
    return arrayType.elementType;
  }

  public int getSize() {
    return arrayType.size;
  }
}
