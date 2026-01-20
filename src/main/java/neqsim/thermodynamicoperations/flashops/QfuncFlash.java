package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * QfuncFlash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class QfuncFlash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(QfuncFlash.class);

  double Hspec = 0;
  Flash tpFlash;
  int type = 0;

  /**
   * <p>
   * Constructor for QfuncFlash.
   * </p>
   */
  public QfuncFlash() {}

  /**
   * <p>
   * Constructor for QfuncFlash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Hspec a double
   * @param type a int
   */
  public QfuncFlash(SystemInterface system, double Hspec, int type) {
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
    return dQdTT;
  }

  /**
   * <p>
   * calcdQdT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdT() {
    double dQ = system.getEnthalpy() - Hspec;
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
    do {
      iterations++;
      oldTemp = nyTemp;
      system.init(3);
      nyTemp = oldTemp - calcdQdT() / calcdQdTT();
      system.setTemperature(1.0 / nyTemp);
      tpFlash.run();
    } while (Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / nyTemp)) > 1e-9 && iterations < 1000);
    return 1.0 / nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    logger.info("entropy: " + system.getEntropy());
    SysNewtonRhapsonPHflash secondOrderSolver =
        new SysNewtonRhapsonPHflash(system, 2, system.getPhases()[0].getNumberOfComponents(), type);
    secondOrderSolver.setSpec(Hspec);
    secondOrderSolver.solve(1);
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
