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

        if (varDecl.type instanceof ArrayType at) {
          System.out.println("[ExprAddrCodeGen] Variable is an array: " + v.name);
          v.type = at;
        } else {
          System.out.println("[ExprAddrCodeGen] Variable is NOT an array: " + v.name);
        }

        if (allocator.isGlobal(v.name)) {
          Label varLabel = Label.get(v.name);
          text.emit(OpCode.LA, addrReg, varLabel);
        } else {
          int offset = allocator.getLocalOffset(varDecl);
          text.emit(OpCode.ADDIU, addrReg, Register.Arch.fp, offset);
        }

        return addrReg;
      }
      case ArrayAccessExpr aa -> {
        System.out.println("[ExprAddrCodeGen] Resolving array access: " + aa);

        Register baseReg = visit(aa.array);
        Register indexReg = visit(aa.index);

        if (aa.array.type instanceof ArrayType arrayType) {
          System.out.println("[ExprAddrCodeGen] Array type: " + arrayType);
          int elementSize = allocator.computeSizeWithMask(arrayType.elementType);

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

      case AddressOfExpr ao -> {
        Register addrReg1 = visit(ao.expr);
        text.emit(OpCode.ADDU, addrReg1, addrReg, Register.Arch.zero);
        return addrReg;
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
        text.emit(OpCode.LI, addrReg, i.value);
      }

      case ValueAtExpr va -> {
        Register addrReg1 = visit(va.expr);
        text.emit(OpCode.LW, addrReg, addrReg1, 0);
        return addrReg;
      }

      default ->
          throw new UnsupportedOperationException(
              "[ExprAddrCodeGen] Unsupported address computation: " + e.getClass().getSimpleName());
    }
    return addrReg;
  }

  // Computes the byte offset of a struct field with proper alignment.
  int computeFieldOffset(StructType structType, String fieldName) {
    // StructTypeDecl decl = allocator.findStructDeclaration(structType);
    StructTypeDecl structDecl = allocator.findStructDeclaration(structType);

    int offset = 0;
    // Track max alignment for struct
    int maxAlignment = 1;

    for (VarDecl field : structDecl.fields) {
      int fieldAlignment = allocator.computeAlignment(field.type);
      // align field start
      offset = (offset + fieldAlignment - 1) & ~(fieldAlignment - 1);

      if (field.name.equals(fieldName)) {
        return offset;
      }

      offset += allocator.computeSizeWithMask(field.type);
      maxAlignment = Math.max(maxAlignment, fieldAlignment);
    }

    // ensure struct size is a multiple of max alignment
    offset = (offset + maxAlignment - 1) & ~(maxAlignment - 1);
    return offset;
  }
}
