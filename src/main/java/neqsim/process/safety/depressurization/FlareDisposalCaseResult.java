package neqsim.process.safety.depressurization;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Peak source loads and capacity screens for one independent initiating event.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class FlareDisposalCaseResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Capacity status for a load and its configured limit. */
  public enum CapacityStatus {
    WITHIN_LIMIT, EXCEEDED, NOT_EVALUATED
  }

  private final String event;
  private final double peakMassFlowKgPerS;
  private final double peakTimeSeconds;
  private final double peakSteadyMassFlowKgPerS;
  private final Map<String, Double> contributions;
  private final Map<String, CapacityStatus> checks;
  private final Map<String, Double> peaks;
  private final Map<String, Double> lowerBounds;
  private final Map<String, Double> upperBounds;

  FlareDisposalCaseResult(String event, double mass, double time, double steadyMass, Map<String, Double> contributions,
      double tipMassLow, double heatLow, double heatHigh, double molarLow, double molarHigh,
      DynamicBlowdownFlareStudyDataSource config) {
    this.event = event;
    this.peakMassFlowKgPerS = mass;
    this.peakTimeSeconds = time;
    this.peakSteadyMassFlowKgPerS = steadyMass;
    this.contributions = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(contributions));
    Map<String, CapacityStatus> limits = new LinkedHashMap<String, CapacityStatus>();
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    Map<String, Double> lower = new LinkedHashMap<String, Double>();
    Map<String, Double> upper = new LinkedHashMap<String, Double>();
    double mach = MultiVesselBlowdownStudy.peakHeaderMach(mass, config.getHeaderDiameterM(),
        config.getHeaderPressureBara(), config.getHeaderTemperatureK(), config.getHeaderMolarMassKgPerMol(),
        config.getHeaderGamma());
    check(limits, values, lower, upper, "massFlowKgPerS", tipMassLow, mass, config.getFlareDesignMassFlowKgPerS());
    check(limits, values, lower, upper, "heatDutyW", heatLow, heatHigh, config.getFlareDesignHeatDutyW());
    check(limits, values, lower, upper, "molarFlowMolPerS", molarLow, molarHigh,
        config.getFlareDesignMolarFlowMolePerS());
    check(limits, values, lower, upper, "headerMach", mach, mach, config.getMaxAllowableHeaderMach());
    this.checks = Collections.unmodifiableMap(limits);
    this.peaks = Collections.unmodifiableMap(values);
    this.lowerBounds = Collections.unmodifiableMap(lower);
    this.upperBounds = Collections.unmodifiableMap(upper);
  }

  /** @return initiating event */
  public String getEvent() {
    return event;
  }

  /** @return governing mass flow in kg/s */
  public double getPeakMassFlowKgPerS() {
    return peakMassFlowKgPerS;
  }

  /** @return first time at which the peak mass flow is reached in seconds */
  public double getPeakTimeSeconds() {
    return peakTimeSeconds;
  }

  /** @return largest simultaneous PSV-only load for this event in kg/s */
  public double getPeakSteadyMassFlowKgPerS() {
    return peakSteadyMassFlowKgPerS;
  }

  /** @return per-source kg/s at the mass-flow peak */
  public Map<String, Double> getContributionsKgPerS() {
    return contributions;
  }

  /** @return maximum value for each metric, or null for missing demand data */
  public Map<String, Double> getPeakValues() {
    return peaks;
  }

  /** @return lower bound on each peak, including known contributions to incomplete load cases */
  public Map<String, Double> getPeakLowerBounds() {
    return lowerBounds;
  }

  /** @return upper bound on each peak, or null if discharge properties are missing */
  public Map<String, Double> getPeakUpperBounds() {
    return upperBounds;
  }

  /** @return per-limit capacity status */
  public Map<String, CapacityStatus> getCapacityChecks() {
    return checks;
  }

  /** @return combined disposal status, with a known exceedance taking priority */
  public CapacityStatus getCapacityStatus() {
    if (checks.containsValue(CapacityStatus.EXCEEDED)) {
      return CapacityStatus.EXCEEDED;
    }
    return checks.containsValue(CapacityStatus.NOT_EVALUATED) ? CapacityStatus.NOT_EVALUATED
        : CapacityStatus.WITHIN_LIMIT;
  }

  /** @return JSON-compatible result */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("initiatingEvent", event);
    map.put("governingTimeSeconds", peakTimeSeconds);
    map.put("peakSteadyMassFlowKgPerS", peakSteadyMassFlowKgPerS);
    map.put("peakReleasedMassFlowKgPerS", peakMassFlowKgPerS);
    map.put("contributionsKgPerS", contributions);
    map.put("peakValues", peaks);
    map.put("peakLowerBounds", lowerBounds);
    map.put("peakUpperBounds", upperBounds);
    map.put("capacityChecks", checks);
    map.put("capacityStatus", getCapacityStatus().name());
    return map;
  }

  private static void check(Map<String, CapacityStatus> checks, Map<String, Double> values, Map<String, Double> lower,
      Map<String, Double> upper, String metric, double low, double high, double limit) {
    values.put(metric, Double.isFinite(low) && low == high ? low : null);
    lower.put(metric, Double.isFinite(low) ? low : null);
    upper.put(metric, Double.isFinite(high) ? high : null);
    CapacityStatus status = CapacityStatus.NOT_EVALUATED;
    if (Double.isFinite(limit) && limit > 0.0) {
      if (Double.isFinite(low) && low > limit) {
        status = CapacityStatus.EXCEEDED;
      } else if (Double.isFinite(high) && high >= 0.0 && high <= limit) {
        status = CapacityStatus.WITHIN_LIMIT;
      }
    }
    checks.put(metric, status);
  }
}
