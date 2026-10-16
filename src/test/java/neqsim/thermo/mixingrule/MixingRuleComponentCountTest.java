package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseModifiedFurstElectrolyteEos;
import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermo.system.SystemElectrolyteCPAAdvanced;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemFurstElectrolyteEosMod2004;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for mixing rules configured before reaction species are added (#4343).
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MixingRuleComponentCountTest extends neqsim.NeqSimTest {
  /**
   * Reject stale matrices before array access and recover to the correctly ordered setup.
   *
   * @param model electrolyte model selector
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4})
  void chemistryAfterMixingRuleRequiresExplicitRefresh(int model) {
    SystemInterface fluid = brine(model);
    int mixingRule = model < 2 ? 4 : 10;
    fluid.setMixingRule(mixingRule);
    fluid.chemicalReactionInit();
    assertEquals(8, fluid.getNumberOfComponents());
    assertTrue(fluid.isChemicalSystem());

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fluid.init(0));
    assertRecoveryMessage(failure, 4, 8);
    assertThrows(IllegalStateException.class, () -> fluid.init(1));

    fluid.createDatabase(true);
    fluid.setMixingRule(mixingRule);
    fluid.init(0);
    fluid.init(1);

    SystemInterface reference = brine(model);
    reference.chemicalReactionInit();
    reference.createDatabase(true);
    reference.setMixingRule(mixingRule);
    reference.init(0);
    reference.init(1);
    assertSameState(reference, fluid);

    fluid.chemicalReactionInit();
    fluid.init(0);
    fluid.init(1);
    assertSameState(reference, fluid);
  }

  /** A retained electrolyte rule must reject the stale arrays without overwriting fitted values. */
  @Test
  void directElectrolyteRecalculationPreservesParametersWhenRejected() {
    SystemInterface fluid = brine(3);
    fluid.setMixingRule(10);
    PhaseModifiedFurstElectrolyteEos phase = (PhaseModifiedFurstElectrolyteEos) fluid.getPhase(0);
    ElectrolyteMixingRulesInterface electrolyteRule = phase.getElectrolyteMixingRule();
    EosMixingRulesInterface eosRule = phase.getEosMixingRule();
    eosRule.setBinaryInteractionParameter(0, 1, 0.1234);
    electrolyteRule.setWijT1Parameter(2, 1, 0.004321);
    fluid.chemicalReactionInit();

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> electrolyteRule.calcWij(phase));
    assertRecoveryMessage(failure, 4, 8);
    assertEquals(0.1234, eosRule.getBinaryInteractionParameter(0, 1), 0.0);
    assertEquals(0.004321, electrolyteRule.gettWijT1Parameter(2, 1), 0.0);
    assertSame(eosRule, phase.getEosMixingRule());
  }

  /**
   * Cubic and Huron-Vidal rules also need rebuilding after the component count changes.
   *
   * @param mixingRule selected mixing rule number
   */
  @ParameterizedTest
  @ValueSource(ints = {2, 4, 10})
  void nonElectrolyteRulesRejectComponentGrowth(int mixingRule) {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(mixingRule);
    fluid.addComponent("CO2", 0.01);

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fluid.init(0));
    assertRecoveryMessage(failure, 2, 3);
    fluid.setMixingRule(mixingRule);
    fluid.init(0);
    fluid.init(1);
    assertTrue(Double.isFinite(fluid.getZ()));
  }

  /** Removing a species must not silently use the old component-indexed parameters. */
  @Test
  void componentRemovalRequiresRefresh() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("CO2", 0.1);
    fluid.setMixingRule(2);
    fluid.removeComponent("ethane");

    IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fluid.init(0));
    assertRecoveryMessage(failure, 3, 2);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(1);
    assertTrue(Double.isFinite(fluid.getZ()));
  }

  /** Mole-amount changes and repeated initialization keep the configured rule and custom parameters. */
  @Test
  void unchangedComponentCountKeepsCustomParameters() {
    SystemInterface fluid = brine(3);
    fluid.chemicalReactionInit();
    fluid.setMixingRule(10);
    EosMixingRulesInterface rule = ((PhaseEosInterface) fluid.getPhase(0)).getEosMixingRule();
    rule.setBinaryInteractionParameter(0, 1, 0.1234);
    fluid.addComponent("CO2", 0.1);
    for (int repeat = 0; repeat < 3; repeat++) {
      fluid.init(0);
      fluid.init(1);
      assertSame(rule, ((PhaseEosInterface) fluid.getPhase(0)).getEosMixingRule());
      assertEquals(0.1234, rule.getBinaryInteractionParameter(0, 1), 0.0);
    }
  }

  /** The zero-interaction rule has no fitted pair matrices to invalidate. */
  @Test
  void zeroInteractionRuleAllowsComponentGrowth() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.setMixingRule(1);
    fluid.init(0);
    fluid.addComponent("ethane", 0.1);
    fluid.init(0);
    fluid.init(1);
    assertTrue(Double.isFinite(fluid.getZ()));
  }

  /**
   * Check that the diagnostic contains counts, cause, and an executable recovery sequence.
   *
   * @param failure stale mixing-rule exception
   * @param configured component count used to build the rule
   * @param current current component count
   */
  private static void assertRecoveryMessage(IllegalStateException failure, int configured, int current) {
    assertTrue(failure.getMessage().contains("Mixing-rule state is stale"));
    assertTrue(failure.getMessage().contains("configured for " + configured + " components"));
    assertTrue(failure.getMessage().contains("phase contains " + current));
    assertTrue(failure.getMessage().contains("chemicalReactionInit()"));
    assertTrue(failure.getMessage().contains("createDatabase(true)"));
    assertTrue(failure.getMessage().contains("setMixingRule(...)"));
  }

  /**
   * Compare initialized physical properties and species inventories with an independently built reference.
   *
   * @param expected correctly ordered configuration
   * @param actual configuration recovered after an explicit refresh
   */
  private static void assertSameState(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int phaseIndex = 0; phaseIndex < expected.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface expectedPhase = expected.getPhase(phaseIndex);
      PhaseInterface actualPhase = actual.getPhase(phaseIndex);
      assertTrue(Double.isFinite(actualPhase.getZ()));
      assertEquals(expectedPhase.getZ(), actualPhase.getZ(), 1e-10);
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expectedPhase.getComponent(component).getComponentName(),
            actualPhase.getComponent(component).getComponentName());
        assertEquals(expectedPhase.getComponent(component).getNumberOfMolesInPhase(),
            actualPhase.getComponent(component).getNumberOfMolesInPhase(), 1e-12);
        double expectedLogPhi = expectedPhase.getComponent(component).getLogFugacityCoefficient();
        double actualLogPhi = actualPhase.getComponent(component).getLogFugacityCoefficient();
        assertTrue(Double.isFinite(actualLogPhi));
        assertEquals(expectedLogPhi, actualLogPhi, 1e-9);
      }
    }
  }

  /**
   * Create the four-component feed reported in issue #4343 at 283.15 K and 40 bara.
   *
   * @param model electrolyte model selector
   * @return fluid before reaction or mixing-rule initialization
   */
  private static SystemInterface brine(int model) {
    SystemInterface fluid;
    if (model == 0) {
      fluid = new SystemFurstElectrolyteEos(283.15, 40.0);
    } else if (model == 1) {
      fluid = new SystemFurstElectrolyteEosMod2004(283.15, 40.0);
    } else if (model == 2) {
      fluid = new SystemElectrolyteCPA(283.15, 40.0);
    } else if (model == 3) {
      fluid = new SystemElectrolyteCPAstatoil(283.15, 40.0);
    } else {
      fluid = new SystemElectrolyteCPAAdvanced(283.15, 40.0);
    }
    fluid.addComponent("CO2", 10.0);
    fluid.addComponent("water", 1.0 / 0.01801528);
    fluid.addComponent("Na+", 1.9);
    fluid.addComponent("Cl-", 1.9);
    return fluid;
  }
}
