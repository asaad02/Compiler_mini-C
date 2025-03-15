package gen;

import ast.*;
import java.util.Map;
import java.util.Stack;

/**
 * Utility class for structured debugging of memory allocations, including global variables,
 * function stack frames, heap allocations, and struct alignments.
 */
public class MemDebugUtils {

  public static void printGlobalMemory(Map<String, VarDecl> globalVars, MemAllocCodeGen allocator) {
    System.out.println("\n==== Global Memory Allocation ====");
    for (String varName : globalVars.keySet()) {
      VarDecl var = globalVars.get(varName);
      int size = allocator.computeSize(var.type);
      int align = allocator.computeAlignment(var.type);
      System.out.printf(
          "GLOBAL | %-12s | Address: 0x%08X | Size: %2d bytes | Align: %d\n",
          varName, 0, size, align);
    }
  }

  public static void printFunctionFrames(
      Map<FunDef, Integer> frameSizes,
      Map<VarDecl, Integer> localVarOffsets,
      MemAllocCodeGen allocator) {
    System.out.println("\n==== Function Stack Frames ====");
    for (FunDef fd : frameSizes.keySet()) {
      int frameSize = frameSizes.get(fd);
      System.out.printf("\nFunction: %-12s | Frame Size: %3d bytes\n", fd.name, frameSize);

      System.out.println("  Parameters:");
      for (VarDecl param : fd.params) {
        int offset = localVarOffsets.getOrDefault(param, -999);
        int size = allocator.computeSize(param.type);
        int align = allocator.computeAlignment(param.type);
        System.out.printf(
            "    %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            param.name, offset, size, align, param.type);
      }

      System.out.println("  Local Variables:");
      for (VarDecl local : fd.block.vds) {
        int offset = localVarOffsets.getOrDefault(local, -999);
        int size = allocator.computeSize(local.type);
        int align = allocator.computeAlignment(local.type);
        System.out.printf(
            "    %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            local.name, offset, size, align, local.type);
      }
    }
  }

  public static void printScopeStack(
      Stack<Map<String, VarDecl>> scopeStack,
      Map<VarDecl, Integer> localVarOffsets,
      MemAllocCodeGen allocator) {
    System.out.println("\n==== Scope Stack ====");
    int level = scopeStack.size();
    for (Map<String, VarDecl> scope : scopeStack) {
      System.out.printf("Scope Level %d:\n", level--);
      for (String varName : scope.keySet()) {
        VarDecl var = scope.get(varName);
        int offset = localVarOffsets.getOrDefault(var, -999);
        int size = allocator.computeSize(var.type);
        int align = allocator.computeAlignment(var.type);
        System.out.printf(
            "  %-12s | Offset: %3d | Size: %2d | Align: %d | Type: %s\n",
            varName, offset, size, align, var.type);
      }
    }
  }

  public static void printHeapAllocations(Map<String, Integer> structSizes) {
    System.out.println("\n==== Heap Memory Allocations ====");
    for (String structName : structSizes.keySet()) {
      int size = structSizes.get(structName);
      System.out.printf("Struct: %-12s | Heap Size: %3d bytes\n", structName, size);
    }
  }

  public static void printAllMemoryDebug(MemAllocCodeGen allocator) {
    System.out.println("\n========= COMPLETE MEMORY DEBUG REPORT =========");
    printGlobalMemory(allocator.globalVars, allocator);
    printFunctionFrames(allocator.frameSizes, allocator.localVarOffsets, allocator);
    printScopeStack(allocator.scopeStack, allocator.localVarOffsets, allocator);
    printHeapAllocations(allocator.structSizes);
    System.out.println("===============================================\n");
  }

  public static void callMemoryDebug(MemAllocCodeGen allocator) {
    System.out.println("\n=== Memory Debug Start ===");
    printAllMemoryDebug(allocator);
    System.out.println("=== Memory Debug End ===\n");
  }

  public static void attachDebugToMemAlloc(MemAllocCodeGen allocator) {
    System.out.println("[MemAllocCodeGen] Debugging memory state after allocations.");
    callMemoryDebug(allocator);
  }
}
