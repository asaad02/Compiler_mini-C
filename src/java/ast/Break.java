package ast;

import java.util.List;

// represents a break statement in loops.
public final class Break extends Stmt {
  @Override
  public List<ASTNode> children() {
    // there no children for break statement
    return List.of();
  }
}
