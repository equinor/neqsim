package neqsim.process.safety.depressurization;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.rupture.SafetyStudyReadiness;

/**
 * Versioned handoff package for governed dynamic blowdown and flare-load studies.
 *
 * <p>
 * The handoff keeps the data source, calculation readiness, standards readiness, transient result, and compact flare
 * load/source-term handoff together so downstream report, consequence, or flare-network agents do not lose provenance.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class DynamicBlowdownFlareStudyHandoff implements Serializable {
  private static final long serialVersionUID = 1L;

  private final DynamicBlowdownFlareStudyDataSource dataSource;
  private final SafetyStudyReadiness calculationReadiness;
  private final SafetyStudyReadiness standardsReadiness;
  private final Map<String, Object> result;
  private final Map<String, Object> flareLoadHandoff;

  /**
   * Creates a handoff package.
   *
   * @param builder populated builder
   */
  private DynamicBlowdownFlareStudyHandoff(Builder builder) {
    this.dataSource = builder.dataSource;
    this.calculationReadiness = builder.calculationReadiness;
    this.standardsReadiness = builder.standardsReadiness;
    this.result = copyOrNull(builder.result);
    this.flareLoadHandoff = copyOrNull(builder.flareLoadHandoff);
  }

  /**
   * Creates a builder.
   *
   * @param dataSource study data source
   * @return builder
   */
  public static Builder builder(DynamicBlowdownFlareStudyDataSource dataSource) {
    return new Builder(dataSource);
  }

  /**
   * Gets calculation readiness.
   *
   * @return calculation readiness
   */
  public SafetyStudyReadiness getCalculationReadiness() {
    return calculationReadiness;
  }

  /**
   * Gets standards readiness.
   *
   * @return standards readiness
   */
  public SafetyStudyReadiness getStandardsReadiness() {
    return standardsReadiness;
  }

  /**
   * Gets transient result map.
   *
   * @return result map or null
   */
  public Map<String, Object> getResult() {
    return copyOrNull(result);
  }

  /**
   * Gets flare-load handoff map.
   *
   * @return flare-load handoff or null
   */
  public Map<String, Object> getFlareLoadHandoff() {
    return copyOrNull(flareLoadHandoff);
  }

  /**
   * Converts handoff to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "dynamic_blowdown_flare_study_handoff.v1");
    map.put("dataSource", dataSource == null ? null : dataSource.toMap());
    map.put("calculationReadiness", calculationReadiness == null ? null : calculationReadiness.toMap());
    map.put("standardsReadiness", standardsReadiness == null ? null : standardsReadiness.toMap());
    map.put("result", result);
    map.put("flareLoadHandoff", flareLoadHandoff);
    return map;
  }

  /**
   * Converts handoff to JSON.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Copies map values.
   *
   * @param source source map
   * @return copied map or null
   */
  private static Map<String, Object> copyOrNull(Map<String, Object> source) {
    return source == null ? null : new LinkedHashMap<String, Object>(source);
  }

  /** Builder for {@link DynamicBlowdownFlareStudyHandoff}. */
  public static final class Builder {
    private final DynamicBlowdownFlareStudyDataSource dataSource;
    private SafetyStudyReadiness calculationReadiness;
    private SafetyStudyReadiness standardsReadiness;
    private Map<String, Object> result;
    private Map<String, Object> flareLoadHandoff;

    /**
     * Creates a builder.
     *
     * @param dataSource study data source
     */
    private Builder(DynamicBlowdownFlareStudyDataSource dataSource) {
      this.dataSource = dataSource;
    }

    /**
     * Sets calculation readiness.
     *
     * @param calculationReadiness readiness result
     * @return this builder
     */
    public Builder calculationReadiness(SafetyStudyReadiness calculationReadiness) {
      this.calculationReadiness = calculationReadiness;
      return this;
    }

    /**
     * Sets standards readiness.
     *
     * @param standardsReadiness standards readiness
     * @return this builder
     */
    public Builder standardsReadiness(SafetyStudyReadiness standardsReadiness) {
      this.standardsReadiness = standardsReadiness;
      return this;
    }

    /**
     * Sets result map.
     *
     * @param result result map
     * @return this builder
     */
    public Builder result(Map<String, Object> result) {
      this.result = result;
      return this;
    }

    /**
     * Sets flare-load handoff.
     *
     * @param flareLoadHandoff flare-load handoff map
     * @return this builder
     */
    public Builder flareLoadHandoff(Map<String, Object> flareLoadHandoff) {
      this.flareLoadHandoff = flareLoadHandoff;
      return this;
    }

    /**
     * Builds the handoff.
     *
     * @return handoff package
     */
    public DynamicBlowdownFlareStudyHandoff build() {
      return new DynamicBlowdownFlareStudyHandoff(this);
    }
  }
}