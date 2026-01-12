package neqsim.process.fielddevelopment.subsea;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem.SubseaArchitecture;
import neqsim.process.fielddevelopment.subsea.SubseaProductionSystem.SubseaSystemResult;
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
    subsea.setArchitecture(SubseaArchitecture.DIRECT_TIEBACK).setWaterDepthM(450.0)
        .setTiebackDistanceKm(30.0).setWellCount(6).setFlowlineDiameterInches(10.0);

    assertEquals(SubseaArchitecture.DIRECT_TIEBACK, subsea.getArchitecture());
    assertEquals(450.0, subsea.getWaterDepthM(), 0.1);
    assertEquals(30.0, subsea.getTiebackDistanceKm(), 0.1);
    assertEquals(6, subsea.getWellCount());
    assertEquals(10.0, subsea.getFlowlineDiameterInches(), 0.1);
  }

  @Test
  public void testSubseaCapexEstimation() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("CAPEX Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0)
        .setTiebackDistanceKm(25.0).setWellCount(4).setFlowlineDiameterInches(12.0)
        .setReservoirFluid(gasFluid);

    subsea.build();

    // Verify wells and flowlines were created
    assertEquals(4, subsea.getWells().size());
    assertFalse(subsea.getFlowlines().isEmpty());
  }

  @Test
  public void testSubseaSystemResultCostBreakdown() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Cost Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0)
        .setTiebackDistanceKm(25.0).setWellCount(4).setManifoldCount(1)
        .setFlowlineDiameterInches(12.0).setRatePerWell(1.5e6).setWellheadConditions(180.0, 80.0)
        .setReservoirFluid(gasFluid);

    subsea.build();
    subsea.run();

    SubseaSystemResult result = subsea.getResult();
    assertNotNull(result);

    // Verify cost components
    assertTrue(result.getSubseaTreeCostMusd() > 0, "Tree cost should be positive");
    assertTrue(result.getManifoldCostMusd() > 0, "Manifold cost should be positive");
    assertTrue(result.getPipelineCostMusd() > 0, "Pipeline cost should be positive");
    assertTrue(result.getUmbilicalCostMusd() > 0, "Umbilical cost should be positive");

    // Verify total is sum of components
    double expectedTotal = result.getSubseaTreeCostMusd() + result.getManifoldCostMusd()
        + result.getPipelineCostMusd() + result.getUmbilicalCostMusd() + 4 * 3.0 + 1 * 5.0; // Control
                                                                                            // system
                                                                                            // cost
                                                                                            // approximation

    assertEquals(expectedTotal, result.getTotalSubseaCapexMusd(), 5.0);

    // Verify reasonable cost ranges (4 wells, 25km tieback)
    assertEquals(100.0, result.getSubseaTreeCostMusd(), 10.0); // 4 × 25 MUSD
    assertEquals(35.0, result.getManifoldCostMusd(), 10.0); // 1 × 35 MUSD
    assertTrue(result.getPipelineCostMusd() > 50.0); // 25km × ~2.5+ MUSD/km
    assertTrue(result.getTotalSubseaCapexMusd() > 200.0); // Total should be > 200 MUSD
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
    shallow.setWaterDepthM(200.0).setTiebackDistanceKm(25.0).setWellCount(4)
        .setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid);
    shallow.build();
    shallow.run();

    // Deep water
    SubseaProductionSystem deep = new SubseaProductionSystem("Deep");
    deep.setWaterDepthM(1200.0).setTiebackDistanceKm(25.0).setWellCount(4)
        .setFlowlineDiameterInches(12.0).setReservoirFluid(gasFluid);
    deep.build();
    deep.run();

    // Deep water should have higher pipeline cost
    assertTrue(deep.getResult().getPipelineCostMusd() > shallow.getResult().getPipelineCostMusd(),
        "Deep water pipeline should cost more than shallow");
  }

  @Test
  public void testResultSummaryFormat() {
    SubseaProductionSystem subsea = new SubseaProductionSystem("Summary Test");
    subsea.setArchitecture(SubseaArchitecture.MANIFOLD_CLUSTER).setWaterDepthM(350.0)
        .setTiebackDistanceKm(25.0).setWellCount(4).setFlowlineDiameterInches(12.0)
        .setReservoirFluid(gasFluid);

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
