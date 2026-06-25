package neqsim.process.processmodel;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Performance profiling test for NeqSim process simulation.
 *
 * <p>
 * Uses the built-in execution profiling API and JFR (Java Flight Recorder) to identify performance bottlenecks at the
 * equipment and method level.
 * </p>
 *
 * <p>
 * Run with JFR enabled for method-level profiling:
 * </p>
 *
 * <pre>
 * mvnw test -Dtest=ProcessProfilingTest#profileLargeProcess \
 *   -DargLine="-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=neqsim.jfr"
 * </pre>
 *
 * <p>
 * Then open neqsim.jfr in JDK Mission Control (jmc) or IntelliJ profiler.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ProcessProfilingTest {
  private static final Logger logger = LogManager.getLogger(ProcessProfilingTest.class);

  private SystemInterface makeRichGasFluid() {
    SystemInterface f = new SystemSrkEos(298.0, 80.0);
    f.addComponent("nitrogen", 0.01);
    f.addComponent("CO2", 0.02);
    f.addComponent("methane", 0.65);
    f.addComponent("ethane", 0.10);
    f.addComponent("propane", 0.06);
    f.addComponent("i-butane", 0.02);
    f.addComponent("n-butane", 0.03);
    f.addComponent("i-pentane", 0.015);
    f.addComponent("n-pentane", 0.015);
    f.addComponent("n-hexane", 0.01);
    f.addComponent("n-heptane", 0.01);
    f.addComponent("n-octane", 0.01);
    f.addComponent("water", 0.02);
    f.setMixingRule("classic");
    f.setMultiPhaseCheck(true);
    return f;
  }

  /**
   * Profile a large process with the built-in profiling API. Shows per-equipment time breakdown.
   */
  @Test
  void profileLargeProcess() {
    logger.info("\n========== PROCESS PROFILING: Built-in Execution Profiler ==========\n");

    // Build a realistic process: HP/LP separation + compression + heat exchange
    ProcessSystem process = buildRealisticProcess();
    process.setProfilingEnabled(true);

    // Warm up
    process.run();

    // Profile 5 runs
    int runs = 5;
    long totalStart = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      process.run();
    }
    double totalMs = (System.nanoTime() - totalStart) / 1e6;

    logger.printf(org.apache.logging.log4j.Level.INFO, "Total for %d runs: %.1f ms (%.1f ms/run)%n%n", runs, totalMs,
        totalMs / runs);
    process.printExecutionProfile();

    // Show execution strategy info
    logger.info("\n" + process.getExecutionPartitionInfo());
  }

  /**
   * Profile with JFR. Run this test with JFR VM args to get method-level profiling:
   *
   * <pre>
   * -XX:StartFlightRecording=duration=30s,filename=neqsim_profile.jfr,settings=profile
   * </pre>
   */
  @Test
  void profileWithJFR() throws Exception {
    logger.info("\n========== JFR PROFILING: Running 50 iterations ==========\n");

    // Check if JFR is available
    boolean jfrAvailable = false;
    try {
      Class.forName("jdk.jfr.FlightRecorder");
      jfrAvailable = true;
    } catch (ClassNotFoundException e) {
      logger.info("JFR not available (Java 8?). Running plain benchmark instead.");
    }

    // Start JFR programmatically if available (with "profile" config for CPU sampling)
    File jfrFile = null;
    Object recording = null;
    if (jfrAvailable) {
      try {
        jfrFile = new File("neqsim_profile.jfr");
        // Use reflection to avoid compile error on Java 8
        // Load "profile" configuration which enables jdk.ExecutionSample
        Class<?> configClass = Class.forName("jdk.jfr.Configuration");
        Object profileConfig = configClass.getMethod("getConfiguration", String.class).invoke(null, "profile");

        Class<?> recordingClass = Class.forName("jdk.jfr.Recording");
        recording = recordingClass.getConstructor(configClass).newInstance(profileConfig);
        recordingClass.getMethod("start").invoke(recording);
        System.out.println("JFR recording started (profile config) -> " + jfrFile.getAbsolutePath());
      } catch (Exception e) {
        logger.info("Could not start JFR programmatically: " + e.getMessage());
        jfrAvailable = false;
      }
    }

    ProcessSystem process = buildRealisticProcess();
    process.setProfilingEnabled(true);

    // Warm up
    process.run();

    // Run many iterations for profiling
    int runs = 50;
    long start = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      process.run();
    }
    double elapsedMs = (System.nanoTime() - start) / 1e6;

    // Stop JFR
    if (jfrAvailable && recording != null) {
      try {
        Class<?> recordingClass = recording.getClass();
        Class<?> pathClass = Class.forName("java.nio.file.Path");
        java.lang.reflect.Method dumpMethod = recordingClass.getMethod("dump", pathClass);
        Object path = java.nio.file.Paths.get(jfrFile.toURI());
        dumpMethod.invoke(recording, path);
        recordingClass.getMethod("stop").invoke(recording);
        recordingClass.getMethod("close").invoke(recording);
        logger.info("JFR recording saved to: " + jfrFile.getAbsolutePath());
        System.out.println("Open with: jmc " + jfrFile.getAbsolutePath() + "  (or IntelliJ profiler)");
      } catch (Exception e) {
        logger.info("Error saving JFR: " + e.getMessage());
      }
    }

    logger.printf(org.apache.logging.log4j.Level.INFO, "%nTotal for %d runs: %.1f ms (%.1f ms/run)%n%n", runs,
        elapsedMs, elapsedMs / runs);
    process.printExecutionProfile();
  }

  /**
   * Micro-benchmark individual operations to identify the real CPU cost of each. This doesn't need JFR - it directly
   * measures TPflash, init() levels, etc.
   */
  @Test
  void microBenchmarkThermodynamicOperations() {
    logger.info("\n========== MICRO-BENCHMARK: Thermodynamic Operations ==========\n");

    SystemInterface fluid = makeRichGasFluid();
    neqsim.thermodynamicoperations.ThermodynamicOperations ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        fluid);

    // First flash to establish phases
    ops.TPflash();
    fluid.initProperties();

    int runs = 100;

    // Benchmark TPflash
    long t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      ops.TPflash();
    }
    double tpflashMs = (System.nanoTime() - t0) / 1e6 / runs;

    // Benchmark init levels
    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.init(0);
    }
    double init0Ms = (System.nanoTime() - t0) / 1e6 / runs;

    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.init(1);
    }
    double init1Ms = (System.nanoTime() - t0) / 1e6 / runs;

    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.init(2);
    }
    double init2Ms = (System.nanoTime() - t0) / 1e6 / runs;

    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.init(3);
    }
    double init3Ms = (System.nanoTime() - t0) / 1e6 / runs;

    // Benchmark initProperties (init(2) + initPhysicalProperties)
    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.initProperties();
    }
    double initPropsMs = (System.nanoTime() - t0) / 1e6 / runs;

    // Benchmark PHflash
    double enthalpy = fluid.getEnthalpy();
    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      ops.PHflash(enthalpy);
    }
    double phflashMs = (System.nanoTime() - t0) / 1e6 / runs;

    // Benchmark PSflash
    double entropy = fluid.getEntropy();
    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      ops.PSflash(entropy);
    }
    double psflashMs = (System.nanoTime() - t0) / 1e6 / runs;

    // Benchmark clone
    t0 = System.nanoTime();
    for (int i = 0; i < runs; i++) {
      fluid.clone();
    }
    double cloneMs = (System.nanoTime() - t0) / 1e6 / runs;

    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10s%n", "Operation", "Time (ms)");
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10s%n", "-----------------------------------",
        "----------");
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "TPflash", tpflashMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "PHflash", phflashMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "PSflash", psflashMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "init(0) - compositions", init0Ms);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "init(1) - fugacities", init1Ms);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "init(2) - properties", init2Ms);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "init(3) - composition derivs", init3Ms);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "initProperties() - all props", initPropsMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%-35s %10.3f%n", "clone()", cloneMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "%n");

    double totalEquipRun = tpflashMs + initPropsMs; // Approximate what a Separator does
    logger.printf(org.apache.logging.log4j.Level.INFO,
        "Estimated Separator.run() cost: ~%.3f ms (TPflash + initProperties)%n", totalEquipRun);
    logger.printf(org.apache.logging.log4j.Level.INFO,
        "Estimated Compressor.run() cost: ~%.3f ms (PSflash + init(3) + initProperties)%n",
        psflashMs + init3Ms + initPropsMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Components: %d  |  Phases: %d%n", fluid.getNumberOfComponents(),
        fluid.getNumberOfPhases());
  }

  /**
   * Compare sequential vs optimized (hybrid/parallel) on process WITH Mixer and HeatExchanger. This was previously
   * always forced to sequential; now uses graph-based execution.
   */
  @Test
  void benchmarkHybridExecution() {
    System.out.println("\n========== BENCHMARK: Hybrid Execution (Mixer + HX processes) ==========\n");

    int warmup = 3;
    int runs = 10;

    // Scenario A: 3-train compression with Mixer
    logger.info("--- Scenario A: 3-train parallel compression + Mixer ---");
    {
      ProcessSystem seq = buildParallelCompressionWithMixer();
      seq.setUseOptimizedExecution(false); // Force sequential
      for (int i = 0; i < warmup; i++)
        seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      ProcessSystem opt = buildParallelCompressionWithMixer();
      opt.setUseOptimizedExecution(true); // Now uses parallel (graph handles Mixer)
      for (int i = 0; i < warmup; i++)
        opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      logger.printf(org.apache.logging.log4j.Level.INFO,
          "  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs, optMs, seqMs / optMs);
      logger.printf(org.apache.logging.log4j.Level.INFO, "  hasMultiInputEquipment: %b  |  Max parallelism: %d%n",
          opt.hasMultiInputEquipment(), opt.getMaxParallelism());
    }

    // Scenario B: HP/LP separation with HeatExchanger
    logger.info("\n--- Scenario B: HP/LP separation + HeatExchanger ---");
    {
      ProcessSystem seq = buildHPLPWithHeatExchanger();
      seq.setUseOptimizedExecution(false);
      for (int i = 0; i < warmup; i++)
        seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      ProcessSystem opt = buildHPLPWithHeatExchanger();
      opt.setUseOptimizedExecution(true);
      for (int i = 0; i < warmup; i++)
        opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      logger.printf(org.apache.logging.log4j.Level.INFO,
          "  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs, optMs, seqMs / optMs);
      logger.printf(org.apache.logging.log4j.Level.INFO, "  hasMultiInputEquipment: %b  |  Max parallelism: %d%n",
          opt.hasMultiInputEquipment(), opt.getMaxParallelism());
    }

    // Scenario C: Large 6-train process with Mixer (the big parallelism opportunity)
    logger.info("\n--- Scenario C: 6-train compression + Mixer (max parallel) ---");
    {
      ProcessSystem seq = buildNTrainCompression(6);
      seq.setUseOptimizedExecution(false);
      for (int i = 0; i < warmup; i++)
        seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      ProcessSystem opt = buildNTrainCompression(6);
      opt.setUseOptimizedExecution(true);
      opt.setProfilingEnabled(true);
      for (int i = 0; i < warmup; i++)
        opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < runs; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) runs / 1e6;

      logger.printf(org.apache.logging.log4j.Level.INFO,
          "  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs, optMs, seqMs / optMs);
      logger.printf(org.apache.logging.log4j.Level.INFO, "  hasMultiInputEquipment: %b  |  Max parallelism: %d%n",
          opt.hasMultiInputEquipment(), opt.getMaxParallelism());
      logger.info("\n  Per-unit profile (last run):");
      opt.printExecutionProfile();
    }

    logger.info("\n===== BENCHMARK COMPLETE =====\n");
  }

  /**
   * Thread CPU time profiling - measures actual CPU time vs wall-clock to detect serialization bottlenecks vs true
   * parallelism.
   */
  @Test
  void measureCPUVsWallTime() {
    logger.info("\n========== CPU Time vs Wall-Clock Analysis ==========\n");
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    if (!threadMXBean.isCurrentThreadCpuTimeSupported()) {
      logger.info("CPU time measurement not supported on this JVM");
      return;
    }
    threadMXBean.setThreadCpuTimeEnabled(true);

    ProcessSystem process = buildNTrainCompression(4);

    // Sequential
    process.setUseOptimizedExecution(false);
    process.run(); // warm up

    long cpuStart = threadMXBean.getCurrentThreadCpuTime();
    long wallStart = System.nanoTime();
    for (int i = 0; i < 10; i++)
      process.run();
    double seqWallMs = (System.nanoTime() - wallStart) / 1e6;
    double seqCpuMs = (threadMXBean.getCurrentThreadCpuTime() - cpuStart) / 1e6;

    logger.printf(org.apache.logging.log4j.Level.INFO, "Sequential: wall=%.1f ms, CPU=%.1f ms, ratio=%.2f%n", seqWallMs,
        seqCpuMs, seqCpuMs / seqWallMs);

    // Optimized (parallel/hybrid)
    process.setUseOptimizedExecution(true);
    process.run(); // warm up

    cpuStart = threadMXBean.getCurrentThreadCpuTime();
    wallStart = System.nanoTime();
    for (int i = 0; i < 10; i++)
      process.run();
    double optWallMs = (System.nanoTime() - wallStart) / 1e6;
    double optCpuMs = (threadMXBean.getCurrentThreadCpuTime() - cpuStart) / 1e6;

    logger.printf(org.apache.logging.log4j.Level.INFO, "Optimized:  wall=%.1f ms, CPU=%.1f ms, ratio=%.2f%n", optWallMs,
        optCpuMs, optCpuMs / optWallMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Wall-clock speedup: %.2fx%n", seqWallMs / optWallMs);
    logger.printf(org.apache.logging.log4j.Level.INFO, "Note: CPU/wall ratio > 1.0 indicates multi-threaded work%n");
  }

  // ---- Builder methods ----

  private ProcessSystem buildRealisticProcess() {
    ProcessSystem sys = new ProcessSystem("realistic");
    Stream f = new Stream("feed", makeRichGasFluid());
    f.setFlowRate(80000, "kg/hr");
    sys.add(f);

    // HP separation
    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP sep", f);
    sys.add(hpSep);

    // Gas path: compression + cooling
    Compressor gasComp = new Compressor("gas comp", hpSep.getGasOutStream());
    gasComp.setOutletPressure(120.0);
    sys.add(gasComp);

    Cooler gasCool = new Cooler("gas cooler", gasComp.getOutletStream());
    gasCool.setOutTemperature(310.0);
    sys.add(gasCool);

    // Liquid path: LP valve + separator
    ThrottlingValve lpValve = new ThrottlingValve("LP valve", hpSep.getLiquidOutStream());
    lpValve.setOutletPressure(5.0);
    sys.add(lpValve);

    Separator lpSep = new Separator("LP sep", lpValve.getOutletStream());
    sys.add(lpSep);

    Heater lpGasHeater = new Heater("LP gas heater", lpSep.getGasOutStream());
    lpGasHeater.setOutTemperature(330.0);
    sys.add(lpGasHeater);

    // Heat exchange between gas and LP gas paths
    HeatExchanger hx = new HeatExchanger("HX", gasCool.getOutletStream());
    hx.setFeedStream(1, lpGasHeater.getOutletStream());
    hx.setUAvalue(5000.0);
    sys.add(hx);

    return sys;
  }

  private ProcessSystem buildParallelCompressionWithMixer() {
    ProcessSystem sys = new ProcessSystem("par-comp-mixer");
    Stream f = new Stream("feed", makeRichGasFluid());
    f.setFlowRate(90000, "kg/hr");
    sys.add(f);

    Splitter sp = new Splitter("splitter", f, 3);
    sys.add(sp);

    for (int t = 0; t < 3; t++) {
      Separator sep = new Separator("sep" + t, sp.getSplitStream(t));
      sys.add(sep);
      Compressor comp = new Compressor("comp" + t, sep.getGasOutStream());
      comp.setOutletPressure(150.0);
      sys.add(comp);
      Cooler cool = new Cooler("cool" + t, comp.getOutletStream());
      cool.setOutTemperature(303.0);
      sys.add(cool);
    }

    Mixer mx = new Mixer("mixer");
    mx.addStream(((Cooler) sys.getUnit("cool0")).getOutletStream());
    mx.addStream(((Cooler) sys.getUnit("cool1")).getOutletStream());
    mx.addStream(((Cooler) sys.getUnit("cool2")).getOutletStream());
    sys.add(mx);

    return sys;
  }

  private ProcessSystem buildHPLPWithHeatExchanger() {
    ProcessSystem sys = new ProcessSystem("hplp-hx");
    Stream f = new Stream("feed", makeRichGasFluid());
    f.setFlowRate(80000, "kg/hr");
    sys.add(f);

    ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP sep", f);
    sys.add(hpSep);

    Compressor gasComp = new Compressor("gas comp", hpSep.getGasOutStream());
    gasComp.setOutletPressure(120.0);
    sys.add(gasComp);

    Cooler gasCool = new Cooler("gas cooler", gasComp.getOutletStream());
    gasCool.setOutTemperature(310.0);
    sys.add(gasCool);

    ThrottlingValve lpValve = new ThrottlingValve("LP valve", hpSep.getLiquidOutStream());
    lpValve.setOutletPressure(5.0);
    sys.add(lpValve);

    Separator lpSep = new Separator("LP sep", lpValve.getOutletStream());
    sys.add(lpSep);

    Heater lpGasHeater = new Heater("LP gas heater", lpSep.getGasOutStream());
    lpGasHeater.setOutTemperature(330.0);
    sys.add(lpGasHeater);

    HeatExchanger hx = new HeatExchanger("HX", gasCool.getOutletStream());
    hx.setFeedStream(1, lpGasHeater.getOutletStream());
    hx.setUAvalue(5000.0);
    sys.add(hx);

    return sys;
  }

  private ProcessSystem buildNTrainCompression(int numTrains) {
    ProcessSystem sys = new ProcessSystem(numTrains + "-train");
    Stream f = new Stream("feed", makeRichGasFluid());
    f.setFlowRate(numTrains * 30000.0, "kg/hr");
    sys.add(f);

    Splitter sp = new Splitter("splitter", f, numTrains);
    sys.add(sp);

    for (int t = 0; t < numTrains; t++) {
      ThreePhaseSeparator sep = new ThreePhaseSeparator("sep" + t, sp.getSplitStream(t));
      sys.add(sep);

      Compressor comp = new Compressor("comp" + t, sep.getGasOutStream());
      comp.setOutletPressure(150.0);
      sys.add(comp);

      Cooler cool = new Cooler("cool" + t, comp.getOutletStream());
      cool.setOutTemperature(303.0);
      sys.add(cool);

      Separator sep2 = new Separator("sep2-" + t, cool.getOutletStream());
      sys.add(sep2);

      Heater heater = new Heater("heat" + t, sep2.getGasOutStream());
      heater.setOutTemperature(340.0);
      sys.add(heater);
    }

    // Mixer at the end (multi-input equipment)
    Mixer mx = new Mixer("mixer");
    for (int t = 0; t < numTrains; t++) {
      mx.addStream(((Heater) sys.getUnit("heat" + t)).getOutletStream());
    }
    sys.add(mx);

    return sys;
  }
}
