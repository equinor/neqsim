package neqsim.thermo.util.example;

import java.util.Arrays;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * TestNeqSimBug class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestNeqSimBug {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    // double[] spec1 = {1.0}; // salt water Pressure
    // double[] spec2 = { -39678.555}; // salt water Enthalpy

    SystemInterface fluid = new SystemSrkEos(273.15 + 55.0, 10.0);
    fluid.addComponent("water", 0.0386243104934692);
    fluid.addComponent("nitrogen", 1.08263303991407E-05);
    fluid.addComponent("CO2", 0.00019008457660675);
    fluid.addComponent("methane", 0.00305547803640366);
    fluid.addComponent("ethane", 0.00200786963105202);
    fluid.addComponent("propane", 0.00389420658349991);
    fluid.addComponent("i-butane", 0.00179276615381241);
    fluid.addComponent("n-butane", 0.00255768150091171);
    fluid.addComponent("i-pentane", 0.00205287128686905);
    fluid.addComponent("n-pentane", 0.00117853358387947);
    fluid.addTBPfraction("CHCmp_1", 0.000867870151996613, 0.0810000000000000, 0.72122997045517);
    fluid.addTBPfraction("CHCmp_2", 0.048198757171630900, 0.0987799987792969, 0.754330039024353);
    fluid.addTBPfraction("CHCmp_3", 0.097208471298217800, 0.1412200012207030, 0.81659996509552);
    fluid.addTBPfraction("CHCmp_4", 0.165174083709717000, 0.1857899932861330, 0.861050009727478);
    fluid.addTBPfraction("CHCmp_5", 0.279571933746338000, 0.2410899963378910, 0.902539968490601);
    fluid.addTBPfraction("CHCmp_6", 0.240494251251221000, 0.4045100097656250, 0.955269992351531);
    fluid.addTBPfraction("CHCmp_7", 0.113120021820068000, 0.9069699707031250, 1.0074599981308);

    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    fluid.useVolumeCorrection(true);
    fluid.setMultiPhaseCheck(true);

    ThermodynamicOperations fluidOps = new ThermodynamicOperations(fluid);

    double spec1 = 73.22862045673597; // grane Pressure
    double spec2 = -62179.7247076579; // Enthalpy

    fluidOps.propertyFlash(Arrays.asList(spec1), Arrays.asList(spec2), 3, null, null);

    /*
     * for (int t = 0; t < 1; t++) { fluid.setPressure(spec1[t]); fluidOps.PHflash(spec2[t],
     * "J/mol"); // fluidOps.TPflash(); fluid.init(2); fluid.initPhysicalProperties();
     */
    fluid.display();
  }
}
