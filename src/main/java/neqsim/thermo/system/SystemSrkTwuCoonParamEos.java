package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon Param equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkTwuCoonParamEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonParamEos.
   * </p>
   */
  public SystemSrkTwuCoonParamEos() {
    this(298.15, 1.0, false);
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
    this(T, P, false);
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

    return clonedSystem;
  }
}
