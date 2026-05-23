package neqsim.process.equipment.distillation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.costestimation.column.ColumnCostEstimate;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.distillation.DistillationColumnMechanicalDesign;
import neqsim.process.util.monitor.DistillationColumnResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.unit.TemperatureUnit;
import neqsim.util.validation.ValidationResult;

/**
 * Models a tray based distillation column with optional condenser and reboiler.
 *
 * <p>
 * The column is solved using a sequential substitution approach. The {@link #init()} method sets
 * initial tray temperatures by running the feed tray and linearly distributing temperatures towards
 * the top and bottom. During {@link #run(UUID)} the trays are iteratively solved in upward and
 * downward sweeps until the summed temperature change between iterations is below the configured
 * {@link #temperatureTolerance} or the iteration limit is reached.
 * </p>
 *
 * @author esol
 */
public class DistillationColumn extends ProcessEquipmentBaseClass implements DistillationInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(DistillationColumn.class);

  private boolean doInitializion = true;
  boolean hasReboiler = false;
  boolean hasCondenser = false;
  protected ArrayList<SimpleTray> trays = new ArrayList<SimpleTray>(0);
  /** Scaling factor used to derive a tray-proportional iteration budget. */
  private static final double TRAY_ITERATION_FACTOR = 5.0;
  /** Target relative mass imbalance for the post-processing polish stage. */
  private static final double MASS_POLISH_TARGET = 2.0e-2;
  /** Target relative energy imbalance for the post-processing polish stage. */
  private static final double ENERGY_POLISH_TARGET = 2.0e-2;
  /** Target average temperature drift for the polishing stage in Kelvin. */
  private static final double TEMPERATURE_POLISH_TARGET = 5.0e-3;
  /** Extra iterations granted when a polish stage is triggered. */
  private static final int POLISH_ITERATION_MARGIN = 6;
  /**
   * Multiplier governing how much the solver can extend beyond the nominal iteration budget.
   */
  private static final int ITERATION_OVERFLOW_MULTIPLIER = 12;
  /** Recommended base temperature tolerance for adaptive defaults. */
  private static final double DEFAULT_TEMPERATURE_TOLERANCE = 9.0e-3;
  /** Recommended base mass balance tolerance for adaptive defaults. */
  private static final double DEFAULT_MASS_BALANCE_TOLERANCE = 1.6e-2;
  /** Recommended base enthalpy balance tolerance for adaptive defaults. */
  private static final double DEFAULT_ENTHALPY_BALANCE_TOLERANCE = 1.6e-2;
  /** Default scaled MESH residual tolerance when residual gating is enabled. */
  private static final double DEFAULT_MESH_RESIDUAL_TOLERANCE = 1.0;
  /**
   * Default product draw residual tolerance when MESH residual gating is enabled.
   */
  private static final double DEFAULT_MESH_PRODUCT_DRAW_RESIDUAL_TOLERANCE = 2.0e-2;
  /** Maximum product-flow drift allowed when accepting a MESH Newton polish candidate. */
  private static final double MESH_POLISH_PRODUCT_FLOW_TOLERANCE = 2.0e-2;
  /** Product reconciliation drift above this level is reported as a non-rigorous solve status. */
  private static final double PRODUCT_RECONCILIATION_STATUS_TOLERANCE = 2.0e-2;
  /**
   * Maximum internal tray traffic accepted after divergence recovery relative to external feed.
   */
  private static final double MAX_SOLVED_INTERNAL_TRAFFIC_TO_FEED_RATIO = 100.0;
  /**
   * Maximum tear-stream flow allowed during relaxed updates relative to external feed.
   */
  private static final double MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO = 1.0e5;
  /**
   * Minimum temperature span required before a tray temperature profile is considered useful.
   */
  private static final double MINIMUM_FEED_PROFILE_SPAN = 1.0;
  /**
   * Temperature offset used when only one column-end temperature is specified.
   */
  private static final double FEED_PROFILE_END_TEMPERATURE_OFFSET = 20.0;
  /** Tolerance used when comparing equivalent feed tray candidates. */
  private static final double FEED_TRAY_TIE_TOLERANCE = 1.0e-9;
  /** Default maximum number of candidate cases in tray optimization searches. */
  private static final int DEFAULT_MAX_TRAY_OPTIMIZATION_CANDIDATES = 2000;
  /** Default maximum elapsed time for tray optimization searches in seconds. */
  private static final double DEFAULT_MAX_TRAY_OPTIMIZATION_TIME_SECONDS = 120.0;
  /** Minimum tray count where matrix warm-start overhead is expected to pay off. */
  private static final int MIN_MATRIX_INSIDE_OUT_WARM_START_TRAYS = 12;
  /** Default specification continuation stages used by automatic solver mode. */
  private static final int AUTO_SPECIFICATION_HOMOTOPY_STEPS = 3;
  double condenserCoolingDuty = 10.0;
  private double reboilerTemperature = 273.15;
  private double condenserTemperature = 270.15;
  double topTrayPressure = -1.0;

  /** Temperature convergence tolerance. */
  private double temperatureTolerance = DEFAULT_TEMPERATURE_TOLERANCE;
  /** Mass balance convergence tolerance. */
  private double massBalanceTolerance = DEFAULT_MASS_BALANCE_TOLERANCE;
  /** Enthalpy balance convergence tolerance. */
  private double enthalpyBalanceTolerance = DEFAULT_ENTHALPY_BALANCE_TOLERANCE;
  /** Scaled MESH residual convergence tolerance. */
  private double meshResidualTolerance = DEFAULT_MESH_RESIDUAL_TOLERANCE;
  /** Scaled terminal product-draw residual convergence tolerance. */
  private double meshProductDrawResidualTolerance = DEFAULT_MESH_PRODUCT_DRAW_RESIDUAL_TOLERANCE;
  /** Maximum number of candidate cases allowed in tray optimization searches. */
  private int maxTrayOptimizationCandidates = DEFAULT_MAX_TRAY_OPTIMIZATION_CANDIDATES;
  /** Maximum elapsed time allowed in tray optimization searches in seconds. */
  private double maxTrayOptimizationTimeSeconds = DEFAULT_MAX_TRAY_OPTIMIZATION_TIME_SECONDS;
  /** Latest shortcut-initialization result, or {@code null} if none has been applied. */
  private transient ShortcutInitializationResult lastShortcutInitializationResult = null;
  /** Track whether temperature tolerance has been manually overridden. */
  private boolean temperatureToleranceCustomized = false;
  /** Track whether mass balance tolerance has been manually overridden. */
  private boolean massBalanceToleranceCustomized = false;
  /** Track whether enthalpy balance tolerance has been manually overridden. */
  private boolean enthalpyBalanceToleranceCustomized = false;

  /** Available solving strategies for the column. */
  public enum SolverType {
    /** Classic sequential substitution without damping. */
    DIRECT_SUBSTITUTION,
    /** Sequential substitution with temperature damping. */
    DAMPED_SUBSTITUTION,
    /** Inside-out style simultaneous correction of upward/downward flows. */
    INSIDE_OUT,
    /** Adaptive matrix inside-out component-balance warm start with rigorous polishing. */
    MATRIX_INSIDE_OUT,
    /** Wegstein acceleration of successive substitution. */
    WEGSTEIN,
    /** Sum-rates tearing method with flow correction. */
    SUM_RATES,
    /**
     * Newton-Raphson tray-temperature correction accelerator, not a full MESH Newton solver.
     */
    NEWTON,
    /** Naphtali-Sandholm simultaneous correction of full MESH equation blocks. */
    NAPHTALI_SANDHOLM,
    /** MESH residual-monitored solve with inside-out initialization. */
    MESH_RESIDUAL,
    /** Automatically select a robust solver from the built-in strategy set. */
    AUTO
  }

  /** Status of the latest column solve. */
  public enum SolveStatus {
    /** No solve has been run since the diagnostics were reset. */
    NOT_RUN,
    /** The tray solution satisfies the active rigorous convergence gates. */
    RIGOROUS_CONVERGED,
    /** Public products were materially reconciled after the tray solve. */
    RECONCILED_PRODUCTS,
    /** Public products came from a guarded fallback estimate, not a rigorous tray solve. */
    FALLBACK_PRODUCTS,
    /** The latest solve did not satisfy the active rigorous convergence gates. */
    FAILED
  }

  /** Phase withdrawn by a column side draw. */
  public enum SideDrawPhase {
    /** Withdraw vapor traffic from the selected tray. */
    GAS,
    /** Withdraw liquid traffic from the selected tray. */
    LIQUID
  }

  /** Operating mode for the condenser tray. */
  public enum CondenserMode {
    /** Equilibrium partial condenser with vapor product and liquid reflux. */
    PARTIAL,
    /** Bubble-point total condenser with split liquid reflux and distillate product. */
    TOTAL,
    /** Partial condenser with an explicit fixed liquid reflux stream split. */
    LIQUID_REFLUX_SPLIT
  }

  /** Operating mode for the reboiler tray. */
  public enum ReboilerMode {
    /** Equilibrium reboiler without an explicit boilup/reflux ratio. */
    EQUILIBRIUM,
    /** Reboiler solved with an explicit vapor boilup/reflux ratio. */
    VAPOR_BOILUP_RATIO
  }

  /** Dynamic column model formulation. */
  public enum DynamicColumnModel {
    /** Experimental explicit-Euler holdup model retained for screening studies. */
    EXPERIMENTAL_EULER
  }

  /**
   * Flow specification for a side-product draw.
   *
   * <p>
   * The column uses this specification as a tear variable by adjusting the corresponding tray
   * side-draw fraction until the withdrawn stream flow matches the target flow.
   * </p>
   *
   * @author esol
   * @version 1.0
   */
  public static class ColumnSideDrawSpecification implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final int trayNumber;
    private final SideDrawPhase phase;
    private final double targetFlowRate;
    private final String flowUnit;
    private double tolerance = 1.0e-4;
    private int maxIterations = 12;
    private transient double lastActualFlowRate = Double.NaN;
    private transient double lastRelativeResidual = Double.POSITIVE_INFINITY;

    /**
     * Create a side-draw flow specification.
     *
     * @param trayNumber bottom-up tray index where the draw is located
     * @param phase side-draw phase
     * @param targetFlowRate target side-draw flow rate
     * @param flowUnit flow-rate unit for the target and actual flow
     * @throws IllegalArgumentException if phase is null, target flow is negative or non-finite, or
     *         the flow unit is empty
     */
    public ColumnSideDrawSpecification(int trayNumber, SideDrawPhase phase, double targetFlowRate,
        String flowUnit) {
      if (phase == null) {
        throw new IllegalArgumentException("Side draw phase cannot be null");
      }
      if (!Double.isFinite(targetFlowRate) || targetFlowRate < 0.0) {
        throw new IllegalArgumentException("Side draw target flow must be finite and >= 0");
      }
      if (flowUnit == null || flowUnit.trim().isEmpty()) {
        throw new IllegalArgumentException("Side draw flow unit cannot be empty");
      }
      this.trayNumber = trayNumber;
      this.phase = phase;
      this.targetFlowRate = targetFlowRate;
      this.flowUnit = flowUnit;
    }

    /**
     * Get the draw tray number.
     *
     * @return bottom-up tray index
     */
    public int getTrayNumber() {
      return trayNumber;
    }

    /**
     * Get the side-draw phase.
     *
     * @return side-draw phase
     */
    public SideDrawPhase getPhase() {
      return phase;
    }

    /**
     * Get the target side-draw flow rate.
     *
     * @return target flow rate in {@link #getFlowUnit()}
     */
    public double getTargetFlowRate() {
      return targetFlowRate;
    }

    /**
     * Get the flow unit used by this specification.
     *
     * @return flow unit string
     */
    public String getFlowUnit() {
      return flowUnit;
    }

    /**
     * Get the relative convergence tolerance.
     *
     * @return relative tolerance
     */
    public double getTolerance() {
      return tolerance;
    }

    /**
     * Set the relative convergence tolerance.
     *
     * @param tolerance positive finite relative tolerance
     */
    public void setTolerance(double tolerance) {
      if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
        throw new IllegalArgumentException("Side draw tolerance must be finite and positive");
      }
      this.tolerance = tolerance;
    }

    /**
     * Get the maximum number of tear iterations requested by this specification.
     *
     * @return maximum iterations
     */
    public int getMaxIterations() {
      return maxIterations;
    }

    /**
     * Set the maximum number of tear iterations requested by this specification.
     *
     * @param maxIterations positive maximum iteration count
     */
    public void setMaxIterations(int maxIterations) {
      if (maxIterations <= 0) {
        throw new IllegalArgumentException("Side draw maxIterations must be positive");
      }
      this.maxIterations = maxIterations;
    }

    /**
     * Get the latest actual flow rate.
     *
     * @return latest actual flow rate in {@link #getFlowUnit()}, or {@link Double#NaN}
     */
    public double getLastActualFlowRate() {
      return lastActualFlowRate;
    }

    /**
     * Get the latest relative residual.
     *
     * @return relative residual from the latest side-draw update
     */
    public double getLastRelativeResidual() {
      return lastRelativeResidual;
    }

    /**
     * Store latest actual flow and return its relative residual.
     *
     * @param actualFlowRate actual draw flow rate in {@link #getFlowUnit()}
     * @return relative residual
     */
    private double updateActualFlowRate(double actualFlowRate) {
      lastActualFlowRate = actualFlowRate;
      double scale = Math.max(1.0e-12, Math.abs(targetFlowRate));
      lastRelativeResidual = Math.abs(actualFlowRate - targetFlowRate) / scale;
      return lastRelativeResidual;
    }
  }

  /**
   * Liquid pumparound circuit that withdraws liquid from one tray, changes temperature, and returns
   * it to another tray.
   *
   * @author esol
   * @version 1.0
   */
  public static class ColumnPumparound implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private final String name;
    private final int drawTrayNumber;
    private final int returnTrayNumber;
    private final double drawFraction;
    private final double temperatureDrop;
    private transient StreamInterface drawStream;
    private transient StreamInterface returnStream;
    private transient double lastReturnFlowKgPerHour = 0.0;

    /**
     * Create a liquid pumparound definition.
     *
     * @param name pumparound name
     * @param drawTrayNumber tray index where liquid is withdrawn
     * @param returnTrayNumber tray index where cooled/heated liquid is returned
     * @param drawFraction fraction of tray liquid traffic withdrawn
     * @param temperatureDrop temperature drop from draw to return in Kelvin
     */
    public ColumnPumparound(String name, int drawTrayNumber, int returnTrayNumber,
        double drawFraction, double temperatureDrop) {
      this.name = name;
      this.drawTrayNumber = drawTrayNumber;
      this.returnTrayNumber = returnTrayNumber;
      this.drawFraction = drawFraction;
      this.temperatureDrop = temperatureDrop;
    }

    /**
     * Get the pumparound name.
     *
     * @return pumparound name
     */
    public String getName() {
      return name;
    }

    /**
     * Get the draw tray number.
     *
     * @return draw tray index
     */
    public int getDrawTrayNumber() {
      return drawTrayNumber;
    }

    /**
     * Get the return tray number.
     *
     * @return return tray index
     */
    public int getReturnTrayNumber() {
      return returnTrayNumber;
    }

    /**
     * Get the liquid draw fraction.
     *
     * @return draw fraction
     */
    public double getDrawFraction() {
      return drawFraction;
    }

    /**
     * Get the temperature drop from draw to return.
     *
     * @return temperature drop in Kelvin
     */
    public double getTemperatureDrop() {
      return temperatureDrop;
    }

    /**
     * Get the latest liquid draw stream.
     *
     * @return latest draw stream, or {@code null} before the column has been run
     */
    public StreamInterface getDrawStream() {
      return drawStream;
    }

    /**
     * Get the latest liquid return stream.
     *
     * @return latest return stream, or {@code null} before the first draw update
     */
    public StreamInterface getReturnStream() {
      return returnStream;
    }

    /**
     * Update the return stream from a tray liquid draw.
     *
     * @param newDrawStream latest liquid draw stream
     * @param id calculation identifier
     * @return relative change in return flow rate
     */
    private double updateReturnStream(StreamInterface newDrawStream, UUID id) {
      drawStream = newDrawStream;
      double previousFlow = lastReturnFlowKgPerHour;
      SystemInterface returnSystem = newDrawStream.getThermoSystem().clone();
      double returnTemperature = returnSystem.getTemperature() - temperatureDrop;
      if (!Double.isFinite(returnTemperature) || returnTemperature <= 0.0) {
        throw new IllegalStateException(
            "Pumparound return temperature must be finite and above 0 K");
      }
      returnSystem.setTemperature(returnTemperature);
      if (returnStream == null) {
        returnStream = new Stream(name + " return", returnSystem);
      } else {
        returnStream.setThermoSystem(returnSystem);
      }
      returnStream.run(id);
      lastReturnFlowKgPerHour = Math.abs(returnStream.getFlowRate("kg/hr"));
      double scale = Math.max(1.0e-12, Math.max(previousFlow, lastReturnFlowKgPerHour));
      return Math.abs(lastReturnFlowKgPerHour - previousFlow) / scale;
    }
  }

  /** Selected solver algorithm. Defaults to direct substitution. */
  private SolverType solverType = SolverType.DIRECT_SUBSTITUTION;
  /** Solver strategy that actually completed the latest solve. */
  private transient SolverType lastSolverTypeUsed = SolverType.DIRECT_SUBSTITUTION;
  /** Strict status of the latest solve. */
  private transient SolveStatus lastSolveStatus = SolveStatus.NOT_RUN;
  /** Optional reason explaining why the latest solve fell back or was rejected. */
  private transient String lastSolveStatusReason = "";
  /** Trace of solver candidates attempted by automatic solver mode. */
  private transient String lastAutoSolverSummary = "";
  /** Feasibility report from the latest automatic solver pre-screen. */
  private transient String lastAutoFeasibilityReport = "";
  /** Initialization report from the latest automatic solver seed attempt. */
  private transient String lastInitializationReport = "";
  /** Chronological event log from the latest automatic solver pipeline. */
  private transient List<String> lastAutoSolverHistory = new ArrayList<String>();

  /**
   * Relaxation factor used when {@link SolverType#DAMPED_SUBSTITUTION} is active.
   */
  private double relaxationFactor = 0.5;
  /**
   * Minimum relaxation factor used when adaptive damping scales down the sequential step.
   */
  private double minSequentialRelaxation = 0.5;
  /** Minimum relaxation factor allowed for the inside-out tear streams. */
  private double minInsideOutRelaxation = 0.5;
  /** Maximum relaxation factor allowed by the adaptive controller. */
  private double maxAdaptiveRelaxation = 1.2;
  /** Factor used to expand the relaxation factor when residuals shrink. */
  private double relaxationIncreaseFactor = 1.2;
  /** Factor used to shrink the relaxation factor when residuals grow. */
  private double relaxationDecreaseFactor = 0.5;
  /** Minimum relaxation applied when blending tray temperatures. */
  private double minTemperatureRelaxation = 0.2;
  /** Cap applied to energy residual when adjusting relaxation. */
  private double maxEnergyRelaxationWeight = 10.0;
  /**
   * Control whether energy residual must satisfy tolerance before convergence.
   */
  private boolean enforceEnergyBalanceTolerance = false;
  /**
   * Explicit control of whether the MESH residual vector must satisfy tolerance before convergence.
   * When not explicitly set, the gate is active for residual-based solver modes and inactive for
   * substitution and temperature/flow accelerator modes.
   */
  private boolean enforceMeshResidualTolerance = false;
  /**
   * Track whether MESH residual convergence gating has been explicitly configured.
   */
  private boolean enforceMeshResidualToleranceCustomized = false;
  private boolean doMultiPhaseCheck = true;

  /**
   * When {@code true}, trays in the reactive section use {@link ReactiveTray} (simultaneous
   * chemical + phase equilibrium via the Modified RAND method) instead of standard VLE
   * {@link SimpleTray}. Set this before the first {@link #run()} call.
   */
  private boolean reactive = false;

  /**
   * First tray index (0-based, inclusive) of the reactive section. A value of {@code -1} means all
   * middle trays (i.e. excluding reboiler/condenser) are reactive.
   */
  private int reactiveStartTray = -1;

  /**
   * Last tray index (0-based, inclusive) of the reactive section. A value of {@code -1} means all
   * middle trays are reactive.
   */
  private int reactiveEndTray = -1;

  /**
   * Flag tracking whether the column has been solved at least once. Used to seed the sequential
   * solver with the previous tray state on re-runs, preventing divergence from an unrelaxed start.
   */
  private transient boolean hasBeenSolvedBefore = false;

  /**
   * Total feed flow (kg/hr) recorded at the end of the previous solve. Used to detect whether the
   * column needs to re-solve or can reuse the previous result.
   */
  private transient double lastTotalFeedFlow = -1.0;

  /** Mechanical design for the distillation column. */
  private DistillationColumnMechanicalDesign mechanicalDesign;

  /** Column specification for the top (condenser) end. */
  private ColumnSpecification topSpecification;
  /** Column specification for the bottom (reboiler) end. */
  private ColumnSpecification bottomSpecification;
  /** Number of continuation stages used for adjustable product specifications. */
  private int specificationHomotopySteps = 1;
  /** Number of specification continuation stages completed by the latest solve. */
  private transient int lastSpecificationHomotopyStepCount = 0;

  Mixer feedmixer = new Mixer("temp mixer");
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  int maxNumberOfIterations = 50;
  /**
   * Optional per-stage initial temperature guesses for simultaneous residual solvers.
   */
  private double[] seedTemperatures = null;
  StreamInterface stream_3 = new Stream("stream_3");
  StreamInterface gasOutStream = new Stream("gasOutStream");
  StreamInterface liquidOutStream = new Stream("liquidOutStream");
  boolean stream_3isset = false;
  private double internalDiameter = 1.0;
  neqsim.process.processmodel.ProcessSystem distoperations;
  Heater heater;
  Separator separator2;

  /**
   * Error measure used in solver to check convergence in run().
   */
  private double err = 1.0e10;

  /** Last number of iterations executed by the active solver. */
  private int lastIterationCount = 0;
  /** Last recorded average temperature residual in Kelvin. */
  private double lastTemperatureResidual = 0.0;
  /** Last recorded relative mass balance residual. */
  private double lastMassResidual = 0.0;
  /** Last recorded relative enthalpy residual. */
  private double lastEnergyResidual = 0.0;
  /** Last maximum raw internal tray traffic divided by external feed flow. */
  private double lastInternalTrafficRatio = 0.0;
  /** Last reported top specification residual. */
  private double lastTopSpecificationResidual = 0.0;
  /** Last reported bottom specification residual. */
  private double lastBottomSpecificationResidual = 0.0;
  /** Latest MESH residual diagnostics. */
  private transient ColumnMeshResidual lastMeshResidual = null;
  /**
   * Whether the latest public products came from the guarded overall-feed flash fallback.
   */
  private transient boolean lastUsedFeedFlashFallback = false;
  /**
   * Whether the latest solve reached the internal-traffic guard after divergence recovery.
   */
  private transient boolean lastInternalTrafficGuardReached = false;
  /**
   * Whether emergency capping of relaxed internal traffic is active for the current solve.
   */
  private transient boolean internalTrafficCapActive = false;
  /** Reconciled top product draw used by product-draw residual diagnostics. */
  private transient StreamInterface terminalGasProductDrawStream = null;
  /** Reconciled bottom product draw used by product-draw residual diagnostics. */
  private transient StreamInterface terminalLiquidProductDrawStream = null;
  /** Duration of the latest solve step in seconds. */
  private double lastSolveTimeSeconds = 0.0;
  /** Rigorous inside-out outer flash sweeps performed by the latest inside-out solve. */
  private transient int lastInsideOutOuterFlashSweeps = 0;
  /** Simplified inside-out inner-loop iterations performed by the latest inside-out solve. */
  private transient int lastInsideOutInnerLoopIterations = 0;
  /** Latest inside-out K-value residual. */
  private transient double lastInsideOutKValueResidual = Double.NaN;
  /** Latest simplified inside-out surrogate temperature residual. */
  private transient double lastInsideOutSurrogateResidual = Double.NaN;
  /** Number of simplified inside-out surrogate resets in the latest solve. */
  private transient int lastInsideOutSurrogateResetCount = 0;
  /** Whether matrix inside-out used a matrix warm-start on the latest solve. */
  private transient boolean lastMatrixInsideOutWarmStartUsed = false;
  /** Whether matrix inside-out bypassed the matrix warm-start on the latest solve. */
  private transient boolean lastMatrixInsideOutWarmStartBypassed = false;
  /** Matrix warm-start iterations from the latest matrix inside-out solve. */
  private transient int lastMatrixInsideOutIterationCount = 0;
  /** Matrix warm-start average temperature residual from the latest solve. */
  private transient double lastMatrixInsideOutTemperatureResidual = Double.NaN;
  /** Matrix warm-start wall time from the latest solve in seconds. */
  private transient double lastMatrixInsideOutSolveTimeSeconds = 0.0;
  /** Latest Naphtali-Sandholm semi-analytic Jacobian column count. */
  private transient int lastNaphtaliAnalyticJacobianColumns = 0;
  /** Latest Naphtali-Sandholm finite-difference Jacobian column count. */
  private transient int lastNaphtaliFiniteDifferenceJacobianColumns = 0;
  /** Latest Naphtali-Sandholm tray thermodynamic evaluation count. */
  private transient int lastNaphtaliThermoEvaluationCount = 0;
  /** Latest Naphtali-Sandholm thermodynamic cache hit count. */
  private transient int lastNaphtaliThermoCacheHitCount = 0;
  /** Latest Naphtali-Sandholm Jacobian build wall time in seconds. */
  private transient double lastNaphtaliJacobianBuildTimeSeconds = 0.0;
  /** Latest Naphtali-Sandholm block-tridiagonal linear solve count. */
  private transient int lastNaphtaliBlockLinearSolveCount = 0;
  /** Latest Naphtali-Sandholm dense fallback linear solve count. */
  private transient int lastNaphtaliDenseLinearSolveCount = 0;
  /** Latest Naphtali-Sandholm linear solve wall time in seconds. */
  private transient double lastNaphtaliLinearSolveTimeSeconds = 0.0;

  /**
   * Instead of Map&lt;Integer,StreamInterface&gt;, we store a list of feed streams per tray number.
   * This allows multiple feeds to the same tray.
   */
  private Map<Integer, List<StreamInterface>> feedStreams = new HashMap<>();
  /**
   * Legacy direct tray feeds captured before internal vapor/liquid traffic is connected.
   */
  private Map<Integer, List<StreamInterface>> directExternalFeedStreams = new HashMap<>();
  private List<StreamInterface> unassignedFeedStreams = new ArrayList<>();
  /** Flow specifications for side-product draws. */
  private List<ColumnSideDrawSpecification> sideDrawSpecifications = new ArrayList<>();
  /** Liquid pumparound circuits configured on the column. */
  private List<ColumnPumparound> pumparounds = new ArrayList<>();
  /** Maximum outer iterations used to converge pumparound return streams. */
  private int maxPumparoundIterations = 8;
  /** Relative return-flow tolerance for pumparound outer iterations. */
  private double pumparoundTolerance = 1.0e-4;
  /** Maximum outer iterations used to converge column tear variables. */
  private int maxColumnTearIterations = 12;
  /** Relative tolerance used for side-draw and hydraulic tear variables. */
  private double columnTearTolerance = 1.0e-4;
  /** Whether tray/packing hydraulic pressure drop should update the pressure profile. */
  private boolean hydraulicPressureDropCouplingEnabled = false;
  /** Internals type used when hydraulic pressure-drop coupling is active. */
  private String hydraulicPressureDropInternalsType = "sieve";
  /** Latest coupled hydraulic pressure drop in Pa. */
  private double lastHydraulicPressureDropPa = 0.0;
  /** Latest relative pressure-profile change from hydraulic coupling. */
  private double lastHydraulicPressureDropResidual = 0.0;
  /** Number of outer tear-variable iterations used in the latest run. */
  private int lastColumnTearIterationCount = 0;
  /** Maximum relative residual from the latest outer tear-variable solve. */
  private double lastColumnTearResidual = 0.0;
  /** Whether the latest outer tear-variable solve satisfied tolerance. */
  private boolean lastColumnTearConverged = true;
  /** Latest maximum relative pumparound return-stream change. */
  private double lastPumparoundRelativeChange = 0.0;
  /** Whether the latest outer tear update changed any manipulated variable. */
  private transient boolean columnTearVariablesChanged = false;

  /**
   * <p>
   * Setter for the field <code>doMultiPhaseCheck</code>.
   * </p>
   *
   * @param doMultiPhaseCheck a boolean
   */
  public void setMultiPhaseCheck(boolean doMultiPhaseCheck) {
    this.doMultiPhaseCheck = doMultiPhaseCheck;
    feedmixer.setMultiPhaseCheck(doMultiPhaseCheck);
    for (SimpleTray tray : trays) {
      tray.setMultiPhaseCheck(doMultiPhaseCheck);
    }
  }

  /**
   * <p>
   * Getter for the field <code>doMultiPhaseCheck</code>.
   * </p>
   *
   * @return a boolean
   */
  public boolean isDoMultiPhaseCheck() {
    return doMultiPhaseCheck;
  }

  /**
   * Murphree tray efficiency applied to each equilibrium stage (0..1). Default 1.0 = ideal.
   */
  private double murphreeEfficiency = 1.0;

  /**
   * Per-stage Murphree efficiency overrides. Index 0 is the reboiler and the last stage is the
   * condenser if present. A {@link Double#NaN} value means that the column-wide Murphree efficiency
   * is used for that stage.
   */
  private double[] perStageMurphreeEfficiency = null;

  /**
   * Per-iteration convergence history: [iteration][0=tempErr, 1=massErr, 2=energyErr].
   */
  private transient List<double[]> convergenceHistory = new ArrayList<>();

  /**
   * Number of simplified inner-loop iterations between rigorous flash updates in the IO solver.
   * Higher values reduce flash count but may reduce accuracy. Default 3.
   */
  private int innerLoopSteps = 3;

  // ============ Dynamic Simulation Fields ============
  /** Whether the dynamic tray model is enabled for transient simulation. */
  private boolean dynamicColumnEnabled = false;
  /** Dynamic model formulation currently used by {@link #runTransient(double, UUID)}. */
  private DynamicColumnModel dynamicColumnModel = DynamicColumnModel.EXPERIMENTAL_EULER;
  /** Liquid holdup per tray in moles. Indexed by tray number. */
  private transient double[] trayLiquidHoldup = null;
  /** Weir height on each tray in metres. */
  private double trayWeirHeight = 0.05;
  /** Weir length (crest length) on each tray in metres. */
  private double trayWeirLength = 1.0;
  /** Per-tray enthalpy in J. Indexed by tray number. Null until initialized. */
  private transient double[] trayEnthalpy = null;
  /** Dry tray pressure drop in Pa per tray — for vapor hydraulic model. */
  private double trayDryPressureDrop = 0.0;
  /** Whether per-tray energy balance is active (uses PH flash instead of TP). */
  private boolean dynamicEnergyEnabled = false;

  /**
   * <p>
   * Constructor for DistillationColumn.
   * </p>
   *
   * @param name Name of distillation column
   * @param numberOfTraysLocal Number of SimpleTrays to add (excluding reboiler/condenser)
   * @param hasReboiler Set true to add reboiler
   * @param hasCondenser Set true to add Condenser
   */
  public DistillationColumn(String name, int numberOfTraysLocal, boolean hasReboiler,
      boolean hasCondenser) {
    super(name);
    this.hasReboiler = hasReboiler;
    this.hasCondenser = hasCondenser;
    distoperations = new neqsim.process.processmodel.ProcessSystem();
    this.numberOfTrays = numberOfTraysLocal;
    initMechanicalDesign();

    // If user sets hasReboiler, put that in as the first tray in 'trays' list
    if (hasReboiler) {
      trays.add(new Reboiler("Reboiler"));
      this.numberOfTrays++;
    }

    // Then the middle "simple" trays
    for (int i = 0; i < numberOfTraysLocal; i++) {
      trays.add(createMiddleTray("SimpleTray" + (i + 1), i));
    }

    // If user sets hasCondenser, add it at the top
    if (hasCondenser) {
      trays.add(new Condenser("Condenser"));
      this.numberOfTrays++;
    }

    // Add them all to the process system
    for (int i = 0; i < this.numberOfTrays; i++) {
      distoperations.add(trays.get(i));
    }
  }

  /**
   * <p>
   * Add a feed stream to the specified tray. (Now allows multiple streams on the same trayNumber,
   * using a list.)
   * </p>
   *
   * @param inputStream the feed stream
   * @param feedTrayNumber the tray number (0-based in the code) to which this feed goes
   * @throws IllegalArgumentException if the stream is null or the tray index is outside the column
   *         tray range
   */
  public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
    if (inputStream == null) {
      throw new IllegalArgumentException("inputStream can not be null");
    }
    if (feedTrayNumber < 0 || feedTrayNumber >= numberOfTrays) {
      throw new IllegalArgumentException(
          "Feed tray index must be between 0 and " + (numberOfTrays - 1));
    }
    // Put this feed into our feedStreams list for that trayNumber
    feedStreams.computeIfAbsent(feedTrayNumber, k -> new ArrayList<>()).add(inputStream);

    // Also attach it to the tray itself
    getTray(feedTrayNumber).addStream(inputStream);

    // If your design is that *all* feed streams get combined in feedmixer:
    feedmixer.addStream(inputStream);
    feedmixer.run();

    // Then you optionally split the feedmixer output into dummy streams_3,
    // gasOutStream, liquidOutStream (the existing pattern).
    double moles = feedmixer.getOutletStream().getThermoSystem().getTotalNumberOfMoles();
    stream_3 = feedmixer.getOutletStream(); // combined
    gasOutStream.setThermoSystem(stream_3.getThermoSystem().clone());
    gasOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);

    liquidOutStream.setThermoSystem(stream_3.getThermoSystem().clone());
    liquidOutStream.getThermoSystem().setTotalNumberOfMoles(moles / 2.0);

    // Mark that we need to re-initialize if new feeds are added
    setDoInitializion(true);
  }

  /**
   * Add a feed stream to the column without specifying the tray.
   *
   * <p>
   * The feed tray is estimated automatically when the column is run. The estimate uses an existing
   * tray temperature profile when available, otherwise it builds a simple temperature profile from
   * configured condenser/reboiler temperatures and the feed temperature. This is a robust initial
   * placement heuristic, not a guarantee of global optimum or convergence for every specification.
   * </p>
   *
   * @param inputStream the feed stream
   */
  public void addFeedStream(StreamInterface inputStream) {
    unassignedFeedStreams.add(inputStream);
    setDoInitializion(true);
  }

  /**
   * Return the feed streams connected to a given tray.
   *
   * @param feedTrayNumber tray index where feeds are connected
   * @return immutable view of feed streams connected to the tray
   */
  public List<StreamInterface> getFeedStreams(int feedTrayNumber) {
    List<StreamInterface> feeds = feedStreams.get(feedTrayNumber);
    if (feeds == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(feeds);
  }

  /**
   * Return all feed streams connected to the column, keyed by bottom-up tray index.
   *
   * @return immutable view of the feed stream map
   */
  public Map<Integer, List<StreamInterface>> getFeedStreams() {
    return Collections.unmodifiableMap(feedStreams);
  }

  /**
   * Check whether this column includes a reboiler stage.
   *
   * @return {@code true} if a reboiler is present
   */
  public boolean hasReboiler() {
    return hasReboiler;
  }

  /**
   * Check whether this column includes a condenser stage.
   *
   * @return {@code true} if a condenser is present
   */
  public boolean hasCondenser() {
    return hasCondenser;
  }

  /**
   * Estimate which tray an unassigned feed stream would be placed on.
   *
   * <p>
   * This method does not connect the feed stream to the column. It is intended for diagnostics and
   * for checking automatic feed placement before calling {@link #run()}.
   * </p>
   *
   * @param inputStream feed stream to evaluate
   * @return 0-based tray number, or {@code -1} if the stream is null or no trays exist
   */
  public int estimateFeedTrayNumber(StreamInterface inputStream) {
    if (inputStream == null || numberOfTrays == 0) {
      return -1;
    }
    inputStream.run();
    return estimateFeedTrayNumber(inputStream.getTemperature());
  }

  /**
   * Return the tray number for a feed stream currently assigned to the column.
   *
   * <p>
   * The lookup first compares stream object identity and then falls back to the stream name. Feed
   * streams added with {@link #addFeedStream(StreamInterface)} are assigned when the column is run.
   * </p>
   *
   * @param inputStream feed stream to locate
   * @return 0-based tray number, or {@code -1} if the stream is null or not assigned
   */
  public int getFeedTrayNumber(StreamInterface inputStream) {
    if (inputStream == null) {
      return -1;
    }
    int feedTrayNumber = getFeedTrayNumberByReference(inputStream);
    if (feedTrayNumber >= 0) {
      return feedTrayNumber;
    }
    return getFeedTrayNumber(inputStream.getName());
  }

  /**
   * Return the tray number for a feed stream with the given name.
   *
   * @param streamName feed stream name to locate
   * @return 0-based tray number, or {@code -1} if the name is null or not assigned
   */
  public int getFeedTrayNumber(String streamName) {
    if (streamName == null) {
      return -1;
    }
    for (int trayNumber = 0; trayNumber < numberOfTrays; trayNumber++) {
      List<StreamInterface> feeds = feedStreams.get(trayNumber);
      if (feeds == null) {
        continue;
      }
      for (StreamInterface feed : feeds) {
        if (streamName.equals(feed.getName())) {
          return trayNumber;
        }
      }
    }
    return -1;
  }

  /**
   * Return the tray number for the exact feed stream object.
   *
   * @param inputStream feed stream object to locate
   * @return 0-based tray number, or {@code -1} if the stream object is not assigned
   */
  private int getFeedTrayNumberByReference(StreamInterface inputStream) {
    for (int trayNumber = 0; trayNumber < numberOfTrays; trayNumber++) {
      List<StreamInterface> feeds = feedStreams.get(trayNumber);
      if (feeds == null) {
        continue;
      }
      for (StreamInterface feed : feeds) {
        if (feed == inputStream) {
          return trayNumber;
        }
      }
    }
    return -1;
  }

  /**
   * Prepare the column for calculation by estimating tray temperatures and linking streams between
   * trays.
   *
   * <p>
   * The feed tray is solved first to obtain a temperature estimate. This temperature is then used
   * to linearly guess temperatures upwards to the condenser and downwards to the reboiler. Gas and
   * liquid outlet streams are connected to neighbouring trays so that a subsequent call to
   * {@link #run(UUID)} can iterate to convergence.
   * </p>
   */
  public void init() {
    if (!isDoInitializion()) {
      return;
    }
    setDoInitializion(false);

    captureDirectExternalTrayFeeds();
    resetTrayInputsToExternalFeeds();

    // If feed streams are empty, nothing to do
    if (feedStreams.isEmpty() && directExternalFeedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    // Grab the tray with the lowest index among the feed trays
    int firstFeedTrayNumber = getFirstExternalFeedTrayNumber();

    // We run the first feed tray to see its temperature:
    getTray(firstFeedTrayNumber).run();

    // If that tray ended up single-phase, see if adding some other feed helps
    if (getTray(firstFeedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      for (int i = 0; i < numberOfTrays; i++) {
        if (getTray(i).getNumberOfInputStreams() > 0 && i != firstFeedTrayNumber) {
          getTray(firstFeedTrayNumber).addStream(trays.get(i).getStream(0));
          getTray(firstFeedTrayNumber).run();
          // remove it again
          getTray(firstFeedTrayNumber)
              .removeInputStream(getTray(firstFeedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(firstFeedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        } else if (i == firstFeedTrayNumber && getTray(i).getNumberOfInputStreams() > 1) {
          getTray(firstFeedTrayNumber).addStream(trays.get(i).getStream(1));
          trays.get(firstFeedTrayNumber).run();
          getTray(firstFeedTrayNumber)
              .removeInputStream(getTray(firstFeedTrayNumber).getNumberOfInputStreams() - 1);
          if (getTray(firstFeedTrayNumber).getThermoSystem().getNumberOfPhases() > 1) {
            break;
          }
        }
      }
    }

    // Just in case it’s still single-phase, do an init(0), init(3).
    if (getTray(firstFeedTrayNumber).getFluid().getNumberOfPhases() == 1) {
      getTray(firstFeedTrayNumber).getThermoSystem().init(0);
      getTray(firstFeedTrayNumber).getThermoSystem().init(3);
    }

    // Set up reboiler tray’s temperature
    trays.get(0).addStream(trays.get(firstFeedTrayNumber).getLiquidOutStream().clone());
    trays.get(0).run();

    double feedTrayTemperature = getTray(firstFeedTrayNumber).getTemperature();

    if (trays.get(numberOfTrays - 1).getNumberOfInputStreams() > 0) {
      condenserTemperature = trays.get(numberOfTrays - 1).getThermoSystem().getTemperature();
    } else {
      condenserTemperature = feedTrayTemperature - 1.0;
    }
    reboilerTemperature = trays.get(0).getThermoSystem().getTemperature();

    // Rough guess for temperature steps
    double deltaTempCondenser = (feedTrayTemperature - condenserTemperature)
        / (numberOfTrays * 1.0 - firstFeedTrayNumber - 1);
    double deltaTempReboiler =
        (reboilerTemperature - feedTrayTemperature) / (firstFeedTrayNumber * 1.0);

    // set temperature from feed tray up
    double delta = 0;
    for (int i = firstFeedTrayNumber + 1; i < numberOfTrays; i++) {
      delta += deltaTempCondenser;
      trays.get(i)
          .setTemperature(getTray(firstFeedTrayNumber).getThermoSystem().getTemperature() - delta);
    }

    // set temperature from feed tray down
    delta = 0;
    for (int i = firstFeedTrayNumber - 1; i >= 0; i--) {
      delta += deltaTempReboiler;
      trays.get(i)
          .setTemperature(getTray(firstFeedTrayNumber).getThermoSystem().getTemperature() + delta);
    }

    // Link upward
    for (int i = 1; i < numberOfTrays; i++) {
      trays.get(i).addStream(trays.get(i - 1).getGasOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }

    // Link downward
    for (int i = numberOfTrays - 2; i >= 1; i--) {
      trays.get(i).addStream(trays.get(i + 1).getLiquidOutStream());
      trays.get(i).init();
      trays.get(i).run();
    }

    int streamNumb = (trays.get(0)).getNumberOfInputStreams() - 1;
    trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
    trays.get(0).init();
    trays.get(0).run();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Solve the column until tray temperatures converge.
   *
   * The method applies sequential substitution with an adaptive relaxation controller. Pressures
   * are set linearly between bottom and top. Each iteration performs an upward sweep where liquid
   * flows downward followed by a downward sweep where vapour flows upward. Tray temperatures and
   * inter-tray stream flow rates are relaxed if the combined temperature, mass and energy residuals
   * grow, providing basic line-search behaviour.
   * </p>
   */
  @Override
  public void run(UUID id) {
    lastInternalTrafficGuardReached = false;
    internalTrafficCapActive = false;
    lastSpecificationHomotopyStepCount = 0;
    lastAutoSolverSummary = "";
    resetMatrixInsideOutDiagnostics();
    assignUnassignedFeeds();
    convergenceHistory = new ArrayList<>();
    applyDirectSpecifications();
    if (hasActiveColumnTearVariables()) {
      solveWithColumnTearVariables(id);
      return;
    }
    solveConfiguredColumn(id);
  }

  /**
   * Check whether side draws, pumparounds, or hydraulics add outer tear variables.
   *
   * @return {@code true} when an outer tear-variable solve is required
   */
  private boolean hasActiveColumnTearVariables() {
    return !sideDrawSpecifications.isEmpty() || !pumparounds.isEmpty()
        || hydraulicPressureDropCouplingEnabled;
  }

  /**
   * Solve the configured column once and update diagnostics.
   *
   * @param id calculation identifier
   */
  private void solveConfiguredColumn(UUID id) {
    if (hasAdjustableSpecifications()) {
      solveWithSpecifications(id);
      updateSpecificationResiduals();
      updateMeshResiduals();
      return;
    }
    solveInner(id);
    updateSpecificationResiduals();
    updateMeshResiduals();
  }

  /**
   * Solve the column with outer iterations for side draws, pumparounds, and hydraulics.
   *
   * @param id calculation identifier
   */
  private void solveWithColumnTearVariables(UUID id) {
    int iterationLimit = getColumnTearIterationLimit();
    double tolerance = getColumnTearTolerance();
    lastColumnTearIterationCount = 0;
    lastColumnTearResidual = Double.POSITIVE_INFINITY;
    lastColumnTearConverged = false;
    for (int iteration = 0; iteration < iterationLimit; iteration++) {
      solveConfiguredColumn(id);
      double relativeChange = updateColumnTearVariables(id);
      lastColumnTearIterationCount = iteration + 1;
      lastColumnTearResidual = relativeChange;
      if (relativeChange <= tolerance) {
        if (columnTearVariablesChanged) {
          setDoInitializion(true);
          solveConfiguredColumn(id);
        }
        updateSideDrawSpecificationResidualsOnly();
        lastColumnTearResidual = Math.max(relativeChange, getMaxSideDrawSpecificationResidual());
        lastColumnTearConverged = lastColumnTearResidual <= tolerance;
        return;
      }
      if (!columnTearVariablesChanged) {
        updateSideDrawSpecificationResidualsOnly();
        lastColumnTearResidual = Math.max(relativeChange, getMaxSideDrawSpecificationResidual());
        lastColumnTearConverged = false;
        return;
      }
      if (iteration < iterationLimit - 1) {
        setDoInitializion(true);
      }
    }
    setDoInitializion(true);
    solveConfiguredColumn(id);
    updateSideDrawSpecificationResidualsOnly();
    lastColumnTearResidual =
        Math.max(lastColumnTearResidual, getMaxSideDrawSpecificationResidual());
    lastColumnTearConverged = lastColumnTearResidual <= tolerance;
  }

  /**
   * Get the maximum iteration count for all active column tear variables.
   *
   * @return maximum outer tear-variable iterations
   */
  private int getColumnTearIterationLimit() {
    int iterationLimit = Math.max(maxColumnTearIterations, maxPumparoundIterations);
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      iterationLimit = Math.max(iterationLimit, specification.getMaxIterations());
    }
    return Math.max(1, iterationLimit);
  }

  /**
   * Get the active tolerance for all column tear variables.
   *
   * @return active relative tolerance
   */
  private double getColumnTearTolerance() {
    double tolerance = Math.min(columnTearTolerance, pumparoundTolerance);
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      tolerance = Math.min(tolerance, specification.getTolerance());
    }
    return Math.max(1.0e-12, tolerance);
  }

  /**
   * Update all outer tear variables from the latest column solution.
   *
   * @param id calculation identifier
   * @return maximum relative change or residual across tear variables
   */
  private double updateColumnTearVariables(UUID id) {
    columnTearVariablesChanged = false;
    double maxRelativeChange = 0.0;
    maxRelativeChange = Math.max(maxRelativeChange, updateSideDrawSpecificationFractions());
    maxRelativeChange = Math.max(maxRelativeChange, enforceSideDrawFeedInventoryLimit());
    maxRelativeChange = Math.max(maxRelativeChange, updatePumparoundReturnStreams(id));
    maxRelativeChange = Math.max(maxRelativeChange, updatePressureProfileFromHydraulics());
    return maxRelativeChange;
  }

  /**
   * Update all pumparound return streams from the latest tray liquid draws.
   *
   * @param id calculation identifier
   * @return maximum relative return-flow change across pumparounds
   */
  private double updatePumparoundReturnStreams(UUID id) {
    double maxRelativeChange = 0.0;
    for (ColumnPumparound pumparound : pumparounds) {
      StreamInterface drawStream =
          getTray(pumparound.getDrawTrayNumber()).getLiquidPumparoundDrawStream();
      maxRelativeChange =
          Math.max(maxRelativeChange, pumparound.updateReturnStream(drawStream, id));
    }
    lastPumparoundRelativeChange = maxRelativeChange;
    if (maxRelativeChange > 1.0e-12) {
      columnTearVariablesChanged = true;
    }
    return maxRelativeChange;
  }

  /**
   * Update configured side-draw fractions to meet flow specifications.
   *
   * @return maximum relative side-draw flow residual
   */
  private double updateSideDrawSpecificationFractions() {
    double maxRelativeResidual = 0.0;
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      StreamInterface sideDrawStream =
          getSideDrawStream(specification.getTrayNumber(), specification.getPhase());
      double actualFlowRate = sideDrawStream.getFlowRate(specification.getFlowUnit());
      double residual = specification.updateActualFlowRate(actualFlowRate);
      maxRelativeResidual = Math.max(maxRelativeResidual, residual);
      if (residual <= specification.getTolerance()) {
        continue;
      }
      double newFraction = calculateNextSideDrawFraction(specification, actualFlowRate);
      columnTearVariablesChanged = setSideDrawFractionWithinLimit(specification.getTrayNumber(),
          specification.getPhase(), newFraction) || columnTearVariablesChanged;
    }
    return maxRelativeResidual;
  }

  /** Update side-draw flow residuals without changing side-draw fractions. */
  private void updateSideDrawSpecificationResidualsOnly() {
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      StreamInterface sideDrawStream =
          getSideDrawStream(specification.getTrayNumber(), specification.getPhase());
      specification.updateActualFlowRate(sideDrawStream.getFlowRate(specification.getFlowUnit()));
    }
  }

  /**
   * Get the maximum residual across side-draw flow specifications.
   *
   * @return maximum relative residual, or zero when no side-draw specs are configured
   */
  private double getMaxSideDrawSpecificationResidual() {
    double maxResidual = 0.0;
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      maxResidual = Math.max(maxResidual, specification.getLastRelativeResidual());
    }
    return maxResidual;
  }

  /**
   * Calculate the next side-draw fraction for a flow specification.
   *
   * @param specification side-draw flow specification
   * @param actualFlowRate latest actual flow rate
   * @return next candidate side-draw fraction
   */
  private double calculateNextSideDrawFraction(ColumnSideDrawSpecification specification,
      double actualFlowRate) {
    double currentFraction =
        getSideDrawFraction(specification.getTrayNumber(), specification.getPhase());
    if (specification.getTargetFlowRate() <= 1.0e-12) {
      return 0.0;
    }
    if (Math.abs(actualFlowRate) <= 1.0e-12) {
      return currentFraction > 0.0 ? currentFraction + 0.05 : 0.05;
    }
    return currentFraction * specification.getTargetFlowRate() / actualFlowRate;
  }

  /**
   * Get the configured side-draw fraction on a tray.
   *
   * @param trayNumber bottom-up tray index
   * @param phase side-draw phase
   * @return current side-draw fraction
   */
  private double getSideDrawFraction(int trayNumber, SideDrawPhase phase) {
    SimpleTray tray = getTray(trayNumber);
    if (phase == SideDrawPhase.GAS) {
      return tray.getGasSideDrawFraction();
    }
    if (phase == SideDrawPhase.LIQUID) {
      return tray.getLiquidSideDrawFraction();
    }
    throw new IllegalArgumentException("Side draw phase cannot be null");
  }

  /**
   * Set a side-draw fraction after clamping it to the available tray phase traffic.
   *
   * @param trayNumber bottom-up tray index
   * @param phase side-draw phase
   * @param fraction requested side-draw fraction
   * @return true if the tray fraction changed, false if the requested value was already set
   */
  private boolean setSideDrawFractionWithinLimit(int trayNumber, SideDrawPhase phase,
      double fraction) {
    double limitedFraction =
        Math.max(0.0, Math.min(getMaximumSideDrawFraction(trayNumber, phase), fraction));
    double currentFraction = getSideDrawFraction(trayNumber, phase);
    if (Math.abs(limitedFraction - currentFraction) <= 1.0e-12) {
      return false;
    }
    setSideDrawFraction(trayNumber, phase, limitedFraction);
    return true;
  }

  /**
   * Get the maximum side-draw fraction available for the selected tray phase.
   *
   * @param trayNumber bottom-up tray index
   * @param phase side-draw phase
   * @return maximum allowed side-draw fraction
   */
  private double getMaximumSideDrawFraction(int trayNumber, SideDrawPhase phase) {
    if (phase == SideDrawPhase.GAS) {
      return 1.0;
    }
    if (phase == SideDrawPhase.LIQUID) {
      return Math.max(0.0, 1.0 - getTray(trayNumber).getLiquidPumparoundDrawFraction());
    }
    throw new IllegalArgumentException("Side draw phase cannot be null");
  }

  /**
   * Limit side-product fractions so side draws cannot remove more component inventory than feeds.
   *
   * @return relative reduction applied to side-draw fractions, or zero if no reduction was needed
   */
  private double enforceSideDrawFeedInventoryLimit() {
    if (getSideDrawStreams().isEmpty()) {
      return 0.0;
    }
    double[] feedComponentMoles = getFeedComponentMoles();
    double[] sideDrawComponentMoles = getSideDrawComponentMoles(feedComponentMoles.length);
    double scaleFactor = 1.0;
    for (int componentIndex = 0; componentIndex < sideDrawComponentMoles.length; componentIndex++) {
      double sideDrawMoles = sideDrawComponentMoles[componentIndex];
      if (sideDrawMoles > feedComponentMoles[componentIndex] + 1.0e-12) {
        scaleFactor = Math.min(scaleFactor, feedComponentMoles[componentIndex] / sideDrawMoles);
      }
    }
    if (scaleFactor >= 1.0 - 1.0e-10) {
      return 0.0;
    }
    scaleSideDrawFractions(scaleFactor);
    columnTearVariablesChanged = true;
    return 1.0 - scaleFactor;
  }

  /**
   * Scale all side-draw fractions by a common factor.
   *
   * @param scaleFactor common scale factor from zero to one
   */
  private void scaleSideDrawFractions(double scaleFactor) {
    for (int trayNumber = 0; trayNumber < numberOfTrays; trayNumber++) {
      SimpleTray tray = getTray(trayNumber);
      if (tray.getGasSideDrawFraction() > 0.0) {
        tray.setGasSideDrawFraction(tray.getGasSideDrawFraction() * scaleFactor);
      }
      if (tray.getLiquidSideDrawFraction() > 0.0) {
        tray.setLiquidSideDrawFraction(tray.getLiquidSideDrawFraction() * scaleFactor);
      }
    }
  }

  /**
   * Update the column pressure profile from tray or packing hydraulic pressure drop.
   *
   * @return relative pressure-profile change
   */
  private double updatePressureProfileFromHydraulics() {
    if (!hydraulicPressureDropCouplingEnabled) {
      return 0.0;
    }
    try {
      ColumnInternalsDesigner designer = calcColumnInternals(hydraulicPressureDropInternalsType);
      double pressureDropPa = Math.max(0.0, designer.getTotalPressureDrop());
      lastHydraulicPressureDropPa = pressureDropPa;
      lastHydraulicPressureDropResidual = applyHydraulicPressureDrop(pressureDropPa);
      if (lastHydraulicPressureDropResidual > 1.0e-12) {
        columnTearVariablesChanged = true;
      }
      return lastHydraulicPressureDropResidual;
    } catch (Exception exception) {
      logger.warn("Could not update hydraulic pressure drop for column {}", getName(), exception);
      lastHydraulicPressureDropResidual = Double.POSITIVE_INFINITY;
      return lastHydraulicPressureDropResidual;
    }
  }

  /**
   * Apply a hydraulic pressure drop to the configured pressure profile.
   *
   * @param pressureDropPa total hydraulic pressure drop in Pa
   * @return relative pressure-profile endpoint change
   */
  private double applyHydraulicPressureDrop(double pressureDropPa) {
    double pressureDropBar = pressureDropPa / 1.0e5;
    if (isPositiveFinite(topTrayPressure)) {
      double previousBottomPressure = bottomTrayPressure;
      bottomTrayPressure = topTrayPressure + pressureDropBar;
      applyOptimizationPressureProfile();
      if (!isPositiveFinite(previousBottomPressure)) {
        return 1.0;
      }
      return Math.abs(bottomTrayPressure - previousBottomPressure)
          / Math.max(1.0e-12, Math.abs(previousBottomPressure));
    }
    if (isPositiveFinite(bottomTrayPressure)) {
      double previousTopPressure = topTrayPressure;
      topTrayPressure = Math.max(1.0e-6, bottomTrayPressure - pressureDropBar);
      applyOptimizationPressureProfile();
      if (!isPositiveFinite(previousTopPressure)) {
        return 1.0;
      }
      return Math.abs(topTrayPressure - previousTopPressure)
          / Math.max(1.0e-12, Math.abs(previousTopPressure));
    }
    return 0.0;
  }

  /** Apply specifications that map directly to condenser or reboiler controls. */
  private void applyDirectSpecifications() {
    applyDirectSpecification(topSpecification);
    applyDirectSpecification(bottomSpecification);
  }

  /**
   * Apply a specification that does not require an outer iteration.
   *
   * @param spec the specification to apply
   */
  private void applyDirectSpecification(ColumnSpecification spec) {
    if (spec == null) {
      return;
    }

    if (spec.getType() == ColumnSpecification.SpecificationType.REFLUX_RATIO) {
      if (spec.getLocation() == ColumnSpecification.ProductLocation.TOP && hasCondenser) {
        getCondenser().setRefluxRatio(spec.getTargetValue());
      } else if (spec.getLocation() == ColumnSpecification.ProductLocation.BOTTOM && hasReboiler) {
        getReboiler().setRefluxRatio(spec.getTargetValue());
      }
    } else if (spec.getType() == ColumnSpecification.SpecificationType.DUTY) {
      if (spec.getLocation() == ColumnSpecification.ProductLocation.TOP && hasCondenser) {
        getCondenser().setHeatInput(spec.getTargetValue());
      } else if (spec.getLocation() == ColumnSpecification.ProductLocation.BOTTOM && hasReboiler) {
        getReboiler().setHeatInput(spec.getTargetValue());
      }
    }
  }

  /**
   * Check whether any configured column specification requires iterative adjustment.
   *
   * @return {@code true} when an active product/recovery/flow specification is present
   */
  private boolean hasAdjustableSpecifications() {
    return needsAdjustment(topSpecification) || needsAdjustment(bottomSpecification);
  }

  /**
   * Check whether all active column specifications are within their configured tolerance.
   *
   * @return {@code true} if all specifications are satisfied
   */
  private boolean specificationsSatisfied() {
    return specificationSatisfied(topSpecification) && specificationSatisfied(bottomSpecification);
  }

  /**
   * Update the stored residuals for the currently configured column specifications.
   */
  private void updateSpecificationResiduals() {
    lastTopSpecificationResidual = evaluateSpecErrorSafely(topSpecification);
    lastBottomSpecificationResidual = evaluateSpecErrorSafely(bottomSpecification);
  }

  /** Update the stored specification residuals for package-level diagnostics. */
  void updateSpecificationResidualDiagnostics() {
    updateSpecificationResiduals();
  }

  /** Update the stored MESH residual diagnostics for the current column state. */
  private void updateMeshResiduals() {
    lastMeshResidual = ColumnMeshResidualEvaluator.evaluate(this);
  }

  /**
   * Evaluate a specification residual for diagnostics without interrupting a solve.
   *
   * @param spec the specification to evaluate
   * @return current residual, zero for no specification, or {@code Double.NaN} if unavailable
   */
  private double evaluateSpecErrorSafely(ColumnSpecification spec) {
    if (spec == null) {
      return 0.0;
    }
    try {
      return evaluateSpecError(spec);
    } catch (Exception ex) {
      logger.debug("Could not evaluate column specification residual", ex);
      return Double.NaN;
    }
  }

  /**
   * Evaluate the current value represented by a specification without subtracting its target.
   *
   * @param spec the specification to evaluate
   * @return current value for the specification, or {@code Double.NaN} if unavailable
   */
  private double evaluateSpecValueSafely(ColumnSpecification spec) {
    if (spec == null) {
      return Double.NaN;
    }
    try {
      return evaluateSpecError(spec) + spec.getTargetValue();
    } catch (Exception ex) {
      logger.debug("Could not evaluate column specification value", ex);
      return Double.NaN;
    }
  }

  /**
   * Check whether a single column specification is satisfied.
   *
   * @param spec the specification to evaluate
   * @return {@code true} if no residual check is needed or the residual is within tolerance
   */
  private boolean specificationSatisfied(ColumnSpecification spec) {
    if (spec == null || !needsAdjustment(spec)) {
      return true;
    }
    return Math.abs(evaluateSpecError(spec)) <= spec.getTolerance();
  }

  /**
   * Solve the column using the currently selected inner solver.
   *
   * @param id calculation identifier
   * @return result from the selected column solver
   */
  private ColumnSolveResult solveSelectedSolver(UUID id) {
    ColumnSolveResult result = ColumnSolverFactory.create(solverType).solve(this, id);
    lastSolverTypeUsed = result.getSolverType();
    return result;
  }

  /**
   * Solve the column with an outer loop that adjusts condenser/reboiler temperatures to satisfy
   * product specifications. Uses a secant method for each specification that requires adjustment.
   *
   * @param id calculation identifier
   */
  private void solveWithSpecifications(UUID id) {
    lastSpecificationHomotopyStepCount = 0;
    int effectiveHomotopySteps = getEffectiveSpecificationHomotopySteps();
    if (effectiveHomotopySteps > 1) {
      specificationHomotopySteps = effectiveHomotopySteps;
      solveWithSpecificationHomotopy(id, effectiveHomotopySteps);
      return;
    }
    solveWithSpecificationTargets(id, topSpecification, bottomSpecification);
  }

  /**
   * Determine the specification continuation stage count for the current solve.
   *
   * @return explicit user homotopy steps, or automatic robust-mode steps when AUTO is active
   */
  private int getEffectiveSpecificationHomotopySteps() {
    if (specificationHomotopySteps > 1) {
      return specificationHomotopySteps;
    }
    if (solverType == SolverType.AUTO && hasAdjustableSpecifications()) {
      return AUTO_SPECIFICATION_HOMOTOPY_STEPS;
    }
    return specificationHomotopySteps;
  }

  /**
   * Solve adjustable product specifications through staged continuation targets.
   *
   * <p>
   * The first stage starts from the current product value after a warm baseline solve and then
   * ramps linearly to the user-specified final target. This avoids an abrupt jump to a difficult
   * purity, recovery, or product-flow target while leaving the stored public specifications
   * unchanged.
   * </p>
   *
   * @param id calculation identifier
   * @param steps number of continuation stages to run
   */
  private void solveWithSpecificationHomotopy(UUID id, int steps) {
    boolean adjustTop = needsAdjustment(topSpecification) && hasCondenser;
    boolean adjustBottom = needsAdjustment(bottomSpecification) && hasReboiler;
    if (!adjustTop && !adjustBottom) {
      solveWithSpecificationTargets(id, topSpecification, bottomSpecification);
      return;
    }

    double feedTemp = estimateFeedTemperature();
    double topTemp =
        hasCondenser && getCondenser().isSetOutTemperature() ? getCondenser().getOutTemperature()
            : feedTemp - 20.0;
    double bottomTemp =
        hasReboiler && getReboiler().isSetOutTemperature() ? getReboiler().getOutTemperature()
            : feedTemp + 20.0;

    applySpecificationTemperatureGuess(adjustTop, adjustBottom, topTemp, bottomTemp);
    setDoInitializion(!hasBeenSolvedBefore);
    solveInner(id);

    double topStart = adjustTop ? evaluateSpecValueSafely(topSpecification) : Double.NaN;
    double bottomStart = adjustBottom ? evaluateSpecValueSafely(bottomSpecification) : Double.NaN;
    if (adjustTop && !Double.isFinite(topStart)) {
      topStart = topSpecification.getTargetValue();
    }
    if (adjustBottom && !Double.isFinite(bottomStart)) {
      bottomStart = bottomSpecification.getTargetValue();
    }

    int stageCount = Math.max(1, steps);
    for (int step = 1; step <= stageCount; step++) {
      double fraction = step / (double) stageCount;
      ColumnSpecification stagedTop =
          adjustTop ? createHomotopySpecification(topSpecification, topStart, fraction)
              : topSpecification;
      ColumnSpecification stagedBottom =
          adjustBottom ? createHomotopySpecification(bottomSpecification, bottomStart, fraction)
              : bottomSpecification;
      solveWithSpecificationTargets(id, stagedTop, stagedBottom);
      lastSpecificationHomotopyStepCount = step;
      setDoInitializion(false);
    }
    updateSpecificationResiduals();
  }

  /**
   * Solve the column against the provided effective top and bottom specification targets.
   *
   * @param id calculation identifier
   * @param effectiveTopSpecification effective top specification for this solve
   * @param effectiveBottomSpecification effective bottom specification for this solve
   */
  private void solveWithSpecificationTargets(UUID id, ColumnSpecification effectiveTopSpecification,
      ColumnSpecification effectiveBottomSpecification) {
    boolean adjustTop = needsAdjustment(effectiveTopSpecification) && hasCondenser;
    boolean adjustBottom = needsAdjustment(effectiveBottomSpecification) && hasReboiler;

    int maxOuterIter = 20;
    if (adjustTop && effectiveTopSpecification != null) {
      maxOuterIter = Math.max(maxOuterIter, effectiveTopSpecification.getMaxIterations());
    }
    if (adjustBottom && effectiveBottomSpecification != null) {
      maxOuterIter = Math.max(maxOuterIter, effectiveBottomSpecification.getMaxIterations());
    }

    double topTol = adjustTop ? effectiveTopSpecification.getTolerance() : 1.0e-4;
    double bottomTol = adjustBottom ? effectiveBottomSpecification.getTolerance() : 1.0e-4;

    // Initialize temperature bounds from feed conditions
    double feedTemp = estimateFeedTemperature();

    // Initial guesses for temperatures to adjust
    double topTemp =
        hasCondenser && getCondenser().isSetOutTemperature() ? getCondenser().getOutTemperature()
            : feedTemp - 20.0;
    double bottomTemp =
        hasReboiler && getReboiler().isSetOutTemperature() ? getReboiler().getOutTemperature()
            : feedTemp + 20.0;

    // Secant method state for top
    double topTemp0 = topTemp;
    double topTemp1 = topTemp - 5.0;
    double topErr0 = Double.NaN;
    double topErr1 = Double.NaN;

    // Secant method state for bottom
    double bottomTemp0 = bottomTemp;
    double bottomTemp1 = bottomTemp + 5.0;
    double bottomErr0 = Double.NaN;
    double bottomErr1 = Double.NaN;

    for (int outerIter = 0; outerIter < maxOuterIter; outerIter++) {
      // Set current guess temperatures
      if (adjustTop) {
        double currentTopTemp = (outerIter == 0) ? topTemp0 : topTemp1;
        getCondenser().setOutTemperature(currentTopTemp);
      }
      if (adjustBottom) {
        double currentBottomTemp = (outerIter == 0) ? bottomTemp0 : bottomTemp1;
        getReboiler().setOutTemperature(currentBottomTemp);
      }
      applySpecificationTemperatureGuess(adjustTop, adjustBottom,
          adjustTop ? (outerIter == 0 ? topTemp0 : topTemp1) : Double.NaN,
          adjustBottom ? (outerIter == 0 ? bottomTemp0 : bottomTemp1) : Double.NaN);

      // Keep the previous stage profile after the first full initialization.
      setDoInitializion(outerIter == 0 && !hasBeenSolvedBefore);

      // Solve the column with current settings
      solveInner(id);

      if (!solved()) {
        logger.warn("Inner solver did not converge in outer iteration {}", outerIter);
        // Try to continue with reduced step
      }

      // Evaluate specification errors
      double topError = adjustTop ? evaluateSpecError(effectiveTopSpecification) : 0.0;
      double bottomError = adjustBottom ? evaluateSpecError(effectiveBottomSpecification) : 0.0;
      lastTopSpecificationResidual = topError;
      lastBottomSpecificationResidual = bottomError;

      logger.debug("Spec outer iteration {} topErr={} bottomErr={} topT={} bottomT={}", outerIter,
          topError, bottomError, adjustTop ? (outerIter == 0 ? topTemp0 : topTemp1) : 0.0,
          adjustBottom ? (outerIter == 0 ? bottomTemp0 : bottomTemp1) : 0.0);

      // Check convergence
      boolean topConverged = !adjustTop || Math.abs(topError) < topTol;
      boolean bottomConverged = !adjustBottom || Math.abs(bottomError) < bottomTol;

      if (topConverged && bottomConverged) {
        break;
      }

      // Update secant method for top temperature
      if (adjustTop && !topConverged) {
        if (outerIter == 0) {
          topErr0 = topError;
        } else {
          topErr1 = topError;
          double newTopTemp = secantStep(topTemp0, topTemp1, topErr0, topErr1, feedTemp);
          topTemp0 = topTemp1;
          topErr0 = topErr1;
          topTemp1 = newTopTemp;
        }
      }

      // Update secant method for bottom temperature
      if (adjustBottom && !bottomConverged) {
        if (outerIter == 0) {
          bottomErr0 = bottomError;
        } else {
          bottomErr1 = bottomError;
          double newBottomTemp =
              secantStep(bottomTemp0, bottomTemp1, bottomErr0, bottomErr1, feedTemp);
          bottomTemp0 = bottomTemp1;
          bottomErr0 = bottomErr1;
          bottomTemp1 = newBottomTemp;
        }
      }
    }
  }

  /**
   * Build one staged specification by interpolating from a start value to the final target.
   *
   * @param specification final user specification
   * @param startValue initial product value from the warm baseline solve
   * @param fraction continuation fraction, where one means the final user target
   * @return staged specification preserving the original tolerance and iteration limit
   */
  private ColumnSpecification createHomotopySpecification(ColumnSpecification specification,
      double startValue, double fraction) {
    double boundedFraction = Math.max(0.0, Math.min(1.0, fraction));
    double target = startValue + boundedFraction * (specification.getTargetValue() - startValue);
    target = boundSpecificationTarget(specification, target);
    ColumnSpecification staged = new ColumnSpecification(specification.getType(),
        specification.getLocation(), target, specification.getComponentName());
    staged.setTolerance(specification.getTolerance());
    staged.setMaxIterations(specification.getMaxIterations());
    return staged;
  }

  /**
   * Bound a staged target so it remains valid for its specification type.
   *
   * @param specification specification defining the valid target range
   * @param target staged target candidate
   * @return bounded finite target value
   */
  private double boundSpecificationTarget(ColumnSpecification specification, double target) {
    double finiteTarget = Double.isFinite(target) ? target : specification.getTargetValue();
    if (specification.getType() == ColumnSpecification.SpecificationType.PRODUCT_PURITY
        || specification.getType() == ColumnSpecification.SpecificationType.COMPONENT_RECOVERY) {
      return Math.max(0.0, Math.min(1.0, finiteTarget));
    }
    if (specification.getType() == ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE) {
      return Math.max(1.0e-12, finiteTarget);
    }
    return finiteTarget;
  }

  /**
   * Apply temperature guesses and seed the internal tray-temperature profile.
   *
   * @param adjustTop whether the condenser temperature is being adjusted
   * @param adjustBottom whether the reboiler temperature is being adjusted
   * @param topTemperature top temperature guess in kelvin
   * @param bottomTemperature bottom temperature guess in kelvin
   */
  private void applySpecificationTemperatureGuess(boolean adjustTop, boolean adjustBottom,
      double topTemperature, double bottomTemperature) {
    double top = adjustTop && Double.isFinite(topTemperature) ? topTemperature : Double.NaN;
    double bottom =
        adjustBottom && Double.isFinite(bottomTemperature) ? bottomTemperature : Double.NaN;
    if (!Double.isFinite(top) && hasCondenser && getCondenser().isSetOutTemperature()) {
      top = getCondenser().getOutTemperature();
    }
    if (!Double.isFinite(bottom) && hasReboiler && getReboiler().isSetOutTemperature()) {
      bottom = getReboiler().getOutTemperature();
    }
    seedTrayTemperatureProfile(top, bottom);
  }

  /**
   * Seed tray temperatures linearly between bottom and top endpoints.
   *
   * @param topTemperature top-stage seed temperature in kelvin
   * @param bottomTemperature bottom-stage seed temperature in kelvin
   */
  private void seedTrayTemperatureProfile(double topTemperature, double bottomTemperature) {
    if (!Double.isFinite(topTemperature) && !Double.isFinite(bottomTemperature)) {
      return;
    }
    double feedTemperature = estimateFeedTemperature();
    double top = Double.isFinite(topTemperature) ? topTemperature : feedTemperature;
    double bottom = Double.isFinite(bottomTemperature) ? bottomTemperature : feedTemperature;
    ensureSeedTemperatureArray();
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      double fraction = numberOfTrays <= 1 ? 0.0 : trayIndex / (numberOfTrays - 1.0);
      double temperature = bottom + fraction * (top - bottom);
      seedTemperatures[trayIndex] = temperature;
      trays.get(trayIndex).setTemperature(temperature);
      try {
        trays.get(trayIndex).getThermoSystem().setTemperature(temperature);
      } catch (RuntimeException exception) {
        logger.debug("Could not seed tray temperature for {}", trays.get(trayIndex).getName(),
            exception);
      }
    }
  }

  /**
   * Compute the secant method step for temperature adjustment, with safeguards.
   *
   * @param t0 previous temperature
   * @param t1 current temperature
   * @param f0 spec error at t0
   * @param f1 spec error at t1
   * @param feedTemp reference feed temperature for bounding
   * @return the next temperature guess
   */
  private double secantStep(double t0, double t1, double f0, double f1, double feedTemp) {
    double denom = f1 - f0;
    double tNew;
    if (Math.abs(denom) < 1.0e-15) {
      // Secant denominator too small — perturb
      tNew = t1 + 2.0;
    } else {
      tNew = t1 - f1 * (t1 - t0) / denom;
    }
    // Limit the step to avoid unreasonable temperature jumps
    double maxStep = 50.0;
    if (tNew - t1 > maxStep) {
      tNew = t1 + maxStep;
    } else if (tNew - t1 < -maxStep) {
      tNew = t1 - maxStep;
    }
    // Keep temperature physically reasonable (above 100 K, below 1000 K)
    tNew = Math.max(100.0, Math.min(1000.0, tNew));
    return tNew;
  }

  /**
   * Checks whether a specification requires iterative temperature adjustment.
   *
   * @param spec the column specification to check
   * @return true if the specification is non-null and requires adjustment
   */
  private boolean needsAdjustment(ColumnSpecification spec) {
    if (spec == null) {
      return false;
    }
    // Reflux ratio and duty specs are handled directly; the others need outer-loop
    // adjustment
    return spec.getType() != ColumnSpecification.SpecificationType.REFLUX_RATIO
        && spec.getType() != ColumnSpecification.SpecificationType.DUTY;
  }

  /**
   * Run the inner column solver (one full solve with the currently selected solver type) without
   * resetting convergence history. Used by {@link #solveWithSpecifications(UUID)} in the outer
   * adjustment loop.
   *
   * @param id calculation identifier
   */
  private void solveInner(UUID id) {
    solveSelectedSolver(id);
  }

  /**
   * Solve using direct substitution.
   *
   * @param id calculation identifier
   */
  void solveDirectSubstitution(UUID id) {
    solveSequential(id, 1.0);
  }

  /**
   * Solve using damped substitution and the configured relaxation factor.
   *
   * @param id calculation identifier
   */
  void solveDampedSubstitution(UUID id) {
    solveSequential(id, relaxationFactor);
  }

  /**
   * Solve using inside-out initialization while reporting MESH residual diagnostics.
   *
   * @param id calculation identifier
   */
  void solveMeshResidual(UUID id) {
    solveInsideOut(id);
    updateMeshResiduals();
    if (meshResidualNeedsPolishing()) {
      double residualNorm =
          lastMeshResidual == null ? Double.NaN : lastMeshResidual.getInfinityNorm();
      if (!tryGuardedMeshNewtonPolish(id, residualNorm)) {
        logger.debug("MESH residual Newton polish rejected for column {}; residual={}", getName(),
            Double.valueOf(residualNorm));
      }
    }
  }

  /**
   * Solve using Naphtali-Sandholm simultaneous MESH equation linearization.
   *
   * <p>
   * The rigorous residual solver is warm-started from the current inside-out path. If the Newton
   * refinement does not produce a residual-improving state, the accepted inside-out state is kept.
   * In that rejected-state path, the column products and solve metrics remain the inside-out
   * warm-start values. This preserves the robust legacy behavior while making the new solver an
   * explicit residual-driven option.
   * </p>
   *
   * @param id calculation identifier
   * @return {@code true} when the Naphtali-Sandholm solver accepted its direct result
   */
  boolean solveNaphtaliSandholm(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return false;
    }

    if (numberOfTrays == 1) {
      solveDirectSubstitution(id);
      return solved();
    }

    long startTime = System.nanoTime();

    Map<Integer, List<SystemInterface>> originalFeedSystems = new java.util.HashMap<>();
    Map<Integer, List<Double>> originalFeedFlowRates = new java.util.HashMap<>();
    for (Map.Entry<Integer, List<StreamInterface>> entry : feedStreams.entrySet()) {
      List<SystemInterface> clones = new java.util.ArrayList<>();
      List<Double> flowRates = new java.util.ArrayList<>();
      for (StreamInterface feed : entry.getValue()) {
        clones.add(feed.getThermoSystem().clone());
        flowRates.add(feed.getFlowRate("mol/hr"));
      }
      originalFeedSystems.put(entry.getKey(), clones);
      originalFeedFlowRates.put(entry.getKey(), flowRates);
    }

    if (isDoInitializion()) {
      this.init();
    }
    prepareColumnForSolve();

    NaphtaliSandholmSolver solver =
        new NaphtaliSandholmSolver(this, originalFeedSystems, originalFeedFlowRates);
    solver.setMaxIterations(maxNumberOfIterations);
    solver.setTolerance(1.0e-8);
    boolean accepted = solver.solve(id);
    storeNaphtaliTelemetry(solver);
    markSolverTypeUsed(SolverType.NAPHTALI_SANDHOLM);

    double temperatureResidual = accepted ? solver.getLastTemperatureResidual() : 1.0e10;
    finalizeNaphtaliSolve(id, solver.getLastIterations(), temperatureResidual,
        solver.getLastMassBalanceError(), solver.getLastEnergyResidual(), startTime);
    hasBeenSolvedBefore = true;
    lastTotalFeedFlow = -1.0;

    if (!accepted) {
      logger.warn("Naphtali-Sandholm solver did not fully converge for column {}", getName());
    }
    return accepted;
  }

  /**
   * Finalize a direct Naphtali-Sandholm solve without invoking generic product reconciliation.
   *
   * @param id calculation identifier
   * @param iterations number of solver iterations
   * @param temperatureResidual final temperature residual
   * @param massResidual final mass residual
   * @param energyResidual final energy residual
   * @param startTime nano time when the solve started
   */
  private void finalizeNaphtaliSolve(UUID id, int iterations, double temperatureResidual,
      double massResidual, double energyResidual, long startTime) {
    err = temperatureResidual;
    lastIterationCount = iterations;
    lastTemperatureResidual = temperatureResidual;
    lastMassResidual = massResidual;
    lastEnergyResidual = energyResidual;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    lastUsedFeedFlashFallback = false;
    lastInternalTrafficGuardReached = false;

    gasOutStream.setThermoSystem(trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem());
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem());
    liquidOutStream.setCalculationIdentifier(id);

    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setCalculationIdentifier(id);
    }
    lastSolveStatus = SolveStatus.RECONCILED_PRODUCTS;
    lastSolveStatusReason = "Naphtali-Sandholm direct products were applied";
    setCalculationIdentifier(id);
  }

  /**
   * Store telemetry reported by a Naphtali-Sandholm solver instance.
   *
   * @param solver solver instance containing latest linearization and thermodynamic metrics
   */
  private void storeNaphtaliTelemetry(NaphtaliSandholmSolver solver) {
    lastNaphtaliAnalyticJacobianColumns = solver.getLastAnalyticJacobianColumns();
    lastNaphtaliFiniteDifferenceJacobianColumns = solver.getLastFiniteDifferenceJacobianColumns();
    lastNaphtaliThermoEvaluationCount = solver.getLastThermoEvaluationCount();
    lastNaphtaliThermoCacheHitCount = solver.getLastThermoCacheHitCount();
    lastNaphtaliJacobianBuildTimeSeconds = solver.getLastJacobianBuildTimeSeconds();
    lastNaphtaliBlockLinearSolveCount = solver.getLastBlockLinearSolveCount();
    lastNaphtaliDenseLinearSolveCount = solver.getLastDenseLinearSolveCount();
    lastNaphtaliLinearSolveTimeSeconds = solver.getLastLinearSolveTimeSeconds();
  }

  /**
   * Return the smaller of two finite values, or the finite value when only one is finite.
   *
   * @param first first candidate value
   * @param second second candidate value
   * @return finite minimum, or zero if neither value is finite
   */
  private double finiteMinimum(double first, double second) {
    if (Double.isFinite(first) && Double.isFinite(second)) {
      return Math.min(first, second);
    }
    return finiteOr(first, finiteOr(second, 0.0));
  }

  /**
   * Return a fallback for non-finite values.
   *
   * @param value value to inspect
   * @param fallback fallback value
   * @return value when finite, otherwise fallback
   */
  private double finiteOr(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }

  /**
   * Try one Newton polishing pass on a deep-copied candidate column and accept it only if the MESH
   * residual norm improves.
   *
   * <p>
   * Running the aggressive Newton accelerator on a candidate protects the accepted inside-out
   * solution from flash failures, non-finite states, or residual growth. This provides a bounded
   * line-search style guard for the residual-monitored solver without changing the legacy Newton
   * solver contract.
   * </p>
   *
   * @param id calculation identifier
   * @param baselineResidualNorm accepted residual norm before the polish attempt
   * @return {@code true} if the candidate polish was accepted
   */
  private boolean tryGuardedMeshNewtonPolish(UUID id, double baselineResidualNorm) {
    if (!Double.isFinite(baselineResidualNorm)) {
      return false;
    }
    double baselineGasFlow = getProductFlowKgPerHour(gasOutStream);
    double baselineLiquidFlow = getProductFlowKgPerHour(liquidOutStream);
    DistillationColumn candidate;
    try {
      candidate = (DistillationColumn) this.copy();
    } catch (RuntimeException exception) {
      logger.debug("MESH Newton polish skipped because candidate copy failed.", exception);
      return false;
    }

    try {
      candidate.solveNewton(id);
      candidate.updateMeshResiduals();
    } catch (RuntimeException exception) {
      logger.debug("MESH Newton polish rejected because the candidate solve failed.", exception);
      return false;
    }

    double candidateResidualNorm = candidate.lastMeshResidual == null ? Double.NaN
        : candidate.lastMeshResidual.getInfinityNorm();
    if (!Double.isFinite(candidateResidualNorm)
        || candidateResidualNorm >= baselineResidualNorm * 0.999) {
      logger.debug("MESH Newton polish rejected: residual {} did not improve baseline {}.",
          Double.valueOf(candidateResidualNorm), Double.valueOf(baselineResidualNorm));
      return false;
    }

    if (!meshPolishProductSplitMatches(candidate, baselineGasFlow, baselineLiquidFlow)) {
      logger.debug(
          "MESH Newton polish rejected: product split changed from gas/liquid "
              + "{}/{} kg/hr to {}/{} kg/hr.",
          Double.valueOf(baselineGasFlow), Double.valueOf(baselineLiquidFlow),
          Double.valueOf(getProductFlowKgPerHour(candidate.gasOutStream)),
          Double.valueOf(getProductFlowKgPerHour(candidate.liquidOutStream)));
      return false;
    }

    acceptSolvedStateCandidate(candidate);
    logger.debug("MESH Newton polish accepted: residual {} improved baseline {}.",
        Double.valueOf(candidateResidualNorm), Double.valueOf(baselineResidualNorm));
    return true;
  }

  /**
   * Check whether a MESH Newton polish preserved the accepted terminal product split.
   *
   * @param candidate candidate column after Newton polishing
   * @param baselineGasFlow gas product flow before polishing in kg/hr
   * @param baselineLiquidFlow liquid product flow before polishing in kg/hr
   * @return {@code true} when both product flows remain within tolerance
   */
  private boolean meshPolishProductSplitMatches(DistillationColumn candidate,
      double baselineGasFlow, double baselineLiquidFlow) {
    if (candidate == null) {
      return false;
    }
    double candidateGasFlow = getProductFlowKgPerHour(candidate.gasOutStream);
    double candidateLiquidFlow = getProductFlowKgPerHour(candidate.liquidOutStream);
    return productFlowWithinMeshPolishTolerance(candidateGasFlow, baselineGasFlow)
        && productFlowWithinMeshPolishTolerance(candidateLiquidFlow, baselineLiquidFlow);
  }

  /**
   * Read a product stream flow in kg/hr.
   *
   * @param stream product stream to inspect
   * @return flow in kg/hr, or {@link Double#NaN} when unavailable
   */
  private double getProductFlowKgPerHour(StreamInterface stream) {
    if (stream == null) {
      return Double.NaN;
    }
    try {
      return stream.getFlowRate("kg/hr");
    } catch (RuntimeException exception) {
      return Double.NaN;
    }
  }

  /**
   * Compare a candidate product flow against a baseline flow for MESH polish acceptance.
   *
   * @param candidateFlow candidate flow in kg/hr
   * @param baselineFlow baseline flow in kg/hr
   * @return {@code true} if the candidate flow is finite and within tolerance
   */
  private boolean productFlowWithinMeshPolishTolerance(double candidateFlow, double baselineFlow) {
    if (!Double.isFinite(candidateFlow) || !Double.isFinite(baselineFlow)) {
      return false;
    }
    double tolerance =
        Math.max(1.0e-8, Math.abs(baselineFlow) * MESH_POLISH_PRODUCT_FLOW_TOLERANCE);
    return Math.abs(candidateFlow - baselineFlow) <= tolerance;
  }

  /**
   * Copy the solved state from an accepted candidate back to this live column.
   *
   * <p>
   * User-facing feed stream maps are intentionally not replaced: they may contain stream object
   * identities supplied by callers and are reused on later runs. The candidate tray network already
   * contains equivalent cloned feed streams for the accepted solved state, and the preserved maps
   * will refresh tray inputs from the caller-owned streams on the next solve.
   * </p>
   *
   * @param candidate accepted candidate column
   */
  private void acceptSolvedStateCandidate(DistillationColumn candidate) {
    this.trays = candidate.trays;
    this.numberOfTrays = candidate.numberOfTrays;
    this.distoperations = candidate.distoperations;
    this.feedmixer = candidate.feedmixer;
    this.stream_3 = candidate.stream_3;
    this.gasOutStream = candidate.gasOutStream;
    this.liquidOutStream = candidate.liquidOutStream;
    this.stream_3isset = candidate.stream_3isset;
    this.heater = candidate.heater;
    this.separator2 = candidate.separator2;
    this.err = candidate.err;
    this.lastIterationCount = candidate.lastIterationCount;
    this.lastTemperatureResidual = candidate.lastTemperatureResidual;
    this.lastMassResidual = candidate.lastMassResidual;
    this.lastEnergyResidual = candidate.lastEnergyResidual;
    this.lastTopSpecificationResidual = candidate.lastTopSpecificationResidual;
    this.lastBottomSpecificationResidual = candidate.lastBottomSpecificationResidual;
    this.lastMeshResidual = candidate.lastMeshResidual;
    this.lastUsedFeedFlashFallback = candidate.lastUsedFeedFlashFallback;
    this.lastInternalTrafficRatio = candidate.lastInternalTrafficRatio;
    this.lastInternalTrafficGuardReached = candidate.lastInternalTrafficGuardReached;
    this.internalTrafficCapActive = candidate.internalTrafficCapActive;
    this.terminalGasProductDrawStream = candidate.terminalGasProductDrawStream;
    this.terminalLiquidProductDrawStream = candidate.terminalLiquidProductDrawStream;
    this.lastSolveTimeSeconds = candidate.lastSolveTimeSeconds;
    this.lastInsideOutOuterFlashSweeps = candidate.lastInsideOutOuterFlashSweeps;
    this.lastInsideOutInnerLoopIterations = candidate.lastInsideOutInnerLoopIterations;
    this.lastInsideOutKValueResidual = candidate.lastInsideOutKValueResidual;
    this.lastInsideOutSurrogateResidual = candidate.lastInsideOutSurrogateResidual;
    this.lastInsideOutSurrogateResetCount = candidate.lastInsideOutSurrogateResetCount;
    this.lastMatrixInsideOutWarmStartUsed = candidate.lastMatrixInsideOutWarmStartUsed;
    this.lastMatrixInsideOutWarmStartBypassed = candidate.lastMatrixInsideOutWarmStartBypassed;
    this.lastMatrixInsideOutIterationCount = candidate.lastMatrixInsideOutIterationCount;
    this.lastMatrixInsideOutTemperatureResidual = candidate.lastMatrixInsideOutTemperatureResidual;
    this.lastMatrixInsideOutSolveTimeSeconds = candidate.lastMatrixInsideOutSolveTimeSeconds;
    this.lastNaphtaliAnalyticJacobianColumns = candidate.lastNaphtaliAnalyticJacobianColumns;
    this.lastNaphtaliFiniteDifferenceJacobianColumns =
        candidate.lastNaphtaliFiniteDifferenceJacobianColumns;
    this.lastNaphtaliThermoEvaluationCount = candidate.lastNaphtaliThermoEvaluationCount;
    this.lastNaphtaliThermoCacheHitCount = candidate.lastNaphtaliThermoCacheHitCount;
    this.lastNaphtaliJacobianBuildTimeSeconds = candidate.lastNaphtaliJacobianBuildTimeSeconds;
    this.lastNaphtaliBlockLinearSolveCount = candidate.lastNaphtaliBlockLinearSolveCount;
    this.lastNaphtaliDenseLinearSolveCount = candidate.lastNaphtaliDenseLinearSolveCount;
    this.lastNaphtaliLinearSolveTimeSeconds = candidate.lastNaphtaliLinearSolveTimeSeconds;
    this.hasBeenSolvedBefore = candidate.hasBeenSolvedBefore;
    this.lastTotalFeedFlow = candidate.lastTotalFeedFlow;
    this.doInitializion = candidate.doInitializion;
    this.lastSolverTypeUsed = candidate.lastSolverTypeUsed;
    this.lastSolveStatus = candidate.lastSolveStatus;
    this.lastSolveStatusReason = candidate.lastSolveStatusReason;
    this.lastAutoSolverSummary = candidate.lastAutoSolverSummary;
    this.lastAutoFeasibilityReport = candidate.lastAutoFeasibilityReport;
    this.lastInitializationReport = candidate.lastInitializationReport;
    this.lastAutoSolverHistory = candidate.lastAutoSolverHistory == null ? new ArrayList<String>()
        : new ArrayList<String>(candidate.lastAutoSolverHistory);
    this.specificationHomotopySteps = candidate.specificationHomotopySteps;
    this.lastSpecificationHomotopyStepCount = candidate.lastSpecificationHomotopyStepCount;
  }

  /**
   * Accept a candidate produced by the automatic solver selector.
   *
   * @param candidate solved or best available candidate state
   * @param selectedSolver solver strategy used for the candidate
   */
  void acceptAutoSolverCandidate(DistillationColumn candidate, SolverType selectedSolver) {
    acceptSolvedStateCandidate(candidate);
    lastSolverTypeUsed = selectedSolver;
  }

  /**
   * Store the candidate trace from the automatic solver selector.
   *
   * @param summary human-readable candidate trace, or {@code null} to clear it
   */
  void setLastAutoSolverSummary(String summary) {
    lastAutoSolverSummary = summary == null ? "" : summary;
  }

  /**
   * Store the feasibility report from automatic solver pre-screening.
   *
   * @param report feasibility report text, or {@code null} to clear it
   */
  void setLastAutoFeasibilityReport(String report) {
    lastAutoFeasibilityReport = report == null ? "" : report;
  }

  /**
   * Store the latest automatic initialization report.
   *
   * @param report initialization report text, or {@code null} to clear it
   */
  void setLastInitializationReport(String report) {
    lastInitializationReport = report == null ? "" : report;
  }

  /**
   * Record one automatic solver pipeline event.
   *
   * @param event concise event text
   */
  void recordAutoSolverEvent(String event) {
    if (lastAutoSolverHistory == null) {
      lastAutoSolverHistory = new ArrayList<String>();
    }
    if (event != null && !event.trim().isEmpty()) {
      lastAutoSolverHistory.add(event);
    }
  }

  /**
   * Accept a damped fallback candidate after an accelerator result has been rejected.
   *
   * @param candidate solved fallback candidate
   * @param reason reason the accelerator result was rejected
   */
  void acceptDampedFallbackCandidate(DistillationColumn candidate, String reason) {
    logger.warn("Accelerated solver result rejected for column {}: {}. Using damped "
        + "substitution fallback candidate.", getName(), reason);
    acceptSolvedStateCandidate(candidate);
    lastSolverTypeUsed = SolverType.DAMPED_SUBSTITUTION;
    lastSolveStatusReason = reason;
  }

  /**
   * Accept a residual-monitored warm-start state after rejecting a Naphtali-Sandholm candidate.
   *
   * <p>
   * The accepted state comes from the warm-start solver, but the strategy reported to callers
   * remains {@link SolverType#NAPHTALI_SANDHOLM}. Naphtali-Sandholm telemetry from the rejected
   * candidate is preserved so diagnostics still show the attempted linearization work.
   * </p>
   *
   * @param candidate solved warm-start candidate to keep
   * @param reason reason the direct Naphtali-Sandholm candidate was rejected
   */
  void acceptNaphtaliWarmStartCandidate(DistillationColumn candidate, String reason) {
    int analyticJacobianColumns = lastNaphtaliAnalyticJacobianColumns;
    int finiteDifferenceJacobianColumns = lastNaphtaliFiniteDifferenceJacobianColumns;
    int thermoEvaluationCount = lastNaphtaliThermoEvaluationCount;
    int thermoCacheHitCount = lastNaphtaliThermoCacheHitCount;
    double jacobianBuildTimeSeconds = lastNaphtaliJacobianBuildTimeSeconds;
    int blockLinearSolveCount = lastNaphtaliBlockLinearSolveCount;
    int denseLinearSolveCount = lastNaphtaliDenseLinearSolveCount;
    double linearSolveTimeSeconds = lastNaphtaliLinearSolveTimeSeconds;

    logger.warn("Naphtali-Sandholm candidate rejected for column {}: {}. Keeping "
        + "residual-monitored warm-start state.", getName(), reason);
    acceptSolvedStateCandidate(candidate);
    lastSolverTypeUsed = SolverType.NAPHTALI_SANDHOLM;
    lastSolveStatusReason = reason;
    lastNaphtaliAnalyticJacobianColumns = analyticJacobianColumns;
    lastNaphtaliFiniteDifferenceJacobianColumns = finiteDifferenceJacobianColumns;
    lastNaphtaliThermoEvaluationCount = thermoEvaluationCount;
    lastNaphtaliThermoCacheHitCount = thermoCacheHitCount;
    lastNaphtaliJacobianBuildTimeSeconds = jacobianBuildTimeSeconds;
    lastNaphtaliBlockLinearSolveCount = blockLinearSolveCount;
    lastNaphtaliDenseLinearSolveCount = denseLinearSolveCount;
    lastNaphtaliLinearSolveTimeSeconds = linearSolveTimeSeconds;
  }

  /**
   * Check whether the latest MESH residual still needs Newton polishing.
   *
   * @return {@code true} when no finite residual is available or the norm is above tolerance
   */
  private boolean meshResidualNeedsPolishing() {
    return lastMeshResidual == null || !lastMeshResidual.isFinite()
        || lastMeshResidual.getInfinityNorm() > meshResidualTolerance
        || !productDrawResidualsSatisfied();
  }

  /**
   * Evaluate how far the current column solution is from satisfying a specification.
   *
   * @param spec the column specification to evaluate
   * @return the error (current value minus target value); zero when satisfied
   */
  private double evaluateSpecError(ColumnSpecification spec) {
    if (spec == null) {
      return 0.0;
    }

    StreamInterface productStream;
    if (spec.getLocation() == ColumnSpecification.ProductLocation.TOP) {
      productStream = gasOutStream;
    } else {
      productStream = liquidOutStream;
    }

    switch (spec.getType()) {
      case PRODUCT_PURITY: {
        double currentPurity =
            productStream.getFluid().getComponent(spec.getComponentName()).getz();
        return currentPurity - spec.getTargetValue();
      }
      case COMPONENT_RECOVERY: {
        double productCompFlow = productStream.getFluid().getComponent(spec.getComponentName())
            .getTotalFlowRate("mol/hr");
        double totalFeedCompFlow = getTotalFeedComponentFlow(spec.getComponentName());
        double recovery = (totalFeedCompFlow > 1.0e-12) ? productCompFlow / totalFeedCompFlow : 0.0;
        return recovery - spec.getTargetValue();
      }
      case PRODUCT_FLOW_RATE: {
        double currentFlow = productStream.getFluid().getFlowRate("mol/hr");
        return currentFlow - spec.getTargetValue();
      }
      default:
        return 0.0;
    }
  }

  /**
   * Calculate the total feed flow of a named component across all feed streams.
   *
   * @param componentName the component name
   * @return total molar flow in mol/hr
   */
  private double getTotalFeedComponentFlow(String componentName) {
    double total = 0.0;
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      total += feed.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
    }
    return total;
  }

  /**
   * Estimate a representative feed temperature from the assigned feed streams.
   *
   * @return average feed temperature in Kelvin
   */
  private double estimateFeedTemperature() {
    double sumTemp = 0.0;
    int count = 0;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface feed : feeds) {
        sumTemp += feed.getTemperature("K");
        count++;
      }
    }
    return count > 0 ? sumTemp / count : 300.0;
  }

  /**
   * Assign queued feed streams to estimated tray locations.
   */
  private void assignUnassignedFeeds() {
    if (unassignedFeedStreams.isEmpty()) {
      return;
    }

    if (numberOfTrays == 0) {
      return;
    }

    Iterator<StreamInterface> iter = unassignedFeedStreams.iterator();
    while (iter.hasNext()) {
      StreamInterface feed = iter.next();
      int bestTray = estimateFeedTrayNumber(feed);

      addFeedStream(feed, bestTray);
      iter.remove();
    }
  }

  /**
   * Estimate the feed tray number from a feed temperature.
   *
   * @param feedTemperature feed stream temperature in Kelvin
   * @return 0-based estimated feed tray number
   */
  private int estimateFeedTrayNumber(double feedTemperature) {
    int firstFeedTray = getFirstFeedTrayCandidate();
    int lastFeedTray = getLastFeedTrayCandidate();
    if (firstFeedTray > lastFeedTray) {
      firstFeedTray = 0;
      lastFeedTray = numberOfTrays - 1;
    }

    if (!isUsableTemperature(feedTemperature)) {
      return (firstFeedTray + lastFeedTray) / 2;
    }

    boolean useTrayProfile = hasUsableTrayTemperatureProfile(firstFeedTray, lastFeedTray);
    int bestTray = (firstFeedTray + lastFeedTray) / 2;
    double minimumTemperatureDifference = Double.MAX_VALUE;

    for (int trayNumber = firstFeedTray; trayNumber <= lastFeedTray; trayNumber++) {
      double trayTemperature = useTrayProfile ? trays.get(trayNumber).getTemperature()
          : estimateTrayTemperatureFromColumnEnds(trayNumber, feedTemperature);
      if (!isUsableTemperature(trayTemperature)) {
        continue;
      }

      double temperatureDifference = Math.abs(trayTemperature - feedTemperature);
      if (temperatureDifference < minimumTemperatureDifference || Math
          .abs(temperatureDifference - minimumTemperatureDifference) <= FEED_TRAY_TIE_TOLERANCE) {
        minimumTemperatureDifference = temperatureDifference;
        bestTray = trayNumber;
      }
    }
    return bestTray;
  }

  /**
   * Return the first tray to consider for automatic feed placement.
   *
   * @return first 0-based feed tray candidate
   */
  private int getFirstFeedTrayCandidate() {
    return hasReboiler && numberOfTrays > 1 ? 1 : 0;
  }

  /**
   * Return the last tray to consider for automatic feed placement.
   *
   * @return last 0-based feed tray candidate
   */
  private int getLastFeedTrayCandidate() {
    int lastFeedTray = numberOfTrays - 1;
    if (hasCondenser && lastFeedTray > 0) {
      lastFeedTray--;
    }
    return lastFeedTray;
  }

  /**
   * Check whether the column already has a useful tray temperature profile.
   *
   * @param firstFeedTray first tray index included in the check
   * @param lastFeedTray last tray index included in the check
   * @return {@code true} when at least two tray temperatures span a useful range
   */
  private boolean hasUsableTrayTemperatureProfile(int firstFeedTray, int lastFeedTray) {
    double minimumTemperature = Double.MAX_VALUE;
    double maximumTemperature = -Double.MAX_VALUE;
    int temperatureCount = 0;
    for (int trayNumber = firstFeedTray; trayNumber <= lastFeedTray; trayNumber++) {
      double trayTemperature = trays.get(trayNumber).getTemperature();
      if (!isUsableTemperature(trayTemperature)) {
        continue;
      }
      minimumTemperature = Math.min(minimumTemperature, trayTemperature);
      maximumTemperature = Math.max(maximumTemperature, trayTemperature);
      temperatureCount++;
    }
    return temperatureCount >= 2
        && Math.abs(maximumTemperature - minimumTemperature) > MINIMUM_FEED_PROFILE_SPAN;
  }

  /**
   * Estimate a tray temperature from configured column-end temperatures.
   *
   * @param trayNumber tray index to estimate
   * @param feedTemperature feed stream temperature in Kelvin
   * @return estimated tray temperature in Kelvin, or {@link Double#NaN} if no useful profile exists
   */
  private double estimateTrayTemperatureFromColumnEnds(int trayNumber, double feedTemperature) {
    if (numberOfTrays <= 1) {
      return feedTemperature;
    }

    double bottomTemperature = estimateBottomFeedProfileTemperature(feedTemperature);
    double topTemperature = estimateTopFeedProfileTemperature(feedTemperature, bottomTemperature);
    if (!isUsableTemperature(bottomTemperature) || !isUsableTemperature(topTemperature)
        || bottomTemperature - topTemperature <= MINIMUM_FEED_PROFILE_SPAN) {
      return Double.NaN;
    }

    double trayFraction = trayNumber / (numberOfTrays - 1.0);
    return bottomTemperature + trayFraction * (topTemperature - bottomTemperature);
  }

  /**
   * Estimate the bottom temperature used for initial feed placement.
   *
   * @param feedTemperature feed stream temperature in Kelvin
   * @return bottom temperature estimate in Kelvin
   */
  private double estimateBottomFeedProfileTemperature(double feedTemperature) {
    if (hasReboiler && getReboiler().isSetOutTemperature()
        && isUsableTemperature(getReboiler().getOutTemperature())) {
      return getReboiler().getOutTemperature();
    }
    double trayTemperature = trays.get(0).getTemperature();
    if (isUsableTemperature(trayTemperature)) {
      return trayTemperature;
    }
    return feedTemperature + FEED_PROFILE_END_TEMPERATURE_OFFSET;
  }

  /**
   * Estimate the top temperature used for initial feed placement.
   *
   * @param feedTemperature feed stream temperature in Kelvin
   * @param bottomTemperature bottom temperature estimate in Kelvin
   * @return top temperature estimate in Kelvin
   */
  private double estimateTopFeedProfileTemperature(double feedTemperature,
      double bottomTemperature) {
    int topTrayNumber = numberOfTrays - 1;
    if (hasCondenser && getCondenser().isSetOutTemperature()
        && isUsableTemperature(getCondenser().getOutTemperature())) {
      return getCondenser().getOutTemperature();
    }
    double trayTemperature = trays.get(topTrayNumber).getTemperature();
    if (isUsableTemperature(trayTemperature)) {
      return trayTemperature;
    }
    double topTemperature = feedTemperature - FEED_PROFILE_END_TEMPERATURE_OFFSET;
    if (isUsableTemperature(bottomTemperature)
        && topTemperature >= bottomTemperature - MINIMUM_FEED_PROFILE_SPAN) {
      topTemperature = bottomTemperature - FEED_PROFILE_END_TEMPERATURE_OFFSET;
    }
    return topTemperature;
  }

  /**
   * Check whether a temperature is finite and physically usable for feed placement.
   *
   * @param temperature temperature in Kelvin
   * @return {@code true} when the temperature can be used in the feed-placement heuristic
   */
  private boolean isUsableTemperature(double temperature) {
    return !Double.isNaN(temperature) && !Double.isInfinite(temperature) && temperature > 0.0;
  }

  /**
   * Result from a rigorous tray-count and feed-tray search.
   *
   * <p>
   * The result is immutable and records the selected tray count, selected feed tray, product
   * purity, duty estimates and convergence diagnostics from the final candidate run.
   * </p>
   *
   * @author esol
   * @version 1.0
   */
  public static class TrayOptimizationResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    private final boolean feasible;
    private final int numberOfTrays;
    private final int feedTrayNumber;
    private final String componentName;
    private final boolean topProduct;
    private final double targetPurity;
    private final double productPurity;
    private final double reboilerDuty;
    private final double condenserDuty;
    private final double totalAbsoluteDuty;
    private final int iterationCount;
    private final double temperatureResidual;
    private final double massResidual;
    private final double energyResidual;
    private final int evaluatedCases;
    private final int convergedCases;
    private final String message;

    /**
     * Create a tray optimization result.
     *
     * @param feasible {@code true} if a candidate met the product specification
     * @param numberOfTrays total tray count including reboiler and condenser if present
     * @param feedTrayNumber 0-based feed tray number selected for all optimization feeds
     * @param componentName product component used in the purity specification
     * @param topProduct {@code true} when the purity target applies to the top product
     * @param targetPurity target product mole fraction
     * @param productPurity achieved product mole fraction
     * @param reboilerDuty reboiler duty in W
     * @param condenserDuty condenser duty in W
     * @param totalAbsoluteDuty sum of absolute condenser and reboiler duties in W
     * @param iterationCount solver iteration count for the final candidate
     * @param temperatureResidual final temperature residual in K
     * @param massResidual final relative mass residual
     * @param energyResidual final relative energy residual
     * @param evaluatedCases number of tray-count/feed-tray cases evaluated
     * @param convergedCases number of evaluated cases that converged
     * @param message diagnostic message describing the outcome
     */
    public TrayOptimizationResult(boolean feasible, int numberOfTrays, int feedTrayNumber,
        String componentName, boolean topProduct, double targetPurity, double productPurity,
        double reboilerDuty, double condenserDuty, double totalAbsoluteDuty, int iterationCount,
        double temperatureResidual, double massResidual, double energyResidual, int evaluatedCases,
        int convergedCases, String message) {
      this.feasible = feasible;
      this.numberOfTrays = numberOfTrays;
      this.feedTrayNumber = feedTrayNumber;
      this.componentName = componentName;
      this.topProduct = topProduct;
      this.targetPurity = targetPurity;
      this.productPurity = productPurity;
      this.reboilerDuty = reboilerDuty;
      this.condenserDuty = condenserDuty;
      this.totalAbsoluteDuty = totalAbsoluteDuty;
      this.iterationCount = iterationCount;
      this.temperatureResidual = temperatureResidual;
      this.massResidual = massResidual;
      this.energyResidual = energyResidual;
      this.evaluatedCases = evaluatedCases;
      this.convergedCases = convergedCases;
      this.message = message;
    }

    /**
     * Check whether the optimization found a feasible candidate.
     *
     * @return {@code true} if the product specification was met by a converged candidate
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Get the selected total number of trays.
     *
     * @return total tray count including reboiler and condenser if present, or {@code -1}
     */
    public int getNumberOfTrays() {
      return numberOfTrays;
    }

    /**
     * Get the selected feed tray number.
     *
     * @return 0-based feed tray number, or {@code -1} if no feasible candidate was found
     */
    public int getFeedTrayNumber() {
      return feedTrayNumber;
    }

    /**
     * Get the component used for the product-purity specification.
     *
     * @return component name
     */
    public String getComponentName() {
      return componentName;
    }

    /**
     * Check whether the optimized specification applies to the top product.
     *
     * @return {@code true} for top product, {@code false} for bottom product
     */
    public boolean isTopProduct() {
      return topProduct;
    }

    /**
     * Get the target product mole fraction.
     *
     * @return target purity as mole fraction
     */
    public double getTargetPurity() {
      return targetPurity;
    }

    /**
     * Get the achieved product mole fraction.
     *
     * @return achieved product purity as mole fraction, or {@link Double#NaN}
     */
    public double getProductPurity() {
      return productPurity;
    }

    /**
     * Get the final reboiler duty.
     *
     * @return reboiler duty in W, or {@code 0.0} when no reboiler is present
     */
    public double getReboilerDuty() {
      return reboilerDuty;
    }

    /**
     * Get the final condenser duty.
     *
     * @return condenser duty in W, or {@code 0.0} when no condenser is present
     */
    public double getCondenserDuty() {
      return condenserDuty;
    }

    /**
     * Get the objective duty used to compare candidates with the same tray count.
     *
     * @return sum of absolute condenser and reboiler duties in W
     */
    public double getTotalAbsoluteDuty() {
      return totalAbsoluteDuty;
    }

    /**
     * Get the final solver iteration count.
     *
     * @return iteration count for the final candidate
     */
    public int getIterationCount() {
      return iterationCount;
    }

    /**
     * Get the final temperature residual.
     *
     * @return temperature residual in K
     */
    public double getTemperatureResidual() {
      return temperatureResidual;
    }

    /**
     * Get the final mass residual.
     *
     * @return relative mass residual
     */
    public double getMassResidual() {
      return massResidual;
    }

    /**
     * Get the final energy residual.
     *
     * @return relative energy residual
     */
    public double getEnergyResidual() {
      return energyResidual;
    }

    /**
     * Get the number of evaluated candidate cases.
     *
     * @return evaluated tray-count/feed-tray combinations
     */
    public int getEvaluatedCases() {
      return evaluatedCases;
    }

    /**
     * Get the number of converged candidate cases.
     *
     * @return converged tray-count/feed-tray combinations
     */
    public int getConvergedCases() {
      return convergedCases;
    }

    /**
     * Get the optimization diagnostic message.
     *
     * @return diagnostic message
     */
    public String getMessage() {
      return message;
    }
  }

  /**
   * Result from initializing a rigorous column with shortcut FUG design estimates.
   *
   * <p>
   * The result records the Fenske-Underwood-Gilliland estimates and the translated rigorous-column
   * settings applied to {@link DistillationColumn}: total stage count, bottom-up feed tray and
   * condenser reflux ratio.
   * </p>
   *
   * @author esol
   * @version 1.0
   */
  public static class ShortcutInitializationResult implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private final boolean initialized;
    private final int totalStageCount;
    private final int feedTrayNumber;
    private final int feedTrayNumberFromTop;
    private final double minimumStages;
    private final double minimumRefluxRatio;
    private final double actualStages;
    private final double actualRefluxRatio;
    private final double condenserDuty;
    private final double reboilerDuty;
    private final String lightKey;
    private final String heavyKey;
    private final String message;

    /**
     * Create a shortcut initialization result.
     *
     * @param initialized whether the rigorous column was configured
     * @param totalStageCount total rigorous stage count including condenser/reboiler if present
     * @param feedTrayNumber bottom-up rigorous feed tray index
     * @param feedTrayNumberFromTop shortcut feed tray count from the top product end
     * @param minimumStages Fenske minimum theoretical stages
     * @param minimumRefluxRatio Underwood minimum reflux ratio
     * @param actualStages Gilliland actual theoretical stages
     * @param actualRefluxRatio selected actual reflux ratio
     * @param condenserDuty estimated condenser duty in W
     * @param reboilerDuty estimated reboiler duty in W
     * @param lightKey light-key component name
     * @param heavyKey heavy-key component name
     * @param message diagnostic message
     */
    public ShortcutInitializationResult(boolean initialized, int totalStageCount,
        int feedTrayNumber, int feedTrayNumberFromTop, double minimumStages,
        double minimumRefluxRatio, double actualStages, double actualRefluxRatio,
        double condenserDuty, double reboilerDuty, String lightKey, String heavyKey,
        String message) {
      this.initialized = initialized;
      this.totalStageCount = totalStageCount;
      this.feedTrayNumber = feedTrayNumber;
      this.feedTrayNumberFromTop = feedTrayNumberFromTop;
      this.minimumStages = minimumStages;
      this.minimumRefluxRatio = minimumRefluxRatio;
      this.actualStages = actualStages;
      this.actualRefluxRatio = actualRefluxRatio;
      this.condenserDuty = condenserDuty;
      this.reboilerDuty = reboilerDuty;
      this.lightKey = lightKey;
      this.heavyKey = heavyKey;
      this.message = message;
    }

    /**
     * Check whether initialization succeeded.
     *
     * @return {@code true} when shortcut estimates were applied to the rigorous column
     */
    public boolean isInitialized() {
      return initialized;
    }

    /**
     * Get the applied total stage count.
     *
     * @return total stage count including condenser/reboiler if present, or {@code -1}
     */
    public int getTotalStageCount() {
      return totalStageCount;
    }

    /**
     * Get the applied bottom-up feed tray number.
     *
     * @return bottom-up feed tray number, or {@code -1}
     */
    public int getFeedTrayNumber() {
      return feedTrayNumber;
    }

    /**
     * Get the shortcut feed tray count from the top.
     *
     * @return feed tray from top, or {@code -1}
     */
    public int getFeedTrayNumberFromTop() {
      return feedTrayNumberFromTop;
    }

    /**
     * Get the Fenske minimum stage count.
     *
     * @return minimum theoretical stages
     */
    public double getMinimumStages() {
      return minimumStages;
    }

    /**
     * Get the Underwood minimum reflux ratio.
     *
     * @return minimum reflux ratio
     */
    public double getMinimumRefluxRatio() {
      return minimumRefluxRatio;
    }

    /**
     * Get the Gilliland actual stage estimate.
     *
     * @return actual theoretical stages
     */
    public double getActualStages() {
      return actualStages;
    }

    /**
     * Get the applied actual reflux ratio.
     *
     * @return actual reflux ratio
     */
    public double getActualRefluxRatio() {
      return actualRefluxRatio;
    }

    /**
     * Get the shortcut condenser duty estimate.
     *
     * @return condenser duty in W
     */
    public double getCondenserDuty() {
      return condenserDuty;
    }

    /**
     * Get the shortcut reboiler duty estimate.
     *
     * @return reboiler duty in W
     */
    public double getReboilerDuty() {
      return reboilerDuty;
    }

    /**
     * Get the light-key component name.
     *
     * @return light-key component name
     */
    public String getLightKey() {
      return lightKey;
    }

    /**
     * Get the heavy-key component name.
     *
     * @return heavy-key component name
     */
    public String getHeavyKey() {
      return heavyKey;
    }

    /**
     * Get the diagnostic message.
     *
     * @return initialization diagnostic message
     */
    public String getMessage() {
      return message;
    }
  }

  /**
   * Result from an economic tray-count, feed-tray, and optional reflux/boilup search.
   *
   * <p>
   * The result extends the rigorous tray optimization result with mechanical design, installed
   * capital cost, annual utility cost, and annualized total-cost metrics. Costs are screening-level
   * estimates using the column mechanical design and column cost-estimation correlations.
   * </p>
   *
   * @author esol
   * @version 1.0
   */
  public static class EconomicTrayOptimizationResult extends TrayOptimizationResult {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private final double capitalCost;
    private final double annualUtilityCost;
    private final double annualizedCapitalCost;
    private final double totalAnnualizedCost;
    private final double capitalChargeFactor;
    private final double operatingHoursPerYear;
    private final double steamCostPerTonne;
    private final double coolingWaterCostPerM3;
    private final double trayEfficiency;
    private final int actualTrays;
    private final double columnDiameter;
    private final double columnHeight;
    private final double condenserRefluxRatio;
    private final double reboilerRatio;

    /**
     * Create an economic tray optimization result from a rigorous tray result.
     *
     * @param baseResult rigorous tray optimization result used as the process-design basis
     * @param capitalCost installed capital cost estimate in USD
     * @param annualUtilityCost annual utility cost estimate in USD/year
     * @param annualizedCapitalCost annualized capital cost in USD/year
     * @param totalAnnualizedCost total annualized cost in USD/year
     * @param capitalChargeFactor capital annualization factor in 1/year
     * @param operatingHoursPerYear operating hours used for utility costing in hr/year
     * @param steamCostPerTonne steam cost used for reboiler duty in USD/tonne
     * @param coolingWaterCostPerM3 cooling-water cost used for condenser duty in USD/m3
     * @param trayEfficiency overall tray efficiency used to convert theoretical to actual trays
     * @param actualTrays actual tray count after tray-efficiency correction
     * @param columnDiameter mechanically designed column diameter in m
     * @param columnHeight mechanically designed tangent-to-tangent column height in m
     * @param condenserRefluxRatio selected condenser reflux ratio, or {@link Double#NaN}
     * @param reboilerRatio selected reboiler boilup/reflux ratio, or {@link Double#NaN}
     */
    public EconomicTrayOptimizationResult(TrayOptimizationResult baseResult, double capitalCost,
        double annualUtilityCost, double annualizedCapitalCost, double totalAnnualizedCost,
        double capitalChargeFactor, double operatingHoursPerYear, double steamCostPerTonne,
        double coolingWaterCostPerM3, double trayEfficiency, int actualTrays, double columnDiameter,
        double columnHeight, double condenserRefluxRatio, double reboilerRatio) {
      super(baseResult.isFeasible(), baseResult.getNumberOfTrays(), baseResult.getFeedTrayNumber(),
          baseResult.getComponentName(), baseResult.isTopProduct(), baseResult.getTargetPurity(),
          baseResult.getProductPurity(), baseResult.getReboilerDuty(),
          baseResult.getCondenserDuty(), baseResult.getTotalAbsoluteDuty(),
          baseResult.getIterationCount(), baseResult.getTemperatureResidual(),
          baseResult.getMassResidual(), baseResult.getEnergyResidual(),
          baseResult.getEvaluatedCases(), baseResult.getConvergedCases(), baseResult.getMessage());
      this.capitalCost = capitalCost;
      this.annualUtilityCost = annualUtilityCost;
      this.annualizedCapitalCost = annualizedCapitalCost;
      this.totalAnnualizedCost = totalAnnualizedCost;
      this.capitalChargeFactor = capitalChargeFactor;
      this.operatingHoursPerYear = operatingHoursPerYear;
      this.steamCostPerTonne = steamCostPerTonne;
      this.coolingWaterCostPerM3 = coolingWaterCostPerM3;
      this.trayEfficiency = trayEfficiency;
      this.actualTrays = actualTrays;
      this.columnDiameter = columnDiameter;
      this.columnHeight = columnHeight;
      this.condenserRefluxRatio = condenserRefluxRatio;
      this.reboilerRatio = reboilerRatio;
    }

    /**
     * Get the installed capital cost estimate.
     *
     * @return installed capital cost in USD
     */
    public double getCapitalCost() {
      return capitalCost;
    }

    /**
     * Get annual utility cost.
     *
     * @return annual utility cost in USD/year
     */
    public double getAnnualUtilityCost() {
      return annualUtilityCost;
    }

    /**
     * Get annualized capital cost.
     *
     * @return annualized capital cost in USD/year
     */
    public double getAnnualizedCapitalCost() {
      return annualizedCapitalCost;
    }

    /**
     * Get total annualized cost.
     *
     * @return annualized capital plus annual utility cost in USD/year
     */
    public double getTotalAnnualizedCost() {
      return totalAnnualizedCost;
    }

    /**
     * Get the capital charge factor.
     *
     * @return capital annualization factor in 1/year
     */
    public double getCapitalChargeFactor() {
      return capitalChargeFactor;
    }

    /**
     * Get the operating hours used for utility costing.
     *
     * @return operating hours per year
     */
    public double getOperatingHoursPerYear() {
      return operatingHoursPerYear;
    }

    /**
     * Get the steam cost assumption.
     *
     * @return steam cost in USD/tonne
     */
    public double getSteamCostPerTonne() {
      return steamCostPerTonne;
    }

    /**
     * Get the cooling-water cost assumption.
     *
     * @return cooling-water cost in USD/m3
     */
    public double getCoolingWaterCostPerM3() {
      return coolingWaterCostPerM3;
    }

    /**
     * Get the tray efficiency used for the mechanical design.
     *
     * @return overall tray efficiency
     */
    public double getTrayEfficiency() {
      return trayEfficiency;
    }

    /**
     * Get the actual tray count after applying tray efficiency.
     *
     * @return actual tray count
     */
    public int getActualTrays() {
      return actualTrays;
    }

    /**
     * Get the mechanically designed column diameter.
     *
     * @return column diameter in m
     */
    public double getColumnDiameter() {
      return columnDiameter;
    }

    /**
     * Get the mechanically designed column height.
     *
     * @return column height in m
     */
    public double getColumnHeight() {
      return columnHeight;
    }

    /**
     * Get the selected condenser reflux ratio.
     *
     * @return selected reflux ratio, or {@link Double#NaN} if not set by the optimization
     */
    public double getCondenserRefluxRatio() {
      return condenserRefluxRatio;
    }

    /**
     * Get the selected reboiler boilup/reflux ratio.
     *
     * @return selected reboiler ratio, or {@link Double#NaN} if not set by the optimization
     */
    public double getReboilerRatio() {
      return reboilerRatio;
    }
  }

  /**
   * Economic metrics calculated for one distillation-column candidate.
   *
   * @author esol
   * @version 1.0
   */
  private static class EconomicTrayOptimizationMetrics implements java.io.Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    private double capitalCost;
    private double annualUtilityCost;
    private double annualizedCapitalCost;
    private double totalAnnualizedCost;
    private int actualTrays;
    private double columnDiameter;
    private double columnHeight;
  }

  /**
   * Snapshot of column settings reused when rebuilding candidates during tray optimization.
   *
   * @author esol
   * @version 1.0
   */
  private static class ColumnOptimizationState {
    private boolean reboilerRefluxSet;
    private double reboilerRefluxRatio = 0.1;
    private boolean reboilerHasSetTemperature;
    private double reboilerTemperature = Double.NaN;
    private double reboilerHeatInput;
    private boolean condenserRefluxSet;
    private double condenserRefluxRatio = 0.1;
    private boolean condenserHasSetTemperature;
    private double condenserTemperature = Double.NaN;
    private double condenserHeatInput;
    private boolean totalCondenser;

    /**
     * Create a copy of the optimization state.
     *
     * @return independent copy with the same settings
     */
    private ColumnOptimizationState copy() {
      ColumnOptimizationState copy = new ColumnOptimizationState();
      copy.reboilerRefluxSet = reboilerRefluxSet;
      copy.reboilerRefluxRatio = reboilerRefluxRatio;
      copy.reboilerHasSetTemperature = reboilerHasSetTemperature;
      copy.reboilerTemperature = reboilerTemperature;
      copy.reboilerHeatInput = reboilerHeatInput;
      copy.condenserRefluxSet = condenserRefluxSet;
      copy.condenserRefluxRatio = condenserRefluxRatio;
      copy.condenserHasSetTemperature = condenserHasSetTemperature;
      copy.condenserTemperature = condenserTemperature;
      copy.condenserHeatInput = condenserHeatInput;
      copy.totalCondenser = totalCondenser;
      return copy;
    }
  }

  /**
   * Find the minimum tray count and best feed tray that meet a product specification.
   *
   * <p>
   * The search evaluates total tray count and feed tray together. It returns the first total tray
   * count that has a converged case meeting the requested purity, then selects the feed tray with
   * the lowest absolute condenser-plus-reboiler duty for that tray count. The selected candidate is
   * applied back to this column and the final solved state is left in the object.
   * </p>
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @return structured optimization result with selected tray count, feed tray, duties and
   *         residuals
   */
  public TrayOptimizationResult findOptimalTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays) {
    long optimizationStartNanos = System.nanoTime();
    ColumnOptimizationState state = captureColumnOptimizationState();
    List<StreamInterface> optimizationFeeds = collectOptimizationFeeds();
    if (optimizationFeeds.isEmpty()) {
      return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct, 0, 0,
          "No feed streams are connected to the column.");
    }

    int minimumTrayCount = getMinimumOptimizationTrayCount();
    if (maxTrays < minimumTrayCount) {
      return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct, 0, 0,
          "Maximum tray count is below the minimum searchable column size.");
    }

    int evaluatedCases = 0;
    int convergedCases = 0;
    for (int totalTrayCount = minimumTrayCount; totalTrayCount <= maxTrays; totalTrayCount++) {
      TrayOptimizationResult bestForTrayCount = null;
      rebuildColumnForOptimization(totalTrayCount, state);
      int firstFeedTray = getFirstFeedTrayCandidate();
      int lastFeedTray = getLastFeedTrayCandidate();

      for (int feedTray = firstFeedTray; feedTray <= lastFeedTray; feedTray++) {
        if (isTrayOptimizationSearchBudgetExceeded(evaluatedCases, optimizationStartNanos)) {
          String budgetMessage =
              createTrayOptimizationBudgetMessage(evaluatedCases, optimizationStartNanos);
          if (bestForTrayCount != null) {
            return applyTrayOptimizationResult(bestForTrayCount, optimizationFeeds, state,
                productSpec, componentName, isTopProduct, evaluatedCases, convergedCases,
                "Selected best candidate found before " + budgetMessage);
          }
          return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct,
              evaluatedCases, convergedCases, budgetMessage);
        }
        evaluatedCases++;
        TrayOptimizationResult candidate = evaluateTrayOptimizationCandidate(totalTrayCount,
            feedTray, productSpec, componentName, isTopProduct, optimizationFeeds, state);
        if (solved()) {
          convergedCases++;
        }
        if (candidate.isFeasible()
            && isBetterTrayOptimizationCandidate(candidate, bestForTrayCount)) {
          bestForTrayCount = candidate;
        }
        if (isTrayOptimizationSearchBudgetExceeded(evaluatedCases, optimizationStartNanos)) {
          String budgetMessage =
              createTrayOptimizationBudgetMessage(evaluatedCases, optimizationStartNanos);
          if (bestForTrayCount != null) {
            return applyTrayOptimizationResult(bestForTrayCount, optimizationFeeds, state,
                productSpec, componentName, isTopProduct, evaluatedCases, convergedCases,
                "Selected best candidate found before " + budgetMessage);
          }
          return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct,
              evaluatedCases, convergedCases, budgetMessage);
        }
      }

      if (bestForTrayCount != null) {
        return applyTrayOptimizationResult(bestForTrayCount, optimizationFeeds, state, productSpec,
            componentName, isTopProduct, evaluatedCases, convergedCases,
            "Selected minimum-tray candidate with lowest duty for that tray count.");
      }
    }

    return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct,
        evaluatedCases, convergedCases,
        "No converged tray/feed-tray candidate met the product spec.");
  }

  /**
   * Find the optimal number of trays to meet a product specification.
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product (distillate), false for bottom
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @return the optimal number of trays, or -1 if the spec could not be met
   */
  public int findOptimalNumberOfTrays(double productSpec, String componentName,
      boolean isTopProduct, int maxTrays) {
    TrayOptimizationResult result =
        findOptimalTrayConfiguration(productSpec, componentName, isTopProduct, maxTrays);
    return result.isFeasible() ? result.getNumberOfTrays() : -1;
  }

  /**
   * Find the tray count and feed tray that minimize annualized column cost.
   *
   * <p>
   * This method searches tray count and feed tray for all converged candidates that meet the
   * product specification, then selects the candidate with the lowest annualized cost. The cost is
   * calculated as annualized installed capital plus reboiler/condenser utility cost using the
   * column mechanical design and cost-estimation correlations. Default assumptions are 15%/year
   * capital charge factor, 8000 operating hours/year, 25 USD/tonne steam, and 0.03 USD/m3 cooling
   * water.
   * </p>
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @return economic optimization result with process, mechanical design, and cost metrics
   */
  public EconomicTrayOptimizationResult findEconomicOptimalTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays) {
    return findEconomicOptimalTrayConfiguration(productSpec, componentName, isTopProduct, maxTrays,
        0.15, 8000.0, 25.0, 0.03, getCurrentMechanicalDesignTrayEfficiency());
  }

  /**
   * Find the annualized-cost optimum for tray count and feed tray using supplied economics.
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @return economic optimization result with process, mechanical design, and cost metrics
   */
  public EconomicTrayOptimizationResult findEconomicOptimalTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays, double capitalChargeFactor,
      double operatingHoursPerYear, double steamCostPerTonne, double coolingWaterCostPerM3,
      double trayEfficiency) {
    return findEconomicOptimalTrayConfiguration(productSpec, componentName, isTopProduct, maxTrays,
        null, null, capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne,
        coolingWaterCostPerM3, trayEfficiency);
  }

  /**
   * Find the annualized-cost optimum for tray count, feed tray, and optional ratio candidates.
   *
   * <p>
   * If reflux or reboiler ratio candidate arrays are supplied, each positive finite ratio is tried
   * for every tray-count/feed-tray case. If an array is {@code null} or empty, the current column
   * specification is preserved for that end of the column.
   * </p>
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product, false for the bottom product
   * @param maxTrays the maximum total tray count to try including reboiler/condenser if present
   * @param condenserRefluxRatios optional condenser reflux-ratio candidates to evaluate
   * @param reboilerRatios optional reboiler boilup/reflux-ratio candidates to evaluate
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @return economic optimization result with process, mechanical design, and cost metrics
   */
  public EconomicTrayOptimizationResult findEconomicOptimalTrayConfiguration(double productSpec,
      String componentName, boolean isTopProduct, int maxTrays, double[] condenserRefluxRatios,
      double[] reboilerRatios, double capitalChargeFactor, double operatingHoursPerYear,
      double steamCostPerTonne, double coolingWaterCostPerM3, double trayEfficiency) {
    long optimizationStartNanos = System.nanoTime();
    ColumnOptimizationState state = captureColumnOptimizationState();
    List<StreamInterface> optimizationFeeds = collectOptimizationFeeds();
    if (optimizationFeeds.isEmpty()) {
      return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName,
          isTopProduct, 0, 0, capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne,
          coolingWaterCostPerM3, trayEfficiency, "No feed streams are connected to the column.");
    }

    int minimumTrayCount = getMinimumOptimizationTrayCount();
    if (maxTrays < minimumTrayCount) {
      return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName,
          isTopProduct, 0, 0, capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne,
          coolingWaterCostPerM3, trayEfficiency,
          "Maximum tray count is below the minimum searchable column size.");
    }

    double[] refluxCandidates = getEconomicRatioCandidates(condenserRefluxRatios);
    double[] reboilerCandidates = getEconomicRatioCandidates(reboilerRatios);
    int evaluatedCases = 0;
    int convergedCases = 0;
    EconomicTrayOptimizationResult bestCandidate = null;

    for (int totalTrayCount = minimumTrayCount; totalTrayCount <= maxTrays; totalTrayCount++) {
      rebuildColumnForOptimization(totalTrayCount, state);
      int firstFeedTray = getFirstFeedTrayCandidate();
      int lastFeedTray = getLastFeedTrayCandidate();

      for (int feedTray = firstFeedTray; feedTray <= lastFeedTray; feedTray++) {
        for (int refluxIndex = 0; refluxIndex < refluxCandidates.length; refluxIndex++) {
          for (int reboilerIndex = 0; reboilerIndex < reboilerCandidates.length; reboilerIndex++) {
            if (isTrayOptimizationSearchBudgetExceeded(evaluatedCases, optimizationStartNanos)) {
              String budgetMessage =
                  createTrayOptimizationBudgetMessage(evaluatedCases, optimizationStartNanos);
              if (bestCandidate != null) {
                return applyEconomicTrayOptimizationResult(bestCandidate, optimizationFeeds, state,
                    productSpec, componentName, isTopProduct, evaluatedCases, convergedCases,
                    capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne,
                    coolingWaterCostPerM3, trayEfficiency,
                    "Selected best economic candidate found before " + budgetMessage);
              }
              return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName,
                  isTopProduct, evaluatedCases, convergedCases, capitalChargeFactor,
                  operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
                  budgetMessage);
            }
            evaluatedCases++;
            EconomicTrayOptimizationResult candidate = evaluateEconomicTrayOptimizationCandidate(
                totalTrayCount, feedTray, productSpec, componentName, isTopProduct,
                optimizationFeeds, state, refluxCandidates[refluxIndex],
                reboilerCandidates[reboilerIndex], capitalChargeFactor, operatingHoursPerYear,
                steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency);
            if (solved()) {
              convergedCases++;
            }
            if (candidate.isFeasible()
                && isBetterEconomicTrayOptimizationCandidate(candidate, bestCandidate)) {
              bestCandidate = candidate;
            }
            if (isTrayOptimizationSearchBudgetExceeded(evaluatedCases, optimizationStartNanos)) {
              String budgetMessage =
                  createTrayOptimizationBudgetMessage(evaluatedCases, optimizationStartNanos);
              if (bestCandidate != null) {
                return applyEconomicTrayOptimizationResult(bestCandidate, optimizationFeeds, state,
                    productSpec, componentName, isTopProduct, evaluatedCases, convergedCases,
                    capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne,
                    coolingWaterCostPerM3, trayEfficiency,
                    "Selected best economic candidate found before " + budgetMessage);
              }
              return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName,
                  isTopProduct, evaluatedCases, convergedCases, capitalChargeFactor,
                  operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
                  budgetMessage);
            }
          }
        }
      }
    }

    if (bestCandidate != null) {
      return applyEconomicTrayOptimizationResult(bestCandidate, optimizationFeeds, state,
          productSpec, componentName, isTopProduct, evaluatedCases, convergedCases,
          capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3,
          trayEfficiency, "Selected annualized-cost optimum candidate.");
    }

    return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName, isTopProduct,
        evaluatedCases, convergedCases, capitalChargeFactor, operatingHoursPerYear,
        steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
        "No converged economic tray/feed-tray candidate met the product spec.");
  }

  /**
   * Initialize the rigorous column from Fenske-Underwood-Gilliland shortcut estimates.
   *
   * <p>
   * The method runs {@link ShortcutDistillationColumn}, converts its stage and feed-tray estimates
   * into this column's bottom-up tray indexing, rebuilds the tray stack, adds the feed at the
   * shortcut-estimated feed tray, applies condenser reflux/duty and reboiler duty estimates, and
   * stores light-key/heavy-key recovery specifications for later rigorous solving.
   * </p>
   *
   * @param feedStream feed stream used for the shortcut calculation and rigorous column
   * @param lightKey light-key component name
   * @param heavyKey heavy-key component name
   * @param lightKeyRecoveryDistillate light-key recovery to top product, 0 to 1
   * @param heavyKeyRecoveryBottoms heavy-key recovery to bottom product, 0 to 1
   * @param refluxRatioMultiplier actual reflux divided by minimum reflux, normally greater than 1
   * @return shortcut initialization result with applied rigorous-column settings
   */
  public ShortcutInitializationResult initializeFromShortcut(StreamInterface feedStream,
      String lightKey, String heavyKey, double lightKeyRecoveryDistillate,
      double heavyKeyRecoveryBottoms, double refluxRatioMultiplier) {
    if (feedStream == null) {
      lastShortcutInitializationResult = createFailedShortcutInitialization(lightKey, heavyKey,
          "No feed stream was supplied for shortcut initialization.");
      return lastShortcutInitializationResult;
    }
    ShortcutDistillationColumn shortcut =
        new ShortcutDistillationColumn(getName() + " shortcut", feedStream);
    shortcut.setLightKey(lightKey);
    shortcut.setHeavyKey(heavyKey);
    shortcut.setLightKeyRecoveryDistillate(lightKeyRecoveryDistillate);
    shortcut.setHeavyKeyRecoveryBottoms(heavyKeyRecoveryBottoms);
    shortcut.setRefluxRatioMultiplier(refluxRatioMultiplier);
    applyShortcutPressureBasis(shortcut, feedStream);

    try {
      shortcut.run(UUID.randomUUID());
    } catch (Exception exception) {
      logger.warn("Shortcut initialization failed for column {}", getName(), exception);
      lastShortcutInitializationResult = createFailedShortcutInitialization(lightKey, heavyKey,
          "Shortcut calculation failed: " + exception.getMessage());
      return lastShortcutInitializationResult;
    }

    if (!shortcut.isSolved()) {
      lastShortcutInitializationResult = createFailedShortcutInitialization(lightKey, heavyKey,
          "Shortcut calculation did not solve. Check key-component order and recoveries.");
      return lastShortcutInitializationResult;
    }

    int totalStageCount = getShortcutTotalStageCount(shortcut);
    int feedTrayNumber =
        convertShortcutFeedTrayFromTop(shortcut.getFeedTrayNumber(), totalStageCount);
    ColumnOptimizationState state = captureColumnOptimizationState();
    applyShortcutEndpointDuties(state, shortcut);
    rebuildColumnForOptimization(totalStageCount, state);
    addFeedStream(feedStream, feedTrayNumber);
    setTopComponentRecovery(lightKey, lightKeyRecoveryDistillate);
    setBottomComponentRecovery(heavyKey, heavyKeyRecoveryBottoms);

    lastShortcutInitializationResult = new ShortcutInitializationResult(true, totalStageCount,
        feedTrayNumber, shortcut.getFeedTrayNumber(), shortcut.getMinimumNumberOfStages(),
        shortcut.getMinimumRefluxRatio(), shortcut.getActualNumberOfStages(),
        shortcut.getActualRefluxRatio(), shortcut.getCondenserDuty(), shortcut.getReboilerDuty(),
        lightKey, heavyKey, "Shortcut estimates applied to rigorous column.");
    return lastShortcutInitializationResult;
  }

  /**
   * Get the latest shortcut initialization result.
   *
   * @return latest shortcut initialization result, or {@code null} if none has been applied
   */
  public ShortcutInitializationResult getLastShortcutInitializationResult() {
    return lastShortcutInitializationResult;
  }

  /**
   * Screen the current column setup before automatic solver candidate probing.
   *
   * <p>
   * This method reuses the normal setup validator and adds commercial-style active-bound warnings
   * for specifications that are mathematically valid but likely to make solver continuation or
   * outer tear-variable convergence difficult.
   * </p>
   *
   * @return validation result with errors and active-bound warnings for the automatic solver
   */
  public ValidationResult screenSpecificationFeasibility() {
    ValidationResult result = validateSetup();
    validateCommercialActiveBounds(result);
    return result;
  }

  /**
   * Attempt an automatic shortcut-column seed for the AUTO solver pipeline.
   *
   * @param summary automatic solver summary receiving initialization diagnostics
   * @return {@code true} if a shortcut seed was successfully applied
   */
  boolean tryAutomaticShortcutInitialization(StringBuilder summary) {
    StreamInterface feedStream = getPrimaryExternalFeedStream();
    if (feedStream == null || feedStream.getThermoSystem() == null) {
      return recordInitializationAttempt(summary, "shortcut initialization", false,
          "skipped because the column has no external feed stream");
    }

    String[] keys = selectAutomaticShortcutKeys(feedStream.getThermoSystem());
    if (keys == null) {
      return recordInitializationAttempt(summary, "shortcut initialization", false,
          "skipped because fewer than two non-water feed components were available");
    }

    ShortcutInitializationResult result =
        initializeFromShortcut(feedStream, keys[0], keys[1], 0.95, 0.95, 1.4);
    String message = result.getMessage() + " lightKey=" + keys[0] + " heavyKey=" + keys[1];
    return recordInitializationAttempt(summary, "shortcut initialization", result.isInitialized(),
        message);
  }

  /**
   * Attempt a thermodynamic temperature-profile seed for the AUTO solver pipeline.
   *
   * @param summary automatic solver summary receiving initialization diagnostics
   * @return {@code true} if a tray temperature profile was successfully seeded
   */
  boolean tryThermodynamicProfileInitialization(StringBuilder summary) {
    StreamInterface feedStream = getPrimaryExternalFeedStream();
    if (feedStream == null || feedStream.getThermoSystem() == null) {
      return recordInitializationAttempt(summary, "thermodynamic profile initialization", false,
          "skipped because the column has no external feed stream");
    }
    double feedTemperature = feedStream.getThermoSystem().getTemperature();
    if (!Double.isFinite(feedTemperature) || feedTemperature <= 0.0) {
      return recordInitializationAttempt(summary, "thermodynamic profile initialization", false,
          "skipped because the feed temperature is not finite and positive");
    }

    double topTemperature = Math.max(150.0, feedTemperature - 20.0);
    double bottomTemperature = Math.max(topTemperature + 1.0, feedTemperature + 20.0);
    seedTrayTemperatureProfile(topTemperature, bottomTemperature);
    setDoInitializion(true);
    return recordInitializationAttempt(summary, "thermodynamic profile initialization", true,
        "seeded tray temperatures from " + topTemperature + " K to " + bottomTemperature + " K");
  }

  /**
   * Select the first and last non-water feed components as shortcut light and heavy keys.
   *
   * @param system feed thermodynamic system
   * @return two-element array containing light key and heavy key, or {@code null} if unavailable
   */
  private String[] selectAutomaticShortcutKeys(SystemInterface system) {
    String lightKey = null;
    String heavyKey = null;
    for (int componentIndex = 0; componentIndex < system
        .getNumberOfComponents(); componentIndex++) {
      String componentName = system.getPhase(0).getComponent(componentIndex).getComponentName();
      if ("water".equalsIgnoreCase(componentName)) {
        continue;
      }
      if (lightKey == null) {
        lightKey = componentName;
      }
      heavyKey = componentName;
    }
    if (lightKey == null || heavyKey == null || lightKey.equalsIgnoreCase(heavyKey)) {
      return null;
    }
    return new String[] {lightKey, heavyKey};
  }

  /**
   * Get the first configured external feed stream.
   *
   * @return primary feed stream, or {@code null} when the column has no external feed
   */
  private StreamInterface getPrimaryExternalFeedStream() {
    List<StreamInterface> externalFeeds = getAllExternalFeedStreams();
    return externalFeeds.isEmpty() ? null : externalFeeds.get(0);
  }

  /**
   * Record an AUTO initialization attempt in both report fields and the solver summary.
   *
   * @param summary automatic solver summary, possibly {@code null}
   * @param label human-readable initialization label
   * @param applied {@code true} if the initialization changed the candidate column state
   * @param message detailed diagnostic message
   * @return {@code applied}
   */
  private boolean recordInitializationAttempt(StringBuilder summary, String label, boolean applied,
      String message) {
    String report = label + " " + (applied ? "applied" : "skipped") + ": " + message;
    setLastInitializationReport(report);
    recordAutoSolverEvent(report);
    if (summary != null) {
      summary.append("AUTO ").append(report).append('\n');
    }
    return applied;
  }

  /**
   * Create an unsuccessful shortcut initialization result.
   *
   * @param lightKey light-key component name
   * @param heavyKey heavy-key component name
   * @param message diagnostic message
   * @return failed initialization result
   */
  private ShortcutInitializationResult createFailedShortcutInitialization(String lightKey,
      String heavyKey, String message) {
    return new ShortcutInitializationResult(false, -1, -1, -1, Double.NaN, Double.NaN, Double.NaN,
        Double.NaN, Double.NaN, Double.NaN, lightKey, heavyKey, message);
  }

  /**
   * Apply pressure defaults to a shortcut calculation and this rigorous column.
   *
   * @param shortcut shortcut column to configure
   * @param feedStream feed stream providing pressure if no endpoint pressure is already set
   */
  private void applyShortcutPressureBasis(ShortcutDistillationColumn shortcut,
      StreamInterface feedStream) {
    double feedPressure =
        feedStream.getFluid() == null ? Double.NaN : feedStream.getPressure("bara");
    double condenserPressure = isPositiveFinite(topTrayPressure) ? topTrayPressure : feedPressure;
    double reboilerPressure =
        isPositiveFinite(bottomTrayPressure) ? bottomTrayPressure : condenserPressure;
    if (isPositiveFinite(condenserPressure)) {
      shortcut.setCondenserPressure(condenserPressure);
      if (!isPositiveFinite(topTrayPressure)) {
        setTopPressure(condenserPressure);
      }
    }
    if (isPositiveFinite(reboilerPressure)) {
      shortcut.setReboilerPressure(reboilerPressure);
      if (!isPositiveFinite(bottomTrayPressure)) {
        setBottomPressure(reboilerPressure);
      }
    }
  }

  /**
   * Calculate a rigorous total stage count from shortcut stage estimates.
   *
   * @param shortcut solved shortcut column
   * @return rigorous total stage count including condenser/reboiler if present
   */
  private int getShortcutTotalStageCount(ShortcutDistillationColumn shortcut) {
    int totalStageCount = (int) Math.ceil(shortcut.getActualNumberOfStages());
    totalStageCount = Math.max(totalStageCount, getMinimumOptimizationTrayCount());
    return totalStageCount;
  }

  /**
   * Convert a shortcut feed tray counted from the top to this column's bottom-up tray index.
   *
   * @param feedTrayFromTop shortcut feed tray count from the top product end
   * @param totalStageCount total rigorous stage count
   * @return bottom-up tray index clamped to feasible feed trays
   */
  private int convertShortcutFeedTrayFromTop(int feedTrayFromTop, int totalStageCount) {
    int bottomUpTrayNumber = totalStageCount - Math.max(1, feedTrayFromTop);
    int firstFeedTray = hasReboiler ? 1 : 0;
    int lastFeedTray = totalStageCount - (hasCondenser ? 2 : 1);
    if (lastFeedTray < firstFeedTray) {
      lastFeedTray = firstFeedTray;
    }
    return Math.max(firstFeedTray, Math.min(lastFeedTray, bottomUpTrayNumber));
  }

  /**
   * Apply shortcut endpoint duties and reflux to a rebuild state.
   *
   * @param state column optimization state to update before rebuilding trays
   * @param shortcut solved shortcut column
   */
  private void applyShortcutEndpointDuties(ColumnOptimizationState state,
      ShortcutDistillationColumn shortcut) {
    if (hasCondenser) {
      state.condenserRefluxSet = true;
      state.condenserRefluxRatio = Math.max(0.0, shortcut.getActualRefluxRatio());
      state.condenserHeatInput = shortcut.getCondenserDuty();
    }
    if (hasReboiler) {
      state.reboilerHeatInput = shortcut.getReboilerDuty();
    }
  }

  /**
   * Capture the current column settings needed to rebuild optimization candidates.
   *
   * @return column settings snapshot
   */
  private ColumnOptimizationState captureColumnOptimizationState() {
    ColumnOptimizationState state = new ColumnOptimizationState();
    if (hasReboiler && getReboiler() != null) {
      Reboiler reboiler = getReboiler();
      state.reboilerRefluxSet = reboiler.refluxIsSet;
      state.reboilerRefluxRatio = reboiler.getRefluxRatio();
      state.reboilerHasSetTemperature = reboiler.isSetOutTemperature();
      if (state.reboilerHasSetTemperature) {
        state.reboilerTemperature = reboiler.getOutTemperature();
      }
      state.reboilerHeatInput = reboiler.heatInput;
    }
    if (hasCondenser && getCondenser() != null) {
      Condenser condenser = getCondenser();
      state.condenserRefluxSet = condenser.refluxIsSet;
      state.condenserRefluxRatio = condenser.getRefluxRatio();
      state.condenserHasSetTemperature = condenser.isSetOutTemperature();
      if (state.condenserHasSetTemperature) {
        state.condenserTemperature = condenser.getOutTemperature();
      }
      state.condenserHeatInput = condenser.heatInput;
      state.totalCondenser = condenser.totalCondenser;
    }
    return state;
  }

  /**
   * Collect all feeds already assigned or queued for automatic placement.
   *
   * @return list of feed streams used in the optimization search
   */
  private List<StreamInterface> collectOptimizationFeeds() {
    List<StreamInterface> optimizationFeeds = new ArrayList<>(unassignedFeedStreams);
    for (List<StreamInterface> feeds : feedStreams.values()) {
      optimizationFeeds.addAll(feeds);
    }
    return optimizationFeeds;
  }

  /**
   * Get the minimum total tray count used by the rigorous tray search.
   *
   * @return minimum total tray count including reboiler/condenser if present
   */
  private int getMinimumOptimizationTrayCount() {
    int minimumTrayCount = 2;
    if (hasReboiler) {
      minimumTrayCount++;
    }
    if (hasCondenser) {
      minimumTrayCount++;
    }
    return minimumTrayCount;
  }

  /**
   * Check whether tray optimization should stop because a configured search budget is exhausted.
   *
   * @param evaluatedCases number of candidate cases already evaluated
   * @param optimizationStartNanos value from {@link System#nanoTime()} at search start
   * @return {@code true} when candidate-count or elapsed-time budget has been reached
   */
  private boolean isTrayOptimizationSearchBudgetExceeded(int evaluatedCases,
      long optimizationStartNanos) {
    return evaluatedCases >= maxTrayOptimizationCandidates || getTrayOptimizationElapsedSeconds(
        optimizationStartNanos) >= maxTrayOptimizationTimeSeconds;
  }

  /**
   * Calculate tray optimization elapsed time in seconds.
   *
   * @param optimizationStartNanos value from {@link System#nanoTime()} at search start
   * @return elapsed time in seconds
   */
  private double getTrayOptimizationElapsedSeconds(long optimizationStartNanos) {
    return (System.nanoTime() - optimizationStartNanos) * 1.0e-9;
  }

  /**
   * Create a diagnostic message for a budget-limited optimization search.
   *
   * @param evaluatedCases number of candidate cases already evaluated
   * @param optimizationStartNanos value from {@link System#nanoTime()} at search start
   * @return diagnostic message explaining the active budget limits
   */
  private String createTrayOptimizationBudgetMessage(int evaluatedCases,
      long optimizationStartNanos) {
    double elapsedSeconds =
        Math.round(getTrayOptimizationElapsedSeconds(optimizationStartNanos) * 10.0) / 10.0;
    return "Tray optimization stopped after evaluating " + evaluatedCases + " candidate cases in "
        + elapsedSeconds + " s due to the configured search budget. Increase "
        + "max tray optimization candidates or time for larger studies.";
  }

  /**
   * Run the current tray optimization candidate, retrying with damped substitution when the
   * configured solver leaves the candidate unconverged.
   *
   * <p>
   * Tray optimization is a configuration search. A candidate should not be discarded solely because
   * the default direct-substitution path stalls from a cold start when the more robust damped
   * substitution solver can solve the same thermodynamic and hydraulic setup. The retry is scoped
   * to the current candidate and restores the caller's configured solver type before returning.
   * </p>
   *
   * @return {@code true} if the candidate is solved after the configured solver or damped fallback
   */
  private boolean runTrayOptimizationCandidateWithFallback() {
    try {
      run();
    } catch (Exception exception) {
      logger.debug("Tray optimization candidate failed with configured solver {}.", solverType,
          exception);
      return false;
    }

    if (solved() || solverType == SolverType.DAMPED_SUBSTITUTION) {
      return solved();
    }

    SolverType configuredSolverType = solverType;
    try {
      solverType = SolverType.DAMPED_SUBSTITUTION;
      setDoInitializion(true);
      run(UUID.randomUUID());
      return solved();
    } catch (Exception exception) {
      logger.debug("Tray optimization damped fallback failed for solver {}.", configuredSolverType,
          exception);
      return false;
    } finally {
      solverType = configuredSolverType;
    }
  }

  /**
   * Evaluate one tray-count/feed-tray candidate.
   *
   * @param totalTrayCount total tray count for the candidate
   * @param feedTray 0-based feed tray used for all optimization feeds
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param optimizationFeeds feed streams to connect to the candidate
   * @param state captured column settings to apply during rebuild
   * @return candidate result, feasible only when the column converged and met the purity spec
   */
  private TrayOptimizationResult evaluateTrayOptimizationCandidate(int totalTrayCount, int feedTray,
      double productSpec, String componentName, boolean isTopProduct,
      List<StreamInterface> optimizationFeeds, ColumnOptimizationState state) {
    rebuildColumnForOptimization(totalTrayCount, state);
    addOptimizationFeedsToTray(optimizationFeeds, feedTray);
    if (!runTrayOptimizationCandidateWithFallback()) {
      return createTrayOptimizationResult(false, totalTrayCount, feedTray, productSpec,
          componentName, isTopProduct, Double.NaN, 0, 0, "Candidate did not converge.");
    }

    double productPurity = getProductComponentMoleFraction(componentName, isTopProduct);
    boolean feasible = productPurity >= productSpec;
    return createTrayOptimizationResult(feasible, totalTrayCount, feedTray, productSpec,
        componentName, isTopProduct, productPurity, 0, 0,
        feasible ? "Candidate met product specification." : "Candidate purity below target.");
  }

  /**
   * Evaluate one annualized-cost optimization candidate.
   *
   * @param totalTrayCount total tray count for the candidate
   * @param feedTray 0-based feed tray used for all optimization feeds
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param optimizationFeeds feed streams to connect to the candidate
   * @param baseState captured column settings to apply during rebuild
   * @param condenserRefluxRatio condenser reflux-ratio candidate, or {@link Double#NaN}
   * @param reboilerRatio reboiler boilup/reflux-ratio candidate, or {@link Double#NaN}
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @return economic candidate result, feasible only when converged and meeting the purity spec
   */
  private EconomicTrayOptimizationResult evaluateEconomicTrayOptimizationCandidate(
      int totalTrayCount, int feedTray, double productSpec, String componentName,
      boolean isTopProduct, List<StreamInterface> optimizationFeeds,
      ColumnOptimizationState baseState, double condenserRefluxRatio, double reboilerRatio,
      double capitalChargeFactor, double operatingHoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3, double trayEfficiency) {
    ColumnOptimizationState candidateState = baseState.copy();
    applyEconomicRatioOverrides(candidateState, condenserRefluxRatio, reboilerRatio);
    TrayOptimizationResult trayResult = evaluateTrayOptimizationCandidate(totalTrayCount, feedTray,
        productSpec, componentName, isTopProduct, optimizationFeeds, candidateState);
    if (!trayResult.isFeasible()) {
      return createEconomicTrayOptimizationResult(trayResult,
          createEmptyEconomicTrayOptimizationMetrics(), capitalChargeFactor, operatingHoursPerYear,
          steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
          getSelectedCondenserRatio(candidateState), getSelectedReboilerRatio(candidateState));
    }
    EconomicTrayOptimizationMetrics metrics =
        calculateEconomicTrayOptimizationMetrics(capitalChargeFactor, operatingHoursPerYear,
            steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency);
    return createEconomicTrayOptimizationResult(trayResult, metrics, capitalChargeFactor,
        operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
        getSelectedCondenserRatio(candidateState), getSelectedReboilerRatio(candidateState));
  }

  /**
   * Apply selected annualized-cost result to the live column and return final diagnostics.
   *
   * @param selectedResult selected economic candidate from the search
   * @param optimizationFeeds feed streams to connect to the selected tray
   * @param state captured column settings to apply during rebuild
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param evaluatedCases number of evaluated candidate cases
   * @param convergedCases number of converged candidate cases
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @param message diagnostic message to store in the returned result
   * @return final economic optimization result from the applied selected candidate
   */
  private EconomicTrayOptimizationResult applyEconomicTrayOptimizationResult(
      EconomicTrayOptimizationResult selectedResult, List<StreamInterface> optimizationFeeds,
      ColumnOptimizationState state, double productSpec, String componentName, boolean isTopProduct,
      int evaluatedCases, int convergedCases, double capitalChargeFactor,
      double operatingHoursPerYear, double steamCostPerTonne, double coolingWaterCostPerM3,
      double trayEfficiency, String message) {
    ColumnOptimizationState selectedState = state.copy();
    applyEconomicRatioOverrides(selectedState, selectedResult.getCondenserRefluxRatio(),
        selectedResult.getReboilerRatio());
    rebuildColumnForOptimization(selectedResult.getNumberOfTrays(), selectedState);
    addOptimizationFeedsToTray(optimizationFeeds, selectedResult.getFeedTrayNumber());
    if (!runTrayOptimizationCandidateWithFallback()) {
      return createInfeasibleEconomicTrayOptimizationResult(productSpec, componentName,
          isTopProduct, evaluatedCases, convergedCases, capitalChargeFactor, operatingHoursPerYear,
          steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
          "Selected economic candidate did not converge when reapplied.");
    }

    double productPurity = getProductComponentMoleFraction(componentName, isTopProduct);
    TrayOptimizationResult trayResult = createTrayOptimizationResult(productPurity >= productSpec,
        selectedResult.getNumberOfTrays(), selectedResult.getFeedTrayNumber(), productSpec,
        componentName, isTopProduct, productPurity, evaluatedCases, convergedCases, message);
    EconomicTrayOptimizationMetrics metrics =
        calculateEconomicTrayOptimizationMetrics(capitalChargeFactor, operatingHoursPerYear,
            steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency);
    return createEconomicTrayOptimizationResult(trayResult, metrics, capitalChargeFactor,
        operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency,
        selectedResult.getCondenserRefluxRatio(), selectedResult.getReboilerRatio());
  }

  /**
   * Apply optional reflux/boilup ratio overrides to an optimization state.
   *
   * @param state state to modify
   * @param condenserRefluxRatio condenser reflux-ratio candidate, or {@link Double#NaN}
   * @param reboilerRatio reboiler boilup/reflux-ratio candidate, or {@link Double#NaN}
   */
  private void applyEconomicRatioOverrides(ColumnOptimizationState state,
      double condenserRefluxRatio, double reboilerRatio) {
    if (hasCondenser && isPositiveFinite(condenserRefluxRatio)) {
      state.condenserRefluxSet = true;
      state.condenserRefluxRatio = condenserRefluxRatio;
    }
    if (hasReboiler && isPositiveFinite(reboilerRatio)) {
      state.reboilerRefluxSet = true;
      state.reboilerRefluxRatio = reboilerRatio;
    }
  }

  /**
   * Get sanitized economic ratio candidates.
   *
   * @param ratios candidate ratios supplied by the caller
   * @return positive finite ratios, or one {@link Double#NaN} entry to preserve current settings
   */
  private double[] getEconomicRatioCandidates(double[] ratios) {
    if (ratios == null || ratios.length == 0) {
      return new double[] {Double.NaN};
    }
    double[] sanitized = new double[ratios.length];
    int count = 0;
    for (int ratioIndex = 0; ratioIndex < ratios.length; ratioIndex++) {
      if (isPositiveFinite(ratios[ratioIndex])) {
        sanitized[count] = ratios[ratioIndex];
        count++;
      }
    }
    if (count == 0) {
      return new double[] {Double.NaN};
    }
    double[] result = new double[count];
    System.arraycopy(sanitized, 0, result, 0, count);
    return result;
  }

  /**
   * Check whether a value is finite and positive.
   *
   * @param value value to check
   * @return {@code true} when the value is finite and greater than zero
   */
  private boolean isPositiveFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
  }

  /**
   * Calculate mechanical design and cost metrics for the current solved candidate.
   *
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @return populated economic metrics for the current column state
   */
  private EconomicTrayOptimizationMetrics calculateEconomicTrayOptimizationMetrics(
      double capitalChargeFactor, double operatingHoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3, double trayEfficiency) {
    EconomicTrayOptimizationMetrics metrics = new EconomicTrayOptimizationMetrics();
    DistillationColumnMechanicalDesign design = new DistillationColumnMechanicalDesign(this);
    design.setTrayEfficiency(trayEfficiency);
    double designPressure = getEconomicDesignPressure();
    if (isPositiveFinite(designPressure)) {
      design.setMaxOperationPressure(designPressure);
    }
    design.calcDesign();
    design.calculateWeights();

    ColumnCostEstimate costEstimate = new ColumnCostEstimate(design);
    costEstimate.setColumnType("trayed");
    costEstimate.setTrayType(design.getTrayType());
    costEstimate.setColumnDiameter(design.getColumnDiameter());
    costEstimate.setColumnHeight(design.getColumnHeight());
    costEstimate.setNumberOfTrays(design.getActualTrays());
    costEstimate.setDesignPressure(design.getMaxDesignPressure());
    costEstimate.setIncludeReboiler(hasReboiler);
    costEstimate.setIncludeCondenser(hasCondenser);
    costEstimate.setReboilerDuty(Math.abs(design.getReboilerDuty()));
    costEstimate.setCondenserDuty(Math.abs(design.getCondenserDuty()));
    costEstimate.calculateCostEstimate();

    metrics.capitalCost = costEstimate.getTotalModuleCost();
    if (!isPositiveFinite(metrics.capitalCost)) {
      metrics.capitalCost = design.calculateTotalSystemCost();
    }
    metrics.annualUtilityCost = costEstimate.calcAnnualUtilityCost(operatingHoursPerYear,
        steamCostPerTonne, coolingWaterCostPerM3);
    metrics.annualizedCapitalCost = metrics.capitalCost * capitalChargeFactor;
    metrics.totalAnnualizedCost = metrics.annualizedCapitalCost + metrics.annualUtilityCost;
    metrics.actualTrays = design.getActualTrays();
    metrics.columnDiameter = design.getColumnDiameter();
    metrics.columnHeight = design.getColumnHeight();
    return metrics;
  }

  /**
   * Create empty economic metrics for infeasible candidates.
   *
   * @return economic metrics with not-a-number cost and design fields
   */
  private EconomicTrayOptimizationMetrics createEmptyEconomicTrayOptimizationMetrics() {
    EconomicTrayOptimizationMetrics metrics = new EconomicTrayOptimizationMetrics();
    metrics.capitalCost = Double.NaN;
    metrics.annualUtilityCost = Double.NaN;
    metrics.annualizedCapitalCost = Double.NaN;
    metrics.totalAnnualizedCost = Double.NaN;
    metrics.actualTrays = -1;
    metrics.columnDiameter = Double.NaN;
    metrics.columnHeight = Double.NaN;
    return metrics;
  }

  /**
   * Create an economic optimization result.
   *
   * @param trayResult rigorous tray optimization result
   * @param metrics economic metrics from the current candidate
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @param condenserRefluxRatio selected condenser reflux ratio, or {@link Double#NaN}
   * @param reboilerRatio selected reboiler boilup/reflux ratio, or {@link Double#NaN}
   * @return economic optimization result
   */
  private EconomicTrayOptimizationResult createEconomicTrayOptimizationResult(
      TrayOptimizationResult trayResult, EconomicTrayOptimizationMetrics metrics,
      double capitalChargeFactor, double operatingHoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3, double trayEfficiency, double condenserRefluxRatio,
      double reboilerRatio) {
    return new EconomicTrayOptimizationResult(trayResult, metrics.capitalCost,
        metrics.annualUtilityCost, metrics.annualizedCapitalCost, metrics.totalAnnualizedCost,
        capitalChargeFactor, operatingHoursPerYear, steamCostPerTonne, coolingWaterCostPerM3,
        trayEfficiency, metrics.actualTrays, metrics.columnDiameter, metrics.columnHeight,
        condenserRefluxRatio, reboilerRatio);
  }

  /**
   * Create an infeasible economic optimization result.
   *
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param evaluatedCases number of evaluated candidate cases
   * @param convergedCases number of converged candidate cases
   * @param capitalChargeFactor annual capital charge factor in 1/year
   * @param operatingHoursPerYear operating hours per year for utility costing
   * @param steamCostPerTonne steam cost in USD/tonne for reboiler duty
   * @param coolingWaterCostPerM3 cooling-water cost in USD/m3 for condenser duty
   * @param trayEfficiency overall tray efficiency used for actual tray count and column height
   * @param message diagnostic message
   * @return infeasible economic optimization result
   */
  private EconomicTrayOptimizationResult createInfeasibleEconomicTrayOptimizationResult(
      double productSpec, String componentName, boolean isTopProduct, int evaluatedCases,
      int convergedCases, double capitalChargeFactor, double operatingHoursPerYear,
      double steamCostPerTonne, double coolingWaterCostPerM3, double trayEfficiency,
      String message) {
    TrayOptimizationResult trayResult = createInfeasibleTrayOptimizationResult(productSpec,
        componentName, isTopProduct, evaluatedCases, convergedCases, message);
    return createEconomicTrayOptimizationResult(trayResult,
        createEmptyEconomicTrayOptimizationMetrics(), capitalChargeFactor, operatingHoursPerYear,
        steamCostPerTonne, coolingWaterCostPerM3, trayEfficiency, Double.NaN, Double.NaN);
  }

  /**
   * Compare two economic candidates.
   *
   * @param candidate candidate result to evaluate
   * @param currentBest current best result, or {@code null}
   * @return {@code true} if the candidate has a lower annualized cost or better tie-breaker
   */
  private boolean isBetterEconomicTrayOptimizationCandidate(
      EconomicTrayOptimizationResult candidate, EconomicTrayOptimizationResult currentBest) {
    if (currentBest == null) {
      return true;
    }
    double costDifference =
        candidate.getTotalAnnualizedCost() - currentBest.getTotalAnnualizedCost();
    if (Math.abs(costDifference) > 1.0e-6) {
      return costDifference < 0.0;
    }
    if (candidate.getNumberOfTrays() != currentBest.getNumberOfTrays()) {
      return candidate.getNumberOfTrays() < currentBest.getNumberOfTrays();
    }
    return isBetterTrayOptimizationCandidate(candidate, currentBest);
  }

  /**
   * Get the tray efficiency currently configured on the mechanical design.
   *
   * @return tray efficiency from the column mechanical design, or the default value
   */
  private double getCurrentMechanicalDesignTrayEfficiency() {
    if (mechanicalDesign instanceof DistillationColumnMechanicalDesign) {
      return ((DistillationColumnMechanicalDesign) mechanicalDesign).getTrayEfficiency();
    }
    return 0.65;
  }

  /**
   * Estimate the pressure basis for economic mechanical design.
   *
   * @return maximum configured tray endpoint pressure in bara, or {@link Double#NaN}
   */
  private double getEconomicDesignPressure() {
    if (isPositiveFinite(topTrayPressure) && isPositiveFinite(bottomTrayPressure)) {
      return Math.max(topTrayPressure, bottomTrayPressure);
    }
    if (isPositiveFinite(bottomTrayPressure)) {
      return bottomTrayPressure;
    }
    if (isPositiveFinite(topTrayPressure)) {
      return topTrayPressure;
    }
    return Double.NaN;
  }

  /**
   * Get the reflux ratio recorded as selected by an economic candidate.
   *
   * @param state optimization state to inspect
   * @return selected condenser reflux ratio, or {@link Double#NaN} if no ratio is set
   */
  private double getSelectedCondenserRatio(ColumnOptimizationState state) {
    return state.condenserRefluxSet ? state.condenserRefluxRatio : Double.NaN;
  }

  /**
   * Get the reboiler ratio recorded as selected by an economic candidate.
   *
   * @param state optimization state to inspect
   * @return selected reboiler boilup/reflux ratio, or {@link Double#NaN} if no ratio is set
   */
  private double getSelectedReboilerRatio(ColumnOptimizationState state) {
    return state.reboilerRefluxSet ? state.reboilerRefluxRatio : Double.NaN;
  }

  /**
   * Apply the selected candidate to the live column and return final diagnostics.
   *
   * @param selectedResult selected candidate from the search
   * @param optimizationFeeds feed streams to connect to the selected tray
   * @param state captured column settings to apply during rebuild
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param evaluatedCases number of evaluated candidate cases
   * @param convergedCases number of converged candidate cases
   * @param message diagnostic message to store in the returned result
   * @return final optimization result from the applied selected candidate
   */
  private TrayOptimizationResult applyTrayOptimizationResult(TrayOptimizationResult selectedResult,
      List<StreamInterface> optimizationFeeds, ColumnOptimizationState state, double productSpec,
      String componentName, boolean isTopProduct, int evaluatedCases, int convergedCases,
      String message) {
    rebuildColumnForOptimization(selectedResult.getNumberOfTrays(), state);
    addOptimizationFeedsToTray(optimizationFeeds, selectedResult.getFeedTrayNumber());
    if (!runTrayOptimizationCandidateWithFallback()) {
      return createInfeasibleTrayOptimizationResult(productSpec, componentName, isTopProduct,
          evaluatedCases, convergedCases, "Selected candidate did not converge when reapplied.");
    }

    double productPurity = getProductComponentMoleFraction(componentName, isTopProduct);
    return createTrayOptimizationResult(productPurity >= productSpec,
        selectedResult.getNumberOfTrays(), selectedResult.getFeedTrayNumber(), productSpec,
        componentName, isTopProduct, productPurity, evaluatedCases, convergedCases, message);
  }

  /**
   * Rebuild the column internals for an optimization candidate.
   *
   * @param totalTrayCount total tray count including reboiler/condenser if present
   * @param state captured column settings to apply to the rebuilt trays
   */
  private void rebuildColumnForOptimization(int totalTrayCount, ColumnOptimizationState state) {
    trays.clear();
    distoperations = new neqsim.process.processmodel.ProcessSystem();
    feedStreams.clear();
    unassignedFeedStreams.clear();
    feedmixer = new Mixer("temp mixer");
    feedmixer.setMultiPhaseCheck(doMultiPhaseCheck);
    hasBeenSolvedBefore = false;
    lastTotalFeedFlow = -1.0;
    lastMeshResidual = null;
    terminalGasProductDrawStream = null;
    terminalLiquidProductDrawStream = null;
    err = 1.0e10;
    resetLastSolveMetrics();

    if (hasReboiler) {
      Reboiler reboiler = new Reboiler("Reboiler");
      reboiler.setMultiPhaseCheck(doMultiPhaseCheck);
      reboiler.setHeatInput(state.reboilerHeatInput);
      if (state.reboilerRefluxSet) {
        reboiler.setRefluxRatio(state.reboilerRefluxRatio);
      }
      if (state.reboilerHasSetTemperature) {
        reboiler.setOutTemperature(state.reboilerTemperature);
      }
      trays.add(reboiler);
    }

    int middleTrayCount = totalTrayCount - (hasReboiler ? 1 : 0) - (hasCondenser ? 1 : 0);
    for (int trayIndex = 0; trayIndex < middleTrayCount; trayIndex++) {
      SimpleTray tray = createMiddleTray("SimpleTray" + (trayIndex + 1), trayIndex);
      tray.setMultiPhaseCheck(doMultiPhaseCheck);
      trays.add(tray);
    }

    if (hasCondenser) {
      Condenser condenser = new Condenser("Condenser");
      condenser.setMultiPhaseCheck(doMultiPhaseCheck);
      condenser.setHeatInput(state.condenserHeatInput);
      condenser.setTotalCondenser(state.totalCondenser);
      if (state.condenserRefluxSet) {
        condenser.setRefluxRatio(state.condenserRefluxRatio);
      }
      if (state.condenserHasSetTemperature) {
        condenser.setOutTemperature(state.condenserTemperature);
      }
      trays.add(condenser);
    }

    numberOfTrays = trays.size();
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      distoperations.add(trays.get(trayIndex));
    }
    applyOptimizationPressureProfile();
    applyOptimizationTemperatureProfile(state);
    setDoInitializion(true);
  }

  /**
   * Add all optimization feeds to a candidate feed tray.
   *
   * @param optimizationFeeds feed streams to connect
   * @param feedTray 0-based feed tray number
   */
  private void addOptimizationFeedsToTray(List<StreamInterface> optimizationFeeds, int feedTray) {
    for (StreamInterface feed : optimizationFeeds) {
      addFeedStream(feed, feedTray);
    }
  }

  /**
   * Apply the configured pressure profile to a rebuilt candidate column.
   */
  private void applyOptimizationPressureProfile() {
    if (topTrayPressure <= 0 || bottomTrayPressure <= 0 || numberOfTrays == 0) {
      return;
    }
    if (numberOfTrays == 1) {
      trays.get(0).setPressure(bottomTrayPressure);
      return;
    }
    double pressureStep = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      trays.get(trayIndex).setPressure(bottomTrayPressure - trayIndex * pressureStep);
    }
  }

  /**
   * Apply a linear endpoint temperature profile when both endpoint temperatures are specified.
   *
   * @param state captured column settings to use for endpoint temperatures
   */
  private void applyOptimizationTemperatureProfile(ColumnOptimizationState state) {
    if (!state.reboilerHasSetTemperature || !state.condenserHasSetTemperature
        || numberOfTrays <= 1) {
      return;
    }
    double temperatureStep =
        (state.condenserTemperature - state.reboilerTemperature) / (numberOfTrays - 1.0);
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      trays.get(trayIndex).setTemperature(state.reboilerTemperature + trayIndex * temperatureStep);
    }
  }

  /**
   * Read product mole fraction for the component used in the optimization specification.
   *
   * @param componentName component name
   * @param isTopProduct {@code true} to read top product, {@code false} to read bottom product
   * @return component mole fraction in the requested product
   */
  private double getProductComponentMoleFraction(String componentName, boolean isTopProduct) {
    if (isTopProduct) {
      return gasOutStream.getFluid().getComponent(componentName).getz();
    }
    return liquidOutStream.getFluid().getComponent(componentName).getz();
  }

  /**
   * Create an optimization result from the current column state.
   *
   * @param feasible {@code true} if the current candidate meets the target purity
   * @param totalTrayCount total tray count for the candidate
   * @param feedTray 0-based feed tray number used by the candidate
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param productPurity achieved product mole fraction
   * @param evaluatedCases number of evaluated candidate cases
   * @param convergedCases number of converged candidate cases
   * @param message diagnostic message
   * @return optimization result populated from current duties and residuals
   */
  private TrayOptimizationResult createTrayOptimizationResult(boolean feasible, int totalTrayCount,
      int feedTray, double productSpec, String componentName, boolean isTopProduct,
      double productPurity, int evaluatedCases, int convergedCases, String message) {
    double reboilerDuty = hasReboiler ? getReboiler().getDuty() : 0.0;
    double condenserDuty = hasCondenser ? getCondenser().getDuty() : 0.0;
    double totalAbsoluteDuty = Math.abs(reboilerDuty) + Math.abs(condenserDuty);
    return new TrayOptimizationResult(feasible, feasible ? totalTrayCount : -1,
        feasible ? feedTray : -1, componentName, isTopProduct, productSpec, productPurity,
        reboilerDuty, condenserDuty, totalAbsoluteDuty, lastIterationCount, lastTemperatureResidual,
        lastMassResidual, lastEnergyResidual, evaluatedCases, convergedCases, message);
  }

  /**
   * Create an infeasible tray optimization result.
   *
   * @param productSpec target product mole fraction
   * @param componentName component used in the purity specification
   * @param isTopProduct {@code true} for top product, {@code false} for bottom product
   * @param evaluatedCases number of evaluated candidate cases
   * @param convergedCases number of converged candidate cases
   * @param message diagnostic message
   * @return infeasible optimization result
   */
  private TrayOptimizationResult createInfeasibleTrayOptimizationResult(double productSpec,
      String componentName, boolean isTopProduct, int evaluatedCases, int convergedCases,
      String message) {
    return new TrayOptimizationResult(false, -1, -1, componentName, isTopProduct, productSpec,
        Double.NaN, 0.0, 0.0, 0.0, lastIterationCount, lastTemperatureResidual, lastMassResidual,
        lastEnergyResidual, evaluatedCases, convergedCases, message);
  }

  /**
   * Compare two feasible tray optimization candidates for the same tray count.
   *
   * @param candidate candidate result to evaluate
   * @param currentBest current best result, or {@code null}
   * @return {@code true} if the candidate is preferred
   */
  private boolean isBetterTrayOptimizationCandidate(TrayOptimizationResult candidate,
      TrayOptimizationResult currentBest) {
    if (currentBest == null) {
      return true;
    }
    double dutyDifference = candidate.getTotalAbsoluteDuty() - currentBest.getTotalAbsoluteDuty();
    if (Math.abs(dutyDifference) > 1.0e-6) {
      return dutyDifference < 0.0;
    }
    if (Math.abs(candidate.getMassResidual() - currentBest.getMassResidual()) > 1.0e-9) {
      return candidate.getMassResidual() < currentBest.getMassResidual();
    }
    if (Math.abs(candidate.getEnergyResidual() - currentBest.getEnergyResidual()) > 1.0e-9) {
      return candidate.getEnergyResidual() < currentBest.getEnergyResidual();
    }
    return candidate.getProductPurity() > currentBest.getProductPurity();
  }

  /**
   * Execute the sequential substitution solver with an adaptive relaxation controller.
   *
   * @param id calculation identifier
   * @param initialRelaxation relaxation factor applied to the first iteration
   */
  private void solveSequential(UUID id, double initialRelaxation) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;
    double previousCombinedResidual = Double.POSITIVE_INFINITY;

    long startTime = System.nanoTime();

    double[] oldtemps = new double[numberOfTrays];
    StreamInterface[] previousGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] previousLiquidStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] currentGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] currentLiquidStreams = new StreamInterface[numberOfTrays];

    double relaxation =
        Math.max(minSequentialRelaxation, Math.min(maxAdaptiveRelaxation, initialRelaxation));

    // Run the feed tray to establish initial conditions.
    // On re-runs this is skipped because the tray already holds a valid state
    // from the previous solve; running it unrelaxed would perturb the state
    // and can trigger divergence.
    if (!hasBeenSolvedBefore) {
      trays.get(firstFeedTrayNumber).run(id);
    }

    // Compute total feed flow for divergence detection.
    double totalFeedFlow = 0.0;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface f : feeds) {
        totalFeedFlow += Math.abs(f.getFlowRate("kg/hr"));
      }
    }
    double divergenceThreshold =
        Math.max(totalFeedFlow * MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO, 1.0e3);
    boolean divergenceRecoveryApplied = false;

    // Snapshot tray state before iterations as a safe recovery point.
    StreamInterface[] snapshotGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] snapshotLiquidStreams = new StreamInterface[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      snapshotGasStreams[i] = trays.get(i).getGasOutStream().clone();
      snapshotLiquidStreams[i] = trays.get(i).getLiquidOutStream().clone();
    }

    // On re-runs, seed previous-stream arrays from the snapshot so that
    // relaxation-based damping is active from the very first iteration.
    if (hasBeenSolvedBefore) {
      for (int i = 0; i < numberOfTrays; i++) {
        previousGasStreams[i] = snapshotGasStreams[i].clone();
        previousLiquidStreams[i] = snapshotLiquidStreams[i].clone();
      }
    }

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int polishIterationLimit = baseIterationLimit
        + Math.max(POLISH_ITERATION_MARGIN, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowIncrement = Math.max(3, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowBand = Math.max(overflowIncrement, numberOfTrays);
    int maxIterationLimit = Math.max(iterationLimit, maxNumberOfIterations)
        + overflowBand * ITERATION_OVERFLOW_MULTIPLIER;
    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    double polishTempTolerance = Math.min(baseTempTolerance, TEMPERATURE_POLISH_TARGET);
    double polishMassTolerance = Math.min(baseMassTolerance, MASS_POLISH_TARGET);
    double polishEnergyTolerance = Math.min(baseEnergyTolerance, ENERGY_POLISH_TARGET);
    boolean polishing = false;
    boolean massEnergyEvaluated = false;
    int balanceCheckStride = Math.max(3, numberOfTrays / 2);

    while (iter < iterationLimit) {
      iter++;

      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream = trays.get(i - 1).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxationFast(previousLiquidStreams[i],
            trays.get(i).getLiquidOutStream(), relaxation);
        trays.get(i - 1).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[i] = relaxedLiquid;
        trays.get(i - 1).run(id);
        applyMurphreeCorrection(i - 1);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      StreamInterface reboilerFeed = applyRelaxationFast(previousLiquidStreams[1],
          trays.get(1).getLiquidOutStream(), relaxation);
      trays.get(0).replaceStream(streamNumb, reboilerFeed);
      currentLiquidStreams[1] = reboilerFeed;
      trays.get(0).run(id);
      applyMurphreeCorrection(0);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        StreamInterface relaxedGas = applyRelaxationFast(previousGasStreams[i - 1],
            trays.get(i - 1).getGasOutStream(), relaxation);
        trays.get(i).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[i - 1] = relaxedGas;
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxationFast(previousLiquidStreams[i + 1],
            trays.get(i + 1).getLiquidOutStream(), relaxation);
        trays.get(i).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[i + 1] = relaxedLiquid;
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      double temperatureResidual = 0.0;
      double effectiveRelaxation = Math.max(minTemperatureRelaxation, Math.min(1.0, relaxation));
      for (int i = 0; i < numberOfTrays; i++) {
        double updated = trays.get(i).getThermoSystem().getTemperature();
        if (Double.isNaN(updated) || Double.isInfinite(updated)) {
          updated = oldtemps[i];
        }
        double newTemp = oldtemps[i] + effectiveRelaxation * (updated - oldtemps[i]);
        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - oldtemps[i]);
      }
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      boolean evaluateBalances = shouldEvaluateBalances(iter, iterationLimit, polishing, err,
          baseTempTolerance, balanceCheckStride);
      if (evaluateBalances || !massEnergyEvaluated) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        massEnergyEvaluated = true;
      }

      double tempScaled = err / baseTempTolerance;
      double massScaled = massErr / baseMassTolerance;
      double energyScaled = energyErr / baseEnergyTolerance;
      double combinedResidual = Math.max(tempScaled, massScaled);
      if (Double.isFinite(energyScaled)) {
        combinedResidual =
            Math.max(combinedResidual, Math.min(energyScaled, maxEnergyRelaxationWeight));
      }

      if (combinedResidual > previousCombinedResidual * 1.05) {
        relaxation = Math.max(minSequentialRelaxation, relaxation * relaxationDecreaseFactor);
      } else if (combinedResidual < previousCombinedResidual * 0.98) {
        relaxation = Math.min(maxAdaptiveRelaxation, relaxation * relaxationIncreaseFactor);
      }

      previousCombinedResidual = combinedResidual;

      // Divergence recovery: if flows have grown far beyond the total feed,
      // the sequential iteration is unstable. Restore previous-stream arrays
      // from the pre-iteration snapshot and drop to minimum relaxation
      // so that subsequent iterations are heavily damped. This is a one-shot
      // recovery that does not fire when the column is already converging.
      if (!divergenceRecoveryApplied && iter <= 10) {
        double maxTrayFlow = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
          maxTrayFlow =
              Math.max(maxTrayFlow, Math.abs(trays.get(i).getGasOutStream().getFlowRate("kg/hr")));
          maxTrayFlow = Math.max(maxTrayFlow,
              Math.abs(trays.get(i).getLiquidOutStream().getFlowRate("kg/hr")));
        }
        if (maxTrayFlow > divergenceThreshold) {
          relaxation = minSequentialRelaxation;
          for (int i = 0; i < numberOfTrays; i++) {
            previousGasStreams[i] = snapshotGasStreams[i].clone();
            previousLiquidStreams[i] = snapshotLiquidStreams[i].clone();
          }
          divergenceRecoveryApplied = true;
          internalTrafficCapActive = true;
          previousCombinedResidual = Double.POSITIVE_INFINITY;
          logger.info(
              "Divergence detected at iter {}, maxTrayFlow={} > threshold={}. "
                  + "Restoring from snapshot and reducing relaxation to {}.",
              iter, maxTrayFlow, divergenceThreshold, relaxation);
        }
      }

      for (int i = 0; i < numberOfTrays; i++) {
        if (currentGasStreams[i] != null) {
          previousGasStreams[i] = currentGasStreams[i];
        }
        if (currentLiquidStreams[i] != null) {
          previousLiquidStreams[i] = currentLiquidStreams[i];
        }
      }

      // Absolute flow magnitude check: if tray flows are vastly larger than
      // the total feed, the solver has diverged beyond recovery. Break early
      // and report a large mass residual so callers can detect the failure.
      if (divergenceRecoveryApplied && iter > 15) {
        double maxFlow = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
          maxFlow =
              Math.max(maxFlow, Math.abs(trays.get(i).getGasOutStream().getFlowRate("kg/hr")));
          maxFlow =
              Math.max(maxFlow, Math.abs(trays.get(i).getLiquidOutStream().getFlowRate("kg/hr")));
        }
        if (maxFlow > 1000.0 * totalFeedFlow) {
          logger.warn("Column solver diverged: maxTrayFlow={} exceeds 1000x totalFeed={}. "
              + "Terminating at iteration {}.", maxFlow, totalFeedFlow, iter);
          massErr = maxFlow / Math.max(1.0, totalFeedFlow);
          break;
        }
      }

      double guardedFlow = getMaximumTrayOutletFlowKgPerHour();
      if (divergenceRecoveryApplied && iter > 15
          && guardedFlow >= 0.99 * getMaximumRelaxedInternalFlowKgPerHour()) {
        logger.warn("Column solver reached internal traffic guard: maxTrayFlow={} at iteration {}.",
            guardedFlow, iter);
        massErr = Math.max(massErr, guardedFlow / Math.max(1.0, totalFeedFlow));
        lastInternalTrafficGuardReached = true;
        break;
      }

      logger.debug("iteration {} relaxation={} tempErr={} massErr={} energyErr={}", iter,
          relaxation, err, massErr, energyErr);

      if (convergenceHistory != null) {
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      boolean energyWithinBase = !enforceEnergyBalanceTolerance || energyErr <= baseEnergyTolerance;
      boolean withinBaseTolerance =
          err <= baseTempTolerance && massErr <= baseMassTolerance && energyWithinBase;

      if (withinBaseTolerance) {
        boolean polishingAvailable =
            polishMassTolerance < baseMassTolerance || polishEnergyTolerance < baseEnergyTolerance
                || polishTempTolerance < baseTempTolerance;

        if (!polishing && polishingAvailable
            && (massErr > polishMassTolerance || energyErr > polishEnergyTolerance)) {
          polishing = true;
          iterationLimit = Math.max(iterationLimit, polishIterationLimit);
          previousCombinedResidual = Double.POSITIVE_INFINITY;
          continue;
        }

        double tempTarget = polishing ? polishTempTolerance : baseTempTolerance;
        double massTarget = polishing ? polishMassTolerance : baseMassTolerance;
        double energyTarget = polishing ? polishEnergyTolerance : baseEnergyTolerance;
        boolean energyWithinTarget = !enforceEnergyBalanceTolerance || energyErr <= energyTarget;

        if (err <= tempTarget && massErr <= massTarget && energyWithinTarget) {
          break;
        }
      }

      if (iter >= iterationLimit && err > baseTempTolerance && iterationLimit < maxIterationLimit) {
        iterationLimit = Math.min(maxIterationLimit, iterationLimit + overflowIncrement);
        continue;
      }
    }

    lastIterationCount = iter;
    lastTemperatureResidual = err;
    lastMassResidual = massErr;
    lastEnergyResidual = energyErr;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    hasBeenSolvedBefore = true;
    lastTotalFeedFlow = totalFeedFlow;

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
  }

  /**
   * Solve and finalize a column that contains only one active tray.
   *
   * @param id calculation identifier
   */
  private void solveSingleTray(UUID id) {
    long startTime = System.nanoTime();
    trays.get(0).run(id);
    err = 0.0;
    finalizeSolve(id, 1, 0.0, 0.0, 0.0, startTime);
    hasBeenSolvedBefore = true;
    lastTotalFeedFlow = getTotalExternalFeedFlowKgPerHour();
  }

  /**
   * Determine the iteration limit based on configuration and column size.
   *
   * @return maximum number of solver iterations allowed
   */
  private int computeIterationLimit() {
    int trayBasedLimit = (int) Math.ceil(Math.max(5.0, numberOfTrays * TRAY_ITERATION_FACTOR));
    return Math.max(Math.max(1, maxNumberOfIterations), trayBasedLimit);
  }

  /**
   * Derive the effective temperature tolerance based on column complexity unless overridden.
   *
   * @return adaptive temperature tolerance in Kelvin
   */
  private double getEffectiveTemperatureTolerance() {
    if (temperatureToleranceCustomized) {
      return temperatureTolerance;
    }
    return DEFAULT_TEMPERATURE_TOLERANCE * computeToleranceComplexityMultiplier();
  }

  /**
   * Derive the effective mass balance tolerance based on column complexity unless overridden.
   *
   * @return adaptive mass balance tolerance (relative)
   */
  private double getEffectiveMassBalanceTolerance() {
    if (massBalanceToleranceCustomized) {
      return massBalanceTolerance;
    }
    return DEFAULT_MASS_BALANCE_TOLERANCE * computeToleranceComplexityMultiplier();
  }

  /**
   * Derive the effective enthalpy balance tolerance based on column complexity unless overridden.
   *
   * @return adaptive energy balance tolerance (relative)
   */
  private double getEffectiveEnthalpyBalanceTolerance() {
    if (enthalpyBalanceToleranceCustomized) {
      return enthalpyBalanceTolerance;
    }
    return DEFAULT_ENTHALPY_BALANCE_TOLERANCE * computeToleranceComplexityMultiplier();
  }

  /**
   * Estimate a scaling factor that reflects the degree of distillation complexity.
   *
   * <p>
   * The factor increases with the number of theoretical stages and independent feed streams and is
   * bounded to avoid overly loose convergence criteria.
   * </p>
   *
   * @return scaling multiplier for recommended tolerances
   */
  private double computeToleranceComplexityMultiplier() {
    int stageCount = Math.max(1, getEffectiveStageCount());
    double stageMultiplier = 1.0 + Math.max(0, stageCount - 3) * 0.06;

    int feedCount = Math.max(0, getTotalFeedCount());
    double feedMultiplier = 1.0 + Math.max(0, feedCount - 1) * 0.25;

    double combined = Math.max(stageMultiplier, feedMultiplier);
    return Math.min(2.5, combined);
  }

  /**
   * Count the number of simple trays, excluding optional reboiler and condenser sections.
   *
   * @return effective number of equilibrium stages
   */
  private int getEffectiveStageCount() {
    int stageCount = numberOfTrays;
    if (hasReboiler && stageCount > 0) {
      stageCount--;
    }
    if (hasCondenser && stageCount > 0) {
      stageCount--;
    }
    return Math.max(0, stageCount);
  }

  /**
   * Count connected feed streams across all trays.
   *
   * @return total number of feeds currently attached to the column
   */
  private int getTotalFeedCount() {
    int total = 0;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      total += feeds.size();
    }
    return total;
  }

  /**
   * Decide whether mass and energy balance residuals should be recomputed this iteration.
   *
   * @param iteration current iteration index (1-based)
   * @param iterationLimit current iteration ceiling
   * @param polishing whether the solver is in the polish stage
   * @param tempResidual average temperature residual this iteration
   * @param baseTempTolerance nominal temperature tolerance
   * @param balanceCheckStride cadence for periodic balance checks
   * @return {@code true} if balances should be evaluated
   */
  private boolean shouldEvaluateBalances(int iteration, int iterationLimit, boolean polishing,
      double tempResidual, double baseTempTolerance, int balanceCheckStride) {
    if (polishing || iteration <= 2 || iteration >= iterationLimit - 1) {
      return true;
    }
    if (tempResidual <= baseTempTolerance * 4.0) {
      return true;
    }
    return balanceCheckStride <= 1 || iteration % balanceCheckStride == 0;
  }

  /**
   * Prepare the column for a solving sequence by updating pressures and cloning feed systems.
   *
   * @return index of the lowest feed tray in the column
   */
  private int prepareColumnForSolve() {
    int firstFeedTrayNumber = feedStreams.keySet().stream().min(Integer::compareTo).get();

    if (bottomTrayPressure < 0) {
      bottomTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }
    if (topTrayPressure < 0) {
      topTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }

    double dp = 0.0;
    if (numberOfTrays > 1) {
      dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
    }
    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setPressure(bottomTrayPressure - i * dp);
    }

    int[] numeroffeeds = new int[numberOfTrays];
    for (Entry<Integer, List<StreamInterface>> entry : feedStreams.entrySet()) {
      int feedTrayNumber = entry.getKey();
      List<StreamInterface> trayFeeds = entry.getValue();
      for (StreamInterface feedStream : trayFeeds) {
        numeroffeeds[feedTrayNumber]++;
        SystemInterface cloned = feedStream.getThermoSystem().clone();
        trays.get(feedTrayNumber).getStream(numeroffeeds[feedTrayNumber] - 1)
            .setThermoSystem(cloned);
      }
    }

    return firstFeedTrayNumber;
  }

  /**
   * Solve the column using an improved inside-out strategy inspired by Boston and Sullivan (1974).
   *
   * <p>
   * Key improvements over basic sequential substitution:
   * <ul>
   * <li>K-value caching: previous iteration K-values are stored to track composition convergence
   * and detect stagnation early.</li>
   * <li>Composition-based convergence: monitors maximum relative K-value change alongside
   * temperature and balance residuals.</li>
   * <li>Stripping factor correction: applies a bulk flow correction between outer iterations based
   * on the ratio of computed-to-assumed vapor/liquid split on each tray.</li>
   * <li>Accelerated relaxation ramp: increases relaxation faster (1.3× vs 1.2×) when residuals
   * decrease, enabling the IO method to reach full step sooner.</li>
   * <li>Lazy balance evaluation: mass/energy balances are only recomputed when temperatures are
   * close to tolerance, reducing expensive per-tray flow rate queries.</li>
   * </ul>
   *
   * @param id calculation identifier
   */
  void solveInsideOut(UUID id) {
    resetInsideOutTelemetry();
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;
    double previousCombinedResidual = Double.POSITIVE_INFINITY;

    long startTime = System.nanoTime();

    double[] oldtemps = new double[numberOfTrays];
    StreamInterface[] previousGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] previousLiquidStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] currentGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] currentLiquidStreams = new StreamInterface[numberOfTrays];

    // K-value cache for composition convergence tracking
    double[][] previousKvalues = null;
    double kValueResidual = Double.POSITIVE_INFINITY;

    // IO method: start with moderate relaxation, ramp up aggressively
    double relaxation = Math.max(minInsideOutRelaxation, Math.min(maxAdaptiveRelaxation, 0.8));
    double ioRelaxationIncreaseFactor = 1.3; // faster ramp than sequential (1.2)

    if (!hasBeenSolvedBefore) {
      trays.get(firstFeedTrayNumber).run(id);
    }

    // Compute total feed flow for divergence detection.
    double totalFeedFlowIO = 0.0;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface f : feeds) {
        totalFeedFlowIO += Math.abs(f.getFlowRate("kg/hr"));
      }
    }
    double divergenceThresholdIO =
        Math.max(totalFeedFlowIO * MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO, 1.0e3);
    boolean divergenceRecoveryAppliedIO = false;

    // Snapshot tray state before iterations as a safe recovery point.
    StreamInterface[] snapshotGasStreamsIO = new StreamInterface[numberOfTrays];
    StreamInterface[] snapshotLiquidStreamsIO = new StreamInterface[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      snapshotGasStreamsIO[i] = trays.get(i).getGasOutStream().clone();
      snapshotLiquidStreamsIO[i] = trays.get(i).getLiquidOutStream().clone();
    }

    // On re-runs, seed previous-stream arrays from the snapshot.
    if (hasBeenSolvedBefore) {
      for (int i = 0; i < numberOfTrays; i++) {
        previousGasStreams[i] = snapshotGasStreamsIO[i].clone();
        previousLiquidStreams[i] = snapshotLiquidStreamsIO[i].clone();
      }
    }

    // Simplified inner-loop K-value model setup
    int nc = trays.get(firstFeedTrayNumber).getThermoSystem().getNumberOfComponents();
    SimplifiedKvalueModel kModel = new SimplifiedKvalueModel(numberOfTrays, nc);
    double[][] prevOuterKvalues = null;
    double[] prevOuterTemps = new double[numberOfTrays];
    int outerIterCount = 0; // counts rigorous (outer) iterations
    int totalFlashSweeps = 0; // tracks flash count for diagnostics
    int totalInnerLoopIterations = 0;
    double latestSurrogateResidual = Double.NaN;

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int polishIterationLimit = baseIterationLimit
        + Math.max(POLISH_ITERATION_MARGIN, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowIncrement = Math.max(3, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowBand = Math.max(overflowIncrement, numberOfTrays);
    int maxIterationLimit = Math.max(iterationLimit, maxNumberOfIterations)
        + overflowBand * ITERATION_OVERFLOW_MULTIPLIER;
    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    double polishTempTolerance = Math.min(baseTempTolerance, TEMPERATURE_POLISH_TARGET);
    double polishMassTolerance = Math.min(baseMassTolerance, MASS_POLISH_TARGET);
    double polishEnergyTolerance = Math.min(baseEnergyTolerance, ENERGY_POLISH_TARGET);
    boolean polishing = false;
    boolean massEnergyEvaluated = false;
    int balanceCheckStride = Math.max(3, numberOfTrays / 2);

    while (iter < iterationLimit) {
      iter++;

      Arrays.fill(currentGasStreams, null);
      Arrays.fill(currentLiquidStreams, null);

      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Phase 1: Liquid sweep (feed → reboiler) with relaxed tear streams
      for (int stage = firstFeedTrayNumber; stage >= 1; stage--) {
        int target = stage - 1;
        int replaceStream = trays.get(target).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxationFast(previousLiquidStreams[stage],
            trays.get(stage).getLiquidOutStream(), relaxation);
        trays.get(target).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[stage] = relaxedLiquid;
        trays.get(target).run(id);
        applyMurphreeCorrection(target);
      }

      // Phase 2: Vapor sweep (reboiler → condenser) with relaxed tear streams
      for (int stage = 1; stage <= numberOfTrays - 1; stage++) {
        int replaceStream = trays.get(stage).getNumberOfInputStreams() - 2;
        if (stage == (numberOfTrays - 1)) {
          replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
        }
        StreamInterface relaxedGas = applyRelaxationFast(previousGasStreams[stage - 1],
            trays.get(stage - 1).getGasOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[stage - 1] = relaxedGas;
        trays.get(stage).run(id);
        applyMurphreeCorrection(stage);
      }

      // Phase 3: Polish liquid sweep (condenser → feed) for better coupling
      for (int stage = numberOfTrays - 2; stage >= firstFeedTrayNumber; stage--) {
        int replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxationFast(previousLiquidStreams[stage + 1],
            trays.get(stage + 1).getLiquidOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[stage + 1] = relaxedLiquid;
        trays.get(stage).run(id);
        applyMurphreeCorrection(stage);
      }

      // Phase 4: Stripping factor correction — adjust temperatures using V/L flow
      // balance
      double temperatureResidual = 0.0;
      double effectiveRelaxation = Math.max(minTemperatureRelaxation, Math.min(1.0, relaxation));

      for (int i = 0; i < numberOfTrays; i++) {
        double updated = trays.get(i).getThermoSystem().getTemperature();

        // Stripping factor correction: if V/L ratio on a tray is far from unity,
        // bias temperature update toward the flow-corrected value
        double vaporFlow = trays.get(i).getGasOutStream().getFlowRate("kg/hr");
        double liquidFlow = trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
        double strippingCorrection = 1.0;
        if (liquidFlow > 1e-12 && vaporFlow > 1e-12) {
          double vOverL = vaporFlow / liquidFlow;
          // Mild correction: push temperature up if too much liquid, down if too much
          // vapor
          strippingCorrection = 1.0 + 0.05 * Math.max(-1.0, Math.min(1.0, Math.log(vOverL)));
        }

        double newTemp =
            oldtemps[i] + effectiveRelaxation * strippingCorrection * (updated - oldtemps[i]);
        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - oldtemps[i]);
      }
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      // K-value convergence tracking
      kValueResidual = computeKvalueResidual(previousKvalues);
      previousKvalues = cacheCurrentKvalues();
      outerIterCount++;
      totalFlashSweeps++;

      // Fit simplified K-value model after 2nd rigorous outer iteration
      if (outerIterCount >= 2 && prevOuterKvalues != null && innerLoopSteps > 0) {
        double[] currentTemps = new double[numberOfTrays];
        for (int i = 0; i < numberOfTrays; i++) {
          currentTemps[i] = trays.get(i).getThermoSystem().getTemperature();
        }
        kModel.fit(prevOuterKvalues, prevOuterTemps, previousKvalues, currentTemps);
      }

      // Save outer-loop state for next model fitting
      prevOuterKvalues = previousKvalues.clone();
      for (int i = 0; i < numberOfTrays; i++) {
        prevOuterTemps[i] = trays.get(i).getThermoSystem().getTemperature();
        if (prevOuterKvalues[i] != null) {
          prevOuterKvalues[i] = prevOuterKvalues[i].clone();
        }
      }

      // Run simplified inner-loop iterations (no PH-flash) if model is fitted
      if (kModel.fitted && innerLoopSteps > 0 && !polishing) {
        double latestInnerTempResidual = err;
        for (int inner = 0; inner < innerLoopSteps; inner++) {
          double innerTempResidual = innerLoopIteration(kModel, relaxation);
          latestInnerTempResidual = innerTempResidual;
          latestSurrogateResidual = innerTempResidual;
          totalInnerLoopIterations++;
          // Log inner iteration (inner iters don't count in outer iteration budget)
          logger.debug("inside-out INNER step {}/{} tempErr={}", inner + 1, innerLoopSteps,
              innerTempResidual);
          if (convergenceHistory != null) {
            convergenceHistory
                .add(new double[] {innerTempResidual, massErr, energyErr, kValueResidual});
          }
          // If inner loop has converged, no need for more inner steps
          if (innerTempResidual < baseTempTolerance * 0.5) {
            break;
          }
        }
        // Do not let the simplified inner loop hide the rigorous outer-loop residual.
        err = Math.max(err, latestInnerTempResidual);
      }

      boolean evaluateBalances = shouldEvaluateBalances(iter, iterationLimit, polishing, err,
          baseTempTolerance, balanceCheckStride);
      if (evaluateBalances || !massEnergyEvaluated) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        massEnergyEvaluated = true;
      }

      double tempScaled = err / baseTempTolerance;
      double massScaled = massErr / baseMassTolerance;
      double energyScaled = energyErr / baseEnergyTolerance;
      double combinedResidual = Math.max(tempScaled, massScaled);
      if (Double.isFinite(energyScaled)) {
        combinedResidual =
            Math.max(combinedResidual, Math.min(energyScaled, maxEnergyRelaxationWeight));
      }

      // Accelerated adaptive relaxation for IO method
      if (combinedResidual > previousCombinedResidual * 1.05) {
        relaxation = Math.max(minInsideOutRelaxation, relaxation * relaxationDecreaseFactor);
      } else if (combinedResidual < previousCombinedResidual * 0.95) {
        // More aggressive increase than sequential — IO can tolerate faster ramp
        relaxation = Math.min(maxAdaptiveRelaxation, relaxation * ioRelaxationIncreaseFactor);
      }

      previousCombinedResidual = combinedResidual;

      // Divergence recovery (same logic as solveSequential).
      if (!divergenceRecoveryAppliedIO && iter <= 10) {
        double maxTrayFlow = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
          maxTrayFlow =
              Math.max(maxTrayFlow, Math.abs(trays.get(i).getGasOutStream().getFlowRate("kg/hr")));
          maxTrayFlow = Math.max(maxTrayFlow,
              Math.abs(trays.get(i).getLiquidOutStream().getFlowRate("kg/hr")));
        }
        if (maxTrayFlow > divergenceThresholdIO) {
          relaxation = minInsideOutRelaxation;
          for (int i = 0; i < numberOfTrays; i++) {
            previousGasStreams[i] = snapshotGasStreamsIO[i].clone();
            previousLiquidStreams[i] = snapshotLiquidStreamsIO[i].clone();
          }
          divergenceRecoveryAppliedIO = true;
          internalTrafficCapActive = true;
          previousCombinedResidual = Double.POSITIVE_INFINITY;
          logger.info(
              "inside-out divergence detected at iter {}, maxTrayFlow={} > threshold={}. "
                  + "Restoring from snapshot and reducing relaxation to {}.",
              iter, maxTrayFlow, divergenceThresholdIO, relaxation);
        }
      }

      for (int i = 0; i < numberOfTrays; i++) {
        if (currentGasStreams[i] != null) {
          previousGasStreams[i] = currentGasStreams[i];
        }
        if (currentLiquidStreams[i] != null) {
          previousLiquidStreams[i] = currentLiquidStreams[i];
        }
      }

      double guardedFlow = getMaximumTrayOutletFlowKgPerHour();
      if (divergenceRecoveryAppliedIO && iter > 15
          && guardedFlow >= 0.99 * getMaximumRelaxedInternalFlowKgPerHour()) {
        logger.warn(
            "Inside-out solver reached internal traffic guard: maxTrayFlow={} at iteration {}.",
            guardedFlow, iter);
        massErr = Math.max(massErr, guardedFlow / Math.max(1.0, totalFeedFlowIO));
        lastInternalTrafficGuardReached = true;
        break;
      }

      logger.debug(
          "inside-out iteration {} relaxation={} tempErr={} massErr={} energyErr={} kErr={} outerFlashes={}",
          iter, relaxation, err, massErr, energyErr, kValueResidual, totalFlashSweeps);

      if (convergenceHistory != null) {
        recordConvergence(new double[] {err, massErr, energyErr, kValueResidual});
      }

      boolean energyWithinBase = !enforceEnergyBalanceTolerance || energyErr <= baseEnergyTolerance;
      boolean withinBaseTolerance =
          err <= baseTempTolerance && massErr <= baseMassTolerance && energyWithinBase;

      if (withinBaseTolerance) {
        boolean polishingAvailable =
            polishMassTolerance < baseMassTolerance || polishEnergyTolerance < baseEnergyTolerance
                || polishTempTolerance < baseTempTolerance;

        if (!polishing && polishingAvailable
            && (massErr > polishMassTolerance || energyErr > polishEnergyTolerance)) {
          polishing = true;
          iterationLimit = Math.max(iterationLimit, polishIterationLimit);
          previousCombinedResidual = Double.POSITIVE_INFINITY;
          continue;
        }

        double tempTarget = polishing ? polishTempTolerance : baseTempTolerance;
        double massTarget = polishing ? polishMassTolerance : baseMassTolerance;
        double energyTarget = polishing ? polishEnergyTolerance : baseEnergyTolerance;
        boolean energyWithinTarget = !enforceEnergyBalanceTolerance || energyErr <= energyTarget;

        if (err <= tempTarget && massErr <= massTarget && energyWithinTarget) {
          break;
        }
      }

      // Early termination: if K-values have converged but mass/energy haven't,
      // the problem may be ill-conditioned — avoid wasting iterations
      if (kValueResidual < 1.0e-6 && iter > 5 && err > baseTempTolerance * 10) {
        logger.warn("Inside-out: K-values converged but temperatures stagnated at iter {}", iter);
      }

      if (iter >= iterationLimit && err > baseTempTolerance && iterationLimit < maxIterationLimit) {
        iterationLimit = Math.min(maxIterationLimit, iterationLimit + overflowIncrement);
        continue;
      }
    }

    lastInsideOutOuterFlashSweeps = totalFlashSweeps;
    lastInsideOutInnerLoopIterations = totalInnerLoopIterations;
    lastInsideOutKValueResidual = kValueResidual;
    lastInsideOutSurrogateResidual = latestSurrogateResidual;
    lastInsideOutSurrogateResetCount = 0;
    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
  }

  /**
   * Solve the column with matrix inside-out component balances before rigorous polishing.
   *
   * <p>
   * The matrix stage solves component material-balance tridiagonal systems using cached K-values
   * and cached K-temperature derivatives. It is accepted only as a warm start; the final products
   * and convergence metrics still come from the rigorous inside-out solver.
   * </p>
   *
   * @param id calculation identifier
   */
  void solveMatrixInsideOut(UUID id) {
    resetMatrixInsideOutDiagnostics();
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    if (shouldBypassMatrixInsideOutWarmStart()) {
      lastMatrixInsideOutWarmStartBypassed = true;
      solveInsideOut(id);
      return;
    }

    prepareColumnForSolve();
    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    boolean wasSolvedBefore = hasBeenSolvedBefore;
    DistillationColumnMatrixSolver matrixSolver = new DistillationColumnMatrixSolver(this);
    int matrixIterationLimit =
        Math.max(2, Math.min(Math.max(4, numberOfTrays), Math.max(2, maxNumberOfIterations / 4)));
    matrixSolver.setMaxIterations(matrixIterationLimit);
    matrixSolver.setTolerance(Math.max(getEffectiveTemperatureTolerance(), 5.0e-2));
    matrixSolver.setDampingFactor(Math.max(0.2, Math.min(0.6, minInsideOutRelaxation)));

    boolean matrixWarmStartAccepted = false;
    try {
      matrixWarmStartAccepted = matrixSolver.solve(id);
    } catch (RuntimeException exception) {
      logger.debug("Matrix inside-out warm start failed; continuing with rigorous inside-out.",
          exception);
    }

    int matrixIterations = matrixSolver.getLastIterationCount();
    double matrixSolveTime = matrixSolver.getLastSolveTimeSeconds();
    double matrixTemperatureResidual = matrixSolver.getLastTemperatureResidual();
    lastMatrixInsideOutWarmStartUsed = matrixWarmStartAccepted;
    lastMatrixInsideOutWarmStartBypassed = false;
    lastMatrixInsideOutIterationCount = matrixIterations;
    lastMatrixInsideOutTemperatureResidual = matrixTemperatureResidual;
    lastMatrixInsideOutSolveTimeSeconds = matrixSolveTime;
    if (matrixWarmStartAccepted) {
      hasBeenSolvedBefore = true;
      setDoInitializion(false);
    } else {
      hasBeenSolvedBefore = wasSolvedBefore;
      setDoInitializion(true);
    }

    solveInsideOut(id);
    lastIterationCount += matrixIterations;
    lastSolveTimeSeconds += matrixSolveTime;
    logger.debug("Matrix inside-out stage iterations={} residual={} accepted={}", matrixIterations,
        matrixTemperatureResidual, matrixWarmStartAccepted);
  }

  /**
   * Decide whether the matrix warm-start stage should be skipped for the current column size.
   *
   * <p>
   * The matrix component-balance stage has fixed setup cost and is only expected to pay off on
   * larger columns. Small benchmark columns are faster with the rigorous inside-out path directly.
   * </p>
   *
   * @return {@code true} when the adaptive matrix solver should use rigorous inside-out directly
   */
  private boolean shouldBypassMatrixInsideOutWarmStart() {
    return numberOfTrays < MIN_MATRIX_INSIDE_OUT_WARM_START_TRAYS;
  }

  /**
   * Compute the maximum relative K-value change compared to the previous iteration.
   *
   * <p>
   * K-values are computed as the ratio of vapor to liquid mole fractions for each component on each
   * tray. This provides a composition-based convergence metric that complements the
   * temperature-based metric, similar to what commercial inside-out implementations track.
   * </p>
   *
   * @param previousKvalues K-values from the previous iteration (null if first iteration)
   * @return maximum relative K-value change; {@code Double.POSITIVE_INFINITY} if no previous data
   */
  private double computeKvalueResidual(double[][] previousKvalues) {
    if (previousKvalues == null) {
      return Double.POSITIVE_INFINITY;
    }

    double maxRelChange = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      SystemInterface fluid = trays.get(i).getThermoSystem();
      if (fluid.getNumberOfPhases() < 2) {
        continue;
      }
      int nc = fluid.getNumberOfComponents();
      for (int j = 0; j < nc; j++) {
        double xj = fluid.getPhase(1).getComponent(j).getx();
        double yj = fluid.getPhase(0).getComponent(j).getx();
        if (xj > 1e-15) {
          double kCurrent = yj / xj;
          double kPrevious = previousKvalues[i][j];
          if (kPrevious > 1e-15) {
            double relChange = Math.abs(kCurrent - kPrevious) / kPrevious;
            maxRelChange = Math.max(maxRelChange, relChange);
          }
        }
      }
    }
    return maxRelChange;
  }

  /**
   * Cache K-values (y/x) for all components on all trays for use in the next iteration comparison.
   *
   * @return 2D array [tray][component] of K-values
   */
  private double[][] cacheCurrentKvalues() {
    double[][] kvalues = new double[numberOfTrays][];
    for (int i = 0; i < numberOfTrays; i++) {
      SystemInterface fluid = trays.get(i).getThermoSystem();
      int nc = fluid.getNumberOfComponents();
      kvalues[i] = new double[nc];
      if (fluid.getNumberOfPhases() >= 2) {
        for (int j = 0; j < nc; j++) {
          double xj = fluid.getPhase(1).getComponent(j).getx();
          if (xj > 1e-15) {
            kvalues[i][j] = fluid.getPhase(0).getComponent(j).getx() / xj;
          }
        }
      }
    }
    return kvalues;
  }

  /**
   * Simplified K-value model for the inside-out inner loop.
   *
   * <p>
   * For each component on each tray, the model stores coefficients for the correlation:
   *
   * <pre>
   *   ln K_i = a_i + b_i / T
   * </pre>
   *
   * where T is in Kelvin. The coefficients are fitted from rigorous flash results at two
   * temperature points (the current and previous outer-loop temperatures). Between outer-loop
   * updates, compositions are estimated using this simplified model instead of full PH-flash
   * calculations, reducing computational cost by a factor of approximately {@code innerLoopSteps}.
   */
  static class SimplifiedKvalueModel {
    /** Intercept coefficient: lnK = a + b/T. Indexed [tray][component]. */
    final double[][] coeffA;
    /** Slope coefficient: lnK = a + b/T. Indexed [tray][component]. */
    final double[][] coeffB;
    /** Number of trays. */
    final int nTrays;
    /** Number of components. */
    final int nComponents;
    /** Whether the model has been fitted (needs at least 2 temperature points). */
    boolean fitted = false;

    SimplifiedKvalueModel(int nTrays, int nComponents) {
      this.nTrays = nTrays;
      this.nComponents = nComponents;
      this.coeffA = new double[nTrays][nComponents];
      this.coeffB = new double[nTrays][nComponents];
    }

    /**
     * Fit the model from two sets of K-values at two temperatures.
     *
     * <p>
     * Given K-values at T1 and T2, we solve:
     *
     * <pre>
     *   ln(K1) = a + b/T1
     *   ln(K2) = a + b/T2
     * </pre>
     *
     * yielding b = (ln(K2) - ln(K1)) / (1/T2 - 1/T1) and a = ln(K1) - b/T1.
     *
     * @param kvalues1 K-values at temperature T1 [tray][component]
     * @param temps1 tray temperatures at point 1
     * @param kvalues2 K-values at temperature T2 [tray][component]
     * @param temps2 tray temperatures at point 2
     */
    void fit(double[][] kvalues1, double[] temps1, double[][] kvalues2, double[] temps2) {
      for (int i = 0; i < nTrays; i++) {
        double t1 = temps1[i];
        double t2 = temps2[i];
        if (t1 < 1.0 || t2 < 1.0 || Math.abs(t1 - t2) < 0.01) {
          // Temperatures too close or invalid — use single-point model (b=0)
          for (int j = 0; j < nComponents && j < kvalues2[i].length; j++) {
            if (kvalues2[i][j] > 1e-30) {
              coeffA[i][j] = Math.log(kvalues2[i][j]);
              coeffB[i][j] = 0.0;
            }
          }
          continue;
        }
        double invT1 = 1.0 / t1;
        double invT2 = 1.0 / t2;
        double dInvT = invT2 - invT1;
        for (int j = 0; j < nComponents && j < kvalues1[i].length && j < kvalues2[i].length; j++) {
          double k1 = kvalues1[i][j];
          double k2 = kvalues2[i][j];
          if (k1 > 1e-30 && k2 > 1e-30) {
            double lnK1 = Math.log(k1);
            double lnK2 = Math.log(k2);
            coeffB[i][j] = (lnK2 - lnK1) / dInvT;
            coeffA[i][j] = lnK1 - coeffB[i][j] * invT1;
          } else if (k2 > 1e-30) {
            coeffA[i][j] = Math.log(k2);
            coeffB[i][j] = 0.0;
          }
        }
      }
      fitted = true;
    }

    /**
     * Predict K-value for component j on tray i at temperature T.
     *
     * @param tray tray index
     * @param component component index
     * @param temperature temperature in Kelvin
     * @return estimated K-value
     */
    double predict(int tray, int component, double temperature) {
      if (temperature < 1.0) {
        return 1.0;
      }
      double lnK = coeffA[tray][component] + coeffB[tray][component] / temperature;
      // Bound to prevent extreme values
      lnK = Math.max(-30.0, Math.min(30.0, lnK));
      return Math.exp(lnK);
    }
  }

  /**
   * Perform a simplified inner-loop iteration using the K-value model instead of rigorous flash.
   *
   * <p>
   * This method updates tray compositions using the simplified K-value correlation and adjusts
   * temperatures via a bubble-point calculation (sum of K*x = 1 condition). No PH-flash is called,
   * making each inner iteration much cheaper than a rigorous outer iteration.
   * </p>
   *
   * @param model the fitted simplified K-value model
   * @param relaxation current relaxation factor
   * @return average absolute temperature change across all trays
   */
  private double innerLoopIteration(SimplifiedKvalueModel model, double relaxation) {
    double tempResidual = 0.0;
    double effectiveRelaxation = Math.max(minTemperatureRelaxation, Math.min(1.0, relaxation));

    for (int i = 0; i < numberOfTrays; i++) {
      SystemInterface fluid = trays.get(i).getThermoSystem();
      if (fluid.getNumberOfPhases() < 2) {
        continue;
      }

      double trayTemp = fluid.getTemperature();
      int nc = fluid.getNumberOfComponents();

      // Estimate new compositions using simplified K-values at current temperature
      double sumKx = 0.0;
      double[] kPredicted = new double[nc];
      for (int j = 0; j < nc; j++) {
        kPredicted[j] = model.predict(i, j, trayTemp);
        double xj = fluid.getPhase(1).getComponent(j).getx();
        sumKx += kPredicted[j] * xj;
      }

      // Bubble-point temperature correction: if sum(K*x) != 1, adjust T
      // Using Newton-like step: dT = -f(T)/f'(T) where f(T) = sum(K*x) - 1
      // f'(T) ≈ -sum(b_j/T^2 * K_j * x_j) (derivative of K model w.r.t. T)
      if (sumKx > 1e-10) {
        double dfdt = 0.0;
        for (int j = 0; j < nc; j++) {
          double xj = fluid.getPhase(1).getComponent(j).getx();
          dfdt += -model.coeffB[i][j] / (trayTemp * trayTemp) * kPredicted[j] * xj;
        }

        double correction = 0.0;
        if (Math.abs(dfdt) > 1e-15) {
          correction = -(sumKx - 1.0) / dfdt;
          // Safeguard: limit step size
          correction = Math.max(-15.0, Math.min(15.0, correction));
        }

        double newTemp = trayTemp + effectiveRelaxation * correction;
        // Ensure temperature stays positive
        newTemp = Math.max(50.0, newTemp);
        tempResidual += Math.abs(newTemp - trayTemp);
        trays.get(i).setTemperature(newTemp);

        // Update vapor compositions: y_j = K_j * x_j / sum(K*x)
        // (normalized to ensure summation)
        if (sumKx > 1e-10) {
          for (int j = 0; j < nc; j++) {
            double xj = fluid.getPhase(1).getComponent(j).getx();
            double newYj = Math.max(0.0, kPredicted[j] * xj / sumKx);
            // Only update if we have vapor phase access
            try {
              fluid.getPhase(0).getComponent(j).setx(newYj);
            } catch (Exception ex) {
              // If composition update fails, skip this component
              logger.debug("Inner loop: could not update y[{}] on tray {}", j, i);
            }
          }
        }
      }
    }

    return tempResidual / Math.max(1, numberOfTrays);
  }

  /**
   * Solve the column using a simple Broyden mixing of tray temperatures.
   *
   * @param id calculation identifier
   */
  public void runBroyden(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = feedStreams.keySet().stream().min(Integer::compareTo).get();

    if (bottomTrayPressure < 0) {
      bottomTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }
    if (topTrayPressure < 0) {
      topTrayPressure = getTray(firstFeedTrayNumber).getStream(0).getPressure();
    }

    double dp = 0.0;
    if (numberOfTrays > 1) {
      dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
    }
    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setPressure(bottomTrayPressure - i * dp);
    }

    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;

    double[] oldtemps = new double[numberOfTrays];
    double[] oldDelta = new double[numberOfTrays];
    double[] delta = new double[numberOfTrays];

    trays.get(firstFeedTrayNumber).run(id);

    long startTime = System.nanoTime();

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int polishIterationLimit = baseIterationLimit
        + Math.max(POLISH_ITERATION_MARGIN, (int) Math.ceil(0.5 * numberOfTrays));
    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    double polishTempTolerance = Math.min(baseTempTolerance, TEMPERATURE_POLISH_TARGET);
    double polishMassTolerance = Math.min(baseMassTolerance, MASS_POLISH_TARGET);
    double polishEnergyTolerance = Math.min(baseEnergyTolerance, ENERGY_POLISH_TARGET);
    boolean polishing = false;
    double monotonicBaseline = Double.POSITIVE_INFINITY;
    boolean massEnergyEvaluated = false;
    int balanceCheckStride = Math.max(3, numberOfTrays / 2);

    while (iter < iterationLimit) {
      iter++;
      err = 0.0;
      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream1 = trays.get(i - 1).getNumberOfInputStreams() - 1;
        trays.get(i - 1).replaceStream(replaceStream1, trays.get(i).getLiquidOutStream());
        trays.get(i - 1).run(id);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
      trays.get(0).run(id);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        trays.get(i).replaceStream(replaceStream, trays.get(i - 1).getGasOutStream());
        trays.get(i).run(id);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        trays.get(i).replaceStream(replaceStream, trays.get(i + 1).getLiquidOutStream());
        trays.get(i).run(id);
      }

      for (int i = 0; i < numberOfTrays; i++) {
        delta[i] = trays.get(i).getThermoSystem().getTemperature() - oldtemps[i];
        double newTemp = oldtemps[i] + delta[i] + 0.3 * (delta[i] - oldDelta[i]);
        trays.get(i).setTemperature(newTemp);
        oldDelta[i] = delta[i];
        err += Math.abs(newTemp - oldtemps[i]);
      }

      boolean evaluateBalances = shouldEvaluateBalances(iter, iterationLimit, polishing, err,
          baseTempTolerance, balanceCheckStride);
      if (evaluateBalances || !massEnergyEvaluated) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        massEnergyEvaluated = true;
      }

      logger.debug("error iteration = " + iter + "   err = " + err + " massErr= " + massErr
          + " energyErr= " + energyErr);

      boolean improved = err < monotonicBaseline;
      monotonicBaseline = err;
      if (!improved) {
        break;
      }

      boolean withinBaseTolerance = err <= baseTempTolerance && massErr <= baseMassTolerance
          && energyErr <= baseEnergyTolerance;

      if (withinBaseTolerance) {
        boolean polishingAvailable =
            polishMassTolerance < baseMassTolerance || polishEnergyTolerance < baseEnergyTolerance
                || polishTempTolerance < baseTempTolerance;

        if (!polishing && polishingAvailable
            && (massErr > polishMassTolerance || energyErr > polishEnergyTolerance)) {
          polishing = true;
          iterationLimit = Math.max(iterationLimit, polishIterationLimit);
          monotonicBaseline = Double.POSITIVE_INFINITY;
          continue;
        }

        double tempTarget = polishing ? polishTempTolerance : baseTempTolerance;
        double massTarget = polishing ? polishMassTolerance : baseMassTolerance;
        double energyTarget = polishing ? polishEnergyTolerance : baseEnergyTolerance;

        if (err <= tempTarget && massErr <= massTarget && energyErr <= energyTarget) {
          break;
        }
      }
    }

    double totalFeedFlowBroyden = 0.0;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface f : feeds) {
        totalFeedFlowBroyden += Math.abs(f.getFlowRate("kg/hr"));
      }
    }

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
    hasBeenSolvedBefore = true;
    lastTotalFeedFlow = totalFeedFlowBroyden;
  }

  /**
   * Solve the column using Wegstein acceleration of successive substitution.
   *
   * <p>
   * Wegstein's method uses two consecutive fixed-point iterates to extrapolate a better estimate.
   * For temperatures on each tray the acceleration factor q is computed from the slope of the
   * fixed-point map: q = s / (s - 1) where s = (x_{k} - x_{k-1}) / (g(x_{k}) - g(x_{k-1})). The
   * factor is bounded to [-5, 0] to prevent divergence.
   * </p>
   *
   * @param id calculation identifier
   */
  void solveWegstein(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    if (useGuardedWegsteinFallback()) {
      solveDampedSubstitution(id);
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;

    long startTime = System.nanoTime();

    // Wegstein requires two consecutive iterates to estimate the slope.
    // prevInput[i] = x_{k-1}, prevOutput[i] = g(x_{k-1})
    double[] prevInput = new double[numberOfTrays];
    double[] prevOutput = new double[numberOfTrays];
    boolean wegsteinReady = false;

    for (int i = 0; i < numberOfTrays; i++) {
      prevInput[i] = trays.get(i).getThermoSystem().getTemperature();
    }

    trays.get(firstFeedTrayNumber).run(id);

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int overflowIncrement = Math.max(3, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowBand = Math.max(overflowIncrement, numberOfTrays);
    int maxIterationLimit = Math.max(iterationLimit, maxNumberOfIterations)
        + overflowBand * ITERATION_OVERFLOW_MULTIPLIER;
    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    boolean massEnergyEvaluated = false;
    int balanceCheckStride = Math.max(3, numberOfTrays / 2);
    // Warm-up: use direct substitution for first few iterations
    int warmUpIterations = Math.max(2, numberOfTrays / 3);

    while (iter < iterationLimit) {
      iter++;

      double[] xk = new double[numberOfTrays];
      for (int i = 0; i < numberOfTrays; i++) {
        xk[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Standard tray sweep (same as direct substitution)
      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream = trays.get(i - 1).getNumberOfInputStreams() - 1;
        trays.get(i - 1).replaceStream(replaceStream, trays.get(i).getLiquidOutStream());
        trays.get(i - 1).run(id);
        applyMurphreeCorrection(i - 1);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
      trays.get(0).run(id);
      applyMurphreeCorrection(0);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        trays.get(i).replaceStream(replaceStream, trays.get(i - 1).getGasOutStream());
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        trays.get(i).replaceStream(replaceStream, trays.get(i + 1).getLiquidOutStream());
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      // Compute g(x_k) = direct substitution output
      double[] gxk = new double[numberOfTrays];
      for (int i = 0; i < numberOfTrays; i++) {
        gxk[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Apply Wegstein acceleration after warm-up period
      double temperatureResidual = 0.0;
      for (int i = 0; i < numberOfTrays; i++) {
        double newTemp;
        if (wegsteinReady && iter > warmUpIterations) {
          double denominator = (xk[i] - prevInput[i]);
          if (Math.abs(denominator) > 1.0e-10) {
            double s = (gxk[i] - prevOutput[i]) / denominator;
            double q = s / (s - 1.0);
            // Bound q conservatively: [-2, 0] to avoid oscillation
            q = Math.max(-2.0, Math.min(0.0, q));
            double candidate = (1.0 - q) * gxk[i] + q * xk[i];
            // Safeguard: limit step size to avoid overshooting
            double maxStep = 30.0;
            if (Math.abs(candidate - xk[i]) > maxStep) {
              candidate = xk[i] + Math.signum(candidate - xk[i]) * maxStep;
            }
            newTemp = candidate;
          } else {
            newTemp = gxk[i];
          }
        } else {
          newTemp = gxk[i]; // Direct substitution during warm-up
        }

        // Store for next iteration
        prevOutput[i] = gxk[i];
        prevInput[i] = xk[i];

        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - xk[i]);
      }
      wegsteinReady = true;
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      boolean evaluateBalances = shouldEvaluateBalances(iter, iterationLimit, false, err,
          baseTempTolerance, balanceCheckStride);
      if (evaluateBalances || !massEnergyEvaluated) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        massEnergyEvaluated = true;
      }

      if (convergenceHistory != null) {
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      logger.debug("Wegstein iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
          energyErr);

      boolean energyWithinBase = !enforceEnergyBalanceTolerance || energyErr <= baseEnergyTolerance;
      if (err <= baseTempTolerance && massErr <= baseMassTolerance && energyWithinBase) {
        break;
      }

      if (iter >= iterationLimit && err > baseTempTolerance && iterationLimit < maxIterationLimit) {
        iterationLimit = Math.min(maxIterationLimit, iterationLimit + overflowIncrement);
        continue;
      }
    }

    synchronizeTrayStreamsAfterAcceleratedTemperatureUpdate(id, firstFeedTrayNumber);
    massErr = getMassBalanceError();
    energyErr = getEnergyBalanceError();

    if (!Double.isFinite(err) || !Double.isFinite(energyErr) || err > baseTempTolerance
        || massErr > baseMassTolerance) {
      logger.warn("Wegstein did not converge cleanly for column {}. Falling back to damped "
          + "substitution.", getName());
      solveDampedFallbackFromFreshInitialization(id);
      return;
    }

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
  }

  /**
   * Recompute tray outlet streams after an accelerated temperature update.
   *
   * @param id calculation identifier
   * @param firstFeedTrayNumber index of the lowest feed tray
   */
  private void synchronizeTrayStreamsAfterAcceleratedTemperatureUpdate(UUID id,
      int firstFeedTrayNumber) {
    StreamInterface[] previousGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] previousLiquidStreams = new StreamInterface[numberOfTrays];
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      previousGasStreams[trayIndex] = trays.get(trayIndex).getGasOutStream().clone();
      previousLiquidStreams[trayIndex] = trays.get(trayIndex).getLiquidOutStream().clone();
    }
    performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
  }

  /**
   * Rerun damped substitution from a fresh tray initialization after an accelerator fails.
   *
   * @param id calculation identifier
   */
  private void solveDampedFallbackFromFreshInitialization(UUID id) {
    boolean originalInitializationFlag = doInitializion;
    doInitializion = true;
    lastSolverTypeUsed = SolverType.DAMPED_SUBSTITUTION;
    solveDampedSubstitution(id);
    doInitializion = originalInitializationFlag;
  }

  /**
   * Rerun damped substitution after an accelerator throws during a solver adapter call.
   *
   * @param id calculation identifier
   * @param exception exception that caused the accelerator to be rejected
   */
  void solveDampedFallbackAfterAcceleratorFailure(UUID id, RuntimeException exception) {
    logger.warn("Accelerated solver failed for column {}. Falling back to damped substitution.",
        getName(), exception);
    solveDampedFallbackFromFreshInitialization(id);
  }

  /**
   * Rerun damped substitution after an accelerator returns a rejected state.
   *
   * @param id calculation identifier
   * @param reason reason the accelerator result was rejected
   */
  void solveDampedFallbackAfterRejectedAccelerator(UUID id, String reason) {
    logger.warn("Accelerated solver result rejected for column {}: {}. Falling back to damped "
        + "substitution.", getName(), reason);
    solveDampedFallbackFromFreshInitialization(id);
  }

  /**
   * Decide whether Wegstein acceleration should be routed to the guarded damped solver.
   *
   * @return {@code true} while the Wegstein accelerator is guarded by damped substitution
   */
  private boolean useGuardedWegsteinFallback() {
    return false;
  }

  /**
   * Decide whether sum-rates acceleration should be routed to the guarded damped solver.
   *
   * @return {@code true} when the sum-rates accelerator should be guarded by damped substitution
   */
  private boolean useGuardedSumRatesFallback() {
    return hasCondenser || hasReboiler;
  }

  /**
   * Decide whether temperature-Newton acceleration should be routed to the guarded damped solver.
   *
   * @return {@code true} while the Newton accelerator is guarded by damped substitution
   */
  private boolean useGuardedNewtonFallback() {
    return false;
  }

  /**
   * Solve the column using a sum-rates tearing method.
   *
   * <p>
   * The sum-rates method adjusts tray liquid flow rates based on the ratio of computed to assumed
   * total flow leaving each tray. This is effective for absorber and stripper columns where the
   * temperature profile is relatively flat. The method alternates between: (1) bubble-point
   * temperature calculations on each tray, and (2) flow rate corrections using the sum-rates
   * formula of Burningham and Otto (1967).
   * </p>
   *
   * @param id calculation identifier
   */
  void solveSumRates(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    if (useGuardedSumRatesFallback()) {
      markSolverTypeUsed(SolverType.DAMPED_SUBSTITUTION);
      solveDampedSubstitution(id);
      if (lastSolveStatus == SolveStatus.RIGOROUS_CONVERGED
          || lastSolveStatus == SolveStatus.RECONCILED_PRODUCTS) {
        setLastSolveStatus(lastSolveStatus,
            "Sum-rates is guarded to damped substitution for columns with condenser/reboiler "
                + "energy equipment");
      }
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;

    long startTime = System.nanoTime();
    double[] oldtemps = new double[numberOfTrays];

    trays.get(firstFeedTrayNumber).run(id);

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    boolean massEnergyEvaluated = false;
    int balanceCheckStride = Math.max(3, numberOfTrays / 2);

    double relaxation = Math.max(minSequentialRelaxation, Math.min(maxAdaptiveRelaxation, 0.7));

    while (iter < iterationLimit) {
      iter++;

      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Standard tray-by-tray sweep
      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream = trays.get(i - 1).getNumberOfInputStreams() - 1;
        trays.get(i - 1).replaceStream(replaceStream, trays.get(i).getLiquidOutStream());
        trays.get(i - 1).run(id);
        applyMurphreeCorrection(i - 1);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      trays.get(0).replaceStream(streamNumb, trays.get(1).getLiquidOutStream());
      trays.get(0).run(id);
      applyMurphreeCorrection(0);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        trays.get(i).replaceStream(replaceStream, trays.get(i - 1).getGasOutStream());
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        trays.get(i).replaceStream(replaceStream, trays.get(i + 1).getLiquidOutStream());
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      // Sum-rates flow correction: adjust tray temperatures with flow-weighted
      // damping
      double totalFlowRatio = 0.0;
      int countTrays = 0;
      for (int i = 0; i < numberOfTrays; i++) {
        double vaporOut = trays.get(i).getGasOutStream().getFlowRate("kg/hr");
        double liquidOut = trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
        double totalOut = vaporOut + liquidOut;
        double totalIn = 0.0;
        for (int j = 0; j < trays.get(i).getNumberOfInputStreams(); j++) {
          totalIn += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
        }
        if (totalIn > 1e-12) {
          totalFlowRatio += totalOut / totalIn;
          countTrays++;
        }
      }
      double avgFlowRatio = countTrays > 0 ? totalFlowRatio / countTrays : 1.0;
      double flowCorrection = Math.max(0.5, Math.min(1.5, 1.0 / avgFlowRatio));

      double temperatureResidual = 0.0;
      double effectiveRelaxation = relaxation * flowCorrection;
      effectiveRelaxation = Math.max(minTemperatureRelaxation, Math.min(1.0, effectiveRelaxation));
      for (int i = 0; i < numberOfTrays; i++) {
        double updated = trays.get(i).getThermoSystem().getTemperature();
        double newTemp = oldtemps[i] + effectiveRelaxation * (updated - oldtemps[i]);
        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - oldtemps[i]);
      }
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      boolean evaluateBalances = shouldEvaluateBalances(iter, iterationLimit, false, err,
          baseTempTolerance, balanceCheckStride);
      if (evaluateBalances || !massEnergyEvaluated) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        massEnergyEvaluated = true;
      }

      if (convergenceHistory != null) {
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      logger.debug("sum-rates iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
          energyErr);

      boolean energyWithinBase = !enforceEnergyBalanceTolerance || energyErr <= baseEnergyTolerance;
      if (err <= baseTempTolerance && massErr <= baseMassTolerance && energyWithinBase) {
        break;
      }
    }

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
  }

  /**
   * Solve the column using a Newton-Raphson simultaneous temperature correction method.
   *
   * <p>
   * This is inspired by the Naphtali-Sandholm (1971) approach of solving MESH equations
   * simultaneously, adapted to NeqSim's tray-by-tray flash infrastructure. The method treats the N
   * tray temperatures as the independent variables. A residual vector is formed by running full
   * tray sweeps and measuring the temperature discrepancy each tray exhibits after equilibrium. The
   * Jacobian is computed by finite-difference perturbation of each tray temperature.
   * </p>
   *
   * <p>
   * Key features:
   * <ul>
   * <li>Simultaneous correction: all tray temperatures are updated together using a dense N×N
   * Jacobian solved by Gaussian elimination with partial pivoting.</li>
   * <li>Line search: the full Newton step is scaled back if it increases residuals.</li>
   * <li>Warm-up: a few direct-substitution iterations are performed first to get close to the
   * solution basin where Newton convergence is quadratic.</li>
   * </ul>
   *
   * @param id calculation identifier
   */
  void solveNewton(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    if (useGuardedNewtonFallback()) {
      solveDampedSubstitution(id);
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      solveSingleTray(id);
      return;
    }

    if (isDoInitializion()) {
      this.init();
    }

    long startTime = System.nanoTime();
    err = 1.0e10;
    int iter = 0;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;

    double baseTempTolerance = getEffectiveTemperatureTolerance();
    double baseMassTolerance = getEffectiveMassBalanceTolerance();
    double baseEnergyTolerance = getEffectiveEnthalpyBalanceTolerance();
    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = Math.max(baseIterationLimit, maxNumberOfIterations);
    int maxIterationLimit =
        iterationLimit + Math.max(numberOfTrays, 3) * ITERATION_OVERFLOW_MULTIPLIER;

    // Warm-up: run a few direct substitution iterations to establish a reasonable
    // profile
    int warmUpIterations = Math.min(3, iterationLimit / 3);
    trays.get(firstFeedTrayNumber).run(id);
    StreamInterface[] previousGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] previousLiquidStreams = new StreamInterface[numberOfTrays];

    for (int w = 0; w < warmUpIterations; w++) {
      iter++;
      double[] warmUpTemperatures = captureTrayTemperatures();
      performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
      double tempRes = computeTemperatureResidual(warmUpTemperatures);
      err = tempRes;

      if (convergenceHistory != null) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      logger.debug("newton warm-up iteration {} tempErr={}", iter, err);
      if (err < baseTempTolerance) {
        break;
      }
    }

    // Newton iterations
    double perturbation = 0.1; // temperature perturbation for Jacobian (K)
    double[] temperatures = new double[numberOfTrays];
    double[] residuals = new double[numberOfTrays];
    double[][] jacobian = new double[numberOfTrays][numberOfTrays];

    // Line-search damping memo: cache the last successful step length so the
    // next iteration can start probing at min(1.0, 2.0 * lastSuccessful) rather
    // than always at 1.0. Each skipped probe saves one full tray sweep — the
    // dominant cost of the line search. A periodic reset every LINESEARCH_RESET_PERIOD
    // iterations re-tries the full step so the algorithm can recover quickly
    // when nonlinearity eases.
    double lastSuccessfulStepLength = 1.0;
    final int LINESEARCH_RESET_PERIOD = 4;

    while (iter < iterationLimit) {
      iter++;

      // Save current temperatures
      for (int i = 0; i < numberOfTrays; i++) {
        temperatures[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      // Compute base residuals: run a full sweep at current temperatures,
      // residual = (post-sweep temperature) - (pre-sweep temperature)
      performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
      for (int i = 0; i < numberOfTrays; i++) {
        residuals[i] = trays.get(i).getThermoSystem().getTemperature() - temperatures[i];
      }

      // Check if already converged
      double normRes = 0.0;
      for (int i = 0; i < numberOfTrays; i++) {
        normRes += Math.abs(residuals[i]);
      }
      normRes /= Math.max(1, numberOfTrays);
      err = normRes;

      massErr = getMassBalanceError();
      energyErr = getEnergyBalanceError();

      if (convergenceHistory != null) {
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      logger.debug("newton iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
          energyErr);

      boolean energyOk = !enforceEnergyBalanceTolerance || energyErr <= baseEnergyTolerance;
      if (err <= baseTempTolerance && massErr <= baseMassTolerance && energyOk) {
        break;
      }

      // Compute Jacobian by finite differences with banded structure
      // J[i][j] = d(residual_i) / d(T_j)
      // Distillation Jacobians are near-tridiagonal: tray i is primarily affected by
      // trays i-1, i, i+1. For columns with > 6 trays, exploit this sparsity by only
      // perturbing trays within a half-bandwidth of each row.
      int halfBand = numberOfTrays <= 6 ? numberOfTrays : Math.max(2, numberOfTrays / 4);

      // Zero the Jacobian — entries outside the band stay zero
      for (int i = 0; i < numberOfTrays; i++) {
        for (int jj = 0; jj < numberOfTrays; jj++) {
          jacobian[i][jj] = 0.0;
        }
      }

      // Determine which columns actually need perturbation.
      // A Jacobian column J[:,j] only contributes to the Newton step through rows i
      // whose residual is non-negligible. If every row in the band around column j is
      // already at or below the temperature tolerance, the corresponding column has
      // no useful information and we can skip its perturbation sweep entirely. Each
      // skipped column saves one full tray sweep — the dominant cost of Newton.
      boolean[] needsPerturb = new boolean[numberOfTrays];
      double residualSkipThreshold = 0.5 * baseTempTolerance;
      for (int j = 0; j < numberOfTrays; j++) {
        if (numberOfTrays <= 6) {
          needsPerturb[j] = true;
        } else {
          int rowStart = Math.max(0, j - halfBand);
          int rowEnd = Math.min(numberOfTrays - 1, j + halfBand);
          double bandResidualMax = 0.0;
          for (int i = rowStart; i <= rowEnd; i++) {
            double r = Math.abs(residuals[i]);
            if (r > bandResidualMax) {
              bandResidualMax = r;
            }
          }
          // Always perturb the diagonal column itself, even if its own residual is
          // tight — the rest of the band may still couple through off-diagonal entries
          // on the next iteration. The skip only fires when the entire band is tight.
          needsPerturb[j] = bandResidualMax > residualSkipThreshold;
        }
      }

      for (int j = 0; j < numberOfTrays; j++) {
        if (!needsPerturb[j]) {
          continue;
        }

        // Reset temperatures to base state
        for (int i = 0; i < numberOfTrays; i++) {
          trays.get(i).setTemperature(temperatures[i]);
          trays.get(i).getThermoSystem().setTemperature(temperatures[i]);
        }

        // Perturb tray j
        double pertT = temperatures[j] + perturbation;
        trays.get(j).setTemperature(pertT);
        trays.get(j).getThermoSystem().setTemperature(pertT);

        // Run sweep with perturbed temperature
        performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams,
            1.0);

        // Compute perturbed residuals — only for rows within band of column j
        int rowStart = numberOfTrays <= 6 ? 0 : Math.max(0, j - halfBand);
        int rowEnd =
            numberOfTrays <= 6 ? numberOfTrays - 1 : Math.min(numberOfTrays - 1, j + halfBand);
        for (int i = rowStart; i <= rowEnd; i++) {
          double pertResidual = trays.get(i).getThermoSystem().getTemperature() - temperatures[i];
          if (j == i) {
            pertResidual = trays.get(i).getThermoSystem().getTemperature() - pertT;
          }
          jacobian[i][j] = (pertResidual - residuals[i]) / perturbation;
        }
      }

      // For the Newton correction, we want to solve: J * deltaT = -residuals
      // where residuals = f(T) = T_sweep - T_current (the function maps T_k ->
      // T_{k+1})
      // The fixed-point is T* such that f(T*) = 0
      // Newton step: deltaT = -J^{-1} * residuals, but since we want T such that
      // f(T)=0
      // and our Jacobian approximates df/dT, we solve: (J - I) * deltaT = -residuals
      // Because the true Jacobian of g(T) = T + f(T) is I + J_f, and Newton on g(T) =
      // T
      // means (I + J_f - I) * deltaT = -(T + f(T) - T) => J_f * deltaT = -f(T)

      // Actually, residuals[i] = T_new[i] - T_old[i] is already f(T) = g(T) - T
      // The Jacobian J[i][j] = df_i/dT_j ≈ (f_perturbed - f_base) / dT_j
      // Newton seeks f(T) = 0, so: deltaT = -J^{-1} * f(T)

      // Solve J * deltaT = -residuals using Gaussian elimination with partial
      // pivoting
      double[] rhs = new double[numberOfTrays];
      for (int i = 0; i < numberOfTrays; i++) {
        rhs[i] = -residuals[i];
      }

      double[] deltaT = solveLinearSystem(jacobian, rhs);

      if (deltaT == null || !isFiniteVector(deltaT)) {
        // Singular Jacobian — fall back to direct substitution step
        logger.warn("Newton: singular Jacobian at iter {}, using direct substitution step", iter);
        for (int i = 0; i < numberOfTrays; i++) {
          trays.get(i).setTemperature(temperatures[i] + 0.5 * residuals[i]);
          trays.get(i).getThermoSystem().setTemperature(temperatures[i] + 0.5 * residuals[i]);
        }
        // Reset damping memo: the Jacobian was bad, so prior step length is not a reliable hint.
        lastSuccessfulStepLength = 1.0;
        continue;
      }

      // Line search: try full Newton step, halve if residual increases.
      // Damping memo: start at min(1.0, 2.0 * lastSuccessful) to skip probes that
      // would predictably fail given prior nonlinearity. Periodically reset to 1.0
      // so the algorithm can recover the full step when conditions improve.
      double trialStart = (iter % LINESEARCH_RESET_PERIOD == 0) ? 1.0
          : Math.min(1.0, 2.0 * lastSuccessfulStepLength);
      double bestStepLength = trialStart;
      double bestNormRes = normRes;
      for (double stepLength = trialStart; stepLength >= 0.125; stepLength *= 0.5) {
        // Apply trial step
        for (int i = 0; i < numberOfTrays; i++) {
          double newTemp = temperatures[i] + stepLength * deltaT[i];
          // Safeguard: keep temperatures reasonable
          newTemp = Math.max(50.0, Math.min(1000.0, newTemp));
          trays.get(i).setTemperature(newTemp);
          trays.get(i).getThermoSystem().setTemperature(newTemp);
        }

        // Check trial step quality with a sweep
        performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams,
            1.0);

        double trialNormRes = 0.0;
        for (int i = 0; i < numberOfTrays; i++) {
          double trialRes = trays.get(i).getThermoSystem().getTemperature() - temperatures[i]
              - stepLength * deltaT[i];
          trialNormRes += Math.abs(trialRes);
        }
        trialNormRes /= Math.max(1, numberOfTrays);

        if (trialNormRes < bestNormRes) {
          bestStepLength = stepLength;
          bestNormRes = trialNormRes;
          break; // Accept first improving step
        }
      }

      // Apply the best step
      if (bestStepLength < 1.0) {
        // Need to re-apply since the loop may have tried smaller steps
        for (int i = 0; i < numberOfTrays; i++) {
          double newTemp = temperatures[i] + bestStepLength * deltaT[i];
          newTemp = Math.max(50.0, Math.min(1000.0, newTemp));
          trays.get(i).setTemperature(newTemp);
          trays.get(i).getThermoSystem().setTemperature(newTemp);
        }
      }

      // Update damping memo for next iteration's line-search start point.
      lastSuccessfulStepLength = bestStepLength;

      logger.debug("newton iteration {} step={} normRes={}->{}", iter, bestStepLength, normRes,
          bestNormRes);

      // Overflow: extend limit if not converged
      if (iter >= iterationLimit && err > baseTempTolerance && iterationLimit < maxIterationLimit) {
        iterationLimit = Math.min(maxIterationLimit, iterationLimit + 3);
      }
    }

    // Final sweep to ensure consistent tray state
    double[] finalTemperatures = captureTrayTemperatures();
    performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
    err = computeTemperatureResidual(finalTemperatures);
    massErr = getMassBalanceError();
    energyErr = getEnergyBalanceError();

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
  }

  /**
   * Perform a full upward+downward tray sweep, running PH-flash on each tray.
   *
   * @param id calculation identifier
   * @param firstFeedTrayNumber index of the lowest feed tray
   * @param previousGasStreams cached gas streams from previous iteration (updated in-place)
   * @param previousLiquidStreams cached liquid streams from previous iteration (updated in-place)
   * @param relaxation relaxation factor for stream blending
   */
  private void performFullTraySweep(UUID id, int firstFeedTrayNumber,
      StreamInterface[] previousGasStreams, StreamInterface[] previousLiquidStreams,
      double relaxation) {
    // Downward liquid sweep: feed → reboiler
    for (int stage = firstFeedTrayNumber; stage >= 1; stage--) {
      int target = stage - 1;
      int replaceStream = trays.get(target).getNumberOfInputStreams() - 1;
      StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[stage],
          trays.get(stage).getLiquidOutStream(), relaxation);
      trays.get(target).replaceStream(replaceStream, relaxedLiquid);
      previousLiquidStreams[stage] = relaxedLiquid.clone();
      trays.get(target).run(id);
      applyMurphreeCorrection(target);
    }

    // Upward vapor sweep: reboiler → condenser
    for (int stage = 1; stage <= numberOfTrays - 1; stage++) {
      int replaceStream = trays.get(stage).getNumberOfInputStreams() - 2;
      if (stage == (numberOfTrays - 1)) {
        replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
      }
      StreamInterface relaxedGas = applyRelaxation(previousGasStreams[stage - 1],
          trays.get(stage - 1).getGasOutStream(), relaxation);
      trays.get(stage).replaceStream(replaceStream, relaxedGas);
      previousGasStreams[stage - 1] = relaxedGas.clone();
      trays.get(stage).run(id);
      applyMurphreeCorrection(stage);
    }
  }

  /**
   * Compute the average absolute temperature residual across all trays. The residual is the
   * difference between the tray's stored temperature and its thermo system temperature after a
   * flash.
   *
   * @return average absolute temperature change per tray (K)
   */
  private double computeTemperatureResidual() {
    return computeTemperatureResidual(captureStoredTrayTemperatures());
  }

  /**
   * Capture current tray thermodynamic temperatures.
   *
   * @return current tray thermodynamic temperatures in Kelvin
   */
  private double[] captureTrayTemperatures() {
    double[] temperatures = new double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      temperatures[i] = trays.get(i).getThermoSystem().getTemperature();
    }
    return temperatures;
  }

  /**
   * Capture tray stored temperatures.
   *
   * @return tray stored temperatures in Kelvin
   */
  private double[] captureStoredTrayTemperatures() {
    double[] temperatures = new double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      temperatures[i] = trays.get(i).getTemperature();
    }
    return temperatures;
  }

  /**
   * Compute average temperature change relative to a reference profile.
   *
   * @param referenceTemperatures reference tray temperatures in Kelvin
   * @return average absolute temperature change in Kelvin
   */
  private double computeTemperatureResidual(double[] referenceTemperatures) {
    double residual = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      residual +=
          Math.abs(trays.get(i).getThermoSystem().getTemperature() - referenceTemperatures[i]);
    }
    return residual / Math.max(1, numberOfTrays);
  }

  /**
   * Solve a dense linear system Ax = b using Gaussian elimination with partial pivoting.
   *
   * @param matrixA coefficient matrix (will be modified in-place)
   * @param vectorB right-hand side vector (will be modified in-place)
   * @return solution vector x, or null if the matrix is singular
   */
  private double[] solveLinearSystem(double[][] matrixA, double[] vectorB) {
    int n = vectorB.length;
    // Create copies to avoid modifying the originals from the caller's perspective
    double[][] a = new double[n][n];
    double[] b = new double[n];
    for (int i = 0; i < n; i++) {
      b[i] = vectorB[i];
      if (!Double.isFinite(b[i])) {
        return null;
      }
      for (int j = 0; j < n; j++) {
        a[i][j] = matrixA[i][j];
        if (!Double.isFinite(a[i][j])) {
          return null;
        }
      }
    }

    // Forward elimination with partial pivoting
    for (int k = 0; k < n; k++) {
      // Find pivot
      int maxRow = k;
      double maxVal = Math.abs(a[k][k]);
      for (int i = k + 1; i < n; i++) {
        if (Math.abs(a[i][k]) > maxVal) {
          maxVal = Math.abs(a[i][k]);
          maxRow = i;
        }
      }

      if (!Double.isFinite(maxVal) || maxVal < 1e-30) {
        return null; // Singular matrix
      }

      // Swap rows
      if (maxRow != k) {
        double[] tempRow = a[k];
        a[k] = a[maxRow];
        a[maxRow] = tempRow;
        double tempB = b[k];
        b[k] = b[maxRow];
        b[maxRow] = tempB;
      }

      // Eliminate
      for (int i = k + 1; i < n; i++) {
        double factor = a[i][k] / a[k][k];
        for (int j = k + 1; j < n; j++) {
          a[i][j] -= factor * a[k][j];
        }
        b[i] -= factor * b[k];
        a[i][k] = 0.0;
      }
    }

    // Back substitution
    double[] x = new double[n];
    for (int i = n - 1; i >= 0; i--) {
      double sum = b[i];
      for (int j = i + 1; j < n; j++) {
        sum -= a[i][j] * x[j];
      }
      x[i] = sum / a[i][i];
      if (!Double.isFinite(x[i])) {
        return null;
      }
    }
    return x;
  }

  /**
   * Check whether all values in an array are finite.
   *
   * @param values values to inspect
   * @return {@code true} when every entry is finite
   */
  private boolean isFiniteVector(double[] values) {
    for (int i = 0; i < values.length; i++) {
      if (!Double.isFinite(values[i])) {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  @ExcludeFromJacocoGeneratedReport
  public void displayResult() {
    distoperations.displayResult();
  }

  /**
   * <p>
   * getTray.
   * </p>
   *
   * @param trayNumber a int
   * @return a {@link neqsim.process.equipment.distillation.SimpleTray} object
   */
  public SimpleTray getTray(int trayNumber) {
    return trays.get(trayNumber);
  }

  /** {@inheritDoc} */
  @Override
  public void setNumberOfTrays(int number) {
    int oldNumberOfTrays = numberOfTrays;
    int tempNumberOfTrays = number;
    if (hasReboiler) {
      tempNumberOfTrays++;
    }
    if (hasCondenser) {
      tempNumberOfTrays++;
    }
    int change = tempNumberOfTrays - oldNumberOfTrays;
    if (change > 0) {
      for (int i = 0; i < change; i++) {
        trays.add(1,
            createMiddleTray("SimpleTray" + (oldNumberOfTrays + i + 1), oldNumberOfTrays + i));
      }
    } else if (change < 0) {
      for (int i = 0; i > change; i--) {
        trays.remove(1);
      }
    }
    numberOfTrays = tempNumberOfTrays;
    setDoInitializion(true);
    init();
  }

  /**
   * Create a middle tray (between reboiler and condenser). Sets the reactive flash flag when the
   * column is in reactive mode and the tray index falls inside the reactive section.
   *
   * @param name the tray name
   * @param middleTrayIndex 0-based index among the middle trays (excluding reboiler/condenser)
   * @return a new SimpleTray with reactive flash configured
   */
  private SimpleTray createMiddleTray(String name, int middleTrayIndex) {
    SimpleTray tray = new SimpleTray(name);
    if (reactive && isInReactiveSection(middleTrayIndex)) {
      tray.setUseReactiveFlash(true);
    }
    return tray;
  }

  /**
   * Check whether a middle-tray index falls inside the reactive section.
   *
   * @param middleTrayIndex 0-based index among middle trays
   * @return {@code true} when the tray should use reactive flash
   */
  private boolean isInReactiveSection(int middleTrayIndex) {
    if (reactiveStartTray < 0 || reactiveEndTray < 0) {
      return true; // all middle trays are reactive
    }
    return middleTrayIndex >= reactiveStartTray && middleTrayIndex <= reactiveEndTray;
  }

  /**
   * Enable or disable reactive distillation for all middle trays. When enabled, middle trays use
   * {@link ReactiveTray} (simultaneous chemical + phase equilibrium via the Modified RAND method).
   * Can be called after construction; existing trays will be replaced.
   *
   * @param reactive {@code true} to enable reactive distillation
   */
  public void setReactive(boolean reactive) {
    this.reactive = reactive;
    this.reactiveStartTray = -1;
    this.reactiveEndTray = -1;
    replaceMiddleTrays();
  }

  /**
   * Enable reactive distillation on a specific section of middle trays. Tray indices are 0-based
   * among the middle trays (excluding reboiler/condenser). For example, in a column with reboiler +
   * 10 middle trays + condenser, {@code setReactive(true, 3, 7)} makes trays 4–8 (1-based) of the
   * middle section reactive.
   *
   * @param reactive {@code true} to enable reactive distillation
   * @param startTray first reactive middle-tray index (0-based, inclusive)
   * @param endTray last reactive middle-tray index (0-based, inclusive)
   */
  public void setReactive(boolean reactive, int startTray, int endTray) {
    this.reactive = reactive;
    this.reactiveStartTray = startTray;
    this.reactiveEndTray = endTray;
    replaceMiddleTrays();
  }

  /**
   * Update the reactive flash flag on middle trays to match the current reactive mode
   * configuration. Called automatically by {@link #setReactive}.
   */
  private void replaceMiddleTrays() {
    int start = hasReboiler ? 1 : 0;
    int end = hasCondenser ? trays.size() - 1 : trays.size();
    for (int i = start; i < end; i++) {
      int middleIndex = i - start;
      boolean shouldBeReactive = reactive && isInReactiveSection(middleIndex);
      trays.get(i).setUseReactiveFlash(shouldBeReactive);
    }
  }

  /**
   * Check whether reactive distillation mode is enabled.
   *
   * @return {@code true} when the column has reactive trays
   */
  public boolean isReactive() {
    return reactive;
  }

  /**
   * Select the algorithm used when solving the column.
   *
   * @param solverType choice of solver, or {@code null} to restore direct substitution
   */
  public void setSolverType(SolverType solverType) {
    this.solverType = solverType == null ? SolverType.DIRECT_SUBSTITUTION : solverType;
    this.lastSolverTypeUsed =
        this.solverType == SolverType.AUTO ? SolverType.DIRECT_SUBSTITUTION : this.solverType;
  }

  /**
   * Enable or disable the post-success damped-substitution verification run for accelerated solvers
   * (Wegstein, Sum-Rates, Naphtali-Sandholm, MESH residual). Off by default. When enabled every
   * successful accelerated solve is double-checked against a fresh damped-substitution solve on a
   * column clone; if product flows differ by more than 2&nbsp;% the damped result is accepted. The
   * verification roughly doubles wallclock time, so it is recommended only for regression auditing.
   *
   * @param enabled {@code true} to verify accelerated results against damped substitution
   */
  public static void setVerifyAcceleratedResults(boolean enabled) {
    ColumnSolverFactory.setVerifyAcceleratedResults(enabled);
  }

  /**
   * Check whether accelerated solver verification is currently enabled.
   *
   * @return {@code true} when accelerated solves are verified against damped substitution
   */
  public static boolean isVerifyAcceleratedResults() {
    return ColumnSolverFactory.isVerifyAcceleratedResults();
  }

  /**
   * Set relaxation factor for the damped solver.
   *
   * @param relaxationFactor value between 0 and 1
   */
  public void setRelaxationFactor(double relaxationFactor) {
    this.relaxationFactor = relaxationFactor;
  }

  /**
   * <p>
   * setTopCondenserDuty.
   * </p>
   *
   * @param duty a double
   */
  public void setTopCondenserDuty(double duty) {
    condenserCoolingDuty = duty;
  }

  /**
   * <p>
   * setTopPressure.
   * </p>
   *
   * @param topPressure a double
   */
  public void setTopPressure(double topPressure) {
    topTrayPressure = topPressure;
  }

  /**
   * Get the configured top tray pressure.
   *
   * @return top tray pressure in bara
   */
  public double getTopPressure() {
    return topTrayPressure;
  }

  /**
   * <p>
   * setBottomPressure.
   * </p>
   *
   * @param bottomPressure a double
   */
  public void setBottomPressure(double bottomPressure) {
    bottomTrayPressure = bottomPressure;
  }

  /**
   * Get the configured bottom tray pressure.
   *
   * @return bottom tray pressure in bara
   */
  public double getBottomPressure() {
    return bottomTrayPressure;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    boolean acceptableStatus = lastSolveStatus == SolveStatus.RIGOROUS_CONVERGED
        || lastSolveStatus == SolveStatus.RECONCILED_PRODUCTS;
    return acceptableStatus && residualConvergenceSatisfied();
  }

  /**
   * Check whether the current residual diagnostics satisfy all active rigorous convergence gates.
   *
   * @return {@code true} when temperature, mass, energy, internal traffic, MESH, and specification
   *         gates are all satisfied
   */
  private boolean residualConvergenceSatisfied() {
    boolean temperatureSolved = err < getEffectiveTemperatureTolerance();
    boolean massSolved = lastMassResidual <= getEffectiveMassBalanceTolerance();
    boolean energySolved = !enforceEnergyBalanceTolerance
        || lastEnergyResidual <= getEffectiveEnthalpyBalanceTolerance();
    return temperatureSolved && massSolved && energySolved && internalTrafficSatisfied()
        && meshResidualsSatisfied() && specificationsSatisfied();
  }

  /**
   * Check whether raw internal tray traffic stayed within the solved-state guard.
   *
   * @return {@code true} if the latest maximum internal traffic ratio is acceptable
   */
  private boolean internalTrafficSatisfied() {
    return Double.isFinite(lastInternalTrafficRatio) && !lastInternalTrafficGuardReached
        && lastInternalTrafficRatio <= MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO;
  }

  /**
   * Check whether the latest MESH residual satisfies the optional convergence gate.
   *
   * @return {@code true} if MESH residual gating is disabled or the latest residual is acceptable
   */
  private boolean meshResidualsSatisfied() {
    if (!isEffectiveMeshResidualToleranceEnforced()) {
      return true;
    }
    if (lastMeshResidual == null) {
      return false;
    }
    return lastMeshResidual.isFinite()
        && lastMeshResidual.getInfinityNorm() <= meshResidualTolerance
        && productDrawResidualsSatisfied();
  }

  /**
   * Check whether the product draw residual satisfies its convergence gate.
   *
   * @return {@code true} when product draws are consistent with terminal tray traffic
   */
  private boolean productDrawResidualsSatisfied() {
    double productDrawResidual = getLastMeshProductDrawResidualNorm();
    return Double.isFinite(productDrawResidual)
        && productDrawResidual <= meshProductDrawResidualTolerance;
  }

  /**
   * Determine whether MESH residuals are currently part of the convergence contract.
   *
   * @return {@code true} if the active convergence gate includes the MESH residual vector
   */
  private boolean isEffectiveMeshResidualToleranceEnforced() {
    if (enforceMeshResidualToleranceCustomized) {
      return enforceMeshResidualTolerance;
    }
    return isResidualGatedSolverType(solverType) || isResidualGatedSolverType(lastSolverTypeUsed);
  }

  /**
   * Check whether a solver uses a residual-based formulation that should satisfy the MESH residual
   * gate by default.
   *
   * @param type solver type to inspect
   * @return {@code true} when the solver should enforce full MESH residual diagnostics by default
   */
  private boolean isResidualGatedSolverType(SolverType type) {
    return type == SolverType.NAPHTALI_SANDHOLM || type == SolverType.MESH_RESIDUAL;
  }

  /**
   * Mark the solver strategy currently responsible for the accepted state.
   *
   * @param solverTypeUsed solver strategy that produced the accepted state
   */
  void markSolverTypeUsed(SolverType solverTypeUsed) {
    if (solverTypeUsed != null) {
      lastSolverTypeUsed = solverTypeUsed;
    }
  }

  void setError(double err) {
    this.err = err;
  }

  /**
   * Retrieve the iteration count of the most recent solve.
   *
   * @return iteration count
   */
  public int getLastIterationCount() {
    return lastIterationCount;
  }

  /**
   * Retrieve the latest average temperature residual in Kelvin.
   *
   * @return average temperature residual
   */
  public double getLastTemperatureResidual() {
    return lastTemperatureResidual;
  }

  /**
   * Retrieve the latest relative mass residual.
   *
   * @return relative mass balance residual
   */
  public double getLastMassResidual() {
    return lastMassResidual;
  }

  /**
   * Retrieve the latest relative enthalpy residual.
   *
   * @return relative enthalpy residual
   */
  public double getLastEnergyResidual() {
    return lastEnergyResidual;
  }

  /**
   * Retrieve the latest maximum raw internal tray traffic divided by total external feed flow.
   *
   * @return internal traffic ratio from the latest solve
   */
  public double getLastInternalTrafficRatio() {
    return lastInternalTrafficRatio;
  }

  /**
   * Check whether the latest public product streams came from the guarded feed-flash fallback.
   *
   * @return {@code true} if fallback product estimation was applied in the latest run
   */
  public boolean wasFeedFlashFallbackApplied() {
    return lastUsedFeedFlashFallback;
  }

  /**
   * Check whether matrix inside-out used its matrix warm-start stage in the latest run.
   *
   * @return {@code true} if a matrix warm-start state was accepted before rigorous polishing
   */
  public boolean wasMatrixInsideOutWarmStartUsed() {
    return lastMatrixInsideOutWarmStartUsed;
  }

  /**
   * Check whether matrix inside-out bypassed the matrix stage in the latest run.
   *
   * @return {@code true} if the adaptive solver used rigorous inside-out directly
   */
  public boolean wasMatrixInsideOutWarmStartBypassed() {
    return lastMatrixInsideOutWarmStartBypassed;
  }

  /**
   * Retrieve matrix warm-start iterations from the latest matrix inside-out run.
   *
   * @return number of matrix warm-start iterations, or zero if no matrix stage ran
   */
  public int getLastMatrixInsideOutIterationCount() {
    return lastMatrixInsideOutIterationCount;
  }

  /**
   * Retrieve matrix warm-start average temperature residual from the latest run.
   *
   * @return average temperature residual in Kelvin, or {@code Double.NaN} if no matrix stage ran
   */
  public double getLastMatrixInsideOutTemperatureResidual() {
    return lastMatrixInsideOutTemperatureResidual;
  }

  /**
   * Retrieve matrix warm-start wall time from the latest matrix inside-out run.
   *
   * @return matrix warm-start solve time in seconds, or zero if no matrix stage ran
   */
  public double getLastMatrixInsideOutSolveTimeSeconds() {
    return lastMatrixInsideOutSolveTimeSeconds;
  }

  /**
   * Retrieve rigorous inside-out outer flash sweeps from the latest solve.
   *
   * @return number of rigorous outside flash sweeps
   */
  public int getLastInsideOutOuterFlashSweeps() {
    return lastInsideOutOuterFlashSweeps;
  }

  /**
   * Retrieve simplified inside-out inner-loop iterations from the latest solve.
   *
   * @return number of surrogate inner-loop iterations
   */
  public int getLastInsideOutInnerLoopIterations() {
    return lastInsideOutInnerLoopIterations;
  }

  /**
   * Retrieve the latest inside-out K-value residual.
   *
   * @return K-value residual, or {@code Double.NaN} if inside-out was not run
   */
  public double getLastInsideOutKValueResidual() {
    return lastInsideOutKValueResidual;
  }

  /**
   * Retrieve the latest simplified inside-out surrogate residual.
   *
   * @return surrogate temperature residual, or {@code Double.NaN} if no surrogate loop ran
   */
  public double getLastInsideOutSurrogateResidual() {
    return lastInsideOutSurrogateResidual;
  }

  /**
   * Retrieve the latest simplified inside-out surrogate reset count.
   *
   * @return number of surrogate resets in the latest solve
   */
  public int getLastInsideOutSurrogateResetCount() {
    return lastInsideOutSurrogateResetCount;
  }

  /**
   * Retrieve latest Naphtali-Sandholm semi-analytic Jacobian columns.
   *
   * @return semi-analytic Jacobian column count
   */
  public int getLastNaphtaliAnalyticJacobianColumns() {
    return lastNaphtaliAnalyticJacobianColumns;
  }

  /**
   * Retrieve latest Naphtali-Sandholm finite-difference Jacobian columns.
   *
   * @return finite-difference Jacobian column count
   */
  public int getLastNaphtaliFiniteDifferenceJacobianColumns() {
    return lastNaphtaliFiniteDifferenceJacobianColumns;
  }

  /**
   * Retrieve latest Naphtali-Sandholm thermodynamic evaluation count.
   *
   * @return tray thermodynamic evaluations performed by the latest Naphtali solve
   */
  public int getLastNaphtaliThermoEvaluationCount() {
    return lastNaphtaliThermoEvaluationCount;
  }

  /**
   * Retrieve latest Naphtali-Sandholm thermodynamic cache hit count.
   *
   * @return tray thermodynamic evaluations avoided by cache reuse
   */
  public int getLastNaphtaliThermoCacheHitCount() {
    return lastNaphtaliThermoCacheHitCount;
  }

  /**
   * Retrieve latest Naphtali-Sandholm Jacobian build wall time.
   *
   * @return Jacobian build time in seconds
   */
  public double getLastNaphtaliJacobianBuildTimeSeconds() {
    return lastNaphtaliJacobianBuildTimeSeconds;
  }

  /**
   * Retrieve latest Naphtali-Sandholm block-tridiagonal linear solve count.
   *
   * @return block-tridiagonal solve count
   */
  public int getLastNaphtaliBlockLinearSolveCount() {
    return lastNaphtaliBlockLinearSolveCount;
  }

  /**
   * Retrieve latest Naphtali-Sandholm dense fallback linear solve count.
   *
   * @return dense linear solve count
   */
  public int getLastNaphtaliDenseLinearSolveCount() {
    return lastNaphtaliDenseLinearSolveCount;
  }

  /**
   * Retrieve latest Naphtali-Sandholm linear solve wall time.
   *
   * @return linear solve time in seconds
   */
  public double getLastNaphtaliLinearSolveTimeSeconds() {
    return lastNaphtaliLinearSolveTimeSeconds;
  }

  /**
   * Retrieve the latest top specification residual.
   *
   * @return top specification residual as current value minus target value
   */
  public double getLastTopSpecificationResidual() {
    return lastTopSpecificationResidual;
  }

  /**
   * Retrieve the latest bottom specification residual.
   *
   * @return bottom specification residual as current value minus target value
   */
  public double getLastBottomSpecificationResidual() {
    return lastBottomSpecificationResidual;
  }

  /**
   * Retrieve the largest absolute active specification residual.
   *
   * @return maximum absolute top or bottom specification residual
   */
  public double getLastSpecificationResidual() {
    return Math.max(Math.abs(lastTopSpecificationResidual),
        Math.abs(lastBottomSpecificationResidual));
  }

  /**
   * Set the number of continuation stages used for adjustable product specifications.
   *
   * <p>
   * A value of one preserves the legacy direct outer-loop solve. Values above one ramp purity,
   * recovery, and product-flow targets from the current product value to the final target over the
   * requested number of stages.
   * </p>
   *
   * @param steps number of homotopy stages, must be positive
   * @throws IllegalArgumentException if {@code steps} is less than one
   */
  public void setSpecificationHomotopySteps(int steps) {
    if (steps < 1) {
      throw new IllegalArgumentException("Specification homotopy steps must be positive");
    }
    specificationHomotopySteps = steps;
  }

  /**
   * Get the configured number of specification continuation stages.
   *
   * @return configured homotopy stage count
   */
  public int getSpecificationHomotopySteps() {
    return specificationHomotopySteps;
  }

  /**
   * Get the number of specification continuation stages completed by the latest solve.
   *
   * @return latest completed homotopy stage count, or zero when homotopy was not used
   */
  public int getLastSpecificationHomotopyStepCount() {
    return lastSpecificationHomotopyStepCount;
  }

  /**
   * Retrieve the latest MESH residual vector infinity norm.
   *
   * @return maximum absolute MESH residual, or {@code Double.NaN} if no solve has been run
   */
  public double getLastMeshResidualNorm() {
    return lastMeshResidual == null ? Double.NaN : lastMeshResidual.getInfinityNorm();
  }

  /**
   * Retrieve the latest MESH material residual infinity norm.
   *
   * @return maximum absolute component material residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshMaterialResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.MATERIAL);
  }

  /**
   * Retrieve the latest MESH equilibrium residual infinity norm.
   *
   * @return maximum absolute equilibrium residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshEquilibriumResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.EQUILIBRIUM);
  }

  /**
   * Retrieve the latest MESH summation residual infinity norm.
   *
   * @return maximum absolute summation residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshSummationResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.SUMMATION);
  }

  /**
   * Retrieve the latest MESH energy residual infinity norm.
   *
   * @return maximum absolute energy residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshEnergyResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.ENERGY);
  }

  /**
   * Retrieve the latest MESH product draw residual infinity norm.
   *
   * @return maximum absolute product draw residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshProductDrawResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.PRODUCT_DRAW);
  }

  /**
   * Retrieve the latest MESH specification residual infinity norm.
   *
   * @return maximum absolute specification residual, or {@code Double.NaN} if unavailable
   */
  public double getLastMeshSpecificationResidualNorm() {
    return getLastMeshResidualNorm(ColumnMeshEquationType.SPECIFICATION);
  }

  /**
   * Retrieve a copy of the latest MESH residual vector.
   *
   * @return residual vector copy, or an empty array if no solve has been run
   */
  public double[] getLastMeshResidualVector() {
    return lastMeshResidual == null ? new double[0] : lastMeshResidual.getValues();
  }

  /**
   * Retrieve the latest internal MESH residual diagnostics.
   *
   * @return latest residual diagnostics, or null if no solve has been run
   */
  ColumnMeshResidual getLastMeshResidual() {
    return lastMeshResidual;
  }

  /**
   * Get a MESH residual norm by equation type.
   *
   * @param equationType equation type to inspect
   * @return infinity norm for that equation type, or {@code Double.NaN} if unavailable
   */
  private double getLastMeshResidualNorm(ColumnMeshEquationType equationType) {
    return lastMeshResidual == null ? Double.NaN : lastMeshResidual.getInfinityNorm(equationType);
  }

  /**
   * Retrieve the duration of the most recent solve in seconds.
   *
   * @return solve time in seconds
   */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
  }

  /**
   * Build a human-readable convergence diagnostic report for the latest column solve.
   *
   * <p>
   * The report is intended for notebooks, agents, and troubleshooting scripts that need to know
   * which convergence gate failed and which common modelling choices should be checked first. It
   * does not change the column state.
   * </p>
   *
   * @return multi-line diagnostic report with residuals, feed-tray placement, and recommendations
   */
  public String getConvergenceDiagnostics() {
    StringBuilder diagnostics = new StringBuilder();
    boolean solved = solved();
    diagnostics.append("DistillationColumn Diagnostics:\n");
    diagnostics.append("  Name: ").append(getName()).append("\n");
    diagnostics.append("  Solved: ").append(solved).append("\n");
    diagnostics.append("  Solver: ").append(solverType).append("\n");
    diagnostics.append("  Last solver used: ").append(lastSolverTypeUsed).append("\n");
    diagnostics.append("  Solve status: ").append(lastSolveStatus).append("\n");
    if (lastSolveStatusReason != null && !lastSolveStatusReason.trim().isEmpty()) {
      diagnostics.append("  Solve status reason: ").append(lastSolveStatusReason).append("\n");
    }
    diagnostics.append("  Trays: ").append(numberOfTrays).append(" total, ")
        .append(getEffectiveStageCount()).append(" equilibrium stages").append("\n");
    diagnostics.append("  Iterations: ").append(lastIterationCount).append("\n");
    diagnostics.append("  Solve time: ").append(lastSolveTimeSeconds).append(" s\n");
    if (lastAutoSolverSummary != null && !lastAutoSolverSummary.trim().isEmpty()) {
      diagnostics.append("  Automatic solver candidates:\n");
      diagnostics.append(lastAutoSolverSummary);
      if (!lastAutoSolverSummary.endsWith("\n")) {
        diagnostics.append("\n");
      }
    }
    if (specificationHomotopySteps > 1 || lastSpecificationHomotopyStepCount > 0) {
      diagnostics.append("  Specification homotopy: ").append(lastSpecificationHomotopyStepCount)
          .append("/").append(specificationHomotopySteps).append(" stages\n");
    }
    if (lastInsideOutOuterFlashSweeps > 0 || lastInsideOutInnerLoopIterations > 0) {
      diagnostics.append("  Inside-out model:\n");
      diagnostics.append("    outer flash sweeps: ").append(lastInsideOutOuterFlashSweeps)
          .append("\n");
      diagnostics.append("    inner loop iterations: ").append(lastInsideOutInnerLoopIterations)
          .append("\n");
      diagnostics.append("    k-value residual: ").append(lastInsideOutKValueResidual).append("\n");
      diagnostics.append("    surrogate residual: ").append(lastInsideOutSurrogateResidual)
          .append("\n");
      diagnostics.append("    surrogate resets: ").append(lastInsideOutSurrogateResetCount)
          .append("\n");
    }
    if (solverType == SolverType.MATRIX_INSIDE_OUT || lastMatrixInsideOutWarmStartUsed
        || lastMatrixInsideOutWarmStartBypassed) {
      diagnostics.append("  Matrix inside-out:\n");
      diagnostics.append("    warm start used: ").append(lastMatrixInsideOutWarmStartUsed)
          .append("\n");
      diagnostics.append("    warm start bypassed: ").append(lastMatrixInsideOutWarmStartBypassed)
          .append("\n");
      diagnostics.append("    matrix iterations: ").append(lastMatrixInsideOutIterationCount)
          .append("\n");
      diagnostics.append("    matrix temperature residual: ")
          .append(lastMatrixInsideOutTemperatureResidual).append(" K\n");
      diagnostics.append("    matrix time: ").append(lastMatrixInsideOutSolveTimeSeconds)
          .append(" s\n");
    }
    if (lastNaphtaliAnalyticJacobianColumns > 0 || lastNaphtaliFiniteDifferenceJacobianColumns > 0
        || lastNaphtaliThermoEvaluationCount > 0) {
      diagnostics.append("  Naphtali-Sandholm Jacobian:\n");
      diagnostics.append("    semi-analytic columns: ").append(lastNaphtaliAnalyticJacobianColumns)
          .append("\n");
      diagnostics.append("    finite-difference columns: ")
          .append(lastNaphtaliFiniteDifferenceJacobianColumns).append("\n");
      diagnostics.append("    thermodynamic evaluations: ")
          .append(lastNaphtaliThermoEvaluationCount).append("\n");
      diagnostics.append("    thermodynamic cache hits: ").append(lastNaphtaliThermoCacheHitCount)
          .append("\n");
      diagnostics.append("    jacobian build time: ").append(lastNaphtaliJacobianBuildTimeSeconds)
          .append(" s\n");
      diagnostics.append("    block linear solves: ").append(lastNaphtaliBlockLinearSolveCount)
          .append("\n");
      diagnostics.append("    dense linear solves: ").append(lastNaphtaliDenseLinearSolveCount)
          .append("\n");
      diagnostics.append("    linear solve time: ").append(lastNaphtaliLinearSolveTimeSeconds)
          .append(" s\n");
    }
    diagnostics.append("  Residuals:\n");
    diagnostics.append("    temperature: ").append(lastTemperatureResidual).append(" K (tolerance ")
        .append(getEffectiveTemperatureTolerance()).append(")\n");
    diagnostics.append("    mass: ").append(lastMassResidual).append(" (tolerance ")
        .append(getEffectiveMassBalanceTolerance()).append(")\n");
    diagnostics.append("    energy: ").append(lastEnergyResidual).append(" (tolerance ")
        .append(getEffectiveEnthalpyBalanceTolerance()).append(", enforced=")
        .append(enforceEnergyBalanceTolerance).append(")\n");
    diagnostics.append("    mesh infinity norm: ").append(getLastMeshResidualNorm())
        .append(" (tolerance ").append(meshResidualTolerance).append(", enforced=")
        .append(isEffectiveMeshResidualToleranceEnforced()).append(")\n");
    diagnostics.append("      material: ").append(getLastMeshMaterialResidualNorm())
        .append(", equilibrium: ").append(getLastMeshEquilibriumResidualNorm())
        .append(", summation: ").append(getLastMeshSummationResidualNorm()).append(", energy: ")
        .append(getLastMeshEnergyResidualNorm()).append(", product draw: ")
        .append(getLastMeshProductDrawResidualNorm()).append(", specification: ")
        .append(getLastMeshSpecificationResidualNorm()).append("\n");

    diagnostics.append("  Feed trays:\n");
    if (feedStreams.isEmpty()) {
      diagnostics.append("    none\n");
    } else {
      List<Integer> feedTrayNumbers = new ArrayList<Integer>(feedStreams.keySet());
      Collections.sort(feedTrayNumbers);
      for (Integer feedTrayNumber : feedTrayNumbers) {
        int stagesBelow = Math.max(0, feedTrayNumber.intValue());
        int stagesAbove = Math.max(0, numberOfTrays - feedTrayNumber.intValue() - 1);
        diagnostics.append("    tray ").append(feedTrayNumber).append(" with ")
            .append(feedStreams.get(feedTrayNumber).size()).append(" feed(s), ").append(stagesAbove)
            .append(" stages above, ").append(stagesBelow).append(" stages below");
        if (isFeedTrayNearTop(feedTrayNumber.intValue())) {
          diagnostics.append(" (near top/condenser)");
        } else if (isFeedTrayNearBottom(feedTrayNumber.intValue())) {
          diagnostics.append(" (near bottom/reboiler)");
        }
        diagnostics.append("\n");
      }
    }

    diagnostics.append("  Recommendations:\n");
    int recommendationCount = appendConvergenceRecommendations(diagnostics, solved);
    if (recommendationCount == 0) {
      diagnostics.append("    - No immediate convergence issue detected.\n");
    }
    return diagnostics.toString();
  }

  /**
   * Append modelling and solver recommendations to a convergence diagnostic report.
   *
   * @param diagnostics report builder receiving recommendation lines
   * @param solved whether the column currently satisfies its convergence gates
   * @return number of recommendation lines appended
   */
  private int appendConvergenceRecommendations(StringBuilder diagnostics, boolean solved) {
    int count = 0;
    for (Integer feedTrayNumber : feedStreams.keySet()) {
      if (isFeedTrayNearTop(feedTrayNumber.intValue())) {
        diagnostics.append("    - Feed tray ").append(feedTrayNumber)
            .append(" is close to the condenser. For debutanizer/depropanizer-style ")
            .append("hydrocarbon splits, start near the middle of the column and move the feed ")
            .append("only after the base case converges.\n");
        count++;
      } else if (isFeedTrayNearBottom(feedTrayNumber.intValue())) {
        diagnostics.append("    - Feed tray ").append(feedTrayNumber)
            .append(" is close to the reboiler. Check whether the feed should enter higher in ")
            .append("the column or whether a side draw/flash should be represented explicitly.\n");
        count++;
      }
    }
    if (hasCondenser && getCondenser().getRefluxRatio() > 0.0
        && getCondenser().getRefluxRatio() <= 0.2) {
      diagnostics.append("    - Condenser reflux ratio is low (")
          .append(getCondenser().getRefluxRatio())
          .append("). Low reflux can make tray-temperature substitution oscillatory; ")
          .append("try a higher reflux during initialization before tightening the spec.\n");
      count++;
    }
    if ((!solved || lastIterationCount > Math.max(20, getEffectiveStageCount() * 3)) && hasCondenser
        && hasReboiler && solverType != SolverType.MESH_RESIDUAL
        && solverType != SolverType.NAPHTALI_SANDHOLM) {
      diagnostics.append("    - For full hydrocarbon fractionators, benchmark ")
          .append(SolverType.NAPHTALI_SANDHOLM).append(", ").append(SolverType.MESH_RESIDUAL)
          .append(", or ").append(SolverType.NEWTON)
          .append(" after checking feed-tray placement. They can be faster than direct ")
          .append("substitution for well-conditioned columns.\n");
      count++;
    }
    double productDrawResidual = getLastMeshProductDrawResidualNorm();
    if (Double.isFinite(productDrawResidual)
        && productDrawResidual > meshProductDrawResidualTolerance) {
      diagnostics
          .append("    - Product draw residual is above the product-draw tolerance. This means ")
          .append("the exposed overhead/bottom streams do not match the terminal tray traffic; ")
          .append("use ").append(SolverType.NAPHTALI_SANDHOLM).append(" or ")
          .append(SolverType.MESH_RESIDUAL)
          .append(" or inspect reflux, boilup, and product specifications before trusting the ")
          .append("product split.\n");
      count++;
    }
    if (!solved) {
      diagnostics.append("    - Inspect the residual above the tolerance: temperature usually ")
          .append("points to tray/specification oscillation, while mass residual points to ")
          .append("stream wiring or divergent internal L/V traffic.\n");
      count++;
    }
    return count;
  }

  /**
   * Check whether a feed tray is close to the condenser/top of the column.
   *
   * @param feedTrayNumber tray index to inspect
   * @return {@code true} when the feed has few stages above it in a column with a condenser
   */
  private boolean isFeedTrayNearTop(int feedTrayNumber) {
    if (!hasCondenser || numberOfTrays < 5) {
      return false;
    }
    int stagesAbove = Math.max(0, numberOfTrays - feedTrayNumber - 1);
    int topLimit = Math.max(1, (int) Math.ceil(getEffectiveStageCount() * 0.25));
    return stagesAbove <= topLimit;
  }

  /**
   * Check whether a feed tray is close to the reboiler/bottom of the column.
   *
   * @param feedTrayNumber tray index to inspect
   * @return {@code true} when the feed has few stages below it in a column with a reboiler
   */
  private boolean isFeedTrayNearBottom(int feedTrayNumber) {
    if (!hasReboiler || numberOfTrays < 5) {
      return false;
    }
    int stagesBelow = Math.max(0, feedTrayNumber);
    int bottomLimit = Math.max(1, (int) Math.ceil(getEffectiveStageCount() * 0.25));
    return stagesBelow <= bottomLimit;
  }

  /**
   * Access the configured relative mass balance tolerance.
   *
   * @return mass balance tolerance
   */
  public double getMassBalanceTolerance() {
    return getEffectiveMassBalanceTolerance();
  }

  /**
   * Control whether the solver enforces the energy balance tolerance when determining convergence.
   *
   * @param enforce {@code true} to require the energy residual to satisfy the configured tolerance
   */
  public void setEnforceEnergyBalanceTolerance(boolean enforce) {
    this.enforceEnergyBalanceTolerance = enforce;
  }

  /**
   * Check if the solver currently enforces the energy balance tolerance during convergence checks.
   *
   * @return {@code true} if the energy residual must satisfy its tolerance before convergence
   */
  public boolean isEnforceEnergyBalanceTolerance() {
    return enforceEnergyBalanceTolerance;
  }

  /**
   * Control whether the latest MESH residual vector must satisfy tolerance during convergence
   * checks. Calling this method explicitly overrides the default behavior where residual-based
   * solvers enforce the gate and substitution or temperature/flow accelerator solvers do not.
   *
   * @param enforce {@code true} to require MESH residuals to satisfy the configured tolerance
   */
  public void setEnforceMeshResidualTolerance(boolean enforce) {
    this.enforceMeshResidualTolerance = enforce;
    this.enforceMeshResidualToleranceCustomized = true;
  }

  /**
   * Check if convergence currently requires the latest MESH residual vector to satisfy tolerance.
   *
   * @return {@code true} if MESH residuals are part of the convergence check
   */
  public boolean isEnforceMeshResidualTolerance() {
    return isEffectiveMeshResidualToleranceEnforced();
  }

  /**
   * Access the configured relative enthalpy balance tolerance.
   *
   * @return enthalpy balance tolerance
   */
  public double getEnthalpyBalanceTolerance() {
    return getEffectiveEnthalpyBalanceTolerance();
  }

  /**
   * Access the configured scaled MESH residual tolerance.
   *
   * @return MESH residual tolerance
   */
  public double getMeshResidualTolerance() {
    return meshResidualTolerance;
  }

  /**
   * Access the configured scaled product-draw residual tolerance.
   *
   * @return product-draw residual tolerance
   */
  public double getMeshProductDrawResidualTolerance() {
    return meshProductDrawResidualTolerance;
  }

  /**
   * Access the maximum number of tray optimization candidate cases allowed per search.
   *
   * @return maximum candidate count
   */
  public int getMaxTrayOptimizationCandidates() {
    return maxTrayOptimizationCandidates;
  }

  /**
   * Set the maximum number of tray optimization candidate cases allowed per search.
   *
   * @param maxCandidates maximum candidate count, must be at least one
   * @throws IllegalArgumentException if {@code maxCandidates} is less than one
   */
  public void setMaxTrayOptimizationCandidates(int maxCandidates) {
    if (maxCandidates < 1) {
      throw new IllegalArgumentException("Maximum tray optimization candidates must be positive.");
    }
    this.maxTrayOptimizationCandidates = maxCandidates;
  }

  /**
   * Access the maximum elapsed time allowed for a tray optimization search.
   *
   * @return maximum search time in seconds
   */
  public double getMaxTrayOptimizationTimeSeconds() {
    return maxTrayOptimizationTimeSeconds;
  }

  /**
   * Set the maximum elapsed time allowed for a tray optimization search.
   *
   * @param maxTimeSeconds maximum search time in seconds, must be finite and greater than zero
   * @throws IllegalArgumentException if {@code maxTimeSeconds} is not finite or positive
   */
  public void setMaxTrayOptimizationTimeSeconds(double maxTimeSeconds) {
    if (!isPositiveFinite(maxTimeSeconds)) {
      throw new IllegalArgumentException(
          "Maximum tray optimization time must be finite and positive.");
    }
    this.maxTrayOptimizationTimeSeconds = maxTimeSeconds;
  }

  /**
   * Access the configured average temperature tolerance.
   *
   * @return temperature tolerance in Kelvin
   */
  public double getTemperatureTolerance() {
    return getEffectiveTemperatureTolerance();
  }

  /**
   * <p>
   * Setter for the field <code>maxNumberOfIterations</code>.
   * </p>
   *
   * @param maxIter a int
   */
  public void setMaxNumberOfIterations(int maxIter) {
    this.maxNumberOfIterations = Math.max(1, maxIter);
  }

  /**
   * <p>
   * Setter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @param internalDiameter a double
   */
  public void setInternalDiameter(double internalDiameter) {
    this.internalDiameter = internalDiameter;
  }

  /**
   * <p>
   * Getter for the field <code>internalDiameter</code>.
   * </p>
   *
   * @return a double
   */
  public double getInternalDiameter() {
    return internalDiameter;
  }

  /**
   * Calculates the Fs factor for the distillation column. The Fs factor is a measure of the gas
   * flow rate through the column relative to the cross-sectional area and the density of the gas.
   *
   * @return the Fs factor as a double value
   */
  public double getFsFactor() {
    double intArea = Math.PI * getInternalDiameter() * getInternalDiameter() / 4.0;
    return getGasOutStream().getThermoSystem().getFlowRate("m3/sec") / intArea
        * Math.sqrt(getGasOutStream().getThermoSystem().getDensity("kg/m3"));
  }

  /**
   * Create and run a column internals designer for this column.
   *
   * <p>
   * Evaluates hydraulics on every tray (flooding, weeping, entrainment, downcomer backup, pressure
   * drop, efficiency) and sizes the column diameter from the controlling tray.
   * </p>
   *
   * @param internalsType tray type ("sieve", "valve", "bubble-cap") or "packed"
   * @return a fully evaluated {@link ColumnInternalsDesigner} with per-tray results
   */
  public ColumnInternalsDesigner calcColumnInternals(String internalsType) {
    ColumnInternalsDesigner designer = new ColumnInternalsDesigner(this);
    designer.setInternalsType(internalsType);
    designer.calculate();
    return designer;
  }

  /**
   * Create and run a column internals designer for this column with default sieve trays.
   *
   * @return a fully evaluated {@link ColumnInternalsDesigner} with per-tray results
   */
  public ColumnInternalsDesigner calcColumnInternals() {
    return calcColumnInternals("sieve");
  }

  /**
   * Set a gas or liquid side-draw fraction on a tray.
   *
   * <p>
   * Side draws are implemented on the tray outlet itself, so all column solver paths use the
   * residual gas/liquid traffic for inter-tray flow and expose the withdrawn stream separately.
   * </p>
   *
   * @param trayNumber bottom-up tray index
   * @param phase phase to withdraw
   * @param fraction fraction of the selected phase outlet to withdraw, from zero to one
   */
  public void setSideDrawFraction(int trayNumber, SideDrawPhase phase, double fraction) {
    SimpleTray tray = getTray(trayNumber);
    if (phase == SideDrawPhase.GAS) {
      tray.setGasSideDrawFraction(fraction);
    } else if (phase == SideDrawPhase.LIQUID) {
      tray.setLiquidSideDrawFraction(fraction);
    } else {
      throw new IllegalArgumentException("Side draw phase cannot be null");
    }
    setDoInitializion(true);
  }

  /**
   * Set a vapor side-draw fraction on a tray.
   *
   * @param trayNumber bottom-up tray index
   * @param fraction fraction of vapor outlet to withdraw, from zero to one
   */
  public void setGasSideDrawFraction(int trayNumber, double fraction) {
    setSideDrawFraction(trayNumber, SideDrawPhase.GAS, fraction);
  }

  /**
   * Set a liquid side-draw fraction on a tray.
   *
   * @param trayNumber bottom-up tray index
   * @param fraction fraction of liquid outlet to withdraw, from zero to one
   */
  public void setLiquidSideDrawFraction(int trayNumber, double fraction) {
    setSideDrawFraction(trayNumber, SideDrawPhase.LIQUID, fraction);
  }

  /**
   * Get a side-draw stream from a tray.
   *
   * @param trayNumber bottom-up tray index
   * @param phase phase to retrieve
   * @return side-draw stream, or a zero-flow stream when no side draw is configured
   */
  public StreamInterface getSideDrawStream(int trayNumber, SideDrawPhase phase) {
    SimpleTray tray = getTray(trayNumber);
    if (phase == SideDrawPhase.GAS) {
      return tray.getGasSideDrawStream();
    } else if (phase == SideDrawPhase.LIQUID) {
      return tray.getLiquidSideDrawStream();
    }
    throw new IllegalArgumentException("Side draw phase cannot be null");
  }

  /**
   * Get all non-zero side-draw streams from the column.
   *
   * @return unmodifiable list of side-draw streams currently withdrawn from trays
   */
  public List<StreamInterface> getSideDrawStreams() {
    List<StreamInterface> sideDrawStreams = new ArrayList<>();
    for (int trayNumber = 0; trayNumber < numberOfTrays; trayNumber++) {
      SimpleTray tray = getTray(trayNumber);
      if (tray.getGasSideDrawFraction() > 0.0) {
        sideDrawStreams.add(tray.getGasSideDrawStream());
      }
      if (tray.getLiquidSideDrawFraction() > 0.0) {
        sideDrawStreams.add(tray.getLiquidSideDrawStream());
      }
    }
    return Collections.unmodifiableList(sideDrawStreams);
  }

  /**
   * Add a side-draw flow specification solved as a column tear variable.
   *
   * <p>
   * The solver adjusts the side-draw fraction on the requested tray until the side-product stream
   * flow matches the target. This turns side draws into formal product specifications while
   * preserving the existing tray split implementation.
   * </p>
   *
   * @param trayNumber bottom-up tray index
   * @param phase side-draw phase
   * @param flowRate target flow rate
   * @param unit flow-rate unit
   * @return configured side-draw specification
   * @throws IllegalArgumentException if the tray number, phase, flow rate, or unit is invalid
   */
  public ColumnSideDrawSpecification addSideDrawFlowSpecification(int trayNumber,
      SideDrawPhase phase, double flowRate, String unit) {
    validateTrayIndex(trayNumber, "side draw specification tray");
    ColumnSideDrawSpecification specification =
        new ColumnSideDrawSpecification(trayNumber, phase, flowRate, unit);
    sideDrawSpecifications.add(specification);
    if (flowRate > 0.0 && getSideDrawFraction(trayNumber, phase) <= 0.0) {
      setSideDrawFractionWithinLimit(trayNumber, phase, 0.05);
    }
    setDoInitializion(true);
    return specification;
  }

  /**
   * Get configured side-draw flow specifications.
   *
   * @return unmodifiable list of side-draw specifications
   */
  public List<ColumnSideDrawSpecification> getSideDrawSpecifications() {
    return Collections.unmodifiableList(sideDrawSpecifications);
  }

  /**
   * Set the maximum number of outer tear iterations for side draws and hydraulics.
   *
   * @param maxIterations maximum number of tear iterations, minimum one
   */
  public void setMaxColumnTearIterations(int maxIterations) {
    maxColumnTearIterations = Math.max(1, maxIterations);
  }

  /**
   * Set the relative tolerance for side-draw and hydraulic tear variables.
   *
   * @param tolerance relative tolerance, must be finite and positive
   */
  public void setColumnTearTolerance(double tolerance) {
    if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("Column tear tolerance must be finite and positive");
    }
    columnTearTolerance = tolerance;
  }

  /**
   * Enable or disable hydraulic pressure-drop coupling.
   *
   * @param enabled {@code true} to update the column pressure profile from internals hydraulics
   */
  public void setHydraulicPressureDropCouplingEnabled(boolean enabled) {
    hydraulicPressureDropCouplingEnabled = enabled;
    setDoInitializion(true);
  }

  /**
   * Check whether hydraulic pressure-drop coupling is enabled.
   *
   * @return {@code true} when hydraulic pressure drop is coupled into the pressure profile
   */
  public boolean isHydraulicPressureDropCouplingEnabled() {
    return hydraulicPressureDropCouplingEnabled;
  }

  /**
   * Set the internals type used for hydraulic pressure-drop coupling.
   *
   * @param internalsType tray type (for example "sieve") or "packed"
   */
  public void setHydraulicPressureDropInternalsType(String internalsType) {
    if (internalsType == null || internalsType.trim().isEmpty()) {
      throw new IllegalArgumentException("Hydraulic internals type cannot be empty");
    }
    hydraulicPressureDropInternalsType = internalsType;
  }

  /**
   * Convenience method for enabling hydraulic pressure-drop coupling with an internals type.
   *
   * @param internalsType tray type or packing mode passed to {@link #calcColumnInternals(String)}
   */
  public void enableHydraulicPressureDropCoupling(String internalsType) {
    setHydraulicPressureDropInternalsType(internalsType);
    setHydraulicPressureDropCouplingEnabled(true);
  }

  /**
   * Get the latest hydraulic pressure drop used for coupling.
   *
   * @return latest total pressure drop in Pa
   */
  public double getLastHydraulicPressureDropPa() {
    return lastHydraulicPressureDropPa;
  }

  /**
   * Get the latest hydraulic pressure-drop coupling residual.
   *
   * @return relative endpoint-pressure change from the latest coupling update
   */
  public double getLastHydraulicPressureDropResidual() {
    return lastHydraulicPressureDropResidual;
  }

  /**
   * Get the number of outer tear iterations used in the latest run.
   *
   * @return latest side-draw/pumparound/hydraulic tear iteration count
   */
  public int getLastColumnTearIterationCount() {
    return lastColumnTearIterationCount;
  }

  /**
   * Get the maximum relative residual from the latest outer tear-variable solve.
   *
   * @return latest outer tear residual
   */
  public double getLastColumnTearResidual() {
    return lastColumnTearResidual;
  }

  /**
   * Check whether the latest outer tear-variable solve converged.
   *
   * @return {@code true} when the latest side-draw/pumparound/hydraulic solve met tolerance
   */
  public boolean isLastColumnTearConverged() {
    return lastColumnTearConverged;
  }

  /**
   * Get the latest relative pumparound return-stream change.
   *
   * @return maximum relative return-stream flow change from the latest pumparound update
   */
  public double getLastPumparoundRelativeChange() {
    return lastPumparoundRelativeChange;
  }

  /**
   * Get the dynamic column model formulation.
   *
   * @return dynamic model formulation used for transient calculations
   */
  public DynamicColumnModel getDynamicColumnModel() {
    return dynamicColumnModel;
  }

  /**
   * Check whether the active dynamic column model is experimental.
   *
   * @return {@code true} because the current dynamic model is an explicit-Euler screening model
   */
  public boolean isDynamicColumnModelExperimental() {
    return dynamicColumnModel == DynamicColumnModel.EXPERIMENTAL_EULER;
  }

  /**
   * Add a liquid pumparound circuit.
   *
   * <p>
   * The draw is treated as an internal liquid withdrawal, not as a side-product stream. The return
   * stream is updated between column solves and added to the configured return tray as an internal
   * recycle, so external mass-balance reporting continues to use only true feeds and products.
   * </p>
   *
   * @param name pumparound name
   * @param drawTrayNumber bottom-up tray index where liquid is withdrawn
   * @param returnTrayNumber bottom-up tray index where liquid is returned
   * @param drawFraction fraction of tray liquid traffic withdrawn
   * @param temperatureDrop temperature drop from draw to return in Kelvin
   * @return configured pumparound definition
   * @throws IllegalArgumentException if tray numbers, draw fraction, or temperature drop are
   *         invalid
   */
  public ColumnPumparound addLiquidPumparound(String name, int drawTrayNumber, int returnTrayNumber,
      double drawFraction, double temperatureDrop) {
    validateTrayIndex(drawTrayNumber, "pumparound draw tray");
    validateTrayIndex(returnTrayNumber, "pumparound return tray");
    if (!Double.isFinite(drawFraction) || drawFraction < 0.0 || drawFraction > 1.0) {
      throw new IllegalArgumentException("Pumparound draw fraction must be between 0 and 1");
    }
    if (!Double.isFinite(temperatureDrop)) {
      throw new IllegalArgumentException("Pumparound temperature drop must be finite");
    }
    if (getTray(drawTrayNumber).getLiquidPumparoundDrawFraction() > 0.0 && drawFraction > 0.0) {
      throw new IllegalArgumentException("Only one liquid pumparound draw is supported per tray");
    }

    ColumnPumparound pumparound =
        new ColumnPumparound(name, drawTrayNumber, returnTrayNumber, drawFraction, temperatureDrop);
    pumparounds.add(pumparound);
    getTray(drawTrayNumber).setLiquidPumparoundDrawFraction(drawFraction);
    setDoInitializion(true);
    return pumparound;
  }

  /**
   * Get configured liquid pumparound circuits.
   *
   * @return unmodifiable list of configured pumparounds
   */
  public List<ColumnPumparound> getPumparounds() {
    return Collections.unmodifiableList(pumparounds);
  }

  /**
   * Set the maximum outer iterations used to converge pumparound return streams.
   *
   * @param maxIterations maximum number of pumparound iterations, minimum one
   */
  public void setMaxPumparoundIterations(int maxIterations) {
    maxPumparoundIterations = Math.max(1, maxIterations);
  }

  /**
   * Set the relative return-flow tolerance used for pumparound iterations.
   *
   * @param tolerance relative flow tolerance, must be finite and positive
   */
  public void setPumparoundTolerance(double tolerance) {
    if (!Double.isFinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("Pumparound tolerance must be finite and positive");
    }
    pumparoundTolerance = tolerance;
  }

  /**
   * Validate that a tray index is inside the column.
   *
   * @param trayNumber tray index to validate
   * @param label diagnostic label for the tray role
   */
  private void validateTrayIndex(int trayNumber, String label) {
    if (trayNumber < 0 || trayNumber >= numberOfTrays) {
      throw new IllegalArgumentException(label + " must be between 0 and " + (numberOfTrays - 1));
    }
  }

  /**
   * <p>
   * Getter for the field <code>gasOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getGasOutStream() {
    return gasOutStream;
  }

  /**
   * <p>
   * Getter for the field <code>liquidOutStream</code>.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> inletStreams = new ArrayList<>();
    for (StreamInterface feedStream : getAllExternalFeedStreams()) {
      addStreamIfMissingByIdentity(inletStreams, feedStream);
    }
    for (StreamInterface feedStream : unassignedFeedStreams) {
      addStreamIfMissingByIdentity(inletStreams, feedStream);
    }
    return Collections.unmodifiableList(inletStreams);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> outletStreams = new ArrayList<>();
    addStreamIfMissingByIdentity(outletStreams, getGasOutStream());
    addStreamIfMissingByIdentity(outletStreams, getLiquidOutStream());
    for (StreamInterface sideDrawStream : getSideDrawStreams()) {
      addStreamIfMissingByIdentity(outletStreams, sideDrawStream);
    }
    return Collections.unmodifiableList(outletStreams);
  }

  /**
   * Get all external feed streams connected to a tray.
   *
   * <p>
   * Feeds registered through {@link #addFeedStream(StreamInterface, int)} are always included. The
   * method also includes named streams added directly to the tray to preserve legacy workflows that
   * use {@code getTray(index).addStream(stream)} for side feeds or stripping gas.
   * </p>
   *
   * @param trayIndex tray index to inspect
   * @return external feed streams connected to the tray
   */
  List<StreamInterface> getExternalFeedStreams(int trayIndex) {
    List<StreamInterface> externalFeeds = new ArrayList<>();
    List<StreamInterface> registeredFeeds = feedStreams.get(trayIndex);
    if (registeredFeeds != null) {
      externalFeeds.addAll(registeredFeeds);
    }
    List<StreamInterface> directFeeds = directExternalFeedStreams.get(trayIndex);
    if (directFeeds != null) {
      for (StreamInterface directFeed : directFeeds) {
        addStreamIfMissingByIdentity(externalFeeds, directFeed);
      }
    }
    return externalFeeds;
  }

  /**
   * Get all external feed streams connected to the column.
   *
   * @return external feed streams connected through the column API or named direct tray inputs
   */
  private List<StreamInterface> getAllExternalFeedStreams() {
    List<StreamInterface> externalFeeds = new ArrayList<>();
    for (List<StreamInterface> feedList : feedStreams.values()) {
      for (StreamInterface feed : feedList) {
        addStreamIfMissingByIdentity(externalFeeds, feed);
      }
    }
    for (List<StreamInterface> directFeedList : directExternalFeedStreams.values()) {
      for (StreamInterface directFeed : directFeedList) {
        addStreamIfMissingByIdentity(externalFeeds, directFeed);
      }
    }
    return externalFeeds;
  }

  /**
   * Capture named streams added directly to trays before the column connects internal traffic.
   */
  private void captureDirectExternalTrayFeeds() {
    for (int trayIndex = 0; trayIndex < trays.size(); trayIndex++) {
      List<StreamInterface> externalFeeds = getExternalFeedStreams(trayIndex);
      SimpleTray tray = trays.get(trayIndex);
      for (int streamIndex = 0; streamIndex < tray.getNumberOfInputStreams(); streamIndex++) {
        StreamInterface stream = tray.getStream(streamIndex);
        if (isUnregisteredExternalTrayFeed(stream, externalFeeds)) {
          List<StreamInterface> directFeeds = directExternalFeedStreams.get(trayIndex);
          if (directFeeds == null) {
            directFeeds = new ArrayList<>();
            directExternalFeedStreams.put(trayIndex, directFeeds);
          }
          directFeeds.add(stream);
          externalFeeds.add(stream);
        }
      }
    }
  }

  /**
   * Reset tray inputs to caller-supplied feeds before generated internal traffic is relinked.
   */
  private void resetTrayInputsToExternalFeeds() {
    for (int trayIndex = 0; trayIndex < trays.size(); trayIndex++) {
      List<StreamInterface> trayInputs = new ArrayList<>(getExternalFeedStreams(trayIndex));
      for (ColumnPumparound pumparound : pumparounds) {
        StreamInterface returnStream = pumparound.getReturnStream();
        if (pumparound.getReturnTrayNumber() == trayIndex && returnStream != null) {
          trayInputs.add(returnStream);
        }
      }
      trays.get(trayIndex).resetInputStreams(trayInputs);
    }
  }

  /**
   * Get the lowest tray index containing an external feed stream.
   *
   * @return first external feed tray index
   */
  private int getFirstExternalFeedTrayNumber() {
    int firstFeedTrayNumber = numberOfTrays;
    for (Integer trayIndex : feedStreams.keySet()) {
      firstFeedTrayNumber = Math.min(firstFeedTrayNumber, trayIndex.intValue());
    }
    for (Integer trayIndex : directExternalFeedStreams.keySet()) {
      firstFeedTrayNumber = Math.min(firstFeedTrayNumber, trayIndex.intValue());
    }
    return firstFeedTrayNumber;
  }

  /**
   * Add a stream to a list if the exact object is not already present.
   *
   * @param streams stream list to update
   * @param candidate stream to add
   */
  private void addStreamIfMissingByIdentity(List<StreamInterface> streams,
      StreamInterface candidate) {
    if (!containsStreamByIdentity(streams, candidate)) {
      streams.add(candidate);
    }
  }

  /**
   * Check whether a list already contains a stream object by identity.
   *
   * @param streams stream list to inspect
   * @param candidate stream to find
   * @return {@code true} if the exact stream object is present
   */
  private boolean containsStreamByIdentity(List<StreamInterface> streams,
      StreamInterface candidate) {
    for (StreamInterface stream : streams) {
      if (stream == candidate) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether a tray input should be treated as a legacy direct external feed.
   *
   * @param stream tray input stream to inspect
   * @param knownExternalFeeds external streams already identified
   * @return {@code true} if the stream looks like a named direct external feed
   */
  private boolean isUnregisteredExternalTrayFeed(StreamInterface stream,
      List<StreamInterface> knownExternalFeeds) {
    if (stream == null || containsStreamByIdentity(knownExternalFeeds, stream)) {
      return false;
    }
    String streamName = stream.getName();
    if (streamName == null || streamName.trim().isEmpty()) {
      return false;
    }
    return !isInternalTrayTrafficStreamName(streamName);
  }

  /**
   * Check whether a stream name belongs to generated internal column traffic.
   *
   * @param streamName stream name to inspect
   * @return {@code true} when the name is reserved for generated internal traffic
   */
  private boolean isInternalTrayTrafficStreamName(String streamName) {
    return streamName.startsWith("Split Stream_") || streamName.startsWith("naphtali gas ")
        || streamName.startsWith("naphtali liquid ");
  }

  /**
   * Get the terminal top product draw used by product-draw residual diagnostics.
   *
   * @return synchronized top product draw stream, or the top tray vapor outlet before a solve has
   *         synchronized product draws
   */
  StreamInterface getTerminalGasProductDrawStream() {
    if (terminalGasProductDrawStream != null) {
      return terminalGasProductDrawStream;
    }
    if (numberOfTrays <= 0) {
      return gasOutStream;
    }
    return trays.get(numberOfTrays - 1).getGasOutStream();
  }

  /**
   * Get the terminal bottom product draw used by product-draw residual diagnostics.
   *
   * @return synchronized bottom product draw stream, or the bottom tray liquid outlet before a
   *         solve has synchronized product draws
   */
  StreamInterface getTerminalLiquidProductDrawStream() {
    if (terminalLiquidProductDrawStream != null) {
      return terminalLiquidProductDrawStream;
    }
    if (numberOfTrays <= 0) {
      return liquidOutStream;
    }
    return trays.get(0).getLiquidOutStream();
  }

  /**
   * Override terminal product draw streams for package-level residual diagnostics.
   *
   * @param topProductDraw top product draw stream to use in diagnostics
   * @param bottomProductDraw bottom product draw stream to use in diagnostics
   */
  void setTerminalProductDrawStreamsForDiagnostics(StreamInterface topProductDraw,
      StreamInterface bottomProductDraw) {
    terminalGasProductDrawStream = topProductDraw;
    terminalLiquidProductDrawStream = bottomProductDraw;
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      inletFlow += feed.getThermoSystem().getFlowRate(unit);
    }
    double outletFlow = getGasOutStream().getThermoSystem().getFlowRate(unit)
        + getLiquidOutStream().getThermoSystem().getFlowRate(unit);
    for (StreamInterface sideDrawStream : getSideDrawStreams()) {
      outletFlow += sideDrawStream.getThermoSystem().getFlowRate(unit);
    }
    return outletFlow - inletFlow;
  }

  /**
   * <p>
   * getReboiler.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.distillation.Reboiler} object
   */
  public Reboiler getReboiler() {
    return (Reboiler) trays.get(0);
  }

  /**
   * <p>
   * getCondenser.
   * </p>
   *
   * @return a {@link neqsim.process.equipment.distillation.Condenser} object
   */
  public Condenser getCondenser() {
    return (Condenser) trays.get(trays.size() - 1);
  }

  /**
   * Configure the condenser operating mode.
   *
   * @param mode condenser operating mode
   * @throws IllegalStateException if the column has no condenser
   * @throws IllegalArgumentException if mode is {@code null} or requires more data
   */
  public void setCondenserMode(CondenserMode mode) {
    Condenser condenser = requireCondenser();
    if (mode == CondenserMode.PARTIAL) {
      condenser.setSeparation_with_liquid_reflux(false, 0.0, "kg/hr");
      condenser.setTotalCondenser(false);
    } else if (mode == CondenserMode.TOTAL) {
      condenser.setSeparation_with_liquid_reflux(false, 0.0, "kg/hr");
      condenser.setTotalCondenser(true);
    } else if (mode == CondenserMode.LIQUID_REFLUX_SPLIT) {
      throw new IllegalArgumentException(
          "Use setCondenserLiquidReflux(value, unit) to configure liquid reflux split mode");
    } else {
      throw new IllegalArgumentException("Condenser mode cannot be null");
    }
    setDoInitializion(true);
  }

  /**
   * Configure a partial condenser with a fixed liquid reflux split.
   *
   * @param value fixed liquid reflux flow rate
   * @param unit flow-rate unit for the fixed reflux value
   * @throws IllegalStateException if the column has no condenser
   */
  public void setCondenserLiquidReflux(double value, String unit) {
    Condenser condenser = requireCondenser();
    condenser.setTotalCondenser(false);
    condenser.setSeparation_with_liquid_reflux(true, value, unit);
    setDoInitializion(true);
  }

  /**
   * Get the configured condenser operating mode.
   *
   * @return condenser operating mode
   * @throws IllegalStateException if the column has no condenser
   */
  public CondenserMode getCondenserMode() {
    Condenser condenser = requireCondenser();
    if (condenser.isSeparation_with_liquid_reflux()) {
      return CondenserMode.LIQUID_REFLUX_SPLIT;
    }
    return condenser.isTotalCondenser() ? CondenserMode.TOTAL : CondenserMode.PARTIAL;
  }

  /**
   * Configure the reboiler operating mode.
   *
   * @param mode reboiler operating mode
   * @throws IllegalStateException if the column has no reboiler
   * @throws IllegalArgumentException if mode is {@code null} or requires more data
   */
  public void setReboilerMode(ReboilerMode mode) {
    requireReboiler();
    if (mode == ReboilerMode.EQUILIBRIUM) {
      getReboiler().clearRefluxRatio();
      setDoInitializion(true);
    } else if (mode == ReboilerMode.VAPOR_BOILUP_RATIO) {
      throw new IllegalArgumentException(
          "Use setReboilerVaporBoilupRatio(ratio) to configure vapor boilup ratio mode");
    } else {
      throw new IllegalArgumentException("Reboiler mode cannot be null");
    }
  }

  /**
   * Configure the reboiler with an explicit vapor boilup/reflux ratio.
   *
   * @param ratio finite non-negative boilup/reflux ratio
   * @throws IllegalArgumentException if ratio is negative or not finite
   * @throws IllegalStateException if the column has no reboiler
   */
  public void setReboilerVaporBoilupRatio(double ratio) {
    if (!Double.isFinite(ratio) || ratio < 0.0) {
      throw new IllegalArgumentException("Reboiler vapor boilup ratio must be finite and >= 0");
    }
    requireReboiler().setRefluxRatio(ratio);
    setDoInitializion(true);
  }

  /**
   * Get the configured reboiler operating mode.
   *
   * @return reboiler operating mode
   * @throws IllegalStateException if the column has no reboiler
   */
  public ReboilerMode getReboilerMode() {
    return requireReboiler().isRefluxSet() ? ReboilerMode.VAPOR_BOILUP_RATIO
        : ReboilerMode.EQUILIBRIUM;
  }

  /**
   * Get the condenser or fail with a setup-oriented error.
   *
   * @return condenser tray
   * @throws IllegalStateException if the column has no condenser
   */
  private Condenser requireCondenser() {
    if (!hasCondenser) {
      throw new IllegalStateException("Column has no condenser");
    }
    return getCondenser();
  }

  /**
   * Get the reboiler or fail with a setup-oriented error.
   *
   * @return reboiler tray
   * @throws IllegalStateException if the column has no reboiler
   */
  private Reboiler requireReboiler() {
    if (!hasReboiler) {
      throw new IllegalStateException("Column has no reboiler");
    }
    return getReboiler();
  }

  /**
   * <p>
   * Getter for the field <code>reboilerTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getReboilerTemperature() {
    return reboilerTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>reboilerTemperature</code>.
   * </p>
   *
   * @param reboilerTemperature a double
   */
  public void setReboilerTemperature(double reboilerTemperature) {
    this.reboilerTemperature = reboilerTemperature;
    if (hasReboiler) {
      getReboiler().setOutTemperature(reboilerTemperature);
    }
  }

  /**
   * Set the reboiler outlet temperature with unit conversion.
   *
   * @param reboilerTemperature reboiler outlet temperature
   * @param unit temperature unit, for example {@code "K"} or {@code "C"}
   * @throws IllegalArgumentException if the temperature unit is unsupported
   */
  public void setReboilerTemperature(double reboilerTemperature, String unit) {
    setReboilerTemperature(new TemperatureUnit(reboilerTemperature, unit).getValue("K"));
  }

  /**
   * <p>
   * Getter for the field <code>condenserTemperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getCondenserTemperature() {
    return condenserTemperature;
  }

  /**
   * <p>
   * Setter for the field <code>condenserTemperature</code>.
   * </p>
   *
   * @param condenserTemperature a double
   */
  public void setCondenserTemperature(double condenserTemperature) {
    this.condenserTemperature = condenserTemperature;
    if (hasCondenser) {
      getCondenser().setOutTemperature(condenserTemperature);
    }
  }

  /**
   * Set the condenser outlet temperature with unit conversion.
   *
   * @param condenserTemperature condenser outlet temperature
   * @param unit temperature unit, for example {@code "K"} or {@code "C"}
   * @throws IllegalArgumentException if the temperature unit is unsupported
   */
  public void setCondenserTemperature(double condenserTemperature, String unit) {
    setCondenserTemperature(new TemperatureUnit(condenserTemperature, unit).getValue("K"));
  }

  /**
   * <p>
   * isDoInitializion.
   * </p>
   *
   * @return a boolean
   */
  public boolean isDoInitializion() {
    return doInitializion;
  }

  /**
   * <p>
   * Setter for the field <code>doInitializion</code>.
   * </p>
   *
   * @param doInitializion a boolean
   */
  public void setDoInitializion(boolean doInitializion) {
    this.doInitializion = doInitializion;
  }

  /** {@inheritDoc} */
  @Override
  public MechanicalDesign getMechanicalDesign() {
    return mechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    mechanicalDesign = new DistillationColumnMechanicalDesign(this);
  }

  /**
   * Set temperature convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setTemperatureTolerance(double tol) {
    this.temperatureTolerance = tol;
    this.temperatureToleranceCustomized = true;
  }

  /**
   * Set mass balance convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setMassBalanceTolerance(double tol) {
    this.massBalanceTolerance = tol;
    this.massBalanceToleranceCustomized = true;
  }

  /**
   * Set enthalpy balance convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setEnthalpyBalanceTolerance(double tol) {
    this.enthalpyBalanceTolerance = tol;
    this.enthalpyBalanceToleranceCustomized = true;
  }

  /**
   * Set the scaled MESH residual tolerance used when MESH residual gating is enabled.
   *
   * @param tol positive finite tolerance
   * @throws IllegalArgumentException if the tolerance is not positive and finite
   */
  public void setMeshResidualTolerance(double tol) {
    if (!Double.isFinite(tol) || tol <= 0.0) {
      throw new IllegalArgumentException("MESH residual tolerance must be positive and finite");
    }
    this.meshResidualTolerance = tol;
  }

  /**
   * Set the product-draw residual tolerance used when MESH residual gating is enabled.
   *
   * @param tol positive finite product-draw residual tolerance
   * @throws IllegalArgumentException if the tolerance is not positive and finite
   */
  public void setMeshProductDrawResidualTolerance(double tol) {
    if (!Double.isFinite(tol) || tol <= 0.0) {
      throw new IllegalArgumentException(
          "MESH product draw residual tolerance must be positive and finite");
    }
    this.meshProductDrawResidualTolerance = tol;
  }

  /**
   * Restore adaptive default tolerances, discarding manual overrides.
   */
  public void resetToleranceOverrides() {
    temperatureToleranceCustomized = false;
    massBalanceToleranceCustomized = false;
    enthalpyBalanceToleranceCustomized = false;
    temperatureTolerance = DEFAULT_TEMPERATURE_TOLERANCE;
    massBalanceTolerance = DEFAULT_MASS_BALANCE_TOLERANCE;
    enthalpyBalanceTolerance = DEFAULT_ENTHALPY_BALANCE_TOLERANCE;
    meshResidualTolerance = DEFAULT_MESH_RESIDUAL_TOLERANCE;
    meshProductDrawResidualTolerance = DEFAULT_MESH_PRODUCT_DRAW_RESIDUAL_TOLERANCE;
    enforceMeshResidualTolerance = false;
    enforceMeshResidualToleranceCustomized = false;
  }

  /**
   * Check mass balance for all components.
   *
   * @return true if mass balance is within 1e-6
   */
  public boolean massBalanceCheck() {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
      }
      massOutput[i] = trays.get(i).getThermoSystem().getFlowRate("kg/hr");
      massBalance[i] = massInput[i] - massOutput[i];

      System.out.println("Tray " + i + ": #in=" + numberOfInputStreams + ", massIn=" + massInput[i]
          + ", massOut=" + massOutput[i] + ", balance=" + massBalance[i]);
    }
    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    return (massError <= 1e-6);
  }

  /**
   * Check mass balance for a specific component.
   *
   * @param componentName the component name
   * @return true if mass balance is within 1e-6
   */
  public boolean componentMassBalanceCheck(String componentName) {
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        for (int k = 0; k < trays.get(i).getStream(j).getFluid().getNumberOfPhases(); k++) {
          massInput[i] += trays.get(i).getStream(j).getFluid().getPhase(k)
              .getComponent(componentName).getFlowRate("kg/hr");
        }
      }
      // outputs
      for (int k = 0; k < trays.get(i).getGasOutStream().getFluid().getNumberOfPhases(); k++) {
        massOutput[i] += trays.get(i).getGasOutStream().getFluid().getPhase(k)
            .getComponent(componentName).getFlowRate("kg/hr");
      }
      for (int k = 0; k < trays.get(i).getLiquidOutStream().getFluid().getNumberOfPhases(); k++) {
        massOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getPhase(k)
            .getComponent(componentName).getFlowRate("kg/hr");
      }

      massBalance[i] = massInput[i] - massOutput[i];
      System.out.println(
          "Tray " + i + ", comp=" + componentName + ", #in=" + numberOfInputStreams + ", massIn="
              + massInput[i] + ", massOut=" + massOutput[i] + ", balance=" + massBalance[i]);
    }

    double massError = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      massError += Math.abs(massBalance[i]);
    }
    return (massError < 1e-6);
  }

  /**
   * Calculate the relative mass balance error across the column.
   *
   * @return maximum of tray-wise and overall relative mass imbalance
   */
  public double getMassBalanceError() {
    double trayRelativeError = 0.0;
    double totalInlet = 0.0;
    double totalResidual = 0.0;

    for (int i = 0; i < numberOfTrays; i++) {
      double inlet = 0.0;
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        inlet += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
      }

      double outlet = trays.get(i).getThermoSystem().getFlowRate("kg/hr");

      double absInlet = Math.abs(inlet);
      double imbalance = Math.abs(inlet - outlet);
      if (absInlet > 1e-12) {
        trayRelativeError = Math.max(trayRelativeError, imbalance / absInlet);
      }
      totalInlet += absInlet;
      totalResidual += imbalance;
    }

    double columnRelative = totalInlet > 1e-12 ? totalResidual / totalInlet : totalResidual;
    return Math.max(trayRelativeError, columnRelative);
  }

  /**
   * Calculates the relative mass imbalance between external feed streams and public product
   * streams.
   *
   * @return relative external mass imbalance based on kg/hr flow rates
   */
  private double getExternalMassBalanceError() {
    double totalFeedMass = 0.0;
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      totalFeedMass += Math.abs(feed.getThermoSystem().getFlowRate("kg/hr"));
    }
    double externalMassBalance = Math.abs(getMassBalance("kg/hr"));
    return totalFeedMass > 1.0e-12 ? externalMassBalance / totalFeedMass : externalMassBalance;
  }

  /**
   * Calculates the relative enthalpy imbalance across all trays.
   *
   * @return maximum of tray-wise and overall relative enthalpy imbalance
   */
  public double getEnergyBalanceError() {
    double trayRelativeError = 0.0;
    double totalInlet = 0.0;
    double totalResidual = 0.0;

    for (int i = 0; i < numberOfTrays; i++) {
      double inlet = 0.0;
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        inlet += getFiniteStreamEnthalpy(trays.get(i).getStream(j));
      }

      double outlet = getFiniteStreamEnthalpy(trays.get(i).getGasOutStream());
      outlet += getFiniteStreamEnthalpy(trays.get(i).getLiquidOutStream());

      if (trays.get(i) instanceof Reboiler) {
        inlet += getFiniteDiagnosticValue(((Reboiler) trays.get(i)).getDuty());
      } else if (trays.get(i) instanceof Condenser) {
        inlet += getFiniteDiagnosticValue(((Condenser) trays.get(i)).getDuty());
      }

      double absInlet = Math.abs(inlet);
      double imbalance = Math.abs(inlet - outlet);
      if (absInlet > 1e-12) {
        trayRelativeError = Math.max(trayRelativeError, imbalance / absInlet);
      }
      totalInlet += absInlet;
      totalResidual += imbalance;
    }

    double columnRelative = totalInlet > 1e-12 ? totalResidual / totalInlet : totalResidual;
    return Math.max(trayRelativeError, columnRelative);
  }

  /**
   * Read stream enthalpy for diagnostics, treating non-finite values as no contribution.
   *
   * @param stream stream to inspect
   * @return finite stream enthalpy contribution
   */
  private double getFiniteStreamEnthalpy(StreamInterface stream) {
    if (stream == null || stream.getThermoSystem() == null) {
      return 0.0;
    }
    double enthalpy = stream.getFluid().getEnthalpy();
    if (Double.isFinite(enthalpy)) {
      return enthalpy;
    }
    double flowRate = Math.abs(stream.getThermoSystem().getFlowRate("kg/hr"));
    if (flowRate <= 1.0e-12) {
      return 0.0;
    }
    return 0.0;
  }

  /**
   * Sanitize a scalar diagnostic contribution.
   *
   * @param value value to sanitize
   * @return value when finite, otherwise zero
   */
  private double getFiniteDiagnosticValue(double value) {
    return Double.isFinite(value) ? value : 0.0;
  }

  /** Maximum number of entries stored in the convergence history list. */
  private static final int MAX_CONVERGENCE_HISTORY = 500;

  /**
   * Append a residual snapshot to the convergence history, capping at
   * {@link #MAX_CONVERGENCE_HISTORY} entries.
   *
   * @param entry residual array to record
   */
  private void recordConvergence(double[] entry) {
    if (convergenceHistory != null && convergenceHistory.size() < MAX_CONVERGENCE_HISTORY) {
      convergenceHistory.add(entry);
    }
  }

  /**
   * Apply Murphree tray efficiency correction to the vapor leaving a tray. The correction blends
   * the equilibrium vapor composition with the inlet vapor composition:
   *
   * <pre>
   * y_i^out = y_i^in + E_MV * (y_i^eq - y_i^in)
   * </pre>
   *
   * where {@code E_MV} is the Murphree efficiency, {@code y_i^eq} is the equilibrium composition
   * from the flash, and {@code y_i^in} is the inlet vapor composition. When efficiency is 1.0, the
   * tray is ideal and no correction is applied. The correction is skipped for reboilers and
   * condensers (first and last trays).
   *
   * @param trayIndex index of the tray in the {@code trays} list
   */
  private void applyMurphreeCorrection(int trayIndex) {
    double emv = getEffectiveMurphreeEfficiency(trayIndex);
    if (emv >= 1.0 - 1e-10) {
      return; // ideal tray, no correction needed
    }
    // Skip reboiler (index 0) — always an equilibrium stage
    if (trayIndex <= 0) {
      return;
    }
    // Skip condenser (last index) if present
    if (hasCondenser && trayIndex >= numberOfTrays - 1) {
      return;
    }

    SystemInterface fluid = trays.get(trayIndex).getThermoSystem();
    if (fluid.getNumberOfPhases() < 2) {
      return;
    }

    // The inlet vapor to this tray comes from the tray below (index - 1)
    SystemInterface belowFluid = trays.get(trayIndex - 1).getThermoSystem();
    if (belowFluid.getNumberOfPhases() < 2) {
      return;
    }

    int nc = fluid.getPhase(0).getNumberOfComponents();
    // Equilibrium compositions from flash
    double[] yEq = new double[nc];
    double[] yIn = new double[nc];
    for (int j = 0; j < nc; j++) {
      yEq[j] = fluid.getPhase(0).getComponent(j).getx();
      yIn[j] = belowFluid.getPhase(0).getComponent(j).getx();
    }

    // Apply Murphree to vapor: y_actual = y_in + E*(y_eq - y_in)
    double[] yActual = new double[nc];
    double sumY = 0.0;
    for (int j = 0; j < nc; j++) {
      yActual[j] = yIn[j] + emv * (yEq[j] - yIn[j]);
      yActual[j] = Math.max(0.0, yActual[j]);
      sumY += yActual[j];
    }
    if (sumY > 1e-15) {
      for (int j = 0; j < nc; j++) {
        yActual[j] /= sumY;
      }
    }

    // Build corrected gas stream from phase clone — total moles unchanged.
    // The liquid stream is NOT corrected: it uses the equilibrium result from the
    // flash.
    // This is the standard post-correction approach for Murphree efficiency in
    // bubble-point sequential methods.
    double vaporMoles = fluid.getPhase(0).getNumberOfMolesInPhase();
    SystemInterface gasSystem = fluid.phaseToSystem(0);
    for (int j = 0; j < nc; j++) {
      gasSystem.getPhase(0).getComponent(j).setx(yActual[j]);
      gasSystem.getPhase(0).getComponent(j).setNumberOfMolesInPhase(yActual[j] * vaporMoles);
      gasSystem.getPhase(0).getComponent(j).setNumberOfmoles(yActual[j] * vaporMoles);
    }
    gasSystem.setTotalNumberOfMoles(vaporMoles);
    gasSystem.init(0);
    gasSystem.init(1);

    if (trays.get(trayIndex) instanceof SimpleTray) {
      trays.get(trayIndex).setCachedGasOutStream(new Stream("", gasSystem));
    }
  }

  /**
   * Blend the current stream update with the previous iterate using the provided relaxation factor.
   *
   * @param previous stream from the previous iteration (may be {@code null})
   * @param current current iteration stream
   * @param relaxation relaxation factor applied to the update
   * @return relaxed stream instance to be used in the next tear
   */
  private StreamInterface applyRelaxation(StreamInterface previous, StreamInterface current,
      double relaxation) {
    return applyRelaxationInternal(previous, current, relaxation, false);
  }

  /**
   * Blend streams using the fast unchanged-clone path for fixed-point sweeps.
   *
   * @param previous stream from the previous iteration (may be {@code null})
   * @param current current iteration stream
   * @param relaxation relaxation factor applied to the update
   * @return relaxed stream instance to be used in the next tear
   */
  private StreamInterface applyRelaxationFast(StreamInterface previous, StreamInterface current,
      double relaxation) {
    return applyRelaxationInternal(previous, current, relaxation, true);
  }

  /**
   * Blend the current stream update with the previous iterate.
   *
   * @param previous stream from the previous iteration (may be {@code null})
   * @param current current iteration stream
   * @param relaxation relaxation factor applied to the update
   * @param skipUnchangedReflash whether an unchanged clone can reuse the current stream flash state
   * @return relaxed stream instance to be used in the next tear
   */
  private StreamInterface applyRelaxationInternal(StreamInterface previous, StreamInterface current,
      double relaxation, boolean skipUnchangedReflash) {
    double maximumInternalFlow = getMaximumRelaxedInternalFlowKgPerHour();
    // Fast path: no damping needed; clone the already-flashed tray outlet.
    if (previous == null || relaxation >= 1.0) {
      StreamInterface relaxed = current.clone();
      boolean requiresReflash =
          internalTrafficCapActive || !Double.isFinite(relaxed.getFlowRate("kg/hr"));
      if (requiresReflash) {
        capStreamFlow(relaxed, maximumInternalFlow);
      }
      if (requiresReflash || !skipUnchangedReflash) {
        relaxed.run();
      }
      return relaxed;
    }

    StreamInterface relaxed = current.clone();
    double step = Math.max(0.0, Math.min(1.0, relaxation));
    double previousFlow =
        getRelaxedInternalFlow(previous.getFlowRate("kg/hr"), maximumInternalFlow);
    double currentFlow = getRelaxedInternalFlow(current.getFlowRate("kg/hr"), maximumInternalFlow);
    double mixedFlow = previousFlow + step * (currentFlow - previousFlow);
    mixedFlow = getRelaxedInternalFlow(mixedFlow, maximumInternalFlow);
    relaxed.setFlowRate(mixedFlow, "kg/hr");

    double mixedTemperature = previous.getTemperature("K")
        + step * (current.getTemperature("K") - previous.getTemperature("K"));
    relaxed.setTemperature(mixedTemperature, "K");

    double mixedPressure = previous.getPressure("bara")
        + step * (current.getPressure("bara") - previous.getPressure("bara"));
    relaxed.setPressure(mixedPressure, "bara");

    double[] zPrev = previous.getThermoSystem().getMolarComposition();
    double totalMolesPrev = previous.getThermoSystem().getTotalNumberOfMoles();
    double[] zCurr = current.getThermoSystem().getMolarComposition();
    double totalMolesCurr = current.getThermoSystem().getTotalNumberOfMoles();

    if (!canRelaxMolarComposition(previous, current, relaxed, zPrev, zCurr)) {
      relaxed.run();
      return relaxed;
    }

    double[] zMixed = new double[zPrev.length];
    double totalMolesMixed = 0.0;

    for (int i = 0; i < zPrev.length; i++) {
      double molesPrev_i = zPrev[i] * totalMolesPrev;
      double molesCurr_i = zCurr[i] * totalMolesCurr;
      double mixedMoles_i = molesPrev_i + step * (molesCurr_i - molesPrev_i);
      if (Double.isFinite(mixedMoles_i) && mixedMoles_i > 0.0) {
        zMixed[i] = mixedMoles_i;
        totalMolesMixed += mixedMoles_i;
      } else {
        zMixed[i] = 0.0;
      }
    }

    if (totalMolesMixed > 1e-12 && relaxed.getThermoSystem().getTotalNumberOfMoles() > 1e-100) {
      for (int i = 0; i < zMixed.length; i++) {
        zMixed[i] /= totalMolesMixed;
      }
      relaxed.getThermoSystem().setMolarComposition(zMixed);
    }

    relaxed.run();

    return relaxed;
  }

  /**
   * Check whether two stream compositions can be relaxed component-by-component.
   *
   * @param previous previous internal stream state
   * @param current current internal stream state
   * @param relaxed relaxed stream receiving the mixed composition
   * @param previousComposition previous molar composition vector
   * @param currentComposition current molar composition vector
   * @return {@code true} when all streams expose the same component count and composition length
   */
  private boolean canRelaxMolarComposition(StreamInterface previous, StreamInterface current,
      StreamInterface relaxed, double[] previousComposition, double[] currentComposition) {
    if (previous == null || current == null || relaxed == null || previousComposition == null
        || currentComposition == null) {
      return false;
    }
    int previousComponents = previous.getThermoSystem().getNumberOfComponents();
    int currentComponents = current.getThermoSystem().getNumberOfComponents();
    int relaxedComponents = relaxed.getThermoSystem().getNumberOfComponents();
    return previousComponents == currentComponents && currentComponents == relaxedComponents
        && previousComposition.length == previousComponents
        && currentComposition.length == currentComponents;
  }

  /**
   * Return a relaxed internal flow, applying emergency capping only when active or required.
   *
   * @param flow internal flow in kg/hr
   * @param maximumInternalFlow maximum emergency flow magnitude in kg/hr
   * @return uncapped finite flow during normal solves, otherwise capped flow
   */
  private double getRelaxedInternalFlow(double flow, double maximumInternalFlow) {
    if (!internalTrafficCapActive && Double.isFinite(flow)) {
      return flow;
    }
    return limitInternalFlow(flow, maximumInternalFlow);
  }

  /**
   * Calculate the relaxed-update flow cap from the current external feed flow.
   *
   * @return maximum allowed internal tear-stream flow in kg/hr
   */
  private double getMaximumRelaxedInternalFlowKgPerHour() {
    return Math.max(1.0e3,
        getTotalExternalFeedFlowKgPerHour() * MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO);
  }

  /**
   * Limit a flow to the configured internal traffic cap.
   *
   * @param flow flow rate to limit
   * @param maximumFlow maximum absolute flow rate
   * @return finite capped flow rate
   */
  private double limitInternalFlow(double flow, double maximumFlow) {
    if (!Double.isFinite(flow)) {
      return 0.0;
    }
    double cappedMagnitude = Math.min(Math.abs(flow), Math.max(0.0, maximumFlow));
    return Math.signum(flow) * cappedMagnitude;
  }

  /**
   * Cap a stream flow rate if it exceeds the internal traffic guard.
   *
   * @param stream stream to cap
   * @param maximumFlow maximum absolute flow rate in kg/hr
   */
  private void capStreamFlow(StreamInterface stream, double maximumFlow) {
    double flow = stream.getFlowRate("kg/hr");
    double cappedFlow = limitInternalFlow(flow, maximumFlow);
    if (Double.isFinite(cappedFlow) && Math.abs(cappedFlow - flow) > 1.0e-12) {
      stream.setFlowRate(cappedFlow, "kg/hr");
    }
  }

  /**
   * Finalise a successful solver run by updating iteration metrics and product streams.
   *
   * @param id calculation identifier
   * @param iterations number of iterations performed
   * @param temperatureResidual final average temperature residual
   * @param massResidual final relative mass residual
   * @param energyResidual final relative energy residual
   * @param startTime nano time when the solve started
   */
  private void finalizeSolve(UUID id, int iterations, double temperatureResidual,
      double massResidual, double energyResidual, long startTime) {
    err = temperatureResidual;
    lastIterationCount = iterations;
    lastTemperatureResidual = temperatureResidual;
    lastMassResidual = massResidual;
    lastEnergyResidual = energyResidual;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    lastUsedFeedFlashFallback = false;

    gasOutStream
        .setThermoSystem(trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    liquidOutStream.setCalculationIdentifier(id);

    captureTerminalProductDrawStreams(id);
    boolean productReconciled = updateProductsFromExternalComponentBalance(id);
    lastInternalTrafficRatio = getInternalTrafficRatio();
    if (!internalTrafficSatisfied()) {
      capInternalTrayTraffic();
      lastInternalTrafficGuardReached = true;
      lastInternalTrafficRatio =
          Math.min(getInternalTrafficRatio(), MAX_RELAXED_INTERNAL_TRAFFIC_TO_FEED_RATIO);
    }
    boolean fallbackProductsApplied = false;
    if ((!internalTrafficSatisfied() || lastMassResidual > getEffectiveMassBalanceTolerance()
        || getExternalMassBalanceError() > getEffectiveMassBalanceTolerance()
        || bottomProductPhaseInvalid()) && updateProductsFromOverallFeedFlash(id)) {
      fallbackProductsApplied = true;
    }
    lastUsedFeedFlashFallback = fallbackProductsApplied;
    lastMassResidual = Math.max(lastMassResidual, getExternalMassBalanceError());
    if (lastInternalTrafficGuardReached) {
      lastMassResidual = Math.max(lastMassResidual,
          lastInternalTrafficRatio / MAX_SOLVED_INTERNAL_TRAFFIC_TO_FEED_RATIO);
    }

    boolean anyFeedMultiPhase = false;
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface feed : feeds) {
        if (feed.getThermoSystem().doMultiPhaseCheck()) {
          anyFeedMultiPhase = true;
          break;
        }
      }
      if (anyFeedMultiPhase) {
        break;
      }
    }
    if (anyFeedMultiPhase) {
      gasOutStream.getThermoSystem().setMultiPhaseCheck(true);
      liquidOutStream.getThermoSystem().setMultiPhaseCheck(true);
    }

    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setCalculationIdentifier(id);
    }
    if (isEffectiveMeshResidualToleranceEnforced() || lastMeshResidual != null) {
      updateMeshResiduals();
    }
    updateLastSolveStatus(productReconciled, fallbackProductsApplied);
    setCalculationIdentifier(id);
  }

  /**
   * Update the strict solve status after product handling and residual diagnostics are current.
   *
   * @param productReconciled {@code true} if public products were materially reconciled
   * @param fallbackProductsApplied {@code true} if fallback products were generated
   */
  private void updateLastSolveStatus(boolean productReconciled, boolean fallbackProductsApplied) {
    if (fallbackProductsApplied) {
      setLastSolveStatus(SolveStatus.FALLBACK_PRODUCTS,
          "Public products were generated from guarded fallback flash products");
      return;
    }
    if (lastInternalTrafficGuardReached) {
      setLastSolveStatus(SolveStatus.FAILED,
          "Internal tray traffic exceeded the rigorous solved-state guard");
      return;
    }
    if (!residualConvergenceSatisfied()) {
      setLastSolveStatus(SolveStatus.FAILED, "Residual convergence gates were not satisfied");
      return;
    }
    if (productReconciled) {
      setLastSolveStatus(SolveStatus.RECONCILED_PRODUCTS,
          "Public products were materially reconciled after the tray solve");
      return;
    }
    setLastSolveStatus(SolveStatus.RIGOROUS_CONVERGED,
        "Tray solution satisfies active rigorous convergence gates");
  }

  /**
   * Store the latest solve status and explanatory reason.
   *
   * @param status solve status to store
   * @param reason concise status reason
   */
  private void setLastSolveStatus(SolveStatus status, String reason) {
    lastSolveStatus = status == null ? SolveStatus.FAILED : status;
    lastSolveStatusReason = reason == null ? "" : reason;
  }

  /**
   * Check whether the bottom product is phase-inconsistent for a reboiled column.
   *
   * @return {@code true} when a reboiled column does not expose a liquid-like bottom product
   */
  private boolean bottomProductPhaseInvalid() {
    return hasReboiler && !isLiquidLikeProduct(liquidOutStream);
  }

  /**
   * Check whether a product stream has a liquid-like thermodynamic phase.
   *
   * @param productStream stream to inspect
   * @return {@code true} when the stream contains an oil, liquid, or aqueous phase
   */
  private boolean isLiquidLikeProduct(StreamInterface productStream) {
    if (productStream == null || productStream.getThermoSystem() == null) {
      return false;
    }
    SystemInterface system = productStream.getThermoSystem();
    return system.hasPhaseType("oil") || system.hasPhaseType("liquid")
        || system.hasPhaseType("aqueous");
  }

  /** Cap cached internal tray outlet streams to the emergency traffic limit. */
  private void capInternalTrayTraffic() {
    double maximumInternalFlow = getMaximumRelaxedInternalFlowKgPerHour();
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      capStreamFlow(trays.get(trayIndex).getGasOutStream(), maximumInternalFlow);
      capStreamFlow(trays.get(trayIndex).getLiquidOutStream(), maximumInternalFlow);
    }
  }

  /**
   * Updates products so the exposed streams close the external component balance.
   *
   * @param id calculation identifier to assign to the updated stream
   * @return {@code true} if product component amounts were materially changed
   */
  private boolean updateProductsFromExternalComponentBalance(UUID id) {
    if (getAllExternalFeedStreams().isEmpty() || gasOutStream == null || liquidOutStream == null) {
      return false;
    }

    double[] feedComponentMoles = getFeedComponentMoles();
    double[] sideDrawComponentMoles = getSideDrawComponentMoles(feedComponentMoles.length);
    // Sum only gas-phase moles for the top product and only liquid-like phase moles for the bottom
    // product. Tray terminal thermo systems can carry both phases (e.g. a reboiler holding a 2-
    // phase oil/gas system), and summing across all phases incorrectly attributes ascending vapor
    // moles to the bottom product (and descending reflux moles to the top product). The
    // accelerator solvers (Inside-Out, Newton, SUM_RATES) are more sensitive to this than DAMPED
    // because their tray-0 gas fraction is proportionally larger, which drove a degenerate per-
    // component scaling and a fallback to overall feed flash on small heavy-rich columns.
    //
    // Source the moles directly from the tray terminal systems rather than from the public
    // streams. The public streams are clones of these tray systems at this point, but reading the
    // tray systems makes the data flow explicit and defends against any pre-call mutation of the
    // public stream's thermo system that may collapse a two-phase tray system into a single
    // phase.
    SystemInterface topTraySystem =
        trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem();
    SystemInterface bottomTraySystem = trays.get(0).getLiquidOutStream().getThermoSystem();
    double[] topProductComponentMoles = getPhaseFilteredComponentMoles(topTraySystem, true);
    double[] bottomProductComponentMoles = getPhaseFilteredComponentMoles(bottomTraySystem, false);
    if (feedComponentMoles.length != topProductComponentMoles.length
        || feedComponentMoles.length != bottomProductComponentMoles.length) {
      return false;
    }

    double feedTotalMoles = 0.0;
    double currentProductTotalMoles = 0.0;
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      feedTotalMoles += Math.max(0.0, feedComponentMoles[componentIndex]);
      currentProductTotalMoles += Math.max(0.0, topProductComponentMoles[componentIndex])
          + Math.max(0.0, bottomProductComponentMoles[componentIndex]);
    }
    if (feedTotalMoles <= 1.0e-20 || currentProductTotalMoles <= 1.0e-20) {
      return false;
    }

    double[] balancedTopProductComponentMoles = new double[feedComponentMoles.length];
    double[] balancedBottomProductComponentMoles = new double[feedComponentMoles.length];
    double topTotalMoles = 0.0;
    double bottomTotalMoles = 0.0;
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      double currentTopMoles = Math.max(0.0, topProductComponentMoles[componentIndex]);
      double currentBottomMoles = Math.max(0.0, bottomProductComponentMoles[componentIndex]);
      double currentComponentMoles = currentTopMoles + currentBottomMoles;
      if (currentComponentMoles > 1.0e-20) {
        double terminalProductMoles = Math.max(0.0,
            feedComponentMoles[componentIndex] - sideDrawComponentMoles[componentIndex]);
        double componentScale = terminalProductMoles / currentComponentMoles;
        balancedTopProductComponentMoles[componentIndex] = currentTopMoles * componentScale;
        balancedBottomProductComponentMoles[componentIndex] = currentBottomMoles * componentScale;
      }
      topTotalMoles += balancedTopProductComponentMoles[componentIndex];
      bottomTotalMoles += balancedBottomProductComponentMoles[componentIndex];
    }

    if (topTotalMoles <= 1.0e-20 || bottomTotalMoles <= 1.0e-20) {
      return false;
    }

    boolean materialChange =
        componentMolesMateriallyDiffer(topProductComponentMoles, balancedTopProductComponentMoles)
            || componentMolesMateriallyDiffer(bottomProductComponentMoles,
                balancedBottomProductComponentMoles);
    updateProductStreamFromComponentMoles(gasOutStream, balancedTopProductComponentMoles, id);
    updateProductStreamFromComponentMoles(liquidOutStream, balancedBottomProductComponentMoles, id);

    // Per-component scaling can shift a borderline bottom composition across the dew-point
    // boundary at the reboiler T/P, leaving the bottom stream single-phase gas. When that
    // happens, retry with a uniform scalar scale that preserves the oil-phase composition
    // shape from tray 0 (which is liquid by construction). This recovers Inside-Out / Newton
    // results on small heavy-rich columns without resorting to the overall feed-flash fallback.
    if (hasReboiler && !isLiquidLikeProduct(liquidOutStream)) {
      double terminalProductTotalMoles = 0.0;
      for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
        terminalProductTotalMoles += Math.max(0.0,
            feedComponentMoles[componentIndex] - sideDrawComponentMoles[componentIndex]);
      }
      if (terminalProductTotalMoles > 1.0e-20 && currentProductTotalMoles > 1.0e-20) {
        double overallScale = terminalProductTotalMoles / currentProductTotalMoles;
        for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
          balancedTopProductComponentMoles[componentIndex] =
              Math.max(0.0, topProductComponentMoles[componentIndex]) * overallScale;
          balancedBottomProductComponentMoles[componentIndex] =
              Math.max(0.0, bottomProductComponentMoles[componentIndex]) * overallScale;
        }
        updateProductStreamFromComponentMoles(gasOutStream, balancedTopProductComponentMoles, id);
        updateProductStreamFromComponentMoles(liquidOutStream, balancedBottomProductComponentMoles,
            id);
        materialChange = true;
      }
    }

    // Phase-preserving rescue. If the bottom product still flashes single-phase gas at the
    // reboiler T/P after both per-component and scalar retries, force the public bottom stream
    // to a single liquid phase using the converged tray-0 oil-phase moles. The tray system was
    // two-phase by construction, so the moles are valid; only the in-isolation re-flash at the
    // reboiler T/P is producing a spurious vapor product. This avoids the overall-feed-flash
    // fallback for Inside-Out, Matrix-IO, and Newton solvers on small heavy-rich columns.
    if (hasReboiler && !isLiquidLikeProduct(liquidOutStream)) {
      updateProductStreamWithForcedPhase(liquidOutStream, balancedBottomProductComponentMoles,
          "liquid", id);
      materialChange = true;
    }
    return materialChange;
  }

  /**
   * Check whether two component-flow vectors differ materially for solve-status classification.
   *
   * @param before component mole amounts before reconciliation
   * @param after component mole amounts after reconciliation
   * @return {@code true} when relative component drift exceeds the status tolerance
   */
  private boolean componentMolesMateriallyDiffer(double[] before, double[] after) {
    if (before == null || after == null || before.length != after.length) {
      return true;
    }
    double difference = 0.0;
    double scale = 0.0;
    for (int componentIndex = 0; componentIndex < before.length; componentIndex++) {
      double beforeValue = Math.max(0.0, before[componentIndex]);
      double afterValue = Math.max(0.0, after[componentIndex]);
      difference += Math.abs(afterValue - beforeValue);
      scale += Math.abs(beforeValue) + Math.abs(afterValue);
    }
    if (scale <= 1.0e-20) {
      return difference > 1.0e-20;
    }
    return difference / scale > PRODUCT_RECONCILIATION_STATUS_TOLERANCE;
  }

  /**
   * Captures the raw terminal tray draw streams used by product-draw residual diagnostics.
   *
   * <p>
   * Product reconciliation and guarded fallback updates can change the public column products after
   * the tray solver has produced terminal draws. MESH diagnostics must compare public products to
   * these raw terminal draws rather than to synchronized clones of the public products.
   * </p>
   *
   * @param id calculation identifier to assign to the captured draw streams
   */
  private void captureTerminalProductDrawStreams(UUID id) {
    terminalGasProductDrawStream =
        new Stream("", trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    terminalGasProductDrawStream.setCalculationIdentifier(id);
    terminalLiquidProductDrawStream =
        new Stream("", trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    terminalLiquidProductDrawStream.setCalculationIdentifier(id);
  }

  /**
   * Replaces a product stream fluid with the same thermodynamic model at the current stream
   * temperature and pressure but with specified component mole amounts.
   *
   * @param productStream stream to update
   * @param componentMoles component mole amounts on the stream-flow basis
   * @param id calculation identifier to assign after the update
   */
  private void updateProductStreamFromComponentMoles(StreamInterface productStream,
      double[] componentMoles, UUID id) {
    SystemInterface balancedSystem = productStream.getThermoSystem().clone();
    double productTemperature = productStream.getTemperature("K");
    double productPressure = productStream.getPressure("bara");
    balancedSystem.setMolarFlowRates(componentMoles);
    balancedSystem.setTemperature(productTemperature);
    balancedSystem.setPressure(productPressure, "bara");
    balancedSystem.init(0);
    ThermodynamicOperations operations = new ThermodynamicOperations(balancedSystem);
    operations.TPflash();
    balancedSystem.init(3);
    balancedSystem.initProperties();
    productStream.setThermoSystem(balancedSystem);
    productStream.setCalculationIdentifier(id);
  }

  /**
   * Replace a product stream fluid with the same thermodynamic model at the product stream's own
   * temperature and pressure, forcing a single phase identity instead of running a TP flash.
   *
   * <p>
   * Used as a phase-preserving rescue when an accelerator solver (Inside-Out, Matrix-IO, Newton)
   * converges to a tray-0 oil-phase composition that, when re-flashed in isolation at the reboiler
   * T/P, collapses to single-phase gas. The tray system itself was two-phase by construction, so
   * the moles drawn from it are valid; forcing the phase preserves the rigorous solver result and
   * avoids the spurious overall-feed-flash fallback that otherwise triggers via
   * {@code bottomProductPhaseInvalid()}.
   * </p>
   *
   * @param productStream stream to update
   * @param componentMoles component mole amounts on the stream-flow basis
   * @param phaseTypeName phase type description ("gas" or "liquid")
   * @param id calculation identifier to assign after the update
   */
  private void updateProductStreamWithForcedPhase(StreamInterface productStream,
      double[] componentMoles, String phaseTypeName, UUID id) {
    SystemInterface productSystem = productStream.getThermoSystem().clone();
    double productTemperature = productStream.getTemperature("K");
    double productPressure = productStream.getPressure("bara");
    productSystem.setMolarFlowRates(componentMoles);
    productSystem.setTemperature(productTemperature);
    productSystem.setPressure(productPressure, "bara");
    productSystem.init(0);
    setSingleProductPhaseType(productSystem, phaseTypeName);
    productSystem.init(1);
    productSystem.initProperties();
    setSingleProductPhaseType(productSystem, phaseTypeName);
    productStream.setThermoSystem(productSystem);
    productStream.setCalculationIdentifier(id);
  }

  /**
   * Update public products from an overall equilibrium flash of all external feeds.
   *
   * <p>
   * This fallback is used only after the tray solver has produced non-physical internal traffic. It
   * gives bounded, mass-conserving products for diagnostics without claiming that the rigorous tray
   * MESH problem has converged.
   * </p>
   *
   * @param id calculation identifier to assign to fallback products
   * @return {@code true} if fallback products were created
   */
  private boolean updateProductsFromOverallFeedFlash(UUID id) {
    List<StreamInterface> externalFeeds = getAllExternalFeedStreams();
    if (externalFeeds.isEmpty()) {
      return false;
    }

    Mixer fallbackMixer = new Mixer("distillation product fallback mixer");
    for (StreamInterface feed : externalFeeds) {
      fallbackMixer.addStream(feed);
    }
    fallbackMixer.run(id);
    SystemInterface fallbackSystem = fallbackMixer.getOutletStream().getThermoSystem().clone();
    fallbackSystem.setTemperature(getFallbackProductTemperature());
    fallbackSystem.setPressure(getFallbackProductPressure(), "bara");
    fallbackSystem.setMultiPhaseCheck(true);

    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(fallbackSystem);
      operations.TPflash();
      fallbackSystem.initProperties();
    } catch (Exception ex) {
      logger.warn("Overall-feed flash fallback failed for column {}", getName(), ex);
      return false;
    }

    int gasPhaseIndex = findPhaseIndex(fallbackSystem, "gas", 0);
    int liquidPhaseIndex = findLiquidPhaseIndex(fallbackSystem);
    if (gasPhaseIndex < 0 || liquidPhaseIndex < 0 || gasPhaseIndex == liquidPhaseIndex) {
      return updateProductsFromShortcutEquilibriumSplit(fallbackSystem, id);
    }

    gasOutStream.setThermoSystem(createNormalizedPhaseSystem(fallbackSystem, gasPhaseIndex));
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(createNormalizedPhaseSystem(fallbackSystem, liquidPhaseIndex));
    liquidOutStream.setCalculationIdentifier(id);
    return true;
  }

  /**
   * Create fallback products from a bounded shortcut equilibrium split.
   *
   * <p>
   * The split uses bottom terminal K-values and a Rachford-Rice vapor-fraction estimate to place
   * volatile components preferentially in the vapor and heavy components preferentially in the
   * liquid. It is only used when an overall TP flash does not expose both gas and liquid phases.
   * </p>
   *
   * @param feedSystem combined external feed system
   * @param id calculation identifier to assign to fallback products
   * @return {@code true} if shortcut fallback products were created
   */
  private boolean updateProductsFromShortcutEquilibriumSplit(SystemInterface feedSystem, UUID id) {
    double[] feedComponentMoles = getComponentMoles(feedSystem);
    double totalMoles = 0.0;
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      totalMoles += Math.max(0.0, feedComponentMoles[componentIndex]);
    }
    if (totalMoles <= 1.0e-20) {
      return false;
    }

    SystemInterface kSystem = feedSystem.clone();
    kSystem.setTemperature(getFallbackProductTemperature());
    kSystem.setPressure(getFallbackProductPressure(), "bara");
    kSystem.setNumberOfPhases(2);
    kSystem.init(0);
    kSystem.init(1);

    double vaporFraction = estimateShortcutVaporFraction(feedComponentMoles, kSystem,
        getCurrentProductVaporFraction());
    double vaporToLiquidRatio = vaporFraction / Math.max(1.0e-12, 1.0 - vaporFraction);
    double[] topComponentMoles = new double[feedComponentMoles.length];
    double[] bottomComponentMoles = new double[feedComponentMoles.length];
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      double feedMoles = Math.max(0.0, feedComponentMoles[componentIndex]);
      double kValue = Math.max(1.0e-8, kSystem.getComponent(componentIndex).getK());
      double denominator = 1.0 + vaporToLiquidRatio * kValue;
      if (!Double.isFinite(denominator) || denominator <= 1.0e-12) {
        denominator = 1.0e-12;
      }
      bottomComponentMoles[componentIndex] = feedMoles / denominator;
      topComponentMoles[componentIndex] = feedMoles - bottomComponentMoles[componentIndex];
    }

    updateProductStreamFromComponentMolesAsPhase(gasOutStream, topComponentMoles, "gas", id);
    updateProductStreamFromComponentMolesAsPhase(liquidOutStream, bottomComponentMoles, "liquid",
        id);
    return true;
  }

  /**
   * Estimate a bounded vapor fraction for shortcut fallback products from K-values.
   *
   * @param feedComponentMoles feed component mole amounts
   * @param kSystem initialized system carrying component K-values at fallback conditions
   * @param fallbackVaporFraction vapor fraction to use if the estimate cannot be calculated
   * @return vapor fraction clamped away from zero and one
   */
  private double estimateShortcutVaporFraction(double[] feedComponentMoles, SystemInterface kSystem,
      double fallbackVaporFraction) {
    double totalMoles = 0.0;
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      totalMoles += Math.max(0.0, feedComponentMoles[componentIndex]);
    }
    if (totalMoles <= 1.0e-20) {
      return clampShortcutVaporFraction(fallbackVaporFraction);
    }

    double liquidLimitResidual =
        evaluateRachfordRiceResidual(feedComponentMoles, kSystem, totalMoles, 0.0);
    double vaporLimitResidual =
        evaluateRachfordRiceResidual(feedComponentMoles, kSystem, totalMoles, 1.0);
    if (!Double.isFinite(liquidLimitResidual) || !Double.isFinite(vaporLimitResidual)) {
      return clampShortcutVaporFraction(fallbackVaporFraction);
    }
    if (liquidLimitResidual <= 0.0) {
      return 0.05;
    }
    if (vaporLimitResidual >= 0.0) {
      return 0.95;
    }

    double lowerVaporFraction = 0.0;
    double upperVaporFraction = 1.0;
    for (int iteration = 0; iteration < 80; iteration++) {
      double trialVaporFraction = 0.5 * (lowerVaporFraction + upperVaporFraction);
      double residual =
          evaluateRachfordRiceResidual(feedComponentMoles, kSystem, totalMoles, trialVaporFraction);
      if (!Double.isFinite(residual)) {
        return clampShortcutVaporFraction(fallbackVaporFraction);
      }
      if (residual > 0.0) {
        lowerVaporFraction = trialVaporFraction;
      } else {
        upperVaporFraction = trialVaporFraction;
      }
    }
    return clampShortcutVaporFraction(0.5 * (lowerVaporFraction + upperVaporFraction));
  }

  /**
   * Evaluate the Rachford-Rice residual for a trial vapor fraction.
   *
   * @param feedComponentMoles feed component mole amounts
   * @param kSystem initialized system carrying component K-values
   * @param totalMoles total positive feed moles
   * @param vaporFraction trial vapor fraction from zero to one
   * @return Rachford-Rice residual
   */
  private double evaluateRachfordRiceResidual(double[] feedComponentMoles, SystemInterface kSystem,
      double totalMoles, double vaporFraction) {
    double residual = 0.0;
    for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
      double z = Math.max(0.0, feedComponentMoles[componentIndex]) / totalMoles;
      double kValue = Math.max(1.0e-8, kSystem.getComponent(componentIndex).getK());
      double denominator = 1.0 + vaporFraction * (kValue - 1.0);
      if (!Double.isFinite(denominator) || denominator <= 1.0e-12) {
        return Double.NaN;
      }
      residual += z * (kValue - 1.0) / denominator;
    }
    return residual;
  }

  /**
   * Clamp shortcut vapor fractions to keep both fallback product streams populated.
   *
   * @param vaporFraction vapor fraction to clamp
   * @return finite vapor fraction in the range 0.05 to 0.95
   */
  private double clampShortcutVaporFraction(double vaporFraction) {
    if (!Double.isFinite(vaporFraction)) {
      return 0.5;
    }
    return Math.max(0.05, Math.min(0.95, vaporFraction));
  }

  /**
   * Calculate the current public vapor product mole fraction.
   *
   * @return vapor fraction bounded away from zero and one
   */
  private double getCurrentProductVaporFraction() {
    double topMoles = Math.max(0.0, gasOutStream.getThermoSystem().getTotalNumberOfMoles());
    double bottomMoles = Math.max(0.0, liquidOutStream.getThermoSystem().getTotalNumberOfMoles());
    double totalMoles = topMoles + bottomMoles;
    if (totalMoles <= 1.0e-20) {
      return 0.5;
    }
    return Math.max(0.05, Math.min(0.95, topMoles / totalMoles));
  }

  /**
   * Update a product stream with specified component moles and a fixed phase type.
   *
   * @param productStream stream to update
   * @param componentMoles component mole amounts on the stream-flow basis
   * @param phaseTypeName phase type name to assign to the product
   * @param id calculation identifier to assign to the product
   */
  private void updateProductStreamFromComponentMolesAsPhase(StreamInterface productStream,
      double[] componentMoles, String phaseTypeName, UUID id) {
    SystemInterface productSystem = productStream.getThermoSystem().clone();
    productSystem.setMolarFlowRates(componentMoles);
    productSystem.setTemperature(getFallbackProductTemperature());
    productSystem.setPressure(getFallbackProductPressure(), "bara");
    productSystem.init(0);
    setSingleProductPhaseType(productSystem, phaseTypeName);
    productSystem.init(1);
    productSystem.initProperties();
    setSingleProductPhaseType(productSystem, phaseTypeName);
    productStream.setThermoSystem(productSystem);
    productStream.setCalculationIdentifier(id);
  }

  /**
   * Set both the system-level and phase-level type of a single-phase product system.
   *
   * @param productSystem product system to update
   * @param phaseTypeName phase type description or enum name to assign
   */
  private void setSingleProductPhaseType(SystemInterface productSystem, String phaseTypeName) {
    productSystem.setNumberOfPhases(1);
    productSystem.setPhaseType(0, getPhaseTypeEnumName(phaseTypeName));
    productSystem.getPhase(0).setPhaseTypeName(phaseTypeName);
  }

  /**
   * Convert a phase description to the enum name expected by {@code setPhaseType}.
   *
   * @param phaseTypeName phase type description or enum name
   * @return enum name accepted by {@code SystemInterface#setPhaseType(int, String)}
   */
  private String getPhaseTypeEnumName(String phaseTypeName) {
    if ("gas".equals(phaseTypeName)) {
      return "GAS";
    }
    if ("liquid".equals(phaseTypeName)) {
      return "LIQUID";
    }
    if ("oil".equals(phaseTypeName)) {
      return "OIL";
    }
    if ("aqueous".equals(phaseTypeName)) {
      return "AQUEOUS";
    }
    return phaseTypeName;
  }

  /**
   * Get the temperature used for overall-feed flash fallback products.
   *
   * @return fallback product temperature in Kelvin
   */
  private double getFallbackProductTemperature() {
    if (hasReboiler && getReboiler().isSetOutTemperature()) {
      return getReboiler().getOutTemperature();
    }
    double liquidProductTemperature = liquidOutStream.getTemperature("K");
    if (Double.isFinite(liquidProductTemperature) && liquidProductTemperature > 0.0) {
      return liquidProductTemperature;
    }
    return estimateFeedTemperature();
  }

  /**
   * Get the pressure used for overall-feed flash fallback products.
   *
   * @return fallback product pressure in bara
   */
  private double getFallbackProductPressure() {
    if (bottomTrayPressure > 0.0) {
      return bottomTrayPressure;
    }
    double liquidProductPressure = liquidOutStream.getPressure("bara");
    if (Double.isFinite(liquidProductPressure) && liquidProductPressure > 0.0) {
      return liquidProductPressure;
    }
    return Math.max(1.0, topTrayPressure);
  }

  /**
   * Find a phase index by type name in a thermodynamic system.
   *
   * @param system thermodynamic system to inspect
   * @param phaseTypeName phase type name to locate
   * @param fallbackPhaseIndex fallback index if the type is not found
   * @return phase index, or {@code -1} if no phase is available
   */
  private int findPhaseIndex(SystemInterface system, String phaseTypeName, int fallbackPhaseIndex) {
    int numberOfPhases = system.getNumberOfPhases();
    if (numberOfPhases <= 0) {
      return -1;
    }
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (phaseTypeName.equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return Math.max(0, Math.min(fallbackPhaseIndex, numberOfPhases - 1));
  }

  /**
   * Find a liquid-like phase index in a thermodynamic system.
   *
   * @param system thermodynamic system to inspect
   * @return liquid-like phase index, or {@code -1} if no phase is available
   */
  private int findLiquidPhaseIndex(SystemInterface system) {
    int numberOfPhases = system.getNumberOfPhases();
    if (numberOfPhases <= 0) {
      return -1;
    }
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      String phaseTypeName = system.getPhase(phaseIndex).getPhaseTypeName();
      if ("liquid".equals(phaseTypeName) || "oil".equals(phaseTypeName)) {
        return phaseIndex;
      }
    }
    for (int phaseIndex = 0; phaseIndex < numberOfPhases; phaseIndex++) {
      if (!"gas".equals(system.getPhase(phaseIndex).getPhaseTypeName())) {
        return phaseIndex;
      }
    }
    return -1;
  }

  /**
   * Create a phase system normalized so all selected phase products sum to total inventory.
   *
   * @param sourceSystem flashed source system
   * @param phaseIndex phase index to extract
   * @return normalized single-phase system
   */
  private SystemInterface createNormalizedPhaseSystem(SystemInterface sourceSystem,
      int phaseIndex) {
    SystemInterface phaseSystem = sourceSystem.phaseToSystem(phaseIndex);
    double targetMoles = getNormalizedPhaseMoles(sourceSystem, phaseIndex);
    scaleSystemMoles(phaseSystem, targetMoles);
    phaseSystem.initProperties();
    return phaseSystem;
  }

  /**
   * Calculate normalized phase moles from a potentially over-specified phase split.
   *
   * @param sourceSystem source thermodynamic system
   * @param phaseIndex phase index to normalize
   * @return phase moles normalized to the source total
   */
  private double getNormalizedPhaseMoles(SystemInterface sourceSystem, int phaseIndex) {
    double totalMoles = Math.max(0.0, sourceSystem.getTotalNumberOfMoles());
    double phaseMoles = Math.max(0.0, sourceSystem.getPhase(phaseIndex).getNumberOfMolesInPhase());
    double phaseMoleSum = 0.0;
    for (int i = 0; i < sourceSystem.getNumberOfPhases(); i++) {
      double moles = sourceSystem.getPhase(i).getNumberOfMolesInPhase();
      if (Double.isFinite(moles) && moles > 0.0) {
        phaseMoleSum += moles;
      }
    }
    if (totalMoles > 0.0 && phaseMoleSum > 0.0) {
      return totalMoles * phaseMoles / phaseMoleSum;
    }
    return phaseMoles;
  }

  /**
   * Scale all component moles in a system to a target total.
   *
   * @param system system to scale
   * @param targetMoles target total moles
   */
  private void scaleSystemMoles(SystemInterface system, double targetMoles) {
    double currentMoles = system.getTotalNumberOfMoles();
    if (!Double.isFinite(currentMoles) || currentMoles <= 0.0 || !Double.isFinite(targetMoles)) {
      system.setTotalNumberOfMoles(0.0);
      return;
    }
    double scaleFactor = Math.max(0.0, targetMoles) / currentMoles;
    for (int phaseIndex = 0; phaseIndex < system.getMaxNumberOfPhases(); phaseIndex++) {
      for (int componentIndex = 0; componentIndex < system.getPhase(phaseIndex)
          .getNumberOfComponents(); componentIndex++) {
        double moles =
            system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase()
                * scaleFactor;
        system.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfMolesInPhase(moles);
        system.getPhase(phaseIndex).getComponent(componentIndex).setNumberOfmoles(moles);
      }
    }
    system.setTotalNumberOfMoles(Math.max(0.0, targetMoles));
    system.init(0);
    system.init(1);
  }

  /**
   * Calculate the maximum raw internal tray traffic relative to total external feed flow.
   *
   * @return maximum internal gas or liquid tray outlet flow divided by feed flow
   */
  private double getInternalTrafficRatio() {
    double feedFlow = getTotalExternalFeedFlowKgPerHour();
    if (feedFlow <= 1.0e-20) {
      return 0.0;
    }
    return getMaximumTrayOutletFlowKgPerHour() / feedFlow;
  }

  /**
   * Calculate the maximum raw gas or liquid outlet flow from all trays.
   *
   * @return maximum tray outlet flow in kg/hr
   */
  private double getMaximumTrayOutletFlowKgPerHour() {
    double maximumInternalFlow = 0.0;
    for (int trayIndex = 0; trayIndex < numberOfTrays; trayIndex++) {
      maximumInternalFlow = Math.max(maximumInternalFlow,
          getFiniteAbsoluteFlow(trays.get(trayIndex).getGasOutStream()));
      maximumInternalFlow = Math.max(maximumInternalFlow,
          getFiniteAbsoluteFlow(trays.get(trayIndex).getLiquidOutStream()));
    }
    return maximumInternalFlow;
  }

  /**
   * Calculate total external feed flow in kg/hr.
   *
   * @return total feed flow in kg/hr
   */
  private double getTotalExternalFeedFlowKgPerHour() {
    double totalFeedFlow = 0.0;
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      double flow = feed.getFlowRate("kg/hr");
      if (Double.isFinite(flow)) {
        totalFeedFlow += Math.abs(flow);
      }
    }
    return totalFeedFlow;
  }

  /**
   * Return a stream flow magnitude, or positive infinity for non-finite flow values.
   *
   * @param stream stream to inspect
   * @return finite absolute flow, or positive infinity if the stream is non-finite
   */
  private double getFiniteAbsoluteFlow(StreamInterface stream) {
    double flow = stream.getFlowRate("kg/hr");
    return Double.isFinite(flow) ? Math.abs(flow) : Double.POSITIVE_INFINITY;
  }

  /**
   * Calculates total component mole amounts entering the column through all external feeds.
   *
   * @return component mole amounts on the stream-flow basis used by NeqSim streams, or an empty
   *         array if external feeds do not share a common component basis
   */
  private double[] getFeedComponentMoles() {
    int componentCount = getNumberOfComponentsFromFeeds();
    if (componentCount == 0) {
      return new double[0];
    }
    double[] feedComponentMoles = new double[componentCount];
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      double[] componentMoles = getComponentMoles(feed.getThermoSystem());
      if (componentMoles.length != componentCount) {
        return new double[0];
      }
      for (int componentIndex = 0; componentIndex < feedComponentMoles.length; componentIndex++) {
        feedComponentMoles[componentIndex] += componentMoles[componentIndex];
      }
    }
    return feedComponentMoles;
  }

  /**
   * Calculates component mole amounts withdrawn in all side-draw streams.
   *
   * @param componentCount number of components expected in the feed/product basis
   * @return component mole amounts withdrawn through side draws
   */
  private double[] getSideDrawComponentMoles(int componentCount) {
    double[] sideDrawComponentMoles = new double[componentCount];
    for (StreamInterface sideDrawStream : getSideDrawStreams()) {
      double[] componentMoles = getComponentMoles(sideDrawStream.getThermoSystem());
      if (componentMoles.length != componentCount) {
        continue;
      }
      for (int componentIndex =
          0; componentIndex < sideDrawComponentMoles.length; componentIndex++) {
        sideDrawComponentMoles[componentIndex] += componentMoles[componentIndex];
      }
    }
    return sideDrawComponentMoles;
  }

  /**
   * Gets the number of components from the first available feed stream.
   *
   * @return number of components, or zero when no feeds are connected
   */
  private int getNumberOfComponentsFromFeeds() {
    for (StreamInterface feed : getAllExternalFeedStreams()) {
      return feed.getThermoSystem().getNumberOfComponents();
    }
    return 0;
  }

  /**
   * Calculates component mole amounts from all phases in a thermodynamic system.
   *
   * @param system thermodynamic system to inspect
   * @return component mole amounts on the system-flow basis
   */
  private double[] getComponentMoles(SystemInterface system) {
    double[] componentMoles = new double[system.getNumberOfComponents()];
    for (int componentIndex = 0; componentIndex < componentMoles.length; componentIndex++) {
      double componentTotal = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        componentTotal +=
            system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
      }
      if (componentTotal <= 0.0 && system.getNumberOfPhases() > 0) {
        componentTotal = system.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      }
      componentMoles[componentIndex] = componentTotal;
    }
    return componentMoles;
  }

  /**
   * Sum component mole amounts contributed by gas-like or liquid-like phases only.
   *
   * <p>
   * Tray terminal thermo systems can hold both an ascending gas phase and a descending liquid
   * phase. When reconciling a single-phase public product against the external feed mass balance,
   * the moles attributed to the product must come only from the relevant phase. If no matching
   * phase is present (e.g. a single-phase oil reboiler or pure-vapor distillate), this method falls
   * back to {@link #getComponentMoles(SystemInterface)} so the reconciliation step still has a
   * non-zero composition to scale.
   * </p>
   *
   * @param system thermodynamic system to inspect
   * @param gasPhase {@code true} to sum gas-like phases, {@code false} to sum oil/liquid/aqueous
   *        phases
   * @return component mole amounts contributed by the matching phases
   */
  private double[] getPhaseFilteredComponentMoles(SystemInterface system, boolean gasPhase) {
    double[] componentMoles = new double[system.getNumberOfComponents()];
    boolean anyMatch = false;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      String phaseName = system.getPhase(phaseIndex).getPhaseTypeName();
      boolean matches;
      if (gasPhase) {
        matches = "gas".equalsIgnoreCase(phaseName);
      } else {
        matches = "oil".equalsIgnoreCase(phaseName) || "liquid".equalsIgnoreCase(phaseName)
            || "aqueous".equalsIgnoreCase(phaseName);
      }
      if (!matches) {
        continue;
      }
      anyMatch = true;
      for (int componentIndex = 0; componentIndex < componentMoles.length; componentIndex++) {
        componentMoles[componentIndex] +=
            system.getPhase(phaseIndex).getComponent(componentIndex).getNumberOfMolesInPhase();
      }
    }
    if (!anyMatch) {
      return getComponentMoles(system);
    }
    return componentMoles;
  }

  /** Reset cached solve metrics when no calculation is performed. */
  private void resetLastSolveMetrics() {
    err = 1.0e10;
    lastIterationCount = 0;
    lastTemperatureResidual = 0.0;
    lastMassResidual = 0.0;
    lastEnergyResidual = 0.0;
    lastSolveTimeSeconds = 0.0;
    lastInternalTrafficRatio = 0.0;
    lastInternalTrafficGuardReached = false;
    lastUsedFeedFlashFallback = false;
    lastSolveStatus = SolveStatus.NOT_RUN;
    lastSolveStatusReason = "No solve has been run";
    lastAutoSolverSummary = "";
    lastAutoFeasibilityReport = "";
    lastInitializationReport = "";
    lastAutoSolverHistory = new ArrayList<String>();
    lastSpecificationHomotopyStepCount = 0;
    resetInsideOutTelemetry();
    resetNaphtaliTelemetry();
    terminalGasProductDrawStream = null;
    terminalLiquidProductDrawStream = null;
    resetMatrixInsideOutDiagnostics();
  }

  /** Reset rigorous inside-out telemetry fields. */
  private void resetInsideOutTelemetry() {
    lastInsideOutOuterFlashSweeps = 0;
    lastInsideOutInnerLoopIterations = 0;
    lastInsideOutKValueResidual = Double.NaN;
    lastInsideOutSurrogateResidual = Double.NaN;
    lastInsideOutSurrogateResetCount = 0;
  }

  /** Reset Naphtali-Sandholm telemetry fields. */
  private void resetNaphtaliTelemetry() {
    lastNaphtaliAnalyticJacobianColumns = 0;
    lastNaphtaliFiniteDifferenceJacobianColumns = 0;
    lastNaphtaliThermoEvaluationCount = 0;
    lastNaphtaliThermoCacheHitCount = 0;
    lastNaphtaliJacobianBuildTimeSeconds = 0.0;
    lastNaphtaliBlockLinearSolveCount = 0;
    lastNaphtaliDenseLinearSolveCount = 0;
    lastNaphtaliLinearSolveTimeSeconds = 0.0;
  }

  /** Reset matrix inside-out warm-start diagnostics. */
  private void resetMatrixInsideOutDiagnostics() {
    lastMatrixInsideOutWarmStartUsed = false;
    lastMatrixInsideOutWarmStartBypassed = false;
    lastMatrixInsideOutIterationCount = 0;
    lastMatrixInsideOutTemperatureResidual = Double.NaN;
    lastMatrixInsideOutSolveTimeSeconds = 0.0;
  }

  /**
   * Prints a simple energy balance for each tray to the console. The method calculates the total
   * enthalpy of all inlet streams and compares it with the outlet enthalpy in order to highlight
   * any discrepancies in the column setup.
   */
  public void energyBalanceCheck() {
    double[] energyInput = new double[numberOfTrays];
    double[] energyOutput = new double[numberOfTrays];
    double[] energyBalance = new double[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        energyInput[i] += trays.get(i).getStream(j).getFluid().getEnthalpy();
      }
      energyOutput[i] += trays.get(i).getGasOutStream().getFluid().getEnthalpy();
      energyOutput[i] += trays.get(i).getLiquidOutStream().getFluid().getEnthalpy();
      energyBalance[i] = energyInput[i] - energyOutput[i];

      System.out.println("Tray " + i + ", #in=" + numberOfInputStreams + ", eIn=" + energyInput[i]
          + ", eOut=" + energyOutput[i] + ", balance=" + energyBalance[i]);
    }
  }

  /**
   * The main method demonstrates the creation and operation of a distillation column using the
   * NeqSim library. It performs the following steps:
   * <ol>
   * <li>Creates a test thermodynamic system with methane, ethane, and propane components.</li>
   * <li>Performs a TP flash calculation on the test system.</li>
   * <li>Creates two separate feed streams from the test system.</li>
   * <li>Constructs a distillation column with 5 trays, a reboiler, and a condenser.</li>
   * <li>Adds the two feed streams to the distillation column at tray 3.</li>
   * <li>Builds and runs the process system.</li>
   * <li>Displays the results of the distillation column, including the gas and liquid output
   * streams.</li>
   * </ol>
   *
   * @param args command line arguments (not used)
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // Create a test system
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 15.0);
    testSystem.addComponent("methane", 10.00);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("propane", 10.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    neqsim.thermodynamicoperations.ThermodynamicOperations ops =
        new neqsim.thermodynamicoperations.ThermodynamicOperations(testSystem);
    ops.TPflash();
    testSystem.display();

    // Make two separate feed streams
    Stream feed1 = new Stream("Feed1", testSystem.clone());
    Stream feed2 = new Stream("Feed2", testSystem.clone());

    // Create the column with 5 "simple" trays, reboiler, condenser
    DistillationColumn column = new DistillationColumn("distColumn", 5, true, true);

    // Add feed1 to tray 3, feed2 also to tray 3
    column.addFeedStream(feed1, 3);
    column.addFeedStream(feed2, 3);

    // Build process
    neqsim.process.processmodel.ProcessSystem operations =
        new neqsim.process.processmodel.ProcessSystem();
    operations.add(feed1);
    operations.add(feed2);
    operations.add(column);
    operations.run();

    // Display
    column.displayResult();
    System.out.println("Gas out:");
    column.getGasOutStream().getThermoSystem().display();
    System.out.println("Liquid out:");
    column.getLiquidOutStream().getThermoSystem().display();
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());

    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Equipment has no name", "Set equipment name in constructor");
    }

    validateColumnGeometry(result);
    validateColumnFeeds(result);
    validateColumnPressureProfile(result);
    validateColumnNumerics(result);
    validateColumnSpecifications(result);
    validateColumnTearVariables(result);

    return result;
  }

  /**
   * Validate only the configured top and bottom column specifications.
   *
   * <p>
   * This focused validator is useful before a solve when users are scripting product-purity,
   * component-recovery, flow-rate, reflux, or duty specifications and want actionable diagnostics
   * without validating the full equipment setup.
   * </p>
   *
   * @return validation result containing specification errors and warnings
   */
  public ValidationResult validateSpecifications() {
    ValidationResult result = new ValidationResult(getName() + ":specifications");
    validateColumnSpecifications(result);
    return result;
  }

  /**
   * Add warnings for valid but numerically severe active-bound specifications.
   *
   * @param result validation result receiving active-bound warnings
   */
  private void validateCommercialActiveBounds(ValidationResult result) {
    validateSpecificationActiveBound(result, topSpecification);
    validateSpecificationActiveBound(result, bottomSpecification);
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      if (specification.getTargetFlowRate() > 1.0e8) {
        result.addWarning("sidedraw.activeBound",
            "Side-draw target flow is far above typical column traffic",
            "Use homotopy steps or check units before solving the column");
      }
    }
    for (ColumnPumparound pumparound : pumparounds) {
      if (pumparound.getDrawFraction() > 0.90) {
        result.addWarning("pumparound.activeBound",
            "Pumparound draw fraction is close to the full tray liquid traffic",
            "Reduce draw fraction or solve with staged continuation before rigorous refinement");
      }
    }
  }

  /**
   * Add a warning for purity or recovery targets close to 0 or 1.
   *
   * @param result validation result receiving active-bound warnings
   * @param specification column specification to screen
   */
  private void validateSpecificationActiveBound(ValidationResult result,
      ColumnSpecification specification) {
    if (specification == null) {
      return;
    }
    ColumnSpecification.SpecificationType type = specification.getType();
    if ((type == ColumnSpecification.SpecificationType.PRODUCT_PURITY
        || type == ColumnSpecification.SpecificationType.COMPONENT_RECOVERY)
        && isNearFractionBound(specification.getTargetValue())) {
      result.addWarning("specification.activeBound",
          "Column specification target is close to a hard fraction bound",
          "Relax the target for an initial homotopy solve and tighten it after convergence");
    }
  }

  /**
   * Check whether a fraction value is close enough to 0 or 1 to be numerically active.
   *
   * @param value fraction target value
   * @return {@code true} when the value lies close to either hard bound
   */
  private boolean isNearFractionBound(double value) {
    return value <= 1.0e-6 || value >= 1.0 - 1.0e-6;
  }

  /**
   * Validate tray count and geometric inputs.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnGeometry(ValidationResult result) {
    if (numberOfTrays <= 0) {
      result.addError("column.trays", "Column has no trays",
          "Create the column with at least one equilibrium tray");
    }
    if (!Double.isFinite(internalDiameter) || internalDiameter <= 0.0) {
      result.addError("column.diameter", "Internal column diameter is not positive and finite",
          "Set a positive diameter with column.setInternalDiameter(valueInMeters)");
    }
    if (!hasCondenser && !hasReboiler) {
      result.addWarning("configuration",
          "Column has neither condenser nor reboiler - acting as a stripper/absorber",
          "Set hasCondenser=true and/or hasReboiler=true in constructor if separation is needed");
    }
  }

  /**
   * Validate feed-stream connectivity and tray assignments.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnFeeds(ValidationResult result) {

    if (feedStreams.isEmpty() && unassignedFeedStreams.isEmpty()) {
      result.addError("stream", "No feed stream connected to distillation column",
          "Add a feed stream: column.addFeedStream(stream, feedTrayNumber)");
    }

    for (Entry<Integer, List<StreamInterface>> feedEntry : feedStreams.entrySet()) {
      Integer trayNumber = feedEntry.getKey();
      if (trayNumber == null || trayNumber.intValue() < 0
          || trayNumber.intValue() >= numberOfTrays) {
        result.addError("stream.feedTray", "Feed tray index is outside the column tray range",
            "Use a feed tray from 0 to numberOfTrays - 1");
      }
      List<StreamInterface> streams = feedEntry.getValue();
      if (streams == null || streams.isEmpty()) {
        result.addWarning("stream.feedTray", "Feed tray has no streams assigned",
            "Remove the empty tray assignment or add a feed stream");
        continue;
      }
      for (StreamInterface feedStream : streams) {
        validateFeedStream(result, feedStream);
      }
    }

    for (StreamInterface feedStream : unassignedFeedStreams) {
      validateFeedStream(result, feedStream);
    }
  }

  /**
   * Validate a single feed stream used by the column.
   *
   * @param result validation result receiving issues
   * @param feedStream feed stream to validate
   */
  private void validateFeedStream(ValidationResult result, StreamInterface feedStream) {
    if (feedStream == null) {
      result.addError("stream.feed", "A null feed stream is connected",
          "Remove the null feed or replace it with a Stream instance");
      return;
    }
    SystemInterface fluid = feedStream.getFluid();
    if (fluid == null) {
      result.addError("stream.feed", "Feed stream has no thermodynamic system",
          "Construct the stream with a fluid before adding it to the column");
      return;
    }
    if (fluid.getTotalNumberOfMoles() <= 0.0) {
      result.addWarning("stream.feed", "Feed stream has zero or negative total moles",
          "Set a positive flow rate on the feed stream before running the column");
    }
  }

  /**
   * Validate top and bottom column pressure inputs.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnPressureProfile(ValidationResult result) {
    if (topTrayPressure == 0.0 || (Double.isFinite(topTrayPressure) && topTrayPressure < 0.0)) {
      result.addWarning("column.pressure", "Top pressure is not explicitly set",
          "Call column.setTopPressure(value) or allow the solver to initialize from feed pressure");
    }
    if (bottomTrayPressure == 0.0
        || (Double.isFinite(bottomTrayPressure) && bottomTrayPressure < 0.0)) {
      result.addWarning("column.pressure", "Bottom pressure is not explicitly set",
          "Call column.setBottomPressure(value) or allow the solver to initialize from feed pressure");
    }
    if (topTrayPressure > 0.0 && bottomTrayPressure > 0.0 && topTrayPressure > bottomTrayPressure) {
      result.addWarning("column.pressure", "Top pressure is higher than bottom pressure",
          "Check the pressure profile; distillation columns normally have bottom pressure >= top pressure");
    }
  }

  /**
   * Validate numerical solver configuration.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnNumerics(ValidationResult result) {
    if (solverType == null) {
      result.addError("solver", "Solver type is null",
          "Use column.setSolverType(DistillationColumn.SolverType.AUTO) or another solver");
    }
    if (maxNumberOfIterations <= 0) {
      result.addError("solver.iterations", "Maximum iteration count is not positive",
          "Set a positive value with column.setMaxNumberOfIterations(iterations)");
    }
    if (!isPositiveFiniteValue(temperatureTolerance)) {
      result.addError("solver.temperatureTolerance", "Temperature tolerance is not positive finite",
          "Set a positive finite tolerance with column.setTemperatureTolerance(tol)");
    }
    if (!isPositiveFiniteValue(massBalanceTolerance)) {
      result.addError("solver.massBalanceTolerance",
          "Mass balance tolerance is not positive finite",
          "Set a positive finite tolerance with column.setMassBalanceTolerance(tol)");
    }
    if (!isPositiveFiniteValue(enthalpyBalanceTolerance)) {
      result.addError("solver.enthalpyTolerance", "Enthalpy tolerance is not positive finite",
          "Set a positive finite tolerance with column.setEnthalpyBalanceTolerance(tol)");
    }
    if (!isPositiveFiniteValue(meshResidualTolerance)) {
      result.addError("solver.meshTolerance", "MESH residual tolerance is not positive finite",
          "Set a positive finite tolerance with column.setMeshResidualTolerance(tol)");
    }
    if (murphreeEfficiency < 0.0 || murphreeEfficiency > 1.0
        || !Double.isFinite(murphreeEfficiency)) {
      result.addError("efficiency", "Murphree efficiency is outside 0..1",
          "Set tray efficiency with column.setMurphreeEfficiency(valueBetweenZeroAndOne)");
    }
  }

  /**
   * Validate top and bottom specification consistency.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnSpecifications(ValidationResult result) {
    validateColumnSpecification(result, topSpecification, ColumnSpecification.ProductLocation.TOP);
    validateColumnSpecification(result, bottomSpecification,
        ColumnSpecification.ProductLocation.BOTTOM);
  }

  /**
   * Validate side-draw, pumparound, hydraulic, and dynamic tear-variable configuration.
   *
   * @param result validation result receiving issues
   */
  private void validateColumnTearVariables(ValidationResult result) {
    validateSideDrawSpecifications(result);
    validatePumparounds(result);
    validateHydraulicPressureDropCoupling(result);
    validateDynamicColumnModel(result);
  }

  /**
   * Validate configured side-draw flow specifications.
   *
   * @param result validation result receiving issues
   */
  private void validateSideDrawSpecifications(ValidationResult result) {
    for (ColumnSideDrawSpecification specification : sideDrawSpecifications) {
      if (specification.getTrayNumber() < 0 || specification.getTrayNumber() >= numberOfTrays) {
        result.addError("sidedraw.tray", "Side-draw specification tray is outside the column",
            "Use a tray number between 0 and column.getNumberOfTrays() - 1");
      }
      if (!Double.isFinite(specification.getTargetFlowRate())
          || specification.getTargetFlowRate() < 0.0) {
        result.addError("sidedraw.flow", "Side-draw target flow is not finite and non-negative",
            "Create the side-draw specification with a finite flow rate >= 0");
      }
      if (!isPositiveFiniteValue(specification.getTolerance())) {
        result.addError("sidedraw.tolerance", "Side-draw tolerance is not positive finite",
            "Set a positive finite tolerance on the side-draw specification");
      }
      if (specification.getMaxIterations() <= 0) {
        result.addError("sidedraw.iterations", "Side-draw iteration limit is not positive",
            "Set maxIterations to a positive value on the side-draw specification");
      }
    }
  }

  /**
   * Validate configured pumparound circuits.
   *
   * @param result validation result receiving issues
   */
  private void validatePumparounds(ValidationResult result) {
    if (!isPositiveFiniteValue(pumparoundTolerance)) {
      result.addError("pumparound.tolerance", "Pumparound tolerance is not positive finite",
          "Set a positive finite tolerance with column.setPumparoundTolerance(tolerance)");
    }
    if (maxPumparoundIterations <= 0) {
      result.addError("pumparound.iterations", "Pumparound iteration limit is not positive",
          "Set max pumparound iterations to a positive value");
    }
    for (ColumnPumparound pumparound : pumparounds) {
      if (pumparound.getDrawTrayNumber() < 0 || pumparound.getDrawTrayNumber() >= numberOfTrays
          || pumparound.getReturnTrayNumber() < 0
          || pumparound.getReturnTrayNumber() >= numberOfTrays) {
        result.addError("pumparound.tray", "Pumparound tray is outside the column",
            "Use draw and return tray numbers between 0 and column.getNumberOfTrays() - 1");
      }
    }
  }

  /**
   * Validate hydraulic pressure-drop coupling configuration.
   *
   * @param result validation result receiving issues
   */
  private void validateHydraulicPressureDropCoupling(ValidationResult result) {
    if (!isPositiveFiniteValue(columnTearTolerance)) {
      result.addError("columntear.tolerance", "Column tear tolerance is not positive finite",
          "Set a positive finite tolerance with column.setColumnTearTolerance(tolerance)");
    }
    if (maxColumnTearIterations <= 0) {
      result.addError("columntear.iterations", "Column tear iteration limit is not positive",
          "Set max column tear iterations to a positive value");
    }
    if (hydraulicPressureDropCouplingEnabled) {
      if (hydraulicPressureDropInternalsType == null
          || hydraulicPressureDropInternalsType.trim().isEmpty()) {
        result.addError("hydraulics.internals", "Hydraulic coupling has no internals type",
            "Set the internals type with column.setHydraulicPressureDropInternalsType(type)");
      }
      if (!isPositiveFinite(topTrayPressure) && !isPositiveFinite(bottomTrayPressure)) {
        result.addWarning("hydraulics.pressure",
            "Hydraulic pressure-drop coupling has no pressure endpoint basis",
            "Set either top or bottom pressure so hydraulic pressure drop can anchor the profile");
      }
    }
  }

  /**
   * Validate dynamic model configuration and label experimental behavior.
   *
   * @param result validation result receiving issues
   */
  private void validateDynamicColumnModel(ValidationResult result) {
    if (dynamicColumnEnabled && isDynamicColumnModelExperimental()) {
      result.addWarning("dynamic.model",
          "Dynamic distillation model is experimental explicit-Euler holdup screening",
          "Use it for qualitative transients only; rigorous industrial dynamics require a DAE formulation");
    }
  }

  /**
   * Validate one column specification against column hardware and feed components.
   *
   * @param result validation result receiving issues
   * @param specification specification to validate
   * @param expectedLocation expected product location
   */
  private void validateColumnSpecification(ValidationResult result,
      ColumnSpecification specification, ColumnSpecification.ProductLocation expectedLocation) {
    if (specification == null) {
      return;
    }
    if (specification.getLocation() != expectedLocation) {
      result.addError("specification.location", "Specification is assigned to the wrong column end",
          "Use setTopSpecification() for TOP specs and setBottomSpecification() for BOTTOM specs");
    }
    if (!Double.isFinite(specification.getTargetValue())) {
      result.addError("specification.target", "Specification target is not finite",
          "Create the specification with a finite target value");
    }
    if (!isPositiveFiniteValue(specification.getTolerance())) {
      result.addError("specification.tolerance", "Specification tolerance is not positive finite",
          "Set a positive finite tolerance on the ColumnSpecification");
    }
    if (specification.getMaxIterations() <= 0) {
      result.addError("specification.iterations", "Specification iteration limit is not positive",
          "Set maxIterations to a positive value on the ColumnSpecification");
    }
    validateSpecificationHardware(result, specification);
    validateSpecificationComponent(result, specification);
  }

  /**
   * Validate whether a specification can be manipulated by the configured column hardware.
   *
   * @param result validation result receiving issues
   * @param specification specification to validate
   */
  private void validateSpecificationHardware(ValidationResult result,
      ColumnSpecification specification) {
    boolean topSpecificationLocal =
        specification.getLocation() == ColumnSpecification.ProductLocation.TOP;
    boolean hasControllerEnd = topSpecificationLocal ? hasCondenser : hasReboiler;
    if (!hasControllerEnd && needsAdjustment(specification)) {
      result.addWarning("specification.hardware",
          "Adjustable specification has no condenser/reboiler handle on that column end",
          "Add the matching condenser/reboiler or replace the spec with a directly set temperature/duty");
    }
    if (!hasControllerEnd
        && (specification.getType() == ColumnSpecification.SpecificationType.REFLUX_RATIO
            || specification.getType() == ColumnSpecification.SpecificationType.DUTY)) {
      result.addWarning("specification.hardware",
          "Direct reflux or duty specification has no matching condenser/reboiler",
          "Enable the matching column end or remove the direct specification");
    }
  }

  /**
   * Validate component references for component-based specifications.
   *
   * @param result validation result receiving issues
   * @param specification specification to validate
   */
  private void validateSpecificationComponent(ValidationResult result,
      ColumnSpecification specification) {
    if (specification.getType() != ColumnSpecification.SpecificationType.PRODUCT_PURITY
        && specification.getType() != ColumnSpecification.SpecificationType.COMPONENT_RECOVERY) {
      return;
    }
    String componentName = specification.getComponentName();
    if (componentName == null || componentName.trim().isEmpty()) {
      result.addError("specification.component", "Component-based specification has no component",
          "Pass the component name to the ColumnSpecification constructor");
      return;
    }
    if (!isComponentPresentInAnyFeed(componentName)) {
      result.addError("specification.component",
          "Specification component is not present in any feed stream: " + componentName,
          "Use a component name from the feed fluid or add the component to the feed");
    }
  }

  /**
   * Check whether a named component is present in at least one external feed stream.
   *
   * @param componentName component name to search for
   * @return {@code true} when any feed fluid contains the component name
   */
  private boolean isComponentPresentInAnyFeed(String componentName) {
    if (componentName == null) {
      return false;
    }
    for (StreamInterface feedStream : getAllExternalFeedStreams()) {
      if (feedStream == null || feedStream.getFluid() == null) {
        continue;
      }
      if (fluidContainsComponent(feedStream.getFluid(), componentName)) {
        return true;
      }
    }
    for (StreamInterface feedStream : unassignedFeedStreams) {
      if (feedStream == null || feedStream.getFluid() == null) {
        continue;
      }
      if (fluidContainsComponent(feedStream.getFluid(), componentName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether a thermodynamic system contains a named component.
   *
   * @param fluid fluid system to inspect
   * @param componentName component name to search for
   * @return {@code true} when the component exists in the fluid
   */
  private boolean fluidContainsComponent(SystemInterface fluid, String componentName) {
    String[] componentNames = fluid.getComponentNames();
    if (componentNames == null) {
      return false;
    }
    for (String candidateName : componentNames) {
      if (componentName.equals(candidateName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether a value is positive and finite.
   *
   * @param value value to inspect
   * @return {@code true} when value is finite and greater than zero
   */
  private boolean isPositiveFiniteValue(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(new DistillationColumnResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    DistillationColumnResponse res = new DistillationColumnResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(res);
  }

  /**
   * <p>
   * getNumerOfTrays.
   * </p>
   *
   * @return a int
   */
  public int getNumerOfTrays() {
    return numberOfTrays;
  }

  /**
   * Get the number of stages in the column using the correctly spelled API name.
   *
   * <p>
   * This method is an alias for the legacy {@link #getNumerOfTrays()} method and is kept separate
   * to preserve backwards compatibility with existing scripts.
   * </p>
   *
   * @return number of stages in the column including reboiler and condenser stages when present
   */
  public int getNumberOfTrays() {
    return getNumerOfTrays();
  }

  /**
   * Set a per-stage seed temperature used as an initial guess by residual solvers.
   *
   * <p>
   * A seed temperature is not a tray specification. Unlike {@link SimpleTray#setOutTemperature}, it
   * does not pin the stage temperature or replace the energy balance. The current
   * {@link SolverType#NAPHTALI_SANDHOLM} implementation uses finite seeds only when the same stage
   * has no fixed output-temperature specification.
   * </p>
   *
   * @param stageIndex bottom-up stage index, where zero is the reboiler when present and
   *        {@code numberOfTrays - 1} is the top stage
   * @param temperatureK seed temperature in kelvin; pass {@link Double#NaN} to clear a stage seed
   */
  public void setSeedTemperature(int stageIndex, double temperatureK) {
    if (stageIndex < 0 || stageIndex >= numberOfTrays) {
      return;
    }
    ensureSeedTemperatureArray();
    seedTemperatures[stageIndex] = Double.isFinite(temperatureK) ? temperatureK : Double.NaN;
  }

  /**
   * Get the seed temperature configured for one stage.
   *
   * @param stageIndex bottom-up stage index to inspect
   * @return seed temperature in kelvin, or {@link Double#NaN} when no seed is configured or the
   *         stage index is outside the current column range
   */
  public double getSeedTemperature(int stageIndex) {
    if (seedTemperatures == null || stageIndex < 0 || stageIndex >= numberOfTrays) {
      return Double.NaN;
    }
    return seedTemperatures[stageIndex];
  }

  /**
   * Check whether at least one finite stage seed temperature is configured.
   *
   * @return {@code true} when one or more stages have a finite seed temperature
   */
  public boolean hasSeedTemperatures() {
    if (seedTemperatures == null) {
      return false;
    }
    for (double seedTemperature : seedTemperatures) {
      if (Double.isFinite(seedTemperature)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Clear all per-stage seed temperatures.
   */
  public void clearSeedTemperatures() {
    seedTemperatures = null;
  }

  /**
   * Ensure the seed-temperature array exists and matches the current stage count.
   */
  private void ensureSeedTemperatureArray() {
    if (seedTemperatures != null && seedTemperatures.length == numberOfTrays) {
      return;
    }
    double[] resized = new double[numberOfTrays];
    Arrays.fill(resized, Double.NaN);
    if (seedTemperatures != null) {
      System.arraycopy(seedTemperatures, 0, resized, 0,
          Math.min(seedTemperatures.length, resized.length));
    }
    seedTemperatures = resized;
  }

  /**
   * Set the Murphree tray efficiency for all stages without explicit overrides.
   *
   * @param efficiency value between 0.0 (no separation) and 1.0 (ideal equilibrium stage)
   */
  public void setMurphreeEfficiency(double efficiency) {
    this.murphreeEfficiency = clampMurphreeEfficiency(efficiency);
  }

  /**
   * Set the Murphree tray efficiency for a single stage.
   *
   * <p>
   * Stage numbering follows the column internal order: stage 0 is the reboiler when present and the
   * last stage is the condenser when present. Reboiler and condenser stages are still treated as
   * equilibrium stages by the correction algorithm, but the value is stored so external style
   * scripts can round-trip stage efficiency data consistently.
   * </p>
   *
   * @param stage 0-based stage index in the range {@code [0, numberOfTrays)}
   * @param efficiency value between 0.0 (no separation) and 1.0 (ideal equilibrium stage)
   */
  public void setMurphreeEfficiency(int stage, double efficiency) {
    validateStageIndex(stage);
    ensurePerStageMurphreeEfficiencyArray();
    perStageMurphreeEfficiency[stage] = clampMurphreeEfficiency(efficiency);
  }

  /**
   * Set Murphree tray efficiencies for every stage in one call.
   *
   * <p>
   * The array length must equal the total number of stages including reboiler and condenser if they
   * are present. Entries equal to {@link Double#NaN} restore use of the column-wide default for
   * that stage.
   * </p>
   *
   * @param efficiencies per-stage Murphree efficiencies, or {@code null} to clear all overrides
   */
  public void setMurphreeEfficiencies(double[] efficiencies) {
    if (efficiencies == null) {
      clearPerStageMurphreeEfficiency();
      return;
    }
    if (efficiencies.length != numberOfTrays) {
      throw new IllegalArgumentException("efficiencies length " + efficiencies.length
          + " does not match number of stages " + numberOfTrays);
    }
    double[] copy = new double[numberOfTrays];
    for (int stage = 0; stage < numberOfTrays; stage++) {
      copy[stage] = Double.isNaN(efficiencies[stage]) ? Double.NaN
          : clampMurphreeEfficiency(efficiencies[stage]);
    }
    perStageMurphreeEfficiency = copy;
  }

  /**
   * Clear all per-stage Murphree efficiency overrides.
   */
  public void clearPerStageMurphreeEfficiency() {
    perStageMurphreeEfficiency = null;
  }

  /**
   * Retrieve the current column-wide Murphree tray efficiency.
   *
   * @return Murphree efficiency used as the default for stages without overrides
   */
  public double getMurphreeEfficiency() {
    return murphreeEfficiency;
  }

  /**
   * Retrieve the effective Murphree tray efficiency for a single stage.
   *
   * @param stage 0-based stage index in the range {@code [0, numberOfTrays)}
   * @return per-stage override when set, otherwise the column-wide Murphree efficiency
   */
  public double getMurphreeEfficiency(int stage) {
    validateStageIndex(stage);
    return getEffectiveMurphreeEfficiency(stage);
  }

  /**
   * Ensure that the per-stage Murphree efficiency array exists and matches the current stage count.
   */
  private void ensurePerStageMurphreeEfficiencyArray() {
    if (perStageMurphreeEfficiency != null && perStageMurphreeEfficiency.length == numberOfTrays) {
      return;
    }
    double[] resized = new double[numberOfTrays];
    for (int stage = 0; stage < numberOfTrays; stage++) {
      resized[stage] =
          perStageMurphreeEfficiency != null && stage < perStageMurphreeEfficiency.length
              ? perStageMurphreeEfficiency[stage]
              : Double.NaN;
    }
    perStageMurphreeEfficiency = resized;
  }

  /**
   * Get the effective Murphree efficiency for a stage without performing bounds validation.
   *
   * @param stage 0-based stage index
   * @return per-stage override when finite, otherwise the column-wide Murphree efficiency
   */
  private double getEffectiveMurphreeEfficiency(int stage) {
    if (perStageMurphreeEfficiency != null && stage >= 0
        && stage < perStageMurphreeEfficiency.length) {
      double value = perStageMurphreeEfficiency[stage];
      if (!Double.isNaN(value)) {
        return value;
      }
    }
    return murphreeEfficiency;
  }

  /**
   * Clamp a Murphree efficiency into the supported interval.
   *
   * @param efficiency requested efficiency value
   * @return value clamped to the interval {@code [0.0, 1.0]}
   */
  private double clampMurphreeEfficiency(double efficiency) {
    return Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Validate that a stage index is within the current column stage range.
   *
   * @param stage stage index to validate
   * @throws IndexOutOfBoundsException if the stage is outside {@code [0, numberOfTrays)}
   */
  private void validateStageIndex(int stage) {
    if (stage < 0 || stage >= numberOfTrays) {
      throw new IndexOutOfBoundsException(
          "stage index " + stage + " out of range [0, " + numberOfTrays + ")");
    }
  }

  /**
   * Set the number of simplified inner-loop iterations between rigorous flash updates in the IO
   * solver. A value of 0 disables the simplified model (all iterations use rigorous flash).
   *
   * @param steps number of inner-loop steps (0 to disable, typically 2-5)
   */
  public void setInnerLoopSteps(int steps) {
    this.innerLoopSteps = Math.max(0, steps);
  }

  /**
   * Return the current number of simplified inner-loop iterations per outer flash update.
   *
   * @return inner-loop step count
   */
  public int getInnerLoopSteps() {
    return innerLoopSteps;
  }

  /**
   * Return the per-iteration convergence history from the most recent solve.
   *
   * <p>
   * Each entry is a three-element array: [temperatureResidual, massResidual, energyResidual].
   * </p>
   *
   * @return list of residual arrays, one per iteration; empty if no solve has been run
   */
  public List<double[]> getConvergenceHistory() {
    return convergenceHistory != null ? Collections.unmodifiableList(convergenceHistory)
        : Collections.emptyList();
  }

  /**
   * Retrieve the currently selected solver type.
   *
   * @return solver type enum value
   */
  public SolverType getSolverType() {
    return solverType;
  }

  /**
   * Get the solver strategy that completed the latest run.
   *
   * <p>
   * For explicitly selected solvers this is normally the same as {@link #getSolverType()}. When
   * {@link SolverType#AUTO} is configured, this reports the concrete solver selected by the
   * automatic solver factory.
   * </p>
   *
   * @return solver strategy used by the latest solve
   */
  public SolverType getLastSolverTypeUsed() {
    return lastSolverTypeUsed;
  }

  /**
   * Get the strict status of the latest column solve.
   *
   * @return latest solve status
   */
  public SolveStatus getLastSolveStatus() {
    return lastSolveStatus;
  }

  /**
   * Get the explanatory reason for the latest solve status.
   *
   * @return concise status reason, or an empty string if none is available
   */
  public String getLastSolveStatusReason() {
    return lastSolveStatusReason;
  }

  /**
   * Get the latest automatic solver candidate trace.
   *
   * @return candidate trace from {@link SolverType#AUTO}, or an empty string when automatic mode
   *         was not used in the latest solve
   */
  public String getLastAutoSolverSummary() {
    return lastAutoSolverSummary;
  }

  /**
   * Get the latest feasibility report generated before AUTO candidate probing.
   *
   * @return feasibility report text, or an empty string when AUTO has not screened the column
   */
  public String getLastAutoFeasibilityReport() {
    return lastAutoFeasibilityReport;
  }

  /**
   * Get the latest automatic initialization report.
   *
   * @return initialization report text, or an empty string when no seed was attempted
   */
  public String getLastInitializationReport() {
    return lastInitializationReport;
  }

  /**
   * Get the chronological event log from the latest automatic solver pipeline.
   *
   * @return unmodifiable automatic solver event list
   */
  public List<String> getLastAutoSolverHistory() {
    if (lastAutoSolverHistory == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(lastAutoSolverHistory);
  }

  /**
   * Returns the list of trays in this column.
   *
   * @return list of {@link SimpleTray} objects
   */
  public List<SimpleTray> getTrays() {
    return trays;
  }

  /**
   * Sets the reflux ratio on the condenser (if present). Also stores a
   * {@link ColumnSpecification.SpecificationType#REFLUX_RATIO REFLUX_RATIO} top specification so
   * that the column records the user's intent.
   *
   * @param refluxRatio the desired reflux ratio (L/D)
   */
  public void setCondenserRefluxRatio(double refluxRatio) {
    if (hasCondenser) {
      getCondenser().setRefluxRatio(refluxRatio);
    }
    this.topSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.TOP, refluxRatio);
  }

  // ======================== Column specification convenience methods
  // ========================

  /**
   * Returns the top (condenser) column specification.
   *
   * @return the top specification, or null if not set
   */
  public ColumnSpecification getTopSpecification() {
    return topSpecification;
  }

  /**
   * Returns the bottom (reboiler) column specification.
   *
   * @return the bottom specification, or null if not set
   */
  public ColumnSpecification getBottomSpecification() {
    return bottomSpecification;
  }

  /**
   * Sets the top column specification with location validation.
   *
   * @param spec the specification (must have location TOP)
   * @throws IllegalArgumentException if the specification location is not TOP
   */
  public void setTopSpecification(ColumnSpecification spec) {
    if (spec != null && spec.getLocation() != ColumnSpecification.ProductLocation.TOP) {
      throw new IllegalArgumentException(
          "Top specification must have location TOP, got: " + spec.getLocation());
    }
    this.topSpecification = spec;
  }

  /**
   * Sets the bottom column specification with location validation.
   *
   * @param spec the specification (must have location BOTTOM)
   * @throws IllegalArgumentException if the specification location is not BOTTOM
   */
  public void setBottomSpecification(ColumnSpecification spec) {
    if (spec != null && spec.getLocation() != ColumnSpecification.ProductLocation.BOTTOM) {
      throw new IllegalArgumentException(
          "Bottom specification must have location BOTTOM, got: " + spec.getLocation());
    }
    this.bottomSpecification = spec;
  }

  /**
   * Convenience method to specify a target mole fraction purity for the top product.
   *
   * @param componentName the component to constrain
   * @param purity the desired mole fraction (0 to 1)
   */
  public void setTopProductPurity(String componentName, double purity) {
    this.topSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.TOP, purity, componentName);
  }

  /**
   * Convenience method to specify a target mole fraction purity for the bottom product.
   *
   * @param componentName the component to constrain
   * @param purity the desired mole fraction (0 to 1)
   */
  public void setBottomProductPurity(String componentName, double purity) {
    this.bottomSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
            ColumnSpecification.ProductLocation.BOTTOM, purity, componentName);
  }

  /**
   * Convenience method to specify a reboiler boilup ratio (V/B).
   *
   * @param boilupRatio the desired boilup ratio
   */
  public void setReboilerBoilupRatio(double boilupRatio) {
    if (hasReboiler) {
      getReboiler().setRefluxRatio(boilupRatio);
    }
    this.bottomSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
            ColumnSpecification.ProductLocation.BOTTOM, boilupRatio);
  }

  /**
   * Convenience method to specify a target component recovery in the top product.
   *
   * @param componentName the component to constrain
   * @param recovery the desired recovery fraction (0 to 1)
   */
  public void setTopComponentRecovery(String componentName, double recovery) {
    this.topSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
            ColumnSpecification.ProductLocation.TOP, recovery, componentName);
  }

  /**
   * Convenience method to specify a target component recovery in the bottom product.
   *
   * @param componentName the component to constrain
   * @param recovery the desired recovery fraction (0 to 1)
   */
  public void setBottomComponentRecovery(String componentName, double recovery) {
    this.bottomSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.COMPONENT_RECOVERY,
            ColumnSpecification.ProductLocation.BOTTOM, recovery, componentName);
  }

  /**
   * Convenience method to specify a target molar flow rate for the top product.
   *
   * @param flowRate the desired flow rate value
   * @param unit the flow rate unit (currently expected as {@code mol/hr})
   */
  public void setTopProductFlowRate(double flowRate, String unit) {
    this.topSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
            ColumnSpecification.ProductLocation.TOP, flowRate);
  }

  /**
   * Convenience method to specify a target molar flow rate for the bottom product.
   *
   * @param flowRate the desired flow rate value
   * @param unit the flow rate unit (e.g. "mol/hr")
   */
  public void setBottomProductFlowRate(double flowRate, String unit) {
    // Store the specification in mol/hr (the column evaluator uses mol/hr
    // internally)
    this.bottomSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
            ColumnSpecification.ProductLocation.BOTTOM, flowRate);
  }

  /**
   * Convenience method to specify a target condenser duty.
   *
   * @param duty target condenser duty in watts
   */
  public void setCondenserDutySpecification(double duty) {
    this.topSpecification = new ColumnSpecification(ColumnSpecification.SpecificationType.DUTY,
        ColumnSpecification.ProductLocation.TOP, duty);
  }

  /**
   * Convenience method to specify a target reboiler duty.
   *
   * @param duty target reboiler duty in watts
   */
  public void setReboilerDutySpecification(double duty) {
    this.bottomSpecification = new ColumnSpecification(ColumnSpecification.SpecificationType.DUTY,
        ColumnSpecification.ProductLocation.BOTTOM, duty);
  }

  // ======================== Builder pattern ========================

  /**
   * Creates a new Builder for constructing a DistillationColumn with a fluent API.
   *
   * @param name the column name
   * @return a new Builder instance
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  // ============ Dynamic Column Getters/Setters ============

  /**
   * Enables or disables the dynamic tray-by-tray model for transient simulation.
   *
   * @param enabled true to enable dynamic column model
   */
  public void setDynamicColumnEnabled(boolean enabled) {
    this.dynamicColumnEnabled = enabled;
  }

  /**
   * Returns whether the dynamic tray-by-tray model is enabled.
   *
   * @return true if dynamic column model is active
   */
  public boolean isDynamicColumnEnabled() {
    return dynamicColumnEnabled;
  }

  /**
   * Sets the weir height for all trays (used in Francis weir overflow).
   *
   * @param weirHeight weir height in metres
   */
  public void setTrayWeirHeight(double weirHeight) {
    this.trayWeirHeight = Math.max(0.0, weirHeight);
  }

  /**
   * Gets the weir height for all trays.
   *
   * @return weir height in metres
   */
  public double getTrayWeirHeight() {
    return trayWeirHeight;
  }

  /**
   * Sets the weir crest length for all trays.
   *
   * @param weirLength weir length in metres
   */
  public void setTrayWeirLength(double weirLength) {
    this.trayWeirLength = Math.max(0.0, weirLength);
  }

  /**
   * Gets the weir crest length for all trays.
   *
   * @return weir length in metres
   */
  public double getTrayWeirLength() {
    return trayWeirLength;
  }

  /**
   * Returns the liquid holdup array (moles per tray). May be null if dynamic model has not been
   * initialized.
   *
   * @return array of liquid holdups indexed by tray number, or null
   */
  public double[] getTrayLiquidHoldup() {
    return trayLiquidHoldup;
  }

  /**
   * Returns the per-tray enthalpy array in J. May be null if energy balance has not been
   * initialized.
   *
   * @return array of tray enthalpies indexed by tray number, or null
   */
  public double[] getTrayEnthalpy() {
    return trayEnthalpy;
  }

  /**
   * Sets the dry tray pressure drop per tray in Pa. Used in the dynamic vapor hydraulic model to
   * compute vapor flow rate as a function of pressure difference between trays.
   *
   * @param dpPa dry tray pressure drop in Pascals (positive value)
   */
  public void setTrayDryPressureDrop(double dpPa) {
    this.trayDryPressureDrop = Math.max(0.0, dpPa);
  }

  /**
   * Returns the dry tray pressure drop per tray in Pa.
   *
   * @return dry tray pressure drop in Pa
   */
  public double getTrayDryPressureDrop() {
    return trayDryPressureDrop;
  }

  /**
   * Enables or disables the per-tray energy balance in dynamic mode. When enabled, each tray's
   * enthalpy is tracked and PH flash is used for re-equilibration instead of TP flash.
   *
   * @param enabled true to enable energy-balanced trays
   */
  public void setDynamicEnergyEnabled(boolean enabled) {
    this.dynamicEnergyEnabled = enabled;
  }

  /**
   * Returns whether the per-tray energy balance is enabled.
   *
   * @return true if energy-balanced trays are active
   */
  public boolean isDynamicEnergyEnabled() {
    return dynamicEnergyEnabled;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Dynamic distillation column model. When {@code dynamicColumnEnabled} is true, performs a single
   * forward-Euler integration step on each tray's liquid holdup using the MESH equations. Liquid
   * leaving each tray is calculated using the Francis weir overflow formula.
   * </p>
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!dynamicColumnEnabled || trays.isEmpty()) {
      // Fall back to steady-state solve
      if (getCalculateSteadyState()) {
        run(id);
      }
      increaseTime(dt);
      return;
    }

    int nTrays = trays.size();

    // Initialize liquid holdups on first call
    if (trayLiquidHoldup == null || trayLiquidHoldup.length != nTrays) {
      trayLiquidHoldup = new double[nTrays];
      for (int i = 0; i < nTrays; i++) {
        SystemInterface trayFluid = trays.get(i).getThermoSystem();
        if (trayFluid != null) {
          trayLiquidHoldup[i] = trayFluid.getTotalNumberOfMoles();
        } else {
          trayLiquidHoldup[i] = 100.0; // default
        }
      }
    }

    // Initialize per-tray enthalpy on first call (when energy balance enabled)
    if (dynamicEnergyEnabled && (trayEnthalpy == null || trayEnthalpy.length != nTrays)) {
      trayEnthalpy = new double[nTrays];
      for (int i = 0; i < nTrays; i++) {
        SystemInterface trayFluid = trays.get(i).getThermoSystem();
        if (trayFluid != null) {
          trayEnthalpy[i] = trayFluid.getEnthalpy();
        }
      }
    }

    // Francis weir coefficient (SI)
    double cWeir = 1.84;

    // Pre-compute overflow rates for all trays so downstream trays can
    // read the correct liquid-in from the tray above
    double[] overflowMolRate = new double[nTrays];
    double[] liquidMolarVol = new double[nTrays];
    double trayArea = Math.PI / 4.0 * internalDiameter * internalDiameter;
    for (int i = 0; i < nTrays; i++) {
      SystemInterface trayFluid = trays.get(i).getThermoSystem();
      liquidMolarVol[i] = 1.0e-4; // default m3/mol
      if (trayFluid != null && (trayFluid.hasPhaseType("aqueous") || trayFluid.hasPhaseType("oil")
          || trayFluid.getNumberOfPhases() > 1)) {
        double liquidDensity = trayFluid.getPhase(1).getDensity("mol/m3");
        if (liquidDensity > 0) {
          liquidMolarVol[i] = 1.0 / liquidDensity;
        }
      }
      double liquidVolume = trayLiquidHoldup[i] * liquidMolarVol[i];
      double liquidHeight = trayArea > 0 ? liquidVolume / trayArea : 0.0;
      double howOverWeir = Math.max(0.0, liquidHeight - trayWeirHeight);
      double overflowVol = cWeir * trayWeirLength * Math.pow(howOverWeir, 1.5);
      overflowMolRate[i] = overflowVol / liquidMolarVol[i];
    }

    // For each tray (top to bottom), compute flows and update holdup
    for (int i = 0; i < nTrays; i++) {
      SimpleTray tray = trays.get(i);
      SystemInterface trayFluid = tray.getThermoSystem();
      if (trayFluid == null) {
        continue;
      }

      double liquidOutRate = overflowMolRate[i];

      // Vapor in-flow from tray below
      double vaporInRate = 0.0;
      if (i < nTrays - 1) {
        SystemInterface belowFluid = trays.get(i + 1).getThermoSystem();
        if (belowFluid != null && belowFluid.getNumberOfPhases() > 0) {
          if (trayDryPressureDrop > 0.0) {
            // Pressure-driven vapor hydraulic: vapor rises if pressure below exceeds
            // pressure above by more than the tray resistance (dry DP + liquid head).
            double liquidHeight =
                trayArea > 0 ? trayLiquidHoldup[i] * liquidMolarVol[i] / trayArea : 0.0;
            double liquidHeadPa =
                liquidHeight * 9.81 * (liquidMolarVol[i] > 0 ? 1.0 / liquidMolarVol[i] : 800.0);
            double totalTrayDp = trayDryPressureDrop + liquidHeadPa;
            double pBelow = belowFluid.getPressure("Pa");
            double pAbove = trayFluid.getPressure("Pa");
            double dpAvailable = pBelow - pAbove;
            if (dpAvailable > 0.0 && totalTrayDp > 0.0) {
              // Vapor flow proportional to sqrt of available DP fraction
              double vaporMoles = belowFluid.getPhase(0).getNumberOfMolesInPhase();
              double dpRatio = Math.min(dpAvailable / totalTrayDp, 2.0);
              vaporInRate = vaporMoles * Math.sqrt(dpRatio) / Math.max(dt, 0.001);
            }
          } else {
            // Original simplified model: all vapor rises in one timestep
            vaporInRate = belowFluid.getPhase(0).getNumberOfMolesInPhase() / Math.max(dt, 0.001);
          }
        }
      }

      // Liquid in-flow from tray above (use pre-computed overflow)
      double liquidInRate = (i > 0) ? overflowMolRate[i - 1] : 0.0;

      // Feed stream contribution
      double feedRate = 0.0;
      double feedEnthalpy = 0.0;
      List<StreamInterface> trayFeeds = feedStreams.get(i);
      if (trayFeeds != null) {
        for (StreamInterface feedStream : trayFeeds) {
          if (feedStream.getThermoSystem() != null) {
            double fMoles =
                feedStream.getThermoSystem().getTotalNumberOfMoles() / Math.max(dt, 0.001);
            feedRate += fMoles;
            if (dynamicEnergyEnabled) {
              feedEnthalpy += feedStream.getThermoSystem().getEnthalpy() / Math.max(dt, 0.001);
            }
          }
        }
      }

      // Vapor production rate from this tray
      double vaporOutRate = 0.0;
      if (trayFluid.getNumberOfPhases() > 0) {
        vaporOutRate = trayFluid.getPhase(0).getNumberOfMolesInPhase() / Math.max(dt, 0.001);
      }

      // Forward Euler holdup update: dn/dt = Lin + Vin + F - Lout - Vout
      double dHoldup = (liquidInRate + vaporInRate + feedRate - liquidOutRate - vaporOutRate) * dt;
      trayLiquidHoldup[i] = Math.max(0.0, trayLiquidHoldup[i] + dHoldup);

      // --- Re-flash the tray ---
      if (dynamicEnergyEnabled && trayEnthalpy != null) {
        // Energy-balance mode: compute enthalpy flows and use PH flash
        double hLiqIn = 0.0;
        if (i > 0 && liquidInRate > 0) {
          SystemInterface aboveFluid = trays.get(i - 1).getThermoSystem();
          if (aboveFluid != null && aboveFluid.getNumberOfPhases() > 1) {
            double molarH = aboveFluid.getPhase(1).getEnthalpy()
                / Math.max(aboveFluid.getPhase(1).getNumberOfMolesInPhase(), 1.0);
            hLiqIn = liquidInRate * molarH;
          }
        }
        double hVapIn = 0.0;
        if (i < nTrays - 1 && vaporInRate > 0) {
          SystemInterface belowFluid = trays.get(i + 1).getThermoSystem();
          if (belowFluid != null && belowFluid.getNumberOfPhases() > 0) {
            double molarH = belowFluid.getPhase(0).getEnthalpy()
                / Math.max(belowFluid.getPhase(0).getNumberOfMolesInPhase(), 1.0);
            hVapIn = vaporInRate * molarH;
          }
        }
        double hLiqOut = 0.0;
        if (liquidOutRate > 0 && trayFluid.getNumberOfPhases() > 1) {
          double molarH = trayFluid.getPhase(1).getEnthalpy()
              / Math.max(trayFluid.getPhase(1).getNumberOfMolesInPhase(), 1.0);
          hLiqOut = liquidOutRate * molarH;
        }
        double hVapOut = 0.0;
        if (vaporOutRate > 0 && trayFluid.getNumberOfPhases() > 0) {
          double molarH = trayFluid.getPhase(0).getEnthalpy()
              / Math.max(trayFluid.getPhase(0).getNumberOfMolesInPhase(), 1.0);
          hVapOut = vaporOutRate * molarH;
        }
        double dEnthalpy = (hLiqIn + hVapIn + feedEnthalpy - hLiqOut - hVapOut) * dt;
        trayEnthalpy[i] += dEnthalpy;

        // PH flash: set tray fluid to tracked enthalpy
        try {
          neqsim.thermodynamicoperations.ThermodynamicOperations trayOps =
              new neqsim.thermodynamicoperations.ThermodynamicOperations(trayFluid);
          trayOps.PHflash(trayEnthalpy[i]);
        } catch (Exception ex) {
          logger.warn("Dynamic tray " + i + " PH flash failed: " + ex.getMessage());
          // Fallback to TP flash
          try {
            tray.run(id);
          } catch (Exception ex2) {
            logger.warn("Dynamic tray " + i + " TP flash fallback failed: " + ex2.getMessage());
          }
        }
      } else {
        // Default: TP flash (original behavior)
        try {
          tray.run(id);
        } catch (Exception ex) {
          logger.warn("Dynamic tray " + i + " flash failed: " + ex.getMessage());
        }
      }
    }

    // Update column outlet streams from top and bottom trays
    if (trays.size() > 0) {
      StreamInterface gasOut = trays.get(nTrays - 1).getGasOutStream();
      if (gasOut != null && gasOut.getThermoSystem() != null) {
        gasOutStream.setThermoSystem(gasOut.getThermoSystem().clone());
      }
      StreamInterface liqOut = trays.get(0).getLiquidOutStream();
      if (liqOut != null && liqOut.getThermoSystem() != null) {
        liquidOutStream.setThermoSystem(liqOut.getThermoSystem().clone());
      }
    }

    increaseTime(dt);
    setCalculationIdentifier(id);
  }

  /**
   * Fluent builder for {@link DistillationColumn}.
   *
   * <p>
   * Example usage:
   * </p>
   *
   * <pre>
   * DistillationColumn col = DistillationColumn.builder("Deethanizer").numberOfTrays(7)
   *     .withCondenserAndReboiler().topPressure(30.0, "bara").bottomPressure(31.0, "bara")
   *     .insideOut().addFeedStream(feed, 4).build();
   * </pre>
   *
   * @author esol
   * @version 1.0
   */
  public static class Builder {
    /** Column name. */
    private final String name;
    /** Number of simple (non-condenser/reboiler) trays. */
    private int numberOfTrays = 5;
    /** Whether to add a condenser. */
    private boolean condenser = false;
    /** Whether to add a reboiler. */
    private boolean reboiler = false;
    /** Top tray pressure in bara (-1 means unset). */
    private double topPressure = -1.0;
    /** Bottom tray pressure in bara (-1 means unset). */
    private double bottomPressure = -1.0;
    /** Temperature convergence tolerance. */
    private double tempTol = -1.0;
    /** Mass balance convergence tolerance. */
    private double massTol = -1.0;
    /** Maximum iterations. */
    private int maxIter = -1;
    /** Solver type. */
    private SolverType solver = null;
    /** Relaxation factor for damped substitution. */
    private double relaxation = -1.0;
    /** Internal column diameter. */
    private double diameter = -1.0;
    /** Top product specification. */
    private ColumnSpecification topSpec = null;
    /** Bottom product specification. */
    private ColumnSpecification bottomSpec = null;
    /** Feed streams with tray indices. */
    private final java.util.List<Object[]> feeds = new ArrayList<Object[]>();

    /**
     * Creates a builder with the given column name.
     *
     * @param name column name
     */
    Builder(String name) {
      this.name = name;
    }

    /**
     * Sets the number of simple trays (excluding condenser and reboiler).
     *
     * @param n number of trays
     * @return this builder
     */
    public Builder numberOfTrays(int n) {
      this.numberOfTrays = n;
      return this;
    }

    /**
     * Configures the column with both a condenser and a reboiler.
     *
     * @return this builder
     */
    public Builder withCondenserAndReboiler() {
      this.condenser = true;
      this.reboiler = true;
      return this;
    }

    /**
     * Sets the top tray pressure.
     *
     * @param pressure pressure value
     * @param unit pressure unit (e.g. "bara")
     * @return this builder
     */
    public Builder topPressure(double pressure, String unit) {
      this.topPressure = pressure;
      return this;
    }

    /**
     * Sets the bottom tray pressure.
     *
     * @param pressure pressure value
     * @param unit pressure unit (e.g. "bara")
     * @return this builder
     */
    public Builder bottomPressure(double pressure, String unit) {
      this.bottomPressure = pressure;
      return this;
    }

    /**
     * Sets both top and bottom pressure to the same value.
     *
     * @param pressure pressure value
     * @param unit pressure unit (e.g. "bara")
     * @return this builder
     */
    public Builder pressure(double pressure, String unit) {
      this.topPressure = pressure;
      this.bottomPressure = pressure;
      return this;
    }

    /**
     * Sets the temperature convergence tolerance.
     *
     * @param tol tolerance value
     * @return this builder
     */
    public Builder temperatureTolerance(double tol) {
      this.tempTol = tol;
      return this;
    }

    /**
     * Sets the mass balance convergence tolerance.
     *
     * @param tol tolerance value
     * @return this builder
     */
    public Builder massBalanceTolerance(double tol) {
      this.massTol = tol;
      return this;
    }

    /**
     * Sets a combined tolerance for temperature and mass balance.
     *
     * @param tol tolerance value
     * @return this builder
     */
    public Builder tolerance(double tol) {
      this.tempTol = tol;
      this.massTol = tol;
      return this;
    }

    /**
     * Sets the maximum number of solver iterations.
     *
     * @param maxIter maximum iterations
     * @return this builder
     */
    public Builder maxIterations(int maxIter) {
      this.maxIter = maxIter;
      return this;
    }

    /**
     * Selects the damped substitution solver.
     *
     * @return this builder
     */
    public Builder dampedSubstitution() {
      this.solver = SolverType.DAMPED_SUBSTITUTION;
      return this;
    }

    /**
     * Selects the inside-out solver.
     *
     * @return this builder
     */
    public Builder insideOut() {
      this.solver = SolverType.INSIDE_OUT;
      return this;
    }

    /**
     * Selects the automatic solver strategy.
     *
     * @return this builder
     */
    public Builder autoSolver() {
      this.solver = SolverType.AUTO;
      return this;
    }

    /**
     * Sets the relaxation factor for damped substitution.
     *
     * @param factor relaxation factor
     * @return this builder
     */
    public Builder relaxationFactor(double factor) {
      this.relaxation = factor;
      return this;
    }

    /**
     * Sets the internal column diameter.
     *
     * @param diameter diameter in meters
     * @return this builder
     */
    public Builder internalDiameter(double diameter) {
      this.diameter = diameter;
      return this;
    }

    /**
     * Adds a feed stream at the specified tray index.
     *
     * @param feed the feed stream
     * @param trayIndex the tray index for this feed
     * @return this builder
     */
    public Builder addFeedStream(StreamInterface feed, int trayIndex) {
      this.feeds.add(new Object[] {feed, trayIndex});
      return this;
    }

    /**
     * Sets a top product purity specification.
     *
     * @param componentName the component name
     * @param purity the target mole fraction
     * @return this builder
     */
    public Builder topProductPurity(String componentName, double purity) {
      this.topSpec = new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_PURITY,
          ColumnSpecification.ProductLocation.TOP, purity, componentName);
      return this;
    }

    /**
     * Sets the bottom column specification.
     *
     * @param spec the bottom specification
     * @return this builder
     */
    public Builder bottomSpecification(ColumnSpecification spec) {
      this.bottomSpec = spec;
      return this;
    }

    /**
     * Builds the {@link DistillationColumn} with the configured parameters.
     *
     * @return the constructed column
     */
    public DistillationColumn build() {
      DistillationColumn col = new DistillationColumn(name, numberOfTrays, condenser, reboiler);
      if (topPressure >= 0) {
        col.setTopPressure(topPressure);
      }
      if (bottomPressure >= 0) {
        col.setBottomPressure(bottomPressure);
      }
      if (tempTol >= 0) {
        col.setTemperatureTolerance(tempTol);
      }
      if (massTol >= 0) {
        col.setMassBalanceTolerance(massTol);
      }
      if (maxIter >= 0) {
        col.setMaxNumberOfIterations(maxIter);
      }
      if (solver != null) {
        col.setSolverType(solver);
      }
      if (relaxation >= 0) {
        col.setRelaxationFactor(relaxation);
      }
      if (diameter >= 0) {
        col.setInternalDiameter(diameter);
      }
      if (topSpec != null) {
        col.topSpecification = topSpec;
      }
      if (bottomSpec != null) {
        col.bottomSpecification = bottomSpec;
      }
      for (Object[] feedEntry : feeds) {
        col.addFeedStream((StreamInterface) feedEntry[0], (Integer) feedEntry[1]);
      }
      return col;
    }
  }
}
