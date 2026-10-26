package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrCPA;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * Thermodynamic system using the PR-CPA equation of state with generic PR-CPA database parameters.
 *
 * <p>
 * Uses the PR-based CPA mass-action and analytical site-derivative implementation with the ordinary PR alpha and fitted
 * CPA coefficient when available. Sharing the association solver with UMR-CPA does not select its dedicated parameter
 * or alpha families. Use CPA mixing rules 7, 9 or 10. Legacy Java-serialized PR-CPA phases must be recreated after the
 * association implementation repair; see {@link PhasePrCPA}.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrCPA extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemPrCPA.
   */
  public SystemPrCPA() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemPrCPA.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemPrCPA(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemPrCPA.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemPrCPA(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPA-PR-EOS";

    // Recreates phases created in super constructor SystemPrEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(true);

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
  public SystemPrCPA clone() {
    SystemPrCPA clonedSystem = null;
    try {
      clonedSystem = (SystemPrCPA) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
