package neqsim.process.equipment.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;

class ElectrolyzerTest extends neqsim.NeqSimTest {
  @Test
  void testElectrolyzer() {
    SystemInterface water = new Fluid().create("water");
    Stream inlet = new Stream("water", water);
    inlet.setPressure(1.0, "bara");
    inlet.setTemperature(298.15, "K");
    inlet.setFlowRate(2.0, "mole/sec");
    inlet.run();

    Electrolyzer el = new Electrolyzer("el", inlet);
    el.run();

    assertEquals(2.0, el.getHydrogenOutStream().getFlowRate("mole/sec"), 1e-6);
    assertEquals(1.0, el.getOxygenOutStream().getFlowRate("mole/sec"), 1e-6);
    double expectedPower = 2.0 * 2.0 * 96485.3329 * 1.23;
    assertEquals(expectedPower, el.getEnergyStream().getDuty(), 1.0);
  }
}
