package neqsim.process.processmodel.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessModelGraphvizExporter;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.graph.ProcessEdge;
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Golden topology-equivalence cases shared by canonical and legacy diagram projections. */
class ProcessDiagramTopologyEquivalenceTest {

  @Test
  void simpleTrainHasEquivalentCanonicalAndDotTopology(@TempDir Path tempDir) throws IOException {
    ProcessDiagramGoldenFixtures.Fixture fixture = ProcessDiagramGoldenFixtures.simpleTrain();
    List<String> expected = withRecycleFlag(fixture.getMaterialConnections(), false);

    assertSystemTopology(expected, expected, fixture.getProcessSystem(), "GOLDEN-SIMPLE",
        tempDir.resolve("simple.dot"));
  }

  @Test
  void parallelBranchManifestPreservesConnectionMultiplicity(@TempDir Path tempDir) throws IOException {
    ProcessDiagramGoldenFixtures.Fixture fixture = ProcessDiagramGoldenFixtures.parallelBranchTrain();
    List<String> expected = withRecycleFlag(fixture.getMaterialConnections(), false);

    assertSystemTopology(expected, expected, fixture.getProcessSystem(), "GOLDEN-BRANCH",
        tempDir.resolve("parallel-branch.dot"));
  }

  @Test
  void parallelRecycleTrainHasEquivalentCanonicalAndDotTopology(@TempDir Path tempDir) throws IOException {
    Stream feed = createFeed("branch feed");
    feed.run();
    Splitter splitter = new Splitter("branch splitter", feed);
    splitter.setSplitFactors(new double[] { 0.4, 0.6 });
    splitter.run();
    StreamInterface firstBranch = splitter.getSplitStream(0);
    StreamInterface secondBranch = splitter.getSplitStream(1);
    Mixer branchMixer = new Mixer("branch mixer");
    branchMixer.addStream(firstBranch);
    branchMixer.addStream(secondBranch);

    Stream tear = new Stream("tear seed", feed.getThermoSystem().clone());
    Mixer loopMixer = new Mixer("loop mixer");
    loopMixer.addStream(branchMixer.getOutletStream());
    loopMixer.addStream(tear);
    Separator separator = new Separator("loop separator", loopMixer.getOutletStream());
    Recycle recycle = new Recycle("liquid recycle");
    recycle.addStream(separator.getLiquidOutStream());
    recycle.setOutletStream(tear);

    ProcessSystem process = new ProcessSystem("parallel recycle train");
    process.add(feed);
    process.add(splitter);
    process.add(branchMixer);
    process.add(tear);
    process.add(loopMixer);
    process.add(separator);
    process.add(recycle);

    List<String> expected = sorted("branch feed->branch splitter|false", "branch splitter->branch mixer|false",
        "branch splitter->branch mixer|false", "branch mixer->loop mixer|false", "liquid recycle->tear seed|true",
        "tear seed->loop mixer|false", "loop mixer->loop separator|false", "loop separator->liquid recycle|true");

    // The registered tear stream is an explicit mutable-state barrier in both execution and
    // diagram projections: liquid recycle -> tear seed -> loop mixer.
    List<String> legacyExpected = sorted("branch feed->branch splitter|false", "branch splitter->branch mixer|false",
        "branch splitter->branch mixer|false", "branch mixer->loop mixer|false", "liquid recycle->tear seed|true",
        "tear seed->loop mixer|false", "loop mixer->loop separator|false", "loop separator->liquid recycle|true");

    assertSystemTopology(expected, legacyExpected, process, "GOLDEN-RECYCLE", tempDir.resolve("parallel-recycle.dot"));
  }

  @Test
  void multiAreaPlantHasOneEquivalentPlantWideTopology() {
    Stream feed = createFeed("upstream feed");
    Heater upstreamHeater = new Heater("upstream heater", feed);
    ProcessSystem upstream = new ProcessSystem("upstream system");
    upstream.add(feed);
    upstream.add(upstreamHeater);

    Heater downstreamHeater = new Heater("downstream heater", upstreamHeater.getOutletStream());
    ProcessSystem downstream = new ProcessSystem("downstream system");
    downstream.add(downstreamHeater);

    ProcessModel plant = new ProcessModel();
    plant.add("Upstream", upstream);
    plant.add("Downstream", downstream);

    List<String> expected = sorted("upstream feed->upstream heater|false", "upstream heater->downstream heater|false");
    EngineeringGraph canonical = ProcessDiagramGraphAdapter.fromProcessModel(plant, "GOLDEN-MULTI", "A").getGraph();

    assertEquals(expected, canonicalConnections(canonical));
    assertEquals(2, countNodeKind(canonical, EngineeringNode.Kind.AREA));

    ProcessModelGraphvizExporter graphviz = new ProcessModelGraphvizExporter(plant);
    String combinedDot = graphviz.toDot();
    assertTrue(combinedDot.contains("\"Upstream::upstream feed\" -> \"Upstream::upstream heater\""));
    assertTrue(combinedDot.contains("\"Upstream::upstream heater\" -> \"Downstream::downstream heater\""));
    assertEquals(Arrays.asList("Upstream", "Downstream"), new ArrayList<String>(graphviz.toAreaDots().keySet()));
    assertDotContainsConnections(graphviz.toAreaDots().get("Upstream"), sorted("upstream feed->upstream heater|false"));
  }

  private static void assertSystemTopology(List<String> expected, List<String> legacyExpected, ProcessSystem process,
      String plantId, Path legacyDotPath) throws IOException {
    ProcessGraph executionGraph = ProcessGraphBuilder.buildGraph(process);
    EngineeringGraph canonical = ProcessDiagramGraphAdapter.fromProcessSystem(process, plantId, "A").getGraph();

    assertEquals(expected, processConnections(executionGraph));
    assertEquals(expected, canonicalConnections(canonical));
    assertDotContainsConnections(process.toDOT(), expected);

    process.exportToGraphviz(legacyDotPath.toString());
    String legacyDot = new String(Files.readAllBytes(legacyDotPath), StandardCharsets.UTF_8);
    assertDotContainsConnections(legacyDot, legacyExpected);
  }

  private static List<String> processConnections(ProcessGraph graph) {
    List<String> connections = new ArrayList<String>();
    for (ProcessEdge edge : graph.getEdges()) {
      connections.add(connectionKey(edge.getSource().getName(), edge.getTarget().getName(), edge.isRecycle()));
    }
    Collections.sort(connections);
    return connections;
  }

  private static List<String> canonicalConnections(EngineeringGraph graph) {
    List<String> connections = new ArrayList<String>();
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() != EngineeringNode.Kind.PIPE_SEGMENT) {
        continue;
      }
      connections.add(connectionKey(String.valueOf(node.getProperties().get("sourceEquipment")),
          String.valueOf(node.getProperties().get("targetEquipment")),
          Boolean.TRUE.equals(node.getProperties().get("recycle"))));
    }
    Collections.sort(connections);
    return connections;
  }

  private static void assertDotContainsConnections(String dot, List<String> expected) {
    Map<String, Integer> requiredCounts = new LinkedHashMap<String, Integer>();
    for (String expectedConnection : expected) {
      String pair = expectedConnection.substring(0, expectedConnection.lastIndexOf('|'));
      Integer count = requiredCounts.get(pair);
      requiredCounts.put(pair, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
    }
    for (Map.Entry<String, Integer> entry : requiredCounts.entrySet()) {
      String[] endpoints = entry.getKey().split("->", -1);
      String edgeToken = "\"" + endpoints[0] + "\" -> \"" + endpoints[1] + "\"";
      assertTrue(countOccurrences(dot, edgeToken) >= entry.getValue().intValue(),
          "DOT projection lost expected connection " + entry.getKey());
    }
  }

  private static int countOccurrences(String text, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = text.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }

  private static int countNodeKind(EngineeringGraph graph, EngineeringNode.Kind kind) {
    int count = 0;
    for (EngineeringNode node : graph.getNodes().values()) {
      if (node.getKind() == kind) {
        count++;
      }
    }
    return count;
  }

  private static String connectionKey(String source, String target, boolean recycle) {
    return source + "->" + target + "|" + recycle;
  }

  private static List<String> sorted(String... values) {
    List<String> result = new ArrayList<String>(Arrays.asList(values));
    Collections.sort(result);
    return result;
  }

  private static List<String> withRecycleFlag(List<String> connections, boolean recycle) {
    List<String> result = new ArrayList<String>();
    for (String connection : connections) {
      result.add(connection + "|" + recycle);
    }
    Collections.sort(result);
    return result;
  }

  private static Stream createFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    return feed;
  }
}
