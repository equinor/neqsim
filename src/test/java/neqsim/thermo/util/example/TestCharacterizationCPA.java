package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestCharacterizationCPA class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestCharacterizationCPA {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestCharacterizationCPA.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 20.0, 31.0);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15+20.0,
    // 31.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("nitrogen", 0.372);
    testSystem.addComponent("CO2", 3.344);
    testSystem.addComponent("H2S", 3.344);
    testSystem.addComponent("methane", 82.224);
    testSystem.addComponent("ethane", 6.812);
    testSystem.addComponent("propane", 2.771);
    testSystem.addComponent("i-butane", 0.434);
    testSystem.addComponent("n-butane", 0.9);
    testSystem.addComponent("22-dim-C3", 0.9);
    testSystem.addComponent("i-pentane", 0.303);
    testSystem.addComponent("n-pentane", 0.356);
    testSystem.addComponent("n-hexane", 0.356);
    testSystem.addComponent("n-heptane", 0.356);
    testSystem.addComponent("n-octane", 0.356);
    testSystem.addComponent("n-nonane", 0.356);
    testSystem.addComponent("nC10", 0.356);
    testSystem.addComponent("nC12", 0.356);

    testSystem.addTBPfraction("ST_C13-C14", 0.428, 181.977005004883 / 1000.0, 0.830299988);
    testSystem.addTBPfraction("ST_C16", 0.626, 222.000000000000 / 1000.0, 0.849);
    testSystem.addTBPfraction("ST_C17-C19", 0.609, 253.000000000000 / 1000.0, 0.853299988);
    testSystem.addTBPfraction("ST_C21", 0.309, 291.000000000000 / 1000.0, 0.868);
    testSystem.addTBPfraction("ST_C24", 0.254, 331.000000000000 / 1000.0, 0.881);
    testSystem.addTBPfraction("ST_C28", 0.137, 388.000000000000 / 1000.0, 0.897);
    testSystem.addTBPfraction("ST_C38", 0.067, 528.000000000000 / 1000.0, 0.927);

    testSystem.addTBPfraction("LP_C17", 0.03, 238.779998779297 / 1000.0, 0.84325);
    testSystem.addTBPfraction("LP_C24", 0.017, 327.040008544922 / 1000.0, 0.879969971);
    testSystem.addTBPfraction("LP_C32", 0.005, 440.910003662109 / 1000.0, 0.914400024);
    testSystem.addTBPfraction("LP_C48", 0.005, 665.849975585938 / 1000.0, 0.962919983);

    testSystem.addTBPfraction("C6", 0.428, 86.178 / 1000.0, 0.664);
    testSystem.addTBPfraction("C7", 0.626, 96.00 / 1000.0, 0.738);
    testSystem.addTBPfraction("C8", 0.609, 107.000000000000 / 1000.0, 0.765);
    testSystem.addTBPfraction("C9", 0.309, 121.000000000000 / 1000.0, 0.781);
    testSystem.addTBPfraction("C12", 0.137, 161.000000000000 / 1000.0, 0.804900024);

    testSystem.addTBPfraction("C10-C11", 0.03, 140.089996337891 / 1000.0, 0.793599976);
    testSystem.addTBPfraction("C13-C14", 0.428, 182.026000976563 / 1000.0, 0.814599976);
    testSystem.addTBPfraction("C15-C16", 0.626, 213.494995117188 / 1000.0, 0.826099976);
    testSystem.addTBPfraction("C17-C18", 0.609, 243.557998657227 / 1000.0, 0.836200012);
    testSystem.addTBPfraction("C19-C21", 0.309, 275.160003662109 / 1000.0, 0.847099976);
    testSystem.addTBPfraction("C22-C24", 0.254, 316.907012939453 / 1000.0, 0.858799988);
    testSystem.addTBPfraction("C25-C31", 0.137, 380.630004882813 / 1000.0, 0.874099976);
    testSystem.addTBPfraction("C32-C80", 0.067, 546.447998046875 / 1000.0, 0.904799988);

    testSystem.addTBPfraction("Undecanes", 0.017, 147 / 1000.0, 0.793);
    testSystem.addTBPfraction("Dodecanes", 0.005, 161 / 1000.0, 0.804);
    testSystem.addTBPfraction("Tridecanes", 0.428, 175 / 1000.0, 0.815);
    testSystem.addTBPfraction("Tetradecanes", 0.626, 190 / 1000.0, 0.826);
    testSystem.addTBPfraction("Pentadecanes", 0.609, 206 / 1000.0, 0.836);
    testSystem.addTBPfraction("Hexadecanes", 0.309, 222 / 1000.0, 0.843);
    testSystem.addTBPfraction("Heptadecanes", 0.254, 237 / 1000.0, 0.851);
    testSystem.addTBPfraction("Octadecanes", 0.137, 251 / 1000.0, 0.856);
    testSystem.addTBPfraction("Nonadecanes", 0.067, 263 / 1000.0, 0.861);
    testSystem.addTBPfraction("Eicosanes", 0.03, 275 / 1000.0, 0.866);

    testSystem.addTBPfraction("C21", 0.017, 291 / 1000.0, 0.871);
    testSystem.addTBPfraction("C22", 0.017, 300 / 1000.0, 0.876);
    testSystem.addTBPfraction("C23", 0.017, 312 / 1000.0, 0.881);
    testSystem.addTBPfraction("C24", 0.017, 324 / 1000.0, 0.885);

    testSystem.addTBPfraction("C25", 0.017, 337 / 1000.0, 0.888);
    testSystem.addTBPfraction("C26", 0.017, 349 / 1000.0, 0.892);
    testSystem.addTBPfraction("C27", 0.017, 360 / 1000.0, 0.896);
    testSystem.addTBPfraction("C28", 0.017, 372 / 1000.0, 0.899);
    testSystem.addTBPfraction("C29", 0.017, 382 / 1000.0, 0.902);
    testSystem.addTBPfraction("C30", 0.017, 394 / 1000.0, 0.905);
    testSystem.addTBPfraction("C31", 0.017, 404 / 1000.0, 0.909);
    testSystem.addTBPfraction("C32", 0.017, 415 / 1000.0, 0.912);
    testSystem.addTBPfraction("C33", 0.017, 426 / 1000.0, 0.915);
    testSystem.addTBPfraction("C34", 0.017, 437 / 1000.0, 0.917);
    testSystem.addTBPfraction("C35", 0.017, 445 / 1000.0, 0.92);
    testSystem.addTBPfraction("C36+", 0.017, 600 / 1000.0, 0.95);

    // testSystem.addTBPfraction("water", 10.005, 303.531/1000.0, 0.8551);
    testSystem.addComponent("water", 0.303);
    // testSystem.addComponent("MEG", 1.0e-10);
    testSystem.addComponent("TEG", 1.0e-10);
    testSystem.setMultiPhaseCheck(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);

    try {
      testOps.TPflash();
      // testOps.calcPTphaseEnvelope(false);
      // testOps.displayResult();
      // testOps.bubblePointPressureFlash(false);
      // testOps.dewPointPressureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // testSystem.saveObject(96);
    // testSystem.saveFluid(1947);

    testSystem.display();
  }
}
