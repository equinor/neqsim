package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon Param equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkTwuCoonParamEos extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonParamEos.
   * </p>
   */
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
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
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
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkTwuCoonParamEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
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
