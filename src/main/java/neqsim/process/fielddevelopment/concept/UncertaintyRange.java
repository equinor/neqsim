package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;

/**
 * Stores a P10/P50/P90 range for a field-development assumption.
 *
 * @author ESOL
 * @version 1.0
 */
public final class UncertaintyRange implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final String unit;
  private final double p10;
  private final double p50;
  private final double p90;

  /**
   * Creates an uncertainty range.
   *
   * @param name assumption name
   * @param unit engineering unit for the values
   * @param p10 P10 case value
   * @param p50 P50 case value
   * @param p90 P90 case value
   */
  public UncertaintyRange(String name, String unit, double p10, double p50, double p90) {
    this.name = name == null ? "assumption" : name;
    this.unit = unit == null ? "-" : unit;
    this.p10 = p10;
    this.p50 = p50;
    this.p90 = p90;
  }

  /**
   * Creates a deterministic range where P10, P50, and P90 are equal.
   *
   * @param name assumption name
   * @param unit engineering unit for the value
   * @param value deterministic value
   * @return deterministic uncertainty range
   */
  public static UncertaintyRange deterministic(String name, String unit, double value) {
    return new UncertaintyRange(name, unit, value, value, value);
  }

  /**
   * Gets the assumption name.
   *
   * @return assumption name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the value unit.
   *
   * @return value unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the P10 value.
   *
   * @return P10 value
   */
  public double getP10() {
    return p10;
  }

  /**
   * Gets the P50 value.
   *
   * @return P50 value
   */
  public double getP50() {
    return p50;
  }

  /**
   * Gets the P90 value.
   *
   * @return P90 value
   */
  public double getP90() {
    return p90;
  }

  /**
   * Gets the absolute range span.
   *
   * @return absolute difference between P90 and P10
   */
  public double getSpan() {
    return Math.abs(p90 - p10);
  }

  /**
   * Checks whether the range is deterministic.
   *
   * @return true if P10, P50, and P90 are identical within numerical tolerance
   */
  public boolean isDeterministic() {
    return Math.abs(p10 - p50) < 1.0e-12 && Math.abs(p50 - p90) < 1.0e-12;
  }

  @Override
  public String toString() {
    return String.format("%s: P10 %.3g, P50 %.3g, P90 %.3g %s", name, p10, p50, p90, unit);
  }
}
