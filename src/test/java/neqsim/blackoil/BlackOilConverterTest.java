package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;

class BlackOilConverterTest {
  @Test
  void testConvertOilProducesFinitePvtProperties() {
    SystemInterface oil = new SystemPrEos(373.15, 300.0);
    oil.addComponent("nitrogen", 0.005);
    oil.addComponent("CO2", 0.010);
    oil.addComponent("methane", 0.350);
    oil.addComponent("ethane", 0.070);
    oil.addComponent("propane", 0.065);
    oil.addComponent("i-butane", 0.025);
    oil.addComponent("n-butane", 0.040);
    oil.addComponent("i-pentane", 0.020);
    oil.addComponent("n-pentane", 0.025);
    oil.addComponent("n-hexane", 0.050);
    oil.addComponent("n-heptane", 0.080);
    oil.addComponent("n-octane", 0.080);
    oil.addComponent("n-nonane", 0.060);
    oil.addComponent("nC10", 0.120);
    oil.setMixingRule("classic");
    oil.useVolumeCorrection(true);
    oil.setMultiPhaseCheck(true);

    double[] pressures = { 25.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0 };
    BlackOilConverter.Result result = BlackOilConverter.convert(oil, 373.15, pressures, 1.01325, 288.15);

    assertNotNull(result);
    assertNotNull(result.pvt);
    assertTrue(Double.isFinite(result.rho_o_sc));
    assertTrue(result.rho_o_sc > 0.0);
    assertTrue(Double.isFinite(result.rho_g_sc));
    assertTrue(result.rho_g_sc > 0.0);
    assertTrue(result.pvt.Rs(100.0) > 0.0);

    for (double pressure : pressures) {
      assertTrue(Double.isFinite(result.pvt.Bo(pressure)));
      assertTrue(result.pvt.Bo(pressure) > 0.0);
      assertTrue(Double.isFinite(result.pvt.Rs(pressure)));
      assertTrue(result.pvt.Rs(pressure) >= 0.0);
      assertTrue(Double.isFinite(result.pvt.mu_o(pressure)));
      assertTrue(result.pvt.mu_o(pressure) > 0.0);
      assertTrue(Double.isFinite(result.pvt.Bg(pressure)));
      assertTrue(result.pvt.Bg(pressure) > 0.0);
      assertTrue(Double.isFinite(result.pvt.mu_g(pressure)));
      assertTrue(result.pvt.mu_g(pressure) > 0.0);
    }
  }

  @Test
  void testConvertWetOilProducesFiniteWaterProperties() {
    SystemInterface wetOil = new SystemSrkCPAstatoil(353.15, 200.0);
    wetOil.addComponent("methane", 0.35);
    wetOil.addComponent("n-heptane", 0.25);
    wetOil.addComponent("nC10", 0.20);
    wetOil.addComponent("water", 0.20);
    wetOil.setMixingRule(10);
    wetOil.setMultiPhaseCheck(true);

    double[] pressures = { 50.0, 100.0, 150.0, 200.0 };
    BlackOilConverter.Result result = BlackOilConverter.convert(wetOil, 353.15, pressures, 1.01325, 288.15);

    assertNotNull(result);
    assertNotNull(result.pvt);
    assertTrue(Double.isFinite(result.rho_w_sc));
    assertTrue(result.rho_w_sc > 0.0);
    for (double pressure : pressures) {
      assertTrue(Double.isFinite(result.pvt.Bw(pressure)));
      assertTrue(result.pvt.Bw(pressure) > 0.0);
      assertTrue(Double.isFinite(result.pvt.mu_w(pressure)));
      assertTrue(result.pvt.mu_w(pressure) > 0.0);
    }
  }
}
