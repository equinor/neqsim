package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Convergence evidence covering both physical design variables and simulated process values. */
public final class EngineeringConvergenceReport implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final double maximumDesignVariableRelativeChange;
  private final double maximumProcessValueRelativeChange;
  private final double processValueTolerance;
  private final int satisfiedConstraintCount;
  private final int constraintCount;
  private final boolean designVariablesStable;
  private final boolean processValuesStable;
  private final boolean caseRunsSuccessful;

  EngineeringConvergenceReport(double maximumDesignVariableRelativeChange, double maximumProcessValueRelativeChange,
      double processValueTolerance, int satisfiedConstraintCount, int constraintCount, boolean designVariablesStable,
      boolean processValuesStable, boolean caseRunsSuccessful) {
    this.maximumDesignVariableRelativeChange = maximumDesignVariableRelativeChange;
    this.maximumProcessValueRelativeChange = maximumProcessValueRelativeChange;
    this.processValueTolerance = processValueTolerance;
    this.satisfiedConstraintCount = satisfiedConstraintCount;
    this.constraintCount = constraintCount;
    this.designVariablesStable = designVariablesStable;
    this.processValuesStable = processValuesStable;
    this.caseRunsSuccessful = caseRunsSuccessful;
  }

  public double getMaximumProcessValueRelativeChange() {
    return maximumProcessValueRelativeChange;
  }

  public double getMaximumDesignVariableRelativeChange() {
    return maximumDesignVariableRelativeChange;
  }

  public double getProcessValueTolerance() {
    return processValueTolerance;
  }

  public int getSatisfiedConstraintCount() {
    return satisfiedConstraintCount;
  }

  public int getConstraintCount() {
    return constraintCount;
  }

  public boolean areDesignVariablesStable() {
    return designVariablesStable;
  }

  public boolean areProcessValuesStable() {
    return processValuesStable;
  }

  public boolean areCaseRunsSuccessful() {
    return caseRunsSuccessful;
  }

  public boolean isConverged() {
    return designVariablesStable && processValuesStable && caseRunsSuccessful
        && satisfiedConstraintCount == constraintCount;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("maximumDesignVariableRelativeChange", Double.valueOf(maximumDesignVariableRelativeChange));
    result.put("maximumProcessValueRelativeChange", Double.valueOf(maximumProcessValueRelativeChange));
    result.put("processValueTolerance", Double.valueOf(processValueTolerance));
    result.put("satisfiedConstraintCount", Integer.valueOf(satisfiedConstraintCount));
    result.put("constraintCount", Integer.valueOf(constraintCount));
    result.put("designVariablesStable", Boolean.valueOf(designVariablesStable));
    result.put("processValuesStable", Boolean.valueOf(processValuesStable));
    result.put("caseRunsSuccessful", Boolean.valueOf(caseRunsSuccessful));
    result.put("converged", Boolean.valueOf(isConverged()));
    return result;
  }
}
