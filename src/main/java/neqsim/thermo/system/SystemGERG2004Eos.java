package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGERG2004Eos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the GERG2004 equation of state.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemGERG2004Eos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemGERG2004Eos.
   * </p>
   */
  public SystemGERG2004Eos() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemGERG2004Eos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemGERG2004Eos(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemGERG2004Eos.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemGERG2004Eos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "GERG2004-EOS";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGERG2004Eos();
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

    // What could set hydratecheck? Will never be true
    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
    this.useVolumeCorrection(false);
    commonInitialization();
  }

  /** {@inheritDoc} */
  @Override
  public SystemGERG2004Eos clone() {
    SystemGERG2004Eos clonedSystem = null;
    try {
      clonedSystem = (SystemGERG2004Eos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }

  /**
   * <p>
   * commonInitialization.
   * </p>
   */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }
}
