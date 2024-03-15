package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the SRK Schwartzentruber equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkSchwartzentruberEos extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkSchwartzentruberEos.
   * </p>
   */
  public SystemSrkSchwartzentruberEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkSchwartzentruberEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkSchwartzentruberEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkSchwartzentruberEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkSchwartzentruberEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "ScRK-EOS";
    attractiveTermNumber = 2;
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkSchwartzentruberEos clone() {
    SystemSrkSchwartzentruberEos clonedSystem = null;
    try {
      clonedSystem = (SystemSrkSchwartzentruberEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
