package neqsim.process.fielddevelopment.wellplanning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.wellplanning.AnnularPressureBuildup.Result;
import neqsim.process.mechanicaldesign.subsea.WellIntegrityScreening;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Closed-form hand-calculation tests of {@link AnnularPressureBuildup}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class AnnularPressureBuildupTest {

  /**
   * Rigid annulus: dP = alpha dT / kappa.
   */
  @Test
  public void rigidAnnulusGivesAlphaDtOverKappa() {
    Result r = new AnnularPressureBuildup().setFluidProperties(3.0e-4, 4.5e-5).setTemperatureChange(50.0)
        .setRigidBoundaries(true).setInitialPressure(10.0).calculate();
    assertEquals(3.0e-4 * 50.0 / 4.5e-5, r.getDeltaPressureBar(), 1.0e-6);
    assertEquals(r.getRigidLimitBar(), r.getDeltaPressureBar(), 1.0e-6);
  }

  /**
   * Compliant strings: dP = V0 alpha dT / (V0 kappa + C) with C from the closed-form Lame displacements.
   */
  @Test
  public void compliantAnnulusMatchesLameClosedForm() {
    double len = 1500.0;
    double e = 207.0e9;
    double nu = 0.3;
    AnnularPressureBuildup apb = new AnnularPressureBuildup().setAnnulusLength(len)
        .setGeometry(0.1397, 0.1215, 0.2204, 0.2445).setSteel(207.0, nu, 0.0).setFluidProperties(3.0e-4, 4.5e-5)
        .setTemperatureChange(40.0).setInitialPressure(10.0);
    Result r = apb.calculate();
    double a2 = 0.2204 / 2.0;
    double b2 = 0.2445 / 2.0;
    double a1 = 0.1215 / 2.0;
    double b1 = 0.1397 / 2.0;
    double uo = a2 / e * ((b2 * b2 + a2 * a2) / (b2 * b2 - a2 * a2) + nu);
    double ui = -b1 / e * ((b1 * b1 + a1 * a1) / (b1 * b1 - a1 * a1) - nu);
    double c = 2.0 * Math.PI * len * (a2 * uo - b1 * ui) * 1.0e5;
    double v0 = Math.PI * (a2 * a2 - b1 * b1) * len;
    assertEquals(v0, r.getAnnulusVolumeM3(), 1.0e-9 * v0);
    assertEquals(c, r.getComplianceM3PerBar(), 1.0e-9 * c);
    double expected = v0 * 3.0e-4 * 40.0 / (v0 * 4.5e-5 + c);
    assertEquals(expected, r.getDeltaPressureBar(), 1.0e-6 * expected);
    assertTrue(r.getDeltaPressureBar() < r.getRigidLimitBar());
  }

  /**
   * Equal steel temperature rise changes the annulus volume by twice the linear expansion times the annulus volume.
   */
  @Test
  public void steelThermalExpansionOfAnnulusVolume() {
    AnnularPressureBuildup apb = new AnnularPressureBuildup().setSteel(207.0, 0.3, 1.2e-5).setTemperatureChange(50.0);
    assertEquals(2.0 * 1.2e-5 * 50.0 * apb.getAnnulusVolumeM3(), apb.getFixedVolumeChangeM3(),
        1.0e-9 * apb.getAnnulusVolumeM3());
  }

  /**
   * Bleeding a volume lowers the pressure as dP = (V0 alpha dT - Vb) / (V0 kappa + C); bleeding the free expansion
   * removes the build-up.
   */
  @Test
  public void bleedVolumeReducesPressure() {
    AnnularPressureBuildup apb = new AnnularPressureBuildup().setRigidBoundaries(true)
        .setFluidProperties(3.0e-4, 4.5e-5).setTemperatureChange(50.0).setInitialPressure(10.0);
    double v0 = apb.getAnnulusVolumeM3();
    double vb = 0.2 * v0 * 3.0e-4 * 50.0;
    double dp = apb.setBledVolume(vb).calculate().getDeltaPressureBar();
    assertEquals((v0 * 3.0e-4 * 50.0 - vb) / (v0 * 4.5e-5), dp, 1.0e-6);
    assertEquals(0.0, apb.setBledVolume(v0 * 3.0e-4 * 50.0).calculate().getDeltaPressureBar(), 1.0e-6);
  }

  /**
   * Gas cushion satisfies the volume balance and strongly reduces the build-up.
   */
  @Test
  public void gasCushionSatisfiesVolumeBalance() {
    AnnularPressureBuildup apb = new AnnularPressureBuildup().setRigidBoundaries(true)
        .setFluidProperties(3.0e-4, 4.5e-5).setTemperatureChange(50.0).setInitialPressure(20.0);
    double v0 = apb.getAnnulusVolumeM3();
    double rigid = apb.calculate().getDeltaPressureBar();
    double vg = 0.02 * v0;
    double dp = apb.setGasCushionVolume(vg).calculate().getDeltaPressureBar();
    double lhs = v0 * 3.0e-4 * 50.0 - v0 * 4.5e-5 * dp;
    double rhs = vg * dp / (20.0 + dp);
    assertEquals(lhs, rhs, 1.0e-9 * v0);
    assertTrue(dp < 0.2 * rigid);
  }

  /**
   * Cooling gives a pressure reduction bounded by the initial pressure.
   */
  @Test
  public void coolingReducesPressureWithinBounds() {
    Result r = new AnnularPressureBuildup().setRigidBoundaries(true).setFluidProperties(3.0e-4, 4.5e-5)
        .setTemperatureChange(-60.0).setInitialPressure(50.0).calculate();
    assertTrue(r.getDeltaPressureBar() < 0.0);
    assertTrue(r.getFinalPressureBara() >= 0.1 - 1.0e-6);
  }

  /**
   * Fluid properties derived from a NeqSim CPA water model are physically reasonable.
   */
  @Test
  public void neqsimWaterPropertiesAreReasonable() {
    SystemInterface water = new SystemSrkCPAstatoil(330.0, 100.0);
    water.addComponent("water", 1.0);
    water.setMixingRule(10);
    double[] ak = new AnnularPressureBuildup().setTemperatureChange(40.0).setFluidFromThermoSystem(water, 100.0, 310.0);
    assertTrue(ak[0] > 2.0e-4 && ak[0] < 8.0e-4, "alpha=" + ak[0]);
    assertTrue(ak[1] > 3.0e-5 && ak[1] < 7.0e-5, "kappa=" + ak[1]);
  }

  /**
   * Predicted pressure feeds the well integrity screening reading.
   */
  @Test
  public void resultConvertsToAnnulusReading() {
    Result r = new AnnularPressureBuildup().setRigidBoundaries(true).setTemperatureChange(20.0).setInitialPressure(5.0)
        .calculate();
    WellIntegrityScreening.AnnulusReading reading = r.toAnnulusReading("A", 150.0);
    assertEquals(r.getFinalPressureBara(), reading.getMeasuredPressureBara(), 1.0e-12);
    assertEquals(150.0, reading.getMaaspBara(), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> new AnnularPressureBuildup().setGeometry(0.2, 0.1, 0.15, 0.3));
  }
}
