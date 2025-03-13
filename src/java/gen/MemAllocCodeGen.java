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
  public final Map<String, StructTypeDecl> structDeclarations = new HashMap<>();
  private final Set<String> globalVariables = new HashSet<>();
  private final AssemblyProgram.Section dataSection;

  // fpOffset is the offset from the frame pointer ($fp) for local variables
  private int fpOffset = 0;
  // nextAvailableOffset is the next available offset for local variables
  private int nextAvailableOffset = 0;

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
      case StructTypeDecl std -> {
        structDeclarations.put(std.name, std);
        structSizes.put(std.name, computeStructSize(std));
      }
      default -> {}
    }
  }

  // allocateFunction
  private void allocateFunction(FunDef fd) {
    fpOffset = 0;
    enterScope();

    for (int i = 0; i < fd.params.size(); i++) {
      allocateFunctionParameter(fd.params.get(i), i);
    }

    enterScope();
    for (VarDecl localVar : fd.block.vds) {
      allocateVariable(localVar);
    }
    // compute frame size
    frameSizes.put(fd, alignTo16(-fpOffset));
  }

  private void allocateFunctionParameter(VarDecl vd, int paramIndex) {
    if (scopeStack.isEmpty()) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: No active scope for function parameters.");
    }

    scopeStack.peek().put(vd.name, vd);

    int offset;
    if (vd.type instanceof StructType) {
      int structSize = computeSize(vd.type);
      structSize = alignTo16(structSize);
      offset = -(paramIndex + 1) * structSize;
    } else {
      offset = alignTo4(-(paramIndex + 1) * 4);
    }

    localVarOffsets.put(vd, offset);
    System.out.printf("[MemAllocCodeGen] Allocated parameter '%s' at offset %d\n", vd.name, offset);
  }

  // allocateVariable
  public void allocateVariable(VarDecl vd) {
    if (scopeStack.isEmpty()) {
      allocateGlobalVariable(vd);
    } else {
      allocateLocalVariable(vd);
    }
  }

  // allocateGlobalVariable
  void allocateGlobalVariable(VarDecl vd) {
    if (globalVars.containsKey(vd.name)) {
      throw new IllegalStateException("ERROR: Global variable redeclaration: " + vd.name);
    }
    globalVars.put(vd.name, vd);
    globalVariables.add(vd.name);

    dataSection.emit(new Directive("align 4"));
    dataSection.emit(Label.get(vd.name));
    dataSection.emit(new Directive("space 4"));
  }

  // allocateLocalVariable
  private void allocateLocalVariable(VarDecl vd) {
    if (scopeStack.isEmpty()) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: No active scope for local variable.");
    }

    Map<String, VarDecl> currentScope = scopeStack.peek();
    if (currentScope.containsKey(vd.name)) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Variable redeclared in the same scope: " + vd.name);
    }

    currentScope.put(vd.name, vd);
    int alignedSize = alignTo4(computeSize(vd.type));
    fpOffset -= alignedSize;
    int offset = fpOffset;
    localVarOffsets.put(vd, offset);
    System.out.println(
        "[MemAllocCodeGen] Allocated local variable " + vd.name + " at offset " + offset);
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

  public int computeSize(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case BaseType.CHAR -> 1;
      case PointerType p -> 4;
      case ArrayType at -> {
        int elemSize = computeSize(at.elementType);
        int arraySize = elemSize * at.size;
        yield alignTo(arraySize, computeAlignment(at.elementType));
      }
      case StructType st -> alignTo(structSizes.getOrDefault(st.name, 0), computeAlignment(st));

      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  public int computeAlignment(Type type) {
    return switch (type) {
      case BaseType.INT -> 4;
      case PointerType p -> 4;
      case BaseType.CHAR -> 1;
      case ArrayType at -> computeAlignment(at.elementType);
      case StructType st -> {
        StructTypeDecl structDecl = getStructDeclaration(st.name);
        int maxAlign = 1;
        for (VarDecl field : structDecl.fields) {
          maxAlign = Math.max(maxAlign, computeAlignment(field.type));
        }
        yield maxAlign;
      }
      default -> throw new UnsupportedOperationException("Unknown type: " + type);
    };
  }

  // alignTo4
  public int alignTo4(int value) {
    return (value + 3) & ~3;
  }

  // allign to 8 bytes
  public int alignTo8(int value) {
    return (value + 7) & ~7;
  }

  // alignTo16
  public int alignTo16(int value) {
    return (value + 15) & ~15;
  }

  // alignTo
  public int alignTo(int value, int alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
  }

  // enterScope
  public void enterScope() {
    scopeStack.push(new HashMap<>());
  }

  // exitScope
  public void exitScope() {
    if (!scopeStack.isEmpty()) {
      scopeStack.pop();
    }
  }

  // Retrieves a variable declaration considering scoping and shadowing.
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

  // Computes the total size of a struct, considering field alignment.
  public int computeStructSize(StructTypeDecl structDecl) {
    int offset = 0;
    int maxAlignment = 1;

    for (VarDecl field : structDecl.fields) {
      int fieldSize = computeSize(field.type);
      int fieldAlign = computeAlignment(field.type);
      offset = alignTo(offset, fieldAlign);
      offset += fieldSize;
      maxAlignment = Math.max(maxAlignment, fieldAlign);
    }

    return alignTo(offset, maxAlignment);
  }

  // Computes the byte offset of a struct field with proper alignment.
  int computeFieldOffset(StructType structType, String fieldName) {
    StructTypeDecl structDecl = getStructDeclaration(structType.name);
    if (structDecl == null) {
      throw new IllegalStateException(
          "[MemAllocCodeGen] ERROR: Struct not found: " + structType.name);
    }

    int offset = 0;
    for (VarDecl field : structDecl.fields) {
      int fieldSize = computeSize(field.type);
      int fieldAlign = computeAlignment(field.type);
      offset = alignTo(offset, fieldAlign);

      if (field.name.equals(fieldName)) {
        return offset;
      }

      offset += fieldSize;
    }

    throw new IllegalStateException("[MemAllocCodeGen] ERROR: Field not found: " + fieldName);
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
    int level = 0;
    for (int i = scopeStack.size() - 1; i >= 0; i--) {
      if (scopeStack.get(i).containsKey(varName)) {
        return level;
      }
      level++;
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

  public void printStructDebugInfo() {
    System.out.println("\n======= Struct Debug Info =======");
    for (String structName : structDeclarations.keySet()) {
      StructTypeDecl structDecl = structDeclarations.get(structName);
      int structSize = structSizes.get(structName);
      int structAlign = computeAlignment(new StructType(structName));

      System.out.printf(
          "Struct: %-12s | Total Size: %3d bytes | Alignment: %d\n",
          structName, structSize, structAlign);
      System.out.println("  Fields:");

      int offset = 0;
      for (VarDecl field : structDecl.fields) {
        int fieldSize = computeSize(field.type);
        int fieldAlign = computeAlignment(field.type);
        offset = alignTo(offset, fieldAlign);
        System.out.printf(
            "    %-10s | Offset: %3d | Size: %2d bytes | Align: %d\n",
            field.name, offset, fieldSize, fieldAlign);
        offset += fieldSize;
      }
      System.out.println("---------------------------------");
    }
    System.out.println("=================================");
  }

  public void printDebugTable() {
    System.out.println("\n======= Memory Allocation Debug Table =======");
    // Global Variables
    System.out.println("\n--- Global Variables ---");
    for (String varName : globalVars.keySet()) {
      int size = computeSize(globalVars.get(varName).type);
      int align = computeAlignment(globalVars.get(varName).type);
      System.out.printf(
          "GLOBAL | %-12s | Address: 0x%08X | Size: %2d bytes | Align: %d\n",
          varName, 0, size, align);
    }

    // Function Stack Frames
    System.out.println("\n--- Function Memory Allocation ---");
    for (FunDef func : frameSizes.keySet()) {
      int frameSize = getFrameSize(func);
      System.out.printf("\nFunction: %-12s | Frame Size: %3d bytes\n", func.name, frameSize);

      System.out.println("  Parameters:");
      for (VarDecl param : func.params) {
        int offset = localVarOffsets.getOrDefault(param, Integer.MIN_VALUE);
        int size = computeSize(param.type);
        int align = computeAlignment(param.type);
        System.out.printf(
            "    %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            param.name, offset, size, align, param.type);
      }

      System.out.println("  Local Variables:");
      for (VarDecl local : func.block.vds) {
        int offset = localVarOffsets.getOrDefault(local, Integer.MIN_VALUE);
        int size = computeSize(local.type);
        int align = computeAlignment(local.type);
        System.out.printf(
            "    %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            local.name, offset, size, align, local.type);
      }
    }

    // Print Pointer Allocations
    System.out.println("\n--- Heap & Pointer Variables ---");
    for (String varName : globalVars.keySet()) {
      VarDecl var = globalVars.get(varName);
      if (var.type instanceof PointerType) {
        System.out.printf(
            "POINTER | %-12s | Address: 0x%08X | Size: %2d bytes | Align: %d\n",
            varName, 0, computeSize(var.type), computeAlignment(var.type));
      }
    }

    // Print Scope Stack
    System.out.println("\n--- Scope Stack ---");
    int level = scopeStack.size();
    for (Map<String, VarDecl> scope : scopeStack) {
      System.out.printf("Scope Level %d:\n", level--);
      for (String varName : scope.keySet()) {
        VarDecl var = scope.get(varName);
        int offset = localVarOffsets.getOrDefault(var, Integer.MIN_VALUE);
        int size = computeSize(var.type);
        int align = computeAlignment(var.type);
        System.out.printf(
            "  %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            varName, offset, size, align, var.type);
      }
    }

    System.out.println("=============================================\n");
  }

  // Prints a detailed memory table for a specific function, including stack and alignment.
  public void printMemoryTable(FunDef fd) {
    System.out.println("\n==== Function: " + fd.name + " ====");
    System.out.println("| Variable       | Offset | Size | Align | Type         |");
    System.out.println("|---------------|--------|------|-------|--------------|");

    for (VarDecl param : fd.params) {
      int offset = localVarOffsets.getOrDefault(param, -999);
      int size = computeSize(param.type);
      int align = computeAlignment(param.type);
      System.out.printf(
          "| %-14s | %-6d | %-4d | %-5d | %-12s |\n", param.name, offset, size, align, param.type);
    }

    for (VarDecl localVar : fd.block.vds) {
      int offset = localVarOffsets.getOrDefault(localVar, -999);
      int size = computeSize(localVar.type);
      int align = computeAlignment(localVar.type);
      System.out.printf(
          "| %-14s | %-6d | %-4d | %-5d | %-12s |\n",
          localVar.name, offset, size, align, localVar.type);
    }

    System.out.printf(
        "\n[MemAllocCodeGen] Function: %-12s | Frame Size: %3d bytes\n",
        fd.name, frameSizes.get(fd));

    System.out.println("=====================================================");
  }
}
