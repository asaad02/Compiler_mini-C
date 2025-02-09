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
    else {
      token = tokeniser.nextToken();
      System.out.println(token);
    }
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
    else if (accept(Category.INT, Category.CHAR, Category.VOID) ) {
      // type  ::= ("int" | "char" | "void" | structtype) ("*")*
      //Type type = parseType();
      //Token id = expect(Category.IDENTIFIER);
      //if (accept(Category.LPAR)) {
        if (lookAhead(1).category == Category.IDENTIFIER
        && lookAhead(2).category == Category.LPAR ) {
            Type type = parseType();
            Token id = expect(Category.IDENTIFIER);
          // if the next token is an identifier and the one after that is a left parenthesis ['(']
          // then it's a function declaration or definition
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
        // parseFuncDefAndDec(type, id);
        // return empty AST node for now
        return parseFuncDefOrDecl(type, id);
      }
      // if it's not LPAR ['('], then it's a variable declaration
      else {
        /*
         * ===================== VARIABLE DECLARATION PARSING =====================
         * vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
         * Variable declaration
         * VarDecl    ::= Type String
         */
        return parseVarDecl();
      }
    }
    //expect(Category.SC);

    //error(Category.STRUCT, Category.INT, Category.CHAR, Category.VOID);
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
        baseType = new PointerType(baseType); 
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
      // expect(Category.RPAR);
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
      varDecls.add(parseVarDecl());
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
  private VarDecl parseVarDecl() {
    Type type = parseType();
    String varName = expect(Category.IDENTIFIER).data;
    // Handle array declarations (e.g., int a[10];)
    while (accept(Category.LSBR)) {
      nextToken();
      int size = Integer.parseInt(expect(Category.INT_LITERAL).data);
      expect(Category.RSBR);
      type = new ArrayType(type, size);
    }
    expect(Category.SC);
    return new VarDecl(type, varName);
  }
  /*
   * ===================== FUNCTION PARSING =====================
   * function declaration | function definition
   * fundecl   ::= type IDENT "(" params ")" ";"
   * fundef    ::= type IDENT "(" params ")" block
   * AST:
   * FunDecl definition (the String is the name of the FunDecl)
   * funDef     ::= Type String VarDecl* Block
   * Function prototype (the String is the name of the function)
   * FunDecl    ::= Type String VarDecl*
   */
  private Decl parseFuncDefOrDecl(Type type, Token id) {
    List<VarDecl> params = new ArrayList<>();
    // expect the left parenthesis ["("]
    expect(Category.LPAR);
    // params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL"]")*)*]
    // parseParams();
    // Parse parameters if they exist
    /*
     * params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL "]")*)* ]
     */
    // if the token is not a right parenthesis [')']
    if (!accept(Category.RPAR) && !accept(Category.EOF)) {
      do {
        // parse the type
        Type paramType = parseType();
        // expect the identifier
        String paramName = expect(Category.IDENTIFIER).data;
        // check if it's an array '[' for the parameter
        while (accept(Category.LSBR) && !accept(Category.EOF)) {
          nextToken();
          int size = Integer.parseInt(expect(Category.INT_LITERAL).data);
          // expect the right square brace ["]"]
          expect(Category.RSBR);
          paramType = new ArrayType(paramType, size);
        }
        params.add(new VarDecl(paramType, paramName));
        // check if there's a comma for more parameters
        if (accept(Category.COMMA)) {
          System.out.println("i'm here COMMA");
          nextToken();
        } else {
          // if no comma so we don't have more parameters so we break the loop
          break;
        }
      } while (true);
    }
    expect(Category.RPAR);
    // FunDecl(type, id.data, params);
    if (accept(Category.LBRA)) {
        // funDef     ::= Type String VarDecl* Block
      return new FunDef(type, id.data, params, parseBlock());
    } 
    else if (accept(Category.SC)) {
         // consume the semicolon [';']
      expect(Category.SC);
      // FunDecl    ::= Type String VarDecl*
      return new FunDecl(type, id.data, params);
    } 
    
    // Handle unexpected cases explicitly
    error(Category.LBRA, Category.SC);
    throw new RuntimeException("Unexpected token after function declaration: " + token);
  }
  /*
   * ===================== BLOCK PARSING =====================
   * block      ::= "{" (vardecl)* (stmt)* "}"
   * AST:
   * Block ::= VarDecl* Stmt*
   */
  private Block parseBlock() {
    expect(Category.LBRA);
    List<Stmt> stmts = new ArrayList<>();
    List<VarDecl> varDecls = new ArrayList<>();
    // Parse statements within the block , the block will have the [while, if, return, continue,
    // break, exp]
    while (!accept(Category.RBRA) && !accept(Category.EOF)) {

      if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
        // parse all the variable declarations within the block
        varDecls.add(parseVarDecl());
      } else {
        // parse all the statements within the block
        stmts.add(parseStmt());
      }
    }
    // Ensure the block ends with a right brace '}'
    // consume the right brace ['}']
    expect(Category.RBRA);
    return new Block(varDecls, stmts);
  }
  /**
   * stmt ::= block | "while" "(" exp ")" stmt # while loop | "if" "(" exp ")" stmt ["else" stmt] #
   * if then else | "return" [exp] ";" # return | exp ";" # expression statement, e.g. a function
   * call | "continue" ";" # continue | "break" ";" Stmt ::= Block | While | If | Return | Continue
   * | Break | ExprStmt
   */
  private Stmt parseStmt() {
    return switch (token.category) {
        case RETURN -> parseReturnStmt();
        case WHILE -> parseWhileStmt();
        case IF -> parseIfStmt();
        case CONTINUE -> { nextToken(); expect(Category.SC); yield new Continue(); }
        case BREAK -> { nextToken(); expect(Category.SC); yield new Break(); }
        case LBRA -> parseBlock();
        case ELSE -> throw new RuntimeException("Unexpected 'else' without matching 'if'"); 
        default -> parseExprStmt(); 
    };
}
  /*
   * Helper methods for Stmt
   */

  // parser [While] statement - "while" "(" exp ")" stmt
  private Stmt parseWhileStmt() {
    // Consume 'while'
    expect(Category.WHILE);
    // Expect '('
    expect(Category.LPAR);
    // Parse condition
    Expr condition = parseExpr();
    // Expect ')'
    expect(Category.RPAR);
    // Parse body
    return new While(condition, parseStmt());
  }
  // parser [If] statement - "if" "(" exp ")" stmt ["else" stmt]
  private Stmt parseIfStmt() {
    // Consume 'if'
    expect(Category.IF);
    // Expect '('
    expect(Category.LPAR);
    // Parse condition
    Expr condition = parseExpr();
    // Expect ')'
    expect(Category.RPAR);
    // Parse 'if' body
    Stmt thenBranch = parseStmt();
    // Parse 'else' body
    // optional else body
    Stmt elseBranch = null;
    if (accept(Category.ELSE)) {
      // Consume 'else'
      nextToken();
      // Parse 'else' body
      elseBranch = parseStmt();
    }
    return new If(condition, thenBranch, elseBranch);
  }

  // parser [return] statement - "return" [exp] ";"
  private Stmt parseReturnStmt() {
    // Consume 'return'
    expect(Category.RETURN);
    // if not [';'] then we need to parse the expression
    // optional expression after 'return'
    Expr expr = null;
    if (!accept(Category.SC)) {
      expr = parseExpr();
    }
    // Expect ';'
    expect(Category.SC);
    return new Return(expr);
  }

  // parser for for continue and break statement
  //private Stmt parseContinueStmt() {
    // Consume 'continue' or 'break'
    //nextToken();
    // Expect ';'
    //expect(Category.SC);
    //return new Continue();
  //}

  //private Stmt parseBreakStmt() {
    // Consume 'continue' or 'break'
    //nextToken();
    // Expect ';'
    //expect(Category.SC);
    //return new Break();
  //}
  private Stmt parseExprStmt() {
    Expr expr = parseExpr();
    expect(Category.SC);
    return new ExprStmt(expr);
    }



    private Expr parseExpr() {
        Expr lhs = parseLogicalOrExpr(); 
        if (accept(Category.ASSIGN)) { 
            nextToken();
            return new Assign(lhs, parseExpr()); 
        }
        return lhs;
    }


    private Expr parseLogicalOrExpr() {
        Expr expr = parseLogicalAndExpr();
        while (accept(Category.LOGOR)) {
            nextToken();
            expr = new BinOp(expr, Op.OR, parseLogicalAndExpr());
        }
        return expr;
    }


    private Expr parseLogicalAndExpr() {
        Expr expr = parseEqualityExpr();
        while (accept(Category.LOGAND)) {
            nextToken();
            expr = new BinOp(expr, Op.AND, parseEqualityExpr());
        }
        return expr;
    }


    private Expr parseEqualityExpr() {
        Expr expr = parseRelationalExpr();
        while (accept(Category.EQ, Category.NE)) {
            Op op = (token.category == Category.EQ) ? Op.EQ : Op.NE;
            nextToken();
            expr = new BinOp(expr, op, parseRelationalExpr());
        }
        return expr;
    }


    private Expr parseRelationalExpr() {
        Expr expr = parseAdditiveExpr();
        while (accept(Category.LT, Category.GT, Category.LE, Category.GE)) {
            Op op = switch (token.category) {
                case LT -> Op.LT;
                case GT -> Op.GT;
                case LE -> Op.LE;
                case GE -> Op.GE;
                default -> throw new IllegalArgumentException("Invalid relational operator");
            };
            nextToken();
            expr = new BinOp(expr, op, parseAdditiveExpr());
        }
        return expr;
    }


    private Expr parseAdditiveExpr() {
        Expr expr = parseMultiplicativeExpr();
        while (accept(Category.PLUS, Category.MINUS)) {
            Op op = (token.category == Category.PLUS) ? Op.ADD : Op.SUB;
            nextToken();
            expr = new BinOp(expr, op, parseMultiplicativeExpr());
        }
        return expr;
    }


    private Expr parseMultiplicativeExpr() {
        Expr expr = parseUnaryExpr(); 
        while (accept(Category.ASTERISK, Category.DIV, Category.REM)) {
            Op op = switch (token.category) {
                case ASTERISK -> Op.MUL;
                case DIV -> Op.DIV;
                case REM -> Op.MOD;
                default -> throw new IllegalArgumentException("Unsupported operator");
            };
            nextToken();
            expr = new BinOp(expr, op, parseUnaryExpr());
        }
        return expr;
    }

    private Expr parseUnaryExpr() {
        if (accept(Category.PLUS, Category.MINUS, Category.ASTERISK, Category.AND, Category.SIZEOF)) {
            Category op = token.category;
            nextToken();
            Expr operand = parseUnaryExpr(); 
            return switch (op) {
                case PLUS -> operand; 
                case MINUS -> new BinOp(new IntLiteral(0), Op.SUB, operand); 
                case ASTERISK -> new ValueAtExpr(operand); 
                case AND -> new AddressOfExpr(operand); 
                case SIZEOF -> new SizeOfExpr(parseType());
                default -> throw new IllegalArgumentException("Invalid unary operator");
            };
        }
        return parsePostfixExpr();
    }


    private Expr parsePostfixExpr() {
        Expr expr = parsePrimaryExpr(); 

        while (accept(Category.LSBR, Category.LPAR)) { 
            if (accept(Category.LSBR)) { 
                nextToken();
                Expr index = parseExpr(); 
                expect(Category.RSBR); 
                expr = new ArrayAccessExpr(expr, index);
            } else { 
                nextToken();
                List<Expr> args = new ArrayList<>();
                if (!accept(Category.RPAR)) {
                    args.add(parseExpr());
                    while (accept(Category.COMMA)) {
                        nextToken();
                        args.add(parseExpr());
                    }
                }
                expect(Category.RPAR);
                expr = new FunCallExpr(((VarExpr) expr).name, args);
            }
        }
        return expr;
    }

    private Expr parseFuncCallExpr(Token id) {
        expect(Category.LPAR);
        List<Expr> args = new ArrayList<>();
        if (!accept(Category.RPAR)) {
            args.add(parseExpr());
            while (accept(Category.COMMA)) {
                nextToken();
                args.add(parseExpr());
            }
        }
        expect(Category.RPAR);
        return new FunCallExpr(id.data, args);
    }

    private Expr parsePrimaryExpr() {
        if (accept(Category.INT_LITERAL)) {
            return new IntLiteral(Integer.parseInt(expect(Category.INT_LITERAL).data));
        } 
        else if (accept(Category.CHAR_LITERAL)) {
            return new ChrLiteral(expect(Category.CHAR_LITERAL).data.charAt(0));
        } 
        else if (accept(Category.STRING_LITERAL)) {
            return new StrLiteral(expect(Category.STRING_LITERAL).data);
        } 
        else if (accept(Category.IDENTIFIER)) {
            Token id = expect(Category.IDENTIFIER);
            if (accept(Category.LPAR)) {
                return parseFuncCallExpr(id);
            }
            return new VarExpr(id.data);
        } 
        if (accept(Category.LPAR)) {
            nextToken();
            if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
                Type castType = parseType();
                while (accept(Category.ASTERISK)) { 
                    nextToken();
                    castType = new PointerType(castType);
                }
                expect(Category.RPAR);               
                Expr castExpr = parsePrimaryExpr();  
                return new TypecastExpr(castType, castExpr);  
            }
            Expr expr = parseExpr();
            expect(Category.RPAR);
            return expr;
        }
        
        
        else if (accept(Category.ASTERISK)) {  
            nextToken();
            return new ValueAtExpr(parsePrimaryExpr());
        } 
        else if (accept(Category.AND)) {  
            nextToken();
            return new AddressOfExpr(parsePrimaryExpr());
        } 
        else if (accept(Category.SIZEOF)) {  
            nextToken();
            expect(Category.LPAR);
            Type sizeOfType = parseType();
            expect(Category.RPAR);
            return new SizeOfExpr(sizeOfType);
        }
        if (accept(Category.ELSE)) {  
            throw new RuntimeException("Unexpected 'else' in expression.");
        }

        throw new RuntimeException("Unexpected token in expression: " + token);
    }



}
