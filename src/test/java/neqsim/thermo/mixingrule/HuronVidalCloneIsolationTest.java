package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseGE;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Checks ownership, tuning and retained work state of standard modified-NRTL HV clones.
 *
 * <p>
 * Synthetic forced-liquid coefficients test copying, not physical calibration.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalCloneIsolationTest extends neqsim.NeqSimTest {
  /**
   * Preserves a just-configured GE temperature convention before the first EOS initialization.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void justConfiguredGeStateKeepsItsConvention(boolean pr) {
    SystemInterface source = pr ? new SystemPrEos(330.0, 50.0) : new SystemSrkEos(330.0, 50.0);
    source.addComponent("methanol", 0.3);
    source.addComponent("water", 0.7);
    source.init(0);
    source.setMixingRule(3);
    HVMixingRulesInterface rule = (HVMixingRulesInterface) source.getPhase(0).getMixingRule();
    rule.setHVDijTParameter(0, 1, 0.8);
    rule.setHVDijTParameter(1, 0, -0.3);
    SystemInterface copied = source.clone();
    PhaseInterface a = source.getPhase(0);
    PhaseInterface b = copied.getPhase(0);
    double expected = ((PhaseGE) ((EosMixingRulesInterface) a.getMixingRule()).getGEPhase()).getExcessGibbsEnergy(a, 2,
        330.0, 50.0, a.getType());
    double actual = ((PhaseGE) ((EosMixingRulesInterface) b.getMixingRule()).getGEPhase()).getExcessGibbsEnergy(b, 2,
        330.0, 50.0, b.getType());
    assertTrue(Double.isFinite(expected));
    assertTrue(Double.isFinite(actual));
    assertEquals(expected, actual, 0.0, "Copying must not activate a different retained GE convention");
  }

  /**
   * Reproduces deterministic cross-clone temperature-derivative contamination.
   *
   * @param pr whether to use PR
   * @param rule HV mixing-rule number
   * @param initialization initial property level
   */
  @ParameterizedTest
  @CsvSource({"false,3,1", "false,3,3", "false,4,1", "false,4,3", "true,3,1", "true,3,3", "true,4,1", "true,4,3"})
  void retainedDerivativesSurviveSiblingInitialization(boolean pr, int rule, int initialization) {
    SystemInterface original = forced(pr, rule, initialization);
    PhaseInterface a = original.getPhase(0);
    EosMixingRulesInterface originalRule = (EosMixingRulesInterface) a.getMixingRule();
    originalRule.calcA(a, 330.0, 50.0, 2);
    double expectedAT = originalRule.calcAT(a, 330.0, 50.0, 2);
    double expectedATT = originalRule.calcATT(a, 330.0, 50.0, 2);
    SystemInterface copy = original.clone();
    PhaseInterface b = copy.getPhase(0);
    EosMixingRulesInterface copiedRule = (EosMixingRulesInterface) b.getMixingRule();
    assertEquals(expectedAT, copiedRule.calcAT(b, 330.0, 50.0, 2), 0.0,
        "A clone retains the evaluated derivative before any reinitialization");
    assertEquals(expectedATT, copiedRule.calcATT(b, 330.0, 50.0, 2), 0.0);
    copy.setTemperature(400.0);
    copy.init(3);
    assertEquals(expectedAT, originalRule.calcAT(a, 330.0, 50.0, 2), 0.0,
        "Initializing a sibling must not overwrite the original AT");
    assertEquals(expectedATT, originalRule.calcATT(a, 330.0, 50.0, 2), 0.0,
        "Initializing a sibling must not overwrite the original ATT");
    copy.setMolarComposition(new double[] {0.6, 0.4});
    copy.init(0);
    copy.init(3);
    assertEquals(expectedAT, originalRule.calcAT(a, 330.0, 50.0, 2), 0.0,
        "Changing a sibling composition must not overwrite the original AT");
    assertEquals(expectedATT, originalRule.calcATT(a, 330.0, 50.0, 2), 0.0);
    assertNotSame(originalRule, copiedRule);
    EosMixingRuleHandler.SRKHuronVidal2 first = (EosMixingRuleHandler.SRKHuronVidal2) originalRule;
    EosMixingRuleHandler.SRKHuronVidal2 second = (EosMixingRuleHandler.SRKHuronVidal2) copiedRule;
    assertNotSame(first.getGEPhase(), second.getGEPhase());
    assertNotSame(first.getGEPhase().getMixingRule(), second.getGEPhase().getMixingRule());
    for (int component = 0; component < 2; component++) {
      assertNotSame(first.getGEPhase().getComponent(component), second.getGEPhase().getComponent(component));
    }
    original.init(3);
    assertNative(original);
    assertNative(copy);
  }

  /**
   * Keeps every directed tuned coefficient through cloning and serialization, then isolates mutation.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void tunedParametersSurviveCopyAndSerialization(boolean pr) {
    SystemInterface original = forced(pr, 4, 3);
    SystemInterface copied = original.clone();
    SystemInterface restored = SerializationUtils.clone(copied);
    for (SystemInterface fluid : new SystemInterface[] {original, copied, restored}) {
      HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();
      assertEquals(345.0, rule.getHVDijParameter(0, 1), 0.0);
      assertEquals(1765.0, rule.getHVDijParameter(1, 0), 0.0);
      assertEquals(0.08, rule.getHVDijTParameter(0, 1), 0.0);
      assertEquals(-0.12, rule.getHVDijTParameter(1, 0), 0.0);
      assertEquals(0.17, rule.getHValphaParameter(0, 1), 0.0);
      fluid.init(3);
      assertNative(fluid);
    }
    PhaseInterface originalPhase = original.getPhase(0);
    double originalAttraction = ((EosMixingRulesInterface) originalPhase.getMixingRule()).calcA(originalPhase,
        original.getTemperature(), original.getPressure(), original.getNumberOfComponents());
    HVMixingRulesInterface changed = (HVMixingRulesInterface) copied.getPhase(0).getMixingRule();
    changed.setHVDijParameter(0, 1, 368.0);
    changed.setHVDijTParameter(1, 0, -0.09);
    changed.setHValphaParameter(0, 1, 0.21);
    changed.setClassicOrHV(0, 1, "Classic");
    for (SystemInterface fluid : new SystemInterface[] {original, restored}) {
      HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();
      assertEquals(345.0, rule.getHVDijParameter(0, 1), 0.0);
      assertEquals(-0.12, rule.getHVDijTParameter(1, 0), 0.0);
      assertEquals(0.17, rule.getHValphaParameter(0, 1), 0.0);
      fluid.init(3);
      PhaseInterface phase = fluid.getPhase(0);
      assertEquals(originalAttraction,
          ((EosMixingRulesInterface) phase.getMixingRule()).calcA(phase, fluid.getTemperature(), fluid.getPressure(),
              fluid.getNumberOfComponents()),
          0.0, "Changing a copied pair activation must not change the source or restored model");
      assertNative(fluid);
    }
  }

  /**
   * Compares concurrently flashed siblings with independently configured controls.
   *
   * @param pr whether to use PR
   * @param inhibitor neutral inhibitor component
   * @throws Exception if a worker fails or does not finish
   */
  @ParameterizedTest
  @CsvSource({"false,MEG", "true,MEG", "false,methanol", "true,methanol"})
  void parallelSiblingFlashesMatchIndependentControls(boolean pr, String inhibitor) throws Exception {
    SystemInterface source = mixture(pr, inhibitor);
    new ThermodynamicOperations(source).TPflash();
    source.init(3);
    ExecutorService workers = Executors.newFixedThreadPool(3);
    List<Future<SystemInterface>> results = new ArrayList<>();
    try {
      for (int worker = 0; worker < 3; worker++) {
        final int index = worker;
        final SystemInterface fluid = source.clone();
        results.add(workers.submit(() -> repeatedFlashes(fluid, index)));
      }
      for (int worker = 0; worker < 3; worker++) {
        SystemInterface actual = results.get(worker).get(60, TimeUnit.SECONDS);
        SystemInterface expected = repeatedFlashes(mixture(pr, inhibitor), worker);
        assertEquilibrium(actual);
        assertNative(actual);
        assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
        for (int phase = 0; phase < actual.getNumberOfPhases(); phase++) {
          PhaseInterface a = actual.getPhase(phase);
          PhaseInterface b = expected.getPhase(a.getType());
          assertEquals(b.getBeta(), a.getBeta(), 1e-10);
          for (int component = 0; component < actual.getNumberOfComponents(); component++) {
            double value = b.getComponent(component).getx();
            assertEquals(value, a.getComponent(component).getx(), Math.max(1e-13, Math.abs(value) * 1e-8));
          }
        }
      }
      assertEquals(298.15, source.getTemperature(), 0.0);
      assertEquals(60.0, source.getPressure(), 0.0);
      assertEquilibrium(source);
      assertNative(source);
    } finally {
      workers.shutdownNow();
      assertTrue(workers.awaitTermination(60, TimeUnit.SECONDS));
    }
  }

  /**
   * Preserves the existing classical-rule clone path as a cost and compatibility control.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void classicalCloneKeepsItsExistingRuleSharing(boolean pr) {
    SystemInterface source = forced(pr, 2, 3);
    assertSame(source.getPhase(0).getMixingRule(), source.clone().getPhase(0).getMixingRule());
  }

  /**
   * Preserves classical interaction and covolume selections without qualifying a different covolume model.
   *
   * @param pr whether to use PR
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void configuredClassicalAndCovolumeChoicesAreIndependent(boolean pr) {
    SystemInterface source = forced(pr, 4, 3);
    EosMixingRulesInterface rule = (EosMixingRulesInterface) source.getPhase(0).getMixingRule();
    rule.setBinaryInteractionParameter(0, 1, 0.123);
    rule.setBmixType(1);
    SystemInterface copied = source.clone();
    SystemInterface restored = SerializationUtils.clone(copied);
    EosMixingRulesInterface changed = (EosMixingRulesInterface) copied.getPhase(0).getMixingRule();
    assertEquals(0.123, changed.getBinaryInteractionParameter(0, 1), 0.0);
    assertEquals(1, changed.getBmixType());
    changed.setBinaryInteractionParameter(0, 1, 0.456);
    changed.setBmixType(0);
    for (SystemInterface fluid : new SystemInterface[] {source, restored}) {
      EosMixingRulesInterface unchanged = (EosMixingRulesInterface) fluid.getPhase(0).getMixingRule();
      assertEquals(0.123, unchanged.getBinaryInteractionParameter(0, 1), 0.0);
      assertEquals(1, unchanged.getBmixType());
    }
  }

  /**
   * Retains and isolates explicitly configured indexed pure-alpha coefficients.
   *
   * @param pr whether to use PR
   * @param rule classical or HV mixing rule
   */
  @ParameterizedTest
  @CsvSource({"false,2", "false,4", "true,2", "true,4"})
  void pureAlphaCoefficientsAreNotShared(boolean pr, int rule) {
    SystemInterface source = forced(pr, rule, 3);
    for (int phase = 0; phase < source.getMaxNumberOfPhases(); phase++) {
      ComponentEosInterface component = (ComponentEosInterface) source.getPhase(phase).getComponent(0);
      component.setAttractiveTerm(4);
      component.getAttractiveTerm().setParameters(0, 0.8);
      component.getAttractiveTerm().setParameters(1, 0.1);
      component.getAttractiveTerm().setParameters(2, -0.1);
    }
    source.init(3);
    SystemInterface copied = source.clone();
    SystemInterface restored = SerializationUtils.clone(copied);
    ComponentEosInterface original = (ComponentEosInterface) source.getPhase(0).getComponent(0);
    ComponentEosInterface changed = (ComponentEosInterface) copied.getPhase(0).getComponent(0);
    assertEquals(0.8, changed.getAttractiveTerm().getParameters(0), 0.0);
    changed.getAttractiveTerm().setParameters(0, 1.7);
    assertEquals(0.8, original.getAttractiveTerm().getParameters(0), 0.0,
        "Changing a copied pure-alpha coefficient must not retune its source");
    assertEquals(0.8,
        ((ComponentEosInterface) restored.getPhase(0).getComponent(0)).getAttractiveTerm().getParameters(0), 0.0);
    source.init(3);
    copied.init(3);
    assertNative(source);
    assertNative(copied);
  }

  /**
   * Constructs a forced liquid with explicit synthetic HV parameters.
   *
   * @param pr whether to use PR
   * @param mixingRule rule number
   * @param initialization property level
   * @return initialized fluid
   */
  private static SystemInterface forced(boolean pr, int mixingRule, int initialization) {
    SystemInterface fluid = pr ? new SystemPrEos(330.0, 50.0) : new SystemSrkEos(330.0, 50.0);
    fluid.addComponent("methanol", 0.3);
    fluid.addComponent("water", 0.7);
    fluid.setMixingRule(mixingRule);
    fluid.setForceSinglePhase(PhaseType.LIQUID);
    if (mixingRule != 2) {
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
        rule.setClassicOrHV(0, 1, "HV");
        rule.setClassicOrHV(1, 0, "HV");
        rule.setHVDijParameter(0, 1, 345.0);
        rule.setHVDijParameter(1, 0, 1765.0);
        rule.setHValphaParameter(0, 1, 0.17);
        if (mixingRule == 4) {
          rule.setHVDijTParameter(0, 1, 0.08);
          rule.setHVDijTParameter(1, 0, -0.12);
        }
      }
    }
    fluid.init(initialization);
    return fluid;
  }

  /**
   * Creates the independent four-component gas/oil/water control.
   *
   * @param pr whether to use PR
   * @param inhibitor neutral inhibitor component
   * @return unflashed fluid
   */
  private static SystemInterface mixture(boolean pr, String inhibitor) {
    SystemInterface fluid = pr ? new SystemPrEos(298.15, 60.0) : new SystemSrkEos(298.15, 60.0);
    fluid.addComponent("methane", 0.65);
    fluid.addComponent("n-decane", 0.20);
    fluid.addComponent("water", 0.15);
    fluid.addComponent(inhibitor, 0.03);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Repeats nearby changed-state flashes in one worker.
   *
   * @param fluid owned worker fluid
   * @param worker deterministic state index
   * @return flashed worker fluid
   */
  private static SystemInterface repeatedFlashes(SystemInterface fluid, int worker) {
    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    for (int repeat = 0; repeat < 3; repeat++) {
      fluid.setTemperature(298.15 + worker * 2.0 + repeat * 0.1);
      fluid.setPressure(60.0 + worker * 5.0);
      operations.TPflash();
      fluid.init(3);
      assertEquilibrium(fluid);
    }
    return fluid;
  }

  /**
   * Asserts all native identities and independently resolved numerical derivatives.
   *
   * @param fluid initialized fluid
   */
  private static void assertNative(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1e-6);
    assertTrue(checker.checkNumerically());
  }

  /**
   * Checks normalized compositions, inventory and interphase fugacity equality.
   *
   * @param fluid equilibrated worker
   */
  private static void assertEquilibrium(SystemInterface fluid) {
    double beta = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      beta += state.getBeta();
      double sum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        sum += state.getComponent(component).getx();
      }
      assertEquals(1.0, sum, 1e-12);
    }
    assertEquals(1.0, beta, 1e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double inventory = 0.0;
      double reference = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        inventory += state.getBeta() * state.getComponent(component).getx();
        assertEquals(reference,
            Math.log(state.getComponent(component).getx()) + state.getComponent(component).getLogFugacityCoefficient(),
            1e-8);
      }
      assertEquals(fluid.getComponent(component).getz(), inventory, 1e-10);
    }
  }
}
