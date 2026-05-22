package neqsim.process.safety.risk.eta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventTreeAnalyzerTest {

  @Test
  void twoBranchTreeFrequenciesSumToInitiating() {
    EventTreeAnalyzer eta = new EventTreeAnalyzer("Leak", 1.0e-3);
    eta.addBranch("Ignition", 0.1);
    List<EventTreeAnalyzer.Outcome> out = eta.evaluate();
    assertEquals(2, out.size());
    double sum = 0.0;
    for (EventTreeAnalyzer.Outcome o : out) {
      sum += o.frequencyPerYear;
    }
    assertEquals(1.0e-3, sum, 1.0e-12);
  }

  @Test
  void threeBranchTreeHasEightOutcomes() {
    EventTreeAnalyzer eta = new EventTreeAnalyzer("Leak", 1.0e-2);
    eta.addBranch("Immediate ignition", 0.05);
    eta.addBranch("Delayed ignition", 0.10);
    eta.addBranch("ESD success", 0.99);
    List<EventTreeAnalyzer.Outcome> out = eta.evaluate();
    assertEquals(8, out.size());
    double sum = 0.0;
    for (EventTreeAnalyzer.Outcome o : out) {
      sum += o.probability;
    }
    assertEquals(1.0, sum, 1.0e-9);
    assertTrue(eta.report().contains("Event Tree"));
  }
}
