package neqsim.process.safety.esd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import neqsim.process.safety.esd.EsdResponseTimeSimulator.EsdResponseTimeResult;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EsdResponseTimeSimulator}.
 *
 * @author ESOL
 * @version 1.0
 */
public class EsdResponseTimeSimulatorTest {

  /**
   * The total response time should be the sum of detection, logic and final-element contributions, and pass when within
   * the budget.
   */
  @Test
  public void testResponseTimeWithinBudget() {
    EsdResponseTimeResult res = new EsdResponseTimeSimulator().setSifTag("ESD-1234")
	.addDetection("PT-1001 high-pressure", 1.0).addLogic("Logic solver scan + 2oo3 vote", 0.5)
	.addValve("ESDV-2001", 0.5, 8.0).setAllowableResponseTimeS(15.0).evaluate();

    assertEquals(1.0, res.getDetectionTimeS(), 1.0e-9);
    assertEquals(0.5, res.getLogicTimeS(), 1.0e-9);
    assertEquals(8.5, res.getFinalElementTimeS(), 1.0e-9);
    assertEquals(10.0, res.getTotalResponseTimeS(), 1.0e-9);
    assertEquals(5.0, res.getMarginS(), 1.0e-9);
    assertTrue(res.isWithinBudget());
  }

  /**
   * A slow valve should push the total beyond the budget and fail the acceptance check.
   */
  @Test
  public void testResponseTimeExceedsBudget() {
    EsdResponseTimeResult res = new EsdResponseTimeSimulator().setSifTag("ESD-9999")
	.addDetection("LT-3001 high-level", 2.0).addLogic("Logic solver", 0.5)
	.addValve("ESDV-3002 (large bore)", 1.0, 20.0).setAllowableResponseTimeS(15.0).evaluate();

    assertEquals(23.5, res.getTotalResponseTimeS(), 1.0e-9);
    assertFalse(res.isWithinBudget());
    assertTrue(res.getMarginS() < 0.0);
  }
}
