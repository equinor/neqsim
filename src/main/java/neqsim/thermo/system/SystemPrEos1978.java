package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the Peng Robinson v. 1978 equation of state
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrEos1978 extends SystemPrEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPrEos1978.
   * </p>
   */
  public SystemPrEos1978() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemPrEos1978.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrEos1978(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemPrEos1978.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrEos1978(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "PR78-EoS";
    attractiveTermNumber = 6;
  }

  /** {@inheritDoc} */
  @Override
  public SystemPrEos1978 clone() {
    SystemPrEos1978 clonedSystem = null;
    try {
      clonedSystem = (SystemPrEos1978) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
