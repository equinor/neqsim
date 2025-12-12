package neqsim.process.mechanicaldesign.separator.internals;

/**
 * <p>
 * DemistingInternalWithDrainage class.
 * </p>
 * 
 * Extends DemistingInternal to include drainage pipe functionality. Drainage pipes allow separated
 * liquid to be effectively removed from the deisting internal, improving separation performance and
 * reducing liquid carry-over.
 *
 * @author User
 * @version 1.0
 */
public class DemistingInternalWithDrainage extends DemistingInternal {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Whether drainage pipes are installed. */
  private boolean hasDrainagePipes;

  /** Drainage efficiency factor (0 to 1, where 1 is perfect drainage). */
  private double drainageEfficiency;

  /**
   * Constructor for DemistingInternalWithDrainage.
   *
   * @param area the internal surface area in m²
   * @param euNumber the Euler number for pressure drop calculation
   */
  public DemistingInternalWithDrainage(double area, double euNumber) {
    super(area, euNumber);
    this.hasDrainagePipes = true;
    this.drainageEfficiency = 0.9; // Default 90% drainage efficiency
  }

  /**
   * Constructor for DemistingInternalWithDrainage with custom drainage efficiency.
   *
   * @param area the internal surface area in m²
   * @param euNumber the Euler number for pressure drop calculation
   * @param drainageEfficiency drainage efficiency factor (0 to 1)
   */
  public DemistingInternalWithDrainage(double area, double euNumber, double drainageEfficiency) {
    super(area, euNumber);
    this.hasDrainagePipes = true;
    this.drainageEfficiency = drainageEfficiency;
  }

  /**
   * Get whether drainage pipes are present.
   *
   * @return true if drainage pipes are installed
   */
  public boolean hasDrainagePipes() {
    return hasDrainagePipes;
  }

  /**
   * Get the drainage efficiency factor.
   *
   * @return drainage efficiency (0 to 1)
   */
  public double getDrainageEfficiency() {
    return drainageEfficiency;
  }

  /**
   * Set the drainage efficiency factor.
   *
   * @param drainageEfficiency the drainage efficiency (0 to 1)
   */
  public void setDrainageEfficiency(double drainageEfficiency) {
    if (drainageEfficiency < 0 || drainageEfficiency > 1) {
      throw new IllegalArgumentException("Drainage efficiency must be between 0 and 1");
    }
    this.drainageEfficiency = drainageEfficiency;
  }

  /**
   * Calculate the liquid carry-over with drainage efficiency improvement.
   * 
   * The drainage pipes reduce carry-over by the drainage efficiency factor. Carry-over with
   * drainage = base carry-over * (1 - drainage efficiency)
   *
   * @return liquid carry-over with drainage reduction
   */
  @Override
  public double calcLiquidCarryOver() {
    // Get base carry-over from parent class
    double baseCarryOver = super.calcLiquidCarryOver();

    // Apply drainage efficiency reduction
    double carryOverWithDrainage = baseCarryOver * (1.0 - drainageEfficiency);

    return carryOverWithDrainage;
  }

  /**
   * Calculate the liquid carry-over with explicit parameters (legacy support for testing).
   *
   * @param gasVelocity gas velocity in m/s
   * @param gasDensity gas density in kg/m³
   * @param liquidDensity liquid density in kg/m³
   * @param inletLiquidContent inlet liquid content (mass fraction)
   * @return liquid carry-over with drainage reduction
   * @deprecated Use {@link #calcLiquidCarryOver()} instead
   */
  @Deprecated
  @Override
  public double calcLiquidCarryOver(double gasVelocity, double gasDensity, double liquidDensity,
      double inletLiquidContent) {
    if (separator != null) {
      return calcLiquidCarryOver();
    }

    // Fallback for testing without separator reference
    // Get base carry-over from parent class
    double baseCarryOver =
        super.calcLiquidCarryOver(gasVelocity, gasDensity, liquidDensity, inletLiquidContent);

    // Apply drainage efficiency reduction
    double carryOverWithDrainage = baseCarryOver * (1.0 - drainageEfficiency);

    return carryOverWithDrainage;
  }

  @Override
  public String toString() {
    return "DemistingInternalWithDrainage [area=" + area + " m², euNumber=" + euNumber
        + ", drainageEfficiency=" + drainageEfficiency + "]";
  }
}
