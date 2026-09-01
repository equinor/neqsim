package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportAudit;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityAssessment;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityCalculator;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.AssetType;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LateralMethod;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LoadCase;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109StabilityCheck;
import org.junit.jupiter.api.Test;

/** Tests the fail-closed DNV-RP-F109 on-bottom stability screening kernel. */
class DnvRpF109OnBottomStabilityKernelTest {
  @Test
  void completeInputCalculatesStaticAndExternalRoutesAndRequiresReview() {
    DnvRpF109OnBottomStabilityKernel kernel = new DnvRpF109OnBottomStabilityKernel();
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("route section 1")
        .addStandardReference("DNV-RP-F109 2021-05, amended 2025-09, licensed project copy").build();
    DnvRpF109OnBottomStabilityInput input = commonInputBuilder()
        .addLoadCase(staticCase("operating", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8))
        .addLoadCase(externalCase("installation", LateralMethod.EXTERNAL_RESPONSE_0_5D, 0.20, Double.NaN, true,
            "Qualified response model QRM-1, verification record V-17"))
        .build();

    EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment> result = kernel.calculate(input, context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(4, result.getValue().getChecks().size());
    assertEquals("route section 1", result.getContext().getDesignCaseId());
    assertTrue(result.getReadiness().isReady());
    assertTrue(result.getReadiness().requiresReview());
    assertNotNull(result.getValue().getGoverningCheck());
    assertEquals(Boolean.TRUE, result.getValue().toMap().get("engineeringApprovalRequired"));
    assertTrue(result.toMap().toString().contains("responseModelBasis"));
    assertTrue(result.getValue().getChecks().stream()
        .anyMatch(check -> check.getLimitState() == DnvRpF109StabilityCheck.LimitState.LATERAL_DISPLACEMENT));
  }

  @Test
  void missingInputsUnsupportedEditionAndProjectAmendmentsAreBlocked() {
    DnvRpF109OnBottomStabilityKernel kernel = new DnvRpF109OnBottomStabilityKernel();
    DnvRpF109OnBottomStabilityInput missing = DnvRpF109OnBottomStabilityInput.builder().build();
    DnvRpF109OnBottomStabilityInput unsupported = commonInputBuilder()
        .edition(StandardEdition.of(StandardType.DNV_RP_F109, "2017"))
        .addLoadCase(staticCase("operating", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8)).build();
    DnvRpF109OnBottomStabilityInput mismatched = commonInputBuilder().assetType(AssetType.CABLE)
        .addLoadCase(staticCase("operating", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8)).build();
    StandardEdition amended = StandardEdition.of(StandardType.DNV_RP_F109, "2021-05+AMD 2025-09",
        Collections.singletonList("Project amendment A"));
    DnvRpF109OnBottomStabilityInput amendedInput = commonInputBuilder().edition(amended)
        .addLoadCase(staticCase("operating", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8)).build();
    DnvRpF109OnBottomStabilityInput duplicate = commonInputBuilder()
        .addLoadCase(staticCase("duplicate", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8))
        .addLoadCase(staticCase("duplicate", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8)).build();

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(missing, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(unsupported, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(mismatched, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(duplicate, null).getStatus());
    assertFalse(kernel.supports(StandardEdition.of(StandardType.DNV_RP_F109, "2017")));
    assertFalse(kernel.supports(amended));
    assertThrows(IllegalArgumentException.class, () -> DnvRpF109OnBottomStabilityCalculator.calculate(missing));
    assertThrows(IllegalArgumentException.class, () -> DnvRpF109OnBottomStabilityCalculator.calculate(unsupported));
    assertThrows(IllegalArgumentException.class, () -> DnvRpF109OnBottomStabilityCalculator.calculate(amendedInput));
    assertThrows(IllegalArgumentException.class, () -> DnvRpF109OnBottomStabilityCalculator.calculate(duplicate));
  }

  @Test
  void hydrodynamicDemandAndSoilResistanceBehaveConservatively() {
    DnvRpF109OnBottomStabilityAssessment base = value(input(staticCase("base", 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8)));
    DnvRpF109OnBottomStabilityAssessment highVelocity = value(
        input(staticCase("high velocity", 3200.0, 1.0, 90.0, 0.5, 100.0, 0.8)));
    DnvRpF109OnBottomStabilityAssessment axial = value(input(staticCase("axial", 3200.0, 0.6, 0.0, 0.5, 100.0, 0.8)));
    DnvRpF109OnBottomStabilityAssessment heavy = value(input(staticCase("heavy", 4200.0, 0.6, 90.0, 0.5, 100.0, 0.8)));
    DnvRpF109OnBottomStabilityAssessment highResistance = value(
        input(staticCase("soil", 3200.0, 0.6, 90.0, 0.7, 300.0, 0.8)));
    DnvRpF109OnBottomStabilityAssessment highLift = value(
        input(staticCase("lift", 3200.0, 0.6, 90.0, 0.5, 100.0, 1.2)));

    assertTrue(utilization(highVelocity, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY) > utilization(
        base, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY));
    assertTrue(utilization(base, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY) > utilization(axial,
        DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY));
    assertTrue(utilization(heavy, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY) < utilization(base,
        DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY));
    assertTrue(utilization(highResistance, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY) < utilization(
        base, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY));
    assertTrue(utilization(highLift, DnvRpF109StabilityCheck.LimitState.VERTICAL_STABILITY) > utilization(base,
        DnvRpF109StabilityCheck.LimitState.VERTICAL_STABILITY));
    assertTrue(utilization(highLift, DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY) > utilization(base,
        DnvRpF109StabilityCheck.LimitState.ABSOLUTE_LATERAL_STABILITY));
  }

  @Test
  void displacementRoutesUseExplicitLimitsAndFailClosedOnExternalEvidence() {
    DnvRpF109OnBottomStabilityAssessment halfDiameter = value(
        input(externalCase("half diameter", LateralMethod.EXTERNAL_RESPONSE_0_5D, 0.20, Double.NaN, true, "QRM-1")));
    DnvRpF109OnBottomStabilityAssessment tenDiameters = value(
        input(externalCase("ten diameters", LateralMethod.EXTERNAL_RESPONSE_10D, 0.20, Double.NaN, true, "QRM-1")));
    DnvRpF109OnBottomStabilityAssessment userDefined = value(
        input(externalCase("user limit", LateralMethod.EXTERNAL_RESPONSE_USER_DEFINED, 0.20, 0.25, true, "QRM-1")));
    DnvRpF109OnBottomStabilityInput invalidEvidence = input(
        externalCase("invalid", LateralMethod.EXTERNAL_RESPONSE_0_5D, 0.20, Double.NaN, false, null));

    assertEquals(0.20 / (0.5 * 0.508),
        utilization(halfDiameter, DnvRpF109StabilityCheck.LimitState.LATERAL_DISPLACEMENT), 1.0e-12);
    assertEquals(0.20 / (10.0 * 0.508),
        utilization(tenDiameters, DnvRpF109StabilityCheck.LimitState.LATERAL_DISPLACEMENT), 1.0e-12);
    assertEquals(0.20 / (0.25 * 0.508),
        utilization(userDefined, DnvRpF109StabilityCheck.LimitState.LATERAL_DISPLACEMENT), 1.0e-12);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        new DnvRpF109OnBottomStabilityKernel().calculate(invalidEvidence, null).getStatus());
  }

  @Test
  void registryAndSupportAuditExposeScreeningBoundary() {
    EquipmentDesignKernelRegistry.Lookup lookup = EquipmentDesignKernelRegistry.lookup(StandardType.DNV_RP_F109);

    assertTrue(lookup.isImplemented());
    assertEquals(StandardSupportLevel.SCREENING, lookup.getMaturity());
    assertEquals("DnvRpF109OnBottomStabilityKernel", lookup.getImplementationClassName());
    assertTrue(lookup.supports(StandardEdition.defaultEdition(StandardType.DNV_RP_F109)));
    assertEquals("DnvRpF109OnBottomStabilityKernel",
        StandardSupportAudit.getSupport(StandardType.DNV_RP_F109).getCalculationImplementation());
    assertTrue(StandardSupportAudit.getSupport(StandardType.DNV_RP_F109).getLimitation()
        .contains("dynamic response generation"));
  }

  private static DnvRpF109OnBottomStabilityAssessment value(DnvRpF109OnBottomStabilityInput input) {
    EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment> result = new DnvRpF109OnBottomStabilityKernel()
        .calculate(input, null);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    return result.getValue();
  }

  private static double utilization(DnvRpF109OnBottomStabilityAssessment assessment,
      DnvRpF109StabilityCheck.LimitState limitState) {
    for (DnvRpF109StabilityCheck check : assessment.getChecks()) {
      if (check.getLimitState() == limitState) {
        return check.getUtilization();
      }
    }
    throw new AssertionError("Missing limit state " + limitState);
  }

  private static DnvRpF109OnBottomStabilityInput input(LoadCase loadCase) {
    return commonInputBuilder().addLoadCase(loadCase).build();
  }

  private static DnvRpF109OnBottomStabilityInput.Builder commonInputBuilder() {
    return DnvRpF109OnBottomStabilityInput.builder().edition(StandardEdition.defaultEdition(StandardType.DNV_RP_F109))
        .assetType(AssetType.PIPELINE).equipmentType("Pipeline").outsideDiameterM(0.508).seawaterDensityKgM3(1025.0)
        .gravitationalAccelerationMPerS2(9.81)
        .engineeringBasis("Environmental basis ENV-1; soil basis GEO-2; hydrodynamic basis HYD-3");
  }

  private static LoadCase staticCase(String id, double weight, double current, double direction, double friction,
      double passiveResistance, double liftCoefficient) {
    return baseCase(id, weight, current, direction, friction, passiveResistance, liftCoefficient)
        .lateralMethod(LateralMethod.ABSOLUTE_STATIC).build();
  }

  private static LoadCase externalCase(String id, LateralMethod method, double displacement, double allowableDiameters,
      boolean withinRange, String basis) {
    return baseCase(id, 3200.0, 0.6, 90.0, 0.5, 100.0, 0.8).lateralMethod(method)
        .predictedLateralDisplacementM(displacement).allowableDisplacementDiameters(allowableDiameters)
        .responseModelWithinValidatedRange(withinRange).responseModelBasis(basis).build();
  }

  private static LoadCase.Builder baseCase(String id, double weight, double current, double direction, double friction,
      double passiveResistance, double liftCoefficient) {
    return LoadCase.builder().caseId(id).submergedWeightNPerM(weight).currentVelocityMPerS(current)
        .waveVelocityMPerS(1.2).waveAccelerationMPerS2(0.8).currentDirectionRelativeToPipeDeg(direction)
        .waveDirectionRelativeToPipeDeg(direction).dragCoefficient(1.0).liftCoefficient(liftCoefficient)
        .inertiaCoefficient(3.0).horizontalLoadReductionFactor(0.8).verticalLoadReductionFactor(0.7)
        .soilFrictionCoefficient(friction).passiveSoilResistanceNPerM(passiveResistance).horizontalSafetyFactor(1.1)
        .verticalSafetyFactor(1.1).stormDurationHours(3.0).oscillationCount(1000.0);
  }
}
