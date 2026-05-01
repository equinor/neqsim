package neqsim.process.processmodel;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Speed benchmark for recycle-containing processes. Compares runOptimized() (which on master routes
 * recycle systems to sequential, on combined-speedup routes them to runHybrid) to baseline
 * runSequential() execution.
 */
public class RecycleSpeedBenchmarkTest {

  private SystemInterface makeFluid() {
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
    f.setMixingRule("classic");
    return f;
  }

  /**
   * 3 parallel feed-forward compression trains THEN a recycle loop at the tail. Maximises the
   * hybrid feed-forward-parallel opportunity before the tear.
   */
  private ProcessSystem buildParallelWithTailRecycle(boolean optimized) {
    ProcessSystem sys = new ProcessSystem("par-tail-recycle");
    sys.setUseOptimizedExecution(optimized);

    Stream feed = new Stream("feed", makeFluid());
    feed.setFlowRate(90000, "kg/hr");
    sys.add(feed);

    Splitter sp = new Splitter("splitter", feed, 3);
    sys.add(sp);

    Stream[] trainOut = new Stream[3];
    for (int t = 0; t < 3; t++) {
      Separator sep = new Separator("sep" + t, sp.getSplitStream(t));
      sys.add(sep);
      Compressor comp = new Compressor("comp" + t, sep.getGasOutStream());
      comp.setOutletPressure(150.0);
      sys.add(comp);
      Cooler cool = new Cooler("cool" + t, comp.getOutletStream());
      cool.setOutTemperature(310.0);
      sys.add(cool);
      trainOut[t] = (Stream) cool.getOutletStream();
    }

    Mixer mx = new Mixer("mixer");
    for (Stream s : trainOut)
      mx.addStream(s);
    sys.add(mx);

    // Tail recycle: split off a small fraction, valve it down, recycle to mixer
    Splitter tailSp = new Splitter("tailSplit", mx.getOutletStream(), 2);
    tailSp.setSplitFactors(new double[] {0.95, 0.05});
    sys.add(tailSp);

    ThrottlingValve recValve = new ThrottlingValve("recValve", tailSp.getSplitStream(1));
    recValve.setOutletPressure(80.0);
    sys.add(recValve);

    Recycle rec = new Recycle("tailRecycle");
    rec.addStream(recValve.getOutletStream());
    rec.setOutletStream(new Stream("recBack", recValve.getOutletStream().getFluid().clone()));
    sys.add(rec);
    mx.addStream(rec.getOutletStream());

    return sys;
  }

  @Test
  void benchmarkRecycleProcess() throws Exception {
    int RUNS = 5;

    System.out.println("\n===== RECYCLE BENCHMARK: 3-train parallel + tail recycle =====");

    // Sequential baseline
    ProcessSystem seq = buildParallelWithTailRecycle(false);
    seq.run(); // warmup
    long t0 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      // rebuild to avoid cached convergence
      seq = buildParallelWithTailRecycle(false);
      seq.run();
    }
    double seqMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

    // Optimized (runs runHybrid on combined-speedup, runSequential on master via fallback)
    ProcessSystem opt = buildParallelWithTailRecycle(true);
    opt.run(); // warmup
    t0 = System.nanoTime();
    for (int i = 0; i < RUNS; i++) {
      opt = buildParallelWithTailRecycle(true);
      opt.run();
    }
    double optMs = (System.nanoTime() - t0) / (double) RUNS / 1e6;

    System.out.printf("  Sequential: %.1f ms  |  Optimized: %.1f ms  |  Speedup: %.2fx%n", seqMs,
        optMs, seqMs / optMs);
    System.out.printf("  hasRecycles: %b  |  Max parallelism: %d%n", opt.hasRecycles(),
        opt.getParallelPartition().getMaxParallelism());
  }
}
