package neqsim.process.engineering;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.designcase.EngineeringCaseRunReport;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;

/** Coordinated result from the multi-case, safety-system, and dynamic engineering simulator. */
public final class EngineeringSimulationResult implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final String revision;
  private final EngineeringCaseRunReport caseRunReport;
  private final List<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>> coupledSafetyResults;
  private final List<DynamicSafetyScenarioResult> dynamicScenarioResults;

  EngineeringSimulationResult(String projectId, String revision, EngineeringCaseRunReport caseRunReport,
      List<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>> coupledSafetyResults,
      List<DynamicSafetyScenarioResult> dynamicScenarioResults) {
    this.projectId = projectId;
    this.revision = revision;
    this.caseRunReport = caseRunReport;
    this.coupledSafetyResults = Collections.unmodifiableList(
        new ArrayList<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>>(coupledSafetyResults));
    this.dynamicScenarioResults = Collections
        .unmodifiableList(new ArrayList<DynamicSafetyScenarioResult>(dynamicScenarioResults));
  }

  public EngineeringCaseRunReport getCaseRunReport() {
    return caseRunReport;
  }

  public List<EngineeringCalculationResult<CoupledReliefBlowdownFlareResult>> getCoupledSafetyResults() {
    return coupledSafetyResults;
  }

  public List<DynamicSafetyScenarioResult> getDynamicScenarioResults() {
    return dynamicScenarioResults;
  }

  public boolean hasFailedDynamicScenario() {
    for (DynamicSafetyScenarioResult result : dynamicScenarioResults) {
      if (!result.isPassed()) {
        return true;
      }
    }
    return false;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "engineering_simulation_result.v1");
    result.put("projectId", projectId);
    result.put("revision", revision);
    result.put("caseRun", caseRunReport == null ? null : caseRunReport.toMap());
    List<Map<String, Object>> safety = new ArrayList<Map<String, Object>>();
    for (EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> item : coupledSafetyResults) {
      Map<String, Object> map = item.toMap();
      CoupledReliefBlowdownFlareResult value = item.getValue();
      if (value != null) {
        map.put("value", value.toMap());
      }
      safety.add(map);
    }
    result.put("coupledReliefBlowdownFlare", safety);
    List<Map<String, Object>> dynamics = new ArrayList<Map<String, Object>>();
    for (DynamicSafetyScenarioResult item : dynamicScenarioResults) {
      dynamics.add(item.toMap());
    }
    result.put("dynamicControlAndSisScenarios", dynamics);
    result.put("failedDynamicScenario", Boolean.valueOf(hasFailedDynamicScenario()));
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }
}
