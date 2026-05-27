package neqsim.process.equipment.reactor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Screening model for hydrogen-production catalyst deactivation.
 *
 * <p>
 * The model combines simple first-order damage rates for sulfur poisoning, chloride poisoning,
 * coking and thermal sintering. It is intended for early SMR, WGS and ammonia-cracking studies
 * where a reactor or {@link CatalystBed} needs a transparent activity factor over operating time.
 * </p>
 *
 * <p>
 * The constants are deliberately conservative order-of-magnitude defaults. Vendor-specific
 * catalysts should replace the defaults with measured rates before detailed design.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CatalystDeactivationKinetics implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double GAS_CONSTANT = 8.314462618;

  /** Catalyst families used in hydrogen production and carrier cracking service. */
  public enum CatalystFamily {
    /** Nickel reforming catalyst for SMR, pre-reforming and methanation side service. */
    NICKEL_REFORMING("Nickel reforming", 1.5e-4, 4.0e-5, 2.0e-4, 2.0e7, 220000.0, 923.15),
    /** Iron-chromium high-temperature shift catalyst. */
    IRON_CHROMIUM_HT_SHIFT("Iron-chromium HT shift", 3.0e-5, 2.0e-5, 1.0e-5, 5.0e6, 210000.0,
        673.15),
    /** Copper-zinc low-temperature shift catalyst. */
    COPPER_ZINC_LT_SHIFT("Copper-zinc LT shift", 8.0e-4, 2.5e-4, 5.0e-6, 1.0e5, 140000.0, 523.15),
    /** Ruthenium catalyst for ammonia cracking. */
    RUTHENIUM_AMMONIA_CRACKING("Ruthenium ammonia cracking", 4.0e-4, 1.0e-4, 5.0e-5, 1.0e7,
        200000.0, 823.15);

    private final String displayName;
    private final double sulfurCoefficientPerHourPerPpm;
    private final double chlorideCoefficientPerHourPerPpm;
    private final double cokingCoefficientPerHour;
    private final double sinteringPreExponentialPerHour;
    private final double sinteringActivationEnergyJPerMol;
    private final double sinteringOnsetTemperatureK;

    /**
     * Creates a catalyst-family default data set.
     *
     * @param displayName human-readable catalyst name
     * @param sulfurCoefficientPerHourPerPpm sulfur poisoning coefficient in 1/(h ppmv)
     * @param chlorideCoefficientPerHourPerPpm chloride poisoning coefficient in 1/(h ppmv)
     * @param cokingCoefficientPerHour coking damage coefficient in 1/h
     * @param sinteringPreExponentialPerHour sintering pre-exponential factor in 1/h
     * @param sinteringActivationEnergyJPerMol sintering activation energy in J/mol
     * @param sinteringOnsetTemperatureK sintering onset temperature in K
     */
    CatalystFamily(String displayName, double sulfurCoefficientPerHourPerPpm,
        double chlorideCoefficientPerHourPerPpm, double cokingCoefficientPerHour,
        double sinteringPreExponentialPerHour, double sinteringActivationEnergyJPerMol,
        double sinteringOnsetTemperatureK) {
      this.displayName = displayName;
      this.sulfurCoefficientPerHourPerPpm = sulfurCoefficientPerHourPerPpm;
      this.chlorideCoefficientPerHourPerPpm = chlorideCoefficientPerHourPerPpm;
      this.cokingCoefficientPerHour = cokingCoefficientPerHour;
      this.sinteringPreExponentialPerHour = sinteringPreExponentialPerHour;
      this.sinteringActivationEnergyJPerMol = sinteringActivationEnergyJPerMol;
      this.sinteringOnsetTemperatureK = sinteringOnsetTemperatureK;
    }

    /**
     * Gets a human-readable catalyst-family name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  private CatalystFamily catalystFamily = CatalystFamily.NICKEL_REFORMING;
  private double temperatureK = 973.15;
  private double operationHours = 0.0;
  private double sulfurPpmv = 0.0;
  private double chloridePpmv = 0.0;
  private double carbonPotential = 0.0;
  private double steamToCarbonRatio = 3.0;

  /**
   * Creates a catalyst deactivation model with nickel reforming defaults.
   */
  public CatalystDeactivationKinetics() {}

  /**
   * Creates a catalyst deactivation model for a catalyst family.
   *
   * @param catalystFamily catalyst family, not null
   */
  public CatalystDeactivationKinetics(CatalystFamily catalystFamily) {
    setCatalystFamily(catalystFamily);
  }

  /**
   * Sets the catalyst family.
   *
   * @param catalystFamily catalyst family, not null
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setCatalystFamily(CatalystFamily catalystFamily) {
    if (catalystFamily == null) {
      throw new IllegalArgumentException("catalystFamily cannot be null");
    }
    this.catalystFamily = catalystFamily;
    return this;
  }

  /**
   * Sets the catalyst-bed operating temperature.
   *
   * @param temperatureK temperature in K, must be greater than 0
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setTemperature(double temperatureK) {
    if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
      throw new IllegalArgumentException("temperatureK must be finite and greater than 0");
    }
    this.temperatureK = temperatureK;
    return this;
  }

  /**
   * Sets operating time.
   *
   * @param operationHours operating time in hours, must be non-negative
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setOperationHours(double operationHours) {
    validateNonNegative(operationHours, "operationHours");
    this.operationHours = operationHours;
    return this;
  }

  /**
   * Sets sulfur slip to the catalyst bed.
   *
   * @param sulfurPpmv sulfur compounds as H2S-equivalent ppmv, must be non-negative
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setSulfurPpmv(double sulfurPpmv) {
    validateNonNegative(sulfurPpmv, "sulfurPpmv");
    this.sulfurPpmv = sulfurPpmv;
    return this;
  }

  /**
   * Sets chloride slip to the catalyst bed.
   *
   * @param chloridePpmv chloride compounds as HCl-equivalent ppmv, must be non-negative
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setChloridePpmv(double chloridePpmv) {
    validateNonNegative(chloridePpmv, "chloridePpmv");
    this.chloridePpmv = chloridePpmv;
    return this;
  }

  /**
   * Sets a dimensionless carbon-formation driving force.
   *
   * @param carbonPotential carbon potential, where 0 is no coking drive and 1 is severe
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setCarbonPotential(double carbonPotential) {
    validateNonNegative(carbonPotential, "carbonPotential");
    this.carbonPotential = carbonPotential;
    return this;
  }

  /**
   * Sets steam-to-carbon ratio for coking suppression.
   *
   * @param steamToCarbonRatio steam-to-carbon molar ratio, must be non-negative
   * @return this deactivation model
   */
  public CatalystDeactivationKinetics setSteamToCarbonRatio(double steamToCarbonRatio) {
    validateNonNegative(steamToCarbonRatio, "steamToCarbonRatio");
    this.steamToCarbonRatio = steamToCarbonRatio;
    return this;
  }

  /**
   * Gets the sulfur poisoning damage rate.
   *
   * @return sulfur poisoning rate in 1/h
   */
  public double getSulfurPoisoningRatePerHour() {
    double temperatureFactor = Math.sqrt(getReferenceTemperatureK() / temperatureK);
    return catalystFamily.sulfurCoefficientPerHourPerPpm * sulfurPpmv * temperatureFactor;
  }

  /**
   * Gets the chloride poisoning damage rate.
   *
   * @return chloride poisoning rate in 1/h
   */
  public double getChloridePoisoningRatePerHour() {
    double temperatureFactor = Math.sqrt(getReferenceTemperatureK() / temperatureK);
    return catalystFamily.chlorideCoefficientPerHourPerPpm * chloridePpmv * temperatureFactor;
  }

  /**
   * Gets the coking damage rate.
   *
   * @return coking rate in 1/h
   */
  public double getCokingRatePerHour() {
    double steamProtection = Math.exp(-0.6 * Math.max(0.0, steamToCarbonRatio - 1.0));
    double lowSteamPenalty = steamToCarbonRatio < 2.0 ? 1.0 + (2.0 - steamToCarbonRatio) : 1.0;
    return catalystFamily.cokingCoefficientPerHour * carbonPotential * steamProtection
        * lowSteamPenalty;
  }

  /**
   * Gets the thermal sintering damage rate.
   *
   * @return thermal sintering rate in 1/h
   */
  public double getThermalSinteringRatePerHour() {
    if (temperatureK <= catalystFamily.sinteringOnsetTemperatureK) {
      return 0.0;
    }
    double arrhenius = catalystFamily.sinteringPreExponentialPerHour * Math
        .exp(-catalystFamily.sinteringActivationEnergyJPerMol / (GAS_CONSTANT * temperatureK));
    double severity = 1.0 + (temperatureK - catalystFamily.sinteringOnsetTemperatureK) / 100.0;
    return arrhenius * severity;
  }

  /**
   * Calculates the total first-order deactivation rate.
   *
   * @return total deactivation rate in 1/h
   */
  public double getTotalDeactivationRatePerHour() {
    return getSulfurPoisoningRatePerHour() + getChloridePoisoningRatePerHour()
        + getCokingRatePerHour() + getThermalSinteringRatePerHour();
  }

  /**
   * Calculates remaining catalyst activity after the configured operating time.
   *
   * @return catalyst activity factor in the range 0 to 1
   */
  public double calculateActivity() {
    double rate = getTotalDeactivationRatePerHour();
    double activity = Math.exp(-rate * operationHours);
    return Math.max(0.0, Math.min(1.0, activity));
  }

  /**
   * Applies the calculated activity to a CatalystBed instance.
   *
   * @param catalystBed catalyst bed to update, not null
   * @return applied activity factor
   */
  public double applyTo(CatalystBed catalystBed) {
    if (catalystBed == null) {
      throw new IllegalArgumentException("catalystBed cannot be null");
    }
    double activity = calculateActivity();
    catalystBed.setActivityFactor(activity);
    return activity;
  }

  /**
   * Estimates operating time until the catalyst reaches a target activity.
   *
   * @param activityThreshold target activity in the interval (0, 1]
   * @return time in hours, or positive infinity when no deactivation rate is present
   */
  public double estimateTimeToActivity(double activityThreshold) {
    if (!Double.isFinite(activityThreshold) || activityThreshold <= 0.0
        || activityThreshold > 1.0) {
      throw new IllegalArgumentException("activityThreshold must be in the interval (0, 1]");
    }
    double rate = getTotalDeactivationRatePerHour();
    if (rate <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return -Math.log(activityThreshold) / rate;
  }

  /**
   * Identifies the largest deactivation mechanism at the current conditions.
   *
   * @return mechanism name
   */
  public String getDominantMechanism() {
    Map<String, Double> rates = getRateBreakdown();
    String dominant = "none";
    double maxRate = 0.0;
    for (Map.Entry<String, Double> entry : rates.entrySet()) {
      if (entry.getValue() > maxRate) {
        dominant = entry.getKey();
        maxRate = entry.getValue();
      }
    }
    return dominant;
  }

  /**
   * Gets a rate breakdown for all deactivation mechanisms.
   *
   * @return ordered map of mechanism names to rates in 1/h
   */
  public Map<String, Double> getRateBreakdown() {
    Map<String, Double> rates = new LinkedHashMap<String, Double>();
    rates.put("sulfur_poisoning", getSulfurPoisoningRatePerHour());
    rates.put("chloride_poisoning", getChloridePoisoningRatePerHour());
    rates.put("coking", getCokingRatePerHour());
    rates.put("thermal_sintering", getThermalSinteringRatePerHour());
    return rates;
  }

  /**
   * Serializes the current calculation state and rates to JSON.
   *
   * @return pretty-printed JSON string
   */
  public String toJson() {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("catalystFamily", catalystFamily.name());
    data.put("displayName", catalystFamily.getDisplayName());
    data.put("temperatureK", temperatureK);
    data.put("operationHours", operationHours);
    data.put("sulfurPpmv", sulfurPpmv);
    data.put("chloridePpmv", chloridePpmv);
    data.put("carbonPotential", carbonPotential);
    data.put("steamToCarbonRatio", steamToCarbonRatio);
    data.put("ratesPerHour", getRateBreakdown());
    data.put("totalRatePerHour", getTotalDeactivationRatePerHour());
    data.put("activity", calculateActivity());
    data.put("dominantMechanism", getDominantMechanism());
    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
  }

  /**
   * Gets the configured catalyst family.
   *
   * @return catalyst family
   */
  public CatalystFamily getCatalystFamily() {
    return catalystFamily;
  }

  /**
   * Gets the configured temperature.
   *
   * @return temperature in K
   */
  public double getTemperatureK() {
    return temperatureK;
  }

  /**
   * Gets the configured operating time.
   *
   * @return operating time in hours
   */
  public double getOperationHours() {
    return operationHours;
  }

  /**
   * Gets sulfur concentration.
   *
   * @return sulfur concentration in ppmv
   */
  public double getSulfurPpmv() {
    return sulfurPpmv;
  }

  /**
   * Gets chloride concentration.
   *
   * @return chloride concentration in ppmv
   */
  public double getChloridePpmv() {
    return chloridePpmv;
  }

  /**
   * Gets carbon potential.
   *
   * @return carbon potential
   */
  public double getCarbonPotential() {
    return carbonPotential;
  }

  /**
   * Gets steam-to-carbon ratio.
   *
   * @return steam-to-carbon ratio
   */
  public double getSteamToCarbonRatio() {
    return steamToCarbonRatio;
  }

  /**
   * Returns a common reference temperature for poisoning damage normalization.
   *
   * @return reference temperature in K
   */
  private static double getReferenceTemperatureK() {
    return 673.15;
  }

  /**
   * Validates a finite non-negative input.
   *
   * @param value value to validate
   * @param name parameter name for diagnostics
   */
  private static void validateNonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and non-negative");
    }
  }
}
