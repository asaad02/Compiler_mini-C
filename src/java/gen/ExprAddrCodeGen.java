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

  public ExprAddrCodeGen(
      AssemblyProgram asmProg, MemAllocCodeGen allocator, List<String> definedFunctions) {
    this.asmProg = asmProg;
    this.allocator = allocator;
    this.definedFunctions = definedFunctions;
  }

  /** Computes the address of an expression. */
  public Register visit(Expr e) {
    AssemblyProgram.TextSection text = asmProg.getCurrentTextSection();
    Register addrReg = Register.Virtual.create();

    switch (e) {
      case VarExpr v -> {
        System.out.println("[ExprAddrCodeGen] Resolving variable address: " + v.name);

        // Retrieve the  variable declaration local or global
        VarDecl varDecl = allocator.getVarDecl(v.name);

        if (varDecl == null) {
          throw new IllegalStateException("[ExprAddrCodeGen] ERROR: Variable not found: " + v.name);
        }

        // Check if variable is an array
        if (varDecl.type instanceof ArrayType at) {
          System.out.println("[ExprAddrCodeGen] Variable is an array: " + v.name);
          v.type = at;
        } else {
          System.out.println("[ExprAddrCodeGen] Variable is NOT an array: " + v.name);
        }

        // Determine the correct scope level
        int scopeLevel = allocator.getScopeLevel(v.name);
        System.out.println(
            "[ExprAddrCodeGen] Variable '" + v.name + "' found at scope level: " + scopeLevel);

        // If the variable is local, use the local offset
        if (scopeLevel >= 0) {
          int offset = allocator.getLocalOffset(varDecl);
          System.out.printf(
              "[ExprAddrCodeGen] Using local variable '%s' at offset: %d\n", v.name, offset);
          text.emit(OpCode.ADDIU, addrReg, Register.Arch.fp, offset);
        } else if (allocator.isGlobal(v.name)) {
          // If the variable is global, load the global address
          System.out.println("[ExprAddrCodeGen] Accessing global variable: " + v.name);
          text.emit(OpCode.LA, addrReg, Label.get(v.name));
        } else {
          throw new IllegalStateException(
              "[ExprAddrCodeGen] ERROR: Variable '" + v.name + "' not found in any scope!");
        }

        return addrReg;
      }

      case ArrayAccessExpr a -> {
        Register baseAddr = visit(a.array);
        Type arrayType = a.array.type;

        if (!(arrayType instanceof ArrayType at)) {
          throw new IllegalStateException("[ExprAddrCodeGen] ERROR: ArrayAccessExpr on non-array.");
        }

        Register offsetReg = Register.Virtual.create();
        text.emit(OpCode.LI, offsetReg, 0);

        for (int i = 0; i < a.indices.size(); i++) {
          ExprValCodeGen valGen = new ExprValCodeGen(asmProg, allocator, definedFunctions);
          Register indexReg = valGen.visit(a.indices.get(i));

          int dimSize = at.dimensions.get(i);

          Register sizeReg = Register.Virtual.create();
          text.emit(OpCode.LI, sizeReg, dimSize);

          Register tempReg = Register.Virtual.create();
          text.emit(OpCode.MUL, tempReg, indexReg, sizeReg);
          text.emit(OpCode.ADDU, offsetReg, offsetReg, tempReg);
        }

        int elementSize = allocator.computeSize(at.elementType);
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, elementSize);
        text.emit(OpCode.MUL, offsetReg, offsetReg, sizeReg);

        Register finalAddr = Register.Virtual.create();
        text.emit(OpCode.ADDU, finalAddr, baseAddr, offsetReg);
        return finalAddr;
      }

      case FieldAccessExpr fa -> {
        Register baseReg = visit(fa.structure);

        if (!(fa.structure.type instanceof StructType structType)) {
          throw new IllegalStateException(
              "[ExprAddrCodeGen] ERROR: Field access on non-struct type.");
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
