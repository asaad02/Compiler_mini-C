package ast;

import java.util.List;

// field access in a structure (struct.field).
public final class FieldAccessExpr extends Expr {
  // The structure from which the field is accessed
  public final Expr structure;
  // The field name being accessed
  public final String field;

  public FieldAccessExpr(Expr structure, String field) {
    this.structure = structure;
    this.field = field;
  }

  @Override
  public List<ASTNode> children() {
    return List.of(structure);
  }
}
