package neqsim.process.engineering.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.designcase.EngineeringCaseRunOptions;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.engineering.designcase.EngineeringCaseRunner;
import neqsim.process.engineering.designcase.EngineeringCaseSet;
import neqsim.process.processmodel.ProcessSystem;

/** Iterates process cases and discipline modules until physical design variables stabilize. */
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
      for (EngineeringDesignModuleResult moduleResult : moduleResults) {
        for (EngineeringDesignUpdate update : moduleResult.getUpdates()) {
          double selected = update.selectedValue();
          EngineeringDesignValue previous = state.get(update.getKey());
          double change = relativeChange(previous, selected);
          maximumChange = Math.max(maximumChange, change);
          if (previous == null || change > update.getRelativeTolerance()) {
            update.apply(working, selected);
            state.put(new EngineeringDesignValue(update.getKey(), selected, update.getUnit(),
                moduleResult.getModuleId(), update.getGoverningCaseId(), number));
            appliedUpdates++;
          }
        }
      }

      List<EngineeringConstraintResult> constraintResults = evaluateConstraints(moduleResults, state);
      EngineeringDesignIteration iteration = new EngineeringDesignIteration(number, caseReport, moduleResults,
          constraintResults, appliedUpdates, maximumChange);
      iterations.add(iteration);
      boolean caseSuccess = !caseReport.getEnvelope().hasCaseFailures();
      boolean constraintSuccess = !options.isAllConstraintsRequired() || iteration.areAllConstraintsSatisfied();
      if (appliedUpdates == 0 && caseSuccess && constraintSuccess) {
        return new EngineeringDesignLoopResult(true, "DESIGN_VARIABLES_AND_CONSTRAINTS_CONVERGED", state, iterations,
            working);
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
}
