package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SlugImpactForce} (NIP-02 slug momentum impact forces).
 */
public class SlugImpactForceTest {
  private static final double DIAMETER = 0.2; // m
  private static final double AREA = 0.25 * Math.PI * DIAMETER * DIAMETER;
  private static final double LIQUID_DENSITY = 1000.0; // kg/m3

  /**
   * The momentum impact force must scale with the square of the slug velocity.
   */
  @Test
  void momentumForceScalesWithVelocitySquared() {
    double f1 = SlugImpactForce.momentumForce(LIQUID_DENSITY, AREA, 3.0);
    double f2 = SlugImpactForce.momentumForce(LIQUID_DENSITY, AREA, 6.0);
    assertEquals(4.0, f2 / f1, 1.0e-9);
    assertEquals(LIQUID_DENSITY * AREA * 9.0, f1, 1.0e-6);
  }

  /**
   * Effective slug density must equal the liquid density at zero void fraction and the gas density at unity void
   * fraction.
   */
  @Test
  void effectiveDensityBounds() {
    assertEquals(LIQUID_DENSITY, SlugImpactForce.effectiveSlugDensity(LIQUID_DENSITY, 50.0, 0.0), 1.0e-9);
    assertEquals(50.0, SlugImpactForce.effectiveSlugDensity(LIQUID_DENSITY, 50.0, 1.0), 1.0e-9);
    double mid = SlugImpactForce.effectiveSlugDensity(LIQUID_DENSITY, 50.0, 0.5);
    assertEquals(0.5 * LIQUID_DENSITY + 0.5 * 50.0, mid, 1.0e-9);
  }

  /**
   * The design force must equal the dynamic load factor times the momentum force evaluated at the effective slug
   * density.
   */
  @Test
  void designForceAppliesDlfAndEffectiveDensity() {
    double gvf = 0.3;
    double gasDensity = 40.0;
    double velocity = 5.0;
    double dlf = 2.0;
    double rhoEff = SlugImpactForce.effectiveSlugDensity(LIQUID_DENSITY, gasDensity, gvf);
    double expected = dlf * SlugImpactForce.momentumForce(rhoEff, AREA, velocity);
    double actual = SlugImpactForce.designForce(LIQUID_DENSITY, gasDensity, AREA, velocity, gvf, dlf);
    assertEquals(expected, actual, 1.0e-6);
  }

  /**
   * A 180 deg bend (full reversal) must produce a reaction equal to twice the axial momentum force.
   */
  @Test
  void bendForceFullReversal() {
    double velocity = 4.0;
    double axial = SlugImpactForce.momentumForce(LIQUID_DENSITY, AREA, velocity);
    double bend180 = SlugImpactForce.bendForce(LIQUID_DENSITY, AREA, velocity, 180.0);
    assertEquals(2.0 * axial, bend180, 1.0e-6);

    double bend90 = SlugImpactForce.bendForce(LIQUID_DENSITY, AREA, velocity, 90.0);
    assertTrue(bend90 < bend180, "90 deg reaction must be less than full reversal");
  }

  /**
   * The flow area helper must match the circular-area formula.
   */
  @Test
  void areaFromDiameter() {
    assertEquals(AREA, SlugImpactForce.areaFromDiameter(DIAMETER), 1.0e-12);
  }

  /**
   * Force unit conversion must round-trip to the expected scaling.
   */
  @Test
  void convertForceUnits() {
    assertEquals(1.0, SlugImpactForce.convertForce(1000.0, "kN"), 1.0e-9);
    assertEquals(1000.0, SlugImpactForce.convertForce(1000.0, "N"), 1.0e-9);
    assertEquals(1000.0, SlugImpactForce.convertForce(1000.0, null), 1.0e-9);
    assertEquals(1.0, SlugImpactForce.convertForce(4.4482216152605, "lbf"), 1.0e-9);
  }

  /**
   * Out-of-range void fractions must be rejected.
   */
  @Test
  void invalidVoidFractionRejected() {
    assertThrows(IllegalArgumentException.class, () -> SlugImpactForce.effectiveSlugDensity(LIQUID_DENSITY, 40.0, 1.5));
  }
}
