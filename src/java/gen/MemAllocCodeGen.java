package gen;

import ast.*;
import gen.asm.*;
import java.util.HashMap;
import java.util.Map;

/* This allocator should deal with all global and local variable declarations. */
/**
 * Handles memory allocation for global and local variables: - Globals: Allocated in `.data` section
 * with proper alignment - Locals: Allocated on stack relative to frame pointer ($fp) - Computes
 * struct layouts with field alignment and padding ensures correct memory alignment for arrays and
 * structs
 */
public class MemAllocCodeGen extends CodeGen {
  private final Map<VarDecl, Integer> localVarOffsets = new HashMap<>();
  private final Map<FunDef, Integer> frameSizes = new HashMap<>();
  private final Map<String, VarDecl> globalVars = new HashMap<>();
  private final Map<String, VarDecl> localVars = new HashMap<>();
  private final Map<String, Integer> structSizes = new HashMap<>();
  private final Map<String, StructTypeDecl> structDeclarations = new HashMap<>();
  private final Map<String, StructTypeDecl> programStructs = new HashMap<>();

  private final AssemblyProgram.Section dataSection;
  private boolean global = true;
  private int fpOffset = 0;

  public MemAllocCodeGen(AssemblyProgram asmProg) {
    this.asmProg = asmProg;
    this.dataSection = asmProg.dataSection;
  }

  void visit(ASTNode n) {
    switch (n) {
      case Program p -> {
        for (Decl d : p.decls) {
          if (d instanceof StructTypeDecl std) {
            structDeclarations.put(std.structType.name, std);
            System.out.println("[MemAlloc] Registered struct: " + std.structType.name);
          }
        }
        p.decls.forEach(this::visit);
      }
      case FunDef fd -> allocateLocalVariables(fd);
      case VarDecl vd -> allocateVariable(vd);
      default -> {}
    }
  }

  /** Computes the correct size of a struct, ensuring proper alignment. */
  int computeStructSize(StructTypeDecl structDecl) {
    int offset = 0;
    int maxAlignment = 1;

    for (VarDecl field : structDecl.fields) {
      System.out.println("[MemAlloc] Field: " + field.name + " Type: " + field.type);
      int fieldAlignment = computeAlignment(field.type);

      // ensure the field is correctly aligned
      offset = (offset + fieldAlignment - 1) & ~(fieldAlignment - 1);
      offset += computeSizeWithMask(field.type);

      // keep track of max alignment needed
      maxAlignment = Math.max(maxAlignment, fieldAlignment);
    }

    // ensure total struct size is a multiple of max alignment
    return (offset + maxAlignment - 1) & ~(maxAlignment - 1);
  }

  /** Computes the memory size for a given type. */
  int computeSizeWithMask(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 1;
      case ArrayType at -> computeSizeWithMask(at.elementType) * at.size;
      case StructType st -> structSizes.getOrDefault(st.name, 0);
      case PointerType p -> 4;
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  /** Computes the alignment requirements for a given type. */
  int computeAlignment(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 1;
      case ArrayType at -> computeAlignment(at.elementType);
      case StructType st -> 4; // Default struct alignment
      case PointerType p -> 4;
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  /** Allocates memory for a variable, ensuring correct alignment. */
  private void allocateVariable(VarDecl vd) {
    System.out.println("[MemAlloc] Allocating variable: " + vd.name);

    if (global) {
      globalVars.put(vd.name, vd);
      int size = computeSizeWithMask(vd.type);

      // ensure proper alignment before allocating space
      dataSection.emit(new Directive("align 2"));
      dataSection.emit(Label.get(vd.name));
      dataSection.emit(new Directive("space " + size));
    } else {
      localVars.put(vd.name, vd);
      int size = computeSizeWithMask(vd.type);
      int alignment = computeAlignment(vd.type);

      // Align offset properly
      fpOffset -= size;
      // Ensure alignment
      fpOffset = (fpOffset - alignment) & ~(alignment - 1);

      localVarOffsets.put(vd, fpOffset);

      // store struct type mapping
      if (vd.type instanceof StructType st) {
        System.out.println("[MemAlloc] Storing struct type for variable: " + vd.name);
        programStructs.put(st.name, findStructDeclaration(st));
      }

      System.out.println(
          "[MemAllocCodeGen] Allocated local variable " + vd.name + " at offset " + fpOffset);
    }
  }

  /** Allocates local variables within a function, ensuring proper stack alignment. */
  private void allocateLocalVariables(FunDef fd) {
    System.out.println("[MemAlloc] Allocating locals for function: " + fd.name);
    global = false;
    fpOffset = 0;
    fd.params.forEach(this::allocateVariable);
    fd.block.vds.forEach(this::allocateVariable);
    global = true;

    // ensure the stack frame is aligned to 16 bytes
    int frameSize = (-fpOffset + 15) & ~15;
    frameSizes.put(fd, frameSize);
  }

  /** finds the struct declaration corresponding to a given struct type. */
  StructTypeDecl findStructDeclaration(StructType structType) {
    if (structType == null) {
      System.out.println("[MemAlloc] Warning: structType is null during lookup!");
      return null;
    }

    StructTypeDecl decl = structDeclarations.get(structType.name);

    if (decl == null) {
      System.out.println("[MemAlloc] ERROR: Struct not found: " + structType.name);
    }
    return decl;
  }

  public int getLocalOffset(VarDecl vd) {
    return localVarOffsets.getOrDefault(vd, 0);
  }

  public int getFrameSize(FunDef fd) {
    return frameSizes.getOrDefault(fd, 0);
  }

  public VarDecl getVarDecl(String name) {
    return localVars.getOrDefault(name, globalVars.get(name));
  }

  public boolean isGlobal(String varName) {
    return globalVars.containsKey(varName);
  }

  /** Computes the offset of a field within a struct. */
  int computeFieldOffset(StructType structType, String fieldName) {
    StructTypeDecl decl = findStructDeclaration(structType);

    if (decl == null) {
      throw new IllegalStateException(
          "[MemAlloc] ERROR: Struct not found for field lookup: " + structType.name);
    }

    int offset = 0;
    for (VarDecl field : decl.fields) {
      int fieldAlignment = computeAlignment(field.type);
      // Align fields properly
      offset = (offset + fieldAlignment - 1) & ~(fieldAlignment - 1);

      if (field.name.equals(fieldName)) {
        return offset;
      }
      offset += computeSizeWithMask(field.type);
    }

    throw new IllegalStateException(
        "[MemAlloc] ERROR: Field " + fieldName + " not found in struct " + structType.name);
  }
}
