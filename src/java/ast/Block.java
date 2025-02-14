package ast;

import java.util.ArrayList;
import java.util.List;

// Represents a code block containing variable declarations and statements.
public final class Block extends Stmt {
  private final List<ASTNode> elements; // Store both VarDecl and Stmt together

  public Block(List<ASTNode> elements) {
    this.elements = elements;
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>();
    children.addAll(elements);
    return children;
  }
}
