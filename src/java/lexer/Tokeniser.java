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
     *   LBRA,  '{' // left brace
     *   RBRA,  '}' // right brace
     *   LPAR,  '(' // left parenthesis
     *   RPAR,  ')' // right parenthesis
     *   LSBR,  '[' // left square brace
     *   RSBR,  ']' // left square brace
     *   SC,    ';' // semicolon
     *   COMMA, ','
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

    /*
     *   [types]
     *   INT,  'int'
     *   VOID, 'void'
     *   CHAR, 'char'
     *
     *   [keywords]
     *   IF,      'if'
     *   ELSE,    'else'
     *   WHILE,   'while'
     *   RETURN,  'return'
     *   STRUCT,  'struct'
     *   SIZEOF,  'sizeof'
     *   CONTINUE,  'continue'
     *   BREAK, 'break'
     */
    if (Character.isLetter(c) || c == '_') {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      while (scanner.hasNext()
          && (Character.isLetterOrDigit(scanner.peek()) || scanner.peek() == '_')) {
        sb.append(scanner.next());
      }
      String lexeme = sb.toString();
      switch (lexeme) {
        case "int":
          return new Token(Token.Category.INT, line, column);
        case "void":
          return new Token(Token.Category.VOID, line, column);
        case "char":
          return new Token(Token.Category.CHAR, line, column);
        case "if":
          return new Token(Token.Category.IF, line, column);
        case "else":
          return new Token(Token.Category.ELSE, line, column);
        case "while":
          return new Token(Token.Category.WHILE, line, column);
        case "return":
          return new Token(Token.Category.RETURN, line, column);
        case "struct":
          return new Token(Token.Category.STRUCT, line, column);
        case "sizeof":
          return new Token(Token.Category.SIZEOF, line, column);
        case "continue":
          return new Token(Token.Category.CONTINUE, line, column);
        case "break":
          return new Token(Token.Category.BREAK, line, column);
        default:
          return new Token(Token.Category.IDENTIFIER, lexeme, line, column);
      }
    }

    // if we reach this point, it means we did not recognise a valid token
    error(c, line, column);
    return new Token(Token.Category.INVALID, line, column);
  }
}
