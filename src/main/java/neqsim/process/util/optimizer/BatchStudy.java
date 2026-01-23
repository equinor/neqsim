package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Parallel parameter study for rapid concept screening.
 *
 * <p>
 * This class enables cloud-scale batch simulations by:
 * <ul>
 * <li><b>Parameter Variation:</b> Define ranges for design variables</li>
 * <li><b>Parallel Execution:</b> Run thousands of cases concurrently</li>
 * <li><b>Multi-Objective Ranking:</b> Compare by CAPEX, OPEX, emissions, etc.</li>
 * <li><b>Result Aggregation:</b> Collect and analyze results efficiently</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem baseCase = new ProcessSystem();
 * // ... configure base case ...
 *
 * BatchStudy study = BatchStudy.builder(baseCase).vary("heater.duty", 1.0e6, 5.0e6, 5) // 5 values
 *     .vary("compressor.pressure", 30.0, 80.0, 6) // 6 values
 *     .addObjective("power", Objective.MINIMIZE).addObjective("throughput", Objective.MAXIMIZE)
 *     .parallelism(8).build();
 *
 * BatchStudyResult result = study.run();
 *
 * System.out.println("Total cases: " + result.getTotalCases());
 * System.out.println("Best case: " + result.getBestCase("power"));
 *
 * result.exportToCSV("batch_results.csv");
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class BatchStudy implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem baseCase;
  private final List<ParameterVariation> variations;
  private final Map<String, ObjectiveDefinition> objectives;
  private final Map<String, Function<ProcessSystem, Double>> objectiveExtractors;
  private int parallelism;
  private String studyName;
  private boolean stopOnFailure = false;

  private BatchStudy(Builder builder) {
    this.baseCase = builder.baseCase;
    this.variations = new ArrayList<>(builder.variations);
    this.objectives = new HashMap<>(builder.objectives);
    this.objectiveExtractors = new HashMap<>(builder.objectiveExtractors);
    this.parallelism = builder.parallelism;
    this.studyName = builder.studyName;
    this.stopOnFailure = builder.stopOnFailure;
  }

  /**
   * Creates a builder for a batch study.
   *
   * @param baseCase the base process system to vary
   * @return a new builder
   */
  public static Builder builder(ProcessSystem baseCase) {
    return new Builder(baseCase);
  }

  /**
   * Runs the batch study.
   *
   * @return results of all cases
   */
  public BatchStudyResult run() {
    List<ParameterSet> allCases = generateAllCases();
    BatchStudyResult result = new BatchStudyResult(studyName, allCases.size());

    if (parallelism <= 1) {
      // Sequential execution
      for (ParameterSet params : allCases) {
        CaseResult caseResult = runCase(params);
        result.addResult(caseResult);
      }
    } else {
      // Parallel execution
      ExecutorService executor = Executors.newFixedThreadPool(parallelism);
      List<Future<CaseResult>> futures = new ArrayList<>();

      for (ParameterSet params : allCases) {
        futures.add(executor.submit(() -> runCase(params)));
      }

      for (Future<CaseResult> future : futures) {
        try {
          CaseResult caseResult = future.get();
          result.addResult(caseResult);

          if (caseResult.failed && stopOnFailure) {
            executor.shutdownNow();
            break;
          }
        } catch (InterruptedException | ExecutionException e) {
          result.addResult(new CaseResult(null, true, e.getMessage()));
        }
      }

      executor.shutdown();
    }

    result.complete();
    return result;
  }

  private List<ParameterSet> generateAllCases() {
    List<ParameterSet> cases = new ArrayList<>();
    generateCasesRecursive(cases, new HashMap<>(), 0);
    return cases;
  }

  private void generateCasesRecursive(List<ParameterSet> cases, Map<String, Double> currentValues,
      int varIndex) {
    if (varIndex >= variations.size()) {
      // Base case: all parameters assigned
      ParameterSet params = new ParameterSet(new HashMap<>(currentValues));
      params.caseId = UUID.randomUUID().toString().substring(0, 8);
      cases.add(params);
      return;
    }

    ParameterVariation variation = variations.get(varIndex);
    for (double value : variation.getValues()) {
      currentValues.put(variation.parameterName, value);
      generateCasesRecursive(cases, currentValues, varIndex + 1);
    }
  }

  private CaseResult runCase(ParameterSet params) {
    try {
      // Clone the base case
      ProcessSystem caseCopy = baseCase.copy();

      // Apply parameter variations
      for (Map.Entry<String, Double> param : params.values.entrySet()) {
        applyParameter(caseCopy, param.getKey(), param.getValue());
      }

      // Run simulation
      Instant start = Instant.now();
      caseCopy.run();
      Duration runtime = Duration.between(start, Instant.now());

      // Extract objectives
      Map<String, Double> objectiveValues = new HashMap<>();
      for (Map.Entry<String, Function<ProcessSystem, Double>> extractor : objectiveExtractors
          .entrySet()) {
        try {
          double value = extractor.getValue().apply(caseCopy);
          objectiveValues.put(extractor.getKey(), value);
        } catch (Exception e) {
          objectiveValues.put(extractor.getKey(), Double.NaN);
        }
      }

      CaseResult result = new CaseResult(params, false, null);
      result.objectiveValues = objectiveValues;
      result.runtime = runtime;
      return result;

    } catch (Exception e) {
      return new CaseResult(params, true, e.getMessage());
    }
  }

  private void applyParameter(ProcessSystem process, String paramPath, double value) {
    // Parse parameter path (e.g., "heater.duty")
    String[] parts = paramPath.split("\\.");
    if (parts.length < 2) {
      return;
    }

    String equipmentName = parts[0];
    String property = parts[1];

    // Find equipment and set property
    neqsim.process.equipment.ProcessEquipmentInterface equipment = process.getUnit(equipmentName);
    if (equipment == null) {
      return;
    }

    // Common property setters (can be extended)
    switch (property.toLowerCase()) {
      // Heater/Cooler properties
      case "duty":
        if (equipment instanceof neqsim.process.equipment.heatexchanger.Heater) {
          ((neqsim.process.equipment.heatexchanger.Heater) equipment).setDuty(value);
        }
        break;
      case "outlettemperature":
        if (equipment instanceof neqsim.process.equipment.heatexchanger.Heater) {
          ((neqsim.process.equipment.heatexchanger.Heater) equipment).setOutTemperature(value, "C");
        }
        break;

      // Valve properties
      case "pressure":
      case "outletpressure":
        if (equipment instanceof neqsim.process.equipment.valve.ThrottlingValve) {
          ((neqsim.process.equipment.valve.ThrottlingValve) equipment).setOutletPressure(value);
        } else if (equipment instanceof neqsim.process.equipment.compressor.Compressor) {
          ((neqsim.process.equipment.compressor.Compressor) equipment).setOutletPressure(value);
        } else if (equipment instanceof neqsim.process.equipment.pump.Pump) {
          ((neqsim.process.equipment.pump.Pump) equipment).setOutletPressure(value);
        }
        break;
      case "opening":
      case "percentvalveopening":
        if (equipment instanceof neqsim.process.equipment.valve.ValveInterface) {
          ((neqsim.process.equipment.valve.ValveInterface) equipment).setPercentValveOpening(value);
        }
        break;
      case "cv":
        if (equipment instanceof neqsim.process.equipment.valve.ThrottlingValve) {
          ((neqsim.process.equipment.valve.ThrottlingValve) equipment).setCv(value);
        }
        break;

      // Compressor properties
      case "polytropicefficiency":
        if (equipment instanceof neqsim.process.equipment.compressor.Compressor) {
          ((neqsim.process.equipment.compressor.Compressor) equipment)
              .setPolytropicEfficiency(value);
        }
        break;
      case "isentropicefficiency":
        if (equipment instanceof neqsim.process.equipment.compressor.Compressor) {
          ((neqsim.process.equipment.compressor.Compressor) equipment)
              .setIsentropicEfficiency(value);
        }
        break;

      // Stream properties
      case "temperature":
        if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
          ((neqsim.process.equipment.stream.StreamInterface) equipment).setTemperature(value, "C");
        }
        break;
      case "flowrate":
        if (equipment instanceof neqsim.process.equipment.stream.StreamInterface) {
          ((neqsim.process.equipment.stream.StreamInterface) equipment).setFlowRate(value, "kg/hr");
        }
        break;

      // Separator properties
      case "internaldiameter":
        if (equipment instanceof neqsim.process.equipment.separator.Separator) {
          ((neqsim.process.equipment.separator.Separator) equipment).setInternalDiameter(value);
        }
        break;

      default:
        // Extensible for other properties
        break;
    }
  }

  /**
   * Gets the total number of cases to be run.
   *
   * @return case count
   */
  public int getTotalCases() {
    int total = 1;
    for (ParameterVariation v : variations) {
      total *= v.getValues().length;
    }
    return total;
  }

  /**
   * Builder for BatchStudy.
   */
  public static class Builder {
    private final ProcessSystem baseCase;
    private final List<ParameterVariation> variations = new ArrayList<>();
    private final Map<String, ObjectiveDefinition> objectives = new HashMap<>();
    private final Map<String, Function<ProcessSystem, Double>> objectiveExtractors =
        new HashMap<>();
    private int parallelism = Runtime.getRuntime().availableProcessors();
    private String studyName = "BatchStudy";
    private boolean stopOnFailure = false;

    Builder(ProcessSystem baseCase) {
      this.baseCase = baseCase;
    }

    /**
     * Adds a parameter to vary with explicit values.
     *
     * @param parameterPath path to parameter (e.g., "heater.duty")
     * @param values explicit values to test
     * @return this builder
     */
    public Builder vary(String parameterPath, double... values) {
      variations.add(new ParameterVariation(parameterPath, values));
      return this;
    }

    /**
     * Adds a parameter to vary over a range.
     *
     * @param parameterPath path to parameter
     * @param min minimum value
     * @param max maximum value
     * @param steps number of steps
     * @return this builder
     */
    public Builder vary(String parameterPath, double min, double max, int steps) {
      double[] values = new double[steps];
      for (int i = 0; i < steps; i++) {
        values[i] = min + (max - min) * i / (steps - 1);
      }
      variations.add(new ParameterVariation(parameterPath, values));
      return this;
    }

    /**
     * Adds an objective to optimize.
     *
     * @param name objective name
     * @param direction minimize or maximize
     * @param extractor function to extract objective value from process
     * @return this builder
     */
    public Builder addObjective(String name, Objective direction,
        Function<ProcessSystem, Double> extractor) {
      objectives.put(name, new ObjectiveDefinition(name, direction));
      objectiveExtractors.put(name, extractor);
      return this;
    }

    /**
     * Sets the parallelism level.
     *
     * @param threads number of parallel threads
     * @return this builder
     */
    public Builder parallelism(int threads) {
      this.parallelism = threads;
      return this;
    }

    /**
     * Sets the study name.
     *
     * @param name study name
     * @return this builder
     */
    public Builder name(String name) {
      this.studyName = name;
      return this;
    }

    /**
     * Sets whether to stop on first failure.
     *
     * @param stop true to stop on failure
     * @return this builder
     */
    public Builder stopOnFailure(boolean stop) {
      this.stopOnFailure = stop;
      return this;
    }

    /**
     * Builds the batch study.
     *
     * @return the configured BatchStudy
     */
    public BatchStudy build() {
      return new BatchStudy(this);
    }
  }

  /**
   * Optimization direction.
   */
  public enum Objective {
    MINIMIZE, MAXIMIZE
  }

  /**
   * Definition of an objective.
   */
  public static class ObjectiveDefinition implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final String name;
    public final Objective direction;

    public ObjectiveDefinition(String name, Objective direction) {
      this.name = name;
      this.direction = direction;
    }
  }

  /**
   * A set of parameter values for one case.
   */
  public static class ParameterSet implements Serializable {
    private static final long serialVersionUID = 1000L;

    public String caseId;
    public final Map<String, Double> values;

    public ParameterSet(Map<String, Double> values) {
      this.values = values;
    }

    @Override
    public String toString() {
      return values.toString();
    }
  }

  /**
   * Result of a single case.
   */
  public static class CaseResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    public final ParameterSet parameters;
    public final boolean failed;
    public final String errorMessage;
    public Map<String, Double> objectiveValues = new HashMap<>();
    public Duration runtime;

    public CaseResult(ParameterSet parameters, boolean failed, String errorMessage) {
      this.parameters = parameters;
      this.failed = failed;
      this.errorMessage = errorMessage;
    }
  }

  /**
   * Results of a batch study.
   */
  public static class BatchStudyResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String studyName;
    private final int totalCases;
    private final List<CaseResult> results = new ArrayList<>();
    private Instant startTime;
    private Instant endTime;
    private int successCount = 0;
    private int failureCount = 0;

    BatchStudyResult(String studyName, int totalCases) {
      this.studyName = studyName;
      this.totalCases = totalCases;
      this.startTime = Instant.now();
    }

    void addResult(CaseResult result) {
      results.add(result);
      if (result.failed) {
        failureCount++;
      } else {
        successCount++;
      }
    }

    void complete() {
      this.endTime = Instant.now();
    }

    /**
     * Gets all successful results.
     *
     * @return list of successful results
     */
    public List<CaseResult> getSuccessfulResults() {
      return results.stream().filter(r -> !r.failed).collect(Collectors.toList());
    }

    /**
     * Gets the best case for a given objective.
     *
     * @param objectiveName the objective to optimize
     * @return the best case result
     */
    public CaseResult getBestCase(String objectiveName) {
      return getSuccessfulResults().stream()
          .filter(r -> r.objectiveValues.containsKey(objectiveName))
          .min(Comparator.comparingDouble(r -> r.objectiveValues.get(objectiveName))).orElse(null);
    }

    /**
     * Exports results to CSV.
     *
     * @param filePath output file path
     * @throws java.io.IOException if write fails
     */
    public void exportToCSV(String filePath) throws java.io.IOException {
      StringBuilder sb = new StringBuilder();

      // Header
      sb.append("CaseID,Status");
      if (!results.isEmpty() && results.get(0).parameters != null) {
        for (String param : results.get(0).parameters.values.keySet()) {
          sb.append(",").append(param);
        }
      }
      if (!results.isEmpty() && !results.get(0).objectiveValues.isEmpty()) {
        for (String obj : results.get(0).objectiveValues.keySet()) {
          sb.append(",").append(obj);
        }
      }
      sb.append(",Runtime_ms\n");

      // Data
      for (CaseResult r : results) {
        sb.append(r.parameters != null ? r.parameters.caseId : "N/A");
        sb.append(",").append(r.failed ? "FAILED" : "SUCCESS");

        if (r.parameters != null) {
          for (Double val : r.parameters.values.values()) {
            sb.append(",").append(val);
          }
        }
        for (Double val : r.objectiveValues.values()) {
          sb.append(",").append(val);
        }
        sb.append(",").append(r.runtime != null ? r.runtime.toMillis() : "N/A");
        sb.append("\n");
      }

      try (java.io.FileWriter writer = new java.io.FileWriter(filePath)) {
        writer.write(sb.toString());
      }
    }

    /**
     * Exports results to JSON format.
     *
     * @param filePath output file path
     * @throws java.io.IOException if write fails
     */
    public void exportToJSON(String filePath) throws java.io.IOException {
      com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
          .serializeSpecialFloatingPointValues().create();
      try (java.io.FileWriter writer = new java.io.FileWriter(filePath)) {
        gson.toJson(this, writer);
      }
    }

    /**
     * Converts results to a JSON string.
     *
     * @return JSON representation
     */
    public String toJson() {
      com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting()
          .serializeSpecialFloatingPointValues().create();
      return gson.toJson(this);
    }

    /**
     * Gets the Pareto-optimal front for two objectives.
     *
     * <p>
     * A case is Pareto-optimal if no other case is better in both objectives.
     *
     * @param objective1 first objective name (to minimize)
     * @param objective2 second objective name (to minimize)
     * @return list of Pareto-optimal cases
     */
    public List<CaseResult> getParetoFront(String objective1, String objective2) {
      List<CaseResult> successful =
          getSuccessfulResults().stream().filter(r -> r.objectiveValues.containsKey(objective1)
              && r.objectiveValues.containsKey(objective2)).collect(Collectors.toList());

      List<CaseResult> paretoFront = new ArrayList<>();

      for (CaseResult candidate : successful) {
        boolean isDominated = false;
        double val1 = candidate.objectiveValues.get(objective1);
        double val2 = candidate.objectiveValues.get(objective2);

        for (CaseResult other : successful) {
          if (candidate == other) {
            continue;
          }
          double otherVal1 = other.objectiveValues.get(objective1);
          double otherVal2 = other.objectiveValues.get(objective2);

          // Check if 'other' dominates 'candidate' (better or equal in both, strictly better in
          // one)
          if (otherVal1 <= val1 && otherVal2 <= val2 && (otherVal1 < val1 || otherVal2 < val2)) {
            isDominated = true;
            break;
          }
        }

        if (!isDominated) {
          paretoFront.add(candidate);
        }
      }

      // Sort by first objective for easier visualization
      paretoFront.sort(Comparator.comparingDouble(r -> r.objectiveValues.get(objective1)));

      return paretoFront;
    }

    /**
     * Gets study summary.
     *
     * @return formatted summary
     */
    public String getSummary() {
      Duration totalRuntime = Duration.between(startTime, endTime);

      StringBuilder sb = new StringBuilder();
      sb.append("=== Batch Study: ").append(studyName).append(" ===\n");
      sb.append("Total cases: ").append(totalCases).append("\n");
      sb.append("Successful: ").append(successCount).append("\n");
      sb.append("Failed: ").append(failureCount).append("\n");
      sb.append("Total runtime: ").append(totalRuntime.getSeconds()).append(" seconds\n");
      sb.append("Avg per case: ").append(totalRuntime.toMillis() / Math.max(1, results.size()))
          .append(" ms\n");

      return sb.toString();
    }

    public int getTotalCases() {
      return totalCases;
    }

    public int getSuccessCount() {
      return successCount;
    }

    public int getFailureCount() {
      return failureCount;
    }

    public List<CaseResult> getAllResults() {
      return results;
    }
  }

  /**
   * Definition of a parameter variation.
   */
  private static class ParameterVariation implements Serializable {
    private static final long serialVersionUID = 1000L;

    final String parameterName;
    final double[] values;

    ParameterVariation(String parameterName, double[] values) {
      this.parameterName = parameterName;
      this.values = values;
    }

    double[] getValues() {
      return values;
    }
  }
}
