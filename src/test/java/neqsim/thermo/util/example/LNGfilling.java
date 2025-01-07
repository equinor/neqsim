package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGfilling class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGfilling {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LNGfilling.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem2 =
    // util.serialization.SerializationManager.open("c:/test.fluid");
    // testSystem2.display();
    // SystemInterface testSystem = new SystemElectrolyteCPAstatoil(293.15, 1.0);
    SystemInterface testSystem = new SystemSrkCPAs(273.15 + 10.0, 450.0);
    // SystemInterface testSystem = new SystemSrkEos(273.15+0.0, 0.1);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // SystemInterface testSystem = new SystemSrkCPAs(273.15+68.0, 170.0);

    testSystem.addComponent("nitrogen", 0.616);
    testSystem.addComponent("water", 0.616);
    // testSystem.addComponent("TEG", 5.9622);

    // testSystem.addComponent("methanol", 4.0378);
    // testSystem.addComponent("CO2", 4.0378);
    testSystem.createDatabase(true);
    // testSystem = testSystem.autoSelectModel();
    // testSystem.chemicalReactionInit();
    // testSystem.createDatabase(true);
    testSystem.setMixingRule(7);

    // testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);

    try {
      // testSystem = testSystem.autoSelectModel();
      // testOps = new ThermodynamicOperations(testSystem);
      // testOps.TPflash();
      testOps.hydrateFormationTemperature(2);
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("JT " + testSystem.getPhase(0).getJouleThomsonCoefficient());
    // System.out.println("wt%MEG " +
    // testSystem.getPhase(1).getComponent("MEG").getMolarMass()*testSystem.getPhase(1).getComponent("MEG").getx()/testSystem.getPhase(1).getMolarMass());
    // System.out.println("fug"
    // +testSystem.getPhase(0).getComponent("water").getx()*testSystem.getPhase(0).getPressure()*testSystem.getPhase(0).getComponent(0).getFugacityCoefficient());
  }
}
// testSystem = testSystem.setModel("GERG-water");
// testSystem.setMixingRule(8);

// testSystem = testSystem.autoSelectModel();
// testSystem.autoSelectMixingRule();
// testSystem.setMultiPhaseCheck(true);
// testOps.setSystem(testSystem);

// System.out.println("new model name " + testSystem.getModelName());
// try{
// testOps.TPflash();
// testSystem.display();
// }
// catch(Exception ex){
// System.out.println(ex.toString());
// }
// }
// }
