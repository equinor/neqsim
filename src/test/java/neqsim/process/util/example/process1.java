package neqsim.process.util.example;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * <p>
 * process1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class process1 {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 25.0), 50.00);
        testSystem.addComponent("methane", 180.00);
        testSystem.addComponent("ethane", 10.00);
        testSystem.addComponent("propane", 1.00);
        // testSystem.addComponent("n-nonane", 1.00);
        // testSystem.addComponent("water", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(10);

        testSystem.setPressure(20.0, "bara");
        testSystem.setTemperature(20.0, "C");
        testSystem.setTotalFlowRate(100.0, "Am3/hr");

        testSystem.useVolumeCorrection(false);

        ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);
        ops.TPflash();
        System.out.println("actual flow 1 " + testSystem.getFlowRate("m3/hr"));
        double stdflowrate = testSystem.getFlowRate("Sm3/day");
        testSystem.setTotalFlowRate(stdflowrate, "Sm3/day");
        ops.TPflash();

        double actFLowRate = testSystem.getFlowRate("m3/hr");

        System.out.println("actual flow 2 " + actFLowRate);

        Stream stream_1 = new Stream("Stream1", testSystem);

        neqsim.process.equipment.compressor.Compressor compr =
                new neqsim.process.equipment.compressor.Compressor("compr",
                        stream_1);
        compr.setOutletPressure(80.0);
        compr.setOutTemperature(345.0);
        compr.setUsePolytropicCalc(true);
        // compr.setNumberOfCompressorCalcSteps(10);

        neqsim.process.processmodel.ProcessSystem operations =
                new neqsim.process.processmodel.ProcessSystem();
        operations.add(stream_1);
        operations.add(compr);

        operations.run();
        compr.displayResult();
        System.out.println(
                "polytropic head " + stream_1.getThermoSystem().getFlowRate("m3/hr") + " m3/hr");
        System.out.println("polytropic head " + compr.getPolytropicHead("kJ/kg") + " kJ/kg");
        System.out.println("polytropic efficiency " + compr.getPolytropicEfficiency());

        // operations.displayResult();

        // compr.solvePolytropicEfficiency(compr.getOutStream().getTemperature() +
        // 0.01);
        // operations.displayResult();
    }
}
