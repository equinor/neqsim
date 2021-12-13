package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class process1 {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 25.0),
                50.00);
        testSystem.addComponent("methane", 180.00);
        testSystem.addComponent("ethane", 10.00);
        testSystem.addComponent("propane", 1.00);
        // testSystem.addComponent("n-nonane", 1.00);
        // testSystem.addComponent("water", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(10);

                double actFLowRate = testSystem.getFlowRate("m3/hr");

                System.out.println("actual flow 2 " + actFLowRate);

                Stream stream_1 = new Stream("Stream1", testSystem);

                neqsim.processSimulation.processEquipment.compressor.Compressor compr =
                                new neqsim.processSimulation.processEquipment.compressor.Compressor(
                                                stream_1);
                compr.setOutletPressure(80.0);
                compr.setOutTemperature(345.0);
                compr.setUsePolytropicCalc(true);
                // compr.setNumberOfCompresorCalcSteps(10);

                neqsim.processSimulation.processSystem.ProcessSystem operations =
                                new neqsim.processSimulation.processSystem.ProcessSystem();
                operations.add(stream_1);
                operations.add(compr);

                operations.run();
                compr.displayResult();
                System.out.println("polytropic head "
                                + stream_1.getThermoSystem().getFlowRate("m3/hr") + " m3/hr");
                System.out.println(
                                "polytropic head " + compr.getPolytropicHead("kJ/kg") + " kJ/kg");
                System.out.println("polytropic efficiency " + compr.getPolytropicEfficiency());

                // operations.displayResult();

                // compr.solvePolytropicEfficiency(compr.getOutStream().getTemperature() +
                // 0.01);
                // operations.displayResult();
        }
}
