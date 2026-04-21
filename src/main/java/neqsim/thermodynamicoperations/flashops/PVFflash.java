package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * Pressure-Vapor Fraction (PVF) flash calculation.
 *
 * <p>
 * Given a specified pressure and molar vapor fraction (quality), this flash determines the
 * temperature at which the system achieves the specified vapor fraction. Uses a Newton-Raphson
 * iteration on temperature, adjusting until the computed vapor fraction matches the target.
 * </p>
 *
 * <p>
 * The vapor fraction (beta) is defined as the ratio of moles in the vapor phase to total moles.
 * Beta = 0.0 corresponds to the bubble point and beta = 1.0 corresponds to the dew point.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class PVFflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PVFflash.class);

  /** Target molar vapor fraction (0.0 to 1.0). */
  private double vaporFractionSpec;

  /** Internal TP flash for intermediate calculations. */
  private Flash tpFlash;

  /** Maximum number of iterations for the Newton solver. */
  private int maxIterations = 200;

  /** Convergence tolerance for vapor fraction. */
  private double tolerance = 1.0e-8;

  /**
   * Constructor for PVFflash.
   *
   * @param system the thermodynamic system (pressure must be set)
   * @param vaporFractionSpec target molar vapor fraction (0.0 = bubble point, 1.0 = dew point)
   * @throws IllegalArgumentException if vaporFractionSpec is not in [0, 1]
   */
  public PVFflash(SystemInterface system, double vaporFractionSpec) {
    if (vaporFractionSpec < 0.0 || vaporFractionSpec > 1.0) {
      throw new IllegalArgumentException(
          "Vapor fraction must be between 0.0 and 1.0, got: " + vaporFractionSpec);
    }
    this.system = system;
    this.vaporFractionSpec = vaporFractionSpec;
    this.tpFlash = new TPflash(system);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // First TPflash runs COLD (Wilson K) to avoid bias from stale K-values
    // left by a previous unrelated flash. Warm-start is enabled only for the
    // subsequent inner TPflash iterations within the outer vapor-fraction
    // search (runs dozens of TPflashes at nearby T values).
    boolean prevWarm = neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(false);
      runInternal();
    } finally {
      neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(prevWarm);
    }
  }

  /**
   * Internal implementation of the PVF flash. The public {@link #run()} wraps this method to enable
   * K-value warm-start for the inner TPflash loop.
   */
  private void runInternal() {
    // Handle pure liquid (bubble point) and pure vapor (dew point) as special cases
    if (vaporFractionSpec <= 0.0) {
      runBubblePoint();
      return;
    }
    if (vaporFractionSpec >= 1.0) {
      runDewPoint();
      return;
    }

    // General case: iterate on temperature to match the specified vapor fraction
    // (first TPflash cold, subsequent internal calls warm)
    tpFlash.run();
    neqsim.thermo.ThermodynamicModelSettings.setUseWarmStartKValues(true);
    system.init(2);

    double tempLow = estimateLowTemperature();
    double tempHigh = estimateHighTemperature();
    double temperature = system.getTemperature();

    // Bisection + secant method hybrid for robustness
    double betaLow = computeBeta(tempLow);
    double betaHigh = computeBeta(tempHigh);

    // Ensure bracket: betaLow < vaporFractionSpec < betaHigh
    // If initial bracket doesn't contain the root, widen it
    int bracketAttempts = 0;
    while (betaLow > vaporFractionSpec && bracketAttempts < 20) {
      tempLow -= 10.0;
      if (tempLow < 50.0) {
        tempLow = 50.0;
        break;
      }
      betaLow = computeBeta(tempLow);
      bracketAttempts++;
    }
    bracketAttempts = 0;
    while (betaHigh < vaporFractionSpec && bracketAttempts < 20) {
      tempHigh += 10.0;
      if (tempHigh > 2000.0) {
        tempHigh = 2000.0;
        break;
      }
      betaHigh = computeBeta(tempHigh);
      bracketAttempts++;
    }

    // Illinois method (regula falsi with acceleration)
    double tA = tempLow;
    double tB = tempHigh;
    double fA = betaLow - vaporFractionSpec;
    double fB = betaHigh - vaporFractionSpec;

    for (int iter = 0; iter < maxIterations; iter++) {
      double tC = tA - fA * (tB - tA) / (fB - fA);

      // Clamp to valid range
      if (tC < 50.0) {
        tC = 50.0;
      }
      if (tC > 2000.0) {
        tC = 2000.0;
      }

      double fC = computeBeta(tC) - vaporFractionSpec;

      if (Math.abs(fC) < tolerance) {
        // Converged — set final temperature
        system.setTemperature(tC);
        tpFlash.run();
        system.init(2);
        return;
      }

      if (fC * fB < 0.0) {
        // Root is in [tC, tB]
        tA = tC;
        fA = fC;
      } else {
        // Root is in [tA, tC] — Illinois acceleration
        tB = tC;
        fB = fC;
        fA *= 0.5;
      }

      if (Math.abs(tB - tA) < 1.0e-10) {
        break;
      }
    }

    // Final flash at best estimate
    double tFinal = 0.5 * (tA + tB);
    system.setTemperature(tFinal);
    tpFlash.run();
    system.init(2);
  }

  /**
   * Compute the vapor fraction (beta) at the given temperature.
   *
   * @param temperature temperature in Kelvin
   * @return molar vapor fraction
   */
  private double computeBeta(double temperature) {
    system.setTemperature(temperature);
    tpFlash.run();
    system.init(2);

    if (system.getNumberOfPhases() == 1) {
      if (system.getPhase(0).getType() == neqsim.thermo.phase.PhaseType.GAS) {
        return 1.0;
      } else {
        return 0.0;
      }
    }

    // getBeta() returns the vapor phase fraction
    return system.getBeta();
  }

  /**
   * Estimate a low temperature (below bubble point).
   *
   * @return estimated low temperature in Kelvin
   */
  private double estimateLowTemperature() {
    double temp = system.getTemperature();
    return Math.max(temp * 0.7, 100.0);
  }

  /**
   * Estimate a high temperature (above dew point).
   *
   * @return estimated high temperature in Kelvin
   */
  private double estimateHighTemperature() {
    double temp = system.getTemperature();
    return Math.min(temp * 1.5, 1500.0);
  }

  /**
   * Run bubble point temperature flash (vapor fraction = 0).
   */
  private void runBubblePoint() {
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
      ops.bubblePointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Bubble point flash failed", ex);
    }
  }

  /**
   * Run dew point temperature flash (vapor fraction = 1).
   */
  private void runDewPoint() {
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(system);
      ops.dewPointTemperatureFlash();
    } catch (Exception ex) {
      logger.error("Dew point flash failed", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
