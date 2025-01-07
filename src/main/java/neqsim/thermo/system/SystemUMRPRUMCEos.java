package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC parameters equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRPRUMCEos extends SystemUMRPRUEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   */
  public SystemUMRPRUMCEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRPRUMCEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUMRPRUMCEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UMR-PRU-MC-EoS";
    attractiveTermNumber = 13;
  }

  /** {@inheritDoc} */
  @Override
  public SystemUMRPRUMCEos clone() {
    SystemUMRPRUMCEos clonedSystem = null;
    try {
      clonedSystem = (SystemUMRPRUMCEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
