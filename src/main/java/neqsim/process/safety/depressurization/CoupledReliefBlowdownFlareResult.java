package neqsim.process.safety.depressurization;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.overpressure.OverpressureStudyResult;
import neqsim.process.safety.overpressure.ReliefScenario;

/** Result joining governing PSV cases, concurrent disposal loads, and transient flare performance. */
public final class CoupledReliefBlowdownFlareResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String studyId;
  private final List<OverpressureStudyResult> reliefResults;
  private final Map<String, Double> steadyLoadByConcurrencyGroupKgPerS;
  private final DynamicBlowdownFlareStudyHandoff dynamicHandoff;
  private final double governingMassFlowKgPerS;
  private final String governingBasis;
  private final boolean capacityAcceptable;
  private final List<String> findings;

  CoupledReliefBlowdownFlareResult(String studyId, List<OverpressureStudyResult> reliefResults,
      Map<String, Double> steadyLoadByConcurrencyGroupKgPerS, DynamicBlowdownFlareStudyHandoff dynamicHandoff,
      double governingMassFlowKgPerS, String governingBasis, boolean capacityAcceptable, List<String> findings) {
    this.studyId = studyId;
    this.reliefResults = Collections.unmodifiableList(new ArrayList<OverpressureStudyResult>(reliefResults));
    this.steadyLoadByConcurrencyGroupKgPerS = Collections
        .unmodifiableMap(new LinkedHashMap<String, Double>(steadyLoadByConcurrencyGroupKgPerS));
    this.dynamicHandoff = dynamicHandoff;
    this.governingMassFlowKgPerS = governingMassFlowKgPerS;
    this.governingBasis = governingBasis;
    this.capacityAcceptable = capacityAcceptable;
    this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
  }

  public double getGoverningMassFlowKgPerS() {
    return governingMassFlowKgPerS;
  }

  public String getGoverningBasis() {
    return governingBasis;
  }

  public boolean isCapacityAcceptable() {
    return capacityAcceptable;
  }

  public Map<String, Double> getSteadyLoadByConcurrencyGroupKgPerS() {
    return steadyLoadByConcurrencyGroupKgPerS;
  }

  public DynamicBlowdownFlareStudyHandoff getDynamicHandoff() {
    return dynamicHandoff;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "coupled_relief_blowdown_flare_result.v1");
    result.put("studyId", studyId);
    List<Map<String, Object>> relief = new ArrayList<Map<String, Object>>();
    for (OverpressureStudyResult studyResult : reliefResults) {
      relief.add(reliefMap(studyResult));
    }
    result.put("reliefStudies", relief);
    result.put("steadyLoadByConcurrencyGroupKgPerS",
        new LinkedHashMap<String, Double>(steadyLoadByConcurrencyGroupKgPerS));
    result.put("dynamicStudy", dynamicHandoff == null ? null : dynamicHandoff.toMap());
    result.put("governingMassFlowKgPerS", Double.valueOf(governingMassFlowKgPerS));
    result.put("governingBasis", governingBasis);
    result.put("capacityAcceptable", Boolean.valueOf(capacityAcceptable));
    result.put("findings", new ArrayList<String>(findings));
    result.put("scenarioCredibilityRequiresHazardReview", Boolean.TRUE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  private static Map<String, Object> reliefMap(OverpressureStudyResult studyResult) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("protectedItem", studyResult.getItem().getName());
    ReliefScenario governing = studyResult.getGoverningScenario();
    if (governing != null) {
      result.put("governingScenario", governing.getName());
      result.put("governingCause", governing.getCause().name());
      result.put("governingPhase", governing.getPhase().name());
      result.put("reliefRateKgPerS", Double.valueOf(governing.getReliefRateKgPerS()));
    }
    result.put("requiredAreaM2", finiteOrNull(studyResult.getRequiredAreaM2()));
    result.put("recommendedOrifice", studyResult.getRecommendedOrifice());
    result.put("capacityAdequate", Boolean.valueOf(studyResult.isCapacityAdequate()));
    result.put("accumulatedPressureAccepted",
        studyResult.getAcceptance() == null ? null : Boolean.valueOf(studyResult.getAcceptance().isAccepted()));
    result.put("warnings", new ArrayList<String>(studyResult.getWarnings()));
    return result;
  }

  private static Double finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }
}
