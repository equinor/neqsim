package neqsim.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the ResultProvenance model.
 */
class ResultProvenanceTest {

  @Test
  void testForFlash() {
    ResultProvenance p = ResultProvenance.forFlash("SRK", "TP", "classic");
    assertEquals("NeqSim", p.getEngine());
    assertEquals("SRK", p.getThermodynamicModel());
    assertEquals("TP flash", p.getCalculationType());
    assertTrue(p.isConverged());
    assertFalse(p.getAssumptions().isEmpty());
    assertFalse(p.getLimitations().isEmpty());
    assertNotNull(p.getTimestamp());
  }

  @Test
  void testForProcess() {
    ResultProvenance p = ResultProvenance.forProcess("PR", "classic", 5);
    assertEquals("PR", p.getThermodynamicModel());
    assertEquals("steady-state process simulation", p.getCalculationType());
    assertFalse(p.getAssumptions().isEmpty());
  }

  @Test
  void testForPropertyTable() {
    ResultProvenance p = ResultProvenance.forPropertyTable("SRK", "temperature", 20);
    assertTrue(p.getCalculationType().contains("temperature sweep"));
    assertTrue(p.getCalculationType().contains("20 points"));
  }

  @Test
  void testForPhaseEnvelope() {
    ResultProvenance p = ResultProvenance.forPhaseEnvelope("GERG2008");
    assertEquals("GERG2008", p.getThermodynamicModel());
    assertTrue(p.getCalculationType().contains("phase envelope"));
    assertFalse(p.getLimitations().isEmpty());
  }

  @Test
  void testAddAssumptionAndLimitation() {
    ResultProvenance p = new ResultProvenance();
    p.addAssumption("test assumption");
    p.addLimitation("test limitation");
    p.addValidationPassed("mass_balance");
    assertTrue(p.getAssumptions().contains("test assumption"));
    assertTrue(p.getLimitations().contains("test limitation"));
    assertTrue(p.getValidationsPassed().contains("mass_balance"));
  }

  @Test
  void testConvergenceFlag() {
    ResultProvenance p = new ResultProvenance();
    assertTrue(p.isConverged()); // default true
    p.setConverged(false);
    assertFalse(p.isConverged());
  }

  @Test
  void testGERG2008Limitations() {
    ResultProvenance p = ResultProvenance.forFlash("GERG2008", "TP", "classic");
    boolean hasRangeLimit = false;
    for (String lim : p.getLimitations()) {
      if (lim.contains("natural gas components")) {
        hasRangeLimit = true;
        break;
      }
    }
    assertTrue(hasRangeLimit, "GERG2008 should note component limitations");
  }
}
