package gen;

import ast.*;
import gen.asm.*;

/** Generates code to calculate the address of an expression and return the result in a register. */

/**
 * Generates code to compute memory addresses for expressions and Variables (global/local) and Array
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
        System.out.println("[ExprAddrCodeGen] Resolving array element address: " + aa);

        // retrieve base address of the array
        Register baseReg = visit(aa.array);

        // ensure the variable has an array type
        VarDecl arrayDecl = allocator.getVarDecl(((VarExpr) aa.array).name);
        if (!(arrayDecl.type instanceof ArrayType at)) {
          throw new IllegalStateException(
              "[ExprAddrCodeGen] ERROR: Variable " + arrayDecl.name + " is not an array!");
        }

        int elemSize = allocator.computeSizeWithMask(at.elementType);

        // compute index offset
        Register indexReg = new ExprValCodeGen(asmProg, allocator).visit(aa.index);
        Register offsetReg = Register.Virtual.create();
        text.emit(OpCode.LI, offsetReg, elemSize);
        text.emit(OpCode.MUL, offsetReg, indexReg, offsetReg);
        text.emit(OpCode.ADD, addrReg, baseReg, offsetReg);

        return addrReg;
      }

      case FieldAccessExpr fa -> {
        System.out.println("[ExprAddrCodeGen] Resolving struct field address: " + fa.field);

        // base struct variable
        Register baseReg = visit(fa.structure);

        // retrieve struct type from variable declaration
        VarDecl structVar = allocator.getVarDecl(((VarExpr) fa.structure).name);

        if (!(structVar.type instanceof StructType structType)) {
          throw new IllegalStateException(
              "[ExprAddrCodeGen] ERROR: Variable " + structVar.name + " is not a struct!");
        }

        // compute field offset correctly
        int fieldOffset = computeFieldOffset(structType, fa.field);

        // add offset to base address
        text.emit(OpCode.ADDIU, addrReg, baseReg, fieldOffset);

        return addrReg;
      }

      default -> {
        throw new UnsupportedOperationException(
            "[ExprAddrCodeGen] Unsupported address computation: " + e.getClass().getSimpleName());
      }
    }
  }

  /** Computes the byte offset of a struct field with proper alignment. */
  int computeFieldOffset(StructType structType, String fieldName) {
    StructTypeDecl decl = allocator.findStructDeclaration(structType);

    if (decl == null) {
      System.out.println(
          "[ExprAddrCodeGen] ERROR: Struct not found for field lookup: " + structType.name);
      return -1; // Return invalid offset
    }

    int offset = 0;
    for (VarDecl field : decl.fields) {
      int alignment = allocator.computeAlignment(field.type);
      offset = (offset + alignment - 1) & ~(alignment - 1);
      if (field.name.equals(fieldName)) {
        return offset;
      }
      offset += allocator.computeSizeWithMask(field.type);
    }

    System.out.println(
        "[ExprAddrCodeGen] ERROR: Field " + fieldName + " not found in struct " + structType.name);
    return -1; // Field not found
  }
}
