package neqsim.process.equipment.powergeneration.gasturbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests inlet/exhaust installation pressure-loss corrections of {@link GasTurbineUnit}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasTurbineInstallationLossTest {

  private static Stream fuel() {
    SystemInterface sys = new SystemSrkEos(288.15, 30.0);
    sys.addComponent("methane", 0.92);
    sys.addComponent("ethane", 0.08);
    sys.setMixingRule("classic");
    sys.init(0);
    Stream s = new Stream("fuel", sys);
    s.setFlowRate(10.0, "kg/hr");
    s.run();
    return s;
  }

  /** Default behaviour is unchanged and losses lower power and raise fuel per kWh analytically. */
  @Test
  void lossesCorrectPowerAndHeatRate() {
    GasTurbineSpec spec = GasTurbineCatalog.get("LM2500");
    GasTurbineUnit base = new GasTurbineUnit("base", fuel(), spec);
    base.setDemandedPower(10.0e6);
    base.run();
    assertEquals(1.0, base.getInstallationPowerFactor(), 1e-12);

    GasTurbineUnit lossy = new GasTurbineUnit("lossy", fuel(), spec);
    lossy.setInstallationPressureLosses(1.0, 2.0);
    lossy.setDemandedPower(10.0e6);
    lossy.run();
    double pf = 1.0 - 0.010 * 1.0 - 0.005 * 2.0;
    double hf = 1.0 + 0.004 * 1.0 + 0.005 * 2.0;
    assertEquals(base.getAvailablePowerW() * pf, lossy.getAvailablePowerW(), 1.0);
    assertTrue(lossy.getEffectiveHeatRateKJPerKWh() > base.getEffectiveHeatRateKJPerKWh());
    assertTrue(lossy.getLoadFraction() > base.getLoadFraction());
    assertEquals(hf, lossy.getInstallationHeatRateFactor(), 1e-12);
  }

  /** Losses equal to the reference basis give no correction; invalid input is rejected. */
  @Test
  void referenceBasisAndValidation() {
    GasTurbineUnit gt = new GasTurbineUnit("gt", fuel(), GasTurbineCatalog.get("LM2500"));
    gt.setReferencePressureLosses(1.0, 2.0);
    gt.setInstallationPressureLosses(1.0, 2.0);
    assertEquals(1.0, gt.getInstallationPowerFactor(), 1e-12);
    assertEquals(1.0, gt.getInstallationHeatRateFactor(), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> gt.setInstallationPressureLosses(-1.0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> gt.setInstallationPressureLosses(Double.NaN, 0.0));
  }
}
