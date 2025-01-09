package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PS_PH_flash class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class PS_PH_flash {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkMathiasCopeman(273.15 + 5, 80);
    SystemInterface testSystem = new SystemSrkEos(273.15 + 15.0, 100.0);
    // SystemInterface testSystem = new SystemGERG2004Eos(277.59,689.474483);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(350.15,30.00);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("water", 51.0);

    testSystem.addComponent("nitrogen", 1.08263303991407E-05);
    testSystem.addComponent("CO2", 0.00019008457660675);
    testSystem.addComponent("methane", 0.00305547803640366);
    testSystem.addComponent("ethane", 0.00200786963105202);
    testSystem.addComponent("propane", 0.00389420658349991);
    testSystem.addComponent("n-butane", 0.00179276615381241);
    testSystem.addComponent("i-butane", 0.00255768150091171);
    testSystem.addComponent("i-pentane", 0.00205287128686905);
    testSystem.addComponent("n-pentane", 0.00117853358387947);
    /*
     * testSystem.addTBPfraction("CH2", 0.000867870151996613, 0.0810000000000000, 0.72122997045517);
     * testSystem.addTBPfraction("CH3", 0.04819875717163090, 0.0987799987792969, 0.754330039024353);
     * testSystem.addTBPfraction("CH4", 0.0972084712982178, 0.1412200012207030, 0.81659996509552);
     * testSystem.addTBPfraction("CH5", 0.16517408370, 0.1857899932861330, 0.861050009727478);
     * testSystem.addTBPfraction("CH6", 0.279571933746338, 0.2410899963378910, 0.902539968490601);
     * testSystem.addTBPfraction("CH7", 0.2404942512512, 0.4045100097656250, 0.955269992351531);
     * testSystem.addTBPfraction("CH8", 0.1131200218, 0.9069699, 1.0074599981308); //
     * testSystem.addComponent("ethane", 0.05); testSystem.addComponent("water", 1.19299e-1); //
     * testSystem.addComponent("n-butane", 3.53465e-1); // testSystem.addComponent("propane", 50);
     * //testSystem.addComponent("CO2", 50); //testSystem.addComponent("water", 20);
     */
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);
    try {
      testOps.TPflash();
      // testOps.bubblePointTemperatureFlash();
    } catch (Exception ex) {
    }
    testSystem.init(3);
    // testSystem.display();

    // testSystem.setPressure(testSystem.getPressure() - 1.2);
    // double entropy = testSystem.getEntropy();
    // System.out.println("entropy spec" + entropy);
    double enthalpy = testSystem.getEnthalpy();
    // System.out.println("enthalpy spec" + enthalpy);

    double entropy = testSystem.getEntropy();
    /*
     * testSystem.setTemperature(273.15 + 0.0); testSystem.setPressure(100.0); try {
     * testOps.TPflash(); // testOps.bubblePointTemperatureFlash(); } catch (Exception ex) { }
     * testSystem.init(2); testSystem.setPressure(100.0); // System.out.println("entropy spec" +
     * entropy);
     *
     * // testSystem.setPressure(20.894745);
     */
    testSystem.setPressure(10.0);
    testOps.PSflash(entropy);
    // testOps.PHflash(enthalpy);
    // testSystem.display();

    // testOps.PSflash(entropy);
    // testSystem.display();
    // testSystem.display();
    // testSystem.setTemperature(273.15 + 30.0);
    // testSystem.setPressure(200.0);
    try {
      // testOps.TPflash();
      // testOps.bubblePointTemperatureFlash();
    } catch (Exception ex) {
    }
    // testSystem.init(2);
    // testSystem.setPressure(1.0);
    // testOps.PSflash(entropy);
    testSystem.display();
    // System.out.println("enthalpy spec" + testSystem.getEnthalpy());
  }
}
