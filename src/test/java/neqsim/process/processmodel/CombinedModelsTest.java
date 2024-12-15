package neqsim.process.processmodel;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

public class CombinedModelsTest {
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

  public ProcessSystem getCompressorProcess() {

    neqsim.process.equipment.stream.Stream gasFeedStream =
        new neqsim.process.equipment.stream.Stream("compressor feed stream");

    neqsim.process.equipment.compressor.Compressor compressor1 =
        new neqsim.process.equipment.compressor.Compressor("Compressor1", gasFeedStream);
    compressor1.setPolytropicEfficiency(0.56);
    compressor1.setUsePolytropicCalc(true);

    ProcessSystem process1 = new ProcessSystem();
    process1.add(gasFeedStream);
    process1.add(compressor1);

    return process1;
  }

  @Test
  public void testProcess() {

    ProcessSystem inletProcess = getinletModel();
    ProcessSystem compressorProcess = getCompressorProcess();
    ((Compressor) compressorProcess.getUnit("Compressor1")).setInletStream(
        ((ThreePhaseSeparator) inletProcess.getUnit("1st stage separator")).getGasOutStream());

    // Set pro1 properties;
    ((Stream) inletProcess.getUnit("HP well stream")).setTemperature(80.0, "C");
    ((Stream) inletProcess.getUnit("HP well stream")).setPressure(50.0, "bara");

    ((Compressor) compressorProcess.getUnit("Compressor1")).setOutletPressure(100.0, "bara");

    ProcessModel fullProcess = new ProcessModel();
    fullProcess.add("feed process", inletProcess);
    fullProcess.add("compressor process", compressorProcess);
    fullProcess.setRunStep(true);

    try {
      fullProcess.run();
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }

    Assertions.assertEquals(164.44139872, ((Compressor) compressorProcess.getUnit("Compressor1"))
        .getOutletStream().getTemperature("C"), 0.1);

    // ((Compressor) compressorProcess.getUnit("Compressor1")).getOutletStream().getFluid()
    // .prettyPrint();

  }

}
