package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.overpressure.AcceptanceResult;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Immutable engineering-workflow snapshot of an API 521 relief-scenario screening study. */
public final class Api521ReliefAssessment implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Immutable summary of one documented relief scenario. */
  public static final class ScenarioSummary implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final String cause;
    private final String phase;
    private final double reliefRateKgPerS;
    private final boolean credible;
    private final boolean dynamicallyDetermined;
    private final String standardReference;
    private final List<String> assumptions;

    private ScenarioSummary(ReliefScenario scenario) {
      name = scenario.getName();
      cause = scenario.getCause().name();
      phase = scenario.getPhase().name();
      reliefRateKgPerS = scenario.getReliefRateKgPerS();
      credible = scenario.isCredible();
      dynamicallyDetermined = scenario.isDynamicallyDetermined();
      standardReference = scenario.getStandardReference();
      assumptions = Collections.unmodifiableList(new ArrayList<String>(scenario.getAssumptions()));
    }

    /** @return scenario name */
    public String getName() {
      return name;
    }

    /** @return overpressure cause identifier */
    public String getCause() {
      return cause;
    }

    /** @return relieving phase */
    public String getPhase() {
      return phase;
    }

    /** @return required relief rate in kg/s */
    public double getReliefRateKgPerS() {
      return reliefRateKgPerS;
    }

    /** @return whether the scenario is eligible for governing-case selection */
    public boolean isCredible() {
      return credible;
    }

    /** @return whether the relief rate was obtained dynamically */
    public boolean isDynamicallyDetermined() {
      return dynamicallyDetermined;
    }

    /** @return supplied standard or project reference */
    public String getStandardReference() {
      return standardReference;
    }

    /** @return immutable scenario assumptions */
    public List<String> getAssumptions() {
      return assumptions;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("name", name);
      result.put("cause", cause);
      result.put("phase", phase);
      result.put("reliefRateKgPerS", Double.valueOf(reliefRateKgPerS));
      result.put("credible", Boolean.valueOf(credible));
      result.put("dynamicallyDetermined", Boolean.valueOf(dynamicallyDetermined));
      result.put("standardReference", standardReference);
      result.put("assumptions", new ArrayList<String>(assumptions));
      return result;
    }
  }

  private final String standardEdition;
  private final String protectedItemName;
  private final List<ScenarioSummary> scenarios;
  private final String governingScenarioName;
  private final String governingCause;
  private final String governingPhase;
  private final double governingReliefRateKgPerS;
  private final double requiredAreaM2;
  private final double requiredAreaIn2;
  private final String recommendedOrifice;
  private final double selectedAreaIn2;
  private final double selectedCapacityKgPerS;
  private final boolean capacityAdequate;
  private final boolean accumulatedPressureAccepted;
  private final double accumulatedPressureMarginBara;
  private final String acceptanceBasis;
  private final List<String> warnings;

  private Api521ReliefAssessment(String standardEdition, OverpressureStudyResult result) {
    this.standardEdition = standardEdition;
    protectedItemName = result.getItem().getName();
    List<ScenarioSummary> scenarioSnapshots = new ArrayList<ScenarioSummary>();
    for (ReliefScenario scenario : result.getScenarios()) {
      scenarioSnapshots.add(new ScenarioSummary(scenario));
    }
    scenarios = Collections.unmodifiableList(scenarioSnapshots);
    ReliefScenario governing = result.getGoverningScenario();
    governingScenarioName = governing == null ? null : governing.getName();
    governingCause = governing == null ? null : governing.getCause().name();
    governingPhase = governing == null ? null : governing.getPhase().name();
    governingReliefRateKgPerS = governing == null ? Double.NaN : governing.getReliefRateKgPerS();
    requiredAreaM2 = result.getRequiredAreaM2();
    requiredAreaIn2 = result.getRequiredAreaIn2();
    recommendedOrifice = result.getRecommendedOrifice();
    selectedAreaIn2 = result.getSelectedAreaIn2();
    selectedCapacityKgPerS = result.getSelectedOrificeCapacityKgPerS();
    capacityAdequate = result.isCapacityAdequate();
    AcceptanceResult acceptance = result.getAcceptance();
    accumulatedPressureAccepted = acceptance != null && acceptance.isAccepted();
    accumulatedPressureMarginBara = acceptance == null ? Double.NaN : acceptance.getMarginBara();
    acceptanceBasis = acceptance == null ? "" : acceptance.getBasis();
    warnings = Collections.unmodifiableList(new ArrayList<String>(result.getWarnings()));
  }

  static Api521ReliefAssessment from(String standardEdition, OverpressureStudyResult result) {
    return new Api521ReliefAssessment(standardEdition, result);
  }

  /** @return explicit edition used by the screening adapter */
  public String getStandardEdition() {
    return standardEdition;
  }

  /** @return protected equipment tag */
  public String getProtectedItemName() {
    return protectedItemName;
  }

  /** @return immutable documented-scenario summaries */
  public List<ScenarioSummary> getScenarios() {
    return scenarios;
  }

  /** @return governing scenario name */
  public String getGoverningScenarioName() {
    return governingScenarioName;
  }

  /** @return governing overpressure cause */
  public String getGoverningCause() {
    return governingCause;
  }

  /** @return governing relieving phase */
  public String getGoverningPhase() {
    return governingPhase;
  }

  /** @return governing required relief rate in kg/s */
  public double getGoverningReliefRateKgPerS() {
    return governingReliefRateKgPerS;
  }

  /** @return calculated required area in square metres */
  public double getRequiredAreaM2() {
    return requiredAreaM2;
  }

  /** @return calculated required area in square inches */
  public double getRequiredAreaIn2() {
    return requiredAreaIn2;
  }

  /** @return recommended standard orifice letter */
  public String getRecommendedOrifice() {
    return recommendedOrifice;
  }

  /** @return selected standard orifice area in square inches */
  public double getSelectedAreaIn2() {
    return selectedAreaIn2;
  }

  /** @return selected-orifice capacity in kg/s */
  public double getSelectedCapacityKgPerS() {
    return selectedCapacityKgPerS;
  }

  /** @return whether the selected orifice covers the governing required rate */
  public boolean isCapacityAdequate() {
    return capacityAdequate;
  }

  /** @return accumulated-pressure acceptance decision */
  public boolean isAccumulatedPressureAccepted() {
    return accumulatedPressureAccepted;
  }

  /** @return allowable accumulated-pressure margin in bara */
  public double getAccumulatedPressureMarginBara() {
    return accumulatedPressureMarginBara;
  }

  /** @return accumulated-pressure check basis */
  public String getAcceptanceBasis() {
    return acceptanceBasis;
  }

  /** @return immutable warnings from the underlying study */
  public List<String> getWarnings() {
    return warnings;
  }

  /** @return serializable assessment representation */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("standardEdition", standardEdition);
    result.put("protectedItemName", protectedItemName);
    List<Map<String, Object>> scenarioMaps = new ArrayList<Map<String, Object>>();
    for (ScenarioSummary scenario : scenarios) {
      scenarioMaps.add(scenario.toMap());
    }
    result.put("scenarios", scenarioMaps);
    result.put("governingScenarioName", governingScenarioName);
    result.put("governingCause", governingCause);
    result.put("governingPhase", governingPhase);
    result.put("governingReliefRateKgPerS", Double.valueOf(governingReliefRateKgPerS));
    result.put("requiredAreaM2", Double.valueOf(requiredAreaM2));
    result.put("requiredAreaIn2", Double.valueOf(requiredAreaIn2));
    result.put("recommendedOrifice", recommendedOrifice);
    result.put("selectedAreaIn2", Double.valueOf(selectedAreaIn2));
    result.put("selectedCapacityKgPerS", Double.valueOf(selectedCapacityKgPerS));
    result.put("capacityAdequate", Boolean.valueOf(capacityAdequate));
    result.put("accumulatedPressureAccepted", Boolean.valueOf(accumulatedPressureAccepted));
    result.put("accumulatedPressureMarginBara", Double.valueOf(accumulatedPressureMarginBara));
    result.put("acceptanceBasis", acceptanceBasis);
    result.put("warnings", new ArrayList<String>(warnings));
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }
}
