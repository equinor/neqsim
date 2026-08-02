package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import org.junit.jupiter.api.Test;

/** Tests the fail-closed DNV-RP-F114 pipe-soil interaction screening boundary. */
class DnvRpF114PipeSoilInteractionScreeningKernelTest {
  private final DnvRpF114PipeSoilInteractionScreeningKernel kernel = new DnvRpF114PipeSoilInteractionScreeningKernel();

  @Test
  void calculatesCallerControlledMarginsWithoutClaimingConformity() {
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = verifiedBuilder()
        .addInteractionCase(interactionCase("section 1", 0.0, 200.0, 500.0, 80.0, 160.0, 120.0, 240.0))
        .addInteractionCase(interactionCase("section 2", 12000.0, 300.0, 600.0, 100.0, 250.0, 220.0, 275.0)).build();

    EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result = kernel.calculate(input, null);
    DnvRpF114PipeSoilInteractionAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(2, value.getInteractionCases().size());
    assertEquals(300.0, value.getMinimumVerticalMarginNPerM(), 0.0);
    assertEquals(80.0, value.getMinimumAxialMarginNPerM(), 0.0);
    assertEquals(55.0, value.getMinimumLateralMarginNPerM(), 0.0);
    assertEquals(0.8, value.getMaximumLateralUtilization(), 1.0e-15);
    assertTrue(value.allConstraintsSatisfied());
    assertTrue(result.toMap().toString().contains("not a DNV-RP-F114 compliance decision"));
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void anExceededCallerControlledResistanceRemainsAVisibleCalculatedFinding() {
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = verifiedBuilder()
        .addInteractionCase(interactionCase("lateral governing", 5000.0, 200.0, 500.0, 80.0, 160.0, 300.0, 250.0))
        .build();

    EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(-50.0, result.getValue().getMinimumLateralMarginNPerM(), 0.0);
    assertEquals(1.2, result.getValue().getMaximumLateralUtilization(), 1.0e-15);
    assertFalse(result.getValue().allConstraintsSatisfied());
  }

  @Test
  void missingGeotechnicalEvidenceBlocksTheCalculation() {
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = verifiedBuilder().siteInvestigationVerified(false)
        .addInteractionCase(interactionCase("section", 0.0, 100.0, 200.0, 50.0, 100.0, 70.0, 140.0)).build();

    EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertTrue(result.getReadiness().toMap().toString().contains("DNV_RP_F114_SITE_INVESTIGATION_NOT_VERIFIED"));
  }

  @Test
  void duplicateLabelsAndInvalidResistanceBlockTheCalculation() {
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = verifiedBuilder()
        .addInteractionCase(interactionCase("same", 0.0, 100.0, 0.0, 50.0, 100.0, 70.0, 140.0))
        .addInteractionCase(interactionCase("same", 100.0, 100.0, 200.0, 50.0, 100.0, 70.0, 140.0)).build();

    EngineeringCalculationResult<DnvRpF114PipeSoilInteractionAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    String findings = result.getReadiness().toMap().toString();
    assertTrue(findings.contains("DNV_RP_F114_CASE_LABEL_INVALID"));
    assertTrue(findings.contains("DNV_RP_F114_CASE_VALUES_INVALID"));
  }

  @Test
  void inputDefensivelyCopiesInteractionCases() {
    List<DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase> cases = new ArrayList<DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase>();
    cases.add(interactionCase("section", 0.0, 100.0, 200.0, 50.0, 100.0, 70.0, 140.0));
    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = verifiedBuilder().interactionCases(cases).build();
    cases.clear();

    assertEquals(1, input.getInteractionCases().size());
    assertThrows(UnsupportedOperationException.class, () -> input.getInteractionCases().clear());
  }

  @Test
  void builderRejectsWrongStandardAndKernelRejectsWrongEquipment() {
    assertThrows(IllegalArgumentException.class, () -> DnvRpF114PipeSoilInteractionScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline"));

    DnvRpF114PipeSoilInteractionScreeningKernel.Input input = DnvRpF114PipeSoilInteractionScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F114), "Compressor").pipelineOuterDiameterM(0.3)
        .submergedWeightNPerM(1000.0)
        .addInteractionCase(interactionCase("section", 0.0, 100.0, 200.0, 50.0, 100.0, 70.0, 140.0))
        .applicabilityVerified(true).siteInvestigationVerified(true).soilModelVerified(true)
        .pipelineConfigurationVerified(true).installationHistoryVerified(true).cyclicDrainageRateEffectsVerified(true)
        .loadDisplacementAndResistanceVerified(true).uncertaintyAndVariabilityVerified(true)
        .designActionsAndAcceptanceCriteriaVerified(true).interfacesAndLifecycleReviewed(true).build();

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(input, null).getStatus());
  }

  private static DnvRpF114PipeSoilInteractionScreeningKernel.Input.Builder verifiedBuilder() {
    return DnvRpF114PipeSoilInteractionScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F114), "Pipeline").pipelineOuterDiameterM(0.3239)
        .submergedWeightNPerM(1200.0).applicabilityVerified(true).siteInvestigationVerified(true)
        .soilModelVerified(true).pipelineConfigurationVerified(true).installationHistoryVerified(true)
        .cyclicDrainageRateEffectsVerified(true).loadDisplacementAndResistanceVerified(true)
        .uncertaintyAndVariabilityVerified(true).designActionsAndAcceptanceCriteriaVerified(true)
        .interfacesAndLifecycleReviewed(true);
  }

  private static DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase interactionCase(String label,
      double distanceM, double verticalDemand, double verticalResistance, double axialDemand, double axialResistance,
      double lateralDemand, double lateralResistance) {
    return new DnvRpF114PipeSoilInteractionScreeningKernel.InteractionCase(label, distanceM, "operation",
        verticalDemand, verticalResistance, axialDemand, axialResistance, lateralDemand, lateralResistance);
  }
}
