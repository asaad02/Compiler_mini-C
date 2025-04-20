package gen;

import ast.ClassDecl;
import ast.Decl;
import ast.Program;
import ast.VarDecl;
import gen.asm.*;
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
    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        // Build vtable
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
        // after vptr (4 bytes) fields start at offset 0
        int offset = 0;
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
    // assume all fields are 4 byte (
    return 4;
  }
}
