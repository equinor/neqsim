package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.reservoir.RadialDarcyWaterInjectivity;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Synthetic shared-header acceptance for explicit liquid mass injectivity on the canonical topology.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldLiquidInjectionAcceptanceTest {
  /**
   * Create a water stream using one consistent thermodynamic definition.
   *
   * @param name stream identity
   * @param pressure pressure in bara
   * @return water stream
   */
  private Stream water(String name, double pressure) {
    SystemSrkEos fluid = new SystemSrkEos(298.15, pressure);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(1.0, "kg/sec");
    stream.run();
    return stream;
  }

  /**
   * Build two live injectors behind one canonical pressure-controlled pump and header.
   *
   * @return process-compatible coupled network
   */
  private FieldWellNetworkProcessUnit fixture() {
    FieldNetworkTopology topology = new FieldNetworkTopology("liquid injection field");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water("template", 180.0).getThermoSystem());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.01);
    topology.addPressureSource("supply", "SUP-1", NodeRole.INJECTION_SOURCE, Service.INJECTION, 10.0, 0.0, 0.0);
    topology.addJunction("header", "MA-1", NodeRole.MANIFOLD, Service.INJECTION, 0.0);
    topology.addPump("pump", "P-1", Service.INJECTION, FlowDirection.FROM_TO, "supply", "out", "header", "in", 180.0,
        0.8);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.6);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setMaximumIterations(100);
    for (int i = 0; i < 2; i++) {
      String node = "injector-" + i;
      topology.addLiveWellNode(node, "XT-" + i, NodeRole.INJECTION_WELL, Service.INJECTION, 150.0, 0.0);
      topology.addPipe("branch-" + i, "WI-" + i, EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO, "header",
          "slot-" + i, node, "in", 1000.0 + i * 1000.0, 0.10, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
      WellFlow well = new WellFlow(node);
      well.setInletStream(water("feed-" + i, 180.0));
      well.addLiquidInjectionZone("target", water("target-" + i, 100.0), 100.0, 0.01, 200.0);
      well.addLiquidInjectionZone("secondary", water("secondary-" + i, 110.0), 110.0, 0.005, 195.0);
      well.setTargetZone("target");
      coupler.bindInjectionWell(node, well);
    }
    return new FieldWellNetworkProcessUnit("injection", coupler);
  }

  /**
   * Build two pressure-dependent ordinary-water injectors on the same canonical topology.
   *
   * @return process-compatible coupled network
   */
  private FieldWellNetworkProcessUnit radialFixture() {
    FieldNetworkTopology topology = new FieldNetworkTopology("radial water injection field");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(water("radial template", 180.0).getThermoSystem());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.01);
    topology.addPressureSource("supply", "SUP-R", NodeRole.INJECTION_SOURCE, Service.INJECTION, 10.0, 0.0, 0.0);
    topology.addJunction("header", "MA-R", NodeRole.MANIFOLD, Service.INJECTION, 0.0);
    topology.addPump("pump", "P-R", Service.INJECTION, FlowDirection.FROM_TO, "supply", "out", "header", "in", 180.0,
        0.8);
    FieldWellNetworkCoupler coupler = new FieldWellNetworkCoupler(topology);
    coupler.setRelaxationFactor(0.6);
    coupler.setTolerances(1.0e-8, 1.0e-6);
    coupler.setMaximumIterations(100);
    for (int i = 0; i < 2; i++) {
      String node = "radial-injector-" + i;
      topology.addLiveWellNode(node, "XT-R" + i, NodeRole.INJECTION_WELL, Service.INJECTION, 150.0, 0.0);
      topology.addPipe("radial-branch-" + i, "WI-R" + i, EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO,
          "header", "slot-" + i, node, "in", 1000.0 + i * 1000.0, 0.10, LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
      WellFlow well = new WellFlow(node);
      well.setInletStream(water("radial-feed-" + i, 180.0));
      well.addRadialDarcyWaterInjectionZone("target", water("radial-target-" + i, 100.0), 100.0,
          RadialDarcyWaterInjectivity.iapwsPureWater(100.0, 20.0, 1.0, 500.0, 0.1, 300.0), 200.0);
      well.addRadialDarcyWaterInjectionZone("secondary", water("radial-secondary-" + i, 110.0), 110.0,
          RadialDarcyWaterInjectivity.iapwsPureWater(50.0, 10.0, 2.0, 500.0, 0.1, 300.0), 195.0);
      well.setTargetZone("target");
      coupler.bindInjectionWell(node, well);
    }
    return new FieldWellNetworkProcessUnit("radial injection", coupler);
  }

  /**
   * Check the analytical well law against the solved node pressure and conservative edge demand.
   *
   * @param unit coupled process unit
   */
  private void assertAccepted(FieldWellNetworkProcessUnit unit) {
    assertTrue(unit.solved(), unit.getCoupler().getLastResult().getMessage());
    LoopedPipeNetwork network = unit.getTopology().getHydraulicNetwork();
    double total = 0.0;
    for (int i = 0; i < 2; i++) {
      WellFlow well = (WellFlow) unit.getTopology().getBoundEquipment("injector-" + i);
      double pressure = network.getNodePressure("injector-" + i);
      double expected = 0.01 * Math.max(pressure - well.getLayer(0).reservoirPressure, 0.0)
          + 0.005 * Math.max(pressure - 110.0, 0.0);
      double flow = network.getPipe("branch-" + i).getFlowRate();
      assertEquals(expected, flow, 3.0e-8);
      assertEquals(expected, well.getOutletStream().getFlowRate("kg/sec"), 3.0e-8);
      assertEquals(1.0, well.getZoneAllocationFractions()[0] + well.getZoneAllocationFractions()[1], 1.0e-12);
      assertFalse(well.getZoneFractureRisk()[0]);
      assertTrue(pressure > 110.0 && pressure < 180.0);
      total += flow;
    }
    assertEquals(total, network.getPipe("pump").getFlowRate(), 1.0e-8);
    assertEquals(0.0, unit.getCoupler().getLastResult().getMassBalanceResidualKgS(), 1.0e-8);
    assertTrue(unit.getPower("kW") > 0.0);
  }

  /** The same field supports liquid injectivity, mixed hydraulic fidelity, host power and restored boundaries. */
  @Test
  void conservesSharedHeaderAndHostPowerAcrossChanges() {
    FieldWellNetworkProcessUnit unit = fixture();
    ProcessSystem system = new ProcessSystem();
    system.add(unit);
    ProcessModel host = new ProcessModel();
    host.add("injection", system);
    host.run();
    assertAccepted(unit);
    assertEquals(unit.getPower("kW"), host.getPower("kW"), 1.0e-10);
    LoopedPipeNetwork network = unit.getTopology().getHydraulicNetwork();
    LoopedPipeNetwork.NetworkPipe branch = network.getPipe("branch-0");
    double original = branch.getFlowRate();
    WellFlow well = (WellFlow) unit.getTopology().getBoundEquipment("injector-0");
    well.getLayer(0).setReservoirPressure(120.0, "bara");
    host.run();
    assertAccepted(unit);
    assertTrue(branch.getFlowRate() < original);
    well.getLayer(0).setReservoirPressure(100.0, "bara");
    branch.setHydraulicModelType(LoopedPipeNetwork.PipeModelType.BEGGS_BRILL);
    host.run();
    assertAccepted(unit);
    assertEquals("BEGGS_BRILL", branch.getHydraulicModelStatus());
    assertSame(branch, network.getPipe("branch-0"));
    assertSame(well, unit.getTopology().getBoundEquipment("injector-0"));
    branch.setHydraulicModelType(LoopedPipeNetwork.PipeModelType.DARCY_WEISBACH);
    host.run();
    assertAccepted(unit);
    assertEquals(original, branch.getFlowRate(), 3.0e-8);
  }

  /** Replaying a copied live network retains basis and rejects fracture excursions without clipping. */
  @Test
  void copiesDefinitionAndRejectsFractureExcursion() {
    FieldWellNetworkProcessUnit original = fixture();
    original.run();
    assertAccepted(original);
    FieldWellNetworkProcessUnit copy = (FieldWellNetworkProcessUnit) original.copy();
    copy.run();
    assertAccepted(copy);
    WellFlow copiedWell = (WellFlow) copy.getTopology().getBoundEquipment("injector-0");
    WellFlow originalWell = (WellFlow) original.getTopology().getBoundEquipment("injector-0");
    assertTrue(copiedWell.isLiquidMassInjection());
    assertNotSame(originalWell, copiedWell);
    copiedWell.getLayer(0).setFracturePressure(170.0, "bara");
    copy.run();
    assertFalse(copy.solved());
    assertEquals(FieldWellNetworkCoupler.WellStatus.FRACTURE_LIMIT_EXCEEDED,
        copy.getCoupler().getLastResult().getWellResults().get(0).getStatus());
    assertFalse(originalWell.getZoneFractureRisk()[0]);
    copiedWell.getLayer(0).setFracturePressure(200.0, "bara");
    copy.run();
    assertAccepted(copy);
  }

  /** Pressure-dependent well demand remains conservative through the coupled network inner iteration. */
  @Test
  void closesLiveRadialDarcyDemandAndRefreshesReservoirBoundary() {
    FieldWellNetworkProcessUnit unit = radialFixture();
    unit.run();
    assertTrue(unit.solved(), unit.getCoupler().getLastResult().getMessage());
    LoopedPipeNetwork network = unit.getTopology().getHydraulicNetwork();
    double total = 0.0;
    for (int i = 0; i < 2; i++) {
      WellFlow well = (WellFlow) unit.getTopology().getBoundEquipment("radial-injector-" + i);
      double branchFlow = network.getPipe("radial-branch-" + i).getFlowRate();
      double wellFlow = 0.0;
      for (int zone = 0; zone < well.getNumberOfLayers(); zone++) {
        RadialDarcyWaterInjectivity.Result evidence = well.getLayer(zone).getRadialDarcyWaterInjectivityResult();
        wellFlow += evidence.getStandardVolumeRateSm3PerDay() * evidence.getStandardDensityKgPerSm3() / 86400.0;
      }
      assertEquals(wellFlow, branchFlow, 3.0e-8);
      assertEquals(wellFlow, well.getOutletStream().getFlowRate("kg/sec"), 3.0e-8);
      total += branchFlow;
    }
    assertEquals(total, network.getPipe("pump").getFlowRate(), 1.0e-8);
    assertEquals(0.0, unit.getCoupler().getLastResult().getMassBalanceResidualKgS(), 1.0e-8);
    WellFlow first = (WellFlow) unit.getTopology().getBoundEquipment("radial-injector-0");
    double original = network.getPipe("radial-branch-0").getFlowRate();
    first.getLayer(0).setReservoirPressure(120.0, "bara");
    unit.run();
    assertTrue(unit.solved(), unit.getCoupler().getLastResult().getMessage());
    assertEquals(120.0, first.getLayer(0).getRadialDarcyWaterInjectivityResult().getReservoirPressureBara(), 0.0);
    assertTrue(network.getPipe("radial-branch-0").getFlowRate() < original);
    assertEquals(0.0, unit.getCoupler().getLastResult().getMassBalanceResidualKgS(), 1.0e-8);
  }
}
