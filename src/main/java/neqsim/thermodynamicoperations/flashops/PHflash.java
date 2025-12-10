/*
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PHflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PHflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PHflash.class);

  double Hspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * <p>
   * Constructor for PHflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   * @param type a int
   */
  public PHflash(SystemInterface system, double Hspec, int type) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Hspec = Hspec;
    this.type = type;
  }

  /**
   * <p>
   * calcdQdTT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdTT() {
    double dQdTT = -system.getTemperature() * system.getTemperature() * system.getCp();
    return dQdTT / Math.abs(Hspec);
  }

  /**
   * <p>
   * calcdQdT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdT() {
    double dQ = (system.getEnthalpy() - Hspec) / Math.abs(Hspec);
    return dQ;
  }

  /**
   * Estimates initial temperature for PHflash using Cp approximation. This provides a better
   * starting point for the Newton-Raphson iteration, reducing the number of iterations needed.
   *
   * @return estimated temperature in Kelvin
   */
  private double estimateInitialTemperature() {
    double currentEnthalpy = system.getEnthalpy();
    double deltaH = Hspec - currentEnthalpy;

    // If enthalpy difference is very small, current temperature is good
    if (Math.abs(deltaH) < 1e-6 * Math.abs(Hspec)) {
      return system.getTemperature();
    }

    // Use Cp to estimate temperature change: deltaH = Cp * deltaT
    double cp = system.getCp();
    if (cp > 1e-10) {
      double deltaT = deltaH / cp;
      // Limit the temperature change to avoid overshooting - be conservative
      double maxDeltaT = 30.0; // Maximum 30K change in initial estimate
      if (Math.abs(deltaT) > maxDeltaT) {
        deltaT = Math.signum(deltaT) * maxDeltaT;
      }
      double estimatedTemp = system.getTemperature() + deltaT;
      // Ensure temperature stays within reasonable bounds relative to current
      double currentTemp = system.getTemperature();
      double minAllowed = Math.max(50.0, currentTemp - 50.0);
      double maxAllowed = Math.min(1000.0, currentTemp + 50.0);
      if (estimatedTemp < minAllowed) {
        estimatedTemp = minAllowed;
      } else if (estimatedTemp > maxAllowed) {
        estimatedTemp = maxAllowed;
      }
      return estimatedTemp;
    }
    return system.getTemperature();
  }

  /**
   * <p>
   * solveQ.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    // Use improved initial temperature estimate
    system.init(2);
    double estimatedTemp = estimateInitialTemperature();
    system.setTemperature(estimatedTemp);

    double oldTemp = 1.0 / system.getTemperature();
    double nyTemp = 1.0 / system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;

    // Do initial flash with estimated temperature
    tpFlash.run();
    system.init(2);

    boolean correctFactor = true;
    double maxTemperature = 1e10;
    double minTemperature = 0.0;

    // Check if we're already converged after initial estimate
    error = calcdQdT();
    if (Math.abs(error) < 1e-8) {
      return system.getTemperature();
    }

    do {
      if (Math.abs(error) > Math.abs(errorOld) && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (Math.abs(error) < Math.abs(errorOld) && correctFactor) {
        factor = iterations / (iterations + 1.0) * 1.0;
      }
      iterations++;
      oldTemp = nyTemp;
      newCorr = factor * calcdQdT() / calcdQdTT();
      nyTemp = oldTemp - newCorr;

      if (iterations > 150 && Math.abs(error) < 10.0) {
        nyTemp = 1.0 / (system.getTemperature()
            - Math.signum(system.getTemperature() - 1.0 / nyTemp) * Math.abs(error));
        correctFactor = false;
      } else if (Math.abs(system.getTemperature() - 1.0 / nyTemp) > 10.0) {
        nyTemp = 1.0 / (system.getTemperature()
            - Math.signum(system.getTemperature() - 1.0 / nyTemp) * 10.0);
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(1.0 / (system.getTemperature() + 10.0));
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 0.1;
        correctFactor = false;
      } else {
        correctFactor = true;
      }
      system.setTemperature(1.0 / nyTemp);
      if (system.getTemperature() > maxTemperature) {
        system.setTemperature(maxTemperature - 0.1);
      } else if (system.getTemperature() < minTemperature) {
        system.setTemperature(minTemperature + 0.1);
      }
      tpFlash.run();
      system.init(2);
      errorOld = error;
      error = calcdQdT();

      if (error > 0 && system.getTemperature() > maxTemperature) {
        maxTemperature = system.getTemperature();
      } else if (error < 0 && system.getTemperature() < minTemperature) {
        minTemperature = system.getTemperature();
      }
    } while (((Math.abs(error) + Math.abs(errorOld)) > 1e-8 || iterations < 3) && iterations < 200);
    return 1.0 / nyTemp;
  }

  /**
   * <p>
   * solveQ2.
   * </p>
   *
   * @return a double
   */
  public double solveQ2() {
    double oldTemp = 1.0 / system.getTemperature();
    double nyTemp = 1.0 / system.getTemperature();
    double iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;
    // System.out.println("temp start " + system.getTemperature());
    do {
      if (error > errorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < errorOld && correctFactor) {
        factor = iterations / (iterations + 1.0) * 1.0;
      }
      iterations++;
      oldTemp = nyTemp;
      newCorr = factor * calcdQdT() / calcdQdTT();
      nyTemp = oldTemp - newCorr;
      if (Math.abs(system.getTemperature() - 1.0 / nyTemp) > 10.0) {
        nyTemp = 1.0 / (system.getTemperature()
            - Math.signum(system.getTemperature() - 1.0 / nyTemp) * 10.0);
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(1.0 / (system.getTemperature() + 10.0));
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 0.1;
        correctFactor = false;
      } else {
        correctFactor = true;
      }
      system.setTemperature(1.0 / nyTemp);
      tpFlash.run();
      system.init(2);
      errorOld = error;
      error = Math.abs(calcdQdT());
      // error = Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / oldTemp));
      // if(iterations>100) System.out.println("temp " + system.getTemperature() + "
      // iter "+ iterations + " error "+ error + " correction " + newCorr + " factor
      // "+ factor);
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
    // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
    // " error "+ error );
    return 1.0 / nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    // System.out.println("enthalpy start: " + system.getEnthalpy());
    if (type == 0) {
      solveQ();
    } else {
      SysNewtonRhapsonPHflash secondOrderSolver =
          new SysNewtonRhapsonPHflash(system, 2, system.getPhases()[0].getNumberOfComponents(), 0);
      secondOrderSolver.setSpec(Hspec);
      secondOrderSolver.solve(1);
    }
    // System.out.println("enthalpy: " + system.getEnthalpy());
    // System.out.println("Temperature: " + system.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
