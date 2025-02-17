package sem;

import ast.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * funSymbol stores function declarations and definitions. ensures correct handling of declaration
 * and definition cases.
 */
public class FunSymbol extends Symbol {
  public FunDecl decl;
  public FunDef def;
  public Type type;

  public FunSymbol(FunDecl decl) {
    super(decl.name);
    this.decl = decl;
    this.def = null;
    this.type = decl.type;
  }

  public FunSymbol(FunDef def) {
    super(def.name);
    this.def = def;
    this.decl = null;
    this.type = def.type;
  }

  public void setDeclaration(FunDecl decl) {
    this.decl = decl;
    this.type = decl.type;
  }

  public void setDefinition(FunDef def) {
    this.def = def;
    this.type = def.type;
  }

  public List<Type> getParamTypes() {
    return (def != null)
        ? def.params.stream().map(param -> param.type).collect(Collectors.toList())
        : (decl != null)
            ? decl.params.stream().map(param -> param.type).collect(Collectors.toList())
            : List.of();
  }
}
