package gen;

import ast.*;
import gen.asm.*;

/** Generates code to evaluate an expression and return the result in a register. */

/**
 * Generates assembly code for evaluating expressions such as Integer, character, and string
 * literals and Binary and logical operations and Variable access and assignments and Function calls
 * and memory operations
 */
public class ExprValCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  private static int strCounter = 0; // Unique counter for string literals

  public ExprValCodeGen(AssemblyProgram asmProg, MemAllocCodeGen allocator) {
    this.asmProg = asmProg;
    this.allocator = allocator;
  }

  /** Generates assembly code for evaluating an expression. */
  public Register visit(Expr e) {
    System.out.println("[ExprValCodeGen] Processing expression: " + e.getClass().getSimpleName());
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    Register resReg = Register.Virtual.create(); // Allocate a new register for result

    switch (e) {
      case IntLiteral i -> {
        System.out.println("[ExprValCodeGen] Loading integer literal: " + i.value);
        text.emit(OpCode.LI, resReg, i.value);
        return resReg;
      }

      case StrLiteral s -> {
        System.out.println("[ExprValCodeGen] Allocating string literal: " + s.value);
        // ensure wordaligned strings in .data
        Label strLabel = Label.get("str_" + strCounter++);

        // emit properly aligned string
        asmProg.dataSection.emit(new Directive("align 2"));
        asmProg.dataSection.emit(strLabel);
        asmProg.dataSection.emit(new Directive("asciiz \"" + s.value + "\""));

        // load the string address
        text.emit(OpCode.LA, resReg, strLabel);
        return resReg;
      }

      case ChrLiteral c -> {
        System.out.println("[ExprValCodeGen] Loading character literal: " + c.value);
        text.emit(OpCode.LI, resReg, c.value.charAt(0));
        return resReg;
      }

      case BinOp b -> {
        System.out.println("[ExprValCodeGen] Processing binary operation: " + b.op);
        Register leftReg = visit(b.left);
        Register rightReg = visit(b.right);

        switch (b.op) {
          case ADD -> text.emit(OpCode.ADD, resReg, leftReg, rightReg);
          case SUB -> text.emit(OpCode.SUB, resReg, leftReg, rightReg);
          case MUL -> text.emit(OpCode.MUL, resReg, leftReg, rightReg);
          case DIV -> {
            text.emit(OpCode.DIV, leftReg, rightReg);
            text.emit(OpCode.MFLO, resReg); // Quotient in LO
          }
          case MOD -> {
            text.emit(OpCode.DIV, leftReg, rightReg);
            text.emit(OpCode.MFHI, resReg); // Remainder in HI
          }
          case EQ, NE -> {
            Label trueLabel = Label.create();
            Label endLabel = Label.create();
            text.emit(b.op == Op.EQ ? OpCode.BEQ : OpCode.BNE, leftReg, rightReg, trueLabel);
            text.emit(OpCode.LI, resReg, 0);
            text.emit(OpCode.J, endLabel);
            text.emit(trueLabel);
            text.emit(OpCode.LI, resReg, 1);
            text.emit(endLabel);
          }
          case LT -> text.emit(OpCode.SLT, resReg, leftReg, rightReg);
          case LE -> {
            Register tempReg = Register.Virtual.create();
            text.emit(OpCode.SLT, tempReg, rightReg, leftReg);
            text.emit(OpCode.XORI, resReg, tempReg, 1);
          }
          case GT -> text.emit(OpCode.SLT, resReg, rightReg, leftReg);
          case GE -> {
            Register tempReg = Register.Virtual.create();
            text.emit(OpCode.SLT, tempReg, leftReg, rightReg);
            text.emit(OpCode.XORI, resReg, tempReg, 1);
          }
          case AND -> text.emit(OpCode.AND, resReg, leftReg, rightReg);
          case OR -> text.emit(OpCode.OR, resReg, leftReg, rightReg);
          default ->
              throw new UnsupportedOperationException("Unsupported binary operator: " + b.op);
        }
        return resReg;
      }

      case VarExpr v -> {
        System.out.println("[ExprValCodeGen] Processing variable: " + v.name);
        if (allocator.isGlobal(v.name)) {
          Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(v);
          text.emit(v.type == BaseType.CHAR ? OpCode.LB : OpCode.LW, resReg, addrReg, 0);
        } else {
          int offset = allocator.getLocalOffset(allocator.getVarDecl(v.name));
          text.emit(
              v.type == BaseType.CHAR ? OpCode.LB : OpCode.LW, resReg, Register.Arch.fp, offset);
        }
        return resReg;
      }
      case FunCallExpr fc -> {
        System.out.println("[ExprValCodeGen] Processing function call: " + fc.name);
        Register argReg = fc.args.isEmpty() ? null : visit(fc.args.get(0));

        switch (fc.name) {
          case "print_i" -> {
            text.emit(OpCode.ADDU, Register.Arch.a0, argReg, Register.Arch.zero);
            text.emit(OpCode.LI, Register.Arch.v0, 1);
            text.emit(OpCode.SYSCALL);
          }
          case "print_c" -> {
            text.emit(OpCode.ADDU, Register.Arch.a0, argReg, Register.Arch.zero);
            text.emit(OpCode.LI, Register.Arch.v0, 11);
            text.emit(OpCode.SYSCALL);
          }
          case "mcmalloc" -> {
            text.emit(OpCode.ADDU, Register.Arch.a0, argReg, Register.Arch.zero);
            text.emit(OpCode.LI, Register.Arch.v0, 9);
            text.emit(OpCode.SYSCALL);
          }
          case "print_s" -> {
            if (argReg == null) {
              throw new IllegalStateException(
                  "[ExprValCodeGen] ERROR: print_s requires an argument!");
            }
            text.emit(OpCode.ADDU, Register.Arch.a0, argReg, Register.Arch.zero);
            text.emit(OpCode.LI, Register.Arch.v0, 4); // Syscall: print string
            text.emit(OpCode.SYSCALL);
            return argReg;
          }
          case "read_i" -> {
            text.emit(OpCode.LI, Register.Arch.v0, 5); // Syscall: read integer
            text.emit(OpCode.SYSCALL);
            return Register.Arch.v0;
          }
          case "read_c" -> {
            text.emit(OpCode.LI, Register.Arch.v0, 12); // Syscall: read character
            text.emit(OpCode.SYSCALL);
            return Register.Arch.v0;
          }
          default -> {}
        }
        return argReg;
      }

      case ArrayAccessExpr aa -> {
        System.out.println("[ExprValCodeGen] Processing array access: " + aa);

        // ensure the array expression retains the correct type
        if (!(aa.array instanceof VarExpr v)) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Array expression is not a variable!");
        }

        VarDecl arrayDecl = allocator.getVarDecl(v.name);
        if (!(arrayDecl.type instanceof ArrayType at)) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Variable " + arrayDecl.name + " is not an array!");
        }

        // get base address of array
        Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(aa);

        // load value from computed address
        text.emit(OpCode.LW, resReg, baseReg, 0);

        return resReg;
      }

      case FieldAccessExpr fa -> {
        System.out.println("[ExprValCodeGen] Processing field access: " + fa.field);
        Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(fa.structure);

        VarDecl structVar = allocator.getVarDecl(((VarExpr) fa.structure).name);
        if (!(structVar.type instanceof StructType structType)) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Variable " + structVar.name + " is not a struct!");
        }

        int fieldOffset =
            new ExprAddrCodeGen(asmProg, allocator).computeFieldOffset(structType, fa.field);
        text.emit(fa.type == BaseType.CHAR ? OpCode.LB : OpCode.LW, resReg, baseReg, fieldOffset);
        return resReg;
      }

      case Assign a -> {
        switch (a.left) {
          case VarExpr v -> {
            System.out.println("[ExprValCodeGen] Processing assignment to variable: " + v.name);
            Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(v);
            Register rhsReg = visit(a.right);
            text.emit(a.type == BaseType.CHAR ? OpCode.SB : OpCode.SW, rhsReg, addrReg, 0);
            return rhsReg;
          }

          case ArrayAccessExpr aa -> {
            System.out.println("[ExprValCodeGen] Processing assignment to array: " + aa);
            Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(aa);
            Register rhsReg = visit(a.right);
            text.emit(OpCode.SW, rhsReg, baseReg, 0);
            return rhsReg;
          }

          case FieldAccessExpr fa -> {
            System.out.println("[ExprValCodeGen] Processing assignment to field: " + fa.field);
            Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(fa.structure);
            int fieldOffset =
                new ExprAddrCodeGen(asmProg, allocator)
                    .computeFieldOffset(
                        (StructType) allocator.getVarDecl(((VarExpr) fa.structure).name).type,
                        fa.field);
            Register rhsReg = visit(a.right);
            text.emit(
                a.type == BaseType.CHAR ? OpCode.SB : OpCode.SW, rhsReg, baseReg, fieldOffset);
            return rhsReg;
          }

          default ->
              throw new UnsupportedOperationException(
                  "[ExprValCodeGen] Unsupported assignment target: "
                      + a.left.getClass().getSimpleName());
        }
      }
      case TypecastExpr tc -> {
        System.out.println("[ExprValCodeGen] Processing typecast: " + tc.type);
        Register valReg = visit(tc.expr);
        if (tc.type == BaseType.CHAR) {
          text.emit(OpCode.ANDI, resReg, valReg, 0xFF);
        } else {
          text.emit(OpCode.ADDU, resReg, valReg, Register.Arch.zero);
        }
        return resReg;
      }

      default -> {
        throw new UnsupportedOperationException(
            "[ExprValCodeGen] Unsupported expression type: " + e.getClass().getSimpleName());
      }
    }
  }

  private Register getArgumentRegister(int index) {
    return switch (index) {
      case 0 -> Register.Arch.a0;
      case 1 -> Register.Arch.a1;
      case 2 -> Register.Arch.a2;
      case 3 -> Register.Arch.a3;
      default ->
          throw new UnsupportedOperationException("[ExprValCodeGen] Too many function arguments.");
    };
  }
}
