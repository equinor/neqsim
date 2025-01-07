package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseUMRCPA;

/**
 * This class defines a thermodynamic system using the UMR CPA equation of state.
 *
 * @author Even Solbraa
 */
public class SystemUMRCPAEoS extends SystemPrEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemUMRCPAEoS.
   * </p>
   */
  public SystemUMRCPAEoS() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemUMRCPAEoS.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
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
