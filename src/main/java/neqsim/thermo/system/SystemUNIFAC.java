package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the Unifac for liquids with SRK equation of state for gas.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUNIFAC extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemUNIFAC.
   */
  public SystemUNIFAC() {
    this(273.15, 0);
  }

  /**
   * Constructor for SystemUNIFAC.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUNIFAC(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemUNIFAC.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUNIFAC(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UNIFAC-GE-model";
    attractiveTermNumber = 0;

    configureEosGePhases(T, P, new PhaseSrkEos(), new PhaseGEUnifac());
  }

  /** {@inheritDoc} */
  @Override
  public SystemUNIFAC clone() {
    SystemUNIFAC clonedSystem = null;
    try {
      clonedSystem = (SystemUNIFAC) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
