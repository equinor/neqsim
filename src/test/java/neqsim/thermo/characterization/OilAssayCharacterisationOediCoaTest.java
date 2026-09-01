package neqsim.thermo.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public-data matrix qualification of refinery-assay bulk density bookkeeping. */
public class OilAssayCharacterisationOediCoaTest {
  private static final double MAX_SPECIFIC_GRAVITY_ERROR = 0.006;
  private static final double MAX_API_GRAVITY_ERROR = 1.5;

  private static final CoaCase[] CASES = {
      new CoaCase(920, "Turner Valley, Alberta", 0.779, 50.1, 70.5, 0.754, 13.2, 0.813, 3.7, 0.831, 12.6, 0.889,
          0.781647),
      new CoaCase(50146, "Ranch W, Texas", 0.847, 35.6, 17.8, 0.794, 0.0, 0.0, 78.7, 0.856, 3.5, 0.907, 0.846749),
      new CoaCase(56337, "Manderson, Wyoming", 0.771, 52.0, 88.7, 0.767, 8.7, 0.794, 0.0, 0.0, 2.6, 0.815, 0.770597),
      new CoaCase(60205, "South McCallum, Colorado", 0.765, 53.5, 75.0, 0.741, 19.8, 0.804, 4.2, 0.842, 1.0, 0.873,
          0.759036),
      new CoaCase(68120, "Vermilion Block 14, Louisiana", 0.782, 49.4, 49.7, 0.749, 38.5, 0.805, 0.0, 0.0, 11.8, 0.832,
          0.780354) };

  @Test
  public void testCompleteFourCategoryCoaDensityMatrix() {
    double maximumSpecificGravityError = 0.0;
    double sumSpecificGravityError = 0.0;
    double sumSquaredSpecificGravityError = 0.0;
    double maximumApiGravityError = 0.0;
    double sumApiGravityError = 0.0;
    double sumSquaredApiGravityError = 0.0;

    for (CoaCase coaCase : CASES) {
      String message = "COA sample " + coaCase.sampleId + " (" + coaCase.location + ")";
      assertEquals(100.0, coaCase.totalVolumePercent(), 1e-12, message);

      SystemInterface system = new SystemSrkEos(288.706, 1.01325);
      OilAssayCharacterisation assay = system.getOilAssayCharacterisation();
      assay.clearCuts();
      addCategories(assay, coaCase, false);

      assertEquals(coaCase.expectedAdditiveVolumeSpecificGravity, coaCase.calculateAdditiveVolumeSpecificGravity(),
          1e-12, message);
      assertEquals(coaCase.expectedAdditiveVolumeSpecificGravity, assay.getBulkSpecificGravity(), 1e-12, message);
      assertTrue(Double.isFinite(assay.getBulkSpecificGravity()), message);
      assertTrue(Double.isFinite(assay.getBulkApiGravity()), message);
      assertEquals(0, system.getNumberOfComponents(), message);

      double specificGravityError = Math.abs(assay.getBulkSpecificGravity() - coaCase.publishedSpecificGravity);
      double apiGravityError = Math.abs(assay.getBulkApiGravity() - coaCase.publishedApiGravity);
      assertTrue(specificGravityError <= MAX_SPECIFIC_GRAVITY_ERROR, message);
      assertTrue(apiGravityError <= MAX_API_GRAVITY_ERROR, message);

      OilAssayCharacterisation reversed = new SystemSrkEos(288.706, 1.01325).getOilAssayCharacterisation();
      reversed.clearCuts();
      addCategories(reversed, coaCase, true);
      assertEquals(assay.getBulkSpecificGravity(), reversed.getBulkSpecificGravity(), 1e-12, message);

      maximumSpecificGravityError = Math.max(maximumSpecificGravityError, specificGravityError);
      sumSpecificGravityError += specificGravityError;
      sumSquaredSpecificGravityError += specificGravityError * specificGravityError;
      maximumApiGravityError = Math.max(maximumApiGravityError, apiGravityError);
      sumApiGravityError += apiGravityError;
      sumSquaredApiGravityError += apiGravityError * apiGravityError;
    }

    assertEquals(0.005964, maximumSpecificGravityError, 1e-12);
    assertEquals(0.0021822, sumSpecificGravityError / CASES.length, 1e-12);
    assertEquals(0.003016973019435194, Math.sqrt(sumSquaredSpecificGravityError / CASES.length), 1e-12);
    assertEquals(1.4206704293340522, maximumApiGravityError, 1e-12);
    assertEquals(0.5108445008224933, sumApiGravityError / CASES.length, 1e-12);
    assertEquals(0.7133115463932141, Math.sqrt(sumSquaredApiGravityError / CASES.length), 1e-12);
  }

  private static void addCategories(OilAssayCharacterisation assay, CoaCase coaCase, boolean reverse) {
    if (reverse) {
      addCategory(assay, "Residuum", coaCase.residuumVolumePercent, coaCase.residuumSpecificGravity);
      addCategory(assay, "GasOil", coaCase.gasOilVolumePercent, coaCase.gasOilSpecificGravity);
      addCategory(assay, "Kerosene", coaCase.keroseneVolumePercent, coaCase.keroseneSpecificGravity);
      addCategory(assay, "GasolineNaphtha", coaCase.gasolineVolumePercent, coaCase.gasolineSpecificGravity);
      return;
    }
    addCategory(assay, "GasolineNaphtha", coaCase.gasolineVolumePercent, coaCase.gasolineSpecificGravity);
    addCategory(assay, "Kerosene", coaCase.keroseneVolumePercent, coaCase.keroseneSpecificGravity);
    addCategory(assay, "GasOil", coaCase.gasOilVolumePercent, coaCase.gasOilSpecificGravity);
    addCategory(assay, "Residuum", coaCase.residuumVolumePercent, coaCase.residuumSpecificGravity);
  }

  private static void addCategory(OilAssayCharacterisation assay, String name, double volumePercent,
      double specificGravity) {
    if (volumePercent == 0.0) {
      return;
    }
    assay.addCut(new AssayCut(name).withVolumePercent(volumePercent).withSpecificGravity(specificGravity));
  }

  private static final class CoaCase {
    private final int sampleId;
    private final String location;
    private final double publishedSpecificGravity;
    private final double publishedApiGravity;
    private final double gasolineVolumePercent;
    private final double gasolineSpecificGravity;
    private final double keroseneVolumePercent;
    private final double keroseneSpecificGravity;
    private final double gasOilVolumePercent;
    private final double gasOilSpecificGravity;
    private final double residuumVolumePercent;
    private final double residuumSpecificGravity;
    private final double expectedAdditiveVolumeSpecificGravity;

    private CoaCase(int sampleId, String location, double publishedSpecificGravity, double publishedApiGravity,
        double gasolineVolumePercent, double gasolineSpecificGravity, double keroseneVolumePercent,
        double keroseneSpecificGravity, double gasOilVolumePercent, double gasOilSpecificGravity,
        double residuumVolumePercent, double residuumSpecificGravity, double expectedAdditiveVolumeSpecificGravity) {
      this.sampleId = sampleId;
      this.location = location;
      this.publishedSpecificGravity = publishedSpecificGravity;
      this.publishedApiGravity = publishedApiGravity;
      this.gasolineVolumePercent = gasolineVolumePercent;
      this.gasolineSpecificGravity = gasolineSpecificGravity;
      this.keroseneVolumePercent = keroseneVolumePercent;
      this.keroseneSpecificGravity = keroseneSpecificGravity;
      this.gasOilVolumePercent = gasOilVolumePercent;
      this.gasOilSpecificGravity = gasOilSpecificGravity;
      this.residuumVolumePercent = residuumVolumePercent;
      this.residuumSpecificGravity = residuumSpecificGravity;
      this.expectedAdditiveVolumeSpecificGravity = expectedAdditiveVolumeSpecificGravity;
    }

    private double totalVolumePercent() {
      return gasolineVolumePercent + keroseneVolumePercent + gasOilVolumePercent + residuumVolumePercent;
    }

    private double calculateAdditiveVolumeSpecificGravity() {
      return (gasolineVolumePercent * gasolineSpecificGravity + keroseneVolumePercent * keroseneSpecificGravity
          + gasOilVolumePercent * gasOilSpecificGravity + residuumVolumePercent * residuumSpecificGravity) / 100.0;
    }
  }
}
