package sem;

import java.util.*;

public class Scope {
  private final Scope outer;
  private final Map<String, VarSymbol> variables = new HashMap<>();
  private final Map<String, FunSymbol> functions = new HashMap<>();
  private final Map<String, StructSymbol> structs = new HashMap<>();
  private final Map<String, ClassSymbol> classes = new HashMap<>();
  private final List<String> orderedDeclarations = new ArrayList<>();

  public Scope(Scope outer) {
    this.outer = outer;
  }

  public Scope() {
    this(null);
  }

  public VarSymbol lookupVariable(String name) {
    VarSymbol v = variables.get(name);
    if (v != null) return v;
    return outer != null ? outer.lookupVariable(name) : null;
  }

  public FunSymbol lookupFunction(String name) {
    FunSymbol f = functions.get(name);
    if (f != null) return f;
    return outer != null ? outer.lookupFunction(name) : null;
  }

  public StructSymbol lookupStruct(String name) {
    StructSymbol s = structs.get(name);
    if (s != null) return s;
    return outer != null ? outer.lookupStruct(name) : null;
  }

  public ClassSymbol lookupClass(String name) {
    ClassSymbol c = classes.get(name);
    if (c != null) return c;
    return outer != null ? outer.lookupClass(name) : null;
  }

  public void put(Symbol sym) {
    if (sym instanceof VarSymbol vs) {
      variables.put(vs.name, vs);
    } else if (sym instanceof FunSymbol fs) {
      functions.put(fs.name, fs);
    } else if (sym instanceof StructSymbol ss) {
      structs.put(ss.name, ss);
    } else if (sym instanceof ClassSymbol cs) {
      classes.put(cs.name, cs);
    } else {
      throw new IllegalStateException("Unknown symbol type: " + sym);
    }
    orderedDeclarations.add(sym.name);
  }

  public Symbol lookupCurrent(String name) {
    if (variables.containsKey(name)) return variables.get(name);
    if (functions.containsKey(name)) return functions.get(name);
    if (structs.containsKey(name)) return structs.get(name);
    if (classes.containsKey(name)) return classes.get(name);
    return null;
  }

  public boolean isShadowed(String name) {
    Scope o = this.outer;
    while (o != null) {
      if (o.variables.containsKey(name)) {
        return true;
      }
      o = o.outer;
    }
    return false;
  }

  public boolean isDeclaredBeforeUse(String name) {
    if (orderedDeclarations.contains(name)) return true;

    // Allow function calls if declaration exists
    if (lookupFunction(name) != null) {
      return true;
    }
    return outer != null && outer.isDeclaredBeforeUse(name);
  }

  public void trackDeclaration(String name) {
    orderedDeclarations.add(name);
  }

  public boolean isDeclaredBefore(String name, String reference) {
    return orderedDeclarations.indexOf(name) < orderedDeclarations.indexOf(reference);
  }
}
