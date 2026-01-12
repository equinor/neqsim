package neqsim.process.mechanicaldesign.separator.primaryseparation;

/**
 * <p>
 * InletVane class.
 * </p>
 * 
 * Represents a basic inlet vane primary separation device. The inlet vane
 * redirects the inlet
 * stream and separates liquid droplets through momentum.
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
 * @see neqsim.process.mechanicaldesign.separator.primaryseparation.InletVaneWithMeshpad
 */
public class InletVane extends PrimarySeparation {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Geometrical expansion ratio: vanesOpenArea / nozzleArea. */
  private double geometricalExpansionRatio;

  /**
   * Constructor for InletVane.
   *
   * @param name                      the name of the inlet vane
   * @param inletNozzleDiameter       the inlet nozzle diameter in m
   * @param geometricalExpansionRatio the ratio of vane open area to nozzle area
   */
  public InletVane(String name, double inletNozzleDiameter, double geometricalExpansionRatio) {
    super(name, inletNozzleDiameter);
    this.geometricalExpansionRatio = geometricalExpansionRatio;
  }

  /**
   * Get the geometrical expansion ratio of the vane.
   *
   * @return expansion ratio (vane open area / nozzle area)
   */
  public double getGeometricalExpansionRatio() {
    return geometricalExpansionRatio;
  }

  /**
   * Set the geometrical expansion ratio of the vane.
   *
   * @param geometricalExpansionRatio the expansion ratio (vane open area / nozzle
   *                                  area, must be
   *                                  positive)
   * @throws IllegalArgumentException if expansion ratio is not positive
   */
  public void setGeometricalExpansionRatio(double geometricalExpansionRatio) {
    if (geometricalExpansionRatio <= 0) {
      throw new IllegalArgumentException("Expansion ratio must be positive");
    }
    this.geometricalExpansionRatio = geometricalExpansionRatio;
  }

  /**
   * Calculate liquid carry-over for inlet vane.
   * 
   * [PLACEHOLDER] Simple correlation based on inlet velocity and expansion ratio.
   * Better separation
   * (higher expansion ratio) reduces carry-over. This should be validated against
   * experimental
   * data.
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

    // Expansion ratio efficiency: higher ratio means better separation
    double expansionEfficiency = Math.min(geometricalExpansionRatio / 5.0, 1.0);

    // Velocity effect: higher velocity increases carry-over
    double velocityFactor = Math.min(1.0, inletVelocity / 10.0);

    // Combined effect
    double carryOverFactor = velocityFactor * (1.0 - 0.5 * expansionEfficiency);

    return carryOverFactor * inletLiquidContent;
  }

  @Override
  public String toString() {
    return "InletVane [name=" + name + ", nozzleDiameter=" + inletNozzleDiameter + " m, "
        + ", expansionRatio=" + geometricalExpansionRatio + "]";
  }
}
