package neqsim.process.engineering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import neqsim.process.engineering.calculation.EngineeringCalculationContext;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.engineering.calculation.EquipmentDesignCalculations;
import neqsim.process.engineering.calculation.MaterialsMechanicalDesignCalculations;
import neqsim.process.engineering.calculation.ValveInstrumentDesignCalculations;
import neqsim.process.engineering.model.EngineeringDiagramLayout;
import neqsim.process.engineering.model.EngineeringEdge;
import neqsim.process.engineering.model.EngineeringGraph;
import neqsim.process.engineering.model.EngineeringNode;
import neqsim.process.engineering.piping.PipingNetworkDesignCalculation;
import neqsim.process.engineering.piping.PipingRulePack;
import neqsim.process.engineering.safety.SafetyScenarioEngineCalculation;
import org.junit.jupiter.api.Test;

/** Regression tests for process-to-engineering roadmap calculations through coordinated package design. */
class EngineeringRoadmapCalculationsTest {
  private final EngineeringCalculationContext context = EngineeringCalculationContext.builder().designCaseId("max")
      .build();

  @Test
  void calculatesEveryPriorityEquipmentFamilyWithGovernedMetadata() {
    assertCalculated(new EquipmentDesignCalculations.Separator(),
        EquipmentDesignCalculations.Input.builder("V-1", "max", "DB-1").value("gasFlowM3s", 2.0)
            .value("liquidFlowM3s", 0.02).value("gasDensityKgM3", 35.0).value("liquidDensityKgM3", 750.0).build());
    assertCalculated(new EquipmentDesignCalculations.Compressor(),
        EquipmentDesignCalculations.Input.builder("K-1", "max", "DB-1").value("operatingFlowM3s", 5.0)
            .value("surgeFlowM3s", 3.5).value("chokeFlowM3s", 7.0).value("shaftPowerKw", 2400.0).build());
    assertCalculated(new EquipmentDesignCalculations.Pump(),
        EquipmentDesignCalculations.Input.builder("P-1", "max", "DB-1").value("flowM3s", 0.05).value("headM", 120.0)
            .value("npshaM", 8.0).value("npshrM", 4.0).value("shaftPowerKw", 80.0).build());
    assertCalculated(new EquipmentDesignCalculations.HeatExchanger(),
        EquipmentDesignCalculations.Input.builder("E-1", "max", "DB-1").value("dutyKw", 1000.0)
            .value("overallU_WPerM2K", 500.0).value("correctedLmtdK", 25.0).build());
    assertCalculated(new EquipmentDesignCalculations.Column(),
        EquipmentDesignCalculations.Input.builder("T-1", "max", "DB-1").value("operatingVaporLoadM3s", 4.0)
            .value("floodingVelocityMPerS", 2.0).value("minimumVaporLoadM3s", 1.5).build());
    assertCalculated(new EquipmentDesignCalculations.Tank(),
        EquipmentDesignCalculations.Input.builder("TK-1", "max", "DB-1").value("normalInflowM3s", 0.02)
            .value("workingTimeS", 600.0).value("emergencyTimeS", 300.0).build());
  }

  @Test
  void selectsSmallestNetworkCandidateThatSatisfiesAllCases() {
    PipingNetworkDesignCalculation.Segment segment = new PipingNetworkDesignCalculation.Segment("L-1", true, false,
        1000.0, 100.0, 10.0, 1.0, 0.0)
        .addCase(new PipingNetworkDesignCalculation.Case("max", 0.30, 0.10, 0.20, 50.0, 35.0));
    PipingNetworkDesignCalculation.Input input = new PipingNetworkDesignCalculation.Input("export",
        PipingRulePack.norsokP0022023Ac2024(),
        Arrays.asList(new PipingNetworkDesignCalculation.Candidate("4in", "40", 0.10, 100.0),
            new PipingNetworkDesignCalculation.Candidate("8in", "40", 0.20, 100.0)),
        Collections.singletonList(segment), Collections.singletonMap("export", Double.valueOf(0.30)));

    EngineeringCalculationResult<PipingNetworkDesignCalculation.Result> result = new PipingNetworkDesignCalculation()
        .calculate(input, context);

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    Map<?, ?> line = (Map<?, ?>) result.getValue().toMap().get("selections");
    Map<?, ?> selected = (Map<?, ?>) line.get("L-1");
    assertEquals("8in", ((Map<?, ?>) selected.get("candidate")).get("nominalSize"));
  }

  @Test
  void calculatesValveInstrumentSafetyMaterialsAndMechanicalReviews() {
    ValveInstrumentDesignCalculations.ValveInput valveInput = new ValveInstrumentDesignCalculations.ValveInput("FV-1",
        "max", 80.0, 100.0, 70.0, 50.0, 30.0, 10.0, 46.0, 600.0, 5.0, 5.0, 8.0,
        ValveInstrumentDesignCalculations.FailurePosition.HAZOP_INPUT_REQUIRED);
    EngineeringCalculationResult<ValveInstrumentDesignCalculations.ValveResult> valve = new ValveInstrumentDesignCalculations.Valve()
        .calculate(valveInput, context);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, valve.getStatus());
    assertTrue(valve.getReadiness().requiresReview());

    ValveInstrumentDesignCalculations.InstrumentInput instrumentInput = new ValveInstrumentDesignCalculations.InstrumentInput(
        "PIT-1", "max", 20.0, 55.0, 0.0, 100.0, 0.005, 1.0, 0.5, 4.0, 10.0, 2.0, 30.0);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        new ValveInstrumentDesignCalculations.Instrument().calculate(instrumentInput, context).getStatus());

    SafetyScenarioEngineCalculation.Scenario scenario = new SafetyScenarioEngineCalculation.Scenario("blocked-outlet",
        "V-1", SafetyScenarioEngineCalculation.Type.BLOCKED_OUTLET,
        SafetyScenarioEngineCalculation.Credibility.CREDIBLE, SafetyScenarioEngineCalculation.FluidModel.GAS, "train-a",
        "HAZOP-1", 10.0, 50.0, 0.20, 2.0, 5.0, 1.0, -20.0);
    SafetyScenarioEngineCalculation.Input safetyInput = new SafetyScenarioEngineCalculation.Input("safety-1",
        Collections.singletonList(scenario), new double[] { 0.110, 0.196, 0.307 }, 3.0, 10.0, 120.0, -46.0);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        new SafetyScenarioEngineCalculation().calculate(safetyInput, context).getStatus());

    MaterialsMechanicalDesignCalculations.MaterialInput materialInput = new MaterialsMechanicalDesignCalculations.MaterialInput(
        "V-1", "max", 0.02, 0.001, 2000.0, true, true, 40.0, 80.0, -46.0, 55.0, 3.0, 25.0,
        "marine offshore external environment");
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        new MaterialsMechanicalDesignCalculations.MaterialSelection().calculate(materialInput, context).getStatus());

    MaterialsMechanicalDesignCalculations.MechanicalInput mechanicalInput = new MaterialsMechanicalDesignCalculations.MechanicalInput(
        "V-1", "max", 55.0, 80.0, 1.5, 4.5, 138.0, 0.85, 3.0, -46.0, 0.10);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        new MaterialsMechanicalDesignCalculations.PreliminaryMechanical().calculate(mechanicalInput, context)
            .getStatus());
  }

  @Test
  void blocksSafetyCalculationWithoutHazopCredibilityAndBuildsDeterministicLayout() {
    SafetyScenarioEngineCalculation.Scenario unresolved = new SafetyScenarioEngineCalculation.Scenario("fire", "V-1",
        SafetyScenarioEngineCalculation.Type.FIRE_EXPOSURE,
        SafetyScenarioEngineCalculation.Credibility.HAZOP_DECISION_REQUIRED,
        SafetyScenarioEngineCalculation.FluidModel.TWO_PHASE, "fire-zone-a", "", 8.0, 50.0, 0.30, 2.0, 5.0, 1.0, -55.0);
    SafetyScenarioEngineCalculation.Input input = new SafetyScenarioEngineCalculation.Input("blocked",
        Collections.singletonList(unresolved), new double[] { 0.307, 0.503 }, 3.0, 10.0, 120.0, -46.0);
    assertEquals(EngineeringCalculationResult.Status.BLOCKED,
        new SafetyScenarioEngineCalculation().calculate(input, context).getStatus());

    EngineeringGraph graph = new EngineeringGraph("project", "A");
    graph.addNode(new EngineeringNode("equipment:a", EngineeringNode.Kind.EQUIPMENT, "A", "A"));
    graph.addNode(new EngineeringNode("equipment:b", EngineeringNode.Kind.EQUIPMENT, "B", "B"));
    graph.addEdge(
        new EngineeringEdge("flow:a:b", "equipment:a", "equipment:b", EngineeringEdge.Kind.PROCESS_FLOW, "process"));
    Map<String, Object> first = EngineeringDiagramLayout.build(graph);
    Map<String, Object> second = EngineeringDiagramLayout.build(graph);
    assertEquals(first, second);
    assertFalse(((java.util.List<?>) first.get("routes")).isEmpty());
  }

  private void assertCalculated(
      neqsim.process.engineering.calculation.EngineeringCalculationModule<EquipmentDesignCalculations.Input, EquipmentDesignCalculations.Result> module,
      EquipmentDesignCalculations.Input input) {
    EngineeringCalculationResult<EquipmentDesignCalculations.Result> result = module.calculate(input, context);
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertTrue(result.getValue().toMap().containsKey("designBasisReference"));
  }
}
