package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import org.netlib.util.StringW;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;

/** Regression tests for critical-property initialization in the EOS-CG mixture reducing functions. */
class EOSCGMixtureReducingTest {
  @ParameterizedTest
  @ValueSource(ints = {22, 23, 24, 25, 26, 27, 28})
  void addedComponentsHaveFiniteCriticalFactorsOnFirstSetup(int component) {
    EOSCGModel model = new EOSCGModel();
    model.SetupEOSCG();

    assertEquals(0.5 / Math.cbrt(model.Dc[component]), model.Vc3[component], 1.0e-14);
    assertEquals(Math.sqrt(model.Tc[component]), model.Tc2[component], 1.0e-14);
    double[] composition = new double[29];
    composition[3] = 0.95;
    composition[component] = 0.05;
    doubleW temperature = new doubleW(0.0);
    doubleW density = new doubleW(0.0);
    model.ReducingParametersGERG(composition, temperature, density);
    assertTrue(Double.isFinite(temperature.val) && temperature.val > 0.0);
    assertTrue(Double.isFinite(density.val) && density.val > 0.0);
  }

  @Test
  void co2So2ReducingFunctionsMatchBinaryMixingEquation() {
    EOSCGModel model = new EOSCGModel();
    model.SetupEOSCG();
    double[] composition = new double[29];
    composition[3] = 0.95;
    composition[22] = 0.05;
    doubleW temperature = new doubleW(0.0);
    doubleW density = new doubleW(0.0);
    model.ReducingParametersGERG(composition, temperature, density);

    // EOS-CG-2021 Table 4: CO2 + SO2, in that order; F_ij = 0.
    double expectedTemperature = 0.95 * 0.95 * 304.1282 + 0.05 * 0.05 * 430.64
        + 2.0 * 0.95 * 0.05 * 1.020063 * 1.007975 * Math.sqrt(304.1282 * 430.64) / (1.020063 * 1.020063 * 0.95 + 0.05);
    double expectedVolume = 0.95 * 0.95 / 10.624978698 + 0.05 * 0.05 / 8.195
        + 2.0 * 0.95 * 0.05 * 0.889865 * 1.005778 * Math.pow(1.0 / Math.cbrt(10.624978698) + 1.0 / Math.cbrt(8.195), 3)
            / 8.0 / (0.889865 * 0.889865 * 0.95 + 0.05);
    assertEquals(expectedTemperature, temperature.val, 1.0e-10);
    assertEquals(1.0 / expectedVolume, density.val, 1.0e-10);
  }

  @ParameterizedTest
  @EnumSource(value = PhaseType.class, names = {"GAS", "LIQUID"})
  void nonconvergedIdealGasFallbackRemainsRejected(PhaseType phaseType) {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0);
    fluid.addComponent("CO2", 0.95);
    fluid.addComponent("SO2", 0.05);
    fluid.init(0);
    NeqSimEOSCG eos = new NeqSimEOSCG(fluid.getPhase(0));
    eos.eosCG = new EOSCG() {
      @Override
      public void pressure(double temperature, double density, double[] composition, doubleW p, doubleW z) {
        p.val = Double.NaN;
        z.val = Double.NaN;
      }

      @Override
      public void density(int flag, double temperature, double pressure, double[] composition, doubleW density,
          intW error, StringW message) {
        density.val = pressure / (8.314462618 * temperature);
        error.val = 1;
        message.val = "Calculation failed to converge; ideal gas density returned.";
      }
    };
    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> eos.getMolarDensity(phaseType));
    assertTrue(failure.getMessage().contains("ideal gas density returned"));
  }
}
