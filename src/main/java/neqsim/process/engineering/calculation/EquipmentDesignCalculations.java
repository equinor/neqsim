package neqsim.process.engineering.calculation;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed preliminary equipment calculations used by process-to-engineering workflows. */
public final class EquipmentDesignCalculations {
  private EquipmentDesignCalculations() {
  }

  /** Common governed input for one equipment-family calculation. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String governingCaseId;
    private final String designBasisReference;
    private final Map<String, Double> values;

    private Input(Builder builder) {
      equipmentTag = requireText(builder.equipmentTag, "equipmentTag");
      governingCaseId = requireText(builder.governingCaseId, "governingCaseId");
      designBasisReference = requireText(builder.designBasisReference, "designBasisReference");
      values = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(builder.values));
    }

    public static Builder builder(String equipmentTag, String governingCaseId, String designBasisReference) {
      return new Builder(equipmentTag, governingCaseId, designBasisReference);
    }

    public double require(String name) {
      Double value = values.get(name);
      if (value == null) {
        throw new IllegalArgumentException("Missing equipment design input " + name + " for " + equipmentTag);
      }
      return value.doubleValue();
    }

    public double value(String name, double defaultValue) {
      Double value = values.get(name);
      return value == null ? defaultValue : value.doubleValue();
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("equipmentTag", equipmentTag);
      result.put("governingCaseId", governingCaseId);
      result.put("designBasisReference", designBasisReference);
      result.put("values", new LinkedHashMap<String, Double>(values));
      return result;
    }

    /** Builder for governed equipment inputs. */
    public static final class Builder {
      private final String equipmentTag;
      private final String governingCaseId;
      private final String designBasisReference;
      private final Map<String, Double> values = new LinkedHashMap<String, Double>();

      private Builder(String equipmentTag, String governingCaseId, String designBasisReference) {
        this.equipmentTag = equipmentTag;
        this.governingCaseId = governingCaseId;
        this.designBasisReference = designBasisReference;
      }

      public Builder value(String name, double value) {
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException(name + " must be finite");
        }
        values.put(requireText(name, "value name"), Double.valueOf(value));
        return this;
      }

      public Input build() {
        return new Input(this);
      }
    }
  }

  /** Common typed output with constraints and review state. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String equipmentTag;
    private final String family;
    private final String governingCaseId;
    private final String designBasisReference;
    private final Map<String, Double> values;
    private final Map<String, Boolean> constraints;

    Result(Input input, String family, Map<String, Double> values, Map<String, Boolean> constraints) {
      equipmentTag = input.equipmentTag;
      governingCaseId = input.governingCaseId;
      designBasisReference = input.designBasisReference;
      this.family = family;
      this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(values));
      this.constraints = Collections.unmodifiableMap(new LinkedHashMap<String, Boolean>(constraints));
    }

    public double requireValue(String name) {
      Double value = values.get(name);
      if (value == null) {
        throw new IllegalArgumentException("Unknown result value " + name);
      }
      return value.doubleValue();
    }

    public boolean allConstraintsSatisfied() {
      for (Boolean value : constraints.values()) {
        if (!value.booleanValue()) {
          return false;
        }
      }
      return true;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("equipmentTag", equipmentTag);
      result.put("family", family);
      result.put("governingCaseId", governingCaseId);
      result.put("designBasisReference", designBasisReference);
      result.put("values", new LinkedHashMap<String, Double>(values));
      result.put("constraints", new LinkedHashMap<String, Boolean>(constraints));
      result.put("allConstraintsSatisfied", Boolean.valueOf(allConstraintsSatisfied()));
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }
  }

  private abstract static class FamilyModule implements EngineeringCalculationModule<Input, Result> {
    private final String method;
    private final String[] requiredInputs;

    FamilyModule(String method, String... requiredInputs) {
      this.method = method;
      this.requiredInputs = requiredInputs.clone();
    }

    @Override
    public String getMethod() {
      return method;
    }

    @Override
    public String getMethodVersion() {
      return "2.0";
    }

    @Override
    public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
      CalculationReadiness.Builder readiness = CalculationReadiness.builder();
      if (input == null) {
        return readiness.addBlocker("EQUIPMENT_INPUT", "Equipment design input is required",
            "Supply a governed process/design-case input").build();
      }
      for (String required : requiredInputs) {
        if (!input.values.containsKey(required)) {
          readiness.addBlocker("EQUIPMENT_INPUT_" + required.toUpperCase(), "Missing required input " + required,
              "Supply the value from a converged process/design case");
        }
      }
      if (productionQualification(context)) {
        for (String required : productionRequiredInputs()) {
          if (!input.values.containsKey(required)) {
            readiness.addBlocker("PRODUCTION_INPUT_" + required.toUpperCase(),
                "Production qualification requires explicit input " + required,
                "Supply a governed project value; screening defaults cannot qualify this method");
          }
        }
        if (context.getEvidenceReferences().isEmpty() || context.getStandardReferences().isEmpty()) {
          readiness.addBlocker("PRODUCTION_METHOD_EVIDENCE",
              "Production qualification requires standard and evidence references",
              "Attach the controlled calculation basis and independent evidence");
        }
      }
      return readiness.build();
    }

    @Override
    public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
      CalculationReadiness readiness = assess(input, context);
      EngineeringCalculationResult.Builder<Result> builder = EngineeringCalculationResult
          .<Result>builder(getMethod() + ":" + (input == null ? "unassigned" : input.equipmentTag), getMethod(),
              getMethodVersion())
          .context(context).readiness(readiness).input("designBasis", input == null ? null : input.toMap());
      if (!readiness.isReady()) {
        return builder.status(EngineeringCalculationResult.Status.BLOCKED)
            .message("Required governed equipment-design inputs are incomplete").build();
      }
      Result result = evaluate(input);
      double nominal = uncertaintyBasis(result);
      return builder.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(result)
          .uncertainty(new EngineeringCalculationResult.Uncertainty(0.9 * nominal, nominal, 1.1 * nominal, "1",
              "Preliminary method screening band; replace with vendor/code uncertainty"))
          .warning("Preliminary sizing requires discipline and vendor verification").build();
    }

    abstract Result evaluate(Input input);

    abstract double uncertaintyBasis(Result result);

    String[] productionRequiredInputs() {
      return new String[0];
    }
  }

  /** Separator/scrubber capacity, retention, level-volume and response-time calculation. */
  public static final class Separator extends FamilyModule {
    public Separator() {
      super("separator-scrubber-preliminary-design", "gasFlowM3s", "liquidFlowM3s", "gasDensityKgM3",
          "liquidDensityKgM3");
    }

    @Override
    Result evaluate(Input input) {
      double gasFlow = input.require("gasFlowM3s");
      double liquidFlow = input.require("liquidFlowM3s");
      double gasDensity = input.require("gasDensityKgM3");
      double liquidDensity = input.require("liquidDensityKgM3");
      double k = input.value("gasLoadFactorMPerS", 0.107);
      double retention = input.value("retentionTimeS", 120.0);
      double terminalVelocity = k * Math.sqrt(Math.max(liquidDensity - gasDensity, 1.0e-9) / gasDensity);
      double diameter = Math.sqrt(4.0 * gasFlow / Math.max(Math.PI * terminalVelocity * 0.5, 1.0e-12));
      double liquidArea = Math.PI * diameter * diameter * 0.25 * 0.5;
      double length = Math.max(3.0 * diameter, liquidFlow * retention / Math.max(liquidArea, 1.0e-12));
      Map<String, Double> values = values("insideDiameterM", diameter, "tangentLengthM", length,
          "gasTerminalVelocityMPerS", terminalVelocity, "workingLiquidVolumeM3", liquidFlow * retention,
          "highHighResponseTimeS", input.value("highHighResponseTimeS", 10.0), "inletMomentumPa",
          gasDensity * terminalVelocity * terminalVelocity);
      Map<String, Boolean> constraints = constraints("gasCapacity", diameter > 0.0, "retention",
          length >= 3.0 * diameter, "levelResponse",
          input.value("availableTripResponseTimeS", 20.0) >= input.value("highHighResponseTimeS", 10.0));
      return new Result(input, "SEPARATOR_OR_SCRUBBER", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("insideDiameterM");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "gasLoadFactorMPerS", "retentionTimeS", "highHighResponseTimeS",
          "availableTripResponseTimeS" };
    }
  }

  /** Compressor map-envelope, recycle, settle-out and driver screening. */
  public static final class Compressor extends FamilyModule {
    public Compressor() {
      super("compressor-package-envelope-design", "operatingFlowM3s", "surgeFlowM3s", "chokeFlowM3s", "shaftPowerKw");
    }

    @Override
    Result evaluate(Input input) {
      double flow = input.require("operatingFlowM3s");
      double surge = input.require("surgeFlowM3s");
      double choke = input.require("chokeFlowM3s");
      double power = input.require("shaftPowerKw");
      double driver = power * (1.0 + input.value("driverMarginFraction", 0.10));
      double recycle = Math.max(input.value("minimumSurgeMarginFraction", 0.10) * flow - (flow - surge), 0.0);
      double recycleCv = recycle / Math.sqrt(Math.max(input.value("recycleDifferentialPressureBar", 1.0), 1.0e-9));
      Map<String, Double> values = values("driverRequiredPowerKw", driver, "surgeMarginPercent",
          100.0 * (flow - surge) / Math.max(flow, 1.0e-12), "chokeMarginPercent",
          100.0 * (choke - flow) / Math.max(flow, 1.0e-12), "recycleRequiredCv", recycleCv, "settleOutPressureBara",
          input.value("settleOutPressureBara", 0.0));
      Map<String, Boolean> constraints = constraints("surgeMargin", flow > surge, "chokeMargin", flow < choke,
          "mapInterpolation",
          input.value("mapExtrapolationFraction", 0.0) <= input.value("maxMapExtrapolationFraction", 0.02),
          "startupPower", input.value("startupPowerKw", power) <= driver);
      return new Result(input, "COMPRESSOR", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("driverRequiredPowerKw");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "driverMarginFraction", "minimumSurgeMarginFraction", "recycleDifferentialPressureBar",
          "settleOutPressureBara", "mapExtrapolationFraction", "maxMapExtrapolationFraction", "startupPowerKw" };
    }
  }

  /** Pump system-curve, NPSH, recycle and driver screening. */
  public static final class Pump extends FamilyModule {
    public Pump() {
      super("pump-package-hydraulic-design", "flowM3s", "headM", "npshaM", "npshrM", "shaftPowerKw");
    }

    @Override
    Result evaluate(Input input) {
      double flow = input.require("flowM3s");
      double head = input.require("headM");
      double npshMargin = input.require("npshaM") - input.require("npshrM");
      double minimumFlow = input.value("minimumContinuousFlowM3s", 0.30 * flow);
      double recycle = Math.max(minimumFlow - input.value("turndownFlowM3s", flow), 0.0);
      Map<String, Double> values = values("systemCurveCoefficient", head / Math.max(flow * flow, 1.0e-12),
          "npshMarginM", npshMargin, "minimumFlowRecycleM3s", recycle, "driverRequiredPowerKw",
          input.require("shaftPowerKw") * (1.0 + input.value("driverMarginFraction", 0.10)));
      Map<String, Boolean> constraints = constraints("npshMargin", npshMargin >= input.value("minimumNpshMarginM", 1.0),
          "minimumFlow", input.value("turndownFlowM3s", flow) + recycle >= minimumFlow);
      return new Result(input, "PUMP", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("driverRequiredPowerKw");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "minimumContinuousFlowM3s", "turndownFlowM3s", "minimumNpshMarginM",
          "driverMarginFraction" };
    }
  }

  /** Heater/exchanger thermal, pressure-drop, utility and tube-rupture screening. */
  public static final class HeatExchanger extends FamilyModule {
    public HeatExchanger() {
      super("heat-exchanger-preliminary-thermal-design", "dutyKw", "overallU_WPerM2K", "correctedLmtdK");
    }

    @Override
    Result evaluate(Input input) {
      double duty = Math.abs(input.require("dutyKw"));
      double area = duty * 1000.0 / input.require("overallU_WPerM2K") / input.require("correctedLmtdK")
          * (1.0 + input.value("areaMarginFraction", 0.15));
      double utilityFlow = duty / Math.max(input.value("utilitySpecificDutyKJPerKg", 100.0), 1.0e-12);
      Map<String, Double> values = values("preliminaryAreaM2", area, "utilityFlowKgPerS", utilityFlow,
          "processPressureDropBar", input.value("processPressureDropBar", 0.0), "tubeRuptureDesignPressureBara",
          Math.max(input.value("highPressureSideBara", 0.0), input.value("lowPressureSideDesignBara", 0.0)),
          "controlTurndownRatio",
          input.value("maximumDutyKw", duty) / Math.max(input.value("minimumDutyKw", duty), 1.0e-12));
      Map<String, Boolean> constraints = constraints("pressureDrop",
          input.value("processPressureDropBar", 0.0) <= input.value("maximumPressureDropBar", Double.MAX_VALUE),
          "utilityCapacity", utilityFlow <= input.value("availableUtilityFlowKgPerS", Double.MAX_VALUE));
      return new Result(input, "HEAT_EXCHANGER_OR_HEATER", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("preliminaryAreaM2");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "areaMarginFraction", "utilitySpecificDutyKJPerKg", "processPressureDropBar",
          "maximumPressureDropBar", "availableUtilityFlowKgPerS", "highPressureSideBara", "lowPressureSideDesignBara",
          "maximumDutyKw", "minimumDutyKw" };
    }
  }

  /** Column/absorber flooding, turndown and preliminary diameter screening. */
  public static final class Column extends FamilyModule {
    public Column() {
      super("column-absorber-hydraulic-design", "operatingVaporLoadM3s", "floodingVelocityMPerS");
    }

    @Override
    Result evaluate(Input input) {
      double load = input.require("operatingVaporLoadM3s");
      double floodVelocity = input.require("floodingVelocityMPerS");
      double targetFlooding = input.value("targetFloodingFraction", 0.80);
      double diameter = Math.sqrt(4.0 * load / Math.max(Math.PI * floodVelocity * targetFlooding, 1.0e-12));
      double turndown = input.value("minimumVaporLoadM3s", load) / Math.max(load, 1.0e-12);
      Map<String, Double> values = values("preliminaryDiameterM", diameter, "designFloodingPercent",
          targetFlooding * 100.0, "turndownPercent", turndown * 100.0, "condenserEnvelopeKw",
          input.value("maximumCondenserDutyKw", 0.0), "reboilerEnvelopeKw", input.value("maximumReboilerDutyKw", 0.0));
      Map<String, Boolean> constraints = constraints("flooding", targetFlooding <= 1.0, "turndown",
          turndown >= input.value("minimumTurndownFraction", 0.30));
      return new Result(input, "COLUMN_OR_ABSORBER", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("preliminaryDiameterM");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "targetFloodingFraction", "minimumVaporLoadM3s", "minimumTurndownFraction",
          "maximumCondenserDutyKw", "maximumReboilerDutyKw" };
    }
  }

  /** Tank/vessel working, surge, emergency, venting and blanketing screening. */
  public static final class Tank extends FamilyModule {
    public Tank() {
      super("tank-vessel-inventory-design", "normalInflowM3s", "workingTimeS", "emergencyTimeS");
    }

    @Override
    Result evaluate(Input input) {
      double flow = input.require("normalInflowM3s");
      double working = flow * input.require("workingTimeS");
      double emergency = input.value("emergencyInflowM3s", flow) * input.require("emergencyTimeS");
      double usable = input.value("usableVolumeFraction", 0.70);
      double total = (working + emergency) / Math.max(usable, 1.0e-12);
      double diameter = Math.cbrt(4.0 * total / Math.PI);
      Map<String, Double> values = values("workingVolumeM3", working, "emergencyVolumeM3", emergency,
          "preliminaryTotalVolumeM3", total, "preliminaryDiameterM", diameter, "preliminaryHeightM", diameter,
          "normalVentRateM3s", input.value("normalVentRateM3s", flow), "blanketingRateM3s",
          input.value("blanketingRateM3s", flow));
      Map<String, Boolean> constraints = constraints("usableVolumeFraction", usable > 0.0 && usable < 1.0,
          "ventCapacity",
          input.value("installedVentCapacityM3s", Double.MAX_VALUE) >= input.value("normalVentRateM3s", flow));
      return new Result(input, "TANK_OR_VESSEL", values, constraints);
    }

    @Override
    double uncertaintyBasis(Result result) {
      return result.requireValue("preliminaryTotalVolumeM3");
    }

    @Override
    String[] productionRequiredInputs() {
      return new String[] { "emergencyInflowM3s", "usableVolumeFraction", "normalVentRateM3s",
          "installedVentCapacityM3s", "blanketingRateM3s" };
    }
  }

  private static Map<String, Double> values(Object... pairs) {
    Map<String, Double> result = new LinkedHashMap<String, Double>();
    for (int i = 0; i < pairs.length; i += 2) {
      result.put((String) pairs[i], Double.valueOf(((Number) pairs[i + 1]).doubleValue()));
    }
    return result;
  }

  private static boolean productionQualification(EngineeringCalculationContext context) {
    return context != null && "true".equalsIgnoreCase(context.getAttributes().get("productionQualification"));
  }

  private static Map<String, Boolean> constraints(Object... pairs) {
    Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
    for (int i = 0; i < pairs.length; i += 2) {
      result.put((String) pairs[i], Boolean.valueOf(((Boolean) pairs[i + 1]).booleanValue()));
    }
    return result;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
