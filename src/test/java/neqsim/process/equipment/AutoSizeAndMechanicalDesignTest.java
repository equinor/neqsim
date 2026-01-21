package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.GasScrubber;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Test class to verify autoSize and mechanical design work for separators, three-phase separators,
 * scrubbers, and heat exchangers.
 *
 * @author NeqSim Development Team
 */
class AutoSizeAndMechanicalDesignTest extends neqsim.NeqSimTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkCPAstatoil(298.0, 50.0);
    testFluid.addComponent("methane", 70.0);
    testFluid.addComponent("ethane", 10.0);
    testFluid.addComponent("propane", 5.0);
    testFluid.addComponent("nC10", 10.0);
    testFluid.addComponent("water", 5.0);
    testFluid.setMixingRule(10);
    testFluid.setMultiPhaseCheck(true);
  }

  @Test
  void testSeparatorAutoSizeAndMechanicalDesign() {
    Stream feed = new Stream("feed", testFluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(35.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");

    Separator separator = new Separator("test-separator", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();

    // Test autoSize
    assertFalse(separator.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    separator.autoSize(1.2);
    assertTrue(separator.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test getSizingReport
    String report = separator.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Separator Auto-Sizing Report"));
    assertTrue(report.contains("Auto-sized: true"));
    assertTrue(report.contains("Internal Diameter"));

    // Test getSizingReportJson
    String jsonReport = separator.getSizingReportJson();
    assertNotNull(jsonReport);
    assertTrue(jsonReport.contains("autoSized"));
    assertTrue(jsonReport.contains("internalDiameter_m"));

    // Test mechanical design
    separator.initMechanicalDesign();
    assertNotNull(separator.getMechanicalDesign());
    separator.getMechanicalDesign().calcDesign();
  }

  @Test
  void testThreePhaseSeparatorAutoSizeAndMechanicalDesign() {
    Stream feed = new Stream("feed", testFluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(35.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");

    ThreePhaseSeparator separator = new ThreePhaseSeparator("test-3phase-sep", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();

    // Test autoSize - inherited from Separator
    assertFalse(separator.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    separator.autoSize(1.2);
    assertTrue(separator.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test getSizingReport - inherited from Separator
    String report = separator.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Auto-sized: true"));

    // Test mechanical design
    separator.initMechanicalDesign();
    assertNotNull(separator.getMechanicalDesign());
  }

  @Test
  void testGasScrubberAutoSizeAndMechanicalDesign() {
    // Use gas-only fluid for scrubber
    SystemInterface gasFluid = new SystemSrkEos(298.0, 50.0);
    gasFluid.addComponent("methane", 90.0);
    gasFluid.addComponent("ethane", 5.0);
    gasFluid.addComponent("propane", 3.0);
    gasFluid.addComponent("nC10", 2.0);
    gasFluid.setMixingRule(2);
    gasFluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", gasFluid);
    feed.setPressure(70.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.setFlowRate(2.0, "MSm3/day");

    GasScrubber scrubber = new GasScrubber("test-scrubber", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(scrubber);
    process.run();

    // Test autoSize - inherited from Separator
    assertFalse(scrubber.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    scrubber.autoSize(1.3);
    assertTrue(scrubber.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test orientation - scrubbers should be vertical by default
    assertEquals("vertical", scrubber.getOrientation());

    // Test getSizingReport - inherited from Separator
    String report = scrubber.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Auto-sized: true"));

    // Test mechanical design - uses GasScrubberMechanicalDesign
    assertNotNull(scrubber.getMechanicalDesign());
  }

  @Test
  void testHeaterAutoSizeAndMechanicalDesign() {
    Stream feed = new Stream("feed", testFluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(25.0, "C");
    feed.setFlowRate(500.0, "kg/hr");

    Heater heater = new Heater("test-heater", feed);
    heater.setOutTemperature(60.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    // Test autoSize
    assertFalse(heater.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    heater.autoSize(1.2);
    assertTrue(heater.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test getSizingReport
    String report = heater.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Heater/Cooler Auto-Sizing Report"));
    assertTrue(report.contains("Auto-sized: true"));
    assertTrue(report.contains("Duty"));

    // Test getSizingReportJson
    String jsonReport = heater.getSizingReportJson();
    assertNotNull(jsonReport);
    assertTrue(jsonReport.contains("autoSized"));
    assertTrue(jsonReport.contains("duty_kW"));

    // Test mechanical design
    assertNotNull(heater.getMechanicalDesign());
    heater.getMechanicalDesign().calcDesign();
  }

  @Test
  void testCoolerAutoSizeAndMechanicalDesign() {
    Stream feed = new Stream("feed", testFluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(80.0, "C");
    feed.setFlowRate(500.0, "kg/hr");

    Cooler cooler = new Cooler("test-cooler", feed);
    cooler.setOutTemperature(30.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);
    process.run();

    // Test autoSize - inherited from Heater
    assertFalse(cooler.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    cooler.autoSize(1.2);
    assertTrue(cooler.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test getSizingReport - inherited from Heater
    String report = cooler.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Auto-sized: true"));

    // Test mechanical design - inherited from Heater
    assertNotNull(cooler.getMechanicalDesign());
  }

  @Test
  void testHeatExchangerAutoSizeAndMechanicalDesign() {
    // Create hot stream
    SystemInterface hotFluid = new SystemSrkEos(373.15, 30.0);
    hotFluid.addComponent("methane", 80.0);
    hotFluid.addComponent("ethane", 15.0);
    hotFluid.addComponent("propane", 5.0);
    hotFluid.setMixingRule(2);

    Stream hotStream = new Stream("hot-stream", hotFluid);
    hotStream.setPressure(30.0, "bara");
    hotStream.setTemperature(100.0, "C");
    hotStream.setFlowRate(1000.0, "kg/hr");

    // Create cold stream
    SystemInterface coldFluid = new SystemSrkEos(293.15, 30.0);
    coldFluid.addComponent("methane", 80.0);
    coldFluid.addComponent("ethane", 15.0);
    coldFluid.addComponent("propane", 5.0);
    coldFluid.setMixingRule(2);

    Stream coldStream = new Stream("cold-stream", coldFluid);
    coldStream.setPressure(30.0, "bara");
    coldStream.setTemperature(20.0, "C");
    coldStream.setFlowRate(800.0, "kg/hr");

    // Create heat exchanger
    HeatExchanger hx = new HeatExchanger("test-hx", hotStream, coldStream);
    hx.setUAvalue(5000.0);

    ProcessSystem process = new ProcessSystem();
    process.add(hotStream);
    process.add(coldStream);
    process.add(hx);
    process.run();

    // Test autoSize
    assertFalse(hx.isAutoSized(), "Should not be auto-sized before calling autoSize()");
    hx.autoSize(1.2);
    assertTrue(hx.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Test getSizingReport - overridden for two-stream heat exchanger
    String report = hx.getSizingReport();
    assertNotNull(report);
    assertTrue(report.contains("Heat Exchanger Auto-Sizing Report"));
    assertTrue(report.contains("Auto-sized: true"));
    assertTrue(report.contains("Hot Side"));
    assertTrue(report.contains("Cold Side"));
    assertTrue(report.contains("UA Value"));
    assertTrue(report.contains("LMTD"));

    // Test mechanical design
    assertNotNull(hx.getMechanicalDesign());
    hx.getMechanicalDesign().calcDesign();
  }

  @Test
  void testAutoSizeWithCompanyStandards() {
    Stream feed = new Stream("feed", testFluid);
    feed.setPressure(50.0, "bara");
    feed.setTemperature(35.0, "C");
    feed.setFlowRate(1.0, "MSm3/day");

    Separator separator = new Separator("test-separator-equinor", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.run();

    // Test autoSize with company standards
    separator.autoSize("Equinor", "TR2000");
    assertTrue(separator.isAutoSized(), "Should be auto-sized after calling autoSize()");

    // Verify mechanical design was configured
    assertNotNull(separator.getMechanicalDesign());
    assertNotNull(separator.getMechanicalDesign().getCompanySpecificDesignStandards());
    assertEquals("Equinor", separator.getMechanicalDesign().getCompanySpecificDesignStandards());
  }
}
