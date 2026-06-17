package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Measures where time is being spent inside ProcessSystem.run() — wall-clock vs sum of unit
 * execution times vs framework overhead — to locate optimization opportunities beyond the simple
 * seq-vs-optimized speedup numbers.
 */
public class ProfilingBenchmarkTest {

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

  /** Run and gather aggregate timing. Returns [wallMs, sumUnitMs, numUnitCalls]. */
  private double[] timed(ProcessSystem sys, int runs) {
    sys.setProfilingEnabled(true);
    // Warm up
    sys.run();
    double totalWall = 0.0;
    double totalUnit = 0.0;
    long totalCalls = 0;
    for (int i = 0; i < runs; i++) {
      sys.run();
      totalWall += sys.getLastRunElapsedMs();
      for (double[] v : sys.getExecutionProfile().values()) {
        totalUnit += v[0];
        totalCalls += (long) v[1];
      }
    }
    return new double[] {totalWall / runs, totalUnit / runs, totalCalls / (double) runs};
  }

  /** Aggregated per-equipment-class timing across all runs. */
  private Map<String, double[]> classProfile(ProcessSystem sys, int runs) {
    sys.setProfilingEnabled(true);
    sys.run(); // warm-up
    // Build name -> simple class name map
    Map<String, String> nameToClass = new HashMap<>();
    for (ProcessEquipmentInterface u : sys.getUnitOperations()) {
      nameToClass.put(u.getName(), u.getClass().getSimpleName());
    }
    Map<String, double[]> classTotals = new HashMap<>();
    for (int i = 0; i < runs; i++) {
      sys.run();
      for (Map.Entry<String, double[]> e : sys.getExecutionProfile().entrySet()) {
        String cls = nameToClass.getOrDefault(e.getKey(), "?");
        double[] cur = classTotals.get(cls);
        if (cur == null) {
          cur = new double[] {0, 0};
          classTotals.put(cls, cur);
        }
        cur[0] += e.getValue()[0];
        cur[1] += e.getValue()[1];
      }
    }
    for (double[] v : classTotals.values()) {
      v[0] /= runs;
      v[1] /= runs;
    }
    return classTotals;
  }

  @Test
  void profileIndependentTrains() throws Exception {
    final int RUNS = 20;
    int[] trainsCases = {1, 4, 8};

    System.out.println("\n===== PROFILING: Independent trains (where is time spent?) =====");
    System.out.printf("%-8s %-12s %10s %10s %10s %10s %10s%n", "trains", "mode", "wall_ms",
        "sumUnit_ms", "framework", "calls/run", "speedup_vs_seq");
    System.out.println(
        "--------------------------------------------------------------------------------");

    for (int nTrains : trainsCases) {
      ProcessSystem seq = buildIndependentTrains(false, nTrains);
      double[] seqT = timed(seq, RUNS);

      ProcessSystem opt = buildIndependentTrains(true, nTrains);
      double[] optT = timed(opt, RUNS);

      double seqOverhead = seqT[0] - seqT[1];
      double optOverhead = optT[0] - optT[1];

      System.out.printf("%-8d %-12s %10.2f %10.2f %10.2f %10.1f %10s%n", nTrains, "sequential",
          seqT[0], seqT[1], seqOverhead, seqT[2], "1.00x");
      System.out.printf("%-8d %-12s %10.2f %10.2f %10.2f %10.1f %10s%n", nTrains, "optimized",
          optT[0], optT[1], optOverhead, optT[2], String.format("%.2fx", seqT[0] / optT[0]));
    }

    // Equipment-class breakdown for a single representative case
    System.out.println("\n----- Per-class timing (8 trains, optimized, averaged) -----");
    ProcessSystem probe = buildIndependentTrains(true, 8);
    Map<String, double[]> cls = classProfile(probe, RUNS);
    List<Map.Entry<String, double[]>> sorted = new ArrayList<>(cls.entrySet());
    Collections.sort(sorted, (a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
    System.out.printf("%-30s %12s %12s%n", "equipment_class", "total_ms", "calls");
    System.out.println("------------------------------------------------------------");
    double total = 0.0;
    for (double[] v : cls.values()) {
      total += v[0];
    }
    for (Map.Entry<String, double[]> e : sorted) {
      System.out.printf("%-30s %12.2f %12.1f  (%.1f%%)%n", e.getKey(), e.getValue()[0],
          e.getValue()[1], 100.0 * e.getValue()[0] / total);
    }
    System.out.printf("%-30s %12.2f%n", "TOTAL_UNIT_TIME", total);
  }
}
