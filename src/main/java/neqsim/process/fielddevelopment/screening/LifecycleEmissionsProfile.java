package neqsim.process.fielddevelopment.screening;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Time-series emissions profile for a development concept.
 *
 * @author ESOL
 * @version 1.0
 */
public final class LifecycleEmissionsProfile implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final List<AnnualEmissions> annualEmissions;

  /**
   * Creates a lifecycle emissions profile.
   *
   * @param annualEmissions annual emissions records
   */
  public LifecycleEmissionsProfile(List<AnnualEmissions> annualEmissions) {
    if (annualEmissions == null) {
      this.annualEmissions = Collections.emptyList();
    } else {
      this.annualEmissions =
          Collections.unmodifiableList(new ArrayList<AnnualEmissions>(annualEmissions));
    }
  }

  /**
   * Creates an empty lifecycle emissions profile.
   *
   * @return empty profile
   */
  public static LifecycleEmissionsProfile empty() {
    return new LifecycleEmissionsProfile(Collections.<AnnualEmissions>emptyList());
  }

  /**
   * Gets the annual emissions records.
   *
   * @return immutable annual emissions records
   */
  public List<AnnualEmissions> getAnnualEmissions() {
    return annualEmissions;
  }

  /**
   * Gets total lifecycle emissions.
   *
   * @return total emissions in tonnes CO2e
   */
  public double getTotalLifecycleEmissionsTonnes() {
    double total = 0.0;
    for (AnnualEmissions annual : annualEmissions) {
      total += annual.getTotalEmissionsTonnes();
    }
    return total;
  }

  /**
   * Gets peak annual emissions.
   *
   * @return peak annual emissions in tonnes CO2e per year
   */
  public double getPeakAnnualEmissionsTonnes() {
    double peak = 0.0;
    for (AnnualEmissions annual : annualEmissions) {
      peak = Math.max(peak, annual.getTotalEmissionsTonnes());
    }
    return peak;
  }

  /**
   * Gets average emissions intensity.
   *
   * @return production-weighted intensity in kg CO2e per boe
   */
  public double getAverageIntensityKgCO2PerBoe() {
    double totalEmissionsKg = getTotalLifecycleEmissionsTonnes() * 1000.0;
    double totalBoe = 0.0;
    for (AnnualEmissions annual : annualEmissions) {
      totalBoe += annual.getProductionBoe();
    }
    return totalBoe > 0.0 ? totalEmissionsKg / totalBoe : 0.0;
  }

  /**
   * Checks whether the profile has annual data.
   *
   * @return true if one or more annual records exist
   */
  public boolean hasData() {
    return !annualEmissions.isEmpty();
  }

  /**
   * One annual lifecycle-emissions record.
   */
  public static final class AnnualEmissions implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final int year;
    private final double production;
    private final double productionBoe;
    private final double loadFactor;
    private final double powerMw;
    private final String powerSource;
    private final double powerEmissionsTonnes;
    private final double flaringEmissionsTonnes;
    private final double fugitiveEmissionsTonnes;
    private final double ventedCo2Tonnes;
    private final double totalEmissionsTonnes;

    /**
     * Creates an annual emissions record.
     *
     * @param year calendar year
     * @param production annual production in the source profile unit
     * @param productionBoe annual production in boe
     * @param loadFactor production load factor from zero to one
     * @param powerMw average power demand in MW
     * @param powerSource power source description
     * @param powerEmissionsTonnes power emissions in tonnes CO2e
     * @param flaringEmissionsTonnes flaring emissions in tonnes CO2e
     * @param fugitiveEmissionsTonnes fugitive emissions in tonnes CO2e
     * @param ventedCo2Tonnes vented CO2 in tonnes
     */
    public AnnualEmissions(int year, double production, double productionBoe, double loadFactor,
        double powerMw, String powerSource, double powerEmissionsTonnes,
        double flaringEmissionsTonnes, double fugitiveEmissionsTonnes, double ventedCo2Tonnes) {
      this.year = year;
      this.production = production;
      this.productionBoe = productionBoe;
      this.loadFactor = loadFactor;
      this.powerMw = powerMw;
      this.powerSource = powerSource == null ? "UNKNOWN" : powerSource;
      this.powerEmissionsTonnes = powerEmissionsTonnes;
      this.flaringEmissionsTonnes = flaringEmissionsTonnes;
      this.fugitiveEmissionsTonnes = fugitiveEmissionsTonnes;
      this.ventedCo2Tonnes = ventedCo2Tonnes;
      this.totalEmissionsTonnes =
          powerEmissionsTonnes + flaringEmissionsTonnes + fugitiveEmissionsTonnes + ventedCo2Tonnes;
    }

    /**
     * Gets the year.
     *
     * @return calendar year
     */
    public int getYear() {
      return year;
    }

    /**
     * Gets source-unit production.
     *
     * @return annual production in the source profile unit
     */
    public double getProduction() {
      return production;
    }

    /**
     * Gets BOE production.
     *
     * @return annual production in boe
     */
    public double getProductionBoe() {
      return productionBoe;
    }

    /**
     * Gets load factor.
     *
     * @return load factor from zero to one
     */
    public double getLoadFactor() {
      return loadFactor;
    }

    /**
     * Gets power demand.
     *
     * @return average power demand in MW
     */
    public double getPowerMw() {
      return powerMw;
    }

    /**
     * Gets power source.
     *
     * @return power source description
     */
    public String getPowerSource() {
      return powerSource;
    }

    /**
     * Gets power emissions.
     *
     * @return power emissions in tonnes CO2e
     */
    public double getPowerEmissionsTonnes() {
      return powerEmissionsTonnes;
    }

    /**
     * Gets flaring emissions.
     *
     * @return flaring emissions in tonnes CO2e
     */
    public double getFlaringEmissionsTonnes() {
      return flaringEmissionsTonnes;
    }

    /**
     * Gets fugitive emissions.
     *
     * @return fugitive emissions in tonnes CO2e
     */
    public double getFugitiveEmissionsTonnes() {
      return fugitiveEmissionsTonnes;
    }

    /**
     * Gets vented CO2.
     *
     * @return vented CO2 in tonnes
     */
    public double getVentedCo2Tonnes() {
      return ventedCo2Tonnes;
    }

    /**
     * Gets total emissions.
     *
     * @return total emissions in tonnes CO2e
     */
    public double getTotalEmissionsTonnes() {
      return totalEmissionsTonnes;
    }

    /**
     * Gets emissions intensity.
     *
     * @return intensity in kg CO2e per boe
     */
    public double getIntensityKgCO2PerBoe() {
      return productionBoe > 0.0 ? totalEmissionsTonnes * 1000.0 / productionBoe : 0.0;
    }
  }
}
