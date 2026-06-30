package neqsim.util.unit;

/**
 * BaseUnit class.
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class BaseUnit implements Unit, neqsim.thermo.ThermodynamicConstantsInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Process value in given unit */
  protected double invalue;
  /** Unit of process value */
  protected String inunit;

  /**
   * Constructor for BaseUnit.
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   */
  public BaseUnit(double value, String unit) {
    Unit.validateUnitInput(unit, "unit");
    this.invalue = value;
    this.inunit = unit;
  }

  @Override
  public double getSIvalue() {
    throw new UnsupportedOperationException("Unimplemented method 'getSIvalue'");
  }

  /** {@inheritDoc} */
  @Override
  public double getValue(String toUnit) {
    throw new UnsupportedOperationException("Unimplemented method 'getValue'");
  }

  /**
   * <p>
   * Convert value from a specified unit to a specified unit.
   * </p>
   *
   * @param value a double
   * @param unit a {@link java.lang.String} object
   * @param toUnit a {@link java.lang.String} object
   * @return a double
   * @deprecated Use the static convert method on the concrete unit class instead (e.g., TemperatureUnit.convert,
   * LengthUnit.convert). This method delegates to the Unit interface's default getValue(double, String, String) which
   * uses reflection.
   */
  @Deprecated
  @Override
  public double getValue(double value, String unit, String toUnit) {
    return Unit.super.getValue(value, unit, toUnit);
  }
}
