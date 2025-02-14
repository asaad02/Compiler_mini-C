package sem;

import ast.*;

// StructSymbol stores struct declarations.
public class StructSymbol extends Symbol {
  public StructTypeDecl std;

  public StructSymbol(StructTypeDecl std) {
    // The name of the struct is the name of the struct type declaration
    super(std.structType.name);
    // Store the struct type declaration
    this.std = std;
  }
}
