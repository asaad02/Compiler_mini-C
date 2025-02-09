package ast;

import java.util.List;

// a continue statement in loops.
public final class Continue extends Stmt {
  @Override
  public List<ASTNode> children() {
    // there no children for continue statement
    return List.of();
  }
}
