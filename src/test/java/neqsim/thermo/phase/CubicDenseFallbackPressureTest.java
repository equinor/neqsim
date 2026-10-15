package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Dense cubic fallback roots must close the actual pressure equation despite cancellation in Cardano's formula.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class CubicDenseFallbackPressureTest extends neqsim.NeqSimTest {
  /**
   * Distillation compositions with one small Cardano radicand retain pressure closure independently of feed scale.
   *
   * @param scale overall mole scale
   * @throws Exception if the cubic volume calculation fails
   */
  @ParameterizedTest
  @ValueSource(doubles = {1.0e-6, 1.0, 1.0e6})
  void denseFallbackClosesActualPressureEquation(double scale) throws Exception {
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane"};
    double[][] compositions = {
        {0.001244279271238458, 1.5973916163956387e-4, 0.48651750009494776, 0.26971307002456213, 0.14949705327233084,
            0.023747517009050943, 0.040963444744817175, 0.009123955517386364, 0.008653300572394334,
            0.003530957709649175, 0.0068491826219831},
        {4.239255388217675e-6, 3.252832648172023e-5, 0.01764586574548378, 0.31819119708647225, 0.4668931230502657,
            0.059626727150659, 0.09250067465811007, 0.016002470265581856, 0.014489133717799611, 0.005159798133984671,
            0.009454242609773289}};
    double[] temperatures = {244.01660971299057, 326.7261479725102};
    double[] pressures = {30.0, 30.8};
    for (int fixture = 0; fixture < compositions.length; fixture++) {
      SystemInterface fluid = new SystemSrkEos(temperatures[fixture], pressures[fixture]);
      for (int component = 0; component < names.length; component++) {
        fluid.addComponent(names[component], scale * compositions[fixture][component]);
      }
      fluid.setMixingRule("classic");
      fluid.init(0);
      fluid.init(1);
      PhaseEos phase = (PhaseEos) fluid.getPhase(0);
      phase.molarVolumeAnalytical(pressures[fixture], temperatures[fixture], PhaseType.GAS);
      assertEquals(pressures[fixture], phase.calcPressure(), pressures[fixture] * 1.0e-8,
          "the analytic candidate must solve the actual EOS pressure equation");
      assertTrue(phase.getMolarVolume() > phase.getB() / phase.getNumberOfMolesInPhase());
      assertTrue(Double.isFinite(phase.calcPressuredV()) && phase.calcPressuredV() < 0.0);
    }
  }
}
