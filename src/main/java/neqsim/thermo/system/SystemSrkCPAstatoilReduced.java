package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAreduced;

/**
 * Thermodynamic system using the reduced-dimension CPA-EOS solver with Broyden acceleration.
 *
 * <p>
 * Combines association site symmetry reduction (grouping equivalent sites by charge pattern) with
 * Broyden rank-1 inverse-Jacobian updates for compounded speedup. For systems with high site
 * symmetry (e.g., water + MEG, both 4C), the coupled Newton system is reduced from (n_s+1) to (p+1)
 * dimensions where p is the number of unique site types.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilReduced extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002;

  /**
   * Constructor of a fluid object using the reduced CPA-EoS.
   */
  public SystemSrkCPAstatoilReduced() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilReduced(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilReduced(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-Reduced";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAreduced();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }

    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /** {@inheritDoc} */
  @Override
  public SystemSrkCPAstatoilReduced clone() {
    SystemSrkCPAstatoilReduced clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilReduced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
