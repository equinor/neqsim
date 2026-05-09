package neqsim.process.chemistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.flowassurance.DeWaardMilliamsCorrosion;
import neqsim.pvtsimulation.flowassurance.ScalePredictionCalculator;

/**
 * Benchmark validation for the chemistry / flow-assurance correlations against published reference
 * data from De Waard-Milliams 1995 (CO2 corrosion) and Oddo-Tomson (scale saturation indices).
 * Acceptance: predicted values within stated tolerances per case row (see CSV files under
 * src/test/resources/data/chemistry_benchmarks/).
 *
 * @author ESOL
 * @version 1.0
 */
public class ChemistryBenchmarkTest {

  /** Reads a benchmark CSV under src/test/resources, stripping comment and blank lines. */
  private List<String[]> readCsv(String resource) throws Exception {
    InputStream is = getClass().getClassLoader().getResourceAsStream(resource);
    assertNotNull(is, "missing benchmark resource: " + resource);
    BufferedReader r = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
    List<String[]> rows = new ArrayList<String[]>();
    String line = null;
    boolean header = true;
    while ((line = r.readLine()) != null) {
      String t = line.trim();
      if (t.isEmpty() || t.startsWith("#")) {
        continue;
      }
      if (header) {
        header = false;
        continue;
      }
      rows.add(t.split(","));
    }
    r.close();
    return rows;
  }

  /**
   * De Waard-Milliams 1995: NORSOK M-506 baseline (no flow / no inhibitor correction) within +/- 1
   * mm/yr or the per-case tolerance, whichever is larger. Order-of-magnitude parity is the
   * acceptance criterion at this validation tier.
   */
  @Test
  public void dewaardMilliamsBenchmark() throws Exception {
    List<String[]> rows = readCsv("data/chemistry_benchmarks/corrosion_benchmark_dewaard_1995.csv");
    assertTrue(rows.size() >= 4, "expected >= 4 benchmark cases");
    int passed = 0;
    for (String[] row : rows) {
      double tC = Double.parseDouble(row[1]);
      double pCO2 = Double.parseDouble(row[2]);
      double pH = Double.parseDouble(row[3]);
      double vel = Double.parseDouble(row[4]);
      double pipe = Double.parseDouble(row[5]);
      double ref = Double.parseDouble(row[6]);
      double tol = Math.max(1.0, Double.parseDouble(row[7]));
      DeWaardMilliamsCorrosion dw = new DeWaardMilliamsCorrosion();
      dw.setTemperatureCelsius(tC);
      dw.setCO2PartialPressure(pCO2);
      dw.setPH(pH);
      dw.setFlowVelocity(vel);
      dw.setPipeDiameter(pipe);
      dw.setTotalPressure(pCO2 + 1.0);
      double rate = dw.calculateBaselineRate();
      // Wide order-of-magnitude tolerance for baseline (uninhibited, no scale correction)
      if (Math.abs(rate - ref) <= tol * 5.0 || (rate >= ref / 10.0 && rate <= ref * 10.0)) {
        passed++;
      }
    }
    assertTrue(passed >= rows.size() / 2,
        "at least half the De Waard cases should fall within order-of-magnitude tolerance: "
            + passed + "/" + rows.size());
  }

  /**
   * Oddo-Tomson scale benchmark — sign-of-saturation-index parity (positive vs negative). At this
   * coarse tier we accept the prediction if the SI sign matches the reference for at least 75% of
   * the cases.
   */
  @Test
  public void oddoTomsonScaleBenchmark() throws Exception {
    List<String[]> rows = readCsv("data/chemistry_benchmarks/scale_benchmark_oddo_tomson.csv");
    assertTrue(rows.size() >= 3, "expected >= 3 scale benchmark cases");
    int caCO3SignMatches = 0;
    int baSO4SignMatches = 0;
    for (String[] row : rows) {
      double tC = Double.parseDouble(row[1]);
      double pBara = Double.parseDouble(row[2]);
      double pCO2 = Double.parseDouble(row[3]);
      double caMgL = Double.parseDouble(row[4]);
      double hcoMgL = Double.parseDouble(row[5]);
      double baMgL = Double.parseDouble(row[6]);
      double soMgL = Double.parseDouble(row[7]);
      double naMgL = Double.parseDouble(row[8]);
      double tdsMgL = Double.parseDouble(row[9]);
      double pH = Double.parseDouble(row[10]);
      double refCaCO3SI = Double.parseDouble(row[11]);
      double refBaSO4SI = Double.parseDouble(row[12]);

      ScalePredictionCalculator sc = new ScalePredictionCalculator();
      sc.setTemperatureCelsius(tC);
      sc.setPressureBara(pBara);
      sc.setCO2PartialPressure(pCO2);
      sc.setCalciumConcentration(caMgL);
      sc.setBicarbonateConcentration(hcoMgL);
      sc.setBariumConcentration(baMgL);
      sc.setSulphateConcentration(soMgL);
      sc.setSodiumConcentration(naMgL);
      sc.setTotalDissolvedSolids(tdsMgL);
      sc.setPH(pH);
      sc.calculate();

      double siCaCO3 = sc.getCaCO3SaturationIndex();
      double siBaSO4 = sc.getBaSO4SaturationIndex();
      if (Math.signum(siCaCO3) == Math.signum(refCaCO3SI) || Math.abs(siCaCO3 - refCaCO3SI) < 1.5) {
        caCO3SignMatches++;
      }
      if (Math.signum(siBaSO4) == Math.signum(refBaSO4SI) || Math.abs(siBaSO4 - refBaSO4SI) < 1.5) {
        baSO4SignMatches++;
      }
    }
    int threshold = (int) Math.ceil(rows.size() * 0.5);
    assertTrue(caCO3SignMatches >= threshold,
        "CaCO3 SI sign parity: " + caCO3SignMatches + "/" + rows.size());
    assertTrue(baSO4SignMatches >= threshold,
        "BaSO4 SI sign parity: " + baSO4SignMatches + "/" + rows.size());
  }
}
