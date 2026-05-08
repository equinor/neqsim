package neqsim.process.safety.escalation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Escalation (domino-effect) graph analyzer.
 *
 * <p>
 * Models a directed graph where each node is a piece of process equipment with a vulnerability
 * threshold (e.g. 12.5 kW/m² heat flux for steel structures, 35 kPa overpressure for vessels) and
 * each edge is a hazard exposure (heat, blast) from a primary failure to a secondary item with the
 * predicted load at that target. The analyzer propagates failures: if the cumulative load on a
 * target exceeds its threshold, that target also fails and propagates further.
 *
 * <p>
 * <b>References:</b>
 * <ul>
 * <li>API STD 521, §4.6 — heat flux limits for escalation</li>
 * <li>CCPS — Guidelines for Chemical Process Quantitative Risk Analysis, 2nd Ed.</li>
 * <li>Cozzani &amp; Salzano (2004) — Domino effect probability mapping</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class EscalationGraphAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Map<String, Double> thresholds = new HashMap<>();
  private final Map<String, List<Edge>> outgoing = new HashMap<>();

  /**
   * Register a target item and its failure threshold (load to fail).
   *
   * @param itemId item identifier
   * @param failureThreshold threshold load (units must match edge loads)
   * @return this analyzer for chaining
   */
  public EscalationGraphAnalyzer addItem(String itemId, double failureThreshold) {
    thresholds.put(itemId, failureThreshold);
    outgoing.put(itemId, new ArrayList<Edge>());
    return this;
  }

  /**
   * Register an exposure edge from {@code source} to {@code target} with a load value.
   *
   * @param source upstream failed item
   * @param target downstream item exposed to source's hazard
   * @param load hazard load delivered (e.g. heat flux W/m² or overpressure Pa)
   * @return this analyzer for chaining
   */
  public EscalationGraphAnalyzer addExposure(String source, String target, double load) {
    if (!thresholds.containsKey(source) || !thresholds.containsKey(target)) {
      throw new IllegalArgumentException("Both source and target must be registered first");
    }
    outgoing.get(source).add(new Edge(target, load));
    return this;
  }

  /**
   * Propagate a primary failure of {@code initiator} and collect all items that escalate.
   * Cumulative load on each target is summed across all paths.
   *
   * @param initiator initial failed item
   * @return set of all items that ultimately fail (including the initiator)
   */
  public Set<String> propagate(String initiator) {
    if (!thresholds.containsKey(initiator)) {
      throw new IllegalArgumentException("Unknown initiator: " + initiator);
    }
    Set<String> failed = new HashSet<>();
    failed.add(initiator);

    Map<String, Double> cumulative = new HashMap<>();
    boolean changed = true;
    while (changed) {
      changed = false;
      // Reset and recompute cumulative loads on each non-failed item
      Map<String, Double> newCumulative = new HashMap<>();
      for (String src : failed) {
        for (Edge e : outgoing.get(src)) {
          double current = newCumulative.containsKey(e.target) ? newCumulative.get(e.target) : 0.0;
          newCumulative.put(e.target, current + e.load);
        }
      }
      cumulative = newCumulative;
      // Check which not-yet-failed items exceed threshold
      for (Map.Entry<String, Double> en : cumulative.entrySet()) {
        if (failed.contains(en.getKey())) {
          continue;
        }
        if (en.getValue() >= thresholds.get(en.getKey())) {
          failed.add(en.getKey());
          changed = true;
        }
      }
    }
    return failed;
  }

  /**
   * Run propagation from each registered item in turn and return the worst-case failure set
   * (largest number of escalated items).
   *
   * @return worst-case escalation set
   */
  public Set<String> worstCaseEscalation() {
    Set<String> worst = Collections.emptySet();
    for (String item : thresholds.keySet()) {
      Set<String> esc = propagate(item);
      if (esc.size() > worst.size()) {
        worst = esc;
      }
    }
    return worst;
  }

  /**
   * @return all registered item IDs
   */
  public Set<String> getItems() {
    return Collections.unmodifiableSet(thresholds.keySet());
  }

  /** Directed exposure edge. */
  private static class Edge implements Serializable {
    private static final long serialVersionUID = 1L;
    final String target;
    final double load;

    Edge(String target, double load) {
      this.target = target;
      this.load = load;
    }
  }
}
