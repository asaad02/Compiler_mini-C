package ast;

import java.util.ArrayList;
import java.util.List;

/** Represents a structure type declaration with a list of fields. */
public final class StructTypeDecl extends Decl {
  public final List<VarDecl> fields; // List of variable declarations as fields in the struct
  public final StructType structType; // The struct type
  public final List<StructTypeDecl> nestedStructs; // List of nested struct declarations

  public StructTypeDecl(
      StructType structType, List<VarDecl> fields, List<StructTypeDecl> nestedStructs) {
    this.structType = structType;
    this.fields = fields;
    this.nestedStructs = nestedStructs; // Store nested structs
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>(fields);
    children.addAll(nestedStructs);
    return children;
  }
}
