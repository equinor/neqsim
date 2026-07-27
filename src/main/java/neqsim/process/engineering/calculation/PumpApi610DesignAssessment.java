package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.AssessmentStatus;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.CheckStatus;

/** Immutable engineering-workflow snapshot of an API 610 screening calculation. */
public final class PumpApi610DesignAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable snapshot of one legacy calculator check. */
  public static final class CheckResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String requirement;
    private final CheckStatus status;
    private final double actualValue;
    private final double limitValue;
    private final String unit;
    private final String message;

    private CheckResult(PumpApi610DesignCalculator.Check check) {
      id = check.getId();
      requirement = check.getRequirement();
      status = check.getStatus();
      actualValue = check.getActualValue();
      limitValue = check.getLimitValue();
      unit = check.getUnit();
      message = check.getMessage();
    }

    /** @return stable check identifier */
    public String getId() {
      return id;
    }

    /** @return requirement description */
    public String getRequirement() {
      return requirement;
    }

    /** @return check status */
    public CheckStatus getStatus() {
      return status;
    }

    /** @return calculated or supplied value */
    public double getActualValue() {
      return actualValue;
    }

    /** @return configured limiting value */
    public double getLimitValue() {
      return limitValue;
    }

    /** @return engineering unit */
    public String getUnit() {
      return unit;
    }

    /** @return check explanation */
    public String getMessage() {
      return message;
    }

    /** @return serializable check representation */
    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("id", id);
      result.put("requirement", requirement);
      result.put("status", status.name());
      result.put("actualValue", Double.valueOf(actualValue));
      result.put("limitValue", Double.valueOf(limitValue));
      result.put("unit", unit);
      result.put("message", message);
      return result;
    }
  }

  private final String standardEdition;
  private final AssessmentStatus assessmentStatus;
  private final String operatingRegion;
  private final double operatingFlowRatio;
  private final double npshMarginM;
  private final double npshMarginRatio;
  private final double governingShutoffHeadM;
  private final double requiredCasingPressureBara;
  private final double preliminaryHydrostaticTestPressureBara;
  private final double headRiseToShutoffFraction;
  private final double requiredDriverPowerKw;
  private final double selectedDriverPowerKw;
  private final double bearingL10LifeHours;
  private final double criticalSpeedRatio;
  private final List<CheckResult> checks;

  private PumpApi610DesignAssessment(PumpApi610DesignCalculator calculator) {
    standardEdition = calculator.getStandardEdition();
    assessmentStatus = calculator.getAssessmentStatus();
    operatingRegion = calculator.getOperatingRegion();
    operatingFlowRatio = calculator.getOperatingFlowRatio();
    npshMarginM = calculator.getNpshMarginM();
    npshMarginRatio = calculator.getNpshMarginRatio();
    governingShutoffHeadM = calculator.getGoverningShutoffHeadM();
    requiredCasingPressureBara = calculator.getRequiredCasingPressureBara();
    preliminaryHydrostaticTestPressureBara = calculator.getPreliminaryHydrostaticTestPressureBara();
    headRiseToShutoffFraction = calculator.getHeadRiseToShutoffFraction();
    requiredDriverPowerKw = calculator.getRequiredDriverPowerKw();
    selectedDriverPowerKw = calculator.getSelectedDriverPowerKw();
    bearingL10LifeHours = calculator.getBearingL10LifeHours();
    criticalSpeedRatio = calculator.getCriticalSpeedRatio();
    List<CheckResult> checkResults = new ArrayList<CheckResult>();
    for (PumpApi610DesignCalculator.Check check : calculator.getChecks()) {
      checkResults.add(new CheckResult(check));
    }
    checks = Collections.unmodifiableList(checkResults);
  }

  static PumpApi610DesignAssessment from(PumpApi610DesignCalculator calculator) {
    return new PumpApi610DesignAssessment(calculator);
  }

  /** @return explicit edition evaluated by the legacy calculator */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return overall API 610 screening status */
  public AssessmentStatus getAssessmentStatus() {
    return assessmentStatus;
  }

  /** @return operating-region label */
  public String getOperatingRegion() {
    return operatingRegion;
  }

  /** @return operating flow divided by BEP flow */
  public double getOperatingFlowRatio() {
    return operatingFlowRatio;
  }

  /** @return NPSH head margin in metres */
  public double getNpshMarginM() {
    return npshMarginM;
  }

  /** @return NPSHa divided by NPSHr */
  public double getNpshMarginRatio() {
    return npshMarginRatio;
  }

  /** @return governing shutoff head in metres */
  public double getGoverningShutoffHeadM() {
    return governingShutoffHeadM;
  }

  /** @return required casing pressure in bara */
  public double getRequiredCasingPressureBara() {
    return requiredCasingPressureBara;
  }

  /** @return preliminary hydrostatic test pressure in bara */
  public double getPreliminaryHydrostaticTestPressureBara() {
    return preliminaryHydrostaticTestPressureBara;
  }

  /** @return fractional head rise from rated point to shutoff */
  public double getHeadRiseToShutoffFraction() {
    return headRiseToShutoffFraction;
  }

  /** @return required driver power in kW */
  public double getRequiredDriverPowerKw() {
    return requiredDriverPowerKw;
  }

  /** @return selected driver rating in kW */
  public double getSelectedDriverPowerKw() {
    return selectedDriverPowerKw;
  }

  /** @return calculated L10 bearing life in hours */
  public double getBearingL10LifeHours() {
    return bearingL10LifeHours;
  }

  /** @return first-critical-speed separation ratio */
  public double getCriticalSpeedRatio() {
    return criticalSpeedRatio;
  }

  /** @return immutable check snapshots */
  public List<CheckResult> getChecks() {
    return checks;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("assessmentStatus", assessmentStatus.name());
    result.put("operatingRegion", operatingRegion);
    result.put("operatingFlowRatio", Double.valueOf(operatingFlowRatio));
    result.put("npshMarginM", Double.valueOf(npshMarginM));
    result.put("npshMarginRatio", Double.valueOf(npshMarginRatio));
    result.put("governingShutoffHeadM", Double.valueOf(governingShutoffHeadM));
    result.put("requiredCasingPressureBara", Double.valueOf(requiredCasingPressureBara));
    result.put("preliminaryHydrostaticTestPressureBara", Double.valueOf(preliminaryHydrostaticTestPressureBara));
    result.put("headRiseToShutoffFraction", Double.valueOf(headRiseToShutoffFraction));
    result.put("requiredDriverPowerKw", Double.valueOf(requiredDriverPowerKw));
    result.put("selectedDriverPowerKw", Double.valueOf(selectedDriverPowerKw));
    result.put("bearingL10LifeHours", Double.valueOf(bearingL10LifeHours));
    result.put("criticalSpeedRatio", Double.valueOf(criticalSpeedRatio));
    List<Map<String, Object>> checkMaps = new ArrayList<Map<String, Object>>();
    for (CheckResult check : checks) {
      checkMaps.add(check.toMap());
    }
    result.put("checks", checkMaps);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
