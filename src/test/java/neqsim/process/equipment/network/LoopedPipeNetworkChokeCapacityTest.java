package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression fixtures for issue #3601: IPR, choke, and fixed-pressure arrival. */
class LoopedPipeNetworkChokeCapacityTest {
  @Test
  void gasCapacityAgreesWithStandaloneValveAcrossOpeningAndPressureSweeps() {
    for (double arrival : new double[] { 90.0, 60.0, 20.0 }) {
      double previous = 0.0;
      for (double opening : new double[] { 10.0, 30.0, 60.0, 100.0 }) {
        LoopedPipeNetwork network = network(arrival, 10.0, opening);
        network.run();
        assertTrue(network.isConverged(), "arrival=" + arrival + ", opening=" + opening);
        assertTrue(network.isProductionOptimizationApplicable());
        double flow = network.getTotalSinkFlow();
        double upstream = network.getNodePressure("wellhead");
        assertEquals(referenceFlow(upstream, arrival, 10.0, opening), flow, 1e-5);
        assertEquals((120.0 - upstream) * 1e5 * 1e-5, flow, 1e-5);
        assertEquals(network.getPipeFlowRate("ipr"), network.getPipeFlowRate("choke"), 1e-3);
        assertTrue(flow > previous, "Opening must retain throughput sensitivity in critical flow");
        assertTrue(upstream >= arrival && upstream <= 120.0);
        previous = flow;
      }
    }
  }

  @Test
  void criticalCapacityIsIndependentOfBackpressureButRespondsToKv() {
    double previous = 0.0;
    for (double kv : new double[] { 1.0, 5.0, 10.0, 20.0 }) {
      LoopedPipeNetwork network = network(20.0, kv, 60.0);
      network.run();
      double criticalFlow = network.getTotalSinkFlow();
      assertEquals("IEC_GAS_CRITICAL", network.getPipe("choke").getChokeModelStatus());
      assertTrue(criticalFlow > previous);
      network.setNodePressure("arrival", 10.0);
      network.run();
      assertTrue(network.isProductionOptimizationApplicable());
      assertEquals(criticalFlow, network.getTotalSinkFlow(), 1e-6);
      assertEquals(network.getTotalSinkFlow(), network.getPipe("choke").getChokeCapacityKgS(), 1e-6);
      previous = criticalFlow;
    }
  }

  @Test
  void closureReopeningAndSmallOpeningPerturbationsAreStable() {
    LoopedPipeNetwork network = network(20.0, 10.0, 0.0);
    network.run();
    assertTrue(network.isProductionOptimizationApplicable());
    assertEquals("CLOSED", network.getPipe("choke").getChokeModelStatus());
    assertEquals(0.0, network.getTotalSinkFlow(), 1e-6);
    assertEquals(120.0, network.getNodePressure("wellhead"), 1e-5);

    double previous = 0.0;
    for (double opening : new double[] { 0.01, 49.99, 50.0, 50.01, 100.0 }) {
      network.getPipe("choke").setChokeOpening(opening);
      assertFalse(network.isProductionOptimizationApplicable(), "Changed openings require a fresh solve");
      network.run();
      assertTrue(network.isProductionOptimizationApplicable());
      double flow = network.getTotalSinkFlow();
      assertTrue(flow > previous);
      network.run();
      assertEquals(flow, network.getTotalSinkFlow(), 1e-8);
      previous = flow;
    }
    network.getPipe("choke").setChokeKv(0.0);
    network.run();
    assertEquals(0.0, network.getTotalSinkFlow(), 1e-6);
  }

  @Test
  void availabilityScalesCapacityAndDoesNotScaleReportedPressureLossTwice() {
    LoopedPipeNetwork network = network(20.0, 10.0, 60.0);
    network.getPipe("choke").setAvailability(0.5);
    network.run();
    assertTrue(network.isProductionOptimizationApplicable());
    double upstream = network.getNodePressure("wellhead");
    assertEquals(referenceFlow(upstream, 20.0, 5.0, 60.0), network.getTotalSinkFlow(), 1e-5);
    assertEquals((upstream - 20.0) * 1e5, network.getPipe("choke").getHeadLoss(), 1e-5);
    network.getPipe("choke").setAvailability(0.0);
    network.run();
    assertTrue(network.isProductionOptimizationApplicable());
    assertEquals(0.0, network.getTotalSinkFlow(), 1e-6);
  }

  @Test
  void criticalBoundaryIsContinuousAndReverseFlowUsesPhysicalUpstream() {
    Stream inlet = new Stream("upstream properties", fluid(120.0));
    inlet.run();
    double criticalArrival = 120.0 * (1.0 - inlet.getFluid().getGamma2() / 1.4 * 0.5);
    double criticalFlow = referenceFlow(120.0, criticalArrival, 10.0, 60.0);
    for (double offset : new double[] { -0.001, 0.0, 0.001 }) {
      for (boolean reverse : new boolean[] { false, true }) {
        LoopedPipeNetwork network = new LoopedPipeNetwork("fixed pressure choke");
        network.setFluidTemplate(fluid(120.0));
        network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
        network.setTolerance(0.01);
        network.addSourceNode("high", 120.0, 0.0);
        network.getNode("high").setTemperature(313.15);
        network.addFixedPressureSinkNode("low", criticalArrival + offset);
        network.addChoke(reverse ? "low" : "high", reverse ? "high" : "low", "choke", 10.0, 60.0)
            .setChokeUseValveModel(true);
        network.run();
        assertTrue(network.isProductionOptimizationApplicable());
        assertEquals(criticalFlow, network.getTotalSinkFlow(), criticalFlow * 1e-6);
        assertEquals((reverse ? -1.0 : 1.0) * criticalFlow * 3600.0, network.getPipeFlowRate("choke"),
            criticalFlow * 0.01);
      }
    }
  }

  @Test
  void unsupportedLegacyCriticalPointCannotBeReportedAsOptimizedProduction() {
    LoopedPipeNetwork network = network(20.0, 1.0, 60.0);
    network.getPipe("choke").setChokeUseValveModel(false);
    Map<String, Object> result = network.optimizeFullField(3, 0.01);
    assertEquals("UNSUPPORTED_CRITICAL_FLOW", network.getPipe("choke").getChokeModelStatus());
    assertFalse(network.isProductionOptimizationApplicable());
    assertEquals(0.0, (Double) result.get("converged"));
    assertEquals(Boolean.FALSE, result.get("chokeModelApplicable"));
    assertTrue(Double.isNaN((Double) result.get("revenue_usd_hr")));
    assertTrue(Double.isNaN(network.optimizeChokeOpenings(3, 0.01)));
    assertEquals(0.0, network.runCoupled().get("converged"));
    assertTrue(network.toJson().contains("UNSUPPORTED_CRITICAL_FLOW"));
  }

  @Test
  void gasOptimizationReplaysApplicableStateAndFlowLimitDiagnosticsRemainAvailable() {
    LoopedPipeNetwork network = network(20.0, 10.0, 30.0);
    network.run();
    double before = network.getTotalSinkFlow();
    Map<String, Object> result = network.optimizeFullField(5, 0.001);
    assertEquals(1.0, (Double) result.get("converged"));
    assertEquals(Boolean.TRUE, result.get("chokeModelApplicable"));
    assertTrue(network.getTotalSinkFlow() > before);
    assertEquals(network.getTotalSinkFlow() * 3600.0, (Double) result.get("totalFlow_kghr"), 1e-6);
    double finalFlow = network.getTotalSinkFlow();
    network.run();
    assertEquals(finalFlow, network.getTotalSinkFlow(), 1e-8);
    network.setElementFlowLimits("choke", 0.0, finalFlow * 3600.0 * 0.9);
    assertFalse(network.checkConstraints().isEmpty());
    network.setElementFlowLimits("choke", 0.0, finalFlow * 3600.0 * 1.1);
    assertTrue(network.checkConstraints().isEmpty());
  }

  @Test
  void invalidInputsAndUnsupportedGasModeCasesAreRejectedExplicitly() {
    LoopedPipeNetwork network = network(20.0, 10.0, 60.0);
    LoopedPipeNetwork.NetworkPipe choke = network.getPipe("choke");
    for (double opening : new double[] { -1.0, 100.01, Double.NaN, Double.POSITIVE_INFINITY }) {
      assertThrows(IllegalArgumentException.class, () -> choke.setChokeOpening(opening));
    }
    assertThrows(IllegalArgumentException.class, () -> choke.setChokeKv(-1.0));
    assertThrows(IllegalArgumentException.class, () -> choke.setChokeKv(Double.NaN));
    for (double ratio : new double[] { 0.0, 1.0, Double.NaN }) {
      assertThrows(IllegalArgumentException.class, () -> choke.setChokeCriticalPressureRatio(ratio));
    }
    network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
    assertThrows(IllegalStateException.class, network::run);
    assertEquals("UNSUPPORTED_SOLVER", choke.getChokeModelStatus());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    SystemInterface liquid = new SystemSrkEos(313.15, 120.0);
    liquid.addComponent("water", 1.0);
    liquid.setMixingRule(2);
    network.setFluidTemplate(liquid);
    assertThrows(IllegalStateException.class, network::run);
    assertEquals("UNSUPPORTED_PHASE", choke.getChokeModelStatus());
  }

  private static double referenceFlow(double upstream, double downstream, double kv, double opening) {
    Stream inlet = new Stream("reference inlet", fluid(upstream));
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();
    ThrottlingValve valve = new ThrottlingValve("reference valve", inlet);
    valve.getMechanicalDesign().setValveSizingStandard("IEC 60534");
    valve.getMechanicalDesign().getValveSizingMethod().setxT(0.5);
    valve.setCv(kv, "Kv");
    valve.setPercentValveOpening(opening);
    valve.setOutletPressure(downstream);
    return valve.calculateMolarFlow() * inlet.getFluid().getMolarMass("kg/mol");
  }

  private static LoopedPipeNetwork network(double arrival, double kv, double opening) {
    LoopedPipeNetwork network = new LoopedPipeNetwork("gas choke qualification");
    network.setFluidTemplate(fluid(120.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.01);
    network.setMaxIterations(150);
    network.addSourceNode("reservoir", 120.0, 0.0);
    network.addJunctionNode("wellhead");
    network.getNode("wellhead").setTemperature(313.15);
    network.addFixedPressureSinkNode("arrival", arrival);
    network.getNode("arrival").setTemperature(313.15);
    network.addWellIPR("reservoir", "wellhead", "ipr", 1e-5, false);
    network.addChoke("wellhead", "arrival", "choke", kv, opening).setChokeUseValveModel(true);
    return network;
  }

  private static SystemInterface fluid(double pressure) {
    SystemInterface fluid = new SystemSrkEos(313.15, pressure);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    return fluid;
  }
}
