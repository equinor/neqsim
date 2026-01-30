package neqsim.process.safety.risk;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionImpactAnalyzer;
import neqsim.process.util.optimizer.ProductionImpactResult;

/**
 * Monte Carlo simulator for operational risk and production availability analysis.
 *
 * <p>
 * Simulates equipment failures over time to estimate:
 * <ul>
 * <li>Expected production over a time horizon</li>
 * <li>Production availability (uptime percentage)</li>
 * <li>Expected downtime events and durations</li>
 * <li>Production loss distribution (P10/P50/P90)</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>
 * {@code
 * OperationalRiskSimulator simulator = new OperationalRiskSimulator(processSystem);
 * simulator.setFeedStreamName("Well Feed");
 * simulator.setProductStreamName("Export Gas");
 * 
 * // Add equipment with failure rates
 * simulator.addEquipmentReliability("HP Compressor", 0.02, 24.0); // 2% failures/year, 24hr MTTR
 * simulator.addEquipmentReliability("LP Compressor", 0.02, 24.0);
 * 
 * // Run simulation
 * OperationalRiskResult result = simulator.runSimulation(1000, 365.0); // 1000 iterations, 1 year
 * 
 * System.out.println("Expected Availability: " + result.getAvailability() + "%");
 * System.out.println("P50 Production: " + result.getP50Production() + " kg");
 * }
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperationalRiskSimulator implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Logger. */
  private static final Logger logger = LogManager.getLogger(OperationalRiskSimulator.class);

  /** The process system to simulate. */
  private ProcessSystem processSystem;

  /** Equipment reliability data. */
  private Map<String, EquipmentReliability> equipmentReliability;

  /** Production impact analyzer. */
  private transient ProductionImpactAnalyzer impactAnalyzer;

  /** Feed stream name. */
  private String feedStreamName;

  /** Product stream name. */
  private String productStreamName;

  /** Random seed for reproducibility. */
  private long randomSeed;

  /** Hours per year for calculations. */
  private static final double HOURS_PER_YEAR = 8760.0;

  /**
   * Equipment reliability data holder.
   */
  public static class EquipmentReliability implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String equipmentName;
    private double failureRate; // failures per year
    private double mttr; // mean time to repair (hours)
    private double mtbf; // mean time between failures (hours)
    private EquipmentFailureMode defaultFailureMode;

    /**
     * Creates equipment reliability data.
     *
     * @param name equipment name
     * @param failureRate failures per year
     * @param mttr mean time to repair in hours
     */
    public EquipmentReliability(String name, double failureRate, double mttr) {
      this.equipmentName = name;
      this.failureRate = failureRate;
      this.mttr = mttr;
      this.mtbf = failureRate > 0 ? HOURS_PER_YEAR / failureRate : Double.MAX_VALUE;
      this.defaultFailureMode = EquipmentFailureMode.trip(name);
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public double getFailureRate() {
      return failureRate;
    }

    public double getMttr() {
      return mttr;
    }

    public double getMtbf() {
      return mtbf;
    }

    public EquipmentFailureMode getDefaultFailureMode() {
      return defaultFailureMode;
    }

    public void setDefaultFailureMode(EquipmentFailureMode mode) {
      this.defaultFailureMode = mode;
    }

    /**
     * Calculates availability for this equipment.
     *
     * @return availability as fraction (0-1)
     */
    public double getAvailability() {
      return mtbf / (mtbf + mttr);
    }
  }

  /**
   * Creates an operational risk simulator.
   *
   * @param processSystem the process system to simulate
   */
  public OperationalRiskSimulator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.equipmentReliability = new HashMap<String, EquipmentReliability>();
    this.randomSeed = System.currentTimeMillis();
    autoDetectStreams();
  }

  private void autoDetectStreams() {
    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      if (unit instanceof StreamInterface) {
        if (feedStreamName == null) {
          feedStreamName = unit.getName();
        }
        productStreamName = unit.getName();
      }
    }
  }

  // Configuration methods

  /**
   * Sets the feed stream name.
   *
   * @param name the feed stream name
   * @return this simulator for chaining
   */
  public OperationalRiskSimulator setFeedStreamName(String name) {
    this.feedStreamName = name;
    return this;
  }

  /**
   * Sets the product stream name.
   *
   * @param name the product stream name
   * @return this simulator for chaining
   */
  public OperationalRiskSimulator setProductStreamName(String name) {
    this.productStreamName = name;
    return this;
  }

  /**
   * Sets the random seed for reproducibility.
   *
   * @param seed the random seed
   * @return this simulator for chaining
   */
  public OperationalRiskSimulator setRandomSeed(long seed) {
    this.randomSeed = seed;
    return this;
  }

  /**
   * Adds equipment reliability data.
   *
   * @param equipmentName name of the equipment
   * @param failureRate failures per year
   * @param mttr mean time to repair in hours
   * @return this simulator for chaining
   */
  public OperationalRiskSimulator addEquipmentReliability(String equipmentName, double failureRate,
      double mttr) {
    equipmentReliability.put(equipmentName,
        new EquipmentReliability(equipmentName, failureRate, mttr));
    return this;
  }

  /**
   * Adds equipment reliability using OREDA-style data.
   *
   * @param equipmentName equipment name
   * @param mtbf mean time between failures in hours
   * @param mttr mean time to repair in hours
   * @return this simulator for chaining
   */
  public OperationalRiskSimulator addEquipmentMtbf(String equipmentName, double mtbf, double mttr) {
    double failureRate = HOURS_PER_YEAR / mtbf;
    return addEquipmentReliability(equipmentName, failureRate, mttr);
  }

  /**
   * Gets the equipment reliability data.
   *
   * @return map of equipment name to reliability data
   */
  public Map<String, EquipmentReliability> getEquipmentReliability() {
    return new HashMap<String, EquipmentReliability>(equipmentReliability);
  }

  // Simulation methods

  /**
   * Runs the Monte Carlo simulation.
   *
   * @param iterations number of Monte Carlo iterations
   * @param timeHorizonDays simulation time horizon in days
   * @return simulation result
   */
  public OperationalRiskResult runSimulation(int iterations, double timeHorizonDays) {
    Random random = new Random(randomSeed);
    double timeHorizonHours = timeHorizonDays * 24.0;

    // Initialize impact analyzer if needed
    if (impactAnalyzer == null) {
      impactAnalyzer = new ProductionImpactAnalyzer(processSystem);
      impactAnalyzer.setFeedStreamName(feedStreamName);
      impactAnalyzer.setProductStreamName(productStreamName);
    }

    // Calculate baseline production
    processSystem.run();
    double baselineProduction = getBaselineProductionRate();
    double maxPossibleProduction = baselineProduction * timeHorizonHours;

    // Arrays to store results
    double[] totalProductions = new double[iterations];
    double[] availabilities = new double[iterations];
    int[] failureCounts = new int[iterations];
    double[] downtimeHours = new double[iterations];

    // Cache production impact for each equipment
    Map<String, Double> productionWithFailure = new HashMap<String, Double>();
    for (String equipName : equipmentReliability.keySet()) {
      try {
        ProductionImpactResult impact = impactAnalyzer.analyzeFailureImpact(equipName);
        productionWithFailure.put(equipName, impact.getProductionWithFailure());
      } catch (Exception e) {
        logger.warn("Could not analyze failure impact for {}: {}", equipName, e.getMessage());
        productionWithFailure.put(equipName, 0.0);
      }
    }

    // Run Monte Carlo iterations
    for (int iter = 0; iter < iterations; iter++) {
      SimulationState state =
          simulateIteration(random, timeHorizonHours, baselineProduction, productionWithFailure);

      totalProductions[iter] = state.totalProduction;
      availabilities[iter] = state.uptimeHours / timeHorizonHours;
      failureCounts[iter] = state.failureCount;
      downtimeHours[iter] = state.downtimeHours;
    }

    // Build result
    OperationalRiskResult result = new OperationalRiskResult();
    result.setIterations(iterations);
    result.setTimeHorizonDays(timeHorizonDays);
    result.setBaselineProductionRate(baselineProduction);
    result.setMaxPossibleProduction(maxPossibleProduction);

    // Calculate statistics
    result.calculateStatistics(totalProductions, availabilities, failureCounts, downtimeHours);

    // Add equipment-specific results
    for (Map.Entry<String, EquipmentReliability> entry : equipmentReliability.entrySet()) {
      result.addEquipmentAvailability(entry.getKey(), entry.getValue().getAvailability());
    }

    return result;
  }

  private SimulationState simulateIteration(Random random, double timeHorizonHours,
      double baselineProduction, Map<String, Double> productionWithFailure) {

    SimulationState state = new SimulationState();
    double currentTime = 0.0;
    state.totalProduction = 0.0;
    state.uptimeHours = 0.0;
    state.downtimeHours = 0.0;
    state.failureCount = 0;

    // Generate failure events for each equipment
    List<FailureEvent> events = new ArrayList<FailureEvent>();
    for (Map.Entry<String, EquipmentReliability> entry : equipmentReliability.entrySet()) {
      String equipName = entry.getKey();
      EquipmentReliability rel = entry.getValue();

      // Generate failure times using exponential distribution
      double time = 0.0;
      while (time < timeHorizonHours) {
        // Time to next failure (exponential distribution)
        double lambda = rel.getFailureRate() / HOURS_PER_YEAR; // failures per hour
        if (lambda > 0) {
          double timeToFailure = -Math.log(1 - random.nextDouble()) / lambda;
          time += timeToFailure;
          if (time < timeHorizonHours) {
            events.add(new FailureEvent(equipName, time, rel.getMttr()));
          }
        } else {
          break; // No failures
        }
      }
    }

    // Sort events by time
    events.sort((a, b) -> Double.compare(a.startTime, b.startTime));

    // Process events in time order
    for (FailureEvent event : events) {
      // Production at baseline until failure
      double uptime = event.startTime - currentTime;
      if (uptime > 0) {
        state.totalProduction += baselineProduction * uptime;
        state.uptimeHours += uptime;
      }

      // Production during failure (at degraded rate)
      Double degradedRate = productionWithFailure.get(event.equipmentName);
      if (degradedRate == null) {
        degradedRate = 0.0;
      }
      state.totalProduction += degradedRate * event.duration;
      state.downtimeHours += event.duration;
      state.failureCount++;

      currentTime = event.startTime + event.duration;
    }

    // Production from last event to end
    if (currentTime < timeHorizonHours) {
      double remaining = timeHorizonHours - currentTime;
      state.totalProduction += baselineProduction * remaining;
      state.uptimeHours += remaining;
    }

    return state;
  }

  private double getBaselineProductionRate() {
    ProcessEquipmentInterface unit = processSystem.getUnit(productStreamName);
    if (unit instanceof StreamInterface) {
      return ((StreamInterface) unit).getFlowRate("kg/hr");
    }
    if (unit instanceof neqsim.process.equipment.TwoPortInterface) {
      StreamInterface outlet = ((neqsim.process.equipment.TwoPortInterface) unit).getOutletStream();
      if (outlet != null) {
        return outlet.getFlowRate("kg/hr");
      }
    }
    return 0.0;
  }

  /**
   * Generates a production forecast with confidence intervals.
   *
   * @param days number of days to forecast
   * @param iterations Monte Carlo iterations
   * @return production forecast
   */
  public ProductionForecast generateForecast(int days, int iterations) {
    ProductionForecast forecast = new ProductionForecast(days);

    // Run simulation for each day
    for (int day = 1; day <= days; day++) {
      OperationalRiskResult result = runSimulation(iterations, day);
      forecast.addDataPoint(day, result.getMeanProduction(), result.getP10Production(),
          result.getP50Production(), result.getP90Production());
    }

    return forecast;
  }

  // Inner classes

  private static class SimulationState {
    double totalProduction;
    double uptimeHours;
    double downtimeHours;
    int failureCount;
  }

  private static class FailureEvent {
    final String equipmentName;
    final double startTime;
    final double duration;

    FailureEvent(String name, double start, double duration) {
      this.equipmentName = name;
      this.startTime = start;
      this.duration = duration;
    }
  }

  /**
   * Production forecast with time-series data.
   */
  public static class ProductionForecast implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int days;
    private final List<ForecastPoint> points = new ArrayList<ForecastPoint>();

    public ProductionForecast(int days) {
      this.days = days;
    }

    public void addDataPoint(int day, double mean, double p10, double p50, double p90) {
      points.add(new ForecastPoint(day, mean, p10, p50, p90));
    }

    public int getDays() {
      return days;
    }

    public List<ForecastPoint> getPoints() {
      return new ArrayList<ForecastPoint>(points);
    }

    /**
     * Gets cumulative production at a specific day.
     *
     * @param day the day number
     * @return forecast point or null if not found
     */
    public ForecastPoint getPoint(int day) {
      for (ForecastPoint p : points) {
        if (p.day == day) {
          return p;
        }
      }
      return null;
    }
  }

  /**
   * Single point in a production forecast.
   */
  public static class ForecastPoint implements Serializable {
    private static final long serialVersionUID = 1L;

    public final int day;
    public final double mean;
    public final double p10;
    public final double p50;
    public final double p90;

    public ForecastPoint(int day, double mean, double p10, double p50, double p90) {
      this.day = day;
      this.mean = mean;
      this.p10 = p10;
      this.p50 = p50;
      this.p90 = p90;
    }
  }
}
