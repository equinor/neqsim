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
    super();
    modelName = "Mathias-Copeman-SRK-EOS";
    attractiveTermNumber = 4;
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
    super(T, P);
    modelName = "Mathias-Copeman-SRK-EOS";
    attractiveTermNumber = 4;
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
    attractiveTermNumber = 4;
    modelName = "Mathias-Copeman-SRK-EOS";
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

    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }
}
