package neqsim.process.engineering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.engineering.EngineeringValidationReport.Severity;
import neqsim.process.engineering.automation.EngineeringAutomationStudy;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.designcase.EngineeringMetric;
import neqsim.process.engineering.model.EngineeringCalculation;
import neqsim.process.materials.MaterialsReviewInput;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource;
import neqsim.process.safety.esd.EmergencyShutdownTestResult;
import neqsim.process.safety.overpressure.OverpressureProtectionStudy;

/**
 * Governed engineering representation associated with a runnable NeqSim process.
 *
 * <p>
 * The process model remains the calculation authority. This class holds the design basis, deterministic engineering
 * proposals, standards traceability, and approval state needed for DEXPI and engineering-deliverable generation.
 * </p>
 */
public final class EngineeringProject implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final String name;
  private String revision = "working";
  private final ProcessSystem processSystem;
  private final EngineeringDesignBasis designBasis;
  private final List<EngineeringRequirement> requirements = new ArrayList<EngineeringRequirement>();
  private final List<OverpressureProtectionStudy> overpressureStudies = new ArrayList<OverpressureProtectionStudy>();
  private final List<DynamicBlowdownFlareStudyDataSource> blowdownFlareStudies = new ArrayList<DynamicBlowdownFlareStudyDataSource>();
  private final List<LineDesignInput> lineDesignInputs = new ArrayList<LineDesignInput>();
  private final List<ReliefScenarioBasis> reliefScenarioBases = new ArrayList<ReliefScenarioBasis>();
  private final List<SafetyFunctionDesign> safetyFunctionDesigns = new ArrayList<SafetyFunctionDesign>();
  private final List<ShutdownSequence> shutdownSequences = new ArrayList<ShutdownSequence>();
  private final List<EngineeringBoundary> boundaries = new ArrayList<EngineeringBoundary>();
  private final List<EngineeringDesignCase> executableDesignCases = new ArrayList<EngineeringDesignCase>();
  private final List<EngineeringMetric> engineeringMetrics = new ArrayList<EngineeringMetric>();
  private final List<EngineeringCalculation> calculations = new ArrayList<EngineeringCalculation>();
  private final List<EngineeringApprovalRecord> approvalRecords = new ArrayList<EngineeringApprovalRecord>();
  private final List<EngineeringAutomationStudy> automationStudies = new ArrayList<EngineeringAutomationStudy>();
  private final List<ReliefDeviceDesignInput> reliefDeviceDesignInputs = new ArrayList<ReliefDeviceDesignInput>();
  private final List<EngineeringEvidenceRecord> evidenceRecords = new ArrayList<EngineeringEvidenceRecord>();
  private final Map<String, EmergencyShutdownTestResult> shutdownVerificationResults = new LinkedHashMap<String, EmergencyShutdownTestResult>();
  private MaterialsReviewInput materialsReviewInput;

  EngineeringProject(String name, ProcessSystem processSystem, EngineeringDesignBasis designBasis) {
    this(UUID.randomUUID().toString(), name, processSystem, designBasis);
  }

  EngineeringProject(String projectId, String name, ProcessSystem processSystem, EngineeringDesignBasis designBasis) {
    if (projectId == null || projectId.trim().isEmpty()) {
      throw new IllegalArgumentException("projectId must not be blank");
    }
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    if (designBasis == null) {
      throw new IllegalArgumentException("designBasis must not be null");
    }
    this.projectId = projectId.trim();
    this.name = name;
    this.processSystem = processSystem;
    this.designBasis = designBasis;
  }

  /**
   * Adds a requirement from a deterministic or project-specific rule pack.
   *
   * @param requirement requirement to add
   */
  public void addRequirement(EngineeringRequirement requirement) {
    if (requirement == null) {
      throw new IllegalArgumentException("requirement must not be null");
    }
    requirements.add(requirement);
  }

  /** @return immutable project identifier */
  public String getProjectId() {
    return projectId;
  }

  /** @return engineering project name */
  public String getName() {
    return name;
  }

  /** Sets the controlled project revision used by graph snapshots and change comparison. */
  public EngineeringProject setRevision(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("revision must not be blank");
    }
    revision = value.trim();
    return this;
  }

  /** @return controlled project revision or {@code working} */
  public String getRevision() {
    return revision;
  }

  /** @return associated runnable process system */
  public ProcessSystem getProcessSystem() {
    return processSystem;
  }

  /** @return project design basis */
  public EngineeringDesignBasis getDesignBasis() {
    return designBasis;
  }

  /** @return immutable generated-requirement list */
  public List<EngineeringRequirement> getRequirements() {
    return Collections.unmodifiableList(requirements);
  }

  /**
   * Adds a project-specific overpressure study. The supplied scenarios remain engineering inputs and the calculated PSV
   * size remains a review-required design result.
   *
   * @param study governed overpressure study
   * @return this project
   */
  public EngineeringProject addOverpressureStudy(OverpressureProtectionStudy study) {
    if (study == null) {
      throw new IllegalArgumentException("study must not be null");
    }
    overpressureStudies.add(study);
    return this;
  }

  /** @return immutable project-specific overpressure studies */
  public List<OverpressureProtectionStudy> getOverpressureStudies() {
    return Collections.unmodifiableList(overpressureStudies);
  }

  /**
   * Adds a readiness-gated transient blowdown and flare study data source.
   *
   * @param study governed study inputs and evidence
   * @return this project
   */
  public EngineeringProject addBlowdownFlareStudy(DynamicBlowdownFlareStudyDataSource study) {
    if (study == null) {
      throw new IllegalArgumentException("study must not be null");
    }
    blowdownFlareStudies.add(study);
    return this;
  }

  /** @return immutable transient blowdown and flare study inputs */
  public List<DynamicBlowdownFlareStudyDataSource> getBlowdownFlareStudies() {
    return Collections.unmodifiableList(blowdownFlareStudies);
  }

  /** Adds a controlled line-list row associated with pipeline equipment. */
  public EngineeringProject addLineDesignInput(LineDesignInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    lineDesignInputs.add(input);
    return this;
  }

  /** @return immutable line-list inputs */
  public List<LineDesignInput> getLineDesignInputs() {
    return Collections.unmodifiableList(lineDesignInputs);
  }

  /** Adds the hazard-review basis defining credible relief causes for one protected item. */
  public EngineeringProject addReliefScenarioBasis(ReliefScenarioBasis basis) {
    if (basis == null) {
      throw new IllegalArgumentException("basis must not be null");
    }
    reliefScenarioBases.add(basis);
    return this;
  }

  /** @return immutable relief-scenario bases */
  public List<ReliefScenarioBasis> getReliefScenarioBases() {
    return Collections.unmodifiableList(reliefScenarioBases);
  }

  /** Adds externally justified SIF architecture and reliability data for PFD screening. */
  public EngineeringProject addSafetyFunctionDesign(SafetyFunctionDesign design) {
    if (design == null) {
      throw new IllegalArgumentException("design must not be null");
    }
    safetyFunctionDesigns.add(design);
    return this;
  }

  /** @return immutable safety-function designs */
  public List<SafetyFunctionDesign> getSafetyFunctionDesigns() {
    return Collections.unmodifiableList(safetyFunctionDesigns);
  }

  /** Adds a review-governed shutdown cause-and-effect sequence. */
  public EngineeringProject addShutdownSequence(ShutdownSequence sequence) {
    if (sequence == null) {
      throw new IllegalArgumentException("sequence must not be null");
    }
    shutdownSequences.add(sequence);
    return this;
  }

  /** @return immutable shutdown sequences */
  public List<ShutdownSequence> getShutdownSequences() {
    return Collections.unmodifiableList(shutdownSequences);
  }

  /** Adds an explicit off-page process, flare, vent, drain, utility or recycle boundary. */
  public EngineeringProject addBoundary(EngineeringBoundary boundary) {
    if (boundary == null) {
      throw new IllegalArgumentException("boundary must not be null");
    }
    boundaries.add(boundary);
    return this;
  }

  /** @return immutable controlled document boundaries */
  public List<EngineeringBoundary> getBoundaries() {
    return Collections.unmodifiableList(boundaries);
  }

  /** Adds an executable design case evaluated on an isolated process copy. */
  public EngineeringProject addDesignCase(EngineeringDesignCase designCase) {
    if (designCase == null) {
      throw new IllegalArgumentException("designCase must not be null");
    }
    for (EngineeringDesignCase existing : executableDesignCases) {
      if (existing.getId().equals(designCase.getId())) {
        throw new IllegalArgumentException("Duplicate design case " + designCase.getId());
      }
    }
    executableDesignCases.add(designCase);
    return this;
  }

  /** @return immutable executable design-case list */
  public List<EngineeringDesignCase> getExecutableDesignCases() {
    return Collections.unmodifiableList(executableDesignCases);
  }

  /** Adds a quantity that will be enveloped across executable design cases. */
  public EngineeringProject addEngineeringMetric(EngineeringMetric metric) {
    if (metric == null) {
      throw new IllegalArgumentException("metric must not be null");
    }
    for (EngineeringMetric existing : engineeringMetrics) {
      if (existing.getId().equals(metric.getId())) {
        throw new IllegalArgumentException("Duplicate engineering metric " + metric.getId());
      }
    }
    engineeringMetrics.add(metric);
    return this;
  }

  /** @return immutable design-envelope metric list */
  public List<EngineeringMetric> getEngineeringMetrics() {
    return Collections.unmodifiableList(engineeringMetrics);
  }

  /** Adds a traceable calculation node and its dependencies to the engineering model. */
  public EngineeringProject addCalculation(EngineeringCalculation calculation) {
    if (calculation == null) {
      throw new IllegalArgumentException("calculation must not be null");
    }
    for (EngineeringCalculation existing : calculations) {
      if (existing.getId().equals(calculation.getId())) {
        throw new IllegalArgumentException("Duplicate engineering calculation " + calculation.getId());
      }
    }
    calculations.add(calculation);
    return this;
  }

  /** @return immutable auditable calculation list */
  public List<EngineeringCalculation> getCalculations() {
    return Collections.unmodifiableList(calculations);
  }

  /** Adds an accountable approval decision for a canonical engineering graph object. */
  public EngineeringProject addApprovalRecord(EngineeringApprovalRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("approval record must not be null");
    }
    for (EngineeringApprovalRecord existing : approvalRecords) {
      if (existing.getId().equals(record.getId())) {
        throw new IllegalArgumentException("Duplicate engineering approval record " + record.getId());
      }
    }
    approvalRecords.add(record);
    return this;
  }

  /** @return immutable accountable approval decision history */
  public List<EngineeringApprovalRecord> getApprovalRecords() {
    return Collections.unmodifiableList(approvalRecords);
  }

  /** Adds a governed bounded-screening or optimization study definition. */
  public EngineeringProject addAutomationStudy(EngineeringAutomationStudy study) {
    if (study == null) {
      throw new IllegalArgumentException("automation study must not be null");
    }
    for (EngineeringAutomationStudy existing : automationStudies) {
      if (existing.getId().equals(study.getId())) {
        throw new IllegalArgumentException("Duplicate engineering automation study " + study.getId());
      }
    }
    automationStudies.add(study);
    return this;
  }

  /** @return immutable governed automation study definitions */
  public List<EngineeringAutomationStudy> getAutomationStudies() {
    return Collections.unmodifiableList(automationStudies);
  }

  /** Adds installed relief-device and inlet/outlet piping design data. */
  public EngineeringProject addReliefDeviceDesignInput(ReliefDeviceDesignInput input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    reliefDeviceDesignInputs.add(input);
    return this;
  }

  /** @return immutable installed relief-device design inputs */
  public List<ReliefDeviceDesignInput> getReliefDeviceDesignInputs() {
    return Collections.unmodifiableList(reliefDeviceDesignInputs);
  }

  /** Adds a revision-controlled evidence record. */
  public EngineeringProject addEvidenceRecord(EngineeringEvidenceRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("record must not be null");
    }
    evidenceRecords.add(record);
    return this;
  }

  /** @return immutable engineering-evidence records */
  public List<EngineeringEvidenceRecord> getEvidenceRecords() {
    return Collections.unmodifiableList(evidenceRecords);
  }

  /**
   * Attaches a dynamic ESD verification result to its controlled shutdown sequence.
   *
   * @param sequenceId shutdown-sequence identifier
   * @param result dynamic ESD result
   * @return this project
   */
  public EngineeringProject addShutdownVerificationResult(String sequenceId, EmergencyShutdownTestResult result) {
    if (sequenceId == null || sequenceId.trim().isEmpty() || result == null) {
      throw new IllegalArgumentException("sequenceId and result are required");
    }
    shutdownVerificationResults.put(sequenceId.trim(), result);
    return this;
  }

  /** @return immutable shutdown-sequence to dynamic-verification result mapping */
  public Map<String, EmergencyShutdownTestResult> getShutdownVerificationResults() {
    return Collections.unmodifiableMap(shutdownVerificationResults);
  }

  /**
   * Sets project material-register and service data to overlay on simulation-derived conditions.
   *
   * @param input optional project materials input
   * @return this project
   */
  public EngineeringProject setMaterialsReviewInput(MaterialsReviewInput input) {
    this.materialsReviewInput = input;
    return this;
  }

  /** @return optional project material-register and service input */
  public MaterialsReviewInput getMaterialsReviewInput() {
    return materialsReviewInput;
  }

  /** Returns requirements associated with one equipment tag. */
  public List<EngineeringRequirement> getRequirementsForEquipment(String equipmentTag) {
    List<EngineeringRequirement> result = new ArrayList<EngineeringRequirement>();
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getEquipmentTag().equals(equipmentTag)) {
        result.add(requirement);
      }
    }
    return result;
  }

  /**
   * Validates topology identifiers, minimum design data, compressor protection data, and approval governance.
   *
   * @return validation report
   */
  public EngineeringValidationReport validate() {
    EngineeringValidationReport report = new EngineeringValidationReport();
    Set<String> tags = new HashSet<String>();
    for (ProcessEquipmentInterface unit : processSystem.getUnitOperations()) {
      if (unit == null || unit instanceof Stream) {
        continue;
      }
      String tag = unit.getName();
      if (tag == null || tag.trim().isEmpty()) {
        report.add(Severity.ERROR, "ENG-TAG-001", "", "Equipment has no tag name");
        continue;
      }
      if (!tags.add(tag)) {
        report.add(Severity.ERROR, "ENG-TAG-002", tag, "Equipment tag is not unique");
      }
      if (unit.getDesignConditions() == null || unit.getDesignConditions().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-DESIGN-001", tag,
            "Nameplate design conditions have not been specified or approved");
      }
      if (unit instanceof Compressor) {
        Compressor compressor = (Compressor) unit;
        if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
          report.add(Severity.WARNING, "ENG-COMP-001", tag, "No active vendor or design compressor map is available");
        }
        if (compressor.getAntiSurge() == null || !compressor.getAntiSurge().isActive()) {
          report.add(Severity.REVIEW, "ENG-COMP-002", tag, "Antisurge protection is not active in the process model");
        }
      }
    }
    for (EngineeringRequirement requirement : requirements) {
      if (requirement.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
        report.add(Severity.REVIEW, "ENG-APPROVAL-001", requirement.getEquipmentTag(),
            requirement.getId() + " requires engineering review");
      }
      if ((requirement.getType() == EngineeringRequirement.Type.TRIP
          || requirement.getType() == EngineeringRequirement.Type.FIRE_AND_GAS)
          && "SIL_UNASSIGNED".equals(requirement.getSilTarget())) {
        report.add(Severity.INFORMATION, "ENG-SIL-001", requirement.getEquipmentTag(),
            requirement.getId() + " has no SIL target; determine through HAZOP/LOPA and the SRS");
      }
    }
    if (!executableDesignCases.isEmpty() && engineeringMetrics.isEmpty()) {
      report.add(Severity.REVIEW, "ENG-CASE-001", name,
          "Executable design cases exist but no engineering envelope metrics are configured");
    }
    if (executableDesignCases.isEmpty() && !engineeringMetrics.isEmpty()) {
      report.add(Severity.REVIEW, "ENG-CASE-002", name,
          "Engineering envelope metrics exist but no executable design cases are configured");
    }
    for (EngineeringMetric metric : engineeringMetrics) {
      if (processSystem.getUnit(metric.getSubjectTag()) == null) {
        report.add(Severity.ERROR, "ENG-CASE-003", metric.getSubjectTag(),
            "Engineering metric references unknown equipment: " + metric.getId());
      }
    }
    Set<String> boundaryIds = new HashSet<String>();
    for (EngineeringBoundary boundary : boundaries) {
      if (!boundaryIds.add(boundary.getId())) {
        report.add(Severity.ERROR, "ENG-BOUNDARY-001", boundary.getEquipmentTag(),
            "Engineering boundary id is not unique: " + boundary.getId());
      }
      if (processSystem.getUnit(boundary.getEquipmentTag()) == null) {
        report.add(Severity.ERROR, "ENG-BOUNDARY-002", boundary.getEquipmentTag(),
            "Engineering boundary references unknown equipment");
      } else if (!boundary.isResolved()) {
        report.add(Severity.REVIEW, "ENG-BOUNDARY-003", boundary.getEquipmentTag(),
            boundary.getId() + " requires an external document tie-in");
      }
    }
    Set<String> requirementIds = new HashSet<String>();
    for (EngineeringRequirement requirement : requirements) {
      if (!requirementIds.add(requirement.getId())) {
        report.add(Severity.ERROR, "ENG-REQ-001", requirement.getId(), "Requirement ID is not unique");
      }
    }
    Set<String> sifTags = new HashSet<String>();
    for (SafetyFunctionDesign design : safetyFunctionDesigns) {
      if (!sifTags.add(design.getSifTag())) {
        report.add(Severity.ERROR, "ENG-SIF-004", design.getSifTag(), "Safety-function tag is not unique");
      }
      if (!requirementIds.contains(design.getRequirementId())) {
        report.add(Severity.ERROR, "ENG-SIF-001", design.getSifTag(),
            "Safety-function requirement ID does not exist in the project");
      }
      if (!design.areArchitecturalConstraintsMet()) {
        report.add(Severity.REVIEW, "ENG-SIF-002", design.getSifTag(),
            "Systematic capability or hardware-fault-tolerance evidence is incomplete");
      }
      if (!design.getMissingFields().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-SIF-003", design.getSifTag(),
            "Safety-function basis is incomplete: " + design.getMissingFields());
      }
    }
    Set<String> sequenceIds = new HashSet<String>();
    for (ShutdownSequence sequence : shutdownSequences) {
      if (!sequenceIds.add(sequence.getSequenceId())) {
        report.add(Severity.ERROR, "ENG-ESD-007", sequence.getSequenceId(), "Shutdown-sequence ID is not unique");
      }
      if (!sequence.getProtectedEquipmentTag().isEmpty()
          && processSystem.getUnit(sequence.getProtectedEquipmentTag()) == null) {
        report.add(Severity.ERROR, "ENG-ESD-003", sequence.getSequenceId(),
            "Protected equipment does not exist: " + sequence.getProtectedEquipmentTag());
      }
      for (String requirementId : sequence.getRequirementIds()) {
        if (!requirementIds.contains(requirementId)) {
          report.add(Severity.ERROR, "ENG-ESD-001", sequence.getSequenceId(),
              "Shutdown-sequence requirement ID does not exist: " + requirementId);
        }
      }
      if (!sequence.isWithinResponseTimeBudget()) {
        report.add(Severity.REVIEW, "ENG-ESD-002", sequence.getSequenceId(),
            "Shutdown sequence exceeds or lacks its response-time budget");
      }
      if (!sequence.getMissingFields().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-ESD-004", sequence.getSequenceId(),
            "Shutdown-sequence basis is incomplete: " + sequence.getMissingFields());
      }
    }
    Set<String> reliefDeviceTags = new HashSet<String>();
    for (ReliefDeviceDesignInput input : reliefDeviceDesignInputs) {
      if (!reliefDeviceTags.add(input.getDeviceTag())) {
        report.add(Severity.ERROR, "ENG-PSV-002", input.getDeviceTag(), "Relief-device tag is not unique");
      }
      if (processSystem.getUnit(input.getEquipmentTag()) == null) {
        report.add(Severity.ERROR, "ENG-PSV-001", input.getDeviceTag(),
            "Protected equipment does not exist: " + input.getEquipmentTag());
      }
      if (!input.getMissingFields().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-PSV-003", input.getDeviceTag(),
            "Installed relief-device basis is incomplete: " + input.getMissingFields());
      }
    }
    Set<String> evidenceKeys = new HashSet<String>();
    for (EngineeringEvidenceRecord evidence : evidenceRecords) {
      String evidenceKey = evidence.getDocumentId() + "@" + evidence.getRevision();
      if (!evidenceKeys.add(evidenceKey)) {
        report.add(Severity.ERROR, "ENG-EVIDENCE-003", evidence.getDocumentId(),
            "Evidence document ID and revision are not unique");
      }
      for (String requirementId : evidence.getRequirementIds()) {
        if (!requirementIds.contains(requirementId)) {
          report.add(Severity.ERROR, "ENG-EVIDENCE-001", evidence.getDocumentId(),
              "Evidence references unknown requirement: " + requirementId);
        }
      }
      for (String equipmentTag : evidence.getEquipmentTags()) {
        if (processSystem.getUnit(equipmentTag) == null) {
          report.add(Severity.ERROR, "ENG-EVIDENCE-002", evidence.getDocumentId(),
              "Evidence references unknown equipment: " + equipmentTag);
        }
      }
      if (!evidence.getMissingFields().isEmpty()) {
        report.add(Severity.REVIEW, "ENG-EVIDENCE-004", evidence.getDocumentId(),
            "Engineering-evidence record is incomplete: " + evidence.getMissingFields());
      }
    }
    for (Map.Entry<String, EmergencyShutdownTestResult> result : shutdownVerificationResults.entrySet()) {
      if (!sequenceIds.contains(result.getKey())) {
        report.add(Severity.ERROR, "ENG-ESD-005", result.getKey(),
            "Dynamic shutdown result references an unknown sequence");
      } else if (result.getValue().getVerdict() == EmergencyShutdownTestResult.Verdict.FAIL) {
        report.add(Severity.REVIEW, "ENG-ESD-006", result.getKey(), "Dynamic shutdown verification failed");
      }
    }
    return report;
  }

  /** Serializes the governed engineering manifest without serializing the complete process object graph. */
  public String toJson() {
    Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
    JsonObject root = new JsonObject();
    root.addProperty("projectId", projectId);
    root.addProperty("name", name);
    root.addProperty("revision", revision);
    root.addProperty("processName", processSystem.getName());

    JsonObject basis = new JsonObject();
    basis.addProperty("jurisdiction", designBasis.getJurisdiction());
    basis.addProperty("facilityType", designBasis.getFacilityType());
    basis.addProperty("projectPhase", designBasis.getProjectPhase());
    JsonArray cases = new JsonArray();
    for (String designCase : designBasis.getDesignCases()) {
      cases.add(designCase);
    }
    basis.add("designCases", cases);
    JsonArray standards = new JsonArray();
    for (EngineeringStandard standard : designBasis.getStandards()) {
      JsonObject item = new JsonObject();
      item.addProperty("code", standard.getCode());
      item.addProperty("edition", standard.getEdition());
      item.addProperty("title", standard.getTitle());
      item.addProperty("application", standard.getApplication());
      standards.add(item);
    }
    basis.add("standards", standards);
    root.add("designBasis", basis);

    root.add("requirements", gson.toJsonTree(requirements));
    JsonArray lineList = new JsonArray();
    for (LineDesignInput input : lineDesignInputs) {
      lineList.add(gson.toJsonTree(input.toMap()));
    }
    root.add("lineList", lineList);
    root.add("reliefScenarioBases", gson.toJsonTree(reliefScenarioBases));
    root.add("safetyFunctionDesigns", gson.toJsonTree(safetyFunctionDesigns));
    root.add("shutdownSequences", gson.toJsonTree(shutdownSequences));
    root.add("reliefDeviceDesignInputs", gson.toJsonTree(reliefDeviceDesignInputs));
    root.add("evidenceRecords", gson.toJsonTree(evidenceRecords));
    root.add("shutdownVerificationResults", gson.toJsonTree(shutdownVerificationResults));
    root.add("boundaries", gson.toJsonTree(boundaries));
    JsonArray executableCases = new JsonArray();
    for (EngineeringDesignCase designCase : executableDesignCases) {
      executableCases.add(gson.toJsonTree(designCase.toMap()));
    }
    root.add("executableDesignCases", executableCases);
    JsonArray metrics = new JsonArray();
    for (EngineeringMetric metric : engineeringMetrics) {
      metrics.add(gson.toJsonTree(metric.toMap()));
    }
    root.add("engineeringMetrics", metrics);
    JsonArray calculationNodes = new JsonArray();
    for (EngineeringCalculation calculation : calculations) {
      calculationNodes.add(gson.toJsonTree(calculation.toMap()));
    }
    root.add("calculationProvenance", calculationNodes);
    root.add("validation", gson.toJsonTree(validate().getFindings()));
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }
}
