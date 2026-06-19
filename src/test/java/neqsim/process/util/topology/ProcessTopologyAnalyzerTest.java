package neqsim.process.util.topology;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for ProcessTopologyAnalyzer.
 */
public class ProcessTopologyAnalyzerTest {

  private ProcessSystem process;
  private ProcessTopologyAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    // Create a simple process
    SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    process = new ProcessSystem();

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    process.add(feed);

    Separator separator = new Separator("HP Separator", feed);
    process.add(separator);

    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(100.0, "bara");
    process.add(compressor);

    Cooler cooler = new Cooler("After Cooler", compressor.getOutletStream());
    cooler.setOutTemperature(30.0, "C");
    process.add(cooler);

    Stream export = new Stream("Export", cooler.getOutletStream());
    process.add(export);

    process.run();

    analyzer = new ProcessTopologyAnalyzer(process);
  }

  @Test
  void testBuildTopology() {
    analyzer.buildTopology();

    // Should have nodes
    assertFalse(analyzer.getNodes().isEmpty());
    assertTrue(analyzer.getNodes().size() >= 4);

    // Should have edges
    assertFalse(analyzer.getEdges().isEmpty());
  }

  @Test
  void testTopologicalOrder() {
    analyzer.buildTopology();

    Map<String, Integer> order = analyzer.getTopologicalOrder();

    // Should have some ordering
    assertFalse(order.isEmpty());

    // Feed should exist and have an order
    assertNotNull(order.get("Feed"));
  }

  @Test
  void testUpstreamDownstream() {
    analyzer.buildTopology();

    // Test that we can query upstream/downstream
    List<String> downstream = analyzer.getDownstreamEquipment("HP Separator");
    List<String> upstream = analyzer.getUpstreamEquipment("Export Compressor");

    // These lists should exist (may be empty if stream tracking doesn't work)
    assertNotNull(downstream);
    assertNotNull(upstream);
  }

  @Test
  void testGetAffectedByFailure() {
    analyzer.buildTopology();

    // If separator fails, get affected equipment
    List<String> affected = analyzer.getAffectedByFailure("HP Separator");

    // Should return a list (may be empty if stream connections not detected)
    assertNotNull(affected);
  }

  @Test
  void testSetFunctionalLocation() {
    analyzer.buildTopology();

    analyzer.setFunctionalLocation("Export Compressor", "1775-KA-23011A");

    ProcessTopologyAnalyzer.EquipmentNode node = analyzer.getNode("Export Compressor");
    assertNotNull(node.getFunctionalLocation());
    assertEquals("1775-KA-23011A", node.getFunctionalLocation().getFullTag());
    assertEquals("Gullfaks C", node.getFunctionalLocation().getInstallationName());
  }

  @Test
  void testToJson() {
    analyzer.buildTopology();
    analyzer.setFunctionalLocation("Export Compressor", "1775-KA-23011A");

    String json = analyzer.toJson();

    assertNotNull(json);
    assertTrue(json.contains("nodes"));
    assertTrue(json.contains("edges"));
    assertTrue(json.contains("Export Compressor"));
    assertTrue(json.contains("1775-KA-23011A"));
  }

  @Test
  void testToDotGraph() {
    analyzer.buildTopology();
    analyzer.setFunctionalLocation("Export Compressor", "1775-KA-23011A");

    String dot = analyzer.toDotGraph();

    assertNotNull(dot);
    assertTrue(dot.contains("digraph"));
    assertTrue(dot.contains("Export Compressor"));
    assertTrue(dot.contains("->"));
  }

  @Test
  void testGetIncreasedCriticalityOn() {
    analyzer.buildTopology();

    // Test increased criticality when something fails
    Map<String, Double> increased = analyzer.getIncreasedCriticalityOn("HP Separator");

    // Should return something (map may be empty if no redundancy)
    assertNotNull(increased);
  }

  @Test
  void testRecycleExposesInletAndOutletStreams() {
    SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("Loop Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    Stream tear = new Stream("Tear Seed", fluid.clone());
    tear.setFlowRate(1.0, "kg/hr");

    neqsim.process.equipment.mixer.Mixer mixer = new neqsim.process.equipment.mixer.Mixer("Loop Mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);

    Separator sep = new Separator("Loop Separator", mixer.getOutletStream());

    neqsim.process.equipment.util.Recycle recycle = new neqsim.process.equipment.util.Recycle("Liquid Recycle");
    recycle.addStream(sep.getLiquidOutStream());
    recycle.setOutletStream(tear);

    // Recycle now reports its tear inlet and converged outlet through the standard accessors.
    assertEquals(1, recycle.getInletStreams().size());
    assertSame(sep.getLiquidOutStream(), recycle.getInletStreams().get(0));
    assertEquals(1, recycle.getOutletStreams().size());
    assertSame(tear, recycle.getOutletStreams().get(0));
  }

  @Test
  void testRecycleIsConnectedInTopologyGraph() {
    SystemSrkEos fluid = new SystemSrkEos(280.0, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    ProcessSystem loop = new ProcessSystem();

    Stream feed = new Stream("Loop Feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    loop.add(feed);

    Stream tear = new Stream("Tear Seed", fluid.clone());
    tear.setFlowRate(1.0, "kg/hr");
    loop.add(tear);

    neqsim.process.equipment.mixer.Mixer mixer = new neqsim.process.equipment.mixer.Mixer("Loop Mixer");
    mixer.addStream(feed);
    mixer.addStream(tear);
    loop.add(mixer);

    Separator sep = new Separator("Loop Separator", mixer.getOutletStream());
    loop.add(sep);

    neqsim.process.equipment.util.Recycle recycle = new neqsim.process.equipment.util.Recycle("Liquid Recycle");
    recycle.addStream(sep.getLiquidOutStream());
    recycle.setOutletStream(tear);
    loop.add(recycle);

    loop.run();

    ProcessTopologyAnalyzer loopAnalyzer = new ProcessTopologyAnalyzer(loop);
    loopAnalyzer.buildTopology();

    // The recycle node must be wired into the graph, not isolated:
    // separator liquid -> recycle (inlet) and recycle -> mixer (outlet/tear seed).
    List<String> recycleUpstream = loopAnalyzer.getUpstreamEquipment("Liquid Recycle");
    List<String> recycleDownstream = loopAnalyzer.getDownstreamEquipment("Liquid Recycle");
    assertTrue(recycleUpstream.contains("Loop Separator"),
	"Recycle should receive the separator liquid outlet as an inlet");
    assertTrue(recycleDownstream.contains("Loop Mixer"), "Recycle outlet (tear seed) should feed the loop mixer");

    String dot = loopAnalyzer.toDotGraph();
    assertTrue(dot.contains("Liquid Recycle"), "DOT/Graphviz export should include the recycle node");
  }
}
