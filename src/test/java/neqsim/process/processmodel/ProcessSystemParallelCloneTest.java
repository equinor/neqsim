package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Thread-safety audit for the clone-and-run pattern used by parallel scenario sweeps.
 *
 * <p>
 * Agentic optimization and DoE workflows want to fan out many independent scenarios across threads.
 * The safe pattern is to {@link ProcessSystem#copy()} a base flowsheet once per scenario (a
 * serialization deep copy that produces a fully independent object graph) and run each copy on its
 * own thread. This test verifies that the deep copies are genuinely independent: running many
 * copies in parallel produces results bit-identical to running the same scenarios sequentially.
 * </p>
 */
class ProcessSystemParallelCloneTest {

  /**
   * Builds a base feed/separator/compressor process whose compressor outlet pressure encodes the
   * scenario, so each scenario produces a distinct, deterministic compressor power.
   *
   * @param outletPressureBara compressor discharge pressure in bara
   * @return a constructed (not yet run) process system
   */
  private ProcessSystem buildScenario(double outletPressureBara) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.08);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(outletPressureBara);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
  }

  /**
   * Runs a scenario and returns the compressor shaft power as the deterministic fingerprint.
   *
   * @param process the process to run
   * @return compressor power in watts
   */
  private double runAndGetPower(ProcessSystem process) {
    process.run();
    Compressor compressor = (Compressor) process.getUnit("Compressor");
    return compressor.getPower();
  }

  @Test
  void testParallelCloneRunMatchesSequential() throws Exception {
    final int scenarioCount = 16;
    ProcessSystem base = buildScenario(120.0);

    // Sequential baseline: copy + run each scenario one at a time.
    List<Double> sequential = new ArrayList<Double>();
    for (int i = 0; i < scenarioCount; i++) {
      double outletPressure = 110.0 + i;
      ProcessSystem copy = base.copy();
      ((Compressor) copy.getUnit("Compressor")).setOutletPressure(outletPressure);
      sequential.add(runAndGetPower(copy));
    }

    // Parallel: copy once per scenario, run each copy on its own thread.
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Double>> futures = new ArrayList<Future<Double>>();
      for (int i = 0; i < scenarioCount; i++) {
        final double outletPressure = 110.0 + i;
        final ProcessSystem copy = base.copy();
        ((Compressor) copy.getUnit("Compressor")).setOutletPressure(outletPressure);
        futures.add(pool.submit(new Callable<Double>() {
          @Override
          public Double call() {
            return runAndGetPower(copy);
          }
        }));
      }
      for (int i = 0; i < scenarioCount; i++) {
        double parallelResult = futures.get(i).get();
        double sequentialResult = sequential.get(i).doubleValue();
        assertTrue(parallelResult > 0.0, "scenario " + i + " should produce positive power");
        assertEquals(sequentialResult, parallelResult, Math.abs(sequentialResult) * 1.0e-9 + 1.0e-6,
            "parallel clone-and-run must match sequential for scenario " + i);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void testCopiesAreIndependent() {
    ProcessSystem base = buildScenario(120.0);
    base.run();
    double basePower = ((Compressor) base.getUnit("Compressor")).getPower();

    // Mutate a copy and re-run it; the base must be unaffected.
    ProcessSystem copy = base.copy();
    ((Compressor) copy.getUnit("Compressor")).setOutletPressure(150.0);
    copy.run();
    double copyPower = ((Compressor) copy.getUnit("Compressor")).getPower();

    double basePowerAfter = ((Compressor) base.getUnit("Compressor")).getPower();
    assertEquals(basePower, basePowerAfter, 1.0e-9,
        "mutating and running a copy must not change the base process");
    assertTrue(copyPower > basePower,
        "higher discharge pressure on the copy must require more power");
  }
}
