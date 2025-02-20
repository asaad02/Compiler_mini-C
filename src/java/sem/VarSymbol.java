package sem;

import ast.*;

// VarSymbol stores variable declarations.
public class VarSymbol extends Symbol {
  public VarDecl vd;

  public VarSymbol(VarDecl vd) {
    // The name of the variable is the name of the variable declaration
    super(vd.name);
    // Store the variable declaration
    this.vd = vd;
  }

  public Type getType() {
    return vd.type;
  }
}
