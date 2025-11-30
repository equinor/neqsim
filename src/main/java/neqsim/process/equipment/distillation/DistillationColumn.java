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
  private static final double TRAY_ITERATION_FACTOR = 2.0;
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
  double condenserCoolingDuty = 10.0;
  private double reboilerTemperature = 273.15;
  private double condenserTemperature = 270.15;
  double topTrayPressure = -1.0;

  /** Temperature convergence tolerance. */
  private double temperatureTolerance = 5.0e-3;
  /** Mass balance convergence tolerance. */
  private double massBalanceTolerance = 2.0e-2;
  /** Enthalpy balance convergence tolerance. */
  private double enthalpyBalanceTolerance = 2.0e-2;

  /** Available solving strategies for the column. */
  public enum SolverType {
    /** Classic sequential substitution without damping. */
    DIRECT_SUBSTITUTION,
    /** Sequential substitution with temperature damping. */
    DAMPED_SUBSTITUTION,
    /** Inside-out style simultaneous correction of upward/downward flows. */
    INSIDE_OUT
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

  Mixer feedmixer = new Mixer("temp mixer");
  double bottomTrayPressure = -1.0;
  int numberOfTrays = 1;
  int maxNumberOfIterations = 8;
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
    switch (solverType) {
      case DAMPED_SUBSTITUTION:
        solveSequential(id, relaxationFactor);
        break;
      case INSIDE_OUT:
        solveInsideOut(id);
        break;
      case DIRECT_SUBSTITUTION:
      default:
        solveSequential(id, 1.0);
        break;
    }
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
      if (reboilerHasSetTemp)
        reboilerTemp = getReboiler().getOutTemperature();
    }

    if (hasCondenser && getCondenser() != null) {
      condenserReflux = getCondenser().getRefluxRatio();
      condenserHasSetTemp = getCondenser().isSetOutTemperature();
      if (condenserHasSetTemp)
        condenserTemp = getCondenser().getOutTemperature();
    }

    // Collect all feeds (assigned and unassigned)
    List<StreamInterface> allFeeds = new ArrayList<>(unassignedFeedStreams);
    for (List<StreamInterface> feeds : feedStreams.values()) {
      allFeeds.addAll(feeds);
    }

    // Start searching from a low number of trays to find the minimum (optimal)
    int startN = 2;
    if (hasReboiler)
      startN++;
    if (hasCondenser)
      startN++;

    // Ensure we don't exceed maxTrays immediately
    if (startN > maxTrays)
      startN = maxTrays;

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

      if (hasReboiler) {
        Reboiler reb = new Reboiler("Reboiler");
        reb.setRefluxRatio(reboilerReflux);
        if (reboilerHasSetTemp)
          reb.setOutTemperature(reboilerTemp);
        trays.add(reb);
        trayCount++;
      }

      // Middle trays
      int simpleTrays = n - (hasReboiler ? 1 : 0) - (hasCondenser ? 1 : 0);
      for (int i = 0; i < simpleTrays; i++) {
        trays.add(new SimpleTray("SimpleTray" + (i + 1)));
        trayCount++;
      }

      if (hasCondenser) {
        Condenser cond = new Condenser("Condenser");
        cond.setRefluxRatio(condenserReflux);
        if (condenserHasSetTemp)
          cond.setOutTemperature(condenserTemp);
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

    trays.get(firstFeedTrayNumber).run(id);

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int polishIterationLimit = baseIterationLimit
        + Math.max(POLISH_ITERATION_MARGIN, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowIncrement = Math.max(3, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowBand = Math.max(overflowIncrement, numberOfTrays);
    int maxIterationLimit = Math.max(iterationLimit, maxNumberOfIterations)
        + overflowBand * ITERATION_OVERFLOW_MULTIPLIER;
    double baseTempTolerance = temperatureTolerance;
    double baseMassTolerance = massBalanceTolerance;
    double baseEnergyTolerance = enthalpyBalanceTolerance;
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

      double tempScaled = err / temperatureTolerance;
      double massScaled = massErr / massBalanceTolerance;
      double energyScaled = energyErr / enthalpyBalanceTolerance;
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
   * Solve the column using an inside-out strategy with adaptive stream relaxation.
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

    double relaxation = Math.max(minInsideOutRelaxation, Math.min(maxAdaptiveRelaxation, 0.8));

    trays.get(firstFeedTrayNumber).run(id);

    int baseIterationLimit = computeIterationLimit();
    int iterationLimit = baseIterationLimit;
    int polishIterationLimit = baseIterationLimit
        + Math.max(POLISH_ITERATION_MARGIN, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowIncrement = Math.max(3, (int) Math.ceil(0.5 * numberOfTrays));
    int overflowBand = Math.max(overflowIncrement, numberOfTrays);
    int maxIterationLimit = Math.max(iterationLimit, maxNumberOfIterations)
        + overflowBand * ITERATION_OVERFLOW_MULTIPLIER;
    double baseTempTolerance = temperatureTolerance;
    double baseMassTolerance = massBalanceTolerance;
    double baseEnergyTolerance = enthalpyBalanceTolerance;
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

      for (int stage = firstFeedTrayNumber; stage >= 1; stage--) {
        int target = stage - 1;
        int replaceStream = trays.get(target).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[stage],
            trays.get(stage).getLiquidOutStream(), relaxation);
        trays.get(target).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[stage] = relaxedLiquid.clone();
        trays.get(target).run(id);
      }

      for (int stage = 1; stage <= numberOfTrays - 1; stage++) {
        int replaceStream = trays.get(stage).getNumberOfInputStreams() - 2;
        if (stage == (numberOfTrays - 1)) {
          replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
        }
        StreamInterface relaxedGas = applyRelaxation(previousGasStreams[stage - 1],
            trays.get(stage - 1).getGasOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedGas);
        currentGasStreams[stage - 1] = relaxedGas.clone();
        trays.get(stage).run(id);
      }

      for (int stage = numberOfTrays - 2; stage >= firstFeedTrayNumber; stage--) {
        int replaceStream = trays.get(stage).getNumberOfInputStreams() - 1;
        StreamInterface relaxedLiquid = applyRelaxation(previousLiquidStreams[stage + 1],
            trays.get(stage + 1).getLiquidOutStream(), relaxation);
        trays.get(stage).replaceStream(replaceStream, relaxedLiquid);
        currentLiquidStreams[stage + 1] = relaxedLiquid.clone();
        trays.get(stage).run(id);
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

      double tempScaled = err / temperatureTolerance;
      double massScaled = massErr / massBalanceTolerance;
      double energyScaled = energyErr / enthalpyBalanceTolerance;
      double combinedResidual = Math.max(tempScaled, massScaled);
      if (Double.isFinite(energyScaled)) {
        combinedResidual =
            Math.max(combinedResidual, Math.min(energyScaled, maxEnergyRelaxationWeight));
      }

      if (combinedResidual > previousCombinedResidual * 1.05) {
        relaxation = Math.max(minInsideOutRelaxation, relaxation * relaxationDecreaseFactor);
      } else if (combinedResidual < previousCombinedResidual * 0.98) {
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

      logger.info("inside-out iteration {} relaxation={} tempErr={} massErr={} energyErr={}", iter,
          relaxation, err, massErr, energyErr);

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

    finalizeSolve(id, iter, err, massErr, energyErr, startTime);
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
    double baseTempTolerance = temperatureTolerance;
    double baseMassTolerance = massBalanceTolerance;
    double baseEnergyTolerance = enthalpyBalanceTolerance;
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

  public ArrayList<SimpleTray> getTrays() {
    return trays;
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
    setDoInitializion(true);
    init();
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
    return (err < temperatureTolerance);
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
   * Blend the current stream update with the previous iterate using the provided relaxation factor.
   *
   * @param previous stream from the previous iteration (may be {@code null})
   * @param current current iteration stream
   * @param relaxation relaxation factor applied to the update
   * @return relaxed stream instance to be used in the next tear
   */
  private StreamInterface applyRelaxation(StreamInterface previous, StreamInterface current,
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
