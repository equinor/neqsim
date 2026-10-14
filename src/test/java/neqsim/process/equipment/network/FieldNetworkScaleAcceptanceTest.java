package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Representative field-scale acceptance for canonical production and injection topology.
 *
 * <p>
 * These synthetic cases qualify integration, conservation, deterministic replay and a broad runtime envelope. They do
 * not independently qualify hydraulic correlations, reservoir deliverability, pump/compressor maps or field data.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldNetworkScaleAcceptanceTest {
  /** Solve a 12-well, three-template daisy chain with a brownfield satellite and mixed hydraulics. */
  @Test
  void qualifiesMultiTemplateDaisyChainAndBrownfieldTieIn() {
    FieldNetworkTopology topology = productionField(4, 3, true);
    LoopedPipeNetwork network = topology.getHydraulicNetwork();

    topology.validateForExecution();
    network.run();

    assertTrue(network.isConverged());
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-6);
    assertEquals(13.0 * 0.20, network.getPipe("host-riser").getFlowRate(), 1.0e-6);
    assertEquals(LoopedPipeNetwork.PipeModelType.BEGGS_BRILL,
        network.getEffectiveHydraulicModelType("template-1-to-template-2"));
    assertEquals(LoopedPipeNetwork.PipeModelType.TWO_FLUID,
        network.getEffectiveHydraulicModelType("template-2-to-template-3"));
    assertTrue(network.getNodePressure("host") < network.getNodePressure("well-1"));
    assertTrue(network.getPipe("template-2-to-template-3").getHydraulicProfile() != null);

    FieldNetworkTopology replay = topology.copyDefinition();
    replay.getHydraulicNetwork().setFluidTemplate(productionFluid(0.90));
    applyProductionSourceFluids(replay, 12, true);
    replay.validateForExecution();
    replay.getHydraulicNetwork().run();

    assertTrue(replay.getHydraulicNetwork().isConverged());
    assertEquals(network.getPipe("host-riser").getFlowRate(),
        replay.getHydraulicNetwork().getPipe("host-riser").getFlowRate(), 1.0e-8);
    assertEquals(network.getNodePressure("host"), replay.getHydraulicNetwork().getNodePressure("host"), 1.0e-5);
  }

  /** Solve water and CO2-rich gas injection headers using canonical powered-source boundaries. */
  @Test
  void qualifiesPoweredWaterAndGasInjectionHeaders() {
    FieldNetworkTopology water = waterInjectionField();
    water.validateForExecution();
    water.getHydraulicNetwork().run();

    LoopedPipeNetwork waterNetwork = water.getHydraulicNetwork();
    assertTrue(waterNetwork.isConverged());
    assertEquals(0.0, waterNetwork.getMassBalanceError(), 1.0e-8);
    assertEquals(6.0, waterNetwork.getPipe("water-pump").getFlowRate(), 1.0e-8);
    assertEquals(160.0, waterNetwork.getNodePressure("water-header"), 1.0e-4);
    assertTrue(waterNetwork.getPipe("water-pump").getPumpPowerKW() > 0.0);
    for (int injector = 1; injector <= 3; injector++) {
      assertEquals(2.0, waterNetwork.getPipe("water-branch-" + injector).getFlowRate(), 1.0e-8);
      assertTrue(waterNetwork.getNodePressure("water-injector-" + injector) > 160.0,
          "Downward injection must include positive hydrostatic pressure gain");
    }

    FieldNetworkTopology gas = gasInjectionField();
    gas.validateForExecution();
    gas.getHydraulicNetwork().run();

    LoopedPipeNetwork gasNetwork = gas.getHydraulicNetwork();
    assertTrue(gasNetwork.isConverged());
    assertEquals(0.0, gasNetwork.getMassBalanceError(), 1.0e-8);
    assertEquals(3.0, gasNetwork.getPipe("co2-source-line").getFlowRate(), 1.0e-8);
    assertTrue(gasNetwork.getNodePressure("co2-header") < gasNetwork.getNodePressure("co2-source"));
    assertEquals(1.5, gasNetwork.getPipe("co2-branch-1").getFlowRate(), 1.0e-8);
    assertEquals(1.5, gasNetwork.getPipe("co2-branch-2").getFlowRate(), 1.0e-8);
  }

  /** Exercise a 50-well, five-template field and retain a deliberately broad CI runtime guard. */
  @Test
  void solvesFiftyWellFieldWithinRepresentativeRuntimeEnvelope() {
    FieldNetworkTopology topology = productionField(10, 5, false);
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    long startNanos = System.nanoTime();

    topology.validateForExecution();
    network.run();
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

    assertTrue(network.isConverged());
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-6);
    assertEquals(50.0 * 0.20, network.getPipe("host-riser").getFlowRate(), 1.0e-6);
    assertTrue(elapsedMillis < 60000L, "Synthetic 50-well solve exceeded 60 s: " + elapsedMillis + " ms");
  }

  /**
   * Build a production field with a daisy chain of templates.
   *
   * @param wellsPerTemplate number of wells connected to each template
   * @param templateCount number of templates
   * @param includeBrownfieldSatellite whether to connect one additional satellite through a brownfield tie-in
   * @return canonical typed topology
   */
  private FieldNetworkTopology productionField(int wellsPerTemplate, int templateCount,
      boolean includeBrownfieldSatellite) {
    FieldNetworkTopology topology = new FieldNetworkTopology("representative production field");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(productionFluid(0.90));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(200);
    network.setTolerance(5.0);
    network.setCompositionalHydraulicsEnabled(true);
    network.setThermalHydraulicsEnabled(true);

    int wellCount = wellsPerTemplate * templateCount;
    double totalRateKgS = 0.20 * (wellCount + (includeBrownfieldSatellite ? 1 : 0));
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, totalRateKgS * 3600.0, 0.0);
    topology.addJunction("plet", "PLET-01", NodeRole.PLET, Service.PRODUCTION, -300.0);
    topology.addJunction("plem", "PLEM-01", NodeRole.PLEM, Service.PRODUCTION, -300.0);

    int well = 0;
    for (int template = 1; template <= templateCount; template++) {
      String templateId = "template-" + template;
      topology.addJunction(templateId, "TM-" + template, NodeRole.TEMPLATE, Service.PRODUCTION, -300.0);
      for (int slot = 1; slot <= wellsPerTemplate; slot++) {
        well++;
        String wellId = "well-" + well;
        topology.addPressureSource(wellId, "XT-" + well, NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 120.0, 720.0,
            -300.0);
        LoopedPipeNetwork.PipeModelType branchModel = slot == 1 && wellsPerTemplate <= 4
            ? LoopedPipeNetwork.PipeModelType.BEGGS_BRILL
            : LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH;
        LoopedPipeNetwork.NetworkPipe branch = topology.addPipe("branch-" + well, "FL-" + well, EdgeRole.FLOWLINE,
            Service.PRODUCTION, FlowDirection.FROM_TO, wellId, "production", templateId, "slot-" + slot, 750.0, 0.15,
            branchModel);
        branch.setMultiphaseSegments(3);
      }
    }

    for (int template = 1; template < templateCount; template++) {
      String edgeId = "template-" + template + "-to-template-" + (template + 1);
      LoopedPipeNetwork.PipeModelType model = LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH;
      if (templateCount == 3 && template == 1) {
        model = LoopedPipeNetwork.PipeModelType.BEGGS_BRILL;
      } else if (templateCount == 3 && template == 2) {
        model = LoopedPipeNetwork.PipeModelType.TWO_FLUID;
      }
      LoopedPipeNetwork.NetworkPipe trunk = topology.addPipe(edgeId, "TL-" + template, EdgeRole.TRUNKLINE,
          Service.PRODUCTION, FlowDirection.FROM_TO, "template-" + template, "export", "template-" + (template + 1),
          "daisy-inlet", 2500.0, 0.25, model);
      trunk.setMultiphaseSegments(4);
      trunk.setAmbientTemperature(277.15);
      trunk.setOverallHeatTransferCoeff(2.0);
    }

    if (includeBrownfieldSatellite) {
      topology.addPressureSource("satellite-well", "XT-SAT", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 118.0, 720.0,
          -320.0);
      topology.addJunction("brownfield-tie-in", "TI-01", NodeRole.BROWNFIELD_TIE_IN, Service.PRODUCTION, -300.0);
      topology.addPipe("satellite-flowline", "FL-SAT", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
          "satellite-well", "production", "brownfield-tie-in", "new-spool", 3500.0, 0.15,
          LoopedPipeNetwork.PipeModelType.BEGGS_BRILL).setMultiphaseSegments(4);
      topology.addPipe("brownfield-spool", "SP-01", EdgeRole.TIE_IN, Service.PRODUCTION, FlowDirection.FROM_TO,
          "brownfield-tie-in", "existing-outlet", "template-2", "brownfield-inlet", 200.0, 0.20,
          LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    }

    topology.addPipe("field-trunk", "PL-01", EdgeRole.PIPELINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "template-" + templateCount, "export", "plem", "inlet", 15000.0, 0.30,
        LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("plem-to-plet", "PL-02", EdgeRole.PIPELINE, Service.PRODUCTION, FlowDirection.FROM_TO, "plem",
        "export", "plet", "inlet", 5000.0, 0.30, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("host-riser", "RI-01", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "plet", "riser",
        "host", "arrival", 500.0, 0.25, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    applyProductionSourceFluids(topology, wellCount, includeBrownfieldSatellite);
    return topology;
  }

  /** Build a shared-header water injection system with a fixed-outlet-pressure pump. */
  private FieldNetworkTopology waterInjectionField() {
    FieldNetworkTopology topology = new FieldNetworkTopology("shared water injection");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(waterFluid());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(5.0);
    topology.addPressureSource("water-source", "V-201", NodeRole.INJECTION_SOURCE, Service.INJECTION, 50.0,
        6.0 * 3600.0, 0.0);
    topology.addJunction("water-header", "HD-201", NodeRole.MANIFOLD, Service.INJECTION, 0.0);
    topology.addPump("water-pump", "P-201", Service.INJECTION, FlowDirection.FROM_TO, "water-source", "suction",
        "water-header", "discharge", 160.0, 0.82);
    for (int injector = 1; injector <= 3; injector++) {
      String id = "water-injector-" + injector;
      topology.addDemandSink(id, "XT-WI-" + injector, NodeRole.INJECTION_WELL, Service.INJECTION, 2.0 * 3600.0, -300.0);
      topology.addPipe("water-branch-" + injector, "WI-" + injector, EdgeRole.FLOWLINE, Service.INJECTION,
          FlowDirection.FROM_TO, "water-header", "slot-" + injector, id, "injection", 4000.0 + 500.0 * injector, 0.15,
          LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    }
    return topology;
  }

  /** Build a CO2-rich gas injection system from a compressor-discharge boundary to a shared header. */
  private FieldNetworkTopology gasInjectionField() {
    FieldNetworkTopology topology = new FieldNetworkTopology("shared CO2 injection");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(co2RichFluid());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(5.0);
    topology.addPressureSource("co2-source", "K-301-D", NodeRole.INJECTION_SOURCE, Service.INJECTION, 180.0,
        3.0 * 3600.0, 0.0);
    topology.addJunction("co2-header", "HD-301", NodeRole.MANIFOLD, Service.INJECTION, 0.0);
    topology.addPipe("co2-source-line", "GI-TRUNK", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO,
        "co2-source", "discharge", "co2-header", "inlet", 2000.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    for (int injector = 1; injector <= 2; injector++) {
      String id = "co2-injector-" + injector;
      topology.addDemandSink(id, "XT-GI-" + injector, NodeRole.INJECTION_WELL, Service.INJECTION, 1.5 * 3600.0, -300.0);
      topology.addPipe("co2-branch-" + injector, "GI-" + injector, EdgeRole.FLOWLINE, Service.INJECTION,
          FlowDirection.FROM_TO, "co2-header", "slot-" + injector, id, "injection", 3000.0 + 500.0 * injector, 0.15,
          LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    }
    return topology;
  }

  /** Apply differing but component-compatible production fluids to every source. */
  private void applyProductionSourceFluids(FieldNetworkTopology topology, int wellCount,
      boolean includeBrownfieldSatellite) {
    for (int well = 1; well <= wellCount; well++) {
      topology.getHydraulicNetwork().setNodeFluid("well-" + well, productionFluid(0.86 + 0.01 * (well % 5)));
    }
    if (includeBrownfieldSatellite) {
      topology.getHydraulicNetwork().setNodeFluid("satellite-well", productionFluid(0.84));
    }
  }

  /** Build a methane/n-heptane multiphase production fluid. */
  private SystemInterface productionFluid(double methaneFraction) {
    SystemInterface fluid = new SystemSrkEos(303.15, 120.0);
    fluid.addComponent("methane", methaneFraction);
    fluid.addComponent("n-heptane", 1.0 - methaneFraction);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /** Build a single-component water injection fluid. */
  private SystemInterface waterFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Build a CO2-rich gas injection fluid with a small methane impurity. */
  private SystemInterface co2RichFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 70.0);
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("methane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }
}
