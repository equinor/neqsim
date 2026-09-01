package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Map;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import org.junit.jupiter.api.Test;

/** Tests the edition, model, environment, trigger, and evidence boundaries of the F105 kernel. */
class DnvRpF105FreeSpanScreeningKernelTest {
  private final DnvRpF105FreeSpanScreeningKernel kernel = new DnvRpF105FreeSpanScreeningKernel();

  @Test
  void currentEditionCalculatesReviewGatedFirstModeAndDimensionlessGroups() {
    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = kernel.calculate(validInput(), null);
    DnvRpF105FreeSpanAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("DNV-RP-F105 2025-12", value.getStandardEdition());
    assertEquals(0.00022674784021694156, value.getSecondMomentOfAreaM4(), 1.0e-16);
    assertEquals(514719.64080080553, value.getEulerCriticalCompressionN(), 1.0e-7);
    assertEquals(92.62117937635072, value.getSpanToHydrodynamicDiameter(), 1.0e-12);
    assertEquals(1.0618221449736536, value.getFundamentalNaturalFrequencyHz(), 1.0e-12);
    assertEquals(0.49397962334053724, value.getCurrentVortexSheddingFrequencyHz(), 1.0e-12);
    assertEquals(0.46521879928657367, value.getCurrentFrequencyRatio(), 1.0e-12);
    assertEquals(2.326093996432868, value.getCurrentReducedVelocity(), 1.0e-12);
    assertEquals(0.1, value.getWaveFrequencyHz(), 0.0);
    assertEquals(0.09417773068057575, value.getWaveFrequencyRatio(), 1.0e-12);
    assertEquals(3.489140994649302, value.getWaveReducedVelocity(), 1.0e-12);
    assertEquals(37.04847175054029, value.getKeuleganCarpenterNumber(), 1.0e-12);
    assertEquals(Arrays.asList("WAVE_REDUCED_VELOCITY"), value.getDetailedResponseTriggers());
    assertTrue(value.isDetailedResponseAssessmentRequired());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
    assertThrows(UnsupportedOperationException.class, () -> value.getDetailedResponseTriggers().clear());
  }

  @Test
  void positiveTensionRaisesTheFirstModeFrequency() {
    DnvRpF105FreeSpanAssessment withoutTension = kernel.calculate(baseBuilder().effectiveAxialForceN(0.0).build(), null)
        .getValue();
    DnvRpF105FreeSpanAssessment withTension = kernel.calculate(validInput(), null).getValue();

    assertNotNull(withoutTension);
    assertNotNull(withTension);
    assertTrue(withTension.getFundamentalNaturalFrequencyHz() > withoutTension.getFundamentalNaturalFrequencyHz());
  }

  @Test
  void compressionBeyondEulerInstabilityFailsClosed() {
    DnvRpF105FreeSpanScreeningKernel.Input input = baseBuilder().effectiveAxialForceN(-600000.0).build();

    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void invalidRawValuesAndMissingEvidenceFailClosed() {
    DnvRpF105FreeSpanScreeningKernel.Input input = baseBuilder().steelWallThicknessM(-0.0206)
        .spanGeometryVerified(false).environmentalBasisVerified(false).build();

    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = kernel.calculate(input, null);

    assertEquals(-0.0206, input.getSteelWallThicknessM(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void exactEditionAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.DNV_RP_F105, "2021");
    StandardEdition amended = StandardEdition.of(StandardType.DNV_RP_F105, "2025-12",
        Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F105)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(baseBuilder(historical, "Pipeline").build(), null).isReady());
    assertFalse(kernel
        .assess(baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipe").build(), null).isReady());
    assertFalse(
        kernel.assess(baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Separator").build(), null)
            .isReady());
    assertThrows(IllegalArgumentException.class, () -> DnvRpF105FreeSpanScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_C203), "Pipeline"));
  }

  @Test
  void currentOnlyCaseReturnsZeroWaveMetrics() {
    DnvRpF105FreeSpanScreeningKernel.Input input = baseBuilder().waveOrbitalVelocityAmplitudeMPerS(0.0)
        .wavePeriodS(Double.NaN).maxWaveReducedVelocityForScreening(3.0).build();

    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(0.0, result.getValue().getWaveFrequencyHz(), 0.0);
    assertEquals(0.0, result.getValue().getWaveFrequencyRatio(), 0.0);
    assertEquals(0.0, result.getValue().getWaveReducedVelocity(), 0.0);
    assertEquals(0.0, result.getValue().getKeuleganCarpenterNumber(), 0.0);
  }

  @Test
  void documentedExampleIsRunnable() {
    EngineeringCalculationResult<DnvRpF105FreeSpanAssessment> result = new DnvRpF105FreeSpanScreeningKernel()
        .calculate(validInput(), null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(1.0618221449736536, ((Double) report.get("fundamentalNaturalFrequencyHz")).doubleValue(), 1.0e-12);
    assertEquals(Boolean.TRUE, report.get("detailedResponseAssessmentRequired"));
  }

  private static DnvRpF105FreeSpanScreeningKernel.Input validInput() {
    return baseBuilder().build();
  }

  private static DnvRpF105FreeSpanScreeningKernel.Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline");
  }

  private static DnvRpF105FreeSpanScreeningKernel.Input.Builder baseBuilder(StandardEdition edition,
      String equipmentType) {
    return DnvRpF105FreeSpanScreeningKernel.Input.builder(edition, equipmentType).spanLengthM(30.0)
        .steelOuterDiameterM(0.3239).steelWallThicknessM(0.0206).hydrodynamicDiameterM(0.3239).youngsModulusPa(207.0e9)
        .effectiveMassPerLengthKgPerM(250.0).effectiveAxialForceN(500000.0).currentVelocityMPerS(0.8)
        .waveOrbitalVelocityAmplitudeMPerS(1.2).wavePeriodS(10.0).strouhalNumber(0.2).lockInFrequencyRatioLower(0.8)
        .lockInFrequencyRatioUpper(1.2).maxCurrentReducedVelocityForScreening(4.0)
        .maxWaveReducedVelocityForScreening(3.0).spanGeometryVerified(true).structuralModelVerified(true)
        .environmentalBasisVerified(true).projectScreeningLimitsVerified(true);
  }
}
