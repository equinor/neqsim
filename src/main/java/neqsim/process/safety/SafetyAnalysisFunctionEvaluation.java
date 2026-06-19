package neqsim.process.safety;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Simplified API RP 14C / ISO 10418 protective safety-function coverage evaluation.
 *
 * <p>
 * Compares the protective (Safety Analysis Function) devices provided on a process component against the simplified set
 * required by API RP 14C (Analysis, Design, Installation, and Testing of Basic Surface Safety Systems for Offshore
 * Production Platforms) and ISO 10418. The standard function codes used here are:
 * </p>
 *
 * <table>
 * <caption>Protective function codes</caption>
 * <tr>
 * <th>Code</th>
 * <th>Function</th>
 * </tr>
 * <tr>
 * <td>PSH</td>
 * <td>Pressure Safety High (high-pressure shutdown)</td>
 * </tr>
 * <tr>
 * <td>PSL</td>
 * <td>Pressure Safety Low (low-pressure shutdown)</td>
 * </tr>
 * <tr>
 * <td>PSV</td>
 * <td>Pressure Safety Valve (relief)</td>
 * </tr>
 * <tr>
 * <td>LSH</td>
 * <td>Level Safety High</td>
 * </tr>
 * <tr>
 * <td>LSL</td>
 * <td>Level Safety Low</td>
 * </tr>
 * <tr>
 * <td>TSH</td>
 * <td>Temperature Safety High</td>
 * </tr>
 * <tr>
 * <td>BSDV</td>
 * <td>Boarding / blowdown shutdown valve</td>
 * </tr>
 * </table>
 *
 * <p>
 * This is a simplified screening aid. It performs no undesirable-event analysis, does not credit alternate protection
 * per the API RP 14C SAFE-chart methodology, and does not assess device reliability or testing. A formal SAFE chart
 * prepared by a qualified safety engineer is required for design decisions.
 * </p>
 *
 * @author NeqSim Community
 * @version 1.0
 */
public final class SafetyAnalysisFunctionEvaluation implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final Map<String, List<String>> REQUIRED_FUNCTIONS;

  static {
    Map<String, List<String>> req = new LinkedHashMap<String, List<String>>();
    req.put("pressure_vessel", listOf("PSH", "PSL", "PSV", "LSH"));
    req.put("separator", listOf("PSH", "PSL", "PSV", "LSH", "LSL"));
    req.put("gas_pipeline_segment", listOf("PSH", "PSL", "PSV"));
    req.put("liquid_pipeline_segment", listOf("PSH", "PSL", "PSV"));
    req.put("fired_heater", listOf("PSH", "PSL", "TSH", "PSV", "BSDV"));
    req.put("compressor", listOf("PSH", "PSL", "TSH", "PSV"));
    req.put("pump", listOf("PSH", "PSL", "PSV"));
    req.put("wellhead", listOf("PSH", "PSL", "PSV", "BSDV"));
    REQUIRED_FUNCTIONS = Collections.unmodifiableMap(req);
  }

  private final double watchThreshold;

  /**
   * Creates an evaluator with the default watch threshold of 1.0 (full coverage required).
   */
  public SafetyAnalysisFunctionEvaluation() {
    this(1.0);
  }

  /**
   * Creates an evaluator with a custom coverage watch threshold.
   *
   * @param watchThreshold the coverage ratio below which a {@code "watch"} flag is raised even with no missing
   *                       functions; must be in the interval (0, 1]
   * @throws IllegalArgumentException if {@code watchThreshold} is not in (0, 1]
   */
  public SafetyAnalysisFunctionEvaluation(double watchThreshold) {
    if (!(watchThreshold > 0.0) || watchThreshold > 1.0) {
      throw new IllegalArgumentException("watchThreshold must be in the interval (0, 1]");
    }
    this.watchThreshold = watchThreshold;
  }

  /**
   * Immutable safety-function coverage result.
   *
   * @author NeqSim Community
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String componentType;
    private final List<String> requiredFunctions;
    private final List<String> providedFunctions;
    private final List<String> missingFunctions;
    private final double coverageRatio;
    private final String coverageWarning;

    /**
     * Creates a coverage result.
     *
     * @param componentType     the normalised component type
     * @param requiredFunctions the required protective function codes
     * @param providedFunctions the normalised provided function codes
     * @param missingFunctions  the required codes not provided
     * @param coverageRatio     the fraction of required functions provided (0-1)
     * @param coverageWarning   screening flag: {@code "ok"}, {@code "watch"}, or {@code "gap"}
     */
    public Result(String componentType, List<String> requiredFunctions, List<String> providedFunctions,
	List<String> missingFunctions, double coverageRatio, String coverageWarning) {
      this.componentType = componentType;
      this.requiredFunctions = Collections.unmodifiableList(requiredFunctions);
      this.providedFunctions = Collections.unmodifiableList(providedFunctions);
      this.missingFunctions = Collections.unmodifiableList(missingFunctions);
      this.coverageRatio = coverageRatio;
      this.coverageWarning = coverageWarning;
    }

    /**
     * Returns the normalised component type.
     *
     * @return the component type key
     */
    public String getComponentType() {
      return componentType;
    }

    /**
     * Returns the required protective function codes.
     *
     * @return an unmodifiable list of required function codes
     */
    public List<String> getRequiredFunctions() {
      return requiredFunctions;
    }

    /**
     * Returns the provided protective function codes.
     *
     * @return an unmodifiable list of provided function codes
     */
    public List<String> getProvidedFunctions() {
      return providedFunctions;
    }

    /**
     * Returns the required function codes that are not provided.
     *
     * @return an unmodifiable list of missing function codes (empty when fully covered)
     */
    public List<String> getMissingFunctions() {
      return missingFunctions;
    }

    /**
     * Returns the coverage ratio.
     *
     * @return the fraction of required functions provided, in the range 0-1
     */
    public double getCoverageRatio() {
      return coverageRatio;
    }

    /**
     * Returns the screening warning flag.
     *
     * @return {@code "ok"}, {@code "watch"}, or {@code "gap"}
     */
    public String getCoverageWarning() {
      return coverageWarning;
    }
  }

  /**
   * Evaluates the protective-function coverage of a component.
   *
   * @param componentType     the component type; case-insensitive, spaces/hyphens are normalised to underscores. Must
   *                          be one of the supported types (see {@link #getSupportedComponentTypes()}).
   * @param providedFunctions the protective function codes provided on the component; case-insensitive, duplicates
   *                          ignored. Must not be null.
   * @return the {@link Result} of the coverage evaluation
   * @throws IllegalArgumentException if {@code componentType} is null or unsupported, or if {@code providedFunctions}
   *                                  is null or contains a blank code
   */
  public Result evaluate(String componentType, Iterable<String> providedFunctions) {
    String normalizedType = normalizeType(componentType);
    List<String> required = REQUIRED_FUNCTIONS.get(normalizedType);
    List<String> provided = normalizeFunctions(providedFunctions);

    List<String> missing = new ArrayList<String>();
    for (int i = 0; i < required.size(); i++) {
      String code = required.get(i);
      if (!provided.contains(code)) {
	missing.add(code);
      }
    }

    double coverageRatio = (required.size() - missing.size()) / (double) required.size();
    String warning = warning(missing, coverageRatio);

    return new Result(normalizedType, required, provided, missing, Math.round(coverageRatio * 10000.0) / 10000.0,
	warning);
  }

  /**
   * Returns the sorted set of supported component types.
   *
   * @return an unmodifiable sorted set of supported component-type keys
   */
  public static java.util.Set<String> getSupportedComponentTypes() {
    return Collections.unmodifiableSet(new TreeSet<String>(REQUIRED_FUNCTIONS.keySet()));
  }

  /**
   * Determines the screening warning flag from the missing functions and coverage ratio.
   *
   * @param missing       the missing function codes
   * @param coverageRatio the coverage ratio (0-1)
   * @return {@code "gap"} when any function is missing, {@code "watch"} when coverage is below the watch threshold,
   *         otherwise {@code "ok"}
   */
  private String warning(List<String> missing, double coverageRatio) {
    if (!missing.isEmpty()) {
      return "gap";
    }
    if (coverageRatio < watchThreshold) {
      return "watch";
    }
    return "ok";
  }

  /**
   * Normalises and validates a component type.
   *
   * @param componentType the raw component type
   * @return the normalised component-type key
   * @throws IllegalArgumentException if null or not a supported type
   */
  private static String normalizeType(String componentType) {
    if (componentType == null) {
      throw new IllegalArgumentException("componentType must not be null");
    }
    String key = componentType.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    if (!REQUIRED_FUNCTIONS.containsKey(key)) {
      StringBuilder supported = new StringBuilder();
      for (String type : new TreeSet<String>(REQUIRED_FUNCTIONS.keySet())) {
	if (supported.length() > 0) {
	  supported.append(", ");
	}
	supported.append(type);
      }
      throw new IllegalArgumentException("componentType must be one of: " + supported.toString());
    }
    return key;
  }

  /**
   * Normalises the provided function codes to upper case, removing duplicates.
   *
   * @param providedFunctions the provided function codes
   * @return an ordered list of unique, upper-case function codes
   * @throws IllegalArgumentException if null, or if any code is null or blank
   */
  private static List<String> normalizeFunctions(Iterable<String> providedFunctions) {
    if (providedFunctions == null) {
      throw new IllegalArgumentException("providedFunctions must not be null");
    }
    List<String> normalized = new ArrayList<String>();
    for (String code : providedFunctions) {
      if (code == null || code.trim().isEmpty()) {
	throw new IllegalArgumentException("providedFunctions must not contain blank codes");
      }
      String token = code.trim().toUpperCase();
      if (!normalized.contains(token)) {
	normalized.add(token);
      }
    }
    return normalized;
  }

  /**
   * Builds an unmodifiable list from the supplied codes.
   *
   * @param codes the function codes
   * @return an unmodifiable list containing the codes in order
   */
  private static List<String> listOf(String... codes) {
    List<String> list = new ArrayList<String>(codes.length);
    for (int i = 0; i < codes.length; i++) {
      list.add(codes[i]);
    }
    return Collections.unmodifiableList(list);
  }
}
