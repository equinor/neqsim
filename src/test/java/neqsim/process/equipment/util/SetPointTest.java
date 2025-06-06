package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;

public class SetPointTest {
  SystemInterface testFluid;

  @Test
  void testRun() {
    testFluid = neqsim.thermo.FluidCreator.create("dry gas");

    Stream stream = new Stream("Test Stream", testFluid);
    stream.setPressure(20.0, "bara");
    stream.setTemperature(22.0, "C");
    stream.run();

    Stream stream2 = new Stream("Test Stream", testFluid.clone());
    stream2.setPressure(10.0, "bara");
    stream2.setTemperature(32.0, "C");
    stream2.run();

    SetPoint setter = new SetPoint("Test Setter");
    setter.setSourceVariable(stream, "pressure");
    setter.setTargetVariable(stream2, "pressure");
    setter.run();

    assertEquals(20.0, stream2.getPressure(), 0.01);

    Heater heater = new Heater("Test Heater", stream);
    heater.setOutTemperature(100.0, "C");
    heater.run();

    SetPoint setter2 = new SetPoint("Test Setter2");
    setter2.setSourceVariable(stream, "temperature");
    setter2.setTargetVariable(heater);
    setter2.run();
    heater.run();
    heater.getOutletStream().getTemperature("C");
    assertEquals(22.0, heater.getOutletStream().getTemperature("C"), 0.01);

    Heater heater2 = new Heater("Test Heater", stream);
    heater2.setOutPressure(23.0, "bara");
    heater2.setdT(0.0);
    heater2.run();

    assertEquals(22.0, heater2.getOutletStream().getTemperature("C"), 0.01);
    assertEquals(23.0, heater2.getOutletStream().getPressure("bara"), 0.01);

    Pump pump1 = new Pump("Test Pump", stream2);
    pump1.setOutletPressure(45.0, "bara");
    pump1.run();

    SetPoint setter3 = new SetPoint("Test Setter3");
    setter3.setSourceVariable(stream, "pressure");
    setter3.setTargetVariable(pump1);
    setter3.run();
    pump1.run();
    assertEquals(20.0, pump1.getOutletStream().getPressure(), 0.01);
  }
}
