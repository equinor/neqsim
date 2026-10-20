package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.util.readwrite.EclipseFluidReadWrite;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Public-fluid regressions for missing free water in wet gas-condensate flashes, issue #4451.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashWetCondensateAqueousRecoveryTest {
  /**
   * Provide identical overall compositions at different molar flow scales.
   *
   * @return hydrocarbon flows in mol/s and water/hydrocarbon molar ratios
   */
  private static Stream<Arguments> wetCases() {
    Stream.Builder<Arguments> cases = Stream.builder();
    for (double hydrocarbonFlow : new double[] {1.0e-6, 1.0, 50.0, 237.0, 237.04, 500.0, 2.37e6}) {
      for (double waterRatio : new double[] {0.02, 50.6 / 237.0, 97.95 / 237.04}) {
        cases.add(Arguments.of(hydrocarbonFlow, waterRatio));
      }
    }
    return cases.build();
  }

  /**
   * Exercise the recovery entry point with a converged but water-supersaturated GAS+OIL equilibrium.
   *
   * <p>
   * Disabling multiphase checking constructs the missed-aqueous endpoint deterministically without relying on the
   * unavailable internal composition or on a platform-specific rounding perturbation in the stability search.
   * </p>
   *
   * @param hydrocarbonFlow dry template flow in mol/s
   * @param waterRatio water/hydrocarbon molar ratio
   */
  @ParameterizedTest(name = "recover HC={0} mol/s, water/HC={1}")
  @MethodSource("wetCases")
  void recoversMissingAqueousWithoutReplacingGas(double hydrocarbonFlow, double waterRatio) {
    SystemInterface reference = fluid(hydrocarbonFlow, waterRatio, true);
    new ThermodynamicOperations(reference).TPflash();
    assertEquilibrium(reference);

    SystemInterface actual = fluid(hydrocarbonFlow, waterRatio, false);
    new ThermodynamicOperations(actual).TPflash();
    assertEquals(2, actual.getNumberOfPhases());
    assertTrue(actual.hasPhaseType(PhaseType.GAS));
    assertTrue(actual.hasPhaseType(PhaseType.OIL));
    assertFalse(actual.hasPhaseType(PhaseType.AQUEOUS));
    double originalMoles = actual.getTotalNumberOfMoles();
    double originalMolarGibbs = actual.getGibbsEnergy() / originalMoles;
    actual.setMultiPhaseCheck(true);

    new TPmultiflash(actual, false).rescueMetastableOilMissingAqueous();

    assertEquilibrium(actual);
    assertEquals(originalMoles, actual.getTotalNumberOfMoles(), originalMoles * 1.0e-12);
    assertTrue(actual.getGibbsEnergy() / originalMoles < originalMolarGibbs - 1.0e-6);
    assertEquivalentSplit(reference, actual);
  }

  /**
   * Qualify the complete public TPflash path and an immediate warm repeat at all flow scales.
   *
   * @param hydrocarbonFlow dry template flow in mol/s
   * @param waterRatio water/hydrocarbon molar ratio
   */
  @ParameterizedTest(name = "TPflash HC={0} mol/s, water/HC={1}")
  @MethodSource("wetCases")
  void publicFlashIsIndependentOfFlowScale(double hydrocarbonFlow, double waterRatio) {
    SystemInterface reference = fluid(237.0, waterRatio, true);
    new ThermodynamicOperations(reference).TPflash();
    SystemInterface actual = fluid(hydrocarbonFlow, waterRatio, true);
    ThermodynamicOperations operations = new ThermodynamicOperations(actual);
    operations.TPflash();
    assertEquilibrium(actual);
    assertEquivalentSplit(reference, actual);
    operations.TPflash();
    assertEquilibrium(actual);
    assertEquivalentSplit(reference, actual);
  }

  /** Verify that dry and undersaturated feeds do not acquire a spurious aqueous phase. */
  @Test
  void leavesUndersaturatedGasOilUnchanged() {
    for (double hydrocarbonFlow : new double[] {1.0, 50.0, 500.0}) {
      for (double waterRatio : new double[] {0.0, 0.006}) {
        SystemInterface actual = fluid(hydrocarbonFlow, waterRatio, true);
        new ThermodynamicOperations(actual).TPflash();
        assertEquals(2, actual.getNumberOfPhases());
        SystemInterface reference = actual.clone();
        new TPmultiflash(actual, false).rescueMetastableOilMissingAqueous();
        assertFalse(actual.hasPhaseType(PhaseType.AQUEOUS));
        assertEquals(reference.getGibbsEnergy(), actual.getGibbsEnergy(), 0.0);
        assertEquivalentSplit(reference, actual);
      }
    }
  }

  /** Verify the inexpensive negative screen avoids cloning the system or evaluating its Gibbs energy. */
  @Test
  void undersaturatedScreenAvoidsSystemCloneAndGibbsEvaluation() {
    CountingSystem actual = new CountingSystem();
    actual.addComponent("methane", 0.9);
    actual.addComponent("n-decane", 0.1);
    actual.addComponent("water", 0.001);
    actual.setMixingRule(2);
    actual.setMultiPhaseCheck(false);
    new ThermodynamicOperations(actual).TPflash();
    actual.setMultiPhaseCheck(true);
    assertTrue(actual.hasPhaseType(PhaseType.GAS));
    assertTrue(actual.hasPhaseType(PhaseType.OIL));
    actual.systemClones = 0;
    actual.gibbsEvaluations = 0;

    new TPmultiflash(actual, false).rescueMetastableOilMissingAqueous();

    assertEquals(0, actual.systemClones);
    assertEquals(0, actual.gibbsEvaluations);
    assertEquals(2, actual.getNumberOfPhases());
    assertFalse(actual.hasPhaseType(PhaseType.AQUEOUS));
  }

  /**
   * Count expensive operations at the public system boundary without introducing production instrumentation.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class CountingSystem extends SystemPrEos {
    private static final long serialVersionUID = 1L;
    private int systemClones;
    private int gibbsEvaluations;

    /** Construct a PR fluid at the standard conditions used by the wet-condensate regressions. */
    private CountingSystem() {
      super(288.15, 1.01325);
    }

    /** {@inheritDoc} */
    @Override
    public CountingSystem clone() {
      systemClones++;
      return (CountingSystem) super.clone();
    }

    /** {@inheritDoc} */
    @Override
    public double getGibbsEnergy() {
      gibbsEvaluations++;
      return super.getGibbsEnergy();
    }
  }

  /** Verify that a stale unused phase slot cannot overwrite either hydrocarbon phase. */
  @Test
  void recoveryRepairsAliasedInactivePhaseSlot() {
    SystemInterface actual = fluid(237.0, 50.6 / 237.0, false);
    new ThermodynamicOperations(actual).TPflash();
    actual.setMultiPhaseCheck(true);
    actual.setPhaseIndex(2, actual.getPhaseIndex(0));
    new TPmultiflash(actual, false).rescueMetastableOilMissingAqueous();
    assertEquilibrium(actual);
    for (int phase = 0; phase < actual.getNumberOfPhases(); phase++) {
      for (int other = phase + 1; other < actual.getNumberOfPhases(); other++) {
        assertTrue(actual.getPhaseIndex(phase) != actual.getPhaseIndex(other));
      }
    }
  }

  /** Verify that a phase-role lock prevents recovery from altering the caller's state. */
  @Test
  void respectsLockedPhaseRoles() {
    SystemInterface actual = fluid(237.0, 50.6 / 237.0, false);
    new ThermodynamicOperations(actual).TPflash();
    actual.setMultiPhaseCheck(true);
    actual.allowPhaseShift(false);
    SystemInterface reference = actual.clone();
    new TPmultiflash(actual, false).rescueMetastableOilMissingAqueous();
    assertEquals(2, actual.getNumberOfPhases());
    assertEquals(reference.getGibbsEnergy(), actual.getGibbsEnergy(), 0.0);
    assertEquivalentSplit(reference, actual);
  }

  /**
   * Construct the public PR78 E300 template with water and the reported binary interaction coefficient.
   *
   * @param hydrocarbonFlow dry template flow in mol/s
   * @param waterRatio water/hydrocarbon molar ratio
   * @param multiphase whether to search for a third fluid phase
   * @return fluid at 288.15 K and 1.01325 bara
   */
  private SystemInterface fluid(double hydrocarbonFlow, double waterRatio, boolean multiphase) {
    SystemInterface fluid = EclipseFluidReadWrite.read("src/test/java/neqsim/thermo/util/readwrite/pa_universal.e300");
    EclipseFluidReadWrite.addWaterToFluid(fluid, 0.5);
    double[] composition = fluid.getMolarComposition();
    for (int component = 0; component < composition.length - 1; component++) {
      composition[component] /= 1.0 + waterRatio;
    }
    composition[composition.length - 1] = waterRatio / (1.0 + waterRatio);
    fluid.setMolarComposition(composition);
    fluid.setTotalFlowRate(hydrocarbonFlow * (1.0 + waterRatio), "mol/sec");
    fluid.setTemperature(288.15);
    fluid.setPressure(1.01325);
    fluid.setMultiPhaseCheck(multiphase);
    return fluid;
  }

  /**
   * Check phase presence, normalization, inventory closure and interphase chemical potentials.
   *
   * @param fluid converged wet-condensate equilibrium
   */
  private void assertEquilibrium(SystemInterface fluid) {
    fluid.init(1);
    assertEquals(3, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    assertEquals(0.01394347495, fluid.getPhase("gas").getComponent("water").getx(), 1.0e-8);
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction >= 0.0 && fraction <= 1.0);
        compositionSum += fraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      double reference = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      assertTrue(Double.isFinite(reference));
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        recovered += fluid.getBeta(phase) * fraction;
        assertEquals(reference,
            Math.log(fraction) + fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 1.0e-8);
      }
      assertEquals(fluid.getComponent(component).getz(), recovered, 1.0e-10);
    }
  }

  /**
   * Compare intensive equilibrium results by phase role, independently of total amount and storage order.
   *
   * @param expected independently flashed reference
   * @param actual recovered or scaled equilibrium
   */
  private void assertEquivalentSplit(SystemInterface expected, SystemInterface actual) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    assertEquals(expected.getGibbsEnergy() / expected.getTotalNumberOfMoles(),
        actual.getGibbsEnergy() / actual.getTotalNumberOfMoles(), 1.0e-7);
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      String role = expected.getPhase(phase).getType().getDesc();
      assertTrue(actual.hasPhaseType(role));
      assertEquals(expected.getBeta(phase), actual.getBeta(actual.getPhaseNumberOfPhase(role)), 1.0e-9);
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(phase).getComponent(component).getx(),
            actual.getPhase(role).getComponent(component).getx(), 1.0e-9);
      }
    }
  }
}
