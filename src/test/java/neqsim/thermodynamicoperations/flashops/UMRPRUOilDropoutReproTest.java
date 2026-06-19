package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression test for the UMR-PRU trace oil dropout bug.
 *
 * <p>
 * A lean gas with trace heavy ends drops a tiny amount of retrograde oil below its dew point. The multiphase TP flash
 * transiently splits this trace oil into two numerically identical duplicate "OIL" phases (the enhanced post-flash
 * stability re-check seeds a duplicate, and the bounded rerun divides the oil equally between the two). When the
 * duplicate phase was removed via {@code removePhaseKeepTotalComposition}, its phase fraction was discarded and
 * renormalised into the gas phase, halving the trace oil dropout below a temperature-dependent stability threshold. The
 * corrected code merges the duplicate's phase fraction into the surviving phase before removal, so the dropout curve is
 * smooth and monotonic in temperature.
 * </p>
 */
public class UMRPRUOilDropoutReproTest {

  private static final String[] NAMES = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane",
      "i-pentane", "n-pentane", "2-m-C5", "3-m-C5", "n-hexane", "c-hexane", "n-heptane", "benzene", "n-octane", "c-C7",
      "toluene", "n-nonane", "c-C8", "m-Xylene", "nC10", "nC11", "nC12" };
  private static final double[] Z = { 0.00959, 0.00634, 0.946, 0.0265, 0.00416, 0.00159, 0.00103, 0.000842, 0.000268,
      0.000418, 0.000127, 0.000216, 0.000857, 0.00016, 2.14e-05, 4.92e-05, 0.000575, 5.5e-05, 4.17e-05, 7.85e-05,
      3.73e-05, 4.69e-05 * 2, 7.61e-06 * 2, 1e-6 * 2 };

  /**
   * Run a TP flash and return the total liquid (oil) phase fraction, asserting overall mass balance.
   *
   * @param tC    temperature in degrees Celsius
   * @param pBara pressure in bara
   * @return the summed oil/liquid phase fraction (beta)
   */
  private static double oilBeta(double tC, double pBara) {
    SystemInterface sys = new SystemUMRPRUMCEos(273.15 + tC, pBara);
    for (int i = 0; i < NAMES.length; i++) {
      sys.addComponent(NAMES[i], Z[i]);
    }
    sys.setMixingRule("HV", "UNIFAC_UMRPRU");
    sys.setMultiPhaseCheck(true);
    ThermodynamicOperations ops = new ThermodynamicOperations(sys);
    ops.TPflash();
    sys.init(3);
    double betaSum = 0.0;
    double oil = 0.0;
    for (int p = 0; p < sys.getNumberOfPhases(); p++) {
      betaSum += sys.getBeta(p);
      if (sys.getPhase(p).getType() == neqsim.thermo.phase.PhaseType.OIL
	  || sys.getPhase(p).getType() == neqsim.thermo.phase.PhaseType.LIQUID) {
	oil += sys.getBeta(p);
      }
    }
    // Mass balance must be preserved regardless of duplicate-phase handling.
    assertEquals(1.0, betaSum, 1.0e-9, "phase fractions must sum to 1 at T=" + tC + "C");
    return oil;
  }

  /**
   * The trace oil dropout must increase smoothly and monotonically as temperature decreases below the dew point. The
   * bug produced a factor-of-two discontinuity around 18-19 C.
   */
  @Test
  public void traceOilDropoutIsMonotonic() {
    double prev = Double.NaN;
    for (int t = 20; t >= 8; t--) {
      double b = oilBeta(t, 78.0);
      if (!Double.isNaN(prev)) {
	// As temperature drops the retrograde oil dropout must not decrease.
	assertTrue(b >= prev - 1.0e-9, "oil dropout decreased as T fell to " + t + "C: " + b + " < " + prev);
      }
      prev = b;
    }
  }

  /**
   * Spot-check the corrected oil dropout values (smooth-curve reference). The buggy code halved these below ~18.5 C
   * (e.g. 8 C gave 4.57e-4 instead of 9.14e-4).
   */
  @Test
  public void traceOilDropoutMatchesReference() {
    assertEquals(9.144142e-04, oilBeta(8.0, 78.0), 5.0e-6, "oil dropout at 8C");
    assertEquals(3.502455e-04, oilBeta(18.0, 78.0), 5.0e-6, "oil dropout at 18C");
    assertEquals(2.582079e-04, oilBeta(20.0, 78.0), 5.0e-6, "oil dropout at 20C");
  }
}
