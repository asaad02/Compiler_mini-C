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
  private static int strCounter = 0;

  public ExprValCodeGen(AssemblyProgram asmProg, MemAllocCodeGen allocator) {
    this.asmProg = asmProg;
    this.allocator = allocator;
  }

  // generates assembly code for evaluating an expression.
  public Register visit(Expr e) {
    System.out.println("[ExprValCodeGen] Processing expression: " + e.getClass().getSimpleName());
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    // allocate a new register for result
    Register resReg = Register.Virtual.create();

    switch (e) {
      case IntLiteral i -> {
        text.emit(OpCode.LI, resReg, i.value);
        return resReg;
      }

      case StrLiteral s -> {
        Label strLabel = Label.get("str_" + strCounter++);
        // correct alignment
        asmProg.dataSection.emit(new Directive("align 2")); 
        String escapeChar = s.value.replace("\n", "\\n").replace("\t", "\\t").replace("\"", "\\\"");
        asmProg.dataSection.emit(strLabel);
        asmProg.dataSection.emit(new Directive("asciiz \"" + escapeChar + "\""));
        asmProg.dataSection.emit(new Directive("align 2"));
        text.emit(OpCode.LA, resReg, strLabel);
        return resReg;
      }

      case ChrLiteral c -> {
        asmProg.dataSection.emit(new Directive("align 2"));
        text.emit(OpCode.LI, resReg, c.value.charAt(0));
        asmProg.dataSection.emit(new Directive("align 2"));
        return resReg;
      }

      case SizeOfExpr sz -> {
        text.emit(OpCode.LI, resReg, allocator.computeSizeWithMask(sz.type));
        return resReg;
      }
      case BinOp b -> {
        Register leftReg = visit(b.left);
        Register rightReg = visit(b.right);

        switch (b.op) {
          case ADD -> text.emit(OpCode.ADD, resReg, leftReg, rightReg);
          case SUB -> text.emit(OpCode.SUB, resReg, leftReg, rightReg);
          case MUL -> text.emit(OpCode.MUL, resReg, leftReg, rightReg);
          case DIV -> {
            text.emit(OpCode.DIV, leftReg, rightReg);
            text.emit(OpCode.MFLO, resReg);
          }
          case MOD -> {
            text.emit(OpCode.DIV, leftReg, rightReg);
            text.emit(OpCode.MFHI, resReg);
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
          case GT -> text.emit(OpCode.SLT, resReg, rightReg, leftReg);
          case LE, GE -> {
            Register tempReg = Register.Virtual.create();
            text.emit(OpCode.SLT, tempReg, rightReg, leftReg);
            text.emit(OpCode.XORI, resReg, tempReg, 1);
          }
          case AND -> text.emit(OpCode.AND, resReg, leftReg, rightReg);
          case OR -> text.emit(OpCode.OR, resReg, leftReg, rightReg);
          default ->
              throw new UnsupportedOperationException("Unsupported binary operator: " + b.op);
        }
        return resReg;
      }

      case Assign a -> {
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(a.left);
        Register rhsReg = visit(a.right);
        Type type = a.left.type;
        if (type != null && type.equals(BaseType.CHAR)) {
          text.emit(OpCode.SB, rhsReg, addrReg, 0);
        } else {
          text.emit(OpCode.SW, rhsReg, addrReg, 0);
        }
        return rhsReg;
      }

      case VarExpr v -> {
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(v);
        VarDecl varDecl = allocator.getVarDecl(v.name);
        Type type = varDecl.type;
        if (type.equals(BaseType.CHAR)) {
          text.emit(OpCode.LB, resReg, addrReg, 0);
        } else {
          text.emit(OpCode.LW, resReg, addrReg, 0);
        }
        return resReg;
      }
      case FunCallExpr fc -> {
        if (SyscallCodeGen.isSyscall(fc.name)) {
          Register argReg = null;

          if (!fc.args.isEmpty()) {
            Expr firstArg = fc.args.get(0);

            // accessing an array element, resolve it as a value not an address
            if (firstArg instanceof ArrayAccessExpr || firstArg instanceof FieldAccessExpr) {
              argReg = new ExprValCodeGen(asmProg, allocator).visit(firstArg);
            } else if (firstArg instanceof VarExpr) {
              VarDecl varDecl = allocator.getVarDecl(((VarExpr) firstArg).name);
              if (varDecl.type instanceof ArrayType || varDecl.type instanceof StructType) {
                argReg = new ExprAddrCodeGen(asmProg, allocator).visit(firstArg);
              } else {
                argReg = visit(firstArg);
              }
            } else {
              argReg = visit(firstArg);
            }
          }

          SyscallCodeGen.generateSyscall(text, fc.name, argReg);
          return Register.Arch.v0;
        }

        Label funcLabel = Label.get(fc.name);
        int stackOffset = 0;

        for (int i = 0; i < fc.args.size(); i++) {
          Expr arg = fc.args.get(i);
          Type argType = arg.type;
          Register argReg;

          if (arg instanceof ArrayAccessExpr || arg instanceof FieldAccessExpr) {
            argReg = new ExprValCodeGen(asmProg, allocator).visit(arg);
          } else if (arg instanceof VarExpr) {
            VarDecl varDecl = allocator.getVarDecl(((VarExpr) arg).name);
            if (varDecl.type instanceof ArrayType || varDecl.type instanceof StructType) {
              argReg = new ExprAddrCodeGen(asmProg, allocator).visit(arg);
            } else {
              argReg = visit(arg);
            }
          } else {
            argReg = visit(arg);
          }

          if (i < 4) {
            text.emit(OpCode.ADDU, getArgumentRegister(i), argReg, Register.Arch.zero);
          } else {
            text.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -4);
            text.emit(OpCode.SW, argReg, Register.Arch.sp, 0);
            stackOffset += 4;
          }
        }

        text.emit(OpCode.JAL, funcLabel);

        if (stackOffset > 0) {
          text.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, stackOffset);
        }

        return Register.Arch.v0;
      }

      case ArrayAccessExpr a -> {
        System.out.println("[ExprValCodeGen] Resolving array access value: " + a);

        // Compute the address of arr[i][j]
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(a);

        // Load value from computed address
        Register valueReg = Register.Virtual.create();
        // Load integer value
        text.emit(OpCode.LW, valueReg, addrReg, 0);

        System.out.println("[ExprValCodeGen] Loaded value into register: " + valueReg);
        return valueReg;
      }

      case FieldAccessExpr fa -> {
        Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(fa);
        Register fieldValue = Register.Virtual.create();
        // Load from computed address
        text.emit(OpCode.LW, fieldValue, baseReg, 0);
        return fieldValue;
      }

      case ValueAtExpr va -> {
        Register addrReg = visit(va.expr);
        text.emit(OpCode.LW, resReg, addrReg, 0);
        return resReg;
      }

      case AddressOfExpr ao -> {
        return new ExprAddrCodeGen(asmProg, allocator).visit(ao.expr);
      }

      case TypecastExpr tc -> {
        Register castReg = visit(tc.expr);
        text.emit(OpCode.ADDU, resReg, castReg, Register.Arch.zero);
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
