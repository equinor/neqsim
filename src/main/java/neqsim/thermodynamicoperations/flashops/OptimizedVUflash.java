/*
 * OptimizedVUflash.java
 *
 * High-performance VU flash with optimized convergence for separator applications
 */

package neqsim.thermodynamicoperations.flashops;

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * OptimizedVUflash class with enhanced performance for transient separator simulations. Key optimizations:
 * <ul>
 * <li>Adaptive convergence criteria based on system state</li>
 * <li>Smart initial guessing using previous state</li>
 * <li>Reduced thermodynamic property evaluations</li>
 * <li>Early termination for well-behaved cases</li>
 * <li>Damped Newton updates with bounded steps</li>
 * <li>Caloric initialization on the returned TP state, reused by the next iteration</li>
 * </ul>
 *
 * @author GitHub Copilot
 * @version $Id: $Id
 */
public class OptimizedVUflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OptimizedVUflash.class);

  double Vspec = 0;
  double Uspec = 0.0;
  Flash tpFlash;

  // Optimization parameters
  private static final double MIN_PRESSURE = 0.01; // bar
  private static final double MAX_PRESSURE = 2000.0; // bar
  private static final double MIN_TEMPERATURE = 50.0; // K
  private static final double MAX_TEMPERATURE = 5000.0; // K
  private static final double ADAPTIVE_TOL_FACTOR = 1e-8; // Base tolerance
  private static final double FAST_CONV_TOL = 1e-6; // Relaxed tolerance for fast convergence
  private static final double DERIVATIVE_THRESHOLD = 1e-12;
  private static final int MAX_ITERATIONS = 100;
  private static final double MIN_DAMPING = 0.05;
  private static final double MAX_DAMPING = 0.8;
  /** Relative volume residual accepted as a converged VU solution. */
  private static final double SOLUTION_VOLUME_TOL = 1.0e-3;
  /** Relative energy residual accepted as a converged VU solution. */
  private static final double SOLUTION_ENERGY_TOL = 1.0e-3;

  // Performance tracking - instance-level to avoid cross-contamination between different systems
  private double lastPressure = Double.NaN;
  private double lastTemperature = Double.NaN;
  private boolean isWellBehaved = true;
  /** Whether the initialization TP flash may reuse the current system's K-values. */
  private final boolean warmStartInitialization;
  /** Number of Newton iterations used by the most recent solve. */
  private int lastIterationCount = 0;
  /** Whether the most recent solve met both V and U specifications. */
  private boolean lastRunConverged = false;
  /** Whether the most recent warm-initialized run required a cold retry. */
  private boolean coldFallbackUsed = false;
  /** Maximum temperature iterations of the equilibrium polish stage. */
  private static final int POLISH_MAX_ITERATIONS = 30;
  /** Scaled volume tolerance of the equilibrium polish stage. */
  private static final double POLISH_VOLUME_TOL = 1.0e-9;
  /** Scaled internal-energy tolerance of the equilibrium polish stage. */
  private static final double POLISH_ENERGY_TOL = 1.0e-9;
  /** Maximum Newton iterations for detached equilibrium recovery. */
  private static final int RECOVERY_MAX_ITERATIONS = 10;
  /** Maximum residual-reducing step attempts per recovery iteration. */
  private static final int RECOVERY_MAX_BACKTRACKS = 8;
  /** Near-singularity threshold for the Jacobian of the two scaled recovery residuals. */
  private static final double RECOVERY_MIN_DETERMINANT = 1.0e-14;
  /** Whether the most recent run needed the equilibrium polish stage. */
  private volatile boolean equilibriumPolishUsed = false;
  /** Whether the most recent run attempted detached equilibrium recovery. */
  private volatile boolean equilibriumRecoveryUsed = false;
  /** Number of detached TP flashes used by the most recent equilibrium recovery. */
  private final AtomicInteger equilibriumRecoveryFlashCount = new AtomicInteger();

  /**
   * Constructor for OptimizedVUflash.
   *
   * @param system thermodynamic system to flash
   * @param Vspec specified total volume
   * @param Uspec specified internal energy
   */
  public OptimizedVUflash(SystemInterface system, double Vspec, double Uspec) {
    this(system, Vspec, Uspec, false);
  }

  /**
   * Constructor for an optimized VU flash with an explicit initialization policy.
   *
   * <p>
   * Warm initialization is intended for continuous dynamic calculations where the supplied system is the immediately
   * preceding converged state. Standalone flashes should retain the cold default so unrelated states cannot seed each
   * other.
   * </p>
   *
   * @param system thermodynamic system to flash
   * @param Vspec specified total volume
   * @param Uspec specified internal energy
   * @param warmStartInitialization whether the initialization TP flash may reuse current K-values
   */
  public OptimizedVUflash(SystemInterface system, double Vspec, double Uspec, boolean warmStartInitialization) {
    this.system = system;
    this.tpFlash = createFixedInventoryFlash(system);
    this.Vspec = Vspec;
    this.Uspec = Uspec;
    this.warmStartInitialization = warmStartInitialization;
  }

  /**
   * Validates inputs with fast checks.
   *
   * @return true if specified volume and internal energy are finite and usable
   */
  private boolean validateInputs() {
    return Vspec > 0 && Double.isFinite(Uspec);
  }

  /**
   * Smart initial guess using previous solution and system characteristics.
   */
  private void improveInitialGuess() {
    if (!Double.isNaN(lastPressure) && !Double.isNaN(lastTemperature)) {
      // Use previous solution as starting point for transient calculations
      double pressureDiff = Math.abs(system.getPressure() - lastPressure);
      double tempDiff = Math.abs(system.getTemperature() - lastTemperature);

      // If we're close to the previous solution, use it as starting point
      if (pressureDiff < 0.5 * lastPressure && tempDiff < 20.0) {
        system.setPressure(lastPressure);
        system.setTemperature(lastTemperature);
        return;
      }
    }

    // Estimate initial guess based on ideal gas behavior
    double currentVolume = system.getVolume();
    if (currentVolume > 0) {
      double volumeRatio = Vspec / currentVolume;
      // Adjust pressure inversely proportional to volume change (ideal gas approximation)
      double newPressure = system.getPressure() / volumeRatio;
      newPressure = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, newPressure));
      system.setPressure(newPressure);
    }
  }

  /**
   * Optimized derivative calculations with safety checks.
   *
   * @return derivative of the objective with respect to pressure
   */
  private double calcdQdP() {
    return system.getPressure() * (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
  }

  private double calcdQdT() {
    return (Uspec + system.getPressure() * Vspec - system.getEnthalpy())
        / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R);
  }

  private double calcdQdPP() {
    double dVdP = system.getdVdPtn();
    double dQdVV = (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())
        + system.getPressure() * dVdP / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());

    // Ensure derivative is not too small
    if (Math.abs(dQdVV) < DERIVATIVE_THRESHOLD) {
      dQdVV = Math.signum(dQdVV) * DERIVATIVE_THRESHOLD;
    }
    return dQdVV;
  }

  private double calcdQdTT() {
    double dQdT_val = calcdQdT();
    double dQdTT = -system.getCp() / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R)
        - dQdT_val / system.getTemperature();

    // Ensure derivative is not too small
    if (Math.abs(dQdTT) < DERIVATIVE_THRESHOLD) {
      dQdTT = Math.signum(dQdTT) * DERIVATIVE_THRESHOLD;
    }
    return dQdTT;
  }

  /**
   * High-performance solver with adaptive convergence and line search.
   *
   * @return converged pressure, or the current system pressure if input validation fails
   */
  public double solveQ() {
    lastIterationCount = 0;
    lastRunConverged = false;
    if (!validateInputs()) {
      logger.warn("Invalid inputs for OptimizedVUflash");
      return system.getPressure();
    }

    // Smart initial guess
    improveInitialGuess();

    double oldPres = system.getPressure();
    double oldTemp = system.getTemperature();
    double nyPres = oldPres;
    double nyTemp = oldTemp;

    int iterations = 0;
    double tolerance = isWellBehaved ? FAST_CONV_TOL : ADAPTIVE_TOL_FACTOR;
    double damping = isWellBehaved ? MAX_DAMPING : MIN_DAMPING;

    // Track convergence quality
    double lastError = Double.MAX_VALUE;
    int stagnationCount = 0;

    try {
      // Volume, Cp and temperature/pressure derivatives require level two, not composition derivatives.
      system.init(2);
      do {
        iterations++;
        oldPres = nyPres;
        oldTemp = nyTemp;

        // The caloric state is current from initialization or the preceding TP flash.

        // Calculate all derivatives at once
        double dQdP = calcdQdP();
        double dQdT = calcdQdT();
        double dQdPP = calcdQdPP();
        double dQdTT = calcdQdTT();

        // Newton-Raphson updates with adaptive damping
        double deltaPres = -damping * dQdP / dQdPP;
        double deltaTemp = -damping * dQdT / dQdTT;

        // Limit step sizes based on system behavior
        double maxPresChange = isWellBehaved ? 0.3 * oldPres : 0.1 * oldPres;
        double maxTempChange = isWellBehaved ? 50.0 : 20.0;

        deltaPres = Math.max(-maxPresChange, Math.min(maxPresChange, deltaPres));
        deltaTemp = Math.max(-maxTempChange, Math.min(maxTempChange, deltaTemp));

        nyPres = oldPres + deltaPres;
        nyTemp = oldTemp + deltaTemp;

        // Enforce bounds
        nyPres = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, nyPres));
        nyTemp = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, nyTemp));

        system.setPressure(nyPres);
        system.setTemperature(nyTemp);

        // Single TP flash per iteration
        tpFlash.run();
        // A TP flash refreshes fixed-T/P equilibrium; initialize caloric derivatives on its resulting state.
        // Reuse this level-two state in the next iteration rather than adding a second initialization there.
        system.init(2);

        // Calculate convergence metrics - check BOTH iteration variable changes AND
        // specification
        // errors
        double presError = Math.abs((nyPres - oldPres) / Math.max(nyPres, 0.1));
        double tempError = Math.abs((nyTemp - oldTemp) / Math.max(nyTemp, 1.0));
        double totalError = presError + tempError;

        // Also check actual volume and energy specification errors
        double volErr = Math.abs((system.getVolume() - Vspec) / Vspec);
        double hTarget = Uspec + system.getPressure() * Vspec;
        double hErr = Math.abs((system.getEnthalpy() - hTarget) / Math.max(Math.abs(hTarget), 1.0));

        // Early termination only if BOTH iteration convergence AND specification errors are
        // small
        if (totalError < tolerance && volErr < 1e-6 && hErr < 1e-5) {
          isWellBehaved = true;
          lastRunConverged = true;
          break;
        }

        // Adaptive damping and tolerance
        if (totalError < lastError) {
          damping = Math.min(MAX_DAMPING, damping * 1.1); // Increase damping
          stagnationCount = 0;
        } else {
          damping = Math.max(MIN_DAMPING, damping * 0.7); // Decrease damping
          stagnationCount++;
        }

        // Detect stagnation and adjust strategy
        if (stagnationCount > 3) {
          tolerance *= 2; // Relax tolerance
          isWellBehaved = false;
        }

        lastError = totalError;
      } while (iterations < MAX_ITERATIONS);

      // Update performance tracking
      lastIterationCount = iterations;
      if (lastRunConverged) {
        lastPressure = nyPres;
        lastTemperature = nyTemp;

        // Consider system well-behaved if converged quickly
        if (iterations <= 10) {
          isWellBehaved = true;
        }
      } else {
        logger.warn("OptimizedVUflash did not converge after " + iterations + " iterations");
        isWellBehaved = false;
      }
    } catch (Exception e) {
      lastIterationCount = iterations;
      lastRunConverged = false;
      logger.warn("Exception in OptimizedVUflash: " + e.getMessage());
      isWellBehaved = false;
    }

    return nyPres;
  }

  /**
   * Checks whether the state currently held by the system actually satisfies the volume and internal-energy
   * specification. {@link #solveQ()} leaves the system at its last iterate even when the Newton iteration diverges, so
   * the caller must verify the result before accepting it - an unverified iterate becomes the initial state of the next
   * transient step and can corrupt an entire dynamic run.
   *
   * @return true when pressure, temperature, volume and energy are finite and both specifications are met
   */
  private boolean isSolutionAcceptable() {
    return isSolutionAcceptable(system);
  }

  /**
   * Checks whether a thermodynamic state satisfies the accepted volume and internal-energy specification.
   *
   * @param state equilibrated state to check
   * @return true when pressure, temperature, volume and energy are finite and both specifications are met
   */
  private boolean isSolutionAcceptable(SystemInterface state) {
    try {
      double pressure = state.getPressure();
      double temperature = state.getTemperature();
      if (!Double.isFinite(pressure) || !Double.isFinite(temperature) || pressure <= 0.0 || temperature <= 0.0) {
        return false;
      }
      double volume = state.getVolume();
      double enthalpy = state.getEnthalpy();
      if (!Double.isFinite(volume) || !Double.isFinite(enthalpy) || Vspec <= 0.0) {
        return false;
      }
      double volumeError = Math.abs((volume - Vspec) / Vspec);
      double enthalpyTarget = Uspec + pressure * Vspec;
      double energyError = Math.abs((enthalpy - enthalpyTarget) / Math.max(Math.abs(enthalpyTarget), 1.0));
      return volumeError < SOLUTION_VOLUME_TOL && energyError < SOLUTION_ENERGY_TOL;
    } catch (RuntimeException ex) {
      logger.warn("Could not evaluate VU flash residuals: " + ex.getMessage());
      return false;
    }
  }

  /**
   * Runs the initialization TP flash and Newton iteration with their requested warm-start settings.
   *
   * @param warmStartInitialFlash whether the initialization TP flash may reuse current K-values
   * @param warmStartInnerFlashes whether the inner TP flashes of the Newton iteration may reuse K-values
   */
  private void solveFromCurrentState(boolean warmStartInitialFlash, boolean warmStartInnerFlashes) {
    lastPressure = Double.NaN;
    lastTemperature = Double.NaN;
    isWellBehaved = true;
    neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(warmStartInitialFlash);
    tpFlash.run();
    neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(warmStartInnerFlashes);
    solveQ();
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    double startPressure = system.getPressure();
    double startTemperature = system.getTemperature();
    coldFallbackUsed = false;
    equilibriumPolishUsed = false;
    equilibriumRecoveryUsed = false;
    equilibriumRecoveryFlashCount.set(0);
    try {
      // Every Newton-loop TP flash may reuse K-values. The initialization flash does so only when
      // the caller identifies the state as the previous point on a continuous dynamic trajectory.
      solveFromCurrentState(warmStartInitialization, true);
      boolean solutionAcceptable = isSolutionAcceptable();
      if (warmStartInitialization && !solutionAcceptable) {
        // A warm seed is an optimization, never a correctness requirement. Retry once from the
        // incoming P/T with a cold initialization if the nearby-state assumption was invalid.
        coldFallbackUsed = true;
        system.setPressure(startPressure);
        system.setTemperature(startTemperature);
        solveFromCurrentState(false, true);
        solutionAcceptable = isSolutionAcceptable();
      }
      if (!solutionAcceptable && isFiniteState()) {
        // The Q-function Newton step uses frozen-composition derivatives. Close to a bubble point (a small gas cap
        // in a liquid-filled vessel) these understate dV/dP several-fold because they ignore evaporation, so the
        // damped iteration oscillates and stalls a few per mille from the solution, or jumps into the single-phase
        // region where V(P) is almost flat. Polish from the last iterate along the constant-volume path.
        equilibriumPolishUsed = true;
        polishOnConstantVolumePath();
        solutionAcceptable = isSolutionAcceptable();
      }
      if (!solutionAcceptable && isFiniteState()) {
        try {
          solutionAcceptable = recoverFromInitialEquilibrium(startPressure, startTemperature);
        } catch (RuntimeException failure) {
          logger.debug("Detached equilibrium VU recovery failed", failure);
        }
      }
      lastRunConverged = solutionAcceptable;
      if (!solutionAcceptable) {
        logger.warn("OptimizedVUflash did not reach the volume/energy specification");
      }
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
  }

  /**
   * Returns the Newton iteration count from the most recent solve.
   *
   * @return last Newton iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Returns whether the most recent solve met the accepted volume and energy residual criteria.
   *
   * @return true when the final state satisfied both accepted residual criteria
   */
  public boolean isLastRunConverged() {
    return lastRunConverged;
  }

  /**
   * Returns whether the most recent run needed the equilibrium polish stage after the Q-function Newton iteration (and
   * its cold retry) missed the specification.
   *
   * @return true when the polish stage ran
   */
  public boolean wasEquilibriumPolishUsed() {
    return equilibriumPolishUsed;
  }

  /**
   * Returns whether the most recent solve attempted detached equilibrium recovery after the regular VU solve and
   * constant-volume polish both missed their acceptance criteria.
   *
   * @return true when detached equilibrium recovery was attempted
   */
  public boolean wasEquilibriumRecoveryUsed() {
    return equilibriumRecoveryUsed;
  }

  /**
   * Returns the number of detached TP flashes used by the most recent equilibrium recovery.
   *
   * <p>
   * The value is zero when recovery was not attempted. Recovery is bounded by one seed flash plus four central
   * difference flashes and at most {@value #RECOVERY_MAX_BACKTRACKS} backtracking flashes for each of at most
   * {@value #RECOVERY_MAX_ITERATIONS} Newton iterations.
   * </p>
   *
   * @return detached TP flash count
   */
  public int getEquilibriumRecoveryFlashCount() {
    return equilibriumRecoveryFlashCount.get();
  }

  /**
   * Flashes the system at the given pressure and temperature.
   *
   * @param pressure pressure in bara
   * @param temperature temperature in K
   * @return scaled volume residual {@code (V - Vspec)/Vspec}, NaN when the state is not finite
   */
  private double flashVolumeResidual(double pressure, double temperature) {
    system.setPressure(pressure);
    system.setTemperature(temperature);
    tpFlash.run();
    system.init(2);
    return (system.getVolume() - Vspec) / Vspec;
  }

  /**
   * Returns the scaled internal-energy residual of the state currently held by the system.
   *
   * @return {@code (U - Uspec)/max(|Uspec|, 1)} with {@code U = H - PV}
   */
  private double currentEnergyResidual() {
    double energy = system.getEnthalpy() - system.getPressure() * system.getVolume();
    return (energy - Uspec) / Math.max(Math.abs(Uspec), 1.0);
  }

  /**
   * Solves {@code V(P, T) = Vspec} for pressure at fixed temperature. Volume falls monotonically with pressure, also
   * across phase boundaries, so the root is bracketed by geometric expansion and refined by Illinois false position.
   * Leaves the system flashed at the returned pressure.
   *
   * @param temperature temperature in K
   * @param pressureGuess starting pressure in bara
   * @return pressure in bara, or NaN when no root could be bracketed
   */
  private double solvePressureForVolume(double temperature, double pressureGuess) {
    double pA = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, pressureGuess));
    double fA = flashVolumeResidual(pA, temperature);
    if (!Double.isFinite(fA)) {
      return Double.NaN;
    }
    if (Math.abs(fA) < POLISH_VOLUME_TOL) {
      return pA;
    }
    double factor = 1.02;
    double pB = pA;
    double fB = fA;
    boolean bracketed = false;
    for (int k = 0; k < 80; k++) {
      double next = fA > 0.0 ? pB * factor : pB / factor;
      next = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, next));
      if (next == pB) {
        return Double.NaN;
      }
      pB = next;
      fB = flashVolumeResidual(pB, temperature);
      if (!Double.isFinite(fB)) {
        return Double.NaN;
      }
      if (Math.abs(fB) < POLISH_VOLUME_TOL) {
        return pB;
      }
      if ((fB > 0.0) != (fA > 0.0)) {
        bracketed = true;
        break;
      }
      pA = pB;
      fA = fB;
      factor = Math.min(factor * 1.5, 2.0);
    }
    if (!bracketed) {
      return Double.NaN;
    }
    double pC = pB;
    int side = 0;
    for (int k = 0; k < 100; k++) {
      pC = (pA * fB - pB * fA) / (fB - fA);
      if (!(pC > Math.min(pA, pB) && pC < Math.max(pA, pB))) {
        pC = 0.5 * (pA + pB);
      }
      double fC = flashVolumeResidual(pC, temperature);
      if (!Double.isFinite(fC)) {
        return Double.NaN;
      }
      if (Math.abs(fC) < POLISH_VOLUME_TOL || Math.abs(pB - pA) < 1.0e-12 * pC) {
        return pC;
      }
      if ((fC > 0.0) == (fB > 0.0)) {
        pB = pC;
        fB = fC;
        if (side == -1) {
          fA *= 0.5;
        }
        side = -1;
      } else {
        pA = pC;
        fA = fC;
        if (side == 1) {
          fB *= 0.5;
        }
        side = 1;
      }
    }
    flashVolumeResidual(pC, temperature);
    return pC;
  }

  /**
   * Robust fallback that meets both specifications from the last Newton iterate. The volume specification is solved
   * exactly for pressure at each trial temperature ({@link #solvePressureForVolume(double, double)}); along that
   * constant-volume path the internal energy rises monotonically with temperature, so a guarded secant iteration on
   * temperature meets the energy specification. Unlike the Q-function Newton step this uses equilibrium (not
   * frozen-composition) behaviour, so it also converges next to a bubble point and when the iterate has left the
   * two-phase region. Leaves the system at the best state found.
   */
  private void polishOnConstantVolumePath() {
    try {
      double t0 = system.getTemperature();
      double p = solvePressureForVolume(t0, system.getPressure());
      if (!Double.isFinite(p)) {
        return;
      }
      double h0 = currentEnergyResidual();
      if (Math.abs(h0) < POLISH_ENERGY_TOL) {
        return;
      }
      double t1 = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, t0 + (h0 > 0.0 ? -0.05 : 0.05)));
      double p1 = solvePressureForVolume(t1, p);
      if (!Double.isFinite(p1)) {
        solvePressureForVolume(t0, p);
        return;
      }
      double h1 = currentEnergyResidual();
      for (int k = 0; k < POLISH_MAX_ITERATIONS && Math.abs(h1) >= POLISH_ENERGY_TOL; k++) {
        double slope = (h1 - h0) / (t1 - t0);
        if (!(slope > 0.0) || !Double.isFinite(slope)) {
          break;
        }
        double t2 = t1 - h1 / slope;
        t2 = Math.max(t1 - 10.0, Math.min(t1 + 10.0, t2));
        t2 = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, t2));
        double p2 = solvePressureForVolume(t2, p1);
        if (!Double.isFinite(p2)) {
          break;
        }
        t0 = t1;
        h0 = h1;
        t1 = t2;
        p1 = p2;
        h1 = currentEnergyResidual();
      }
      // Leave the system flashed at the last (best) constant-volume point.
      solvePressureForVolume(t1, p1);
    } catch (RuntimeException ex) {
      logger.warn("OptimizedVUflash equilibrium polish failed: " + ex.getMessage());
    }
  }

  /**
   * Recover a nearby equilibrium from the incoming pressure and temperature after the other VU paths fail.
   *
   * <p>
   * Using a new cold TP trial for every evaluation keeps a trial's phase selection from seeding the next one. The
   * derivatives include changes in the equilibrium phase fractions and compositions.
   * </p>
   *
   * @param initialPressure incoming pressure in bara
   * @param initialTemperature incoming temperature in K
   * @return whether the recovered state satisfies both specifications
   */
  boolean recoverFromInitialEquilibrium(double initialPressure, double initialTemperature) {
    equilibriumRecoveryUsed = true;
    equilibriumRecoveryFlashCount.set(0);
    if (!validateInputs() || !Double.isFinite(Vspec) || !withinFlashBounds(initialPressure, initialTemperature)) {
      return false;
    }
    boolean previousWarmStart = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      SystemInterface seed = system.clone();
      SystemInterface trial = coldEquilibrium(seed, initialPressure, initialTemperature);
      double[] residual = equilibriumResidual(trial);
      // Each accepted step strictly reduces the residual norm, so trial is always the best state found so far.
      for (int iteration = 0; iteration < RECOVERY_MAX_ITERATIONS && !meetsPolishTolerances(residual); iteration++) {
        double[] step = equilibriumNewtonStep(seed, trial, residual);
        if (step == null) {
          break;
        }
        SystemInterface next = decreaseEquilibriumResidual(seed, trial, step, Math.hypot(residual[0], residual[1]));
        if (next == null) {
          // No further decrease: typically the finite-difference noise floor of the TP flash, well inside the
          // acceptance criterion. Keep the best state rather than discarding it.
          break;
        }
        trial = next;
        residual = equilibriumResidual(trial);
      }
      // Iterate towards the tight polish tolerances but publish with the same acceptance criterion as every other
      // VU path, so a stalled or iteration-limited search that already closes both specifications is not lost.
      if (!isSolutionAcceptable(trial)) {
        return false;
      }
      installEquilibrium(system, trial);
      return isSolutionAcceptable();
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(previousWarmStart);
    }
  }

  /**
   * Compute a local Newton update using central differences of explicitly flashed states.
   *
   * @param seed system supplying the component inventory for detached trials
   * @param trial current trial state
   * @param residual current scaled volume and energy residuals
   * @return pressure and temperature steps in bara and K, or null if this local Jacobian cannot be used
   */
  private double[] equilibriumNewtonStep(SystemInterface seed, SystemInterface trial, double[] residual) {
    double pressure = trial.getPressure();
    double temperature = trial.getTemperature();
    double pressureIncrement = Math.max(0.002, 5.0e-4 * pressure);
    double temperatureIncrement = Math.max(0.002, 1.0e-4 * temperature);
    if (!withinFlashBounds(pressure - pressureIncrement, temperature - temperatureIncrement)
        || !withinFlashBounds(pressure + pressureIncrement, temperature + temperatureIncrement)) {
      return null;
    }
    double[] highPressure = equilibriumResidual(coldEquilibrium(seed, pressure + pressureIncrement, temperature));
    double[] lowPressure = equilibriumResidual(coldEquilibrium(seed, pressure - pressureIncrement, temperature));
    double[] highTemperature = equilibriumResidual(coldEquilibrium(seed, pressure, temperature + temperatureIncrement));
    double[] lowTemperature = equilibriumResidual(coldEquilibrium(seed, pressure, temperature - temperatureIncrement));
    double volumePressure = (highPressure[0] - lowPressure[0]) / (2.0 * pressureIncrement);
    double energyPressure = (highPressure[1] - lowPressure[1]) / (2.0 * pressureIncrement);
    double volumeTemperature = (highTemperature[0] - lowTemperature[0]) / (2.0 * temperatureIncrement);
    double energyTemperature = (highTemperature[1] - lowTemperature[1]) / (2.0 * temperatureIncrement);
    double determinant = volumePressure * energyTemperature - volumeTemperature * energyPressure;
    if (!Double.isFinite(determinant) || Math.abs(determinant) < RECOVERY_MIN_DETERMINANT) {
      return null;
    }
    return new double[] {(-residual[0] * energyTemperature + volumeTemperature * residual[1]) / determinant,
        (-volumePressure * residual[1] + residual[0] * energyPressure) / determinant};
  }

  /**
   * Backtrack until an equilibrium trial reduces the combined scaled residual.
   *
   * @param seed system supplying the component inventory for detached trials
   * @param trial current trial state
   * @param step proposed pressure and temperature change in bara and K
   * @param currentNorm scaled residual norm at the current trial
   * @return improving trial state, or null when no improving bounded step is found
   */
  private SystemInterface decreaseEquilibriumResidual(SystemInterface seed, SystemInterface trial, double[] step,
      double currentNorm) {
    double scale = 1.0;
    for (int attempt = 0; attempt < RECOVERY_MAX_BACKTRACKS; attempt++) {
      double pressure = trial.getPressure() + scale * step[0];
      double temperature = trial.getTemperature() + scale * step[1];
      if (withinFlashBounds(pressure, temperature)) {
        SystemInterface candidate = coldEquilibrium(seed, pressure, temperature);
        double[] residual = equilibriumResidual(candidate);
        if (Math.hypot(residual[0], residual[1]) < currentNorm) {
          return candidate;
        }
      }
      scale *= 0.5;
    }
    return null;
  }

  /**
   * Check the same pressure and temperature bounds used by the regular VU solver.
   *
   * @param pressure trial pressure in bara
   * @param temperature trial temperature in K
   * @return whether both variables are within bounds
   */
  private static boolean withinFlashBounds(double pressure, double temperature) {
    return pressure >= MIN_PRESSURE && pressure <= MAX_PRESSURE && temperature >= MIN_TEMPERATURE
        && temperature <= MAX_TEMPERATURE;
  }

  /**
   * Configure an inner TP operation that retains the inventory of the outer VU specification.
   *
   * @param fluid fluid owned by the TP operation
   * @return inner flash that retains component amounts during trace-phase cleanup
   */
  private static TPflash createFixedInventoryFlash(SystemInterface fluid) {
    TPflash flash = new TPflash(fluid);
    flash.setPreserveFeedInventoryOnPhaseRemoval(true);
    return flash;
  }

  /**
   * Flash an independent fluid and initialize its caloric properties.
   *
   * @param seed fluid providing the component inventory
   * @param pressure trial pressure in bara
   * @param temperature trial temperature in K
   * @return trial thermodynamic state
   */
  private SystemInterface coldEquilibrium(SystemInterface seed, double pressure, double temperature) {
    equilibriumRecoveryFlashCount.incrementAndGet();
    SystemInterface trial = seed.clone();
    trial.setPressure(pressure);
    trial.setTemperature(temperature);
    createFixedInventoryFlash(trial).run();
    trial.init(2);
    return trial;
  }

  /**
   * Compute specification errors on the same scaled basis as the equilibrium polish.
   *
   * @param trial equilibrated trial state
   * @return dimensionless volume and internal-energy residuals
   */
  private double[] equilibriumResidual(SystemInterface trial) {
    return new double[] {(trial.getVolume() - Vspec) / Vspec,
        (trial.getInternalEnergy() - Uspec) / Math.max(Math.abs(Uspec), 1.0)};
  }

  /**
   * Check the two specifications separately before publishing a recovered state.
   *
   * @param residual dimensionless volume and internal-energy residuals
   * @return whether both residuals are within tolerance
   */
  private static boolean meetsPolishTolerances(double[] residual) {
    return Math.abs(residual[0]) < POLISH_VOLUME_TOL && Math.abs(residual[1]) < POLISH_ENERGY_TOL;
  }

  /**
   * Install a converged trial while retaining the caller's thermodynamic system identity.
   *
   * @param destination caller's thermodynamic system
   * @param candidate converged equilibrium state for the same component inventory
   */
  static void installEquilibrium(SystemInterface destination, SystemInterface candidate) {
    destination.setPressure(candidate.getPressure());
    destination.setTemperature(candidate.getTemperature());
    destination.setNumberOfPhases(candidate.getNumberOfPhases());
    // Include inactive logical positions so later phase appearance can reuse each physical slot exactly once.
    for (int phase = 0; phase < candidate.getMaxNumberOfPhases(); phase++) {
      destination.setPhaseIndex(phase, candidate.getPhaseIndex(phase));
    }
    for (int phase = 0; phase < candidate.getNumberOfPhases(); phase++) {
      int slot = candidate.getPhaseIndex(phase);
      destination.setPhase(candidate.getPhase(phase).clone(), slot);
      destination.setPhaseType(phase, candidate.getPhase(phase).getType());
      destination.setBeta(phase, candidate.getBeta(phase));
    }
    destination.normalizeBeta();
    destination.init(2);
  }

  /**
   * Returns whether the system holds a finite, positive pressure and temperature to continue from.
   *
   * @return true when continuation from the current iterate is meaningful
   */
  private boolean isFiniteState() {
    double pressure = system.getPressure();
    double temperature = system.getTemperature();
    return Double.isFinite(pressure) && Double.isFinite(temperature) && pressure > 0.0 && temperature > 0.0;
  }

  /**
   * Returns whether a warm-initialized run required the cold safety fallback.
   *
   * @return true when the last run retried with cold initialization
   */
  public boolean wasColdFallbackUsed() {
    return coldFallbackUsed;
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
