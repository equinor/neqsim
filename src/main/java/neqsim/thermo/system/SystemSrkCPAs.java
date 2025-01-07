package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkCPAsOld;

/**
 * This class defines a thermodynamic system using the sCPA-EOS equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkCPAs extends SystemSrkCPA {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemSrkCPAs.
   * </p>
   */
  public SystemSrkCPAs() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkCPAs.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkCPAs(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemSrkCPAs.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSrkCPAs(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CPAs-SRK-EOS";

    // Recreates phases created in super constructor SystemSrkCPA
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkCPAsOld();
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
  public SystemSrkCPAs clone() {
    SystemSrkCPAs clonedSystem = null;
    try {
      clonedSystem = (SystemSrkCPAs) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
