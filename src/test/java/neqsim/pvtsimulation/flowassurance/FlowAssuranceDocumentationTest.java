package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening.DeBoerRisk;

/** Verifies the executable quick start in the flow-assurance documentation index. */
class FlowAssuranceDocumentationTest {

  @Test
  void testDeBoerQuickStart() {
    DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(350.0, 150.0, 750.0);

    DeBoerRisk risk = screening.evaluateRisk();
    double riskIndex = screening.calculateRiskIndex();

    assertEquals(DeBoerRisk.MODERATE_PROBLEM, risk);
    assertEquals(1.6, riskIndex, 1.0e-12);
    assertFalse(risk.getDescription().trim().isEmpty());
  }
}
