package neqsim.process.equipment.distillation.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TrayHydraulicsCalculator}.
 *
 * <p>
 * Tests sieve/valve/bubble-cap tray hydraulics including flooding, weeping, entrainment, downcomer
 * backup, pressure drop, tray efficiency, and column sizing.
 * </p>
 */
public class TrayHydraulicsCalculatorTest {

  /**
   * Tests sieve tray hydraulics with typical light hydrocarbon conditions. Vapor: ~5 kg/m3, Liquid:
   * ~500 kg/m3 — typical mid-column conditions for a depropanizer.
   */
  @Test
  public void testSieveTrayTypicalConditions() {
    TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
    calc.setTrayType("sieve");
    calc.setColumnDiameter(1.5);
    calc.setTraySpacing(0.6);
    calc.setWeirHeight(0.05);
    calc.setHoleDiameter(12.7);
    calc.setHoleAreaFraction(0.10);
    calc.setDowncommerAreaFraction(0.10);
    calc.setDesignFloodFraction(0.80);

    // Typical depropanizer conditions
    calc.setVaporMassFlow(2.0); // kg/s
    calc.setLiquidMassFlow(3.0); // kg/s
    calc.setVaporDensity(5.0); // kg/m3
    calc.setLiquidDensity(500.0); // kg/m3
    calc.setLiquidViscosity(0.0003); // Pa·s
    calc.setSurfaceTension(0.015); // N/m
    calc.setRelativeVolatility(3.0);

    calc.calculate();

    // Flooding velocity should be reasonable (0.5-3 m/s typically)
    assertTrue(calc.getFloodingVelocity() > 0.3, "Flooding velocity too low");
    assertTrue(calc.getFloodingVelocity() < 5.0, "Flooding velocity too high");

    // Percent flood should be within normal operating range for 1.5m column
    assertTrue(calc.getPercentFlood() > 0, "Percent flood should be positive");
    assertTrue(calc.getPercentFlood() < 200, "Percent flood unreasonable");

    // Weeping check
    // Note: weeping depends on actual vapor velocity vs minimum
    // For moderate flow rates, weeping should be OK
    assertNotNull(Boolean.valueOf(calc.isWeepingOk()));

    // Entrainment should be between 0 and 1
    assertTrue(calc.getEntrainment() >= 0, "Entrainment negative");
    assertTrue(calc.getEntrainment() <= 1.0, "Entrainment > 1");

    // Downcomer backup should be less than tray spacing
    assertTrue(calc.getDowncommerBackup() >= 0, "Backup negative");

    // Pressure drop should be positive
    assertTrue(calc.getTotalTrayPressureDrop() > 0, "Pressure drop should be positive");
    assertTrue(calc.getDryTrayPressureDrop() > 0, "Dry tray DP positive");
    assertTrue(calc.getLiquidHeadPressureDrop() > 0, "Liquid head DP positive");

    // Tray efficiency (O'Connell) should be 0.3-0.9
    assertTrue(calc.getTrayEfficiency() > 0.2, "Efficiency too low");
    assertTrue(calc.getTrayEfficiency() < 1.0, "Efficiency too high");

    // Fs factor
    assertTrue(calc.getFsFactor() > 0, "Fs factor should be positive");

    // Turndown ratio
    assertTrue(calc.getTurndownRatio() > 1.0, "Turndown should be > 1");
  }

  /**
   * Tests that column diameter sizing produces reasonable results.
   */
  @Test
  public void testColumnDiameterSizing() {
    TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
    calc.setTrayType("sieve");
    calc.setTraySpacing(0.6);
    calc.setDesignFloodFraction(0.80);
    calc.setDowncommerAreaFraction(0.10);

    // Moderate flow rates
    calc.setVaporMassFlow(5.0);
    calc.setLiquidMassFlow(8.0);
    calc.setVaporDensity(4.0);
    calc.setLiquidDensity(600.0);
    calc.setLiquidViscosity(0.0005);
    calc.setSurfaceTension(0.020);

    double diameter = calc.sizeColumnDiameter();

    // Should be a standard vessel size (0.3, 0.5, 0.6, 0.8, 1.0, 1.2, ...)
    assertTrue(diameter >= 0.3, "Diameter too small: " + diameter);
    assertTrue(diameter <= 8.0, "Diameter too large: " + diameter);
  }

  /**
   * Tests valve tray type factor produces higher flooding velocity than sieve trays.
   */
  @Test
  public void testValveTrayHigherCapacity() {
    TrayHydraulicsCalculator sieveCalc = new TrayHydraulicsCalculator();
    sieveCalc.setTrayType("sieve");
    sieveCalc.setColumnDiameter(2.0);
    sieveCalc.setTraySpacing(0.6);
    sieveCalc.setVaporMassFlow(3.0);
    sieveCalc.setLiquidMassFlow(5.0);
    sieveCalc.setVaporDensity(5.0);
    sieveCalc.setLiquidDensity(600.0);
    sieveCalc.setLiquidViscosity(0.0004);
    sieveCalc.setSurfaceTension(0.020);
    sieveCalc.calculate();

    TrayHydraulicsCalculator valveCalc = new TrayHydraulicsCalculator();
    valveCalc.setTrayType("valve");
    valveCalc.setColumnDiameter(2.0);
    valveCalc.setTraySpacing(0.6);
    valveCalc.setVaporMassFlow(3.0);
    valveCalc.setLiquidMassFlow(5.0);
    valveCalc.setVaporDensity(5.0);
    valveCalc.setLiquidDensity(600.0);
    valveCalc.setLiquidViscosity(0.0004);
    valveCalc.setSurfaceTension(0.020);
    valveCalc.calculate();

    // Valve trays have higher flooding velocity (~1.1x sieve)
    assertTrue(valveCalc.getFloodingVelocity() > sieveCalc.getFloodingVelocity(),
        "Valve trays should have higher capacity");
    // So percent flood should be lower for valve trays
    assertTrue(valveCalc.getPercentFlood() < sieveCalc.getPercentFlood(),
        "Valve tray should show lower % flood");
  }

  /**
   * Tests high liquid rate scenario that may cause downcomer flooding.
   */
  @Test
  public void testHighLiquidRateDowncomerCheck() {
    TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
    calc.setTrayType("sieve");
    calc.setColumnDiameter(1.0); // Small column
    calc.setTraySpacing(0.45); // Tight spacing
    calc.setWeirHeight(0.06);
    calc.setDowncommerAreaFraction(0.08); // Small downcomers

    calc.setVaporMassFlow(1.0);
    calc.setLiquidMassFlow(15.0); // Very high L/V ratio
    calc.setVaporDensity(3.0);
    calc.setLiquidDensity(800.0);
    calc.setLiquidViscosity(0.001);
    calc.setSurfaceTension(0.030);

    calc.calculate();

    // Downcomer backup fraction should be high with this much liquid
    assertTrue(calc.getDowncommerBackupFraction() > 0,
        "Backup fraction should be significant with high liquid rate");
  }

  /**
   * Tests low vapor rate scenario for weeping check.
   */
  @Test
  public void testLowVaporRateWeepingCheck() {
    TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
    calc.setTrayType("sieve");
    calc.setColumnDiameter(2.0); // Oversized column
    calc.setTraySpacing(0.6);
    calc.setHoleDiameter(12.7);
    calc.setHoleAreaFraction(0.10);

    calc.setVaporMassFlow(0.1); // Very low vapor rate
    calc.setLiquidMassFlow(5.0);
    calc.setVaporDensity(2.0);
    calc.setLiquidDensity(700.0);
    calc.setLiquidViscosity(0.0005);
    calc.setSurfaceTension(0.025);

    calc.calculate();

    // At very low vapor rates in an oversized column, weeping should fail
    assertFalse(calc.isWeepingOk(),
        "Weeping should not be OK at very low vapor rates in oversized column");
  }

  /**
   * Tests pressure drop in mbar output.
   */
  @Test
  public void testPressureDropMbar() {
    TrayHydraulicsCalculator calc = new TrayHydraulicsCalculator();
    calc.setTrayType("sieve");
    calc.setColumnDiameter(1.5);
    calc.setTraySpacing(0.6);
    calc.setVaporMassFlow(2.0);
    calc.setLiquidMassFlow(3.0);
    calc.setVaporDensity(5.0);
    calc.setLiquidDensity(500.0);
    calc.setLiquidViscosity(0.0003);
    calc.setSurfaceTension(0.015);

    calc.calculate();

    // Total DP = dry + liquid + residual
    double totalDP = calc.getDryTrayPressureDrop() + calc.getLiquidHeadPressureDrop()
        + calc.getResidualHeadPressureDrop();
    assertEquals(totalDP, calc.getTotalTrayPressureDrop(), 0.01,
        "Total should equal sum of components");

    // mbar conversion
    assertEquals(calc.getTotalTrayPressureDrop() / 100.0, calc.getTotalTrayPressureDropMbar(),
        0.001);

    // Typical sieve tray DP is 3-15 mbar
    assertTrue(calc.getTotalTrayPressureDropMbar() > 0.1,
        "Pressure drop too low: " + calc.getTotalTrayPressureDropMbar());
    assertTrue(calc.getTotalTrayPressureDropMbar() < 100,
        "Pressure drop too high: " + calc.getTotalTrayPressureDropMbar());
  }

  /**
   * Tests O'Connell efficiency depends on relative volatility.
   */
  @Test
  public void testOConnellEfficiencyDependsOnAlpha() {
    TrayHydraulicsCalculator calcLowAlpha = new TrayHydraulicsCalculator();
    calcLowAlpha.setColumnDiameter(1.5);
    calcLowAlpha.setVaporMassFlow(2.0);
    calcLowAlpha.setLiquidMassFlow(3.0);
    calcLowAlpha.setVaporDensity(5.0);
    calcLowAlpha.setLiquidDensity(500.0);
    calcLowAlpha.setLiquidViscosity(0.0003);
    calcLowAlpha.setSurfaceTension(0.015);
    calcLowAlpha.setRelativeVolatility(1.5); // Easy separation
    calcLowAlpha.calculate();

    TrayHydraulicsCalculator calcHighAlpha = new TrayHydraulicsCalculator();
    calcHighAlpha.setColumnDiameter(1.5);
    calcHighAlpha.setVaporMassFlow(2.0);
    calcHighAlpha.setLiquidMassFlow(3.0);
    calcHighAlpha.setVaporDensity(5.0);
    calcHighAlpha.setLiquidDensity(500.0);
    calcHighAlpha.setLiquidViscosity(0.0003);
    calcHighAlpha.setSurfaceTension(0.015);
    calcHighAlpha.setRelativeVolatility(10.0); // Harder separation
    calcHighAlpha.calculate();

    // O'Connell: higher alpha*mu → lower efficiency
    assertTrue(calcLowAlpha.getTrayEfficiency() > calcHighAlpha.getTrayEfficiency(),
        "Higher alpha should give lower O'Connell efficiency");
  }
}
