package neqsim.process.engineering.calculation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests typed readiness, provenance, and uncertainty calculation contracts. */
class EngineeringCalculationContractTest {

  @Test
  void calculatedResultCarriesContextInputsAndUncertainty() {
    EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("MAX-RATE")
        .simulationFingerprint("abc123").addEvidenceReference("SIM-REV-A").addStandardReference("API 521:2020 5.3")
        .attribute("discipline", "process safety").build();
    CalculationReadiness readiness = CalculationReadiness.builder()
        .addWarning("ENG-REVIEW", "Result has not been independently checked", "Complete discipline verification.")
        .build();

    EngineeringCalculationResult<Double> result = EngineeringCalculationResult
        .<Double>builder("flare-peak", "coupled load envelope", "1.0").context(context).readiness(readiness)
        .status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(Double.valueOf(12.5))
        .input("sourceCount", Integer.valueOf(3))
        .uncertainty(new EngineeringCalculationResult.Uncertainty(11.0, 12.5, 15.0, "kg/s", "case sensitivity"))
        .build();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(result.toMap().containsKey("uncertainty"));
    assertTrue(result.toMap().toString().contains("API 521"));
  }

  @Test
  void readinessBlockerPreventsCalculatedStatus() {
    CalculationReadiness blocked = CalculationReadiness.builder()
        .addBlocker("INPUT-001", "Required input missing", "Provide the input.").build();

    assertThrows(IllegalStateException.class,
        () -> EngineeringCalculationResult.<Double>builder("blocked", "test", "1.0").readiness(blocked)
            .status(EngineeringCalculationResult.Status.CALCULATED).value(Double.valueOf(1.0)).build());
  }
}
