package neqsim.thermodynamicoperations.flashops;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Standalone allocation and complete-operation qualification for internal flash workspaces.
 *
 * <p>
 * Use identical JVM options and this identical source against each revision. Cold means a newly constructed fluid, not
 * a cold JVM. Fluid/process construction, boundary changes and correctness snapshots are excluded; each measured flash
 * includes operation construction and run. Both cold and warm changed operations alternate temperature and pressure.
 * The process case includes feed flashing, separation, compression, cooling and outlet separation. Its unchanged
 * sequence intentionally includes equipment cache behavior, and must not be interpreted as full recalculation.
 * Snapshots use public clones outside measurement; old revisions may refresh shared same-state derivative caches. Raw
 * per-operation wall samples, CPU and allocation totals, failures and final numerical states are retained. No timing or
 * allocation threshold is asserted in CI; compare repeated JVM forks only after checking correctness.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TPflashAllocationQualificationBenchmark {
  private static final String[] FAMILIES = {"dry-gas", "two-phase", "condensate", "cpa-water", "trace-aqueous-srk",
      "trace-aqueous-cpa", "process-gas"};
  private static final String[] SEQUENCES = {"cold-changed", "warm-unchanged", "warm-changed"};

  /** Prevents construction of the standalone runner. */
  private TPflashAllocationQualificationBenchmark() {
  }

  /**
   * Creates a synthetic numerical workload, without implying experimental model validation.
   *
   * @param family workload identifier
   * @return configured feed
   */
  private static SystemInterface fluid(String family) {
    SystemInterface result;
    if (family.startsWith("trace-")) {
      result = family.endsWith("cpa") ? new SystemSrkCPAstatoil(307.0, 10.5) : new SystemSrkEos(307.0, 10.5);
      result.addComponent("methane", 0.0);
      result.addComponent("n-heptane", 0.997);
      result.addComponent("water", 0.003);
      result.setMixingRule(family.endsWith("cpa") ? 10 : 2);
      result.setMultiPhaseCheck(true);
      return result;
    } else if ("cpa-water".equals(family)) {
      result = new SystemSrkCPAstatoil(298.15, 60.0);
      result.addComponent("methane", 0.9);
      result.addComponent("water", 0.1);
      result.setMixingRule(10);
      return result;
    } else if ("two-phase".equals(family)) {
      result = new SystemPrEos(300.0, 50.0);
      result.addComponent("methane", 0.9);
      result.addComponent("n-decane", 0.1);
    } else if ("condensate".equals(family)) {
      result = new SystemSrkEos(353.15, 350.0);
      String[] names = {"methane", "ethane", "propane", "n-butane", "n-hexane", "n-decane", "n-hexadecane"};
      double[] fractions = {0.85, 0.05, 0.02, 0.01, 0.02, 0.03, 0.02};
      for (int index = 0; index < names.length; index++) {
        result.addComponent(names[index], fractions[index]);
      }
      result.useVolumeCorrection(true);
    } else {
      result = new SystemSrkEos(323.15, 20.0);
      result.addComponent("methane", 0.95);
      result.addComponent("ethane", 0.04);
      result.addComponent("nitrogen", 0.01);
    }
    result.setMixingRule(2);
    return result;
  }

  /** One mutable workload with construction and boundary changes outside measurement. */
  private static final class Workload {
    private final SystemInterface feed;
    private final double temperature;
    private final double pressure;
    private final double moles;
    private final double mass;
    private final ProcessSystem process;
    private final Stream stream;
    private final Separator inlet;
    private final Separator outlet;
    private final Compressor compressor;
    private final Cooler cooler;

    private Workload(String family) {
      feed = fluid(family);
      temperature = feed.getTemperature();
      pressure = feed.getPressure();
      if (family.startsWith("process-")) {
        feed.setTotalFlowRate(12000.0, "kg/hr");
        stream = new Stream("feed", feed);
        inlet = new Separator("inlet separator", stream);
        compressor = new Compressor("compressor", inlet.getGasOutStream());
        compressor.setOutletPressure(35.0, "bara");
        compressor.setIsentropicEfficiency(0.75);
        cooler = new Cooler("cooler", compressor.getOutletStream());
        cooler.setOutletTemperature(298.15);
        outlet = new Separator("outlet separator", cooler.getOutletStream());
        process = new ProcessSystem();
        process.add(stream);
        process.add(inlet);
        process.add(compressor);
        process.add(cooler);
        process.add(outlet);
      } else {
        process = null;
        stream = null;
        inlet = null;
        outlet = null;
        compressor = null;
        cooler = null;
      }
      moles = feed.getTotalNumberOfMoles();
      mass = StabilityOptimizationBenchmark.inventoryMass(feed);
    }

    private void prepare(String sequence, int iteration) {
      boolean offset = !"warm-unchanged".equals(sequence) && iteration % 2 != 0;
      SystemInterface inletFluid = stream == null ? feed : stream.getFluid();
      inletFluid.setTemperature(temperature + (offset ? 0.25 : 0.0));
      inletFluid.setPressure(pressure * (offset ? 0.995 : 1.0));
    }

    private void run() {
      if (process == null) {
        new TPflash(feed).run();
      } else {
        process.run();
      }
    }

    private JsonObject snapshot() {
      SystemInterface inletFluid = stream == null ? feed : stream.getFluid();
      JsonObject result = StabilityOptimizationBenchmark.snapshot(inletFluid.clone(), moles, mass);
      if (process != null) {
        double products = inlet.getLiquidOutStream().getFlowRate("kg/sec")
            + outlet.getGasOutStream().getFlowRate("kg/sec") + outlet.getLiquidOutStream().getFlowRate("kg/sec");
        double flowResidual = Math.abs(products - feed.getFlowRate("kg/sec")) / feed.getFlowRate("kg/sec");
        result.addProperty("productMassFlowRelativeResidual", flowResidual);
        result.addProperty("compressorPowerW", compressor.getPower());
        result.addProperty("coolerDutyW", cooler.getDuty());
        SystemInterface product = cooler.getOutletStream().getFluid();
        JsonObject productState = StabilityOptimizationBenchmark.snapshot(product.clone(),
            product.getTotalNumberOfMoles(), StabilityOptimizationBenchmark.inventoryMass(product));
        result.add("cooledProduct", productState);
        result.addProperty("validationPassed",
            result.get("validationPassed").getAsBoolean() && productState.get("validationPassed").getAsBoolean()
                && flowResidual < 1.0e-10 && Double.isFinite(compressor.getPower()) && compressor.getPower() > 0.0
                && Double.isFinite(cooler.getDuty()));
      }
      return result;
    }
  }

  /**
   * Enables optional current-thread allocation accounting.
   *
   * @param timing JVM thread management interface
   * @return enabled allocation counter or null
   */
  private static com.sun.management.ThreadMXBean allocationCounter(ThreadMXBean timing) {
    if (!(timing instanceof com.sun.management.ThreadMXBean)) {
      return null;
    }
    com.sun.management.ThreadMXBean result = (com.sun.management.ThreadMXBean) timing;
    try {
      if (!result.isThreadAllocatedMemorySupported()) {
        return null;
      }
      if (!result.isThreadAllocatedMemoryEnabled()) {
        result.setThreadAllocatedMemoryEnabled(true);
      }
      return result;
    } catch (SecurityException | UnsupportedOperationException ex) {
      return null;
    }
  }

  /**
   * Writes comparable measurements while retaining failed numerical baselines.
   *
   * @param args output path, operations per batch, batches, warmup operations and optional family prefix
   * @throws Exception if arguments are invalid or output cannot be written
   */
  public static void main(String[] args) throws Exception {
    Path output = Paths.get(args.length > 0 ? args[0] : "tpflash-allocation-qualification.json");
    int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 32;
    int batches = args.length > 2 ? Integer.parseInt(args[2]) : 5;
    int warmup = args.length > 3 ? Integer.parseInt(args[3]) : 256;
    if (iterations < 1 || batches < 1 || warmup < 1) {
      throw new IllegalArgumentException("Operation, batch and warmup counts must be positive");
    }
    ThreadMXBean timing = ManagementFactory.getThreadMXBean();
    boolean cpu = timing.isCurrentThreadCpuTimeSupported();
    if (cpu && !timing.isThreadCpuTimeEnabled()) {
      timing.setThreadCpuTimeEnabled(true);
    }
    com.sun.management.ThreadMXBean allocation = allocationCounter(timing);
    long thread = Thread.currentThread().getId();
    JsonArray records = new JsonArray();
    for (String family : FAMILIES) {
      if (args.length > 4 && !family.startsWith(args[4])) {
        continue;
      }
      for (String sequence : SEQUENCES) {
        Workload workload = new Workload(family);
        int caseWarmup = "dry-gas".equals(family) || "two-phase".equals(family) ? Math.max(2048, warmup) : warmup;
        try {
          for (int iteration = 0; iteration < caseWarmup; iteration++) {
            if ("cold-changed".equals(sequence)) {
              workload = new Workload(family);
            }
            workload.prepare(sequence, iteration);
            workload.run();
          }
          for (int batch = 0; batch < batches; batch++) {
            long elapsed = 0;
            long cpuNanos = 0;
            long allocated = 0;
            long[] samples = new long[iterations];
            JsonObject firstState = null;
            for (int iteration = 0; iteration < iterations; iteration++) {
              if ("cold-changed".equals(sequence)) {
                workload = new Workload(family);
              }
              workload.prepare(sequence, iteration + batch * iterations + caseWarmup);
              long allocationStart = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
              long cpuStart = cpu ? timing.getCurrentThreadCpuTime() : 0;
              long start = System.nanoTime();
              workload.run();
              samples[iteration] = System.nanoTime() - start;
              elapsed += samples[iteration];
              cpuNanos += cpu ? timing.getCurrentThreadCpuTime() - cpuStart : 0;
              allocated += allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread) - allocationStart;
              if (iteration == 0) {
                firstState = workload.snapshot();
              }
            }
            JsonObject record = new JsonObject();
            record.addProperty("case", family);
            record.addProperty("sequence", sequence);
            record.addProperty("batch", batch);
            record.addProperty("operations", iterations);
            record.addProperty("warmupOperations", caseWarmup);
            record.addProperty("nanosPerOperation", (double) elapsed / iterations);
            record.addProperty("cpuNanosPerOperation", cpu ? (double) cpuNanos / iterations : -1.0);
            record.addProperty("bytesPerOperation", allocation == null ? -1.0 : (double) allocated / iterations);
            JsonArray raw = new JsonArray();
            for (long sample : samples) {
              raw.add(sample);
            }
            record.add("elapsedSamplesNanos", raw);
            Arrays.sort(samples);
            record.addProperty("p95Nanos", samples[(int) Math.ceil(0.95 * samples.length) - 1]);
            record.add("firstState", firstState);
            record.add("state", workload.snapshot());
            records.add(record);
          }
        } catch (RuntimeException ex) {
          JsonObject failure = new JsonObject();
          failure.addProperty("case", family);
          failure.addProperty("sequence", sequence);
          failure.addProperty("failure", ex.toString());
          records.add(failure);
        }
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("threadAllocationCountingEnabled", allocation != null);
    report.addProperty("threadCpuTimingEnabled", cpu);
    report.addProperty("warmStartKValues", neqsim.thermo.ThermodynamicModelSettings.isUseWarmStartKValues());
    report.addProperty("scope", "complete operations; synthetic numerical consistency, not experimental validation");
    report.add("records", records);
    Files.createDirectories(output.toAbsolutePath().getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
    }
  }
}
