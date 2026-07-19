package neqsim.process.fielddevelopment.lifecycle;

import java.io.Serializable;

/** Standard-condition oil, gas and water rates handled by a production facility. */
public final class FacilityProductionRate implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double oilSm3PerDay;
  private final double gasSm3PerDay;
  private final double waterSm3PerDay;

  /**
   * Creates a non-negative production-rate vector.
   *
   * @param oilSm3PerDay standard oil rate
   * @param gasSm3PerDay standard gas rate
   * @param waterSm3PerDay standard produced-water rate
   */
  public FacilityProductionRate(double oilSm3PerDay, double gasSm3PerDay, double waterSm3PerDay) {
    this.oilSm3PerDay = nonNegative(oilSm3PerDay, "oilSm3PerDay");
    this.gasSm3PerDay = nonNegative(gasSm3PerDay, "gasSm3PerDay");
    this.waterSm3PerDay = nonNegative(waterSm3PerDay, "waterSm3PerDay");
  }

  /** Returns a zero-rate vector. */
  public static FacilityProductionRate zero() {
    return new FacilityProductionRate(0.0, 0.0, 0.0);
  }

  private static double nonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
    return value;
  }

  /** Returns a rate vector multiplied by a non-negative factor. */
  public FacilityProductionRate scale(double factor) {
    if (!Double.isFinite(factor) || factor < 0.0) {
      throw new IllegalArgumentException("factor must be finite and non-negative");
    }
    return new FacilityProductionRate(oilSm3PerDay * factor, gasSm3PerDay * factor, waterSm3PerDay * factor);
  }

  /** Returns the component-wise sum of this and another rate vector. */
  public FacilityProductionRate plus(FacilityProductionRate other) {
    if (other == null) {
      throw new IllegalArgumentException("other rate is required");
    }
    return new FacilityProductionRate(oilSm3PerDay + other.oilSm3PerDay, gasSm3PerDay + other.gasSm3PerDay,
        waterSm3PerDay + other.waterSm3PerDay);
  }

  /** Returns standard oil rate in Sm3/day. */
  public double getOilSm3PerDay() {
    return oilSm3PerDay;
  }

  /** Returns standard gas rate in Sm3/day. */
  public double getGasSm3PerDay() {
    return gasSm3PerDay;
  }

  /** Returns standard produced-water rate in Sm3/day. */
  public double getWaterSm3PerDay() {
    return waterSm3PerDay;
  }

  /** Returns total standard liquid rate in Sm3/day. */
  public double getLiquidSm3PerDay() {
    return oilSm3PerDay + waterSm3PerDay;
  }
}
