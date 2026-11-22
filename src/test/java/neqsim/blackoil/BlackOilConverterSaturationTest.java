package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Saturation-pressure detection in {@link BlackOilConverter} for both an oil and a retrograde gas condensate.
 *
 * <p>
 * The legacy {@code bubblePoint} field is found by scanning for the highest pressure with free gas, which degenerates
 * to the top of the grid for a condensate because free gas is present everywhere. {@code saturationPressure} scans for
 * two coexisting hydrocarbon phases instead and is therefore meaningful for both fluid types.
 *
 * @author esol
 */
public class BlackOilConverterSaturationTest {

  private static double[] pressureGrid(double from, double to, int n) {
    double[] grid = new double[n];
    double step = (to - from) / (n - 1);
    for (int i = 0; i < n; i++) {
      grid[i] = from + i * step;
    }
    return grid;
  }

  /**
   * A live oil is not flagged retrograde and its saturation pressure sits inside the grid.
   */
  @Test
  public void testLiveOilSaturationPressure() {
    SystemInterface oil = new SystemSrkEos(273.15 + 80.0, 100.0);
    oil.addComponent("methane", 0.35);
    oil.addComponent("ethane", 0.05);
    oil.addComponent("propane", 0.04);
    oil.addComponent("n-heptane", 0.30);
    oil.addComponent("nC10", 0.26);
    oil.setMixingRule("classic");

    BlackOilConverter.Result result = BlackOilConverter.convert(oil, 273.15 + 80.0, pressureGrid(20.0, 300.0, 15),
        1.01325, 288.15);

    assertFalse(result.retrogradeCondensate, "a live oil must not be flagged as a retrograde condensate");
    assertTrue(result.saturationPressure > 0.0,
        "saturation pressure should be found inside the grid, got " + result.saturationPressure);
  }

  /**
   * A rich gas condensate is flagged retrograde, and its saturation pressure is a real dew point rather than the
   * degenerate top of the pressure grid that bubblePoint returns.
   */
  @Test
  public void testGasCondensateIsFlaggedRetrograde() {
    SystemInterface condensate = new SystemSrkEos(273.15 + 90.0, 300.0);
    condensate.addComponent("methane", 0.80);
    condensate.addComponent("ethane", 0.07);
    condensate.addComponent("propane", 0.04);
    condensate.addComponent("n-pentane", 0.03);
    condensate.addComponent("n-heptane", 0.03);
    condensate.addComponent("nC10", 0.03);
    condensate.setMixingRule("classic");

    double[] grid = pressureGrid(20.0, 300.0, 15);
    BlackOilConverter.Result result = BlackOilConverter.convert(condensate, 273.15 + 90.0, grid, 1.01325, 288.15);

    assertTrue(result.retrogradeCondensate,
        "a fluid that is single-phase gas at the top of the grid and drops out liquid below "
            + "must be flagged retrograde");
    assertTrue(result.saturationPressure < grid[grid.length - 1],
        "dew point should lie below the top of the grid, got " + result.saturationPressure);
  }
}
