package parser;

import java.util.LinkedList;
import java.util.Queue;
import lexer.Token;
import lexer.Token.Category;
import lexer.Tokeniser;
import util.CompilerPass;

/** @author cdubach */
public class Parser extends CompilerPass {

  private Token token;

  private final Queue<Token> buffer = new LinkedList<>();

  private final Tokeniser tokeniser;

  public Parser(Tokeniser tokeniser) {
    this.tokeniser = tokeniser;
  }

  public void parse() {
    // get the first token
    nextToken();
    parseProgram();
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

  private void parseProgram() {

    // parsing the include
    /*
     * program    ::= (include)* (structdecl | vardecl | fundecl | fundef)* EOF
     */

    /*
     * Parsing includes for the program
     * program    ::= (include)*
     * include    ::= "#include" STRING_LITERAL
     */
    // System.out.println("\u001B[32m" + "Parsing includes ... inside the program\n" + "\u001B[0m");
    parseIncludes();

    /*
     * program    ::= (structdecl | vardecl | fundecl | fundef)* EOF
     */
    while (accept(Category.STRUCT, Category.INT, Category.CHAR, Category.VOID)) {
      if (token.category == Category.STRUCT
          && lookAhead(1).category == Category.IDENTIFIER
          && lookAhead(2).category == Category.LBRA) {
        // System.out.println("\u001B[32m" + "Parsing struct declaration ... inside the program\n" +
        // "\u001B[0m");
        // parsing the type of the struct
        parseType();
        // structdecl ::= structtype "{" (vardecl)+ "}" ";"    # structure declaration
        parseStructDecl();
      } else {

        // parseVarDeclOrFunc();
        // type  ::= ("int" | "char" | "void" | structtype) ("*")*
        parseType();
        // check the type IDENT and return error if not found
        if (!accept(Category.IDENTIFIER)) {
          error(Category.IDENTIFIER);
          return;
        }
        // consume the Identifier
        expect(Category.IDENTIFIER);
        // check the left parenthesis
        // check the parameters ['(']
        if (accept(Category.LPAR)) {
          // fundecl   ::= type IDENT "(" params ")" ";"
          // fundef    ::= type IDENT "(" params ")" block
          parseFuncDefAndDec();
        }
        // if it's not LPAR, then it's a variable declaration
        else {
          // System.out.println("\u001B[32mParsing variable declaration ...\u001B[0m");
          parseVarDecl(); // Parse variable declaration
        }
      }
    }
    expect(Category.EOF);
  }

  /*
   *[include]
   *include    ::= "#include" STRING_LITERAL
   *
   */
  private void parseIncludes() {
    if (accept(Category.INCLUDE)) {
      nextToken();
      expect(Category.STRING_LITERAL);
      parseIncludes();
    }
  }

  /*
   * type ::= ("int" | "char" | "void" | structtype) ("*")*
   */
  private void parseType() {
    // Check the type ("int" | "char" | "void" | structtype ) ("*")*
    if (accept(Category.INT, Category.CHAR, Category.VOID)) {
      // Consume the type token
      nextToken();
    } else if (accept(Category.STRUCT)) {
      // Parse a struct type
      parseStructType();
    } else {
      error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
    }

    // valueat      ::= "*" exp  - Value at operator (pointer indirection)
    while (accept(Category.ASTERISK)) {
      // Consume ['*']
      nextToken();
    }
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
    // if struct is nasted within the struct
    // if (accept(Category.STRUCT)) {
    // Parsing the struct declaration within the struct
    // parseStructDecl();
    // variable declaration, (e.g. int a;), or multi-dimensional array declaration, (e.g. int
    // a[2][5];)
    parseVarDecls();
    // if the token is a right brace ["}"]
    expect(Category.RBRA);
    // if the token is a semicolon [";"]
    expect(Category.SC);
  }
  // structtype ::= "struct" IDENT
  private void parseStructType() {
    // expect the token to be a struct
    expect(Category.STRUCT);
    // expect the token to be an identifier
    expect(Category.IDENTIFIER);
  }

  /*
   * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
   * variable declaration, (e.g. int a;), or multi-dimensional array declaration, (e.g. int a[2][5];)
   */
  private void parseVarDecls() {
    // loop through the variable declaration
    while (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
      // we parse the type and consume it
      parseType();
      // loop through the identifiers
      boolean moreVariables = true;
      while (moreVariables) {
        // each variable declaration should have an identifier
        if (!accept(Category.IDENTIFIER)) {
          error(Category.IDENTIFIER);
          break;
        }
        // consume the identifier
        expect(Category.IDENTIFIER);
        // check if it's an arrays
        while (accept(Category.LSBR)) {
          // consume the left square brace ["["]
          nextToken();
          // check if it's an integer literal , and we will throw an error if it's not
          if (!accept(Category.INT_LITERAL)) {
            error(Category.INT_LITERAL);
            break;
          }
          // consume the integer literal
          expect(Category.INT_LITERAL);
          // check if it's a right square brace ["]"]
          expect(Category.RSBR);
        }

        // Check if there's a comma for more variables in the same declaration
        if (accept(Category.COMMA)) {
          // Consume the comma
          nextToken();
        } else {
          // No more variables in this declaration we break the loop
          moreVariables = false;
        }
      }

      // Expect semicolon to end the declaration and throw an error if it's not
      if (!accept(Category.SC)) {
        error(Category.SC);
      }
      // Consume semicolon
      expect(Category.SC);
    }
  }

  /*
   * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
   * # variable declaration, (e.g. int a;), or multi-dimensional array declaration, (e.g. int a[2][5];)
   */
  private void parseVarDecl() {
    // parseType();
    // expect(Category.IDENTIFIER);
    // System.out.println("Parsed variable: \n");
    // Handle array dimensions such as int a[2][5];
    // if the token is a left square brace ["["]
    while (accept(Category.LSBR)) {
      // consume the left square brace ["["]
      nextToken();
      // consume the integer literal
      expect(Category.INT_LITERAL);
      // expect the right square brace ["]"]
      expect(Category.RSBR);
    }
    // expect the semicolon [";"]
    expect(Category.SC);
  }

  /*
   * # function definition
   *  fundef    ::= type IDENT "(" params ")" block       # function definition
   */
  private void parseFuncDefAndDec() {
    // params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL"]")*)*]
    parseParams();
    // consume the right parenthesis ['{']
    if (accept(Category.LBRA)) {
      // System.out.println("\u001B[32mParsing function definition ...\u001B[0m");
      // block      ::= "{" (vardecl)* (stmt)* "}"
      parseBlock(); // Parse function body
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
    if (!accept(Category.RPAR)) {
      do {
        // parse the type
        parseType();
        // expect the identifier
        expect(Category.IDENTIFIER);
        // check if it's an array
        while (accept(Category.LSBR)) {
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
    // System.out.println("Entering block...");
    // Parse variable declarations within the block
    parseVarDecls();
    // Parse statements within the block , the block will have the [while, if, return, continue,
    // break, exp]
    parseStmts();
    // Ensure the block ends with a right brace '}'
    if (!accept(Category.RBRA)) {
      error(Category.RBRA);
    }
    // consume the right brace ['}']
    expect(Category.RBRA);
    // System.out.println("Exiting block...");
  }
  // parse all the statements within the block
  private void parseStmts() {
    // loop through the statements until we reach the right brace ['}']
    while (!accept(Category.RBRA)) {
      // save the current token
      Token currentToken = token;
      // if its a integer or char or void or struct then we will parse the type and the identifier
      // other than that we will parse the statement
      if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
        // Parse variable declarations within the block
        parseVarDecls();
      } else {
        // parse the statement
        parseStmt();
      }
      // parse each statement
      // parseStmt();
      // if the current token is the same as the token then we will throw an error
      if (currentToken == token) {
        error(Category.RBRA);
        return;
      }
    }
    // Parse variable declarations within the block
    // parseVarDecls();
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
      expect(Category.SC);
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

  // parser for the logical OR expression
  private void parseLogicalOrExp() {
    // Parse right-hand side of the logical OR expression
    parseLogicalAndExp();
    // exp "||" exp
    // loop through the logical OR expression
    while (accept(Category.LOGOR)) {
      // Consume '||'
      nextToken();
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

  // Parses an equality expression. [Grammar: ]exp ::= exp ("==" | "!=") exp]
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
      nextToken(); // Consume '*'
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
   * STRING_LITERAL]
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
        if (!accept(Category.RPAR)) {
          // if the token is not a right parenthesis [")"] then we will throw an error
          error(Category.RPAR);
        }
        // consume the right parenthesis [")"]
        expect(Category.RPAR);
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
    else if (accept(Category.ASTERISK)) {
      // parse value at operator
      // valueat      ::= "*" exp
      parseValueAt();
      // check if the token is an address of ["&"] - Address of operator
      // addressof ::= "&" exp
      parseAddressOf();
    }
    // check if the token is a sizeof ["sizeof"] - Sizeof operator
    else if (accept(Category.SIZEOF)) {
      // parse sizeof operator
      // sizeof ::= "sizeof" "(" type ")"
      parseSizeOf();
    } else if (accept(Category.AND)) {
      // parse address of operator
      // addressof ::= "&" exp
      parseAddressOf();
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
    if (!accept(Category.RPAR)) {
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
    // Parse loop body
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
