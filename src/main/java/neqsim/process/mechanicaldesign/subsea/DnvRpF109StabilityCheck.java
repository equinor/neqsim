package neqsim.process.mechanicaldesign.subsea;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable result for one DNV-RP-F109 screening limit state and load case. */
public final class DnvRpF109StabilityCheck implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Screening limit states exposed by the transparent calculation. */
  public enum LimitState {
    /** Factored upward hydrodynamic load versus submerged weight. */
    VERTICAL_STABILITY,
    /** Factored horizontal hydrodynamic load versus friction and passive resistance. */
    ABSOLUTE_LATERAL_STABILITY,
    /** Externally predicted displacement versus the selected displacement limit. */
    LATERAL_DISPLACEMENT
  }

  /** Screening verdict. */
  public enum Verdict {
    /** Demand does not exceed resistance or acceptance limit. */
    PASS,
    /** Demand exceeds resistance or acceptance limit. */
    FAIL
  }

  private final String caseId;
  private final LimitState limitState;
  private final Verdict verdict;
  private final double demand;
  private final double resistance;
  private final double utilization;
  private final String unit;
  private final String method;
  private final String note;

  DnvRpF109StabilityCheck(String caseId, LimitState limitState, double demand, double resistance, String unit,
      String method, String note) {
    this.caseId = caseId;
    this.limitState = limitState;
    this.demand = demand;
    this.resistance = resistance;
    utilization = resistance > 0.0 ? demand / resistance : Double.POSITIVE_INFINITY;
    verdict = utilization <= 1.0 ? Verdict.PASS : Verdict.FAIL;
    this.unit = unit;
    this.method = method;
    this.note = note;
  }

  /** @return traceable load-case identifier */
  public String getCaseId() {
    return caseId;
  }

  /** @return evaluated screening limit state */
  public LimitState getLimitState() {
    return limitState;
  }

  /** @return pass/fail screening verdict */
  public Verdict getVerdict() {
    return verdict;
  }

  /** @return factored demand */
  public double getDemand() {
    return demand;
  }

  /** @return resistance or acceptance limit */
  public double getResistance() {
    return resistance;
  }

  /** @return demand divided by resistance */
  public double getUtilization() {
    return utilization;
  }

  /** @return unit shared by demand and resistance */
  public String getUnit() {
    return unit;
  }

  /** @return calculation or externally supplied response route */
  public String getMethod() {
    return method;
  }

  /** @return engineering boundary note */
  public String getNote() {
    return note;
  }

  /** @return immutable provenance map */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("caseId", caseId);
    result.put("limitState", limitState.name());
    result.put("verdict", verdict.name());
    result.put("demand", Double.valueOf(demand));
    result.put("resistance", Double.valueOf(resistance));
    result.put("utilization", Double.valueOf(utilization));
    result.put("unit", unit);
    result.put("method", method);
    result.put("note", note);
    return Collections.unmodifiableMap(result);
  }
}
