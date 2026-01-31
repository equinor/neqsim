package neqsim.process.safety.risk.sis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result from SIS-integrated risk analysis.
 *
 * <p>
 * Contains residual risk calculations after SIF mitigation including:
 * </p>
 * <ul>
 * <li>Unmitigated vs mitigated frequencies</li>
 * <li>Risk reduction achieved by each SIF</li>
 * <li>Total risk reduction factor</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SISRiskResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Study name. */
  private String studyName;

  /** Results per event. */
  private List<EventMitigationResult> eventResults;

  /** Total unmitigated frequency. */
  private double totalUnmitigatedFrequency;

  /** Total mitigated frequency. */
  private double totalMitigatedFrequency;

  /** Overall risk reduction factor. */
  private double overallRRF;

  /**
   * Result for a single event's mitigation.
   */
  public static class EventMitigationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String eventName;
    private double unmitigatedFrequency;
    private double mitigatedFrequency;
    private double riskReduction;
    private List<String> appliedSIFs;
    private Map<String, Double> sifContributions;

    public EventMitigationResult(String eventName) {
      this.eventName = eventName;
      this.appliedSIFs = new ArrayList<>();
      this.sifContributions = new HashMap<>();
    }

    public String getEventName() {
      return eventName;
    }

    public double getUnmitigatedFrequency() {
      return unmitigatedFrequency;
    }

    public double getMitigatedFrequency() {
      return mitigatedFrequency;
    }

    public double getRiskReduction() {
      return riskReduction;
    }

    public List<String> getAppliedSIFs() {
      return new ArrayList<>(appliedSIFs);
    }

    public Map<String, Double> getSifContributions() {
      return new HashMap<>(sifContributions);
    }

    void setFrequencies(double unmitigated, double mitigated) {
      this.unmitigatedFrequency = unmitigated;
      this.mitigatedFrequency = mitigated;
      this.riskReduction = unmitigated > 0 ? unmitigated / mitigated : 0;
    }

    void addSIF(String sifName, double contribution) {
      appliedSIFs.add(sifName);
      sifContributions.put(sifName, contribution);
    }
  }

  /**
   * Creates a SIS risk result.
   *
   * @param studyName study name
   */
  public SISRiskResult(String studyName) {
    this.studyName = studyName;
    this.eventResults = new ArrayList<>();
  }

  /**
   * Adds an event result.
   *
   * @param eventName event name
   * @param unmitigated unmitigated frequency
   * @param mitigated mitigated frequency
   * @param sifs applied SIFs
   */
  public void addEventResult(String eventName, double unmitigated, double mitigated,
      List<SafetyInstrumentedFunction> sifs) {
    EventMitigationResult result = new EventMitigationResult(eventName);
    result.setFrequencies(unmitigated, mitigated);

    double remaining = unmitigated;
    for (SafetyInstrumentedFunction sif : sifs) {
      double after = sif.getMitigatedFrequency(remaining);
      result.addSIF(sif.getName(), remaining - after);
      remaining = after;
    }

    eventResults.add(result);
  }

  /**
   * Calculates totals from event results.
   */
  public void calculateTotals() {
    totalUnmitigatedFrequency = 0;
    totalMitigatedFrequency = 0;

    for (EventMitigationResult result : eventResults) {
      totalUnmitigatedFrequency += result.getUnmitigatedFrequency();
      totalMitigatedFrequency += result.getMitigatedFrequency();
    }

    overallRRF =
        totalMitigatedFrequency > 0 ? totalUnmitigatedFrequency / totalMitigatedFrequency : 0;
  }

  // Getters

  public String getStudyName() {
    return studyName;
  }

  public List<EventMitigationResult> getEventResults() {
    return new ArrayList<>(eventResults);
  }

  public double getTotalUnmitigatedFrequency() {
    return totalUnmitigatedFrequency;
  }

  public double getTotalMitigatedFrequency() {
    return totalMitigatedFrequency;
  }

  public double getResidualFrequency() {
    return totalMitigatedFrequency;
  }

  public double getOverallRRF() {
    return overallRRF;
  }

  public double getTotalRiskReduction() {
    return totalUnmitigatedFrequency - totalMitigatedFrequency;
  }

  public double getRiskReductionPercent() {
    if (totalUnmitigatedFrequency <= 0) {
      return 0;
    }
    return (1.0 - totalMitigatedFrequency / totalUnmitigatedFrequency) * 100;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("studyName", studyName);

    // Summary
    Map<String, Object> summary = new HashMap<>();
    summary.put("totalUnmitigatedFrequency", totalUnmitigatedFrequency);
    summary.put("totalMitigatedFrequency", totalMitigatedFrequency);
    summary.put("residualFrequency", totalMitigatedFrequency);
    summary.put("overallRRF", overallRRF);
    summary.put("riskReductionPercent", getRiskReductionPercent());
    map.put("summary", summary);

    // Event results
    List<Map<String, Object>> events = new ArrayList<>();
    for (EventMitigationResult result : eventResults) {
      Map<String, Object> eventMap = new HashMap<>();
      eventMap.put("eventName", result.getEventName());
      eventMap.put("unmitigatedFrequency", result.getUnmitigatedFrequency());
      eventMap.put("mitigatedFrequency", result.getMitigatedFrequency());
      eventMap.put("riskReduction", result.getRiskReduction());
      eventMap.put("appliedSIFs", result.getAppliedSIFs());
      eventMap.put("sifContributions", result.getSifContributions());
      events.add(eventMap);
    }
    map.put("eventResults", events);

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

  @Override
  public String toString() {
    return String.format(
        "SISRiskResult[%s: unmitigated=%.2e, mitigated=%.2e, RRF=%.0f, reduction=%.1f%%]",
        studyName, totalUnmitigatedFrequency, totalMitigatedFrequency, overallRRF,
        getRiskReductionPercent());
  }
}
