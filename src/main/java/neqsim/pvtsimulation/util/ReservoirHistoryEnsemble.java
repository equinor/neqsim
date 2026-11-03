package neqsim.pvtsimulation.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Result;
import neqsim.pvtsimulation.util.CompositionalReservoirHistory.Step;

/**
 * Discrete Bayesian integration of geological, aquifer, laboratory-EOS and measured-history hypotheses.
 *
 * <p>
 * Each candidate is one complete joint prior scenario, with a probability mass, not a probability density. Nonuniform
 * parameter grids require cell-integrated masses. Existing compositional replay supplies the pressure likelihood; this
 * class never retunes the EOS or fits produced compositions supplied as inputs. The same independent Gaussian pressure
 * observations and sigmas are required across candidates. Held-out and prediction-only pressures do not enter posterior
 * weights. PVT and production uncertainty are propagated only when explicitly represented among the candidate models
 * and component histories.
 * </p>
 *
 * <p>
 * These finite-ensemble posterior moments and lower-tail quantiles are conditional on the supplied hypotheses,
 * likelihood and fixed-temperature/immiscible-aquifer approximations. They are not continuous-grid convergence, ES-MDA,
 * a global-optimum proof or recoverable reserves. Refine the joint grid and inspect rejected prior mass, effective
 * scenario count, correlations and prior-to-posterior shifts before interpretation.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ReservoirHistoryEnsemble {
  private static final String[] PARAMETERS = {"initialVolumeM3", "initialGasVolumeFraction",
      "initialAqueousVolumeFraction", "aquiferCapacityM3PerBar", "aquiferProductivityM3PerSecondPerBar"};

  /**
   * Prevents construction of a stateless integration utility.
   */
  private ReservoirHistoryEnsemble() {
  }

  /**
   * Immutable joint prior scenario. The history owns its frozen laboratory EOS and component-amount realization.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Candidate implements Serializable {
    private static final long serialVersionUID = 1L;
    private final CompositionalReservoirHistory history;
    private final double volume;
    private final FetkovichAquifer aquifer;
    private final double priorMass;
    private final String reference;

    /**
     * Defines one joint prior scenario without running or tuning an EOS.
     *
     * @param history common observations and this scenario's measured component realization and accepted EOS
     * @param initialVolumeM3 positive initial total pore volume, m3
     * @param aquifer optional immutable physical aquifer; null gives no support
     * @param priorMass positive finite relative prior probability mass of this complete scenario
     * @param reference provenance of joint geological/PVT/history assumptions and mass assignment
     */
    public Candidate(CompositionalReservoirHistory history, double initialVolumeM3, FetkovichAquifer aquifer,
        double priorMass, String reference) {
      if (history == null || !Double.isFinite(initialVolumeM3) || initialVolumeM3 <= 0.0 || !Double.isFinite(priorMass)
          || priorMass <= 0.0) {
        throw new IllegalArgumentException("History, positive finite pore volume and prior mass required");
      }
      this.history = history;
      this.volume = initialVolumeM3;
      this.aquifer = aquifer;
      this.priorMass = priorMass;
      this.reference = CalibratedPvtModel.reference(reference);
    }

    /**
     * Returns the frozen history, including laboratory provenance and uncertain component realization.
     *
     * @return immutable history
     */
    public CompositionalReservoirHistory getHistory() {
      return history;
    }

    /**
     * Returns scenario pore volume.
     *
     * @return initial total pore volume in m3
     */
    public double getInitialVolumeM3() {
      return volume;
    }

    /**
     * Returns the physical aquifer hypothesis.
     *
     * @return immutable aquifer or null for no support
     */
    public FetkovichAquifer getAquifer() {
      return aquifer;
    }

    /**
     * Returns scenario provenance.
     *
     * @return joint prior assumptions and assignment reference
     */
    public String getReference() {
      return reference;
    }

    /**
     * Returns the reported physical parameter vector.
     *
     * @return pore volume, gas/aqueous fractions, aquifer storage and productivity
     */
    private double[] parameters() {
      double[] f = history.getInitialPhaseVolumeFractions();
      return new double[] {volume, f[0], f[2], aquifer == null ? 0.0 : aquifer.getCapacityM3PerBar(),
          aquifer == null ? 0.0 : aquifer.getProductivityM3PerSecondPerBar()};
    }
  }

  /**
   * Immutable discrete posterior and prior/history conflict diagnostics.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class Posterior implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Candidate[] candidates;
    private final Result[] results;
    private final double[] prior;
    private final double[] weights;
    private final String[] rejected;
    private final double logEvidence;
    private final double rejectedMass;
    private final int bestLikelihood;

    /**
     * Freezes complete evaluated hypotheses and normalized probability masses.
     *
     * @param candidates frozen candidate array
     * @param results feasible replays or null for failures
     * @param prior normalized prior masses including failures
     * @param weights normalized posterior masses
     * @param rejected failed candidate diagnostics
     * @param logEvidence log relative likelihood normalizer without common Gaussian density constants
     * @param rejectedMass prior probability assigned to infeasible hypotheses
     * @param bestLikelihood index of minimum calibration chi-square
     */
    private Posterior(Candidate[] candidates, Result[] results, double[] prior, double[] weights, List<String> rejected,
        double logEvidence, double rejectedMass, int bestLikelihood) {
      this.candidates = candidates;
      this.results = results;
      this.prior = prior;
      this.weights = weights;
      this.rejected = rejected.toArray(new String[rejected.size()]);
      this.logEvidence = logEvidence;
      this.rejectedMass = rejectedMass;
      this.bestLikelihood = bestLikelihood;
    }

    /**
     * Returns common parameter names and units encoded in their identifiers.
     *
     * @return names for all moment/correlation vectors
     */
    public String[] getParameterNames() {
      return PARAMETERS.clone();
    }

    /**
     * Returns joint hypotheses in the original order.
     *
     * @return defensive candidate-array copy
     */
    public Candidate[] getCandidates() {
      return candidates.clone();
    }

    /**
     * Returns evaluated immutable replays, with null for infeasible hypotheses.
     *
     * @return defensive replay-array copy
     */
    public Result[] getResults() {
      return results.clone();
    }

    /**
     * Returns normalized prior probability masses, including infeasible hypotheses.
     *
     * @return prior masses summing to one
     */
    public double[] getPriorMasses() {
      return prior.clone();
    }

    /**
     * Returns normalized posterior probability masses; failed hypotheses have zero mass.
     *
     * @return posterior masses summing to one
     */
    public double[] getPosteriorMasses() {
      return weights.clone();
    }

    /**
     * Returns failures with candidate index and provenance, rather than manufactured pressure predictions.
     *
     * @return rejected hypothesis diagnostics
     */
    public String[] getRejectedCandidates() {
      return rejected.clone();
    }

    /**
     * Returns prior mass assigned to infeasible histories; posterior normalization does not hide that mass.
     *
     * @return rejected prior probability fraction
     */
    public double getRejectedPriorMassFraction() {
      return rejectedMass;
    }

    /**
     * Returns the log normalizer of exp(-chi-square/2), without common Gaussian density constants.
     *
     * @return dimensionless log relative evidence, not an absolute data-density likelihood
     */
    public double getLogEvidenceWithoutGaussianConstant() {
      return logEvidence;
    }

    /**
     * Returns the minimum-chi-square hypothesis, separately from posterior preference.
     *
     * @return original candidate index of the maximum likelihood scenario
     */
    public int getBestLikelihoodIndex() {
      return bestLikelihood;
    }

    /**
     * Returns 1/sum(weight squared), a weight-concentration diagnostic for the supplied discrete ensemble.
     *
     * @return effective scenario count, not independent MCMC sample size
     */
    public double getEffectiveScenarioCount() {
      double sum = 0.0;
      for (double w : weights) {
        sum += w * w;
      }
      return 1.0 / sum;
    }

    /**
     * Returns posterior means of the physical parameter vector.
     *
     * @return posterior means; NaN for any parameter with infinite support values at positive mass
     */
    public double[] getPosteriorMeanParameters() {
      return mean(weights);
    }

    /**
     * Returns posterior parameter correlations, explicitly NaN for a constant or non-finite parameter.
     *
     * @return symmetric population correlation matrix for the supplied discrete posterior
     */
    public double[][] getPosteriorCorrelations() {
      double[] means = mean(weights);
      double[][] covariance = covariance(weights, means);
      double[][] correlation = new double[PARAMETERS.length][PARAMETERS.length];
      for (int i = 0; i < correlation.length; i++) {
        for (int j = 0; j < correlation.length; j++) {
          correlation[i][j] = covariance[i][i] > 0.0 && covariance[j][j] > 0.0
              ? covariance[i][j] / Math.sqrt(covariance[i][i]) / Math.sqrt(covariance[j][j])
              : Double.NaN;
        }
      }
      return correlation;
    }

    /**
     * Returns posterior-minus-prior parameter means divided by prior standard deviations.
     *
     * @return signed prior-scale shifts; NaN for degenerate/non-finite priors, not significance tests
     */
    public double[] getPriorStandardizedShifts() {
      double[] before = mean(prior);
      double[] after = mean(weights);
      double[][] cov = covariance(prior, before);
      double[] shift = new double[before.length];
      for (int i = 0; i < shift.length; i++) {
        shift[i] = cov[i][i] > 0.0 ? (after[i] - before[i]) / Math.sqrt(cov[i][i]) : Double.NaN;
      }
      return shift;
    }

    /**
     * Returns posterior lower-tail pressure quantiles at each common time, including the initial state.
     *
     * <p>
     * Probability 0.1 means the lower tenth percentile, not the petroleum exceedance convention. These are uncertainty
     * in the deterministic model predictions from the supplied scenarios, excluding future measurement noise. Held-out
     * measurements were not used to construct them.
     * </p>
     *
     * @param probability lower-tail cumulative probability between zero and one inclusive
     * @return pressure quantiles in bara at every common history/forecast time
     */
    public double[] pressureQuantiles(double probability) {
      if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
        throw new IllegalArgumentException("Quantile probability must be between zero and one");
      }
      int count = results[bestLikelihood].getPressuresBara().length;
      double[] quantiles = new double[count];
      for (int t = 0; t < count; t++) {
        List<double[]> pairs = new ArrayList<double[]>();
        for (int i = 0; i < results.length; i++) {
          if (weights[i] > 0.0) {
            pairs.add(new double[] {results[i].getPressuresBara()[t], weights[i]});
          }
        }
        pairs.sort(new Comparator<double[]>() {
          /** {@inheritDoc} */
          @Override
          public int compare(double[] first, double[] second) {
            return Double.compare(first[0], second[0]);
          }
        });
        double cumulative = 0.0;
        quantiles[t] = pairs.get(pairs.size() - 1)[0];
        for (double[] pair : pairs) {
          cumulative += pair[1];
          if (cumulative >= probability) {
            quantiles[t] = pair[0];
            break;
          }
        }
      }
      return quantiles;
    }

    /**
     * Computes weighted parameter means without allowing zero mass times infinity to contaminate finite moments.
     *
     * @param mass normalized probabilities
     * @return weighted means or NaN for unbounded numerical moments
     */
    private double[] mean(double[] mass) {
      int originIndex = 0;
      while (mass[originIndex] == 0.0) {
        originIndex++;
      }
      double[] origin = candidates[originIndex].parameters();
      double[] mean = new double[PARAMETERS.length];
      double totalMass = 0.0;
      for (int i = 0; i < candidates.length; i++) {
        if (mass[i] == 0.0) {
          continue;
        }
        totalMass += mass[i];
        double[] x = candidates[i].parameters();
        for (int j = 0; j < x.length; j++) {
          mean[j] += mass[i] * (x[j] - origin[j]);
        }
      }
      for (int j = 0; j < mean.length; j++) {
        mean[j] = Double.isFinite(origin[j]) && Double.isFinite(mean[j]) ? origin[j] + mean[j] / totalMass : Double.NaN;
      }
      return mean;
    }

    /**
     * Computes population covariance of the discrete parameter hypotheses.
     *
     * @param mass normalized probabilities
     * @param mean weighted parameter means
     * @return weighted covariance matrix
     */
    private double[][] covariance(double[] mass, double[] mean) {
      double[][] covariance = new double[PARAMETERS.length][PARAMETERS.length];
      for (int i = 0; i < candidates.length; i++) {
        if (mass[i] == 0.0) {
          continue;
        }
        double[] x = candidates[i].parameters();
        for (int j = 0; j < x.length; j++) {
          for (int k = 0; k < x.length; k++) {
            covariance[j][k] += mass[i] * (x[j] - mean[j]) * (x[k] - mean[k]);
          }
        }
      }
      return covariance;
    }
  }

  /**
   * Evaluates and conditions joint hypotheses on common independent-Gaussian pressure observations.
   *
   * @param supplied complete joint candidates, including probability masses and provenance
   * @return normalized discrete posterior, failed prior mass, joint correlations and forecast quantiles
   * @throws IllegalArgumentException if observations, times, component basis or initial conditions differ
   * @throws IllegalStateException if every hypothesis is infeasible
   */
  public static Posterior evaluate(Candidate[] supplied) {
    if (supplied == null || supplied.length == 0) {
      throw new IllegalArgumentException("At least one complete prior candidate is required");
    }
    Candidate[] candidates = supplied.clone();
    if (candidates[0] == null) {
      throw new IllegalArgumentException("Null candidate");
    }
    Step[] reference = candidates[0].history.getSteps();
    int observations = 0;
    for (Step s : reference) {
      if (!s.isHoldout() && !Double.isNaN(s.getPressureBara())) {
        observations++;
      }
    }
    if (observations == 0) {
      throw new IllegalArgumentException("At least one calibration pressure is required");
    }
    for (Candidate candidate : candidates) {
      requireCommonObservations(candidates[0], candidate);
    }
    double[] logPrior = new double[candidates.length];
    for (int i = 0; i < candidates.length; i++) {
      logPrior[i] = Math.log(candidates[i].priorMass);
    }
    double priorNormalizer = logSumExp(logPrior);
    double[] prior = new double[candidates.length];
    double[] logPosterior = new double[candidates.length];
    Result[] results = new Result[candidates.length];
    List<String> rejected = new ArrayList<String>();
    double rejectedMass = 0.0;
    int best = -1;
    for (int i = 0; i < candidates.length; i++) {
      prior[i] = Math.exp(logPrior[i] - priorNormalizer);
      logPosterior[i] = Double.NEGATIVE_INFINITY;
      Candidate candidate = candidates[i];
      try {
        results[i] = candidate.history.replay(candidate.volume, candidate.aquifer);
        if (best < 0 || results[i].getCalibrationChiSquare() < results[best].getCalibrationChiSquare()) {
          best = i;
        }
      } catch (IllegalArgumentException | IllegalStateException failure) {
        rejected.add(i + " (" + candidate.reference + "): " + failure.getMessage());
        rejectedMass += prior[i];
      }
    }
    if (best < 0) {
      throw new IllegalStateException("All prior hypotheses are infeasible: " + rejected);
    }
    // Center likelihoods before adding log prior masses. Otherwise a huge common finite chi-square
    // erases log prior odds and the small log-sum-exp normalizer through floating-point rounding.
    double minimumChiSquare = results[best].getCalibrationChiSquare();
    double maximumLogMass = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < candidates.length; i++) {
      if (results[i] != null) {
        logPosterior[i] = logPrior[i] - priorNormalizer
            - 0.5 * (results[i].getCalibrationChiSquare() - minimumChiSquare);
      }
      maximumLogMass = Math.max(maximumLogMass, logPosterior[i]);
    }
    double[] weights = new double[candidates.length];
    double totalWeight = 0.0;
    for (int i = 0; i < weights.length; i++) {
      weights[i] = Math.exp(logPosterior[i] - maximumLogMass);
      totalWeight += weights[i];
    }
    for (int i = 0; i < weights.length; i++) {
      weights[i] /= totalWeight;
    }
    double logEvidence = -0.5 * minimumChiSquare + maximumLogMass + Math.log(totalWeight);
    return new Posterior(candidates, results, prior, weights, rejected, logEvidence, rejectedMass, best);
  }

  /**
   * Requires the same pressure likelihood while allowing explicitly supplied EOS, volume and flow realizations.
   *
   * @param first reference candidate
   * @param next candidate to validate
   */
  private static void requireCommonObservations(Candidate first, Candidate next) {
    if (next == null || !Arrays.equals(first.history.getComponents(), next.history.getComponents())
        || first.history.getTemperatureK() != next.history.getTemperatureK()
        || first.history.getInitialPressureBara() != next.history.getInitialPressureBara()) {
      throw new IllegalArgumentException(
          "Candidates must share named component basis and initial temperature/pressure");
    }
    Step[] before = first.history.getSteps();
    Step[] after = next.history.getSteps();
    if (before.length != after.length) {
      throw new IllegalArgumentException("Common observation/forecast times required");
    }
    for (int i = 0; i < before.length; i++) {
      if (before[i].getDurationSeconds() != after[i].getDurationSeconds()
          || Double.doubleToLongBits(before[i].getPressureBara()) != Double.doubleToLongBits(after[i].getPressureBara())
          || Double.doubleToLongBits(before[i].getSigmaBara()) != Double.doubleToLongBits(after[i].getSigmaBara())
          || before[i].isHoldout() != after[i].isHoldout()) {
        throw new IllegalArgumentException("Pressure observations, uncertainties, holdouts and times must match");
      }
    }
  }

  /**
   * Computes a stable logarithmic normalizer.
   *
   * @param values logarithms of nonnegative weights
   * @return log of their sum, or negative infinity when all are zero
   */
  private static double logSumExp(double[] values) {
    double max = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      max = Math.max(max, value);
    }
    if (max == Double.NEGATIVE_INFINITY) {
      return max;
    }
    double sum = 0.0;
    for (double value : values) {
      sum += Math.exp(value - max);
    }
    return max + Math.log(sum);
  }
}
