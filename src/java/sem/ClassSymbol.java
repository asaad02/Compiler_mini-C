package sem;

import ast.*;
import java.util.*;

public class ClassSymbol extends Symbol {
  public final String parentName;
  // The name of the parent class
  public ClassSymbol parent;
  // the fields and methods of the class
  public final Map<String, Type> fields = new HashMap<>();
  // the methods of the class
  public final Map<String, FunSymbol> methods = new HashMap<>();

  public ClassSymbol(String name, String parentName) {
    super(name);
    this.parentName = parentName;
  }

  // add a field to the class
  public void addField(String name, Type t) {
    fields.put(name, t);
  }

  // add a method to the class
  public void addMethod(FunSymbol m) {
    methods.put(m.name, m);
  }

  // check if the class has a field
  public Type getField(String name) {
    Type t = fields.get(name);
    return t != null ? t : (parent != null ? parent.getField(name) : null);
  }

  // check if the class has a method
  public FunSymbol getMethod(String name) {
    FunSymbol m = methods.get(name);
    return m != null ? m : (parent != null ? parent.getMethod(name) : null);
  }
}
