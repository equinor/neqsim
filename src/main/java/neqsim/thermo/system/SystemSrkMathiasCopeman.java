package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK with Mathias Copeman equation of state.
 * 
 * @author Even Solbraa
 */
public class SystemSrkMathiasCopeman extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

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
   * @param T a double
   * @param P a double
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
   * @param T a double
   * @param P a double
   * @param solidCheck a boolean
   */
  public SystemSrkMathiasCopeman(double T, double P, boolean solidCheck) {
    super(T, P, solidCheck);
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
