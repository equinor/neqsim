package neqsim.process.engineering.verticalslice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.engineering.EngineeringEvidenceRecord;
import neqsim.process.engineering.EngineeringProject;
import neqsim.process.engineering.EngineeringSimulationResult;
import neqsim.process.engineering.EngineeringStandard;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.design.EngineeringDesignLoopResult;
import neqsim.process.engineering.design.EngineeringDesignState;
import neqsim.process.engineering.designcase.DesignCaseResult;
import neqsim.process.engineering.designcase.EngineeringDesignCase;
import neqsim.process.engineering.validation.EngineeringSchemaCatalog;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.equipment.valve.SafetyReliefValve;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.depressurization.CoupledReliefBlowdownFlareResult;
import neqsim.process.safety.scenario.DynamicSafetyScenarioResult;

/** Fail-closed qualification of the first complete process-to-engineering production vertical slice. */
public final class InletCompressionExportSliceQualification {
  private InletCompressionExportSliceQualification() {
  }

  public static Result qualify(EngineeringProject project, EngineeringSimulationResult simulation,
      InletCompressionExportSlicePolicy policy) {
    if (project == null || simulation == null || policy == null) {
      throw new IllegalArgumentException("project, simulation and policy are required");
    }
    Map<String, Gate> gates = new LinkedHashMap<String, Gate>();
    EngineeringDesignLoopResult loop = simulation.getEngineeringDesignLoopResult();
    ProcessSystem process = loop == null ? project.getEngineeringProcessSystem() : loop.getDesignedProcess();

    List<String> topologyFindings = topologyFindings(process, policy);
    gates.put("COMPLETE_PROCESS_AND_SAFETY_TOPOLOGY",
        gate(topologyFindings.isEmpty(), topologyFindings,
            "Add the separator, charted compressor, cooler, export line, recycle, PCV, LCV, PSV, BDV, ESDVs "
                + "and flare connection"));

    Set<EngineeringDesignCase.Type> presentTypes = EnumSet.noneOf(EngineeringDesignCase.Type.class);
    List<String> failedCases = new ArrayList<String>();
    if (simulation.getCaseRunReport() != null) {
      for (DesignCaseResult designCase : simulation.getCaseRunReport().getEnvelope().getCaseResults()) {
        presentTypes.add(designCase.getDesignCase().getType());
        if (!designCase.isConverged() || !"CALCULATED".equals(designCase.getStatus())) {
          failedCases.add(designCase.getDesignCase().getId() + ":" + designCase.getStatus());
        }
      }
    }
    Set<EngineeringDesignCase.Type> missingTypes = EnumSet.copyOf(policy.getRequiredCaseTypes());
    missingTypes.removeAll(presentTypes);
    List<String> caseFindings = new ArrayList<String>();
    if (!missingTypes.isEmpty()) {
      caseFindings.add("Missing case types " + missingTypes);
    }
    if (!failedCases.isEmpty()) {
      caseFindings.add("Non-converged or failed cases " + failedCases);
    }
    gates.put("COMPLETE_CONVERGED_CASE_MATRIX", gate(caseFindings.isEmpty(), caseFindings,
        "Run normal, turndown, maximum, startup, shutdown, trip, settle-out, blocked-outlet, fire and blowdown cases"));

    gates.put("CLOSED_DESIGN_LOOP",
        gate(loop != null && loop.isConverged(),
            singleton(loop == null ? "Engineering design loop was not executed" : loop.getTerminationReason()),
            "Converge physical design variables, process values and constraints"));

    List<String> missingVariables = missingDesignVariables(loop == null ? null : loop.getState(), policy);
    gates.put("COUPLED_PHYSICAL_DESIGN",
        gate(missingVariables.isEmpty(), missingVariables,
            "Size separator, compressor, cooler, piping, valves, relief, materials and mechanical design in the "
                + "common loop"));

    List<String> compressorFindings = compressorFindings(process, simulation, policy);
    gates.put("COMPRESSOR_MAP_AND_ANTI_SURGE", gate(compressorFindings.isEmpty(), compressorFindings,
        "Attach a vendor-supported map and verify surge, stonewall, extrapolation and recycle demand in every case"));

    List<String> dynamicFindings = dynamicFindings(simulation, policy);
    gates.put("DYNAMIC_SAFE_STATE_VERIFICATION", gate(dynamicFindings.isEmpty(), dynamicFindings,
        "Execute the required anti-surge, trip, isolation and depressurization scenarios against response deadlines"));

    List<String> reliefFindings = reliefFindings(simulation, policy);
    gates.put("COUPLED_RELIEF_BLOWDOWN_FLARE", gate(reliefFindings.isEmpty(), reliefFindings,
        "Couple credible PSV and blowdown loads to the flare network and close capacity constraints"));

    List<String> standardsFindings = standardsFindings(project, policy);
    gates.put("VERSIONED_STANDARDS_BASIS", gate(standardsFindings.isEmpty(), standardsFindings,
        "Register the controlled international and NORSOK project editions"));

    List<String> evidenceFindings = evidenceFindings(project, policy);
    gates.put("CONTROLLED_ENGINEERING_EVIDENCE", gate(evidenceFindings.isEmpty(), evidenceFindings,
        "Attach HAZOP, compressor map, cause-and-effect, relief, materials and calculation evidence"));

    boolean passed = true;
    for (Gate gate : gates.values()) {
      passed &= gate.passed;
    }
    return new Result(project.getProjectId(), project.getRevision(), policy, gates, passed);
  }

  private static List<String> topologyFindings(ProcessSystem process, InletCompressionExportSlicePolicy policy) {
    List<String> result = new ArrayList<String>();
    requireType(process, policy.getSeparatorTag(), Separator.class, result);
    requireType(process, policy.getCompressorTag(), Compressor.class, result);
    requireType(process, policy.getCoolerTag(), Cooler.class, result);
    ProcessEquipmentInterface line = process.getUnit(policy.getExportLineTag());
    if (line == null || !line.getClass().getSimpleName().toLowerCase().contains("pipe")) {
      result.add(policy.getExportLineTag() + " is missing or is not piping");
    }
    requireType(process, policy.getRecycleValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getRecycleUnitTag(), Recycle.class, result);
    requireType(process, policy.getPressureControlValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getLevelControlValveTag(), ThrottlingValve.class, result);
    requireType(process, policy.getReliefValveTag(), SafetyReliefValve.class, result);
    requireType(process, policy.getBlowdownValveTag(), BlowdownValve.class, result);
    requireType(process, policy.getSuctionEsdValveTag(), ESDValve.class, result);
    requireType(process, policy.getDischargeEsdValveTag(), ESDValve.class, result);
    if (process.getUnit(policy.getFlareConnectionTag()) == null) {
      result.add(policy.getFlareConnectionTag() + " flare connection is missing");
    }
    return result;
  }

  private static void requireType(ProcessSystem process, String tag, Class<?> type, List<String> findings) {
    Object value = process.getUnit(tag);
    if (!type.isInstance(value)) {
      findings.add(tag + " is missing or is not " + type.getSimpleName());
    }
  }

  private static List<String> missingDesignVariables(EngineeringDesignState state,
      InletCompressionExportSlicePolicy policy) {
    List<String> required = new ArrayList<String>();
    Collections.addAll(required, policy.getSeparatorTag() + ".insideDiameter",
        policy.getSeparatorTag() + ".tangentLength", policy.getSeparatorTag() + ".liquidRetentionTime",
        policy.getSeparatorTag() + ".designPressure", policy.getSeparatorTag() + ".proposedPsvSetPressure",
        policy.getSeparatorTag() + ".minimumDesignMetalTemperature",
        policy.getSeparatorTag() + ".preliminaryNominalWallThickness",
        policy.getCompressorTag() + ".driverRatedPower", policy.getCompressorTag() + ".minimumSurgeMargin",
        policy.getCompressorTag() + ".minimumStonewallMargin",
        policy.getCompressorTag() + ".maximumRequiredRecycleFraction",
        policy.getCompressorTag() + ".maximumRecycleCoolerDuty",
        policy.getCoolerTag() + ".preliminaryHeatTransferArea", policy.getExportLineTag() + ".insideDiameter",
        policy.getRecycleValveTag() + ".selectedCv", policy.getPressureControlValveTag() + ".selectedCv",
        policy.getLevelControlValveTag() + ".selectedCv", policy.getReliefValveTag() + ".selectedOrificeArea");
    List<String> missing = new ArrayList<String>();
    for (String variable : required) {
      if (state == null || !state.contains(variable)) {
        missing.add(variable);
      }
    }
    return missing;
  }

  private static List<String> compressorFindings(ProcessSystem process, EngineeringSimulationResult simulation,
      InletCompressionExportSlicePolicy policy) {
    List<String> result = new ArrayList<String>();
    ProcessEquipmentInterface unit = process.getUnit(policy.getCompressorTag());
    if (!(unit instanceof Compressor)) {
      result.add("Compressor is not available");
      return result;
    }
    Compressor compressor = (Compressor) unit;
    if (compressor.getCompressorChart() == null || !compressor.getCompressorChart().isUseCompressorChart()) {
      result.add("Compressor chart is not active");
      return result;
    }
    if (compressor.getCompressorChart().getSurgeCurve() == null
        || !compressor.getCompressorChart().getSurgeCurve().isActive()) {
      result.add("Surge curve is not active");
    }
    if (compressor.getCompressorChart().getStoneWallCurve() == null
        || !compressor.getCompressorChart().getStoneWallCurve().isActive()) {
      result.add("Stonewall curve is not active");
    }
    if (simulation.getCaseRunReport() != null) {
      String extrapolationMetric = policy.getCompressorTag() + ".chartExtrapolated";
      for (DesignCaseResult designCase : simulation.getCaseRunReport().getEnvelope().getCaseResults()) {
        Double extrapolated = designCase.getValues().get(extrapolationMetric);
        if (extrapolated == null) {
          result.add("Missing chart-extrapolation evidence for " + designCase.getDesignCase().getId());
        } else if (extrapolated.doubleValue() > 0.5) {
          result.add("Chart extrapolation in " + designCase.getDesignCase().getId());
        }
      }
    }
    return result;
  }

  private static List<String> dynamicFindings(EngineeringSimulationResult simulation,
      InletCompressionExportSlicePolicy policy) {
    Map<String, DynamicSafetyScenarioResult> results = new LinkedHashMap<String, DynamicSafetyScenarioResult>();
    for (DynamicSafetyScenarioResult dynamic : simulation.getDynamicScenarioResults()) {
      results.put(dynamic.getScenarioId(), dynamic);
    }
    List<String> findings = new ArrayList<String>();
    for (String required : policy.getRequiredDynamicScenarioIds()) {
      DynamicSafetyScenarioResult result = results.get(required);
      if (result == null) {
        findings.add("Missing dynamic scenario " + required);
      } else if (!result.isPassed()) {
        findings.add("Dynamic scenario failed " + required);
      }
    }
    return findings;
  }

  private static List<String> reliefFindings(EngineeringSimulationResult simulation,
      InletCompressionExportSlicePolicy policy) {
    List<String> findings = new ArrayList<String>();
    if (!policy.isCoupledReliefBlowdownFlareRequired()) {
      return findings;
    }
    if (simulation.getCoupledSafetyResults().isEmpty()) {
      findings.add("No coupled relief/blowdown/flare study was executed");
      return findings;
    }
    for (EngineeringCalculationResult<CoupledReliefBlowdownFlareResult> calculation : simulation
        .getCoupledSafetyResults()) {
      if (calculation.getValue() == null || !calculation.getValue().isCapacityAcceptable()) {
        findings.add("Coupled safety calculation is blocked, incomplete or over capacity");
      }
    }
    return findings;
  }

  private static List<String> standardsFindings(EngineeringProject project,
      InletCompressionExportSlicePolicy policy) {
    Set<String> present = new LinkedHashSet<String>();
    for (EngineeringStandard standard : project.getDesignBasis().getStandards()) {
      present.add(standard.getCode());
    }
    List<String> missing = new ArrayList<String>();
    for (String required : policy.getRequiredStandards()) {
      boolean found = false;
      for (String available : present) {
        found |= available.equals(required) || available.startsWith(required + " ");
      }
      if (!found) {
        missing.add(required);
      }
    }
    return missing;
  }

  private static List<String> evidenceFindings(EngineeringProject project,
      InletCompressionExportSlicePolicy policy) {
    List<String> findings = new ArrayList<String>();
    if (policy.getEvidenceReferences().isEmpty()) {
      findings.add("No controlled evidence references were required by the policy");
      return findings;
    }
    for (String required : policy.getEvidenceReferences()) {
      EngineeringEvidenceRecord matched = null;
      for (EngineeringEvidenceRecord evidence : project.getEvidenceRecords()) {
        if (required.equals(evidence.getDocumentId())
            || required.equals(evidence.getDocumentId() + "@" + evidence.getRevision())) {
          matched = evidence;
          break;
        }
      }
      if (matched == null) {
        findings.add("Missing controlled evidence record " + required);
      } else if (!matched.getMissingFields().isEmpty()) {
        findings.add("Incomplete controlled evidence record " + required + ": " + matched.getMissingFields());
      }
    }
    return findings;
  }

  private static Gate gate(boolean passed, List<String> findings, String requiredAction) {
    return new Gate(passed, findings, passed ? "NONE" : requiredAction);
  }

  private static List<String> singleton(String value) {
    List<String> result = new ArrayList<String>();
    if (value != null && !value.trim().isEmpty()) {
      result.add(value);
    }
    return result;
  }

  private static final class Gate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final boolean passed;
    private final List<String> findings;
    private final String requiredAction;

    Gate(boolean passed, List<String> findings, String requiredAction) {
      this.passed = passed;
      this.findings = Collections.unmodifiableList(new ArrayList<String>(findings));
      this.requiredAction = requiredAction;
    }

    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("passed", Boolean.valueOf(passed));
      result.put("findings", findings);
      result.put("requiredAction", requiredAction);
      return result;
    }
  }

  /** Immutable vertical-slice qualification record. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String projectId;
    private final String projectRevision;
    private final InletCompressionExportSlicePolicy policy;
    private final Map<String, Gate> gates;
    private final boolean qualifiedForControlledPilot;

    Result(String projectId, String projectRevision, InletCompressionExportSlicePolicy policy,
        Map<String, Gate> gates, boolean qualifiedForControlledPilot) {
      this.projectId = projectId;
      this.projectRevision = projectRevision;
      this.policy = policy;
      this.gates = Collections.unmodifiableMap(new LinkedHashMap<String, Gate>(gates));
      this.qualifiedForControlledPilot = qualifiedForControlledPilot;
    }

    public boolean isQualifiedForControlledPilot() {
      return qualifiedForControlledPilot;
    }

    public List<String> getFailedGates() {
      List<String> result = new ArrayList<String>();
      for (Map.Entry<String, Gate> gate : gates.entrySet()) {
        if (!gate.getValue().passed) {
          result.add(gate.getKey());
        }
      }
      return result;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("schemaVersion", EngineeringSchemaCatalog.VERTICAL_SLICE_QUALIFICATION);
      result.put("schemaUri",
          EngineeringSchemaCatalog.schemaUri(EngineeringSchemaCatalog.VERTICAL_SLICE_QUALIFICATION));
      result.put("projectId", projectId);
      result.put("projectRevision", projectRevision);
      result.put("policyId", policy.getPolicyId());
      result.put("policyRevision", policy.getRevision());
      Map<String, Object> gateMaps = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Gate> gate : gates.entrySet()) {
        gateMaps.put(gate.getKey(), gate.getValue().toMap());
      }
      result.put("gates", gateMaps);
      result.put("failedGates", getFailedGates());
      result.put("qualifiedForControlledPilot", Boolean.valueOf(qualifiedForControlledPilot));
      result.put("qualifiedForFeedSupport", Boolean.FALSE);
      result.put("fitnessForConstruction", Boolean.FALSE);
      result.put("finalEngineeringApprovalGranted", Boolean.FALSE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      result.put("governance", "Passing the simulator gates qualifies a controlled pilot only; accountable "
          + "engineering approval remains required");
      return result;
    }
  }
}
