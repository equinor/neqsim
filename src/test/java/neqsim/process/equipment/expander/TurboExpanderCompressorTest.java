package neqsim.process.equipment.expander;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

public class TurboExpanderCompressorTest {
  @Test
  void testRun() {
    System.out.println("Turtall etter konvergens: " );
    neqsim.thermo.system.SystemInterface feedGas =
        new neqsim.thermo.system.SystemSrkEos(273.15 + 42.0, 10.00);
    feedGas.addComponent("nitrogen", 0.245);
    feedGas.addComponent("CO2", 3.4);
    feedGas.addComponent("methane", 85.7);
    feedGas.addComponent("ethane", 5.981);
    feedGas.addComponent("propane", 0.2743);
    feedGas.setMixingRule(10);
    feedGas.init(0);

    Stream feedStream = new Stream("dry feed gas Smorbukk", feedGas);
    feedStream.setFlowRate(1.0, "MSm3/day");
    feedStream.setTemperature(25.0, "C");
    feedStream.setPressure(60.0, "bara");

    TurboExpanderCompressor turboExpander =
        new TurboExpanderCompressor("TurboExpander", feedStream);
    turboExpander.setCompressorRequiredPower(120_000); // W
    turboExpander.setIGVposition(0.8); // 80%
    turboExpander.setExpanderOutPressure(45.0); // bar
    turboExpander.run(UUID.randomUUID());

    System.out.println("Turtall etter konvergens: " + turboExpander.getTurboSpeed());
  }
}
