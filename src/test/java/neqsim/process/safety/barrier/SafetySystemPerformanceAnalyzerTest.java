package neqsim.process.safety.barrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.logic.sis.Detector;
import neqsim.process.logic.sis.Detector.AlarmLevel;
import neqsim.process.logic.sis.Detector.DetectorType;
import neqsim.process.logic.sis.SafetyInstrumentedFunction;
import neqsim.process.logic.sis.VotingLogic;
import neqsim.process.measurementdevice.FireDetector;
import neqsim.process.measurementdevice.GasDetector;

/**
 * Unit tests for {@link SafetySystemPerformanceAnalyzer}.
 *
 * @author ESOL
 * @version 1.0
 */
class SafetySystemPerformanceAnalyzerTest {

  /**
   * Verifies a complete deluge demand/capacity case passes without warnings.
   */
  @Test
  void testDelugeBarrierPassesDemandCapacityAssessment() {
    BarrierRegister register = new BarrierRegister("BR-FIRE-001").setName("Fire protection");
    SafetyBarrier deluge = createBarrier("B-DELUGE-001", "Deluge system",
        "Mitigate jet fire exposure using deluge in module M01",
        SafetySystemCategory.FIREWATER_DELUGE).setPfd(2.8e-4).setEffectiveness(0.95);
    register.addBarrier(deluge).addSafetyCriticalElement(
        new SafetyCriticalElement("SCE-FW-001").setTag("FW-001").setName("Deluge zone M01")
            .setType(SafetyCriticalElement.ElementType.FIRE_PROTECTION).addBarrier(deluge));

    SafetySystemDemand demand = new SafetySystemDemand("D-DELUGE-001").setBarrierId("B-DELUGE-001")
        .setCategory(SafetySystemCategory.FIREWATER_DELUGE).setScenario("Jet fire on V-101")
        .setDemandValue(10.0).setCapacityValue(15.0).setDemandUnit("L/min/m2")
        .setRequiredResponseTimeSeconds(45.0).setActualResponseTimeSeconds(30.0)
        .setRequiredAvailability(0.99).setActualAvailability(0.995).setRequiredEffectiveness(0.90)
        .setActualEffectiveness(0.95);

    SafetySystemPerformanceReport report =
        new SafetySystemPerformanceAnalyzer(register).addDemandCase(demand).analyze();

    assertEquals(SafetySystemPerformanceReport.Verdict.PASS, report.getOverallVerdict());
    assertEquals(1, report.getAssessments().size());
    assertTrue(report.toJson().contains("capacityMargin"));
  }

  /**
   * Verifies that existing fire/gas detector and SIS implementations are included in the report.
   */
  @Test
  void testFireGasBarrierUsesDetectorAndSifEvidence() {
    BarrierRegister register = new BarrierRegister("BR-FG-001").setName("Fire and gas");
    SafetyBarrier detection = createBarrier("B-FGS-001", "Fire and gas detection",
        "Detect gas or fire and activate deluge", SafetySystemCategory.FIRE_GAS_DETECTION)
            .setPfd(2.8e-4);
    detection.addEquipmentTag("FD-101").addEquipmentTag("GD-101");
    register.addBarrier(detection).addSafetyCriticalElement(new SafetyCriticalElement("SCE-FGS-001")
        .setTag("FGS-001").setName("Fire and gas detection")
        .setType(SafetyCriticalElement.ElementType.INSTRUMENTED_FUNCTION).addBarrier(detection));

    FireDetector fireDetector = new FireDetector("FD-101", "Module M01");
    fireDetector.setDetectionDelay(2.0);
    GasDetector gasDetector =
        new GasDetector("GD-101", GasDetector.GasType.COMBUSTIBLE, "Module M01");
    gasDetector.setResponseTime(8.0);

    SafetyInstrumentedFunction sif =
        new SafetyInstrumentedFunction("B-FGS-001 Deluge SIF", VotingLogic.TWO_OUT_OF_THREE);
    sif.addDetector(new Detector("FD-101", DetectorType.FIRE, AlarmLevel.HIGH, 1.0, "binary"));
    sif.addDetector(new Detector("GD-101", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 60.0, "%LEL"));
    sif.addDetector(new Detector("GD-102", DetectorType.GAS, AlarmLevel.HIGH_HIGH, 60.0, "%LEL"));

    SafetySystemDemand demand = new SafetySystemDemand("D-FGS-001").setBarrierId("B-FGS-001")
        .setCategory(SafetySystemCategory.FIRE_GAS_DETECTION).setRequiredResponseTimeSeconds(20.0)
        .setActualResponseTimeSeconds(10.0).setRequiredAvailability(0.99)
        .setActualAvailability(0.996);

    SafetySystemPerformanceReport report = new SafetySystemPerformanceAnalyzer(register)
        .addMeasurementDevice(fireDetector).addMeasurementDevice(gasDetector)
        .addSafetyInstrumentedFunction(sif).addDemandCase(demand).analyze();

    assertEquals(SafetySystemPerformanceReport.Verdict.PASS, report.getOverallVerdict());
    String json = report.toJson();
    assertTrue(json.contains("GD-101"));
    assertTrue(json.contains("2oo3"));
  }

  /**
   * Verifies that passive fire protection fails when calculated demand exceeds documented rating.
   */
  @Test
  void testPassiveFireProtectionFailsWhenDemandExceedsCapacity() {
    BarrierRegister register = new BarrierRegister("BR-PFP-001").setName("PFP review");
    SafetyBarrier pfp =
        createBarrier("B-PFP-001", "Passive fire protection", "PFP coating on V-101 support steel",
            SafetySystemCategory.PASSIVE_FIRE_PROTECTION).setEffectiveness(0.80);
    register.addBarrier(pfp).addSafetyCriticalElement(
        new SafetyCriticalElement("SCE-PFP-001").setTag("PFP-V-101").setName("PFP on V-101")
            .setType(SafetyCriticalElement.ElementType.STRUCTURAL).addBarrier(pfp));

    SafetySystemDemand demand = new SafetySystemDemand("D-PFP-001").setBarrierId("B-PFP-001")
        .setCategory(SafetySystemCategory.PASSIVE_FIRE_PROTECTION)
        .setScenario("Jet fire thermal radiation at support steel").setDemandValue(45.0)
        .setCapacityValue(37.5).setDemandUnit("kW/m2").setRequiredEffectiveness(0.90)
        .setActualEffectiveness(0.80);

    SafetySystemPerformanceReport report =
        new SafetySystemPerformanceAnalyzer(register).addDemandCase(demand).analyze();

    assertEquals(SafetySystemPerformanceReport.Verdict.FAIL, report.getOverallVerdict());
    assertTrue(report.toJson().contains("Calculated demand exceeds documented barrier capacity"));
  }

  /**
   * Verifies that the quantitative SIL/PFD model is bridged into the same barrier report.
   */
  @Test
  void testQuantitativeSifBridgeAddsSilMetrics() {
    BarrierRegister register = new BarrierRegister("BR-QSIF-001").setName("Quantitative SIF");
    SafetyBarrier sifBarrier = createBarrier("B-SIF-101", "SIF shutdown",
        "SIF shutdown protects V-101", SafetySystemCategory.ESD_BLOWDOWN).setPfd(8.0e-4);
    register.addBarrier(sifBarrier);

    neqsim.process.safety.risk.sis.SafetyInstrumentedFunction quantitativeSif =
        neqsim.process.safety.risk.sis.SafetyInstrumentedFunction.builder().id("B-SIF-101")
            .name("B-SIF-101 quantitative SIL").description("SIF shutdown protects V-101").sil(3)
            .pfd(8.0e-4).architecture("1oo2").testIntervalHours(8760.0)
            .addProtectedEquipment("V-101").build();

    SafetySystemPerformanceReport report = new SafetySystemPerformanceAnalyzer(register)
        .addQuantitativeSafetyInstrumentedFunction(quantitativeSif).analyze();

    assertEquals(SafetySystemPerformanceReport.Verdict.PASS_WITH_WARNINGS,
        report.getOverallVerdict());
    String json = report.toJson();
    assertTrue(json.contains("claimedSIL"));
    assertTrue(json.contains("pfdAverage"));
  }

    /**
     * Verifies that process shutdown text is classified as PSD instead of generic ESD/blowdown.
     */
    @Test
    void testProcessShutdownBarrierClassifiesAsPsd() {
        BarrierRegister register = new BarrierRegister("BR-PSD-001").setName("PSD review");
        SafetyBarrier psdBarrier = createBarrier("B-PSD-101", "PSD valve",
            "Process shutdown closes shutdown valve SDV-101", SafetySystemCategory.UNKNOWN)
                        .setPfd(8.0e-4);
        register.addBarrier(psdBarrier);

        SafetySystemPerformanceReport report = new SafetySystemPerformanceAnalyzer(register).analyze();

        assertTrue(report.toJson().contains("PSD"));
    }

  /**
   * Creates a reusable barrier with a matching performance standard and evidence.
   *
   * @param id barrier identifier
   * @param name barrier name
   * @param function safety function text
   * @param category safety-system category
   * @return configured safety barrier
   */
  private SafetyBarrier createBarrier(String id, String name, String function,
      SafetySystemCategory category) {
    PerformanceStandard standard = new PerformanceStandard("PS-" + id).setTitle(name + " standard")
        .setSafetyFunction(function).setDemandMode(PerformanceStandard.DemandMode.LOW_DEMAND)
        .setTargetPfd(
            category == SafetySystemCategory.PASSIVE_FIRE_PROTECTION ? Double.NaN : 1.0e-3)
        .setRequiredAvailability(0.99).setResponseTimeSeconds(60.0)
        .addAcceptanceCriterion("Barrier shall meet documented functional requirements.")
        .addEvidence(createEvidence("E-" + id));

    return new SafetyBarrier(id).setName(name).setDescription(function)
        .setType(SafetyBarrier.BarrierType.MITIGATION)
        .setStatus(SafetyBarrier.BarrierStatus.AVAILABLE).setSafetyFunction(function)
        .setOwner("Technical safety").addEquipmentTag("V-101").addHazardId("LOC-V-101")
        .setPerformanceStandard(standard).addEvidence(createEvidence("BE-" + id));
  }

  /**
   * Creates traceable document evidence.
   *
   * @param id evidence identifier
   * @return document evidence
   */
  private DocumentEvidence createEvidence(String id) {
    return new DocumentEvidence(id, "STID-001", "Safety systems STID", "A", "4.2", 12,
        "references/stid.pdf", "Barrier requirement extracted from STID and C&E documents.", 0.95);
  }
}
