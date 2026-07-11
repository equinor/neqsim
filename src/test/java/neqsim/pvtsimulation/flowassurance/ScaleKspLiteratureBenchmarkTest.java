package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Literature-benchmark validation of the mineral-scale thermodynamics.
 *
 * <p>
 * This suite pins each mineral's solubility product at 25 &deg;C to published values and checks the end-to-end
 * single-salt solubility predicted by the coupled equilibrium solver. It is the objective "is the model correct?" gate:
 * a drift in any Ksp correlation (or an activity/solver regression) breaks a benchmark here.
 * </p>
 *
 * <p>
 * Reference log10(Ksp) at 25 &deg;C, 1 atm:
 * </p>
 * <ul>
 * <li>Calcite CaCO3: -8.48 (Plummer &amp; Busenberg, 1982)</li>
 * <li>Barite BaSO4: -9.97 (Monnin, 1999; Blount, 1977)</li>
 * <li>Celestite SrSO4: -6.63 (Reardon &amp; Armstrong, 1987)</li>
 * <li>Anhydrite CaSO4: -4.36 (Blount &amp; Dickson, 1973)</li>
 * <li>Siderite FeCO3: -10.89 (Greenberg &amp; Tomson, 1992)</li>
 * </ul>
 */
public class ScaleKspLiteratureBenchmarkTest {

  /** Tolerance on log10(Ksp) — about a factor of 1.8; tight enough to catch a wrong correlation. */
  private static final double LOG_TOL = 0.25;

  private ScalePredictionCalculator at25C() {
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(25.0);
    p.setPressureBara(1.013);
    return p;
  }

  private void assertLogKsp(double ksp, double expectedLog, String mineral) {
    assertTrue(ksp > 0.0 && !Double.isNaN(ksp), mineral + " Ksp must be a positive finite number, was " + ksp);
    double log = Math.log10(ksp);
    assertEquals(expectedLog, log, LOG_TOL,
        mineral + " log10(Ksp) at 25 C should match literature (" + expectedLog + "), was " + log);
  }

  @Test
  @DisplayName("Calcite Ksp(25C) matches Plummer & Busenberg (1982)")
  void testCalciteKsp() {
    assertLogKsp(at25C().getKspCalcite(), -8.48, "calcite");
  }

  @Test
  @DisplayName("Barite Ksp(25C) matches Monnin (1999)")
  void testBariteKsp() {
    assertLogKsp(at25C().getKspBarite(), -9.97, "barite");
  }

  @Test
  @DisplayName("Celestite Ksp(25C) matches Reardon & Armstrong (1987)")
  void testCelestiteKsp() {
    assertLogKsp(at25C().getKspCelestite(), -6.63, "celestite");
  }

  @Test
  @DisplayName("Anhydrite Ksp(25C) matches Blount & Dickson (1973)")
  void testAnhydriteKsp() {
    assertLogKsp(at25C().getKspAnhydrite(), -4.36, "anhydrite");
  }

  @Test
  @DisplayName("Siderite Ksp(25C) matches Greenberg & Tomson (1992) — regression guard for the T^2 bug")
  void testSideriteKsp() {
    double log = Math.log10(at25C().getKspSiderite());
    assertEquals(-10.89, log, LOG_TOL, "siderite log10(Ksp) at 25 C should be ~-10.89, was " + log);
    // Explicit guard against the earlier spurious +2.518e-5*T^2 term (which gave ~-8.6).
    assertTrue(log < -10.0, "siderite must not regress to the over-soluble ~-8.6 value, was " + log);
  }

  @Test
  @DisplayName("Barite single-salt solubility in near-pure water matches sqrt(Ksp)")
  void testBariteSolubilityBenchmark() {
    // Excess, equimolar Ba and SO4 in near-pure water so the residual free ions equal the solubility.
    ScalePredictionCalculator p = new ScalePredictionCalculator();
    p.setTemperatureCelsius(25.0);
    p.setPressureBara(1.013);
    p.setCalciumConcentration(0.0);
    p.setSodiumConcentration(0.0);
    p.setBariumConcentration(1000.0); // ~7.3e-3 mol/L
    p.setSulphateConcentration(700.0); // ~7.3e-3 mol/L (equimolar)
    p.setBicarbonateConcentration(0.0);
    p.setTotalDissolvedSolids(20.0); // near-pure water -> activity coefficients near 1
    p.setCO2PartialPressure(0.0);
    p.setPH(7.0);

    MultiMineralScaleEquilibrium eq = new MultiMineralScaleEquilibrium(p);
    eq.solve();

    double residualBa = eq.getResidualFreeIonMolPerL("Ba++");
    // Literature barite solubility at 25 C is ~1.0e-5 mol/L (~2.3 mg/L as BaSO4); activity lowers gamma
    // slightly so the equilibrium residual sits a little above sqrt(Ksp).
    assertTrue(residualBa > 0.7e-5 && residualBa < 2.0e-5,
        "Barite residual free Ba should be ~1e-5 mol/L (literature solubility), was " + residualBa);
    // Barite must sit at equilibrium (SI ~ 0) and have precipitated.
    assertEquals(0.0, eq.getResults().get("BaSO4").getFinalSI(), 1.0e-3);
    assertTrue(eq.getPrecipitatedMolPerL("BaSO4") > 0.0, "Barite should precipitate from the supersaturated brine");
  }

  @Test
  @DisplayName("Carbonate Ksp decreases with temperature (retrograde solubility)")
  void testCalciteRetrogradeTemperatureTrend() {
    ScalePredictionCalculator cold = new ScalePredictionCalculator();
    cold.setTemperatureCelsius(25.0);
    cold.setPressureBara(1.013);
    ScalePredictionCalculator hot = new ScalePredictionCalculator();
    hot.setTemperatureCelsius(90.0);
    hot.setPressureBara(1.013);
    assertTrue(hot.getKspCalcite() < cold.getKspCalcite(),
        "Calcite should be less soluble (lower Ksp) at higher temperature");
    assertTrue(hot.getKspSiderite() < cold.getKspSiderite(),
        "Siderite should be less soluble (lower Ksp) at higher temperature");
  }
}
