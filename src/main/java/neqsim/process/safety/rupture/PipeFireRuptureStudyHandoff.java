package neqsim.process.safety.rupture;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.rupture.PipeFireRuptureUncertaintyRunner.UncertaintySummary;

/**
 * Versioned handoff package for pipe fire-rupture studies.
 *
 * <p>
 * The handoff keeps the data source, calculation readiness, standards readiness, solver result, uncertainty summary,
 * and consequence source-term package together so downstream agents do not lose provenance while generating reports or
 * continuing into consequence analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeFireRuptureStudyHandoff implements Serializable {
  private static final long serialVersionUID = 1L;

  private final PipeFireRuptureDataSource dataSource;
  private final SafetyStudyReadiness calculationReadiness;
  private final SafetyStudyReadiness standardsReadiness;
  private final PipeFireRuptureResult result;
  private final UncertaintySummary uncertaintySummary;
  private final Map<String, Object> sourceTermHandoff;

  /**
   * Creates a handoff package.
   *
   * @param builder populated builder
   */
  private PipeFireRuptureStudyHandoff(Builder builder) {
    this.dataSource = builder.dataSource;
    this.calculationReadiness = builder.calculationReadiness;
    this.standardsReadiness = builder.standardsReadiness;
    this.result = builder.result;
    this.uncertaintySummary = builder.uncertaintySummary;
    this.sourceTermHandoff = builder.sourceTermHandoff == null ? null
        : new LinkedHashMap<String, Object>(builder.sourceTermHandoff);
  }

  /**
   * Creates a handoff builder.
   *
   * @param dataSource study data source
   * @return handoff builder
   */
  public static Builder builder(PipeFireRuptureDataSource dataSource) {
    return new Builder(dataSource);
  }

  /**
   * Gets the calculation result.
   *
   * @return calculation result or null
   */
  public PipeFireRuptureResult getResult() {
    return result;
  }

  /**
   * Gets calculation readiness.
   *
   * @return readiness result
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
   * Gets uncertainty summary.
   *
   * @return uncertainty summary or null
   */
  public UncertaintySummary getUncertaintySummary() {
    return uncertaintySummary;
  }

  /**
   * Gets source-term handoff.
   *
   * @return source-term map or null
   */
  public Map<String, Object> getSourceTermHandoff() {
    return sourceTermHandoff == null ? null : new LinkedHashMap<String, Object>(sourceTermHandoff);
  }

  /**
   * Converts handoff to a JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "pipe_fire_rupture_study_handoff.v1");
    map.put("dataSource", dataSource == null ? null : dataSource.toMap());
    map.put("calculationReadiness", calculationReadiness == null ? null : calculationReadiness.toMap());
    map.put("standardsReadiness", standardsReadiness == null ? null : standardsReadiness.toMap());
    map.put("result", result == null ? null : result.toMap());
    map.put("uncertainty", uncertaintySummary == null ? null : uncertaintySummary.toMap());
    map.put("sourceTermHandoff", sourceTermHandoff);
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

  /** Builder for {@link PipeFireRuptureStudyHandoff}. */
  public static final class Builder {
    private final PipeFireRuptureDataSource dataSource;
    private SafetyStudyReadiness calculationReadiness;
    private SafetyStudyReadiness standardsReadiness;
    private PipeFireRuptureResult result;
    private UncertaintySummary uncertaintySummary;
    private Map<String, Object> sourceTermHandoff;

    /**
     * Creates a builder.
     *
     * @param dataSource study data source
     */
    private Builder(PipeFireRuptureDataSource dataSource) {
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
     * Sets calculation result.
     *
     * @param result calculation result
     * @return this builder
     */
    public Builder result(PipeFireRuptureResult result) {
      this.result = result;
      return this;
    }

    /**
     * Sets uncertainty summary.
     *
     * @param uncertaintySummary uncertainty summary
     * @return this builder
     */
    public Builder uncertaintySummary(UncertaintySummary uncertaintySummary) {
      this.uncertaintySummary = uncertaintySummary;
      return this;
    }

    /**
     * Sets source-term handoff.
     *
     * @param sourceTermHandoff source-term handoff map
     * @return this builder
     */
    public Builder sourceTermHandoff(Map<String, Object> sourceTermHandoff) {
      this.sourceTermHandoff = sourceTermHandoff;
      return this;
    }

    /**
     * Builds the handoff.
     *
     * @return handoff package
     */
    public PipeFireRuptureStudyHandoff build() {
      return new PipeFireRuptureStudyHandoff(this);
    }
  }
}
