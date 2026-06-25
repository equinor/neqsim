package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.fire.PfpDemandCalculator.FireType;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpDemandResult;
import neqsim.process.safety.fire.PfpDemandCalculator.PfpRating;

/**
 * Unit tests for {@link PfpDemandCalculator}.
 *
 * @author ESOL
 * @version 1.0
 */
public class PfpDemandCalculatorTest {

  /**
   * A thin wall in a pool fire reaches critical temperature quickly, so PFP is required and an H-rating is selected.
   */
  @Test
  public void testPoolFireRequiresPfp() {
    PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.012).setFireType(FireType.POOL).evaluate(3600.0);

    assertTrue(res.getBareSteelTimeToCriticalS() < 3600.0);
    assertTrue(res.isPfpRequired());
    assertTrue(res.getRequiredPfpThicknessMm() > 0.0);
    assertEquals(PfpRating.H60, res.getRating());
  }

  /**
   * A jet fire over 120 min should select a J120 rating.
   */
  @Test
  public void testJetFireLongDurationRating() {
    PfpDemandResult res = new PfpDemandCalculator(250.0e3, 0.012).setFireType(FireType.JET).evaluate(7200.0);
    assertTrue(res.isPfpRequired());
    assertEquals(PfpRating.J120, res.getRating());
  }

  /**
   * A very thick wall with a short survival requirement may not need PFP.
   */
  @Test
  public void testThickWallNoPfp() {
    PfpDemandResult res = new PfpDemandCalculator(100.0e3, 0.20).setFireType(FireType.POOL).evaluate(600.0);
    assertFalse(res.isPfpRequired());
    assertEquals(PfpRating.NONE, res.getRating());
    assertEquals(0.0, res.getRequiredPfpThicknessMm(), 1.0e-9);
  }
}
