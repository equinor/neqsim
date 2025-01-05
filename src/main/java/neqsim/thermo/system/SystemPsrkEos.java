package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the Predictive SRK-EoS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPsrkEos extends SystemSrkEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPsrkEos.
   * </p>
   */
  public SystemPsrkEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemPsrkEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPsrkEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemPsrkEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPsrkEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Predictive-SRK-EOS";
    attractiveTermNumber = 4;
  }

  /** {@inheritDoc} */
  @Override
  public SystemPsrkEos clone() {
    SystemPsrkEos clonedSystem = null;
    try {
      clonedSystem = (SystemPsrkEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
