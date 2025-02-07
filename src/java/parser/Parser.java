package parser;

import ast.Decl;
import ast.Program;
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

  // part 1 : private void parseProgram() {
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
    // part 2 : List<Decl> decls = new ArrayList<>();
    List<Decl> decls = new ArrayList<>();
    /*
     * program    ::= (structdecl | vardecl | fundecl | fundef)* EOF
     */
    parse_structdecl_VarDecl_fundecl_fundef();
    // expect the end of file
    expect(Category.EOF);

    // to be completed ... for part 2
    // code part 2:
    // expect(Category.EOF);
    // return new Program(decls);
    // end code for part 2
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
        // part 1 : parseStructDecl();
        parseStructDecl();

        // part 2 : decls.add(parseStructDecl());
        // decls.add(parseStructDecl());
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
        // part 1 :
        // if it's not LPAR ['('], then it's a variable declaration
        else {
          // vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
          // parse once the variable declaration
          parseVarDecls('o');
        }
      }
    }

    // to be completed ... for part 2
    // code part 2:
    // expect(Category.EOF);
    // return new Program(decls);
    // end code for part 2
  }

  // includes are ignored, so does not need to return an AST node
  private void parseIncludes() {
    if (accept(Category.INCLUDE)) {
      nextToken();
      expect(Category.STRING_LITERAL);
      parseIncludes();
    }
  }
  // part 2 :
  // private StructTypeDecl parseStructDecl(){
  // expect(Category.STRUCT);
  // Token id = expect(Category.IDENTIFIER);
  // expect(Category.LBRA);
  // to be completed ...
  // return null; // to be changed
  // }

  // to be completed  for part 2:

  /*
   * old code for part 1
   */
  /*
   * type ::= ("int" | "char" | "void" | structtype) ("*")*
   */
  private void parseType() {
    // Check the type ("int" | "char" | "void" | structtype ) ("*")*
    if (accept(Category.INT, Category.CHAR, Category.VOID)) {
      // expect the token to be an integer or char or void
      expect(Category.INT, Category.CHAR, Category.VOID);
    } else if (accept(Category.STRUCT)) {
      // Parse a struct type
      // structtype ::= "struct" IDENT
      parseStructType();
    } else {
      error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
    }
    // followed by asterisks
    while (accept(Category.ASTERISK)) {
      // Consume the asterisk token
      nextToken();
    }
  }

  /*
   * structtype ::= "struct" IDENT
   */
  private void parseStructType() {
    // expect the token to be a struct
    expect(Category.STRUCT);
    // expect the token to be an identifier
    expect(Category.IDENTIFIER);
  }

  /*
   * [structdecl]
   * structdecl ::= structtype "{" (vardecl)+ "}" ";"    # structure declaration
   * structtype ::= "struct" IDENT
   * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
   * ";"
   */

  private void parseStructDecl() {
    // structtype ::= "struct" IDENT
    // we parse already the key in parseType()
    // if the token is a left brace ["{"]
    expect(Category.LBRA);
    // parse the at least one variable declaration within the block or more
    // (vardecl)+
    parseVarDecls('+');
    // if the token is a right brace ["}"]
    expect(Category.RBRA);
    // if the token is a semicolon [";"]
    expect(Category.SC);
  }

  /*
   * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
   * variable declaration, (e.g. int a;), or multi-dimensional array declaration, (e.g. int a[2][5];)
   */
  private void parseVarDecls(char mode) {
    // Base case we will check if the token is an integer or char or void or struct
    if (!accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
      // if the mode is '*' then we will return
      if (mode == '*') {
        return;
      }
      // if the mode is '+' then we will throw an error because we need at least one declaration
      else if (mode == '+') {
        error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
      }
      // if the mode is 'o' then we will parse a single declaration
      else if (mode == 'o') {
        // Check for array declarations ['['] for the variable declaration
        while (accept(Category.LSBR) && !accept(Category.EOF)) {
          // Consume '['
          nextToken();
          // Expect array size
          expect(Category.INT_LITERAL);
          // Expect ']'
          expect(Category.RSBR);
        }
        // Expect a semicolon
        expect(Category.SC);
        return;
      }
    }
    // type       ::= ("int" | "char" | "void" | structtype) ("*")*
    parseType();
    // Expect the identifier
    expect(Category.IDENTIFIER);

    // Check for array declarations ['['] for the variable declaration
    while (accept(Category.LSBR) && !accept(Category.EOF)) {
      // Consume '['
      nextToken();
      // Expect array size
      expect(Category.INT_LITERAL);
      // Expect ']'
      expect(Category.RSBR);
    }

    // Expect a semicolon
    expect(Category.SC);

    // handle different mode if + then we will parse more declarations and if * then we will parse
    // zero or more
    if (mode == '+') {
      // Parse more declarations
      parseVarDecls('*');
    } else if (mode == '*') {
      // Parse zero or more declarations
      parseVarDecls('*');
    }
  }

  /*
   * # function definition
   *  fundef    ::= type IDENT "(" params ")" block       # function definition
   *  fundecl   ::= type IDENT "(" params ")" ";"
   */
  private void parseFuncDefAndDec() {
    // params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL"]")*)*]
    parseParams();
    // fundef    ::= type IDENT "(" params ")" block
    // consume the right parenthesis ['{']
    if (accept(Category.LBRA)) {
      // block      ::= "{" (vardecl)* (stmt)* "}"
      // Parse function body
      parseBlock();
    } else {
      /*
       * fundecl   ::= type IDENT "(" params ")" ";"
       * # function declaration
       */
      // consume the semicolon [';']
      expect(Category.SC);
    }
  }
  /*
   * params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL "]")*)* ]
   */
  private void parseParams() {
    // consume the left parenthesis ['(']
    expect(Category.LPAR);
    // if the token is not a right parenthesis [')']
    if (!accept(Category.RPAR) && !accept(Category.EOF)) {
      do {
        // parse the type
        parseType();
        // expect the identifier
        expect(Category.IDENTIFIER);
        // check if it's an array '[' for the parameter
        while (accept(Category.LSBR) && !accept(Category.EOF)) {
          nextToken();
          expect(Category.INT_LITERAL);
          // expect the right square brace ["]"]
          expect(Category.RSBR);
        }
        // check if there's a comma for more parameters
        if (accept(Category.COMMA)) {
          nextToken();
        } else {
          // if no comma so we don't have more parameters so we break the loop
          break;
        }
      } while (true);
    }
    // expect the right parenthesis [')']
    expect(Category.RPAR);
  }

  /*
   * block      ::= "{" (vardecl)* (stmt)* "}"
   */
  private void parseBlock() {
    // consume the left brace ['{']
    expect(Category.LBRA);
    // Parse statements within the block , the block will have the [while, if, return, continue,
    // break, exp]
    while (!accept(Category.RBRA) && !accept(Category.EOF)) {

      // if its a integer or char or void or struct then we will parse the type and the identifier
      // other than that we will parse the statement
      if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
        // parse all the variable declarations within the block
        parseVarDecls('*');
      } else {
        // parse all the statements within the block
        parseStmt();
      }
    }
    // Ensure the block ends with a right brace '}'
    // consume the right brace ['}']
    expect(Category.RBRA);
  }
  /**
   * stmt ::= block | "while" "(" exp ")" stmt # while loop | "if" "(" exp ")" stmt ["else" stmt] #
   * if then else | "return" [exp] ";" # return | exp ";" # expression statement, e.g. a function
   * call | "continue" ";" # continue | "break" ";"
   */
  private void parseStmt() {
    // case 1 : block [if started with ["{"] - we will parse all the statements within the block
    if (accept(Category.LBRA)) {
      // parsing the block
      parseBlock();
      // case 2 : if started with ["while"] - we will parse the while loop
    } else if (accept(Category.WHILE)) {
      parseWhileStmt();
      // case 3 : if started with ["if"] - we will parse the if statement
    } else if (accept(Category.IF)) {
      parseIfStmt();
      // case 4 : if started with ["return"] - we will parse the return statement
    } else if (accept(Category.RETURN)) {
      parseReturnStmt();
      // case 5 : if started with ["continue"] and [Break] we will parse the continue statement
    } else if (accept(Category.CONTINUE, Category.BREAK)) {
      parseContinueAndBreakStmt();
      // case 6 : if started with an expression - we will parse the expression statement
    } else {
      // Parse an expression statement
      parseExp();
      // if it's not a semicolon then we will throw an error
      if (accept(Category.SC)) {
        expect(Category.SC);
      } else {
        error(Category.SC);
        // consume the token
        nextToken();
      }
    }
  }

  /*
   * exp      ::= "(" exp ")"
   *          | exp "=" exp                           # assignment
   *          | (IDENT | INT_LITERAL)
   *          | ("-" | "+") exp
   *          | CHAR_LITERAL
   *          | STRING_LITERAL
   *          | exp (">" | "<" | ">=" | "<=" | "!=" | "==" | "+" | "-" | "/" | "*" | "%" | "||" | "&&") exp  # binary operators
   *          | arrayaccess | fieldaccess | valueat | addressof | funcall | sizeof | typecast
   */

  // parser for the expression statement - exp ";"
  private void parseExp() {
    // parse the expression statement - exp
    parseAssignmentExp();
  }

  // parser for the assignment expression - exp "=" exp
  private void parseAssignmentExp() {
    // parse the logical OR expression (right-hand side)
    parseLogicalOrExp();
    // exp "=" exp
    if (accept(Category.ASSIGN)) {
      nextToken();
      // Parse right-hand side after '=' sign
      parseAssignmentExp();
    }
  }

  // parser for the logical OR expression - exp "||" exp
  private void parseLogicalOrExp() {
    // Parse right-hand side of the logical OR expression
    parseLogicalAndExp();
    // exp "||" exp
    // loop through the logical OR expression
    while (accept(Category.LOGOR)) {
      // Consume '||'
      nextToken();
      // Parse right-hand side of the logical OR expression
      parseLogicalAndExp();
    }
  }

  // Parses a logical AND expression. [exp ::= exp "&&" exp]
  private void parseLogicalAndExp() {
    // Parse left-hand side of the logical AND expression
    parseEqualityExp();
    // loop through the logical AND expression
    while (accept(Category.LOGAND)) {
      // Consume '&&'
      nextToken();
      // Parse right-hand side of the logical AND expression
      parseEqualityExp();
    }
  }

  // Parses an equality expression. [ exp ::= exp ("==" | "!=") exp]
  private void parseEqualityExp() {
    // Parse left-hand side of the equality expression
    parseRelationalExp();
    // loop through the equality expression - exp ("==" | "!=") exp
    while (accept(Category.EQ, Category.NE)) {
      // Consume '==' or '!='
      nextToken();
      // Parse right-hand side of the equality expression
      parseRelationalExp();
    }
  }

  // Parses a relational expression. [exp ::= exp (">" | "<" | ">=" | "<=") exp]
  private void parseRelationalExp() {
    // Parse left-hand side of the relational expression - exp
    parseAdditiveExp();
    while (accept(Category.LT, Category.GT, Category.LE, Category.GE)) {
      // Consume relational operator
      nextToken();
      // Parse right-hand side of the relational expression
      parseAdditiveExp();
    }
  }

  // Parses an additive expression. [exp ::= exp ("+" | "-") exp]
  private void parseAdditiveExp() {
    parseMultiplicativeExp();
    while (accept(Category.PLUS, Category.MINUS)) {
      // Consume '+' or '-'
      nextToken();
      // Parse right-hand side of the additive expression
      parseMultiplicativeExp();
    }
  }

  // Parses a multiplicative expression. [exp ::= exp ("*" | "/" | "%") exp]
  private void parseMultiplicativeExp() {
    // Parse left-hand side of the multiplicative expression
    parseUnaryExp();
    // loop through the multiplicative expression
    while (accept(Category.ASTERISK, Category.DIV, Category.REM)) {
      // Consume '*' or '/' or '%'
      nextToken();
      // Parse right-hand side of the multiplicative expression
      parseUnaryExp();
    }
  }

  // Parses a unary expression. [exp ::= ("-" | "+") exp]
  private void parseUnaryExp() {
    // Handle multiple levels of pointer dereference (e.g., **ptr)
    while (accept(Category.ASTERISK)) {
      // Consume '*'
      nextToken();
    }
    // Check if the token is a unary operator
    if (accept(Category.MINUS, Category.PLUS)) {
      // Consume unary operator
      nextToken();
      // Parse the operand
      parseUnaryExp();
    } else {
      // Parse primary expression
      parsePrimaryExp();
    }
  }

  /**
   * Parses a primary expression. [exp ::= "(" exp ")" | IDENT | INT_LITERAL | CHAR_LITERAL |
   * STRING_LITERAL] | valueat | addressof | funcall | sizeof | typecast | arrayaccess | fieldaccess
   */
  private void parsePrimaryExp() {
    // check if the token is a left parenthesis ["("]
    if (accept(Category.LPAR)) {
      // if the token is a left parenthesis ["("] then we parse the expression within the
      // parenthesis
      Token lookahead = lookAhead(1);
      if (lookahead.category == Category.INT
          || lookahead.category == Category.CHAR
          || lookahead.category == Category.VOID
          || lookahead.category == Category.STRUCT) {
        // parse typecast expression [typecast     ::= "(" type ")" exp  ]
        parseTypeCast();
      } else {
        // consume the left parenthesis ["("]
        nextToken();
        // parse the expression within the parenthesis
        parseExp();
        // consume the right parenthesis [")"]
        expect(Category.RPAR);
      }
      while (accept(Category.DOT)) {
        // parse field access
        parseFieldAccess();
      }
    }
    // check if the token is an identifier
    else if (accept(Category.IDENTIFIER)) {
      // consume the identifier
      nextToken();
      // check if the token is a left parenthesis ["("]
      if (accept(Category.LPAR)) {
        // parse function call
        // funcall      ::= IDENT "(" [ exp ("," exp)* ] ")" # function call
        parseFuncall();
        // if the token is a left square brace ["["]
      } else if (accept(Category.LSBR)) {
        // parse array access
        // arrayaccess  ::= exp "[" exp "]"                  # array access
        parseArrayAccess();
      } // if the token is a dot ["."]
      else if (accept(Category.DOT)) {
        // parse field access
        // fieldaccess  ::= exp "." IDENT
        parseFieldAccess();
      }
    }
    // check if the token is an integer literal ["INT_LITERAL"] or a character literal
    // ["CHAR_LITERAL"] or a string literal ["STRING_LITERAL"]
    else if (accept(Category.INT_LITERAL, Category.CHAR_LITERAL, Category.STRING_LITERAL)) {
      // Parse literals
      nextToken();
    }
    // check if the token is value at ["*"] - Value at operator (pointer indirection)
    // valueat ::= "*" exp
    else if (accept(Category.ASTERISK)) {
      // parse value at operator
      // valueat      ::= "*" exp
      parseValueAt();
    }
    // check if the token is a sizeof ["sizeof"] - Sizeof operator
    // sizeof ::= "sizeof" "(" type ")"
    else if (accept(Category.SIZEOF)) {
      // parse sizeof operator
      // sizeof ::= "sizeof" "(" type ")"
      parseSizeOf();

    } // check if the token is an address of ["&"] - Address of operator
    // addressof ::= "&" exp
    else if (accept(Category.AND)) {
      // parse address of operator
      // addressof ::= "&" exp
      parseAddressOf();
    } // fieldaccess  ::= exp "." IDENT
    else if (accept(Category.DOT)) {
      // parse field access
      parseFieldAccess();
    }
    // check if the token is a left square brace ["["]
    else if (accept(Category.LSBR)) {
      // parse array access
      // arrayaccess  ::= exp "[" exp "]"                  # array access
      parseArrayAccess();
    }
    // typecast ::= "(" type ")" exp
    else if (accept(Category.LPAR)) {
      // parse typecast expression
      // typecast ::= "(" type ")" exp
      parseTypeCast();
    } else {
      // if the token is not any of the above then we will throw an error
      error(
          Category.LPAR,
          Category.IDENTIFIER,
          Category.INT_LITERAL,
          Category.CHAR_LITERAL,
          Category.STRING_LITERAL);
    }
  }
  /*
   * Helper methods for Exp
   */

  /*
   * funcall      ::= IDENT "(" [ exp ("," exp)* ] ")" # function call
   */
  private void parseFuncall() {
    // consume the left parenthesis ["("]
    expect(Category.LPAR);
    // check if the token is not a right parenthesis [")"]
    // optional expression after the left parenthesis
    if (!accept(Category.RPAR) && !accept(Category.EOF)) {
      do {
        // parse the expression
        parseExp();
        // check if there's a comma for more expressions
        if (accept(Category.COMMA)) {
          // consume the comma
          nextToken();
        } else {
          // if no comma so we don't have more expressions so we break the loop
          break;
        }
      } while (true);
    }
    // consume the right parenthesis [")"]
    expect(Category.RPAR);
  }

  /*
   * Parses an array access.
   * arrayaccess ::= exp "[" exp "]"
   */
  private void parseArrayAccess() {
    // expect the left square brace '['
    expect(Category.LSBR);
    // Parse the expression within the square brackets
    parseExp();
    // Parse the closing square bracket ']'
    expect(Category.RSBR);
  }

  /** Parses a structure field access. fieldaccess ::= exp "." IDENT # field access */
  private void parseFieldAccess() {
    // expect the dot operator
    expect(Category.DOT);
    // expect the identifier
    expect(Category.IDENTIFIER);
  }

  /*
   * Parses a pointer dereference.
   * valueat ::= "*" exp
   */
  private void parseValueAt() {
    // expect the asterisk operator [*]
    expect(Category.ASTERISK);
    // parse the expression
    parseExp();
  }

  /*
   * Parses an address-of operation.
   * Grammar: addressof ::= "&" exp
   */
  private void parseAddressOf() {
    // expect the ampersand operator [&]
    expect(Category.AND);
    // parse the expression
    parseExp();
  }

  /*
   * Parses a sizeof operation.
   * sizeof ::= "sizeof" "(" type ")"
   */
  private void parseSizeOf() {
    // expect the sizeof operator ['sizeof']
    expect(Category.SIZEOF);
    // expect the left parenthesis ['(']
    expect(Category.LPAR);
    // parse the type
    parseType();
    // expect the right parenthesis [')']
    expect(Category.RPAR);
  }

  /*
   * Parses a typecast operation.
   * typecast ::= "(" type ")" exp
   */
  private void parseTypeCast() {
    // expect the left parenthesis ['(']
    expect(Category.LPAR);
    // parse the type
    parseType();
    // expect the right parenthesis [')']
    expect(Category.RPAR);
    // parse the expression
    parseExp();
  }

  /*
   * Helper methods for Stmt
   */

  // parser [While] statement - "while" "(" exp ")" stmt
  private void parseWhileStmt() {
    // Consume 'while'
    nextToken();
    // Expect '('
    expect(Category.LPAR);
    // Parse condition
    parseExp();
    // Expect ')'
    expect(Category.RPAR);
    // Parse body
    parseStmt();
  }
  // parser [If] statement - "if" "(" exp ")" stmt ["else" stmt]
  private void parseIfStmt() {
    // Consume 'if'
    nextToken();
    // Expect '('
    expect(Category.LPAR);
    // Parse condition
    parseExp();
    // Expect ')'
    expect(Category.RPAR);
    // Parse 'if' body
    parseStmt();
    // Parse 'else' body
    // optional else body
    if (accept(Category.ELSE)) {
      // Consume 'else'
      nextToken();
      // Parse 'else' body
      parseStmt();
    }
  }

  // parser [return] statement - "return" [exp] ";"
  private void parseReturnStmt() {
    // Consume 'return'
    nextToken();
    // if not [';'] then we need to parse the expression
    // optional expression after 'return'
    if (!accept(Category.SC)) {
      parseExp();
    }
    // Expect ';'
    expect(Category.SC);
  }

  // parser for for continue and break statement
  private void parseContinueAndBreakStmt() {
    // Consume 'continue' or 'break'
    nextToken();
    // Expect ';'
    expect(Category.SC);
  }
}
