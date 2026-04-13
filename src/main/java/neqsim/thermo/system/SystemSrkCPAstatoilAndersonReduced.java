package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAandersonReduced;

/**
 * Thermodynamic system using the Anderson-accelerated nested CPA-EOS with site symmetry reduction.
 *
 * <p>
 * Combines Anderson acceleration (mixing depth m=3) for the inner site fraction loop with site type
 * grouping that reduces the loop dimension from n_s to p, where p is the number of unique site
 * types. This is a nested-family solver that avoids the coupled-family equilibrium sensitivity. The
 * {@code initCPAMatrix(1)} volume derivative computation also uses the reduced p-dimensional
 * Hessian.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilAndersonReduced extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003;

  /**
   * Constructor of a fluid object using the Anderson-reduced CPA-EoS.
   */
  public SystemSrkCPAstatoilAndersonReduced() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the Anderson-reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilAndersonReduced(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the Anderson-reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilAndersonReduced(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-AndersonReduced";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAandersonReduced();
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
  public SystemSrkCPAstatoilAndersonReduced clone() {
    SystemSrkCPAstatoilAndersonReduced clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilAndersonReduced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
