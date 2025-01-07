package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkTwuCoonEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonEos.
   * </p>
   */
  public SystemSrkTwuCoonEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkTwuCoonEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkTwuCoonEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "TwuCoonRK-EOS";
    attractiveTermNumber = 11;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkTwuCoonEos clone() {
    SystemSrkTwuCoonEos clonedSystem = null;
    try {
      clonedSystem = (SystemSrkTwuCoonEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
