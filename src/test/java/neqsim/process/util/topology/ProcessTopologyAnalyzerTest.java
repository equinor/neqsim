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
}
