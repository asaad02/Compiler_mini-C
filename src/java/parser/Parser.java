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

/**
 * @author cdubach
 */
public class Parser extends CompilerPass {

  private Token token;

  private Queue<Token> buffer = new LinkedList<>();
  List<Integer> dimensions = new ArrayList<>();

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
   *
   * For P5 :
   * program    ::= (include)* (classdecl | structdecl | vardecl | fundecl | fundef)* EOF # Part V
   */
  private Program parseProgram() {
    // includes are ignored, so does not need to return an AST node
    parseIncludes();
    // decls is the list of declarations in the program (Program ::= (Decl)*)
    List<Decl> decls = new ArrayList<>();
    // loop through the tokens until we reach the end of file
    while (!accept(Category.EOF)) {
      Decl decl = parseDecl();
      if (decl != null) {
        decls.add(decl);
      }
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

  /*
   * program    ::= ( structdecl | vardecl | fundecl | fundef)* EOF
   */
  private Decl parseDecl() {
    /*
    *  ===================== STRUCT DECLARATION PARSING =====================
    * structdecl ::= structtype "{" (vardecl)+ "}" ";"
    * structtype ::= "struct" IDENT
    * if the token is a struct and we have an identifier and a left brace ["{"]
    * AST : StructType  ::= String
    * AST : StructTypeDecl ::= StructType VarDecl*
    * represents a struct type (the String is the name of the declared struct type)Struct declaration
    *
    * for P5 we have to regnize the class Decl
    * // Declarations/definitions
    * Decl       ::= ClassDecl | StructTypeDecl | VarDecl | FunDecl | FunDef // Part V

    */
    // if the token is a struct or an int or a char or a void
    if (accept(Category.CLASS, Category.STRUCT, Category.INT, Category.CHAR, Category.VOID)) {

      // Handle class declarations
      /*
       * classdecl  ::= classtype ["extends" IDENT] "{" (vardecl)* (fundef)* "}" # Part V
       */
      if (token.category == Category.CLASS
          && lookAhead(1).category == Category.IDENTIFIER
          && (lookAhead(2).category == Category.LBRA
              || lookAhead(2).category == Category.EXTENDS)) {
        return parseClassDecl();
      }
      // Handle struct declarations
      // if the token is a struct and we have an identifier and a left brace ["{"] then parse the
      // struct declaration
      if (token.category == Category.STRUCT
          && lookAhead(1).category == Category.IDENTIFIER
          && lookAhead(2).category == Category.LBRA) {
        Type structType = structtype();
        // System.out.println("Parsing struct declaration");
        return parseStructDecl(structType);
      }
      // we look a head to check if it's function or variable declaration
      // we look ahead to see if the next token is an Asterisk and an identifier and a left
      // parenthesis ["("]
      // or we look ahead to see if the next token is an identifier and a left parenthesis ["("]
      // or we look ahead to see if the next token is an identifier and an Asterisk and an
      // identifier and a left parenthesis ["("]
      if ((accept(Category.STRUCT)
              && lookAhead(1).category == Category.IDENTIFIER
              && lookAhead(2).category == Category.IDENTIFIER
              && lookAhead(3).category == Category.LPAR)
          || (lookAhead(1).category == Category.ASTERISK
              && lookAhead(2).category == Category.IDENTIFIER
              && lookAhead(3).category == Category.LPAR)
          || (lookAhead(1).category == Category.IDENTIFIER
              && lookAhead(2).category == Category.LPAR)
          || (lookAhead(1).category == Category.IDENTIFIER
              && lookAhead(2).category == Category.ASTERISK
              && lookAhead(3).category == Category.IDENTIFIER
              && lookAhead(4).category == Category.LPAR)) {

        // handle the type of the declaration and Asterisk and identifier
        // type  ::= ("int" | "char" | "void" | structtype) ("*")*
        Type type = parseType();
        Token id = expect(Category.IDENTIFIER);
        // System.out.println("Parsing function declaration or definition");
        return parseFuncDefOrDecl(type, id);
      }
      // if it's not LPAR ['('], then it's a variable declaration
      // vardecl    ::= type IDENT ("[" INT_LITERAL "]")* ";"
      // parse once the variable declaration
      // System.out.println("Parsing variable declaration");
      return parseVarDecl();
    }
    error(Category.STRUCT, Category.INT, Category.CHAR, Category.VOID);
    recovery();
    return null;
  }

  /*
   * ===================== STRUCT TYPE PARSING =====================
   * structtype ::= "struct" IDENT
   * AST : StructType  ::= String
   */
  private StructType structtype() {
    // expect the token to be a struct
    expect(Category.STRUCT);
    // return the struct type as AST node (string is the name of the declared struct type)
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
   *
   * for P5 :
   * type       ::= ("int" | "char" | "void" | structtype | classtype) ("*")*   # Part V
   */
  private Type parseType() {
    Type baseType;
    // Check the type ("int" | "char" | "void" | structtype ) ("*")*
    if (accept(Category.INT, Category.CHAR, Category.VOID)) {
      Token typeToken = expect(Category.INT, Category.CHAR, Category.VOID);
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
    }
    // Parse Struct Types (struct Node)
    else if (accept(Category.STRUCT)) {
      baseType = structtype();
    }
    /*
     * classtype ::= "class" IDENT # Part V
     */
    else if (accept(Category.CLASS)) {
      // consume class
      nextToken();
      String cname = expect(Category.IDENTIFIER).data;
      baseType = new ClassType(cname);
    } else {
      error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS);
      recovery();
      return BaseType.UNKNOWN;
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
   *  ===================== STRUCT DECLARATION PARSING =====================
   * structdecl ::= structtype "{" (vardecl)+ "}" ";"
   * StructTypeDecl ::= StructType VarDecl*
   * represents a struct type (the String is the name of the declared struct type)Struct declaration
   */
  private StructTypeDecl parseStructDecl(Type structType) {
    // expect the left brace ["{"]
    expect(Category.LBRA);
    // varDecls is the list of variable declarations in the struct
    List<VarDecl> varDecls = new ArrayList<>();
    // Must have at least one variable declaration
    if (!accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT)) {
      error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
    }
    // while we have not reached the right brace ["}"]
    do {
      if (accept(Category.STRUCT)
          && lookAhead(1).category == Category.IDENTIFIER
          && lookAhead(2).category == Category.LBRA) {
        // Parse nested struct declaration
        Type nestedStructType = structtype();
        StructTypeDecl nestedStructDecl = parseStructDecl(nestedStructType);
        // Add the nested struct declaration as a variable declaration
        varDecls.add(new VarDecl(nestedStructDecl.structType, nestedStructDecl.structType.name));
      } else {
        varDecls.add(parseVarDecl());
      }
    } while (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT));
    // expect the right brace ["}"]
    expect(Category.RBRA);
    // expect the semicolon [";"]
    expect(Category.SC);
    // return the struct type declaration AST node
    return new StructTypeDecl((StructType) structType, varDecls);
  }

  /*
   *  ===================== CLASS DECLARATION PARSING =====================
   * ClassDecl ::= (ClassType ClassType | ClassType) (VarDecl)* (FunDef)*
   * Part V - First ClassType is for newly-declared class
   * second one is dedicated to optional parent name
   *
   * classdecl  ::= classtype ["extends" IDENT] "{" (vardecl)* (fundef)* "}" # Part V
   * classtype  ::= "class" IDENT   # Part V
   */

  private Decl parseClassDecl() {
    // Consume class
    // classtype  ::= "class" IDENT   # Part V
    expect(Category.CLASS);
    String className = expect(Category.IDENTIFIER).data;

    // Optional extends parent
    String parentName = null;
    if (accept(Category.EXTENDS)) {
      nextToken();
      parentName = expect(Category.IDENTIFIER).data;
    }

    // class body
    expect(Category.LBRA);

    // vardecl
    List<VarDecl> fields = new ArrayList<>();
    while (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS)) {
      // lookAhead(1) is the IDENT, lookAhead(2) is what follows
      if (lookAhead(2).category == Category.LPAR) {
        break;
      }
      fields.add(parseVarDecl());
    }

    // Methods
    List<FunDef> methods = new ArrayList<>();
    while (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS)) {
      // Parse the return type and name
      Type returnType = parseType();
      Token nameMethod = expect(Category.IDENTIFIER);
      // Only definitions allowed inside
      Decl decl = parseFuncDefOrDecl(returnType, nameMethod);
      if (decl instanceof FunDef fd) {
        methods.add(fd);
      } else {
        // declaration instead of a definition will show erorr
        error(Category.LBRA);
        recovery();
      }
    }

    // End body
    expect(Category.RBRA);

    // AST node
    // ClassDecl ::= (ClassType ClassType | ClassType) (VarDecl)* (FunDef)*
    return new ClassDecl(className, parentName, fields, methods);
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
    while (accept(Category.LSBR)) {
      nextToken();
      int size = Integer.parseInt(expect(Category.INT_LITERAL).data);
      List<Integer> dimensions123 = new ArrayList<>();
      dimensions123.add(size);
      expect(Category.RSBR);
      // new array with list of int literals ,passsize to the list

      type = new ArrayType(type, dimensions123, size);
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
    // params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL"]")*)*]
    List<VarDecl> params = parseParams();
    // FunDecl(type, id.data, params);
    if (accept(Category.LBRA)) {
      // block      ::= "{" (vardecl)* (stmt)* "}"
      // Parse function body
      return new FunDef(type, id.data, params, parseBlock());
    } else if (accept(Category.SC)) {
      /*
       * fundecl   ::= type IDENT "(" params ")" ";"
       * # function declaration
       */
      expect(Category.SC);
      return new FunDecl(type, id.data, params);
    } else {
      error(Category.LBRA, Category.SC);
      recovery();
      return null;
    }
  }

  /*
   * Parse the parameters of a function declaration or definition
   * params     ::= [ type IDENT ("[" INT_LITERAL "]")* ("," type IDENT ("[" INT_LITERAL"]")*)*]
   * AST:
   * VarDecl ::= Type String
   */
  private List<VarDecl> parseParams() {
    List<VarDecl> params = new ArrayList<>();
    expect(Category.LPAR);
    if (!accept(Category.RPAR) && !accept(Category.EOF)) {
      do {
        Type paramType = parseType();
        // expect the identifier
        String paramName = expect(Category.IDENTIFIER).data;
        // check if it's an array '[' for the parameter
        while (accept(Category.LSBR) && !accept(Category.EOF)) {
          nextToken();
          List<Integer> dimensions = new ArrayList<>();
          // list of int literals
          // size is a list of int literals
          // size ::= INT_LITERAL
          int size = Integer.parseInt(expect(Category.INT_LITERAL).data);
          dimensions.add(size);
          expect(Category.RSBR);
          paramType = new ArrayType(paramType, dimensions, size);
        }
        params.add(new VarDecl(paramType, paramName));
        // check if there's a comma for more parameters
        if (accept(Category.COMMA)) {
          nextToken();
        } else {
          break;
        }
      } while (true);
    }
    expect(Category.RPAR);
    return params;
  }

  /*
   * ===================== BLOCK PARSING =====================
   * block      ::= "{" (vardecl)* (stmt)* "}"
   * AST:
   * Block ::= VarDecl* Stmt*
   * Block statement (starts with { and end with } in the source code)
   * Block      ::= VarDecl* Stmt*
   */
  private Block parseBlock() {
    expect(Category.LBRA); // Consume '{'

    // Store VarDecl
    List<VarDecl> vds = new ArrayList<>();
    // store statements
    List<Stmt> stmts = new ArrayList<>(); // Store Stmt

    while (!accept(Category.RBRA) && !accept(Category.EOF)) {
      if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS)) {
        // elements.add(parseVarDecl());
        vds.add(parseVarDecl());
      } else {
        // elements.add(parseStmt());
        stmts.add(parseStmt());
      }
    }

    expect(Category.RBRA);
    // return new Block(elements);
    return new Block(vds, stmts);
  }

  /*
  * stmt ::= block | "while" "(" exp ")" stmt # while loop | "if" "(" exp ")" stmt ["else" stmt] #
  * if then else | "return" [exp] ";" # return | exp ";" # expression statement, e.g. a function
  * call | "continue" ";" # continue | "break" ";" Stmt ::= Block | While | If | Return | Continue
  * | Break | ExprStmt
  * Stmt       ::= Block | While | If | Return | Continue | Break | ExprStmt
  * // An expression statement (e.g. x+2;)

  */
  private Stmt parseStmt() {
    return switch (token.category) {
      // if the token is a return then parse the return statement
      // return statement : (the Expr is optional)
      // Return     ::= [Expr]
      case RETURN -> parseReturnStmt();
      // if the token is a while then parse the while statement
      // While      ::= Expr Stmt
      // While loop statement : while (Expr) Stmt;
      case WHILE -> parseWhileStmt();
      // if the token is an if then parse the if statement
      // If         ::= Expr Stmt [Stmt]
      // If statement: if (Expr) Stmt1 else Stmt2; (if the second Stmt is null, this means there is
      // no else part)
      case IF -> parseIfStmt();
      // if the token is a continue then parse the continue statement
      // Continue   ::= ;
      // Continue statement (nothing stored)
      case CONTINUE -> {
        nextToken();
        expect(Category.SC);
        yield new Continue();
      }
      // if the token is a break then parse the break statement
      // Break      ::= ;
      // Break statement (nothing stored)
      case BREAK -> {
        nextToken();
        expect(Category.SC);
        yield new Break();
      }
      // if the token is a left brace then parse
      // Block      ::= VarDecl* Stmt*
      // Block statement (starts with { and end with } in the source code)
      case LBRA -> parseBlock();
      // if the token is  else then parse the expression statement
      case ELSE -> {
        error(Category.IF);
        recovery();
        yield new ExprStmt(new IntLiteral(0));
      }
      // default case parse the expression statement
      // ExprStmt ::= Expr
      default -> parseExprStmt();
    };
  }

  // parser [While] statement - "while" "(" exp ")" stmt
  // While      ::= Expr Stmt
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
  // If         ::= Expr Stmt [Stmt]
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
  // Return     ::= [Expr]
  private Stmt parseReturnStmt() {
    expect(Category.RETURN);
    Expr expr = null;
    if (!accept(Category.SC)) {
      expr = parseExpr();
    } else {
      // Use NONE type for return without expression
      // Placeholder to satisfy AST optional
      // return type for the return statement
      expr = null;
    }
    expect(Category.SC);
    return new Return(expr);
  }

  /*
   * exp      ::= "(" exp ")"
   *          | exp "=" exp
   *          | (IDENT | INT_LITERAL)
   *          | ("-" | "+") exp
   *          | CHAR_LITERAL
   *          | STRING_LITERAL
   *          | exp (">" | "<" | ">=" | "<=" | "!=" | "==" | "+" | "-" | "/" | "*" | "%" | "||" | "&&") exp  # binary operators
   *          | arrayaccess | fieldaccess | valueat | addressof | funcall | sizeof | typecast
   */
  /*
   * Expression Grammar with Precedence and Associativity:
   *
   * Precedence Highest to Lowest:
   * 1. Postfix Operators: () (function call) and  [] array access and . (field access) all will be [Left-to-Right]
   * 2. Unary Operators: +, -, *, &, sizeof [Right-to-Left]
   * 3. Multiplicative: *, /, % [Left-to-Right]
   * 4. Additive: +, - [Left-to-Right]
   * 5. Relational: <, <=, >, >= [Left-to-Right]
   * 6. Equality: ==, != [Left-to-Right]
   * 7. Logical AND: && [Left-to-Right]
   * 8. Logical OR: || [Left-to-Right]
   * 9. Assignment: = [Right-to-Left]
   */

  // parser for the expression statement - exp ";"
  // ExprStmt ::= Expr
  private Stmt parseExprStmt() {
    // Parse the expression
    Expr expr = parseExpr();
    // Expect the semicolon
    expect(Category.SC);
    // Return the expression statement AST node
    return new ExprStmt(expr);
  }

  // Assignment should be right to left associative
  // Assignment ::= Expr "=" Expr
  private Expr parseExpr() {
    // Parse the left-hand side of the operator
    Expr lhs = parseLogicalOrExpr();
    // Assignment ::= Expr "=" Expr
    if (accept(Category.ASSIGN)) {
      nextToken();
      // Recursive right hand side of the operator
      Expr rhs = parseExpr();
      // Return the assignment AST node
      return new Assign(lhs, rhs);
    }
    // Return the left-hand side of the operator
    return lhs;
  }

  // Logical OR expression left-to-right associativity
  // LogicalOr  ::= LogicalAnd ("||" LogicalAnd)*
  // parser for the logical OR expression - exp "||" exp
  private Expr parseLogicalOrExpr() {
    // Parse the left-hand side of the operator
    Expr expr = parseLogicalAndExpr();
    // exp "||" exp
    // loop through the logical OR expression
    while (accept(Category.LOGOR)) {
      nextToken();
      // parse the right hand side of the operator
      expr = new BinOp(expr, Op.OR, parseLogicalAndExpr());
    }
    return expr;
  }

  // Parses a logical AND expression. [exp ::= exp "&&" exp]
  // LogicalAnd ::= Equality ("&&" Equality)*
  // left-to-right associativity
  private Expr parseLogicalAndExpr() {
    // Parse the left-hand side of the operator
    Expr expr = parseEqualityExpr();
    // loop through the logical AND expression
    while (accept(Category.LOGAND)) {
      nextToken();
      // Parse the right-hand side of the operator
      expr = new BinOp(expr, Op.AND, parseEqualityExpr());
    }
    return expr;
  }

  // Parses an equality expression. [ exp ::= exp ("==" | "!=") exp]
  // left-to-right associativity
  private Expr parseEqualityExpr() {
    // Parse the left-hand side of the operator
    Expr expr = parseRelationalExpr();
    // Parse the right-hand side of the operator
    while (accept(Category.EQ, Category.NE)) {
      if (token.category == Category.EQ) {
        nextToken();
        expr = new BinOp(expr, Op.EQ, parseRelationalExpr());
      } else {
        nextToken();
        expr = new BinOp(expr, Op.NE, parseRelationalExpr());
      }
      // Parse the right-hand side of the operator
      // expr = new BinOp(expr, op, parseRelationalExpr());
    }
    return expr;
  }

  // Parses a relational expression. [exp ::= exp (">" | "<" | ">=" | "<=") exp]
  private Expr parseRelationalExpr() {
    Expr expr = parseAdditiveExpr();
    // Parse the right-hand side of the operator
    while (accept(Category.LT, Category.GT, Category.LE, Category.GE)) {
      if (token.category == Category.LT) {
        nextToken();
        expr = new BinOp(expr, Op.LT, parseAdditiveExpr());
      } else if (token.category == Category.GT) {
        nextToken();
        expr = new BinOp(expr, Op.GT, parseAdditiveExpr());
      } else if (token.category == Category.LE) {
        nextToken();
        expr = new BinOp(expr, Op.LE, parseAdditiveExpr());
      } else {
        nextToken();
        expr = new BinOp(expr, Op.GE, parseAdditiveExpr());
      }
    }
    return expr;
  }

  // Parses an additive expression. [exp ::= exp ("+" | "-") exp]
  // left-to-right associativity
  private Expr parseAdditiveExpr() {
    Expr expr = parseMultiplicativeExpr();
    // Parse the right-hand side of the operator
    while (accept(Category.PLUS, Category.MINUS)) {
      if (token.category == Category.PLUS) {
        nextToken();
        expr = new BinOp(expr, Op.ADD, parseMultiplicativeExpr());
      } else {
        nextToken();
        expr = new BinOp(expr, Op.SUB, parseMultiplicativeExpr());
      }
    }
    return expr;
  }

  // Parses a multiplicative expression. [exp ::= exp ("*" | "/" | "%") exp]
  // left-to-right associativity
  private Expr parseMultiplicativeExpr() {
    // Parse the left-hand side of the operator
    Expr expr = parseUnaryExpr();
    while (accept(Category.ASTERISK, Category.DIV, Category.REM)) {
      if (token.category == Category.ASTERISK) {
        nextToken();
        expr = new BinOp(expr, Op.MUL, parseUnaryExpr());
      } else if (token.category == Category.DIV) {
        nextToken();
        expr = new BinOp(expr, Op.DIV, parseUnaryExpr());
      } else {
        nextToken();
        expr = new BinOp(expr, Op.MOD, parseUnaryExpr());
      }
    }
    return expr;
  }

  // Unary operators (*, &, +, -, sizeof, type cast)
  // Parse a unary expression
  // Unary operators is right to left associativity
  private Expr parseUnaryExpr() {
    if (accept(Category.PLUS, Category.MINUS, Category.ASTERISK, Category.AND, Category.SIZEOF)) {
      Category op = token.category;
      nextToken();
      // Handle sizeof operator
      if (op == Category.SIZEOF) {
        expect(Category.LPAR);

        if (!accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS)) {
          error(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT);
          recovery();
          return new SizeOfExpr(BaseType.UNKNOWN);
        }

        // handling sizeof
        Type sizeOfType = parseType();
        expect(Category.RPAR);
        return new SizeOfExpr(sizeOfType);
      }

      // recursively parse the unary expression
      Expr operand = parseUnaryExpr();
      switch (op) {
        case PLUS:
          return new BinOp(new IntLiteral(0), Op.ADD, operand);
        case MINUS:
          return new BinOp(new IntLiteral(0), Op.SUB, operand);
        case ASTERISK:
          return new ValueAtExpr(operand);
        case AND:
          return new AddressOfExpr(operand);
        default:
          error(Category.PLUS, Category.MINUS, Category.ASTERISK, Category.AND);
          recovery();
          return new IntLiteral(0);
      }
    }
    return parsePostfixExpr();
  }

  // psofix operators: () (function call) and  [] array access and . (field access)
  private Expr parsePostfixExpr() {
    // we will parse the primary expression
    Expr expr = parsePrimaryExpr();
    return parsePostfixExprTail(expr);
  }

  // parse the function call expression
  private FunCallExpr parseFuncCallExpr(Token id) {
    expect(Category.LPAR);
    List<Expr> args = new ArrayList<>();
    // Check if there are arguments
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

  /**
   * Parses a primary expression. [exp ::= "(" exp ")" | IDENT | INT_LITERAL | CHAR_LITERAL |
   * STRING_LITERAL] | valueat | addressof | funcall | sizeof | typecast | arrayaccess | fieldaccess
   */
  private Expr parsePrimaryExpr() {
    // check if the token is an identifier
    // INT_LITERAL
    if (accept(Category.INT_LITERAL)) {
      return new IntLiteral(Integer.parseInt(expect(Category.INT_LITERAL).data));
    }
    // check if the token is an integer literal ["INT_LITERAL"] or a character literal
    // ["CHAR_LITERAL"] or a string literal ["STRING_LITERAL"]
    else if (accept(Category.CHAR_LITERAL)) {
      return new ChrLiteral(expect(Category.CHAR_LITERAL).data);
    }
    // STRING_LITERAL
    else if (accept(Category.STRING_LITERAL)) {
      return new StrLiteral(expect(Category.STRING_LITERAL).data);
    }
    // IDENTIFIER
    else if (accept(Category.IDENTIFIER)) {
      Token id = expect(Category.IDENTIFIER);
      // Check for function call, array access, or field access
      // function call
      if (accept(Category.LPAR)) {
        return parseFuncCallExpr(id);
      }
      // array access or field access
      else if (accept(Category.LSBR)) {
        nextToken();
        List<Expr> dimensionsExper1 = new ArrayList<>();
        Expr index = parseExpr();
        dimensionsExper1.add(index);
        expect(Category.RSBR);
        return new ArrayAccessExpr(new VarExpr(id.data), dimensionsExper1, index);
      }
      /*
      // here the error for sort list
      else if (accept(Category.DOT)) {
        nextToken();
        // Parse the field identifier
        String field = expect(Category.IDENTIFIER).data;
        // to test for sort link list
        return new FieldAccessExpr(new VarExpr(id.data), field);
      }
        */
      return new VarExpr(id.data);
    }
    // Class instantiation
    /*
     * newInstance     ::= "new" classtype"()"
     * Part V - new class object instantiation
     */
    else if (accept(Category.NEW)) {
      // new class nameofclass()
      // consume NEW
      nextToken();
      // consume CLASS keyword
      expect(Category.CLASS);
      String cname = expect(Category.IDENTIFIER).data;
      expect(Category.LPAR);
      expect(Category.RPAR);
      // AST node
      return new NewInstance(cname);
    }
    // '(' expr ')' typecast ::= "(" type ")" exp
    else if (accept(Category.LPAR)) {
      nextToken();
      // Check if it's a type cast
      if (accept(Category.INT, Category.CHAR, Category.VOID, Category.STRUCT, Category.CLASS)) {
        Type castType = parseType();
        expect(Category.RPAR);
        Expr expr = parseUnaryExpr();
        return new TypecastExpr(castType, expr);
      } else {
        //  handle (a + b)
        Expr expr = parseExpr();
        expect(Category.RPAR);
        // check if field access
        // Reenter postfix parsing after parenthesized expression
        return parsePostfixExprTail(expr);
        // return expr;
      }
    }
    // check if the token is value at ["*"] - Value at operator (pointer indirection)
    // valueat ::= "*" exp
    else if (accept(Category.ASTERISK)) {
      nextToken();
      return new ValueAtExpr(parsePrimaryExpr());
    }
    // check if the token is an address of ["&"] - Address of operator
    // addressof ::= "&" exp
    else if (accept(Category.AND)) {
      nextToken();
      return new AddressOfExpr(parsePrimaryExpr());
    }
    // check if the token is a sizeof ["sizeof"] - Sizeof operator
    // sizeof ::= "sizeof" "(" type ")"
    else if (accept(Category.SIZEOF)) {
      expect(Category.SIZEOF);
      expect(Category.LPAR);
      Type sizeOfType = parseType();
      expect(Category.RPAR);
      return new SizeOfExpr(sizeOfType);
    } else if (accept(Category.LPAR)) {
      nextToken();
      Expr expr = parseExpr();
      expect(Category.RPAR);
      return expr;
    } // check if the token is a left square brace ["["]
    else if (accept(Category.LSBR)) {
      nextToken();
      List<Expr> dimensionsExper2 = new ArrayList<>();
      Expr index = parseExpr();
      dimensionsExper2.add(index);
      expect(Category.RSBR);
      return new ArrayAccessExpr(parsePrimaryExpr(), dimensionsExper2, index);
    } else if (accept(Category.DOT)) {
      nextToken();
      // Parse the field identifier
      String field = expect(Category.IDENTIFIER).data;
      // to test for sort link list
      return new FieldAccessExpr(parsePrimaryExpr(), field);
    } // plus and minus
    else if (accept(Category.PLUS, Category.MINUS)) {
      Category op = token.category;
      nextToken();
      return new BinOp(
          new IntLiteral(0), op == Category.PLUS ? Op.ADD : Op.SUB, parsePrimaryExpr());
    } else {
      error(
          Category.INT_LITERAL,
          Category.CHAR_LITERAL,
          Category.STRING_LITERAL,
          Category.IDENTIFIER,
          Category.LPAR,
          Category.ASTERISK,
          Category.AND,
          Category.SIZEOF,
          Category.LSBR,
          Category.DOT);
      recovery();
      return new IntLiteral(0);
    }
  }

  // handle postfix operators after primary expression
  private Expr parsePostfixExprTail(Expr expr) {
    while (true) {
      if (accept(Category.LSBR)) {
        nextToken();
        List<Expr> dimensionsExper3 = new ArrayList<>();
        Expr index = parseExpr();
        expect(Category.RSBR);
        dimensionsExper3.add(index);
        expr = new ArrayAccessExpr(expr, dimensionsExper3, index);
      }
      /*
       *  instanceFuncall ::= exp "." funcall
       * Part V : call to an instance method
       * */
      else if (accept(Category.DOT)) {
        // consume '.'
        nextToken();
        Token id = expect(Category.IDENTIFIER);
        if (accept(Category.LPAR)) {
          // P5 : instance method: exp.method(args)
          // instanceFuncall ::= exp "." funcall
          FunCallExpr call = parseFuncCallExpr(id);
          // P5 : AST node
          expr = new InstanceFunCallExpr(expr, call);
        } else {
          // field access: exp.field
          expr = new FieldAccessExpr(expr, id.data);
        }
      } else {
        break;
      }
    }
    return expr;
  }

  private void recovery() {
    // Skip tokens until a recovery point is found after finish the statement
    while (!accept(Category.SC, Category.LBRA, Category.RBRA, Category.EOF)) {
      nextToken();
      // System.out.println("Skipping token: " + token);
    }
    // Consume the token if it's not EOF
    if (!accept(Category.EOF)) {
      nextToken();
    }
  }
}
