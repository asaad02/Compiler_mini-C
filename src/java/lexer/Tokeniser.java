package lexer;

import util.CompilerPass;

/** @author cdubach */
public class Tokeniser extends CompilerPass {

  private final Scanner scanner;

  public Tokeniser(Scanner scanner) {
    this.scanner = scanner;
  }

  private void error(char c, int line, int col) {
    String msg = "Lexing error: unrecognised character (" + c + ") at " + line + ":" + col;
    System.out.println(msg);
    incError();
  }

  /*
   * To be completed
   */
  public Token nextToken() {

    int line = scanner.getLine();
    int column = scanner.getColumn();

    if (!scanner.hasNext())
      return new Token(Token.Category.EOF, scanner.getLine(), scanner.getColumn());

    // get the next character
    char c = scanner.next();

    // skip white spaces between lexems
    if (Character.isWhitespace(c)) return nextToken();

    // recognises the plus operator
    if (c == '+') return new Token(Token.Category.PLUS, line, column);

    // ... to be completed

    // Handle the comments such as [//] [/*] [*/] [//- DIV]
    if (c == '/') {
      // will peak at the next character if it's '/' then it's a single line comment
      if (scanner.peek() == '/') {
        // will skip the rest of the line because it's a comment
        while (scanner.hasNext() && scanner.next() != '\n') {}
        return nextToken();
        // will peak at the next character if it's '*' then it's a multi line comment
      } else if (scanner.peek() == '*') {
        // will consume the next character which is '*'
        scanner.next();
        // will skip the rest of the comment until it finds '*/'
        while (scanner.hasNext()) {
          char nextChar = scanner.next();
          if (nextChar == '*' && scanner.peek() == '/') {
            // will consume the next character which is '/' as the end of multi line comments
            scanner.next();
            break;
          }
        }
        return nextToken();
      } else {
        // if it's not single or multi line comment then it's a division operator
        return new Token(Token.Category.DIV, line, column);
      }
    }
    // Handle the [= 'EQ'] and [== 'ASSIGN'] operators
    if (c == '=') {
      if (scanner.peek() == '=') {
        scanner.next();
        return new Token(Token.Category.EQ, line, column);
      } else {
        return new Token(Token.Category.ASSIGN, line, column);
      }
    }

    /*
     *   [delimiters]
     *   LBRA,  // '{' // left brace
     *   RBRA,  // '}' // right brace
     *   LPAR,  // '(' // left parenthesis
     *   RPAR,  // ')' // right parenthesis
     *   LSBR,  // '[' // left square brace
     *   RSBR,  // ']' // left square brace
     *   SC,    // ';' // semicolon
     *   COMMA, // ','
     */
    switch (c) {
      case '{':
        return new Token(Token.Category.LBRA, line, column);
      case '}':
        return new Token(Token.Category.RBRA, line, column);
      case '(':
        return new Token(Token.Category.LPAR, line, column);
      case ')':
        return new Token(Token.Category.RPAR, line, column);
      case '[':
        return new Token(Token.Category.LSBR, line, column);
      case ']':
        return new Token(Token.Category.RSBR, line, column);
      case ';':
        return new Token(Token.Category.SC, line, column);
      case ',':
        return new Token(Token.Category.COMMA, line, column);
    }

    // if we reach this point, it means we did not recognise a valid token
    error(c, line, column);
    return new Token(Token.Category.INVALID, line, column);
  }
}
