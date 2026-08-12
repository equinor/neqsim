package neqsim.process.mechanicaldesign.pipeline;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** One transparent utilization check in a DNV-ST-F101 pipeline screening assessment. */
public final class DnvStF101LimitStateCheck implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Limit states represented by the screening kernel. */
  public enum LimitState {
    OPERATING_PRESSURE_CONTAINMENT, INCIDENTAL_PRESSURE_CONTAINMENT, SYSTEM_TEST_PRESSURE_CONTAINMENT,
    EXTERNAL_PRESSURE_COLLAPSE, PROPAGATION_BUCKLING, LOCAL_BUCKLING_LOAD_INTERACTION, FATIGUE, OVALITY,
    INSTALLATION_STRAIN
  }

  /** Screening outcome. */
  public enum Status {
    PASS, FAIL
  }

  private final LimitState limitState;
  private final Status status;
  private final double demand;
  private final double resistance;
  private final double utilization;
  private final String unit;
  private final String method;
  private final String note;

  DnvStF101LimitStateCheck(LimitState limitState, double demand, double resistance, String unit, String method,
      String note) {
    this.limitState = limitState;
    this.demand = demand;
    this.resistance = resistance;
    utilization = resistance > 0.0 ? demand / resistance : Double.POSITIVE_INFINITY;
    status = utilization <= 1.0 ? Status.PASS : Status.FAIL;
    this.unit = unit;
    this.method = method;
    this.note = note;
  }

  /** @return evaluated limit state */
  public LimitState getLimitState() {
    return limitState;
  }

  /** @return screening status */
  public Status getStatus() {
    return status;
  }

  /** @return calculated demand */
  public double getDemand() {
    return demand;
  }

  /** @return calculated resistance or utilization limit */
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

  /** @return transparent screening method label */
  public String getMethod() {
    return method;
  }

  /** @return implementation boundary note */
  public String getNote() {
    return note;
  }

  /** @return deterministic map suitable for JSON serialization */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("limitState", limitState.name());
    result.put("status", status.name());
    result.put("demand", Double.valueOf(demand));
    result.put("resistance", Double.valueOf(resistance));
    result.put("utilization", Double.valueOf(utilization));
    result.put("unit", unit);
    result.put("method", method);
    result.put("note", note);
    return result;
  }
}
