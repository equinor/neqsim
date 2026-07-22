package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the PR Mathias Copeman equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrMathiasCopeman extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemPrMathiasCopeman.
   */
  public SystemPrMathiasCopeman() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemPrMathiasCopeman.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrMathiasCopeman(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemPrMathiasCopeman.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrMathiasCopeman(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Mathias-Copeman-PR-EOS";
    attractiveTermNumber = 13;
  }

  /** {@inheritDoc} */
  @Override
  public SystemPrMathiasCopeman clone() {
    SystemPrMathiasCopeman clonedSystem = null;
    try {
      clonedSystem = (SystemPrMathiasCopeman) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
