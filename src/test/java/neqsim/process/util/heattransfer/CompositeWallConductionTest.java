package neqsim.process.util.heattransfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompositeWallConduction}.
 *
 * <p>
 * Validates the transient multi-layer conduction solver: a through-wall temperature gradient develops under an outer
 * heat flux, the wall approaches a physically meaningful steady state, and the Biot-number helper matches its
 * definition.
 *
 * @author ESOL
 * @version 1.0
 */
public class CompositeWallConductionTest {

  /** A constructor requesting fewer than two nodes per layer must be rejected. */
  @Test
  public void rejectsTooFewNodes() {
    assertThrows(IllegalArgumentException.class, () -> new CompositeWallConduction(1));
  }

  /** An outer flux must drive the outer surface hotter than the inner surface. */
  @Test
  public void developsThroughWallGradient() {
    CompositeWallConduction wall = new CompositeWallConduction(5);
    wall.addLayer("steel", 0.02, 45.0, 7850.0, 470.0);
    wall.addLayer("insulation", 0.05, 0.04, 120.0, 900.0);
    wall.initialize(300.0);
    for (int i = 0; i < 2000; i++) {
      wall.step(1.0, 8000.0, 50.0, 300.0);
    }
    double outer = wall.getOuterSurfaceTemperatureK();
    double inner = wall.getInnerSurfaceTemperatureK();
    assertTrue(outer > inner, "Outer surface should be hotter than inner under outer flux");
    assertTrue(inner >= 300.0, "Inner surface should warm above the initial temperature");
  }

  /** Energy must monotonically accumulate so the mean temperature rises under heating. */
  @Test
  public void meanTemperatureRisesUnderHeating() {
    CompositeWallConduction wall = new CompositeWallConduction(4);
    wall.addLayer("steel", 0.03, 45.0, 7850.0, 470.0);
    wall.initialize(290.0);
    double startMean = wall.getMeanTemperatureK();
    for (int i = 0; i < 500; i++) {
      wall.step(1.0, 5000.0, 30.0, 290.0);
    }
    assertTrue(wall.getMeanTemperatureK() > startMean);
  }

  /** Total thickness must equal the sum of the layer thicknesses. */
  @Test
  public void totalThicknessSumsLayers() {
    CompositeWallConduction wall = new CompositeWallConduction(3);
    wall.addLayer("a", 0.02, 45.0, 7850.0, 470.0);
    wall.addLayer("b", 0.05, 0.04, 120.0, 900.0);
    wall.initialize(300.0);
    assertEquals(0.07, wall.getTotalThicknessM(), 1.0e-12);
  }

  /** Stepping before initialization must fail loudly. */
  @Test
  public void rejectsStepBeforeInitialize() {
    CompositeWallConduction wall = new CompositeWallConduction(3);
    wall.addLayer("a", 0.02, 45.0, 7850.0, 470.0);
    assertThrows(IllegalStateException.class, () -> wall.step(1.0, 1000.0, 30.0, 300.0));
  }

  /** Biot number must equal h*L/k. */
  @Test
  public void biotMatchesDefinition() {
    double bi = CompositeWallConduction.biotNumber(50.0, 0.02, 45.0);
    assertEquals(50.0 * 0.02 / 45.0, bi, 1.0e-12);
  }
}
