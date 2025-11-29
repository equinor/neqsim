package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseEOSCGEos;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;

/** Thermodynamic system using the EOS-CG equation of state. */
public class SystemEOSCGEos extends SystemEos {
  private static final long serialVersionUID = 1000;

  public SystemEOSCGEos() {
    this(298.15, 1.0, false);
  }

  public SystemEOSCGEos(double T, double P) {
    this(T, P, false);
  }

  public SystemEOSCGEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "EOS-CG";

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseEOSCGEos();
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
    this.useVolumeCorrection(false);
    commonInitialization();
  }

  @Override
  public SystemEOSCGEos clone() {
    return (SystemEOSCGEos) super.clone();
  }

  public void commonInitialization() {
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);
  }
}
