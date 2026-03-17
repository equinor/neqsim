package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import neqsim.NeqSimTest;
import neqsim.process.processmodel.dexpi.DexpiTopologyResolver.ResolvedTopology;
import neqsim.process.processmodel.dexpi.DexpiTopologyResolver.TopologyEdge;

/**
 * Tests for {@link DexpiTopologyResolver}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiTopologyResolverTest extends NeqSimTest {

  /**
   * Tests topology resolution with a simple two-equipment, one-connection graph.
   *
   * @throws Exception if XML parsing or topology resolution fails
   */
  @Test
  public void testSimpleTopology() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\">"
        + "      <Nozzle ID=\"Nozzle-1\"/>" + "      <Nozzle ID=\"Nozzle-2\"/>"
        + "    </CentrifugalPump>" + "  </Equipment>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"HX-1\">"
        + "      <Nozzle ID=\"Nozzle-3\"/>" + "      <Nozzle ID=\"Nozzle-4\"/>"
        + "    </PlateHeatExchanger>" + "  </Equipment>"
        + "  <PipingNetworkSystem ID=\"PNS-1\" ComponentClass=\"PipingNetworkSystem\">"
        + "    <PipingNetworkSegment ID=\"Seg-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"Nozzle-2\" FromNode=\"1\" ToID=\"Nozzle-3\" ToNode=\"1\"/>"
        + "    </PipingNetworkSegment>" + "  </PipingNetworkSystem>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    assertNotNull(topology);
    assertEquals(2, topology.getOrderedEquipmentIds().size());
    assertEquals(1, topology.getEdges().size());

    TopologyEdge edge = topology.getEdges().get(0);
    assertEquals("Pump-1", edge.getSourceEquipmentId());
    assertEquals("HX-1", edge.getTargetEquipmentId());

    // Pump should come before HX in topological order
    List<String> order = topology.getOrderedEquipmentIds();
    assertTrue(order.indexOf("Pump-1") < order.indexOf("HX-1"));
  }

  /**
   * Tests topology resolution with a piping component inline in a segment.
   *
   * @throws Exception if XML parsing or topology resolution fails
   */
  @Test
  public void testTopologyWithPipingComponent() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <Tank ComponentClass=\"Tank\" ID=\"Tank-1\">" + "      <Nozzle ID=\"Nozzle-1\"/>"
        + "    </Tank>" + "  </Equipment>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\">"
        + "      <Nozzle ID=\"Nozzle-2\"/>" + "    </CentrifugalPump>" + "  </Equipment>"
        + "  <PipingNetworkSystem ID=\"PNS-1\" ComponentClass=\"PipingNetworkSystem\">"
        + "    <PipingNetworkSegment ID=\"Seg-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <PipingComponent ID=\"PC-wrapper\">"
        + "        <BallValve ComponentClass=\"BallValve\" ID=\"BV-1\"/>"
        + "      </PipingComponent>"
        + "      <Connection FromID=\"Nozzle-1\" FromNode=\"1\" ToID=\"BV-1\" ToNode=\"1\"/>"
        + "    </PipingNetworkSegment>"
        + "    <PipingNetworkSegment ID=\"Seg-2\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"BV-1\" FromNode=\"1\" ToID=\"Nozzle-2\" ToNode=\"1\"/>"
        + "    </PipingNetworkSegment>" + "  </PipingNetworkSystem>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    assertNotNull(topology);
    // Should have a collapsed edge Tank-1 -> Pump-1
    assertTrue(topology.getEdges().size() >= 1);

    boolean foundTankToPump = false;
    for (TopologyEdge edge : topology.getEdges()) {
      if ("Tank-1".equals(edge.getSourceEquipmentId())
          && "Pump-1".equals(edge.getTargetEquipmentId())) {
        foundTankToPump = true;
      }
    }
    assertTrue(foundTankToPump, "Should find collapsed edge Tank-1 -> Pump-1");
  }

  /**
   * Tests nozzle-to-equipment mapping.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testNozzleToEquipmentMapping() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\">"
        + "      <Nozzle ID=\"N-1\"/>" + "      <Nozzle ID=\"N-2\"/>" + "    </CentrifugalPump>"
        + "  </Equipment>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    Map<String, String> nozzleMap = topology.getNozzleToEquipment();
    assertEquals("Pump-1", nozzleMap.get("N-1"));
    assertEquals("Pump-1", nozzleMap.get("N-2"));
  }

  /**
   * Tests that equipment elements are correctly identified.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testEquipmentElements() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <Tank ComponentClass=\"Tank\" ID=\"Tank-1\"/>" + "  </Equipment>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\"/>"
        + "  </Equipment>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    assertEquals(2, topology.getEquipmentElements().size());
    assertNotNull(topology.getEquipmentElements().get("Tank-1"));
    assertNotNull(topology.getEquipmentElements().get("Pump-1"));
  }

  /**
   * Tests that sub-components (Chamber, TubeBundle, etc.) are not treated as equipment.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testSubComponentsSkipped() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <ShellAndTubeHeatExchanger ComponentClass=\"ShellAndTubeHeatExchanger\" ID=\"HX-1\">"
        + "      <Chamber ComponentClass=\"Chamber\" ID=\"Chamber-1\">"
        + "        <Nozzle ID=\"N-1\"/>" + "      </Chamber>"
        + "      <TubeBundle ComponentClass=\"TubeBundle\" ID=\"TB-1\"/>"
        + "    </ShellAndTubeHeatExchanger>" + "  </Equipment>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    // Only the HX should be found, not Chamber or TubeBundle
    assertEquals(1, topology.getEquipmentElements().size());
    assertNotNull(topology.getEquipmentElements().get("HX-1"));
    // Nozzle in Chamber should still be mapped to HX-1
    assertEquals("HX-1", topology.getNozzleToEquipment().get("N-1"));
  }

  /**
   * Tests incoming and outgoing edge queries.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testEdgeQueries() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <Tank ComponentClass=\"Tank\" ID=\"Tank-1\">" + "      <Nozzle ID=\"N-1\"/>"
        + "    </Tank>" + "  </Equipment>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\">"
        + "      <Nozzle ID=\"N-2\"/>" + "      <Nozzle ID=\"N-3\"/>" + "    </CentrifugalPump>"
        + "  </Equipment>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"HX-1\">"
        + "      <Nozzle ID=\"N-4\"/>" + "    </PlateHeatExchanger>" + "  </Equipment>"
        + "  <PipingNetworkSystem ID=\"PNS-1\" ComponentClass=\"PipingNetworkSystem\">"
        + "    <PipingNetworkSegment ID=\"Seg-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"N-1\" ToID=\"N-2\"/>" + "    </PipingNetworkSegment>"
        + "    <PipingNetworkSegment ID=\"Seg-2\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"N-3\" ToID=\"N-4\"/>" + "    </PipingNetworkSegment>"
        + "  </PipingNetworkSystem>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    assertEquals(2, topology.getEdges().size());
    assertEquals(1, topology.getOutgoingEdges("Tank-1").size());
    assertEquals(1, topology.getIncomingEdges("Pump-1").size());
    assertEquals(1, topology.getOutgoingEdges("Pump-1").size());
    assertEquals(0, topology.getOutgoingEdges("HX-1").size());
    assertEquals(1, topology.getIncomingEdges("HX-1").size());
  }

  /**
   * Tests that a linear topology has no cycle.
   *
   * @throws Exception if XML parsing or topology resolution fails
   */
  @Test
  public void testHasCycleReturnsFalseForLinear() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"Pump-1\">"
        + "      <Nozzle ID=\"N-1\"/>" + "      <Nozzle ID=\"N-2\"/>" + "    </CentrifugalPump>"
        + "  </Equipment>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"HX-1\">"
        + "      <Nozzle ID=\"N-3\"/>" + "      <Nozzle ID=\"N-4\"/>" + "    </PlateHeatExchanger>"
        + "  </Equipment>"
        + "  <PipingNetworkSystem ID=\"PNS-1\" ComponentClass=\"PipingNetworkSystem\">"
        + "    <PipingNetworkSegment ID=\"Seg-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"N-2\" FromNode=\"1\" ToID=\"N-3\" ToNode=\"1\"/>"
        + "    </PipingNetworkSegment>" + "  </PipingNetworkSystem>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);
    assertFalse(topology.hasCycle(), "Linear topology should not have a cycle");
  }

  /**
   * Tests that an empty topology has no cycle.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testHasCycleReturnsFalseForEmpty() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><PlantModel/>";
    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);
    assertFalse(topology.hasCycle(), "Empty topology should not have a cycle");
  }

  /**
   * Tests that a cyclic topology (A -> B -> A) is correctly detected.
   *
   * @throws Exception if XML parsing or topology resolution fails
   */
  @Test
  public void testHasCycleReturnsTrueForCycle() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<PlantModel>" + "  <Equipment>"
        + "    <CentrifugalPump ComponentClass=\"CentrifugalPump\" ID=\"A\">"
        + "      <Nozzle ID=\"N-A-in\"/>" + "      <Nozzle ID=\"N-A-out\"/>"
        + "    </CentrifugalPump>" + "  </Equipment>" + "  <Equipment>"
        + "    <PlateHeatExchanger ComponentClass=\"PlateHeatExchanger\" ID=\"B\">"
        + "      <Nozzle ID=\"N-B-in\"/>" + "      <Nozzle ID=\"N-B-out\"/>"
        + "    </PlateHeatExchanger>" + "  </Equipment>"
        + "  <PipingNetworkSystem ID=\"PNS-1\" ComponentClass=\"PipingNetworkSystem\">"
        + "    <PipingNetworkSegment ID=\"Seg-1\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"N-A-out\" ToID=\"N-B-in\"/>" + "    </PipingNetworkSegment>"
        + "    <PipingNetworkSegment ID=\"Seg-2\" ComponentClass=\"PipingNetworkSegment\">"
        + "      <Connection FromID=\"N-B-out\" ToID=\"N-A-in\"/>" + "    </PipingNetworkSegment>"
        + "  </PipingNetworkSystem>" + "</PlantModel>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);
    assertTrue(topology.hasCycle(), "Cyclic topology A -> B -> A should be detected");
  }

  /**
   * Tests empty document handling.
   *
   * @throws Exception if XML parsing fails
   */
  @Test
  public void testEmptyDocument() throws Exception {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><PlantModel/>";

    Document doc = parseXml(xml);
    ResolvedTopology topology = DexpiTopologyResolver.resolve(doc);

    assertNotNull(topology);
    assertTrue(topology.getOrderedEquipmentIds().isEmpty());
    assertTrue(topology.getEdges().isEmpty());
  }

  /**
   * Parses XML string into a Document for testing.
   *
   * @param xml the XML string
   * @return the parsed Document
   * @throws Exception if parsing fails
   */
  private Document parseXml(String xml) throws Exception {
    File tempFile = File.createTempFile("dexpi-test", ".xml");
    tempFile.deleteOnExit();
    try (FileWriter writer = new FileWriter(tempFile)) {
      writer.write(xml);
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setNamespaceAware(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document document = builder.parse(tempFile);
    document.getDocumentElement().normalize();
    return document;
  }
}
