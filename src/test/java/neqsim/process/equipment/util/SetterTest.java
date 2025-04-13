package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

public class SetterTest {

  private Setter setter;
  SystemInterface testFluid;


  @BeforeEach
  public void setUp() {
    setter = new Setter("Test Setter");
    testFluid = neqsim.thermo.FluidCreator.create("dry gas");
    testFluid.init(0);
  }

  @Test
  public void testRunWithStream() {
    // Mock a Stream object
    Stream stream = new Stream("Test Stream", testFluid);
    setter.addTargetEquipment(stream);

    // Set parameters and run
    setter.addParameter("pressure", "bar", 10.0);
    setter.run();

    // Assert that the pressure was set correctly
    assertEquals(10.0, stream.getPressure("bar"), 0.01);
  }

  @Test
  public void testRunWithThrottlingValve() {
    // Mock a ThrottlingValve object
    Stream stream = new Stream("Test Stream", testFluid);
    setter.addTargetEquipment(stream);
    stream.run();
    ThrottlingValve valve = new ThrottlingValve("Test Valve", stream);
    setter = new Setter("Test Setter");
    setter.addTargetEquipment(valve);

    // Set parameters and run
    setter.addParameter("pressure", "bara", 5.0);
    setter.run(UUID.randomUUID());

    // Assert that the outlet pressure was set correctly
    assertEquals(5.0, valve.getOutletPressure(), 0.01);
  }

  @Test
  public void testRunWithHeater() {
    // Mock a Heater object
    Stream stream = new Stream("Test Stream", testFluid);
    setter.addTargetEquipment(stream);

    Heater heater = new Heater("Test Heater", stream);
    setter.addTargetEquipment(heater);

    Setter setter2 = new Setter("Test Setter");
    Compressor comp1 = new Compressor("Test Compressor", stream);
    setter2.addTargetEquipment(comp1);
    setter2.addParameter("pressure", "bar", 50.0);
    setter2.run(UUID.randomUUID());
    comp1.run();

    Pump pump1 = new Pump("Test Pump", stream);
    setter2.addTargetEquipment(pump1);
    setter2.run(UUID.randomUUID());
    pump1.run();

    // Set parameters and run
    setter.addParameter("temperature", "C", 150.0);
    setter.addParameter("pressure", "bar", 5.0);
    setter.run(UUID.randomUUID());
    heater.run();

    // Assert that the outlet temperature was set correctly
    assertEquals(150.0, heater.getOutletTemperature() - 273.15, 0.01);
    assertEquals(5.0, heater.getOutletPressure(), 0.01);
    assertEquals(50.0, comp1.getOutletPressure(), 0.01);
    assertEquals(50.0, pump1.getOutletPressure(), 0.01);
  }

  @Test
  public void testRunWithCooler() {
    // Mock a Cooler object
    Stream stream = new Stream("Test Stream", testFluid);
    Cooler cooler = new Cooler("Test Cooler", stream);
    setter.addTargetEquipment(cooler);

    // Set parameters and run
    setter.addParameter("temperature", "C", 5.0);
    setter.run(UUID.randomUUID());
    cooler.run();

    // Assert that the outlet temperature was set correctly
    assertEquals(5.0, cooler.getOutletTemperature() - 273.15, 0.01);
  }

}
