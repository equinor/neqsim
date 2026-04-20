package neqsim.process.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Integration tests for the operating envelope package with real NeqSim process simulations.
 */
class OperatingEnvelopeIntegrationTest {

  private static ProcessSystem process;
  private static SystemInterface feedFluid;

  @BeforeAll
  static void setUpProcess() {
    // Build a simple gas processing flowsheet: feed → separator → compressor → valve
    feedFluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    feedFluid.addComponent("methane", 0.80);
    feedFluid.addComponent("ethane", 0.10);
    feedFluid.addComponent("propane", 0.05);
    feedFluid.addComponent("n-butane", 0.03);
    feedFluid.addComponent("water", 0.02);
    feedFluid.setMixingRule("classic");

    Stream feed = new Stream("Feed Gas", feedFluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(50.0, "bara");

    Separator separator = new Separator("HP Separator", feed);

    Stream gasOut = new Stream("Gas Out", separator.getGasOutStream());

    Compressor compressor = new Compressor("Export Compressor", gasOut);
    compressor.setOutletPressure(100.0);

    Stream compOut = new Stream("Comp Out", compressor.getOutletStream());

    ThrottlingValve valve = new ThrottlingValve("Choke Valve", compOut);
    valve.setOutletPressure(60.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(gasOut);
    process.add(compressor);
    process.add(compOut);
    process.add(valve);
    process.run();
  }

  @Test
  void testProcessOperatingEnvelopeEvaluate() {
    ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
    envelope.evaluate();

    List<OperatingMargin> margins = envelope.getAllMargins();
    assertNotNull(margins);
    // Should have margins for separator level, compressor, valve
    assertTrue(margins.size() > 0, "Should detect at least one margin");

    ProcessOperatingEnvelope.EnvelopeStatus status = envelope.getOverallStatus();
    assertNotNull(status);
  }

  @Test
  void testProcessOperatingEnvelopeByEquipment() {
    ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
    envelope.evaluate();

    List<OperatingMargin> sepMargins = envelope.getMarginsByEquipment("HP Separator");
    assertNotNull(sepMargins);
    // May or may not have margins depending on whether design limits are set
  }

  @Test
  void testProcessOperatingEnvelopeCustomMargin() {
    ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);

    OperatingMargin custom = new OperatingMargin("Custom-Device", "temperature",
        OperatingMargin.MarginType.TEMPERATURE, OperatingMargin.Direction.HIGH, 80.0, 120.0, "C");
    envelope.addCustomMargin(custom);

    envelope.evaluate();
    List<OperatingMargin> margins = envelope.getAllMargins();
    boolean found = false;
    for (OperatingMargin m : margins) {
      if ("Custom-Device".equals(m.getEquipmentName())) {
        found = true;
        break;
      }
    }
    assertTrue(found, "Custom margin should appear in results");
  }

  @Test
  void testOperatingEnvelopeAgentEvaluate() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    AgentEvaluationResult result = agent.evaluate();

    assertNotNull(result);
    assertEquals(1, result.getEvaluationCycleNumber());
    assertNotNull(result.getOverallStatus());
    assertNotNull(result.getSummaryMessage());
    assertFalse(result.getSummaryMessage().isEmpty());
  }

  @Test
  void testAgentMultipleCycles() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);

    AgentEvaluationResult r1 = agent.evaluate();
    assertEquals(1, r1.getEvaluationCycleNumber());

    AgentEvaluationResult r2 = agent.evaluate();
    assertEquals(2, r2.getEvaluationCycleNumber());

    AgentEvaluationResult r3 = agent.evaluate();
    assertEquals(3, r3.getEvaluationCycleNumber());
  }

  @Test
  void testAgentWithCompositionBaseline() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);

    // Set baseline to the feed fluid
    agent.setCompositionBaseline(feedFluid);
    agent.setOperatingConditions(25.0, 50.0);

    // Create a modified fluid (more C2)
    SystemInterface newFluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    newFluid.addComponent("methane", 0.70);
    newFluid.addComponent("ethane", 0.20);
    newFluid.addComponent("propane", 0.05);
    newFluid.addComponent("n-butane", 0.03);
    newFluid.addComponent("water", 0.02);
    newFluid.setMixingRule("classic");
    agent.updateFeedFluid(newFluid);

    AgentEvaluationResult result = agent.evaluate();
    assertNotNull(result);
    // Composition impact should be computed
    // (may or may not be significant depending on thresholds)
  }

  @Test
  void testAgentReset() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    agent.evaluate();
    agent.evaluate();
    assertEquals(2, agent.getCycleNumber());

    agent.reset();
    assertEquals(0, agent.getCycleNumber());

    agent.evaluate();
    assertEquals(1, agent.getCycleNumber());
  }

  @Test
  void testAgentConfigurable() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    agent.setRSquaredThreshold(0.7);
    agent.setTrendConfidenceThreshold(0.5);
    agent.setTrackerWindowSize(30);
    agent.setAutoRunProcess(false);

    AgentEvaluationResult result = agent.evaluate();
    assertNotNull(result);
  }

  @Test
  void testCompositionChangeAnalyzer() {
    CompositionChangeAnalyzer analyzer = new CompositionChangeAnalyzer(feedFluid);

    // Test with same composition — should show no significant impact
    CompositionChangeAnalyzer.ImpactReport report = analyzer.analyzeImpact(feedFluid, 25.0, 50.0);
    assertNotNull(report);
    assertFalse(report.hasSignificantImpact(),
        "Same composition should not have significant impact");
  }

  @Test
  void testCompositionChangeAnalyzerWithDrift() {
    CompositionChangeAnalyzer analyzer = new CompositionChangeAnalyzer(feedFluid);

    // Create significantly different fluid
    SystemInterface richFluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    richFluid.addComponent("methane", 0.50);
    richFluid.addComponent("ethane", 0.25);
    richFluid.addComponent("propane", 0.15);
    richFluid.addComponent("n-butane", 0.08);
    richFluid.addComponent("water", 0.02);
    richFluid.setMixingRule("classic");

    CompositionChangeAnalyzer.ImpactReport report = analyzer.analyzeImpact(richFluid, 25.0, 50.0);
    assertNotNull(report);
    // Rich gas should shift dew point and potentially hydrate temp
    assertNotNull(report.getSignificantImpacts());
  }

  @Test
  void testCascadeImpactAnalyzerDownstream() {
    ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
    envelope.evaluate();

    CascadeImpactAnalyzer cascade = new CascadeImpactAnalyzer(process, envelope);

    List<String> downstream = cascade.getDownstreamChain("HP Separator");
    assertNotNull(downstream);
    // Should find equipment downstream of separator
  }

  @Test
  void testCascadeImpactAnalyzerThreshold() {
    ProcessOperatingEnvelope envelope = new ProcessOperatingEnvelope(process);
    envelope.evaluate();

    CascadeImpactAnalyzer cascade = new CascadeImpactAnalyzer(process, envelope);
    cascade.setSignificanceThreshold(5.0);
    assertEquals(5.0, cascade.getSignificanceThreshold(), 1e-6);
  }

  @Test
  void testDigitalTwinLoopBasic() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);

    assertTrue(loop.isEnabled());
    assertEquals(0, loop.getCycleCount());

    AgentEvaluationResult result = loop.executeCycle();
    assertNotNull(result);
    assertEquals(1, loop.getCycleCount());
    assertTrue(loop.getLastCycleDurationSeconds() >= 0);
  }

  @Test
  void testDigitalTwinLoopDisabled() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);
    loop.setEnabled(false);

    AgentEvaluationResult result = loop.executeCycle();
    assertEquals(null, result);
    assertEquals(0, loop.getCycleCount());
  }

  @Test
  void testDigitalTwinLoopWithStaticData() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);

    Map<String, Double> staticData = new HashMap<String, Double>();
    staticData.put("Feed Gas.temperature", 30.0);
    ProcessDigitalTwinLoop.StaticDataProvider provider =
        new ProcessDigitalTwinLoop.StaticDataProvider(staticData);
    assertTrue(provider.isHealthy());

    loop.setDataProvider(provider);
    AgentEvaluationResult result = loop.executeCycle();
    assertNotNull(result);
  }

  @Test
  void testDigitalTwinLoopStatusSummary() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);
    loop.executeCycle();

    Map<String, Object> status = loop.getStatusSummary();
    assertNotNull(status);
    assertEquals(true, status.get("enabled"));
    assertEquals(1, status.get("cycleCount"));
    assertNotNull(status.get("lastOverallStatus"));
  }

  @Test
  void testDigitalTwinLoopResultConsumer() {
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    ProcessDigitalTwinLoop loop = new ProcessDigitalTwinLoop(process, agent);

    final boolean[] consumed = {false};
    loop.addResultConsumer(new ProcessDigitalTwinLoop.ResultConsumer() {
      private static final long serialVersionUID = 1L;

      @Override
      public void consume(AgentEvaluationResult result) {
        consumed[0] = true;
      }
    });

    loop.executeCycle();
    assertTrue(consumed[0], "Consumer should have been called");
  }

  @Test
  void testFullEndToEndFlow() {
    // Full cycle: create agent → evaluate → dashboard → JSON
    OperatingEnvelopeAgent agent = new OperatingEnvelopeAgent(process);
    agent.setCompositionBaseline(feedFluid);
    agent.setOperatingConditions(25.0, 50.0);
    agent.updateFeedFluid(feedFluid);

    AgentEvaluationResult result = agent.evaluate();
    assertNotNull(result);

    EnvelopeDashboardData dashboard = EnvelopeDashboardData.fromResult(result);
    assertNotNull(dashboard);

    String json = dashboard.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"overallStatus\""));
    assertTrue(json.contains("\"equipmentCards\""));
    assertTrue(json.contains("\"marginGauges\""));
    assertTrue(json.contains("\"tripAlerts\""));
    assertTrue(json.contains("\"advisories\""));
  }
}
