package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Per-period availability/derating series for a network edge.
 */
public class NetworkAvailabilitySchedule implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String edgeName;
  private final double[] availability;

  /**
   * Create an all-available schedule.
   *
   * @param edgeName edge name
   * @param periodCount period count
   */
  public NetworkAvailabilitySchedule(String edgeName, int periodCount) {
    this.edgeName = edgeName;
    this.availability = new double[periodCount];
    Arrays.fill(availability, 1.0);
  }

  /**
   * Derate an inclusive-exclusive period range.
   *
   * @param fromPeriod inclusive start
   * @param toPeriod exclusive end
   * @param fraction availability from 0 to 1
   */
  public void derate(int fromPeriod, int toPeriod, double fraction) {
    if (fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Availability must be between 0 and 1");
    }
    if (fromPeriod < 0 || toPeriod > availability.length || fromPeriod >= toPeriod) {
      throw new IllegalArgumentException("Invalid availability period range");
    }
    for (int index = fromPeriod; index < toPeriod; index++) {
      availability[index] = fraction;
    }
  }

  /** @return edge name */
  public String getEdgeName() {
    return edgeName;
  }

  /**
   * Get availability at a period.
   *
   * @param periodIndex zero-based period index
   * @return availability at the period
   */
  public double getAvailability(int periodIndex) {
    return availability[periodIndex];
  }

  /** @return defensive series copy */
  public double[] getAvailability() {
    return availability.clone();
  }
}
