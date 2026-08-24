package neqsim.process.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.InfrastructureInput;
import neqsim.process.fielddevelopment.concept.ReservoirInput;
import neqsim.process.fielddevelopment.concept.WellsInput;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.Criterion;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.DevelopmentOption;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.RankingResult;

/** Executable regression coverage for the field-development API guide. */
class FieldDevelopmentApiGuideDocumentationTest extends neqsim.NeqSimTest {
  @Test
  void completeScreeningAndRankingExampleExecutes() {
    FieldConcept tieback = FieldConcept.gasTieback("Gas tieback", 30.0, 2, 0.8);
    FieldConcept standalone = FieldConcept.oilDevelopment("Oil development", 6, 5000.0, 0.15);

    ConceptEvaluator evaluator = new ConceptEvaluator();
    ConceptKPIs tiebackKpis = evaluator.evaluate(tieback);
    ConceptKPIs standaloneKpis = evaluator.evaluate(standalone);

    DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
    ranker.setWeightProfile("balanced");

    DevelopmentOption tiebackOption = ranker.addOption(tieback.getName());
    tiebackOption.setScore(Criterion.NPV, 600.0);
    tiebackOption.setScore(Criterion.CO2_INTENSITY, tiebackKpis.getCo2IntensityKgPerBoe());
    tiebackOption.setScore(Criterion.TECHNICAL_RISK, 1.0 - tiebackKpis.getTechnicalScore());

    DevelopmentOption standaloneOption = ranker.addOption(standalone.getName());
    standaloneOption.setScore(Criterion.NPV, 750.0);
    standaloneOption.setScore(Criterion.CO2_INTENSITY, standaloneKpis.getCo2IntensityKgPerBoe());
    standaloneOption.setScore(Criterion.TECHNICAL_RISK, 1.0 - standaloneKpis.getTechnicalScore());

    RankingResult ranking = ranker.rank();

    assertEquals(2, ranking.getRankedOptions().size());
    assertNotNull(ranking.getBestOption());
    assertTrue(Double.isFinite(ranking.getBestOption().getWeightedScore()));
    assertTrue(tiebackKpis.getTotalCapexMUSD() > 0.0);
    assertTrue(standaloneKpis.getTotalCapexMUSD() > 0.0);
    assertFalse(tiebackKpis.getOneLiner().isEmpty());
    assertFalse(standaloneKpis.getOneLiner().isEmpty());
  }

  @Test
  void inputUnitsIdentityAndExportPressureBoundaryAreExplicit() {
    ReservoirInput reservoir = ReservoirInput.gasCondensate().gor(5000.0, "Sm3/Sm3").co2Percent(2.5).h2sPercent(0.01)
        .waterCut(0.10).waterSalinity(35000.0).reservoirPressure(320.0).reservoirTemperature(95.0)
        .resourceUncertainty(30.0, 24.0, 18.0, "GSm3").recoveryFactor(0.65).build();
    WellsInput wells = WellsInput.builder().producerCount(4).injectorCount(2).producerType(WellsInput.WellType.GAS_LIFT)
        .completionType(WellsInput.CompletionType.SUBSEA).tubeheadPressure(100.0).ratePerWell(0.8, "MSm3/d")
        .shutInPressure(250.0).productivityIndex(10000.0).gasLift(0.15, "MSm3/d").build();
    InfrastructureInput infrastructure = InfrastructureInput.subseaTieback().tiebackLength(30.0).waterDepth(350.0)
        .exportPipeline(85.0, 20.0).ambientTemperatures(6.0, 10.0).exportType(InfrastructureInput.ExportType.WET_GAS)
        .hostCapacityAvailable(0.25).insulatedFlowline(true).electricHeating(false).exportPressure(999.0).build();
    FieldConcept concept = FieldConcept.builder("Gas-condensate tieback")
        .id("asset:gas-condensate-tieback:screening-v1").reservoir(reservoir).wells(wells)
        .infrastructure(infrastructure).build();

    assertEquals("asset:gas-condensate-tieback:screening-v1", concept.getId());
    assertEquals(0.8e6, wells.getRatePerWellSm3d(), 1.0e-9);
    assertEquals(24.0, reservoir.getResourceP50(), 1.0e-9);
    assertEquals("GSm3", reservoir.getResourceUnit());
    assertEquals(120.0, infrastructure.getExportPressure(), 1.0e-9);
  }

  @Test
  void fullAndQuickEvaluationExposeDocumentedOwnershipBoundary() {
    FieldConcept concept = FieldConcept.gasTieback("Evaluation boundary", 25.0, 2, 0.7);
    ConceptEvaluator evaluator = new ConceptEvaluator();

    ConceptKPIs full = evaluator.evaluate(concept);
    ConceptKPIs quick = evaluator.quickScreen(concept);

    assertNotNull(full.getSafetyReport());
    assertNull(quick.getSafetyReport());
    assertEquals(0.0, full.getNpv10MUSD(), 1.0e-12);
    assertEquals(0.0, full.getBreakEvenOilPriceUSD(), 1.0e-12);
    assertNotSame(full.getWarnings(), full.getWarnings());
    assertNotSame(full.getNotes(), full.getNotes());
  }
}
