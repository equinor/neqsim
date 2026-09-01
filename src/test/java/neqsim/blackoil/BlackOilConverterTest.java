package neqsim.blackoil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

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

  /**
   * The stock-tank oil density reported by the converter must be the volume-shift corrected density, i.e. the same
   * value a direct standard-condition flash reports. Reading the uncorrected EOS density instead gives an error of the
   * order of the Peneloux shift (a few percent for a tuned reservoir fluid).
   */
  @Test
  void testStockTankDensitiesUseVolumeShiftCorrectedDensity() {
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

    SystemInterface reference = oil.clone();
    reference.setTemperature(288.15);
    reference.setPressure(1.01325);
    new ThermodynamicOperations(reference).TPflash();
    reference.initProperties();
    double expectedOilDensity = reference.getPhase("oil").getDensity("kg/m3");
    double expectedGasDensity = reference.getPhase("gas").getDensity("kg/m3");

    double[] pressures = { 25.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0 };
    BlackOilConverter.Result result = BlackOilConverter.convert(oil, 373.15, pressures, 1.01325, 288.15);

    assertEquals(expectedOilDensity, result.rho_o_sc, 0.01 * expectedOilDensity);
    assertEquals(expectedGasDensity, result.rho_g_sc, 0.02 * expectedGasDensity);
  }

  /**
   * Below the bubble point the solution gas-oil ratio of a live oil must fall smoothly with pressure. A standalone
   * phase system rebuilt from stale flash state can converge to the trivial single-phase solution, which reports Rs = 0
   * and Bo = 1 for an oil that plainly carries dissolved gas, so this asserts that Rs stays positive and monotone
   * rather than merely non-negative.
   */
  @Test
  void testSolutionGasOilRatioDoesNotCollapseBelowBubblePoint() {
    SystemInterface oil = new SystemPrEos(290.4, 70.0);
    oil.addComponent("nitrogen", 0.004);
    oil.addComponent("CO2", 0.008);
    oil.addComponent("methane", 0.180);
    oil.addComponent("ethane", 0.035);
    oil.addComponent("propane", 0.030);
    oil.addComponent("i-butane", 0.012);
    oil.addComponent("n-butane", 0.020);
    oil.addComponent("i-pentane", 0.012);
    oil.addComponent("n-pentane", 0.015);
    oil.addComponent("n-hexane", 0.030);
    oil.addComponent("n-heptane", 0.060);
    oil.addComponent("n-octane", 0.070);
    oil.addComponent("n-nonane", 0.064);
    oil.addComponent("nC10", 0.460);
    oil.setMixingRule("classic");
    oil.useVolumeCorrection(true);
    oil.setMultiPhaseCheck(true);

    double[] pressures = { 20.0, 30.0, 40.0, 50.0, 55.0, 60.0, 65.0, 70.0 };
    BlackOilConverter.Result result = BlackOilConverter.convert(oil, 290.4, pressures, 1.01325, 288.15);

    double previous = 0.0;
    for (double pressure : pressures) {
      double rs = result.pvt.Rs(pressure);
      assertTrue(rs > 0.0, "Rs collapsed to zero at " + pressure + " bara; the oil is live");
      assertTrue(rs >= previous - 1.0e-9,
          "Rs decreased with rising pressure at " + pressure + " bara: " + previous + " -> " + rs);
      previous = rs;
    }

    // A dissolved-gas step of this size between neighbouring points is a solver artefact, not
    // physics; the historical failure jumped from 0 to about 40 Sm3/Sm3 across one bar.
    double[] finer = { 50.0, 51.0, 52.0, 53.0, 54.0, 55.0, 56.0, 57.0, 58.0 };
    BlackOilConverter.Result fine = BlackOilConverter.convert(oil, 290.4, finer, 1.01325, 288.15);
    for (int i = 1; i < finer.length; i++) {
      double jump = Math.abs(fine.pvt.Rs(finer[i]) - fine.pvt.Rs(finer[i - 1]));
      assertTrue(jump < 10.0, "Rs jumped by " + jump + " Sm3/Sm3 over one bar near " + finer[i] + " bara");
    }
  }
}
