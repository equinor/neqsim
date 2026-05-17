package neqsim.process.safety.barrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.risk.bowtie.BowTieModel;

/**
 * Unit tests for safety barrier register objects.
 *
 * @author ESOL
 * @version 1.0
 */
class BarrierRegisterTest {

  /**
   * Verifies document evidence traceability and JSON export.
   */
  @Test
  void testDocumentEvidenceTraceability() {
    DocumentEvidence evidence = new DocumentEvidence("E-001", "SRS-001", "Safety Requirement", "A",
        "4.2", 12, "step1_scope_and_research/references/srs.pdf",
        "HIPPS shall close SDV-101 within 2 seconds on PAHH.", 0.92);

    assertTrue(evidence.isTraceable());
    assertEquals(0.92, evidence.getConfidence(), 1.0e-12);
    assertTrue(evidence.toJson().contains("SRS-001"));
  }

  /**
   * Verifies that a complete performance standard has no validation findings.
   */
  @Test
  void testPerformanceStandardValidation() {
    PerformanceStandard standard = createHippsStandard();

    assertTrue(standard.validate().isEmpty());
    assertTrue(standard.hasTraceableEvidence());
    assertEquals(PerformanceStandard.DemandMode.LOW_DEMAND, standard.getDemandMode());
  }

  /**
   * Verifies barrier validation, linked equipment, and RRF calculation.
   */
  @Test
  void testSafetyBarrierValidationAndRrf() {
    SafetyBarrier barrier = createHippsBarrier();

    assertTrue(barrier.validate().isEmpty());
    assertEquals(1000.0, barrier.getRiskReductionFactor(), 1.0e-12);
    assertEquals(1, barrier.getLinkedEquipmentTags().size());
  }

  /**
   * Verifies conversion from the existing bow-tie barrier model.
   */
  @Test
  void testCreateBarrierFromBowTieBarrier() {
    BowTieModel.Barrier bowTieBarrier = new BowTieModel.Barrier("B-PSV", "PSV protection", 0.01);
    bowTieBarrier.setBarrierType(BowTieModel.BarrierType.MITIGATION);
    bowTieBarrier.setFunctional(false);
    bowTieBarrier.setOwner("Technical safety");
    bowTieBarrier.setVerificationStatus("Verified against PSV datasheet DS-100.");

    SafetyBarrier barrier = SafetyBarrier.fromBowTieBarrier(bowTieBarrier);

    assertEquals(SafetyBarrier.BarrierType.MITIGATION, barrier.getType());
    assertEquals(SafetyBarrier.BarrierStatus.OUT_OF_SERVICE, barrier.getStatus());
    assertEquals("Technical safety", barrier.getOwner());
    assertTrue(barrier.hasTraceableEvidence());
  }

  /**
   * Verifies SCE grouping and impaired-barrier detection.
   */
  @Test
  void testSafetyCriticalElementGroupsBarriers() {
    SafetyCriticalElement element = new SafetyCriticalElement("SCE-HIPPS-001").setTag("HIPPS-001")
        .setName("HIPPS for HP separator")
        .setType(SafetyCriticalElement.ElementType.INSTRUMENTED_FUNCTION).setOwner("Instrument")
        .addEquipmentTag("V-101").addBarrier(createHippsBarrier());

    assertEquals(1, element.getAvailableBarrierCount());
    assertFalse(element.hasImpairedBarrier());
    assertTrue(element.validate().isEmpty());
  }

  /**
   * Verifies register linking, equipment lookup, impairment reporting, and JSON export.
   */
  @Test
  void testBarrierRegisterLinksAndExports() {
    SafetyBarrier hipps = createHippsBarrier();
    SafetyBarrier deluge = new SafetyBarrier("B-DELUGE-001").setName("Deluge system")
        .setDescription("Mitigate jet fire exposure").setType(SafetyBarrier.BarrierType.MITIGATION)
        .setStatus(SafetyBarrier.BarrierStatus.IMPAIRED).setEffectiveness(0.8)
        .setSafetyFunction("Reduce fire escalation probability").setOwner("Technical safety")
        .addEquipmentTag("V-101").addHazardId("LOC-V-101").addEvidence(createEvidence());
    deluge.setPerformanceStandard(createHippsStandard().setTitle("Deluge response standard"));

    SafetyCriticalElement element = new SafetyCriticalElement("SCE-V-101").setTag("V-101")
        .setName("HP separator safety functions")
        .setType(SafetyCriticalElement.ElementType.PROCESS_EQUIPMENT).addEquipmentTag("V-101");

    BarrierRegister register = new BarrierRegister("BR-001").setName("Process safety register")
        .addSafetyCriticalElement(element).addBarrier(hipps).addBarrier(deluge)
        .addPerformanceStandard(createHippsStandard()).addEvidence(createEvidence());

    assertTrue(register.linkBarrierToSafetyCriticalElement("B-HIPPS-001", "SCE-V-101"));
    assertTrue(register.linkBarrierToSafetyCriticalElement("B-DELUGE-001", "SCE-V-101"));

    assertEquals(2, register.getBarriersForEquipment("V-101").size());
    assertEquals(1, register.getImpairedBarriers().size());
    assertNotNull(register.getSafetyCriticalElement("SCE-V-101"));
    assertTrue(register.toJson().contains("Process safety register"));
  }

  /**
   * Verifies that the TR2237 template creates starter performance standards and mappings.
   */
  @Test
  void testTR2237OnshoreTemplateLoadsPerformanceStandards() {
    BarrierRegister register = TR2237Templates.createOnshoreTemplate();

    assertTrue(register.getPerformanceStandards().size() >= 10);
    assertNotNull(register.getPerformanceStandard("PS-05"));
    assertTrue(TR2237Templates.createNorsokS001Mapping().containsKey("PS-05"));
    assertTrue(register.toJson().contains("TR2237 onshore"));
  }

  /**
   * Creates reusable traceable evidence for tests.
   *
   * @return document evidence
   */
  private DocumentEvidence createEvidence() {
    return new DocumentEvidence("E-HIPPS-001", "SRS-HIPPS", "HIPPS SRS", "B", "5.1", 8,
        "references/hipps_srs.pdf", "HIPPS shall isolate feed to V-101 on PAHH.", 0.95);
  }

  /**
   * Creates a reusable HIPPS performance standard for tests.
   *
   * @return performance standard
   */
  private PerformanceStandard createHippsStandard() {
    return new PerformanceStandard("PS-HIPPS-001").setTitle("HIPPS performance standard")
        .setSafetyFunction("Isolate feed to prevent HP separator overpressure")
        .setDemandMode(PerformanceStandard.DemandMode.LOW_DEMAND).setTargetPfd(1.0e-3)
        .setRequiredAvailability(0.995).setProofTestIntervalHours(8760.0)
        .setResponseTimeSeconds(2.0)
        .addAcceptanceCriterion("Final elements shall close within 2 seconds.")
        .addAcceptanceCriterion("Proof test interval shall not exceed 12 months.")
        .addEvidence(createEvidence());
  }

  /**
   * Creates a reusable HIPPS barrier for tests.
   *
   * @return safety barrier
   */
  private SafetyBarrier createHippsBarrier() {
    return new SafetyBarrier("B-HIPPS-001").setName("HIPPS")
        .setDescription("High-integrity pressure protection for HP separator")
        .setType(SafetyBarrier.BarrierType.PREVENTION)
        .setStatus(SafetyBarrier.BarrierStatus.AVAILABLE)
        .setSafetyFunction("Prevent V-101 overpressure by closing SDV-101A/B")
        .setOwner("Instrument").setPfd(1.0e-3).addEquipmentTag("V-101").addHazardId("OVP-V-101")
        .setPerformanceStandard(createHippsStandard()).addEvidence(createEvidence());
  }
}
