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
   * Compute an initial pressure estimate using the TV specification approach.
   *
   * <p>
   * Uses a clone of the system set to TV mode (constant volume, single-phase). The EOS then
   * computes pressure analytically from the Helmholtz free energy derivative
   * ({@code P = -dF/dV + nRT/V}) without iterating on volume. This is the same approach used by
   * gradient theory for interfacial tension calculations.
   * </p>
   *
   * <p>
   * This only works reliably for single-phase systems. For two-phase states the EOS will compute a
   * pressure corresponding to the metastable single-phase root, but this still provides a much
   * better initial guess than the arbitrary starting pressure.
   * </p>
   *
   * @return estimated pressure in bar, or -1 if the estimate is invalid
   */
  private double estimatePressureFromTV() {
    try {
      // Use a clone to avoid mutating the original system state
      SystemInterface tvSystem = system.clone();
      tvSystem.setNumberOfPhases(1);
      tvSystem.setUseTVasIndependentVariables(true);
      tvSystem.getPhase(0).setTotalVolume(Vspec / 1.0e5);
      tvSystem.useVolumeCorrection(false);
      tvSystem.init(3);
      double estimatedPressure = tvSystem.getPressure();

      if (estimatedPressure > 0 && !Double.isNaN(estimatedPressure)
          && !Double.isInfinite(estimatedPressure) && estimatedPressure < 5000.0) {
        return estimatedPressure;
      }
    } catch (Exception ex) {
      // TV estimate failed — fall back to normal iteration
    }
    return -1.0;
  }

  /**
   * Solve for pressure that gives the specified volume using Newton iteration.
   *
   * <p>
   * The algorithm first attempts a fast single-phase pressure estimate using the TV specification
   * approach (same as gradient theory): given T and V, the EOS computes P analytically via
   * {@code calcPressure()} on a clone. If the system turns out to be single-phase, this converges
   * in 1-2 iterations. For two-phase systems, it provides a good initial guess for the Newton
   * iteration.
   * </p>
   *
   * <p>
   * The Newton loop uses 1/10 damped analytical dV/dP derivatives with 2x max step safety bounds.
   * Early termination when error starts growing ({@code error &lt; errorOld}) prevents oscillation
   * near phase boundaries.
   * </p>
   *
   * @return converged pressure in bar
   */
  public double solveQ() {
    // --- Fast path: estimate P directly from EOS using TV specification ---
    double tvEstimate = estimatePressureFromTV();
    if (tvEstimate > 0) {
      system.setPressure(tvEstimate);
    }

    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    double initialPressure = system.getPressure();
    iterations = 0;
    double error = 100.0;
    double errorOld = error;

    // Absolute upper pressure limit for safety
    double maxPressure = Math.min(5000.0, Math.max(1000.0, initialPressure * 10.0));

    do {
      iterations++;
      oldPres = nyPres;

      system.init(3);

      // Newton step with 1/10 damping on analytical dV/dP derivative
      nyPres = oldPres - (1.0 / 10.0) * calcdQdV() / calcdQdVdP();

      // Pressure safety bounds
      if (nyPres <= 0.0) {
        nyPres = oldPres / 2.0;
      }
      if (nyPres >= oldPres * 2) {
        nyPres = oldPres * 2.0;
      }
      if (nyPres > maxPressure) {
        nyPres = maxPressure;
      }

      system.setPressure(nyPres);
      tpFlash.run();
      errorOld = error;
      error = Math.abs(calcdQdV());

    } while ((error > TOLERANCE && iterations < MAX_ITERATIONS && error < errorOld)
        || iterations < 3);

    // If the fast loop terminated with significant error (e.g. near phase boundary),
    // continue iterating with a stall counter to allow the solver to find a better solution
    if (error > Vspec * 0.01 && iterations < MAX_ITERATIONS) {
      double bestError = error;
      double bestPressure = nyPres;
      int stallCount = 0;

      while (error > TOLERANCE && iterations < MAX_ITERATIONS && stallCount < 10) {
        iterations++;
        oldPres = nyPres;

        system.init(3);
        nyPres = oldPres - (1.0 / 10.0) * calcdQdV() / calcdQdVdP();

        if (nyPres <= 0.0) {
          nyPres = oldPres / 2.0;
        }
        if (nyPres >= oldPres * 2) {
          nyPres = oldPres * 2.0;
        }
        if (nyPres > maxPressure) {
          nyPres = maxPressure;
        }

        system.setPressure(nyPres);
        tpFlash.run();
        error = Math.abs(calcdQdV());

        if (error < bestError) {
          bestError = error;
          bestPressure = nyPres;
          stallCount = 0;
        } else {
          stallCount++;
        }
      }

      // Restore the best solution found during retry
      if (bestError < error) {
        system.setPressure(bestPressure);
        tpFlash.run();
      }
    }

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
    // First TPflash runs COLD (Wilson K) to avoid bias from stale K-values
    // left by a previous unrelated flash. Warm-start is then enabled for the
    // subsequent inner TPflash iterations — safe because the outer loop
    // converges on P via volume residual.
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      tpFlash.run();
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
      solveQ();
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
