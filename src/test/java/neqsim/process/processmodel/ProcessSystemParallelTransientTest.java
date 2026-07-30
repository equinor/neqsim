package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.measurementdevice.MeasurementDeviceBaseClass;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for parallel transient execution in {@link ProcessSystem}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessSystemParallelTransientTest extends neqsim.NeqSimTest {
  /**
   * Minimal transient unit that records the worker thread used for each step.
   */
  private static final class ThreadRecordingUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final Set<String> workerNames;
    private final AtomicInteger executionCount;

    /**
     * Creates a recording unit.
     *
     * @param name unit name
     * @param workerNames shared set receiving worker-thread names
     * @param executionCount shared execution counter
     */
    private ThreadRecordingUnit(String name, Set<String> workerNames, AtomicInteger executionCount) {
      super(name);
      this.workerNames = workerNames;
      this.executionCount = executionCount;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      workerNames.add(Thread.currentThread().getName());
      executionCount.incrementAndGet();
      increaseTime(dt);
      setCalculationIdentifier(id);
    }
  }

  /**
   * Transient unit that blocks until released so the process runner can be interrupted while waiting on its future.
   */
  private static final class BlockingTransientUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final transient CountDownLatch started;
    private final transient CountDownLatch release;

    /**
     * Creates a blocking unit.
     *
     * @param started latch signalled when execution starts
     * @param release latch controlling when execution may finish
     */
    private BlockingTransientUnit(CountDownLatch started, CountDownLatch release) {
      this("blocking-unit", started, release);
    }

    /**
     * Creates a named blocking unit.
     *
     * @param name unit name
     * @param started latch signalled when execution starts
     * @param release latch controlling when execution may finish
     */
    private BlockingTransientUnit(String name, CountDownLatch started, CountDownLatch release) {
      super(name);
      this.started = started;
      this.release = release;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      setCalculationIdentifier(id);
    }
  }

  /**
   * Transient unit that detects overlapping invocations while the first invocation is blocked.
   */
  private static final class ReentrantBlockingTransientUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final transient CountDownLatch firstStarted;
    private final transient CountDownLatch secondStarted;
    private final transient CountDownLatch release;
    private final AtomicInteger executionCount = new AtomicInteger();

    /**
     * Creates a blocking unit that reports its first and second invocations.
     *
     * @param firstStarted latch signalled when the first invocation starts
     * @param secondStarted latch signalled if a second invocation starts before release
     * @param release latch controlling when invocations may finish
     */
    private ReentrantBlockingTransientUnit(CountDownLatch firstStarted, CountDownLatch secondStarted,
        CountDownLatch release) {
      super("reentrant-blocking-unit");
      this.firstStarted = firstStarted;
      this.secondStarted = secondStarted;
      this.release = release;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      if (executionCount.incrementAndGet() == 1) {
        firstStarted.countDown();
      } else {
        secondStarted.countDown();
      }
      try {
        release.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      setCalculationIdentifier(id);
    }
  }

  /**
   * Transient unit that signals when execution starts.
   */
  private static final class SignallingTransientUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;
    private final transient CountDownLatch started;
    private final AtomicInteger executionCount;

    /**
     * Creates a signalling unit.
     *
     * @param started latch signalled when execution starts
     * @param executionCount shared execution counter
     */
    private SignallingTransientUnit(CountDownLatch started, AtomicInteger executionCount) {
      super("signalling-unit");
      this.started = started;
      this.executionCount = executionCount;
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      executionCount.incrementAndGet();
      started.countDown();
      setCalculationIdentifier(id);
    }
  }

  /**
   * Direct executor that interrupts the submitting thread after completing its first task. This creates a deterministic
   * interruption at the barrier between two dependency levels.
   */
  private static final class InterruptAfterFirstTaskExecutor extends AbstractExecutorService {
    private final AtomicBoolean interruptAfterNextTask = new AtomicBoolean(true);
    private volatile boolean shutdown;

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
      shutdown = true;
    }

    /** {@inheritDoc} */
    @Override
    public java.util.List<Runnable> shutdownNow() {
      shutdown = true;
      return Collections.emptyList();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTerminated() {
      return shutdown;
    }

    /** {@inheritDoc} */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return shutdown;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(Runnable command) {
      if (shutdown) {
        throw new RejectedExecutionException("executor is shut down");
      }
      command.run();
      if (interruptAfterNextTask.compareAndSet(true, false)) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Two-port unit that blocks while representing an upstream dynamic calculation.
   */
  private static final class BlockingHeater extends Heater {
    private static final long serialVersionUID = 1000L;
    private final transient CountDownLatch started;
    private final transient CountDownLatch release;
    private final AtomicBoolean completed;

    /**
     * Creates a blocking upstream heater.
     *
     * @param name unit name
     * @param inletStream inlet stream
     * @param started latch signalled when execution starts
     * @param release latch controlling when execution may finish
     * @param completed flag set after the upstream state update completes
     */
    private BlockingHeater(String name, StreamInterface inletStream, CountDownLatch started, CountDownLatch release,
        AtomicBoolean completed) {
      super(name, inletStream);
      this.started = started;
      this.release = release;
      this.completed = completed;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      completed.set(true);
      setCalculationIdentifier(id);
    }
  }

  /**
   * Downstream two-port unit that records whether it started before its upstream dependency completed.
   */
  private static final class DependencyRecordingHeater extends Heater {
    private static final long serialVersionUID = 1000L;
    private final AtomicBoolean upstreamCompleted;
    private final AtomicBoolean observedIncompleteUpstream;
    private final transient CountDownLatch started;

    /**
     * Creates a downstream dependency observer.
     *
     * @param name unit name
     * @param inletStream upstream outlet stream
     * @param upstreamCompleted upstream completion flag
     * @param observedIncompleteUpstream flag set if this unit starts too early
     * @param started latch signalled when this unit starts
     */
    private DependencyRecordingHeater(String name, StreamInterface inletStream, AtomicBoolean upstreamCompleted,
        AtomicBoolean observedIncompleteUpstream, CountDownLatch started) {
      super(name, inletStream);
      this.upstreamCompleted = upstreamCompleted;
      this.observedIncompleteUpstream = observedIncompleteUpstream;
      this.started = started;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double dt, UUID id) {
      if (!upstreamCompleted.get()) {
        observedIncompleteUpstream.set(true);
      }
      started.countDown();
      setCalculationIdentifier(id);
    }
  }

  /**
   * Standalone controller that records transient scan execution.
   */
  private static final class CountingController extends ControllerDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private final AtomicInteger executionCount;

    /**
     * Creates a counting controller.
     *
     * @param executionCount shared execution counter
     */
    private CountingController(AtomicInteger executionCount) {
      super("counting-controller");
      this.executionCount = executionCount;
    }

    /** {@inheritDoc} */
    @Override
    public void runTransient(double initResponse, double dt, UUID id) {
      executionCount.incrementAndGet();
    }
  }

  /**
   * Measurement device that records each value scan.
   */
  private static final class CountingMeasurement extends MeasurementDeviceBaseClass {
    private static final long serialVersionUID = 1000L;
    private final AtomicInteger executionCount;

    /**
     * Creates a counting measurement.
     *
     * @param executionCount shared execution counter
     */
    private CountingMeasurement(AtomicInteger executionCount) {
      super("counting-measurement", "-");
      this.executionCount = executionCount;
    }

    /** {@inheritDoc} */
    @Override
    public double getMeasuredValue(String unit) {
      executionCount.incrementAndGet();
      return 0.0;
    }
  }

  /**
   * Repeated transient steps must reuse the configured bounded worker set instead of creating a new executor for every
   * step.
   */
  @Test
  public void reusesConfiguredWorkersAcrossTransientSteps() {
    int numberOfUnits = 8;
    int numberOfSteps = 20;
    int numberOfWorkers = 2;
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger executionCount = new AtomicInteger();

    ProcessSystem process = new ProcessSystem();
    for (int i = 0; i < numberOfUnits; i++) {
      process.add(new ThreadRecordingUnit("unit-" + i, workerNames, executionCount));
    }
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(numberOfWorkers);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < numberOfSteps; i++) {
      process.runTransient(1.0, id);
    }

    assertEquals(numberOfUnits * numberOfSteps, executionCount.get());
    assertTrue(workerNames.size() <= numberOfWorkers,
        "Expected at most " + numberOfWorkers + " reused workers, but observed " + workerNames);
    assertEquals(numberOfSteps, process.getTime(), 1.0e-12);
  }

  /**
   * Connected equipment must respect stream-dependency order even when transient parallelism is enabled.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void connectedEquipmentWaitsForUpstreamTransientCompletion() throws Exception {
    CountDownLatch upstreamStarted = new CountDownLatch(1);
    CountDownLatch releaseUpstream = new CountDownLatch(1);
    CountDownLatch downstreamStarted = new CountDownLatch(1);
    AtomicBoolean upstreamCompleted = new AtomicBoolean();
    AtomicBoolean downstreamObservedIncompleteUpstream = new AtomicBoolean();

    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("feed", fluid);
    BlockingHeater upstream = new BlockingHeater("upstream", feed, upstreamStarted, releaseUpstream, upstreamCompleted);
    DependencyRecordingHeater downstream = new DependencyRecordingHeater("downstream", upstream.getOutletStream(),
        upstreamCompleted, downstreamObservedIncompleteUpstream, downstreamStarted);

    ProcessSystem process = new ProcessSystem();
    process.add(upstream);
    process.add(downstream);
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
      }
    });
    processRunner.setDaemon(true);
    processRunner.setName("NeqSim-Test-Connected-Transient");

    try {
      processRunner.start();
      assertTrue(upstreamStarted.await(5L, TimeUnit.SECONDS), "Upstream equipment did not start");
      downstreamStarted.await(500L, TimeUnit.MILLISECONDS);
      releaseUpstream.countDown();
      processRunner.join(5000L);

      assertFalse(processRunner.isAlive(), "Transient process did not finish after releasing upstream equipment");
      assertTrue(downstreamStarted.getCount() == 0L, "Downstream equipment did not execute");
      assertFalse(downstreamObservedIncompleteUpstream.get(),
          "Downstream equipment started before its stream-producing dependency completed");
    } finally {
      releaseUpstream.countDown();
      processRunner.join(2000L);
    }
  }

  /**
   * Units in the same dependency level must retain parallel execution after introducing level barriers.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void independentEquipmentWithinLevelStillRunsConcurrently() throws Exception {
    CountDownLatch bothStarted = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    ProcessSystem process = new ProcessSystem();
    process.add(new BlockingTransientUnit("independent-a", bothStarted, release));
    process.add(new BlockingTransientUnit("independent-b", bothStarted, release));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
      }
    });
    processRunner.setDaemon(true);
    processRunner.setName("NeqSim-Test-Independent-Transient");

    try {
      processRunner.start();
      assertTrue(bothStarted.await(5L, TimeUnit.SECONDS),
          "Independent equipment in the same dependency level did not execute concurrently");
    } finally {
      release.countDown();
      processRunner.join(5000L);
    }
    assertFalse(processRunner.isAlive(), "Parallel transient process did not finish after releasing both units");
  }

  /**
   * An interrupt observed after one dependency level completes must prevent submission of downstream equipment.
   *
   * @throws Exception if the deterministic executor cannot be installed
   */
  @Test
  public void interruptBetweenDependencyLevelsDoesNotSubmitDownstreamEquipment() throws Exception {
    CountDownLatch upstreamStarted = new CountDownLatch(1);
    CountDownLatch releaseUpstream = new CountDownLatch(0);
    CountDownLatch downstreamStarted = new CountDownLatch(1);
    AtomicBoolean upstreamCompleted = new AtomicBoolean();
    AtomicBoolean downstreamObservedIncompleteUpstream = new AtomicBoolean();

    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    Stream feed = new Stream("feed", fluid);
    BlockingHeater upstream = new BlockingHeater("upstream", feed, upstreamStarted, releaseUpstream, upstreamCompleted);
    DependencyRecordingHeater downstream = new DependencyRecordingHeater("downstream", upstream.getOutletStream(),
        upstreamCompleted, downstreamObservedIncompleteUpstream, downstreamStarted);

    ProcessSystem process = new ProcessSystem();
    process.add(upstream);
    process.add(downstream);
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(1);
    setParallelTransientExecutor(process, new InterruptAfterFirstTaskExecutor(), 1);

    try {
      process.runTransient(1.0, UUID.randomUUID());
      assertTrue(Thread.currentThread().isInterrupted(), "Boundary interrupt status was not preserved");
      assertEquals(1L, downstreamStarted.getCount(), "Downstream equipment was submitted after a boundary interrupt");
    } finally {
      Thread.interrupted();
    }
  }

  /**
   * Runtime worker pools must not participate in process serialization or copying.
   */
  @Test
  public void parallelTransientExecutorIsExcludedFromCopies() throws Exception {
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger executionCount = new AtomicInteger();
    ProcessSystem process = new ProcessSystem();
    process.add(new ThreadRecordingUnit("unit", workerNames, executionCount));
    process.add(new ThreadRecordingUnit("unit-2", workerNames, executionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);
    process.runTransient(1.0, UUID.randomUUID());
    ExecutorService originalExecutor = getParallelTransientExecutor(process);
    assertNotNull(originalExecutor, "Original process must create its configured transient executor");

    ProcessSystem copiedProcess = process.copy();
    assertNull(getParallelTransientExecutor(copiedProcess),
        "A copied process must not contain the original executor runtime resource");
    copiedProcess.runTransient(1.0, UUID.randomUUID());
    ExecutorService copiedExecutor = getParallelTransientExecutor(copiedProcess);
    assertNotNull(copiedExecutor, "Copied process must lazily create a replacement executor");

    assertEquals(1.0, process.getTime(), 1.0e-12);
    assertEquals(2.0, copiedProcess.getTime(), 1.0e-12);
    assertNotSame(originalExecutor, copiedExecutor, "The copied process must create its own transient executor");
  }

  /**
   * Interrupting the caller while it waits for parallel transient equipment must preserve the caller's interrupt flag
   * and allow it to return promptly.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void preservesCallerInterruptStatusWhileWaitingForEquipment() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger executionCount = new AtomicInteger();
    AtomicBoolean interruptStatusAfterRun = new AtomicBoolean();
    ProcessSystem process = new ProcessSystem();
    process.add(new BlockingTransientUnit(started, release));
    process.add(new ThreadRecordingUnit("recording-unit", workerNames, executionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
        interruptStatusAfterRun.set(Thread.currentThread().isInterrupted());
      }
    });

    try {
      processRunner.start();
      assertTrue(started.await(5L, TimeUnit.SECONDS), "Blocking unit did not start");
      processRunner.interrupt();
      processRunner.join(2000L);
      assertFalse(processRunner.isAlive(), "Interrupted process runner did not return promptly");
      assertTrue(interruptStatusAfterRun.get(), "Parallel transient execution cleared the caller's interrupt status");
    } finally {
      release.countDown();
      processRunner.join(2000L);
    }
  }

  /**
   * An interrupted semi-implicit step must not submit its second parallel equipment pass while the first pass can still
   * be mutating equipment state.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void interruptedSemiImplicitStepDoesNotSubmitSecondParallelPass() throws Exception {
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger executionCount = new AtomicInteger();
    ProcessSystem process = new ProcessSystem();
    process.add(new ReentrantBlockingTransientUnit(firstStarted, secondStarted, release));
    process.add(new ThreadRecordingUnit("recording-unit", workerNames, executionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(4);
    process.setIntegrationMethod(ProcessSystem.IntegrationMethod.SEMI_IMPLICIT);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
      }
    });

    try {
      processRunner.start();
      assertTrue(firstStarted.await(5L, TimeUnit.SECONDS), "First semi-implicit equipment pass did not start");
      processRunner.interrupt();
      assertFalse(secondStarted.await(500L, TimeUnit.MILLISECONDS),
          "Interrupted semi-implicit step submitted a second parallel equipment pass");
    } finally {
      release.countDown();
      processRunner.join(2000L);
    }
  }

  /**
   * Interrupting a parallel transient wait must cancel queued equipment work without interrupting a unit already
   * updating its state.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void interruptCancelsQueuedEquipmentWithoutInterruptingRunningTask() throws Exception {
    CountDownLatch blockingStarted = new CountDownLatch(1);
    CountDownLatch releaseBlocking = new CountDownLatch(1);
    CountDownLatch queuedStarted = new CountDownLatch(1);
    AtomicInteger queuedExecutionCount = new AtomicInteger();
    ProcessSystem process = new ProcessSystem();
    process.add(new BlockingTransientUnit(blockingStarted, releaseBlocking));
    process.add(new SignallingTransientUnit(queuedStarted, queuedExecutionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(1);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
      }
    });

    try {
      processRunner.start();
      assertTrue(blockingStarted.await(5L, TimeUnit.SECONDS), "Blocking equipment did not start");
      processRunner.interrupt();
      processRunner.join(2000L);
      assertFalse(processRunner.isAlive(), "Interrupted process runner did not return promptly");
      assertEquals(0, queuedExecutionCount.get(), "Queued equipment ran before the blocking unit was released");

      releaseBlocking.countDown();
      assertFalse(queuedStarted.await(500L, TimeUnit.MILLISECONDS),
          "Queued equipment continued executing after runTransient returned");
      assertEquals(0, queuedExecutionCount.get(), "Queued equipment mutated state after runTransient returned");
    } finally {
      releaseBlocking.countDown();
      processRunner.join(2000L);
    }
  }

  /**
   * An interrupted parallel equipment pass must abort controller and measurement phases that could otherwise race with
   * equipment still updating state.
   *
   * @throws Exception if the test thread cannot coordinate with the process runner
   */
  @Test
  public void interruptAbortsRemainingTransientStepPhases() throws Exception {
    CountDownLatch blockingStarted = new CountDownLatch(1);
    CountDownLatch releaseBlocking = new CountDownLatch(1);
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger equipmentExecutionCount = new AtomicInteger();
    AtomicInteger controllerExecutionCount = new AtomicInteger();
    AtomicInteger measurementExecutionCount = new AtomicInteger();
    ProcessSystem process = new ProcessSystem();
    process.add(new BlockingTransientUnit(blockingStarted, releaseBlocking));
    process.add(new ThreadRecordingUnit("recording-unit", workerNames, equipmentExecutionCount));
    process.add(new CountingController(controllerExecutionCount));
    process.add(new CountingMeasurement(measurementExecutionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);

    Thread processRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        process.runTransient(1.0, UUID.randomUUID());
      }
    });

    try {
      processRunner.start();
      assertTrue(blockingStarted.await(5L, TimeUnit.SECONDS), "Blocking equipment did not start");
      processRunner.interrupt();
      processRunner.join(2000L);
      assertFalse(processRunner.isAlive(), "Interrupted process runner did not return promptly");
      assertEquals(0, controllerExecutionCount.get(), "Controller scan ran after equipment interruption");
      assertEquals(0, measurementExecutionCount.get(), "Measurement scan ran after equipment interruption");
    } finally {
      releaseBlocking.countDown();
      processRunner.join(2000L);
    }
  }

  /**
   * Reads the executor runtime field for copy-lifecycle regression assertions.
   *
   * @param process process system to inspect
   * @return current transient executor, or {@code null} when none has been created
   * @throws Exception if reflection cannot access the private field
   */
  private static ExecutorService getParallelTransientExecutor(ProcessSystem process) throws Exception {
    Field field = ProcessSystem.class.getDeclaredField("parallelTransientExecutor");
    field.setAccessible(true);
    return (ExecutorService) field.get(process);
  }

  /**
   * Installs a deterministic executor for level-boundary interruption testing.
   *
   * @param process process system to configure
   * @param executor executor to install
   * @param size configured executor size
   * @throws Exception if reflection cannot access the private runtime fields
   */
  private static void setParallelTransientExecutor(ProcessSystem process, ExecutorService executor, int size)
      throws Exception {
    Field executorField = ProcessSystem.class.getDeclaredField("parallelTransientExecutor");
    executorField.setAccessible(true);
    executorField.set(process, executor);
    Field sizeField = ProcessSystem.class.getDeclaredField("parallelTransientExecutorSize");
    sizeField.setAccessible(true);
    sizeField.setInt(process, size);
  }
}
