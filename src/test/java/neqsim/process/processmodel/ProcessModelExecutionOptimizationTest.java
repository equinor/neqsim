package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.processmodel.lifecycle.ProcessModelState;
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

    /** Number of step-mode runs selected by ProcessModel.runStep. */
    private final AtomicInteger stepRuns = new AtomicInteger();

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
    public void run_step(UUID id) {
      stepRuns.incrementAndGet();
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
     * Gets the number of step-mode runs.
     *
     * @return step-mode run count
     */
    private int getStepRuns() {
      return stepRuns.get();
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
   * Verifies that ProcessModel step mode uses child run_step without invoking run().
   */
  @Test
  void runStepModeUsesChildStepExecution() {
    RecordingProcessSystem firstProcess = new RecordingProcessSystem("first");
    RecordingProcessSystem secondProcess = new RecordingProcessSystem("second");
    ProcessModel model = new ProcessModel();
    model.setRunStep(true);
    model.add("first", firstProcess);
    model.add("second", secondProcess);

    model.run();

    assertTrue(firstProcess.getStepRuns() > 0, "first area should run in step mode");
    assertTrue(secondProcess.getStepRuns() > 0, "second area should run in step mode");
    assertEquals(0, firstProcess.getOptimizedRuns(), "first area should not call run()");
    assertEquals(0, secondProcess.getOptimizedRuns(), "second area should not call run()");
    assertEquals(0, firstProcess.getSequentialRuns(), "first area should not call run()");
    assertEquals(0, secondProcess.getSequentialRuns(), "second area should not call run()");
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
   * Verifies that applying JSON inter-area links invalidates ProcessModel topology caches.
   */
  @Test
  void applyingInterAreaLinksInvalidatesCachedExecutionPlan() {
    ProcessSystem upstream = new ProcessSystem("upstream");
    Stream feed = new Stream("feed", createGasFluid());
    Separator separator = new Separator("separator", feed);
    upstream.add(feed);
    upstream.add(separator);

    ProcessSystem downstream = new ProcessSystem("downstream");
    Stream placeholder = new Stream("placeholder", createGasFluid());
    Separator consumer = new Separator("consumer", placeholder);
    downstream.add(placeholder);
    downstream.add(consumer);

    ProcessModel model = new ProcessModel();
    model.add("upstream", upstream);
    model.add("downstream", downstream);
    model.run();

    JsonObject link = new JsonObject();
    link.addProperty("sourceArea", "upstream");
    link.addProperty("source", "separator.gasOut");
    link.addProperty("targetArea", "downstream");
    link.addProperty("targetUnit", "consumer");
    link.addProperty("targetInletIndex", 0);
    JsonArray links = new JsonArray();
    links.add(link);

    assertTrue(model.applyInterAreaLinks(links).isEmpty(), "inter-area link should apply");

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
        "downstream should wait for upstream after inter-area link application");
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

  /**
   * Verifies that ProcessModel JSON preserves execution optimization flags.
   */
  @Test
  void executionOptimizationFlagsRoundTripThroughProcessModelJson() {
    ProcessSystem process = new ProcessSystem("area");
    process.add(new Stream("feed", createGasFluid()));
    ProcessModel model = new ProcessModel();
    model.setUseOptimizedExecution(false);
    model.setPreventNestedParallelExecution(false);
    model.setUseAdaptiveModelParallelism(false);
    model.setUseIncrementalAreaExecution(false);
    model.setUseFastRecycleConvergence(true);
    model.setUseCoordinatedRecycleAcceleration(true);
    model.setUseFlashWarmStart(true);
    model.add("area", process);

    ProcessModel restored = ProcessModel.fromJson(model.toJson(false));

    assertFalse(restored.isUseOptimizedExecution(), "optimized execution flag should round-trip");
    assertFalse(restored.isPreventNestedParallelExecution(),
        "nested-parallel prevention flag should round-trip");
    assertFalse(restored.isUseAdaptiveModelParallelism(),
        "adaptive parallelism flag should round-trip");
    assertFalse(restored.isUseIncrementalAreaExecution(),
        "incremental execution flag should round-trip");
    assertTrue(restored.isUseFastRecycleConvergence(),
        "fast recycle convergence flag should round-trip");
    assertTrue(restored.isUseCoordinatedRecycleAcceleration(),
        "coordinated recycle acceleration flag should round-trip");
    assertTrue(restored.isUseFlashWarmStart(), "flash warm-start flag should round-trip");
    assertTrue(restored.get("area").isUseFlashWarmStart(),
        "flash warm-start should propagate to rebuilt areas");
    assertTrue(restored.get("area").isUseCoordinatedRecycleAcceleration(),
        "coordinated acceleration should propagate to rebuilt areas");
  }

  /**
   * Verifies that ProcessModel lifecycle state preserves execution optimization flags.
   */
  @Test
  void executionOptimizationFlagsRoundTripThroughLifecycleState() {
    ProcessSystem process = new ProcessSystem("area");
    ProcessModel model = new ProcessModel();
    model.setUseOptimizedExecution(false);
    model.setPreventNestedParallelExecution(false);
    model.setUseAdaptiveModelParallelism(false);
    model.setUseIncrementalAreaExecution(false);
    model.setUseFastRecycleConvergence(true);
    model.setUseCoordinatedRecycleAcceleration(true);
    model.setUseFlashWarmStart(true);
    model.add("area", process);

    ProcessModelState state = model.exportState();
    ProcessModelState.ExecutionConfig config = state.getExecutionConfig();
    ProcessModel restored = state.toProcessModel();

    assertFalse(config.isUseOptimizedExecution(), "state should capture optimized execution");
    assertFalse(config.isPreventNestedParallelExecution(),
        "state should capture nested-parallel prevention");
    assertFalse(config.isUseAdaptiveModelParallelism(),
        "state should capture adaptive parallelism");
    assertFalse(config.isUseIncrementalAreaExecution(),
        "state should capture incremental execution");
    assertTrue(config.isUseFastRecycleConvergence(), "state should capture fast recycle mode");
    assertTrue(config.isUseCoordinatedRecycleAcceleration(),
        "state should capture coordinated recycle acceleration");
    assertTrue(config.isUseFlashWarmStart(), "state should capture flash warm-start");

    assertFalse(restored.isUseOptimizedExecution(), "optimized execution flag should restore");
    assertFalse(restored.isPreventNestedParallelExecution(),
        "nested-parallel prevention flag should restore");
    assertFalse(restored.isUseAdaptiveModelParallelism(),
        "adaptive parallelism flag should restore");
    assertFalse(restored.isUseIncrementalAreaExecution(),
        "incremental execution flag should restore");
    assertTrue(restored.isUseFastRecycleConvergence(), "fast recycle flag should restore");
    assertTrue(restored.isUseCoordinatedRecycleAcceleration(),
        "coordinated acceleration flag should restore");
    assertTrue(restored.isUseFlashWarmStart(), "flash warm-start flag should restore");
    assertTrue(restored.get("area").isUseFlashWarmStart(),
        "flash warm-start should propagate to restored areas");
    assertTrue(restored.get("area").isUseCoordinatedRecycleAcceleration(),
        "coordinated acceleration should propagate to restored areas");
  }
}
