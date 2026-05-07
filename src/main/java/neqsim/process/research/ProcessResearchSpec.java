package neqsim.process.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input specification for a process research run.
 *
 * <p>
 * The specification describes feed composition, desired products, allowed unit operations, reaction
 * options, candidate-count limits, and optimization variables. It is intentionally declarative so
 * callers can generate the same process candidates from Java, Python, notebooks, or web APIs.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessResearchSpec {
  /** Objective used by the default candidate ranker. */
  public enum Objective {
    /** Maximize weighted product flow and purity. */
    MAXIMIZE_PRODUCT,
    /** Minimize energy use; currently applied as a secondary penalty. */
    MINIMIZE_ENERGY,
    /** Maximize the combined weighted score. */
    MAXIMIZE_SCORE
  }

  private String name = "process research";
  private String fluidModel = "SRK";
  private String mixingRule = "classic";
  private String feedMaterialName = "feed";
  private double feedTemperatureK = 298.15;
  private double feedPressureBara = 50.0;
  private double feedFlowRate = 10000.0;
  private String feedFlowUnit = "kg/hr";
  private int maxCandidates = 20;
  private int maxOptimizationCases = 81;
  private int maxSynthesisDepth = 4;
  private boolean evaluateCandidates = true;
  private boolean enableFeasibilityPruning = true;
  private boolean includeHeatIntegration = false;
  private boolean includeCostEstimate = false;
  private boolean includeEmissionEstimate = false;
  private double heatIntegrationDeltaTMinC = 10.0;
  private Objective objective = Objective.MAXIMIZE_PRODUCT;
  private ScoringWeights scoringWeights = new ScoringWeights();
  private EconomicAssumptions economicAssumptions = new EconomicAssumptions();
  private final Map<String, Double> feedComponents = new LinkedHashMap<>();
  private final List<MaterialNode> materialNodes = new ArrayList<>();
  private final List<ProductTarget> productTargets = new ArrayList<>();
  private final List<String> allowedUnitTypes = new ArrayList<>();
  private final List<OperationOption> operationOptions = new ArrayList<>();
  private final List<ReactionOption> reactionOptions = new ArrayList<>();
  private final List<DecisionVariable> decisionVariables = new ArrayList<>();
  private final List<RobustnessScenario> robustnessScenarios = new ArrayList<>();

  /**
   * Creates an empty process research specification.
   */
  public ProcessResearchSpec() {}

  /**
   * Creates a new builder.
   *
   * @return builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the study name.
   *
   * @return study name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the fluid model.
   *
   * @return fluid model name, e.g. SRK or PR
   */
  public String getFluidModel() {
    return fluidModel;
  }

  /**
   * Gets the mixing rule.
   *
   * @return mixing rule name
   */
  public String getMixingRule() {
    return mixingRule;
  }

  /**
   * Gets the material name used as the synthesis-graph feed node.
   *
   * @return feed material name
   */
  public String getFeedMaterialName() {
    return feedMaterialName;
  }

  /**
   * Gets the feed temperature.
   *
   * @return feed temperature in Kelvin
   */
  public double getFeedTemperatureK() {
    return feedTemperatureK;
  }

  /**
   * Gets the feed pressure.
   *
   * @return feed pressure in bara
   */
  public double getFeedPressureBara() {
    return feedPressureBara;
  }

  /**
   * Gets the feed flow rate.
   *
   * @return feed flow rate
   */
  public double getFeedFlowRate() {
    return feedFlowRate;
  }

  /**
   * Gets the feed flow unit.
   *
   * @return feed flow unit
   */
  public String getFeedFlowUnit() {
    return feedFlowUnit;
  }

  /**
   * Gets the maximum number of candidates to keep.
   *
   * @return maximum candidate count
   */
  public int getMaxCandidates() {
    return maxCandidates;
  }

  /**
   * Gets the maximum number of grid optimization cases per candidate.
   *
   * @return maximum optimization cases
   */
  public int getMaxOptimizationCases() {
    return maxOptimizationCases;
  }

  /**
   * Gets the maximum operation path depth for graph-based synthesis.
   *
   * @return maximum synthesis depth
   */
  public int getMaxSynthesisDepth() {
    return maxSynthesisDepth;
  }

  /**
   * Returns whether generated candidates should be evaluated.
   *
   * @return true if candidates should be simulated and ranked
   */
  public boolean isEvaluateCandidates() {
    return evaluateCandidates;
  }

  /**
   * Returns whether fast feasibility pruning is enabled.
   *
   * @return true if pre-simulation pruning is enabled
   */
  public boolean isFeasibilityPruningEnabled() {
    return enableFeasibilityPruning;
  }

  /**
   * Returns whether heat-integration metrics are included.
   *
   * @return true if pinch-analysis metrics are included
   */
  public boolean isIncludeHeatIntegration() {
    return includeHeatIntegration;
  }

  /**
   * Returns whether cost-estimate metrics are included.
   *
   * @return true if cost metrics are included
   */
  public boolean isIncludeCostEstimate() {
    return includeCostEstimate;
  }

  /**
   * Returns whether emissions metrics are included.
   *
   * @return true if emissions metrics are included
   */
  public boolean isIncludeEmissionEstimate() {
    return includeEmissionEstimate;
  }

  /**
   * Gets the pinch-analysis minimum approach temperature.
   *
   * @return minimum approach temperature in Celsius
   */
  public double getHeatIntegrationDeltaTMinC() {
    return heatIntegrationDeltaTMinC;
  }

  /**
   * Gets the objective.
   *
   * @return objective enum
   */
  public Objective getObjective() {
    return objective;
  }

  /**
   * Gets scoring weights.
   *
   * @return scoring weights
   */
  public ScoringWeights getScoringWeights() {
    return scoringWeights;
  }

  /**
   * Gets economic and emissions assumptions.
   *
   * @return economic assumptions
   */
  public EconomicAssumptions getEconomicAssumptions() {
    return economicAssumptions;
  }

  /**
   * Gets feed components.
   *
   * @return unmodifiable component mole-fraction map
   */
  public Map<String, Double> getFeedComponents() {
    return Collections.unmodifiableMap(feedComponents);
  }

  /**
   * Gets material nodes used by graph-based synthesis.
   *
   * @return unmodifiable material node list
   */
  public List<MaterialNode> getMaterialNodes() {
    return Collections.unmodifiableList(materialNodes);
  }

  /**
   * Gets product targets.
   *
   * @return unmodifiable product target list
   */
  public List<ProductTarget> getProductTargets() {
    return Collections.unmodifiableList(productTargets);
  }

  /**
   * Gets allowed unit types.
   *
   * @return unmodifiable allowed unit type list
   */
  public List<String> getAllowedUnitTypes() {
    return Collections.unmodifiableList(allowedUnitTypes);
  }

  /**
   * Gets operation options.
   *
   * @return unmodifiable operation option list
   */
  public List<OperationOption> getOperationOptions() {
    return Collections.unmodifiableList(operationOptions);
  }

  /**
   * Gets reaction options.
   *
   * @return unmodifiable reaction option list
   */
  public List<ReactionOption> getReactionOptions() {
    return Collections.unmodifiableList(reactionOptions);
  }

  /**
   * Gets decision variables.
   *
   * @return unmodifiable decision variable list
   */
  public List<DecisionVariable> getDecisionVariables() {
    return Collections.unmodifiableList(decisionVariables);
  }

  /**
   * Gets robustness scenarios.
   *
   * @return unmodifiable robustness scenario list
   */
  public List<RobustnessScenario> getRobustnessScenarios() {
    return Collections.unmodifiableList(robustnessScenarios);
  }

  /**
   * Checks whether a unit type is allowed.
   *
   * @param unitType unit type to check
   * @return true if allowed or if no explicit allow-list is set
   */
  public boolean allowsUnitType(String unitType) {
    if (allowedUnitTypes.isEmpty()) {
      return true;
    }
    for (String allowed : allowedUnitTypes) {
      if (allowed.equalsIgnoreCase(unitType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Product target used for candidate scoring.
   */
  public static class ProductTarget {
    private final String name;
    private String componentName;
    private String materialName;
    private String streamRole = "product";
    private String streamReference;
    private double minPurity = 0.0;
    private double minFlowRate = 0.0;
    private double weight = 1.0;

    /**
     * Creates a product target.
     *
     * @param name target name; must be non-empty
     */
    public ProductTarget(String name) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Product target name cannot be empty");
      }
      this.name = name;
    }

    /**
     * Sets the target component.
     *
     * @param componentName component name
     * @return this target
     */
    public ProductTarget setComponentName(String componentName) {
      this.componentName = componentName;
      return this;
    }

    /**
     * Sets the target material name used by graph-based synthesis.
     *
     * @param materialName material node name
     * @return this target
     */
    public ProductTarget setMaterialName(String materialName) {
      this.materialName = materialName;
      return this;
    }

    /**
     * Sets the target stream role.
     *
     * @param streamRole role such as gas, liquid, oil, water, or product
     * @return this target
     */
    public ProductTarget setStreamRole(String streamRole) {
      this.streamRole = streamRole;
      return this;
    }

    /**
     * Sets an explicit stream reference for scoring.
     *
     * @param streamReference dot-notation stream reference
     * @return this target
     */
    public ProductTarget setStreamReference(String streamReference) {
      this.streamReference = streamReference;
      return this;
    }

    /**
     * Sets minimum product purity.
     *
     * @param minPurity minimum mole fraction, 0.0 to 1.0
     * @return this target
     */
    public ProductTarget setMinPurity(double minPurity) {
      this.minPurity = minPurity;
      return this;
    }

    /**
     * Sets minimum product flow.
     *
     * @param minFlowRate minimum product flow in kg/hr
     * @return this target
     */
    public ProductTarget setMinFlowRate(double minFlowRate) {
      this.minFlowRate = minFlowRate;
      return this;
    }

    /**
     * Sets target score weight.
     *
     * @param weight positive weighting factor
     * @return this target
     */
    public ProductTarget setWeight(double weight) {
      this.weight = weight;
      return this;
    }

    /**
     * Gets the target name.
     *
     * @return target name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the component name.
     *
     * @return component name, or null
     */
    public String getComponentName() {
      return componentName;
    }

    /**
     * Gets the target material name used by graph-based synthesis.
     *
     * @return material name, or null
     */
    public String getMaterialName() {
      return materialName;
    }

    /**
     * Gets the stream role.
     *
     * @return stream role
     */
    public String getStreamRole() {
      return streamRole;
    }

    /**
     * Gets the explicit stream reference.
     *
     * @return stream reference, or null
     */
    public String getStreamReference() {
      return streamReference;
    }

    /**
     * Gets the minimum purity.
     *
     * @return minimum purity mole fraction
     */
    public double getMinPurity() {
      return minPurity;
    }

    /**
     * Gets the minimum flow rate.
     *
     * @return minimum flow rate in kg/hr
     */
    public double getMinFlowRate() {
      return minFlowRate;
    }

    /**
     * Gets the target weight.
     *
     * @return target weight
     */
    public double getWeight() {
      return weight;
    }
  }

  /**
   * Continuous decision variable for per-candidate optimization.
   */
  public static class DecisionVariable {
    private final String equipmentName;
    private final String propertyName;
    private final double lowerBound;
    private final double upperBound;
    private final String unit;
    private int gridLevels = 3;

    /**
     * Creates a decision variable.
     *
     * @param equipmentName equipment name in the generated candidate
     * @param propertyName property setter name without the set prefix
     * @param lowerBound lower bound
     * @param upperBound upper bound
     * @param unit unit string accepted by the equipment setter
     */
    public DecisionVariable(String equipmentName, String propertyName, double lowerBound,
        double upperBound, String unit) {
      this.equipmentName = equipmentName;
      this.propertyName = propertyName;
      this.lowerBound = lowerBound;
      this.upperBound = upperBound;
      this.unit = unit;
    }

    /**
     * Sets the number of grid levels for screening.
     *
     * @param gridLevels number of grid levels; values below 2 are treated as 2
     * @return this decision variable
     */
    public DecisionVariable setGridLevels(int gridLevels) {
      this.gridLevels = Math.max(2, gridLevels);
      return this;
    }

    /**
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the property name.
     *
     * @return property name
     */
    public String getPropertyName() {
      return propertyName;
    }

    /**
     * Gets the lower bound.
     *
     * @return lower bound
     */
    public double getLowerBound() {
      return lowerBound;
    }

    /**
     * Gets the upper bound.
     *
     * @return upper bound
     */
    public double getUpperBound() {
      return upperBound;
    }

    /**
     * Gets the unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the grid level count.
     *
     * @return grid levels
     */
    public int getGridLevels() {
      return gridLevels;
    }
  }

  /**
   * Weights used to collapse process synthesis metrics into a ranking score.
   */
  public static class ScoringWeights {
    private double productFlowWeight = 1.0;
    private double purityWeight = 1000.0;
    private double electricPowerPenalty = 0.0;
    private double hotUtilityPenalty = 0.0;
    private double coldUtilityPenalty = 0.0;
    private double capitalCostPenalty = 0.0;
    private double emissionsPenalty = 0.0;
    private double complexityPenalty = 0.0;
    private double robustnessWeight = 0.0;

    /**
     * Creates scoring weights with backwards-compatible product-focused defaults.
     */
    public ScoringWeights() {}

    /**
     * Sets the product flow reward weight.
     *
     * @param productFlowWeight score per kg/hr of weighted product flow
     * @return this scoring weights object
     */
    public ScoringWeights setProductFlowWeight(double productFlowWeight) {
      this.productFlowWeight = productFlowWeight;
      return this;
    }

    /**
     * Sets the purity reward weight.
     *
     * @param purityWeight score per mole-fraction purity unit
     * @return this scoring weights object
     */
    public ScoringWeights setPurityWeight(double purityWeight) {
      this.purityWeight = purityWeight;
      return this;
    }

    /**
     * Sets the electric power penalty.
     *
     * @param electricPowerPenalty score penalty per kW electric power demand
     * @return this scoring weights object
     */
    public ScoringWeights setElectricPowerPenalty(double electricPowerPenalty) {
      this.electricPowerPenalty = electricPowerPenalty;
      return this;
    }

    /**
     * Sets the hot utility penalty.
     *
     * @param hotUtilityPenalty score penalty per kW hot utility demand
     * @return this scoring weights object
     */
    public ScoringWeights setHotUtilityPenalty(double hotUtilityPenalty) {
      this.hotUtilityPenalty = hotUtilityPenalty;
      return this;
    }

    /**
     * Sets the cold utility penalty.
     *
     * @param coldUtilityPenalty score penalty per kW cold utility demand
     * @return this scoring weights object
     */
    public ScoringWeights setColdUtilityPenalty(double coldUtilityPenalty) {
      this.coldUtilityPenalty = coldUtilityPenalty;
      return this;
    }

    /**
     * Sets the capital cost penalty.
     *
     * @param capitalCostPenalty score penalty per USD of capital cost proxy
     * @return this scoring weights object
     */
    public ScoringWeights setCapitalCostPenalty(double capitalCostPenalty) {
      this.capitalCostPenalty = capitalCostPenalty;
      return this;
    }

    /**
     * Sets the emissions penalty.
     *
     * @param emissionsPenalty score penalty per kg CO2-equivalent per hour
     * @return this scoring weights object
     */
    public ScoringWeights setEmissionsPenalty(double emissionsPenalty) {
      this.emissionsPenalty = emissionsPenalty;
      return this;
    }

    /**
     * Sets the complexity penalty.
     *
     * @param complexityPenalty score penalty per equipment item
     * @return this scoring weights object
     */
    public ScoringWeights setComplexityPenalty(double complexityPenalty) {
      this.complexityPenalty = complexityPenalty;
      return this;
    }

    /**
     * Sets the robustness weight.
     *
     * @param robustnessWeight score weight for worst-case robustness delta
     * @return this scoring weights object
     */
    public ScoringWeights setRobustnessWeight(double robustnessWeight) {
      this.robustnessWeight = robustnessWeight;
      return this;
    }

    /**
     * Gets the product flow reward weight.
     *
     * @return product flow weight
     */
    public double getProductFlowWeight() {
      return productFlowWeight;
    }

    /**
     * Gets the purity reward weight.
     *
     * @return purity weight
     */
    public double getPurityWeight() {
      return purityWeight;
    }

    /**
     * Gets the electric power penalty.
     *
     * @return electric power penalty
     */
    public double getElectricPowerPenalty() {
      return electricPowerPenalty;
    }

    /**
     * Gets the hot utility penalty.
     *
     * @return hot utility penalty
     */
    public double getHotUtilityPenalty() {
      return hotUtilityPenalty;
    }

    /**
     * Gets the cold utility penalty.
     *
     * @return cold utility penalty
     */
    public double getColdUtilityPenalty() {
      return coldUtilityPenalty;
    }

    /**
     * Gets the capital cost penalty.
     *
     * @return capital cost penalty
     */
    public double getCapitalCostPenalty() {
      return capitalCostPenalty;
    }

    /**
     * Gets the emissions penalty.
     *
     * @return emissions penalty
     */
    public double getEmissionsPenalty() {
      return emissionsPenalty;
    }

    /**
     * Gets the complexity penalty.
     *
     * @return complexity penalty
     */
    public double getComplexityPenalty() {
      return complexityPenalty;
    }

    /**
     * Gets the robustness weight.
     *
     * @return robustness weight
     */
    public double getRobustnessWeight() {
      return robustnessWeight;
    }
  }

  /**
   * Economic and emissions assumptions for synthesis ranking.
   */
  public static class EconomicAssumptions {
    private double operatingHoursPerYear = 8000.0;
    private double electricityCostUsdPerKWh = 0.08;
    private double hotUtilityCostUsdPerKWh = 0.03;
    private double coldUtilityCostUsdPerKWh = 0.01;
    private double electricityEmissionFactorKgCO2PerKWh = 0.35;
    private double carbonPriceUsdPerTonne = 0.0;
    private double defaultEquipmentCostUsd = 250000.0;
    private final Map<String, Double> equipmentCostProxyUsd = new LinkedHashMap<String, Double>();

    /**
     * Creates economic assumptions with generic screening values.
     */
    public EconomicAssumptions() {
      equipmentCostProxyUsd.put("Stream", 0.0);
      equipmentCostProxyUsd.put("Separator", 500000.0);
      equipmentCostProxyUsd.put("Compressor", 1500000.0);
      equipmentCostProxyUsd.put("Pump", 250000.0);
      equipmentCostProxyUsd.put("Heater", 350000.0);
      equipmentCostProxyUsd.put("Cooler", 350000.0);
      equipmentCostProxyUsd.put("HeatExchanger", 450000.0);
      equipmentCostProxyUsd.put("GibbsReactor", 1000000.0);
      equipmentCostProxyUsd.put("PlugFlowReactor", 1200000.0);
      equipmentCostProxyUsd.put("StirredTankReactor", 900000.0);
    }

    /**
     * Sets operating hours per year.
     *
     * @param operatingHoursPerYear annual operating hours
     * @return this assumptions object
     */
    public EconomicAssumptions setOperatingHoursPerYear(double operatingHoursPerYear) {
      this.operatingHoursPerYear = operatingHoursPerYear;
      return this;
    }

    /**
     * Sets electricity cost.
     *
     * @param electricityCostUsdPerKWh electricity cost in USD/kWh
     * @return this assumptions object
     */
    public EconomicAssumptions setElectricityCostUsdPerKWh(double electricityCostUsdPerKWh) {
      this.electricityCostUsdPerKWh = electricityCostUsdPerKWh;
      return this;
    }

    /**
     * Sets hot utility cost.
     *
     * @param hotUtilityCostUsdPerKWh hot utility cost in USD/kWh
     * @return this assumptions object
     */
    public EconomicAssumptions setHotUtilityCostUsdPerKWh(double hotUtilityCostUsdPerKWh) {
      this.hotUtilityCostUsdPerKWh = hotUtilityCostUsdPerKWh;
      return this;
    }

    /**
     * Sets cold utility cost.
     *
     * @param coldUtilityCostUsdPerKWh cold utility cost in USD/kWh
     * @return this assumptions object
     */
    public EconomicAssumptions setColdUtilityCostUsdPerKWh(double coldUtilityCostUsdPerKWh) {
      this.coldUtilityCostUsdPerKWh = coldUtilityCostUsdPerKWh;
      return this;
    }

    /**
     * Sets electricity emissions factor.
     *
     * @param electricityEmissionFactorKgCO2PerKWh kg CO2-equivalent per kWh
     * @return this assumptions object
     */
    public EconomicAssumptions setElectricityEmissionFactorKgCO2PerKWh(
        double electricityEmissionFactorKgCO2PerKWh) {
      this.electricityEmissionFactorKgCO2PerKWh = electricityEmissionFactorKgCO2PerKWh;
      return this;
    }

    /**
     * Sets carbon price.
     *
     * @param carbonPriceUsdPerTonne carbon price in USD per tonne CO2-equivalent
     * @return this assumptions object
     */
    public EconomicAssumptions setCarbonPriceUsdPerTonne(double carbonPriceUsdPerTonne) {
      this.carbonPriceUsdPerTonne = carbonPriceUsdPerTonne;
      return this;
    }

    /**
     * Sets the default equipment cost proxy.
     *
     * @param defaultEquipmentCostUsd default equipment cost in USD
     * @return this assumptions object
     */
    public EconomicAssumptions setDefaultEquipmentCostUsd(double defaultEquipmentCostUsd) {
      this.defaultEquipmentCostUsd = defaultEquipmentCostUsd;
      return this;
    }

    /**
     * Sets an equipment type cost proxy.
     *
     * @param equipmentType equipment type name
     * @param costUsd cost proxy in USD
     * @return this assumptions object
     */
    public EconomicAssumptions setEquipmentCostProxyUsd(String equipmentType, double costUsd) {
      equipmentCostProxyUsd.put(equipmentType, costUsd);
      return this;
    }

    /**
     * Gets operating hours per year.
     *
     * @return annual operating hours
     */
    public double getOperatingHoursPerYear() {
      return operatingHoursPerYear;
    }

    /**
     * Gets electricity cost.
     *
     * @return electricity cost in USD/kWh
     */
    public double getElectricityCostUsdPerKWh() {
      return electricityCostUsdPerKWh;
    }

    /**
     * Gets hot utility cost.
     *
     * @return hot utility cost in USD/kWh
     */
    public double getHotUtilityCostUsdPerKWh() {
      return hotUtilityCostUsdPerKWh;
    }

    /**
     * Gets cold utility cost.
     *
     * @return cold utility cost in USD/kWh
     */
    public double getColdUtilityCostUsdPerKWh() {
      return coldUtilityCostUsdPerKWh;
    }

    /**
     * Gets electricity emissions factor.
     *
     * @return kg CO2-equivalent per kWh
     */
    public double getElectricityEmissionFactorKgCO2PerKWh() {
      return electricityEmissionFactorKgCO2PerKWh;
    }

    /**
     * Gets carbon price.
     *
     * @return carbon price in USD per tonne CO2-equivalent
     */
    public double getCarbonPriceUsdPerTonne() {
      return carbonPriceUsdPerTonne;
    }

    /**
     * Gets the cost proxy for an equipment type.
     *
     * @param equipmentType equipment type name
     * @return equipment cost proxy in USD
     */
    public double getEquipmentCostProxyUsd(String equipmentType) {
      Double value = equipmentCostProxyUsd.get(equipmentType);
      return value == null ? defaultEquipmentCostUsd : value.doubleValue();
    }
  }

  /**
   * Robustness scenario for feed-condition sensitivity checks.
   */
  public static class RobustnessScenario {
    private final String name;
    private double feedFlowMultiplier = 1.0;
    private double feedTemperatureOffsetK = 0.0;
    private double feedPressureMultiplier = 1.0;

    /**
     * Creates a robustness scenario.
     *
     * @param name scenario name
     */
    public RobustnessScenario(String name) {
      this.name = name;
    }

    /**
     * Sets the feed flow multiplier.
     *
     * @param feedFlowMultiplier feed flow multiplier
     * @return this scenario
     */
    public RobustnessScenario setFeedFlowMultiplier(double feedFlowMultiplier) {
      this.feedFlowMultiplier = feedFlowMultiplier;
      return this;
    }

    /**
     * Sets the feed temperature offset.
     *
     * @param feedTemperatureOffsetK temperature offset in Kelvin
     * @return this scenario
     */
    public RobustnessScenario setFeedTemperatureOffsetK(double feedTemperatureOffsetK) {
      this.feedTemperatureOffsetK = feedTemperatureOffsetK;
      return this;
    }

    /**
     * Sets the feed pressure multiplier.
     *
     * @param feedPressureMultiplier pressure multiplier
     * @return this scenario
     */
    public RobustnessScenario setFeedPressureMultiplier(double feedPressureMultiplier) {
      this.feedPressureMultiplier = feedPressureMultiplier;
      return this;
    }

    /**
     * Gets the scenario name.
     *
     * @return scenario name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the feed flow multiplier.
     *
     * @return feed flow multiplier
     */
    public double getFeedFlowMultiplier() {
      return feedFlowMultiplier;
    }

    /**
     * Gets the feed temperature offset.
     *
     * @return temperature offset in Kelvin
     */
    public double getFeedTemperatureOffsetK() {
      return feedTemperatureOffsetK;
    }

    /**
     * Gets the feed pressure multiplier.
     *
     * @return feed pressure multiplier
     */
    public double getFeedPressureMultiplier() {
      return feedPressureMultiplier;
    }
  }

  /**
   * Builder for {@link ProcessResearchSpec}.
   */
  public static class Builder {
    private final ProcessResearchSpec spec = new ProcessResearchSpec();

    /**
     * Sets the study name.
     *
     * @param name study name
     * @return this builder
     */
    public Builder setName(String name) {
      spec.name = name;
      return this;
    }

    /**
     * Sets the thermodynamic model.
     *
     * @param fluidModel model name, e.g. SRK or PR
     * @return this builder
     */
    public Builder setFluidModel(String fluidModel) {
      spec.fluidModel = fluidModel;
      return this;
    }

    /**
     * Sets the mixing rule.
     *
     * @param mixingRule mixing rule name
     * @return this builder
     */
    public Builder setMixingRule(String mixingRule) {
      spec.mixingRule = mixingRule;
      return this;
    }

    /**
     * Sets the feed material name used by graph-based synthesis.
     *
     * @param feedMaterialName feed material node name
     * @return this builder
     */
    public Builder setFeedMaterialName(String feedMaterialName) {
      spec.feedMaterialName = feedMaterialName;
      return this;
    }

    /**
     * Sets feed temperature.
     *
     * @param temperatureK feed temperature in Kelvin
     * @return this builder
     */
    public Builder setFeedTemperature(double temperatureK) {
      spec.feedTemperatureK = temperatureK;
      return this;
    }

    /**
     * Sets feed pressure.
     *
     * @param pressureBara feed pressure in bara
     * @return this builder
     */
    public Builder setFeedPressure(double pressureBara) {
      spec.feedPressureBara = pressureBara;
      return this;
    }

    /**
     * Sets feed flow rate.
     *
     * @param flowRate flow rate value
     * @param unit flow rate unit
     * @return this builder
     */
    public Builder setFeedFlowRate(double flowRate, String unit) {
      spec.feedFlowRate = flowRate;
      spec.feedFlowUnit = unit;
      return this;
    }

    /**
     * Adds a feed component.
     *
     * @param componentName component name
     * @param moleFraction mole fraction or relative amount
     * @return this builder
     */
    public Builder addFeedComponent(String componentName, double moleFraction) {
      spec.feedComponents.put(componentName, moleFraction);
      return this;
    }

    /**
     * Adds a material node for graph-based synthesis.
     *
     * @param node material node
     * @return this builder
     */
    public Builder addMaterialNode(MaterialNode node) {
      spec.materialNodes.add(node);
      return this;
    }

    /**
     * Adds a product target.
     *
     * @param target product target
     * @return this builder
     */
    public Builder addProductTarget(ProductTarget target) {
      spec.productTargets.add(target);
      return this;
    }

    /**
     * Adds an allowed unit type.
     *
     * @param unitType unit type
     * @return this builder
     */
    public Builder addAllowedUnitType(String unitType) {
      spec.allowedUnitTypes.add(unitType);
      return this;
    }

    /**
     * Adds a process-network operation option.
     *
     * @param option operation option
     * @return this builder
     */
    public Builder addOperationOption(OperationOption option) {
      spec.operationOptions.add(option);
      return this;
    }

    /**
     * Adds a reaction option.
     *
     * @param option reaction option
     * @return this builder
     */
    public Builder addReactionOption(ReactionOption option) {
      spec.reactionOptions.add(option);
      return this;
    }

    /**
     * Adds a decision variable.
     *
     * @param variable decision variable
     * @return this builder
     */
    public Builder addDecisionVariable(DecisionVariable variable) {
      spec.decisionVariables.add(variable);
      return this;
    }

    /**
     * Adds a robustness scenario.
     *
     * @param scenario robustness scenario
     * @return this builder
     */
    public Builder addRobustnessScenario(RobustnessScenario scenario) {
      spec.robustnessScenarios.add(scenario);
      return this;
    }

    /**
     * Sets the maximum number of candidates.
     *
     * @param maxCandidates maximum candidate count
     * @return this builder
     */
    public Builder setMaxCandidates(int maxCandidates) {
      spec.maxCandidates = Math.max(1, maxCandidates);
      return this;
    }

    /**
     * Sets the maximum number of optimization cases per candidate.
     *
     * @param maxOptimizationCases maximum optimization cases
     * @return this builder
     */
    public Builder setMaxOptimizationCases(int maxOptimizationCases) {
      spec.maxOptimizationCases = Math.max(1, maxOptimizationCases);
      return this;
    }

    /**
     * Sets the maximum graph synthesis depth.
     *
     * @param maxSynthesisDepth maximum number of operation steps per graph path
     * @return this builder
     */
    public Builder setMaxSynthesisDepth(int maxSynthesisDepth) {
      spec.maxSynthesisDepth = Math.max(1, maxSynthesisDepth);
      return this;
    }

    /**
     * Sets whether candidates should be evaluated.
     *
     * @param evaluateCandidates true to run generated process candidates
     * @return this builder
     */
    public Builder setEvaluateCandidates(boolean evaluateCandidates) {
      spec.evaluateCandidates = evaluateCandidates;
      return this;
    }

    /**
     * Sets whether fast feasibility pruning is enabled.
     *
     * @param enableFeasibilityPruning true to enable pre-simulation pruning
     * @return this builder
     */
    public Builder setEnableFeasibilityPruning(boolean enableFeasibilityPruning) {
      spec.enableFeasibilityPruning = enableFeasibilityPruning;
      return this;
    }

    /**
     * Sets whether heat-integration metrics are included.
     *
     * @param includeHeatIntegration true to run pinch analysis after candidate simulation
     * @return this builder
     */
    public Builder setIncludeHeatIntegration(boolean includeHeatIntegration) {
      spec.includeHeatIntegration = includeHeatIntegration;
      return this;
    }

    /**
     * Sets whether cost metrics are included.
     *
     * @param includeCostEstimate true to include cost proxies
     * @return this builder
     */
    public Builder setIncludeCostEstimate(boolean includeCostEstimate) {
      spec.includeCostEstimate = includeCostEstimate;
      return this;
    }

    /**
     * Sets whether emissions metrics are included.
     *
     * @param includeEmissionEstimate true to include emissions estimates
     * @return this builder
     */
    public Builder setIncludeEmissionEstimate(boolean includeEmissionEstimate) {
      spec.includeEmissionEstimate = includeEmissionEstimate;
      return this;
    }

    /**
     * Sets pinch-analysis minimum approach temperature.
     *
     * @param heatIntegrationDeltaTMinC minimum approach temperature in Celsius
     * @return this builder
     */
    public Builder setHeatIntegrationDeltaTMinC(double heatIntegrationDeltaTMinC) {
      spec.heatIntegrationDeltaTMinC = heatIntegrationDeltaTMinC;
      return this;
    }

    /**
     * Sets the objective.
     *
     * @param objective objective enum
     * @return this builder
     */
    public Builder setObjective(Objective objective) {
      spec.objective = objective;
      return this;
    }

    /**
     * Sets scoring weights.
     *
     * @param scoringWeights scoring weights
     * @return this builder
     */
    public Builder setScoringWeights(ScoringWeights scoringWeights) {
      spec.scoringWeights = scoringWeights == null ? new ScoringWeights() : scoringWeights;
      return this;
    }

    /**
     * Sets economic assumptions.
     *
     * @param economicAssumptions economic assumptions
     * @return this builder
     */
    public Builder setEconomicAssumptions(EconomicAssumptions economicAssumptions) {
      spec.economicAssumptions =
          economicAssumptions == null ? new EconomicAssumptions() : economicAssumptions;
      return this;
    }

    /**
     * Builds the specification.
     *
     * @return process research specification
     */
    public ProcessResearchSpec build() {
      if (spec.feedComponents.isEmpty()) {
        throw new IllegalStateException("At least one feed component must be specified");
      }
      if (spec.productTargets.isEmpty()) {
        throw new IllegalStateException("At least one product target must be specified");
      }
      return spec;
    }
  }
}
