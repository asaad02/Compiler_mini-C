package ast;

// enum representing binary and logical operators.
public enum Op {
  ADD, // +
  SUB, // -
  MUL, // *
  DIV, // /
  MOD, // %

  LT, // <
  GT, // >
  LE, // <=
  GE, // >=
  EQ, // ==
  NE, // /!=

  AND, // &&
  OR, // ||

  // ASSIGN // =

  // Op         ::= ADD | SUB | MUL | DIV | MOD | GT | LT | GE | LE | NE | EQ | OR | AND
}
