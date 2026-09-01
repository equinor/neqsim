package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests generalized network decisions, residuals, and automation addresses.
 */
class GeneralizedNetworkOptimizerTest {
  @Test
  void testCandidateEvaluationRestoresDecisionState() {
    LoopedPipeNetwork network = createPressureDrivenNetwork();
    LoopedPipeNetwork.NetworkPipe edge = network.getPipe("export");
    edge.setAvailability(0.8);
    network.run();

    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.addDecisionVariable(
        new NetworkDecisionVariable("edge.export.availability", NetworkDecisionVariable.Type.EDGE_AVAILABILITY,
            "export", "-", NetworkDecisionVariable.RateBasis.NONE, 0.1, 1.0));
    optimizer.addObjective(NetworkObjectives.maximizeThroughput(1.0));
    optimizer.addConstraint(NetworkConstraints.convergence());

    NetworkCandidateEvaluation evaluation = optimizer.evaluateCandidate(new double[] { 0.5 });

    assertNotNull(evaluation);
    assertEquals(0.5, evaluation.getDecisions().get("edge.export.availability"), 1.0e-12);
    assertEquals(0.8, edge.getAvailability(), 1.0e-12);
    assertTrue(evaluation.toJson().contains("\"rateBases\""));
  }

  @Test
  void testSoftPenaltyUsesResidualMagnitude() {
    LoopedPipeNetwork network = createPressureDrivenNetwork();
    network.run();
    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.setConstraintPenalty(100.0);
    optimizer.addDecisionVariable(
        new NetworkDecisionVariable("edge.export.availability", NetworkDecisionVariable.Type.EDGE_AVAILABILITY,
            "export", "-", NetworkDecisionVariable.RateBasis.NONE, 0.1, 1.0));
    optimizer.addObjective(NetworkObjectives.custom("availability", 1.0, new NetworkObjectives.Evaluator() {
      private static final long serialVersionUID = 1000L;

      @Override
      public double evaluate(LoopedPipeNetwork solved) {
        return solved.getPipe("export").getAvailability();
      }
    }));
    optimizer.addConstraint(
        NetworkConstraints.custom("minimumAvailability", false, 0.1, "-", new NetworkConstraints.ResidualEvaluator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double evaluate(LoopedPipeNetwork solved) {
            return Math.max(0.0, 0.8 - solved.getPipe("export").getAvailability());
          }
        }));

    NetworkCandidateEvaluation smallViolation = optimizer.evaluateCandidate(new double[] { 0.7 });
    NetworkCandidateEvaluation largeViolation = optimizer.evaluateCandidate(new double[] { 0.4 });

    assertTrue(largeViolation.getPenalty() > smallViolation.getPenalty());
  }

  @Test
  void testProcessAutomationExposesNetworkInternals() {
    LoopedPipeNetwork network = createPressureDrivenNetwork();
    ProcessSystem process = new ProcessSystem();
    process.add(network);
    ProcessAutomation automation = new ProcessAutomation(process);

    List<SimulationVariable> variables = automation.getVariableList("allocation network");
    assertTrue(containsAddress(variables, "allocation network.node.supply.pressure"));
    assertTrue(containsAddress(variables, "allocation network.source.supply.rate"));
    assertTrue(containsAddress(variables, "allocation network.edge.export.flowRate"));
    assertTrue(automation.isWritableAddress("allocation network.edge.export.availability"));

    automation.setVariableValue("allocation network.source.supply.rate", 7200.0, "kg/hr");
    assertEquals(7200.0, automation.getVariableValue("allocation network.source.supply.rate", "kg/hr"), 1.0e-12);
    automation.setVariableValue("allocation network.edge.export.availability", 0.6, "-");
    assertEquals(0.6, automation.getVariableValue("allocation network.edge.export.availability", "-"), 1.0e-12);
  }

  @Test
  void testHardConstraintMarksCandidateInfeasible() {
    LoopedPipeNetwork network = createPressureDrivenNetwork();
    network.run();
    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.addDecisionVariable(
        new NetworkDecisionVariable("edge.export.availability", NetworkDecisionVariable.Type.EDGE_AVAILABILITY,
            "export", "-", NetworkDecisionVariable.RateBasis.NONE, 0.1, 1.0));
    optimizer.addConstraint(NetworkConstraints.custom("availabilityAtLeastHalf", true, 0.5, "-",
        new NetworkConstraints.ResidualEvaluator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public double evaluate(LoopedPipeNetwork solved) {
            return Math.max(0.0, 0.5 - solved.getPipe("export").getAvailability());
          }
        }));

    NetworkCandidateEvaluation evaluation = optimizer.evaluateCandidate(new double[] { 0.2 });

    assertFalse(evaluation.isFeasible());
    assertTrue(evaluation.getPenalty() >= 1.0e9);
  }

  private boolean containsAddress(List<SimulationVariable> variables, String address) {
    for (SimulationVariable variable : variables) {
      if (address.equals(variable.getAddress())) {
        return true;
      }
    }
    return false;
  }

  private LoopedPipeNetwork createPressureDrivenNetwork() {
    SystemInterface gas = new SystemSrkEos(293.15, 80.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.05);
    gas.setMixingRule("classic");
    LoopedPipeNetwork network = new LoopedPipeNetwork("allocation network");
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(500.0);
    network.addSourceNode("supply", 80.0, 0.0);
    network.addFixedPressureSinkNode("delivery", 45.0);
    network.addPipe("supply", "delivery", "export", 5000.0, 0.3);
    return network;
  }
}
