package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PSFlashLeachman class.
 * </p>
 *
 * @author victorigi99
 * @version $Id: $Id
 */
public class PSFlashLeachman extends QfuncFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PSFlashLeachman.class);

  double Sspec = 0;
  Flash tpFlash;

  double entropy_Leachman = 0.0;
  double cP_Leachman = 0.0;

  /**
   * <p>
   * Constructor for PSFlashLeachman.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Sspec a double
   */
  public PSFlashLeachman(SystemInterface system, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdTT() {
    return -cP_Leachman / system.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double calcdQdT() {
    return -entropy_Leachman + Sspec;
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
    double[] LeachmanProps;

    // Get initial properties and check for early exit
    LeachmanProps = system.getPhase(0).getProperties_Leachman();
    entropy_Leachman = LeachmanProps[8] * system.getPhase(0).getNumberOfMolesInPhase();
    double initialError = Math.abs(-entropy_Leachman + Sspec);
    if (initialError < 1e-10) {
      return nyTemp;
    }

    do {
      if (error > errorOld && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (error < errorOld && correctFactor) {
        factor = 1.0;
      }

      iterations++;
      oldTemp = system.getTemperature();
      LeachmanProps = system.getPhase(0).getProperties_Leachman();
      entropy_Leachman = LeachmanProps[8] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
      cP_Leachman = LeachmanProps[10] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K

      // Inline calcdQdT and calcdQdTT to avoid method call overhead
      double dQdT = -entropy_Leachman + Sspec;
      double dQdTT = -cP_Leachman / system.getTemperature();
      newCorr = factor * dQdT / dQdTT;
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
      error = Math.abs(-entropy_Leachman + Sspec);
    } while (((error + errorOld) > 1e-8 || iterations < 3) && iterations < 200);
    return nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    if (system.getNumberOfPhases() > 1) {
      logger.error("PSFlashLeachman only supprt single phase gas calculations");
      return;
    }
    solveQ();
    system.init(3);
  }
}
