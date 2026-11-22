package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic.Input;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic.Result;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic.Status;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic.ThreePhaseBound;

class SevereSluggingSystemDiagnosticTest {
  private static Input.Builder baseInput() {
    return Input.builder().upstreamGasVolumeM3(20.0).riserAreaM2(0.1).riserHeightM(100.0).separatorPressurePa(500_000.0)
        .liquidDensityKgPerM3(800.0).riserLiquidHoldup(0.9).gasCapVoidFraction(0.8).validFlowlineRiserTopology(true)
        .flowlineStratified(true).flowlineContainsGasAndLiquid(true);
  }

  @Test
  void reproducesTaitelAnalyticalCriterion() {
    Result result = SevereSluggingSystemDiagnostic.evaluate(baseInput().build());

    double expectedGasExpansionHead = 20.0 / (0.1 * 0.8);
    double expectedCriticalPressure = 0.9 * 800.0 * SevereSluggingSystemDiagnostic.STANDARD_GRAVITY
        * (expectedGasExpansionHead - 100.0);
    assertEquals(expectedGasExpansionHead, result.getGasExpansionHeadM(), 1.0e-12);
    assertEquals(expectedCriticalPressure, result.getCriticalTopPressurePa(), 1.0e-9);
    assertEquals(500_000.0 - expectedCriticalPressure, result.getPressureMarginPa(), 1.0e-9);
    assertEquals(Status.UNSTABLE, result.getStatus());
    assertTrue(result.isSevereSluggingPossible());
  }

  @Test
  void systemScaleAndBackpressureChangeThePrediction() {
    Result compact = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().upstreamGasVolumeM3(10.0).separatorPressurePa(200_000.0).build());
    Result longFlowline = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().upstreamGasVolumeM3(20.0).separatorPressurePa(200_000.0).build());
    Result choked = SevereSluggingSystemDiagnostic.evaluate(baseInput().upstreamGasVolumeM3(20.0)
        .separatorPressurePa(200_000.0).staticChokePressureDropPa(900_000.0).build());

    assertEquals(Status.STABLE, compact.getStatus());
    assertEquals(Status.UNSTABLE, longFlowline.getStatus());
    assertEquals(Status.STABLE, choked.getStatus());
    assertTrue(longFlowline.getCriticalTopPressurePa() > compact.getCriticalTopPressurePa());
    assertEquals(1_100_000.0, choked.getEffectiveTopPressurePa(), 0.0);
  }

  @Test
  void extractsVariableAreaFlowlineAndConstantAreaRiser() {
    TwoFluidSection[] sections = new TwoFluidSection[] {
        section(100.0, 0.20, Math.toRadians(-2.0), 0.60, 0.40, PipeSection.FlowRegime.STRATIFIED_SMOOTH, 300_000.0),
        section(200.0, 0.30, 0.0, 0.50, 0.50, PipeSection.FlowRegime.STRATIFIED_WAVY, 280_000.0),
        section(20.0, 0.10, Math.toRadians(30.0), 0.20, 0.80, PipeSection.FlowRegime.SLUG, 240_000.0),
        section(30.0, 0.10, Math.toRadians(90.0), 0.30, 0.70, PipeSection.FlowRegime.SLUG, 200_000.0) };

    Input input = SevereSluggingSystemDiagnostic.fromSections(sections, 2, 0.89, 0.0);
    double expectedGasVolume = sections[0].getArea() * 100.0 * 0.60 + sections[1].getArea() * 200.0 * 0.50;

    assertTrue(input.hasValidFlowlineRiserTopology());
    assertTrue(input.isFlowlineStratified());
    assertTrue(input.flowlineContainsGasAndLiquid());
    assertEquals(expectedGasVolume, input.getUpstreamGasVolumeM3(), 1.0e-12);
    assertEquals(40.0, input.getRiserHeightM(), 1.0e-12);
    assertEquals(0.74, input.getRiserLiquidHoldup(), 1.0e-12);
    assertEquals(800.0, input.getLiquidDensityKgPerM3(), 1.0e-12);
    assertEquals(200_000.0, input.getSeparatorPressurePa(), 0.0);
    assertTrue(SevereSluggingSystemDiagnostic.evaluate(input).isApplicable());
  }

  @Test
  void rejectsAreaChangeInsideRiserButNotAtFlowlineRiserTransition() {
    TwoFluidSection[] sections = new TwoFluidSection[] {
        section(100.0, 0.20, 0.0, 0.60, 0.40, PipeSection.FlowRegime.STRATIFIED_SMOOTH, 300_000.0),
        section(20.0, 0.10, Math.toRadians(30.0), 0.20, 0.80, PipeSection.FlowRegime.SLUG, 240_000.0),
        section(30.0, 0.12, Math.toRadians(90.0), 0.30, 0.70, PipeSection.FlowRegime.SLUG, 200_000.0) };

    Input input = SevereSluggingSystemDiagnostic.fromSections(sections, 1, 0.89, 0.0);

    assertFalse(input.hasValidFlowlineRiserTopology());
    assertEquals(Status.NOT_APPLICABLE_INVALID_TOPOLOGY, SevereSluggingSystemDiagnostic.evaluate(input).getStatus());
  }

  @Test
  void higherRiserAndTopPressureAreStabilisingTrends() {
    Result shortRiser = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().riserHeightM(50.0).separatorPressurePa(800_000.0).build());
    Result tallRiser = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().riserHeightM(180.0).separatorPressurePa(800_000.0).build());

    assertEquals(Status.UNSTABLE, shortRiser.getStatus());
    assertEquals(Status.STABLE, tallRiser.getStatus());
    assertTrue(tallRiser.getCriticalTopPressurePa() < shortRiser.getCriticalTopPressurePa());
  }

  @Test
  void rejectsStatesOutsideDocumentedValidityRange() {
    assertEquals(Status.NOT_APPLICABLE_INVALID_TOPOLOGY,
        SevereSluggingSystemDiagnostic.evaluate(baseInput().validFlowlineRiserTopology(false).build()).getStatus());
    assertEquals(Status.NOT_APPLICABLE_NON_STRATIFIED_FLOWLINE,
        SevereSluggingSystemDiagnostic.evaluate(baseInput().flowlineStratified(false).build()).getStatus());
    assertEquals(Status.NOT_APPLICABLE_SINGLE_PHASE,
        SevereSluggingSystemDiagnostic.evaluate(baseInput().upstreamGasVolumeM3(0.0).build()).getStatus());
    assertEquals(Status.NOT_APPLICABLE_SINGLE_PHASE, SevereSluggingSystemDiagnostic
        .evaluate(baseInput().flowlineContainsGasAndLiquid(false).flowlineStratified(false).build()).getStatus());
    assertEquals(Status.NOT_VALIDATED_THREE_PHASE,
        SevereSluggingSystemDiagnostic.evaluate(baseInput().threePhase(true).build()).getStatus());

    Result notApplicable = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().validFlowlineRiserTopology(false).build());
    assertFalse(notApplicable.isApplicable());
    assertTrue(Double.isNaN(notApplicable.getStabilityRatio()));
  }

  @Test
  void isDeterministicAndSerializable() throws Exception {
    Input input = baseInput().build();
    Result first = SevereSluggingSystemDiagnostic.evaluate(input);
    Result second = SevereSluggingSystemDiagnostic.evaluate(input);
    assertEquals(first.getCriticalTopPressurePa(), second.getCriticalTopPressurePa(), 0.0);
    assertEquals(first.getStatus(), second.getStatus());

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(first);
    }
    Result restored;
    try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Result) inputStream.readObject();
    }
    assertEquals(first.getStatus(), restored.getStatus());
    assertEquals(first.getCriticalTopPressurePa(), restored.getCriticalTopPressurePa(), 0.0);
  }

  @Test
  void threePhaseBoundsBracketTheTwoPhaseCriterion() {
    Input.Builder threePhase = baseInput().threePhase(true).oilDensityKgPerM3(800.0).waterDensityKgPerM3(1000.0);
    Result result = SevereSluggingSystemDiagnostic.evaluate(threePhase.separatorPressurePa(1_200_000.0).build());

    double oilOnly = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().liquidDensityKgPerM3(800.0).separatorPressurePa(1_200_000.0).build())
        .getCriticalTopPressurePa();
    double waterOnly = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().liquidDensityKgPerM3(1000.0).separatorPressurePa(1_200_000.0).build())
        .getCriticalTopPressurePa();

    assertEquals(Status.NOT_VALIDATED_THREE_PHASE, result.getStatus());
    assertFalse(result.isApplicable());
    assertFalse(result.isSevereSluggingPossible());
    assertEquals(oilOnly, result.getCriticalTopPressureLowerBoundPa(), 1.0e-9);
    assertEquals(waterOnly, result.getCriticalTopPressureUpperBoundPa(), 1.0e-9);
    assertEquals(1_200_000.0, result.getEffectiveTopPressurePa(), 0.0);
    assertEquals(ThreePhaseBound.INCONCLUSIVE, result.getThreePhaseBound());
    assertTrue(Double.isNaN(result.getCriticalTopPressurePa()));
  }

  @Test
  void threePhaseBoundsAgreeAwayFromTheBracket() {
    Input.Builder threePhase = baseInput().threePhase(true).oilDensityKgPerM3(800.0).waterDensityKgPerM3(1000.0);

    Result low = SevereSluggingSystemDiagnostic.evaluate(threePhase.separatorPressurePa(500_000.0).build());
    Result high = SevereSluggingSystemDiagnostic.evaluate(threePhase.separatorPressurePa(1_500_000.0).build());
    Result choked = SevereSluggingSystemDiagnostic
        .evaluate(threePhase.separatorPressurePa(500_000.0).staticChokePressureDropPa(1_000_000.0).build());
    // Order of the two densities must not matter
    Result swapped = SevereSluggingSystemDiagnostic.evaluate(baseInput().threePhase(true).oilDensityKgPerM3(1000.0)
        .waterDensityKgPerM3(800.0).separatorPressurePa(500_000.0).build());

    assertEquals(ThreePhaseBound.UNSTABLE_FOR_BOTH_BOUNDS, low.getThreePhaseBound());
    assertEquals(ThreePhaseBound.STABLE_FOR_BOTH_BOUNDS, high.getThreePhaseBound());
    assertEquals(ThreePhaseBound.STABLE_FOR_BOTH_BOUNDS, choked.getThreePhaseBound());
    assertEquals(low.getCriticalTopPressureLowerBoundPa(), swapped.getCriticalTopPressureLowerBoundPa(), 0.0);
    assertEquals(low.getCriticalTopPressureUpperBoundPa(), swapped.getCriticalTopPressureUpperBoundPa(), 0.0);
    assertEquals(Status.NOT_VALIDATED_THREE_PHASE, low.getStatus());
    assertEquals(Status.NOT_VALIDATED_THREE_PHASE, high.getStatus());
  }

  @Test
  void threePhaseBoundsNeedDensitiesAndTwoPhaseValidityGates() {
    Result noDensities = SevereSluggingSystemDiagnostic.evaluate(baseInput().threePhase(true).build());
    Result oneDensity = SevereSluggingSystemDiagnostic
        .evaluate(baseInput().threePhase(true).oilDensityKgPerM3(800.0).build());
    Result nonStratified = SevereSluggingSystemDiagnostic.evaluate(baseInput().threePhase(true).oilDensityKgPerM3(800.0)
        .waterDensityKgPerM3(1000.0).flowlineStratified(false).build());
    Result twoPhase = SevereSluggingSystemDiagnostic.evaluate(baseInput().build());

    assertEquals(ThreePhaseBound.NOT_AVAILABLE, noDensities.getThreePhaseBound());
    assertEquals(ThreePhaseBound.NOT_AVAILABLE, oneDensity.getThreePhaseBound());
    assertEquals(ThreePhaseBound.NOT_AVAILABLE, nonStratified.getThreePhaseBound());
    assertEquals(Status.NOT_VALIDATED_THREE_PHASE, nonStratified.getStatus());
    assertTrue(Double.isNaN(noDensities.getCriticalTopPressureUpperBoundPa()));
    assertEquals(ThreePhaseBound.NOT_AVAILABLE, twoPhase.getThreePhaseBound());
    assertThrows(IllegalArgumentException.class, () -> baseInput().oilDensityKgPerM3(-800.0).build());
  }

  @Test
  void extractsOilAndWaterDensitiesForThreePhaseRiser() {
    TwoFluidSection[] sections = new TwoFluidSection[] {
        section(100.0, 0.20, Math.toRadians(-2.0), 0.60, 0.40, PipeSection.FlowRegime.STRATIFIED_SMOOTH, 300_000.0),
        section(30.0, 0.10, Math.toRadians(90.0), 0.30, 0.70, PipeSection.FlowRegime.SLUG, 200_000.0) };
    for (TwoFluidSection section : sections) {
      section.setOilHoldup(0.75 * section.getLiquidHoldup());
      section.setWaterHoldup(0.25 * section.getLiquidHoldup());
      section.setOilDensity(780.0);
      section.setWaterDensity(1020.0);
    }

    Input input = SevereSluggingSystemDiagnostic.fromSections(sections, 1, 0.89, 0.0);
    Result result = SevereSluggingSystemDiagnostic.evaluate(input);

    assertTrue(input.isThreePhase());
    assertTrue(input.hasThreePhaseDensities());
    assertEquals(780.0, input.getOilDensityKgPerM3(), 1.0e-9);
    assertEquals(1020.0, input.getWaterDensityKgPerM3(), 1.0e-9);
    assertEquals(Status.NOT_VALIDATED_THREE_PHASE, result.getStatus());
    assertTrue(result.getCriticalTopPressureUpperBoundPa() > result.getCriticalTopPressureLowerBoundPa());
    assertFalse(result.getThreePhaseBound() == ThreePhaseBound.NOT_AVAILABLE);

    // The two-phase extraction test case has no oil-water split and therefore no bounds
    assertFalse(SevereSluggingSystemDiagnostic.fromSections(
        new TwoFluidSection[] {
            section(100.0, 0.20, 0.0, 0.60, 0.40, PipeSection.FlowRegime.STRATIFIED_SMOOTH, 300_000.0),
            section(30.0, 0.10, Math.toRadians(90.0), 0.30, 0.70, PipeSection.FlowRegime.SLUG, 200_000.0) },
        1, 0.89, 0.0).hasThreePhaseDensities());
  }

  @SuppressWarnings("deprecation")
  @Test
  void preservesLegacyLocalScreenWithoutSettingSystemRisk() {
    TwoFluidSection section = new TwoFluidSection(0.0, 10.0, 0.2, Math.toRadians(45.0));
    section.setInclinedSectionGasCarryoverNumber(0.75);
    section.setInclinedSectionLiquidFallbackPotential(true);

    assertEquals(0.75, section.getSevereSluggingNumber(), 0.0);
    assertTrue(section.isInclinedSectionLiquidFallbackPotential());
    assertFalse(section.isSevereSlugPotential());

    TwoFluidSection cloned = section.clone();
    assertEquals(0.75, cloned.getInclinedSectionGasCarryoverNumber(), 0.0);
    assertTrue(cloned.isInclinedSectionLiquidFallbackPotential());
    assertFalse(cloned.isSevereSlugPotential());
  }

  @Test
  void validatesUnitsAndFractionsAtConstruction() {
    assertThrows(IllegalArgumentException.class, () -> baseInput().separatorPressurePa(0.0).build());
    assertThrows(IllegalArgumentException.class, () -> baseInput().riserAreaM2(Double.NaN).build());
    assertThrows(IllegalArgumentException.class, () -> baseInput().gasCapVoidFraction(0.0).build());
    assertThrows(IllegalArgumentException.class, () -> baseInput().riserLiquidHoldup(1.01).build());
  }

  private static TwoFluidSection section(double lengthM, double diameterM, double inclinationRad, double gasHoldup,
      double liquidHoldup, PipeSection.FlowRegime regime, double pressurePa) {
    TwoFluidSection section = new TwoFluidSection(0.0, lengthM, diameterM, inclinationRad);
    section.setGasHoldup(gasHoldup);
    section.setLiquidHoldup(liquidHoldup);
    section.setLiquidDensity(800.0);
    section.setPressure(pressurePa);
    section.setFlowRegime(regime);
    return section;
  }
}
