package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.deliverables.EngineeringApprovalLedger;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringGraphDiff;
import neqsim.process.engineering.model.EngineeringGraphBuilder;
import neqsim.process.engineering.model.EngineeringIds;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.scenario.DynamicSafetyScenario;
import neqsim.process.safety.scenario.DynamicScenarioCriterion;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/** Qualifies scenario traceability and revision-controlled execution evidence. */
class EngineeringDynamicScenarioEvidenceGraphTest {
  private static final String PROJECT_ID = "dynamic-evidence-project";
  private static final String SCENARIO_ID = "SIF-HP-101";
  private static final String REQUIREMENT_ID = "REQ-HP-101";
  private static final String EVIDENCE_ID = "SRS-HP-101";

  @Test
  void validatesRequirementAndTagReferencesAgainstCanonicalProjectIdentities() {
    EngineeringProject project = project("A", 5.0);
    EngineeringValidationReport report = project.validate();

    assertFalse(report.hasErrors());
    assertFalse(hasCode(report, "ENG-DYNAMIC-003"));
    assertFalse(hasCode(report, "ENG-DYNAMIC-004"));
    assertFalse(hasCode(report, "ENG-DYNAMIC-006"));

    EngineeringProject invalid = new EngineeringProject(PROJECT_ID, "Invalid traceability", process(),
        new EngineeringDesignBasis());
    invalid.addDynamicSafetyScenario(scenario(5.0, "UNKNOWN-REQ", "UNKNOWN-TAG"));

    EngineeringValidationReport invalidReport = invalid.validate();
    assertTrue(invalidReport.hasErrors());
    assertTrue(hasCode(invalidReport, "ENG-DYNAMIC-003"));
    assertTrue(hasCode(invalidReport, "ENG-DYNAMIC-004"));
  }

  @Test
  void resultChangeInvalidatesOnlyTheAffectedScenarioResultApproval() {
    EngineeringProject revisionA = project("A", 5.0);
    addApprovals(revisionA);
    EngineeringSimulationResult resultA = EngineeringSimulationRunner.run(revisionA, null);
    EngineeringGraph graphA = EngineeringGraphBuilder.fromProject(revisionA);

    String scenarioNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO, SCENARIO_ID);
    String resultNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO_RESULT, SCENARIO_ID);
    assertNotNull(revisionA.getLatestEngineeringSimulationResult());
    assertNotNull(graphA.getNode(scenarioNodeId));
    assertNotNull(graphA.getNode(resultNodeId));
    assertTrue(resultA.getDynamicScenarioResults().get(0).isPassed());

    EngineeringProject revisionB = project("B", 4.0);
    addApprovals(revisionB);
    EngineeringSimulationRunner.run(revisionB, null);
    EngineeringGraph graphB = EngineeringGraphBuilder.fromProject(revisionB);
    EngineeringGraphDiff diff = graphA.compareTo(graphB);

    assertFalse(diff.getModifiedNodeIds().contains(scenarioNodeId));
    assertTrue(diff.getModifiedNodeIds().contains(resultNodeId));

    Map<String, Object> ledger = EngineeringApprovalLedger.build(revisionB, graphB, diff);
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) ledger.get("summary");
    assertEquals(Integer.valueOf(1), summary.get("approvedCount"));
    assertEquals(Integer.valueOf(1), summary.get("revalidationRequiredCount"));
  }

  @Test
  void definitionChangePropagatesRevalidationToGeneratedResult() {
    EngineeringProject revisionA = project("A", 5.0, 2.0);
    addApprovals(revisionA);
    EngineeringSimulationRunner.run(revisionA, null);
    EngineeringGraph graphA = EngineeringGraphBuilder.fromProject(revisionA);

    EngineeringProject revisionB = project("B", 5.0, 3.0);
    addApprovals(revisionB);
    EngineeringSimulationRunner.run(revisionB, null);
    EngineeringGraph graphB = EngineeringGraphBuilder.fromProject(revisionB);
    EngineeringGraphDiff diff = graphA.compareTo(graphB);

    String scenarioNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO, SCENARIO_ID);
    String resultNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO_RESULT, SCENARIO_ID);
    assertTrue(diff.getModifiedNodeIds().contains(scenarioNodeId));
    assertTrue(diff.getImpactedNodeIds().contains(resultNodeId));

    Map<String, Object> ledger = EngineeringApprovalLedger.build(revisionB, graphB, diff);
    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) ledger.get("summary");
    assertEquals(Integer.valueOf(2), summary.get("revalidationRequiredCount"));
  }

  private static EngineeringProject project(String revision, double safePressureBara) {
    return project(revision, safePressureBara, 2.0);
  }

  private static EngineeringProject project(String revision, double safePressureBara, double durationSeconds) {
    EngineeringProject project = new EngineeringProject(PROJECT_ID, "Dynamic evidence", process(),
        new EngineeringDesignBasis()).setRevision(revision);
    project.addRequirement(new EngineeringRequirement(REQUIREMENT_ID, "FEED", EngineeringRequirement.Type.TRIP,
        "Isolate high pressure", "Protect the downstream pressure envelope"));
    project.addEvidenceRecord(new EngineeringEvidenceRecord(EVIDENCE_ID, "SRS", "A")
        .setTitle("High-pressure shutdown requirement").setSourceOrganization("Synthetic qualification")
        .linkRequirement(REQUIREMENT_ID).linkEquipment("FEED"));
    project.addDynamicSafetyScenario(scenario(safePressureBara, REQUIREMENT_ID, "FEED", durationSeconds));
    return project;
  }

  private static void addApprovals(EngineeringProject project) {
    String scenarioNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO, SCENARIO_ID);
    String resultNodeId = EngineeringIds.nodeId(EngineeringNode.Kind.SCENARIO_RESULT, SCENARIO_ID);
    project.addApprovalRecord(new EngineeringApprovalRecord("APR-SCENARIO", scenarioNodeId, "PROCESS_SAFETY",
        EngineeringApprovalRecord.Status.APPROVED, "Synthetic reviewer", "TEST-REVIEW-A", "2026-10-31"));
    project.addApprovalRecord(new EngineeringApprovalRecord("APR-RESULT", resultNodeId, "PROCESS_SAFETY",
        EngineeringApprovalRecord.Status.APPROVED, "Synthetic reviewer", "TEST-REVIEW-A", "2026-10-31"));
  }

  private static DynamicSafetyScenario scenario(final double safePressureBara, String requirementId,
      String equipmentTag) {
    return scenario(safePressureBara, requirementId, equipmentTag, 2.0);
  }

  private static DynamicSafetyScenario scenario(final double safePressureBara, String requirementId,
      String equipmentTag, double durationSeconds) {
    return DynamicSafetyScenario.builder(SCENARIO_ID, "High-pressure isolation").durationSeconds(durationSeconds)
        .timeStepSeconds(1.0).triggerTimeSeconds(0.0).eventExactTiming(true)
        .initiatingEvent("feed-pressure-step-v1", new DynamicSafetyScenario.ProcessManipulator() {
          private static final long serialVersionUID = 1000L;

          @Override
          public void apply(ProcessSystem process) {
            ((Stream) process.getUnit("FEED")).setPressure(60.0, "bara");
          }
        }).addLogic("pressure-trip-v1", new DynamicSafetyScenario.LogicFactory() {
          private static final long serialVersionUID = 1000L;

          @Override
          public ProcessLogic create(ProcessSystem process) {
            return new PressureTripLogic((Stream) process.getUnit("FEED"), safePressureBara);
          }
        }).addCriterion(DynamicScenarioCriterion
            .builder("safe-pressure", "Safe pressure", "bara", "feed-pressure-bara-v1",
                new DynamicScenarioCriterion.Extractor() {
              private static final long serialVersionUID = 1000L;

              @Override
              public double extract(ProcessSystem process) {
                return ((Stream) process.getUnit("FEED")).getPressure("bara");
              }
            }).acceptanceRange(null, Double.valueOf(10.0)).deadlineSeconds(1.0).build())
        .addEvidenceReference(EVIDENCE_ID + "@A").addRequirementId(requirementId).addEquipmentTag(equipmentTag)
        .build();
  }

  private static ProcessSystem process() {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("FEED", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.run();
    return process;
  }

  private static boolean hasCode(EngineeringValidationReport report, String code) {
    for (EngineeringValidationReport.Finding finding : report.getFindings()) {
      if (code.equals(finding.getCode())) {
        return true;
      }
    }
    return false;
  }

  private static final class PressureTripLogic implements ProcessLogic {
    private static final long serialVersionUID = 1000L;
    private final Stream stream;
    private final double safePressureBara;
    private LogicState state = LogicState.IDLE;

    private PressureTripLogic(Stream stream, double safePressureBara) {
      this.stream = stream;
      this.safePressureBara = safePressureBara;
    }

    @Override
    public String getName() {
      return "Pressure trip";
    }

    @Override
    public LogicState getState() {
      return state;
    }

    @Override
    public void activate() {
      state = LogicState.RUNNING;
    }

    @Override
    public void deactivate() {
      state = LogicState.PAUSED;
    }

    @Override
    public boolean reset() {
      state = LogicState.IDLE;
      return true;
    }

    @Override
    public void execute(double timeStep) {
      stream.setPressure(safePressureBara, "bara");
      state = LogicState.COMPLETED;
    }

    @Override
    public boolean isActive() {
      return state == LogicState.RUNNING;
    }

    @Override
    public boolean isComplete() {
      return state == LogicState.COMPLETED;
    }

    @Override
    public List<ProcessEquipmentInterface> getTargetEquipment() {
      return new ArrayList<ProcessEquipmentInterface>();
    }

    @Override
    public String getStatusDescription() {
      return state.name();
    }
  }
}
