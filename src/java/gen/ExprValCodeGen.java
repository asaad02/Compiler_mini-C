package gen;

import ast.*;
import gen.asm.*;
import java.util.ArrayList;
import java.util.List;

/** Generates code to evaluate an expression and return the result in a register. */

/**
 * Generates assembly code for evaluating expressions such as integer, character, and string
 * literals; binary and logical operations; variable accesses and assignments; function calls; and
 * memory operations.
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

  // Generates assembly code for evaluating an expression.
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
        // Correct alignment.
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
              case LE -> { // left <= right
                Register tempReg = Register.Virtual.create();
                text.emit(OpCode.SLT, tempReg, rightReg, leftReg);
                text.emit(OpCode.XORI, resReg, tempReg, 1);
              }
              case GE -> { // left >= right
                Register tempReg = Register.Virtual.create();
                text.emit(OpCode.SLT, tempReg, leftReg, rightReg);
                text.emit(OpCode.XORI, resReg, tempReg, 1);
              }
            }
          }
          case AND -> {
            Label falseLabel = Label.create();
            Label endLabel = Label.create();
            Register leftReg = visit(b.left);
            text.emit(OpCode.BEQZ, leftReg, falseLabel);
            Register rightReg = visit(b.right);
            text.emit(OpCode.BEQZ, rightReg, falseLabel);
            text.emit(OpCode.LI, resReg, 1);
            text.emit(OpCode.J, endLabel);
            text.emit(falseLabel);
            text.emit(OpCode.LI, resReg, 0);
            text.emit(endLabel);
          }
          case OR -> {
            Label trueLabel = Label.create();
            Label endLabel = Label.create();
            Register leftReg = visit(b.left);
            text.emit(OpCode.BNEZ, leftReg, trueLabel);
            Register rightReg = visit(b.right);
            text.emit(OpCode.BNEZ, rightReg, trueLabel);
            text.emit(OpCode.LI, resReg, 0);
            text.emit(OpCode.J, endLabel);
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
        // Evaluate left-hand side address.
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(a.left);
        Register rhsReg = visit(a.right);
        Type type = a.left.type;

        // If the LHS is a variable and is promoted, do a register-to-register move.
        if (a.left instanceof VarExpr) {
          VarDecl varDecl = allocator.getVarDecl(((VarExpr) a.left).name);
          if (allocator.promotedRegisters.containsKey(varDecl)) {
            // Here we simply move the computed value into the promoted register.
            text.emit(OpCode.ADDU, addrReg, rhsReg, Register.Arch.zero);
            return rhsReg;
          }
        }

        // Otherwise, use the standard assignment code.
        if (type instanceof StructType structType) {
          int structSize = allocator.computeSize(structType);
          structSize = allocator.alignTo8(structSize);
          Register temp = Register.Virtual.create();
          Register counter = Register.Virtual.create();
          Register sizeReg = Register.Virtual.create();
          Register rhsAddr = Register.Virtual.create();
          Register destAddr = Register.Virtual.create();
          Label copyLoop = Label.create();
          Label endCopy = Label.create();

          text.emit(OpCode.LI, counter, 0);
          text.emit(OpCode.LI, sizeReg, structSize);
          text.emit(OpCode.ADDU, rhsAddr, rhsReg, Register.Arch.zero);
          text.emit(OpCode.ADDU, destAddr, addrReg, Register.Arch.zero);

          text.emit(copyLoop);
          Register tempReg = Register.Virtual.create();
          text.emit(OpCode.SLT, tempReg, counter, sizeReg);
          text.emit(OpCode.BEQZ, tempReg, endCopy);

          Register loadAddr = Register.Virtual.create();
          Register storeAddr = Register.Virtual.create();
          text.emit(OpCode.ADDU, loadAddr, rhsAddr, counter);
          text.emit(OpCode.LW, temp, loadAddr, 0);
          text.emit(OpCode.ADDU, storeAddr, destAddr, counter);
          text.emit(OpCode.SW, temp, storeAddr, 0);

          text.emit(OpCode.ADDI, counter, counter, 4);
          text.emit(OpCode.J, copyLoop);
          text.emit(endCopy);
        } else if (type instanceof ArrayType) {
          Register rhsAddr =
              new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(a.right);
          text.emit(OpCode.SW, rhsAddr, addrReg, 0);
        } else if (type.equals(BaseType.CHAR)) {
          text.emit(OpCode.SB, rhsReg, addrReg, 0);
        } else {
          text.emit(OpCode.SW, rhsReg, addrReg, 0);
        }
        return rhsReg;
      }

      case VarExpr v -> {
        VarDecl varDecl = allocator.getVarDecl(v.name);
        if (varDecl == null || varDecl.type == null) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: Variable type not found: " + v.name);
        }
        // Get the address from ExprAddrCodeGen which already checks for promotion.
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(v);
        // If the variable was promoted, addrReg already holds its value.
        if (allocator.promotedRegisters.containsKey(varDecl)) {
          return addrReg;
        }
        Type type = varDecl.type;
        if (type instanceof StructType structType) {
          int structSize = allocator.computeSize(structType);
          structSize = allocator.alignTo8(structSize);
          Register structAddr = Register.Virtual.create();
          Register counter = Register.Virtual.create();
          Register temp = Register.Virtual.create();
          Register loadAddr = Register.Virtual.create();
          Register storeAddr = Register.Virtual.create();
          Register sizeReg = Register.Virtual.create();
          Label copyLoop = Label.create();
          Label endCopy = Label.create();
          text.emit(OpCode.ADDIU, structAddr, Register.Arch.sp, -structSize);
          text.emit(OpCode.LI, counter, 0);
          text.emit(OpCode.LI, sizeReg, structSize);
          text.emit(copyLoop);
          Register cmpReg = Register.Virtual.create();
          text.emit(OpCode.SLT, cmpReg, counter, sizeReg);
          text.emit(OpCode.BEQZ, cmpReg, endCopy);
          text.emit(OpCode.ADDU, loadAddr, addrReg, counter);
          text.emit(OpCode.LW, temp, loadAddr, 0);
          text.emit(OpCode.ADDU, storeAddr, structAddr, counter);
          text.emit(OpCode.SW, temp, storeAddr, 0);
          text.emit(OpCode.ADDI, counter, counter, 4);
          text.emit(OpCode.J, copyLoop);
          text.emit(endCopy);
          return structAddr;
        } else if (type instanceof ArrayType) {
          // For array return the pointer computed by ExprAddrCodeGen.
          return addrReg;
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
        int totalStackSize = 0;
        for (Expr arg : fc.args) {
          Type argType = arg.type;
          Register tempReg = visit(arg);
          if (argType instanceof StructType) {
            int argSize = allocator.computeSize(argType);
            argSize = allocator.alignTo8(argSize);
            totalStackSize += argSize;
            Register addrReg = new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(arg);
            Register tempReg1 = Register.Virtual.create();
            for (int offset = 0; offset < argSize; offset += 4) {
              text.emit(OpCode.LW, tempReg1, addrReg, offset);
              text.emit(OpCode.SW, tempReg1, Register.Arch.sp, -totalStackSize + offset);
            }
          } else if (argType instanceof ArrayType) {
            totalStackSize += 4;
            argumentRegs.add(new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(arg));
          } else {
            argumentRegs.add(tempReg);
          }
        }
        text.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, -totalStackSize);
        for (int i = 0; i < argumentRegs.size(); i++) {
          text.emit(OpCode.SW, argumentRegs.get(i), Register.Arch.sp, i * 4);
        }
        // Call function.
        text.emit(OpCode.JAL, funcLabel);
        // Cleanup stack.
        text.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, totalStackSize);
        Register returnReg = Register.Virtual.create();
        text.emit(OpCode.ADDU, returnReg, Register.Arch.v0, Register.Arch.zero);
        return returnReg;
      }

      case ValueAtExpr va -> {
        Register addrReg = visit(va.expr);
        text.emit(OpCode.LW, resReg, addrReg, 0);
        return resReg;
      }

      case AddressOfExpr ao -> {
        return new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(ao.expr);
      }

      case TypecastExpr tc -> {
        Register castReg = visit(tc.expr);
        text.emit(OpCode.ADDU, resReg, castReg, Register.Arch.zero);
        return resReg;
      }

      case ArrayAccessExpr a -> {
        Register baseAddr =
            new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(a.array);
        if (!(a.array.type instanceof ArrayType arrayType)) {
          throw new IllegalStateException(
              "[ExprValCodeGen] ERROR: ArrayAccessExpr on non-array type.");
        }
        Type elementType = arrayType.elementType;
        int elementSize = allocator.computeSize(elementType);
        Register offsetReg = Register.Virtual.create();
        text.emit(OpCode.LI, offsetReg, 0);
        for (int i = 0; i < a.indices.size(); i++) {
          Register indexReg = visit(a.indices.get(i));
          Register strideReg = Register.Virtual.create();
          int stride = 1;
          for (int j = i + 1; j < a.indices.size(); j++) {
            stride *= arrayType.dimensions.get(j);
          }
          text.emit(OpCode.LI, strideReg, stride);
          text.emit(OpCode.MUL, indexReg, indexReg, strideReg);
          text.emit(OpCode.ADDU, offsetReg, offsetReg, indexReg);
        }
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, elementSize);
        text.emit(OpCode.MUL, offsetReg, offsetReg, sizeReg);
        Register finalAddr = Register.Virtual.create();
        text.emit(OpCode.ADDU, finalAddr, baseAddr, offsetReg);
        System.out.println("[ExprValCodeGen] Computed array element address.");
        if (elementType.equals(BaseType.CHAR)) {
          text.emit(OpCode.LBU, resReg, finalAddr, 0);
        } else {
          text.emit(OpCode.LW, resReg, finalAddr, 0);
        }
        return resReg;
      }

      case FieldAccessExpr fa -> {
        Register baseReg =
            new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(fa.structure);
        if (fa.structure.type instanceof StructType structType) {
          int offset = allocator.computeFieldOffset(structType, fa.field);
          if (fa.type.equals(BaseType.CHAR)) {
            text.emit(OpCode.LBU, resReg, baseReg, offset);
          } else {
            text.emit(OpCode.LW, resReg, baseReg, offset);
          }
          return resReg;
        }
      }

      default ->
          throw new UnsupportedOperationException(
              "[ExprValCodeGen] Unsupported expression type: " + e.getClass().getSimpleName());
    }
    return resReg;
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
