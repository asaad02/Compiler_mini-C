package gen;

import ast.*;
import gen.asm.*;
import java.util.*;

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
  private final Stack<Map<String, VarDecl>> scopeStack = new Stack<>();
  private final Map<String, Integer> structSizes = new HashMap<>();
  private final Map<String, StructTypeDecl> structDeclarations = new HashMap<>();

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
      case FunDef fd -> allocateFunction(fd);
      case VarDecl vd -> allocateVariable(vd);
      default -> {}
    }
  }

  private void allocateFunction(FunDef fd) {
    System.out.println("[MemAlloc] Allocating locals for function: " + fd.name);
    fpOffset = 0;

    enterScope(); // Parameters scope
    for (VarDecl param : fd.params) {
      System.out.println("[MemAlloc] Allocating parameter: " + param.name);

      // Ensure struct parameters have their struct type set
      if (param.type instanceof StructType structType) {
        StructTypeDecl structDecl = findStructDeclaration(structType.name);
        if (structDecl == null) {
          throw new IllegalStateException(
              "[MemAlloc] ERROR: Struct type not declared for parameter: " + param.name);
        }
        param.type = structType; // Assign correct type
      }

      allocateVariable(param);
    }

    enterScope(); // Local variables scope
    for (VarDecl localVar : fd.block.vds) {
      System.out.println("[MemAlloc] Allocating local variable: " + localVar.name);
      allocateVariable(localVar);
    }

    int frameSize = (-fpOffset + 15) & ~15;
    frameSizes.put(fd, frameSize);
  }

  void allocateVariable(VarDecl vd) {
    System.out.println("[MemAlloc] Allocating variable: " + vd.name);

    // Global variable case
    if (scopeStack.isEmpty()) {
      if (globalVars.containsKey(vd.name)) {
        throw new IllegalStateException(
            "[MemAlloc] ERROR: Global variable redeclaration: " + vd.name);
      }
      globalVars.put(vd.name, vd);
      int size = computeSizeWithMask(vd.type);
      dataSection.emit(new Directive("align 2")); // Ensure global alignment
      dataSection.emit(Label.get(vd.name));
      dataSection.emit(new Directive("space " + size));
      return;
    }

    // Local variable case
    Map<String, VarDecl> currentScope = scopeStack.peek();
    if (currentScope.containsKey(vd.name)) {
      throw new IllegalStateException("[MemAlloc] ERROR: Local variable redeclaration: " + vd.name);
    }

    // Add to current scope
    currentScope.put(vd.name, vd);

    // Subtract size first
    int size = computeSizeWithMask(vd.type);
    fpOffset -= size;

    // Ensure proper alignment
    int alignment = computeAlignment(vd.type);
    fpOffset = fpOffset & ~(alignment - 1);

    localVarOffsets.put(vd, fpOffset);
    System.out.println("[MemAlloc] Allocated " + vd.name + " at offset " + fpOffset);
  }

  public VarDecl getVarDecl(String name) {
    // Check function parameters first
    if (!scopeStack.isEmpty()) {
      for (int i = scopeStack.size() - 1; i >= 0; i--) {
        if (scopeStack.get(i).containsKey(name)) {
          System.out.println("[MemAlloc] Found parameter: " + name);
          return scopeStack.get(i).get(name);
        }
      }
    }
    // Finally check global variables
    return globalVars.get(name);
  }

  public void enterScope() {
    scopeStack.push(new HashMap<>());
  }

  public void exitScope() {
    if (!scopeStack.isEmpty()) {
      scopeStack.pop();
    }
  }

  public boolean isGlobal(String name) {
    return globalVars.containsKey(name);
  }

  /** Computes the correct size of a struct, ensuring proper alignment. */
  int computeStructSize(StructTypeDecl structDecl) {
    if (structSizes.containsKey(structDecl.structType.name)) {
      return structSizes.get(structDecl.structType.name);
    }

    int offset = 0;
    int maxAlignment = 1;

    for (VarDecl field : structDecl.fields) {
      int fieldAlignment = computeAlignment(field.type);
      offset = (offset + fieldAlignment - 1) & ~(fieldAlignment - 1);
      offset += computeSizeWithMask(field.type);
      maxAlignment = Math.max(maxAlignment, fieldAlignment);
    }

    offset = (offset + maxAlignment - 1) & ~(maxAlignment - 1);
    structSizes.put(structDecl.structType.name, offset);
    return offset;
  }

  /** Computes the memory size for a given type. */
  int computeSizeWithMask(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 1;
      case ArrayType at -> {
        int elementSize = computeSizeWithMask(at.elementType);
        yield elementSize * at.size;
      }
      case StructType st -> structSizes.getOrDefault(st.name, 0);
      case PointerType p -> 4;
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  /** Computes the alignment requirements for a given type. */
  int computeAlignment(Type type) {
    if (type instanceof BaseType) {
      BaseType baseType = (BaseType) type;
      if (baseType == BaseType.INT) {
        return 4; // Ensure at least 4-byte alignment
      }
    }
    if (type instanceof PointerType) {
      return 4;
    }
    if (type instanceof ArrayType) {
      return computeAlignment(((ArrayType) type).elementType) * ((ArrayType) type).size;
    }

    return Math.max(4, computeSizeWithMask(type));
  }

  /** Finds the struct declaration corresponding to a given struct type. */
  StructTypeDecl findStructDeclaration(StructType structType) {
    if (structType == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Null structType passed to findStructDeclaration.");
    }
    StructTypeDecl structDecl = structDeclarations.get(structType.name);
    if (structDecl == null) {
      System.out.println("[MemAllocCodeGen] ERROR: Struct type " + structType.name + " not found.");
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Struct type not found: " + structType.name);
    }
    return structDecl;
  }

  /** Returns the stack offset of a local variable. */
  public int getLocalOffset(VarDecl vd) {
    return localVarOffsets.getOrDefault(vd, 0);
  }

  /** Returns the total frame size for a function. */
  public int getFrameSize(FunDef fd) {
    return frameSizes.getOrDefault(fd, 0);
  }

  /** Computes the offset of a field within a struct. */
  public int computeFieldOffset(StructType structType, String fieldName) {
    // Ensure struct declaration is found
    StructTypeDecl structDecl = findStructDeclaration(structType);
    if (structDecl == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Could not find struct declaration for " + structType.name);
    }

    int offset = 0;
    for (VarDecl field : structDecl.fields) {
      int alignment = computeAlignment(field.type);
      offset = (offset + alignment - 1) & ~(alignment - 1); // Align correctly

      if (field.name.equals(fieldName)) return offset;

      offset += computeSizeWithMask(field.type);
    }

    throw new IllegalStateException(
        "[MemAllocCodeGen] ERROR: Field '"
            + fieldName
            + "' not found in struct: "
            + structType.name);
  }

  /** Computes the return offset for a function. */
  public int getReturnOffset(FunDef fd) {
    int offset = 8;
    for (VarDecl param : fd.params) {
      offset += computeSizeWithMask(param.type);
    }
    return offset;
  }

  // Add in MemAllocCodeGen.java:
  public int computeSize(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 1;
      case ArrayType at -> computeSize(at.elementType) * at.size;
      case StructType st -> {
        // calculate struct size
        int size = 0;
        for (VarDecl field : findStructDeclaration(st).fields) {
          size += computeSize(field.type);
        }
        yield size;
      }
      default -> throw new UnsupportedOperationException("Unknown type");
    };
  }

  public void registerStruct(StructTypeDecl std) {
    System.out.println("[MemAlloc] Registering struct: " + std.structType.name);
    structDeclarations.put(std.structType.name, std);
  }

  public StructTypeDecl findStructDeclaration(String structName) {
    if (structName == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: structName is null in findStructDeclaration.");
    }

    StructTypeDecl std = structDeclarations.get(structName);
    if (std == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Struct '" + structName + "' not found in declarations.");
    }

    System.out.println("[MemAllocCodeGen] Found struct declaration: " + structName);
    return std;
  }

  // getStructFields
  public List<VarDecl> getStructFields(StructType structType) {
    StructTypeDecl std = findStructDeclaration(structType.name);
    return std.fields;
  }
}
