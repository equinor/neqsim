package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardSupportLevel;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.Api610PumpType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.AssessmentStatus;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.BearingType;
import neqsim.process.mechanicaldesign.pump.PumpApi610DesignCalculator.DataSource;

/** Tests the common engineering-kernel adapter for API 610 screening. */
class PumpApi610DesignKernelTest {
  @Test
  void registryReturnsExplicitImplementationStatus() {
    EquipmentDesignKernelRegistry.Lookup implemented =
        EquipmentDesignKernelRegistry.lookup(StandardType.API_610);
    EquipmentDesignKernelRegistry.Lookup absent = EquipmentDesignKernelRegistry.lookup(StandardType.API_660);

    assertEquals(EquipmentDesignKernelRegistry.Status.IMPLEMENTED, implemented.getStatus());
    assertEquals("PumpApi610DesignKernel", implemented.getImplementationClassName());
    assertEquals(StandardSupportLevel.SCREENING, implemented.getMaturity());
    assertTrue(implemented.supports(StandardEdition.defaultEdition(StandardType.API_610)));
    assertTrue(implemented.requireKernel() instanceof PumpApi610DesignKernel);
    assertEquals(EquipmentDesignKernelRegistry.Status.NOT_IMPLEMENTED, absent.getStatus());
    assertEquals("None", absent.getImplementationClassName());
    assertThrows(IllegalStateException.class, absent::requireKernel);
  }

  @Test
  void calculatesWithoutMutatingLegacyConfiguration() {
    PumpApi610DesignCalculator legacyConfiguration = passingConfiguration();
    StandardEdition edition = StandardEdition.of(StandardType.API_610, "13th Ed",
        Collections.singletonList("Project amendment A"));
    PumpApi610DesignKernel.Input input =
        new PumpApi610DesignKernel.Input(edition, "Pump", legacyConfiguration);
    PumpApi610DesignKernel kernel = new PumpApi610DesignKernel();
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("rated")
        .addStandardReference(edition.getDisplayName()).build();

    EngineeringCalculationResult<PumpApi610DesignAssessment> result = kernel.calculate(input, context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertEquals(AssessmentStatus.PASS, result.getValue().getAssessmentStatus());
    assertEquals("13th Ed", result.getValue().getStandardEdition());
    assertEquals(30.0, result.getValue().getSelectedDriverPowerKw(), 1.0e-12);
    assertTrue(result.getValue().toMap().containsKey("checks"));
    assertThrows(UnsupportedOperationException.class, () -> result.getValue().getChecks().clear());
    assertEquals(AssessmentStatus.NOT_EVALUATED, legacyConfiguration.getAssessmentStatus());
    assertTrue(legacyConfiguration.getChecks().isEmpty());
    assertEquals(StandardType.API_610, kernel.standard());
    assertEquals(StandardSupportLevel.SCREENING, kernel.maturity());
    assertEquals(StandardApplicability.Status.APPLICABLE, kernel.applicability(input).getStatus());
  }

  @Test
  void blocksInapplicableEquipmentAndRejectsWrongStandard() {
    PumpApi610DesignKernel kernel = new PumpApi610DesignKernel();
    PumpApi610DesignKernel.Input separatorInput = new PumpApi610DesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_610), "Separator", passingConfiguration());

    EngineeringCalculationResult<PumpApi610DesignAssessment> result =
        kernel.calculate(separatorInput, EngineeringCalculationContext.builder().build());

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, result.getStatus());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, kernel.applicability(separatorInput).getStatus());
    PumpApi610DesignKernel.Input oldEditionInput = new PumpApi610DesignKernel.Input(
        StandardEdition.of(StandardType.API_610, "12th Ed"), "Pump", passingConfiguration());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        kernel.calculate(oldEditionInput, EngineeringCalculationContext.builder().build()).getStatus());
    assertThrows(IllegalArgumentException.class,
        () -> new PumpApi610DesignKernel.Input(StandardEdition.defaultEdition(StandardType.API_617), "Pump",
            passingConfiguration()));
  }

  private static PumpApi610DesignCalculator passingConfiguration() {
    PumpApi610DesignCalculator calculator = new PumpApi610DesignCalculator();
    calculator.setPumpType(Api610PumpType.OH2);
    calculator.setDutyPoint(100.0, 80.0, 3000.0, 850.0, 25.0);
    calculator.setBepPoint(100.0, 80.0, DataSource.VENDOR_CURVE);
    calculator.setNpsh(6.0, 4.0, DataSource.VENDOR_CURVE);
    calculator.setPressureBasis(5.0, 20.0, 90.0, DataSource.VENDOR_CURVE);
    calculator.setHydrostaticTestPressureBara(30.0);
    calculator.setDriverCriteria(1.10, new double[] { 22.0, 30.0, 37.0 });
    calculator.setBearingData(BearingType.BALL, 100.0, 5.0);
    calculator.setMechanicalEvidence(0.03, 4000.0, 0.8, 2.5);
    return calculator;
  }
}
