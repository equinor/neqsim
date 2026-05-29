package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Regression tests for {@link Flash#stabilityCheck()} skipping the supplementary
 * amplified-K and pure-component trials when the caller has explicitly disabled
 * the multi-phase check via {@link SystemInterface#setMultiPhaseCheck(boolean)}.
 *
 * <p>
 * The trial functions may seed the active system with K-values that are valid
 * for stability analysis but produce a non-physical state (PR cubic with no
 * real root) once the main flash continues. This is observed for example with
 * a near-cricondenbar gas inside a compressor: the supplementary trial declares
 * the gas "unstable", calls {@code init(1)} on the trial-seeded two-phase
 * split, and {@code PhasePrEos.molarVolumeAnalytical} returns NaN. When the
 * caller is operating in known-single-phase mode (typical for compressor
 * outlet PHflash), the trials should be skipped entirely and the K-vector left
 * untouched.
 */
public class FlashStabilityTrialRevertTest {

  /**
   * Build a typical natural-gas mixture near its cricondenbar where the
   * supplementary stability trials are most likely to fire.
   */
  private static SystemInterface buildGas(double temperatureK, double pressureBara) {
    SystemInterface gas = new SystemPrEos(temperatureK, pressureBara);
    gas.addComponent("nitrogen", 0.012);
    gas.addComponent("CO2", 0.013);
    gas.addComponent("methane", 0.880);
    gas.addComponent("ethane", 0.053);
    gas.addComponent("propane", 0.033);
    gas.addComponent("n-butane", 0.005);
    gas.addComponent("n-hexane", 0.002);
    gas.addComponent("n-heptane", 0.002);
    gas.setMixingRule("classic");
    return gas;
  }

  @Test
  public void testSingleGasPhaseStaysFiniteWithMultiPhaseCheckDisabled() {
    SystemInterface gas = buildGas(273.15 + 25.0, 60.0);
    gas.setMultiPhaseCheck(false);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    assertEquals(1, gas.getNumberOfPhases(),
        "single-phase gas should remain single-phase when multi-phase check is disabled");
    double density = gas.getPhase(0).getDensity();
    assertTrue(Double.isFinite(density) && density > 0.0,
        "density must be finite and positive (was " + density + ")");
    double viscosity = gas.getPhase(0).getViscosity();
    assertTrue(Double.isFinite(viscosity) && viscosity > 0.0,
        "viscosity must be finite and positive (was " + viscosity + ")");
  }

  @Test
  public void testKvectorPreservedWhenSupplementaryTrialsSkipped() {
    SystemInterface gas = buildGas(273.15 + 25.0, 60.0);
    gas.setMultiPhaseCheck(false);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    double[] k = gas.getKvector();
    if (k != null) {
      for (int i = 0; i < k.length; i++) {
        assertTrue(Double.isFinite(k[i]),
            "K-value " + i + " must be finite (was " + k[i] + ")");
        assertFalse(k[i] > 1.0e4,
            "K-value " + i + " (" + k[i] + ") suggests trial K-pollution leaked through");
      }
    }
  }

  @Test
  public void testTPflashSurvivesNearCricondenbarSingleGas() {
    // Conditions historically tripping the amplified-K trial into a NaN
    // downstream of compressor PHflash on similar gas compositions.
    SystemInterface gas = buildGas(273.15 + 45.0, 95.0);
    gas.setMultiPhaseCheck(false);
    ThermodynamicOperations ops = new ThermodynamicOperations(gas);
    ops.TPflash();
    gas.initProperties();
    assertEquals(1, gas.getNumberOfPhases());
    assertTrue(Double.isFinite(gas.getPhase(0).getDensity()));
    assertTrue(Double.isFinite(gas.getEnthalpy()));
  }
}
