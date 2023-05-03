package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseUMRCPA;

/**
 * This class defines a thermodynamic system using the UMR CPA equation of state.
 *
 * @author Even Solbraa
 */
public class SystemUMRCPAEoS extends SystemPrEos {
  private static final long serialVersionUID = 1L;

  /**
   * <p>Constructor for SystemUMRCPAEoS.</p>
   */
  public SystemUMRCPAEoS() {
    super();
    modelName = "UMR-CPA";
    attractiveTermNumber = 19;
    useVolumeCorrection(false);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseUMRCPA();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
      phaseArray[i].useVolumeCorrection(false);
    }
  }

  /**
   * <p>Constructor for SystemUMRCPAEoS.</p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemUMRCPAEoS(double T, double P) {
    super(T, P);
    modelName = "UMR-CPA";
    attractiveTermNumber = 19;
    useVolumeCorrection(false);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseUMRCPA();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].useVolumeCorrection(false);
    }
  }
}
