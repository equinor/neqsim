package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Deterministic overlapping evaluations of the zero-interaction mixing rule used by PFCT reference fluids.
 *
 * @author NeqSim
 * @version 1.0
 */
class NoInteractionMixingRuleAccumulatorTest {
  /**
   * A nested phase calculation cannot replace a partially accumulated attraction derivative.
   *
   */
  @Test
  void nestedEvaluationPreservesCalculationLocalSum() {
    PhaseInterface first = phase(300.0);
    PhaseInterface second = phase(420.0);
    InterleavingComponent component = new InterleavingComponent();
    component.init(300.0, 40.0, 1.0, 1.0, 3);
    first.getcomponentArray()[0] = component;
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    EosMixingRuleHandler.ClassicVdW rule = handler.new ClassicVdW();
    double expected = calculate(rule, first, true);
    component.interference = () -> calculate(rule, second, true);
    assertEquals(expected, calculate(rule, first, true), 0.0);
  }

  /**
   * Shared reference-fluid rules keep each mole derivative independent during concurrent evaluation.
   *
   * @throws Exception if a worker fails
   */
  @Test
  void concurrentMoleDerivativesKeepTheirOwnSum() throws Exception {
    EosMixingRuleHandler handler = new EosMixingRuleHandler();
    EosMixingRuleHandler.ClassicVdW rule = handler.new ClassicVdW();
    List<Callable<Void>> calculations = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      final PhaseInterface phase = phase(280.0 + 60.0 * index);
      final double expected = calculate(rule, phase, false);
      calculations.add(() -> {
        for (int repeat = 0; repeat < 10000; repeat++) {
          assertEquals(expected, calculate(rule, phase, false), 0.0);
        }
        return null;
      });
    }
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      for (Future<Void> result : executor.invokeAll(calculations)) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Evaluates either attraction derivative on the supplied phase.
   *
   * @param rule shared parameter handler's calculation object
   * @param phase phase whose state is independent
   * @param temperatureDerivative whether to evaluate the second temperature derivative
   * @return attraction derivative in NeqSim's internal units
   */
  private double calculate(EosMixingRuleHandler.ClassicVdW rule, PhaseInterface phase, boolean temperatureDerivative) {
    return temperatureDerivative
        ? rule.calcATT(phase, phase.getTemperature(), phase.getPressure(), phase.getNumberOfComponents())
        : rule.calcAi(0, phase, phase.getTemperature(), phase.getPressure(), phase.getNumberOfComponents());
  }

  /**
   * Creates an initialized gas phase.
   *
   * @param temperature temperature in K
   * @return initialized gas phase
   */
  private PhaseInterface phase(double temperature) {
    SystemInterface fluid = new SystemPrEos(temperature, 40.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-decane", 0.3);
    fluid.setMixingRule(1);
    fluid.init(0);
    fluid.init(3);
    return fluid.getPhase(0);
  }

  /** Methane component permitting a one-shot nested evaluation during accumulation. */
  private static class InterleavingComponent extends ComponentPR {
    /** Serialization identifier. */
    private static final long serialVersionUID = 1L;
    /** One-shot nested calculation. */
    private transient Runnable interference;

    /** Initializes methane with the first phase's mole amount. */
    InterleavingComponent() {
      super("methane", 0.7, 0.7, 0);
    }

    /** {@inheritDoc} */
    @Override
    public double getaT() {
      Runnable calculation = interference;
      interference = null;
      if (calculation != null) {
        calculation.run();
      }
      return super.getaT();
    }
  }
}
