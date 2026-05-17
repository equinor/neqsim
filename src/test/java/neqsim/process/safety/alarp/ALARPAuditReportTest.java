package neqsim.process.safety.alarp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ALARPAuditReportTest {

  @Test
  void cheapEffectiveMeasureAccepted() {
    ALARPAuditReport r = new ALARPAuditReport("Test")
        .setValueOfStatisticalLife(30.0e6)
        .setDisproportionFactor(3.0)
        // ICAF = 50 000 / 1e-3 = 5e7 NOK = 50 MNOK < threshold 90 MNOK
        .addMeasure("Add gas detector", 1.0e-3, 50000.0);
    List<ALARPAuditReport.EvaluationResult> out = r.evaluate();
    assertEquals(1, out.size());
    assertTrue(out.get(0).verdict.startsWith("IMPLEMENT"));
  }

  @Test
  void expensiveLowImpactMeasureRejected() {
    ALARPAuditReport r = new ALARPAuditReport("Test")
        .addMeasure("Replace whole HC system", 1.0e-7, 100.0e6);
    assertTrue(r.evaluate().get(0).verdict.startsWith("REJECT"));
  }
}
