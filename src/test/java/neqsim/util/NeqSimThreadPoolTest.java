package neqsim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the NeqSimThreadPool utility class.
 */
class NeqSimThreadPoolTest {

  @AfterEach
  void tearDown() {
    // Reset pool to default state after each test
    NeqSimThreadPool.resetPoolSize();
  }

  @Test
  void testGetPool() {
    assertNotNull(NeqSimThreadPool.getPool());
    assertFalse(NeqSimThreadPool.isShutdown());
  }

  @Test
  void testSubmitRunnable() throws Exception {
    AtomicBoolean executed = new AtomicBoolean(false);

    Future<?> future = NeqSimThreadPool.submit(() -> {
      executed.set(true);
    });

    assertNotNull(future);
    future.get(5, TimeUnit.SECONDS);
    assertTrue(executed.get());
  }

  @Test
  void testExecuteRunnable() throws Exception {
    AtomicBoolean executed = new AtomicBoolean(false);

    NeqSimThreadPool.execute(() -> {
      executed.set(true);
    });

    // Give it some time to execute
    Thread.sleep(100);
    assertTrue(executed.get());
  }

  @Test
  void testGetPoolSize() {
    assertEquals(Runtime.getRuntime().availableProcessors(), NeqSimThreadPool.getDefaultPoolSize());
  }

  @Test
  void testSetPoolSizeInvalid() {
    assertThrows(IllegalArgumentException.class, () -> NeqSimThreadPool.setPoolSize(0));
    assertThrows(IllegalArgumentException.class, () -> NeqSimThreadPool.setPoolSize(-1));
  }

  @Test
  void testSetPoolSize() {
    int newSize = 2;
    NeqSimThreadPool.setPoolSize(newSize);
    assertEquals(newSize, NeqSimThreadPool.getPoolSize());
  }

  @Test
  void testSubmitCallable() throws Exception {
    Callable<Integer> task = () -> 42;
    Future<Integer> future = NeqSimThreadPool.submit(task);

    assertNotNull(future);
    assertEquals(42, future.get(5, TimeUnit.SECONDS));
  }

  /**
   * Creates a process system with a feed stream, valve, and separator. Each process is independent
   * with its own fluid system.
   *
   * @param id unique identifier for naming units
   * @param feedPressure inlet pressure in bara
   * @return configured ProcessSystem ready to run
   */
  private ProcessSystem createSimpleProcess(int id, double feedPressure) {
    // Create a fresh fluid system for each process
    SystemInterface fluid = new SystemSrkEos(298.15, feedPressure);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");

    ProcessSystem process = new ProcessSystem();
    process.setName("Process-" + id);

    // Feed stream
    Stream feed = new Stream("Feed-" + id, fluid);
    feed.setFlowRate(100.0 + id, "kg/hr"); // Slightly different flow rates
    feed.setTemperature(25.0 + id * 0.5, "C"); // Slightly different temperatures
    feed.setPressure(feedPressure, "bara");

    // Throttling valve to reduce pressure
    ThrottlingValve valve = new ThrottlingValve("Valve-" + id, feed);
    valve.setOutletPressure(5.0);

    // Separator after valve
    Separator separator = new Separator("Separator-" + id, valve.getOutletStream());

    process.add(feed);
    process.add(valve);
    process.add(separator);

    return process;
  }

  /**
   * Test running 20 real process simulations concurrently using the thread pool. Each process has a
   * feed stream, valve, and separator with slightly different conditions.
   */
  @Test
  void testRun20ConcurrentProcessSimulations() throws Exception {
    final int numProcesses = 20;
    List<ProcessSystem> processes = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    // Create 20 independent process systems with varying conditions
    for (int i = 0; i < numProcesses; i++) {
      double pressure = 20.0 + i * 2.0; // Pressures from 20 to 58 bara
      ProcessSystem process = createSimpleProcess(i, pressure);
      processes.add(process);
    }

    long startTime = System.currentTimeMillis();

    // Submit all processes to the thread pool
    for (ProcessSystem process : processes) {
      Future<?> future = process.runAsTask();
      futures.add(future);
    }

    // Wait for all to complete (with timeout)
    for (int i = 0; i < futures.size(); i++) {
      Future<?> future = futures.get(i);
      future.get(60, TimeUnit.SECONDS); // 60 second timeout per process
    }

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    // Verify all processes completed successfully
    for (int i = 0; i < numProcesses; i++) {
      ProcessSystem process = processes.get(i);
      assertTrue(process.solved(), "Process " + i + " should be solved");

      // Verify the separator has valid output
      Separator sep = (Separator) process.getUnit("Separator-" + i);
      assertNotNull(sep, "Separator-" + i + " should exist");
      assertTrue(sep.getGasOutStream().getFlowRate("kg/hr") > 0,
          "Separator-" + i + " gas flow should be > 0");
    }

    System.out.println("Successfully ran " + numProcesses + " concurrent process simulations in "
        + duration + " ms");
    System.out.println(
        "Average time per process: " + (duration / numProcesses) + " ms (with parallelism)");
  }

  /**
   * Test comparing sequential vs parallel execution of process simulations.
   */
  @Test
  void testParallelVsSequentialPerformance() throws Exception {
    final int numProcesses = 10;

    // Sequential execution
    List<ProcessSystem> sequentialProcesses = new ArrayList<>();
    for (int i = 0; i < numProcesses; i++) {
      sequentialProcesses.add(createSimpleProcess(i, 30.0 + i));
    }

    long seqStart = System.currentTimeMillis();
    for (ProcessSystem process : sequentialProcesses) {
      process.run();
    }
    long seqDuration = System.currentTimeMillis() - seqStart;

    // Parallel execution
    List<ProcessSystem> parallelProcesses = new ArrayList<>();
    for (int i = 0; i < numProcesses; i++) {
      parallelProcesses.add(createSimpleProcess(i + 100, 30.0 + i));
    }

    long parStart = System.currentTimeMillis();
    List<Future<?>> futures = new ArrayList<>();
    for (ProcessSystem process : parallelProcesses) {
      futures.add(process.runAsTask());
    }
    for (Future<?> future : futures) {
      future.get(60, TimeUnit.SECONDS);
    }
    long parDuration = System.currentTimeMillis() - parStart;

    // Verify all parallel processes completed
    for (ProcessSystem process : parallelProcesses) {
      assertTrue(process.solved());
    }

    System.out.println("Sequential execution of " + numProcesses + " processes: " + seqDuration
        + " ms (" + (seqDuration / numProcesses) + " ms/process)");
    System.out.println("Parallel execution of " + numProcesses + " processes: " + parDuration
        + " ms (using " + NeqSimThreadPool.getPoolSize() + " threads)");
    System.out
        .println("Speedup factor: " + String.format("%.2f", (double) seqDuration / parDuration));
  }
}
