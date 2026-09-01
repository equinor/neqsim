package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseDesmukhMather;
import neqsim.thermo.phase.PhaseSrkEos;

/**
 * Thermodynamic system using SRK for gas and oil and Desmukh-Mather for the aqueous electrolyte phase.
 *
 * <p>
 * Calling {@code setMultiPhaseCheck(true)} selects the fixed-role hybrid gas-oil-aqueous flash. If
 * {@code chemicalReactionInit()} has loaded reactions, aqueous chemical equilibrium is coupled to phase equilibrium.
 * Scale potential can then be evaluated from the Desmukh-Mather ion activities, subject to that model's component and
 * interaction-parameter coverage.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class SystemDesmukhMather extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructor for SystemDesmukhMather.
   */
  public SystemDesmukhMather() {
    this(298.15, 1.0, false);
  }

  /**
   * Constructor for SystemDesmukhMather.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemDesmukhMather(double T, double P) {
    this(T, P, false);
  }

  /**
   * Constructor for SystemDesmukhMather.
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemDesmukhMather(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "Desmukh-Mather-model";
    attractiveTermNumber = 0;

    configureHybridEosGePhases(T, P, new PhaseSrkEos(), new PhaseDesmukhMather(), new PhaseSrkEos());
  }

  /** {@inheritDoc} */
  @Override
  public SystemDesmukhMather clone() {
    SystemDesmukhMather clonedSystem = null;
    try {
      clonedSystem = (SystemDesmukhMather) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    return clonedSystem;
  }
}
