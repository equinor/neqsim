package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class PressureDropTest {
  @Test
  void testRun() {
    double pressure_inlet = 85.0;
    double dp = 0.1;
    double temperature_inlet = 35.0;
    double gasFlowRate = 5.0;

    neqsim.thermo.system.SystemInterface testSystem = new SystemSrkEos(298.0, 100.0);
    testSystem.addComponent("methane", 100.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    inletStream.run();

    PressureDrop dpGasStream1 = new PressureDrop("dp element 1", inletStream);
    dpGasStream1.setPressureDrop(dp, "bara");
    dpGasStream1.run();

    Stream outletStream = new Stream("outletStream", dpGasStream1.getOutletStream());
    outletStream.run();

    assertEquals(pressure_inlet - dp, outletStream.getPressure("bara"), 1e-16);
    assertEquals(34.967499, outletStream.getTemperature("C"), 0.001);
  }
}
