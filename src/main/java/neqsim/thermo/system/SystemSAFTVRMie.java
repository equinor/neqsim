package neqsim.thermo.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSAFTVRMie;

/**
 * Thermodynamic system using the SAFT-VR Mie equation of state (Lafitte et al. 2013).
 *
 * <p>
 * The Mie potential generalises the Lennard-Jones 12-6 potential by allowing variable repulsive and
 * attractive exponents, providing improved accuracy for many pure fluids and mixtures.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemSAFTVRMie extends SystemSrkEos {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SystemSAFTVRMie.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemSAFTVRMie with default T=298.15 K and P=1.0 bara.
   */
  public SystemSAFTVRMie() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemSAFTVRMie.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemSAFTVRMie(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemSAFTVRMie.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemSAFTVRMie(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "SAFTVRMie-EOS";
    attractiveTermNumber = 0;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseSAFTVRMie();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
    }
    setImplementedCompositionDeriativesofFugacity(true);
    setImplementedPressureDeriativesofFugacity(true);
    setImplementedTemperatureDeriativesofFugacity(true);

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
  }
}
