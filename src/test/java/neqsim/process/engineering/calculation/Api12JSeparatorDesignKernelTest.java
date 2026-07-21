package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.mechanicaldesign.designstandards.StandardApplicability;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Tests unit-aware, fail-closed API 12J separator screening. */
class Api12JSeparatorDesignKernelTest {
  @Test
  void passingSiAndMicrometreInputsAreEquivalent() {
    Api12JSeparatorDesignKernel kernel = new Api12JSeparatorDesignKernel();
    StandardEdition edition = StandardEdition.of(StandardType.API_12J, "8th Ed");
    Api12JSeparatorDesignKernel.Input micrometre = new Api12JSeparatorDesignKernel.Input(edition, "Separator", 80.0,
        Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false);
    Api12JSeparatorDesignKernel.Input si = new Api12JSeparatorDesignKernel.Input(edition, "Separator", 80.0e-6,
        Api12JSeparatorDesignKernel.DiameterUnit.METRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false);

    Api12JSeparatorAssessment first = kernel.calculate(micrometre, null).getValue();
    Api12JSeparatorAssessment second = kernel.calculate(si, null).getValue();

    assertTrue(first.areAllScreeningCriteriaPassing());
    assertEquals(first.getGravityCutDiameterMicrometre(), second.getGravityCutDiameterMicrometre(), 1.0e-12);
    assertEquals(first.getKFactorUtilization(), second.getKFactorUtilization(), 1.0e-12);
    assertTrue(first.toMap().containsKey("engineeringApprovalRequired"));
  }

  @Test
  void nonPassingCriteriaRemainCalculatedFindings() {
    Api12JSeparatorDesignKernel.Input input = new Api12JSeparatorDesignKernel.Input(
        StandardEdition.of(StandardType.API_12J, "8th Ed"), "ThreePhaseSeparator", 150.0,
        Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.30, false, 200.0,
        Api12JSeparatorDesignKernel.Orientation.VERTICAL, true);

    EngineeringCalculationResult<Api12JSeparatorAssessment> result = new Api12JSeparatorDesignKernel().calculate(input,
        null);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertFalse(result.getValue().areAllScreeningCriteriaPassing());
    assertFalse(result.getValue().isGasLiquidSectionPassing());
    assertFalse(result.getValue().isLiquidSectionPassing());
  }

  @Test
  void blocksUnsupportedEditionEquipmentAndInvalidInputs() {
    Api12JSeparatorDesignKernel kernel = new Api12JSeparatorDesignKernel();
    Api12JSeparatorDesignKernel.Input oldEdition = new Api12JSeparatorDesignKernel.Input(
        StandardEdition.of(StandardType.API_12J, "7th Ed"), "Separator", 80.0,
        Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false);
    Api12JSeparatorDesignKernel.Input wrongEquipment = new Api12JSeparatorDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_12J), "Pump", 80.0,
        Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false);
    Api12JSeparatorDesignKernel.Input invalid = new Api12JSeparatorDesignKernel.Input(
        StandardEdition.defaultEdition(StandardType.API_12J), "Separator", Double.NaN,
        Api12JSeparatorDesignKernel.DiameterUnit.METRE, -1.0, false, 0.0,
        Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false);

    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(oldEdition, null).getStatus());
    assertEquals(StandardApplicability.Status.NOT_APPLICABLE, kernel.applicability(wrongEquipment).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(wrongEquipment, null).getStatus());
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, kernel.calculate(invalid, null).getStatus());
    assertThrows(IllegalArgumentException.class,
        () -> new Api12JSeparatorDesignKernel.Input(StandardEdition.defaultEdition(StandardType.API_610), "Separator",
            80.0, Api12JSeparatorDesignKernel.DiameterUnit.MICROMETRE, 0.08, false, 240.0,
            Api12JSeparatorDesignKernel.Orientation.HORIZONTAL, false));
  }
}
