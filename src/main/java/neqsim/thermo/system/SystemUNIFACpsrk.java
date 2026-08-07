package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGEUnifacPSRK;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the UNIFAC for liquid and PSRK EoS for gas.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemUNIFACpsrk extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemUNIFACpsrk.
   */
  public SystemUNIFACpsrk() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemUNIFACpsrk.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemUNIFACpsrk(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemUNIFACpsrk.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemUNIFACpsrk(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "UNIFAC-GE-model";
    attractiveTermNumber = 0;

    configureEosGePhases(T, P, new PhaseSrkEos(), new PhaseGEUnifacPSRK());
  }

  /** {@inheritDoc} */
  @Override
  public SystemUNIFACpsrk clone() {
    SystemUNIFACpsrk clonedSystem = null;
    try {
      clonedSystem = (SystemUNIFACpsrk) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
