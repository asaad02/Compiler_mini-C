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
  public final Map<VarDecl, Integer> localVarOffsets = new HashMap<>();
  public final Map<FunDef, Integer> frameSizes = new HashMap<>();
  public final Map<String, VarDecl> globalVars = new HashMap<>();
  public final Stack<Map<String, VarDecl>> scopeStack = new Stack<>();
  public final Map<String, Integer> structSizes = new HashMap<>();
  public final Map<String, StructTypeDecl> structDeclarations = new HashMap<>();
  public final Set<String> globalVariables = new HashSet<>();
  private final Map<String, Map<String, Integer>> structFieldOffsets = new HashMap<>();

  public final AssemblyProgram.Section dataSection;

  private int globalOffset = 0;
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
            structSizes.put(std.structType.name, alignTo(computeStructSize(std), 8));
          }
        }
        p.decls.forEach(this::visit);
      }
      case FunDef fd -> allocateFunction(fd);
      case VarDecl vd -> allocateVariable(vd);
      case StructTypeDecl std -> {
        structDeclarations.put(std.name, std);
        structSizes.put(std.name, alignTo(computeStructSize(std), 8));
      }
      default -> {}
    }
  }

  // allocateFunction
  void allocateFunction(FunDef fd) {
    fpOffset = 0;

    // Scope 1: Parameter scope
    enterScope();
    for (int i = 0; i < fd.params.size(); i++) {
      allocateFunctionParameter(fd.params.get(i), i);
    }

    // Scope 2: Local variable scope
    enterScope();
    for (VarDecl localVar : fd.block.vds) {
      allocateVariable(localVar);
    }

    frameSizes.put(fd, alignTo16(-fpOffset));
    System.out.println("[MemAllocCodeGen] Allocated function: " + fd.name);
    for (VarDecl param : fd.params) {
      System.out.printf(
          "[MemAllocCodeGen] Param: %s | Offset: %d\n", param.name, getLocalOffset(param));
    }
  }

  void allocateFunctionParameter(VarDecl vd, int paramIndex) {
    if (scopeStack.isEmpty()) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: No active scope for function parameters.");
    }

    int offset;
    if (vd.type instanceof StructType) {
      offset = -(paramIndex + 1) * alignTo(computeSize(vd.type), 8);
    } else if (vd.type instanceof ArrayType at) {
      offset = alignTo4(-(paramIndex + 1) * 4);
    } else {
      offset = alignTo4(-(paramIndex + 1) * 4);
    }

    localVarOffsets.put(vd, offset);
    scopeStack.peek().put(vd.name, vd);

    System.out.printf(
        "[MemAllocCodeGen] Allocated parameter '%s' at offset %d (Size: %d, Align: %d)\n",
        vd.name, offset, computeSize(vd.type), computeAlignment(vd.type));
  }

  private void allocateLocalVariable(VarDecl vd) {
    if (scopeStack.peek().containsKey(vd.name)) {
      throw new IllegalStateException("[MemAlloc] ERROR: Variable redeclared: " + vd.name);
    }

    int alignedSize = alignTo4(computeSize(vd.type));
    fpOffset -= alignedSize;
    localVarOffsets.put(vd, fpOffset);
    scopeStack.peek().put(vd.name, vd);
  }

  // Allocates global variables
  private final Map<String, Integer> globalVarOffsets = new HashMap<>();

  public void allocateGlobalVariable(VarDecl vd) {
    if (globalVars.containsKey(vd.name)) {
      throw new IllegalStateException("[MemAlloc] ERROR: Global variable redeclared: " + vd.name);
    }

    globalVars.put(vd.name, vd);
    globalOffset = alignTo(globalOffset, computeAlignment(vd.type)); // Ensure proper alignment
    globalVarOffsets.put(vd.name, globalOffset); // Track per-variable offsets

    dataSection.emit(new Directive("align " + computeAlignment(vd.type)));
    dataSection.emit(Label.get(vd.name));
    dataSection.emit(new Directive("space " + computeSize(vd.type)));

    globalOffset += computeSize(vd.type);
  }

  public int getGlobalOffset(String varName) {
    return globalVarOffsets.getOrDefault(varName, -1);
  }

  public int getGlobalOffset(VarDecl vd) {
    if (!globalVars.containsKey(vd.name)) {
      throw new IllegalStateException("[MemAlloc] ERROR: Global variable not found: " + vd.name);
    }
    return globalVarOffsets.getOrDefault(vd.name, -1);
  }

  // Computes memory size for different types
  public int computeSize(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case PointerType p -> 4;
      case BaseType.CHAR -> 4;
      case ArrayType at ->
          alignTo(
              computeSize(at.elementType) * at.dimensions.stream().reduce(1, (a, b) -> a * b),
              computeAlignment(at.elementType));
      case StructType st -> alignTo(structSizes.getOrDefault(st.name, 0), computeAlignment(st));
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  // Computes memory alignment for different types
  public int computeAlignment(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 4;
      case PointerType p -> 4;
      case ArrayType at -> computeAlignment(at.elementType);
      case StructType st -> Math.max(alignTo(structSizes.getOrDefault(st.name, 8), 8), 8);
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  public int alignTo(int value, int alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
  }

  public int alignTo4(int value) {
    return alignTo(value, 4);
  }

  public int alignTo16(int value) {
    return alignTo(value, 16);
  }

  // Computes total struct size, considering alignment
  public int computeStructSize(StructTypeDecl structDecl) {
    int offset = 0;
    int maxAlignment = 1;

    for (VarDecl field : structDecl.fields) {
      int fieldAlign = computeAlignment(field.type);
      offset = alignTo(offset, fieldAlign);
      maxAlignment = Math.max(maxAlignment, fieldAlign);
      offset += computeSize(field.type);
    }

    return alignTo(offset, maxAlignment);
  }

  public int computeFieldOffset(StructType structType, String fieldName) {
    Map<String, Integer> fieldOffsets =
        structFieldOffsets.computeIfAbsent(structType.name, k -> new HashMap<>());
    if (fieldOffsets.containsKey(fieldName)) {
      return fieldOffsets.get(fieldName);
    }

    StructTypeDecl structDecl = structDeclarations.get(structType.name);
    if (structDecl == null) {
      throw new IllegalStateException("[MemAlloc] ERROR: Struct not found: " + structType.name);
    }

    int offset = 0;
    for (VarDecl field : structDecl.fields) {
      offset = alignTo(offset, computeAlignment(field.type));
      fieldOffsets.put(field.name, offset);
      offset += computeSize(field.type);
    }

    return fieldOffsets.getOrDefault(fieldName, -1);
  }

  public int getArrayDimensionSize(ArrayType arrayType, int i) {
    if (i < 0 || i >= arrayType.dimensions.size()) {
      throw new IndexOutOfBoundsException("[MemAlloc] ERROR: Invalid array dimension index: " + i);
    }
    return arrayType.dimensions.get(i);
  }

  public void enterScope() {
    System.out.println("[MemAllocCodeGen] ENTER scope level: " + scopeStack.size());
    scopeStack.push(new HashMap<>());
  }

  public void exitScope() {
    if (!scopeStack.isEmpty()) {
      scopeStack.pop();
      System.out.println("[MemAllocCodeGen] EXIT scope  now level: " + scopeStack.size());
    } else {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Attempted to exit non-existent scope!");
    }
  }

  // allocateVariable
  public void allocateVariable(VarDecl vd) {
    if (scopeStack.isEmpty()) {
      allocateGlobalVariable(vd);
    } else {
      allocateLocalVariable(vd);
    }
  }

  public int getLocalOffset(VarDecl varDecl, int scopeLevel) {
    // Variable not found
    if (scopeLevel < 0) return -1;
    return localVarOffsets.getOrDefault(varDecl, -1);
  }

  public int getLocalOffset(VarDecl varDecl) {
    int scopeLevel = getScopeLevel(varDecl.name);
    if (scopeLevel < 0) {
      throw new IllegalStateException(
          "[ExprAddrCodeGen] ERROR: Variable not found: " + varDecl.name);
    }

    if (localVarOffsets.containsKey(varDecl)) {
      return localVarOffsets.get(varDecl);
    }

    throw new IllegalStateException(
        "[ExprAddrCodeGen] ERROR: Offset not found for: " + varDecl.name);
  }

  // allign to 8 bytes
  public int alignTo8(int value) {
    return (value + 7) & ~7;
  }

  // retrieves a variable declaration considering scoping and shadowing.
  public VarDecl getVarDecl(String name) {
    for (int i = scopeStack.size() - 1; i >= 0; i--) {
      Map<String, VarDecl> scope = scopeStack.get(i);
      if (scope.containsKey(name)) {
        return scope.get(name);
      }
    }

    // Only return global if no local variable exists
    if (globalVars.containsKey(name)) {
      return globalVars.get(name);
    }
    throw new IllegalStateException("[MemAllocCodeGen] ERROR: Variable not found: " + name);
  }

  // Returns the total stack frame size for a function.
  public int getFrameSize(FunDef fd) {
    return frameSizes.getOrDefault(fd, 0);
  }

  // Checks if a variable is global.
  public boolean isGlobal(String name) {
    return globalVars.containsKey(name);
  }

  public StructTypeDecl getStructDeclaration(String structName) {
    if (structName == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Attempting to retrieve a struct with a NULL name!");
    }

    StructTypeDecl structDecl = structDeclarations.get(structName);

    if (structDecl == null) {
      System.err.println("[MemAllocCodeGen] ERROR: Struct not found: " + structName);
      System.err.println("Available Structs: " + structDeclarations.keySet());
      throw new IllegalStateException("[MemAllocCodeGen] ERROR: Struct not found: " + structName);
    }

    if (structDecl.structType == null || structDecl.structType.name == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: StructType in StructTypeDecl is NULL for: " + structName);
    }

    return structDecl;
  }

  public void registerStructDeclaration(StructTypeDecl structDecl) {
    if (structDecl == null || structDecl.structType == null || structDecl.structType.name == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Attempting to register a NULL struct!");
    }

    System.out.println("[MemAllocCodeGen] Registering struct: " + structDecl.structType.name);

    if (structDeclarations.containsKey(structDecl.structType.name)) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Struct already declared: " + structDecl.structType.name);
    }

    structDeclarations.put(structDecl.structType.name, structDecl);
    structSizes.put(structDecl.structType.name, computeStructSize(structDecl));
  }

  public int getScopeLevel(String varName) {
    for (int i = scopeStack.size() - 1; i >= 0; i--) {
      if (scopeStack.get(i).containsKey(varName)) {
        return scopeStack.size() - 1 - i; // level 0 = innermost scope
      }
    }
    return -1;
  }

  // computeFrameSize
  public int computeFrameSize(FunDef fd) {
    int frameSize = 0;
    for (VarDecl param : fd.params) {
      frameSize += computeSize(param.type);
    }
    for (VarDecl local : fd.block.vds) {
      frameSize += computeSize(local.type);
    }
    return frameSize;
  }

  // getFunctionDefinition
  public FunDef getFunctionDefinition(String name) {
    for (FunDef fd : frameSizes.keySet()) {
      if (fd.name.equals(name)) {
        return fd;
      }
    }
    throw new IllegalStateException("[MemAllocCodeGen] ERROR: Function not found: " + name);
  }

  // getLocalOffset string
  public int getLocalOffset(String varName) {
    for (int i = scopeStack.size() - 1; i >= 0; i--) {
      Map<String, VarDecl> scope = scopeStack.get(i);
      if (scope.containsKey(varName)) {
        return localVarOffsets.get(scope.get(varName));
      }
    }
    throw new IllegalStateException("[MemAllocCodeGen] ERROR: Variable not found: " + varName);
  }

  private int align(int value, int alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
  }

  // call printAllMemoryDebug
  public void printAllMemory() {
    MemDebugUtils.printAllMemoryDebug(this);
  }
}
