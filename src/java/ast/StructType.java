package ast;

import java.util.List;

/**
 * StructType ::= String represents a struct type (the String is the name of the declared struct
 * type)
 */
public final class StructType implements Type {
  // Name of the structure type
  public final String name;

  public StructType(String name) {
    this.name = name;
  }
  // No children for structure types
  @Override
  public List<ASTNode> children() {
    return List.of();
  }
}
