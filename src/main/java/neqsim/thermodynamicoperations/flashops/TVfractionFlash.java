/*
 * TVflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * TVflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TVfractionFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TVfractionFlash.class);
  /** Maximum consecutive failures before aborting. */
  private static final int MAX_CONSECUTIVE_FAILURES = 3;
  double Vfractionspec = 0;
  Flash tpFlash;
  double oldPres = 1.0;
  private boolean reportedUncountableState = false;
  /** Flag indicating if the flash calculation converged successfully. */
  private boolean converged = false;

  /**
   * <p>
   * Constructor for TVflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vfractionspec a double
   */
  public TVfractionFlash(SystemInterface system, double Vfractionspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vfractionspec = Vfractionspec;
  }

  /**
   * <p>
   * calcdQdVP.
   * </p>
   *
   * @return a double
   */
  public double calcdQdVdP() {
    double dQdVP = 1.0 / system.getPhase(0).getdPdVTn() / system.getVolume()
        + system.getPhase(0).getVolume() / Math.pow(system.getVolume(), 2.0) * system.getdVdPtn();
    return dQdVP;
  }

  /**
   * <p>
   * calcdQdV.
   * </p>
   *
   * @return a double
   */
  public double calcdQdV() {
    double dQ = system.getPhase(0).getVolume() / system.getVolume() - Vfractionspec;
    return dQ;
  }

  /**
   * <p>
   * solveQ.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    oldPres = system.getPressure();
    double nyPres = system.getPressure();
    int iterations = 0;
    int consecutiveFailures = 0;
    double error = 100.0;
    double errorOld = error;
    double pressureStep = 1.0;
    double dampingFactor = 100.0; // Adaptive damping parameter
    double lastValidPressure = nyPres;

    do {
      iterations++;
      system.init(3);

      // Check for uncountable state after init
      if (stateHasUncountableNumbers(system)) {
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
          logger.debug("TVfractionFlash aborting after {} consecutive failures",
              consecutiveFailures);
          system.setPressure(lastValidPressure);
          converged = false;
          return lastValidPressure;
        }
        // Try recovering with a smaller pressure step
        nyPres = lastValidPressure * 0.95;
        system.setPressure(nyPres);
        tpFlash.run();
        continue;
      }

      consecutiveFailures = 0;
      lastValidPressure = nyPres;

      oldPres = nyPres;
      double dqdv = calcdQdV();
      double dqdvdp = calcdQdVdP();

      // Validate derivatives - abort if calculations produce invalid values
      if (!Double.isFinite(dqdv) || !Double.isFinite(dqdvdp) || Math.abs(dqdvdp) < 1e-30) {
        logger.debug("TVfractionFlash: invalid derivatives dqdv={} dqdvdp={}, aborting", dqdv,
            dqdvdp);
        system.setPressure(lastValidPressure);
        converged = false;
        return lastValidPressure;
      }

      // Adaptive damping: reduce damping if converging well
      if (iterations > 3 && error < errorOld * 0.9) {
        dampingFactor = Math.max(20.0, dampingFactor * 0.9);
      }

      double stepFraction = iterations / (iterations + dampingFactor);
      nyPres = oldPres - stepFraction * dqdv / dqdvdp;
      pressureStep = nyPres - oldPres;

      // Validate new pressure
      if (!Double.isFinite(nyPres)) {
        logger.debug("TVfractionFlash: calculated pressure is not finite ({}), aborting", nyPres);
        system.setPressure(lastValidPressure);
        converged = false;
        return lastValidPressure;
      }

      // Prevent negative pressure
      if (nyPres <= 0.0) {
        nyPres = oldPres * 0.9;
      }

      // Limit large pressure steps
      double maxStep = Math.min(10.0, Math.abs(oldPres) * 0.5);
      if (Math.abs(pressureStep) > maxStep) {
        nyPres = oldPres + Math.signum(pressureStep) * maxStep;
        pressureStep = nyPres - oldPres;
      }

      system.setPressure(nyPres);
      if (system.getPressure() < 5000) {
        tpFlash.run();

        // Check state after flash
        if (stateHasUncountableNumbers(system)) {
          consecutiveFailures++;
          if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            logger.debug("TVfractionFlash aborting after {} consecutive flash failures",
                consecutiveFailures);
            system.setPressure(lastValidPressure);
            converged = false;
            return lastValidPressure;
          }
          continue;
        }
      } else {
        logger.debug("Too high pressure in TVfractionFlash ({} bar), stopping",
            system.getPressure());
        system.setPressure(lastValidPressure);
        converged = false;
        return lastValidPressure;
      }

      errorOld = error;
      error = Math.abs(dqdv / Vfractionspec);

      // Check for diverging error
      if (iterations > 10 && error > errorOld * 2.0) {
        logger.debug("TVfractionFlash: error diverging ({}), aborting", error);
        system.setPressure(lastValidPressure);
        converged = false;
        return lastValidPressure;
      }

      logger.trace("iter {} pressure {:.6f} error {:.3e} damping {:.1f}", iterations, nyPres, error,
          dampingFactor);

      // Convergence check with early exit
      if (error < 1e-8 && iterations > 3) {
        logger.trace("Early convergence achieved at iteration {}", iterations);
        converged = true;
        break;
      }
    } while ((error > 1e-6 && Math.abs(pressureStep) > 1e-6 && iterations < 200) || iterations < 6);

    converged = (error <= 1e-4);

    if (!converged) {
      logger.debug("TVfractionFlash converged with high error: {} after {} iterations", error,
          iterations);
    }

    return nyPres;
  }

  /**
   * Check if the flash calculation converged successfully.
   *
   * @return true if converged, false otherwise
   */
  public boolean isConverged() {
    return converged;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    converged = false;
    double initialPressure = system.getPressure();
    int phaseSearchAttempts = 0;
    final int MAX_PHASE_SEARCH_ATTEMPTS = 20;

    tpFlash.run();

    // Check for initial flash failure
    if (stateHasUncountableNumbers(system)) {
      logger.debug("TVfractionFlash: initial flash produced invalid state, aborting");
      system.setPressure(initialPressure);
      return;
    }

    // Try to establish two-phase state if needed
    if (system.getNumberOfPhases() == 1 || !system.hasPhaseType("gas")) {
      while (phaseSearchAttempts < MAX_PHASE_SEARCH_ATTEMPTS) {
        phaseSearchAttempts++;
        system.setPressure(system.getPressure() * 0.9);

        // Check for pressure getting too low
        if (system.getPressure() < 1e-6) {
          logger.debug("TVfractionFlash: pressure too low during phase search, aborting");
          system.setPressure(initialPressure);
          return;
        }

        tpFlash.run();

        if (stateHasUncountableNumbers(system)) {
          logger.debug("TVfractionFlash: invalid state during phase search, aborting");
          system.setPressure(initialPressure);
          return;
        }

        if (system.getNumberOfPhases() > 1 && system.hasPhaseType("gas")) {
          break;
        }
      }

      if (phaseSearchAttempts >= MAX_PHASE_SEARCH_ATTEMPTS) {
        logger.debug("TVfractionFlash: could not establish two-phase state after {} attempts",
            MAX_PHASE_SEARCH_ATTEMPTS);
        system.setPressure(initialPressure);
        return;
      }
    }

    solveQ();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  private boolean stateHasUncountableNumbers(SystemInterface sys) {
    for (int phaseIndex = 0; phaseIndex < sys.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = sys.getPhase(phaseIndex);
      if (phase == null) {
        continue;
      }
      if (!Double.isFinite(phase.getBeta())) {
        reportNonFinite("phase beta", phaseIndex, null, phase.getBeta());
        return true;
      }
      if (!Double.isFinite(phase.getZ())) {
        reportNonFinite("phase Z", phaseIndex, null, phase.getZ());
        return true;
      }
      for (int compIndex = 0; compIndex < phase.getNumberOfComponents(); compIndex++) {
        ComponentInterface component = phase.getComponent(compIndex);
        double x = component.getx();
        if (!Double.isFinite(x) || x < 0.0) {
          reportNonFinite("component x", phaseIndex, component.getComponentName(), x);
          return true;
        }
        double logPhi = component.getLogFugacityCoefficient();
        if (!Double.isFinite(logPhi)) {
          reportNonFinite("component logPhi", phaseIndex, component.getComponentName(), logPhi);
          return true;
        }
        double phi = component.getFugacityCoefficient();
        if (!Double.isFinite(phi) || phi <= 0.0) {
          reportNonFinite("component phi", phaseIndex, component.getComponentName(), phi);
          return true;
        }
      }
    }
    return false;
  }

  private void reportNonFinite(String field, int phaseIndex, String componentName, double value) {
    if (reportedUncountableState) {
      return;
    }

    reportedUncountableState = true;

    if (componentName != null) {
      logger.debug(
          "Solution contains uncountable numbers: {} for component '{}' in phase {} (value={})",
          field, componentName, phaseIndex, value);
    } else {
      logger.debug("Solution contains uncountable numbers: {} in phase {} (value={})", field,
          phaseIndex, value);
    }
  }
}
