package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Tests for {@link PitzerScaleActivityModel}. */
public class PitzerScaleActivityModelTest {

  /**
   * Reproduces the critically evaluated 25 C NaCl mean activity coefficients in Hamer and Wu (1972), Table 16, from
   * dilute solution through 6 mol/kg. DOI 10.1063/1.3253108.
   *
   * @throws Exception if the benchmark resource cannot be read
   */
  @Test
  public void reproducesHamerWuNaClMeanActivityCoefficients() throws Exception {
    ScalePredictionCalculator predictor = new ScalePredictionCalculator();
    predictor.setTemperatureCelsius(25.0);
    double a = predictor.getDebyeHuckelAParameter();

    InputStream input = getClass()
        .getResourceAsStream("/neqsim/pvtsimulation/flowassurance/nacl_mean_activity_hamer_wu_1972.csv");
    assertTrue(input != null, "Hamer-Wu benchmark data must be packaged with the tests");

    double maximumAbsoluteError = 0.0;
    double sumAbsoluteError = 0.0;
    double pitzerHighSalinitySquaredError = 0.0;
    double daviesHighSalinitySquaredError = 0.0;
    double bdotHighSalinitySquaredError = 0.0;
    int highSalinityCount = 0;
    int count = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        double molality = Double.parseDouble(fields[0]);
        double reference = Double.parseDouble(fields[1]);
        PitzerScaleActivityModel model = new PitzerScaleActivityModel(298.15, molality, a);
        double calculated = model.getMeanActivityCoefficient(PitzerScaleActivityModel.Salt.NACL, molality);
        double error = Math.abs(calculated - reference);
        maximumAbsoluteError = Math.max(maximumAbsoluteError, error);
        sumAbsoluteError += error;
        count++;
        if (molality >= 0.5) {
          double sqrtI = Math.sqrt(molality);
          double davies = Math.pow(10.0, -a * (sqrtI / (1.0 + sqrtI) - 0.3 * molality));
          double bdot = Math.pow(10.0, -a * sqrtI / (1.0 + 5.0 * 0.3283 * sqrtI) + 0.041 * molality);
          pitzerHighSalinitySquaredError += error * error;
          daviesHighSalinitySquaredError += Math.pow(davies - reference, 2.0);
          bdotHighSalinitySquaredError += Math.pow(bdot - reference, 2.0);
          highSalinityCount++;
        }
      }
    }

    assertEquals(29, count, "All published benchmark points must be exercised");
    assertTrue(maximumAbsoluteError < 0.006,
        "Maximum absolute gamma error must be below 0.006, got " + maximumAbsoluteError);
    assertTrue(sumAbsoluteError / count < 0.002,
        "Mean absolute gamma error must be below 0.002, got " + sumAbsoluteError / count);
    double pitzerRmse = Math.sqrt(pitzerHighSalinitySquaredError / highSalinityCount);
    double daviesRmse = Math.sqrt(daviesHighSalinitySquaredError / highSalinityCount);
    double bdotRmse = Math.sqrt(bdotHighSalinitySquaredError / highSalinityCount);
    assertTrue(pitzerRmse < 0.005, "Pitzer high-salinity RMSE must be below 0.005, got " + pitzerRmse);
    assertTrue(pitzerRmse < 0.1 * bdotRmse, "Pitzer must materially outperform B-dot for the published NaCl data");
    assertTrue(pitzerRmse < 0.1 * daviesRmse,
        "Pitzer must materially outperform Davies outside the Davies validity range");
  }

  /** Binary Pitzer assigns ion-specific coefficients instead of one common divalent coefficient. */
  @Test
  public void producesMineralSpecificActivityProducts() {
    PitzerScaleActivityModel model = new PitzerScaleActivityModel(298.15, 2.0, 0.5085);
    double barite = model.getActivityCoefficientProduct(PitzerScaleActivityModel.Ion.BARIUM,
        PitzerScaleActivityModel.Ion.SULPHATE);
    double calcite = model.getActivityCoefficientProduct(PitzerScaleActivityModel.Ion.CALCIUM,
        PitzerScaleActivityModel.Ion.CARBONATE);

    assertTrue(barite > 0.0 && Double.isFinite(barite));
    assertTrue(calcite > 0.0 && Double.isFinite(calcite));
    assertNotEquals(barite, calcite, 1.0e-6,
        "Pitzer binary parameters should distinguish barite and calcite activity products");
  }

  /** Coupled scale equilibrium exposes the selected model and coefficients in its report. */
  @Test
  public void integratesWithCoupledScaleEquilibrium() {
    ScalePredictionCalculator predictor = new ScalePredictionCalculator();
    predictor.setTemperatureCelsius(70.0);
    predictor.setPressureBara(100.0);
    predictor.setSodiumConcentration(70000.0);
    predictor.setCalciumConcentration(3000.0);
    predictor.setBariumConcentration(200.0);
    predictor.setSulphateConcentration(1000.0);
    predictor.setBicarbonateConcentration(500.0);
    predictor.setPH(7.0);
    predictor.setTotalDissolvedSolids(150000.0);

    MultiMineralScaleEquilibrium equilibrium = new MultiMineralScaleEquilibrium(predictor)
        .setActivityModel(MultiMineralScaleEquilibrium.ActivityModel.PITZER_BINARY).solve();

    assertTrue(equilibrium.getActivityCoefficientsUsed().containsKey("Ba++"));
    assertTrue(equilibrium.getResults().get("BaSO4").getActivityCoefficientProduct() > 0.0);
    assertTrue(equilibrium.toJson().contains("Binary Pitzer trace-ion mapping"));
  }
}
