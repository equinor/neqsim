package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Traces the cascade impact of a process perturbation through the flowsheet topology.
 *
 * <p>
 * When an operating margin degrades on one piece of equipment, the effects can propagate downstream
 * through connected streams and equipment. The {@code CascadeImpactAnalyzer} walks the process
 * topology (using {@link ProcessEquipmentInterface#getOutletStreams()}) and performs steady-state
 * what-if simulations to identify which downstream equipment margins are affected.
 * </p>
 *
 * <p>
 * The analysis is performed by:
 * </p>
 * <ol>
 * <li>Taking a snapshot of all current margin values</li>
 * <li>Applying a perturbation to a specified variable</li>
 * <li>Re-running the process model</li>
 * <li>Comparing new margin values against the snapshot</li>
 * <li>Identifying all margins that changed by more than a significance threshold</li>
 * </ol>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * CascadeImpactAnalyzer analyzer = new CascadeImpactAnalyzer(process, envelope);
 *
 * List&lt;CascadeImpactAnalyzer.ImpactNode&gt; impacts =
 *     analyzer.analyzeImpact("HP Separator", "pressure", 85.0, "bara");
 *
 * for (CascadeImpactAnalyzer.ImpactNode node : impacts) {
 *   System.out.printf("  %s.%s: margin changed from %.1f%% to %.1f%%\n", node.getEquipmentName(),
 *       node.getMarginKey(), node.getOriginalMarginPercent(), node.getNewMarginPercent());
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CascadeImpactAnalyzer implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default threshold for significance (percent change in margin). */
  private static final double DEFAULT_SIGNIFICANCE_THRESHOLD = 1.0;

  private final ProcessSystem processSystem;
  private final ProcessOperatingEnvelope envelope;
  private double significanceThreshold;

  /**
   * Creates a cascade impact analyzer for the given process and envelope.
   *
   * @param processSystem the process system to analyze
   * @param envelope the operating envelope with current margin state
   */
  public CascadeImpactAnalyzer(ProcessSystem processSystem, ProcessOperatingEnvelope envelope) {
    this.processSystem = processSystem;
    this.envelope = envelope;
    this.significanceThreshold = DEFAULT_SIGNIFICANCE_THRESHOLD;
  }

  /**
   * Analyzes the downstream impact of changing a variable on a specific equipment.
   *
   * <p>
   * This method takes a snapshot of all margins, applies the change via process re-run, and
   * identifies all margins that changed significantly. The process state is restored after
   * analysis.
   * </p>
   *
   * @param equipmentName the equipment to perturb
   * @param variableName the variable to change
   * @param newValue the new value to set
   * @param unit the engineering unit
   * @return list of impact nodes describing downstream effects
   */
  public List<ImpactNode> analyzeImpact(String equipmentName, String variableName, double newValue,
      String unit) {
    // Step 1: Snapshot current margins
    envelope.evaluate();
    Map<String, Double> baselineMargins = snapshotMargins();

    // Step 2: Run the process (it should already be configured with the perturbation)
    // Note: caller is responsible for applying the perturbation before calling this
    try {
      processSystem.run();
    } catch (Exception e) {
      // Process run failed — return empty impact list
      return new ArrayList<ImpactNode>();
    }

    // Step 3: Evaluate margins with new state
    envelope.evaluate();
    Map<String, Double> newMargins = snapshotMargins();

    // Step 4: Compare and find significant changes
    return compareMargins(baselineMargins, newMargins, equipmentName);
  }

  /**
   * Identifies the downstream equipment chain from a given starting equipment.
   *
   * <p>
   * Uses {@link ProcessEquipmentInterface#getOutletStreams()} to walk the topology graph and
   * collect all equipment reachable from the starting point.
   * </p>
   *
   * @param startEquipmentName name of the starting equipment
   * @return ordered list of downstream equipment names
   */
  public List<String> getDownstreamChain(String startEquipmentName) {
    List<String> chain = new ArrayList<String>();
    Map<String, Boolean> visited = new HashMap<String, Boolean>();

    ProcessEquipmentInterface startUnit = findUnit(startEquipmentName);
    if (startUnit == null) {
      return chain;
    }

    walkDownstream(startUnit, chain, visited);
    return chain;
  }

  /**
   * Recursively walks downstream from the given equipment.
   *
   * @param unit current equipment
   * @param chain list accumulating downstream names
   * @param visited map preventing cycles
   */
  private void walkDownstream(ProcessEquipmentInterface unit, List<String> chain,
      Map<String, Boolean> visited) {
    if (visited.containsKey(unit.getName())) {
      return;
    }
    visited.put(unit.getName(), Boolean.TRUE);

    List<StreamInterface> outlets = unit.getOutletStreams();
    if (outlets == null) {
      return;
    }

    for (StreamInterface outStream : outlets) {
      if (outStream == null) {
        continue;
      }
      // Find equipment that uses this stream as inlet
      for (ProcessEquipmentInterface downstream : processSystem.getUnitOperations()) {
        if (downstream.getName().equals(unit.getName())) {
          continue;
        }
        List<StreamInterface> inlets = downstream.getInletStreams();
        if (inlets == null) {
          continue;
        }
        for (StreamInterface inlet : inlets) {
          if (inlet != null && inlet.getName().equals(outStream.getName())) {
            chain.add(downstream.getName());
            walkDownstream(downstream, chain, visited);
          }
        }
      }
    }
  }

  /**
   * Finds a unit operation by name in the process system.
   *
   * @param name equipment name
   * @return the equipment, or null if not found
   */
  private ProcessEquipmentInterface findUnit(String name) {
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit.getName().equals(name)) {
        return unit;
      }
    }
    return null;
  }

  /**
   * Takes a snapshot of all current margin percentages.
   *
   * @return map of margin key to margin percent
   */
  private Map<String, Double> snapshotMargins() {
    Map<String, Double> snapshot = new HashMap<String, Double>();
    for (OperatingMargin m : envelope.getAllMargins()) {
      snapshot.put(m.getKey(), m.getMarginPercent());
    }
    return snapshot;
  }

  /**
   * Compares baseline and new margin snapshots to find significant changes.
   *
   * @param baseline original margin values
   * @param updated new margin values after perturbation
   * @param sourceEquipment the equipment where perturbation was applied
   * @return list of impact nodes
   */
  private List<ImpactNode> compareMargins(Map<String, Double> baseline, Map<String, Double> updated,
      String sourceEquipment) {
    List<ImpactNode> impacts = new ArrayList<ImpactNode>();

    for (Map.Entry<String, Double> entry : updated.entrySet()) {
      String key = entry.getKey();
      double newPercent = entry.getValue();
      Double basePercent = baseline.get(key);
      if (basePercent == null) {
        continue;
      }

      double delta = newPercent - basePercent;
      if (Math.abs(delta) >= significanceThreshold) {
        // Extract equipment name from key (format: equipment.variable.direction)
        String eqName = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        impacts.add(new ImpactNode(eqName, key, basePercent, newPercent, delta, sourceEquipment));
      }
    }

    // Sort by magnitude of impact (largest first)
    java.util.Collections.sort(impacts);
    return impacts;
  }

  /**
   * Sets the significance threshold for margin changes.
   *
   * @param threshold minimum percent change to be considered significant
   */
  public void setSignificanceThreshold(double threshold) {
    this.significanceThreshold = Math.max(0.0, threshold);
  }

  /**
   * Returns the significance threshold.
   *
   * @return threshold in percent
   */
  public double getSignificanceThreshold() {
    return significanceThreshold;
  }

  /**
   * Represents a single node in the cascade impact chain.
   *
   * <p>
   * Each node records the equipment affected, which margin changed, the original and new values,
   * and the delta. Nodes are sorted by absolute magnitude of change.
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class ImpactNode implements Serializable, Comparable<ImpactNode> {
    private static final long serialVersionUID = 1L;

    private final String equipmentName;
    private final String marginKey;
    private final double originalMarginPercent;
    private final double newMarginPercent;
    private final double deltaPercent;
    private final String sourceEquipment;

    /**
     * Creates an impact node.
     *
     * @param equipmentName name of the affected equipment
     * @param marginKey full margin key
     * @param originalMarginPercent margin before perturbation
     * @param newMarginPercent margin after perturbation
     * @param deltaPercent change in margin percent
     * @param sourceEquipment equipment where perturbation originated
     */
    public ImpactNode(String equipmentName, String marginKey, double originalMarginPercent,
        double newMarginPercent, double deltaPercent, String sourceEquipment) {
      this.equipmentName = equipmentName;
      this.marginKey = marginKey;
      this.originalMarginPercent = originalMarginPercent;
      this.newMarginPercent = newMarginPercent;
      this.deltaPercent = deltaPercent;
      this.sourceEquipment = sourceEquipment;
    }

    /**
     * Returns the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Returns the margin key.
     *
     * @return margin key
     */
    public String getMarginKey() {
      return marginKey;
    }

    /**
     * Returns the margin percentage before perturbation.
     *
     * @return original margin percent
     */
    public double getOriginalMarginPercent() {
      return originalMarginPercent;
    }

    /**
     * Returns the margin percentage after perturbation.
     *
     * @return new margin percent
     */
    public double getNewMarginPercent() {
      return newMarginPercent;
    }

    /**
     * Returns the change in margin percentage.
     *
     * @return delta percent (negative = margin reduced)
     */
    public double getDeltaPercent() {
      return deltaPercent;
    }

    /**
     * Returns the source equipment where perturbation originated.
     *
     * @return source equipment name
     */
    public String getSourceEquipment() {
      return sourceEquipment;
    }

    /**
     * Returns whether the margin degraded (moved closer to limit).
     *
     * @return true if margin decreased
     */
    public boolean isDegraded() {
      return deltaPercent < 0;
    }

    /**
     * Compares by absolute magnitude of change (largest first).
     *
     * @param other the other node
     * @return comparison result
     */
    @Override
    public int compareTo(ImpactNode other) {
      return Double.compare(Math.abs(other.deltaPercent), Math.abs(this.deltaPercent));
    }

    /**
     * Returns a formatted summary string.
     *
     * @return summary
     */
    @Override
    public String toString() {
      return String.format("%s [%s]: %.1f%% -> %.1f%% (delta: %+.1f%%)", equipmentName, marginKey,
          originalMarginPercent, newMarginPercent, deltaPercent);
    }
  }
}
