package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration style regression test for the Graphviz export of a complex process model.
 */
public class ProcessSystemGraphvizExportTest extends neqsim.NeqSimTest {

  @Test
  public void exportGraphvizForComplexOilProcess(@TempDir Path tempDir) throws IOException {
    ProcessSystem process = createExampleProcess();

    Path dotFile = tempDir.resolve("example-process.dot");
    process.exportToGraphviz(dotFile.toString());

    String dot = readString(dotFile);
    String expectedDot = readString(
        Paths.get("src", "test", "java", "neqsim", "process", "processmodel",
            "example-process.dot"));
    assertEquals(expectedDot.trim(), dot.trim(), "Graphviz export differs from expected fixture");

    assertTrue(dot.contains("\"well stream\""), "well stream node missing");
    assertTrue(dot.contains("\"20-HA-01\""), "first heater node missing");
    assertTrue(dot.contains("\"23-KA-01\""), "export compressor node missing");

    assertTrue(dot.contains("\"well stream\" -> \"20-HA-01\""),
        "connection from well stream to first heater missing");
    assertTrue(dot.contains("\"20-VA-01\" -> \"VLV-100\""),
        "connection from first separator oil stream to throttling valve missing");
    assertTrue(dot.contains("\"23-KA-03\" -> \"MIX-103\""),
        "recompressor to gas mixer connection missing");
    assertTrue(dot.contains("\"splitter\" -> \"25-HA-01\""),
        "splitter to heat exchanger connection missing");
    assertTrue(dot.contains("\"25-HA-01\" -> \"27-KA-01\""),
        "heat exchanger to export compressor connection missing");
  }

  @Test
  public void exportGraphvizForThreePhaseSeparatorProcess(@TempDir Path tempDir)
      throws IOException {
    ProcessSystem process = createThreePhaseSeparatorProcess();

    Path dotFile = tempDir.resolve("three-phase-separator-process.dot");
    process.exportToGraphviz(dotFile.toString());

    String dot = readString(dotFile);
    String expectedDot = readString(
        Paths.get("src", "test", "java", "neqsim", "process", "processmodel",
            "three-phase-separator-process.dot"));
    assertEquals(expectedDot.trim(), dot.trim(),
        "Graphviz export differs from expected three-phase fixture");

    assertTrue(dot.contains("\"1st stage separator\" -> \"water out 1st stage\""),
        "water draw-off from first stage separator missing");
    assertTrue(dot.contains("\"1st stage separator\" -> \"valve oil from first stage\""),
        "oil outlet from first stage separator missing");
    assertTrue(dot.contains("\"3RD stage separator\" -> \"gas mixer 0\""),
        "gas connection from third stage separator missing");
    assertTrue(dot.contains("\"VG-23-006\" -> \"recycle 1st stage liquid\""),
        "liquid recycle to third stage separator missing");
    assertTrue(dot.contains("\"23-KA-010\" -> \"gas mixer 2\""),
        "third stage compressor discharge missing");
  }

  @Test
  public void exportGraphvizForAntiSurgeProcess(@TempDir Path tempDir) throws IOException {
    ProcessSystem process = createAntiSurgeProcess();

    Path dotFile = tempDir.resolve("anti-surge-process.dot");
    process.exportToGraphviz(dotFile.toString());

    String dot = readString(dotFile);
    String expectedDot = readString(
        Paths.get("src", "test", "java", "neqsim", "process", "processmodel",
            "anti-surge-process.dot"));
    assertEquals(expectedDot.trim(), dot.trim(),
        "Graphviz export differs from expected anti-surge fixture");

    assertTrue(dot.contains("\"3RD stage separator\" -> \"gas mixer 0\""),
        "third stage separator gas connection missing");
    assertTrue(dot.contains("\"VG-23-002\" -> \"stream to closed drain\""),
        "scrubber liquid stream to closed drain missing");
    assertTrue(dot.contains("\"23-KA-004\" -> \"1st stage anti surge splitter\""),
        "compressor discharge to anti surge splitter missing");
    assertTrue(dot.contains("\"1st stage anti surge splitter\" -> \"anti surge valve\""),
        "anti surge splitter bypass path missing");
    assertTrue(dot.contains(
        "\"anti surge cooler\" -> \"recycle anti surge 1st stage compressor\""),
        "anti surge recycle loop closure missing");
  }

  private String readString(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  private ProcessSystem createExampleProcess() {
    ProcessSystem process = new ProcessSystem();
    Stream wellStream = new Stream("well stream", createWellFluid());
    wellStream.setTemperature(60.0, "C");
    wellStream.setPressure(33.01, "bara");

    Heater wellStreamCooler = new Heater("20-HA-01", wellStream);

    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("20-VA-01", wellStreamCooler.getOutStream());

    neqsim.process.equipment.valve.ThrottlingValve oilValve1 =
        new neqsim.process.equipment.valve.ThrottlingValve("VLV-100",
            firstStageSeparator.getOilOutStream());

    Mixer oilSecondStageMixer = new Mixer("MIX-101");
    oilSecondStageMixer.addStream(oilValve1.getOutStream());

    Heater oilHeaterFromFirstStage =
        new Heater("20-HA-02", oilSecondStageMixer.getOutStream());

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("20-VA-02", oilHeaterFromFirstStage.getOutStream());

    neqsim.process.equipment.valve.ThrottlingValve oilValve2 =
        new neqsim.process.equipment.valve.ThrottlingValve("VLV-102",
            secondStageSeparator.getOilOutStream());

    Stream oilReflux = (Stream) wellStream.clone();
    oilReflux.setName("third stage reflux");
    oilReflux.setFlowRate(1e-6, "kg/hr");

    Mixer thirdStageOilMixer = new Mixer("MIX-102");
    thirdStageOilMixer.addStream(oilValve2.getOutStream());
    thirdStageOilMixer.addStream(oilReflux);

    Heater oilHeaterFromSecondStage =
        new Heater("20-HA-03", thirdStageOilMixer.getOutletStream());

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("20-VA-03", oilHeaterFromSecondStage.getOutStream());

    Cooler firstStageCooler = new Cooler("23-HA-03", thirdStageSeparator.getGasOutStream());

    Separator firstStageScrubber = new Separator("23-VG-03", firstStageCooler.getOutStream());

    Pump firstStageScrubberPump = new Pump("23-PA-01", firstStageScrubber.getLiquidOutStream());

    Recycle lpRecycle = new Recycle("LP oil resycle");
    lpRecycle.addStream(firstStageScrubberPump.getOutStream());
    lpRecycle.setOutletStream(oilReflux);
    lpRecycle.setTolerance(1e-6);

    Compressor firstStageRecompressor =
        new Compressor("23-KA-03", firstStageScrubber.getGasOutStream());
    firstStageRecompressor.setIsentropicEfficiency(0.75);

    Mixer firstStageGasMixer = new Mixer("MIX-103");
    firstStageGasMixer.addStream(firstStageRecompressor.getOutStream());
    firstStageGasMixer.addStream(secondStageSeparator.getGasOutStream());

    Cooler secondStageCooler = new Cooler("23-HA-02", firstStageGasMixer.getOutStream());

    Separator secondStageScrubber = new Separator("23-VG-02", secondStageCooler.getOutStream());

    thirdStageOilMixer.addStream(secondStageScrubber.getLiquidOutStream());

    Compressor secondStageRecompressor =
        new Compressor("23-KA-02", secondStageScrubber.getGasOutStream());
    secondStageRecompressor.setIsentropicEfficiency(0.75);

    Mixer exportGasMixer = new Mixer("MIX-100");
    exportGasMixer.addStream(secondStageRecompressor.getOutStream());
    exportGasMixer.addStream(firstStageSeparator.getGasOutStream());

    Cooler dewPointCooler = new Cooler("23-HA-01", exportGasMixer.getOutStream());
    Separator dewPointScrubber = new Separator("23-VG-01", dewPointCooler.getOutStream());
    oilSecondStageMixer.addStream(dewPointScrubber.getLiquidOutStream());

    Compressor firstStageExportCompressor =
        new Compressor("23-KA-01", dewPointScrubber.getGasOutStream());
    firstStageExportCompressor.setIsentropicEfficiency(0.75);

    Cooler dewPointCooler2 = new Cooler("24-HA-01", firstStageExportCompressor.getOutStream());
    Separator dewPointScrubber2 = new Separator("24-VG-01", dewPointCooler2.getOutStream());
    oilSecondStageMixer.addStream(dewPointScrubber2.getLiquidOutStream());

    Splitter gasSplitter = new Splitter("splitter", dewPointScrubber2.getGasOutStream());
    gasSplitter.setSplitNumber(2);
    gasSplitter.setFlowRates(new double[] {-1.0, 2966.0}, "kg/hr");
    Stream fuelGas = (Stream) gasSplitter.getSplitStream(1);
    fuelGas.setName("fuel gas");

    HeatExchanger gasHeatExchanger = new HeatExchanger("25-HA-01",
        gasSplitter.getSplitStream(0));
    gasHeatExchanger.setGuessOutTemperature(273.15 + 15.0);
    gasHeatExchanger.setUAvalue(800e3);

    Cooler dewPointCooler3 = new Cooler("25-HA-02", gasHeatExchanger.getOutStream(0));
    Separator dewPointScrubber3 = new Separator("25-VG-01", dewPointCooler3.getOutStream());
    exportGasMixer.addStream(dewPointScrubber3.getLiquidOutStream());
    gasHeatExchanger.setFeedStream(1, dewPointScrubber3.getGasOutStream());

    Compressor secondStageExportCompressor =
        new Compressor("27-KA-01", gasHeatExchanger.getOutStream(1));
    secondStageExportCompressor.setIsentropicEfficiency(0.75);

    Cooler exportCompressorCooler =
        new Cooler("27-HA-01", secondStageExportCompressor.getOutStream());
    Stream exportGas = (Stream) exportCompressorCooler.getOutStream();
    exportGas.setName("export gas");

    Cooler exportOilCooler = new Cooler("21-HA-01", thirdStageSeparator.getOilOutStream());
    Pump exportOilPump = new Pump("21-PA-01", exportOilCooler.getOutStream());
    Stream exportOil = (Stream) exportOilPump.getOutStream();
    exportOil.setName("export oil");

    process.add(wellStream);
    process.add(wellStreamCooler);
    process.add(firstStageSeparator);
    process.add(oilValve1);
    process.add(oilSecondStageMixer);
    process.add(oilHeaterFromFirstStage);
    process.add(secondStageSeparator);
    process.add(oilValve2);
    process.add(oilReflux);
    process.add(thirdStageOilMixer);
    process.add(oilHeaterFromSecondStage);
    process.add(thirdStageSeparator);
    process.add(firstStageCooler);
    process.add(firstStageScrubber);
    process.add(firstStageScrubberPump);
    process.add(lpRecycle);
    process.add(firstStageRecompressor);
    process.add(firstStageGasMixer);
    process.add(secondStageCooler);
    process.add(secondStageScrubber);
    process.add(secondStageRecompressor);
    process.add(exportGasMixer);
    process.add(dewPointCooler);
    process.add(dewPointScrubber);
    process.add(firstStageExportCompressor);
    process.add(dewPointCooler2);
    process.add(dewPointScrubber2);
    process.add(gasSplitter);
    process.add(gasHeatExchanger);
    process.add(dewPointCooler3);
    process.add(dewPointScrubber3);
    process.add(secondStageExportCompressor);
    process.add(exportCompressorCooler);
    process.add(exportOilCooler);
    process.add(exportOilPump);
    process.add(exportGas);
    process.add(exportOil);
    process.add(fuelGas);

    return process;
  }

  private ProcessSystem createThreePhaseSeparatorProcess() {
    ProcessSystem process = new ProcessSystem();

    Stream osebergCFeed = createHydrocarbonStream("Oseberg C Feed");
    osebergCFeed.setTemperature(60.0, "C");
    osebergCFeed.setPressure(40.0, "bara");
    osebergCFeed.setFlowRate(250000.0, "kg/day");

    Stream osebergCWaterFeed = createWaterStream("Oseberg C Water Feed");
    osebergCWaterFeed.setTemperature(60.0, "C");
    osebergCWaterFeed.setPressure(40.0, "bara");
    osebergCWaterFeed.setFlowRate(2500.0, "kg/day");

    Mixer feedMixer = new Mixer("feed mixer");
    feedMixer.addStream(osebergCFeed);
    feedMixer.addStream(osebergCWaterFeed);

    Heater feedHeater = new Heater("feed heater");
    feedHeater.setInletStream(feedMixer.getOutStream());
    feedHeater.setOutTemperature(60.0, "C");

    ThreePhaseSeparator firstStageSeparator =
        new ThreePhaseSeparator("1st stage separator", feedHeater.getOutStream());

    Stream waterOut1stStage =
        new Stream("water out 1st stage", firstStageSeparator.getWaterOutStream());

    ThrottlingValve valveOilFromFirstStage =
        new ThrottlingValve("valve oil from first stage", firstStageSeparator.getOilOutStream());
    valveOilFromFirstStage.setOutletPressure(25.0, "bara");

    Heater oilHeaterFromFirstStage = new Heater("oil heater second stage",
        valveOilFromFirstStage.getOutStream());
    oilHeaterFromFirstStage.setOutTemperature(55.0, "C");

    Stream recycleLiqStream2 = createHydrocarbonStream("recycle 2 nd stage");
    recycleLiqStream2.setPressure(25.0, "bara");
    recycleLiqStream2.setTemperature(25.0, "C");
    recycleLiqStream2.setFlowRate(1.0, "kg/hr");

    ThreePhaseSeparator secondStageSeparator =
        new ThreePhaseSeparator("2nd stage separator", oilHeaterFromFirstStage.getOutStream());
    secondStageSeparator.addStream(recycleLiqStream2);

    Stream waterOut2ndStage =
        new Stream("water out 2nd stage", secondStageSeparator.getWaterOutStream());

    ThrottlingValve valveOilFromSecondStage =
        new ThrottlingValve("valve oil from second stage", secondStageSeparator.getOilOutStream());
    valveOilFromSecondStage.setOutletPressure(10.0, "bara");

    Stream recycleLiqStream3 = createHydrocarbonStream("recycle 3rd stage");
    recycleLiqStream3.setPressure(10.0, "bara");
    recycleLiqStream3.setTemperature(25.0, "C");
    recycleLiqStream3.setFlowRate(1.0, "kg/hr");

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3RD stage separator", valveOilFromSecondStage.getOutStream());
    thirdStageSeparator.addStream(recycleLiqStream3);

    Stream recycleGasStream1 = createHydrocarbonStream("recycle gas 1st stage");
    recycleGasStream1.setPressure(10.0, "bara");
    recycleGasStream1.setTemperature(25.0, "C");
    recycleGasStream1.setFlowRate(1.0, "kg/hr");

    Mixer gasMixer0 = new Mixer("gas mixer 0");
    gasMixer0.addStream(thirdStageSeparator.getGasOutStream());
    gasMixer0.addStream(recycleGasStream1);

    Separator firstStageScrubber0 = new Separator("VG-23-002", gasMixer0.getOutStream());

    Stream streamToClosedDrain =
        new Stream("stream to closed drain", firstStageScrubber0.getLiquidOutStream());

    Compressor firstStageCompressor =
        new Compressor("23-KA-004", firstStageScrubber0.getGasOutStream());
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.7);
    firstStageCompressor.setOutletPressure(25.0, "bara");

    Stream recycleGasStream2 = createHydrocarbonStream("recycle gas 2nd stage");
    recycleGasStream2.setPressure(25.0, "bara");
    recycleGasStream2.setTemperature(25.0, "C");
    recycleGasStream2.setFlowRate(1.0, "kg/hr");

    Mixer gasMixer22 = new Mixer("gas mixer 22");
    gasMixer22.addStream(firstStageCompressor.getOutStream());
    gasMixer22.addStream(recycleGasStream2);
    gasMixer22.addStream(secondStageSeparator.getGasOutStream());

    Cooler firstStageCooler = new Cooler("HA-23-005", gasMixer22.getOutStream());
    firstStageCooler.setOutTemperature(30.0, "C");

    Separator firstStageScrubber1 = new Separator("VG-23-006", firstStageCooler.getOutStream());

    Recycle recycle1stStageLiquid = new Recycle("recycle 1st stage liquid");
    recycle1stStageLiquid.addStream(firstStageScrubber1.getLiquidOutStream());
    recycle1stStageLiquid.setOutletStream(recycleLiqStream3);
    recycle1stStageLiquid.setTolerance(1e-2);

    Compressor secondStageCompressor =
        new Compressor("23-KA-007", firstStageScrubber1.getGasOutStream());
    secondStageCompressor.setUsePolytropicCalc(true);
    secondStageCompressor.setPolytropicEfficiency(0.7);
    secondStageCompressor.setOutletPressure(20.0, "bara");

    Stream recycleGasStream12 = createHydrocarbonStream("recycle gas 2nd stage 2");
    recycleGasStream12.setPressure(20.0, "bara");
    recycleGasStream12.setTemperature(25.0, "C");
    recycleGasStream12.setFlowRate(1.0, "kg/hr");

    Mixer gasMixer1 = new Mixer("gas mixer 1");
    gasMixer1.addStream(secondStageCompressor.getOutStream());
    gasMixer1.addStream(recycleGasStream12);

    Cooler secondStageCooler = new Cooler("23-HA-006", gasMixer1.getOutStream());
    secondStageCooler.setOutTemperature(25.0, "C");

    Separator secondStageScrubber1 =
        new Separator("23-VG-009", secondStageCooler.getOutStream());

    Recycle recycle2ndStageLiquid = new Recycle("recycle 2nd stage liquid");
    recycle2ndStageLiquid.addStream(secondStageScrubber1.getLiquidOutStream());
    recycle2ndStageLiquid.setOutletStream(recycleLiqStream2);
    recycle2ndStageLiquid.setTolerance(1e-2);

    Compressor thirdStageCompressor =
        new Compressor("23-KA-010", secondStageScrubber1.getGasOutStream());
    thirdStageCompressor.setUsePolytropicCalc(true);
    thirdStageCompressor.setPolytropicEfficiency(0.7);
    thirdStageCompressor.setOutletPressure(40.0, "bara");

    Mixer gasMixer2 = new Mixer("gas mixer 2");
    gasMixer2.addStream(thirdStageCompressor.getOutStream());
    gasMixer2.addStream(firstStageSeparator.getGasOutStream());

    Stream gasToInjectionManifold =
        new Stream("gas to injection manifold", gasMixer2.getOutStream());

    Manifold gasManifold = new Manifold("gas manifold");
    gasManifold.addStream(gasToInjectionManifold);
    gasManifold.setSplitFactors(new double[] {0.3, 0.3, 0.4});

    process.add(osebergCFeed);
    process.add(osebergCWaterFeed);
    process.add(feedMixer);
    process.add(feedHeater);
    process.add(firstStageSeparator);
    process.add(waterOut1stStage);
    process.add(valveOilFromFirstStage);
    process.add(oilHeaterFromFirstStage);
    process.add(recycleLiqStream2);
    process.add(secondStageSeparator);
    process.add(waterOut2ndStage);
    process.add(valveOilFromSecondStage);
    process.add(recycleLiqStream3);
    process.add(thirdStageSeparator);
    process.add(recycleGasStream1);
    process.add(gasMixer0);
    process.add(firstStageScrubber0);
    process.add(streamToClosedDrain);
    process.add(firstStageCompressor);
    process.add(recycleGasStream2);
    process.add(gasMixer22);
    process.add(firstStageCooler);
    process.add(firstStageScrubber1);
    process.add(recycle1stStageLiquid);
    process.add(secondStageCompressor);
    process.add(recycleGasStream12);
    process.add(gasMixer1);
    process.add(secondStageCooler);
    process.add(secondStageScrubber1);
    process.add(recycle2ndStageLiquid);
    process.add(thirdStageCompressor);
    process.add(gasMixer2);
    process.add(gasToInjectionManifold);
    process.add(gasManifold);

    return process;
  }

  private ProcessSystem createAntiSurgeProcess() {
    ProcessSystem process = new ProcessSystem();

    Stream oilFeed = createHydrocarbonStream("oil from second stage");
    oilFeed.setPressure(10.0, "bara");
    oilFeed.setTemperature(45.0, "C");

    Stream recycleLiqStream3 = createHydrocarbonStream("recycle 3rd stage");
    recycleLiqStream3.setPressure(10.0, "bara");
    recycleLiqStream3.setTemperature(25.0, "C");
    recycleLiqStream3.setFlowRate(1.0, "kg/hr");

    ThreePhaseSeparator thirdStageSeparator =
        new ThreePhaseSeparator("3RD stage separator", oilFeed);
    thirdStageSeparator.addStream(recycleLiqStream3);

    Stream recycleGasStream1 = createHydrocarbonStream("recycle gas 1st stage");
    recycleGasStream1.setPressure(10.0, "bara");
    recycleGasStream1.setTemperature(25.0, "C");
    recycleGasStream1.setFlowRate(1.0, "kg/hr");

    Mixer gasMixer0 = new Mixer("gas mixer 0");
    gasMixer0.addStream(thirdStageSeparator.getGasOutStream());
    gasMixer0.addStream(recycleGasStream1);

    Separator firstStageScrubber0 = new Separator("VG-23-002", gasMixer0.getOutStream());

    Stream streamToClosedDrain =
        new Stream("stream to closed drain", firstStageScrubber0.getLiquidOutStream());

    Compressor firstStageCompressor =
        new Compressor("23-KA-004", firstStageScrubber0.getGasOutStream());
    firstStageCompressor.setCompressorChartType("interpolate and extrapolate");
    firstStageCompressor.setUsePolytropicCalc(true);
    firstStageCompressor.setPolytropicEfficiency(0.7);
    firstStageCompressor.setOutletPressure(25.0, "bara");

    firstStageCompressor.getCompressorChart().getSurgeCurve()
        .setCurve(new double[] {0.0, 0.5, 1.0}, new double[] {0.0, 0.5, 1.0},
            new double[] {0.0, 0.5, 1.0});

    Splitter gasSplitterAntiSurge = new Splitter("1st stage anti surge splitter");
    gasSplitterAntiSurge.setInletStream(firstStageCompressor.getOutletStream());
    gasSplitterAntiSurge.setFlowRates(new double[] {-1.0, 1.0}, "kg/hr");

    Calculator antiSurgeCalculator = new Calculator("anti surge calculator_1");
    antiSurgeCalculator.addInputVariable(firstStageCompressor);
    antiSurgeCalculator.setOutputVariable(gasSplitterAntiSurge);

    ThrottlingValve antiSurgeValve =
        new ThrottlingValve("anti surge valve", gasSplitterAntiSurge.getSplitStream(1));
    antiSurgeValve.setOutletPressure(10.0, "bara");

    Cooler antiSurgeCooler = new Cooler("anti surge cooler", antiSurgeValve.getOutletStream());
    antiSurgeCooler.setOutTemperature(30.0, "C");

    Recycle antiSurgeRecycle = new Recycle("recycle anti surge 1st stage compressor");
    antiSurgeRecycle.addStream(antiSurgeCooler.getOutletStream());
    antiSurgeRecycle.setOutletStream(recycleGasStream1);
    antiSurgeRecycle.setTolerance(0.01);

    process.add(oilFeed);
    process.add(recycleLiqStream3);
    process.add(thirdStageSeparator);
    process.add(recycleGasStream1);
    process.add(gasMixer0);
    process.add(firstStageScrubber0);
    process.add(streamToClosedDrain);
    process.add(firstStageCompressor);
    process.add(gasSplitterAntiSurge);
    process.add(antiSurgeCalculator);
    process.add(antiSurgeValve);
    process.add(antiSurgeCooler);
    process.add(antiSurgeRecycle);

    return process;
  }

  private SystemInterface createWellFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("nitrogen", 0.5);
    fluid.addComponent("CO2", 1.0);
    fluid.addComponent("methane", 85.0);
    fluid.addComponent("ethane", 5.0);
    fluid.addComponent("propane", 4.0);
    fluid.addComponent("i-butane", 1.5);
    fluid.addComponent("n-butane", 1.0);
    fluid.addComponent("i-pentane", 0.8);
    fluid.addComponent("n-pentane", 0.7);
    fluid.addComponent("n-hexane", 0.5);
    fluid.addComponent("n-heptane", 0.4);
    fluid.addComponent("water", 0.5);
    fluid.createDatabase(true);
    fluid.setMixingRule(2);
    fluid.setMolarComposition(new double[] {0.005, 0.01, 0.85, 0.05, 0.04, 0.015, 0.01, 0.008, 0.007,
        0.005, 0.004, 0.011});
    return fluid;
  }

  private Stream createHydrocarbonStream(String name) {
    SystemInterface fluid = (SystemInterface) createWellFluid().clone();
    return new Stream(name, fluid);
  }

  private Stream createWaterStream(String name) {
    SystemInterface water = new SystemSrkEos(298.15, 5.0);
    water.addComponent("water", 1.0);
    return new Stream(name, water);
  }
}
