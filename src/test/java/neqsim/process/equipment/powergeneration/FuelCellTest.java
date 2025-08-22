package neqsim.process.equipment.powergeneration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

public class FuelCellTest extends neqsim.NeqSimTest {
  static Stream fuel;
  static Stream oxidant;

  @BeforeAll
  static void setUp() {
    SystemSrkEos fuelSys = new SystemSrkEos(298.15, 1.0);
    fuelSys.addComponent("hydrogen", 2.0);
    fuel = new Stream("fuel", fuelSys);
    fuel.setFlowRate(1.0, "kg/hr");
    fuel.setTemperature(298.15, "K");
    fuel.setPressure(1.0, "bara");

    SystemSrkEos oxSys = new SystemSrkEos(298.15, 1.0);
    oxSys.addComponent("oxygen", 1.0);
    oxidant = new Stream("oxidant", oxSys);
    oxidant.setFlowRate(1.0, "kg/hr");
    oxidant.setTemperature(298.15, "K");
    oxidant.setPressure(1.0, "bara");
  }

  @Test
  void testRun() {
    fuel.run();
    oxidant.run();

    FuelCell cell = new FuelCell("cell", fuel, oxidant);
    cell.setEfficiency(0.5);
    cell.run();

    assertTrue(cell.getEnergyStream().getDuty() < 0.0);
    assertTrue(cell.getOutletStream().getFluid().hasComponent("water"));
  }
}
