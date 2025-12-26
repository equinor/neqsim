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
    Thread.sleep(20);
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

  @Test
  void testNewCompletionService() throws Exception {
    java.util.concurrent.CompletionService<Integer> cs = NeqSimThreadPool.newCompletionService();
    assertNotNull(cs);

    // Submit 5 tasks
    for (int i = 1; i <= 5; i++) {
      final int value = i;
      cs.submit(() -> value * 10);
    }

    // Collect results as they complete
    List<Integer> results = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Future<Integer> completed = cs.take(); // Blocks until next result available
      results.add(completed.get());
    }

    // Verify all results received (order may vary due to parallelism)
    assertEquals(5, results.size());
    assertTrue(results.contains(10));
    assertTrue(results.contains(20));
    assertTrue(results.contains(30));
    assertTrue(results.contains(40));
    assertTrue(results.contains(50));
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
    final int numProcesses = 6;
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
      future.get(30, TimeUnit.SECONDS); // 30 second timeout per process
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
    final int numProcesses = 4;

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
      future.get(30, TimeUnit.SECONDS);
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

  /**
   * Test reporting results in completion order using CompletionService. This is useful when you
   * want to process results as soon as they become available, rather than waiting for all to
   * complete.
   */
  @Test
  void testReportResultsInCompletionOrder() throws Exception {
    final int numProcesses = 4;

    // Create processes with varying complexity (different pressures affect computation time)
    List<ProcessSystem> processes = new ArrayList<>();
    for (int i = 0; i < numProcesses; i++) {
      // Reverse order so higher IDs have lower pressure (may complete in different order)
      double pressure = 50.0 - i * 3.0;
      ProcessSystem process = createSimpleProcess(i, pressure);
      processes.add(process);
    }

    // Use the built-in newCompletionService() method
    java.util.concurrent.CompletionService<Integer> completionService =
        NeqSimThreadPool.newCompletionService();

    // Submit all processes, returning their index when done
    for (int i = 0; i < processes.size(); i++) {
      final int index = i;
      final ProcessSystem process = processes.get(i);
      completionService.submit(() -> {
        process.run();
        return index;
      });
    }

    System.out.println("\nResults in completion order:");
    List<Integer> completionOrder = new ArrayList<>();

    // Collect results as they complete
    for (int i = 0; i < numProcesses; i++) {
      // take() blocks until the next result is available
      Future<Integer> completedFuture = completionService.take();
      int completedIndex = completedFuture.get();
      completionOrder.add(completedIndex);

      ProcessSystem process = processes.get(completedIndex);
      Separator sep = (Separator) process.getUnit("Separator-" + completedIndex);
      double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");

      System.out.printf("  Process %d completed: gas flow = %.2f kg/hr%n", completedIndex, gasFlow);
    }

    // Verify all processes completed
    assertEquals(numProcesses, completionOrder.size());
    for (ProcessSystem process : processes) {
      assertTrue(process.solved());
    }

    System.out.println("Completion order: " + completionOrder);
  }

  /**
   * Test polling futures to report results as they complete without blocking. This approach allows
   * you to do other work while waiting for results.
   */
  @Test
  void testPollFuturesForCompletion() throws Exception {
    final int numProcesses = 4;

    // Create processes
    List<ProcessSystem> processes = new ArrayList<>();
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < numProcesses; i++) {
      ProcessSystem process = createSimpleProcess(i, 30.0 + i * 5);
      processes.add(process);
      futures.add(process.runAsTask());
    }

    // Track which have been reported
    boolean[] reported = new boolean[numProcesses];
    int completedCount = 0;

    System.out.println("\nPolling for completed processes:");

    // Poll until all complete
    while (completedCount < numProcesses) {
      for (int i = 0; i < numProcesses; i++) {
        if (!reported[i] && futures.get(i).isDone()) {
          // This one just completed - report it
          ProcessSystem process = processes.get(i);
          Separator sep = (Separator) process.getUnit("Separator-" + i);
          double gasFlow = sep.getGasOutStream().getFlowRate("kg/hr");

          System.out.printf("  Process %d done: gas flow = %.2f kg/hr%n", i, gasFlow);

          reported[i] = true;
          completedCount++;
        }
      }

      // Could do other work here while waiting
      if (completedCount < numProcesses) {
        Thread.sleep(1); // Small sleep to avoid busy-waiting
      }
    }

    // Verify all completed successfully
    for (int i = 0; i < numProcesses; i++) {
      assertTrue(processes.get(i).solved(), "Process " + i + " should be solved");
    }
  }

  /**
   * Test that bounded queue mode works correctly.
   */
  @Test
  void testBoundedQueueMode() throws Exception {
    // Set a bounded queue
    NeqSimThreadPool.setMaxQueueCapacity(100);
    assertEquals(100, NeqSimThreadPool.getMaxQueueCapacity());

    // Submit some tasks - should work fine within capacity
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      futures.add(NeqSimThreadPool.submit(() -> {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
    }

    // Wait for all to complete
    for (Future<?> future : futures) {
      future.get(5, TimeUnit.SECONDS);
    }

    // Reset to unbounded
    NeqSimThreadPool.setMaxQueueCapacity(0);
    assertEquals(0, NeqSimThreadPool.getMaxQueueCapacity());
  }

  /**
   * Test that exceptions in tasks are logged (via UncaughtExceptionHandler for direct Thread
   * exceptions) and captured in Future for submitted tasks.
   */
  @Test
  void testExceptionHandling() throws Exception {
    // Submit a task that throws an exception
    Future<?> future = NeqSimThreadPool.submit(() -> {
      throw new RuntimeException("Test exception for logging");
    });

    // The exception should be captured in the Future
    Exception exception = assertThrows(java.util.concurrent.ExecutionException.class, () -> {
      future.get(5, TimeUnit.SECONDS);
    });

    assertTrue(exception.getCause() instanceof RuntimeException);
    assertEquals("Test exception for logging", exception.getCause().getMessage());
  }

  /**
   * Test core thread timeout configuration.
   */
  @Test
  void testCoreThreadTimeout() throws Exception {
    // Default should be disabled with 600 seconds (10 minutes) keep-alive
    assertFalse(NeqSimThreadPool.isAllowCoreThreadTimeout());
    assertEquals(600, NeqSimThreadPool.getKeepAliveTimeSeconds());

    // Enable core thread timeout
    NeqSimThreadPool.setAllowCoreThreadTimeout(true);
    assertTrue(NeqSimThreadPool.isAllowCoreThreadTimeout());

    // Change keep-alive time
    NeqSimThreadPool.setKeepAliveTimeSeconds(30);
    assertEquals(30, NeqSimThreadPool.getKeepAliveTimeSeconds());

    // Verify pool still works
    Future<?> future = NeqSimThreadPool.submit(() -> {
      // Simple task
    });
    future.get(5, TimeUnit.SECONDS);

    // Reset to defaults
    NeqSimThreadPool.setAllowCoreThreadTimeout(false);
    NeqSimThreadPool.setKeepAliveTimeSeconds(600);
    assertFalse(NeqSimThreadPool.isAllowCoreThreadTimeout());
  }

  @Test
  void testKeepAliveTimeValidation() {
    assertThrows(IllegalArgumentException.class, () -> NeqSimThreadPool.setKeepAliveTimeSeconds(0));
    assertThrows(IllegalArgumentException.class,
        () -> NeqSimThreadPool.setKeepAliveTimeSeconds(-1));
  }

  /**
   * Benchmark comparing old runAsThread() vs new runAsTask() approach.
   * 
   * This test demonstrates the performance difference between: - runAsThread(): Creates a new
   * Thread for each process (unmanaged) - runAsTask(): Uses the managed thread pool (reuses
   * threads)
   */
  @Test
  @SuppressWarnings("deprecation")
  void testRunAsThreadVsRunAsTask() throws Exception {
    final int numProcesses = 20;
    final int numRuns = 3; // Run multiple times to warm up and get stable results

    System.out.println("\n=== Benchmark: runAsThread() vs runAsTask() ===");
    System.out.println(
        "Running " + numProcesses + " process simulations, " + numRuns + " iterations each\n");

    long totalThreadTime = 0;
    long totalTaskTime = 0;

    for (int run = 0; run < numRuns; run++) {
      // --- Test OLD WAY: runAsThread() ---
      List<ProcessSystem> threadProcesses = new ArrayList<>();
      List<Thread> threads = new ArrayList<>();
      for (int i = 0; i < numProcesses; i++) {
        threadProcesses.add(createSimpleProcess(i, 25.0 + i));
      }

      long threadStart = System.nanoTime();
      for (ProcessSystem process : threadProcesses) {
        Thread t = process.runAsThread(); // Deprecated - creates new thread each time
        threads.add(t);
      }
      for (Thread t : threads) {
        t.join(); // Wait for all threads
      }
      long threadDuration = (System.nanoTime() - threadStart) / 1_000_000; // Convert to ms
      totalThreadTime += threadDuration;

      // Verify all completed
      for (ProcessSystem process : threadProcesses) {
        assertTrue(process.solved(), "Thread-based process should be solved");
      }

      // --- Test NEW WAY: runAsTask() ---
      List<ProcessSystem> taskProcesses = new ArrayList<>();
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < numProcesses; i++) {
        taskProcesses.add(createSimpleProcess(i + 1000, 25.0 + i));
      }

      long taskStart = System.nanoTime();
      for (ProcessSystem process : taskProcesses) {
        Future<?> future = process.runAsTask(); // Uses thread pool
        futures.add(future);
      }
      for (Future<?> future : futures) {
        future.get(60, TimeUnit.SECONDS);
      }
      long taskDuration = (System.nanoTime() - taskStart) / 1_000_000; // Convert to ms
      totalTaskTime += taskDuration;

      // Verify all completed
      for (ProcessSystem process : taskProcesses) {
        assertTrue(process.solved(), "Task-based process should be solved");
      }

      System.out.println("Run " + (run + 1) + ": runAsThread=" + threadDuration + "ms, runAsTask="
          + taskDuration + "ms");
    }

    // Calculate averages
    double avgThreadTime = (double) totalThreadTime / numRuns;
    double avgTaskTime = (double) totalTaskTime / numRuns;
    double improvement = ((avgThreadTime - avgTaskTime) / avgThreadTime) * 100;

    System.out.println("\n--- Results ---");
    System.out
        .println("Average runAsThread() time: " + String.format("%.1f", avgThreadTime) + " ms");
    System.out.println("Average runAsTask() time:   " + String.format("%.1f", avgTaskTime) + " ms");
    System.out.println("Pool size: " + NeqSimThreadPool.getPoolSize() + " threads");

    if (avgTaskTime < avgThreadTime) {
      System.out.println("runAsTask() is " + String.format("%.1f", improvement) + "% faster");
    } else if (avgTaskTime > avgThreadTime) {
      System.out.println("runAsThread() is " + String.format("%.1f", -improvement) + "% faster");
    } else {
      System.out.println("Both methods performed equally");
    }

    System.out.println("\nNote: The main benefit of runAsTask() is resource management,");
    System.out.println("      not raw speed. It prevents thread explosion and provides");
    System.out.println("      Future API for cancellation, timeout, and exception handling.");
  }
}
