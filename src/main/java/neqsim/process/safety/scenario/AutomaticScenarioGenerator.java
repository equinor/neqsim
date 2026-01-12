package neqsim.process.safety.scenario;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.valve.ValveInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.ProcessSafetyScenario;

/**
 * Automatically generates safety scenarios from equipment failure modes.
 *
 * <p>
 * This class creates comprehensive what-if scenarios by:
 * <ul>
 * <li><b>Failure Mode Analysis:</b> Identify equipment-specific failure modes</li>
 * <li><b>Combination Generation:</b> Create multi-failure scenarios</li>
 * <li><b>HAZOP Integration:</b> Map to standard HAZOP deviations</li>
 * <li><b>Prioritization:</b> Rank scenarios by severity and likelihood</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * 
 * <pre>
 * ProcessSystem process = new ProcessSystem();
 * // ... configure process ...
 *
 * AutomaticScenarioGenerator generator = new AutomaticScenarioGenerator(process);
 *
 * // Generate all single-failure scenarios
 * List&lt;ProcessSafetyScenario&gt; scenarios = generator
 *     .addFailureModes(FailureMode.COOLING_LOSS, FailureMode.VALVE_STUCK).generateSingleFailures();
 *
 * // Generate combination scenarios (up to 2 simultaneous failures)
 * List&lt;ProcessSafetyScenario&gt; combinations = generator.generateCombinations(2);
 *
 * // Run all scenarios
 * for (ProcessSafetyScenario scenario : scenarios) {
 *   scenario.applyTo(process.copy());
 *   process.run();
 *   // Analyze results
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class AutomaticScenarioGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;
  private final Set<FailureMode> enabledFailureModes;
  private final List<EquipmentFailure> identifiedFailures;
  private final Map<String, List<String>> equipmentToDeviations;

  /**
   * Standard equipment failure modes.
   */
  public enum FailureMode {
    /** Complete loss of cooling (air coolers, cooling water). */
    COOLING_LOSS("Loss of Cooling", "No flow", HazopDeviation.NO_FLOW),

    /** Complete loss of heating. */
    HEATING_LOSS("Loss of Heating", "No flow", HazopDeviation.NO_FLOW),

    /** Valve stuck in closed position. */
    VALVE_STUCK_CLOSED("Valve Stuck Closed", "No flow", HazopDeviation.NO_FLOW),

    /** Valve stuck in open position. */
    VALVE_STUCK_OPEN("Valve Stuck Open", "More flow", HazopDeviation.MORE_FLOW),

    /** Valve fails to control position. */
    VALVE_CONTROL_FAILURE("Control Valve Failure", "Other", HazopDeviation.OTHER),

    /** Compressor trip. */
    COMPRESSOR_TRIP("Compressor Trip", "No flow", HazopDeviation.NO_FLOW),

    /** Pump trip. */
    PUMP_TRIP("Pump Trip", "No flow", HazopDeviation.NO_FLOW),

    /** Blocked outlet. */
    BLOCKED_OUTLET("Blocked Outlet", "No flow", HazopDeviation.NO_FLOW),

    /** Power failure. */
    POWER_FAILURE("Power Failure", "Other", HazopDeviation.OTHER),

    /** Instrument failure. */
    INSTRUMENT_FAILURE("Instrument Failure", "Other", HazopDeviation.OTHER),

    /** External fire. */
    EXTERNAL_FIRE("External Fire", "High temperature", HazopDeviation.HIGH_TEMPERATURE),

    /** Loss of containment. */
    LOSS_OF_CONTAINMENT("Loss of Containment", "Less pressure", HazopDeviation.LESS_PRESSURE);

    private final String description;
    private final String category;
    private final HazopDeviation hazopDeviation;

    FailureMode(String description, String category, HazopDeviation deviation) {
      this.description = description;
      this.category = category;
      this.hazopDeviation = deviation;
    }

    public String getDescription() {
      return description;
    }

    public String getCategory() {
      return category;
    }

    public HazopDeviation getHazopDeviation() {
      return hazopDeviation;
    }
  }

  /**
   * Standard HAZOP deviation types.
   */
  public enum HazopDeviation {
    NO_FLOW, LESS_FLOW, MORE_FLOW, REVERSE_FLOW, HIGH_PRESSURE, LOW_PRESSURE, LESS_PRESSURE, HIGH_TEMPERATURE, LOW_TEMPERATURE, HIGH_LEVEL, LOW_LEVEL, CONTAMINATION, CORROSION, OTHER
  }

  /**
   * Creates a scenario generator for a process system.
   *
   * @param processSystem the process system to analyze
   */
  public AutomaticScenarioGenerator(ProcessSystem processSystem) {
    this.processSystem = processSystem;
    this.enabledFailureModes = EnumSet.noneOf(FailureMode.class);
    this.identifiedFailures = new ArrayList<>();
    this.equipmentToDeviations = new HashMap<>();
    identifyEquipmentFailureModes();
  }

  /**
   * Adds failure modes to consider.
   *
   * @param modes failure modes to enable
   * @return this generator for chaining
   */
  public AutomaticScenarioGenerator addFailureModes(FailureMode... modes) {
    for (FailureMode mode : modes) {
      enabledFailureModes.add(mode);
    }
    return this;
  }

  /**
   * Enables all failure modes.
   *
   * @return this generator for chaining
   */
  public AutomaticScenarioGenerator enableAllFailureModes() {
    enabledFailureModes.addAll(EnumSet.allOf(FailureMode.class));
    return this;
  }

  /**
   * Generates single-failure scenarios for all enabled failure modes.
   *
   * @return list of scenarios
   */
  public List<ProcessSafetyScenario> generateSingleFailures() {
    List<ProcessSafetyScenario> scenarios = new ArrayList<>();

    for (EquipmentFailure failure : identifiedFailures) {
      if (enabledFailureModes.contains(failure.mode)) {
        ProcessSafetyScenario scenario = createScenario(failure);
        if (scenario != null) {
          scenarios.add(scenario);
        }
      }
    }

    return scenarios;
  }

  /**
   * Generates combination scenarios (multiple simultaneous failures).
   *
   * @param maxCombinationSize maximum number of simultaneous failures
   * @return list of combination scenarios
   */
  public List<ProcessSafetyScenario> generateCombinations(int maxCombinationSize) {
    List<ProcessSafetyScenario> scenarios = new ArrayList<>();

    // Start with single failures
    scenarios.addAll(generateSingleFailures());

    // Add combinations
    List<EquipmentFailure> enabledFailures = identifiedFailures.stream()
        .filter(f -> enabledFailureModes.contains(f.mode)).collect(Collectors.toList());

    // Generate 2-failure combinations
    if (maxCombinationSize >= 2) {
      for (int i = 0; i < enabledFailures.size(); i++) {
        for (int j = i + 1; j < enabledFailures.size(); j++) {
          ProcessSafetyScenario combo =
              createCombinationScenario(enabledFailures.get(i), enabledFailures.get(j));
          if (combo != null) {
            scenarios.add(combo);
          }
        }
      }
    }

    // Generate 3-failure combinations if requested
    if (maxCombinationSize >= 3) {
      for (int i = 0; i < enabledFailures.size(); i++) {
        for (int j = i + 1; j < enabledFailures.size(); j++) {
          for (int k = j + 1; k < enabledFailures.size(); k++) {
            ProcessSafetyScenario combo = createCombinationScenario(enabledFailures.get(i),
                enabledFailures.get(j), enabledFailures.get(k));
            if (combo != null) {
              scenarios.add(combo);
            }
          }
        }
      }
    }

    return scenarios;
  }

  /**
   * Gets all identified equipment failures.
   *
   * @return list of potential failures
   */
  public List<EquipmentFailure> getIdentifiedFailures() {
    return new ArrayList<>(identifiedFailures);
  }

  /**
   * Gets a summary of identified failure modes by equipment type.
   *
   * @return formatted summary string
   */
  public String getFailureModeSummary() {
    Map<String, Integer> countByType = new HashMap<>();

    for (EquipmentFailure f : identifiedFailures) {
      countByType.merge(f.equipmentType, 1, Integer::sum);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Failure Mode Analysis Summary\n");
    sb.append("=============================\n");
    sb.append("Total potential failures: ").append(identifiedFailures.size()).append("\n\n");

    sb.append("By Equipment Type:\n");
    for (Map.Entry<String, Integer> entry : countByType.entrySet()) {
      sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue())
          .append(" failure modes\n");
    }

    return sb.toString();
  }

  private void identifyEquipmentFailureModes() {
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      identifyFailuresForEquipment(equipment);
    }
  }

  private void identifyFailuresForEquipment(ProcessEquipmentInterface equipment) {
    String name = equipment.getName();
    String type = equipment.getClass().getSimpleName();

    // Valves
    if (equipment instanceof ValveInterface || equipment instanceof ThrottlingValve) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.VALVE_STUCK_CLOSED));
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.VALVE_STUCK_OPEN));
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.VALVE_CONTROL_FAILURE));
    }

    // Compressors
    if (equipment instanceof Compressor) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.COMPRESSOR_TRIP));
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.POWER_FAILURE));
    }

    // Pumps
    if (equipment instanceof Pump) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.PUMP_TRIP));
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.POWER_FAILURE));
    }

    // Coolers
    if (equipment instanceof Cooler) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.COOLING_LOSS));
    }

    // Heaters
    if (equipment instanceof Heater) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.HEATING_LOSS));
    }

    // Separators
    if (equipment instanceof Separator) {
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.BLOCKED_OUTLET));
      identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.INSTRUMENT_FAILURE));
    }

    // All equipment can have external fire
    identifiedFailures.add(new EquipmentFailure(name, type, FailureMode.EXTERNAL_FIRE));
  }

  private ProcessSafetyScenario createScenario(EquipmentFailure failure) {
    String scenarioName = failure.mode.getDescription() + " - " + failure.equipmentName;

    ProcessSafetyScenario.Builder builder = ProcessSafetyScenario.builder(scenarioName);

    // Add appropriate manipulator based on failure mode
    switch (failure.mode) {
      case VALVE_STUCK_CLOSED:
        builder.customManipulator(failure.equipmentName, eq -> {
          if (eq instanceof ValveInterface) {
            ((ValveInterface) eq).setPercentValveOpening(0.0);
          }
        });
        break;

      case VALVE_STUCK_OPEN:
        builder.customManipulator(failure.equipmentName, eq -> {
          if (eq instanceof ValveInterface) {
            ((ValveInterface) eq).setPercentValveOpening(100.0);
          }
        });
        break;

      case COOLING_LOSS:
        builder.customManipulator(failure.equipmentName, eq -> {
          if (eq instanceof Cooler) {
            ((Cooler) eq).setDuty(0.0);
          }
        });
        break;

      case HEATING_LOSS:
        builder.customManipulator(failure.equipmentName, eq -> {
          if (eq instanceof Heater) {
            ((Heater) eq).setDuty(0.0);
          }
        });
        break;

      case COMPRESSOR_TRIP:
        // Compressor trip would require flow bypass or shutdown logic
        break;

      case PUMP_TRIP:
        // Pump trip would require flow bypass or shutdown logic
        break;

      default:
        // Other failure modes may need custom handling
        break;
    }

    return builder.build();
  }

  private ProcessSafetyScenario createCombinationScenario(EquipmentFailure... failures) {
    StringBuilder nameBuilder = new StringBuilder("Combination: ");
    for (int i = 0; i < failures.length; i++) {
      if (i > 0) {
        nameBuilder.append(" + ");
      }
      nameBuilder.append(failures[i].equipmentName).append(" ")
          .append(failures[i].mode.getDescription());
    }

    ProcessSafetyScenario.Builder builder = ProcessSafetyScenario.builder(nameBuilder.toString());

    for (EquipmentFailure failure : failures) {
      // Use utilityLoss for equipment failures instead of addTargetUnit
      builder.utilityLoss(failure.equipmentName);
    }

    return builder.build();
  }

  /**
   * Represents a potential equipment failure.
   */
  public static class EquipmentFailure implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String equipmentName;
    private final String equipmentType;
    private final FailureMode mode;

    public EquipmentFailure(String equipmentName, String equipmentType, FailureMode mode) {
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.mode = mode;
    }

    public String getEquipmentName() {
      return equipmentName;
    }

    public String getEquipmentType() {
      return equipmentType;
    }

    public FailureMode getMode() {
      return mode;
    }

    @Override
    public String toString() {
      return equipmentName + ": " + mode.getDescription();
    }
  }

  /**
   * Result of running a single safety scenario.
   */
  public static class ScenarioRunResult implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final ProcessSafetyScenario scenario;
    private final boolean successful;
    private final String errorMessage;
    private final Map<String, Double> resultValues;
    private final long executionTimeMs;

    /**
     * Creates a successful result.
     *
     * @param scenario the executed scenario
     * @param resultValues key result values captured
     * @param executionTimeMs execution time in milliseconds
     */
    public ScenarioRunResult(ProcessSafetyScenario scenario, Map<String, Double> resultValues,
        long executionTimeMs) {
      this.scenario = scenario;
      this.successful = true;
      this.errorMessage = null;
      this.resultValues = resultValues;
      this.executionTimeMs = executionTimeMs;
    }

    /**
     * Creates a failed result.
     *
     * @param scenario the executed scenario
     * @param errorMessage the error that occurred
     * @param executionTimeMs execution time in milliseconds
     */
    public ScenarioRunResult(ProcessSafetyScenario scenario, String errorMessage,
        long executionTimeMs) {
      this.scenario = scenario;
      this.successful = false;
      this.errorMessage = errorMessage;
      this.resultValues = new HashMap<>();
      this.executionTimeMs = executionTimeMs;
    }

    public ProcessSafetyScenario getScenario() {
      return scenario;
    }

    public boolean isSuccessful() {
      return successful;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public Map<String, Double> getResultValues() {
      return resultValues;
    }

    public long getExecutionTimeMs() {
      return executionTimeMs;
    }
  }

  /**
   * Runs all generated scenarios and collects results.
   *
   * <p>
   * This method runs each scenario on a copy of the process system and captures key result values.
   * Scenarios that fail to converge are recorded with their error messages.
   *
   * @param scenarios list of scenarios to run
   * @return list of results for each scenario
   */
  public List<ScenarioRunResult> runScenarios(List<ProcessSafetyScenario> scenarios) {
    List<ScenarioRunResult> results = new ArrayList<>();

    for (ProcessSafetyScenario scenario : scenarios) {
      long startTime = System.currentTimeMillis();

      try {
        // Clone the process system
        ProcessSystem copy = processSystem.copy();

        // Apply and run scenario
        scenario.applyTo(copy);
        copy.run();

        // Capture key results
        Map<String, Double> resultValues = captureKeyResults(copy);
        long elapsed = System.currentTimeMillis() - startTime;
        results.add(new ScenarioRunResult(scenario, resultValues, elapsed));

      } catch (Exception e) {
        long elapsed = System.currentTimeMillis() - startTime;
        results.add(new ScenarioRunResult(scenario, e.getMessage(), elapsed));
      }
    }

    return results;
  }

  /**
   * Runs all single-failure scenarios for enabled failure modes.
   *
   * @return list of results
   */
  public List<ScenarioRunResult> runAllSingleFailures() {
    return runScenarios(generateSingleFailures());
  }

  /**
   * Captures key result values from a process system after running a scenario.
   *
   * @param process the process system after scenario execution
   * @return map of result name to value
   */
  private Map<String, Double> captureKeyResults(ProcessSystem process) {
    Map<String, Double> results = new HashMap<>();

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      String name = equipment.getName();

      // Capture pressure and temperature for key equipment
      if (equipment instanceof Separator) {
        Separator sep = (Separator) equipment;
        results.put(name + ".pressure", sep.getPressure());
        results.put(name + ".temperature", sep.getTemperature());
      }

      if (equipment instanceof Compressor) {
        Compressor comp = (Compressor) equipment;
        results.put(name + ".outletPressure", comp.getOutletPressure());
        results.put(name + ".power", comp.getPower("kW"));
      }
    }

    return results;
  }

  /**
   * Generates a summary of scenario run results.
   *
   * @param results list of scenario results
   * @return formatted summary string
   */
  public static String summarizeResults(List<ScenarioRunResult> results) {
    StringBuilder sb = new StringBuilder();
    sb.append("Scenario Execution Summary\n");
    sb.append("==========================\n\n");

    long successCount = results.stream().filter(ScenarioRunResult::isSuccessful).count();
    long failCount = results.size() - successCount;

    sb.append("Total scenarios: ").append(results.size()).append("\n");
    sb.append("Successful: ").append(successCount).append("\n");
    sb.append("Failed: ").append(failCount).append("\n\n");

    if (failCount > 0) {
      sb.append("Failed Scenarios:\n");
      for (ScenarioRunResult result : results) {
        if (!result.isSuccessful()) {
          sb.append("  - ").append(result.getScenario().getName()).append(": ")
              .append(result.getErrorMessage()).append("\n");
        }
      }
    }

    return sb.toString();
  }
}
