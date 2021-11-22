package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.compressor.Compressor;
import neqsim.processSimulation.processEquipment.mixer.MixerInterface;
import neqsim.processSimulation.processEquipment.mixer.StaticMixer;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class compressorTest {
    private static final long serialVersionUID = 1000;

    /**
     * This method is just meant to test the thermo package.
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
        testSystem.addComponent("CO2", 1800.00);
        testSystem.addComponent("water", 1200.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        Stream stream_1 = new Stream("Stream1", testSystem);

        MixerInterface mixer = new StaticMixer("Mixer 1");
        mixer.addStream(stream_1);
        Stream stream_3 = mixer.getOutStream();
        stream_3.setName("stream3");

        Separator separator = new Separator("Separator 1", stream_3);
        StreamInterface stream_2 = separator.getGasOutStream();
        stream_2.setName("stream2");

        Compressor comp1 = new Compressor(stream_2);
        comp1.setOutletPressure(50.0);

        mixer.addStream(stream_2);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(stream_2);
        operations.add(mixer);
        operations.add(stream_3);
        operations.add(separator);
        operations.add(comp1);

        operations.run();

        operations.displayResult();
    }
}
