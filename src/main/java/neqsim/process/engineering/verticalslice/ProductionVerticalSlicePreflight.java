package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringApprovalStatus;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareInput;
import neqsim.process.safety.scenario.DynamicSafetyScenario;

/**
 * Fail-closed preflight for the controlled inlet-separation, compression and export workflow.
 *
 * <p>
 * The preflight checks controlled definitions only. It deliberately does not claim that a calculation converges or that
 * a safeguard is adequate; those claims belong to the subsequent simulator and qualification results.
 * </p>
 */
public final class ProductionVerticalSlicePreflight {
  private ProductionVerticalSlicePreflight() {
  }

  /** Assesses whether the controlled project inputs are complete enough to start the engineering design loop. */
  public static Result assess(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    if (project == null || policy == null) {
      throw new IllegalArgumentException("project and policy are required");
    }
    Map<String, Check> checks = new LinkedHashMap<String, Check>();
    checks.put("PROCESS_AND_SAFETY_TOPOLOGY", topology(project.getEngineeringProcessSystem(), policy));
    checks.put("CONTROLLED_CASE_DEFINITIONS", cases(project, policy));
    checks.put("COMPRESSOR_MAP_INPUT", compressorMap(project.getEngineeringProcessSystem(), policy));
    checks.put("DYNAMIC_SCENARIO_DEFINITIONS", dynamicScenarios(project, policy));
    checks.put("COUPLED_RELIEF_BLOWDOWN_FLARE_INPUT", coupledSafety(project, policy));
    checks.put("VERSIONED_STANDARDS_INPUT", standards(project, policy));
    checks.put("CONTROLLED_EVIDENCE_INPUT", evidence(project, policy));
    boolean ready = true;
    for (Check check : checks.values()) {
      ready &= check.isPassed();
    }
    return new Result(project.getProjectId(), project.getRevision(), policy.getPolicyId(), policy.getRevision(), checks,
        ready);
  }

  private static Check topology(ProcessSystem process, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder("Complete and correctly typed process/control/safety topology");
    requireType(process, policy.getSeparatorTag(), Separator.class, result);
    requireType(process, policy.getCompressorTag(), Compressor.class, result);
    requireType(process, policy.getCoolerTag(), Cooler.class, result);
    ProcessEquipmentInterface line = process == null ? null : process.getUnit(policy.getExportLineTag());
    if (line == null || !line.getClass().getSimpleName().toLowerCase().contains("pipe")) {
      result.block(policy.getExportLineTag() + " is missing or is not piping");
    }
    requireType(process, policy.getRecycleValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getRecycleUnitTag(), Recycle.class, result);
    requireType(process, policy.getPressureControlValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getLevelControlValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getReliefValveTag(), SafetyReliefValve.class, result);
    requireType(process, policy.getBlowdownValveTag(), BlowdownValve.class, result);
    requireType(process, policy.getSuctionEsdValveTag(), ESDValve.class, result);
    requireType(process, policy.getDischargeEsdValveTag(), ESDValve.class, result);
    if (process == null || process.getUnit(policy.getFlareConnectionTag()) == null) {
      result.block(policy.getFlareConnectionTag() + " flare connection is missing");
    }
    requirePath(process, policy.getSeparatorTag(), policy.getSuctionEsdValveTag(), result);
    requirePath(process, policy.getSuctionEsdValveTag(), policy.getCompressorTag(), result);
    requirePath(process, policy.getCompressorTag(), policy.getCoolerTag(), result);
    requirePath(process, policy.getCoolerTag(), policy.getDischargeEsdValveTag(), result);
    requirePath(process, policy.getDischargeEsdValveTag(), policy.getPressureControlValveTag(), result);
    requirePath(process, policy.getPressureControlValveTag(), policy.getExportLineTag(), result);
    requirePath(process, policy.getCoolerTag(), policy.getRecycleValveTag(), result);
    requirePath(process, policy.getRecycleValveTag(), policy.getRecycleUnitTag(), result);
    requirePath(process, policy.getRecycleUnitTag(), policy.getCompressorTag(), result);
    requirePath(process, policy.getSeparatorTag(), policy.getLevelControlValveTag(), result);
    requirePath(process, policy.getCoolerTag(), policy.getReliefValveTag(), result);
    requirePath(process, policy.getReliefValveTag(), policy.getFlareConnectionTag(), result);
    requirePath(process, policy.getCoolerTag(), policy.getBlowdownValveTag(), result);
    requirePath(process, policy.getBlowdownValveTag(), policy.getFlareConnectionTag(), result);
    return result.build();
  }

  private static void requirePath(ProcessSystem process, String fromTag, String toTag, CheckBuilder result) {
    if (!hasDirectedProcessPath(process, fromTag, toTag)) {
      result.block("No connected process path from " + fromTag + " to " + toTag);
    }
  }

  /** Returns true when process streams form a directed path between two tagged units. */
  static boolean hasDirectedProcessPath(ProcessSystem process, String fromTag, String toTag) {
    if (process == null || process.getUnit(fromTag) == null || process.getUnit(toTag) == null) {
      return false;
    }
    Map<String, ProcessEquipmentInterface> units = new LinkedHashMap<String, ProcessEquipmentInterface>();
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (unit != null && unit.getName() != null) {
        units.put(unit.getName(), unit);
      }
    }
    Set<String> visited = new LinkedHashSet<String>();
    ArrayDeque<String> pending = new ArrayDeque<String>();
    pending.add(fromTag);
    while (!pending.isEmpty()) {
      String currentTag = pending.removeFirst();
      if (!visited.add(currentTag)) {
        continue;
      }
      if (toTag.equals(currentTag)) {
        return true;
      }
      ProcessEquipmentInterface current = units.get(currentTag);
      if (current == null) {
        continue;
      }
      for (Map.Entry<String, ProcessEquipmentInterface> candidate : units.entrySet()) {
        if (!visited.contains(candidate.getKey()) && streamsConnect(current, candidate.getValue())) {
          pending.addLast(candidate.getKey());
        }
      }
    }
    return false;
  }

  private static boolean streamsConnect(ProcessEquipmentInterface upstream, ProcessEquipmentInterface downstream) {
    List<StreamInterface> outlets = upstream.getOutletStreams();
    List<StreamInterface> inlets = downstream.getInletStreams();
    if (outlets == null || inlets == null) {
      return false;
    }
    for (StreamInterface outlet : outlets) {
      for (StreamInterface inlet : inlets) {
        if (sameStream(outlet, inlet)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean sameStream(StreamInterface first, StreamInterface second) {
    if (first == null || second == null) {
      return false;
    }
    if (first == second) {
      return true;
    }
    String firstName = first.getName();
    String secondName = second.getName();
    return firstName != null && !firstName.trim().isEmpty() && firstName.equals(secondName);
  }

  private static void requireType(ProcessSystem process, String tag, Class<?> type, CheckBuilder result) {
    Object value = process == null ? null : process.getUnit(tag);
    if (!type.isInstance(value)) {
      result.block(tag + " is missing or is not " + type.getSimpleName());
    }
  }

  private static Check cases(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder("One enabled, controlled definition for every required case type");
    Map<EngineeringDesignCase.Type, List<EngineeringDesignCase>> byType = new EnumMap<EngineeringDesignCase.Type, List<EngineeringDesignCase>>(
        EngineeringDesignCase.Type.class);
    for (EngineeringDesignCase designCase : project.getExecutableDesignCases()) {
      List<EngineeringDesignCase> values = byType.get(designCase.getType());
      if (values == null) {
        values = new ArrayList<EngineeringDesignCase>();
        byType.put(designCase.getType(), values);
      }
      values.add(designCase);
    }
    for (EngineeringDesignCase.Type type : policy.getRequiredCaseTypes()) {
      List<EngineeringDesignCase> definitions = byType.get(type);
      if (definitions == null || definitions.isEmpty()) {
        result.block("Missing required case type " + type);
        continue;
      }
      if (definitions.size() > 1) {
        result.warn("Multiple definitions exist for " + type + "; all will run and governing values will be retained");
      }
      for (EngineeringDesignCase definition : definitions) {
        if (!definition.isEnabled()) {
          result.block(definition.getId() + " is disabled");
        }
        if (definition.getInputs().isEmpty()) {
          result.block(definition.getId() + " has no controlled scalar inputs");
        }
        if (definition.getEvidenceReferences().isEmpty()) {
          result.block(definition.getId() + " has no evidence reference");
        }
        for (String reference : definition.getEvidenceReferences()) {
          if (!hasEvidence(project, reference)) {
            result.block(definition.getId() + " references missing evidence " + reference);
          }
        }
        if (!"APPROVED".equalsIgnoreCase(definition.getApprovalStatus())) {
          result.warn(definition.getId() + " remains " + definition.getApprovalStatus());
        }
      }
    }
    return result.build();
  }

  private static Check compressorMap(ProcessSystem process, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder("Active compressor map with surge and stonewall limits");
    Object value = process == null ? null : process.getUnit(policy.getCompressorTag());
    if (!(value instanceof Compressor)) {
      result.block(policy.getCompressorTag() + " compressor is missing");
      return result.build();
    }
    Compressor compressor = (Compressor) value;
    if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
      result.block("Compressor chart is not active");
    } else {
      if (compressor.getCompressorChart().getSurgeCurve() == null
          || !compressor.getCompressorChart().getSurgeCurve().isActive()) {
        result.block("Compressor surge curve is not active");
      }
      if (compressor.getCompressorChart().getStoneWallCurve() == null
          || !compressor.getCompressorChart().getStoneWallCurve().isActive()) {
        result.block("Compressor stonewall curve is not active");
      }
    }
    return result.build();
  }

  private static Check dynamicScenarios(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder("Executable dynamic scenarios with criteria and controlled evidence");
    Map<String, DynamicSafetyScenario> scenarios = new LinkedHashMap<String, DynamicSafetyScenario>();
    for (DynamicSafetyScenario scenario : project.getDynamicSafetyScenarios()) {
      scenarios.put(scenario.getId(), scenario);
    }
    for (String required : policy.getRequiredDynamicScenarioIds()) {
      DynamicSafetyScenario scenario = scenarios.get(required);
      if (scenario == null) {
        result.block("Missing dynamic scenario " + required);
        continue;
      }
      if (scenario.getLogicFactories().isEmpty()) {
        result.block(required + " has no executable protection logic");
      }
      if (scenario.getCriteria().isEmpty()) {
        result.block(required + " has no measurable safe-state criteria");
      }
      if (scenario.getEvidenceReferences().isEmpty()) {
        result.block(required + " has no evidence reference");
      }
      for (String reference : scenario.getEvidenceReferences()) {
        if (!hasEvidence(project, reference)) {
          result.block(required + " references missing evidence " + reference);
        }
      }
    }
    return result.build();
  }

  private static Check coupledSafety(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder(
        "Reviewed relief scenarios coupled to calculation-ready blowdown/flare data");
    if (!policy.isCoupledReliefBlowdownFlareRequired()) {
      result.warn("Policy does not require a coupled relief, blowdown and flare calculation");
      return result.build();
    }
    if (project.getCoupledReliefBlowdownFlareStudies().isEmpty()) {
      result.block("No coupled relief, blowdown and flare study is configured");
      return result.build();
    }
    for (CoupledReliefBlowdownFlareInput study : project.getCoupledReliefBlowdownFlareStudies()) {
      if (study.getReliefStudies().isEmpty()) {
        result.block(study.getStudyId() + " has no protected-item relief studies");
      }
      if (study.getDynamicStudy() == null) {
        result.block(study.getStudyId() + " has no dynamic blowdown/flare study");
      } else if (!study.getDynamicStudy().readiness().isReadyForCalculation()) {
        result.block(study.getStudyId() + " dynamic blowdown/flare input is not calculation-ready");
      }
      if (!study.isScenarioSelectionReviewed()) {
        result.block(study.getStudyId() + " scenario credibility and concurrency are not marked reviewed");
      }
      if (study.getEvidenceReferences().isEmpty()) {
        result.block(study.getStudyId() + " has no evidence reference");
      }
      for (String reference : study.getEvidenceReferences()) {
        if (!hasEvidence(project, reference)) {
          result.block(study.getStudyId() + " references missing evidence " + reference);
        }
      }
    }
    return result.build();
  }

  private static Check standards(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder("All policy standards have controlled project editions");
    Set<String> present = new LinkedHashSet<String>();
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      present.add(standard.getCode());
    }
    for (String required : policy.getRequiredStandards()) {
      boolean found = false;
      for (String available : present) {
        found |= available.equals(required) || available.startsWith(required + " ");
      }
      if (!found) {
        result.block("Missing standard " + required);
      }
    }
    return result.build();
  }

  private static Check evidence(EngineeringProject project, InletCompressionExportSlicePolicy policy) {
    CheckBuilder result = new CheckBuilder(
        "Complete, revision-controlled evidence records linked to engineering objects");
    for (String reference : policy.getEvidenceReferences()) {
      EngineeringEvidenceRecord record = evidence(project, reference);
      if (record == null) {
        result.block("Missing controlled evidence " + reference);
      } else {
        if (!record.getMissingFields().isEmpty()) {
          result.block("Incomplete controlled evidence " + reference + ": " + record.getMissingFields());
        }
        if (record.getApprovalStatus() != EngineeringApprovalStatus.APPROVED) {
          result.warn(reference + " remains " + record.getApprovalStatus());
        }
      }
    }
    return result.build();
  }

  private static boolean hasEvidence(EngineeringProject project, String reference) {
    return evidence(project, reference) != null;
  }

  private static EngineeringEvidenceRecord evidence(EngineeringProject project, String reference) {
    for (EngineeringEvidenceRecord record : project.getEvidenceRecords()) {
      if (reference.equals(record.getDocumentId())
          || reference.equals(record.getDocumentId() + "@" + record.getRevision())) {
        return record;
      }
    }
    return null;
  }

  private static final class CheckBuilder {
    private final String acceptanceBasis;
    private final List<String> blockers = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();

    CheckBuilder(String acceptanceBasis) {
      this.acceptanceBasis = acceptanceBasis;
    }

    void block(String value) {
      blockers.add(value);
    }

    void warn(String value) {
      warnings.add(value);
    }

    Check build() {
      return new Check(acceptanceBasis, blockers, warnings);
    }
  }

  /** One preflight check with separated execution blockers and engineering-review warnings. */
  public static final class Check implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String acceptanceBasis;
    private final List<String> blockers;
    private final List<String> warnings;

    Check(String acceptanceBasis, List<String> blockers, List<String> warnings) {
      this.acceptanceBasis = acceptanceBasis;
      this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
      this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public boolean isPassed() {
      return blockers.isEmpty();
    }

    public List<String> getBlockers() {
      return blockers;
    }

    public List<String> getWarnings() {
      return warnings;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("passed", Boolean.valueOf(isPassed()));
      result.put("acceptanceBasis", acceptanceBasis);
      result.put("blockers", blockers);
      result.put("warnings", warnings);
      return result;
    }
  }

  /** Immutable preflight record used to authorize or block strict execution. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String projectId;
    private final String projectRevision;
    private final String policyId;
    private final String policyRevision;
    private final Map<String, Check> checks;
    private final boolean readyForSimulation;

    Result(String projectId, String projectRevision, String policyId, String policyRevision, Map<String, Check> checks,
        boolean readyForSimulation) {
      this.projectId = projectId;
      this.projectRevision = projectRevision;
      this.policyId = policyId;
      this.policyRevision = policyRevision;
      this.checks = Collections.unmodifiableMap(new LinkedHashMap<String, Check>(checks));
      this.readyForSimulation = readyForSimulation;
    }

    public boolean isReadyForSimulation() {
      return readyForSimulation;
    }

    public List<String> getFailedChecks() {
      List<String> result = new ArrayList<String>();
      for (Map.Entry<String, Check> check : checks.entrySet()) {
        if (!check.getValue().isPassed()) {
          result.add(check.getKey());
        }
      }
      return result;
    }

    public List<String> getBlockers() {
      List<String> result = new ArrayList<String>();
      for (Map.Entry<String, Check> check : checks.entrySet()) {
        for (String blocker : check.getValue().getBlockers()) {
          result.add(check.getKey() + ": " + blocker);
        }
      }
      return result;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("projectId", projectId);
      result.put("projectRevision", projectRevision);
      result.put("policyId", policyId);
      result.put("policyRevision", policyRevision);
      Map<String, Object> mappedChecks = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Check> check : checks.entrySet()) {
        mappedChecks.put(check.getKey(), check.getValue().toMap());
      }
      result.put("checks", mappedChecks);
      result.put("failedChecks", getFailedChecks());
      result.put("readyForSimulation", Boolean.valueOf(readyForSimulation));
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }
}
