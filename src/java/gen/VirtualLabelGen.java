package gen;

import ast.ClassDecl;
import ast.Decl;
import ast.FunDef;
import ast.Program;
import java.util.HashMap;
import java.util.Map;

/**
 * generate a unique ASM label for every class method methodLabels: className to (methodName to
 * label with arity)
 */
public class VirtualLabelGen {
  private final Map<String, Map<String, String>> methodLabels = new HashMap<>();

  /** Walk all top level decls find ClassDecl record each FunDef name with arity. */
  public void collect(Program p) {
    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        System.out.println("[VirtualLabelGen] Processing class: " + cd.name);
        Map<String, String> map = new HashMap<>();
        for (FunDef fd : cd.methods) {
          int arity = fd.params.size();
          // Label format  ClassName_methodName_arity
          String label = cd.name + "_" + fd.name + "_" + arity;
          System.out.println("[VirtualLabelGen] Adding method: " + label);
          map.put(fd.name, label);
        }
        methodLabels.put(cd.name, map);
      }
    }
    CodeGenContext.setMethodLabels(methodLabels);
  }
}
