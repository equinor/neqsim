package neqsim.process.processmodel;

import java.io.File;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.filter.Filter;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.EnergyStream;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;


public class LargeCombinedModelsTest {
  static Logger logger = LogManager.getLogger(LargeCombinedModelsTest.class);
  File file = new File("src/test/java/neqsim/process/processmodel");
  String fileFluid1 = file.getAbsolutePath() + "/feedfluid.e300";
  SystemInterface wellFluid = neqsim.thermo.util.readwrite.EclipseFluidReadWrite.read(fileFluid1);
  ProcessInput inp = new ProcessInput();

  public static class ProcessInput {

    private List<Double> moleRateHP;

    public List<Double> moleRateLP;

    public Double firstStagePressure = 70.0;
    public Double firstStageTemperature = 50.0;

  }

  public static void updateInput(ProcessSystem process, ProcessInput input) {
    Stream hpStream = (Stream) process.getUnit("HP well stream");
    hpStream.getFluid()
        .setMolarComposition(input.moleRateHP.stream().mapToDouble(Double::doubleValue).toArray());
    hpStream.setPressure(input.firstStagePressure, "bara");
    hpStream.setTemperature(input.firstStageTemperature, "C");
    // Add other updates as necessary...
  }

  public ProcessSystem getWellStreamAndManifoldModel(SystemInterface feedFluid) {

    ProcessSystem process = new ProcessSystem();

    // 1. High Pressure (HP) well stream
    Stream wellStreamHP = new Stream("HP well stream", feedFluid.clone());
    wellStreamHP.setPressure(inp.firstStagePressure, "bara");
    wellStreamHP.setTemperature(inp.firstStageTemperature, "C");
    process.add(wellStreamHP);

    Stream wellStreamLP = new Stream("LP well stream", feedFluid.clone());
    wellStreamLP.setPressure(inp.firstStagePressure, "bara");
    wellStreamLP.setTemperature(inp.firstStageTemperature, "C");
    process.add(wellStreamLP);

    Splitter hpManifold = new Splitter("HP manifold", wellStreamHP);
    hpManifold.setSplitFactors(new double[] {0.4, 0.6});
    process.add(hpManifold);

    Splitter lpManifold = new Splitter("LP manifold", wellStreamHP);
    lpManifold.setSplitFactors(new double[] {0.5, 0.5});
    process.add(hpManifold);

    return process;
  };

  @Test
  public void testWellStreamAndManifoldModel() {
    ProcessSystem process = getWellStreamAndManifoldModel(wellFluid);
    process.run();
    Assertions.assertEquals(329.8247377,
        ((Stream) process.getUnit("HP well stream")).getFlowRate("kg/hr"), 0.1);

    Assertions.assertEquals(131.92989508,
        ((Splitter) process.getUnit("HP manifold")).getSplitStream(0).getFlowRate("kg/hr"), 0.1);

    Assertions.assertEquals(197.894842628,
        ((Splitter) process.getUnit("HP manifold")).getSplitStream(1).getFlowRate("kg/hr"), 0.1);

  }

  public ProcessSystem createSeparationTrainProcess(StreamInterface inputStream) {

    ProcessSystem process = new ProcessSystem();

    Heater feedTPsetter = new Heater("feed TP setter", inputStream);
    feedTPsetter.setOutPressure(70.0, "bara");
    feedTPsetter.setOutTemperature(80.0, "C");

    // Step 2: First Stage Separator
    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", feedTPsetter.getOutStream());

    // Step 3: Oil Valve (Throttling Valve) after the First Stage
    ThrottlingValve oilValve1 =
        new ThrottlingValve("oil depres valve", firstStageSeparator.getOilOutStream());
    oilValve1.setOutletPressure(20.0, "bara");

    // Step 4: First Stage Oil Reflux Stream
    Stream oilFirstStage = (Stream) inputStream.clone();
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
    Stream LPwellStream = (Stream) inputStream.clone();
    LPwellStream.setName("LP well stream");
    LPwellStream.setPressure(10.0, "bara");
    LPwellStream.setTemperature(40.0, "C");

    secondStageSeparator.addStream(LPwellStream);

    // Step 8: Second Stage Oil Reflux Stream
    Stream oilSecondStage = (Stream) inputStream.clone();
    oilSecondStage.setName("second stage oil reflux");
    oilSecondStage.setFlowRate(10.0, "kg/hr");
    oilSecondStage.setPressure(7.0, "bara");
    oilSecondStage.setTemperature(30.0, "C");

    // Step 9: Valve for Oil from the Second Stage
    ThrottlingValve valve_oil_from_seccond_stage =
        new ThrottlingValve("valve oil from second stage", secondStageSeparator.getOilOutStream());
    valve_oil_from_seccond_stage.setOutletPressure(7.0, "bara");

    Mixer oilSeccondStageMixer =
        new neqsim.process.equipment.mixer.Mixer("seccond stage oil mixer");
    oilSeccondStageMixer.addStream(valve_oil_from_seccond_stage.getOutletStream());
    oilSeccondStageMixer.addStream(oilSecondStage);

    ThreePhaseSeparator thirdStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("3rd stage separator",
            oilSeccondStageMixer.getOutStream());

    ThrottlingValve valve_oil_from_third_stage = new neqsim.process.equipment.valve.ThrottlingValve(
        "valve oil from third stage", thirdStageSeparator.getOilOutStream());
    valve_oil_from_third_stage.setOutletPressure(3.0, "bara");

    StreamInterface oilThirdStage = (StreamInterface) inputStream.clone();
    oilThirdStage.setName("third stage oil reflux");
    oilThirdStage.setFlowRate(10.0, "kg/hr");
    oilThirdStage.setPressure(3.0, "bara");
    oilThirdStage.setTemperature(30.0, "C");

    Mixer oilThirdStageMixer = new neqsim.process.equipment.mixer.Mixer("third stage oil mixer");
    oilThirdStageMixer.addStream(valve_oil_from_third_stage.getOutletStream());
    oilThirdStageMixer.addStream(oilThirdStage);

    ThreePhaseSeparator fourthStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("4th stage separator",
            oilThirdStageMixer.getOutStream());

    Cooler firstStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("1st stage cooler",
        fourthStageSeparator.getGasOutStream());
    firstStageCooler.setOutTemperature(30, "C");

    Separator firstStageScrubber = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber", firstStageCooler.getOutStream());

    Pump firststagescrubberpump = new neqsim.process.equipment.pump.Pump("1st stage scrubber pump",
        firstStageScrubber.getLiquidOutStream());
    firststagescrubberpump.setOutletPressure(7.0);

    Compressor firstStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "1st stage compressor", firstStageScrubber.getGasOutStream());
    firstStageCompressor.setOutletPressure(7.0, "bara");
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);

    Mixer firststagegasmixer = new neqsim.process.equipment.mixer.Mixer("first stage mixer");
    firststagegasmixer.addStream(firstStageCompressor.getOutStream());
    firststagegasmixer.addStream(thirdStageSeparator.getGasOutStream());

    Cooler firstStageCooler2 = new neqsim.process.equipment.heatexchanger.Cooler(
        "1st stage cooler2", firststagegasmixer.getOutStream());
    firstStageCooler2.setOutTemperature(30, "C");

    Separator firstStageScrubber2 = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber2", firstStageCooler2.getOutStream());

    Compressor firstStageCompressor2 = new neqsim.process.equipment.compressor.Compressor(
        "2nd stage compressor", firstStageScrubber2.getGasOutStream());
    firstStageCompressor2.setUsePolytropicCalc(true);
    firstStageCompressor2.setPolytropicEfficiency(0.8);
    firstStageCompressor2.setOutletPressure(20.0, "bara");

    Mixer secondstagegasmixer = new neqsim.process.equipment.mixer.Mixer("second Stage mixer");
    secondstagegasmixer.addStream(firstStageCompressor2.getOutStream());
    secondstagegasmixer.addStream(secondStageSeparator.getGasOutStream());

    Cooler secondStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("2nd stage cooler",
        secondstagegasmixer.getOutStream());
    secondStageCooler.setOutTemperature(30.0, "C");

    Separator secondStageScrubber = new neqsim.process.equipment.separator.Separator(
        "2nd stage scrubber", secondStageCooler.getOutStream());

    Compressor secondStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "3rd stage compressor", secondStageScrubber.getGasOutStream());
    secondStageCompressor.setUsePolytropicCalc(true);
    secondStageCompressor.setPolytropicEfficiency(0.8);
    secondStageCompressor.setOutletPressure(70.0, "bara");

    Mixer richGasMixer = new neqsim.process.equipment.mixer.Mixer("fourth Stage mixer");
    richGasMixer.addStream(secondStageCompressor.getOutStream());
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());

    Cooler dewPointControlCooler = new neqsim.process.equipment.heatexchanger.Cooler(
        "dew point cooler", richGasMixer.getOutStream());
    dewPointControlCooler.setOutTemperature(30.0, "C");

    Separator dewPointScrubber = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber", dewPointControlCooler.getOutStream());


    neqsim.process.equipment.heatexchanger.Cooler dewPointControlCooler2 =
        new neqsim.process.equipment.heatexchanger.Cooler("dew point cooler 2",
            dewPointScrubber.getGasOutStream());
    dewPointControlCooler.setOutTemperature(-5.0, "C");

    Separator dewPointScrubber2 = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber 2", dewPointControlCooler2.getOutStream());

    Mixer hpLiqmixer = new neqsim.process.equipment.mixer.Mixer("HP liq gas mixer");
    hpLiqmixer.addStream(dewPointScrubber.getLiquidOutStream());

    Mixer mpLiqmixer = new neqsim.process.equipment.mixer.Mixer("MP liq gas mixer");
    mpLiqmixer.addStream(secondStageScrubber.getLiquidOutStream());

    Mixer lpLiqmixer = new neqsim.process.equipment.mixer.Mixer("LP liq gas mixer");
    lpLiqmixer.addStream(firststagescrubberpump.getOutletStream());
    lpLiqmixer.addStream(firstStageScrubber2.getLiquidOutStream());

    Recycle hpResycle = new neqsim.process.equipment.util.Recycle("HP liq resycle");
    hpResycle.addStream(hpLiqmixer.getOutStream());
    hpResycle.setOutletStream(oilFirstStage);
    hpResycle.setTolerance(1e-2);

    Recycle mpResycle = new neqsim.process.equipment.util.Recycle("MP liq resycle");
    mpResycle.addStream(mpLiqmixer.getOutStream());
    mpResycle.setOutletStream(oilSecondStage);
    mpResycle.setTolerance(1e-2);

    Recycle lpResycle = new neqsim.process.equipment.util.Recycle("LP liq resycle");
    lpResycle.addStream(lpLiqmixer.getOutStream());
    lpResycle.setOutletStream(oilThirdStage);
    lpResycle.setTolerance(1e-2);

    process.add(feedTPsetter);
    process.add(firstStageSeparator);
    process.add(oilValve1);
    process.add(oilFirstStage);
    process.add(oilFirstStageMixer);
    process.add(oilHeaterFromFirstStage);
    process.add(secondStageSeparator);
    process.add(oilSecondStage);
    process.add(valve_oil_from_seccond_stage);
    process.add(oilSeccondStageMixer);
    process.add(thirdStageSeparator);
    process.add(valve_oil_from_third_stage);
    process.add(oilThirdStage);
    process.add(oilThirdStageMixer);
    process.add(fourthStageSeparator);
    process.add(firstStageCooler);
    process.add(firstStageScrubber);
    process.add(firstStageCompressor);
    process.add(firststagegasmixer);
    process.add(firstStageCooler2);
    process.add(firstStageScrubber2);
    process.add(firstStageCompressor2);
    process.add(secondstagegasmixer);
    process.add(secondStageCooler);
    process.add(secondStageScrubber);
    process.add(secondStageCompressor);
    process.add(richGasMixer);
    process.add(dewPointControlCooler);
    process.add(dewPointScrubber);
    process.add(dewPointControlCooler2);
    process.add(dewPointScrubber2);
    process.add(hpLiqmixer);
    process.add(mpLiqmixer);
    process.add(lpLiqmixer);
    process.add(hpResycle);
    process.add(mpResycle);
    process.add(lpResycle);

    return process;
  }

  @Test
  public void testSeparationTrainProcess() {

    ProcessSystem process = getWellStreamAndManifoldModel(wellFluid);
    process.run();
    Assertions.assertEquals(329.8247377,
        ((Stream) process.getUnit("HP well stream")).getFlowRate("kg/hr"), 0.1);

    ProcessSystem sepprocessTrain1 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(0));
    sepprocessTrain1.setRunInSteps(true);
    sepprocessTrain1.run();

    ProcessSystem sepprocessTrain2 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(1));
    sepprocessTrain2.setRunInSteps(true);
    sepprocessTrain2.run();

    Assertions.assertEquals(118.660810625,
        ((ThreePhaseSeparator) sepprocessTrain1.getUnit("1st stage separator")).getOilOutStream()
            .getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(177.99121593807,
        ((ThreePhaseSeparator) sepprocessTrain2.getUnit("1st stage separator")).getOilOutStream()
            .getFlowRate("kg/hr"),
        0.1);
  }

  public ProcessSystem createExpanderProcessModel(Separator dewPointScrubber2,
      ThreePhaseSeparator fourthStageSeparator, Mixer secondstagegasmixer, Mixer firststagegasmixer,
      Mixer mpLiqmixer) {

    ProcessSystem process = new ProcessSystem();

    EnergyStream expander_energy_stream =
        new neqsim.process.equipment.stream.EnergyStream("expander energy");

    Expander turboexpander =
        new neqsim.process.equipment.expander.Expander("TEX", dewPointScrubber2.getGasOutStream());
    turboexpander.setPolytropicEfficiency(0.75);
    turboexpander.setUsePolytropicCalc(true);
    turboexpander.setOutletPressure(55.0, "bara");
    turboexpander.setEnergyStream(expander_energy_stream);
    process.add(turboexpander);
    turboexpander.run();
    turboexpander.getOutStream().getFluid().prettyPrint();

    Separator DPCUScrubber = new neqsim.process.equipment.separator.Separator("TEX LT scrubber",
        turboexpander.getOutStream());
    process.add(DPCUScrubber);

    Mixer NGLpremixer = new neqsim.process.equipment.mixer.Mixer("NGL pre mixer");
    NGLpremixer.addStream(DPCUScrubber.getLiquidOutStream());
    NGLpremixer.addStream(dewPointScrubber2.getLiquidOutStream());
    process.add(NGLpremixer);

    Heater NGLpreflashheater = new neqsim.process.equipment.heatexchanger.Heater(
        "NGL preflash heater", NGLpremixer.getOutletStream());
    NGLpreflashheater.setOutTemperature(0.0, "C");
    process.add(NGLpreflashheater);

    Separator NGLpreflashsseparator = new neqsim.process.equipment.separator.Separator(
        "NGL pre flash separator", NGLpreflashheater.getOutStream());
    process.add(NGLpreflashsseparator);

    ThrottlingValve NGLfeedvalve = new neqsim.process.equipment.valve.ThrottlingValve(
        "NGL column feed valve", NGLpreflashsseparator.getLiquidOutStream());
    NGLfeedvalve.setOutletPressure(20.0, "bara");
    process.add(NGLfeedvalve);

    DistillationColumn NGLcolumn =
        new neqsim.process.equipment.distillation.DistillationColumn("NGL column", 5, true, false);
    NGLcolumn.addFeedStream(NGLfeedvalve.getOutletStream(), 5);
    NGLcolumn.getReboiler().setOutTemperature(273.15 + 40.0);
    NGLcolumn.setTopPressure(7.5);
    NGLcolumn.setBottomPressure(7.5);
    process.add(NGLcolumn);

    // TODO: Add more equipment to the process..
    secondstagegasmixer.addStream(NGLpreflashsseparator.getGasOutStream());
    firststagegasmixer.addStream(NGLcolumn.getGasOutStream());

    Splitter NGLsplitter = new neqsim.process.equipment.splitter.Splitter("NGL splitter",
        NGLcolumn.getLiquidOutStream());
    NGLsplitter.setSplitFactors(new double[] {0.999, 0.001});
    process.add(NGLsplitter);

    Mixer NGLiqmixer = new neqsim.process.equipment.mixer.Mixer("NGL mixer");
    NGLiqmixer.addStream(fourthStageSeparator.getOilOutStream());
    NGLiqmixer.addStream(NGLsplitter.getSplitStream(0));
    mpLiqmixer.addStream(NGLsplitter.getSplitStream(1));
    process.add(NGLiqmixer);

    Heater exportoil = new neqsim.process.equipment.heatexchanger.Heater("export oil cooler",
        NGLiqmixer.getOutStream());
    process.add(exportoil);

    Stream exportoilstream =
        new neqsim.process.equipment.stream.Stream("export oil", exportoil.getOutStream());
    process.add(exportoilstream);

    Heater preheater = new neqsim.process.equipment.heatexchanger.Heater("compresor pre heater",
        DPCUScrubber.getGasOutStream());
    preheater.setOutTemperature(15.0, "C");
    process.add(preheater);

    Compressor compressor_KX25831 = new neqsim.process.equipment.compressor.Compressor(
        "comp_KX25831B", preheater.getOutStream());
    compressor_KX25831.setUsePolytropicCalc(true);
    compressor_KX25831.setPolytropicEfficiency(0.75);
    compressor_KX25831.setEnergyStream(expander_energy_stream);
    compressor_KX25831.setCalcPressureOut(true);
    process.add(compressor_KX25831);

    Filter valve_dp1 = new neqsim.process.equipment.filter.Filter("gas split valve",
        compressor_KX25831.getOutStream());
    valve_dp1.setDeltaP(1.0);
    process.add(valve_dp1);

    return process;
  }

  @Test
  public void tesExpanderProcess() {

    ProcessSystem process = getWellStreamAndManifoldModel(wellFluid);
    process.run();

    ProcessSystem sepprocessTrain1 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(0));
    sepprocessTrain1.setRunInSteps(true);
    sepprocessTrain1.run();

    Assertions.assertEquals(46.723963,
        ((Separator) sepprocessTrain1.getUnit("dew point scrubber 2")).getGasOutStream()
            .getFlowRate("kg/hr"),
        0.1);

    ProcessSystem expanderProcess =
        createExpanderProcessModel((Separator) sepprocessTrain1.getUnit("dew point scrubber 2"),
            (ThreePhaseSeparator) sepprocessTrain1.getUnit("4th stage separator"),
            (Mixer) sepprocessTrain1.getUnit("second Stage mixer"),
            (Mixer) sepprocessTrain1.getUnit("first stage mixer"),
            (Mixer) sepprocessTrain1.getUnit("MP liq gas mixer"));
    expanderProcess.setRunInSteps(true);
    expanderProcess.run();

    Assertions.assertEquals(46.7239639,
        ((Expander) expanderProcess.getUnit("TEX")).getOutStream().getFlowRate("kg/hr"), 0.1);

  }


  public ProcessModel getCombinedModel() {
    ProcessSystem wellProcess = getWellStreamAndManifoldModel(wellFluid);

    ProcessSystem separationTrainA = createSeparationTrainProcess(
        ((Splitter) wellProcess.getUnit("HP manifold")).getSplitStream(0));

    ProcessSystem separationTrainB = createSeparationTrainProcess(
        ((Splitter) wellProcess.getUnit("HP manifold")).getSplitStream(1));

    ProcessSystem expanderProcessA =
        createExpanderProcessModel((Separator) separationTrainA.getUnit("dew point scrubber 2"),
            (ThreePhaseSeparator) separationTrainA.getUnit("4th stage separator"),
            (Mixer) separationTrainA.getUnit("second Stage mixer"),
            (Mixer) separationTrainA.getUnit("first stage mixer"),
            (Mixer) separationTrainA.getUnit("MP liq gas mixer"));



    ProcessModel combinedProcess = new ProcessModel();
    combinedProcess.add("well and manifold process", wellProcess);
    combinedProcess.add("separation train A", separationTrainA);
    combinedProcess.add("separation train B", separationTrainB);
    combinedProcess.add("expander process A", expanderProcessA);

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



    Assertions.assertEquals(4023.0713293,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(35976.92867062,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getOilOutStream().getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(6034.60699406,
        ((ThreePhaseSeparator) fullProcess.get("separation train B").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("kg/hr"),
        0.1);

  }

}
