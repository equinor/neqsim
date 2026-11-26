package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import neqsim.thermo.phase.PhaseGENRTLmodifiedHV;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Checks complete-phase row reuse against the unchanged standalone activity evaluator.
 *
 * <p>
 * Numeric tuples are implementation controls, not new qualified parameter profiles. Explicit phase construction binds
 * the complete tuple with setParams; legacy constructor-only binding remains a separate compatibility issue.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalPhasePureRequestTest {
  /** Counts actual pure-method entries without changing returned energies. */
  private final long[] calls = new long[3];

  /** Proves request reduction and bit-identical activity derivatives through changed and returned temperatures. */
  @Test
  void completePhaseRetainsStandaloneValuesWithFewerRequests() {
    String[] names = {"water", "CO2", "methane", "methanol", "MEG", "ethane", "propane", "i-butane", "n-butane",
        "i-pentane", "n-pentane", "n-hexane", "n-heptane", "n-octane", "nitrogen"};
    for (boolean pr : new boolean[] {false, true}) {
      for (int n : new int[] {1, 5, 15}) {
        for (int type : new int[] {1, 3}) {
          for (String selection : new String[] {"pair", "classic", "hv"}) {
            if (n == 1 && selection.equals("pair")) {
              continue;
            }
            SystemInterface fluid = pr ? new SystemPrEos(350.0, 10.0) : new SystemSrkEos(350.0, 10.0);
            ComponentGENRTLmodifiedHV[] activities = new ComponentGENRTLmodifiedHV[n];
            for (int i = 0; i < n; i++) {
              fluid.addComponent(names[i], 1.0 / n);
              activities[i] = new ComponentGENRTLmodifiedHV(names[i], 1.0 / n, 1.0 / n, i);
            }
            fluid.setMixingRule(2);
            fluid.setForceSinglePhase(PhaseType.GAS);
            fluid.init(3);
            PhaseInterface phase = fluid.getPhase(0);
            phase.setInitType(type);
            wrap(phase, n);
            double[][] zero = new double[n][n];
            String[][] rules = new String[n][n];
            for (String[] row : rules) {
              Arrays.fill(row, selection.equals("hv") ? "HV" : "Classic");
            }
            if (selection.equals("pair")) {
              rules[0][1] = "HV";
              rules[1][0] = "HV";
            }
            for (double temperature : new double[] {350.0, 350.25, 350.0}) {
              fluid.setTemperature(temperature);
              Arrays.fill(calls, 0);
              double[][] expected = new double[n][];
              double sum = 0.0;
              for (int i = 0; i < n; i++) {
                double gamma = activities[i].getGamma(phase, n, temperature, 10.0, PhaseType.GAS, zero, zero, zero,
                    zero, rules);
                sum += phase.getComponent(i).getx() * Math.log(gamma);
                expected[i] = values(activities[i], n);
              }
              long[] before = calls.clone();
              Arrays.fill(calls, 0);
              double actual = ComponentGENRTLmodifiedHV.excessGibbsSum(phase, activities, n, temperature, 10.0,
                  PhaseType.GAS, zero, zero, zero, zero, rules);
              assertEquals(sum, actual, 0.0);
              for (int i = 0; i < n; i++) {
                assertArrayEquals(expected[i], values(activities[i], n), 0.0);
              }
              long saved = selection.equals("hv") ? 0 : (long) n * (n * n - (selection.equals("pair") ? 2 : 0)) - n;
              assertEquals(before[0] - saved, calls[0], "At most one inner row attraction per phase evaluation");
              assertEquals(before[1], calls[1], "First temperature derivative requests are unchanged");
              assertEquals(before[2], calls[2], "Second temperature derivative requests are unchanged");
            }
          }
        }
      }
    }
  }

  /** Checks scratch independence across lower initialization levels and changed directed inputs. */
  @Test
  void scratchDoesNotRetainValuesAcrossInitializationOrParameterChanges() {
    for (boolean pr : new boolean[] {false, true}) {
      String[] names = {"water", "CO2", "methane", "methanol", "MEG"};
      int n = names.length;
      SystemInterface fluid = pr ? new SystemPrEos(350.0, 10.0) : new SystemSrkEos(350.0, 10.0);
      ComponentGENRTLmodifiedHV[] activities = new ComponentGENRTLmodifiedHV[n];
      String[][] rules = new String[n][n];
      for (int i = 0; i < n; i++) {
        fluid.addComponent(names[i], 1.0 / n);
        activities[i] = new ComponentGENRTLmodifiedHV(names[i], 1.0 / n, 1.0 / n, i);
        Arrays.fill(rules[i], "Classic");
      }
      fluid.setMixingRule(2);
      fluid.setForceSinglePhase(PhaseType.GAS);
      fluid.init(3);
      PhaseInterface phase = fluid.getPhase(0);
      double[][] alpha = new double[n][n];
      double[][] energies = new double[n][n];
      double[][] offsets = new double[n][n];
      double[][] kij = new double[n][n];
      alpha[0][1] = 0.15;
      alpha[1][0] = 0.15;
      energies[0][1] = -1500.0;
      energies[1][0] = 4050.0;
      offsets[0][1] = 11.0;
      offsets[1][0] = -5.2;
      rules[0][1] = "HV";
      rules[1][0] = "HV";
      int step = 0;
      for (int type : new int[] {3, 1, 3, 2, 3}) {
        double temperature = 350.0 + 0.25 * step;
        fluid.setTemperature(temperature);
        phase.setInitType(type);
        offsets[0][1] += 0.125;
        double[][] expected = new double[n][];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
          sum += phase.getComponent(i).getx() * Math.log(
              activities[i].getGamma(phase, n, temperature, 10.0, PhaseType.GAS, alpha, energies, offsets, kij, rules));
          expected[i] = values(activities[i], n);
        }
        assertEquals(sum, ComponentGENRTLmodifiedHV.excessGibbsSum(phase, activities, n, temperature, 10.0,
            PhaseType.GAS, alpha, energies, offsets, kij, rules), 0.0);
        for (int i = 0; i < n; i++) {
          assertArrayEquals(expected[i], values(activities[i], n), 0.0);
        }
        step++;
      }
    }
  }

  /** Checks that custom activity subclasses retain their original virtual entry point. */
  @Test
  void customActivityUsesOriginalEntryPoint() {
    SystemInterface fluid = new SystemPrEos(350.0, 10.0);
    fluid.addComponent("water", 0.5);
    fluid.addComponent("methane", 0.5);
    fluid.setMixingRule(2);
    fluid.init(3);
    CountingActivity custom = new CountingActivity();
    ComponentInterface[] activities = {custom, new ComponentGENRTLmodifiedHV("methane", 0.5, 0.5, 1)};
    double[][] zero = new double[2][2];
    String[][] rules = {{"HV", "HV"}, {"HV", "HV"}};
    double actual = ComponentGENRTLmodifiedHV.excessGibbsSum(fluid.getPhase(0), activities, 2, 350.0, 10.0,
        PhaseType.GAS, zero, zero, zero, zero, rules);
    assertEquals(0.5 * Math.log(2.0), actual, 0.0);
    assertEquals(1, custom.entries);
  }

  /** Checks the actual phase entry point with asymmetric offsets, raw selectors and custom activity fallback. */
  @Test
  void phaseEntryPreservesDirectedRawSelectorsAndSubclassDispatch() {
    SystemInterface fluid = new SystemPrEos(350.0, 10.0);
    fluid.addComponent("water", 0.3);
    fluid.addComponent("methane", 0.7);
    fluid.setMixingRule(2);
    fluid.setForceSinglePhase(PhaseType.GAS);
    fluid.init(3);
    PhaseInterface phase = fluid.getPhase(0);
    double[][] alpha = {{0.0, 0.15}, {0.15, 0.0}};
    double[][] energies = {{0.0, -1500.0}, {4050.0, 0.0}};
    double[][] offsets = {{0.0, 11.0}, {-5.2, 0.0}};
    double[][] kij = {{0.0, 0.03}, {0.03, 0.0}};
    for (String selector : new String[] {"HV", " HV ", "Classic", " Classic ", new String(new char[] {'H', 'V'})}) {
      String[][] rules = {{"Classic", selector}, {selector, "Classic"}};
      PhaseGENRTLmodifiedHV ge = new PhaseGENRTLmodifiedHV(phase, alpha, energies, offsets, rules, kij);
      ge.setParams(phase, alpha, energies, offsets, rules, kij);
      double sum = 0.0;
      double[][] expected = new double[2][];
      for (int i = 0; i < 2; i++) {
        ComponentGENRTLmodifiedHV activity = (ComponentGENRTLmodifiedHV) ge.getComponent(i);
        sum += phase.getComponent(i).getx()
            * Math.log(activity.getGamma(phase, 2, 350.0, 10.0, PhaseType.GAS, alpha, energies, offsets, kij, rules));
        expected[i] = values(activity, 2);
      }
      assertEquals(neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature()
          * phase.getNumberOfMolesInPhase() * sum, ge.getExcessGibbsEnergy(phase, 2, 350.0, 10.0, PhaseType.GAS), 0.0);
      for (int i = 0; i < 2; i++) {
        assertArrayEquals(expected[i], values((ComponentGENRTLmodifiedHV) ge.getComponent(i), 2), 0.0);
      }
      rules[0][1] = null;
      assertThrows(NullPointerException.class, () -> ge.getExcessGibbsEnergy(phase, 2, 350.0, 10.0, PhaseType.GAS));
      assertEquals(0.0, ge.getExcessGibbsEnergy(), 0.0, "A failed evaluation must not expose the previous sum");
      rules[0][1] = selector;
      assertEquals(neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature()
          * phase.getNumberOfMolesInPhase() * sum, ge.getExcessGibbsEnergy(phase, 2, 350.0, 10.0, PhaseType.GAS), 0.0);
      CountingActivity custom = new CountingActivity();
      ComponentInterface[] components = {custom, ge.getComponent(1)};
      ge.setComponentArray(components);
      double customSum = phase.getComponent(0).getx() * Math.log(2.0)
          + phase.getComponent(1).getx() * Math.log(((ComponentGENRTLmodifiedHV) components[1]).getGamma(phase, 2,
              350.0, 10.0, PhaseType.GAS, alpha, energies, offsets, kij, rules));
      assertEquals(neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature()
          * phase.getNumberOfMolesInPhase() * customSum, ge.getExcessGibbsEnergy(phase, 2, 350.0, 10.0, PhaseType.GAS),
          0.0);
      assertEquals(1, custom.entries);
    }
  }

  /** Checks complete explicit zero-offset binding on both EOS families. */
  @Test
  void explicitZeroOffsetConfigurationRetainsCompleteInputs() {
    for (boolean pr : new boolean[] {false, true}) {
      SystemInterface fluid = pr ? new SystemPrEos(350.0, 10.0) : new SystemSrkEos(350.0, 10.0);
      fluid.addComponent("water", 0.3);
      fluid.addComponent("methane", 0.7);
      fluid.setMixingRule(2);
      fluid.setForceSinglePhase(PhaseType.GAS);
      fluid.init(3);
      PhaseInterface phase = fluid.getPhase(0);
      double[][] alpha = {{0.0, 0.15}, {0.15, 0.0}};
      double[][] energies = {{0.0, -1500.0}, {4050.0, 0.0}};
      double[][] kij = {{0.0, 0.03}, {0.03, 0.0}};
      String[][] rules = {{"Classic", "HV"}, {"HV", "Classic"}};
      PhaseGENRTLmodifiedHV ge = new PhaseGENRTLmodifiedHV(phase, alpha, energies, rules, kij);
      ge.setParams(phase, alpha, energies, new double[2][2], rules, kij);
      double sum = 0.0;
      double[][] expected = new double[2][];
      for (int i = 0; i < 2; i++) {
        ComponentGENRTLmodifiedHV activity = (ComponentGENRTLmodifiedHV) ge.getComponent(i);
        sum += phase.getComponent(i).getx()
            * Math.log(activity.getGamma(phase, 2, 350.0, 10.0, PhaseType.GAS, alpha, energies, kij, rules));
        expected[i] = values(activity, 2);
      }
      assertEquals(neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature()
          * phase.getNumberOfMolesInPhase() * sum, ge.getExcessGibbsEnergy(phase, 2, 350.0, 10.0, PhaseType.GAS), 0.0);
      for (int i = 0; i < 2; i++) {
        assertArrayEquals(expected[i], values((ComponentGENRTLmodifiedHV) ge.getComponent(i), 2), 0.0);
      }
    }
  }

  /**
   * Installs counting delegates on the supplied forced phase.
   *
   * @param phase original cubic phase
   * @param n component count
   */
  private void wrap(PhaseInterface phase, int n) {
    ComponentEosInterface[] wrapped = new ComponentEosInterface[phase.getcomponentArray().length];
    for (int i = 0; i < n; i++) {
      final ComponentInterface original = phase.getComponent(i);
      wrapped[i] = (ComponentEosInterface) Proxy.newProxyInstance(getClass().getClassLoader(),
          new Class<?>[] {ComponentEosInterface.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("aT")) {
              calls[0]++;
            } else if (method.getName().equals("diffaT")) {
              calls[1]++;
            } else if (method.getName().equals("diffdiffaT")) {
              calls[2]++;
            }
            return method.invoke(original, arguments);
          });
    }
    phase.setComponentArray(wrapped);
  }

  /**
   * Copies every activity output before another evaluation mutates it.
   *
   * @param component evaluated activity component
   * @param n derivative-vector size
   * @return finite activity and derivative vector
   */
  private static double[] values(ComponentGENRTLmodifiedHV component, int n) {
    double[] values = new double[n + 4];
    values[0] = component.getGamma();
    values[1] = component.getLnGamma();
    values[2] = component.getLnGammadt();
    values[3] = component.getLnGammadtdt();
    for (int i = 0; i < n; i++) {
      values[i + 4] = component.getLnGammadn(i);
    }
    for (double value : values) {
      assertTrue(Double.isFinite(value));
    }
    return values;
  }

  /** Activity subclass used solely to check virtual dispatch. */
  private static final class CountingActivity extends ComponentGENRTLmodifiedHV {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000;
    /** Actual virtual entries. */
    private int entries;

    /** Constructs the custom water activity. */
    private CountingActivity() {
      super("water", 0.5, 0.5, 0);
    }

    /** {@inheritDoc} */
    @Override
    public double getGamma(PhaseInterface phase, int count, double temperature, double pressure, PhaseType pt,
        double[][] alpha, double[][] energies, double[][] offsets, double[][] kij, String[][] rules) {
      entries++;
      return 2.0;
    }
  }
}
