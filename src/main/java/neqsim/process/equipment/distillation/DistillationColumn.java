package neqsim.process.equipment.distillation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
  double condenserCoolingDuty = 10.0;
  private double reboilerTemperature = 273.15;
  private double condenserTemperature = 270.15;
  double topTrayPressure = -1.0;

  /** Temperature convergence tolerance. */
  private double temperatureTolerance = 1.0e-2;
  /** Mass balance convergence tolerance. */
  private double massBalanceTolerance = 1.0e-2;
  /** Enthalpy balance convergence tolerance. */
  private double enthalpyBalanceTolerance = 1.0e-2;
  /** Maximum allowed wall clock time for the solver (seconds). Zero disables the limit. */
  private double maxSolveSeconds = 60.0;
  /** Maximum iterations allowed without sufficient residual improvement before aborting. */
  private int maxStagnantIterations = 50;
  /** Minimum relative residual improvement required to reset the stagnation counter. */
  private double minResidualImprovement = 0.02;
  /** Minimum absolute residual improvement required to reset the stagnation counter. */
  private double minAbsoluteResidualImprovement = 1.0e-8;
  /** Flag indicating that the last solve aborted due to slow or stalled convergence. */
  private boolean abortedDueToSlowConvergence = false;
  /** Explanation for why the last solve aborted, if any. */
  private String lastAbortReason = null;

  /** Available solving strategies for the column. */
  public enum SolverType {
    /** Classic sequential substitution without damping. */
    DIRECT_SUBSTITUTION,
    /** Sequential substitution with temperature damping. */
    DAMPED_SUBSTITUTION,
    /** Broyden mixing of tray temperatures. */
    BROYDEN,
    /** Inside-out algorithm using centre-out propagation. */
    INSIDE_OUT
  }

  /** Selected solver algorithm. Defaults to direct substitution. */
  private SolverType solverType = SolverType.DIRECT_SUBSTITUTION;

  /**
   * Relaxation factor used when {@link SolverType#DAMPED_SUBSTITUTION} is active.
   */
  private double relaxationFactor = 0.5;
  /**
   * Minimum relaxation factor used when adaptive damping scales down the step.
   */
  private double minAdaptiveRelaxation = 0.1;
  /** Maximum relaxation factor allowed by the adaptive controller. */
  private double maxAdaptiveRelaxation = 1.0;
  /** Factor used to expand the relaxation factor when residuals shrink. */
  private double relaxationIncreaseFactor = 1.2;
  /** Factor used to shrink the relaxation factor when residuals grow. */
  private double relaxationDecreaseFactor = 0.5;

  Mixer feedmixer = new Mixer("temp mixer");
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  // Increased default max iterations for better convergence and stability
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

  /** Workspace array reused by sequential solver for previous temperatures. */
  private transient double[] sequentialOldTemperatures;
  /** Cached previous gas streams for sequential relaxation. */
  private transient StreamInterface[] sequentialPrevGasStreams;
  /** Cached previous liquid streams for sequential relaxation. */
  private transient StreamInterface[] sequentialPrevLiquidStreams;
  /** Workspace array for current gas stream updates. */
  private transient StreamInterface[] sequentialCurrentGasStreams;
  /** Workspace array for current liquid stream updates. */
  private transient StreamInterface[] sequentialCurrentLiquidStreams;

  /** Workspace array reused by Broyden solver for previous temperatures. */
  private transient double[] broydenOldTemperatures;
  /** Residual from previous Broyden iteration. */
  private transient double[] broydenPrevResidual;
  /** Difference between consecutive residual vectors. */
  private transient double[] broydenResidualDiff;
  /** Current residual vector for Broyden solver. */
  private transient double[] broydenResidual;

  /** Cached upward gas streams produced by trays during inside-out iterations. */
  private transient StreamInterface[] insideOutPrevUpGas;
  /**
   * Cached downward liquid streams produced by trays during inside-out iterations.
   */
  private transient StreamInterface[] insideOutPrevDownLiquid;
  /** Workspace for current upward gas streams in inside-out iterations. */
  private transient StreamInterface[] insideOutCurrentUpGas;
  /** Workspace for current downward liquid streams in inside-out iterations. */
  private transient StreamInterface[] insideOutCurrentDownLiquid;

  /** Minimum acceleration factor applied in Broyden mixing. */
  private double broydenMinAcceleration = -0.5;
  /** Maximum acceleration factor applied in Broyden mixing. */
  private double broydenMaxAcceleration = 0.8;
  /** Factor used to shrink acceleration when residuals grow. */
  private double broydenAccelerationDecreaseFactor = 0.5;
  /** Factor used to expand acceleration when residuals shrink. */
  private double broydenAccelerationIncreaseFactor = 1.25;
  /** Lower bound on adaptive acceleration scaling. */
  private double broydenMinScale = 0.1;
  /** Upper bound on adaptive acceleration scaling. */
  private double broydenMaxScale = 1.0;

  /**
   * Instead of Map&lt;Integer,StreamInterface&gt;, we store a list of feed streams per tray number.
   * This allows multiple feeds to the same tray.
   */
  private Map<Integer, List<StreamInterface>> feedStreams = new HashMap<>();

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

    // If user sets hasReboiler, put that in as the first tray in 'trays' list
    if (hasReboiler) {
      trays.add(new Reboiler("Reboiler"));
      this.numberOfTrays++;
    }

    // Then the middle "simple" trays
    for (int i = 0; i < numberOfTraysLocal; i++) {
      trays.add(new SimpleTray("SimpleTray" + (i + 1)));
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
    invalidateSolverWorkspace();
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
    switch (solverType) {
      case BROYDEN:
        runBroyden(id);
        return;
      case INSIDE_OUT:
        runInsideOut(id);
        return;
      case DAMPED_SUBSTITUTION:
        solveSequential(id, relaxationFactor);
        return;
      case DIRECT_SUBSTITUTION:
      default:
        solveSequential(id, 1.0);
        return;
    }
  }

  /**
   * Execute the sequential substitution solver with an adaptive relaxation controller.
   *
   * @param id calculation identifier
   * @param initialRelaxation relaxation factor applied to the first iteration
   */
  private void solveSequential(UUID id, double initialRelaxation) {
    resetAbortState();
    if (feedStreams.isEmpty()) {
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

    int[] numeroffeeds = new int[numberOfTrays];
    for (Entry<Integer, List<StreamInterface>> entry : feedStreams.entrySet()) {
      int feedTrayNumber = entry.getKey();
      List<StreamInterface> trayFeeds = entry.getValue();
      for (StreamInterface feedStream : trayFeeds) {
        numeroffeeds[feedTrayNumber]++;
        SystemInterface inpS = feedStream.getThermoSystem().clone();
        trays.get(feedTrayNumber).getStream(numeroffeeds[feedTrayNumber] - 1).setThermoSystem(inpS);
      }
    }

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
    ConvergenceTracker convergenceTracker = new ConvergenceTracker();
    sequentialOldTemperatures = ensureDoubleArray(sequentialOldTemperatures, numberOfTrays);
    sequentialPrevGasStreams = ensureStreamArray(sequentialPrevGasStreams, numberOfTrays);
    sequentialPrevLiquidStreams = ensureStreamArray(sequentialPrevLiquidStreams, numberOfTrays);
    sequentialCurrentGasStreams = ensureStreamArray(sequentialCurrentGasStreams, numberOfTrays);
    sequentialCurrentLiquidStreams =
        ensureStreamArray(sequentialCurrentLiquidStreams, numberOfTrays);

    double[] oldtemps = sequentialOldTemperatures;
    StreamInterface[] previousGasStreams = sequentialPrevGasStreams;
    StreamInterface[] previousLiquidStreams = sequentialPrevLiquidStreams;
    StreamInterface[] currentGasStreams = sequentialCurrentGasStreams;
    StreamInterface[] currentLiquidStreams = sequentialCurrentLiquidStreams;
    clearStreamArray(currentGasStreams);
    clearStreamArray(currentLiquidStreams);

    double relaxation =
        Math.max(minAdaptiveRelaxation, Math.min(maxAdaptiveRelaxation, initialRelaxation));

    trays.get(firstFeedTrayNumber).run(id);

    int iterationLimit = Math.max(maxNumberOfIterations, numberOfTrays * 3);

    do {
      iter++;

      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      clearStreamArray(currentGasStreams);
      clearStreamArray(currentLiquidStreams);

      for (int i = firstFeedTrayNumber; i > 1; i--) {
        int replaceStream = trays.get(i - 1).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[i],
            trays.get(i).getLiquidOutStream(), relaxation);
        trays.get(i - 1).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[i] = relaxedLiquid.clone();
        trays.get(i - 1).run(id);
      }

      int streamNumb = trays.get(0).getNumberOfInputStreams() - 1;
      StreamInterface reboilerFeed =
          applyRelaxation(previousLiquidStreams[1], trays.get(1).getLiquidOutStream(), relaxation);
      trays.get(0).replaceStream(streamNumb, reboilerFeed);
      currentLiquidStreams[1] = reboilerFeed.clone();
      trays.get(0).run(id);

      for (int i = 1; i <= numberOfTrays - 1; i++) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == (numberOfTrays - 1)) {
          replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        }
        StreamInterface relaxedGas = applyRelaxation(previousGasStreams[i - 1],
            trays.get(i - 1).getGasOutStream(), relaxation);
        trays.get(i).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[i - 1] = relaxedGas.clone();
        trays.get(i).run(id);
      }

      for (int i = numberOfTrays - 2; i >= firstFeedTrayNumber; i--) {
        int replaceStream = trays.get(i).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[i + 1],
            trays.get(i + 1).getLiquidOutStream(), relaxation);
        trays.get(i).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[i + 1] = relaxedLiquid.clone();
        trays.get(i).run(id);
      }

      double temperatureResidual = 0.0;
      double effectiveRelaxation = Math.max(0.0, Math.min(1.0, relaxation));
      for (int i = 0; i < numberOfTrays; i++) {
        double updated = trays.get(i).getThermoSystem().getTemperature();
        double newTemp = oldtemps[i] + effectiveRelaxation * (updated - oldtemps[i]);
        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - oldtemps[i]);
      }
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      massErr = getMassBalanceError();
      energyErr = getEnergyBalanceError();

      double combinedResidual =
          Math.max(Math.max(err / temperatureTolerance, massErr / massBalanceTolerance),
              energyErr / enthalpyBalanceTolerance);

      if (shouldAbortDueToSlowConvergence(convergenceTracker, iter, combinedResidual, startTime,
          "Sequential")) {
        break;
      }

      if (combinedResidual > previousCombinedResidual * 1.05) {
        relaxation = Math.max(minAdaptiveRelaxation, relaxation * relaxationDecreaseFactor);
      } else if (combinedResidual < previousCombinedResidual * 0.95) {
        relaxation = Math.min(maxAdaptiveRelaxation, relaxation * relaxationIncreaseFactor);
      }

      previousCombinedResidual = combinedResidual;

      for (int i = 0; i < numberOfTrays; i++) {
        if (currentGasStreams[i] != null) {
          previousGasStreams[i] = currentGasStreams[i];
        }
        if (currentLiquidStreams[i] != null) {
          previousLiquidStreams[i] = currentLiquidStreams[i];
        }
      }

      logger.info("iteration {} relaxation={} tempErr={} massErr={} energyErr={}", iter, relaxation,
          err, massErr, energyErr);
    } while ((err > temperatureTolerance || massErr > massBalanceTolerance
        || energyErr > enthalpyBalanceTolerance) && iter < iterationLimit);

    lastIterationCount = iter;
    lastTemperatureResidual = err;
    lastMassResidual = massErr;
    lastEnergyResidual = energyErr;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;

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
   * Determine pivot tray for inside-out iterations.
   *
   * @return tray index to use as pivot point for inside-out propagation
   */
  protected int determineInsideOutPivotTray() {
    if (!feedStreams.isEmpty()) {
      int min = feedStreams.keySet().stream().min(Integer::compareTo).orElse(0);
      int max = feedStreams.keySet().stream().max(Integer::compareTo).orElse(numberOfTrays - 1);
      int pivot = (min + max) / 2;
      if (pivot < 0) {
        return 0;
      }
      if (pivot >= numberOfTrays) {
        return numberOfTrays - 1;
      }
      return pivot;
    }
    return Math.max(0, Math.min(numberOfTrays / 2, numberOfTrays - 1));
  }

  /**
   * Default inside-out implementation falls back to sequential solution. Subclasses may override to
   * provide specialised behaviour.
   *
   * @param id calculation identifier
   */
  protected void runInsideOut(UUID id) {
    resetAbortState();
    if (feedStreams.isEmpty()) {
      resetLastSolveMetrics();
      return;
    }

    if (numberOfTrays <= 1) {
      solveSequential(id, relaxationFactor);
      return;
    }

    int pivotTray = determineInsideOutPivotTray();
    if (pivotTray <= 0 || pivotTray >= numberOfTrays - 1) {
      solveSequential(id, relaxationFactor);
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

    int[] numeroffeeds = new int[numberOfTrays];
    for (Entry<Integer, List<StreamInterface>> entry : feedStreams.entrySet()) {
      int feedTrayNumber = entry.getKey();
      List<StreamInterface> trayFeeds = entry.getValue();
      for (StreamInterface feedStream : trayFeeds) {
        numeroffeeds[feedTrayNumber]++;
        SystemInterface inpS = feedStream.getThermoSystem().clone();
        trays.get(feedTrayNumber).getStream(numeroffeeds[feedTrayNumber] - 1).setThermoSystem(inpS);
      }
    }

    if (isDoInitializion()) {
      this.init();
    }

    long startTime = System.nanoTime();
    ConvergenceTracker convergenceTracker = new ConvergenceTracker();

    sequentialOldTemperatures = ensureDoubleArray(sequentialOldTemperatures, numberOfTrays);
    double[] oldtemps = sequentialOldTemperatures;

    insideOutPrevUpGas = ensureStreamArray(insideOutPrevUpGas, numberOfTrays);
    insideOutPrevDownLiquid = ensureStreamArray(insideOutPrevDownLiquid, numberOfTrays);
    insideOutCurrentUpGas = ensureStreamArray(insideOutCurrentUpGas, numberOfTrays);
    insideOutCurrentDownLiquid = ensureStreamArray(insideOutCurrentDownLiquid, numberOfTrays);
    clearStreamArray(insideOutCurrentUpGas);
    clearStreamArray(insideOutCurrentDownLiquid);

    trays.get(pivotTray).run(id);

    double relaxation =
        Math.max(minAdaptiveRelaxation, Math.min(maxAdaptiveRelaxation, relaxationFactor));

    double previousCombinedResidual = Double.POSITIVE_INFINITY;
    double massErr = 1.0e10;
    double energyErr = 1.0e10;
    err = 1.0e10;
    int iter = 0;
    int iterationLimit = Math.max(maxNumberOfIterations, numberOfTrays * 3);

    do {
      iter++;

      for (int i = 0; i < numberOfTrays; i++) {
        oldtemps[i] = trays.get(i).getThermoSystem().getTemperature();
      }

      clearStreamArray(insideOutCurrentUpGas);
      clearStreamArray(insideOutCurrentDownLiquid);

      StreamInterface downStream = applyRelaxation(insideOutPrevDownLiquid[pivotTray],
          trays.get(pivotTray).getLiquidOutStream(), relaxation);
      insideOutCurrentDownLiquid[pivotTray] = downStream != null ? downStream.clone() : null;

      for (int i = pivotTray - 1; i >= 0 && downStream != null; i--) {
        int replaceIndex = trays.get(i).getNumberOfInputStreams() - 1;
        trays.get(i).replaceStream(replaceIndex, downStream);
        trays.get(i).run(id);
        insideOutCurrentUpGas[i] = trays.get(i).getGasOutStream().clone();
        if (i > 0) {
          downStream = applyRelaxation(insideOutPrevDownLiquid[i],
              trays.get(i).getLiquidOutStream(), relaxation);
          insideOutCurrentDownLiquid[i] = downStream != null ? downStream.clone() : null;
        }
      }

      StreamInterface upStream = applyRelaxation(insideOutPrevUpGas[pivotTray],
          trays.get(pivotTray).getGasOutStream(), relaxation);
      insideOutCurrentUpGas[pivotTray] = upStream != null ? upStream.clone() : null;

      for (int i = pivotTray + 1; i < numberOfTrays && upStream != null; i++) {
        int replaceIndex = trays.get(i).getNumberOfInputStreams() - 2;
        if (i == numberOfTrays - 1) {
          replaceIndex = trays.get(i).getNumberOfInputStreams() - 1;
        }
        trays.get(i).replaceStream(replaceIndex, upStream);
        trays.get(i).run(id);
        insideOutCurrentDownLiquid[i] = trays.get(i).getLiquidOutStream().clone();
        if (i < numberOfTrays - 1) {
          upStream =
              applyRelaxation(insideOutPrevUpGas[i], trays.get(i).getGasOutStream(), relaxation);
          insideOutCurrentUpGas[i] = upStream != null ? upStream.clone() : null;
        }
      }

      if (pivotTray > 0 && insideOutCurrentUpGas[pivotTray - 1] != null) {
        int gasIndex = trays.get(pivotTray).getNumberOfInputStreams() - 2;
        StreamInterface relaxedGas = applyRelaxation(insideOutPrevUpGas[pivotTray - 1],
            insideOutCurrentUpGas[pivotTray - 1], relaxation);
        trays.get(pivotTray).replaceStream(gasIndex, relaxedGas);
        insideOutCurrentUpGas[pivotTray - 1] = relaxedGas.clone();
      }

      if (pivotTray < numberOfTrays - 1 && insideOutCurrentDownLiquid[pivotTray + 1] != null) {
        int liquidIndex = trays.get(pivotTray).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(insideOutPrevDownLiquid[pivotTray + 1],
            insideOutCurrentDownLiquid[pivotTray + 1], relaxation);
        trays.get(pivotTray).replaceStream(liquidIndex, relaxedLiquid);
        insideOutCurrentDownLiquid[pivotTray + 1] = relaxedLiquid.clone();
      }

      trays.get(pivotTray).run(id);
      insideOutCurrentUpGas[pivotTray] = trays.get(pivotTray).getGasOutStream().clone();
      insideOutCurrentDownLiquid[pivotTray] = trays.get(pivotTray).getLiquidOutStream().clone();

      double temperatureResidual = 0.0;
      for (int i = 0; i < numberOfTrays; i++) {
        double updated = trays.get(i).getThermoSystem().getTemperature();
        temperatureResidual += Math.abs(updated - oldtemps[i]);
      }
      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      massErr = getMassBalanceError();
      energyErr = getEnergyBalanceError();

      double combinedResidual =
          Math.max(Math.max(err / temperatureTolerance, massErr / massBalanceTolerance),
              energyErr / enthalpyBalanceTolerance);

      if (shouldAbortDueToSlowConvergence(convergenceTracker, iter, combinedResidual, startTime,
          "Inside-out")) {
        break;
      }

      if (combinedResidual > previousCombinedResidual * 1.05) {
        relaxation = Math.max(minAdaptiveRelaxation, relaxation * relaxationDecreaseFactor);
      } else if (combinedResidual < previousCombinedResidual * 0.95) {
        relaxation = Math.min(maxAdaptiveRelaxation, relaxation * relaxationIncreaseFactor);
      }

      previousCombinedResidual = combinedResidual;

      for (int i = 0; i < numberOfTrays; i++) {
        if (insideOutCurrentUpGas[i] != null) {
          insideOutPrevUpGas[i] = insideOutCurrentUpGas[i];
        }
        if (insideOutCurrentDownLiquid[i] != null) {
          insideOutPrevDownLiquid[i] = insideOutCurrentDownLiquid[i];
        }
      }

      logger.info("inside-out iteration {} relaxation={} tempErr={} massErr={} energyErr={}", iter,
          relaxation, err, massErr, energyErr);
    } while ((err > temperatureTolerance || massErr > massBalanceTolerance
        || energyErr > enthalpyBalanceTolerance) && iter < iterationLimit);

    double solveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;
    updateSolveMetrics(iter, err, massErr, energyErr, solveTimeSeconds);

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
   * Solve the column using a simple Broyden mixing of tray temperatures.
   *
   * @param id calculation identifier
   */
  public void runBroyden(UUID id) {
    resetAbortState();
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

    broydenOldTemperatures = ensureDoubleArray(broydenOldTemperatures, numberOfTrays);
    broydenPrevResidual = ensureDoubleArray(broydenPrevResidual, numberOfTrays);
    broydenResidualDiff = ensureDoubleArray(broydenResidualDiff, numberOfTrays);
    broydenResidual = ensureDoubleArray(broydenResidual, numberOfTrays);
    fillArray(broydenPrevResidual, 0.0);
    fillArray(broydenResidualDiff, 0.0);
    fillArray(broydenResidual, 0.0);

    double previousCombinedResidual = Double.POSITIVE_INFINITY;
    double accelerationScale = Math.min(broydenMaxScale, Math.max(broydenMinScale, 1.0));

    trays.get(firstFeedTrayNumber).run(id);

    long startTime = System.nanoTime();
    int iterationLimit = Math.max(maxNumberOfIterations, numberOfTrays * 3);
    ConvergenceTracker convergenceTracker = new ConvergenceTracker();

    do {
      iter++;

      double[] oldTemps = broydenOldTemperatures;
      for (int i = 0; i < numberOfTrays; i++) {
        oldTemps[i] = trays.get(i).getThermoSystem().getTemperature();
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

      double betaNumerator = 0.0;
      double betaDenominator = 0.0;
      double temperatureResidual = 0.0;

      for (int i = 0; i < numberOfTrays; i++) {
        double residual = trays.get(i).getThermoSystem().getTemperature() - oldTemps[i];
        double diff = residual - broydenPrevResidual[i];
        broydenResidual[i] = residual;
        broydenResidualDiff[i] = diff;
        betaNumerator += residual * diff;
        betaDenominator += diff * diff;
      }

      double beta = 0.0;
      if (betaDenominator > 1.0e-16) {
        beta = -betaNumerator / betaDenominator;
      }

      double appliedAcceleration = accelerationScale * beta;
      if (appliedAcceleration > broydenMaxAcceleration) {
        appliedAcceleration = broydenMaxAcceleration;
      } else if (appliedAcceleration < broydenMinAcceleration) {
        appliedAcceleration = broydenMinAcceleration;
      }

      for (int i = 0; i < numberOfTrays; i++) {
        double adjustedResidual = broydenResidual[i] + appliedAcceleration * broydenResidualDiff[i];
        double newTemp = oldTemps[i] + adjustedResidual;
        trays.get(i).setTemperature(newTemp);
        temperatureResidual += Math.abs(newTemp - oldTemps[i]);
        broydenPrevResidual[i] = broydenResidual[i];
      }

      temperatureResidual /= Math.max(1, numberOfTrays);
      err = temperatureResidual;

      massErr = getMassBalanceError();
      energyErr = getEnergyBalanceError();

      double combinedResidual =
          Math.max(Math.max(err / temperatureTolerance, massErr / massBalanceTolerance),
              energyErr / enthalpyBalanceTolerance);

      if (shouldAbortDueToSlowConvergence(convergenceTracker, iter, combinedResidual, startTime,
          "Broyden")) {
        break;
      }

      if (combinedResidual > previousCombinedResidual * 1.05) {
        accelerationScale =
            Math.max(broydenMinScale, accelerationScale * broydenAccelerationDecreaseFactor);
        if (accelerationScale <= broydenMinScale + 1.0e-8) {
          fillArray(broydenPrevResidual, 0.0);
        }
      } else if (combinedResidual < previousCombinedResidual * 0.95) {
        accelerationScale =
            Math.min(broydenMaxScale, accelerationScale * broydenAccelerationIncreaseFactor);
      }
      previousCombinedResidual = combinedResidual;

      logger.info("iteration {} accel={} beta={} tempErr={} massErr={} energyErr={}", iter,
          appliedAcceleration, beta, err, massErr, energyErr);
    } while ((err > temperatureTolerance || massErr > massBalanceTolerance
        || energyErr > enthalpyBalanceTolerance) && iter < iterationLimit);

    lastIterationCount = iter;
    lastTemperatureResidual = err;
    lastMassResidual = massErr;
    lastEnergyResidual = energyErr;
    lastSolveTimeSeconds = (System.nanoTime() - startTime) / 1.0e9;

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
        trays.add(1, new SimpleTray("SimpleTray" + (oldNumberOfTrays + i + 1)));
      }
    } else if (change < 0) {
      for (int i = 0; i > change; i--) {
        trays.remove(1);
      }
    }
    numberOfTrays = tempNumberOfTrays;
    invalidateSolverWorkspace();
    setDoInitializion(true);
    init();
  }

  /**
   * Select the algorithm used when solving the column.
   *
   * @param solverType choice of solver
   */
  public void setSolverType(SolverType solverType) {
    if (solverType == null) {
      throw new IllegalArgumentException("Solver type cannot be null");
    }
    if (this.solverType != solverType) {
      invalidateSolverWorkspace();
    }
    this.solverType = solverType;
  }

  /**
   * Convenience overload allowing solver selection by name.
   *
   * @param solverName textual solver identifier
   */
  public void setSolverType(String solverName) {
    setSolverType(parseSolverType(solverName));
  }

  /**
   * Parse a textual solver identifier into a {@link SolverType}.
   *
   * @param solverName textual solver identifier
   * @return matching {@link SolverType}
   */
  public static SolverType parseSolverType(String solverName) {
    if (solverName == null) {
      throw new IllegalArgumentException("Solver name cannot be null");
    }
    String normalized = solverName.trim().toUpperCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("Solver name cannot be empty");
    }
    normalized = normalized.replace(" ", "").replace("-", "").replace("_", "");
    switch (normalized) {
      case "DIRECT":
      case "DIRECTSUBSTITUTION":
      case "SEQUENTIAL":
      case "SEQUENTIALSUBSTITUTION":
        return SolverType.DIRECT_SUBSTITUTION;
      case "DAMPED":
      case "DAMPEDSUBSTITUTION":
      case "ADAPTIVE":
        return SolverType.DAMPED_SUBSTITUTION;
      case "BROYDEN":
      case "BROUDEN":
      case "BRODYEN":
        return SolverType.BROYDEN;
      case "INSIDEOUT":
      case "INSIDEOUTALGORITHM":
      case "INSIDE-OUT":
        return SolverType.INSIDE_OUT;
      default:
        throw new IllegalArgumentException("Unknown solver type: " + solverName);
    }
  }

  /**
   * Retrieve the currently configured solver type.
   *
   * @return solver selection
   */
  public SolverType getSolverType() {
    return solverType;
  }

  /**
   * Convenience helper to select the Broyden solver.
   */
  public void useBroydenSolver() {
    setSolverType(SolverType.BROYDEN);
  }

  /**
   * Convenience helper to select the inside-out solver.
   */
  public void useInsideOutSolver() {
    setSolverType(SolverType.INSIDE_OUT);
  }

  /**
   * Reset cached solver state and progress metrics.
   */
  public void resetSolverState() {
    invalidateSolverWorkspace();
    resetLastSolveMetrics();
  }

  /**
   * Configure the acceleration bounds used by the Broyden solver.
   *
   * @param minAcceleration minimum acceleration factor
   * @param maxAcceleration maximum acceleration factor
   */
  public void setBroydenAccelerationBounds(double minAcceleration, double maxAcceleration) {
    if (minAcceleration > maxAcceleration) {
      throw new IllegalArgumentException("minAcceleration cannot be greater than maxAcceleration");
    }
    broydenMinAcceleration = minAcceleration;
    broydenMaxAcceleration = maxAcceleration;
  }

  /**
   * Configure the adaptive scaling limits applied to the Broyden acceleration.
   *
   * @param minScale minimum scaling value (positive)
   * @param maxScale maximum scaling value (&gt;= minScale)
   */
  public void setBroydenScalingLimits(double minScale, double maxScale) {
    if (minScale <= 0.0) {
      throw new IllegalArgumentException("minScale must be positive");
    }
    if (minScale > maxScale) {
      throw new IllegalArgumentException("minScale cannot be greater than maxScale");
    }
    broydenMinScale = minScale;
    broydenMaxScale = maxScale;
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
    return !abortedDueToSlowConvergence && err < temperatureTolerance;
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
    return massBalanceTolerance;
  }

  /**
   * Access the configured relative enthalpy balance tolerance.
   *
   * @return enthalpy balance tolerance
   */
  public double getEnthalpyBalanceTolerance() {
    return enthalpyBalanceTolerance;
  }

  /**
   * Access the configured average temperature tolerance.
   *
   * @return temperature tolerance in Kelvin
   */
  public double getTemperatureTolerance() {
    return temperatureTolerance;
  }

  /**
   * Retrieve the configured maximum wall time for solver iterations.
   *
   * @return maximum allowed solve time in seconds (0 disables the timeout)
   */
  public double getMaxSolveSeconds() {
    return maxSolveSeconds;
  }

  /**
   * Set the maximum allowed wall time for solver iterations.
   *
   * @param seconds time limit in seconds, non-positive values disable the timeout
   */
  public void setMaxSolveSeconds(double seconds) {
    if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
      throw new IllegalArgumentException("Max solve seconds must be a finite value");
    }
    maxSolveSeconds = seconds <= 0.0 ? 0.0 : seconds;
  }

  /**
   * Retrieve the maximum number of stagnant iterations allowed before aborting.
   *
   * @return maximum stagnant iteration count (0 disables stagnation detection)
   */
  public int getMaxStagnantIterations() {
    return maxStagnantIterations;
  }

  /**
   * Configure the maximum number of iterations permitted without significant improvement.
   *
   * @param iterations number of iterations, zero disables the guard
   */
  public void setMaxStagnantIterations(int iterations) {
    if (iterations < 0) {
      throw new IllegalArgumentException("Max stagnant iterations cannot be negative");
    }
    this.maxStagnantIterations = iterations;
  }

  /**
   * Retrieve the required relative residual improvement to reset the stagnation counter.
   *
   * @return minimum relative residual improvement
   */
  public double getMinResidualImprovement() {
    return minResidualImprovement;
  }

  /**
   * Configure the required relative residual improvement to reset the stagnation counter.
   *
   * @param improvement relative improvement (fraction), must be between 0 and 1
   */
  public void setMinResidualImprovement(double improvement) {
    if (Double.isNaN(improvement) || improvement < 0.0 || improvement >= 1.0) {
      throw new IllegalArgumentException("Relative improvement must be in [0, 1)");
    }
    this.minResidualImprovement = improvement;
  }

  /**
   * Retrieve the required absolute residual improvement to reset the stagnation counter.
   *
   * @return minimum absolute residual improvement
   */
  public double getMinAbsoluteResidualImprovement() {
    return minAbsoluteResidualImprovement;
  }

  /**
   * Configure the required absolute residual improvement to reset the stagnation counter.
   *
   * @param improvement absolute improvement threshold, must be non-negative
   */
  public void setMinAbsoluteResidualImprovement(double improvement) {
    if (Double.isNaN(improvement) || improvement < 0.0) {
      throw new IllegalArgumentException("Absolute improvement must be non-negative");
    }
    this.minAbsoluteResidualImprovement = improvement;
  }

  /**
   * Indicates whether the last solve aborted due to the convergence guards.
   *
   * @return {@code true} if the last solve aborted, otherwise {@code false}
   */
  public boolean wasAborted() {
    return abortedDueToSlowConvergence;
  }

  /**
   * Retrieve the explanation of why the last solve aborted, if available.
   *
   * @return abort reason or {@code null} if the previous solve completed normally
   */
  public String getLastAbortReason() {
    return lastAbortReason;
  }

  /**
   * <p>
   * Setter for the field <code>maxNumberOfIterations</code>.
   * </p>
   *
   * @param maxIter a int
   */
  public void setMaxNumberOfIterations(int maxIter) {
    this.maxNumberOfIterations = maxIter;
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

  /**
   * Set temperature convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setTemperatureTolerance(double tol) {
    this.temperatureTolerance = tol;
  }

  /**
   * Set mass balance convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setMassBalanceTolerance(double tol) {
    this.massBalanceTolerance = tol;
  }

  /**
   * Set enthalpy balance convergence tolerance.
   *
   * @param tol the tolerance
   */
  public void setEnthalpyBalanceTolerance(double tol) {
    this.enthalpyBalanceTolerance = tol;
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
      massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
      massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
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
    double[] massInput = new double[numberOfTrays];
    double[] massOutput = new double[numberOfTrays];
    double[] massBalance = new double[numberOfTrays];

    for (int i = 0; i < numberOfTrays; i++) {
      int numberOfInputStreams = trays.get(i).getNumberOfInputStreams();
      for (int j = 0; j < numberOfInputStreams; j++) {
        massInput[i] += trays.get(i).getStream(j).getFluid().getFlowRate("kg/hr");
      }
      massOutput[i] += trays.get(i).getGasOutStream().getFlowRate("kg/hr");
      massOutput[i] += trays.get(i).getLiquidOutStream().getFlowRate("kg/hr");
      massBalance[i] = massInput[i] - massOutput[i];
    }
    double trayRelativeError = 0.0;
    double totalInlet = 0.0;
    double totalResidual = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      double inlet = Math.abs(massInput[i]);
      double imbalance = Math.abs(massBalance[i]);
      if (inlet > 1e-12) {
        trayRelativeError = Math.max(trayRelativeError, imbalance / inlet);
      }
      totalInlet += inlet;
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
    }
    double trayRelativeError = 0.0;
    double totalInlet = 0.0;
    double totalResidual = 0.0;
    for (int i = 0; i < numberOfTrays; i++) {
      double inlet = Math.abs(energyInput[i]);
      double imbalance = Math.abs(energyBalance[i]);
      if (inlet > 1e-12) {
        trayRelativeError = Math.max(trayRelativeError, imbalance / inlet);
      }
      totalInlet += inlet;
      totalResidual += imbalance;
    }
    double columnRelative = totalInlet > 1e-12 ? totalResidual / totalInlet : totalResidual;
    return Math.max(trayRelativeError, columnRelative);
  }

  /**
   * Blend the current stream update with the previous iterate using the provided relaxation factor.
   *
   * @param previous stream from the previous iteration (may be {@code null})
   * @param current current iteration stream
   * @param relaxation relaxation factor applied to the update
   * @return relaxed stream instance to be used in the next tear
   */
  protected StreamInterface applyRelaxation(StreamInterface previous, StreamInterface current,
      double relaxation) {
    StreamInterface relaxed = current.clone();
    if (previous == null) {
      relaxed.run();
      return relaxed;
    }

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

    relaxed.run();

    return relaxed;
  }

  /**
   * Ensure a double array has the required size, creating a new one if needed.
   *
   * @param array existing array or null
   * @param size required array size
   * @return array with the required size
   */
  protected double[] ensureDoubleArray(double[] array, int size) {
    if (array == null || array.length != size) {
      return new double[size];
    }
    return array;
  }

  /**
   * Ensure a stream array has the required size, creating a new one if needed.
   *
   * @param array existing array or null
   * @param size required array size
   * @return array with the required size
   */
  protected StreamInterface[] ensureStreamArray(StreamInterface[] array, int size) {
    if (array == null || array.length != size) {
      return new StreamInterface[size];
    }
    return array;
  }

  /**
   * Clear references within the provided stream array.
   *
   * @param array stream array to clear
   */
  protected void clearStreamArray(StreamInterface[] array) {
    if (array == null) {
      return;
    }
    for (int i = 0; i < array.length; i++) {
      array[i] = null;
    }
  }

  /**
   * Fill the provided double array with the specified value.
   *
   * @param array array to fill
   * @param value value to fill with
   */
  protected void fillArray(double[] array, double value) {
    if (array == null) {
      return;
    }
    for (int i = 0; i < array.length; i++) {
      array[i] = value;
    }
  }

  /** Invalidate cached solver workspaces whenever topology or solver changes. */
  protected void invalidateSolverWorkspace() {
    sequentialOldTemperatures = null;
    sequentialPrevGasStreams = null;
    sequentialPrevLiquidStreams = null;
    sequentialCurrentGasStreams = null;
    sequentialCurrentLiquidStreams = null;
    broydenOldTemperatures = null;
    broydenPrevResidual = null;
    broydenResidualDiff = null;
    broydenResidual = null;
    insideOutPrevUpGas = null;
    insideOutPrevDownLiquid = null;
    insideOutCurrentUpGas = null;
    insideOutCurrentDownLiquid = null;
  }

  /** Convergence tracking helper used to detect stalled iterations. */
  private static final class ConvergenceTracker {
    double bestResidual = Double.POSITIVE_INFINITY;
    int stagnantIterations = 0;
  }

  /** Reset abort state before starting a new solve. */
  private void resetAbortState() {
    abortedDueToSlowConvergence = false;
    lastAbortReason = null;
  }

  /**
   * Record that the current solve was aborted together with an explanatory message.
   *
   * @param reason textual reason for aborting
   */
  private void markAbort(String reason) {
    if (!abortedDueToSlowConvergence) {
      abortedDueToSlowConvergence = true;
      lastAbortReason = reason;
      logger.warn("Distillation column '{}' aborted solve: {}", getName(), reason);
    }
  }

  /**
   * Evaluate convergence progress and decide whether to abort the iteration loop.
   *
   * @param tracker convergence tracker for this run
   * @param iteration current iteration number (1-based)
   * @param combinedResidual normalised combined residual metric
   * @param startTime timestamp when the solver started (nanoseconds)
   * @param solverLabel human readable solver name for logging
   * @return {@code true} if the solve should abort, otherwise {@code false}
   */
  private boolean shouldAbortDueToSlowConvergence(ConvergenceTracker tracker, int iteration,
      double combinedResidual, long startTime, String solverLabel) {
    if (abortedDueToSlowConvergence) {
      return true;
    }

    if (!Double.isFinite(combinedResidual)) {
      markAbort(String.format(Locale.ROOT, "%s solver residual became non-finite at iteration %d",
          solverLabel, iteration));
      return true;
    }

    if (maxSolveSeconds > 0.0) {
      double elapsedSeconds = (System.nanoTime() - startTime) / 1.0e9;
      if (elapsedSeconds > maxSolveSeconds) {
        markAbort(String.format(Locale.ROOT,
            "%s solver exceeded maximum wall time of %.3f s after %d iterations", solverLabel,
            maxSolveSeconds, iteration));
        return true;
      }
    }

    if (maxStagnantIterations <= 0) {
      tracker.bestResidual = Math.min(tracker.bestResidual, combinedResidual);
      return abortedDueToSlowConvergence;
    }

    if (tracker.bestResidual == Double.POSITIVE_INFINITY) {
      tracker.bestResidual = combinedResidual;
      tracker.stagnantIterations = 0;
      return false;
    }

    double bestResidual = tracker.bestResidual;
    double epsilon = 1.0e-12;
    boolean improved = false;

    if (combinedResidual < bestResidual) {
      double absImprovement = bestResidual - combinedResidual;
      double relImprovement = absImprovement / Math.max(Math.abs(bestResidual), epsilon);
      if (absImprovement > minAbsoluteResidualImprovement
          || relImprovement > minResidualImprovement) {
        tracker.bestResidual = combinedResidual;
        tracker.stagnantIterations = 0;
        improved = true;
      } else {
        tracker.bestResidual = combinedResidual;
      }
    }

    if (!improved) {
      tracker.stagnantIterations++;
      if (tracker.stagnantIterations >= maxStagnantIterations) {
        markAbort(String.format(Locale.ROOT,
            "%s solver stalled after %d iterations without sufficient improvement (residual %.3g)",
            solverLabel, tracker.stagnantIterations, combinedResidual));
        return true;
      }
    }

    return abortedDueToSlowConvergence;
  }

  /** Reset cached solve metrics when no calculation is performed. */
  protected void resetLastSolveMetrics() {
    lastIterationCount = 0;
    lastTemperatureResidual = 0.0;
    lastMassResidual = 0.0;
    lastEnergyResidual = 0.0;
    lastSolveTimeSeconds = 0.0;
    resetAbortState();
  }

  /**
   * Update cached solve metrics after a successful calculation.
   *
   * @param iterationCount number of iterations performed
   * @param temperatureResidual average temperature residual
   * @param massResidual relative mass balance residual
   * @param energyResidual relative enthalpy residual
   * @param solveTimeSeconds solve time in seconds
   */
  protected void updateSolveMetrics(int iterationCount, double temperatureResidual,
      double massResidual, double energyResidual, double solveTimeSeconds) {
    this.lastIterationCount = iterationCount;
    this.lastTemperatureResidual = temperatureResidual;
    this.lastMassResidual = massResidual;
    this.lastEnergyResidual = energyResidual;
    this.lastSolveTimeSeconds = solveTimeSeconds;
  }

  /**
   * Record the current convergence error used by {@link #solved()}.
   *
   * @param value error value to set
   */
  protected void setCurrentError(double value) {
    this.err = value;
  }

  /**
   * Retrieve the latest convergence error measured for the column solution.
   *
   * @return current error metric
   */
  protected double getCurrentError() {
    return err;
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
  public String toJson() {
    return new GsonBuilder().create().toJson(new DistillationColumnResponse(this));
  }

  /** {@inheritDoc} */
  @Override
  public String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    DistillationColumnResponse res = new DistillationColumnResponse(this);
    res.applyConfig(cfg);
    return new GsonBuilder().create().toJson(res);
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
}
