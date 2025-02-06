package parser;

import java.util.LinkedList;
import java.util.Queue;
import ast.Decl;
import ast.Program;
import ast.StructTypeDecl;
import lexer.Token;
import lexer.Token.Category;
import lexer.Tokeniser;
import util.CompilerPass;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;


/**
 * @author cdubach
 */
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
    // debugging the token and seeing the next token in the buffer
    // System.out.println("Token: " + token + " Buffer: " + buffer);
  }

  /*
   * If the current token is equals to the expected one, then skip it, otherwise report an error.
   */
  private Token expect(Category... expected) {
    for (Category e : expected) {
      if (e == token.category) {
        Token ret = token;
        // print the token and the expected token
        // System.out.println("Token: " + ret + " Expected: " + e);
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

  private Program parseProgram() {

    // parsing the include
    /*
     * program    ::= (include)* (structdecl | vardecl | fundecl | fundef)* EOF
     */

    /*
     * Parsing includes for the program
     * program    ::= (include)*
     * include    ::= "#include" STRING_LITERAL
     */
    parseIncludes();

        List<Decl> decls = new ArrayList<>();
    /*
     * program    ::= (structdecl | vardecl | fundecl | fundef)* EOF
     */
    parse_structdecl_VarDecl_fundecl_fundef();
    // expect the end of file
    expect(Category.EOF);
  }
  /*
   * program    ::= ( structdecl | vardecl | fundecl | fundef)* EOF
   */
  private void parse_structdecl_VarDecl_fundecl_fundef() {
    while (accept(Category.STRUCT, Category.INT, Category.CHAR, Category.VOID)
        && !accept(Category.EOF)) {
      // Struct declaration
      // structdecl ::= structtype "{" (vardecl)+ "}" ";"    # structure declaration
      // if the token is a struct and we have an identifier and a left brace ["{"]
      if (token.category == Category.STRUCT
          && lookAhead(1).category == Category.IDENTIFIER
          && lookAhead(2).category == Category.LBRA) {
        // parsing the type of the struct
        // type  ::= ("int" | "char" | "void" | structtype) ("*")*
        parseType();
        // structdecl ::= structtype "{" (vardecl)+ "}" ";"    # structure declaration
        decls.add(parseStructDecl());
      }
      // variable declaration | function declaration | function definition
      // if the token is an integer or char or void {fundec | fundef | vardecl}
      else {
        // type  ::= ("int" | "char" | "void" | structtype) ("*")*
        parseType();
        // consume the Identifier [IDENTIFIER]
        expect(Category.IDENTIFIER);
        // check the left parenthesis ['('] then it's a function declaration or definition
        if (accept(Category.LPAR)) {
          // fundecl   ::= type IDENT "(" params ")" ";"
          // fundef    ::= type IDENT "(" params ")" block
          parseFuncDefAndDec();
        }
        // to be completed ...

        expect(Category.EOF);
        return new Program(decls);
    }

    // includes are ignored, so does not need to return an AST node
    private void parseIncludes() {
        if (accept(Category.INCLUDE)) {
            nextToken();
            expect(Category.STRING_LITERAL);
            parseIncludes();
        }
    }

    private StructTypeDecl parseStructDecl(){
        expect(Category.STRUCT);
        Token id = expect(Category.IDENTIFIER);
        expect(Category.LBRA);
        // to be completed ...
        return null; // to be changed
    }



    // to be completed ...
}
