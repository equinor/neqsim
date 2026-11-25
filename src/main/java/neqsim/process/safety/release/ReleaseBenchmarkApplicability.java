package neqsim.process.safety.release;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    MATERIAL_LIQUID,
    /** Coexisting gas and material-liquid phases in the benchmark upstream state. */
    GAS_LIQUID_MIXTURE,
    /**
     * One supported material phase, without relying on an EOS gas-versus-liquid phase label.
     *
     * <p>
     * This is intended for compressed or supercritical single-fluid benchmark states where the external experiment
     * establishes a homogeneous feed but the EOS may label that sole dense phase as gas or liquid. It never accepts a
     * gas/liquid split, aqueous coexistence, solids or an unsupported native phase.
     */
    SINGLE_MATERIAL_PHASE
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
    UNSUPPORTED_UPSTREAM_PHASE,
    /** The release calculation failed or rejected the requested physical path. */
    RESULT_UNUSABLE
  }

  private final ExpectedPhase expectedPhase;
  private final Status status;
  private final double expectedPhaseMassFraction;
  private final Map<String, Double> resolvedPhaseMassFractions;
  private final List<String> resultDiagnosticCodes;

  private ReleaseBenchmarkApplicability(ExpectedPhase expectedPhase, Status status, double expectedPhaseMassFraction,
      Map<String, Double> resolvedPhaseMassFractions, List<String> resultDiagnosticCodes) {
    this.expectedPhase = expectedPhase;
    this.status = status;
    this.expectedPhaseMassFraction = expectedPhaseMassFraction;
    this.resolvedPhaseMassFractions = Collections
        .unmodifiableMap(new TreeMap<String, Double>(resolvedPhaseMassFractions));
    this.resultDiagnosticCodes = Collections.unmodifiableList(new ArrayList<String>(resultDiagnosticCodes));
  }

  /**
   * Assesses whether a release result represents the published withdrawal phase.
   *
   * <p>
   * Unusable model results return {@link Status#RESULT_UNUSABLE} and retain their diagnostic codes. No station or rate
   * is recovered from a failed result.
   *
   * @param result release result
   * @param expectedPhase phase withdrawn in the external experiment
   * @return immutable applicability assessment
   */
  public static ReleaseBenchmarkApplicability assess(ReleaseFlowResult result, ExpectedPhase expectedPhase) {
    if (result == null || expectedPhase == null) {
      throw new IllegalArgumentException("A result and expected phase are required");
    }
    List<String> diagnosticCodes = new ArrayList<String>();
    for (ReleaseFlowResult.Diagnostic diagnostic : result.getDiagnostics()) {
      diagnosticCodes.add(diagnostic.getCode());
    }
    if (!result.isUsable()) {
      return new ReleaseBenchmarkApplicability(expectedPhase, Status.RESULT_UNUSABLE, Double.NaN,
          Collections.<String, Double>emptyMap(), diagnosticCodes);
    }
    ReleaseState upstream = result.getStations().get(Station.UPSTREAM_STAGNATION);
    if (upstream == null) {
      throw new IllegalArgumentException("Result must retain an upstream station");
    }
    Map<String, Double> phases = upstream.getPhaseMassFractions();
    double gasFraction = value(phases, "GAS");
    double liquidFraction = value(phases, "OIL") + value(phases, "LIQUID") + value(phases, "AQUEOUS");
    double supportedFraction = gasFraction + liquidFraction;
    double expectedFraction;
    if (expectedPhase == ExpectedPhase.GAS) {
      expectedFraction = gasFraction;
    } else if (expectedPhase == ExpectedPhase.MATERIAL_LIQUID) {
      expectedFraction = liquidFraction;
    } else if (expectedPhase == ExpectedPhase.GAS_LIQUID_MIXTURE) {
      expectedFraction = Math.min(gasFraction, liquidFraction);
    } else {
      expectedFraction = supportedFraction;
    }
    Status status;
    if (supportedFraction < 1.0 - PURE_PHASE_TOLERANCE) {
      status = Status.UNSUPPORTED_UPSTREAM_PHASE;
    } else if (expectedPhase == ExpectedPhase.GAS_LIQUID_MIXTURE
        && (gasFraction <= PURE_PHASE_TOLERANCE || liquidFraction <= PURE_PHASE_TOLERANCE)) {
      status = Status.EXPECTED_PHASE_ABSENT;
    } else if (expectedPhase == ExpectedPhase.GAS_LIQUID_MIXTURE) {
      status = Status.COMPARABLE;
    } else if (expectedPhase == ExpectedPhase.SINGLE_MATERIAL_PHASE && activeMaterialPhases(phases) != 1) {
      status = Status.MIXED_UPSTREAM;
    } else if (expectedFraction <= PURE_PHASE_TOLERANCE) {
      status = Status.EXPECTED_PHASE_ABSENT;
    } else if (expectedFraction < 1.0 - PURE_PHASE_TOLERANCE) {
      status = Status.MIXED_UPSTREAM;
    } else {
      status = Status.COMPARABLE;
    }
    return new ReleaseBenchmarkApplicability(expectedPhase, status, expectedFraction, phases, diagnosticCodes);
  }

  private static int activeMaterialPhases(Map<String, Double> phases) {
    int active = 0;
    String[] materialPhases = {"GAS", "OIL", "LIQUID", "AQUEOUS"};
    for (String phase : materialPhases) {
      if (value(phases, phase) > PURE_PHASE_TOLERANCE) {
        active++;
      }
    }
    return active;
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

  /**
   * @return resolved mass fraction of the expected material phase, the smaller constituent fraction for a gas/liquid
   * mixture, or NaN when the model result is unusable
   */
  public double getExpectedPhaseMassFraction() {
    return expectedPhaseMassFraction;
  }

  /** @return immutable native upstream phase mass fractions */
  public Map<String, Double> getResolvedPhaseMassFractions() {
    return resolvedPhaseMassFractions;
  }

  /** @return immutable calculation diagnostic codes retained for the applicability decision */
  public List<String> getResultDiagnosticCodes() {
    return resultDiagnosticCodes;
  }

  /** @return whether a rate-error comparison is physically applicable */
  public boolean isComparable() {
    return status == Status.COMPARABLE;
  }
}
