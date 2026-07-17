package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.production.EngineeringAutoConfigurationPolicy;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput;
import neqsim.process.safety.scenario.DynamicSafetyScenario;

/** Immutable execution manifest binding a simulator run to controlled inputs and policy revisions. */
public final class ProductionVerticalSliceExecutionManifest implements Serializable {
  private static final long serialVersionUID = 1000L;
  private final String projectId;
  private final String projectRevision;
  private final String designPolicyId;
  private final String designPolicyRevision;
  private final String designPolicyFingerprint;
  private final String qualificationPolicyId;
  private final String qualificationPolicyRevision;
  private final String qualificationPolicyFingerprint;
  private final String inputFingerprint;
  private final List<String> caseDefinitions;
  private final List<String> dynamicScenarios;
  private final List<String> coupledStudies;
  private final List<String> standards;
  private final List<String> evidence;
  private final boolean preflightReady;
  private final List<String> preflightBlockers;

  private ProductionVerticalSliceExecutionManifest(EngineeringProject project,
      EngineeringAutoConfigurationPolicy designPolicy, InletCompressionExportSlicePolicy qualificationPolicy,
      ProductionVerticalSlicePreflight.Result preflight) {
    projectId = project.getProjectId();
    projectRevision = project.getRevision();
    designPolicyId = designPolicy.getId();
    designPolicyRevision = designPolicy.getRevision();
    designPolicyFingerprint = designPolicy.getPolicyFingerprint();
    qualificationPolicyId = qualificationPolicy.getPolicyId();
    qualificationPolicyRevision = qualificationPolicy.getRevision();
    qualificationPolicyFingerprint = sha256(qualificationPolicy.toMap().toString());
    caseDefinitions = immutableSorted(caseDefinitions(project));
    dynamicScenarios = immutableSorted(dynamicScenarios(project));
    coupledStudies = immutableSorted(coupledStudies(project));
    standards = immutableSorted(standards(project));
    evidence = immutableSorted(evidence(project));
    preflightReady = preflight.isReadyForSimulation();
    preflightBlockers = Collections.unmodifiableList(new ArrayList<String>(preflight.getBlockers()));
    inputFingerprint = sha256(fingerprintMaterial());
  }

  public static ProductionVerticalSliceExecutionManifest build(EngineeringProject project,
      EngineeringAutoConfigurationPolicy designPolicy, InletCompressionExportSlicePolicy qualificationPolicy,
      ProductionVerticalSlicePreflight.Result preflight) {
    if (project == null || designPolicy == null || qualificationPolicy == null || preflight == null) {
      throw new IllegalArgumentException("project, design policy, qualification policy and preflight are required");
    }
    return new ProductionVerticalSliceExecutionManifest(project, designPolicy, qualificationPolicy, preflight);
  }

  public String getInputFingerprint() {
    return inputFingerprint;
  }

  public boolean isPreflightReady() {
    return preflightReady;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", EngineeringSchemaCatalog.VERTICAL_SLICE_EXECUTION_MANIFEST);
    result.put("schemaUri",
        EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.VERTICAL_SLICE_EXECUTION_MANIFEST));
    result.put("status", preflightReady ? "READY_FOR_SIMULATION" : "BLOCKED");
    result.put("projectId", projectId);
    result.put("projectRevision", projectRevision);
    result.put("designPolicyId", designPolicyId);
    result.put("designPolicyRevision", designPolicyRevision);
    result.put("designPolicyFingerprint", designPolicyFingerprint);
    result.put("qualificationPolicyId", qualificationPolicyId);
    result.put("qualificationPolicyRevision", qualificationPolicyRevision);
    result.put("qualificationPolicyFingerprint", qualificationPolicyFingerprint);
    result.put("inputFingerprint", inputFingerprint);
    result.put("caseDefinitions", caseDefinitions);
    result.put("dynamicScenarios", dynamicScenarios);
    result.put("coupledStudies", coupledStudies);
    result.put("standards", standards);
    result.put("evidence", evidence);
    result.put("preflightReady", Boolean.valueOf(preflightReady));
    result.put("preflightBlockers", preflightBlockers);
    result.put("fitnessForConstruction", Boolean.FALSE);
    result.put("engineeringApprovalRequired", Boolean.TRUE);
    return result;
  }

  private String fingerprintMaterial() {
    return projectId + '|' + projectRevision + '|' + designPolicyId + '|' + designPolicyRevision + '|'
        + designPolicyFingerprint + '|' + qualificationPolicyId + '|' + qualificationPolicyRevision + '|'
        + qualificationPolicyFingerprint + '|' + caseDefinitions + '|' + dynamicScenarios + '|' + coupledStudies + '|'
        + standards + '|' + evidence;
  }

  private static List<String> caseDefinitions(EngineeringProject project) {
    List<String> result = new ArrayList<String>();
    for (EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      result.add(designCase.toMap().toString());
    }
    return result;
  }

  private static List<String> dynamicScenarios(EngineeringProject project) {
    List<String> result = new ArrayList<String>();
    for (DynamicSafetyScenario scenario : project.getDynamicSafetyScenarios()) {
      result.add(scenario.toMap().toString());
    }
    return result;
  }

  private static List<String> coupledStudies(EngineeringProject project) {
    List<String> result = new ArrayList<String>();
    for (CoupledReliefBlowdownFlareInput study : project.getCoupledReliefBlowdownFlareStudies()) {
      result.add(study.toMap().toString());
    }
    return result;
  }

  private static List<String> standards(EngineeringProject project) {
    List<String> result = new ArrayList<String>();
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      result.add(standard.getCode() + '@' + standard.getEdition() + '@' + standard.getTitle() + '@'
          + standard.getApplication());
    }
    return result;
  }

  private static List<String> evidence(EngineeringProject project) {
    List<String> result = new ArrayList<String>();
    for (EngineeringEvidenceRecord record : project.getEvidenceRecords()) {
      result.add(record.toMap().toString());
    }
    return result;
  }

  private static List<String> immutableSorted(List<String> values) {
    Collections.sort(values);
    return Collections.unmodifiableList(values);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : bytes) {
        result.append(String.format("%02x", Integer.valueOf(item & 0xff)));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
