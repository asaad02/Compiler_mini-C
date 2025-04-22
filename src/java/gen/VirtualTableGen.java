package gen;

import ast.*;
import gen.asm.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * for each class collect inherited + overridden methods into an ordered map methodName to asmLabel
 * build object field layouts for each class.
 */
public class VirtualTableGen extends CodeGen {
  private final Map<String, Map<String, String>> methodLabels = CodeGenContext.getMethodLabels();
  private final Map<String, List<String>> classAncestors = CodeGenContext.getClassAncestors();
  private final Map<String, LinkedHashMap<String, String>> vtables = new LinkedHashMap<>();

  /** Walk all ClassDecls build each vtable & field layout store in CodeGenContext. */
  public void build(Program p) {
    // Build a lookup from class name to its AST node for field inheritance
    Map<String, ClassDecl> classByName = new HashMap<>();
    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        classByName.put(cd.name, cd);
      }
    }

    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        // --- Build vtable ---
        LinkedHashMap<String, String> table = new LinkedHashMap<>();

        // insert ancestor methods
        for (String anc : classAncestors.getOrDefault(cd.name, List.of())) {
          Map<String, String> amap = methodLabels.getOrDefault(anc, Map.of());
          amap.forEach(table::put);
        }
        // override with own methods
        Map<String, String> own = methodLabels.getOrDefault(cd.name, Map.of());
        own.forEach(table::put);
        vtables.put(cd.name, table);

        // build object field layout for this class
        LinkedHashMap<String, Integer> fieldMap = new LinkedHashMap<>();
        int offset = 0;
        for (String anc : classAncestors.getOrDefault(cd.name, List.of())) {
          ClassDecl parent = classByName.get(anc);
          if (parent != null) {
            for (VarDecl f : parent.fields) {
              fieldMap.put(f.name, offset);
              offset += computeFieldSize(f);
            }
          }
        }
        // now append this class's own fields
        for (VarDecl f : cd.fields) {
          fieldMap.put(f.name, offset);
          offset += computeFieldSize(f);
        }
        CodeGenContext.putClassFieldOffsets(cd.name, fieldMap);
      }
    }
    CodeGenContext.setVTables(vtables);
  }

  /** compute a field's size 4 byte aligned */
  private int computeFieldSize(VarDecl f) {
    Type t = f.type;
    if (t instanceof ArrayType at) {
      // BaseType.CHAR and BaseType.INT  occupy 4 bytes
      int elemSize = 4;
      // multiply by all dimensions
      int count = at.dimensions.stream().reduce(1, (a, b) -> a * b);
      return elemSize * count;
    }
    // pointers or ints or chars or object 4 bytes
    return 4;
  }
}
