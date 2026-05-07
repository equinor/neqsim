package neqsim.process.safety.escalation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EscalationGraphAnalyzerTest {

  @Test
  void simpleDominoPropagation() {
    EscalationGraphAnalyzer g = new EscalationGraphAnalyzer();
    g.addItem("V-100", 12500.0);
    g.addItem("V-101", 12500.0);
    g.addItem("V-102", 12500.0);
    g.addExposure("V-100", "V-101", 25000.0);
    g.addExposure("V-101", "V-102", 8000.0); // below threshold alone
    g.addExposure("V-100", "V-102", 6000.0); // sum = 14000 > threshold once V-100+V-101 fail
    Set<String> failed = g.propagate("V-100");
    assertTrue(failed.contains("V-101"));
    assertTrue(failed.contains("V-102"));
  }

  @Test
  void noPropagationIfBelowThreshold() {
    EscalationGraphAnalyzer g = new EscalationGraphAnalyzer();
    g.addItem("A", 12500.0);
    g.addItem("B", 12500.0);
    g.addExposure("A", "B", 5000.0);
    Set<String> failed = g.propagate("A");
    assertFalse(failed.contains("B"));
  }
}
