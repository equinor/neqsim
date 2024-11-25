package neqsim.process.util.example;

import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.splitter.SplitterInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * process2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class process2 {
  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
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

    Heater heater = new Heater("heater", stream_1);
    heater.setOutTemperature(310.0);

    MixerInterface mixer = new StaticMixer("Mixer 1");
    mixer.addStream(heater.getOutletStream());

    StreamInterface stream_3 = mixer.getOutletStream();
    stream_3.setName("stream3");

    Separator separator = new Separator("Separator 1", stream_3);
    Stream stream_2 = new Stream("stream2", separator.getGasOutStream());

    SplitterInterface splitter = new Splitter("splitter", stream_2, 2);
    StreamInterface stream_5 = splitter.getSplitStream(0);
    stream_5.setName("stream5");

    mixer.addStream(stream_5);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
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
