package gen;

import ast.*;
import gen.asm.*;
import java.util.List;

/** Generates code to calculate the address of an expression and return the result in a register. */

/**
 * Generates code to compute memory addresses for expressions and Variables global and local Array
 * elements - Struct fields.
 */
public class ExprAddrCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;
  private final List<String> definedFunctions;
  private final String currentClass;

  public ExprAddrCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, List<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
    this.currentClass = null;
  }

  public ExprAddrCodeGen(
      AssemblyProgram asmProg,
      MemAllocCodeGen allocator,
      List<String> definedFunctions,
      String currentClass) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
    this.currentClass = currentClass;
  }

  /** Computes the address of an expression. */
  public Register visit(Expr e) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    Register addrReg = Register.Virtual.create();

    switch (e) {
      case VarExpr v -> {
        System.out.println("[ExprAddrCodeGen] Resolving variable address: " + v.name);
        // try locals or globals
        VarDecl varDecl = null;
        try {
          varDecl = allocator.getVarDecl(v.name);
        } catch (IllegalStateException ise) {
          // not found as var try field fallback
        }

        if (varDecl != null) {
          // local/global logic
          if (varDecl.type instanceof ArrayType at) {
            System.out.println("[ExprAddrCodeGen] Variable is an array: " + v.name);
            v.type = at;
          } else {
            System.out.println("[ExprAddrCodeGen] Variable is NOT an array: " + v.name);
          }

          int scopeLevel = allocator.getScopeLevel(v.name);
          System.out.printf(
              "[ExprAddrCodeGen] Variable '%s' found at scope level: %d\n", v.name, scopeLevel);

          if (scopeLevel >= 0) {
            int offset = allocator.getLocalOffset(varDecl);
            System.out.printf(
                "[ExprAddrCodeGen] Using local variable '%s' at offset: %d\n", v.name, offset);
            text.emit(OpCode.ADDIU, addrReg, Register.Arch.fp, offset);
            if (varDecl.type instanceof ArrayType && Math.abs(offset) <= 16) {
              Register tmp = Register.Virtual.create();
              text.emit(OpCode.LW, tmp, addrReg, 0);
              return tmp;
            }
          } else if (allocator.isGlobal(v.name)) {
            System.out.println("[ExprAddrCodeGen] Accessing global variable: " + v.name);
            text.emit(OpCode.LA, addrReg, Label.get(v.name));
          } else {
            throw new IllegalStateException(
                "[ExprAddrCodeGen] ERROR unexpected lookup for: " + v.name);
          }

          return addrReg;
        }

        // class field
        if (currentClass != null
            && CodeGenContext.getClassFieldOffsets(currentClass).containsKey(v.name)) {
          System.out.println("[ExprAddrCodeGen] Falling back to field: " + v.name);
          // 'this' pointer in $a0 and field offset = 4 + base
          // text.emit(OpCode.ADDU, addrReg, Register.Arch.a0, Register.Arch.zero);
          text.emit(OpCode.ADDU, addrReg, Register.Arch.s0, Register.Arch.zero);
          int fieldOff = CodeGenContext.getClassFieldOffsets(currentClass).get(v.name);
          text.emit(OpCode.ADDIU, addrReg, addrReg, 4 + fieldOff);
          return addrReg;
        }

        // neither var nor field
        throw new IllegalStateException(
            "[ExprAddrCodeGen] ERROR: Variable or field not found: " + v.name);
      }

      case ArrayAccessExpr a -> {
        Register baseAddr = visit(a.array);
        Type arrayType = a.array.type;

        if (!(arrayType instanceof ArrayType at)) {
          throw new IllegalStateException("[ExprAddrCodeGen] ERROR: ArrayAccessExpr on non-array.");
        }

        Register offsetReg = Register.Virtual.create();
        text.emit(OpCode.LI, offsetReg, 0);

        int stride = 1;
        for (int i = a.indices.size() - 1; i >= 0; i--) {
          ExprValCodeGen valGen = new ExprValCodeGen(asmProg, allocator, definedFunctions);
          Register indexReg = valGen.visit(a.indices.get(i));

          Register strideReg = Register.Virtual.create();
          text.emit(OpCode.LI, strideReg, stride);

          Register tempReg = Register.Virtual.create();
          text.emit(OpCode.MUL, tempReg, indexReg, strideReg);
          text.emit(OpCode.ADDU, offsetReg, offsetReg, tempReg);

          int prevStride = stride;
          stride *= at.dimensions.get(i);
          System.out.printf(
              "[ExprAddrCodeGen] Computed offset for arr[%d] at index %s: %s\n",
              i, indexReg.toString(), offsetReg.toString());

          System.out.printf(
              "[ExprAddrCodeGen] Stride used for arr[%d] at index %s: %d (Prev: %d)\n",
              i, indexReg.toString(), prevStride, stride);
        }

        int elementSize = allocator.computeSize(at.elementType);
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, elementSize);
        text.emit(OpCode.MUL, offsetReg, offsetReg, sizeReg);

        Register finalAddr = Register.Virtual.create();
        text.emit(OpCode.ADDU, finalAddr, baseAddr, offsetReg);

        System.out.printf(
            "[ExprAddrCodeGen] Computed final address for arr at offset %s\n",
            offsetReg.toString());

        return finalAddr;
      }

      case FieldAccessExpr fa -> {
        Register baseReg = visit(fa.structure);

        // Handle class field access dynamic object
        if (fa.structure.type instanceof ClassType ct) {
          // compute address of the variable holding the object pointer
          Register varAddr = visit(fa.structure);
          // Load the object pointer from that variable
          Register objPtr = Register.Virtual.create();
          text.emit(OpCode.LW, objPtr, varAddr, 0);
          // Compute field offset after the 4 byte vptr
          int fieldOff = CodeGenContext.getClassFieldOffsets(ct.name).get(fa.field);
          // addrReg = objPtr + 4 + fieldOff
          text.emit(OpCode.ADDIU, addrReg, objPtr, 4 + fieldOff);
          return addrReg;
        }

        if (!(fa.structure.type instanceof StructType structType)) {
          throw new IllegalStateException(
              "[ExprAddrCodeGen] ERROR: Field access on non-struct/type.");
        }

        int offset = allocator.computeFieldOffset(structType, fa.field);
        offset = allocator.alignTo(offset, 4); // field alignment

        System.out.println(
            "[ExprAddrCodeGen] Resolving field access: " + fa.field + " at offset " + offset);

        text.emit(OpCode.ADDIU, addrReg, baseReg, offset);

        return addrReg;
      }

      case AddressOfExpr ao -> {
        System.out.println("[ExprAddrCodeGen] Resolving address-of expression");
        Register exprReg = visit(ao.expr);
        text.emit(OpCode.ADDU, addrReg, exprReg, Register.Arch.zero);
        return addrReg;
      }

      case Assign a -> {
        System.out.println("[ExprAddrCodeGen] Resolving assignment address");
        return visit(a.left);
      }

      case SizeOfExpr sz -> {
        System.out.println("[ExprAddrCodeGen] Resolving sizeof expression");
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, allocator.computeSize(sz.type));
        text.emit(OpCode.ADDU, addrReg, sizeReg, Register.Arch.zero);
        return addrReg;
      }

      case TypecastExpr tc -> {
        System.out.println("[ExprAddrCodeGen] Resolving typecast expression");
        addrReg = visit(tc.expr);
        return addrReg;
      }

      case IntLiteral i -> {
        System.out.println("[ExprAddrCodeGen] Resolving integer literal: " + i.value);
        text.emit(OpCode.LI, addrReg, i.value);
        return addrReg;
      }

      case ValueAtExpr va -> {
        System.out.println("[ExprAddrCodeGen] Resolving value-at (dereferencing) expression");
        Register exprReg = visit(va.expr);
        text.emit(OpCode.LW, addrReg, exprReg, 0);
        return addrReg;
      }

      default ->
          throw new UnsupportedOperationException(
              "[ExprAddrCodeGen] Unsupported address computation: " + e.getClass().getSimpleName());
    }
  }
}
