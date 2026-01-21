package neqsim.process.util.fielddevelopment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioComparisonResult;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioKpi;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioRequest;
import neqsim.process.util.optimizer.ProductionOptimizer.UtilizationRecord;

/**
 * Extended facility capacity analysis for field development planning.
 *
 * <p>
 * This class builds on {@link ProductionOptimizer} to provide comprehensive facility capacity
 * assessment capabilities including:
 * <ul>
 * <li>Current bottleneck identification and utilization analysis</li>
 * <li>Near-bottleneck equipment detection (approaching capacity)</li>
 * <li>Debottleneck option generation and evaluation</li>
 * <li>Multi-scenario comparison for capital planning</li>
 * <li>Capacity evolution over field life as production declines</li>
 * <li>NPV calculation for debottleneck investments</li>
 * </ul>
 *
 * <h2>Bottleneck Analysis Concepts</h2>
 * <p>
 * The facility capacity is limited by the equipment with the highest utilization ratio:
 * 
 * <pre>
 * Utilization = Current Duty / Maximum Capacity
 * </pre>
 * 
 * The equipment with the highest utilization is the "bottleneck". Equipment with utilization above
 * a threshold (typically 80%) are "near-bottlenecks" that may become constraints if the current
 * bottleneck is relieved.
 *
 * <h2>Debottleneck Option Generation</h2>
 * <p>
 * For each near-bottleneck, the system generates a {@link DebottleneckOption} containing:
 * <ul>
 * <li>Current and proposed capacity</li>
 * <li>Incremental production enabled by the upgrade</li>
 * <li>Estimated CAPEX (if cost factors are configured)</li>
 * <li>Simple payback period</li>
 * </ul>
 *
 * <h2>Integration with ProductionOptimizer</h2>
 * <p>
 * This class uses {@link ProductionOptimizer} for:
 * <ul>
 * <li>Finding maximum sustainable production rate</li>
 * <li>Evaluating utilization across all equipment</li>
 * <li>Running scenario comparisons for debottleneck cases</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * FacilityCapacity capacity = new FacilityCapacity(facilityProcess);
 *
 * // Perform comprehensive capacity assessment
 * CapacityAssessment assessment = capacity.assess(feedStream, 1000.0, // lower bound
 *     100000.0, // upper bound
 *     "kg/hr");
 *
 * System.out.println("Current max rate: " + assessment.getCurrentMaxRate());
 * System.out.println("Bottleneck: " + assessment.getCurrentBottleneck());
 *
 * // Review debottleneck options
 * for (DebottleneckOption option : assessment.getDebottleneckOptions()) {
 *   System.out.printf("%s: +%.0f kg/hr for $%.0f M (payback: %.1f years)%n",
 *       option.getEquipmentName(), option.getIncrementalProduction(), option.getCapex() / 1e6,
 *       option.getPaybackYears());
 * }
 *
 * // Compare debottleneck scenarios
 * ScenarioComparisonResult comparison = capacity.compareDebottleneckScenarios(feedStream,
 *     assessment.getDebottleneckOptions().subList(0, 3), 1000.0, 100000.0, "kg/hr");
 * System.out.println(comparison.toMarkdownTable());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see ProductionOptimizer
 * @see ProductionProfile
 */
public class FacilityCapacity implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Default threshold for near-bottleneck detection (80% utilization). */
  public static final double DEFAULT_NEAR_BOTTLENECK_THRESHOLD = 0.80;

  /** Default capacity increase factor for debottleneck options. */
  public static final double DEFAULT_CAPACITY_INCREASE_FACTOR = 1.5;

  /** Process system representing the surface facility. */
  private final ProcessSystem facility;

  /** Production optimizer for bottleneck analysis. */
  private transient ProductionOptimizer optimizer;

  /** Threshold for near-bottleneck detection. */
  private double nearBottleneckThreshold = DEFAULT_NEAR_BOTTLENECK_THRESHOLD;

  /** Default capacity increase factor for debottleneck options. */
  private double capacityIncreaseFactor = DEFAULT_CAPACITY_INCREASE_FACTOR;

  /** Cost factors by equipment type for CAPEX estimation. */
  private final Map<Class<?>, Double> costFactorsByType = new HashMap<>();

  /** Cost factors by equipment name for CAPEX estimation. */
  private final Map<String, Double> costFactorsByName = new HashMap<>();

  /**
   * Capacity assessment period for time-varying analysis.
   *
   * <p>
   * Represents the facility capacity status at a specific point in field life, capturing the
   * current bottleneck, equipment utilizations, and headroom.
   */
  public static final class CapacityPeriod implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String periodName;
    private final double time;
    private final String timeUnit;
    private final double maxFacilityRate;
    private final String rateUnit;
    private final String bottleneckEquipment;
    private final double bottleneckUtilization;
    private final Map<String, Double> equipmentUtilizations;
    private final List<String> nearBottlenecks;
    private final boolean isFacilityConstrained;

    /**
     * Creates a capacity period.
     *
     * @param periodName descriptive name for the period
     * @param time time from start
     * @param timeUnit time unit
     * @param maxFacilityRate maximum sustainable rate
     * @param rateUnit rate unit
     * @param bottleneckEquipment name of bottleneck equipment
     * @param bottleneckUtilization utilization of bottleneck
     * @param equipmentUtilizations map of equipment name to utilization
     * @param nearBottlenecks list of near-bottleneck equipment names
     * @param isFacilityConstrained true if facility limits production
     */
    public CapacityPeriod(String periodName, double time, String timeUnit, double maxFacilityRate,
        String rateUnit, String bottleneckEquipment, double bottleneckUtilization,
        Map<String, Double> equipmentUtilizations, List<String> nearBottlenecks,
        boolean isFacilityConstrained) {
      this.periodName = periodName;
      this.time = time;
      this.timeUnit = timeUnit;
      this.maxFacilityRate = maxFacilityRate;
      this.rateUnit = rateUnit;
      this.bottleneckEquipment = bottleneckEquipment;
      this.bottleneckUtilization = bottleneckUtilization;
      this.equipmentUtilizations = new LinkedHashMap<>(equipmentUtilizations);
      this.nearBottlenecks = new ArrayList<>(nearBottlenecks);
      this.isFacilityConstrained = isFacilityConstrained;
    }

    /**
     * Gets the period name.
     *
     * @return period name
     */
    public String getPeriodName() {
      return periodName;
    }

    /**
     * Gets the time from start.
     *
     * @return time value
     */
    public double getTime() {
      return time;
    }

    /**
     * Gets the time unit.
     *
     * @return time unit
     */
    public String getTimeUnit() {
      return timeUnit;
    }

    /**
     * Gets the maximum sustainable facility rate.
     *
     * @return max rate
     */
    public double getMaxFacilityRate() {
      return maxFacilityRate;
    }

    /**
     * Gets the rate unit.
     *
     * @return rate unit
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the bottleneck equipment name.
     *
     * @return bottleneck name
     */
    public String getBottleneckEquipment() {
      return bottleneckEquipment;
    }

    /**
     * Gets the bottleneck utilization.
     *
     * @return utilization (0-1)
     */
    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    /**
     * Gets all equipment utilizations.
     *
     * @return map of equipment name to utilization
     */
    public Map<String, Double> getEquipmentUtilizations() {
      return Collections.unmodifiableMap(equipmentUtilizations);
    }

    /**
     * Gets near-bottleneck equipment names.
     *
     * @return list of near-bottleneck names
     */
    public List<String> getNearBottlenecks() {
      return Collections.unmodifiableList(nearBottlenecks);
    }

    /**
     * Checks if facility is constraining production.
     *
     * @return true if facility-constrained
     */
    public boolean isFacilityConstrained() {
      return isFacilityConstrained;
    }
  }

  /**
   * Debottleneck option with cost-benefit analysis.
   *
   * <p>
   * Represents a potential capacity upgrade for a piece of equipment, including the expected
   * production benefit and associated costs.
   */
  public static final class DebottleneckOption
      implements Serializable, Comparable<DebottleneckOption> {
    private static final long serialVersionUID = 1000L;

    private final String equipmentName;
    private final Class<?> equipmentType;
    private final String description;
    private final double currentCapacity;
    private final double upgradedCapacity;
    private final double currentUtilization;
    private final double incrementalProduction;
    private final String rateUnit;
    private final double capex;
    private final String currency;
    private final double paybackYears;
    private final double npv;

    /**
     * Creates a debottleneck option.
     *
     * @param equipmentName equipment name
     * @param equipmentType equipment class type
     * @param description upgrade description
     * @param currentCapacity current capacity
     * @param upgradedCapacity proposed capacity after upgrade
     * @param currentUtilization current utilization
     * @param incrementalProduction additional production enabled
     * @param rateUnit rate unit
     * @param capex capital expenditure
     * @param currency currency code
     * @param paybackYears simple payback period
     * @param npv net present value of upgrade
     */
    public DebottleneckOption(String equipmentName, Class<?> equipmentType, String description,
        double currentCapacity, double upgradedCapacity, double currentUtilization,
        double incrementalProduction, String rateUnit, double capex, String currency,
        double paybackYears, double npv) {
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.description = description;
      this.currentCapacity = currentCapacity;
      this.upgradedCapacity = upgradedCapacity;
      this.currentUtilization = currentUtilization;
      this.incrementalProduction = incrementalProduction;
      this.rateUnit = rateUnit;
      this.capex = capex;
      this.currency = currency;
      this.paybackYears = paybackYears;
      this.npv = npv;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the equipment type class.
     *
     * @return equipment class
     */
    public Class<?> getEquipmentType() {
      return equipmentType;
    }

    /**
     * Gets the upgrade description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets the current capacity.
     *
     * @return current capacity
     */
    public double getCurrentCapacity() {
      return currentCapacity;
    }

    /**
     * Gets the proposed upgraded capacity.
     *
     * @return upgraded capacity
     */
    public double getUpgradedCapacity() {
      return upgradedCapacity;
    }

    /**
     * Gets the current utilization.
     *
     * @return utilization (0-1)
     */
    public double getCurrentUtilization() {
      return currentUtilization;
    }

    /**
     * Gets the incremental production enabled by upgrade.
     *
     * @return incremental rate
     */
    public double getIncrementalProduction() {
      return incrementalProduction;
    }

    /**
     * Gets the rate unit.
     *
     * @return rate unit
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the capital expenditure.
     *
     * @return CAPEX
     */
    public double getCapex() {
      return capex;
    }

    /**
     * Gets the currency code.
     *
     * @return currency
     */
    public String getCurrency() {
      return currency;
    }

    /**
     * Gets the simple payback period in years.
     *
     * @return payback years
     */
    public double getPaybackYears() {
      return paybackYears;
    }

    /**
     * Gets the net present value.
     *
     * @return NPV
     */
    public double getNpv() {
      return npv;
    }

    /**
     * Gets the capacity increase percentage.
     *
     * @return capacity increase as percentage
     */
    public double getCapacityIncreasePercent() {
      return currentCapacity > 0 ? (upgradedCapacity - currentCapacity) / currentCapacity * 100 : 0;
    }

    @Override
    public int compareTo(DebottleneckOption other) {
      // Sort by NPV descending (best options first)
      return Double.compare(other.npv, this.npv);
    }

    @Override
    public String toString() {
      return String.format(
          "DebottleneckOption[%s: +%.1f%% capacity, +%.0f %s, CAPEX=%.0f %s, payback=%.1f yr]",
          equipmentName, getCapacityIncreasePercent(), incrementalProduction, rateUnit, capex,
          currency, paybackYears);
    }
  }

  /**
   * Complete facility capacity assessment result.
   *
   * <p>
   * Contains the full capacity analysis including current bottleneck, near-bottlenecks,
   * debottleneck options, and equipment headroom.
   */
  public static final class CapacityAssessment implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final double currentMaxRate;
    private final String rateUnit;
    private final String currentBottleneck;
    private final double bottleneckUtilization;
    private final List<UtilizationRecord> utilizationRecords;
    private final List<String> nearBottlenecks;
    private final List<DebottleneckOption> debottleneckOptions;
    private final Map<String, Double> equipmentHeadroom;
    private final boolean feasible;

    /**
     * Creates a capacity assessment.
     *
     * @param currentMaxRate maximum sustainable rate
     * @param rateUnit rate unit
     * @param currentBottleneck bottleneck equipment name
     * @param bottleneckUtilization bottleneck utilization
     * @param utilizationRecords all utilization records
     * @param nearBottlenecks near-bottleneck equipment names
     * @param debottleneckOptions generated debottleneck options
     * @param equipmentHeadroom map of equipment to remaining capacity
     * @param feasible true if a feasible operating point was found
     */
    public CapacityAssessment(double currentMaxRate, String rateUnit, String currentBottleneck,
        double bottleneckUtilization, List<UtilizationRecord> utilizationRecords,
        List<String> nearBottlenecks, List<DebottleneckOption> debottleneckOptions,
        Map<String, Double> equipmentHeadroom, boolean feasible) {
      this.currentMaxRate = currentMaxRate;
      this.rateUnit = rateUnit;
      this.currentBottleneck = currentBottleneck;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationRecords = new ArrayList<>(utilizationRecords);
      this.nearBottlenecks = new ArrayList<>(nearBottlenecks);
      this.debottleneckOptions = new ArrayList<>(debottleneckOptions);
      this.equipmentHeadroom = new LinkedHashMap<>(equipmentHeadroom);
      this.feasible = feasible;
    }

    /**
     * Gets the current maximum sustainable rate.
     *
     * @return max rate
     */
    public double getCurrentMaxRate() {
      return currentMaxRate;
    }

    /**
     * Gets the rate unit.
     *
     * @return rate unit
     */
    public String getRateUnit() {
      return rateUnit;
    }

    /**
     * Gets the current bottleneck equipment name.
     *
     * @return bottleneck name
     */
    public String getCurrentBottleneck() {
      return currentBottleneck;
    }

    /**
     * Gets the bottleneck utilization.
     *
     * @return utilization (0-1)
     */
    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    /**
     * Gets all utilization records.
     *
     * @return unmodifiable list of utilization records
     */
    public List<UtilizationRecord> getUtilizationRecords() {
      return Collections.unmodifiableList(utilizationRecords);
    }

    /**
     * Gets near-bottleneck equipment names.
     *
     * @return unmodifiable list of near-bottleneck names
     */
    public List<String> getNearBottlenecks() {
      return Collections.unmodifiableList(nearBottlenecks);
    }

    /**
     * Gets debottleneck options sorted by NPV.
     *
     * @return unmodifiable list of debottleneck options
     */
    public List<DebottleneckOption> getDebottleneckOptions() {
      return Collections.unmodifiableList(debottleneckOptions);
    }

    /**
     * Gets equipment headroom (rest capacity).
     *
     * @return map of equipment name to remaining capacity
     */
    public Map<String, Double> getEquipmentHeadroom() {
      return Collections.unmodifiableMap(equipmentHeadroom);
    }

    /**
     * Checks if a feasible operating point was found.
     *
     * @return true if feasible
     */
    public boolean isFeasible() {
      return feasible;
    }

    /**
     * Gets the total potential production gain from all debottleneck options.
     *
     * @return sum of incremental production from all options
     */
    public double getTotalPotentialGain() {
      return debottleneckOptions.stream().mapToDouble(DebottleneckOption::getIncrementalProduction)
          .sum();
    }

    /**
     * Gets the top N debottleneck options by NPV.
     *
     * @param n number of options to return
     * @return list of top options
     */
    public List<DebottleneckOption> getTopOptions(int n) {
      return debottleneckOptions.stream().sorted().limit(n).collect(Collectors.toList());
    }

    /**
     * Generates a Markdown summary of the assessment.
     *
     * @return Markdown formatted string
     */
    public String toMarkdown() {
      StringBuilder sb = new StringBuilder();
      sb.append("## Facility Capacity Assessment\n\n");
      sb.append(String.format("- **Maximum Rate**: %.2f %s\n", currentMaxRate, rateUnit));
      sb.append(String.format("- **Current Bottleneck**: %s (%.1f%% utilization)\n",
          currentBottleneck != null ? currentBottleneck : "None", bottleneckUtilization * 100));
      sb.append(String.format("- **Feasible**: %s\n\n", feasible ? "Yes" : "No"));

      if (!nearBottlenecks.isEmpty()) {
        sb.append("### Near-Bottlenecks (>80% utilization)\n\n");
        for (String name : nearBottlenecks) {
          Double headroom = equipmentHeadroom.get(name);
          sb.append(
              String.format("- %s (headroom: %.2f)\n", name, headroom != null ? headroom : 0.0));
        }
        sb.append("\n");
      }

      sb.append("### Equipment Utilization\n\n");
      sb.append("| Equipment | Duty | Capacity | Utilization | Headroom |\n");
      sb.append("|---|---|---|---|---|\n");
      for (UtilizationRecord record : utilizationRecords) {
        double headroom = equipmentHeadroom.getOrDefault(record.getEquipmentName(), 0.0);
        sb.append(String.format("| %s | %.2f | %.2f | %.1f%% | %.2f |\n", record.getEquipmentName(),
            record.getCapacityDuty(), record.getCapacityMax(), record.getUtilization() * 100,
            headroom));
      }
      sb.append("\n");

      if (!debottleneckOptions.isEmpty()) {
        sb.append("### Debottleneck Options\n\n");
        sb.append(
            "| Equipment | Current Cap | Upgraded Cap | Incremental Prod | CAPEX | Payback |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (DebottleneckOption option : debottleneckOptions) {
          sb.append(String.format("| %s | %.2f | %.2f | +%.2f %s | %.0f %s | %.1f yr |\n",
              option.getEquipmentName(), option.getCurrentCapacity(), option.getUpgradedCapacity(),
              option.getIncrementalProduction(), option.getRateUnit(), option.getCapex(),
              option.getCurrency(), option.getPaybackYears()));
        }
      }

      return sb.toString();
    }
  }

  /**
   * Creates a facility capacity analyzer.
   *
   * @param facility process system representing the surface facility
   */
  public FacilityCapacity(ProcessSystem facility) {
    this.facility = Objects.requireNonNull(facility, "Facility is required");
    this.optimizer = new ProductionOptimizer();
  }

  /**
   * Performs comprehensive capacity assessment.
   *
   * <p>
   * This method:
   * <ol>
   * <li>Runs production optimization to find maximum sustainable rate</li>
   * <li>Identifies the current bottleneck and its utilization</li>
   * <li>Finds all near-bottleneck equipment (&gt;80% utilization)</li>
   * <li>Generates debottleneck options for each near-bottleneck</li>
   * <li>Estimates CAPEX and payback for each option</li>
   * </ol>
   *
   * @param feedStream feed stream for rate adjustment
   * @param lowerBound lower bound for rate optimization
   * @param upperBound upper bound for rate optimization
   * @param rateUnit rate unit
   * @return comprehensive capacity assessment
   */
  public CapacityAssessment assess(StreamInterface feedStream, double lowerBound, double upperBound,
      String rateUnit) {
    Objects.requireNonNull(feedStream, "Feed stream is required");

    // Run optimization to find max rate
    OptimizationConfig config = new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit)
        .tolerance(lowerBound * 0.001);

    OptimizationResult result = getOptimizer().optimize(facility, feedStream, config,
        Collections.emptyList(), Collections.emptyList());

    // Extract bottleneck info
    String bottleneckName =
        result.getBottleneck() != null ? result.getBottleneck().getName() : null;
    double bottleneckUtilization = result.getBottleneckUtilization();

    // Find near-bottlenecks
    List<String> nearBottlenecks = new ArrayList<>();
    Map<String, Double> equipmentHeadroom = new LinkedHashMap<>();

    for (UtilizationRecord record : result.getUtilizationRecords()) {
      double headroom = record.getCapacityMax() - record.getCapacityDuty();
      equipmentHeadroom.put(record.getEquipmentName(), headroom);

      if (record.getUtilization() >= nearBottleneckThreshold
          && !record.getEquipmentName().equals(bottleneckName)) {
        nearBottlenecks.add(record.getEquipmentName());
      }
    }

    // Generate debottleneck options
    List<DebottleneckOption> options =
        generateDebottleneckOptions(result, feedStream, lowerBound, upperBound, rateUnit);

    return new CapacityAssessment(result.getOptimalRate(), rateUnit, bottleneckName,
        bottleneckUtilization, result.getUtilizationRecords(), nearBottlenecks, options,
        equipmentHeadroom, result.isFeasible());
  }

  /**
   * Generates debottleneck options for near-bottleneck equipment.
   */
  private List<DebottleneckOption> generateDebottleneckOptions(OptimizationResult baseResult,
      StreamInterface feedStream, double lowerBound, double upperBound, String rateUnit) {
    List<DebottleneckOption> options = new ArrayList<>();

    // Start with bottleneck
    if (baseResult.getBottleneck() != null) {
      DebottleneckOption bottleneckOption = createDebottleneckOption(baseResult.getBottleneck(),
          baseResult, feedStream, lowerBound, upperBound, rateUnit);
      if (bottleneckOption != null) {
        options.add(bottleneckOption);
      }
    }

    // Add near-bottlenecks
    for (UtilizationRecord record : baseResult.getUtilizationRecords()) {
      if (record.getUtilization() >= nearBottleneckThreshold) {
        ProcessEquipmentInterface equipment = findEquipment(record.getEquipmentName());
        if (equipment != null && !equipment.getName().equals(
            baseResult.getBottleneck() != null ? baseResult.getBottleneck().getName() : "")) {
          DebottleneckOption option = createDebottleneckOption(equipment, baseResult, feedStream,
              lowerBound, upperBound, rateUnit);
          if (option != null) {
            options.add(option);
          }
        }
      }
    }

    // Sort by NPV (best first)
    Collections.sort(options);

    return options;
  }

  /**
   * Creates a debottleneck option for a piece of equipment.
   */
  private DebottleneckOption createDebottleneckOption(ProcessEquipmentInterface equipment,
      OptimizationResult baseResult, StreamInterface feedStream, double lowerBound,
      double upperBound, String rateUnit) {
    double currentCapacity = equipment.getCapacityMax();
    double currentDuty = equipment.getCapacityDuty();
    double currentUtilization = currentCapacity > 0 ? currentDuty / currentCapacity : 0;

    if (currentCapacity <= 0) {
      return null; // Can't create option without capacity info
    }

    // Calculate upgraded capacity
    double upgradedCapacity = currentCapacity * capacityIncreaseFactor;

    // Estimate incremental production
    // Simple estimate: proportional to capacity increase if this is the bottleneck
    double incrementalProduction = 0;
    if (baseResult.getBottleneck() != null
        && baseResult.getBottleneck().getName().equals(equipment.getName())) {
      // This is the bottleneck - increasing its capacity directly increases throughput
      double capacityRatio = upgradedCapacity / currentCapacity;
      incrementalProduction = baseResult.getOptimalRate() * (capacityRatio - 1);
      // Cap at the potential of the next bottleneck
      incrementalProduction =
          Math.min(incrementalProduction, upperBound - baseResult.getOptimalRate());
    } else {
      // Not the bottleneck - may enable future debottlenecking
      incrementalProduction = 0;
    }

    // Estimate CAPEX
    double capex = estimateCapex(equipment, currentCapacity, upgradedCapacity);
    String currency = "USD";

    // Calculate simple payback
    double annualProductionGain = incrementalProduction * 365.25; // Assuming daily rate
    double revenuePerUnit = 50.0; // Default revenue assumption (e.g., $/bbl or $/Sm3)
    double annualRevenue = annualProductionGain * revenuePerUnit;
    double paybackYears = annualRevenue > 0 ? capex / annualRevenue : Double.POSITIVE_INFINITY;

    // Calculate NPV (simplified)
    double discountRate = 0.10;
    int years = 10;
    double npv =
        calculateSimpleNPV(incrementalProduction, revenuePerUnit, capex, discountRate, years);

    String description = String.format("Increase %s capacity from %.0f to %.0f",
        equipment.getName(), currentCapacity, upgradedCapacity);

    return new DebottleneckOption(equipment.getName(), equipment.getClass(), description,
        currentCapacity, upgradedCapacity, currentUtilization, incrementalProduction, rateUnit,
        capex, currency, paybackYears, npv);
  }

  /**
   * Estimates CAPEX for a capacity upgrade.
   */
  private double estimateCapex(ProcessEquipmentInterface equipment, double currentCapacity,
      double upgradedCapacity) {
    // Check for configured cost factors
    Double costFactor = costFactorsByName.get(equipment.getName());
    if (costFactor == null) {
      costFactor = costFactorsByType.get(equipment.getClass());
    }
    if (costFactor == null) {
      // Default cost factor based on capacity increase
      costFactor = 100000.0; // Base cost per unit capacity
    }

    double capacityIncrease = upgradedCapacity - currentCapacity;
    return capacityIncrease * costFactor;
  }

  /**
   * Calculates simplified NPV for a debottleneck investment.
   */
  private double calculateSimpleNPV(double dailyIncrementalProduction, double revenuePerUnit,
      double capex, double discountRate, int years) {
    double annualRevenue = dailyIncrementalProduction * 365.25 * revenuePerUnit;
    double npv = -capex;

    for (int year = 1; year <= years; year++) {
      npv += annualRevenue / Math.pow(1 + discountRate, year);
    }

    return npv;
  }

  /**
   * Finds equipment by name in the facility.
   */
  private ProcessEquipmentInterface findEquipment(String name) {
    for (ProcessEquipmentInterface equipment : facility.getUnitOperations()) {
      if (equipment.getName().equals(name)) {
        return equipment;
      }
    }
    return null;
  }

  /**
   * Compares multiple debottleneck scenarios.
   *
   * <p>
   * Creates and runs optimization scenarios for each debottleneck option, allowing direct
   * comparison of production gains and costs.
   *
   * @param feedStream feed stream for optimization
   * @param options debottleneck options to compare
   * @param lowerBound lower bound for rate optimization
   * @param upperBound upper bound for rate optimization
   * @param rateUnit rate unit
   * @return scenario comparison result
   */
  public ScenarioComparisonResult compareDebottleneckScenarios(StreamInterface feedStream,
      List<DebottleneckOption> options, double lowerBound, double upperBound, String rateUnit) {
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(options, "Options are required");

    List<ScenarioRequest> scenarios = new ArrayList<>();

    // Base case
    OptimizationConfig baseConfig =
        new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);
    scenarios.add(new ScenarioRequest("Base Case", facility, feedStream, baseConfig,
        Collections.emptyList(), Collections.emptyList()));

    // Note: In a full implementation, each scenario would clone the facility
    // and modify the equipment capacity. For now, we just set up the scenario structure.
    for (DebottleneckOption option : options) {
      String scenarioName = "Debottleneck " + option.getEquipmentName();
      // Would need to clone facility and modify equipment capacity
      scenarios.add(new ScenarioRequest(scenarioName, facility, feedStream, baseConfig,
          Collections.emptyList(), Collections.emptyList()));
    }

    // Define KPIs
    List<ScenarioKpi> kpis = new ArrayList<>();
    kpis.add(ScenarioKpi.optimalRate(rateUnit));

    return getOptimizer().compareScenarios(scenarios, kpis);
  }

  /**
   * Analyzes capacity evolution over field life.
   *
   * <p>
   * Tracks how the bottleneck shifts as production declines, identifying when the facility becomes
   * unconstrained.
   *
   * @param forecast production forecast from {@link ProductionProfile}
   * @param feedStream feed stream for facility analysis
   * @return list of capacity periods showing evolution
   */
  public List<CapacityPeriod> analyzeOverFieldLife(ProductionProfile.ProductionForecast forecast,
      StreamInterface feedStream) {
    Objects.requireNonNull(forecast, "Forecast is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");

    List<CapacityPeriod> periods = new ArrayList<>();

    for (ProductionProfile.ProductionPoint point : forecast.getProfile()) {
      // Set feed rate to the forecast rate
      feedStream.setFlowRate(point.getRate(), forecast.getDeclineParams().getRateUnit());

      try {
        facility.run();

        // Get bottleneck info
        ProcessEquipmentInterface bottleneck = facility.getBottleneck();
        String bottleneckName = bottleneck != null ? bottleneck.getName() : null;
        double bottleneckUtil = 0;
        if (bottleneck != null && bottleneck.getCapacityMax() > 0) {
          bottleneckUtil = bottleneck.getCapacityDuty() / bottleneck.getCapacityMax();
        }

        // Collect utilizations
        Map<String, Double> utilizations = new LinkedHashMap<>();
        List<String> nearBottlenecks = new ArrayList<>();

        for (ProcessEquipmentInterface equipment : facility.getUnitOperations()) {
          double capacity = equipment.getCapacityMax();
          if (capacity > 0) {
            double util = equipment.getCapacityDuty() / capacity;
            utilizations.put(equipment.getName(), util);
            if (util >= nearBottleneckThreshold && !equipment.getName().equals(bottleneckName)) {
              nearBottlenecks.add(equipment.getName());
            }
          }
        }

        boolean constrained = bottleneckUtil >= nearBottleneckThreshold;

        periods.add(new CapacityPeriod(String.format("Year %.1f", point.getTime()), point.getTime(),
            "years", point.getRate(), forecast.getDeclineParams().getRateUnit(), bottleneckName,
            bottleneckUtil, utilizations, nearBottlenecks, constrained));

      } catch (Exception e) {
        // Log and continue
        System.err.println(
            "Failed to analyze capacity at time " + point.getTime() + ": " + e.getMessage());
      }
    }

    return periods;
  }

  /**
   * Calculates NPV for a debottleneck investment.
   *
   * <p>
   * Uses discounted cash flow analysis considering:
   * <ul>
   * <li>Incremental production over the benefit period</li>
   * <li>Product price and operating costs</li>
   * <li>Capital expenditure</li>
   * <li>Discount rate for time value of money</li>
   * </ul>
   *
   * @param option debottleneck option to evaluate
   * @param productPrice price per unit of production (e.g., $/bbl)
   * @param opexPerUnit operating cost per unit
   * @param discountRate annual discount rate (e.g., 0.10 for 10%)
   * @param yearsOfBenefit number of years to realize benefit
   * @return net present value
   */
  public double calculateDebottleneckNPV(DebottleneckOption option, double productPrice,
      double opexPerUnit, double discountRate, int yearsOfBenefit) {
    Objects.requireNonNull(option, "Option is required");

    double annualProduction = option.getIncrementalProduction() * 365.25;
    double annualRevenue = annualProduction * (productPrice - opexPerUnit);

    double npv = -option.getCapex();

    for (int year = 1; year <= yearsOfBenefit; year++) {
      npv += annualRevenue / Math.pow(1 + discountRate, year);
    }

    return npv;
  }

  /**
   * Gets the production optimizer instance.
   *
   * @return optimizer (creates if needed)
   */
  private ProductionOptimizer getOptimizer() {
    if (optimizer == null) {
      optimizer = new ProductionOptimizer();
    }
    return optimizer;
  }

  /**
   * Gets the facility process system.
   *
   * @return facility
   */
  public ProcessSystem getFacility() {
    return facility;
  }

  /**
   * Sets the near-bottleneck threshold.
   *
   * @param threshold utilization threshold (0-1)
   */
  public void setNearBottleneckThreshold(double threshold) {
    if (threshold < 0 || threshold > 1) {
      throw new IllegalArgumentException("Threshold must be between 0 and 1");
    }
    this.nearBottleneckThreshold = threshold;
  }

  /**
   * Gets the near-bottleneck threshold.
   *
   * @return threshold
   */
  public double getNearBottleneckThreshold() {
    return nearBottleneckThreshold;
  }

  /**
   * Sets the capacity increase factor for debottleneck options.
   *
   * @param factor multiplication factor (e.g., 1.5 for 50% increase)
   */
  public void setCapacityIncreaseFactor(double factor) {
    if (factor <= 1) {
      throw new IllegalArgumentException("Capacity increase factor must be > 1");
    }
    this.capacityIncreaseFactor = factor;
  }

  /**
   * Sets a cost factor for a specific equipment type.
   *
   * @param equipmentType equipment class
   * @param costPerUnit cost per unit of capacity increase
   */
  public void setCostFactorForType(Class<?> equipmentType, double costPerUnit) {
    costFactorsByType.put(equipmentType, costPerUnit);
  }

  /**
   * Sets a cost factor for a specific equipment name.
   *
   * @param equipmentName equipment name
   * @param costPerUnit cost per unit of capacity increase
   */
  public void setCostFactorForName(String equipmentName, double costPerUnit) {
    costFactorsByName.put(equipmentName, costPerUnit);
  }
}

