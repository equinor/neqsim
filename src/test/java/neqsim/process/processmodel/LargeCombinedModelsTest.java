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
import neqsim.thermo.system.SystemPrEos;


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

    Stream wellStreamHP = new Stream("HP well stream", feedFluid.clone());
    wellStreamHP.setPressure(inp.firstStagePressure, "bara");
    wellStreamHP.setTemperature(inp.firstStageTemperature, "C");
    wellStreamHP.setFlowRate(30.0, "MSm3/day");
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
    Assertions.assertEquals(29.99999999999,
        ((Stream) process.getUnit("HP well stream")).getFlowRate("MSm3/day"), 0.1);

    Assertions.assertEquals(11.9999999,
        ((Splitter) process.getUnit("HP manifold")).getSplitStream(0).getFlowRate("MSm3/day"), 0.1);

    Assertions.assertEquals(17.99999999999,
        ((Splitter) process.getUnit("HP manifold")).getSplitStream(1).getFlowRate("MSm3/day"), 0.1);

  }

  public ProcessSystem createSeparationTrainProcess(StreamInterface inputStream) {

    ProcessSystem process = new ProcessSystem();

    Heater feedTPsetter = new Heater("feed TP setter", inputStream);
    feedTPsetter.setOutPressure(70.0, "bara");
    feedTPsetter.setOutTemperature(80.0, "C");

    // Step 2: First Stage Separator
    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", feedTPsetter.getOutletStream());

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
    LPwellStream.setFlowRate(10.0, "kg/hr");
    LPwellStream.setPressure(10.0, "bara");
    LPwellStream.setTemperature(40.0, "C");
    LPwellStream.run();

    secondStageSeparator.addStream(LPwellStream);

    // Step 8: Second Stage Oil Reflux Stream
    Stream oilSecondStage = (Stream) inputStream.clone();
    oilSecondStage.setName("second stage oil reflux");
    oilSecondStage.setFlowRate(10.0, "kg/hr");
    oilSecondStage.setPressure(7.0, "bara");
    oilSecondStage.setTemperature(30.0, "C");
    oilSecondStage.run();

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
            oilSeccondStageMixer.getOutletStream());

    ThrottlingValve valve_oil_from_third_stage = new neqsim.process.equipment.valve.ThrottlingValve(
        "valve oil from third stage", thirdStageSeparator.getOilOutStream());
    valve_oil_from_third_stage.setOutletPressure(3.0, "bara");

    StreamInterface oilThirdStage = (StreamInterface) inputStream.clone();
    oilThirdStage.setName("third stage oil reflux");
    oilThirdStage.setFlowRate(10.0, "kg/hr");
    oilThirdStage.setPressure(3.0, "bara");
    oilThirdStage.setTemperature(30.0, "C");
    oilThirdStage.run();

    Mixer oilThirdStageMixer = new neqsim.process.equipment.mixer.Mixer("third stage oil mixer");
    oilThirdStageMixer.addStream(valve_oil_from_third_stage.getOutletStream());
    oilThirdStageMixer.addStream(oilThirdStage);

    ThreePhaseSeparator fourthStageSeparator =
        new neqsim.process.equipment.separator.ThreePhaseSeparator("4th stage separator",
            oilThirdStageMixer.getOutletStream());

    Cooler firstStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("1st stage cooler",
        fourthStageSeparator.getGasOutStream());
    firstStageCooler.setOutTemperature(30, "C");

    Separator firstStageScrubber = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber", firstStageCooler.getOutletStream());

    Pump firststagescrubberpump = new neqsim.process.equipment.pump.Pump("1st stage scrubber pump",
        firstStageScrubber.getLiquidOutStream());
    firststagescrubberpump.setOutletPressure(7.0);

    Compressor firstStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "1st stage compressor", firstStageScrubber.getGasOutStream());
    firstStageCompressor.setOutletPressure(7.0, "bara");
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.8);

    Mixer firststagegasmixer = new neqsim.process.equipment.mixer.Mixer("first stage mixer");
    firststagegasmixer.addStream(firstStageCompressor.getOutletStream());
    firststagegasmixer.addStream(thirdStageSeparator.getGasOutStream());

    Cooler firstStageCooler2 = new neqsim.process.equipment.heatexchanger.Cooler(
        "1st stage cooler2", firststagegasmixer.getOutletStream());
    firstStageCooler2.setOutTemperature(30, "C");

    Separator firstStageScrubber2 = new neqsim.process.equipment.separator.Separator(
        "1st stage scrubber2", firstStageCooler2.getOutletStream());

    Compressor firstStageCompressor2 = new neqsim.process.equipment.compressor.Compressor(
        "2nd stage compressor", firstStageScrubber2.getGasOutStream());
    firstStageCompressor2.setUsePolytropicCalc(true);
    firstStageCompressor2.setPolytropicEfficiency(0.8);
    firstStageCompressor2.setOutletPressure(20.0, "bara");

    Mixer secondstagegasmixer = new neqsim.process.equipment.mixer.Mixer("second Stage mixer");
    secondstagegasmixer.addStream(firstStageCompressor2.getOutletStream());
    secondstagegasmixer.addStream(secondStageSeparator.getGasOutStream());

    Cooler secondStageCooler = new neqsim.process.equipment.heatexchanger.Cooler("2nd stage cooler",
        secondstagegasmixer.getOutletStream());
    secondStageCooler.setOutTemperature(30.0, "C");

    Separator secondStageScrubber = new neqsim.process.equipment.separator.Separator(
        "2nd stage scrubber", secondStageCooler.getOutletStream());

    Compressor secondStageCompressor = new neqsim.process.equipment.compressor.Compressor(
        "3rd stage compressor", secondStageScrubber.getGasOutStream());
    secondStageCompressor.setUsePolytropicCalc(true);
    secondStageCompressor.setPolytropicEfficiency(0.8);
    secondStageCompressor.setOutletPressure(70.0, "bara");

    Mixer richGasMixer = new neqsim.process.equipment.mixer.Mixer("fourth Stage mixer");
    richGasMixer.addStream(secondStageCompressor.getOutletStream());
    richGasMixer.addStream(firstStageSeparator.getGasOutStream());

    Cooler dewPointControlCooler = new neqsim.process.equipment.heatexchanger.Cooler(
        "dew point cooler", richGasMixer.getOutletStream());
    dewPointControlCooler.setOutTemperature(30.0, "C");

    Separator dewPointScrubber = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber", dewPointControlCooler.getOutletStream());


    neqsim.process.equipment.heatexchanger.Cooler dewPointControlCooler2 =
        new neqsim.process.equipment.heatexchanger.Cooler("dew point cooler 2",
            dewPointScrubber.getGasOutStream());
    dewPointControlCooler.setOutTemperature(-5.0, "C");

    Separator dewPointScrubber2 = new neqsim.process.equipment.separator.Separator(
        "dew point scrubber 2", dewPointControlCooler2.getOutletStream());

    Mixer hpLiqmixer = new neqsim.process.equipment.mixer.Mixer("HP liq gas mixer");
    hpLiqmixer.addStream(dewPointScrubber.getLiquidOutStream());

    Mixer mpLiqmixer = new neqsim.process.equipment.mixer.Mixer("MP liq gas mixer");
    mpLiqmixer.addStream(secondStageScrubber.getLiquidOutStream());

    Mixer lpLiqmixer = new neqsim.process.equipment.mixer.Mixer("LP liq gas mixer");
    lpLiqmixer.addStream(firststagescrubberpump.getOutletStream());
    lpLiqmixer.addStream(firstStageScrubber2.getLiquidOutStream());

    Recycle hpResycle = new neqsim.process.equipment.util.Recycle("HP liq resycle");
    hpResycle.addStream(hpLiqmixer.getOutletStream());
    hpResycle.setOutletStream(oilFirstStage);
    hpResycle.setTolerance(1e-2);

    Recycle mpResycle = new neqsim.process.equipment.util.Recycle("MP liq resycle");
    mpResycle.addStream(mpLiqmixer.getOutletStream());
    mpResycle.setOutletStream(oilSecondStage);
    mpResycle.setTolerance(1e-2);

    Recycle lpResycle = new neqsim.process.equipment.util.Recycle("LP liq resycle");
    lpResycle.addStream(lpLiqmixer.getOutletStream());
    lpResycle.setOutletStream(oilThirdStage);
    lpResycle.setTolerance(1e-2);

    process.add(feedTPsetter);
    process.add(firstStageSeparator);
    process.add(oilValve1);
    process.add(oilFirstStage);
    process.add(oilFirstStageMixer);
    process.add(oilHeaterFromFirstStage);
    process.add(secondStageSeparator);
    process.add(LPwellStream);
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
    process.add(firststagescrubberpump);
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
    Assertions.assertEquals(29.999999999,
        ((Stream) process.getUnit("HP well stream")).getFlowRate("MSm3/day"), 0.1);

    ProcessSystem sepprocessTrain1 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(0));
    sepprocessTrain1.setRunInSteps(true);
    sepprocessTrain1.run();

    ProcessSystem sepprocessTrain2 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(1));
    sepprocessTrain2.setRunInSteps(true);
    sepprocessTrain2.run();

    Assertions.assertEquals(6.459824768494631,
        ((ThreePhaseSeparator) sepprocessTrain1.getUnit("1st stage separator")).getOilOutStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(7.72480776,
        ((Separator) sepprocessTrain1.getUnit("dew point scrubber 2")).getGasOutStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(9.6897371527419,
        ((ThreePhaseSeparator) sepprocessTrain2.getUnit("1st stage separator")).getOilOutStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(11.58711783545,
        ((Separator) sepprocessTrain2.getUnit("dew point scrubber 2")).getGasOutStream()
            .getFlowRate("MSm3/day"),
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

    Separator DPCUScrubber = new neqsim.process.equipment.separator.Separator("TEX LT scrubber",
        turboexpander.getOutletStream());
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
        "NGL pre flash separator", NGLpreflashheater.getOutletStream());
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
        NGLiqmixer.getOutletStream());
    process.add(exportoil);

    Stream exportoilstream =
        new neqsim.process.equipment.stream.Stream("export oil", exportoil.getOutletStream());
    process.add(exportoilstream);

    Heater preheater = new neqsim.process.equipment.heatexchanger.Heater("compresor pre heater",
        DPCUScrubber.getGasOutStream());
    preheater.setOutTemperature(15.0, "C");
    process.add(preheater);

    Compressor compressor_KX25831 = new neqsim.process.equipment.compressor.Compressor(
        "comp_KX25831", preheater.getOutletStream());
    compressor_KX25831.setUsePolytropicCalc(true);
    compressor_KX25831.setPolytropicEfficiency(0.75);
    compressor_KX25831.setEnergyStream(expander_energy_stream);
    compressor_KX25831.setCalcPressureOut(true);
    process.add(compressor_KX25831);

    ThrottlingValve valve_dp1 = new neqsim.process.equipment.valve.ThrottlingValve(
        "gas split valve", compressor_KX25831.getOutletStream());
    valve_dp1.setDeltaPressure(1.0, "bara");
    process.add(valve_dp1);

    return process;
  }

  @Test
  public void tesExpanderProcess() {

    ProcessSystem process = getWellStreamAndManifoldModel(wellFluid);
    process.run();

    ProcessSystem sepprocessTrain1 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(0));
    // sepprocessTrain1.setRunInSteps(true);
    sepprocessTrain1.run();

    ProcessSystem sepprocessTrain2 =
        createSeparationTrainProcess(((Splitter) process.getUnit("HP manifold")).getSplitStream(1));
    // sepprocessTrain2.setRunInSteps(true);
    sepprocessTrain2.run();

    Assertions.assertEquals(8.69422423996,
        ((Separator) sepprocessTrain1.getUnit("dew point scrubber 2")).getGasOutStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(13.0413807213,
        ((Separator) sepprocessTrain2.getUnit("dew point scrubber 2")).getGasOutStream()
            .getFlowRate("MSm3/day"),
        0.1);

    ProcessSystem expanderProcess1 =
        createExpanderProcessModel((Separator) sepprocessTrain1.getUnit("dew point scrubber 2"),
            (ThreePhaseSeparator) sepprocessTrain1.getUnit("4th stage separator"),
            (Mixer) sepprocessTrain1.getUnit("second Stage mixer"),
            (Mixer) sepprocessTrain1.getUnit("first stage mixer"),
            (Mixer) sepprocessTrain1.getUnit("MP liq gas mixer"));
    expanderProcess1.setRunInSteps(true);
    expanderProcess1.run();

    Assertions.assertEquals(8.69422423996,
        ((Expander) expanderProcess1.getUnit("TEX")).getOutletStream().getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(8.4376749385,
        ((ThrottlingValve) expanderProcess1.getUnit("gas split valve")).getOutletStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(60.1288734,
        ((ThrottlingValve) expanderProcess1.getUnit("gas split valve")).getOutletStream()
            .getPressure("bara"),
        0.1);

    ProcessSystem expanderProcess2 =
        createExpanderProcessModel((Separator) sepprocessTrain2.getUnit("dew point scrubber 2"),
            (ThreePhaseSeparator) sepprocessTrain2.getUnit("4th stage separator"),
            (Mixer) sepprocessTrain2.getUnit("second Stage mixer"),
            (Mixer) sepprocessTrain2.getUnit("first stage mixer"),
            (Mixer) sepprocessTrain2.getUnit("MP liq gas mixer"));
    expanderProcess2.setRunInSteps(true);
    expanderProcess2.run();

    Assertions.assertEquals(13.04138072,
        ((Expander) expanderProcess2.getUnit("TEX")).getOutletStream().getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(12.65654610568,
        ((ThrottlingValve) expanderProcess2.getUnit("gas split valve")).getOutletStream()
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(60.1288734,
        ((ThrottlingValve) expanderProcess2.getUnit("gas split valve")).getOutletStream()
            .getPressure("bara"),
        0.1);

  }

  public ProcessSystem getExportCopressorModel(StreamInterface feedStream) {
    ProcessSystem process = new ProcessSystem();

    Filter valve_dp1 = new neqsim.process.equipment.filter.Filter("gas split valve", feedStream);
    valve_dp1.setDeltaP(1.0, "bara");
    process.add(valve_dp1);

    // Define chart data
    double[] chartConditions = {}; // Used to set molecular weight etc.

    double[] speed = {6056, 6922, 7788, 8452, 8653, 9062};

    double[][] flow = {{5142.0765, 6978.1421, 8087.4317, 9120.2186},
        {5848.6679, 7627.7535, 9001.4248, 10434.2025},
        {6663.9168, 6684.2612, 8968.5565, 10866.4216, 12176.113},
        {7842.1913, 9985.0959, 11963.8853, 13415.3073},
        {8288.4078, 9986.9093, 12007.0675, 13862.3171},
        {8736.8911, 11001.182, 12657.5213, 14593.6951}};

    double[][] head = {{63.51995404800001, 59.95565633700001, 52.876617111, 42.72948567000001},
        {82.842135201, 77.94996177600001, 69.992872614, 55.8786330900000},
        {106.11871754400002, 106.557742512, 99.451383417, 84.443210586, 63.7378782930000},
        {124.987260753, 118.76466984300001, 103.75395524100001, 83.483835750000},
        {129.36988610100002, 125.79923445300001, 114.30453176100002, 90.94408372800001},
        {142.54571770200002, 136.319314626, 126.15502941900002, 102.7920396150000}};

    double[][] flowPolyEff = {{5142.0765, 6978.1421, 8087.4317, 9120.2186},
        {5868.8525, 7743.1694, 8508.1967, 9273.224, 10459.0164},
        {6748.6339, 9005.4645, 10114.7541, 10918.0328, 12161.2022},
        {7896.1749, 10650.2732, 11568.306, 12065.5738, 13404.3716},
        {8278.6885, 10956.2842, 11912.5683, 12486.3388, 13863.388},
        {8775.9563, 10803.2787, 11989.071, 12868.8525, 14551.9126}};

    double[][] polyEff = {{75.6796, 79.4536, 76.4411, 65.3459},
        {75.2319, 79.0048, 78.556, 75.9795, 64.8797}, {75.6307, 79.8175, 78.9327, 75.0785, 59.7215},
        {76.0214, 78.9164, 78.0374, 75.0436, 63.088}, {75.5842, 78.9071, 78.027, 75.0308, 64.7762},
        {75.9947, 78.4862, 79.7268, 76.7213, 65.6063}};

    String headUnit = "kJ/kg";

    double[] surgeFlow = {5588.855374539698, 5588.855374539698, 5626.89941, 5826.08345, 6025.26749,
        6224.45153, 6423.63557, 6622.8196100000005, 6822.003650000001, 7021.18769, 7220.37173,
        7419.55577, 7618.73981, 7817.92385, 8017.10789, 8216.29193, 8415.47597, 8614.66001,
        8813.84405, 9013.02809, 9212.21213, 9411.39617, 9546.675330043363};

    double[] surgeHead = {42.72948567000001, 62.60864535225944, 63.51995404800001,
        68.29121833446465, 73.06248262092927, 77.8337469073939, 82.60501119385853, 87.7551980890982,
        92.92520167324781, 98.09520525739738, 103.26520884154696, 107.41797208771176,
        110.31767659420001, 113.21738110068823, 116.11708560717645, 119.01679011366468,
        121.9164946201529, 124.81619912664114, 126.6608293908074, 128.43931588909822,
        131.9061592019831, 137.22593845199154, 140.8389551804265};

    double[] chokeFlow = {9136.016, 10434.202, 12176.113, 13415.308, 13862.317, 14593.695,
        12657.521, 11001.182, 8736.891};
    double[] chokeHead = {42.729485, 55.87863, 63.737877, 83.48383, 90.944084, 102.79204, 126.15503,
        136.31932, 142.54572};

    // Compressor setup
    Compressor compressor_KA27831 =
        new neqsim.process.equipment.compressor.Compressor("KA27831", valve_dp1.getOutletStream());
    compressor_KA27831.setUsePolytropicCalc(true);
    compressor_KA27831.setSpeed(7600);
    compressor_KA27831.setUseGERG2008(false);
    // compressor_KA27831.setCompressorChartType("interpolate");
    compressor_KA27831.getCompressorChart().setCurves(chartConditions, speed, flow, head,
        flowPolyEff, polyEff);
    compressor_KA27831.getCompressorChart().setHeadUnit(headUnit);
    compressor_KA27831.getCompressorChart().getSurgeCurve().setCurve(chartConditions, surgeFlow,
        surgeHead);
    compressor_KA27831.getCompressorChart().getStoneWallCurve().setCurve(chartConditions, chokeFlow,
        chokeHead);
    process.add(compressor_KA27831);

    Cooler cooler_HA27831 = new neqsim.process.equipment.heatexchanger.Cooler("HA27831",
        compressor_KA27831.getOutletStream());
    cooler_HA27831.setOutTemperature(30, "C");
    process.add(cooler_HA27831);

    double[] chartConditions_KA27841 = {}; // Used to set molecular weight etc.

    double[] speed_KA27841 = {6057, 6922, 7890, 8653, 9086};

    double[][] flow_KA27841 = {{2061.8, 2494.38, 3241.57, 3772.47, 4106.74},
        {2356.74, 3064.61, 3831.46, 4735.96}, {2848.31, 3949.44, 4657.3, 5365.17},
        {3398.88, 4441.01, 5306.18, 6033.71}, {3634.83, 4500, 5483.15, 6367.98}};

    double[][] head_KA27841 = {{41.44, 40.18, 35.99, 30.53, 25.92}, {53.61, 51.52, 46.06, 33.05},
        {67.88, 63.69, 56.55, 41.44}, {84.25, 78.38, 69.14, 53.61}, {92.64, 87.61, 77.54, 58.65}};

    double[][] flowPolyEff_KA27841 = {{2032.085, 2856.825, 3393.439, 3795.561, 4120.573},
        {2415.522, 3451.125, 3949.375, 4724.254}, {3009.797, 3987.866, 4658.589, 4984.06, 5471.654},
        {3450.717, 4754.537, 5329.311, 6056.369}, {3623.19, 4869.604, 5501.988, 6343.96}};

    double[][] polyEff_KA27841 = {{74.468, 78.298, 77.234, 73.617, 66.383},
        {74.894, 78.723, 77.447, 65.106}, {75.106, 78.723, 77.021, 73.617, 63.404},
        {75.319, 77.872, 75.319, 63.83}, {74.894, 78.298, 76.596, 64.255}};

    String headUnit_KA27841 = "kJ/kg";

    double[] surgeFlow_KA27841 = {2254.705013080774, 2254.705013080774, 2267.9775799999998,
        2354.4944334999996, 2441.011287, 2527.5281404999996, 2614.044994, 2700.5618474999997,
        2787.0787009999995, 2873.5955544999997, 2960.112408, 3046.6292614999998, 3133.1461149999996,
        3219.6629685, 3306.179822, 3392.6966755, 3479.2135289999997, 3565.7303825,
        3652.2472359999997, 3738.7640895, 3825.2809429999998, 3911.7977965, 3944.639486569277};

    double[] surgeHead_KA27841 = {25.915186872000003, 40.94541953943677, 41.443315209000005,
        44.68883410252227, 47.93435299604455, 51.179871889566826, 54.18477452074823,
        56.46782928268527, 58.75088404462232, 61.03393880655938, 63.31699356849644,
        65.60004833043348, 67.88310309625099, 70.17226196517589, 72.46142083410078,
        74.75057970302565, 77.03973857195052, 79.32889744087541, 81.7325145287474,
        84.25058989377591, 87.04845041018393, 89.84631092659198, 90.90837530156303};

    double[] chokeFlow_KA27841 =
        {4106.7416, 4735.9551, 5365.1685, 5876.4045, 6033.7079, 6367.98, 5483.15, 4500, 3634.83};
    double[] chokeHead_KA27841 = {25.91518687, 33.04973304, 41.44331521, 49.83689836, 53.61401029,
        58.65, 77.54, 87.61, 92.64};

    Compressor compressor_KA27841 = new neqsim.process.equipment.compressor.Compressor("KA27841",
        cooler_HA27831.getOutletStream());
    compressor_KA27841.setUsePolytropicCalc(true);
    compressor_KA27841.setSpeed(7600);
    compressor_KA27841.setUseGERG2008(false);
    compressor_KA27841.getCompressorChart().setCurves(chartConditions_KA27841, speed_KA27841,
        flow_KA27841, head_KA27841, flowPolyEff_KA27841, polyEff_KA27841);
    compressor_KA27841.getCompressorChart().setHeadUnit(headUnit_KA27841);
    compressor_KA27841.getCompressorChart().getSurgeCurve().setCurve(chartConditions_KA27841,
        surgeFlow_KA27841, surgeHead_KA27841);
    compressor_KA27841.getCompressorChart().getStoneWallCurve().setCurve(chartConditions_KA27841,
        chokeFlow_KA27841, chokeHead_KA27841);
    process.add(compressor_KA27841);

    Splitter splitter_TEE_104 = new neqsim.process.equipment.splitter.Splitter("TEE-104",
        compressor_KA27841.getOutletStream());
    splitter_TEE_104.setFlowRates(new double[] {-1, 0.0001}, "MSm3/day");
    process.add(splitter_TEE_104);

    return process;
  }

  @Test
  public void testExportCopressorModel() {

    SystemInterface gasFluid = new SystemPrEos(273.15 + 20.0, 10.0);
    gasFluid.addComponent("methane", 0.9);
    gasFluid.addComponent("ethane", 0.1);

    StreamInterface gasStream = new Stream("test stream", gasFluid);
    gasStream.setPressure(50.0, "bara");
    gasStream.setTemperature(20.0, "C");
    gasStream.setFlowRate(12.0, "MSm3/day");
    gasStream.run();

    ProcessSystem exportCompressorSystem = getExportCopressorModel(gasStream);
    exportCompressorSystem.setRunInSteps(true);
    exportCompressorSystem.run();

    Assertions.assertEquals(12, ((Splitter) exportCompressorSystem.getUnit("TEE-104"))
        .getSplitStream(0).getFlowRate("MSm3/day"), 0.1);

    ((Splitter) exportCompressorSystem.getUnit("TEE-104")).getSplitStream(0).getFluid()
        .prettyPrint();
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

    ProcessSystem expanderProcessB =
        createExpanderProcessModel((Separator) separationTrainB.getUnit("dew point scrubber 2"),
            (ThreePhaseSeparator) separationTrainB.getUnit("4th stage separator"),
            (Mixer) separationTrainB.getUnit("second Stage mixer"),
            (Mixer) separationTrainB.getUnit("first stage mixer"),
            (Mixer) separationTrainB.getUnit("MP liq gas mixer"));


    ProcessSystem exportCompressorTrainA = getExportCopressorModel(
        ((ThrottlingValve) expanderProcessA.getUnit("gas split valve")).getOutletStream());

    ProcessSystem exportCompressorTrainB = getExportCopressorModel(
        ((ThrottlingValve) expanderProcessB.getUnit("gas split valve")).getOutletStream());

    ProcessModel combinedProcess = new ProcessModel();
    combinedProcess.add("well and manifold process", wellProcess);
    combinedProcess.add("separation train A", separationTrainA);
    combinedProcess.add("separation train B", separationTrainB);
    combinedProcess.add("expander process A", expanderProcessA);
    combinedProcess.add("expander process B", expanderProcessB);
    combinedProcess.add("compressor process A", exportCompressorTrainA);
    combinedProcess.add("compressor process B", exportCompressorTrainB);

    return combinedProcess;
  }


  @Test
  public void testCombinedProcess() {
    ProcessModel fullProcess = getCombinedModel();
    fullProcess.setRunStep(true);

    // Set fullProcess properties;
    ((Stream) (fullProcess.get("well and manifold process")).getUnit("HP well stream"))
        .setFlowRate(30.0, "MSm3/day");

    try {
      fullProcess.run();
      fullProcess.run();
      fullProcess.run();
      fullProcess.run();
      fullProcess.run();
      fullProcess.run();
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }

    Assertions.assertEquals(5.54017523150,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(1742523.539419,
        ((ThreePhaseSeparator) fullProcess.get("separation train A").getUnit("1st stage separator"))
            .getOilOutStream().getFlowRate("kg/hr"),
        0.1);

    Assertions.assertEquals(8.3102628472,
        ((ThreePhaseSeparator) fullProcess.get("separation train B").getUnit("1st stage separator"))
            .getGasOutStream().getFlowRate("MSm3/day"),
        0.1);


    Assertions.assertEquals(13.748552781153,
        ((ThrottlingValve) fullProcess.get("expander process A").getUnit("gas split valve"))
            .getOutletStream().getFlowRate("MSm3/day"),
        0.1);


    Assertions.assertEquals(18.6662224172093,
        ((ThrottlingValve) fullProcess.get("expander process B").getUnit("gas split valve"))
            .getOutletStream().getFlowRate("MSm3/day"),
        0.1);


    Assertions.assertEquals(13.7484527811,
        ((Splitter) fullProcess.get("compressor process A").getUnit("TEE-104")).getSplitStream(0)
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(308.6553992,
        ((Compressor) fullProcess.get("compressor process A").getUnit("KA27841")).getOutletStream()
            .getPressure("bara"),
        0.1);

    Assertions.assertEquals(18.6661224172,
        ((Splitter) fullProcess.get("compressor process B").getUnit("TEE-104")).getSplitStream(0)
            .getFlowRate("MSm3/day"),
        0.1);

    Assertions.assertEquals(214.71698355702,
        ((Compressor) fullProcess.get("compressor process B").getUnit("KA27841")).getOutletStream()
            .getPressure("bara"),
        0.1);



  }

}
