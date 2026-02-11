/*
 * PHflash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

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
   * <p>
   * solveQ.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    double oldTemp = 1.0 / system.getTemperature();
    double nyTemp = 1.0 / system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;
    double maxTemperature = 1e10;
    double minTemperature = 0.0;
    int noProgressCount = 0;
    double bestError = Double.MAX_VALUE;
    double prevBestError = Double.MAX_VALUE;

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

      // Detect convergence stagnation due to numerical noise floor.
      // In multi-phase CPA systems, phase equilibrium tolerances in constituent
      // solvers (RR, stability analysis, volume root) create an error floor
      // typically around 1e-6 to 1e-5. Once error stops improving for several
      // consecutive iterations, we accept the solution.
      double absError = Math.abs(error);
      if (absError < bestError) {
        prevBestError = bestError;
        bestError = absError;
        noProgressCount = 0;
      } else {
        noProgressCount++;
      }
      if (noProgressCount >= 3 && bestError < 1e-3 && iterations > 5) {
        break;
      }
      if (bestError < 1e-4 && prevBestError < 1e-3 && iterations > 10
          && bestError / prevBestError > 0.5) {
        break;
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
    int iterations = 1;
    double error = 1.0;
    double errorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;
    int noProgressCount = 0;
    double bestError = Double.MAX_VALUE;
    double prevBestError = Double.MAX_VALUE;
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

      // Detect convergence stagnation due to numerical noise floor.
      if (error < bestError) {
        prevBestError = bestError;
        bestError = error;
        noProgressCount = 0;
      } else {
        noProgressCount++;
      }
      if (noProgressCount >= 3 && bestError < 1e-3 && iterations > 5) {
        break;
      }
      if (bestError < 1e-4 && prevBestError < 1e-3 && iterations > 10
          && bestError / prevBestError > 0.5) {
        break;
      }
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
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
