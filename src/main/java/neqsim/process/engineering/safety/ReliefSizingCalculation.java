package neqsim.process.engineering.safety;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.engineering.calculation.CalculationReadiness;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationModule;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;

/** Gas, liquid, steam and governed two-phase relief-orifice sizing from relieving-case conditions. */
public final class ReliefSizingCalculation
    implements EngineeringCalculationModule<ReliefSizingCalculation.Input, ReliefSizingCalculation.Result> {

  public enum Phase {
    GAS, LIQUID, STEAM, TWO_PHASE
  }

  /** Governed relieving-case inputs in explicit SI/absolute-pressure units. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String scenarioId;
    private final Phase phase;
    private final double massFlowKgS;
    private final double relievingPressureBara;
    private final double backPressureBara;
    private final double relievingTemperatureK;
    private final double isentropicExponent;
    private final double compressibilityFactor;
    private final double molarMassKgKmol;
    private final double liquidDensityKgM3;
    private final double dischargeCoefficient;
    private final double backPressureCorrection;
    private final double combinationCorrection;
    private final double twoPhaseMassFluxKgM2S;
    private final String twoPhaseMethodReference;
    private final double[] orificeCandidatesIn2;

    private Input(Builder builder) {
      scenarioId = text(builder.scenarioId, "scenarioId");
      if (builder.phase == null) {
        throw new IllegalArgumentException("phase must not be null");
      }
      phase = builder.phase;
      massFlowKgS = positive(builder.massFlowKgS, "massFlowKgS");
      relievingPressureBara = positive(builder.relievingPressureBara, "relievingPressureBara");
      backPressureBara = nonNegative(builder.backPressureBara, "backPressureBara");
      relievingTemperatureK = builder.relievingTemperatureK;
      isentropicExponent = builder.isentropicExponent;
      compressibilityFactor = builder.compressibilityFactor;
      molarMassKgKmol = builder.molarMassKgKmol;
      liquidDensityKgM3 = builder.liquidDensityKgM3;
      dischargeCoefficient = positive(builder.dischargeCoefficient, "dischargeCoefficient");
      backPressureCorrection = positive(builder.backPressureCorrection, "backPressureCorrection");
      combinationCorrection = positive(builder.combinationCorrection, "combinationCorrection");
      twoPhaseMassFluxKgM2S = builder.twoPhaseMassFluxKgM2S;
      twoPhaseMethodReference = optional(builder.twoPhaseMethodReference);
      orificeCandidatesIn2 = builder.orificeCandidatesIn2 == null ? new double[0]
          : builder.orificeCandidatesIn2.clone();
      Arrays.sort(orificeCandidatesIn2);
      for (double candidate : orificeCandidatesIn2) {
        positive(candidate, "orificeCandidatesIn2");
      }
    }

    public static Builder builder(String scenarioId, Phase phase) {
      return new Builder(scenarioId, phase);
    }

    /** Builder retaining every correction factor as an explicit project input. */
    public static final class Builder {
      private final String scenarioId;
      private final Phase phase;
      private double massFlowKgS;
      private double relievingPressureBara;
      private double backPressureBara;
      private double relievingTemperatureK = Double.NaN;
      private double isentropicExponent = Double.NaN;
      private double compressibilityFactor = Double.NaN;
      private double molarMassKgKmol = Double.NaN;
      private double liquidDensityKgM3 = Double.NaN;
      private double dischargeCoefficient;
      private double backPressureCorrection = 1.0;
      private double combinationCorrection = 1.0;
      private double twoPhaseMassFluxKgM2S = Double.NaN;
      private String twoPhaseMethodReference = "";
      private double[] orificeCandidatesIn2 = new double[0];

      private Builder(String scenarioId, Phase phase) {
        this.scenarioId = scenarioId;
        this.phase = phase;
      }

      public Builder flowAndPressure(double massFlowKgS, double relievingPressureBara, double backPressureBara) {
        this.massFlowKgS = massFlowKgS;
        this.relievingPressureBara = relievingPressureBara;
        this.backPressureBara = backPressureBara;
        return this;
      }

      public Builder gasProperties(double temperatureK, double isentropicExponent, double compressibilityFactor,
          double molarMassKgKmol) {
        relievingTemperatureK = temperatureK;
        this.isentropicExponent = isentropicExponent;
        this.compressibilityFactor = compressibilityFactor;
        this.molarMassKgKmol = molarMassKgKmol;
        return this;
      }

      public Builder liquidDensityKgM3(double value) {
        liquidDensityKgM3 = value;
        return this;
      }

      public Builder correctionFactors(double dischargeCoefficient, double backPressureCorrection,
          double combinationCorrection) {
        this.dischargeCoefficient = dischargeCoefficient;
        this.backPressureCorrection = backPressureCorrection;
        this.combinationCorrection = combinationCorrection;
        return this;
      }

      public Builder twoPhaseMethod(double massFluxKgM2S, String methodReference) {
        twoPhaseMassFluxKgM2S = massFluxKgM2S;
        twoPhaseMethodReference = methodReference;
        return this;
      }

      public Builder orificeCandidatesIn2(double... values) {
        orificeCandidatesIn2 = values == null ? new double[0] : values.clone();
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Required/selected area, flow regime and selection utilization. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Object> values;

    Result(Map<String, Object> values) {
      this.values = values;
    }

    public Map<String, Object> toMap() {
      return new LinkedHashMap<String, Object>(values);
    }
  }

  @Override
  public String getMethod() {
    return "relief-device-phase-sizing-and-orifice-selection";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder result = CalculationReadiness.builder();
    if (input == null) {
      return result.addBlocker("RELIEF_INPUT", "Relief sizing input is required", "Supply the governed relieving case")
          .build();
    }
    if (input.backPressureBara >= input.relievingPressureBara) {
      result.addBlocker("RELIEF_DIFFERENTIAL_PRESSURE", "Backpressure must be below relieving pressure",
          "Correct the relieving/disposal-network pressure basis");
    }
    if (input.orificeCandidatesIn2.length == 0) {
      result.addBlocker("RELIEF_ORIFICES", "API/project orifice candidates are required",
          "Supply the controlled device table");
    }
    if (input.phase == Phase.GAS || input.phase == Phase.STEAM) {
      if (!positiveFinite(input.relievingTemperatureK) || !positiveFinite(input.compressibilityFactor)
          || !positiveFinite(input.molarMassKgKmol) || !Double.isFinite(input.isentropicExponent)
          || input.isentropicExponent <= 1.0) {
        result.addBlocker("RELIEF_GAS_PROPERTIES", "Gas/steam critical-flow properties are incomplete",
            "Supply T, Z, molar mass and isentropic exponent from the relieving case");
      }
    } else if (input.phase == Phase.LIQUID && !positiveFinite(input.liquidDensityKgM3)) {
      result.addBlocker("RELIEF_LIQUID_DENSITY", "Liquid density is required", "Supply relieving-case density");
    } else if (input.phase == Phase.TWO_PHASE
        && (!positiveFinite(input.twoPhaseMassFluxKgM2S) || input.twoPhaseMethodReference.isEmpty())) {
      result.addBlocker("RELIEF_TWO_PHASE_METHOD", "Approved two-phase mass-flux method is required",
          "Supply the specialist method result and controlled reference");
    }
    if (productionQualification(context)
        && (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty())) {
      result.addBlocker("RELIEF_PRODUCTION_EVIDENCE", "Relief qualification evidence is incomplete",
          "Attach scenario, sizing, disposal-network and standards evidence");
    }
    return result.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> result = EngineeringCalculationResult
        .<Result>builder("relief-sizing:" + (input == null ? "unassigned" : input.scenarioId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED).build();
    }
    double massFlux;
    String regime;
    if (input.phase == Phase.LIQUID) {
      massFlux = input.dischargeCoefficient * input.backPressureCorrection * input.combinationCorrection
          * Math.sqrt(2.0 * input.liquidDensityKgM3 * (input.relievingPressureBara - input.backPressureBara) * 1.0e5);
      regime = "INCOMPRESSIBLE";
    } else if (input.phase == Phase.TWO_PHASE) {
      massFlux = input.twoPhaseMassFluxKgM2S * input.dischargeCoefficient * input.backPressureCorrection
          * input.combinationCorrection;
      regime = "APPROVED_TWO_PHASE_MASS_FLUX";
    } else {
      double specificGasConstant = 8314.46261815324 / input.molarMassKgKmol;
      double pressurePa = input.relievingPressureBara * 1.0e5;
      double pressureRatio = input.backPressureBara / input.relievingPressureBara;
      double criticalRatio = Math.pow(2.0 / (input.isentropicExponent + 1.0),
          input.isentropicExponent / (input.isentropicExponent - 1.0));
      double idealFlux;
      if (pressureRatio <= criticalRatio) {
        idealFlux = pressurePa
            * Math.sqrt(input.isentropicExponent
                / (input.compressibilityFactor * specificGasConstant * input.relievingTemperatureK))
            * Math.pow(2.0 / (input.isentropicExponent + 1.0),
                (input.isentropicExponent + 1.0) / (2.0 * (input.isentropicExponent - 1.0)));
        regime = "CHOKED_COMPRESSIBLE";
      } else {
        double term = Math.pow(pressureRatio, 2.0 / input.isentropicExponent)
            - Math.pow(pressureRatio, (input.isentropicExponent + 1.0) / input.isentropicExponent);
        idealFlux = pressurePa
            * Math.sqrt(2.0 * input.isentropicExponent / (input.compressibilityFactor * specificGasConstant
                * input.relievingTemperatureK * (input.isentropicExponent - 1.0)) * Math.max(term, 0.0));
        regime = "SUBCRITICAL_COMPRESSIBLE";
      }
      massFlux = idealFlux * input.dischargeCoefficient * input.backPressureCorrection * input.combinationCorrection;
    }
    double requiredAreaM2 = input.massFlowKgS / Math.max(massFlux, 1.0e-12);
    double requiredAreaIn2 = requiredAreaM2 / 0.00064516;
    double selectedAreaIn2 = select(requiredAreaIn2, input.orificeCandidatesIn2);
    if (!Double.isFinite(selectedAreaIn2)) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .input("requiredAreaIn2", Double.valueOf(requiredAreaIn2))
          .message("No supplied relief-orifice candidate satisfies the calculated required area").build();
    }
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("scenarioId", input.scenarioId);
    values.put("phase", input.phase.name());
    values.put("flowRegime", regime);
    values.put("calculatedMassFluxKgM2S", Double.valueOf(massFlux));
    values.put("requiredAreaM2", Double.valueOf(requiredAreaM2));
    values.put("requiredAreaIn2", Double.valueOf(requiredAreaIn2));
    values.put("selectedOrificeAreaIn2", Double.valueOf(selectedAreaIn2));
    values.put("orificeUtilization", Double.valueOf(requiredAreaIn2 / selectedAreaIn2));
    values.put("twoPhaseMethodReference", input.twoPhaseMethodReference);
    values.put("approvalStatus", "REVIEW_REQUIRED");
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(new Result(values))
        .input("relievingPressureBara", Double.valueOf(input.relievingPressureBara))
        .input("backPressureBara", Double.valueOf(input.backPressureBara))
        .warning(
            "Device stability, certified coefficients, inlet/outlet losses and disposal-network interaction remain required")
        .build();
  }

  private static double select(double required, double[] candidates) {
    for (double candidate : candidates) {
      if (candidate >= required) {
        return candidate;
      }
    }
    return Double.NaN;
  }

  private static boolean positiveFinite(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
  }

  private static String optional(String value) {
    return value == null ? "" : value.trim();
  }

  private static String text(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static double positive(double value, String field) {
    if (!positiveFinite(value)) {
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
