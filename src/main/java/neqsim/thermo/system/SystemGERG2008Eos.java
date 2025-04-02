package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the GERG2008 equation of state.
 * It is analogous to the GERG2004 system, but uses the upgraded GERG2008 model.
 * 
 * @author YourName
 * @version 1.0
 */
public class SystemGERG2008Eos extends SystemEos {
    private static final long serialVersionUID = 1000;

    /**
     * Constructor for SystemGERG2008Eos.
     */
    public SystemGERG2008Eos() {
        this(298.15, 1.0, false);
    }

    /**
     * Constructor for SystemGERG2008Eos.
     *
     * @param T The temperature in Kelvin.
     * @param P The pressure in bara (absolute pressure).
     */
    public SystemGERG2008Eos(double T, double P) {
        this(T, P, false);
    }

    /**
     * Constructor for SystemGERG2008Eos.
     *
     * @param T             The temperature in Kelvin.
     * @param P             The pressure in bara (absolute pressure).
     * @param checkForSolids Set true to enable solid phase check and calculations.
     */
  public SystemGERG2008Eos(double T, double P, boolean checkForSolids) {
      super(T, P, checkForSolids);
      modelName = "GERG2008-EOS";

      // Initialize the phase array with GERG2008 phases.
      for (int i = 0; i < numberOfPhases; i++) {
          phaseArray[i] = new PhaseGERG2008Eos();
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

  // Hydrate check (if enabled) is handled similarly.
    if (hydrateCheck) {
      phaseArray[numberOfPhases - 1] = new PhaseHydrate();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
  }

      this.useVolumeCorrection(false);
      commonInitialization();
  }

  @Override
  public SystemGERG2008Eos clone() {
      SystemGERG2008Eos clonedSystem = null;
      try {
          clonedSystem = (SystemGERG2008Eos) super.clone();
      } catch (Exception ex) {
          logger.error("Cloning failed.", ex);
      }
      return clonedSystem;
  }

  /**
   * Common initialization settings for the GERG2008 EOS system.
   */
  public void commonInitialization() {
      setImplementedCompositionDeriativesofFugacity(false);
      setImplementedPressureDeriativesofFugacity(false);
      setImplementedTemperatureDeriativesofFugacity(false);
  }
}
