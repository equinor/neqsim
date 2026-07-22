package neqsim.process.safety.rupture;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.safety.rupture.PipeFireRuptureUncertaintyRunner.UncertaintySummary;

/**
 * Orchestrates governed pipe fire-rupture studies from a data source package.
 *
 * <p>
 * The runner performs readiness checks, runs the existing pipe fire-rupture solver when inputs are present, applies the
 * standards validator, optionally executes deterministic uncertainty cases, and returns one versioned handoff package.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class PipeFireRuptureStudyRunner implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double DEFAULT_MAX_TIME_SECONDS = 3600.0;
  private static final double DEFAULT_TIME_STEP_SECONDS = 5.0;

  private final double timeStepSeconds;
  private final double maxTimeSeconds;
  private final boolean spreadsheetGasThermalMass;
  private final boolean runUncertainty;
  private final PipeFireRuptureStandardsValidator standardsValidator;

  /**
   * Creates a runner.
   *
   * @param builder populated builder
   */
  private PipeFireRuptureStudyRunner(Builder builder) {
    builder.validate();
    this.timeStepSeconds = builder.timeStepSeconds;
    this.maxTimeSeconds = builder.maxTimeSeconds;
    this.spreadsheetGasThermalMass = builder.spreadsheetGasThermalMass;
    this.runUncertainty = builder.runUncertainty;
    this.standardsValidator = builder.standardsValidator;
  }

  /**
   * Creates a runner builder.
   *
   * @return runner builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Runs a study and returns a governed handoff package.
   *
   * @param dataSource study data source
   * @return study handoff
   */
  public PipeFireRuptureStudyHandoff run(PipeFireRuptureDataSource dataSource) {
    PipeFireRuptureDataSource source = dataSource == null
        ? PipeFireRuptureDataSource.builder("missing-data-source").addGap("PipeFireRuptureDataSource was null.").build()
        : dataSource;
    SafetyStudyReadiness calculationReadiness = source.readiness();
    PipeFireRuptureResult result = null;
    UncertaintySummary uncertaintySummary = null;
    Map<String, Object> sourceTerm = null;
    if (calculationReadiness.isReadyForCalculation()) {
      result = PipeFireRuptureStudy
          .builder(source.getInput(), source.getMaterial(), source.getScenario(), source.getPressureProfile())
          .timeStepSeconds(timeStepSeconds).maxTimeSeconds(maxTimeSeconds)
          .spreadsheetGasThermalMass(spreadsheetGasThermalMass).build().run();
      sourceTerm = sourceTermHandoff(source, result);
      if (runUncertainty) {
        uncertaintySummary = new PipeFireRuptureUncertaintyRunner(timeStepSeconds, maxTimeSeconds,
            spreadsheetGasThermalMass).run(source);
      }
    }
    SafetyStudyReadiness standardsReadiness = standardsValidator.validate(source, result);
    return PipeFireRuptureStudyHandoff.builder(source).calculationReadiness(calculationReadiness)
        .standardsReadiness(standardsReadiness).result(result).uncertaintySummary(uncertaintySummary)
        .sourceTermHandoff(sourceTerm).build();
  }

  /**
   * Builds a compact source-term handoff from a rupture result.
   *
   * @param dataSource study data source
   * @param result pipe fire-rupture result
   * @return source-term handoff map
   */
  private static Map<String, Object> sourceTermHandoff(PipeFireRuptureDataSource dataSource,
      PipeFireRuptureResult result) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", "pipe_fire_rupture_source_term_handoff.v1");
    map.put("studyId", dataSource.getStudyId());
    map.put("segmentId", dataSource.getInput().getSegmentId());
    map.put("rupturePredicted", Boolean.valueOf(result.isRupturePredicted()));
    map.put("ruptureTimeSeconds", finiteOrNull(result.getRuptureTimeSeconds()));
    map.put("rupturePressureBarg", finiteOrNull(result.getRupturePressureBarg()));
    map.put("ruptureMeanWallTemperatureC", finiteOrNull(result.getRuptureMeanWallTemperatureC()));
    map.put("releaseEstimate", result.getReleaseEstimate() == null ? null : result.getReleaseEstimate().toMap());
    map.put("basis", "Screening source-term handoff from pipe fire-rupture strain-rate result.");
    map.put("humanReviewRequired", Boolean.TRUE);
    return map;
  }

  /**
   * Converts finite values to boxed values and non-finite values to null.
   *
   * @param value numeric value
   * @return boxed value or null
   */
  private static Object finiteOrNull(double value) {
    return Double.isFinite(value) ? Double.valueOf(value) : null;
  }

  /** Builder for {@link PipeFireRuptureStudyRunner}. */
  public static final class Builder {
    private double timeStepSeconds = DEFAULT_TIME_STEP_SECONDS;
    private double maxTimeSeconds = DEFAULT_MAX_TIME_SECONDS;
    private boolean spreadsheetGasThermalMass = true;
    private boolean runUncertainty = true;
    private PipeFireRuptureStandardsValidator standardsValidator = new PipeFireRuptureStandardsValidator();

    /** Creates a builder. */
    private Builder() {
    }

    /**
     * Sets calculation time step.
     *
     * @param timeStepSeconds time step in seconds
     * @return this builder
     */
    public Builder timeStepSeconds(double timeStepSeconds) {
      this.timeStepSeconds = timeStepSeconds;
      return this;
    }

    /**
     * Sets maximum simulation time.
     *
     * @param maxTimeSeconds maximum time in seconds
     * @return this builder
     */
    public Builder maxTimeSeconds(double maxTimeSeconds) {
      this.maxTimeSeconds = maxTimeSeconds;
      return this;
    }

    /**
     * Sets gas thermal-mass mode.
     *
     * @param spreadsheetGasThermalMass true to use spreadsheet gas thermal mass
     * @return this builder
     */
    public Builder spreadsheetGasThermalMass(boolean spreadsheetGasThermalMass) {
      this.spreadsheetGasThermalMass = spreadsheetGasThermalMass;
      return this;
    }

    /**
     * Enables or disables default uncertainty cases.
     *
     * @param runUncertainty true to run uncertainty cases
     * @return this builder
     */
    public Builder runUncertainty(boolean runUncertainty) {
      this.runUncertainty = runUncertainty;
      return this;
    }

    /**
     * Sets standards validator.
     *
     * @param standardsValidator standards validator
     * @return this builder
     */
    public Builder standardsValidator(PipeFireRuptureStandardsValidator standardsValidator) {
      if (standardsValidator != null) {
        this.standardsValidator = standardsValidator;
      }
      return this;
    }

    /**
     * Builds a runner.
     *
     * @return study runner
     */
    public PipeFireRuptureStudyRunner build() {
      return new PipeFireRuptureStudyRunner(this);
    }

    /**
     * Validates builder state.
     *
     * @throws IllegalArgumentException if time settings are invalid
     */
    private void validate() {
      validatePositive(timeStepSeconds, "timeStepSeconds");
      validatePositive(maxTimeSeconds, "maxTimeSeconds");
      if (maxTimeSeconds < timeStepSeconds) {
        throw new IllegalArgumentException("maxTimeSeconds must be at least timeStepSeconds");
      }
    }

    /**
     * Validates a positive value.
     *
     * @param value value to validate
     * @param name parameter name
     * @throws IllegalArgumentException if value is invalid
     */
    private static void validatePositive(double value, String name) {
      if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
        throw new IllegalArgumentException(name + " must be positive and finite");
      }
    }
  }
}
