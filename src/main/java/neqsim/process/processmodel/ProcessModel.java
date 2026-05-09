package neqsim.process.processmodel;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.util.event.ProcessEvent;
import neqsim.process.util.event.ProcessEventBus;
import neqsim.process.util.report.Report;
import neqsim.util.validation.ValidationResult;

/**
 * <p>
 * ProcessModel class. Manages a collection of processes that can be run in steps or continuously.
 * </p>
 *
 * <p>
 * This class supports serialization via {@link #saveToNeqsim(String)} and
 * {@link #loadFromNeqsim(String)} for full model persistence.
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

  private boolean runStep = false;
  private int maxIterations = 50;
  private boolean useOptimizedExecution = true;

  /**
   * Transient listener for model-level progress callbacks. Marked transient to avoid serialization
   * issues.
   */
  private transient ModelProgressListener progressListener = null;

  /**
   * When true, lifecycle events are published to the ProcessEventBus singleton during model
   * execution. Default is false for zero overhead when not needed.
   */
  private boolean publishEvents = false;

  /**
   * When true, validateSetup() is called on each ProcessSystem before the first iteration.
   * Validation warnings are logged but do not abort execution.
   */
  private boolean autoValidate = false;

  /**
   * When true, every ProcessSystem registered with this model has flash warm-start enabled for the
   * duration of its run (via {@link ProcessSystem#setUseFlashWarmStart(boolean)}). Default is
   * {@code false}. Setting this flag updates all currently registered ProcessSystems and applies to
   * any ProcessSystem added afterwards.
   */
  private boolean useFlashWarmStart = false;

  /** Whether automatic checkpointing is enabled during model execution. */
  private boolean checkpointEnabled = false;

  /** Number of iterations between automatic checkpoints. */
  private int checkpointInterval = 10;

  /** File path for saving checkpoint files. */
  private String checkpointPath = null;

  /**
   * Interface for monitoring ProcessModel execution progress. Implementations receive callbacks at
   * the model level: before/after each process area runs, before/after each outer iteration, and
   * when the model starts/completes.
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
    void onProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
        int totalAreas, int iterationNumber);

    /**
     * Called before a process area is executed.
     *
     * @param areaName the name of the process area about to run
     * @param process the ProcessSystem about to run
     * @param areaIndex zero-based index of the area
     * @param totalAreas total number of process areas
     * @param iterationNumber current outer iteration number (starts at 1)
     */
    default void onBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
        int totalAreas, int iterationNumber) {
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
    default boolean onProcessAreaError(String areaName, ProcessSystem process,
        Exception exception) {
      return false;
    }
  }

  // Convergence tolerances (relative errors)
  private double flowTolerance = 1e-4;
  private double temperatureTolerance = 1e-4;
  private double pressureTolerance = 1e-4;

  // Convergence tracking
  private int lastIterationCount = 0;
  private double lastMaxFlowError = Double.MAX_VALUE;
  private double lastMaxTemperatureError = Double.MAX_VALUE;
  private double lastMaxPressureError = Double.MAX_VALUE;
  private boolean modelConverged = false;
  private boolean lastAllProcessesSolved = false;
  private boolean lastBoundaryValuesConverged = false;
  private int lastBoundaryStreamCount = 0;

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
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which
   * auto-selects the best execution strategy based on topology.
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
   * When enabled (default), each ProcessSystem uses {@link ProcessSystem#runOptimized()} which
   * auto-selects the best execution strategy (parallel for feed-forward, hybrid for recycle
   * processes). When disabled, uses standard sequential {@link ProcessSystem#run()}.
   * </p>
   *
   * @param useOptimizedExecution true to enable optimized execution
   */
  public void setUseOptimizedExecution(boolean useOptimizedExecution) {
    this.useOptimizedExecution = useOptimizedExecution;
  }

  /**
   * Enable or disable flash warm-start K-values for every ProcessSystem in this model.
   *
   * <p>
   * When enabled, the iterative TPflash inside every fluid evaluation re-uses the previously
   * converged K-values as the initial estimate instead of seeding from Wilson on every call. This
   * is delegated to {@link ProcessSystem#setUseFlashWarmStart(boolean)} on every currently
   * registered ProcessSystem and is also applied to any ProcessSystem added afterwards via
   * {@link #add(String, ProcessSystem)}. Each ProcessSystem manages the underlying
   * {@code ThermodynamicModelSettings} flag with try/finally inside its own {@code run()} so the
   * setting never leaks past the model run. Default is {@code false} (historical behaviour) —
   * recycle-heavy multi-area models are sensitive to flash trajectory and warm-start can shift the
   * converged fixed point.
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
  }

  /**
   * Set all tolerances at once.
   *
   * @param tolerance relative tolerance for all variables (flow, temperature, pressure)
   */
  public void setTolerance(double tolerance) {
    this.flowTolerance = tolerance;
    this.temperatureTolerance = tolerance;
    this.pressureTolerance = tolerance;
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
   * Enables or disables event publishing to the ProcessEventBus singleton. When enabled, lifecycle
   * events (model start/complete, area errors, convergence) are published during execution.
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
   * Enables or disables automatic validation of each ProcessSystem before the first iteration. When
   * enabled, validateSetup() is called on each ProcessSystem. Validation failures are logged as
   * warnings but do not abort execution.
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
    processes.put(name, process);
    return true;
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

  /**
   * Generates IEC 81346 reference designations for all equipment across all process areas in this
   * model. Each area receives a unique function sub-level (A1, A2, A3, ...).
   *
   * <p>
   * This is a convenience wrapper around
   * {@link neqsim.process.equipment.iec81346.ReferenceDesignationGenerator}.
   * </p>
   *
   * @param locationPrefix the location-aspect prefix (e.g. "P1" for a specific platform)
   * @return the generator instance (for further queries such as {@code toJson()})
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator generateReferenceDesignations(
      String locationPrefix) {
    neqsim.process.equipment.iec81346.ReferenceDesignationGenerator gen =
        new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator(this);
    gen.setLocationPrefix(locationPrefix);
    gen.generate();
    return gen;
  }

  /**
   * Generates IEC 81346 reference designations with hierarchical function structure. Each area
   * receives a nested function sub-level under the given prefix (e.g. "A1.A1", "A1.A2").
   *
   * @param functionPrefix the top-level function prefix (e.g. "A1")
   * @param locationPrefix the location-aspect prefix (e.g. "P1")
   * @return the generator instance
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignationGenerator generateReferenceDesignations(
      String functionPrefix, String locationPrefix) {
    neqsim.process.equipment.iec81346.ReferenceDesignationGenerator gen =
        new neqsim.process.equipment.iec81346.ReferenceDesignationGenerator(this);
    gen.setFunctionPrefix(functionPrefix);
    gen.setLocationPrefix(locationPrefix);
    gen.setUseHierarchicalFunctions(true);
    gen.generate();
    return gen;
  }

  /**
   * Looks up a process equipment unit across all process areas by its IEC 81346 reference
   * designation string (e.g. {@code "=A1.B1"}, {@code "-B1"}).
   *
   * @param refDesignation the reference designation string to match
   * @return the matching equipment, or {@code null} if not found in any area
   */
  public neqsim.process.equipment.ProcessEquipmentInterface getUnitByReferenceDesignation(
      String refDesignation) {
    if (refDesignation == null || refDesignation.trim().isEmpty()) {
      return null;
    }
    for (ProcessSystem system : processes.values()) {
      neqsim.process.equipment.ProcessEquipmentInterface found =
          system.getUnitByReferenceDesignation(refDesignation);
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
    return processes.remove(name) != null;
  }

  /**
   * Apply an acceleration method to every {@link neqsim.process.equipment.util.Recycle Recycle}
   * unit across all areas in this {@code ProcessModel}.
   *
   * <p>
   * For large multi-area plants with many recycle loops, Wegstein acceleration typically reduces
   * outer-loop iteration count by 2-3x over the default direct substitution. This is a bulk
   * convenience that delegates to
   * {@link ProcessSystem#setRecycleAccelerationMethod(neqsim.process.equipment.util.AccelerationMethod)}
   * on every registered area.
   * </p>
   *
   * @param method acceleration method to apply (must not be {@code null})
   * @return total number of {@code Recycle} units updated across all areas
   */
  public int setRecycleAccelerationMethod(neqsim.process.equipment.util.AccelerationMethod method) {
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
   * Total change in stream exergy (outlet − inlet) aggregated over every unit operation in every
   * process area. Each area contributes using its own
   * {@link ProcessSystem#getSurroundingTemperature() surrounding temperature}.
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
   * Total exergy destruction rate aggregated over every unit operation in every process area. Each
   * area contributes using its own surrounding temperature.
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
   * Build a structured {@link neqsim.process.util.exergy.ExergyAnalysisReport} covering every unit
   * operation in every process area, with each entry tagged by its area name. The surrounding
   * temperature of the report is taken from the first registered area (or 288.15 K if the model is
   * empty).
   *
   * @return a new report suitable for ranking destruction hot-spots across a plant-wide flowsheet
   */
  public neqsim.process.util.exergy.ExergyAnalysisReport getExergyAnalysis() {
    double t0 = 288.15;
    if (!processes.isEmpty()) {
      t0 = processes.values().iterator().next().getSurroundingTemperature();
    }
    neqsim.process.util.exergy.ExergyAnalysisReport report =
        new neqsim.process.util.exergy.ExergyAnalysisReport(t0);
    for (Map.Entry<String, ProcessSystem> e : processes.entrySet()) {
      e.getValue().populateExergyAnalysis(report, e.getValue().getSurroundingTemperature(),
          e.getKey());
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
   * {@inheritDoc}
   *
   * <p>
   * - If runStep == true, each process is run in "step" mode exactly once. - Otherwise (continuous
   * mode), it loops up to maxIterations or until all processes are finished (isFinished() == true).
   * If forceIteration is true, the loop runs all maxIterations regardless of convergence.
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
    publishModelEvent(ProcessEvent.EventType.INFO,
        "ProcessModel starting with " + totalAreas + " process areas", ProcessEvent.Severity.INFO);

    // Auto-validate all ProcessSystems before first iteration
    if (autoValidate) {
      runModelAutoValidation();
    }

    if (runStep) {
      lastIterationCount = 1;
      modelConverged = true;
      lastMaxFlowError = 0.0;
      lastMaxTemperatureError = 0.0;
      lastMaxPressureError = 0.0;
      lastAllProcessesSolved = true;
      lastBoundaryValuesConverged = true;
      lastBoundaryStreamCount = collectBoundaryStreams().size();
      // Step mode: just run each process once in step mode
      int areaIdx = 0;
      for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
        try {
          if (Thread.currentThread().isInterrupted()) {
            logger.debug("Thread was interrupted, exiting run()...");
            return;
          }
          notifyBeforeProcessArea(entry.getKey(), entry.getValue(), areaIdx, totalAreas, 1);
          entry.getValue().run_step();
          notifyProcessAreaComplete(entry.getKey(), entry.getValue(), areaIdx, totalAreas, 1);
        } catch (Exception e) {
          logger.error("Error running process step: " + e.getMessage(), e);
          publishModelEvent(ProcessEvent.EventType.ERROR,
              "Error in process area '" + entry.getKey() + "': " + e.getMessage(),
              ProcessEvent.Severity.ERROR);
          if (!notifyProcessAreaError(entry.getKey(), entry.getValue(), e)) {
            break;
          }
        }
        areaIdx++;
      }
      notifyModelComplete(1, true);
      publishModelEvent(ProcessEvent.EventType.SIMULATION_COMPLETE,
          "ProcessModel step mode completed", ProcessEvent.Severity.INFO);
    } else {
      // Reset convergence tracking
      lastIterationCount = 0;
      modelConverged = false;
      lastMaxFlowError = Double.MAX_VALUE;
      lastMaxTemperatureError = Double.MAX_VALUE;
      lastMaxPressureError = Double.MAX_VALUE;
      lastAllProcessesSolved = false;
      lastBoundaryValuesConverged = false;
      lastBoundaryStreamCount = 0;

      // Capture initial stream states for convergence tracking. Restrict to
      // streams that cross area boundaries - these are the only streams whose
      // values change between outer iterations. For a 500-stream plant with
      // 10 boundary streams this cuts capture cost by ~50x.
      java.util.Set<Object> boundaryStreams = collectBoundaryStreams();
      lastBoundaryStreamCount = boundaryStreams.size();
      Map<String, double[]> previousStreamStates = captureStreamStates(boundaryStreams);

      int iterations = 0;
      while (!Thread.currentThread().isInterrupted() && iterations < maxIterations) {
        // Notify before-iteration
        notifyBeforeIteration(iterations + 1);

        // Run all processes - use parallel execution for independent systems
        runAllProcessesWithHooks(iterations + 1);
        iterations++;

        // Capture current stream states and calculate errors
        Map<String, double[]> currentStreamStates = captureStreamStates(boundaryStreams);
        double[] errors = calculateConvergenceErrors(previousStreamStates, currentStreamStates);
        lastMaxFlowError = errors[0];
        lastMaxTemperatureError = errors[1];
        lastMaxPressureError = errors[2];

        // Check if model has converged
        boolean allProcessesSolved = isFinished();
        boolean valuesConverged =
            lastMaxFlowError < flowTolerance && lastMaxTemperatureError < temperatureTolerance
                && lastMaxPressureError < pressureTolerance;
        lastAllProcessesSolved = allProcessesSolved;
        lastBoundaryValuesConverged = valuesConverged;

        if (logger.isDebugEnabled()) {
          logger.debug("Iteration " + iterations + ": flowErr=" + lastMaxFlowError + ", tempErr="
              + lastMaxTemperatureError + ", pressErr=" + lastMaxPressureError + ", allSolved="
              + allProcessesSolved + ", valuesConverged=" + valuesConverged);
        }

        double maxError = getError();

        // Notify iteration complete
        boolean boundaryDrivenModel = !boundaryStreams.isEmpty();
        boolean iterConverged =
            valuesConverged && iterations > 1 && (allProcessesSolved || boundaryDrivenModel);
        notifyIterationComplete(iterations, iterConverged, maxError);

        // Converged if all processes solved AND values are not changing
        if (iterConverged) {
          modelConverged = true;
          logger.debug("ProcessModel converged after " + iterations + " iterations");
          break;
        }

        // Update previous states for next iteration
        previousStreamStates = currentStreamStates;
      }
      lastIterationCount = iterations;

      if (!modelConverged && iterations >= maxIterations) {
        logger.warn("ProcessModel reached max iterations (" + maxIterations
            + ") without full convergence. Flow error: " + lastMaxFlowError + ", Temp error: "
            + lastMaxTemperatureError);
        publishModelEvent(
            ProcessEvent.EventType.WARNING, "ProcessModel did not converge after " + maxIterations
                + " iterations. Max error: " + String.format("%.2e", getError()),
            ProcessEvent.Severity.WARNING);
      }

      notifyModelComplete(lastIterationCount, modelConverged);
      publishModelEvent(ProcessEvent.EventType.SIMULATION_COMPLETE,
          "ProcessModel completed: " + (modelConverged ? "CONVERGED" : "NOT CONVERGED") + " after "
              + lastIterationCount + " iterations",
          ProcessEvent.Severity.INFO);
    }
  }

  /**
   * Runs all ProcessSystems, using parallel execution for independent systems.
   *
   * <p>
   * If there are multiple independent ProcessSystems (no shared streams between them), they are
   * executed concurrently using the NeqSim thread pool. Systems that depend on each other are
   * executed sequentially in insertion order.
   * </p>
   */
  private void runAllProcesses() {
    if (processes.size() <= 1) {
      // Single process - run directly, no parallelism overhead
      for (ProcessSystem process : processes.values()) {
        runSingleProcess(process);
      }
      return;
    }

    // Partition processes into levels based on inter-area stream dependencies.
    // Areas at the same level are independent and can run in parallel; later
    // levels run after their predecessors complete. This generalises the
    // previous all-or-nothing logic: a 6-area plant with 4 independent areas
    // and one producer→consumer pair now parallelises the 4 (plus the pair
    // serialised) instead of falling back to fully sequential.
    List<List<ProcessSystem>> levels = buildAreaExecutionLevels();
    for (List<ProcessSystem> level : levels) {
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      if (level.size() == 1) {
        runSingleProcess(level.get(0));
      } else {
        List<Future<?>> futures = new ArrayList<>();
        for (ProcessSystem process : level) {
          final ProcessSystem proc = process;
          futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
            runSingleProcess(proc);
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
    try {
      if (useOptimizedExecution) {
        process.runOptimized();
      } else {
        process.run();
      }
    } catch (Exception e) {
      logger.error("Error running process " + process.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * Runs all ProcessSystems with listener hooks, firing before/after area callbacks sequentially.
   * For dependent processes (shared streams), runs sequentially with hooks. For independent
   * processes without a listener, delegates to the parallel strategy.
   *
   * @param iterationNumber current outer iteration number (starts at 1)
   */
  private void runAllProcessesWithHooks(int iterationNumber) {
    int totalAreas = processes.size();

    // If no listener is attached and events disabled, delegate to the
    // parallel-aware method
    if (progressListener == null && !publishEvents) {
      runAllProcesses();
      return;
    }

    // Build area execution levels. Areas on the same level run in parallel;
    // consecutive levels run sequentially. Hooks fire before/after each area.
    List<List<ProcessSystem>> levels = buildAreaExecutionLevels();
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
      // Fire "before" hooks for all areas in this level first (main thread)
      for (ProcessSystem process : level) {
        notifyBeforeProcessArea(areaName.get(process), process, areaIndex.get(process), totalAreas,
            iterationNumber);
      }
      if (level.size() == 1) {
        ProcessSystem process = level.get(0);
        try {
          runSingleProcess(process);
          notifyProcessAreaComplete(areaName.get(process), process, areaIndex.get(process),
              totalAreas, iterationNumber);
        } catch (Exception e) {
          publishModelEvent(ProcessEvent.EventType.ERROR,
              "Error in process area '" + areaName.get(process) + "': " + e.getMessage(),
              ProcessEvent.Severity.ERROR);
          if (!notifyProcessAreaError(areaName.get(process), process, e)) {
            return;
          }
        }
      } else {
        List<Future<?>> futures = new ArrayList<>();
        for (ProcessSystem process : level) {
          final ProcessSystem proc = process;
          futures.add(neqsim.util.NeqSimThreadPool.submit(() -> {
            runSingleProcess(proc);
          }));
        }
        waitForFutures(futures);
        // Fire "after" hooks for the completed level
        for (ProcessSystem process : level) {
          notifyProcessAreaComplete(areaName.get(process), process, areaIndex.get(process),
              totalAreas, iterationNumber);
        }
      }
    }
  }

  /**
   * Finds groups of independent ProcessSystems that can run in parallel.
   *
   * <p>
   * Two ProcessSystems are dependent if any outlet stream of one is used as an inlet stream of
   * another. Independent systems have no shared stream references.
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
      java.util.Set<Object> streams =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
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
   * Partitions ProcessSystems into execution levels based on inter-area stream dependencies. Areas
   * at the same level share no producer/consumer link and can run in parallel; areas at level N+1
   * start only after all areas at level N complete.
   *
   * <p>
   * Direction is inferred from stream ownership: if a stream is an outlet of some equipment in area
   * A and also present (directly or as a consumed inlet) in area B, then A is the producer and B is
   * the consumer, so A → B in the meta-graph. Ambiguous links (stream is produced in both areas)
   * fall back to insertion order to preserve legacy behaviour.
   * </p>
   *
   * @return ordered list of execution levels; each level contains areas that can run in parallel
   */
  private List<List<ProcessSystem>> buildAreaExecutionLevels() {
    List<ProcessSystem> allProcesses = new ArrayList<>(processes.values());
    int n = allProcesses.size();

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
      java.util.Set<Object> outs =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      java.util.Set<Object> mem =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (Object unit : p.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          mem.add(unit);
        }
        if (unit instanceof neqsim.process.equipment.ProcessEquipmentInterface) {
          try {
            java.util.List<StreamInterface> outletStreams =
                ((neqsim.process.equipment.ProcessEquipmentInterface) unit).getOutletStreams();
            if (outletStreams != null) {
              outs.addAll(outletStreams);
            }
          } catch (Exception e) {
            // Not all equipment implements getOutletStreams cleanly; ignore.
          }
          try {
            java.util.List<StreamInterface> inletStreams =
                ((neqsim.process.equipment.ProcessEquipmentInterface) unit).getInletStreams();
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
      return fallback;
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
    return levels;
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
   * Capture current state of all streams in all processes.
   *
   * @return map of stream name to [flowRate, temperature, pressure]
   */
  private Map<String, double[]> captureStreamStates() {
    return captureStreamStates(null);
  }

  /**
   * Capture current state of streams in all processes, optionally restricted to the supplied
   * boundary set.
   *
   * <p>
   * For multi-area {@link ProcessModel}s, inter-area convergence is driven only by streams that
   * cross area boundaries. Passing the boundary set avoids scanning every stream in every iteration
   * (O(N×M) → O(boundary)). When {@code boundaryStreams} is {@code null} or empty, all streams are
   * captured (backward-compatible behaviour).
   * </p>
   *
   * @param boundaryStreams identity-set of streams to capture, or {@code null} for all streams
   * @return map of stream name to [flowRate, temperature, pressure]
   */
  private Map<String, double[]> captureStreamStates(java.util.Set<Object> boundaryStreams) {
    Map<String, double[]> states = new LinkedHashMap<>();
    boolean filter = boundaryStreams != null && !boundaryStreams.isEmpty();
    if (filter) {
      for (Object boundaryObject : boundaryStreams) {
        if (!(boundaryObject instanceof StreamInterface)) {
          continue;
        }
        StreamInterface stream = (StreamInterface) boundaryObject;
        try {
          double flow = stream.getFlowRate("kg/hr");
          double temp = stream.getTemperature("K");
          double press = stream.getPressure("bara");
          String key = "boundary." + System.identityHashCode(stream);
          states.put(key, new double[] {flow, temp, press});
        } catch (Exception exception) {
          // Skip streams that can't be read
        }
      }
      return states;
    }
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      for (Object unit : process.getUnitOperations()) {
        if (!(unit instanceof StreamInterface)) {
          continue;
        }
        if (filter && !boundaryStreams.contains(unit)) {
          continue;
        }
        StreamInterface stream = (StreamInterface) unit;
        String key = processName + "." + stream.getName();
        try {
          double flow = stream.getFlowRate("kg/hr");
          double temp = stream.getTemperature("K");
          double press = stream.getPressure("bara");
          states.put(key, new double[] {flow, temp, press});
        } catch (Exception e) {
          // Skip streams that can't be read
        }
      }
    }
    return states;
  }

  /**
   * Collect the identity-set of streams that cross area boundaries in the current
   * {@link ProcessModel}. A stream is a boundary stream if it appears in at least two
   * {@link ProcessSystem}s.
   *
   * @return identity-based set of boundary streams (may be empty)
   */
  private java.util.Set<Object> collectBoundaryStreams() {
    java.util.Set<Object> boundary =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    if (processes.size() < 2) {
      return boundary;
    }
    // For each stream object count how many processes contain, consume, or produce it.
    java.util.Map<Object, Integer> counts = new java.util.IdentityHashMap<>();
    for (ProcessSystem process : processes.values()) {
      java.util.Set<Object> seen =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (Object unit : process.getUnitOperations()) {
        if (unit instanceof StreamInterface && seen.add(unit)) {
          counts.merge(unit, 1, Integer::sum);
        }
        if (unit instanceof ProcessEquipmentInterface) {
          ProcessEquipmentInterface equipment = (ProcessEquipmentInterface) unit;
          countStreamsForBoundaryDetection(equipment.getInletStreams(), seen, counts);
          countStreamsForBoundaryDetection(equipment.getOutletStreams(), seen, counts);
        }
      }
    }
    for (Map.Entry<Object, Integer> e : counts.entrySet()) {
      if (e.getValue() != null && e.getValue() >= 2) {
        boundary.add(e.getKey());
      }
    }
    return boundary;
  }

  /**
   * Adds streams from one equipment stream list to the boundary occurrence counter.
   *
   * @param streams streams to count
   * @param seen streams already counted for the current process area
   * @param counts identity-based stream occurrence counts across process areas
   */
  private void countStreamsForBoundaryDetection(List<StreamInterface> streams,
      java.util.Set<Object> seen, java.util.Map<Object, Integer> counts) {
    if (streams == null) {
      return;
    }
    for (StreamInterface stream : streams) {
      if (stream != null && seen.add(stream)) {
        counts.merge(stream, 1, Integer::sum);
      }
    }
  }

  /**
   * Calculate maximum relative errors between previous and current stream states.
   *
   * @param previous previous stream states
   * @param current current stream states
   * @return array of [maxFlowError, maxTempError, maxPressError]
   */
  private double[] calculateConvergenceErrors(Map<String, double[]> previous,
      Map<String, double[]> current) {
    double maxFlowErr = 0.0;
    double maxTempErr = 0.0;
    double maxPressErr = 0.0;

    for (String key : current.keySet()) {
      if (previous.containsKey(key)) {
        double[] prev = previous.get(key);
        double[] curr = current.get(key);

        // Flow rate relative error (with min threshold to avoid div by zero)
        double flowBase = Math.max(Math.abs(prev[0]), 1e-10);
        double flowErr = Math.abs(curr[0] - prev[0]) / flowBase;
        maxFlowErr = Math.max(maxFlowErr, flowErr);

        // Temperature relative error (use Kelvin to avoid issues near 0)
        double tempBase = Math.max(prev[1], 1.0);
        double tempErr = Math.abs(curr[1] - prev[1]) / tempBase;
        maxTempErr = Math.max(maxTempErr, tempErr);

        // Pressure relative error
        double pressBase = Math.max(prev[2], 1e-10);
        double pressErr = Math.abs(curr[2] - prev[2]) / pressBase;
        maxPressErr = Math.max(maxPressErr, pressErr);
      }
    }

    return new double[] {maxFlowErr, maxTempErr, maxPressErr};
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
    sb.append("Iterations: ").append(lastIterationCount).append(" / ").append(maxIterations)
        .append("\n");
    sb.append("Boundary streams tracked: ").append(lastBoundaryStreamCount).append("\n");
    sb.append("Boundary values converged: ").append(lastBoundaryValuesConverged ? "YES" : "NO")
        .append("\n");
    sb.append("All process areas solved: ").append(lastAllProcessesSolved ? "YES" : "NO")
        .append("\n");
    sb.append("\nFinal Errors (relative):\n");
    sb.append(String.format("  Flow rate:    %.2e (tolerance: %.2e) %s\n", lastMaxFlowError,
        flowTolerance, lastMaxFlowError < flowTolerance ? "OK" : "NOT CONVERGED"));
    sb.append(String.format("  Temperature:  %.2e (tolerance: %.2e) %s\n", lastMaxTemperatureError,
        temperatureTolerance,
        lastMaxTemperatureError < temperatureTolerance ? "OK" : "NOT CONVERGED"));
    sb.append(String.format("  Pressure:     %.2e (tolerance: %.2e) %s\n", lastMaxPressureError,
        pressureTolerance, lastMaxPressureError < pressureTolerance ? "OK" : "NOT CONVERGED"));

    sb.append("\nProcess Status:\n");
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      boolean processSolved = entry.getValue().solved();
      sb.append(
          String.format("  %-30s: %s\n", entry.getKey(), processSolved ? "SOLVED" : "NOT SOLVED"));
      if (!processSolved) {
        List<String> unsolvedUnits = getUnsolvedUnitNames(entry.getValue());
        if (!unsolvedUnits.isEmpty()) {
          sb.append("    Unsolved units: ").append(formatUnitNameList(unsolvedUnits, 12))
              .append("\n");
        }
      }
    }
    return sb.toString();
  }

  /**
   * Gets names of unit operations that currently report unsolved status.
   *
   * @param process process system to inspect
   * @return list of unsolved unit names in process execution order
   */
  private List<String> getUnsolvedUnitNames(ProcessSystem process) {
    List<String> names = new ArrayList<>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!unit.solved()) {
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
    sb.append("Optimized execution: ").append(useOptimizedExecution ? "enabled" : "disabled")
        .append("\n\n");

    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      sb.append("--- ProcessSystem: ").append(entry.getKey()).append(" ---\n");
      ProcessSystem process = entry.getValue();
      sb.append("Units: ").append(process.getUnitOperations().size()).append("\n");
      sb.append("Has recycles: ").append(process.hasRecycleLoops()).append("\n");
      if (useOptimizedExecution) {
        sb.append("Strategy: ")
            .append(process.hasRecycleLoops() ? "Hybrid (parallel + iterative)" : "Parallel")
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
   * {@link java.util.concurrent.Future} that can be used to monitor completion, cancel the task, or
   * retrieve any exceptions that occurred.
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
   * @deprecated Use {@link #runAsTask()} instead for better resource management. This method
   *             creates a new unmanaged thread directly.
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
   * @return a {@link java.util.Collection} of {@link neqsim.process.processmodel.ProcessSystem}
   *         objects
   */
  public Collection<ProcessSystem> getAllProcesses() {
    return processes.values();
  }

  /**
   * Check mass balance of all unit operations in all processes.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @return a map with process name and unit operation name as key and mass balance result as value
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance(String unit) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allMassBalanceResults =
        new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> massBalanceResults =
          process.checkMassBalance(unit);
      allMassBalanceResults.put(processName, massBalanceResults);
    }
    return allMassBalanceResults;
  }

  /**
   * Check mass balance of all unit operations in all processes using kg/sec.
   *
   * @return a map with process name and unit operation name as key and mass balance result as value
   *         in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> checkMassBalance() {
    return checkMassBalance("kg/sec");
  }

  /**
   * Get unit operations that failed mass balance check in all processes based on percentage error
   * threshold.
   *
   * @param unit unit for mass flow rate (e.g., "kg/sec", "kg/hr", "mole/sec")
   * @param percentThreshold percentage error threshold (default: 0.1%)
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(String unit,
      double percentThreshold) {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults =
        new LinkedHashMap<>();
    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      String processName = entry.getKey();
      ProcessSystem process = entry.getValue();
      Map<String, ProcessSystem.MassBalanceResult> failedResults =
          process.getFailedMassBalance(unit, percentThreshold);
      if (!failedResults.isEmpty()) {
        allFailedResults.put(processName, failedResults);
      }
    }
    return allFailedResults;
  }

  /**
   * Get unit operations that failed mass balance check in all processes using kg/sec and default
   * threshold.
   *
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance() {
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> allFailedResults =
        new LinkedHashMap<>();
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
   * @return a map with process name and a map of failed unit operation names and their mass balance
   *         results in kg/sec
   */
  public Map<String, Map<String, ProcessSystem.MassBalanceResult>> getFailedMassBalance(
      double percentThreshold) {
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

    for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : allResults
        .entrySet()) {
      report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
      report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

      Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
      if (unitResults.isEmpty()) {
        report.append("No unit operations found.\n");
      } else {
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults
            .entrySet()) {
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
    Map<String, Map<String, ProcessSystem.MassBalanceResult>> failedResults =
        getFailedMassBalance(unit, percentThreshold);

    if (failedResults.isEmpty()) {
      report.append("All unit operations passed mass balance check.\n");
    } else {
      for (Map.Entry<String, Map<String, ProcessSystem.MassBalanceResult>> processEntry : failedResults
          .entrySet()) {
        report.append("\nProcess: ").append(processEntry.getKey()).append("\n");
        report.append(String.format("%0" + 60 + "d", 0).replace('0', '=')).append("\n");

        Map<String, ProcessSystem.MassBalanceResult> unitResults = processEntry.getValue();
        for (Map.Entry<String, ProcessSystem.MassBalanceResult> unitEntry : unitResults
            .entrySet()) {
          String unitName = unitEntry.getKey();
          ProcessSystem.MassBalanceResult result = unitEntry.getValue();
          report.append(String.format("  %-30s: %s\n", unitName, result.toString()));
        }
      }
    }
    return report.toString();
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using kg/sec and default
   * threshold.
   *
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport() {
    return getFailedMassBalanceReport("kg/sec", 0.1);
  }

  /**
   * Get a formatted report of failed mass balance checks for all processes using specified
   * threshold.
   *
   * @param percentThreshold percentage error threshold
   * @return a formatted string report with process name and failed unit operations
   */
  public String getFailedMassBalanceReport(double percentThreshold) {
    return getFailedMassBalanceReport("kg/sec", percentThreshold);
  }

  /**
   * <p>
   * getReport_json.
   * </p>
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
   * The exported JSON has a top-level "areas" object where each key is the process area name and
   * each value is a JSON object in the {@link JsonProcessBuilder} schema (with "fluid" and
   * "process" sections). This format can be used to reconstruct the model or to export individual
   * areas to external simulators (e.g., UniSim Design via COM automation).
   * </p>
   *
   * <p>
   * Example output:
   *
   * <pre>{@code { "areas": { "separation": { "fluid": {...}, "process": [...] }, "compression": {
   * "fluid": {...}, "process": [...] } } } }</pre>
   *
   * @return JSON string representing all process areas @see JsonProcessExporter @see
   *         ProcessSystem#toJson()
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
    IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences =
        new IdentityHashMap<>();
    Map<String, JsonObject> exportedAreas = new LinkedHashMap<>();

    for (Map.Entry<String, ProcessSystem> entry : processes.entrySet()) {
      JsonProcessExporter exporter = new JsonProcessExporter();
      JsonObject areaJson = exporter.toJsonObject(entry.getValue());
      exportedAreas.put(entry.getKey(), areaJson);
      collectProducedStreamReferences(entry.getKey(), entry.getValue(), exporter,
          producedStreamReferences);
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

    JsonArray interAreaLinks = exportInterAreaLinks(producedStreamReferences);
    if (interAreaLinks.size() > 0) {
      root.add(INTER_AREA_LINKS_KEY, interAreaLinks);
    }

    com.google.gson.Gson gson;
    if (prettyPrint) {
      gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
          .serializeSpecialFloatingPointValues().create();
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
  private void collectProducedStreamReferences(String areaName, ProcessSystem process,
      JsonProcessExporter exporter,
      IdentityHashMap<StreamInterface, AreaStreamReference> producedStreamReferences) {
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit instanceof StreamInterface) {
        addProducedStreamReference(areaName, (StreamInterface) unit, exporter,
            producedStreamReferences);
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
  private void addProducedStreamReference(String areaName, StreamInterface stream,
      JsonProcessExporter exporter,
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
   * Each area is built independently using {@link JsonProcessBuilder}. If any area fails to build,
   * it is skipped and a warning is logged.
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
    com.google.gson.JsonObject root =
        com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    if (!root.has("areas")) {
      throw new IllegalArgumentException(
          "JSON must have an 'areas' object with named process systems");
    }

    ProcessModel model = new ProcessModel();
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
   * Applies model-level inter-area stream links after all process areas have been built.
   *
   * @param interAreaLinks JSON array with sourceArea, source, targetArea, targetUnit, and
   *        targetInletIndex fields
   * @return warnings for links that could not be applied
   */
  public List<String> applyInterAreaLinks(JsonArray interAreaLinks) {
    List<String> warnings = new ArrayList<>();
    if (interAreaLinks == null) {
      return warnings;
    }
    for (JsonElement linkElement : interAreaLinks) {
      if (!linkElement.isJsonObject()) {
        warnings.add("Skipping interAreaLinks entry because it is not a JSON object");
        continue;
      }
      applyInterAreaLink(linkElement.getAsJsonObject(), warnings);
    }
    return warnings;
  }

  /**
   * Applies one inter-area stream link.
   *
   * @param link JSON link definition
   * @param warnings mutable warning list to append to
   */
  private void applyInterAreaLink(JsonObject link, List<String> warnings) {
    String sourceArea = getString(link, "sourceArea");
    String sourceReference = getString(link, "source");
    String targetArea = getString(link, "targetArea");
    String targetUnitName = getString(link, "targetUnit");
    int targetInletIndex =
        link.has("targetInletIndex") ? link.get("targetInletIndex").getAsInt() : 0;

    ProcessSystem sourceProcess = processes.get(sourceArea);
    ProcessSystem targetProcess = processes.get(targetArea);
    if (sourceProcess == null) {
      warnings.add("Inter-area link source area not found: " + sourceArea);
      return;
    }
    if (targetProcess == null) {
      warnings.add("Inter-area link target area not found: " + targetArea);
      return;
    }

    StreamInterface sourceStream = resolveAreaStreamReference(sourceProcess, sourceReference);
    if (sourceStream == null) {
      warnings
          .add("Inter-area link source stream not found: " + sourceArea + "::" + sourceReference);
      return;
    }

    ProcessEquipmentInterface targetUnit = targetProcess.getUnit(targetUnitName);
    if (targetUnit == null) {
      warnings.add("Inter-area link target unit not found: " + targetArea + "::" + targetUnitName);
      return;
    }
    if (!replaceInletReference(targetUnit, targetInletIndex, sourceStream)) {
      warnings.add("Could not apply inter-area link to " + targetArea + "::" + targetUnitName
          + " inlet " + targetInletIndex);
    }
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
   * @param reference stream reference such as {@code feed}, {@code Sep.gasOut}, or
   *        {@code Tee.split0}
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
        return (StreamInterface) unit.getClass().getMethod("getSplitStream", int.class).invoke(unit,
            splitIndex);
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
  private boolean invokeIndexedStreamReplacement(ProcessEquipmentInterface targetUnit,
      int targetInletIndex, StreamInterface sourceStream) {
    try {
      java.lang.reflect.Method replaceStream =
          targetUnit.getClass().getMethod("replaceStream", int.class, StreamInterface.class);
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
  private boolean invokeSingleInletSetter(ProcessEquipmentInterface targetUnit,
      StreamInterface sourceStream) {
    try {
      java.lang.reflect.Method setInletStream =
          targetUnit.getClass().getMethod("setInletStream", StreamInterface.class);
      setInletStream.invoke(targetUnit, sourceStream);
      return true;
    } catch (Exception firstException) {
      try {
        java.lang.reflect.Method setFeedStream =
            targetUnit.getClass().getMethod("setFeedStream", StreamInterface.class);
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
        StreamInterface inlet =
            (StreamInterface) unit.getClass().getMethod("getInletStream").invoke(unit);
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
   * Convenience method that combines {@link #fromJson(String)} and {@link #run()} in a single call.
   * This is the round-trip counterpart to {@link #toJson()}.
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
   * This method iterates through all ProcessSystems and validates each one. The results are
   * aggregated into a single ValidationResult. Use this method before running the model to identify
   * configuration issues.
   * </p>
   *
   * @return a {@link neqsim.util.validation.ValidationResult} containing all validation issues
   *         across all processes
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
          result.addError("[" + processName + "] " + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        } else {
          result.addWarning("[" + processName + "] " + issue.getCategory(), issue.getMessage(),
              issue.getRemediation());
        }
      }
    }

    return result;
  }

  /**
   * Validates all processes and returns results organized by process name.
   *
   * <p>
   * This method provides detailed validation results for each ProcessSystem separately, making it
   * easier to identify which process has issues.
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
   * This is a convenience method that returns true if no CRITICAL validation errors exist across
   * all processes. Use this for a quick go/no-go check before running the model.
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
   * This method provides a human-readable summary of all validation issues across all processes in
   * the model.
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
   * This is the recommended format for production use, providing compact storage with full model
   * state preservation including all ProcessSystems. The file can be loaded with
   * {@link #loadFromNeqsim(String)}.
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
   * After loading, the model is automatically run to reinitialize calculations. This ensures the
   * internal state is consistent for all ProcessSystems.
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
        logger.error("Loaded object is not a ProcessModel: "
            + (loaded != null ? loaded.getClass().getName() : "null"));
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
      try (java.io.ObjectOutputStream oos =
          new java.io.ObjectOutputStream(new java.io.FileOutputStream(filename))) {
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
      try (java.io.ObjectInputStream ois =
          new java.io.ObjectInputStream(new java.io.FileInputStream(filename))) {
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
   * This exports state for all ProcessSystems in the model. The JSON format is Git-friendly and
   * human-readable, suitable for version control and diffing.
   * </p>
   *
   * @param filename the file path to save to (recommended extension: .json)
   * @return true if save was successful
   */
  public boolean saveStateToFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state =
          neqsim.process.processmodel.lifecycle.ProcessModelState.fromProcessModel(this);
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
   * Note: This returns a new ProcessModel with ProcessSystems initialized from the saved state.
   * Full reconstruction requires the original equipment configuration.
   * </p>
   *
   * @param filename the file path to load from
   * @return the loaded ProcessModel, or null if loading fails
   */
  public static ProcessModel loadStateFromFile(String filename) {
    try {
      neqsim.process.processmodel.lifecycle.ProcessModelState state =
          neqsim.process.processmodel.lifecycle.ProcessModelState.loadFromFile(filename);
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
   * Auto-sizes all equipment in this model that implements
   * {@link neqsim.process.design.AutoSizeable}.
   *
   * <p>
   * This method iterates through all process systems in the model and calls autoSize() on each
   * equipment that implements the AutoSizeable interface. The equipment is sized using the default
   * safety factor (1.2 = 20% margin).
   * </p>
   *
   * <p>
   * <strong>Important:</strong> This method should be called AFTER running the process model so
   * that flow rates and conditions are known for sizing calculations.
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
   * This method iterates through all process systems in the model and calls autoSize() on each
   * equipment that implements the AutoSizeable interface.
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
   * This method applies design rules from the specified company's technical requirements (TR)
   * documents. The standards are loaded from the NeqSim design database.
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
   * Enables or disables capacity analysis for all equipment in all process systems.
   *
   * <p>
   * This is a convenience method that applies the setting to all equipment in all processes. When
   * disabled, equipment is excluded from:
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
        logger
            .warn("ModelProgressListener threw exception in onBeforeIteration: " + ex.getMessage());
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
        logger.warn(
            "ModelProgressListener threw exception in onIterationComplete: " + ex.getMessage());
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
  private void notifyBeforeProcessArea(String areaName, ProcessSystem process, int areaIndex,
      int totalAreas, int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onBeforeProcessArea(areaName, process, areaIndex, totalAreas,
            iterationNumber);
      } catch (Exception ex) {
        logger.warn(
            "ModelProgressListener threw exception in onBeforeProcessArea: " + ex.getMessage());
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
  private void notifyProcessAreaComplete(String areaName, ProcessSystem process, int areaIndex,
      int totalAreas, int iterationNumber) {
    if (progressListener != null) {
      try {
        progressListener.onProcessAreaComplete(areaName, process, areaIndex, totalAreas,
            iterationNumber);
      } catch (Exception ex) {
        logger.warn(
            "ModelProgressListener threw exception in onProcessAreaComplete: " + ex.getMessage());
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
  private boolean notifyProcessAreaError(String areaName, ProcessSystem process,
      Exception exception) {
    if (progressListener != null) {
      try {
        return progressListener.onProcessAreaError(areaName, process, exception);
      } catch (Exception ex) {
        logger.warn(
            "ModelProgressListener threw exception in onProcessAreaError: " + ex.getMessage());
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
  private void publishModelEvent(ProcessEvent.EventType type, String description,
      ProcessEvent.Severity severity) {
    if (publishEvents) {
      try {
        ProcessEvent event = new ProcessEvent(ProcessEvent.generateId(), type, "ProcessModel",
            description, severity);
        ProcessEventBus.getInstance().publish(event);
      } catch (Exception ex) {
        logger.warn("Failed to publish ProcessModel event: " + ex.getMessage());
      }
    }
  }

  /**
   * Run auto-validation on all ProcessSystems. Called once before the first iteration when
   * autoValidate is enabled. Validation failures are logged as warnings.
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
                "Validation warning for area '" + areaName + "': " + result.toString(),
                ProcessEvent.Severity.WARNING);
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
   * Returns an automation facade for this process model. The facade provides a stable,
   * string-addressable API for scripts and AI agents to interact with all process areas using
   * area-qualified addresses like {@code "AreaName::UnitName.property"}.
   *
   * @return a {@link neqsim.process.automation.ProcessAutomation} facade
   */
  public neqsim.process.automation.ProcessAutomation getAutomation() {
    return new neqsim.process.automation.ProcessAutomation(this);
  }

  /**
   * Returns the names of all unit operations across all process areas. Names are area-qualified in
   * the format {@code "AreaName::UnitName"}. Convenience delegate for
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
   * Returns all available variables for the named unit operation. The {@code unitName} may be
   * area-qualified: {@code "AreaName::UnitName"}. Convenience delegate for
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
   * Reads the current value of a simulation variable by its address. The address should be
   * area-qualified: {@code "AreaName::unitName.property"}. Convenience delegate for
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
