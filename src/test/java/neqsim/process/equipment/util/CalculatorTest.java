package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class CalculatorTest {
  @Test
  public void testFlexibleCalculator() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setFlowRate(1.0, "kg/hr");
    inletStream.run();

    Stream outletStream = new Stream("outlet stream", testSystem.clone());
    outletStream.setFlowRate(1.0, "kg/hr");
    outletStream.run();

    Calculator calculator = new Calculator("Energy Calculator");
    calculator.addInputVariable(inletStream);
    calculator.setOutputVariable(outletStream);

    calculator.setCalculationMethod((inputs, output) -> {
      Stream in = (Stream) inputs.get(0);
      Stream out = (Stream) output;
      double energy = in.LCV() * in.getFlowRate("Sm3/hr");
      // Adjust temperature of output stream based on energy
      // For testing, we just set a specific temperature
      out.setTemperature(350.0, "K");
    });

    calculator.run();

    assertEquals(350.0, outletStream.getTemperature("K"), 0.1);
  }

  @Test
  public void testSimpleCalculator() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);
    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setFlowRate(1.0, "kg/hr");
    inletStream.run();

    Stream outletStream = new Stream("outlet stream", testSystem.clone());
    outletStream.setFlowRate(1.0, "kg/hr");
    outletStream.run();

    Calculator calculator = new Calculator("Energy Calculator");
    // No need to add input/output variables if we capture them in the lambda

    calculator.setCalculationMethod(() -> {
      double energy = inletStream.LCV() * inletStream.getFlowRate("Sm3/hr");
      // Adjust temperature of output stream based on energy
      // For testing, we just set a specific temperature
      outletStream.setTemperature(350.0, "K");
    });

    calculator.run();

    assertEquals(350.0, outletStream.getTemperature("K"), 0.1);
  }

  @Test
  public void testCalculatorWithInputsAndOutputs() {
    SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
    testSystem.addComponent("methane", 100.0);
    Stream inletStream1 = new Stream("inlet stream 1", testSystem);
    inletStream1.setFlowRate(1.0, "kg/hr");
    inletStream1.run();

    Stream inletStream2 = new Stream("inlet stream 2", testSystem.clone());
    inletStream2.setFlowRate(2.0, "kg/hr");
    inletStream2.run();

    Stream outletStream = new Stream("outlet stream", testSystem.clone());
    outletStream.setFlowRate(1.0, "kg/hr");
    outletStream.run();

    Calculator calculator = new Calculator("Total Flow Calculator");

    // Add multiple inputs
    calculator.addInputVariable(inletStream1, inletStream2);
    calculator.setOutputVariable(outletStream);

    // Use the BiConsumer to access the registered inputs and output
    calculator.setCalculationMethod((inputs, output) -> {
      double totalFlow = 0.0;
      for (ProcessEquipmentInterface input : inputs) {
        totalFlow += ((Stream) input).getFlowRate("kg/hr");
      }
      ((Stream) output).setFlowRate(totalFlow, "kg/hr");
    });

    calculator.run();

    assertEquals(3.0, outletStream.getFlowRate("kg/hr"), 0.1);
  }
}
