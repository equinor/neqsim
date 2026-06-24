package neqsim.process.safety.rupture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import org.junit.jupiter.api.Test;

/**
 * Tests for blowdown pipe fire-rupture strain-rate screening.
 *
 * @author ESOL
 * @version 1.0
 */
class PipeFireRuptureStudyTest {

  /**
   * Verifies that exact pressure-profile points return the tabulated pressure.
   */
  @Test
  void pressureProfileReturnsExactTabulatedPoint() {
    BlowdownPressureProfile profile = benchmarkPressureProfile();

    assertEquals(33.0255, profile.pressureBaraAt(110.0), 1.0e-6);
    assertEquals(32.0255, profile.pressureBargAt(110.0), 1.0e-6);
  }

  /**
   * Verifies that a dynamic depressurization time series can feed the rupture pressure profile
   * directly.
   */
  @Test
  void pressureProfileCanBeBuiltFromDepressurizationResult() {
    DepressurizationResult result = new DepressurizationResult();
    result.time.add(Double.valueOf(0.0));
    result.pressureBara.add(Double.valueOf(87.0));
    result.time.add(Double.valueOf(5.0));
    result.pressureBara.add(Double.valueOf(80.0));
    result.time.add(Double.valueOf(10.0));
    result.pressureBara.add(Double.valueOf(74.0));

    BlowdownPressureProfile profile = BlowdownPressureProfile.fromDepressurizationResult(result,
        BlowdownPressureProfile.InterpolationMode.LINEAR);

    assertEquals(87.0, profile.pressureBaraAt(0.0), 1.0e-12);
    assertEquals(77.0, profile.pressureBaraAt(7.5), 1.0e-12);
    assertEquals(76.0, profile.pressureBargAt(7.5), 1.0e-12);
  }

  /**
   * Verifies the workbook material labels and fire scenario constants.
   */
  @Test
  void workbookMaterialLabelsAndSmallJetFireResolve() {
    assertEquals("22Cr duplex",
        PipeFireRuptureMaterial.fromSpreadsheetMaterialName("22Cr duplex").getMaterialName());
    assertEquals("Superduplex",
        PipeFireRuptureMaterial.fromSpreadsheetMaterialName("SUPERDUPLEX").getMaterialName());
    assertEquals("CS 360 API 5L-X52",
        PipeFireRuptureMaterial.fromSpreadsheetMaterialName("API 5L-X52").getMaterialName());
    assertEquals("6Mo",
        PipeFireRuptureMaterial.fromSpreadsheetMaterialName("6 Mo").getMaterialName());

    double smallJetHeatFlux =
        PipeFireRuptureScenario.spreadsheetSmallJetFire().heatFluxKWPerM2(20.0);
    double largeJetHeatFlux =
        PipeFireRuptureScenario.spreadsheetLargeJetFire().heatFluxKWPerM2(20.0);
    assertTrue(smallJetHeatFlux > 220.0);
    assertTrue(smallJetHeatFlux < 250.0);
    assertTrue(smallJetHeatFlux < largeJetHeatFlux);
  }

  /**
   * Benchmarks the Pipe 1 large-jet-fire case from the attached strain-rate workbook.
   */
  @Test
  void largeJetFirePredictsWorkbookPipeOneRuptureTime() {
    PipeFireRuptureResult result = PipeFireRuptureStudy
        .builder(benchmarkPipeOne(), PipeFireRuptureMaterial.spreadsheetDuplex22Cr(),
            PipeFireRuptureScenario.spreadsheetLargeJetFire(), benchmarkPressureProfile())
        .timeStepSeconds(5.0).maxTimeSeconds(200.0).build().run();

    assertTrue(result.isRupturePredicted());
    assertEquals(110.0, result.getRuptureTimeSeconds(), 5.0);
    assertEquals(32.0255, result.getRupturePressureBarg(), 1.0e-3);
    assertTrue(result.getRuptureAccumulatedStrain() > result.getRuptureStrainLimitValue());
    assertEquals(21.9, result.getReleaseEstimate().getLongPipeGasTwoSidesKgPerS(), 1.0);
    assertTrue(result.toJson().contains("pipe_fire_rupture_result.v1"));
  }

  /**
   * Verifies the governed data-source readiness and runner handoff path.
   */
  @Test
  void governedDataSourceRunnerBuildsHandoffAndUncertainty() {
    SafetyEvidenceReference pipingSpecEvidence = SafetyEvidenceReference
        .builder("PIPING_SPEC", "wall_thickness_mm").documentId("PCS-DD100").revision("D")
        .valueText("3.7").unit("mm").status("fetched_joined").confidence(0.95).build();
    SafetyEvidenceReference sourceDrawingEvidence =
        SafetyEvidenceReference.builder("P_ID", "line_tag").documentId("P-ID-001")
            .valueText("3DD100").status("diagram_reviewed").confidence(0.90).build();
    PipeFireRuptureInput input =
        benchmarkPipeOne().toBuilder().evidenceReference(pipingSpecEvidence).build();
    PipeFireRuptureDataSource dataSource = PipeFireRuptureDataSource.builder("study-1").input(input)
        .material(PipeFireRuptureMaterial.spreadsheetDuplex22Cr())
        .scenario(PipeFireRuptureScenario.spreadsheetLargeJetFire())
        .pressureProfile(benchmarkPressureProfile())
        .addPipingSpecificationEvidence(pipingSpecEvidence)
        .addSourceDocumentEvidence(sourceDrawingEvidence)
        .addAssumption(
            "Representative workbook material curve used until material basis is confirmed.")
        .sourceDiagramsReviewed(true).pidTopologyVerified(true)
        .pipingSpecificationRowsReviewed(true).materialCertificateReviewed(false)
        .blowdownProfileVerified(true).fireScenarioReviewed(true).standardsReviewed(false).build();

    SafetyStudyReadiness readiness = dataSource.readiness();
    assertTrue(readiness.isReadyForCalculation());
    assertTrue(!readiness.isDesignGrade());

    PipeFireRuptureStudyHandoff handoff = PipeFireRuptureStudyRunner.builder().timeStepSeconds(5.0)
        .maxTimeSeconds(200.0).runUncertainty(true).build().run(dataSource);

    assertTrue(handoff.getResult().isRupturePredicted());
    assertEquals(110.0, handoff.getResult().getRuptureTimeSeconds(), 5.0);
    assertTrue(handoff.getCalculationReadiness().isReadyForCalculation());
    assertTrue(!handoff.getStandardsReadiness().isDesignGrade());
    assertTrue(handoff.getUncertaintySummary().getCases().size() >= 5);
    assertTrue(handoff.toJson().contains("pipe_fire_rupture_study_handoff.v1"));
    assertTrue(handoff.toJson().contains("pipe_fire_rupture_source_term_handoff.v1"));
  }

  /**
   * Verifies that P&amp;ID topology evidence contributes to data-source readiness.
   */
  @Test
  void pidTopologyEvidenceCarriesGraphAndReadiness() {
    PidTopologyEvidence topology =
        PidTopologyEvidence.builder("P-ID-001").revision("A").embeddedTextRead(true)
            .overlayGenerated(true).boundaryVerified(true).addInScopeTag("3DD100")
            .addBoundaryTag("XV-001").addNode("n1", "20VA001", "equipment", "source")
            .addNode("n2", "XV-001", "valve", "boundary").addEdge("e1", "n1", "n2", "3DD100", "gas")
            .build();
    PipeFireRuptureDataSource dataSource = PipeFireRuptureDataSource.builder("study-with-topology")
        .input(benchmarkPipeOne()).material(PipeFireRuptureMaterial.spreadsheetDuplex22Cr())
        .scenario(PipeFireRuptureScenario.spreadsheetLargeJetFire())
        .pressureProfile(benchmarkPressureProfile()).pidTopologyEvidence(topology)
        .sourceDiagramsReviewed(true).pidTopologyVerified(true)
        .pipingSpecificationRowsReviewed(true).materialCertificateReviewed(true)
        .blowdownProfileVerified(true).fireScenarioReviewed(true).standardsReviewed(true)
        .humanReviewRequired(false).build();

    assertTrue(topology.isSimulationReady());
    assertTrue(dataSource.readiness().isDesignGrade());
    assertTrue(dataSource.toJson().contains("pid_topology_evidence.v1"));
  }

  /**
   * Verifies that a missing computational basis blocks the runner from calculating.
   */
  @Test
  void missingDataSourceInputsAreBlockedBeforeCalculation() {
    PipeFireRuptureDataSource dataSource = PipeFireRuptureDataSource.builder("blocked-study")
        .addGap("Piping specification row missing.").build();

    PipeFireRuptureStudyHandoff handoff =
        PipeFireRuptureStudyRunner.builder().build().run(dataSource);

    assertTrue(!handoff.getCalculationReadiness().isReadyForCalculation());
    assertNull(handoff.getResult());
    assertTrue(handoff.toJson().contains("NOT_READY"));
  }

  /**
   * Creates the workbook Pipe 1 input row.
   *
   * @return pipe input object
   */
  private PipeFireRuptureInput benchmarkPipeOne() {
    return PipeFireRuptureInput.builder("3DD100").pipeClass("DD100").nominalDiameterInches(3.0)
        .outsideDiameter(88.9, "mm").nominalWallThickness(3.7, "mm").corrosionAllowance(0.0, "mm")
        .wallThicknessUndertoleranceFraction(0.10).weldFactor(1.0).weightStressMPa(0.0)
        .fluidDensityKgPerM3(23.7487037155012).fluidHeatCapacityJPerKgK(2283.35469905934)
        .gasMolecularWeightKgPerKmol(18.2).initialTemperatureC(20.0).exposedLength(1.0, "m")
        .build();
  }

  /**
   * Creates the workbook pressure profile used in the benchmark case.
   *
   * @return blowdown pressure profile
   */
  private BlowdownPressureProfile benchmarkPressureProfile() {
    return BlowdownPressureProfile.fromMinutesAndBara(
        new double[] {0.0, 0.083333333, 0.166666667, 0.25, 0.333333333, 0.416666667, 0.5,
            0.583333333, 0.666666667, 0.75, 0.833333333, 0.916666667, 1.0, 1.083333333, 1.166666667,
            1.25, 1.333333333, 1.416666667, 1.5, 1.583333333, 1.666666667, 1.75, 1.833333333,
            1.916666667, 2.0},
        new double[] {61.3, 59.7053, 58.6349, 57.3483, 55.9731, 54.5583, 53.1249, 51.6842, 50.2472,
            48.8145, 47.3977, 46.004, 44.6383, 43.3046, 42.0058, 40.7442, 39.5206, 38.3367, 37.1941,
            36.0922, 35.0302, 34.0082, 33.0255, 32.0817, 31.1758});
  }
}
