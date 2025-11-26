package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseSrkEosvolcor;

/**
 * This class defines a thermodynamic system using the Peng-Robinson equation of state with volume
 * correction (Pénéloux shift).
 * 
 * <p>
 * The volume correction improves volumetric predictions (density, molar volume) without affecting
 * phase equilibrium calculations. This is particularly useful for:
 * </p>
 * <ul>
 * <li>High-pressure applications (&gt; 100 bar)</li>
 * <li>Accurate density requirements (custody transfer, flow metering)</li>
 * <li>Joule-Thomson coefficient calculations</li>
 * <li>Equipment design requiring precise volumetric properties</li>
 * </ul>
 * 
 * <p>
 * The translation parameter is calculated using the correlation: c = (0.1154 - 0.4406 * (0.29056 -
 * 0.08775 * ω)) * R * Tc / Pc
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSrkEosvolcor extends SystemSrkEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * Constructor for SystemPrEosvolcor.
   * </p>
   */
  public SystemSrkEosvolcor() {
    this(298.15, 1.0);
  }

  /**
   * <p>
   * Constructor for SystemPrEosvolcor.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSrkEosvolcor(double T, double P) {
    super(T, P);
    modelName = "Srk-EoS-volcor";
    useVolumeCorrection(false);

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSrkEosvolcor();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].useVolumeCorrection(false);
    }
  }
}
