package neqsim.process.processmodel;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import neqsim.process.dynamics.EventScheduler;
import neqsim.process.dynamics.IntegratorStrategy;
import neqsim.process.dynamics.TransientStepTransaction;
import neqsim.process.dynamics.TransientTransactionCoverage;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.report.Report;
import neqsim.util.validation.ValidationResult;

/**
 * ProcessModel class. Manages a collection of processes that can be run in steps or continuously.
 *
 * <p>
 * This class supports serialization via {@link #saveToNeqsim(String)} and {@link #loadFromNeqsim(String)} for full
 * model persistence.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessModel implements Runnable, Serializable {
  private static final long serialVersionUID = 1001L;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ProcessModel.class);
  private Map<String, ProcessSystem> processes = new LinkedHashMap<>();

  /** Active multi-area transient transaction, or {@code null} outside a trial step. */
  private transient ProcessModelStepTransaction activeTransientStepTransaction = null;

  /** Absolute tolerance used when checking that transient process-area clocks are aligned. */
  private static final double TRANSIENT_AREA_TIME_ABSOLUTE_TOLERANCE_SECONDS = 1.0e-9;

  /** Relative tolerance used when checking that transient process-area clocks are aligned. */
  private static final double TRANSIENT_AREA_TIME_RELATIVE_TOLERANCE = 1.0e-12;

  /** Metadata key used for JSON round-trip inter-area stream rewiring. */
  private static final String INTER_AREA_LINKS_KEY = "interAreaLinks";

  /**
   * Internal JSON reference to a stream produced inside one process area.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class AreaStreamReference {
    /** Name of the process area that produces the stream. */
    private final String areaName;

    /** Stream reference within the producing area's JSON schema. */
    private final String streamReference;

    /**
     * Creates a stream reference descriptor.
     *
     * @param areaName name of the producing process area
     * @param streamReference stream reference inside the producing area
     */
    private AreaStreamReference(String areaName, String streamReference) {
      this.areaName = areaName;
      this.streamReference = streamReference;
    }
  }

  /**
   * Cached inter-area topology used during model execution.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class AreaExecutionPlan {
    /** Ordered execution levels; areas in the same level may run concurrently. */
    private final List<List<ProcessSystem>> levels;

    /** Downstream area adjacency map, keyed by process identity. */
    private final Map<ProcessSystem, java.util.Set<ProcessSystem>> successors;

    /** Boundary streams keyed by object identity. */
    private final java.util.Set<Object> boundaryStreams;

    /** Areas that consume each boundary stream, keyed by stream identity. */
    private final Map<Object, java.util.Set<ProcessSystem>> boundaryConsumers;

    /** Producing {@code "area::unit"} label for each stream, keyed by stream identity. */
    private final Map<Object, String> streamProducers;

    /** Structure versions observed when this plan was built. */
    private final Map<ProcessSystem, Long> structureVersions;

    /**
     * Creates an immutable execution-plan holder.
     *
     * @param levels ordered inter-area execution levels
     * @param successors downstream area adjacency map
     * @param boundaryStreams streams crossing process-area boundaries
     * @param boundaryConsumers consumer areas for each boundary stream
     * @param streamProducers producing {@code "area::unit"} label per stream identity
     * @param structureVersions process structure versions observed while building the plan
     */
    private AreaExecutionPlan(List<List<ProcessSystem>> levels,
        Map<ProcessSystem, java.util.Set<ProcessSystem>> successors, java.util.Set<Object> boundaryStreams,
        Map<Object, java.util.Set<ProcessSystem>> boundaryConsumers, Map<Object, String> streamProducers,
        Map<ProcessSystem, Long> structureVersions) {
      this.levels = levels;
      this.successors = successors;
      this.boundaryStreams = boundaryStreams;
      this.boundaryConsumers = boundaryConsumers;
      this.streamProducers = streamProducers;
      this.structureVersions = structureVersions;
    }
  }

  private boolean runStep = false;
  private int maxIterations = 50;
  private boolean useOptimizedExecution = true;

  /** Cached inter-area execution plan for the current model topology. */
  private transient AreaExecutionPlan cachedAreaExecutionPlan;

  /** True when the cached inter-area execution plan must be rebuilt. */
  private transient boolean areaExecutionPlanDirty = true;

  /**
   * When true, model-level parallel area execution temporarily disables inner ProcessSystem parallelism for areas
   * running in the same level. This avoids nested work submitted to the same fixed-size shared thread pool.
   */
  private boolean preventNestedParallelExecution = true;

  /**
   * When true, ProcessModel chooses per execution level whether outer area parallelism or inner ProcessSystem
   * parallelism is expected to give better throughput.
   */
  private boolean useAdaptiveModelParallelism = true;

  /**
   * When true, clean process areas may be skipped on later outer iterations when their boundary streams did not change
   * beyond convergence tolerances.
   */
  private boolean useIncrementalAreaExecution = true;

  /** Whether fast recycle convergence options have been requested for large models. */
  private boolean useFastRecycleConvergence = false;

  /** Whether coordinated acceleration should be enabled for recycle-heavy child areas. */
  private boolean useCoordinatedRecycleAcceleration = false;

  /**
   * Transient listener for model-level progress callbacks. Marked transient to avoid serialization issues.
   */
  private transient ModelProgressListener progressListener = null;

  /**
   * When true, lifecycle events are published to the ProcessEventBus singleton during model execution. Default is false
   * for zero overhead when not needed.
   */
  private boolean publishEvents = false;

  /**
   * When true, validateSetup() is called on each ProcessSystem before the first iteration. Validation warnings are
   * logged but do not abort execution.
   */
  private boolean autoValidate = false;

  /**
   * When true, every ProcessSystem registered with this model has flash warm-start enabled for the duration of its run
   * (via {@link ProcessSystem#setUseFlashWarmStart(boolean)}). Default is {@code false}. Setting this flag updates all
   * currently registered ProcessSystems and applies to any ProcessSystem added afterwards.
   */
  private boolean useFlashWarmStart = false;

  /** Whether automatic checkpointing is enabled during model execution. */
  private boolean checkpointEnabled = false;

  /** Number of iterations between automatic checkpoints. */
  private int checkpointInterval = 10;

  /** File path for saving checkpoint files. */
  private String checkpointPath = null;

  /**
   * Interface for monitoring ProcessModel execution progress. Implementations receive callbacks at the model level:
   * before/after each process area runs, before/after each outer iteration, and when the model starts/completes.
   *
   * <p>
   * Designed for integration with:
   * <ul>
   * <li>Jupyter notebooks for monitoring multi-area convergence</li>
   * <li>Digital twin dashboards for plant-wide status</li>
   * <li>Debugging tools for inter-process convergence analysis</li>
   * </ul>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public interface ModelProgressListener {
    /**
     * Called after a process area completes a single execution pass.
     *
     * @param areaName the name of the process area
     * @param process the ProcessSystem that completed
     * @param areaIndex zero-based index of the area in execution order
     * @param totalAreas total number of process areas
     * @param iterationNumber current outer iteration number (starts at 1)
     */
    void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex, int totalAreas,
        int iterationNumber);

    /**
     * Called before a process area is executed.
     *
     * @param areaName the name of the process area about to run
     * @param process the ProcessSystem about to run
     * @param areaIndex zero-based index of the area
     * @param totalAreas total number of process areas
     * @param iterationNumber current outer iteration number (starts at 1)
     */
    default void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex, int totalAreas,
        int iterationNumber) {
      // Default does nothing
    }

    /**
     * Called when an outer iteration of the model completes.
     *
     * @param iterationNumber the iteration that just completed (starts at 1)
     * @param converged true if the model has converged
     * @param maxError maximum relative error across all variables
     */
    default void onIterationComplete(int iterationNumber, boolean converged, double maxError) {
      // Default does nothing
    }

    /**
     * Called at the start of each outer iteration, before any areas are run.
     *
     * @param iterationNumber the iteration about to start (starts at 1)
     */
    default void onBeforeIteration(int iterationNumber) {
      // Default does nothing
    }

    /**
     * Called once when the model begins execution.
     *
     * @param totalAreas total number of process areas
     */
    default void onModelStart(int totalAreas) {
      // Default does nothing
    }

    /**
     * Called once when the model finishes execution.
     *
     * @param totalIterations total number of iterations performed
     * @param converged true if the model converged
     */
    default void onModelComplete(int totalIterations, boolean converged) {
      // Default does nothing
    }

    /**
     * Called if a process area encounters an error during execution.
     *
     * @param areaName name of the area that failed
     * @param process the ProcessSystem that failed
     * @param exception the exception that was thrown
     * @return true to continue with next area, false to abort
     */
    default boolean onProcessAreaError(String areaName, ProcessSystem process, Exception exception) {
      return false;
    }
  }

  // Convergence tolerances (relative errors)
  private double flowTolerance = 1e-4;
  private double temperatureTolerance = 1e-4;
  private double pressureTolerance = 1e-4;

  /**
   * Relative tolerance the auto-tuner applies when no tolerance was set explicitly. 1e-3 (0.1 %) is an
   * engineering-grade accuracy for process calculations: it is well below plant instrument and EOS uncertainty, yet
   * loose enough that recycle-rich plants converge in a fraction of the passes a 1e-4 gate needs.
   */
  public static final double DEFAULT_ENGINEERING_TOLERANCE = 1.0e-3;

  /**
   * Loosest relative tolerance the auto-tuner will ever accept (1 %). A residual that stalls above this is a real
   * convergence failure, not a tight gate, and is never accepted.
   */
  public static final double DEFAULT_AUTO_TOLERANCE_CEILING = 1.0e-2;

  /** Outer iterations the residual must stop improving over before the auto-tuner accepts it. */
  public static final int AUTO_TOLERANCE_STALL_WINDOW = 5;

  /** Relative improvement across the stall window that still counts as progress. */
  private static final double AUTO_TOLERANCE_STALL_IMPROVEMENT = 0.10;

  /** True once a tolerance was set explicitly, so the auto-tuner must not touch it. */
  private boolean toleranceExplicit = false;

  /** Whether the model may pick (and, on a stall, relax) its own convergence tolerance. */
  private boolean autoTolerance = true;

  /** Loosest relative tolerance the auto-tuner may relax to on a stalled residual. */
  private double autoToleranceCeiling = DEFAULT_AUTO_TOLERANCE_CEILING;

  /** Human-readable description of the tolerance the auto-tuner chose on the last run. */
  private String autoToleranceSummary = "";

  /** Worst relative error per outer iteration, used to detect a stalled residual. */
  private transient java.util.List<Double> autoToleranceErrorHistory = new java.util.ArrayList<>();

  /**
   * Plant mass-closure error the auto-tuner accepts, as a fraction of plant feed (0.1 %).
   *
   * <p>
   * The boundary residual only measures how much stream values still move between outer passes. It is blind to an
   * unconverged {@link neqsim.process.equipment.util.Recycle}, whose open tear is a standing mass source or sink: the
   * plant can sit perfectly still on the boundary metric while destroying several percent of the feed. This gate adds
   * the missing physical criterion.
   * </p>
   */
  public static final double DEFAULT_MASS_CLOSURE_TOLERANCE = 1.0e-3;

  /** Whether the auto-tuner refuses to call the model converged while recycle tears are still open. */
  private boolean autoMassClosureGate = true;

  /** Accepted plant mass-closure error, as a fraction of plant feed. */
  private double massClosureTolerance = DEFAULT_MASS_CLOSURE_TOLERANCE;

  /** Plant mass-closure error at the last convergence check, as a fraction of plant feed. */
  private double lastMassClosureError = Double.NaN;

  /** Human-readable description of the last mass-closure check. */
  private String massClosureSummary = "";

  /** Units creating or destroying the most mass at the last check, worst first. */
  private String massClosureOffenders = "";

  /**
   * Whether the unit-level closure figure also blocks a converged verdict.
   *
   * <p>
   * Off by default: a non-recycle unit that does not conserve mass is an equipment defect rather than something the
   * outer solver can close, so gating on it would iterate to the cap and bury the real diagnosis. The figure is always
   * reported so the defect is never silent.
   * </p>
   */
  private boolean unitMassClosureGate = false;

  /** Unit-level mass-closure error at the last check, as a fraction of plant feed. */
  private double lastUnitMassClosureError = Double.NaN;

  /** Non-recycle units creating or destroying the most mass at the last check, worst first. */
  private String unitMassClosureOffenders = "";

  /** Default boundary-stream flow floor in kg/hr (streams below this are ignored entirely). */
  public static final double DEFAULT_BOUNDARY_FLOW_FLOOR = 1e-9;

  /**
   * Boundary streams whose flow is below this value (kg/hr) are excluded from the convergence metric entirely.
   */
  private double boundaryFlowFloor = DEFAULT_BOUNDARY_FLOW_FLOOR;

  /**
   * Absolute flow tolerance in kg/hr. A boundary stream is flow-converged when EITHER its relative flow error is below
   * {@link #flowTolerance} OR its absolute flow change is below this value. Default 0.0 preserves the historical
   * relative-only behaviour.
   */
  private double absoluteFlowTolerance = 0.0;

  /**
   * Default noise-floor fraction of the detected total plant feed flow used by the auto-tuner. The threshold is a
   * convergence scale, not a declaration that every smaller process stream is physically unimportant; callers can
   * protect significant small-flow equipment with an explicit minimum-flow setting or disable automatic bypass.
   */
  public static final double DEFAULT_AUTO_TUNING_FLOW_FRACTION = 1.0e-6;

  /** Whether {@link #runUntilConverged(int)} auto-derives the flow noise filters from the plant flow scale. */
  private boolean autoConvergenceTuning = true;

  /** Whether the auto-tuner may also auto-bypass units whose inlet flow is below the noise floor. */
  private boolean autoLowFlowBypass = true;

  /** Noise-floor fraction of the detected plant mass-flow scale (see {@link #DEFAULT_AUTO_TUNING_FLOW_FRACTION}). */
  private double autoTuningFlowFraction = DEFAULT_AUTO_TUNING_FLOW_FRACTION;

  /** True once {@link #setBoundaryFlowFloor(double)} has been called, so the auto-tuner must not override it. */
  private boolean boundaryFlowFloorExplicit = false;

  /** True once {@link #setAbsoluteFlowTolerance(double)} has been called, so the auto-tuner must not override it. */
  private boolean absoluteFlowToleranceExplicit = false;

  /** Total feed-boundary mass flow (kg/hr) detected by the auto-tuner on the last run. */
  private double detectedPlantFlowScale = 0.0;

  /** Flow scale the auto-tuner last applied its thresholds for; used to detect a ramping plant. */
  private transient double autoTuningAppliedScale = 0.0;

  /** Human-readable description of what the auto-tuner did on the last run. */
  private String autoTuningSummary = "";

  // Convergence tracking
  private int lastIterationCount = 0;
  private double lastMaxFlowError = Double.MAX_VALUE;
  private double lastMaxTemperatureError = Double.MAX_VALUE;
  private double lastMaxPressureError = Double.MAX_VALUE;
  private boolean modelConverged = false;
  private boolean lastAllProcessesSolved = false;
  private boolean lastBoundaryValuesConverged = false;
  private int lastBoundaryStreamCount = 0;
  /** Per-boundary-stream convergence errors recorded on the last outer iteration. */
  private List<BoundaryStreamError> lastBoundaryStreamErrors = new ArrayList<>();
  /** Identity cache for immutable boundary diagnostics reused across unchanged observations. */
  private transient Map<Object, BoundaryStreamError> boundaryStreamErrorCache = new IdentityHashMap<>();

  /**
   * Per-stream convergence diagnostics for a single boundary stream.
   *
   * <p>
   * Recorded on every outer iteration so that a non-converged model can name the stream responsible for the reported
   * maximum flow, temperature or pressure error instead of only reporting the magnitude.
   * </p>
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class BoundaryStreamError implements Serializable {
    private static final long serialVersionUID = 1000L;
    /** Name of the boundary stream. */
    private final String streamName;
    /** Producing {@code "area::unit"} label, or an empty string when unknown. */
    private final String producerLabel;
    /** Relative flow-rate error between the two last outer iterations. */
    private final double flowError;
    /** Relative temperature error between the two last outer iterations. */
    private final double temperatureError;
    /** Relative pressure error between the two last outer iterations. */
    private final double pressureError;
    /** Flow rate on the previous outer iteration in kg/hr. */
    private final double previousFlow;
    /** Flow rate on the current outer iteration in kg/hr. */
    private final double currentFlow;

    /**
     * Creates a boundary stream error record.
     *
     * @param streamName name of the boundary stream
     * @param producerLabel producing {@code "area::unit"} label, or {@code null} when unknown
     * @param flowError relative flow-rate error
     * @param temperatureError relative temperature error
     * @param pressureError relative pressure error
     * @param previousFlow previous-iteration flow rate in kg/hr
     * @param currentFlow current-iteration flow rate in kg/hr
     */
    private BoundaryStreamError(String streamName, String producerLabel, double flowError, double temperatureError,
        double pressureError, double previousFlow, double currentFlow) {
      this.streamName = streamName;
      this.producerLabel = producerLabel == null ? "" : producerLabel;
      this.flowError = flowError;
      this.temperatureError = temperatureError;
      this.pressureError = pressureError;
      this.previousFlow = previousFlow;
      this.currentFlow = currentFlow;
    }

    /**
     * Name of the boundary stream.
     *
     * @return the stream name
     */
    public String getStreamName() {
      return streamName;
    }

    /**
     * Producing unit of this boundary stream as {@code "area::unit"}.
     *
     * <p>
     * Equipment that auto-names its outlets (splitters emit {@code "Split Stream_0"}, {@code "Split Stream_1"}, ...)
     * produces identical stream names all over a large plant, so the stream name alone cannot identify the offender.
     * This label names the unit that produced the stream.
     * </p>
     *
     * @return {@code "area::unit"}, or an empty string when the producer is unknown
     */
    public String getProducerLabel() {
      return producerLabel;
    }

    /**
     * Stream name qualified by its producing unit, e.g. {@code "sep train B::gassplitter2 -> Split Stream_1"}.
     *
     * @return the qualified name, or the plain stream name when the producer is unknown
     */
    public String getQualifiedName() {
      return producerLabel.isEmpty() ? streamName : producerLabel + " -> " + streamName;
    }

    /**
     * Relative flow-rate error between the two last outer iterations.
     *
     * @return relative flow error
     */
    public double getFlowError() {
      return flowError;
    }

    /**
     * Relative temperature error between the two last outer iterations.
     *
     * @return relative temperature error
     */
    public double getTemperatureError() {
      return temperatureError;
    }

    /**
     * Relative pressure error between the two last outer iterations.
     *
     * @return relative pressure error
     */
    public double getPressureError() {
      return pressureError;
    }

    /**
     * Flow rate recorded on the previous outer iteration.
     *
     * @return previous flow rate in kg/hr
     */
    public double getPreviousFlow() {
      return previousFlow;
    }

    /**
     * Flow rate recorded on the current outer iteration.
     *
     * @return current flow rate in kg/hr
     */
    public double getCurrentFlow() {
      return currentFlow;
    }

    /**
     * Absolute change in flow rate between the two last outer iterations.
     *
     * <p>
     * A large relative error on a very small absolute change is numerical noise on a stagnant leg rather than a real
     * process residual; use this value to tell the two apart.
     * </p>
     *
     * @return absolute flow change in kg/hr
     */
    public double getAbsoluteFlowChange() {
      return Math.abs(currentFlow - previousFlow);
    }

    /**
     * Largest of the flow, temperature and pressure relative errors.
     *
     * @return maximum relative error for this stream
     */
    public double getMaxError() {
      return Math.max(flowError, Math.max(temperatureError, pressureError));
    }

    /**
     * Whether the stream flow collapsed from a non-zero value to (numerically) zero between the two last outer
     * iterations. This produces a relative flow error of exactly 1.0 and usually means an upstream unit stopped
     * producing the stream rather than a slowly converging recycle.
     *
     * @return {@code true} when the flow dropped from non-zero to zero
     */
    public boolean isFlowCollapsedToZero() {
      return Math.abs(previousFlow) > 1e-9 && Math.abs(currentFlow) <= 1e-9;
    }

    /**
     * Whether the stream flow started from (numerically) zero and became non-zero between the two last outer
     * iterations.
     *
     * @return {@code true} when the flow started up from zero
     */
    public boolean isFlowStartedFromZero() {
      return Math.abs(previousFlow) <= 1e-9 && Math.abs(currentFlow) > 1e-9;
    }
  }

  /**
   * Checks if the model is running in step mode.
   *
   * @return a boolean
   */
  public boolean isRunStep() {
    return runStep;
  }

  /**
   * Sets the step mode for the process.
   *
   * @param runStep a boolean
   */
  public void setRunStep(boolean runStep) {
    this.runStep = runStep;
  }

  /**
   * Check if optimized execution is enabled for individual ProcessSystems.
   *
   * <p>
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which auto-selects the best
   * execution strategy based on topology.
   * </p>
   *
   * @return true if optimized execution is enabled
   */
  public boolean isUseOptimizedExecution() {
    return useOptimizedExecution;
  }

  /**
   * Enable or disable optimized execution for individual ProcessSystems.
   *
   * <p>
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which auto-selects the best
   * execution strategy (parallel for feed-forward, hybrid for recycle processes). When disabled, uses standard
   * sequential {@link ProcessSystem#run()}.
   * </p>
   *
   * @param useOptimizedExecution true to enable optimized execution
   */
  public void setUseOptimizedExecution(boolean useOptimizedExecution) {
    this.useOptimizedExecution = useOptimizedExecution;
  }

  /**
   * Returns whether nested parallel execution is prevented during model-level parallel area runs.
   *
   * @return true if ProcessSystems in the same parallel area level are run sequentially internally
   */
  public boolean isPreventNestedParallelExecution() {
    return preventNestedParallelExecution;
  }

  /**
   * Enable or disable nested parallel execution prevention.
   *
   * <p>
   * When enabled, areas that run concurrently at the {@code ProcessModel} level temporarily execute their child
   * {@link ProcessSystem}s in sequential mode. This keeps area-level workers from blocking while also submitting inner
   * unit-operation work to the same shared thread pool.
   * </p>
   *
   * @param preventNestedParallelExecution true to prevent nested parallel submissions
   */
  public void setPreventNestedParallelExecution(boolean preventNestedParallelExecution) {
    this.preventNestedParallelExecution = preventNestedParallelExecution;
  }

  /**
   * Returns whether adaptive model-level parallelism is enabled.
   *
   * @return true if ProcessModel may choose inner ProcessSystem parallelism for wide child areas
   */
  public boolean isUseAdaptiveModelParallelism() {
    return useAdaptiveModelParallelism;
  }

  /**
   * Enable or disable adaptive model-level parallelism.
   *
   * <p>
   * When enabled, a ProcessModel execution level with multiple independent areas may run those areas sequentially while
   * preserving child ProcessSystem optimized execution if the children expose substantially more parallel work than the
   * outer area level. This avoids the blunt choice of always using outer area parallelism with child parallelism
   * disabled.
   * </p>
   *
   * @param useAdaptiveModelParallelism true to enable adaptive outer-vs-inner parallelism choice
   */
  public void setUseAdaptiveModelParallelism(boolean useAdaptiveModelParallelism) {
    this.useAdaptiveModelParallelism = useAdaptiveModelParallelism;
  }

  /**
   * Returns whether incremental area execution is enabled for outer iterations.
   *
   * @return true if unchanged areas may be skipped on later outer iterations
   */
  public boolean isUseIncrementalAreaExecution() {
    return useIncrementalAreaExecution;
  }

  /**
   * Enable or disable incremental area execution for converging large models.
   *
   * <p>
   * When enabled, the first outer iteration runs every area. Later iterations rerun only areas downstream of boundary
   * streams that changed beyond the configured flow, temperature, or pressure tolerances. If lifecycle hooks or event
   * publishing are enabled, all areas are run to preserve callback semantics.
   * </p>
   *
   * @param useIncrementalAreaExecution true to allow skipping clean areas in later iterations
   */
  public void setUseIncrementalAreaExecution(boolean useIncrementalAreaExecution) {
    this.useIncrementalAreaExecution = useIncrementalAreaExecution;
  }

  /**
   * Explicitly enables the fast large-model execution profile.
   *
   * <p>
   * This profile keeps the conservative model-level safeguards enabled, turns on flash warm-start for child
   * ProcessSystems, and applies Wegstein acceleration to all existing Recycle units. The same recycle acceleration is
   * applied to ProcessSystems added after the profile is enabled.
   * </p>
   *
   * @return number of Recycle units updated across all currently registered areas
   */
  public int enableFastLargeModelMode() {
    preventNestedParallelExecution = true;
    useAdaptiveModelParallelism = true;
    useIncrementalAreaExecution = true;
    useFastRecycleConvergence = true;
    setUseCoordinatedRecycleAcceleration(true);
    setUseFlashWarmStart(true);
    return setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);
  }

  /**
   * Returns whether fast recycle convergence has been requested for this model.
   *
   * @return true if newly added ProcessSystems should receive fast recycle settings
   */
  public boolean isUseFastRecycleConvergence() {
    return useFastRecycleConvergence;
  }

  /**
   * Enable or disable propagation of fast recycle convergence settings.
   *
   * <p>
   * When enabled, existing and newly added {@link ProcessSystem}s receive Wegstein acceleration on their
   * {@link neqsim.process.equipment.util.Recycle} units. Disabling this flag stops propagation to later areas but does
   * not reset acceleration methods already applied to existing recycles.
   * </p>
   *
   * @param useFastRecycleConvergence true to apply fast recycle settings to registered areas
   */
  public void setUseFastRecycleConvergence(boolean useFastRecycleConvergence) {
    this.useFastRecycleConvergence = useFastRecycleConvergence;
    if (useFastRecycleConvergence) {
      setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);
    }
  }

  /**
   * Enable or disable coordinated recycle acceleration across all registered ProcessSystems.
   *
   * @param useCoordinatedRecycleAcceleration true to enable coordinated recycle acceleration
   */
  public void setUseCoordinatedRecycleAcceleration(boolean useCoordinatedRecycleAcceleration) {
    this.useCoordinatedRecycleAcceleration = useCoordinatedRecycleAcceleration;
    for (ProcessSystem p : processes.values()) {
      p.setUseCoordinatedRecycleAcceleration(useCoordinatedRecycleAcceleration);
    }
  }

  /**
   * Returns whether coordinated recycle acceleration is propagated to child ProcessSystems.
   *
   * @return true if coordinated recycle acceleration is enabled at model level
   */
  public boolean isUseCoordinatedRecycleAcceleration() {
    return useCoordinatedRecycleAcceleration;
  }

  /**
   * Enable or disable flash warm-start K-values for every ProcessSystem in this model.
   *
   * <p>
   * When enabled, the iterative TPflash inside every fluid evaluation re-uses the previously converged K-values as the
   * initial estimate instead of seeding from Wilson on every call. This is delegated to
   * {@link ProcessSystem#setUseFlashWarmStart(boolean)} on every currently registered ProcessSystem and is also applied
   * to any ProcessSystem added afterwards via {@link #add(String, ProcessSystem)}. Each ProcessSystem manages the
   * underlying {@code ThermodynamicModelSettings} flag with try/finally inside its own {@code run()} so the setting
   * never leaks past the model run. Default is {@code false} (historical behaviour) — recycle-heavy multi-area models
   * are sensitive to flash trajectory and warm-start can shift the converged fixed point.
   * </p>
   *
   * @param useWarmStart true to enable warm-start across all ProcessSystems in this model
   */
  public void setUseFlashWarmStart(boolean useWarmStart) {
    this.useFlashWarmStart = useWarmStart;
    for (ProcessSystem p : processes.values()) {
      p.setUseFlashWarmStart(useWarmStart);
    }
  }

  /**
   * Returns whether flash warm-start is enabled for the ProcessSystems in this model.
   *
   * @return true if warm-start K-values are propagated to every ProcessSystem in this model
   */
  public boolean isUseFlashWarmStart() {
    return useFlashWarmStart;
  }

  /**
   * Get the maximum number of iterations for the model.
   *
   * @return maximum number of iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set the maximum number of iterations for the model.
   *
   * @param maxIterations maximum number of iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * Get flow tolerance for convergence check (relative error).
   *
   * @return flow tolerance
   */
  public double getFlowTolerance() {
    return flowTolerance;
  }

  /**
   * Set flow tolerance for convergence check (relative error).
   *
   * @param flowTolerance relative tolerance for flow rate convergence (e.g., 1e-4 = 0.01%)
   */
  public void setFlowTolerance(double flowTolerance) {
    this.flowTolerance = flowTolerance;
    this.toleranceExplicit = true;
  }

  /**
   * Get temperature tolerance for convergence check (relative error).
   *
   * @return temperature tolerance
   */
  public double getTemperatureTolerance() {
    return temperatureTolerance;
  }

  /**
   * Set temperature tolerance for convergence check (relative error).
   *
   * @param temperatureTolerance relative tolerance for temperature convergence
   */
  public void setTemperatureTolerance(double temperatureTolerance) {
    this.temperatureTolerance = temperatureTolerance;
    this.toleranceExplicit = true;
  }

  /**
   * Get pressure tolerance for convergence check (relative error).
   *
   * @return pressure tolerance
   */
  public double getPressureTolerance() {
    return pressureTolerance;
  }

  /**
   * Set pressure tolerance for convergence check (relative error).
   *
   * @param pressureTolerance relative tolerance for pressure convergence
   */
  public void setPressureTolerance(double pressureTolerance) {
    this.pressureTolerance = pressureTolerance;
    this.toleranceExplicit = true;
  }

  /**
   * Set all tolerances at once.
   *
   * <p>
   * Calling this switches the automatic tolerance selection off for this model: the value given here is used exactly as
   * specified.
   * </p>
   *
   * @param tolerance relative tolerance for all variables (flow, temperature, pressure)
   */
  public void setTolerance(double tolerance) {
    this.flowTolerance = tolerance;
    this.temperatureTolerance = tolerance;
    this.pressureTolerance = tolerance;
    this.toleranceExplicit = true;
  }

  /**
   * Whether a convergence tolerance has been set explicitly on this model.
   *
   * @return true when {@link #setTolerance(double)} or one of the per-variable setters was called
   */
  public boolean isToleranceExplicit() {
    return toleranceExplicit;
  }

  /**
   * Whether the model picks its own convergence tolerance when none was given.
   *
   * @return true when automatic tolerance selection is enabled (default)
   */
  public boolean isAutoTolerance() {
    return autoTolerance;
  }

  /**
   * Enables or disables automatic tolerance selection.
   *
   * <p>
   * When enabled (default) and no tolerance was set explicitly, {@code run()} starts from
   * {@value #DEFAULT_ENGINEERING_TOLERANCE} instead of the historical 1e-4, and accepts a residual that has stopped
   * improving as long as it is below {@link #getAutoToleranceCeiling()}. An explicit {@link #setTolerance(double)}
   * always wins.
   * </p>
   *
   * @param autoTolerance true to let the model choose its own accuracy
   */
  public void setAutoTolerance(boolean autoTolerance) {
    this.autoTolerance = autoTolerance;
  }

  /**
   * Loosest relative tolerance the auto-tuner may relax to when the residual stalls.
   *
   * @return the ceiling (default {@value #DEFAULT_AUTO_TOLERANCE_CEILING})
   */
  public double getAutoToleranceCeiling() {
    return autoToleranceCeiling;
  }

  /**
   * Sets the loosest relative tolerance the auto-tuner may relax to on a stalled residual.
   *
   * @param autoToleranceCeiling relative tolerance; must be finite and greater than zero
   * @throws IllegalArgumentException if the value is not a finite positive number
   */
  public void setAutoToleranceCeiling(double autoToleranceCeiling) {
    if (Double.isNaN(autoToleranceCeiling) || Double.isInfinite(autoToleranceCeiling) || autoToleranceCeiling <= 0.0) {
      throw new IllegalArgumentException(
          "autoToleranceCeiling must be a finite positive number, was " + autoToleranceCeiling);
    }
    this.autoToleranceCeiling = autoToleranceCeiling;
  }

  /**
   * Description of the accuracy the auto-tuner selected on the last run.
   *
   * @return a one-line summary, or an empty string when no tolerance was auto-selected
   */
  public String getAutoToleranceSummary() {
    return autoToleranceSummary;
  }

  /**
   * Get the number of iterations from the last run.
   *
   * @return iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Check if the model converged in the last run.
   *
   * @return true if converged
   */
  public boolean isModelConverged() {
    return modelConverged;
  }

  /**
   * Get maximum flow error from the last iteration.
   *
   * @return maximum relative flow error
   */
  public double getLastMaxFlowError() {
    return lastMaxFlowError;
  }

  /**
   * Get maximum temperature error from the last iteration.
   *
   * @return maximum relative temperature error
   */
  public double getLastMaxTemperatureError() {
    return lastMaxTemperatureError;
  }

  /**
   * Get maximum pressure error from the last iteration.
   *
   * @return maximum relative pressure error
   */
  public double getLastMaxPressureError() {
    return lastMaxPressureError;
  }

  /**
   * Get the maximum error across all variables (flow, temperature, pressure).
   *
   * <p>
   * This is the largest relative error from the last iteration, useful for quick convergence check.
   * </p>
   *
   * @return maximum relative error across all variables
   */
  public double getError() {
    return Math.max(lastMaxFlowError, Math.max(lastMaxTemperatureError, lastMaxPressureError));
  }

  /**
   * Set a listener to receive progress updates during model execution.
   *
   * @param listener the progress listener, or null to disable callbacks
   */
  public void setProgressListener(ModelProgressListener listener) {
    this.progressListener = listener;
  }

  /**
   * Get the current model progress listener.
   *
   * @return the current listener, or null if none is set
   */
  public ModelProgressListener getProgressListener() {
    return this.progressListener;
  }

  /**
   * Enables or disables event publishing to the ProcessEventBus singleton. When enabled, lifecycle events (model
   * start/complete, area errors, convergence) are published during execution.
   *
   * @param publish true to enable event publishing, false to disable (default)
   */
  public void setPublishEvents(boolean publish) {
    this.publishEvents = publish;
  }

  /**
   * Returns whether event publishing is enabled.
   *
   * @return true if events are published to ProcessEventBus during model execution
   */
  public boolean isPublishEvents() {
    return this.publishEvents;
  }

  /**
   * Enables or disables automatic validation of each ProcessSystem before the first iteration. When enabled,
   * validateSetup() is called on each ProcessSystem. Validation failures are logged as warnings but do not abort
   * execution.
   *
   * @param validate true to enable auto-validation, false to disable (default)
   */
  public void setAutoValidate(boolean validate) {
    this.autoValidate = validate;
  }

  /**
   * Returns whether auto-validation is enabled.
   *
   * @return true if process systems are validated before model runs
   */
  public boolean isAutoValidate() {
    return this.autoValidate;
  }

  /**
   * Adds a process to the model.
   *
   * @param name a {@link java.lang.String} object
   * @param process a {@link neqsim.process.processmodel.ProcessSystem} object
   * @return a boolean
   */
  public boolean add(String name, ProcessSystem process) {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Name cannot be null or empty");
    }
    if (process == null) {
      throw new IllegalArgumentException("Process cannot be null");
    }
    if (processes.containsKey(name)) {
      throw new IllegalArgumentException("A process with the given name already exists");
    }
    process.setName(name);
    if (useFlashWarmStart) {
      process.setUseFlashWarmStart(true);
    }
    if (useFastRecycleConvergence) {
      process.setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);
    }
    if (useCoordinatedRecycleAcceleration) {
      process.setUseCoordinatedRecycleAcceleration(true);
    }
    processes.put(name, process);
    invalidateTopology();
    return true;
  }

  /**
   * Invalidates cached inter-area topology for this model.
   *
   * <p>
   * The cache is invalidated automatically by {@link #add(String, ProcessSystem)} and {@link #remove(String)}. Call
   * this method explicitly after mutating stream wiring inside an already registered {@link ProcessSystem}, because the
   * model cannot observe those internal topology changes directly.
   * </p>
   */
  public void invalidateTopology() {
    areaExecutionPlanDirty = true;
    cachedAreaExecutionPlan = null;
  }

  /**
   * Retrieves a process by its name.
   *
   * @param name a {@link java.lang.String} object
   * @return a {@link neqsim.process.processmodel.ProcessSystem} object
   */
  public ProcessSystem get(String name) {
    return processes.get(name);
  }

  /**
   * Returns the names of all process systems in insertion order.
   *
   * @return a {@link java.util.List} of process system names
   */
  public List<String> getProcessSystemNames() {
    return new ArrayList<>(processes.keySet());
  }

  /**
   * Resolves a stream reference across the process areas in this model.
   *
   * <p>
   * Area-qualified references use {@code "area::streamRef"}, where {@code streamRef} follows
   * {@link ProcessSystem#resolveStreamReference(String)} conventions such as {@code feed}, {@code separator.gasOut}, or
   * {@code splitter.split0}. An unqualified reference is accepted only when exactly one process area resolves it. If
   * multiple areas contain the same unqualified reference, this method throws so callers cannot silently modify the
   * wrong train.
   * </p>
   *
   * @param reference area-qualified or unqualified stream reference
   * @return resolved stream, or {@code null} when the area or stream does not exist
   * @throws IllegalArgumentException if an unqualified reference matches more than one process area
   */
  public StreamInterface resolveStreamReference(String reference) {
    if (reference == null || reference.trim().isEmpty()) {
      return null;
    }
    String normalizedReference = reference.trim();
    int areaSeparator = normalizedReference.indexOf("::");
    if (areaSeparator >= 0) {
      String areaName = normalizedReference.substring(0, areaSeparator).trim();
      String localReference = normalizedReference.substring(areaSeparator + 2).trim();
      if (areaName.isEmpty() || localReference.isEmpty()) {
        return null;
      }
      ProcessSystem processSystem = processes.get(areaName);
      return processSystem == null ? null : processSystem.resolveStreamReference(localReference);
    }

    StreamInterface resolved = null;
    String resolvedArea = null;
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      StreamInterface candidate = entry.getValue().resolveStreamReference(normalizedReference);
      if (candidate == null) {
        continue;
      }
      if (resolved != null) {
        throw new IllegalArgumentException("Ambiguous stream reference '" + normalizedReference + "' in areas '"
            + resolvedArea + "' and '" + entry.getKey() + "'; use area::streamRef");
      }
      resolved = candidate;
      resolvedArea = entry.getKey();
    }
    return resolved;
  }

  /**
   * Returns the aggregated structured outcome of the most recent run across all process areas.
   *
   * <p>
   * The returned {@link RunStatus} merges the per-unit outcomes of every area (each unit tagged with its area name) and
   * reports overall success only if every area ran without a unit failure. This lets agents detect which area and unit
   * failed in a multi-area plant without catching a {@link RuntimeException}.
   * </p>
   *
   * @return an aggregated run status across all areas
   */
  public RunStatus getRunStatus() {
    RunStatus aggregate = new RunStatus();
    boolean anyFailure = false;
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String areaName = entry.getKey();
      ProcessSystem area = entry.getValue();
      RunStatus areaStatus = area.getRunStatus();
      for (UnitRunStatus u : areaStatus.getUnits()) {
        if (u.isSuccess()) {
          aggregate.recordSuccess(u.getUnitName(), u.getUnitType(), areaName);
        } else {
          aggregate.recordFailure(u.getUnitName(), u.getUnitType(), u.getErrorMessage(), areaName);
          anyFailure = true;
        }
      }
    }
    aggregate.markComplete(!anyFailure);
    return aggregate;
  }

  /**
   * Returns the aggregated structured outcome of the most recent run as a JSON string.
   *
   * @return schema-versioned JSON describing the last run outcome across all areas
   */
  public String getRunStatusJson() {
    return getRunStatus().toJson();
  }

  /**
   * Returns the number of process systems in this model.
   *
   * @return the number of process systems
   */
  public int size() {
    return processes.size();
  }

  /**
   * Checks whether a process system with the given name exists.
   *
   * @param name the name to look up
   * @return true if a process system with that name exists
   */
  public boolean has(String name) {
    return processes.containsKey(name);
  }

  // ================================================================
  // Plant-wide capacity / bottleneck analysis (multi-area aware)
  // ================================================================

  /**
   * Returns a ranked list of all constrained units across the plant, highest utilization first.
   *
   * <p>
   * Each entry is formatted as {@code "area::unit = NN.N%"}. This supports debottlenecking sequencing: after relieving
   * the top bottleneck, the next binding constraint is already known. The underlying utilization data comes from
   * {@link #getCapacityUtilizationSummary()}.
   * </p>
   *
   * @return list of {@code "area::unit = NN.N%"} entries sorted by descending utilization
   */
  public List<String> getBottleneckRanking() {
    List<Map.Entry<String, Double>> entries = new ArrayList<Map.Entry<String, Double>>(
        getCapacityUtilizationSummary().entrySet());
    entries.sort(new java.util.Comparator<Map.Entry<String, Double>>() {
      @Override
      public int compare(Map.Entry<String, Double> a, Map.Entry<String, Double> b) {
        return Double.compare(b.getValue(), a.getValue());
      }
    });
    List<String> ranking = new ArrayList<String>();
    for (Map.Entry<String, Double> e : entries) {
      ranking.add(String.format(java.util.Locale.ROOT, "%s = %.1f%%", e.getKey(), e.getValue()));
    }
    return ranking;
  }

  /**
   * Propagates a low-flow bypass threshold to every equipment in every area of this model.
   *
   * @param threshold low-flow cutoff in kg/hr (must be &gt;= 0). Equipment whose primary inlet flow is below this value
   * auto-bypasses on the next run.
   */
  public void setSectionLowFlowThreshold(double threshold) {
    for (ProcessSystem ps : processes.values()) {
      ps.setSectionLowFlowThreshold(threshold);
    }
  }

  /**
   * Sets the low-flow bypass threshold on a single named unit. Searches every area for the unit name and applies the
   * threshold to the first match.
   *
   * @param unitName name of the unit
   * @param threshold low-flow cutoff in kg/hr (must be &gt;= 0)
   * @return true if a matching unit was found and updated, false otherwise
   */
  public boolean setSectionLowFlowThreshold(String unitName, double threshold) {
    for (ProcessSystem ps : processes.values()) {
      if (ps.hasUnitName(unitName)) {
        ps.setSectionLowFlowThreshold(unitName, threshold);
        return true;
      }
    }
    return false;
  }

  /**
   * Sets the low-flow bypass threshold on every unit in every area as a fraction of its current primary inlet flow.
   *
   * @param fraction fraction of the inlet flow used as the cutoff (must be &gt;= 0)
   * @return total number of units updated across all areas
   */
  public int setSectionLowFlowThresholdFraction(double fraction) {
    int total = 0;
    for (ProcessSystem ps : processes.values()) {
      total += ps.setSectionLowFlowThresholdFraction(fraction);
    }
    return total;
  }

  /**
   * Returns the names of bypassed units across every area, prefixed with the area name as {@code "area::unitName"} to
   * disambiguate when the same unit name appears in multiple areas.
   *
   * @return ordered list of bypassed units (may be empty)
   */
  public java.util.List<String> getBypassedUnits() {
    java.util.List<String> all = new java.util.ArrayList<String>();
    for (Map.Entry<String, ProcessSystem> e : processes.entrySet()) {
      for (String unit : e.getValue().getBypassedUnits()) {
        all.add(e.getKey() + "::" + unit);
      }
    }
    return all;
  }

  /**
   * Manually deactivates a section starting at the given unit. Searches every area for a unit with the supplied name
   * and delegates to {@link ProcessSystem#deactivateSection(String)} on the first match.
   *
   * @param unitName name of the seed unit in some area
   * @return number of units locked inactive (0 if not found)
   */
  public int deactivateSection(String unitName) {
    for (ProcessSystem ps : processes.values()) {
      if (ps.hasUnitName(unitName)) {
        return ps.deactivateSection(unitName);
      }
    }
    return 0;
  }

  /**
   * Manually deactivates a section starting at {@code areaName::unitName}.
   *
   * @param areaName name of the process area
   * @param unitName name of the seed unit within that area
   * @return number of units locked inactive (0 if area or unit not found)
   */
  public int deactivateSection(String areaName, String unitName) {
    ProcessSystem ps = processes.get(areaName);
    if (ps == null) {
      return 0;
    }
    return ps.deactivateSection(unitName);
  }

  /**
   * Re-activates a previously deactivated section starting at the given unit (first area match).
   *
   * @param unitName name of the seed unit
   * @return number of units unlocked
   */
  public int activateSection(String unitName) {
    for (ProcessSystem ps : processes.values()) {
      if (ps.hasUnitName(unitName)) {
        return ps.activateSection(unitName);
      }
    }
    return 0;
  }

  /**
   * Re-activates a previously deactivated section in a specific area.
   *
   * @param areaName name of the process area
   * @param unitName name of the seed unit within that area
   * @return number of units unlocked
   */
  public int activateSection(String areaName, String unitName) {
    ProcessSystem ps = processes.get(areaName);
    if (ps == null) {
      return 0;
    }
    return ps.activateSection(unitName);
  }

  /**
   * Re-activates every equipment in every area (clears all locked-inactive flags).
   */
  public void activateAll() {
    for (ProcessSystem ps : processes.values()) {
      ps.activateAll();
    }
  }

  /**
   * Generates IEC 81346 reference designations for all equipment across all process areas in this model. Each area
   * receives a unique function sub-level (A1, A2, A3, ...).
   *
   * <p>
   * This is a convenience wrapper around {@link neqsim.process.equipment.iec81346.ReferenceDesignationGenerator}.
   * </p>
   *
   * @param locationPrefix the location-aspect prefix (e.g. "P1" for a specific platform)
   * @return the generator instance (for further queries such as {@code toJson()})
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator generateReferenceDesignations(
      String locationPrefix) {
    neqsim.process.equipment.iec81346.ReferenceDesignationGenerator gen = new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator(
        this);
    gen.setLocationPrefix(locationPrefix);
    gen.generate();
    return gen;
  }

  /**
   * Generates IEC 81346 reference designations with hierarchical function structure. Each area receives a nested
   * function sub-level under the given prefix (e.g. "A1.A1", "A1.A2").
   *
   * @param functionPrefix the top-level function prefix (e.g. "A1")
   * @param locationPrefix the location-aspect prefix (e.g. "P1")
   * @return the generator instance
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator generateReferenceDesignations(
      String functionPrefix, String locationPrefix) {
    neqsim.process.equipment.iec81346.ReferenceDesignationGenerator gen = new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator(
        this);
    gen.setFunctionPrefix(functionPrefix);
    gen.setLocationPrefix(locationPrefix);
    gen.setUseHierarchicalFunctions(true);
    gen.generate();
    return gen;
  }

  /**
   * Looks up a process equipment unit across all process areas by its IEC 81346 reference designation string (e.g.
   * {@code "=A1.B1"}, {@code "-B1"}).
   *
   * @param refDesignation the reference designation string to match
   * @return the matching equipment, or {@code null} if not found in any area
   */
  public neqsim.process.equipment.ProcessEquipmentInterface getUnitByReferenceDesignation(String refDesignation) {
    if (refDesignation == null || refDesignation.trim().isEmpty()) {
      return null;
    }
    for (ProcessSystem system : processes.values()) {
      neqsim.process.equipment.ProcessEquipmentInterface found = system.getUnitByReferenceDesignation(refDesignation);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  /**
   * Removes a process by its name.
   *
   * @param name a {@link java.lang.String} object
   * @return a boolean
   */
  public boolean remove(String name) {
    boolean removed = processes.remove(name) != null;
    if (removed) {
      invalidateTopology();
    }
    return removed;
  }

  /**
   * Apply an acceleration method to every {@link neqsim.process.equipment.util.Recycle Recycle} unit across all areas
   * in this {@code ProcessModel}.
   *
   * <p>
   * For large multi-area plants with many recycle loops, Wegstein acceleration typically reduces outer-loop iteration
   * count by 2-3x over the default direct substitution. This is a bulk convenience that delegates to
   * {@link ProcessSystem#setRecycleAccelerationMethod(neqsim.process.equipment.util.AccelerationMethod)} on every
   * registered area.
   * </p>
   *
   * @param method acceleration method to apply (must not be {@code null})
   * @return total number of {@code Recycle} units updated across all areas
   */
  public int setRecycleAccelerationMethod(AccelerationMethod method) {
    if (method == null) {
      throw new IllegalArgumentException("AccelerationMethod must not be null");
    }
    int total = 0;
    for (ProcessSystem ps : processes.values()) {
      total += ps.setRecycleAccelerationMethod(method);
    }
    return total;
  }

  /**
   * Total change in stream exergy (outlet − inlet) aggregated over every unit operation in every process area. Each
   * area contributes using its own {@link ProcessSystem#getSurroundingTemperature() surrounding temperature}.
   *
   * @param unit energy / power unit of the returned value (J, kJ, MJ, W, kW, MW)
   * @return total exergy change in the requested unit
   */
  public double getExergyChange(String unit) {
    double totalJ = 0.0;
    for (ProcessSystem ps : processes.values()) {
      totalJ += ps.getExergyChange("J");
    }
    return convertEnergy(totalJ, unit);
  }

  /**
   * Total exergy destruction rate aggregated over every unit operation in every process area. Each area contributes
   * using its own surrounding temperature.
   *
   * @param unit energy / power unit of the returned value
   * @return total exergy destruction in the requested unit
   */
  public double getExergyDestruction(String unit) {
    double totalJ = 0.0;
    for (ProcessSystem ps : processes.values()) {
      totalJ += ps.getExergyDestruction("J");
    }
    return convertEnergy(totalJ, unit);
  }

  /**
   * Total mechanical power consumed by every compressor and pump in every process area.
   *
   * @param unit power unit of the returned value (W, kW or MW)
   * @return total shaft power in the requested unit, summed over all areas
   */
  public double getPower(String unit) {
    double power = 0.0;
    for (ProcessSystem ps : processes.values()) {
      power += ps.getPower(unit);
    }
    return power;
  }

  /**
   * Total cooling duty of every cooler in every process area.
   *
   * @param unit power unit of the returned value (W, kW or MW)
   * @return total cooler duty in the requested unit, summed over all areas
   */
  public double getCoolerDuty(String unit) {
    double duty = 0.0;
    for (ProcessSystem ps : processes.values()) {
      duty += ps.getCoolerDuty(unit);
    }
    return duty;
  }

  /**
   * Total heating duty of every heater in every process area.
   *
   * @param unit power unit of the returned value (W, kW or MW)
   * @return total heater duty in the requested unit, summed over all areas
   */
  public double getHeaterDuty(String unit) {
    double duty = 0.0;
    for (ProcessSystem ps : processes.values()) {
      duty += ps.getHeaterDuty(unit);
    }
    return duty;
  }

  /**
   * Total entropy production aggregated over every unit operation in every process area.
   *
   * @param unit entropy-rate unit of the returned value (e.g. "J/K")
   * @return total entropy production in the requested unit, summed over all areas
   */
  public double getEntropyProduction(String unit) {
    double entropyProduction = 0.0;
    for (ProcessSystem ps : processes.values()) {
      entropyProduction += ps.getEntropyProduction(unit);
    }
    return entropyProduction;
  }

  /**
   * Build a structured {@link neqsim.process.util.exergy.ExergyAnalysisReport} covering every unit operation in every
   * process area, with each entry tagged by its area name. The surrounding temperature of the report is taken from the
   * first registered area (or 288.15 K if the model is empty).
   *
   * @return a new report suitable for ranking destruction hot-spots across a plant-wide flowsheet
   */
  public neqsim.process.util.exergy.ExergyAnalysisReport getExergyAnalysis() {
    double t0 = 288.15;
    if (!processes.isEmpty()) {
      t0 = processes.values().iterator().next().getSurroundingTemperature();
    }
    neqsim.process.util.exergy.ExergyAnalysisReport report = new neqsim.process.util.exergy.ExergyAnalysisReport(t0);
    for (Map.Entry<String, ProcessSystem> e : processes.entrySet()) {
      e.getValue().populateExergyAnalysis(report, e.getValue().getSurroundingTemperature(), e.getKey());
    }
    return report;
  }

  /**
   * Convert Joules to the requested energy / power unit.
   *
   * @param valueJ value in Joules (treated identically to watts for rate quantities)
   * @param unit target unit (J, kJ, MJ, W, kW, MW)
   * @return converted value
   */
  private static double convertEnergy(double valueJ, String unit) {
    if (unit == null) {
      return valueJ;
    }
    if ("J".equals(unit) || "W".equals(unit)) {
      return valueJ;
    }
    if ("kJ".equals(unit) || "kW".equals(unit)) {
      return valueJ / 1.0e3;
    }
    if ("MJ".equals(unit) || "MW".equals(unit)) {
      return valueJ / 1.0e6;
    }
    return valueJ;
  }

  /**
   * Advances every registered {@link ProcessSystem} by a single transient step of size {@code dt}.
   *
   * <p>
   * Areas are stepped in insertion order. Any {@link EventScheduler} previously installed via
   * {@link #setEventScheduler(EventScheduler)} is propagated to each child {@code ProcessSystem} before stepping, so a
   * single scheduler can coordinate events across all areas. All area clocks must be finite and aligned before the
   * step; a mismatch fails before any area or shared event state changes.
   * </p>
   *
   * @param dt finite timestep size in seconds (must be {@code > 0})
   * @param id calculation UUID forwarded to each child {@code ProcessSystem.runTransient}
   * @throws IllegalArgumentException if {@code dt} is non-finite or not greater than zero
   * @throws IllegalStateException if child process-area clocks are non-finite or not aligned
   */
  public void runTransient(double dt, UUID id) {
    ProcessSystem.validateTransientTimestep(dt);
    validateTransientAreaTimes();
    for (ProcessSystem area : processes.values()) {
      area.runTransient(dt, id);
    }
  }

  /**
   * Audits aggregate identity-preserving transient transaction coverage across all process areas.
   *
   * <p>
   * Counts are summed over area-local unique process elements. Blocking diagnostics are qualified by area name so
   * duplicate equipment names in different areas remain distinguishable.
   * </p>
   *
   * @return immutable aggregate coverage report
   */
  public TransientTransactionCoverage getTransientTransactionCoverage() {
    int elementCount = 0;
    int participantCount = 0;
    List<String> blockingIssues = new ArrayList<String>();
    Set<String> modelEventStateIdentities = new java.util.LinkedHashSet<String>();
    for (ProcessSystem processSystem : processes.values()) {
      modelEventStateIdentities.addAll(processSystem.getCompleteTransientStateIdentities());
    }
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      TransientTransactionCoverage areaCoverage = entry.getValue()
          .getTransientTransactionCoverage(modelEventStateIdentities);
      elementCount += areaCoverage.getProcessElementCount();
      participantCount += areaCoverage.getParticipantCount();
      for (String issue : areaCoverage.getBlockingIssues()) {
        blockingIssues.add("process area '" + entry.getKey() + "': " + issue);
      }
    }
    return new TransientTransactionCoverage(elementCount, participantCount, blockingIssues);
  }

  /**
   * Captures one coordinated rollback point across all process areas.
   *
   * <p>
   * Area clocks and complete coverage are validated before the first area transaction is opened. Area transactions are
   * captured in insertion order and rolled back in reverse order, preserving shared boundary-object identities and
   * deterministic replay order.
   * </p>
   *
   * @return open multi-area transaction
   * @throws IllegalStateException if area clocks are misaligned, coverage is incomplete, or another model transaction
   * is open
   */
  public synchronized TransientStepTransaction beginTransientStepTransaction() {
    if (activeTransientStepTransaction != null && activeTransientStepTransaction.isOpen()) {
      throw new IllegalStateException("A transient step transaction is already open for this ProcessModel");
    }
    validateTransientAreaTimes();
    getTransientTransactionCoverage().assertComplete();
    Set<String> modelEventStateIdentities = new java.util.LinkedHashSet<String>();
    for (ProcessSystem processSystem : processes.values()) {
      modelEventStateIdentities.addAll(processSystem.getCompleteTransientStateIdentities());
    }

    List<AreaTransientCheckpoint> areaCheckpoints = new ArrayList<AreaTransientCheckpoint>();
    try {
      for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
        areaCheckpoints.add(new AreaTransientCheckpoint(entry.getKey(), entry.getValue(),
            entry.getValue().beginTransientStepTransaction(modelEventStateIdentities)));
      }
    } catch (RuntimeException ex) {
      rollbackOpenAreaTransactions(areaCheckpoints, ex);
      throw ex;
    }

    ProcessModelStepTransaction transaction = new ProcessModelStepTransaction(areaCheckpoints);
    activeTransientStepTransaction = transaction;
    return transaction;
  }

  /**
   * Advances every process area and accepts the common physical step only if all areas succeed.
   *
   * <p>
   * A failure in any later area restores already-advanced earlier areas in place. The shared event scheduler
   * bookkeeping is restored with the same object identity by the area transactions.
   * </p>
   *
   * @param dt finite timestep in seconds
   * @param id common physical-step calculation identifier
   * @throws IllegalStateException if transaction coverage is incomplete
   */
  public void runTransientTransactional(double dt, UUID id) {
    try (TransientStepTransaction transaction = beginTransientStepTransaction()) {
      runTransient(dt, id);
      transaction.commit();
    }
  }

  /**
   * Rolls back open child transactions in reverse area order.
   *
   * @param areaCheckpoints child transactions captured so far
   * @param primary primary failure receiving suppressed rollback failures
   */
  private static void rollbackOpenAreaTransactions(List<AreaTransientCheckpoint> areaCheckpoints,
      RuntimeException primary) {
    for (int i = areaCheckpoints.size() - 1; i >= 0; i--) {
      TransientStepTransaction transaction = areaCheckpoints.get(i).transaction;
      if (transaction.isOpen()) {
        try {
          transaction.rollback();
        } catch (RuntimeException rollbackFailure) {
          primary.addSuppressed(rollbackFailure);
        }
      }
    }
  }

  /** Captured child-area transaction and identity. */
  private static final class AreaTransientCheckpoint {
    private final String areaName;
    private final ProcessSystem processSystem;
    private final TransientStepTransaction transaction;

    private AreaTransientCheckpoint(String areaName, ProcessSystem processSystem,
        TransientStepTransaction transaction) {
      this.areaName = areaName;
      this.processSystem = processSystem;
      this.transaction = transaction;
    }
  }

  /** Coordinated multi-area transaction implementation. */
  private final class ProcessModelStepTransaction implements TransientStepTransaction {
    private final List<AreaTransientCheckpoint> areaCheckpoints;
    private Status status = Status.OPEN;

    private ProcessModelStepTransaction(List<AreaTransientCheckpoint> areaCheckpoints) {
      this.areaCheckpoints = new ArrayList<AreaTransientCheckpoint>(areaCheckpoints);
    }

    /** {@inheritDoc} */
    @Override
    public void prepareCommit() {
      synchronized (ProcessModel.this) {
        requireOpen("prepare commit");
        RuntimeException failure = validateAreaIdentities();
        if (failure == null) {
          for (AreaTransientCheckpoint checkpoint : areaCheckpoints) {
            try {
              checkpoint.transaction.prepareCommit();
            } catch (RuntimeException ex) {
              failure = appendFailure(failure,
                  "Failed to prepare transient transaction for process area '" + checkpoint.areaName + "'", ex);
            }
          }
        }
        if (failure != null) {
          throw failure;
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public void commit() {
      synchronized (ProcessModel.this) {
        try {
          prepareCommit();
        } catch (RuntimeException validationFailure) {
          try {
            rollback();
          } catch (RuntimeException rollbackFailure) {
            validationFailure.addSuppressed(rollbackFailure);
          }
          throw validationFailure;
        }

        RuntimeException failure = null;
        for (AreaTransientCheckpoint checkpoint : areaCheckpoints) {
          try {
            checkpoint.transaction.commit();
          } catch (RuntimeException ex) {
            failure = appendFailure(failure,
                "Failed to commit transient transaction for process area '" + checkpoint.areaName + "'", ex);
            break;
          }
        }
        if (failure != null) {
          for (int i = areaCheckpoints.size() - 1; i >= 0; i--) {
            TransientStepTransaction child = areaCheckpoints.get(i).transaction;
            if (child.isOpen()) {
              try {
                child.rollback();
              } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
              }
            }
          }
          status = Status.ROLLED_BACK;
          activeTransientStepTransaction = null;
          throw failure;
        }
        status = Status.COMMITTED;
        activeTransientStepTransaction = null;
      }
    }

    /** {@inheritDoc} */
    @Override
    public void rollback() {
      synchronized (ProcessModel.this) {
        if (status == Status.ROLLED_BACK) {
          return;
        }
        requireOpen("rollback");
        RuntimeException failure = validateAreaIdentities();
        for (int i = areaCheckpoints.size() - 1; i >= 0; i--) {
          AreaTransientCheckpoint checkpoint = areaCheckpoints.get(i);
          try {
            checkpoint.transaction.rollback();
          } catch (RuntimeException ex) {
            failure = appendFailure(failure,
                "Failed to roll back transient transaction for process area '" + checkpoint.areaName + "'", ex);
          }
        }
        status = Status.ROLLED_BACK;
        activeTransientStepTransaction = null;
        if (failure != null) {
          throw failure;
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public Status getStatus() {
      return status;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
      if (isOpen()) {
        rollback();
      }
    }

    /**
     * Verifies area-name, insertion-order, and process-system object identities.
     *
     * @return failure diagnostic, or {@code null}
     */
    private RuntimeException validateAreaIdentities() {
      if (processes.size() != areaCheckpoints.size()) {
        return new IllegalStateException("ProcessModel area structure changed during transient transaction: captured "
            + areaCheckpoints.size() + " areas but found " + processes.size());
      }
      int index = 0;
      for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
        AreaTransientCheckpoint checkpoint = areaCheckpoints.get(index);
        if (!checkpoint.areaName.equals(entry.getKey()) || checkpoint.processSystem != entry.getValue()) {
          return new IllegalStateException(
              "ProcessModel area identity or insertion order changed during transient transaction at index " + index);
        }
        index++;
      }
      return null;
    }

    /**
     * Enforces single-use transaction semantics.
     *
     * @param operation requested operation
     */
    private void requireOpen(String operation) {
      if (status != Status.OPEN) {
        throw new IllegalStateException(
            "Cannot " + operation + " ProcessModel transient transaction in state " + status);
      }
    }
  }

  /**
   * Accumulates multi-area transaction failures while allowing later rollback work to continue.
   *
   * @param existing first failure, or {@code null}
   * @param message diagnostic context
   * @param cause new failure
   * @return first failure with later failures suppressed
   */
  private static RuntimeException appendFailure(RuntimeException existing, String message, RuntimeException cause) {
    RuntimeException wrapped = new IllegalStateException(message, cause);
    if (existing == null) {
      return wrapped;
    }
    existing.addSuppressed(wrapped);
    return existing;
  }

  /**
   * Verifies that every process area starts a model-level transient step on the same finite simulation clock.
   *
   * <p>
   * The preflight is deliberately completed before the first area advances. Otherwise a shared event scheduler could be
   * evaluated after an early area has already run but before a later area runs, applying one event to only part of the
   * model during a nominally common timestep.
   * </p>
   *
   * @throws IllegalStateException if an area clock is non-finite or differs materially from the first area clock
   */
  private void validateTransientAreaTimes() {
    Map.Entry<String, ProcessSystem> referenceEntry = null;
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      double currentTime = entry.getValue().getTime();
      if (!Double.isFinite(currentTime)) {
        throw new IllegalStateException(
            "ProcessModel transient area '" + entry.getKey() + "' has non-finite simulation time " + currentTime
                + " s; reset or synchronize area clocks before runTransient");
      }
      if (referenceEntry == null) {
        referenceEntry = entry;
        continue;
      }

      double referenceTime = referenceEntry.getValue().getTime();
      double difference = Math.abs(currentTime - referenceTime);
      double scale = Math.max(Math.abs(referenceTime), Math.abs(currentTime));
      double tolerance = Math.max(TRANSIENT_AREA_TIME_ABSOLUTE_TOLERANCE_SECONDS,
          TRANSIENT_AREA_TIME_RELATIVE_TOLERANCE * scale);
      if (difference > tolerance) {
        throw new IllegalStateException("ProcessModel transient areas must have aligned clocks before stepping: area '"
            + referenceEntry.getKey() + "' is at " + referenceTime + " s while area '" + entry.getKey() + "' is at "
            + currentTime + " s (difference " + difference + " s, tolerance " + tolerance
            + " s); reset or synchronize area clocks before runTransient");
      }
    }
  }

  /**
   * Returns the {@link EventScheduler} currently attached to this model. Returns the scheduler of the first child area,
   * or {@code null} if no schedulers are attached.
   *
   * @return event scheduler or {@code null}
   */
  public EventScheduler getEventScheduler() {
    for (ProcessSystem area : processes.values()) {
      EventScheduler s = area.getEventScheduler();
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  /**
   * Attaches an {@link EventScheduler} to every child {@link ProcessSystem}, so events scheduled on the shared
   * scheduler will fire during any area's transient step. Pass {@code null} to detach from all child areas.
   *
   * @param scheduler scheduler instance, or {@code null} to detach
   */
  public void setEventScheduler(EventScheduler scheduler) {
    for (ProcessSystem area : processes.values()) {
      area.setEventScheduler(scheduler);
    }
  }

  /**
   * Sets the same {@link IntegratorStrategy} on every child {@link ProcessSystem}.
   *
   * @param strategy integrator strategy ({@code null} restores the default explicit Euler)
   */
  public void setIntegratorStrategy(IntegratorStrategy strategy) {
    for (ProcessSystem area : processes.values()) {
      area.setIntegratorStrategy(strategy);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * - If runStep == true, each process is run in "step" mode exactly once. - Otherwise (continuous mode), it loops up
   * to maxIterations or until all processes are finished (isFinished() == true). If forceIteration is true, the loop
   * runs all maxIterations regardless of convergence.
   * </p>
   *
   * <p>
   * When {@link #isUseOptimizedExecution()} is true (default), each ProcessSystem uses
   * {@link ProcessSystem#runOptimized()} for best performance.
   * </p>
   */
  @Override
  public void run() {
    int totalAreas = processes.size();

    // Publish model-start event and notify listener
    notifyModelStart(totalAreas);
    publishModelEvent(ProcessEvent.EventType.INFO, "ProcessModel starting with " + totalAreas + " process areas",
        ProcessEvent.Severity.INFO);

    // Auto-validate all ProcessSystems before first iteration
    if (autoValidate) {
      runModelAutoValidation();
    }

    if (runStep) {
      AreaExecutionPlan areaPlan = getAreaExecutionPlan();
      lastIterationCount = 1;
      modelConverged = true;
      lastMaxFlowError = 0.0;
      lastMaxTemperatureError = 0.0;
      lastMaxPressureError = 0.0;
      lastAllProcessesSolved = true;
      lastBoundaryValuesConverged = true;
      lastBoundaryStreamCount = areaPlan.boundaryStreams.size();
      lastBoundaryStreamErrors = new ArrayList<>();
      runAllProcessStepsWithHooks(1);
      notifyModelComplete(1, true);
      publishModelEvent(ProcessEvent.EventType.SIMULATION_COMPLETE, "ProcessModel step mode completed",
          ProcessEvent.Severity.INFO);
    } else {
      boolean previouslyConverged = modelConverged;
      // Reset convergence tracking
      lastIterationCount = 0;
      modelConverged = false;
      lastMaxFlowError = Double.MAX_VALUE;
      lastMaxTemperatureError = Double.MAX_VALUE;
      lastMaxPressureError = Double.MAX_VALUE;
      lastAllProcessesSolved = false;
      lastBoundaryValuesConverged = false;
      lastBoundaryStreamCount = 0;
      lastBoundaryStreamErrors = new ArrayList<>();

      // Capture initial stream states for convergence tracking. Restrict to
      // streams that cross area boundaries - these are the only streams whose
      // values change between outer iterations. For a 500-stream plant with
      // 10 boundary streams this cuts capture cost by ~50x.
      AreaExecutionPlan areaPlan = getAreaExecutionPlan();
      java.util.Set<Object> boundaryStreams = areaPlan.boundaryStreams;
      lastBoundaryStreamCount = boundaryStreams.size();
      Map<Object, double[]> previousBoundaryStreamStates = captureBoundaryStreamStates(boundaryStreams);
      java.util.Set<ProcessSystem> dirtyAreas = null;
      resetAutoTuningRunState();
      applyAutoDefaultTolerance();
      // A converged model already has populated internal streams, so repeated execution can
      // safely apply automatic thresholds before the first area pass. Cold execution and
      // observable lifecycle-hook runs retain the post-pass tuning/confirmation behaviour.
      if (previouslyConverged && useIncrementalAreaExecution && progressListener == null && !publishEvents) {
        applyAutoConvergenceTuning();
      }

      int iterations = 0;
      while (!Thread.currentThread().isInterrupted() && iterations < maxIterations) {
        // Notify before-iteration
        notifyBeforeIteration(iterations + 1);

        // Run all processes - use parallel execution for independent systems
        runAllProcessesWithHooks(iterations + 1, dirtyAreas, areaPlan);
        iterations++;

        // Capture current stream states and calculate errors
        Map<Object, double[]> currentBoundaryStreamStates = captureBoundaryStreamStates(boundaryStreams);
        boolean autoTuningChanged = applyAutoConvergenceTuning();
        double[] errors = calculateConvergenceErrors(previousBoundaryStreamStates, currentBoundaryStreamStates,
            areaPlan);
        java.util.Set<Object> changedBoundaryStreams = findChangedBoundaryStreams(previousBoundaryStreamStates,
            currentBoundaryStreamStates);
        lastMaxFlowError = errors[0];
        lastMaxTemperatureError = errors[1];
        lastMaxPressureError = errors[2];

        // Check if model has converged
        boolean allProcessesSolved = isFinished();
        boolean valuesConverged = lastMaxFlowError < flowTolerance && lastMaxTemperatureError < temperatureTolerance
            && lastMaxPressureError < pressureTolerance;
        if (!valuesConverged && relaxToleranceIfStalled()) {
          valuesConverged = lastMaxFlowError < flowTolerance && lastMaxTemperatureError < temperatureTolerance
              && lastMaxPressureError < pressureTolerance;
        }
        lastAllProcessesSolved = allProcessesSolved;
        lastBoundaryValuesConverged = valuesConverged;

        if (logger.isDebugEnabled()) {
          logger.debug("Iteration " + iterations + ": flowErr=" + lastMaxFlowError + ", tempErr="
              + lastMaxTemperatureError + ", pressErr=" + lastMaxPressureError + ", allSolved=" + allProcessesSolved
              + ", valuesConverged=" + valuesConverged);
        }

        double maxError = getError();

        // Notify iteration complete
        boolean boundaryDrivenModel = !boundaryStreams.isEmpty();
        boolean minimumIterationsMet = boundaryStreams.isEmpty() || iterations > 1;
        boolean iterConverged = valuesConverged && minimumIterationsMet && (allProcessesSolved || boundaryDrivenModel)
            && !autoTuningChanged;
        if (iterConverged && !massClosureAccepted()) {
          iterConverged = false;
        }
        notifyIterationComplete(iterations, iterConverged, maxError);

        // Converged if all processes solved AND values are not changing
        if (iterConverged) {
          modelConverged = true;
          logger.debug("ProcessModel converged after " + iterations + " iterations");
          break;
        }

        // Update previous states for next iteration
        previousBoundaryStreamStates = currentBoundaryStreamStates;
        dirtyAreas = autoTuningChanged ? null : getDirtyAreasForNextIteration(areaPlan, changedBoundaryStreams);
      }
      lastIterationCount = iterations;

      // A run that never reached the acceptance test still has to report its mass closure,
      // otherwise an internal mass source stays invisible behind a max-iterations warning.
      if (!modelConverged) {
        massClosureAccepted();
      }

      if (!modelConverged && iterations >= maxIterations) {
        logger.warn("ProcessModel reached max iterations (" + maxIterations + ") without full convergence. Flow error: "
            + lastMaxFlowError + formatWorstStreamSuffix("flow") + ", Temp error: " + lastMaxTemperatureError
            + formatWorstStreamSuffix("temperature"));
        publishModelEvent(ProcessEvent.EventType.WARNING, "ProcessModel did not converge after " + maxIterations
            + " iterations. Max error: " + String.format("%.2e", getError()), ProcessEvent.Severity.WARNING);
      }

      notifyModelComplete(lastIterationCount, modelConverged);
      publishModelEvent(
          ProcessEvent.EventType.SIMULATION_COMPLETE, "ProcessModel completed: "
              + (modelConverged ? "CONVERGED" : "NOT CONVERGED") + " after " + lastIterationCount + " iterations",
          ProcessEvent.Severity.INFO);
    }
  }

  /**
   * Runs all ProcessSystems, using parallel execution for independent systems.
   *
   * <p>
   * If there are multiple independent ProcessSystems (no shared streams between them), they are executed concurrently
   * using the NeqSim thread pool. Systems that depend on each other are executed sequentially in insertion order.
   * </p>
   */
  private void runAllProcesses() {
    runAllProcesses(null);
  }

  /**
   * Runs runnable ProcessSystems, using parallel execution for independent systems.
   *
   * @param runnableAreas process areas to run, or {@code null} to run every area
   */
  private void runAllProcesses(java.util.Set<ProcessSystem> runnableAreas) {
    runAllProcesses(runnableAreas, getAreaExecutionPlan());
  }

  /**
   * Runs runnable ProcessSystems using a caller-supplied area execution plan.
   *
   * @param runnableAreas process areas to run, or {@code null} to run every area
   * @param areaPlan cached inter-area execution plan
   */
  private void runAllProcesses(java.util.Set<ProcessSystem> runnableAreas, AreaExecutionPlan areaPlan) {
    if (processes.size() <= 1) {
      // Single process - run directly, no parallelism overhead
      for (ProcessSystem process : processes.values()) {
        if (shouldRunArea(process, runnableAreas)) {
          runSingleProcess(process);
        }
      }
      return;
    }

    // Partition processes into levels based on inter-area stream dependencies.
    // Areas at the same level are independent and can run in parallel; later
    // levels run after their predecessors complete. This generalises the
    // previous all-or-nothing logic: a 6-area plant with 4 independent areas
    // and one producer→consumer pair now parallelises the 4 (plus the pair
    // serialised) instead of falling back to fully sequential.
    List<List<ProcessSystem>> levels = areaPlan.levels;
    for (List<ProcessSystem> level : levels) {
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      List<ProcessSystem> activeLevel = filterRunnableAreas(level, runnableAreas);
      if (activeLevel.isEmpty()) {
        continue;
      }
      if (activeLevel.size() == 1) {
        runSingleProcess(activeLevel.get(0));
      } else if (shouldPreserveInnerParallelism(activeLevel)) {
        for (ProcessSystem process : activeLevel) {
          runSingleProcess(process, true);
        }
      } else {
        List<Future<?>> futures = new ArrayList<>();
        for (ProcessSystem process : activeLevel) {
          final ProcessSystem proc = process;
          final boolean allowInnerParallel = !preventNestedParallelExecution;
          futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
            runSingleProcess(proc, allowInnerParallel);
          }));
        }
        waitForFutures(futures);
      }
    }
  }

  /**
   * Runs a single ProcessSystem using the configured execution strategy.
   *
   * @param process the process to run
   */
  private void runSingleProcess(ProcessSystem process) {
    runSingleProcess(process, true);
  }

  /**
   * Runs a single ProcessSystem with optional child-level parallelism.
   *
   * @param process the process to run
   * @param allowInnerParallelExecution true to allow the child ProcessSystem to choose optimized parallel/hybrid
   * execution, false to force sequential child execution for this run
   */
  private void runSingleProcess(ProcessSystem process, boolean allowInnerParallelExecution) {
    // Skip areas whose units are all bypassed (manually locked or auto-bypassed via
    // low-flow detection). The inner convergence loop in ProcessSystem.run() would do
    // no useful work but still pay the iteration / event / state-snapshot overhead.
    if (isFullyBypassed(process)) {
      return;
    }
    boolean previousOptimizedExecution = process.isUseOptimizedExecution();
    try {
      process.setUseOptimizedExecution(useOptimizedExecution && allowInnerParallelExecution);
      process.run();
    } catch (Exception e) {
      logger.error("Error running process " + process.getName() + ": " + e.getMessage(), e);
    } finally {
      process.setUseOptimizedExecution(previousOptimizedExecution);
    }
  }

  /**
   * Returns true when every unit in the given process is currently bypassed (manually locked inactive or auto-bypassed
   * via low-flow detection). Such areas have no work to do and may be skipped by
   * {@link #runSingleProcess(ProcessSystem, boolean)} without affecting results.
   *
   * @param process the process area to inspect
   * @return true if there is at least one unit and all of them are bypassed
   */
  private boolean isFullyBypassed(ProcessSystem process) {
    int total = process.getUnitOperations().size();
    if (total == 0) {
      return false;
    }
    return process.getBypassedUnits().size() == total;
  }

  /**
   * Runs all ProcessSystems once in step mode using insertion order.
   */
  private void runAllProcessSteps() {
    for (ProcessSystem process : processes.values()) {
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      runSingleProcessStep(process);
    }
  }

  /**
   * Runs a single ProcessSystem once in step mode.
   *
   * <p>
   * Areas flagged with {@link ProcessSystem#setSolveFullyInModelStep(boolean)} are fully converged (recycles included)
   * instead of advancing a single pass, allowing selected sub-processes to reach a consistent state on every model step
   * while the rest of the plant single-steps.
   * </p>
   *
   * @param process the process to run in step mode
   */
  private void runSingleProcessStep(ProcessSystem process) {
    try {
      if (process.isSolveFullyInModelStep()) {
        process.run();
      } else {
        process.run_step();
      }
    } catch (Exception e) {
      logger.error("Error running process step " + process.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Runs all ProcessSystems in step mode with optional progress hooks.
   *
   * @param iterationNumber step-mode iteration number reported to progress hooks
   */
  private void runAllProcessStepsWithHooks(int iterationNumber) {
    if (progressListener == null && !publishEvents) {
      runAllProcessSteps();
      return;
    }

    int totalAreas = processes.size();
    int areaIdx = 0;
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Thread was interrupted, exiting run()...");
          return;
        }
        notifyBeforeProcessArea(entry.getKey(), entry.getValue(), areaIdx, totalAreas, iterationNumber);
        if (entry.getValue().isSolveFullyInModelStep()) {
          entry.getValue().run();
        } else {
          entry.getValue().run_step();
        }
        notifyProcessAreaComplete(entry.getKey(), entry.getValue(), areaIdx, totalAreas, iterationNumber);
      } catch (Exception e) {
        logger.error("Error running process step: " + e.getMessage(), e);
        publishModelEvent(ProcessEvent.EventType.ERROR,
            "Error in process area '" + entry.getKey() + "': " + e.getMessage(), ProcessEvent.Severity.ERROR);
        if (!notifyProcessAreaError(entry.getKey(), entry.getValue(), e)) {
          break;
        }
      }
      areaIdx++;
    }
  }

  /**
   * Checks whether a process area should run in the current outer iteration.
   *
   * @param process process area to check
   * @param runnableAreas process areas selected for execution, or {@code null} for all areas
   * @return true if the area should be run
   */
  private boolean shouldRunArea(ProcessSystem process, java.util.Set<ProcessSystem> runnableAreas) {
    return runnableAreas == null || runnableAreas.contains(process);
  }

  /**
   * Filters a level down to areas selected for execution.
   *
   * @param level process areas in one execution level
   * @param runnableAreas process areas selected for execution, or {@code null} for all areas
   * @return active areas from the level in original order
   */
  private List<ProcessSystem> filterRunnableAreas(List<ProcessSystem> level,
      java.util.Set<ProcessSystem> runnableAreas) {
    if (runnableAreas == null) {
      return level;
    }
    List<ProcessSystem> activeLevel = new ArrayList<>();
    for (ProcessSystem process : level) {
      if (runnableAreas.contains(process)) {
        activeLevel.add(process);
      }
    }
    return activeLevel;
  }

  /**
   * Runs all ProcessSystems with listener hooks, firing before/after area callbacks sequentially. For dependent
   * processes (shared streams), runs sequentially with hooks. For independent processes without a listener, delegates
   * to the parallel strategy.
   *
   * @param iterationNumber current outer iteration number (starts at 1)
   */
  private void runAllProcessesWithHooks(int iterationNumber) {
    runAllProcessesWithHooks(iterationNumber, null);
  }

  /**
   * Runs all ProcessSystems with listener hooks and optional dirty-area filtering.
   *
   * @param iterationNumber current outer iteration number (starts at 1)
   * @param runnableAreas process areas to run, or {@code null} to run every area
   */
  private void runAllProcessesWithHooks(int iterationNumber, java.util.Set<ProcessSystem> runnableAreas) {
    runAllProcessesWithHooks(iterationNumber, runnableAreas, getAreaExecutionPlan());
  }

  /**
   * Runs all ProcessSystems with listener hooks using a caller-supplied area plan.
   *
   * @param iterationNumber current outer iteration number (starts at 1)
   * @param runnableAreas process areas to run, or {@code null} to run every area
   * @param areaPlan cached inter-area execution plan
   */
  private void runAllProcessesWithHooks(int iterationNumber, java.util.Set<ProcessSystem> runnableAreas,
      AreaExecutionPlan areaPlan) {
    int totalAreas = processes.size();

    // If no listener is attached and events disabled, delegate to the
    // parallel-aware method
    if (progressListener == null && !publishEvents) {
      runAllProcesses(runnableAreas, areaPlan);
      return;
    }

    // Build area execution levels. Areas on the same level run in parallel;
    // consecutive levels run sequentially. Hooks fire before/after each area.
    List<List<ProcessSystem>> levels = areaPlan.levels;
    // Map each ProcessSystem to its insertion-order index for hook indexing.
    Map<ProcessSystem, Integer> areaIndex = new java.util.IdentityHashMap<>();
    Map<ProcessSystem, String> areaName = new java.util.IdentityHashMap<>();
    {
      int idx = 0;
      for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
        areaIndex.put(entry.getValue(), idx);
        areaName.put(entry.getValue(), entry.getKey());
        idx++;
      }
    }

    for (List<ProcessSystem> level : levels) {
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      List<ProcessSystem> activeLevel = level;
      // Fire "before" hooks for all areas in this level first (main thread)
      for (ProcessSystem process : activeLevel) {
        notifyBeforeProcessArea(areaName.get(process), process, areaIndex.get(process), totalAreas, iterationNumber);
      }
      if (activeLevel.size() == 1) {
        ProcessSystem process = activeLevel.get(0);
        try {
          runSingleProcess(process);
          notifyProcessAreaComplete(areaName.get(process), process, areaIndex.get(process), totalAreas,
              iterationNumber);
        } catch (Exception e) {
          publishModelEvent(ProcessEvent.EventType.ERROR,
              "Error in process area '" + areaName.get(process) + "': " + e.getMessage(), ProcessEvent.Severity.ERROR);
          if (!notifyProcessAreaError(areaName.get(process), process, e)) {
            return;
          }
        }
      } else if (shouldPreserveInnerParallelism(activeLevel)) {
        for (ProcessSystem process : activeLevel) {
          try {
            runSingleProcess(process, true);
            notifyProcessAreaComplete(areaName.get(process), process, areaIndex.get(process), totalAreas,
                iterationNumber);
          } catch (Exception e) {
            publishModelEvent(ProcessEvent.EventType.ERROR,
                "Error in process area '" + areaName.get(process) + "': " + e.getMessage(),
                ProcessEvent.Severity.ERROR);
            if (!notifyProcessAreaError(areaName.get(process), process, e)) {
              return;
            }
          }
        }
      } else {
        List<Future<?>> futures = new ArrayList<>();
        for (ProcessSystem process : activeLevel) {
          final ProcessSystem proc = process;
          final boolean allowInnerParallel = !preventNestedParallelExecution;
          futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
            runSingleProcess(proc, allowInnerParallel);
          }));
        }
        waitForFutures(futures);
        // Fire "after" hooks for the completed level
        for (ProcessSystem process : activeLevel) {
          notifyProcessAreaComplete(areaName.get(process), process, areaIndex.get(process), totalAreas,
              iterationNumber);
        }
      }
    }
  }

  /**
   * Determines whether a parallel area level should preserve child ProcessSystem parallelism.
   *
   * @param activeLevel process areas ready to run at the same model dependency level
   * @return true if the level should run areas sequentially with child optimized execution enabled
   */
  private boolean shouldPreserveInnerParallelism(List<ProcessSystem> activeLevel) {
    if (!useAdaptiveModelParallelism || !preventNestedParallelExecution || !useOptimizedExecution || activeLevel == null
        || activeLevel.size() <= 1) {
      return false;
    }
    int maxInnerParallelism = 1;
    int innerParallelismScore = 0;
    for (ProcessSystem process : activeLevel) {
      int estimatedParallelism = estimateInnerParallelism(process);
      maxInnerParallelism = Math.max(maxInnerParallelism, estimatedParallelism);
      innerParallelismScore += Math.max(0, estimatedParallelism - 1);
    }
    return maxInnerParallelism > activeLevel.size() && innerParallelismScore >= activeLevel.size();
  }

  /**
   * Estimates useful child-level parallelism for a ProcessSystem.
   *
   * @param process process area to inspect
   * @return estimated maximum child parallelism, with one as the conservative floor
   */
  private int estimateInnerParallelism(ProcessSystem process) {
    if (process == null || process.hasAdjusters()) {
      return 1;
    }
    try {
      neqsim.process.processmodel.graph.ProcessGraph.ParallelPartition partition = process.getParallelPartition();
      if (partition == null) {
        return 1;
      }
      return Math.max(1, partition.getMaxParallelism());
    } catch (Exception exception) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not estimate inner parallelism for process " + process.getName(), exception);
      }
      return 1;
    }
  }

  /**
   * Finds groups of independent ProcessSystems that can run in parallel.
   *
   * <p>
   * Two ProcessSystems are dependent if any outlet stream of one is used as an inlet stream of another. Independent
   * systems have no shared stream references.
   * </p>
   *
   * @return list of groups, where systems within each group are independent of each other
   */
  private List<List<ProcessSystem>> findIndependentProcessGroups() {
    List<ProcessSystem> allProcesses = new ArrayList<>(processes.values());

    if (allProcesses.size() <= 1) {
      List<List<ProcessSystem>> result = new ArrayList<>();
      result.add(allProcesses);
      return result;
    }

    // Collect all stream objects for each process
    List<java.util.Set<Object>> processStreams = new ArrayList<>();
    for (ProcessSystem process : allProcesses) {
      java.util.Set<Object> streams = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (Object unit : process.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          streams.add(unit);
        }
      }
      processStreams.add(streams);
    }

    // Check if any two processes share stream objects
    boolean hasSharedStreams = false;
    for (int i = 0; i < allProcesses.size() && !hasSharedStreams; i++) {
      for (int j = i + 1; j < allProcesses.size() && !hasSharedStreams; j++) {
        for (Object stream : processStreams.get(i)) {
          if (processStreams.get(j).contains(stream)) {
            hasSharedStreams = true;
            break;
          }
        }
      }
    }

    List<List<ProcessSystem>> result = new ArrayList<>();
    if (!hasSharedStreams) {
      // All independent - single group with all processes
      result.add(allProcesses);
    } else {
      // Has dependencies - each process is its own group (sequential execution)
      for (ProcessSystem process : allProcesses) {
        List<ProcessSystem> single = new ArrayList<>();
        single.add(process);
        result.add(single);
      }
    }
    return result;
  }

  /**
   * Gets cached inter-area execution levels, rebuilding only after topology invalidation.
   *
   * @return ordered list of execution levels; each level contains areas that can run in parallel
   */
  private List<List<ProcessSystem>> getAreaExecutionLevels() {
    return getAreaExecutionPlan().levels;
  }

  /**
   * Gets the cached inter-area execution plan.
   *
   * @return cached execution plan for the current model topology
   */
  private AreaExecutionPlan getAreaExecutionPlan() {
    if (cachedAreaExecutionPlan == null || areaExecutionPlanDirty
        || isAreaExecutionPlanStale(cachedAreaExecutionPlan)) {
      cachedAreaExecutionPlan = buildAreaExecutionPlan();
      areaExecutionPlanDirty = false;
    }
    return cachedAreaExecutionPlan;
  }

  /**
   * Checks whether a cached area execution plan is stale.
   *
   * @param plan cached plan to inspect
   * @return true if registered areas or child topology versions differ from the cached plan
   */
  private boolean isAreaExecutionPlanStale(AreaExecutionPlan plan) {
    if (plan == null || plan.structureVersions.size() != processes.size()) {
      return true;
    }
    for (ProcessSystem process : processes.values()) {
      Long cachedVersion = plan.structureVersions.get(process);
      if (cachedVersion == null || cachedVersion.longValue() != process.getStructureVersion()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Captures structure versions for all process areas in insertion order.
   *
   * @param allProcesses process areas to capture
   * @return identity map from process area to current structure version
   */
  private Map<ProcessSystem, Long> captureStructureVersions(List<ProcessSystem> allProcesses) {
    Map<ProcessSystem, Long> structureVersions = new java.util.IdentityHashMap<>();
    for (ProcessSystem process : allProcesses) {
      structureVersions.put(process, Long.valueOf(process.getStructureVersion()));
    }
    return structureVersions;
  }

  /**
   * Builds the inter-area execution plan from current ProcessSystem stream wiring.
   *
   * <p>
   * Direction is inferred from stream ownership: if a stream is an outlet of some equipment in area A and also present
   * as a consumed inlet or member stream in area B, then A is the producer and B is the consumer, so A → B in the
   * meta-graph. Ambiguous links fall back to insertion order to preserve legacy behaviour.
   * </p>
   *
   * @return execution plan containing levels, adjacency, and boundary-stream consumers
   */
  private AreaExecutionPlan buildAreaExecutionPlan() {
    List<ProcessSystem> allProcesses = new ArrayList<>(processes.values());
    int n = allProcesses.size();
    Map<ProcessSystem, Long> structureVersions = captureStructureVersions(allProcesses);

    Map<ProcessSystem, java.util.Set<ProcessSystem>> successorMap = new IdentityHashMap<>();
    java.util.Set<Object> boundaryStreams = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    Map<Object, java.util.Set<ProcessSystem>> boundaryConsumers = new IdentityHashMap<>();
    Map<Object, String> streamProducers = new IdentityHashMap<>();
    for (ProcessSystem process : allProcesses) {
      successorMap.put(process, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    if (n == 0) {
      return new AreaExecutionPlan(new ArrayList<>(), successorMap, boundaryStreams, boundaryConsumers, streamProducers,
          structureVersions);
    }

    // Index processes by their position in the insertion order for
    // tie-breaking on ambiguous shared-stream directions.
    Map<ProcessSystem, Integer> index = new java.util.IdentityHashMap<>();
    for (int i = 0; i < n; i++) {
      index.put(allProcesses.get(i), i);
    }

    // For each process, collect the set of stream objects it OUTPUTS (appears
    // as outlet of some equipment in that process) and the set of stream
    // objects it CONSUMES (appears as unit-level membership or inlet of some
    // equipment there).
    List<java.util.Set<Object>> outputs = new ArrayList<>(n);
    List<java.util.Set<Object>> members = new ArrayList<>(n);
    for (ProcessSystem p : allProcesses) {
      java.util.Set<Object> outs = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      java.util.Set<Object> mem = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (Object unit : p.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          mem.add(unit);
        }
        if (unit instanceof neqsim.process.equipment.ProcessEquipmentInterface) {
          try {
            java.util.List<StreamInterface> outletStreams = ((neqsim.process.equipment.ProcessEquipmentInterface) unit)
                .getOutletStreams();
            if (outletStreams != null) {
              outs.addAll(outletStreams);
              recordStreamProducers(streamProducers, outletStreams, p, unit);
            }
          } catch (Exception e) {
            // Not all equipment implements getOutletStreams cleanly; ignore.
          }
          try {
            java.util.List<StreamInterface> inletStreams = ((neqsim.process.equipment.ProcessEquipmentInterface) unit)
                .getInletStreams();
            if (inletStreams != null) {
              mem.addAll(inletStreams);
            }
          } catch (Exception e) {
            // ignore
          }
        }
      }
      outputs.add(outs);
      members.add(mem);
    }

    java.util.Map<Object, Integer> occurrenceCounts = new java.util.IdentityHashMap<>();
    for (int i = 0; i < n; i++) {
      java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      seen.addAll(outputs.get(i));
      seen.addAll(members.get(i));
      for (Object stream : seen) {
        occurrenceCounts.merge(stream, 1, Integer::sum);
      }
    }
    for (Map.Entry<Object, Integer> entry : occurrenceCounts.entrySet()) {
      if (entry.getValue() == null || entry.getValue() < 2) {
        continue;
      }
      Object stream = entry.getKey();
      boundaryStreams.add(stream);
      java.util.Set<ProcessSystem> consumers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      java.util.Set<ProcessSystem> participants = java.util.Collections
          .newSetFromMap(new java.util.IdentityHashMap<>());
      for (int j = 0; j < n; j++) {
        boolean produced = outputs.get(j).contains(stream);
        boolean member = members.get(j).contains(stream);
        if (produced || member) {
          participants.add(allProcesses.get(j));
        }
        if (member && !produced) {
          consumers.add(allProcesses.get(j));
        }
      }
      if (consumers.isEmpty()) {
        consumers.addAll(participants);
      }
      boundaryConsumers.put(stream, consumers);
    }

    // Build directed adjacency: A → B iff some stream is outputs(A) and also
    // appears in members(B) but not in outputs(B).
    int[] inDegree = new int[n];
    List<List<Integer>> successors = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      successors.add(new ArrayList<>());
    }
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (i == j) {
          continue;
        }
        boolean linked = false;
        for (Object s : outputs.get(i)) {
          if (outputs.get(j).contains(s)) {
            // Produced in both — ambiguous. Treat as link only in insertion order.
            if (index.get(allProcesses.get(i)) < index.get(allProcesses.get(j))) {
              linked = true;
              break;
            }
          } else if (members.get(j).contains(s)) {
            linked = true;
            break;
          }
        }
        if (linked) {
          successors.get(i).add(j);
          successorMap.get(allProcesses.get(i)).add(allProcesses.get(j));
          inDegree[j]++;
        }
      }
    }

    // Kahn topological sort with level assignment.
    int[] level = new int[n];
    java.util.Deque<Integer> queue = new java.util.ArrayDeque<>();
    for (int i = 0; i < n; i++) {
      if (inDegree[i] == 0) {
        queue.add(i);
      }
    }
    int processed = 0;
    while (!queue.isEmpty()) {
      int u = queue.poll();
      processed++;
      for (int v : successors.get(u)) {
        level[v] = Math.max(level[v], level[u] + 1);
        if (--inDegree[v] == 0) {
          queue.add(v);
        }
      }
    }
    if (processed < n) {
      // Cycle detected (should be rare - indicates two areas produce streams
      // consumed by each other). Fall back to insertion order one-per-level.
      List<List<ProcessSystem>> fallback = new ArrayList<>();
      for (ProcessSystem p : allProcesses) {
        List<ProcessSystem> single = new ArrayList<>();
        single.add(p);
        fallback.add(single);
      }
      return new AreaExecutionPlan(fallback, successorMap, boundaryStreams, boundaryConsumers, streamProducers,
          structureVersions);
    }

    int maxLevel = 0;
    for (int l : level) {
      maxLevel = Math.max(maxLevel, l);
    }
    List<List<ProcessSystem>> levels = new ArrayList<>();
    for (int l = 0; l <= maxLevel; l++) {
      levels.add(new ArrayList<>());
    }
    for (int i = 0; i < n; i++) {
      levels.get(level[i]).add(allProcesses.get(i));
    }
    return new AreaExecutionPlan(levels, successorMap, boundaryStreams, boundaryConsumers, streamProducers,
        structureVersions);
  }

  /**
   * Records the producing {@code "area::unit"} label for each outlet stream of a unit.
   *
   * <p>
   * The first producer wins so the label is deterministic in insertion order. Streams that a unit merely forwards
   * (already produced upstream) therefore keep their original producer.
   * </p>
   *
   * @param streamProducers identity map to populate
   * @param outletStreams outlet streams of the unit
   * @param area process area owning the unit
   * @param unit the producing unit
   */
  private void recordStreamProducers(Map<Object, String> streamProducers, java.util.List<StreamInterface> outletStreams,
      ProcessSystem area, Object unit) {
    String unitName = null;
    if (unit instanceof neqsim.process.equipment.ProcessEquipmentInterface) {
      unitName = ((neqsim.process.equipment.ProcessEquipmentInterface) unit).getName();
    }
    if (unitName == null || unitName.trim().isEmpty()) {
      return;
    }
    String areaName = area == null ? null : area.getName();
    String label = (areaName == null || areaName.trim().isEmpty()) ? unitName : areaName + "::" + unitName;
    for (StreamInterface outlet : outletStreams) {
      if (outlet != null && !streamProducers.containsKey(outlet)) {
        streamProducers.put(outlet, label);
      }
    }
  }

  /**
   * Waits for all futures to complete and logs any errors.
   *
   * @param futures list of futures to wait for
   */
  private void waitForFutures(List<Future<?>> futures) {
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("ProcessModel execution interrupted");
        break;
      } catch (ExecutionException e) {
        logger.error("ProcessModel parallel execution error: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Collect the identity-set of streams that cross area boundaries in the current {@link ProcessModel}. A stream is a
   * boundary stream if it appears in at least two {@link ProcessSystem}s.
   *
   * @return identity-based set of boundary streams (may be empty)
   */
  private java.util.Set<Object> collectBoundaryStreams() {
    return getAreaExecutionPlan().boundaryStreams;
  }

  /**
   * Capture current boundary stream states by stream object identity.
   *
   * @param boundaryStreams identity-set of streams to capture
   * @return identity map from stream object to [flowRate, temperature, pressure]
   */
  private Map<Object, double[]> captureBoundaryStreamStates(java.util.Set<Object> boundaryStreams) {
    Map<Object, double[]> states = new java.util.IdentityHashMap<>();
    if (boundaryStreams == null || boundaryStreams.isEmpty()) {
      return states;
    }
    for (Object boundaryObject : boundaryStreams) {
      if (!(boundaryObject instanceof StreamInterface)) {
        continue;
      }
      StreamInterface stream = (StreamInterface) boundaryObject;
      try {
        double flow = stream.getFlowRate("kg/hr");
        double temp = stream.getTemperature("K");
        double press = stream.getPressure("bara");
        states.put(boundaryObject, new double[] { flow, temp, press });
      } catch (Exception exception) {
        // Skip streams that cannot be read.
      }
    }
    return states;
  }

  /**
   * Finds boundary streams that changed beyond any configured convergence tolerance.
   *
   * @param previous previous boundary stream states
   * @param current current boundary stream states
   * @return identity-set of changed boundary stream objects
   */
  private java.util.Set<Object> findChangedBoundaryStreams(Map<Object, double[]> previous,
      Map<Object, double[]> current) {
    java.util.Set<Object> changed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    for (Map.Entry<Object, double[]> entry : current.entrySet()) {
      double[] prev = previous.get(entry.getKey());
      if (prev == null) {
        changed.add(entry.getKey());
        continue;
      }
      double[] curr = entry.getValue();
      double flowBase = Math.max(Math.abs(prev[0]), 1e-10);
      double tempBase = Math.max(prev[1], 1.0);
      double pressBase = Math.max(prev[2], 1e-10);
      boolean flowChanged = Math.abs(curr[0] - prev[0]) / flowBase >= flowTolerance;
      boolean tempChanged = Math.abs(curr[1] - prev[1]) / tempBase >= temperatureTolerance;
      boolean pressureChanged = Math.abs(curr[2] - prev[2]) / pressBase >= pressureTolerance;
      if (flowChanged || tempChanged || pressureChanged) {
        changed.add(entry.getKey());
      }
    }
    return changed;
  }

  /**
   * Selects areas to rerun on the next outer iteration based on changed boundary streams.
   *
   * @param changedBoundaryStreams streams that changed beyond convergence tolerance
   * @return areas to run on the next iteration, or {@code null} to run every area
   */
  private java.util.Set<ProcessSystem> getDirtyAreasForNextIteration(java.util.Set<Object> changedBoundaryStreams) {
    return getDirtyAreasForNextIteration(getAreaExecutionPlan(), changedBoundaryStreams);
  }

  /**
   * Selects areas to rerun using an already resolved area execution plan.
   *
   * @param plan cached area execution plan
   * @param changedBoundaryStreams streams that changed beyond convergence tolerance
   * @return areas to run on the next iteration, or {@code null} to run every area
   */
  private java.util.Set<ProcessSystem> getDirtyAreasForNextIteration(AreaExecutionPlan plan,
      java.util.Set<Object> changedBoundaryStreams) {
    if (!useIncrementalAreaExecution || progressListener != null || publishEvents || changedBoundaryStreams == null) {
      return null;
    }
    java.util.Set<ProcessSystem> dirtyAreas = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    java.util.ArrayDeque<ProcessSystem> queue = new java.util.ArrayDeque<>();
    for (Object stream : changedBoundaryStreams) {
      java.util.Set<ProcessSystem> consumers = plan.boundaryConsumers.get(stream);
      if (consumers == null) {
        return null;
      }
      for (ProcessSystem consumer : consumers) {
        if (dirtyAreas.add(consumer)) {
          queue.add(consumer);
        }
      }
    }
    while (!queue.isEmpty()) {
      ProcessSystem current = queue.poll();
      java.util.Set<ProcessSystem> successors = plan.successors.get(current);
      if (successors == null) {
        continue;
      }
      for (ProcessSystem successor : successors) {
        if (dirtyAreas.add(successor)) {
          queue.add(successor);
        }
      }
    }
    if (dirtyAreas.size() >= processes.size()) {
      return null;
    }
    return dirtyAreas;
  }

  /**
   * Calculate maximum relative errors between previous and current stream states.
   *
   * <p>
   * Also records the per-stream errors in {@link #getLastBoundaryStreamErrors()} so that a non-converged model can name
   * the stream responsible for each reported maximum error.
   * </p>
   *
   * <p>
   * Package-private (rather than private) so the boundary-flow floor and absolute-flow-tolerance filters can be unit
   * tested directly without constructing an oscillating multi-area plant.
   * </p>
   *
   * @param previous previous stream states
   * @param current current stream states
   * @return array of [maxFlowError, maxTempError, maxPressError]
   */
  double[] calculateConvergenceErrors(Map<?, double[]> previous, Map<?, double[]> current) {
    return calculateConvergenceErrors(previous, current, getAreaExecutionPlan());
  }

  /**
   * Calculates convergence errors using an already resolved area execution plan.
   *
   * @param previous previous stream states
   * @param current current stream states
   * @param areaPlan area execution plan for the current model topology
   * @return array of [maxFlowError, maxTempError, maxPressError]
   */
  private double[] calculateConvergenceErrors(Map<?, double[]> previous, Map<?, double[]> current,
      AreaExecutionPlan areaPlan) {
    double maxFlowErr = 0.0;
    double maxTempErr = 0.0;
    double maxPressErr = 0.0;
    int expectedStreamErrors = lastBoundaryStreamErrors == null ? 0
        : Math.min(current.size(), lastBoundaryStreamErrors.size());
    List<BoundaryStreamError> streamErrors = new ArrayList<>(expectedStreamErrors);
    Map<Object, BoundaryStreamError> priorStreamErrors = boundaryStreamErrorCache;
    Map<Object, BoundaryStreamError> nextStreamErrors = current.isEmpty() ? Collections.emptyMap()
        : new IdentityHashMap<>(current.size());

    for (Object key : current.keySet()) {
      if (previous.containsKey(key)) {
        double[] prev = previous.get(key);
        double[] curr = current.get(key);

        // Skip near-zero (inactive / bypassed) boundary streams so that low-flow
        // sections do not block global convergence. The floor is configurable via
        // setBoundaryFlowFloor() because the default (1e-9 kg/hr) excludes nothing
        // in practice - a stagnant dead leg carrying a fraction of a kg/hr still
        // produces a large RELATIVE error and dominates the plant-wide maximum.
        if (Math.max(Math.abs(prev[0]), Math.abs(curr[0])) < boundaryFlowFloor) {
          continue;
        }

        // Flow rate relative error (with min threshold to avoid div by zero)
        double flowBase = Math.max(Math.abs(prev[0]), 1e-10);
        double flowErr = Math.abs(curr[0] - prev[0]) / flowBase;
        // A stream whose ABSOLUTE flow change is negligible is converged for
        // engineering purposes even when the relative error is large (tiny
        // denominator). The true relative error is still recorded below so the
        // per-stream diagnostics remain honest.
        if (Math.abs(curr[0] - prev[0]) >= absoluteFlowTolerance) {
          maxFlowErr = Math.max(maxFlowErr, flowErr);
        }

        // Temperature relative error (use Kelvin to avoid issues near 0)
        double tempBase = Math.max(prev[1], 1.0);
        double tempErr = Math.abs(curr[1] - prev[1]) / tempBase;
        maxTempErr = Math.max(maxTempErr, tempErr);

        // Pressure relative error
        double pressBase = Math.max(prev[2], 1e-10);
        double pressErr = Math.abs(curr[2] - prev[2]) / pressBase;
        maxPressErr = Math.max(maxPressErr, pressErr);

        String streamName = getStreamName(key);
        String producerLabel = getStreamProducerLabel(key, areaPlan);
        BoundaryStreamError streamError = priorStreamErrors == null ? null : priorStreamErrors.get(key);
        if (!matchesBoundaryStreamError(streamError, streamName, producerLabel, flowErr, tempErr, pressErr, prev[0],
            curr[0])) {
          streamError = new BoundaryStreamError(streamName, producerLabel, flowErr, tempErr, pressErr, prev[0],
              curr[0]);
        }
        streamErrors.add(streamError);
        nextStreamErrors.put(key, streamError);
      }
    }

    lastBoundaryStreamErrors = streamErrors;
    boundaryStreamErrorCache = nextStreamErrors;
    return new double[] { maxFlowErr, maxTempErr, maxPressErr };
  }

  /** Returns whether an immutable cached diagnostic exactly represents the current boundary observation. */
  private boolean matchesBoundaryStreamError(BoundaryStreamError cached, String streamName, String producerLabel,
      double flowError, double temperatureError, double pressureError, double previousFlow, double currentFlow) {
    return cached != null && java.util.Objects.equals(cached.getStreamName(), streamName)
        && java.util.Objects.equals(cached.getProducerLabel(), producerLabel)
        && Double.doubleToLongBits(cached.getFlowError()) == Double.doubleToLongBits(flowError)
        && Double.doubleToLongBits(cached.getTemperatureError()) == Double.doubleToLongBits(temperatureError)
        && Double.doubleToLongBits(cached.getPressureError()) == Double.doubleToLongBits(pressureError)
        && Double.doubleToLongBits(cached.getPreviousFlow()) == Double.doubleToLongBits(previousFlow)
        && Double.doubleToLongBits(cached.getCurrentFlow()) == Double.doubleToLongBits(currentFlow);
  }

  /**
   * Resolve a readable name for a boundary stream object.
   *
   * @param streamObject boundary stream object
   * @return the stream name, or a generic identity label when unavailable
   */
  private String getStreamName(Object streamObject) {
    if (streamObject instanceof StreamInterface) {
      String name = ((StreamInterface) streamObject).getName();
      if (name != null && !name.trim().isEmpty()) {
        return name;
      }
    }
    return "unnamed stream@" + Integer.toHexString(System.identityHashCode(streamObject));
  }

  /**
   * Resolve the producing {@code "area::unit"} label for a boundary stream object.
   *
   * @param streamObject boundary stream object
   * @param areaPlan area execution plan containing producer labels
   * @return the producer label, or an empty string when the producer cannot be resolved
   */
  private String getStreamProducerLabel(Object streamObject, AreaExecutionPlan areaPlan) {
    if (streamObject == null || areaPlan == null || processes.isEmpty()) {
      return "";
    }
    try {
      String label = areaPlan.streamProducers.get(streamObject);
      return label == null ? "" : label;
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Per-boundary-stream convergence errors recorded on the last completed outer iteration.
   *
   * <p>
   * Use this to identify which boundary stream drives a reported maximum error. A stream with
   * {@link BoundaryStreamError#isFlowCollapsedToZero()} set explains the characteristic relative flow error of exactly
   * 1.0 that appears when an upstream area stops producing a stream between outer passes.
   * </p>
   *
   * @return unmodifiable list of per-stream errors, sorted by descending maximum error
   */
  public List<BoundaryStreamError> getLastBoundaryStreamErrors() {
    List<BoundaryStreamError> sorted = new ArrayList<>(
        lastBoundaryStreamErrors == null ? new ArrayList<BoundaryStreamError>() : lastBoundaryStreamErrors);
    java.util.Collections.sort(sorted, new java.util.Comparator<BoundaryStreamError>() {
      @Override
      public int compare(BoundaryStreamError first, BoundaryStreamError second) {
        return Double.compare(second.getMaxError(), first.getMaxError());
      }
    });
    return java.util.Collections.unmodifiableList(sorted);
  }

  /**
   * Name of the boundary stream responsible for the reported maximum error of the given variable.
   *
   * @param variable one of {@code "flow"}, {@code "temperature"} or {@code "pressure"} (case-insensitive)
   * @return the worst-offending stream name, or an empty string when no boundary stream data is available
   * @throws IllegalArgumentException if {@code variable} is not a recognized variable name
   */
  public String getWorstBoundaryStreamName(String variable) {
    BoundaryStreamError worst = getWorstBoundaryStreamError(variable);
    return worst == null ? "" : worst.getStreamName();
  }

  /**
   * Boundary stream record responsible for the reported maximum error of the given variable.
   *
   * @param variable one of {@code "flow"}, {@code "temperature"} or {@code "pressure"} (case-insensitive)
   * @return the worst-offending stream record, or {@code null} when no boundary stream data is available
   * @throws IllegalArgumentException if {@code variable} is not a recognized variable name
   */
  public BoundaryStreamError getWorstBoundaryStreamError(String variable) {
    if (variable == null) {
      throw new IllegalArgumentException("variable must be one of flow, temperature or pressure");
    }
    String key = variable.trim().toLowerCase(Locale.US);
    if (!"flow".equals(key) && !"temperature".equals(key) && !"pressure".equals(key)) {
      throw new IllegalArgumentException(
          "variable must be one of flow, temperature or pressure, was '" + variable + "'");
    }
    BoundaryStreamError worst = null;
    double worstError = -1.0;
    if (lastBoundaryStreamErrors != null) {
      for (BoundaryStreamError streamError : lastBoundaryStreamErrors) {
        double error;
        if ("flow".equals(key)) {
          error = streamError.getFlowError();
        } else if ("temperature".equals(key)) {
          error = streamError.getTemperatureError();
        } else {
          error = streamError.getPressureError();
        }
        if (error > worstError) {
          worstError = error;
          worst = streamError;
        }
      }
    }
    return worst;
  }

  /**
   * Boundary streams whose flow, temperature or pressure error exceeded the configured tolerance on the last outer
   * iteration.
   *
   * @return unmodifiable list of offending streams, sorted by descending maximum error
   */
  public List<BoundaryStreamError> getNonConvergedBoundaryStreamErrors() {
    List<BoundaryStreamError> offenders = new ArrayList<>();
    for (BoundaryStreamError streamError : getLastBoundaryStreamErrors()) {
      boolean flowConverged = streamError.getFlowError() < flowTolerance
          || streamError.getAbsoluteFlowChange() < absoluteFlowTolerance;
      if (!flowConverged || streamError.getTemperatureError() >= temperatureTolerance
          || streamError.getPressureError() >= pressureTolerance) {
        offenders.add(streamError);
      }
    }
    return java.util.Collections.unmodifiableList(offenders);
  }

  /**
   * Formats the worst-offending stream name for a convergence summary line.
   *
   * @param variable variable name (flow, temperature or pressure)
   * @return a parenthesized stream reference, or an empty string when unavailable
   */
  private String formatWorstStreamSuffix(String variable) {
    BoundaryStreamError worst = getWorstBoundaryStreamError(variable);
    if (worst == null) {
      return "";
    }
    return " [worst: " + worst.getQualifiedName() + formatFlowTransitionNote(worst) + "]";
  }

  /**
   * Describes a zero-crossing flow transition that produces a relative flow error of exactly 1.0.
   *
   * @param streamError stream error record to describe
   * @return a short note, or an empty string when the flow did not cross zero
   */
  private String formatFlowTransitionNote(BoundaryStreamError streamError) {
    if (streamError.isFlowCollapsedToZero()) {
      return " (flow collapsed to zero)";
    }
    if (streamError.isFlowStartedFromZero()) {
      return " (flow started from zero)";
    }
    return "";
  }

  /**
   * Get a summary of the convergence status after running the model.
   *
   * @return formatted convergence summary string
   */
  public String getConvergenceSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ProcessModel Convergence Summary ===\n");
    sb.append("Converged: ").append(modelConverged ? "YES" : "NO").append("\n");
    sb.append("Iterations: ").append(lastIterationCount).append(" / ").append(maxIterations).append("\n");
    sb.append("Boundary streams tracked: ").append(lastBoundaryStreamCount).append("\n");
    sb.append("Boundary values converged: ").append(lastBoundaryValuesConverged ? "YES" : "NO").append("\n");
    sb.append("All process areas solved: ").append(lastAllProcessesSolved ? "YES" : "NO").append("\n");
    sb.append("\nFinal Errors (relative):\n");
    sb.append(String.format(Locale.US, "  Flow rate:    %.2e (tolerance: %.2e) %s%s\n", lastMaxFlowError, flowTolerance,
        lastMaxFlowError < flowTolerance ? "OK" : "NOT CONVERGED", formatWorstStreamSuffix("flow")));

    sb.append(String.format(Locale.US, "  Temperature:  %.2e (tolerance: %.2e) %s%s\n", lastMaxTemperatureError,
        temperatureTolerance, lastMaxTemperatureError < temperatureTolerance ? "OK" : "NOT CONVERGED",
        formatWorstStreamSuffix("temperature")));

    sb.append(String.format(Locale.US, "  Pressure:     %.2e (tolerance: %.2e) %s%s\n", lastMaxPressureError,
        pressureTolerance, lastMaxPressureError < pressureTolerance ? "OK" : "NOT CONVERGED",
        formatWorstStreamSuffix("pressure")));

    if (absoluteFlowTolerance > 0.0 || boundaryFlowFloor > DEFAULT_BOUNDARY_FLOW_FLOOR) {
      sb.append(String.format(Locale.US, "  Flow filters: absolute tolerance %.3g kg/hr, boundary floor %.3g kg/hr\n",
          absoluteFlowTolerance, boundaryFlowFloor));
    }
    if (!autoTuningSummary.isEmpty()) {
      sb.append("  Auto-tuning:  ").append(autoTuningSummary).append("\n");
    }
    if (!autoToleranceSummary.isEmpty()) {
      sb.append("  Auto-accuracy: ").append(autoToleranceSummary).append("\n");
    }
    if (!massClosureSummary.isEmpty()) {
      sb.append("  Mass closure:  ").append(massClosureSummary).append("\n");
    }

    List<BoundaryStreamError> offenders = getNonConvergedBoundaryStreamErrors();
    if (!offenders.isEmpty()) {
      sb.append("\nBoundary streams outside tolerance (worst first):\n");
      int shown = Math.min(offenders.size(), 10);
      for (int i = 0; i < shown; i++) {
        BoundaryStreamError streamError = offenders.get(i);
        sb.append(String.format(Locale.US, "  %-30s flow=%.2e (%.3g kg/hr) temp=%.2e press=%.2e%s\n",
            streamError.getQualifiedName(), streamError.getFlowError(), streamError.getAbsoluteFlowChange(),
            streamError.getTemperatureError(), streamError.getPressureError(), formatFlowTransitionNote(streamError)));
      }
      if (offenders.size() > shown) {
        sb.append("  ... and ").append(offenders.size() - shown).append(" more\n");
      }
    }

    sb.append("\nProcess Status:\n");
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      boolean processSolved = entry.getValue().solved();
      List<String> bypassedUnits = getBypassedUnitNames(entry.getValue());
      String bypassNote = bypassedUnits.isEmpty() ? ""
          : String.format(Locale.US, " (%d unit(s) bypassed on low flow)", bypassedUnits.size());
      sb.append(String.format(Locale.US, "  %-30s: %s%s\n", entry.getKey(), processSolved ? "SOLVED" : "NOT SOLVED",
          bypassNote));
      if (!processSolved) {
        List<String> unsolvedUnits = getUnsolvedUnitNames(entry.getValue());
        if (!unsolvedUnits.isEmpty()) {
          sb.append("    Unsolved units: ").append(formatUnitNameList(unsolvedUnits, 12)).append("\n");
        }
      }
    }
    return sb.toString();
  }

  /**
   * Runs the model until convergence with automatic convergence tuning, using the currently configured iteration limit
   * and tolerances.
   *
   * @return true if the model converged within the iteration limit, false otherwise
   */
  public boolean runUntilConverged() {
    return runUntilConverged(maxIterations);
  }

  /**
   * Runs the model until convergence, letting NeqSim work out the flow noise filters by itself.
   *
   * <p>
   * This is the recommended entry point for large multi-area plants. It behaves like
   * {@link #runUntilConverged(int, double)} with the currently configured relative tolerance (default {@code 1e-4}, or
   * whatever {@link #setTolerance(double)} was last given), but with {@linkplain #isAutoConvergenceTuning() automatic
   * convergence tuning} the model no longer needs hand-picked, plant-specific numbers for the boundary flow floor, the
   * absolute flow tolerance, per-section low-flow bypass threshold, or stalled-recycle acceleration.
   * </p>
   *
   * <p>
   * After the first outer sweep the total mass flow entering the plant across its feed boundary is measured and every
   * flow-noise threshold is derived from it as a fraction ({@link #getAutoTuningFlowFraction()}, default
   * {@value #DEFAULT_AUTO_TUNING_FLOW_FRACTION}) of that scale. The same model therefore self-configures across
   * scenarios and production years without editing any convergence parameter, and a dead leg carrying a seed flow is
   * recognised as noise rather than dominating the plant-wide relative error. Anything set explicitly by the caller
   * (via {@link #setBoundaryFlowFloor(double)}, {@link #setAbsoluteFlowTolerance(double)} or a per-unit
   * {@code setMinimumFlow}) always wins over the automatic value. A caller choice made with
   * {@code Recycle.setAdaptiveAcceleration(...)} likewise takes precedence.
   * </p>
   *
   * <p>
   * Call {@link #getAutoTuningSummary()} afterwards to see the detected flow scale and the thresholds that were
   * applied, or {@link #setAutoConvergenceTuning(boolean) setAutoConvergenceTuning(false)} to opt out entirely.
   * </p>
   *
   * @param maxIterations maximum number of outer iterations to attempt; must be at least 1
   * @return true if the model converged within the iteration limit, false otherwise
   * @throws IllegalArgumentException if maxIterations is less than 1
   */
  public boolean runUntilConverged(int maxIterations) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1, was " + maxIterations);
    }
    setRunStep(false);
    setMaxIterations(maxIterations);
    run();
    return modelConverged;
  }

  /**
   * Runs the model in continuous (multi-area) mode until convergence or the iteration limit.
   *
   * <p>
   * This is an explicit, agent-friendly convenience wrapper around {@link #run()}. It guarantees the model runs in
   * iterating mode (not step mode) and applies the supplied iteration limit and tolerance before running. Use this
   * instead of manually configuring {@link #setRunStep(boolean)}, {@link #setMaxIterations(int)} and
   * {@link #setTolerance(double)} and hard-coding an outer loop.
   * </p>
   *
   * <p>
   * After this call returns, inspect {@link #isModelConverged()}, {@link #getLastIterationCount()}, {@link #getError()}
   * or {@link #getConvergenceReportJson()} for the outcome.
   * </p>
   *
   * @param maxIterations maximum number of outer iterations to attempt; must be at least 1
   * @param tolerance relative convergence tolerance applied to flow, temperature and pressure; must be a finite
   * positive value
   * @return true if the model converged within the iteration limit, false otherwise
   * @throws IllegalArgumentException if maxIterations is less than 1 or tolerance is not a finite positive number
   */
  public boolean runUntilConverged(int maxIterations, double tolerance) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1, was " + maxIterations);
    }
    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be a finite positive number, was " + tolerance);
    }
    setRunStep(false);
    setMaxIterations(maxIterations);
    setTolerance(tolerance);
    run();
    return modelConverged;
  }

  /**
   * Runs the model until convergence using a combined relative AND absolute flow criterion.
   *
   * <p>
   * A boundary stream counts as flow-converged when EITHER its relative flow error is below {@code tolerance} OR its
   * absolute flow change is below {@code absoluteFlowTolerance} (kg/hr). This is the standard industrial form and is
   * the recommended way to run plants that contain stagnant or nearly-stagnant legs: a stream carrying 0.1 kg/hr can
   * swing 6 % between outer passes (0.007 kg/hr in absolute terms) and would otherwise dominate the relative maximum
   * and mask a genuine multi-hundred kg/hr residual elsewhere in the plant.
   * </p>
   *
   * @param maxIterations maximum number of outer iterations to attempt; must be at least 1
   * @param tolerance relative convergence tolerance applied to flow, temperature and pressure; must be a finite
   * positive value
   * @param absoluteFlowTolerance absolute flow tolerance in kg/hr; must be finite and non-negative. Use 0.0 for the
   * historical relative-only behaviour
   * @return true if the model converged within the iteration limit, false otherwise
   * @throws IllegalArgumentException if maxIterations is less than 1, tolerance is not a finite positive number, or
   * absoluteFlowTolerance is negative or not finite
   */
  public boolean runUntilConverged(int maxIterations, double tolerance, double absoluteFlowTolerance) {
    setAbsoluteFlowTolerance(absoluteFlowTolerance);
    return runUntilConverged(maxIterations, tolerance);
  }

  /**
   * Absolute flow tolerance used by the boundary-stream convergence check.
   *
   * @return absolute flow tolerance in kg/hr (0.0 means relative-only checking)
   */
  public double getAbsoluteFlowTolerance() {
    return absoluteFlowTolerance;
  }

  /**
   * Sets the absolute flow tolerance used by the boundary-stream convergence check.
   *
   * <p>
   * A boundary stream is flow-converged when EITHER its relative flow error is below the relative tolerance OR its
   * absolute flow change is below this value. Setting 0.0 restores pure relative checking.
   * </p>
   *
   * @param absoluteFlowTolerance absolute flow tolerance in kg/hr; must be finite and non-negative
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  public void setAbsoluteFlowTolerance(double absoluteFlowTolerance) {
    if (Double.isNaN(absoluteFlowTolerance) || Double.isInfinite(absoluteFlowTolerance)
        || absoluteFlowTolerance < 0.0) {
      throw new IllegalArgumentException(
          "absoluteFlowTolerance must be a finite non-negative number, was " + absoluteFlowTolerance);
    }
    this.absoluteFlowTolerance = absoluteFlowTolerance;
    this.absoluteFlowToleranceExplicit = true;
  }

  /**
   * Flow floor below which a boundary stream is excluded from the convergence metric entirely.
   *
   * @return boundary flow floor in kg/hr
   */
  public double getBoundaryFlowFloor() {
    return boundaryFlowFloor;
  }

  /**
   * Sets the flow floor below which a boundary stream is excluded from the convergence metric entirely.
   *
   * <p>
   * Streams carrying less than this value are treated as inactive plumbing (a stagnant dead leg, a bypassed section, or
   * a tell-tale seed stream) and neither contribute to the reported maximum errors nor appear in
   * {@link #getNonConvergedBoundaryStreamErrors()}. The default {@link #DEFAULT_BOUNDARY_FLOW_FLOOR} excludes only
   * numerically-zero streams; raise it to exclude physically negligible legs as well.
   * </p>
   *
   * @param boundaryFlowFloor flow floor in kg/hr; must be finite and non-negative
   * @throws IllegalArgumentException if the value is negative or not finite
   */
  public void setBoundaryFlowFloor(double boundaryFlowFloor) {
    if (Double.isNaN(boundaryFlowFloor) || Double.isInfinite(boundaryFlowFloor) || boundaryFlowFloor < 0.0) {
      throw new IllegalArgumentException(
          "boundaryFlowFloor must be a finite non-negative number, was " + boundaryFlowFloor);
    }
    this.boundaryFlowFloor = boundaryFlowFloor;
    this.boundaryFlowFloorExplicit = true;
  }

  /**
   * Whether the model derives its flow-noise convergence filters automatically from the plant flow scale.
   *
   * @return true if automatic convergence tuning is enabled (default)
   */
  public boolean isAutoConvergenceTuning() {
    return autoConvergenceTuning;
  }

  /**
   * Enables or disables automatic convergence tuning.
   *
   * <p>
   * When enabled (the default) the first outer sweep measures the plant's total feed throughput and derives the
   * boundary flow floor, the absolute flow tolerance and - when {@link #isAutoLowFlowBypass()} is also on - the
   * per-unit low-flow bypass threshold from it. Disabling restores the historical behaviour where every one of those
   * numbers has to be supplied per plant. Values the caller set explicitly are never overridden either way.
   * </p>
   *
   * @param autoConvergenceTuning true to let the model tune its own flow-noise filters
   */
  public void setAutoConvergenceTuning(boolean autoConvergenceTuning) {
    this.autoConvergenceTuning = autoConvergenceTuning;
  }

  /**
   * Whether the auto-tuner may bypass units whose inlet flow is below the detected noise floor.
   *
   * @return true if automatic low-flow bypass is enabled (default)
   */
  public boolean isAutoLowFlowBypass() {
    return autoLowFlowBypass;
  }

  /**
   * Enables or disables automatic low-flow bypass of stagnant sections.
   *
   * <p>
   * A dead leg (a shut-in injection train, a recompression stage switched off by a split factor) drains towards zero
   * one unit per outer pass and keeps perturbing the convergence gate for tens of iterations. When enabled, units whose
   * inlet flow falls below the detected noise floor are marked inactive for the rest of the run and stop being solved;
   * they reactivate automatically if flow returns. Units with a caller-supplied {@code setMinimumFlow} are never
   * touched.
   * </p>
   *
   * @param autoLowFlowBypass true to auto-bypass negligible-flow units
   */
  public void setAutoLowFlowBypass(boolean autoLowFlowBypass) {
    this.autoLowFlowBypass = autoLowFlowBypass;
  }

  /**
   * Noise-floor fraction of the detected plant flow scale used by the auto-tuner.
   *
   * @return the fraction (default {@value #DEFAULT_AUTO_TUNING_FLOW_FRACTION})
   */
  public double getAutoTuningFlowFraction() {
    return autoTuningFlowFraction;
  }

  /**
   * Sets the noise-floor fraction of the detected plant flow scale used by the auto-tuner.
   *
   * <p>
   * Raise it to be more aggressive about ignoring small streams (faster, more forgiving convergence), lower it to keep
   * smaller streams inside the convergence metric. A plant fed 1000 t/hr with the default {@code 1e-6} gets a 1 kg/hr
   * noise floor.
   * </p>
   *
   * @param autoTuningFlowFraction fraction of the total plant feed flow; must be finite and in [0, 1)
   * @throws IllegalArgumentException if the value is not finite or outside [0, 1)
   */
  public void setAutoTuningFlowFraction(double autoTuningFlowFraction) {
    if (Double.isNaN(autoTuningFlowFraction) || Double.isInfinite(autoTuningFlowFraction)
        || autoTuningFlowFraction < 0.0 || autoTuningFlowFraction >= 1.0) {
      throw new IllegalArgumentException(
          "autoTuningFlowFraction must be a finite value in [0, 1), was " + autoTuningFlowFraction);
    }
    this.autoTuningFlowFraction = autoTuningFlowFraction;
  }

  /**
   * Total feed mass flow (kg/hr) detected across the plant boundary on the last run.
   *
   * @return the detected plant flow scale in kg/hr, or 0.0 if the model has not run with auto-tuning enabled
   */
  public double getDetectedPlantFlowScale() {
    return detectedPlantFlowScale;
  }

  /**
   * Human-readable description of what the auto-tuner detected and applied on the last run.
   *
   * @return a one-line summary, or an empty string when auto-tuning did not run
   */
  public String getAutoTuningSummary() {
    return autoTuningSummary;
  }

  /**
   * Restores every threshold the auto-tuner applied, returning the model to its unconfigured state.
   *
   * <p>
   * Units whose low-flow threshold was written by the auto-tuner get it reset and are reactivated. Explicitly
   * configured values are left untouched.
   * </p>
   *
   * @return the number of units whose auto-assigned low-flow threshold was cleared
   */
  public int resetAutoTuning() {
    int cleared = 0;
    for (ProcessSystem process : processes.values()) {
      cleared += process.resetAutoLowFlowThreshold();
    }
    if (!boundaryFlowFloorExplicit) {
      boundaryFlowFloor = DEFAULT_BOUNDARY_FLOW_FLOOR;
    }
    if (!absoluteFlowToleranceExplicit) {
      absoluteFlowTolerance = 0.0;
    }
    autoTuningAppliedScale = 0.0;
    detectedPlantFlowScale = 0.0;
    autoTuningSummary = "";
    return cleared;
  }

  /** Clears the per-run auto-tuning bookkeeping so a re-run re-measures the plant flow scale. */
  private void resetAutoTuningRunState() {
    autoTuningAppliedScale = 0.0;
    detectedPlantFlowScale = 0.0;
    autoTuningSummary = "";
    autoToleranceSummary = "";
    massClosureSummary = "";
    massClosureOffenders = "";
    lastMassClosureError = Double.NaN;
    unitMassClosureOffenders = "";
    lastUnitMassClosureError = Double.NaN;
    if (autoToleranceErrorHistory == null) {
      autoToleranceErrorHistory = new java.util.ArrayList<>();
    }
    autoToleranceErrorHistory.clear();
    if (!boundaryFlowFloorExplicit) {
      boundaryFlowFloor = DEFAULT_BOUNDARY_FLOW_FLOOR;
    }
    if (!absoluteFlowToleranceExplicit) {
      absoluteFlowTolerance = 0.0;
    }
    for (ProcessSystem process : processes.values()) {
      process.resetAutoLowFlowThreshold();
      process.resetAutoRecycleFlowTolerance();
      process.resetAutoRecycleAdaptiveAcceleration();
    }
  }

  /**
   * Applies the engineering-grade default accuracy when the caller did not ask for one.
   *
   * <p>
   * The historical default (1e-4 relative on flow, temperature and pressure) is tighter than any process-engineering
   * result needs, and it is what makes recycle-rich plants grind through many extra outer passes. When no tolerance was
   * set explicitly, a plain {@code run()} therefore starts from {@value #DEFAULT_ENGINEERING_TOLERANCE}.
   * </p>
   */
  private void applyAutoDefaultTolerance() {
    if (!autoConvergenceTuning || !autoTolerance || toleranceExplicit) {
      return;
    }
    flowTolerance = DEFAULT_ENGINEERING_TOLERANCE;
    temperatureTolerance = DEFAULT_ENGINEERING_TOLERANCE;
    pressureTolerance = DEFAULT_ENGINEERING_TOLERANCE;
    autoToleranceSummary = String.format(Locale.US,
        "no tolerance given - using the engineering default %.1e relative " + "on flow, temperature and pressure",
        DEFAULT_ENGINEERING_TOLERANCE);
  }

  /**
   * Accepts a residual that has stopped improving but is already accurate enough for process work.
   *
   * <p>
   * A recycle-rich plant can approach its solution asymptotically: the last decade of the residual costs more outer
   * passes than the whole approach did, and buys an accuracy far below the uncertainty of the fluid model itself. When
   * the worst relative error has not improved materially over {@value #AUTO_TOLERANCE_STALL_WINDOW} outer passes and is
   * still below {@link #getAutoToleranceCeiling()}, the tolerance is widened to just above that residual and the
   * accepted accuracy is reported through {@link #getAutoToleranceSummary()}. An explicit tolerance, a residual above
   * the ceiling, or a still-improving residual all suppress this.
   * </p>
   *
   * @return true when the tolerance was widened and convergence must be re-evaluated
   */
  private boolean relaxToleranceIfStalled() {
    if (!autoConvergenceTuning || !autoTolerance || toleranceExplicit) {
      return false;
    }
    double worstError = Math.max(lastMaxFlowError, Math.max(lastMaxTemperatureError, lastMaxPressureError));
    if (Double.isNaN(worstError) || Double.isInfinite(worstError) || !(worstError > 0.0)) {
      return false;
    }
    if (autoToleranceErrorHistory == null) {
      autoToleranceErrorHistory = new java.util.ArrayList<>();
    }
    autoToleranceErrorHistory.add(Double.valueOf(worstError));
    if (autoToleranceErrorHistory.size() <= AUTO_TOLERANCE_STALL_WINDOW) {
      return false;
    }
    double reference = autoToleranceErrorHistory.get(autoToleranceErrorHistory.size() - 1 - AUTO_TOLERANCE_STALL_WINDOW)
        .doubleValue();
    if (reference > 0.0 && (reference - worstError) / reference >= AUTO_TOLERANCE_STALL_IMPROVEMENT) {
      return false; // still making real progress - keep iterating
    }
    if (worstError > autoToleranceCeiling) {
      return false; // genuinely not converged, not merely a too-tight gate
    }
    double accepted = Math.min(autoToleranceCeiling, worstError * 1.05);
    if (accepted <= flowTolerance) {
      return false;
    }
    flowTolerance = accepted;
    temperatureTolerance = accepted;
    pressureTolerance = accepted;
    autoToleranceSummary = String.format(Locale.US,
        "residual stalled at %.2e after %d passes - accepted %.2e relative "
            + "(engineering accuracy, ceiling %.1e); set a tolerance explicitly to override",
        worstError, autoToleranceErrorHistory.size(), accepted, autoToleranceCeiling);
    logger.debug("ProcessModel auto-tolerance: {}", autoToleranceSummary);
    return true;
  }

  /**
   * Whether the auto-tuner refuses a converged verdict while recycle tears still create or destroy mass.
   *
   * @return true when the mass-closure gate is active
   */
  public boolean isAutoMassClosureGate() {
    return autoMassClosureGate;
  }

  /**
   * Enables or disables the automatic mass-closure acceptance gate.
   *
   * @param autoMassClosureGate true to require plant mass closure before accepting convergence
   */
  public void setAutoMassClosureGate(boolean autoMassClosureGate) {
    this.autoMassClosureGate = autoMassClosureGate;
  }

  /**
   * Whether the unit-level mass-closure figure also blocks a converged verdict.
   *
   * @return true when non-recycle unit imbalances gate convergence as well as being reported
   */
  public boolean isUnitMassClosureGate() {
    return unitMassClosureGate;
  }

  /**
   * Enables or disables gating on the unit-level mass-closure figure.
   *
   * @param unitMassClosureGate true to also require non-recycle units to conserve mass before accepting convergence
   */
  public void setUnitMassClosureGate(boolean unitMassClosureGate) {
    this.unitMassClosureGate = unitMassClosureGate;
  }

  /**
   * Mass created or destroyed by non-recycle units at the last check, as a fraction of plant feed.
   *
   * @return relative unit-level closure error, or NaN when it was never evaluated
   */
  public double getLastUnitMassClosureError() {
    return lastUnitMassClosureError;
  }

  /**
   * Non-recycle units creating or destroying the most mass at the last check.
   *
   * @return worst offenders text, empty when the check never ran or found nothing
   */
  public String getUnitMassClosureOffenders() {
    return unitMassClosureOffenders;
  }

  /**
   * Accepted plant mass-closure error, as a fraction of plant feed.
   *
   * @return relative mass-closure tolerance
   */
  public double getMassClosureTolerance() {
    return massClosureTolerance;
  }

  /**
   * Sets the accepted plant mass-closure error.
   *
   * @param massClosureTolerance relative tolerance; must be a finite number greater than zero
   * @throws IllegalArgumentException if the tolerance is not a finite positive number
   */
  public void setMassClosureTolerance(double massClosureTolerance) {
    if (Double.isNaN(massClosureTolerance) || Double.isInfinite(massClosureTolerance) || massClosureTolerance <= 0.0) {
      throw new IllegalArgumentException(
          "massClosureTolerance must be a finite positive number, was " + massClosureTolerance);
    }
    this.massClosureTolerance = massClosureTolerance;
  }

  /**
   * Plant mass-closure error at the last convergence check, as a fraction of plant feed.
   *
   * @return relative mass-closure error, or NaN when it was never evaluated
   */
  public double getLastMassClosureError() {
    return lastMassClosureError;
  }

  /**
   * Human-readable description of the last mass-closure check.
   *
   * @return summary text, empty when the gate never ran
   */
  public String getMassClosureSummary() {
    return massClosureSummary;
  }

  /**
   * Collect recycle units once for a process area, including units inside nested modules.
   *
   * @param process process area or nested module operations
   * @return recycle paths and unit instances in the process hierarchy
   */
  private List<Map.Entry<String, Recycle>> getRecycleUnits(ProcessSystem process) {
    List<Map.Entry<String, Recycle>> recycles = new ArrayList<>();
    Set<ProcessSystem> visited = Collections.newSetFromMap(new IdentityHashMap<ProcessSystem, Boolean>());
    collectRecycleUnits(process, "", recycles, visited);
    return recycles;
  }

  /**
   * Add recycle units from one process hierarchy without revisiting cyclic module references.
   *
   * @param process process area or nested module operations
   * @param pathPrefix module path prefix
   * @param recycles destination list
   * @param visited process systems already traversed by identity
   */
  private void collectRecycleUnits(ProcessSystem process, String pathPrefix, List<Map.Entry<String, Recycle>> recycles,
      Set<ProcessSystem> visited) {
    if (process == null || !visited.add(process)) {
      return;
    }
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      String equipmentPath = pathPrefix.isEmpty() ? equipment.getName() : pathPrefix + "::" + equipment.getName();
      if (equipment instanceof Recycle) {
        recycles.add(new AbstractMap.SimpleEntry<String, Recycle>(equipmentPath, (Recycle) equipment));
      }
      if (equipment instanceof ModuleInterface && equipment.isActive() && !equipment.isLockedInactive()) {
        collectRecycleUnits(((ModuleInterface) equipment).getOperations(), equipmentPath, recycles, visited);
      }
    }
  }

  /**
   * Total mass created or destroyed by open recycle tears, as a fraction of plant feed.
   *
   * <p>
   * A recycle whose outlet no longer matches the sum of its inlets is a standing mass source or sink of exactly that
   * difference, so summing the absolute tear imbalances is the mass the flowsheet is failing to conserve. Loops
   * carrying less than the auto-derived boundary floor are skipped as noise.
   * </p>
   *
   * @return relative mass-closure error, or NaN when no usable flow scale exists
   */
  private double computeMassClosureError() {
    double scale = Math.max(detectedPlantFlowScale, getTotalFeedFlowRate());
    if (!(scale > 0.0) || Double.isInfinite(scale)) {
      massClosureOffenders = "";
      return Double.NaN;
    }
    double created = 0.0;
    List<Map.Entry<String, Double>> offenders = new ArrayList<>();
    for (Map.Entry<String, ProcessSystem> area : processes.entrySet()) {
      for (Map.Entry<String, Recycle> recycleEntry : getRecycleUnits(area.getValue())) {
        Recycle recycle = recycleEntry.getValue();
        if (recycle.isLockedInactive() || !recycle.isActive()) {
          continue;
        }
        double error;
        try {
          error = recycle.getMassBalance("kg/hr");
        } catch (RuntimeException exception) {
          logger.warn("Failed to calculate recycle mass balance for area {} unit {}", area.getKey(),
              recycleEntry.getKey(), exception);
          continue;
        }
        if (!Double.isFinite(error) || Math.abs(error) < boundaryFlowFloor) {
          continue;
        }
        created += Math.abs(error);
        offenders.add(
            new AbstractMap.SimpleEntry<String, Double>(area.getKey() + "::" + recycleEntry.getKey(), Math.abs(error)));
      }
    }
    massClosureOffenders = formatClosureOffenders(offenders);
    return created / scale;
  }

  /**
   * Formats the worst mass-closure offenders, largest absolute imbalance first.
   *
   * @param offenders unit path and absolute imbalance in kg/hr; reordered in place
   * @return comma-separated text for at most the three worst offenders, empty when there are none
   */
  private String formatClosureOffenders(List<Map.Entry<String, Double>> offenders) {
    Collections.sort(offenders, new Comparator<Map.Entry<String, Double>>() {
      @Override
      public int compare(Map.Entry<String, Double> first, Map.Entry<String, Double> second) {
        return Double.compare(second.getValue(), first.getValue());
      }
    });
    StringBuilder worst = new StringBuilder();
    for (int i = 0; i < offenders.size() && i < 3; i++) {
      if (i > 0) {
        worst.append(", ");
      }
      worst.append(String.format(Locale.US, "%s %.4g kg/hr", offenders.get(i).getKey(), offenders.get(i).getValue()));
    }
    return worst.toString();
  }

  /**
   * Total mass created or destroyed by non-recycle unit operations, as a fraction of plant feed.
   *
   * <p>
   * The recycle-tear figure only covers what the outer solver itself can close. A separator, pipe or mixer whose
   * outlets no longer match its inlets is an equally real mass source or sink, and the boundary residual is blind to
   * it. Bypassed and low-flow units are already excluded by {@link ProcessSystem#getFailedMassBalance(String, double)};
   * recycles are skipped here so they are not counted twice.
   * </p>
   *
   * @return relative unit-level closure error, or NaN when no usable flow scale exists
   */
  private double computeUnitMassClosureError() {
    double scale = Math.max(detectedPlantFlowScale, getTotalFeedFlowRate());
    if (!(scale > 0.0) || Double.isInfinite(scale)) {
      unitMassClosureOffenders = "";
      return Double.NaN;
    }
    double created = 0.0;
    List<Map.Entry<String, Double>> offenders = new ArrayList<>();
    for (Map.Entry<String, ProcessSystem> area : processes.entrySet()) {
      Map<String, ProcessSystem.MassBalanceResult> failures;
      try {
        failures = area.getValue().getFailedMassBalance("kg/hr", 0.0);
      } catch (RuntimeException exception) {
        logger.warn("Failed to calculate unit mass balance for area {}", area.getKey(), exception);
        continue;
      }
      for (Map.Entry<String, ProcessSystem.MassBalanceResult> entry : failures.entrySet()) {
        if (area.getValue().getUnit(entry.getKey()) instanceof Recycle) {
          continue;
        }
        double error = entry.getValue().getAbsoluteError();
        if (!Double.isFinite(error) || Math.abs(error) < boundaryFlowFloor) {
          continue;
        }
        created += Math.abs(error);
        offenders
            .add(new AbstractMap.SimpleEntry<String, Double>(area.getKey() + "::" + entry.getKey(), Math.abs(error)));
      }
    }
    unitMassClosureOffenders = formatClosureOffenders(offenders);
    return created / scale;
  }

  /**
   * Whether the plant conserves mass well enough for the auto-tuner to accept convergence.
   *
   * @return true when the gate is inactive or the closure error is within tolerance
   */
  private boolean massClosureAccepted() {
    if (!autoConvergenceTuning || !autoMassClosureGate) {
      return true;
    }
    double closure = computeMassClosureError();
    lastMassClosureError = closure;
    double unitClosure = computeUnitMassClosureError();
    lastUnitMassClosureError = unitClosure;

    boolean recycleAccepted = Double.isNaN(closure) || closure <= massClosureTolerance;
    String recyclePart;
    if (Double.isNaN(closure)) {
      recyclePart = "recycle tear mass closure not evaluable - no usable plant flow scale";
    } else if (recycleAccepted) {
      recyclePart = String.format(Locale.US,
          "recycle tear mass closure %.3g of feed (tolerance %.3g) - every active recycle tear closes", closure,
          massClosureTolerance);
    } else {
      recyclePart = String.format(Locale.US,
          "recycle tear mass closure %.3g of feed exceeds %.3g - open recycle tears are still creating or destroying mass inside the "
              + "flowsheet, so the boundary residual alone does not mean the model is solved. Worst: %s",
          closure, massClosureTolerance,
          massClosureOffenders.isEmpty() ? "none above the flow floor" : massClosureOffenders);
    }

    boolean unitWithinTolerance = Double.isNaN(unitClosure) || unitClosure <= massClosureTolerance;
    String unitPart;
    if (Double.isNaN(unitClosure)) {
      unitPart = "";
    } else if (unitWithinTolerance) {
      unitPart = String.format(Locale.US, " Unit-level closure %.3g of feed is within the same tolerance.",
          unitClosure);
    } else {
      unitPart = String.format(Locale.US,
          " Unit-level closure %.3g of feed exceeds %.3g%s - non-recycle units are creating or destroying mass, which "
              + "the recycle-tear gate does not cover. Worst: %s",
          unitClosure, massClosureTolerance, unitMassClosureGate ? "" : " (reported, not gating)",
          unitMassClosureOffenders.isEmpty() ? "none above the flow floor" : unitMassClosureOffenders);
    }

    massClosureSummary = recyclePart + unitPart;
    boolean accepted = recycleAccepted && (unitWithinTolerance || !unitMassClosureGate);
    if (!accepted) {
      logger.debug("ProcessModel {}", massClosureSummary);
    }
    return accepted;
  }

  /**
   * Derives the plant flow scale and re-applies the automatic noise thresholds when the throughput has grown.
   *
   * @return true when thresholds changed and every process area must be evaluated once more
   */
  private boolean applyAutoConvergenceTuning() {
    if (!autoConvergenceTuning) {
      return false;
    }
    double scale = Math.max(detectedPlantFlowScale, getTotalFeedFlowRate());
    if (!(scale > 0.0) || Double.isInfinite(scale)) {
      return false;
    }
    detectedPlantFlowScale = scale;

    // Re-apply only on the first pass or when the plant has grown materially since.
    if (autoTuningAppliedScale > 0.0 && scale < 2.0 * autoTuningAppliedScale) {
      return false;
    }
    autoTuningAppliedScale = scale;

    double noiseFloor = scale * autoTuningFlowFraction;
    if (!boundaryFlowFloorExplicit) {
      boundaryFlowFloor = Math.max(DEFAULT_BOUNDARY_FLOW_FLOOR, noiseFloor);
    }
    if (!absoluteFlowToleranceExplicit) {
      absoluteFlowTolerance = noiseFloor;
    }
    int bypassCandidates = autoLowFlowBypass ? applyAutoLowFlowThreshold(noiseFloor) : 0;
    int recyclesTuned = 0;
    int adaptiveRecycles = 0;
    for (ProcessSystem process : processes.values()) {
      recyclesTuned += process.applyAutoRecycleFlowTolerance(noiseFloor);
      adaptiveRecycles += process.applyAutoRecycleAdaptiveAcceleration();
    }

    autoTuningSummary = String.format(Locale.US,
        "auto-tuned to a plant feed rate of %.4g kg/hr: boundary floor %.3g kg/hr, absolute flow tolerance "
            + "%.3g kg/hr, low-flow bypass %.3g kg/hr on %d unit(s), recycle flow tolerance on %d loop(s), "
            + "adaptive acceleration on %d loop(s)",
        scale, boundaryFlowFloor, absoluteFlowTolerance, autoLowFlowBypass ? noiseFloor : 0.0, bypassCandidates,
        recyclesTuned, adaptiveRecycles);
    logger.debug("ProcessModel {}", autoTuningSummary);
    return true;
  }

  /**
   * Writes the auto-derived low-flow bypass threshold onto every unit that has no caller-supplied threshold.
   *
   * @param thresholdKgPerHour low-flow bypass threshold in kg/hr
   * @return the number of units the auto-tuner manages
   */
  private int applyAutoLowFlowThreshold(double thresholdKgPerHour) {
    int managed = 0;
    for (ProcessSystem process : processes.values()) {
      managed += process.applyAutoLowFlowThreshold(thresholdKgPerHour);
    }
    return managed;
  }

  /**
   * Total mass flow entering the whole model across its feed boundary.
   *
   * <p>
   * A stream produced by any area - including a cross-area link or a recycle target - is not a feed, so this is the
   * plant throughput rather than a sum of internal traffic.
   * </p>
   *
   * @return total feed mass flow in kg/hr, or 0.0 when no feed stream could be read
   */
  public double getTotalFeedFlowRate() {
    java.util.Set<StreamInterface> produced = java.util.Collections
        .newSetFromMap(new java.util.IdentityHashMap<StreamInterface, Boolean>());
    java.util.Set<StreamInterface> inlets = java.util.Collections
        .newSetFromMap(new java.util.IdentityHashMap<StreamInterface, Boolean>());
    for (ProcessSystem process : processes.values()) {
      process.collectProducedStreams(produced);
      process.collectInletStreams(inlets);
    }
    double total = 0.0;
    for (StreamInterface stream : inlets) {
      if (produced.contains(stream)) {
        continue;
      }
      try {
        double flow = stream.getFlowRate("kg/hr");
        if (!Double.isNaN(flow) && !Double.isInfinite(flow) && flow > 0.0) {
          total += flow;
        }
      } catch (RuntimeException ex) {
        logger.debug("Could not read feed flow rate while detecting the plant flow scale", ex);
      }
    }
    return total;
  }

  /**
   * Builds a machine-readable JSON convergence report for the last model run.
   *
   * <p>
   * This is the structured counterpart to {@link #getConvergenceSummary()}, intended for agentic workflows that need to
   * parse the convergence outcome rather than read a formatted string. The report is schema-versioned and includes the
   * per-area solved status and the names of any unsolved units, so an agent can pinpoint where a large multi-area model
   * failed to converge.
   * </p>
   *
   * <p>
   * Top-level fields: {@code schemaVersion}, {@code converged}, {@code iterations}, {@code maxIterations},
   * {@code boundaryStreamCount}, {@code boundaryValuesConverged}, {@code allProcessesSolved}, {@code maxError}, an
   * {@code errors} object (flow/temperature/pressure value, tolerance, converged flag and the {@code worstStream} that
   * drove the error), a {@code boundaryStreamErrors} array naming every boundary stream outside tolerance, and an
   * {@code areas} array (one object per process area with {@code name}, {@code solved} and {@code unsolvedUnits}).
   * </p>
   *
   * <p>
   * Each boundary stream entry carries {@code name}, {@code flowError}, {@code temperatureError},
   * {@code pressureError}, {@code previousFlowKgPerHr}, {@code currentFlowKgPerHr}, {@code flowCollapsedToZero} and
   * {@code flowStartedFromZero}. A relative flow error of exactly 1.0 together with {@code flowCollapsedToZero} means
   * the stream stopped flowing between outer passes rather than converging slowly.
   * </p>
   *
   * @return a JSON string describing the convergence outcome of the last run
   */
  public String getConvergenceReportJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", "1.0");
    root.addProperty("converged", modelConverged);
    root.addProperty("iterations", lastIterationCount);
    root.addProperty("maxIterations", maxIterations);
    root.addProperty("boundaryStreamCount", lastBoundaryStreamCount);
    root.addProperty("boundaryValuesConverged", lastBoundaryValuesConverged);
    root.addProperty("allProcessesSolved", lastAllProcessesSolved);
    root.addProperty("maxError", getError());

    JsonObject errors = new JsonObject();
    errors.add("flow", buildErrorEntry(lastMaxFlowError, flowTolerance, "flow"));
    errors.add("temperature", buildErrorEntry(lastMaxTemperatureError, temperatureTolerance, "temperature"));
    errors.add("pressure", buildErrorEntry(lastMaxPressureError, pressureTolerance, "pressure"));
    root.add("errors", errors);

    JsonObject autoTuning = new JsonObject();
    autoTuning.addProperty("enabled", autoConvergenceTuning);
    autoTuning.addProperty("lowFlowBypassEnabled", autoLowFlowBypass);
    autoTuning.addProperty("flowFraction", autoTuningFlowFraction);
    autoTuning.addProperty("detectedPlantFlowScaleKgPerHr", detectedPlantFlowScale);
    autoTuning.addProperty("boundaryFlowFloorKgPerHr", boundaryFlowFloor);
    autoTuning.addProperty("absoluteFlowToleranceKgPerHr", absoluteFlowTolerance);
    autoTuning.addProperty("summary", autoTuningSummary);
    root.add("autoTuning", autoTuning);

    JsonObject autoToleranceInfo = new JsonObject();
    autoToleranceInfo.addProperty("enabled", autoTolerance);
    autoToleranceInfo.addProperty("toleranceExplicit", toleranceExplicit);
    autoToleranceInfo.addProperty("appliedTolerance", flowTolerance);
    autoToleranceInfo.addProperty("ceiling", autoToleranceCeiling);
    autoToleranceInfo.addProperty("summary", autoToleranceSummary);
    root.add("autoTolerance", autoToleranceInfo);

    JsonObject massClosure = new JsonObject();
    massClosure.addProperty("enabled", autoConvergenceTuning && autoMassClosureGate);
    massClosure.addProperty("tolerance", massClosureTolerance);
    if (Double.isFinite(lastMassClosureError)) {
      massClosure.addProperty("relativeError", lastMassClosureError);
    } else {
      massClosure.add("relativeError", JsonNull.INSTANCE);
    }
    massClosure.addProperty("summary", massClosureSummary);
    massClosure.addProperty("worstUnits", massClosureOffenders);
    massClosure.addProperty("unitGateEnabled", autoConvergenceTuning && autoMassClosureGate && unitMassClosureGate);
    if (Double.isFinite(lastUnitMassClosureError)) {
      massClosure.addProperty("unitRelativeError", lastUnitMassClosureError);
    } else {
      massClosure.add("unitRelativeError", JsonNull.INSTANCE);
    }
    massClosure.addProperty("unitWorstUnits", unitMassClosureOffenders);
    root.add("massClosure", massClosure);

    JsonArray boundaryStreamErrors = new JsonArray();
    for (BoundaryStreamError streamError : getNonConvergedBoundaryStreamErrors()) {
      boundaryStreamErrors.add(buildBoundaryStreamErrorEntry(streamError));
    }
    root.add("boundaryStreamErrors", boundaryStreamErrors);

    JsonArray areas = new JsonArray();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      JsonObject area = new JsonObject();
      area.addProperty("name", entry.getKey());
      boolean processSolved = entry.getValue().solved();
      area.addProperty("solved", processSolved);
      JsonArray unsolved = new JsonArray();
      if (!processSolved) {
        for (String unitName : getUnsolvedUnitNames(entry.getValue())) {
          unsolved.add(unitName);
        }
      }
      area.add("unsolvedUnits", unsolved);
      JsonArray bypassed = new JsonArray();
      for (String unitName : getBypassedUnitNames(entry.getValue())) {
        bypassed.add(unitName);
      }
      area.add("bypassedUnits", bypassed);
      areas.add(area);
    }
    root.add("areas", areas);
    return root.toString();
  }

  /**
   * Builds a single error entry for {@link #getConvergenceReportJson()}.
   *
   * @param error the relative error value for the variable
   * @param tolerance the convergence tolerance for the variable
   * @param variable variable name used to look up the worst-offending boundary stream
   * @return a JSON object with {@code value}, {@code tolerance}, {@code converged} and {@code worstStream} fields
   */
  private JsonObject buildErrorEntry(double error, double tolerance, String variable) {
    JsonObject entry = new JsonObject();
    entry.addProperty("value", error);
    entry.addProperty("tolerance", tolerance);
    entry.addProperty("converged", error < tolerance);
    BoundaryStreamError worst = getWorstBoundaryStreamError(variable);
    if (worst == null) {
      entry.add("worstStream", null);
    } else {
      entry.add("worstStream", buildBoundaryStreamErrorEntry(worst));
    }
    return entry;
  }

  /**
   * Builds a per-boundary-stream error entry for {@link #getConvergenceReportJson()}.
   *
   * @param streamError the stream error record to serialize
   * @return a JSON object describing the stream and its convergence errors
   */
  private JsonObject buildBoundaryStreamErrorEntry(BoundaryStreamError streamError) {
    JsonObject entry = new JsonObject();
    entry.addProperty("name", streamError.getStreamName());
    entry.addProperty("producer", streamError.getProducerLabel());
    entry.addProperty("qualifiedName", streamError.getQualifiedName());
    entry.addProperty("flowError", streamError.getFlowError());
    entry.addProperty("temperatureError", streamError.getTemperatureError());
    entry.addProperty("pressureError", streamError.getPressureError());
    entry.addProperty("previousFlowKgPerHr", streamError.getPreviousFlow());
    entry.addProperty("currentFlowKgPerHr", streamError.getCurrentFlow());
    entry.addProperty("flowCollapsedToZero", streamError.isFlowCollapsedToZero());
    entry.addProperty("flowStartedFromZero", streamError.isFlowStartedFromZero());
    return entry;
  }

  /**
   * Gets names of unit operations that currently report unsolved status.
   *
   * <p>
   * Bypassed units (locked inactive, or auto-bypassed because their inlet flow fell below the configured low-flow
   * threshold) are excluded: they never execute, so their {@code solved()} flag carries no information. Use
   * {@link #getBypassedUnitNames(ProcessSystem)} to list those separately.
   * </p>
   *
   * @param process process system to inspect
   * @return list of unsolved unit names in process execution order
   */
  private List<String> getUnsolvedUnitNames(ProcessSystem process) {
    List<String> names = new ArrayList<>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.isLockedInactive() || !unit.isActive()) {
        continue;
      }
      if (!unit.solved()) {
        names.add(unit.getName());
      }
    }
    return names;
  }

  /**
   * Gets names of unit operations that are currently bypassed in the given process area.
   *
   * @param process process system to inspect
   * @return list of bypassed unit names in process execution order
   */
  private List<String> getBypassedUnitNames(ProcessSystem process) {
    List<String> names = new ArrayList<>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit.isLockedInactive() || !unit.isActive()) {
        names.add(unit.getName());
      }
    }
    return names;
  }

  /**
   * Formats a unit-name list for compact convergence diagnostics.
   *
   * @param unitNames names to format
   * @param maxNames maximum number of names to include before truncating
   * @return comma-separated unit list with truncation count when needed
   */
  private String formatUnitNameList(List<String> unitNames, int maxNames) {
    StringBuilder names = new StringBuilder();
    int includedNames = Math.min(unitNames.size(), maxNames);
    for (int unitIndex = 0; unitIndex < includedNames; unitIndex++) {
      if (unitIndex > 0) {
        names.append(", ");
      }
      names.append(unitNames.get(unitIndex));
    }
    if (unitNames.size() > maxNames) {
      names.append(", ... (").append(unitNames.size() - maxNames).append(" more)");
    }
    return names.toString();
  }

  /**
   * Gets a combined execution partition analysis for all ProcessSystems.
   *
   * <p>
   * This method provides insight into how each ProcessSystem will be executed, including:
   * </p>
   * <ul>
   * <li>Whether each system has recycle loops</li>
   * <li>Number of units and parallel levels</li>
   * <li>Which execution strategy will be used</li>
   * </ul>
   *
   * @return combined execution partition info for all ProcessSystems
   */
  public String getExecutionPartitionInfo() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ProcessModel Execution Analysis ===\n");
    sb.append("Total ProcessSystems: ").append(processes.size()).append("\n");
    sb.append("Optimized execution: ").append(useOptimizedExecution ? "enabled" : "disabled").append("\n\n");

    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      sb.append("--- ProcessSystem: ").append(entry.getKey()).append(" ---\n");
      ProcessSystem process = entry.getValue();
      sb.append("Units: ").append(process.getUnitOperations().size()).append("\n");
      sb.append("Has recycles: ").append(process.hasRecycleLoops()).append("\n");
      if (useOptimizedExecution) {
        sb.append("Strategy: ").append(process.hasRecycleLoops() ? "Hybrid (parallel + iterative)" : "Parallel")
            .append("\n");
      } else {
        sb.append("Strategy: Sequential\n");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Runs this model in a separate thread using the global NeqSim thread pool.
   *
   * <p>
   * This method submits the model to the shared {@link neqsim.util.NeqSimThreadPool} and returns a
   * {@link java.util.concurrent.Future} that can be used to monitor completion, cancel the task, or retrieve any
   * exceptions that occurred.
   * </p>
   *
   * @return a {@link java.util.concurrent.Future} representing the pending completion of the task
   * @see neqsim.util.NeqSimThreadPool
   */
  public java.util.concurrent.Future<?> runAsTask() {
    return neqsim.util.NeqSimThreadPool.submit(this);
  }

  /**
   * Starts this model in a new thread and returns that thread.
   *
   * @return a {@link java.lang.Thread} object
   * @deprecated Use {@link #runAsTask()} instead for better resource management. This method creates a new unmanaged
   * thread directly.
   */
  @Deprecated
  public Thread runAsThread() {
    Thread processThread = new Thread(this);
    processThread.start();
    return processThread;
  }

  /**
   * Checks if all processes are finished.
   *
   * @return a boolean
   */
  public boolean isFinished() {
    for (ProcessSystem process : processes.values()) {
      if (!process.solved()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Runs all processes in a single step (used outside of the thread model).
   */
  public void runStep() {
    for (ProcessSystem process : processes.values()) {
      try {
        if (Thread.currentThread().isInterrupted()) {
          logger.debug("Thread was interrupted, exiting run()...");
          return;
        }
        process.run_step();
      } catch (Exception e) {
        System.err.println("Error in runStep: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * (Optional) Creates separate threads for each process (if you need them).
   *
   * @return a {@link java.util.Map} object
   */
  public Map<String, Thread> getThreads() {
    Map<String, Thread> threads = new LinkedHashMap<>();
    try {
      for (ProcessSystem process : processes.values()) {
        Thread thread = new Thread(process);
        thread.setName(process.getName() + " thread");
        threads.put(process.getName(), thread);
      }
    } catch (Exception ex) {
      logger.debug(ex.getMessage(), ex);
    }
    return threads;
  }

  /**
   * Retrieves a list of all processes.
   *
   * @return a {@link java.util.Collection} of {@link neqsim.process.processmodel.ProcessSystem} objects
   */
  public Collection<ProcessSystem> getAllProcesses() {
    return processes.values();
  }

  /**
   * Sets the minimum tear-stream flow on every {@link neqsim.process.equipment.util.Recycle} unit across all
   * process-area {@link ProcessSystem}s of this model.
   *
   * @param minimumFlowKgPerHr the minimum recycle flow rate in kg/hr; must be non-negative
   * @return the total number of recycle units updated across all areas
   * @throws IllegalArgumentException if {@code minimumFlowKgPerHr} is negative
   */
  public int setRecycleMinimumFlow(double minimumFlowKgPerHr) {
    if (minimumFlowKgPerHr < 0.0) {
      throw new IllegalArgumentException("minimumFlowKgPerHr cannot be negative");
    }
    int total = 0;
    for (ProcessSystem area : processes.values()) {
      total += area.setRecycleMinimumFlow(minimumFlowKgPerHr);
    }
    return total;
  }

  /**
   * Enables or disables the multiphase (three-phase) flash on every fluid in every process area of this model.
   *
   * <p>
   * Turning the multiphase check off on areas known to be two-phase only avoids the extra phase-stability work in every
   * flash and can speed up the solve considerably. Use {@link #setMultiPhaseCheck(String, boolean)} to configure a
   * single area, for example to keep the check on in the separation trains but turn it off in the compression and
   * export areas.
   * </p>
   *
   * @param enabled true to enable the multiphase flash, false to turn it off in all areas
   * @return the total number of distinct fluids updated across all areas
   * @see ProcessSystem#setMultiPhaseCheck(boolean)
   */
  public int setMultiPhaseCheck(boolean enabled) {
    int total = 0;
    for (ProcessSystem area : processes.values()) {
      total += area.setMultiPhaseCheck(enabled);
    }
    return total;
  }

  /**
   * Enables or disables the multiphase (three-phase) flash on every fluid of a single process area.
   *
   * @param areaName the name the {@link ProcessSystem} was registered with in {@link #add(String, ProcessSystem)}
   * @param enabled true to enable the multiphase flash, false to turn it off in this area
   * @return the number of distinct fluids updated, or -1 if no area with the given name exists
   * @see ProcessSystem#setMultiPhaseCheck(boolean)
   */
  public int setMultiPhaseCheck(String areaName, boolean enabled) {
    ProcessSystem area = processes.get(areaName);
    if (area == null) {
      return -1;
    }
    return area.setMultiPhaseCheck(enabled);
  }

  /**
   * Sets the physical-property initialization level used by every stream in every process area of this model.
   *
   * <p>
   * Selecting {@link neqsim.process.equipment.stream.Stream.PropertyInitLevel#DENSITY_ONLY} skips the viscosity,
   * thermal-conductivity and diffusivity correlations after every stream flash, which is substantially cheaper on a
   * large plant. Use {@link #setPropertyInitLevel(String, neqsim.process.equipment.stream.Stream.PropertyInitLevel)} to
   * configure a single area, for example to keep full properties in a flow-assurance or heat-exchanger area while
   * running the rest of the plant on mass balances only.
   * </p>
   *
   * <p>
   * <b>Warning - transport properties read back as zero</b> under {@code DENSITY_ONLY}; see
   * {@link ProcessSystem#setPropertyInitLevel(neqsim.process.equipment.stream.Stream.PropertyInitLevel)}.
   * </p>
   *
   * @param level the level to apply; null restores per-stream control without changing already applied settings
   * @return the total number of distinct streams updated across all areas
   * @see ProcessSystem#setPropertyInitLevel(neqsim.process.equipment.stream.Stream.PropertyInitLevel)
   */
  public int setPropertyInitLevel(neqsim.process.equipment.stream.Stream.PropertyInitLevel level) {
    int total = 0;
    for (ProcessSystem area : processes.values()) {
      total += area.setPropertyInitLevel(level);
    }
    return total;
  }

  /**
   * Sets the physical-property initialization level used by every stream of a single process area.
   *
   * @param areaName the name the {@link ProcessSystem} was registered with in {@link #add(String, ProcessSystem)}
   * @param level the level to apply; null restores per-stream control without changing already applied settings
   * @return the number of distinct streams updated, or -1 if no area with the given name exists
   * @see ProcessSystem#setPropertyInitLevel(neqsim.process.equipment.stream.Stream.PropertyInitLevel)
   */
  public int setPropertyInitLevel(String areaName, neqsim.process.equipment.stream.Stream.PropertyInitLevel level) {
    ProcessSystem area = processes.get(areaName);
    if (area == null) {
      return -1;
    }
    return area.setPropertyInitLevel(level);
  }

  /**
   * Creates a Graphviz exporter for common plant-wide and per-area DOT diagrams.
   *
   * @return a new {@link ProcessModelGraphvizExporter} for this model
   */
  public ProcessModelGraphvizExporter createGraphvizExporter() {
    return new ProcessModelGraphvizExporter(this);
  }

  /**
   * Generates a common Graphviz DOT diagram for the full process model.
   *
   * <p>
   * The common diagram uses one Graphviz cluster per process area and draws cross-area stream links when areas share
   * live stream objects.
   * </p>
   *
   * @return DOT-format string for the full process model
   */
  public String toDOT() {
    return createGraphvizExporter().toDot();
  }

  /**
   * Exports a common Graphviz DOT diagram for the full process model.
   *
   * @param filename destination file name for the common DOT graph
   */
  public void exportToGraphviz(String filename) {
    try {
      createGraphvizExporter().exportDOT(Paths.get(filename));
    } catch (IOException exception) {
      logger.error("Error exporting ProcessModel to Graphviz", exception);
    }
  }

  /**
   * Exports one Graphviz DOT file per process area.
   *
   * @param outputDirectory directory where area DOT files are written
   * @return map from area name to written DOT file path
   * @throws IOException if the directory cannot be created or a file cannot be written
   */
  public Map<String, Path> exportAreaDOT(Path outputDirectory) throws IOException {
    return createGraphvizExporter().exportAreaDOT(outputDirectory);
  }

  /**
   * Exports one Graphviz DOT file per process area.
   *
   * @param outputDirectory directory where area DOT files are written
   * @return map from area name to written DOT file path
   * @throws IOException if the directory cannot be created or a file cannot be written
   */
  public Map<String, Path> exportAreaDOT(String outputDirectory) throws IOException {
    return exportAreaDOT(Paths.get(outputDirectory));
  }

  /**
   * Check mass balance of all unit operations in all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a map with process name and unit operation name as key and mass balance result as value
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance(String unit) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allMassBalanceResults = new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> massBalanceResults = process.checkMassBalance(unit);
      allMassBalanceResults.put(processName, massBalanceResults);
    }
    return allMassBalanceResults;
  }

  /**
   * Check mass balance of all unit operations in all processes using kg/sec.
   *
   * @return a map with process name and unit operation name as key and mass balance result as value in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance() {
    return checkMassBalance("kg/sec");
  }

  /**
   * Get unit operations that failed mass balance check in all processes based on percentage error threshold.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold (default: 0.1%)
   * @return a map with process name and a map of failed unit operation names and their mass balance results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(String unit,
      double percentThreshold) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults = new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> failedResults = process.getFailedMassBalance(unit, percentThreshold);
      if (!failedResults.isEmpty()) {
        allFailedResults.put(processName, failedResults);
      }
    }
    return allFailedResults;
  }

  /**
   * Get unit operations that failed mass balance check in all processes using kg/sec and default threshold.
   *
   * @return a map with process name and a map of failed unit operation names and their mass balance results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults = new LinkedHashMap<>();
    for (ProcessSystem process : processes.values()) {
      Map<String, ProcessSystem.MassBalanceResult> failedResults = process.getFailedMassBalance();
      if (!failedResults.isEmpty()) {
        allFailedResults.put(process.getName(), failedResults);
      }
    }
    return allFailedResults;
  }

  /**
   * Get unit operations that failed mass balance check in all processes using specified threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a map with process name and a map of failed unit operation names and their mass balance results in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(double percentThreshold) {
    return getFailedMassBalance("kg/sec", percentThreshold);
  }

  /**
   * Get a formatted mass balance report for all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a formatted string report with process name and mass balance results
   */
  public String getMassBalanceReport(String unit) {
    StringBuilder report = new StringBuilder();
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allResults = checkMassBalance(unit);

    for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : allResults.entrySet()) {
      report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
      report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

      Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
      if (unitResults.isEmpty()) {
        report.append("No unit operations found.\n");
      } else {
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults.entrySet()) {
          String unitName = unitEntry.getKey();
          ProcessSystem.MassBalanceResult result = unitEntry.getValue();
          report.append(String.format("  %-30s: %s\n", unitName, result.toString()));
        }
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted mass balance report for all processes using kg/sec.
   *
   * @return a formatted string report with process name and mass balance results
   */
  public String getMassBalanceReport() {
    return getMassBalanceReport("kg/sec");
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport(String unit, double percentThreshold) {
    StringBuilder report = new StringBuilder();
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> failedResults = getFailedMassBalance(unit,
        percentThreshold);

    if (failedResults.isEmpty()) {
      report.append("All unit operations passed mass balance check.\n");
    } else {
      for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : failedResults.entrySet()) {
        report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
        report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

        Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults.entrySet()) {
          String unitName = unitEntry.getKey();
          ProcessSystem.MassBalanceResult result = unitEntry.getValue();
          report.append(String.format("  %-30s: %s\n", unitName, result.toString()));
        }
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using kg/sec and default threshold.
   *
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport() {
    return getFailedMassBalanceReport("kg/sec", 0.1);
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using specified threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport(double percentThreshold) {
    return getFailedMassBalanceReport("kg/sec", percentThreshold);
  }

  /**
   * getReport_json.
   *
   * @return a {@link java.lang.String} object
   */
  public String getReport_json() {
    return new Report(this).generateJsonReport();
  }

  /**
   * Exports this ProcessModel to a JSON string containing all named process areas.
   *
   * <p>
   * The exported JSON has a top-level "areas" object where each key is the process area name and each value is a JSON
   * object in the {@link JsonProcessBuilder} schema (with "fluid" and "process" sections). This format can be used to
   * reconstruct the model or to export individual areas to external simulators (e.g., UniSim Design via COM
   * automation).
   * </p>
   *
   * <p>
   * Example output:
   *
   * <pre>{@code { "areas": { "separation": { "fluid": {...}, "process": [...] }, "compression": {
   * "fluid": {...}, "process": [...] } } } }</pre>
   *
   * @return JSON string representing all process areas @see JsonProcessExporter @see ProcessSystem#toJson()
   */
  public String toJson() {
    return toJson(true);
  }

  /**
   * Exports this ProcessModel to a JSON string.
   *
   * @param prettyPrint whether to format the JSON with indentation
   * @return JSON string representing all process areas
   */
  public String toJson(boolean prettyPrint) {
    JsonObject root = new JsonObject();
    JsonObject areas = new JsonObject();
    IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences = new IdentityHashMap<>();
    Map<String, JsonObject> exportedAreas = new LinkedHashMap<>();

    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      JsonProcessExporter exporter = new JsonProcessExporter();
      JsonObject areaJson = exporter.toJsonObject(entry.getValue());
      exportedAreas.put(entry.getKey(), areaJson);
      collectProducedStreamReferences(entry.getKey(), entry.getValue(), exporter, producedStreamReferences);
    }

    for (Map.Entry<String, JsonObject> entry : exportedAreas.entrySet()) {
      areas.add(entry.getKey(), entry.getValue());
    }
    root.add("areas", areas);
    root.addProperty("runStep", isRunStep());
    root.addProperty("maxIterations", getMaxIterations());
    root.addProperty("flowTolerance", getFlowTolerance());
    root.addProperty("temperatureTolerance", getTemperatureTolerance());
    root.addProperty("pressureTolerance", getPressureTolerance());
    root.addProperty("useOptimizedExecution", isUseOptimizedExecution());
    root.addProperty("preventNestedParallelExecution", isPreventNestedParallelExecution());
    root.addProperty("useAdaptiveModelParallelism", isUseAdaptiveModelParallelism());
    root.addProperty("useIncrementalAreaExecution", isUseIncrementalAreaExecution());
    root.addProperty("useFastRecycleConvergence", isUseFastRecycleConvergence());
    root.addProperty("useCoordinatedRecycleAcceleration", isUseCoordinatedRecycleAcceleration());
    root.addProperty("useFlashWarmStart", isUseFlashWarmStart());

    JsonArray interAreaLinks = exportInterAreaLinks(producedStreamReferences);
    if (interAreaLinks.size() > 0) {
      root.add(INTER_AREA_LINKS_KEY, interAreaLinks);
    }

    com.google.gson.Gson gson;
    if (prettyPrint) {
      gson = new com.google.gson.GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    } else {
      gson = new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create();
    }
    return gson.toJson(root);
  }

  /**
   * Collects stream references that are locally produced by one process area.
   *
   * @param areaName name of the process area being exported
   * @param process process area being exported
   * @param exporter exporter used for this area
   * @param producedStreamReferences identity map to populate with produced stream references
   */
  private void collectProducedStreamReferences(String areaName, ProcessSystem process, JsonProcessExporter exporter,
      IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        addProducedStreamReference(areaName, (StreamInterface) unit, exporter, producedStreamReferences);
      }
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets != null) {
        for (StreamInterface outlet : outlets) {
          addProducedStreamReference(areaName, outlet, exporter, producedStreamReferences);
        }
      }
    }
  }

  /**
   * Adds one locally produced stream reference to the identity map.
   *
   * @param areaName name of the producing process area
   * @param stream produced stream object
   * @param exporter exporter used for this area
   * @param producedStreamReferences identity map to populate with produced stream references
   */
  private void addProducedStreamReference(String areaName, StreamInterface stream, JsonProcessExporter exporter,
      IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences) {
    if (stream == null || producedStreamReferences.containsKey(stream)) {
      return;
    }
    String streamReference = exporter.getStreamReference(stream);
    if (streamReference != null) {
      producedStreamReferences.put(stream, new AreaStreamReference(areaName, streamReference));
    }
  }

  /**
   * Exports live inter-area stream links for model-level JSON round-tripping.
   *
   * @param producedStreamReferences identity map from produced streams to source references
   * @return JSON array of inter-area link definitions
   */
  private JsonArray exportInterAreaLinks(
      IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences) {
    JsonArray links = new JsonArray();
    for (Map.Entry<String, ProcessSystem> areaEntry : processes.entrySet()) {
      String targetAreaName = areaEntry.getKey();
      ProcessSystem targetProcess = areaEntry.getValue();
      for (ProcessEquipmentInterface unit : targetProcess.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          continue;
        }
        List<StreamInterface> inlets = getEquipmentInletStreams(unit);
        for (int inletIndex = 0; inletIndex < inlets.size(); inletIndex++) {
          StreamInterface inlet = inlets.get(inletIndex);
          AreaStreamReference source = producedStreamReferences.get(inlet);
          if (source == null || source.areaName.equals(targetAreaName)) {
            continue;
          }
          JsonObject link = new JsonObject();
          link.addProperty("sourceArea", source.areaName);
          link.addProperty("source", source.streamReference);
          link.addProperty("targetArea", targetAreaName);
          link.addProperty("targetUnit", unit.getName());
          link.addProperty("targetInletIndex", inletIndex);
          links.add(link);
        }
      }
    }
    return links;
  }

  /**
   * Builds a ProcessModel from a JSON string containing named process areas.
   *
   * <p>
   * Expected JSON format:
   *
   * <pre>{@code { "areas": { "separation": { "fluid": {...}, "process": [...] }, "compression": {
   * "fluid": {...}, "process": [...] } } } }</pre>
   *
   * <p>
   * Each area is built independently using {@link JsonProcessBuilder}. If any area fails to build, it is skipped and a
   * warning is logged.
   * </p>
   *
   * @param json the JSON string with the "areas" structure
   * @return the built ProcessModel (not yet run)
   * @throws IllegalArgumentException if JSON is null, empty, or missing the "areas" key
   * @see #toJson()
   */
  public static ProcessModel fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("JSON input is null or empty");
    }
    com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    if (!root.has("areas")) {
      throw new IllegalArgumentException("JSON must have an 'areas' object with named process systems");
    }

    ProcessModel model = new ProcessModel();
    applyModelSettings(model, root);
    com.google.gson.JsonObject areas = root.getAsJsonObject("areas");

    for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
      String areaName = entry.getKey();
      String areaJson = entry.getValue().toString();
      SimulationResult result = new JsonProcessBuilder().build(areaJson);
      if (result.isSuccess()) {
        model.add(areaName, result.getProcessSystem());
      } else {
        logger.warn("Failed to build area '{}': {}", areaName, result);
      }
    }
    if (root.has(INTER_AREA_LINKS_KEY) && root.get(INTER_AREA_LINKS_KEY).isJsonArray()) {
      List<String> warnings = model.applyInterAreaLinks(root.getAsJsonArray(INTER_AREA_LINKS_KEY));
      for (String warning : warnings) {
        logger.warn(warning);
      }
    }
    return model;
  }

  /**
   * Applies model-level execution settings from the root JSON object onto a ProcessModel.
   *
   * @param model the model to configure
   * @param root the root JSON object that may contain settings keys
   */
  private static void applyModelSettings(ProcessModel model, com.google.gson.JsonObject root) {
    if (root.has("runStep")) {
      model.setRunStep(root.get("runStep").getAsBoolean());
    }
    if (root.has("maxIterations")) {
      model.setMaxIterations(root.get("maxIterations").getAsInt());
    }
    if (root.has("flowTolerance")) {
      model.setFlowTolerance(root.get("flowTolerance").getAsDouble());
    }
    if (root.has("temperatureTolerance")) {
      model.setTemperatureTolerance(root.get("temperatureTolerance").getAsDouble());
    }
    if (root.has("pressureTolerance")) {
      model.setPressureTolerance(root.get("pressureTolerance").getAsDouble());
    }
    if (root.has("useOptimizedExecution")) {
      model.setUseOptimizedExecution(root.get("useOptimizedExecution").getAsBoolean());
    }
    if (root.has("preventNestedParallelExecution")) {
      model.setPreventNestedParallelExecution(root.get("preventNestedParallelExecution").getAsBoolean());
    }
    if (root.has("useAdaptiveModelParallelism")) {
      model.setUseAdaptiveModelParallelism(root.get("useAdaptiveModelParallelism").getAsBoolean());
    }
    if (root.has("useIncrementalAreaExecution")) {
      model.setUseIncrementalAreaExecution(root.get("useIncrementalAreaExecution").getAsBoolean());
    }
    if (root.has("useFastRecycleConvergence")) {
      model.setUseFastRecycleConvergence(root.get("useFastRecycleConvergence").getAsBoolean());
    }
    if (root.has("useCoordinatedRecycleAcceleration")) {
      model.setUseCoordinatedRecycleAcceleration(root.get("useCoordinatedRecycleAcceleration").getAsBoolean());
    }
    if (root.has("useFlashWarmStart")) {
      model.setUseFlashWarmStart(root.get("useFlashWarmStart").getAsBoolean());
    }
  }

  /**
   * Builds a multi-area ProcessModel from JSON, returning a structured, never-throwing result.
   *
   * <p>
   * This is the agent-friendly counterpart to {@link #fromJson(String)}. Where {@code fromJson} only logs per-area
   * build failures, this method captures each area's {@link SimulationResult}, the names of areas that failed to build,
   * and any inter-area link warnings inside a {@link ProcessModelResult}. Invalid input (null, empty, or missing the
   * {@code "areas"} key) yields an error result rather than a thrown exception, so an automated pipeline that builds a
   * plant from extracted JSON can degrade gracefully.
   * </p>
   *
   * <p>
   * The expected JSON format is identical to {@link #fromJson(String)}: an {@code "areas"} object whose values are
   * individual process definitions (as understood by {@link JsonProcessBuilder}), plus an optional
   * {@code "interAreaLinks"} array.
   * </p>
   *
   * @param json the JSON string with the {@code "areas"} structure
   * @return a structured build result; never null and never throwing
   * @see #fromJson(String)
   * @see #buildFromJsonAndRun(String)
   */
  public static ProcessModelResult buildFromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      return ProcessModelResult.error("EMPTY_INPUT", "JSON input is null or empty",
          "Provide a JSON string with an 'areas' object");
    }
    com.google.gson.JsonObject root;
    try {
      com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
      if (!parsed.isJsonObject()) {
        return ProcessModelResult.error("JSON_PARSE_ERROR", "Top-level JSON is not an object",
            "Wrap the model definition in a JSON object with an 'areas' key");
      }
      root = parsed.getAsJsonObject();
    } catch (RuntimeException ex) {
      return ProcessModelResult.error("JSON_PARSE_ERROR", "Could not parse JSON: " + ex.getMessage(),
          "Verify the JSON syntax is valid");
    }
    if (!root.has("areas") || !root.get("areas").isJsonObject()) {
      return ProcessModelResult.error("MISSING_AREAS", "JSON must contain an 'areas' object with named process systems",
          "Add an 'areas' object whose keys are area names and values are process definitions");
    }

    ProcessModel model = new ProcessModel();
    applyModelSettings(model, root);

    Map<String, SimulationResult> areaResults = new java.util.LinkedHashMap<String, SimulationResult>();
    List<String> failedAreas = new ArrayList<String>();
    List<String> warnings = new ArrayList<String>();
    com.google.gson.JsonObject areas = root.getAsJsonObject("areas");

    for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
      String areaName = entry.getKey();
      SimulationResult result;
      try {
        result = new JsonProcessBuilder().build(entry.getValue().toString());
      } catch (RuntimeException ex) {
        result = SimulationResult.error("AREA_BUILD_EXCEPTION", "Area '" + areaName + "' threw: " + ex.getMessage(),
            "Inspect the area definition for invalid equipment or stream references");
      }
      areaResults.put(areaName, result);
      if (result.isSuccess() && result.getProcessSystem() != null) {
        model.add(areaName, result.getProcessSystem());
      } else {
        failedAreas.add(areaName);
        warnings.add("Failed to build area '" + areaName + "'");
      }
    }

    List<String> interAreaLinkWarnings = new ArrayList<String>();
    if (root.has(INTER_AREA_LINKS_KEY) && root.get(INTER_AREA_LINKS_KEY).isJsonArray()) {
      try {
        interAreaLinkWarnings.addAll(model.applyInterAreaLinks(root.getAsJsonArray(INTER_AREA_LINKS_KEY)));
      } catch (RuntimeException ex) {
        interAreaLinkWarnings.add("Could not apply inter-area links: " + ex.getMessage());
      }
    }

    if (model.size() == 0) {
      List<SimulationResult.ErrorDetail> errors = new ArrayList<SimulationResult.ErrorDetail>();
      errors.add(new SimulationResult.ErrorDetail("NO_AREAS_BUILT", "No process area could be built", null,
          "Check the per-area errors in the result"));
      return ProcessModelResult.failure(errors, areaResults, failedAreas, warnings);
    }
    return ProcessModelResult.success(model, areaResults, failedAreas, interAreaLinkWarnings, warnings, null);
  }

  /**
   * Builds a multi-area ProcessModel from JSON and runs it, returning a structured, never-throwing result.
   *
   * <p>
   * Combines {@link #buildFromJson(String)} with execution. The model is run only when at least one area built
   * successfully. Run failures are captured as a warning plus the model's run-status JSON instead of being thrown, so
   * an automated pipeline always receives a usable {@link ProcessModelResult}.
   * </p>
   *
   * @param json the JSON string with the {@code "areas"} structure
   * @return a structured build-and-run result; never null and never throwing
   * @see #buildFromJson(String)
   * @see #fromJsonAndRun(String)
   */
  public static ProcessModelResult buildFromJsonAndRun(String json) {
    ProcessModelResult buildResult = buildFromJson(json);
    if (!buildResult.isSuccess() || buildResult.getModel() == null) {
      return buildResult;
    }
    ProcessModel model = buildResult.getModel();
    List<String> warnings = new ArrayList<String>(buildResult.getWarnings());
    String runStatusJson = null;
    try {
      model.run();
    } catch (RuntimeException ex) {
      warnings.add("Model run did not complete cleanly: " + ex.getMessage());
    }
    try {
      runStatusJson = model.getRunStatusJson();
    } catch (RuntimeException ex) {
      warnings.add("Could not read run status: " + ex.getMessage());
    }
    return ProcessModelResult.success(model, buildResult.getAreaResults(), buildResult.getFailedAreas(),
        buildResult.getInterAreaLinkWarnings(), warnings, runStatusJson);
  }

  /**
   * Applies model-level inter-area stream links after all process areas have been built.
   *
   * @param interAreaLinks JSON array with sourceArea, source, targetArea, targetUnit, and targetInletIndex fields
   * @return warnings for links that could not be applied
   */
  public List<String> applyInterAreaLinks(JsonArray interAreaLinks) {
    List<String> warnings = new ArrayList<>();
    if (interAreaLinks == null) {
      return warnings;
    }
    boolean topologyChanged = false;
    for (JsonElement linkElement : interAreaLinks) {
      if (!linkElement.isJsonObject()) {
        warnings.add("Skipping interAreaLinks entry because it is not a JSON object");
        continue;
      }
      topologyChanged = applyInterAreaLink(linkElement.getAsJsonObject(), warnings) || topologyChanged;
    }
    if (topologyChanged) {
      invalidateTopology();
    }
    return warnings;
  }

  /**
   * Applies one inter-area stream link.
   *
   * @param link JSON link definition
   * @param warnings mutable warning list to append to
   * @return true if the link was applied and model topology changed
   */
  private boolean applyInterAreaLink(JsonObject link, List<String> warnings) {
    String sourceArea = getString(link, "sourceArea");
    String sourceReference = getString(link, "source");
    String targetArea = getString(link, "targetArea");
    String targetUnitName = getString(link, "targetUnit");
    int targetInletIndex = link.has("targetInletIndex") ? link.get("targetInletIndex").getAsInt() : 0;

    ProcessSystem sourceProcess = processes.get(sourceArea);
    ProcessSystem targetProcess = processes.get(targetArea);
    if (sourceProcess == null) {
      warnings.add("Inter-area link source area not found: " + sourceArea);
      return false;
    }
    if (targetProcess == null) {
      warnings.add("Inter-area link target area not found: " + targetArea);
      return false;
    }

    StreamInterface sourceStream = resolveAreaStreamReference(sourceProcess, sourceReference);
    if (sourceStream == null) {
      warnings.add("Inter-area link source stream not found: " + sourceArea + "::" + sourceReference);
      return false;
    }

    ProcessEquipmentInterface targetUnit = targetProcess.getUnit(targetUnitName);
    if (targetUnit == null) {
      warnings.add("Inter-area link target unit not found: " + targetArea + "::" + targetUnitName);
      return false;
    }
    if (!replaceInletReference(targetUnit, targetInletIndex, sourceStream)) {
      warnings.add(
          "Could not apply inter-area link to " + targetArea + "::" + targetUnitName + " inlet " + targetInletIndex);
      return false;
    }
    targetProcess.invalidateGraph();
    return true;
  }

  /**
   * Gets a string field from a JSON object.
   *
   * @param object JSON object to inspect
   * @param field field name
   * @return field value, or an empty string when absent
   */
  private String getString(JsonObject object, String field) {
    if (object.has(field) && !object.get(field).isJsonNull()) {
      return object.get(field).getAsString();
    }
    return "";
  }

  /**
   * Resolves a stream reference inside one process area.
   *
   * @param process process area containing the referenced unit
   * @param reference stream reference such as {@code feed}, {@code Sep.gasOut}, or {@code Tee.split0}
   * @return resolved stream, or {@code null} when no stream matches the reference
   */
  private StreamInterface resolveAreaStreamReference(ProcessSystem process, String reference) {
    if (reference == null || reference.trim().isEmpty()) {
      return null;
    }
    String unitName = reference;
    String port = "outlet";
    if (reference.contains(".")) {
      String[] parts = reference.split("\\.", 2);
      unitName = parts[0];
      port = parts[1].toLowerCase();
    }
    ProcessEquipmentInterface unit = process.getUnit(unitName);
    if (unit == null) {
      return null;
    }
    if (unit instanceof StreamInterface) {
      return (StreamInterface) unit;
    }
    return resolveEquipmentOutlet(unit, port);
  }

  /**
   * Resolves a port name on an equipment unit to an outlet stream.
   *
   * @param unit equipment unit producing the stream
   * @param port outlet port name
   * @return outlet stream, or {@code null} when no matching port exists
   */
  private StreamInterface resolveEquipmentOutlet(ProcessEquipmentInterface unit, String port) {
    try {
      if ("gasout".equals(port) || "gas".equals(port)) {
        return (StreamInterface) unit.getClass().getMethod("getGasOutStream").invoke(unit);
      }
      if ("liquidout".equals(port) || "liquid".equals(port)) {
        return (StreamInterface) unit.getClass().getMethod("getLiquidOutStream").invoke(unit);
      }
      if ("oilout".equals(port) || "oil".equals(port)) {
        return (StreamInterface) unit.getClass().getMethod("getOilOutStream").invoke(unit);
      }
      if ("waterout".equals(port) || "water".equals(port)) {
        return (StreamInterface) unit.getClass().getMethod("getWaterOutStream").invoke(unit);
      }
      int splitIndex = parseNumericSuffix(port, "split");
      if (splitIndex >= 0) {
        return (StreamInterface) unit.getClass().getMethod("getSplitStream", int.class).invoke(unit, splitIndex);
      }
      int outletIndex = parseNumericSuffix(port, "outlet");
      if (outletIndex >= 0 && unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getOutStream(outletIndex);
      }
      int heatExchangerIndex = parseNumericSuffix(port, "hx");
      if (heatExchangerIndex >= 0 && unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getOutStream(heatExchangerIndex);
      }
      if (unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getOutStream(0);
      }
      return (StreamInterface) unit.getClass().getMethod("getOutletStream").invoke(unit);
    } catch (Exception exception) {
      List<StreamInterface> outlets = unit.getOutletStreams();
      if (outlets != null && !outlets.isEmpty()) {
        return outlets.get(0);
      }
      return null;
    }
  }

  /**
   * Parses a non-negative integer suffix from a port name.
   *
   * @param value port name to parse
   * @param prefix expected prefix before the number
   * @return parsed suffix, or {@code -1} when the value does not match
   */
  private int parseNumericSuffix(String value, String prefix) {
    if (value == null || !value.startsWith(prefix) || value.length() <= prefix.length()) {
      return -1;
    }
    try {
      return Integer.parseInt(value.substring(prefix.length()));
    } catch (NumberFormatException exception) {
      return -1;
    }
  }

  /**
   * Replaces one inlet reference on an equipment unit with a live inter-area stream.
   *
   * @param targetUnit equipment whose inlet should be replaced
   * @param targetInletIndex zero-based inlet index
   * @param sourceStream replacement source stream
   * @return true if the inlet was replaced
   */
  private boolean replaceInletReference(ProcessEquipmentInterface targetUnit, int targetInletIndex,
      StreamInterface sourceStream) {
    if (targetUnit instanceof HeatExchanger) {
      try {
        ((HeatExchanger) targetUnit).setFeedStream(targetInletIndex, sourceStream);
        return true;
      } catch (Exception exception) {
        return false;
      }
    }
    if (invokeIndexedStreamReplacement(targetUnit, targetInletIndex, sourceStream)) {
      return true;
    }
    if (targetInletIndex == 0 && invokeSingleInletSetter(targetUnit, sourceStream)) {
      return true;
    }
    return false;
  }

  /**
   * Invokes a {@code replaceStream(int, StreamInterface)} style method when available.
   *
   * @param targetUnit equipment whose inlet should be replaced
   * @param targetInletIndex zero-based inlet index
   * @param sourceStream replacement source stream
   * @return true if a replacement method existed and completed
   */
  private boolean invokeIndexedStreamReplacement(ProcessEquipmentInterface targetUnit, int targetInletIndex,
      StreamInterface sourceStream) {
    try {
      java.lang.reflect.Method replaceStream = targetUnit.getClass().getMethod("replaceStream", int.class,
          StreamInterface.class);
      replaceStream.invoke(targetUnit, targetInletIndex, sourceStream);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }

  /**
   * Invokes a single-inlet setter on equipment with one inlet.
   *
   * @param targetUnit equipment whose inlet should be replaced
   * @param sourceStream replacement source stream
   * @return true if a setter existed and completed
   */
  private boolean invokeSingleInletSetter(ProcessEquipmentInterface targetUnit, StreamInterface sourceStream) {
    try {
      java.lang.reflect.Method setInletStream = targetUnit.getClass().getMethod("setInletStream",
          StreamInterface.class);
      setInletStream.invoke(targetUnit, sourceStream);
      return true;
    } catch (Exception firstException) {
      try {
        java.lang.reflect.Method setFeedStream = targetUnit.getClass().getMethod("setFeedStream",
            StreamInterface.class);
        setFeedStream.invoke(targetUnit, sourceStream);
        return true;
      } catch (Exception secondException) {
        return false;
      }
    }
  }

  /**
   * Gets inlet streams from equipment with a reflection fallback for legacy unit operations.
   *
   * @param unit equipment unit to inspect
   * @return list of current inlet streams, possibly empty
   */
  private List<StreamInterface> getEquipmentInletStreams(ProcessEquipmentInterface unit) {
    List<StreamInterface> inlets = new ArrayList<>();
    try {
      List<StreamInterface> listedInlets = unit.getInletStreams();
      if (listedInlets != null) {
        for (StreamInterface inlet : listedInlets) {
          if (inlet != null) {
            inlets.add(inlet);
          }
        }
      }
    } catch (Exception exception) {
      // Fall back below for equipment without robust getInletStreams support.
    }
    if (inlets.isEmpty()) {
      try {
        StreamInterface inlet = (StreamInterface) unit.getClass().getMethod("getInletStream").invoke(unit);
        if (inlet != null) {
          inlets.add(inlet);
        }
      } catch (Exception exception) {
        // No single inlet accessor available.
      }
    }
    return inlets;
  }

  /**
   * Builds and immediately runs a ProcessModel from a JSON string.
   *
   * <p>
   * Convenience method that combines {@link #fromJson(String)} and {@link #run()} in a single call. This is the
   * round-trip counterpart to {@link #toJson()}.
   * </p>
   *
   * @param json the JSON string with the "areas" structure
   * @return the built and executed ProcessModel
   * @throws IllegalArgumentException if JSON is null, empty, or missing the "areas" key
   */
  public static ProcessModel fromJsonAndRun(String json) {
    ProcessModel model = fromJson(json);
    model.run();
    return model;
  }

  /**
   * Validates the setup of all processes in this model.
   *
   * <p>
   * This method iterates through all ProcessSystems and validates each one. The results are aggregated into a single
   * ValidationResult. Use this method before running the model to identify configuration issues.
   * </p>
   *
   * @return a {@link neqsim.util.validation.ValidationResult} containing all validation issues across all processes
   */
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult();

    // Check if model has any processes
    if (processes.isEmpty()) {
      result.addError("ProcessModel", "ProcessModel has no processes added",
          "Add at least one ProcessSystem using add(name, process)");
    }

    // Validate each ProcessSystem
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      ValidationResult processResult = process.validateSetup();

      // Add all issues from the process, prefixed with process name
      for (ValidationResult.ValidationIssue issue : processResult.getIssues()) {
        if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
          result.addError("[" + processName + "] " + issue.getCategory(), issue.getMessage(), issue.getRemediation());
        } else {
          result.addWarning("[" + processName + "] " + issue.getCategory(), issue.getMessage(), issue.getRemediation());
        }
      }
    }

    return result;
  }

  /**
   * Validates all processes and returns results organized by process name.
   *
   * <p>
   * This method provides detailed validation results for each ProcessSystem separately, making it easier to identify
   * which process has issues.
   * </p>
   *
   * @return a {@link java.util.Map} mapping process names to their validation results
   */
  public Map<String, ValidationResult> validateAll() {
    Map<String, ValidationResult> results = new LinkedHashMap<>();

    // Add ProcessModel-level validation
    ValidationResult modelResult = new ValidationResult();
    if (processes.isEmpty()) {
      modelResult.addError("ProcessModel", "ProcessModel has no processes added",
          "Add at least one ProcessSystem using add(name, process)");
    }
    results.put("ProcessModel", modelResult);

    // Validate each ProcessSystem
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      results.put(processName, process.validateSetup());
    }

    return results;
  }

  /**
   * Checks if all processes in the model are ready to run.
   *
   * <p>
   * This is a convenience method that returns true if no CRITICAL validation errors exist across all processes. Use
   * this for a quick go/no-go check before running the model.
   * </p>
   *
   * @return true if no critical validation errors exist, false otherwise
   */
  public boolean isReadyToRun() {
    ValidationResult result = validateSetup();
    // Check if there are any CRITICAL errors
    for (ValidationResult.ValidationIssue issue : result.getIssues()) {
      if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get a formatted validation report for all processes.
   *
   * <p>
   * This method provides a human-readable summary of all validation issues across all processes in the model.
   * </p>
   *
   * @return a formatted validation report string
   */
  public String getValidationReport() {
    StringBuilder report = new StringBuilder();
    report.append("=== ProcessModel Validation Report ===\n\n");

    Map<String, ValidationResult> allResults = validateAll();

    int totalIssues = 0;
    int criticalCount = 0;
    int majorCount = 0;

    for (Map.Entry<String, ValidationResult> entry : allResults.entrySet()) {
      String name = entry.getKey();
      ValidationResult result = entry.getValue();

      if (!result.getIssues().isEmpty()) {
        report.append("--- ").append(name).append(" ---\n");
        for (ValidationResult.ValidationIssue issue : result.getIssues()) {
          report.append("  [").append(issue.getSeverity()).append("] ");
          report.append(issue.getMessage()).append("\n");
          if (issue.getRemediation() != null && !issue.getRemediation().isEmpty()) {
            report.append("    Fix: ").append(issue.getRemediation()).append("\n");
          }
          totalIssues++;
          if (issue.getSeverity() == ValidationResult.Severity.CRITICAL) {
            criticalCount++;
          } else if (issue.getSeverity() == ValidationResult.Severity.MAJOR) {
            majorCount++;
          }
        }
        report.append("\n");
      }
    }

    if (totalIssues == 0) {
      report.append("No validation issues found. Model is ready to run.\n");
    } else {
      report.append("Summary: ").append(totalIssues).append(" issue(s) found");
      report.append(" (").append(criticalCount).append(" critical, ");
      report.append(majorCount).append(" major)\n");
      report.append("Ready to run: ").append(criticalCount == 0 ? "YES" : "NO").append("\n");
    }

    return report.toString();
  }

  // ============ NEQSIM FILE SERIALIZATION ============

  /**
   * Saves this ProcessModel (with all ProcessSystems) to a compressed .neqsim file.
   *
   * <p>
   * This is the recommended format for production use, providing compact storage with full model state preservation
   * including all ProcessSystems. The file can be loaded with {@link #loadFromNeqsim(String)}.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessModel model = new ProcessModel();
   * model.add("upstream", upstreamProcess);
   * model.add("downstream", downstreamProcess);
   * model.run();
   * model.saveToNeqsim("multi_process_model.neqsim");
   * </pre>
   *
   * @param filename the file path to save to (recommended extension: .neqsim)
   * @return true if save was successful, false otherwise
   */
  public boolean saveToNeqsim(String filename) {
    boolean success = neqsim.util.serialization.NeqSimXtream.saveNeqsim(this, filename);
    if (success) {
      logger.info("ProcessModel saved to: " + filename);
    } else {
      logger.error("Failed to save ProcessModel to: " + filename);
    }
    return success;
  }

  /**
   * Loads a ProcessModel from a compressed .neqsim file.
   *
   * <p>
   * After loading, the model is automatically run to reinitialize calculations. This ensures the internal state is
   * consistent for all ProcessSystems.
   * </p>
   *
   * <p>
   * Example usage:
   *
   * <pre>
   * ProcessModel loaded = ProcessModel.loadFromNeqsim("multi_process_model.neqsim");
   * // Model is already run and ready to use
   * ProcessSystem upstream = loaded.get("upstream");
   * </pre>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadFromNeqsim(String filename) {
    try {
      Object loaded = neqsim.util.serialization.NeqSimXtream.openNeqsim(filename);
      if (loaded instanceof ProcessModel) {
        ProcessModel model = (ProcessModel) loaded;
        model.run();
        logger.info("ProcessModel loaded from: " + filename);
        return model;
      } else {
        logger.error("Loaded object is not a ProcessModel: " + (loaded != null ? loaded.getClass().getName() : "null"));
        return null;
      }
    } catch (Exception e) {
      logger.error("Failed to load ProcessModel from file: " + filename, e);
      return null;
    }
  }

  /**
   * Saves this ProcessModel with automatic format detection based on file extension.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML (full serialization)</li>
   * <li>.json → JSON state (lightweight, Git-friendly, requires ProcessModelState)</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to save to
   * @return true if save was successful
   */
  public boolean saveAuto(String filename) {
    if (filename.endsWith(".neqsim")) {
      return saveToNeqsim(filename);
    } else if (filename.endsWith(".json")) {
      return saveStateToFile(filename);
    } else {
      // Legacy binary serialization
      try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(filename))) {
        oos.writeObject(this);
        logger.info("ProcessModel saved (binary) to: " + filename);
        return true;
      } catch (IOException e) {
        logger.error("Failed to save ProcessModel to: " + filename, e);
        return false;
      }
    }
  }

  /**
   * Loads a ProcessModel with automatic format detection based on file extension.
   *
   * <p>
   * File format is determined by extension:
   * <ul>
   * <li>.neqsim → XStream compressed XML (full serialization)</li>
   * <li>.json → JSON state (requires matching ProcessSystems already configured)</li>
   * <li>other → Java binary serialization (legacy)</li>
   * </ul>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadAuto(String filename) {
    if (filename.endsWith(".neqsim")) {
      return loadFromNeqsim(filename);
    } else if (filename.endsWith(".json")) {
      return loadStateFromFile(filename);
    } else {
      // Legacy binary serialization
      try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(filename))) {
        ProcessModel model = (ProcessModel) ois.readObject();
        model.run();
        logger.info("ProcessModel loaded (binary) from: " + filename);
        return model;
      } catch (Exception e) {
        logger.error("Failed to load ProcessModel from: " + filename, e);
        return null;
      }
    }
  }

  // ============ JSON STATE SERIALIZATION ============

  /**
   * Exports the current state of this ProcessModel to a JSON file.
   *
   * <p>
   * This exports state for all ProcessSystems in the model. The JSON format is Git-friendly and human-readable,
   * suitable for version control and diffing.
   * </p>
   *
   * @param filename the file path to save to (recommended extension: .json)
   * @return true if save was successful
   */
  public boolean saveStateToFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state = neqsim.process.processmodel.lifecycle.ProcessModelState
          .fromProcessModel(this);
      state.saveToFile(filename);
      logger.info("ProcessModel state saved to: " + filename);
      return true;
    } catch (Exception e) {
      logger.error("Failed to save ProcessModel state to: " + filename, e);
      return false;
    }
  }

  /**
   * Loads ProcessModel state from a JSON file.
   *
   * <p>
   * Note: This returns a new ProcessModel with ProcessSystems initialized from the saved state. Full reconstruction
   * requires the original equipment configuration.
   * </p>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadStateFromFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state = neqsim.process.processmodel.lifecycle.ProcessModelState
          .loadFromFile(filename);
      ProcessModel model = state.toProcessModel();
      logger.info("ProcessModel state loaded from: " + filename);
      return model;
    } catch (Exception e) {
      logger.error("Failed to load ProcessModel state from: " + filename, e);
      return null;
    }
  }

  /**
   * Exports the current state of this ProcessModel for inspection or modification.
   *
   * @return a ProcessModelState snapshot of the current model
   */
  public neqsim.process.processmodel.lifecycle.ProcessModelState exportState() {
    return neqsim.process.processmodel.lifecycle.ProcessModelState.fromProcessModel(this);
  }

  // ============ AUTO-SIZING METHODS ============

  /**
   * Auto-sizes all equipment in this model that implements {@link neqsim.process.design.AutoSizeable}.
   *
   * <p>
   * This method iterates through all process systems in the model and calls autoSize() on each equipment that
   * implements the AutoSizeable interface. The equipment is sized using the default safety factor (1.2 = 20% margin).
   * </p>
   *
   * <p>
   * <strong>Important:</strong> This method should be called AFTER running the process model so that flow rates and
   * conditions are known for sizing calculations.
   * </p>
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * ProcessModel model = new ProcessModel();
   * model.add("upstream", upstreamProcess);
   * model.add("downstream", downstreamProcess);
   * model.run();
   * model.autoSizeEquipment(); // Size all equipment based on actual flow rates
   * model.run(); // Re-run with sized equipment
   * </pre>
   *
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment() {
    return autoSizeEquipment(1.2);
  }

  /**
   * Auto-sizes all equipment in this model with the specified safety factor.
   *
   * <p>
   * This method iterates through all process systems in the model and calls autoSize() on each equipment that
   * implements the AutoSizeable interface.
   * </p>
   *
   * @param safetyFactor multiplier for design capacity, typically 1.1-1.3 (10-30% over design)
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment(double safetyFactor) {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.autoSizeEquipment(safetyFactor);
    }
    return count;
  }

  /**
   * Auto-sizes all equipment in this model using company-specific design standards.
   *
   * <p>
   * This method applies design rules from the specified company's technical requirements (TR) documents. The standards
   * are loaded from the NeqSim design database.
   * </p>
   *
   * @param companyStandard company name (e.g., "Equinor", "Shell", "TotalEnergies")
   * @param trDocument TR document reference (e.g., "TR2000", "DEP-31.38.01.11")
   * @return the number of equipment items that were auto-sized
   */
  public int autoSizeEquipment(String companyStandard, String trDocument) {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.autoSizeEquipment(companyStandard, trDocument);
    }
    return count;
  }

  /**
   * Applies mechanical-design-derived capacity constraints to every equipment item in every process area of this model.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#applyMechanicalDesignCapacityConstraints()}. It iterates
   * over all process areas and, for each equipment, derives capacity constraints from the limits configured on its
   * {@link neqsim.process.mechanicaldesign.MechanicalDesign}. After this call the limits surface in
   * {@link #getUtilizationSnapshotJson()} (per-area, with {@code area} labels) and in each equipment's
   * {@link neqsim.process.equipment.ProcessEquipmentInterface#getMaxUtilization()}.
   * </p>
   *
   * <p>
   * Typical out-of-the-box workflow for a large multi-area plant:
   * </p>
   *
   * <pre>
   * model.run();
   * model.autoSizeEquipment(); // populate maxDesign* limits from flow conditions
   * model.applyMechanicalDesignCapacityConstraints(); // surface them as utilization
   * String snapshot = model.getUtilizationSnapshotJson();
   * </pre>
   *
   * <p>
   * The method is idempotent and never throws. Call it again whenever design limits or operating conditions change.
   * </p>
   *
   * @return the total number of mechanical-design-derived constraints registered across all areas
   */
  public int applyMechanicalDesignCapacityConstraints() {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.applyMechanicalDesignCapacityConstraints();
    }
    return count;
  }

  /**
   * Enables or disables capacity analysis for all equipment in all process systems.
   *
   * <p>
   * This is a convenience method that applies the setting to all equipment in all processes. When disabled, equipment
   * is excluded from:
   * <ul>
   * <li>System bottleneck detection</li>
   * <li>Capacity utilization summaries</li>
   * <li>Equipment near capacity lists</li>
   * <li>Optimization constraint checking</li>
   * </ul>
   *
   * @param enabled true to enable capacity analysis for all equipment, false to disable
   * @return the number of equipment items that were updated
   */
  public int setCapacityAnalysisEnabled(boolean enabled) {
    int count = 0;
    for (ProcessSystem processSystem : processes.values()) {
      count += processSystem.setCapacityAnalysisEnabled(enabled);
    }
    return count;
  }

  // ============ CAPACITY & BOTTLENECK ANALYSIS (whole-plant) ============

  /**
   * Gets all capacity-constrained equipment across every process area in the model.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#getConstrainedEquipment()}. Equipment is returned in
   * area insertion order, and within each area in unit order.
   * </p>
   *
   * @return list of capacity-constrained equipment aggregated across all areas
   */
  public java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> getConstrainedEquipment() {
    java.util.List<neqsim.process.equipment.capacity.CapacityConstrainedEquipment> result = new java.util.ArrayList<neqsim.process.equipment.capacity.CapacityConstrainedEquipment>();
    for (ProcessSystem processSystem : processes.values()) {
      result.addAll(processSystem.getConstrainedEquipment());
    }
    return result;
  }

  /**
   * Identifies the single equipment with the highest capacity utilization across the whole plant.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#getBottleneck()}. It evaluates each area's bottleneck
   * and returns the most heavily utilized unit plant-wide.
   * </p>
   *
   * @return the plant-wide bottleneck equipment, or {@code null} if no equipment has capacity defined
   */
  public ProcessEquipmentInterface getBottleneck() {
    ProcessEquipmentInterface bottleneck = null;
    double maxUtilization = 0.0;
    for (ProcessSystem processSystem : processes.values()) {
      ProcessEquipmentInterface areaBottleneck = processSystem.getBottleneck();
      if (areaBottleneck == null) {
        continue;
      }
      double utilization = processSystem.getBottleneckUtilization();
      if (!Double.isNaN(utilization) && !Double.isInfinite(utilization) && utilization > maxUtilization) {
        maxUtilization = utilization;
        bottleneck = areaBottleneck;
      }
    }
    return bottleneck;
  }

  /**
   * Gets the utilization ratio of the plant-wide bottleneck equipment.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#getBottleneckUtilization()}.
   * </p>
   *
   * @return utilization as a fraction (1.0 = 100%), or 0.0 if no bottleneck is found
   */
  public double getBottleneckUtilization() {
    double maxUtilization = 0.0;
    for (ProcessSystem processSystem : processes.values()) {
      if (processSystem.getBottleneck() == null) {
        continue;
      }
      double utilization = processSystem.getBottleneckUtilization();
      if (!Double.isNaN(utilization) && !Double.isInfinite(utilization) && utilization > maxUtilization) {
        maxUtilization = utilization;
      }
    }
    return maxUtilization;
  }

  /**
   * Checks whether any equipment in any area exceeds a HARD capacity limit.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#isAnyHardLimitExceeded()}.
   * </p>
   *
   * @return true if any HARD constraint is exceeded in any area
   */
  public boolean isAnyHardLimitExceeded() {
    for (ProcessSystem processSystem : processes.values()) {
      if (processSystem.isAnyHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Finds the global capacity bottleneck across every process area in this model.
   *
   * <p>
   * Each area's most-constrained unit is evaluated via {@link ProcessSystem#findBottleneck()} and the unit with the
   * highest utilization across the whole plant is returned. This makes the reservoir / subsurface, midstream and
   * topside areas compete on a single ranking so the true field-wide limiting constraint is surfaced rather than the
   * bottleneck of a single area.
   * </p>
   *
   * @return the plant-wide {@link neqsim.process.equipment.capacity.BottleneckResult}; an empty result if no enabled
   * constraints are found in any area
   */
  public neqsim.process.equipment.capacity.BottleneckResult findBottleneck() {
    neqsim.process.equipment.capacity.BottleneckResult best = neqsim.process.equipment.capacity.BottleneckResult
        .empty();
    double maxUtil = -1.0;
    for (ProcessSystem ps : processes.values()) {
      neqsim.process.equipment.capacity.BottleneckResult areaResult = ps.findBottleneck();
      if (areaResult != null && areaResult.hasBottleneck()) {
        double util = areaResult.getUtilizationPercent();
        if (util > maxUtil) {
          maxUtil = util;
          best = areaResult;
        }
      }
    }
    return best;
  }

  /**
   * Checks whether any unit in any area is overloaded (utilization above 100%).
   *
   * @return true if any enabled constraint in any area is exceeded
   */
  public boolean isAnyEquipmentOverloaded() {
    for (ProcessSystem ps : processes.values()) {
      if (ps.isAnyEquipmentOverloaded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds an area-qualified capacity-utilization summary across the whole plant.
   *
   * <p>
   * Keys use the {@code "area::unit"} convention so units with the same name in different areas do not collide. Values
   * are maximum constraint utilization in percent.
   * </p>
   *
   * @return ordered map of {@code "area::unit"} to utilization percentage
   */
  public Map<String, Double> getCapacityUtilizationSummary() {
    Map<String, Double> summary = new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String area = entry.getKey();
      Map<String, Double> areaSummary = entry.getValue().getCapacityUtilizationSummary();
      for (Map.Entry<String, Double> u : areaSummary.entrySet()) {
        summary.put(area + "::" + u.getKey(), u.getValue());
      }
    }
    return summary;
  }

  /**
   * Returns the area-qualified names of units that are near their capacity limit (above the warning threshold) in any
   * area.
   *
   * @return list of {@code "area::unit"} names near a capacity limit
   */
  public List<String> getEquipmentNearCapacityLimit() {
    List<String> nearLimit = new ArrayList<String>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String area = entry.getKey();
      for (String unitName : entry.getValue().getEquipmentNearCapacityLimit()) {
        nearLimit.add(area + "::" + unitName);
      }
    }
    return nearLimit;
  }

  /**
   * Disables all capacity constraints on every unit in every area of this model (what-if analysis).
   *
   * @return the total number of constraints disabled across all areas
   */
  public int disableAllConstraints() {
    int total = 0;
    for (ProcessSystem ps : processes.values()) {
      total += ps.disableAllConstraints();
    }
    return total;
  }

  /**
   * Enables all capacity constraints on every unit in every area of this model.
   *
   * <p>
   * This is the plant-wide preset that switches subsurface, midstream and topside constraints on in one call, mirroring
   * {@link ProcessSystem#enableAllConstraints()} at the multi-area level.
   * </p>
   *
   * @return the total number of constraints enabled across all areas
   */
  public int enableAllConstraints() {
    int total = 0;
    for (ProcessSystem ps : processes.values()) {
      total += ps.enableAllConstraints();
    }
    return total;
  }

  /**
   * Returns a stable, side-effect-free JSON utilization snapshot of every unit across all process areas in this plant.
   *
   * <p>
   * This is the multi-area counterpart of {@link ProcessSystem#getUtilizationSnapshotJson()} and the recommended
   * observation endpoint for machine-learning / reinforcement-learning optimization loops on a full plant. Each unit
   * entry carries an {@code "area"} property. A non-null plant-wide {@code bottleneck} carries both {@code "area"} and
   * the unambiguous {@code "qualifiedName"} ({@code "area::unit"}), in addition to its legacy {@code "name"}. The
   * {@code anyOverloaded} and {@code anyHardLimitExceeded} flags summarise the whole model. Schema is versioned by
   * {@code schemaVersion} ("1.0").
   * </p>
   *
   * <p>
   * The method does <b>not</b> run the model; call {@link #run()} (or
   * {@link neqsim.process.automation.ProcessAutomation#evaluate}) first so the reported utilization reflects the latest
   * setpoints.
   * </p>
   *
   * @return JSON string {@code {schemaVersion, name, units:[...], bottleneck:{...}, anyOverloaded,
   * anyHardLimitExceeded}}
   */
  public String getUtilizationSnapshotJson() {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", "1.0");
    com.google.gson.JsonArray unitsArr = new com.google.gson.JsonArray();
    for (java.util.Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String areaName = entry.getKey();
      unitsArr.addAll(entry.getValue().buildUtilizationUnitsJson(areaName));
    }
    root.add("units", unitsArr);

    neqsim.process.equipment.capacity.BottleneckResult bottleneck = findBottleneck();
    if (bottleneck != null && bottleneck.getEquipment() != null) {
      com.google.gson.JsonObject bn = new com.google.gson.JsonObject();
      bn.addProperty("name", bottleneck.getEquipment().getName());
      String areaName = findAreaNameForEquipment(bottleneck.getEquipment());
      if (areaName != null) {
        bn.addProperty("area", areaName);
        bn.addProperty("qualifiedName", areaName + "::" + bottleneck.getEquipment().getName());
      }
      bn.addProperty("utilization", bottleneck.getUtilization());
      bn.addProperty("utilizationPercent", bottleneck.getUtilization() * 100.0);
      if (bottleneck.getConstraint() != null) {
        bn.addProperty("limitingConstraint", bottleneck.getConstraint().getName());
      }
      root.add("bottleneck", bn);
    } else {
      root.add("bottleneck", com.google.gson.JsonNull.INSTANCE);
    }
    root.addProperty("anyOverloaded", isAnyEquipmentOverloaded());
    root.addProperty("anyHardLimitExceeded", isAnyHardLimitExceeded());
    return root.toString();
  }

  /**
   * Finds the process-area name that owns the supplied equipment instance.
   *
   * @param equipment equipment instance returned by the plant-wide bottleneck ranking
   * @return owning area name, or {@code null} when the instance is not present in this model
   */
  private String findAreaNameForEquipment(ProcessEquipmentInterface equipment) {
    for (java.util.Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      for (ProcessEquipmentInterface areaEquipment : entry.getValue().getUnitOperations()) {
        if (areaEquipment == equipment) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  // ============ PRIVATE HOOK / EVENT HELPER METHODS ============

  /**
   * Notify the listener that the model is starting.
   *
   * @param totalAreas total number of process areas
   */
  private void notifyModelStart(int totalAreas) {
    if (progressListener != null) {
      try {
        progressListener.onModelStart(totalAreas);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onModelStart: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that the model has completed.
   *
   * @param totalIterations total iterations performed
   * @param converged whether the model converged
   */
  private void notifyModelComplete(int totalIterations, boolean converged) {
    if (progressListener != null) {
      try {
        progressListener.onModelComplete(totalIterations, converged);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onModelComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that an iteration is about to start.
   *
   * @param iterationNumber the iteration about to start
   */
  private void notifyBeforeIteration(int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onBeforeIteration(iterationNumber);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onBeforeIteration: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that an iteration has completed.
   *
   * @param iterationNumber the iteration that completed
   * @param converged whether convergence was achieved
   * @param maxError maximum relative error across all variables
   */
  private void notifyIterationComplete(int iterationNumber, boolean converged, double maxError) {
    if (progressListener != null) {
      try {
        progressListener.onIterationComplete(iterationNumber, converged, maxError);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onIterationComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that a process area is about to run.
   *
   * @param areaName name of the area
   * @param process the ProcessSystem
   * @param areaIndex area index
   * @param totalAreas total number of areas
   * @param iterationNumber current iteration
   */
  private void notifyBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex, int totalAreas,
      int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onBeforeProcessArea(areaName, process, areaIndex, totalAreas, iterationNumber);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onBeforeProcessArea: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that a process area has completed.
   *
   * @param areaName name of the area
   * @param process the ProcessSystem
   * @param areaIndex area index
   * @param totalAreas total number of areas
   * @param iterationNumber current iteration
   */
  private void notifyProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex, int totalAreas,
      int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onProcessAreaComplete(areaName, process, areaIndex, totalAreas, iterationNumber);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onProcessAreaComplete: " + ex.getMessage());
      }
    }
  }

  /**
   * Notify the listener that a process area encountered an error.
   *
   * @param areaName name of the failed area
   * @param process the ProcessSystem that failed
   * @param exception the exception
   * @return true to continue execution, false to abort
   */
  private boolean notifyProcessAreaError(String areaName, ProcessSystem process, Exception exception) {
    if (progressListener != null) {
      try {
        return progressListener.onProcessAreaError(areaName, process, exception);
      } catch (Exception ex) {
        logger.warn("ModelProgressListener threw exception in onProcessAreaError: " + ex.getMessage());
      }
    }
    return false;
  }

  /**
   * Publish a model-level event to the ProcessEventBus if event publishing is enabled.
   *
   * @param type the event type
   * @param description event description
   * @param severity event severity
   */
  private void publishModelEvent(ProcessEvent.EventType type, String description, ProcessEvent.Severity severity) {
    if (publishEvents) {
      try {
        ProcessEvent event = new ProcessEvent(ProcessEvent.generateId(), type, "ProcessModel", description, severity);
        ProcessEventBus.getInstance().publish(event);
      } catch (Exception ex) {
        logger.warn("Failed to publish ProcessModel event: " + ex.getMessage());
      }
    }
  }

  /**
   * Run auto-validation on all ProcessSystems. Called once before the first iteration when autoValidate is enabled.
   * Validation failures are logged as warnings.
   */
  private void runModelAutoValidation() {
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String areaName = entry.getKey();
      ProcessSystem process = entry.getValue();
      try {
        ValidationResult result = process.validateSetup();
        if (result != null && !result.isValid()) {
          logger.warn("Validation warning for area '" + areaName + "': " + result);
          if (publishEvents) {
            publishModelEvent(ProcessEvent.EventType.WARNING,
                "Validation warning for area '" + areaName + "': " + result.toString(), ProcessEvent.Severity.WARNING);
          }
        }
      } catch (Exception ex) {
        logger.debug("Could not validate area '" + areaName + "': " + ex.getMessage());
      }
    }
  }

  // ========================== Checkpointing ==========================

  /**
   * Checks if automatic checkpointing is enabled.
   *
   * @return true if checkpointing is enabled
   */
  public boolean isCheckpointEnabled() {
    return checkpointEnabled;
  }

  /**
   * Sets whether automatic checkpointing is enabled during model execution.
   *
   * @param checkpointEnabled true to enable checkpointing
   */
  public void setCheckpointEnabled(boolean checkpointEnabled) {
    this.checkpointEnabled = checkpointEnabled;
  }

  /**
   * Gets the checkpoint interval (number of iterations between checkpoints).
   *
   * @return the checkpoint interval
   */
  public int getCheckpointInterval() {
    return checkpointInterval;
  }

  /**
   * Sets the checkpoint interval.
   *
   * @param checkpointInterval number of iterations between automatic checkpoints
   */
  public void setCheckpointInterval(int checkpointInterval) {
    this.checkpointInterval = checkpointInterval;
  }

  /**
   * Gets the file path for checkpoint files.
   *
   * @return the checkpoint file path, or null if not set
   */
  public String getCheckpointPath() {
    return checkpointPath;
  }

  /**
   * Sets the file path for saving checkpoint files.
   *
   * @param checkpointPath the file path for checkpoint files
   */
  public void setCheckpointPath(String checkpointPath) {
    this.checkpointPath = checkpointPath;
  }

  // ========================== Automation API ==========================

  /**
   * Cached automation facade for this process model. Lazily initialized on first call so that diagnostic state persists
   * across calls.
   */
  private transient neqsim.process.automation.ProcessAutomation cachedAutomation;

  /**
   * Returns an automation facade for this process model. The facade provides a stable, string-addressable API for
   * scripts and AI agents to interact with all process areas using area-qualified addresses like
   * {@code "AreaName::UnitName.property"}.
   *
   * <p>
   * The facade is cached and reused across calls so that diagnostics (learned corrections, operation history,
   * dirty-state tracking) persist for the lifetime of the process model.
   * </p>
   *
   * @return a {@link neqsim.process.automation.ProcessAutomation} facade
   */
  public neqsim.process.automation.ProcessAutomation getAutomation() {
    if (cachedAutomation == null) {
      cachedAutomation = new neqsim.process.automation.ProcessAutomation(this);
    }
    return cachedAutomation;
  }

  /**
   * Returns the names of all unit operations across all process areas. Names are area-qualified in the format
   * {@code "AreaName::UnitName"}. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getUnitList()}.
   *
   * @return unmodifiable list of area-qualified unit operation names
   */
  public List<String> getUnitNames() {
    return getAutomation().getUnitList();
  }

  /**
   * Returns the names of all process areas. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getAreaList()}.
   *
   * @return unmodifiable list of area names
   */
  public List<String> getAreaNames() {
    return getAutomation().getAreaList();
  }

  /**
   * Returns the names of unit operations in a specific process area. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getUnitList(String)}.
   *
   * @param areaName the name of the process area
   * @return unmodifiable list of unit operation names
   * @throws IllegalArgumentException if the area is not found
   */
  public List<String> getUnitNames(String areaName) {
    return getAutomation().getUnitList(areaName);
  }

  /**
   * Returns all available variables for the named unit operation. The {@code unitName} may be area-qualified:
   * {@code "AreaName::UnitName"}. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getVariableList(String)}.
   *
   * @param unitName the name of the unit operation, optionally area-qualified
   * @return list of variable descriptors
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<neqsim.process.automation.SimulationVariable> getVariableList(String unitName) {
    return getAutomation().getVariableList(unitName);
  }

  /**
   * Reads the current value of a simulation variable by its address. The address should be area-qualified:
   * {@code "AreaName::unitName.property"}. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#getVariableValue(String, String)}.
   *
   * @param address the area-qualified address, e.g. "Separation::HP Sep.gasOutStream.temperature"
   * @param unitOfMeasure the desired unit, e.g. "C", "bara", "kg/hr"
   * @return the variable value in the requested unit
   * @throws IllegalArgumentException if the address cannot be resolved
   */
  public double getVariableValue(String address, String unitOfMeasure) {
    return getAutomation().getVariableValue(address, unitOfMeasure);
  }

  /**
   * Sets the value of a simulation input variable. The address should be area-qualified:
   * {@code "AreaName::Compressor.outletPressure"}. Convenience delegate for
   * {@link neqsim.process.automation.ProcessAutomation#setVariableValue(String, double, String)}.
   *
   * @param address the area-qualified address, e.g. "Compression::Compressor.outletPressure"
   * @param value the value to set
   * @param unitOfMeasure the unit of the provided value, e.g. "bara", "C"
   * @throws IllegalArgumentException if the address cannot be resolved or the variable is read-only
   */
  public void setVariableValue(String address, double value, String unitOfMeasure) {
    getAutomation().setVariableValue(address, value, unitOfMeasure);
  }
}
