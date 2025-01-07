package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestPCSAFT1 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestPCSAFT1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestPCSAFT1.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    double pressure = 0.0;
    // String[] componentName = {"nitrogen", "CO2", "methane", "ethane", "propane",
    // "i-butane", "n-butane", "22-dim-C3", "iC5", "n-pentane", "22-dim-C4", "c-C5",
    // "23-dim-C4", "2-m-C5", "3-m-C5", "n-hexane", "n-heptane", "c-C6", "benzene",
    // "n-octane", "c-C7", "toluene", "n-nonane", "c-C8", "m-Xylene"};

    // double[] compositions = {0.5108, 1.8922, 86.6295, 6.1903, 2.7941, 0.4635,
    // 0.7524, 0.0160, 0.2109, 0.1837, 0.0055, 0.0164, 0.0094, 0.0402, 0.0221,
    // 0.0503}; //, 0.0297, 0.0866, 0.03262, 0.0039, 0.0317, 0.0209, 0.0006, 0.0008,
    // 0.0023};
    // String[] componentName = {"nitrogen", "CO2", "methane", "ethane", "propane",
    // "i-butane", "n-butane", "22-dim-C3", "iC5", "n-pentane", "22-dim-C4", "c-C5",
    // "23-dim-C4", "2-m-C5", "3-m-C5", "n-hexane"}; //, "n-heptane", "c-C6",
    // "benzene", "n-octane", "c-C7", "toluene", "c-C8"};

    String[] componentName =
        {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "22-dim-C3",
            "iC5", "n-pentane", "22-dim-C4", "c-C5", "23-dim-C4", "2-m-C5", "3-m-C5", "n-hexane"};

    double[] compositions = {1.6298, 0.3207, 93.2144, 3.7328, 0.5654, 0.2906, 0.0653, 0.0042,
        0.0483, 0.0167, 0.0042, 0.0092, 0.0065, 0.0165, 0.0089, 0.0038,};

    double[] uncertcompositions = {0.0655, 0.0240, 1.061, 0.2144, 0.0968, 0.0217, 0.0353, 0.0011,
        0.011, 0.007, 0.0005, 0.0011, 0.0008, 0.0028, 0.0015, 0.0035, 0.0021, 0.006, 0.0025, 0.0003,
        0.0022, 0.0014, 0.0001, 0.0002, 0.0007};
    double[] runcompositions = new double[componentName.length];
    SystemInterface testSystem = new SystemSrkEos(273.14, pressure);
    // SystemInterface testSystem = new SystemPCSAFT(273.14, pressure);
    double pres = 0.0;
    testSystem = new SystemSrkEos(testSystem.getTemperature(), pres);

    for (int i = 0; i < componentName.length; i++) {
      double newVar =
          cern.jet.random.Normal.staticNextDouble(compositions[i], uncertcompositions[i]);
      newVar = cern.jet.random.Normal.staticNextDouble(compositions[i], uncertcompositions[i]);
      runcompositions[i] = compositions[i] + 0 * newVar;
      testSystem.addComponent(componentName[i], runcompositions[i]);
    }
    testSystem.addTBPfraction("C7", 0.0477, 88.435 / 1000.0, 746.9 / 1000.0);
    testSystem.addTBPfraction("C8", 0.0147, 101.948 / 1000.0, 763.7 / 1000.0);
    testSystem.addTBPfraction("C9", 0.0004, 116.170 / 1000.0, 783.9 / 1000.0);
    testSystem.init(0);
    testSystem.init(1);
    // if (testSystem.characterize()) {
    // testSystem.getCharacterization().removeTBPfraction();
    // testSystem.getCharacterization().addTBPFractions();
    // }
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.init(0);
    for (int p = 0; p < 1; p++) {
      pres += 1.0;
      testSystem.setPressure(pres);
      try {
        // testOps.dewPointTemperatureFlash();
        testOps.calcPTphaseEnvelope(false);
        testOps.displayResult();
        // testSystem.display();
        logger.info(
            "pressure " + testSystem.getPressure() + " dew point " + testSystem.getTemperature());
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
  }
}
