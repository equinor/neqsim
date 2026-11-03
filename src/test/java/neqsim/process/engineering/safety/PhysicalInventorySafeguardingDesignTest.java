package neqsim.process.engineering.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.EngineeringRequirement;
import neqsim.process.engineering.pid.PidDesignModel;
import neqsim.process.engineering.pid.PidElement;
import neqsim.process.engineering.pid.PidElementType;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.Blocker;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.DesignPolicy;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.SectionOperatingBasis;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.SectionProposal;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StandardApplicability;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StandardEvidenceStatus;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StudyInput;
import neqsim.process.engineering.safety.PhysicalInventorySafeguardingDesign.StudyResult;

/** Quantitative and fail-closed tests for the DYN-C029 to safeguarding-proposal bridge. */
class PhysicalInventorySafeguardingDesignTest {
  private static final double TOLERANCE = 1.0e-10;

  /** Verifies the independent geometry, mass, range, margin and response-budget calculations. */
  @Test
  void calculatesTraceableProposalFromControlledPhysicalInventory() {
    StudyResult result = run(completeInventory(), standardInput(policy(new double[] {50.0, 75.0, 100.0})));

    assertTrue(result.isReadyForDisciplineReview(), result.toJson());
    assertTrue(result.getBlockers().isEmpty());
    assertEquals(1, result.getProposals().size());
    SectionProposal proposal = result.getProposals().get(0);
    double expectedVolume = 17.0 * Math.PI + 10.0;
    assertEquals(expectedVolume, proposal.getIsolatedVolumeM3(), TOLERANCE);
    assertEquals(expectedVolume * 700.0, proposal.getInventoryMassKg(), TOLERANCE);
    assertEquals(expectedVolume * 10.0, proposal.getInventoryTurnoverSeconds(), TOLERANCE);
    assertEquals(75.0, proposal.getPressureRangeUpperBara(), TOLERANCE);
    assertEquals(51.0, proposal.getHighAlarmBara(), TOLERANCE);
    assertEquals(56.25, proposal.getHighHighTripBara(), TOLERANCE);
    assertEquals(3.75, proposal.getTripToDesignMarginBar(), TOLERANCE);
    assertEquals(2.0, proposal.getSensorResponseRequirementSeconds(), TOLERANCE);
    assertEquals(1.0, proposal.getLogicResponseRequirementSeconds(), TOLERANCE);
    assertEquals(5.0, proposal.getFinalElementResponseRequirementSeconds(), TOLERANCE);
    assertEquals(2.0, proposal.getVerificationReserveSeconds(), TOLERANCE);
    assertEquals(0.0, proposal.getResponseBudgetResidualSeconds(), TOLERANCE);
    assertEquals("CLOSED", proposal.getIsolationActions().get(0).getCandidateAction());
    assertEquals("OPEN", proposal.getIsolationActions().get(0).getAlternativeAction());
    assertEquals("OPEN", proposal.getIsolationActions().get(1).getCandidateAction());
    assertEquals("CLOSED", proposal.getIsolationActions().get(1).getAlternativeAction());
    assertTrue(result.toJson().contains("\"scenarioExecutionStatus\": \"NOT_EXECUTED\""));
    assertTrue(result.toJson().contains("\"safetyCredit\": false"));
    assertEquals(64, result.getInputFingerprint().length());
  }

  /** Verifies stable ordering, tags, trace links and existing P&amp;ID contract materialization. */
  @Test
  void materializesCoordinatedPidAndEngineeringRequirements() {
    StudyResult result = run(completeInventory(), standardInput(policy(new double[] {50.0, 75.0, 100.0})));
    PidDesignModel model = PhysicalInventorySafeguardingPidBridge.materialize(result, "ONSHORE-PROPOSAL");

    assertEquals(6, model.getElements().size());
    assertEquals(1, model.getElementsByType(PidElementType.MEASUREMENT).size());
    assertEquals(1, model.getElementsByType(PidElementType.CONTROLLER).size());
    assertEquals(1, model.getElementsByType(PidElementType.ALARM).size());
    assertEquals(1, model.getElementsByType(PidElementType.TRIP).size());
    assertEquals(2, model.getElementsByType(PidElementType.SHUTDOWN_VALVE).size());

    PidElement transmitter = byTag(model, "PT-SECTION-001");
    PidElement trip = byTag(model, "PSHH-SECTION-001");
    assertEquals(75.0, ((Number) transmitter.getAttributes().get("rangeUpperBara")).doubleValue(), TOLERANCE);
    assertEquals(56.25, ((Number) trip.getAttributes().get("setPointBara")).doubleValue(), TOLERANCE);
    assertEquals("NOT_EXECUTED", trip.getAttributes().get("scenarioExecutionStatus"));
    assertTrue(transmitter.getConnectedElementIds().contains(trip.getId()));
    assertTrue(trip.getConnectedElementIds().contains(byTag(model, "ESDV-101").getId()));
    assertTrue(trip.getConnectedElementIds().contains(byTag(model, "ESDV-102").getId()));
    assertTrue(model.toMap().toString().contains("CATALOG_METADATA_ONLY"));

    List<EngineeringRequirement> requirements = PhysicalInventorySafeguardingPidBridge.requirements(result);
    assertEquals(4, requirements.size());
    assertEquals(EngineeringRequirement.Type.INSTRUMENT, requirements.get(0).getType());
    assertEquals(EngineeringRequirement.Type.CONTROL, requirements.get(1).getType());
    assertEquals(EngineeringRequirement.Type.ALARM, requirements.get(2).getType());
    assertEquals(EngineeringRequirement.Type.TRIP, requirements.get(3).getType());
    assertEquals("SIL_UNASSIGNED", requirements.get(3).getSilTarget());
  }

  /** Verifies repeated execution is deterministic and revisions alter the invalidation fingerprint. */
  @Test
  void isDeterministicAndRevisionSensitive() {
    StudyInput firstInput = standardInput(policy(new double[] {50.0, 75.0, 100.0}));
    StudyResult first = run(completeInventory(), firstInput);
    StudyResult repeated = run(completeInventory(), firstInput);
    assertEquals(first.toJson(), repeated.toJson());

    StudyInput changed = input("B", policy(new double[] {50.0, 75.0, 100.0}));
    StudyResult changedResult = run(completeInventory(), changed);
    assertFalse(first.getInputFingerprint().equals(changedResult.getInputFingerprint()));

    String changedPhysicalSource = completeInventory().replace("\"normal_state\": \"OPEN\"",
        "\"normal_state\": \"CLOSED\"");
    StudyResult changedPhysicalResult = run(changedPhysicalSource, firstInput);
    assertFalse(first.getInputFingerprint().equals(changedPhysicalResult.getInputFingerprint()));
  }

  /** Verifies incomplete upstream physical evidence cannot yield a partial proposal. */
  @Test
  void blocksIncompletePhysicalInventory() {
    String incomplete = completeInventory().replace("\"status\": \"COMPLETE\"", "\"status\": \"INCOMPLETE\"")
        .replace("\"ready_for_dynamic_safeguarding_design\": true", "\"ready_for_dynamic_safeguarding_design\": false");
    StudyResult result = run(incomplete, standardInput(policy(new double[] {50.0, 75.0, 100.0})));

    assertFalse(result.isReadyForDisciplineReview());
    assertTrue(result.getProposals().isEmpty());
    assertTrue(hasBlocker(result, "SAFEGUARD-INVENTORY-NOT-READY"));
    assertThrows(IllegalArgumentException.class,
        () -> PhysicalInventorySafeguardingPidBridge.materialize(result, "ONSHORE-PROPOSAL"));
  }

  /** Verifies missing or unknown physical fail actions remain explicit blockers. */
  @Test
  void blocksUnknownPhysicalFailAction() {
    String unknown = completeInventory().replace("\"fail_state\": \"CLOSED\"", "\"fail_state\": \"UNKNOWN\"");
    StudyResult result = run(unknown, standardInput(policy(new double[] {50.0, 75.0, 100.0})));

    assertFalse(result.isReadyForDisciplineReview());
    assertTrue(result.getProposals().isEmpty());
    assertTrue(hasBlocker(result, "SAFEGUARD-FAIL-STATE-MISSING"));
  }

  /** Verifies an uncovered range remains a blocker and is never silently clipped. */
  @Test
  void blocksWhenNoDeclaredRangeCoversDesignEnvelope() {
    StudyResult result = run(completeInventory(), standardInput(policy(new double[] {40.0, 50.0, 60.0})));

    assertFalse(result.isReadyForDisciplineReview());
    assertTrue(result.getProposals().isEmpty());
    assertTrue(hasBlocker(result, "SAFEGUARD-PROPOSAL-CALCULATION-BLOCKED"));
  }

  /** Verifies a proposed measurement cannot be silently placed outside the isolated inventory. */
  @Test
  void blocksMeasurementLocationOutsidePhysicalSection() {
    SectionOperatingBasis outside = new SectionOperatingBasis("SECTION-001", "V-999", "CASE-BLOCKED-OUTLET", "A",
        "Dynamic operating envelope report DYN-001", 40.0, 45.0, 60.0, 700.0, 70.0, 10.0);
    StudyInput input = new StudyInput("PROJECT-001", "A", policy(new double[] {50.0, 75.0, 100.0}),
        Arrays.asList(outside), standards());
    StudyResult result = run(completeInventory(), input);

    assertFalse(result.isReadyForDisciplineReview());
    assertTrue(result.getProposals().isEmpty());
    assertTrue(hasBlocker(result, "SAFEGUARD-MEASUREMENT-LOCATION-OUTSIDE-SECTION"));
  }

  /** Verifies response allocations and alarm/trip ordering must be explicit and coherent. */
  @Test
  void rejectsHiddenOrIncoherentNumericalPolicy() {
    assertThrows(IllegalArgumentException.class,
        () -> new DesignPolicy("POLICY", "A", "DB-001", 0.1, 0.8, 0.5, 0.2, 0.1, 0.5, 0.2, new double[] {50.0, 75.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new DesignPolicy("POLICY", "A", "DB-001", 0.1, 0.4, 0.75, 0.2, 0.1, 0.5, 0.1, new double[] {50.0, 75.0}));
    assertThrows(IllegalArgumentException.class,
        () -> new DesignPolicy("POLICY", "A", "DB-001", 0.1, 0.4, 0.75, 0.2, 0.1, 0.5, 0.2, new double[] {75.0, 50.0}));
  }

  /**
   * Runs one in-memory study.
   *
   * @param inventory physical inventory JSON
   * @param input controlled study input
   * @return study result
   */
  private static StudyResult run(String inventory, StudyInput input) {
    return PhysicalInventorySafeguardingDesign.run(new StringReader(inventory), input);
  }

  /**
   * Returns the standard test policy.
   *
   * @param ranges pressure range candidates in bara
   * @return explicit proposal policy
   */
  private static DesignPolicy policy(double[] ranges) {
    return new DesignPolicy("PID-SAFEGUARD-POLICY", "A", "Project control and safeguarding design basis DB-001", 0.10,
        0.40, 0.75, 0.20, 0.10, 0.50, 0.20, ranges);
  }

  /**
   * Returns a controlled study input at revision A.
   *
   * @param policy explicit proposal policy
   * @return study input
   */
  private static StudyInput standardInput(DesignPolicy policy) {
    return input("A", policy);
  }

  /**
   * Returns a controlled study input.
   *
   * @param revision project revision
   * @param policy explicit proposal policy
   * @return study input
   */
  private static StudyInput input(String revision, DesignPolicy policy) {
    SectionOperatingBasis basis = new SectionOperatingBasis("SECTION-001", "V-101", "CASE-BLOCKED-OUTLET", "A",
        "Dynamic operating envelope report DYN-001", 40.0, 45.0, 60.0, 700.0, 70.0, 10.0);
    return new StudyInput("PROJECT-001", revision, policy, Arrays.asList(basis), standards());
  }

  /**
   * Returns the standards applicability/evidence test register.
   *
   * @return standards register rows
   */
  private static List<StandardApplicability> standards() {
    return Arrays.asList(
        new StandardApplicability("ANSI/ISA-5.1", "2024", "Diagram identification and tag review", "NOT_APPLIED",
            "Public catalog metadata; project adoption pending", StandardEvidenceStatus.CATALOG_METADATA_ONLY),
        new StandardApplicability("IEC 61511", "PROJECT_ADOPTED_EDITION_REQUIRED",
            "SIS lifecycle review; no SIL or voting inferred", "NOT_APPLIED",
            "Licensed project standard and risk assessment required",
            StandardEvidenceStatus.DETAILED_REQUIREMENTS_REVIEW_REQUIRED),
        new StandardApplicability("IEC 62682", "PROJECT_ADOPTED_EDITION_REQUIRED",
            "Alarm philosophy and rationalization review", "NOT_APPLIED",
            "Licensed project standard and alarm philosophy required",
            StandardEvidenceStatus.DETAILED_REQUIREMENTS_REVIEW_REQUIRED));
  }

  /**
   * Finds one generated P&amp;ID element by tag.
   *
   * @param model proposal model
   * @param tag requested tag
   * @return matching element
   */
  private static PidElement byTag(PidDesignModel model, String tag) {
    for (PidElement element : model.getElements()) {
      if (tag.equals(element.getTag())) {
        return element;
      }
    }
    throw new AssertionError("No P&ID element with tag " + tag);
  }

  /**
   * Tests whether a result contains a blocker code.
   *
   * @param result study result
   * @param code requested blocker code
   * @return true when present
   */
  private static boolean hasBlocker(StudyResult result, String code) {
    for (Blocker blocker : result.getBlockers()) {
      if (code.equals(blocker.getCode())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a complete controlled DYN-C029 physical inventory with analytically known volume.
   *
   * @return physical-inventory JSON
   */
  private static String completeInventory() {
    return "{\n" + "  \"schema_version\": \"plant_layout_physical_inventory.v1\",\n" + "  \"status\": \"COMPLETE\",\n"
        + "  \"ready_for_dynamic_safeguarding_design\": true,\n"
        + "  \"scope\": {\"id\": \"INV-SCOPE-001\", \"revision\": \"A\", "
        + "\"source_reference\": \"Controlled layout report LAY-001\"},\n"
        + "  \"routes\": [{\"id\": \"ROUTE-001\", \"connection_id\": \"C-001\", "
        + "\"line_tag\": \"L-101\", \"length_m\": 17.0, \"pipe_volume_m3\": " + (17.0 * Math.PI)
        + ", \"source\": {\"reference\": \"Piping model PM-001\", \"revision\": \"A\", "
        + "\"evidence_status\": \"CONTROLLED_PROJECT_INPUT\"}}],\n"
        + "  \"equipment_volumes\": [{\"id\": \"VOL-001\", \"equipment_tag\": \"V-101\", "
        + "\"volume_m3\": 10.0, \"source\": {\"reference\": \"Vessel datasheet DS-001\", "
        + "\"revision\": \"A\", \"evidence_status\": \"CONTROLLED_PROJECT_INPUT\"}}],\n"
        + "  \"isolation_devices\": [\n" + "    {\"id\": \"DEV-001\", \"tag\": \"ESDV-101\", "
        + "\"device_type\": \"emergency_shutdown_valve\", \"normal_state\": \"OPEN\", " + "\"fail_state\": \"CLOSED\", "
        + "\"source\": {\"reference\": \"Valve register VR-001\", \"revision\": \"A\", "
        + "\"evidence_status\": \"CONTROLLED_PROJECT_INPUT\"}},\n" + "    {\"id\": \"DEV-002\", \"tag\": \"ESDV-102\", "
        + "\"device_type\": \"process_shutdown_valve\", \"fail_state\": \"OPEN\", "
        + "\"source\": {\"reference\": \"Valve register VR-001\", \"revision\": \"A\", "
        + "\"evidence_status\": \"CONTROLLED_PROJECT_INPUT\"}}\n" + "  ],\n" + "  \"isolation_sections\": [\n"
        + "    {\"id\": \"SECTION-001\", \"route_ids\": [\"ROUTE-001\"], " + "\"equipment_volume_ids\": [\"VOL-001\"], "
        + "\"boundary_device_ids\": [\"DEV-001\", \"DEV-002\"], " + "\"pipe_volume_m3\": " + (17.0 * Math.PI)
        + ", \"equipment_volume_m3\": 10.0, " + "\"total_isolated_volume_m3\": " + (17.0 * Math.PI + 10.0) + ", "
        + "\"source\": {\"reference\": \"Isolation register IR-001\", \"revision\": \"A\", "
        + "\"evidence_status\": \"CONTROLLED_PROJECT_INPUT\"}}\n" + "  ],\n" + "  \"blockers\": [],\n"
        + "  \"totals\": {\"route_length_m\": 17.0, \"pipe_volume_m3\": " + (17.0 * Math.PI)
        + ", \"equipment_volume_m3\": 10.0}\n" + "}";
  }
}
