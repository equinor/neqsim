package neqsim.process.util.optimizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.ScenarioRequest;
import neqsim.process.util.optimizer.ProductionOptimizer.ManipulatedVariable;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintDirection;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Loader for YAML/JSON optimization specification files.
 *
 * <p>
 * This class parses YAML or JSON specification files that define optimization scenarios for
 * production optimization. It enables configuration-driven optimization where scenarios, variables,
 * objectives, and constraints are defined in external files rather than code.
 * </p>
 *
 * <p>
 * <strong>YAML Format Specification:</strong>
 * </p>
 * 
 * <pre>
 * scenarios:
 *   - name: "Scenario1"
 *     process: "myProcess"           # Key in processes map
 *     feedStream: "myFeed"           # Key in feeds map (for single-variable)
 *     lowerBound: 50000.0            # Min flow rate
 *     upperBound: 200000.0           # Max flow rate
 *     rateUnit: "kg/hr"              # Unit for bounds
 *     tolerance: 100.0               # Convergence tolerance
 *     maxIterations: 30              # Max solver iterations
 *     searchMode: "GOLDEN_SECTION_SCORE"  # BINARY_FEASIBILITY, GOLDEN_SECTION_SCORE,
 *                                         # NELDER_MEAD_SCORE, PARTICLE_SWARM_SCORE
 *     utilizationMarginFraction: 0.05     # 5% margin on equipment limits
 *     capacityUncertaintyFraction: 0.0    # Uncertainty buffer
 *     capacityPercentile: 0.5             # P50 capacity
 *     enableCaching: true                 # Cache evaluations
 *     columnFsFactorLimit: 2.5            # Distillation column Fs factor limit
 *
 *     # Multi-variable optimization (instead of feedStream)
 *     variables:
 *       - name: "flowRate"
 *         stream: "feedStream"       # Key in feeds map
 *         lowerBound: 50000.0
 *         upperBound: 200000.0
 *         unit: "kg/hr"
 *       - name: "pressure"
 *         stream: "compOutlet"
 *         lowerBound: 100.0
 *         upperBound: 150.0
 *         unit: "bara"
 *
 *     # Objectives
 *     objectives:
 *       - name: "throughput"
 *         weight: 1.0
 *         type: "MAXIMIZE"           # MAXIMIZE or MINIMIZE
 *         metric: "throughputMetric" # Key in metrics map
 *
 *     # Constraints
 *     constraints:
 *       - name: "maxPower"
 *         metric: "powerMetric"      # Key in metrics map
 *         limit: 5000.0
 *         direction: "LESS_THAN"     # LESS_THAN or GREATER_THAN
 *         severity: "HARD"           # HARD or SOFT
 *         penaltyWeight: 0.0         # For SOFT constraints
 *         description: "Compressor power limit"
 * </pre>
 *
 * <p>
 * <strong>Java Usage Example:</strong>
 * </p>
 * 
 * <pre>{@code
 * // 1. Build process and create registries
 * ProcessSystem process = new ProcessSystem();
 * // ... add equipment ...
 * process.run();
 * 
 * StreamInterface feed = (StreamInterface) process.getUnit("feed");
 * 
 * // 2. Create mapping registries
 * Map<String, ProcessSystem> processes = new HashMap<>();
 * processes.put("myProcess", process);
 * 
 * Map<String, StreamInterface> feeds = new HashMap<>();
 * feeds.put("myFeed", feed);
 * 
 * Map<String, ToDoubleFunction<ProcessSystem>> metrics = new HashMap<>();
 * metrics.put("throughputMetric", p -> p.getUnit("outlet").getFlowRate("kg/hr"));
 * metrics.put("powerMetric", p -> ((Compressor) p.getUnit("comp")).getPower("kW"));
 * 
 * // 3. Load and run scenarios
 * Path specFile = Paths.get("optimization_scenarios.yaml");
 * List<ScenarioRequest> scenarios =
 *     ProductionOptimizationSpecLoader.load(specFile, processes, feeds, metrics);
 * 
 * ProductionOptimizer optimizer = new ProductionOptimizer();
 * for (ScenarioRequest scenario : scenarios) {
 *   OptimizationResult result = optimizer.optimizeScenario(scenario);
 *   System.out.println(scenario.getName() + ": " + result.getOptimalRate());
 * }
 * }</pre>
 *
 * <p>
 * <strong>Python Usage Example (via JPype):</strong>
 * </p>
 * 
 * <pre>
 * from neqsim.neqsimpython import jneqsim
 * from jpype import JImplements, JOverride
 * import java.nio.file.Paths as JPaths
 * import java.util.HashMap as JHashMap
 *
 * # Get Java classes
 * SpecLoader = jneqsim.process.util.optimizer.ProductionOptimizationSpecLoader
 *
 * # Create registries
 * processes = JHashMap()
 * processes.put("myProcess", process)
 *
 * feeds = JHashMap()
 * feeds.put("myFeed", process.getUnit("feed"))
 *
 * # Create metrics with Java interface
 * &#64;JImplements("java.util.function.ToDoubleFunction")
 * class ThroughputMetric:
 *     &#64;JOverride
 *     def applyAsDouble(self, proc):
 *         return proc.getUnit("outlet").getFlowRate("kg/hr")
 *
 * metrics = JHashMap()
 * metrics.put("throughputMetric", ThroughputMetric())
 *
 * # Load scenarios
 * spec_path = JPaths.get("optimization_scenarios.yaml")
 * scenarios = SpecLoader.load(spec_path, processes, feeds, metrics)
 *
 * # Run each scenario
 * optimizer = jneqsim.process.util.optimizer.ProductionOptimizer()
 * for scenario in scenarios:
 *     result = optimizer.optimizeScenario(scenario)
 *     print(f"{scenario.getName()}: {result.getOptimalRate():.0f}")
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see ProductionOptimizer
 * @see ProductionOptimizer.ScenarioRequest
 * @see ProductionOptimizer.OptimizationConfig
 */
public final class ProductionOptimizationSpecLoader {
  private ProductionOptimizationSpecLoader() {}

  /**
   * Parse a YAML/JSON specification file into scenario requests.
   *
   * <p>
   * This method reads a specification file and converts it into a list of {@link ScenarioRequest}
   * objects that can be executed by {@link ProductionOptimizer}. The file format (YAML or JSON) is
   * auto-detected based on the file extension.
   * </p>
   *
   * @param specPath path to the YAML (.yaml, .yml) or JSON (.json) specification file
   * @param processes map of process name (as used in spec) to ProcessSystem instances
   * @param feeds map of feed stream name (as used in spec) to StreamInterface instances
   * @param metrics map of metric name (as used in spec) to evaluation functions
   * @return list of ScenarioRequest objects ready for optimization
   * @throws IOException if the file cannot be read or parsed
   * @throws IllegalArgumentException if a referenced process, feed, or metric is not found in the
   *         provided maps
   */
  public static List<ScenarioRequest> load(Path specPath, Map<String, ProcessSystem> processes,
      Map<String, StreamInterface> feeds,
      Map<String, java.util.function.ToDoubleFunction<ProcessSystem>> metrics) throws IOException {
    ObjectMapper mapper =
        specPath.toString().endsWith(".yaml") || specPath.toString().endsWith(".yml")
            ? new ObjectMapper(new YAMLFactory())
            : new ObjectMapper();
    String raw = new String(Files.readAllBytes(specPath), StandardCharsets.UTF_8);
    Spec spec = mapper.readValue(raw, Spec.class);

    List<ScenarioRequest> scenarios = new ArrayList<>();
    for (ScenarioSpec scenarioSpec : spec.scenarios) {
      ProcessSystem process = processes.get(scenarioSpec.process);
      StreamInterface feed =
          scenarioSpec.feedStream != null ? feeds.get(scenarioSpec.feedStream) : null;
      if (process == null) {
        throw new IllegalArgumentException(
            "Missing process mapping for scenario " + scenarioSpec.name);
      }
      OptimizationConfig config =
          new OptimizationConfig(scenarioSpec.lowerBound, scenarioSpec.upperBound)
              .rateUnit(scenarioSpec.rateUnit).tolerance(scenarioSpec.tolerance)
              .maxIterations(scenarioSpec.maxIterations)
              .searchMode(SearchMode.valueOf(scenarioSpec.searchMode))
              .utilizationMarginFraction(scenarioSpec.utilizationMarginFraction)
              .capacityUncertaintyFraction(scenarioSpec.capacityUncertaintyFraction)
              .capacityPercentile(scenarioSpec.capacityPercentile)
              .enableCaching(scenarioSpec.enableCaching).swarmSize(scenarioSpec.swarmSize)
              .inertiaWeight(scenarioSpec.inertiaWeight)
              .cognitiveWeight(scenarioSpec.cognitiveWeight).socialWeight(scenarioSpec.socialWeight)
              .columnFsFactorLimit(scenarioSpec.columnFsFactorLimit);
      List<OptimizationObjective> objectives = new ArrayList<>();
      if (scenarioSpec.objectives != null) {
        for (ObjectiveSpec objectiveSpec : scenarioSpec.objectives) {
          ObjectiveType type = ObjectiveType.valueOf(objectiveSpec.type.toUpperCase());
          objectives.add(new OptimizationObjective(objectiveSpec.name,
              requireMetric(metrics, objectiveSpec.metric), objectiveSpec.weight, type));
        }
      }

      List<OptimizationConstraint> constraints = new ArrayList<>();
      if (scenarioSpec.constraints != null) {
        for (ConstraintSpec constraintSpec : scenarioSpec.constraints) {
          ConstraintDirection direction = ConstraintDirection.valueOf(constraintSpec.direction);
          ConstraintSeverity severity = ConstraintSeverity.valueOf(constraintSpec.severity);
          constraints.add(new OptimizationConstraint(constraintSpec.name,
              requireMetric(metrics, constraintSpec.metric), constraintSpec.limit, direction,
              severity, constraintSpec.penaltyWeight, constraintSpec.description));
        }
      }
      if (scenarioSpec.variables != null && !scenarioSpec.variables.isEmpty()) {
        List<ManipulatedVariable> vars = new ArrayList<>();
        for (VariableSpec variableSpec : scenarioSpec.variables) {
          StreamInterface stream = feeds.get(variableSpec.stream);
          if (stream == null) {
            throw new IllegalArgumentException(
                "Missing stream mapping for variable: " + variableSpec.name);
          }
          vars.add(new ManipulatedVariable(variableSpec.name, variableSpec.lowerBound,
              variableSpec.upperBound, variableSpec.unit, (proc, value) -> stream.setFlowRate(value,
                  variableSpec.unit != null ? variableSpec.unit : scenarioSpec.rateUnit)));
        }
        scenarios.add(
            new ScenarioRequest(scenarioSpec.name, process, vars, config, objectives, constraints));
      } else {
        if (feed == null) {
          throw new IllegalArgumentException(
              "Missing feed mapping for scenario " + scenarioSpec.name);
        }
        scenarios.add(
            new ScenarioRequest(scenarioSpec.name, process, feed, config, objectives, constraints));
      }
    }
    return scenarios;
  }

  /**
   * Looks up a metric function by key, throwing if not found.
   *
   * @param metrics the metrics map to search
   * @param key the metric key to look up
   * @return the metric evaluation function
   * @throws IllegalArgumentException if the key is not found
   */
  private static java.util.function.ToDoubleFunction<ProcessSystem> requireMetric(
      Map<String, java.util.function.ToDoubleFunction<ProcessSystem>> metrics, String key) {
    java.util.function.ToDoubleFunction<ProcessSystem> metric = metrics.get(key);
    if (metric == null) {
      throw new IllegalArgumentException("Missing metric mapping for key: " + key);
    }
    return metric;
  }

  /**
   * Root element of the specification file.
   *
   * <p>
   * Contains a list of scenario specifications to be converted to ScenarioRequest objects.
   * </p>
   */
  private static final class Spec {
    /** List of scenario specifications. */
    public List<ScenarioSpec> scenarios = Collections.emptyList();
  }

  /**
   * Individual scenario configuration from YAML/JSON.
   *
   * <p>
   * Defines all parameters for a single optimization scenario including bounds, objectives,
   * constraints, and algorithm settings.
   * </p>
   */
  private static final class ScenarioSpec {
    /** Unique name for the scenario. */
    public String name;
    /** Key to look up ProcessSystem in processes map. */
    public String process;
    /** Key to look up feed stream in feeds map (for single-variable optimization). */
    public String feedStream;
    /** Lower bound for flow rate optimization. */
    public double lowerBound;
    /** Upper bound for flow rate optimization. */
    public double upperBound;
    /** Unit for flow rate bounds (default: kg/hr). */
    public String rateUnit = "kg/hr";
    /** Convergence tolerance for optimization. */
    public double tolerance = 1e-3;
    /** Maximum number of iterations. */
    public int maxIterations = 30;
    /** Search algorithm to use. */
    public String searchMode = SearchMode.BINARY_FEASIBILITY.name();
    /** Margin fraction to apply to equipment utilization limits. */
    public double utilizationMarginFraction = 0.0;
    /** Uncertainty fraction for capacity calculations. */
    public double capacityUncertaintyFraction = 0.0;
    /** Percentile for capacity calculations (0.5 = P50). */
    public double capacityPercentile = 0.5;
    /** Whether to cache evaluation results. */
    public boolean enableCaching = true;
    /** Swarm size for particle swarm optimization. */
    public int swarmSize = 8;
    /** Inertia weight for PSO. */
    public double inertiaWeight = 0.6;
    /** Cognitive weight for PSO. */
    public double cognitiveWeight = 1.2;
    /** Social weight for PSO. */
    public double socialWeight = 1.2;
    /** Fs factor limit for distillation columns. */
    public double columnFsFactorLimit = 2.5;
    /** List of optimization objectives. */
    public List<ObjectiveSpec> objectives;
    /** List of optimization constraints. */
    public List<ConstraintSpec> constraints;
    /** List of manipulated variables (for multi-variable optimization). */
    public List<VariableSpec> variables;
  }

  /**
   * Objective specification in the YAML/JSON file.
   *
   * <p>
   * Defines a single optimization objective with its evaluation metric and weight.
   * </p>
   */
  private static final class ObjectiveSpec {
    /** Unique name for the objective. */
    public String name;
    /** Weight for multi-objective scalarization (default: 1.0). */
    public double weight = 1.0;
    /** Optimization direction: MAXIMIZE or MINIMIZE. */
    public String type = "MAXIMIZE";
    /** Key to look up the metric function in the metrics map. */
    public String metric;
  }

  /**
   * Constraint specification in the YAML/JSON file.
   *
   * <p>
   * Defines a single optimization constraint with its limit, direction, and severity.
   * </p>
   */
  private static final class ConstraintSpec {
    /** Unique name for the constraint. */
    public String name;
    /** Constraint limit value. */
    public double limit;
    /** Comparison direction: LESS_THAN or GREATER_THAN. */
    public String direction;
    /** Constraint severity: HARD or SOFT. */
    public String severity;
    /** Penalty weight for soft constraints (default: 0.0). */
    public double penaltyWeight = 0.0;
    /** Human-readable description of the constraint. */
    public String description;
    /** Key to look up the metric function in the metrics map. */
    public String metric;
  }

  /**
   * Variable specification for multi-variable optimization.
   *
   * <p>
   * Defines a single manipulated variable with its bounds and associated stream.
   * </p>
   */
  private static final class VariableSpec {
    /** Unique name for the variable. */
    public String name;
    /** Key to look up the stream in the feeds map. */
    public String stream;
    /** Lower bound for the variable. */
    public double lowerBound;
    /** Upper bound for the variable. */
    public double upperBound;
    /** Unit for the variable (e.g., "kg/hr", "bara"). */
    public String unit;
  }
}
