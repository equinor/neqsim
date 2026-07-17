package neqsim.process.engineering.safety;

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

/** Governed scenario engine for relief, blowdown, flare and dynamic-safety verification inputs. */
public final class SafetyScenarioEngineCalculation implements
    EngineeringCalculationModule<SafetyScenarioEngineCalculation.Input, SafetyScenarioEngineCalculation.Result> {

  public enum Type {
    BLOCKED_OUTLET, CONTROL_VALVE_FAILURE, UTILITY_OR_COOLING_FAILURE, COMPRESSOR_TRIP_SETTLE_OUT, FIRE_EXPOSURE,
    TUBE_RUPTURE, THERMAL_EXPANSION, GAS_BLOW_BY, ABNORMAL_HEAT_INPUT, CHECK_VALVE_FAILURE, SIMULTANEOUS_BLOWDOWN,
    LOSS_OF_CONTAINMENT
  }

  public enum Credibility {
    CREDIBLE, NOT_CREDIBLE, HAZOP_DECISION_REQUIRED
  }

  public enum FluidModel {
    GAS, LIQUID, STEAM, TWO_PHASE
  }

  /** One controlled scenario definition. */
  public static final class Scenario implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String id;
    private final String protectedEquipmentTag;
    private final Type type;
    private final Credibility credibility;
    private final FluidModel fluidModel;
    private final String concurrencyGroup;
    private final String hazardReviewReference;
    private final double relievingMassFlowKgS;
    private final double setPressureBarg;
    private final double requiredAreaIn2;
    private final double inletLossPercent;
    private final double builtUpBackPressurePercent;
    private final double superimposedBackPressureBarg;
    private final double blowdownMinimumTemperatureC;

    public Scenario(String id, String protectedEquipmentTag, Type type, Credibility credibility, FluidModel fluidModel,
        String concurrencyGroup, String hazardReviewReference, double relievingMassFlowKgS, double setPressureBarg,
        double requiredAreaIn2, double inletLossPercent, double builtUpBackPressurePercent,
        double superimposedBackPressureBarg, double blowdownMinimumTemperatureC) {
      this.id = text(id, "id");
      this.protectedEquipmentTag = text(protectedEquipmentTag, "protectedEquipmentTag");
      if (type == null || credibility == null || fluidModel == null) {
        throw new IllegalArgumentException("type, credibility and fluidModel must not be null");
      }
      this.type = type;
      this.credibility = credibility;
      this.fluidModel = fluidModel;
      this.concurrencyGroup = text(concurrencyGroup, "concurrencyGroup");
      this.hazardReviewReference = hazardReviewReference == null ? "" : hazardReviewReference.trim();
      this.relievingMassFlowKgS = nonNegative(relievingMassFlowKgS, "relievingMassFlowKgS");
      this.setPressureBarg = positive(setPressureBarg, "setPressureBarg");
      this.requiredAreaIn2 = positive(requiredAreaIn2, "requiredAreaIn2");
      this.inletLossPercent = nonNegative(inletLossPercent, "inletLossPercent");
      this.builtUpBackPressurePercent = nonNegative(builtUpBackPressurePercent, "builtUpBackPressurePercent");
      this.superimposedBackPressureBarg = nonNegative(superimposedBackPressureBarg, "superimposedBackPressureBarg");
      this.blowdownMinimumTemperatureC = blowdownMinimumTemperatureC;
    }
  }

  /** Scenario set plus API orifice and disposal-system design limits. */
  public static final class Input implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String studyId;
    private final List<Scenario> scenarios;
    private final double[] apiOrificeAreasIn2;
    private final double maximumInletLossPercent;
    private final double maximumBuiltUpBackPressurePercent;
    private final double knockoutDrumResidenceTimeSeconds;
    private final double allowableMaterialTemperatureC;

    public Input(String studyId, List<Scenario> scenarios, double[] apiOrificeAreasIn2, double maximumInletLossPercent,
        double maximumBuiltUpBackPressurePercent, double knockoutDrumResidenceTimeSeconds,
        double allowableMaterialTemperatureC) {
      this.studyId = text(studyId, "studyId");
      this.scenarios = scenarios == null ? Collections.<Scenario>emptyList()
          : Collections.unmodifiableList(new ArrayList<Scenario>(scenarios));
      this.apiOrificeAreasIn2 = apiOrificeAreasIn2 == null ? new double[0] : apiOrificeAreasIn2.clone();
      java.util.Arrays.sort(this.apiOrificeAreasIn2);
      for (double candidate : this.apiOrificeAreasIn2) {
        positive(candidate, "apiOrificeAreasIn2");
      }
      this.maximumInletLossPercent = positive(maximumInletLossPercent, "maximumInletLossPercent");
      this.maximumBuiltUpBackPressurePercent = positive(maximumBuiltUpBackPressurePercent,
          "maximumBuiltUpBackPressurePercent");
      this.knockoutDrumResidenceTimeSeconds = positive(knockoutDrumResidenceTimeSeconds,
          "knockoutDrumResidenceTimeSeconds");
      this.allowableMaterialTemperatureC = allowableMaterialTemperatureC;
    }
  }

  /** Scenario calculations and simultaneous disposal loads. */
  public static final class Result implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final Map<String, Map<String, Object>> scenarioResults;
    private final Map<String, Double> concurrencyGroupLoads;
    private final double knockoutDrumLiquidHoldUpM3;

    Result(Map<String, Map<String, Object>> scenarioResults, Map<String, Double> concurrencyGroupLoads,
        double knockoutDrumLiquidHoldUpM3) {
      this.scenarioResults = Collections
          .unmodifiableMap(new LinkedHashMap<String, Map<String, Object>>(scenarioResults));
      this.concurrencyGroupLoads = Collections
          .unmodifiableMap(new LinkedHashMap<String, Double>(concurrencyGroupLoads));
      this.knockoutDrumLiquidHoldUpM3 = knockoutDrumLiquidHoldUpM3;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<String, Object>();
      result.put("scenarios", scenarioResults);
      result.put("concurrencyGroupLoadsKgS", concurrencyGroupLoads);
      result.put("knockoutDrumLiquidHoldUpM3", Double.valueOf(knockoutDrumLiquidHoldUpM3));
      result.put("flareRadiationInterface", "REQUIRED");
      result.put("dispersionInterface", "REQUIRED");
      result.put("flareNoiseInterface", "REQUIRED");
      result.put("scenarioCredibilityControlledByHazop", Boolean.TRUE);
      result.put("approvalStatus", "REVIEW_REQUIRED");
      return result;
    }
  }

  @Override
  public String getMethod() {
    return "governed-relief-blowdown-flare-scenario-engine";
  }

  @Override
  public String getMethodVersion() {
    return "1.0";
  }

  @Override
  public CalculationReadiness assess(Input input, EngineeringCalculationContext context) {
    CalculationReadiness.Builder readiness = CalculationReadiness.builder();
    if (input == null) {
      return readiness
          .addBlocker("SAFETY_SCENARIO_INPUT", "Safety-scenario study input is required", "Define controlled scenarios")
          .build();
    }
    if (input.scenarios.isEmpty()) {
      readiness.addBlocker("SAFETY_SCENARIOS", "At least one scenario is required", "Complete HAZOP scenario list");
    }
    if (input.apiOrificeAreasIn2.length == 0) {
      readiness.addBlocker("API_ORIFICES", "API orifice candidates are required", "Supply project orifice table");
    }
    for (Scenario scenario : input.scenarios) {
      if (scenario.credibility == Credibility.HAZOP_DECISION_REQUIRED || scenario.hazardReviewReference.isEmpty()) {
        readiness.addBlocker("SCENARIO_CREDIBILITY_" + scenario.id,
            "Scenario credibility or hazard-review evidence is incomplete for " + scenario.id,
            "Record the controlled HAZOP decision and reference");
      }
    }
    return readiness.build();
  }

  @Override
  public EngineeringCalculationResult<Result> calculate(Input input, EngineeringCalculationContext context) {
    CalculationReadiness readiness = assess(input, context);
    EngineeringCalculationResult.Builder<Result> result = EngineeringCalculationResult
        .<Result>builder("safety-scenarios:" + (input == null ? "unassigned" : input.studyId), getMethod(),
            getMethodVersion())
        .context(context).readiness(readiness);
    if (!readiness.isReady()) {
      return result.status(EngineeringCalculationResult.Status.BLOCKED)
          .message("HAZOP-controlled credibility is required before consequence calculation").build();
    }
    Map<String, Map<String, Object>> scenarios = new LinkedHashMap<String, Map<String, Object>>();
    Map<String, Double> groups = new LinkedHashMap<String, Double>();
    double liquidLoad = 0.0;
    for (Scenario scenario : input.scenarios) {
      if (scenario.credibility != Credibility.CREDIBLE) {
        continue;
      }
      double selectedArea = selectOrifice(scenario.requiredAreaIn2, input.apiOrificeAreasIn2);
      boolean inletLoss = scenario.inletLossPercent <= input.maximumInletLossPercent;
      boolean backPressure = scenario.builtUpBackPressurePercent <= input.maximumBuiltUpBackPressurePercent;
      boolean temperature = scenario.blowdownMinimumTemperatureC >= input.allowableMaterialTemperatureC;
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("scenarioId", scenario.id);
      row.put("protectedEquipmentTag", scenario.protectedEquipmentTag);
      row.put("type", scenario.type.name());
      row.put("fluidModel", scenario.fluidModel.name());
      row.put("hazardReviewReference", scenario.hazardReviewReference);
      row.put("requiredAreaIn2", Double.valueOf(scenario.requiredAreaIn2));
      row.put("selectedApiOrificeAreaIn2", Double.valueOf(selectedArea));
      row.put("inletLossSatisfied", Boolean.valueOf(inletLoss));
      row.put("builtUpBackPressureSatisfied", Boolean.valueOf(backPressure));
      row.put("superimposedBackPressureBarg", Double.valueOf(scenario.superimposedBackPressureBarg));
      row.put("psvStabilityReviewRequired", Boolean.TRUE);
      row.put("minimumTemperatureSatisfied", Boolean.valueOf(temperature));
      row.put("concurrencyGroup", scenario.concurrencyGroup);
      row.put("status", inletLoss && backPressure && temperature ? "CALCULATED_REVIEW_REQUIRED" : "CONSTRAINT_FAILED");
      scenarios.put(scenario.id, row);
      Double existing = groups.get(scenario.concurrencyGroup);
      groups.put(scenario.concurrencyGroup,
          Double.valueOf((existing == null ? 0.0 : existing.doubleValue()) + scenario.relievingMassFlowKgS));
      if (scenario.fluidModel == FluidModel.LIQUID || scenario.fluidModel == FluidModel.TWO_PHASE) {
        liquidLoad += scenario.relievingMassFlowKgS / 800.0;
      }
    }
    Result value = new Result(scenarios, groups, liquidLoad * input.knockoutDrumResidenceTimeSeconds);
    return result.status(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED).value(value)
        .input("standards", "API 520/API 521 project editions")
        .warning("Two-phase method, PSV stability and flare-network interaction require specialist verification")
        .build();
  }

  private static double selectOrifice(double required, double[] candidates) {
    for (double candidate : candidates) {
      if (candidate >= required) {
        return candidate;
      }
    }
    throw new IllegalStateException("No API orifice candidate satisfies " + required + " in2");
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
}
