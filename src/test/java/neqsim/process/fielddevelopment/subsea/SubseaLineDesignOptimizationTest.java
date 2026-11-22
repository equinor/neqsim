package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.equipment.network.NetworkConstraint;
import neqsim.process.equipment.network.NetworkConstraints;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.network.NetworkOptimizer;
import neqsim.process.mechanicaldesign.designstandards.StandardEdition;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineAssessment;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput.FabricationRoute;
import neqsim.process.mechanicaldesign.pipeline.DnvStF101PipelineDesignInput.SafetyClass;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityAssessment;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.AssetType;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LateralMethod;
import neqsim.process.mechanicaldesign.subsea.DnvRpF109OnBottomStabilityInput.LoadCase;
import neqsim.process.mechanicaldesign.subsea.SURFCostEstimator.LineDesign;
import neqsim.process.equipment.pipeline.RadialThermalLayer;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Acceptance tests for exact canonical route-design alternative enumeration. */
class SubseaLineDesignOptimizationTest {
  /** Select the least-cost candidate that passes the attached structural and stability screening constraints. */
  @Test
  void selectsLeastCostFeasibleCompleteLineDesign() {
    FieldNetworkTopology topology = topology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    LoopedPipeNetwork.NetworkPipe pipe = network.getPipe("production-flowline");
    SubseaProductionSystem subsea = new SubseaProductionSystem("line alternatives");
    LineDesign small = lineDesign(8.625, 8.0, "X52", "S-lay", 15.0, 5000.0);
    LineDesign large = lineDesign(10.75, 12.0, "X65", "J-lay", 50.0, 6500.0);
    NetworkDecisionVariable selection = subsea.createLineDesignDecisionVariable(topology, "production-flowline",
        Arrays.asList(small, large), 0);
    PipeModelType originalModel = pipe.getHydraulicModelType();

    double smallCostUsd = subsea.estimateSurfCosts(topology).getCapitalCostSummary().get("totalSURF");
    double smallUtilization = governingDnvUtilization(subsea, topology);
    selection.setValue(network, 1.0);
    double largeCostUsd = subsea.estimateSurfCosts(topology).getCapitalCostSummary().get("totalSURF");
    double largeUtilization = governingDnvUtilization(subsea, topology);
    EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment> largeStability = subsea
        .assessOnBottomStabilityLineDesigns(topology).get("production-flowline");
    assertTrue(smallUtilization > 1.0);
    assertTrue(largeUtilization <= 1.0);
    assertTrue(largeStability.getValue().areAllScreeningChecksPassing());
    assertTrue(largeCostUsd > smallCostUsd);
    assertTrue(large.resolveOverallHeatTransferCoefficientWm2K() < small.resolveOverallHeatTransferCoefficientWm2K());
    selection.setValue(network, 0.0);

    List<NetworkConstraint> screening = subsea.createLineDesignScreeningConstraints(topology, true);
    assertEquals(2, screening.size());
    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.setMaxEvaluations(2);
    optimizer.addDecisionVariable(selection);
    optimizer.addObjective(subsea.createSurfCapitalCostObjective(topology, 1.0e-6));
    optimizer.addConstraint(NetworkConstraints.convergence());
    optimizer.addConstraint(NetworkConstraints.massBalance(1.0e-8, true));
    for (NetworkConstraint constraint : screening) {
      optimizer.addConstraint(constraint);
    }

    NetworkOptimizer.OptimizationResult result = optimizer.optimize();

    assertTrue(result.converged);
    assertEquals("DISCRETE_ENUMERATION", result.algorithm);
    assertEquals(2, result.functionEvaluations);
    assertEquals(1.0, result.decisionValues.get("edge.production-flowline.lineDesign"), 1.0e-12);
    assertFalse(optimizer.getTrajectory().get(0).isFeasible());
    assertTrue(optimizer.getTrajectory().get(1).isFeasible());
    assertSame(large, subsea.getLineDesigns().get("production-flowline"));
    assertEquals(large.getResolvedInnerDiameterM(), pipe.getDiameter(), 1.0e-12);
    assertEquals(large.getWallThicknessMm() / 1000.0, pipe.getWallThickness(), 1.0e-12);
    assertEquals(large.resolveOverallHeatTransferCoefficientWm2K(), pipe.getOverallHeatTransferCoeff(), 1.0e-12);
    assertEquals(originalModel, pipe.getHydraulicModelType());
    assertTrue(network.isConverged());
    assertEquals(0.0, network.getMassBalanceError(), 1.0e-8);
    assertTrue(subsea.estimateSurfCosts(topology).getCapitalCostSummary().get("totalSURF") > 0.0);
    EngineeringCalculationResult<DnvStF101PipelineAssessment> assessment = subsea.assessDnvStF101LineDesigns(topology)
        .get("production-flowline");
    assertTrue(assessment.getValue().areAllScreeningChecksPassing());
  }

  /** Return the current selected route's governing ST-F101 utilization. */
  private double governingDnvUtilization(SubseaProductionSystem subsea, FieldNetworkTopology topology) {
    return subsea.assessDnvStF101LineDesigns(topology).get("production-flowline").getValue().getGoverningCheck()
        .getUtilization();
  }

  /** Candidate evaluation and rejected registration must preserve the complete baseline route state. */
  @Test
  void restoresCandidateStateAndRejectsInconsistentAlternatives() {
    FieldNetworkTopology topology = topology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    LoopedPipeNetwork.NetworkPipe pipe = network.getPipe("production-flowline");
    SubseaProductionSystem subsea = new SubseaProductionSystem("restored alternatives");
    LineDesign small = lineDesign(8.625, 8.0, "X52", "S-lay", 15.0, 5000.0);
    LineDesign large = lineDesign(10.75, 12.0, "X65", "J-lay", 50.0, 6500.0);
    NetworkDecisionVariable selection = subsea.createLineDesignDecisionVariable(topology, "production-flowline",
        Arrays.asList(small, large), 0);
    double initialDiameterM = pipe.getDiameter();
    double initialUValue = pipe.getOverallHeatTransferCoeff();
    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.addDecisionVariable(selection);

    optimizer.evaluateCandidate(new double[] {1.0});

    assertSame(small, subsea.getLineDesigns().get("production-flowline"));
    assertEquals(initialDiameterM, pipe.getDiameter(), 1.0e-12);
    assertEquals(initialUValue, pipe.getOverallHeatTransferCoeff(), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> selection.setValue(network, 0.5));
    LineDesign missingThermal = LineDesign.builder(10.75).wallThicknessMm(12.0)
        .dnvStF101Input(dnvInput(10.75 * 0.0254, 0.012)).onBottomStabilityInput(stabilityInput(10.75 * 0.0254, 6500.0))
        .build();
    assertThrows(IllegalArgumentException.class, () -> subsea.createLineDesignDecisionVariable(topology,
        "production-flowline", Arrays.asList(small, missingThermal), 0));
    assertSame(small, subsea.getLineDesigns().get("production-flowline"));
  }

  /** Complete enumeration with no passing screen must restore the initial complete route design. */
  @Test
  void restoresInitialDesignWhenEveryCandidateIsInfeasible() {
    FieldNetworkTopology topology = topology();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    SubseaProductionSystem subsea = new SubseaProductionSystem("infeasible alternatives");
    LineDesign initial = lineDesign(8.625, 8.0, "X52", "S-lay", 15.0, 5000.0);
    LineDesign secondWeak = lineDesign(10.75, 9.0, "X65", "J-lay", 50.0, 6500.0);
    NetworkDecisionVariable selection = subsea.createLineDesignDecisionVariable(topology, "production-flowline",
        Arrays.asList(initial, secondWeak), 0);
    assertTrue(governingDnvUtilization(subsea, topology) > 1.0);
    selection.setValue(network, 1.0);
    assertTrue(governingDnvUtilization(subsea, topology) > 1.0);
    selection.setValue(network, 0.0);

    NetworkOptimizer optimizer = new NetworkOptimizer(network);
    optimizer.setMaxEvaluations(2);
    optimizer.addDecisionVariable(selection);
    optimizer.addObjective(subsea.createSurfCapitalCostObjective(topology, 1.0e-6));
    for (NetworkConstraint constraint : subsea.createLineDesignScreeningConstraints(topology, true)) {
      optimizer.addConstraint(constraint);
    }

    NetworkOptimizer.OptimizationResult result = optimizer.optimize();

    assertFalse(result.converged);
    assertEquals("No feasible discrete candidate", result.message);
    assertSame(initial, subsea.getLineDesigns().get("production-flowline"));
    assertEquals(0.0, selection.getValue(network), 1.0e-12);
    assertEquals(initial.getResolvedInnerDiameterM(), network.getPipe("production-flowline").getDiameter(), 1.0e-12);
  }

  /** Build one complete rigid route alternative. */
  private LineDesign lineDesign(double outsideDiameterInches, double wallThicknessMm, String grade,
      String installationMethod, double insulationThicknessMm, double submergedWeightNPerM) {
    double outsideDiameterM = outsideDiameterInches * 0.0254;
    return LineDesign.builder(outsideDiameterInches).wallThicknessMm(wallThicknessMm).materialGrade(grade)
        .installationMethod(installationMethod)
        .insulation(RadialThermalLayer.MaterialType.PU_FOAM, insulationThicknessMm).thermalBoundary(4.0, 300.0, 100.0)
        .dnvStF101Input(dnvInput(outsideDiameterM, wallThicknessMm / 1000.0))
        .onBottomStabilityInput(stabilityInput(outsideDiameterM, submergedWeightNPerM)).build();
  }

  /** Build the same explicit pressure/load case for each geometric alternative. */
  private DnvStF101PipelineDesignInput dnvInput(double outsideDiameterM, double wallThicknessM) {
    return DnvStF101PipelineDesignInput.builder().safetyClass(SafetyClass.MEDIUM)
        .fabricationRoute(FabricationRoute.SEAMLESS).geometry(outsideDiameterM, wallThicknessM, 0.001)
        .fabrication(0.10, 0.005, 0.03, 1.0).material(448.0, 531.0, 207000.0, 0.30)
        .resistanceFactors(1.0, 1.0, 0.96, 1.15).pressures(25.0, 28.0, 1.0, 0.2, 29.0, 1.0).designLoads(20.0, 1.0, 0.0)
        .installationStrains(0.0, 0.001, 0.0, 0.02).fatigueCurve(12.0, 3.0, 1.0, 3.0).addFatigueBin(20.0, 1000.0)
        .build();
  }

  /** Build a complete conservative absolute-static stability load case for one outside diameter. */
  private DnvRpF109OnBottomStabilityInput stabilityInput(double outsideDiameterM, double submergedWeightNPerM) {
    LoadCase loadCase = LoadCase.builder().caseId("100-year storm").submergedWeightNPerM(submergedWeightNPerM)
        .currentVelocityMPerS(0.3).waveVelocityMPerS(0.5).waveAccelerationMPerS2(0.3)
        .currentDirectionRelativeToPipeDeg(90.0).waveDirectionRelativeToPipeDeg(90.0).dragCoefficient(1.0)
        .liftCoefficient(0.5).inertiaCoefficient(2.0).horizontalLoadReductionFactor(0.8)
        .verticalLoadReductionFactor(0.7).soilFrictionCoefficient(0.6).passiveSoilResistanceNPerM(1000.0)
        .horizontalSafetyFactor(1.1).verticalSafetyFactor(1.1).stormDurationHours(3.0).oscillationCount(1000.0)
        .lateralMethod(LateralMethod.ABSOLUTE_STATIC).build();
    return DnvRpF109OnBottomStabilityInput.builder().edition(StandardEdition.defaultEdition(StandardType.DNV_RP_F109))
        .assetType(AssetType.PIPELINE).equipmentType("Pipeline").outsideDiameterM(outsideDiameterM)
        .seawaterDensityKgM3(1025.0).gravitationalAccelerationMPerS2(9.81)
        .engineeringBasis("Synthetic environmental basis ENV-1 and soil basis GEO-1").addLoadCase(loadCase).build();
  }

  /** Build one pressure-driven multiphase route with explicit Beggs-Brill fidelity. */
  private FieldNetworkTopology topology() {
    SystemInterface gas = new SystemSrkEos(303.15, 100.0);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.08);
    gas.addComponent("n-heptane", 0.02);
    gas.setMixingRule("classic");

    FieldNetworkTopology topology = new FieldNetworkTopology("line-design alternatives");
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    network.setFluidTemplate(gas);
    network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
    network.setMaxIterations(100);
    network.setTolerance(100.0);
    topology.addPressureSource("well", "W-01", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 0.0, -300.0);
    topology.addFixedPressureSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 50.0, 0.0);
    topology.addPipe("production-flowline", "FL-01", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "well", "production", "host", "arrival", 5000.0, 0.203075, PipeModelType.BEGGS_BRILL);
    topology.validateForExecution();
    return topology;
  }
}
