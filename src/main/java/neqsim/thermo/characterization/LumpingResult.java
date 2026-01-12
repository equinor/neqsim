package neqsim.thermo.characterization;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Result class containing lumping quality metrics and configuration details.
 *
 * <p>
 * This class provides feedback on the lumping operation, including:
 * </p>
 * <ul>
 * <li>Number of components before and after lumping</li>
 * <li>Property preservation errors (MW, density)</li>
 * <li>Configuration details applied</li>
 * <li>Any warnings generated during lumping</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class LumpingResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String modelName;
  private final int originalComponentCount;
  private final int lumpedComponentCount;
  private final double originalAverageMW;
  private final double lumpedAverageMW;
  private final double originalAverageDensity;
  private final double lumpedAverageDensity;
  private final java.util.List<String> warnings;
  private final java.util.List<String> lumpedComponentNames;
  private final int[] carbonNumberBoundaries;

  /**
   * Constructor for LumpingResult.
   *
   * @param builder the builder containing all result data
   */
  private LumpingResult(Builder builder) {
    this.modelName = builder.modelName;
    this.originalComponentCount = builder.originalComponentCount;
    this.lumpedComponentCount = builder.lumpedComponentCount;
    this.originalAverageMW = builder.originalAverageMW;
    this.lumpedAverageMW = builder.lumpedAverageMW;
    this.originalAverageDensity = builder.originalAverageDensity;
    this.lumpedAverageDensity = builder.lumpedAverageDensity;
    this.warnings = builder.warnings;
    this.lumpedComponentNames = builder.lumpedComponentNames;
    this.carbonNumberBoundaries = builder.carbonNumberBoundaries;
  }

  /**
   * Get the lumping model name used.
   *
   * @return the model name
   */
  public String getModelName() {
    return modelName;
  }

  /**
   * Get the number of heavy components before lumping.
   *
   * @return original component count
   */
  public int getOriginalComponentCount() {
    return originalComponentCount;
  }

  /**
   * Get the number of pseudo-components after lumping.
   *
   * @return lumped component count
   */
  public int getLumpedComponentCount() {
    return lumpedComponentCount;
  }

  /**
   * Get the relative error in average molecular weight after lumping.
   *
   * <p>
   * Error is calculated as: |lumpedMW - originalMW| / originalMW
   * </p>
   *
   * @return relative MW error (0.0 = perfect match, 0.01 = 1% error)
   */
  public double getMWError() {
    if (originalAverageMW == 0.0) {
      return 0.0;
    }
    return Math.abs(lumpedAverageMW - originalAverageMW) / originalAverageMW;
  }

  /**
   * Get the relative error in average density after lumping.
   *
   * <p>
   * Error is calculated as: |lumpedDensity - originalDensity| / originalDensity
   * </p>
   *
   * @return relative density error (0.0 = perfect match, 0.01 = 1% error)
   */
  public double getDensityError() {
    if (originalAverageDensity == 0.0) {
      return 0.0;
    }
    return Math.abs(lumpedAverageDensity - originalAverageDensity) / originalAverageDensity;
  }

  /**
   * Get the original mass-weighted average molecular weight.
   *
   * @return original average MW in kg/mol
   */
  public double getOriginalAverageMW() {
    return originalAverageMW;
  }

  /**
   * Get the lumped mass-weighted average molecular weight.
   *
   * @return lumped average MW in kg/mol
   */
  public double getLumpedAverageMW() {
    return lumpedAverageMW;
  }

  /**
   * Get the original mass-weighted average density.
   *
   * @return original average density in kg/m3
   */
  public double getOriginalAverageDensity() {
    return originalAverageDensity;
  }

  /**
   * Get the lumped mass-weighted average density.
   *
   * @return lumped average density in kg/m3
   */
  public double getLumpedAverageDensity() {
    return lumpedAverageDensity;
  }

  /**
   * Get any warnings generated during lumping.
   *
   * @return list of warning messages
   */
  public java.util.List<String> getWarnings() {
    return java.util.Collections.unmodifiableList(warnings);
  }

  /**
   * Check if lumping generated any warnings.
   *
   * @return true if there are warnings
   */
  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  /**
   * Get the names of the lumped components created.
   *
   * @return list of component names
   */
  public java.util.List<String> getLumpedComponentNames() {
    return java.util.Collections.unmodifiableList(lumpedComponentNames);
  }

  /**
   * Get the carbon number boundaries used for lumping.
   *
   * @return array of carbon number boundaries, or null if auto-calculated
   */
  public int[] getCarbonNumberBoundaries() {
    return carbonNumberBoundaries != null ? carbonNumberBoundaries.clone() : null;
  }

  /**
   * Get a summary map of all metrics for easy inspection.
   *
   * @return map of metric names to values
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("modelName", modelName);
    map.put("originalComponentCount", originalComponentCount);
    map.put("lumpedComponentCount", lumpedComponentCount);
    map.put("originalAverageMW", originalAverageMW);
    map.put("lumpedAverageMW", lumpedAverageMW);
    map.put("mwError", getMWError());
    map.put("originalAverageDensity", originalAverageDensity);
    map.put("lumpedAverageDensity", lumpedAverageDensity);
    map.put("densityError", getDensityError());
    map.put("warnings", warnings);
    map.put("lumpedComponentNames", lumpedComponentNames);
    return map;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("LumpingResult {\n");
    sb.append("  model: ").append(modelName).append("\n");
    sb.append("  components: ").append(originalComponentCount).append(" -> ")
        .append(lumpedComponentCount).append("\n");
    sb.append("  MW error: ").append(String.format("%.4f%%", getMWError() * 100)).append("\n");
    sb.append("  Density error: ").append(String.format("%.4f%%", getDensityError() * 100))
        .append("\n");
    if (!warnings.isEmpty()) {
      sb.append("  warnings: ").append(warnings).append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Builder for LumpingResult.
   */
  public static class Builder {
    private String modelName = "";
    private int originalComponentCount = 0;
    private int lumpedComponentCount = 0;
    private double originalAverageMW = 0.0;
    private double lumpedAverageMW = 0.0;
    private double originalAverageDensity = 0.0;
    private double lumpedAverageDensity = 0.0;
    private java.util.List<String> warnings = new java.util.ArrayList<String>();
    private java.util.List<String> lumpedComponentNames = new java.util.ArrayList<String>();
    private int[] carbonNumberBoundaries = null;

    /**
     * Set the model name.
     *
     * @param modelName the lumping model name
     * @return this builder
     */
    public Builder modelName(String modelName) {
      this.modelName = modelName;
      return this;
    }

    /**
     * Set the original component count.
     *
     * @param count number of components before lumping
     * @return this builder
     */
    public Builder originalComponentCount(int count) {
      this.originalComponentCount = count;
      return this;
    }

    /**
     * Set the lumped component count.
     *
     * @param count number of components after lumping
     * @return this builder
     */
    public Builder lumpedComponentCount(int count) {
      this.lumpedComponentCount = count;
      return this;
    }

    /**
     * Set the original average MW.
     *
     * @param mw original mass-weighted average MW
     * @return this builder
     */
    public Builder originalAverageMW(double mw) {
      this.originalAverageMW = mw;
      return this;
    }

    /**
     * Set the lumped average MW.
     *
     * @param mw lumped mass-weighted average MW
     * @return this builder
     */
    public Builder lumpedAverageMW(double mw) {
      this.lumpedAverageMW = mw;
      return this;
    }

    /**
     * Set the original average density.
     *
     * @param density original mass-weighted average density
     * @return this builder
     */
    public Builder originalAverageDensity(double density) {
      this.originalAverageDensity = density;
      return this;
    }

    /**
     * Set the lumped average density.
     *
     * @param density lumped mass-weighted average density
     * @return this builder
     */
    public Builder lumpedAverageDensity(double density) {
      this.lumpedAverageDensity = density;
      return this;
    }

    /**
     * Add a warning message.
     *
     * @param warning warning message
     * @return this builder
     */
    public Builder addWarning(String warning) {
      this.warnings.add(warning);
      return this;
    }

    /**
     * Set the lumped component names.
     *
     * @param names list of component names
     * @return this builder
     */
    public Builder lumpedComponentNames(java.util.List<String> names) {
      this.lumpedComponentNames = new java.util.ArrayList<String>(names);
      return this;
    }

    /**
     * Set the carbon number boundaries.
     *
     * @param boundaries array of carbon number boundaries
     * @return this builder
     */
    public Builder carbonNumberBoundaries(int[] boundaries) {
      this.carbonNumberBoundaries = boundaries != null ? boundaries.clone() : null;
      return this;
    }

    /**
     * Build the LumpingResult.
     *
     * @return the constructed LumpingResult
     */
    public LumpingResult build() {
      return new LumpingResult(this);
    }
  }
}
