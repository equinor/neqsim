package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.thermo.system.SystemSrkEos;

/** Protects signed loop corrections and local mass balance in documented network examples. */
class LoopDetectorFlowConservationTest extends NeqSimTest {
  @Test
  void everyCycleCorrectionHasZeroIncidenceAtEveryNode() {
    String[][] edges = { { "A", "B", "AB" }, { "B", "C", "BC" }, { "C", "A", "CA" }, { "C", "D", "CD" },
        { "D", "B", "DB" }, { "B", "A", "parallel" } };
    for (int reversed = 0; reversed < 2; reversed++) {
      LoopDetector detector = new LoopDetector();
      Map<String, String[]> physical = new HashMap<String, String[]>();
      for (String[] edge : edges) {
        String from = edge[reversed];
        String to = edge[1 - reversed];
        detector.addEdge(from, to, edge[2]);
        physical.put(edge[2], new String[] { from, to });
      }
      List<NetworkLoop> loops = detector.findLoops();
      assertEquals(3, loops.size());
      for (NetworkLoop loop : loops) {
        Map<String, Integer> incidence = new HashMap<String, Integer>();
        for (NetworkLoop.LoopMember member : loop.getMembers()) {
          String[] edge = physical.get(member.getPipeName());
          incidence.put(edge[0], incidence.getOrDefault(edge[0], 0) - member.getDirection());
          incidence.put(edge[1], incidence.getOrDefault(edge[1], 0) + member.getDirection());
        }
        for (Map.Entry<String, Integer> node : incidence.entrySet()) {
          assertEquals(0, node.getValue().intValue(), "Loop correction must conserve mass at " + node.getKey());
        }
      }
    }
  }

  @Test
  void hardyCrossTrianglePreservesBothCustomerDemands() {
    LoopedPipeNetwork network = network(LoopedPipeNetwork.SolverType.HARDY_CROSS, false);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(40.0, network.getPipeFlowRate("AB") - network.getPipeFlowRate("BC"), 1e-6);
    assertEquals(60.0, network.getPipeFlowRate("BC") - network.getPipeFlowRate("CA"), 1e-6);
    assertEquals(100.0, network.getPipeFlowRate("AB") - network.getPipeFlowRate("CA"), 1e-6);
  }

  @Test
  void twoLoopHardyCrossFlowsHaveSameSignsAndDemandsAsNewtonRaphson() {
    LoopedPipeNetwork hardy = network(LoopedPipeNetwork.SolverType.HARDY_CROSS, true);
    LoopedPipeNetwork newton = network(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON, true);
    hardy.run();
    newton.run();
    assertTrue(hardy.isConverged());
    assertTrue(newton.isConverged());
    assertEquals(0.0, hardy.getPipeFlowRate("AB") + hardy.getPipeFlowRate("DB") - hardy.getPipeFlowRate("BC"), 1e-6);
    assertEquals(80.0, hardy.getPipeFlowRate("BC") - hardy.getPipeFlowRate("CA") - hardy.getPipeFlowRate("CD"), 1e-6);
    assertEquals(120.0, hardy.getPipeFlowRate("CD") - hardy.getPipeFlowRate("DB"), 1e-6);
    for (String pipe : new String[] { "AB", "BC", "CA", "CD", "DB" }) {
      assertEquals(newton.getPipeFlowRate(pipe), hardy.getPipeFlowRate(pipe), 0.1, pipe);
    }
  }

  private LoopedPipeNetwork network(LoopedPipeNetwork.SolverType solver, boolean twoLoops) {
    SystemSrkEos gas = new SystemSrkEos(288.15, twoLoops ? 60.0 : 50.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    LoopedPipeNetwork network = new LoopedPipeNetwork("Signed network example");
    network.setFluidTemplate(gas);
    network.setSolverType(solver);
    network.setTolerance(1e-5);
    network.setMaxIterations(300);
    network.addSourceNode("A", twoLoops ? 60.0 : 50.0, twoLoops ? 200.0 : 100.0);
    if (twoLoops) {
      network.addJunctionNode("B");
      network.addSinkNode("C", 80.0);
      network.addSinkNode("D", 120.0);
      network.addPipe("A", "B", "AB", 2000.0, 0.20);
      network.addPipe("B", "C", "BC", 2000.0, 0.15);
      network.addPipe("C", "A", "CA", 2000.0, 0.15);
      network.addPipe("C", "D", "CD", 2000.0, 0.15);
      network.addPipe("D", "B", "DB", 2000.0, 0.15);
    } else {
      network.addSinkNode("B", 40.0);
      network.addSinkNode("C", 60.0);
      network.addPipe("A", "B", "AB", 1000.0, 0.15);
      network.addPipe("B", "C", "BC", 1000.0, 0.15);
      network.addPipe("C", "A", "CA", 1000.0, 0.15);
    }
    return network;
  }
}
