package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflashDehyd class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashDehyd {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflashDehyd.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // SystemInterface testSystem = new SystemSrkEos(288.15 + 5, 165.01325);
    SystemInterface testSystem2 = new SystemSrkEos(298, 10); //
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
    // System.out.println("water activity " +
    // testSystem.getPhase(1).getActivityCoefficient(2));
    // testSystem.init(0);
    // testSystem.setPhaseType(0, 1);
    // testSystem.init(1);
    /*
     * testSystem.init(2);
     *
     * // testSystem.init(1); // testSystem.init(2); // testSystem.init(3); //
     * System.out.println("heat cap " + (testSystem.getPhase(1).getCp())); //
     * testOps.calcPTphaseEnvelope(); testSystem.display(); // testSystem.getPhase(0).getCp(); }
     * catch (Exception ex) { System.out.println(ex.toString()); }
     *
     * /* System.out.println("gas density " + (testSystem.getPhase(0).getDensity()));
     * System.out.println("gas density " + (1.0 / (testSystem.getPhase(0).getDensity() /
     * testSystem.getPhase(0).getMolarMass())));
     *
     * System.out.println("liq density " + (testSystem.getPhase(1).getDensity()));
     * System.out.println("liq density " + (1.0 / (testSystem.getPhase(1).getDensity() /
     * testSystem.getPhase(1).getMolarMass())));
     *
     * testSystem.initPhysicalProperties(); testSystem.init(1);
     *
     * System.out.println("start....."); testSystem.setEmptyFluid();
     * testSystem.setMolarComposition(new double[]{1.0, 1e-20, 1e-20}); testSystem.init(0, 0);
     * testSystem.init(1); testSystem.display();
     *
     * System.out.println("end....."); testSystem.setMolarComposition(new double[]{0.000001,
     * 0.00001, 1e-20}); // testSystem.init(1); testSystem.init(0, 0); testSystem.init(1); //
     * testSystem.display();
     *
     * /* testSystem.initPhysicalProperties(); double rho1 =
     * testSystem.getPhase(0).getPhysicalProperties().getDensity(); System.out.println("drhodP " +
     * testSystem.getPhase(0).getdrhodP()); System.out.println("drhodT " +
     * testSystem.getPhase(0).getdrhodP()); testSystem.setPressure(testSystem.getPressure()+0.01);
     * testSystem.setPressure(testSystem.getPressure() + 0.001); testSystem.init(1); //
     * testSystem.display(); // testSystem.initPhysicalProperties(); double rho2 =
     * testSystem.getPhase(0).getPhysicalProperties().getDensity();
     *
     * //System.out.println("drhodPnum " + (rho2 - rho1) / 0.01); System.out.println("drhodTnum " +
     * (rho2 - rho1) / 0.001);
     *
     * testSystem.saveFluid(2327); testSystem.saveFluid(2301); // testSystem.setNumberOfPhases(1);
     * // testSystem.setTemperature(299.0); // testSystem.init(1); //
     * testSystem.getPhase(0).getEntropy(); // System.out.println("enthalpy " +
     * testSystem.getPhase(0).getEntropy()); //testSystem.setTemperature(299.0); testSystem.init(1);
     * System.out.println("water activity " + testSystem.getPhase(1).getActivityCoefficient(1)); //
     * testSystem.getPhase(0).getEntropydP(); System.out.println("Cp " +
     * testSystem.getPhase(0).getCp()); System.out.println("enthalpy " +
     * testSystem.getPhase(0).getEnthalpy()); System.out.println("entropy " +
     * testSystem.getPhase(0).getEntropy());
     *
     * testSystem.init(2); System.out.println("Cp " + testSystem.getPhase(0).getCp());
     * System.out.println("enthalpy " + testSystem.getPhase(0).getEnthalpy());
     * System.out.println("entropy " + testSystem.getPhase(0).getEntropy()); testSystem.init(3);
     * System.out.println("Cp " + testSystem.getPhase(0).getCp()); System.out.println("enthalpy " +
     * testSystem.getPhase(0).getEnthalpy()); System.out.println("entropy " +
     * testSystem.getPhase(0).getEntropy()); // testSystem.setPhysicalPropertyModel(4);
     * //testSystem.setSolidPhaseCheck("CO2");9 //
     * testSystem.getInterphaseProperties().setInterfacialTensionModel(3); testOps = new
     * ThermodynamicOperations(testSystem); try { // testOps.freezingPointTemperatureFlash();
     * testOps.TPflash(); // testOps.calcPTphaseEnvelope(); // testOps.display(); //
     * testSystem.display(); } catch (Exception ex) { System.out.println(ex.toString()); }
     *
     * /* double h1 = testSystem.getPhase(0).getEntropy(); System.out.println("H " +
     * testSystem.getPhase(0).getEntropy()); System.out.println("H dP " +
     * testSystem.getPhase(0).getEntropydT()); testSystem.setTemperature(testSystem.getTemperature()
     * + 1); testSystem.init(3); double h2 = testSystem.getPhase(0).getEntropy();
     * System.out.println("H " + testSystem.getPhase(0).getEntropy()); System.out.println("H dP " +
     * testSystem.getPhase(0).getEntropydT());
     */
    // System.out.println("dhdp " + (h2 - h1));
    /*
     * double seletivity = testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() /
     * (testSystem.getPhase(1).getComponent("methane").getx() /
     * testSystem.getPhase(0).getComponent("methane").getx());
     * System.out.println("selectivity CO2/methane " + seletivity);
     *
     * double seletivity2 = testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() /
     * (testSystem.getPhase(1).getComponent("ethane").getx() /
     * testSystem.getPhase(0).getComponent("ethane").getx());
     * System.out.println("selectivity CO2/ethane " + seletivity2);
     */
    // testSystem.saveObject(2201);
    // System.out.println("activity coef MDEA " +
    // testSystem.getPhase(1).getActivityCoefficient(1));
    /*
     * testSystem.saveObject(928); testSystem.display(); System.out.println("wt " +
     * testSystem.getPhase(2).getWtFraction(testSystem) +
     * testSystem.getPhase(1).getWtFraction(testSystem) +
     * testSystem.getPhase(0).getWtFraction(testSystem));
     *
     * double a = testSystem.getPhase(0).getBeta() * testSystem.getPhase(0).getMolarMass() /
     * testSystem.getMolarMass(); double seletivity =
     * testSystem.getPhase(1).getComponent("CO2").getx() /
     * testSystem.getPhase(0).getComponent("CO2").getx() /
     * (testSystem.getPhase(1).getComponent("methane").getx() /
     * testSystem.getPhase(0).getComponent("methane").getx());
     *
     * double solubility = testSystem.getPhase(1).getComponent("CO2").getx() *
     * ThermodynamicConstantsInterface.R * 298.15 /ThermodynamicConstantsInterface.atm /
     * (testSystem.getPhase(1).getMolarMass()) * 1000;
     *
     * System.out.println("selectivity " + seletivity); System.out.println("CO2 solubility " +
     * solubility); System.out.println("Z " + testSystem.getPhase(0).getZ()); //
     * testSystem.getPhase(0).getComponentWithIndex(1); // testSystem.saveObject(300); //
     * System.out.println("ethanol activity " + testSystem.getPhase(0).getActivityCoefficient(0));
     *
     * //testSystem. // System.out.println("water activity " +
     * testSystem.getPhase(1).getActivityCoefficient(1)); // System.out.println("TEG activity " +
     * testSystem.getPhase(1).getActivityCoefficient(0)); // testSystem.display();
     * //System.out.println("fugacitycoefficient " +
     * testSystem.getPhase(1).getComponent("water").getLogFugacityCoefficient()); /*
     * System.out.println("fugacitycoefficientdp " +
     * testSystem.getPhase(0).getComponent("MEG").getdfugdp());
     * System.out.println("fugacitycoefficientdp " +
     * testSystem.getPhase(0).getComponent("water").getdfugdp());
     * System.out.println("fugacitycoefficientdt " +
     * testSystem.getPhase(0).getComponent("MEG").getdfugdt());
     * System.out.println("fugacitycoefficientdt " +
     * testSystem.getPhase(0).getComponent("water").getdfugdt());
     * System.out.println("fugacitycoefficientdn " +
     * testSystem.getPhase(0).getComponent("MEG").getdfugdn(1));
     * System.out.println("fugacitycoefficientdn " +
     * testSystem.getPhase(0).getComponent("water").getdfugdn(0));
     *
     * System.out.println("Hres " + testSystem.getPhase(0).getHresTP() /
     * thermo.ThermodynamicConstantsInterface.R); System.out.println("Sres " +
     * testSystem.getPhase(0).getSresTP() / thermo.ThermodynamicConstantsInterface.R);
     * System.out.println("Cpres " + testSystem.getPhase(0).getCpres() /
     * thermo.ThermodynamicConstantsInterface.R); System.out.println("Gibbs gas " +
     * testSystem.getPhase(0).getGibbsEnergy()); System.out.println("Gibbs liquid " +
     * testSystem.getPhase(1).getGibbsEnergy());
     */
  }
}
