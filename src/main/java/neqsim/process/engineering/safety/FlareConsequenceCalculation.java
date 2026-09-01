package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EngineeringConstraintResult;

/**
 * Versioned flare radiation, dispersion and noise screening calculation.
 *
 * <p>
 * Radiation uses an explicit point-source fraction, atmospheric transmissivity and geometry factor. Dispersion uses a
 * neutral continuous Gaussian centerline screen with project-supplied lateral and vertical spread coefficients. Noise
 * uses spherical spreading with project-supplied atmospheric absorption. These correlations are screening interfaces;
 * production qualification requires applicability evidence or a project-qualified consequence model.
 * </p>
 */
public final class FlareConsequenceCalculation
    implements EngineeringCalculationModule<FlareConsequenceCalculation.Input, FlareConsequenceCalculation.Result> {

  /** Controlled flare load, receptor and method input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String studyId;
    private final double heatReleaseMw;
    private final double radiativeFraction;
    private final double atmosphericTransmissivity;
    private final double geometryFactor;
    private final double receptorDistanceM;
    private final double maximumRadiationKwM2;
    private final double releaseMassRateKgS;
    private final double windSpeedMPerS;
    private final double lateralSpreadCoefficient;
    private final double verticalSpreadCoefficient;
    private final double targetConcentrationKgM3;
    private final double maximumDispersionDistanceM;
    private final double sourceSoundPowerDbA;
    private final double atmosphericAbsorptionDbPerM;
    private final double maximumNoiseDbA;
    private final double flareTipVelocityMPerS;
    private final double speedOfSoundMPerS;
    private final double maximumTipMach;

    private Input(Builder builder) {
      studyId = text(builder.studyId, "studyId");
      heatReleaseMw = positive(builder.heatReleaseMw, "heatReleaseMw");
      radiativeFraction = fraction(builder.radiativeFraction, "radiativeFraction");
      atmosphericTransmissivity = fraction(builder.atmosphericTransmissivity, "atmosphericTransmissivity");
      geometryFactor = fraction(builder.geometryFactor, "geometryFactor");
      receptorDistanceM = positive(builder.receptorDistanceM, "receptorDistanceM");
      maximumRadiationKwM2 = positive(builder.maximumRadiationKwM2, "maximumRadiationKwM2");
      releaseMassRateKgS = positive(builder.releaseMassRateKgS, "releaseMassRateKgS");
      windSpeedMPerS = positive(builder.windSpeedMPerS, "windSpeedMPerS");
      lateralSpreadCoefficient = positive(builder.lateralSpreadCoefficient, "lateralSpreadCoefficient");
      verticalSpreadCoefficient = positive(builder.verticalSpreadCoefficient, "verticalSpreadCoefficient");
      targetConcentrationKgM3 = positive(builder.targetConcentrationKgM3, "targetConcentrationKgM3");
      maximumDispersionDistanceM = positive(builder.maximumDispersionDistanceM, "maximumDispersionDistanceM");
      sourceSoundPowerDbA = finite(builder.sourceSoundPowerDbA, "sourceSoundPowerDbA");
      atmosphericAbsorptionDbPerM = nonNegative(builder.atmosphericAbsorptionDbPerM, "atmosphericAbsorptionDbPerM");
      maximumNoiseDbA = finite(builder.maximumNoiseDbA, "maximumNoiseDbA");
      flareTipVelocityMPerS = nonNegative(builder.flareTipVelocityMPerS, "flareTipVelocityMPerS");
      speedOfSoundMPerS = positive(builder.speedOfSoundMPerS, "speedOfSoundMPerS");
      maximumTipMach = positive(builder.maximumTipMach, "maximumTipMach");
    }

    public static Builder builder(String studyId) {
      return new Builder(studyId);
    }

    /** Builder for flare consequence screening input. */
    public static final class Builder {
      private final String studyId;
      private double heatReleaseMw;
      private double radiativeFraction;
      private double atmosphericTransmissivity;
      private double geometryFactor;
      private double receptorDistanceM;
      private double maximumRadiationKwM2;
      private double releaseMassRateKgS;
      private double windSpeedMPerS;
      private double lateralSpreadCoefficient;
      private double verticalSpreadCoefficient;
      private double targetConcentrationKgM3;
      private double maximumDispersionDistanceM;
      private double sourceSoundPowerDbA;
      private double atmosphericAbsorptionDbPerM;
      private double maximumNoiseDbA;
      private double flareTipVelocityMPerS;
      private double speedOfSoundMPerS;
      private double maximumTipMach;

      private Builder(String studyId) {
        this.studyId = studyId;
      }

      public Builder radiation(double heatReleaseMw, double radiativeFraction, double transmissivity,
          double geometryFactor, double receptorDistanceM, double maximumRadiationKwM2) {
        this.heatReleaseMw = heatReleaseMw;
        this.radiativeFraction = radiativeFraction;
        atmosphericTransmissivity = transmissivity;
        this.geometryFactor = geometryFactor;
        this.receptorDistanceM = receptorDistanceM;
        this.maximumRadiationKwM2 = maximumRadiationKwM2;
        return this;
      }

      public Builder dispersion(double releaseMassRateKgS, double windSpeedMPerS, double lateralSpreadCoefficient,
          double verticalSpreadCoefficient, double targetConcentrationKgM3, double maximumDispersionDistanceM) {
        this.releaseMassRateKgS = releaseMassRateKgS;
        this.windSpeedMPerS = windSpeedMPerS;
        this.lateralSpreadCoefficient = lateralSpreadCoefficient;
        this.verticalSpreadCoefficient = verticalSpreadCoefficient;
        this.targetConcentrationKgM3 = targetConcentrationKgM3;
        this.maximumDispersionDistanceM = maximumDispersionDistanceM;
        return this;
      }

      public Builder noise(double sourceSoundPowerDbA, double atmosphericAbsorptionDbPerM, double maximumNoiseDbA) {
        this.sourceSoundPowerDbA = sourceSoundPowerDbA;
        this.atmosphericAbsorptionDbPerM = atmosphericAbsorptionDbPerM;
        this.maximumNoiseDbA = maximumNoiseDbA;
        return this;
      }

      public Builder flareTip(double velocityMPerS, double speedOfSoundMPerS, double maximumMach) {
        flareTipVelocityMPerS = velocityMPerS;
        this.speedOfSoundMPerS = speedOfSoundMPerS;
        maximumTipMach = maximumMach;
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable flare consequence metrics and constraints. */
  public static final class Result implements EngineeringConstraintResult, Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Double> metrics;
    private final Map<String, Boolean> constraints;

    private Result(Map<String, Double> metrics, Map<String, Boolean> constraints) {
      this.metrics = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(metrics));
      this.constraints = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(constraints));
    }

    @Override
    public boolean allConstraintsSatisfied() {
      for (Boolean value : constraints.values()) {
        if (!value.booleanValue()) {
          return false;
        }
      }
      return !constraints.isEmpty();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("metrics", new LinkedHashMap<String, Double>(metrics));
      result.put("constraints", new LinkedHashMap<String, Boolean>(constraints));
      result.put("allConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      result.put("radiationMethod", "POINT_SOURCE_SCREEN");
      result.put("dispersionMethod", "NEUTRAL_GAUSSIAN_CENTERLINE_SCREEN");
      result.put("noiseMethod", "SPHERICAL_SPREADING_SCREEN");
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "flare-radiation-dispersion-noise-screening";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("FLARE_CONSEQUENCE_INPUT", "Flare consequence input is required",
          "Supply flare load, receptor, atmospheric and acceptance data").build();
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      readiness.addBlocker("FLARE_CONSEQUENCE_PRODUCTION_EVIDENCE",
          "Production qualification requires consequence-method and project standards evidence",
          "Attach the controlled flare, radiation, dispersion and noise calculation basis");
    }
    if (productionQualification(context)
        && !"approved".equalsIgnoreCase(context.getAttributes().get("consequenceMethodApplicability"))) {
      readiness.addBlocker("FLARE_METHOD_APPLICABILITY",
          "The consequence method has not been approved for the release and geometry",
          "Approve the project consequence method or attach qualified external-model results");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> output = EngineeringCalculationResult
        .<Result>builder("flare-consequence:" + (input == null ? "unassigned" : input.studyId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    if (input == null) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }

    double heatReleaseW = input.heatReleaseMw * 1.0e6;
    double radiationKwM2 = input.radiativeFraction * input.atmosphericTransmissivity * input.geometryFactor
        * heatReleaseW / (4.0 * Math.PI * input.receptorDistanceM * input.receptorDistanceM) / 1000.0;
    double dispersionDistanceM = Math.sqrt(input.releaseMassRateKgS / (2.0 * Math.PI * input.windSpeedMPerS
        * input.lateralSpreadCoefficient * input.verticalSpreadCoefficient * input.targetConcentrationKgM3));
    double receptorNoiseDbA = input.sourceSoundPowerDbA - 20.0 * Math.log10(input.receptorDistanceM) - 11.0
        - input.atmosphericAbsorptionDbPerM * input.receptorDistanceM;
    double tipMach = input.flareTipVelocityMPerS / input.speedOfSoundMPerS;

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("radiationAtReceptorKwM2", Double.valueOf(radiationKwM2));
    metrics.put("dispersionTargetDistanceM", Double.valueOf(dispersionDistanceM));
    metrics.put("noiseAtReceptorDbA", Double.valueOf(receptorNoiseDbA));
    metrics.put("flareTipMach", Double.valueOf(tipMach));

    Map<String, Boolean> constraints = new LinkedHashMap<String, Boolean>();
    constraints.put("THERMAL_RADIATION", Boolean.valueOf(radiationKwM2 <= input.maximumRadiationKwM2));
    constraints.put("DISPERSION_DISTANCE", Boolean.valueOf(dispersionDistanceM <= input.maximumDispersionDistanceM));
    constraints.put("RECEPTOR_NOISE", Boolean.valueOf(receptorNoiseDbA <= input.maximumNoiseDbA));
    constraints.put("FLARE_TIP_MACH", Boolean.valueOf(tipMach <= input.maximumTipMach));

    return output.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).input("studyId", input.studyId)
        .input("receptorDistanceM", Double.valueOf(input.receptorDistanceM)).value(new Result(metrics, constraints))
        .uncertainty(new EngineeringCalculationResult.Uncertainty(0.7 * radiationKwM2, radiationKwM2,
            1.5 * radiationKwM2, "kW/m2", "Screening correlation range; replace with project uncertainty model"))
        .warning("Complex geometry, crosswind, combustion efficiency and weather classes require qualified modeling")
        .build();
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(field + " must be finite and positive");
    }
    return value;
  }

  private static double nonNegative(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(field + " must be finite and non-negative");
    }
    return value;
  }

  private static double fraction(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(field + " must be between zero and one");
    }
    return value;
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }
}
