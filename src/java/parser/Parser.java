package parser;

import ast.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import lexer.Token;
import lexer.Token.Category;
import lexer.Tokeniser;
import util.CompilerPass;

/** @author cdubach */
public class Parser extends CompilerPass {

  private Token token;

  private Queue<Token> buffer = new LinkedList<>();

  private final Tokeniser tokeniser;

  public Parser(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  public Program parse() {
    // get the first token
    nextToken();

    return parseProgram();
  }

  // private int error = 0;
  private Token lastErrorToken;

  private void error(Category... expected) {

    if (lastErrorToken == token) {
      // skip this error, same token causing trouble
      return;
    }

    StringBuilder sb = new StringBuilder();
    String sep = "";
    for (Category e : expected) {
      sb.append(sep);
      sb.append(e);
      sep = "|";
    }
    String msg = "Parsing error: expected (" + sb + ") found (" + token + ") at " + token.position;
    System.out.println(msg);

    incError();
    lastErrorToken = token;
  }

  /*
   * Look ahead the i^th element from the stream of token.
   * i should be >= 1
   */
  private Token lookAhead(int i) {
    // ensures the buffer has the element we want to look ahead
    while (buffer.size() < i) buffer.add(tokeniser.nextToken());

    int cnt = 1;
    for (Token t : buffer) {
      if (cnt == i) return t;
      cnt++;
    }

    assert false; // should never reach this
    return tokeniser.nextToken();
  }

  /*
   * Consumes the next token from the tokeniser or the buffer if not empty.
   */
  private void nextToken() {
    if (!buffer.isEmpty()) token = buffer.remove();
    else token = tokeniser.nextToken();
  }

  /*
   * If the current token is equals to the expected one, then skip it, otherwise report an error.
   */
  private Token expect(Category... expected) {
    for (Category e : expected) {
      if (e == token.category) {
        Token ret = token;
        nextToken();
        return ret;
      }
    }
    error(expected);
    return token;
  }

  /*
   * Returns true if the current token is equals to any of the expected ones.
   */
  private boolean accept(Category... expected) {
    for (Category e : expected) {
      if (e == token.category) return true;
    }
    return false;
  }
  /*
   *  ===================== PROGRAM PARSING =====================
   * program    ::= (include)* (structdecl | vardecl | fundecl | fundef)* EOF
   * The program top AST node (a list of declarations)
   * AST : Program    ::= (Decl)*
   * A program consists of zero or more declarations.
   */
  private Program parseProgram() {
    // includes are ignored, so does not need to return an AST node
    parseIncludes();
    // decls is the list of declarations in the program (Program    ::= (Decl)*)
    List<Decl> decls = new ArrayList<>();
    // while we have not reached the end of the file
    while (!accept(Category.EOF)) {
      decls.add(parseDecl());
    }
    // expect the end of file
    expect(Category.EOF);
    // return the program AST node
    return new Program(decls);
  }

  /*
   * ===================== INCLUDE PARSING =====================
   * Parsing includes for the program
   * program    ::= (include)*
   * include    ::= "#include" STRING_LITERAL
   * The include directive is not part of the AST
   */
  private void parseIncludes() {
    if (accept(Category.INCLUDE)) {
      nextToken();
      expect(Category.STRING_LITERAL);
      parseIncludes();
    }
  }

  private Decl parseDecl() {
    /*
     *  ===================== STRUCT DECLARATION PARSING =====================
     * structdecl ::= structtype "{" (vardecl)+ "}" ";"
     * structtype ::= "struct" IDENT
     * if the token is a struct and we have an identifier and a left brace ["{"]
     * AST : StructType  ::= String
     * AST : StructTypeDecl ::= StructType VarDecl*
     * represents a struct type (the String is the name of the declared struct type)Struct declaration
     */
    if (token.category == Category.STRUCT
        && lookAhead(1).category == Category.IDENTIFIER
        && lookAhead(2).category == Category.LBRA) {
      Type structType = structtype();
      return parseStructDecl(structType);
    }
    // if the token is an integer or char or void {fundec | fundef | vardecl}
    else if (accept(Category.INT, Category.CHAR, Category.VOID)) {
      // type  ::= ("int" | "char" | "void" | structtype) ("*")*
      parseType();
      // consume the Identifier [IDENTIFIER]
      expect(Category.IDENTIFIER);
      if (accept(Category.LPAR)) {
        /*
         *  ===================== FUNCTION PARSING =====================
         * function declaration | function definition
         * fundecl   ::= type IDENT "(" params ")" ";"
         * fundef    ::= type IDENT "(" params ")" block
         * AST:
         * FunDecl definition (the String is the name of the FunDecl)
         * funDef     ::= Type String VarDecl* Block
         * Function prototype (the String is the name of the function)
         * FunDecl    ::= Type String VarDecl*
         */
        // return empty AST node for now
        return null;
      }
      // if it's not LPAR ['('], then it's a variable declaration
      else {
        /*
         * ===================== VARIABLE DECLARATION PARSING =====================
         * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
         * Variable declaration
         * VarDecl    ::= Type String
         */
        return null;
        // return parseVarDecls('o');
      }
    }
    throw new RuntimeException("Unexpected token in declaration: " + token);
  }
  /*
   * ===================== STRUCT TYPE PARSING =====================
   * structtype ::= "struct" IDENT
   * AST : StructType  ::= String
   */
  private StructType structtype() {
    // expect the token to be a struct
    expect(Category.STRUCT);
    // expect the token to be an identifier and return the struct type AST node
    return new StructType(expect(Category.IDENTIFIER).data);
  }
  /*
   * ===================== TYPE PARSING =====================
   * type ::= ("int" | "char" | "void" | structtype) ("*")*
   * Type        ::= BaseType | PointerType | StructType | ArrayType
   * NONE should be used mostly for statements and UNKNOWN in case where no type can be inferred
   * BaseType    ::= INT | CHAR | VOID | NONE | UNKNOWN
   * use to represent pointers to other types
   * PoInterType ::= Type
   * represents a struct type (the String is the name of the declared struct type)
   * StructType  ::= String
   * Type represents the element type, Int represents the number of elements in the declared array
   * ArrayType   ::= Type Int
   */
  private Type parseType() {
    /*
     * Check the type ("int" | "char" | "void" | structtype ) ("*")*
     * if the token is an integer or char or void
     * BaseType ::= INT | CHAR | VOID | NONE | UNKNOWN
     * PoInterType ::= Type
     */
    if (accept(Category.INT, Category.CHAR, Category.VOID)) {
      // expect the token to be an integer or char or void
      Token typeToken = expect(Category.INT, Category.CHAR, Category.VOID);
      // baseType is the base type of the token
      Type baseType;
      // return the base type
      switch (typeToken.category) {
        case INT:
          baseType = BaseType.INT;
          break;
        case CHAR:
          baseType = BaseType.CHAR;
          break;
        case VOID:
          baseType = BaseType.VOID;
          break;
        default:
          baseType = BaseType.UNKNOWN;
      }
      /*
       * if the token is an asterisk ["*"]
       * PoInterType ::= Type
       */
      while (accept(Category.ASTERISK)) {
        nextToken();
        return new PointerType(parseType());
      }
      return baseType;
    }
    /*
     * if the token is a struct and we have an identifier and a left brace ["{"]
     * StructType  ::= String
     * StructTypeDecl ::= StructType VarDecl*
     * represents a struct type (the String is the name of the declared struct type)Struct declaration
     */

    /*
     * ArrayType   ::= Type Int
     * if the token is a left square bracket ["["]
     * type ::= ("int" | "char" | "void" | structtype) ("*")*
     * Type        ::= BaseType | PointerType | StructType | ArrayType
     */
    else if (accept(Category.LSBR)) {
      Type elementType = parseType();
      expect(Category.RSBR);
      return new ArrayType(elementType, Integer.parseInt(expect(Category.INT_LITERAL).data));

    }
    /*
     * if its LPAR ['('] then it's a type
     * type ::= ("int" | "char" | "void" | structtype) ("*")*
     * PoInterType ::= Type
     */
    else if (accept(Category.LPAR)) {
      nextToken();
      Type innerType = parseType();
      expect(Category.RPAR);
      return innerType;
    }
    /*
     * if the type is not int, char, void, struct, or *, then it's an error
     */
    else {
      // if the type is not int, char, void, struct, or *, then it's an error
      error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
      // return unknown type
      return BaseType.UNKNOWN;
    }
  }
  /*
   *  ===================== STRUCT DECLARATION PARSING =====================
   * structdecl ::= structtype "{" (vardecl)+ "}" ";"
   * StructTypeDecl ::= StructType VarDecl*
   * represents a struct type (the String is the name of the declared struct type)Struct declaration
   * structdecl ::= structtype "{" (vardecl)+ "}" ";"    # structure declaration
   */
  private StructTypeDecl parseStructDecl(Type structType) {
    // expect the left brace ["{"]
    expect(Category.LBRA);
    // varDecls is the list of variable declarations in the struct
    List<VarDecl> varDecls = new ArrayList<>();
    // while we have not reached the right brace ["}"]
    while (!accept(Category.RBRA)) {
      Type type = parseType();
      String varName = expect(Category.IDENTIFIER).data;
      varDecls.add(parseVarDecl(type, varName));
    }
    // expect the right brace ["}"]
    expect(Category.RBRA);
    // expect the semicolon [";"]
    expect(Category.SC);
    // return the struct type declaration AST node
    return new StructTypeDecl((StructType) structType, varDecls);
  }
  /*
   * ===================== VARIABLE DECLARATION PARSING =====================
   * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
   * Variable declaration
   * VarDecl    ::= Type String
   */
  private VarDecl parseVarDecl(Type type, String name) {
    // Handle array declarations (e.g., int a[10];)
    while (accept(Category.LSBR)) {
      nextToken();
      int size = Integer.parseInt(expect(Category.INT_LITERAL).data);
      expect(Category.RSBR);
      type = new ArrayType(type, size);
    }

    expect(Category.SC); // Semicolon after variable declaration
    return new VarDecl(type, name);
  }
}
