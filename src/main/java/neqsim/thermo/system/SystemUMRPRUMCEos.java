package neqsim.thermo.system;

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUMRPRUMCEos extends SystemUMRPRUEos {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEos.
   * </p>
   */
  public SystemUMRPRUMCEos() {
    super();
    setBmixType(1);
    modelName = "UMR-PRU-MC-EoS";
    attractiveTermNumber = 13;
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
    super(T, P);
    setBmixType(1);
    modelName = "UMR-PRU-MC-EoS";
    attractiveTermNumber = 13;
    CapeOpenProperties11 = new String[] {"speedOfSound", "jouleThomsonCoefficient",
        "internalEnergy", "internalEnergy.Dtemperature", "gibbsEnergy", "helmholtzEnergy",
        "fugacityCoefficient", "logFugacityCoefficient", "logFugacityCoefficient.Dtemperature",
        "logFugacityCoefficient.Dpressure", "logFugacityCoefficient.Dmoles", "enthalpy",
        "enthalpy.Dtemperature", "entropy", "heatCapacityCp", "heatCapacityCv", "density",
        "volume"};
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
    setBmixType(1);
    attractiveTermNumber = 13;
    modelName = "UMR-PRU-MC-EoS";
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
