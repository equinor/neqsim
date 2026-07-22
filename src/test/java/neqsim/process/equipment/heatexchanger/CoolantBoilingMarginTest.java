package neqsim.process.equipment.heatexchanger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Unit tests for {@link CoolantBoilingMargin}.
 *
 * <p>
 * Reproduces the Gullfaks A export-cooler (27-HX01A/B) coolant boiling screening: a tempered-water coolant must stay
 * above its boiling pressure at the 140 C gas inlet. The operator-quoted 2.5 barg floor corresponds to the water
 * saturation pressure at 140 C.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class CoolantBoilingMarginTest {
  /** Standard atmosphere used to convert barg operating points to bara [bar]. */
  private static final double ATM = 1.01325;

  /**
   * Build a pure-water coolant fluid using the CPA model.
   *
   * @return a single-component water {@link SystemInterface}
   */
  private static SystemInterface waterCoolant() {
    SystemInterface water = new SystemSrkCPAstatoil(273.15 + 100.0, 5.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    return water;
  }

  /**
   * At the 140 C hot-gas temperature the coolant sub-cooling margin should decrease monotonically as the coolant
   * pressure falls, cross ~zero near the operator's 2.5 barg floor, and stay clearly positive at 4.7 barg.
   */
  @Test
  void marginDecreasesAndCrossesZeroNearFloor() {
    SystemInterface water = waterCoolant();
    double hot = 140.0;
    CoolantBoilingMargin.Result r47 = CoolantBoilingMargin.evaluate(water, 4.7 + ATM, hot);
    CoolantBoilingMargin.Result r30 = CoolantBoilingMargin.evaluate(water, 3.0 + ATM, hot);
    CoolantBoilingMargin.Result r25 = CoolantBoilingMargin.evaluate(water, 2.5 + ATM, hot);

    // Monotonic: higher coolant pressure -> higher saturation temperature -> more margin.
    assertTrue(r47.getSubcoolingMarginC() > r30.getSubcoolingMarginC());
    assertTrue(r30.getSubcoolingMarginC() > r25.getSubcoolingMarginC());

    // 4.7 barg keeps a large margin (study: ~+17 C), not boiling.
    assertTrue(r47.getSubcoolingMarginC() > 10.0);
    assertFalse(r47.isBoiling());

    // 3.0 barg keeps a small positive margin (study: ~+4 C), not boiling.
    assertTrue(r30.getSubcoolingMarginC() > 1.0);
    assertFalse(r30.isBoiling());

    // 2.5 barg sits at incipient boiling (study: ~0 C margin).
    assertTrue(Math.abs(r25.getSubcoolingMarginC()) < 2.0);
  }

  /**
   * The saturation temperature at the 2.5 barg floor should be close to the 140 C gas temperature, i.e. the floor is
   * the coolant saturation pressure at the hot-side temperature.
   */
  @Test
  void saturationTemperatureAtFloorNear140C() {
    SystemInterface water = waterCoolant();
    CoolantBoilingMargin.Result r25 = CoolantBoilingMargin.evaluate(water, 2.5 + ATM, 140.0);
    assertEquals(140.0, r25.getSaturationTemperatureC(), 3.0);
  }

  /**
   * The minimum-margin criterion should classify a 3 C required margin as met at 3 barg but not at 2.5 barg.
   */
  @Test
  void withinMarginRespectsMinimum() {
    SystemInterface water = waterCoolant();
    double hot = 140.0;
    double minMargin = 3.0;
    CoolantBoilingMargin.Result r30 = CoolantBoilingMargin.evaluate(water, 3.0 + ATM, hot, minMargin);
    CoolantBoilingMargin.Result r25 = CoolantBoilingMargin.evaluate(water, 2.5 + ATM, hot, minMargin);
    assertTrue(r30.isWithinMargin());
    assertFalse(r25.isWithinMargin());
  }

  /**
   * The evaluation must not modify the caller's coolant fluid (it clones internally).
   */
  @Test
  void callerFluidIsNotModified() {
    SystemInterface water = waterCoolant();
    double pBefore = water.getPressure("bara");
    CoolantBoilingMargin.evaluate(water, 3.0 + ATM, 140.0);
    assertEquals(pBefore, water.getPressure("bara"), 1.0e-9);
  }

  /**
   * Invalid inputs should raise {@link IllegalArgumentException}.
   */
  @Test
  void invalidInputsThrow() {
    SystemInterface water = waterCoolant();
    assertThrows(IllegalArgumentException.class, () -> CoolantBoilingMargin.evaluate(null, 3.0, 140.0));
    assertThrows(IllegalArgumentException.class, () -> CoolantBoilingMargin.evaluate(water, 0.0, 140.0));
  }
}
