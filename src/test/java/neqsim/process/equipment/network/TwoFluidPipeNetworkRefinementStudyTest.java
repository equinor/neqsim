package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.BranchProfile;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.CaseResult;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.ProfileQuantity;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.Result;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.SpatialConvergenceReport;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.SpatialConvergenceStatus;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Representative topology and contract tests for {@link TwoFluidPipeNetworkRefinementStudy}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@Tag("slow")
class TwoFluidPipeNetworkRefinementStudyTest {
  /** Exercise direct, daisy-chain and branched multi-template numerical replay at common times. */
  @Test
  void replaysRepresentativeTopologiesAtCommonPhysicalTime() {
    assertStudy(createDirectTieback(), "source", "tieback");
    assertStudy(createDaisyChain(), "upstream manifold", "export");
    assertStudy(createBranchedMultiTemplate(), "PLEM", "export riser");
    assertStudy(createInjectionHeader(), "injection PLET", "injection well flowline");
  }

  /** Replay one multi-template network at two physically aligned spatial meshes. */
  @Test
  void comparesSpatialMeshesOnUnchangedNetworkDefinition() {
    TwoFluidPipeNetwork network = createBranchedMultiTemplate();
    Map<String, Integer> subcycles = uniformSubcycles(network, 2);
    Map<String, Integer> coarseMesh = uniformSectionCounts(network, 8);
    Map<String, Integer> fineMesh = uniformSectionCounts(network, 12);
    Result result = new TwoFluidPipeNetworkRefinementStudy(network)
        .addConfiguration("eight cells", 0.0005, subcycles, coarseMesh)
        .addConfiguration("twelve cells", 0.0005, subcycles, fineMesh).run(0.001);

    CaseResult coarse = result.getCase("eight cells");
    CaseResult fine = result.getCase("twelve cells");
    assertEquals(coarseMesh, coarse.getBranchSectionCounts());
    assertEquals(fineMesh, fine.getBranchSectionCounts());
    for (String branchName : network.getBranchNames()) {
      assertEquals(8, coarse.getBranchProfile(branchName).getPressurePa().length);
      assertEquals(12, fine.getBranchProfile(branchName).getPressurePa().length);
      assertEquals(50.0, sum(coarse.getBranchProfile(branchName).getCellLengthM()), 1.0e-12);
      assertEquals(50.0, sum(fine.getBranchProfile(branchName).getCellLengthM()), 1.0e-12);
      for (ProfileQuantity quantity : ProfileQuantity.values()) {
        double difference = result.getMaximumNormalizedBranchProfileDifference("eight cells", "twelve cells",
            branchName, quantity);
        assertTrue(Double.isFinite(difference));
        assertTrue(difference < 0.5, branchName + " " + quantity + " difference=" + difference);
      }
    }
    assertEquals(4, network.getPipe("export riser").getNumberOfSections());
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  /** Quantify three-grid convergence on longer production and injection trajectories. */
  @Test
  void reportsLongTrajectorySpatialConvergence() {
    assertLongTrajectoryConvergence(createLongProductionTieback(), "long production flowline",
        EnumSet.of(ProfileQuantity.TEMPERATURE, ProfileQuantity.LIQUID_HOLDUP, ProfileQuantity.GAS_VELOCITY,
            ProfileQuantity.LIQUID_VELOCITY));
    assertLongTrajectoryConvergence(createLongInjectionTieback(), "long injection flowline",
        EnumSet.of(ProfileQuantity.PRESSURE, ProfileQuantity.TEMPERATURE));
  }

  /** Reject three-grid estimates that also change time integration or use unequal grid ratios. */
  @Test
  void rejectsConfoundedSpatialConvergenceSequences() {
    TwoFluidPipeNetwork network = createDirectTieback();
    Map<String, Integer> subcycles = uniformSubcycles(network, 2);
    Result result = new TwoFluidPipeNetworkRefinementStudy(network)
        .addConfiguration("eight cells", 0.001, subcycles, uniformSectionCounts(network, 8))
        .addConfiguration("twelve cells", 0.001, subcycles, uniformSectionCounts(network, 12))
        .addConfiguration("sixteen cells", 0.001, subcycles, uniformSectionCounts(network, 16))
        .addConfiguration("eighteen cells", 0.001, subcycles, uniformSectionCounts(network, 18))
        .addConfiguration("different step", 0.0005, subcycles, uniformSectionCounts(network, 18)).run(0.001);

    assertThrows(IllegalArgumentException.class, () -> result.getSpatialConvergenceReport("eight cells", "twelve cells",
        "sixteen cells", "tieback", ProfileQuantity.PRESSURE));
    assertThrows(IllegalArgumentException.class, () -> result.getSpatialConvergenceReport("eight cells", "twelve cells",
        "different step", "tieback", ProfileQuantity.PRESSURE));
  }

  /** Reject invalid configurations without changing the captured network. */
  @Test
  void rejectsInvalidConfigurationAndDuration() {
    TwoFluidPipeNetwork network = createDirectTieback();
    TwoFluidPipeNetworkRefinementStudy study = new TwoFluidPipeNetworkRefinementStudy(network);
    Map<String, Integer> invalid = new LinkedHashMap<String, Integer>();
    invalid.put("missing", Integer.valueOf(1));

    assertThrows(IllegalArgumentException.class, () -> study.addConfiguration("bad", 0.001, invalid));
    assertThrows(IllegalArgumentException.class, () -> study.addConfiguration("bad", 0.0, null));
    assertThrows(IllegalStateException.class, () -> study.run(0.001));
    study.addConfiguration("coarse", 0.001, null);
    assertThrows(IllegalArgumentException.class, () -> study.addConfiguration("coarse", 0.0005, null));
    assertThrows(IllegalArgumentException.class, () -> study.run(0.0015));
    Map<String, Integer> invalidMesh = new LinkedHashMap<String, Integer>();
    invalidMesh.put("tieback", Integer.valueOf(1));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidPipeNetworkRefinementStudy(network).addConfiguration("bad mesh", 0.001, null, invalidMesh));
    invalidMesh.clear();
    invalidMesh.put("missing", Integer.valueOf(8));
    assertThrows(IllegalArgumentException.class,
        () -> new TwoFluidPipeNetworkRefinementStudy(network).addConfiguration("bad mesh", 0.001, null, invalidMesh));
    TwoFluidPipeNetwork legacyTerrain = createDirectTieback();
    legacyTerrain.getPipe("tieback").setElevationProfile(new double[] {0.0, 1.0, 2.0, 3.0});
    Map<String, Integer> refinedLegacy = new LinkedHashMap<String, Integer>();
    refinedLegacy.put("tieback", Integer.valueOf(8));
    TwoFluidPipeNetworkRefinementStudy legacyStudy = new TwoFluidPipeNetworkRefinementStudy(legacyTerrain)
        .addConfiguration("unsupported legacy terrain", 0.001, null, refinedLegacy);
    assertThrows(IllegalArgumentException.class, () -> legacyStudy.run(0.001));
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  /**
   * Execute and check one representative topology.
   *
   * @param network topology fixture
   * @param comparisonNode node used for refinement comparison
   * @param comparisonBranch branch used for profile comparison
   */
  private static void assertStudy(TwoFluidPipeNetwork network, String comparisonNode, String comparisonBranch) {
    Map<String, Integer> doubled = uniformSubcycles(network, 2);
    Result result = new TwoFluidPipeNetworkRefinementStudy(network).addConfiguration("coarse", 0.001, null)
        .addConfiguration("subcycled", 0.001, doubled).addConfiguration("fine", 0.0005, doubled).run(0.001);

    assertEquals(0.001, result.getDurationSeconds(), 0.0);
    assertEquals(3, result.getCases().size());
    assertEquals(1L, result.getCase("coarse").getStepCount());
    assertEquals(2L, result.getCase("fine").getStepCount());
    assertTrue(result.getRelativeNodePressureDifference("coarse", "fine", comparisonNode) < 0.25);
    assertTrue(result.getRelativeBranchOutletPressureDifference("coarse", "fine", comparisonBranch) < 0.25);
    for (CaseResult caseResult : result.getCases()) {
      assertTrue(caseResult.getRuntimeNanoseconds() > 0L);
      assertTrue(caseResult.getMaximumMassResidual() < 1.0e-8);
      assertTrue(caseResult.getMaximumComponentResidual() < 1.0e-8);
      // The network accepts absolute-or-relative first-law closure; retain the raw relative diagnostic
      // without incorrectly replacing the solver's accepted absolute tolerance.
      assertTrue(Double.isFinite(caseResult.getMaximumEnergyResidual()));
      assertTrue(Double.isFinite(caseResult.getMaximumJunctionResidual()));
      for (String branchName : network.getBranchNames()) {
        assertPhysicalProfile(caseResult.getBranchProfile(branchName));
      }
    }
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  /**
   * Run one equally refined 8/12/18-cell sequence and check its convergence receipt.
   *
   * @param network unadvanced long-route topology
   * @param branchName compared branch
   * @param expectedMonotonic quantities expected to have a positive observed order
   */
  private static void assertLongTrajectoryConvergence(TwoFluidPipeNetwork network, String branchName,
      EnumSet<ProfileQuantity> expectedMonotonic) {
    Map<String, Integer> subcycles = uniformSubcycles(network, 2);
    Result result = new TwoFluidPipeNetworkRefinementStudy(network)
        .addConfiguration("eight cells", 0.0005, subcycles, uniformSectionCounts(network, 8))
        .addConfiguration("twelve cells", 0.0005, subcycles, uniformSectionCounts(network, 12))
        .addConfiguration("eighteen cells", 0.0005, subcycles, uniformSectionCounts(network, 18)).run(0.001);

    for (ProfileQuantity quantity : ProfileQuantity.values()) {
      SpatialConvergenceReport report = result.getSpatialConvergenceReport("eight cells", "twelve cells",
          "eighteen cells", branchName, quantity);
      assertEquals(8, report.getCoarseSections());
      assertEquals(12, report.getMediumSections());
      assertEquals(18, report.getFineSections());
      assertEquals(1.5, report.getRefinementRatio(), 1.0e-12);
      if (expectedMonotonic.contains(quantity)) {
        assertEquals(SpatialConvergenceStatus.MONOTONIC, report.getStatus(), branchName + " " + quantity);
        assertTrue(report.isMonotonicallyConvergent());
        assertTrue(report.getCoarseMediumDifference() > report.getMediumFineDifference());
        assertTrue(report.getObservedOrder() > 0.0);
        assertTrue(report.getFineGridConvergenceIndex() >= 0.0);
      } else {
        assertEquals(SpatialConvergenceStatus.NON_MONOTONIC, report.getStatus(), branchName + " " + quantity);
        assertTrue(!report.isMonotonicallyConvergent());
        assertTrue(report.getObservedOrder() <= 0.0);
        assertTrue(Double.isNaN(report.getFineGridConvergenceIndex()));
      }
    }
    assertEquals(0.0, network.getSimulationTimeSeconds(), 0.0);
  }

  /**
   * Create one factor map covering all branches.
   *
   * @param network topology fixture
   * @param factor positive common factor
   * @return deterministic branch map
   */
  private static Map<String, Integer> uniformSubcycles(TwoFluidPipeNetwork network, int factor) {
    Map<String, Integer> factors = new LinkedHashMap<String, Integer>();
    for (String branchName : network.getBranchNames()) {
      factors.put(branchName, Integer.valueOf(factor));
    }
    return factors;
  }

  /**
   * Create one section-count map covering all branches.
   *
   * @param network topology fixture
   * @param sectionCount common spatial section count
   * @return deterministic branch map
   */
  private static Map<String, Integer> uniformSectionCounts(TwoFluidPipeNetwork network, int sectionCount) {
    Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    for (String branchName : network.getBranchNames()) {
      counts.put(branchName, Integer.valueOf(sectionCount));
    }
    return counts;
  }

  /**
   * Check final profile dimensions and physical bounds.
   *
   * @param profile profile receipt
   */
  private static void assertPhysicalProfile(BranchProfile profile) {
    double[] pressure = profile.getPressurePa();
    double[] position = profile.getPositionM();
    double[] cellLength = profile.getCellLengthM();
    double[] temperature = profile.getTemperatureK();
    double[] holdup = profile.getLiquidHoldup();
    double[] gasVelocity = profile.getGasVelocityMPerS();
    double[] liquidVelocity = profile.getLiquidVelocityMPerS();
    assertEquals(pressure.length, temperature.length);
    assertEquals(pressure.length, position.length);
    assertEquals(pressure.length, cellLength.length);
    assertEquals(pressure.length, holdup.length);
    assertEquals(pressure.length, gasVelocity.length);
    assertEquals(pressure.length, liquidVelocity.length);
    assertTrue(pressure.length >= 2);
    double previousPosition = -1.0;
    for (int index = 0; index < pressure.length; index++) {
      assertTrue(Double.isFinite(position[index]) && position[index] > previousPosition);
      assertTrue(Double.isFinite(cellLength[index]) && cellLength[index] > 0.0);
      previousPosition = position[index];
      assertTrue(Double.isFinite(pressure[index]) && pressure[index] > 0.0);
      assertTrue(Double.isFinite(temperature[index]) && temperature[index] > 0.0);
      assertTrue(Double.isFinite(holdup[index]) && holdup[index] >= 0.0 && holdup[index] <= 1.0);
      assertTrue(Double.isFinite(gasVelocity[index]));
      assertTrue(Double.isFinite(liquidVelocity[index]));
    }
  }

  /**
   * Sum one finite array.
   *
   * @param values values to sum
   * @return arithmetic sum
   */
  private static double sum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  /**
   * Create a single-branch direct tieback.
   *
   * @return unadvanced topology
   */
  private static TwoFluidPipeNetwork createDirectTieback() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("direct tieback refinement");
    network.addFixedPressureNode("source", 65.0e5, createFluid(0.98, 0.02));
    network.addFixedPressureNode("host", 50.0e5, createFluid(0.95, 0.05));
    network.addPipe("tieback", "source", "host", createPipe("tieback", 0.98, 0.02));
    return network;
  }

  /**
   * Create a two-junction daisy-chain topology.
   *
   * @return unadvanced topology
   */
  private static TwoFluidPipeNetwork createDaisyChain() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("daisy chain refinement");
    network.addFixedPressureNode("source", 70.0e5, createFluid(0.99, 0.01));
    network.addAlgebraicJunction("upstream manifold", 62.0e5, createFluid(0.97, 0.03));
    network.addAlgebraicJunction("downstream PLEM", 58.0e5, createFluid(0.95, 0.05));
    network.addFixedPressureNode("host", 50.0e5, createFluid(0.95, 0.05));
    network.addPipe("production flowline", "source", "upstream manifold",
        createPipe("production flowline", 0.99, 0.01));
    network.addPipe("daisy jumper", "upstream manifold", "downstream PLEM", createPipe("daisy jumper", 0.96, 0.04));
    network.addPipe("export", "downstream PLEM", "host", createPipe("export", 0.95, 0.05));
    return network;
  }

  /**
   * Create two finite conservative templates feeding one algebraic PLEM and export riser.
   *
   * @return unadvanced topology
   */
  private static TwoFluidPipeNetwork createBranchedMultiTemplate() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("branched multi-template refinement");
    network.addConservativeCompressibleNode("template A", createVolume(63.0e5, 5000.0), createFluid(0.98, 0.02));
    network.addConservativeCompressibleNode("template B", createVolume(61.0e5, 5000.0), createFluid(0.90, 0.10));
    network.addAlgebraicJunction("PLEM", 56.0e5, createFluid(0.95, 0.05));
    network.addFixedPressureNode("host", 50.0e5, createFluid(0.95, 0.05));
    network.addPipe("template A flowline", "template A", "PLEM", createPipe("template A flowline", 0.98, 0.02));
    network.addPipe("template B flowline", "template B", "PLEM", createPipe("template B flowline", 0.90, 0.10));
    network.addPipe("export riser", "PLEM", "host", createPipe("export riser", 0.95, 0.05));
    return network;
  }

  /**
   * Create a shared injection header feeding one pressure-constrained injection well.
   *
   * @return unadvanced injection topology
   */
  private static TwoFluidPipeNetwork createInjectionHeader() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("injection header refinement");
    network.addFixedPressureNode("injection source", 75.0e5, createFluid(0.98, 0.02));
    network.addAlgebraicJunction("injection PLET", 65.0e5, createFluid(0.98, 0.02));
    network.addFixedPressureNode("injection well", 55.0e5, createFluid(0.98, 0.02));
    network.addPipe("injection header", "injection source", "injection PLET",
        createPipe("injection header", 0.98, 0.02));
    network.addPipe("injection well flowline", "injection PLET", "injection well",
        createPipe("injection well flowline", 0.98, 0.02));
    return network;
  }

  /**
   * Create a 5 km production route with explicit falling terrain.
   *
   * @return unadvanced long production topology
   */
  private static TwoFluidPipeNetwork createLongProductionTieback() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("long production refinement");
    network.addFixedPressureNode("production tree", 85.0e5, createFluid(0.94, 0.06));
    network.addFixedPressureNode("host separator", 50.0e5, createFluid(0.94, 0.06));
    network.addPipe("long production flowline", "production tree", "host separator", createPipe(
        "long production flowline", 0.94, 0.06, 5000.0, 0.25, 4.0, new double[] {0.0, -40.0, -100.0, -160.0, -200.0}));
    return network;
  }

  /**
   * Create a 5 km gas-injection route with explicit rising terrain toward the well.
   *
   * @return unadvanced long injection topology
   */
  private static TwoFluidPipeNetwork createLongInjectionTieback() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("long injection refinement");
    network.addFixedPressureNode("injection host", 110.0e5, createFluid(0.99, 0.01));
    network.addFixedPressureNode("injection tree", 70.0e5, createFluid(0.99, 0.01));
    network.addPipe("long injection flowline", "injection host", "injection tree", createPipe("long injection flowline",
        0.99, 0.01, 5000.0, 0.20, 3.0, new double[] {0.0, 30.0, 70.0, 120.0, 180.0}));
    return network;
  }

  /**
   * Create one short four-cell pipe.
   *
   * @param name pipe name
   * @param methane methane mole fraction
   * @param heptane heptane mole fraction
   * @return configured pipe
   */
  private static TwoFluidPipe createPipe(String name, double methane, double heptane) {
    return createPipe(name, methane, heptane, 50.0, 0.20, 2.0, new double[5]);
  }

  /**
   * Create one four-cell pipe with explicit physical geometry.
   *
   * @param name pipe name
   * @param methane methane mole fraction
   * @param heptane heptane mole fraction
   * @param lengthM branch length in m
   * @param diameterM internal diameter in m
   * @param massFlowKgS feed mass flow in kg/s
   * @param cellFaceElevationM elevation at the five cell faces in m
   * @return configured pipe
   */
  private static TwoFluidPipe createPipe(String name, double methane, double heptane, double lengthM, double diameterM,
      double massFlowKgS, double[] cellFaceElevationM) {
    SystemInterface fluid = createFluid(methane, heptane);
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(massFlowKgS, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe(name, feed);
    pipe.setLength(lengthM);
    pipe.setDiameter(diameterM);
    pipe.setNumberOfSections(4);
    pipe.setCellFaceElevationProfile(cellFaceElevationM);
    return pipe;
  }

  /**
   * Create a two-component SRK fluid.
   *
   * @param methane methane mole fraction
   * @param heptane heptane mole fraction
   * @return configured fluid
   */
  private static SystemInterface createFluid(double methane, double heptane) {
    SystemInterface fluid = new SystemSrkEos(300.0, 60.0);
    fluid.addComponent("methane", methane);
    fluid.addComponent("n-heptane", heptane);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Create a compressible volume with gas and oil inventory.
   *
   * @param pressurePa pressure in Pa
   * @param volumeM3 volume in m3
   * @return configured volume
   */
  private static UpstreamCompressibleVolume createVolume(double pressurePa, double volumeM3) {
    double[] density = {45.0, 700.0, 1000.0};
    double[] soundSpeed = {350.0, 1200.0, 1450.0};
    double[] mass = {0.90 * volumeM3 * density[0], 0.10 * volumeM3 * density[1], 0.0};
    return new UpstreamCompressibleVolume(volumeM3, pressurePa, mass, density, soundSpeed);
  }
}
