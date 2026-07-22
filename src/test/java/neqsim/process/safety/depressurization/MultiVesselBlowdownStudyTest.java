package neqsim.process.safety.depressurization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.depressurization.DepressurizationSimulator.DepressurizationResult;
import neqsim.process.safety.depressurization.MultiVesselBlowdownStudy.MultiVesselBlowdownResult;

/**
 * Unit tests for {@link MultiVesselBlowdownStudy}.
 *
 * @author ESOL
 * @version 1.0
 */
public class MultiVesselBlowdownStudyTest {

  /**
   * Build a synthetic depressurization result with a linear decay mass-flow profile.
   *
   * @param peak peak mass flow in kg/s (at t=0)
   * @param durationS blowdown duration in s
   * @param stepS time step in s
   * @return a populated result
   */
  private DepressurizationResult linearDecay(double peak, double durationS, double stepS) {
    DepressurizationResult r = new DepressurizationResult();
    for (double t = 0.0; t <= durationS + 1.0e-9; t += stepS) {
      double q = peak * Math.max(0.0, 1.0 - t / durationS);
      r.time.add(t);
      r.massFlowKgPerS.add(q);
    }
    return r;
  }

  /**
   * Two simultaneous sources should superimpose so the combined peak occurs at t=0 and equals the sum of the peaks.
   */
  @Test
  public void testSimultaneousPeakIsSumOfSources() {
    MultiVesselBlowdownStudy study = new MultiVesselBlowdownStudy().setGridStep(1.0)
        .addSourceResult("HP-sep", linearDecay(40.0, 120.0, 1.0))
        .addSourceResult("Inlet-sep", linearDecay(25.0, 90.0, 1.0));

    MultiVesselBlowdownResult res = study.run();

    assertEquals(65.0, res.getPeakTotalMassFlowKgPerS(), 1.0e-6);
    assertEquals(0.0, res.getPeakTimeS(), 1.0e-9);
    assertEquals(40.0, res.getPeakContributionKgPerS().get("HP-sep"), 1.0e-6);
    assertEquals(25.0, res.getPeakContributionKgPerS().get("Inlet-sep"), 1.0e-6);
  }

  /**
   * A small header should exceed the Mach limit while a large header should pass.
   */
  @Test
  public void testHeaderMachCheck() {
    MultiVesselBlowdownStudy smallHeader = new MultiVesselBlowdownStudy().setGridStep(1.0)
        .addSourceResult("HP-sep", linearDecay(40.0, 120.0, 1.0))
        .addSourceResult("Inlet-sep", linearDecay(25.0, 90.0, 1.0)).setHeader(0.20, 1.5, 288.15, 0.020, 1.30);
    MultiVesselBlowdownResult smallRes = smallHeader.run();
    assertTrue(smallRes.getHeaderMach() > 0.70);
    assertFalse(smallRes.isHeaderMachAcceptable());

    MultiVesselBlowdownStudy largeHeader = new MultiVesselBlowdownStudy().setGridStep(1.0)
        .addSourceResult("HP-sep", linearDecay(40.0, 120.0, 1.0))
        .addSourceResult("Inlet-sep", linearDecay(25.0, 90.0, 1.0)).setHeader(0.80, 1.5, 288.15, 0.020, 1.30);
    MultiVesselBlowdownResult largeRes = largeHeader.run();
    assertTrue(largeRes.getHeaderMach() < 0.70);
    assertTrue(largeRes.isHeaderMachAcceptable());
  }
}
