package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAfullyImplicitReduced;

/**
 * Thermodynamic system using the fully implicit CPA-EOS with site type reduction.
 *
 * <p>
 * Combines two acceleration strategies: (1) the fully implicit coupled Newton-Raphson algorithm
 * from Igben et al. (2026) that eliminates inner XA iterations, and (2) association site symmetry
 * reduction that groups equivalent sites into types with multiplicities, reducing the system
 * dimension from (n_s+1) to (p+1).
 * </p>
 *
 * <p>
 * The Newton Jacobian is built analytically at every iteration on the reduced system (no Broyden
 * approximation), giving both the per-iteration cost reduction from dimension reduction AND the
 * quadratic convergence rate of full Newton-Raphson.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class SystemSrkCPAstatoilFullyImplicitReduced extends SystemSrkCPAstatoil {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003;

  /**
   * Constructor of a fluid object using the fully implicit reduced CPA-EoS.
   */
  public SystemSrkCPAstatoilFullyImplicitReduced() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor of a fluid object using the fully implicit reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAstatoilFullyImplicitReduced(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor of a fluid object using the fully implicit reduced CPA-EoS.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAstatoilFullyImplicitReduced(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS-statoil-FullyImplicit-Reduced";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAfullyImplicitReduced();
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
  public SystemSrkCPAstatoilFullyImplicitReduced clone() {
    SystemSrkCPAstatoilFullyImplicitReduced clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAstatoilFullyImplicitReduced) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return clonedSystem;
  }
}
