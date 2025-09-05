package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseAmmoniaEos;

/**
 * Thermodynamic system using a simplified ammonia reference equation of state.
 */
public class SystemAmmoniaEos extends SystemEos {
  private static final long serialVersionUID = 1000L;

  public SystemAmmoniaEos() {
    this(298.15, 1.0, false);
  }

  public SystemAmmoniaEos(double T, double P) {
    this(T, P, false);
  }

  public SystemAmmoniaEos(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Ammonia-EOS";
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseAmmoniaEos();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    this.useVolumeCorrection(false);
  }

  @Override
  public SystemAmmoniaEos clone() {
    SystemAmmoniaEos cloned = null;
    try {
      cloned = (SystemAmmoniaEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }
    return cloned;
  }
}
