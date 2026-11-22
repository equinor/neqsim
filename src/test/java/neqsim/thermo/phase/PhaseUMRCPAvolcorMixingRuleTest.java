package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.component.ComponentUMRCPAvolcor;

/**
 * Checks factored translation sums against independent pair sums and preserves subclass mixing hooks.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
class PhaseUMRCPAvolcorMixingRuleTest {
  /** Checks the arithmetic rule across mole scales and deliberately non-normalized component inventories. */
  @Test
  void arithmeticTranslationMatchesPairSumsAcrossInventories() {
    for (double scale : new double[] {1.0e-6, 1.0, 1.0e6}) {
      for (double inventoryFactor : new double[] {0.5, 1.0, 2.0}) {
        PhaseUMRCPAvolcor phase = createPhase(new PhaseUMRCPAvolcor(), scale, inventoryFactor);
        assertPairSums(phase);
      }
    }
  }

  /** Checks that subclasses overriding the pair rules retain their extensive and derivative calculations. */
  @Test
  void subclassPairRulesRetainVirtualDispatch() {
    for (double scale : new double[] {0.01, 1.0, 100.0}) {
      assertPairSums(createPhase(new PairRulePhase(), scale, 0.7));
    }
  }

  /**
   * Creates an initialized phase with independently controlled phase and component inventories.
   *
   * @param phase phase whose arithmetic or custom pair rule is tested
   * @param scale extensive mole scale
   * @param inventoryFactor factor applied only to individual component amounts
   * @return initialized phase, with no translation derivative cache populated
   */
  private static PhaseUMRCPAvolcor createPhase(PhaseUMRCPAvolcor phase, double scale, double inventoryFactor) {
    String[] names = {"methane", "ethane", "propane", "n-butane", "n-pentane", "water"};
    double[] fractions = {0.3, 0.2, 0.15, 0.1, 0.05, 0.2};
    phase.setTemperature(350.0);
    phase.setPressure(30.0);
    for (int component = 0; component < names.length; component++) {
      double moles = scale * fractions[component];
      phase.addComponent(names[component], moles, moles, component);
      // Nonzero temperature derivatives exercise the linear path without assuming the built-in cT is zero.
      phase.getcomponentArray()[component] = new TemperatureTranslationComponent(names[component], moles, component);
    }
    phase.init(scale, names.length, 0, PhaseType.GAS, 0.75);
    for (int component = 0; component < names.length; component++) {
      phase.getComponent(component).setNumberOfMolesInPhase(scale * fractions[component] * inventoryFactor);
    }
    return phase;
  }

  /**
   * Evaluates the unfactored pair definition independently of the optimized translation implementation.
   *
   * @param phase phase to check against its pair mixing hooks
   */
  private static void assertPairSums(PhaseUMRCPAvolcor phase) {
    int components = phase.getNumberOfComponents();
    double totalMoles = phase.getNumberOfMolesInPhase();
    double expectedC = 0.0;
    double expectedCT = 0.0;
    double[] expectedCi = new double[components];
    double[] expectedCiT = new double[components];
    for (int i = 0; i < components; i++) {
      ComponentEosInterface first = (ComponentEosInterface) phase.getComponent(i);
      for (int j = 0; j < components; j++) {
        ComponentEosInterface second = (ComponentEosInterface) phase.getComponent(j);
        double pairC = phase.getcij(first, second);
        double pairCT = phase.getcijT(first, second);
        expectedC += first.getNumberOfMolesInPhase() * second.getNumberOfMolesInPhase() * pairC;
        expectedCT += first.getNumberOfMolesInPhase() * second.getNumberOfMolesInPhase() * pairCT;
        expectedCi[i] += 2.0 * second.getNumberOfMolesInPhase() * pairC;
        expectedCiT[i] += 2.0 * second.getNumberOfMolesInPhase() * pairCT;
      }
    }
    expectedC /= totalMoles;
    expectedCT /= totalMoles;
    phase.calcB(phase, phase.getTemperature(), phase.getPressure(), components);
    assertTranslationEquals(expectedC, phase.getC());
    assertTranslationEquals(expectedCT, phase.getCT());
    for (int component = 0; component < components; component++) {
      expectedCi[component] = (expectedCi[component] - expectedC) / totalMoles;
      expectedCiT[component] = (expectedCiT[component] - expectedCT) / totalMoles;
      assertTranslationEquals(expectedCi[component],
          phase.calcCi(component, phase, phase.getTemperature(), phase.getPressure(), components));
      assertTranslationEquals(expectedCiT[component],
          phase.calcCiT(component, phase, phase.getTemperature(), phase.getPressure(), components));
    }
    for (int i = 0; i < components; i++) {
      for (int j = 0; j < components; j++) {
        double pairC = phase.getcij((ComponentEosInterface) phase.getComponent(i),
            (ComponentEosInterface) phase.getComponent(j));
        double expectedCij = (2.0 * pairC - expectedCi[i] - expectedCi[j]) / totalMoles;
        assertTranslationEquals(expectedCij,
            phase.calcCij(i, j, phase, phase.getTemperature(), phase.getPressure(), components));
      }
    }
  }

  /**
   * Compares translation quantities with relative tolerance and an absolute round-off allowance.
   *
   * @param expected independent pair result
   * @param actual optimized or subclass-dispatched result
   */
  private static void assertTranslationEquals(double expected, double actual) {
    assertEquals(expected, actual, Math.max(1.0e-12, Math.abs(expected) * 1.0e-12));
  }

  /**
   * Supplies nonzero component temperature responses to exercise the public getter contract.
   *
   * @author Even Solbraa
   * @version $Id: $Id
   */
  private static class TemperatureTranslationComponent extends ComponentUMRCPAvolcor {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /**
     * Creates a component with a nonzero translation-temperature response.
     *
     * @param name component name
     * @param moles component amount
     * @param index component number
     */
    TemperatureTranslationComponent(String name, double moles, int index) {
      super(name, moles, moles, index);
    }

    /** {@inheritDoc} */
    @Override
    public double getcT() {
      return (getComponentNumber() + 1) * 1.0e-7;
    }
  }

  /**
   * Defines a non-arithmetic pair rule whose overrides must survive the arithmetic optimization.
   *
   * @author Even Solbraa
   * @version $Id: $Id
   */
  private static class PairRulePhase extends PhaseUMRCPAvolcor {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;

    /** {@inheritDoc} */
    @Override
    public double getcij(ComponentEosInterface first, ComponentEosInterface second) {
      return super.getcij(first, second)
          + (first.getComponentNumber() + 1) * (second.getComponentNumber() + 1) * 1.0e-4;
    }

    /** {@inheritDoc} */
    @Override
    public double getcijT(ComponentEosInterface first, ComponentEosInterface second) {
      return super.getcijT(first, second)
          + (first.getComponentNumber() + 1) * (second.getComponentNumber() + 1) * 1.0e-8;
    }
  }
}
