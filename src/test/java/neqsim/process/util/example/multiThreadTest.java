package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.MixerInterface;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * multiThreadTest class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class multiThreadTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(multiThreadTest.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
    testSystem.addComponent("methane", 500.00);
    testSystem.addComponent("ethane", 500.00);
    testSystem.addComponent("CO2", 100.00);
    testSystem.addComponent("water", 100.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

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

    Cooler cooler1 = new Cooler("cooler1", comp1.getOutletStream());
    cooler1.setOutTemperature(283.15 + 30);

    // mixer.addStream(stream_2);
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(stream_2);
    operations.add(mixer);
    operations.add(stream_3);
    operations.add(separator);
    operations.add(comp1);
    operations.add(cooler1);

    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 20.00);
    testSystem2.addComponent("methane", 400.00);
    testSystem2.addComponent("ethane", 4.00);
    testSystem2.addComponent("CO2", 100.00);
    testSystem2.addComponent("water", 100.0);
    testSystem2.createDatabase(true);
    testSystem2.setMixingRule(2);

    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
    testOps2.TPflash();

    Stream stream_22 = new Stream("Stream1", testSystem2);

    MixerInterface mixer2 = new StaticMixer("Mixer 1");
    mixer2.addStream(stream_22);
    StreamInterface stream_32 = mixer2.getOutletStream();
    stream_32.setName("stream32");

    Separator separator2 = new Separator("Separator 1", stream_32);
    StreamInterface stream_222 = separator2.getGasOutStream();
    stream_222.setName("stream222");

    Compressor comp12 = new Compressor("comp22", stream_222);
    comp12.setOutletPressure(45.0);

    Cooler cooler12 = new Cooler("cooler12", comp12.getOutletStream());
    cooler12.setOutTemperature(283.15 + 30);

    Separator separator3 = new Separator("Separator 122", cooler12.getOutletStream());

    Recycle resyc = new Recycle("resyc");
    resyc.addStream(separator3.getLiquidOutStream());

    mixer2.addStream(resyc.getOutletStream());

    // mixer2.addStream(stream_222);
    neqsim.process.processmodel.ProcessSystem operations2 =
        new neqsim.process.processmodel.ProcessSystem();
    operations2.add(stream_22);
    operations2.add(mixer2);
    operations2.add(stream_32);
    operations2.add(separator2);
    operations2.add(comp12);
    operations2.add(cooler12);
    operations2.add(separator3);
    operations2.add(resyc);

    long time = System.currentTimeMillis();

    for (int i = 0; i < 1; i++) {
      // operations.run();
      // operations2.run();
      Thread processThread1 = new Thread(operations);
      Thread processThread2 = new Thread(operations2);

      processThread1.start();
      processThread2.start();

      try {
        processThread1.join(1000);
        processThread2.join(1000);
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    // } while (processThread1.isAlive()); // && processThread2.isAlive());

    logger.info("Time taken for simulation = " + (System.currentTimeMillis() - time));

    ((Compressor) operations.getUnit("comp1")).displayResult();
    ((Compressor) operations2.getUnit("comp22")).displayResult();
    // operations2.displayResult();
  }
}
