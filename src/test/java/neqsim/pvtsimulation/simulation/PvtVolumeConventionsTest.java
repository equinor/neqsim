package neqsim.pvtsimulation.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.pvtsimulation.regression.RegressionParameter;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for equinor/neqsim#5195: gas gravity, volume-corrected Z/Bg/relative volume, the Z = 1
 * standard-volume convention, the C7+ volume-shift regression parameter and separator-test stock-tank properties.
 */
public class PvtVolumeConventionsTest {
  private static final double R_TSTD_PSTD = ThermodynamicConstantsInterface.R * 288.15
      / (ThermodynamicConstantsInterface.referencePressure * 1.0e5);

  private static SystemInterface oil() {
    SystemInterface s = new SystemSrkEos(373.15, 250.0);
    s.addComponent("nitrogen", 0.4);
    s.addComponent("CO2", 0.3);
    s.addComponent("methane", 40.2);
    s.addComponent("ethane", 7.6);
    s.addComponent("propane", 8.0);
    s.addComponent("n-butane", 5.3);
    s.addComponent("n-pentane", 3.5);
    s.addComponent("n-hexane", 2.8);
    s.addTBPfraction("C7", 4.3, 0.095, 0.729);
    s.addTBPfraction("C10", 9.5, 0.140, 0.790);
    s.addTBPfraction("C20", 18.1, 0.330, 0.880);
    s.setMixingRule("classic");
    s.useVolumeCorrection(true);
    return s;
  }

  private static SystemInterface flashed(double temperatureK, double pressureBara) {
    SystemInterface s = oil();
    s.setTemperature(temperatureK);
    s.setPressure(pressureBara);
    new ThermodynamicOperations(s).TPflash();
    s.initProperties();
    return s;
  }

  private static DifferentialLiberation runDl(boolean idealStd) {
    SystemInterface fluid = oil();
    DifferentialLiberation dl = new DifferentialLiberation(fluid);
    dl.setUseIdealGasStandardVolume(idealStd);
    dl.setTemperature(100.0, "C");
    dl.setPressures(new double[] {150.0, 100.0, 50.0, 1.01325});
    dl.runCalc();
    return dl;
  }

  @Test
  void dlGasGravityIsRelativeToAirAndZIsVolumeCorrected() {
    DifferentialLiberation dl = runDl(true);
    PhaseInterface gas = flashed(373.15, 150.0).getPhase("gas");

    assertEquals(gas.getMolarMass() * 1000.0 / 28.96546, dl.getRelGasGravity()[0], 1e-6);
    assertEquals(gas.getZvolcorr(), dl.getZgas()[0], 1e-6);
    assertTrue(Math.abs(gas.getZvolcorr() - gas.getZ()) > 1e-3, "test fluid must have a non-trivial Peneloux shift");

    // Bg is the reservoir (corrected) gas volume over its Z = 1 standard volume.
    double expectedBg = gas.getZvolcorr() * 373.15 / 150.0 * ThermodynamicConstantsInterface.referencePressure / 288.15;
    assertEquals(expectedBg, dl.getBg()[0], 1e-6 * expectedBg);
  }

  @Test
  void dlStandardGasVolumeUsesIdealGasConventionByDefault() {
    DifferentialLiberation dl = runDl(true);
    assertTrue(dl.isUseIdealGasStandardVolume());
    double moles = flashed(373.15, 150.0).getPhase("gas").getNumberOfMolesInPhase();
    assertEquals(moles * R_TSTD_PSTD, dl.getGasStandardVolume()[0], 1e-6 * moles * R_TSTD_PSTD);
    assertTrue(dl.validateRsMonotonicity());
    assertTrue(dl.getRs()[0] > dl.getRs()[3]);

    // Real-gas Z at standard conditions is below 1, so the optional convention gives smaller volumes and Rs.
    DifferentialLiberation real = runDl(false);
    assertFalse(real.isUseIdealGasStandardVolume());
    assertTrue(real.getGasStandardVolume()[0] < dl.getGasStandardVolume()[0]);
    assertTrue(real.getRs()[0] < dl.getRs()[0]);
    assertEquals(dl.getRs()[0], real.getRs()[0], 0.02 * dl.getRs()[0]);
  }

  @Test
  void cmeRelativeVolumeIsVolumeCorrected() {
    double[] ps = {150.0, 100.0, 60.0};
    ConstantMassExpansion cme = new ConstantMassExpansion(oil());
    cme.setTemperaturesAndPressures(new double[] {373.15, 373.15, 373.15}, ps);
    cme.runCalc();
    double vsat = flashed(373.15, cme.getSaturationPressure()).getCorrectedVolume();
    for (int i = 0; i < ps.length; i++) {
      double expected = flashed(373.15, ps[i]).getCorrectedVolume() / vsat;
      assertEquals(expected, cme.getRelativeVolume()[i], 2e-3 * expected, "p = " + ps[i]);
    }
  }

  @Test
  void volumeShiftC7PlusChangesOilDensity() {
    double[] density = new double[3];
    double[] factors = {1.0, 1.2, 0.8};
    for (int k = 0; k < factors.length; k++) {
      SystemInterface s = oil();
      s.setTemperature(288.15);
      s.setPressure(ThermodynamicConstantsInterface.referencePressure);
      RegressionParameter.VOLUME_SHIFT_C7PLUS.applyToFluid(s, factors[k]);
      new ThermodynamicOperations(s).TPflash();
      s.initProperties();
      density[k] = s.getPhase("oil").getDensity("kg/m3");
    }
    assertTrue(Math.abs(density[1] - density[0]) > 1.0, "factor 1.2 must change density");
    assertTrue(Math.abs(density[2] - density[0]) > 1.0, "factor 0.8 must change density");
    assertTrue((density[1] - density[0]) * (density[2] - density[0]) < 0, "opposite factors, opposite changes");

    SystemInterface s = oil();
    int c20 = s.getPhase(0).getNumberOfComponents() - 1;
    s.init(0);
    double before = s.getPhase(0).getComponent(c20).getVolumeCorrection();
    RegressionParameter.VOLUME_SHIFT_C7PLUS.applyToFluid(s, 1.2);
    s.init(0);
    assertEquals(1.2 * before, s.getPhase(0).getComponent(c20).getVolumeCorrection(), 1e-9 * Math.abs(before));
  }

  @Test
  void separatorTestReportsStockTankOilDensityAndGasGravity() {
    SeparatorTest sep = new SeparatorTest(oil());
    sep.setSeparatorConditions(new double[] {313.15, 288.15},
        new double[] {30.0, ThermodynamicConstantsInterface.referencePressure});
    sep.runCalc();
    assertTrue(sep.getStockTankOilDensity() > 750.0 && sep.getStockTankOilDensity() < 950.0);
    assertTrue(sep.getStockTankOilAPIGravity() > 15.0 && sep.getStockTankOilAPIGravity() < 55.0);
    assertTrue(sep.getGasGravity()[0] > 0.6 && sep.getGasGravity()[0] < 1.2);
    assertTrue(sep.getGasGravity()[1] > sep.getGasGravity()[0], "lower-pressure gas is heavier");
  }
}
