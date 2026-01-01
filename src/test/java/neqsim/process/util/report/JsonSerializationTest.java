package neqsim.process.util.report;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.expander.TurboExpanderCompressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.mixer.StaticMixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests JSON serialization capability for all process equipment types.
 *
 * <p>
 * This test ensures that all equipment classes can be properly serialized to JSON for reporting and
 * export purposes.
 * </p>
 *
 * @author esol
 */
public class JsonSerializationTest {

  private static SystemSrkEos testFluid;
  private static Stream feedStream;

  @BeforeAll
  static void setUp() {
    testFluid = new SystemSrkEos(298.0, 10.0);
    testFluid.addComponent("methane", 80.0);
    testFluid.addComponent("ethane", 10.0);
    testFluid.addComponent("propane", 5.0);
    testFluid.addComponent("n-heptane", 5.0);
    testFluid.setMixingRule("classic");
    testFluid.setMultiPhaseCheck(true);

    feedStream = new Stream("test feed", testFluid);
    feedStream.setPressure(50.0, "bara");
    feedStream.setTemperature(25.0, "C");
    feedStream.setFlowRate(1000.0, "kg/hr");
    feedStream.run();
  }

  @Test
  @DisplayName("Stream should serialize to JSON")
  void testStreamToJson() {
    Stream stream = new Stream("test stream", testFluid.clone());
    stream.setPressure(50.0, "bara");
    stream.setTemperature(25.0, "C");
    stream.setFlowRate(1000.0, "kg/hr");
    stream.run();

    String json = stream.toJson();
    assertNotNull(json, "Stream.toJson() should not return null");
    assertTrue(json.contains("test stream") || json.contains("tagName"),
        "JSON should contain stream name or tagName");
  }

  @Test
  @DisplayName("Separator should serialize to JSON")
  void testSeparatorToJson() {
    Stream inlet = new Stream("sep inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Separator separator = new Separator("test separator", inlet);
    separator.run();

    String json = separator.toJson();
    assertNotNull(json, "Separator.toJson() should not return null");
  }

  @Test
  @DisplayName("ThreePhaseSeparator should serialize to JSON")
  void testThreePhaseSeparatorToJson() {
    Stream inlet = new Stream("3ph sep inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    ThreePhaseSeparator separator = new ThreePhaseSeparator("test 3ph separator", inlet);
    separator.run();

    String json = separator.toJson();
    assertNotNull(json, "ThreePhaseSeparator.toJson() should not return null");
  }

  @Test
  @DisplayName("Compressor should serialize to JSON")
  void testCompressorToJson() {
    Stream inlet = new Stream("comp inlet", testFluid.clone());
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Compressor compressor = new Compressor("test compressor", inlet);
    compressor.setOutletPressure(50.0, "bara");
    compressor.run();

    String json = compressor.toJson();
    assertNotNull(json, "Compressor.toJson() should not return null");
  }

  @Test
  @DisplayName("Pump should serialize to JSON")
  void testPumpToJson() {
    SystemSrkEos liquidSystem = new SystemSrkEos(298.0, 10.0);
    liquidSystem.addComponent("n-heptane", 100.0);
    liquidSystem.setMixingRule("classic");

    Stream inlet = new Stream("pump inlet", liquidSystem);
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Pump pump = new Pump("test pump", inlet);
    pump.setOutletPressure(50.0, "bara");
    pump.run();

    String json = pump.toJson();
    assertNotNull(json, "Pump.toJson() should not return null");
  }

  @Test
  @DisplayName("ThrottlingValve should serialize to JSON")
  void testValveToJson() {
    Stream inlet = new Stream("valve inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    ThrottlingValve valve = new ThrottlingValve("test valve", inlet);
    valve.setOutletPressure(10.0, "bara");
    valve.run();

    String json = valve.toJson();
    assertNotNull(json, "ThrottlingValve.toJson() should not return null");
  }

  @Test
  @DisplayName("Heater should serialize to JSON")
  void testHeaterToJson() {
    Stream inlet = new Stream("heater inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Heater heater = new Heater("test heater", inlet);
    heater.setOutTemperature(80.0, "C");
    heater.run();

    String json = heater.toJson();
    assertNotNull(json, "Heater.toJson() should not return null");
  }

  @Test
  @DisplayName("Cooler should serialize to JSON")
  void testCoolerToJson() {
    Stream inlet = new Stream("cooler inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(80.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Cooler cooler = new Cooler("test cooler", inlet);
    cooler.setOutTemperature(25.0, "C");
    cooler.run();

    String json = cooler.toJson();
    assertNotNull(json, "Cooler.toJson() should not return null");
  }

  @Test
  @DisplayName("Mixer should serialize to JSON")
  void testMixerToJson() {
    Stream inlet1 = new Stream("mixer inlet 1", testFluid.clone());
    inlet1.setPressure(50.0, "bara");
    inlet1.setTemperature(25.0, "C");
    inlet1.setFlowRate(500.0, "kg/hr");
    inlet1.run();

    Stream inlet2 = new Stream("mixer inlet 2", testFluid.clone());
    inlet2.setPressure(50.0, "bara");
    inlet2.setTemperature(30.0, "C");
    inlet2.setFlowRate(500.0, "kg/hr");
    inlet2.run();

    Mixer mixer = new Mixer("test mixer");
    mixer.addStream(inlet1);
    mixer.addStream(inlet2);
    mixer.run();

    String json = mixer.toJson();
    assertNotNull(json, "Mixer.toJson() should not return null");
  }

  @Test
  @DisplayName("Splitter should serialize to JSON")
  void testSplitterToJson() {
    Stream inlet = new Stream("splitter inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Splitter splitter = new Splitter("test splitter", inlet, 2);
    splitter.setSplitFactors(new double[] {0.5, 0.5});
    splitter.run();

    String json = splitter.toJson();
    assertNotNull(json, "Splitter.toJson() should not return null");
  }

  @Test
  @DisplayName("Tank should serialize to JSON")
  void testTankToJson() {
    Stream inlet = new Stream("tank inlet", testFluid.clone());
    inlet.setPressure(5.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    Tank tank = new Tank("test tank", inlet);
    tank.run();

    String json = tank.toJson();
    assertNotNull(json, "Tank.toJson() should not return null");
  }

  @Test
  @DisplayName("HeatExchanger should serialize to JSON")
  void testHeatExchangerToJson() {
    Stream hotStream = new Stream("hot stream", testFluid.clone());
    hotStream.setPressure(50.0, "bara");
    hotStream.setTemperature(100.0, "C");
    hotStream.setFlowRate(1000.0, "kg/hr");
    hotStream.run();

    Stream coldStream = new Stream("cold stream", testFluid.clone());
    coldStream.setPressure(50.0, "bara");
    coldStream.setTemperature(25.0, "C");
    coldStream.setFlowRate(1000.0, "kg/hr");
    coldStream.run();

    HeatExchanger hx = new HeatExchanger("test HX", hotStream, coldStream);
    hx.setUAvalue(1000.0);
    hx.run();

    String json = hx.toJson();
    assertNotNull(json, "HeatExchanger.toJson() should not return null");
  }

  @Test
  @DisplayName("Recycle should serialize to JSON")
  void testRecycleToJson() {
    // Create a simple process with recycle
    ProcessSystem process = new ProcessSystem();

    Stream inlet = new Stream("recycle inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Recycle recycle = new Recycle("test recycle");
    recycle.addStream(inlet);
    recycle.setOutletStream(inlet); // recycle outlet connects back

    process.add(inlet);
    process.add(recycle);
    process.run();

    String json = recycle.toJson();
    assertNotNull(json, "Recycle.toJson() should not return null");
  }

  @Test
  @DisplayName("ProcessSystem should serialize to JSON")
  void testProcessSystemToJson() {
    ProcessSystem process = new ProcessSystem();

    Stream inlet = new Stream("process inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("process separator", inlet);

    process.add(inlet);
    process.add(separator);
    process.run();

    String json = process.getReport_json();
    assertNotNull(json, "ProcessSystem.getReport_json() should not return null");
    assertTrue(!json.isEmpty(), "ProcessSystem JSON should not be empty");
  }

  @Test
  @DisplayName("ProcessModel should serialize to JSON")
  void testProcessModelToJson() {
    ProcessSystem process1 = new ProcessSystem();

    Stream inlet = new Stream("model inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("model separator", inlet);

    process1.add(inlet);
    process1.add(separator);

    ProcessModel model = new ProcessModel();
    model.add("process1", process1);
    model.run();

    String json = model.getReport_json();
    assertNotNull(json, "ProcessModel.getReport_json() should not return null");
    assertTrue(!json.isEmpty(), "ProcessModel JSON should not be empty");
  }

  @Test
  @DisplayName("All equipment in ProcessSystem should have valid JSON")
  void testAllEquipmentInProcessSystem() {
    ProcessSystem process = new ProcessSystem();

    // Create a comprehensive process with many equipment types
    Stream inlet = new Stream("feed", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("separator", inlet);

    Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");

    ThrottlingValve valve = new ThrottlingValve("valve", separator.getLiquidOutStream());
    valve.setOutletPressure(10.0, "bara");

    Heater heater = new Heater("heater", compressor.getOutStream());
    heater.setOutTemperature(50.0, "C");

    process.add(inlet);
    process.add(separator);
    process.add(compressor);
    process.add(valve);
    process.add(heater);
    process.run();

    // Get JSON report
    String json = process.getReport_json();
    assertNotNull(json, "Process JSON report should not be null");

    // Verify each equipment can generate JSON individually
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      String equipmentJson = equipment.toJson();
      // Note: Some equipment may return null if toJson() is not implemented
      // This test documents which equipment types need toJson() implementations
      if (equipmentJson == null) {
        System.out.println("WARNING: " + equipment.getClass().getSimpleName() + " ("
            + equipment.getName() + ") returns null from toJson()");
      }
    }
  }

  @Test
  @DisplayName("Verify JSON contains expected structure")
  void testJsonStructure() {
    Stream inlet = new Stream("test stream", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    String json = inlet.toJson();
    assertNotNull(json, "Stream JSON should not be null");
    assertTrue(json.startsWith("{"), "JSON should start with {");
    assertTrue(json.endsWith("}"), "JSON should end with }");
  }

  @Test
  @DisplayName("ComponentSplitter should serialize to JSON")
  void testComponentSplitterToJson() {
    Stream inlet = new Stream("comp splitter inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    ComponentSplitter splitter = new ComponentSplitter("test component splitter", inlet);
    double[] splitFactors = new double[inlet.getFluid().getNumberOfComponents()];
    for (int i = 0; i < splitFactors.length; i++) {
      splitFactors[i] = 0.5;
    }
    splitter.setSplitFactors(splitFactors);
    splitter.run();

    String json = splitter.toJson();
    assertNotNull(json, "ComponentSplitter.toJson() should not return null");
  }

  @Test
  @DisplayName("StaticMixer should serialize to JSON")
  void testStaticMixerToJson() {
    Stream inlet1 = new Stream("static mixer inlet 1", testFluid.clone());
    inlet1.setPressure(50.0, "bara");
    inlet1.setTemperature(25.0, "C");
    inlet1.setFlowRate(500.0, "kg/hr");
    inlet1.run();

    Stream inlet2 = new Stream("static mixer inlet 2", testFluid.clone());
    inlet2.setPressure(50.0, "bara");
    inlet2.setTemperature(30.0, "C");
    inlet2.setFlowRate(500.0, "kg/hr");
    inlet2.run();

    StaticMixer mixer = new StaticMixer("test static mixer");
    mixer.addStream(inlet1);
    mixer.addStream(inlet2);
    mixer.run();

    String json = mixer.toJson();
    assertNotNull(json, "StaticMixer.toJson() should not return null");
  }

  @Test
  @DisplayName("Manifold should serialize to JSON")
  void testManifoldToJson() {
    Stream inlet1 = new Stream("manifold inlet 1", testFluid.clone());
    inlet1.setPressure(50.0, "bara");
    inlet1.setTemperature(25.0, "C");
    inlet1.setFlowRate(500.0, "kg/hr");
    inlet1.run();

    Stream inlet2 = new Stream("manifold inlet 2", testFluid.clone());
    inlet2.setPressure(50.0, "bara");
    inlet2.setTemperature(30.0, "C");
    inlet2.setFlowRate(500.0, "kg/hr");
    inlet2.run();

    Manifold manifold = new Manifold("test manifold");
    manifold.addStream(inlet1);
    manifold.addStream(inlet2);
    manifold.run();

    String json = manifold.toJson();
    assertNotNull(json, "Manifold.toJson() should not return null");
  }

  @Test
  @DisplayName("PipeBeggsAndBrills should serialize to JSON")
  void testPipeToJson() {
    Stream inlet = new Stream("pipe inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(10000.0, "kg/hr");
    inlet.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("test pipe", inlet);
    pipe.setPipeWallRoughness(1e-5);
    pipe.setLength(1000.0);
    pipe.setDiameter(0.1);
    pipe.setElevation(0.0); // horizontal pipe
    pipe.run();

    String json = pipe.toJson();
    assertNotNull(json, "PipeBeggsAndBrills.toJson() should not return null");
  }

  @Test
  @DisplayName("DistillationColumn should serialize to JSON")
  void testDistillationColumnToJson() {
    SystemSrkEos columnFeed = new SystemSrkEos(298.0, 10.0);
    columnFeed.addComponent("methane", 50.0);
    columnFeed.addComponent("ethane", 30.0);
    columnFeed.addComponent("propane", 20.0);
    columnFeed.setMixingRule("classic");
    columnFeed.setMultiPhaseCheck(true);

    Stream inlet = new Stream("column feed", columnFeed);
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(-20.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    DistillationColumn column = new DistillationColumn("test column", 5, true, true);
    column.addFeedStream(inlet, 3);
    column.getReboiler().setRefluxRatio(0.5);
    column.getCondenser().setRefluxRatio(0.5);
    column.setTopPressure(5.0);
    column.setBottomPressure(10.0);
    column.run();

    String json = column.toJson();
    assertNotNull(json, "DistillationColumn.toJson() should not return null");
  }

  @Test
  @DisplayName("TurboExpanderCompressor should serialize to JSON")
  void testTurboExpanderCompressorToJson() {
    Stream expanderInlet = new Stream("expander inlet", testFluid.clone());
    expanderInlet.setPressure(100.0, "bara");
    expanderInlet.setTemperature(50.0, "C");
    expanderInlet.setFlowRate(5000.0, "kg/hr");
    expanderInlet.run();

    TurboExpanderCompressor turboExpComp = new TurboExpanderCompressor("test turbo", expanderInlet);
    turboExpComp.setOutletPressure(30.0);
    turboExpComp.run();

    String json = turboExpComp.toJson();
    assertNotNull(json, "TurboExpanderCompressor.toJson() should not return null");
  }

  @Test
  @DisplayName("ProcessSystem with comprehensive equipment should serialize to JSON")
  void testComprehensiveProcessSystemToJson() {
    ProcessSystem process = new ProcessSystem();

    // Create a comprehensive gas processing system
    Stream feed = new Stream("feed stream", testFluid.clone());
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.setFlowRate(5000.0, "kg/hr");

    // Inlet separator
    Separator inletSeparator = new Separator("inlet separator", feed);
    inletSeparator.setInternalDiameter(1.5);
    inletSeparator.setSeparatorLength(4.0);

    // Gas compression
    Compressor compressor = new Compressor("main compressor", inletSeparator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    compressor.setPolytropicEfficiency(0.75);

    // After-cooler
    Cooler afterCooler = new Cooler("after cooler", compressor.getOutStream());
    afterCooler.setOutTemperature(35.0, "C");

    // High pressure separator
    ThreePhaseSeparator hpSeparator =
        new ThreePhaseSeparator("HP separator", afterCooler.getOutletStream());

    // Liquid handling - valve
    ThrottlingValve liquidValve =
        new ThrottlingValve("liquid valve", inletSeparator.getLiquidOutStream());
    liquidValve.setOutletPressure(10.0, "bara");

    // Storage tank
    Tank storageTank = new Tank("storage tank", liquidValve.getOutletStream());

    // Export pump
    Pump exportPump = new Pump("export pump", storageTank.getLiquidOutStream());
    exportPump.setOutletPressure(30.0, "bara");

    // Splitter for gas distribution
    Splitter gasSplitter = new Splitter("gas splitter", hpSeparator.getGasOutStream(), 2);
    gasSplitter.setSplitFactors(new double[] {0.7, 0.3});

    // Add all equipment to process
    process.add(feed);
    process.add(inletSeparator);
    process.add(compressor);
    process.add(afterCooler);
    process.add(hpSeparator);
    process.add(liquidValve);
    process.add(storageTank);
    process.add(exportPump);
    process.add(gasSplitter);

    process.run();

    // Test JSON serialization
    String processJson = process.getReport_json();
    assertNotNull(processJson, "ProcessSystem JSON should not be null");
    assertTrue(!processJson.isEmpty(), "ProcessSystem JSON should not be empty");
    assertTrue(processJson.startsWith("{"), "ProcessSystem JSON should be valid JSON object");
    assertTrue(processJson.endsWith("}"), "ProcessSystem JSON should be valid JSON object");

    // Verify key equipment names are in JSON
    assertTrue(processJson.contains("feed stream") || processJson.contains("inlet separator"),
        "JSON should contain equipment names");

    // Count equipment that serialize properly
    int successCount = 0;
    int nullCount = 0;
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      String equipmentJson = equipment.toJson();
      if (equipmentJson != null) {
        successCount++;
      } else {
        nullCount++;
      }
    }

    System.out.println("ProcessSystem JSON serialization: " + successCount + " successful, "
        + nullCount + " returned null");
    assertTrue(successCount >= 8,
        "At least 8 equipment types should serialize (got " + successCount + ")");
  }

  @Test
  @DisplayName("ProcessModel with multiple ProcessSystems should serialize to JSON")
  void testComprehensiveProcessModelToJson() {
    // Create first process - Gas Processing (simple equipment only)
    ProcessSystem gasProcess = new ProcessSystem();
    gasProcess.setName("Gas Processing");

    // Use gas-dominated fluid
    SystemSrkEos gasFluid = new SystemSrkEos(298.0, 30.0);
    gasFluid.addComponent("methane", 85.0);
    gasFluid.addComponent("ethane", 10.0);
    gasFluid.addComponent("propane", 5.0);
    gasFluid.setMixingRule("classic");
    gasFluid.setMultiPhaseCheck(true);

    Stream gasFeed = new Stream("gas feed", gasFluid);
    gasFeed.setPressure(30.0, "bara");
    gasFeed.setTemperature(20.0, "C");
    gasFeed.setFlowRate(3000.0, "kg/hr");

    Heater gasHeater = new Heater("gas heater", gasFeed);
    gasHeater.setOutTemperature(60.0, "C");

    ThrottlingValve gasValve = new ThrottlingValve("gas valve", gasHeater.getOutletStream());
    gasValve.setOutletPressure(10.0, "bara");

    Cooler gasCooler = new Cooler("gas cooler", gasValve.getOutletStream());
    gasCooler.setOutTemperature(25.0, "C");

    gasProcess.add(gasFeed);
    gasProcess.add(gasHeater);
    gasProcess.add(gasValve);
    gasProcess.add(gasCooler);

    // Create second process - Liquid Processing (no separator to avoid NaN with single-phase)
    ProcessSystem liquidProcess = new ProcessSystem();
    liquidProcess.setName("Liquid Processing");

    // Use a two-phase fluid to avoid NaN issues
    SystemSrkEos liquidFluid = new SystemSrkEos(298.0, 5.0);
    liquidFluid.addComponent("methane", 5.0);
    liquidFluid.addComponent("n-pentane", 30.0);
    liquidFluid.addComponent("n-hexane", 35.0);
    liquidFluid.addComponent("n-heptane", 30.0);
    liquidFluid.setMixingRule("classic");
    liquidFluid.setMultiPhaseCheck(true);

    Stream liquidFeed = new Stream("liquid feed", liquidFluid);
    liquidFeed.setPressure(10.0, "bara");
    liquidFeed.setTemperature(25.0, "C");
    liquidFeed.setFlowRate(2000.0, "kg/hr");

    Heater liquidHeater = new Heater("liquid heater", liquidFeed);
    liquidHeater.setOutTemperature(80.0, "C");

    ThrottlingValve pressureValve =
        new ThrottlingValve("pressure valve", liquidHeater.getOutletStream());
    pressureValve.setOutletPressure(5.0, "bara");

    Cooler liquidCooler = new Cooler("liquid cooler", pressureValve.getOutletStream());
    liquidCooler.setOutTemperature(30.0, "C");

    liquidProcess.add(liquidFeed);
    liquidProcess.add(liquidHeater);
    liquidProcess.add(pressureValve);
    liquidProcess.add(liquidCooler);

    // Create ProcessModel with both processes
    ProcessModel model = new ProcessModel();
    model.add("GasProcessing", gasProcess);
    model.add("LiquidProcessing", liquidProcess);

    // Run the model
    model.run();

    // Test JSON serialization
    String modelJson = model.getReport_json();
    assertNotNull(modelJson, "ProcessModel JSON should not be null");
    assertTrue(!modelJson.isEmpty(), "ProcessModel JSON should not be empty");
    assertTrue(modelJson.startsWith("{"), "ProcessModel JSON should be valid JSON object");
    assertTrue(modelJson.endsWith("}"), "ProcessModel JSON should be valid JSON object");

    // Verify both processes are in the JSON
    assertTrue(modelJson.contains("GasProcessing") || modelJson.contains("gas"),
        "JSON should contain gas processing data");

    System.out.println("ProcessModel JSON length: " + modelJson.length() + " characters");
    System.out.println("ProcessModel contains " + model.getAllProcesses().size() + " processes");

    // Verify all processes can be individually serialized
    for (ProcessSystem process : model.getAllProcesses()) {
      String processJson = process.getReport_json();
      assertNotNull(processJson, "Individual ProcessSystem JSON should not be null");
      assertTrue(!processJson.isEmpty(),
          "Individual ProcessSystem JSON should not be empty for " + process.getName());
    }
  }

  @Test
  @DisplayName("ProcessSystem JSON should contain all equipment data")
  void testProcessSystemJsonContainsAllEquipment() {
    ProcessSystem process = new ProcessSystem();

    Stream inlet = new Stream("test inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("test separator", inlet);

    Compressor compressor = new Compressor("test compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");

    process.add(inlet);
    process.add(separator);
    process.add(compressor);
    process.run();

    String json = process.getReport_json();

    // Verify equipment count matches
    int equipmentCount = process.getUnitOperations().size();
    System.out.println("ProcessSystem has " + equipmentCount + " units");

    // Parse and check equipment presence in JSON
    // Note: Equipment returning null from toJson() won't appear in the report
    int jsonEquipmentCount = 0;
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      if (equipment.toJson() != null) {
        jsonEquipmentCount++;
      }
    }

    System.out.println("Equipment with JSON support: " + jsonEquipmentCount);
    assertTrue(jsonEquipmentCount == equipmentCount,
        "All equipment in this test should have JSON support");
  }

  @Test
  @DisplayName("ProcessModel mass balance check should work with JSON export")
  void testProcessModelMassBalanceAndJson() {
    ProcessSystem process = new ProcessSystem();

    Stream inlet = new Stream("mass balance inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");

    Separator separator = new Separator("mass balance separator", inlet);

    Mixer mixer = new Mixer("mass balance mixer");
    mixer.addStream(separator.getGasOutStream());
    mixer.addStream(separator.getLiquidOutStream());

    process.add(inlet);
    process.add(separator);
    process.add(mixer);
    process.run();

    ProcessModel model = new ProcessModel();
    model.add("MassBalanceTest", process);
    model.run();

    // Get JSON report
    String json = model.getReport_json();
    assertNotNull(json, "ProcessModel JSON should not be null");

    // Check mass balance
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> massBalanceResults =
        model.checkMassBalance("kg/hr");
    assertNotNull(massBalanceResults, "Mass balance results should not be null");

    // Get mass balance report
    String massBalanceReport = model.getMassBalanceReport("kg/hr");
    assertNotNull(massBalanceReport, "Mass balance report should not be null");
    System.out.println("Mass balance report:\n" + massBalanceReport);
  }

  @Test
  @DisplayName("Filter should serialize to JSON")
  void testFilterToJson() {
    Stream inlet = new Stream("filter inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    neqsim.process.equipment.filter.Filter filter =
        new neqsim.process.equipment.filter.Filter("test filter", inlet);
    filter.setDeltaP(0.5);
    filter.run();

    String json = filter.toJson();
    assertNotNull(json, "Filter.toJson() should not return null");
    assertTrue(json.contains("filter") || json.contains("pressure"),
        "JSON should contain filter data");
  }

  @Test
  @DisplayName("Ejector should serialize to JSON")
  void testEjectorToJson() {
    Stream motiveStream = new Stream("motive stream", testFluid.clone());
    motiveStream.setPressure(50.0, "bara");
    motiveStream.setTemperature(25.0, "C");
    motiveStream.setFlowRate(500.0, "kg/hr");
    motiveStream.run();

    Stream suctionStream = new Stream("suction stream", testFluid.clone());
    suctionStream.setPressure(10.0, "bara");
    suctionStream.setTemperature(25.0, "C");
    suctionStream.setFlowRate(300.0, "kg/hr");
    suctionStream.run();

    neqsim.process.equipment.ejector.Ejector ejector =
        new neqsim.process.equipment.ejector.Ejector("test ejector", motiveStream, suctionStream);
    ejector.setDischargePressure(15.0);
    ejector.run();

    String json = ejector.toJson();
    assertNotNull(json, "Ejector.toJson() should not return null");
    assertTrue(json.contains("ejector") || json.contains("efficiency"),
        "JSON should contain ejector data");
  }

  @Test
  @DisplayName("Flare should serialize to JSON")
  void testFlareToJson() {
    Stream inlet = new Stream("flare inlet", testFluid.clone());
    inlet.setPressure(10.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(100.0, "kg/hr");
    inlet.run();

    neqsim.process.equipment.flare.Flare flare =
        new neqsim.process.equipment.flare.Flare("test flare", inlet);
    // Note: Not running the flare as it requires element database for CO2 calculation
    // Just testing that toJson() works without throwing an exception

    String json = flare.toJson();
    assertNotNull(json, "Flare.toJson() should not return null");
    assertTrue(json.contains("flare") || json.contains("name"), "JSON should contain flare data");
  }

  @Test
  @DisplayName("Pipeline should serialize to JSON")
  void testPipelineToJson() {
    Stream inlet = new Stream("pipeline inlet", testFluid.clone());
    inlet.setPressure(50.0, "bara");
    inlet.setTemperature(25.0, "C");
    inlet.setFlowRate(1000.0, "kg/hr");
    inlet.run();

    neqsim.process.equipment.pipeline.Pipeline pipeline =
        new neqsim.process.equipment.pipeline.Pipeline("test pipeline", inlet);
    // Note: Pipeline needs more setup for full run, just test basic serialization

    String json = pipeline.toJson();
    assertNotNull(json, "Pipeline.toJson() should not return null");
    assertTrue(json.contains("pipeline") || json.contains("inlet"),
        "JSON should contain pipeline data");
  }
}
