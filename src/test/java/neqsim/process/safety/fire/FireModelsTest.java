package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class FireModelsTest {

  @Test
  void jetFireFluxDecreasesWithDistance() {
    JetFireModel jet = new JetFireModel(2.0, 50.0e6, 0.20);
    assertTrue(jet.incidentHeatFlux(20.0) > jet.incidentHeatFlux(80.0));
  }

  @Test
  void jetFireDistanceToFluxConsistent() {
    JetFireModel jet = new JetFireModel(2.0, 50.0e6, 0.20);
    double r = jet.distanceToFlux(12500.0);
    double q = jet.incidentHeatFlux(r);
    // 5% tolerance because of the iteration
    assertEquals(12500.0, q, 12500.0 * 0.05);
  }

  @Test
  void poolFireGeometryAndFluxArePositive() {
    PoolFireModel pool = new PoolFireModel(10.0, 0.06, 45.0e6, 800.0, 0.30);
    assertTrue(pool.flameHeightM() > 0.0);
    assertTrue(pool.surfaceEmissivePowerWPerM2() > 0.0);
    assertTrue(pool.incidentHeatFlux(20.0) > 0.0);
  }

  @Test
  void vceOverpressureDecreasesWithDistance() {
    // 1000 kg propane (ΔHc≈46 MJ/kg) class-4 cloud
    VCEModel vce = new VCEModel(1000.0, 46.0e6, 4);
    assertTrue(vce.overpressurePa(20.0) > vce.overpressurePa(200.0));
  }

  @Test
  void bleveDiameterAndDurationFromCorrelation() {
    // M = 1000 kg
    BLEVECalculator b = new BLEVECalculator(1000.0, 46.0e6, 0.30);
    double D = b.fireballDiameterM();
    double t = b.fireballDurationS();
    // 5.8 * 10 = 58 m;  0.45 * 10 = 4.5 s
    assertEquals(58.0, D, 0.5);
    assertEquals(4.5, t, 0.05);
  }
}
