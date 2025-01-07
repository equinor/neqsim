package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSFlash extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * <p>
   * Constructor for PSFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   * @param type a int
   */
  public PSFlash(SystemInterface system, double Sspec, int type) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
    this.type = type;
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
    double oldTemp = system.getTemperature();
    double nyTemp = system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double erorOld = 10.0e10;
    double factor = 0.8;

    boolean correctFactor = true;
    double newCorr = 1.0;
    system.init(2);

    do {
      if (error > erorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < erorOld && correctFactor) {
        factor = 1.0;
      }

      iterations++;
      oldTemp = system.getTemperature();

      newCorr = factor * calcdQdT() / calcdQdTT();
      nyTemp = oldTemp - newCorr;
      if (Math.abs(system.getTemperature() - nyTemp) > 10.0) {
        nyTemp = system.getTemperature() - Math.signum(system.getTemperature() - nyTemp) * 10.0;
        correctFactor = false;
      } else if (nyTemp < 0) {
        nyTemp = Math.abs(system.getTemperature() - 10.0);
        correctFactor = false;
      } else if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 1.0;
        correctFactor = false;
      } else {
        correctFactor = true;
      }

      system.setTemperature(nyTemp);
      tpFlash.run();
      system.init(2);
      erorOld = error;
      error = Math.abs(calcdQdT()); // Math.abs((nyTemp - oldTemp) / (nyTemp));
      // if(error>erorOld) factor *= -1.0;
      // System.out.println("temp " + system.getTemperature() + " iter "+ iterations +
      // " error "+ error + " correction " + newCorr + " factor "+ factor);
      // newCorr = Math.abs(factor * calcdQdT() / calcdQdTT());
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

    if (type == 0) {
      solveQ();
    } else {
      SysNewtonRhapsonPHflash secondOrderSolver =
          new SysNewtonRhapsonPHflash(system, 2, system.getPhases()[0].getNumberOfComponents(), 1);
      secondOrderSolver.setSpec(Sspec);
      secondOrderSolver.solve(1);
    }
    // System.out.println("Entropy: " + system.getEntropy());
    // System.out.println("Temperature: " + system.getTemperature());
  }
}
