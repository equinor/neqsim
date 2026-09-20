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
    validateAllowedUnit(unit);
    this.invalue = value;
    this.inunit = unit;
  }
}
