package neqsim.process.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.heatintegration.PinchAnalysis;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.process.util.optimizer.ProcessSimulationEvaluator;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Evaluates and scores generated process candidates.
 *
 * <p>
 * The evaluator builds each candidate through {@link ProcessSystem#fromJsonAndRun(String)}, applies
 * optional decision-variable grid screening through {@link ProcessSimulationEvaluator}, and ranks
 * candidates by weighted product flow, purity, and constraint penalties.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessCandidateEvaluator {
  private final Gson gson = new Gson();

  /**
   * Creates a process candidate evaluator.
   */
  public ProcessCandidateEvaluator() {}

  /**
   * Evaluates a candidate and updates it in place.
   *
   * @param candidate candidate to evaluate
   * @param spec process research specification
   */
  public void evaluate(ProcessCandidate candidate, ProcessResearchSpec spec) {
    if (candidate.getJsonDefinition() == null || !candidate.getErrors().isEmpty()) {
      candidate.setFeasible(false);
      candidate.setScore(Double.NEGATIVE_INFINITY);
      return;
    }
    SimulationResult simulationResult = ProcessSystem.fromJsonAndRun(candidate.getJsonDefinition());
    for (String warning : simulationResult.getWarnings()) {
      candidate.addWarning(warning);
    }
    if (simulationResult.isError() || simulationResult.getProcessSystem() == null) {
      for (SimulationResult.ErrorDetail error : simulationResult.getErrors()) {
        candidate.addError(error.toString());
      }
      candidate.setFeasible(false);
      candidate.setScore(Double.NEGATIVE_INFINITY);
      return;
    }

    ProcessSystem process = simulationResult.getProcessSystem();
    candidate.setProcessSystem(process);

    if (spec.getDecisionVariables().isEmpty()) {
      double score = scoreProcess(process, candidate, spec, true);
      candidate.setScore(score);
      candidate.setFeasible(Double.isFinite(score));
      return;
    }

    optimizeWithGrid(candidate, process, spec);
  }

  /**
   * Runs a bounded grid search for the candidate decision variables.
   *
   * @param candidate candidate being optimized
   * @param process built process system
   * @param spec process research specification
   */
  private void optimizeWithGrid(ProcessCandidate candidate, ProcessSystem process,
      ProcessResearchSpec spec) {
    ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
    for (ProcessResearchSpec.DecisionVariable variable : spec.getDecisionVariables()) {
      evaluator.addParameter(variable.getEquipmentName(), variable.getPropertyName(),
          variable.getLowerBound(), variable.getUpperBound(), variable.getUnit());
    }
    evaluator.addObjective("processResearchScore",
        currentProcess -> scoreProcess(currentProcess, candidate, spec, false),
        ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);

    List<double[]> grid = buildGrid(spec.getDecisionVariables(), spec.getMaxOptimizationCases());
    double bestScore = Double.NEGATIVE_INFINITY;
    double[] bestPoint = null;
    for (double[] point : grid) {
      ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(point);
      if (!result.isSimulationConverged()) {
        continue;
      }
      double[] raw = result.getObjectivesRaw();
      double currentScore = raw != null && raw.length > 0 ? raw[0] : Double.NEGATIVE_INFINITY;
      if (currentScore > bestScore) {
        bestScore = currentScore;
        bestPoint = point.clone();
      }
    }
    if (bestPoint != null) {
      evaluator.evaluate(bestPoint);
      candidate.setOptimized(true);
      candidate.setScore(scoreProcess(process, candidate, spec, true));
      candidate.setFeasible(Double.isFinite(bestScore));
      addBestPointObjectives(candidate, spec, bestPoint);
    } else {
      candidate.setFeasible(false);
      candidate.setScore(Double.NEGATIVE_INFINITY);
      candidate.addError("No decision-variable grid point converged");
    }
  }

  /**
   * Adds best-point decision variable values to candidate objectives.
   *
   * @param candidate candidate to update
   * @param spec process research specification
   * @param bestPoint best decision variable vector
   */
  private void addBestPointObjectives(ProcessCandidate candidate, ProcessResearchSpec spec,
      double[] bestPoint) {
    for (int i = 0; i < bestPoint.length; i++) {
      ProcessResearchSpec.DecisionVariable variable = spec.getDecisionVariables().get(i);
      candidate.addObjectiveValue(variable.getEquipmentName() + "." + variable.getPropertyName(),
          bestPoint[i]);
    }
  }

  /**
   * Builds a Cartesian grid for decision-variable screening.
   *
   * @param variables decision variables
   * @param maxCases maximum number of cases
   * @return list of grid points
   */
  private List<double[]> buildGrid(List<ProcessResearchSpec.DecisionVariable> variables,
      int maxCases) {
    List<double[]> points = new ArrayList<>();
    buildGridRecursive(variables, 0, new double[variables.size()], points, maxCases);
    return points;
  }

  /**
   * Recursively builds grid points.
   *
   * @param variables decision variables
   * @param index current variable index
   * @param current current point under construction
   * @param points accumulated points
   * @param maxCases maximum number of points
   */
  private void buildGridRecursive(List<ProcessResearchSpec.DecisionVariable> variables, int index,
      double[] current, List<double[]> points, int maxCases) {
    if (points.size() >= maxCases) {
      return;
    }
    if (index >= variables.size()) {
      points.add(current.clone());
      return;
    }
    ProcessResearchSpec.DecisionVariable variable = variables.get(index);
    int levels = variable.getGridLevels();
    for (int i = 0; i < levels; i++) {
      double fraction = levels == 1 ? 0.0 : ((double) i) / (levels - 1);
      current[index] = variable.getLowerBound()
          + fraction * (variable.getUpperBound() - variable.getLowerBound());
      buildGridRecursive(variables, index + 1, current, points, maxCases);
    }
  }

  /**
   * Scores a process against the product targets.
   *
   * @param process process system to score
   * @param candidate candidate metadata with product stream references
   * @param spec process research specification
   * @param recordMetrics whether to record detailed metrics and warnings on the candidate
   * @return ranking score; higher is better
   */
  private double scoreProcess(ProcessSystem process, ProcessCandidate candidate,
      ProcessResearchSpec spec, boolean recordMetrics) {
    ProcessResearchMetrics metrics = collectProcessMetrics(process, candidate, spec, recordMetrics);
    double score = 0.0;
    for (ProcessResearchSpec.ProductTarget target : spec.getProductTargets()) {
      String reference = resolveTargetReference(candidate, target);
      StreamInterface stream = process.resolveStreamReference(reference);
      if (stream == null) {
        if (recordMetrics) {
          candidate.addWarning("Could not resolve product stream reference: " + reference);
        }
        score -= 1.0e6;
        continue;
      }
      double flow = safeFlowRate(stream);
      double purity = safePurity(stream, target.getComponentName());
      metrics.add("productFlow_kg_hr", target.getWeight() * flow);
      if (recordMetrics) {
        candidate.addObjectiveValue(target.getName() + ".flow_kg_hr", flow);
        if (!Double.isNaN(purity)) {
          candidate.addObjectiveValue(target.getName() + ".purity", purity);
        }
      }
      score += spec.getScoringWeights().getProductFlowWeight() * target.getWeight() * flow;
      if (!Double.isNaN(purity)) {
        metrics.add("productPurity_weighted", target.getWeight() * purity);
        score += spec.getScoringWeights().getPurityWeight() * target.getWeight() * purity;
      }
      if (flow < target.getMinFlowRate()) {
        score -= 1.0e5 * (target.getMinFlowRate() - flow);
      }
      if (!Double.isNaN(purity) && purity < target.getMinPurity()) {
        score -= 1.0e6 * (target.getMinPurity() - purity);
      }
    }
    score = applyProcessPenalties(score, metrics, spec);
    List<String> violations = findConstraintViolations(metrics, spec);
    if (!violations.isEmpty()) {
      if (recordMetrics) {
        for (String violation : violations) {
          candidate.addError("Hard synthesis constraint violated: " + violation);
        }
        metrics.set("constraintViolationCount", violations.size());
        metrics.set("compositeScore", Double.NEGATIVE_INFINITY);
        recordMetrics(candidate, metrics);
      }
      return Double.NEGATIVE_INFINITY;
    }
    if (spec.getObjective() == ProcessResearchSpec.Objective.MINIMIZE_ENERGY) {
      score -= metrics.get("totalPower_kW", 0.0) + metrics.get("hotUtility_kW", 0.0)
          + metrics.get("coldUtility_kW", 0.0);
    }
    if (recordMetrics) {
      double robustnessDelta = evaluateRobustness(candidate, spec, score);
      metrics.set("robustnessDelta", robustnessDelta);
      score += spec.getScoringWeights().getRobustnessWeight() * robustnessDelta;
      metrics.set("compositeScore", score);
      recordMetrics(candidate, metrics);
    }
    return score;
  }

  /**
   * Records structured metrics on a candidate.
   *
   * @param candidate candidate to update
   * @param metrics metrics to record
   */
  private void recordMetrics(ProcessCandidate candidate, ProcessResearchMetrics metrics) {
    candidate.setMetrics(metrics);
    for (Map.Entry<String, Double> entry : metrics.asMap().entrySet()) {
      candidate.addObjectiveValue(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Collects process-wide energy, heat-integration, cost, emissions, and complexity metrics.
   *
   * @param process process system to inspect
   * @param candidate candidate receiving warnings when requested
   * @param spec process research specification
   * @param recordWarnings true to record warning messages on the candidate
   * @return process metrics
   */
  private ProcessResearchMetrics collectProcessMetrics(ProcessSystem process,
      ProcessCandidate candidate, ProcessResearchSpec spec, boolean recordWarnings) {
    ProcessResearchMetrics metrics = new ProcessResearchMetrics();
    double totalPower = 0.0;
    double heatingDuty = 0.0;
    double coolingDuty = 0.0;
    double capitalCostProxy = 0.0;
    int equipmentCount = 0;
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      equipmentCount++;
      capitalCostProxy += spec.getEconomicAssumptions()
          .getEquipmentCostProxyUsd(equipment.getClass().getSimpleName());
      if (equipment instanceof Compressor) {
        totalPower += Math.abs(((Compressor) equipment).getPower("kW"));
      } else if (equipment instanceof Pump) {
        totalPower += Math.abs(((Pump) equipment).getPower("kW"));
      } else if (equipment instanceof Heater) {
        double duty = ((Heater) equipment).getDuty("kW");
        if (equipment instanceof Cooler || duty < 0.0) {
          coolingDuty += Math.abs(duty);
        } else {
          heatingDuty += Math.abs(duty);
        }
      } else if (equipment instanceof HeatExchanger) {
        double duty = ((HeatExchanger) equipment).getDuty() / 1000.0;
        if (duty < 0.0) {
          coolingDuty += Math.abs(duty);
        } else {
          heatingDuty += Math.abs(duty);
        }
      }
    }
    metrics.set("equipmentCount", equipmentCount);
    metrics.set("totalPower_kW", totalPower);
    metrics.set("heatingDuty_kW", heatingDuty);
    metrics.set("coolingDuty_kW", coolingDuty);
    ProcessResearchSpec.SynthesisConstraints constraints = spec.getSynthesisConstraints();
    if (spec.isIncludeCostEstimate() || isFinite(constraints.getMaxCapitalCostProxyUSD())) {
      metrics.set("capitalCostProxy_USD", capitalCostProxy);
    }
    if (spec.isIncludeEmissionEstimate() || isFinite(constraints.getMaxEmissionsKgCO2ePerHr())) {
      double emissions =
          totalPower * spec.getEconomicAssumptions().getElectricityEmissionFactorKgCO2PerKWh();
      metrics.set("emissions_kgCO2e_per_hr", emissions);
    }
    addHeatIntegrationMetrics(process, candidate, spec, metrics, recordWarnings);
    addOperatingCostMetrics(spec, metrics);
    return metrics;
  }

  /**
   * Adds pinch-analysis metrics when requested.
   *
   * @param process process system to analyze
   * @param candidate candidate receiving warnings when requested
   * @param spec process research specification
   * @param metrics metrics to update
   * @param recordWarnings true to record warning messages on the candidate
   */
  private void addHeatIntegrationMetrics(ProcessSystem process, ProcessCandidate candidate,
      ProcessResearchSpec spec, ProcessResearchMetrics metrics, boolean recordWarnings) {
    if (!spec.isIncludeHeatIntegration()) {
      return;
    }
    try {
      PinchAnalysis pinch =
          PinchAnalysis.fromProcessSystem(process, spec.getHeatIntegrationDeltaTMinC());
      pinch.run();
      metrics.set("hotUtility_kW", pinch.getMinimumHeatingUtility());
      metrics.set("coldUtility_kW", pinch.getMinimumCoolingUtility());
      metrics.set("maximumHeatRecovery_kW", pinch.getMaximumHeatRecovery());
      metrics.set("pinchTemperature_C", pinch.getPinchTemperatureC());
    } catch (Exception e) {
      if (recordWarnings) {
        candidate.addWarning("Heat integration metrics unavailable: " + e.getMessage());
      }
      metrics.set("hotUtility_kW", metrics.get("heatingDuty_kW", 0.0));
      metrics.set("coldUtility_kW", metrics.get("coolingDuty_kW", 0.0));
    }
  }

  /**
   * Adds annual operating cost metrics from energy and utility terms.
   *
   * @param spec process research specification
   * @param metrics metrics to update
   */
  private void addOperatingCostMetrics(ProcessResearchSpec spec, ProcessResearchMetrics metrics) {
    double hours = spec.getEconomicAssumptions().getOperatingHoursPerYear();
    double electricCost = metrics.get("totalPower_kW", 0.0) * hours
        * spec.getEconomicAssumptions().getElectricityCostUsdPerKWh();
    double hotUtilityCost = metrics.get("hotUtility_kW", metrics.get("heatingDuty_kW", 0.0)) * hours
        * spec.getEconomicAssumptions().getHotUtilityCostUsdPerKWh();
    double coldUtilityCost = metrics.get("coldUtility_kW", metrics.get("coolingDuty_kW", 0.0))
        * hours * spec.getEconomicAssumptions().getColdUtilityCostUsdPerKWh();
    double carbonCost = metrics.get("emissions_kgCO2e_per_hr", 0.0) * hours / 1000.0
        * spec.getEconomicAssumptions().getCarbonPriceUsdPerTonne();
    metrics.set("annualElectricityCost_USD_per_yr", electricCost);
    metrics.set("annualHotUtilityCost_USD_per_yr", hotUtilityCost);
    metrics.set("annualColdUtilityCost_USD_per_yr", coldUtilityCost);
    metrics.set("annualCarbonCost_USD_per_yr", carbonCost);
    metrics.set("annualOperatingCostProxy_USD_per_yr",
        electricCost + hotUtilityCost + coldUtilityCost + carbonCost);
  }

  /**
   * Applies process-level penalties to the product score.
   *
   * @param score current product score
   * @param metrics process metrics
   * @param spec process research specification
   * @return adjusted score
   */
  private double applyProcessPenalties(double score, ProcessResearchMetrics metrics,
      ProcessResearchSpec spec) {
    ProcessResearchSpec.ScoringWeights weights = spec.getScoringWeights();
    double adjusted = score;
    adjusted -= weights.getElectricPowerPenalty() * metrics.get("totalPower_kW", 0.0);
    adjusted -= weights.getHotUtilityPenalty()
        * metrics.get("hotUtility_kW", metrics.get("heatingDuty_kW", 0.0));
    adjusted -= weights.getColdUtilityPenalty()
        * metrics.get("coldUtility_kW", metrics.get("coolingDuty_kW", 0.0));
    adjusted -= weights.getCapitalCostPenalty() * metrics.get("capitalCostProxy_USD", 0.0);
    adjusted -= weights.getEmissionsPenalty() * metrics.get("emissions_kgCO2e_per_hr", 0.0);
    adjusted -= weights.getComplexityPenalty() * metrics.get("equipmentCount", 0.0);
    return adjusted;
  }

  /**
   * Finds hard synthesis constraint violations.
   *
   * @param metrics process metrics
   * @param spec process research specification
   * @return violation descriptions
   */
  private List<String> findConstraintViolations(ProcessResearchMetrics metrics,
      ProcessResearchSpec spec) {
    List<String> violations = new ArrayList<String>();
    ProcessResearchSpec.SynthesisConstraints constraints = spec.getSynthesisConstraints();
    addViolation(violations, "equipmentCount", metrics.get("equipmentCount", 0.0),
        constraints.getMaxEquipmentCount());
    addViolation(violations, "totalPower_kW", metrics.get("totalPower_kW", 0.0),
        constraints.getMaxTotalPowerKW());
    addViolation(violations, "hotUtility_kW",
        metrics.get("hotUtility_kW", metrics.get("heatingDuty_kW", 0.0)),
        constraints.getMaxHotUtilityKW());
    addViolation(violations, "coldUtility_kW",
        metrics.get("coldUtility_kW", metrics.get("coolingDuty_kW", 0.0)),
        constraints.getMaxColdUtilityKW());
    addViolation(violations, "capitalCostProxy_USD", metrics.get("capitalCostProxy_USD", 0.0),
        constraints.getMaxCapitalCostProxyUSD());
    addViolation(violations, "emissions_kgCO2e_per_hr", metrics.get("emissions_kgCO2e_per_hr", 0.0),
        constraints.getMaxEmissionsKgCO2ePerHr());
    addViolation(violations, "annualOperatingCostProxy_USD_per_yr",
        metrics.get("annualOperatingCostProxy_USD_per_yr", 0.0),
        constraints.getMaxAnnualOperatingCostProxyUSDPerYr());
    return violations;
  }

  /**
   * Adds a violation when a metric exceeds a finite limit.
   *
   * @param violations violation list to update
   * @param metricName metric name
   * @param value metric value
   * @param limit finite maximum limit or infinity
   */
  private void addViolation(List<String> violations, String metricName, double value,
      double limit) {
    if (isFinite(limit) && value > limit) {
      violations.add(metricName + " = " + value + " exceeds limit " + limit);
    }
  }

  /**
   * Checks whether a value is finite.
   *
   * @param value value to inspect
   * @return true when value is neither infinite nor NaN
   */
  private boolean isFinite(double value) {
    return !Double.isInfinite(value) && !Double.isNaN(value);
  }

  /**
   * Evaluates optional feed-condition robustness scenarios.
   *
   * @param candidate candidate to perturb
   * @param spec process research specification
   * @param baseScore base-case score
   * @return worst-case score delta relative to the base case
   */
  private double evaluateRobustness(ProcessCandidate candidate, ProcessResearchSpec spec,
      double baseScore) {
    if (spec.getRobustnessScenarios().isEmpty()
        || spec.getScoringWeights().getRobustnessWeight() == 0.0) {
      return 0.0;
    }
    double worstScore = baseScore;
    for (ProcessResearchSpec.RobustnessScenario scenario : spec.getRobustnessScenarios()) {
      String scenarioJson = createScenarioJson(candidate.getJsonDefinition(), scenario);
      SimulationResult result = ProcessSystem.fromJsonAndRun(scenarioJson);
      if (result.isError() || result.getProcessSystem() == null) {
        worstScore = Math.min(worstScore, -1.0e9);
        continue;
      }
      ProcessCandidate scenarioCandidate =
          new ProcessCandidate(candidate.getId() + "-" + scenario.getName(), candidate.getName(),
              candidate.getGenerationMethod());
      for (Map.Entry<String, String> entry : candidate.getProductStreamReferences().entrySet()) {
        scenarioCandidate.addProductStreamReference(entry.getKey(), entry.getValue());
      }
      double scenarioScore =
          scoreProcess(result.getProcessSystem(), scenarioCandidate, spec, false);
      worstScore = Math.min(worstScore, scenarioScore);
    }
    return worstScore - baseScore;
  }

  /**
   * Creates a perturbed candidate JSON for a robustness scenario.
   *
   * @param jsonDefinition base candidate JSON
   * @param scenario robustness scenario
   * @return perturbed JSON
   */
  private String createScenarioJson(String jsonDefinition,
      ProcessResearchSpec.RobustnessScenario scenario) {
    JsonObject root = new JsonParser().parse(jsonDefinition).getAsJsonObject();
    JsonObject fluid = root.getAsJsonObject("fluid");
    if (fluid != null) {
      if (fluid.has("temperature")) {
        fluid.addProperty("temperature",
            fluid.get("temperature").getAsDouble() + scenario.getFeedTemperatureOffsetK());
      }
      if (fluid.has("pressure")) {
        fluid.addProperty("pressure",
            fluid.get("pressure").getAsDouble() * scenario.getFeedPressureMultiplier());
      }
    }
    JsonArray process = root.getAsJsonArray("process");
    if (process != null) {
      for (int i = 0; i < process.size(); i++) {
        JsonObject unit = process.get(i).getAsJsonObject();
        if (unit.has("name") && "feed".equals(unit.get("name").getAsString())
            && unit.has("properties")) {
          JsonObject props = unit.getAsJsonObject("properties");
          if (props.has("flowRate") && props.get("flowRate").isJsonArray()) {
            JsonArray flow = props.getAsJsonArray("flowRate");
            flow.set(0,
                new JsonPrimitive(flow.get(0).getAsDouble() * scenario.getFeedFlowMultiplier()));
          }
        }
      }
    }
    return gson.toJson(root);
  }

  /**
   * Resolves the stream reference used for a product target.
   *
   * @param candidate process candidate
   * @param target product target
   * @return stream reference string
   */
  private String resolveTargetReference(ProcessCandidate candidate,
      ProcessResearchSpec.ProductTarget target) {
    if (target.getStreamReference() != null && !target.getStreamReference().trim().isEmpty()) {
      return target.getStreamReference();
    }
    String reference = candidate.getProductStreamReference(target.getStreamRole());
    if (reference == null) {
      reference = candidate.getProductStreamReference("product");
    }
    return reference;
  }

  /**
   * Safely reads stream flow rate.
   *
   * @param stream stream to read
   * @return stream flow rate in kg/hr, or zero on failure
   */
  private double safeFlowRate(StreamInterface stream) {
    try {
      return stream.getFlowRate("kg/hr");
    } catch (Exception e) {
      return 0.0;
    }
  }

  /**
   * Safely reads product purity as overall mole fraction.
   *
   * @param stream stream to inspect
   * @param componentName component name, or null to skip purity
   * @return purity mole fraction, or NaN when unavailable
   */
  private double safePurity(StreamInterface stream, String componentName) {
    if (componentName == null || componentName.trim().isEmpty()) {
      return Double.NaN;
    }
    try {
      SystemInterface fluid = stream.getThermoSystem();
      ComponentInterface component = fluid.getComponent(componentName);
      return component.getz();
    } catch (Exception e) {
      return Double.NaN;
    }
  }
}
