package neqsim.thermodynamicoperations.flashops;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PHflashLeachman class.
 * </p>
 *
 * @author victorigi99
 * @version $Id: $Id
 */
public class PHflashLeachman extends Flash{
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Hspec = 0;
  Flash tpFlash;
  double enthalpy_Leachman = 0.0;
  double cP_Leachman = 0.0;

  /**
   * <p>
   * Constructor for PHflashLeachman.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   */
  public PHflashLeachman(SystemInterface system, double Hspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Hspec = Hspec;
  }

  /**
   * <p>
   * calcdQdTT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdTT() {
    double dQdTT = -system.getTemperature() * system.getTemperature() * cP_Leachman;
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
    double dQ = (enthalpy_Leachman - Hspec) / Math.abs(Hspec);
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
    double iterations = 1;
    double error = 1.0;
    double erorOld = 1.0e10;
    double factor = 0.8;
    double newCorr = 1.0;
    system.init(2);
    boolean correctFactor = true;

    double maxTemperature = 1e10;
    double minTemperature = 0.0;
    do {
      if (Math.abs(error) > Math.abs(erorOld) && factor > 0.1 && correctFactor) {
        factor *= 0.5;
      } else if (Math.abs(error) < Math.abs(erorOld) && correctFactor) {
        factor = iterations / (iterations + 1.0) * 1.0;
      }
      iterations++;
      oldTemp = nyTemp;
      double[] LeachmanProps = system.getPhase(0).getProperties_Leachman();
      cP_Leachman = LeachmanProps[10] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
      enthalpy_Leachman = LeachmanProps[7] * system.getPhase(0).getNumberOfMolesInPhase(); // J/mol K
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
      if (system.getTemperature() > maxTemperature) {
        system.setTemperature(maxTemperature - 0.1);
      } else if (system.getTemperature() < minTemperature) {
        system.setTemperature(minTemperature + 0.1);
      }
      erorOld = error;
      error = calcdQdT();

      if (error > 0 && system.getTemperature() > maxTemperature) {
        maxTemperature = system.getTemperature();
      } else if (error < 0 && system.getTemperature() < minTemperature) {
        minTemperature = system.getTemperature();
      }

      /*
       * if (false && error * erorOld < 0) { system.setTemperature( (Math.abs(erorOld) * 1.0 /
       * oldTemp + Math.abs(error) * 1.0 / nyTemp) / (Math.abs(erorOld) + Math.abs(error))); erorOld
       * = error; error = calcdQdT(); System.out.println("reset temperature -- new temp " +
       * system.getTemperature() + " error " + error + " iter " + iterations); } // error =
       * Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / oldTemp)); // System.out.println("temp " +
       * system.getTemperature() + " iter "+ iterations + // " error "+ error + " correction " +
       * newCorr + " factor "+ factor);
       */
    } while (((Math.abs(error) + Math.abs(erorOld)) > 1e-8 || iterations < 3) && iterations < 200);
    // System.out.println("temp " + system.getTemperature() + " iter " + iterations
    // + " error " + error);
    return 1.0 / nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    if (system.getNumberOfPhases() > 1) {
      logger.error("PHflashLeachman only supports single-phase gas calculations");
      return;
    }
    solveQ();
    system.init(3);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}



