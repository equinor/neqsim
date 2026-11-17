package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import neqsim.process.safety.release.ReleaseFlowResult.Station;

/**
 * Fail-closed applicability check between a published withdrawal phase and a release result.
 *
 * <p>
 * A numerical rate is not experimental validation when the model's resolved upstream material phase differs from the
 * experimental withdrawal phase. This helper retains that distinction without modifying either the immutable request or
 * result.
 */
public final class ReleaseBenchmarkApplicability implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final double PURE_PHASE_TOLERANCE = 1.0e-6;

  /** Coarse material phase declared by an external benchmark. */
  public enum ExpectedPhase {
    /** Gas or vapour withdrawal. */
    GAS,
    /** Hydrocarbon, generic or aqueous material-liquid withdrawal. */
    MATERIAL_LIQUID
  }

  /** Applicability of the model result to the benchmark withdrawal phase. */
  public enum Status {
    /** The resolved upstream phase matches the declared benchmark phase. */
    COMPARABLE,
    /** The declared benchmark phase is absent from the resolved upstream state. */
    EXPECTED_PHASE_ABSENT,
    /** The declared phase exists, but the resolved upstream state is materially multiphase. */
    MIXED_UPSTREAM,
    /** A solid or otherwise unsupported native phase prevents a phase-only comparison. */
    UNSUPPORTED_UPSTREAM_PHASE
  }

  private final ExpectedPhase expectedPhase;
  private final Status status;
  private final double expectedPhaseMassFraction;
  private final Map<String, Double> resolvedPhaseMassFractions;

  private ReleaseBenchmarkApplicability(ExpectedPhase expectedPhase, Status status, double expectedPhaseMassFraction,
      Map<String, Double> resolvedPhaseMassFractions) {
    this.expectedPhase = expectedPhase;
    this.status = status;
    this.expectedPhaseMassFraction = expectedPhaseMassFraction;
    this.resolvedPhaseMassFractions = Collections
        .unmodifiableMap(new TreeMap<String, Double>(resolvedPhaseMassFractions));
  }

  /**
   * Assesses whether a usable release result represents the published withdrawal phase.
   *
   * @param result usable release result retaining an upstream station
   * @param expectedPhase phase withdrawn in the external experiment
   * @return immutable applicability assessment
   */
  public static ReleaseBenchmarkApplicability assess(ReleaseFlowResult result, ExpectedPhase expectedPhase) {
    if (result == null || !result.isUsable() || expectedPhase == null) {
      throw new IllegalArgumentException("A usable result and expected phase are required");
    }
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    if (upstream == null) {
      throw new IllegalArgumentException("Result must retain an upstream station");
    }
    Map<String, Double> phases = upstream.getPhaseMassFractions();
    double gasFraction = value(phases, "GAS");
    double liquidFraction = value(phases, "OIL") + value(phases, "LIQUID") + value(phases, "AQUEOUS");
    double supportedFraction = gasFraction + liquidFraction;
    double expectedFraction = expectedPhase == ExpectedPhase.GAS ? gasFraction : liquidFraction;
    Status status;
    if (supportedFraction < 1.0 - PURE_PHASE_TOLERANCE) {
      status = Status.UNSUPPORTED_UPSTREAM_PHASE;
    } else if (expectedFraction <= PURE_PHASE_TOLERANCE) {
      status = Status.EXPECTED_PHASE_ABSENT;
    } else if (expectedFraction < 1.0 - PURE_PHASE_TOLERANCE) {
      status = Status.MIXED_UPSTREAM;
    } else {
      status = Status.COMPARABLE;
    }
    return new ReleaseBenchmarkApplicability(expectedPhase, status, expectedFraction, phases);
  }

  private static double value(Map<String, Double> values, String key) {
    Double value = values.get(key);
    return value == null ? 0.0 : value.doubleValue();
  }

  /** @return benchmark-declared material phase */
  public ExpectedPhase getExpectedPhase() {
    return expectedPhase;
  }

  /** @return explicit fail-closed comparison status */
  public Status getStatus() {
    return status;
  }

  /** @return resolved mass fraction of the expected material phase */
  public double getExpectedPhaseMassFraction() {
    return expectedPhaseMassFraction;
  }

  /** @return immutable native upstream phase mass fractions */
  public Map<String, Double> getResolvedPhaseMassFractions() {
    return resolvedPhaseMassFractions;
  }

  /** @return whether a rate-error comparison is physically applicable */
  public boolean isComparable() {
    return status == Status.COMPARABLE;
  }
}
