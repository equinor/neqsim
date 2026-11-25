package neqsim.process.fielddevelopment.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldWellNetworkCoupler;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.CouplingResult;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Acceptance tests for simulator-derived reservoir state on the canonical production-well and field graph. */
class ReservoirStateWellBindingTest {
  /**
   * Builds an initialized hydrocarbon/water template.
   *
   * @param pressureBara pressure in bara
   * @return initialized SRK fluid
   */
  private SystemInterface fluid(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(358.15, pressureBara);
    fluid.addComponent("methane", 0.79);
    fluid.addComponent("ethane", 0.20);
    fluid.addComponent("water", 0.01);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    return fluid;
  }

  /**
   * Builds a proxy with declining pressure and PI and evolving composition.
   *
   * @return reservoir-state proxy
   */
  private ReservoirStateProxy state() {
    return new ReservoirStateProxy("simulator W1", new double[] {0.0, 1000.0}, new double[] {220.0, 190.0})
        .setWaterCut(new double[] {0.0, 0.20}).setGasOilRatio(new double[] {120.0, 150.0})
        .setProductivityIndex(new double[] {20.0, 10.0}).setComposition(new String[] {"methane", "ethane"},
            new double[][] {{0.8, 0.2}, {0.6, 0.4}}, new double[] {9000.0, 9000.0});
  }

  /** Reservoir state updates the existing WellSystem boundary without adding a second graph. */
  @Test
  void appliesStateToExistingWellAndCanonicalNetwork() {
    ReservoirStateProxy state = state();
    Stream reservoir = new Stream("reservoir boundary", fluid(220.0));
    reservoir.setFlowRate(0.01, "MSm3/day");
    reservoir.run();
    WellSystem well = new WellSystem("canonical producer", reservoir);
    well.setTubingLength(1800.0, "m");
    well.setTubingDiameter(0.12, "m");
    ReservoirStateWellBinding binding = new ReservoirStateWellBinding(state, well,
        new MolarFeedAdapter(null, fluid(220.0)));

    binding.apply();
    assertSame(reservoir, well.getInletStream());
    assertEquals(220.0, well.getReservoirPressure("bara"), 1.0e-12);
    assertEquals(20.0, binding.getLastAppliedProductivityIndex(), 1.0e-12);
    assertEquals(2200.0, well.generateIPRCurve(3)[0][1], 1.0e-9, "The simulator PI is linear in pressure drawdown");

    FieldNetworkTopology topology = new FieldNetworkTopology("state-bound canonical network");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(fluid(150.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addLiveWellNode("producer", "XT-901", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 70.0, -250.0);
    topology.addJunction("manifold", "MA-901", NodeRole.MANIFOLD, Service.PRODUCTION, -250.0);
    topology.addFixedPressureSink("host", "HOST-901", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    topology.addPipe("branch", "FL-901", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "producer",
        "production", "manifold", "slot-1", 1500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("riser", "RI-901", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "manifold", "riser",
        "host", "arrival", 500.0, 0.25, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.6);
    coupler.setTolerances(1.0e-5, 1.0e-4);
    coupler.bindProductionWell("producer", well);

    CouplingResult initial = coupler.run();
    assertTrue(initial.isConverged(), initial.getMessage());
    assertEquals(0.0, initial.getMassBalanceResidualKgS(), 1.0e-8);
    double initialRate = initial.getWellResults().get(0).getTargetRateKgS();

    state.produce(500.0, 0.0, 0.0);
    binding.apply();
    CouplingResult depleted = coupler.run();
    assertTrue(depleted.isConverged(), depleted.getMessage());
    assertTrue(depleted.getWellResults().get(0).getTargetRateKgS() < initialRate);
    assertSame(reservoir, well.getInletStream());
    assertEquals(205.0, binding.getLastAppliedPressureBara(), 1.0e-12);
    assertEquals(15.0, binding.getLastAppliedProductivityIndex(), 1.0e-12);
    assertEquals(0.0, depleted.getMassBalanceResidualKgS(), 1.0e-8);

    state.produce(600.0, 0.0, 0.0);
    binding.apply();
    assertTrue(binding.isOutsideValidity());
  }

  /** Invalid proxy data and invalid explicit rates fail before mutating the canonical well. */
  @Test
  void rejectsIncompleteStateAndInvalidFeedRates() {
    ReservoirStateProxy missingPi = new ReservoirStateProxy("missing PI", new double[] {0.0, 1.0},
        new double[] {200.0, 190.0});
    WellSystem well = new WellSystem("well", new Stream("reservoir", fluid(200.0)));
    assertThrows(IllegalStateException.class, () -> new ReservoirStateWellBinding(missingPi, well).apply());

    MolarFeedAdapter adapter = new MolarFeedAdapter(null, fluid(200.0));
    assertThrows(IllegalArgumentException.class,
        () -> adapter.wellFeed(state(), Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0));
  }
}
