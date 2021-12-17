package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.heatExchanger.Heater;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.splitter.Splitter;
import neqsim.processSimulation.processEquipment.splitter.SplitterInterface;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class process2 {
    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
        testSystem.addComponent("CO2", 1200.00);
        testSystem.addComponent("water", 1200.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        Stream stream_1 = new Stream("Stream1", testSystem);

        Heater heater = new Heater(stream_1);
        heater.setOutTemperature(310.0);

        MixerInterface mixer = new StaticMixer("Mixer 1");
        mixer.addStream(heater.getOutStream());

        Stream stream_3 = mixer.getOutStream();
        stream_3.setName("stream3");

        Separator separator = new Separator("Separator 1", stream_3);
        Stream stream_2 = new Stream(separator.getGasOutStream());
        stream_2.setName("stream2");

        SplitterInterface splitter = new Splitter("splitter", stream_2, 2);
        Stream stream_5 = splitter.getSplitStream(0);
        stream_5.setName("stream5");

        mixer.addStream(stream_5);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(heater);
        operations.add(mixer);
        operations.add(stream_3);
        operations.add(separator);
        operations.add(stream_2);
        operations.add(splitter);
        operations.add(stream_5);

        for (int i = 0; i < 3; i++) {
            operations.run();
        }
        // operations.displayResult();

        for (int i = 0; i < 3; i++) {
            operations.run();
        }
        operations.displayResult();
    }
}
