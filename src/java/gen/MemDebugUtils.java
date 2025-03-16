package gen;

import ast.*;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MemDebugUtils {

  // Prints allocated global variables with memory addresses and alignment
  public static void printGlobalMemory(Map<String, VarDecl> globalVars, MemAllocCodeGen allocator) {
    System.out.println("\n========== GLOBAL MEMORY ==========");
    System.out.printf(
        "| %-14s | %-10s | %-6s | %-6s | %-12s |\n",
        "Variable", "Address", "Size", "Align", "Type");
    System.out.println("--------------------------------------------------------");

    for (Map.Entry<String, VarDecl> entry : globalVars.entrySet()) {
      String varName = entry.getKey();
      VarDecl var = entry.getValue();
      int size = allocator.computeSize(var.type);
      int align = allocator.computeAlignment(var.type);
      int address = 0;

      System.out.printf(
          "| %-14s | 0x%08X | %-6d | %-6d | %-12s |\n", varName, address, size, align, var.type);
    }
    System.out.println("===================================");
  }

  // Prints function stack frames, showing parameters and local variables
  public static void printFunctionFrames(
      Map<FunDef, Integer> frameSizes,
      Map<VarDecl, Integer> localVarOffsets,
      MemAllocCodeGen allocator) {

    System.out.println("\n========== FUNCTION STACK FRAMES ==========");
    for (Map.Entry<FunDef, Integer> entry : frameSizes.entrySet()) {
      FunDef fd = entry.getKey();
      int frameSize = allocator.alignTo16(entry.getValue());

      System.out.printf("\nFunction: %-12s | Frame Size: %3d bytes\n", fd.name, frameSize);
      System.out.println("------------------------------------------");

      System.out.println("  Parameters:");
      for (VarDecl param : fd.params) {
        printVarDebugInfo(param, localVarOffsets, allocator);
      }

      System.out.println("\n  Local Variables:");
      for (VarDecl local : fd.block.vds) {
        printVarDebugInfo(local, localVarOffsets, allocator);
      }
    }
    System.out.println("===========================================");
  }

  // Prints scope stack with variables, memory locations, and offsets
  public static void printScopeStack(
      Stack<Map<String, VarDecl>> scopeStack,
      Map<VarDecl, Integer> localVarOffsets,
      MemAllocCodeGen allocator) {

    System.out.println("\n========== SCOPE STACK ==========");
    List<Map<String, VarDecl>> scopeList = scopeStack.stream().toList();
    for (int i = scopeList.size() - 1; i >= 0; i--) {
      System.out.printf("Scope Level %d:\n", scopeList.size() - i);
      System.out.println("---------------------------------");

      for (Map.Entry<String, VarDecl> entry : scopeList.get(i).entrySet()) {
        printVarDebugInfo(entry.getValue(), localVarOffsets, allocator);
      }
    }
    System.out.println("===================================");
  }

  // Prints heap-allocated memory including structs and arrays
  public static void printHeapAllocations(
      Map<String, Integer> structSizes, MemAllocCodeGen allocator) {
    System.out.println("\n========== HEAP MEMORY ALLOCATIONS ==========");
    System.out.printf("| %-14s | %-10s |\n", "Struct", "Heap Size");
    System.out.println("---------------------------------------------");

    for (Map.Entry<String, Integer> entry : structSizes.entrySet()) {
      String structName = entry.getKey();
      int size = entry.getValue();
      int alignedSize = allocator.alignTo(size, 8);
      System.out.printf(
          "| %-14s | %3d bytes (Aligned: %d bytes) |\n", structName, size, alignedSize);
    }
    System.out.println("=============================================");
  }

  // Prints struct layouts including field offsets and sizes
  public static void printStructDebugInfo(
      Map<String, StructTypeDecl> structDeclarations,
      Map<String, Integer> structSizes,
      MemAllocCodeGen allocator) {

    System.out.println("\n========== STRUCT MEMORY DEBUG INFO ==========");
    for (String structName : structDeclarations.keySet()) {
      StructTypeDecl structDecl = structDeclarations.get(structName);
      int structSize = structSizes.get(structName);
      int structAlign = allocator.computeAlignment(new StructType(structName));

      System.out.printf(
          "\nStruct: %-12s | Total Size: %3d bytes | Alignment: %d\n",
          structName, structSize, structAlign);
      System.out.println("----------------------------------------------------");

      int offset = 0;
      for (VarDecl field : structDecl.fields) {
        int fieldSize = allocator.computeSize(field.type);
        int fieldAlign = allocator.computeAlignment(field.type);
        offset = allocator.alignTo(offset, fieldAlign);

        System.out.printf(
            "    %-10s | Offset: %3d | Size: %2d bytes | Align: %d\n",
            field.name, offset, fieldSize, fieldAlign);
        offset += fieldSize;
      }
    }
    System.out.println("===============================================");
  }

  // Full Debug Report: Includes every part of memory allocation
  public static void printAllMemoryDebug(MemAllocCodeGen allocator) {
    System.out.println("\n=========== COMPLETE MEMORY DEBUG REPORT ===========");
    printGlobalMemory(allocator.globalVars, allocator);
    printFunctionFrames(allocator.frameSizes, allocator.localVarOffsets, allocator);
    printScopeStack(allocator.scopeStack, allocator.localVarOffsets, allocator);
    printHeapAllocations(allocator.structSizes, allocator);
    printStructDebugInfo(allocator.structDeclarations, allocator.structSizes, allocator);
    printPointerTable(allocator.globalVars);
    System.out.println("=====================================================");
  }

  // Prints all pointer allocations
  public static void printPointerTable(Map<String, VarDecl> globalVars) {
    System.out.println("\n========== POINTER TABLE ==========");
    System.out.printf("| %-14s | %-10s |\n", "Variable", "Address");
    System.out.println("----------------------------------");

    for (String varName : globalVars.keySet()) {
      VarDecl var = globalVars.get(varName);
      if (var.type instanceof PointerType) {
        System.out.printf("| %-14s | 0x%08X |\n", varName, 0);
      }
    }
    System.out.println("===================================");
  }

  // Helper function to print variable memory details
  private static void printVarDebugInfo(
      VarDecl var, Map<VarDecl, Integer> localVarOffsets, MemAllocCodeGen allocator) {
    int offset = localVarOffsets.getOrDefault(var, -1);
    int size = allocator.computeSize(var.type);
    int align = allocator.computeAlignment(var.type);
    System.out.printf(
        "    %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
        var.name, offset, size, align, var.type);
  }
}
