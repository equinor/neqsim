package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseHydrate;
import neqsim.thermo.phase.PhasePrEos;
import neqsim.thermo.phase.PhasePureComponentSolid;

/**
 * This class defines a thermodynamic system using the Peng–Robinson equation of state
 * (PR‑EoS).
 *
 * <p>The Peng–Robinson EOS is expressed as
 *
 * <pre>
 * P = \frac{R T}{v - b} - \frac{a \alpha}{v (v + b) + b (v - b)}
 * </pre>
 *
 * where {@code R} is the gas constant, {@code T} is the temperature, {@code v} is the molar
 * volume, and {@code a} and {@code b} are component specific parameters. The temperature
 * dependent parameter {@code \alpha} is calculated from the acentric factor {@code \omega}
 * and critical temperature {@code T_c} as
 *
 * <pre>
 * \alpha = \left[1 + \left(0.37464 + 1.54226\,\omega - 0.26992\,\omega^2\right)
 *           \left(1 - \sqrt{T/T_c}\right)\right]^2
 * </pre>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemPrEos extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Default constructor creating a PR-EoS system at 298.15 K and 1 bara.
   */
  public SystemPrEos() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructs a PR-EoS system at the specified temperature and pressure.
   *
   * @param T the temperature in Kelvin
   * @param P the pressure in bara (absolute pressure)
   */
  public SystemPrEos(final double T, final double P) {
    this(T, P, false);
  }

  /**
   * Constructs a PR-EoS system with optional solid phase calculations.
   *
   * @param T the temperature in Kelvin
   * @param P the pressure in bara (absolute pressure)
   * @param checkForSolids set {@code true} to enable solid phase calculations
   */
  public SystemPrEos(final double T, final double P, final boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "PR-EOS";
    getCharacterization().setTBPModel("PedersenPR");
    attractiveTermNumber = 1;

    for (int i = 0; i < numberOfPhases; i++) {
      phaseArray[i] = new PhasePrEos();
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
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a deep copy of this {@code SystemPrEos} instance.
   */
  @Override
  public SystemPrEos clone() {
    SystemPrEos clonedSystem = null;
    try {
      clonedSystem = (SystemPrEos) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
