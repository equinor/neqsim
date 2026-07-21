package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Tests unit-aware, fail-closed API 526 standard-orifice selection. */
class Api526OrificeSelectionKernelTest {
  private static final double SQUARE_METRES_PER_SQUARE_INCH = 6.4516e-4;

  @Test
  void registryExposesScreeningKernel() {
    EquipmentDesignKernelRegistry.Lookup lookup = EquipmentDesignKernelRegistry.lookup(StandardType.API_526);

    assertEquals(EquipmentDesignKernelRegistry.Status.IMPLEMENTED, lookup.getStatus());
    assertEquals("Api526OrificeSelectionKernel", lookup.getImplementationClassName());
    assertEquals(StandardSupportLevel.SCREENING, lookup.getMaturity());
  }

  @Test
  void exactBoundaryAndUnitEquivalentInputsSelectSameOrifice() {
    Api526OrificeSelectionKernel kernel = new Api526OrificeSelectionKernel();
    StandardEdition edition = StandardEdition.defaultEdition(StandardType.API_526);
    Api526OrificeSelectionKernel.Input customary = new Api526OrificeSelectionKernel.Input(edition, "SafetyValve",
        0.503, Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH);
    Api526OrificeSelectionKernel.Input si = new Api526OrificeSelectionKernel.Input(edition, "SafetyReliefValve",
        0.503 * SQUARE_METRES_PER_SQUARE_INCH, Api526OrificeSelectionKernel.AreaUnit.SQUARE_METRE);

    Api526OrificeSelectionAssessment customaryResult = kernel
        .calculate(customary, EngineeringCalculationContext.builder().build()).getValue();
    Api526OrificeSelectionAssessment siResult = kernel
        .calculate(si, EngineeringCalculationContext.builder().build()).getValue();

    assertEquals("G", customaryResult.getSelectedOrifice());
    assertEquals(customaryResult.getSelectedOrifice(), siResult.getSelectedOrifice());
    assertEquals(customaryResult.getRequiredAreaIn2(), siResult.getRequiredAreaIn2(), 1.0e-12);
    assertTrue(customaryResult.isAdequate());
    assertEquals(0.0, customaryResult.getAreaMarginFraction(), 1.0e-12);
    assertTrue(customaryResult.toMap().containsKey("engineeringApprovalRequired"));
  }

  @Test
  void reportsLargestOrificeAsInadequateInsteadOfClaimingSuccess() {
    Api526OrificeSelectionKernel.Input input = new Api526OrificeSelectionKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_526), "SafetyValve", 30.0,
        Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH);

    EngineeringCalculationResult<Api526OrificeSelectionAssessment> result = new Api526OrificeSelectionKernel()
        .calculate(input, EngineeringCalculationContext.builder().build());

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals("T", result.getValue().getSelectedOrifice());
    assertFalse(result.getValue().isAdequate());
    assertTrue(result.getValue().getAreaMarginFraction() < 0.0);
  }

  @Test
  void blocksUnsupportedEditionEquipmentAndInvalidArea() {
    Api526OrificeSelectionKernel kernel = new Api526OrificeSelectionKernel();
    Api526OrificeSelectionKernel.Input oldEdition = new Api526OrificeSelectionKernel.Input(
        StandardEdition.of(StandardType.API_526, "6th Ed"), "SafetyValve", 1.0,
        Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH);
    Api526OrificeSelectionKernel.Input wrongEquipment = new Api526OrificeSelectionKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_526), "Valve", 1.0,
        Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH);
    Api526OrificeSelectionKernel.Input invalidArea = new Api526OrificeSelectionKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_526), "SafetyValve", Double.NaN,
        Api526OrificeSelectionKernel.AreaUnit.SQUARE_METRE);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(oldEdition, EngineeringCalculationContext.builder().build()).getStatus());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, kernel.applicability(wrongEquipment).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(wrongEquipment, EngineeringCalculationContext.builder().build()).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(invalidArea, EngineeringCalculationContext.builder().build()).getStatus());
    assertThrows(IllegalArgumentException.class,
        () -> new Api526OrificeSelectionKernel.Input(StandardEdition.defaultEdition(StandardType.API_521),
            "SafetyValve", 1.0, Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH));
  }
}
