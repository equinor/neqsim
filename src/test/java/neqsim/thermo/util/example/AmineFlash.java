package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * AmineFlash class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class AmineFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AmineFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemFurstElectrolyteEos(273.15 + 50,
        ThermodynamicConstantsInterface.referencePressure);
    // SystemInterface testSystem = new SystemElectrolyteCPA(273.15+40,
    // ThermodynamicConstantsInterface.referencePressure);
    double molMDEA = 0.1;
    double loading = 0.4;
    double density = 1088;

    // testSystem.addComponent("methane", loading*molMDEA*0.001);
    testSystem.addComponent("CO2", loading * molMDEA);
    testSystem.addComponent("water", 1.0 - molMDEA);
    // testSystem.addComponent("Piperazine", 0.1*molMDEA);
    testSystem.addComponent("MDEA", molMDEA);
    testSystem.chemicalReactionInit();
    // testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
    testSystem.init(0);
    testSystem.init(1);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error("err ", ex);
    }
    double molprMDEA = (molMDEA / (1.0 + 0.30 * molMDEA));
    logger.info("mol % MDEA " + molprMDEA);
    logger.info("molCO2/liter "
        + loading * molprMDEA / testSystem.getPhase(1).getMolarMass() * density / 1e3);
    logger.info("pressure " + testSystem.getPressure());
    logger.info("pH " + testSystem.getPhase(1).getpH());
    logger.info("Henrys Constant CO2 " + testSystem.calcHenrysConstant("CO2"));
    testSystem.display();
  }
}
