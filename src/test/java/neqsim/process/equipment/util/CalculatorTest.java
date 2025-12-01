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
}
