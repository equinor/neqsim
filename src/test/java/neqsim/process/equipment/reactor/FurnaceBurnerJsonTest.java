package neqsim.process.equipment.reactor;

import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FurnaceBurnerJsonTest {

  @Test
  public void testToJson() {
    SystemInterface gas = new SystemSrkEos(298.15, 1.0);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");

    Stream fuel = new Stream("fuel", gas);
    fuel.setFlowRate(1.0, "kg/hr");

    SystemInterface air = new SystemSrkEos(298.15, 1.0);
    air.addComponent("nitrogen", 0.79);
    air.addComponent("oxygen", 0.21);
    air.setMixingRule("classic");

    Stream airStream = new Stream("air", air);
    airStream.setFlowRate(20.0, "kg/hr"); // Excess air

    FurnaceBurner burner = new FurnaceBurner("testBurner");
    burner.setFuelInlet(fuel);
    burner.setAirInlet(airStream);

    fuel.run();
    airStream.run();
    burner.run();

    String json = burner.toJson();
    System.out.println("JSON Output: " + json);

    assertNotNull(json);
    assertTrue(json.contains("flame temperature"));
    assertTrue(json.contains("heat release"));
    assertTrue(json.contains("outlet temperature"));
    assertTrue(json.contains("mole fraction CO2"));
    assertTrue(json.contains("mole fraction nitrogen"));
    assertTrue(json.contains("fuel inlet temperature"));
    assertTrue(json.contains("fuel inlet pressure"));
    assertTrue(json.contains("fuel inlet flow"));
    assertTrue(json.contains("air inlet temperature"));
    assertTrue(json.contains("air inlet pressure"));
    assertTrue(json.contains("air inlet flow"));
  }
}
