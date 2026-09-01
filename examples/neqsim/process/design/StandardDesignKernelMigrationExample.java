package neqsim.process.design;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.engineering.calculation.Api526OrificeSelectionAssessment;
import neqsim.process.engineering.calculation.Api526OrificeSelectionKernel;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EquipmentDesignKernel;
import neqsim.process.engineering.calculation.StandardDesignKernelVerificationSuite;
import neqsim.process.engineering.production.EngineeringBenchmarkSuite;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardRegistry;
import neqsim.process.mechanicaldesign.designstandards.StandardSelection;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

/** Demonstrates the fail-closed migration from a standard selection to an executable typed kernel. */
public final class StandardDesignKernelMigrationExample {
  private static final Logger logger = LogManager.getLogger(StandardDesignKernelMigrationExample.class);

  private StandardDesignKernelMigrationExample() {
  }

  public static void main(String[] args) {
    StandardSelection selection =
        StandardSelection.historical(StandardEdition.of(StandardType.API_526, "7th Ed"));
    EquipmentDesignKernel<?, ?> selected = StandardRegistry.requireDesignKernel(selection);
    if (!(selected instanceof Api526OrificeSelectionKernel)) {
      throw new IllegalStateException("The API 526 selection did not resolve to the expected kernel");
    }

    Api526OrificeSelectionKernel kernel = (Api526OrificeSelectionKernel) selected;
    Api526OrificeSelectionKernel.Input input = new Api526OrificeSelectionKernel.Input(selection.getEdition(),
        "SafetyValve", 0.503, Api526OrificeSelectionKernel.AreaUnit.SQUARE_INCH);
    EngineeringCalculationResult<Api526OrificeSelectionAssessment> result = kernel.calculate(input, null);
    if (result.getStatus() != EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED) {
      throw new IllegalStateException("API 526 calculation was not ready: " + result.getReadiness().toMap());
    }

    Api526OrificeSelectionAssessment assessment = result.getValue();
    logger.info("Selected API 526 orifice: {}", assessment.getSelectedOrifice());
    logger.info("Adequate: {}", Boolean.valueOf(assessment.isAdequate()));

    EngineeringBenchmarkSuite.Report regression = StandardDesignKernelVerificationSuite.evaluateRegression();
    if (!regression.areAllBenchmarksPassed()) {
      throw new IllegalStateException("Standard-kernel regression failed: " + regression.getFailedBenchmarkIds());
    }
    logger.info("Numeric regression passed; independent method qualification is still required.");
  }
}
