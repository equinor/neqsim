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
    double pressureStep = 1.0;
    do {
      iterations++;
      system.init(3);
      oldPres = nyPres;
      double dqdv = calcdQdV();
      double dqdvdp = calcdQdVdP();
      nyPres = oldPres - iterations / (iterations + 100.0) * dqdv / dqdvdp;
      pressureStep = nyPres - oldPres;

      if (nyPres <= 0.0) {
        nyPres = oldPres * 0.9;
      }
      if (Math.abs(nyPres - oldPres) >= 10.0) {
        nyPres = oldPres + Math.signum(nyPres - oldPres) * 10.0;
      }
      system.setPressure(nyPres);
      if (system.getPressure() < 5000) {
        tpFlash.run();
      } else {
        logger.error("too high pressure in TVfractionFLash.....stopping");
        break;
      }

      error = Math.abs(dqdv / Vfractionspec);
      logger.debug("pressure " + nyPres + "  iteration " + iterations);
      // System.out.println("error " + error + "iteration " + iterations + " dQdv " +
      // calcdQdV()
      // + " new pressure " + nyPres + " error " + Math.abs((nyPres - oldPres) /
      // (nyPres))
      // + " numberofphases " + system.getNumberOfPhases());
    } while ((error > 1e-6 && Math.abs(pressureStep) > 1e-6 && iterations < 200) || iterations < 6);
    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();

    if (stateHasUncountableNumbers(system)) {
      if (!reportedUncountableState) {
        logger.error("Solution contains uncountable numbers");
        reportedUncountableState = true;
      }
      system.setPressure(oldPres);
    }

    if (system.getNumberOfPhases() == 1) {
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
    if (!reportedUncountableState) {
      if (componentName != null) {
        logger.error(
            "Solution contains uncountable numbers: {} for component '{}' in phase {} (value={})",
            field, componentName, phaseIndex, value);
      } else {
        logger.error("Solution contains uncountable numbers: {} in phase {} (value={})", field,
            phaseIndex, value);
      }
    }
  }
}
