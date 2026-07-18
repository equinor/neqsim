package neqsim.process.engineering.piping;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EngineeringConstraintResult;

/**
 * Qualifies a distributed piping-transient profile for engineering use.
 *
 * <p>
 * The hydraulic transient remains the responsibility of the declared source model, for example
 * {@code TwoFluidPipe}, {@code WaterHammerPipe}, or a project-qualified external solver. This calculation verifies
 * that the exported profile resolves the acoustic time scale, closes the line-pack balance, and satisfies explicit
 * pressure, slug, acoustic, vibration and stress constraints. It does not turn a quasi-steady line calculation into a
 * distributed transient model.
 * </p>
 */
public final class TransientPipingQualificationCalculation implements
    EngineeringCalculationModule<TransientPipingQualificationCalculation.Input, TransientPipingQualificationCalculation.Result> {

  /** One ordered transient sample exported by the source hydraulic model. */
  public static final class Sample implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double timeSeconds;
    private final double inletMassFlowKgS;
    private final double outletMassFlowKgS;
    private final double linePackMassKg;
    private final double minimumPressureBara;
    private final double maximumPressureBara;
    private final double maximumLiquidHoldupFraction;
    private final double maximumVelocityMPerS;
    private final double maximumEquivalentStressMpa;

    /**
     * Creates one profile sample.
     *
     * @param timeSeconds elapsed time in seconds
     * @param inletMassFlowKgS inlet mass flow in kg/s
     * @param outletMassFlowKgS outlet mass flow in kg/s
     * @param linePackMassKg total fluid inventory in kg
     * @param minimumPressureBara minimum pressure anywhere in the network in bara
     * @param maximumPressureBara maximum pressure anywhere in the network in bara
     * @param maximumLiquidHoldupFraction maximum liquid holdup fraction
     * @param maximumVelocityMPerS maximum local mixture velocity in m/s
     * @param maximumEquivalentStressMpa maximum calculated equivalent pipe stress in MPa
     */
    public Sample(double timeSeconds, double inletMassFlowKgS, double outletMassFlowKgS, double linePackMassKg,
        double minimumPressureBara, double maximumPressureBara, double maximumLiquidHoldupFraction,
        double maximumVelocityMPerS, double maximumEquivalentStressMpa) {
      this.timeSeconds = nonNegative(timeSeconds, "timeSeconds");
      this.inletMassFlowKgS = finite(inletMassFlowKgS, "inletMassFlowKgS");
      this.outletMassFlowKgS = finite(outletMassFlowKgS, "outletMassFlowKgS");
      this.linePackMassKg = nonNegative(linePackMassKg, "linePackMassKg");
      this.minimumPressureBara = nonNegative(minimumPressureBara, "minimumPressureBara");
      this.maximumPressureBara = nonNegative(maximumPressureBara, "maximumPressureBara");
      if (maximumPressureBara < minimumPressureBara) {
        throw new IllegalArgumentException("maximumPressureBara must not be below minimumPressureBara");
      }
      this.maximumLiquidHoldupFraction = fraction(maximumLiquidHoldupFraction,
          "maximumLiquidHoldupFraction");
      this.maximumVelocityMPerS = nonNegative(maximumVelocityMPerS, "maximumVelocityMPerS");
      this.maximumEquivalentStressMpa = nonNegative(maximumEquivalentStressMpa,
          "maximumEquivalentStressMpa");
    }
  }

  /** Controlled transient profile and project acceptance limits. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String networkId;
    private final String sourceModel;
    private final String sourceModelVersion;
    private final double hydraulicLengthM;
    private final double internalVolumeM3;
    private final double waveSpeedMPerS;
    private final double maximumTimeStepToTransitFraction;
    private final double maximumLinePackBalanceErrorFraction;
    private final double maximumAllowablePressureBara;
    private final double minimumAllowablePressureBara;
    private final double maximumSlugVolumeM3;
    private final double maximumAcousticMach;
    private final double maximumPressurePulsationPercent;
    private final double allowableEquivalentStressMpa;
    private final List<Sample> samples;

    private Input(Builder builder) {
      networkId = text(builder.networkId, "networkId");
      sourceModel = text(builder.sourceModel, "sourceModel");
      sourceModelVersion = text(builder.sourceModelVersion, "sourceModelVersion");
      hydraulicLengthM = positive(builder.hydraulicLengthM, "hydraulicLengthM");
      internalVolumeM3 = positive(builder.internalVolumeM3, "internalVolumeM3");
      waveSpeedMPerS = positive(builder.waveSpeedMPerS, "waveSpeedMPerS");
      maximumTimeStepToTransitFraction = positive(builder.maximumTimeStepToTransitFraction,
          "maximumTimeStepToTransitFraction");
      maximumLinePackBalanceErrorFraction = nonNegative(builder.maximumLinePackBalanceErrorFraction,
          "maximumLinePackBalanceErrorFraction");
      maximumAllowablePressureBara = positive(builder.maximumAllowablePressureBara,
          "maximumAllowablePressureBara");
      minimumAllowablePressureBara = nonNegative(builder.minimumAllowablePressureBara,
          "minimumAllowablePressureBara");
      maximumSlugVolumeM3 = nonNegative(builder.maximumSlugVolumeM3, "maximumSlugVolumeM3");
      maximumAcousticMach = positive(builder.maximumAcousticMach, "maximumAcousticMach");
      maximumPressurePulsationPercent = nonNegative(builder.maximumPressurePulsationPercent,
          "maximumPressurePulsationPercent");
      allowableEquivalentStressMpa = positive(builder.allowableEquivalentStressMpa,
          "allowableEquivalentStressMpa");
      samples = Collections.unmodifiableList(new ArrayList<Sample>(builder.samples));
    }

    public static Builder builder(String networkId, String sourceModel, String sourceModelVersion) {
      return new Builder(networkId, sourceModel, sourceModelVersion);
    }

    /** Builder for controlled transient qualification input. */
    public static final class Builder {
      private final String networkId;
      private final String sourceModel;
      private final String sourceModelVersion;
      private double hydraulicLengthM;
      private double internalVolumeM3;
      private double waveSpeedMPerS;
      private double maximumTimeStepToTransitFraction;
      private double maximumLinePackBalanceErrorFraction;
      private double maximumAllowablePressureBara;
      private double minimumAllowablePressureBara;
      private double maximumSlugVolumeM3;
      private double maximumAcousticMach;
      private double maximumPressurePulsationPercent;
      private double allowableEquivalentStressMpa;
      private final List<Sample> samples = new ArrayList<Sample>();

      private Builder(String networkId, String sourceModel, String sourceModelVersion) {
        this.networkId = networkId;
        this.sourceModel = sourceModel;
        this.sourceModelVersion = sourceModelVersion;
      }

      public Builder geometry(double lengthM, double volumeM3, double acousticWaveSpeedMPerS) {
        hydraulicLengthM = lengthM;
        internalVolumeM3 = volumeM3;
        waveSpeedMPerS = acousticWaveSpeedMPerS;
        return this;
      }

      public Builder resolutionAndBalanceLimits(double timeStepToTransitFraction,
          double linePackBalanceErrorFraction) {
        maximumTimeStepToTransitFraction = timeStepToTransitFraction;
        maximumLinePackBalanceErrorFraction = linePackBalanceErrorFraction;
        return this;
      }

      public Builder pressureLimits(double minimumBara, double maximumBara,
          double maximumPulsationPercent) {
        minimumAllowablePressureBara = minimumBara;
        maximumAllowablePressureBara = maximumBara;
        maximumPressurePulsationPercent = maximumPulsationPercent;
        return this;
      }

      public Builder responseLimits(double slugVolumeM3, double acousticMach,
          double equivalentStressMpa) {
        maximumSlugVolumeM3 = slugVolumeM3;
        maximumAcousticMach = acousticMach;
        allowableEquivalentStressMpa = equivalentStressMpa;
        return this;
      }

      public Builder addSample(Sample sample) {
        if (sample == null) {
          throw new IllegalArgumentException("sample must not be null");
        }
        samples.add(sample);
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable qualification metrics and independently visible constraints. */
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
      result.put("distributedTransientModelRequired", Boolean.TRUE);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "distributed-piping-transient-qualification";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("TRANSIENT_INPUT", "Transient piping input is required",
          "Export a controlled distributed transient profile").build();
    }
    if (input.samples.size() < 2) {
      readiness.addBlocker("TRANSIENT_SAMPLES", "At least two transient samples are required",
          "Supply an ordered transient profile");
    }
    double previous = -1.0;
    for (Sample sample : input.samples) {
      if (sample.timeSeconds <= previous) {
        readiness.addBlocker("TRANSIENT_TIME_ORDER", "Transient sample times must increase strictly",
            "Correct the source-model export");
        break;
      }
      previous = sample.timeSeconds;
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      readiness.addBlocker("TRANSIENT_PRODUCTION_EVIDENCE",
          "Production qualification requires model-validation and standards evidence",
          "Attach the hydraulic model validation and project piping basis");
    }
    if (productionQualification(context)
        && !"approved".equalsIgnoreCase(context.getAttributes().get("distributedTransientModel"))) {
      readiness.addBlocker("DISTRIBUTED_TRANSIENT_MODEL",
          "The source profile has not been approved as a distributed transient-model result",
          "Approve the model applicability or attach controlled external-solver results");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> output = EngineeringCalculationResult
        .<Result>builder("transient-piping:" + (input == null ? "unassigned" : input.networkId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }

    double integratedBoundaryImbalanceKg = 0.0;
    double maximumTimeStepSeconds = 0.0;
    double minimumPressureBara = Double.POSITIVE_INFINITY;
    double maximumPressureBara = 0.0;
    double maximumOfMinimumPressureBara = 0.0;
    double minimumOfMaximumPressureBara = Double.POSITIVE_INFINITY;
    double maximumHoldup = 0.0;
    double maximumVelocity = 0.0;
    double maximumStress = 0.0;
    for (int i = 0; i < input.samples.size(); i++) {
      Sample sample = input.samples.get(i);
      minimumPressureBara = Math.min(minimumPressureBara, sample.minimumPressureBara);
      maximumPressureBara = Math.max(maximumPressureBara, sample.maximumPressureBara);
      maximumOfMinimumPressureBara = Math.max(maximumOfMinimumPressureBara,
          sample.minimumPressureBara);
      minimumOfMaximumPressureBara = Math.min(minimumOfMaximumPressureBara,
          sample.maximumPressureBara);
      maximumHoldup = Math.max(maximumHoldup, sample.maximumLiquidHoldupFraction);
      maximumVelocity = Math.max(maximumVelocity, sample.maximumVelocityMPerS);
      maximumStress = Math.max(maximumStress, sample.maximumEquivalentStressMpa);
      if (i > 0) {
        Sample previous = input.samples.get(i - 1);
        double dt = sample.timeSeconds - previous.timeSeconds;
        maximumTimeStepSeconds = Math.max(maximumTimeStepSeconds, dt);
        double previousImbalance = previous.inletMassFlowKgS - previous.outletMassFlowKgS;
        double currentImbalance = sample.inletMassFlowKgS - sample.outletMassFlowKgS;
        integratedBoundaryImbalanceKg += 0.5 * (previousImbalance + currentImbalance) * dt;
      }
    }
    double observedLinePackChangeKg = input.samples.get(input.samples.size() - 1).linePackMassKg
        - input.samples.get(0).linePackMassKg;
    double balanceDenominator = Math.max(1.0,
        Math.max(Math.abs(integratedBoundaryImbalanceKg), Math.abs(observedLinePackChangeKg)));
    double linePackBalanceErrorFraction = Math
        .abs(integratedBoundaryImbalanceKg - observedLinePackChangeKg) / balanceDenominator;
    double acousticTransitSeconds = input.hydraulicLengthM / input.waveSpeedMPerS;
    double pressureMean = Math.max(0.5 * (maximumPressureBara + minimumPressureBara), 1.0e-12);
    double pressureExcursionBara = Math.max(maximumOfMinimumPressureBara - minimumPressureBara,
        maximumPressureBara - minimumOfMaximumPressureBara);
    double pressurePulsationPercent = 100.0 * pressureExcursionBara / pressureMean;
    double maximumSlugVolumeM3 = maximumHoldup * input.internalVolumeM3;
    double maximumAcousticMach = maximumVelocity / input.waveSpeedMPerS;

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("acousticTransitSeconds", Double.valueOf(acousticTransitSeconds));
    metrics.put("maximumTimeStepSeconds", Double.valueOf(maximumTimeStepSeconds));
    metrics.put("integratedBoundaryImbalanceKg", Double.valueOf(integratedBoundaryImbalanceKg));
    metrics.put("observedLinePackChangeKg", Double.valueOf(observedLinePackChangeKg));
    metrics.put("linePackBalanceErrorFraction", Double.valueOf(linePackBalanceErrorFraction));
    metrics.put("minimumPressureBara", Double.valueOf(minimumPressureBara));
    metrics.put("maximumPressureBara", Double.valueOf(maximumPressureBara));
    metrics.put("pressurePulsationPercent", Double.valueOf(pressurePulsationPercent));
    metrics.put("maximumSlugVolumeM3", Double.valueOf(maximumSlugVolumeM3));
    metrics.put("maximumAcousticMach", Double.valueOf(maximumAcousticMach));
    metrics.put("maximumEquivalentStressMpa", Double.valueOf(maximumStress));

    Map<String, Boolean> constraints = new LinkedHashMap<String, Boolean>();
    constraints.put("TRANSIENT_TIME_RESOLUTION", Boolean
        .valueOf(maximumTimeStepSeconds <= acousticTransitSeconds * input.maximumTimeStepToTransitFraction));
    constraints.put("LINE_PACK_MASS_BALANCE",
        Boolean.valueOf(linePackBalanceErrorFraction <= input.maximumLinePackBalanceErrorFraction));
    constraints.put("MINIMUM_PRESSURE",
        Boolean.valueOf(minimumPressureBara >= input.minimumAllowablePressureBara));
    constraints.put("MAXIMUM_PRESSURE",
        Boolean.valueOf(maximumPressureBara <= input.maximumAllowablePressureBara));
    constraints.put("PRESSURE_PULSATION",
        Boolean.valueOf(pressurePulsationPercent <= input.maximumPressurePulsationPercent));
    constraints.put("TRANSIENT_SLUG_INVENTORY",
        Boolean.valueOf(maximumSlugVolumeM3 <= input.maximumSlugVolumeM3));
    constraints.put("ACOUSTIC_VELOCITY", Boolean.valueOf(maximumAcousticMach <= input.maximumAcousticMach));
    constraints.put("EQUIVALENT_PIPE_STRESS",
        Boolean.valueOf(maximumStress <= input.allowableEquivalentStressMpa));

    return output.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
        .input("networkId", input.networkId).input("sourceModel", input.sourceModel)
        .input("sourceModelVersion", input.sourceModelVersion).input("sampleCount", Integer.valueOf(input.samples.size()))
        .value(new Result(metrics, constraints))
        .warning("Final stress, support, acoustic-induced vibration and fatigue acceptance require discipline review")
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
