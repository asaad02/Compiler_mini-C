package regalloc;

import gen.asm.Register;
import java.util.*;

public class InterferenceGraph {
  // Each virtual register maps to the set of virtual registers it interferes with.
  public final Map<Register.Virtual, Set<Register.Virtual>> edges = new HashMap<>();

  public void addEdge(Register.Virtual v1, Register.Virtual v2) {
    edges.computeIfAbsent(v1, k -> new HashSet<>()).add(v2);
    edges.computeIfAbsent(v2, k -> new HashSet<>()).add(v1);
  }

  public Set<Register.Virtual> getInterferences(Register.Virtual v) {
    return edges.getOrDefault(v, new HashSet<>());
  }
}
