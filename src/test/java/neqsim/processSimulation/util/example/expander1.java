package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.stream.Stream;

/**
 * <p>expander1 class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class expander1 {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0),
                120.00);
        testSystem.addComponent("methane", 180.00);
        testSystem.addComponent("ethane", 10.00);
        testSystem.addComponent("propane", 1.00);
        testSystem.addComponent("n-nonane", 1.00);
        // testSystem.addComponent("water", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setMultiPhaseCheck(true);

        Stream stream_1 = new Stream("Stream1", testSystem);

        // neqsim.processSimulation.processEquipment.expander.Expander expander = new
        // neqsim.processSimulation.processEquipment.expander.Expander(stream_1);
        neqsim.processSimulation.processEquipment.compressor.Compressor expander = new neqsim.processSimulation.processEquipment.compressor.Compressor(
                stream_1);

        expander.setOutletPressure(80.0);
        expander.setPolytropicEfficiency(0.9);
        expander.setIsentropicEfficiency(0.9);
        expander.setUsePolytropicCalc(true);

        neqsim.processSimulation.processSystem.ProcessSystem operations = new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(expander);

        operations.run();

        // expander.solveEfficiency(272.50);
        operations.displayResult();

        // compr.solvePolytropicEfficiency(compr.getOutStream().getTemperature() +
        // 0.01);
        // operations.displayResult();
    }
}
