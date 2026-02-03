/*
 * TVflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Temperature-Volume flash calculation.
 *
 * <p>
 * Given temperature T and volume V, solves for pressure P using Newton iteration. The calculation
 * iteratively adjusts pressure until the system volume matches the specified volume.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TVflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TVflash.class);

  /** Specified volume in cm³. */
  private double Vspec = 0;
  /** TPflash operation used for phase equilibrium. */
  private Flash tpFlash;
  /** Maximum number of iterations. */
  private static final int MAX_ITERATIONS = 500;
  /** Convergence tolerance for volume error. */
  private static final double TOLERANCE = 1e-9;
  /** Minimum pressure step to avoid division by zero. */
  private static final double MIN_PRESSURE_STEP = 1e-10;

  /**
   * Constructor for TVflash.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec specified volume in cm³
   */
  public TVflash(SystemInterface system, double Vspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vspec = Vspec;
  }

  /**
   * Calculate dV/dP at constant T and n (derivative of volume with respect to pressure).
   *
   * <p>
   * For multiphase systems, the total derivative is the sum of individual phase derivatives.
   * </p>
   *
   * @return dV/dP in cm³/bar
   */
  public double calcdQdVdP() {
    double dQdVP = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      double dPdV = system.getPhase(i).getdPdVTn();
      // Check for near-zero derivative (can happen near critical point)
      if (Math.abs(dPdV) > 1e-20) {
        dQdVP += 1.0 / dPdV;
      }
    }
    return dQdVP;
  }

  /**
   * Calculate volume residual (current volume - specified volume).
   *
   * @return volume residual in cm³
   */
  public double calcdQdV() {
    return system.getVolume() - Vspec;
  }

  /**
   * Solve for pressure that gives the specified volume using Newton iteration.
   *
   * <p>
   * The algorithm uses a combination of analytical derivatives (for early iterations) and numerical
   * derivatives (for later iterations) to ensure robust convergence. It includes safeguards against
   * numerical instability in the equation of state at extreme conditions.
   * </p>
   *
   * @return converged pressure in bar
   */
  public double solveQ() {
    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    double initialPressure = system.getPressure();
    iterations = 0;
    double error = 100.0;
    double numericdQdVdP = 0.0;
    double olddQdV = 0.0;
    double pressureStep = 1.0;

    // Reasonable absolute pressure limits for industrial applications
    // Most process simulations won't exceed 1000 bara; 5000 is a generous upper limit
    double minPressure = Math.max(0.001, initialPressure * 0.01);
    double maxPressure = Math.min(5000.0, Math.max(1000.0, initialPressure * 10.0));

    do {
      iterations++;
      oldPres = nyPres;

      // init(3) computes fugacity coefficients and their T/P derivatives
      // This is needed for the dPdVTn derivative calculation
      system.init(3);

      double dQDVdP = calcdQdVdP();
      double currentVolumeError = calcdQdV();

      // Detect numerical instability: positive dV/dP indicates unstable EoS region
      // dP/dV should always be negative for mechanical stability, so dV/dP should also be negative
      if (dQDVdP > 0) {
        logger.warn("TVflash: Positive dV/dP detected at P={} bar - EoS may be in unstable region",
            oldPres);
        // Use bisection-like approach when derivative has wrong sign
        if (currentVolumeError > 0) {
          nyPres = oldPres * 1.05; // Volume too large, slightly increase pressure
        } else {
          nyPres = oldPres * 0.95; // Volume too small, slightly decrease pressure
        }
      } else {
        // Calculate numerical derivative for later iterations
        if (Math.abs(pressureStep) > MIN_PRESSURE_STEP) {
          numericdQdVdP = (currentVolumeError - olddQdV) / pressureStep;
        }

        // Determine which derivative to use and calculate step
        double derivativeToUse;
        double dampingFactor;

        if (iterations < 5) {
          // Early iterations: use analytical with strong damping
          derivativeToUse = dQDVdP;
          dampingFactor = 0.1;
        } else if (Math.abs(numericdQdVdP) > MIN_PRESSURE_STEP && !Double.isNaN(numericdQdVdP)
            && !Double.isInfinite(numericdQdVdP) && numericdQdVdP < 0) {
          // Numerical derivative is valid and negative (stable)
          derivativeToUse = numericdQdVdP;
          dampingFactor = 0.5;
        } else if (Math.abs(dQDVdP) > MIN_PRESSURE_STEP && !Double.isNaN(dQDVdP)
            && !Double.isInfinite(dQDVdP)) {
          // Fall back to analytical
          derivativeToUse = dQDVdP;
          dampingFactor = 0.3;
        } else {
          // Both derivatives unreliable - use bisection-like approach
          if (currentVolumeError > 0) {
            nyPres = oldPres * 1.1; // Volume too large, increase pressure
          } else {
            nyPres = oldPres * 0.9; // Volume too small, decrease pressure
          }
          derivativeToUse = 1.0; // Placeholder
          dampingFactor = 0.0; // Indicates we used bisection
        }

        if (dampingFactor > 0) {
          double step = -dampingFactor * currentVolumeError / derivativeToUse;

          // Limit step size to prevent wild oscillations (max 20% change per iteration)
          double maxStep = oldPres * 0.2;
          if (Math.abs(step) > maxStep) {
            step = Math.signum(step) * maxStep;
          }

          nyPres = oldPres + step;
        }
      }

      // Strict bounds checking on pressure
      if (nyPres <= minPressure) {
        nyPres = minPressure;
      }
      if (nyPres >= maxPressure) {
        nyPres = maxPressure;
        // If we hit max pressure and volume is still too small, we can't converge
        if (currentVolumeError < 0 && iterations > 10) {
          logger.warn(
              "TVflash: Hit maximum pressure limit ({} bar) but target volume not achievable",
              maxPressure);
        }
      }

      // Additional check: prevent pressure from changing too drastically per iteration
      if (nyPres > oldPres * 1.3) {
        nyPres = oldPres * 1.3;
      }
      if (nyPres < oldPres * 0.77) {
        nyPres = oldPres * 0.77;
      }

      pressureStep = nyPres - oldPres;

      olddQdV = currentVolumeError;
      system.setPressure(nyPres);
      tpFlash.run();
      error = Math.abs(calcdQdV()) / Math.max(system.getVolume(), 1e-10);

      if (logger.isDebugEnabled() && iterations % 50 == 0) {
        logger.debug(
            "TVflash iteration {}: error={}, P={} bar, V={} cm³, Vspec={} cm³, dQDVdP={}, numericdQdVdP={}",
            iterations, error, nyPres, system.getVolume(), Vspec, dQDVdP, numericdQdVdP);
      }

    } while ((error > TOLERANCE && iterations < MAX_ITERATIONS) || iterations < 3);

    if (iterations >= MAX_ITERATIONS && error > TOLERANCE) {
      logger.warn(
          "TVflash did not converge after {} iterations. Final error: {}, P: {} bar, V: {} cm³",
          iterations, error, nyPres, system.getVolume());
    }

    return nyPres;
  }

  /**
   * Get the number of iterations from the last calculation.
   *
   * @return number of iterations
   */
  public int getIterations() {
    return iterations;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    solveQ();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
