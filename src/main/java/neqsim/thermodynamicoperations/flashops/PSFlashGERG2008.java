package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSFlashGERG2008 class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PSFlashGERG2008 extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PSFlashGERG2008.class);

  double Sspec = 0;
  Flash tpFlash;

  double entropy_GERG2008 = 0.0;
  double cP_GERG2008 = 0.0;

  /**
   * <p>
   * Constructor for PSFlashGERG2008.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   */
  public PSFlashGERG2008(SystemInterface system, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdTT() {
    return -cP_GERG2008 / system.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdT() {
    return -entropy_GERG2008 + Sspec;
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
    double[] gergProps;
    do {
      if (error > erorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < erorOld && correctFactor) {
        factor = 1.0;
      }

      iterations++;
      oldTemp = system.getTemperature();
      gergProps = system.getPhase(0).getProperties_GERG2008();
      entropy_GERG2008 = gergProps[8] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
      cP_GERG2008 = gergProps[10] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
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
      erorOld = error;
      error = Math.abs(calcdQdT()); // Math.abs((nyTemp - oldTemp) / (nyTemp));
    } while (((error + erorOld) > 1e-8 || iterations < 3) && iterations < 200);
    return nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    if (system.getNumberOfPhases() > 1) {
      logger.error("PSFlashGERG2008 only supprt single phase gas calculations");
      return;
    }
    solveQ();
    system.init(3);
  }
}
