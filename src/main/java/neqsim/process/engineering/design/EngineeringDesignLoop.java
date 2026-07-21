package neqsim.process.engineering.design;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    EngineeringDesignDependencyGraph dependencyGraph = EngineeringDesignDependencyGraph.of(configuredModules);
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
      int appliedUpdates = 0;
      double maximumChange = 0.0;
      Map<String, EngineeringDesignVariable> designVariablesByKey = new LinkedHashMap<String, EngineeringDesignVariable>();
      Map<String, List<OwnedUpdate>> proposalsByKey = new LinkedHashMap<String, List<OwnedUpdate>>();
      for (List<EngineeringDesignModule> level : dependencyGraph.getOrderedLevels()) {
        Set<String> levelUpdateKeys = new LinkedHashSet<String>();
        for (EngineeringDesignModule module : level) {
          EngineeringDesignModuleResult moduleResult = module.evaluate(working, caseReport, state, context);
          if (moduleResult == null || !module.getId().trim().equals(moduleResult.getModuleId())) {
            throw new EngineeringDesignDependencyException("Engineering design module " + module.getId()
                + " returned a result for " + (moduleResult == null ? "null" : moduleResult.getModuleId()));
          }
          moduleResults.add(moduleResult);
          for (EngineeringDesignUpdate update : moduleResult.getUpdates()) {
            List<OwnedUpdate> proposals = proposalsByKey.get(update.getKey());
            if (proposals == null) {
              proposals = new ArrayList<OwnedUpdate>();
              proposalsByKey.put(update.getKey(), proposals);
            }
            proposals.add(new OwnedUpdate(moduleResult.getModuleId(), update));
            levelUpdateKeys.add(update.getKey());
          }
        }
        for (String key : levelUpdateKeys) {
          OwnedUpdate selectedUpdate = resolveUpdate(key, proposalsByKey.get(key));
          EngineeringDesignUpdate update = selectedUpdate.update;
          double selected = update.selectedValue();
          EngineeringDesignValue previous = state.get(update.getKey());
          double change = relativeChange(previous, selected);
          maximumChange = Math.max(maximumChange, change);
          boolean applied = previous == null || change > update.getRelativeTolerance();
          designVariablesByKey.put(key, new EngineeringDesignVariable(update, previous, selected, change, applied));
          if (applied) {
            update.apply(working, selected);
            state.put(new EngineeringDesignValue(update.getKey(), selected, update.getUnit(), selectedUpdate.moduleId,
                update.getGoverningCaseId(), number));
            appliedUpdates++;
          }
        }
      }
      List<EngineeringDesignVariable> designVariables = new ArrayList<EngineeringDesignVariable>(
          designVariablesByKey.values());

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

  private static OwnedUpdate resolveUpdate(String key, List<OwnedUpdate> proposals) {
    if (proposals.size() == 1) {
      return proposals.get(0);
    }
    EngineeringDesignUpdate.ConflictResolution resolution = proposals.get(0).update.getConflictResolution();
    String unit = proposals.get(0).update.getUnit();
    for (OwnedUpdate proposal : proposals) {
      if (!unit.equals(proposal.update.getUnit())) {
        throw conflict(key, proposals, "incompatible units");
      }
      if (resolution != proposal.update.getConflictResolution()) {
        throw conflict(key, proposals, "incompatible conflict-resolution rules");
      }
    }
    if (resolution == EngineeringDesignUpdate.ConflictResolution.REQUIRE_UNIQUE) {
      throw conflict(key, proposals, "no governing rule was declared");
    }

    OwnedUpdate selected = proposals.get(0);
    double selectedValue = selected.update.selectedValue();
    for (int index = 1; index < proposals.size(); index++) {
      OwnedUpdate candidate = proposals.get(index);
      double candidateValue = candidate.update.selectedValue();
      boolean governing = resolution == EngineeringDesignUpdate.ConflictResolution.GOVERNING_MAXIMUM
          ? candidateValue > selectedValue
          : candidateValue < selectedValue;
      if (governing || (candidateValue == selectedValue && candidate.moduleId.compareTo(selected.moduleId) < 0)) {
        selected = candidate;
        selectedValue = candidateValue;
      }
    }
    return selected;
  }

  private static EngineeringDesignConflictException conflict(String key, List<OwnedUpdate> proposals, String reason) {
    StringBuilder diagnostic = new StringBuilder("Conflicting engineering design updates for ").append(key).append(": ")
        .append(reason).append("; proposals=");
    for (int index = 0; index < proposals.size(); index++) {
      OwnedUpdate proposal = proposals.get(index);
      if (index > 0) {
        diagnostic.append(", ");
      }
      diagnostic.append(proposal.moduleId).append("=").append(proposal.update.selectedValue()).append(" ")
          .append(proposal.update.getUnit()).append(" [").append(proposal.update.getConflictResolution()).append("]");
    }
    return new EngineeringDesignConflictException(key, diagnostic.toString());
  }

  private static final class OwnedUpdate {
    private final String moduleId;
    private final EngineeringDesignUpdate update;

    private OwnedUpdate(String moduleId, EngineeringDesignUpdate update) {
      this.moduleId = moduleId;
      this.update = update;
    }
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
