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

/** Tests the edition, readiness, immutability, and output boundary of the M-506 adapter. */
class NorsokM506CorrosionDesignKernelTest {
  private final NorsokM506CorrosionDesignKernel kernel = new NorsokM506CorrosionDesignKernel();

  @Test
  void currentEditionCalculatesReviewGatedTypedResult() {
    NorsokM506CorrosionDesignKernel.Input input = validInput(0.0);

    EngineeringCalculationResult<NorsokM506CorrosionAssessment> result = kernel.calculate(input, null);
    NorsokM506CorrosionAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("NORSOK-M-506 2017", value.getStandardEdition());
    assertEquals(2.0, value.getCO2PartialPressureBar(), 1.0e-12);
    assertEquals(4.2, value.getEffectivePH(), 1.0e-12);
    assertTrue(value.getCorrectedCorrosionRateMmPerYear() > 0.0);
    assertEquals(25.0 * value.getCorrectedCorrosionRateMmPerYear(), value.getProjectedUniformWallLossMm(), 1.0e-12);
    assertFalse(value.isCalculatedPHUsed());
    assertFalse(value.isFeCO3FilmExtensionUsed());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
    assertEquals(Boolean.TRUE, result.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void inputRetainsRawInvalidValuesAndCalculationFailsClosed() {
    NorsokM506CorrosionDesignKernel.Input input = NorsokM506CorrosionDesignKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.NORSOK_M_506), "Pipeline").temperatureC(60.0)
        .totalPressureBara(100.0).co2MoleFraction(1.2).actualPH(4.2).flowVelocityMPerS(3.0).pipeInternalDiameterM(0.254)
        .liquidDensityKgPerM3(1000.0).liquidDynamicViscosityPaS(0.001).inhibitorEfficiencyFraction(-0.1)
        .exposureYears(25.0).build();

    EngineeringCalculationResult<NorsokM506CorrosionAssessment> result = kernel.calculate(input, null);

    assertEquals(1.2, input.getCO2MoleFraction(), 0.0);
    assertEquals(-0.1, input.getInhibitorEfficiencyFraction(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertFalse(result.getReadiness().isReady());
  }

  @Test
  void editionAndApplicabilityAreExact() {
    StandardEdition historical = StandardEdition.of(StandardType.NORSOK_M_506, "2005");
    StandardEdition amended = StandardEdition.of(StandardType.NORSOK_M_506, "2017",
        Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.NORSOK_M_506)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(copyWithBasis(historical, "Pipeline"), null).isReady());
    assertFalse(kernel
        .assess(copyWithBasis(StandardEdition.defaultEdition(StandardType.NORSOK_M_506), "Separator"), null).isReady());
    assertThrows(IllegalArgumentException.class, () -> NorsokM506CorrosionDesignKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.API_521), "Pipeline"));
  }

  @Test
  void inhibitorFactorIsAppliedWithoutMutatingTheUninhibitedCase() {
    NorsokM506CorrosionDesignKernel.Input untreatedInput = validInput(0.0);
    double untreated = kernel.calculate(untreatedInput, null).getValue().getCorrectedCorrosionRateMmPerYear();
    double treated = kernel.calculate(validInput(0.8), null).getValue().getCorrectedCorrosionRateMmPerYear();

    assertEquals(0.0, untreatedInput.getInhibitorEfficiencyFraction(), 0.0);
    assertEquals(untreated * 0.2, treated, untreated * 1.0e-12);
  }

  @Test
  void documentedExampleIsRunnable() {
    StandardEdition edition = StandardEdition.defaultEdition(StandardType.NORSOK_M_506);
    NorsokM506CorrosionDesignKernel.Input input = NorsokM506CorrosionDesignKernel.Input.builder(edition, "Pipeline")
        .temperatureC(60.0).totalPressureBara(100.0).co2MoleFraction(0.02).actualPH(4.2).flowVelocityMPerS(3.0)
        .pipeInternalDiameterM(0.254).liquidDensityKgPerM3(1000.0).liquidDynamicViscosityPaS(0.001)
        .inhibitorEfficiencyFraction(0.8).exposureYears(25.0).build();

    EngineeringCalculationResult<NorsokM506CorrosionAssessment> result = new NorsokM506CorrosionDesignKernel()
        .calculate(input, null);
    NorsokM506CorrosionAssessment assessment = result.getValue();
    Map<String, Object> report = assessment.toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(((Double) report.get("correctedCorrosionRateMmPerYear")).doubleValue() > 0.0);
  }

  private static NorsokM506CorrosionDesignKernel.Input validInput(double inhibitorEfficiency) {
    return copyWithBasis(StandardEdition.defaultEdition(StandardType.NORSOK_M_506), "Pipeline", inhibitorEfficiency);
  }

  private static NorsokM506CorrosionDesignKernel.Input copyWithBasis(StandardEdition edition, String equipmentType) {
    return copyWithBasis(edition, equipmentType, 0.0);
  }

  private static NorsokM506CorrosionDesignKernel.Input copyWithBasis(StandardEdition edition, String equipmentType,
      double inhibitorEfficiency) {
    return NorsokM506CorrosionDesignKernel.Input.builder(edition, equipmentType).temperatureC(60.0)
        .totalPressureBara(100.0).co2MoleFraction(0.02).actualPH(4.2).flowVelocityMPerS(3.0)
        .pipeInternalDiameterM(0.254).liquidDensityKgPerM3(1000.0).liquidDynamicViscosityPaS(0.001)
        .inhibitorEfficiencyFraction(inhibitorEfficiency).exposureYears(25.0).build();
  }
}
