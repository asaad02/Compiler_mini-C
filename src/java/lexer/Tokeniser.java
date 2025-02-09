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
   * isLetter(char c) true if c is a letter  upper or lower case
   * The ASCII code of a letter is between 65 and 90 for upper case letters and  97 to 122 for lower case letters.
   */
  private boolean isLetter(char c) {
    int ascii = (int) c;
    return (ascii >= 65 && ascii <= 90) || (ascii >= 97 && ascii <= 122);
  }
  /*
   * isDigit(char c)  true if c is a digit
   * The ASCII code of a digit is between 48 and 57.
   */
  private boolean isDigit(char c) {
    int ascii = (int) c;
    return (ascii >= 48 && ascii <= 57);
  }
  /*
   * isLetterOrDigit char c  true if c is a letter or a digit
   */
  private boolean isLetterOrDigit(char c) {
    return isLetter(c) || isDigit(c);
  }
  /*
   * isSpecialCharWithoutSingleQuote char c
   * SpecialCharWithoutSingleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? "
   */
  private boolean isSpecialCharWithoutSingleQuote(char c) {
    return "`~@!$#^*%&()[]{}<>+=_-|/;:,.?\"".indexOf(c) >= 0;
  }

  /*
   * isSpecialCharWithoutDoubleQuote char c
   * SpecialCharWithoutDoubleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? '
   */
  private boolean isSpecialCharWithoutDoubleQuote(char c) {
    return "`~@!$#^*%&()[]{}<>+=_-|/;:,.?\'".indexOf(c) >= 0;
  }
  /*
   * isEscapedChar char c returns true if c is an escaped character and false otherwise
   * EscapedChar    = '\a' | '\b' | '\n' | '\r' | '\t' | '\\' | '\'' | '\"' | '\0'
   */
  private boolean isEscapedChar(char c) {
    return c == 'a' || c == 'b' || c == 'n' || c == 'r' || c == 't' || c == '\\' || c == '\''
        || c == '"' || c == '0';
  }
  /*
   * isValidIdentifierPart char c  true if c is a valid identifier part and false
   *  lower case letters, upper case letters, digits and underscore
   */
  private boolean isValidIdentifierPart(char c) {
    return isLetterOrDigit(c) || c == '_';
  }
  /*
   * isValidIdentifierStart char c
   * not start with a digit and can only contain lower case letters upper case letters and underscore
   */
  private boolean isValidIdentifierStart(char c) {
    return isLetter(c) || c == '_';
  }

  /*
   * To be completed
   */
  public Token nextToken() {

    int line = scanner.getLine();
    int column = scanner.getColumn();

    /*
     * Debugging
     */
    // System.out.println("Current position: line " + line + ", column " + column);

    // [EOF], signal end of file
    if (!scanner.hasNext()) {
      // debug message
      // System.out.println("EOF detected at line " + line + ", column " + column);
      return new Token(Token.Category.EOF, scanner.getLine(), scanner.getColumn());
    }
    // get the next character
    char c = scanner.next();

    // skip white spaces between lexems
    if (Character.isWhitespace(c)) return nextToken();
    // debug message
    // System.out.println("Processing character: " + c);

    // recognises the plus operator
    // if (c == '+') return new Token(Token.Category.PLUS, line, column);

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
          // not closed comments
          if (!scanner.hasNext()) {
            error(nextChar, line, column);
            return new Token(Token.Category.INVALID, line, column);
          }
        }
        return nextToken();
      } else if (scanner.peek() == '0') {
        // return the end of the file token
        return new Token(Token.Category.EOF, line, column);
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

    if (isValidIdentifierStart(c)) {
      StringBuilder sb = new StringBuilder();
      sb.append(c);
      while (scanner.hasNext() && isValidIdentifierPart(scanner.peek())) {
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
      while (scanner.hasNext() && isLetter(scanner.peek())) {
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
     *   CHAR_LITERAL,  ''' (LowerCaseAlpha | UpperCaseAlpha | Digit |  SpecialCharWithoutSingleQuote  | WhiteSpace | EscapedChar) '''
     *   any character (except single quote) enclosed within  a pair of single quotes
     */
    // if the character is a single quote then it's a char literal
    if (c == '\'') {
      try {
        char charValue = scanner.next();
        // if the character is a backslash then it's an escaped character
        if (charValue == '\\') {
          char escapedChar = scanner.next();
          // if the escaped character is a valid escaped character then it's a char literal
          if (isEscapedChar(escapedChar) && scanner.next() == '\'') {
            // return the char literal token
            // if it's scape character then it will be returned as it is
            // EscapedChar    = '\a' | '\b' | '\n' | '\r' | '\t' | '\\' | '\'' | '\"' | '\0'
            String escapeString;
            switch (escapedChar) {
              case 'a':
                escapeString = "\\a";
                break;
              case 'b':
                escapeString = "\\b";
                break;
              case 'n':
                escapeString = "\\n";
                break;
              case 'r':
                escapeString = "\\r";
                break;
              case 't':
                escapeString = "\\t";
                break;
              case '\\':
                escapeString = "\\";
                break;
              case '\'':
                escapeString = "\'";
                break;
              case '"':
                escapeString = "\"";
                break;
              case '0':
                escapeString = "\\0";
                break;
              default:
                // If the escape sequence is invalid
                error(escapedChar, line, column);
                return new Token(Token.Category.INVALID, line, column);
            }
            return new Token(Token.Category.CHAR_LITERAL, escapeString, line, column);
          } else {
            error(escapedChar, line, column);
            return new Token(Token.Category.INVALID, line, column);
          }
        }
        // if the character is a letter and digit and special character without single quote or
        // white space then it's a char literal
        else if (isLetter(charValue)
            || isDigit(charValue)
            || isSpecialCharWithoutSingleQuote(charValue)
            || charValue == ' ') {
          if (scanner.next() == '\'') {
            return new Token(Token.Category.CHAR_LITERAL, String.valueOf(charValue), line, column);
          }
        }
        error(charValue, line, column);
        return new Token(Token.Category.INVALID, line, column);
      } catch (Error e) {
        error(c, line, column);
        return new Token(Token.Category.INVALID, line, column);
      }
    }

    /*
     *   STRING_LITERAL, '"' (LowerCaseAlpha | UpperCaseAlpha | Digit |  SpecialCharWithoutDoubleQuote  | WhiteSpace | EscapedChar)* '"'
     *   any sequence of characters (except double quote) enclosed within two double quotes
     *   // SpecialCharWithoutSingleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? "
     *   // SpecialCharWithoutDoubleQuote = One of the following 30 characters: ` ~ @ ! $ # ^ * % & ( ) [ ] { } < > + = _ - | / ; : , . ? '
     *   // WhiteSpace                    = ' '
     *   // EscapedChar                   = '\a' | '\b' | '\n' | '\r' | '\t' | '\\' | '\'' | '\"' | '\0'
     *   // literals
     */
    if (c == '"') {
      StringBuilder sb = new StringBuilder();
      try {
        if (!scanner.hasNext()) {
          error('"', line, column);
          return new Token(Token.Category.INVALID, line, column);
        }
        while (scanner.hasNext()) {
          char charValue = scanner.next();
          if (charValue == '"') {
            return new Token(Token.Category.STRING_LITERAL, sb.toString(), line, column);
          }
          // if the character is a backslash then it's an escaped character
          // EscapedChar    = '\a' | '\b' | '\n' | '\r' | '\t' | '\\' | '\'' | '\"' | '\0'
          if (charValue == '\\') {
            char escapedChar = scanner.next();
            if (isEscapedChar(escapedChar)) {
              switch (escapedChar) {
                case 'a':
                  sb.append("\\a");
                  break;
                case 'b':
                  sb.append("\\b");
                  break;
                case 'n':
                  sb.append("\\n");
                  break;
                case 'r':
                  sb.append("\\r");
                  break;
                case 't':
                  sb.append("\\t");
                  break;
                case '\\':
                  sb.append("\\");
                  break;
                case '\'':
                  sb.append("\'");
                  break;
                case '"':
                  sb.append("\"");
                  break;
                case '0':
                  sb.append("\\0");
                  break;
              }
            } else {
              error(escapedChar, line, column);
              return new Token(Token.Category.INVALID, line, column);
            }
          } else if (isLetter(charValue)
              || isDigit(charValue)
              || isSpecialCharWithoutDoubleQuote(charValue)
              || charValue == ' ') {
            sb.append(charValue);
          } else {
            error(charValue, line, column);
            return new Token(Token.Category.INVALID, line, column);
          }
        }
      } catch (Error e) {
        error('"', line, column);
        return new Token(Token.Category.INVALID, line, column);
      }
    }

    /*
     * INT_LITERAL Digit+
     */

    if (isDigit(c)) {
      StringBuilder sb = new StringBuilder();
      sb.append(c);

      while (scanner.hasNext() && isDigit(scanner.peek())) {
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
    // Handle the [== 'EQ'] and [= 'ASSIGN'] operators
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
        error(c, line, column);
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

    /*
     * operators
     *   PLUS, '+'
     *   MINUS, '-'
     *   ASTERISK, '*'  // can be used for multiplication or pointers
     *   DIV, '/', REM, '%'
     *   AND, '&'
     */

    switch (c) {
      case '+':
        return new Token(Token.Category.PLUS, line, column);
      case '-':
        return new Token(Token.Category.MINUS, line, column);
      case '*':
        return new Token(Token.Category.ASTERISK, line, column);
      case '%':
        return new Token(Token.Category.REM, line, column);
      case '&':
        return new Token(Token.Category.AND, line, column);
    }

    /*
     * struct member access
     *   DOT, '.'
     */
    if (c == '.') return new Token(Token.Category.DOT, line, column);

    // if we reach this point, it means we did not recognise a valid token
    error(c, line, column);
    return new Token(Token.Category.INVALID, line, column);
  }
}
