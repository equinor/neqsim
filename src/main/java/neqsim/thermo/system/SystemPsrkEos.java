package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the Predictive SRK-EoS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPsrkEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemPsrkEos.
   */
  public SystemPsrkEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemPsrkEos.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPsrkEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemPsrkEos.
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

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException if a required part of the clone cannot be copied
   */
  @Override
  public SystemPsrkEos clone() {
    SystemPsrkEos clonedSystem = null;
    try {
      clonedSystem = (SystemPsrkEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
      throw new IllegalStateException("Unable to complete clone of SystemPsrkEos", ex);
    }

    return clonedSystem;
  }
}
