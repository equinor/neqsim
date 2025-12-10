package neqsim.process.processmodel;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Benchmark test to identify performance bottlenecks in process simulation.
 */
public class ProcessSimulationBenchmark {

  /**
   * Benchmark a typical oil and gas process with multiple unit operations.
   */
  @Test
  public void benchmarkTypicalOilGasProcess() {
    System.out.println("=== Process Simulation Benchmark ===\n");

    // Create a realistic multi-component hydrocarbon system
    SystemInterface feed = new SystemSrkEos(298.15, 50.0);
    feed.addComponent("nitrogen", 0.02);
    feed.addComponent("CO2", 0.03);
    feed.addComponent("methane", 0.70);
    feed.addComponent("ethane", 0.10);
    feed.addComponent("propane", 0.08);
    feed.addComponent("n-butane", 0.04);
    feed.addComponent("n-pentane", 0.02);
    feed.addComponent("n-hexane", 0.01);
    feed.setMixingRule("classic");

    // Benchmark individual operations
    Map<String, Long> timings = new HashMap<>();

    // 1. Benchmark Stream creation and initialization
    long start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Stream testStream = new Stream("test", feed.clone());
      testStream.setFlowRate(1000.0, "kg/hr");
      testStream.run();
    }
    timings.put("Stream (100x)", (System.nanoTime() - start) / 1_000_000);

    // 2. Benchmark Separator
    Stream sepFeed = new Stream("sepFeed", feed.clone());
    sepFeed.setFlowRate(1000.0, "kg/hr");
    sepFeed.run();

    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Separator sep = new Separator("sep", sepFeed);
      sep.run();
    }
    timings.put("Separator (100x)", (System.nanoTime() - start) / 1_000_000);

    // 3. Benchmark Mixer with multiple streams
    Stream stream1 = new Stream("stream1", feed.clone());
    stream1.setFlowRate(500.0, "kg/hr");
    stream1.run();

    Stream stream2 = new Stream("stream2", feed.clone());
    stream2.setFlowRate(500.0, "kg/hr");
    stream2.setTemperature(310.0, "K");
    stream2.run();

    Stream stream3 = new Stream("stream3", feed.clone());
    stream3.setFlowRate(300.0, "kg/hr");
    stream3.setTemperature(290.0, "K");
    stream3.run();

    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Mixer mixer = new Mixer("mixer");
      mixer.addStream(stream1);
      mixer.addStream(stream2);
      mixer.addStream(stream3);
      mixer.run();
    }
    timings.put("Mixer 3-stream (100x)", (System.nanoTime() - start) / 1_000_000);

    // 4. Benchmark Splitter
    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Splitter splitter = new Splitter("splitter", sepFeed, 3);
      splitter.setSplitFactors(new double[] {0.33, 0.33, 0.34});
      splitter.run();
    }
    timings.put("Splitter 3-way (100x)", (System.nanoTime() - start) / 1_000_000);

    // 5. Benchmark Heater
    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Heater heater = new Heater("heater", sepFeed);
      heater.setOutTemperature(350.0, "K");
      heater.run();
    }
    timings.put("Heater (100x)", (System.nanoTime() - start) / 1_000_000);

    // 6. Benchmark Cooler
    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Cooler cooler = new Cooler("cooler", sepFeed);
      cooler.setOutTemperature(280.0, "K");
      cooler.run();
    }
    timings.put("Cooler (100x)", (System.nanoTime() - start) / 1_000_000);

    // 7. Benchmark ThrottlingValve
    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      ThrottlingValve valve = new ThrottlingValve("valve", sepFeed);
      valve.setOutletPressure(30.0, "bara");
      valve.run();
    }
    timings.put("ThrottlingValve (100x)", (System.nanoTime() - start) / 1_000_000);

    // 8. Benchmark Compressor
    Stream lowPStream = new Stream("lowP", feed.clone());
    lowPStream.setFlowRate(1000.0, "kg/hr");
    lowPStream.setPressure(10.0, "bara");
    lowPStream.run();

    start = System.nanoTime();
    for (int i = 0; i < 100; i++) {
      Compressor comp = new Compressor("comp", lowPStream);
      comp.setOutletPressure(30.0, "bara");
      comp.run();
    }
    timings.put("Compressor (100x)", (System.nanoTime() - start) / 1_000_000);

    // 9. Benchmark full ProcessSystem
    start = System.nanoTime();
    for (int i = 0; i < 20; i++) {
      ProcessSystem process = createTypicalProcess(feed.clone());
      process.run();
    }
    timings.put("Full ProcessSystem (20x)", (System.nanoTime() - start) / 1_000_000);

    // Print results sorted by time
    System.out.println("Operation                     | Time (ms)");
    System.out.println("------------------------------|----------");
    timings.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .forEach(e -> System.out.printf("%-30s| %d ms%n", e.getKey(), e.getValue()));

    System.out.println("\n=== Detailed ThrottlingValve Breakdown ===\n");
    benchmarkThrottlingValveDetailed(feed.clone());

    System.out.println("\n=== Detailed ProcessSystem Breakdown ===\n");
    benchmarkProcessSystemDetailed(feed.clone());
  }

  /**
   * Detailed benchmark of ThrottlingValve internals.
   */
  private void benchmarkThrottlingValveDetailed(SystemInterface feed) {
    Stream sepFeed = new Stream("sepFeed", feed);
    sepFeed.setFlowRate(1000.0, "kg/hr");
    sepFeed.run();

    int iterations = 100;

    // Time clone operation
    long cloneTime = 0;
    long initPropsTime = 0;
    long flashTime = 0;

    for (int i = 0; i < iterations; i++) {
      // Clone timing
      long start = System.nanoTime();
      SystemInterface cloned = sepFeed.getThermoSystem().clone();
      cloneTime += System.nanoTime() - start;

      // initProperties timing
      start = System.nanoTime();
      cloned.initProperties();
      initPropsTime += System.nanoTime() - start;

      // Flash timing
      cloned.setPressure(30.0, "bara");
      start = System.nanoTime();
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(cloned);
      double enthalpy = cloned.getEnthalpy();
      ops.PHflash(enthalpy, 0);
      flashTime += System.nanoTime() - start;
    }

    System.out.println("ThrottlingValve breakdown for " + iterations + " iterations:");
    System.out.printf("  Clone:          %d ms (avg %.2f ms)%n", cloneTime / 1_000_000,
        (cloneTime / 1_000_000.0) / iterations);
    System.out.printf("  initProperties: %d ms (avg %.2f ms)%n", initPropsTime / 1_000_000,
        (initPropsTime / 1_000_000.0) / iterations);
    System.out.printf("  PH Flash:       %d ms (avg %.2f ms)%n", flashTime / 1_000_000,
        (flashTime / 1_000_000.0) / iterations);
    System.out.printf("  Total:          %d ms%n",
        (cloneTime + initPropsTime + flashTime) / 1_000_000);
  }

  /**
   * Detailed benchmark of ProcessSystem internals.
   */
  private void benchmarkProcessSystemDetailed(SystemInterface feed) {
    // Time each phase of process system execution
    Stream feedStream = new Stream("feed", feed);
    feedStream.setFlowRate(1000.0, "kg/hr");

    long totalCloneTime = 0;
    long totalFlashTime = 0;

    int iterations = 50;

    for (int iter = 0; iter < iterations; iter++) {
      // Clone timing
      long start = System.nanoTime();
      SystemInterface cloned = feed.clone();
      totalCloneTime += System.nanoTime() - start;

      // Flash timing
      start = System.nanoTime();
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(cloned);
      ops.TPflash();
      totalFlashTime += System.nanoTime() - start;
    }

    System.out.println("Detailed timing for " + iterations + " iterations:");
    System.out.printf("  Clone fluid:     %d ms (avg %.2f ms each)%n", totalCloneTime / 1_000_000,
        (totalCloneTime / 1_000_000.0) / iterations);
    System.out.printf("  TP Flash:        %d ms (avg %.2f ms each)%n", totalFlashTime / 1_000_000,
        (totalFlashTime / 1_000_000.0) / iterations);

    // Test effect of component count
    System.out.println("\n=== Impact of Component Count on Flash Time ===\n");
    testComponentCountImpact();
  }

  /**
   * Test how component count affects performance.
   */
  private void testComponentCountImpact() {
    int[] componentCounts = {3, 5, 8, 10};
    String[] components = {"methane", "ethane", "propane", "n-butane", "n-pentane", "n-hexane",
        "n-heptane", "n-octane", "nitrogen", "CO2"};

    System.out.println("Components | Clone (ms) | Flash (ms) | Total (ms)");
    System.out.println("-----------|------------|------------|------------");

    for (int count : componentCounts) {
      SystemInterface sys = new SystemSrkEos(298.15, 50.0);
      for (int i = 0; i < count && i < components.length; i++) {
        sys.addComponent(components[i], 1.0 / count);
      }
      sys.setMixingRule("classic");

      int iterations = 100;
      long cloneTime = 0;
      long flashTime = 0;

      for (int iter = 0; iter < iterations; iter++) {
        long start = System.nanoTime();
        SystemInterface cloned = sys.clone();
        cloneTime += System.nanoTime() - start;

        start = System.nanoTime();
        neqsim.thermodynamicoperations.ThermodynamicOperations ops =
            new neqsim.thermodynamicoperations.ThermodynamicOperations(cloned);
        ops.TPflash();
        flashTime += System.nanoTime() - start;
      }

      System.out.printf("%10d | %10.1f | %10.1f | %10.1f%n", count, cloneTime / 1_000_000.0,
          flashTime / 1_000_000.0, (cloneTime + flashTime) / 1_000_000.0);
    }
  }

  /**
   * Create a typical process for benchmarking.
   */
  private ProcessSystem createTypicalProcess(SystemInterface feed) {
    ProcessSystem process = new ProcessSystem();

    Stream feedStream = new Stream("feed", feed);
    feedStream.setFlowRate(1000.0, "kg/hr");
    process.add(feedStream);

    Heater heater = new Heater("heater", feedStream);
    heater.setOutTemperature(350.0, "K");
    process.add(heater);

    Separator sep1 = new Separator("sep1", heater.getOutletStream());
    process.add(sep1);

    Compressor comp = new Compressor("comp", sep1.getGasOutStream());
    comp.setOutletPressure(80.0, "bara");
    process.add(comp);

    Cooler cooler = new Cooler("cooler", comp.getOutletStream());
    cooler.setOutTemperature(300.0, "K");
    process.add(cooler);

    Separator sep2 = new Separator("sep2", cooler.getOutletStream());
    process.add(sep2);

    ThrottlingValve valve = new ThrottlingValve("valve", sep1.getLiquidOutStream());
    valve.setOutletPressure(10.0, "bara");
    process.add(valve);

    return process;
  }

  /**
   * Profile ThermodynamicOperations to find internal bottlenecks.
   */
  @Test
  public void profileThermodynamicOperations() {
    System.out.println("\n=== ThermodynamicOperations Profiling ===\n");

    SystemInterface sys = new SystemSrkEos(298.15, 50.0);
    sys.addComponent("nitrogen", 0.02);
    sys.addComponent("CO2", 0.03);
    sys.addComponent("methane", 0.70);
    sys.addComponent("ethane", 0.10);
    sys.addComponent("propane", 0.08);
    sys.addComponent("n-butane", 0.04);
    sys.addComponent("n-pentane", 0.02);
    sys.addComponent("n-hexane", 0.01);
    sys.setMixingRule("classic");

    int iterations = 200;

    // Warmup
    for (int i = 0; i < 20; i++) {
      SystemInterface warmup = sys.clone();
      new neqsim.thermodynamicoperations.ThermodynamicOperations(warmup).TPflash();
    }

    // Profile different flash types
    long tpFlashTime = 0;
    long phFlashTime = 0;
    long psFlashTime = 0;

    for (int i = 0; i < iterations; i++) {
      SystemInterface test = sys.clone();
      long start = System.nanoTime();
      new neqsim.thermodynamicoperations.ThermodynamicOperations(test).TPflash();
      tpFlashTime += System.nanoTime() - start;
    }

    for (int i = 0; i < iterations; i++) {
      SystemInterface test = sys.clone();
      test.init(0);
      test.init(1);
      double H = test.getEnthalpy();
      double P = test.getPressure();
      long start = System.nanoTime();
      new neqsim.thermodynamicoperations.ThermodynamicOperations(test).PHflash(H);
      phFlashTime += System.nanoTime() - start;
    }

    for (int i = 0; i < iterations; i++) {
      SystemInterface test = sys.clone();
      test.init(0);
      test.init(1);
      double S = test.getEntropy();
      long start = System.nanoTime();
      new neqsim.thermodynamicoperations.ThermodynamicOperations(test).PSflash(S);
      psFlashTime += System.nanoTime() - start;
    }

    System.out.printf("TP Flash (%d iterations): %d ms (avg %.2f ms)%n", iterations,
        tpFlashTime / 1_000_000, (tpFlashTime / 1_000_000.0) / iterations);
    System.out.printf("PH Flash (%d iterations): %d ms (avg %.2f ms)%n", iterations,
        phFlashTime / 1_000_000, (phFlashTime / 1_000_000.0) / iterations);
    System.out.printf("PS Flash (%d iterations): %d ms (avg %.2f ms)%n", iterations,
        psFlashTime / 1_000_000, (psFlashTime / 1_000_000.0) / iterations);
  }
}
