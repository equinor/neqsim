package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.engineering.calculation.EngineeringCalculationResult;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
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

/**
 * Acceptance tests for route-identified structural and stability screening.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class SubseaLineMechanicalScreeningTest {
  /** Pipe outside diameter in metres for an 8.625 inch line. */
  private static final double OUTSIDE_DIAMETER_M = 8.625 * 0.0254;

  /** Explicit nominal wall thickness in metres. */
  private static final double WALL_THICKNESS_M = 0.008;

  /**
   * Evaluate both existing screening kernels without changing canonical hydraulic fidelity.
   */
  @Test
  void evaluatesExplicitScreeningInputsByCanonicalRouteIdentity() {
    FieldNetworkTopology topology = topology();
    PipeModelType originalModel = topology.getHydraulicNetwork().getPipe("production-flowline").getHydraulicModelType();
    DnvStF101PipelineDesignInput limitStateInput = dnvInput(8.0, 8.8);
    DnvRpF109OnBottomStabilityInput stabilityInput = stabilityInput(OUTSIDE_DIAMETER_M);
    LineDesign design = LineDesign.builder(8.625).wallThicknessMm(8.0).dnvStF101Input(limitStateInput)
        .onBottomStabilityInput(stabilityInput).build();
    SubseaProductionSystem subsea = new SubseaProductionSystem("mechanical screening");
    subsea.setLineDesign("production-flowline", design);

    Map<String, EngineeringCalculationResult<DnvStF101PipelineAssessment>> limitStates = subsea
        .assessDnvStF101LineDesigns(topology);
    Map<String, EngineeringCalculationResult<DnvRpF109OnBottomStabilityAssessment>> stability = subsea
        .assessOnBottomStabilityLineDesigns(topology);

    assertEquals(1, limitStates.size());
    assertEquals(1, stability.size());
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        limitStates.get("production-flowline").getStatus());
    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED,
        stability.get("production-flowline").getStatus());
    assertTrue(limitStates.get("production-flowline").getValue().areAllScreeningChecksPassing());
    assertTrue(stability.get("production-flowline").getValue().areAllScreeningChecksPassing());
    assertEquals(9, limitStates.get("production-flowline").getValue().getChecks().size());
    assertEquals(2, stability.get("production-flowline").getValue().getChecks().size());
    assertEquals("production-flowline", limitStates.get("production-flowline").getContext().getDesignCaseId());
    assertSame(limitStateInput, design.getDnvStF101Input());
    assertSame(stabilityInput, design.getOnBottomStabilityInput());
    assertEquals(originalModel, topology.getHydraulicNetwork().getPipe("production-flowline").getHydraulicModelType());
    assertThrows(UnsupportedOperationException.class, limitStates::clear);
  }

  /**
   * Reject structural inputs that do not describe the exact registered line geometry.
   */
  @Test
  void rejectsGeometryMismatchAndIncompleteProjectInput() {
    DnvStF101PipelineDesignInput outsideDiameterMismatch = DnvStF101PipelineDesignInput.builder()
        .geometry(0.25, WALL_THICKNESS_M, 0.001).build();
    DnvStF101PipelineDesignInput wallMismatch = DnvStF101PipelineDesignInput.builder()
        .geometry(OUTSIDE_DIAMETER_M, 0.010, 0.001).build();

    assertThrows(IllegalArgumentException.class,
        () -> LineDesign.builder(8.625).wallThicknessMm(8.0).dnvStF101Input(outsideDiameterMismatch).build());
    assertThrows(IllegalArgumentException.class,
        () -> LineDesign.builder(8.625).wallThicknessMm(8.0).dnvStF101Input(wallMismatch).build());
    assertThrows(IllegalArgumentException.class,
        () -> LineDesign.builder(8.625).flexible(true).dnvStF101Input(dnvInput(8.0, 8.8)).build());
    assertThrows(IllegalArgumentException.class,
        () -> LineDesign.builder(8.625).wallThicknessMm(8.0).onBottomStabilityInput(stabilityInput(0.25)).build());

    DnvStF101PipelineDesignInput incomplete = DnvStF101PipelineDesignInput.builder()
        .geometry(OUTSIDE_DIAMETER_M, WALL_THICKNESS_M, 0.001).build();
    LineDesign design = LineDesign.builder(8.625).wallThicknessMm(8.0).dnvStF101Input(incomplete).build();
    SubseaProductionSystem subsea = new SubseaProductionSystem("fail closed");
    subsea.setLineDesign("production-flowline", design);
    EngineeringCalculationResult<DnvStF101PipelineAssessment> blocked = subsea.assessDnvStF101LineDesigns(topology())
        .get("production-flowline");
    assertEquals(EngineeringCalculationResult.Status.BLOCKED, blocked.getStatus());
    assertFalse(blocked.getReadiness().isReady());
  }

  /**
   * Preserve a failed engineering check as evidence instead of clipping or approving the design.
   */
  @Test
  void returnsFailedLimitStateAssessmentWithoutMutatingTopology() {
    FieldNetworkTopology topology = topology();
    LineDesign design = LineDesign.builder(8.625).wallThicknessMm(8.0).dnvStF101Input(dnvInput(30.0, 35.0)).build();
    SubseaProductionSystem subsea = new SubseaProductionSystem("failed screening");
    subsea.setLineDesign("production-flowline", design);

    EngineeringCalculationResult<DnvStF101PipelineAssessment> result = subsea.assessDnvStF101LineDesigns(topology)
        .get("production-flowline");
    DnvStF101PipelineAssessment assessment = result.getValue();

    assertEquals(EngineeringCalculationResult.Status.CALCULATED_REVIEW_REQUIRED, result.getStatus());
    assertFalse(assessment.areAllScreeningChecksPassing());
    assertTrue(assessment.getGoverningCheck().getUtilization() > 1.0);
    assertEquals(0.203075, topology.getHydraulicNetwork().getPipe("production-flowline").getDiameter(), 1.0e-12);
  }

  /**
   * Build a complete explicit DNV-ST-F101 screening input.
   *
   * @param operatingPressureMPa operating pressure in MPa
   * @param incidentalPressureMPa incidental pressure in MPa
   * @return complete project input
   */
  private DnvStF101PipelineDesignInput dnvInput(double operatingPressureMPa, double incidentalPressureMPa) {
    return DnvStF101PipelineDesignInput.builder().safetyClass(SafetyClass.MEDIUM)
        .fabricationRoute(FabricationRoute.SEAMLESS).geometry(OUTSIDE_DIAMETER_M, WALL_THICKNESS_M, 0.001)
        .fabrication(0.10, 0.005, 0.03, 1.0).material(448.0, 531.0, 207000.0, 0.30)
        .resistanceFactors(1.0, 1.0, 0.96, 1.15)
        .pressures(operatingPressureMPa, incidentalPressureMPa, 1.0, 0.2, incidentalPressureMPa + 1.0, 1.0)
        .designLoads(20.0, 1.0, 0.0).installationStrains(0.0, 0.001, 0.0, 0.02).fatigueCurve(12.0, 3.0, 1.0, 3.0)
        .addFatigueBin(20.0, 1000.0).build();
  }

  /**
   * Build a complete explicit DNV-RP-F109 absolute-static load case.
   *
   * @param outsideDiameterM hydrodynamic outside diameter in metres
   * @return complete project input
   */
  private DnvRpF109OnBottomStabilityInput stabilityInput(double outsideDiameterM) {
    LoadCase loadCase = LoadCase.builder().caseId("100-year storm").submergedWeightNPerM(5000.0)
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

  /**
   * Build one canonical route with explicit Beggs-Brill fidelity.
   *
   * @return canonical field topology
   */
  private FieldNetworkTopology topology() {
    FieldNetworkTopology topology = new FieldNetworkTopology("screened tieback");
    topology.addPressureSource("well", "W-01", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, 100.0, 3600.0, -100.0);
    topology.addDemandSink("host", "HOST-01", NodeRole.HOST, Service.PRODUCTION, 3600.0, -100.0);
    topology.addPipe("production-flowline", "FL-01", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "well", "out", "host", "in", 5000.0, 0.203075, PipeModelType.BEGGS_BRILL);
    return topology;
  }
}
