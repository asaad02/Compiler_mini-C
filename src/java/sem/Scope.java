package sem;

import java.util.*;

/** Scope class handles symbol table management and supports nested scopes. */
public class Scope {
  // Reference to the outer scope null for global scope
  private Scope outer;
  // Stores declared symbols in the current scope
  private Map<String, Symbol> symbolTable;
  // Tracks the order of symbol declarations for scoping checks
  private List<String> orderedDeclarations;

  /** nested scope linked to an outer scope. */
  public Scope(Scope outer) {
    this.outer = outer;
    this.symbolTable = new HashMap<>();
    this.orderedDeclarations = new ArrayList<>();
  }

  // constructor for the global scope
  public Scope() {
    this(null);
  }

  /** looks up a symbol in the current and enclosing scopes. */
  public Symbol lookup(String name) {
    Symbol sym = symbolTable.get(name);
    if (sym != null) {
      return sym;
    } else if (outer != null) {
      return outer.lookup(name);
    } else {
      return null;
    }
  }

  /** looks up a symbol only in the current scope without checking parent scopes. */
  public Symbol lookupCurrent(String name) {
    return symbolTable.get(name);
  }

  /** adds a new symbol to the current scope and ensures that a duplicate declaration does not */
  public void put(Symbol sym) {
    symbolTable.put(sym.name, sym);
    // Track the order of declarations
    orderedDeclarations.add(sym.name);
  }

  // tracks a new declaration explicitly for debugging and ensuring correct order.
  public void trackDeclaration(String name) {
    orderedDeclarations.add(name);
  }

  /**
   * ensures variables or functions are declared before use and checks current and outer scopes
   * recursively. allows functions to be used before definition if a declaration exists.
   */
  public boolean isDeclaredBeforeUse(String name) {
    // check if the name is declared in the current scope
    if (orderedDeclarations.contains(name)) {
      return true;
    }

    // allow function calls if either a declaration or definition exists
    Symbol sym = lookup(name);
    if (sym instanceof FunSymbol fs) {
      return fs.decl != null || fs.def != null;
    }

    // recursively check parent scopes
    return outer != null && outer.isDeclaredBeforeUse(name);
  }

  /**
   * shadowing works correctly ,ff an identifier exists in an inner scope, it takes precedence over
   * outer scopes.
   */
  public boolean isShadowed(String name) {
    Scope outerScope = this.outer;

    while (outerScope != null) {
      if (outerScope.lookupCurrent(name) != null) {
        return true;
      }
      outerScope = outerScope.outer;
    }
    return false;
  }

  /** returns a function symbol if the name exists in scope. */
  public FunSymbol lookupFunction(String name) {
    Symbol sym = lookup(name);
    if (sym instanceof FunSymbol fs) {
      return fs;
    } else {
      return null;
    }
  }

  /** ensures variables are distinguished from function names. */
  public VarSymbol lookupVariable(String name) {
    Symbol sym = lookup(name);
    // check if the symbol is a variable
    if (sym instanceof VarSymbol vs) {
      return vs;
    } else {
      return null;
    }
  }

  /** ensures that a function is properly declared before it is called. */
  public boolean isFunctionDeclaredBeforeUse(String name) {
    FunSymbol function = lookupFunction(name);
    // check if the function is declared before use
    if (function != null && (function.decl != null || function.def != null)) {
      return true;
    } else {
      return false;
    }
  }

  /** checks if a variable was declared before another reference for scoping validation */
  public boolean isDeclaredBefore(String name, String reference) {
    // check if the name is declared before the reference
    return orderedDeclarations.indexOf(name) < orderedDeclarations.indexOf(reference);
  }

  /** lookup struct declarations in the current and parent scopes */
  public StructSymbol lookupStruct(String name) {
    Symbol sym = lookup(name);
    return (sym instanceof StructSymbol) ? (StructSymbol) sym : null;
  }
}
