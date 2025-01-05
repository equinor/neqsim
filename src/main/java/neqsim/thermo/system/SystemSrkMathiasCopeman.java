package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK with Mathias Copeman equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkMathiasCopeman extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkMathiasCopeman.
   * </p>
   */
  public SystemSrkMathiasCopeman() {
    this(289.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkMathiasCopeman.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkMathiasCopeman(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkMathiasCopeman.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkMathiasCopeman(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Mathias-Copeman-SRK-EOS";
    attractiveTermNumber = 4;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkMathiasCopeman clone() {
    SystemSrkMathiasCopeman clonedSystem = null;
    try {
      clonedSystem = (SystemSrkMathiasCopeman) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
