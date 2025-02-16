package ast;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class FunDecl extends Decl {
  public final List<VarDecl> params;

  public FunDecl(Type type, String name, List<VarDecl> params) {
    this.type = type;
    this.name = name;
    this.params = params;
  }

  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<ASTNode>();
    children.add(type);
    children.addAll(params);
    return children;
  }

  public List<Type> getParamTypes() {
    return params.stream().map(vd -> vd.type).collect(Collectors.toList());
  }
}
