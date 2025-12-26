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
  double Vfractionspec = 0;
  Flash tpFlash;
  double oldPres = 1.0;
  private boolean reportedUncountableState = false;


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
    double error = 100.0;
    double errorOld = error;
    double pressureStep = 1.0;
    double dampingFactor = 100.0; // Adaptive damping parameter

    do {
      iterations++;
      system.init(3);
      oldPres = nyPres;
      double dqdv = calcdQdV();
      double dqdvdp = calcdQdVdP();

      // Adaptive damping: reduce damping if converging well
      if (iterations > 3 && error < errorOld * 0.9) {
        dampingFactor = Math.max(20.0, dampingFactor * 0.9);
      }

      double stepFraction = iterations / (iterations + dampingFactor);
      nyPres = oldPres - stepFraction * dqdv / dqdvdp;
      pressureStep = nyPres - oldPres;

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
      } else {
        logger.error("too high pressure in TVfractionFLash.....stopping");
        break;
      }

      errorOld = error;
      error = Math.abs(dqdv / Vfractionspec);

      logger.debug("iter {} pressure {:.6f} error {:.3e} damping {:.1f}", iterations, nyPres, error,
          dampingFactor);

      // Convergence check with early exit
      if (error < 1e-8 && iterations > 3) {
        logger.debug("Early convergence achieved at iteration {}", iterations);
        break;
      }

    } while ((error > 1e-6 && Math.abs(pressureStep) > 1e-6 && iterations < 200) || iterations < 6);

    if (error > 1e-4) {
      logger.warn("TVfractionFlash converged with high error: {:.3e} after {} iterations", error,
          iterations);
    }

    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();

    if (stateHasUncountableNumbers(system)) {
      system.setPressure(oldPres);
    }

    if (system.getNumberOfPhases() == 1 || !system.hasPhaseType("gas")) {
      do {
        system.setPressure(system.getPressure() * 0.9);
        tpFlash.run();
      } while (system.getNumberOfPhases() == 1);
    }

    // System.out.println("enthalpy: " + system.getEnthalpy());
    try {
      solveQ();
    } catch (Exception e) {
      throw e;
    }
    // System.out.println("volume: " + system.getVolume());
    // System.out.println("Temperature: " + system.getTemperature());
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
