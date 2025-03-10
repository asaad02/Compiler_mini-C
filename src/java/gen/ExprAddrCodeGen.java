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

        // retrieve variable declaration
        VarDecl varDecl = allocator.getVarDecl(v.name);

        if (varDecl == null) {
          throw new IllegalStateException("[ExprAddrCodeGen] ERROR: Variable not found: " + v.name);
        }

        // an array type when needed
        if (varDecl.type instanceof ArrayType at) {
          System.out.println("[ExprAddrCodeGen] Variable is an array: " + v.name);
          v.type = at;
        } else {
          System.out.println("[ExprAddrCodeGen] Variable is NOT an array: " + v.name);
        }

        if (allocator.isGlobal(v.name)) {
          System.out.println("[ExprAddrCodeGen] Global variable: " + v.name);
          Label varLabel = Label.get(v.name);
          text.emit(OpCode.LA, addrReg, varLabel);
        } else {
          System.out.println("[ExprAddrCodeGen] Local variable: " + v.name);
          int offset = allocator.getLocalOffset(varDecl);
          text.emit(OpCode.ADDIU, addrReg, Register.Arch.fp, offset);
        }

        return addrReg;
      }

      case ArrayAccessExpr aa -> {
        System.out.println("[ExprAddrCodeGen] Resolving array access: " + aa);

        Register baseReg = visit(aa.array);
        Register indexReg = visit(aa.index);

        // ensure array type is set
        if (aa.array.type == null && aa.array instanceof VarExpr varExpr) {
          VarDecl varDecl = allocator.getVarDecl(varExpr.name);
          if (varDecl != null && varDecl.type instanceof ArrayType arrayType) {
            aa.array.type = arrayType;
          }
        }

        if (aa.array.type instanceof ArrayType arrayType) {
          System.out.println("[ExprAddrCodeGen] Array type: " + arrayType);
          int elementSize = allocator.computeSizeWithMask(arrayType.elementType);
          System.out.println("[ExprAddrCodeGen] Element size: " + elementSize);

          // correcting multiplication operation
          Register elementSizeReg = Register.Virtual.create();
          asmProg.getCurrentTextSection().emit(OpCode.LI, elementSizeReg, elementSize);
          asmProg.getCurrentTextSection().emit(OpCode.MUL, indexReg, indexReg, elementSizeReg);

          asmProg.getCurrentTextSection().emit(OpCode.ADDU, baseReg, baseReg, indexReg);
        }

        return baseReg;
      }

      case FieldAccessExpr fa -> {
        System.out.println("[ExprAddrCodeGen] Resolving field access: " + fa);

        Register baseReg = visit(fa.structure);

        // Ensure struct type is set
        if (fa.structure.type == null && fa.structure instanceof VarExpr varExpr) {
          VarDecl varDecl = allocator.getVarDecl(varExpr.name);
          if (varDecl != null && varDecl.type instanceof StructType structType) {
            fa.structure.type = structType;
          }
        }

        if (fa.structure.type instanceof StructType structType) {
          System.out.println("[ExprAddrCodeGen] Struct type: " + structType);
          int offset = computeFieldOffset(structType, fa.field);
          System.out.println("[ExprAddrCodeGen] Field offset: " + offset);

          asmProg.getCurrentTextSection().emit(OpCode.ADDIU, baseReg, baseReg, offset);
        }

        return baseReg;
      }
      case ValueAtExpr va -> {
        Register pointerReg = visit(va.expr);
        text.emit(OpCode.LW, addrReg, pointerReg, 0);
        return addrReg;
      }

      case AddressOfExpr ao -> {
        return visit(ao.expr);
      }

      case Assign a -> {
        return visit(a.left);
      }

      case SizeOfExpr sz -> {
        Register sizeReg = Register.Virtual.create();
        text.emit(OpCode.LI, sizeReg, allocator.computeSizeWithMask(sz.type));
        text.emit(OpCode.ADDU, addrReg, sizeReg, Register.Arch.zero);
        return addrReg;
      }

      case TypecastExpr tc -> addrReg = visit(tc.expr);

      case IntLiteral i -> {
        Register intReg = Register.Virtual.create();
        text.emit(OpCode.LI, intReg, i.value);
        text.emit(OpCode.ADDU, addrReg, intReg, Register.Arch.zero);
      }

      default ->
          throw new UnsupportedOperationException(
              "[ExprAddrCodeGen] Unsupported address computation: " + e.getClass().getSimpleName());
    }
    return addrReg;
  }

  /** Computes the byte offset of a struct field with proper alignment. */
  int computeFieldOffset(StructType structType, String fieldName) {
    // StructTypeDecl decl = allocator.findStructDeclaration(structType);
    StructTypeDecl structDecl = allocator.findStructDeclaration(structType);

    if (structDecl == null) {
      System.out.println(
          "[ExprAddrCodeGen] ERROR: Struct not found for field lookup: " + structType.name);
      return -1; // Return invalid offset
    }

    int offset = 0;
    for (VarDecl field : structDecl.fields) {
      int alignment = allocator.computeAlignment(field.type);
      offset = (offset + alignment - 1) & ~(alignment - 1);
      if (field.name.equals(fieldName)) {
        return offset;
      }
      offset += allocator.computeSizeWithMask(field.type);
    }

    System.out.println(
        "[ExprAddrCodeGen] ERROR: Field " + fieldName + " not found in struct " + structType.name);
    // Field not found
    return -1;
  }
}
