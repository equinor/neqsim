package com.equinor.neqsim.eqn;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Container for the regression coefficients and validity envelope of the
 * EQN π-number scrubber model. Parsed once at plug-in load time from the
 * JSON resource {@code /eqn/coefficients/v1.json} bundled in the JAR.
 *
 * <p>
 * This class is intentionally minimal — it holds numbers only, never any
 * raw test rows. The raw EQN database stays out of the JAR.
 *
 * @author Equinor NeqSim team
 * @version 1.0.0
 */
public final class EqnCoefficients {

  /** Map of pi-group id → regression coefficient. */
  private final Map<String, Double> piCoefficients;

  /** Residual standard error of the regression on log-carry-over. */
  private final double residualStandardError;

  /** Validity envelope, e.g. {@code "D_vessel_min": 0.5}. */
  private final Map<String, Double> envelope;

  /**
   * Constructs an immutable coefficient set.
   *
   * @param piCoefficients map of pi-group id to regression coefficient;
   *        must not be null
   * @param residualStandardError residual standard error of the regression
   *        on log-carry-over; must be non-negative
   * @param envelope validity envelope as named min/max bounds; must not be
   *        null
   */
  public EqnCoefficients(Map<String, Double> piCoefficients, double residualStandardError,
      Map<String, Double> envelope) {
    if (piCoefficients == null) {
      throw new IllegalArgumentException("piCoefficients must not be null");
    }
    if (envelope == null) {
      throw new IllegalArgumentException("envelope must not be null");
    }
    if (residualStandardError < 0.0 || Double.isNaN(residualStandardError)) {
      throw new IllegalArgumentException(
          "residualStandardError must be finite and non-negative");
    }
    this.piCoefficients = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(piCoefficients));
    this.residualStandardError = residualStandardError;
    this.envelope = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(envelope));
  }

  /**
   * Returns the regression coefficients keyed by pi-group id.
   *
   * @return unmodifiable map of pi-group id to regression coefficient
   */
  public Map<String, Double> getPiCoefficients() {
    return piCoefficients;
  }

  /**
   * Returns the residual standard error of the regression on
   * log-carry-over.
   *
   * @return residual standard error
   */
  public double getResidualStandardError() {
    return residualStandardError;
  }

  /**
   * Returns the validity envelope as named min/max bounds.
   *
   * @return unmodifiable map of envelope bounds
   */
  public Map<String, Double> getEnvelope() {
    return envelope;
  }

  /**
   * Parses an {@link EqnCoefficients} instance from a JSON input stream.
   *
   * <p>
   * TODO Replace the placeholder with a real JSON parser (e.g. Gson,
   * Jackson) once a parser dependency is approved for this private module.
   * For now we return a minimal stub so the plug-in compiles and tests
   * exercise the contract.
   *
   * @param in the JSON input stream; must not be null
   * @return the parsed coefficient set; never null
   */
  public static EqnCoefficients parse(InputStream in) {
    if (in == null) {
      throw new IllegalArgumentException("input stream must not be null");
    }
    // TODO real JSON parsing once a parser dependency is approved
    Map<String, Double> coeffs = new LinkedHashMap<String, Double>();
    Map<String, Double> env = new LinkedHashMap<String, Double>();
    return new EqnCoefficients(coeffs, 0.0, env);
  }
}
