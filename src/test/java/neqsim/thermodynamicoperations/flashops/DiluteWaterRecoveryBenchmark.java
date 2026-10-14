package neqsim.thermodynamicoperations.flashops;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete-flash CPU, elapsed and allocation evidence for dilute-water recovery.
 *
 * <p>
 * Run identical JVMs on the comparison heads. Timings are observations, never CI assertions. Construction and cloning
 * of cold-start inputs are outside the measured interval. Numerical snapshots accompany every batch so a faster
 * incorrect phase split cannot pass as an improvement.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class DiluteWaterRecoveryBenchmark {
  /** Prevents construction of this standalone benchmark. */
  private DiluteWaterRecoveryBenchmark() {
  }

  /**
   * Builds a synthetic SRK workload with the specified water content.
   *
   * @param family workload identifier
   * @return unflashed fluid
   */
  private static SystemInterface create(String family) {
    if ("dry-gas".equals(family)) {
      SystemInterface gas = new SystemSrkEos(323.15, 20.0);
      gas.addComponent("methane", 0.95);
      gas.addComponent("ethane", 0.05);
      gas.setMixingRule("classic");
      gas.setMultiPhaseCheck(true);
      return gas;
    }
    double water = "undersaturated".equals(family) ? 1.0e-8
        : "seed-floor".equals(family) ? 1.0e-12 : "dilute".equals(family) ? 0.007 : 0.07767062724736153;
    SystemInterface fluid = new SystemSrkEos("three-phase".equals(family) ? 284.15 : 308.2,
        "three-phase".equals(family) ? 1.8 : 4.7);
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane", "n-octane"};
    double[] amounts = {0.03333751952167422, 0.054160101428384885, 0.16166835683018532, 0.2519356897139214,
        0.19282738794737694, 0.17687766832017773, 0.05152264899091814};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component] * (1.0 - water) / (1.0 - 0.07767062724736153));
    }
    fluid.addComponent("water", water);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Measures complete cold and warm flashes after per-case warmup.
   *
   * @param args output path, optional warmup count and optional flashes per batch
   * @throws Exception if execution or writing fails
   */
  public static void main(String[] args) throws Exception {
    int warmup = args.length > 1 ? Integer.parseInt(args[1]) : 128;
    int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 64;
    java.lang.management.ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
    boolean hasCpu = cpu.isCurrentThreadCpuTimeSupported();
    if (hasCpu && !cpu.isThreadCpuTimeEnabled()) {
      cpu.setThreadCpuTimeEnabled(true);
    }
    com.sun.management.ThreadMXBean allocations = cpu instanceof com.sun.management.ThreadMXBean
        ? (com.sun.management.ThreadMXBean) cpu
        : null;
    boolean hasAllocation = allocations != null && allocations.isThreadAllocatedMemorySupported();
    if (hasAllocation && !allocations.isThreadAllocatedMemoryEnabled()) {
      allocations.setThreadAllocatedMemoryEnabled(true);
    }
    long thread = Thread.currentThread().getId();
    JsonArray records = new JsonArray();
    for (String family : new String[] {"dry-gas", "seed-floor", "undersaturated", "dilute", "three-phase"}) {
      SystemInterface template = create(family);
      double originalMoles = template.getTotalNumberOfMoles();
      double originalMass = StabilityOptimizationBenchmark.inventoryMass(template);
      int caseWarmup = "dry-gas".equals(family) || "seed-floor".equals(family) ? Math.max(4096, warmup) : warmup;
      for (boolean cold : new boolean[] {true, false}) {
        SystemInterface fluid = template.clone();
        for (int iteration = 0; iteration < caseWarmup; iteration++) {
          if (cold) {
            fluid = template.clone();
          }
          new TPflash(fluid).run();
        }
        for (int batch = 0; batch < 7; batch++) {
          long nanos = 0;
          long cpuNanos = 0;
          long bytes = 0;
          for (int iteration = 0; iteration < iterations; iteration++) {
            if (cold) {
              fluid = template.clone();
            }
            long allocationStart = hasAllocation ? allocations.getThreadAllocatedBytes(thread) : 0;
            long cpuStart = hasCpu ? cpu.getCurrentThreadCpuTime() : 0;
            long start = System.nanoTime();
            new TPflash(fluid).run();
            nanos += System.nanoTime() - start;
            cpuNanos += hasCpu ? cpu.getCurrentThreadCpuTime() - cpuStart : 0;
            bytes += hasAllocation ? allocations.getThreadAllocatedBytes(thread) - allocationStart : 0;
          }
          JsonObject record = new JsonObject();
          record.addProperty("case", family);
          record.addProperty("start", cold ? "cold" : "warm");
          record.addProperty("batch", batch);
          record.addProperty("warmupFlashes", caseWarmup);
          record.addProperty("nanosPerFlash", (double) nanos / iterations);
          record.addProperty("cpuNanosPerFlash", hasCpu ? (double) cpuNanos / iterations : -1.0);
          record.addProperty("bytesPerFlash", hasAllocation ? (double) bytes / iterations : -1.0);
          record.add("state", StabilityOptimizationBenchmark.snapshot(fluid, originalMoles, originalMass));
          records.add(record);
        }
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("warmup", warmup);
    report.addProperty("flashesPerBatch", iterations);
    report.add("records", records);
    Files.write(Paths.get(args[0]),
        new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
  }
}
