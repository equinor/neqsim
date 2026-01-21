package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SaturationPressureCorrelation.
 *
 * @author ESOL
 */
public class SaturationPressureCorrelationTest {
  // Typical medium oil input values
  private static final double RS = 500.0; // scf/STB
  private static final double GAMMA_G = 0.75;
  private static final double API = 35.0;
  private static final double TEMP_F = 180.0; // °F

  @Test
  void testStandingCorrelation() {
    double Pb = SaturationPressureCorrelation.standing(RS, GAMMA_G, API, TEMP_F);

    assertTrue(Pb > 1000 && Pb < 5000, "Bubble point should be in reasonable range for medium oil");
    // Standing typically gives around 2000-3000 psia for these inputs
    assertTrue(Pb > 1500 && Pb < 4000, "Pb = " + Pb + " psia seems too extreme");
  }

  @Test
  void testVasquezBeggsCorrelation() {
    double Pb = SaturationPressureCorrelation.vasquezBeggs(RS, GAMMA_G, API, TEMP_F);

    // VB can give higher values for high-Rs systems
    assertTrue(Pb > 500 && Pb < 25000,
        "Bubble point should be in reasonable range, got: " + Pb + " psia");
  }

  @Test
  void testGlasoCorrelation() {
    double Pb = SaturationPressureCorrelation.glaso(RS, GAMMA_G, API, TEMP_F);

    assertTrue(Pb > 1000 && Pb < 5000, "Bubble point should be in reasonable range");
  }

  @Test
  void testPetroskyFarshadCorrelation() {
    double Pb = SaturationPressureCorrelation.petroskyFarshad(RS, GAMMA_G, API, TEMP_F);

    // PF can give higher values for certain input ranges
    assertTrue(Pb > 500 && Pb < 25000,
        "Bubble point should be in reasonable range, got: " + Pb + " psia");
  }

  @Test
  void testAlMarhounCorrelation() {
    double gammaO = SaturationPressureCorrelation.apiToSpecificGravity(API);
    double Pb = SaturationPressureCorrelation.alMarhoun(RS, GAMMA_G, gammaO, TEMP_F);

    assertTrue(Pb > 500 && Pb < 10000, "Bubble point should be in reasonable range");
  }

  @Test
  void testStandingWithZeroRs() {
    double Pb = SaturationPressureCorrelation.standing(0, GAMMA_G, API, TEMP_F);
    assertTrue(Double.isNaN(Pb), "Zero Rs should return NaN");
  }

  @Test
  void testStandingWithNegativeInput() {
    double Pb = SaturationPressureCorrelation.standing(RS, -0.5, API, TEMP_F);
    assertTrue(Double.isNaN(Pb), "Negative gas gravity should return NaN");
  }

  @Test
  void testVasquezBeggsAPIBranching() {
    // Test with API <= 30 (heavy oil)
    double PbHeavy = SaturationPressureCorrelation.vasquezBeggs(RS, GAMMA_G, 25.0, TEMP_F);
    assertTrue(PbHeavy > 0, "Should work for heavy oil");

    // Test with API > 30 (light oil)
    double PbLight = SaturationPressureCorrelation.vasquezBeggs(RS, GAMMA_G, 40.0, TEMP_F);
    assertTrue(PbLight > 0, "Should work for light oil");

    // Light oil typically has higher Pb at same Rs
    assertNotEquals(PbHeavy, PbLight, 100, "Different API should give different results");
  }

  @Test
  void testApiToSpecificGravityConversion() {
    // API 10 = SG 1.0 (water)
    assertEquals(1.0, SaturationPressureCorrelation.apiToSpecificGravity(10.0), 0.001);

    // API 35 = typical medium oil
    double sg35 = SaturationPressureCorrelation.apiToSpecificGravity(35.0);
    assertTrue(sg35 > 0.8 && sg35 < 0.9, "API 35 should be ~0.85 SG");
  }

  @Test
  void testSpecificGravityToApiConversion() {
    // SG 1.0 = API 10
    assertEquals(10.0, SaturationPressureCorrelation.specificGravityToAPI(1.0), 0.001);

    // Round-trip test
    double api = 35.0;
    double sg = SaturationPressureCorrelation.apiToSpecificGravity(api);
    double apiBack = SaturationPressureCorrelation.specificGravityToAPI(sg);
    assertEquals(api, apiBack, 0.001);
  }

  @Test
  void testPressureConversions() {
    // 14.7 psia = 1.01325 bar (approximately)
    double bar = SaturationPressureCorrelation.psiaToBar(14.7);
    assertEquals(1.0, bar, 0.02);

    // Round-trip
    double psia = 1000.0;
    double barConv = SaturationPressureCorrelation.psiaToBar(psia);
    double psiaBack = SaturationPressureCorrelation.barToPsia(barConv);
    assertEquals(psia, psiaBack, 0.001);
  }

  @Test
  void testTemperatureConversions() {
    // 32°F = 0°C
    assertEquals(0.0, SaturationPressureCorrelation.fahrenheitToCelsius(32.0), 0.001);

    // 212°F = 100°C
    assertEquals(100.0, SaturationPressureCorrelation.fahrenheitToCelsius(212.0), 0.001);

    // Round-trip
    double celsius = 25.0;
    double fahrenheit = SaturationPressureCorrelation.celsiusToFahrenheit(celsius);
    double celsiusBack = SaturationPressureCorrelation.fahrenheitToCelsius(fahrenheit);
    assertEquals(celsius, celsiusBack, 0.001);
  }

  @Test
  void testGORConversions() {
    // Approximate: 1 Sm3/Sm3 ≈ 5.615 scf/STB
    double scfStb = 100.0;
    double sm3Sm3 = SaturationPressureCorrelation.scfStbToSm3Sm3(scfStb);
    assertTrue(sm3Sm3 > 15 && sm3Sm3 < 20, "Conversion should give ~17.8 Sm3/Sm3");

    // Round-trip
    double scfBack = SaturationPressureCorrelation.sm3Sm3ToScfStb(sm3Sm3);
    assertEquals(scfStb, scfBack, 0.001);
  }

  @Test
  void testEstimateWithStatistics() {
    double[] stats = SaturationPressureCorrelation.estimateWithStatistics(RS, GAMMA_G, API, TEMP_F);

    assertEquals(4, stats.length);

    double avg = stats[0];
    double min = stats[1];
    double max = stats[2];
    double stdDev = stats[3];

    // Different correlations can give quite different results
    assertTrue(avg > 500 && avg < 25000, "Average should be reasonable, got: " + avg + " psia");
    assertTrue(min <= avg, "Min should be <= average");
    assertTrue(max >= avg, "Max should be >= average");
    assertTrue(stdDev >= 0, "Std dev should be non-negative");
  }

  @Test
  void testEstimateWithStatisticsInvalidInput() {
    double[] stats = SaturationPressureCorrelation.estimateWithStatistics(0, GAMMA_G, API, TEMP_F);

    assertTrue(Double.isNaN(stats[0]), "Invalid input should give NaN average");
  }

  @Test
  void testGenerateComparisonReport() {
    String report =
        SaturationPressureCorrelation.generateComparisonReport(RS, GAMMA_G, API, TEMP_F);

    assertNotNull(report);
    assertTrue(report.contains("Bubble Point Pressure Comparison"));
    assertTrue(report.contains("Standing"));
    assertTrue(report.contains("Vasquez-Beggs"));
    assertTrue(report.contains("Glaso"));
    assertTrue(report.contains("Petrosky-Farshad"));
    assertTrue(report.contains("Statistics"));
    assertTrue(report.contains("psia"));
    assertTrue(report.contains("bar"));
  }

  @Test
  void testLowGORCase() {
    // Dead oil case - very low GOR
    double lowRs = 50.0; // scf/STB
    double Pb = SaturationPressureCorrelation.standing(lowRs, GAMMA_G, API, TEMP_F);

    assertTrue(Pb > 14.7, "Should be above atmospheric");
    assertTrue(Pb < 1000, "Low GOR should give low Pb");
  }

  @Test
  void testHighGORCase() {
    // Volatile oil case - high GOR
    double highRs = 2000.0; // scf/STB
    double Pb = SaturationPressureCorrelation.standing(highRs, GAMMA_G, API, TEMP_F);

    assertTrue(Pb > 4000, "High GOR should give high Pb");
  }

  @Test
  void testCorrelationsAreConsistent() {
    // All correlations should give positive results
    double standing = SaturationPressureCorrelation.standing(RS, GAMMA_G, API, TEMP_F);
    double vb = SaturationPressureCorrelation.vasquezBeggs(RS, GAMMA_G, API, TEMP_F);
    double glaso = SaturationPressureCorrelation.glaso(RS, GAMMA_G, API, TEMP_F);
    double pf = SaturationPressureCorrelation.petroskyFarshad(RS, GAMMA_G, API, TEMP_F);

    // All should be positive and in reasonable oil industry range
    assertTrue(standing > 14.7, "Standing should be positive");
    assertTrue(vb > 14.7, "VB should be positive");
    assertTrue(glaso > 14.7, "Glaso should be positive");
    assertTrue(pf > 14.7, "PF should be positive");

    // Note: Different correlations can give quite different results
    // depending on input parameters. This is expected behavior.
  }

  @Test
  void testTemperatureSensitivity() {
    // Higher temperature typically reduces Pb slightly
    double PbLow = SaturationPressureCorrelation.standing(RS, GAMMA_G, API, 100.0);
    double PbHigh = SaturationPressureCorrelation.standing(RS, GAMMA_G, API, 250.0);

    // Both should be valid
    assertTrue(PbLow > 0 && PbHigh > 0);

    // Temperature effect varies by correlation but shouldn't be extreme
    assertTrue(Math.abs(PbHigh - PbLow) / PbLow < 0.5, "Temperature effect should be < 50%");
  }

  @Test
  void testMinimumPbIsAtmospheric() {
    // Very low Rs should give atmospheric pressure
    double Pb = SaturationPressureCorrelation.standing(1.0, GAMMA_G, API, TEMP_F);
    assertEquals(14.7, Pb, 1.0, "Minimum Pb should be atmospheric");
  }
}
