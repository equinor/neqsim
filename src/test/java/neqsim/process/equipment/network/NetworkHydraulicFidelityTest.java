package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.LoopedPipeNetwork.NetworkPipe;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression and pipe-composition checks for edge-local hydraulic fidelity.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class NetworkHydraulicFidelityTest {
  /**
   * Build the synthetic gas used by network and standalone calculations.
   *
   * @return SRK gas at 100 bara and 298.15 K
   */
  private SystemInterface gas() {
    SystemInterface fluid = new SystemSrkEos(298.15, 100.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Build a demand-driven network with no fixed delivery pressure.
   *
   * @param reverse whether edge identity opposes the physical flow
   * @return configured network
   */
  private LoopedPipeNetwork network(boolean reverse) {
    LoopedPipeNetwork network = new LoopedPipeNetwork("synthetic SURF");
    network.setFluidTemplate(gas());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(1.0);
    network.setMaxIterations(50);
    network.addSourceNode("well", 100.0, 3600.0);
    network.getNode("well").setTemperature(298.15);
    network.addSinkNode("host", 3600.0);
    NetworkPipe edge = network.addPipe(reverse ? "host" : "well", reverse ? "well" : "host", "riser", 100.0, 0.3);
    edge.setMultiphaseSegments(4);
    return network;
  }

  /** Verify backward compatibility, override precedence, stable identity and JSON replay. */
  @Test
  void selectionAndReplayPreserveTopology() {
    LoopedPipeNetwork network = network(false);
    NetworkPipe edge = network.getPipe("riser");
    assertEquals(PipeModelType.DARCY_WEISBACH, network.getEffectiveHydraulicModelType("riser"));
    network.setPipeModelType(PipeModelType.BEGGS_BRILL);
    assertEquals(PipeModelType.BEGGS_BRILL, network.getEffectiveHydraulicModelType("riser"));
    edge.setHydraulicModelType(PipeModelType.TWO_FLUID);
    edge.setAmbientTemperature(280.0);
    edge.setOverallHeatTransferCoeff(8.0);
    network.setPipeModelType(PipeModelType.DARCY_WEISBACH);
    LoopedPipeNetwork restored = LoopedPipeNetwork.fromJson(network.toJson());
    assertEquals(PipeModelType.TWO_FLUID, restored.getEffectiveHydraulicModelType("riser"));
    assertEquals("well", restored.getPipe("riser").getFromNode());
    assertEquals("host", restored.getPipe("riser").getToNode());
    assertEquals(100.0, restored.getPipe("riser").getLength());
    assertEquals(4, restored.getPipe("riser").getMultiphaseSegments());
    assertEquals(280.0, restored.getPipe("riser").getAmbientTemperature());
    assertEquals(8.0, restored.getPipe("riser").getOverallHeatTransferCoeff());
    assertEquals(LoopedPipeNetwork.NetworkElementType.PIPE, edge.getElementType());
    edge.setHydraulicModelType(null);
    assertEquals(PipeModelType.DARCY_WEISBACH, network.getEffectiveHydraulicModelType("riser"));
    assertNull(edge.getTwoFluidModel());
    assertEquals("NOT_RUN", edge.getHydraulicModelStatus());
  }

  /** Check legacy explicit multiphase elements keep Beggs-Brill unless overridden. */
  @Test
  void explicitMultiphaseDefaultAndUnsupportedElements() {
    LoopedPipeNetwork network = network(false);
    NetworkPipe branch = network.addMultiphasePipe("well", "host", "branch", 200.0, 0.2);
    assertEquals(PipeModelType.BEGGS_BRILL, network.getEffectiveHydraulicModelType("branch"));
    branch.setHydraulicModelType(PipeModelType.DARCY_WEISBACH);
    assertEquals(PipeModelType.DARCY_WEISBACH, network.getEffectiveHydraulicModelType("branch"));
    NetworkPipe choke = network.addChoke("well", "host", "choke", 10.0, 50.0);
    assertThrows(IllegalArgumentException.class, () -> choke.setHydraulicModelType(PipeModelType.TWO_FLUID));
    assertThrows(IllegalArgumentException.class, () -> network.getEffectiveHydraulicModelType("missing"));
    network.setSolverType(LoopedPipeNetwork.SolverType.SEQUENTIAL);
    assertThrows(IllegalStateException.class, network::run);
    assertFalse(network.isConverged());
  }

  /** Compare network two-fluid pressure loss against the same standalone finite-volume pipe. */
  @Test
  void twoFluidMatchesStandaloneAndSwitchesBack() {
    LoopedPipeNetwork network = network(false);
    NetworkPipe edge = network.getPipe("riser");
    edge.setHydraulicModelType(PipeModelType.TWO_FLUID);
    network.run();
    assertTrue(network.isConverged());
    assertEquals("TWO_FLUID_CONVERGED", edge.getHydraulicModelStatus());
    assertEquals(1.0, edge.getFlowRate(), 1.0e-8);
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-8);
    assertNotNull(edge.getTwoFluidModel());
    Stream inlet = new Stream("standalone inlet", gas());
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();
    TwoFluidPipe reference = new TwoFluidPipe("standalone", inlet);
    reference.setLength(100.0);
    reference.setDiameter(0.3);
    reference.setRoughness(edge.getRoughness());
    reference.setNumberOfSections(4);
    reference.setCellFaceElevationProfile(new double[] {0.0, 0.0, 0.0, 0.0, 0.0});
    reference.run();
    assertTrue(reference.isSteadyStateConverged());
    double drop = inlet.getPressure("Pa") - reference.getOutletStream().getPressure("Pa");
    assertTrue(drop > 0.0);
    assertEquals(drop, edge.getHeadLoss(), 1.0);
    assertEquals(network.getNodePressure("well") - network.getNodePressure("host"), edge.getHeadLoss() / 1.0e5, 1.0e-5);
    assertEquals(1.0, edge.getOutletFluid().getFlowRate("kg/sec"), 1.0e-8);
    edge.setHydraulicModelType(PipeModelType.BEGGS_BRILL);
    network.run();
    assertTrue(network.isConverged());
    assertNull(edge.getTwoFluidModel());
    assertNotNull(edge.getBBModel());
    assertEquals("BEGGS_BRILL", edge.getHydraulicModelStatus());
  }

  /** Check reverse traversal uses physical upstream fluid and signed hydraulic head loss. */
  @Test
  void reverseTwoFluidUsesSamePhysicalRoute() {
    LoopedPipeNetwork forward = network(false);
    LoopedPipeNetwork reverse = network(true);
    forward.getPipe("riser").setHydraulicModelType(PipeModelType.TWO_FLUID);
    reverse.getPipe("riser").setHydraulicModelType(PipeModelType.TWO_FLUID);
    forward.run();
    reverse.run();
    assertTrue(reverse.isConverged());
    assertEquals(-1.0, reverse.getPipe("riser").getFlowRate(), 1.0e-8);
    assertEquals(forward.getNodePressure("host"), reverse.getNodePressure("host"), 1.0e-5);
    assertEquals(forward.getPipe("riser").getHeadLoss(), -reverse.getPipe("riser").getHeadLoss(), 1.0);
    assertFalse(reverse.getPipe("riser").isThermodynamicStateForward());
  }

  /** Verify cached Beggs-Brill geometry is refreshed after diameter and elevation changes. */
  @Test
  void beggsBrillRefreshesGeometry() {
    LoopedPipeNetwork network = network(false);
    NetworkPipe edge = network.getPipe("riser");
    edge.setHydraulicModelType(PipeModelType.BEGGS_BRILL);
    network.getNode("host").setElevation(10.0);
    network.run();
    edge.setDiameter(0.4);
    network.getNode("host").setElevation(0.0);
    network.run();
    assertTrue(network.isConverged());
    Stream inlet = new Stream("reference", gas());
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();
    PipeBeggsAndBrills reference = new PipeBeggsAndBrills("BB reference", inlet);
    reference.setLength(100.0);
    reference.setDiameter(0.4);
    reference.setPipeWallRoughness(edge.getRoughness());
    reference.setNumberOfIncrements(4);
    reference.setAngle(0.0);
    reference.run();
    assertEquals(inlet.getPressure("Pa") - reference.getOutletStream().getPressure("Pa"), edge.getHeadLoss(), 1.0);
  }

  /** Exercise four branches, conservative manifold flow and a different trunk model. */
  @Test
  void fourWellTemplateUsesMixedFidelity() {
    LoopedPipeNetwork network = new LoopedPipeNetwork("four well template");
    network.setFluidTemplate(gas());
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setTolerance(0.001);
    network.setMaxIterations(80);
    network.addJunctionNode("template");
    network.addSinkNode("host", 14400.0);
    for (int index = 0; index < 4; index++) {
      String well = "well " + index;
      network.addSourceNode(well, 100.0, 3600.0);
      network.getNode(well).setTemperature(298.15);
      NetworkPipe branch = network.addPipe(well, "template", "branch " + index, 50.0, 0.3);
      branch.setMultiphaseSegments(4);
      branch.setHydraulicModelType(PipeModelType.BEGGS_BRILL);
    }
    NetworkPipe trunk = network.addPipe("template", "host", "trunk", 100.0, 0.5);
    trunk.setMultiphaseSegments(4);
    trunk.setHydraulicModelType(PipeModelType.TWO_FLUID);
    network.run();
    assertTrue(network.isConverged());
    assertEquals(4.0, trunk.getFlowRate(), 1.0e-7);
    double branchTotal = 0.0;
    for (int index = 0; index < 4; index++) {
      NetworkPipe branch = network.getPipe("branch " + index);
      assertEquals("BEGGS_BRILL", branch.getHydraulicModelStatus());
      assertEquals(1.0, branch.getFlowRate(), 1.0e-5);
      branchTotal += branch.getFlowRate();
    }
    assertEquals(branchTotal, trunk.getFlowRate(), 1.0e-7);
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-7);
    assertTrue(network.getNodePressure("template") > network.getNodePressure("host"));
    assertEquals("TWO_FLUID_CONVERGED", trunk.getHydraulicModelStatus());
    LoopedPipeNetwork replay = LoopedPipeNetwork.fromJson(network.toJson());
    replay.setFluidTemplate(gas());
    replay.run();
    assertTrue(replay.isConverged());
    assertEquals(network.getNodePressure("host"), replay.getNodePressure("host"), 1.0e-5);
  }

  /** Reject an undeliverable two-fluid rate without a silent Darcy substitution. */
  @Test
  void pressureFloorCannotBecomeConvergedNetwork() {
    LoopedPipeNetwork network = network(false);
    NetworkPipe edge = network.getPipe("riser");
    edge.setDiameter(0.01);
    edge.setHydraulicModelType(PipeModelType.TWO_FLUID);
    assertThrows(IllegalStateException.class, network::run);
    assertFalse(network.isConverged());
    assertEquals("TWO_FLUID_FAILED", edge.getHydraulicModelStatus());
  }

  /** Compare a gas/oil terrain route against an identically configured standalone pipe. */
  @Test
  void multiphaseTerrainUsesExistingTwoFluidClosures() {
    SystemInterface rich = new SystemSrkEos(298.15, 100.0);
    rich.addComponent("methane", 0.9);
    rich.addComponent("n-heptane", 0.1);
    rich.setMixingRule("classic");
    rich.setMultiPhaseCheck(true);
    LoopedPipeNetwork network = network(false);
    network.setFluidTemplate(rich);
    NetworkPipe edge = network.getPipe("riser");
    edge.setElevationProfile(new double[] {0.0, 50.0, 100.0}, new double[] {0.0, -5.0, 0.0});
    edge.setHydraulicModelType(PipeModelType.TWO_FLUID);
    network.run();
    assertTrue(network.isConverged());
    assertTrue(edge.getInletFluid().getNumberOfPhases() >= 2);
    Stream inlet = new Stream("rich reference", rich.clone());
    inlet.setFlowRate(1.0, "kg/sec");
    inlet.run();
    TwoFluidPipe reference = new TwoFluidPipe("rich two-fluid", inlet);
    reference.setLength(100.0);
    reference.setDiameter(0.3);
    reference.setRoughness(edge.getRoughness());
    reference.setNumberOfSections(4);
    reference.setCellFaceElevationProfile(new double[] {0.0, -2.5, -5.0, -2.5, 0.0});
    reference.run();
    assertTrue(reference.isSteadyStateConverged());
    assertEquals(inlet.getPressure("Pa") - reference.getOutletStream().getPressure("Pa"), edge.getHeadLoss(), 1.0);
    assertEquals(reference.getAverageLiquidHoldup(), edge.getLiquidHoldup(), 1.0e-5);
    assertEquals(1.0, edge.getOutletFluid().getFlowRate("kg/sec"), 1.0e-8);
  }

}
