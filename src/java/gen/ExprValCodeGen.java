package gen;

import ast.*;
import gen.asm.*;
import java.util.ArrayList;
import java.util.List;

/** Generates code to evaluate an expression and return the result in a register. */

/**
 * Generates assembly code for evaluating expressions such as Integer, character, and string
 * literals and Binary and logical operations and Variable access and assignments and Function calls
 * and memory operations
 */
public class ExprValCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  private static int strCounter = 0;
  private final List<String> definedFunctions;

  public ExprValCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, List<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
  }

  // generates assembly code for evaluating an expression.
  public Register visit(Expr e) {
    System.out.println("[ExprValCodeGen] Processing expression: " + e.getClass().getSimpleName());
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
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
        text.emit(OpCode.LI, resReg, c.value.charAt(0));
        return resReg;
      }

      case SizeOfExpr sz -> {
        text.emit(OpCode.LI, resReg, allocator.computeSize(sz.type));
        return resReg;
      }

      case BinOp b -> {
        switch (b.op) {
          case ADD, SUB, MUL, DIV, MOD, EQ, NE, LT, GT, LE, GE -> {
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

              case EQ, NE -> generateEqualityCheck(text, leftReg, rightReg, resReg, b.op);
              case LT -> text.emit(OpCode.SLT, resReg, leftReg, rightReg);
              case GT -> text.emit(OpCode.SLT, resReg, rightReg, leftReg);

              case LE -> { // Less than or equal (left <= right)
                Register tempReg = Register.Virtual.create();
                text.emit(OpCode.SLT, tempReg, rightReg, leftReg); // right < left
                text.emit(OpCode.XORI, resReg, tempReg, 1); // Flip result (1 if left <= right)
              }
              case GE -> { // Greater than or equal (left >= right)
                Register tempReg = Register.Virtual.create();
                text.emit(OpCode.SLT, tempReg, leftReg, rightReg); // left < right
                text.emit(OpCode.XORI, resReg, tempReg, 1); // Flip result (1 if left >= right)
              }
            }
          }

          case AND -> {
            Label falseLabel = Label.create();
            Label endLabel = Label.create();
            Register leftReg = visit(b.left);

            // Short-circuit if left operand is zero
            text.emit(OpCode.BEQZ, leftReg, falseLabel);

            // evaluate right operand if needed
            Register rightReg = visit(b.right);
            text.emit(OpCode.BEQZ, rightReg, falseLabel);
            text.emit(OpCode.LI, resReg, 1); // If both nonzero, return 1
            text.emit(OpCode.J, endLabel);

            // Short-circuit case If left was 0, return 0
            text.emit(falseLabel);
            text.emit(OpCode.LI, resReg, 0);
            text.emit(endLabel);
          }

          case OR -> {
            Label trueLabel = Label.create();
            Label endLabel = Label.create();
            Register leftReg = visit(b.left);

            // Short-circuit if left operand is 1
            text.emit(OpCode.BNEZ, leftReg, trueLabel);

            // Now evaluate right operand only if needed
            Register rightReg = visit(b.right);
            text.emit(OpCode.BNEZ, rightReg, trueLabel);
            text.emit(OpCode.LI, resReg, 0); // If both are 0, return 0
            text.emit(OpCode.J, endLabel);

            // Short-circuit case If left was 1, return 1
            text.emit(trueLabel);
            text.emit(OpCode.LI, resReg, 1);
            text.emit(endLabel);
          }

          default ->
              throw new UnsupportedOperationException(
                  "[ExprValCodeGen] Unsupported binary operator: " + b.op);
        }
        return resReg;
      }

      case Assign a -> {
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(a.left);
        Register rhsReg = visit(a.right);
        Type type = a.left.type;
        if (type == null) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Left-hand side type is NULL for assignment.");
        }

        if (type.equals(BaseType.CHAR)) {
          text.emit(OpCode.SB, rhsReg, addrReg, 0); // Store Byte
        } else {
          text.emit(OpCode.SW, rhsReg, addrReg, 0); // Store Word
        }
        return rhsReg;
      }

      case VarExpr v -> {
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(v);
        VarDecl varDecl = allocator.getVarDecl(v.name);

        if (varDecl == null || varDecl.type == null) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Variable type not found: " + v.name);
        }

        Type type = varDecl.type;
        if (type instanceof StructType) {
          System.out.printf(
              "[ExprValCodeGen] Struct '%s' loaded from offset: %d\n",
              v.name, allocator.getLocalOffset(varDecl));
        }

        if (type.equals(BaseType.CHAR)) {
          text.emit(OpCode.LBU, resReg, addrReg, 0);
        } else {
          text.emit(OpCode.LW, resReg, addrReg, 0);
        }
        return resReg;
      }

      case FunCallExpr fc -> {
        if (SyscallCodeGen.isSyscall(fc.name)) {
          Register argReg = fc.args.isEmpty() ? null : visit(fc.args.get(0));
          SyscallCodeGen.generateSyscall(text, fc.name, argReg);
          return Register.Arch.v0;
        }

        String mangledFunctionName = getMangledFunctionName(fc.name, fc.args);
        Label funcLabel = Label.get(mangledFunctionName);

        if (!definedFunctions.contains(mangledFunctionName)) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Function not found: " + mangledFunctionName);
        }

        List<Register> argumentRegs = new ArrayList<>();

        for (Expr arg : fc.args) {
          Type argType = arg.type;
          if (argType instanceof StructType) {
            int structSize = allocator.computeSize(argType);
            structSize = allocator.alignTo8(structSize);
            Register addrReg = new ExprAddrCodeGen(asmProg, allocator).visit(arg);

            for (int word = 0; word < structSize; word += 4) {
              Register temp = Register.Virtual.create();
              text.emit(OpCode.LW, temp, addrReg, word);
              text.emit(OpCode.SW, temp, Register.Arch.sp, word);
            }
          } else {
            argumentRegs.add(visit(arg));
          }
        }

        // Pass arguments in registers and stack
        for (int i = 0; i < argumentRegs.size(); i++) {
          if (i < 4) {
            text.emit(OpCode.ADDU, getArgReg(i), argumentRegs.get(i), Register.Arch.zero);
          } else {
            int stackPos = (i - 4) * 4;
            text.emit(OpCode.SW, argumentRegs.get(i), Register.Arch.sp, stackPos);
          }
        }

        text.emit(OpCode.JAL, funcLabel);
        text.emit(OpCode.NOP);

        // Struct Return Values
        if (fc.type instanceof StructType structType) {
          int structSize = allocator.computeSize(structType);
          structSize = allocator.alignTo8(structSize);

          Register returnAddr = Register.Virtual.create();
          Register structAddr = Register.Virtual.create();

          System.out.printf(
              "[ExprValCodeGen] Copying struct return value (Size: %d, Align: 8) from $sp\n",
              structSize);

          text.emit(OpCode.ADDIU, returnAddr, Register.Arch.sp, -structSize); // Align return space
          text.emit(OpCode.ADDIU, structAddr, Register.Arch.fp, -structSize);

          for (int word = 0; word < structSize; word += 4) {
            Register temp = Register.Virtual.create();
            text.emit(OpCode.LW, temp, returnAddr, word);
            text.emit(OpCode.SW, temp, structAddr, word);
          }

          return structAddr;
        }

        return Register.Arch.v0;
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

      case ArrayAccessExpr a -> {
        Register baseAddr = new ExprAddrCodeGen(asmProg, allocator).visit(a.array);
        Register indexReg = visit(a.index);

        Type elementType = ((ArrayType) a.array.type).elementType;
        int elementSize = allocator.computeSize(elementType);

        Register offsetReg = Register.Virtual.create();
        text.emit(OpCode.LI, offsetReg, elementSize);
        text.emit(OpCode.MUL, indexReg, indexReg, offsetReg);

        Register finalAddr = Register.Virtual.create();
        text.emit(OpCode.ADDU, finalAddr, baseAddr, indexReg); // finalAddr = base + offset

        if (elementType.equals(BaseType.CHAR)) {
          text.emit(OpCode.LBU, resReg, finalAddr, 0); // Load Byte (Unsigned)
        } else {
          text.emit(OpCode.LW, resReg, finalAddr, 0); // Load Word
        }
        return resReg;
      }
      case FieldAccessExpr fa -> {
        Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(fa.structure);
        if (fa.structure.type instanceof StructType structType) {
          int offset = allocator.computeFieldOffset(structType, fa.field);
          offset = allocator.alignTo(offset, 4);

          if (fa.type.equals(BaseType.CHAR)) {
            text.emit(OpCode.LB, resReg, baseReg, offset);
          } else {
            text.emit(OpCode.LW, resReg, baseReg, offset);
          }
          return resReg;
        } else {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Field access on non-struct type: " + fa.structure.type);
        }
      }

      default ->
          throw new UnsupportedOperationException(
              "[ExprValCodeGen] Unsupported expression type: " + e.getClass().getSimpleName());
    }
    // return resReg;
  }

  private void generateEqualityCheck(
      AssemblyProgram.TextSection text, Register left, Register right, Register res, Op op) {
    Label trueLabel = Label.create();
    Label endLabel = Label.create();
    text.emit(op == Op.EQ ? OpCode.BEQ : OpCode.BNE, left, right, trueLabel);
    text.emit(OpCode.LI, res, 0);
    text.emit(OpCode.J, endLabel);
    text.emit(trueLabel);
    text.emit(OpCode.LI, res, 1);
    text.emit(endLabel);
  }

  private String getMangledFunctionName(String functionName, List<Expr> args) {
    return functionName + "_" + args.size();
  }

  private Register getArgReg(int index) {
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
