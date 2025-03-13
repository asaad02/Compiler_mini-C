package gen;

import ast.*;
import gen.asm.*;

/** Generates code to calculate the address of an expression and return the result in a register. */

/**
 * Generates code to compute memory addresses for expressions and Variables global and local Array
 * elements - Struct fields.
 */
public class ExprAddrCodeGen extends CodeGen {
  private final MemAllocCodeGen allocator;

  public ExprAddrCodeGen(AssemblyProgram asmProg, MemAllocCodeGen allocator) {
    this.asmProg = asmProg;
    this.allocator = allocator;
  }

  /** Computes the address of an expression. */
  public Register visit(Expr e) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    Register addrReg = Register.Virtual.create();

    switch (e) {
      case VarExpr v -> {
        System.out.println("[ExprAddrCodeGen] Resolving variable address: " + v.name);
        VarDecl varDecl = allocator.getVarDecl(v.name);

        if (varDecl == null) {
          throw new IllegalStateException("[ExprAddrCodeGen] ERROR: Variable not found: " + v.name);
        }

        if (varDecl.type instanceof ArrayType at) {
          System.out.println("[ExprAddrCodeGen] Variable is an array: " + v.name);
          v.type = at;
        } else {
          System.out.println("[ExprAddrCodeGen] Variable is NOT an array: " + v.name);
        }

        // Get the correct scope level for the variable
        int scopeLevel = allocator.getScopeLevel(v.name);
        System.out.println(
            "[ExprAddrCodeGen] Variable '" + v.name + "' found at scope level: " + scopeLevel);

        if (scopeLevel >= 0) {
          // Retrieve the correct offset for the local variable
          int offset = allocator.getLocalOffset(varDecl, scopeLevel);
          System.out.println("[ExprAddrCodeGen] Using local variable at offset: " + offset);
          text.emit(OpCode.ADDIU, addrReg, Register.Arch.fp, offset);
        } else {
          // Access the global variable if no local variable is found
          System.out.println("[ExprAddrCodeGen] Accessing global variable: " + v.name);
          text.emit(OpCode.LA, addrReg, Label.get(v.name));
        }

        return addrReg;
      }

      case ArrayAccessExpr ae -> {
        Register baseReg = new ExprAddrCodeGen(asmProg, allocator).visit(ae.array);
        Register indexReg = visit(ae.index);
        // Get the element type of the array
        ArrayType arrayType = (ArrayType) ae.array.type;
        Type elementType = arrayType.elementType;
        int elementSize = allocator.computeSize(elementType);

        // Multiply index by the element's actual size
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, elementSize);
        text.emit(OpCode.MUL, indexReg, indexReg, sizeReg);

        text.emit(OpCode.ADDU, addrReg, baseReg, indexReg);

        return addrReg;
      }

      case FieldAccessExpr fa -> {
        Register baseReg = visit(fa.structure);
        int offset = allocator.computeFieldOffset((StructType) fa.structure.type, fa.field);

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
