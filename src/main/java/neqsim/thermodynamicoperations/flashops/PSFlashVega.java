package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSFlashVega class.
 * </p>
 *
 * @author victorigi99
 * @version $Id: $Id
 */
public class PSFlashVega extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PSFlashVega.class);

  double Sspec = 0;
  Flash tpFlash;

  double entropy_Vega = 0.0;
  double cP_Vega = 0.0;

  /**
   * <p>
   * Constructor for PSFlashVega.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   */
  public PSFlashVega(SystemInterface system, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdTT() {
    return -cP_Vega / system.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdT() {
    return -entropy_Vega + Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public double solveQ() {
    double oldTemp = system.getTemperature();
    double nyTemp = system.getTemperature();
    int iterations = 1;
    double error = 1.0;
    double errorOld = 10.0e10;
    double factor = 0.8;

    boolean correctFactor = true;
    double newCorr = 1.0;
    double[] VegaProps;
    do {
      if (error > errorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < errorOld && correctFactor) {
        factor = 1.0;
      }

      iterations++;
      oldTemp = system.getTemperature();
      VegaProps = system.getPhase(0).getProperties_Vega();
      entropy_Vega = VegaProps[8] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
      cP_Vega = VegaProps[10] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
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
      errorOld = error;
      error = Math.abs(calcdQdT()); // Math.abs((nyTemp - oldTemp) / (nyTemp));
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
    return nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    if (system.getNumberOfPhases() > 1) {
      logger.error("PSFlashVega only supprt single phase gas calculations");
      return;
    }
    solveQ();
    system.init(3);
  }
}
