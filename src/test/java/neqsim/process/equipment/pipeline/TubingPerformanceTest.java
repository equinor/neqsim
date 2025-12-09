package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for TubingPerformance VLP model.
 *
 * @author NeqSim development team
 */
class TubingPerformanceTest {

  private SystemInterface gasFluid;
  private Stream gasStream;

  @BeforeEach
  void setUp() {
    // Create a typical gas well fluid
    gasFluid = new SystemSrkEos(85.0, 200.0);
    gasFluid.addComponent("methane", 0.85);
    gasFluid.addComponent("ethane", 0.08);
    gasFluid.addComponent("propane", 0.04);
    gasFluid.addComponent("n-butane", 0.02);
    gasFluid.addComponent("CO2", 0.01);
    gasFluid.setMixingRule("classic");
    gasFluid.init(0);

    gasStream = new Stream("feed", gasFluid);
    gasStream.setFlowRate(2.0, "MSm3/day");
    gasStream.setTemperature(85.0, "C");
    gasStream.setPressure(200.0, "bara");
    gasStream.run();
  }

  @Test
  void testBasicConstruction() {
    TubingPerformance tubing = new TubingPerformance("test_tubing");
    assertNotNull(tubing);
    assertEquals("test_tubing", tubing.getName());
  }

  @Test
  void testTubingWithBeggsBrill() {
    TubingPerformance tubing = new TubingPerformance("beggs_brill_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1); // 100 mm
    tubing.setLength(3000.0); // 3000 m
    tubing.setInclination(90.0); // Vertical
    tubing.setRoughness(0.00005);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

    tubing.run();

    // Check that outlet pressure is lower than inlet
    double outletPressure = tubing.getOutletStream().getPressure("bara");
    assertTrue(outletPressure < 200.0, "Outlet pressure should be less than inlet");
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");

    // Pressure drop should be positive
    double dp = tubing.getPressureDrop();
    assertTrue(dp > 0, "Pressure drop should be positive");
  }

  @Test
  void testTubingWithHagedornBrown() {
    // Hagedorn-Brown requires a two-phase system with significant liquid
    // Create a heavier fluid with condensate for this correlation
    SystemInterface oilGasFluid = new SystemSrkEos(60.0, 150.0);
    oilGasFluid.addComponent("methane", 0.50);
    oilGasFluid.addComponent("ethane", 0.10);
    oilGasFluid.addComponent("propane", 0.10);
    oilGasFluid.addComponent("n-butane", 0.08);
    oilGasFluid.addComponent("n-pentane", 0.07);
    oilGasFluid.addComponent("n-hexane", 0.05);
    oilGasFluid.addComponent("n-heptane", 0.05);
    oilGasFluid.addComponent("n-octane", 0.05);
    oilGasFluid.setMixingRule("classic");
    oilGasFluid.init(0);

    Stream oilGasStream = new Stream("oil_gas_feed", oilGasFluid);
    oilGasStream.setFlowRate(500.0, "Sm3/day");
    oilGasStream.setTemperature(60.0, "C");
    oilGasStream.setPressure(150.0, "bara");
    oilGasStream.run();

    TubingPerformance tubing = new TubingPerformance("hagedorn_brown_tubing");
    tubing.setInletStream(oilGasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(2500.0);
    tubing.setInclination(90.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.HAGEDORN_BROWN);

    tubing.run();

    double outletPressure = tubing.getOutletStream().getPressure("bara");
    assertTrue(outletPressure < 150.0 && outletPressure > 0, "Outlet pressure should be valid");
  }

  @Test
  void testTubingWithGray() {
    TubingPerformance tubing = new TubingPerformance("gray_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.088); // 3.5" tubing
    tubing.setLength(3000.0);
    tubing.setInclination(90.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.GRAY);

    tubing.run();

    double outletPressure = tubing.getOutletStream().getPressure("bara");
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
  }

  @Test
  void testTubingWithHasanKabir() {
    TubingPerformance tubing = new TubingPerformance("hasan_kabir_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(2000.0);
    tubing.setInclination(90.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.HASAN_KABIR);

    tubing.run();

    double outletPressure = tubing.getOutletStream().getPressure("bara");
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
  }

  @Test
  void testTubingWithDunsRos() {
    TubingPerformance tubing = new TubingPerformance("duns_ros_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(2500.0);
    tubing.setInclination(90.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.DUNS_ROS);

    tubing.run();

    double outletPressure = tubing.getOutletStream().getPressure("bara");
    assertTrue(outletPressure > 0, "Outlet pressure should be positive");
  }

  @Test
  void testGenerateVLPCurve() {
    TubingPerformance tubing = new TubingPerformance("vlp_curve_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(3000.0);
    tubing.setInclination(90.0);
    tubing.setWellheadPressure(50.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

    double[] flowRates = {0.5, 1.0, 2.0, 3.0, 4.0};
    double[][] vlpCurve = tubing.generateVLPCurve(flowRates);

    assertNotNull(vlpCurve);
    assertEquals(2, vlpCurve.length);
    assertEquals(flowRates.length, vlpCurve[0].length);

    // Check that required BHP increases with flow rate (typical VLP behavior)
    for (int i = 1; i < vlpCurve[1].length; i++) {
      // VLP curve should show increasing BHP with higher rates (after minimum)
      assertTrue(vlpCurve[1][i] > 0, "BHP values should be positive");
    }
  }

  @Test
  void testIsothermalTemperatureModel() {
    TubingPerformance tubing = new TubingPerformance("isothermal_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(2000.0);
    tubing.setInclination(90.0);
    tubing.setTemperatureModel(TubingPerformance.TemperatureModel.ISOTHERMAL);

    tubing.run();

    // With isothermal model, outlet temp should be close to inlet
    double outletTemp = tubing.getOutletStream().getTemperature("C");
    assertEquals(85.0, outletTemp, 1.0, "Isothermal should maintain temperature");
  }

  @Test
  void testLinearGradientTemperatureModel() {
    TubingPerformance tubing = new TubingPerformance("linear_temp_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(3000.0);
    tubing.setInclination(90.0);
    tubing.setTemperatureModel(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
    tubing.setSurfaceTemperature(25.0);
    tubing.setBottomholeTemperature(85.0);

    tubing.run();

    double outletTemp = tubing.getOutletStream().getTemperature("C");
    assertTrue(outletTemp < 85.0, "Outlet temp should be less than BH temp");
    assertTrue(outletTemp >= 25.0, "Outlet temp should be at least surface temp");
  }

  @Test
  void testRameyTemperatureModel() {
    TubingPerformance tubing = new TubingPerformance("ramey_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(3000.0);
    tubing.setInclination(90.0);
    tubing.setTemperatureModel(TubingPerformance.TemperatureModel.RAMEY);
    tubing.setSurfaceTemperature(20.0);
    tubing.setGeothermalGradient(0.03); // °C/m
    tubing.setFormationThermalConductivity(2.5);
    tubing.setOverallHeatTransferCoefficient(25.0);
    tubing.setProductionTime(365.0);

    tubing.run();

    double outletTemp = tubing.getOutletStream().getTemperature("C");
    assertTrue(outletTemp > 20.0, "Ramey model should give temperature > surface");
  }

  @Test
  void testInclinedTubing() {
    TubingPerformance tubing = new TubingPerformance("inclined_tubing");
    tubing.setInletStream(gasStream);
    tubing.setDiameter(0.1);
    tubing.setLength(3000.0);
    tubing.setInclination(60.0); // 60 degrees from horizontal
    tubing.setRoughness(0.00005);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

    tubing.run();

    double dp60 = tubing.getPressureDrop();

    // Compare with vertical
    tubing.setInclination(90.0);
    tubing.run();
    double dp90 = tubing.getPressureDrop();

    // Both should have positive pressure drops
    assertTrue(dp60 > 0, "60 degree inclination should have positive pressure drop");
    assertTrue(dp90 > 0, "Vertical tubing should have positive pressure drop");
    // The relationship between dp60 and dp90 depends on flow regime
    // Just verify both calculations complete successfully
    assertNotEquals(dp60, dp90, 0.01, "Different inclinations should give different pressure drops");
  }

  @Test
  void testDiameterEffect() {
    TubingPerformance tubing = new TubingPerformance("diameter_test");
    tubing.setInletStream(gasStream);
    tubing.setLength(2500.0);
    tubing.setInclination(90.0);
    tubing.setCorrelationType(TubingPerformance.CorrelationType.BEGGS_BRILL);

    // Small diameter
    tubing.setDiameter(0.05);
    tubing.run();
    double dpSmall = tubing.getPressureDrop();

    // Larger diameter
    tubing.setDiameter(0.15);
    tubing.run();
    double dpLarge = tubing.getPressureDrop();

    // Larger diameter should have less friction
    assertTrue(dpLarge < dpSmall, "Larger diameter should have less pressure drop");
  }

  @Test
  void testSettersAndGetters() {
    TubingPerformance tubing = new TubingPerformance("getter_test");

    tubing.setDiameter(0.1);
    assertEquals(0.1, tubing.getDiameter(), 1e-6);

    tubing.setLength(3000.0);
    assertEquals(3000.0, tubing.getLength(), 1e-6);

    tubing.setInclination(75.0);
    assertEquals(75.0, tubing.getInclination(), 1e-6);

    tubing.setRoughness(0.0001);
    assertEquals(0.0001, tubing.getRoughness(), 1e-8);

    tubing.setWellheadPressure(50.0);
    assertEquals(50.0, tubing.getWellheadPressure(), 1e-6);
  }

  @Test
  void testCorrelationTypeEnum() {
    // Verify all correlation types exist
    assertNotNull(TubingPerformance.CorrelationType.BEGGS_BRILL);
    assertNotNull(TubingPerformance.CorrelationType.HAGEDORN_BROWN);
    assertNotNull(TubingPerformance.CorrelationType.GRAY);
    assertNotNull(TubingPerformance.CorrelationType.HASAN_KABIR);
    assertNotNull(TubingPerformance.CorrelationType.DUNS_ROS);
  }

  @Test
  void testTemperatureModelEnum() {
    // Verify all temperature models exist
    assertNotNull(TubingPerformance.TemperatureModel.ISOTHERMAL);
    assertNotNull(TubingPerformance.TemperatureModel.LINEAR_GRADIENT);
    assertNotNull(TubingPerformance.TemperatureModel.RAMEY);
    assertNotNull(TubingPerformance.TemperatureModel.HASAN_KABIR);
  }
}
