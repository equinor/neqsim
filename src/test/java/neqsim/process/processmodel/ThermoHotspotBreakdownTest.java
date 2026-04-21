package neqsim.process.processmodel;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Break down the Compressor hot-path into discrete phases and measure serial vs
 * parallel scaling
 * for each. This isolates whether the slowdown is due to:
 *
 * <ul>
 * <li>A) Allocation pressure (clone / new ThermodynamicOperations)</li>
 * <li>B) init(3) — EOS derivatives, a(T), b, fugacities</li>
 * <li>C) initPhysicalProperties — transport properties</li>
 * <li>D) PSflash — newton iteration</li>
 * </ul>
 */
public class ThermoHotspotBreakdownTest {

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

  @FunctionalInterface
  interface Task {
    void run(SystemInterface s) throws Exception;
  }

  private double timeSerial(SystemInterface[] fluids, Task task, int iters) throws Exception {
    // Warm
    for (SystemInterface s : fluids) {
      task.run(s);
    }
    long t0 = System.nanoTime();
    for (int i = 0; i < iters; i++) {
      for (SystemInterface s : fluids) {
        task.run(s);
      }
    }
    return (System.nanoTime() - t0) / (double) iters / 1e6;
  }

  private double timeParallel(SystemInterface[] fluids, Task task, int iters) throws Exception {
    for (SystemInterface s : fluids) {
      task.run(s);
    }
    long t0 = System.nanoTime();
    for (int i = 0; i < iters; i++) {
      java.util.List<java.util.concurrent.Future<?>> futs = new java.util.ArrayList<>();
      for (final SystemInterface s : fluids) {
        futs.add(neqsim.util.NeqSimThreadPool.submit(() -> {
          try {
            task.run(s);
          } catch (Exception ex) {
            throw new RuntimeException(ex);
          }
        }));
      }
      for (java.util.concurrent.Future<?> f : futs) {
        f.get();
      }
    }
    return (System.nanoTime() - t0) / (double) iters / 1e6;
  }

  @Test
  void breakdownPerPhase() throws Exception {
    final int N = 8;
    final int ITERS = 40;

    // Build N primed fluids
    SystemInterface[] fluids = new SystemInterface[N];
    for (int i = 0; i < N; i++) {
      fluids[i] = makeHeavyFluid();
      ThermodynamicOperations ops = new ThermodynamicOperations(fluids[i]);
      ops.TPflash();
      fluids[i].init(3);
    }

    System.out.printf("%nCores: %d   N=%d fluids   iters=%d%n",
        Runtime.getRuntime().availableProcessors(), N, ITERS);
    System.out.printf("%-35s %10s %10s %8s%n", "phase", "serial_ms", "par_ms", "speedup");
    System.out.println("---------------------------------------------------------------------");

    Task[] tasks = new Task[] {
        // A: clone only — pure allocation
        s -> {
          SystemInterface c = s.clone();
          if (c.getPressure() < 0) {
            System.out.println("nope");
          }
        },
        // B: clone + init(3) — full EOS init
        s -> {
          SystemInterface c = s.clone();
          c.init(3);
        },
        // C: clone + TPflash (no stability)
        s -> {
          SystemInterface c = s.clone();
          c.setMultiPhaseCheck(false);
          ThermodynamicOperations ops = new ThermodynamicOperations(c);
          ops.TPflash();
        },
        // D: clone + TPflash WITH stability check
        s -> {
          SystemInterface c = s.clone();
          c.setMultiPhaseCheck(true);
          ThermodynamicOperations ops = new ThermodynamicOperations(c);
          ops.TPflash();
        },
        // E: clone + PSflash (compressor-like, no multiphase)
        s -> {
          SystemInterface c = s.clone();
          c.setMultiPhaseCheck(false);
          ThermodynamicOperations ops = new ThermodynamicOperations(c);
          ops.TPflash();
          double entropy = c.getEntropy();
          c.setPressure(150.0);
          ops.PSflash(entropy);
        } };
    String[] names = new String[] { "A: clone only", "B: clone + init(3)", "C: clone + TPflash (no stab)",
        "D: clone + TPflash (with stab)", "E: clone + TPflash + PSflash" };

    for (int k = 0; k < tasks.length; k++) {
      double serial = timeSerial(fluids, tasks[k], ITERS);
      double parallel = timeParallel(fluids, tasks[k], ITERS);
      System.out.printf("%-35s %10.3f %10.3f %7.2fx%n", names[k], serial, parallel,
          serial / parallel);
    }
  }
}
