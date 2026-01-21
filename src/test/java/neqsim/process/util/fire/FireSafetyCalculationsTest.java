package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.util.fire.FireHeatTransferCalculator.SurfaceTemperatureResult;

/**
 * Unit tests for fire heat load, wall temperature, and rupture calculations.
 */
public class FireSafetyCalculationsTest {
  @Test
  public void testApi521HeatLoad() {
    double wettedArea = 50.0; // m2
    double heatLoad = FireHeatLoadCalculator.api521PoolFireHeatLoad(wettedArea, 1.0);
    assertEquals(1.5305e8, heatLoad, 1.0e6);
  }

  @Test
  public void testStefanBoltzmannHeatFlux() {
    double heatFlux =
        FireHeatLoadCalculator.generalizedStefanBoltzmannHeatFlux(0.35, 0.8, 1200.0, 350.0);
    assertEquals(3.268e4, heatFlux, 50.0);
  }

  @Test
  public void testWallTemperaturesForWettedZone() {
    SurfaceTemperatureResult result = FireHeatTransferCalculator.calculateWallTemperatures(300.0,
        1200.0, 0.02, 45.0, 1500.0, 30.0);
    assertEquals(2.6129e4, result.heatFlux(), 100.0);
    assertEquals(317.4, result.innerWallTemperatureK(), 0.2);
    assertEquals(329.0, result.outerWallTemperatureK(), 0.2);
  }

  @Test
  public void testRuptureAssessment() {
    double vonMises = VesselRuptureCalculator.vonMisesStress(5.0e6, 1.0, 0.02);
    double allowable = 2.4e8;
    assertEquals(2.165e8, vonMises, 1.0e6);
    assertFalse(VesselRuptureCalculator.isRuptureLikely(vonMises, allowable));
    assertTrue(VesselRuptureCalculator.ruptureMargin(vonMises, allowable) > 0.0);
  }
}
