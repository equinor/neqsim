package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete immutable plant utilization snapshot for one exact completed process calculation.
 *
 * <p>
 * A snapshot contains one assessed row for every registry definition, including disabled, incomplete, and unavailable
 * restrictions. Enabled missing or invalid evidence fails closed. The class neither evaluates a process nor retains
 * mutable process state, callbacks, suppliers, or external optimizer proposals.
 * </p>
 * <p>
 * Without an expected-coverage report, completeness is relative to registered definitions only. Bind an independently
 * declared equipment/constraint scope with {@link Builder#expectedCoverage(UtilizationCoverageReport)} to detect
 * omitted registrations. This qualified path also requires an explicit convergence declaration for the calculation.
 * </p>
 */
public final class PlantUtilizationSnapshot implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.1";
  private static final double DEFAULT_NEAR_LIMIT_THRESHOLD = 0.9;

  private final String calculationId;
  private final String registryIdentityDigest;
  private final double nearLimitThreshold;
  private final boolean convergenceComplete;
  private final List<PlantConstraintEvidence> evidence;
  private final List<PlantConstraintEvidence> bottleneckLadder;
  private final Map<String, PlantConstraintEvidence> evidenceById;
  private final List<String> coverageDiagnostics;
  private final List<String> feasibilityDiagnostics;
  private final boolean complete;
  private final boolean feasible;
  private final UtilizationCoverageReport expectedCoverage;

  private PlantUtilizationSnapshot(Builder builder) {
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    registryIdentityDigest = builder.registry.getIdentityDigest();
    nearLimitThreshold = validateNearLimitThreshold(builder.nearLimitThreshold);
    convergenceComplete = builder.convergenceComplete;
    expectedCoverage = builder.expectedCoverage;
    if (expectedCoverage != null && !registryIdentityDigest.equals(expectedCoverage.getRegistryIdentityDigest())) {
      throw new IllegalArgumentException("Expected coverage must be bound to the same plant constraint registry");
    }

    List<PlantConstraintEvidence> rows = new ArrayList<PlantConstraintEvidence>();
    Map<String, PlantConstraintEvidence> indexed = new LinkedHashMap<String, PlantConstraintEvidence>();
    for (PlantConstraintDefinition definition : builder.registry.getDefinitions()) {
      PlantConstraintEvidence row = new PlantConstraintEvidence(definition,
          builder.samples.get(definition.getQualifiedId()), calculationId, nearLimitThreshold, convergenceComplete);
      rows.add(row);
      indexed.put(row.getQualifiedConstraintId(), row);
    }
    evidence = Collections.unmodifiableList(rows);
    evidenceById = Collections.unmodifiableMap(indexed);
    bottleneckLadder = Collections.unmodifiableList(buildBottleneckLadder(rows));
    List<String> coverage = buildCoverageDiagnostics(rows);
    if (!convergenceComplete) {
      coverage.add("INCOMPLETE_CONVERGENCE");
    }
    if (expectedCoverage != null) {
      if (!builder.convergenceDeclared) {
        coverage.add("CONVERGENCE_NOT_DECLARED");
      }
      if (!expectedCoverage.isComplete()) {
        coverage.add("INCOMPLETE_EXPECTED_COVERAGE");
        for (String diagnostic : expectedCoverage.getDiagnostics()) {
          coverage.add("EXPECTED_COVERAGE:" + diagnostic);
        }
      }
      for (String expectedId : expectedCoverage.getRequiredConstraintIds()) {
        if (!indexed.containsKey(expectedId)) {
          coverage.add("UNREGISTERED_EXPECTED_CONSTRAINT:" + expectedId);
        }
      }
    }
    coverageDiagnostics = Collections.unmodifiableList(coverage);
    feasibilityDiagnostics = Collections.unmodifiableList(buildFeasibilityDiagnostics(rows));
    complete = coverageDiagnostics.isEmpty();
    feasible = complete && feasibilityDiagnostics.isEmpty();
  }

  /**
   * Starts a JPype-friendly complete-snapshot builder.
   *
   * @param registry immutable registration source for all retained rows
   * @param calculationId exact completed process calculation identity
   * @return new builder
   */
  public static Builder builder(PlantConstraintRegistry registry, String calculationId) {
    return new Builder(registry, calculationId);
  }

  private static double validateNearLimitThreshold(double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException("Near-limit threshold must be finite and in [0, 1]");
    }
    return value;
  }

  private static List<PlantConstraintEvidence> buildBottleneckLadder(List<PlantConstraintEvidence> rows) {
    List<PlantConstraintEvidence> ladder = new ArrayList<PlantConstraintEvidence>();
    for (PlantConstraintEvidence row : rows) {
      if (row.getDefinition().isEnabled() && row.hasAvailableEvidence()) {
        ladder.add(row);
      }
    }
    Collections.sort(ladder, new Comparator<PlantConstraintEvidence>() {
      @Override
      public int compare(PlantConstraintEvidence first, PlantConstraintEvidence second) {
        int utilizationOrder = Double.compare(second.getNormalizedUtilization(), first.getNormalizedUtilization());
        return utilizationOrder != 0 ? utilizationOrder
            : first.getQualifiedConstraintId().compareTo(second.getQualifiedConstraintId());
      }
    });
    return ladder;
  }

  private static List<String> buildCoverageDiagnostics(List<PlantConstraintEvidence> rows) {
    List<String> diagnostics = new ArrayList<String>();
    for (PlantConstraintEvidence row : rows) {
      if (row.getCoverageStatus() != PlantConstraintEvidence.CoverageStatus.AVAILABLE
          && row.getCoverageStatus() != PlantConstraintEvidence.CoverageStatus.DISABLED) {
        diagnostics.add(row.getQualifiedConstraintId() + "=" + row.getDiagnostic());
      }
    }
    return diagnostics;
  }

  private static List<String> buildFeasibilityDiagnostics(List<PlantConstraintEvidence> rows) {
    List<String> diagnostics = new ArrayList<String>();
    for (PlantConstraintEvidence row : rows) {
      if (row.hasAvailableEvidence() && row.isHardConstraint()
          && row.getOperatingStatus() == PlantConstraintEvidence.OperatingStatus.VIOLATED) {
        diagnostics.add(row.getQualifiedConstraintId() + "=VIOLATED");
      }
    }
    return diagnostics;
  }

  /** @return snapshot schema version */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  /** @return exact completed process calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return deterministic SHA-256 identity of the source registry */
  public String getRegistryIdentityDigest() {
    return registryIdentityDigest;
  }

  /** @return dimensionless threshold used to classify near-limit evidence */
  public double getNearLimitThreshold() {
    return nearLimitThreshold;
  }

  /** @return true when the caller declared the full process calculation converged */
  public boolean isConvergenceComplete() {
    return convergenceComplete;
  }

  /** @return deterministic immutable list containing every registered row */
  public List<PlantConstraintEvidence> getEvidence() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintEvidence>(evidence));
  }

  /**
   * Returns one exact assessed row.
   *
   * @param qualifiedConstraintId exact registry-qualified identity
   * @return assessed row, or null when absent
   */
  public PlantConstraintEvidence getEvidence(String qualifiedConstraintId) {
    return evidenceById.get(qualifiedConstraintId);
  }

  /** @return immutable available-evidence ranking in descending utilization order */
  public List<PlantConstraintEvidence> getBottleneckLadder() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintEvidence>(bottleneckLadder));
  }

  /** @return first available limiting row, or null when no enabled evidence is available */
  public PlantConstraintEvidence getBottleneck() {
    return bottleneckLadder.isEmpty() ? null : bottleneckLadder.get(0);
  }

  /**
   * @return true when every enabled registration has valid exact-calculation evidence and any supplied expected scope
   * is complete; without an expected scope this does not establish complete equipment coverage
   */
  public boolean isComplete() {
    return complete;
  }

  /** @return true when coverage is complete and no hard or critical limit is violated */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return immutable expected-coverage preflight, or null for the legacy registration-only path */
  public UtilizationCoverageReport getExpectedCoverage() {
    return expectedCoverage;
  }

  /** @return immutable deterministic list of incomplete-coverage diagnostics */
  public List<String> getCoverageDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(coverageDiagnostics));
  }

  /** @return immutable deterministic list of hard-limit violation diagnostics */
  public List<String> getFeasibilityDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(feasibilityDiagnostics));
  }

  /** Callback-free builder suitable for Java and JPype callers. */
  public static final class Builder {
    private final PlantConstraintRegistry registry;
    private final String calculationId;
    private final Map<String, PlantConstraintSample> samples = new LinkedHashMap<String, PlantConstraintSample>();
    private double nearLimitThreshold = DEFAULT_NEAR_LIMIT_THRESHOLD;
    private boolean convergenceComplete = true;
    private boolean convergenceDeclared;
    private UtilizationCoverageReport expectedCoverage;

    private Builder(PlantConstraintRegistry registry, String calculationId) {
      if (registry == null) {
        throw new IllegalArgumentException("Plant constraint registry is required");
      }
      this.registry = registry;
      this.calculationId = PlantConstraintScope.requireText(calculationId, "Calculation id");
    }

    /**
     * Adds one exact-identity runtime sample.
     *
     * @param sample immutable callback-free sample
     * @return this builder
     */
    public Builder sample(PlantConstraintSample sample) {
      if (sample == null) {
        throw new IllegalArgumentException("Plant constraint sample is required");
      }
      String identity = sample.getQualifiedConstraintId();
      if (!registry.contains(identity)) {
        throw new IllegalArgumentException("Unknown plant constraint sample identity " + identity);
      }
      if (samples.containsKey(identity)) {
        throw new IllegalArgumentException("Duplicate plant constraint sample identity " + identity);
      }
      samples.put(identity, sample);
      return this;
    }

    /**
     * Sets the dimensionless near-limit threshold.
     *
     * @param value finite value in [0, 1]
     * @return this builder
     */
    public Builder nearLimitThreshold(double value) {
      nearLimitThreshold = validateNearLimitThreshold(value);
      return this;
    }

    /**
     * Records whether the full process calculation completed convergence.
     *
     * @param value true only after all applicable convergence checks completed
     * @return this builder
     */
    public Builder convergenceComplete(boolean value) {
      convergenceComplete = value;
      convergenceDeclared = true;
      return this;
    }

    /**
     * Binds the independently declared equipment/constraint scope to this snapshot.
     *
     * <p>
     * The report must reference the exact registry digest. Its values are preflight evidence; the supplied runtime
     * samples still establish exact-calculation feasibility. Call {@link #convergenceComplete(boolean)} explicitly
     * after the model solve. The report is immutable and no equipment supplier is called by this method or build.
     * </p>
     *
     * @param report registry-bound expected-coverage report
     * @return this builder
     * @throws IllegalArgumentException if the report is null or was bound to a different registry
     */
    public Builder expectedCoverage(UtilizationCoverageReport report) {
      if (report == null || !registry.getIdentityDigest().equals(report.getRegistryIdentityDigest())) {
        throw new IllegalArgumentException("Expected coverage must be bound to the same plant constraint registry");
      }
      expectedCoverage = report;
      return this;
    }

    /** @return complete immutable snapshot with one row per registry definition */
    public PlantUtilizationSnapshot build() {
      return new PlantUtilizationSnapshot(this);
    }
  }
}
