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

/** Tests edition, composition, profile, limits, and evidence boundaries of the DNV-RP-F104 kernel. */
class DnvRpF104Co2PipelineEnvelopeScreeningKernelTest {
  private final DnvRpF104Co2PipelineEnvelopeScreeningKernel kernel = new DnvRpF104Co2PipelineEnvelopeScreeningKernel();

  @Test
  void currentEditionCalculatesCallerControlledCompositionAndEnvelopeMargins() {
    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(validInput(), null);
    DnvRpF104Co2PipelineEnvelopeAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("DNV-RP-F104 2021-02+AMD:2021-09", value.getStandardEdition());
    assertEquals(0.01, value.getCo2MoleFractionMargin(), 1.0e-15);
    assertEquals(0.0001, value.getWaterMoleFractionMargin(), 1.0e-15);
    assertEquals(3, value.getOperatingPoints().size());
    assertEquals(1.0e6, value.getMinimumSinglePhasePressureMarginPa(), 0.0);
    assertEquals(1.0e6, value.getMinimumMaximumAllowableOperatingPressureMarginPa(), 0.0);
    assertEquals(9.85, value.getMinimumLowTemperatureMarginK(), 1.0e-12);
    assertEquals(30.0, value.getMinimumHighTemperatureMarginK(), 1.0e-12);
    assertTrue(value.allConstraintsSatisfied());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void profileOutsideBoundaryIsCalculatedAsAVisibleFinding() {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = baseBuilder()
        .addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6))
        .addOperatingPoint(point("low margin", 100000.0, 8.0e6, 283.15, 9.5e6)).build();

    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(result.getValue());
    assertEquals(-1.5e6, result.getValue().getMinimumSinglePhasePressureMarginPa(), 0.0);
    assertFalse(result.getValue().getOperatingPoints().get(1).allConstraintsSatisfied());
    assertFalse(result.getValue().allConstraintsSatisfied());
  }

  @Test
  void compositionOutsideProjectLimitsIsAResultNotAReadinessBlocker() {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = baseBuilder().co2MoleFraction(0.96)
        .waterMoleFraction(0.0003).otherImpuritiesWithinProjectSpecification(false)
        .addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6)).build();

    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertFalse(result.getValue().isCo2FractionWithinProjectSpecification());
    assertFalse(result.getValue().isWaterFractionWithinProjectSpecification());
    assertFalse(result.getValue().areOtherImpuritiesWithinProjectSpecification());
    assertFalse(result.getValue().allConstraintsSatisfied());
  }

  @Test
  void missingThermodynamicEvidenceFailsClosed() {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = baseBuilder().thermodynamicModelVerified(false)
        .addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6)).build();

    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertFalse(result.getReadiness().isReady());
  }

  @Test
  void duplicateLabelsAndUnorderedDistancesFailClosed() {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = baseBuilder()
        .addOperatingPoint(point("same", 1000.0, 14.0e6, 293.15, 10.0e6))
        .addOperatingPoint(point("same", 500.0, 13.0e6, 290.15, 10.0e6)).build();

    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void exactEditionAmendmentsAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.DNV_RP_F104, "2017-08");
    StandardEdition amended = StandardEdition.of(StandardType.DNV_RP_F104, "2021-02+AMD:2021-09",
        Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F104)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(
        baseBuilder(historical, "Pipeline").addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6)).build(),
        null).isReady());
    assertFalse(kernel.assess(baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F104), "Separator")
        .addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6)).build(), null).isReady());
    assertThrows(IllegalArgumentException.class, () -> DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline"));
  }

  @Test
  void operatingPointListIsDefensivelyCopiedAndDocumentedExampleIsRunnable() {
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint inlet = point("inlet", 0.0, 14.0e6, 293.15, 10.0e6);
    DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input input = baseBuilder().operatingPoints(Arrays.asList(inlet))
        .build();
    EngineeringCalculationResult<DnvRpF104Co2PipelineEnvelopeAssessment> result = kernel.calculate(input, null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(1, input.getOperatingPoints().size());
    assertThrows(UnsupportedOperationException.class, () -> input.getOperatingPoints().add(inlet));
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(Boolean.TRUE, report.get("allCallerControlledConstraintsSatisfied"));
    assertEquals("CALLER_CONTROLLED_CO2_TRANSPORT_ENVELOPE_SCREEN", report.get("scope"));
  }

  private static DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input validInput() {
    return baseBuilder().addOperatingPoint(point("inlet", 0.0, 14.0e6, 293.15, 10.0e6))
        .addOperatingPoint(point("midline", 50000.0, 12.0e6, 288.15, 9.0e6))
        .addOperatingPoint(point("outlet", 100000.0, 10.5e6, 283.0, 9.5e6)).build();
  }

  private static DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_F104), "Pipeline");
  }

  private static DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input.Builder baseBuilder(StandardEdition edition,
      String equipmentType) {
    return DnvRpF104Co2PipelineEnvelopeScreeningKernel.Input.builder(edition, equipmentType).co2MoleFraction(0.98)
        .minimumCo2MoleFraction(0.97).waterMoleFraction(0.0001).maximumWaterMoleFraction(0.0002)
        .otherImpuritiesWithinProjectSpecification(true).designMinimumTemperatureK(273.15)
        .designMaximumTemperatureK(323.15).maximumAllowableOperatingPressurePaAbsolute(15.0e6)
        .co2PipelineApplicabilityVerified(true).compositionAndSpecificationVerified(true)
        .thermodynamicModelVerified(true).singlePhaseBoundaryInterpretationVerified(true).operatingProfileVerified(true)
        .pressureTemperatureLimitsVerified(true).materialsCorrosionAndFractureBasisVerified(true)
        .safetyConstructionOperationsAndRequalificationReviewed(true);
  }

  private static DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint point(String label, double distanceM,
      double pressurePaAbsolute, double temperatureK, double boundaryPaAbsolute) {
    return new DnvRpF104Co2PipelineEnvelopeScreeningKernel.OperatingPoint(label, distanceM, pressurePaAbsolute,
        temperatureK, boundaryPaAbsolute);
  }
}
