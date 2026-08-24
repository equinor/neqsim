package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Calculator;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for calculators coupled to material recycle loops. */
class CalculatorRecycleHybridExecutionTest {
  @Test
  void calculatorWithUnregisteredStreamsRunsDuringEveryRecycleIteration() {
    SystemInterface fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream recycleReturn = new Stream("recycle return", fluid.clone());
    recycleReturn.setFlowRate(0.0, "kg/hr");
    Stream makeup = new Stream("makeup", fluid.clone());
    makeup.setFlowRate(0.0, "kg/hr");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(feed);
    mixer.addStream(recycleReturn);
    mixer.addStream(makeup);

    Splitter splitter = new Splitter("splitter", mixer.getOutletStream(), 2);
    splitter.setSplitFactors(new double[] { 0.8, 0.2 });

    Recycle recycle = new Recycle("recycle");
    recycle.addStream(splitter.getSplitStream(1));
    recycle.setOutletStream(recycleReturn);
    recycle.setTolerance(1.0e-8);

    AtomicInteger calculatorRuns = new AtomicInteger();
    Calculator calculator = new Calculator("makeup calculator");
    calculator.addInputVariable(splitter.getSplitStream(0));
    calculator.setOutputVariable(makeup);
    calculator.setCalculationMethod((inputs, output) -> {
      calculatorRuns.incrementAndGet();
      Stream product = (Stream) inputs.get(0);
      Stream makeupStream = (Stream) output;
      makeupStream.setFlowRate(0.1 * product.getFlowRate("kg/hr"), "kg/hr");
      makeupStream.run();
    });

    ProcessSystem process = new ProcessSystem("calculator recycle regression");
    process.add(feed);
    process.add(calculator);
    process.add(mixer);
    process.add(splitter);
    process.add(recycle);

    process.runOptimized();

    assertTrue(recycle.solved(), "material recycle must converge");
    assertTrue(calculatorRuns.get() > 1, "calculator must be re-evaluated as the recycle state changes");
    assertEquals(1111.111, splitter.getSplitStream(0).getFlowRate("kg/hr"), 1.0e-3);
    assertEquals(111.111, makeup.getFlowRate("kg/hr"), 1.0e-3);
  }
}
