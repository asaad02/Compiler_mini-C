package ast;

import java.util.ArrayList;
import java.util.List;

// Represents a code block containing variable declarations and statements.
public final class Block extends Stmt {
  public List<VarDecl> vds;
  public List<Stmt> stmts;

  // Store both VarDecl and Stmt together

  public Block(List<VarDecl> vds, List<Stmt> stmts) {
    this.vds = vds;
    this.stmts = stmts;
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>();
    children.addAll(vds);
    children.addAll(stmts);
    return children;
  }
}
