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

/** Tests the edition, equation, pressure basis, applicability, and evidence boundaries of the F101 kernel. */
class DnvRpF101CorrodedPipelineScreeningKernelTest {
  private final DnvRpF101CorrodedPipelineScreeningKernel kernel = new DnvRpF101CorrodedPipelineScreeningKernel();

  @Test
  void currentEditionCalculatesReviewGatedIsolatedDefectPressureScreen() {
    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = kernel.calculate(validInput(), null);
    DnvRpF101CorrodedPipelineAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("DNV-RP-F101 2019-09+AMD:2025-09", value.getStandardEdition());
    assertEquals(0.0045, value.getAssessmentDefectDepthM(), 1.0e-15);
    assertEquals(0.008199999999999999, value.getRemainingWallThicknessM(), 1.0e-15);
    assertEquals(0.35433070866141736, value.getDefectDepthToWallRatio(), 1.0e-15);
    assertEquals(2.489982409581401, value.getNormalizedDefectLength(), 1.0e-15);
    assertEquals(1.7093869790096357, value.getLengthCorrectionFactor(), 1.0e-15);
    assertEquals(27435897.435897436, value.getUncorrodedFailurePressurePa(), 1.0e-8);
    assertEquals(0.8145039460810929, value.getDefectPressureReductionFactor(), 1.0e-15);
    assertEquals(22346646.7258146, value.getCalculatedFailurePressurePa(), 1.0e-8);
    assertEquals(16089585.64258651, value.getCallerControlledPressureLimitPa(), 1.0e-8);
    assertEquals(10000000.0, value.getAssessedPressureDifferentialPa(), 0.0);
    assertEquals(0.6215200454591963, value.getPressureUtilization(), 1.0e-15);
    assertEquals(6089585.642586511, value.getPressureMarginPa(), 1.0e-8);
    assertTrue(value.isWithinCallerControlledPressureLimit());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void deeperDefectLowersCalculatedFailurePressure() {
    DnvRpF101CorrodedPipelineAssessment shallower = kernel.calculate(baseBuilder().build(), null).getValue();
    DnvRpF101CorrodedPipelineAssessment deeper = kernel
        .calculate(baseBuilder().measuredDefectDepthM(0.006).build(), null).getValue();

    assertNotNull(shallower);
    assertNotNull(deeper);
    assertTrue(deeper.getCalculatedFailurePressurePa() < shallower.getCalculatedFailurePressurePa());
  }

  @Test
  void callerFactorChangesLimitButNotCalculatedFailurePressure() {
    DnvRpF101CorrodedPipelineAssessment factorOne = kernel
        .calculate(baseBuilder().callerControlledPressureFactor(1.0).build(), null).getValue();
    DnvRpF101CorrodedPipelineAssessment factorHalf = kernel
        .calculate(baseBuilder().callerControlledPressureFactor(0.5).build(), null).getValue();

    assertNotNull(factorOne);
    assertNotNull(factorHalf);
    assertEquals(factorOne.getCalculatedFailurePressurePa(), factorHalf.getCalculatedFailurePressurePa(), 0.0);
    assertEquals(0.5 * factorOne.getCallerControlledPressureLimitPa(), factorHalf.getCallerControlledPressureLimitPa(),
        1.0e-8);
  }

  @Test
  void invalidRawValuesAndMissingEvidenceFailClosed() {
    DnvRpF101CorrodedPipelineScreeningKernel.Input input = baseBuilder().assessmentWallThicknessM(-0.0127)
        .inspectionSizingVerified(false).projectFactorVerified(false).build();

    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = kernel.calculate(input, null);

    assertEquals(-0.0127, input.getAssessmentWallThicknessM(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void defectThatLeavesNoRemainingWallFailsClosed() {
    DnvRpF101CorrodedPipelineScreeningKernel.Input input = baseBuilder().measuredDefectDepthM(0.0122)
        .defectDepthAllowanceM(0.0005).build();

    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void overflowedResistanceResultFailsClosed() {
    DnvRpF101CorrodedPipelineScreeningKernel.Input input = baseBuilder().assessmentWallThicknessM(0.2539)
        .characteristicUltimateTensileStrengthPa(Double.MAX_VALUE).build();

    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertTrue(result.getReadiness().getBlockers().stream()
        .anyMatch(item -> "RP_F101_NUMERICAL_RESULT_INVALID".equals(item.toMap().get("code"))));
  }

  @Test
  void exactEditionAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.DNV_RP_F101, "2019-09");
    StandardEdition amended = StandardEdition.of(StandardType.DNV_RP_F101, "2019-09+AMD:2025-09",
        Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F101)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(baseBuilder(historical, "Pipeline").build(), null).isReady());
    assertFalse(kernel
        .assess(baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F101), "Pipe").build(), null).isReady());
    assertFalse(
        kernel.assess(baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F101), "Separator").build(), null)
            .isReady());
    assertThrows(IllegalArgumentException.class, () -> DnvRpF101CorrodedPipelineScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline"));
  }

  @Test
  void documentedExampleIsRunnable() {
    EngineeringCalculationResult<DnvRpF101CorrodedPipelineAssessment> result = new DnvRpF101CorrodedPipelineScreeningKernel()
        .calculate(validInput(), null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(22346646.7258146, ((Double) report.get("calculatedFailurePressurePa")).doubleValue(), 1.0e-8);
    assertEquals(Boolean.TRUE, report.get("withinCallerControlledPressureLimit"));
  }

  private static DnvRpF101CorrodedPipelineScreeningKernel.Input validInput() {
    return baseBuilder().build();
  }

  private static DnvRpF101CorrodedPipelineScreeningKernel.Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F101), "Pipeline");
  }

  private static DnvRpF101CorrodedPipelineScreeningKernel.Input.Builder baseBuilder(StandardEdition edition,
      String equipmentType) {
    return DnvRpF101CorrodedPipelineScreeningKernel.Input.builder(edition, equipmentType).steelOuterDiameterM(0.508)
        .assessmentWallThicknessM(0.0127).measuredDefectDepthM(0.004).defectDepthAllowanceM(0.0005)
        .defectAxialLengthM(0.2).characteristicUltimateTensileStrengthPa(535.0e6).internalPressurePaAbsolute(10.1e6)
        .externalPressurePaAbsolute(0.1e6).callerControlledPressureFactor(0.72).geometryVerified(true)
        .inspectionSizingVerified(true).materialStrengthVerified(true).pressureBasisVerified(true)
        .projectFactorVerified(true).isolatedLongitudinalMetalLossApplicabilityVerified(true);
  }
}
