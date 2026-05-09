package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorChartInterface;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for JsonProcessExporter — exporting ProcessSystem to JSON and round-tripping via
 * JsonProcessBuilder.
 *
 * @author Even Solbraa
 */
class JsonProcessExporterTest {

  /**
   * Creates a simple test process with a feed, separator, compressor and valve.
   *
   * @return a built ProcessSystem
   */
  private ProcessSystem createSimpleProcess() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.addComponent("n-pentane", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(60.0, "bara");

    Separator hpSep = new Separator("HP Sep", feed);
    Compressor comp = new Compressor("Comp", hpSep.getGasOutStream());
    comp.setOutletPressure(120.0);
    comp.setSpeed(10250.0);
    ThrottlingValve valve = new ThrottlingValve("Valve", hpSep.getLiquidOutStream());
    valve.setOutletPressure(10.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(hpSep);
    process.add(comp);
    process.add(valve);
    process.run();

    return process;
  }

  @Test
  void testExportProducesValidJson() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();

    assertNotNull(json, "toJson() should not return null");
    assertTrue(json.length() > 50, "JSON should have substantial content");

    // Parse to verify it's valid JSON
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("fluid"), "JSON should have 'fluid' section");
    assertTrue(root.has("process"), "JSON should have 'process' section");
  }

  @Test
  void testExportFluidSection() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject fluid = root.getAsJsonObject("fluid");

    assertTrue(fluid.has("model"), "Fluid should have model");
    assertTrue(fluid.has("components"), "Fluid should have components");
    assertTrue(fluid.has("mixingRule"), "Fluid should have mixingRule");

    String model = fluid.get("model").getAsString();
    assertTrue("SRK".equals(model), "Model should be SRK, got: " + model);

    JsonObject components = fluid.getAsJsonObject("components");
    assertTrue(components.has("methane"), "Components should include methane");
    assertTrue(components.has("ethane"), "Components should include ethane");
  }

  @Test
  void testExportProcessUnits() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    int processSize = root.getAsJsonArray("process").size();
    assertTrue(processSize == 4,
        "Should have 4 units (feed + sep + comp + valve), got: " + processSize);

    // Check types in order
    String type0 =
        root.getAsJsonArray("process").get(0).getAsJsonObject().get("type").getAsString();
    assertTrue("Stream".equals(type0), "First unit should be Stream, got: " + type0);

    String type1 =
        root.getAsJsonArray("process").get(1).getAsJsonObject().get("type").getAsString();
    assertTrue("Separator".equals(type1), "Second unit should be Separator, got: " + type1);
  }

  @Test
  void testExportStreamWiring() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    // Separator should reference "feed" as inlet
    JsonObject sepJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue(sepJson.has("inlet"), "Separator should have inlet reference");
    String sepInlet = sepJson.get("inlet").getAsString();
    assertTrue("feed".equals(sepInlet), "Separator inlet should be 'feed', got: " + sepInlet);

    // Compressor should reference "HP Sep.gasOut"
    JsonObject compJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertTrue(compJson.has("inlet"), "Compressor should have inlet reference");
    String compInlet = compJson.get("inlet").getAsString();
    assertTrue("HP Sep.gasOut".equals(compInlet),
        "Compressor inlet should be 'HP Sep.gasOut', got: " + compInlet);

    // Valve should reference "HP Sep.liquidOut"
    JsonObject valveJson = root.getAsJsonArray("process").get(3).getAsJsonObject();
    String valveInlet = valveJson.get("inlet").getAsString();
    assertTrue("HP Sep.liquidOut".equals(valveInlet),
        "Valve inlet should be 'HP Sep.liquidOut', got: " + valveInlet);
  }

  @Test
  void testExportCompressorProperties() {
    ProcessSystem process = createSimpleProcess();
    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject compJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertTrue(compJson.has("properties"), "Compressor should have properties");
    JsonObject props = compJson.getAsJsonObject("properties");
    assertTrue(props.has("outletPressure"), "Compressor properties should have outletPressure");
    double outP = props.get("outletPressure").getAsDouble();
    assertTrue(Math.abs(outP - 120.0) < 0.1,
        "Compressor outlet pressure should be ~120 bara, got: " + outP);
    assertEquals(10250.0, props.get("speed").getAsDouble(), 1.0e-12);
  }

  @Test
  void testCompressorChartAndAntiSurgeCalculatorRoundTrip() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Compressor compressor = new Compressor("chart compressor", feed);
    compressor.setCompressorChartType("interpolate and extrapolate");
    compressor.setUsePolytropicCalc(true);
    compressor.setOutletPressure(80.0, "bara");
    compressor.setSpeed(9000.0);
    compressor.setMinimumSpeed(7000.0);
    compressor.setMaximumSpeed(11000.0);
    CompressorChartInterface chart = compressor.getCompressorChart();
    chart.setHeadUnit("kJ/kg");
    chart.setUseRealKappa(true);
    chart.setCurves(new double[] {18.0, 288.15, 45.0, 0.9}, new double[] {8000.0, 10000.0},
        new double[][] {{800.0, 1000.0, 1200.0}, {900.0, 1100.0, 1300.0}},
        new double[][] {{90.0, 85.0, 75.0}, {110.0, 103.0, 92.0}},
        new double[][] {{72.0, 78.0, 74.0}, {73.0, 79.0, 75.0}});
    chart.getSurgeCurve().setCurve(new double[] {18.0, 288.15, 45.0, 0.9},
        new double[] {780.0, 830.0, 880.0}, new double[] {92.0, 101.0, 112.0});
    chart.getStoneWallCurve().setCurve(new double[] {18.0, 288.15, 45.0, 0.9},
        new double[] {1220.0, 1270.0, 1320.0}, new double[] {74.0, 82.0, 91.0});
    compressor.getAntiSurge().setActive(true);
    compressor.getAntiSurge()
        .setControlStrategy(neqsim.process.equipment.compressor.AntiSurge.ControlStrategy.PID);
    compressor.getAntiSurge().setSurgeControlFactor(1.12);

    Splitter antiSurgeSplitter = new Splitter("anti surge splitter", compressor.getOutStream(), 2);
    antiSurgeSplitter.setFlowRates(new double[] {-1.0, 1.0}, "kg/hr");

    Calculator antiSurgeCalculator = new Calculator("anti surge calculator test");
    antiSurgeCalculator.addInputVariable(compressor);
    antiSurgeCalculator.setOutputVariable(antiSurgeSplitter);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.add(antiSurgeSplitter);
    process.add(antiSurgeCalculator);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject compJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    JsonObject compProps = compJson.getAsJsonObject("properties");
    JsonObject chartJson = compProps.getAsJsonObject("compressorChart");
    assertEquals("interpolate and extrapolate", chartJson.get("chartType").getAsString());
    assertEquals("kJ/kg", chartJson.get("headUnit").getAsString());
    assertTrue(chartJson.getAsJsonArray("speeds").size() == 2);
    assertTrue(chartJson.getAsJsonObject("surgeCurve").get("active").getAsBoolean());
    assertTrue(chartJson.getAsJsonObject("stoneWallCurve").get("active").getAsBoolean());

    JsonObject calcJson = root.getAsJsonArray("process").get(3).getAsJsonObject();
    JsonObject calcProps = calcJson.getAsJsonObject("properties");
    assertEquals("chart compressor",
        calcProps.getAsJsonArray("calculatorInputs").get(0).getAsString());
    assertEquals("anti surge splitter", calcProps.get("calculatorOutput").getAsString());

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Compressor chart process should rebuild: " + result.toJson());
    Compressor rebuiltCompressor =
        (Compressor) result.getProcessSystem().getUnit("chart compressor");
    assertEquals(9000.0, rebuiltCompressor.getSpeed(), 1.0e-12);
    assertEquals("interpolate and extrapolate", rebuiltCompressor.getCompressorChartType());
    assertEquals("kJ/kg", rebuiltCompressor.getCompressorChart().getHeadUnit());
    assertTrue(rebuiltCompressor.getCompressorChart().getSurgeCurve().isActive());
    assertTrue(rebuiltCompressor.getCompressorChart().getStoneWallCurve().isActive());
    assertTrue(rebuiltCompressor.getAntiSurge().isActive());
    Calculator rebuiltCalculator =
        (Calculator) result.getProcessSystem().getUnit("anti surge calculator test");
    assertEquals(1, rebuiltCalculator.getInputVariable().size());
    assertEquals("anti surge splitter", rebuiltCalculator.getOutputVariable().getName());
  }

  @Test
  void testTwoPointCompressorBoundaryCurvesImport() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(50.0, "bara");

    Compressor compressor = new Compressor("two point boundary compressor", feed);
    compressor.setOutletPressure(75.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(compressor);
    process.run();

    JsonObject root = JsonParser.parseString(process.toJson()).getAsJsonObject();
    JsonObject compressorProps =
        root.getAsJsonArray("process").get(1).getAsJsonObject().getAsJsonObject("properties");

    JsonObject chartJson = new JsonObject();
    chartJson.addProperty("chartType", "interpolate and extrapolate");

    JsonObject surgeCurve = new JsonObject();
    surgeCurve.addProperty("active", true);
    JsonArray surgeFlow = new JsonArray();
    surgeFlow.add(800.0);
    surgeFlow.add(900.0);
    JsonArray surgeHead = new JsonArray();
    surgeHead.add(90.0);
    surgeHead.add(110.0);
    surgeCurve.add("flow", surgeFlow);
    surgeCurve.add("head", surgeHead);
    chartJson.add("surgeCurve", surgeCurve);

    JsonObject stoneWallCurve = new JsonObject();
    stoneWallCurve.addProperty("active", true);
    JsonArray stoneWallFlow = new JsonArray();
    stoneWallFlow.add(1200.0);
    stoneWallFlow.add(1300.0);
    JsonArray stoneWallHead = new JsonArray();
    stoneWallHead.add(75.0);
    stoneWallHead.add(90.0);
    stoneWallCurve.add("flow", stoneWallFlow);
    stoneWallCurve.add("head", stoneWallHead);
    chartJson.add("stoneWallCurve", stoneWallCurve);

    compressorProps.add("compressorChart", chartJson);

    SimulationResult result = ProcessSystem.fromJsonAndRun(root.toString());
    assertTrue(result.isSuccess(),
        "Two-point compressor boundary curves should import: " + result.toJson());
    Compressor rebuilt =
        (Compressor) result.getProcessSystem().getUnit("two point boundary compressor");
    assertTrue(rebuilt.getCompressorChart().getSurgeCurve().isActive());
    assertTrue(rebuilt.getCompressorChart().getStoneWallCurve().isActive());
    assertEquals(3, rebuilt.getCompressorChart().getSurgeCurve().getFlow().length);
    assertEquals(3, rebuilt.getCompressorChart().getStoneWallCurve().getFlow().length);
  }

  @Test
  void testRoundTripViaJsonProcessBuilder() {
    ProcessSystem original = createSimpleProcess();
    String json = original.toJson();

    // Rebuild from JSON
    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Round-trip build should succeed: " + result);

    ProcessSystem rebuilt = result.getProcessSystem();
    assertNotNull(rebuilt, "Rebuilt process should not be null");
    assertNotNull(rebuilt.getUnit("feed"), "Rebuilt should have 'feed'");
    assertNotNull(rebuilt.getUnit("HP Sep"), "Rebuilt should have 'HP Sep'");
    assertNotNull(rebuilt.getUnit("Comp"), "Rebuilt should have 'Comp'");
    assertNotNull(rebuilt.getUnit("Valve"), "Rebuilt should have 'Valve'");
  }

  @Test
  void testExportWithSplitter() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Splitter splitter = new Splitter("TEE-1", feed, 2);
    splitter.setSplitFactors(new double[] {0.6, 0.4});

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(splitter);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject splitterJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue("Splitter".equals(splitterJson.get("type").getAsString()));
    assertTrue(splitterJson.has("properties"), "Splitter should have properties");
    assertTrue(splitterJson.getAsJsonObject("properties").has("splitFactors"),
        "Splitter properties should have splitFactors");
  }

  @Test
  void testStreamRoundTripPreservesIndividualFluidComposition() {
    SystemInterface methaneFluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    methaneFluid.addComponent("methane", 1.0);
    methaneFluid.setMixingRule("classic");

    SystemInterface nitrogenFluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    nitrogenFluid.addComponent("nitrogen", 1.0);
    nitrogenFluid.setMixingRule("classic");

    Stream methaneFeed = new Stream("methane feed", methaneFluid);
    methaneFeed.setFlowRate(1000.0, "kg/hr");

    Stream nitrogenRecycle = new Stream("nitrogen recycle", nitrogenFluid);
    nitrogenRecycle.setFlowRate(10.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(methaneFeed);
    process.add(nitrogenRecycle);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject secondStream = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue(secondStream.has("fluidRef"), "Stream should reference its own named fluid");

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Stream process should rebuild: " + result.toJson());

    Stream rebuiltNitrogen = (Stream) result.getProcessSystem().getUnit("nitrogen recycle");
    assertEquals(1.0, rebuiltNitrogen.getFluid().getComponent("nitrogen").getz(), 1.0e-12);
  }

  @Test
  void testStreamFluidRoundTripPreservesVolumeCorrectionAndComponentProperties() {
    SystemInterface oilFluid = new SystemPrEos(273.15 + 31.0, 7.55);
    oilFluid.addComponent("methane", 0.35);
    oilFluid.addTBPfraction("C7", 0.65, 0.200, 0.84);
    oilFluid.setMixingRule("classic");
    oilFluid.useVolumeCorrection(true);

    oilFluid.getPhase(0).getComponent("methane").setVolumeCorrectionConst(0.0123);
    ((ComponentEos) oilFluid.getPhase(0).getComponent("methane")).setOmegaA(0.45724);
    oilFluid.getPhase(0).getComponent("C7_PC").setVolumeCorrectionConst(0.0456);
    oilFluid.getPhase(0).getComponent("C7_PC").setCriticalVolume(0.8123);
    oilFluid.getPhase(0).getComponent("C7_PC").setParachorParameter(444.4);

    Stream feed = new Stream("oil feed", oilFluid);
    feed.setFlowRate(1000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject fluid = root.getAsJsonObject("fluid");

    assertTrue(fluid.get("useVolumeCorrection").getAsBoolean(),
        "Fluid JSON should preserve volume-correction state");
    assertTrue(fluid.has("componentProperties"),
        "Fluid JSON should include full component properties");

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Process should rebuild: " + result.toJson());

    Stream rebuiltFeed = (Stream) result.getProcessSystem().getUnit("oil feed");
    SystemInterface rebuiltFluid = rebuiltFeed.getFluid();
    assertTrue(rebuiltFluid.getPhase(0).useVolumeCorrection(),
        "Rebuilt fluid should preserve volume-correction state");
    assertEquals(0.35, rebuiltFluid.getComponent("methane").getz(), 1.0e-12);
    assertEquals(0.65, rebuiltFluid.getPhase(0).getComponent("C7_PC").getz(), 1.0e-12);
    assertEquals(0.0123,
        rebuiltFluid.getPhase(0).getComponent("methane").getVolumeCorrectionConst(), 1.0e-12);
    assertEquals(0.45724,
        ((ComponentEos) rebuiltFluid.getPhase(0).getComponent("methane")).getOmegaAOverride(),
        1.0e-12);
    assertEquals(0.0456, rebuiltFluid.getPhase(0).getComponent("C7_PC").getVolumeCorrectionConst(),
        1.0e-12);
    assertEquals(0.8123, rebuiltFluid.getPhase(0).getComponent("C7_PC").getCriticalVolume(),
        1.0e-12);
    assertEquals(444.4, rebuiltFluid.getPhase(0).getComponent("C7_PC").getParachorParameter(),
        1.0e-12);
  }

  @Test
  void testFluidRoundTripPreservesBinaryInteractionParameters() {
    SystemInterface fluid = new SystemPrEos(273.15 + 31.0, 7.55);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("ethane", 0.25);
    fluid.addComponent("propane", 0.15);
    fluid.setMixingRule("classic");
    fluid.setBinaryInteractionParameter("methane", "ethane", 0.031);
    fluid.setBinaryInteractionParameter("methane", "propane", -0.012);

    Stream feed = new Stream("gas feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonArray bics = root.getAsJsonObject("fluid").getAsJsonArray("binaryInteractionParameters");
    assertNotNull(bics, "Fluid JSON should include non-zero binary interaction parameters");
    assertTrue(bics.size() >= 2, "Fluid JSON should include the configured BIP values");

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Process should rebuild: " + result.toJson());

    Stream rebuiltFeed = (Stream) result.getProcessSystem().getUnit("gas feed");
    EosMixingRulesInterface rebuiltMixingRule =
        (EosMixingRulesInterface) rebuiltFeed.getFluid().getPhase(0).getMixingRule();
    int methaneIndex = rebuiltFeed.getFluid().getComponent("methane").getComponentNumber();
    int ethaneIndex = rebuiltFeed.getFluid().getComponent("ethane").getComponentNumber();
    int propaneIndex = rebuiltFeed.getFluid().getComponent("propane").getComponentNumber();
    assertEquals(0.031, rebuiltMixingRule.getBinaryInteractionParameter(methaneIndex, ethaneIndex),
        1.0e-12);
    assertEquals(-0.012,
        rebuiltMixingRule.getBinaryInteractionParameter(methaneIndex, propaneIndex), 1.0e-12);
  }

  @Test
  void testSplitterRoundTripPreservesFlowRates() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Splitter splitter = new Splitter("fixed-rate splitter", feed, 2);
    splitter.setFlowRates(new double[] {1000.0, -1.0}, "kg/hr");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(splitter);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject splitterJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    JsonObject props = splitterJson.getAsJsonObject("properties");
    assertTrue(props.has("flowRates"), "Splitter properties should have flowRates");
    assertEquals("kg/hr", props.get("flowUnit").getAsString());

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Splitter process should rebuild: " + result.toJson());

    Splitter rebuilt = (Splitter) result.getProcessSystem().getUnit("fixed-rate splitter");
    assertEquals(1000.0, rebuilt.getSplitStream(0).getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(9000.0, rebuilt.getSplitStream(1).getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(-1.0, rebuilt.getFlowRates()[1], 1.0e-12);
    assertEquals("kg/hr", rebuilt.getFlowUnit());
  }

  @Test
  void testRecycleRoundTripPreservesConvergenceSettings() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Recycle recycle = new Recycle("test recycle");
    recycle.addStream(feed);
    recycle.setFlowTolerance(0.11);
    recycle.setCompositionTolerance(0.22);
    recycle.setTemperatureTolerance(0.33);
    recycle.setPressureTolerance(0.44);
    recycle.setPriority(12);
    recycle.setMaxIterations(24);
    recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
    recycle.setWegsteinQMin(-3.0);
    recycle.setWegsteinQMax(0.25);
    recycle.setWegsteinDelayIterations(4);
    recycle.setDownstreamProperty("flow rate");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(recycle);

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject props =
        root.getAsJsonArray("process").get(1).getAsJsonObject().getAsJsonObject("properties");
    assertEquals(0.11, props.get("flowTolerance").getAsDouble(), 1.0e-12);
    assertEquals(0.44, props.get("pressureTolerance").getAsDouble(), 1.0e-12);
    assertEquals("WEGSTEIN", props.get("accelerationMethod").getAsString());
    assertEquals("flow rate", props.getAsJsonArray("downstreamProperty").get(0).getAsString());

    SimulationResult result = new JsonProcessBuilder().build(json);
    assertTrue(result.isSuccess(), "Recycle process should rebuild: " + result.toJson());

    Recycle rebuilt = (Recycle) result.getProcessSystem().getUnit("test recycle");
    assertEquals(0.11, rebuilt.getFlowTolerance(), 1.0e-12);
    assertEquals(0.22, rebuilt.getCompositionTolerance(), 1.0e-12);
    assertEquals(0.33, rebuilt.getTemperatureTolerance(), 1.0e-12);
    assertEquals(0.44, rebuilt.getPressureTolerance(), 1.0e-12);
    assertEquals(12, rebuilt.getPriority());
    assertEquals(24, rebuilt.getMaxIterations());
    assertEquals(AccelerationMethod.WEGSTEIN, rebuilt.getAccelerationMethod());
    assertEquals(-3.0, rebuilt.getWegsteinQMin(), 1.0e-12);
    assertEquals(0.25, rebuilt.getWegsteinQMax(), 1.0e-12);
    assertEquals(4, rebuilt.getWegsteinDelayIterations());
    assertEquals("flow rate", rebuilt.getDownstreamProperty().get(0));
  }

  @Test
  void testBeggsAndBrillPipeRoundTripPreservesGeometry() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", feed);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(50.0e-6);
    pipe.setLength(100.0);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(5);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pipe);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject pipeJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    JsonObject props = pipeJson.getAsJsonObject("properties");
    assertEquals("PipeBeggsAndBrills", pipeJson.get("type").getAsString());
    assertEquals(0.5, props.get("diameter").getAsDouble(), 1.0e-12);
    assertEquals(100.0, props.get("length").getAsDouble(), 1.0e-12);
    assertEquals(50.0e-6, props.get("pipeWallRoughness").getAsDouble(), 1.0e-12);
    assertEquals(5, props.get("numberOfIncrements").getAsInt());

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Pipe process should rebuild: " + result.toJson());

    PipeBeggsAndBrills rebuilt =
        (PipeBeggsAndBrills) result.getProcessSystem().getUnit("test pipe");
    assertEquals(0.5, rebuilt.getDiameter(), 1.0e-12);
    assertEquals(100.0, rebuilt.getLength(), 1.0e-12);
    assertEquals(50.0e-6, rebuilt.getPipeWallRoughness(), 1.0e-12);
    assertTrue(rebuilt.getOutStream().getFlowRate("kg/hr") > 0.0);
  }

  @Test
  void testExportWithCooler() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 80.0, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("hot-gas", fluid);
    feed.setFlowRate(10000.0, "kg/hr");

    Cooler cooler = new Cooler("AC-1", feed);
    cooler.setOutletTemperature(273.15 + 30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();

    JsonObject coolerJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    assertTrue("Cooler".equals(coolerJson.get("type").getAsString()));
    assertTrue(coolerJson.has("properties"), "Cooler should have properties");
  }

  @Test
  void testHeaterRoundTripPreservesOutletPressureSpecification() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 30.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Heater heater = new Heater("pressure setter", feed);
    heater.setOutTemperature(20.0, "C");
    heater.setOutletPressure(5.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject heaterJson = root.getAsJsonArray("process").get(1).getAsJsonObject();
    JsonObject props = heaterJson.getAsJsonObject("properties");
    assertTrue(props.has("outletPressure"), "Heater should export outletPressure");

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Heater process should rebuild: " + result.toJson());

    Heater rebuilt = (Heater) result.getProcessSystem().getUnit("pressure setter");
    assertEquals(5.0, rebuilt.getOutStream().getPressure("bara"), 1.0e-6);
  }

  @Test
  void testProcessModelRoundTrip() {
    // Build area 1 — separation
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid1.addComponent("methane", 0.85);
    fluid1.addComponent("ethane", 0.10);
    fluid1.addComponent("propane", 0.05);
    fluid1.setMixingRule("classic");

    Stream feed1 = new Stream("feed", fluid1);
    feed1.setFlowRate(40000.0, "kg/hr");
    Separator sep = new Separator("HP Sep", feed1);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed1);
    separation.add(sep);
    separation.run();

    // Build area 2 — compression
    SystemInterface fluid2 = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid2.addComponent("methane", 0.90);
    fluid2.addComponent("ethane", 0.10);
    fluid2.setMixingRule("classic");

    Stream feed2 = new Stream("comp-feed", fluid2);
    feed2.setFlowRate(30000.0, "kg/hr");
    Compressor comp = new Compressor("Comp-1", feed2);
    comp.setOutletPressure(120.0);

    ProcessSystem compression = new ProcessSystem();
    compression.add(feed2);
    compression.add(comp);
    compression.run();

    // Assemble ProcessModel
    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("compression", compression);
    model.setRunStep(true);
    model.setMaxIterations(7);
    model.setFlowTolerance(2.0e-3);
    model.setTemperatureTolerance(3.0e-3);
    model.setPressureTolerance(4.0e-3);

    // Export
    String json = model.toJson();
    assertNotNull(json);
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("areas"));
    assertTrue(root.get("runStep").getAsBoolean());
    assertEquals(7, root.get("maxIterations").getAsInt());
    assertEquals(2.0e-3, root.get("flowTolerance").getAsDouble(), 1.0e-12);
    assertEquals(3.0e-3, root.get("temperatureTolerance").getAsDouble(), 1.0e-12);
    assertEquals(4.0e-3, root.get("pressureTolerance").getAsDouble(), 1.0e-12);
    assertTrue(root.getAsJsonObject("areas").has("separation"));
    assertTrue(root.getAsJsonObject("areas").has("compression"));

    // Round-trip
    ProcessModel rebuilt = ProcessModel.fromJson(json);
    assertNotNull(rebuilt);
    assertTrue(rebuilt.isRunStep(), "ProcessModel runStep should survive JSON round-trip");
    assertEquals(7, rebuilt.getMaxIterations());
    assertEquals(2.0e-3, rebuilt.getFlowTolerance(), 1.0e-12);
    assertEquals(3.0e-3, rebuilt.getTemperatureTolerance(), 1.0e-12);
    assertEquals(4.0e-3, rebuilt.getPressureTolerance(), 1.0e-12);
    assertNotNull(rebuilt.get("separation"), "separation area should exist after round-trip");
    assertNotNull(rebuilt.get("compression"), "compression area should exist after round-trip");

    // Verify unit counts match
    assertTrue(rebuilt.get("separation").getUnitOperations().size() >= 2,
        "separation area should have at least feed + separator");
    assertTrue(rebuilt.get("compression").getUnitOperations().size() >= 2,
        "compression area should have at least feed + compressor");
  }

  @Test
  void testProcessModelExportMaterializesExternalAreaInlet() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(40000.0, "kg/hr");
    Separator separator = new Separator("HP Sep", feed);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed);
    separation.add(separator);
    separation.run();

    Cooler downstreamCooler = new Cooler("Downstream Cooler", separator.getGasOutStream());
    downstreamCooler.setOutletTemperature(273.15 + 25.0);

    ProcessSystem downstream = new ProcessSystem();
    downstream.add(downstreamCooler);
    downstream.run();

    ProcessModel model = new ProcessModel();
    model.add("separation", separation);
    model.add("downstream", downstream);

    String json = model.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertTrue(root.has("interAreaLinks"), "Model export should include live inter-area links");
    assertEquals(1, root.getAsJsonArray("interAreaLinks").size());
    JsonObject link = root.getAsJsonArray("interAreaLinks").get(0).getAsJsonObject();
    assertEquals("separation", link.get("sourceArea").getAsString());
    assertEquals("HP Sep.gasOut", link.get("source").getAsString());
    assertEquals("downstream", link.get("targetArea").getAsString());
    assertEquals("Downstream Cooler", link.get("targetUnit").getAsString());

    JsonObject downstreamJson = root.getAsJsonObject("areas").getAsJsonObject("downstream");
    assertTrue(downstreamJson.has("fluids"), "External inlet fluid should be exported by name");

    JsonObject boundaryStream = downstreamJson.getAsJsonArray("process").get(0).getAsJsonObject();
    assertEquals("Stream", boundaryStream.get("type").getAsString());
    assertTrue(boundaryStream.get("name").getAsString().startsWith("boundary_"));
    assertTrue(boundaryStream.has("fluidRef"));

    JsonObject coolerJson = downstreamJson.getAsJsonArray("process").get(1).getAsJsonObject();
    assertEquals(boundaryStream.get("name").getAsString(), coolerJson.get("inlet").getAsString());

    SimulationResult result = new JsonProcessBuilder().build(downstreamJson.toString());
    assertTrue(result.isSuccess(), "Downstream area should rebuild: " + result.toJson());
    assertTrue(result.getWarnings().isEmpty(),
        "Materialized boundary stream should avoid unresolved inlet warnings: "
            + result.getWarnings());

    ProcessModel rebuilt = ProcessModel.fromJson(json);
    Separator rebuiltSeparator = (Separator) rebuilt.get("separation").getUnit("HP Sep");
    Cooler rebuiltCooler = (Cooler) rebuilt.get("downstream").getUnit("Downstream Cooler");
    assertTrue(rebuiltCooler.getInletStreams().get(0) == rebuiltSeparator.getGasOutStream(),
        "Whole-model import should restore the live shared stream, not only the boundary copy");
  }

  @Test
  void testInterAreaLinkPreservesDownstreamTwoPortOutletReference() {
    SystemInterface sourceFluid = new SystemSrkEos(273.15 + 25.0, 30.0);
    sourceFluid.addComponent("methane", 1.0);
    sourceFluid.setMixingRule("classic");

    Stream sourceFeed = new Stream("source feed", sourceFluid);
    sourceFeed.setFlowRate(1000.0, "kg/hr");
    sourceFeed.setPressure(30.0, "bara");
    sourceFeed.setTemperature(25.0, "C");

    ProcessSystem source = new ProcessSystem();
    source.add(sourceFeed);
    source.run();

    Heater pressureSetter = new Heater("pressure setter", sourceFeed);
    pressureSetter.setOutletPressure(5.0, "bara");
    pressureSetter.setOutTemperature(20.0, "C");
    Separator downstreamSeparator =
        new Separator("downstream separator", pressureSetter.getOutStream());

    ProcessSystem target = new ProcessSystem();
    target.add(pressureSetter);
    target.add(downstreamSeparator);
    target.run();

    ProcessModel model = new ProcessModel();
    model.add("source", source);
    model.add("target", target);

    String json = model.toJson();
    ProcessModel rebuilt = ProcessModel.fromJson(json);
    rebuilt.run();

    Separator rebuiltSeparator = (Separator) rebuilt.get("target").getUnit("downstream separator");
    assertEquals(5.0, rebuiltSeparator.getInletStreams().get(0).getPressure("bara"), 1.0e-6);
  }

  @Test
  void testManifoldRoundTripPreservesInletsAndSplitFactors() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feedA = new Stream("feed A", fluid.clone());
    feedA.setFlowRate(100.0, "kg/hr");
    Stream feedB = new Stream("feed B", fluid.clone());
    feedB.setFlowRate(300.0, "kg/hr");

    Manifold manifold = new Manifold("test manifold");
    manifold.addStream(feedA);
    manifold.addStream(feedB);
    manifold.setSplitFactors(new double[] {0.25, 0.75});

    ProcessSystem process = new ProcessSystem();
    process.add(feedA);
    process.add(feedB);
    process.add(manifold);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject manifoldJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertEquals("Manifold", manifoldJson.get("type").getAsString());
    assertEquals(2, manifoldJson.getAsJsonArray("inlets").size());
    assertEquals(2,
        manifoldJson.getAsJsonObject("properties").getAsJsonArray("splitFactors").size());

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Manifold process should rebuild: " + result.toJson());
    assertTrue(result.getWarnings().isEmpty(),
        "Manifold round-trip should not produce warnings: " + result.getWarnings());

    Manifold rebuilt = (Manifold) result.getProcessSystem().getUnit("test manifold");
    assertEquals(2, rebuilt.getInletStreams().size());
    assertEquals(2, rebuilt.getOutletStreams().size());
    assertEquals(100.0, rebuilt.getSplitStream(0).getFlowRate("kg/hr"), 1.0e-6);
    assertEquals(300.0, rebuilt.getSplitStream(1).getFlowRate("kg/hr"), 1.0e-6);
  }

  @Test
  void testHeatExchangerRoundTripPreservesTwoInlets() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 80.0, 30.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");
    SystemInterface coldFluid = new SystemSrkEos(273.15 + 20.0, 30.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");

    Stream hotFeed = new Stream("hot feed", hotFluid);
    hotFeed.setFlowRate(100.0, "kg/hr");
    Stream coldFeed = new Stream("cold feed", coldFluid);
    coldFeed.setFlowRate(100.0, "kg/hr");

    HeatExchanger heatExchanger = new HeatExchanger("test heat exchanger", hotFeed);
    heatExchanger.setFeedStream(1, coldFeed);
    heatExchanger.setUAvalue(1000.0);
    heatExchanger.setGuessOutTemperature(273.15 + 45.0, "K");

    ProcessSystem process = new ProcessSystem();
    process.add(hotFeed);
    process.add(coldFeed);
    process.add(heatExchanger);
    process.run();

    String json = process.toJson();
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    JsonObject heatExchangerJson = root.getAsJsonArray("process").get(2).getAsJsonObject();
    assertEquals("HeatExchanger", heatExchangerJson.get("type").getAsString());
    assertEquals(2, heatExchangerJson.getAsJsonArray("inlets").size());
    assertEquals(1000.0,
        heatExchangerJson.getAsJsonObject("properties").get("UAvalue").getAsDouble(), 1.0e-12);
    assertEquals(273.15 + 45.0, heatExchangerJson.getAsJsonObject("properties")
        .getAsJsonArray("guessOutTemperature").get(0).getAsDouble(), 1.0e-12);

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);
    assertTrue(result.isSuccess(), "Heat exchanger process should rebuild: " + result.toJson());
    assertTrue(result.getWarnings().isEmpty(),
        "Heat exchanger round-trip should not produce warnings: " + result.getWarnings());
  }
}
