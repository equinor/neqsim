package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.mixer.Mixer;
import neqsim.processSimulation.processEquipment.mixer.StaticPhaseMixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class OffshoreProcess2 {
    /**
     * This method is just meant to test the thermo package.
     */
    @SuppressWarnings("unused")

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPA(273.15 + 20.0, 31.0);
        // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15+20.0,
        // 31.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("nitrogen", 0.372);
        testSystem.addComponent("CO2", 3.344);
        testSystem.addComponent("methane", 82.224);
        testSystem.addComponent("ethane", 6.812);
        testSystem.addComponent("propane", 2.771);
        testSystem.addComponent("i-butane", 0.434);
        testSystem.addComponent("n-butane", 0.9);
        testSystem.addComponent("n-pentane", 0.356);
        testSystem.addComponent("i-pentane", 0.303);

        testSystem.addTBPfraction("C6", 10.428, 86.178 / 1000.0, 0.664);
        testSystem.addTBPfraction("C7", 0.626, 96.00 / 1000.0, 0.738);
        testSystem.addTBPfraction("C8", 0.609, 107.00 / 1000.0, 0.7650);
        testSystem.addTBPfraction("C9", 0.309, 121.00 / 1000.0, 0.781);
        testSystem.addTBPfraction("C10-C11", 0.254, 140.09 / 1000.0, 0.7936);
        testSystem.addTBPfraction("C12-C13", 0.137, 167.558 / 1000.0, 0.8082);
        testSystem.addTBPfraction("C14-C15", 0.067, 197.395 / 1000.0, 0.8205);
        testSystem.addTBPfraction("C16-C17", 0.03, 229.026 / 1000.0, 0.8313);
        testSystem.addTBPfraction("C18-C20", 0.017, 261.991 / 1000.0, 0.8428);
        testSystem.addTBPfraction("C21-C23", 0.005, 303.531 / 1000.0, 0.8551);
        testSystem.addComponent("water", 1.0e-10);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);

        SystemInterface testSystem2 = new SystemSrkCPA(273.15 + 20.0, 31.0);
        testSystem2.addComponent("water", 10.0);
        testSystem2.setMixingRule(7);

        Stream stream_1 = new Stream("Stream1", testSystem);

        Separator separator = new Separator("Separator 1", stream_1);

        StreamInterface stream_2 = separator.getGasOutStream();
        stream_2.setName("gas out stream");

        StreamInterface stream_3 = separator.getLiquidOutStream();
        stream_3.setName("liquid out stream");

        Stream stream_4 = new Stream("water_stream", testSystem2);

        Mixer mixer = new StaticPhaseMixer("Mixer 1");
        mixer.addStream(stream_2);
        mixer.addStream(stream_4);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(separator);
        operations.add(stream_2);
        operations.add(stream_3);
        operations.add(stream_4);
        operations.add(mixer);

        operations.run();
        operations.displayResult();

        // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testOps.calcPTphaseEnvelope();
        // testOps.displayResult();
    }
}
