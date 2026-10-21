package neqsim.physicalproperties.interfaceproperties.surfacetension;

import java.util.Arrays;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Opt-in timings for repeated correlations, flash/property evaluation and cloning. Setup is excluded from timings;
 * results use nine warmed batches without wall-clock CI assertions. Use fresh JVMs for revision comparisons.
 *
 * @author NeqSim development team
 * @version 1.0
 */
@Tag("benchmark")
public class SurfaceTensionPerformanceBenchmarkTest {
  /** Logger for benchmark reports, enabled explicitly because normal test logging is suppressed. */
  private static final Logger logger = LogManager.getLogger(SurfaceTensionPerformanceBenchmarkTest.class);
  /** Consumes calculated values outside the timed loop. */
  private static volatile double sink;

  /**
   * Creates the requested synthetic benchmark fluid outside the timed region.
   *
   * @param scenario benchmark name
   * @return flashed fluid with initialized physical properties
   */
  private static SystemInterface fluid(String scenario) {
    SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.6);
    fluid.addComponent("n-decane", 0.4);
    if (scenario.contains("large")) {
      String[] names = {"nitrogen", "CO2", "ethane", "propane", "i-butane", "n-butane", "i-pentane", "n-pentane",
          "n-hexane", "n-heptane", "n-octane", "n-nonane"};
      for (String name : names) {
        fluid.addComponent(name, 0.01);
      }
    }
    if (scenario.contains("three")) {
      fluid.addComponent("water", 0.2);
    }
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    if (fluid.getNumberOfPhases() != (scenario.contains("three") ? 3 : 2)) {
      throw new IllegalStateException("Unexpected benchmark phase count: " + fluid.getNumberOfPhases());
    }
    return fluid;
  }

  /**
   * Executes one benchmark operation.
   *
   * @param fluid working fluid
   * @param scenario benchmark name
   * @param iteration iteration index
   * @return calculated value consumed by the benchmark
   */
  private static double run(SystemInterface fluid, String scenario, int iteration) {
    if (scenario.startsWith("clone")) {
      return fluid.clone().getPhase(0).getMolarVolume();
    }
    if (scenario.startsWith("flash")) {
      fluid.setTemperature(300.0 + iteration % 2);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initProperties();
    }
    double value = fluid.getInterphaseProperties().getSurfaceTension(0, 1);
    if (scenario.contains("three")) {
      value += fluid.getInterphaseProperties().getSurfaceTension(0, 2)
          + fluid.getInterphaseProperties().getSurfaceTension(1, 2);
    }
    return value;
  }

  /**
   * Profiles one workload without imposing a wall-clock CI assertion.
   *
   * @param scenario benchmark name
   */
  @ParameterizedTest
  @ValueSource(strings = {"correlation", "correlation-large", "correlation-three", "flash-large", "clone-large"})
  public void profile(String scenario) {
    measure(scenario);
  }

  /**
   * Runs a single scenario in a fresh JVM for comparisons between revisions.
   *
   * @param args exactly one benchmark name
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException("Supply one benchmark scenario");
    }
    measure(args[0]);
  }

  /**
   * Reports the median of nine warmed timing batches, preserving the final calculated value.
   *
   * @param scenario benchmark name
   */
  private static void measure(String scenario) {
    Configurator.setLevel(logger.getName(), Level.INFO);
    SystemInterface fluid = fluid(scenario);
    int count = scenario.startsWith("flash") ? 150 : scenario.startsWith("clone") ? 1200 : 500000;
    int warmup = scenario.startsWith("flash") ? 100 : scenario.startsWith("clone") ? 2000 : 500000;
    for (int iteration = 0; iteration < warmup; iteration++) {
      sink = run(fluid, scenario, iteration);
    }
    double[] times = new double[9];
    double value = 0.0;
    for (int trial = 0; trial < times.length; trial++) {
      long start = System.nanoTime();
      for (int iteration = 0; iteration < count; iteration++) {
        value = run(fluid, scenario, iteration);
      }
      times[trial] = (System.nanoTime() - start) / (double) count;
      sink = value;
    }
    Arrays.sort(times);
    logger.info("scenario={} median_ns={} value={} sorted_batches_ns={}", scenario, times[4], value,
        Arrays.toString(times));
  }
}
