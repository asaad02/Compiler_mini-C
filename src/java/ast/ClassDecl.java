package ast;

import java.util.ArrayList;
import java.util.List;

/** Represents a class declaration, optionally extending a parent class. */
/*
 * ClassDecl ::= (ClassType ClassType | ClassType) (VarDecl)* (FunDef)*
 * Part V - First ClassType is for newly-declared class
 * second one is dedicated to optional parent name
 */
public final class ClassDecl extends Decl {
  public final String name;
  // Name of the extended class or null
  public final String parent;
  // Field declarations
  public final List<VarDecl> fields;
  // Method definitions
  public final List<FunDef> methods;

  public ClassDecl(String name, String parent, List<VarDecl> fields, List<FunDef> methods) {
    this.name = name;
    this.parent = parent;
    this.fields = fields;
    this.methods = methods;
  }

  @Override
  public List<ASTNode> children() {
    List<ASTNode> children = new ArrayList<>();
    children.addAll(fields);
    children.addAll(methods);
    return children;
  }
}
