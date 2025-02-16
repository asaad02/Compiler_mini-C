package ast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class FunDef extends Decl {
  public final List<VarDecl> params;
  public final Block block;

  public FunDef(Type type, String name, List<VarDecl> params, Block block) {
    this.type = type;
    this.name = name;
    this.params = params;
    this.block = block;
  }

  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<ASTNode>();
    children.add(type);
    children.addAll(params);
    children.add(block);
    return children;
  }

  public List<Type> getParamTypes() {
    return params.stream().map(vd -> vd.type).collect(Collectors.toList());
  }
}
