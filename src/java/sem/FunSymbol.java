package sem;

import ast.*;

/**
 * funSymbol stores function declarations and definitions. ensures correct handling of declaration
 * and definition cases.
 */
public class FunSymbol extends Symbol {
  // Stores the function declaration
  public FunDecl decl;
  // Stores the function definition
  public FunDef def;

  public FunSymbol(FunDecl decl) {
    super(decl.name);
    this.decl = decl;
    // Initially no definition
    this.def = null;
  }

  public FunSymbol(FunDef def) {
    super(def.name);
    this.def = def;
    // Initially no declaration
    this.decl = null;
  }

  // storing both declaration and definition if both exist
  public void setDeclaration(FunDecl decl) {
    this.decl = decl;
  }

  public void setDefinition(FunDef def) {
    this.def = def;
  }
}
