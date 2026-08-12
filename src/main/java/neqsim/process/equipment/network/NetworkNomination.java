package neqsim.process.equipment.network;

import java.io.Serializable;

/**
 * Period series with explicit mass, molar, standard-volume, actual-volume, or energy basis.
 */
public class NetworkNomination implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String pointName;
  private final double[] values;
  private final String unit;
  private final NetworkDecisionVariable.RateBasis basis;
  private final double toleranceFraction;

  /**
   * Create a nomination series.
   *
   * @param pointName named source/sink
   * @param values period values
   * @param unit unit
   * @param basis explicit rate basis
   * @param toleranceFraction allowed relative deviation
   */
  public NetworkNomination(String pointName, double[] values, String unit, NetworkDecisionVariable.RateBasis basis,
      double toleranceFraction) {
    if (values == null || values.length == 0) {
      throw new IllegalArgumentException("Nomination series cannot be empty");
    }
    this.pointName = pointName;
    this.values = values.clone();
    this.unit = unit;
    this.basis = basis;
    this.toleranceFraction = toleranceFraction;
  }

  /** @return point name */
  public String getPointName() {
    return pointName;
  }

  /** @return defensive series copy */
  public double[] getValues() {
    return values.clone();
  }

  /**
   * Get the nominated value at a period.
   *
   * @param periodIndex zero-based period index
   * @return nominated value at the period
   */
  public double getValue(int periodIndex) {
    return values[periodIndex];
  }

  /** @return number of periods */
  public int size() {
    return values.length;
  }

  /** @return unit */
  public String getUnit() {
    return unit;
  }

  /** @return explicit basis */
  public NetworkDecisionVariable.RateBasis getBasis() {
    return basis;
  }

  /** @return relative tolerance */
  public double getToleranceFraction() {
    return toleranceFraction;
  }
}
