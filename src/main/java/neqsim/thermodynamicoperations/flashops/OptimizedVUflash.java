/*
 * OptimizedVUflash.java
 *
 * High-performance VU flash with optimized convergence for separator applications
 */

package neqsim.thermodynamicoperations.flashops;

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
 * <li>Optimized Newton-Raphson solver with line search</li>
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
    this.tpFlash = new TPflash(system);
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
      do {
        iterations++;
        oldPres = nyPres;
        oldTemp = nyTemp;

        // Batch initialization to reduce overhead
        system.init(3);

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
    try {
      double pressure = system.getPressure();
      double temperature = system.getTemperature();
      if (!Double.isFinite(pressure) || !Double.isFinite(temperature) || pressure <= 0.0 || temperature <= 0.0) {
        return false;
      }
      double volume = system.getVolume();
      double enthalpy = system.getEnthalpy();
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
