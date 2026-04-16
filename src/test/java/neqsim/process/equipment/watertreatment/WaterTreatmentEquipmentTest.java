package neqsim.process.equipment.watertreatment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for water treatment equipment: hydrocyclone design validation and gas flotation unit.
 *
 * @author NeqSim
 */
public class WaterTreatmentEquipmentTest {

  // ============================================================================
  // Hydrocyclone design validation tests
  // ============================================================================

  @Test
  public void testHydrocycloneCreation() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");
    assertNotNull(hc);
    assertEquals(12.0, hc.getD50Microns(), 1e-10);
    assertEquals(0.02, hc.getRejectRatio(), 1e-10);
    assertEquals(2.0, hc.getPressureDropBar(), 1e-10);
  }

  @Test
  public void testDifferentialPressureCheck() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");

    // Default dP of 2.0 bar meets minimum requirement
    hc.setPressureDropBar(2.0);
    assertTrue(hc.isDifferentialPressureAdequate());

    // Below minimum
    hc.setPressureDropBar(1.5);
    assertFalse(hc.isDifferentialPressureAdequate());
  }

  @Test
  public void testRequiredInletPressure() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");
    hc.setPressureDropBar(5.0);

    // Water outlet at 3 bar, reject valve dP 1.0, line dP 0.5, height 0.3
    double requiredInlet = hc.calcRequiredInletPressure(3.0, 1.0, 0.5, 0.3);

    // 3.0 + 5.0 + 1.0 + 0.5 + 0.3 = 9.8
    assertEquals(9.8, requiredInlet, 0.001);
  }

  @Test
  public void testEfficiencyFromConditions() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");

    // At reference conditions (20C, 5 bar dP) -> base efficiency ~0.95
    double effRef = hc.estimateEfficiencyFromConditions(5.0, 20.0);
    assertEquals(0.95, effRef, 0.01);

    // Low dP (1 bar) reduces efficiency
    double effLowDP = hc.estimateEfficiencyFromConditions(1.0, 20.0);
    assertTrue(effLowDP < effRef);

    // Low temperature (5C) reduces efficiency
    double effCold = hc.estimateEfficiencyFromConditions(5.0, 5.0);
    assertTrue(effCold < effRef);

    // High dP and warm water maintains efficiency
    double effHighDP = hc.estimateEfficiencyFromConditions(8.0, 30.0);
    assertEquals(0.95, effHighDP, 0.01);
  }

  @Test
  public void testDropletSizeEfficiency() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");
    hc.setD50Microns(12.0);

    // At d50, efficiency should be about 50%
    double effAtD50 = hc.getEfficiencyForDropletSize(12.0);
    assertEquals(0.50, effAtD50, 0.02);

    // Much larger droplets -> near 100%
    double effLarge = hc.getEfficiencyForDropletSize(30.0);
    assertTrue(effLarge > 0.95);

    // Very small droplets -> low efficiency
    double effSmall = hc.getEfficiencyForDropletSize(3.0);
    assertTrue(effSmall < 0.10);
  }

  @Test
  public void testHydrocycloneDesignValidationSummary() {
    Hydrocyclone hc = new Hydrocyclone("HC-001");
    hc.setPressureDropBar(3.0);
    hc.setOilRemovalEfficiency(0.95);
    hc.setInletOilConcentration(500.0);

    String summary = hc.getDesignValidationSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Hydrocyclone Design"));
    assertTrue(summary.contains("dP adequate"));
  }

  // ============================================================================
  // Hydrocyclone sizing and capacity tests (v2.0)
  // ============================================================================

  @Test
  public void testLinerDiameterScaling() {
    Hydrocyclone hc = new Hydrocyclone("HC-002");

    // Default 35 mm liner => design flow 5.0 m3/h
    assertEquals(35.0, hc.getLinerDiameterMm(), 1e-10);
    assertEquals(5.0, hc.getDesignFlowPerLinerM3h(), 0.01);
    assertEquals(2.0, hc.getMinFlowPerLinerM3h(), 0.01);
    assertEquals(7.5, hc.getMaxFlowPerLinerM3h(), 0.01);

    // 45 mm liner => flow scales with D^2 => (45/35)^2 = 1.653
    hc.setLinerDiameterMm(45.0);
    double scale45 = Math.pow(45.0 / 35.0, 2.0);
    assertEquals(5.0 * scale45, hc.getDesignFlowPerLinerM3h(), 0.1);

    // 60 mm liner => (60/35)^2 = 2.939
    hc.setLinerDiameterMm(60.0);
    double scale60 = Math.pow(60.0 / 35.0, 2.0);
    assertEquals(5.0 * scale60, hc.getDesignFlowPerLinerM3h(), 0.1);
  }

  @Test
  public void testCalcNumberOfLiners() {
    Hydrocyclone hc = new Hydrocyclone("HC-003");
    // 35 mm liners, 5 m3/h each

    // 25 m3/h -> 5 liners
    int liners = hc.calcNumberOfLiners(25.0);
    assertEquals(5, liners);
    assertEquals(5, hc.getNumberOfLiners());

    // 23 m3/h -> 5 liners (round up)
    assertEquals(5, hc.calcNumberOfLiners(23.0));

    // 5 m3/h -> 1 liner
    assertEquals(1, hc.calcNumberOfLiners(5.0));

    // 100 m3/h -> 20 liners
    assertEquals(20, hc.calcNumberOfLiners(100.0));
  }

  @Test
  public void testCalcNumberOfVessels() {
    Hydrocyclone hc = new Hydrocyclone("HC-004");
    hc.setLinersPerVessel(6);
    hc.setNumberOfSpareLiners(2);

    // 10 active + 2 spare = 12 total, 6 per vessel -> 2 vessels
    hc.setNumberOfLiners(10);
    assertEquals(2, hc.calcNumberOfVessels());

    // 4 active + 2 spare = 6 total -> 1 vessel
    hc.setNumberOfLiners(4);
    assertEquals(1, hc.calcNumberOfVessels());

    // 11 active + 2 spare = 13 total -> 3 vessels
    hc.setNumberOfLiners(11);
    assertEquals(3, hc.calcNumberOfVessels());
  }

  @Test
  public void testTurndownRatio() {
    Hydrocyclone hc = new Hydrocyclone("HC-005");
    // 35 mm: max 7.5, min 2.0 => turndown 3.75:1
    double turndown = hc.getTurndownRatio();
    assertEquals(3.75, turndown, 0.01);
  }

  @Test
  public void testCapacityCalculations() {
    Hydrocyclone hc = new Hydrocyclone("HC-006");
    hc.setNumberOfLiners(10);

    // 10 liners * 5 m3/h = 50 m3/h design capacity
    assertEquals(50.0, hc.getMaxDesignCapacityM3h(), 0.01);

    // 10 * 2.0 = 20 m3/h min flow
    assertEquals(20.0, hc.getMinOperatingFlowM3h(), 0.01);

    // 10 * 7.5 = 75 m3/h max flow
    assertEquals(75.0, hc.getMaxOperatingFlowM3h(), 0.01);
  }

  @Test
  public void testD50Calculation() {
    Hydrocyclone hc = new Hydrocyclone("HC-007");
    hc.setWaterViscosity(1.0e-3);
    hc.setOilDensity(850.0);
    hc.setWaterDensity(1025.0);

    double d50 = hc.calcD50FromConditions();
    // Should be in the range 8-20 microns for typical conditions
    assertTrue(d50 > 5.0, "d50 should be > 5 microns");
    assertTrue(d50 < 30.0, "d50 should be < 30 microns");
  }

  @Test
  public void testDSDEfficiencyIntegration() {
    Hydrocyclone hc = new Hydrocyclone("HC-008");
    hc.setD50Microns(12.0);
    hc.setDv50Microns(30.0);
    hc.setGeometricStdDev(2.5);

    double efficiency = hc.calcEfficiencyFromDSD();
    // With dv50=30 and d50=12, most droplets are larger than d50
    // so efficiency should be well above 50%
    assertTrue(efficiency > 0.70, "Overall efficiency should be >70%");
    assertTrue(efficiency < 1.0, "Efficiency should be <100%");
  }

  @Test
  public void testPDRModel() {
    Hydrocyclone hc = new Hydrocyclone("HC-009");

    // PDR = 1.8 -> RR ~ 0.01 * 1.8^1.5 = 0.01 * 2.41 = 2.41%
    hc.setPDR(1.8);
    double rr = hc.calcRejectRatioFromPDR();
    assertEquals(0.0241, rr, 0.001);

    // PDR efficiency factor at 1.8 should be near optimum (~1.0)
    double factor = hc.getPDREfficiencyFactor();
    assertTrue(factor > 0.90);
    assertTrue(factor <= 1.0);

    // Low PDR = 1.1 -> poor efficiency factor
    hc.setPDR(1.1);
    assertTrue(hc.getPDREfficiencyFactor() < 0.75);
  }

  @Test
  public void testOSPARCompliance() {
    Hydrocyclone hc = new Hydrocyclone("HC-010");
    hc.setInletOilConcentration(500.0);
    hc.setOilRemovalEfficiency(0.95);

    // Outlet = 500 * 0.05 = 25 mg/L -> OSPAR compliant (<30)
    // Need to trigger run or manually set outletOilMgL
    // We'll just manually set for this unit test
    hc.setD50Microns(12.0);
    // Create a simple process to run it
    SystemSrkEos water = new SystemSrkEos(273.15 + 60.0, 5.0);
    water.addComponent("water", 0.99);
    water.addComponent("n-heptane", 0.01);
    water.setMixingRule("classic");
    Stream feed = new Stream("PW", water);
    feed.setFlowRate(50.0, "m3/hr");
    Hydrocyclone hc2 = new Hydrocyclone("HC-010b", feed);
    hc2.setOilRemovalEfficiency(0.95);
    hc2.setInletOilConcentration(500.0);
    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(hc2);
    ps.run();

    assertTrue(hc2.isOSPARCompliant(), "25 mg/L outlet should be OSPAR compliant");
  }

  @Test
  public void testSizingResults() {
    Hydrocyclone hc = new Hydrocyclone("HC-011");
    hc.setLinerDiameterMm(35.0);
    hc.setNumberOfLiners(8);
    hc.setNumberOfSpareLiners(2);
    hc.setLinersPerVessel(6);
    hc.setPDR(1.8);
    hc.setInletOilConcentration(800.0);
    hc.setOilRemovalEfficiency(0.93);

    Map<String, Object> sizing = hc.getSizingResults();
    assertNotNull(sizing);
    assertEquals(35.0, (Double) sizing.get("linerDiameterMm"), 0.001);
    assertEquals(8, sizing.get("activeLiners"));
    assertEquals(10, sizing.get("totalLiners"));
    assertTrue(sizing.containsKey("capacityUtilization"));
    assertTrue(sizing.containsKey("osparCompliant"));
    assertTrue(sizing.containsKey("turndownRatio"));
  }

  @Test
  public void testAutoSizeWithStream() {
    SystemSrkEos water = new SystemSrkEos(273.15 + 60.0, 5.0);
    water.addComponent("water", 0.99);
    water.addComponent("n-heptane", 0.01);
    water.setMixingRule("classic");
    Stream feed = new Stream("PW", water);
    feed.setFlowRate(50.0, "m3/hr");

    Hydrocyclone hc = new Hydrocyclone("HC-012", feed);
    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(hc);
    ps.run();

    hc.autoSize();

    // After auto-size, number of liners should be reasonable (>0)
    assertTrue(hc.getNumberOfLiners() >= 1, "Should have at least 1 liner");
    assertTrue(hc.getFeedFlowM3h() > 0.0, "Feed flow should be positive");
  }

  // ============================================================================
  // Gas Flotation Unit tests
  // ============================================================================

  @Test
  public void testGasFlotationUnitCreation() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    assertNotNull(gfu);
    assertEquals(4, gfu.getNumberOfStages());
    assertEquals(0.90, gfu.getOilRemovalEfficiency(), 1e-10);
    assertEquals("fuel_gas", gfu.getFlotationGasType());
  }

  @Test
  public void testPerStageEfficiency() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setNumberOfStages(4);
    gfu.setOilRemovalEfficiency(0.90);

    double perStage = gfu.calcPerStageEfficiency();

    // (1 - 0.90) = (1 - eta)^4 => eta = 1 - (0.10)^(1/4) = 1 - 0.5623 = 0.4377
    assertEquals(0.4377, perStage, 0.001);

    // Verify: (1 - 0.4377)^4 = 0.5623^4 = 0.09998 -> overall = 1 - 0.10 = 0.90
    double verifyOverall = 1.0 - Math.pow(1.0 - perStage, 4);
    assertEquals(0.90, verifyOverall, 0.001);
  }

  @Test
  public void testMinimumGasFlowRate() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setWaterFlowRate(200.0); // m3/h

    double minGas = gfu.calcMinimumGasFlowRate();

    // 10 Avol% of 200 m3/h = 20 Am3/h
    assertEquals(20.0, minGas, 0.001);
  }

  @Test
  public void testRejectFlowPerStage() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setNumberOfStages(4);
    gfu.setWaterFlowRate(100.0);

    double rejectPerStage = gfu.calcRejectFlowPerStage();

    // 2% of 100 = 2 m3/h per stage
    assertEquals(2.0, rejectPerStage, 0.001);

    // Total reject = 2 * 4 = 8 m3/h
    assertEquals(8.0, gfu.getTotalRejectFlow(), 0.001);
  }

  @Test
  public void testOutletOilConcentration() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setInletOilConcentration(200.0);
    gfu.setOilRemovalEfficiency(0.90);
    gfu.setWaterFlowRate(100.0);

    gfu.run();

    assertEquals(20.0, gfu.getOutletOilMgL(), 0.1);
  }

  @Test
  public void testNitrogenWarning() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setFlotationGasType("nitrogen");
    gfu.setWaterFlowRate(100.0);
    gfu.setInletOilConcentration(200.0);
    gfu.run();

    String summary = gfu.getDesignValidationSummary();
    assertTrue(summary.contains("nitrogen"));
    assertTrue(summary.contains("corrosion"));
  }

  @Test
  public void testDesignSummaryOutput() {
    GasFlotationUnit gfu = new GasFlotationUnit("IGF-001");
    gfu.setNumberOfStages(3);
    gfu.setWaterFlowRate(150.0);
    gfu.setInletOilConcentration(300.0);
    gfu.run();

    String summary = gfu.getDesignValidationSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Gas Flotation Unit Design Validation"));
    assertTrue(summary.contains("3"));
    assertTrue(summary.contains("reject"));
  }

  // ============================================================================
  // ProcessSystem integration tests
  // ============================================================================

  @Test
  public void testGasFlotationUnitInProcessSystem() {
    // Build a produced water fluid with water + small oil component
    SystemSrkEos producedWater = new SystemSrkEos(273.15 + 60.0, 5.0);
    producedWater.addComponent("water", 0.99);
    producedWater.addComponent("n-heptane", 0.01);
    producedWater.setMixingRule("classic");

    Stream pwStream = new Stream("Produced Water", producedWater);
    pwStream.setFlowRate(200.0, "m3/hr");

    GasFlotationUnit gfu = new GasFlotationUnit("IGF-100", pwStream);
    gfu.setNumberOfStages(3);
    gfu.setOilRemovalEfficiency(0.85);
    gfu.setInletOilConcentration(150.0);
    gfu.setWaterFlowRate(200.0);

    ProcessSystem process = new ProcessSystem();
    process.add(pwStream);
    process.add(gfu);
    process.run();

    // Verify GasFlotationUnit runs without error
    List<StreamInterface> inlets = gfu.getInletStreams();
    assertFalse(inlets.isEmpty());

    List<StreamInterface> outlets = gfu.getOutletStreams();
    assertFalse(outlets.isEmpty());

    // Oil removal should have been calculated
    double outletOil = gfu.getOutletOilMgL();
    assertTrue(outletOil < 150.0, "Outlet oil should be less than inlet");
  }

  @Test
  public void testHydrocycloneInProcessSystem() {
    SystemSrkEos producedWater = new SystemSrkEos(273.15 + 70.0, 10.0);
    producedWater.addComponent("water", 0.98);
    producedWater.addComponent("n-heptane", 0.02);
    producedWater.setMixingRule("classic");

    Stream pwStream = new Stream("PW Feed", producedWater);
    pwStream.setFlowRate(100.0, "m3/hr");

    Hydrocyclone hc = new Hydrocyclone("HC-100", pwStream);

    ProcessSystem process = new ProcessSystem();
    process.add(pwStream);
    process.add(hc);
    process.run();

    // Hydrocyclone inherits Separator - should have streams wired
    List<StreamInterface> inlets = hc.getInletStreams();
    assertFalse(inlets.isEmpty());

    List<StreamInterface> outlets = hc.getOutletStreams();
    assertFalse(outlets.isEmpty());
  }
}
