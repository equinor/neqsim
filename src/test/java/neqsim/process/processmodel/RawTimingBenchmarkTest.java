package neqsim.process.processmodel;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Lock-free timing harness: uses a ConcurrentHashMap with atomic accumulation so profiling overhead
 * is not confused with parallel scaling overhead.
 */
public class RawTimingBenchmarkTest {

  private SystemInterface makeHeavyFluid() {
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

  private ProcessSystem buildIndependentTrains(boolean optimized, int numTrains) {
    ProcessSystem sys = new ProcessSystem(numTrains + "-trains");
    sys.setUseOptimizedExecution(optimized);
    for (int t = 0; t < numTrains; t++) {
      Stream f = new Stream("feed" + t, makeHeavyFluid());
      f.setFlowRate(20000, "kg/hr");
      sys.add(f);
      ThreePhaseSeparator sep = new ThreePhaseSeparator("3ph" + t, f);
      sys.add(sep);
      Compressor comp = new Compressor("comp" + t, sep.getGasOutStream());
      comp.setOutletPressure(150.0);
      sys.add(comp);
      Cooler cool = new Cooler("cool" + t, comp.getOutletStream());
      cool.setOutTemperature(303.0);
      sys.add(cool);
      Separator sep2 = new Separator("sep2-" + t, cool.getOutletStream());
      sys.add(sep2);
      Heater h = new Heater("heat" + t, sep2.getGasOutStream());
      h.setOutTemperature(340.0);
      sys.add(h);
    }
    return sys;
  }

  @Test
  void timeComprssorInSerialVsParallel() throws Exception {
    // Build 8 compressors sharing the architecture of the 8-train case
    ProcessSystem sys = buildIndependentTrains(true, 8);
    sys.run(); // warmup

    // Collect all compressors
    java.util.List<Compressor> compressors = new java.util.ArrayList<>();
    for (ProcessEquipmentInterface u : sys.getUnitOperations()) {
      if (u instanceof Compressor) {
        compressors.add((Compressor) u);
      }
    }
    // Warm-up all fluids
    for (Compressor c : compressors) {
      c.run();
    }

    final int ITERS = 50;

    // === SERIAL: run all 8 compressors sequentially ===
    long t0 = System.nanoTime();
    for (int i = 0; i < ITERS; i++) {
      for (Compressor c : compressors) {
        c.run();
      }
    }
    long serialNs = System.nanoTime() - t0;
    double serialPerRun = serialNs / (double) ITERS / 1e6;
    double serialPerCall = serialPerRun / compressors.size();

    // === PARALLEL: run all 8 compressors concurrently in the NeqSim pool ===
    t0 = System.nanoTime();
    for (int i = 0; i < ITERS; i++) {
      java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
      for (final Compressor c : compressors) {
        futures.add(neqsim.util.NeqSimThreadPool.submit(() -> c.run()));
      }
      for (java.util.concurrent.Future<?> f : futures) {
        f.get();
      }
    }
    long parallelNs = System.nanoTime() - t0;
    double parallelPerRun = parallelNs / (double) ITERS / 1e6;
    double parallelPerCall = parallelPerRun / compressors.size();

    System.out.println("\n===== RAW Compressor timing (8 independent compressors) =====");
    System.out.printf("cores available:      %d%n", Runtime.getRuntime().availableProcessors());
    System.out.printf("SERIAL:   wall=%.2f ms   per-call=%.3f ms%n", serialPerRun, serialPerCall);
    System.out.printf("PARALLEL: wall=%.2f ms   per-call=%.3f ms (effective)%n", parallelPerRun,
        parallelPerCall);
    System.out.printf("Parallel speedup: %.2fx (ideal = %d)%n", serialPerRun / parallelPerRun,
        compressors.size());
    System.out.printf("Parallel slowdown per call: %.2fx%n", parallelPerCall / serialPerCall);
  }

  @Test
  void measureFrameworkOverhead() throws Exception {
    // 8-train case, no profiling. Track per-run wall clock to expose
    // anything other than unit time: graph building, recalculation checks,
    // thread-pool dispatch.
    ProcessSystem opt = buildIndependentTrains(true, 8);
    opt.run(); // warmup - also builds cachedParallelPlan

    // Count needRecalculation calls & run timings
    ConcurrentHashMap<String, long[]> needRecalcCount = new ConcurrentHashMap<>();

    final int RUNS = 40;
    long t0 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      opt.run();
    }
    double optWall = (System.nanoTime() - t0) / (double) RUNS / 1e6;

    ProcessSystem seq = buildIndependentTrains(false, 8);
    seq.run();
    t0 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      seq.run();
    }
    double seqWall = (System.nanoTime() - t0) / (double) RUNS / 1e6;

    System.out.println("\n===== Framework wall-clock (no profiling) =====");
    System.out.printf("sequential:  %.2f ms/run%n", seqWall);
    System.out.printf("optimized:   %.2f ms/run%n", optWall);
    System.out.printf("speedup:     %.2fx%n", seqWall / optWall);
  }
}
