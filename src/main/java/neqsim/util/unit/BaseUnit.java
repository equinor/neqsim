package neqsim.util.unit;

/**
 * <p>
 * BaseUnit class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 */
public abstract class BaseUnit implements Unit, neqsim.thermo.ThermodynamicConstantsInterface {
  private static final long serialVersionUID = 1000;

  /** Process value in SI units */
  protected double SIvalue = 0.0;

  /** Process value in given unit */
  protected double invalue = 0.0;
  /** Unit of process value */
  protected String inunit = null;

  /** Conversion factor */
  protected double factor = 1.0;

  /**
   * <p>
   * Constructor for BaseUnit.
   * </p>
   *
   * @param value a double
   * @param name a {@link java.lang.String} object
   */
  public BaseUnit(double value, String name) {
    this.invalue = value;
    this.inunit = name;
  }

  /** {@inheritDoc} */
  @Override
  public double getSIvalue() {
    return SIvalue;
  }
}
