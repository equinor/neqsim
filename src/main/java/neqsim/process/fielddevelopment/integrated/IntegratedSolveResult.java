package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;
import java.util.Map;

/**
 * Result of a single steady-state solve of an {@link IntegratedProductionModel}.
 *
 * @author NeqSim
 * @version 1.0
 * @see IntegratedProductionModel#solve()
 */
public class IntegratedSolveResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final boolean converged;
  private final int iterations;
  private final double fieldRate; // Sm3/day
  private final Map<String, Double> wellRates; // Sm3/day
  private final Map<String, Double> nodePressures; // bara
  private final double revenue; // currency/day
  private final double energyKWhPerDay; // kWh/day
  private final double emissionsKgPerDay; // kg CO2/day
  private final String method;

  /**
   * Creates an integrated solve result.
   *
   * @param converged whether the network solve converged
   * @param iterations solver iteration count
   * @param fieldRate total field export rate in Sm3/day
   * @param wellRates per-well rate in Sm3/day keyed by well name
   * @param nodePressures node pressures in bara keyed by node name
   * @param revenue revenue rate in currency per day
   * @param energyKWhPerDay processing/compression energy in kWh per day
   * @param emissionsKgPerDay CO2 emissions in kg per day
   * @param method solver method that produced the result
   */
  public IntegratedSolveResult(boolean converged, int iterations, double fieldRate, Map<String, Double> wellRates,
      Map<String, Double> nodePressures, double revenue, double energyKWhPerDay, double emissionsKgPerDay,
      String method) {
    this.converged = converged;
    this.iterations = iterations;
    this.fieldRate = fieldRate;
    this.wellRates = wellRates;
    this.nodePressures = nodePressures;
    this.revenue = revenue;
    this.energyKWhPerDay = energyKWhPerDay;
    this.emissionsKgPerDay = emissionsKgPerDay;
    this.method = method;
  }

  /**
   * Returns whether the solve converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Returns the solver iteration count.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Returns the total field export rate.
   *
   * @return field rate in Sm3/day
   */
  public double getFieldRate() {
    return fieldRate;
  }

  /**
   * Returns the per-well rates.
   *
   * @return map of well name to rate in Sm3/day
   */
  public Map<String, Double> getWellRates() {
    return wellRates;
  }

  /**
   * Returns the solved node pressures.
   *
   * @return map of node name to pressure in bara
   */
  public Map<String, Double> getNodePressures() {
    return nodePressures;
  }

  /**
   * Returns the revenue rate.
   *
   * @return revenue in currency per day
   */
  public double getRevenue() {
    return revenue;
  }

  /**
   * Returns the processing/compression energy rate.
   *
   * @return energy in kWh per day
   */
  public double getEnergyKWhPerDay() {
    return energyKWhPerDay;
  }

  /**
   * Returns the CO2 emission rate.
   *
   * @return emissions in kg per day
   */
  public double getEmissionsKgPerDay() {
    return emissionsKgPerDay;
  }

  /**
   * Returns the solver method used.
   *
   * @return method name
   */
  public String getMethod() {
    return method;
  }
}
