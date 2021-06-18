package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class TestSurfaceTension2 {

    private static final long serialVersionUID = 1000;
    // John debug

    public static void main(String args[]) {
        int i;
        int nncomp = 6; // Hard coded
        double ci[] = new double[6];
        int gtmethod = 1; // 2; John changed

        SystemInterface testSystem = new SystemSrkEos(445.0, 105);
        testSystem.getCharacterization().setTBPModel("PedersenSRK");
        // testSystem.addComponent("nitrogen", 0.0);
        // testSystem.addComponent("TEG", 3.0);
        // testSystem.addComponent("CO2", 0);
        testSystem.addComponent("methane", 28.13913);
        testSystem.addComponent("ethane", 3.48);
        testSystem.addComponent("propane", 1.64);
        testSystem.addComponent("i-butane", 1.22);
        testSystem.addComponent("n-butane", 6.86);
        // testSystem.addComponent("i-pentane", 6.49);
        testSystem.addComponent("n-pentane", 7.52);
        // testSystem.addComponent("n-hexane", 0);
        testSystem.addComponent("n-heptane", 13.16);
        // testSystem.addComponent("n-octane", 12.64);
        testSystem.addComponent("n-nonane", 7.13);

        // testSystem.addTBPfraction("C10", 22.1, 230.3 / 1000.0, 0.859);
        // testSystem.setHeavyTBPfractionAsPlusFraction();
        // testSystem.getCharacterization().characterisePlusFraction();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.useVolumeCorrection(true);
        testSystem.init(0);
        testSystem.setMultiPhaseCheck(true);
        testSystem.getInterphaseProperties().setInterfacialTensionModel(gtmethod); // GT == 1, LGT=2

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        testSystem.display();
        testOps.calcPTphaseEnvelope();
        testOps.displayResult();
    }
}
