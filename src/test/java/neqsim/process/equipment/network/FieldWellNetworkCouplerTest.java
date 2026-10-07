package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.CouplingResult;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellResult;
import neqsim.process.equipment.network.FieldWellNetworkCoupler.WellStatus;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Acceptance tests for live production and injection wells coupled to the canonical field graph.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldWellNetworkCouplerTest {
  /**
   * Build a lean-gas fluid for production tests.
   *
   * @param pressureBara pressure in bara
   * @return initialized SRK gas
   */
  private SystemInterface gas(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(358.15, pressureBara);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Build a water fluid for injection tests.
   *
   * @param pressureBara pressure in bara
   * @return initialized SRK water
   */
  private SystemInterface water(double pressureBara) {
    SystemInterface fluid = new SystemSrkEos(298.15, pressureBara);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return fluid;
  }

  /**
   * Build a live production topology with one well, manifold and host boundary.
   *
   * @return production topology
   */
  private FieldNetworkTopology productionTopology() {
    FieldNetworkTopology topology = new FieldNetworkTopology("live production network");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas(150.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addLiveWellNode("producer", "XT-701", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 70.0, -250.0);
    topology.addJunction("manifold", "MA-701", NodeRole.MANIFOLD, Service.PRODUCTION, -250.0);
    topology.addFixedPressureSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 45.0, 0.0);
    topology.addPipe("branch", "FL-701", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO, "producer",
        "production", "manifold", "slot-1", 1500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    topology.addPipe("riser", "RI-701", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "manifold", "riser",
        "host", "arrival", 500.0, 0.25, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    return topology;
  }

  /** Couple a live WellSystem and preserve inner IPR/VLP and network residual evidence. */
  @Test
  void couplesWellSystemToProductionNetwork() {
    FieldNetworkTopology topology = productionTopology();
    Stream reservoir = new Stream("production reservoir", gas(220.0));
    reservoir.setFlowRate(0.05, "MSm3/day");
    reservoir.run();
    WellSystem well = new WellSystem("live WellSystem", reservoir);
    well.setProductionIndex(0.08, "Sm3/day/bar2");
    well.setTubingLength(1800.0, "m");
    well.setTubingDiameter(0.12, "m");

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.6);
    coupler.setTolerances(1.0e-5, 1.0e-4);
    coupler.bindProductionWell("producer", well);
    CouplingResult result = coupler.run();

    assertTrue(result.isConverged(), result.getMessage());
    assertTrue(topology.getHydraulicNetwork().isConverged());
    assertTrue(result.getIterations() > 1);
    assertTrue(result.getMaximumRateResidualKgS() <= 1.0e-5);
    assertTrue(result.getMaximumPressureResidualBar() <= 1.0e-4);
    assertEquals(0.0, result.getMassBalanceResidualKgS(), 1.0e-8);
    WellResult evidence = result.getWellResults().get(0);
    assertEquals(WellStatus.FLOWING, evidence.getStatus());
    assertTrue(evidence.isInnerConverged());
    assertTrue(Math.abs(evidence.getInnerPressureResidualBar()) < 0.1);
    assertTrue(evidence.getInnerIterations() > 0);
    assertEquals(evidence.getAppliedRateKgS(), topology.getHydraulicNetwork().getPipe("branch").getFlowRate(), 1.0e-8);
    assertSame(well, topology.getBoundEquipment("producer"));
  }

  /** Couple live production WellFlow equipment and retain its real source-fluid state. */
  @Test
  void couplesProductionWellFlowToNetwork() {
    FieldNetworkTopology topology = productionTopology();
    Stream reservoir = new Stream("WellFlow reservoir", gas(200.0));
    reservoir.setFlowRate(0.01, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow("production WellFlow");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(1.0e-7);

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer", well);
    CouplingResult result = coupler.run();

    assertTrue(result.isConverged(), result.getMessage());
    assertEquals(WellStatus.FLOWING, result.getWellResults().get(0).getStatus());
    assertTrue(result.getWellResults().get(0).getTargetRateKgS() > 0.0);
    assertEquals(0.0, result.getMassBalanceResidualKgS(), 1.0e-8);
    assertTrue(topology.getHydraulicNetwork().getNodeFluid("producer").hasComponent("methane"));
  }

  /** Verify the live production operating point responds monotonically to host backpressure. */
  @Test
  void higherHostBackpressureReducesProduction() {
    FieldNetworkTopology topology = productionTopology();
    Stream reservoir = new Stream("backpressure reservoir", gas(220.0));
    reservoir.setFlowRate(0.05, "MSm3/day");
    reservoir.run();
    WellSystem well = new WellSystem("backpressure well", reservoir);
    well.setProductionIndex(0.08, "Sm3/day/bar2");
    well.setTubingLength(1800.0, "m");
    well.setTubingDiameter(0.12, "m");
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.7);
    coupler.bindProductionWell("producer", well);

    CouplingResult lowBackpressure = coupler.run();
    double lowBackpressureRate = lowBackpressure.getWellResults().get(0).getTargetRateKgS();
    topology.getHydraulicNetwork().setNodePressure("host", 70.0);
    CouplingResult highBackpressure = coupler.run();
    double highBackpressureRate = highBackpressure.getWellResults().get(0).getTargetRateKgS();

    assertTrue(lowBackpressure.isConverged());
    assertTrue(highBackpressure.isConverged());
    assertTrue(highBackpressureRate < lowBackpressureRate,
        "Production rate must fall when the fixed host backpressure increases");
    assertEquals(0.0, highBackpressure.getMassBalanceResidualKgS(), 1.0e-8);
  }

  /** Fail acceptance when a production WellFlow exceeds its configured BHP envelope. */
  @Test
  void reportsProductionBottomHolePressureLimit() {
    FieldNetworkTopology topology = productionTopology();
    Stream reservoir = new Stream("BHP-limited reservoir", gas(200.0));
    reservoir.setFlowRate(0.01, "MSm3/day");
    reservoir.run();
    WellFlow well = new WellFlow("BHP-limited WellFlow");
    well.setInletStream(reservoir);
    well.setWellProductionIndex(1.0e-7);
    well.setMaxDrawdown(20.0, "bar");
    well.setMinBottomHolePressure(180.0, "bara");
    well.useWellConstraints();

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(8);
    coupler.bindProductionWell("producer", well);
    CouplingResult result = coupler.run();

    assertFalse(result.isConverged());
    assertEquals(WellStatus.BHP_LIMIT_EXCEEDED, result.getWellResults().get(0).getStatus());
    assertTrue(well.getDrawdown() > well.getMaxDrawdown());
    assertTrue(well.getBottomHolePressure() < well.getMinBottomHolePressure());
  }

  /** Couple multi-zone injection WellFlow and conserve the accepted network flow. */
  @Test
  void couplesInjectionWellFlowAndReportsAllocation() {
    FieldNetworkTopology topology = new FieldNetworkTopology("live water injection network");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water(180.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    topology.addPressureSource("pump", "P-801", NodeRole.INJECTION_SOURCE, Service.INJECTION, 180.0, 0.0, 0.0);
    topology.addLiveWellNode("injector", "XT-801", NodeRole.INJECTION_WELL, Service.INJECTION, 145.0, -200.0);
    topology.addPipe("injection-line", "WI-801", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO, "pump",
        "discharge", "injector", "injection", 2500.0, 0.20, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);

    Stream targetZone = new Stream("target zone", water(100.0));
    Stream thiefZone = new Stream("thief zone", water(110.0));
    WellFlow well = new WellFlow("multi-zone injector");
    well.setInletStream(new Stream("injection fluid", water(180.0)));
    well.addInjectionZone("target", targetZone, 100.0, 7.0e-10, 210.0);
    well.addInjectionZone("thief", thiefZone, 110.0, 3.0e-10, 205.0);
    well.setTargetZone("target");

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.6);
    coupler.bindInjectionWell("injector", well);
    CouplingResult result = coupler.run();

    assertTrue(result.isConverged(), result.getMessage());
    assertEquals(WellStatus.FLOWING, result.getWellResults().get(0).getStatus());
    assertFalse(well.getZoneFractureRisk()[0]);
    assertFalse(well.getZoneFractureRisk()[1]);
    assertEquals(1.0, well.getZoneAllocationFractions()[0] + well.getZoneAllocationFractions()[1], 1.0e-12);
    assertTrue(well.getInjectionEfficiency() > 0.5);
    assertEquals(result.getWellResults().get(0).getAppliedRateKgS(), network.getPipe("injection-line").getFlowRate(),
        1.0e-8);
    assertEquals(0.0, result.getMassBalanceResidualKgS(), 1.0e-8);
  }

  /** Fail the coupled acceptance result when injection pressure exceeds a zone fracture limit. */
  @Test
  void reportsInjectionFractureLimit() {
    FieldNetworkTopology topology = new FieldNetworkTopology("fracture-limited injection");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water(180.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    topology.addPressureSource("pump", "P-802", NodeRole.INJECTION_SOURCE, Service.INJECTION, 180.0, 0.0, 0.0);
    topology.addLiveWellNode("injector", "XT-802", NodeRole.INJECTION_WELL, Service.INJECTION, 150.0, -100.0);
    topology.addPipe("line", "WI-802", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO, "pump", "discharge",
        "injector", "injection", 100.0, 0.30, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    WellFlow well = new WellFlow("fracture-limited injector");
    well.setInletStream(new Stream("fracture case injection fluid", water(180.0)));
    well.addInjectionZone("zone", new Stream("zone", water(100.0)), 100.0, 1.0e-10, 140.0);

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(8);
    coupler.bindInjectionWell("injector", well);
    CouplingResult result = coupler.run();

    assertFalse(result.isConverged());
    assertEquals(WellStatus.FRACTURE_LIMIT_EXCEEDED, result.getWellResults().get(0).getStatus());
    assertTrue(well.getZoneFractureRisk()[0]);
  }

  /** Reject injection coupling when the well and network thermodynamic fluid definitions differ. */
  @Test
  void rejectsIncompatibleInjectionFluid() {
    FieldNetworkTopology topology = new FieldNetworkTopology("incompatible injection fluid");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water(180.0));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    topology.addPressureSource("pump", "P-803", NodeRole.INJECTION_SOURCE, Service.INJECTION, 180.0, 0.0, 0.0);
    topology.addLiveWellNode("injector", "XT-803", NodeRole.INJECTION_WELL, Service.INJECTION, 150.0, -100.0);
    topology.addPipe("line", "WI-803", EdgeRole.PIPELINE, Service.INJECTION, FlowDirection.FROM_TO, "pump", "discharge",
        "injector", "injection", 100.0, 0.30, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    WellFlow well = new WellFlow("gas well on water network");
    well.setInletStream(new Stream("incompatible methane injection", gas(180.0)));
    well.addInjectionZone("zone", new Stream("zone", water(100.0)), 100.0, 1.0e-10, 210.0);

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setMaximumIterations(3);
    coupler.bindInjectionWell("injector", well);
    CouplingResult result = coupler.run();

    assertFalse(result.isConverged());
    assertEquals(WellStatus.FLUID_INCOMPATIBLE, result.getWellResults().get(0).getStatus());
    assertTrue(result.getWellResults().get(0).getMessage().contains("component identity"));
    assertEquals(0.0, result.getWellResults().get(0).getTargetRateKgS(), 0.0);
  }

  /** Report a physically shut-in WellSystem without injecting a negative production rate. */
  @Test
  void reportsShutInProductionWell() {
    FieldNetworkTopology topology = productionTopology();
    Stream depletedReservoir = new Stream("depleted reservoir", gas(35.0));
    depletedReservoir.run();
    WellSystem well = new WellSystem("shut-in well", depletedReservoir);
    well.setProductionIndex(0.1, "Sm3/day/bar2");

    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.bindProductionWell("producer", well);
    CouplingResult result = coupler.run();

    assertFalse(result.isConverged(), "A zero-flow pressure-indeterminate branch must not claim coupled convergence");
    assertEquals(WellStatus.SHUT_IN, result.getWellResults().get(0).getStatus());
    assertEquals(0.0, result.getWellResults().get(0).getTargetRateKgS(), 0.0);
    assertEquals(0.0, topology.getHydraulicNetwork().getPipe("branch").getFlowRate(), 1.0e-10);
  }

  /** Preserve live-well node semantics on replay while requiring runtime equipment rebinding. */
  @Test
  void replayPreservesPressureUnknownButNotRuntimeBinding() {
    FieldNetworkTopology topology = productionTopology();
    Stream reservoir = new Stream("replay reservoir", gas(200.0));
    reservoir.run();
    WellSystem well = new WellSystem("runtime-only well", reservoir);
    topology.bindEquipment("producer", well);

    FieldNetworkTopology replay = topology.copyDefinition();

    assertEquals(NodeRole.PRODUCTION_WELL, replay.getNode("producer").getRole());
    assertEquals(LoopedPipeNetwork.NodeType.SOURCE, replay.getHydraulicNetwork().getNode("producer").getType());
    assertFalse(replay.getHydraulicNetwork().getNode("producer").isPressureFixed());
    assertEquals(70.0, replay.getHydraulicNetwork().getNodePressure("producer"), 1.0e-12);
    assertNull(replay.getBoundEquipment("producer"));
  }

  /** Reject semantic service mismatches and fixed-pressure pseudo-wells before execution. */
  @Test
  void validatesLiveWellBindingsAndPressureUnknowns() {
    FieldNetworkTopology topology = productionTopology();
    WellFlow injection = new WellFlow("wrong service");
    injection.setInletStream(new Stream("wrong service injection fluid", water(180.0)));
    injection.addInjectionZone("zone", new Stream("zone", water(100.0)), 100.0, 1.0e-10, 200.0);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);

    assertThrows(IllegalArgumentException.class, () -> coupler.bindInjectionWell("producer", injection));
    assertThrows(IllegalArgumentException.class,
        () -> topology.addLiveWellNode("wrong-role", "MA-1", NodeRole.MANIFOLD, Service.PRODUCTION, 50.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> topology.addLiveWellNode("wrong-service", "XT-1",
        NodeRole.PRODUCTION_WELL, Service.INJECTION, 50.0, 0.0));
  }
}
