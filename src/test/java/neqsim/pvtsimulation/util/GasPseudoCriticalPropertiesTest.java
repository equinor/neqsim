package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for GasPseudoCriticalProperties correlations.
 *
 * <p>
 * All methods now return Kelvin and bara by default.
 * </p>
 */
class GasPseudoCriticalPropertiesTest {

  // Conversion helpers for reference values
  private static double rToK(double r) {
    return r * 5.0 / 9.0;
  }

  private static double psiaToBara(double psia) {
    return psia / 14.5038;
  }

  // ========== STANDING CORRELATION ==========

  @Test
  void testStandingTpcMethane() {
    double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(0.554);
    // Standing in R: 168 + 325*0.554 - 12.5*0.554^2 ~ 344 R => ~191 K
    assertTrue(tpcK > rToK(330) && tpcK < rToK(360),
        "Standing Tpc for methane should be ~191 K, got " + tpcK);
  }

  @Test
  void testStandingPpcMethane() {
    double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureStanding(0.554);
    // Standing in psia: ~666 => ~45.9 bara
    assertTrue(ppcBara > psiaToBara(650) && ppcBara < psiaToBara(700),
        "Standing Ppc for methane should be ~45.9 bara, got " + ppcBara);
  }

  @Test
  void testStandingTypicalNaturalGas() {
    double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(0.75);
    double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureStanding(0.75);

    assertTrue(tpcK > rToK(370) && tpcK < rToK(430), "Tpc should be in K range, got " + tpcK);
    assertTrue(ppcBara > psiaToBara(630) && ppcBara < psiaToBara(700),
        "Ppc should be in bara range, got " + ppcBara);
  }

  // ========== SUTTON CORRELATION ==========

  @Test
  void testSuttonTpcTypicalGas() {
    double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.75);
    assertTrue(tpcK > rToK(370) && tpcK < rToK(440),
        "Sutton Tpc for gamma_g=0.75 should be ~216 K, got " + tpcK);
  }

  @Test
  void testSuttonPpcTypicalGas() {
    double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(0.75);
    assertTrue(ppcBara > psiaToBara(620) && ppcBara < psiaToBara(700),
        "Sutton Ppc for gamma_g=0.75 should be ~45.3 bara, got " + ppcBara);
  }

  @Test
  void testSuttonVsStandingDifference() {
    double tpcStanding = GasPseudoCriticalProperties.pseudoCriticalTemperatureStanding(0.75);
    double tpcSutton = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.75);

    double diff = Math.abs(tpcStanding - tpcSutton) / tpcStanding;
    assertTrue(diff < 0.10, "Standing and Sutton Tpc should differ by <10%, got " + diff * 100
        + "% (Standing=" + tpcStanding + ", Sutton=" + tpcSutton + ")");
  }

  @Test
  void testSuttonReturnsKelvinAndBara() {
    double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.75);
    double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(0.75);

    // Kelvin range: 200-280 K, bara range: 40-55 bara
    assertTrue(tpcK > 200 && tpcK < 280, "Tpc in K should be 200-280, got " + tpcK);
    assertTrue(ppcBara > 40 && ppcBara < 55, "Ppc in bara should be 40-55, got " + ppcBara);
  }

  // ========== PIPER-McCAIN-CORREDOR ==========

  @Test
  void testPiperSweetGas() {
    double tpcPiper =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.75, 0.0, 0.0, 0.0);
    double tpcSutton = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(0.75);

    double diff = Math.abs(tpcPiper - tpcSutton) / tpcSutton;
    assertTrue(diff < 0.15, "Piper (sweet gas) should be near Sutton, diff=" + diff * 100 + "%");
  }

  @Test
  void testPiperWithH2S() {
    double tpcClean =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.80, 0.0, 0.0, 0.0);
    double tpcSour =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.80, 0.15, 0.0, 0.0);

    // Now in Kelvin so threshold is smaller (5/9 of 1 R)
    assertTrue(Math.abs(tpcClean - tpcSour) > 0.5, "H2S should meaningfully change Tpc");
  }

  @Test
  void testPiperWithCO2() {
    double tpcClean =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.80, 0.0, 0.0, 0.0);
    double tpcCO2 =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.80, 0.0, 0.10, 0.0);

    assertTrue(Math.abs(tpcClean - tpcCO2) > 0.5, "CO2 should meaningfully change Tpc");
  }

  @Test
  void testPiperWithN2() {
    double tpcClean =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.75, 0.0, 0.0, 0.0);
    double tpcN2 = GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.75, 0.0, 0.0, 0.10);

    assertTrue(Math.abs(tpcClean - tpcN2) > 0.5, "N2 should meaningfully change Tpc");
  }

  @Test
  void testPiperReturnsKelvinAndBara() {
    double tpcK =
        GasPseudoCriticalProperties.pseudoCriticalTemperaturePiper(0.75, 0.05, 0.03, 0.02);
    double ppcBara =
        GasPseudoCriticalProperties.pseudoCriticalPressurePiper(0.75, 0.05, 0.03, 0.02);

    assertTrue(tpcK > 150 && tpcK < 350, "Piper Tpc in K should be reasonable, got " + tpcK);
    assertTrue(ppcBara > 20 && ppcBara < 80,
        "Piper Ppc in bara should be reasonable, got " + ppcBara);
  }

  // ========== WICHERT-AZIZ CORRECTION ==========

  @Test
  void testWichertAzizNoImpurities() {
    double tpcK = rToK(400.0); // 222.22 K
    double ppcBara = psiaToBara(660.0); // 45.51 bara

    double[] corrected = GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, 0.0, 0.0);

    assertEquals(tpcK, corrected[0], 1e-6, "No correction needed without impurities");
    assertEquals(ppcBara, corrected[1], 1e-6, "No correction needed without impurities");
  }

  @Test
  void testWichertAzizWithH2S() {
    double tpcK = rToK(400.0);
    double ppcBara = psiaToBara(660.0);

    double[] corrected =
        GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, 0.10, 0.0);

    assertTrue(corrected[0] < tpcK, "Tpc should decrease with H2S");
    assertTrue(Math.abs(corrected[1] - ppcBara) > 0.1, "Ppc should change with H2S");
  }

  @Test
  void testWichertAzizWithCO2() {
    double tpcK = rToK(400.0);
    double ppcBara = psiaToBara(660.0);

    double[] corrected =
        GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, 0.0, 0.10);

    assertTrue(corrected[0] < tpcK, "Tpc should decrease with CO2");
  }

  @Test
  void testWichertAzizCombinedAcidGas() {
    double tpcK = rToK(400.0);
    double ppcBara = psiaToBara(660.0);

    double[] corrected =
        GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, 0.10, 0.05);

    assertTrue(corrected[0] < tpcK, "Tpc should decrease with acid gases");
    assertTrue(corrected[0] > rToK(300), "Corrected Tpc should still be reasonable");
    assertTrue(corrected[1] > psiaToBara(400) && corrected[1] < psiaToBara(800),
        "Corrected Ppc should be reasonable");
  }

  @Test
  void testWichertAzizReturnsKelvinAndBara() {
    double tpcK = 222.0;
    double ppcBara = 45.5;

    double[] corrected =
        GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, 0.10, 0.05);

    assertTrue(corrected[0] > 0 && corrected[0] < 300,
        "Corrected Tpc should be reasonable Kelvin value");
    assertTrue(corrected[1] > 0 && corrected[1] < 100,
        "Corrected Ppc should be reasonable bara value");
  }

  // ========== REDUCED PROPERTIES ==========

  @Test
  void testPseudoReducedProperties() {
    // Now in Kelvin and bara
    double tpr = GasPseudoCriticalProperties.pseudoReducedTemperature(366.48, 222.22);
    assertEquals(366.48 / 222.22, tpr, 1e-10);

    double ppr = GasPseudoCriticalProperties.pseudoReducedPressure(137.9, 45.5);
    assertEquals(137.9 / 45.5, ppr, 1e-10);
  }

  // ========== INTEGRATION: SUTTON + WICHERT-AZIZ FOR SOUR GAS ==========

  @Test
  void testSuttonWithWichertAzizWorkflow() {
    double gammaG = 0.80;
    double yH2S = 0.10;
    double yCO2 = 0.05;

    // Step 1: Sutton pseudocriticals (now in K/bara)
    double tpcK = GasPseudoCriticalProperties.pseudoCriticalTemperatureSutton(gammaG);
    double ppcBara = GasPseudoCriticalProperties.pseudoCriticalPressureSutton(gammaG);

    // Step 2: Wichert-Aziz correction (K/bara in/out)
    double[] corrected =
        GasPseudoCriticalProperties.wichertAzizCorrection(tpcK, ppcBara, yH2S, yCO2);

    assertTrue(corrected[0] > rToK(300) && corrected[0] < rToK(500),
        "Corrected Tpc should be in range, got " + corrected[0]);
    assertTrue(corrected[1] > psiaToBara(400) && corrected[1] < psiaToBara(800),
        "Corrected Ppc should be in range, got " + corrected[1]);

    // Step 3: Calculate reduced properties at reservoir conditions
    double T = 366.48; // K (200 F)
    double P = 137.9; // bara (~2000 psia)
    double tpr = GasPseudoCriticalProperties.pseudoReducedTemperature(T, corrected[0]);
    double ppr = GasPseudoCriticalProperties.pseudoReducedPressure(P, corrected[1]);

    assertTrue(tpr > 1.0, "Tpr should be > 1 at reservoir conditions");
    assertTrue(ppr > 0.5, "Ppr should be > 0.5 at reservoir conditions");
  }
}
