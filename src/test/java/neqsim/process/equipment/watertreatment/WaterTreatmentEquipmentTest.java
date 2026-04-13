package neqsim.process.equipment.watertreatment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
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
    assertTrue(summary.contains("Hydrocyclone Design Validation"));
    assertTrue(summary.contains("dP adequate: OK"));
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
