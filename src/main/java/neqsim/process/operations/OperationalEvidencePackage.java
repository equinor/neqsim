package neqsim.process.operations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.measurementdevice.InstrumentTagRole;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Builds an operational evidence package from document-derived tags, field data, scenarios, and
 * NeqSim bottleneck analysis.
 *
 * <p>
 * The class intentionally orchestrates existing NeqSim features rather than replacing them:
 * {@link OperationalTagMap} applies and reads logical plant tags, {@link OperationalScenarioRunner}
 * executes what-if actions, and {@link ProcessSystem#findBottleneck()} plus the registered capacity
 * strategies identify limiting equipment.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalEvidencePackage {
  /** JSON serializer used for embedded operational result objects. */
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Default tolerance used when comparing benchmark tags. */
  public static final double DEFAULT_BENCHMARK_TOLERANCE_FRACTION = 0.05;

  /**
   * Private constructor for utility class.
   */
  private OperationalEvidencePackage() {}

  /**
   * Builds a complete operational evidence package report.
   *
   * @param studyName name of the study or operating case
   * @param process process system already built from NeqSim JSON
   * @param tagMap logical operational tag map, may be empty
   * @param fieldData field data keyed by logical tag or historian tag, may be empty
   * @param scenarios scenario list to run on copied base cases, may be empty
   * @param benchmarkToleranceFraction allowed relative deviation for benchmark tags; use a value
   *        from 0.0 to 1.0 where 0.05 means five percent
   * @return JSON object containing base evidence, benchmark comparison, bottlenecks, scenarios, and
   *         quality gates
   */
  public static JsonObject buildReport(String studyName, ProcessSystem process,
      OperationalTagMap tagMap, Map<String, Double> fieldData, List<OperationalScenario> scenarios,
      double benchmarkToleranceFraction) {
    requireProcess(process);
    OperationalTagMap effectiveTagMap = tagMap == null ? new OperationalTagMap() : tagMap;
    Map<String, Double> effectiveFieldData = fieldData == null ? new LinkedHashMap<String, Double>()
        : new LinkedHashMap<String, Double>(fieldData);
    List<OperationalScenario> effectiveScenarios =
        scenarios == null ? new ArrayList<OperationalScenario>()
            : new ArrayList<OperationalScenario>(scenarios);
    double tolerance = normalizeTolerance(benchmarkToleranceFraction);

    Map<String, Double> applied = new LinkedHashMap<String, Double>();
    if (!effectiveFieldData.isEmpty() && !effectiveTagMap.getBindings().isEmpty()) {
      applied = effectiveTagMap.applyFieldData(process, effectiveFieldData);
      process.run();
    }

    Map<String, Double> modelValues = effectiveTagMap.readValues(process);
    JsonObject benchmarkComparison =
        buildBenchmarkComparison(effectiveTagMap, effectiveFieldData, modelValues, tolerance);
    JsonObject baseCapacity = buildCapacityReport(process);
    JsonArray scenarioReports = buildScenarioReports(process, effectiveScenarios);
    JsonObject qualityGates = buildQualityGates(baseCapacity, benchmarkComparison, scenarioReports,
        !effectiveTagMap.getBindings().isEmpty());

    JsonObject report = new JsonObject();
    report.addProperty("studyName", cleanStudyName(studyName));
    report.addProperty("benchmarkToleranceFraction", tolerance);
    report.add("appliedFieldData", mapToJson(applied));
    report.add("modelValues", mapToJson(modelValues));
    report.add("benchmarkComparison", benchmarkComparison);
    report.add("baseCapacity", baseCapacity);
    report.add("scenarioStudies", scenarioReports);
    report.add("qualityGates", qualityGates);
    return report;
  }

  /**
   * Builds a capacity and bottleneck report for a process system.
   *
   * @param process process system to inspect
   * @return JSON object with bottleneck, utilization summary, near-limit equipment, and constraint
   *         details
   */
  public static JsonObject buildCapacityReport(ProcessSystem process) {
    requireProcess(process);

    BottleneckResult bottleneck = process.findBottleneck();
    String bottleneckSource = "ProcessSystem.findBottleneck";
    if (!bottleneck.hasBottleneck()) {
      bottleneck = findStrategyBottleneck(process);
      bottleneckSource = "EquipmentCapacityStrategyRegistry";
    }

    JsonArray equipmentDetails = buildEquipmentCapacityDetails(process);
    boolean hardLimitExceeded =
        process.isAnyHardLimitExceeded() || hasStrategyHardLimitExceeded(equipmentDetails);
    boolean equipmentOverloaded =
        process.isAnyEquipmentOverloaded() || hasStrategyCapacityExceeded(equipmentDetails);
    JsonArray nearCapacityEquipment = buildNearCapacityEquipment(process, equipmentDetails);

    JsonObject report = new JsonObject();
    report.add("bottleneck", bottleneckToJson(bottleneck, bottleneckSource));
    report.addProperty("hardLimitExceeded", hardLimitExceeded);
    report.addProperty("equipmentOverloaded", equipmentOverloaded);
    report.add("nearCapacityEquipment", nearCapacityEquipment);
    report.add("utilizationSummary", mapToJson(process.getCapacityUtilizationSummary()));
    report.add("equipmentConstraints", equipmentDetails);
    return report;
  }

  /**
   * Builds benchmark comparisons for tags marked as BENCHMARK.
   *
   * @param tagMap operational tag map
   * @param fieldData field data keyed by logical or historian tag
   * @param modelValues current model values keyed by logical tag
   * @param toleranceFraction allowed relative deviation
   * @return JSON benchmark comparison block
   */
  private static JsonObject buildBenchmarkComparison(OperationalTagMap tagMap,
      Map<String, Double> fieldData, Map<String, Double> modelValues, double toleranceFraction) {
    JsonArray comparisons = new JsonArray();
    int benchmarkCount = 0;
    int withinToleranceCount = 0;

    for (OperationalTagBinding binding : tagMap.getBindings()) {
      if (binding.getRole() != InstrumentTagRole.BENCHMARK) {
        continue;
      }
      benchmarkCount++;
      Double fieldValue = findFieldValue(binding, fieldData);
      Double modelValue = modelValues.get(binding.getLogicalTag());
      JsonObject entry = buildBenchmarkEntry(binding, fieldValue, modelValue, toleranceFraction);
      if (entry.get("withinTolerance").getAsBoolean()) {
        withinToleranceCount++;
      }
      comparisons.add(entry);
    }

    JsonObject result = new JsonObject();
    result.addProperty("benchmarkCount", benchmarkCount);
    result.addProperty("withinToleranceCount", withinToleranceCount);
    result.addProperty("allWithinTolerance", benchmarkCount == withinToleranceCount);
    result.add("comparisons", comparisons);
    return result;
  }

  /**
   * Builds one benchmark comparison entry.
   *
   * @param binding operational tag binding
   * @param fieldValue field value, or null when missing
   * @param modelValue model value, or null when missing
   * @param toleranceFraction allowed relative deviation
   * @return JSON benchmark entry
   */
  private static JsonObject buildBenchmarkEntry(OperationalTagBinding binding, Double fieldValue,
      Double modelValue, double toleranceFraction) {
    JsonObject entry = new JsonObject();
    entry.addProperty("logicalTag", binding.getLogicalTag());
    entry.addProperty("historianTag", binding.getHistorianTag());
    entry.addProperty("automationAddress", binding.getAutomationAddress());
    entry.addProperty("unit", binding.getUnit());
    addNullableNumber(entry, "fieldValue", fieldValue);
    addNullableNumber(entry, "modelValue", modelValue);

    boolean hasBoth = fieldValue != null && modelValue != null;
    double absoluteDeviation = hasBoth ? modelValue.doubleValue() - fieldValue.doubleValue() : 0.0;
    double denominator = hasBoth ? Math.max(Math.abs(fieldValue.doubleValue()), 1.0) : 1.0;
    double relativeDeviation = hasBoth ? Math.abs(absoluteDeviation) / denominator : 0.0;
    boolean withinTolerance = hasBoth && relativeDeviation <= toleranceFraction;

    entry.addProperty("hasFieldValue", fieldValue != null);
    entry.addProperty("hasModelValue", modelValue != null);
    entry.addProperty("absoluteDeviation", absoluteDeviation);
    entry.addProperty("relativeDeviationFraction", relativeDeviation);
    entry.addProperty("withinTolerance", withinTolerance);
    return entry;
  }

  /**
   * Finds a field value for a binding from logical or historian keys.
   *
   * @param binding operational tag binding
   * @param fieldData source field data
   * @return matched field value, or null when no value is present
   */
  private static Double findFieldValue(OperationalTagBinding binding,
      Map<String, Double> fieldData) {
    if (fieldData.containsKey(binding.getLogicalTag())) {
      return fieldData.get(binding.getLogicalTag());
    }
    if (binding.hasHistorianTag() && fieldData.containsKey(binding.getHistorianTag())) {
      return fieldData.get(binding.getHistorianTag());
    }
    return null;
  }

  /**
   * Runs all configured scenarios on copied base cases.
   *
   * @param baseProcess base process after field data application
   * @param scenarios scenarios to run
   * @return JSON array of scenario evidence blocks
   */
  private static JsonArray buildScenarioReports(ProcessSystem baseProcess,
      List<OperationalScenario> scenarios) {
    JsonArray reports = new JsonArray();
    for (OperationalScenario scenario : scenarios) {
      ProcessSystem scenarioProcess = baseProcess.copy();
      OperationalScenarioResult scenarioResult =
          OperationalScenarioRunner.run(scenarioProcess, scenario);
      JsonObject scenarioReport = new JsonObject();
      scenarioReport.addProperty("scenarioName", scenario.getName());
      scenarioReport.addProperty("successful", scenarioResult.isSuccessful());
      scenarioReport.add("scenarioResult", JsonParser.parseString(scenarioResult.toJson()));
      scenarioReport.add("capacity", buildCapacityReport(scenarioProcess));
      reports.add(scenarioReport);
    }
    return reports;
  }

  /**
   * Builds quality gates for the operational evidence package.
   *
   * @param baseCapacity base capacity report
   * @param benchmarkComparison benchmark comparison block
   * @param scenarioReports scenario reports
   * @param hasTagBindings true when tag bindings were supplied
   * @return JSON object with gate status values
   */
  private static JsonObject buildQualityGates(JsonObject baseCapacity,
      JsonObject benchmarkComparison, JsonArray scenarioReports, boolean hasTagBindings) {
    boolean benchmarksWithinTolerance =
        benchmarkComparison.get("allWithinTolerance").getAsBoolean();
    boolean noHardLimitExceeded = !baseCapacity.get("hardLimitExceeded").getAsBoolean();
    boolean noEquipmentOverloaded = !baseCapacity.get("equipmentOverloaded").getAsBoolean();
    boolean scenariosSuccessful = areScenariosSuccessful(scenarioReports);

    JsonObject gates = new JsonObject();
    gates.addProperty("tagBindingsProvided", hasTagBindings);
    gates.addProperty("benchmarksWithinTolerance", benchmarksWithinTolerance);
    gates.addProperty("hardLimitsSatisfied", noHardLimitExceeded);
    gates.addProperty("capacityWithinDesign", noEquipmentOverloaded);
    gates.addProperty("scenariosSuccessful", scenariosSuccessful);
    gates.addProperty("acceptableForOperationScreening", benchmarksWithinTolerance
        && noHardLimitExceeded && noEquipmentOverloaded && scenariosSuccessful);
    return gates;
  }

  /**
   * Checks whether every scenario report is successful.
   *
   * @param scenarioReports scenario report array
   * @return true when no scenario has failed
   */
  private static boolean areScenariosSuccessful(JsonArray scenarioReports) {
    for (int i = 0; i < scenarioReports.size(); i++) {
      if (!scenarioReports.get(i).getAsJsonObject().get("successful").getAsBoolean()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the bottleneck using registered equipment capacity strategies.
   *
   * @param process process system to inspect
   * @return bottleneck result, or an empty result when no strategy provides constraints
   */
  private static BottleneckResult findStrategyBottleneck(ProcessSystem process) {
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    ProcessEquipmentInterface bottleneckEquipment = null;
    CapacityConstraint limitingConstraint = null;
    double maxUtilization = 0.0;

    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      EquipmentCapacityStrategy strategy = registry.findStrategy(unit);
      if (strategy == null) {
        continue;
      }
      CapacityConstraint constraint = strategy.getBottleneckConstraint(unit);
      if (constraint == null || !constraint.isEnabled()) {
        continue;
      }
      double utilization = constraint.getUtilization();
      if (!Double.isNaN(utilization) && !Double.isInfinite(utilization)
          && utilization > maxUtilization) {
        maxUtilization = utilization;
        bottleneckEquipment = unit;
        limitingConstraint = constraint;
      }
    }

    if (bottleneckEquipment == null) {
      return BottleneckResult.empty();
    }
    return new BottleneckResult(bottleneckEquipment, limitingConstraint, maxUtilization);
  }

  /**
   * Converts a bottleneck result to JSON.
   *
   * @param bottleneck bottleneck result
   * @param source source used to detect the bottleneck
   * @return JSON object
   */
  private static JsonObject bottleneckToJson(BottleneckResult bottleneck, String source) {
    JsonObject object = new JsonObject();
    object.addProperty("source", source);
    object.addProperty("hasBottleneck", bottleneck.hasBottleneck());
    object.addProperty("equipmentName", bottleneck.getEquipmentName());
    object.addProperty("constraintName", bottleneck.getConstraintName());
    object.addProperty("utilization", bottleneck.getUtilization());
    object.addProperty("utilizationPercent", bottleneck.getUtilizationPercent());
    object.addProperty("marginPercent", bottleneck.getMarginPercent());
    object.addProperty("exceeded", bottleneck.isExceeded());
    object.addProperty("nearLimit", bottleneck.isNearLimit());
    if (bottleneck.getEquipment() != null) {
      object.addProperty("equipmentType", bottleneck.getEquipment().getClass().getSimpleName());
    }
    if (bottleneck.getConstraint() != null) {
      object.add("constraint", constraintToJson(bottleneck.getConstraint()));
    }
    return object;
  }

  /**
   * Builds capacity details for every equipment item with constraints.
   *
   * @param process process system to inspect
   * @return JSON array of equipment capacity details
   */
  private static JsonArray buildEquipmentCapacityDetails(ProcessSystem process) {
    JsonArray array = new JsonArray();
    EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      Map<String, CapacityConstraint> constraints = unit.getCapacityConstraints();
      String source = "ProcessEquipmentInterface";
      EquipmentCapacityStrategy strategy = registry.findStrategy(unit);
      if ((constraints == null || constraints.isEmpty()) && strategy != null) {
        constraints = strategy.getConstraints(unit);
        source = strategy.getName();
      }
      if (constraints == null || constraints.isEmpty()) {
        continue;
      }

      JsonArray constraintArray = new JsonArray();
      double maxUtilization = 0.0;
      boolean exceeded = false;
      boolean hardLimitExceeded = false;
      boolean nearLimit = false;
      for (CapacityConstraint constraint : constraints.values()) {
        if (!constraint.isEnabled()) {
          continue;
        }
        double utilization = constraint.getUtilization();
        maxUtilization = Math.max(maxUtilization, utilization);
        exceeded = exceeded || constraint.isViolated();
        hardLimitExceeded = hardLimitExceeded || constraint.isHardLimitExceeded();
        nearLimit = nearLimit || constraint.isNearLimit();
        constraintArray.add(constraintToJson(constraint));
      }
      if (constraintArray.size() == 0) {
        continue;
      }

      JsonObject equipment = new JsonObject();
      equipment.addProperty("equipmentName", unit.getName());
      equipment.addProperty("equipmentType", unit.getClass().getSimpleName());
      equipment.addProperty("constraintSource", source);
      equipment.addProperty("maxUtilization", maxUtilization);
      equipment.addProperty("maxUtilizationPercent", maxUtilization * 100.0);
      equipment.addProperty("availableMarginPercent", (1.0 - maxUtilization) * 100.0);
      equipment.addProperty("capacityExceeded", exceeded);
      equipment.addProperty("hardLimitExceeded", hardLimitExceeded);
      equipment.addProperty("nearLimit", nearLimit);
      equipment.add("constraints", constraintArray);
      array.add(equipment);
    }
    return array;
  }

  /**
   * Converts one capacity constraint to JSON.
   *
   * @param constraint capacity constraint
   * @return JSON object
   */
  private static JsonObject constraintToJson(CapacityConstraint constraint) {
    JsonObject object = new JsonObject();
    object.addProperty("name", constraint.getName());
    object.addProperty("unit", constraint.getUnit());
    object.addProperty("type", constraint.getType().name());
    object.addProperty("severity", constraint.getSeverity().name());
    object.addProperty("currentValue", constraint.getCurrentValue());
    object.addProperty("designValue", constraint.getDisplayDesignValue());
    object.addProperty("maxValue", constraint.getMaxValue());
    object.addProperty("minValue", constraint.getMinValue());
    object.addProperty("warningThreshold", constraint.getWarningThreshold());
    object.addProperty("utilization", constraint.getUtilization());
    object.addProperty("utilizationPercent", constraint.getUtilizationPercent());
    object.addProperty("marginPercent", constraint.getMarginPercent());
    object.addProperty("violated", constraint.isViolated());
    object.addProperty("hardLimitExceeded", constraint.isHardLimitExceeded());
    object.addProperty("nearLimit", constraint.isNearLimit());
    object.addProperty("minimumConstraint", constraint.isMinimumConstraint());
    object.addProperty("description", constraint.getDescription());
    object.addProperty("dataSource", constraint.getDataSource());
    return object;
  }

  /**
   * Checks whether any strategy-derived equipment detail exceeds a hard limit.
   *
   * @param equipmentDetails equipment details array
   * @return true if any detail has a hard limit exceeded
   */
  private static boolean hasStrategyHardLimitExceeded(JsonArray equipmentDetails) {
    for (int i = 0; i < equipmentDetails.size(); i++) {
      if (equipmentDetails.get(i).getAsJsonObject().get("hardLimitExceeded").getAsBoolean()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether any strategy-derived equipment detail exceeds design capacity.
   *
   * @param equipmentDetails equipment details array
   * @return true if any detail is over design capacity
   */
  private static boolean hasStrategyCapacityExceeded(JsonArray equipmentDetails) {
    for (int i = 0; i < equipmentDetails.size(); i++) {
      if (equipmentDetails.get(i).getAsJsonObject().get("capacityExceeded").getAsBoolean()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds the near-capacity equipment array from direct and strategy-derived sources.
   *
   * @param process process system
   * @param equipmentDetails equipment details array
   * @return JSON array of equipment names
   */
  private static JsonArray buildNearCapacityEquipment(ProcessSystem process,
      JsonArray equipmentDetails) {
    Set<String> names = new LinkedHashSet<String>(process.getEquipmentNearCapacityLimit());
    for (int i = 0; i < equipmentDetails.size(); i++) {
      JsonObject detail = equipmentDetails.get(i).getAsJsonObject();
      if (detail.get("nearLimit").getAsBoolean()) {
        names.add(detail.get("equipmentName").getAsString());
      }
    }
    JsonArray array = new JsonArray();
    for (String name : names) {
      array.add(name);
    }
    return array;
  }

  /**
   * Converts a map of doubles to a JSON object.
   *
   * @param values values to convert
   * @return JSON object
   */
  private static JsonObject mapToJson(Map<String, Double> values) {
    JsonObject object = new JsonObject();
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      object.addProperty(entry.getKey(), entry.getValue());
    }
    return object;
  }

  /**
   * Adds a nullable number to a JSON object.
   *
   * @param object target object
   * @param name property name
   * @param value number value, or null
   */
  private static void addNullableNumber(JsonObject object, String name, Double value) {
    if (value == null) {
      object.add(name, GSON.toJsonTree(null));
    } else {
      object.addProperty(name, value);
    }
  }

  /**
   * Requires a non-null process system.
   *
   * @param process process system to validate
   * @throws IllegalArgumentException if process is null
   */
  private static void requireProcess(ProcessSystem process) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
  }

  /**
   * Normalizes benchmark tolerance input.
   *
   * @param toleranceFraction requested tolerance fraction
   * @return valid non-negative tolerance fraction
   */
  private static double normalizeTolerance(double toleranceFraction) {
    if (Double.isNaN(toleranceFraction) || toleranceFraction < 0.0) {
      return DEFAULT_BENCHMARK_TOLERANCE_FRACTION;
    }
    return toleranceFraction;
  }

  /**
   * Cleans the study name used in reports.
   *
   * @param studyName raw study name
   * @return non-empty study name
   */
  private static String cleanStudyName(String studyName) {
    if (studyName == null || studyName.trim().isEmpty()) {
      return "operational evidence package";
    }
    return studyName.trim();
  }
}
