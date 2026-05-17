package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
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
   * Creates a small gas system for graph/topology tests.
   *
   * @return configured gas fluid
   */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Adds independent stream nodes so the child ProcessSystem exposes useful inner parallelism.
   *
   * @param process process system to populate
   * @param count number of independent streams to add
   */
  private static void addIndependentStreams(ProcessSystem process, int count) {
    for (int i = 0; i < count; i++) {
      process.add(new Stream(process.getName() + " stream " + i, createGasFluid()));
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
   * Verifies that adaptive scheduling preserves child optimized execution for wide child graphs.
   */
  @Test
  void adaptiveParallelismUsesChildOptimizedRunsForWideAreas() {
    RecordingProcessSystem firstProcess = new RecordingProcessSystem("first");
    RecordingProcessSystem secondProcess = new RecordingProcessSystem("second");
    addIndependentStreams(firstProcess, 4);
    addIndependentStreams(secondProcess, 4);
    ProcessModel model = new ProcessModel();
    model.add("first", firstProcess);
    model.add("second", secondProcess);

    model.run();

    assertTrue(firstProcess.getOptimizedRuns() > 0,
        "wide first area should keep child optimized execution");
    assertTrue(secondProcess.getOptimizedRuns() > 0,
        "wide second area should keep child optimized execution");
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
   * Verifies that child topology edits invalidate ProcessModel's cached area execution plan.
   */
  @Test
  void childTopologyChangeInvalidatesCachedExecutionPlan() {
    RecordingProcessSystem upstream = new RecordingProcessSystem("upstream");
    RecordingProcessSystem downstream = new RecordingProcessSystem("downstream");
    Stream feed = new Stream("feed", createGasFluid());
    Separator separator = new Separator("separator", feed);
    upstream.add(feed);
    upstream.add(separator);
    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);

    model.run();

    StreamInterface boundaryStream = separator.getGasOutStream();
    downstream.add(boundaryStream);
    List<String> events = new CopyOnWriteArrayList<>();
    model.setProgressListener(new ProcessModel.ModelProgressListener() {
      /** {@inheritDoc} */
      @Override
      public void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("before:" + areaName);
      }

      /** {@inheritDoc} */
      @Override
      public void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
          int totalAreas, int iterationNumber) {
        events.add("after:" + areaName);
      }
    });

    model.run();

    int afterUpstream = events.indexOf("after:upstream");
    int beforeDownstream = events.indexOf("before:downstream");
    assertTrue(afterUpstream >= 0, "upstream should complete");
    assertTrue(beforeDownstream >= 0, "downstream should start");
    assertTrue(beforeDownstream > afterUpstream,
        "downstream should wait for upstream after child topology change");
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
    assertTrue(model.isUseCoordinatedRecycleAcceleration(),
        "coordinated recycle acceleration should be enabled in fast mode");
    assertTrue(process.isUseFlashWarmStart(), "warm-start should be enabled on existing area");
    assertTrue(process.isUseCoordinatedRecycleAcceleration(),
        "coordinated acceleration should be enabled on existing area");
    assertEquals(AccelerationMethod.WEGSTEIN, recycle.getAccelerationMethod(),
        "existing recycle should use Wegstein acceleration");

    ProcessSystem laterProcess = new ProcessSystem("later");
    Recycle laterRecycle = new Recycle("laterRecycle");
    laterProcess.add(laterRecycle);
    model.add("later", laterProcess);

    assertTrue(laterProcess.isUseFlashWarmStart(), "warm-start should apply to new area");
    assertTrue(laterProcess.isUseCoordinatedRecycleAcceleration(),
        "coordinated acceleration should apply to new area");
    assertEquals(AccelerationMethod.WEGSTEIN, laterRecycle.getAccelerationMethod(),
        "new recycle should inherit Wegstein acceleration");
  }
}
