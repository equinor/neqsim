package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class shtokman_MEG {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 42.0), 130.00);
        testSystem.addComponent("methane", 1.0);
        // testSystem.addComponent("ethane", 10.039);
        // testSystem.addComponent("propane", 5.858);
        testSystem.addComponent("water", 0.7);
        testSystem.addComponent("MEG", 0.3);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(9);

        Stream stream_1 = new Stream("Stream1", testSystem);

        Separator separator = new Separator("Separator 1", stream_1);
        StreamInterface stream_2 = separator.getGasOutStream();

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        try {
            operations.add(separator);
        } finally {
        }
        operations.add(stream_2);

        operations.run();

        stream_2.getThermoSystem().setPressure(130.0);
        stream_2.getThermoSystem().setTemperature(273.15 + 39.0);
        stream_2.getThermoSystem().init(0);
        ThermodynamicOperations ops = new ThermodynamicOperations(stream_2.getThermoSystem());
        try {
            ops.TPflash();
            // stream_2.getThermoSystem().display();
            // stream_2.getThermoSystem().setTemperature(250.0);
            // ops.dewPointTemperatureFlash();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        System.out.println("temp " + stream_2.getThermoSystem().getTemperature());
        operations.displayResult();
    }
}
