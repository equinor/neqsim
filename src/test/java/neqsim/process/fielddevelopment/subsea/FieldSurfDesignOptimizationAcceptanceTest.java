package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.network.NetworkConstraints;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.network.NetworkOptimizer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Engineering acceptance for coupled canonical hydraulics and route-specific Class 4 SURF design.
 *
 * <p>
 * This synthetic pressure-driven case demonstrates deterministic discrete design, conservation and the expected
 * hydraulic/cost trade-off. It is not independent qualification of the Darcy model or the Class 4 cost correlations.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldSurfDesignOptimizationAcceptanceTest {
  /** Select the least-cost two-route diameter combination that satisfies the hydraulic delivery constraint. */
  @Test
  void selectsLeastCostFeasibleCanonicalRouteDesign() {
    FieldNetworkTopology topology = createTopology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    LoopedPipeNetwork.NetworkPipe longLine = network.getPipe("long-line");
    LoopedPipeNetwork.NetworkPipe shortLine = network.getPipe("short-line");

    double smallM = 0.20;
    double largeM = 0.30;
    double smallLargeFlow = solveFlow(network, longLine, smallM, shortLine, largeM);
    double largeSmallFlow = solveFlow(network, longLine, largeM, shortLine, smallM);
    assertTrue(largeSmallFlow > smallLargeFlow, "Upsizing the longer route must give the larger hydraulic benefit");
    double minimumFlowKgHr = 0.5 * (smallLargeFlow + largeSmallFlow);

    longLine.setDiameter(smallM);
    shortLine.setDiameter(smallM);
    network.run();
    SubseaProductionSystem subsea = new SubseaProductionSystem("Canonical route design");
    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.setMaxEvaluations(4);
    optimizer.addDecisionVariable(
        NetworkDecisionVariable.pipeDiameter("edge.long-line.diameter", "long-line", new double[] {smallM, largeM}));
    optimizer.addDecisionVariable(
        NetworkDecisionVariable.pipeDiameter("edge.short-line.diameter", "short-line", new double[] {smallM, largeM}));
    optimizer.addObjective(subsea.createSurfCapitalCostObjective(topology, 1.0e-6));
    optimizer.addConstraint(NetworkConstraints.convergence());
    optimizer.addConstraint(NetworkConstraints.massBalance(1.0e-8, true));
    optimizer.addConstraint(NetworkConstraints.edgeFlow("long-line", minimumFlowKgHr, 1.0e9, true));

    NetworkOptimizer.OptimizationResult result = optimizer.optimize();

    assertTrue(result.converged);
    assertEquals("DISCRETE_ENUMERATION", result.algorithm);
    assertEquals(4, result.functionEvaluations);
    assertEquals(largeM, longLine.getDiameter(), 1.0e-12);
    assertEquals(smallM, shortLine.getDiameter(), 1.0e-12);
    assertTrue(network.isConverged());
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-8);
    assertTrue(Math.abs(longLine.getFlowRate()) * 3600.0 >= minimumFlowKgHr);
    assertEquals(2, subsea.createSurfDesignBasis(topology).getLineSegments().size());
    assertTrue(subsea.estimateSurfCosts(topology).getCapitalCostSummary().get("totalSURF") > 0.0);
  }

  /**
   * Solve one diameter pair and return the common series flow.
   *
   * @param network canonical hydraulic network
   * @param first first route segment
   * @param firstDiameterM first inner diameter in metres
   * @param second second route segment
   * @param secondDiameterM second inner diameter in metres
   * @return absolute mass flow in kg/hr
   */
  private double solveFlow(LoopedPipeNetwork network, LoopedPipeNetwork.NetworkPipe first, double firstDiameterM,
      LoopedPipeNetwork.NetworkPipe second, double secondDiameterM) {
    first.setDiameter(firstDiameterM);
    second.setDiameter(secondDiameterM);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(Math.abs(first.getFlowRate()), Math.abs(second.getFlowRate()), 1.0e-8);
    return Math.abs(first.getFlowRate()) * 3600.0;
  }

  /**
   * Build a pressure-driven gas route with a long infield segment and a short export segment.
   *
   * @return connected canonical topology
   */
  private FieldNetworkTopology createTopology() {
    SystemInterface gas = new SystemSrkEos(293.15, 100.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");

    FieldNetworkTopology topology = new FieldNetworkTopology("discrete SURF design");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(100.0);
    topology.addPressureSource("well", "W-01", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 0.0, -300.0);
    topology.addJunction("plet", "PLET-01", NodeRole.PLET, Service.PRODUCTION, -300.0);
    topology.addFixedPressureSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 50.0, 0.0);
    topology.addPipe("long-line", "FL-LONG", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "well",
        "production", "plet", "inlet", 10000.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("short-line", "PL-SHORT", EdgeRole.PIPELINE, Service.PRODUCTION, FlowDirection.FROM_TO, "plet",
        "outlet", "host", "arrival", 2000.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.validateForExecution();
    return topology;
  }
}
