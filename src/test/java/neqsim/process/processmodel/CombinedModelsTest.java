package neqsim.process.processmodel;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.report.Report;
import neqsim.thermo.system.SystemInterface;

/**
 * CombinedModelsTest is a test class for validating the combined process model which includes an
 * inlet model and a compressor process.
 * 
 * <p>
 * The class contains methods to set up individual process systems and combine them into a single
 * process model. It also includes a test method to verify the behavior of the combined process
 * model.
 * 
 * <p>
 * Methods:
 * <ul>
 * <li>{@link #getinletModel()}: Sets up the inlet process model including a well stream and a
 * three-phase separator.</li>
 * <li>{@link #getCompressorProcess()}: Sets up the compressor process model including a gas feed
 * stream and a compressor.</li>
 * <li>{@link #getCombinedModel()}: Combines the inlet process model and the compressor process
 * model into a single process model.</li>
 * <li>{@link #testCombinedProcess()}: Tests the combined process model by configuring the
 * temperature and pressure for the well stream and the outlet pressure for the compressor, running
 * the process, and asserting the expected outlet temperature of the compressor.</li>
 * </ul>
 * 
 * <p>
 * Dependencies:
 * <ul>
 * <li>Logger: Used for logging debug information.</li>
 * <li>ProcessSystem: Represents a process system containing various units.</li>
 * <li>Stream: Represents a stream in the process system.</li>
 * <li>ThreePhaseSeparator: Represents a three-phase separator unit in the process system.</li>
 * <li>Compressor: Represents a compressor unit in the process system.</li>
 * <li>ProcessModel: Represents the combined process model.</li>
 * <li>Assertions: Used for asserting expected values in the test method.</li>
 * </ul>
 */
public class CombinedModelsTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CombinedModelsTest.class);

  public ProcessSystem getinletModel() {
    File file = new File("src/test/java/neqsim/process/processmodel");
    String fileFluid1 = file.getAbsolutePath() + "/feedfluid.e300";
    SystemInterface wellFluid = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);
    // wellFluid.setMultiPhaseCheck(true);

    Stream wellStreamHP = new neqsim.process.equipment.stream.Stream("HP well stream", wellFluid);
    wellStreamHP.setFlowRate(10.0, "MSm3/day");

    ThreePhaseSeparator firstStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("1st stage separator",
            wellStreamHP);

    ProcessSystem process1 = new ProcessSystem();
    process1.add(wellStreamHP);
    process1.add(firstStageSeparator);

    return process1;
  };

  public ProcessSystem getCompressorProcess(StreamInterface gasFeedStream) {
    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", gasFeedStream);
    compressor1.setPolytropicMethod("detailed"); // Use detailed for precise calculations
    compressor1.setPolytropicEfficiency(0.56);
    compressor1.setUsePolytropicCalc(true);

    ProcessSystem process1 = new ProcessSystem();
    process1.add(gasFeedStream);
    process1.add(compressor1);

    return process1;
  }

  public ProcessModel getCombinedModel() {
    ProcessSystem inletProcess = getinletModel();
    ProcessSystem compressorProcess = getCompressorProcess(
        ((ThreePhaseSeparator) inletProcess.getUnit("1st stage separator")).getGasOutStream());

    ProcessModel combinedProcess = new ProcessModel();
    combinedProcess.add("feed process", inletProcess);
    combinedProcess.add("compressor process", compressorProcess);

    return combinedProcess;
  }

  /**
   * Test method for the combined process model.
   * 
   * This test sets up a combined process model, configures the temperature and pressure for the "HP
   * well stream" in the "feed process", and sets the outlet pressure for "Compressor1" in the
   * "compressor process". The process is then run, and the test asserts that the outlet temperature
   * of "Compressor1" is as expected.
   * 
   * The expected outlet temperature of "Compressor1" is 164.44139872 degrees Celsius with a
   * tolerance of 0.1 degrees.
   */
  @Test
  public void testCombinedProcess() {
    ProcessModel fullProcess = getCombinedModel();
    fullProcess.setRunStep(true);

    // Set fullProcess properties;
    ((Stream) (fullProcess.get("feed process")).getUnit("HP well stream")).setTemperature(80.0,
        "C");
    ((Stream) (fullProcess.get("feed process")).getUnit("HP well stream")).setPressure(50.0,
        "bara");

    ((Compressor) (fullProcess.get("compressor process")).getUnit("Compressor1"))
        .setOutletPressure(100.0, "bara");

    try {
      fullProcess.run();
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }

    Assertions.assertEquals(164.44139872,
        ((Compressor) fullProcess.get("compressor process").getUnit("Compressor1"))
            .getOutletStream().getTemperature("C"),
        0.1);

    Report reporter = new Report(fullProcess);
    Assertions.assertTrue(fullProcess.getReport_json().equals(reporter.generateJsonReport()));
  }
}
