package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * shtokman_MEG class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class shtokman_MEG {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(shtokman_MEG.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
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

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
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
      logger.error(ex.getMessage(), ex);
    }
    System.out.println("temp " + stream_2.getThermoSystem().getTemperature());
    operations.displayResult();
  }
}
