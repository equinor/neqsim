package neqsim.process.engineering.instrumentation;

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
 * Qualifies valve actuation and instrument installation evidence for one protective or control loop.
 *
 * <p>
 * Hydraulic Cv/noise/cavitation calculations, valve mechanical-design output, transmitter data,
 * {@code ThermowellDesignCalculator} output and dynamic loop tests can be mapped into this input. Failure action,
 * leakage class, permissive/reset/restart logic and final tuning remain controlled HAZOP, SRS, vendor and project
 * inputs.
 * </p>
 */
public final class ValveInstrumentQualificationCalculation implements
    EngineeringCalculationModule<ValveInstrumentQualificationCalculation.Input, ValveInstrumentQualificationCalculation.Result> {

  /** Controlled valve, instrument and response-budget input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String loopId;
    private final String valveTag;
    private final String instrumentTag;
    private final double requiredActuatorThrustN;
    private final double selectedActuatorThrustN;
    private final double requiredShutoffDifferentialPressureBar;
    private final double actuatorShutoffRatingBar;
    private final double measuredLeakageRateKgS;
    private final double allowableLeakageRateKgS;
    private final double valveStrokeSeconds;
    private final double instrumentResponseSeconds;
    private final double logicSolverResponseSeconds;
    private final double processSafetyTimeSeconds;
    private final double processMinimum;
    private final double processMaximum;
    private final double calibratedLowerRange;
    private final double calibratedUpperRange;
    private final double measurementUncertainty;
    private final double maximumAllowableUncertainty;
    private final double thermowellFrequencyRatio;
    private final double minimumThermowellFrequencyRatio;
    private final double thermowellStressUtilization;
    private final boolean failureActionApproved;
    private final boolean vendorSizingAccepted;
    private final boolean tapAndImpulseLineApproved;
    private final boolean tuningDynamicallyValidated;
    private final boolean permissiveResetRestartApproved;

    private Input(Builder builder) {
      loopId = text(builder.loopId, "loopId");
      valveTag = text(builder.valveTag, "valveTag");
      instrumentTag = text(builder.instrumentTag, "instrumentTag");
      requiredActuatorThrustN = positive(builder.requiredActuatorThrustN, "requiredActuatorThrustN");
      selectedActuatorThrustN = positive(builder.selectedActuatorThrustN, "selectedActuatorThrustN");
      requiredShutoffDifferentialPressureBar = nonNegative(builder.requiredShutoffDifferentialPressureBar,
          "requiredShutoffDifferentialPressureBar");
      actuatorShutoffRatingBar = nonNegative(builder.actuatorShutoffRatingBar, "actuatorShutoffRatingBar");
      measuredLeakageRateKgS = nonNegative(builder.measuredLeakageRateKgS, "measuredLeakageRateKgS");
      allowableLeakageRateKgS = nonNegative(builder.allowableLeakageRateKgS, "allowableLeakageRateKgS");
      valveStrokeSeconds = nonNegative(builder.valveStrokeSeconds, "valveStrokeSeconds");
      instrumentResponseSeconds = nonNegative(builder.instrumentResponseSeconds, "instrumentResponseSeconds");
      logicSolverResponseSeconds = nonNegative(builder.logicSolverResponseSeconds, "logicSolverResponseSeconds");
      processSafetyTimeSeconds = positive(builder.processSafetyTimeSeconds, "processSafetyTimeSeconds");
      processMinimum = finite(builder.processMinimum, "processMinimum");
      processMaximum = finite(builder.processMaximum, "processMaximum");
      calibratedLowerRange = finite(builder.calibratedLowerRange, "calibratedLowerRange");
      calibratedUpperRange = finite(builder.calibratedUpperRange, "calibratedUpperRange");
      if (processMaximum < processMinimum || calibratedUpperRange <= calibratedLowerRange) {
        throw new IllegalArgumentException("process and calibrated ranges must be ordered");
      }
      measurementUncertainty = nonNegative(builder.measurementUncertainty, "measurementUncertainty");
      maximumAllowableUncertainty = nonNegative(builder.maximumAllowableUncertainty, "maximumAllowableUncertainty");
      thermowellFrequencyRatio = nonNegative(builder.thermowellFrequencyRatio, "thermowellFrequencyRatio");
      minimumThermowellFrequencyRatio = nonNegative(builder.minimumThermowellFrequencyRatio,
          "minimumThermowellFrequencyRatio");
      thermowellStressUtilization = nonNegative(builder.thermowellStressUtilization, "thermowellStressUtilization");
      failureActionApproved = builder.failureActionApproved;
      vendorSizingAccepted = builder.vendorSizingAccepted;
      tapAndImpulseLineApproved = builder.tapAndImpulseLineApproved;
      tuningDynamicallyValidated = builder.tuningDynamicallyValidated;
      permissiveResetRestartApproved = builder.permissiveResetRestartApproved;
    }

    public static Builder builder(String loopId, String valveTag, String instrumentTag) {
      return new Builder(loopId, valveTag, instrumentTag);
    }

    /** Builder for controlled valve and instrument evidence. */
    public static final class Builder {
      private final String loopId;
      private final String valveTag;
      private final String instrumentTag;
      private double requiredActuatorThrustN;
      private double selectedActuatorThrustN;
      private double requiredShutoffDifferentialPressureBar;
      private double actuatorShutoffRatingBar;
      private double measuredLeakageRateKgS;
      private double allowableLeakageRateKgS;
      private double valveStrokeSeconds;
      private double instrumentResponseSeconds;
      private double logicSolverResponseSeconds;
      private double processSafetyTimeSeconds;
      private double processMinimum;
      private double processMaximum;
      private double calibratedLowerRange;
      private double calibratedUpperRange;
      private double measurementUncertainty;
      private double maximumAllowableUncertainty;
      private double thermowellFrequencyRatio;
      private double minimumThermowellFrequencyRatio;
      private double thermowellStressUtilization;
      private boolean failureActionApproved;
      private boolean vendorSizingAccepted;
      private boolean tapAndImpulseLineApproved;
      private boolean tuningDynamicallyValidated;
      private boolean permissiveResetRestartApproved;

      private Builder(String loopId, String valveTag, String instrumentTag) {
        this.loopId = loopId;
        this.valveTag = valveTag;
        this.instrumentTag = instrumentTag;
      }

      public Builder actuator(double requiredThrustN, double selectedThrustN, double requiredShutoffBar,
          double shutoffRatingBar) {
        requiredActuatorThrustN = requiredThrustN;
        selectedActuatorThrustN = selectedThrustN;
        requiredShutoffDifferentialPressureBar = requiredShutoffBar;
        actuatorShutoffRatingBar = shutoffRatingBar;
        return this;
      }

      public Builder leakage(double measuredKgS, double allowableKgS) {
        measuredLeakageRateKgS = measuredKgS;
        allowableLeakageRateKgS = allowableKgS;
        return this;
      }

      public Builder responseBudget(double valveSeconds, double instrumentSeconds, double logicSeconds,
          double safetyTimeSeconds) {
        valveStrokeSeconds = valveSeconds;
        instrumentResponseSeconds = instrumentSeconds;
        logicSolverResponseSeconds = logicSeconds;
        processSafetyTimeSeconds = safetyTimeSeconds;
        return this;
      }

      public Builder transmitterRange(double processMinimum, double processMaximum, double calibratedLower,
          double calibratedUpper, double uncertainty, double allowableUncertainty) {
        this.processMinimum = processMinimum;
        this.processMaximum = processMaximum;
        calibratedLowerRange = calibratedLower;
        calibratedUpperRange = calibratedUpper;
        measurementUncertainty = uncertainty;
        maximumAllowableUncertainty = allowableUncertainty;
        return this;
      }

      public Builder thermowell(double frequencyRatio, double minimumFrequencyRatio, double stressUtilization) {
        thermowellFrequencyRatio = frequencyRatio;
        minimumThermowellFrequencyRatio = minimumFrequencyRatio;
        thermowellStressUtilization = stressUtilization;
        return this;
      }

      public Builder approvals(boolean failureAction, boolean vendorSizing, boolean installation, boolean dynamicTuning,
          boolean permissiveResetRestart) {
        failureActionApproved = failureAction;
        vendorSizingAccepted = vendorSizing;
        tapAndImpulseLineApproved = installation;
        tuningDynamicallyValidated = dynamicTuning;
        permissiveResetRestartApproved = permissiveResetRestart;
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable qualification metrics and constraints. */
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
      result.put("failureActionRequiresHazopOrSrsApproval", Boolean.TRUE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "valve-instrument-installation-and-response-qualification";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("VALVE_INSTRUMENT_INPUT", "Valve and instrument input is required",
          "Supply valve, transmitter, installation and response evidence").build();
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      readiness.addBlocker("VALVE_INSTRUMENT_PRODUCTION_EVIDENCE",
          "Production qualification requires vendor, installation, SRS and standards evidence",
          "Attach controlled vendor data, loop calculations, installation review and SRS references");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> output = EngineeringCalculationResult
        .<Result>builder("valve-instrument:" + (input == null ? "unassigned" : input.loopId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    if (input == null) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED)
          .warning("Calculation input is missing")
          .build();
    }
    double totalResponseSeconds = input.valveStrokeSeconds + input.instrumentResponseSeconds
        + input.logicSolverResponseSeconds;
    double rangeSpan = input.calibratedUpperRange - input.calibratedLowerRange;
    double processSpan = input.processMaximum - input.processMinimum;

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("actuatorThrustMarginN", Double.valueOf(input.selectedActuatorThrustN - input.requiredActuatorThrustN));
    metrics.put("shutoffPressureMarginBar",
        Double.valueOf(input.actuatorShutoffRatingBar - input.requiredShutoffDifferentialPressureBar));
    metrics.put("leakageMarginKgS", Double.valueOf(input.allowableLeakageRateKgS - input.measuredLeakageRateKgS));
    metrics.put("totalSafetyResponseSeconds", Double.valueOf(totalResponseSeconds));
    metrics.put("processSafetyTimeMarginSeconds",
        Double.valueOf(input.processSafetyTimeSeconds - totalResponseSeconds));
    metrics.put("calibratedSpan", Double.valueOf(rangeSpan));
    metrics.put("processSpan", Double.valueOf(processSpan));
    metrics.put("measurementUncertainty", Double.valueOf(input.measurementUncertainty));
    metrics.put("thermowellFrequencyRatio", Double.valueOf(input.thermowellFrequencyRatio));
    metrics.put("thermowellStressUtilization", Double.valueOf(input.thermowellStressUtilization));

    Map<String, Boolean> constraints = new LinkedHashMap<String, Boolean>();
    constraints.put("ACTUATOR_THRUST", Boolean.valueOf(input.selectedActuatorThrustN >= input.requiredActuatorThrustN));
    constraints.put("SHUTOFF_DIFFERENTIAL_PRESSURE",
        Boolean.valueOf(input.actuatorShutoffRatingBar >= input.requiredShutoffDifferentialPressureBar));
    constraints.put("LEAKAGE_RATE", Boolean.valueOf(input.measuredLeakageRateKgS <= input.allowableLeakageRateKgS));
    constraints.put("PROCESS_SAFETY_TIME", Boolean.valueOf(totalResponseSeconds <= input.processSafetyTimeSeconds));
    constraints.put("TRANSMITTER_RANGE", Boolean.valueOf(
        input.calibratedLowerRange <= input.processMinimum && input.calibratedUpperRange >= input.processMaximum));
    constraints.put("MEASUREMENT_UNCERTAINTY",
        Boolean.valueOf(input.measurementUncertainty <= input.maximumAllowableUncertainty));
    constraints.put("THERMOWELL_FREQUENCY_SEPARATION",
        Boolean.valueOf(input.thermowellFrequencyRatio >= input.minimumThermowellFrequencyRatio));
    constraints.put("THERMOWELL_STRESS", Boolean.valueOf(input.thermowellStressUtilization <= 1.0));
    constraints.put("FAILURE_ACTION_APPROVED", Boolean.valueOf(input.failureActionApproved));
    constraints.put("VENDOR_SIZING_ACCEPTED", Boolean.valueOf(input.vendorSizingAccepted));
    constraints.put("TAP_AND_IMPULSE_LINE_APPROVED", Boolean.valueOf(input.tapAndImpulseLineApproved));
    constraints.put("DYNAMIC_TUNING_VALIDATED", Boolean.valueOf(input.tuningDynamicallyValidated));
    constraints.put("PERMISSIVE_RESET_RESTART_APPROVED", Boolean.valueOf(input.permissiveResetRestartApproved));

    return output.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).input("loopId", input.loopId)
        .input("valveTag", input.valveTag).input("instrumentTag", input.instrumentTag)
        .value(new Result(metrics, constraints))
        .warning("Valve leakage class, final actuator selection, installation and loop tuning require approval")
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

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }
}
