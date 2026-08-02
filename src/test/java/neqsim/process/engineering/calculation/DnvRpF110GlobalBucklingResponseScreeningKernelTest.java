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

/** Tests the fail-closed DNV-RP-F110 global-buckling response screening boundary. */
class DnvRpF110GlobalBucklingResponseScreeningKernelTest {
  private final DnvRpF110GlobalBucklingResponseScreeningKernel kernel = new DnvRpF110GlobalBucklingResponseScreeningKernel();

  @Test
  void calculatesCallerControlledResponseMarginsWithoutClaimingConformity() {
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = verifiedBuilder()
        .addBucklingCase(bucklingCase("controlled buckle", 0.0, 8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0))
        .addBucklingCase(bucklingCase("buried section", 12000.0, 6.0e6, 10.0e6, 0.004, 0.010, 3.0, 5.0, 40.0, 100.0))
        .build();

    EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result = kernel.calculate(input, null);
    DnvRpF110GlobalBucklingResponseAssessment value = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(2, value.getBucklingCases().size());
    assertEquals(0.8, value.getMaximumCompressiveForceUtilization(), 1.0e-15);
    assertEquals(0.6, value.getMaximumLongitudinalStrainUtilization(), 1.0e-15);
    assertEquals(0.8, value.getMaximumGlobalDisplacementUtilization(), 1.0e-15);
    assertEquals(0.75, value.getMaximumFeedInLengthUtilization(), 1.0e-15);
    assertTrue(value.allConstraintsSatisfied());
    assertTrue(result.toMap().toString().contains("not a DNV-RP-F110 or ST-F101 compliance decision"));
    assertEquals(Boolean.TRUE, value.toMap().get("engineeringApprovalRequired"));
  }

  @Test
  void anExceededCallerControlledLimitRemainsAVisibleCalculatedFinding() {
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = verifiedBuilder()
        .addBucklingCase(bucklingCase("strain governing", 5000.0, 8.0e6, 10.0e6, 0.012, 0.010, 4.0, 5.0, 75.0, 100.0))
        .build();

    EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(-0.002, result.getValue().getBucklingCases().get(0).getLongitudinalStrainMarginFraction(), 1.0e-15);
    assertEquals(1.2, result.getValue().getMaximumLongitudinalStrainUtilization(), 1.0e-15);
    assertFalse(result.getValue().allConstraintsSatisfied());
  }

  @Test
  void missingGlobalStructuralModelEvidenceBlocksTheCalculation() {
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = verifiedBuilder().globalStructuralModelVerified(false)
        .addBucklingCase(bucklingCase("section", 0.0, 8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0)).build();

    EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertNull(result.getValue());
    assertTrue(result.getReadiness().toMap().toString().contains("DNV_RP_F110_GLOBAL_MODEL_NOT_VERIFIED"));
  }

  @Test
  void duplicateLabelsAndInvalidAllowableBlockTheCalculation() {
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = verifiedBuilder()
        .addBucklingCase(bucklingCase("same", 0.0, 8.0e6, 0.0, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0))
        .addBucklingCase(bucklingCase("same", 100.0, 8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0)).build();

    EngineeringCalculationResult<DnvRpF110GlobalBucklingResponseAssessment> result = kernel.calculate(input, null);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    String findings = result.getReadiness().toMap().toString();
    assertTrue(findings.contains("DNV_RP_F110_CASE_LABEL_INVALID"));
    assertTrue(findings.contains("DNV_RP_F110_CASE_VALUES_INVALID"));
  }

  @Test
  void inputDefensivelyCopiesBucklingCases() {
    List<DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase> cases = new ArrayList<DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase>();
    cases.add(bucklingCase("section", 0.0, 8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0));
    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = verifiedBuilder().bucklingCases(cases).build();
    cases.clear();

    assertEquals(1, input.getBucklingCases().size());
    assertThrows(UnsupportedOperationException.class, () -> input.getBucklingCases().clear());
  }

  @Test
  void builderRejectsWrongStandardAndKernelRejectsWrongEquipment() {
    assertThrows(IllegalArgumentException.class, () -> DnvRpF110GlobalBucklingResponseScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F105), "Pipeline"));

    DnvRpF110GlobalBucklingResponseScreeningKernel.Input input = DnvRpF110GlobalBucklingResponseScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F110), "Compressor").pipelineOuterDiameterM(0.3)
        .steelWallThicknessM(0.02)
        .addBucklingCase(bucklingCase("section", 0.0, 8.0e6, 10.0e6, 0.006, 0.010, 4.0, 5.0, 75.0, 100.0))
        .applicabilityVerified(true).operatingEnvelopeAndEffectiveForceVerified(true)
        .pipePropertiesAndAsLaidGeometryVerified(true).pipeSoilInteractionVerified(true)
        .imperfectionTriggerAndStrategyVerified(true).globalStructuralModelVerified(true)
        .designSituationsAndLoadCombinationsVerified(true).localCapacityAndStrainCriteriaVerified(true)
        .uncertaintySensitivityAndBuckleSharingVerified(true)
        .installationInterventionMonitoringAndLifecycleReviewed(true).build();

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(input, null).getStatus());
  }

  private static DnvRpF110GlobalBucklingResponseScreeningKernel.Input.Builder verifiedBuilder() {
    return DnvRpF110GlobalBucklingResponseScreeningKernel.Input
        .builder(StandardEdition.defaultEdition(StandardType.DNV_RP_F110), "Pipeline").pipelineOuterDiameterM(0.3239)
        .steelWallThicknessM(0.0206).applicabilityVerified(true).operatingEnvelopeAndEffectiveForceVerified(true)
        .pipePropertiesAndAsLaidGeometryVerified(true).pipeSoilInteractionVerified(true)
        .imperfectionTriggerAndStrategyVerified(true).globalStructuralModelVerified(true)
        .designSituationsAndLoadCombinationsVerified(true).localCapacityAndStrainCriteriaVerified(true)
        .uncertaintySensitivityAndBuckleSharingVerified(true)
        .installationInterventionMonitoringAndLifecycleReviewed(true);
  }

  private static DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase bucklingCase(String label,
      double distanceM, double force, double forceLimit, double strain, double strainLimit, double displacement,
      double displacementLimit, double requiredFeedIn, double availableFeedIn) {
    return new DnvRpF110GlobalBucklingResponseScreeningKernel.BucklingCase(label, distanceM, "operation",
        DnvRpF110GlobalBucklingResponseScreeningKernel.PipelineConfiguration.EXPOSED,
        DnvRpF110GlobalBucklingResponseScreeningKernel.DesignStrategy.CONTROLLED_BUCKLING, force, forceLimit, strain,
        strainLimit, displacement, displacementLimit, requiredFeedIn, availableFeedIn);
  }
}
