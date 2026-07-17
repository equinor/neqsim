package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controlled evidence bundle consumed by the production-readiness assessment. */
public final class EngineeringProductionReadinessBasis implements Serializable {
  private static final long serialVersionUID = 1000L;
  private EngineeringBenchmarkSuite.Report benchmarkReport;
  private EngineeringAutoConfigurator.Result autoConfigurationResult;
  private final List<EngineeringMethodQualification> methodQualifications = new ArrayList<EngineeringMethodQualification>();
  private final List<DexpiToolQualificationEvidence> dexpiEvidence = new ArrayList<DexpiToolQualificationEvidence>();
  private final List<EngineeringPilotProjectEvidence> pilotEvidence = new ArrayList<EngineeringPilotProjectEvidence>();
  private EngineeringReleaseQualityEvidence releaseQualityEvidence;

  public EngineeringProductionReadinessBasis benchmarkReport(EngineeringBenchmarkSuite.Report value) {
    benchmarkReport = value;
    return this;
  }

  public EngineeringProductionReadinessBasis autoConfigurationResult(EngineeringAutoConfigurator.Result value) {
    autoConfigurationResult = value;
    return this;
  }

  public EngineeringProductionReadinessBasis addMethodQualification(EngineeringMethodQualification value) {
    if (value == null) {
      throw new IllegalArgumentException("methodQualification must not be null");
    }
    methodQualifications.add(value);
    return this;
  }

  public EngineeringProductionReadinessBasis addDexpiEvidence(DexpiToolQualificationEvidence value) {
    if (value == null) {
      throw new IllegalArgumentException("dexpiEvidence must not be null");
    }
    dexpiEvidence.add(value);
    return this;
  }

  public EngineeringProductionReadinessBasis addPilotEvidence(EngineeringPilotProjectEvidence value) {
    if (value == null) {
      throw new IllegalArgumentException("pilotEvidence must not be null");
    }
    pilotEvidence.add(value);
    return this;
  }

  public EngineeringProductionReadinessBasis releaseQualityEvidence(EngineeringReleaseQualityEvidence value) {
    releaseQualityEvidence = value;
    return this;
  }

  public EngineeringBenchmarkSuite.Report getBenchmarkReport() {
    return benchmarkReport;
  }

  public EngineeringAutoConfigurator.Result getAutoConfigurationResult() {
    return autoConfigurationResult;
  }

  public List<EngineeringMethodQualification> getMethodQualifications() {
    return Collections.unmodifiableList(methodQualifications);
  }

  public List<DexpiToolQualificationEvidence> getDexpiEvidence() {
    return Collections.unmodifiableList(dexpiEvidence);
  }

  public List<EngineeringPilotProjectEvidence> getPilotEvidence() {
    return Collections.unmodifiableList(pilotEvidence);
  }

  public EngineeringReleaseQualityEvidence getReleaseQualityEvidence() {
    return releaseQualityEvidence;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("benchmarkReport", benchmarkReport == null ? null : benchmarkReport.toMap());
    result.put("autoConfigurationResult", autoConfigurationResult == null ? null : autoConfigurationResult.toMap());
    result.put("methodQualifications", maps(methodQualifications));
    result.put("dexpiToolQualificationEvidence", maps(dexpiEvidence));
    result.put("pilotProjectEvidence", maps(pilotEvidence));
    result.put("releaseQualityEvidence", releaseQualityEvidence == null ? null : releaseQualityEvidence.toMap());
    return result;
  }

  private static List<Map<String, Object>> maps(List<?> values) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Object value : values) {
      if (value instanceof EngineeringMethodQualification) {
        result.add(((EngineeringMethodQualification) value).toMap());
      } else if (value instanceof DexpiToolQualificationEvidence) {
        result.add(((DexpiToolQualificationEvidence) value).toMap());
      } else if (value instanceof EngineeringPilotProjectEvidence) {
        result.add(((EngineeringPilotProjectEvidence) value).toMap());
      }
    }
    return result;
  }
}
