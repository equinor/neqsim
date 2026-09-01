package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed valve and instrument engineering calculations across operating and safety cases. */
public final class ValveInstrumentDesignCalculations {
  private ValveInstrumentDesignCalculations() {
  }

  public enum FailurePosition {
    FAIL_OPEN, FAIL_CLOSED, FAIL_LAST, HAZOP_INPUT_REQUIRED
  }

  /** Case-envelope input for a control or shutdown valve. */
  public static final class ValveInput implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String tag;
    private final String governingCaseId;
    private final double requiredCv;
    private final double selectedCv;
    private final double designOpeningPercent;
    private final double inletPressureBara;
    private final double outletPressureBara;
    private final double vaporPressureBara;
    private final double criticalPressureBara;
    private final double densityKgM3;
    private final double massFlowKgS;
    private final double strokeTimeSeconds;
    private final double maximumAllowedStrokeTimeSeconds;
    private final FailurePosition failurePosition;

    public ValveInput(String tag, String governingCaseId, double requiredCv, double selectedCv,
        double designOpeningPercent, double inletPressureBara, double outletPressureBara, double vaporPressureBara,
        double criticalPressureBara, double densityKgM3, double massFlowKgS, double strokeTimeSeconds,
        double maximumAllowedStrokeTimeSeconds, FailurePosition failurePosition) {
      this.tag = text(tag, "tag");
      this.governingCaseId = text(governingCaseId, "governingCaseId");
      this.requiredCv = positive(requiredCv, "requiredCv");
      this.selectedCv = positive(selectedCv, "selectedCv");
      this.designOpeningPercent = positive(designOpeningPercent, "designOpeningPercent");
      if (designOpeningPercent > 100.0) {
        throw new IllegalArgumentException("designOpeningPercent must not exceed 100");
      }
      this.inletPressureBara = positive(inletPressureBara, "inletPressureBara");
      this.outletPressureBara = nonNegative(outletPressureBara, "outletPressureBara");
      this.vaporPressureBara = nonNegative(vaporPressureBara, "vaporPressureBara");
      this.criticalPressureBara = positive(criticalPressureBara, "criticalPressureBara");
      this.densityKgM3 = positive(densityKgM3, "densityKgM3");
      this.massFlowKgS = nonNegative(massFlowKgS, "massFlowKgS");
      this.strokeTimeSeconds = positive(strokeTimeSeconds, "strokeTimeSeconds");
      this.maximumAllowedStrokeTimeSeconds = positive(maximumAllowedStrokeTimeSeconds,
          "maximumAllowedStrokeTimeSeconds");
      this.failurePosition = failurePosition == null ? FailurePosition.HAZOP_INPUT_REQUIRED : failurePosition;
    }
  }

  /** Valve result including hydraulic phenomena, actuator screening and controlled failure proposal. */
  public static final class ValveResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Object> values;

    ValveResult(Map<String, Object> values) {
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> toMap() {
      return new LinkedHashMap<String, Object>(values);
    }
  }

  /** IEC 60534-based preliminary valve and actuator screening. */
  public static final class Valve implements EngineeringCalculationModule<ValveInput, ValveResult> {
    @Override
    public String getMethod() {
      return "control-and-shutdown-valve-envelope-design";
    }

    @Override
    public String getMethodVersion() {
      return "2.0";
    }

    @Override
    public CalculationReadiness assess(ValveInput input, EngineeringCalculationContext context) {
      CalculationReadiness.Builder readiness = CalculationReadiness.builder();
      if (input == null) {
        readiness.addBlocker("VALVE_INPUT", "Valve case-envelope input is required", "Supply IEC 60534 inputs");
      } else if (input.failurePosition == FailurePosition.HAZOP_INPUT_REQUIRED) {
        if (productionQualification(context)) {
          readiness.addBlocker("VALVE_FAILURE_POSITION", "Failure position has not been selected by HAZOP",
              "Approve fail-open, fail-closed, or fail-last in the safeguarding review");
        } else {
          readiness.addWarning("VALVE_FAILURE_POSITION", "Failure position has not been selected by HAZOP",
              "Approve fail-open, fail-closed, or fail-last in the safeguarding review");
        }
      }
      if (productionQualification(context)
          && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
        readiness.addBlocker("VALVE_PRODUCTION_EVIDENCE", "Valve qualification evidence is incomplete",
            "Attach IEC 60534/project standards, vendor sizing and independent benchmark evidence");
      }
      return readiness.build();
    }

    @Override
    public EngineeringCalculationResult<ValveResult> calculate(ValveInput input,
        EngineeringCalculationContext context) {
      CalculationReadiness readiness = assess(input, context);
      EngineeringCalculationResult.Builder<ValveResult> result = EngineeringCalculationResult
          .<ValveResult>builder("valve:" + (input == null ? "unassigned" : input.tag), getMethod(), getMethodVersion())
          .context(context).readiness(readiness);
      if (!readiness.isReady()) {
        return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
      }
      double differentialPressure = Math.max(input.inletPressureBara - input.outletPressureBara, 0.0);
      double pressureRatio = differentialPressure / input.inletPressureBara;
      double criticalRatio = 0.72 * (1.0 - 0.28 * input.vaporPressureBara / input.criticalPressureBara);
      boolean choked = pressureRatio >= criticalRatio;
      boolean flashing = input.outletPressureBara <= input.vaporPressureBara;
      double cavitationIndex = (input.inletPressureBara - input.vaporPressureBara)
          / Math.max(differentialPressure, 1.0e-12);
      double opening = input.designOpeningPercent * input.requiredCv / input.selectedCv;
      double actuatorForceIndex = differentialPressure * input.selectedCv;
      double noiseScreenDbA = 70.0 + 10.0 * Math.log10(Math.max(input.massFlowKgS * differentialPressure, 1.0));
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("tag", input.tag);
      values.put("governingCaseId", input.governingCaseId);
      values.put("requiredCv", Double.valueOf(input.requiredCv));
      values.put("selectedCv", Double.valueOf(input.selectedCv));
      values.put("predictedOpeningPercent", Double.valueOf(opening));
      values.put("chokedFlowScreen", Boolean.valueOf(choked));
      values.put("flashingScreen", Boolean.valueOf(flashing));
      values.put("cavitationIndex", Double.valueOf(cavitationIndex));
      values.put("noiseScreenDbA", Double.valueOf(noiseScreenDbA));
      values.put("actuatorForceIndex", Double.valueOf(actuatorForceIndex));
      values.put("strokeTimeSatisfied",
          Boolean.valueOf(input.strokeTimeSeconds <= input.maximumAllowedStrokeTimeSeconds));
      values.put("failurePositionProposal", input.failurePosition.name());
      values.put("characteristic", "EQUAL_PERCENTAGE_PRELIMINARY");
      values.put("leakageClass", "PROJECT_INPUT_REQUIRED");
      values.put("approvalStatus", "REVIEW_REQUIRED");
      return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
          .value(new ValveResult(values)).input("standard", "IEC 60534")
          .warning("Noise, cavitation, actuator thrust and leakage class require vendor verification").build();
    }
  }

  /** Instrument range, uncertainty, response, tap and preliminary control-loop input. */
  public static final class InstrumentInput implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String tag;
    private final String governingCaseId;
    private final double minimumProcessValue;
    private final double maximumProcessValue;
    private final double lowerRangeValue;
    private final double upperRangeValue;
    private final double uncertaintyFractionOfSpan;
    private final double sensorResponseSeconds;
    private final double logicResponseSeconds;
    private final double finalElementResponseSeconds;
    private final double processSafetyTimeSeconds;
    private final double processGain;
    private final double processTimeConstantSeconds;

    public InstrumentInput(String tag, String governingCaseId, double minimumProcessValue, double maximumProcessValue,
        double lowerRangeValue, double upperRangeValue, double uncertaintyFractionOfSpan, double sensorResponseSeconds,
        double logicResponseSeconds, double finalElementResponseSeconds, double processSafetyTimeSeconds,
        double processGain, double processTimeConstantSeconds) {
      this.tag = text(tag, "tag");
      this.governingCaseId = text(governingCaseId, "governingCaseId");
      if (!Double.isFinite(minimumProcessValue) || !Double.isFinite(maximumProcessValue)
          || !Double.isFinite(lowerRangeValue) || !Double.isFinite(upperRangeValue) || !Double.isFinite(processGain)) {
        throw new IllegalArgumentException("Instrument range, process envelope and gain must be finite");
      }
      if (maximumProcessValue < minimumProcessValue) {
        throw new IllegalArgumentException("maximumProcessValue must not be below minimumProcessValue");
      }
      this.minimumProcessValue = minimumProcessValue;
      this.maximumProcessValue = maximumProcessValue;
      this.lowerRangeValue = lowerRangeValue;
      this.upperRangeValue = upperRangeValue;
      this.uncertaintyFractionOfSpan = nonNegative(uncertaintyFractionOfSpan, "uncertaintyFractionOfSpan");
      this.sensorResponseSeconds = nonNegative(sensorResponseSeconds, "sensorResponseSeconds");
      this.logicResponseSeconds = nonNegative(logicResponseSeconds, "logicResponseSeconds");
      this.finalElementResponseSeconds = nonNegative(finalElementResponseSeconds, "finalElementResponseSeconds");
      this.processSafetyTimeSeconds = positive(processSafetyTimeSeconds, "processSafetyTimeSeconds");
      this.processGain = processGain;
      this.processTimeConstantSeconds = positive(processTimeConstantSeconds, "processTimeConstantSeconds");
    }
  }

  /** Instrument and preliminary loop-design output. */
  public static final class InstrumentResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Object> values;

    InstrumentResult(Map<String, Object> values) {
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    public Map<String, Object> toMap() {
      return new LinkedHashMap<String, Object>(values);
    }
  }

  /** ANSI/ISA-5.1 and IEC 61511-aligned preliminary instrument/loop calculation. */
  public static final class Instrument implements EngineeringCalculationModule<InstrumentInput, InstrumentResult> {
    @Override
    public String getMethod() {
      return "instrument-range-response-and-loop-design";
    }

    @Override
    public String getMethodVersion() {
      return "2.0";
    }

    @Override
    public CalculationReadiness assess(InstrumentInput input, EngineeringCalculationContext context) {
      CalculationReadiness.Builder result = CalculationReadiness.builder();
      if (input == null) {
        result.addBlocker("INSTRUMENT_INPUT", "Instrument design input is required", "Supply process envelope");
      } else if (input.upperRangeValue <= input.lowerRangeValue) {
        result.addBlocker("INSTRUMENT_RANGE", "Upper range must exceed lower range", "Correct calibrated range");
      }
      if (productionQualification(context)) {
        if (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty()) {
          result.addBlocker("INSTRUMENT_PRODUCTION_EVIDENCE", "Instrument qualification evidence is incomplete",
              "Attach range, uncertainty, response and vendor evidence");
        }
        if (!"approved".equalsIgnoreCase(context.getAttributes().get("instrumentInstallationEvidence"))) {
          result.addBlocker("INSTRUMENT_INSTALLATION", "Tap, impulse-line or thermowell evidence is not approved",
              "Attach the applicable installation and mechanical verification record");
        }
      }
      return result.build();
    }

    @Override
    public EngineeringCalculationResult<InstrumentResult> calculate(InstrumentInput input,
        EngineeringCalculationContext context) {
      CalculationReadiness readiness = assess(input, context);
      EngineeringCalculationResult.Builder<InstrumentResult> result = EngineeringCalculationResult
          .<InstrumentResult>builder("instrument:" + (input == null ? "unassigned" : input.tag), getMethod(),
              getMethodVersion())
          .context(context).readiness(readiness);
      if (!readiness.isReady()) {
        return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
      }
      double span = input.upperRangeValue - input.lowerRangeValue;
      double uncertainty = span * input.uncertaintyFractionOfSpan;
      double totalResponse = input.sensorResponseSeconds + input.logicResponseSeconds
          + input.finalElementResponseSeconds;
      double responseMargin = input.processSafetyTimeSeconds - totalResponse;
      double controllerGain = Math.abs(input.processGain) < 1.0e-12 ? 0.0 : 0.5 / input.processGain;
      Map<String, Object> values = new LinkedHashMap<String, Object>();
      values.put("tag", input.tag);
      values.put("governingCaseId", input.governingCaseId);
      values.put("calibratedSpan", Double.valueOf(span));
      values.put("measurementUncertainty", Double.valueOf(uncertainty));
      values.put("rangeCoversProcessEnvelope", Boolean.valueOf(
          input.minimumProcessValue >= input.lowerRangeValue && input.maximumProcessValue <= input.upperRangeValue));
      values.put("totalSafetyResponseSeconds", Double.valueOf(totalResponse));
      values.put("processSafetyTimeMarginSeconds", Double.valueOf(responseMargin));
      values.put("preliminaryControllerGain", Double.valueOf(controllerGain));
      values.put("preliminaryIntegralTimeSeconds", Double.valueOf(input.processTimeConstantSeconds));
      values.put("tapAndImpulseLineStatus", "PROJECT_LAYOUT_INPUT_REQUIRED");
      values.put("thermowellStatus", "WAKE_FREQUENCY_REVIEW_IF_APPLICABLE");
      values.put("permissiveResetRestartLogic", "CAUSE_AND_EFFECT_INPUT_REQUIRED");
      values.put("standards", "ANSI/ISA-5.1-2024; NORSOK I-001:2025+AC:2026; NORSOK I-002:2021; IEC 61511");
      values.put("approvalStatus", "REVIEW_REQUIRED");
      return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED)
          .value(new InstrumentResult(values)).warning("Loop pairing and tuning require dynamic validation").build();
    }
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
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
}
