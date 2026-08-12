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

/** Tests edition, aggregation, capacity, pressure, applicability, and evidence boundaries of the API 2000 kernel. */
class Api2000TankVentingScreeningKernelTest {
  private final Api2000TankVentingScreeningKernel kernel = new Api2000TankVentingScreeningKernel();

  @Test
  void currentEditionAggregatesDemandAndScreensRatedCapacityAndPressure() {
    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(validInput(), null);
    Api2000TankVentingAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("API-2000 7th Ed", value.getStandardEdition());
    assertEquals(0.105, value.getFillingOutbreathingRateM3PerS(), 1.0e-15);
    assertEquals(0.08, value.getWithdrawalInbreathingRateM3PerS(), 1.0e-15);
    assertEquals(0.13, value.getRequiredNormalOutbreathingRateM3PerS(), 1.0e-15);
    assertEquals(0.115, value.getRequiredNormalInbreathingRateM3PerS(), 1.0e-15);
    assertEquals(0.5, value.getRequiredEmergencyOutbreathingRateM3PerS(), 0.0);
    assertEquals(0.65, value.getNormalOutbreathingUtilization(), 1.0e-15);
    assertEquals(0.7666666666666667, value.getNormalInbreathingUtilization(), 1.0e-15);
    assertEquals(0.8333333333333334, value.getEmergencyOutbreathingUtilization(), 1.0e-15);
    assertEquals(0.07, value.getNormalOutbreathingCapacityMarginM3PerS(), 1.0e-15);
    assertEquals(0.035, value.getNormalInbreathingCapacityMarginM3PerS(), 1.0e-15);
    assertEquals(0.1, value.getEmergencyOutbreathingCapacityMarginM3PerS(), 1.0e-15);
    assertEquals(2000.0, value.getNormalPositivePressureMarginPa(), 0.0);
    assertEquals(500.0, value.getVacuumPressureMarginPa(), 0.0);
    assertEquals(500.0, value.getEmergencyPositivePressureMarginPa(), 0.0);
    assertTrue(value.allConstraintsSatisfied());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void inadequateCapacityAndExcessRatedPressureAreCalculatedFindingsNotHiddenBlockers() {
    Api2000TankVentingScreeningKernel.Input input = baseBuilder().normalOutbreathingRatedCapacityM3PerS(0.1)
        .emergencyOutbreathingRatedGaugePressurePa(5500.0).build();

    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(result.getValue());
    assertFalse(result.getValue().isNormalOutbreathingCapacityAdequate());
    assertFalse(result.getValue().isEmergencyPositivePressureWithinTankLimit());
    assertFalse(result.getValue().allConstraintsSatisfied());
  }

  @Test
  void missingEvidenceAndInvalidRawValuesFailClosedWithoutMutatingInput() {
    Api2000TankVentingScreeningKernel.Input input = baseBuilder().thermalInbreathingRateM3PerS(-0.03)
        .ratedCapacityBasisVerified(false).build();

    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(input, null);

    assertEquals(-0.03, input.getThermalInbreathingRateM3PerS(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void missingDirectionAndEmergencyCasesFailClosed() {
    Api2000TankVentingScreeningKernel.Input input = baseBuilder().liquidWithdrawalRateM3PerS(0.0)
        .thermalInbreathingRateM3PerS(0.0).otherNormalInbreathingRateM3PerS(0.0)
        .totalEmergencyOutbreathingRateM3PerS(0.0).build();

    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void overflowedAggregationFailsClosed() {
    Api2000TankVentingScreeningKernel.Input input = baseBuilder().liquidFillingRateM3PerS(Double.MAX_VALUE)
        .fillingOutbreathingVolumeRatio(Double.MAX_VALUE).build();

    EngineeringCalculationResult<Api2000TankVentingAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void exactEditionAmendmentsAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.API_2000, "6th Ed");
    StandardEdition amended = StandardEdition.of(StandardType.API_2000, "7th Ed", Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.API_2000)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(baseBuilder(historical, "Tank").build(), null).isReady());
    assertFalse(
        kernel.assess(baseBuilder(StandardEdition.defaultEdition(StandardType.API_2000), "Separator").build(), null)
            .isReady());
    assertThrows(IllegalArgumentException.class, () -> Api2000TankVentingScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.API_521), "Tank"));
  }

  @Test
  void documentedExampleIsRunnable() {
    EngineeringCalculationResult<Api2000TankVentingAssessment> result = new Api2000TankVentingScreeningKernel()
        .calculate(validInput(), null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(0.13, ((Double) report.get("requiredNormalOutbreathingRateM3PerS")).doubleValue(), 1.0e-15);
    assertEquals(Boolean.TRUE, report.get("allCallerControlledConstraintsSatisfied"));
  }

  private static Api2000TankVentingScreeningKernel.Input validInput() {
    return baseBuilder().build();
  }

  private static Api2000TankVentingScreeningKernel.Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.API_2000), "Tank");
  }

  private static Api2000TankVentingScreeningKernel.Input.Builder baseBuilder(StandardEdition edition,
      String equipmentType) {
    return Api2000TankVentingScreeningKernel.Input.builder(edition, equipmentType).liquidFillingRateM3PerS(0.1)
        .fillingOutbreathingVolumeRatio(1.05).liquidWithdrawalRateM3PerS(0.08).withdrawalInbreathingVolumeRatio(1.0)
        .thermalOutbreathingRateM3PerS(0.02).thermalInbreathingRateM3PerS(0.03).otherNormalOutbreathingRateM3PerS(0.005)
        .otherNormalInbreathingRateM3PerS(0.005).totalEmergencyOutbreathingRateM3PerS(0.5)
        .normalOutbreathingRatedCapacityM3PerS(0.2).normalInbreathingRatedCapacityM3PerS(0.15)
        .emergencyOutbreathingRatedCapacityM3PerS(0.6).tankMaximumPositiveGaugePressurePa(5000.0)
        .tankMaximumVacuumPressurePa(2000.0).normalOutbreathingRatedGaugePressurePa(3000.0)
        .normalInbreathingRatedVacuumPressurePa(1500.0).emergencyOutbreathingRatedGaugePressurePa(4500.0)
        .flowReferenceTemperatureK(288.15).flowReferencePressurePaAbsolute(101325.0)
        .fixedRoofNonRefrigeratedApplicabilityVerified(true).ventDemandBasisVerified(true)
        .ratedCapacityBasisVerified(true).pressureVacuumBasisVerified(true).normalCombinationBasisVerified(true)
        .emergencyCombinationBasisVerified(true);
  }
}
