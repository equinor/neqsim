package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Two Coon model of Statoil equation of
 * state.
 *
 * @author asmund
 * @version $Id: $Id
 */
public class SystemSrkTwuCoonStatoilEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonStatoilEos.
   * </p>
   */
  public SystemSrkTwuCoonStatoilEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonStatoilEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkTwuCoonStatoilEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkTwuCoonStatoilEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkTwuCoonStatoilEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "TwuCoonStatoil-EOS";
    attractiveTermNumber = 18;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkTwuCoonStatoilEos clone() {
    SystemSrkTwuCoonStatoilEos clonedSystem = null;
    try {
      clonedSystem = (SystemSrkTwuCoonStatoilEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
