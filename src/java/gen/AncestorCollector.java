package gen;

import ast.ClassDecl;
import ast.Decl;
import ast.Program;
import java.util.*;

/** for each class build its inheritance chain lassAncestors: className to rootClass parent class */
public class AncestorCollector {
  public static Map<String, List<String>> collect(Program p) {
    // index classes by name
    Map<String, ClassDecl> byName = new HashMap<>();
    for (Decl d : p.decls) {
      if (d instanceof ClassDecl cd) {
        byName.put(cd.name, cd);
      }
    }

    // build chain per class
    Map<String, List<String>> anc = new HashMap<>();
    for (String cls : byName.keySet()) {
      List<String> chain = new ArrayList<>();
      ClassDecl cur = byName.get(cls);
      while (cur.parent != null) {
        ClassDecl par = byName.get(cur.parent);
        // no such parent
        if (par == null) break;
        // insert at front
        chain.add(0, par.name);
        cur = par;
      }
      anc.put(cls, chain);
    }

    CodeGenContext.setClassAncestors(anc);
    return anc;
  }
}
