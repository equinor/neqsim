package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;

/** Immutable evidence for one process/design iteration. */
public final class EngineeringDesignIteration implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final int number;
  private final EngineeringCaseRunReport caseReport;
  private final List<EngineeringDesignModuleResult> moduleResults;
  private final List<EngineeringConstraintResult> constraintResults;
  private final int appliedUpdateCount;
  private final double maximumRelativeChange;

  EngineeringDesignIteration(int number, EngineeringCaseRunReport caseReport,
      List<EngineeringDesignModuleResult> moduleResults, List<EngineeringConstraintResult> constraintResults,
      int appliedUpdateCount, double maximumRelativeChange) {
    this.number = number;
    this.caseReport = caseReport;
    this.moduleResults = Collections.unmodifiableList(new ArrayList<EngineeringDesignModuleResult>(moduleResults));
    this.constraintResults = Collections
        .unmodifiableList(new ArrayList<EngineeringConstraintResult>(constraintResults));
    this.appliedUpdateCount = appliedUpdateCount;
    this.maximumRelativeChange = maximumRelativeChange;
  }

  public int getNumber() {
    return number;
  }

  public EngineeringCaseRunReport getCaseReport() {
    return caseReport;
  }

  public int getAppliedUpdateCount() {
    return appliedUpdateCount;
  }

  public double getMaximumRelativeChange() {
    return maximumRelativeChange;
  }

  public boolean areAllConstraintsSatisfied() {
    for (EngineeringConstraintResult result : constraintResults) {
      if (!result.isSatisfied()) {
        return false;
      }
    }
    return true;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("iteration", Integer.valueOf(number));
    result.put("caseRun", caseReport.toMap());
    List<Map<String, Object>> modules = new ArrayList<Map<String, Object>>();
    for (EngineeringDesignModuleResult module : moduleResults) {
      modules.add(module.toMap());
    }
    result.put("modules", modules);
    List<Map<String, Object>> constraints = new ArrayList<Map<String, Object>>();
    for (EngineeringConstraintResult constraint : constraintResults) {
      constraints.add(constraint.toMap());
    }
    result.put("constraints", constraints);
    result.put("allConstraintsSatisfied", Boolean.valueOf(areAllConstraintsSatisfied()));
    result.put("appliedUpdateCount", Integer.valueOf(appliedUpdateCount));
    result.put("maximumRelativeChange", Double.valueOf(maximumRelativeChange));
    return result;
  }
}
