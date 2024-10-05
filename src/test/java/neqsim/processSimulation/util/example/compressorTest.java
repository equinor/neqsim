package neqsim.processSimulation.util.example;

import neqsim.processsimulation.processequipment.compressor.Compressor;
import neqsim.processsimulation.processequipment.mixer.MixerInterface;
import neqsim.processsimulation.processequipment.mixer.StaticMixer;
import neqsim.processsimulation.processequipment.separator.Separator;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processequipment.stream.StreamInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>compressorTest class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class compressorTest {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
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
        StreamInterface stream_3 = mixer.getOutletStream();
        stream_3.setName("stream3");

        Separator separator = new Separator("Separator 1", stream_3);
        StreamInterface stream_2 = separator.getGasOutStream();
        stream_2.setName("stream2");

        Compressor comp1 = new Compressor("comp1", stream_2);
        comp1.setOutletPressure(50.0);

        mixer.addStream(stream_2);

        neqsim.processsimulation.processsystem.ProcessSystem operations =
                new neqsim.processsimulation.processsystem.ProcessSystem();
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
