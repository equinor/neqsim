package neqsim.process.util.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FirePreset}.
 *
 * <p>
 * Verifies that the screening-level fire presets produce absorbed-flux magnitudes of the right order (tens to hundreds
 * of kW/m²), that jet fires exceed pool fires, that the absorbed flux decreases as the exposed surface heats up
 * (re-radiation), and that input validation rejects non-physical surface temperatures.
 *
 * @author ESOL
 * @version 1.0
 */
public class FirePresetTest {

  /** Pool-fire peak nominal flux should be of the order of 100 kW/m² onto a cold surface. */
  @Test
  public void poolFirePeakNominalFluxIsAround100kW() {
    double flux = FirePreset.POOL_FIRE_PEAK.nominalAbsorbedFluxWPerM2();
    assertTrue(flux > 80.0e3 && flux < 120.0e3,
        "pool fire peak nominal flux should be ~100 kW/m2, was " + flux + " W/m2");
  }

  /** Jet-fire peak nominal flux should be of the order of 250 kW/m² onto a cold surface. */
  @Test
  public void jetFirePeakNominalFluxIsAround250kW() {
    double flux = FirePreset.JET_FIRE_PEAK.nominalAbsorbedFluxWPerM2();
    assertTrue(flux > 200.0e3 && flux < 300.0e3,
        "jet fire peak nominal flux should be ~250 kW/m2, was " + flux + " W/m2");
  }

  /** A jet fire must impose a higher heat load than a pool fire of the same exposure level. */
  @Test
  public void jetFireExceedsPoolFire() {
    assertTrue(
        FirePreset.JET_FIRE_PEAK.nominalAbsorbedFluxWPerM2() > FirePreset.POOL_FIRE_PEAK.nominalAbsorbedFluxWPerM2(),
        "jet fire peak should exceed pool fire peak");
    assertTrue(FirePreset.JET_FIRE_BACKGROUND.nominalAbsorbedFluxWPerM2() > FirePreset.POOL_FIRE_BACKGROUND
        .nominalAbsorbedFluxWPerM2(), "jet fire background should exceed pool fire background");
  }

  /** Peak presets must impose a higher load than the corresponding background presets. */
  @Test
  public void peakExceedsBackground() {
    assertTrue(FirePreset.POOL_FIRE_PEAK.nominalAbsorbedFluxWPerM2() > FirePreset.POOL_FIRE_BACKGROUND
        .nominalAbsorbedFluxWPerM2());
    assertTrue(FirePreset.JET_FIRE_PEAK.nominalAbsorbedFluxWPerM2() > FirePreset.JET_FIRE_BACKGROUND
        .nominalAbsorbedFluxWPerM2());
  }

  /** Absorbed flux must decrease monotonically as the exposed surface heats up (re-radiation). */
  @Test
  public void fluxDecreasesWithSurfaceTemperature() {
    double cold = FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(300.0);
    double warm = FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(600.0);
    double hot = FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(900.0);
    assertTrue(warm < cold, "flux must fall as surface heats from 300 to 600 K");
    assertTrue(hot < warm, "flux must fall as surface heats from 600 to 900 K");
    assertTrue(hot >= 0.0, "flux must stay non-negative");
  }

  /** Flux must clamp to zero when the surface reaches the flame temperature. */
  @Test
  public void fluxClampsToZeroAtFlameTemperature() {
    double atFlame = FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(FirePreset.POOL_FIRE_PEAK.getFlameTemperatureK());
    assertEquals(0.0, atFlame, 1.0e-6, "flux must be zero when surface equals flame temperature");
  }

  /** Each preset exposes a consistent kind and parameter set. */
  @Test
  public void presetMetadataIsConsistent() {
    assertEquals(FirePreset.FireKind.POOL, FirePreset.POOL_FIRE_PEAK.getKind());
    assertEquals(FirePreset.FireKind.JET, FirePreset.JET_FIRE_PEAK.getKind());
    assertTrue(
        FirePreset.POOL_FIRE_PEAK.getFlameEmissivity() > 0.0 && FirePreset.POOL_FIRE_PEAK.getFlameEmissivity() <= 1.0);
    assertTrue(FirePreset.JET_FIRE_PEAK.getConvectiveCoefficient() > 0.0);
    assertTrue(FirePreset.POOL_FIRE_PEAK.getDisplayName().length() > 0);
  }

  /** A non-positive surface temperature must be rejected. */
  @Test
  public void rejectsNonPositiveSurfaceTemperature() {
    assertThrows(IllegalArgumentException.class, () -> FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(0.0));
    assertThrows(IllegalArgumentException.class, () -> FirePreset.POOL_FIRE_PEAK.incidentHeatFlux(-10.0));
  }
}
