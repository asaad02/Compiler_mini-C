package sem;

import ast.*;
import java.util.HashMap;
import java.util.Map;

public class StructSymbol extends Symbol {
  public StructTypeDecl std;
  private final Map<String, Type> fieldTypes;

  public StructSymbol(StructTypeDecl std) {
    super(std.structType.name);
    this.std = std;
    this.fieldTypes = new HashMap<>();

    // Store field names & types
    for (VarDecl field : std.fields) {
      fieldTypes.put(field.name, field.type);
    }
  }

  // Get type of a field
  public Type getFieldType(String name) {
    return fieldTypes.get(name);
  }
}
