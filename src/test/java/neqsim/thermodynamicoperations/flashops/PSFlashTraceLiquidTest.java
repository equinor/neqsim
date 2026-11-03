package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemPrEos1978;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Entropy-root regressions for compression with a trace heavy-end liquid, issue #4828.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PSFlashTraceLiquidTest {
  /**
   * Builds the public PR78 reproducer using correlation-based petroleum fractions.
   *
   * @param temperature inlet temperature in K
   * @param pressure inlet pressure in bara
   * @param amount total amount in mol
   * @param includeHeavyEnds whether to include the trace C10 and heavier fractions
   * @return initialized fluid before its inlet TP flash
   */
  private SystemInterface fluid(double temperature, double pressure, double amount, boolean includeHeavyEnds) {
    SystemInterface fluid = new SystemPrEos1978(temperature, pressure);
    String[] components = {"nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "water"};
    double[] fractions = {0.0007, 0.0029, 0.169, 0.211, 0.352, 0.043, 0.126, 0.0238, 0.0287, 0.011, 0.0206};
    for (int component = 0; component < components.length; component++) {
      fluid.addComponent(components[component], fractions[component]);
    }
    String[] names = {"C7", "C8", "C9", "C10-C13", "C14-C16", "C17-C20", "C21-C24", "C25-C28", "C29-C34", "C35-C41",
        "C42-C52", "C53-C80"};
    double[] amounts = {8.0e-3, 2.6e-3, 3.5e-4, 2.1e-5, 1.5e-6, 1.3e-6, 9.3e-7, 6.4e-7, 6.1e-7, 4.0e-7, 2.8e-7, 1.5e-7};
    double[] molarMasses = {0.096, 0.107, 0.121, 0.153, 0.205, 0.255, 0.310, 0.365, 0.434, 0.524, 0.644, 0.867};
    double[] densities = {0.738, 0.765, 0.781, 0.80, 0.83, 0.85, 0.865, 0.88, 0.89, 0.905, 0.92, 0.95};
    for (int fraction = 0; fraction < (includeHeavyEnds ? names.length : 3); fraction++) {
      fluid.addTBPfraction(names[fraction], amounts[fraction], molarMasses[fraction], densities[fraction]);
    }
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalNumberOfMoles(amount);
    fluid.init(0);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(2);
    return fluid;
  }

  /** Verifies the reported root against a separate TP-only bisection. */
  @Test
  void traceLiquidCompressionMatchesIndependentTpRoot() {
    SystemInterface inlet = fluid(291.0, 1.7, 1.0, true);
    double target = inlet.getEntropy();
    SystemInterface state = inlet.clone();
    state.setPressure(9.5);
    PSFlash flash = new PSFlash(state, target, 0);
    flash.run();
    assertClosure(inlet, state, target, 9.5);
    assertTrue(flash.getColdBracketEvaluations() <= 12,
        "Smooth trace-liquid recovery must not require dozens of cold TP evaluations");
    SystemInterface reference = independentTpRoot(inlet, target, 9.5);
    assertEquals(reference.getTemperature(), state.getTemperature(), 1.0e-6);
    assertEquals(350.2384, state.getTemperature(), 5.0e-4);
    assertEquals(reference.getBeta(1), state.getBeta(1), 1.0e-9);
  }

  /**
   * Covers the complete inlet-temperature, inlet-pressure, outlet-pressure and amount sweep from the issue.
   *
   * @return the 216 public reproduction cases
   */
  private static Stream<Arguments> compressionCases() {
    Stream.Builder<Arguments> cases = Stream.builder();
    for (double temperature : new double[] {291.0, 293.47, 295.0}) {
      for (double inletPressure : new double[] {1.70, 1.74, 1.78}) {
        for (double outletPressure : new double[] {9.5, 9.66, 9.8, 9.933, 10.1, 10.37}) {
          for (double amount : new double[] {1.0, 50.0, 168.48, 300.0}) {
            cases.add(Arguments.of(temperature, inletPressure, outletPressure, amount));
          }
        }
      }
    }
    return cases.build();
  }

  /**
   * Requires strict entropy closure, equilibrium and component conservation at every reported operating point.
   *
   * @param temperature inlet temperature in K
   * @param inletPressure inlet pressure in bara
   * @param outletPressure discharge pressure in bara
   * @param amount total amount in mol
   */
  @ParameterizedTest
  @MethodSource("compressionCases")
  void traceLiquidSweepClosesEntropy(double temperature, double inletPressure, double outletPressure, double amount) {
    SystemInterface inlet = fluid(temperature, inletPressure, amount, true);
    double target = inlet.getEntropy();
    SystemInterface state = inlet.clone();
    state.setPressure(outletPressure);
    new ThermodynamicOperations(state).PSflash(target);
    assertClosure(inlet, state, target, outletPressure);
    assertEquals(2, state.getNumberOfPhases());
    assertTrue(state.getBeta(1) > 1.0e-7 && state.getBeta(1) < 1.0e-3);
    SystemInterface independent = state.clone();
    coldTpFlash(independent);
    assertEquals(target, independent.getEntropy(), PSFlash.entropyTolerance(state, target));
    assertEquals(state.getBeta(1), independent.getBeta(1), 1.0e-9);
  }

  /** Checks the gas-only control with the C10 and heavier fractions omitted. */
  @Test
  void gasOnlyControlClosesEntropy() {
    SystemInterface inlet = fluid(291.0, 1.7, 168.48, false);
    SystemInterface state = inlet.clone();
    state.setPressure(9.5);
    new ThermodynamicOperations(state).PSflash(inlet.getEntropy());
    assertClosure(inlet, state, inlet.getEntropy(), 9.5);
    assertEquals(1, state.getNumberOfPhases());
  }

  /** Checks cold-recovery work at the CO2 control point that otherwise needs 35 bisection evaluations. */
  @Test
  void co2ColdRecoveryHasBoundedTpWork() {
    SystemInterface inlet = new SystemPrEos(298.15, 150.0);
    inlet.addComponent("CO2", 0.98);
    inlet.addComponent("nitrogen", 0.02);
    inlet.setMixingRule(2);
    inlet.useVolumeCorrection(false);
    new ThermodynamicOperations(inlet).TPflash();
    inlet.init(2);
    SystemInterface state = inlet.clone();
    state.setPressure(49.95);
    PSFlash flash = new PSFlash(state, inlet.getEntropy(), 0);
    flash.run();
    assertClosure(inlet, state, inlet.getEntropy(), 49.95);
    assertTrue(flash.getColdBracketEvaluations() <= 12,
        "Smooth CO2 recovery must keep independent cold TP work bounded");
    SystemInterface reference = state.clone();
    coldTpFlash(reference);
    assertEquals(inlet.getEntropy(), reference.getEntropy(), PSFlash.entropyTolerance(state, inlet.getEntropy()));
  }

  /** Checks that the normal gas-compression path retains its warm-started Newton solve. */
  @Test
  void ordinaryGasCompressionDoesNotEnterColdRecovery() {
    SystemInterface inlet = new SystemSrkEos(300.0, 30.0);
    inlet.addComponent("methane", 0.85);
    inlet.addComponent("ethane", 0.10);
    inlet.addComponent("propane", 0.05);
    inlet.setMixingRule("classic");
    inlet.setMultiPhaseCheck(true);
    new ThermodynamicOperations(inlet).TPflash();
    inlet.init(2);
    SystemInterface state = inlet.clone();
    state.setPressure(60.0);
    PSFlash flash = new PSFlash(state, inlet.getEntropy(), 0);
    flash.run();
    assertClosure(inlet, state, inlet.getEntropy(), 60.0);
    assertEquals(0, flash.getColdBracketEvaluations());
  }

  /**
   * Checks intensive specifications and extensive component inventories.
   *
   * @param inlet original fluid
   * @param state flashed discharge fluid
   * @param target specified total entropy in J/K
   * @param pressure specified discharge pressure in bara
   */
  private void assertClosure(SystemInterface inlet, SystemInterface state, double target, double pressure) {
    state.init(2);
    assertEquals(target, state.getEntropy(), PSFlash.entropyTolerance(state, target));
    assertEquals(pressure, state.getPressure(), 1.0e-12);
    assertEquals(inlet.getTotalNumberOfMoles(), state.getTotalNumberOfMoles(), 1.0e-10);
    double betaSum = 0.0;
    for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
      assertTrue(Double.isFinite(state.getBeta(phase)) && state.getBeta(phase) >= 0.0);
      betaSum += state.getBeta(phase);
    }
    assertEquals(1.0, betaSum, 1.0e-10);
    for (int component = 0; component < state.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < state.getNumberOfPhases(); phase++) {
        recovered += state.getPhase(phase).getComponent(component).getNumberOfMolesInPhase();
      }
      assertEquals(inlet.getComponent(component).getNumberOfmoles(), recovered,
          1.0e-10 * inlet.getTotalNumberOfMoles());
    }
  }

  /**
   * Finds the reference temperature without calling the PS solver or reusing TP iteration history.
   *
   * @param inlet original equilibrated fluid
   * @param target specified total entropy in J/K
   * @param pressure discharge pressure in bara
   * @return independent entropy root
   */
  private SystemInterface independentTpRoot(SystemInterface inlet, double target, double pressure) {
    double lower = 330.0;
    double upper = 390.0;
    for (int iteration = 0; iteration < 60; iteration++) {
      SystemInterface state = inlet.clone();
      state.setPressure(pressure);
      state.setTemperature(0.5 * (lower + upper));
      coldTpFlash(state);
      double residual = state.getEntropy() - target;
      if (Math.abs(residual) < 0.1 * PSFlash.entropyTolerance(state, target)) {
        return state;
      }
      if (residual < 0.0) {
        lower = state.getTemperature();
      } else {
        upper = state.getTemperature();
      }
    }
    throw new AssertionError("Independent TP bisection did not close entropy");
  }

  /**
   * Runs a fresh TP operation while restoring the caller's warm-start policy.
   *
   * @param state trial fluid
   */
  private void coldTpFlash(SystemInterface state) {
    boolean previousWarm = ThermodynamicModelSettings.isUseWarmStartKValues();
    try {
      ThermodynamicModelSettings.setUseWarmStartKValues(false);
      new ThermodynamicOperations(state).TPflash();
      state.init(2);
    } finally {
      ThermodynamicModelSettings.setUseWarmStartKValues(previousWarm);
    }
  }
}
