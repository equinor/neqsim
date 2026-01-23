package neqsim.process.safety.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.envelope.SafetyEnvelope.EnvelopeType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the safety envelope calculation framework.
 */
class SafetyEnvelopeTest {
  private SystemInterface naturalGas;

  @BeforeEach
  void setUp() {
    naturalGas = new SystemSrkEos(280.0, 50.0);
    naturalGas.addComponent("methane", 0.85);
    naturalGas.addComponent("ethane", 0.07);
    naturalGas.addComponent("propane", 0.03);
    naturalGas.addComponent("n-butane", 0.02);
    naturalGas.addComponent("CO2", 0.02);
    naturalGas.addComponent("water", 0.01);
    naturalGas.setMixingRule("classic");
    naturalGas.init(0);
    naturalGas.init(1);
  }

  @Test
  @DisplayName("Test SafetyEnvelope creation")
  void testSafetyEnvelopeCreation() {
    SafetyEnvelope envelope = new SafetyEnvelope("Test Envelope", EnvelopeType.HYDRATE, 10);

    assertEquals("Test Envelope", envelope.getName());
    assertEquals(EnvelopeType.HYDRATE, envelope.getType());
    assertEquals(10, envelope.getNumberOfPoints());
  }

  @Test
  @DisplayName("Test envelope type properties")
  void testEnvelopeTypes() {
    assertEquals("Hydrate Formation", EnvelopeType.HYDRATE.getDisplayName());
    assertEquals("Wax Appearance", EnvelopeType.WAX.getDisplayName());
    assertEquals("CO2 Freezing", EnvelopeType.CO2_FREEZING.getDisplayName());
    assertEquals("MDMT", EnvelopeType.MDMT.getDisplayName());
  }

  @Test
  @DisplayName("Test CO2 freezing envelope calculation")
  void testCO2FreezingEnvelope() {
    SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(naturalGas);
    // Use pressure range above CO2 triple point (5.18 bar) for meaningful freezing temps
    SafetyEnvelope envelope = calc.calculateCO2FreezingEnvelope(10.0, 100.0, 10);

    assertNotNull(envelope);
    assertEquals(EnvelopeType.CO2_FREEZING, envelope.getType());
    assertEquals(10, envelope.getNumberOfPoints());

    double[] temps = envelope.getTemperature();
    double[] pressures = envelope.getPressure();

    assertEquals(10, temps.length);
    assertEquals(10, pressures.length);

    // Verify pressure range
    assertEquals(10.0, pressures[0], 0.01);
    assertEquals(100.0, pressures[9], 0.01);

    // CO2 freezing should be around 216-217 K at moderate pressures (above triple point)
    for (double t : temps) {
      assertTrue(t > 210 && t < 230, "CO2 freezing temp should be 210-230 K, got: " + t);
    }
  }

  @Test
  @DisplayName("Test temperature interpolation")
  void testTemperatureInterpolation() {
    SafetyEnvelope envelope = new SafetyEnvelope("Test", EnvelopeType.HYDRATE, 3);
    // Set some test data: hydrate temp increases with pressure
    envelope.setDataPoint(0, 10.0, 270.0);
    envelope.setDataPoint(1, 50.0, 285.0);
    envelope.setDataPoint(2, 100.0, 295.0);

    // Interpolate at 30 bara
    double interpTemp = envelope.getTemperatureAtPressure(30.0);
    assertTrue(interpTemp > 270 && interpTemp < 285,
        "Interpolated temp should be between 270 and 285 K");

    // Outside range should return NaN
    assertTrue(Double.isNaN(envelope.getTemperatureAtPressure(5.0)));
    assertTrue(Double.isNaN(envelope.getTemperatureAtPressure(150.0)));
  }

  @Test
  @DisplayName("Test operating point safety check")
  void testOperatingPointSafety() {
    SafetyEnvelope envelope = new SafetyEnvelope("Hydrate", EnvelopeType.HYDRATE, 3);
    envelope.setDataPoint(0, 10.0, 270.0, 5.0); // P=10, T=270, margin=5
    envelope.setDataPoint(1, 50.0, 285.0, 5.0);
    envelope.setDataPoint(2, 100.0, 295.0, 5.0);

    // Safe: temperature above hydrate + margin
    assertTrue(envelope.isOperatingPointSafe(50.0, 300.0));

    // Unsafe: temperature below hydrate formation
    assertFalse(envelope.isOperatingPointSafe(50.0, 280.0));
  }

  @Test
  @DisplayName("Test margin calculation")
  void testMarginCalculation() {
    SafetyEnvelope envelope = new SafetyEnvelope("Hydrate", EnvelopeType.HYDRATE, 2);
    envelope.setDataPoint(0, 10.0, 270.0);
    envelope.setDataPoint(1, 100.0, 295.0);

    // Operating at 50 bara, 290 K
    double margin = envelope.calculateMarginToLimit(50.0, 290.0);

    // Limit at 50 bara should be interpolated (≈282.5 K)
    // Margin = 290 - 282.5 = 7.5 K
    assertTrue(margin > 0, "Should have positive margin");
    assertTrue(margin < 20, "Margin should be reasonable");
  }

  @Test
  @DisplayName("Test SafetyEnvelopeCalculator MDMT envelope")
  void testMDMTEnvelope() {
    SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(naturalGas);
    // Use more realistic pressure range (not starting from 1 bara)
    SafetyEnvelope envelope = calc.calculateMDMTEnvelope(5.0, 50.0, 300.0, 10);

    assertNotNull(envelope);
    assertEquals(EnvelopeType.MDMT, envelope.getType());

    double[] temps = envelope.getTemperature();
    double[] pressures = envelope.getPressure();

    // MDMT should be lower than design temp due to isentropic cooling
    for (int i = 0; i < temps.length; i++) {
      double t = temps[i];

      // At low pressures, MDMT is close to design temp (little cooling)
      // At high pressures, more isentropic cooling occurs
      assertTrue(t <= 300.0, "MDMT should be at or below design temp, got: " + t);
      assertTrue(t > 50.0, "MDMT should be reasonable (>50 K), got: " + t);

      // Higher pressure should result in lower MDMT
      if (i > 0) {
        assertTrue(temps[i] <= temps[i - 1] + 1.0, // Allow small tolerance
            "MDMT should decrease with increasing pressure");
      }
    }
  }

  @Test
  @DisplayName("Test multiple envelopes")
  void testMultipleEnvelopes() {
    SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(naturalGas);

    SafetyEnvelope co2Env = calc.calculateCO2FreezingEnvelope(1.0, 100.0, 5);
    SafetyEnvelope mdmtEnv = calc.calculateMDMTEnvelope(1.0, 100.0, 300.0, 5);

    SafetyEnvelope[] envelopes = {co2Env, mdmtEnv};

    // Test combined safety check
    boolean safe = SafetyEnvelopeCalculator.isOperatingPointSafe(envelopes, 50.0, 280.0);
    assertTrue(safe, "280 K at 50 bara should be safe for CO2 and MDMT");

    // Find most limiting
    SafetyEnvelope mostLimiting =
        SafetyEnvelopeCalculator.getMostLimitingEnvelope(envelopes, 50.0, 250.0);
    assertNotNull(mostLimiting);
  }

  @Test
  @DisplayName("Test envelope toString")
  void testEnvelopeToString() {
    SafetyEnvelopeCalculator calc = new SafetyEnvelopeCalculator(naturalGas);
    SafetyEnvelope envelope = calc.calculateCO2FreezingEnvelope(1.0, 100.0, 5);

    String str = envelope.toString();
    assertNotNull(str);
    assertTrue(str.contains("CO2 Freezing"));
    assertTrue(str.contains("5 points"));
  }
}
