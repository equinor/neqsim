package neqsim.thermodynamicoperations.flashops;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete-flash timing and numerical evidence for the issue #4202 endpoint recovery.
 *
 * <p>
 * Run identical JVMs against master, the initial fix and the optimized fix. Construction, state changes and numerical
 * snapshots are outside the timer. Every case receives its own warmup; individual batch timings and failed numerical
 * states are retained. Thread CPU time accompanies elapsed time to expose scheduling noise; its clock reads are outside
 * the elapsed timer. Timing differences must be assessed across batches and JVM runs, never asserted in CI. The
 * uncorrected condensate result is deliberately retained as a failed numerical baseline, not accepted as a faster
 * alternative.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TPflashEndpointRecoveryBenchmark {
  /** Representative ordinary, excluded and recovery workload families. */
  private static final String[] FAMILIES = {"dry-gas", "hot-gas", "two-phase", "cpa-water", "condensate",
      "condensate-cold", "condensate-multiphase"};
  /** Dry condensate component names. */
  private static final String[] CONDENSATE_NAMES = {"methane", "ethane", "propane", "n-butane", "n-hexane", "n-decane",
      "n-hexadecane"};
  /** Dry condensate feed mole fractions. */
  private static final double[] CONDENSATE_MOLES = {0.85, 0.05, 0.02, 0.01, 0.02, 0.03, 0.02};

  /** Prevents construction of this standalone benchmark. */
  private TPflashEndpointRecoveryBenchmark() {
  }

  /**
   * Builds one representative workload.
   *
   * @param family workload name
   * @return configured fluid
   */
  private static SystemInterface create(String family) {
    SystemInterface fluid;
    if ("dry-gas".equals(family)) {
      fluid = new SystemSrkEos(323.15, 20.0);
      fluid.addComponent("methane", 0.95);
      fluid.addComponent("ethane", 0.04);
      fluid.addComponent("nitrogen", 0.01);
    } else if ("two-phase".equals(family)) {
      fluid = new SystemPrEos(300.0, 50.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("n-decane", 0.10);
    } else if ("cpa-water".equals(family)) {
      fluid = new SystemSrkCPAstatoil(298.15, 60.0);
      fluid.addComponent("methane", 0.90);
      fluid.addComponent("water", 0.10);
      fluid.setMixingRule(10);
      return fluid;
    } else {
      fluid = "hot-gas".equals(family) ? new SystemSrkEos(800.0, 10.0) : new SystemSrkEos(353.15, 350.0);
      for (int component = 0; component < CONDENSATE_NAMES.length; component++) {
        fluid.addComponent(CONDENSATE_NAMES[component], CONDENSATE_MOLES[component]);
      }
      fluid.useVolumeCorrection(true);
      fluid.setMultiPhaseCheck("condensate-multiphase".equals(family));
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Sets unchanged or alternating nearby boundary conditions outside the measured interval.
   *
   * @param fluid workload fluid
   * @param temperature reference temperature in K
   * @param pressure reference absolute pressure in bar
   * @param changed whether to alternate nearby conditions
   * @param iteration iteration index
   */
  private static void setState(SystemInterface fluid, double temperature, double pressure, boolean changed,
      int iteration) {
    boolean offset = changed && iteration % 2 != 0;
    fluid.setTemperature(temperature + (offset ? 0.25 : 0.0));
    fluid.setPressure(pressure * (offset ? 0.995 : 1.0));
  }

  /**
   * Writes batch timings alongside complete numerical snapshots.
   *
   * @param args output path, optional flashes per batch, optional batch count, optional warmup count
   * @throws Exception if arguments are invalid, a flash fails or the output cannot be written
   */
  public static void main(String[] args) throws Exception {
    Path output = Paths.get(args.length > 0 ? args[0] : "tpflash-endpoint-recovery-benchmark.json");
    int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 64;
    int batches = args.length > 2 ? Integer.parseInt(args[2]) : 7;
    int warmup = args.length > 3 ? Integer.parseInt(args[3]) : 128;
    if (iterations < 1 || batches < 1 || warmup < 1) {
      throw new IllegalArgumentException("Iteration, batch and warmup counts must be positive");
    }
    ThreadMXBean threadTiming = ManagementFactory.getThreadMXBean();
    boolean cpuTiming = threadTiming.isCurrentThreadCpuTimeSupported();
    if (cpuTiming && !threadTiming.isThreadCpuTimeEnabled()) {
      threadTiming.setThreadCpuTimeEnabled(true);
    }
    JsonArray records = new JsonArray();
    for (String family : FAMILIES) {
      for (boolean changed : new boolean[] {false, true}) {
        SystemInterface fluid = create(family);
        int caseWarmup = "dry-gas".equals(family) || "hot-gas".equals(family) || "two-phase".equals(family)
            ? Math.max(4096, warmup)
            : warmup;
        double temperature = fluid.getTemperature();
        double pressure = fluid.getPressure();
        double originalMoles = fluid.getTotalNumberOfMoles();
        double originalMass = StabilityOptimizationBenchmark.inventoryMass(fluid);
        for (int iteration = 0; iteration < caseWarmup; iteration++) {
          if ("condensate-cold".equals(family)) {
            fluid = create(family);
          }
          setState(fluid, temperature, pressure, changed, iteration);
          new TPflash(fluid).run();
        }
        for (int batch = 0; batch < batches; batch++) {
          long elapsedNanos = 0;
          long cpuNanos = 0;
          int recoveryCount = 0;
          for (int iteration = 0; iteration < iterations; iteration++) {
            if ("condensate-cold".equals(family)) {
              fluid = create(family);
            }
            setState(fluid, temperature, pressure, changed, iteration);
            TPflash flash = new TPflash(fluid);
            long cpuStart = cpuTiming ? threadTiming.getCurrentThreadCpuTime() : 0;
            long start = System.nanoTime();
            flash.run();
            elapsedNanos += System.nanoTime() - start;
            if (cpuTiming) {
              cpuNanos += threadTiming.getCurrentThreadCpuTime() - cpuStart;
            }
            if (flash.getLastStabilityOutcome().startsWith("recovered nonconservative hydrocarbon endpoint")) {
              recoveryCount++;
            }
          }
          JsonObject record = new JsonObject();
          record.addProperty("case", family);
          record.addProperty("sequence", changed ? "changed" : "unchanged");
          record.addProperty("batch", batch);
          record.addProperty("warmupFlashes", caseWarmup);
          record.addProperty("flashes", iterations);
          record.addProperty("elapsedNanos", elapsedNanos);
          record.addProperty("nanosPerFlash", (double) elapsedNanos / iterations);
          record.addProperty("cpuNanosPerFlash", cpuTiming ? (double) cpuNanos / iterations : -1.0);
          record.addProperty("recoveryFlashes", recoveryCount);
          record.add("state", StabilityOptimizationBenchmark.snapshot(fluid, originalMoles, originalMass));
          records.add(record);
        }
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("threadCpuTimingEnabled", cpuTiming);
    report.addProperty("minimumWarmupFlashesPerCase", warmup);
    report.addProperty("warmStartKValues", neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues());
    report.addProperty("scope", "complete flash timings; numerical consistency, not experimental validation");
    report.add("records", records);
    Files.createDirectories(output.toAbsolutePath().getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
    }
  }
}
