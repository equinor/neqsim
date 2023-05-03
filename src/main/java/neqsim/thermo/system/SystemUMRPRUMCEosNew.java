package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePrEosvolcor;

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters equation of state.
 *
 * @author Even Solbraa
 */
public class SystemUMRPRUMCEosNew extends SystemUMRPRUMCEos {

  private static final long serialVersionUID = 1L;

  /**
   * <p>Constructor for SystemUMRPRUMCEosNew.</p>
   */
  public SystemUMRPRUMCEosNew() {
    super();
    modelName = "UMR-PRU-MC-EoS-New";
    attractiveTermNumber = 19;
    useVolumeCorrection(false);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEosvolcor();
      phaseArray[i].setTemperature(298.15);
      phaseArray[i].setPressure(1.0);
      phaseArray[i].useVolumeCorrection(false);
    }
  }

  /**
   * <p>Constructor for SystemUMRPRUMCEosNew.</p>
   *
   * @param T a double
   * @param P a double
   */
  public SystemUMRPRUMCEosNew(double T, double P) {
    super(T, P);
    modelName = "UMR-PRU-MC-EoS-New";
    attractiveTermNumber = 19;
    useVolumeCorrection(false);
    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEosvolcor();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].useVolumeCorrection(false);
    }
  }
}
