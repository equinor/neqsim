package neqsim.process.equipment.reactor;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Enzyme treatment reactor for bio-processing.
 *
 * <p>
 * Models an enzymatic hydrolysis or treatment process. This is a specialized
 * {@link StirredTankReactor} optimized for enzyme-catalyzed reactions, typically operating at mild
 * temperatures (30-60°C), near-neutral pH, and atmospheric pressure.
 * </p>
 *
 * <p>
 * Common applications:
 * </p>
 * <ul>
 * <li>Cellulose hydrolysis (cellulase enzymes)</li>
 * <li>Starch saccharification (amylase, glucoamylase)</li>
 * <li>Protein hydrolysis (protease enzymes)</li>
 * <li>Fat splitting (lipase enzymes)</li>
 * <li>Lactose hydrolysis (lactase)</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * EnzymeTreatment hydrolysis = new EnzymeTreatment("Saccharification", feedStream);
 * hydrolysis.setReactorTemperature(273.15 + 50.0); // 50 C, typical for cellulase
 * hydrolysis.setResidenceTime(72.0, "hr");
 * hydrolysis.setEnzymeLoading(20.0); // mg enzyme per g substrate
 * hydrolysis.setEnzymeType("cellulase");
 *
 * // Define the hydrolysis reaction
 * StoichiometricReaction celluloseHydrolysis = new StoichiometricReaction("CelluloseHydrolysis");
 * celluloseHydrolysis.addReactant("cellulose", 1.0);
 * celluloseHydrolysis.addProduct("glucose", 1.0);
 * celluloseHydrolysis.addProduct("water", -1.0);
 * celluloseHydrolysis.setLimitingReactant("cellulose");
 * celluloseHydrolysis.setConversion(0.80);
 *
 * hydrolysis.addReaction(celluloseHydrolysis);
 * hydrolysis.run();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class EnzymeTreatment extends StirredTankReactor {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Enzyme loading in mg enzyme per g substrate. */
  private double enzymeLoading = 10.0;

  /** Type/name of enzyme used. */
  private String enzymeType = "generic";

  /** Optimal pH for the enzyme. */
  private double optimalPH = 5.0;

  /** Optimal temperature for the enzyme in Kelvin. */
  private double optimalTemperatureK = 273.15 + 50.0;

  /** Temperature sensitivity: relative activity reduction per degree from optimal. */
  private double temperatureSensitivity = 0.02;

  /** Enzyme half-life at operating conditions in hours. */
  private double enzymeHalfLife = 48.0;

  /** Enzyme cost in $/kg. */
  private double enzymeCostPerKg = 10.0;

  /**
   * Constructor for EnzymeTreatment.
   *
   * @param name name of the enzyme treatment unit
   */
  public EnzymeTreatment(String name) {
    super(name);
    setIsothermal(true);
    setAgitatorPowerPerVolume(0.3); // gentle mixing for enzyme reactions
  }

  /**
   * Constructor for EnzymeTreatment with inlet stream.
   *
   * @param name name of the enzyme treatment unit
   * @param inletStream the feed stream
   */
  public EnzymeTreatment(String name, StreamInterface inletStream) {
    super(name, inletStream);
    setIsothermal(true);
    setAgitatorPowerPerVolume(0.3);
  }

  /**
   * Set the enzyme loading.
   *
   * @param loading enzyme loading in mg enzyme per g substrate
   */
  public void setEnzymeLoading(double loading) {
    this.enzymeLoading = loading;
  }

  /**
   * Get the enzyme loading.
   *
   * @return enzyme loading in mg/g
   */
  public double getEnzymeLoading() {
    return enzymeLoading;
  }

  /**
   * Set the enzyme type/name.
   *
   * @param type enzyme name or type (e.g., "cellulase", "amylase", "protease")
   */
  public void setEnzymeType(String type) {
    this.enzymeType = type;
  }

  /**
   * Get the enzyme type.
   *
   * @return enzyme type
   */
  public String getEnzymeType() {
    return enzymeType;
  }

  /**
   * Set the optimal pH for the enzyme.
   *
   * @param pH optimal pH
   */
  public void setOptimalPH(double pH) {
    this.optimalPH = pH;
  }

  /**
   * Get the optimal pH.
   *
   * @return optimal pH
   */
  public double getOptimalPH() {
    return optimalPH;
  }

  /**
   * Set the optimal temperature for the enzyme.
   *
   * @param temperatureK optimal temperature in Kelvin
   */
  public void setOptimalTemperature(double temperatureK) {
    this.optimalTemperatureK = temperatureK;
  }

  /**
   * Get the optimal temperature.
   *
   * @return optimal temperature in Kelvin
   */
  public double getOptimalTemperature() {
    return optimalTemperatureK;
  }

  /**
   * Set the enzyme half-life at operating conditions.
   *
   * @param hours half-life in hours
   */
  public void setEnzymeHalfLife(double hours) {
    this.enzymeHalfLife = hours;
  }

  /**
   * Get the enzyme half-life.
   *
   * @return half-life in hours
   */
  public double getEnzymeHalfLife() {
    return enzymeHalfLife;
  }

  /**
   * Set the enzyme cost.
   *
   * @param costPerKg cost in $/kg enzyme
   */
  public void setEnzymeCostPerKg(double costPerKg) {
    this.enzymeCostPerKg = costPerKg;
  }

  /**
   * Get the enzyme cost.
   *
   * @return cost in $/kg
   */
  public double getEnzymeCostPerKg() {
    return enzymeCostPerKg;
  }

  /**
   * Calculate the relative enzyme activity based on operating temperature deviation from optimal.
   *
   * @return relative activity (0-1), where 1.0 is at optimal temperature
   */
  public double getRelativeActivity() {
    if (Double.isNaN(getReactorTemperature())) {
      return 1.0;
    }
    double deltaT = Math.abs(getReactorTemperature() - optimalTemperatureK);
    return Math.max(0.0, 1.0 - temperatureSensitivity * deltaT);
  }

  /**
   * Estimate enzyme consumption in kg/hr based on loading and feed rate.
   *
   * @return enzyme consumption in kg/hr
   */
  public double getEnzymeConsumption() {
    if (inStream == null) {
      return 0.0;
    }
    try {
      double feedMassFlowKgHr = inStream.getFlowRate("kg/hr");
      return enzymeLoading * feedMassFlowKgHr / 1.0e6; // mg/g to kg/kg
    } catch (Exception ex) {
      return 0.0;
    }
  }

  /**
   * Estimate hourly enzyme cost.
   *
   * @return enzyme cost in $/hr
   */
  public double getEnzymeCostPerHour() {
    return getEnzymeConsumption() * enzymeCostPerKg;
  }
}
