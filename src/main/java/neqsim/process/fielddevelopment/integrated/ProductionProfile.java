package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A time-marched production profile produced by {@link IntegratedProductionModel#runProfile}.
 *
 * <p>
 * Each profile point captures the field rate, revenue, energy and emission rates, and the average
 * reservoir pressure at a point in time. Aggregate cumulative production and revenue over the
 * horizon are also stored. The profile is the natural input for field-economics (NPV/IRR) and
 * emissions reporting.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see IntegratedProductionModel#runProfile(double, double)
 */
public class ProductionProfile implements Serializable {
  private static final long serialVersionUID = 1000L;

  /**
   * A single point on a production profile.
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class Point implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double timeYears;
    private final double rateSm3PerDay;
    private final double revenuePerDay;
    private final double energyKWhPerDay;
    private final double emissionsKgPerDay;
    private final double reservoirPressureBara;

    /**
     * Creates a profile point.
     *
     * @param timeYears time in years
     * @param rateSm3PerDay field rate in Sm3/day
     * @param revenuePerDay revenue in currency per day
     * @param energyKWhPerDay energy in kWh per day
     * @param emissionsKgPerDay emissions in kg CO2 per day
     * @param reservoirPressureBara average reservoir pressure in bara
     */
    public Point(double timeYears, double rateSm3PerDay, double revenuePerDay,
        double energyKWhPerDay, double emissionsKgPerDay, double reservoirPressureBara) {
      this.timeYears = timeYears;
      this.rateSm3PerDay = rateSm3PerDay;
      this.revenuePerDay = revenuePerDay;
      this.energyKWhPerDay = energyKWhPerDay;
      this.emissionsKgPerDay = emissionsKgPerDay;
      this.reservoirPressureBara = reservoirPressureBara;
    }

    /**
     * Returns the time.
     *
     * @return time in years
     */
    public double getTimeYears() {
      return timeYears;
    }

    /**
     * Returns the field rate.
     *
     * @return rate in Sm3/day
     */
    public double getRateSm3PerDay() {
      return rateSm3PerDay;
    }

    /**
     * Returns the revenue rate.
     *
     * @return revenue in currency per day
     */
    public double getRevenuePerDay() {
      return revenuePerDay;
    }

    /**
     * Returns the energy rate.
     *
     * @return energy in kWh per day
     */
    public double getEnergyKWhPerDay() {
      return energyKWhPerDay;
    }

    /**
     * Returns the emission rate.
     *
     * @return emissions in kg CO2 per day
     */
    public double getEmissionsKgPerDay() {
      return emissionsKgPerDay;
    }

    /**
     * Returns the average reservoir pressure.
     *
     * @return reservoir pressure in bara
     */
    public double getReservoirPressureBara() {
      return reservoirPressureBara;
    }
  }

  private final List<Point> points = new ArrayList<Point>();
  private double cumulativeProduction; // Sm3
  private double cumulativeRevenue; // currency

  /**
   * Adds a point to the profile.
   *
   * @param timeYears time in years
   * @param rateSm3PerDay field rate in Sm3/day
   * @param revenuePerDay revenue in currency per day
   * @param energyKWhPerDay energy in kWh per day
   * @param emissionsKgPerDay emissions in kg CO2 per day
   * @param reservoirPressureBara average reservoir pressure in bara
   */
  public void add(double timeYears, double rateSm3PerDay, double revenuePerDay,
      double energyKWhPerDay, double emissionsKgPerDay, double reservoirPressureBara) {
    points.add(new Point(timeYears, rateSm3PerDay, revenuePerDay, energyKWhPerDay,
        emissionsKgPerDay, reservoirPressureBara));
  }

  /**
   * Returns the profile points.
   *
   * @return list of points
   */
  public List<Point> getPoints() {
    return points;
  }

  /**
   * Returns the cumulative production over the horizon.
   *
   * @return cumulative production in Sm3
   */
  public double getCumulativeProduction() {
    return cumulativeProduction;
  }

  /**
   * Sets the cumulative production.
   *
   * @param cumulativeProduction cumulative production in Sm3
   */
  public void setCumulativeProduction(double cumulativeProduction) {
    this.cumulativeProduction = cumulativeProduction;
  }

  /**
   * Returns the cumulative revenue over the horizon.
   *
   * @return cumulative revenue in currency
   */
  public double getCumulativeRevenue() {
    return cumulativeRevenue;
  }

  /**
   * Sets the cumulative revenue.
   *
   * @param cumulativeRevenue cumulative revenue in currency
   */
  public void setCumulativeRevenue(double cumulativeRevenue) {
    this.cumulativeRevenue = cumulativeRevenue;
  }

  /**
   * Returns the peak field rate over the profile.
   *
   * @return peak rate in Sm3/day
   */
  public double getPeakRate() {
    double peak = 0.0;
    for (Point p : points) {
      peak = Math.max(peak, p.getRateSm3PerDay());
    }
    return peak;
  }
}
