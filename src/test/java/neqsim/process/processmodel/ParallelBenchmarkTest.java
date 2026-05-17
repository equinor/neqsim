package neqsim.process.processmodel;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class ParallelBenchmarkTest {

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

  @Test
  void benchmarkParallelVsSequential() throws Exception {
    int RUNS = 10;

    // ---- SCENARIO 1: Linear chain ----
    System.out.println("\n===== SCENARIO 1: Linear chain (8 units, no parallelism) =====");
    {
      ProcessSystem seq = buildLinearChain(false);
      seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      ProcessSystem opt = buildLinearChain(true);
      opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
          optMs, seqMs / optMs);
    }

    // ---- SCENARIO 2: Splitter -> 3 parallel compression trains -> Mixer ----
    System.out
        .println("\n===== SCENARIO 2: 3-train parallel compression with Mixer (14 units) =====");
    {
      ProcessSystem seq = buildParallelCompressionTrains(false);
      seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      ProcessSystem opt = buildParallelCompressionTrains(true);
      opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
          optMs, seqMs / optMs);
      System.out.printf("  hasMultiInputEquipment: %b  |  Max parallelism: %d%n",
          opt.hasMultiInputEquipment(), opt.getParallelPartition().getMaxParallelism());
    }

    // ---- SCENARIO 3: 4 independent heavy trains ----
    System.out.println("\n===== SCENARIO 3: 4 independent heavy trains (24 units) =====");
    {
      ProcessSystem seq = buildIndependentTrains(false, 4);
      seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      ProcessSystem opt = buildIndependentTrains(true, 4);
      opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
          optMs, seqMs / optMs);
      System.out.printf("  Max parallelism: %d%n", opt.getParallelPartition().getMaxParallelism());
    }

    // ---- SCENARIO 4: 8 independent heavy trains ----
    System.out.println("\n===== SCENARIO 4: 8 independent heavy trains (48 units) =====");
    {
      ProcessSystem seq = buildIndependentTrains(false, 8);
      seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      ProcessSystem opt = buildIndependentTrains(true, 8);
      opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
          optMs, seqMs / optMs);
      System.out.printf("  Max parallelism: %d%n", opt.getParallelPartition().getMaxParallelism());
    }

    // ---- SCENARIO 5: HP/LP sep with HeatExchanger (previously forced sequential) ----
    System.out.println(
        "\n===== SCENARIO 5: HP/LP separation with HeatExchanger (previously forced sequential) =====");
    {
      ProcessSystem seq = buildHPLPWithHX(false);
      seq.run();
      long t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        seq.run();
      double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      ProcessSystem opt = buildHPLPWithHX(true);
      opt.run();
      t0 = System.nanoTime();
      for (int i = 0; i < RUNS; i++)
        opt.run();
      double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

      System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
          optMs, seqMs / optMs);
      System.out.printf("  hasMultiInputEquipment: %b  |  Max parallelism: %d%n",
          opt.hasMultiInputEquipment(), opt.getParallelPartition().getMaxParallelism());
    }

    System.out.println("\n===== BENCHMARK COMPLETE =====\n");
  }

  private ProcessSystem buildLinearChain(boolean optimized) {
    ProcessSystem sys = new ProcessSystem("linear");
    sys.setUseOptimizedExecution(optimized);
    Stream f = new Stream("feed", makeHeavyFluid());
    f.setFlowRate(50000, "kg/hr");
    sys.add(f);
    ThreePhaseSeparator sep = new ThreePhaseSeparator("3ph-sep", f);
    sys.add(sep);
    Compressor comp = new Compressor("comp", sep.getGasOutStream());
    comp.setOutletPressure(120.0);
    sys.add(comp);
    Cooler cool = new Cooler("cooler", comp.getOutletStream());
    cool.setOutTemperature(303.0);
    sys.add(cool);
    Separator sep2 = new Separator("sep2", cool.getOutletStream());
    sys.add(sep2);
    Heater h = new Heater("heater", sep2.getGasOutStream());
    h.setOutTemperature(350.0);
    sys.add(h);
    return sys;
  }

  private ProcessSystem buildParallelCompressionTrains(boolean optimized) {
    ProcessSystem sys = new ProcessSystem("par-comp");
    sys.setUseOptimizedExecution(optimized);
    Stream f = new Stream("feed", makeHeavyFluid());
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
    // previously forced sequential due to Mixer
    Mixer mx = new Mixer("mixer");
    mx.addStream(((Cooler) sys.getUnit("cool0")).getOutletStream());
    mx.addStream(((Cooler) sys.getUnit("cool1")).getOutletStream());
    mx.addStream(((Cooler) sys.getUnit("cool2")).getOutletStream());
    sys.add(mx);
    return sys;
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

  private ProcessSystem buildHPLPWithHX(boolean optimized) {
    ProcessSystem sys = new ProcessSystem("hplp-hx");
    sys.setUseOptimizedExecution(optimized);
    Stream f = new Stream("feed", makeHeavyFluid());
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
}
