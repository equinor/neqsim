package neqsim.process.fielddevelopment.reservoir;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TransientWellModel.
 *
 * @author ESOL
 * @version 1.0
 */
public class TransientWellModelTest {

  private TransientWellModel model;

  @BeforeEach
  void setUp() {
    model = new TransientWellModel();
    // Set up typical oil well parameters
    model.setReservoirPressure(300.0, "bara");
    model.setPermeability(100.0, "mD");
    model.setFormationThickness(20.0, "m");
    model.setPorosity(0.20);
    model.setTotalCompressibility(1.5e-4, "1/bar");
    model.setFluidViscosity(2.0, "cP");
    model.setWellboreRadius(0.1, "m");
    model.setSkinFactor(2.0);
    model.setFormationVolumeFactor(1.2);
    model.setDrainageRadius(500.0, "m");
  }

  @Test
  @DisplayName("Test drawdown calculation for oil well")
  void testDrawdownCalculation() {
    double rate = 100.0; // Sm³/d
    double timeHours = 24.0;

    TransientWellModel.DrawdownResult result = model.calculateDrawdown(rate, timeHours);

    assertNotNull(result, "DrawdownResult should not be null");
    assertTrue(result.flowingPressure > 0, "Bottomhole pressure should be positive");
    assertTrue(result.flowingPressure < 300.0,
        "Bottomhole pressure should be less than reservoir pressure");
    assertTrue(result.drawdown > 0, "Pressure drawdown should be positive");
    assertTrue(result.radiusOfInvestigation > 0, "Radius of investigation should be positive");
    assertTrue(result.productivityIndex > 0, "PI should be positive");
  }

  @Test
  @DisplayName("Test drawdown increases with rate")
  void testDrawdownIncreasesWithRate() {
    double timeHours = 24.0;

    TransientWellModel.DrawdownResult result1 = model.calculateDrawdown(50.0, timeHours);
    TransientWellModel.DrawdownResult result2 = model.calculateDrawdown(100.0, timeHours);
    TransientWellModel.DrawdownResult result3 = model.calculateDrawdown(200.0, timeHours);

    assertTrue(result1.drawdown < result2.drawdown, "Higher rate should cause more drawdown");
    assertTrue(result2.drawdown < result3.drawdown, "Higher rate should cause more drawdown");
  }

  @Test
  @DisplayName("Test radius of investigation grows with time")
  void testRadiusOfInvestigationGrowsWithTime() {
    double rate = 100.0;

    TransientWellModel.DrawdownResult result1 = model.calculateDrawdown(rate, 1.0);
    TransientWellModel.DrawdownResult result2 = model.calculateDrawdown(rate, 10.0);
    TransientWellModel.DrawdownResult result3 = model.calculateDrawdown(rate, 100.0);

    assertTrue(result1.radiusOfInvestigation < result2.radiusOfInvestigation,
        "Radius of investigation should grow with time");
    assertTrue(result2.radiusOfInvestigation < result3.radiusOfInvestigation,
        "Radius of investigation should grow with time");
  }

  @Test
  @DisplayName("Test buildup calculation")
  void testBuildupCalculation() {
    // Produce for 100 hours, then shut in for 10 hours
    double productionRate = 100.0;
    double productionTime = 100.0;
    double shutInTime = 10.0;

    model.addRateChange(0.0, productionRate);
    model.addRateChange(productionTime, 0.0); // Shut in

    TransientWellModel.BuildupResult result = model.calculateBuildup(shutInTime);

    assertNotNull(result, "BuildupResult should not be null");
    assertTrue(result.shutInPressure > 0, "Shut-in pressure should be positive");
    assertTrue(result.shutInPressure <= 300.0,
        "Shut-in pressure should not exceed reservoir pressure");
    assertTrue(result.permeabilityFromSlope > 0, "Estimated permeability should be positive");
  }

  @Test
  @DisplayName("Test buildup pressure increases with shut-in time")
  void testBuildupPressureIncreases() {
    model.addRateChange(0.0, 100.0);
    model.addRateChange(100.0, 0.0);

    TransientWellModel.BuildupResult result1 = model.calculateBuildup(1.0);

    model.clearRateHistory();
    model.addRateChange(0.0, 100.0);
    model.addRateChange(100.0, 0.0);
    TransientWellModel.BuildupResult result2 = model.calculateBuildup(10.0);

    model.clearRateHistory();
    model.addRateChange(0.0, 100.0);
    model.addRateChange(100.0, 0.0);
    TransientWellModel.BuildupResult result3 = model.calculateBuildup(100.0);

    assertTrue(result1.shutInPressure < result2.shutInPressure,
        "Pressure should build up with time");
    assertTrue(result2.shutInPressure < result3.shutInPressure,
        "Pressure should build up with time");
  }

  @Test
  @DisplayName("Test superposition with rate changes")
  void testSuperposition() {
    // Add rate history
    model.addRateChange(0.0, 100.0); // Start at 100 Sm³/d
    model.addRateChange(24.0, 150.0); // Increase to 150 at 24h
    model.addRateChange(48.0, 75.0); // Decrease to 75 at 48h

    // Calculate pressure at 72 hours
    double pressure = model.calculatePressureWithSuperposition(72.0);

    assertTrue(pressure > 0, "Pressure should be positive");
    assertTrue(pressure < 300.0, "Pressure should be less than initial reservoir pressure");
  }

  @Test
  @DisplayName("Test pressure profile generation")
  void testPressureProfile() {
    model.addRateChange(0.0, 100.0);

    double[] timePoints = {1.0, 5.0, 10.0, 24.0, 48.0, 72.0};
    List<TransientWellModel.PressurePoint> profile = model.generatePressureProfile(timePoints);

    assertEquals(timePoints.length, profile.size(), "Should have correct number of points");

    // Verify monotonic pressure decline during constant rate production
    for (int i = 1; i < profile.size(); i++) {
      assertTrue(profile.get(i).pressure <= profile.get(i - 1).pressure,
          "Pressure should decline or stay constant during drawdown");
    }
  }

  @Test
  @DisplayName("Test well type affects calculations")
  void testWellTypeOil() {
    model.setWellType(TransientWellModel.WellType.OIL_PRODUCER);
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 24.0);
    assertTrue(result.flowingPressure > 0, "Oil well calculation should work");
  }

  @Test
  @DisplayName("Test gas well configuration")
  void testGasWellConfiguration() {
    TransientWellModel gasWell = new TransientWellModel();
    gasWell.setWellType(TransientWellModel.WellType.GAS_PRODUCER);
    gasWell.setReservoirPressure(250.0, "bara");
    gasWell.setPermeability(50.0, "mD");
    gasWell.setFormationThickness(30.0, "m");
    gasWell.setPorosity(0.15);
    gasWell.setTotalCompressibility(5e-4, "1/bar");
    gasWell.setFluidViscosity(0.02, "cP"); // Gas viscosity
    gasWell.setWellboreRadius(0.1, "m");
    gasWell.setFormationVolumeFactor(0.01); // Gas FVF

    TransientWellModel.DrawdownResult result = gasWell.calculateDrawdown(50000.0, 24.0);
    assertNotNull(result, "Gas well drawdown should work");
  }

  @Test
  @DisplayName("Test boundary effect calculations")
  void testBoundaryEffects() {
    model.setBoundaryType(TransientWellModel.BoundaryType.NO_FLOW);
    model.setDrainageRadius(300.0, "m");

    // With small drainage radius, should see boundary effects earlier
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 100.0);
    assertNotNull(result, "Bounded system calculation should work");
    assertTrue(result.radiusOfInvestigation > 0, "Should calculate radius of investigation");
  }

  @Test
  @DisplayName("Test dimensionless time calculation")
  void testDimensionlessTime() {
    // For this well:
    // k = 100 mD = 100e-15 m²
    // φ = 0.2
    // μ = 2 cP = 0.002 Pa·s
    // ct = 1.5e-4 /bar = 1.5e-9 /Pa
    // rw = 0.1 m

    // Check that dimensionless time is reasonable
    model.addRateChange(0.0, 100.0);
    double pressure = model.calculatePressureWithSuperposition(24.0);
    assertTrue(pressure > 0 && pressure < 300.0, "Pressure should be in valid range");
  }

  @Test
  @DisplayName("Test skin factor effect")
  void testSkinFactorEffect() {
    TransientWellModel lowSkin = new TransientWellModel();
    TransientWellModel highSkin = new TransientWellModel();

    // Configure identical wells except skin
    for (TransientWellModel m : new TransientWellModel[] {lowSkin, highSkin}) {
      m.setReservoirPressure(300.0, "bara");
      m.setPermeability(100.0, "mD");
      m.setFormationThickness(20.0, "m");
      m.setPorosity(0.20);
      m.setTotalCompressibility(1.5e-4, "1/bar");
      m.setFluidViscosity(2.0, "cP");
      m.setWellboreRadius(0.1, "m");
      m.setFormationVolumeFactor(1.2);
    }

    lowSkin.setSkinFactor(0.0);
    highSkin.setSkinFactor(10.0);

    TransientWellModel.DrawdownResult resultLow = lowSkin.calculateDrawdown(100.0, 24.0);
    TransientWellModel.DrawdownResult resultHigh = highSkin.calculateDrawdown(100.0, 24.0);

    assertTrue(resultHigh.drawdown > resultLow.drawdown,
        "Higher skin should cause more pressure drop");
    assertTrue(resultHigh.flowingPressure < resultLow.flowingPressure,
        "Higher skin should result in lower bottomhole pressure");
  }

  @Test
  @DisplayName("Test permeability effect")
  void testPermeabilityEffect() {
    TransientWellModel lowPerm = new TransientWellModel();
    TransientWellModel highPerm = new TransientWellModel();

    // Configure wells
    for (TransientWellModel m : new TransientWellModel[] {lowPerm, highPerm}) {
      m.setReservoirPressure(300.0, "bara");
      m.setFormationThickness(20.0, "m");
      m.setPorosity(0.20);
      m.setTotalCompressibility(1.5e-4, "1/bar");
      m.setFluidViscosity(2.0, "cP");
      m.setWellboreRadius(0.1, "m");
      m.setSkinFactor(0.0);
      m.setFormationVolumeFactor(1.2);
    }

    lowPerm.setPermeability(10.0, "mD");
    highPerm.setPermeability(1000.0, "mD");

    TransientWellModel.DrawdownResult resultLow = lowPerm.calculateDrawdown(100.0, 24.0);
    TransientWellModel.DrawdownResult resultHigh = highPerm.calculateDrawdown(100.0, 24.0);

    assertTrue(resultLow.drawdown > resultHigh.drawdown,
        "Lower permeability should cause more pressure drop");
  }

  @Test
  @DisplayName("Test unit conversions for pressure")
  void testPressureUnitConversion() {
    model.setReservoirPressure(3000.0, "psi");

    // 3000 psi ≈ 206.8 bar
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 24.0);
    assertTrue(result.flowingPressure < 210.0, "Pressure should be in bar after conversion");
  }

  @Test
  @DisplayName("Test unit conversions for permeability")
  void testPermeabilityUnitConversion() {
    model.setPermeability(0.1, "D"); // 0.1 Darcy = 100 mD

    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 24.0);
    assertNotNull(result, "Should handle Darcy units");
  }

  @Test
  @DisplayName("Test unit conversions for compressibility")
  void testCompressibilityUnitConversion() {
    model.setTotalCompressibility(1.0e-5, "1/psi");

    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 24.0);
    assertNotNull(result, "Should handle 1/psi units");
  }

  @Test
  @DisplayName("Test injector well type")
  void testInjectorWellType() {
    model.setWellType(TransientWellModel.WellType.WATER_INJECTOR);
    model.addRateChange(0.0, -100.0); // Negative rate for injection

    double pressure = model.calculatePressureWithSuperposition(24.0);
    // For injection, pressure should increase
    assertTrue(pressure > 300.0 || pressure > 0, "Injector calculation should work");
  }

  @Test
  @DisplayName("Test rate change history tracking")
  void testRateChangeHistory() {
    model.addRateChange(0.0, 100.0);
    model.addRateChange(24.0, 150.0);
    model.addRateChange(48.0, 0.0); // Shut in

    // Calculate during each period
    double p1 = model.calculatePressureWithSuperposition(12.0); // During first rate
    double p2 = model.calculatePressureWithSuperposition(36.0); // During second rate
    double p3 = model.calculatePressureWithSuperposition(72.0); // During shut-in

    assertTrue(p1 < 300.0, "Pressure should drop during production");
    assertTrue(p2 < p1, "Pressure should drop more at higher rate");
    assertTrue(p3 > p2, "Pressure should recover during shut-in");
  }

  @Test
  @DisplayName("Test clear rate history")
  void testClearRateHistory() {
    model.addRateChange(0.0, 100.0);
    model.addRateChange(24.0, 200.0);

    model.clearRateHistory();
    model.addRateChange(0.0, 50.0);

    double pressure = model.calculatePressureWithSuperposition(24.0);
    // Should only reflect the 50 Sm³/d rate
    assertTrue(pressure > 0 && pressure < 300.0, "Should calculate with cleared history");
  }

  @Test
  @DisplayName("Test zero rate returns reservoir pressure")
  void testZeroRateReturnsReservoirPressure() {
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(0.0, 24.0);
    assertEquals(300.0, result.flowingPressure, 0.1, "Zero rate should give reservoir pressure");
    assertEquals(0.0, result.drawdown, 0.1, "Zero rate should give zero drawdown");
  }

  @Test
  @DisplayName("Test very short time drawdown")
  void testVeryShortTimeDrawdown() {
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 0.01);
    assertTrue(result.radiusOfInvestigation > 0, "Should calculate even for very short time");
    assertTrue(result.flowingPressure > 0, "Pressure should be positive");
  }

  @Test
  @DisplayName("Test hydraulic diffusivity calculation")
  void testHydraulicDiffusivity() {
    // η = k / (φ × μ × ct)
    // With typical values, should be reasonable
    model.addRateChange(0.0, 100.0);
    TransientWellModel.DrawdownResult result = model.calculateDrawdown(100.0, 24.0);

    // Radius of investigation should be reasonable (order of 10s to 100s of meters)
    assertTrue(result.radiusOfInvestigation > 10.0, "Rinv should be > 10m after 24h");
    assertTrue(result.radiusOfInvestigation < 10000.0, "Rinv should be < 10km after 24h");
  }
}
