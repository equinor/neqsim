package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    PoolFireModel pool = new PoolFireModel(10.0, 0.06);
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
    // 5.8 * 10 = 58 m; 0.45 * 10 = 4.5 s
    assertEquals(58.0, D, 0.5);
    assertEquals(4.5, t, 0.05);
  }

  /**
   * Dry and very low humidity approach unit transmissivity in both radiation models.
   */
  @Test
  void dryAirTransmitsAllModeledRadiation() {
    JetFireModel jet = new JetFireModel(2.0, 50.0e6, 0.20);
    BLEVECalculator bleve = new BLEVECalculator(1000.0, 46.0e6, 0.30);
    assertEquals(0.4445289824297164, jet.transmissivity(50.0), 1.0e-12);
    assertEquals(jet.transmissivity(50.0), bleve.transmissivity(50.0));

    jet.setHumidity(0.0);
    bleve.setHumidity(0.0);
    assertEquals(1.0, jet.transmissivity(50.0));
    assertEquals(1.0, bleve.transmissivity(50.0));
    assertEquals(jet.totalRadiativePowerW() / (4.0 * Math.PI * 2500.0), jet.incidentHeatFlux(50.0), 1.0e-10);
    assertEquals(bleve.surfaceEmissivePowerWPerM2() * Math.pow(29.0 / 50.0, 2), bleve.incidentHeatFlux(50.0), 1.0e-9);
    assertEquals(1.0, jet.setHumidity(1.0e-12).transmissivity(50.0));
    assertEquals(1.0, bleve.setHumidity(1.0e-12).transmissivity(50.0));
  }

  /**
   * BLEVE accepts the fraction endpoints and rejects fractions outside the physical range.
   */
  @Test
  void bleveRadiativeFractionMustBeInRange() {
    for (double fraction : new double[] {-0.30, 1.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> new BLEVECalculator(1000.0, 46.0e6, fraction));
    }
    assertEquals(0.0, new BLEVECalculator(1000.0, 46.0e6, 0.0).incidentHeatFlux(100.0));
    assertTrue(new BLEVECalculator(1000.0, 46.0e6, 1.0).incidentHeatFlux(100.0) > 0.0);
  }

  /**
   * Preserve the legacy constructor results with the new two-input API.
   */
  @Test
  @SuppressWarnings("deprecation")
  void legacyPoolFireConstructorPreservesResults() {
    PoolFireModel pool = new PoolFireModel(10.0, 0.06);
    PoolFireModel legacy = new PoolFireModel(10.0, 0.06, 45.0e6, 800.0, 0.30);
    assertEquals(legacy.totalBurnRateKgPerS(), pool.totalBurnRateKgPerS());
    assertEquals(legacy.flameHeightM(), pool.flameHeightM());
    assertEquals(56143.30542946426, pool.surfaceEmissivePowerWPerM2(), 1.0e-8);
    assertEquals(legacy.incidentHeatFlux(20.0), pool.incidentHeatFlux(20.0));
  }

  /**
   * An unbracketed threshold must not be reported as a contour at either search endpoint.
   */
  @Test
  void unbracketedContoursReturnNaN() {
    PoolFireModel pool = new PoolFireModel(10.0, 0.06);
    BLEVECalculator bleve = new BLEVECalculator(1000.0, 46.0e6, 0.30).setHumidity(0.0);
    VCEModel vce = new VCEModel(1000.0, 46.0e6, 4);
    assertTrue(Double.isNaN(pool.distanceToFlux(12500.0)));
    assertTrue(Double.isNaN(bleve.distanceToFlux(1.0e6)));
    assertTrue(Double.isNaN(vce.distanceToOverpressure(20700.0)));
    assertTrue(Double.isNaN(pool.distanceToFlux(pool.incidentHeatFlux(1000.0) / 2.0)));
    assertTrue(Double.isNaN(bleve.distanceToFlux(bleve.incidentHeatFlux(5000.0) / 2.0)));
    assertTrue(Double.isNaN(vce.distanceToOverpressure(vce.overpressurePa(50000.0) / 2.0)));
  }

  /**
   * Reject targets that cannot represent a positive flux or overpressure.
   */
  @Test
  void contourTargetsMustBePositiveAndFinite() {
    PoolFireModel pool = new PoolFireModel(10.0, 0.06);
    BLEVECalculator bleve = new BLEVECalculator(1000.0, 46.0e6, 0.30);
    VCEModel vce = new VCEModel(1000.0, 46.0e6, 4);
    for (double target : new double[] {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> pool.distanceToFlux(target));
      assertThrows(IllegalArgumentException.class, () -> bleve.distanceToFlux(target));
      assertThrows(IllegalArgumentException.class, () -> vce.distanceToOverpressure(target));
    }
  }

  /**
   * Round-trip valid contours and locate the outer edge of the VCE near-field plateau.
   */
  @Test
  void bracketedContoursReturnMatchingDistances() {
    PoolFireModel pool = new PoolFireModel(10.0, 0.06);
    BLEVECalculator bleve = new BLEVECalculator(1000.0, 46.0e6, 0.30);
    VCEModel vce = new VCEModel(1000.0, 46.0e6, 4);
    assertEquals(25.0, pool.distanceToFlux(pool.incidentHeatFlux(25.0)), 1.0e-8);
    assertEquals(100.0, bleve.distanceToFlux(bleve.incidentHeatFlux(100.0)), 1.0e-8);
    assertEquals(100.0, vce.distanceToOverpressure(vce.overpressurePa(100.0)), 1.0e-8);
    assertEquals(5.1, pool.distanceToFlux(pool.incidentHeatFlux(5.1)), 1.0e-8);
    assertEquals(1000.0, pool.distanceToFlux(pool.incidentHeatFlux(1000.0)), 1.0e-8);
    double plateauEdge = Math.cbrt(vce.combustionEnergyJ() / 101325.0);
    assertEquals(plateauEdge, vce.distanceToOverpressure(vce.overpressurePa(0.1)), 1.0e-8);
  }
}
