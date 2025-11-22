package neqsim.process.equipment.reactor;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

public class FurnaceBurnerTest {

  @Test
  public void testNaturalGasCombustion() {
    SystemInterface fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 1.0);
    fuel.addComponent("ethane", 0.1);
    fuel.setTotalFlowRate(10.0, "mole/sec");
    fuel.init(0);

    SystemInterface air = new SystemSrkEos(298.15, 20.0);
    air.addComponent("nitrogen", 79.0);
    air.addComponent("oxygen", 21.0);
    air.setTotalFlowRate(300.0, "mole/sec");
    air.init(0);

    StreamInterface fuelStream = new Stream("fuel", fuel);
    StreamInterface airStream = new Stream("air", air);
    fuelStream.run();
    airStream.run();

    FurnaceBurner burner = new FurnaceBurner("test burner");
    burner.setFuelInlet(fuelStream);
    burner.setAirInlet(airStream);
    burner.setExcessAirFraction(0.15);
    burner.run();

    Assertions.assertTrue(burner.getFlameTemperature() > 500.0, "Flame temperature is set");
    Assertions.assertTrue(burner.getHeatReleasekW() > 0.0, "Heat release should be positive");

    Map<String, Double> emissions = burner.getEmissionRatesKgPerHr();
    Assertions.assertTrue(emissions.getOrDefault("CO2", 0.0) > 0.0, "CO2 emitted");
    Assertions.assertTrue(emissions.containsKey("NOx"), "NOx aggregation added");
    Assertions.assertTrue(emissions.getOrDefault("oxygen", 0.0) > 0.0, "O2 balance kept");
  }

  @Test
  public void testCooledBurnerLowersFlameTemperature() {
    SystemInterface fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 1.0);
    fuel.setTotalFlowRate(5.0, "mole/sec");
    fuel.init(0);

    SystemInterface air = new SystemSrkEos(298.15, 20.0);
    air.addComponent("nitrogen", 79.0);
    air.addComponent("oxygen", 21.0);
    air.setTotalFlowRate(160.0, "mole/sec");
    air.init(0);

    StreamInterface fuelStream = new Stream("fuel", fuel);
    StreamInterface airStream = new Stream("air", air);
    fuelStream.run();
    airStream.run();

    FurnaceBurner burner = new FurnaceBurner("cooled burner");
    burner.setFuelInlet(fuelStream);
    burner.setAirInlet(airStream);
    burner.setExcessAirFraction(0.1);
    burner.run();
    double adiabaticTemp = burner.getFlameTemperature();

    FurnaceBurner cooledBurner = new FurnaceBurner("cooled burner with ambient");
    cooledBurner.setFuelInlet(fuelStream);
    cooledBurner.setAirInlet(airStream);
    cooledBurner.setExcessAirFraction(0.1);
    cooledBurner.setBurnerDesign(FurnaceBurner.BurnerDesign.COOLED);
    cooledBurner.setCoolingFactor(0.3);
    cooledBurner.setSurroundingsTemperature(300.0);
    cooledBurner.run();

    Assertions.assertTrue(cooledBurner.getFlameTemperature() < adiabaticTemp,
        "Cooling should reduce flame temperature");
    Assertions.assertTrue(cooledBurner.getHeatReleasekW() > 0.0,
        "Heat release should remain positive when cooled");
  }

  @Test
  public void testSulfurSpeciesTrackedInEmissions() {
    SystemInterface fuel = new SystemSrkEos(298.15, 20.0);
    fuel.addComponent("methane", 1.0);
    fuel.addComponent("ethane", 0.05);
    fuel.addComponent("H2S", 0.01);
    fuel.setTotalFlowRate(8.0, "mole/sec");
    fuel.init(0);

    SystemInterface air = new SystemSrkEos(298.15, 20.0);
    air.addComponent("nitrogen", 79.0);
    air.addComponent("oxygen", 21.0);
    air.setTotalFlowRate(260.0, "mole/sec");
    air.init(0);

    StreamInterface fuelStream = new Stream("fuel", fuel);
    StreamInterface airStream = new Stream("air", air);
    fuelStream.run();
    airStream.run();

    FurnaceBurner burner = new FurnaceBurner("sulfur burner");
    burner.setFuelInlet(fuelStream);
    burner.setAirInlet(airStream);
    burner.setExcessAirFraction(0.1);
    burner.run();

    Map<String, Double> emissions = burner.getEmissionRatesKgPerHr();
    Assertions.assertTrue(emissions.containsKey("SO2"), "SO2 should be tracked in emissions");
    Assertions.assertTrue(emissions.containsKey("SO3"), "SO3 should be tracked in emissions");
  }
}
