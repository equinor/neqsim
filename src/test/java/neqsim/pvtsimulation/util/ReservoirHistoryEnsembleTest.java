package neqsim.pvtsimulation.util;

import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Candidate;
import neqsim.pvtsimulation.util.ReservoirHistoryEnsemble.Posterior;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent Bayesian-odds, pressure-likelihood, joint-identifiability and forecast checks.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReservoirHistoryEnsembleTest {
  /**
   * Builds a common immutable laboratory EOS checkpoint fixture.
   *
   * @return synthetic methane model
   */
  private CalibratedPvtModel model() {
    SystemInterface fluid = new SystemPrEos(400.0, 200.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return new CalibratedPvtModel(fluid, fluid, "synthetic methane", "original PR", "accepted PR",
        "synthetic laboratory EOS fixture", new String[] {"CCE-1"});
  }

  /**
   * Builds a shared pressure likelihood with a specified production-history realization.
   *
   * @param pressures observed end pressures, or NaN for forecast
   * @param sigma common pressure sigma in bar
   * @param heldout whether final pressure is a holdout
   * @param productionScale explicit measured-production uncertainty realization
   * @return immutable history
   */
  private CompositionalReservoirHistory history(double[] pressures, double sigma, boolean heldout,
      double productionScale) {
    Step[] steps = new Step[pressures.length];
    for (int i = 0; i < steps.length; i++) {
      steps[i] = new Step(86400.0, new double[] {(200000.0 + 50000.0 * i) * productionScale}, new double[1],
          pressures[i], Double.isNaN(pressures[i]) ? Double.NaN : sigma, heldout && i == steps.length - 1);
    }
    return new CompositionalReservoirHistory(model(), "synthetic history realization scale=" + productionScale,
        new String[] {"methane"}, 400.0, 200.0, new double[] {1.0, 0.0, 0.0}, steps);
  }

  /** Checks posterior odds against Bayes' rule and complete exclusion of held-out pressures. */
  @Test
  void posteriorOddsRespectPriorsSigmaAndHoldouts() {
    double[] pressure = {190.0, 180.0, 170.0};
    CompositionalReservoirHistory h = history(pressure, 5.0, true, 1.0);
    Candidate[] candidates = {new Candidate(h, 900.0, null, 2.0, "geology A"),
        new Candidate(h, 1100.0, null, 1.0, "geology B")};
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(candidates);
    double first = h.replay(900.0).getCalibrationChiSquare();
    double second = h.replay(1100.0).getCalibrationChiSquare();
    double odds = 2.0 * Math.exp(-0.5 * (first - second));
    Assertions.assertEquals(odds / (1.0 + odds), posterior.getPosteriorMasses()[0], 1.0e-13);
    pressure[2] = 10000.0;
    CompositionalReservoirHistory changed = history(pressure, 5.0, true, 1.0);
    Posterior other = ReservoirHistoryEnsemble.evaluate(new Candidate[] {
        new Candidate(changed, 900.0, null, 2.0, "geology A"), new Candidate(changed, 1100.0, null, 1.0, "geology B")});
    Assertions.assertArrayEquals(posterior.getPosteriorMasses(), other.getPosteriorMasses(), 0.0);
    Assertions
        .assertTrue(other.getResults()[0].getHoldoutChiSquare() > posterior.getResults()[0].getHoldoutChiSquare());
    Assertions.assertEquals(2.0 / 3.0, posterior.getPriorMasses()[0], 1.0e-14);
    Assertions.assertEquals(0.0, posterior.getRejectedPriorMassFraction());
  }

  /** Joint geological-volume/aquifer grid exposes correlations and lower-tail forecast definitions. */
  @Test
  void jointAquiferGridRetainsConfoundingAndForecastUncertainty() {
    FetkovichAquifer truth = new FetkovichAquifer(100000.0, 0.00005, 0.00002, 200.0);
    double[] predicted = history(new double[] {Double.NaN, Double.NaN, Double.NaN}, 1.0, false, 1.0)
        .replay(1000.0, truth).getPressuresBara();
    CompositionalReservoirHistory h = history(new double[] {predicted[1], predicted[2], Double.NaN}, 2.0, false, 1.0);
    Candidate[] candidates = new Candidate[9];
    int row = 0;
    for (double volume : new double[] {850.0, 1000.0, 1150.0}) {
      for (double j : new double[] {0.00001, 0.00002, 0.00003}) {
        candidates[row++] = new Candidate(h, volume, new FetkovichAquifer(100000.0, 0.00005, j, 200.0), 1.0,
            "uniform discrete joint prior");
      }
    }
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(candidates);
    Assertions.assertEquals(4, posterior.getBestLikelihoodIndex());
    Assertions.assertTrue(posterior.getPosteriorCorrelations()[0][4] < -0.05);
    Assertions.assertTrue(Double.isNaN(posterior.getPosteriorCorrelations()[1][1]));
    Assertions.assertTrue(posterior.getEffectiveScenarioCount() > 1.0);
    double[] q10 = posterior.pressureQuantiles(0.1);
    double[] q50 = posterior.pressureQuantiles(0.5);
    double[] q90 = posterior.pressureQuantiles(0.9);
    for (int i = 0; i < q10.length; i++) {
      Assertions.assertTrue(q10[i] <= q50[i] && q50[i] <= q90[i]);
    }
    Assertions.assertTrue(q10[3] < q90[3]);
    Assertions.assertEquals(200.0, q50[0], 1.0e-9);
    Assertions.assertThrows(IllegalArgumentException.class, () -> posterior.pressureQuantiles(-0.1));
    Assertions.assertTrue(Double.isFinite(posterior.getPriorStandardizedShifts()[0]));
  }

  /** Extremely small likelihoods remain normalizable; failed prior mass and uncertain histories remain visible. */
  @Test
  void logWeightsFailuresAndHistoryUncertaintyRemainExplicit() {
    CompositionalReservoirHistory h = history(new double[] {10000.0, 9900.0}, 0.5, false, 1.0);
    Posterior posterior = ReservoirHistoryEnsemble
        .evaluate(new Candidate[] {new Candidate(h, 900.0, null, Double.MAX_VALUE, "large relative mass A"),
            new Candidate(h, 1100.0, null, Double.MAX_VALUE, "large relative mass B"),
            new Candidate(h, 0.0001, null, Double.MAX_VALUE, "infeasible inventory")});
    double[] weights = posterior.getPosteriorMasses();
    Assertions.assertEquals(1.0, weights[0] + weights[1] + weights[2], 1.0e-8);
    Assertions.assertEquals(0.0, weights[2]);
    Assertions.assertEquals(1.0 / 3.0, posterior.getRejectedPriorMassFraction(), 1.0e-13);
    Assertions.assertEquals(1, posterior.getRejectedCandidates().length);
    Assertions.assertTrue(posterior.getLogEvidenceWithoutGaussianConstant() < -1000000.0);
    double[] pressures = {190.0, 180.0};
    CompositionalReservoirHistory low = history(pressures, 1.0, false, 0.9);
    CompositionalReservoirHistory high = history(pressures, 1.0, false, 1.1);
    Posterior uncertainty = ReservoirHistoryEnsemble
        .evaluate(new Candidate[] {new Candidate(low, 1000.0, null, 1.0, "lower production realization"),
            new Candidate(high, 1000.0, null, 1.0, "higher production realization")});
    Assertions.assertNotEquals(uncertainty.getResults()[0].getPressuresBara()[2],
        uncertainty.getResults()[1].getPressuresBara()[2]);
  }

  /** Rejects incomparable likelihoods, owns outputs and preserves serialized posterior evidence. */
  @Test
  void validatesLikelihoodAndOwnsPosteriorEvidence() {
    CompositionalReservoirHistory h = history(new double[] {190.0, 180.0}, 1.0, false, 1.0);
    Candidate c = new Candidate(h, 1000.0, null, 1.0, "geological prior");
    Candidate otherSigma = new Candidate(history(new double[] {190.0, 180.0}, 2.0, false, 1.0), 1100.0, null, 1.0,
        "incomparable pressure likelihood");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ReservoirHistoryEnsemble.evaluate(new Candidate[] {c, otherSigma}));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ReservoirHistoryEnsemble.evaluate(new Candidate[] {null}));
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(new Candidate[] {c});
    posterior.getPosteriorMasses()[0] = -1.0;
    posterior.getCandidates()[0] = null;
    Assertions.assertEquals(1.0, posterior.getPosteriorMasses()[0]);
    Assertions.assertNotNull(posterior.getCandidates()[0]);
    Posterior copy = SerializationUtils.clone(posterior);
    Assertions.assertArrayEquals(posterior.pressureQuantiles(0.5), copy.pressureQuantiles(0.5), 0.0);
  }

  /** A huge common finite chi-square must preserve prior odds and unit posterior mass. */
  @Test
  void commonLargeLikelihoodOffsetPreservesPriorOdds() {
    CompositionalReservoirHistory h = history(new double[] {1.0e150, 1.0e150}, 1.0, false, 1.0);
    Posterior posterior = ReservoirHistoryEnsemble.evaluate(new Candidate[] {
        new Candidate(h, 1000.0, null, 2.0, "prior mass two"), new Candidate(h, 1000.0, null, 1.0, "prior mass one")});
    Assertions.assertEquals(2.0 / 3.0, posterior.getPosteriorMasses()[0], 1.0e-14);
    Assertions.assertEquals(1.0 / 3.0, posterior.getPosteriorMasses()[1], 1.0e-14);
    Assertions.assertEquals(1.8, posterior.getEffectiveScenarioCount(), 1.0e-14);
    Assertions.assertTrue(Double.isFinite(posterior.getLogEvidenceWithoutGaussianConstant()));
  }

  /** Infinite support gives undefined capacity moments regardless of candidate ordering. */
  @Test
  void infiniteParameterMomentsAreOrderIndependent() {
    CompositionalReservoirHistory h = history(new double[] {190.0, 180.0}, 1.0, false, 1.0);
    Candidate finite = new Candidate(h, 1000.0, new FetkovichAquifer(100000.0, 0.00005, 0.0, 200.0), 1.0,
        "finite support without exchange");
    Candidate infinite = new Candidate(h, 1000.0, new FetkovichAquifer(Double.POSITIVE_INFINITY, 0.00005, 0.0, 200.0),
        1.0, "infinite support without exchange");
    for (Candidate[] order : new Candidate[][] {{finite, infinite}, {infinite, finite}}) {
      Posterior posterior = ReservoirHistoryEnsemble.evaluate(order);
      Assertions.assertTrue(Double.isNaN(posterior.getPosteriorMeanParameters()[3]));
      Assertions.assertTrue(Double.isNaN(posterior.getPosteriorCorrelations()[3][3]));
      Assertions.assertEquals(1000.0, posterior.getPosteriorMeanParameters()[0]);
    }
  }

  /** Independently accepted EOS alternatives propagate through their own frozen replay and posterior weight. */
  @Test
  void acceptedPvtModelUncertaintyChangesPredictedHistory() {
    CompositionalReservoirHistory original = history(new double[] {190.0, 180.0}, 1.0, false, 1.0);
    SystemInterface srk = new SystemSrkEos(400.0, 200.0);
    srk.addComponent("methane", 1.0);
    srk.setMixingRule("classic");
    srk.init(0);
    CalibratedPvtModel alternative = new CalibratedPvtModel(model().getOriginalFluid(), srk, "same synthetic methane",
        "original PR", "alternative accepted SRK", "independently specified EOS uncertainty fixture",
        new String[] {"alternative-CCE"});
    CompositionalReservoirHistory changed = new CompositionalReservoirHistory(alternative, "alternative EOS scenario",
        original.getComponents(), original.getTemperatureK(), original.getInitialPressureBara(),
        original.getInitialPhaseVolumeFractions(), original.getSteps());
    Posterior posterior = ReservoirHistoryEnsemble
        .evaluate(new Candidate[] {new Candidate(original, 1000.0, null, 1.0, "accepted PR hypothesis"),
            new Candidate(changed, 1000.0, null, 1.0, "accepted SRK hypothesis")});
    Assertions.assertNotEquals(posterior.getResults()[0].getPressuresBara()[2],
        posterior.getResults()[1].getPressuresBara()[2], 1.0e-5);
    Assertions.assertNotEquals(0.5, posterior.getPosteriorMasses()[0], 1.0e-8);
    Assertions.assertEquals(SystemSrkEos.class, posterior.getResults()[1].getFinalFluid().getClass());
    Assertions.assertEquals(SystemPrEos.class, original.getModel().getTunedFluid().getClass());
  }

}
