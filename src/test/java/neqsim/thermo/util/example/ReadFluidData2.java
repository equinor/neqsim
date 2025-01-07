package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReadFluidData2 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class ReadFluidData2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ReadFluidData.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 30.0, 10.0);
    // testSystem.readObjectFromFile("C:/temp/neqsimfluids/-65919.68493879325.neqsim",
    testSystem = testSystem.readObjectFromFile("c:/temp/neqsimfluidwater.neqsim", "");
    testSystem.init(0);
    testSystem.display();
    Stream stream_1 = new Stream("Stream1", testSystem);
    ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator", stream_1);
    stream_1.run();
    separator.run();
    separator.displayResult();
    separator.run();
    StreamInterface gas = separator.getGasOutStream();
    StreamInterface oil = separator.getOilOutStream();
    StreamInterface water = separator.getWaterOutStream();
    // gas.run();
    gas.displayResult();
    StreamInterface str1 = separator.getGasOutStream();

    // testSystem.getPhase(1).getN;
    /*
     * testSystem.init(0); testSystem.setPressure(100.0); testSystem.setTemperature(273.15 + 15.0);
     * // // ""); // testSystem.addComponent("water", 1.0); testSystem.setMixingRule(2); //
     * testSystem.setMultiPhaseCheck(true); //testSystem.setMultiPhaseCheck(false);
     * ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
     *
     * try { testOps.TPflash(); testSystem.display(); testOps.PSflash(-123.108602625942);
     * testSystem.display(); testSystem.setPressure(100.0); testOps.PSflash(-119.003271056256);
     * testSystem.display(); System.out.println("entropy " + testSystem.getEntropy());
     * //testSystem.setPressure(100.0); //testOps.PSflash(-1.503016881785468e+02);
     * //testSystem.display(); //testSystem.setPressure(100.0);
     * testOps.PSflash(-1.266377583884310e+02); } catch (Exception ex) {
     * logger.error(ex.getMessage(), ex); }
     */
  }
}
