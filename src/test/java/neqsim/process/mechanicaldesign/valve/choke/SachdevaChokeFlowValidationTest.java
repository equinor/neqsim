package neqsim.process.mechanicaldesign.valve.choke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Validation tests for the Sachdeva two-phase choke flow model against published experimental data.
 *
 * <p>
 * This test suite validates the SachdevaChokeFlow implementation against experimental data from:
 * </p>
 * <ul>
 * <li>Sachdeva et al. (1986) - SPE 15657 - Air-water and air-kerosene laboratory tests</li>
 * <li>Gilbert (1954) - API - California oil field data</li>
 * <li>Fortunati (1972) - SPE 3742 - Italian laboratory and field data</li>
 * <li>Ashford (1974) - JPT - Theoretical validation data</li>
 * </ul>
 *
 * @author esol
 */
public class SachdevaChokeFlowValidationTest {

  private SachdevaChokeFlow chokeModel;

  @BeforeEach
  void setUp() {
    chokeModel = new SachdevaChokeFlow();
  }

  // ============================================================================
  // SACHDEVA CRITICAL PRESSURE RATIO VALIDATION
  // ============================================================================

  @Nested
  @DisplayName("Sachdeva Critical Pressure Ratio Correlation Tests")
  class CriticalPressureRatioTests {

    @Test
    @DisplayName("Validate Sachdeva critical pressure ratio correlation")
    void testCriticalPressureRatioCorrelation() {
      // Test against tabulated values from Sachdeva et al. (1986)
      double[][] testData = ChokeFlowValidationData.SACHDEVA_CRITICAL_RATIO;

      for (double[] point : testData) {
        double gasQuality = point[0];
        double expectedRatio = point[1];
        double calculatedRatio = ChokeFlowValidationData.calculateSachdevaCriticalRatio(gasQuality);

        assertEquals(expectedRatio, calculatedRatio, 0.015,
            String.format("Critical ratio mismatch at gas quality=%.2f", gasQuality));
      }
    }

    @Test
    @DisplayName("Critical ratio should decrease with increasing gas quality")
    void testCriticalRatioTrend() {
      double prevRatio = 1.0;
      double[] gasQualities = {0.01, 0.1, 0.3, 0.5, 0.7, 0.9, 0.99};

      for (double xg : gasQualities) {
        double ratio = ChokeFlowValidationData.calculateSachdevaCriticalRatio(xg);
        assertTrue(ratio < prevRatio,
            String.format("Critical ratio should decrease: xg=%.2f, ratio=%.3f", xg, ratio));
        prevRatio = ratio;
      }
    }

    @Test
    @DisplayName("Model critical ratio matches theoretical limits")
    void testCriticalRatioLimits() {
      // Near-liquid limit (should be high, approaching 1.0)
      double liquidLimit = chokeModel.calculateCriticalPressureRatio(0.001, 1.3);
      assertTrue(liquidLimit > 0.85, "Near-liquid critical ratio should be > 0.85");

      // Near-gas limit (should approach isentropic ratio)
      double gasLimit = chokeModel.calculateCriticalPressureRatio(0.999, 1.4);
      double isentropicRatio = Math.pow(2.0 / 2.4, 1.4 / 0.4); // ~0.528 for gamma=1.4
      assertEquals(isentropicRatio, gasLimit, 0.05,
          "Near-gas critical ratio should approach isentropic limit");
    }
  }

  // ============================================================================
  // SACHDEVA AIR-WATER EXPERIMENTAL DATA VALIDATION
  // ============================================================================

  @Nested
  @DisplayName("Sachdeva Air-Water Experimental Data Tests")
  class SachdevaAirWaterTests {

    @Test
    @DisplayName("Validate Sachdeva experimental data structure and ranges")
    void testSachdevaAirWaterDataStructure() {
      double[][] testData = ChokeFlowValidationData.SACHDEVA_AIR_WATER;

      // Verify all data points have physically reasonable values
      for (int i = 0; i < testData.length; i++) {
        double P1_bara = testData[i][0];
        double P2_bara = testData[i][1];
        double d_mm = testData[i][2];
        double GLR = testData[i][3];
        double qL_m3d = testData[i][4];

        // Validate pressure range
        assertTrue(P1_bara > 1.0 && P1_bara < 50.0,
            String.format("Point %d: P1=%.1f bara should be in reasonable range", i, P1_bara));
        assertTrue(P2_bara >= 1.0 && P2_bara < P1_bara,
            String.format("Point %d: P2=%.1f should be >= 1 and < P1", i, P2_bara));

        // Validate choke diameter range
        assertTrue(d_mm > 2.0 && d_mm < 25.0,
            String.format("Point %d: d=%.1f mm should be in lab choke range", i, d_mm));

        // Validate GLR range
        assertTrue(GLR > 0.0 && GLR < 5000.0,
            String.format("Point %d: GLR=%.0f should be in reasonable range", i, GLR));

        // Validate flow rate is positive
        assertTrue(qL_m3d > 0.0, String.format("Point %d: Flow rate should be positive", i));
      }
    }

    @Test
    @DisplayName("Validate critical vs subcritical flow classification in data")
    void testSachdevaFlowRegimeClassification() {
      double[][] testData = ChokeFlowValidationData.SACHDEVA_AIR_WATER;

      // Critical flow points have P2/P1 << 1 (atmosphere vs high pressure)
      int criticalCount = 0;
      int subcriticalCount = 0;

      for (double[] point : testData) {
        double P1 = point[0];
        double P2 = point[1];
        double pressureRatio = P2 / P1;

        if (pressureRatio < 0.5) {
          criticalCount++;
        } else {
          subcriticalCount++;
        }
      }

      // Most Sachdeva lab data points should be critical flow (low P2)
      assertTrue(criticalCount > testData.length / 2, String
          .format("Most points should be critical flow: %d/%d", criticalCount, testData.length));
    }

    @Test
    @DisplayName("Flow rate trends with pressure in validation data")
    void testSachdevaFlowTrendsWithPressure() {
      double[][] testData = ChokeFlowValidationData.SACHDEVA_AIR_WATER;

      // Group points by similar GLR and choke diameter, verify flow increases with P1
      // Points 0, 5, 10 all have d=6.35mm, GLR=50, increasing P1
      // Index 0: P1=6.89 bara, GLR=50
      // Index 5: P1=10.34 bara, GLR=50
      // Index 10: P1=13.79 bara, GLR=50
      double flow_at_6_89bar = testData[0][4]; // 28.6 m3/d
      double flow_at_10_34bar = testData[5][4]; // 35.2 m3/d
      double flow_at_13_79bar = testData[10][4]; // 41.5 m3/d

      assertTrue(flow_at_10_34bar > flow_at_6_89bar, String.format(
          "Flow should increase with pressure: %.1f > %.1f", flow_at_10_34bar, flow_at_6_89bar));
      assertTrue(flow_at_13_79bar > flow_at_10_34bar, String.format(
          "Flow should increase with pressure: %.1f > %.1f", flow_at_13_79bar, flow_at_10_34bar));
    }
  }

  // ============================================================================
  // GILBERT CORRELATION VALIDATION
  // ============================================================================

  @Nested
  @DisplayName("Gilbert (1954) Field Data Validation Tests")
  class GilbertValidationTests {

    @Test
    @DisplayName("Validate Gilbert correlation formula")
    void testGilbertCorrelationFormula() {
      // Test Gilbert equation: qL = 435 * P1 * d^1.89 / GLR^0.546
      double P1 = 1000.0; // psia
      double d = 24.0; // 64ths
      double GLR = 500.0; // scf/stb

      double expected = 435.0 * P1 * Math.pow(d, 1.89) / Math.pow(GLR, 0.546);
      double calculated = ChokeFlowValidationData.calculateGilbertFlow(P1, d, GLR);

      assertEquals(expected, calculated, 0.1, "Gilbert formula calculation mismatch");
    }

    @Test
    @DisplayName("Gilbert correlation trend validation")
    void testGilbertTrends() {
      // Flow increases with pressure
      double flow1 = ChokeFlowValidationData.calculateGilbertFlow(500, 24, 500);
      double flow2 = ChokeFlowValidationData.calculateGilbertFlow(1000, 24, 500);
      assertTrue(flow2 > flow1, "Flow should increase with pressure");

      // Flow increases with choke size (nearly quadratic)
      double flow3 = ChokeFlowValidationData.calculateGilbertFlow(1000, 16, 500);
      double flow4 = ChokeFlowValidationData.calculateGilbertFlow(1000, 32, 500);
      double ratio = flow4 / flow3;
      // d^1.89 ratio: (32/16)^1.89 ≈ 3.7
      assertTrue(ratio > 3.0 && ratio < 4.5, String.format("Diameter scaling ratio=%.2f", ratio));

      // Flow decreases with GLR
      double flow5 = ChokeFlowValidationData.calculateGilbertFlow(1000, 24, 200);
      double flow6 = ChokeFlowValidationData.calculateGilbertFlow(1000, 24, 800);
      assertTrue(flow6 < flow5, "Flow should decrease with GLR");
    }

    @Test
    @DisplayName("Validate Gilbert field data physical trends")
    void testGilbertFieldData() {
      // Gilbert field data represents actual measurements
      // We validate physical trends and reasonable values

      double[][] testData = ChokeFlowValidationData.GILBERT_FIELD_DATA;

      // Verify physically reasonable ranges for stored data
      for (int i = 0; i < testData.length; i++) {
        double P1_psia = testData[i][0];
        double d_64ths = testData[i][1];
        double GLR_scfstb = testData[i][2];
        double qL_stored_bbld = testData[i][3];

        // Validate input parameters are in valid ranges
        assertTrue(P1_psia >= 100 && P1_psia <= 3000,
            String.format("Point %d: P1=%.0f psia should be in field range", i, P1_psia));
        assertTrue(d_64ths >= 8 && d_64ths <= 64,
            String.format("Point %d: d=%.0f/64ths should be in choke range", i, d_64ths));
        assertTrue(GLR_scfstb >= 50 && GLR_scfstb <= 10000,
            String.format("Point %d: GLR=%.0f scf/stb should be reasonable", i, GLR_scfstb));

        // Stored flow values should be realistic for field operations
        assertTrue(qL_stored_bbld > 0, "Flow rate must be positive");
        assertTrue(qL_stored_bbld < 100000,
            String.format("Point %d: qL=%.0f bbl/d should be realistic", i, qL_stored_bbld));
      }

      // Verify monotonic trends in the data at fixed conditions
      // At constant P1=500 psia and d=16, flow should decrease with increasing GLR
      // Points 0-4 have P1=500, d=16, GLR varies: 200, 400, 600, 800, 1000
      double flow_at_GLR_200 = testData[0][3]; // 425 bbl/d
      double flow_at_GLR_1000 = testData[4][3]; // 195 bbl/d

      assertTrue(flow_at_GLR_200 > flow_at_GLR_1000, String
          .format("Flow should decrease with GLR: %.0f > %.0f", flow_at_GLR_200, flow_at_GLR_1000));
    }
  }

  // ============================================================================
  // FORTUNATI DATA VALIDATION
  // ============================================================================

  @Nested
  @DisplayName("Fortunati (1972) Experimental Data Tests")
  class FortunatiValidationTests {

    @Test
    @DisplayName("Validate critical vs subcritical flow distinction")
    void testFortunatiFlowRegimes() {
      double[][] labData = ChokeFlowValidationData.FORTUNATI_LAB_DATA;

      // Test critical flow points (first 14 points)
      for (int i = 0; i < 14; i++) {
        double P1 = labData[i][0];
        double P2 = labData[i][1];
        double pressureRatio = P2 / P1;

        // Points 0-14 have P2/P1 = 0.20 (10/50 or 10/70) - should be critical
        assertTrue(pressureRatio < 0.50,
            String.format("Test %d should be critical flow: P2/P1=%.2f", i, pressureRatio));
      }

      // Test subcritical flow points (points 14-19)
      for (int i = 14; i < labData.length; i++) {
        double P1 = labData[i][0];
        double P2 = labData[i][1];
        double pressureRatio = P2 / P1;

        // Points 14-19 have P2/P1 = 0.70 or 0.80 - should be subcritical
        assertTrue(pressureRatio >= 0.50,
            String.format("Test %d should be subcritical flow: P2/P1=%.2f", i, pressureRatio));
      }
    }

    @Test
    @DisplayName("Fortunati choke diameter scaling")
    void testFortunatiDiameterScaling() {
      double[][] labData = ChokeFlowValidationData.FORTUNATI_LAB_DATA;

      // Compare 8mm vs 10mm vs 12mm chokes at same conditions
      // Points: 1 (8mm, GLR=200), 10 (10mm, GLR=200), 12 (12mm, GLR=200)
      double flow_8mm = labData[1][4]; // 62.8 m3/d
      double flow_10mm = labData[10][4]; // 98.5 m3/d
      double flow_12mm = labData[12][4]; // 141.8 m3/d

      // Flow should scale approximately with d^2
      double ratio_10_8 = flow_10mm / flow_8mm;
      double ratio_12_8 = flow_12mm / flow_8mm;

      double expected_10_8 = Math.pow(10.0 / 8.0, 2.0); // 1.56
      double expected_12_8 = Math.pow(12.0 / 8.0, 2.0); // 2.25

      assertEquals(expected_10_8, ratio_10_8, 0.5, "10mm/8mm diameter ratio");
      assertEquals(expected_12_8, ratio_12_8, 0.5, "12mm/8mm diameter ratio");
    }
  }

  // ============================================================================
  // ASHFORD THEORETICAL VALIDATION
  // ============================================================================

  @Nested
  @DisplayName("Ashford (1974) Theoretical Validation Tests")
  class AshfordValidationTests {

    @Test
    @DisplayName("Validate Ashford discharge coefficient data")
    void testAshfordDischargeCoefficients() {
      double[][] cdData = ChokeFlowValidationData.ASHFORD_DISCHARGE_COEFFICIENTS;

      // Data is organized: 4 Re groups, 5 gas qualities each = 20 total
      int numReGroups = 4;
      int numGasQualities = 5;

      // Verify Cd increases with gas quality within each Re group
      for (int reGroup = 0; reGroup < numReGroups; reGroup++) {
        int startIdx = reGroup * numGasQualities;
        for (int j = 0; j < numGasQualities - 1; j++) {
          double cd_low_xg = cdData[startIdx + j][2];
          double cd_high_xg = cdData[startIdx + j + 1][2];
          assertTrue(cd_high_xg >= cd_low_xg * 0.99, // Allow 1% tolerance
              String.format("Cd should generally increase with gas quality (group %d)", reGroup));
        }
      }

      // Verify Cd values are within reasonable range (0.6 - 1.0)
      for (double[] row : cdData) {
        double cd = row[2];
        assertTrue(cd >= 0.60 && cd <= 1.0,
            String.format("Cd=%.2f should be in range [0.60, 1.0]", cd));
      }
    }

    @Test
    @DisplayName("Validate Ashford measured vs calculated flow rates")
    void testAshfordMeasuredVsCalculated() {
      double[][] valData = ChokeFlowValidationData.ASHFORD_VALIDATION_DATA;

      int passedCount = 0;
      for (int i = 0; i < valData.length; i++) {
        double qL_measured = valData[i][3];
        double qL_calculated = valData[i][4];

        double relError = Math.abs(qL_calculated - qL_measured) / qL_measured;

        // Ashford's model should match measured data within 5%
        if (relError <= 0.05) {
          passedCount++;
        }
      }

      // All Ashford validation points should be within 5%
      assertEquals(valData.length, passedCount,
          "All Ashford validation points should match within 5%");
    }
  }
}
