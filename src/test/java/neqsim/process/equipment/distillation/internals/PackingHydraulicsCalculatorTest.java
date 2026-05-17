package neqsim.process.equipment.distillation.internals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PackingHydraulicsCalculator}.
 *
 * <p>
 * Tests packing hydraulics including flooding, pressure drop, HETP, mass transfer coefficients,
 * wetting check, and column sizing for random and structured packings.
 * </p>
 */
public class PackingHydraulicsCalculatorTest {

  /**
   * Tests random packing (Pall Ring 50mm) with typical absorber conditions.
   */
  @Test
  public void testPallRing50Hydraulics() {
    PackingHydraulicsCalculator calc = new PackingHydraulicsCalculator();
    calc.setPackingPreset("Pall-Ring-50");
    calc.setColumnDiameter(1.2);
    calc.setPackedHeight(6.0);
    calc.setDesignFloodFraction(0.70);

    calc.setVaporMassFlow(2.0); // kg/s
    calc.setLiquidMassFlow(5.0); // kg/s
    calc.setVaporDensity(3.0); // kg/m3
    calc.setLiquidDensity(800.0); // kg/m3
    calc.setVaporViscosity(1.2e-5); // Pa·s
    calc.setLiquidViscosity(0.001); // Pa·s
    calc.setSurfaceTension(0.030); // N/m

    calc.calculate();

    // Flooding velocity should be 1-4 m/s for random packing
    assertTrue(calc.getFloodingVelocity() > 0.5, "Flooding velocity too low");
    assertTrue(calc.getFloodingVelocity() < 6.0, "Flooding velocity too high");

    // Percent flood — should be moderate
    assertTrue(calc.getPercentFlood() > 0, "Percent flood positive");

    // Pressure drop per meter — typical 100-800 Pa/m below flood
    assertTrue(calc.getPressureDropPerMeter() > 0, "DP/m positive");

    // Total pressure drop
    assertEquals(calc.getPressureDropPerMeter() * 6.0, calc.getTotalPressureDrop(), 0.1);

    // HETP for 50mm Pall Ring: typically 0.5-1.0 m
    assertTrue(calc.getHETP() > 0.1, "HETP too low: " + calc.getHETP());
    assertTrue(calc.getHETP() < 3.0, "HETP too high: " + calc.getHETP());

    // Number of theoretical stages
    assertTrue(calc.getNumberOfTheoreticalStages() > 0, "Stages should be positive");

    // Fs factor should be reasonable
    assertTrue(calc.getFsFactor() > 0, "Fs factor positive");
  }

  /**
   * Tests structured packing (Mellapak 250Y) — should give lower HETP than random packing.
   */
  @Test
  public void testStructuredPackingLowerHETP() {
    PackingHydraulicsCalculator randomCalc = new PackingHydraulicsCalculator();
    randomCalc.setPackingPreset("Pall-Ring-50");
    randomCalc.setColumnDiameter(1.5);
    randomCalc.setPackedHeight(6.0);
    randomCalc.setVaporMassFlow(2.0);
    randomCalc.setLiquidMassFlow(5.0);
    randomCalc.setVaporDensity(3.0);
    randomCalc.setLiquidDensity(800.0);
    randomCalc.setVaporViscosity(1.2e-5);
    randomCalc.setLiquidViscosity(0.001);
    randomCalc.setSurfaceTension(0.030);
    randomCalc.calculate();

    PackingHydraulicsCalculator structCalc = new PackingHydraulicsCalculator();
    structCalc.setStructuredPackingPreset("Mellapak-250Y");
    structCalc.setColumnDiameter(1.5);
    structCalc.setPackedHeight(6.0);
    structCalc.setVaporMassFlow(2.0);
    structCalc.setLiquidMassFlow(5.0);
    structCalc.setVaporDensity(3.0);
    structCalc.setLiquidDensity(800.0);
    structCalc.setVaporViscosity(1.2e-5);
    structCalc.setLiquidViscosity(0.001);
    structCalc.setSurfaceTension(0.030);
    structCalc.calculate();

    // Structured packing should have lower HETP (better mass transfer)
    assertTrue(structCalc.getHETP() < randomCalc.getHETP(),
        "Structured packing (Mellapak-250Y) should have lower HETP than Pall-Ring-50. Struct="
            + structCalc.getHETP() + " Random=" + randomCalc.getHETP());

    // Structured packing should give more theoretical stages for same bed height
    assertTrue(structCalc.getNumberOfTheoreticalStages() > randomCalc
        .getNumberOfTheoreticalStages(), "Structured packing should give more stages");
  }

  /**
   * Tests column diameter sizing from flow conditions.
   */
  @Test
  public void testColumnDiameterSizing() {
    PackingHydraulicsCalculator calc = new PackingHydraulicsCalculator();
    calc.setPackingPreset("IMTP-50");
    calc.setDesignFloodFraction(0.70);

    calc.setVaporMassFlow(3.0);
    calc.setLiquidMassFlow(8.0);
    calc.setVaporDensity(4.0);
    calc.setLiquidDensity(700.0);
    calc.setLiquidViscosity(0.0005);
    calc.setSurfaceTension(0.025);

    double diameter = calc.sizeColumnDiameter();

    // Should be a reasonable standard vessel size
    assertTrue(diameter >= 0.3, "Diameter too small: " + diameter);
    assertTrue(diameter <= 8.0, "Diameter too large: " + diameter);
  }

  /**
   * Tests various random packing presets can be set without errors.
   */
  @Test
  public void testPackingPresets() {
    String[] presets =
        {"Pall-Ring-25", "Pall-Ring-38", "Raschig-Ring-25", "IMTP-40", "Berl-Saddle-25"};

    for (String preset : presets) {
      PackingHydraulicsCalculator calc = new PackingHydraulicsCalculator();
      calc.setPackingPreset(preset);

      assertTrue(calc.getSpecificSurfaceArea() > 50,
          preset + " should have specific surface area > 50");
      assertTrue(calc.getVoidFraction() > 0.5, preset + " should have void fraction > 0.5");
      assertTrue(calc.getPackingFactor() > 10, preset + " should have packing factor > 10");
    }
  }

  /**
   * Tests structured packing presets.
   */
  @Test
  public void testStructuredPackingPresets() {
    String[] presets = {"Mellapak-125Y", "Mellapak-250Y", "Mellapak-500Y", "Flexipac-2Y"};

    for (String preset : presets) {
      PackingHydraulicsCalculator calc = new PackingHydraulicsCalculator();
      calc.setStructuredPackingPreset(preset);

      assertTrue(calc.getSpecificSurfaceArea() > 100,
          preset + " should have specific surface area > 100");
      assertTrue(calc.getVoidFraction() > 0.9, preset + " should have void fraction > 0.9");
      assertEquals("structured", calc.getPackingCategory(),
          preset + " should set category to structured");
    }
  }

  /**
   * Tests mass transfer coefficients are positive when inputs are provided.
   */
  @Test
  public void testMassTransferCoefficients() {
    PackingHydraulicsCalculator calc = new PackingHydraulicsCalculator();
    calc.setPackingPreset("Pall-Ring-50");
    calc.setColumnDiameter(1.5);
    calc.setPackedHeight(8.0);

    calc.setVaporMassFlow(2.5);
    calc.setLiquidMassFlow(6.0);
    calc.setVaporDensity(3.5);
    calc.setLiquidDensity(750.0);
    calc.setVaporViscosity(1.5e-5);
    calc.setLiquidViscosity(0.0008);
    calc.setSurfaceTension(0.025);
    calc.setVaporDiffusivity(1e-5);
    calc.setLiquidDiffusivity(1e-9);

    calc.calculate();

    // Mass transfer coefficients should be positive
    assertTrue(calc.getKGa() > 0, "kG*a should be positive: " + calc.getKGa());
    assertTrue(calc.getKLa() > 0, "kL*a should be positive: " + calc.getKLa());

    // Wetted area
    assertTrue(calc.getWettedArea() > 0, "Wetted area should be positive");

    // HTU values should be positive
    assertTrue(calc.getHtuG() > 0, "HTU_G should be positive");
    assertTrue(calc.getHtuL() > 0, "HTU_L should be positive");
    assertTrue(calc.getHtuOG() > 0, "HTU_OG should be positive");
  }

  /**
   * Tests pressure drop increases with higher vapor flow.
   */
  @Test
  public void testPressureDropIncreasesWithFlow() {
    PackingHydraulicsCalculator calcLow = new PackingHydraulicsCalculator();
    calcLow.setPackingPreset("Pall-Ring-50");
    calcLow.setColumnDiameter(1.5);
    calcLow.setPackedHeight(5.0);
    calcLow.setVaporMassFlow(1.0);
    calcLow.setLiquidMassFlow(3.0);
    calcLow.setVaporDensity(3.0);
    calcLow.setLiquidDensity(800.0);
    calcLow.setVaporViscosity(1.2e-5);
    calcLow.setLiquidViscosity(0.001);
    calcLow.setSurfaceTension(0.030);
    calcLow.calculate();

    PackingHydraulicsCalculator calcHigh = new PackingHydraulicsCalculator();
    calcHigh.setPackingPreset("Pall-Ring-50");
    calcHigh.setColumnDiameter(1.5);
    calcHigh.setPackedHeight(5.0);
    calcHigh.setVaporMassFlow(3.0); // 3x higher
    calcHigh.setLiquidMassFlow(3.0);
    calcHigh.setVaporDensity(3.0);
    calcHigh.setLiquidDensity(800.0);
    calcHigh.setVaporViscosity(1.2e-5);
    calcHigh.setLiquidViscosity(0.001);
    calcHigh.setSurfaceTension(0.030);
    calcHigh.calculate();

    assertTrue(calcHigh.getPressureDropPerMeter() > calcLow.getPressureDropPerMeter(),
        "Higher vapor flow should give higher DP");
  }
}
