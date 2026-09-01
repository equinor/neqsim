package neqsim.thermo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRCPAEoS;

/**
 * Locks in the shared inner-flash warm-start policy used by every iterative flash (PH, PS, PV, TV, PU, VU, ...).
 *
 * <p>
 * The policy lived duplicated in {@code PHflash} and {@code PSFlash} while the remaining iterative flashes enabled
 * K-value reuse unconditionally. It is now a single predicate on
 * {@link ThermodynamicModelSettings#isInnerFlashWarmStartSafe(SystemInterface)}.
 * </p>
 */
public class ThermodynamicModelSettingsWarmStartPolicyTest extends neqsim.NeqSimTest {

  private static SystemInterface withMethaneWater(SystemInterface fluid, String mixingRule) {
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("water", 0.2);
    fluid.setMixingRule(mixingRule);
    return fluid;
  }

  private static SystemInterface withMethaneWater(SystemInterface fluid, int mixingRule) {
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("water", 0.2);
    fluid.setMixingRule(mixingRule);
    return fluid;
  }

  @Test
  public void cubicModelsKeepWarmStarts() {
    assertTrue(ThermodynamicModelSettings
        .isInnerFlashWarmStartSafe(withMethaneWater(new SystemSrkEos(298.15, 10.0), "classic")));
    assertTrue(ThermodynamicModelSettings
        .isInnerFlashWarmStartSafe(withMethaneWater(new SystemPrEos(298.15, 10.0), "classic")));
  }

  @Test
  public void cpaModelsDoNotWarmStart() {
    assertFalse(ThermodynamicModelSettings
        .isInnerFlashWarmStartSafe(withMethaneWater(new SystemSrkCPAstatoil(298.15, 10.0), 10)));
    assertFalse(
        ThermodynamicModelSettings.isInnerFlashWarmStartSafe(withMethaneWater(new SystemUMRCPAEoS(298.15, 10.0), 10)));
  }

  @Test
  public void nullSystemIsTreatedAsWarmStartSafe() {
    assertTrue(ThermodynamicModelSettings.isInnerFlashWarmStartSafe(null));
  }

  @Test
  public void predicateWorksOnAnUninitializedFluid() {
    // No components added, so the phases hold no data yet. The predicate must fall back to the
    // model name instead of throwing.
    assertTrue(ThermodynamicModelSettings.isInnerFlashWarmStartSafe(new SystemSrkEos(298.15, 10.0)));
    assertFalse(ThermodynamicModelSettings.isInnerFlashWarmStartSafe(new SystemSrkCPAstatoil(298.15, 10.0)));
  }
}
