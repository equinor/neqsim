package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks the equilibrium root used by rich-gas compressor head inversion.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PSFlashCompressorRootTest {
  /**
   * Creates the two-phase interstage fluid from the flow-rate optimizer fixture.
   *
   * @return equilibrated rich gas at 40 degrees Celsius and approximately 120 bara
   */
  private SystemInterface inlet() {
    SystemInterface fluid = new SystemSrkEos(313.15, 119.9999994001257);
    String[] names = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane"};
    double[] amounts = {0.5, 2.0, 75.0, 7.0, 4.0, 1.5, 2.0, 1.0, 1.0, 2.0, 2.0, 2.0};
    for (int component = 0; component < names.length; component++) {
      fluid.addComponent(names[component], amounts[component]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(50000.0, "kg/hr");
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    return fluid;
  }

  /**
   * Entropy closure must also select a phase-equilibrium state at adjacent pressure trials.
   *
   * @param pressure trial pressure in bara
   */
  @ParameterizedTest
  @ValueSource(doubles = {179.999465176628, 179.999465185289, 180.0})
  void richGasCompressionRetainsEquilibriumRoot(double pressure) {
    SystemInterface inlet = inlet();
    double targetEntropy = inlet.getEntropy();
    SystemInterface state = inlet.clone();
    state.setPressure(pressure);
    new ThermodynamicOperations(state).PSflash(targetEntropy);
    state.init(2);
    assertEquals(targetEntropy, state.getEntropy(), PSFlash.entropyTolerance(state, targetEntropy));
    assertEquals(331.31825, state.getTemperature(), 2.0e-4);
    assertTrue(state.getBeta(0) > 0.85 && state.getBeta(0) < 0.86);
    assertTrue(state.getBeta(1) > 0.14 && state.getBeta(1) < 0.15);

    SystemInterface equilibrium = state.clone();
    boolean previousWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      new ThermodynamicOperations(equilibrium).TPflash();
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
    equilibrium.init(2);
    assertEquals(state.getEntropy(), equilibrium.getEntropy(), 1.0e-4,
        "An independent TP flash must preserve the entropy of the returned root");
    assertEquals(state.getEnthalpy(), equilibrium.getEnthalpy(), 0.1);
    assertEquals(inlet.getFlowRate("kg/sec"), state.getFlowRate("kg/sec"), 1.0e-8);
  }
}
