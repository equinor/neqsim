package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.dexpi.DexpiProcessUnit;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;


/**
 * Tests for the ProcessDiagramExporter class.
 *
 * @author NeqSim
 */
class ProcessDiagramExporterTest {

  private ProcessSystem process;
  private SystemInterface fluid;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    // Create a test fluid
    fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("ethane", 0.15);
    fluid.addComponent("propane", 0.1);
    fluid.addComponent("n-butane", 0.05);
    fluid.setMixingRule("classic");

    // Create a simple process
    process = new ProcessSystem("Test Gas Processing");
  }

  @Test
  void testBasicDOTExport() {
    // Create a simple process
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    // Run the process
    process.run();

    // Export to DOT
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(process);
    String dot = exporter.toDOT();

    // Verify DOT structure
    assertNotNull(dot);
    assertTrue(dot.contains("digraph ProcessFlowDiagram"));
    assertTrue(dot.contains("Feed"));
    assertTrue(dot.contains("HP Separator"));
    assertTrue(dot.contains("->"), "Should contain edge connections");
  }

  @Test
  void testGasSeparationProcess() {
    // Create a realistic gas separation process
    Stream feed = new Stream("Well Fluid", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(60.0, "C");
    feed.setPressure(80.0, "bara");
    process.add(feed);

    // First stage separation
    Separator hpSeparator = new Separator("HP Separator", feed);
    process.add(hpSeparator);

    // Gas compression
    Compressor compressor = new Compressor("Export Compressor", hpSeparator.getGasOutStream());
    compressor.setOutletPressure(120.0, "bara");
    process.add(compressor);

    // Gas cooler
    Cooler gasCooler = new Cooler("Gas Cooler", compressor.getOutletStream());
    gasCooler.setOutTemperature(40.0, "C");
    process.add(gasCooler);

    // Liquid pump
    Pump liquidPump = new Pump("Oil Pump", hpSeparator.getLiquidOutStream());
    liquidPump.setOutletPressure(60.0, "bara");
    process.add(liquidPump);

    process.run();

    // Export to DOT
    ProcessDiagramExporter exporter =
        new ProcessDiagramExporter(process).setTitle("Gas Separation Process")
            .setDetailLevel(DiagramDetailLevel.ENGINEERING).setVerticalLayout(true);

    String dot = exporter.toDOT();

    // Verify industry PFD layout uses LR with phase zone ordering
    assertTrue(dot.contains("rankdir=LR"), "Should use left-to-right layout");
    // Feed/product streams are grouped with rank=same constraints
    assertTrue(dot.contains("rank=same"), "Should group feeds and products by rank");

    // Verify phase-aware coloring
    assertTrue(dot.contains("#87CEEB") || dot.contains("87CEEB"),
        "Should contain gas stream color");
    assertTrue(dot.contains("#4169E1") || dot.contains("4169E1"),
        "Should contain liquid stream color");
  }

  @Test
  void testDetailLevelConceptual() {
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("Separator", feed);
    process.add(sep);

    process.run();

    // Export at CONCEPTUAL level
    String dot =
        new ProcessDiagramExporter(process).setDetailLevel(DiagramDetailLevel.CONCEPTUAL).toDOT();

    // Should have compact labels (no temperature/pressure)
    assertFalse(dot.contains("°C"), "CONCEPTUAL level should not show temperature");
    assertFalse(dot.contains("bara"), "CONCEPTUAL level should not show pressure");
  }

  @Test
  void testDetailLevelEngineering() {
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    process.add(feed);

    process.run();

    // Export at ENGINEERING level
    String dot =
        new ProcessDiagramExporter(process).setDetailLevel(DiagramDetailLevel.ENGINEERING).toDOT();

    // Should include process conditions
    assertTrue(dot.contains("Stream") || dot.contains("Feed"));
    // The label format includes conditions
  }

  @Test
  void testProcessSystemConvenienceMethods() {
    Stream feed = new Stream("Feed", fluid);
    process.add(feed);

    Separator sep = new Separator("Separator", feed);
    process.add(sep);

    process.run();

    // Test toDOT() method
    String dot = process.toDOT();
    assertNotNull(dot);
    assertTrue(dot.contains("digraph"));

    // Test toDOT(detailLevel) method
    String conceptualDot = process.toDOT(DiagramDetailLevel.CONCEPTUAL);
    assertNotNull(conceptualDot);

    // Test createDiagramExporter()
    ProcessDiagramExporter exporter = process.createDiagramExporter();
    assertNotNull(exporter);
  }

  @Test
  void testDOTFileExport() throws IOException {
    Stream feed = new Stream("Feed", fluid);
    process.add(feed);

    Separator sep = new Separator("Separator", feed);
    process.add(sep);

    process.run();

    // Export to file
    Path dotFile = tempDir.resolve("test_process.dot");
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(process);
    exporter.exportDOT(dotFile);

    // Verify file was created
    assertTrue(Files.exists(dotFile));

    // Verify content
    String content = new String(Files.readAllBytes(dotFile));
    assertTrue(content.contains("digraph ProcessFlowDiagram"));
    assertTrue(content.contains("Feed"));
    assertTrue(content.contains("Separator"));
  }

  @Test
  void testEquipmentRoleClassification() {
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    // Create equipment of different types
    Stream feed = new Stream("Feed", fluid);
    Separator separator = new Separator("Separator", feed);
    Compressor compressor = new Compressor("Compressor", feed);
    Pump pump = new Pump("Pump", feed);
    ThrottlingValve valve = new ThrottlingValve("Valve", feed);

    // Test role classification
    assertEquals(EquipmentRole.SEPARATOR, policy.classifyEquipment(separator));
    assertEquals(EquipmentRole.GAS, policy.classifyEquipment(compressor));
    assertEquals(EquipmentRole.LIQUID, policy.classifyEquipment(pump));
    assertEquals(EquipmentRole.CONTROL, policy.classifyEquipment(valve));
  }

  @Test
  void testStreamPhaseClassification() {
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    // Create gas-like stream
    SystemInterface gasFluid = new SystemSrkEos(298.0, 50.0);
    gasFluid.addComponent("methane", 1.0);
    gasFluid.setMixingRule("classic");
    Stream gasStream = new Stream("Gas Stream", gasFluid);
    gasStream.setFlowRate(1000.0, "kg/hr");
    gasStream.setTemperature(25.0, "C");
    gasStream.setPressure(50.0, "bara");
    gasStream.run();

    // Create liquid-like stream (using n-heptane at 50°C and 50 bara - definitely liquid)
    SystemInterface liquidFluid = new SystemSrkEos(323.15, 50.0);
    liquidFluid.addComponent("n-heptane", 1.0);
    liquidFluid.setMixingRule("classic");
    Stream liquidStream = new Stream("Liquid Stream", liquidFluid);
    liquidStream.setFlowRate(1000.0, "kg/hr");
    liquidStream.setTemperature(50.0, "C");
    liquidStream.setPressure(50.0, "bara");
    liquidStream.run();

    // Test phase classification
    PFDLayoutPolicy.StreamPhase gasPhase = policy.classifyStreamPhase(gasStream);
    PFDLayoutPolicy.StreamPhase liquidPhase = policy.classifyStreamPhase(liquidStream);

    assertEquals(PFDLayoutPolicy.StreamPhase.GAS, gasPhase);
    // Liquid can be classified as LIQUID, OIL, or AQUEOUS - all are valid liquid phases
    assertTrue(
        liquidPhase == PFDLayoutPolicy.StreamPhase.LIQUID
            || liquidPhase == PFDLayoutPolicy.StreamPhase.OIL
            || liquidPhase == PFDLayoutPolicy.StreamPhase.AQUEOUS,
        "Expected a liquid phase but was: " + liquidPhase);
  }

  @Test
  void testEquipmentVisualStyles() {
    // Test separator style
    EquipmentVisualStyle separatorStyle = EquipmentVisualStyle.getStyle("separator");
    assertEquals("cylinder", separatorStyle.getShape());
    assertNotNull(separatorStyle.getFillColor());

    // Test compressor style
    EquipmentVisualStyle compressorStyle = EquipmentVisualStyle.getStyle("compressor");
    assertEquals("parallelogram", compressorStyle.getShape());

    // Test pump style
    EquipmentVisualStyle pumpStyle = EquipmentVisualStyle.getStyle("pump");
    assertEquals("circle", pumpStyle.getShape());

    // Test valve style
    EquipmentVisualStyle valveStyle = EquipmentVisualStyle.getStyle("valve");
    assertEquals("diamond", valveStyle.getShape());

    // Test default for unknown
    EquipmentVisualStyle defaultStyle = EquipmentVisualStyle.getStyle("unknowntype");
    assertNotNull(defaultStyle);
    assertEquals("rect", defaultStyle.getShape());
  }

  @Test
  void testGraphvizAttributeGeneration() {
    EquipmentVisualStyle style = EquipmentVisualStyle.getStyle("separator");
    String attrs = style.toGraphvizAttributes("HP Separator");

    assertTrue(attrs.startsWith("["));
    assertTrue(attrs.endsWith("]"));
    assertTrue(attrs.contains("label=\"HP Separator\""));
    assertTrue(attrs.contains("shape=cylinder"));
    assertTrue(attrs.contains("style="));
    assertTrue(attrs.contains("fillcolor="));
  }

  @Test
  void testLayoutPolicyRankAssignment() {
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    Stream feed = new Stream("Feed", fluid);
    Separator separator = new Separator("Separator", feed);
    Compressor compressor = new Compressor("Compressor", feed);
    Pump pump = new Pump("Pump", feed);

    // Test rank levels (0=top, 2=bottom)
    assertEquals(0, policy.classifyEquipment(compressor).getRankPriority());
    assertEquals(1, policy.classifyEquipment(separator).getRankPriority());
    assertEquals(2, policy.classifyEquipment(pump).getRankPriority());
  }

  @Test
  void testClusterGeneration() {
    // Create process with both gas and liquid equipment
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);

    Separator sep = new Separator("Separator", feed);
    process.add(sep);

    Compressor comp = new Compressor("Compressor", sep.getGasOutStream());
    comp.setOutletPressure(80.0, "bara");
    process.add(comp);

    Pump pump = new Pump("Pump", sep.getLiquidOutStream());
    pump.setOutletPressure(60.0, "bara");
    process.add(pump);

    process.run();

    // Export with clusters enabled
    String dot = new ProcessDiagramExporter(process).setUseClusters(true).toDOT();

    // Check for phase zone cluster subgraphs (Gas, Separation, Oil, Water)
    assertTrue(dot.contains("subgraph cluster_gas") || dot.contains("cluster_separation")
        || dot.contains("cluster_oil") || dot.contains("cluster_water")
        || dot.contains("Phase zone"), "Should contain phase zone clusters");
  }

  @Test
  void testLegendGeneration() {
    Stream feed = new Stream("Feed", fluid);
    process.add(feed);
    process.run();

    // With legend
    String dotWithLegend = new ProcessDiagramExporter(process).setShowLegend(true).toDOT();
    assertTrue(dotWithLegend.contains("cluster_legend") || dotWithLegend.contains("Legend"));

    // Without legend
    String dotNoLegend = new ProcessDiagramExporter(process).setShowLegend(false).toDOT();
    assertFalse(dotNoLegend.contains("cluster_legend"));
  }

  @Test
  void testHorizontalLayout() {
    Stream feed = new Stream("Feed", fluid);
    process.add(feed);
    process.run();

    // Industry PFD always uses LR (left-to-right) for proper flow orientation
    // Vertical stratification is handled via phase zone clusters
    String verticalDot = new ProcessDiagramExporter(process).setVerticalLayout(true).toDOT();
    assertTrue(verticalDot.contains("rankdir=LR"),
        "Should use LR layout for industry PFD (left-to-right flow)");

    // Horizontal layout also uses LR
    String horizontalDot = new ProcessDiagramExporter(process).setVerticalLayout(false).toDOT();
    assertTrue(horizontalDot.contains("rankdir=LR"), "Should use LR layout for industry PFD");
  }

  @Test
  void testDiagramDetailLevelEnum() {
    // Test CONCEPTUAL
    assertFalse(DiagramDetailLevel.CONCEPTUAL.showConditions());
    assertFalse(DiagramDetailLevel.CONCEPTUAL.showFlowRates());
    assertTrue(DiagramDetailLevel.CONCEPTUAL.useCompactLabels());

    // Test ENGINEERING
    assertTrue(DiagramDetailLevel.ENGINEERING.showConditions());
    assertTrue(DiagramDetailLevel.ENGINEERING.showFlowRates());
    assertTrue(DiagramDetailLevel.ENGINEERING.useCompactLabels());

    // Test DEBUG
    assertTrue(DiagramDetailLevel.DEBUG.showConditions());
    assertTrue(DiagramDetailLevel.DEBUG.showCompositions());
    assertFalse(DiagramDetailLevel.DEBUG.useCompactLabels());
  }

  @Test
  void testEquipmentRoleEnum() {
    // Test role properties
    assertEquals("Gas", EquipmentRole.GAS.getDisplayName());
    assertEquals("upper", EquipmentRole.GAS.getPreferredZone());
    assertEquals(0, EquipmentRole.GAS.getRankPriority());

    assertEquals("Liquid", EquipmentRole.LIQUID.getDisplayName());
    assertEquals("lower", EquipmentRole.LIQUID.getPreferredZone());
    assertEquals(2, EquipmentRole.LIQUID.getRankPriority());

    assertEquals("Separator", EquipmentRole.SEPARATOR.getDisplayName());
    assertEquals("center", EquipmentRole.SEPARATOR.getPreferredZone());
  }

  @Test
  void testSeparatorOutletClassification() {
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    // Create separator and streams
    Stream feed = new Stream("Feed", fluid);
    feed.run();

    Separator separator = new Separator("Separator", feed);
    separator.run();

    // Gas outlet should be TOP
    PFDLayoutPolicy.SeparatorOutlet gasOutlet =
        policy.classifySeparatorOutlet(separator, separator.getGasOutStream());
    assertEquals("n", gasOutlet.getPort()); // North = top

    // Liquid outlet should be BOTTOM
    PFDLayoutPolicy.SeparatorOutlet liquidOutlet =
        policy.classifySeparatorOutlet(separator, separator.getLiquidOutStream());
    assertEquals("s", liquidOutlet.getPort()); // South = bottom
  }

  @Test
  void testChainedConfiguration() {
    Stream feed = new Stream("Feed", fluid);
    process.add(feed);
    process.run();

    // Test fluent API
    String dot = new ProcessDiagramExporter(process).setTitle("My Process")
        .setDetailLevel(DiagramDetailLevel.ENGINEERING).setVerticalLayout(true)
        .setUseClusters(false).setShowLegend(true).toDOT();

    assertTrue(dot.contains("My Process"));
  }

  @Test
  void testGraphvizAvailabilityCheck() {
    // This just tests that the method doesn't throw
    boolean available = ProcessDiagramExporter.isGraphvizAvailable();
    // We don't assert the value since it depends on system configuration
    assertNotNull(Boolean.valueOf(available));
  }

  @Test
  void testThreePhaseSeparatorOutletClassification() {
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    // Create a three-phase fluid (gas, oil, aqueous)
    SystemInterface threePhaseFluid = new SystemSrkEos(298.0, 50.0);
    threePhaseFluid.addComponent("methane", 0.5);
    threePhaseFluid.addComponent("n-heptane", 0.3);
    threePhaseFluid.addComponent("water", 0.2);
    threePhaseFluid.setMixingRule("classic");
    threePhaseFluid.setMultiPhaseCheck(true);

    // Create feed stream
    Stream feed = new Stream("Feed", threePhaseFluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    feed.run();

    // Create three-phase separator
    ThreePhaseSeparator separator = new ThreePhaseSeparator("HP Separator", feed);
    separator.run();

    // Gas outlet should be TOP (north)
    PFDLayoutPolicy.SeparatorOutlet gasOutlet =
        policy.classifySeparatorOutlet(separator, separator.getGasOutStream());
    assertEquals(PFDLayoutPolicy.SeparatorOutlet.GAS_TOP, gasOutlet,
        "Gas outlet should be classified as GAS_TOP");
    assertEquals("n", gasOutlet.getPort(), "Gas should exit from top (north)");

    // Oil outlet should be MIDDLE (east)
    PFDLayoutPolicy.SeparatorOutlet oilOutlet =
        policy.classifySeparatorOutlet(separator, separator.getOilOutStream());
    assertEquals(PFDLayoutPolicy.SeparatorOutlet.OIL_MIDDLE, oilOutlet,
        "Oil outlet should be classified as OIL_MIDDLE");
    assertEquals("e", oilOutlet.getPort(), "Oil should exit from middle (east)");

    // Water/Aqueous outlet should be BOTTOM (south)
    PFDLayoutPolicy.SeparatorOutlet waterOutlet =
        policy.classifySeparatorOutlet(separator, separator.getWaterOutStream());
    assertEquals(PFDLayoutPolicy.SeparatorOutlet.WATER_BOTTOM, waterOutlet,
        "Water outlet should be classified as WATER_BOTTOM");
    assertEquals("s", waterOutlet.getPort(), "Water should exit from bottom (south)");
  }

  @Test
  void testThreePhaseSeparatorDiagramExport() {
    // Create a three-phase fluid (gas, oil, aqueous)
    SystemInterface threePhaseFluid = new SystemSrkEos(298.0, 50.0);
    threePhaseFluid.addComponent("methane", 0.5);
    threePhaseFluid.addComponent("n-heptane", 0.3);
    threePhaseFluid.addComponent("water", 0.2);
    threePhaseFluid.setMixingRule("classic");
    threePhaseFluid.setMultiPhaseCheck(true);

    // Build process with three-phase separator
    ProcessSystem threePhaseProcess = new ProcessSystem("Three-Phase Test Process");

    Stream feed = new Stream("Well Fluid", threePhaseFluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(60.0, "C");
    feed.setPressure(80.0, "bara");
    threePhaseProcess.add(feed);

    ThreePhaseSeparator separator = new ThreePhaseSeparator("Production Separator", feed);
    threePhaseProcess.add(separator);

    // Add downstream equipment for each phase
    Compressor gasCompressor = new Compressor("Gas Compressor", separator.getGasOutStream());
    gasCompressor.setOutletPressure(120.0, "bara");
    threePhaseProcess.add(gasCompressor);

    Pump oilPump = new Pump("Oil Pump", separator.getOilOutStream());
    oilPump.setOutletPressure(60.0, "bara");
    threePhaseProcess.add(oilPump);

    Pump waterPump = new Pump("Water Pump", separator.getWaterOutStream());
    waterPump.setOutletPressure(10.0, "bara");
    threePhaseProcess.add(waterPump);

    threePhaseProcess.run();

    // Export to DOT
    ProcessDiagramExporter exporter =
        new ProcessDiagramExporter(threePhaseProcess).setTitle("Three-Phase Separation Process")
            .setDetailLevel(DiagramDetailLevel.ENGINEERING).setVerticalLayout(true);

    String dot = exporter.toDOT();

    // Verify DOT structure includes all equipment
    assertNotNull(dot);
    assertTrue(dot.contains("Production Separator"), "Should contain three-phase separator");
    assertTrue(dot.contains("Gas Compressor"), "Should contain gas compressor");
    assertTrue(dot.contains("Oil Pump"), "Should contain oil pump");
    assertTrue(dot.contains("Water Pump"), "Should contain water pump");

    // Verify edges exist from separator to downstream equipment
    assertTrue(dot.contains("->"), "Should contain edge connections");
  }

  @Test
  void testRecycleStreamHighlighting() {
    // Build a process with recycle (typical anti-surge pattern)
    ProcessSystem recycleProcess = new ProcessSystem("Recycle Test Process");

    Stream feed = new Stream("Feed Gas", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    recycleProcess.add(feed);

    neqsim.process.equipment.mixer.Mixer mixer =
        new neqsim.process.equipment.mixer.Mixer("Suction Mixer");
    mixer.addStream(feed);
    recycleProcess.add(mixer);

    Compressor compressor = new Compressor("Main Compressor", mixer.getOutletStream());
    compressor.setOutletPressure(100.0, "bara");
    recycleProcess.add(compressor);

    neqsim.process.equipment.splitter.Splitter splitter =
        new neqsim.process.equipment.splitter.Splitter("Discharge Splitter",
            compressor.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] {0.9, 0.1});
    recycleProcess.add(splitter);

    // Recycle stream back to mixer
    neqsim.process.equipment.util.Recycle recycle =
        new neqsim.process.equipment.util.Recycle("Anti-Surge Recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(mixer.getOutletStream());
    recycleProcess.add(recycle);

    recycleProcess.run();

    // Export with recycle highlighting enabled (default)
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(recycleProcess)
        .setTitle("Compressor Anti-Surge System").setHighlightRecycles(true);

    String dot = exporter.toDOT();

    // Verify DOT structure
    assertNotNull(dot);
    assertTrue(dot.contains("Main Compressor"), "Should contain compressor");
    assertTrue(dot.contains("Suction Mixer"), "Should contain mixer");
    assertTrue(dot.contains("Discharge Splitter"), "Should contain splitter");

    // Verify legend includes recycle stream
    assertTrue(dot.contains("Recycle Stream"), "Legend should contain recycle stream");
  }

  @Test
  void testStreamTableDisplay() {
    // Build a simple process
    ProcessSystem simpleProcess = new ProcessSystem("Stream Table Test");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    simpleProcess.add(feed);

    Separator separator = new Separator("Separator", feed);
    simpleProcess.add(separator);

    simpleProcess.run();

    // Export with stream tables enabled
    ProcessDiagramExporter exporter =
        new ProcessDiagramExporter(simpleProcess).setDetailLevel(DiagramDetailLevel.ENGINEERING)
            .setShowStreamValues(true).setUseStreamTables(true);

    String dot = exporter.toDOT();

    // Verify DOT structure contains HTML table labels
    assertNotNull(dot);
    assertTrue(dot.contains("label=<"), "Should contain HTML labels when stream tables enabled");
    assertTrue(dot.contains("<TABLE"), "Should contain HTML TABLE elements");
  }

  @Test
  void testControlEquipmentFiltering() {
    // Build a process with control equipment
    ProcessSystem controlProcess = new ProcessSystem("Control Equipment Test");

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");
    controlProcess.add(feed);

    ThrottlingValve valve = new ThrottlingValve("Control Valve", feed);
    valve.setOutletPressure(30.0, "bara");
    controlProcess.add(valve);

    Separator separator = new Separator("Separator", valve.getOutletStream());
    controlProcess.add(separator);

    controlProcess.run();

    // Export with control equipment hidden
    ProcessDiagramExporter exporterHidden =
        new ProcessDiagramExporter(controlProcess).setShowControlEquipment(false);

    String dotHidden = exporterHidden.toDOT();

    // Verify valve is excluded when showControlEquipment=false
    assertNotNull(dotHidden);
    assertFalse(dotHidden.contains("\"Control Valve\""),
        "Control valve should be hidden when showControlEquipment=false");

    // Export with control equipment shown (default)
    ProcessDiagramExporter exporterShown =
        new ProcessDiagramExporter(controlProcess).setShowControlEquipment(true);

    String dotShown = exporterShown.toDOT();

    // Verify valve is included when showControlEquipment=true
    assertTrue(dotShown.contains("Control Valve"),
        "Control valve should be shown when showControlEquipment=true");
  }

  @Test
  void testEquipmentVisualStylesForAllCategories() {
    // Test that all major equipment categories have visual styles defined
    String[] equipmentTypes = {"Separator", "ThreePhaseSeparator", "Compressor", "Pump", "Expander",
        "HeatExchanger", "Cooler", "Heater", "ThrottlingValve", "Mixer", "Splitter", "Stream",
        "DistillationColumn", "Reactor", "Recycle", "Adjuster", "Calculator", "Flare", "Ejector",
        "Filter", "Membrane", "Tank", "Pipeline", "Well"};

    for (String type : equipmentTypes) {
      EquipmentVisualStyle style = EquipmentVisualStyle.getStyle(type);
      assertNotNull(style, "Style should not be null for: " + type);
      assertNotNull(style.toGraphvizAttributes("Test Label"),
          "Graphviz attributes should not be null for: " + type);
    }
  }

  @Test
  void testEnhancedLegendWithAllPhases() {
    // Export a process and check legend has all phase types
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("Separator", feed);
    process.add(separator);

    process.run();

    ProcessDiagramExporter exporter =
        new ProcessDiagramExporter(process).setShowLegend(true).setHighlightRecycles(true);

    String dot = exporter.toDOT();

    // Verify legend contains all phase types
    assertTrue(dot.contains("Gas Stream"), "Legend should contain gas stream");
    assertTrue(dot.contains("Liquid Stream"), "Legend should contain liquid stream");
    assertTrue(dot.contains("Oil Stream"), "Legend should contain oil stream");
    assertTrue(dot.contains("Water Stream"), "Legend should contain water stream");
    assertTrue(dot.contains("Mixed Stream"), "Legend should contain mixed stream");
    assertTrue(dot.contains("Recycle Stream"), "Legend should contain recycle stream");
  }

  @Test
  void testEquipmentEnumStyleLookup() {
    // Test that EquipmentEnum-based lookup returns correct styles
    EquipmentVisualStyle separatorStyle = EquipmentVisualStyle.getStyle(EquipmentEnum.Separator);
    assertNotNull(separatorStyle, "Should find style for Separator enum");
    assertEquals("cylinder", separatorStyle.getShape(), "Separator should be cylinder");

    EquipmentVisualStyle compressorStyle = EquipmentVisualStyle.getStyle(EquipmentEnum.Compressor);
    assertNotNull(compressorStyle, "Should find style for Compressor enum");
    assertEquals("parallelogram", compressorStyle.getShape(), "Compressor should be parallelogram");

    EquipmentVisualStyle pumpStyle = EquipmentVisualStyle.getStyle(EquipmentEnum.Pump);
    assertNotNull(pumpStyle, "Should find style for Pump enum");
    assertEquals("circle", pumpStyle.getShape(), "Pump should be circle");

    // All EquipmentEnum values should return a non-null style
    for (EquipmentEnum type : EquipmentEnum.values()) {
      EquipmentVisualStyle style = EquipmentVisualStyle.getStyle(type);
      assertNotNull(style, "Should find style for: " + type);
    }
  }

  @Test
  void testDexpiProcessUnitStyleLookup() {
    // Test that DEXPI-imported equipment uses EquipmentEnum for styling
    DexpiProcessUnit dexpiPump =
        new DexpiProcessUnit("P-101", "CentrifugalPump", EquipmentEnum.Pump, "L-100", "HC");

    EquipmentVisualStyle style = EquipmentVisualStyle.getStyleForEquipment(dexpiPump);
    assertNotNull(style, "Should find style for DEXPI pump");
    assertEquals("circle", style.getShape(), "DEXPI pump should use Pump style (circle)");

    // Test DEXPI heat exchanger
    DexpiProcessUnit dexpiHX = new DexpiProcessUnit("E-101", "PlateHeatExchanger",
        EquipmentEnum.HeatExchanger, "L-200", "HC");

    EquipmentVisualStyle hxStyle = EquipmentVisualStyle.getStyleForEquipment(dexpiHX);
    assertNotNull(hxStyle, "Should find style for DEXPI heat exchanger");
    assertEquals("rect", hxStyle.getShape(), "DEXPI HX should use HeatExchanger style (rect)");
  }

  @Test
  void testRegularEquipmentStyleLookup() {
    // Test that regular NeqSim equipment returns correct styles
    Compressor compressor = new Compressor("K-101");
    EquipmentVisualStyle compStyle = EquipmentVisualStyle.getStyleForEquipment(compressor);
    assertNotNull(compStyle, "Should find style for Compressor");
    assertEquals("parallelogram", compStyle.getShape(), "Compressor should be parallelogram");

    Separator separator = new Separator("V-101");
    EquipmentVisualStyle sepStyle = EquipmentVisualStyle.getStyleForEquipment(separator);
    assertNotNull(sepStyle, "Should find style for Separator");
    assertEquals("cylinder", sepStyle.getShape(), "Separator should be cylinder");
  }

  @Test
  void testIndustryPFDLayoutOrientation() {
    // Create a process that exercises the industry PFD layout:
    // - Feed streams should be on the LEFT
    // - Products should be on the RIGHT
    // - Gas processing at TOP
    // - Oil processing in MIDDLE
    // - Water processing at BOTTOM

    // Create a three-phase separation process
    SystemInterface multiphaseFluid = new SystemSrkEos(298.0, 50.0);
    multiphaseFluid.addComponent("methane", 0.6);
    multiphaseFluid.addComponent("n-heptane", 0.3);
    multiphaseFluid.addComponent("water", 0.1);
    multiphaseFluid.setMixingRule("classic");

    ProcessSystem threePhaseProcess = new ProcessSystem("Three-Phase Separation");

    // Feed stream (should appear on LEFT)
    Stream wellStream = new Stream("Well Stream", multiphaseFluid);
    wellStream.setFlowRate(1000.0, "kg/hr");
    threePhaseProcess.add(wellStream);

    // Three-phase separator (CENTER with gas/oil/water outlets)
    ThreePhaseSeparator sep = new ThreePhaseSeparator("3-Phase Separator", wellStream);
    threePhaseProcess.add(sep);

    // Gas compressor (should appear at TOP)
    Compressor gasComp = new Compressor("Gas Compressor", sep.getGasOutStream());
    gasComp.setOutletPressure(80.0, "bara");
    threePhaseProcess.add(gasComp);

    // Oil pump (should appear in MIDDLE)
    Pump oilPump = new Pump("Oil Pump", sep.getOilOutStream());
    oilPump.setOutletPressure(60.0, "bara");
    threePhaseProcess.add(oilPump);

    // Water pump (should appear at BOTTOM)
    Pump waterPump = new Pump("Water Pump", sep.getWaterOutStream());
    waterPump.setOutletPressure(10.0, "bara");
    threePhaseProcess.add(waterPump);

    threePhaseProcess.run();

    // Export diagram
    ProcessDiagramExporter exporter = new ProcessDiagramExporter(threePhaseProcess)
        .setTitle("Three-Phase Separation Process").setUseClusters(true);

    String dot = exporter.toDOT();

    // Verify left-to-right layout (industry standard)
    assertTrue(dot.contains("rankdir=LR"), "Should use left-to-right layout for industry PFD");

    // Verify feed streams are grouped (left side)
    assertTrue(dot.contains("rank=same"), "Should group streams by horizontal position");

    // Verify separator outlet port positioning (gravity-based)
    assertTrue(dot.contains("tailport=n"), "Gas outlet should exit from top (north)");
    assertTrue(dot.contains("tailport=e"), "Oil outlet should exit from middle (east)");
    assertTrue(dot.contains("tailport=s"), "Water outlet should exit from bottom (south)");

    // Verify phase zone comments are present
    assertTrue(dot.contains("Phase zone") || dot.contains("phase zone") || dot.contains("Gas top")
        || dot.contains("vertical phase"), "Should contain phase zone layout comments");
  }

  @Test
  void testLayoutPolicyProcessPositionAndPhaseZone() {
    // Test the new ProcessPosition and PhaseZone enums
    PFDLayoutPolicy policy = new PFDLayoutPolicy();

    // Test ProcessPosition enum values
    assertEquals(0, PFDLayoutPolicy.ProcessPosition.INLET.getHorizontalRank());
    assertEquals(1, PFDLayoutPolicy.ProcessPosition.CENTER.getHorizontalRank());
    assertEquals(2, PFDLayoutPolicy.ProcessPosition.OUTLET.getHorizontalRank());

    // Test PhaseZone enum values
    assertEquals(0, PFDLayoutPolicy.PhaseZone.GAS_TOP.getVerticalRank());
    assertEquals(1, PFDLayoutPolicy.PhaseZone.OIL_MIDDLE.getVerticalRank());
    assertEquals(2, PFDLayoutPolicy.PhaseZone.WATER_BOTTOM.getVerticalRank());
  }
}
