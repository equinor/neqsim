package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.ThermodynamicCoupling.ThermoProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Unit tests for FlashTable class.
 */
class FlashTableTest {
  private FlashTable table;
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    testFluid = new SystemSrkEos(298.15, 50.0);
    testFluid.addComponent("methane", 0.9);
    testFluid.addComponent("n-heptane", 0.1);
    testFluid.setMixingRule("classic");
    testFluid.init(0);

    table = new FlashTable();
  }

  @Test
  void testTableNotBuiltInitially() {
    assertFalse(table.isBuilt(), "Table should not be built initially");
  }

  @Test
  void testBuildTableSuccessfully() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    assertTrue(table.isBuilt(), "Table should be built after build()");
  }

  @Test
  void testGetPressureRange() {
    double pMin = 10e5;
    double pMax = 50e5;
    table.build(testFluid, pMin, pMax, 5, 280.0, 340.0, 5);

    assertEquals(pMin, table.getMinPressure(), 1e-10);
    assertEquals(pMax, table.getMaxPressure(), 1e-10);
  }

  @Test
  void testGetTemperatureRange() {
    double tMin = 280.0;
    double tMax = 340.0;
    table.build(testFluid, 10e5, 50e5, 5, tMin, tMax, 5);

    assertEquals(tMin, table.getMinTemperature(), 1e-10);
    assertEquals(tMax, table.getMaxTemperature(), 1e-10);
  }

  @Test
  void testGetGridDimensions() {
    int nP = 7;
    int nT = 9;
    table.build(testFluid, 10e5, 50e5, nP, 280.0, 340.0, nT);

    assertEquals(nP, table.getNumPressurePoints());
    assertEquals(nT, table.getNumTemperaturePoints());
    assertEquals(nP * nT, table.getTotalGridPoints());
  }

  @Test
  void testInterpolateReturnsValidProperties() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    ThermoProperties props = table.interpolate(30e5, 310.0);

    assertTrue(props.converged, "Interpolation should succeed on built table");
    assertTrue(props.gasDensity > 0, "Gas density should be positive");
    assertTrue(props.liquidDensity > 0, "Liquid density should be positive");
  }

  @Test
  void testInterpolateAtGridPoint() {
    // Build with known grid points
    table.build(testFluid, 20e5, 40e5, 3, 290.0, 330.0, 3);

    // Interpolate at exact grid point
    ThermoProperties props = table.interpolate(30e5, 310.0);

    assertTrue(props.converged, "Interpolation at grid point should succeed");
    assertTrue(Double.isFinite(props.gasDensity), "Density should be finite");
  }

  @Test
  void testInterpolateClampsToBounds() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    // Test interpolation outside bounds - should clamp
    ThermoProperties propsLow = table.interpolate(5e5, 260.0);
    ThermoProperties propsHigh = table.interpolate(60e5, 360.0);

    assertTrue(propsLow.converged, "Clamped low interpolation should succeed");
    assertTrue(propsHigh.converged, "Clamped high interpolation should succeed");
  }

  @Test
  void testInterpolateOnUnbuiltTable() {
    ThermoProperties props = table.interpolate(30e5, 310.0);

    assertFalse(props.converged, "Interpolation on unbuilt table should fail");
    assertNotNull(props.errorMessage, "Should have error message");
  }

  @Test
  void testGetPropertyMethod() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    double gasDensity = table.getProperty("gasdensity", 30e5, 310.0);
    double liquidDensity = table.getProperty("liquiddensity", 30e5, 310.0);

    assertTrue(gasDensity > 0, "Gas density from getProperty should be positive");
    assertTrue(liquidDensity > 0, "Liquid density from getProperty should be positive");
    assertTrue(liquidDensity > gasDensity, "Liquid should be denser than gas");
  }

  @Test
  void testGetPropertyUnknownReturnsNaN() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    double unknown = table.getProperty("unknown_property", 30e5, 310.0);
    assertTrue(Double.isNaN(unknown), "Unknown property should return NaN");
  }

  @Test
  void testEstimateMemoryUsage() {
    int nP = 10;
    int nT = 20;
    table.build(testFluid, 10e5, 50e5, nP, 280.0, 340.0, nT);

    long memory = table.estimateMemoryUsage();

    // 19 property tables * nP * nT * 8 bytes
    long expected = 19L * nP * nT * 8;
    assertEquals(expected, memory, "Memory estimate should match formula");
  }

  @Test
  void testClearTable() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);
    assertTrue(table.isBuilt(), "Table should be built");

    table.clear();
    assertFalse(table.isBuilt(), "Table should not be built after clear");
    assertNull(table.getPressures(), "Pressure array should be null after clear");
  }

  @Test
  void testGetPressuresReturnsCopy() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    double[] pressures1 = table.getPressures();
    double[] pressures2 = table.getPressures();

    assertNotSame(pressures1, pressures2, "Should return defensive copies");
    assertArrayEquals(pressures1, pressures2, 1e-10, "Copies should be equal");
  }

  @Test
  void testGetTemperaturesReturnsCopy() {
    table.build(testFluid, 10e5, 50e5, 5, 280.0, 340.0, 5);

    double[] temps1 = table.getTemperatures();
    double[] temps2 = table.getTemperatures();

    assertNotSame(temps1, temps2, "Should return defensive copies");
    assertArrayEquals(temps1, temps2, 1e-10, "Copies should be equal");
  }

  @Test
  void testInterpolationContinuity() {
    // Build fine grid
    table.build(testFluid, 20e5, 40e5, 11, 290.0, 330.0, 11);

    // Check properties at nearby points are similar
    ThermoProperties props1 = table.interpolate(30e5, 310.0);
    ThermoProperties props2 = table.interpolate(30.1e5, 310.1);

    // Density should be similar (within 5%)
    double densityRatio = props2.gasDensity / props1.gasDensity;
    assertTrue(densityRatio > 0.95 && densityRatio < 1.05, "Properties at nearby points should be similar");
  }

  @Test
  void testGasOilTableKeepsAbsentAqueousIdentityAtAndBetweenGridPoints() {
    SystemInterface gasOil = new SystemSrkEos(313.15, 10.0);
    gasOil.addComponent("methane", 0.70);
    gasOil.addComponent("n-heptane", 0.30);
    gasOil.setMixingRule("classic");
    gasOil.setMultiPhaseCheck(true);
    table.build(gasOil, 9.0e5, 11.0e5, 3, 308.15, 318.15, 3);

    ThermoProperties exact = table.interpolate(10.0e5, 313.15);
    ThermoProperties between = table.interpolate(10.5e5, 315.65);

    assertTrue(exact.liquidFraction > 0.0);
    assertEquals(1.0, exact.oilMassFractionOfLiquid, 1.0e-12);
    assertEquals(0.0, exact.aqueousMassFractionOfLiquid, 1.0e-12);
    assertEquals(0.0, between.aqueousMassFractionOfLiquid, 1.0e-12);
  }

  @Test
  void testGasWaterTableKeepsAbsentOilIdentityAtAndBetweenGridPoints() {
    SystemInterface gasWater = new SystemSrkCPAstatoil(313.15, 10.0);
    gasWater.addComponent("methane", 0.70);
    gasWater.addComponent("water", 0.30);
    gasWater.setMixingRule(10);
    gasWater.setMultiPhaseCheck(true);
    table.build(gasWater, 9.0e5, 11.0e5, 3, 308.15, 318.15, 3);

    ThermoProperties exact = table.interpolate(10.0e5, 313.15);
    ThermoProperties between = table.interpolate(10.5e5, 315.65);

    assertTrue(exact.liquidFraction > 0.0);
    assertEquals(0.0, exact.oilMassFractionOfLiquid, 1.0e-12);
    assertEquals(1.0, exact.aqueousMassFractionOfLiquid, 1.0e-12);
    assertEquals(0.0, between.oilMassFractionOfLiquid, 1.0e-12);
  }

  @Test
  void testThreePhaseTableNormalizesBothLiquidMassContributions() {
    SystemInterface threePhase = new SystemSrkEos(300.0, 50.0);
    threePhase.addComponent("methane", 0.40);
    threePhase.addComponent("propane", 0.10);
    threePhase.addComponent("n-heptane", 0.15);
    threePhase.addComponent("n-octane", 0.15);
    threePhase.addComponent("water", 0.20);
    threePhase.setMixingRule("classic");
    threePhase.setMultiPhaseCheck(true);
    table.build(threePhase, 45.0e5, 55.0e5, 3, 295.0, 305.0, 3);

    ThermoProperties exact = table.interpolate(50.0e5, 300.0);
    ThermoProperties between = table.interpolate(52.5e5, 302.5);

    assertTrue(exact.oilMassFractionOfLiquid > 0.0);
    assertTrue(exact.aqueousMassFractionOfLiquid > 0.0);
    assertEquals(1.0, exact.oilMassFractionOfLiquid + exact.aqueousMassFractionOfLiquid, 1.0e-12);
    assertEquals(1.0, between.oilMassFractionOfLiquid + between.aqueousMassFractionOfLiquid, 1.0e-12);
  }

  @Test
  void testSerializationPreservesPhaseResolvedInterpolation() throws Exception {
    table.build(testFluid, 10e5, 50e5, 3, 280.0, 340.0, 3);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ObjectOutputStream output = new ObjectOutputStream(bytes);
    output.writeObject(table);
    output.close();
    ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    FlashTable restored = (FlashTable) input.readObject();
    input.close();

    ThermoProperties expected = table.interpolate(30e5, 310.0);
    ThermoProperties actual = restored.interpolate(30e5, 310.0);
    assertEquals(expected.gasVaporFraction, actual.gasVaporFraction, 1.0e-12);
    assertEquals(expected.liquidFraction, actual.liquidFraction, 1.0e-12);
    assertEquals(expected.oilMassFractionOfLiquid, actual.oilMassFractionOfLiquid, 1.0e-12);
    assertEquals(expected.aqueousMassFractionOfLiquid, actual.aqueousMassFractionOfLiquid, 1.0e-12);
  }
}
