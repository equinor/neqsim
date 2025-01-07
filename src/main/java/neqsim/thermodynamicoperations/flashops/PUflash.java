package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * PUflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class PUflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Uspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for PUflash.
   * </p>
   */
  public PUflash() {}

  /**
   * <p>
   * Constructor for PUflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Uspec a double
   */
  public PUflash(SystemInterface system, double Uspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Uspec = Uspec;
  }

  /**
   * <p>
   * calcdQdTT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdTT() {
    double dQdTT = -system.getTemperature() * system.getTemperature() * system.getCv();
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
    double dQ = system.getInternalEnergy() - Uspec;
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
    double erorOld = 10.0e10;
    double factor = 0.8;
    do {
      if (error > erorOld) {
        factor /= 2.0;
      } else if (error < erorOld && factor < 0.8) {
        factor *= 1.1;
      }
      iterations++;
      oldTemp = nyTemp;
      system.init(2);
      nyTemp = oldTemp - factor * calcdQdT() / calcdQdTT();
      // f(Math.abs(1.0/nyTemp-1.0/oldTemp)>5.0) nyTemp = 1.0/(1.0/oldTemp +
      // Math.signum(1.0/nyTemp-1.0/oldTemp)*5.0);
      if (Double.isNaN(nyTemp)) {
        nyTemp = oldTemp + 1.0;
      }
      system.setTemperature(1.0 / nyTemp);
      tpFlash.run();
      erorOld = error;
      error = Math.abs((1.0 / nyTemp - 1.0 / oldTemp) / (1.0 / oldTemp));
      // System.out.println("error " + error);
      // System.out.println("temperature " + system.getTemperature() + " " +
      // iterations);
    } while (error > 1e-8 && iterations < 500);

    return 1.0 / nyTemp;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    // System.out.println("internal energy start: " + system.getInternalEnergy());
    solveQ();
    // System.out.println("internal energy end: " + system.getInternalEnergy());
    // System.out.println("enthalpy: " + system.getEnthalpy());
    // System.out.println("Temperature: " + system.getTemperature());
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }
}
