package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon Param equation of state.
 *
 * @author Even Solbraa
 */
public class SystemSrkTwuCoonParamEos extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  public SystemSrkTwuCoonParamEos() {
    super();
    modelName = "TwuCoonRKparam-EOS";
    attractiveTermNumber = 12;
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonParamEos.
   * </p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemSrkTwuCoonParamEos(double T, double P) {
    super(T, P);
    modelName = "TwuCoonRKparam-EOS";
    attractiveTermNumber = 12;
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonParamEos.
   * </p>
   *
   * @param T a double
   * @param P a double
   * @param solidCheck a boolean
   */
  public SystemSrkTwuCoonParamEos(double T, double P, boolean solidCheck) {
    super(T, P, solidCheck);
    modelName = "TwuCoonRKparam-EOS";
    attractiveTermNumber = 12;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkTwuCoonParamEos clone() {
    SystemSrkTwuCoonParamEos clonedSystem = null;
    try {
      clonedSystem = (SystemSrkTwuCoonParamEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    // for(int i = 0; i < numberOfPhases; i++) {
    // clonedSystem.phaseArray[i] = (PhaseInterface) phaseArray[i].clone();
    // }

    return clonedSystem;
  }
}
