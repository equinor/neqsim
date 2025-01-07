package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HydrateFlash2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HydrateFlash2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateFlash2.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 10, 122.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("water", 400e-4);

    // testSystem.addComponent("CO2", 9.02);
    testSystem.addComponent("methane", 1.0);
    // testSystem.addComponent("ethane", 1.0);
    // testSystem.addComponent("propane", 4.0);
    // testSystem.addComponent("i-butane", 0.5);
    // testSystem.addComponent("n-butane", 0.5);

    // testSystem.addTBPfraction("C6",0.06,86.178/1000.0,0.664);
    // testSystem.addTBPfraction("C7",0.06,96.0/1000.0,0.738);
    // testSystem.addTBPfraction("C8",0.05,107.0/1000.0,0.765);
    // testSystem.addTBPfraction("C9",0.05,121.0/1000.0,0.781);
    // testSystem.addTBPfraction("C10",0.04,134.0/1000.0,0.792);
    // testSystem.addTBPfraction("C13",0.04,184.0/1000.0,0.883);
    // testSystem.addComponent("TEG", 0.5e-4);
    // testSystem.addSalt("NaCl", 0.01);
    // testSystem.addComponent("MEG", 0.1);
    testSystem.addComponent("water", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(9);

    testSystem.setPressure(100);
    testSystem.init(0);
    // testSystem.setSolidPhaseCheck("water");
    testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);

    // testSystem.setMultiPhaseCheck(true);
    try {
      // testOps.dewPointTemperatureFlash();
      // testOps.waterDewPointTemperatureFlash();
      testOps.setRunAsThread(true);
      testOps.hydrateEquilibriumLine(10.0, 200.0);
      boolean isFinished = testOps.waitAndCheckForFinishedCalculation(100000);
      logger.info("finished? " + isFinished);
      // testSystem.setTemperature(240.0);
      // testOps.freezingPointTemperatureFlash();
      // testSystem.display();
      testOps.TPflash();
    } catch (Exception ex) {
      ex.toString();
    }
    testSystem.display();

    // SystemInterface testSystem2 = new SystemSrkCPAs(273.15+0.0, 100.0);
    // testSystem2.addComponent("methane",
    // testSystem.getPhase(0).getComponent("methane").getx());
    // testSystem2.addComponent("ethane", testSystem.getPhase(0).getComponent("ethane").getx());
    // testSystem2.addComponent("propane",
    // testSystem.getPhase(0).getComponent("propane").getx());
    // testSystem2.addComponent("nitrogen",
    // testSystem.getPhase(0).getComponent("nitrogen").getx());
    // testSystem2.addComponent("i-butane",
    // testSystem.getPhase(0).getComponent("i-butane").getx());
    // testSystem2.addComponent("n-butane",
    // testSystem.getPhase(0).getComponent("n-butane").getx());
    // testSystem2.addComponent("iC5", testSystem.getPhase(0).getComponent("iC5").getx());
    // testSystem2.addComponent("n-pentane",
    // testSystem.getPhase(0).getComponent("n-pentane").getx());

    // testSystem2.addTBPfraction("C6",testSystem.getPhase(0).getComponent("C6_DefaultName").getx(),86.178/1000.0,0.664);
    // testSystem2.addTBPfraction("C7",testSystem.getPhase(0).getComponent("C7_DefaultName").getx(),96.0/1000.0,0.738);
    // testSystem2.addTBPfraction("C8",testSystem.getPhase(0).getComponent("C8_DefaultName").getx(),107.0/1000.0,0.765);
    // testSystem2.addTBPfraction("C9",testSystem.getPhase(0).getComponent("C9_DefaultName").getx(),121.0/1000.0,0.781);
    // testSystem2.addTBPfraction("C10",testSystem.getPhase(0).getComponent("C10_DefaultName").getx(),134.0/1000.0,0.792);
    // testSystem2.addTBPfraction("C13",testSystem.getPhase(0).getComponent("C13_DefaultName").getx(),184.0/1000.0,0.883);

    // testSystem2.addComponent("water",
    // testSystem.getPhase(0).getComponent("water").getx());
    // testSystem2.addComponent("MEG",
    // testSystem.getPhase(0).getComponent("MEG").getx());

    // SystemInterface testSystem2 = testSystem.phaseToSystem(testSystem.getPhase(0));
    // testSystem2.createDatabase(true);
    // testSystem2.setMixingRule(7);
    // testSystem2.setTemperature(273.15-4.0);
    // testSystem2.setPressure(120.0);
    // testSystem2.setMultiPhaseCheck(true);
    // testSystem2.setHydrateCheck(true);

    // ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);

    /*
     * try{ // testOps2.TPflash(); //testSystem2.display(); //
     * testOps2.hydrateFormationTemperature(2); //testOps2.dewPointTemperatureFlash();
     * //testOps.freezingPointTemperatureFlash(); //testOps.bubblePointPressureFlash(false);
     * //testOps.hydrateFormationPressure(); //testOps2.hydrateFormationTemperature(2); //
     * testOps.TPflash(); // testSystem.display(); } catch(Exception ex){
     * logger.error(ex.getMessage(), ex); System.out.println(ex.toString()); } /*
     * //testSystem2.display(); int phase = 0; double x1 =
     * testSystem2.getPhase(0).getMolarVolume()*testSystem2.getBeta(0); double x2 =
     * testSystem2.getPhase(1).getMolarVolume()*testSystem2.getBeta(1);
     *
     * double x3 = x1/(x1+x2); double x4 = x2/(x1+x2); System.out.println("vol gas % " + x3*100);
     * System.out.println("vol liq % " + x4*100); int n = testSystem.getNumberOfPhases()-1; double
     * megwtfracInit = testSystem.getPhase(n).getComponent("MEG").getMolarMass()*testSystem.getPhase
     * (n).getComponent("MEG").getx()/testSystem.getPhase(n).getMolarMass();
     * System.out.println("wt % MEG init " + megwtfracInit*100); n =
     * testSystem2.getNumberOfPhases()-1; double megwtfrac =
     * testSystem2.getPhase(n).getComponent("MEG").getMolarMass()*testSystem2.
     * getPhase(n).getComponent("MEG").getx()/testSystem2.getPhase(n).getMolarMass() ;
     * System.out.println("wt % MEG " + megwtfrac*100); testSystem2.display();
     */
  }
}
