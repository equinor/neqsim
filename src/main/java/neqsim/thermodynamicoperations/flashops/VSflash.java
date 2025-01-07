package neqsim.thermodynamicoperations.flashops;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * VSflash class.
 * </p>
 *
 * @author even solbraa
 * @version $Id: $Id
 */
public class VSflash extends Flash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  double Sspec = 0;
  double Vspec = 0;
  Flash tpFlash;

  /**
   * <p>
   * Constructor for VSflash.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @param Vspec a double
   * @param Sspec a double
   */
  public VSflash(SystemInterface system, double Vspec, double Sspec) {
    this.system = system;
    this.tpFlash = new TPflash(system);
    this.Sspec = Sspec;
    this.Vspec = Vspec;
    // System.out.println("enthalpy " + Hspec);
    // System.out.println("volume " + Vspec);
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
    if (system.getNumberOfPhases() == 1) {
      return -system.getPhase(0).getCp() / system.getTemperature()
          / neqsim.thermo.ThermodynamicConstantsInterface.R;
    }

    double dQdTT = 0.0;
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      dQdTT -= system.getPhase(i).getCp() / system.getPhase(i).getTemperature();
    }
    return dQdTT / neqsim.thermo.ThermodynamicConstantsInterface.R;
  }

  /**
   * <p>
   * calcdQdT.
   * </p>
   *
   * @return a double
   */
  public double calcdQdT() {
    double dQdT = (Sspec - system.getEntropy()) / (neqsim.thermo.ThermodynamicConstantsInterface.R);
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
    double oldPres = system.getPressure(), nyPres = system.getPressure(),
        nyTemp = system.getTemperature(), oldTemp = system.getTemperature();
    double iterations = 1;
    // logger.info("Sspec: " + Sspec);
    do {
      iterations++;
      oldPres = nyPres;
      oldTemp = nyTemp;
      system.init(3);
      // logger.info("Sentr: " + system.getEntropy());
      // logger.info("calcdQdT(): " + calcdQdT());
      // logger.info("dQdP: " + calcdQdP());
      // logger.info("dQdT: " + calcdQdT());
      nyPres = oldPres - (iterations) / (iterations + 10.0) * calcdQdP() / calcdQdPP();
      nyTemp = oldTemp - (iterations) / (iterations + 10.0) * calcdQdT() / calcdQdTT();
      // logger.info("volume: " + system.getVolume());
      // logger.info("inernaleng: " + system.getInternalEnergy());
      system.setPressure(nyPres);
      system.setTemperature(nyTemp);
      tpFlash.run();
      // logger.info("error1: " + (Math.abs((nyPres - oldPres) / (nyPres))));
      // logger.info("error2: " + (Math.abs((nyTemp - oldTemp) / (nyTemp))));
    } while (Math.abs((nyPres - oldPres) / (nyPres))
        + Math.abs((nyTemp - oldTemp) / (nyTemp)) > 1e-9 && iterations < 1000);
    return nyPres;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    tpFlash.run();
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
    // testSystem.addComponent("n-heptane", 0.2);
    testSystem.init(0);
    try {
      testOps.TPflash();
      testSystem.display();

      double entropy = testSystem.getEntropy() * 1.2;
      double volume = testSystem.getVolume() * 1.1;

      testOps.VSflash(volume, entropy);
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
