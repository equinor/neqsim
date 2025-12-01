package neqsim.process.util.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Utility class for production optimization based on capacity utilization and configurable
 * constraints. The optimizer currently targets a single decision variable (feed flow) but keeps a
 * structured record of objectives, constraint margins, and utilization limits so that more advanced
 * workflows (multi-objective scoring, probabilistic margins, reporting) can be built on top.
 */
public class ProductionOptimizer {
  /** Default maximum utilization used when no specific equipment rule is provided. */
  public static final double DEFAULT_UTILIZATION_LIMIT = 0.95;

  /** Supported search algorithms. */
  public enum SearchMode {
    /** Traditional monotonic binary search on feasibility. */
    BINARY_FEASIBILITY,
    /** Golden-section scoring search suitable for non-monotonic responses. */
    GOLDEN_SECTION_SCORE,
    /** Nelder–Mead simplex search on the composite score. */
    NELDER_MEAD_SCORE,
    /** Particle-swarm search on the composite/feasibility score. */
    PARTICLE_SWARM_SCORE
  }

  /** Objective optimization direction. */
  public enum ObjectiveType {
    MAXIMIZE,
    MINIMIZE
  }

  /** Direction of a constraint comparison. */
  public enum ConstraintDirection {
    LESS_THAN,
    GREATER_THAN
  }

  /** Severity classification for constraints. */
  public enum ConstraintSeverity {
    HARD,
    SOFT
  }

  /** Simple container for objective configuration. */
  public static final class OptimizationObjective {
    private final String name;
    private final ToDoubleFunction<ProcessSystem> evaluator;
    private final double weight;
    private final ObjectiveType type;

    public OptimizationObjective(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double weight) {
      this(name, evaluator, weight, ObjectiveType.MAXIMIZE);
    }

    public OptimizationObjective(String name, ToDoubleFunction<ProcessSystem> evaluator,
        double weight, ObjectiveType type) {
      this.name = Objects.requireNonNull(name, "Objective name is required");
      this.evaluator = Objects.requireNonNull(evaluator, "Objective evaluator is required");
      this.weight = weight;
      this.type = Objects.requireNonNull(type, "Objective type is required");
    }

    public String getName() {
      return name;
    }

    public double getWeight() {
      return weight;
    }

    public ObjectiveType getType() {
      return type;
    }

    public double evaluate(ProcessSystem process) {
      return evaluator.applyAsDouble(process);
    }
  }

  /** Simple container for constraint configuration. */
  public static final class OptimizationConstraint {
    private final String name;
    private final ToDoubleFunction<ProcessSystem> metric;
    private final double limit;
    private final ConstraintDirection direction;
    private final ConstraintSeverity severity;
    private final double penaltyWeight;
    private final String description;

    public OptimizationConstraint(String name, ToDoubleFunction<ProcessSystem> metric,
        double limit, ConstraintDirection direction, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      this.name = Objects.requireNonNull(name, "Constraint name is required");
      this.metric = Objects.requireNonNull(metric, "Constraint metric is required");
      this.limit = limit;
      this.direction = Objects.requireNonNull(direction, "Constraint direction is required");
      this.severity = Objects.requireNonNull(severity, "Constraint severity is required");
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public static OptimizationConstraint lessThan(String name,
        ToDoubleFunction<ProcessSystem> metric, double limit, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      return new OptimizationConstraint(name, metric, limit, ConstraintDirection.LESS_THAN,
          severity, penaltyWeight, description);
    }

    public static OptimizationConstraint greaterThan(String name,
        ToDoubleFunction<ProcessSystem> metric, double limit, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      return new OptimizationConstraint(name, metric, limit, ConstraintDirection.GREATER_THAN,
          severity, penaltyWeight, description);
    }

    public String getName() {
      return name;
    }

    public ConstraintSeverity getSeverity() {
      return severity;
    }

    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    public String getDescription() {
      return description;
    }

    public double margin(ProcessSystem process) {
      double value = metric.applyAsDouble(process);
      if (direction == ConstraintDirection.LESS_THAN) {
        return limit - value;
      }
      return value - limit;
    }

    public boolean isSatisfied(ProcessSystem process) {
      return margin(process) >= 0.0;
    }
  }

  /** Holds the utilization status for an equipment item. */
  public static final class UtilizationRecord {
    private final String equipmentName;
    private final double capacityDuty;
    private final double capacityMax;
    private final double utilization;
    private final double utilizationLimit;

    public UtilizationRecord(String equipmentName, double capacityDuty, double capacityMax,
        double utilization, double utilizationLimit) {
      this.equipmentName = equipmentName;
      this.capacityDuty = capacityDuty;
      this.capacityMax = capacityMax;
      this.utilization = utilization;
      this.utilizationLimit = utilizationLimit;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public double getCapacityDuty() {
      return capacityDuty;
    }

    public double getCapacityMax() {
      return capacityMax;
    }

    public double getUtilization() {
      return utilization;
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }
  }

  /** Result container for a completed optimization attempt. */
  public static final class OptimizationResult {
    private final double optimalRate;
    private final String rateUnit;
    private final Map<String, Double> decisionVariables;
    private final ProcessEquipmentInterface bottleneck;
    private final double bottleneckUtilization;
    private final List<UtilizationRecord> utilizationRecords;
    private final Map<String, Double> objectiveValues;
    private final List<ConstraintStatus> constraintStatuses;
    private final boolean feasible;
    private final double score;
    private final int iterations;
    private final List<IterationRecord> iterationHistory;

    public OptimizationResult(double optimalRate, String rateUnit,
        Map<String, Double> decisionVariables, ProcessEquipmentInterface bottleneck,
        double bottleneckUtilization, List<UtilizationRecord> utilizationRecords,
        Map<String, Double> objectiveValues, List<ConstraintStatus> constraintStatuses,
        boolean feasible, double score, int iterations, List<IterationRecord> iterationHistory) {
      this.optimalRate = optimalRate;
      this.rateUnit = rateUnit;
      this.decisionVariables = decisionVariables == null ? Map.of()
          : new HashMap<>(decisionVariables);
      this.bottleneck = bottleneck;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationRecords = utilizationRecords;
      this.objectiveValues = objectiveValues;
      this.constraintStatuses = constraintStatuses;
      this.feasible = feasible;
      this.score = score;
      this.iterations = iterations;
      this.iterationHistory = iterationHistory;
    }

    public double getOptimalRate() {
      return optimalRate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public Map<String, Double> getDecisionVariables() {
      return decisionVariables;
    }

    public ProcessEquipmentInterface getBottleneck() {
      return bottleneck;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public List<UtilizationRecord> getUtilizationRecords() {
      return utilizationRecords;
    }

    public Map<String, Double> getObjectiveValues() {
      return objectiveValues;
    }

    public List<ConstraintStatus> getConstraintStatuses() {
      return constraintStatuses;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public double getScore() {
      return score;
    }

    public int getIterations() {
      return iterations;
    }

    public List<IterationRecord> getIterationHistory() {
      return iterationHistory;
    }
  }

  /**
   * Lightweight summary of an optimization run intended for quick-consumption APIs.
   */
  public static final class OptimizationSummary {
    private final double maxRate;
    private final String rateUnit;
    private final String limitingEquipment;
    private final double utilization;
    private final double utilizationLimit;
    private final double utilizationMargin;
    private final boolean feasible;
    private final Map<String, Double> decisionVariables;
    private final List<UtilizationRecord> utilizations;
    private final List<ConstraintStatus> constraints;

    public OptimizationSummary(double maxRate, String rateUnit, String limitingEquipment,
        double utilization, double utilizationLimit, double utilizationMargin, boolean feasible,
        Map<String, Double> decisionVariables, List<UtilizationRecord> utilizations,
        List<ConstraintStatus> constraints) {
      this.maxRate = maxRate;
      this.rateUnit = rateUnit;
      this.limitingEquipment = limitingEquipment;
      this.utilization = utilization;
      this.utilizationLimit = utilizationLimit;
      this.utilizationMargin = utilizationMargin;
      this.feasible = feasible;
      this.decisionVariables = decisionVariables == null ? Map.of()
          : new HashMap<>(decisionVariables);
      this.utilizations = utilizations == null ? List.of() : new ArrayList<>(utilizations);
      this.constraints = constraints == null ? List.of() : new ArrayList<>(constraints);
    }

    public double getMaxRate() {
      return maxRate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public String getLimitingEquipment() {
      return limitingEquipment;
    }

    public double getUtilization() {
      return utilization;
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }

    public double getUtilizationMargin() {
      return utilizationMargin;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public Map<String, Double> getDecisionVariables() {
      return new HashMap<>(decisionVariables);
    }

    public List<UtilizationRecord> getUtilizations() {
      return new ArrayList<>(utilizations);
    }

    public List<ConstraintStatus> getConstraints() {
      return new ArrayList<>(constraints);
    }
  }

  /** Outcome for a single constraint evaluation. */
  public static final class ConstraintStatus {
    private final String name;
    private final ConstraintSeverity severity;
    private final double margin;
    private final double penaltyWeight;
    private final String description;

    public ConstraintStatus(String name, ConstraintSeverity severity, double margin,
        double penaltyWeight, String description) {
      this.name = name;
      this.severity = severity;
      this.margin = margin;
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public String getName() {
      return name;
    }

    public ConstraintSeverity getSeverity() {
      return severity;
    }

    public double getMargin() {
      return margin;
    }

    public double getPenaltyWeight() {
      return penaltyWeight;
    }

    public String getDescription() {
      return description;
    }

    public boolean violated() {
      return margin < 0.0;
    }
  }

  /** Snapshot of each iteration to support diagnostics and plotting. */
  public static final class IterationRecord {
    private final double rate;
    private final String rateUnit;
    private final Map<String, Double> decisionVariables;
    private final String bottleneckName;
    private final double bottleneckUtilization;
    private final boolean utilizationWithinLimits;
    private final boolean hardConstraintsOk;
    private final boolean feasible;
    private final double score;
    private final List<UtilizationRecord> utilizations;

    public IterationRecord(double rate, String rateUnit, Map<String, Double> decisionVariables,
        String bottleneckName, double bottleneckUtilization, boolean utilizationWithinLimits,
        boolean hardConstraintsOk, boolean feasible, double score,
        List<UtilizationRecord> utilizations) {
      this.rate = rate;
      this.rateUnit = rateUnit;
      this.decisionVariables = decisionVariables == null ? Map.of()
          : new HashMap<>(decisionVariables);
      this.bottleneckName = bottleneckName;
      this.bottleneckUtilization = bottleneckUtilization;
      this.utilizationWithinLimits = utilizationWithinLimits;
      this.hardConstraintsOk = hardConstraintsOk;
      this.feasible = feasible;
      this.score = score;
      this.utilizations = new ArrayList<>(utilizations);
    }

    public double getRate() {
      return rate;
    }

    public String getRateUnit() {
      return rateUnit;
    }

    public Map<String, Double> getDecisionVariables() {
      return decisionVariables;
    }

    public String getBottleneckName() {
      return bottleneckName;
    }

    public double getBottleneckUtilization() {
      return bottleneckUtilization;
    }

    public boolean isUtilizationWithinLimits() {
      return utilizationWithinLimits;
    }

    public boolean isHardConstraintsOk() {
      return hardConstraintsOk;
    }

    public boolean isFeasible() {
      return feasible;
    }

    public double getScore() {
      return score;
    }

    public List<UtilizationRecord> getUtilizations() {
      return new ArrayList<>(utilizations);
    }
  }

  /** Builder-style configuration for the optimizer. */
  public static final class OptimizationConfig {
    private double lowerBound;
    private double upperBound;
    private double tolerance = 1e-3;
    private int maxIterations = 30;
    private String rateUnit = "kg/hr";
    private double defaultUtilizationLimit = DEFAULT_UTILIZATION_LIMIT;
    private double utilizationMarginFraction = 0.0;
    private double capacityUncertaintyFraction = 0.0;
    private double capacityPercentile = 0.5;
    private double capacityRangeSpreadFraction = 0.0;
    private SearchMode searchMode = SearchMode.BINARY_FEASIBILITY;
    private boolean enableCaching = true;
    private int swarmSize = 8;
    private double inertiaWeight = 0.6;
    private double cognitiveWeight = 1.2;
    private double socialWeight = 1.2;
    private double columnFsFactorLimit = 2.5;
    private final Map<String, Double> utilizationLimitsByName = new HashMap<>();
    private final Map<Class<?>, Double> utilizationLimitsByType = new HashMap<>();
    private final Map<String, CapacityRule> capacityRulesByName = new HashMap<>();
    private final Map<Class<?>, CapacityRule> capacityRulesByType = new HashMap<>();
    private final Map<String, CapacityRange> capacityRangesByName = new HashMap<>();
    private final Map<Class<?>, CapacityRange> capacityRangesByType = new HashMap<>();
    private final List<EquipmentConstraintRule> equipmentConstraintRules = new ArrayList<>();

    public OptimizationConfig(double lowerBound, double upperBound) {
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
    }

    public OptimizationConfig tolerance(double tolerance) {
      this.tolerance = tolerance;
      return this;
    }

    public OptimizationConfig maxIterations(int maxIterations) {
      this.maxIterations = maxIterations;
      return this;
    }

    public int getMaxIterations() {
      return maxIterations;
    }

    public double getUtilizationMarginFraction() {
      return utilizationMarginFraction;
    }

    public double getCapacityUncertaintyFraction() {
      return capacityUncertaintyFraction;
    }

    public double getCapacityPercentile() {
      return capacityPercentile;
    }

    public double getCapacityRangeSpreadFraction() {
      return capacityRangeSpreadFraction;
    }

    public OptimizationConfig rateUnit(String rateUnit) {
      this.rateUnit = rateUnit;
      return this;
    }

    public OptimizationConfig searchMode(SearchMode mode) {
      this.searchMode = mode;
      return this;
    }

    public OptimizationConfig swarmSize(int swarmSize) {
      this.swarmSize = swarmSize;
      return this;
    }

    public OptimizationConfig inertiaWeight(double inertiaWeight) {
      this.inertiaWeight = inertiaWeight;
      return this;
    }

    public OptimizationConfig cognitiveWeight(double cognitiveWeight) {
      this.cognitiveWeight = cognitiveWeight;
      return this;
    }

    public OptimizationConfig socialWeight(double socialWeight) {
      this.socialWeight = socialWeight;
      return this;
    }

    public OptimizationConfig columnFsFactorLimit(double columnFsFactorLimit) {
      this.columnFsFactorLimit = columnFsFactorLimit;
      return this;
    }

    public OptimizationConfig defaultUtilizationLimit(double defaultUtilizationLimit) {
      this.defaultUtilizationLimit = defaultUtilizationLimit;
      return this;
    }

    public OptimizationConfig utilizationMarginFraction(double utilizationMarginFraction) {
      this.utilizationMarginFraction = utilizationMarginFraction;
      return this;
    }

    public OptimizationConfig capacityUncertaintyFraction(double capacityUncertaintyFraction) {
      this.capacityUncertaintyFraction = capacityUncertaintyFraction;
      return this;
    }

    public OptimizationConfig capacityPercentile(double capacityPercentile) {
      this.capacityPercentile = capacityPercentile;
      return this;
    }

    public OptimizationConfig capacityRangeSpreadFraction(double capacityRangeSpreadFraction) {
      this.capacityRangeSpreadFraction = capacityRangeSpreadFraction;
      return this;
    }

    public OptimizationConfig utilizationLimitForName(String equipmentName, double limit) {
      utilizationLimitsByName.put(equipmentName, limit);
      return this;
    }

    public OptimizationConfig utilizationLimitForType(Class<?> type, double limit) {
      utilizationLimitsByType.put(type, limit);
      return this;
    }

    public OptimizationConfig capacityRuleForName(String equipmentName, CapacityRule rule) {
      capacityRulesByName.put(equipmentName, rule);
      return this;
    }

    public OptimizationConfig capacityRuleForType(Class<?> type, CapacityRule rule) {
      capacityRulesByType.put(type, rule);
      return this;
    }

    public OptimizationConfig capacityRangeForName(String equipmentName, CapacityRange range) {
      capacityRangesByName.put(equipmentName, range);
      return this;
    }

    public OptimizationConfig capacityRangeForType(Class<?> type, CapacityRange range) {
      capacityRangesByType.put(type, range);
      return this;
    }

    public OptimizationConfig equipmentConstraintRule(EquipmentConstraintRule rule) {
      equipmentConstraintRules.add(rule);
      return this;
    }

    public OptimizationConfig enableCaching(boolean enableCaching) {
      this.enableCaching = enableCaching;
      return this;
    }

    public int getSwarmSize() {
      return swarmSize;
    }

    public double getInertiaWeight() {
      return inertiaWeight;
    }

    public double getCognitiveWeight() {
      return cognitiveWeight;
    }

    public double getSocialWeight() {
      return socialWeight;
    }

    public double getColumnFsFactorLimit() {
      return columnFsFactorLimit;
    }
  }

  /** Scenario definition to enable side-by-side optimization comparisons. */
  public static final class ScenarioRequest {
    private final String name;
    private final ProcessSystem process;
    private final StreamInterface feedStream;
    private final List<ManipulatedVariable> variables;
    private final OptimizationConfig config;
    private final List<OptimizationObjective> objectives;
    private final List<OptimizationConstraint> constraints;

    public ScenarioRequest(String name, ProcessSystem process, StreamInterface feedStream,
        OptimizationConfig config, List<OptimizationObjective> objectives,
        List<OptimizationConstraint> constraints) {
      this.name = Objects.requireNonNull(name, "Scenario name is required");
      this.process = Objects.requireNonNull(process, "Scenario process is required");
      this.feedStream = Objects.requireNonNull(feedStream, "Scenario feed stream is required");
      this.variables = Collections.emptyList();
      this.config = Objects.requireNonNull(config, "Scenario config is required");
      this.objectives = objectives == null ? Collections.emptyList() : new ArrayList<>(objectives);
      this.constraints =
          constraints == null ? Collections.emptyList() : new ArrayList<>(constraints);
    }

    public ScenarioRequest(String name, ProcessSystem process, List<ManipulatedVariable> variables,
        OptimizationConfig config, List<OptimizationObjective> objectives,
        List<OptimizationConstraint> constraints) {
      this.name = Objects.requireNonNull(name, "Scenario name is required");
      this.process = Objects.requireNonNull(process, "Scenario process is required");
      this.feedStream = null;
      this.variables = variables == null ? Collections.emptyList() : new ArrayList<>(variables);
      this.config = Objects.requireNonNull(config, "Scenario config is required");
      this.objectives = objectives == null ? Collections.emptyList() : new ArrayList<>(objectives);
      this.constraints =
          constraints == null ? Collections.emptyList() : new ArrayList<>(constraints);
    }

    public String getName() {
      return name;
    }

    public ProcessSystem getProcess() {
      return process;
    }

    public StreamInterface getFeedStream() {
      return feedStream;
    }

    public List<ManipulatedVariable> getVariables() {
      return variables;
    }

    public OptimizationConfig getConfig() {
      return config;
    }

    public List<OptimizationObjective> getObjectives() {
      return objectives;
    }

    public List<OptimizationConstraint> getConstraints() {
      return constraints;
    }
  }

  /** Definition of a manipulated decision variable. */
  public static final class ManipulatedVariable {
    private final String name;
    private final double lowerBound;
    private final double upperBound;
    private final String unit;
    private final java.util.function.BiConsumer<ProcessSystem, Double> setter;

    public ManipulatedVariable(String name, double lowerBound, double upperBound, String unit,
        java.util.function.BiConsumer<ProcessSystem, Double> setter) {
      this.name = Objects.requireNonNull(name, "Variable name is required");
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
      this.setter = Objects.requireNonNull(setter, "Variable setter is required");
    }

    public String getName() {
      return name;
    }

    public double getLowerBound() {
      return lowerBound;
    }

    public double getUpperBound() {
      return upperBound;
    }

    public String getUnit() {
      return unit;
    }

    public void apply(ProcessSystem process, double value) {
      setter.accept(process, value);
    }
  }

  /** Optimization result paired with a scenario name to aid reporting/comparison. */
  public static final class ScenarioResult {
    private final String name;
    private final OptimizationResult result;

    public ScenarioResult(String name, OptimizationResult result) {
      this.name = name;
      this.result = result;
    }

    public String getName() {
      return name;
    }

    public OptimizationResult getResult() {
      return result;
    }
  }

  /** Definition of a KPI to report alongside scenario comparisons. */
  public static final class ScenarioKpi {
    private final String name;
    private final String unit;
    private final java.util.function.ToDoubleFunction<OptimizationResult> metric;

    public ScenarioKpi(String name, String unit,
        java.util.function.ToDoubleFunction<OptimizationResult> metric) {
      this.name = Objects.requireNonNull(name, "name");
      this.unit = unit;
      this.metric = Objects.requireNonNull(metric, "metric");
    }

    public String getName() {
      return name;
    }

    public String getUnit() {
      return unit;
    }

    public double evaluate(OptimizationResult result) {
      return metric.applyAsDouble(result);
    }

    /** Convenience KPI for reporting optimal rate. */
    public static ScenarioKpi optimalRate(String unitLabel) {
      return new ScenarioKpi("optimalRate", unitLabel, OptimizationResult::getOptimalRate);
    }

    /** Convenience KPI for reporting solver score. */
    public static ScenarioKpi score() {
      return new ScenarioKpi("score", null, OptimizationResult::getScore);
    }

    /** KPI that returns a specific objective value by name. */
    public static ScenarioKpi objectiveValue(String objectiveName) {
      return new ScenarioKpi(objectiveName, null,
          result -> result.getObjectiveValues().getOrDefault(objectiveName, Double.NaN));
    }
  }

  /** Per-scenario KPI values and deltas versus the baseline scenario. */
  public static final class ScenarioComparisonResult {
    private final String baselineScenario;
    private final List<ScenarioResult> scenarioResults;
    private final Map<String, Map<String, Double>> kpiValues;
    private final Map<String, Map<String, Double>> kpiDeltas;

    public ScenarioComparisonResult(String baselineScenario, List<ScenarioResult> scenarioResults,
        Map<String, Map<String, Double>> kpiValues, Map<String, Map<String, Double>> kpiDeltas) {
      this.baselineScenario = Objects.requireNonNull(baselineScenario, "baselineScenario");
      this.scenarioResults = new ArrayList<>(Objects.requireNonNull(scenarioResults, "results"));
      this.kpiValues = new HashMap<>(Objects.requireNonNull(kpiValues, "kpiValues"));
      this.kpiDeltas = new HashMap<>(Objects.requireNonNull(kpiDeltas, "kpiDeltas"));
    }

    public String getBaselineScenario() {
      return baselineScenario;
    }

    public List<ScenarioResult> getScenarioResults() {
      return scenarioResults;
    }

    public Map<String, Map<String, Double>> getKpiValues() {
      return kpiValues;
    }

    public Map<String, Map<String, Double>> getKpiDeltas() {
      return kpiDeltas;
    }
  }

  /** Result of a single iteration. */
  private static final class Evaluation {
    private final double bottleneckUtilization;
    private final ProcessEquipmentInterface bottleneck;
    private final List<UtilizationRecord> utilizationRecords;
    private final List<ConstraintStatus> constraintStatuses;
    private final Map<String, Double> objectiveValues;
    private final Map<String, Double> decisionVariables;
    private final boolean utilizationWithinLimits;
    private final boolean hardOk;
    private final double score;

    Evaluation(double bottleneckUtilization, ProcessEquipmentInterface bottleneck,
        List<UtilizationRecord> utilizationRecords, List<ConstraintStatus> constraintStatuses,
        Map<String, Double> objectiveValues, Map<String, Double> decisionVariables,
        boolean utilizationWithinLimits, boolean hardOk, double score) {
      this.bottleneckUtilization = bottleneckUtilization;
      this.bottleneck = bottleneck;
      this.utilizationRecords = utilizationRecords;
      this.constraintStatuses = constraintStatuses;
      this.objectiveValues = objectiveValues;
      this.decisionVariables = decisionVariables == null ? Map.of()
          : new HashMap<>(decisionVariables);
      this.utilizationWithinLimits = utilizationWithinLimits;
      this.hardOk = hardOk;
      this.score = score;
    }

    double bottleneckUtilization() {
      return bottleneckUtilization;
    }

    ProcessEquipmentInterface bottleneck() {
      return bottleneck;
    }

    List<UtilizationRecord> utilizationRecords() {
      return utilizationRecords;
    }

    List<ConstraintStatus> constraintStatuses() {
      return constraintStatuses;
    }

    Map<String, Double> objectiveValues() {
      return objectiveValues;
    }

    Map<String, Double> decisionVariables() {
      return decisionVariables;
    }

    boolean utilizationWithinLimits() {
      return utilizationWithinLimits;
    }

    boolean hardOk() {
      return hardOk;
    }

    double score() {
      return score;
    }
  }

  /**
   * Optimize the feed stream rate of a process to respect utilization limits and constraints.
   *
   * @param process the process model to evaluate
   * @param feedStream the feed stream whose flow rate will be adjusted
   * @param config optimizer configuration
   * @param objectives list of objectives (optional) to compute a weighted score for reporting
   * @param constraints list of constraints with optional penalties
   * @return optimization result
   */
  public OptimizationResult optimize(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    List<OptimizationObjective> safeObjectives = objectives == null ? Collections.emptyList()
        : new ArrayList<>(objectives);
    List<OptimizationConstraint> safeConstraints = constraints == null ? Collections.emptyList()
        : new ArrayList<>(constraints);

    List<IterationRecord> iterationHistory = new ArrayList<>();

    if (config.searchMode == SearchMode.GOLDEN_SECTION_SCORE) {
      return goldenSectionSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.NELDER_MEAD_SCORE) {
      return nelderMeadSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.PARTICLE_SWARM_SCORE) {
      return particleSwarmSearch(process, feedStream, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    return binaryFeasibilitySearch(process, feedStream, config, safeObjectives, safeConstraints,
        iterationHistory);
  }

  /**
   * Optimize multiple manipulated variables (feeds, pressures, temperatures) using multi-dimensional
   * search strategies.
   */
  public OptimizationResult optimize(ProcessSystem process, List<ManipulatedVariable> variables,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(variables, "Variables are required");
    Objects.requireNonNull(config, "OptimizationConfig is required");
    if (variables.isEmpty()) {
      throw new IllegalArgumentException("At least one variable is required");
    }
    List<OptimizationObjective> safeObjectives = objectives == null ? Collections.emptyList()
        : new ArrayList<>(objectives);
    List<OptimizationConstraint> safeConstraints = constraints == null ? Collections.emptyList()
        : new ArrayList<>(constraints);
    List<IterationRecord> iterationHistory = new ArrayList<>();

    if (variables.size() > 1 && (config.searchMode == SearchMode.BINARY_FEASIBILITY
        || config.searchMode == SearchMode.GOLDEN_SECTION_SCORE)) {
      throw new IllegalArgumentException(
          "Binary and golden-section searches support only one decision variable");
    }

    if (variables.size() == 1 && config.searchMode == SearchMode.BINARY_FEASIBILITY) {
      return binaryFeasibilitySearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (variables.size() == 1 && config.searchMode == SearchMode.GOLDEN_SECTION_SCORE) {
      return goldenSectionSearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    if (config.searchMode == SearchMode.NELDER_MEAD_SCORE) {
      return nelderMeadSearch(process, variables, config, safeObjectives, safeConstraints,
          iterationHistory);
    }
    return particleSwarmSearch(process, variables, config, safeObjectives, safeConstraints,
        iterationHistory);
  }

  /**
   * Optimize a collection of named scenarios and return results for side-by-side comparison.
   *
   * @param scenarios scenarios containing process, feed, config, objectives, and constraints
   * @return list of scenario results in the same order as provided
   */
  public List<ScenarioResult> optimizeScenarios(List<ScenarioRequest> scenarios) {
    Objects.requireNonNull(scenarios, "scenarios are required");
    List<ScenarioResult> results = new ArrayList<>();
    for (ScenarioRequest scenario : scenarios) {
      OptimizationResult result;
      if (scenario.getVariables() != null && !scenario.getVariables().isEmpty()) {
        result = optimize(scenario.getProcess(), scenario.getVariables(), scenario.getConfig(),
            scenario.getObjectives(), scenario.getConstraints());
      } else {
        result = optimize(scenario.getProcess(), scenario.getFeedStream(), scenario.getConfig(),
            scenario.getObjectives(), scenario.getConstraints());
      }
      results.add(new ScenarioResult(scenario.getName(), result));
    }
    return results;
  }

  /**
   * Optimize multiple scenarios and compute KPI deltas versus the baseline (first) scenario.
   *
   * @param scenarios list of scenarios to optimize; first entry is treated as baseline
   * @param kpis KPIs to compute per scenario (optional)
   * @return comparison result with KPI deltas and raw results
   */
  public ScenarioComparisonResult compareScenarios(List<ScenarioRequest> scenarios,
      List<ScenarioKpi> kpis) {
    Objects.requireNonNull(scenarios, "scenarios are required");
    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException("At least one scenario is required");
    }
    List<ScenarioKpi> safeKpis = kpis == null ? List.of() : new ArrayList<>(kpis);
    List<ScenarioResult> results = optimizeScenarios(scenarios);
    Map<String, Map<String, Double>> kpiValues = new LinkedHashMap<>();
    Map<String, Map<String, Double>> kpiDeltas = new LinkedHashMap<>();

    ScenarioResult baseline = results.get(0);
    Map<String, Double> baselineValues = new LinkedHashMap<>();
    for (ScenarioKpi kpi : safeKpis) {
      baselineValues.put(kpi.getName(), kpi.evaluate(baseline.getResult()));
    }

    for (ScenarioResult scenarioResult : results) {
      Map<String, Double> scenarioKpis = new LinkedHashMap<>();
      Map<String, Double> scenarioDelta = new LinkedHashMap<>();
      for (ScenarioKpi kpi : safeKpis) {
        double value = kpi.evaluate(scenarioResult.getResult());
        scenarioKpis.put(kpi.getName(), value);
        double delta = value - baselineValues.getOrDefault(kpi.getName(), 0.0);
        scenarioDelta.put(kpi.getName(), delta);
      }
      kpiValues.put(scenarioResult.getName(), scenarioKpis);
      kpiDeltas.put(scenarioResult.getName(), scenarioDelta);
    }

    return new ScenarioComparisonResult(baseline.getName(), results, kpiValues, kpiDeltas);
  }

  /**
   * Convenience wrapper to maximize throughput with optional constraints and custom search config.
   *
   * @param process process system to run
   * @param feedStream feed stream that will be adjusted
   * @param lowerBound lower bound on the manipulated feed rate
   * @param upperBound upper bound on the manipulated feed rate
   * @param rateUnit engineering unit for rate
   * @param additionalConstraints optional hard/soft constraints
   * @return optimization result with utilization and constraint history
   */
  public OptimizationResult optimizeThroughput(ProcessSystem process, StreamInterface feedStream,
      double lowerBound, double upperBound, String rateUnit,
      List<OptimizationConstraint> additionalConstraints) {
    OptimizationConfig config = new OptimizationConfig(lowerBound, upperBound).rateUnit(rateUnit);
    OptimizationObjective throughput = new OptimizationObjective("throughput",
        proc -> feedStream.getFlowRate(rateUnit), 1.0, ObjectiveType.MAXIMIZE);
    return optimize(process, feedStream, config, List.of(throughput), additionalConstraints);
  }

  /** Render a compact Markdown table describing utilization per unit. */
  public static String formatUtilizationTable(List<UtilizationRecord> records) {
    StringBuilder sb = new StringBuilder();
    sb.append("| Equipment | Duty | Capacity | Utilization | Limit |\n");
    sb.append("|---|---|---|---|---|\n");
    for (UtilizationRecord record : records) {
      sb.append("|").append(record.getEquipmentName()).append("|")
          .append(String.format("%.2f", record.getCapacityDuty())).append("|")
          .append(String.format("%.2f", record.getCapacityMax())).append("|")
          .append(String.format("%.3f", record.getUtilization())).append("|")
          .append(String.format("%.3f", record.getUtilizationLimit())).append("|\n");
    }
    return sb.toString();
  }

  /** Render scenario KPIs and bottleneck information side-by-side. */
  public static String formatScenarioComparisonTable(ScenarioComparisonResult comparison,
      List<ScenarioKpi> kpis) {
    Objects.requireNonNull(comparison, "comparison");
    List<ScenarioKpi> safeKpis = kpis == null ? List.of() : new ArrayList<>(kpis);
    StringBuilder sb = new StringBuilder();
    sb.append("| Scenario | Feasible | Optimal Rate | Bottleneck | Score |");
    for (ScenarioKpi kpi : safeKpis) {
      String label = kpi.getUnit() == null ? kpi.getName()
          : kpi.getName() + " (" + kpi.getUnit() + ")";
      sb.append(label).append(" |");
    }
    sb.append("\n|---|---|---|---|---|");
    for (int i = 0; i < safeKpis.size(); i++) {
      sb.append("---|");
    }
    sb.append("\n");

    for (ScenarioResult scenarioResult : comparison.getScenarioResults()) {
      OptimizationResult result = scenarioResult.getResult();
      sb.append("|").append(scenarioResult.getName()).append("|")
          .append(result.isFeasible() ? "yes" : "no").append("|")
          .append(String.format("%.3f %s", result.getOptimalRate(), result.getRateUnit()))
          .append("|")
          .append(result.getBottleneck() != null ? result.getBottleneck().getName() : "")
          .append("|")
          .append(String.format("%.3f", result.getScore())).append("|");
      for (ScenarioKpi kpi : safeKpis) {
        double value = comparison.getKpiValues().getOrDefault(scenarioResult.getName(), Map.of())
            .getOrDefault(kpi.getName(), Double.NaN);
        double delta = comparison.getKpiDeltas().getOrDefault(scenarioResult.getName(), Map.of())
            .getOrDefault(kpi.getName(), 0.0);
        sb.append(String.format("%.3f (%.3f)", value, delta)).append("|");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /** Series-friendly representation of utilization across iterations for plotting/reporting. */
  public static final class UtilizationSeries {
    private final String equipmentName;
    private final List<Double> utilizations;
    private final List<Boolean> bottleneckFlags;
    private final double utilizationLimit;

    public UtilizationSeries(String equipmentName, List<Double> utilizations,
        List<Boolean> bottleneckFlags, double utilizationLimit) {
      this.equipmentName = equipmentName;
      this.utilizations = new ArrayList<>(utilizations);
      this.bottleneckFlags = new ArrayList<>(bottleneckFlags);
      this.utilizationLimit = utilizationLimit;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public List<Double> getUtilizations() {
      return new ArrayList<>(utilizations);
    }

    public List<Boolean> getBottleneckFlags() {
      return new ArrayList<>(bottleneckFlags);
    }

    public double getUtilizationLimit() {
      return utilizationLimit;
    }
  }

  /**
   * Build utilization series for each equipment across the provided iteration history to facilitate
   * charting or CSV export.
   */
  public static List<UtilizationSeries> buildUtilizationSeries(
      List<IterationRecord> iterationHistory) {
    Objects.requireNonNull(iterationHistory, "iterationHistory is required");
    Map<String, List<Double>> utilizationByEquipment = new LinkedHashMap<>();
    Map<String, List<Boolean>> bottleneckFlags = new LinkedHashMap<>();
    Map<String, Double> limits = new HashMap<>();

    int iterationIndex = 0;
    for (IterationRecord record : iterationHistory) {
      for (UtilizationRecord utilization : record.getUtilizations()) {
        utilizationByEquipment.computeIfAbsent(utilization.getEquipmentName(), k -> new ArrayList<>())
            .add(utilization.getUtilization());
        bottleneckFlags.computeIfAbsent(utilization.getEquipmentName(), k -> new ArrayList<>());
        limits.putIfAbsent(utilization.getEquipmentName(), utilization.getUtilizationLimit());
      }
      for (Map.Entry<String, List<Double>> entry : utilizationByEquipment.entrySet()) {
        if (entry.getValue().size() < iterationIndex + 1) {
          entry.getValue().add(Double.NaN);
        }
      }
      for (Map.Entry<String, List<Boolean>> entry : bottleneckFlags.entrySet()) {
        boolean isBottleneck = record.getBottleneckName() != null
            && record.getBottleneckName().equals(entry.getKey());
        entry.getValue().add(isBottleneck);
      }
      iterationIndex++;
    }

    return utilizationByEquipment.entrySet().stream()
        .map(entry -> new UtilizationSeries(entry.getKey(), entry.getValue(),
            bottleneckFlags.getOrDefault(entry.getKey(), List.of()),
            limits.getOrDefault(entry.getKey(), DEFAULT_UTILIZATION_LIMIT)))
        .collect(Collectors.toList());
  }

  /**
   * Render a compact Markdown timeline showing bottlenecks across iterations.
   */
  public static String formatUtilizationTimeline(List<IterationRecord> iterationHistory) {
    StringBuilder sb = new StringBuilder();
    sb.append("| Iteration | Bottleneck | Utilization | Limit | Feasible |\n");
    sb.append("|---|---|---|---|---|\n");
    int iteration = 1;
    for (IterationRecord record : iterationHistory) {
      String bottleneckName = record.getBottleneckName() == null ? "" : record.getBottleneckName();
      UtilizationRecord bottleneck = record.getUtilizations().stream()
          .filter(u -> u.getEquipmentName().equals(bottleneckName)).findFirst().orElse(null);
      double utilization = bottleneck == null ? Double.NaN : bottleneck.getUtilization();
      double limit = bottleneck == null ? Double.NaN : bottleneck.getUtilizationLimit();
      sb.append("|").append(iteration).append("|").append(bottleneckName).append("|")
          .append(String.format("%.3f", utilization)).append("|")
          .append(String.format("%.3f", limit)).append("|")
          .append(record.isFeasible() ? "yes" : "no").append("|\n");
      iteration++;
    }
    return sb.toString();
  }

  private OptimizationResult toResult(double rate, String unit, int iteration,
      Evaluation evaluation, List<IterationRecord> iterationHistory) {
    return new OptimizationResult(rate, unit, evaluation.decisionVariables(),
        evaluation.bottleneck(), evaluation.bottleneckUtilization(), evaluation.utilizationRecords(),
        evaluation.objectiveValues(), evaluation.constraintStatuses(),
        evaluation.utilizationWithinLimits() && evaluation.hardOk(), evaluation.score(), iteration,
        new ArrayList<>(iterationHistory));
  }

  /**
   * Convenience wrapper that derives reasonable bounds from the current feed rate and returns a
   * concise summary (max rate, limiting equipment, utilization margin).
   */
  public OptimizationSummary quickOptimize(ProcessSystem process, StreamInterface feedStream) {
    return quickOptimize(process, feedStream, "kg/hr", Collections.emptyList());
  }

  /**
   * Convenience wrapper that derives reasonable bounds from the current feed rate and returns a
   * concise summary (max rate, limiting equipment, utilization margin).
   */
  public OptimizationSummary quickOptimize(ProcessSystem process, StreamInterface feedStream,
      String rateUnit, List<OptimizationConstraint> constraints) {
    Objects.requireNonNull(process, "ProcessSystem is required");
    Objects.requireNonNull(feedStream, "Feed stream is required");
    double baseRate = Math.max(1.0, feedStream.getFlowRate(rateUnit));
    double lower = 0.0;
    double upper = baseRate * 2.0;
    OptimizationConfig config = new OptimizationConfig(lower, upper).rateUnit(rateUnit)
        .tolerance(baseRate * 0.005).maxIterations(40);
    OptimizationResult result = optimize(process, feedStream, config, List.of(), constraints);
    UtilizationRecord bottleneck = result.getUtilizationRecords().stream()
        .filter(record -> result.getBottleneck() != null
            && record.getEquipmentName().equals(result.getBottleneck().getName()))
        .findFirst()
        .orElse(result.getUtilizationRecords().isEmpty() ? null
            : result.getUtilizationRecords().get(0));
    double utilization = bottleneck == null ? 0.0 : bottleneck.getUtilization();
    double utilizationLimit = bottleneck == null ? 0.0 : bottleneck.getUtilizationLimit();
    double margin = utilizationLimit - utilization;
    return new OptimizationSummary(result.getOptimalRate(), result.getRateUnit(),
        result.getBottleneck() == null ? null : result.getBottleneck().getName(), utilization,
        utilizationLimit, margin, result.isFeasible(), result.getDecisionVariables(),
        result.getUtilizationRecords(), result.getConstraintStatuses());
  }

  private Evaluation evaluateProcess(ProcessSystem process, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      Map<String, Double> decisionVariables) {
    List<UtilizationRecord> utilizations = new ArrayList<>();
    double maxUtilization = -1.0;
    ProcessEquipmentInterface bottleneck = null;
    List<OptimizationConstraint> equipmentConstraints = new ArrayList<>(constraints);

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      for (EquipmentConstraintRule rule : config.equipmentConstraintRules) {
        if (rule.matches(unit)) {
          equipmentConstraints.add(rule.toConstraint(unit));
        }
      }
    }

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      CapacityRule capacityRule = determineCapacityRule(unit, config);
      double capacity = capacityRule.max(unit);
      CapacityRange range = determineCapacityRange(unit, config);
      if (range == null && config.capacityRangeSpreadFraction > 0.0) {
        double spread = config.capacityRangeSpreadFraction;
        range = new CapacityRange(capacity * (1.0 - spread), capacity,
            capacity * (1.0 + spread));
      }
      if (range != null) {
        capacity = range.atPercentile(config.getCapacityPercentile());
      }
      if (config.capacityUncertaintyFraction > 0.0) {
        capacity = capacity * (1.0 - config.capacityUncertaintyFraction);
      }
      double duty = capacityRule.duty(unit);
      if (capacity > 1e-12) {
        double utilization = duty / capacity;
        double limit = determineUtilizationLimit(unit, config)
            * (1.0 - config.utilizationMarginFraction);
        utilizations
            .add(new UtilizationRecord(unit.getName(), duty, capacity, utilization, limit));
        if (utilization > maxUtilization) {
          maxUtilization = utilization;
          bottleneck = unit;
        }
      }
    }

    boolean utilizationOk = utilizations.stream()
        .noneMatch(record -> record.getUtilization() > record.getUtilizationLimit());

    List<ConstraintStatus> constraintStatuses = new ArrayList<>();
    boolean hardOk = true;
    double penalty = 0.0;
    for (OptimizationConstraint constraint : equipmentConstraints) {
      double margin = constraint.margin(process);
      ConstraintStatus status = new ConstraintStatus(constraint.getName(),
          constraint.getSeverity(), margin, constraint.getPenaltyWeight(),
          constraint.getDescription());
      constraintStatuses.add(status);
      if (status.violated()) {
        if (status.getSeverity() == ConstraintSeverity.HARD) {
          hardOk = false;
        } else {
          penalty += status.getPenaltyWeight() * Math.abs(margin);
        }
      }
    }

    Map<String, Double> objectiveValues = new HashMap<>();
    double score = 0.0;
    for (OptimizationObjective objective : objectives) {
      double value = objective.evaluate(process);
      objectiveValues.put(objective.getName(), value);
      double signedValue = objective.getType() == ObjectiveType.MAXIMIZE ? value : -value;
      score += signedValue * objective.getWeight();
    }
    score -= penalty;

    return new Evaluation(maxUtilization, bottleneck, utilizations, constraintStatuses,
        objectiveValues, decisionVariables, utilizationOk, hardOk, score);
  }

  private OptimizationResult binaryFeasibilitySearch(ProcessSystem process,
      StreamInterface feedStream, OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double low = config.lowerBound;
    double high = config.upperBound;
    OptimizationResult bestResult = null;
    int iteration = 0;

    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      double candidate = 0.5 * (low + high);
      Evaluation evaluation = evaluateCandidate(process, feedStream, config, objectives,
          constraints, candidate, cache);
      boolean feasible = evaluation.utilizationWithinLimits() && evaluation.hardOk();
      recordIteration(iterationHistory, candidate, config.rateUnit, evaluation, feasible);
      if (feasible) {
        bestResult = toResult(candidate, config.rateUnit, iteration, evaluation, iterationHistory);
        low = candidate;
      } else {
        high = candidate;
      }
      iteration++;
    }

    if (bestResult == null) {
      Evaluation evaluation = evaluateCandidate(process, feedStream, config, objectives,
          constraints, low, cache);
      recordIteration(iterationHistory, low, config.rateUnit, evaluation,
          evaluation.utilizationWithinLimits() && evaluation.hardOk());
      bestResult = toResult(low, config.rateUnit, iteration, evaluation, iterationHistory);
    }
    return bestResult;
  }

  private OptimizationResult binaryFeasibilitySearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    Map<String, Evaluation> cache = new HashMap<>();
    ManipulatedVariable variable = variables.get(0);
    double low = variable.getLowerBound();
    double high = variable.getUpperBound();
    OptimizationResult bestResult = null;
    int iteration = 0;
    String unit = variable.getUnit() != null ? variable.getUnit() : config.rateUnit;

    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      double candidateValue = 0.5 * (low + high);
      Evaluation evaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          new double[] {candidateValue}, cache);
      boolean feasible = evaluation.utilizationWithinLimits() && evaluation.hardOk();
      recordIteration(iterationHistory, candidateValue, unit, evaluation, feasible);
      if (feasible) {
        bestResult = toResult(candidateValue, unit, iteration, evaluation, iterationHistory);
        low = candidateValue;
      } else {
        high = candidateValue;
      }
      iteration++;
    }

    if (bestResult == null) {
      Evaluation evaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          new double[] {low}, cache);
      recordIteration(iterationHistory, low, unit, evaluation,
          evaluation.utilizationWithinLimits() && evaluation.hardOk());
      bestResult = toResult(low, unit, iteration, evaluation, iterationHistory);
    }
    return bestResult;
  }

  private OptimizationResult goldenSectionSearch(ProcessSystem process,
      StreamInterface feedStream, OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double a = config.lowerBound;
    double b = config.upperBound;
    double phi = 0.5 * (Math.sqrt(5) - 1); // ~0.618

    double c = b - phi * (b - a);
    double d = a + phi * (b - a);

    Evaluation evalC = evaluateCandidate(process, feedStream, config, objectives, constraints, c,
        cache);
    recordIteration(iterationHistory, c, config.rateUnit, evalC,
        evalC.utilizationWithinLimits() && evalC.hardOk());
    Evaluation evalD = evaluateCandidate(process, feedStream, config, objectives, constraints, d,
        cache);
    recordIteration(iterationHistory, d, config.rateUnit, evalD,
        evalD.utilizationWithinLimits() && evalD.hardOk());

    Evaluation bestEval = null;
    double bestRate = a;
    int iteration = 0;

    while (iteration < config.maxIterations && Math.abs(b - a) > config.tolerance) {
      double scoreC = feasibilityScore(evalC);
      double scoreD = feasibilityScore(evalD);

      if (scoreC < scoreD) {
        a = c;
        c = d;
        evalC = evalD;
        d = a + phi * (b - a);
        evalD = evaluateCandidate(process, feedStream, config, objectives, constraints, d, cache);
        recordIteration(iterationHistory, d, config.rateUnit, evalD,
            evalD.utilizationWithinLimits() && evalD.hardOk());
      } else {
        b = d;
        d = c;
        evalD = evalC;
        c = b - phi * (b - a);
        evalC = evaluateCandidate(process, feedStream, config, objectives, constraints, c, cache);
        recordIteration(iterationHistory, c, config.rateUnit, evalC,
            evalC.utilizationWithinLimits() && evalC.hardOk());
      }

      if (evalC.utilizationWithinLimits() && evalC.hardOk()
          && (bestEval == null || evalC.score() > bestEval.score())) {
        bestEval = evalC;
        bestRate = c;
      }
      if (evalD.utilizationWithinLimits() && evalD.hardOk()
          && (bestEval == null || evalD.score() > bestEval.score())) {
        bestEval = evalD;
        bestRate = d;
      }
      iteration++;
    }

    if (bestEval == null) {
      bestEval = evalC;
      bestRate = c;
    }

    return toResult(bestRate, config.rateUnit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult goldenSectionSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    double phi = (1 + Math.sqrt(5)) / 2;
    Map<String, Evaluation> cache = new HashMap<>();
    ManipulatedVariable variable = variables.get(0);
    double low = variable.getLowerBound();
    double high = variable.getUpperBound();
    String unit = variable.getUnit() != null ? variable.getUnit() : config.rateUnit;

    double c = high - (high - low) / phi;
    double d = low + (high - low) / phi;

    Evaluation evalC = evaluateCandidate(process, variables, config, objectives, constraints,
        new double[] {c}, cache);
    Evaluation evalD = evaluateCandidate(process, variables, config, objectives, constraints,
        new double[] {d}, cache);
    recordIteration(iterationHistory, c, unit, evalC, evalC.utilizationWithinLimits()
        && evalC.hardOk());
    recordIteration(iterationHistory, d, unit, evalD, evalD.utilizationWithinLimits()
        && evalD.hardOk());

    int iteration = 0;
    while (iteration < config.maxIterations && Math.abs(high - low) > config.tolerance) {
      if (feasibilityScore(evalC) > feasibilityScore(evalD)) {
        high = d;
        d = c;
        evalD = evalC;
        c = high - (high - low) / phi;
        evalC = evaluateCandidate(process, variables, config, objectives, constraints,
            new double[] {c}, cache);
        recordIteration(iterationHistory, c, unit, evalC,
            evalC.utilizationWithinLimits() && evalC.hardOk());
      } else {
        low = c;
        c = d;
        evalC = evalD;
        d = low + (high - low) / phi;
        evalD = evaluateCandidate(process, variables, config, objectives, constraints,
            new double[] {d}, cache);
        recordIteration(iterationHistory, d, unit, evalD,
            evalD.utilizationWithinLimits() && evalD.hardOk());
      }
      iteration++;
    }

    Evaluation bestEval = feasibilityScore(evalC) > feasibilityScore(evalD) ? evalC : evalD;
    double bestRate = feasibilityScore(evalC) > feasibilityScore(evalD) ? c : d;
    return toResult(bestRate, unit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult nelderMeadSearch(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    double[] simplex = new double[] {config.lowerBound, config.upperBound};
    Evaluation eval0 = evaluateCandidate(process, feedStream, config, objectives, constraints,
        simplex[0], cache);
    Evaluation eval1 = evaluateCandidate(process, feedStream, config, objectives, constraints,
        simplex[1], cache);
    recordIteration(iterationHistory, simplex[0], config.rateUnit, eval0,
        eval0.utilizationWithinLimits() && eval0.hardOk());
    recordIteration(iterationHistory, simplex[1], config.rateUnit, eval1,
        eval1.utilizationWithinLimits() && eval1.hardOk());

    int iteration = 0;
    while (iteration < config.maxIterations
        && Math.abs(simplex[1] - simplex[0]) > config.tolerance) {
      int bestIndex = feasibilityScore(eval0) >= feasibilityScore(eval1) ? 0 : 1;
      int worstIndex = bestIndex == 0 ? 1 : 0;
      double centroid = simplex[bestIndex];
      double reflected = centroid + (centroid - simplex[worstIndex]);
      double clamped = Math.max(config.lowerBound, Math.min(config.upperBound, reflected));
      Evaluation reflectedEval = evaluateCandidate(process, feedStream, config, objectives,
          constraints, clamped, cache);
      recordIteration(iterationHistory, clamped, config.rateUnit, reflectedEval,
          reflectedEval.utilizationWithinLimits() && reflectedEval.hardOk());

      if (feasibilityScore(reflectedEval) > feasibilityScore(eval0)
          && feasibilityScore(reflectedEval) > feasibilityScore(eval1)) {
        simplex[worstIndex] = clamped;
        if (worstIndex == 0) {
          eval0 = reflectedEval;
        } else {
          eval1 = reflectedEval;
        }
      } else {
        double contracted = 0.5 * (simplex[worstIndex] + centroid);
        Evaluation contractedEval = evaluateCandidate(process, feedStream, config, objectives,
            constraints, contracted, cache);
        recordIteration(iterationHistory, contracted, config.rateUnit, contractedEval,
            contractedEval.utilizationWithinLimits() && contractedEval.hardOk());
        simplex[worstIndex] = contracted;
        if (worstIndex == 0) {
          eval0 = contractedEval;
        } else {
          eval1 = contractedEval;
        }
      }
      iteration++;
    }

    Evaluation bestEval = feasibilityScore(eval0) >= feasibilityScore(eval1) ? eval0 : eval1;
    double bestRate = feasibilityScore(eval0) >= feasibilityScore(eval1) ? simplex[0] : simplex[1];
    return toResult(bestRate, config.rateUnit, iteration, bestEval, iterationHistory);
  }

  private OptimizationResult nelderMeadSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    int dim = variables.size();
    int simplexSize = dim + 1;
    double[][] simplex = new double[simplexSize][dim];
    Evaluation[] evaluations = new Evaluation[simplexSize];
    Map<String, Evaluation> cache = new HashMap<>();
    String unit = variables.get(0).getUnit() != null ? variables.get(0).getUnit() : config.rateUnit;

    for (int i = 0; i < simplexSize; i++) {
      for (int j = 0; j < dim; j++) {
        ManipulatedVariable var = variables.get(j);
        double center = 0.5 * (var.getLowerBound() + var.getUpperBound());
        double span = Math.max(config.tolerance, 0.1 * (var.getUpperBound() - var.getLowerBound()));
        simplex[i][j] = Math.max(var.getLowerBound(),
            Math.min(var.getUpperBound(), center + (i == j + 1 ? span : 0.0)));
      }
      evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
          simplex[i], cache);
      recordIteration(iterationHistory, simplex[i][0], unit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      sortSimplexByScore(simplex, evaluations);
      double[] centroid = computeCentroid(simplex, simplexSize - 1);
      double[] worst = simplex[simplexSize - 1];

      double[] reflected = reflect(centroid, worst, 1.0);
      Evaluation reflectedEval = evaluateCandidate(process, variables, config, objectives,
          constraints, reflected, cache);

      if (feasibilityScore(reflectedEval) > feasibilityScore(evaluations[0])) {
        double[] expanded = reflect(centroid, worst, 2.0);
        Evaluation expandedEval = evaluateCandidate(process, variables, config, objectives,
            constraints, expanded, cache);
        if (feasibilityScore(expandedEval) > feasibilityScore(reflectedEval)) {
          simplex[simplexSize - 1] = expanded;
          evaluations[simplexSize - 1] = expandedEval;
        } else {
          simplex[simplexSize - 1] = reflected;
          evaluations[simplexSize - 1] = reflectedEval;
        }
      } else if (feasibilityScore(reflectedEval) > feasibilityScore(evaluations[simplexSize - 2])) {
        simplex[simplexSize - 1] = reflected;
        evaluations[simplexSize - 1] = reflectedEval;
      } else {
        double[] contracted = contract(centroid, worst, 0.5);
        Evaluation contractedEval = evaluateCandidate(process, variables, config, objectives,
            constraints, contracted, cache);
        if (feasibilityScore(contractedEval) > feasibilityScore(evaluations[simplexSize - 1])) {
          simplex[simplexSize - 1] = contracted;
          evaluations[simplexSize - 1] = contractedEval;
        } else {
          shrink(simplex, variables);
          for (int i = 1; i < simplexSize; i++) {
            evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
                simplex[i], cache);
          }
        }
      }

      recordIteration(iterationHistory, simplex[0][0], unit, evaluations[0],
          evaluations[0].utilizationWithinLimits() && evaluations[0].hardOk());
      iteration++;
    }

    sortSimplexByScore(simplex, evaluations);
    return toResult(simplex[0][0], unit, iteration, evaluations[0], iterationHistory);
  }

  private OptimizationResult particleSwarmSearch(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, List<IterationRecord> iterationHistory) {
    Map<Long, Evaluation> cache = new HashMap<>();
    Random random = new Random(0);
    int swarmSize = Math.max(2, config.getSwarmSize());
    double[] positions = new double[swarmSize];
    double[] velocities = new double[swarmSize];
    double[] bestPersonalScores = new double[swarmSize];
    double[] bestPersonalPositions = new double[swarmSize];
    Evaluation[] evaluations = new Evaluation[swarmSize];

    double globalBestScore = Double.NEGATIVE_INFINITY;
    double globalBestPosition = config.lowerBound;
    Evaluation globalBestEvaluation = null;

    for (int i = 0; i < swarmSize; i++) {
      double initPos = config.lowerBound
          + (config.upperBound - config.lowerBound) * ((double) i / (double) swarmSize);
      positions[i] = initPos;
      velocities[i] = 0.0;
      evaluations[i] = evaluateCandidate(process, feedStream, config, objectives, constraints,
          initPos, cache);
      recordIteration(iterationHistory, initPos, config.rateUnit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
      bestPersonalScores[i] = feasibilityScore(evaluations[i]);
      bestPersonalPositions[i] = initPos;
      if (bestPersonalScores[i] > globalBestScore) {
        globalBestScore = bestPersonalScores[i];
        globalBestPosition = initPos;
        globalBestEvaluation = evaluations[i];
      }
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      for (int i = 0; i < swarmSize; i++) {
        double r1 = random.nextDouble();
        double r2 = random.nextDouble();
        velocities[i] = config.getInertiaWeight() * velocities[i]
            + config.getCognitiveWeight() * r1 * (bestPersonalPositions[i] - positions[i])
            + config.getSocialWeight() * r2 * (globalBestPosition - positions[i]);

        positions[i] = Math.max(config.lowerBound,
            Math.min(config.upperBound, positions[i] + velocities[i]));
        evaluations[i] = evaluateCandidate(process, feedStream, config, objectives, constraints,
            positions[i], cache);
        recordIteration(iterationHistory, positions[i], config.rateUnit, evaluations[i],
            evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());

        double score = feasibilityScore(evaluations[i]);
        if (score > bestPersonalScores[i]) {
          bestPersonalScores[i] = score;
          bestPersonalPositions[i] = positions[i];
        }
        if (score > globalBestScore) {
          globalBestScore = score;
          globalBestPosition = positions[i];
          globalBestEvaluation = evaluations[i];
        }
      }
      if (Math.abs(globalBestScore) < 1e-12) {
        break;
      }
      iteration++;
    }

    if (globalBestEvaluation == null) {
      globalBestEvaluation = evaluateCandidate(process, feedStream, config, objectives, constraints,
          config.lowerBound, cache);
      globalBestPosition = config.lowerBound;
    }

    return toResult(globalBestPosition, config.rateUnit, iteration, globalBestEvaluation,
        iterationHistory);
  }

  private OptimizationResult particleSwarmSearch(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      List<IterationRecord> iterationHistory) {
    Map<String, Evaluation> cache = new HashMap<>();
    Random random = new Random(0);
    int swarmSize = Math.max(2, config.getSwarmSize());
    int dim = variables.size();
    String unit = variables.get(0).getUnit() != null ? variables.get(0).getUnit() : config.rateUnit;

    double[][] positions = new double[swarmSize][dim];
    double[][] velocities = new double[swarmSize][dim];
    double[] bestPersonalScores = new double[swarmSize];
    double[][] bestPersonalPositions = new double[swarmSize][dim];
    Evaluation[] evaluations = new Evaluation[swarmSize];

    double globalBestScore = Double.NEGATIVE_INFINITY;
    double[] globalBestPosition = new double[dim];
    Evaluation globalBestEvaluation = null;

    for (int i = 0; i < swarmSize; i++) {
      for (int j = 0; j < dim; j++) {
        ManipulatedVariable var = variables.get(j);
        double init = var.getLowerBound()
            + (var.getUpperBound() - var.getLowerBound()) * random.nextDouble();
        positions[i][j] = init;
        velocities[i][j] = 0.0;
      }
      evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
          positions[i], cache);
      recordIteration(iterationHistory, positions[i][0], unit, evaluations[i],
          evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
      bestPersonalScores[i] = feasibilityScore(evaluations[i]);
      bestPersonalPositions[i] = positions[i].clone();
      if (bestPersonalScores[i] > globalBestScore) {
        globalBestScore = bestPersonalScores[i];
        globalBestPosition = positions[i].clone();
        globalBestEvaluation = evaluations[i];
      }
    }

    int iteration = 0;
    while (iteration < config.maxIterations) {
      for (int i = 0; i < swarmSize; i++) {
        for (int j = 0; j < dim; j++) {
          double r1 = random.nextDouble();
          double r2 = random.nextDouble();
          velocities[i][j] = config.getInertiaWeight() * velocities[i][j]
              + config.getCognitiveWeight() * r1 * (bestPersonalPositions[i][j] - positions[i][j])
              + config.getSocialWeight() * r2 * (globalBestPosition[j] - positions[i][j]);
          ManipulatedVariable var = variables.get(j);
          positions[i][j] = Math.max(var.getLowerBound(),
              Math.min(var.getUpperBound(), positions[i][j] + velocities[i][j]));
        }
        evaluations[i] = evaluateCandidate(process, variables, config, objectives, constraints,
            positions[i], cache);
        recordIteration(iterationHistory, positions[i][0], unit, evaluations[i],
            evaluations[i].utilizationWithinLimits() && evaluations[i].hardOk());
        double score = feasibilityScore(evaluations[i]);
        if (score > bestPersonalScores[i]) {
          bestPersonalScores[i] = score;
          bestPersonalPositions[i] = positions[i].clone();
        }
        if (score > globalBestScore) {
          globalBestScore = score;
          globalBestPosition = positions[i].clone();
          globalBestEvaluation = evaluations[i];
        }
      }
      iteration++;
    }

    if (globalBestEvaluation == null) {
      globalBestEvaluation = evaluateCandidate(process, variables, config, objectives, constraints,
          globalBestPosition, cache);
    }

    return toResult(globalBestPosition[0], unit, iteration, globalBestEvaluation, iterationHistory);
  }

  private void sortSimplexByScore(double[][] simplex, Evaluation[] evaluations) {
    java.util.List<Integer> order = new ArrayList<>();
    for (int i = 0; i < evaluations.length; i++) {
      order.add(i);
    }
    order.sort((a, b) -> Double.compare(feasibilityScore(evaluations[b]),
        feasibilityScore(evaluations[a])));
    double[][] newSimplex = new double[simplex.length][simplex[0].length];
    Evaluation[] newEvals = new Evaluation[evaluations.length];
    for (int i = 0; i < order.size(); i++) {
      newSimplex[i] = simplex[order.get(i)].clone();
      newEvals[i] = evaluations[order.get(i)];
    }
    System.arraycopy(newSimplex, 0, simplex, 0, simplex.length);
    System.arraycopy(newEvals, 0, evaluations, 0, evaluations.length);
  }

  private double[] computeCentroid(double[][] simplex, int count) {
    int dim = simplex[0].length;
    double[] centroid = new double[dim];
    for (int i = 0; i < count; i++) {
      for (int j = 0; j < dim; j++) {
        centroid[j] += simplex[i][j];
      }
    }
    for (int j = 0; j < dim; j++) {
      centroid[j] /= count;
    }
    return centroid;
  }

  private double[] reflect(double[] centroid, double[] point, double factor) {
    double[] result = new double[centroid.length];
    for (int i = 0; i < centroid.length; i++) {
      result[i] = centroid[i] + factor * (centroid[i] - point[i]);
    }
    return result;
  }

  private double[] contract(double[] centroid, double[] point, double factor) {
    double[] result = new double[centroid.length];
    for (int i = 0; i < centroid.length; i++) {
      result[i] = centroid[i] + factor * (point[i] - centroid[i]);
    }
    return result;
  }

  private void shrink(double[][] simplex, List<ManipulatedVariable> variables) {
    for (int i = 1; i < simplex.length; i++) {
      for (int j = 0; j < simplex[i].length; j++) {
        ManipulatedVariable var = variables.get(j);
        simplex[i][j] = Math.max(var.getLowerBound(),
            Math.min(var.getUpperBound(), 0.5 * (simplex[0][j] + simplex[i][j])));
      }
    }
  }

  private Evaluation evaluateCandidate(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double candidateRate,
      Map<Long, Evaluation> cache) {
    if (config.enableCaching) {
      long cacheKey = Math.round(candidateRate / Math.max(config.tolerance, 1e-9));
      Evaluation cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      Evaluation evaluation = evaluateCandidateInternal(process, feedStream, config, objectives,
          constraints, candidateRate);
      cache.put(cacheKey, evaluation);
      return evaluation;
    }
    return evaluateCandidateInternal(process, feedStream, config, objectives, constraints,
        candidateRate);
  }

  private Evaluation evaluateCandidate(ProcessSystem process, List<ManipulatedVariable> variables,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double[] candidate,
      Map<String, Evaluation> cache) {
    if (config.enableCaching) {
      String cacheKey = buildVectorCacheKey(candidate, config);
      Evaluation cached = cache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      Evaluation evaluation = evaluateCandidateInternal(process, variables, config, objectives,
          constraints, candidate);
      cache.put(cacheKey, evaluation);
      return evaluation;
    }
    return evaluateCandidateInternal(process, variables, config, objectives, constraints, candidate);
  }

  private Evaluation evaluateCandidateInternal(ProcessSystem process, StreamInterface feedStream,
      OptimizationConfig config, List<OptimizationObjective> objectives,
      List<OptimizationConstraint> constraints, double candidateRate) {
    feedStream.setFlowRate(candidateRate, config.rateUnit);
    process.run();
    Map<String, Double> decisions = new HashMap<>();
    decisions.put(feedStream.getName(), candidateRate);
    return evaluateProcess(process, config, objectives, constraints, decisions);
  }

  private Evaluation evaluateCandidateInternal(ProcessSystem process,
      List<ManipulatedVariable> variables, OptimizationConfig config,
      List<OptimizationObjective> objectives, List<OptimizationConstraint> constraints,
      double[] candidate) {
    Map<String, Double> decisions = new HashMap<>();
    for (int i = 0; i < variables.size(); i++) {
      ManipulatedVariable variable = variables.get(i);
      double bounded = Math.max(variable.getLowerBound(),
          Math.min(variable.getUpperBound(), candidate[i]));
      variable.apply(process, bounded);
      decisions.put(variable.getName(), bounded);
    }
    process.run();
    return evaluateProcess(process, config, objectives, constraints, decisions);
  }

  private String buildVectorCacheKey(double[] candidate, OptimizationConfig config) {
    double tol = Math.max(config.tolerance, 1e-9);
    StringBuilder key = new StringBuilder();
    for (double value : candidate) {
      long rounded = Math.round(value / tol);
      key.append(rounded).append("|");
    }
    return key.toString();
  }

  private void recordIteration(List<IterationRecord> iterationHistory, double candidate,
      String rateUnit, Evaluation evaluation, boolean feasible) {
    String bottleneckName = evaluation.bottleneck() != null ? evaluation.bottleneck().getName()
        : "unknown";
    iterationHistory.add(new IterationRecord(candidate, rateUnit, evaluation.decisionVariables(),
        bottleneckName, evaluation.bottleneckUtilization(), evaluation.utilizationWithinLimits(),
        evaluation.hardOk(), feasible, evaluation.score(), evaluation.utilizationRecords()));
  }

  private double feasibilityScore(Evaluation evaluation) {
    if (evaluation.utilizationWithinLimits() && evaluation.hardOk()) {
      return evaluation.score();
    }
    // Penalize infeasible points to still allow non-monotonic searches to converge.
    double penalty = 0.0;
    for (ConstraintStatus status : evaluation.constraintStatuses()) {
      if (status.getSeverity() == ConstraintSeverity.HARD && status.violated()) {
        penalty -= Math.abs(status.getMargin());
      }
    }
    if (!evaluation.utilizationWithinLimits()) {
      penalty -= Math.abs(1.0 + evaluation.bottleneckUtilization());
    }
    return evaluation.score() + penalty;
  }

  private double determineUtilizationLimit(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    return Optional.ofNullable(config.utilizationLimitsByName.get(unit.getName()))
        .orElseGet(() -> config.utilizationLimitsByType.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(unit.getClass()))
            .map(Map.Entry::getValue).findFirst().orElse(config.defaultUtilizationLimit));
  }

  private CapacityRule determineCapacityRule(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    CapacityRule byName = config.capacityRulesByName.get(unit.getName());
    if (byName != null) {
      return byName;
    }
    for (Map.Entry<Class<?>, CapacityRule> entry : config.capacityRulesByType.entrySet()) {
      if (entry.getKey().isAssignableFrom(unit.getClass())) {
        return entry.getValue();
      }
    }
    if (unit instanceof DistillationColumn) {
      DistillationColumn column = (DistillationColumn) unit;
      return new CapacityRule(equipment -> column.getFsFactor(),
          equipment -> config.getColumnFsFactorLimit());
    }
    if (unit instanceof neqsim.process.equipment.separator.Separator) {
      return new CapacityRule(
          equipment -> ((neqsim.process.equipment.separator.Separator) unit).getLiquidLevel(),
          equipment -> 1.0);
    }
    if (unit instanceof neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2) {
      neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2 exchanger =
          (neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger2) unit;
      return new CapacityRule(equipment -> Math.abs(exchanger.energyDiff()),
          equipment -> Math.max(1.0, exchanger.getCapacityMax()));
    }
    return new CapacityRule(ProcessEquipmentInterface::getCapacityDuty,
        ProcessEquipmentInterface::getCapacityMax);
  }

  private CapacityRange determineCapacityRange(ProcessEquipmentInterface unit,
      OptimizationConfig config) {
    CapacityRange byName = config.capacityRangesByName.get(unit.getName());
    if (byName != null) {
      return byName;
    }
    for (Map.Entry<Class<?>, CapacityRange> entry : config.capacityRangesByType.entrySet()) {
      if (entry.getKey().isAssignableFrom(unit.getClass())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /** Range container to support percentile-based capacity evaluations. */
  static final class CapacityRange {
    private final double p10;
    private final double p50;
    private final double p90;

    public CapacityRange(double p10, double p50, double p90) {
      this.p10 = p10;
      this.p50 = p50;
      this.p90 = p90;
    }

    public double atPercentile(double percentile) {
      double clamped = Math.max(0.0, Math.min(1.0, percentile));
      if (clamped <= 0.5) {
        return p10 + (p50 - p10) * (clamped / 0.5);
      }
      return p50 + (p90 - p50) * ((clamped - 0.5) / 0.5);
    }
  }

  /** Function to compute capacity duty/limit for a specific equipment. */
  @FunctionalInterface
  interface CapacityMetric extends ToDoubleFunction<ProcessEquipmentInterface> {}

  /** Pair of capacity duty/max providers. */
  static final class CapacityRule {
    private final CapacityMetric dutyMetric;
    private final CapacityMetric maxMetric;

    public CapacityRule(CapacityMetric dutyMetric, CapacityMetric maxMetric) {
      this.dutyMetric = dutyMetric;
      this.maxMetric = maxMetric;
    }

    public double duty(ProcessEquipmentInterface unit) {
      return dutyMetric.applyAsDouble(unit);
    }

    public double max(ProcessEquipmentInterface unit) {
      return maxMetric.applyAsDouble(unit);
    }
  }

  /** Metric to evaluate per-equipment constraints. */
  @FunctionalInterface
  interface EquipmentMetric extends ToDoubleFunction<ProcessEquipmentInterface> {}

  /** Constraint template applied to each matching equipment. */
  static final class EquipmentConstraintRule {
    private final Class<?> equipmentType;
    private final String name;
    private final EquipmentMetric metric;
    private final double limit;
    private final ConstraintDirection direction;
    private final ConstraintSeverity severity;
    private final double penaltyWeight;
    private final String description;

    public EquipmentConstraintRule(Class<?> equipmentType, String name, EquipmentMetric metric,
        double limit, ConstraintDirection direction, ConstraintSeverity severity,
        double penaltyWeight, String description) {
      this.equipmentType = Objects.requireNonNull(equipmentType, "Equipment type is required");
      this.name = Objects.requireNonNull(name, "Equipment constraint name is required");
      this.metric = Objects.requireNonNull(metric, "Equipment metric is required");
      this.limit = limit;
      this.direction = Objects.requireNonNull(direction, "Equipment constraint direction");
      this.severity = Objects.requireNonNull(severity, "Equipment constraint severity");
      this.penaltyWeight = penaltyWeight;
      this.description = description;
    }

    public boolean matches(ProcessEquipmentInterface unit) {
      return equipmentType.isAssignableFrom(unit.getClass());
    }

  public OptimizationConstraint toConstraint(ProcessEquipmentInterface unit) {
      return new OptimizationConstraint(name + " - " + unit.getName(),
          proc -> metric.applyAsDouble(unit), limit, direction, severity, penaltyWeight,
          description);
    }
  }

}
