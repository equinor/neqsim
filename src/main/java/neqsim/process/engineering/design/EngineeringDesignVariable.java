package neqsim.process.engineering.design;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Traceable design-variable change evaluated during one engineering iteration. */
public final class EngineeringDesignVariable implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String key;
  private final String unit;
  private final Double previousValue;
  private final double requiredValue;
  private final double selectedValue;
  private final String selectedCandidateId;
  private final String governingCaseId;
  private final double relativeChange;
  private final boolean applied;

  EngineeringDesignVariable(EngineeringDesignUpdate update, EngineeringDesignValue previous, double selected,
      double relativeChange, boolean applied) {
    key = update.getKey();
    unit = update.getUnit();
    previousValue = previous == null ? null : Double.valueOf(previous.getValue());
    requiredValue = update.getRequiredValue();
    selectedValue = selected;
    selectedCandidateId = update.selectedCandidateId();
    governingCaseId = update.getGoverningCaseId();
    this.relativeChange = relativeChange;
    this.applied = applied;
  }

  public String getKey() {
    return key;
  }

  public String getUnit() {
    return unit;
  }

  public Double getPreviousValue() {
    return previousValue;
  }

  public double getRequiredValue() {
    return requiredValue;
  }

  public double getSelectedValue() {
    return selectedValue;
  }

  public String getSelectedCandidateId() {
    return selectedCandidateId;
  }

  public String getGoverningCaseId() {
    return governingCaseId;
  }

  public double getRelativeChange() {
    return relativeChange;
  }

  public boolean isApplied() {
    return applied;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("key", key);
    result.put("unit", unit);
    result.put("previousValue", previousValue);
    result.put("requiredValue", Double.valueOf(requiredValue));
    result.put("selectedValue", Double.valueOf(selectedValue));
    result.put("selectedCandidateId", selectedCandidateId);
    result.put("governingCaseId", governingCaseId);
    result.put("relativeChange", Double.valueOf(relativeChange));
    result.put("applied", Boolean.valueOf(applied));
    return result;
  }
}
