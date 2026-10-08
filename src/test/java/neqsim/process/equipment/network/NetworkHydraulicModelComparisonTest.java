package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.equipment.network.NetworkHydraulicModelComparison.EdgeComparison;
import neqsim.process.equipment.network.NetworkHydraulicModelComparison.ModelProfiles;
import neqsim.process.equipment.network.NetworkHydraulicModelComparison.Result;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Field-scale acceptance checks for same-topology hydraulic model comparison. */
class NetworkHydraulicModelComparisonTest {
  /**
   * Create a rich production fluid.
   *
   * @param methane methane mole fraction
   * @return SRK fluid with normalized methane/n-heptane composition
   */
  private SystemInterface richFluid(double methane) {
    SystemInterface fluid = new SystemSrkEos(303.15, 120.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-heptane", 1.0 - methane);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Create a two-well, conservative-template, flowline and riser topology.
   *
   * @param totalRateKgS total field production rate in kg/s
   * @return typed field topology
   */
  private FieldNetworkTopology topology(double totalRateKgS) {
    FieldNetworkTopology topology = new FieldNetworkTopology("qualification tieback");
    topology.addPressureSource("well-a", "WI-A", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 120.0,
        totalRateKgS * 1800.0, -100.0);
    topology.addPressureSource("well-b", "WI-B", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 120.0,
        totalRateKgS * 1800.0, -100.0);
    topology.addJunction("template", "TM-01", NodeRole.TEMPLATE, Service.PRODUCTION, -100.0);
    topology.addJunction("plet", "PLET-01", NodeRole.PLET, Service.PRODUCTION, -100.0);
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, totalRateKgS * 3600.0, 0.0);

    NetworkPipe branchA = topology.addPipe("branch-a", "FL-A", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well-a", "out", "template", "in-a", 500.0, 0.20, PipeModelType.BEGGS_BRILL);
    NetworkPipe branchB = topology.addPipe("branch-b", "FL-B", EdgeRole.FLOWLINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "well-b", "out", "template", "in-b", 500.0, 0.20, PipeModelType.BEGGS_BRILL);
    NetworkPipe flowline = topology.addPipe("flowline", "PL-01", EdgeRole.PIPELINE, Service.PRODUCTION,
        FlowDirection.FROM_TO, "template", "out", "plet", "in", 20000.0, 0.30, PipeModelType.DARCY_WEISBACH);
    NetworkPipe riser = topology.addPipe("riser", "RI-01", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO,
        "plet", "out", "host", "in", 500.0, 0.25, PipeModelType.DARCY_WEISBACH);
    branchA.setMultiphaseSegments(4);
    branchB.setMultiphaseSegments(4);
    flowline.setMultiphaseSegments(8);
    flowline.setAmbientTemperature(277.15);
    flowline.setOverallHeatTransferCoeff(2.0);
    riser.setMultiphaseSegments(6);
    riser.setAmbientTemperature(285.15);
    riser.setOverallHeatTransferCoeff(3.0);

    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(richFluid(0.90));
    network.setNodeFluid("well-a", richFluid(0.92));
    network.setNodeFluid("well-b", richFluid(0.86));
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(80);
    network.setCompositionalHydraulicsEnabled(true);
    network.setThermalHydraulicsEnabled(true);
    return topology;
  }

  /** Compare pressure, temperature, holdup and phase velocities without mutating topology. */
  @Test
  void comparesProfilesAndInitializesTransientState() {
    FieldNetworkTopology topology = topology(2.0);
    LoopedPipeNetwork original = topology.getHydraulicNetwork();
    NetworkHydraulicModelComparison comparison = new NetworkHydraulicModelComparison(topology);
    Result result = comparison.compare(Arrays.asList("flowline", "riser"), UUID.randomUUID());

    assertEquals(new HashSet<String>(Arrays.asList("well-a", "well-b")), original.getAssignedNodeFluidNames());
    assertEquals(PipeModelType.DARCY_WEISBACH, original.getEffectiveHydraulicModelType("flowline"));
    assertEquals(PipeModelType.DARCY_WEISBACH, original.getEffectiveHydraulicModelType("riser"));
    assertEquals("NOT_RUN", original.getPipe("flowline").getHydraulicModelStatus());
    assertFalse(original.isConverged());
    assertEquals(0.0, result.getBeggsBrillMassBalanceErrorKgS(), 1.0e-7);
    assertEquals(0.0, result.getTwoFluidMassBalanceErrorKgS(), 1.0e-7);

    for (String edgeId : result.getEdgeIds()) {
      EdgeComparison edge = result.getEdgeComparison(edgeId);
      assertEquals(2.0, edge.getBeggsBrillFlowRateKgS(), 1.0e-6);
      assertEquals(2.0, edge.getTwoFluidFlowRateKgS(), 1.0e-6);
      assertTrue(edge.getBeggsBrillPressureDropPa() > 0.0);
      assertTrue(edge.getTwoFluidPressureDropPa() > 0.0);
      assertTrue(Double.isFinite(edge.getRelativePressureDropDifference()));
      assertTrue(Math.abs(edge.getRelativePressureDropDifference()) < 10.0);
      assertTrue(Math.abs(edge.getOutletTemperatureDifferenceK()) < 50.0);
      assertTrue(Math.abs(edge.getAverageLiquidHoldupDifference()) < 0.8);
      assertTrue(Double.isFinite(edge.getRelativeAverageGasSuperficialVelocityDifference()));
      assertTrue(Math.abs(edge.getRelativeAverageGasSuperficialVelocityDifference()) < 10.0);
      assertTrue(Double.isFinite(edge.getRelativeAverageLiquidSuperficialVelocityDifference()));
      assertTrue(Math.abs(edge.getRelativeAverageLiquidSuperficialVelocityDifference()) < 10.0);
      assertProfilesPhysical(edge.getBeggsBrillProfiles());
      assertProfilesPhysical(edge.getTwoFluidProfiles());
    }

    EdgeComparison flowline = result.getEdgeComparison("flowline");
    TwoFluidPipe initialized = result.createInitializedTwoFluidPipe("flowline");
    TwoFluidPipe independent = result.createInitializedTwoFluidPipe("flowline");
    assertTrue(initialized.isSteadyStateConverged());
    assertArrayEquals(flowline.getTwoFluidProfiles().getPressurePa(), initialized.getPressureProfile(), 1.0e-8);
    assertArrayEquals(flowline.getTwoFluidProfiles().getTemperatureK(), initialized.getTemperatureProfile(), 1.0e-8);
    initialized.setDiameter(0.35);
    assertEquals(0.30, independent.getDiameter(), 1.0e-12);
    independent.setTransactionalTransientEnabled(true);
    independent.runTransient(0.001, UUID.randomUUID());
    assertNotNull(independent.getLastMassBalanceReport());
    assertTrue(Double
        .isFinite(independent.getLastMassBalanceReport().getRelativeResidual(TwoFluidMassBalanceReport.Phase.TOTAL)));
  }

  /** Verify conservation and comparison evidence at a nearby field rate. */
  @Test
  void nearbyRateRemainsConservative() {
    FieldNetworkTopology topology = topology(1.5);
    Result result = new NetworkHydraulicModelComparison(topology).compare(Arrays.asList("flowline"), UUID.randomUUID());
    EdgeComparison flowline = result.getEdgeComparison("flowline");
    assertEquals(1.5, flowline.getBeggsBrillFlowRateKgS(), 1.0e-6);
    assertEquals(1.5, flowline.getTwoFluidFlowRateKgS(), 1.0e-6);
    assertEquals(0.0, result.getBeggsBrillMassBalanceErrorKgS(), 1.0e-7);
    assertEquals(0.0, result.getTwoFluidMassBalanceErrorKgS(), 1.0e-7);
    assertTrue(Double.isFinite(flowline.getRelativePressureDropDifference()));
  }

  /** Reject missing evidence and unknown comparison edges explicitly. */
  @Test
  void rejectsUninitializedAndUnknownEdges() {
    FieldNetworkTopology topology = topology(2.0);
    assertThrows(IllegalStateException.class,
        () -> topology.getHydraulicNetwork().getPipe("flowline").createInitializedTwoFluidPipe());
    NetworkHydraulicModelComparison comparison = new NetworkHydraulicModelComparison(topology);
    assertThrows(IllegalArgumentException.class, () -> comparison.compare(Arrays.asList("missing"), UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> comparison.compare(Arrays.<String>asList(), UUID.randomUUID()));
  }

  /**
   * Verify finite and physically bounded model profiles.
   *
   * @param profiles normalized profile evidence
   */
  private void assertProfilesPhysical(ModelProfiles profiles) {
    assertTrue(profiles.getPressurePa().length > 1);
    assertTrue(profiles.getTemperatureK().length > 0);
    assertTrue(profiles.getLiquidHoldup().length > 0);
    assertTrue(profiles.getGasSuperficialVelocityMs().length > 0);
    assertTrue(profiles.getLiquidSuperficialVelocityMs().length > 0);
    for (double pressure : profiles.getPressurePa()) {
      assertTrue(Double.isFinite(pressure));
      assertTrue(pressure > 0.0);
    }
    for (double temperature : profiles.getTemperatureK()) {
      assertTrue(Double.isFinite(temperature));
      assertTrue(temperature > 200.0 && temperature < 400.0);
    }
    for (double holdup : profiles.getLiquidHoldup()) {
      assertTrue(Double.isFinite(holdup));
      assertTrue(holdup >= 0.0 && holdup <= 1.0);
    }
    for (double velocity : profiles.getGasSuperficialVelocityMs()) {
      assertTrue(Double.isFinite(velocity));
      assertTrue(velocity >= 0.0);
    }
    for (double velocity : profiles.getLiquidSuperficialVelocityMs()) {
      assertTrue(Double.isFinite(velocity));
      assertTrue(velocity >= 0.0);
    }
  }
}
