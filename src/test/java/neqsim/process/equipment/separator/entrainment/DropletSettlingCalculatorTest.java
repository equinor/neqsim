package neqsim.process.equipment.separator.entrainment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DropletSettlingCalculator}.
 */
class DropletSettlingCalculatorTest {

  @Test
  void testStokesSettling() {
    // 100 um water droplet in gas at 50 kg/m3
    double dropletDiameter = 100e-6; // m
    double gasDensity = 50.0; // kg/m3
    double waterDensity = 1000.0; // kg/m3
    double gasViscosity = 1.5e-5; // Pa.s

    double vt = DropletSettlingCalculator.calcTerminalVelocity(dropletDiameter, gasDensity,
        waterDensity, gasViscosity);

    // Velocity should be positive (water settling in gas)
    assertTrue(vt > 0, "Water droplet should settle downward in gas");

    // Stokes velocity check: v_stokes = d^2 * delta_rho * g / (18 * mu)
    double vStokes = dropletDiameter * dropletDiameter * (waterDensity - gasDensity) * 9.81
        / (18.0 * gasViscosity);

    // At gasDensity=50, Re may be significant so Schiller-Naumann gives lower velocity
    // Just check it's in a physically reasonable range and positive
    assertTrue(vt > 0.05 * vStokes && vt <= vStokes,
        "Terminal velocity should be between 5% and 100% of Stokes, got: " + vt);
  }

  @Test
  void testBubbleRising() {
    // 1 mm gas bubble rising in oil
    double bubbleDiameter = 1e-3; // 1 mm
    double gasDensity = 30.0; // kg/m3
    double oilDensity = 800.0; // kg/m3
    double oilViscosity = 5e-3; // Pa.s

    double vt = DropletSettlingCalculator.calcTerminalVelocity(bubbleDiameter, oilDensity,
        gasDensity, oilViscosity);

    // Should be negative (bubble rises)
    assertTrue(vt < 0, "Gas bubble should rise in liquid (negative velocity)");

    // Speed should be physically reasonable (cm/s order)
    assertTrue(Math.abs(vt) > 0.001 && Math.abs(vt) < 1.0,
        "Bubble velocity should be in reasonable range. Got: " + vt + " m/s");
  }

  @Test
  void testDragCoefficientStokes() {
    // Very low Re (Stokes regime)
    double cd = DropletSettlingCalculator.calcDragCoefficient(0.01);
    // Should be close to 24/Re = 24/0.01 = 2400
    assertEquals(2400.0, cd, 100.0);
  }

  @Test
  void testDragCoefficientNewton() {
    // High Re (Newton regime)
    double cd = DropletSettlingCalculator.calcDragCoefficient(5000.0);
    assertEquals(0.44, cd, 1e-10);
  }

  @Test
  void testCriticalDiameter() {
    // Separator with 0.5 m height, 10 s residence time
    double height = 0.5; // m
    double residenceTime = 10.0; // s
    double gasDensity = 50.0; // kg/m3
    double liquidDensity = 800.0; // kg/m3
    double gasViscosity = 1.5e-5; // Pa.s

    double dCut = DropletSettlingCalculator.calcCriticalDiameter(height, residenceTime, gasDensity,
        liquidDensity, gasViscosity);

    // Critical diameter should be positive and physically reasonable
    assertTrue(dCut > 1e-6 && dCut < 1e-2,
        "Critical diameter should be in um-mm range. Got: " + dCut * 1e6 + " um");

    // Longer residence time should give smaller cut diameter
    double dCutLong = DropletSettlingCalculator.calcCriticalDiameter(height, 100.0, gasDensity,
        liquidDensity, gasViscosity);
    assertTrue(dCutLong < dCut, "Longer residence time should give smaller cut diameter");
  }

  @Test
  void testZeroDensityDifference() {
    double vt = DropletSettlingCalculator.calcTerminalVelocity(100e-6, 100.0, 100.0, 1e-5);
    assertEquals(0.0, vt, 1e-10, "Zero density difference should give zero velocity");
  }

  @Test
  void testZeroDiameter() {
    double vt = DropletSettlingCalculator.calcTerminalVelocity(0.0, 50.0, 1000.0, 1.5e-5);
    assertEquals(0.0, vt, 1e-10, "Zero diameter should give zero velocity");
  }

  @Test
  void testCsanadyTurbulenceCorrectionIncreasesCutDiameter() {
    // Published-model check:
    // Csanady (1963) + Koenders et al. (2015) implementation should increase effective
    // cut diameter relative to pure gravity settling under turbulent conditions.
    double gravityCut = 40e-6; // 40 um
    double corrected = DropletSettlingCalculator.calcTurbulenceCorrectedCutDiameter(gravityCut,
        3.0, // gas velocity [m/s]
        1.0, // settling height [m]
        0.10, // operating K-factor [m/s]
        0.15, // design K-factor [m/s]
        50.0, // gas density [kg/m3]
        800.0, // liquid density [kg/m3]
        1.5e-5); // gas viscosity [Pa.s]

    assertTrue(corrected >= gravityCut,
        "Turbulence correction should not reduce cut diameter. gravity=" + gravityCut
            + " corrected=" + corrected);

    // Physical cap in implementation: correction factor <= 3.0
    assertTrue(corrected <= gravityCut * 3.0,
        "Correction should obey capped factor (<=3x). gravity=" + gravityCut
            + " corrected=" + corrected);
  }

  @Test
  void testApi12JCompliantCase() {
    // API 12J check:
    // - Horizontal separator without mist eliminator: K <= 0.120 m/s
    // - Gravity cut diameter <= 100 um
    // - Two-phase liquid residence time >= 180 s
    DropletSettlingCalculator.ApiComplianceResult result =
        DropletSettlingCalculator.checkApi12JCompliance(80e-6, // 80 um
            0.10, // below 0.120 m/s limit
            false, // no mist eliminator
            240.0, // > 180 s
            "horizontal", false);

    assertTrue(result.gasLiquidSectionCompliant, "Gas section should be API 12J compliant");
    assertTrue(result.liquidSectionCompliant, "Liquid section should be API 12J compliant");
    assertTrue(result.isFullyCompliant(), "Overall API 12J status should be compliant");
  }

  @Test
  void testApi12JNonCompliantCase() {
    // Non-compliant reference point:
    // - Vertical no mist eliminator: K limit 0.107 m/s (violated)
    // - Cut diameter > 100 um (violated)
    // - Three-phase HRT < 300 s (violated)
    DropletSettlingCalculator.ApiComplianceResult result =
        DropletSettlingCalculator.checkApi12JCompliance(130e-6, // 130 um (too high)
            0.13, // above vertical limit 0.107
            false, // no mist eliminator
            200.0, // below 300 s for 3-phase
            "vertical", true);

    assertTrue(!result.gasLiquidSectionCompliant,
        "Gas section should be non-compliant for high cut diameter and K-factor");
    assertTrue(!result.liquidSectionCompliant,
        "Liquid section should be non-compliant for low 3-phase residence time");
    assertTrue(!result.isFullyCompliant(), "Overall API 12J status should be non-compliant");
  }
}
