package neqsim.process.safety.fire;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
