package neqsim.process.engineering.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseRunner;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.processmodel.ProcessSystem;

/** Iterates process cases and discipline modules until process values and physical design variables stabilize. */
public final class EngineeringDesignLoop {
  private EngineeringDesignLoop() {
  }

  public static EngineeringDesignLoopResult run(ProcessSystem baseProcess, EngineeringCaseSet cases,
      List<EngineeringDesignModule> configuredModules, EngineeringDesignLoopOptions configuredOptions) {
    if (baseProcess == null || cases == null) {
      throw new IllegalArgumentException("baseProcess and cases must not be null");
    }
    EngineeringDesignLoopOptions options = configuredOptions == null ? EngineeringDesignLoopOptions.builder().build()
        : configuredOptions;
    List<EngineeringDesignModule> modules = orderedModules(configuredModules);
    ProcessSystem working = baseProcess.copy();
    EngineeringDesignState state = new EngineeringDesignState();
    List<EngineeringDesignIteration> iterations = new ArrayList<EngineeringDesignIteration>();
    List<Map<String, Double>> designStateHistory = new ArrayList<Map<String, Double>>();
    Map<String, Double> previousProcessValues = null;

    for (int number = 1; number <= options.getMaximumIterations(); number++) {
      EngineeringCaseRunReport caseReport = EngineeringCaseRunner.run(working, cases, EngineeringCaseRunOptions
          .builder().parallelism(options.getCaseParallelism()).requireConvergence(true).build());
      EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId(cases.getId())
          .simulationFingerprint(caseReport.getResultFingerprint())
          .attribute("designIteration", Integer.toString(number)).build();
      List<EngineeringDesignModuleResult> moduleResults = new ArrayList<EngineeringDesignModuleResult>();
      for (EngineeringDesignModule module : modules) {
        moduleResults.add(module.evaluate(working, caseReport, state, context));
      }

      int appliedUpdates = 0;
      double maximumChange = 0.0;
      List<EngineeringDesignVariable> designVariables = new ArrayList<EngineeringDesignVariable>();
      Map<String, EngineeringDesignUpdate> selectedUpdates = new java.util.LinkedHashMap<String, EngineeringDesignUpdate>();
      Map<String, String> updateOwners = new java.util.LinkedHashMap<String, String>();
      for (EngineeringDesignModuleResult moduleResult : moduleResults) {
        for (EngineeringDesignUpdate update : moduleResult.getUpdates()) {
          selectedUpdates.put(update.getKey(), update);
          updateOwners.put(update.getKey(), moduleResult.getModuleId());
        }
      }
      for (EngineeringDesignUpdate update : selectedUpdates.values()) {
        double selected = update.selectedValue();
        EngineeringDesignValue previous = state.get(update.getKey());
        double change = relativeChange(previous, selected);
        maximumChange = Math.max(maximumChange, change);
        boolean applied = previous == null || change > update.getRelativeTolerance();
        designVariables.add(new EngineeringDesignVariable(update, previous, selected, change, applied));
        if (applied) {
          update.apply(working, selected);
          state.put(new EngineeringDesignValue(update.getKey(), selected, update.getUnit(),
              updateOwners.get(update.getKey()), update.getGoverningCaseId(), number));
          appliedUpdates++;
        }
      }

      List<EngineeringConstraintResult> constraintResults = evaluateConstraints(moduleResults, state);
      Map<String, Double> processValues = flattenProcessValues(caseReport);
      double maximumProcessChange = relativeProcessChange(previousProcessValues, processValues);
      int satisfiedConstraints = countSatisfied(constraintResults);
      boolean processStable = !options.isStableProcessValuesRequired()
          || (previousProcessValues != null && maximumProcessChange <= options.getProcessValueRelativeTolerance());
      boolean caseSuccess = !caseReport.getEnvelope().hasCaseFailures();
      EngineeringConvergenceReport convergence = new EngineeringConvergenceReport(maximumChange, maximumProcessChange,
          options.getProcessValueRelativeTolerance(), satisfiedConstraints, constraintResults.size(),
          appliedUpdates == 0, processStable, caseSuccess);
      EngineeringDesignIteration iteration = new EngineeringDesignIteration(number, caseReport, moduleResults,
          constraintResults, appliedUpdates, maximumChange, designVariables, convergence);
      iterations.add(iteration);
      Map<String, Double> currentDesignState = designStateValues(state);
      if (options.isDiscreteOscillationDetectionEnabled() && appliedUpdates > 0 && designStateHistory.size() >= 2
          && sameState(currentDesignState, designStateHistory.get(designStateHistory.size() - 2))
          && !sameState(currentDesignState, designStateHistory.get(designStateHistory.size() - 1))) {
        return new EngineeringDesignLoopResult(false, "DISCRETE_DESIGN_OSCILLATION_DETECTED", state, iterations,
            working);
      }
      designStateHistory.add(currentDesignState);
      previousProcessValues = processValues;
      boolean constraintSuccess = !options.isAllConstraintsRequired() || iteration.areAllConstraintsSatisfied();
      if (appliedUpdates == 0 && processStable && caseSuccess && constraintSuccess) {
        return new EngineeringDesignLoopResult(true, "DESIGN_VARIABLES_PROCESS_VALUES_AND_CONSTRAINTS_CONVERGED", state,
            iterations, working);
      }
    }
    return new EngineeringDesignLoopResult(false, "MAXIMUM_ITERATIONS_REACHED", state, iterations, working);
  }

  private static List<EngineeringDesignModule> orderedModules(List<EngineeringDesignModule> configured) {
    List<EngineeringDesignModule> result = configured == null ? new ArrayList<EngineeringDesignModule>()
        : new ArrayList<EngineeringDesignModule>(configured);
    Collections.sort(result, new Comparator<EngineeringDesignModule>() {
      @Override
      public int compare(EngineeringDesignModule first, EngineeringDesignModule second) {
        return first.getId().compareTo(second.getId());
      }
    });
    return result;
  }

  private static List<EngineeringConstraintResult> evaluateConstraints(
      List<EngineeringDesignModuleResult> moduleResults, EngineeringDesignState state) {
    List<EngineeringConstraintResult> results = new ArrayList<EngineeringConstraintResult>();
    for (EngineeringDesignModuleResult module : moduleResults) {
      for (EngineeringDesignConstraint constraint : module.getConstraints()) {
        results.add(constraint.evaluate(state));
      }
    }
    return results;
  }

  private static double relativeChange(EngineeringDesignValue previous, double selected) {
    if (previous == null) {
      return Double.POSITIVE_INFINITY;
    }
    return Math.abs(selected - previous.getValue()) / Math.max(Math.abs(previous.getValue()), 1.0e-12);
  }

  private static Map<String, Double> flattenProcessValues(EngineeringCaseRunReport report) {
    Map<String, Double> result = new java.util.LinkedHashMap<String, Double>();
    for (neqsim.process.engineering.designcase.DesignCaseResult designCase : report.getEnvelope().getCaseResults()) {
      for (Map.Entry<String, Double> metric : designCase.getValues().entrySet()) {
        result.put(designCase.getDesignCase().getId() + "/" + metric.getKey(), metric.getValue());
      }
    }
    return result;
  }

  private static double relativeProcessChange(Map<String, Double> previous, Map<String, Double> current) {
    if (previous == null || !previous.keySet().equals(current.keySet())) {
      return Double.POSITIVE_INFINITY;
    }
    double maximum = 0.0;
    for (Map.Entry<String, Double> value : current.entrySet()) {
      double oldValue = previous.get(value.getKey()).doubleValue();
      maximum = Math.max(maximum,
          Math.abs(value.getValue().doubleValue() - oldValue) / Math.max(Math.abs(oldValue), 1.0e-12));
    }
    return maximum;
  }

  private static int countSatisfied(List<EngineeringConstraintResult> results) {
    int count = 0;
    for (EngineeringConstraintResult result : results) {
      if (result.isSatisfied()) {
        count++;
      }
    }
    return count;
  }

  private static Map<String, Double> designStateValues(EngineeringDesignState state) {
    Map<String, Double> result = new java.util.LinkedHashMap<String, Double>();
    for (Map.Entry<String, EngineeringDesignValue> entry : state.getValues().entrySet()) {
      result.put(entry.getKey(), Double.valueOf(entry.getValue().getValue()));
    }
    return result;
  }

  private static boolean sameState(Map<String, Double> first, Map<String, Double> second) {
    if (!first.keySet().equals(second.keySet())) {
      return false;
    }
    for (Map.Entry<String, Double> entry : first.entrySet()) {
      if (Double.doubleToLongBits(entry.getValue().doubleValue()) != Double
          .doubleToLongBits(second.get(entry.getKey()).doubleValue())) {
        return false;
      }
    }
    return true;
  }
}
