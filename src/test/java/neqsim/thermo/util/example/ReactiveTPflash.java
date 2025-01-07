package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReactiveTPflash class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ReactiveTPflash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReactiveTPflash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemFurstElectrolyteEosMod2004(423.2,
    // 24.4);
    SystemInterface testSystem = new SystemElectrolyteCPAstatoil(308.3, 3.8);

    testSystem.addComponent("methane", 99.0);
    testSystem.addComponent("CO2", 1.0e-6);
    // testSystem.addComponent("H2S", 0.10);
    // testSystem.addComponent("MDEA", 13.0);
    // testSystem.addComponent("nC10", 1.00);
    testSystem.addComponent("water", 11.00, "kg/sec");
    // testSystem.addComponent("Na+", 1.200);
    // testSystem.addComponent("OH-", 1.200);
    // testSystem.addComponent("MDEA", 1.100, "mol/sec");
    // testSystem.addComponent("HCO3-", 0.100, "mol/sec");
    // testSystem.addComponent("Piperazine", 0.1e-4);

    testSystem.chemicalReactionInit();
    // testSystem.useVolumeCorrection(true);
    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(10);
    // testSystem.
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
    testSystem.init(0);
    testSystem.init(1);
    // testSystem.init(1);
    // System.out.println("wt% MDEA " +
    // 100*testSystem.getPhase(1).getComponent("MDEA").getx()*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()/(testSystem.getPhase(1).getComponent("MDEA").getx()*testSystem.getPhase(1).getComponent("MDEA").getMolarMass()+testSystem.getPhase(1).getComponent("water").getx()*testSystem.getPhase(1).getComponent("water").getMolarMass()));
    // System.out.println("wt% Piperazine " +
    // testSystem.getPhase(1).getComponent("Piperazine").getx()*testSystem.getPhase(1).getComponent("Piperazine").getMolarMass()/testSystem.getPhase(1).getMolarMass());

    try {
      // testSystem.getChemicalReactionOperations().solveChemEq(1, 0);
      // testSystem.getChemicalReactionOperations().solveChemEq(1, 1);
      // ops.bubblePointPressureFlash(false);
      // // ops.bubblePointPressureFlash(false);
      ops.TPflash();
      // ops.dewPointTemperatureFlash();
    } catch (Exception ex) {
    }
    testSystem.display();
    // System.out.println("pH " + testSystem.getPhase(1).getpH());
    logger.info("pH " + testSystem.getPhase(1).getpH());
    logger.info(
        "activity coefficiet water " + testSystem.getPhase("aqueous").getActivityCoefficient(2));

    // for(int i=0;i<23;i++){
    // try{
    // ops.bubblePointPressureFlash(false);
    // // testSystem.display();
    // //ops.TPflash();
    // } catch(Exception ex){}

    // System.out.println("loading " + (0.0005+0.05*i)+ " PCO2 " +
    // testSystem.getPhase(0).getComponent("CO2").getx()*testSystem.getPressure());
    // testSystem.addComponent("CO2", 0.05*(6.45+1.78));
    // }
  }
}
