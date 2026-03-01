package neqsim.pvtsimulation.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for WaterPropertyCorrelations.
 *
 * <p>
 * All methods use Kelvin and bara as default units.
 * </p>
 */
class WaterPropertyCorrelationsTest {

  // Standard test conditions: 366.48 K (200 F), 206.84 bara (3000 psia), 30000 ppm salinity
  private static final double TEMP_K = 366.48; // 200 F
  private static final double PRESS_BARA = 206.84; // 3000 psia
  private static final double SALINITY_PPM = 30000.0;

  // ========== WATER FVF ==========

  @Test
  void testWaterFVFMcCainReasonableRange() {
    double bw = WaterPropertyCorrelations.waterFVFMcCain(TEMP_K, PRESS_BARA);
    // Bw is typically 1.0 - 1.07 res bbl/STB
    assertTrue(bw > 0.98 && bw < 1.10, "McCain Bw should be 0.98-1.10, got " + bw);
  }

  @Test
  void testWaterFVFMcCainIncreasesWithTemperature() {
    double bw1 = WaterPropertyCorrelations.waterFVFMcCain(330.0, 100.0); // ~134 F
    double bw2 = WaterPropertyCorrelations.waterFVFMcCain(400.0, 100.0); // ~260 F
    assertTrue(bw2 > bw1, "Bw should increase with temperature");
  }

  @Test
  void testWaterFVFOsifReasonableRange() {
    double bw = WaterPropertyCorrelations.waterFVFOsif(TEMP_K, PRESS_BARA, SALINITY_PPM);
    assertTrue(bw > 0.95 && bw < 1.15, "Osif Bw should be 0.95-1.15, got " + bw);
  }

  // ========== WATER VISCOSITY ==========

  @Test
  void testDeadWaterViscosityReasonableRange() {
    double mu = WaterPropertyCorrelations.deadWaterViscosityMcCain(TEMP_K, SALINITY_PPM);
    // Water viscosity at 200 F is around 0.3 - 0.5 cP
    assertTrue(mu > 0.1 && mu < 2.0, "Dead water viscosity should be 0.1-2.0 cP, got " + mu);
  }

  @Test
  void testWaterViscosityDecreasesWithTemperature() {
    double mu1 = WaterPropertyCorrelations.deadWaterViscosityMcCain(330.0, 0.0);
    double mu2 = WaterPropertyCorrelations.deadWaterViscosityMcCain(400.0, 0.0);
    assertTrue(mu2 < mu1, "Viscosity should decrease with temperature");
  }

  @Test
  void testWaterViscosityWithPressure() {
    double mu = WaterPropertyCorrelations.waterViscosityMcCain(TEMP_K, PRESS_BARA, SALINITY_PPM);
    double muDead = WaterPropertyCorrelations.deadWaterViscosityMcCain(TEMP_K, SALINITY_PPM);
    // Pressure correction makes viscosity slightly higher
    assertTrue(mu >= muDead * 0.95, "Live water viscosity should be near dead viscosity");
  }

  // ========== WATER COMPRESSIBILITY ==========

  @Test
  void testWaterCompressibilityReasonableRange() {
    double cw =
        WaterPropertyCorrelations.waterCompressibilityMcCain(TEMP_K, PRESS_BARA, SALINITY_PPM);
    // Water compressibility is small, ~3-5e-5 1/psia => ~4-7e-4 1/bara
    assertTrue(cw > 0.0 && cw < 0.01, "Water compressibility should be small positive, got " + cw);
  }

  // ========== BRINE DENSITY ==========

  @Test
  void testBrineDensityReasonableRange() {
    double rho = WaterPropertyCorrelations.brineDensityBatzleWang(TEMP_K, PRESS_BARA, 0.03);
    // Brine density at 200 F, 3000 psia, 3 wt% salt: ~1000 - 1100 kg/m3
    assertTrue(rho > 900 && rho < 1200, "Brine density should be 900-1200 kg/m3, got " + rho);
  }

  @Test
  void testWaterDensityLessThanBrine() {
    double rhoW = WaterPropertyCorrelations.waterDensity(TEMP_K, PRESS_BARA);
    double rhoB = WaterPropertyCorrelations.brineDensityBatzleWang(TEMP_K, PRESS_BARA, 0.05);
    assertTrue(rhoB > rhoW, "Brine should be denser than pure water");
  }

  @Test
  void testWaterDensityReasonableRange() {
    double rho = WaterPropertyCorrelations.waterDensity(TEMP_K, PRESS_BARA);
    assertTrue(rho > 800 && rho < 1200, "Water density should be 800-1200 kg/m3, got " + rho);
  }

  // ========== SOLUTION GAS-WATER RATIO ==========

  @Test
  void testSolutionGasWaterRatioPositive() {
    double rsw =
        WaterPropertyCorrelations.solutionGasWaterRatioCulberson(TEMP_K, PRESS_BARA, SALINITY_PPM);
    assertTrue(rsw > 0, "Rsw should be positive at reservoir conditions, got " + rsw);
  }

  @Test
  void testSolutionGasWaterRatioIncreasesWithPressure() {
    double rsw1 = WaterPropertyCorrelations.solutionGasWaterRatioCulberson(TEMP_K, 34.47, 0.0); // ~500
                                                                                                // psia
    double rsw2 = WaterPropertyCorrelations.solutionGasWaterRatioCulberson(TEMP_K, PRESS_BARA, 0.0); // ~3000
                                                                                                     // psia
    assertTrue(rsw2 > rsw1, "Rsw should increase with pressure");
  }

  // ========== SURFACE TENSION ==========

  @Test
  void testSurfaceTensionReasonableRange() {
    double sigma = WaterPropertyCorrelations.waterGasSurfaceTension(TEMP_K, PRESS_BARA);
    // Water-gas IFT typically 20-75 dyne/cm
    assertTrue(sigma > 5 && sigma < 80, "Surface tension should be 5-80 dyne/cm, got " + sigma);
  }

  @Test
  void testSurfaceTensionDecreasesWithTemperature() {
    double s1 = WaterPropertyCorrelations.waterGasSurfaceTension(310.0, 20.0);
    double s2 = WaterPropertyCorrelations.waterGasSurfaceTension(420.0, 20.0);
    assertTrue(s2 < s1, "Surface tension should decrease with temperature");
  }

  // ========== SUMMARY ==========

  @Test
  void testWaterPropertiesSummaryContainsAllKeys() {
    Map<String, Double> summary =
        WaterPropertyCorrelations.waterPropertiesSummary(TEMP_K, PRESS_BARA, SALINITY_PPM);

    assertTrue(summary.containsKey("temperature_K"));
    assertTrue(summary.containsKey("pressure_bara"));
    assertTrue(summary.containsKey("salinity_ppm"));
    assertTrue(summary.containsKey("Bw_McCain_resBblPerSTB"));
    assertTrue(summary.containsKey("muW_cP"));
    assertTrue(summary.containsKey("cw_invBara"));
    assertTrue(summary.containsKey("rhoW_kgPerM3"));
    assertTrue(summary.containsKey("Rsw_Sm3PerSm3"));
    assertTrue(summary.containsKey("sigma_wg_dynePerCm"));

    assertEquals(TEMP_K, summary.get("temperature_K"), 1e-6);
    assertEquals(PRESS_BARA, summary.get("pressure_bara"), 1e-6);
  }
}
