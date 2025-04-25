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
  private final String currentClass;

  public ExprValCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, List<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
    this.currentClass = null;
  }

  public ExprValCodeGen(
      AssemblyProgram asmProg,
      MemAllocCodeGen allocator,
      List<String> definedFunctions,
      String currentClass) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
    this.currentClass = currentClass;
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
        Register addrReg = new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(a.left);
        Register rhsReg = visit(a.right);
        Type type = a.left.type;

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
        // locals or globals
        VarDecl varDecl = null;
        Register addrReg = null;

        try {
          varDecl = allocator.getVarDecl(v.name);
          addrReg =
              new ExprAddrCodeGen(asmProg, allocator, definedFunctions, currentClass).visit(v);
        } catch (IllegalStateException ei) {
          // fall  to field check
        }

        if (varDecl != null) {
          Type t = varDecl.type;
          if (t instanceof StructType st) {
            int sz = allocator.computeSize(st);
            sz = allocator.alignTo8(sz);

            Register base = addrReg;
            Register result = Register.Virtual.create();
            Register cnt = Register.Virtual.create();
            Register tmp = Register.Virtual.create();
            Register lwAddr = Register.Virtual.create();
            Register stAddr = Register.Virtual.create();
            Register szReg = Register.Virtual.create();
            Label loopL = Label.create();
            Label endL = Label.create();

            text.emit(OpCode.ADDIU, result, Register.Arch.sp, -sz);
            text.emit(OpCode.LI, cnt, 0);
            text.emit(OpCode.LI, szReg, sz);

            text.emit(loopL);
            text.emit(OpCode.SLT, tmp, cnt, szReg);
            text.emit(OpCode.BEQZ, tmp, endL);

            text.emit(OpCode.ADDU, lwAddr, base, cnt);
            text.emit(OpCode.LW, tmp, lwAddr, 0);
            text.emit(OpCode.ADDU, stAddr, result, cnt);
            text.emit(OpCode.SW, tmp, stAddr, 0);

            text.emit(OpCode.ADDI, cnt, cnt, 4);
            text.emit(OpCode.J, loopL);
            text.emit(endL);

            return result;
          } else if (t instanceof ArrayType) {
            return addrReg;
          } else {
            // primitive
            if (t.equals(BaseType.CHAR)) {
              text.emit(OpCode.LBU, resReg, addrReg, 0);
            } else {
              text.emit(OpCode.LW, resReg, addrReg, 0);
            }
            return resReg;
          }
        }

        // fallback class field
        if (currentClass != null
            && CodeGenContext.getClassFieldOffsets(currentClass).containsKey(v.name)) {
          System.out.println("[ExprValCodeGen] Falling back to field load: " + v.name);
          int off = CodeGenContext.getClassFieldOffsets(currentClass).get(v.name);
          Register fldAddr = Register.Virtual.create();
          asmProg.getCurrentTextSection().emit(OpCode.ADDIU, fldAddr, Register.Arch.s0, 4 + off);
          text.emit(OpCode.LW, resReg, fldAddr, 0);
          return resReg;
        }

        // neither var nor field
        throw new IllegalStateException(
            "[ExprValCodeGen] ERROR: Variable or field not found: " + v.name);
      }

      // Instance method calls dynamic dispatch

      case InstanceFunCallExpr x -> {

        // Evaluate the receiver and put it into $a0
        Register objReg = visit(x.target);

        text.emit(OpCode.ADDU, Register.Arch.a0, objReg, Register.Arch.zero);

        // Evaluate up to three other args into $a1–$a3
        for (int i = 0; i < x.call.args.size() && i < 3; i++) {
          Register argReg = visit(x.call.args.get(i));
          text.emit(OpCode.ADDU, getArgReg(i + 1), argReg, Register.Arch.zero);
        }

        // Look up vtable pointer & load method address
        Register vptr = Register.Virtual.create();
        text.emit(OpCode.LW, vptr, objReg, 0);
        String className = ((ClassType) x.target.type).name;
        int idx = CodeGenContext.getMethodIndex(className, x.call.name);
        Register offReg = Register.Virtual.create();
        text.emit(OpCode.LI, offReg, idx * 4);
        Register slotAddr = Register.Virtual.create();
        text.emit(OpCode.ADDU, slotAddr, vptr, offReg);
        Register target = Register.Virtual.create();
        text.emit(OpCode.LW, target, slotAddr, 0);

        // Call it
        text.emit(OpCode.JALR, target);

        // Grab return value
        Register result = Register.Virtual.create();
        text.emit(OpCode.ADDU, result, Register.Arch.v0, Register.Arch.zero);
        return result;
      }

      case FunCallExpr fc -> {
        // Dynamic dispatch for methods of the current class called
        if (currentClass != null && CodeGenContext.hasVirtualMethod(currentClass, fc.name)) {
          for (int i = 0; i < fc.args.size() && i < 3; i++) {
            Register argReg = visit(fc.args.get(i));
            text.emit(OpCode.ADDU, getArgReg(i + 1), argReg, Register.Arch.zero);
          }
          // Look up vtable pointer  and will load method address
          Register vptr = Register.Virtual.create();
          text.emit(OpCode.LW, vptr, Register.Arch.a0, 0);
          int idx = CodeGenContext.getMethodIndex(currentClass, fc.name);
          Register offReg = Register.Virtual.create();
          text.emit(OpCode.LI, offReg, idx * 4);
          Register slotAddr = Register.Virtual.create();
          text.emit(OpCode.ADDU, slotAddr, vptr, offReg);
          Register target = Register.Virtual.create();
          text.emit(OpCode.LW, target, slotAddr, 0);
          // Call it
          text.emit(OpCode.JALR, target);
          // Grab return value
          text.emit(OpCode.ADDU, resReg, Register.Arch.v0, Register.Arch.zero);
          return resReg;
        }
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

        // Dynamic dispatch for class methods
        if (!fc.args.isEmpty()
            && fc.args.get(0).type instanceof ClassType ct
            && CodeGenContext.hasVirtualMethod(ct.name, fc.name)) {
          Register obj = argumentRegs.remove(0);
          Register vptr = Register.Virtual.create();
          text.emit(OpCode.LW, vptr, obj, 0);
          int idx = CodeGenContext.getMethodIndex(ct.name, fc.name);
          text.emit(OpCode.LI, Register.Arch.t1, idx * 4);
          text.emit(OpCode.ADDU, Register.Arch.t1, vptr, Register.Arch.t1);
          Register target = Register.Virtual.create();
          text.emit(OpCode.LW, target, Register.Arch.t1, 0);
          text.emit(OpCode.JALR, target);
        } else {
          text.emit(OpCode.JAL, funcLabel);
        }

        // Cleanup Stack
        text.emit(OpCode.ADDIU, Register.Arch.sp, Register.Arch.sp, totalStackSize);

        // Retrieve Return Value from `$v0`
        Register returnReg = Register.Virtual.create();
        text.emit(OpCode.ADDU, returnReg, Register.Arch.v0, Register.Arch.zero);

        // Return Register

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
        Register elemAddr =
            new ExprAddrCodeGen(asmProg, allocator, definedFunctions, currentClass).visit(a);
        // Load the actual element
        if (a.array.type instanceof ArrayType at && at.elementType.equals(BaseType.CHAR)) {
          text.emit(OpCode.LBU, resReg, elemAddr, 0);
        } else {
          text.emit(OpCode.LW, resReg, elemAddr, 0);
        }
        return resReg;
      }

      case FieldAccessExpr fa -> {
        // Class field access load field value from heap object
        if (fa.structure.type instanceof ClassType ct) {
          // get address of the field
          Register addr =
              new ExprAddrCodeGen(asmProg, allocator, definedFunctions, currentClass).visit(fa);
          // load the value
          text.emit(OpCode.LW, resReg, addr, 0);
          return resReg;
        }
        Register baseReg =
            new ExprAddrCodeGen(asmProg, allocator, definedFunctions).visit(fa.structure);
        if (fa.structure.type instanceof StructType structType) {
          int offset = allocator.computeFieldOffset(structType, fa.field);

          if (fa.type.equals(BaseType.CHAR)) {
            text.emit(OpCode.LBU, resReg, baseReg, offset); // Load byte for char fields
          } else {
            text.emit(OpCode.LW, resReg, baseReg, offset); // Load word for other types
          }
          return resReg;
        }
      }
      case NewInstance ne -> {
        // allocate object
        int size = allocator.computeSize(ne.type);
        // Syscall mcmalloc(size)
        text.emit(OpCode.LI, Register.Arch.a0, size);
        text.emit(OpCode.LI, Register.Arch.v0, 9);
        text.emit(OpCode.SYSCALL);
        // v0 now has object ptr
        Register objPtr = Register.Virtual.create();
        text.emit(OpCode.ADDU, objPtr, Register.Arch.v0, Register.Arch.zero);
        // store vtable pointer at offset 0
        Label vtLabel = Label.get("vtable_" + ((ClassType) ne.type).name);
        text.emit(OpCode.LA, Register.Arch.t0, vtLabel);
        text.emit(OpCode.SW, Register.Arch.t0, objPtr, 0);
        return objPtr;
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
