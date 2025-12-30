package neqsim.process.mechanicaldesign.separator.primaryseparation;

import java.io.Serializable;
import neqsim.process.equipment.separator.Separator;

/**
 * <p>
 * PrimarySeparation class.
 * </p>
 * 
 * Base class for primary separation devices in separators. Primary separation
 * includes inlet
 * devices that perform initial liquid-gas separation through momentum effects.
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
 * @see neqsim.process.mechanicaldesign.separator.primaryseparation.InletVane
 * @see neqsim.process.mechanicaldesign.separator.primaryseparation.InletVaneWithMeshpad
 * @see neqsim.process.mechanicaldesign.separator.primaryseparation.InletCyclones
 */
public class PrimarySeparation implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Name of the primary separation device. */
  protected String name;

  /** Inlet nozzle diameter in m. */
  protected double inletNozzleDiameter;

  /** Reference to the parent separator for accessing inlet properties. */
  protected transient Separator separator;

  /**
   * Constructor for PrimarySeparation.
   *
   * @param name                the name of the primary separation device
   * @param inletNozzleDiameter the inlet nozzle diameter in m
   */
  public PrimarySeparation(String name, double inletNozzleDiameter) {
    this.name = name;
    this.inletNozzleDiameter = inletNozzleDiameter;
  }

  /**
   * Get the name of the primary separation device.
   *
   * @return device name
   */
  public String getName() {
    return name;
  }

  /**
   * Set the name of the primary separation device.
   *
   * @param name the device name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get the inlet nozzle diameter.
   *
   * @return nozzle diameter in m
   */
  public double getInletNozzleDiameter() {
    return inletNozzleDiameter;
  }

  /**
   * Set the inlet nozzle diameter.
   *
   * @param inletNozzleDiameter the nozzle diameter in m (must be positive)
   * @throws IllegalArgumentException if diameter is not positive
   */
  public void setInletNozzleDiameter(double inletNozzleDiameter) {
    if (inletNozzleDiameter <= 0) {
      throw new IllegalArgumentException("Inlet nozzle diameter must be positive");
    }
    this.inletNozzleDiameter = inletNozzleDiameter;
  }

  /**
   * Set the reference to the parent separator.
   *
   * @param separator the parent separator
   */
  public void setSeparator(Separator separator) {
    this.separator = separator;
  }

  /**
   * Get the reference to the parent separator.
   *
   * @return the parent separator
   */
  public Separator getSeparator() {
    return separator;
  }

  /**
   * Calculate inlet nozzle momentum.
   * 
   * Momentum is calculated as: M = ρ * v * A where ρ is density, v is velocity,
   * and A is nozzle
   * area.
   *
   * @param density       fluid density in kg/m³
   * @param inletVelocity inlet velocity in m/s
   * @return nozzle momentum in kg·m/s²
   */
  public double calcInletNozzleMomentum(double density, double inletVelocity) {
    double nozzleArea = Math.PI * inletNozzleDiameter * inletNozzleDiameter / 4.0;
    double massFlowRate = density * inletVelocity * nozzleArea;
    return massFlowRate * inletVelocity;
  }

  /**
   * Calculate liquid carry-over for this primary separation device.
   * 
   * This is a base implementation that can be overridden by subclasses. Accesses
   * inlet properties
   * from the parent separator.
   *
   * @return liquid carry-over (mass fraction)
   */
  public double calcLiquidCarryOver() {
    // Base implementation: assumes some carry-over proportional to velocity
    // This can be overridden by subclasses with specific correlations
    double inletVelocity = separator != null ? separator.getInletGasVelocity() : 0.0;
    double inletLiquidContent = separator != null ? separator.getInletLiquidContent() : 0.0;
    double carryOverFactor = Math.min(1.0, inletVelocity / 10.0);
    return carryOverFactor * inletLiquidContent;
  }

  @Override
  public String toString() {
    return "PrimarySeparation [name=" + name + ", nozzleDiameter=" + inletNozzleDiameter + " m]";
  }
}
