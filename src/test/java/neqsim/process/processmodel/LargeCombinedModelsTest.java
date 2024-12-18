package neqsim.process.processmodel;

import java.io.File;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;


public class LargeCombinedModelsTest {
  static Logger logger = LogManager.getLogger(LargeCombinedModelsTest.class);
  File file = new File("src/test/java/neqsim/process/processmodel");
  String fileFluid1 = file.getAbsolutePath() + "/feedfluid.e300";
  SystemInterface wellFluid = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);

  public ProcessSystem getWellStreamAndManifoldModel(SystemInterface[] args) {
    SystemInterface wellFluid = args != null && args.length > 0 ? args[0] : this.wellFluid;
    // wellFluid.setMultiPhaseCheck(true);

    // Create a new process system
    ProcessSystem process = new ProcessSystem();

    // 1. High Pressure (HP) well stream
    Stream wellStreamHP = new Stream("HP well stream", wellFluid.clone());
    wellStreamHP.setPressure(50.0, "bara");
    wellStreamHP.setTemperature(80.0, "C");
    process.add(wellStreamHP);

    Stream wellStreamLP = new Stream("LP well stream", wellFluid.clone());
    wellStreamLP.setPressure(50.0, "bara");
    wellStreamLP.setTemperature(80.0, "C");
    process.add(wellStreamLP);

    return process;
  };

  @Test
  public void testWellStreamAndManifoldModel() {
    ProcessSystem process = getWellStreamAndManifoldModel(null);
    process.run();
    Assertions.assertEquals(329.8247377,
        ((Stream) process.getUnit("HP well stream")).getFlowRate("kg/hr"), 0.1);
  }

  public ProcessSystem createSeparationTrainProcess(StreamInterface[] args) {
    StreamInterface wellStreamHP = args != null && args.length > 0 ? (StreamInterface) args[0]
        : new Stream("HP well stream", wellFluid.clone());
    // Create a new process system
    ProcessSystem process = new ProcessSystem();

    Heater feedTPsetter = new Heater("feed TP setter", wellStreamHP);
    feedTPsetter.setOutPressure(50.0, "bara");
    feedTPsetter.setOutTemperature(80.0, "C");

    // Step 2: First Stage Separator
    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", feedTPsetter.getOutStream());

    // Step 3: Oil Valve (Throttling Valve) after the First Stage
    ThrottlingValve oilValve1 =
        new ThrottlingValve("oil depres valve", firstStageSeparator.getOilOutStream());
    oilValve1.setOutletPressure(20.0, "bara");

    // Step 4: First Stage Oil Reflux Stream
    Stream oilFirstStage = (Stream) wellStreamHP.clone();
    oilFirstStage.setName("first stage oil reflux");
    oilFirstStage.setFlowRate(10.0, "kg/hr");
    oilFirstStage.setPressure(20.0, "bara");
    oilFirstStage.setTemperature(30.0, "C");

    // Step 5: Mixer for First Stage Oil
    Mixer oilFirstStageMixer = new Mixer("first stage oil mixer");
    oilFirstStageMixer.addStream(oilValve1.getOutletStream());
    oilFirstStageMixer.addStream(oilFirstStage);

    // Step 6: Oil Heater from First Stage
    Heater oilHeaterFromFirstStage =
        new Heater("oil heater second stage", oilFirstStageMixer.getOutletStream());
    oilHeaterFromFirstStage.setOutTemperature(50.0, "C");

    // Step 7: Second Stage Separator
    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage separator", oilHeaterFromFirstStage.getOutletStream());

    // Simulate the LP well stream
    Stream LPwellStream = (Stream) wellStreamHP.clone();
    LPwellStream.setName("LP well stream");
    LPwellStream.setPressure(10.0, "bara");
    LPwellStream.setTemperature(40.0, "C");

    secondStageSeparator.addStream(LPwellStream);

    // Step 8: Second Stage Oil Reflux Stream
    Stream oilSecondStage = (Stream) wellStreamHP.clone();
    oilSecondStage.setName("second stage oil reflux");
    oilSecondStage.setFlowRate(10.0, "kg/hr");
    oilSecondStage.setPressure(7.0, "bara");
    oilSecondStage.setTemperature(30.0, "C");

    // Step 9: Valve for Oil from the Second Stage
    ThrottlingValve valveOilFromSecondStage =
        new ThrottlingValve("valve oil from second stage", secondStageSeparator.getOilOutStream());
    valveOilFromSecondStage.setOutletPressure(7.0, "bara");

    process.add(wellStreamHP);
    process.add(feedTPsetter);
    process.add(firstStageSeparator);
    process.add(oilValve1);
    process.add(oilFirstStage);
    process.add(oilFirstStageMixer);
    process.add(oilHeaterFromFirstStage);
    process.add(secondStageSeparator);
    process.add(oilSecondStage);
    process.add(valveOilFromSecondStage);


    return process;
  }

  @Test
  public void testSeparationTrainProcess() {
    ProcessSystem process = createSeparationTrainProcess(null);
    process.run();

    Assertions.assertEquals(292.861847439,
        ((ThreePhaseSeparator) process.getUnit("1st stage separator")).getOilOutStream()
            .getFlowRate("kg/hr"),
        0.1);

  }

  public ProcessModel getCombinedModel() {
    ProcessSystem wellProcess = getWellStreamAndManifoldModel(new SystemInterface[] {wellFluid});
    ProcessSystem separationTrainA =
        createSeparationTrainProcess(new Stream[] {(Stream) wellProcess.getUnit("HP well stream")});
    ProcessSystem separationTrainB =
        createSeparationTrainProcess(new Stream[] {(Stream) wellProcess.getUnit("HP well stream")});

    ProcessModel combinedProcess = new ProcessModel();
    combinedProcess.add("well and manifold process", wellProcess);
    combinedProcess.add("separation train A", separationTrainA);
    combinedProcess.add("separation train B", separationTrainB);

    return combinedProcess;
  }


  @Test
  public void testCombinedProcess() {
    ProcessModel fullProcess = getCombinedModel();
    fullProcess.setRunStep(true);

    // Set fullProcess properties;
    ((Stream) (fullProcess.get("well and manifold process")).getUnit("HP well stream"))
        .setFlowRate(100000.0, "kg/hr");

    try {
      fullProcess.run();
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }



    Assertions.assertEquals(11206.8277627,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(88793.17223721,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getOilOutStream().getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(11206.8277627,
        ((ThreePhaseSeparator) fullProcess.get("separation train B").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("kg/hr"),
        0.1);

  }

}
