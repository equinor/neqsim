package neqsim.process.processmodel;

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
 * Long-running harness for attaching JFR / async-profiler to the parallel
 * compressor workload.
 */
public class JfrHotSpotHarness {

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

  /**
   * Run only when explicitly enabled. Usage:
   *
   * <pre>
   * .\mvnw.cmd test -Dtest=JfrHotSpotHarness#parallelCompressorLoop `
   *   -Dneqsim.jfr.run=true `
   *   -Dargline="-XX:StartFlightRecording=duration=30s,filename=parallel.jfr,settings=profile"
   * </pre>
   */
  @Test
  void parallelCompressorLoop() throws Exception {
    if (!"true".equalsIgnoreCase(System.getProperty("neqsim.jfr.run", "false"))) {
      return;
    }
    ProcessSystem sys = buildIndependentTrains(true, 8);
    sys.run(); // warm
    java.util.List<Compressor> compressors = new java.util.ArrayList<>();
    for (ProcessEquipmentInterface u : sys.getUnitOperations()) {
      if (u instanceof Compressor) {
        compressors.add((Compressor) u);
      }
    }
    for (Compressor c : compressors) {
      c.run();
    }

    long deadline = System.nanoTime() + 25L * 1_000_000_000L;
    int iters = 0;
    while (System.nanoTime() < deadline) {
      java.util.List<java.util.concurrent.Future<?>> futs = new java.util.ArrayList<>();
      for (final Compressor c : compressors) {
        futs.add(neqsim.util.NeqSimThreadPool.submit(() -> c.run()));
      }
      for (java.util.concurrent.Future<?> f : futs) {
        f.get();
      }
      iters++;
    }
    System.out.println("JFR harness iterations: " + iters);
  }
}
