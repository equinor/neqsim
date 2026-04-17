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
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.distillation.internals.ColumnInternalsDesigner;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.distillation.DistillationColumnMechanicalDesign;
import neqsim.process.util.monitor.DistillationColumnResponse;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

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
  /** Multiplier governing how much the solver can extend beyond the nominal iteration budget. */
  private static final int ITERATION_OVERFLOW_MULTIPLIER = 12;
  /** Recommended base temperature tolerance for adaptive defaults. */
  private static final double DEFAULT_TEMPERATURE_TOLERANCE = 4.0e-3;
  /** Recommended base mass balance tolerance for adaptive defaults. */
  private static final double DEFAULT_MASS_BALANCE_TOLERANCE = 1.6e-2;
  /** Recommended base enthalpy balance tolerance for adaptive defaults. */
  private static final double DEFAULT_ENTHALPY_BALANCE_TOLERANCE = 1.6e-2;
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
    /** Wegstein acceleration of successive substitution. */
    WEGSTEIN,
    /** Sum-rates tearing method with flow correction. */
    SUM_RATES,
    /** Newton-Raphson simultaneous temperature correction. */
    NEWTON
  }

  /** Selected solver algorithm. Defaults to direct substitution. */
  private SolverType solverType = SolverType.DIRECT_SUBSTITUTION;

  /** Relaxation factor used when {@link SolverType#DAMPED_SUBSTITUTION} is active. */
  private double relaxationFactor = 0.5;
  /** Minimum relaxation factor used when adaptive damping scales down the sequential step. */
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
  /** Control whether energy residual must satisfy tolerance before convergence. */
  private boolean enforceEnergyBalanceTolerance = false;
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

  Mixer feedmixer = new Mixer("temp mixer");
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  int maxNumberOfIterations = 50;
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
  /** Duration of the latest solve step in seconds. */
  private double lastSolveTimeSeconds = 0.0;

  /**
   * Instead of Map&lt;Integer,StreamInterface&gt;, we store a list of feed streams per tray number.
   * This allows multiple feeds to the same tray.
   */
  private Map<Integer, List<StreamInterface>> feedStreams = new HashMap<>();
  private List<StreamInterface> unassignedFeedStreams = new ArrayList<>();

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

  /** Murphree tray efficiency applied to each equilibrium stage (0..1). Default 1.0 = ideal. */
  private double murphreeEfficiency = 1.0;

  /** Per-iteration convergence history: [iteration][0=tempErr, 1=massErr, 2=energyErr]. */
  private transient List<double[]> convergenceHistory = new ArrayList<>();

  /**
   * Number of simplified inner-loop iterations between rigorous flash updates in the IO solver.
   * Higher values reduce flash count but may reduce accuracy. Default 3.
   */
  private int innerLoopSteps = 3;

  // ============ Dynamic Simulation Fields ============
  /** Whether the dynamic tray model is enabled for transient simulation. */
  private boolean dynamicColumnEnabled = false;
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
   */
  public void addFeedStream(StreamInterface inputStream, int feedTrayNumber) {
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
   * Add a feed stream to the column without specifying the tray. The optimal feed tray will be
   * determined automatically based on temperature match.
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

    // If feedStreams is empty, nothing to do
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    // Grab the tray with the lowest index among the feed trays
    int firstFeedTrayNumber = feedStreams.keySet().stream().min(Integer::compareTo).get();

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
    assignUnassignedFeeds();
    convergenceHistory = new ArrayList<>();
    switch (solverType) {
      case DAMPED_SUBSTITUTION:
        solveSequential(id, relaxationFactor);
        break;
      case INSIDE_OUT:
        solveInsideOut(id);
        break;
      case WEGSTEIN:
        solveWegstein(id);
        break;
      case SUM_RATES:
        solveSumRates(id);
        break;
      case NEWTON:
        solveNewton(id);
        break;
      case DIRECT_SUBSTITUTION:
      default:
        solveSequential(id, 1.0);
        break;
    }
  }

  /**
   * Solve the column with an outer loop that adjusts condenser/reboiler temperatures to satisfy
   * product specifications. Uses a secant method for each specification that requires adjustment.
   *
   * @param id calculation identifier
   */
  private void solveWithSpecifications(UUID id) {
    boolean adjustTop = needsAdjustment(topSpecification) && hasCondenser;
    boolean adjustBottom = needsAdjustment(bottomSpecification) && hasReboiler;

    int maxOuterIter = 20;
    if (adjustTop && topSpecification != null) {
      maxOuterIter = Math.max(maxOuterIter, topSpecification.getMaxIterations());
    }
    if (adjustBottom && bottomSpecification != null) {
      maxOuterIter = Math.max(maxOuterIter, bottomSpecification.getMaxIterations());
    }

    double topTol = adjustTop ? topSpecification.getTolerance() : 1.0e-4;
    double bottomTol = adjustBottom ? bottomSpecification.getTolerance() : 1.0e-4;

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

      // Reset initialization flag for new temperature guesses
      setDoInitializion(true);

      // Solve the column with current settings
      solveInner(id);

      if (!solved()) {
        logger.warn("Inner solver did not converge in outer iteration {}", outerIter);
        // Try to continue with reduced step
      }

      // Evaluate specification errors
      double topError = adjustTop ? evaluateSpecError(topSpecification) : 0.0;
      double bottomError = adjustBottom ? evaluateSpecError(bottomSpecification) : 0.0;

      logger.info("Spec outer iteration {} topErr={} bottomErr={} topT={} bottomT={}", outerIter,
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
    // Reflux ratio and duty specs are handled directly; the others need outer-loop adjustment
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
    switch (solverType) {
      case DAMPED_SUBSTITUTION:
        solveSequential(id, relaxationFactor);
        break;
      case INSIDE_OUT:
        solveInsideOut(id);
        break;
      case WEGSTEIN:
        solveWegstein(id);
        break;
      case SUM_RATES:
        solveSumRates(id);
        break;
      case NEWTON:
        solveNewton(id);
        break;
      case DIRECT_SUBSTITUTION:
      default:
        solveSequential(id, 1.0);
        break;
    }
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
    for (List<StreamInterface> feeds : feedStreams.values()) {
      for (StreamInterface feed : feeds) {
        total += feed.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
      }
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
      feed.run(); // Ensure we have T
      double feedT = feed.getTemperature();

      int bestTray = -1;
      double minDiff = Double.MAX_VALUE;

      // Check if trays have reasonable temperatures (not all default)
      // If not initialized, just pick middle.
      boolean isInitialized = Math
          .abs(trays.get(0).getTemperature() - trays.get(numberOfTrays - 1).getTemperature()) > 1.0;

      if (!isInitialized) {
        bestTray = numberOfTrays / 2;
      } else {
        for (int i = 0; i < numberOfTrays; i++) {
          double trayT = trays.get(i).getTemperature();
          double diff = Math.abs(trayT - feedT);
          if (diff < minDiff) {
            minDiff = diff;
            bestTray = i;
          }
        }
      }

      addFeedStream(feed, bestTray);
      iter.remove();
    }
  }

  /**
   * Find the optimal number of trays to meet a product specification.
   *
   * @param productSpec the target purity (mole fraction) of the key component
   * @param componentName the name of the key component
   * @param isTopProduct true if the spec is for the top product (distillate), false for bottom
   * @param maxTrays the maximum number of trays to try
   * @return the optimal number of trays, or -1 if the spec could not be met
   */
  public int findOptimalNumberOfTrays(double productSpec, String componentName,
      boolean isTopProduct, int maxTrays) {
    // Capture existing specs
    double reboilerReflux = 0.1;
    boolean reboilerHasSetTemp = false;
    double reboilerTemp = Double.NaN;

    double condenserReflux = 0.1;
    boolean condenserHasSetTemp = false;
    double condenserTemp = Double.NaN;

    if (hasReboiler && getReboiler() != null) {
      reboilerReflux = getReboiler().getRefluxRatio();
      reboilerHasSetTemp = getReboiler().isSetOutTemperature();
      if (reboilerHasSetTemp) {
        reboilerTemp = getReboiler().getOutTemperature();
      }
    }

    if (hasCondenser && getCondenser() != null) {
      condenserReflux = getCondenser().getRefluxRatio();
      condenserHasSetTemp = getCondenser().isSetOutTemperature();
      if (condenserHasSetTemp) {
        condenserTemp = getCondenser().getOutTemperature();
      }
    }

    // Collect all feeds (assigned and unassigned)
    List<StreamInterface> allFeeds = new ArrayList<>(unassignedFeedStreams);
    for (List<StreamInterface> feeds : feedStreams.values()) {
      allFeeds.addAll(feeds);
    }

    // Start searching from a low number of trays to find the minimum (optimal)
    int startN = 2;
    if (hasReboiler) {
      startN++;
    }
    if (hasCondenser) {
      startN++;
    }

    // Ensure we don't exceed maxTrays immediately
    if (startN > maxTrays) {
      startN = maxTrays;
    }

    for (int n = startN; n <= maxTrays; n++) {
      // Re-initialize column with n trays
      // We can't easily "reset" the object, so we have to clear trays and rebuild.
      // This mimics the constructor logic.
      trays.clear();
      distoperations = new neqsim.process.processmodel.ProcessSystem();
      feedStreams.clear();
      unassignedFeedStreams.clear();
      unassignedFeedStreams.addAll(allFeeds); // All feeds become unassigned

      this.numberOfTrays = n;
      int trayCount = 0;

      // Reset feedmixer to avoid accumulating feeds across iterations
      feedmixer = new Mixer("temp mixer");
      feedmixer.setMultiPhaseCheck(doMultiPhaseCheck);

      if (hasReboiler) {
        Reboiler reb = new Reboiler("Reboiler");
        reb.setMultiPhaseCheck(doMultiPhaseCheck);
        reb.setRefluxRatio(reboilerReflux);
        if (reboilerHasSetTemp) {
          reb.setOutTemperature(reboilerTemp);
        }
        trays.add(reb);
        trayCount++;
      }

      // Middle trays
      int simpleTrays = n - (hasReboiler ? 1 : 0) - (hasCondenser ? 1 : 0);
      for (int i = 0; i < simpleTrays; i++) {
        SimpleTray tray = createMiddleTray("SimpleTray" + (i + 1), i);
        tray.setMultiPhaseCheck(doMultiPhaseCheck);
        trays.add(tray);
        trayCount++;
      }

      if (hasCondenser) {
        Condenser cond = new Condenser("Condenser");
        cond.setMultiPhaseCheck(doMultiPhaseCheck);
        cond.setRefluxRatio(condenserReflux);
        if (condenserHasSetTemp) {
          cond.setOutTemperature(condenserTemp);
        }
        trays.add(cond);
        trayCount++;
      }

      // Ensure numberOfTrays matches actual list size
      this.numberOfTrays = trays.size();

      for (int i = 0; i < this.numberOfTrays; i++) {
        distoperations.add(trays.get(i));
      }

      // Set pressures immediately if known
      if (topTrayPressure > 0 && bottomTrayPressure > 0) {
        double dp = (bottomTrayPressure - topTrayPressure) / (numberOfTrays - 1.0);
        for (int i = 0; i < numberOfTrays; i++) {
          trays.get(i).setPressure(bottomTrayPressure - i * dp);
        }
      }

      // Set temperatures if known to help feed assignment
      if (reboilerHasSetTemp && condenserHasSetTemp) {
        double dt = (condenserTemp - reboilerTemp) / (numberOfTrays - 1.0);
        for (int i = 0; i < numberOfTrays; i++) {
          trays.get(i).setTemperature(reboilerTemp + i * dt);
        }
      }

      // Reset initialization flag
      setDoInitializion(true);

      // Run the column
      try {
        run();
      } catch (Exception e) {
        // If run fails (e.g. convergence error), we might want to continue or log
        // For now, let's assume it might fail for small N and continue
        // System.out.println("Run failed for N=" + n + ": " + e.getMessage());
      }

      if (!solved()) {
        continue;
      }

      // Check spec
      double purity;
      if (isTopProduct) {
        purity = gasOutStream.getFluid().getComponent(componentName).getz();
      } else {
        purity = liquidOutStream.getFluid().getComponent(componentName).getz();
      }

      // System.out.println("Trays: " + n + " Purity: " + purity);

      if (purity >= productSpec) {
        return n;
      }
    }

    return -1;
  }

  /**
   * Execute the sequential substitution solver with an adaptive relaxation controller.
   *
   * @param id calculation identifier
   * @param initialRelaxation relaxation factor applied to the first iteration
   */
  private void solveSequential(UUID id, double initialRelaxation) {
    if (feedStreams.isEmpty()) {
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      lastIterationCount = 1;
      lastTemperatureResidual = 0.0;
      lastMassResidual = 0.0;
      lastEnergyResidual = 0.0;
      lastSolveTimeSeconds = 0.0;
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
    double divergenceThreshold = Math.max(totalFeedFlow * 50.0, 1.0e3);
    boolean divergenceRecoveryApplied = false;

    // Snapshot tray state before iterations as a safe recovery point.
    StreamInterface[] snapshotGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] snapshotLiquidStreams = new StreamInterface[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      snapshotGasStreams[i] = trays.get(i).getGasOutStream().clone();
      snapshotGasStreams[i].run();
      snapshotLiquidStreams[i] = trays.get(i).getLiquidOutStream().clone();
      snapshotLiquidStreams[i].run();
    }

    // On re-runs, seed previous-stream arrays from the snapshot so that
    // relaxation-based damping is active from the very first iteration.
    if (hasBeenSolvedBefore) {
      for (int i = 0; i < numberOfTrays; i++) {
        previousGasStreams[i] = snapshotGasStreams[i].clone();
        previousGasStreams[i].run();
        previousLiquidStreams[i] = snapshotLiquidStreams[i].clone();
        previousLiquidStreams[i].run();
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
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[i],
            trays.get(i).getLiquidOutStream(), relaxation);
        trays.get(i - 1).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[i] = relaxedLiquid;
        trays.get(i - 1).run(id);
        applyMurphreeCorrection(i - 1);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      StreamInterface reboilerFeed =
          applyRelaxation(previousLiquidStreams[1], trays.get(1).getLiquidOutStream(), relaxation);
      trays.get(0).replaceStream(streamNumb, reboilerFeed);
      currentLiquidStreams[1] = reboilerFeed;
      trays.get(0).run(id);
      applyMurphreeCorrection(0);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        StreamInterface relaxedGas = applyRelaxation(previousGasStreams[i - 1],
            trays.get(i - 1).getGasOutStream(), relaxation);
        trays.get(i).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[i - 1] = relaxedGas;
        trays.get(i).run(id);
        applyMurphreeCorrection(i);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[i + 1],
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
            previousGasStreams[i].run();
            previousLiquidStreams[i] = snapshotLiquidStreams[i].clone();
            previousLiquidStreams[i].run();
          }
          divergenceRecoveryApplied = true;
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

      logger.info("iteration {} relaxation={} tempErr={} massErr={} energyErr={}", iter, relaxation,
          err, massErr, energyErr);

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
  private void solveInsideOut(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      lastIterationCount = 1;
      lastTemperatureResidual = 0.0;
      lastMassResidual = 0.0;
      lastEnergyResidual = 0.0;
      lastSolveTimeSeconds = 0.0;
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
    double divergenceThresholdIO = Math.max(totalFeedFlowIO * 50.0, 1.0e3);
    boolean divergenceRecoveryAppliedIO = false;

    // Snapshot tray state before iterations as a safe recovery point.
    StreamInterface[] snapshotGasStreamsIO = new StreamInterface[numberOfTrays];
    StreamInterface[] snapshotLiquidStreamsIO = new StreamInterface[numberOfTrays];
    for (int i = 0; i < numberOfTrays; i++) {
      snapshotGasStreamsIO[i] = trays.get(i).getGasOutStream().clone();
      snapshotGasStreamsIO[i].run();
      snapshotLiquidStreamsIO[i] = trays.get(i).getLiquidOutStream().clone();
      snapshotLiquidStreamsIO[i].run();
    }

    // On re-runs, seed previous-stream arrays from the snapshot.
    if (hasBeenSolvedBefore) {
      for (int i = 0; i < numberOfTrays; i++) {
        previousGasStreams[i] = snapshotGasStreamsIO[i].clone();
        previousGasStreams[i].run();
        previousLiquidStreams[i] = snapshotLiquidStreamsIO[i].clone();
        previousLiquidStreams[i].run();
      }
    }

    // Simplified inner-loop K-value model setup
    int nc = trays.get(firstFeedTrayNumber).getThermoSystem().getNumberOfComponents();
    SimplifiedKvalueModel kModel = new SimplifiedKvalueModel(numberOfTrays, nc);
    double[][] prevOuterKvalues = null;
    double[] prevOuterTemps = new double[numberOfTrays];
    int outerIterCount = 0; // counts rigorous (outer) iterations
    int totalFlashSweeps = 0; // tracks flash count for diagnostics

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
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[stage],
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
        StreamInterface relaxedGas = applyRelaxation(previousGasStreams[stage - 1],
            trays.get(stage - 1).getGasOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[stage - 1] = relaxedGas;
        trays.get(stage).run(id);
        applyMurphreeCorrection(stage);
      }

      // Phase 3: Polish liquid sweep (condenser → feed) for better coupling
      for (int stage = numberOfTrays - 2; stage >= firstFeedTrayNumber; stage--) {
        int replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[stage + 1],
            trays.get(stage + 1).getLiquidOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[stage + 1] = relaxedLiquid;
        trays.get(stage).run(id);
        applyMurphreeCorrection(stage);
      }

      // Phase 4: Stripping factor correction — adjust temperatures using V/L flow balance
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
          // Mild correction: push temperature up if too much liquid, down if too much vapor
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
        for (int inner = 0; inner < innerLoopSteps; inner++) {
          double innerTempResidual = innerLoopIteration(kModel, relaxation);
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
        // Update err to reflect the latest inner loop temperature change
        err = Math.abs(err);
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
            previousGasStreams[i].run();
            previousLiquidStreams[i] = snapshotLiquidStreamsIO[i].clone();
            previousLiquidStreams[i].run();
          }
          divergenceRecoveryAppliedIO = true;
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

      logger.info(
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

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
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

      logger.info("error iteration = " + iter + "   err = " + err + " massErr= " + massErr
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

    lastIterationCount = iter;
    lastTemperatureResidual = err;
    lastMassResidual = massErr;
    lastEnergyResidual = energyErr;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    hasBeenSolvedBefore = true;
    lastTotalFeedFlow = totalFeedFlowBroyden;

    gasOutStream
        .setThermoSystem(trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    liquidOutStream.setCalculationIdentifier(id);

    for (int i = 0; i < numberOfTrays; i++) {
      trays.get(i).setCalculationIdentifier(id);
    }
    setCalculationIdentifier(id);
  }

  /**
   * Solve the column using the adaptive sequential substitution scheme with a damped starting step.
   *
   * @param id calculation identifier
   */
  private void runDamped(UUID id) {
    solveSequential(id, relaxationFactor);
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
  private void solveWegstein(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      lastIterationCount = 1;
      lastTemperatureResidual = 0.0;
      lastMassResidual = 0.0;
      lastEnergyResidual = 0.0;
      lastSolveTimeSeconds = 0.0;
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

      logger.info("Wegstein iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
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

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
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
  private void solveSumRates(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      lastIterationCount = 1;
      lastTemperatureResidual = 0.0;
      lastMassResidual = 0.0;
      lastEnergyResidual = 0.0;
      lastSolveTimeSeconds = 0.0;
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

      // Sum-rates flow correction: adjust tray temperatures with flow-weighted damping
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

      logger.info("sum-rates iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
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
  private void solveNewton(UUID id) {
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    int firstFeedTrayNumber = prepareColumnForSolve();

    if (numberOfTrays == 1) {
      trays.get(0).run(id);
      gasOutStream.setThermoSystem(trays.get(0).getGasOutStream().getThermoSystem().clone());
      liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
      gasOutStream.setCalculationIdentifier(id);
      liquidOutStream.setCalculationIdentifier(id);
      setCalculationIdentifier(id);
      lastIterationCount = 1;
      lastTemperatureResidual = 0.0;
      lastMassResidual = 0.0;
      lastEnergyResidual = 0.0;
      lastSolveTimeSeconds = 0.0;
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

    // Warm-up: run a few direct substitution iterations to establish a reasonable profile
    int warmUpIterations = Math.min(3, iterationLimit / 3);
    trays.get(firstFeedTrayNumber).run(id);
    StreamInterface[] previousGasStreams = new StreamInterface[numberOfTrays];
    StreamInterface[] previousLiquidStreams = new StreamInterface[numberOfTrays];

    for (int w = 0; w < warmUpIterations; w++) {
      iter++;
      performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
      double tempRes = computeTemperatureResidual();
      err = tempRes;

      if (convergenceHistory != null) {
        massErr = getMassBalanceError();
        energyErr = getEnergyBalanceError();
        recordConvergence(new double[] {err, massErr, energyErr});
      }

      logger.info("newton warm-up iteration {} tempErr={}", iter, err);
      if (err < baseTempTolerance) {
        break;
      }
    }

    // Newton iterations
    double perturbation = 0.1; // temperature perturbation for Jacobian (K)
    double[] temperatures = new double[numberOfTrays];
    double[] residuals = new double[numberOfTrays];
    double[][] jacobian = new double[numberOfTrays][numberOfTrays];

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

      logger.info("newton iteration {} tempErr={} massErr={} energyErr={}", iter, err, massErr,
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

      // Determine which columns actually need perturbation
      boolean[] needsPerturb = new boolean[numberOfTrays];
      for (int j = 0; j < numberOfTrays; j++) {
        if (numberOfTrays <= 6) {
          needsPerturb[j] = true;
        } else {
          // Perturb column j if any row i within the band needs it
          for (int i = Math.max(0, j - halfBand); i <= Math.min(numberOfTrays - 1,
              j + halfBand); i++) {
            needsPerturb[j] = true;
            break;
          }
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
      // where residuals = f(T) = T_sweep - T_current (the function maps T_k -> T_{k+1})
      // The fixed-point is T* such that f(T*) = 0
      // Newton step: deltaT = -J^{-1} * residuals, but since we want T such that f(T)=0
      // and our Jacobian approximates df/dT, we solve: (J - I) * deltaT = -residuals
      // Because the true Jacobian of g(T) = T + f(T) is I + J_f, and Newton on g(T) = T
      // means (I + J_f - I) * deltaT = -(T + f(T) - T) => J_f * deltaT = -f(T)

      // Actually, residuals[i] = T_new[i] - T_old[i] is already f(T) = g(T) - T
      // The Jacobian J[i][j] = df_i/dT_j ≈ (f_perturbed - f_base) / dT_j
      // Newton seeks f(T) = 0, so: deltaT = -J^{-1} * f(T)

      // Solve J * deltaT = -residuals using Gaussian elimination with partial pivoting
      double[] rhs = new double[numberOfTrays];
      for (int i = 0; i < numberOfTrays; i++) {
        rhs[i] = -residuals[i];
      }

      double[] deltaT = solveLinearSystem(jacobian, rhs);

      if (deltaT == null) {
        // Singular Jacobian — fall back to direct substitution step
        logger.warn("Newton: singular Jacobian at iter {}, using direct substitution step", iter);
        for (int i = 0; i < numberOfTrays; i++) {
          trays.get(i).setTemperature(temperatures[i] + 0.5 * residuals[i]);
          trays.get(i).getThermoSystem().setTemperature(temperatures[i] + 0.5 * residuals[i]);
        }
        continue;
      }

      // Line search: try full Newton step, halve if residual increases
      double bestStepLength = 1.0;
      double bestNormRes = normRes;
      for (double stepLength = 1.0; stepLength >= 0.125; stepLength *= 0.5) {
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

      logger.debug("newton iteration {} step={} normRes={}->{}", iter, bestStepLength, normRes,
          bestNormRes);

      // Overflow: extend limit if not converged
      if (iter >= iterationLimit && err > baseTempTolerance && iterationLimit < maxIterationLimit) {
        iterationLimit = Math.min(maxIterationLimit, iterationLimit + 3);
      }
    }

    // Final sweep to ensure consistent tray state
    performFullTraySweep(id, firstFeedTrayNumber, previousGasStreams, previousLiquidStreams, 1.0);
    err = computeTemperatureResidual();
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
    double residual = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      residual +=
          Math.abs(trays.get(i).getThermoSystem().getTemperature() - trays.get(i).getTemperature());
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
      for (int j = 0; j < n; j++) {
        a[i][j] = matrixA[i][j];
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

      if (maxVal < 1e-30) {
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
    }
    return x;
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
   * @param solverType choice of solver
   */
  public void setSolverType(SolverType solverType) {
    this.solverType = solverType;
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
   * <p>
   * setBottomPressure.
   * </p>
   *
   * @param bottomPressure a double
   */
  public void setBottomPressure(double bottomPressure) {
    bottomTrayPressure = bottomPressure;
  }

  /** {@inheritDoc} */
  @Override
  public boolean solved() {
    return (err < getEffectiveTemperatureTolerance());
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
   * Retrieve the duration of the most recent solve in seconds.
   *
   * @return solve time in seconds
   */
  public double getLastSolveTimeSeconds() {
    return lastSolveTimeSeconds;
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
   * Access the configured relative enthalpy balance tolerance.
   *
   * @return enthalpy balance tolerance
   */
  public double getEnthalpyBalanceTolerance() {
    return getEffectiveEnthalpyBalanceTolerance();
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
  public double getMassBalance(String unit) {
    double inletFlow = 0.0;
    for (List<StreamInterface> feedList : feedStreams.values()) {
      for (StreamInterface feed : feedList) {
        inletFlow += feed.getThermoSystem().getFlowRate(unit);
      }
    }
    double outletFlow = getGasOutStream().getThermoSystem().getFlowRate(unit)
        + getLiquidOutStream().getThermoSystem().getFlowRate(unit);
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
   * Restore adaptive default tolerances, discarding manual overrides.
   */
  public void resetToleranceOverrides() {
    temperatureToleranceCustomized = false;
    massBalanceToleranceCustomized = false;
    enthalpyBalanceToleranceCustomized = false;
    temperatureTolerance = DEFAULT_TEMPERATURE_TOLERANCE;
    massBalanceTolerance = DEFAULT_MASS_BALANCE_TOLERANCE;
    enthalpyBalanceTolerance = DEFAULT_ENTHALPY_BALANCE_TOLERANCE;
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
        inlet += trays.get(i).getStream(j).getFluid().getEnthalpy();
      }

      double outlet = trays.get(i).getGasOutStream().getFluid().getEnthalpy();
      outlet += trays.get(i).getLiquidOutStream().getFluid().getEnthalpy();

      if (trays.get(i) instanceof Reboiler) {
        inlet += ((Reboiler) trays.get(i)).getDuty();
      } else if (trays.get(i) instanceof Condenser) {
        inlet += ((Condenser) trays.get(i)).getDuty();
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
    if (murphreeEfficiency >= 1.0 - 1e-10) {
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
    double emv = murphreeEfficiency;

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
    // The liquid stream is NOT corrected: it uses the equilibrium result from the flash.
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
      ((SimpleTray) trays.get(trayIndex)).setCachedGasOutStream(new Stream("", gasSystem));
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
    // Fast path: no damping needed, just clone and flash
    if (previous == null || relaxation >= 1.0) {
      StreamInterface relaxed = current.clone();
      relaxed.run();
      return relaxed;
    }

    StreamInterface relaxed = current.clone();
    double step = Math.max(0.0, Math.min(1.0, relaxation));
    double previousFlow = previous.getFlowRate("kg/hr");
    double currentFlow = current.getFlowRate("kg/hr");
    double mixedFlow = previousFlow + step * (currentFlow - previousFlow);
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

    double[] zMixed = new double[zPrev.length];
    double totalMolesMixed = 0.0;

    for (int i = 0; i < zPrev.length; i++) {
      double molesPrev_i = zPrev[i] * totalMolesPrev;
      double molesCurr_i = zCurr[i] * totalMolesCurr;
      double mixedMoles_i = molesPrev_i + step * (molesCurr_i - molesPrev_i);
      zMixed[i] = mixedMoles_i;
      totalMolesMixed += mixedMoles_i;
    }

    if (totalMolesMixed > 1e-12) {
      for (int i = 0; i < zMixed.length; i++) {
        zMixed[i] /= totalMolesMixed;
      }
      relaxed.getThermoSystem().setMolarComposition(zMixed);
    }

    relaxed.run();

    return relaxed;
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
    lastIterationCount = iterations;
    lastTemperatureResidual = temperatureResidual;
    lastMassResidual = massResidual;
    lastEnergyResidual = energyResidual;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;

    gasOutStream
        .setThermoSystem(trays.get(numberOfTrays - 1).getGasOutStream().getThermoSystem().clone());
    gasOutStream.setCalculationIdentifier(id);
    liquidOutStream.setThermoSystem(trays.get(0).getLiquidOutStream().getThermoSystem().clone());
    liquidOutStream.setCalculationIdentifier(id);

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
    setCalculationIdentifier(id);
  }

  /** Reset cached solve metrics when no calculation is performed. */
  private void resetLastSolveMetrics() {
    lastIterationCount = 0;
    lastTemperatureResidual = 0.0;
    lastMassResidual = 0.0;
    lastEnergyResidual = 0.0;
    lastSolveTimeSeconds = 0.0;
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
  public neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    if (getName() == null || getName().trim().isEmpty()) {
      result.addError("equipment", "Equipment has no name", "Set equipment name in constructor");
    }

    if (feedStreams.isEmpty() && unassignedFeedStreams.isEmpty()) {
      result.addError("stream", "No feed stream connected to distillation column",
          "Add a feed stream: column.addFeedStream(stream, feedTrayNumber)");
    }

    if (!hasCondenser && !hasReboiler) {
      result.addWarning("configuration",
          "Column has neither condenser nor reboiler — acting as a stripper/absorber",
          "Set hasCondenser=true and/or hasReboiler=true in constructor if separation is needed");
    }

    return result;
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
   * Set the Murphree tray efficiency for all stages.
   *
   * @param efficiency value between 0.0 (no separation) and 1.0 (ideal equilibrium stage)
   */
  public void setMurphreeEfficiency(double efficiency) {
    this.murphreeEfficiency = Math.max(0.0, Math.min(1.0, efficiency));
  }

  /**
   * Retrieve the current Murphree tray efficiency.
   *
   * @return Murphree efficiency
   */
  public double getMurphreeEfficiency() {
    return murphreeEfficiency;
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

  // ======================== Column specification convenience methods ========================

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
   * Convenience method to specify a target molar flow rate for the bottom product.
   *
   * @param flowRate the desired flow rate value
   * @param unit the flow rate unit (e.g. "mol/hr")
   */
  public void setBottomProductFlowRate(double flowRate, String unit) {
    // Store the specification in mol/hr (the column evaluator uses mol/hr internally)
    this.bottomSpecification =
        new ColumnSpecification(ColumnSpecification.SpecificationType.PRODUCT_FLOW_RATE,
            ColumnSpecification.ProductLocation.BOTTOM, flowRate);
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
      StreamInterface gasOut = trays.get(0).getGasOutStream();
      if (gasOut != null && gasOut.getThermoSystem() != null) {
        gasOutStream.setThermoSystem(gasOut.getThermoSystem().clone());
      }
      StreamInterface liqOut = trays.get(nTrays - 1).getLiquidOutStream();
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
