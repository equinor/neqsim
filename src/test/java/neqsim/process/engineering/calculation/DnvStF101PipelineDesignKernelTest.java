package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportAudit;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101LimitStateCheck;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineAssessment;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput;
import neqsim.process.mechanicaldesign.pipeline.PipeMechanicalDesignCalculator;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesignDataSource;

/** Tests the fail-closed DNV-ST-F101 pipeline screening kernel. */
class DnvStF101PipelineDesignKernelTest {
  @Test
  void completeInputCalculatesEveryLimitStateAndRequiresReview() {
    DnvStF101PipelineDesignKernel kernel = new DnvStF101PipelineDesignKernel();
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("20-inch export line")
        .addStandardReference("DNV-ST-F101 2021 licensed project copy").build();

    EngineeringCalculationResult<DnvStF101PipelineAssessment> result = kernel.calculate(
        input(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM, 0.015, 0.95, 1000.0, 60.0, 100000.0, 0.005), context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(9, result.getValue().getChecks().size());
    assertEquals("20-inch export line", result.getContext().getDesignCaseId());
    assertTrue(result.getReadiness().isReady());
    assertTrue(result.getReadiness().requiresReview());
    assertNotNull(result.getValue().getGoverningCheck());
    assertEquals(Boolean.TRUE, result.getValue().toMap().get("engineeringApprovalRequired"));
    assertTrue(result.toMap().toString().contains("installationBendingStrainFraction"));
    assertTrue(result.getValue().getChecks().stream().anyMatch(
        check -> check.getLimitState() == DnvStF101LimitStateCheck.LimitState.SYSTEM_TEST_PRESSURE_CONTAINMENT));
  }

  @Test
  void missingInputsAndUnsupportedEditionAreBlocked() {
    DnvStF101PipelineDesignKernel kernel = new DnvStF101PipelineDesignKernel();
    DnvStF101PipelineDesignInput missing = DnvStF101PipelineDesignInput.builder().build();
    DnvStF101PipelineDesignInput unsupported = DnvStF101PipelineDesignInput.builder()
        .edition(StandardEdition.of(StandardType.DNV_ST_F101, "2017")).build();

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(missing, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(unsupported, null).getStatus());
    assertFalse(kernel.supports(StandardEdition.of(StandardType.DNV_ST_F101, "2017")));
  }

  @Test
  void safetyClassDeratingAndOvalityActConservatively() {
    DnvStF101PipelineDesignKernel kernel = new DnvStF101PipelineDesignKernel();
    DnvStF101PipelineAssessment low = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.LOW, 0.005, 1.0, 1000.0, 60.0, 100000.0, 0.005));
    DnvStF101PipelineAssessment high = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.HIGH, 0.005, 1.0, 1000.0, 60.0, 100000.0, 0.005));
    DnvStF101PipelineAssessment derated = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.LOW, 0.005, 0.90, 1000.0, 60.0, 100000.0, 0.005));
    DnvStF101PipelineAssessment oval = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.LOW, 0.025, 1.0, 1000.0, 60.0, 100000.0, 0.005));

    assertTrue(utilization(high, DnvStF101LimitStateCheck.LimitState.OPERATING_PRESSURE_CONTAINMENT) > utilization(low,
        DnvStF101LimitStateCheck.LimitState.OPERATING_PRESSURE_CONTAINMENT));
    assertTrue(utilization(derated, DnvStF101LimitStateCheck.LimitState.OPERATING_PRESSURE_CONTAINMENT) > utilization(
        low, DnvStF101LimitStateCheck.LimitState.OPERATING_PRESSURE_CONTAINMENT));
    assertTrue(utilization(oval, DnvStF101LimitStateCheck.LimitState.EXTERNAL_PRESSURE_COLLAPSE) > utilization(low,
        DnvStF101LimitStateCheck.LimitState.EXTERNAL_PRESSURE_COLLAPSE));
  }

  @Test
  void loadFatigueAndInstallationInputsRemainIndependent() {
    DnvStF101PipelineDesignKernel kernel = new DnvStF101PipelineDesignKernel();
    DnvStF101PipelineAssessment base = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM, 0.015, 0.95, 1000.0, 60.0, 100000.0, 0.005));
    DnvStF101PipelineAssessment highLoads = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM, 0.015, 0.95, 3500.0, 60.0, 100000.0, 0.005));
    DnvStF101PipelineAssessment highFatigue = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM, 0.015, 0.95, 1000.0, 100.0, 1000000.0, 0.005));
    DnvStF101PipelineAssessment highInstallation = value(kernel,
        input(DnvStF101PipelineDesignInput.SafetyClass.MEDIUM, 0.015, 0.95, 1000.0, 60.0, 100000.0, 0.025));

    assertTrue(
        utilization(highLoads, DnvStF101LimitStateCheck.LimitState.LOCAL_BUCKLING_LOAD_INTERACTION) > utilization(base,
            DnvStF101LimitStateCheck.LimitState.LOCAL_BUCKLING_LOAD_INTERACTION));
    assertTrue(utilization(highFatigue, DnvStF101LimitStateCheck.LimitState.FATIGUE) > utilization(base,
        DnvStF101LimitStateCheck.LimitState.FATIGUE));
    assertTrue(utilization(highInstallation, DnvStF101LimitStateCheck.LimitState.INSTALLATION_STRAIN) > utilization(
        base, DnvStF101LimitStateCheck.LimitState.INSTALLATION_STRAIN));
  }

  @Test
  void registryExposesScreeningKernel() {
    EquipmentDesignKernelRegistry.Lookup lookup = EquipmentDesignKernelRegistry.lookup(StandardType.DNV_ST_F101);

    assertTrue(lookup.isImplemented());
    assertEquals(StandardSupportLevel.SCREENING, lookup.getMaturity());
    assertEquals("DnvStF101PipelineDesignKernel", lookup.getImplementationClassName());
    assertTrue(lookup.supports(StandardEdition.defaultEdition(StandardType.DNV_ST_F101)));
    assertEquals("DnvStF101PipelineDesignKernel",
        StandardSupportAudit.getSupport(StandardType.DNV_ST_F101).getCalculationImplementation());
  }

  @Test
  void legacyPipelinePathsFailClosedForCurrentStandard() {
    PipelineMechanicalDesign design = new PipelineMechanicalDesign(null);
    design.setDesignStandardCode("DNV-ST-F101");

    assertThrows(IllegalStateException.class, design::readDesignSpecifications);
    assertThrows(IllegalStateException.class, design::calcDesign);
    assertThrows(IllegalArgumentException.class, () -> new PipelineMechanicalDesignDataSource()
        .loadIntoCalculator("X65", "default", "DNV-ST-F101", "Pipeline", new PipeMechanicalDesignCalculator()));
  }

  private static DnvStF101PipelineAssessment value(DnvStF101PipelineDesignKernel kernel,
      DnvStF101PipelineDesignInput input) {
    EngineeringCalculationResult<DnvStF101PipelineAssessment> result = kernel.calculate(input, null);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    return result.getValue();
  }

  private static double utilization(DnvStF101PipelineAssessment assessment,
      DnvStF101LimitStateCheck.LimitState limitState) {
    for (DnvStF101LimitStateCheck check : assessment.getChecks()) {
      if (check.getLimitState() == limitState) {
        return check.getUtilization();
      }
    }
    throw new AssertionError("Missing limit state " + limitState);
  }

  private static DnvStF101PipelineDesignInput input(DnvStF101PipelineDesignInput.SafetyClass safetyClass,
      double ovality, double deratingFactor, double bendingMomentKNm, double fatigueStressRangeMPa,
      double fatigueCycles, double installationBendingStrain) {
    return DnvStF101PipelineDesignInput.builder().safetyClass(safetyClass)
        .fabricationRoute(DnvStF101PipelineDesignInput.FabricationRoute.SEAMLESS).geometry(0.508, 0.028, 0.003)
        .fabrication(0.125, ovality, 0.03, 1.0).material(450.0, 535.0, 207000.0, 0.30)
        .resistanceFactors(deratingFactor, 1.0, 0.96, 1.15).pressures(15.0, 16.5, 3.0, 0.2, 18.5, 3.0)
        .designLoads(1000.0, bendingMomentKNm, 250.0)
        .installationStrains(0.002, installationBendingStrain, 0.003, 0.025).fatigueCurve(12.0, 3.0, 1.0, 3.0)
        .addFatigueBin(fatigueStressRangeMPa, fatigueCycles).addFatigueBin(80.0, 50000.0).build();
  }
}
