package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Forces overlapping classic mixing-rule evaluations without relying on thread scheduling.
 *
 * <p>
 * A virtual callback evaluates a second phase after the first calculation has started. Both rules share the same
 * handler, as configured sibling fluid clones do. Returned physical values must match isolated evaluation even though
 * legacy last-result fields are overwritten by the nested calculation. This does not qualify every mixing-rule family
 * or permit concurrent configuration changes.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ClassicMixingRuleAccumulatorTest {
  /**
   * A second phase's temperature derivative must not overwrite the first phase's partially accumulated derivative.
   *
   * @param pr whether to initialize Peng-Robinson instead of SRK component properties
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void classicTemperatureDerivativeKeepsItsOwnAccumulator(boolean pr) {
    PhaseInterface first = phase(pr, 300.0);
    PhaseInterface second = phase(pr, 340.0);
    EosMixingRuleHandler handler = handler();
    EosMixingRuleHandler.ClassicSRK nested = handler.new ClassicSRK();
    double expected = nested.calcATT(first, first.getTemperature(), first.getPressure(), 2);
    handler.Atot = 123.0;
    int[] calls = {0};
    EosMixingRuleHandler.ClassicSRK interleaved = handler.new ClassicSRK() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public double getkij(double temperature, int firstComponent, int secondComponent) {
        if (++calls[0] == 2) {
          nested.calcATT(second, second.getTemperature(), second.getPressure(), 2);
        }
        return super.getkij(temperature, firstComponent, secondComponent);
      }
    };
    double actual = interleaved.calcATT(first, first.getTemperature(), first.getPressure(), 2);
    assertTrue(calls[0] >= 2);
    assertTrue(Double.isFinite(expected) && expected != 0.0);
    assertEquals(expected, actual, 0.0);
    assertEquals(actual, handler.A, 0.0);
    assertEquals(123.0, interleaved.getA(), 0.0, "A temperature derivative must not replace the attraction total");
  }

  /**
   * The general covolume sum must be independent of another phase evaluated after its accumulator is initialized.
   *
   * @param pr whether to initialize Peng-Robinson instead of SRK component properties
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void generalCovolumeKeepsItsOwnAccumulator(boolean pr) {
    PhaseInterface first = phase(pr, 300.0);
    PhaseInterface second = phase(pr, 340.0);
    EosMixingRuleHandler handler = handler();
    EosMixingRuleHandler.ClassicVdW rule = handler.new ClassicVdW();
    rule.setBmixType(1);
    double expected = rule.calcBFull(first, first.getTemperature(), first.getPressure(), 2);
    int[] calls = {0};
    PhaseInterface interleaved = (PhaseInterface) Proxy.newProxyInstance(PhaseInterface.class.getClassLoader(),
        new Class<?>[] {PhaseInterface.class}, (proxy, method, arguments) -> {
          if ("getcomponentArray".equals(method.getName()) && ++calls[0] == 1) {
            rule.calcBFull(second, second.getTemperature(), second.getPressure(), 2);
          }
          try {
            return method.invoke(first, arguments);
          } catch (InvocationTargetException ex) {
            throw ex.getCause();
          }
        });
    double actual = rule.calcBFull(interleaved, first.getTemperature(), first.getPressure(), 2);
    assertEquals(1, calls[0]);
    assertTrue(Double.isFinite(expected) && expected > 0.0);
    assertEquals(expected, actual, 0.0);
    assertEquals(actual, handler.B, 0.0);
    assertEquals(actual, rule.getB(), 0.0, "The legacy covolume result remains available after serial evaluation");
  }

  /**
   * Composition-dependent attraction must not consume a nested phase's attraction total during its own sum.
   *
   * @param pr whether to initialize Peng-Robinson instead of SRK component properties
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compositionDependentAttractionKeepsItsOwnAccumulator(boolean pr) {
    PhaseInterface first = phase(pr, 300.0);
    PhaseInterface second = phase(pr, 340.0);
    EosMixingRuleHandler handler = handler();
    EosMixingRuleHandler.ClassicSRKT2x nested = handler.new ClassicSRKT2x();
    double expected = nested.calcA(first, first.getTemperature(), first.getPressure(), 2);
    int[] calls = {0};
    EosMixingRuleHandler.ClassicSRKT2x interleaved = handler.new ClassicSRKT2x() {
      private static final long serialVersionUID = 1L;

      /** {@inheritDoc} */
      @Override
      public double getkij(PhaseInterface phase, double temperature, int firstComponent, int secondComponent) {
        if (++calls[0] == 2) {
          nested.calcA(second, second.getTemperature(), second.getPressure(), 2);
        }
        return super.getkij(phase, temperature, firstComponent, secondComponent);
      }
    };
    double actual = interleaved.calcA(first, first.getTemperature(), first.getPressure(), 2);
    assertTrue(calls[0] >= 2);
    assertTrue(Double.isFinite(expected) && expected > 0.0);
    assertEquals(expected, actual, 0.0);
    assertEquals(actual, handler.A, 0.0);
    assertEquals(actual, interleaved.getA(), 0.0,
        "The legacy attraction result remains available after serial evaluation");
  }

  /**
   * Initializes physical component properties without using a shared test mixing-rule handler.
   *
   * @param pr whether to initialize Peng-Robinson instead of SRK
   * @param temperature temperature in K
   * @return initialized vapor-root phase
   */
  private PhaseInterface phase(boolean pr, double temperature) {
    SystemInterface fluid = pr ? new SystemPrEos(temperature, 40.0) : new SystemSrkEos(temperature, 40.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-decane", 0.3);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(3);
    return fluid.getPhase(0);
  }

  /**
   * Creates fixed interaction data shared by the overlapping calculations.
   *
   * @return configured handler with temperature and asymmetric composition terms
   */
  private EosMixingRuleHandler handler() {
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    handler.intparam = new double[][] {{0.0, 0.03}, {0.03, 0.0}};
    handler.intparamT = new double[][] {{0.0, 1.0e-4}, {2.0e-4, 0.0}};
    handler.intparamij = new double[][] {{0.0, 0.02}, {0.05, 0.0}};
    handler.intparamji = new double[][] {{0.0, 0.04}, {0.03, 0.0}};
    handler.intparamTType = new int[2][2];
    return handler;
  }
}
