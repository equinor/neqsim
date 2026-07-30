package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;

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
   * Runtime worker pools must not participate in process serialization or copying.
   */
  @Test
  public void parallelTransientExecutorIsExcludedFromCopies() {
    Set<String> workerNames = Collections.synchronizedSet(new HashSet<String>());
    AtomicInteger executionCount = new AtomicInteger();
    ProcessSystem process = new ProcessSystem();
    process.add(new ThreadRecordingUnit("unit", workerNames, executionCount));
    process.add(new ThreadRecordingUnit("unit-2", workerNames, executionCount));
    process.setParallelTransientEnabled(true);
    process.setTransientThreadPoolSize(2);
    process.runTransient(1.0, UUID.randomUUID());

    ProcessSystem copiedProcess = process.copy();
    copiedProcess.runTransient(1.0, UUID.randomUUID());

    assertEquals(1.0, process.getTime(), 1.0e-12);
    assertEquals(2.0, copiedProcess.getTime(), 1.0e-12);
  }
}
