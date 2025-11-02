/*
 * ImprovedVUflashQfunc.java
 * 
 * Enhanced VU flash with better numerical stability for separator applications
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * ImprovedVUflashQfunc class with enhanced numerical stability.
 * </p>
 * 
 * Improvements: - Bounds checking for pressure and temperature - Better damping and convergence
 * criteria - Validation of inputs and outputs - Fallback mechanisms for problematic cases
 *
 * @author GitHub Copilot
 * @version $Id: $Id
 */
public class ImprovedVUflashQfunc extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ImprovedVUflashQfunc.class);

  double Vspec = 0;
  double Uspec = 0.0;
  Flash tpFlash;

  // Bounds and validation parameters
  private static final double MIN_PRESSURE = 0.1; // bar
  private static final double MAX_PRESSURE = 1000.0; // bar
  private static final double MIN_TEMPERATURE = 200.0; // K
  private static final double MAX_TEMPERATURE = 1000.0; // K
  private static final double MAX_DAMPING = 0.5;
  private static final double MIN_DAMPING = 0.01;
  private static final double DERIVATIVE_THRESHOLD = 1e-12;

  /**
   * Constructor for ImprovedVUflashQfunc.
   */
  public ImprovedVUflashQfunc(SystemInterface system, double Vspec, double Uspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vspec = Vspec;
    this.Uspec = Uspec;
  }

  /**
   * Validates inputs before running VU flash.
   */
  private boolean validateInputs() {
    if (Vspec <= 0) {
      logger.warn("Invalid volume specification: " + Vspec);
      return false;
    }
    if (Double.isNaN(Uspec) || Double.isInfinite(Uspec)) {
      logger.warn("Invalid energy specification: " + Uspec);
      return false;
    }
    return true;
  }

  /**
   * Validates pressure and temperature bounds.
   */
  private boolean isWithinBounds(double pressure, double temperature) {
    return pressure >= MIN_PRESSURE && pressure <= MAX_PRESSURE && temperature >= MIN_TEMPERATURE
        && temperature <= MAX_TEMPERATURE;
  }

  /**
   * Calculates derivative with safety checks.
   */
  public double calcdQdPP() {
    double dVdP = system.getdVdPtn();
    double dQdVV = (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())
        + system.getPressure() * dVdP
            / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());

    // Ensure derivative is not too small
    if (Math.abs(dQdVV) < DERIVATIVE_THRESHOLD) {
      dQdVV = Math.signum(dQdVV) * DERIVATIVE_THRESHOLD;
    }
    return dQdVV;
  }

  /**
   * Calculates derivative with safety checks.
   */
  public double calcdQdTT() {
    double dQdT_val = calcdQdT();
    double dQdTT = -system.getCp()
        / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R)
        - dQdT_val / system.getTemperature();

    // Ensure derivative is not too small
    if (Math.abs(dQdTT) < DERIVATIVE_THRESHOLD) {
      dQdTT = Math.signum(dQdTT) * DERIVATIVE_THRESHOLD;
    }
    return dQdTT;
  }

  /**
   * Calculates derivative.
   */
  public double calcdQdT() {
    double dQdT = (Uspec + system.getPressure() * Vspec - system.getEnthalpy())
        / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R);
    return dQdT;
  }

  /**
   * Calculates derivative.
   */
  public double calcdQdP() {
    double dQdP = system.getPressure() * (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
    return dQdP;
  }

  /**
   * Enhanced solver with better convergence and bounds checking.
   */
  public double solveQ() {
    if (!validateInputs()) {
      logger.error("Invalid inputs for VU flash");
      return system.getPressure();
    }

    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    double nyTemp = system.getTemperature();
    double oldTemp = system.getTemperature();

    // Store initial state in case we need to revert
    double initialPres = oldPres;
    double initialTemp = oldTemp;

    double iterations = 0;
    double maxIterations = 100; // Reduced for stability
    double tolerance = 1e-8; // Relaxed tolerance

    double damping = MAX_DAMPING; // Start with stronger damping

    logger.debug("Starting VU flash: Vspec=" + Vspec + ", Uspec=" + Uspec);

    do {
      iterations++;
      oldPres = nyPres;
      oldTemp = nyTemp;

      try {
        system.init(3);

        // Calculate updates with bounds checking on derivatives
        double dQdP = calcdQdP();
        double dQdT = calcdQdT();
        double dQdPP = calcdQdPP();
        double dQdTT = calcdQdTT();

        // Calculate proposed changes
        double deltaPres = -damping * dQdP / dQdPP;
        double deltaTemp = -damping * dQdT / dQdTT;

        // Limit step sizes to prevent wild changes
        double maxPresChange = 0.2 * oldPres; // Max 20% change per step
        double maxTempChange = 50.0; // Max 50K change per step

        deltaPres = Math.max(-maxPresChange, Math.min(maxPresChange, deltaPres));
        deltaTemp = Math.max(-maxTempChange, Math.min(maxTempChange, deltaTemp));

        nyPres = oldPres + deltaPres;
        nyTemp = oldTemp + deltaTemp;

        // Enforce bounds
        nyPres = Math.max(MIN_PRESSURE, Math.min(MAX_PRESSURE, nyPres));
        nyTemp = Math.max(MIN_TEMPERATURE, Math.min(MAX_TEMPERATURE, nyTemp));

        // Check if within bounds
        if (!isWithinBounds(nyPres, nyTemp)) {
          logger.warn("VU flash exceeded bounds, reverting to initial state");
          system.setPressure(initialPres);
          system.setTemperature(initialTemp);
          return initialPres;
        }

        system.setPressure(nyPres);
        system.setTemperature(nyTemp);
        tpFlash.run();

        // Check convergence
        double presError = Math.abs((nyPres - oldPres) / nyPres);
        double tempError = Math.abs((nyTemp - oldTemp) / nyTemp);
        double totalError = presError + tempError;

        // Adaptive damping
        if (totalError > 0.1) {
          damping = Math.max(MIN_DAMPING, damping * 0.8); // Reduce damping if not converging
        } else {
          damping = Math.min(MAX_DAMPING, damping * 1.1); // Increase damping if converging well
        }

        logger.debug("Iteration " + iterations + ": P=" + nyPres + ", T=" + nyTemp + ", Error="
            + totalError + ", Damping=" + damping);

        if (totalError < tolerance) {
          break;
        }

      } catch (Exception e) {
        logger.warn("Exception in VU flash iteration " + iterations + ": " + e.getMessage());
        // Revert to initial state on exception
        system.setPressure(initialPres);
        system.setTemperature(initialTemp);
        return initialPres;
      }

    } while (iterations < maxIterations);

    if (iterations >= maxIterations) {
      logger.warn("VU flash did not converge after " + maxIterations + " iterations");
      // Consider reverting to initial state or using a fallback
    }

    return nyPres;
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
