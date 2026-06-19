package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rule-based SPE-PRMS / NPD resource classification helper.
 *
 * <p>
 * Maps a discrete project maturity stage onto the high-level petroleum resource categories defined
 * by the Petroleum Resources Management System (SPE-PRMS) and used by the Norwegian Petroleum
 * Directorate (NPD/Sokkeldirektoratet):
 * </p>
 *
 * <table>
 * <caption>SPE-PRMS resource categories used by this helper</caption>
 * <tr>
 * <th>Category</th>
 * <th>PRMS class</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>RESERVES</td>
 * <td>1-3</td>
 * <td>Discovered, commercial, recoverable</td>
 * </tr>
 * <tr>
 * <td>CONTINGENT_RESOURCES</td>
 * <td>4-6</td>
 * <td>Discovered, sub-commercial, recoverable</td>
 * </tr>
 * <tr>
 * <td>PROSPECTIVE_RESOURCES</td>
 * <td>7+</td>
 * <td>Undiscovered, potentially recoverable</td>
 * </tr>
 * <tr>
 * <td>UNRECOVERABLE</td>
 * <td>n/a</td>
 * <td>Maturity stage not recognised</td>
 * </tr>
 * </table>
 *
 * <p>
 * This is a deterministic mapping aid, not a volumetric estimate. It does not assign volumes,
 * recovery factors, or chance of commerciality. A formal SPE-PRMS or NPD estimate with qualified
 * subsurface review is required for project decisions.
 * </p>
 *
 * @author NeqSim Community
 * @version 1.0
 */
public final class ReservesClassification implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** High-level SPE-PRMS resource category. */
  public enum ResourceCategory {
    /** Discovered, commercial, recoverable (PRMS classes 1-3). */
    RESERVES,
    /** Discovered, sub-commercial, recoverable (PRMS classes 4-6). */
    CONTINGENT_RESOURCES,
    /** Undiscovered, potentially recoverable (PRMS classes 7+). */
    PROSPECTIVE_RESOURCES,
    /** Maturity stage not recognised. */
    UNRECOVERABLE
  }

  private static final Map<String, String> RESERVES_STAGES;
  private static final Map<String, String> CONTINGENT_STAGES;
  private static final Map<String, String> PROSPECTIVE_STAGES;

  static {
    Map<String, String> reserves = new LinkedHashMap<String, String>();
    reserves.put("on_production", "PRMS class 1 (on production)");
    reserves.put("approved_for_development", "PRMS class 2 (approved for development)");
    reserves.put("justified_for_development", "PRMS class 3 (justified for development)");
    RESERVES_STAGES = Collections.unmodifiableMap(reserves);

    Map<String, String> contingent = new LinkedHashMap<String, String>();
    contingent.put("development_pending", "PRMS class 4 (development pending)");
    contingent.put("development_on_hold", "PRMS class 5 (development on hold)");
    contingent.put("development_unclarified", "PRMS class 6 (development unclarified or on hold)");
    CONTINGENT_STAGES = Collections.unmodifiableMap(contingent);

    Map<String, String> prospective = new LinkedHashMap<String, String>();
    prospective.put("prospect", "PRMS class 7 (prospect)");
    prospective.put("lead", "PRMS class 8 (lead)");
    prospective.put("play", "PRMS class 9 (play)");
    PROSPECTIVE_STAGES = Collections.unmodifiableMap(prospective);
  }

  /**
   * Immutable result of a resource classification.
   *
   * @author NeqSim Community
   * @version 1.0
   */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String resourceClass;
    private final ResourceCategory resourceCategory;
    private final String prmsClassRange;
    private final String maturityWarning;

    /**
     * Creates a classification result.
     *
     * @param resourceClass the normalised maturity stage identifier
     * @param resourceCategory the high-level resource category
     * @param prmsClassRange a human-readable PRMS class label
     * @param maturityWarning screening flag: {@code "ok"}, {@code "watch"}, or
     *        {@code "unclassified"}
     */
    public Result(String resourceClass, ResourceCategory resourceCategory, String prmsClassRange,
        String maturityWarning) {
      this.resourceClass = resourceClass;
      this.resourceCategory = resourceCategory;
      this.prmsClassRange = prmsClassRange;
      this.maturityWarning = maturityWarning;
    }

    /**
     * Returns the normalised maturity stage identifier.
     *
     * @return the normalised maturity stage (lower case, underscore separated)
     */
    public String getResourceClass() {
      return resourceClass;
    }

    /**
     * Returns the high-level resource category.
     *
     * @return the {@link ResourceCategory}
     */
    public ResourceCategory getResourceCategory() {
      return resourceCategory;
    }

    /**
     * Returns the human-readable PRMS class label.
     *
     * @return the PRMS class range label, or {@code "unclassified"} when unrecognised
     */
    public String getPrmsClassRange() {
      return prmsClassRange;
    }

    /**
     * Returns the screening warning flag.
     *
     * @return {@code "ok"}, {@code "watch"}, or {@code "unclassified"}
     */
    public String getMaturityWarning() {
      return maturityWarning;
    }
  }

  /**
   * Classifies a maturity stage assuming commerciality is unknown.
   *
   * @param maturityStage the project maturity stage (e.g. {@code "on production"},
   *        {@code "development pending"}, {@code "prospect"}); must be non-null and non-blank
   * @return the {@link Result} of the classification
   * @throws IllegalArgumentException if {@code maturityStage} is null or blank
   */
  public Result classify(String maturityStage) {
    return classify(maturityStage, null);
  }

  /**
   * Classifies a maturity stage against the SPE-PRMS categories.
   *
   * @param maturityStage the project maturity stage (e.g. {@code "on production"},
   *        {@code "development pending"}, {@code "prospect"}); must be non-null and non-blank
   * @param commercial whether the volumes are judged commercial; may be {@code null} when unknown.
   *        When {@link Boolean#FALSE} on a reserves-stage input the warning is raised to
   *        {@code "watch"} because reserves require commercial volumes.
   * @return the {@link Result} of the classification
   * @throws IllegalArgumentException if {@code maturityStage} is null or blank
   */
  public Result classify(String maturityStage, Boolean commercial) {
    if (maturityStage == null || maturityStage.trim().isEmpty()) {
      throw new IllegalArgumentException("maturityStage must be a non-empty string");
    }

    String normalized = normalize(maturityStage);

    if (RESERVES_STAGES.containsKey(normalized)) {
      String warning = Boolean.FALSE.equals(commercial) ? "watch" : "ok";
      return new Result(normalized, ResourceCategory.RESERVES, RESERVES_STAGES.get(normalized),
          warning);
    }
    if (CONTINGENT_STAGES.containsKey(normalized)) {
      return new Result(normalized, ResourceCategory.CONTINGENT_RESOURCES,
          CONTINGENT_STAGES.get(normalized), "ok");
    }
    if (PROSPECTIVE_STAGES.containsKey(normalized)) {
      return new Result(normalized, ResourceCategory.PROSPECTIVE_RESOURCES,
          PROSPECTIVE_STAGES.get(normalized), "ok");
    }
    return new Result(normalized, ResourceCategory.UNRECOVERABLE, "unclassified", "unclassified");
  }

  /**
   * Normalises a maturity stage string to a lower-case, underscore-separated identifier.
   *
   * @param maturityStage the raw maturity stage text; must not be null
   * @return the normalised identifier
   */
  static String normalize(String maturityStage) {
    String text = maturityStage.trim().toLowerCase();
    text = text.replace(' ', '_').replace('-', '_').replace('/', '_');
    while (text.contains("__")) {
      text = text.replace("__", "_");
    }
    return text;
  }
}
