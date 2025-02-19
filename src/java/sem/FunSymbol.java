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
    if (decl != null) {
      return decl.params.stream().map(p -> p.type).collect(Collectors.toList());
    } else {
      return def.params.stream().map(p -> p.type).collect(Collectors.toList());
    }
  }
}
