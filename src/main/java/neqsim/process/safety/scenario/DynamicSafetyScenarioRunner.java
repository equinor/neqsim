package neqsim.process.safety.scenario;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    double time = 0.0;
    boolean triggered = false;
    UUID simulationId = UUID.nameUUIDFromBytes(scenario.getId().getBytes(StandardCharsets.UTF_8));
    while (errors.isEmpty() && time <= scenario.getDurationSeconds() + 1.0e-9) {
      if (!triggered && time + 1.0e-9 >= scenario.getTriggerTimeSeconds()) {
        scenario.getInitiatingEvent().apply(process);
        for (ProcessLogic item : logic) {
          item.activate();
        }
        triggered = true;
      }
      if (triggered) {
        for (ProcessLogic item : logic) {
          if (item.isActive()) {
            item.execute(scenario.getTimeStepSeconds());
          }
        }
        sample(criteria, process, time - scenario.getTriggerTimeSeconds(), errors);
      }
      if (time + scenario.getTimeStepSeconds() > scenario.getDurationSeconds() + 1.0e-9) {
        break;
      }
      try {
        process.runTransient(scenario.getTimeStepSeconds(), simulationId);
      } catch (RuntimeException ex) {
        errors.add("Transient simulation failed at " + time + " s: " + failureMessage(ex));
      }
      time += scenario.getTimeStepSeconds();
    }

    Map<String, String> finalStates = new LinkedHashMap<String, String>();
    Map<String, Map<String, Object>> logicEvidence = new LinkedHashMap<String, Map<String, Object>>();
    for (ProcessLogic item : logic) {
      finalStates.put(item.getName(), item.getState().name());
      if (item instanceof DynamicSafetyScenarioEvidenceProvider) {
        logicEvidence.put(item.getName(),
            ((DynamicSafetyScenarioEvidenceProvider) item).getDynamicSafetyScenarioEvidence());
      }
    }
    DynamicSafetyScenarioResult result = new DynamicSafetyScenarioResult(scenario.getId(), scenario.getName(), criteria,
        finalStates, logicEvidence, errors, converged);
    logger.info("Dynamic safety scenario '{}' completed with verdict {}", scenario.getId(),
        result.isPassed() ? "PASS" : "FAIL");
    return result;
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
