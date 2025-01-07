package neqsim.thermo.system;

import neqsim.thermo.phase.PhasePrEosvolcor;

/**
 * This class defines a thermodynamic system using the UMR-PRU with MC paramters equation of state.
 *
 * @author Even Solbraa
 */
public class SystemUMRPRUMCEosNew extends SystemUMRPRUMCEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEosNew.
   * </p>
   */
  public SystemUMRPRUMCEosNew() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemUMRPRUMCEosNew.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
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
