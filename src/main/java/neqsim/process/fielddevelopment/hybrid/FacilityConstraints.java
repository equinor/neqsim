package neqsim.process.fielddevelopment.hybrid;

import java.io.Serializable;

/**
 * Handling capacities of the receiving facility, used to hold wells back when the network can deliver more than the
 * facility can take.
 *
 * <p>
 * A capacity that is not set is unlimited. Rates are surface volumes in Sm3/day.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class FacilityConstraints implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Constrained quantity. */
  public enum Quantity {
    /** Stock-tank oil rate. */
    OIL,
    /** Water rate. */
    WATER,
    /** Gas rate. */
    GAS,
    /** Liquid (oil plus water) rate. */
    LIQUID
  }

  private final double[] capacity = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
      Double.POSITIVE_INFINITY};

  /**
   * Sets a capacity.
   *
   * @param quantity constrained quantity
   * @param capacitySm3PerDay capacity in Sm3/day; {@link Double#POSITIVE_INFINITY} for unlimited
   * @return this object
   * @throws IllegalArgumentException if the capacity is not positive
   */
  public FacilityConstraints setCapacity(Quantity quantity, double capacitySm3PerDay) {
    if (!(capacitySm3PerDay > 0.0)) {
      throw new IllegalArgumentException("Capacity must be positive: " + capacitySm3PerDay);
    }
    capacity[quantity.ordinal()] = capacitySm3PerDay;
    return this;
  }

  /**
   * Gets a capacity.
   *
   * @param quantity constrained quantity
   * @return capacity in Sm3/day, infinite when unlimited
   */
  public double getCapacity(Quantity quantity) {
    return capacity[quantity.ordinal()];
  }

  /**
   * Gets the rate of a constrained quantity from the phase rates.
   *
   * @param quantity constrained quantity
   * @param oil oil rate in Sm3/day
   * @param water water rate in Sm3/day
   * @param gas gas rate in Sm3/day
   * @return rate of the quantity in Sm3/day
   */
  public static double rateOf(Quantity quantity, double oil, double water, double gas) {
    switch (quantity) {
    case OIL:
      return oil;
    case WATER:
      return water;
    case GAS:
      return gas;
    default:
      return oil + water;
    }
  }

  /**
   * Finds the most utilised capacity.
   *
   * @param oil oil rate in Sm3/day
   * @param water water rate in Sm3/day
   * @param gas gas rate in Sm3/day
   * @return the quantity with the highest rate-to-capacity ratio, or null when no capacity is set
   */
  public Quantity mostUtilised(double oil, double water, double gas) {
    Quantity worst = null;
    double highest = 0.0;
    for (Quantity quantity : Quantity.values()) {
      double limit = capacity[quantity.ordinal()];
      if (Double.isInfinite(limit)) {
        continue;
      }
      double utilisation = rateOf(quantity, oil, water, gas) / limit;
      if (worst == null || utilisation > highest) {
        worst = quantity;
        highest = utilisation;
      }
    }
    return worst;
  }

  /**
   * Gets the utilisation of a capacity.
   *
   * @param quantity constrained quantity
   * @param oil oil rate in Sm3/day
   * @param water water rate in Sm3/day
   * @param gas gas rate in Sm3/day
   * @return rate divided by capacity; 0 for an unlimited capacity
   */
  public double utilisation(Quantity quantity, double oil, double water, double gas) {
    double limit = capacity[quantity.ordinal()];
    return Double.isInfinite(limit) ? 0.0 : rateOf(quantity, oil, water, gas) / limit;
  }
}
