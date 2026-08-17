package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/** Executable contract for the complete calculator documentation example. */
class CalculatorDocumentationTest {
  @Test
  void completeCalculatorExampleRunsThroughProcessSystem() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 20.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    Stream adjusted = new Stream("adjusted", fluid.clone());
    adjusted.setFlowRate(0.0, "kg/hr");

    Calculator calculator = new Calculator("flow calculator");
    calculator.addInputVariable(feed);
    calculator.setOutputVariable(adjusted);
    calculator.setCalculationMethod((inputs, output) -> {
      Stream source = (Stream) inputs.get(0);
      Stream target = (Stream) output;
      target.setFlowRate(1.10 * source.getFlowRate("kg/hr"), "kg/hr");
      target.run();
    });

    ProcessSystem process = new ProcessSystem("calculator documentation example");
    process.add(feed);
    process.add(calculator);
    process.run();

    assertEquals(1100.0, adjusted.getFlowRate("kg/hr"), 1.0e-8);
  }
}
