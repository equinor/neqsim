package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.blackoil.io.EclipseBlackOilImporter;

class SystemBlackOilTest {
  private static String sampleDeck() {
    return String.join("\n",
        Arrays.asList("UNITS  METRIC", "", "DENSITY", "  800.0   1000.0   1.2  /", "", "PVTO",
            "-- Rs     Pb     Bo      mu_o", "  0.0     250    1.20    1.50",
            "  300     1.15   1.40", "  400     1.12   1.30 /", "  100.0   200    1.35    1.60",
            "  250     1.30   1.50", "  300     1.26   1.40 /", "  200.0   150    1.50    1.80",
            "  200     1.42   1.70", "  250     1.36   1.60 /", "/", "", "PVTG",
            "-- Rv     Pd     Bg       mu_g", "  0.0     120    0.0045   0.012",
            "  150     0.0042 0.011", "  200     0.0040 0.010 /", "/", "", "PVTW",
            "-- P      Bw      mu_w", "  50      1.02    0.5", "  150     1.01    0.6",
            "  250     1.00    0.7 /", "/"));
  }

  @Test
  void testBasicFlash() throws java.io.IOException {
    Path tmp = Files.createTempFile("MY_FLUID", ".DAT");
    Files.write(tmp, sampleDeck().getBytes(StandardCharsets.UTF_8));

    EclipseBlackOilImporter.Result res = EclipseBlackOilImporter.fromFile(tmp);
    assertNotNull(res);
    assertNotNull(res.pvt);
    assertNotNull(res.system);

    assertEquals(150.0, res.bubblePoint, 1e-9);

    BlackOilPVTTable pvt = res.pvt;

    double rho_o_sc = 800.0;
    double rho_g_sc = 1.2;
    double rho_w_sc = 1000.0;

    SystemBlackOil sys = new SystemBlackOil(pvt, rho_o_sc, rho_g_sc, rho_w_sc);

    sys.setPressure(170.0); // bar
    sys.setTemperature(100.0); // C
    sys.setStdTotals(1000.0, 500.0, 100.0); // Sm3

    BlackOilFlashResult result = sys.flash();

    assertNotNull(result);
    // System.out.println("Oil density: " + sys.getOilDensity());
    // System.out.println("Gas density: " + sys.getGasDensity());
    // System.out.println("Water density: " + sys.getWaterDensity());
    // System.out.println("Oil reservoir volume: " + sys.getOilReservoirVolume());
    // System.out.println("Gas reservoir volume: " + sys.getGasReservoirVolume());
    // System.out.println("Water reservoir volume: " + sys.getWaterReservoirVolume());
    // System.out.println("Oil viscosity: " + sys.getOilViscosity());
    // System.out.println("Gas viscosity: " + sys.getGasViscosity());
    // System.out.println("Water viscosity: " + sys.getWaterViscosity());

    assertTrue(sys.getOilDensity() > 0.0, "Oil density should be positive");
    assertTrue(sys.getGasDensity() > 0.0, "Gas density should be positive");
    assertTrue(sys.getWaterDensity() > 0.0, "Water density should be positive");
    assertTrue(sys.getOilReservoirVolume() > 0.0, "Oil reservoir volume should be positive");
    assertTrue(sys.getOilViscosity() > 0.0, "Oil viscosity should be positive");
    assertTrue(sys.getGasViscosity() > 0.0, "Gas viscosity should be positive");
    assertTrue(sys.getWaterViscosity() > 0.0, "Water viscosity should be positive");
  }

  @Test
  void testDirectPVTTable() {
    double[] boP = {100, 200, 300};
    double[] boV = {1.5, 1.4, 1.3};

    double[] bgP = {120, 150, 200};
    double[] bgV = {0.0045, 0.0042, 0.0040};

    double[] bwP = {50, 150, 250};
    double[] bwV = {1.02, 1.01, 1.00};

    double[] rsP = {0.0, 100.0, 200.0};
    double[] rsV = {1.2, 1.15, 1.12};

    double[] rvP = {0.0, 150.0, 200.0};
    double[] rvV = {0.0045, 0.0042, 0.0040};

    // Build a merged pressure grid and create records by interpolation
    double[] grid = {100, 120, 150, 170, 200, 250, 300};

    List<BlackOilPVTTable.Record> recs = new ArrayList<>();
    for (double p : grid) {
      double Bo = lerp(boP, boV, p);
      double Bg = lerp(bgP, bgV, p);
      double Bw = lerp(bwP, bwV, p);
      double Rs = lerp(rsP, rsV, p);
      double Rv = lerp(rvP, rvV, p);

      // simple positive viscosities (Pa·s) for the test
      double mu_o = 1.5e-3;
      double mu_g = 1.0e-5;
      double mu_w = 0.5e-3;

      recs.add(new BlackOilPVTTable.Record(p, Rs, Bo, mu_o, Bg, mu_g, Rv, Bw, mu_w));
    }

    double Pb = 150.0; // choose a reasonable bubblepoint in the grid
    BlackOilPVTTable pvt = new BlackOilPVTTable(recs, Pb);

    double rho_o_sc = 800.0, rho_g_sc = 1.2, rho_w_sc = 1000.0;
    SystemBlackOil sys = new SystemBlackOil(pvt, rho_o_sc, rho_g_sc, rho_w_sc);

    sys.setPressure(170.0);
    sys.setTemperature(100.0);
    sys.setStdTotals(1000.0, 500.0, 100.0);

    BlackOilFlashResult result = sys.flash();

    assertNotNull(result);
    assertTrue(sys.getOilDensity() > 0.0, "Oil density should be positive");
    assertTrue(sys.getGasDensity() > 0.0, "Gas density should be positive");
    assertTrue(sys.getWaterDensity() > 0.0, "Water density should be positive");
    assertTrue(sys.getOilReservoirVolume() > 0.0, "Oil reservoir volume should be positive");
    assertTrue(sys.getOilViscosity() > 0.0, "Oil viscosity should be positive");
    assertTrue(sys.getGasViscosity() > 0.0, "Gas viscosity should be positive");
    assertTrue(sys.getWaterViscosity() > 0.0, "Water viscosity should be positive");
  }

  /** linear interpolation with end clamping */
  private static double lerp(double[] xp, double[] yp, double x) {
    if (x <= xp[0])
      return yp[0];
    for (int i = 0; i < xp.length - 1; i++) {
      if (x <= xp[i + 1]) {
        double t = (x - xp[i]) / (xp[i + 1] - xp[i]);
        return yp[i] * (1.0 - t) + yp[i + 1] * t;
      }
    }
    return yp[yp.length - 1];
  }
}
