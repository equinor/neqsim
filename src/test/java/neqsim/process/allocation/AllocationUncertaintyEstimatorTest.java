package neqsim.process.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link AllocationUncertaintyEstimator}.
 *
 * <p>
 * Verifies the closed-form first-order uncertainty propagation against analytic expectations on a two-source commingled
 * separation process: zero input variance gives zero output variance, finite input variance gives a positive output
 * variance, sources sum in quadrature (independent metering), and the per-source per-custody standard deviation never
 * exceeds the deterministic source-to-custody allocation (a physical sanity bound for sigma equal to the source flow).
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AllocationUncertaintyEstimatorTest {

  /** Absolute tolerance for variance comparisons (mole/sec)^2. */
  private static final double TOL = 1.0e-9;

  /**
   * Builds the same two-source commingled gas/oil flowsheet used by {@link SourceAllocatorTest}.
   *
   * @return the assembled and solved process system
   */
  private ProcessSystem buildProcess() {
    SystemSrkEos fluidA = new SystemSrkEos(298.15, 50.0);
    fluidA.addComponent("methane", 0.95);
    fluidA.addComponent("nC10", 0.05);
    fluidA.setMixingRule("classic");

    SystemSrkEos fluidB = new SystemSrkEos(298.15, 50.0);
    fluidB.addComponent("methane", 0.30);
    fluidB.addComponent("nC10", 0.70);
    fluidB.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();

    Stream feedA = new Stream("Well-A", fluidA);
    feedA.setFlowRate(1000.0, "kg/hr");
    feedA.setTemperature(25.0, "C");
    feedA.setPressure(50.0, "bara");
    process.add(feedA);

    Stream feedB = new Stream("Well-B", fluidB);
    feedB.setFlowRate(1500.0, "kg/hr");
    feedB.setTemperature(25.0, "C");
    feedB.setPressure(50.0, "bara");
    process.add(feedB);

    Mixer mixer = new Mixer("Commingle");
    mixer.addStream(feedA);
    mixer.addStream(feedB);
    process.add(mixer);

    Separator separator = new Separator("Inlet Separator", mixer.getOutletStream());
    process.add(separator);

    Stream gas = new Stream("ExportGas", separator.getGasOutStream());
    process.add(gas);

    Stream oil = new Stream("ExportOil", separator.getLiquidOutStream());
    process.add(oil);

    process.run();
    return process;
  }

  /**
   * Builds an allocator with both sources and both custody outlets registered explicitly.
   *
   * @param process the base-case process
   * @return the configured allocator
   */
  private SourceAllocator buildAllocator(ProcessSystem process) {
    Stream feedA = (Stream) process.getUnit("Well-A");
    Stream feedB = (Stream) process.getUnit("Well-B");
    Separator separator = (Separator) process.getUnit("Inlet Separator");
    StreamInterface gas = separator.getGasOutStream();
    StreamInterface oil = separator.getLiquidOutStream();

    SourceAllocator allocator = new SourceAllocator();
    allocator.setBaseCase(process);
    allocator.addSource("Well-A", feedA);
    allocator.addSource("Well-B", feedB);
    allocator.addCustodyOutlet("ExportGas", gas, ProductType.GAS);
    allocator.addCustodyOutlet("ExportOil", oil, ProductType.OIL);
    return allocator;
  }

  @Test
  void testZeroInputVarianceGivesZeroOutputVariance() {
    ProcessSystem process = buildProcess();
    SourceAllocator allocator = buildAllocator(process);
    // Run allocate() once to populate diagnostics (and validate the deterministic side).
    allocator.allocate();

    int numSources = 2;
    int numComp = allocator.getExtractor().getComponentNames().size();
    double[][] variance = new double[numSources][numComp];

    AllocationUncertaintyEstimator estimator = new AllocationUncertaintyEstimator();
    AllocationUncertaintyEstimator.UncertaintyResult result = estimator.propagate(allocator, variance);
    assertNotNull(result);
    assertEquals(0.0, result.getAllocatedFlowStdDevMoles("Well-A", "ExportGas"), TOL);
    assertEquals(0.0, result.getAllocatedFlowStdDevMoles("Well-B", "ExportOil"), TOL);
  }

  @Test
  void testPositiveInputVarianceGivesPositiveOutputVariance() {
    ProcessSystem process = buildProcess();
    SourceAllocator allocator = buildAllocator(process);
    ProductionAllocationResult det = allocator.allocate();

    int numSources = 2;
    int numComp = allocator.getExtractor().getComponentNames().size();
    int methaneIdx = -1;
    for (int k = 0; k < numComp; k++) {
      if ("methane".equals(allocator.getExtractor().getComponentNames().get(k))) {
	methaneIdx = k;
	break;
      }
    }
    assertTrue(methaneIdx >= 0, "methane must be on the master slate");

    double[][] variance = new double[numSources][numComp];
    // 1% relative metering noise on methane injection for Well-A only.
    Stream feedA = (Stream) process.getUnit("Well-A");
    double aMethane = RecoveryFactorExtractor.componentFlow(feedA, "methane");
    double sigma = 0.01 * aMethane;
    variance[0][methaneIdx] = sigma * sigma;

    AllocationUncertaintyEstimator estimator = new AllocationUncertaintyEstimator();
    AllocationUncertaintyEstimator.UncertaintyResult uncertainty = estimator.propagate(allocator, variance);

    // Variance must be positive on the gas custody outlet for Well-A and zero for Well-B
    // (independent metering, only Well-A has uncertainty).
    assertTrue(uncertainty.getAllocatedFlowVariance("Well-A", "ExportGas") > 0.0,
	"Well-A gas variance must be positive when Well-A has metering noise");
    assertEquals(0.0, uncertainty.getAllocatedFlowVariance("Well-B", "ExportGas"), TOL,
	"Well-B variance must be zero when only Well-A is noisy");

    // Sanity bound: one-sigma std-dev cannot exceed the deterministic allocated flow.
    double detGas = det.getAllocatedFlow("Well-A", "ExportGas", "mole/sec");
    double stdGas = uncertainty.getAllocatedFlowStdDevMoles("Well-A", "ExportGas");
    assertTrue(stdGas <= detGas + TOL,
	"one-sigma std-dev " + stdGas + " must not exceed deterministic allocation " + detGas);

    // The kg/hr standard deviation must also be positive and finite.
    double stdKg = uncertainty.getAllocatedFlowStdDevKgPerHr("Well-A", "ExportGas");
    assertTrue(stdKg > 0.0 && Double.isFinite(stdKg));
  }

  @Test
  void testIndependentSourcesAddInQuadrature() {
    ProcessSystem process = buildProcess();
    SourceAllocator allocator = buildAllocator(process);
    allocator.allocate();

    int numSources = 2;
    int numComp = allocator.getExtractor().getComponentNames().size();
    int methaneIdx = 0;
    for (int k = 0; k < numComp; k++) {
      if ("methane".equals(allocator.getExtractor().getComponentNames().get(k))) {
	methaneIdx = k;
	break;
      }
    }

    Stream feedA = (Stream) process.getUnit("Well-A");
    Stream feedB = (Stream) process.getUnit("Well-B");
    double sigmaA = 0.01 * RecoveryFactorExtractor.componentFlow(feedA, "methane");
    double sigmaB = 0.01 * RecoveryFactorExtractor.componentFlow(feedB, "methane");

    AllocationUncertaintyEstimator estimator = new AllocationUncertaintyEstimator();

    // Well-A only.
    double[][] vA = new double[numSources][numComp];
    vA[0][methaneIdx] = sigmaA * sigmaA;
    AllocationUncertaintyEstimator.UncertaintyResult onlyA = estimator.propagate(allocator, vA);
    double varA = onlyA.getAllocatedFlowVariance("Well-A", "ExportGas");

    // Well-B only.
    double[][] vB = new double[numSources][numComp];
    vB[1][methaneIdx] = sigmaB * sigmaB;
    AllocationUncertaintyEstimator.UncertaintyResult onlyB = estimator.propagate(allocator, vB);
    double varB = onlyB.getAllocatedFlowVariance("Well-B", "ExportGas");

    // Both sources noisy simultaneously.
    double[][] vBoth = new double[numSources][numComp];
    vBoth[0][methaneIdx] = sigmaA * sigmaA;
    vBoth[1][methaneIdx] = sigmaB * sigmaB;
    AllocationUncertaintyEstimator.UncertaintyResult both = estimator.propagate(allocator, vBoth);

    // Cross-source quadrature: per-source variances are independent of the other source's noise
    // (because the noise enters only its own b vector). So each per-source variance equals the
    // single-source case.
    assertEquals(varA, both.getAllocatedFlowVariance("Well-A", "ExportGas"), TOL);
    assertEquals(varB, both.getAllocatedFlowVariance("Well-B", "ExportGas"), TOL);
  }

  @Test
  void testJsonExportContainsExpectedKeys() {
    ProcessSystem process = buildProcess();
    SourceAllocator allocator = buildAllocator(process);
    allocator.allocate();

    int numComp = allocator.getExtractor().getComponentNames().size();
    double[][] variance = new double[2][numComp];
    variance[0][0] = 1.0e-8;

    AllocationUncertaintyEstimator estimator = new AllocationUncertaintyEstimator();
    String json = estimator.propagate(allocator, variance).toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"schemaVersion\""));
    assertTrue(json.contains("\"allocationStdDev\""));
    assertTrue(json.contains("Well-A"));
    assertTrue(json.contains("ExportGas"));
  }
}
