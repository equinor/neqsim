package neqsim.process.engineering.rotating;

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
 * Qualifies compressor operating, startup, rundown, anti-surge, rotor and settle-out evidence.
 *
 * <p>
 * The input is intended to be assembled from {@code Compressor}, {@code CompressorAntiSurgeApplication},
 * {@code CompressorMechanicalDesign}, and {@code SettleOutPressureAnalyzer} results. Each protection concern remains an
 * independent constraint so an acceptable steady operating point cannot hide a failed transient or machinery check.
 * Vendor guarantees and rotor-dynamic approval are controlled inputs and are never inferred by this calculation.
 * </p>
 */
public final class CompressorProtectionQualificationCalculation implements
    EngineeringCalculationModule<CompressorProtectionQualificationCalculation.Input, CompressorProtectionQualificationCalculation.Result> {

  /** One compressor-map operating point from the controlled case matrix. */
  public static final class OperatingCase implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String caseId;
    private final double actualFlowM3Hr;
    private final double surgeControlFlowM3Hr;
    private final double stonewallFlowM3Hr;
    private final double dischargeTemperatureC;
    private final double shaftPowerKw;
    private final double mapExtrapolationFraction;

    public OperatingCase(String caseId, double actualFlowM3Hr, double surgeControlFlowM3Hr, double stonewallFlowM3Hr,
        double dischargeTemperatureC, double shaftPowerKw, double mapExtrapolationFraction) {
      this.caseId = text(caseId, "caseId");
      this.actualFlowM3Hr = positive(actualFlowM3Hr, "actualFlowM3Hr");
      this.surgeControlFlowM3Hr = positive(surgeControlFlowM3Hr, "surgeControlFlowM3Hr");
      this.stonewallFlowM3Hr = positive(stonewallFlowM3Hr, "stonewallFlowM3Hr");
      if (stonewallFlowM3Hr <= surgeControlFlowM3Hr) {
        throw new IllegalArgumentException("stonewallFlowM3Hr must exceed surgeControlFlowM3Hr");
      }
      this.dischargeTemperatureC = finite(dischargeTemperatureC, "dischargeTemperatureC");
      this.shaftPowerKw = positive(shaftPowerKw, "shaftPowerKw");
      this.mapExtrapolationFraction = nonNegative(mapExtrapolationFraction, "mapExtrapolationFraction");
    }
  }

  /** Controlled compressor package and protection input. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String sourceModel;
    private final String sourceModelVersion;
    private final List<OperatingCase> operatingCases;
    private final double minimumSurgeMarginFraction;
    private final double minimumStonewallMarginFraction;
    private final double maximumDischargeTemperatureC;
    private final double maximumMapExtrapolationFraction;
    private final double driverRatedPowerKw;
    private final double startupRequiredTorqueNm;
    private final double startupAvailableTorqueNm;
    private final double minimumRundownSurgeMarginFraction;
    private final double requiredRundownSurgeMarginFraction;
    private final double antiSurgeDetectionSeconds;
    private final double antiSurgeControllerSeconds;
    private final double antiSurgeInstrumentSeconds;
    private final double antiSurgeValveTravelSeconds;
    private final double antiSurgeFinalOpeningPercent;
    private final double requiredAntiSurgeOpeningPercent;
    private final double processSafetyTimeSeconds;
    private final double rotorCriticalSpeedSeparationPercent;
    private final double requiredRotorSeparationPercent;
    private final double settleOutPressureBara;
    private final double suctionSystemRatingBara;
    private final boolean vendorGuaranteeAccepted;
    private final boolean rotorDynamicReviewApproved;

    private Input(Builder builder) {
      equipmentTag = text(builder.equipmentTag, "equipmentTag");
      sourceModel = text(builder.sourceModel, "sourceModel");
      sourceModelVersion = text(builder.sourceModelVersion, "sourceModelVersion");
      operatingCases = Collections.unmodifiableList(new ArrayList<OperatingCase>(builder.operatingCases));
      minimumSurgeMarginFraction = nonNegative(builder.minimumSurgeMarginFraction, "minimumSurgeMarginFraction");
      minimumStonewallMarginFraction = nonNegative(builder.minimumStonewallMarginFraction,
          "minimumStonewallMarginFraction");
      maximumDischargeTemperatureC = finite(builder.maximumDischargeTemperatureC, "maximumDischargeTemperatureC");
      maximumMapExtrapolationFraction = nonNegative(builder.maximumMapExtrapolationFraction,
          "maximumMapExtrapolationFraction");
      driverRatedPowerKw = positive(builder.driverRatedPowerKw, "driverRatedPowerKw");
      startupRequiredTorqueNm = positive(builder.startupRequiredTorqueNm, "startupRequiredTorqueNm");
      startupAvailableTorqueNm = positive(builder.startupAvailableTorqueNm, "startupAvailableTorqueNm");
      minimumRundownSurgeMarginFraction = finite(builder.minimumRundownSurgeMarginFraction,
          "minimumRundownSurgeMarginFraction");
      requiredRundownSurgeMarginFraction = nonNegative(builder.requiredRundownSurgeMarginFraction,
          "requiredRundownSurgeMarginFraction");
      antiSurgeDetectionSeconds = nonNegative(builder.antiSurgeDetectionSeconds, "antiSurgeDetectionSeconds");
      antiSurgeControllerSeconds = nonNegative(builder.antiSurgeControllerSeconds, "antiSurgeControllerSeconds");
      antiSurgeInstrumentSeconds = nonNegative(builder.antiSurgeInstrumentSeconds, "antiSurgeInstrumentSeconds");
      antiSurgeValveTravelSeconds = nonNegative(builder.antiSurgeValveTravelSeconds, "antiSurgeValveTravelSeconds");
      antiSurgeFinalOpeningPercent = percent(builder.antiSurgeFinalOpeningPercent, "antiSurgeFinalOpeningPercent");
      requiredAntiSurgeOpeningPercent = percent(builder.requiredAntiSurgeOpeningPercent,
          "requiredAntiSurgeOpeningPercent");
      processSafetyTimeSeconds = positive(builder.processSafetyTimeSeconds, "processSafetyTimeSeconds");
      rotorCriticalSpeedSeparationPercent = nonNegative(builder.rotorCriticalSpeedSeparationPercent,
          "rotorCriticalSpeedSeparationPercent");
      requiredRotorSeparationPercent = nonNegative(builder.requiredRotorSeparationPercent,
          "requiredRotorSeparationPercent");
      settleOutPressureBara = positive(builder.settleOutPressureBara, "settleOutPressureBara");
      suctionSystemRatingBara = positive(builder.suctionSystemRatingBara, "suctionSystemRatingBara");
      vendorGuaranteeAccepted = builder.vendorGuaranteeAccepted;
      rotorDynamicReviewApproved = builder.rotorDynamicReviewApproved;
    }

    public static Builder builder(String equipmentTag, String sourceModel, String sourceModelVersion) {
      return new Builder(equipmentTag, sourceModel, sourceModelVersion);
    }

    /** Builder for compressor protection qualification input. */
    public static final class Builder {
      private final String equipmentTag;
      private final String sourceModel;
      private final String sourceModelVersion;
      private final List<OperatingCase> operatingCases = new ArrayList<OperatingCase>();
      private double minimumSurgeMarginFraction;
      private double minimumStonewallMarginFraction;
      private double maximumDischargeTemperatureC;
      private double maximumMapExtrapolationFraction;
      private double driverRatedPowerKw;
      private double startupRequiredTorqueNm;
      private double startupAvailableTorqueNm;
      private double minimumRundownSurgeMarginFraction;
      private double requiredRundownSurgeMarginFraction;
      private double antiSurgeDetectionSeconds;
      private double antiSurgeControllerSeconds;
      private double antiSurgeInstrumentSeconds;
      private double antiSurgeValveTravelSeconds;
      private double antiSurgeFinalOpeningPercent;
      private double requiredAntiSurgeOpeningPercent;
      private double processSafetyTimeSeconds;
      private double rotorCriticalSpeedSeparationPercent;
      private double requiredRotorSeparationPercent;
      private double settleOutPressureBara;
      private double suctionSystemRatingBara;
      private boolean vendorGuaranteeAccepted;
      private boolean rotorDynamicReviewApproved;

      private Builder(String equipmentTag, String sourceModel, String sourceModelVersion) {
        this.equipmentTag = equipmentTag;
        this.sourceModel = sourceModel;
        this.sourceModelVersion = sourceModelVersion;
      }

      public Builder addOperatingCase(OperatingCase value) {
        if (value == null) {
          throw new IllegalArgumentException("operatingCase must not be null");
        }
        operatingCases.add(value);
        return this;
      }

      public Builder mapLimits(double surgeMarginFraction, double stonewallMarginFraction, double dischargeTemperatureC,
          double extrapolationFraction) {
        minimumSurgeMarginFraction = surgeMarginFraction;
        minimumStonewallMarginFraction = stonewallMarginFraction;
        maximumDischargeTemperatureC = dischargeTemperatureC;
        maximumMapExtrapolationFraction = extrapolationFraction;
        return this;
      }

      public Builder driverAndStartup(double ratedPowerKw, double requiredTorqueNm, double availableTorqueNm) {
        driverRatedPowerKw = ratedPowerKw;
        startupRequiredTorqueNm = requiredTorqueNm;
        startupAvailableTorqueNm = availableTorqueNm;
        return this;
      }

      public Builder rundown(double minimumSurgeMarginFraction, double requiredSurgeMarginFraction) {
        minimumRundownSurgeMarginFraction = minimumSurgeMarginFraction;
        requiredRundownSurgeMarginFraction = requiredSurgeMarginFraction;
        return this;
      }

      public Builder antiSurgeResponse(double detectionSeconds, double controllerSeconds, double instrumentSeconds,
          double valveTravelSeconds, double finalOpeningPercent, double requiredOpeningPercent,
          double safetyTimeSeconds) {
        antiSurgeDetectionSeconds = detectionSeconds;
        antiSurgeControllerSeconds = controllerSeconds;
        antiSurgeInstrumentSeconds = instrumentSeconds;
        antiSurgeValveTravelSeconds = valveTravelSeconds;
        antiSurgeFinalOpeningPercent = finalOpeningPercent;
        requiredAntiSurgeOpeningPercent = requiredOpeningPercent;
        processSafetyTimeSeconds = safetyTimeSeconds;
        return this;
      }

      public Builder rotorDynamics(double separationPercent, double requiredSeparationPercent, boolean approved) {
        rotorCriticalSpeedSeparationPercent = separationPercent;
        requiredRotorSeparationPercent = requiredSeparationPercent;
        rotorDynamicReviewApproved = approved;
        return this;
      }

      public Builder settleOut(double pressureBara, double suctionRatingBara) {
        settleOutPressureBara = pressureBara;
        suctionSystemRatingBara = suctionRatingBara;
        return this;
      }

      public Builder vendorGuaranteeAccepted(boolean value) {
        vendorGuaranteeAccepted = value;
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Immutable compressor protection metrics and constraints. */
  public static final class Result implements EngineeringConstraintResult, Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Double> metrics;
    private final Map<String, Boolean> constraints;
    private final String governingSurgeCase;
    private final String governingStonewallCase;

    private Result(Map<String, Double> metrics, Map<String, Boolean> constraints, String governingSurgeCase,
        String governingStonewallCase) {
      this.metrics = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(metrics));
      this.constraints = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(constraints));
      this.governingSurgeCase = governingSurgeCase;
      this.governingStonewallCase = governingStonewallCase;
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
      result.put("governingSurgeCase", governingSurgeCase);
      result.put("governingStonewallCase", governingStonewallCase);
      result.put("allConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      result.put("vendorGuaranteeRequired", Boolean.TRUE);
      result.put("engineeringApprovalRequired", Boolean.TRUE);
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "compressor-protection-and-machinery-qualification";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness.addBlocker("COMPRESSOR_PROTECTION_INPUT", "Compressor protection input is required",
          "Supply controlled map, transient, machinery and settle-out evidence").build();
    }
    if (input.operatingCases.isEmpty()) {
      readiness.addBlocker("COMPRESSOR_CASES", "At least one compressor operating case is required",
          "Run the controlled compressor case matrix");
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      readiness.addBlocker("COMPRESSOR_PRODUCTION_EVIDENCE",
          "Production qualification requires vendor, machinery, dynamic and standards evidence",
          "Attach the compressor data sheet, map, rotor report, anti-surge study and project basis");
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> output = EngineeringCalculationResult
        .<Result>builder("compressor-protection:" + (input == null ? "unassigned" : input.equipmentTag), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    if (input == null) {
      return output.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }

    double minimumSurgeMargin = Double.POSITIVE_INFINITY;
    double minimumStonewallMargin = Double.POSITIVE_INFINITY;
    double maximumDischargeTemperature = -Double.MAX_VALUE;
    double maximumShaftPower = 0.0;
    double maximumExtrapolation = 0.0;
    String governingSurgeCase = "";
    String governingStonewallCase = "";
    for (OperatingCase operatingCase : input.operatingCases) {
      double surgeMargin = (operatingCase.actualFlowM3Hr - operatingCase.surgeControlFlowM3Hr)
          / operatingCase.surgeControlFlowM3Hr;
      double stonewallMargin = (operatingCase.stonewallFlowM3Hr - operatingCase.actualFlowM3Hr)
          / operatingCase.stonewallFlowM3Hr;
      if (surgeMargin < minimumSurgeMargin) {
        minimumSurgeMargin = surgeMargin;
        governingSurgeCase = operatingCase.caseId;
      }
      if (stonewallMargin < minimumStonewallMargin) {
        minimumStonewallMargin = stonewallMargin;
        governingStonewallCase = operatingCase.caseId;
      }
      maximumDischargeTemperature = Math.max(maximumDischargeTemperature, operatingCase.dischargeTemperatureC);
      maximumShaftPower = Math.max(maximumShaftPower, operatingCase.shaftPowerKw);
      maximumExtrapolation = Math.max(maximumExtrapolation, operatingCase.mapExtrapolationFraction);
    }
    double totalAntiSurgeResponseSeconds = input.antiSurgeDetectionSeconds + input.antiSurgeControllerSeconds
        + input.antiSurgeInstrumentSeconds + input.antiSurgeValveTravelSeconds;

    Map<String, Double> metrics = new LinkedHashMap<String, Double>();
    metrics.put("minimumSurgeControlMarginFraction", Double.valueOf(minimumSurgeMargin));
    metrics.put("minimumStonewallMarginFraction", Double.valueOf(minimumStonewallMargin));
    metrics.put("maximumDischargeTemperatureC", Double.valueOf(maximumDischargeTemperature));
    metrics.put("maximumShaftPowerKw", Double.valueOf(maximumShaftPower));
    metrics.put("maximumMapExtrapolationFraction", Double.valueOf(maximumExtrapolation));
    metrics.put("startupTorqueMarginNm",
        Double.valueOf(input.startupAvailableTorqueNm - input.startupRequiredTorqueNm));
    metrics.put("minimumRundownSurgeMarginFraction", Double.valueOf(input.minimumRundownSurgeMarginFraction));
    metrics.put("totalAntiSurgeResponseSeconds", Double.valueOf(totalAntiSurgeResponseSeconds));
    metrics.put("antiSurgeFinalOpeningPercent", Double.valueOf(input.antiSurgeFinalOpeningPercent));
    metrics.put("rotorCriticalSpeedSeparationPercent", Double.valueOf(input.rotorCriticalSpeedSeparationPercent));
    metrics.put("settleOutPressureBara", Double.valueOf(input.settleOutPressureBara));

    Map<String, Boolean> constraints = new LinkedHashMap<String, Boolean>();
    constraints.put("SURGE_CONTROL_MARGIN", Boolean.valueOf(minimumSurgeMargin >= input.minimumSurgeMarginFraction));
    constraints.put("STONEWALL_MARGIN",
        Boolean.valueOf(minimumStonewallMargin >= input.minimumStonewallMarginFraction));
    constraints.put("DISCHARGE_TEMPERATURE",
        Boolean.valueOf(maximumDischargeTemperature <= input.maximumDischargeTemperatureC));
    constraints.put("MAP_EXTRAPOLATION",
        Boolean.valueOf(maximumExtrapolation <= input.maximumMapExtrapolationFraction));
    constraints.put("DRIVER_RATED_POWER", Boolean.valueOf(maximumShaftPower <= input.driverRatedPowerKw));
    constraints.put("STARTUP_TORQUE", Boolean.valueOf(input.startupAvailableTorqueNm >= input.startupRequiredTorqueNm));
    constraints.put("RUNDOWN_SURGE_MARGIN",
        Boolean.valueOf(input.minimumRundownSurgeMarginFraction >= input.requiredRundownSurgeMarginFraction));
    constraints.put("ANTI_SURGE_RESPONSE_TIME",
        Boolean.valueOf(totalAntiSurgeResponseSeconds <= input.processSafetyTimeSeconds));
    constraints.put("ANTI_SURGE_PROTECTIVE_OPENING",
        Boolean.valueOf(input.antiSurgeFinalOpeningPercent >= input.requiredAntiSurgeOpeningPercent));
    constraints.put("ROTOR_CRITICAL_SPEED_SEPARATION",
        Boolean.valueOf(input.rotorCriticalSpeedSeparationPercent >= input.requiredRotorSeparationPercent));
    constraints.put("SETTLE_OUT_PRESSURE",
        Boolean.valueOf(input.settleOutPressureBara <= input.suctionSystemRatingBara));
    constraints.put("VENDOR_GUARANTEE_ACCEPTED", Boolean.valueOf(input.vendorGuaranteeAccepted));
    constraints.put("ROTOR_DYNAMIC_REVIEW_APPROVED", Boolean.valueOf(input.rotorDynamicReviewApproved));

    return output.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
        .input("equipmentTag", input.equipmentTag).input("sourceModel", input.sourceModel)
        .input("sourceModelVersion", input.sourceModelVersion)
        .input("operatingCaseCount", Integer.valueOf(input.operatingCases.size()))
        .value(new Result(metrics, constraints, governingSurgeCase, governingStonewallCase))
        .warning("Vendor guarantees, torsional/lateral rotor dynamics and final anti-surge tuning require approval")
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

  private static double percent(double value, String field) {
    if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
      throw new IllegalArgumentException(field + " must be between zero and one hundred");
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
