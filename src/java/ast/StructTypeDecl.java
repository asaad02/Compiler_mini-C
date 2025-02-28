package ast;

import java.util.ArrayList;
import java.util.List;

/** Represents a structure type declaration with a list of fields. */
public final class StructTypeDecl extends Decl {
  public final List<VarDecl> fields; // List of variable declarations as fields in the struct
  public final StructType structType; // The struct type

  public StructTypeDecl(StructType structType, List<VarDecl> fields) {
    this.structType = structType;
    this.fields = fields;
  }

  @Override
  public List<ASTNode> children() {
    return new ArrayList<>(fields);
  }
}
