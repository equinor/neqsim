package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CompressorCurveCorrections.
 *
 * <p>
 * Tests the correction methods for centrifugal compressor performance curves including Reynolds
 * correction, Mach number calculations, and multistage surge correction.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class CompressorCurveCorrectionsTest {
  private static final double TOLERANCE = 1e-6;

  // ==================== Reynolds Number Tests ====================

  @Test
  void testReynoldsEfficiencyCorrection_highReynolds() {
    // Higher Reynolds number should give slight efficiency improvement
    double actualRe = 2.0e7;
    double refRe = 1.0e7;
    double correction =
        CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(actualRe, refRe);

    // With exponent 0.1: (2)^0.1 ≈ 1.072
    assertTrue(correction > 1.0, "Higher Re should give correction > 1");
    assertTrue(correction <= 1.05, "Correction should be limited to 1.05 max");
  }

  @Test
  void testReynoldsEfficiencyCorrection_lowReynolds() {
    // Lower Reynolds number should reduce efficiency
    double actualRe = 5.0e6;
    double refRe = 1.0e7;
    double correction =
        CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(actualRe, refRe);

    // With exponent 0.1: (0.5)^0.1 ≈ 0.933
    assertTrue(correction < 1.0, "Lower Re should give correction < 1");
    assertTrue(correction >= 0.9, "Correction should be limited to 0.9 min");
  }

  @Test
  void testReynoldsEfficiencyCorrection_sameReynolds() {
    // Same Reynolds should give correction of 1.0
    double correction =
        CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(1.0e7, 1.0e7);
    assertEquals(1.0, correction, TOLERANCE);
  }

  @Test
  void testReynoldsEfficiencyCorrection_invalidInput() {
    // Invalid inputs should return 1.0 (no correction)
    assertEquals(1.0,
        CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(-1.0, 1.0e7));
    assertEquals(1.0, CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(1.0e7, 0));
    assertEquals(1.0, CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(0, 0));
  }

  @Test
  void testReynoldsEfficiencyCorrection_defaultReference() {
    // Test with default reference (1e7)
    double correction = CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(1.0e7);
    assertEquals(1.0, correction, TOLERANCE);
  }

  // ==================== Reynolds Number Calculation Tests ====================

  @Test
  void testCalculateReynoldsNumber() {
    // Typical values for a centrifugal compressor
    double tipSpeed = 300.0; // m/s
    double diameter = 0.4; // m
    double kinematicViscosity = 1.5e-5; // m²/s (typical for air/gas at ~300K)

    double reynolds =
        CompressorCurveCorrections.calculateReynoldsNumber(tipSpeed, diameter, kinematicViscosity);

    // Re = v * D / ν = 300 * 0.4 / 1.5e-5 = 8e6
    assertEquals(8.0e6, reynolds, 1.0e4, "Reynolds number calculation");
  }

  @Test
  void testCalculateReynoldsNumber_edgeCases() {
    // Very low values still give valid calculation
    double smallRe = CompressorCurveCorrections.calculateReynoldsNumber(1, 0.01, 1.5e-5);
    assertTrue(smallRe > 0, "Small but valid inputs should give positive Reynolds");
  }

  // ==================== Tip Speed Calculation Tests ====================

  @Test
  void testCalculateTipSpeed() {
    double rpm = 10000;
    double diameter = 0.4; // m

    double tipSpeed = CompressorCurveCorrections.calculateTipSpeed(rpm, diameter);

    // v = π * D * N / 60 = π * 0.4 * 10000 / 60 ≈ 209.4 m/s
    double expectedTipSpeed = Math.PI * 0.4 * 10000 / 60.0;
    assertEquals(expectedTipSpeed, tipSpeed, TOLERANCE);
  }

  @Test
  void testCalculateTipSpeed_edgeCases() {
    // Very low rpm still calculates
    double lowSpeedTip = CompressorCurveCorrections.calculateTipSpeed(100, 0.4);
    assertTrue(lowSpeedTip > 0, "Low rpm should give small positive tip speed");
  }

  // ==================== Mach Number Tests ====================

  @Test
  void testCalculateMachNumber() {
    double velocity = 250.0; // m/s
    double sonicVelocity = 400.0; // m/s

    double mach = CompressorCurveCorrections.calculateMachNumber(velocity, sonicVelocity);

    assertEquals(0.625, mach, TOLERANCE);
  }

  @Test
  void testCalculateMachNumber_edgeCases() {
    // Very low velocity
    double lowMach = CompressorCurveCorrections.calculateMachNumber(10, 400);
    assertTrue(lowMach > 0 && lowMach < 0.1, "Low velocity should give low Mach");
  }

  // ==================== Sonic Velocity Tests ====================

  @Test
  void testCalculateSonicVelocity_methane() {
    // Methane at typical conditions
    double kappa = 1.31; // Heat capacity ratio for methane
    double temperature = 300.0; // K
    double molarMass = 16.04; // kg/kmol
    double zFactor = 0.998; // Near ideal

    double sonicVelocity =
        CompressorCurveCorrections.calculateSonicVelocity(kappa, temperature, molarMass, zFactor);

    // c = sqrt(κ * Z * R * T / M)
    // c = sqrt(1.31 * 0.998 * 8314 * 300 / 16.04) ≈ 450 m/s
    assertTrue(sonicVelocity > 400 && sonicVelocity < 500,
        "Sonic velocity for methane should be ~450 m/s, got " + sonicVelocity);
  }

  @Test
  void testCalculateSonicVelocity_air() {
    // Air at typical conditions
    double kappa = 1.40;
    double temperature = 300.0; // K
    double molarMass = 28.97; // kg/kmol
    double zFactor = 1.0;

    double sonicVelocity =
        CompressorCurveCorrections.calculateSonicVelocity(kappa, temperature, molarMass, zFactor);

    // Expected ~347 m/s for air at 300 K
    assertTrue(sonicVelocity > 340 && sonicVelocity < 360,
        "Sonic velocity for air should be ~347 m/s, got " + sonicVelocity);
  }

  @Test
  void testCalculateSonicVelocity_highTemperature() {
    // Higher temperature gives higher sonic velocity
    double sonicHigh = CompressorCurveCorrections.calculateSonicVelocity(1.4, 400, 28.97, 1.0);
    double sonicLow = CompressorCurveCorrections.calculateSonicVelocity(1.4, 300, 28.97, 1.0);
    assertTrue(sonicHigh > sonicLow, "Higher temperature should give higher sonic velocity");
  }

  // ==================== Stonewall Flow Tests ====================

  @Test
  void testCalculateStonewallFlow_subsonic() {
    double designFlow = 1000.0; // m³/hr
    double sonicVelocity = 400.0; // m/s
    double designMach = 0.5;

    double stonewallFlow =
        CompressorCurveCorrections.calculateStonewallFlow(designFlow, sonicVelocity, designMach);

    // At design Mach 0.5, stonewall at Mach ~0.9
    // Flow ratio ≈ 0.9/0.5 = 1.8, so stonewall ≈ 1800 m³/hr
    assertTrue(stonewallFlow > designFlow, "Stonewall flow should be greater than design flow");
    assertTrue(stonewallFlow < designFlow * 2.0,
        "Stonewall flow should be reasonable for subsonic design");
  }

  @Test
  void testCalculateStonewallFlow_highMach() {
    double designFlow = 1000.0;
    double sonicVelocity = 400.0;
    double designMach = 0.85; // Already near stonewall

    double stonewallFlow =
        CompressorCurveCorrections.calculateStonewallFlow(designFlow, sonicVelocity, designMach);

    // Near stonewall, not much flow increase possible
    assertTrue(stonewallFlow >= designFlow, "Stonewall should be at least design flow");
    assertTrue(stonewallFlow < designFlow * 1.2, "High Mach design has limited stonewall margin");
  }

  // ==================== Multistage Surge Correction Tests ====================

  @Test
  void testMultistageSurgeCorrection_fullSpeed() {
    // At full speed, no correction needed
    double baseSurgeFlow = 700.0;
    double speedRatio = 1.0;
    int stages = 4;

    double correctedFlow = CompressorCurveCorrections
        .calculateMultistageSurgeCorrection(baseSurgeFlow, speedRatio, stages);

    assertEquals(baseSurgeFlow, correctedFlow, TOLERANCE, "No surge correction at full speed");
  }

  @Test
  void testMultistageSurgeCorrection_reducedSpeed() {
    // At reduced speed, surge shifts to higher relative flow
    double baseSurgeFlow = 500.0;
    double speedRatio = 0.7;
    int stages = 4;

    double correctedFlow = CompressorCurveCorrections
        .calculateMultistageSurgeCorrection(baseSurgeFlow, speedRatio, stages);

    assertTrue(correctedFlow > baseSurgeFlow,
        "Multistage surge at reduced speed should shift to higher flow");
  }

  @Test
  void testMultistageSurgeCorrection_moreStages() {
    // More stages = more correction needed
    double baseSurgeFlow = 500.0;
    double speedRatio = 0.7;

    double corrected4Stage =
        CompressorCurveCorrections.calculateMultistageSurgeCorrection(baseSurgeFlow, speedRatio, 4);
    double corrected8Stage =
        CompressorCurveCorrections.calculateMultistageSurgeCorrection(baseSurgeFlow, speedRatio, 8);

    assertTrue(corrected8Stage > corrected4Stage,
        "More stages should result in larger surge correction");
  }

  @Test
  void testMultistageSurgeCorrection_singleStage() {
    // Single stage should have no multistage correction
    double baseSurgeFlow = 500.0;
    double speedRatio = 0.7;

    double correctedFlow =
        CompressorCurveCorrections.calculateMultistageSurgeCorrection(baseSurgeFlow, speedRatio, 1);

    assertEquals(baseSurgeFlow, correctedFlow, TOLERANCE,
        "Single stage compressor should have no multistage correction");
  }

  // ==================== Multistage Surge Head Correction Tests ====================

  @Test
  void testMultistageSurgeHeadCorrection_fullSpeed() {
    double baseSurgeHead = 100.0;
    double speedRatio = 1.0;
    int stages = 4;

    double correctedHead = CompressorCurveCorrections
        .calculateMultistageSurgeHeadCorrection(baseSurgeHead, speedRatio, stages);

    assertEquals(baseSurgeHead, correctedHead, TOLERANCE, "No head correction at full speed");
  }

  @Test
  void testMultistageSurgeHeadCorrection_reducedSpeed() {
    // At reduced speed, surge head may vary depending on implementation
    double baseSurgeHead = 100.0;
    double speedRatio = 0.7;
    int stages = 4;

    double correctedHead = CompressorCurveCorrections
        .calculateMultistageSurgeHeadCorrection(baseSurgeHead, speedRatio, stages);

    // Just verify that correction produces a reasonable value
    assertTrue(correctedHead > 0, "Surge head should be positive");
    assertTrue(correctedHead < baseSurgeHead * 1.5, "Surge head correction should be bounded");
  }

  // ==================== Integration Test ====================

  @Test
  void testCorrectionsIntegration() {
    // Simulate typical compressor conditions
    double rpm = 12000;
    double diameter = 0.35; // m
    double kinematicViscosity = 1.5e-5; // m²/s

    // Calculate tip speed
    double tipSpeed = CompressorCurveCorrections.calculateTipSpeed(rpm, diameter);
    assertTrue(tipSpeed > 200, "Tip speed should be reasonable");

    // Calculate Reynolds number
    double reynolds =
        CompressorCurveCorrections.calculateReynoldsNumber(tipSpeed, diameter, kinematicViscosity);
    assertTrue(reynolds > 1e6, "Reynolds should be high for industrial compressor");

    // Calculate efficiency correction
    double effCorrection =
        CompressorCurveCorrections.calculateReynoldsEfficiencyCorrection(reynolds);
    assertTrue(effCorrection >= 0.9 && effCorrection <= 1.05,
        "Efficiency correction should be in valid range");

    // Calculate sonic velocity for natural gas
    double sonicVelocity = CompressorCurveCorrections.calculateSonicVelocity(1.3, 310, 18.5, 0.95);
    assertTrue(sonicVelocity > 350, "Sonic velocity for natural gas should be reasonable");

    // Calculate Mach number at tip
    double machTip = CompressorCurveCorrections.calculateMachNumber(tipSpeed, sonicVelocity);
    assertTrue(machTip > 0 && machTip < 1.2, "Tip Mach should be reasonable");
  }
}
