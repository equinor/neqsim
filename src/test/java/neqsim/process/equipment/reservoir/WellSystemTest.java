package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for WellSystem integrated well model.
 *
 * @author NeqSim development team
 */
class WellSystemTest {
  private SystemInterface gasFluid;
  private Stream reservoirStream;

  @BeforeEach
  void setUp() {
    // Create a typical gas well fluid
    gasFluid = new SystemSrkEos(85.0, 250.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.08);
    gasFluid.addComponent("propane", 0.04);
    gasFluid.addComponent("n-butane", 0.02);
    gasFluid.addComponent("CO2", 0.01);
    gasFluid.setMixingRule("classic");
    gasFluid.init(0);

    reservoirStream = new Stream("reservoir", gasFluid);
    reservoirStream.setFlowRate(3.0, "MSm3/day");
    reservoirStream.setTemperature(85.0, "C");
    reservoirStream.setPressure(250.0, "bara");
    reservoirStream.run();
  }

  @Test
  void testBasicConstruction() {
    WellSystem well = new WellSystem("test_well");
    assertNotNull(well);
    assertEquals("test_well", well.getName());
  }

  @Test
  void testProductionIndexIPR() {
    WellSystem well = new WellSystem("pi_well");
    well.setReservoirStream(reservoirStream);
    well.setProductionIndex(2.5e-6, "Sm3/day/bar2");
    well.setIPRModel(WellSystem.IPRModel.PRODUCTION_INDEX);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(3000.0, "m");
    well.setPressureDropCorrelation(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);

    well.run();

    // Check that output stream was created
    assertNotNull(well.getOutletStream(), "Should have outlet stream");

    double flowRate = well.getOperatingFlowRate("MSm3/day");
    double bhp = well.getBottomHolePressure("bara");

    assertTrue(flowRate >= 0, "Flow rate should be non-negative");
    assertTrue(bhp >= 0, "BHP should be non-negative");
  }

  @Test
  void testVogelIPR() {
    WellSystem well = new WellSystem("vogel_well");
    well.setReservoirStream(reservoirStream);
    well.setVogelParameters(5.0, 150.0, 250.0); // qTest, pwfTest, pRes
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(2500.0, "m");

    well.run();

    assertNotNull(well.getOutletStream(), "Should have outlet stream with Vogel");
  }

  @Test
  void testFetkovichIPR() {
    WellSystem well = new WellSystem("fetkovich_well");
    well.setReservoirStream(reservoirStream);
    well.setFetkovichParameters(0.015, 0.85, 250.0); // C, n, pRes
    well.setIPRModel(WellSystem.IPRModel.FETKOVICH);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(3000.0, "m");

    well.run();

    assertNotNull(well.getOutletStream(), "Should have outlet stream with Fetkovich");
  }

  @Test
  void testSettersAndGetters() {
    WellSystem well = new WellSystem("getter_test");
    well.setReservoirStream(reservoirStream);

    well.setProductionIndex(3.5e-6, "Sm3/day/bar2");
    well.setWellheadPressure(55.0, "bara");
    well.setTubingDiameter(0.12, "m");
    well.setTubingLength(2800.0, "m");

    // Run to initialize internal components
    well.run();

    assertEquals(55.0, well.getWellheadPressure("bara"), 1.0);
  }

  @Test
  void testDrawdown() {
    WellSystem well = new WellSystem("drawdown_well");
    well.setReservoirStream(reservoirStream);
    well.setProductionIndex(3.0e-6, "Sm3/day/bar2");
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(2500.0, "m");

    well.run();

    double drawdown = well.getDrawdown("bar");
    assertTrue(drawdown >= 0, "Drawdown should be non-negative");
  }

  @Test
  void testIPRModelEnum() {
    assertNotNull(WellSystem.IPRModel.PRODUCTION_INDEX);
    assertNotNull(WellSystem.IPRModel.VOGEL);
    assertNotNull(WellSystem.IPRModel.FETKOVICH);
    assertNotNull(WellSystem.IPRModel.BACKPRESSURE);
    assertNotNull(WellSystem.IPRModel.TABLE);
  }

  @Test
  void testVLPSolverModeEnum() {
    assertNotNull(WellSystem.VLPSolverMode.SIMPLIFIED);
    assertNotNull(WellSystem.VLPSolverMode.BEGGS_BRILL);
    assertNotNull(WellSystem.VLPSolverMode.HAGEDORN_BROWN);
    assertNotNull(WellSystem.VLPSolverMode.GRAY);
    assertNotNull(WellSystem.VLPSolverMode.HASAN_KABIR);
    assertNotNull(WellSystem.VLPSolverMode.DUNS_ROS);
  }

  @Test
  void testVLPSolverModeSelection() {
    WellSystem well = new WellSystem("solver_mode_test");
    well.setReservoirStream(reservoirStream);
    well.setVogelParameters(5.0, 150.0, 250.0);
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(2500.0, "m");

    // Default should be SIMPLIFIED
    assertEquals(WellSystem.VLPSolverMode.SIMPLIFIED, well.getVLPSolverMode());

    // Test setting to BEGGS_BRILL
    well.setVLPSolverMode(WellSystem.VLPSolverMode.BEGGS_BRILL);
    assertEquals(WellSystem.VLPSolverMode.BEGGS_BRILL, well.getVLPSolverMode());
  }

  @Test
  void testPressureDropCorrelationEnum() {
    assertNotNull(TubingPerformance.PressureDropCorrelation.BEGGS_BRILL);
    assertNotNull(TubingPerformance.PressureDropCorrelation.HAGEDORN_BROWN);
    assertNotNull(TubingPerformance.PressureDropCorrelation.GRAY);
    assertNotNull(TubingPerformance.PressureDropCorrelation.HASAN_KABIR);
    assertNotNull(TubingPerformance.PressureDropCorrelation.DUNS_ROS);
  }

  @Test
  void testTemperatureModelEnum() {
    assertNotNull(TubingPerformance.TemperatureModel.ISOTHERMAL);
    assertNotNull(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
    assertNotNull(TubingPerformance.TemperatureModel.RAMEY);
    assertNotNull(TubingPerformance.TemperatureModel.HASAN_KABIR_ENERGY);
  }

  @Test
  void testDriftFluxVLPSolver() {
    // Test with a more realistic fluid (oil + gas)
    SystemInterface oilFluid = new SystemSrkEos(363.15, 100.0);
    oilFluid.addComponent("methane", 0.4);
    oilFluid.addComponent("n-heptane", 0.6);
    oilFluid.setMixingRule("classic");
    oilFluid.setTotalFlowRate(10000, "Sm3/day");
    Stream oilStream = new Stream("oil_reservoir", oilFluid);
    oilStream.run();

    WellSystem well = new WellSystem("drift_flux_test");
    well.setReservoirStream(oilStream);
    well.setVogelParameters(5.0, 150.0, 250.0);
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(2500.0, "m");
    well.setVLPSolverMode(WellSystem.VLPSolverMode.DRIFT_FLUX);

    assertEquals(WellSystem.VLPSolverMode.DRIFT_FLUX, well.getVLPSolverMode());

    // Solve - verify it runs without exception
    well.run();
    // Verify some reasonable output exists
    assertNotNull(well.getOutletStream());
  }

  @Test
  void testTwoFluidVLPSolver() {
    // Test with a more realistic fluid (oil + gas)
    SystemInterface oilFluid = new SystemSrkEos(363.15, 100.0);
    oilFluid.addComponent("methane", 0.4);
    oilFluid.addComponent("n-heptane", 0.6);
    oilFluid.setMixingRule("classic");
    oilFluid.setTotalFlowRate(10000, "Sm3/day");
    Stream oilStream = new Stream("oil_reservoir", oilFluid);
    oilStream.run();

    WellSystem well = new WellSystem("two_fluid_test");
    well.setReservoirStream(oilStream);
    well.setVogelParameters(5.0, 150.0, 250.0);
    well.setIPRModel(WellSystem.IPRModel.VOGEL);
    well.setWellheadPressure(50.0, "bara");
    well.setTubingDiameter(0.1, "m");
    well.setTubingLength(2500.0, "m");
    well.setVLPSolverMode(WellSystem.VLPSolverMode.TWO_FLUID);

    assertEquals(WellSystem.VLPSolverMode.TWO_FLUID, well.getVLPSolverMode());

    // Solve - verify it runs without exception
    well.run();
    // Verify some reasonable output exists
    assertNotNull(well.getOutletStream());
  }
}
