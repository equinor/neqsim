package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.BranchProfile;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.CaseResult;
import neqsim.process.equipment.network.TwoFluidPipeNetworkRefinementStudy.Result;
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
    assertStudy(createBranchedMultiTemplate(), "template A", "export riser");
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
   * Check final profile dimensions and physical bounds.
   *
   * @param profile profile receipt
   */
  private static void assertPhysicalProfile(BranchProfile profile) {
    double[] pressure = profile.getPressurePa();
    double[] temperature = profile.getTemperatureK();
    double[] holdup = profile.getLiquidHoldup();
    double[] gasVelocity = profile.getGasVelocityMPerS();
    double[] liquidVelocity = profile.getLiquidVelocityMPerS();
    assertEquals(pressure.length, temperature.length);
    assertEquals(pressure.length, holdup.length);
    assertEquals(pressure.length, gasVelocity.length);
    assertEquals(pressure.length, liquidVelocity.length);
    assertTrue(pressure.length >= 2);
    for (int index = 0; index < pressure.length; index++) {
      assertTrue(Double.isFinite(pressure[index]) && pressure[index] > 0.0);
      assertTrue(Double.isFinite(temperature[index]) && temperature[index] > 0.0);
      assertTrue(Double.isFinite(holdup[index]) && holdup[index] >= 0.0 && holdup[index] <= 1.0);
      assertTrue(Double.isFinite(gasVelocity[index]));
      assertTrue(Double.isFinite(liquidVelocity[index]));
    }
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
   * Create two finite conservative templates feeding one conservative PLEM and export riser.
   *
   * @return unadvanced topology
   */
  private static TwoFluidPipeNetwork createBranchedMultiTemplate() {
    TwoFluidPipeNetwork network = new TwoFluidPipeNetwork("branched multi-template refinement");
    network.addConservativeCompressibleNode("template A", createVolume(63.0e5, 5000.0), createFluid(0.95, 0.05));
    network.addConservativeCompressibleNode("template B", createVolume(61.0e5, 5000.0), createFluid(0.95, 0.05));
    network.addConservativeCompressibleNode("PLEM", createVolume(56.0e5, 10000.0), createFluid(0.95, 0.05));
    network.addFixedPressureNode("host", 50.0e5, createFluid(0.95, 0.05));
    network.addPipe("template A flowline", "template A", "PLEM", createPipe("template A flowline", 0.95, 0.05));
    network.addPipe("template B flowline", "template B", "PLEM", createPipe("template B flowline", 0.95, 0.05));
    network.addPipe("export riser", "PLEM", "host", createPipe("export riser", 0.95, 0.05));
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
    SystemInterface fluid = createFluid(methane, heptane);
    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(2.0, "kg/sec");
    feed.run();
    TwoFluidPipe pipe = new TwoFluidPipe(name, feed);
    pipe.setLength(50.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(4);
    pipe.setElevationProfile(new double[4]);
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
