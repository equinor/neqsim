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
import neqsim.process.equipment.network.NetworkHydraulicModelComparison.MeshSensitivityResult;
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
    flowline.setElevationProfile(new double[] {0.0, 4000.0, 9000.0, 14000.0, 20000.0},
        new double[] {-100.0, -140.0, -80.0, -160.0, -100.0});
    flowline.setAmbientTemperatureProfile(new double[] {0.0, 9000.0, 20000.0}, new double[] {277.15, 279.15, 277.15});
    flowline.setHeatTransferProfile(new double[] {0.0, 14000.0, 20000.0}, new double[] {2.0, 3.0, 2.0});
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
    assertEquals(0.0, result.getBeggsBrillMassBalanceErrorKgS(), 1.0e-6);
    assertEquals(0.0, result.getTwoFluidMassBalanceErrorKgS(), 1.0e-6);

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
    assertEquals(0.0, result.getBeggsBrillMassBalanceErrorKgS(), 1.0e-6);
    assertEquals(0.0, result.getTwoFluidMassBalanceErrorKgS(), 1.0e-6);
    assertTrue(Double.isFinite(flowline.getRelativePressureDropDifference()));
  }

  /** Compare the same undulating route at three detached meshes without changing its definition. */
  @Test
  void aggregatesTerrainProfilesAndReportsMeshSensitivity() {
    FieldNetworkTopology topology = topology(2.0);
    NetworkPipe originalFlowline = topology.getHydraulicNetwork().getPipe("flowline");
    MeshSensitivityResult sensitivity = new NetworkHydraulicModelComparison(topology)
        .compareMeshSensitivity(Arrays.asList("flowline"), Arrays.asList(4, 8, 10), UUID.randomUUID());

    assertEquals(new HashSet<Integer>(Arrays.asList(4, 8, 10)), sensitivity.getSegmentCounts());
    assertEquals(8, originalFlowline.getMultiphaseSegments());
    assertEquals("NOT_RUN", originalFlowline.getHydraulicModelStatus());
    for (int segments : sensitivity.getSegmentCounts()) {
      EdgeComparison edge = sensitivity.getResult(segments).getEdgeComparison("flowline");
      assertEquals(0.0, sensitivity.getResult(segments).getBeggsBrillMassBalanceErrorKgS(), 1.0e-6);
      assertEquals(0.0, sensitivity.getResult(segments).getTwoFluidMassBalanceErrorKgS(), 1.0e-6);
      assertProfilesPhysical(edge.getBeggsBrillProfiles());
      assertProfilesPhysical(edge.getTwoFluidProfiles());
      assertEquals(segments, edge.getBeggsBrillProfiles().getPositionM().length);
      assertEquals(segments, edge.getTwoFluidProfiles().getPositionM().length);
      assertTrue(edge.getBeggsBrillProfiles().getPositionM()[0] > 0.0);
      assertTrue(last(edge.getBeggsBrillProfiles().getPositionM()) < 20000.0);
      assertTrue(edge.getTwoFluidProfiles().getPositionM()[0] > 0.0);
      assertTrue(last(edge.getTwoFluidProfiles().getPositionM()) < 20000.0);
      assertEquals(20000.0, sum(edge.getBeggsBrillProfiles().getCellLengthM()), 1.0e-8);
      assertEquals(20000.0, sum(edge.getTwoFluidProfiles().getCellLengthM()), 1.0e-8);
    }

    EdgeComparison coarse = sensitivity.getResult(4).getEdgeComparison("flowline");
    EdgeComparison fine = sensitivity.getResult(10).getEdgeComparison("flowline");
    assertTrue(relativeChange(fine.getBeggsBrillPressureDropPa(), coarse.getBeggsBrillPressureDropPa()) < 0.5);
    assertTrue(relativeChange(fine.getTwoFluidPressureDropPa(), coarse.getTwoFluidPressureDropPa()) < 0.5);
    assertTrue(Math.abs(fine.getBeggsBrillProfiles().getAverageLiquidHoldup()
        - coarse.getBeggsBrillProfiles().getAverageLiquidHoldup()) < 0.2);
    assertTrue(Math.abs(fine.getTwoFluidProfiles().getAverageLiquidHoldup()
        - coarse.getTwoFluidProfiles().getAverageLiquidHoldup()) < 0.2);
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
    assertThrows(IllegalArgumentException.class,
        () -> comparison.compareMeshSensitivity(Arrays.asList("flowline"), Arrays.asList(8, 8), UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class,
        () -> comparison.compareMeshSensitivity(Arrays.asList("flowline"), Arrays.asList(1, 8), UUID.randomUUID()));
  }

  /**
   * Verify finite and physically bounded model profiles.
   *
   * @param profiles normalized profile evidence
   */
  private void assertProfilesPhysical(ModelProfiles profiles) {
    int count = profiles.getPositionM().length;
    assertTrue(count > 1);
    assertEquals(count, profiles.getPressurePa().length);
    assertEquals(count, profiles.getCellLengthM().length);
    assertEquals(count, profiles.getTemperatureK().length);
    assertEquals(count, profiles.getLiquidHoldup().length);
    assertEquals(count, profiles.getGasSuperficialVelocityMs().length);
    assertEquals(count, profiles.getLiquidSuperficialVelocityMs().length);
    double previousPosition = -1.0;
    for (double position : profiles.getPositionM()) {
      assertTrue(Double.isFinite(position));
      assertTrue(position > previousPosition);
      previousPosition = position;
    }
    for (double cellLength : profiles.getCellLengthM()) {
      assertTrue(Double.isFinite(cellLength));
      assertTrue(cellLength > 0.0);
    }
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

  /**
   * Return the final value in an asserted non-empty array.
   *
   * @param values profile values
   * @return final value
   */
  private double last(double[] values) {
    return values[values.length - 1];
  }

  /**
   * Calculate an absolute change relative to the first value.
   *
   * @param value refined value
   * @param reference coarse value
   * @return absolute relative change
   */
  private double relativeChange(double value, double reference) {
    return Math.abs(value - reference) / Math.abs(reference);
  }

  /**
   * Sum finite route-cell lengths.
   *
   * @param values cell lengths in m
   * @return total length in m
   */
  private double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
