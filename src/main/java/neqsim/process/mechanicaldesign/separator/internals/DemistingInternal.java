package neqsim.process.mechanicaldesign.separator.internals;

import java.io.Serializable;
import neqsim.process.equipment.separator.Separator;

/**
 * <p>
 * DemistingInternal class.
 * </p>
 * 
 * Represents a demisting internal (without drainage) for separators. The
 * DemistingInternal reduces
 * liquid carry-over by providing a surface area where droplets can coalesce and
 * settle.
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
 * @see neqsim.process.mechanicaldesign.separator.internals.DemistingInternalWithDrainage
 * @see neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign
 */
public class DemistingInternal implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Internal surface area in m². If there are several cyclones, this is cyclone
   * area * number
   */
  protected double area;

  /** Euler number (dimensionless pressure drop coefficient). */
  protected double euNumber;

  protected int number;

  /** Reference to the parent separator for accessing inlet properties. */
  protected transient Separator separator;

  /**
   * Constructor for DemistingInternal.
   *
   * @param area     the internal surface area in m²
   * @param euNumber the Euler number for pressure drop calculation
   */
  public DemistingInternal(double area, double euNumber) {
    this.area = area;
    this.euNumber = euNumber;
    this.number = 1;
  }

  /**
   * Constructor for DemistingInternal.
   *
   * @param area     the internal surface area in m²
   * @param euNumber the Euler number for pressure drop calculation
   * @param number   number of internals
   */
  public DemistingInternal(int number, double area, double euNumber) {
    this.area = area;
    this.euNumber = euNumber;
    this.number = number;
  }

  /**
   * Get the internal surface area.
   *
   * @return area in m²
   */
  public double getArea() {
    return area;
  }

  /**
   * Set the internal surface area.
   *
   * @param area the area in m² (must be positive)
   * @throws IllegalArgumentException if area is not positive
   */
  public void setArea(double area) {
    if (area <= 0) {
      throw new IllegalArgumentException("Internal area must be positive");
    }
    this.area = area;
  }

  /**
   * Get the Euler number (rho v^2).
   *
   * @return Euler number (dimensionless)
   */
  public double getEuNumber() {
    return euNumber;
  }

  /**
   * Set the Euler number (rho v^2).
   *
   * @param euNumber the Euler number (must be non-negative)
   * @throws IllegalArgumentException if euNumber is negative
   */
  public void setEuNumber(double euNumber) {
    if (euNumber < 0) {
      throw new IllegalArgumentException("Euler number cannot be negative");
    }
    this.euNumber = euNumber;
  }

  /**
   * Get the Pressure Drop Coefficient (1/2 rho v^2).
   *
   * @return Pressure Drop Coefficient (dimensionless)
   */
  public double getPressureDropCoefficient() {
    return euNumber * 2.0;
  }

  /**
   * Set the Pressure Drop Coefficient (1/2 rho v^2).
   *
   * @param pressureDropCoefficient the pressure Drop Coefficient
   */
  public void setPressureDropCoefficient(double pressureDropCoefficient) {
    this.euNumber = pressureDropCoefficient / 2.0;
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
   * Calculate the gas velocity through the deisting internal.
   * 
   * Velocity is calculated as: v = Q / A where Q is the volumetric gas flow rate
   * and A is the
   * internal area.
   *
   * @param volumetricFlowRate volumetric gas flow rate in m³/s
   * @return gas velocity in m/s
   */
  public double calcGasVelocity(double volumetricFlowRate) {
    if (area == 0) {
      throw new IllegalArgumentException("Internal area cannot be zero");
    }
    return volumetricFlowRate / area;
  }

  /**
   * Calculate the pressure drop across the deisting internal.
   * 
   * Pressure drop is calculated using the Euler number: Δp = Eu * ρ * v² / 2
   * where Eu is the Euler
   * number, ρ is gas density, and v is gas velocity.
   *
   * @param gasDensity  gas density in kg/m³
   * @param gasVelocity gas velocity in m/s
   * @return pressure drop in Pa
   */
  public double calcPressureDrop(double gasDensity, double gasVelocity) {
    return euNumber * gasDensity * gasVelocity * gasVelocity;
  }

  /**
   * Calculate the liquid carry-over for this internal.
   * 
   * Liquid carry-over is the mass of liquid droplets that pass through the
   * internal per unit time.
   * This is a simplified model based on the Souders-Brown equation modified for
   * deisting internals.
   * 
   * Carry-over is reduced by the internal efficiency factor which increases with
   * area.
   *
   * @return liquid carry-over reduction factor (0 to 1, where 1 means no
   *         reduction)
   */
  public double calcLiquidCarryOver() {
    if (separator == null) {
      return 0.0;
    }

    double inletLiquidContent = separator.getInletLiquidContent();

    // Carry-over reduction increases with internal area
    // This is a simplified model: carry-over factor decreases exponentially with
    // area
    // carryOverFactor = exp(-k * area) where k is a calibration constant
    double calibrationConstant = 0.5; // Can be adjusted based on experimental data
    double carryOverFactor = Math.exp(-calibrationConstant * area);

    return carryOverFactor * inletLiquidContent;
  }

  /**
   * Calculate the separation efficiency of this internal.
   * 
   * Efficiency is calculated as 1 - (carryOverFactor), representing the fraction
   * of liquid that is
   * successfully separated by this internal.
   *
   * @return separation efficiency (0 to 1)
   */
  public double calcEfficiency() {
    // Efficiency increases with internal area
    // This is a simplified model: efficiency = 1 - exp(-k * area)
    double calibrationConstant = 0.5; // Can be adjusted based on experimental data
    return 1.0 - Math.exp(-calibrationConstant * area);
  }

  @Override
  public String toString() {
    return "DemistingInternal [area=" + area + " m², euNumber=" + euNumber + "]";
  }
}
