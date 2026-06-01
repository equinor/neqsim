package neqsim.thermo.util.hydrogen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.util.hydrogen.ParaOrthoH2Correction.ConversionCatalyst;

/**
 * Tests for para/ortho hydrogen correction utilities.
 */
public class ParaOrthoH2CorrectionTest extends neqsim.NeqSimTest {

  @Test
  public void testEquilibriumParaFractionLimits() {
    assertEquals(0.25, ParaOrthoH2Correction.getNormalParaFraction(), 1.0e-12);

    double roomTemperature = ParaOrthoH2Correction.getEquilibriumParaFraction(300.0);
    double liquidNitrogenTemperature = ParaOrthoH2Correction.getEquilibriumParaFraction(77.0);
    double liquidHydrogenTemperature = ParaOrthoH2Correction.getEquilibriumParaFraction(20.0);

    assertTrue(roomTemperature > 0.24 && roomTemperature < 0.27);
    assertTrue(liquidNitrogenTemperature > 0.45 && liquidNitrogenTemperature < 0.60);
    assertTrue(liquidHydrogenTemperature > 0.995);
  }

  @Test
  public void testNormalToEquilibriumConversionHeatAtLiquidHydrogenTemperature() {
    double heatJPerKg = ParaOrthoH2Correction.getNormalToEquilibriumHeatJPerKg(20.0);

    assertTrue(heatJPerKg > 4.0e5);
    assertTrue(heatJPerKg < 6.5e5);
  }

  @Test
  public void testCpCorrectionIsCryogenicAndPositive() {
    double cryogenicCorrection = ParaOrthoH2Correction.getCpCorrectionJPerKgK(40.0);
    double warmCorrection = Math.abs(ParaOrthoH2Correction.getCpCorrectionJPerKgK(300.0));

    assertTrue(cryogenicCorrection > 0.0);
    assertTrue(warmCorrection < 50.0);
  }

  @Test
  public void testThermalConductivityCorrectionRelaxesAtWarmTemperature() {
    double cryogenicFactor = ParaOrthoH2Correction.getThermalConductivityCorrectionFactor(20.0);
    double warmFactor = ParaOrthoH2Correction.getThermalConductivityCorrectionFactor(300.0);

    assertTrue(cryogenicFactor < 1.0);
    assertEquals(1.0, warmFactor, 0.02);
  }

  @Test
  public void testCatalystEquilibrationTimesRankCorrectly() {
    double charcoal = ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(77.0,
        ConversionCatalyst.ACTIVATED_CHARCOAL);
    double ferricOxide = ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(77.0,
        ConversionCatalyst.HYDROUS_FERRIC_OXIDE);
    double noCatalyst =
        ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(77.0, ConversionCatalyst.NONE);
    double coldFerricOxide = ParaOrthoH2Correction.estimateEquilibrationTimeSeconds(40.0,
        ConversionCatalyst.HYDROUS_FERRIC_OXIDE);

    assertTrue(ferricOxide < charcoal);
    assertTrue(Double.isInfinite(noCatalyst));
    assertTrue(coldFerricOxide > ferricOxide);
  }
}
