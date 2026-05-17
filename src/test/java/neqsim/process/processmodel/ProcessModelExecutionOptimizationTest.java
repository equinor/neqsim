package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.ThermodynamicModelSettings;

/**
 * Tests for ProcessModel large-model execution optimizations.
 *
 * @author ESOL
 * @version 1.0
 */
class ProcessModelExecutionOptimizationTest {

  /**
   * Lightweight ProcessSystem that records which execution path ProcessModel selected.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class RecordingProcessSystem extends ProcessSystem {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    /** Number of optimized runs selected by ProcessSystem.run(). */
    private final AtomicInteger optimizedRuns = new AtomicInteger();

    /** Number of sequential runs selected by ProcessSystem.run(). */
    private final AtomicInteger sequentialRuns = new AtomicInteger();

    /** Warm-start setting observed inside optimized runs. */
    private final List<Boolean> warmStartObserved = new CopyOnWriteArrayList<>();

    /** Child optimized flag observed inside sequential runs. */
    private final List<Boolean> optimizedFlagObservedInSequential = new CopyOnWriteArrayList<>();

    /**
     * Creates a recording process system.
     *
     * @param name process system name
     */
    private RecordingProcessSystem(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void runOptimized(UUID id) {
      optimizedRuns.incrementAndGet();
      warmStartObserved.add(Boolean.valueOf(ThermodynamicModelSettings.isUseWarmStartKValues()));
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void runSequential(UUID id) {
      sequentialRuns.incrementAndGet();
      optimizedFlagObservedInSequential.add(Boolean.valueOf(isUseOptimizedExecution()));
      setCalculationIdentifier(id);
    }

    /** {@inheritDoc} */
    @Override
    public boolean solved() {
      return true;
    }

    /**
     * Gets the number of optimized runs.
     *
     * @return optimized run count
     */
    private int getOptimizedRuns() {
      return optimizedRuns.get();
    }

    /**
     * Gets the number of sequential runs.
     *
     * @return sequential run count
     */
    private int getSequentialRuns() {
      return sequentialRuns.get();
    }

    /**
     * Gets the total number of runs.
     *
     * @return optimized plus sequential run count
     */
    private int getTotalRuns() {
      return optimizedRuns.get() + sequentialRuns.get();
    }
  }

  /**
   * Verifies that ProcessModel uses the child ProcessSystem run wrapper so warm-start is applied.
   */
  @Test
  void processModelOptimizedRunUsesChildWarmStartWrapper() {
    RecordingProcessSystem process = new RecordingProcessSystem("area");
    ProcessModel model = new ProcessModel();
    model.setUseFlashWarmStart(true);
    model.add("area", process);

    model.run();

    assertTrue(process.getOptimizedRuns() > 0, "optimized child path should run");
    assertFalse(process.warmStartObserved.isEmpty(), "warm-start state should be recorded");
    for (Boolean observedWarmStart : process.warmStartObserved) {
      assertTrue(observedWarmStart.booleanValue(), "warm-start should be active inside child run");
    }
    assertTrue(model.isModelConverged(), "model should converge");
  }

  /**
   * Verifies that parallel area execution prevents nested ProcessSystem parallelism by default.
   */
  @Test
  void parallelAreaExecutionPreventsNestedOptimizedRunsByDefault() {
    RecordingProcessSystem firstProcess = new RecordingProcessSystem("first");
    RecordingProcessSystem secondProcess = new RecordingProcessSystem("second");
    ProcessModel model = new ProcessModel();
    model.add("first", firstProcess);
    model.add("second", secondProcess);

    model.run();

    assertEquals(0, firstProcess.getOptimizedRuns(), "first area should avoid inner optimized run");
    assertEquals(0, secondProcess.getOptimizedRuns(),
        "second area should avoid inner optimized run");
    assertTrue(firstProcess.getSequentialRuns() > 0, "first area should run sequentially");
    assertTrue(secondProcess.getSequentialRuns() > 0, "second area should run sequentially");
    assertTrue(firstProcess.isUseOptimizedExecution(), "first child setting should be restored");
    assertTrue(secondProcess.isUseOptimizedExecution(), "second child setting should be restored");
    for (Boolean observedFlag : firstProcess.optimizedFlagObservedInSequential) {
      assertFalse(observedFlag.booleanValue(), "first child optimized flag should be false");
    }
    for (Boolean observedFlag : secondProcess.optimizedFlagObservedInSequential) {
      assertFalse(observedFlag.booleanValue(), "second child optimized flag should be false");
    }
  }

  /**
   * Verifies that nested optimized child execution remains available when explicitly requested.
   */
  @Test
  void nestedOptimizedRunsCanBeAllowedExplicitly() {
    RecordingProcessSystem firstProcess = new RecordingProcessSystem("first");
    RecordingProcessSystem secondProcess = new RecordingProcessSystem("second");
    ProcessModel model = new ProcessModel();
    model.setPreventNestedParallelExecution(false);
    model.add("first", firstProcess);
    model.add("second", secondProcess);

    model.run();

    assertTrue(firstProcess.getOptimizedRuns() > 0, "first area should use optimized child run");
    assertTrue(secondProcess.getOptimizedRuns() > 0, "second area should use optimized child run");
    assertEquals(0, firstProcess.getSequentialRuns(), "first area should not be forced sequential");
    assertEquals(0, secondProcess.getSequentialRuns(),
        "second area should not be forced sequential");
  }

  /**
   * Verifies that removing an area invalidates the cached inter-area execution plan.
   */
  @Test
  void removingAreaInvalidatesCachedExecutionPlan() {
    RecordingProcessSystem firstProcess = new RecordingProcessSystem("first");
    RecordingProcessSystem removedProcess = new RecordingProcessSystem("removed");
    ProcessModel model = new ProcessModel();
    model.add("first", firstProcess);
    model.add("removed", removedProcess);

    model.run();
    int removedRunsBeforeRemoval = removedProcess.getTotalRuns();
    assertTrue(removedRunsBeforeRemoval > 0, "removed area should run before removal");

    assertTrue(model.remove("removed"), "area should be removed");
    model.run();

    assertEquals(removedRunsBeforeRemoval, removedProcess.getTotalRuns(),
        "removed area should not run from a stale cached plan");
  }

  /**
   * Verifies that fast large-model mode applies warm-start and Wegstein recycle acceleration.
   */
  @Test
  void fastLargeModelModeAppliesWarmStartAndRecycleAcceleration() {
    ProcessSystem process = new ProcessSystem("withRecycle");
    Recycle recycle = new Recycle("recycle");
    process.add(recycle);
    ProcessModel model = new ProcessModel();
    model.add("withRecycle", process);

    int updatedRecycles = model.enableFastLargeModelMode();

    assertEquals(1, updatedRecycles, "one recycle should be updated");
    assertTrue(model.isUseFastRecycleConvergence(), "fast recycle convergence should be enabled");
    assertTrue(process.isUseFlashWarmStart(), "warm-start should be enabled on existing area");
    assertEquals(AccelerationMethod.WEGSTEIN, recycle.getAccelerationMethod(),
        "existing recycle should use Wegstein acceleration");

    ProcessSystem laterProcess = new ProcessSystem("later");
    Recycle laterRecycle = new Recycle("laterRecycle");
    laterProcess.add(laterRecycle);
    model.add("later", laterProcess);

    assertTrue(laterProcess.isUseFlashWarmStart(), "warm-start should apply to new area");
    assertEquals(AccelerationMethod.WEGSTEIN, laterRecycle.getAccelerationMethod(),
        "new recycle should inherit Wegstein acceleration");
  }
}
