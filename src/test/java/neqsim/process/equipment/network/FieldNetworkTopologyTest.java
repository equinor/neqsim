package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldNetworkTopology.Severity;
import neqsim.process.equipment.network.FieldNetworkTopology.ValidationIssue;
import neqsim.process.equipment.subsea.PLEM;
import neqsim.process.equipment.subsea.PLET;
import neqsim.process.equipment.subsea.SubseaManifold;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Acceptance tests for typed field and SURF identities over the canonical hydraulic graph.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldNetworkTopologyTest {
  /**
   * Build a simple gas fluid for topology execution tests.
   *
   * @return SRK methane/ethane fluid
   */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Check whether a validation list contains a diagnostic code.
   *
   * @param issues validation issues
   * @param code expected code
   * @return true when the code exists
   */
  private boolean hasIssue(List<ValidationIssue> issues, String code) {
    for (ValidationIssue issue : issues) {
      if (code.equals(issue.getCode())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether validation contains any execution-blocking error.
   *
   * @param issues validation issues
   * @return true when an error exists
   */
  private boolean hasError(List<ValidationIssue> issues) {
    for (ValidationIssue issue : issues) {
      if (issue.getSeverity() == Severity.ERROR) {
        return true;
      }
    }
    return false;
  }

  /** Build multi-template production and injection systems in one typed definition. */
  @Test
  void buildsProductionAndInjectionSystemsOnCanonicalGraph() {
    FieldNetworkTopology topology = new FieldNetworkTopology("mixed field");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);

    topology.addPressureSource("prod-well-a", "XT-101", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 120.0, 3600.0,
        -350.0);
    topology.addPressureSource("prod-well-b", "XT-102", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 120.0, 3600.0,
        -350.0);
    topology.addJunction("template-a", "TM-101", NodeRole.TEMPLATE, Service.PRODUCTION, -350.0);
    topology.addJunction("plem-a", "PLEM-101", NodeRole.PLEM, Service.PRODUCTION, -350.0);
    topology.addFixedPressureSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 40.0, 0.0);
    topology.addPipe("branch-a", "FL-101A", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "prod-well-a",
        "production", "template-a", "slot-1", 1000.0, 0.20, LoopedPipeNetwork.PipeModelType.BEGGS_BRILL);
    topology.addPipe("branch-b", "FL-101B", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "prod-well-b",
        "production", "template-a", "slot-2", 1000.0, 0.20, LoopedPipeNetwork.PipeModelType.BEGGS_BRILL);
    topology.addPipe("template-header", "TL-101", EdgeRole.TRUNKLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "template-a", "export", "plem-a", "inlet", 5000.0, 0.30, LoopedPipeNetwork.PipeModelType.TWO_FLUID);
    topology.addPipe("export-riser", "RI-101", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "plem-a",
        "riser", "host", "production-inlet", 1000.0, 0.35, LoopedPipeNetwork.PipeModelType.TWO_FLUID);

    topology.addPressureSource("injection-source", "P-201", NodeRole.INJECTION_SOURCE, Service.INJECTION, 180.0, 7200.0,
        0.0);
    topology.addJunction("injection-template", "TM-201", NodeRole.TEMPLATE, Service.INJECTION, -350.0);
    topology.addDemandSink("injector-a", "XT-201", NodeRole.INJECTION_WELL, Service.INJECTION, 3600.0, -350.0);
    topology.addDemandSink("injector-b", "XT-202", NodeRole.INJECTION_WELL, Service.INJECTION, 3600.0, -350.0);
    topology.addPipe("injection-trunk", "WI-201", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO,
        "injection-source", "discharge", "injection-template", "header", 8000.0, 0.25,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("injector-a-branch", "WI-201A", EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO,
        "injection-template", "slot-1", "injector-a", "injection", 800.0, 0.15,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("injector-b-branch", "WI-201B", EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO,
        "injection-template", "slot-2", "injector-b", "injection", 800.0, 0.15,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    PLEM plem = new PLEM("Production PLEM");
    SubseaManifold injectionTemplate = new SubseaManifold("Injection template", 2);
    topology.bindEquipment("plem-a", plem).bindEquipment("injection-template", injectionTemplate);

    List<ValidationIssue> issues = topology.validate();
    assertFalse(hasError(issues));
    assertTrue(hasIssue(issues, "DISCONNECTED_COMPONENTS"));
    assertSame(plem, topology.getBoundEquipment("plem-a"));
    assertSame(injectionTemplate, topology.getBoundEquipment("injection-template"));
    assertEquals(LoopedPipeNetwork.PipeModelType.TWO_FLUID, network.getEffectiveHydraulicModelType("template-header"));
    assertEquals("slot-2", topology.getEdge("injector-b-branch").getFromPort());
    assertEquals("XT-202", topology.getNode("injector-b").getEquipmentTag());
  }

  /** Verify definition replay preserves field metadata and hydraulic fidelity without equipment duplication. */
  @Test
  void replayPreservesTypedIdentityPortsAndHydraulicSelection() {
    FieldNetworkTopology topology = new FieldNetworkTopology("direct tieback");
    topology.getHydraulicNetwork().setFluidTemplate(gas());
    topology.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    topology.addPressureSource("well", "XT-301", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -300.0);
    topology.addJunction("plet", "PLET-301", NodeRole.PLET, Service.PRODUCTION, -300.0);
    topology.addFixedPressureSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 30.0, 0.0);
    topology.addPipe("flowline", "FL-301", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "well",
        "production", "plet", "inlet", 10000.0, 0.25, LoopedPipeNetwork.PipeModelType.BEGGS_BRILL);
    topology.addPipe("riser", "RI-301", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "plet", "riser",
        "host", "arrival", 1000.0, 0.30, LoopedPipeNetwork.PipeModelType.TWO_FLUID);
    topology.bindEquipment("plet", new PLET("PLET equipment"));

    FieldNetworkTopology replay = topology.copyDefinition();
    replay.getHydraulicNetwork().setFluidTemplate(gas());

    assertEquals(3, replay.getNodes().size());
    assertEquals(2, replay.getEdges().size());
    assertEquals(NodeRole.PLET, replay.getNode("plet").getRole());
    assertEquals("arrival", replay.getEdge("riser").getToPort());
    assertEquals("plet", replay.getHydraulicNetwork().getPipe("riser").getFromNode());
    assertEquals(LoopedPipeNetwork.PipeModelType.TWO_FLUID,
        replay.getHydraulicNetwork().getEffectiveHydraulicModelType("riser"));
    assertNull(replay.getBoundEquipment("plet"));
    assertFalse(hasError(replay.validate()));
  }

  /** Reject duplicate identities before the underlying network can silently replace a node. */
  @Test
  void rejectsDuplicateStableIdentity() {
    FieldNetworkTopology topology = new FieldNetworkTopology("identity validation");
    topology.addJunction("template", "TM-1", NodeRole.TEMPLATE, Service.PRODUCTION, -200.0);
    assertThrows(IllegalArgumentException.class,
        () -> topology.addJunction("template", "TM-2", NodeRole.MANIFOLD, Service.PRODUCTION, -200.0));
    assertThrows(IllegalArgumentException.class,
        () -> topology.addJunction(" template", "TM-3", NodeRole.MANIFOLD, Service.PRODUCTION, -200.0));
  }

  /** Report duplicate physical ports and production/injection semantic mismatches. */
  @Test
  void validatesPortsAndServiceCompatibility() {
    FieldNetworkTopology topology = new FieldNetworkTopology("semantic diagnostics");
    topology.getHydraulicNetwork().setFluidTemplate(gas());
    topology.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    topology.addPressureSource("well", "XT-401", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -200.0);
    topology.addJunction("manifold", "MA-401", NodeRole.MANIFOLD, Service.SHARED, -200.0);
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 3600.0, 0.0);
    topology.addPipe("branch", "FL-401", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "well",
        "production", "manifold", "slot-1", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("export", "FL-402", EdgeRole.TRUNKLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "manifold",
        "slot-1", "host", "arrival", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("wrong-service", "WI-401", EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO, "well",
        "annulus", "manifold", "slot-2", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    List<ValidationIssue> issues = topology.validate();
    assertTrue(hasIssue(issues, "DUPLICATE_PORT"));
    assertTrue(hasIssue(issues, "SERVICE_MISMATCH"));
    assertThrows(IllegalStateException.class, topology::validateForExecution);
  }

  /** Report unclassified nodes and edges when wrapping an existing hydraulic graph. */
  @Test
  void wrappingExistingGraphRequiresExplicitClassification() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("existing graph");
    network.setFluidTemplate(gas());
    network.addSourceNode("well", 100.0, 3600.0);
    network.addJunctionNode("manifold");
    network.addSinkNode("host", 3600.0);
    network.addPipe("well", "manifold", "branch", 100.0, 0.2);
    network.addPipe("manifold", "host", "export", 100.0, 0.3);
    FieldNetworkTopology topology = new FieldNetworkTopology(network);
    topology.registerExistingNode("well", "XT-501", NodeRole.PRODUCTION_WELL, Service.PRODUCTION);

    List<ValidationIssue> issues = topology.validate();
    assertTrue(hasIssue(issues, "UNCLASSIFIED_NODE"));
    assertTrue(hasIssue(issues, "UNCLASSIFIED_EDGE"));
  }

  /** Diagnose loops before a sequential solver attempts unsupported execution. */
  @Test
  void detectsCycleAndSolverApplicability() {
    FieldNetworkTopology topology = new FieldNetworkTopology("looped templates");
    topology.getHydraulicNetwork().setFluidTemplate(gas());
    topology.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.SEQUENTIAL);
    topology.addPressureSource("source", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 100.0, 3600.0, 0.0);
    topology.addJunction("a", "TM-A", NodeRole.TEMPLATE, Service.PRODUCTION, -200.0);
    topology.addJunction("b", "TM-B", NodeRole.TEMPLATE, Service.PRODUCTION, -200.0);
    topology.addJunction("c", "PLEM-C", NodeRole.PLEM, Service.PRODUCTION, -200.0);
    topology.addPipe("feed", "FL-0", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "source", "outlet",
        "a", "inlet", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("a-b", "FL-1", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.BIDIRECTIONAL, "a", "to-b",
        "b", "from-a", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("b-c", "FL-2", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.BIDIRECTIONAL, "b", "to-c",
        "c", "from-b", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("c-a", "FL-3", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.BIDIRECTIONAL, "c", "to-a",
        "a", "from-c", 100.0, 0.2, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    List<ValidationIssue> issues = topology.validate();
    assertTrue(hasIssue(issues, "CYCLE_UNSUPPORTED_BY_SOLVER"));
    assertTrue(hasIssue(issues, "EDGE_HYDRAULIC_MODEL_REQUIRES_NEWTON"));
  }

  /** Detect solved reverse flow that was not declared as part of the operating envelope. */
  @Test
  void diagnosesUndeclaredReverseFlowAfterSolve() {
    FieldNetworkTopology topology = new FieldNetworkTopology("reverse flow diagnostic");
    topology.getHydraulicNetwork().setFluidTemplate(gas());
    topology.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    topology.addPressureSource("well", "XT-551", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -200.0);
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 3600.0, 0.0);
    topology.addPipe("reversed-edge", "FL-551", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "host",
        "arrival", "well", "production", 100.0, 0.3, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    assertFalse(hasError(topology.validate()));
    topology.getHydraulicNetwork().run();

    assertTrue(topology.getHydraulicNetwork().getPipe("reversed-edge").getFlowRate() < 0.0);
    assertTrue(hasIssue(topology.validate(), "UNDECLARED_REVERSE_FLOW"));
  }

  /** Execute production and injection builder paths and verify conservative mass flow. */
  @Test
  void executesProductionAndInjectionBuilderPaths() {
    FieldNetworkTopology production = new FieldNetworkTopology("production direct tieback");
    production.getHydraulicNetwork().setFluidTemplate(gas());
    production.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    production.addPressureSource("well", "XT-601", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -200.0);
    production.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 3600.0, 0.0);
    production.addPipe("tieback", "FL-601", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "well",
        "production", "host", "arrival", 100.0, 0.3, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    production.validateForExecution();
    production.getHydraulicNetwork().run();
    assertTrue(production.getHydraulicNetwork().isConverged());
    assertEquals(3600.0, production.getHydraulicNetwork().getPipeFlowRate("tieback"), 1.0e-6);
    assertEquals(0.0, production.getHydraulicNetwork().getMassBalanceError(), 1.0e-8);

    FieldNetworkTopology injection = new FieldNetworkTopology("water injection branch");
    injection.getHydraulicNetwork().setFluidTemplate(gas());
    injection.getHydraulicNetwork().setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    injection.addPressureSource("source", "P-601", NodeRole.INJECTION_SOURCE, Service.INJECTION, 150.0, 3600.0, 0.0);
    injection.addDemandSink("injector", "XT-602", NodeRole.INJECTION_WELL, Service.INJECTION, 3600.0, -200.0);
    injection.addPipe("injection-line", "WI-601", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO, "source",
        "discharge", "injector", "injection", 100.0, 0.3, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    injection.validateForExecution();
    injection.getHydraulicNetwork().run();
    assertTrue(injection.getHydraulicNetwork().isConverged());
    assertEquals(3600.0, injection.getHydraulicNetwork().getPipeFlowRate("injection-line"), 1.0e-6);
    assertEquals(0.0, injection.getHydraulicNetwork().getMassBalanceError(), 1.0e-8);
  }
}
