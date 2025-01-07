package neqsim.thermodynamicoperations.flashops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * VHflashQfunc class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class VHflashQfunc extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VHflashQfunc.class);

  double Vspec = 0;
  double Hspec = 0.0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for VHflashQfunc.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec a double
   * @param Hspec a double
   */
  public VHflashQfunc(SystemInterface system, double Vspec, double Hspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Vspec = Vspec;
    this.Hspec = Hspec;
  }

  /**
   * <p>
   * calcdQdPP.
   * </p>
   *
   * @return a double
   */
  public double calcdQdPP() {
    double dQdVV = (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature())
        + system.getPressure() * (system.getdVdPtn())
            / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
    return dQdVV;
  }

  /**
   * <p>
   * calcdQdTT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdTT() {
    double dQdTT = -system.getCp()
        / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R)
        - calcdQdT() / system.getTemperature();
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
    double dQdT = (Hspec - system.getEnthalpy())
        / (system.getTemperature() * neqsim.thermo.ThermodynamicConstantsInterface.R);
    return dQdT;
  }

  /**
   * <p>
   * calcdQdP.
   * </p>
   *
   * @return a double
   */
  public double calcdQdP() {
    double dQdP = system.getPressure() * (system.getVolume() - Vspec)
        / (neqsim.thermo.ThermodynamicConstantsInterface.R * system.getTemperature());
    return dQdP;
  }

  /**
   * <p>
   * solveQ.
   * </p>
   *
   * @return a double
   */
  public double solveQ() {
    double oldPres = system.getPressure();
    double nyPres = system.getPressure();
    double nyTemp = system.getTemperature();
    double oldTemp = system.getTemperature();
    double iterations = 1;
    // logger.info("Vspec: " + Vspec);
    // logger.info("Uspec: " + Uspec);
    do {
      iterations++;
      oldPres = nyPres;
      oldTemp = nyTemp;
      system.init(3);
      // logger.info("dQdP: " + calcdQdP());
      // logger.info("dQdT: " + calcdQdT());
      nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdP() / calcdQdPP();
      nyTemp = oldTemp - (iterations) / (iterations + 10.0) * calcdQdT() / calcdQdTT();
      // logger.info("volume: " + system.getVolume());
      // logger.info("inernaleng: " + system.getInternalEnergy());
      system.setPressure(nyPres);
      system.setTemperature(nyTemp);
      tpFlash.run();
      // logger.info("error1: " + Math.abs((nyPres - oldPres) / (nyPres)));
      // logger.info("error2: " + Math.abs((nyTemp - oldTemp) / (nyTemp)));
      // logger.info("inernaleng: " + system.getInternalEnergy());
    } while (Math.abs((nyPres - oldPres) / (nyPres))
        + Math.abs((nyTemp - oldTemp) / (nyTemp)) > 1e-9 && iterations < 1000);
    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
    // logger.info("internaleng: " + system.getInternalEnergy());
    // logger.info("volume: " + system.getVolume());
    solveQ();
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
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
    SystemInterface testSystem = new SystemSrkEos(273.15 + 55, 50.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 31.0);
    testSystem.addComponent("ethane", 4.0);
    testSystem.addComponent("n-heptane", 0.2);
    testSystem.init(0);
    try {
      testOps.TPflash();
      testSystem.display();

      double energy = testSystem.getEnthalpy() * 1.1;
      double volume = testSystem.getVolume() * 0.9;

      testOps.VHflash(volume, energy);
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
