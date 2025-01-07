package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the UMR-PRU equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRPRUEos extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUEos.
   * </p>
   */
  public SystemUMRPRUEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUMRPRUEos(double T, double P) {
    super(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUMRPRUEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    setBmixType(1);
    modelName = "UMR-PRU-EoS";
    attractiveTermNumber = 1;

    CapeOpenProperties11 = new String[] {"speedOfSound", "jouleThomsonCoefficient",
        "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy",
        "fugacityCoefficient", "logFugacityCoefficient", "logFugacityCoefficient.Dtemperature",
        "logFugacityCoefficient.Dpressure", "logFugacityCoefficient.Dmoles", "enthalpy",
        "enthalpy.Dtemperature", "entropy", "heatCapacityCp", "heatCapacityCv", "density",
        "volume"};
  }

  /** {@inheritDoc} */
  @Override
  public SystemUMRPRUEos clone() {
    SystemUMRPRUEos clonedSystem = null;
    try {
      clonedSystem = (SystemUMRPRUEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * <p>
   * commonInitialization.
   * </p>
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);
  }
}
