package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseWaterIAPWS;

/**
 * Thermodynamic system using the IAPWS-IF97 reference model for water.
 */
public class SystemWaterIF97 extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default constructor setting 298.15 K and 1.0 bara. */
  public SystemWaterIF97() {
    this(298.15, 1.0, false);
  }

  /**
   * Create a system with specified temperature and pressure.
   *
   * @param T temperature in K
   * @param P pressure in bara
   */
  public SystemWaterIF97(double T, double P) {
    this(T, P, false);
  }

  /**
   * Create a system with temperature, pressure and optional solid phase check.
   */
  public SystemWaterIF97(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "IAPWS-IF97";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseWaterIAPWS();
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
    commonInitialization();
  }

  @Override
  public SystemWaterIF97 clone() {
    SystemWaterIF97 cloned = null;
    try {
      cloned = (SystemWaterIF97) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning of SystemWaterIF97 failed", ex);
    }
    return cloned;
  }

  /** Common initialisation of flags. */
  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(false);
    setImplementedPressureDeriativesofFugacity(false);
    setImplementedTemperatureDeriativesofFugacity(false);
  }
}
