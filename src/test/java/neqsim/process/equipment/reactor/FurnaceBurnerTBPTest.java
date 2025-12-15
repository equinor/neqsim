package neqsim.process.equipment.reactor;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class FurnaceBurnerTBPTest {

  @Test
  public void testFurnaceBurnerWithTBPFraction() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 1.0);
    fluid.addComponent("methane", 0.5);
    // Add a TBP fraction.
    // name, numberOfMoles, molarMass (kg/mol), density (g/cm3)
    fluid.addTBPfraction("C7_TBP", 0.5, 0.100, 0.7);

    Stream fuel = new Stream("Fuel", fluid);
    fuel.run();

    SystemSrkEos air = new SystemSrkEos(298.15, 1.0);
    air.addComponent("nitrogen", 0.79);
    air.addComponent("oxygen", 0.21);

    Stream airStream = new Stream("Air", air);
    airStream.run();

    FurnaceBurner burner = new FurnaceBurner("Burner");
    burner.setFuelInlet(fuel);
    burner.setAirInlet(airStream);

    assertDoesNotThrow(() -> burner.run());
  }
}
