package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGENRTL;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * This class defines a thermodynamic system using the SRK EoS for gas and NRTL for liquids.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemNRTL extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemNRTL.
   */
  public SystemNRTL() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemNRTL.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemNRTL(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemNRTL.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemNRTL(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "NRTL-GE-model";
    attractiveTermNumber = 0;

    configureEosGePhases(T, P, new PhaseSrkEos(), new PhaseGENRTL());
  }

  /** {@inheritDoc} */
  @Override
  public SystemNRTL clone() {
    SystemNRTL clonedSystem = null;
    try {
      clonedSystem = (SystemNRTL) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
