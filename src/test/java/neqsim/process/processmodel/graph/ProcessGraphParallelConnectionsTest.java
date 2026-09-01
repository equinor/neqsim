package neqsim.process.processmodel.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Tests preservation of distinct parallel connections in the process graph. */
class ProcessGraphParallelConnectionsTest {

  @Test
  void preservesParallelMaterialStreamsBetweenTheSameEquipmentPair() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.run();

    Splitter splitter = new Splitter("parallel splitter", feed);
    splitter.setSplitFactors(new double[] { 0.4, 0.6 });
    splitter.run();

    StreamInterface firstBranch = splitter.getSplitStream(0);
    StreamInterface secondBranch = splitter.getSplitStream(1);
    Mixer mixer = new Mixer("parallel mixer");
    mixer.addStream(firstBranch);
    mixer.addStream(secondBranch);

    ProcessSystem process = new ProcessSystem("parallel material connections");
    process.add(feed);
    process.add(splitter);
    process.add(mixer);

    ProcessGraph graph = process.buildGraph();
    ProcessNode splitterNode = graph.getNode(splitter);
    ProcessNode mixerNode = graph.getNode(mixer);
    List<ProcessEdge> parallelEdges = new ArrayList<ProcessEdge>();
    for (ProcessEdge edge : splitterNode.getOutgoingEdges()) {
      if (edge.getTarget() == mixerNode) {
        parallelEdges.add(edge);
      }
    }

    assertEquals(2, parallelEdges.size());
    Set<StreamInterface> connectedStreams = Collections.newSetFromMap(new IdentityHashMap<StreamInterface, Boolean>());
    for (ProcessEdge edge : parallelEdges) {
      connectedStreams.add(edge.getStream());
    }
    assertTrue(connectedStreams.contains(firstBranch));
    assertTrue(connectedStreams.contains(secondBranch));
  }

  @Test
  void deduplicatesRepeatedDiscoveryOfTheSameMaterialStream() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 40.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    Heater heater = new Heater("heater", feed);
    ProcessSystem process = new ProcessSystem("duplicate discovery");
    process.add(feed);
    process.add(heater);

    ProcessGraph graph = process.buildGraph();
    ProcessNode feedNode = graph.getNode(feed);
    ProcessNode heaterNode = graph.getNode(heater);
    int connectionCount = 0;
    for (ProcessEdge edge : feedNode.getOutgoingEdges()) {
      if (edge.getTarget() == heaterNode) {
        connectionCount++;
      }
    }

    assertEquals(1, connectionCount);
  }
}
