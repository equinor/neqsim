package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * TPflashDehyd class.
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashDehyd {
  private static final Logger logger = LogManager.getLogger(TPflashDehyd.class);

  /** Logger object for class. */

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // SystemInterface testSystem = new SystemSrkEos(288.15 + 5, 165.01325);
    SystemInterface testSystem2 = new SystemSrkEos(298, 10);
    testSystem2 = testSystem2.readObject(30);
    // testSystem2.addComponent("methane", 1.0, "kg/sec");
    // testSystem2.addComponent("ethane", 0.1, "kg/sec");
    // testSystem2.addComponent("water", 30.0e-6, "kg/sec");
    // testSystem2.addComponent("MEG", 30.0e-16, "kg/sec");
    testSystem2.createDatabase(true);
    testSystem2.setMixingRule(10);
    testSystem2.init(0);

    SystemInterface testSystem = new SystemSrkCPAstatoil(298, 10);
    // testSystem.addComponent("methane", 1.0e-10, "kg/sec");
    testSystem = testSystem.readObject(37);
    testSystem.addComponent("water", 0.5, "kg/sec");
    testSystem.addComponent("MEG", 9.5, "kg/sec");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    for (int i = 0; i < 24; i++) {
      testSystem2.setTotalFlowRate(0.1 * 3600, "MSm3/day");
      testSystem2.init(1);
      testSystem.addFluid(testSystem2);
      testOps.TPflash();
      // testSystem.display();
      logger.info("ppm water" + testSystem.getPhase(0).getComponent("water").getx() * 1e6);
      testSystem = testSystem.phaseToSystem(1);
      testOps = new ThermodynamicOperations(testSystem);
      logger.info("nuber of moles " + testSystem.getNumberOfMoles() + " moleFrac MEG "
          + testSystem.getPhase(0).getComponent("MEG").getx());
    }

    // testSystem.display();
    // testSystem.display();
    // testSystem.init(3);
    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();
    // testSystem.display();
    // logger.info("water activity " +
    // testSystem.getPhase(1).getActivityCoefficient(2));
    // testSystem.init(0);
    // testSystem.setPhaseType(0, 1);
    // testSystem.init(1);
    /*
     * testSystem.init(2);
     *
     * // testSystem.init(1); // testSystem.init(2); // testSystem.init(3); // logger.info("heat cap " +
     * (testSystem.getPhase(1).getCp())); // testOps.calcPTphaseEnvelope(); testSystem.display(); //
     * testSystem.getPhase(0).getCp(); } catch (Exception ex) { logger.info(ex.toString()); }
     *
     * /* logger.info("gas density " + (testSystem.getPhase(0).getDensity())); logger.info("gas density " + (1.0 /
     * (testSystem.getPhase(0).getDensity() / testSystem.getPhase(0).getMolarMass())));
     *
     * logger.info("liq density " + (testSystem.getPhase(1).getDensity())); logger.info("liq density " + (1.0 /
     * (testSystem.getPhase(1).getDensity() / testSystem.getPhase(1).getMolarMass())));
     *
     * testSystem.initPhysicalProperties(); testSystem.init(1);
     *
     * logger.info("start....."); testSystem.setEmptyFluid(); testSystem.setMolarComposition(new double[]{1.0, 1e-20,
     * 1e-20}); testSystem.init(0, 0); testSystem.init(1); testSystem.display();
     *
     * logger.info("end....."); testSystem.setMolarComposition(new double[]{0.000001, 0.00001, 1e-20}); //
     * testSystem.init(1); testSystem.init(0, 0); testSystem.init(1); // testSystem.display();
     *
     * /* testSystem.initPhysicalProperties(); double rho1 =
     * testSystem.getPhase(0).getPhysicalProperties().getDensity(); logger.info("drhodP " +
     * testSystem.getPhase(0).getdrhodP()); logger.info("drhodT " + testSystem.getPhase(0).getdrhodP());
     * testSystem.setPressure(testSystem.getPressure()+0.01); testSystem.setPressure(testSystem.getPressure() + 0.001);
     * testSystem.init(1); // testSystem.display(); // testSystem.initPhysicalProperties(); double rho2 =
     * testSystem.getPhase(0).getPhysicalProperties().getDensity();
     *
     * //logger.info("drhodPnum " + (rho2 - rho1) / 0.01); logger.info("drhodTnum " + (rho2 - rho1) / 0.001);
     *
     * testSystem.saveFluid(2327); testSystem.saveFluid(2301); // testSystem.setNumberOfPhases(1); //
     * testSystem.setTemperature(299.0); // testSystem.init(1); // testSystem.getPhase(0).getEntropy(); //
     * logger.info("enthalpy " + testSystem.getPhase(0).getEntropy()); //testSystem.setTemperature(299.0);
     * testSystem.init(1); logger.info("water activity " + testSystem.getPhase(1).getActivityCoefficient(1)); //
     * testSystem.getPhase(0).getEntropydP(); logger.info("Cp " + testSystem.getPhase(0).getCp());
     * logger.info("enthalpy " + testSystem.getPhase(0).getEnthalpy()); logger.info("entropy " +
     * testSystem.getPhase(0).getEntropy());
     *
     * testSystem.init(2); logger.info("Cp " + testSystem.getPhase(0).getCp()); logger.info("enthalpy " +
     * testSystem.getPhase(0).getEnthalpy()); logger.info("entropy " + testSystem.getPhase(0).getEntropy());
     * testSystem.init(3); logger.info("Cp " + testSystem.getPhase(0).getCp()); logger.info("enthalpy " +
     * testSystem.getPhase(0).getEnthalpy()); logger.info("entropy " + testSystem.getPhase(0).getEntropy()); //
     * testSystem.setPhysicalPropertyModel(4); //testSystem.setSolidPhaseCheck("CO2");9 //
     * testSystem.getInterphaseProperties().setInterfacialTensionModel(3); testOps = new
     * ThermodynamicOperations(testSystem); try { // testOps.freezingPointTemperatureFlash(); testOps.TPflash(); //
     * testOps.calcPTphaseEnvelope(); // testOps.display(); // testSystem.display(); } catch (Exception ex) {
     * logger.info(ex.toString()); }
     *
     * /* double h1 = testSystem.getPhase(0).getEntropy(); logger.info("H " + testSystem.getPhase(0).getEntropy());
     * logger.info("H dP " + testSystem.getPhase(0).getEntropydT());
     * testSystem.setTemperature(testSystem.getTemperature() + 1); testSystem.init(3); double h2 =
     * testSystem.getPhase(0).getEntropy(); logger.info("H " + testSystem.getPhase(0).getEntropy()); logger.info("H dP "
     * + testSystem.getPhase(0).getEntropydT());
     */
    // logger.info("dhdp " + (h2 - h1));
    /*
     * double seletivity = testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() / (testSystem.getPhase(1).getComponent("methane").getx() /
     * testSystem.getPhase(0).getComponent("methane").getx()); logger.info("selectivity CO2/methane " + seletivity);
     *
     * double seletivity2 = testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() / (testSystem.getPhase(1).getComponent("ethane").getx() /
     * testSystem.getPhase(0).getComponent("ethane").getx()); logger.info("selectivity CO2/ethane " + seletivity2);
     */
    // testSystem.saveObject(2201);
    // logger.info("activity coef MDEA " +
    // testSystem.getPhase(1).getActivityCoefficient(1));
    /*
     * testSystem.saveObject(928); testSystem.display(); logger.info("wt " +
     * testSystem.getPhase(2).getWtFraction(testSystem) + testSystem.getPhase(1).getWtFraction(testSystem) +
     * testSystem.getPhase(0).getWtFraction(testSystem));
     *
     * double a = testSystem.getPhase(0).getBeta() * testSystem.getPhase(0).getMolarMass() / testSystem.getMolarMass();
     * double seletivity = testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() / (testSystem.getPhase(1).getComponent("methane").getx() /
     * testSystem.getPhase(0).getComponent("methane").getx());
     *
     * double solubility = testSystem.getPhase(1).getComponent("CO2").getx() * ThermodynamicConstantsInterface.R *
     * 298.15 /ThermodynamicConstantsInterface.atm / (testSystem.getPhase(1).getMolarMass()) * 1000;
     *
     * logger.info("selectivity " + seletivity); logger.info("CO2 solubility " + solubility); logger.info("Z " +
     * testSystem.getPhase(0).getZ()); // testSystem.getPhase(0).getComponentWithIndex(1); //
     * testSystem.saveObject(300); // logger.info("ethanol activity " +
     * testSystem.getPhase(0).getActivityCoefficient(0));
     *
     * //testSystem. // logger.info("water activity " + testSystem.getPhase(1).getActivityCoefficient(1)); //
     * logger.info("TEG activity " + testSystem.getPhase(1).getActivityCoefficient(0)); // testSystem.display();
     * //logger.info("fugacitycoefficient " + testSystem.getPhase(1).getComponent("water").getLogFugacityCoefficient());
     * /* logger.info("fugacitycoefficientdp " + testSystem.getPhase(0).getComponent("MEG").getdfugdp());
     * logger.info("fugacitycoefficientdp " + testSystem.getPhase(0).getComponent("water").getdfugdp());
     * logger.info("fugacitycoefficientdt " + testSystem.getPhase(0).getComponent("MEG").getdfugdt());
     * logger.info("fugacitycoefficientdt " + testSystem.getPhase(0).getComponent("water").getdfugdt());
     * logger.info("fugacitycoefficientdn " + testSystem.getPhase(0).getComponent("MEG").getdfugdn(1));
     * logger.info("fugacitycoefficientdn " + testSystem.getPhase(0).getComponent("water").getdfugdn(0));
     *
     * logger.info("Hres " + testSystem.getPhase(0).getHresTP() / thermo.ThermodynamicConstantsInterface.R);
     * logger.info("Sres " + testSystem.getPhase(0).getSresTP() / thermo.ThermodynamicConstantsInterface.R);
     * logger.info("Cpres " + testSystem.getPhase(0).getCpres() / thermo.ThermodynamicConstantsInterface.R);
     * logger.info("Gibbs gas " + testSystem.getPhase(0).getGibbsEnergy()); logger.info("Gibbs liquid " +
     * testSystem.getPhase(1).getGibbsEnergy());
     */
  }
}
