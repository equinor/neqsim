package neqsim.process.safety.risk.sis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Layer of Protection Analysis (LOPA) result.
 *
 * <p>
 * LOPA is a semi-quantitative risk assessment method that analyzes independent protection layers
 * (IPLs) to determine if the risk from a specific scenario is adequately mitigated.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class LOPAResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Scenario/event name. */
  private String scenarioName;

  /** Initiating event frequency (per year). */
  private double initiatingEventFrequency;

  /** Target mitigated frequency (per year). */
  private double targetFrequency;

  /** Final mitigated frequency (per year). */
  private double mitigatedFrequency;

  /** Protection layers applied. */
  private List<ProtectionLayer> layers;

  /** Whether target is met. */
  private boolean targetMet;

  /** Gap to target (positive = shortfall). */
  private double gapToTarget;

  /**
   * Individual protection layer in LOPA.
   */
  public static class ProtectionLayer implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private double pfd;
    private double frequencyBefore;
    private double frequencyAfter;
    private double contribution;

    public ProtectionLayer(String name, double pfd, double before, double after) {
      this.name = name;
      this.pfd = pfd;
      this.frequencyBefore = before;
      this.frequencyAfter = after;
      this.contribution = before - after;
    }

    public String getName() {
      return name;
    }

    public double getPfd() {
      return pfd;
    }

    public double getFrequencyBefore() {
      return frequencyBefore;
    }

    public double getFrequencyAfter() {
      return frequencyAfter;
    }

    public double getContribution() {
      return contribution;
    }

    public double getRRF() {
      return pfd > 0 ? 1.0 / pfd : Double.POSITIVE_INFINITY;
    }
  }

  /**
   * Creates a LOPA result.
   *
   * @param scenarioName scenario name
   */
  public LOPAResult(String scenarioName) {
    this.scenarioName = scenarioName;
    this.layers = new ArrayList<>();
  }

  /**
   * Sets initiating event frequency.
   *
   * @param frequency frequency per year
   */
  public void setInitiatingEventFrequency(double frequency) {
    this.initiatingEventFrequency = frequency;
  }

  /**
   * Sets target frequency.
   *
   * @param frequency target frequency per year
   */
  public void setTargetFrequency(double frequency) {
    this.targetFrequency = frequency;
    updateTargetStatus();
  }

  /**
   * Sets mitigated frequency.
   *
   * @param frequency mitigated frequency per year
   */
  public void setMitigatedFrequency(double frequency) {
    this.mitigatedFrequency = frequency;
    updateTargetStatus();
  }

  /**
   * Adds a protection layer.
   *
   * @param name layer name
   * @param pfd probability of failure on demand
   * @param before frequency before this layer
   * @param after frequency after this layer
   */
  public void addLayer(String name, double pfd, double before, double after) {
    layers.add(new ProtectionLayer(name, pfd, before, after));
  }

  private void updateTargetStatus() {
    targetMet = mitigatedFrequency <= targetFrequency;
    gapToTarget = mitigatedFrequency - targetFrequency;
  }

  // Getters

  public String getScenarioName() {
    return scenarioName;
  }

  public double getInitiatingEventFrequency() {
    return initiatingEventFrequency;
  }

  public double getTargetFrequency() {
    return targetFrequency;
  }

  public double getMitigatedFrequency() {
    return mitigatedFrequency;
  }

  public List<ProtectionLayer> getLayers() {
    return new ArrayList<>(layers);
  }

  public boolean isTargetMet() {
    return targetMet;
  }

  public double getGapToTarget() {
    return gapToTarget;
  }

  /**
   * Gets total risk reduction factor.
   *
   * @return total RRF
   */
  public double getTotalRRF() {
    return mitigatedFrequency > 0 ? initiatingEventFrequency / mitigatedFrequency : 0;
  }

  /**
   * Gets required additional RRF to meet target.
   *
   * @return required RRF (0 if target already met)
   */
  public double getRequiredAdditionalRRF() {
    if (targetMet) {
      return 0;
    }
    return targetFrequency > 0 ? mitigatedFrequency / targetFrequency : Double.POSITIVE_INFINITY;
  }

  /**
   * Gets required SIL for additional protection.
   *
   * @return required SIL (0 if target met)
   */
  public int getRequiredAdditionalSIL() {
    if (targetMet) {
      return 0;
    }
    double requiredPFD = 1.0 / getRequiredAdditionalRRF();
    return SafetyInstrumentedFunction.getRequiredSil(requiredPFD);
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("scenarioName", scenarioName);
    map.put("initiatingEventFrequency", initiatingEventFrequency);
    map.put("targetFrequency", targetFrequency);
    map.put("mitigatedFrequency", mitigatedFrequency);
    map.put("targetMet", targetMet);
    map.put("gapToTarget", gapToTarget);
    map.put("totalRRF", getTotalRRF());

    // Layers
    List<Map<String, Object>> layerList = new ArrayList<>();
    for (ProtectionLayer layer : layers) {
      Map<String, Object> layerMap = new HashMap<>();
      layerMap.put("name", layer.getName());
      layerMap.put("pfd", layer.getPfd());
      layerMap.put("rrf", layer.getRRF());
      layerMap.put("frequencyBefore", layer.getFrequencyBefore());
      layerMap.put("frequencyAfter", layer.getFrequencyAfter());
      layerMap.put("contribution", layer.getContribution());
      layerList.add(layerMap);
    }
    map.put("protectionLayers", layerList);

    if (!targetMet) {
      Map<String, Object> gap = new HashMap<>();
      gap.put("requiredRRF", getRequiredAdditionalRRF());
      gap.put("requiredSIL", getRequiredAdditionalSIL());
      map.put("gapAnalysis", gap);
    }

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Generates ASCII visualization of LOPA.
   *
   * @return ASCII diagram
   */
  public String toVisualization() {
    StringBuilder sb = new StringBuilder();
    sb.append("LOPA: ").append(scenarioName).append("\n");
    sb.append("═".repeat(60)).append("\n\n");

    sb.append(String.format("Initiating Event Frequency: %.2e /year%n", initiatingEventFrequency));
    sb.append("\n");

    sb.append("Protection Layers:\n");
    sb.append("─".repeat(60)).append("\n");
    sb.append(String.format("%-25s %10s %15s %15s%n", "Layer", "PFD", "Before", "After"));
    sb.append("─".repeat(60)).append("\n");

    for (ProtectionLayer layer : layers) {
      sb.append(String.format("%-25s %10.2e %15.2e %15.2e%n", layer.getName(), layer.getPfd(),
          layer.getFrequencyBefore(), layer.getFrequencyAfter()));
    }

    sb.append("─".repeat(60)).append("\n");
    sb.append(String.format("%-25s %10s %15s %15.2e%n", "TOTAL",
        String.format("%.0fx", getTotalRRF()), "", mitigatedFrequency));
    sb.append("\n");

    sb.append(String.format("Target Frequency: %.2e /year%n", targetFrequency));
    sb.append(String.format("Status: %s%n", targetMet ? "✓ TARGET MET" : "✗ GAP EXISTS"));

    if (!targetMet) {
      sb.append(String.format("Required Additional RRF: %.0f (SIL %d)%n",
          getRequiredAdditionalRRF(), getRequiredAdditionalSIL()));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("LOPAResult[%s: %.2e → %.2e, target=%.2e, %s]", scenarioName,
        initiatingEventFrequency, mitigatedFrequency, targetFrequency, targetMet ? "MET" : "GAP");
  }
}
