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

/** Loader for lightweight YAML/JSON optimization specs that map onto scenarios. */
public final class ProductionOptimizationSpecLoader {
  private ProductionOptimizationSpecLoader() {}

  /** Parse a spec file into scenario requests using provided process and feed registries. */
  public static List<ScenarioRequest> load(Path specPath, Map<String, ProcessSystem> processes,
      Map<String, StreamInterface> feeds, Map<String, java.util.function.ToDoubleFunction<ProcessSystem>>
      metrics) throws IOException {
    ObjectMapper mapper = specPath.toString().endsWith(".yaml") || specPath.toString()
        .endsWith(".yml") ? new ObjectMapper(new YAMLFactory()) : new ObjectMapper();
      String raw = new String(Files.readAllBytes(specPath), StandardCharsets.UTF_8);
    Spec spec = mapper.readValue(raw, Spec.class);

    List<ScenarioRequest> scenarios = new ArrayList<>();
    for (ScenarioSpec scenarioSpec : spec.scenarios) {
      ProcessSystem process = processes.get(scenarioSpec.process);
      StreamInterface feed = scenarioSpec.feedStream != null ? feeds.get(scenarioSpec.feedStream)
          : null;
      if (process == null) {
        throw new IllegalArgumentException(
            "Missing process mapping for scenario " + scenarioSpec.name);
      }
      OptimizationConfig config = new OptimizationConfig(scenarioSpec.lowerBound,
          scenarioSpec.upperBound).rateUnit(scenarioSpec.rateUnit)
              .tolerance(scenarioSpec.tolerance).maxIterations(scenarioSpec.maxIterations)
              .searchMode(SearchMode.valueOf(scenarioSpec.searchMode))
              .utilizationMarginFraction(scenarioSpec.utilizationMarginFraction)
              .capacityUncertaintyFraction(scenarioSpec.capacityUncertaintyFraction)
              .capacityPercentile(scenarioSpec.capacityPercentile)
              .enableCaching(scenarioSpec.enableCaching)
              .swarmSize(scenarioSpec.swarmSize)
              .inertiaWeight(scenarioSpec.inertiaWeight)
              .cognitiveWeight(scenarioSpec.cognitiveWeight)
              .socialWeight(scenarioSpec.socialWeight)
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
        scenarios.add(new ScenarioRequest(scenarioSpec.name, process, vars, config, objectives,
            constraints));
      } else {
        if (feed == null) {
          throw new IllegalArgumentException(
              "Missing feed mapping for scenario " + scenarioSpec.name);
        }
        scenarios.add(new ScenarioRequest(scenarioSpec.name, process, feed, config, objectives,
            constraints));
      }
    }
    return scenarios;
  }

  private static java.util.function.ToDoubleFunction<ProcessSystem> requireMetric(
      Map<String, java.util.function.ToDoubleFunction<ProcessSystem>> metrics, String key) {
    java.util.function.ToDoubleFunction<ProcessSystem> metric = metrics.get(key);
    if (metric == null) {
      throw new IllegalArgumentException("Missing metric mapping for key: " + key);
    }
    return metric;
  }

  /** Spec root. */
  private static final class Spec {
      public List<ScenarioSpec> scenarios = Collections.emptyList();
  }

  /** Scenario configuration from YAML. */
  private static final class ScenarioSpec {
    public String name;
    public String process;
    public String feedStream;
    public double lowerBound;
    public double upperBound;
    public String rateUnit = "kg/hr";
    public double tolerance = 1e-3;
    public int maxIterations = 30;
    public String searchMode = SearchMode.BINARY_FEASIBILITY.name();
    public double utilizationMarginFraction = 0.0;
    public double capacityUncertaintyFraction = 0.0;
    public double capacityPercentile = 0.5;
    public boolean enableCaching = true;
    public int swarmSize = 8;
    public double inertiaWeight = 0.6;
    public double cognitiveWeight = 1.2;
    public double socialWeight = 1.2;
    public double columnFsFactorLimit = 2.5;
    public List<ObjectiveSpec> objectives;
    public List<ConstraintSpec> constraints;
    public List<VariableSpec> variables;
  }

  /** Objective representation inside the spec. */
  private static final class ObjectiveSpec {
    public String name;
    public double weight = 1.0;
    public String type = "MAXIMIZE";
    public String metric;
  }

  /** Constraint representation inside the spec. */
  private static final class ConstraintSpec {
    public String name;
    public double limit;
    public String direction;
    public String severity;
    public double penaltyWeight = 0.0;
    public String description;
    public String metric;
  }

  /** Variable representation for manipulating streams. */
  private static final class VariableSpec {
    public String name;
    public String stream;
    public double lowerBound;
    public double upperBound;
    public String unit;
  }
}
