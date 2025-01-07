/*
 * TSFlash.java
 *
 * Created on 8. mars 2001, 10:56
 */

package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TSFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class TSFlash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for TSFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   */
  public TSFlash(SystemInterface system, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdTT() {
    if (system.getNumberOfPhases() == 1) {
      return -system.getPhase(0).getCp() / system.getTemperature();
    }

    double dQdTT = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
    }
    return dQdTT;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdT() {
    double dQ = -system.getEntropy() + Sspec;
    return dQ;
  }

  /** {@inheritDoc} */
  @Override
  public double solveQ() {
    // this method is not yet implemented
    double oldTemp = system.getPressure(), nyTemp = system.getPressure();
    int iterations = 1;
    double error = 1.0, erorOld = 10.0e10;
    do {
      iterations++;
      oldTemp = system.getPressure();
      system.init(2);

      nyTemp = oldTemp - calcdQdT() / 10.0;

      system.setPressure(nyTemp);
      tpFlash.run();
      erorOld = error;
      error = Math.abs(calcdQdT());
    } while (((error + erorOld) > 1e-8 || iterations < 3) && iterations < 200);
    return nyTemp;
  }

  /**
   * <p>
   * onPhaseSolve.
   * </p>
   */
  public void onPhaseSolve() {}

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    solveQ();
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(373.15, 45.551793);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 9.4935);
    testSystem.addComponent("ethane", 5.06499);
    testSystem.addComponent("n-heptane", 0.2);
    testSystem.init(0);
    try {
      testOps.TPflash();
      testSystem.display();

      double Sspec = testSystem.getEntropy("kJ/kgK");
      logger.info("S spec " + Sspec);
      testSystem.setTemperature(293.15);
      testOps.TSflash(Sspec, "kJ/kgK");
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
