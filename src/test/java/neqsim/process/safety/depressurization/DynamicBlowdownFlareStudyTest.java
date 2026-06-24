package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import neqsim.process.safety.depressurization.DynamicBlowdownFlareStudyDataSource.BlowdownSource;
import neqsim.process.safety.rupture.PidTopologyEvidence;
import neqsim.process.safety.rupture.SafetyEvidenceReference;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Tests for governed dynamic blowdown and flare-load studies.
 *
 * @author ESOL
 * @version 1.0
 */
class DynamicBlowdownFlareStudyTest {

  /**
   * Verifies line/equipment-list evidence readiness and JSON output.
   */
  @Test
  void lineEquipmentEvidenceCarriesReadiness() {
    LineEquipmentListEvidence evidence = reviewedLineEquipmentEvidence();

    assertTrue(evidence.isSimulationReady());
    assertTrue(evidence.readiness().isDesignGrade());
    assertTrue(evidence.toJson().contains("line_equipment_list_evidence.v1"));
  }

  /**
   * Verifies the governed runner executes blowdown, PSV sizing, flare load and handoff generation.
   */
  @Test
  void governedDynamicBlowdownRunnerBuildsFlareHandoff() {
    DynamicBlowdownFlareStudyDataSource dataSource = reviewedDataSource();

    DynamicBlowdownFlareStudyHandoff handoff = DynamicBlowdownFlareStudyRunner.builder()
        .timeStepSeconds(5.0).maxTimeSeconds(120.0).build().run(dataSource);

    assertTrue(handoff.getCalculationReadiness().isReadyForCalculation());
    assertTrue(handoff.getStandardsReadiness().isReadyForCalculation());
    assertNotNull(handoff.getResult());
    assertNotNull(handoff.getFlareLoadHandoff());
    assertTrue(handoff.toJson().contains("dynamic_blowdown_flare_study_handoff.v1"));
    assertTrue(handoff.toJson().contains("dynamic_blowdown_flare_load_handoff.v1"));
    assertTrue(handoff.toJson().contains("recommendedOrifice"));

    Map<String, Object> result = handoff.getResult();
    Map<String, Object> combined = mapValue(result.get("combinedLoad"));
    assertTrue(((Double) combined.get("peakTotalMassFlowKgPerS")).doubleValue() > 0.0);
    Map<String, Object> flareLoad = mapValue(result.get("flareLoad"));
    assertTrue(((Double) flareLoad.get("peakHeatDutyMW")).doubleValue() > 0.0);
  }

  /**
   * Verifies missing source inputs block calculation before running the simulator.
   */
  @Test
  void missingSourcesBlockCalculation() {
    DynamicBlowdownFlareStudyDataSource dataSource = DynamicBlowdownFlareStudyDataSource
        .builder("blocked").addGap("No protected equipment list supplied.").build();

    DynamicBlowdownFlareStudyHandoff handoff =
        DynamicBlowdownFlareStudyRunner.builder().build().run(dataSource);

    assertTrue(!handoff.getCalculationReadiness().isReadyForCalculation());
    assertNull(handoff.getResult());
    assertTrue(handoff.toJson().contains("NOT_READY"));
  }

  /**
   * Creates a reviewed data source for the dynamic runner.
   *
   * @return dynamic blowdown/flare data source
   */
  private DynamicBlowdownFlareStudyDataSource reviewedDataSource() {
    SafetyEvidenceReference sourceDrawing =
        SafetyEvidenceReference.builder("P_ID", "equipment_tag").documentId("P-ID-001")
            .valueText("V-100").status("diagram_reviewed").confidence(0.95).build();
    SafetyEvidenceReference pipingSpec = SafetyEvidenceReference
        .builder("PIPING_SPEC", "bdv_orifice_diameter_m").documentId("PCS-FLARE-001")
        .valueText("0.012").unit("m").status("fetched_joined").confidence(0.90).build();

    BlowdownSource source = BlowdownSource.builder("V-100", gasFluid()).equipmentTag("V-100")
        .vesselVolumeM3(2.0).orificeDiameterM(0.012).dischargeCoefficient(0.72)
        .backPressureBara(1.5).stopPressureBara(1.5).api521FireCase(8.0, true, true)
        .wallModel(300.0, 8.0, 470.0, 50.0).psvBasis(75.0, 0.21, false, false)
        .evidenceReference(sourceDrawing).evidenceReference(pipingSpec).build();

    return DynamicBlowdownFlareStudyDataSource.builder("dyn-1").addSource(source)
        .pidTopologyEvidence(reviewedTopology())
        .lineEquipmentListEvidence(reviewedLineEquipmentEvidence())
        .addSourceDocumentEvidence(sourceDrawing).addPipingSpecificationEvidence(pipingSpec)
        .flareHeader(0.3, 1.5, 288.15, 0.020, 1.30).flareGeometry(0.5, 35.0, 0.20)
        .flareDesignCapacity(5.0e8, 500.0, 30000.0).sourceDiagramsReviewed(true)
        .pidTopologyVerified(true).lineEquipmentListsReviewed(true)
        .pipingSpecificationRowsReviewed(true).vesselInventoryReviewed(true)
        .valveSizingBasisReviewed(true).psvBasisReviewed(true).flareSystemBasisReviewed(true)
        .fireCaseReviewed(true).standardsReviewed(true).humanReviewRequired(false).build();
  }

  /**
   * Creates reviewed topology evidence.
   *
   * @return topology evidence
   */
  private PidTopologyEvidence reviewedTopology() {
    return PidTopologyEvidence.builder("P-ID-001").revision("A").embeddedTextRead(true)
        .overlayGenerated(true).boundaryVerified(true).addInScopeTag("V-100")
        .addInScopeTag("BDV-100").addBoundaryTag("FLARE-HDR")
        .addNode("n1", "V-100", "equipment", "source").addNode("n2", "BDV-100", "valve", "blowdown")
        .addNode("n3", "FLARE-HDR", "boundary", "flare_header")
        .addEdge("e1", "n1", "n2", "BD-100", "gas").addEdge("e2", "n2", "n3", "FL-100", "gas")
        .build();
  }

  /**
   * Creates reviewed line/equipment evidence.
   *
   * @return line/equipment-list evidence
   */
  private LineEquipmentListEvidence reviewedLineEquipmentEvidence() {
    return LineEquipmentListEvidence.builder("line-eq-001").revision("A").lineListReviewed(true)
        .equipmentListReviewed(true).addEquipment("V-100", "separator", 2.0, 80.0, 60.0, 313.15)
        .addLine("FL-100", "BDV-100", "FLARE-HDR", 4.0, 0.102, 0.006, 30.0, "DD100", "API 5L X52")
        .build();
  }

  /**
   * Creates a small gas fluid for tests.
   *
   * @return gas fluid
   */
  private SystemInterface gasFluid() {
    SystemInterface fluid = new SystemSrkEos(313.15, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Casts a value to map for assertions.
   *
   * @param value value to cast
   * @return value cast to map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Object value) {
    return (Map<String, Object>) value;
  }
}
