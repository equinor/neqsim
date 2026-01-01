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
   * Calculate the cross-derivative d²Q/dTdP for coupled Newton solver. This improves convergence by
   * accounting for T-P coupling.
   *
   * @return cross derivative d²Q/dTdP
   */
  public double calcdQdTP() {
    double R = neqsim.thermo.ThermodynamicConstantsInterface.R;
    double T = system.getTemperature();
    // Cross derivative includes dV/dT contribution
    double dVdT = system.getdVdTpn();
    double dQdTP = system.getPressure() * dVdT / (R * T)
        - system.getPressure() * (system.getVolume() - Vspec) / (R * T * T);
    return dQdTP;
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
    int iterations = 0;

    do {
      iterations++;
      oldPres = nyPres;
      oldTemp = nyTemp;
      system.init(3);

      double dQdP = calcdQdP();
      double dQdT = calcdQdT();
      double dQdPP = calcdQdPP();
      double dQdTT = calcdQdTT();
      double dQdTP = calcdQdTP();

      // Solve coupled 2x2 Newton system:
      // [dQdTT dQdTP] [deltaT] [-dQdT]
      // [dQdTP dQdPP] [deltaP] = [-dQdP]
      double det = dQdTT * dQdPP - dQdTP * dQdTP;

      double deltaT;
      double deltaP;

      if (Math.abs(det) > 1e-20) {
        // Coupled Newton solve
        deltaT = (-dQdT * dQdPP + dQdP * dQdTP) / det;
        deltaP = (-dQdP * dQdTT + dQdT * dQdTP) / det;
      } else {
        // Fall back to independent updates if matrix is singular
        deltaT = (Math.abs(dQdTT) > 1e-20) ? -dQdT / dQdTT : 0.0;
        deltaP = (Math.abs(dQdPP) > 1e-20) ? -dQdP / dQdPP : 0.0;
      }

      // Apply damping factor that increases with iterations
      double factor = (double) iterations / (iterations + 5.0);

      // Limit step sizes for stability
      double maxDeltaT = 0.2 * oldTemp;
      double maxDeltaP = 0.3 * oldPres;

      if (Math.abs(deltaT) > maxDeltaT) {
        deltaT = Math.signum(deltaT) * maxDeltaT;
      }
      if (Math.abs(deltaP) > maxDeltaP) {
        deltaP = Math.signum(deltaP) * maxDeltaP;
      }

      nyTemp = oldTemp + factor * deltaT;
      nyPres = oldPres + factor * deltaP;

      // Ensure T and P stay positive and within physical bounds
      if (nyTemp <= 0 || nyTemp > 2000) {
        nyTemp = (nyTemp <= 0) ? oldTemp * 0.9 : oldTemp * 1.1;
      }
      if (nyPres <= 0 || nyPres > 1000) {
        nyPres = (nyPres <= 0) ? oldPres * 0.9 : oldPres * 1.1;
      }

      system.setPressure(nyPres);
      system.setTemperature(nyTemp);
      tpFlash.run();
    } while ((Math.abs((nyPres - oldPres) / nyPres) + Math.abs((nyTemp - oldTemp) / nyTemp) > 1e-9)
        && iterations < 500);

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
