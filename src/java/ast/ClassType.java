package ast;

import java.util.List;

/*
 * classtype  ::= "class" IDENT   # Part V
 * ClassType	::= String 			  // Part V
 * represents a class type (the String is the name of the declared class type)
 */

public final class ClassType implements Type {
  public final String name;
  // vtable label
  public String vtableLabel;

  public ClassType(String name) {
    this.name = name;
    this.vtableLabel = null;
  }

  // setters and getters for vtableLabel
  public void setVTableLabel(String lbl) {
    this.vtableLabel = lbl;
  }

  public String getVTableLabel() {
    return vtableLabel;
  }

  @Override
  public List<ASTNode> children() {
    // ClassType no child AST nodes
    return List.of();
  }
}
