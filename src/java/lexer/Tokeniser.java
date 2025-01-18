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

    /*
     *   [include]
     *   INCLUDE,  '#include'
     */

    if (c == '#') {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      while (scanner.hasNext() && Character.isLetter(scanner.peek())) {
        sb.append(scanner.next());
      }
      String lexeme = sb.toString();
      if (lexeme.equals("#include")) {
        return new Token(Token.Category.INCLUDE, line, column);
      }
    }

    /*
     *   // SpecialCharWithoutSingleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? "
     *   // SpecialCharWithoutDoubleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? '
     *   // WhiteSpace                    = ' '
     *   // EscapedChar                   = '\a' | '\b' | '\n' | '\r' | '\t' | '\\' | '\'' | '\"' | '\0'
     *   // literals
     */

    /*
     *   CHAR_LITERAL,  ''' (LowerCaseAlpha | UpperCaseAlpha | Digit |  SpecialCharWithoutSingleQuote  | WhiteSpace | EscapedChar) '''  any character (except single quote) enclosed within  a pair of single quotes
     */

    if (c == '\'') {
      StringBuilder sb = new StringBuilder();
      c = scanner.next();
      while (c != '\'') {
        if (c == '\\') {
          char escapedChar = scanner.next();
          switch (escapedChar) {
            case 'a':
            case 'b':
            case 'n':
            case 'r':
            case 't':
            case '\\':
            case '\'':
            case '\"':
            case '0':
              sb.append(escapedChar);
              break;
            default:
              error(escapedChar, line, column);
              return new Token(Token.Category.INVALID, line, column);
          }
        } else {
          sb.append(c);
        }
        c = scanner.next();
      }
      return new Token(Token.Category.CHAR_LITERAL, sb.toString(), line, column);
    }

    /*
     *   STRING_LITERAL, '"' (LowerCaseAlpha | UpperCaseAlpha | Digit |  SpecialCharWithoutDoubleQuote  | WhiteSpace | EscapedChar)* '"'  any sequence of characters (except double quote) enclosed within two double quotes
     */

    if (c == '"') {
      StringBuilder sb = new StringBuilder();
      c = scanner.next();
      while (c != '"') {
        if (c == '\\') {
          char escapedChar = scanner.next();
          switch (escapedChar) {
            case 'a':
            case 'b':
            case 'n':
            case 'r':
            case 't':
            case '\\':
            case '\'':
            case '\"':
            case '0':
              sb.append(escapedChar);
              break;
            default:
              error(escapedChar, line, column);
              return new Token(Token.Category.INVALID, line, column);
          }
        } else {
          sb.append(c);
        }
        c = scanner.next();
      }
      return new Token(Token.Category.STRING_LITERAL, sb.toString(), line, column);
    }

    /*
     * INT_LITERAL Digit+
     */

    if (Character.isDigit(c)) {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      while (scanner.hasNext() && Character.isDigit(scanner.peek())) {
        sb.append(scanner.next());
      }
      return new Token(Token.Category.INT_LITERAL, sb.toString(), line, column);
    }

    /*
     * logical operators
     *   LOGAND, '&&'
     *   LOGOR,  '||'
     */

    if (c == '&') {
      if (scanner.peek() == '&') {
        scanner.next();
        return new Token(Token.Category.LOGAND, line, column);
      }
    }

    if (c == '|') {
      if (scanner.peek() == '|') {
        scanner.next();
        return new Token(Token.Category.LOGOR, line, column);
      }
    }

    /*
     * comparisons
     *   EQ, '==' or ASSIGN, '='
     *   NE, '!=' or NOT, '!'
     *   LT, '<' or LE, '<='
     *   GT, '>' or GE, '>='
     */
    // Handle the [= 'EQ'] and [== 'ASSIGN'] operators
    if (c == '=') {
      if (scanner.peek() == '=') {
        scanner.next();
        return new Token(Token.Category.EQ, line, column);
      } else {
        return new Token(Token.Category.ASSIGN, line, column);
      }
    }
    // Handle the [! 'NE'] and [!= 'NOT'] operators
    if (c == '!') {
      if (scanner.peek() == '=') {
        scanner.next();
        return new Token(Token.Category.NE, line, column);
      } else {
        return new Token(Token.Category.INVALID, line, column);
      }
    }
    // Handle the [< 'LT'] and [<= 'LE'] operators
    if (c == '<') {
      if (scanner.peek() == '=') {
        scanner.next();
        return new Token(Token.Category.LE, line, column);
      } else {
        return new Token(Token.Category.LT, line, column);
      }
    }

    // Handle the [> 'GT'] and [>= 'GE'] operators
    if (c == '>') {
      if (scanner.peek() == '=') {
        scanner.next();
        return new Token(Token.Category.GE, line, column);
      } else {
        return new Token(Token.Category.GT, line, column);
      }
    }

    // if we reach this point, it means we did not recognise a valid token
    error(c, line, column);
    return new Token(Token.Category.INVALID, line, column);
  }
}
