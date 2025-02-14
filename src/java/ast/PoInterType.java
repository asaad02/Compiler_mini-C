package ast;

import java.util.List;

// a pointer type (int*).
public final class PointerType implements Type {
  // The base type the pointer points to
  public final Type baseType;

  public PointerType(Type baseType) {
    this.baseType = baseType;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(baseType);
  }
}
