package gen;

import java.util.*;

/** global context for code generation inlcude methodLabels: VirtualLabelGen and classAncestors: */
public class CodeGenContext {
  // methodLabel className to label
  private static Map<String, Map<String, String>> methodLabels = new HashMap<>();

  // classAncestors className to ancestors root
  private static Map<String, List<String>> classAncestors = new HashMap<>();

  // vtables className to LinkedHashMap(methodName to label)
  private static LinkedHashMap<String, LinkedHashMap<String, String>> vtables =
      new LinkedHashMap<>();

  // field layouts className to LinkedHashMap(fieldName to offset)
  private static Map<String, LinkedHashMap<String, Integer>> classFieldOffsets = new HashMap<>();

  // methodLabels
  public static void setMethodLabels(Map<String, Map<String, String>> m) {
    methodLabels.clear();
    methodLabels.putAll(m);
  }

  public static Map<String, Map<String, String>> getMethodLabels() {
    return methodLabels;
  }

  // classAncestors
  public static void setClassAncestors(Map<String, List<String>> m) {
    classAncestors.clear();
    classAncestors.putAll(m);
  }

  public static Map<String, List<String>> getClassAncestors() {
    return classAncestors;
  }

  // vtables
  public static void setVTables(Map<String, LinkedHashMap<String, String>> vt) {
    vtables.clear();
    vtables.putAll(vt);
  }

  public static Map<String, LinkedHashMap<String, String>> getVTables() {
    return vtables;
  }

  // field layouts
  public static void putClassFieldOffsets(
      String className, LinkedHashMap<String, Integer> fieldMap) {
    classFieldOffsets.put(className, fieldMap);
  }

  public static LinkedHashMap<String, Integer> getClassFieldOffsets(String className) {
    return classFieldOffsets.getOrDefault(className, new LinkedHashMap<>());
  }

  /** true if className vtable contains methodName */
  public static boolean hasVirtualMethod(String className, String methodName) {
    return vtables.containsKey(className) && vtables.get(className).containsKey(methodName);
  }

  /** return zero based index of methodName in className's vtable, or -1 */
  public static int getMethodIndex(String className, String methodName) {
    var tbl = vtables.get(className);
    if (tbl == null) return -1;
    int i = 0;
    for (String m : tbl.keySet()) {
      if (m.equals(methodName)) return i;
      i++;
    }
    return -1;
  }
}
