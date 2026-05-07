package neqsim.process.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Tests for the process researcher API.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class ProcessResearcherTest {

  /**
   * Verifies the documented process researcher example builds, runs, and ranks candidates.
   */
  @Test
  void documentationExampleBuildsAndRanksCandidates() {
    ProcessResearchSpec spec = ProcessResearchSpec.builder()
        .setName("gas product from hydrocarbon feed").setFluidModel("SRK")
        .setFeedTemperature(298.15).setFeedPressure(20.0).setFeedFlowRate(1000.0, "kg/hr")
        .addFeedComponent("methane", 0.90).addFeedComponent("n-heptane", 0.10)
        .addProductTarget(new ProcessResearchSpec.ProductTarget("gas product")
            .setStreamRole("gas").setComponentName("methane").setMinFlowRate(1.0))
        .addAllowedUnitType("Separator")
        .addDecisionVariable(
            new ProcessResearchSpec.DecisionVariable("feed", "flowRate", 1000.0, 2000.0, "kg/hr")
                .setGridLevels(2))
        .build();

    ProcessResearchResult result = new ProcessResearcher().research(spec);
    ProcessCandidate best = result.getBestCandidate();

    assertNotNull(best);
    assertTrue(best.isFeasible(), best.getErrors().toString());
    assertTrue(best.isOptimized());
    assertTrue(best.getScore() > 0.0);
    assertEquals(2000.0, best.getObjectiveValues().get("feed.flowRate"), 1.0e-9);
    assertNotNull(best.getProcessSystem());
  }

  /**
   * Verifies reaction-route candidate generation without requiring a full reaction solve.
   */
  @Test
  void reactionRouteCandidateIsGeneratedFromReactionOption() {
    ReactionOption reaction = new ReactionOption("steam methane reforming screen")
        .setReactorType("GibbsReactor").setExpectedProductComponent("hydrogen")
        .setReactorTemperature(1000.0).addStoichiometricCoefficient("methane", -1.0)
        .addStoichiometricCoefficient("water", -1.0).addStoichiometricCoefficient("hydrogen", 3.0)
        .addStoichiometricCoefficient("CO", 1.0);

    ProcessResearchSpec spec =
        ProcessResearchSpec.builder().setName("hydrogen route screen").setFeedTemperature(800.0)
            .setFeedPressure(25.0).setFeedFlowRate(500.0, "kg/hr").addFeedComponent("methane", 0.45)
            .addFeedComponent("water", 0.45).addFeedComponent("hydrogen", 0.05)
            .addFeedComponent("CO", 0.025).addFeedComponent("CO2", 0.025)
            .addProductTarget(new ProcessResearchSpec.ProductTarget("hydrogen").setStreamRole("gas")
                .setComponentName("hydrogen"))
            .addAllowedUnitType("GibbsReactor").addAllowedUnitType("Heater")
            .addAllowedUnitType("Separator").addReactionOption(reaction)
            .setEvaluateCandidates(false).build();

    List<ProcessCandidate> candidates = new ProcessCandidateGenerator().generate(spec);
    boolean foundReactionCandidate = false;
    for (ProcessCandidate candidate : candidates) {
      if ("reaction-route".equals(candidate.getGenerationMethod())) {
        foundReactionCandidate = true;
        assertTrue(candidate.getJsonDefinition().contains("GibbsReactor"));
        assertTrue(candidate.getJsonDefinition().contains("reaction separator"));
      }
    }
    assertTrue(foundReactionCandidate);
  }

  /**
   * Verifies generated JSON can instantiate the generic Gibbs reactor through EquipmentFactory.
   */
  @Test
  void jsonBuilderCanCreateGibbsReactorCandidate() {
    String json = "{" + "\"fluid\":{\"model\":\"SRK\",\"temperature\":700.0,\"pressure\":20.0,"
        + "\"mixingRule\":\"classic\",\"components\":{\"methane\":0.6,"
        + "\"water\":0.3,\"hydrogen\":0.05,\"CO\":0.025,\"CO2\":0.025}},"
        + "\"process\":[{\"type\":\"Stream\",\"name\":\"feed\","
        + "\"properties\":{\"flowRate\":[1000.0,\"kg/hr\"]}},"
        + "{\"type\":\"GibbsReactor\",\"name\":\"reactor\",\"inlet\":\"feed\","
        + "\"properties\":{\"energyMode\":\"ISOTHERMAL\"}}],\"autoRun\":false}";

    SimulationResult result = ProcessSystem.fromJsonAndRun(json);

    assertTrue(result.isSuccess(), result.getErrors().toString());
    assertNotNull(result.getProcessSystem());
    assertTrue(result.getProcessSystem().getUnit("reactor") instanceof GibbsReactor);
    assertFalse(result.getWarnings().toString().contains("Unknown equipment type"));
  }

  /**
   * Verifies graph-based material-operation path generation for process synthesis.
   */
  @Test
  void graphBasedSynthesisGeneratesOperationPathCandidate() {
    OperationOption compression =
        new OperationOption("compression", "Compressor").addInputMaterial("feed gas")
            .addOutputMaterial("compressed gas").setProperty("outletPressure", 40.0, "bara");
    OperationOption separation = new OperationOption("polishing separator", "Separator")
        .addInputMaterial("compressed gas").addOutputMaterial("sales gas");

    ProcessResearchSpec spec = ProcessResearchSpec.builder().setName("graph synthesis")
        .setFeedMaterialName("feed gas").setFeedTemperature(298.15).setFeedPressure(20.0)
        .setFeedFlowRate(1000.0, "kg/hr").addFeedComponent("methane", 0.9)
        .addFeedComponent("n-heptane", 0.1)
        .addMaterialNode(new MaterialNode("compressed gas", "compressed intermediate", "methane"))
        .addMaterialNode(new MaterialNode("sales gas", "target gas", "methane"))
        .addProductTarget(new ProcessResearchSpec.ProductTarget("sales gas")
            .setMaterialName("sales gas").setStreamRole("gas").setComponentName("methane"))
        .addAllowedUnitType("Compressor").addAllowedUnitType("Separator")
        .addOperationOption(compression).addOperationOption(separation).setEvaluateCandidates(false)
        .build();

    List<ProcessCandidate> candidates = new ProcessCandidateGenerator().generate(spec);
    boolean foundGraphCandidate = false;
    for (ProcessCandidate candidate : candidates) {
      if ("process-network-graph".equals(candidate.getGenerationMethod())) {
        foundGraphCandidate = true;
        assertEquals(2, candidate.getSynthesisPath().size());
        assertTrue(candidate.getJsonDefinition().contains("compression path"));
        assertTrue(candidate.getJsonDefinition().contains("polishing separator path"));
      }
    }
    assertTrue(foundGraphCandidate);
  }

  /**
   * Verifies infeasible reaction routes are pruned before rigorous simulation.
   */
  @Test
  void missingReactionReactantIsPruned() {
    ReactionOption reaction = new ReactionOption("oxidation route").setReactorType("GibbsReactor")
        .addStoichiometricCoefficient("methane", -1.0).addStoichiometricCoefficient("oxygen", -2.0)
        .addStoichiometricCoefficient("CO2", 1.0);

    ProcessResearchSpec spec =
        ProcessResearchSpec.builder().setName("pruned reaction").addFeedComponent("methane", 1.0)
            .addProductTarget(new ProcessResearchSpec.ProductTarget("carbon dioxide")
                .setComponentName("CO2").setStreamRole("gas"))
            .addAllowedUnitType("GibbsReactor").addReactionOption(reaction).build();

    ProcessResearchResult result = new ProcessResearcher().research(spec);

    assertNull(result.getBestCandidate());
    assertEquals(1, result.getCandidates().size());
    assertFalse(result.getCandidates().get(0).getErrors().isEmpty());
  }

  /**
   * Verifies heat-integration, cost, emissions, and objective metrics are reported.
   */
  @Test
  void multiObjectiveMetricsAreRecorded() {
    OperationOption heater =
        new OperationOption("feed heater", "Heater").setProperty("outletTemperature", 330.0, "K");

    ProcessResearchSpec.ScoringWeights weights = new ProcessResearchSpec.ScoringWeights()
        .setElectricPowerPenalty(0.01).setHotUtilityPenalty(0.01).setComplexityPenalty(1.0);

    ProcessResearchSpec spec =
        ProcessResearchSpec.builder().setName("metric rich synthesis").setFeedTemperature(300.0)
            .setFeedPressure(10.0).setFeedFlowRate(1000.0, "kg/hr").addFeedComponent("methane", 1.0)
            .addProductTarget(new ProcessResearchSpec.ProductTarget("heated gas")
                .setStreamReference("feed heater.outlet").setComponentName("methane"))
            .addAllowedUnitType("Heater").addOperationOption(heater).setScoringWeights(weights)
            .setIncludeHeatIntegration(true).setIncludeCostEstimate(true)
            .setIncludeEmissionEstimate(true).build();

    ProcessResearchResult result = new ProcessResearcher().research(spec);
    ProcessCandidate best = result.getBestCandidate();

    assertNotNull(best);
    assertTrue(best.getObjectiveValues().containsKey("equipmentCount"));
    assertTrue(best.getObjectiveValues().containsKey("annualOperatingCostProxy_USD_per_yr"));
    assertTrue(best.getObjectiveValues().containsKey("capitalCostProxy_USD"));
    assertTrue(best.getObjectiveValues().containsKey("emissions_kgCO2e_per_hr"));
  }

  /**
   * Verifies external optimizer exports include superstructure and Pyomo/GDP content.
   */
  @Test
  void superstructureExporterCreatesJsonAndPyomoSkeleton() {
    OperationOption separation = new OperationOption("separator option", "Separator")
        .addInputMaterial("feed").addOutputMaterial("gas product");
    ProcessResearchSpec spec = ProcessResearchSpec.builder().setName("external optimizer handoff")
        .addFeedComponent("methane", 0.9).addFeedComponent("ethane", 0.1)
        .addProductTarget(new ProcessResearchSpec.ProductTarget("gas product")
            .setMaterialName("gas product").setComponentName("methane"))
        .addOperationOption(separation).build();

    ProcessSuperstructureExporter exporter = new ProcessSuperstructureExporter();

    assertTrue(exporter.toJson(spec).contains("recommendedSolverClass"));
    assertTrue(exporter.toPyomoSkeleton(spec).contains("Disjunct"));
  }
}
