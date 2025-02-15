package sem;

import ast.*;
import java.util.HashSet;
import java.util.Set;

// StructSymbol stores struct declarations
public class StructSymbol extends Symbol {
  public StructTypeDecl std;
  private final Set<String> fieldNames;

  public StructSymbol(StructTypeDecl std) {
    super(std.structType.name);
    this.std = std;
    this.fieldNames = new HashSet<>();

    // store field names
    for (VarDecl field : std.fields) {
      fieldNames.add(field.name);
    }
  }

  // check if a field exists in the struct
  public boolean hasField(String name) {
    return fieldNames.contains(name);
  }
}
