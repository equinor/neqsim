package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Api537FlareFlameModel}.
 *
 * @author ESOL
 * @version 1.0
 */
public class Api537FlareFlameModelTest {

  /**
   * Flame length should grow with heat release and lie in a physically reasonable range for a large flare.
   */
  @Test
  public void testFlameLength() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0);
    double l = model.flameLengthM();
    assertTrue(l > 20.0 && l < 200.0, "flame length out of range: " + l);
  }

  /** Reject invalid stack heights and preserve the last valid flame geometry. */
  @Test
  public void testStackHeightRequiresFiniteValueAtOrAboveGrade() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0);
    double centerHeightM = model.flameCenterHeightM();
    for (double height : new double[] {-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> model.setStackHeightM(height));
    }
    assertEquals(centerHeightM, model.flameCenterHeightM(), 1.0e-9);
    assertEquals(centerHeightM - 40.0, model.setStackHeightM(0.0).flameCenterHeightM(), 1.0e-9);
  }

  /** Reject invalid wind speeds and preserve the last valid flame tilt. */
  @Test
  public void testWindSpeedRequiresFiniteNonnegativeValue() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setWindSpeedMPerS(10.0);
    double tilt = model.flameTiltRad();
    for (double speed : new double[] {-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> model.setWindSpeedMPerS(speed));
    }
    assertEquals(tilt, model.flameTiltRad(), 1.0e-9);
  }

  /** Accept both transmissivity limits and reject invalid values without changing the flux. */
  @Test
  public void testTransmissivityRequiresFiniteFraction() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0);
    double unattenuatedFlux = model.heatFluxAtGroundDistance(60.0);
    model.setTransmissivity(0.5);
    for (double tau : new double[] {-0.01, 1.01, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> model.setTransmissivity(tau));
    }
    assertEquals(0.5 * unattenuatedFlux, model.heatFluxAtGroundDistance(60.0), 1.0e-9);
    assertEquals(0.0, model.setTransmissivity(0.0).heatFluxAtGroundDistance(60.0), 1.0e-9);
    assertEquals(unattenuatedFlux, model.setTransmissivity(1.0).heatFluxAtGroundDistance(60.0), 1.0e-9);
  }

  /**
   * With no wind the flame is vertical; with wind it tilts downwind and the flame tip moves horizontally.
   */
  @Test
  public void testWindTilt() {
    Api537FlareFlameModel noWind = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0)
        .setWindSpeedMPerS(0.0);
    assertEquals(0.0, noWind.flameTiltRad(), 1.0e-9);
    assertEquals(0.0, noWind.flameTipHorizontalM(), 1.0e-9);

    Api537FlareFlameModel windy = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0)
        .setWindSpeedMPerS(100.0);
    assertTrue(windy.flameTiltRad() > 0.0);
    assertTrue(windy.flameTipHorizontalM() > 0.0);
  }

  /**
   * Sterile-zone radii should be nested: lower flux levels reach further than higher ones.
   */
  @Test
  public void testSterileZoneOrdering() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0).setStackHeightM(40.0);
    double r158 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_1_58_KW);
    double r473 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_4_73_KW);
    double r946 = model.sterileZoneRadiusM(Api537FlareFlameModel.FLUX_9_46_KW);
    assertTrue(r158 >= r473, "1.58 kW/m2 radius must be >= 4.73 kW/m2 radius");
    assertTrue(r473 >= r946, "4.73 kW/m2 radius must be >= 9.46 kW/m2 radius");
  }

  /**
   * Noise should decrease with distance and be in a physically reasonable range for a large flare.
   */
  @Test
  public void testNoise() {
    Api537FlareFlameModel model = new Api537FlareFlameModel(50.0, 50.0e6, 0.20, 200.0);
    double pwl = model.soundPowerLevelDb();
    assertTrue(pwl > 100.0 && pwl < 200.0, "PWL out of range: " + pwl);
    double near = model.soundPressureLevelDb(50.0);
    double far = model.soundPressureLevelDb(500.0);
    assertTrue(near > far, "SPL must decrease with distance");
  }
}
