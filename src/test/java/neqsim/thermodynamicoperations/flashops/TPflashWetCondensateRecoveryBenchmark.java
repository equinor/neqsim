package neqsim.thermodynamicoperations.flashops;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Manual time/allocation benchmark for the wet-condensate aqueous recovery in issue #4451.
 *
 * <p>
 * Run the same compiled benchmark in separate JVMs with the before and after production classes first on the classpath.
 * Fixture setup and restoration are excluded. Results are measurements, never CI timing assertions.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TPflashWetCondensateRecoveryBenchmark {
  private static final Logger logger = LogManager.getLogger("neqsim.benchmark.wet-condensate");
  private static final com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) ManagementFactory
      .getThreadMXBean();

  /** Prevent instantiation of the benchmark utility. */
  private TPflashWetCondensateRecoveryBenchmark() {
  }

  /**
   * Run warmed repeated batches of dry, undersaturated and genuinely supersaturated workloads.
   *
   * @param args unused; configure rounds and iterations through benchmark system properties
   */
  public static void main(String[] args) {
    org.apache.logging.log4j.core.config.Configurator.setLevel(logger.getName(), org.apache.logging.log4j.Level.INFO);
    if (allocationBean.isThreadAllocatedMemorySupported()) {
      allocationBean.setThreadAllocatedMemoryEnabled(true);
    }
    SystemInterface template = EclipseFluidReadWrite
        .read("src/test/java/neqsim/thermo/util/readwrite/pa_universal.e300");
    EclipseFluidReadWrite.addWaterToFluid(template, 0.5);
    measure("dry-recovery-screen", template, 0.0, false, 2000);
    measure("undersaturated-recovery-screen", template, 0.006, false, 2000);
    measure("near-saturation-recovery", template, 0.02, false, 100);
    measure("supersaturated-recovery", template, 50.6 / 237.0, false, 100);
    measure("undersaturated-public-TPflash", template, 0.006, true, 200);
  }

  /**
   * Measure independent batches and report median runtime and thread allocations per operation.
   *
   * @param name workload label
   * @param template public PR78 template containing an initially zero water component
   * @param waterRatio water/hydrocarbon molar ratio
   * @param publicFlash whether to measure the complete public flash instead of its final recovery
   * @param defaultIterations workload-specific default batch size
   */
  private static void measure(String name, SystemInterface template, double waterRatio, boolean publicFlash,
      int defaultIterations) {
    SystemInterface fluid = template.clone();
    double[] composition = fluid.getMolarComposition();
    for (int component = 0; component < composition.length - 1; component++) {
      composition[component] /= 1.0 + waterRatio;
    }
    composition[composition.length - 1] = waterRatio / (1.0 + waterRatio);
    fluid.setMolarComposition(composition);
    fluid.setTotalFlowRate(237.0 * (1.0 + waterRatio), "mol/sec");
    fluid.setTemperature(288.15);
    fluid.setPressure(1.01325);
    fluid.setMultiPhaseCheck(false);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.setMultiPhaseCheck(true);
    boolean restoresInput = waterRatio > 0.01 || publicFlash;
    int expectedPhases = waterRatio > 0.01 ? 3 : 2;
    int iterations = Integer.getInteger("benchmark.iterations", defaultIterations);
    int warmup = Integer.getInteger("benchmark.warmup", defaultIterations);
    int rounds = Integer.getInteger("benchmark.rounds", 7);
    runBatch(fluid, publicFlash, restoresInput, expectedPhases, warmup);
    double[] nanoseconds = new double[rounds];
    double[] bytes = new double[rounds];
    for (int round = 0; round < rounds; round++) {
      double[] result = runBatch(fluid, publicFlash, restoresInput, expectedPhases, iterations);
      nanoseconds[round] = result[0];
      bytes[round] = result[1];
    }
    Arrays.sort(nanoseconds);
    Arrays.sort(bytes);
    logger.info("workload={} rounds={} iterations={} median_ns={} median_bytes={}", name, rounds, iterations,
        nanoseconds[rounds / 2], bytes[rounds / 2]);
  }

  /**
   * Execute one timed batch, excluding the clone used to restore a mutating workload's input.
   *
   * @param template equilibrated gas/oil endpoint
   * @param publicFlash whether to invoke the complete public flash
   * @param restoresInput whether the workload changes the endpoint
   * @param expectedPhases expected number of phases after each operation
   * @param iterations number of operations
   * @return mean nanoseconds and allocated bytes per operation
   */
  private static double[] runBatch(SystemInterface template, boolean publicFlash, boolean restoresInput,
      int expectedPhases, int iterations) {
    long elapsed = 0L;
    long allocated = 0L;
    long thread = Thread.currentThread().getId();
    for (int iteration = 0; iteration < iterations; iteration++) {
      SystemInterface fluid = restoresInput ? template.clone() : template;
      long beforeBytes = allocationBean.getThreadAllocatedBytes(thread);
      long start = System.nanoTime();
      if (publicFlash) {
        new ThermodynamicOperations(fluid).TPflash();
      } else {
        new TPmultiflash(fluid, false).rescueMetastableOilMissingAqueous();
      }
      elapsed += System.nanoTime() - start;
      allocated += allocationBean.getThreadAllocatedBytes(thread) - beforeBytes;
      if (fluid.getNumberOfPhases() != expectedPhases) {
        throw new IllegalStateException("Benchmark lost the expected phase topology: " + fluid.getNumberOfPhases());
      }
    }
    return new double[] {(double) elapsed / iterations, (double) allocated / iterations};
  }
}
