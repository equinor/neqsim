package neqsim.process.engineering.production;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EngineeringConstraintResult;
import neqsim.process.engineering.instrumentation.ValveInstrumentQualificationCalculation;
import neqsim.process.engineering.mechanical.MechanicalIntegrityQualificationCalculation;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.engineering.piping.TransientPipingQualificationCalculation;
import neqsim.process.engineering.rotating.CompressorProtectionQualificationCalculation;
import neqsim.process.engineering.safety.FlareConsequenceCalculation;

/** Controlled evidence bundle consumed by the production-readiness assessment. */
public final class EngineeringProductionReadinessBasis implements Serializable {
  private static final long serialVersionUID = 1000L;
  private EngineeringBenchmarkSuite.Report benchmarkReport;
  private EngineeringAutoConfigurator.Result autoConfigurationResult;
  private final List<EngineeringMethodQualification> methodQualifications = new ArrayList<EngineeringMethodQualification>();
  private final List<DexpiToolQualificationEvidence> dexpiEvidence = new ArrayList<DexpiToolQualificationEvidence>();
  private final List<EngineeringPilotProjectEvidence> pilotEvidence = new ArrayList<EngineeringPilotProjectEvidence>();
  private EngineeringReleaseQualityEvidence releaseQualityEvidence;
  private EngineeringExternalEvidenceRegister externalEvidenceRegister;
  private EngineeringCalculationResult<TransientPipingQualificationCalculation.Result> transientPipingQualification;
  private EngineeringCalculationResult<CompressorProtectionQualificationCalculation.Result> compressorProtectionQualification;
  private EngineeringCalculationResult<ValveInstrumentQualificationCalculation.Result> valveInstrumentQualification;
  private EngineeringCalculationResult<MechanicalIntegrityQualificationCalculation.Result> mechanicalIntegrityQualification;
  private EngineeringCalculationResult<FlareConsequenceCalculation.Result> flareConsequenceQualification;

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

  /** Attaches requirements and controlled receipts for evidence issued outside the simulator. */
  public EngineeringProductionReadinessBasis externalEvidenceRegister(EngineeringExternalEvidenceRegister value) {
    if (value == null) {
      throw new IllegalArgumentException("externalEvidenceRegister must not be null");
    }
    externalEvidenceRegister = value;
    return this;
  }

  public EngineeringProductionReadinessBasis transientPipingQualification(
      EngineeringCalculationResult<TransientPipingQualificationCalculation.Result> value) {
    transientPipingQualification = require(value, "transientPipingQualification");
    return this;
  }

  public EngineeringProductionReadinessBasis compressorProtectionQualification(
      EngineeringCalculationResult<CompressorProtectionQualificationCalculation.Result> value) {
    compressorProtectionQualification = require(value, "compressorProtectionQualification");
    return this;
  }

  public EngineeringProductionReadinessBasis valveInstrumentQualification(
      EngineeringCalculationResult<ValveInstrumentQualificationCalculation.Result> value) {
    valveInstrumentQualification = require(value, "valveInstrumentQualification");
    return this;
  }

  public EngineeringProductionReadinessBasis mechanicalIntegrityQualification(
      EngineeringCalculationResult<MechanicalIntegrityQualificationCalculation.Result> value) {
    mechanicalIntegrityQualification = require(value, "mechanicalIntegrityQualification");
    return this;
  }

  public EngineeringProductionReadinessBasis flareConsequenceQualification(
      EngineeringCalculationResult<FlareConsequenceCalculation.Result> value) {
    flareConsequenceQualification = require(value, "flareConsequenceQualification");
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

  public EngineeringExternalEvidenceRegister getExternalEvidenceRegister() {
    return externalEvidenceRegister;
  }

  public EngineeringCalculationResult<TransientPipingQualificationCalculation.Result> getTransientPipingQualification() {
    return transientPipingQualification;
  }

  public EngineeringCalculationResult<CompressorProtectionQualificationCalculation.Result> getCompressorProtectionQualification() {
    return compressorProtectionQualification;
  }

  public EngineeringCalculationResult<ValveInstrumentQualificationCalculation.Result> getValveInstrumentQualification() {
    return valveInstrumentQualification;
  }

  public EngineeringCalculationResult<MechanicalIntegrityQualificationCalculation.Result> getMechanicalIntegrityQualification() {
    return mechanicalIntegrityQualification;
  }

  public EngineeringCalculationResult<FlareConsequenceCalculation.Result> getFlareConsequenceQualification() {
    return flareConsequenceQualification;
  }

  /**
   * Gets every technical qualification method that must be benchmarked and project-qualified.
   *
   * @return stable method keys in {@code method@version} form
   */
  public Set<String> getTechnicalMethodKeys() {
    Set<String> result = new LinkedHashSet<String>();
    addMethodKey(result, transientPipingQualification);
    addMethodKey(result, compressorProtectionQualification);
    addMethodKey(result, valveInstrumentQualification);
    addMethodKey(result, mechanicalIntegrityQualification);
    addMethodKey(result, flareConsequenceQualification);
    return Collections.unmodifiableSet(result);
  }

  /**
   * Adapts the typed technical qualification results to canonical calculation records.
   *
   * <p>
   * This keeps the calculation DAG, engineering graph, and production-readiness evidence on the same identifiers
   * without weakening the typed calculation API.
   *
   * @param subjectNodeId canonical project node governed by the qualification results
   * @return canonical calculation records in stable discipline order
   */
  public List<EngineeringCalculation> getTechnicalQualificationCalculations(String subjectNodeId) {
    List<EngineeringCalculation> result = new ArrayList<EngineeringCalculation>();
    addCalculation(result, transientPipingQualification, subjectNodeId);
    addCalculation(result, compressorProtectionQualification, subjectNodeId);
    addCalculation(result, valveInstrumentQualification, subjectNodeId);
    addCalculation(result, mechanicalIntegrityQualification, subjectNodeId);
    addCalculation(result, flareConsequenceQualification, subjectNodeId);
    return Collections.unmodifiableList(result);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("benchmarkReport", benchmarkReport == null ? null : benchmarkReport.toMap());
    result.put("autoConfigurationResult", autoConfigurationResult == null ? null : autoConfigurationResult.toMap());
    result.put("methodQualifications", maps(methodQualifications));
    result.put("dexpiToolQualificationEvidence", maps(dexpiEvidence));
    result.put("pilotProjectEvidence", maps(pilotEvidence));
    result.put("releaseQualityEvidence", releaseQualityEvidence == null ? null : releaseQualityEvidence.toMap());
    result.put("externalEvidenceRegister", externalEvidenceRegister == null ? null : externalEvidenceRegister.toMap());
    result.put("transientPipingQualification", map(transientPipingQualification));
    result.put("compressorProtectionQualification", map(compressorProtectionQualification));
    result.put("valveInstrumentQualification", map(valveInstrumentQualification));
    result.put("mechanicalIntegrityQualification", map(mechanicalIntegrityQualification));
    result.put("flareConsequenceQualification", map(flareConsequenceQualification));
    return result;
  }

  private static Map<String, Object> map(EngineeringCalculationResult<?> value) {
    return value == null ? null : value.toMap();
  }

  private static void addMethodKey(Set<String> target, EngineeringCalculationResult<?> value) {
    if (value != null) {
      target.add(value.getMethod() + "@" + value.getMethodVersion());
    }
  }

  private static void addCalculation(List<EngineeringCalculation> target, EngineeringCalculationResult<?> value,
      String subjectNodeId) {
    if (value == null) {
      return;
    }
    EngineeringCalculation calculation = new EngineeringCalculation(value.getCalculationId(), subjectNodeId,
        value.getMethod() + "@" + value.getMethodVersion()).setStatus(status(value.getStatus()))
        .setDesignCaseId(value.getContext().getDesignCaseId()).setMessage(value.getMessage())
        .setStandardsRequired(true);
    if (value.getValue() instanceof EngineeringConstraintResult) {
      calculation.setResult(((EngineeringConstraintResult) value.getValue()).allConstraintsSatisfied() ? 1.0 : 0.0,
          "fraction");
    }
    for (String standard : value.getContext().getStandardReferences()) {
      calculation.addStandardReference(
          new EngineeringCalculation.StandardReference(standard, "", "", "production qualification"));
    }
    for (String evidence : value.getContext().getEvidenceReferences()) {
      calculation.addEvidenceReference(evidence);
    }
    target.add(calculation);
  }

  private static EngineeringCalculation.Status status(EngineeringCalculationResult.Status value) {
    if (value == EngineeringCalculationResult.Status.CALCULATED) {
      return EngineeringCalculation.Status.CALCULATED;
    }
    if (value == EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED) {
      return EngineeringCalculation.Status.REVIEW_REQUIRED;
    }
    if (value == EngineeringCalculationResult.Status.FAILED) {
      return EngineeringCalculation.Status.FAILED;
    }
    return EngineeringCalculation.Status.BLOCKED;
  }

  private static <T> EngineeringCalculationResult<T> require(EngineeringCalculationResult<T> value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    return value;
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
