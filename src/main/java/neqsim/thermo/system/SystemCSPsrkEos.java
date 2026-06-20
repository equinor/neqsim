package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseCSPsrkEos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the CSP SRK equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemCSPsrkEos extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemCSPsrkEos.
   * </p>
   */
  public SystemCSPsrkEos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemCSPsrkEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemCSPsrkEos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemCSPsrkEos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemCSPsrkEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "CSPsrk-EOS";
    attractiveTermNumber = 0;

    // Recreates phases created in super constructor SystemSrkEos
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseCSPsrkEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(5);
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
  public SystemCSPsrkEos clone() {
    SystemCSPsrkEos clonedSystem = null;
    try {
      clonedSystem = (SystemCSPsrkEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
