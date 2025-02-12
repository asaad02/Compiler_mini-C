package ast;

import java.util.ArrayList;
import java.util.List;

// block represents a code block containing variable declarations and statements.
public final class Block extends Stmt {
  // Variable declarations
  public List<VarDecl> vds;
  // Statements within the block
  public List<Stmt> stmts;

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
