package neqsim.process.allocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Compares the results of several {@link AllocationMethod}s run on the same base case, auto-evaluates how much they
 * disagree, reports how each method would benefit each owner (source), and recommends a method.
 *
 * <p>
 * The recommendation follows the fidelity-versus-cost ladder of the three facility-split methods. Differences are
 * measured as the maximum relative difference of any source's per-product (gas / oil / water) allocation between two
 * methods:
 * </p>
 *
 * <ul>
 * <li>If the all-in proxy and the stand-alone re-simulation disagree by more than the tolerance, commingling introduces
 * material non-linear (compositional / thermal) coupling, so the most faithful method
 * ({@link AllocationMethod#STAND_ALONE}) is recommended and a base-case refresh is flagged.</li>
 * <li>Else if the common recovery factor and the all-in proxy disagree by more than the tolerance, source-dependent
 * routing matters but the coupling is weak, so the cheaper linear proxy ({@link AllocationMethod#ALL_IN}) is
 * recommended.</li>
 * <li>Otherwise all methods agree within the tolerance, so the cheapest and most auditable common recovery factor
 * ({@link AllocationMethod#COMPONENT_RATIO}) is recommended.</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class AllocationComparison {

  /** Schema version for the JSON export. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Default agreement tolerance (relative, fraction) for the auto-recommendation. */
  public static final double DEFAULT_TOLERANCE = 0.01;

  /**
   * Minimum absolute product flow (kg/hr) below which a relative difference is ignored as numerical noise.
   */
  private static final double FLOW_NOISE_FLOOR = 1.0e-6;

  /** Product categories used for owner-benefit comparison. */
  private static final ProductType[] COMPARED_PRODUCTS = new ProductType[] { ProductType.GAS, ProductType.OIL,
      ProductType.WATER };

  /** The per-method allocation results, in run order. */
  private final Map<AllocationMethod, ProductionAllocationResult> results;

  /** The per-method wall-clock runtime in milliseconds. */
  private final Map<AllocationMethod, Long> runtimeMillis;

  /** The agreement tolerance used for the recommendation. */
  private double tolerance = DEFAULT_TOLERANCE;

  /** The recommended method (computed lazily). */
  private AllocationMethod recommendedMethod;

  /** The rationale string for the recommendation (computed lazily). */
  private String recommendationRationale;

  /**
   * Creates a comparison from the given per-method results and runtimes.
   *
   * @param results the per-method allocation results, in run order; must be non-null and non-empty
   * @param runtimeMillis the per-method runtimes in milliseconds; must be non-null
   */
  public AllocationComparison(Map<AllocationMethod, ProductionAllocationResult> results,
      Map<AllocationMethod, Long> runtimeMillis) {
    this.results = new LinkedHashMap<>(results);
    this.runtimeMillis = new LinkedHashMap<>(runtimeMillis);
  }

  /**
   * Sets the agreement tolerance (relative fraction) used by {@link #getRecommendedMethod()}. Default
   * {@value #DEFAULT_TOLERANCE}.
   *
   * @param tolerance the relative tolerance; must be positive
   * @return this comparison for chaining
   */
  public AllocationComparison setTolerance(double tolerance) {
    this.tolerance = tolerance;
    this.recommendedMethod = null;
    this.recommendationRationale = null;
    return this;
  }

  /**
   * Gets the result for a method.
   *
   * @param method the method; must have been run
   * @return the result, or {@code null} if the method was not run
   */
  public ProductionAllocationResult getResult(AllocationMethod method) {
    return results.get(method);
  }

  /**
   * Gets all per-method results in run order.
   *
   * @return an unmodifiable view of the results map
   */
  public Map<AllocationMethod, ProductionAllocationResult> getResults() {
    return results;
  }

  /**
   * Gets the runtime of a method in milliseconds.
   *
   * @param method the method; must have been run
   * @return the runtime in milliseconds, or {@code 0} if the method was not run
   */
  public long getRuntimeMillis(AllocationMethod method) {
    Long value = runtimeMillis.get(method);
    return value == null ? 0L : value;
  }

  /**
   * Computes the maximum relative difference between two methods over every source's per-product (gas / oil / water)
   * allocation. Products whose larger absolute flow is below the noise floor are ignored.
   *
   * @param a the first method; must have been run
   * @param b the second method; must have been run
   * @return the maximum relative difference (fraction), or {@code 0} if either method was not run
   */
  public double getMaxRelativeDifference(AllocationMethod a, AllocationMethod b) {
    ProductionAllocationResult ra = results.get(a);
    ProductionAllocationResult rb = results.get(b);
    if (ra == null || rb == null) {
      return 0.0;
    }
    double worst = 0.0;
    for (String source : ra.getSourceNames()) {
      for (ProductType product : COMPARED_PRODUCTS) {
        double va = ra.getProductAllocation(source, product, "kg/hr");
        double vb = rb.getProductAllocation(source, product, "kg/hr");
        double denom = Math.max(Math.abs(va), Math.abs(vb));
        if (denom > FLOW_NOISE_FLOOR) {
          worst = Math.max(worst, Math.abs(va - vb) / denom);
        }
      }
    }
    return worst;
  }

  /**
   * Gets the recommended method, computing it on first use from the pairwise agreement of the methods that were run.
   *
   * @return the recommended method
   */
  public AllocationMethod getRecommendedMethod() {
    if (recommendedMethod == null) {
      evaluate();
    }
    return recommendedMethod;
  }

  /**
   * Gets the human-readable rationale for the recommendation, computing it on first use.
   *
   * @return the recommendation rationale
   */
  public String getRecommendationRationale() {
    if (recommendationRationale == null) {
      evaluate();
    }
    return recommendationRationale;
  }

  /**
   * Evaluates the pairwise agreement of the available methods and selects the recommended method and rationale.
   */
  private void evaluate() {
    boolean hasCr = results.containsKey(AllocationMethod.COMPONENT_RATIO);
    boolean hasAi = results.containsKey(AllocationMethod.ALL_IN);
    boolean hasSa = results.containsKey(AllocationMethod.STAND_ALONE);

    if (hasAi && hasSa) {
      double dAiSa = getMaxRelativeDifference(AllocationMethod.ALL_IN, AllocationMethod.STAND_ALONE);
      if (dAiSa > tolerance) {
        recommendedMethod = AllocationMethod.STAND_ALONE;
        recommendationRationale = String
            .format("All-in and stand-alone disagree by %.2f%% (> %.2f%% tolerance): commingling introduces material "
                + "non-linear compositional/thermal coupling, so the most faithful stand-alone re-simulation is "
                + "recommended; consider refreshing the base case.", dAiSa * 100.0, tolerance * 100.0);
        return;
      }
    }

    if (hasCr && hasAi) {
      double dCrAi = getMaxRelativeDifference(AllocationMethod.COMPONENT_RATIO, AllocationMethod.ALL_IN);
      if (dCrAi > tolerance) {
        recommendedMethod = AllocationMethod.ALL_IN;
        recommendationRationale = String.format(
            "Common recovery factor and all-in disagree by %.2f%% (> %.2f%% tolerance): source-dependent routing "
                + "matters but commingling coupling is weak, so the linear all-in proxy is recommended as a cheaper "
                + "alternative to stand-alone re-simulation.",
            dCrAi * 100.0, tolerance * 100.0);
        return;
      }
    }

    if (hasCr) {
      recommendedMethod = AllocationMethod.COMPONENT_RATIO;
      recommendationRationale = String
          .format("All evaluated methods agree within the %.2f%% tolerance, so the cheapest and most auditable common "
              + "recovery factor is sufficient.", tolerance * 100.0);
      return;
    }

    // Fall back to the most faithful method that was actually run.
    if (hasSa) {
      recommendedMethod = AllocationMethod.STAND_ALONE;
    } else if (hasAi) {
      recommendedMethod = AllocationMethod.ALL_IN;
    } else {
      recommendedMethod = results.keySet().iterator().next();
    }
    recommendationRationale = "Recommending the highest-fidelity method that was run.";
  }

  /**
   * Computes the owner (source) benefit table: for every source and product, the per-method allocation and the spread
   * (maximum minus minimum across methods). A large spread for a source means the choice of method materially changes
   * that owner's booked production.
   *
   * @param unit the flow unit for the table (for example {@code kg/hr}, {@code kg/day}, {@code tonnes/year})
   * @return owner-sensitivity rows, one per source-product combination with a non-trivial flow
   */
  public List<OwnerSensitivity> getOwnerSensitivity(String unit) {
    List<OwnerSensitivity> rows = new ArrayList<>();
    if (results.isEmpty()) {
      return rows;
    }
    ProductionAllocationResult any = results.values().iterator().next();
    for (String source : any.getSourceNames()) {
      for (ProductType product : COMPARED_PRODUCTS) {
        Map<AllocationMethod, Double> perMethod = new LinkedHashMap<>();
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Map.Entry<AllocationMethod, ProductionAllocationResult> entry : results.entrySet()) {
          double value = entry.getValue().getProductAllocation(source, product, unit);
          perMethod.put(entry.getKey(), value);
          min = Math.min(min, value);
          max = Math.max(max, value);
        }
        if (max > FLOW_NOISE_FLOOR) {
          rows.add(new OwnerSensitivity(source, product, unit, perMethod, max - min));
        }
      }
    }
    return rows;
  }

  /**
   * Serialises the comparison (recommendation, pairwise differences, per-method residuals/runtimes, and owner
   * sensitivity in kg/hr) to a pretty-printed JSON string.
   *
   * @return the JSON representation
   */
  public String toJson() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("tolerance", tolerance);
    root.put("recommendedMethod", getRecommendedMethod().name());
    root.put("recommendationRationale", getRecommendationRationale());

    List<Map<String, Object>> methodList = new ArrayList<>();
    for (Map.Entry<AllocationMethod, ProductionAllocationResult> entry : results.entrySet()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("method", entry.getKey().name());
      m.put("displayName", entry.getKey().getDisplayName());
      m.put("runtimeMillis", getRuntimeMillis(entry.getKey()));
      m.put("maxResidual", entry.getValue().getMaxResidual());
      methodList.add(m);
    }
    root.put("methods", methodList);

    Map<String, Object> diffs = new LinkedHashMap<>();
    diffs.put("componentRatio_vs_allIn",
        getMaxRelativeDifference(AllocationMethod.COMPONENT_RATIO, AllocationMethod.ALL_IN));
    diffs.put("allIn_vs_standAlone", getMaxRelativeDifference(AllocationMethod.ALL_IN, AllocationMethod.STAND_ALONE));
    diffs.put("componentRatio_vs_standAlone",
        getMaxRelativeDifference(AllocationMethod.COMPONENT_RATIO, AllocationMethod.STAND_ALONE));
    root.put("maxRelativeDifferences", diffs);

    List<Map<String, Object>> sensitivity = new ArrayList<>();
    for (OwnerSensitivity row : getOwnerSensitivity("kg/hr")) {
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("source", row.getSource());
      r.put("product", row.getProduct().name());
      r.put("unit", row.getUnit());
      Map<String, Double> byMethod = new LinkedHashMap<>();
      for (Map.Entry<AllocationMethod, Double> e : row.getPerMethod().entrySet()) {
        byMethod.put(e.getKey().name(), e.getValue());
      }
      r.put("byMethod", byMethod);
      r.put("spread", row.getSpread());
      sensitivity.add(r);
    }
    root.put("ownerSensitivity", sensitivity);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(root);
  }

  /**
   * One row of the owner-benefit table: a single source's allocation of a single product under each method, plus the
   * spread across methods.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class OwnerSensitivity {

    /** The source (owner) name. */
    private final String source;

    /** The product category. */
    private final ProductType product;

    /** The flow unit of the values. */
    private final String unit;

    /** The per-method allocated flow. */
    private final Map<AllocationMethod, Double> perMethod;

    /** The spread (max minus min) across methods, in {@link #unit}. */
    private final double spread;

    /**
     * Creates an owner-sensitivity row.
     *
     * @param source the source name; must be non-null
     * @param product the product category; must be non-null
     * @param unit the flow unit; must be non-null
     * @param perMethod the per-method allocated flow; must be non-null
     * @param spread the spread across methods in {@code unit}
     */
    OwnerSensitivity(String source, ProductType product, String unit, Map<AllocationMethod, Double> perMethod,
        double spread) {
      this.source = source;
      this.product = product;
      this.unit = unit;
      this.perMethod = new LinkedHashMap<>(perMethod);
      this.spread = spread;
    }

    /**
     * Gets the source (owner) name.
     *
     * @return the source name
     */
    public String getSource() {
      return source;
    }

    /**
     * Gets the product category.
     *
     * @return the product type
     */
    public ProductType getProduct() {
      return product;
    }

    /**
     * Gets the flow unit of the values.
     *
     * @return the unit
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the per-method allocated flow.
     *
     * @return an unmodifiable map of method to allocated flow
     */
    public Map<AllocationMethod, Double> getPerMethod() {
      return perMethod;
    }

    /**
     * Gets the spread (maximum minus minimum allocated flow) across methods.
     *
     * @return the spread in {@link #getUnit()}
     */
    public double getSpread() {
      return spread;
    }
  }
}
