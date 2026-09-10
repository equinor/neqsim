package neqsim.process.util.optimizer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;

/**
 * Generates diagnostic process inlet-pressure tables over rate and composition scenarios.
 *
 * <p>
 * For each combination of (flow rate, outlet pressure, water cut, GOR), calculates the required inlet pressure by
 * running the process simulation. These are screening results, not a qualified well model or reservoir deck. Rate
 * inputs retain their configured simulation units; no standard oil, liquid or gas basis is inferred.
 * </p>
 *
 * <h2>Table Format</h2>
 *
 * <pre>
 * inletPressure[rate][outletP][WC][GOR] = required process inlet pressure (bara)
 *
 * Where:
 *   - inletPressure = process inlet pressure required to achieve the given rate
 *   - outletP = process outlet pressure constraint
 *   - rate = target flow rate (Sm3/d or kg/hr)
 *   - WC = water cut (fraction 0-1)
 *   - GOR = gas-oil ratio (Sm3/Sm3)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The generator uses a process factory to create fresh ProcessSystem instances for parallel execution, ensuring thread
 * safety. This pattern follows the established approach in eclipse_lift_curve_manifold_pressure_paralell_run.ipynb.
 * </p>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // Setup fluid input and generator
 * FluidMagicInput input = FluidMagicInput.fromE300File("FLUID.E300");
 * input.setGORRange(250, 10000);
 * input.setWaterCutRange(0.05, 0.60);
 * input.separateToStandardConditions();
 *
 * RecombinationFlashGenerator flashGen = new RecombinationFlashGenerator(input);
 *
 * // Create VFP generator with process factory
 * MultiScenarioVFPGenerator vfpGen = new MultiScenarioVFPGenerator(() -&gt; createMyProcess(), // Factory
 *     // for
 *     // thread-safe
 *     // execution
 *     "Feed", "Export");
 * vfpGen.setFlashGenerator(flashGen);
 *
 * // Configure table axes
 * vfpGen.setFlowRates(new double[] { 5000, 10000, 20000, 40000, 60000 });
 * vfpGen.setOutletPressures(new double[] { 50, 60, 70, 80 });
 * vfpGen.setWaterCuts(new double[] { 0.05, 0.20, 0.40, 0.60 });
 * vfpGen.setGORs(new double[] { 250, 500, 1000, 2000, 5000, 10000 });
 *
 * // Generate table
 * VFPTable table = vfpGen.generateVFPTable();
 *
 * // Inspect diagnostic results; these are not reservoir simulator input
 * String diagnostic = vfpGen.toDiagnosticString();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 * @see FluidMagicInput
 * @see RecombinationFlashGenerator
 */
public class MultiScenarioVFPGenerator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(MultiScenarioVFPGenerator.class);

  // Process configuration
  private transient Supplier<ProcessSystem> processFactory;
  private String feedStreamName;
  private String outletStreamName;

  // Fluid generation
  private RecombinationFlashGenerator flashGenerator;
  private double inletTemperature = 353.15; // 80°C default

  // Table axes
  private double[] flowRates; // Sm3/d or kg/hr
  private double[] outletPressures; // process outlet pressure in bara
  private double[] waterCuts; // fraction 0-1
  private double[] GORs; // Sm3/Sm3

  // Flow rate settings
  private String flowRateUnit = "Sm3/day";

  // Binary search settings for inlet pressure
  private double minInletPressure = 10.0; // bara
  private double maxInletPressure = 200.0; // bara
  private double pressureTolerance = 0.5; // bara

  // Parallel execution
  private boolean enableParallel = true;
  private int numberOfWorkers = Runtime.getRuntime().availableProcessors();

  // Results
  private VFPTable vfpTable;

  /**
   * Constructor with process factory for thread-safe parallel execution.
   *
   * @param processFactory factory function that creates a fresh process system
   * @param feedStreamName name of inlet stream
   * @param outletStreamName name of outlet stream (pressure target)
   */
  public MultiScenarioVFPGenerator(Supplier<ProcessSystem> processFactory, String feedStreamName,
      String outletStreamName) {
    this.processFactory = processFactory;
    this.feedStreamName = feedStreamName;
    this.outletStreamName = outletStreamName;
  }

  /**
   * Constructor with single process (for sequential execution or when process is thread-safe).
   *
   * @param process the process system (will be copied for parallel execution)
   * @param feedStreamName name of inlet stream
   * @param outletStreamName name of outlet stream
   */
  public MultiScenarioVFPGenerator(ProcessSystem process, String feedStreamName, String outletStreamName) {
    this.processFactory = () -> process.copy();
    this.feedStreamName = feedStreamName;
    this.outletStreamName = outletStreamName;
  }

  /**
   * Generate the VFP table by simulating all combinations.
   *
   * <p>
   * For each (rate, outletP, WC, GOR) combination, finds the minimum inlet pressure required to achieve the target rate
   * at the specified outlet pressure.
   * </p>
   *
   * @return VFPTable with all results
   */
  public VFPTable generateVFPTable() {
    validateConfiguration();

    int nRates = flowRates.length;
    int nTHP = outletPressures.length;
    int nWC = waterCuts.length;
    int nGOR = GORs.length;
    int totalPoints = nRates * nTHP * nWC * nGOR;

    logger.info("Generating process screening table: {} rates × {} outlet pressures × {} WC × {} GOR = {} points",
        nRates, nTHP, nWC, nGOR, totalPoints);

    vfpTable = new VFPTable(flowRates, outletPressures, waterCuts, GORs);
    vfpTable.setFlowRateUnit(flowRateUnit);

    // Build task list
    List<VFPTask> tasks = new ArrayList<>();
    for (int g = 0; g < nGOR; g++) {
      for (int w = 0; w < nWC; w++) {
        for (int t = 0; t < nTHP; t++) {
          for (int r = 0; r < nRates; r++) {
            tasks.add(new VFPTask(flowRates[r], outletPressures[t], waterCuts[w], GORs[g], r, t, w, g));
          }
        }
      }
    }

    long startTime = System.currentTimeMillis();

    if (enableParallel) {
      executeParallel(tasks);
    } else {
      executeSequential(tasks);
    }

    long elapsed = System.currentTimeMillis() - startTime;
    int feasibleCount = vfpTable.getFeasibleCount();

    logger.info("VFP table complete: {}/{} feasible in {}s ({}s/point)", feasibleCount, totalPoints, elapsed / 1000.0,
        elapsed / 1000.0 / totalPoints);

    if (flashGenerator != null) {
      logger.info("Fluid cache: {}", flashGenerator.getCacheStatistics());
    }

    return vfpTable;
  }

  /**
   * Execute tasks sequentially.
   *
   * @param tasks list of VFP tasks
   */
  private void executeSequential(List<VFPTask> tasks) {
    int completed = 0;
    for (VFPTask task : tasks) {
      VFPPoint point = calculateVFPPoint(task);
      vfpTable.setPoint(point);
      completed++;

      if (completed % 10 == 0 || completed == tasks.size()) {
        logger.info("Progress: {}/{}", completed, tasks.size());
      }
    }
  }

  /**
   * Execute tasks in parallel using ThreadPoolExecutor.
   *
   * @param tasks list of VFP tasks
   */
  private void executeParallel(List<VFPTask> tasks) {
    ExecutorService executor = Executors.newFixedThreadPool(numberOfWorkers);
    List<Future<VFPPoint>> futures = new ArrayList<>();

    for (VFPTask task : tasks) {
      futures.add(executor.submit(() -> calculateVFPPoint(task)));
    }

    int completed = 0;
    for (Future<VFPPoint> future : futures) {
      try {
        VFPPoint point = future.get();
        vfpTable.setPoint(point);
        completed++;

        if (completed % 20 == 0 || completed == tasks.size()) {
          String status = point.feasible ? String.format("P_in=%.1f bara", point.bhp) : "INFEASIBLE";
          logger.info("[{}/{}] Rate={}, outlet pressure={}, WC={}%, GOR={} → {}", completed, tasks.size(),
              point.flowRate, point.thp, point.waterCut * 100, point.gor, status);
        }
      } catch (Exception e) {
        logger.error("Task execution failed", e);
        completed++;
      }
    }

    executor.shutdown();
    try {
      executor.awaitTermination(1, TimeUnit.HOURS);
    } catch (InterruptedException e) {
      logger.warn("Executor interrupted", e);
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Calculate single VFP point with fresh process system (thread-safe).
   *
   * <p>
   * Uses binary search to find minimum inlet pressure that achieves target outlet pressure at the specified flow rate.
   * </p>
   *
   * @param task the VFP task to calculate
   * @return VFPPoint with results
   */
  private VFPPoint calculateVFPPoint(VFPTask task) {
    VFPPoint result = new VFPPoint(task);

    try {
      // Create FRESH process system for thread safety
      ProcessSystem process = processFactory.get();

      // Generate fluid for this GOR and WC
      SystemInterface fluid = flashGenerator.generateFluid(task.gor, task.waterCut, task.flowRate, inletTemperature,
          maxInletPressure);

      // Get feed stream
      StreamInterface feedStream = (StreamInterface) process.getUnit(feedStreamName);
      if (feedStream == null) {
        throw new IllegalStateException("Feed stream not found: " + feedStreamName);
      }

      // Binary search for minimum inlet pressure
      double pLow = minInletPressure;
      double pHigh = maxInletPressure;

      // First check if achievable at pHigh
      if (!tryInletPressure(process, feedStream, fluid, task.flowRate, task.thp, pHigh)) {
        result.feasible = false;
        return result;
      }

      // Binary search for minimum P
      double bestP = pHigh;
      while ((pHigh - pLow) > pressureTolerance) {
        double pTry = (pLow + pHigh) / 2.0;

        if (tryInletPressure(process, feedStream, fluid, task.flowRate, task.thp, pTry)) {
          bestP = pTry;
          pHigh = pTry; // Can we go lower?
        } else {
          pLow = pTry; // Need higher pressure
        }
      }

      result.bhp = bestP;
      result.feasible = true;

    } catch (Exception e) {
      logger.debug("VFP point failed: GOR={}, WC={}, Rate={}: {}", task.gor, task.waterCut, task.flowRate,
          e.getMessage());
      result.feasible = false;
    }

    return result;
  }

  /**
   * Try running process at given inlet pressure, check if outlet target achieved.
   *
   * @param process the process system
   * @param feedStream the feed stream to modify
   * @param fluid the fluid composition
   * @param flowRate target flow rate
   * @param targetOutletP target outlet pressure
   * @param inletP inlet pressure to try
   * @return true if target achieved
   */
  private boolean tryInletPressure(ProcessSystem process, StreamInterface feedStream, SystemInterface fluid,
      double flowRate, double targetOutletP, double inletP) {
    try {
      // Set feed stream conditions
      feedStream.setFluid(fluid.clone());
      feedStream.setPressure(inletP, "bara");
      feedStream.setTemperature(inletTemperature, "K");
      feedStream.setFlowRate(flowRate, flowRateUnit);

      // Run process
      process.run();

      // Check outlet pressure
      StreamInterface outletStream = (StreamInterface) process.getUnit(outletStreamName);
      if (outletStream == null) {
        return false;
      }

      double actualOutletP = outletStream.getPressure("bara");
      return actualOutletP >= targetOutletP;

    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Validate configuration before running.
   */
  private void validateConfiguration() {
    if (processFactory == null) {
      throw new IllegalStateException("Process factory not set");
    }
    if (flashGenerator == null) {
      throw new IllegalStateException("Flash generator not set");
    }
    if (flowRates == null || flowRates.length == 0) {
      throw new IllegalStateException("Flow rates not set");
    }
    if (outletPressures == null || outletPressures.length == 0) {
      throw new IllegalStateException("Outlet pressures not set");
    }
    if (waterCuts == null || waterCuts.length == 0) {
      throw new IllegalStateException("Water cuts not set");
    }
    if (GORs == null || GORs.length == 0) {
      throw new IllegalStateException("GORs not set");
    }
  }

  /**
   * Writes diagnostic process results through the legacy file-export entry point.
   *
   * @param filePath output file path
   * @param tableNumber unused legacy identifier
   * @throws IOException if writing fails
   * @deprecated writes diagnostic text, not a reservoir deck; use {@link #toDiagnosticString()}
   */
  @Deprecated
  public void exportVFPEXP(String filePath, int tableNumber) throws IOException {
    exportVFPEXP(Paths.get(filePath), tableNumber);
  }

  /**
   * Writes diagnostic process results through the legacy file-export entry point.
   *
   * @param filePath output file path
   * @param tableNumber unused legacy identifier
   * @throws IOException if writing fails
   * @deprecated writes diagnostic text, not a reservoir deck; use {@link #toDiagnosticString()}
   */
  @Deprecated
  public void exportVFPEXP(Path filePath, int tableNumber) throws IOException {
    if (vfpTable == null) {
      throw new IllegalStateException("VFP table not generated. Call generateVFPTable() first.");
    }

    String content = toDiagnosticString();
    try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
      writer.write(content);
    }
    logger.info("Process screening diagnostics written to: {}", filePath);
  }

  /**
   * Returns diagnostic process results through the legacy format entry point.
   *
   * @param tableNumber unused legacy identifier
   * @return diagnostic text, never a reservoir deck keyword
   * @throws IllegalStateException if no table has been generated
   * @deprecated use {@link #toDiagnosticString()}; well pressure qualification is separate
   */
  @Deprecated
  public String toVFPEXPString(int tableNumber) {
    return toDiagnosticString();
  }

  /**
   * Lists every generated process point with its original axes, units and feasibility.
   *
   * <p>
   * The generated table supplies the axes and rate units, so later changes to generator settings cannot relabel
   * existing results. Unavailable or infeasible inlet pressures remain NaN, with an explicit false feasibility flag. No
   * missing pressure is filled or extrapolated.
   * </p>
   *
   * @return tab-separated process screening diagnostics, not reservoir simulator input
   * @throws IllegalStateException if no table has been generated
   */
  public String toDiagnosticString() {
    if (vfpTable == null) {
      throw new IllegalStateException("VFP table not generated");
    }
    double[] rates = vfpTable.getFlowRates();
    double[] outlets = vfpTable.getOutletPressures();
    double[] water = vfpTable.getWaterCuts();
    double[] gas = vfpTable.getGORs();
    StringBuilder out = new StringBuilder();
    out.append("# Process inlet-pressure screening; diagnostic text only\n");
    out.append("# Rates retain their simulation input basis; no well model is qualified.\n");
    out.append("rate [").append(vfpTable.getFlowRateUnit()).append("]\t");
    out.append("outlet pressure [bara]\twater cut [-]\tGOR [Sm3/Sm3]\t");
    out.append("required inlet pressure [bara]\tfeasible\n");
    for (int r = 0; r < rates.length; r++) {
      for (int p = 0; p < outlets.length; p++) {
        for (int w = 0; w < water.length; w++) {
          for (int g = 0; g < gas.length; g++) {
            double pressure = vfpTable.getBHP(r, p, w, g);
            boolean feasible = vfpTable.isFeasible(r, p, w, g) && Double.isFinite(pressure) && pressure > 0.0;
            out.append(rates[r]).append('\t').append(outlets[p]).append('\t');
            out.append(water[w]).append('\t').append(gas[g]).append('\t');
            out.append(feasible ? pressure : Double.NaN).append('\t').append(feasible).append('\n');
          }
        }
      }
    }
    return out.toString();
  }

  // ==================== Getters and Setters ====================

  /**
   * Set fluid input configuration (convenience method).
   *
   * <p>
   * This is a convenience method that creates a {@link RecombinationFlashGenerator} from the fluid input and
   * automatically configures the water cut and GOR arrays from the fluid input settings.
   * </p>
   *
   * @param fluidInput the fluid input configuration
   */
  public void setFluidInput(FluidMagicInput fluidInput) {
    this.flashGenerator = new RecombinationFlashGenerator(fluidInput);
    this.waterCuts = fluidInput.generateWaterCutValues();
    this.GORs = fluidInput.generateGORValues();
  }

  /**
   * Set the recombination flash generator.
   *
   * @param flashGenerator the flash generator
   */
  public void setFlashGenerator(RecombinationFlashGenerator flashGenerator) {
    this.flashGenerator = flashGenerator;
  }

  /**
   * Get the flash generator.
   *
   * @return the flash generator
   */
  public RecombinationFlashGenerator getFlashGenerator() {
    return flashGenerator;
  }

  /**
   * Set flow rates for VFP table.
   *
   * @param flowRates array of flow rates
   */
  public void setFlowRates(double[] flowRates) {
    this.flowRates = flowRates.clone();
  }

  /**
   * Get flow rates.
   *
   * @return array of flow rates
   */
  public double[] getFlowRates() {
    return flowRates;
  }

  /**
   * Sets process outlet pressures for the diagnostic table.
   *
   * @param outletPressures array of outlet pressures in bara
   */
  public void setOutletPressures(double[] outletPressures) {
    this.outletPressures = outletPressures.clone();
  }

  /**
   * Get outlet pressures.
   *
   * @return array of outlet pressures
   */
  public double[] getOutletPressures() {
    return outletPressures;
  }

  /**
   * Set water cuts for VFP table.
   *
   * @param waterCuts array of water cuts (0-1)
   */
  public void setWaterCuts(double[] waterCuts) {
    this.waterCuts = waterCuts.clone();
  }

  /**
   * Get water cuts.
   *
   * @return array of water cuts
   */
  public double[] getWaterCuts() {
    return waterCuts;
  }

  /**
   * Set GOR values for VFP table.
   *
   * @param gors array of GOR values in Sm3/Sm3
   */
  public void setGORs(double[] gors) {
    this.GORs = gors.clone();
  }

  /**
   * Get GOR values.
   *
   * @return array of GOR values
   */
  public double[] getGORs() {
    return GORs;
  }

  /**
   * Set flow rate unit.
   *
   * @param unit flow rate unit (e.g., "Sm3/day", "kg/hr")
   */
  public void setFlowRateUnit(String unit) {
    this.flowRateUnit = unit;
  }

  /**
   * Get flow rate unit.
   *
   * @return flow rate unit
   */
  public String getFlowRateUnit() {
    return flowRateUnit;
  }

  /**
   * Set inlet temperature for fluid generation.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setInletTemperature(double temperatureK) {
    this.inletTemperature = temperatureK;
  }

  /**
   * Get inlet temperature.
   *
   * @return temperature in Kelvin
   */
  public double getInletTemperature() {
    return inletTemperature;
  }

  /**
   * Set minimum inlet pressure for binary search.
   *
   * @param pressure minimum pressure in bara
   */
  public void setMinInletPressure(double pressure) {
    this.minInletPressure = pressure;
  }

  /**
   * Set maximum inlet pressure for binary search.
   *
   * @param pressure maximum pressure in bara
   */
  public void setMaxInletPressure(double pressure) {
    this.maxInletPressure = pressure;
  }

  /**
   * Set pressure tolerance for binary search.
   *
   * @param tolerance tolerance in bara
   */
  public void setPressureTolerance(double tolerance) {
    this.pressureTolerance = tolerance;
  }

  /**
   * Enable or disable parallel execution.
   *
   * @param enable true to enable parallel execution
   */
  public void setEnableParallel(boolean enable) {
    this.enableParallel = enable;
  }

  /**
   * Set number of worker threads.
   *
   * @param workers number of workers
   */
  public void setNumberOfWorkers(int workers) {
    this.numberOfWorkers = workers;
  }

  /**
   * Get the generated VFP table.
   *
   * @return VFP table or null if not yet generated
   */
  public VFPTable getVfpTable() {
    return vfpTable;
  }

  // ==================== Inner Classes ====================

  /**
   * Task for calculating a single VFP point.
   */
  private static class VFPTask implements Serializable {
    private static final long serialVersionUID = 1L;

    double flowRate;
    double thp;
    double waterCut;
    double gor;
    int rIdx;
    int tIdx;
    int wIdx;
    int gIdx;

    VFPTask(double flowRate, double thp, double waterCut, double gor, int rIdx, int tIdx, int wIdx, int gIdx) {
      this.flowRate = flowRate;
      this.thp = thp;
      this.waterCut = waterCut;
      this.gor = gor;
      this.rIdx = rIdx;
      this.tIdx = tIdx;
      this.wIdx = wIdx;
      this.gIdx = gIdx;
    }
  }

  /**
   * Result of a single VFP point calculation.
   */
  private static class VFPPoint extends VFPTask {
    private static final long serialVersionUID = 1L;

    double bhp;
    boolean feasible;

    VFPPoint(VFPTask task) {
      super(task.flowRate, task.thp, task.waterCut, task.gor, task.rIdx, task.tIdx, task.wIdx, task.gIdx);
    }
  }

  /**
   * Diagnostic process inlet-pressure table. Legacy method names do not establish well BHP semantics.
   */
  public static class VFPTable implements Serializable {
    private static final long serialVersionUID = 1L;

    private double[] flowRates;
    private double[] outletPressures;
    private double[] waterCuts;
    private double[] GORs;
    private double[][][][] bhpTable; // [rate][thp][wc][gor]
    private boolean[][][][] feasible; // [rate][thp][wc][gor]
    private String flowRateUnit = "Sm3/day";

    /**
     * Create VFP table with specified dimensions.
     *
     * @param flowRates flow rate values
     * @param outletPressures outlet pressure values
     * @param waterCuts water cut values
     * @param GORs GOR values
     */
    public VFPTable(double[] flowRates, double[] outletPressures, double[] waterCuts, double[] GORs) {
      this.flowRates = flowRates.clone();
      this.outletPressures = outletPressures.clone();
      this.waterCuts = waterCuts.clone();
      this.GORs = GORs.clone();

      int nR = flowRates.length;
      int nT = outletPressures.length;
      int nW = waterCuts.length;
      int nG = GORs.length;

      this.bhpTable = new double[nR][nT][nW][nG];
      this.feasible = new boolean[nR][nT][nW][nG];

      // Initialize with NaN
      for (int r = 0; r < nR; r++) {
        for (int t = 0; t < nT; t++) {
          for (int w = 0; w < nW; w++) {
            for (int g = 0; g < nG; g++) {
              bhpTable[r][t][w][g] = Double.NaN;
              feasible[r][t][w][g] = false;
            }
          }
        }
      }
    }

    /**
     * Set a point result.
     *
     * @param point the calculated point
     */
    void setPoint(VFPPoint point) {
      bhpTable[point.rIdx][point.tIdx][point.wIdx][point.gIdx] = point.bhp;
      feasible[point.rIdx][point.tIdx][point.wIdx][point.gIdx] = point.feasible;
    }

    /**
     * Gets required process inlet pressures; the legacy name does not imply well bottomhole pressure.
     *
     * @return 4D array [rate][thp][wc][gor]
     */
    public double[][][][] getBHPTable() {
      return bhpTable;
    }

    /**
     * Gets required process inlet pressure at specific indices.
     *
     * @param rateIdx rate index
     * @param thpIdx process outlet-pressure index (legacy parameter name)
     * @param wcIdx water cut index
     * @param gorIdx GOR index
     * @return required process inlet pressure in bara, or NaN if infeasible
     */
    public double getBHP(int rateIdx, int thpIdx, int wcIdx, int gorIdx) {
      return bhpTable[rateIdx][thpIdx][wcIdx][gorIdx];
    }

    /**
     * Check if point is feasible.
     *
     * @param rateIdx rate index
     * @param thpIdx process outlet-pressure index (legacy parameter name)
     * @param wcIdx water cut index
     * @param gorIdx GOR index
     * @return true if feasible
     */
    public boolean isFeasible(int rateIdx, int thpIdx, int wcIdx, int gorIdx) {
      return feasible[rateIdx][thpIdx][wcIdx][gorIdx];
    }

    /**
     * Get count of feasible points.
     *
     * @return number of feasible points
     */
    public int getFeasibleCount() {
      int count = 0;
      for (int r = 0; r < flowRates.length; r++) {
        for (int t = 0; t < outletPressures.length; t++) {
          for (int w = 0; w < waterCuts.length; w++) {
            for (int g = 0; g < GORs.length; g++) {
              if (feasible[r][t][w][g]) {
                count++;
              }
            }
          }
        }
      }
      return count;
    }

    /**
     * Get total number of points.
     *
     * @return total points
     */
    public int getTotalPoints() {
      return flowRates.length * outletPressures.length * waterCuts.length * GORs.length;
    }

    /**
     * Set flow rate unit.
     *
     * @param unit the unit
     */
    public void setFlowRateUnit(String unit) {
      this.flowRateUnit = unit;
    }

    /**
     * Get flow rate unit.
     *
     * @return the unit
     */
    public String getFlowRateUnit() {
      return flowRateUnit;
    }

    /**
     * Get flow rates array.
     *
     * @return flow rates
     */
    public double[] getFlowRates() {
      return flowRates;
    }

    /**
     * Get outlet pressures array.
     *
     * @return outlet pressures
     */
    public double[] getOutletPressures() {
      return outletPressures;
    }

    /**
     * Get water cuts array.
     *
     * @return water cuts
     */
    public double[] getWaterCuts() {
      return waterCuts;
    }

    /**
     * Get GORs array.
     *
     * @return GORs
     */
    public double[] getGORs() {
      return GORs;
    }

    /**
     * Print summary of VFP table at specific WC and GOR.
     *
     * @param wcIdx water cut index
     * @param gorIdx GOR index
     */
    public void printSlice(int wcIdx, int gorIdx) {
      StringBuilder out = new StringBuilder();
      out.append("Process screening slice: water cut=").append(waterCuts[wcIdx]);
      out.append(", GOR=").append(GORs[gorIdx]).append(" Sm3/Sm3\n");
      out.append("Required inlet pressure [bara]; rate [").append(flowRateUnit).append("]\n");
      out.append("rate / outlet pressure [bara]");
      for (double pressure : outletPressures) {
        out.append('\t').append(pressure);
      }
      out.append('\n');
      for (int r = 0; r < flowRates.length; r++) {
        out.append(flowRates[r]);
        for (int p = 0; p < outletPressures.length; p++) {
          double value = bhpTable[r][p][wcIdx][gorIdx];
          out.append('\t').append(feasible[r][p][wcIdx][gorIdx] && Double.isFinite(value) ? value : Double.NaN);
        }
        out.append('\n');
      }
      logger.info("{}", out);
    }
  }
}
