package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class HeatExchanger {
    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
        testSystem.addComponent("methane", 120.00);
        testSystem.addComponent("ethane", 120.0);
        testSystem.addComponent("n-heptane", 3.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        Stream stream_Hot = new Stream("Stream1", testSystem);

        neqsim.thermo.system.SystemInterface testSystem2 =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 40.0), 20.00);
        testSystem2.addComponent("methane", 220.00);
        testSystem2.addComponent("ethane", 120.0);
        // testSystem2.createDatabase(true);
        testSystem2.setMixingRule(2);
        ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
        testOps2.TPflash();

        Stream stream_Cold = new Stream("Stream2", testSystem2);

        neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger heatExchanger1 =
                new neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger(
                        stream_Hot, stream_Cold);
        heatExchanger1.setName("heatEx");

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_Hot);
        operations.add(stream_Cold);
        operations.add(heatExchanger1);

        operations.run();
        // operations.displayResult();

        heatExchanger1.getOutStream(0).displayResult();
        heatExchanger1.getOutStream(1).displayResult();
    }
}
