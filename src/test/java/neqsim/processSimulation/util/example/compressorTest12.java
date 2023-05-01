package neqsim.processSimulation.util.example;

import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

/**
 * <p>
 * compressorTest12 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class compressorTest12 extends neqsim.NeqSimTest {
    public static SystemInterface getSystem() {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos(265, 49.6);
        testSystem.addComponent("methane", 92);
        testSystem.addComponent("ethane", 4.4);
        testSystem.addComponent("propane", 0.9);
        testSystem.addComponent("CO2", 2);
        testSystem.addComponent("nitrogen", 4.4);
        testSystem.addComponent("i-butane", 0.1);
        testSystem.addComponent("n-butane", 0.1);
        testSystem.addComponent("i-pentane", 1e-12);
        testSystem.addComponent("n-pentane", 1e-12);
        testSystem.addComponent("2-m-C5", 1e-12);
        testSystem.addComponent("n-hexane", 1e-12);
        testSystem.addComponent("3-m-C5", 1e-12);
        testSystem.addComponent("n-heptane", 1e-12);
        testSystem.addComponent("c-hexane", 1e-12);
        testSystem.addComponent("benzene", 1e-12);
        testSystem.addComponent("n-octane", 1e-12);
        testSystem.addComponent("c-C7", 1e-12);
        testSystem.addComponent("toluene", 1e-12);
        testSystem.addComponent("n-nonane", 1e-12);
        testSystem.addComponent("c-C8", 1e-12);
        testSystem.addComponent("m-Xylene", 1e-12);
        testSystem.addComponent("nC10", 1e-12);
        testSystem.createDatabase(true);
        return testSystem;
    }

    @Test
    public void testRun() {
        SystemInterface testSystem = getSystem();
        // testSystem.setMixingRule(2);
        // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testOps.TPflash();

        Stream pre = new Stream("pre", testSystem);
        Compressor ka501 = new Compressor("ka501");
        ka501.setInletStream(pre);
        double p = 64.5 + 1; // insert pressure after comp
        double t = 287; // insert temp after comp
        ka501.setOutletPressure(p);

        pre.run();
        ka501.run();
        ka501.solveEfficiency(t);
    }
}
