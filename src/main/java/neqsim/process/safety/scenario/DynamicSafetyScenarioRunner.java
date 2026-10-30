package neqsim.process.safety.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessSystem;

/** Executes initiating events and protection logic against an isolated transient process copy. */
public final class DynamicSafetyScenarioRunner {
  private static final Logger logger = LogManager.getLogger(DynamicSafetyScenarioRunner.class);

  private DynamicSafetyScenarioRunner() {
  }

  public static DynamicSafetyScenarioResult run(ProcessSystem baseProcess, DynamicSafetyScenario scenario) {
    if (baseProcess == null || scenario == null) {
      throw new IllegalArgumentException("baseProcess and scenario are required");
    }
    ProcessSystem process = baseProcess.copy();
    List<String> errors = new ArrayList<String>();
    if (scenario.getCaseConfiguration() != null) {
      scenario.getCaseConfiguration().apply(process);
    }
    boolean converged = false;
    try {
      process.run();
      converged = process.solved();
    } catch (RuntimeException ex) {
      errors.add("Steady-state initialization failed: " + failureMessage(ex));
    }

    List<ProcessLogic> logic = new ArrayList<ProcessLogic>();
    for (DynamicSafetyScenario.LogicFactory factory : scenario.getLogicFactories()) {
      logic.add(factory.create(process));
    }
    Map<String, DynamicSafetyScenarioResult.CriterionResult> criteria = new LinkedHashMap<String, DynamicSafetyScenarioResult.CriterionResult>();
    for (DynamicScenarioCriterion criterion : scenario.getCriteria()) {
      criteria.put(criterion.getId(), new DynamicSafetyScenarioResult.CriterionResult(criterion));
    }

    DynamicSafetyScenarioResult result = scenario.isEventExactTimingEnabled()
        ? runEventExact(process, scenario, logic, criteria, errors, converged)
        : runLegacy(process, scenario, logic, criteria, errors, converged);
    logger.info("Dynamic safety scenario '{}' completed with verdict {}", scenario.getId(),
        result.isPassed() ? "PASS" : "FAIL");
    return result;
  }

  private static DynamicSafetyScenarioResult runLegacy(ProcessSystem process, DynamicSafetyScenario scenario,
      List<ProcessLogic> logic, Map<String, DynamicSafetyScenarioResult.CriterionResult> criteria, List<String> errors,
      boolean converged) {
    double time = 0.0;
    boolean triggered = false;
    long physicalStepIndex = 0L;
    List<Double> physicalStepSeconds = new ArrayList<Double>();
    while (errors.isEmpty() && time <= scenario.getDurationSeconds() + 1.0e-9) {
      if (!triggered && time + 1.0e-9 >= scenario.getTriggerTimeSeconds()) {
        scenario.getInitiatingEvent().apply(process);
        activate(logic);
        triggered = true;
      }
      if (triggered) {
        executeActive(logic, scenario.getTimeStepSeconds());
        sample(criteria, process, time - scenario.getTriggerTimeSeconds(), errors);
      }
      if (time + scenario.getTimeStepSeconds() > scenario.getDurationSeconds() + 1.0e-9) {
        break;
      }
      try {
        UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep(scenario.getId(), physicalStepIndex);
        process.runTransient(scenario.getTimeStepSeconds(), physicalStepId);
        physicalStepSeconds.add(Double.valueOf(scenario.getTimeStepSeconds()));
      } catch (RuntimeException ex) {
        errors.add("Transient simulation failed at " + time + " s: " + failureMessage(ex));
      }
      physicalStepIndex++;
      time += scenario.getTimeStepSeconds();
    }
    return finish(scenario, criteria, logic, errors, converged, "LEGACY_FIXED_SCAN", sum(physicalStepSeconds),
        physicalStepSeconds);
  }

  private static DynamicSafetyScenarioResult runEventExact(ProcessSystem process, DynamicSafetyScenario scenario,
      List<ProcessLogic> logic, Map<String, DynamicSafetyScenarioResult.CriterionResult> criteria, List<String> errors,
      boolean converged) {
    double time = 0.0;
    boolean triggered = false;
    long physicalStepIndex = 0L;
    List<Double> physicalStepSeconds = new ArrayList<Double>();
    if (scenario.getTriggerTimeSeconds() <= 1.0e-12) {
      triggered = trigger(process, scenario, logic, criteria, errors);
    }

    while (errors.isEmpty() && time < scenario.getDurationSeconds() - 1.0e-12) {
      if (!triggered && time + 1.0e-12 >= scenario.getTriggerTimeSeconds()) {
        triggered = trigger(process, scenario, logic, criteria, errors);
        continue;
      }
      double nextBoundary = scenario.getDurationSeconds();
      if (!triggered) {
        nextBoundary = Math.min(nextBoundary, scenario.getTriggerTimeSeconds());
      }
      double stepSeconds = Math.min(scenario.getTimeStepSeconds(), nextBoundary - time);
      if (stepSeconds <= 1.0e-12) {
        break;
      }
      try {
        UUID physicalStepId = TransientStepIdentifier.deterministicPhysicalStep(scenario.getId(), physicalStepIndex);
        process.runTransient(stepSeconds, physicalStepId);
        physicalStepSeconds.add(Double.valueOf(stepSeconds));
      } catch (RuntimeException ex) {
        errors.add("Transient simulation failed at " + time + " s: " + failureMessage(ex));
        break;
      }
      physicalStepIndex++;
      time += stepSeconds;

      if (!triggered && time + 1.0e-12 >= scenario.getTriggerTimeSeconds()) {
        triggered = trigger(process, scenario, logic, criteria, errors);
      } else if (triggered) {
        executeActive(logic, stepSeconds);
        sample(criteria, process, time - scenario.getTriggerTimeSeconds(), errors);
      }
    }
    if (!triggered && errors.isEmpty() && time + 1.0e-12 >= scenario.getTriggerTimeSeconds()) {
      trigger(process, scenario, logic, criteria, errors);
    }
    return finish(scenario, criteria, logic, errors, converged, "EVENT_EXACT", time, physicalStepSeconds);
  }

  private static boolean trigger(ProcessSystem process, DynamicSafetyScenario scenario, List<ProcessLogic> logic,
      Map<String, DynamicSafetyScenarioResult.CriterionResult> criteria, List<String> errors) {
    try {
      scenario.getInitiatingEvent().apply(process);
      activate(logic);
      sample(criteria, process, 0.0, errors);
      return errors.isEmpty();
    } catch (RuntimeException ex) {
      errors.add("Initiating event failed: " + failureMessage(ex));
      return false;
    }
  }

  private static void activate(List<ProcessLogic> logic) {
    for (ProcessLogic item : logic) {
      item.activate();
    }
  }

  private static void executeActive(List<ProcessLogic> logic, double stepSeconds) {
    for (ProcessLogic item : logic) {
      if (item.isActive()) {
        item.execute(stepSeconds);
      }
    }
  }

  private static DynamicSafetyScenarioResult finish(DynamicSafetyScenario scenario,
      Map<String, DynamicSafetyScenarioResult.CriterionResult> criteria, List<ProcessLogic> logic, List<String> errors,
      boolean converged, String timingPolicy, double completedDurationSeconds, List<Double> physicalStepSeconds) {
    Map<String, String> finalStates = new LinkedHashMap<String, String>();
    Map<String, Map<String, Object>> logicEvidence = new LinkedHashMap<String, Map<String, Object>>();
    for (ProcessLogic item : logic) {
      finalStates.put(item.getName(), item.getState().name());
      if (item instanceof DynamicSafetyScenarioEvidenceProvider) {
        logicEvidence.put(item.getName(),
            ((DynamicSafetyScenarioEvidenceProvider) item).getDynamicSafetyScenarioEvidence());
      }
    }
    return new DynamicSafetyScenarioResult(scenario.getId(), scenario.getName(), criteria, finalStates, logicEvidence,
        errors, converged, timingPolicy, scenario.getTriggerTimeSeconds(), scenario.getDurationSeconds(),
        completedDurationSeconds, physicalStepSeconds);
  }

  private static double sum(List<Double> values) {
    double total = 0.0;
    for (Double value : values) {
      total += value.doubleValue();
    }
    return total;
  }

  private static void sample(Map<String, DynamicSafetyScenarioResult.CriterionResult> results, ProcessSystem process,
      double relativeTimeSeconds, List<String> errors) {
    for (Map.Entry<String, DynamicSafetyScenarioResult.CriterionResult> entry : results.entrySet()) {
      try {
        DynamicScenarioCriterion criterion = entry.getValue().getCriterion();
        entry.getValue().sample(relativeTimeSeconds, criterion.extract(process));
      } catch (RuntimeException ex) {
        errors.add("Criterion " + entry.getKey() + " failed: " + failureMessage(ex));
      }
    }
  }

  private static String failureMessage(RuntimeException ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}
