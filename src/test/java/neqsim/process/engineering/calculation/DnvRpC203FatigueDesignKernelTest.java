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

/** Tests edition, curve, spectrum, factor, and Miner-damage boundaries of the C203 kernel. */
class DnvRpC203FatigueDesignKernelTest {
  private final DnvRpC203FatigueDesignKernel kernel = new DnvRpC203FatigueDesignKernel();

  @Test
  void currentEditionCalculatesReviewGatedMinerDamage() {
    DnvRpC203FatigueDesignKernel.Input input = validInput(1.0);

    EngineeringCalculationResult<DnvRpC203FatigueAssessment> result = kernel.calculate(input, null);
    DnvRpC203FatigueAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertNotNull(value);
    assertEquals("DNV-RP-C203 2024-10+AMD:2025-10", value.getStandardEdition());
    assertEquals("PROJECT-CONTROLLED-DEMO", value.getCurveIdentifier());
    assertEquals(0.125, value.getRawMinerDamage(), 1.0e-12);
    assertEquals(0.375, value.getDesignMinerDamage(), 1.0e-12);
    assertEquals(0.375, value.getDamageUtilization(), 1.0e-12);
    assertTrue(value.isWithinDamageLimit());
    assertEquals(20.0 / 0.375, value.getEstimatedDesignFatigueLifeYears(), 1.0e-12);
    assertEquals("high range", value.getGoverningBinLabel());
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
    assertThrows(UnsupportedOperationException.class, () -> value.getBins().clear());
  }

  @Test
  void stressRangeFactorHasSlopeCubedDamageIdentityWithoutMutatingInput() {
    DnvRpC203FatigueDesignKernel.Input baseInput = validInput(1.0);
    double baseDamage = kernel.calculate(baseInput, null).getValue().getRawMinerDamage();
    double factoredDamage = kernel.calculate(validInput(2.0), null).getValue().getRawMinerDamage();

    assertEquals(1.0, baseInput.getStressConcentrationFactor(), 0.0);
    assertEquals(baseDamage * 8.0, factoredDamage, 1.0e-12);
  }

  @Test
  void invalidRawValuesAndMissingEvidenceFailClosed() {
    DnvRpC203FatigueDesignKernel.Input input = baseBuilder().addStressBin("bad", -10.0, -2.0)
        .curveDefinitionVerified(false).stressSpectrumVerified(false).build();

    EngineeringCalculationResult<DnvRpC203FatigueAssessment> result = kernel.calculate(input, null);

    assertEquals(-10.0, input.getStressBins().get(2).getNominalStressRangeMPa(), 0.0);
    assertEquals(-2.0, input.getStressBins().get(2).getNumberOfCycles(), 0.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
  }

  @Test
  void exactEditionAndApplicabilityAreEnforced() {
    StandardEdition historical = StandardEdition.of(StandardType.DNV_RP_C203, "2021");
    StandardEdition amended = StandardEdition.of(StandardType.DNV_RP_C203, "2024-10+AMD:2025-10",
        Arrays.asList("Project amendment A"));

    assertTrue(kernel.supports(StandardEdition.defaultEdition(StandardType.DNV_RP_C203)));
    assertFalse(kernel.supports(historical));
    assertFalse(kernel.supports(amended));
    assertFalse(kernel.assess(copyWithBasis(historical, "Riser"), null).isReady());
    assertFalse(kernel
        .assess(copyWithBasis(StandardEdition.defaultEdition(StandardType.DNV_RP_C203), "Separator"), null).isReady());
    assertThrows(IllegalArgumentException.class, () -> DnvRpC203FatigueDesignKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_ST_F101), "Pipeline"));
  }

  @Test
  void discontinuousBilinearCurveFailsClosed() {
    DnvRpC203FatigueDesignKernel.SnCurve discontinuous = DnvRpC203FatigueDesignKernel.SnCurve.biLinear("DISCONTINUOUS",
        12.0, 3.0, 1.0e7, 12.0, 5.0);
    DnvRpC203FatigueDesignKernel.Input input = baseBuilder().snCurve(discontinuous).build();

    assertFalse(kernel.assess(input, null).isReady());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(input, null).getStatus());
  }

  @Test
  void continuousBilinearCurveUsesTheLowStressBranchBelowTransition() {
    DnvRpC203FatigueDesignKernel.SnCurve curve = DnvRpC203FatigueDesignKernel.SnCurve
        .biLinear("CONTINUOUS-PROJECT-DEMO", 12.0, 3.0, 1.0e6, 16.0, 5.0);
    DnvRpC203FatigueDesignKernel.Input input = baseBuilder().snCurve(curve).build();

    EngineeringCalculationResult<DnvRpC203FatigueAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(1.0e6, result.getValue().getBins().get(0).getCyclesToFailure(), 1.0e-8);
    assertEquals(3.2e7, result.getValue().getBins().get(1).getCyclesToFailure(), 1.0e-6);
    assertEquals(0.10625, result.getValue().getRawMinerDamage(), 1.0e-12);
  }

  @Test
  void documentedExampleIsRunnable() {
    EngineeringCalculationResult<DnvRpC203FatigueAssessment> result = new DnvRpC203FatigueDesignKernel()
        .calculate(validInput(1.0), null);
    Map<String, Object> report = result.getValue().toMap();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(0.375, ((Double) report.get("damageUtilization")).doubleValue(), 1.0e-12);
  }

  private static DnvRpC203FatigueDesignKernel.Input validInput(double stressConcentrationFactor) {
    return baseBuilder().stressConcentrationFactor(stressConcentrationFactor).build();
  }

  private static DnvRpC203FatigueDesignKernel.Input copyWithBasis(StandardEdition edition, String equipmentType) {
    return baseBuilder(edition, equipmentType).build();
  }

  private static DnvRpC203FatigueDesignKernel.Input.Builder baseBuilder() {
    return baseBuilder(StandardEdition.defaultEdition(StandardType.DNV_RP_C203), "Pipeline");
  }

  private static DnvRpC203FatigueDesignKernel.Input.Builder baseBuilder(StandardEdition edition, String equipmentType) {
    return DnvRpC203FatigueDesignKernel.Input.builder(edition, equipmentType)
        .snCurve(DnvRpC203FatigueDesignKernel.SnCurve.singleSlope("PROJECT-CONTROLLED-DEMO", 12.0, 3.0))
        .addStressBin("high range", 100.0, 1.0e5).addStressBin("moderate range", 50.0, 2.0e5)
        .stressConcentrationFactor(1.0).thicknessCorrectionFactor(1.0).otherStressRangeFactor(1.0)
        .designFatigueFactor(3.0).minerDamageLimit(1.0).assessedExposureYears(20.0).curveDefinitionVerified(true)
        .stressSpectrumVerified(true);
  }
}
