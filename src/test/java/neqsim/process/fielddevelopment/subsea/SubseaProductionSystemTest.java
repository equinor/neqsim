package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.costestimation.CostEstimateResult;
import neqsim.process.costestimation.EstimateClass;
import neqsim.process.costestimation.MaterialTakeOffItem;
import neqsim.process.equipment.network.FieldNetworkTopology;
import neqsim.process.equipment.network.FieldNetworkTopology.EdgeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.FlowDirection;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.network.LoopedPipeNetwork.PipeModelType;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem.SubseaArchitecture;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem.SubseaSystemResult;
import neqsim.process.mechanicaldesign.subsea.WellCostEstimator.WellLocationType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for SubseaProductionSystem class.
 *
 * @author ESOL
 */
public class SubseaProductionSystemTest {
  private SystemInterface gasFluid;

  @BeforeEach
  public void setUp() {
    // Create a simple gas condensate fluid
    gasFluid = new SystemSrkEos(353.15, 180.0); // 80°C, 180 bara
    gasFluid.addComponent("nitrogen", 0.01);
    gasFluid.addComponent("CO2", 0.02);
    gasFluid.addComponent("methane", 0.75);
    gasFluid.addComponent("ethane", 0.08);
    gasFluid.addComponent("propane", 0.05);
    gasFluid.addComponent("n-butane", 0.03);
    gasFluid.addComponent("n-pentane", 0.02);
    gasFluid.addComponent("n-hexane", 0.02);
    gasFluid.addComponent("n-heptane", 0.02);
    gasFluid.setMixingRule("classic");
    gasFluid.setMultiPhaseCheck(true);
  }

  @Test
  public void testSubseaSystemCreation() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Test Subsea");

    assertEquals("Test Subsea", subsea.getName());
    assertEquals(SubseaArchitecture.MANIFOLD_CLUSTER, subsea.getArchitecture());
    assertEquals(350.0, subsea.getWaterDepthM(), 0.1);
  }

  @Test
  public void testFluentConfiguration() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Configured Subsea");
    subsea.setArchitecture(SubseaArchitecture.DIRECT_TIEBACK).setWaterDepthM(450.0).setTiebackDistanceKm(30.0)
        .setWellCount(6).setFlowlineDiameterInches(10.0);

    assertEquals(SubseaArchitecture.DIRECT_TIEBACK, subsea.getArchitecture());
    assertEquals(450.0, subsea.getWaterDepthM(), 0.1);
    assertEquals(30.0, subsea.getTiebackDistanceKm(), 0.1);
    assertEquals(6, subsea.getWellCount());
    assertEquals(10.0, subsea.getFlowlineDiameterInches(), 0.1);
  }

  @Test
  public void testSubseaCapexEstimation() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("CAPEX Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid);

    subsea.build();

    // Verify wells and flowlines were created
    assertEquals(4, subsea.getWells().size());
    assertEquals(4, subsea.getTrees().size());
    assertEquals(4, subsea.getJumpers().size());
    assertEquals(1, subsea.getManifolds().size());
    assertFalse(subsea.getPLETs().isEmpty());
    assertEquals(1, subsea.getPLEMs().size());
    assertEquals(1, subsea.getUmbilicals().size());
    assertFalse(subsea.getRisers().isEmpty());
    assertTrue(subsea.getSteelRisers().isEmpty());
    assertFalse(subsea.getFlowlines().isEmpty());

    assertNotNull(subsea.getPLETs().get(0).getMechanicalDesign());
    assertNotNull(subsea.getPLEMs().get(0).getMechanicalDesign());
    assertNotNull(subsea.getUmbilicals().get(0).getMechanicalDesign());
    assertNotNull(subsea.getRisers().get(0).getMechanicalDesign());
  }

  @Test
  public void testSteelRiserCaseCreatesRigidRiserUnitOperations() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Steel Riser Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setFlowlineDiameterInches(12.0).setFlexibleRiser(false).setProductionRiserCount(2)
        .setReservoirFluid(gasFluid);

    subsea.build();
    subsea.run();

    assertTrue(subsea.getRisers().isEmpty());
    assertEquals(2, subsea.getSteelRisers().size());
    assertFalse(subsea.getPLETs().isEmpty());
    assertEquals(1, subsea.getPLEMs().size());
    assertTrue(subsea.getResult().getRiserCostMusd() > 0.0);
    assertNotNull(subsea.getSteelRisers().get(0).getMechanicalDesign());
  }

  @Test
  public void testSubseaSystemResultCostBreakdown() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Cost Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setManifoldCount(1).setFlowlineDiameterInches(12.0).setRatePerWell(1.5e6)
        .setWellheadConditions(180.0, 80.0).setReservoirFluid(gasFluid);

    subsea.build();
    subsea.run();

    SubseaSystemResult result = subsea.getResult();
    assertNotNull(result);

    // Verify cost components
    assertTrue(result.getSubseaTreeCostMusd() > 0, "Tree cost should be positive");
    assertTrue(result.getManifoldCostMusd() > 0, "Manifold cost should be positive");
    assertTrue(result.getJumperAndPletCostMusd() > 0, "Jumper and PLET cost should be positive");
    assertTrue(result.getPipelineCostMusd() > 0, "Pipeline cost should be positive");
    assertTrue(result.getUmbilicalCostMusd() > 0, "Umbilical cost should be positive");
    assertTrue(result.getRiserCostMusd() > 0, "Riser cost should be positive");
    assertTrue(result.getWellCostMusd() > 0, "Well cost should be positive");
    assertTrue(result.getReservoirCostMusd() > 0, "Reservoir cost should be positive");

    // Verify total is sum of components
    double expectedTotal = result.getSubseaTreeCostMusd() + result.getManifoldCostMusd() + result.getPipelineCostMusd()
        + result.getUmbilicalCostMusd() + result.getJumperAndPletCostMusd() + result.getRiserCostMusd();

    assertEquals(expectedTotal, result.getTotalSubseaCapexMusd(), 5.0);
    assertEquals(result.getTotalSubseaCapexMusd() + result.getWellCostMusd() + result.getReservoirCostMusd(),
        result.getTotalDevelopmentCapexMusd(), 5.0);

    // Verify reasonable cost ranges (4 wells, 25km tieback)
    assertTrue(result.getSubseaTreeCostMusd() > 100.0); // depth-adjusted wet trees
    assertTrue(result.getManifoldCostMusd() > 0.0); // 1 manifold
    assertTrue(result.getPipelineCostMusd() > 50.0); // 25km × ~2.5+ MUSD/km
    assertTrue(result.getTotalSubseaCapexMusd() > 200.0); // Total should be > 200 MUSD

    CostEstimateResult surfResult = result.getSurfDetailedEstimateResult();
    assertNotNull(surfResult, "Detailed SURF estimate should be exposed on the system result");
    assertEquals(EstimateClass.CLASS_4, surfResult.getBasis().getEstimateClass());
    assertEquals(result.getTotalSubseaCapexMusd() * 1.0e6, surfResult.getCapitalCostSummary().get("totalSURF"), 5.0e6);
    assertFalse(surfResult.getMaterialTakeOff().isEmpty(), "Detailed SURF estimate should include MTO lines");

    CostEstimateResult developmentResult = result.getDetailedDevelopmentEstimateResult();
    assertNotNull(developmentResult, "Detailed development estimate should be available");
    assertEquals(EstimateClass.CLASS_4, developmentResult.getBasis().getEstimateClass());
    assertEquals(result.getTotalDevelopmentCapexMusd() * 1.0e6,
        developmentResult.getCapitalCostSummary().get("totalDevelopment"), 5.0e6);
    assertEquals(result.getWellCostMusd() * 1.0e6, developmentResult.getCapitalCosts().get("wells"), 1.0e6);
    assertEquals(result.getTotalSubseaCapexMusd() * 1.0e6, developmentResult.getCapitalCostSummary().get("totalSURF"),
        5.0e6);
    assertFalse(developmentResult.getCapitalCosts().containsKey("totalDevelopment"),
        "Development totals should not be in additive capital costs");
    assertTrue(developmentResult.getMaterialTakeOff().size() >= surfResult.getMaterialTakeOff().size() + 2,
        "Development estimate should include reservoir, well and SURF MTO lines");

    boolean hasReservoirPlaceholder = false;
    boolean hasWellPlaceholder = false;
    boolean hasSurfPipelineMto = false;
    for (MaterialTakeOffItem item : developmentResult.getMaterialTakeOff()) {
      hasReservoirPlaceholder |= "reservoir".equals(item.getCategory());
      hasWellPlaceholder |= "wells".equals(item.getCategory());
      hasSurfPipelineMto |= "surf-pipeline-sizing".equals(item.getSource());
    }
    assertTrue(hasReservoirPlaceholder, "Development MTO should include reservoir/appraisal scope");
    assertTrue(hasWellPlaceholder, "Development MTO should include drilling and completion scope");
    assertTrue(hasSurfPipelineMto, "Development MTO should include detailed SURF pipeline quantities");
  }

  @Test
  public void testArchitectureTypes() {
    for (SubseaArchitecture arch : SubseaArchitecture.values()) {
      SubseaProductionSystem subsea = new SubseaProductionSystem("Arch Test " + arch);
      subsea.setArchitecture(arch).setWaterDepthM(300.0).setTiebackDistanceKm(20.0).setWellCount(4)
          .setReservoirFluid(gasFluid);

      subsea.build();

      assertEquals(arch, subsea.getArchitecture());
      assertEquals(4, subsea.getWells().size());
    }
  }

  @Test
  public void testDeepWaterCostFactor() {
    // Shallow water
    SubseaProductionSystem shallow = new SubseaProductionSystem("Shallow");
    shallow.setWaterDepthM(200.0).setTiebackDistanceKm(25.0).setWellCount(4).setFlowlineDiameterInches(12.0)
        .setReservoirFluid(gasFluid);
    shallow.build();
    shallow.run();

    // Deep water
    SubseaProductionSystem deep = new SubseaProductionSystem("Deep");
    deep.setWaterDepthM(1200.0).setTiebackDistanceKm(25.0).setWellCount(4).setFlowlineDiameterInches(12.0)
        .setReservoirFluid(gasFluid);
    deep.build();
    deep.run();

    // Deep water should have higher pipeline cost
    assertTrue(deep.getResult().getPipelineCostMusd() > shallow.getResult().getPipelineCostMusd(),
        "Deep water pipeline should cost more than shallow");
  }

  @Test
  public void testDryTreeCaseExcludesWetSubseaSurfScope() {
    SubseaProductionSystem wet = new SubseaProductionSystem("Wet Tree");
    wet.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid)
        .setWellLocationType(WellLocationType.SUBSEA_WET_TREE);
    wet.build();
    wet.run();

    SubseaProductionSystem dry = new SubseaProductionSystem("Dry Tree");
    dry.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid)
        .setWellLocationType(WellLocationType.PLATFORM_DRY_TREE);
    dry.build();
    dry.run();

    assertTrue(wet.getResult().getTotalSubseaCapexMusd() > 0.0);
    assertEquals(0.0, dry.getResult().getTotalSubseaCapexMusd(), 1.0e-12);
    assertTrue(wet.getResult().getWellCostMusd() > dry.getResult().getWellCostMusd());
    assertTrue(wet.getResult().getTotalDevelopmentCapexMusd() > dry.getResult().getTotalDevelopmentCapexMusd());
    assertEquals(WellLocationType.PLATFORM_DRY_TREE, dry.getResult().getWellLocationType());
    assertNull(dry.getResult().getSurfDetailedEstimateResult());

    CostEstimateResult dryDevelopmentResult = dry.getResult().getDetailedDevelopmentEstimateResult();
    assertEquals(EstimateClass.CLASS_4, dryDevelopmentResult.getBasis().getEstimateClass());
    assertEquals(dry.getResult().getTotalDevelopmentCapexMusd() * 1.0e6,
        dryDevelopmentResult.getCapitalCostSummary().get("totalDevelopment"), 1.0e6);
    assertTrue(dryDevelopmentResult.toJson().contains("No detailed SURF estimate"));
  }

  @Test
  public void testResultSummaryFormat() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Summary Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0).setTiebackDistanceKm(25.0)
        .setWellCount(4).setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid);

    subsea.build();
    subsea.run();

    String summary = subsea.getResult().getSummary();

    assertNotNull(summary);
    assertTrue(summary.contains("Subsea Production System"), "Should have title");
    assertTrue(summary.contains("Configuration"), "Should have configuration section");
    assertTrue(summary.contains("Operating Conditions"), "Should have operating section");
    assertTrue(summary.contains("Subsea CAPEX"), "Should have CAPEX section");
    assertTrue(summary.contains("Water Depth"), "Should show water depth");
    assertTrue(summary.contains("Tieback Distance"), "Should show tieback distance");
  }

  /** The same canonical production/injection topology must drive SURF geometry and cost quantities. */
  @Test
  public void testCanonicalFieldNetworkSurfDesignBasis() {
    FieldNetworkTopology topology = new FieldNetworkTopology("canonical field design");
    topology.addJunction("producer-1", "W-P1", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, -500.0);
    topology.addJunction("producer-2", "W-P2", NodeRole.PRODUCTION_WELL, Service.PRODUCTION, -500.0);
    topology.addJunction("injector-1", "W-I1", NodeRole.INJECTION_WELL, Service.INJECTION, -500.0);
    topology.addJunction("tree-p1", "XT-P1", NodeRole.TREE, Service.PRODUCTION, -500.0);
    topology.addJunction("tree-p2", "XT-P2", NodeRole.TREE, Service.PRODUCTION, -500.0);
    topology.addJunction("tree-i1", "XT-I1", NodeRole.TREE, Service.INJECTION, -500.0);
    topology.addJunction("production-template", "TE-P", NodeRole.TEMPLATE, Service.PRODUCTION, -500.0);
    topology.addJunction("injection-template", "TE-I", NodeRole.TEMPLATE, Service.INJECTION, -500.0);
    topology.addJunction("plem", "PLEM-1", NodeRole.PLEM, Service.PRODUCTION, -500.0);
    topology.addJunction("plet", "PLET-1", NodeRole.PLET, Service.PRODUCTION, -500.0);
    topology.addJunction("host", "HOST-1", NodeRole.HOST, Service.PRODUCTION, 0.0);
    topology.addJunction("injection-source", "HOST-I", NodeRole.INJECTION_SOURCE, Service.INJECTION, 0.0);

    topology.addPipe("jumper-p1", "J-P1", EdgeRole.JUMPER, Service.PRODUCTION, FlowDirection.FROM_TO, "producer-1",
        "outlet", "tree-p1", "well", 30.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("jumper-p2", "J-P2", EdgeRole.JUMPER, Service.PRODUCTION, FlowDirection.FROM_TO, "producer-2",
        "outlet", "tree-p2", "well", 30.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("jumper-i1", "J-I1", EdgeRole.JUMPER, Service.INJECTION, FlowDirection.FROM_TO, "tree-i1", "well",
        "injector-1", "inlet", 30.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("tie-in-p1", "TI-P1", EdgeRole.TIE_IN, Service.PRODUCTION, FlowDirection.FROM_TO, "tree-p1",
        "outlet", "production-template", "inlet-p1", 40.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("tie-in-p2", "TI-P2", EdgeRole.TIE_IN, Service.PRODUCTION, FlowDirection.FROM_TO, "tree-p2",
        "outlet", "production-template", "inlet-p2", 40.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("tie-in-i1", "TI-I1", EdgeRole.TIE_IN, Service.INJECTION, FlowDirection.FROM_TO,
        "injection-template", "outlet-i1", "tree-i1", "inlet", 40.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("production-flowline", "FL-P", EdgeRole.FLOWLINE, Service.PRODUCTION, FlowDirection.FROM_TO,
        "production-template", "outlet", "plem", "inlet", 1000.0, 0.2032, PipeModelType.BEGGS_BRILL);
    topology.addPipe("injection-flowline", "FL-I", EdgeRole.FLOWLINE, Service.INJECTION, FlowDirection.FROM_TO,
        "injection-source", "outlet", "injection-template", "inlet", 2000.0, 0.1524, PipeModelType.DARCY_WEISBACH);
    topology.addPipe("export-pipeline", "PL-P", EdgeRole.PIPELINE, Service.PRODUCTION, FlowDirection.FROM_TO, "plem",
        "outlet", "plet", "inlet", 10000.0, 0.3048, PipeModelType.BEGGS_BRILL);
    topology.addPipe("host-riser", "RI-P", EdgeRole.RISER, Service.PRODUCTION, FlowDirection.FROM_TO, "plet", "outlet",
        "host", "arrival", 750.0, 0.254, PipeModelType.BEGGS_BRILL);

    SubseaProductionSystem subsea = new SubseaProductionSystem("Canonical topology design");
    FieldNetworkSurfDesignBasis basis = subsea.createSurfDesignBasis(topology);

    assertEquals(2, basis.getProductionWellCount());
    assertEquals(1, basis.getInjectionWellCount());
    assertEquals(3, basis.getTreeCount());
    assertEquals(2, basis.getDistributionUnitCount());
    assertEquals(2, basis.getSlotsPerDistributionUnit());
    assertEquals(1, basis.getPletCount());
    assertEquals(1, basis.getPlemCount());
    assertEquals(3, basis.getJumperCount());
    assertEquals(90.0, basis.getTotalJumperLengthM(), 1.0e-12);
    assertEquals(6.0, basis.getJumperDiameterInches(), 1.0e-12);
    assertEquals(3.12, basis.getInfieldFlowlineLengthKm(), 1.0e-12);
    assertEquals(10.0, basis.getExportPipelineLengthKm(), 1.0e-12);
    assertEquals(1, basis.getProductionRiserCount());
    assertEquals(0, basis.getInjectionRiserCount());
    assertEquals(500.0, basis.getWaterDepthM(), 1.0e-12);

    CostEstimateResult estimate = subsea.estimateSurfCosts(topology);
    assertEquals(EstimateClass.CLASS_4, estimate.getBasis().getEstimateClass());
    assertTrue(estimate.getCapitalCostSummary().get("totalSURF") > 0.0);
    boolean hasThreeTrees = false;
    boolean hasTwoDistributionUnits = false;
    for (MaterialTakeOffItem item : estimate.getMaterialTakeOff()) {
      hasThreeTrees |= item.getItem().contains("Christmas Trees") && item.getQuantity() == 3.0;
      hasTwoDistributionUnits |= item.getItem().contains("Manifold/Template") && item.getQuantity() == 2.0;
    }
    assertTrue(hasThreeTrees, "Topology tree identities must set the priced tree quantity");
    assertTrue(hasTwoDistributionUnits, "Template and manifold identities must set the priced distribution quantity");
  }

  /** Generated architectures must price the built manifold count rather than an implicit single unit. */
  @Test
  public void testGeneratedArchitectureManifoldQuantities() {
    SubseaProductionSystem clustered = new SubseaProductionSystem("Two clusters");
    clustered.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(300.0).setTiebackDistanceKm(20.0)
        .setWellCount(4).setManifoldCount(2).setReservoirFluid(gasFluid);
    clustered.build();
    clustered.run();

    int pricedManifoldCount = 0;
    for (MaterialTakeOffItem item : clustered.getResult().getSurfDetailedEstimateResult().getMaterialTakeOff()) {
      if (item.getItem().contains("Manifold/Template")) {
        pricedManifoldCount += (int) item.getQuantity();
        assertTrue(item.getItem().contains("2-slot"));
      }
    }
    assertEquals(2, pricedManifoldCount);

    SubseaProductionSystem direct = new SubseaProductionSystem("Direct tiebacks");
    direct.setArchitecture(SubseaArchitecture.DIRECT_TIEBACK).setWaterDepthM(300.0).setTiebackDistanceKm(20.0)
        .setWellCount(2).setReservoirFluid(gasFluid);
    direct.build();
    direct.run();
    for (MaterialTakeOffItem item : direct.getResult().getSurfDetailedEstimateResult().getMaterialTakeOff()) {
      assertFalse(item.getItem().contains("Manifold/Template"), "Direct tiebacks must not price a manifold");
    }
  }

  @Test
  public void testValidationNoFluid() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("No Fluid");
    subsea.setWellCount(4).setTiebackDistanceKm(25.0);

    // Should throw when building without fluid
    assertThrows(IllegalStateException.class, () -> subsea.build());
  }

  @Test
  public void testValidationInvalidWellCount() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Invalid Wells");
    subsea.setWellCount(0).setReservoirFluid(gasFluid);

    assertThrows(IllegalStateException.class, () -> subsea.build());
  }

  @Test
  public void testValidationInvalidDistance() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Invalid Distance");
    subsea.setWellCount(4).setTiebackDistanceKm(-5.0).setReservoirFluid(gasFluid);

    assertThrows(IllegalStateException.class, () -> subsea.build());
  }
}
