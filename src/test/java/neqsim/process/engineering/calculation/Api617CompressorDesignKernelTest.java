package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.compressor.CompressorCasingDesignCalculator;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Tests the fail-closed common engineering adapter for API 617 casing screening. */
class Api617CompressorDesignKernelTest {
  @Test
  void calculatesFromDefensiveConfigurationCopy() {
    CompressorCasingDesignCalculator configuration = passingConfiguration();
    Api617CompressorDesignKernel.Input input = new Api617CompressorDesignKernel.Input(
        StandardEdition.of(StandardType.API_617, "8th Ed"), "Compressor", configuration);
    configuration.setDesignPressureMPa(50.0);

    EngineeringCalculationResult<Api617CompressorAssessment> result = new Api617CompressorDesignKernel()
        .calculate(input, EngineeringCalculationContext.builder().designCaseId("rated").build());

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(result.getValue().getRequiredWallThicknessMm() > 0.0);
    assertTrue(result.getValue().getSelectedWallThicknessMm() >= result.getValue().getMinimumWallThicknessMm());
    assertEquals("SA-516-70", result.getValue().getMaterialGrade());
    assertTrue(result.getValue().getAppliedStandards().stream().anyMatch(value -> value.contains("API 617 8th Ed")));
    assertThrows(UnsupportedOperationException.class, () -> result.getValue().getAppliedStandards().clear());
    assertEquals(5.0, input.getConfiguration().getDesignPressureMPa(), 1.0e-12);
    input.getConfiguration().setDesignPressureMPa(1.0);
    assertEquals(5.0, input.getConfiguration().getDesignPressureMPa(), 1.0e-12);
  }

  @Test
  void blocksUnsupportedEditionEquipmentAndInvalidPressureBasis() {
    Api617CompressorDesignKernel kernel = new Api617CompressorDesignKernel();
    Api617CompressorDesignKernel.Input oldEdition = new Api617CompressorDesignKernel.Input(
        StandardEdition.of(StandardType.API_617, "7th Ed"), "Compressor", passingConfiguration());
    Api617CompressorDesignKernel.Input wrongEquipment = new Api617CompressorDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_617), "Pump", passingConfiguration());
    CompressorCasingDesignCalculator invalidConfiguration = passingConfiguration();
    invalidConfiguration.setMaxOperatingPressureMPa(6.0);
    Api617CompressorDesignKernel.Input invalid = new Api617CompressorDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_617), "Compressor", invalidConfiguration);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(oldEdition, null).getStatus());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, kernel.applicability(wrongEquipment).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(wrongEquipment, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(invalid, null).getStatus());
    assertFalse(kernel.assess(invalid, null).isReady());
    assertThrows(IllegalArgumentException.class,
        () -> new Api617CompressorDesignKernel.Input(StandardEdition.defaultEdition(StandardType.API_610), "Compressor",
            passingConfiguration()));
  }

  private static CompressorCasingDesignCalculator passingConfiguration() {
    CompressorCasingDesignCalculator calculator = new CompressorCasingDesignCalculator();
    calculator.setDesignPressureMPa(5.0);
    calculator.setMaxOperatingPressureMPa(4.0);
    calculator.setDesignTemperatureC(150.0);
    calculator.setMaxOperatingTemperatureC(100.0);
    calculator.setMinOperatingTemperatureC(-20.0);
    calculator.setCasingInnerDiameterMm(500.0);
    calculator.setCasingLengthMm(1500.0);
    calculator.setMaterialGrade("SA-516-70");
    calculator.setCorrosionAllowanceMm(1.5);
    calculator.setJointEfficiency(0.85);
    calculator.setSuctionNozzleSizeMm(200.0);
    calculator.setDischargeNozzleSizeMm(150.0);
    return calculator;
  }
}
