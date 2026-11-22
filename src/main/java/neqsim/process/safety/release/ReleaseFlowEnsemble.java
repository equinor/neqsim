package neqsim.process.safety.release;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic weighted propagation of caller-defined release scenarios. Each case contains a complete joint input,
 * allowing correlated pressure, temperature, composition and geometry uncertainties without imposing distributions.
 * Evaluation is sequential and uses the selected model for every case. Failed cases remain visible and prohibit
 * unconditional rate statistics; they are never removed, renormalized away or replaced by zero. This API does not
 * qualify a model or simulate inventory depletion.
 */
public final class ReleaseFlowEnsemble implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String ensembleId;
  private final List<Case> cases;
  private final List<ReleaseFlowResult> results;
  private final List<Double> probabilities;
  private final Map<ReleaseFlowResult.Status, Integer> statusCounts;

  /** Immutable joint input with relative probability weight and caller-declared sampling provenance. */
  public static final class Case implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final ReleaseFlowRequest request;
    private final double weight;
    private final Map<String, String> provenance;

    /**
     * Defines one jointly sampled input. Use equal weights for ordinary Monte Carlo samples.
     *
     * @param id unique case identifier within the ensemble
     * @param request complete immutable release input, including composition
     * @param weight finite positive relative probability weight
     * @param provenance sampling method, seed, process calculation identity or other explanatory metadata
     * @throws IllegalArgumentException for missing identity/request, invalid weight or invalid metadata
     */
    public Case(String id, ReleaseFlowRequest request, double weight, Map<String, String> provenance) {
      requireText(id, "Case identifier");
      if (request == null || provenance == null) {
        throw new IllegalArgumentException("Request and provenance required");
      }
      ReleaseFlowRequest.positive(weight, "weight");
      TreeMap<String, String> metadata = new TreeMap<String, String>();
      for (Map.Entry<String, String> entry : provenance.entrySet()) {
        requireText(entry.getKey(), "Provenance key");
        if (entry.getValue() == null) {
          throw new IllegalArgumentException("Provenance values cannot be null");
        }
        metadata.put(entry.getKey(), entry.getValue());
      }
      this.id = id;
      this.request = request;
      this.weight = weight;
      this.provenance = Collections.unmodifiableMap(metadata);
    }

    /** @return stable case identifier */
    public String getId() {
      return id;
    }

    /** @return immutable request; its fluid getter returns a defensive clone */
    public ReleaseFlowRequest getRequest() {
      return request;
    }

    /** @return positive relative probability weight */
    public double getWeight() {
      return weight;
    }

    /** @return immutable caller-supplied sampling metadata */
    public Map<String, String> getProvenance() {
      return Collections.unmodifiableMap(new TreeMap<String, String>(provenance));
    }
  }

  private ReleaseFlowEnsemble(String id, List<Case> cases, List<Double> probabilities,
      List<ReleaseFlowResult> results) {
    ensembleId = id;
    this.cases = Collections.unmodifiableList(new ArrayList<Case>(cases));
    this.probabilities = Collections.unmodifiableList(new ArrayList<Double>(probabilities));
    this.results = Collections.unmodifiableList(new ArrayList<ReleaseFlowResult>(results));
    EnumMap<ReleaseFlowResult.Status, Integer> counts = new EnumMap<ReleaseFlowResult.Status, Integer>(
        ReleaseFlowResult.Status.class);
    for (ReleaseFlowResult.Status status : ReleaseFlowResult.Status.values()) {
      counts.put(status, 0);
    }
    for (ReleaseFlowResult result : results) {
      counts.put(result.getStatus(), counts.get(result.getStatus()) + 1);
    }
    statusCounts = Collections.unmodifiableMap(counts);
  }

  /**
   * Evaluates a frozen list in caller order. Validates the entire input list before invoking the model. Model
   * exceptions, null results and mismatched model identity become per-case INVALID results; other cases are still
   * evaluated.
   *
   * @param ensembleId stable ensemble identifier
   * @param model explicitly selected release model, not mutated by this evaluator
   * @param cases nonempty jointly sampled cases; identifiers must be unique
   * @return immutable ensemble retaining every request, normalized probability and result
   * @throws IllegalArgumentException for invalid input or weights outside representable probability range
   */
  public static ReleaseFlowEnsemble evaluate(String ensembleId, ReleaseFlowModel model, List<Case> cases) {
    requireText(ensembleId, "Ensemble identifier");
    if (model == null || cases == null || cases.isEmpty()) {
      throw new IllegalArgumentException("Model and nonempty cases required");
    }
    requireText(model.getModelId(), "Model identifier");
    requireText(model.getModelVersion(), "Model version");
    List<Case> inputs = new ArrayList<Case>(cases);
    Set<String> ids = new HashSet<String>();
    double maximumWeight = 0.0;
    for (Case input : inputs) {
      if (input == null || !ids.add(input.getId())) {
        throw new IllegalArgumentException("Cases must be nonnull with unique identifiers");
      }
      maximumWeight = Math.max(maximumWeight, input.getWeight());
    }
    // Scale before summing so finite weights such as two Double.MAX_VALUE values remain valid.
    double scaledTotal = 0.0;
    for (Case input : inputs) {
      scaledTotal += input.getWeight() / maximumWeight;
    }
    List<Double> probabilities = new ArrayList<Double>();
    for (Case input : inputs) {
      double probability = (input.getWeight() / maximumWeight) / scaledTotal;
      ReleaseFlowRequest.positive(probability, "normalized probability");
      probabilities.add(probability);
    }
    List<ReleaseFlowResult> results = new ArrayList<ReleaseFlowResult>();
    for (Case input : inputs) {
      ReleaseFlowResult result;
      try {
        result = model.calculate(input.getRequest());
        if (result == null) {
          result = ReleaseFlowResult.failure(model, false, "MODEL_RETURNED_NULL", "Model returned no result");
        } else if (!model.getModelId().equals(result.getModelId())
            || !model.getModelVersion().equals(result.getModelVersion())) {
          result = ReleaseFlowResult.failure(model, false, "MODEL_IDENTITY_MISMATCH",
              "Result does not identify the explicitly selected model and version");
        }
      } catch (RuntimeException ex) {
        result = ReleaseFlowResult.failure(model, false, "MODEL_EXCEPTION",
            ex.getClass().getSimpleName() + ": " + ex.getMessage());
      }
      results.add(result);
    }
    return new ReleaseFlowEnsemble(ensembleId, inputs, probabilities, results);
  }

  private static void requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " required");
    }
  }

  /** @return stable ensemble identifier */
  public String getEnsembleId() {
    return ensembleId;
  }

  /** @return immutable cases in evaluation order */
  public List<Case> getCases() {
    return cases;
  }

  /** @return immutable per-case results, including all failures */
  public List<ReleaseFlowResult> getResults() {
    return Collections.unmodifiableList(new ArrayList<ReleaseFlowResult>(results));
  }

  /** @return immutable normalized probabilities in case order; independent of calculation status */
  public List<Double> getProbabilities() {
    return probabilities;
  }

  /** @return immutable counts including zero counts for every release status */
  public Map<ReleaseFlowResult.Status, Integer> getStatusCounts() {
    return statusCounts;
  }

  /** @return true only if every case is numerically usable, including explicitly reported warnings */
  public boolean isComplete() {
    return statusCounts.get(ReleaseFlowResult.Status.INVALID) == 0
        && statusCounts.get(ReleaseFlowResult.Status.UNSUPPORTED) == 0;
  }

  /** @return total original probability of usable cases; no conditioning on success */
  public double getUsableProbability() {
    double sum = 0.0;
    for (int i = 0; i < results.size(); i++) {
      if (results.get(i).isUsable()) {
        sum += probabilities.get(i);
      }
    }
    return Math.min(1.0, sum);
  }

  private void requireComplete() {
    if (!isComplete()) {
      throw new IllegalStateException("Incomplete ensemble: failed cases prohibit unconditional rate statistics");
    }
  }

  /**
   * Returns the probability-weighted mean rate. Warnings remain available in the results and status counts.
   *
   * @return unconditional mean rate in kg/s
   * @throws IllegalStateException if any case failed or was unsupported
   */
  public double getMeanMassFlowRateKgS() {
    requireComplete();
    double maximumRate = 0.0;
    for (ReleaseFlowResult result : results) {
      maximumRate = Math.max(maximumRate, result.getMassFlowRateKgS());
    }
    if (maximumRate == 0.0) {
      return 0.0;
    }
    // Positive scaled terms avoid overflow and cancellation of a small weighted tail by a zero-rate case.
    double scaledMean = 0.0;
    double totalProbability = 0.0;
    for (int i = 0; i < results.size(); i++) {
      double probability = probabilities.get(i);
      totalProbability += probability;
      scaledMean += probability * (results.get(i).getMassFlowRateKgS() / maximumRate);
    }
    return maximumRate * (scaledMean / totalProbability);
  }

  /**
   * Returns the inverse weighted empirical CDF: the smallest rate whose cumulative probability is at least p. No
   * interpolation is used; p=0 returns the minimum and p=1 the maximum. P90 here means a 90% non-exceedance quantile,
   * not petroleum-reserve exceedance notation or a statistical confidence bound.
   *
   * @param probability finite cumulative probability in [0, 1]
   * @return unconditional mass-rate quantile in kg/s
   * @throws IllegalArgumentException for an invalid probability
   * @throws IllegalStateException if any case failed or was unsupported
   */
  public double getMassFlowRateQuantileKgS(double probability) {
    if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
      throw new IllegalArgumentException("Quantile probability must be in [0, 1]");
    }
    requireComplete();
    List<Integer> order = new ArrayList<Integer>();
    for (int i = 0; i < results.size(); i++) {
      order.add(i);
    }
    Collections.sort(order, new Comparator<Integer>() {
      @Override
      public int compare(Integer left, Integer right) {
        return Double.compare(results.get(left).getMassFlowRateKgS(), results.get(right).getMassFlowRateKgS());
      }
    });
    // Explicit endpoints retain an extreme tail even if adding its probability rounds to 1.0.
    if (probability == 0.0) {
      return results.get(order.get(0)).getMassFlowRateKgS();
    }
    if (probability == 1.0) {
      return results.get(order.get(order.size() - 1)).getMassFlowRateKgS();
    }
    double cumulative = 0.0;
    for (int index : order) {
      cumulative += probabilities.get(index);
      if (cumulative >= probability) {
        return results.get(index).getMassFlowRateKgS();
      }
    }
    return results.get(order.get(order.size() - 1)).getMassFlowRateKgS();
  }

  /**
   * Exports each case using the existing v1 source-frame schema. The sequence indexes ensemble cases, not time steps;
   * all cases share the supplied simulation time. Sampling metadata is prefixed with sampling.; ensemble identity, case
   * ID, relative weight and normalized probability are added separately. Fixed arguments yield deterministic JSON for
   * deterministic model results. These frames must not be interleaved into a live session's sequence.
   *
   * @param scenarioId scenario identifier
   * @param sourceId physical source identifier shared by alternative cases
   * @param calculationId ensemble evaluation UUID
   * @param simulationTimeS sampled simulation time in s
   * @param generatedAt explicit UTC timestamp
   * @return immutable ordered frames; failures contain no numeric source payload
   */
  public List<SourceTermFrame> toFrames(String scenarioId, String sourceId, UUID calculationId, double simulationTimeS,
      Instant generatedAt) {
    List<SourceTermFrame> frames = new ArrayList<SourceTermFrame>();
    for (int i = 0; i < cases.size(); i++) {
      Case input = cases.get(i);
      Map<String, String> metadata = new TreeMap<String, String>();
      for (Map.Entry<String, String> entry : input.getProvenance().entrySet()) {
        metadata.put("sampling." + entry.getKey(), entry.getValue());
      }
      metadata.put("ensembleId", ensembleId);
      metadata.put("ensembleCaseId", input.getId());
      metadata.put("ensembleRelativeWeight", Double.toString(input.getWeight()));
      metadata.put("ensembleProbability", Double.toString(probabilities.get(i)));
      metadata.put("sequenceMeaning", "ENSEMBLE_CASE_INDEX");
      frames.add(SourceTermFrame.calculated(scenarioId, sourceId, calculationId, i, simulationTimeS, generatedAt,
          input.getRequest(), results.get(i), metadata));
    }
    return Collections.unmodifiableList(frames);
  }
}
