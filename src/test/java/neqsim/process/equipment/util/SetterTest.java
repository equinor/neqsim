package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.Fluid;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.heatexchanger.Cooler;

public class SetterTest {

  private Setter setter;
  SystemInterface testFluid;

  @BeforeEach
  public void setUp() {
    setter = new Setter("Test Setter");
    testFluid = neqsim.thermo.FluidCreator.create("natural gas");
  }

  @Test
  public void testRunWithStream() {
    // Mock a Stream object
    Stream stream = new Stream("Test Stream", testFluid);
    setter.addTargetEquipment(stream);

    // Set parameters and run
    setter.setParameter("pressure", "bar", 10.0);
    setter.run(UUID.randomUUID());

    // Assert that the pressure was set correctly
    assertEquals(10.0, stream.getPressure("bar"), 0.01);
  }

  @Test
  public void testRunWithThrottlingValve() {
    // Mock a ThrottlingValve object
    ThrottlingValve valve = new ThrottlingValve("Test Valve");
    setter.addTargetEquipment(valve);

    // Set parameters and run
    setter.setParameter("pressure", "bar", 5.0);
    setter.run(UUID.randomUUID());

    // Assert that the outlet pressure was set correctly
    assertEquals(5.0, valve.getOutletPressure(), 0.01);
  }

  @Test
  public void testRunWithHeater() {
    // Mock a Heater object
    Heater heater = new Heater("Test Heater");
    setter.addTargetEquipment(heater);

    // Set parameters and run
    setter.setParameter("temperature", "C", 150.0);
    setter.run(UUID.randomUUID());

    // Assert that the outlet temperature was set correctly
    assertEquals(150.0, heater.getOutletTemperature() - 273.15, 0.01);
  }

  @Test
  public void testRunWithCooler() {
    // Mock a Cooler object
    Cooler cooler = new Cooler("Test Cooler");
    setter.addTargetEquipment(cooler);

    // Set parameters and run
    setter.setParameter("temperature", "Celsius", 5.0);
    setter.run(UUID.randomUUID());

    // Assert that the outlet temperature was set correctly
    assertEquals(5.0, cooler.getOutletTemperature() - 273.15, 0.01);
  }

}
