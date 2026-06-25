package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReciprocatingCompressorPerformance}.
 *
 * @author ESOL
 * @version 1.0
 */
class ReciprocatingCompressorPerformanceTest {

  @Test
  void basicScreeningOutputs() {
    ReciprocatingCompressorPerformance c = new ReciprocatingCompressorPerformance("K-101").setPressures(10.0, 30.0)
	.setSuctionTemperature(313.15).setClearanceFraction(0.12).setPolytropicExponent(1.3).setDisplacementRate(0.5);
    c.calculate();
    assertEquals(3.0, c.getPressureRatio(), 1.0e-9);
    assertTrue(c.getVolumetricEfficiency() > 0.70 && c.getVolumetricEfficiency() < 0.85);
    assertEquals(0.5 * c.getVolumetricEfficiency(), c.getActualInletCapacity(), 1.0e-9);
    assertEquals(403.5, c.getDischargeTemperature(), 2.0);
    assertTrue(c.getWarnings().isEmpty());
  }

  @Test
  void higherPressureRatioLowersVolumetricEfficiency() {
    ReciprocatingCompressorPerformance low = new ReciprocatingCompressorPerformance().setPressures(10.0, 20.0)
	.setSuctionTemperature(313.15).setDisplacementRate(0.5).calculate();
    ReciprocatingCompressorPerformance high = new ReciprocatingCompressorPerformance().setPressures(10.0, 50.0)
	.setSuctionTemperature(313.15).setDisplacementRate(0.5).calculate();
    assertTrue(high.getVolumetricEfficiency() < low.getVolumetricEfficiency());
  }

  @Test
  void rodLoadUtilisationAndWarning() {
    ReciprocatingCompressorPerformance c = new ReciprocatingCompressorPerformance().setPressures(10.0, 30.0)
	.setSuctionTemperature(313.15).setDisplacementRate(0.5).setRodLoad(0.05, 0.005, 80000.0).calculate();
    assertTrue(c.getRodLoadUtilization() > 1.0);
    assertFalse(c.getWarnings().isEmpty());
  }

  @Test
  void highRatioRaisesDischargeTemperatureAlarm() {
    ReciprocatingCompressorPerformance c = new ReciprocatingCompressorPerformance().setPressures(5.0, 50.0)
	.setSuctionTemperature(320.0).setPolytropicExponent(1.4).setDisplacementRate(0.3).calculate();
    assertTrue(c.getDischargeTemperature() > 408.15);
    assertFalse(c.getWarnings().isEmpty());
  }

  @Test
  void invalidSetupRejected() {
    assertThrows(IllegalStateException.class, () -> new ReciprocatingCompressorPerformance()
	.setSuctionTemperature(300.0).setDisplacementRate(0.5).calculate());
    assertThrows(IllegalStateException.class, () -> new ReciprocatingCompressorPerformance().setPressures(30.0, 10.0)
	.setSuctionTemperature(300.0).setDisplacementRate(0.5).calculate());
  }

  @Test
  void readingBeforeCalculateThrows() {
    ReciprocatingCompressorPerformance c = new ReciprocatingCompressorPerformance().setPressures(10.0, 30.0);
    assertThrows(IllegalStateException.class, () -> c.getVolumetricEfficiency());
  }

  @Test
  void jsonExport() {
    ReciprocatingCompressorPerformance c = new ReciprocatingCompressorPerformance().setPressures(10.0, 30.0)
	.setSuctionTemperature(313.15).setDisplacementRate(0.5).calculate();
    String json = c.toJson();
    assertTrue(json.contains("volumetricEfficiency"));
    assertTrue(json.contains("dischargeTemperature"));
  }
}
