package neqsim.process.equipment.adsorber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.adsorber.MercuryRemovalMechanicalDesign;
import neqsim.process.costestimation.adsorber.MercuryRemovalCostEstimate;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the MercuryRemovalBed unit operation.
 *
 * @author Even Solbraa
 */
public class MercuryRemovalBedTest {
  private SystemInterface testGas;
  private StreamInterface feedStream;
  private MercuryRemovalBed bed;

  /**
   * Set up test fixtures with a natural gas containing trace mercury.
   */
  @BeforeEach
  public void setUp() {
    testGas = new SystemSrkEos(273.15 + 30.0, 60.0);
    testGas.addComponent("methane", 0.85);
    testGas.addComponent("ethane", 0.07);
    testGas.addComponent("propane", 0.03);
    testGas.addComponent("nitrogen", 0.04);
    testGas.addComponent("mercury", 1.0e-9);
    testGas.createDatabase(true);
    testGas.setMixingRule(2);
    testGas.init(0);

    feedStream = new Stream("feed", testGas);
    feedStream.setFlowRate(50000.0, "kg/hr");
    feedStream.run();

    bed = new MercuryRemovalBed("HgGuardBed", feedStream);
    bed.setBedDiameter(1.5);
    bed.setBedLength(4.0);
    bed.setVoidFraction(0.40);
    bed.setSorbentType("PuraSpec");
    bed.setSorbentBulkDensity(1100.0);
    bed.setParticleDiameter(0.004);
    bed.setMaxMercuryCapacity(100000.0);
    bed.setReactionRateConstant(0.5);
  }

  // =============================================
  // Construction tests
  // =============================================

  /**
   * Test default construction.
   */
  @Test
  public void testDefaultConstruction() {
    MercuryRemovalBed simpleBed = new MercuryRemovalBed("SimpleBed");
    assertEquals("SimpleBed", simpleBed.getName());
    assertNotNull(simpleBed.toJson());
  }

  /**
   * Test construction with inlet stream.
   */
  @Test
  public void testConstructionWithStream() {
    assertNotNull(bed.getInletStream());
    assertNotNull(bed.getOutletStream());
    assertEquals("HgGuardBed", bed.getName());
  }

  // =============================================
  // Geometry tests
  // =============================================

  /**
   * Test bed geometry getters and setters.
   */
  @Test
  public void testGeometryConfiguration() {
    bed.setBedDiameter(2.0);
    bed.setBedLength(5.0);
    bed.setVoidFraction(0.38);
    bed.setParticleDiameter(0.005);

    assertEquals(2.0, bed.getBedDiameter(), 1e-10);
    assertEquals(5.0, bed.getBedLength(), 1e-10);
    assertEquals(0.38, bed.getVoidFraction(), 1e-10);
    assertEquals(0.005, bed.getParticleDiameter(), 1e-10);
  }

  /**
   * Test sorbent mass calculation.
   */
  @Test
  public void testSorbentMass() {
    bed.setBedDiameter(1.5);
    bed.setBedLength(4.0);
    bed.setVoidFraction(0.40);
    bed.setSorbentBulkDensity(1100.0);

    double expectedVolume = Math.PI / 4.0 * 1.5 * 1.5 * 4.0;
    double expectedMass = expectedVolume * (1.0 - 0.40) * 1100.0;
    assertEquals(expectedMass, bed.getSorbentMass(), 0.1);
  }

  /**
   * Test bed volume calculation.
   */
  @Test
  public void testBedVolume() {
    bed.setBedDiameter(2.0);
    bed.setBedLength(5.0);
    double expectedVolume = Math.PI / 4.0 * 4.0 * 5.0;
    assertEquals(expectedVolume, bed.getBedVolume(), 1e-6);
  }

  // =============================================
  // Steady-state tests
  // =============================================

  /**
   * Test steady-state mercury removal.
   */
  @Test
  public void testSteadyStateMercuryRemoval() {
    bed.run(UUID.randomUUID());

    StreamInterface outlet = bed.getOutletStream();
    assertNotNull(outlet);

    double efficiency = bed.getRemovalEfficiency();
    assertTrue(efficiency > 0.3, "Mercury removal efficiency should exceed 30%, got " + efficiency);
    assertTrue(efficiency <= 1.0, "Efficiency should not exceed 100%");
  }

  /**
   * Test that mercury moles decrease in the outlet.
   */
  @Test
  public void testOutletMercuryReduction() {
    bed.run(UUID.randomUUID());

    double inletHgMoles = feedStream.getThermoSystem().getPhase(0).getComponent("mercury")
        .getNumberOfmoles();
    double outletHgMoles = bed.getOutletStream().getThermoSystem().getPhase(0)
        .getComponent("mercury").getNumberOfmoles();

    assertTrue(outletHgMoles < inletHgMoles,
        "Outlet mercury should be less than inlet: in=" + inletHgMoles + " out=" + outletHgMoles);
  }

  /**
   * Test pressure drop is calculated.
   */
  @Test
  public void testPressureDrop() {
    bed.setCalculatePressureDrop(true);
    bed.run(UUID.randomUUID());

    double dp = bed.getPressureDrop();
    assertTrue(dp > 0, "Pressure drop should be positive");
    assertTrue(dp < 5e5, "Pressure drop should be reasonable (< 5 bar)");

    double dpBar = bed.getPressureDrop("bar");
    assertEquals(dp / 1e5, dpBar, 1e-10);
  }

  // =============================================
  // Degradation tests
  // =============================================

  /**
   * Test that degradation reduces removal efficiency.
   */
  @Test
  public void testDegradationReducesEfficiency() {
    // Fresh bed
    bed.setDegradationFactor(1.0);
    bed.setBypassFraction(0.0);
    bed.run(UUID.randomUUID());
    double freshEfficiency = bed.getRemovalEfficiency();

    // Degraded bed
    MercuryRemovalBed degradedBed = new MercuryRemovalBed("DegradedBed", feedStream);
    degradedBed.setBedDiameter(1.5);
    degradedBed.setBedLength(4.0);
    degradedBed.setVoidFraction(0.40);
    degradedBed.setSorbentBulkDensity(1100.0);
    degradedBed.setParticleDiameter(0.004);
    degradedBed.setMaxMercuryCapacity(100000.0);
    degradedBed.setReactionRateConstant(0.5);
    degradedBed.setDegradationFactor(0.5);
    degradedBed.setBypassFraction(0.1);
    degradedBed.run(UUID.randomUUID());
    double degradedEfficiency = degradedBed.getRemovalEfficiency();

    assertTrue(degradedEfficiency < freshEfficiency,
        "Degraded bed should have lower efficiency: fresh=" + freshEfficiency + " degraded="
            + degradedEfficiency);
  }

  /**
   * Test bypass fraction limits.
   */
  @Test
  public void testBypassFractionLimits() {
    bed.setBypassFraction(-0.1);
    assertEquals(0.0, bed.getBypassFraction(), 1e-10);

    bed.setBypassFraction(1.5);
    assertEquals(0.99, bed.getBypassFraction(), 1e-10);
  }

  // =============================================
  // Transient simulation tests
  // =============================================

  /**
   * Test transient grid initialisation.
   */
  @Test
  public void testTransientInitialisation() {
    bed.setNumberOfCells(20);
    bed.initialiseTransientGrid();

    double[] loading = bed.getLoadingProfile();
    assertEquals(20, loading.length);
    for (double q : loading) {
      assertEquals(0.0, q, 1e-15, "Fresh bed should have zero loading");
    }
  }

  /**
   * Test bed pre-loading.
   */
  @Test
  public void testPreloadBed() {
    bed.setNumberOfCells(10);
    bed.preloadBed(0.5);

    double avgLoading = bed.getAverageLoading();
    double expectedLoading = 0.5 * bed.getMaxMercuryCapacity() * bed.getDegradationFactor();
    assertEquals(expectedLoading, avgLoading, 1.0);
  }

  /**
   * Test transient simulation accumulates mercury on sorbent.
   */
  @Test
  public void testTransientLoadingAccumulation() {
    bed.setCalculateSteadyState(false);
    bed.setNumberOfCells(10);
    bed.setCalculatePressureDrop(false);

    UUID calcId = UUID.randomUUID();

    // Run several time steps
    for (int i = 0; i < 10; i++) {
      bed.runTransient(60.0, calcId); // 60s steps
    }

    double avgLoading = bed.getAverageLoading();
    assertTrue(avgLoading > 0, "Sorbent should accumulate mercury loading, got " + avgLoading);
    assertTrue(bed.getElapsedTimeHours() > 0, "Elapsed time should advance");
  }

  /**
   * Test that bed utilisation increases over time.
   */
  @Test
  public void testBedUtilisationIncreases() {
    bed.setCalculateSteadyState(false);
    bed.setNumberOfCells(10);
    bed.setCalculatePressureDrop(false);

    UUID calcId = UUID.randomUUID();

    bed.runTransient(60.0, calcId);
    double util1 = bed.getBedUtilisation();

    for (int i = 0; i < 20; i++) {
      bed.runTransient(60.0, calcId);
    }
    double util2 = bed.getBedUtilisation();

    assertTrue(util2 >= util1,
        "Bed utilisation should not decrease: t1=" + util1 + " t2=" + util2);
  }

  /**
   * Test bed reset.
   */
  @Test
  public void testResetBed() {
    bed.setCalculateSteadyState(false);
    bed.setNumberOfCells(10);
    bed.setCalculatePressureDrop(false);

    UUID calcId = UUID.randomUUID();
    bed.runTransient(60.0, calcId);
    assertTrue(bed.getAverageLoading() > 0);

    bed.resetBed();
    assertEquals(0.0, bed.getElapsedTimeHours(), 1e-10);
    assertFalse(bed.isBreakthroughOccurred());
    assertEquals(0, bed.getLoadingProfile().length);
  }

  // =============================================
  // Bed lifetime estimation
  // =============================================

  /**
   * Test bed lifetime estimation returns a positive value.
   */
  @Test
  public void testBedLifetimeEstimate() {
    bed.run(UUID.randomUUID());
    double lifetime = bed.estimateBedLifetime();
    assertTrue(lifetime > 0, "Bed lifetime should be positive, got " + lifetime);
  }

  // =============================================
  // JSON reporting
  // =============================================

  /**
   * Test JSON report contains expected fields.
   */
  @Test
  public void testJsonReport() {
    bed.run(UUID.randomUUID());
    String json = bed.toJson();
    assertNotNull(json);
    assertTrue(json.contains("MercuryRemovalBed"));
    assertTrue(json.contains("geometry"));
    assertTrue(json.contains("sorbent"));
    assertTrue(json.contains("kinetics"));
    assertTrue(json.contains("degradation"));
    assertTrue(json.contains("operating"));
  }

  // =============================================
  // Mechanical design tests
  // =============================================

  /**
   * Test mechanical design calculation.
   */
  @Test
  public void testMechanicalDesign() {
    bed.run(UUID.randomUUID());

    MercuryRemovalMechanicalDesign mechDesign = bed.getMechanicalDesign();
    mechDesign.setMaxOperationPressure(60.0);
    mechDesign.setMaxOperationTemperature(273.15 + 60.0);
    mechDesign.calcDesign();

    assertTrue(mechDesign.getWallThickness() > 0,
        "Wall thickness should be positive: " + mechDesign.getWallThickness());
    assertTrue(mechDesign.getWeightTotal() > 0,
        "Total weight should be positive: " + mechDesign.getWeightTotal());
    assertTrue(mechDesign.getWeigthVesselShell() > 0,
        "Vessel shell weight should be positive");
    assertTrue(mechDesign.getSorbentChargeWeight() > 0,
        "Sorbent charge weight should be positive");
    assertTrue(mechDesign.getOuterDiameter() > mechDesign.innerDiameter,
        "Outer diameter should exceed inner diameter");
  }

  /**
   * Test mechanical design JSON output.
   */
  @Test
  public void testMechanicalDesignJson() {
    bed.run(UUID.randomUUID());

    MercuryRemovalMechanicalDesign mechDesign = bed.getMechanicalDesign();
    mechDesign.setMaxOperationPressure(60.0);
    mechDesign.calcDesign();

    String json = mechDesign.toJson();
    assertNotNull(json);
    assertTrue(json.contains("wallThickness_mm"));
    assertTrue(json.contains("billOfMaterials"));
    assertTrue(json.contains("Sorbent Charge"));
  }

  /**
   * Test bill of materials generation.
   */
  @Test
  public void testBillOfMaterials() {
    bed.run(UUID.randomUUID());

    MercuryRemovalMechanicalDesign mechDesign = bed.getMechanicalDesign();
    mechDesign.setMaxOperationPressure(60.0);
    mechDesign.calcDesign();

    List<Map<String, Object>> bom = mechDesign.generateBillOfMaterials();
    assertFalse(bom.isEmpty(), "BOM should not be empty");
    assertTrue(bom.size() >= 3, "BOM should have at least 3 items");
  }

  // =============================================
  // Cost estimation tests
  // =============================================

  /**
   * Test cost estimation.
   */
  @Test
  public void testCostEstimation() {
    bed.run(UUID.randomUUID());

    MercuryRemovalMechanicalDesign mechDesign = bed.getMechanicalDesign();
    mechDesign.setMaxOperationPressure(60.0);
    mechDesign.calcDesign();

    MercuryRemovalCostEstimate costEst = mechDesign.getCostEstimate();
    costEst.calculateCostEstimate();

    assertTrue(costEst.getPurchasedEquipmentCost() > 0,
        "PEC should be positive");
    assertTrue(costEst.getTotalModuleCost() > 0,
        "Total module cost should be positive");
    assertTrue(costEst.getSorbentReplacementCost() > 0,
        "Sorbent replacement cost should be positive");
  }

  /**
   * Test cost estimation JSON output.
   */
  @Test
  public void testCostEstimationJson() {
    bed.run(UUID.randomUUID());

    MercuryRemovalMechanicalDesign mechDesign = bed.getMechanicalDesign();
    mechDesign.setMaxOperationPressure(60.0);
    mechDesign.calcDesign();

    MercuryRemovalCostEstimate costEst = mechDesign.getCostEstimate();
    costEst.calculateCostEstimate();

    String json = costEst.toJson();
    assertNotNull(json);
    assertTrue(json.contains("capex"));
    assertTrue(json.contains("opex"));
    assertTrue(json.contains("sorbentReplacementCost_USD"));
  }

  // =============================================
  // Validation tests
  // =============================================

  /**
   * Test validation catches invalid configuration.
   */
  @Test
  public void testValidation() {
    MercuryRemovalBed badBed = new MercuryRemovalBed("BadBed", feedStream);
    badBed.setBedDiameter(-1.0);
    badBed.setVoidFraction(1.5);
    badBed.setMaxMercuryCapacity(-100.0);

    neqsim.util.validation.ValidationResult result = badBed.validateSetup();
    assertFalse(result.isValid(), "Validation should detect errors for invalid config");
  }
}
