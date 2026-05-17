package neqsim.process.safety.esd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.operations.OperationalTagBinding;
import neqsim.process.operations.OperationalTagMap;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Runs emergency shutdown dynamic tests and builds structured evidence reports.
 *
 * <p>
 * The runner reuses existing NeqSim process equipment, {@link ProcessLogic} sequences,
 * {@link ProcessSystem#runTransient(double, UUID)}, and operational tag maps. It does not emulate a
 * certified SIS logic solver; instead, it verifies the process response when documented ESD actions
 * are represented by NeqSim logic and equipment models.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class EmergencyShutdownTestRunner {

  /** Private constructor for utility class. */
  private EmergencyShutdownTestRunner() {}

  /**
   * Runs an ESD dynamic test with one or more process logic sequences.
   *
   * @param process process system to test
   * @param plan ESD test plan
   * @param logicSequences process logic sequences available to the test
   * @return ESD test report
   */
  public static EmergencyShutdownTestResult run(ProcessSystem process,
      EmergencyShutdownTestPlan plan, ProcessLogic... logicSequences) {
    List<ProcessLogic> logicList =
        logicSequences == null ? new ArrayList<ProcessLogic>() : Arrays.asList(logicSequences);
    return run(process, plan, logicList);
  }

  /**
   * Runs an ESD dynamic test with a list of process logic sequences.
   *
   * @param process process system to test
   * @param plan ESD test plan
   * @param logicSequences process logic sequences available to the test
   * @return ESD test report
   */
  public static EmergencyShutdownTestResult run(ProcessSystem process,
      EmergencyShutdownTestPlan plan, List<ProcessLogic> logicSequences) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    if (plan == null) {
      throw new IllegalArgumentException("plan must not be null");
    }

    EmergencyShutdownTestResult result = new EmergencyShutdownTestResult(plan);
    List<ProcessLogic> availableLogic = logicSequences == null ? new ArrayList<ProcessLogic>()
        : new ArrayList<ProcessLogic>(logicSequences);
    List<ProcessLogic> enabledLogic =
        selectLogic(availableLogic, plan.getEnabledLogicNames(), result);
    List<ProcessLogic> triggerLogic = selectTriggerLogic(enabledLogic, plan, result);

    applyFieldData(process, plan, result);
    initializeProcess(process, plan, result);
    applyScenario(process, plan, result);
    result.addSample(captureSample(0.0, process, plan, result), plan.getMonitoredUnits());

    runTransientLoop(process, plan, enabledLogic, triggerLogic, result);
    recordLogicStates(availableLogic, result);
    buildFieldComparisons(plan, result);
    evaluateCriteria(plan, result);
    result.finalizeVerdict();
    return result;
  }

  /**
   * Applies field data through the operational tag map.
   *
   * @param process process system
   * @param plan test plan
   * @param result result receiving warnings and errors
   */
  private static void applyFieldData(ProcessSystem process, EmergencyShutdownTestPlan plan,
      EmergencyShutdownTestResult result) {
    if (plan.getFieldData().isEmpty() || plan.getTagMap().getBindings().isEmpty()) {
      return;
    }
    try {
      plan.getTagMap().applyFieldData(process, plan.getFieldData());
    } catch (RuntimeException ex) {
      result.addError("Field data application failed: " + ex.getMessage());
    }
  }

  /**
   * Runs the initial steady-state calculation when configured.
   *
   * @param process process system
   * @param plan test plan
   * @param result result receiving warnings and errors
   */
  private static void initializeProcess(ProcessSystem process, EmergencyShutdownTestPlan plan,
      EmergencyShutdownTestResult result) {
    if (!plan.isInitializeSteadyState()) {
      return;
    }
    try {
      process.run();
    } catch (RuntimeException ex) {
      result.addError("Initial steady-state calculation failed: " + ex.getMessage());
    }
  }

  /**
   * Applies the configured process safety scenario.
   *
   * @param process process system
   * @param plan test plan
   * @param result result receiving warnings and errors
   */
  private static void applyScenario(ProcessSystem process, EmergencyShutdownTestPlan plan,
      EmergencyShutdownTestResult result) {
    if (plan.getScenario() == null) {
      return;
    }
    try {
      plan.getScenario().applyTo(process);
    } catch (RuntimeException ex) {
      result.addError("Scenario application failed: " + ex.getMessage());
    }
  }

  /**
   * Runs the transient loop and captures monitored samples.
   *
   * @param process process system
   * @param plan test plan
   * @param enabledLogic logic sequences executed each step
   * @param triggerLogic logic sequences activated at trigger time
   * @param result result receiving samples, warnings, and errors
   */
  private static void runTransientLoop(ProcessSystem process, EmergencyShutdownTestPlan plan,
      List<ProcessLogic> enabledLogic, List<ProcessLogic> triggerLogic,
      EmergencyShutdownTestResult result) {
    double time = 0.0;
    boolean triggered = false;
    UUID runId = UUID.randomUUID();
    while (time < plan.getDurationSeconds() - 1.0e-12) {
      if (!triggered && time >= plan.getTriggerTimeSeconds() - 1.0e-12) {
        activateLogic(triggerLogic, result);
        triggered = true;
      }
      executeLogic(enabledLogic, plan.getTimeStepSeconds(), result);
      double step = Math.min(plan.getTimeStepSeconds(), plan.getDurationSeconds() - time);
      try {
        process.runTransient(step, runId);
      } catch (RuntimeException ex) {
        result.addError("Transient simulation failed at t=" + time + " s: " + ex.getMessage());
      }
      time += step;
      result.addSample(captureSample(time, process, plan, result), plan.getMonitoredUnits());
    }
  }

  /**
   * Selects enabled logic from the supplied logic list.
   *
   * @param availableLogic available logic sequences
   * @param names requested logic names
   * @param result result receiving warnings
   * @return selected logic list
   */
  private static List<ProcessLogic> selectLogic(List<ProcessLogic> availableLogic,
      List<String> names, EmergencyShutdownTestResult result) {
    if (names == null || names.isEmpty()) {
      return new ArrayList<ProcessLogic>(availableLogic);
    }
    List<ProcessLogic> selected = new ArrayList<ProcessLogic>();
    Set<String> found = new LinkedHashSet<String>();
    for (ProcessLogic logic : availableLogic) {
      if (names.contains(logic.getName())) {
        selected.add(logic);
        found.add(logic.getName());
      }
    }
    for (String name : names) {
      if (!found.contains(name)) {
        result.addWarning("Configured logic was not supplied: " + name);
      }
    }
    return selected;
  }

  /**
   * Selects logic sequences to activate at the trigger time.
   *
   * @param enabledLogic enabled logic sequences
   * @param plan test plan
   * @param result result receiving warnings
   * @return trigger logic list
   */
  private static List<ProcessLogic> selectTriggerLogic(List<ProcessLogic> enabledLogic,
      EmergencyShutdownTestPlan plan, EmergencyShutdownTestResult result) {
    if (plan.getTriggerLogicNames().isEmpty()) {
      return new ArrayList<ProcessLogic>(enabledLogic);
    }
    return selectLogic(enabledLogic, plan.getTriggerLogicNames(), result);
  }

  /**
   * Activates trigger logic sequences.
   *
   * @param triggerLogic logic sequences to activate
   * @param result result receiving warnings
   */
  private static void activateLogic(List<ProcessLogic> triggerLogic,
      EmergencyShutdownTestResult result) {
    if (triggerLogic.isEmpty()) {
      result.addWarning("No process logic was activated at ESD trigger time.");
      return;
    }
    for (ProcessLogic logic : triggerLogic) {
      try {
        logic.activate();
      } catch (RuntimeException ex) {
        result.addError("Logic activation failed for " + logic.getName() + ": " + ex.getMessage());
      }
    }
  }

  /**
   * Executes active logic sequences for one time step.
   *
   * @param enabledLogic enabled logic sequences
   * @param timeStepSeconds time step in seconds
   * @param result result receiving errors
   */
  private static void executeLogic(List<ProcessLogic> enabledLogic, double timeStepSeconds,
      EmergencyShutdownTestResult result) {
    for (ProcessLogic logic : enabledLogic) {
      if (!logic.isActive()) {
        continue;
      }
      try {
        logic.execute(timeStepSeconds);
      } catch (RuntimeException ex) {
        result.addError("Logic execution failed for " + logic.getName() + ": " + ex.getMessage());
      }
    }
  }

  /**
   * Captures one monitored sample.
   *
   * @param timeSeconds elapsed time in seconds
   * @param process process system
   * @param plan test plan
   * @param result result receiving warnings
   * @return signal sample
   */
  private static EmergencyShutdownTestResult.SignalSample captureSample(double timeSeconds,
      ProcessSystem process, EmergencyShutdownTestPlan plan, EmergencyShutdownTestResult result) {
    Map<String, Double> values = new LinkedHashMap<String, Double>();
    for (String tag : plan.getMonitoredLogicalTags()) {
      Double value =
          readMonitorValue(process, plan.getTagMap(), tag, plan.getMonitoredUnits(), result);
      if (value != null) {
        values.put(tag, value);
      }
    }
    return new EmergencyShutdownTestResult.SignalSample(timeSeconds, values);
  }

  /**
   * Reads a monitored value from a tag binding or direct automation address.
   *
   * @param process process system
   * @param tagMap operational tag map
   * @param tag logical tag or automation address
   * @param units units keyed by tag
   * @param result result receiving warnings
   * @return monitored value, or null when unavailable
   */
  private static Double readMonitorValue(ProcessSystem process, OperationalTagMap tagMap,
      String tag, Map<String, String> units, EmergencyShutdownTestResult result) {
    OperationalTagBinding binding = tagMap.getBinding(tag);
    String address =
        binding != null && binding.hasAutomationAddress() ? binding.getAutomationAddress() : tag;
    String unit =
        units.containsKey(tag) ? units.get(tag) : binding == null ? "" : binding.getUnit();
    try {
      return Double.valueOf(process.getAutomation().getVariableValue(address, unit));
    } catch (RuntimeException ex) {
      if (binding != null && binding.hasHistorianTag()) {
        try {
          Map<String, Double> values = tagMap.readValues(process);
          if (values.containsKey(tag)) {
            return values.get(tag);
          }
        } catch (RuntimeException ignored) {
          // Report the primary automation error below.
        }
      }
      result.addWarning("Could not read monitored tag " + tag + ": " + ex.getMessage());
      return null;
    }
  }

  /**
   * Records final logic states.
   *
   * @param logicSequences logic sequences
   * @param result result to update
   */
  private static void recordLogicStates(List<ProcessLogic> logicSequences,
      EmergencyShutdownTestResult result) {
    for (ProcessLogic logic : logicSequences) {
      result.putLogicState(logic.getName(), logic.getState().name());
    }
  }

  /**
   * Builds model-to-field comparisons for field-data backed tags.
   *
   * @param plan test plan
   * @param result result to update
   */
  private static void buildFieldComparisons(EmergencyShutdownTestPlan plan,
      EmergencyShutdownTestResult result) {
    for (OperationalTagBinding binding : plan.getTagMap().getBindings()) {
      Double fieldValue = findFieldValue(binding, plan.getFieldData());
      EmergencyShutdownTestResult.SignalStats stats =
          result.getSignalStats().get(binding.getLogicalTag());
      Double modelValue =
          stats == null || !stats.hasSamples() ? null : Double.valueOf(stats.getFinalValue());
      if (fieldValue != null || modelValue != null) {
        result.addFieldComparison(new EmergencyShutdownTestResult.FieldComparison(
            binding.getLogicalTag(), binding.getHistorianTag(), binding.getUnit(), fieldValue,
            modelValue, plan.getDefaultFieldComparisonToleranceFraction()));
      }
    }
  }

  /**
   * Finds field data using logical tag first and historian tag second.
   *
   * @param binding tag binding
   * @param fieldData field-data map
   * @return field value, or null
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
   * Evaluates all acceptance criteria.
   *
   * @param plan test plan
   * @param result result to update
   */
  private static void evaluateCriteria(EmergencyShutdownTestPlan plan,
      EmergencyShutdownTestResult result) {
    for (EmergencyShutdownTestCriterion criterion : plan.getCriteria()) {
      result.addCriterionResult(criterion.evaluate(result.getSignalStats(), result.getLogicStates(),
          result.getErrors(), result.getFieldComparisons()));
    }
  }
}
