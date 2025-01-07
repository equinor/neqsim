package neqsim.process.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * compressorTest_1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class compressorTest_1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(compressorTest_1.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil((273.15 + 20.0), 10.00);
    testSystem.addComponent("nitrogen", 0.8);
    testSystem.addComponent("oxygen", 2.0);
    // testSystem.addComponent("water", 0.2);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(9);

    Stream stream_1 = new Stream("Stream1", testSystem);

    Compressor comp_1 = new Compressor("compressor", stream_1);
    comp_1.setOutletPressure(40.0);
    comp_1.setUsePolytropicCalc(true);

    comp_1.setPolytropicEfficiency(0.74629255);

    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(stream_1);
    operations.add(comp_1);

    operations.run();

    // comp_1.solvePolytropicEfficiency(380.0);
    // operations.displayResult();
    logger.info("power " + comp_1.getTotalWork());

    logger.info(
        "speed of sound " + comp_1.getOutletStream().getThermoSystem().getPhase(0).getSoundSpeed());
    logger.info("out temperature" + comp_1.getOutletStream().getThermoSystem().getTemperature());
    logger.info("Cp " + comp_1.getOutletStream().getThermoSystem().getPhase(0).getCp());
    logger.info("Cv " + comp_1.getOutletStream().getThermoSystem().getPhase(0).getCv());
    logger
        .info("molarmass " + comp_1.getOutletStream().getThermoSystem().getPhase(0).getMolarMass());

    double outTemp = 500.1; // temperature in Kelvin
    double efficiency = comp_1.solveEfficiency(outTemp);
    logger.info("compressor polytropic efficiency " + efficiency);
    logger.info("compressor out temperature " + comp_1.getOutletStream().getTemperature());
    logger.info("compressor power " + comp_1.getPower() + " J/sec");
    logger.info("compressor head "
        + comp_1.getPower() / comp_1.getThermoSystem().getTotalNumberOfMoles() + " J/mol");
  }
}
