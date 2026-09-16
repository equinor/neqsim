package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Wax appearance temperature from a bracket of independent multiphase TP flashes.
 *
 * <p>
 * The onset is the transition through a wax mass fraction of {@value #WAX_MASS_TOLERANCE}. The returned temperature is
 * the warm endpoint of a bracket narrower than {@value #TEMPERATURE_TOLERANCE} K. This is a numerical onset for the
 * configured TP wax model, not an experimental WAT or a deposition model. Trials always start from the feed, so an
 * existing wax phase or reordered fluid phases cannot seed a spurious saturation solution.
 * </p>
 *
 * @author asmund
 */
public class WATcalc extends ConstantDutyTemperatureFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Numerical wax appearance threshold, mass fraction of the whole fluid. */
  private static final double WAX_MASS_TOLERANCE = 1e-8;
  /** Maximum final temperature bracket width in K. */
  private static final double TEMPERATURE_TOLERANCE = 1e-4;
  /** Lower search bound in K (a numerical guard, not a model validity statement). */
  private static final double MIN_TEMPERATURE = 100.0;
  /** Upper search bound in K. */
  private static final double MAX_TEMPERATURE = 1000.0;
  /** Temperature increment when locating an appearance bracket, in K. */
  private static final double SEARCH_STEP = 10.0;
  /** Limit on all trial flashes, including endpoint verification. */
  private static final int MAX_FLASHES = 120;
  /** Maximum absolute mole-fraction balance error accepted from a TP flash. */
  private static final double BALANCE_TOLERANCE = 1e-6;
  /** Lower, wax-positive bracket temperature in K, NaN until found. */
  private double lowerTemperature = Double.NaN;
  /** Upper bracket temperature in K, NaN until found. */
  private double upperTemperature = Double.NaN;
  /** Number of TP trial evaluations. */
  private int flashEvaluations;

  /**
   * Creates a wax appearance operation.
   *
   * @param system fluid with characterized wax formers and a configured wax phase
   */
  public WATcalc(SystemInterface system) {
    super(system);
  }

  /**
   * Finds and verifies an appearance bracket before changing the caller's state.
   *
   * @throws IllegalArgumentException if pressure, temperature, feed or wax configuration is invalid
   * @throws IllegalStateException if a valid TP onset cannot be bracketed or verified
   */
  @Override
  public void run() {
    flashEvaluations = 0;
    lowerTemperature = Double.NaN;
    upperTemperature = Double.NaN;
    validateInput();
    SystemInterface feed = system.clone();
    feed.setMultiphaseWaxCheck(true);
    // Respect the configured fluid phase model, including the optional aqueous phase check.
    double temperature = feed.getTemperature();
    boolean waxPresent = hasWax(flash(feed, temperature));
    if (waxPresent) {
      lowerTemperature = temperature;
      while (waxPresent && temperature < MAX_TEMPERATURE) {
        temperature = Math.min(MAX_TEMPERATURE, temperature + SEARCH_STEP);
        waxPresent = hasWax(flash(feed, temperature));
        if (waxPresent) {
          lowerTemperature = temperature;
        } else {
          upperTemperature = temperature;
        }
      }
    } else {
      upperTemperature = temperature;
      while (!waxPresent && temperature > MIN_TEMPERATURE) {
        temperature = Math.max(MIN_TEMPERATURE, temperature - SEARCH_STEP);
        waxPresent = hasWax(flash(feed, temperature));
        if (waxPresent) {
          lowerTemperature = temperature;
        } else {
          upperTemperature = temperature;
        }
      }
    }
    if (!Double.isFinite(lowerTemperature) || !Double.isFinite(upperTemperature)) {
      throw failure("No wax appearance bracket in the 100-1000 K search range");
    }
    while (upperTemperature - lowerTemperature > TEMPERATURE_TOLERANCE) {
      temperature = 0.5 * (lowerTemperature + upperTemperature);
      if (hasWax(flash(feed, temperature))) {
        lowerTemperature = temperature;
      } else {
        upperTemperature = temperature;
      }
    }
    // Re-evaluate both sides independently; never manufacture an incipient wax phase.
    SystemInterface cold = flash(feed, lowerTemperature);
    SystemInterface warm = flash(feed, upperTemperature);
    if (!hasWax(cold) || hasWax(warm)) {
      throw failure("Wax appearance endpoints were not reproducible");
    }
    copyState(warm);
  }

  /** Validates the feed without mutating it. */
  private void validateInput() {
    if (!Double.isFinite(system.getTemperature()) || system.getTemperature() < MIN_TEMPERATURE
        || system.getTemperature() > MAX_TEMPERATURE || !Double.isFinite(system.getPressure())
        || system.getPressure() <= 0.0 || !Double.isFinite(system.getTotalNumberOfMoles())
        || system.getTotalNumberOfMoles() <= 0.0) {
      throw new IllegalArgumentException("calcWAT requires positive pressure and moles and temperature in 100-1000 K");
    }
    if (system.getPhases().length <= 5 || system.getPhases()[5] == null
        || system.getPhases()[5].getType() != PhaseType.WAX) {
      throw new IllegalArgumentException("calcWAT requires addSolidComplexPhase(\"wax\") after wax characterization");
    }
    for (int component = 0; component < system.getNumberOfComponents(); component++) {
      if (system.getPhases()[5].getComponent(component).isWaxFormer()
          && system.getPhase(0).getComponent(component).getNumberOfmoles() > 0.0) {
        return;
      }
    }
    throw new IllegalArgumentException("calcWAT requires at least one nonzero wax-forming component");
  }

  /**
   * Runs one independent flash with canonical phase initialization.
   *
   * @param feed unchanged overall feed and model configuration
   * @param temperature trial temperature in K
   * @return balanced flashed trial
   */
  private SystemInterface flash(SystemInterface feed, double temperature) {
    if (++flashEvaluations > MAX_FLASHES) {
      throw failure("Exceeded " + MAX_FLASHES + " TP flash evaluations");
    }
    SystemInterface trial = feed.clone();
    try {
      trial.setTemperature(temperature);
      trial.init(0);
      new ThermodynamicOperations(trial).TPflash();
      validateState(trial);
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          failure("Invalid TP trial at " + temperature + " K: " + ex.getMessage()).getMessage(), ex);
    }
    return trial;
  }

  /**
   * Checks that the TP phase state is finite, normalized and conserves the feed.
   *
   * @param trial TP result to validate
   */
  private void validateState(SystemInterface trial) {
    double betaSum = 0.0;
    double[] composition = new double[trial.getNumberOfComponents()];
    if (trial.getNumberOfPhases() < 1 || trial.getNumberOfPhases() > trial.getMaxNumberOfPhases()) {
      throw failure("Invalid phase count");
    }
    for (int phase = 0; phase < trial.getNumberOfPhases(); phase++) {
      PhaseInterface phaseState = trial.getPhase(phase);
      double beta = trial.getBeta(phase);
      if (!Double.isFinite(beta) || beta <= 0.0 || beta > 1.0) {
        throw failure("Invalid phase fraction");
      }
      betaSum += beta;
      double sum = 0.0;
      for (int component = 0; component < composition.length; component++) {
        double fraction = phaseState.getComponent(component).getx();
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
          throw failure("Invalid phase composition");
        }
        sum += fraction;
        composition[component] += beta * fraction;
      }
      if (Math.abs(sum - 1.0) > BALANCE_TOLERANCE) {
        throw failure("Unnormalized phase composition");
      }
    }
    if (Math.abs(betaSum - 1.0) > BALANCE_TOLERANCE) {
      throw failure("Unnormalized phase fractions");
    }
    for (int component = 0; component < composition.length; component++) {
      if (Math.abs(composition[component] - trial.getPhase(0).getComponent(component).getz()) > BALANCE_TOLERANCE) {
        throw failure("TP component balance failed for component " + component);
      }
    }
  }

  /**
   * Tests numerical wax appearance using phase identity, independent of phase ordering.
   *
   * @param trial validated TP result
   * @return true if the wax mass fraction exceeds the appearance threshold
   */
  private boolean hasWax(SystemInterface trial) {
    if (!trial.hasPhaseType("wax")) {
      return false;
    }
    double fraction = trial.getPhaseFraction("wax", "mass");
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw failure("Invalid wax mass fraction");
    }
    return fraction > WAX_MASS_TOLERANCE;
  }

  /**
   * Publishes the accepted state while preserving storage indices and caller phase-check options.
   *
   * @param accepted verified warm bracket endpoint
   */
  private void copyState(SystemInterface accepted) {
    system.setTemperature(accepted.getTemperature());
    system.setNumberOfPhases(accepted.getNumberOfPhases());
    for (int slot = 0; slot < accepted.getMaxNumberOfPhases(); slot++) {
      system.setPhase(accepted.getPhases()[slot].clone(), slot);
      system.setPhaseIndex(slot, accepted.getPhaseIndex(slot));
    }
    for (int phase = 0; phase < accepted.getNumberOfPhases(); phase++) {
      system.setPhaseType(phase, accepted.getPhase(phase).getType());
      system.setBeta(phase, accepted.getBeta(phase));
    }
  }

  /**
   * Builds a failure with pressure, bracket and evaluation evidence.
   *
   * @param reason failure reason
   * @return explicit nonconvergence exception
   */
  private IllegalStateException failure(String reason) {
    return new IllegalStateException("calcWAT: " + reason + "; pressure=" + system.getPressure() + " bara; bracket=["
        + lowerTemperature + ", " + upperTemperature + "] K; TP evaluations=" + flashEvaluations);
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
  }
}
