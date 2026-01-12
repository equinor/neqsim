package neqsim.process.mechanicaldesign.separator.primaryseparation;

/**
 * <p>
 * InletCyclones class.
 * </p>
 * 
 * Represents an inlet cyclone primary separation device. Inlet cyclones use
 * centrifugal force to
 * separate liquid droplets from the gas stream. This is a more aggressive
 * primary separation
 * compared to vanes.
 *
 * <p>
 * For detailed documentation on separator internals and carry-over
 * calculations, see:
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/separators_and_internals.md">
 * Separators and Internals Wiki</a> and
 * <a href=
 * "https://github.com/equinor/neqsim/blob/master/docs/wiki/carryover_calculations.md">
 * Carry-Over Calculations Wiki</a>
 * </p>
 *
 * @author User
 * @version 1.0
 * @see neqsim.process.mechanicaldesign.separator.primaryseparation.PrimarySeparation
 */
public class InletCyclones extends PrimarySeparation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Number of cyclones in parallel. */
  private int numberOfCyclones;

  /** Cyclone diameter in m. */
  private double cycloneDiameter;

  /**
   * Constructor for InletCyclones.
   *
   * @param name                the name of the inlet cyclone system
   * @param inletNozzleDiameter the inlet nozzle diameter in m
   * @param numberOfCyclones    the number of cyclones in parallel
   * @param cycloneDiameter     the diameter of each cyclone in m
   */
  public InletCyclones(String name, double inletNozzleDiameter, int numberOfCyclones,
      double cycloneDiameter) {
    super(name, inletNozzleDiameter);
    this.numberOfCyclones = numberOfCyclones;
    this.cycloneDiameter = cycloneDiameter;
  }

  /**
   * Get the number of cyclones.
   *
   * @return number of cyclones
   */
  public int getNumberOfCyclones() {
    return numberOfCyclones;
  }

  /**
   * Set the number of cyclones.
   *
   * @param numberOfCyclones the number of cyclones (must be at least 1)
   * @throws IllegalArgumentException if number of cyclones is less than 1
   */
  public void setNumberOfCyclones(int numberOfCyclones) {
    if (numberOfCyclones < 1) {
      throw new IllegalArgumentException("Number of cyclones must be at least 1");
    }
    this.numberOfCyclones = numberOfCyclones;
  }

  /**
   * Get the cyclone diameter.
   *
   * @return diameter in m
   */
  public double getCycloneDiameter() {
    return cycloneDiameter;
  }

  /**
   * Set the cyclone diameter.
   *
   * @param cycloneDiameter the diameter in m (must be positive)
   * @throws IllegalArgumentException if diameter is not positive
   */
  public void setCycloneDiameter(double cycloneDiameter) {
    if (cycloneDiameter <= 0) {
      throw new IllegalArgumentException("Cyclone diameter must be positive");
    }
    this.cycloneDiameter = cycloneDiameter;
  }

  /**
   * Calculate separation efficiency based on cyclone geometry.
   * 
   * Uses the Stokes number and swirl intensity to estimate separation efficiency.
   *
   * @param gasDensity      gas density in kg/m³
   * @param liquidDensity   liquid density in kg/m³
   * @param inletVelocity   inlet velocity in m/s
   * @param liquidViscosity liquid viscosity in Pa·s
   * @return separation efficiency (0 to 1)
   */
  public double calcSeparationEfficiency(double gasDensity, double liquidDensity,
      double inletVelocity, double liquidViscosity) {

    // Swirl intensity: higher velocity in cyclone increases separation efficiency
    double swirl = inletVelocity * Math.PI * cycloneDiameter;

    // Reference swirl for 100% efficiency
    double referenceSwirl = 50.0; // m²/s

    // Efficiency increases with swirl but levels off at 1.0
    double efficiencyFromSwirl = Math.min(swirl / referenceSwirl, 1.0);

    // Density ratio effect (larger density difference improves separation)
    double densityRatio = (liquidDensity - gasDensity) / liquidDensity;

    // Combined efficiency
    return efficiencyFromSwirl * densityRatio;
  }

  /**
   * Calculate liquid carry-over for inlet cyclones.
   * 
   * Cyclones are very effective at removing liquid droplets. Carry-over depends
   * on the separation
   * efficiency and the number of cyclones.
   *
   * @return liquid carry-over (mass fraction)
   */
  @Override
  public double calcLiquidCarryOver() {
    if (separator == null) {
      return 0.0;
    }

    double inletVelocity = separator.getInletGasVelocity();
    double inletLiquidContent = separator.getInletLiquidContent();

    // Estimate separation efficiency from cyclone properties
    // Higher velocity and more cyclones = better separation
    double cycloneSeparationEfficiency = Math.min((inletVelocity / 15.0) * (numberOfCyclones / 4.0), 1.0);

    // Carry-over reduced by separation efficiency
    // Cyclones are very effective, so base carry-over is low
    double carryOverFactor = (1.0 - 0.7 * cycloneSeparationEfficiency);

    return carryOverFactor * inletLiquidContent;
  }

  @Override
  public String toString() {
    return "InletCyclones [name=" + name + ", nozzleDiameter=" + inletNozzleDiameter + " m, "
        + "numberOfCyclones=" + numberOfCyclones + ", cycloneDiameter=" + cycloneDiameter + " m]";
  }
}
