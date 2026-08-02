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

class SevereSluggingSystemDiagnosticTest {
  private static Input.Builder baseInput() {
    return Input.builder().upstreamGasVolumeM3(20.0).riserAreaM2(0.1).riserHeightM(100.0).separatorPressurePa(500_000.0)
        .liquidDensityKgPerM3(800.0).riserLiquidHoldup(0.9).gasCapVoidFraction(0.8);
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
    assertEquals(Status.NOT_APPLICABLE_SINGLE_PHASE,
        SevereSluggingSystemDiagnostic
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
}
