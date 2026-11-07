package neqsim.pvtsimulation.util;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Candidate;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Posterior;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Exact finite probability fixtures for marginal in-place parameter quantiles.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReservoirHistoryParameterQuantilesTest {
  /**
   * Creates an unchanged-pressure observation, making posterior masses equal the supplied priors.
   *
   * @return frozen synthetic methane history
   */
  private CompositionalReservoirHistory history() {
    SystemInterface fluid = new SystemSrkEos(400.0, 200.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.useVolumeCorrection(false);
    fluid.init(0);
    CalibratedPvtModel model = new CalibratedPvtModel(fluid, fluid, "synthetic quantile fixture", "fixed SRK",
        "identical SRK", "exact finite prior masses; no EOS or reservoir fitting", new String[] {"synthetic"});
    Step step = new Step(100.0, new double[1], new double[1], 200.0, 1.0, false);
    return new CompositionalReservoirHistory(model, "no withdrawal or injection", new String[] {"methane"}, 400.0,
        200.0, new double[] {1.0, 0.0, 0.0}, new Step[] {step});
  }

  /**
   * Creates one complete hypothesis; zero productivity makes capacity irrelevant to this fixture's likelihood.
   *
   * @param history frozen common observations
   * @param volume initial pore volume in m3
   * @param water initial water volume in reservoir m3, including explicit infinity
   * @param mass positive relative joint prior mass
   * @return complete finite scenario
   */
  private Candidate candidate(CompositionalReservoirHistory history, double volume, double water, double mass) {
    return new Candidate(history, volume, new FetkovichAquifer(water, 0.00005, 0.0, 200.0), mass,
        "explicit synthetic joint mass");
  }

  /** Marginal lower-tail quantiles follow exact unequal prior masses and retain parameter units/order. */
  @Test
  void returnsMarginalInPlaceQuantilesFromCompleteJointMasses() {
    CompositionalReservoirHistory h = history();
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(new Candidate[] {candidate(h, 1100.0, 40000.0, 1.0),
        candidate(h, 900.0, 200000.0, 1.0), candidate(h, 1000.0, 100000.0, 2.0)});
    Assertions.assertArrayEquals(new double[] {0.25, 0.25, 0.5}, posterior.getPosteriorMasses(), 1.0e-14);
    Assertions.assertArrayEquals(new double[] {900.0, 1.0, 0.0, 2.0, 0.0}, posterior.parameterQuantiles(0.1), 0.0);
    Assertions.assertArrayEquals(new double[] {1000.0, 1.0, 0.0, 5.0, 0.0}, posterior.parameterQuantiles(0.5), 0.0);
    Assertions.assertArrayEquals(new double[] {1100.0, 1.0, 0.0, 10.0, 0.0}, posterior.parameterQuantiles(0.9), 0.0);
    // The marginal lower quantiles (900 m3, 2 m3/bar) are not one supplied joint scenario.
    Assertions.assertEquals("initialVolumeM3", posterior.getParameterNames()[0]);
    Assertions.assertEquals("aquiferCapacityM3PerBar", posterior.getParameterNames()[3]);
    Assertions.assertArrayEquals(posterior.parameterQuantiles(0.5),
        SerializationUtils.clone(posterior).parameterQuantiles(0.5), 0.0);
  }

  /** The generalized inverse includes an exact CDF boundary and both positive-mass support endpoints. */
  @Test
  void definesCdfBoundariesAndSupportEndpoints() {
    CompositionalReservoirHistory h = history();
    Posterior posterior = ReservoirHistoryEnsemble
        .evaluate(new Candidate[] {candidate(h, 1100.0, 100000.0, 1.0), candidate(h, 900.0, 100000.0, 1.0)});
    Assertions.assertArrayEquals(new double[] {0.5, 0.5}, posterior.getPosteriorMasses(), 0.0);
    Assertions.assertEquals(900.0, posterior.parameterQuantiles(0.0)[0]);
    Assertions.assertEquals(900.0, posterior.parameterQuantiles(0.5)[0]);
    Assertions.assertEquals(1100.0, posterior.parameterQuantiles(Math.nextUp(0.5))[0]);
    Assertions.assertEquals(1100.0, posterior.parameterQuantiles(1.0)[0]);
    Assertions.assertEquals(200.0, posterior.pressureQuantiles(0.0)[0], 1.0e-9);
    Assertions.assertEquals(200.0, posterior.pressureQuantiles(1.0)[0], 1.0e-9);
  }

  /** A positive tail remains part of q=1 support even when adding its probability rounds away. */
  @Test
  void retainsTinyPositiveUpperTailForPressureAndParameters() {
    CompositionalReservoirHistory constant = history();
    Step calibration = new Step(100.0, new double[1], new double[1], 200.0, 1.0, false);
    Step forecast = new Step(100.0, new double[] {100000.0}, new double[1], Double.NaN, Double.NaN, false);
    CompositionalReservoirHistory depleted = new CompositionalReservoirHistory(constant.getModel(),
        "prescribed synthetic methane withdrawal", constant.getComponents(), 400.0, 200.0, new double[] {1.0, 0.0, 0.0},
        new Step[] {calibration, forecast});
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(
        new Candidate[] {candidate(depleted, 900.0, 100000.0, 1.0), candidate(depleted, 1100.0, 100000.0, 1.0e-20)});
    double[] weights = posterior.getPosteriorMasses();
    Assertions.assertTrue(weights[1] > 0.0);
    Assertions.assertEquals(1.0, weights[0]);
    double lower = posterior.getResults()[0].getPressuresBara()[2];
    double upper = posterior.getResults()[1].getPressuresBara()[2];
    Assertions.assertTrue(lower < upper);
    Assertions.assertEquals(upper, posterior.pressureQuantiles(1.0)[2]);
    Assertions.assertEquals(lower, posterior.pressureQuantiles(0.9)[2]);
    Assertions.assertEquals(1100.0, posterior.parameterQuantiles(1.0)[0]);
    Assertions.assertEquals(900.0, posterior.parameterQuantiles(0.9)[0]);
  }

  /** Explicit infinite storage is a valid quantile; zero posterior mass never expands its support. */
  @Test
  void retainsInfiniteStorageAndExcludesZeroMassSupport() {
    CompositionalReservoirHistory h = history();
    Candidate finite = candidate(h, 1000.0, 100000.0, 1.0);
    Candidate infinite = candidate(h, 1000.0, Double.POSITIVE_INFINITY, 1.0);
    for (Candidate[] order : new Candidate[][] {{finite, infinite}, {infinite, finite}}) {
      Posterior posterior = ReservoirHistoryEnsemble.evaluate(order);
      Assertions.assertEquals(5.0, posterior.parameterQuantiles(0.0)[3]);
      Assertions.assertEquals(Double.POSITIVE_INFINITY, posterior.parameterQuantiles(1.0)[3]);
      Assertions.assertTrue(Double.isNaN(posterior.getPosteriorMeanParameters()[3]));
    }
    Posterior zeroMass = ReservoirHistoryEnsemble
        .evaluate(new Candidate[] {candidate(h, 1000.0, Double.POSITIVE_INFINITY, Double.MIN_VALUE),
            candidate(h, 1000.0, 100000.0, Double.MAX_VALUE)});
    Assertions.assertEquals(0.0, zeroMass.getPosteriorMasses()[0]);
    Assertions.assertEquals(5.0, zeroMass.parameterQuantiles(1.0)[3]);
    Assertions.assertEquals(5.0, zeroMass.getPosteriorMeanParameters()[3]);
  }

  /** Invalid probabilities are explicit errors and returned arrays cannot mutate the posterior. */
  @Test
  void validatesProbabilityAndOwnsReturnedQuantiles() {
    CompositionalReservoirHistory h = history();
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(new Candidate[] {candidate(h, 1000.0, 100000.0, 1.0)});
    for (double invalid : new double[] {-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      Assertions.assertThrows(IllegalArgumentException.class, () -> posterior.parameterQuantiles(invalid));
      Assertions.assertThrows(IllegalArgumentException.class, () -> posterior.pressureQuantiles(invalid));
    }
    byte[] before = SerializationUtils.serialize(posterior);
    double[] returned = posterior.parameterQuantiles(0.5);
    returned[0] = -1.0;
    Assertions.assertEquals(1000.0, posterior.parameterQuantiles(0.5)[0]);
    Assertions.assertArrayEquals(before, SerializationUtils.serialize(posterior));
  }
}
