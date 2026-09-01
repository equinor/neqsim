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

  @Test
  public void testFlexibleSetPoint() {
    testFluid = neqsim.thermo.FluidCreator.create("dry gas");

    Stream sourceStream = new Stream("Source Stream", testFluid);
    sourceStream.setTemperature(300.0, "K");
    sourceStream.run();

    Stream targetStream = new Stream("Target Stream", testFluid.clone());
    targetStream.setPressure(10.0, "bara");
    targetStream.run();

    SetPoint setPoint = new SetPoint("Custom SetPoint");
    setPoint.setSourceVariable(sourceStream);
    setPoint.setTargetVariable(targetStream, "pressure");

    // Set target pressure based on source temperature: P = T / 10.0
    setPoint.setSourceValueCalculator((equipment) -> {
      Stream s = (Stream) equipment;
      return s.getTemperature("K") / 10.0;
    });

    setPoint.run();
    targetStream.run();

    assertEquals(30.0, targetStream.getPressure("bara"), 0.01);
  }

  @Test
  public void testMultiplierAndOffset() {
    testFluid = neqsim.thermo.FluidCreator.create("dry gas");

    Stream sourceStream = new Stream("Source Stream", testFluid);
    sourceStream.setPressure(60.0, "bara");
    sourceStream.setTemperature(20.0, "C");
    sourceStream.run();

    // UniSim SET-style pressure link with an offset: target = source - 0.30 bara
    // (mirrors a UniSim SET with a -30 kPa offset).
    Stream targetStream = new Stream("Target Stream", testFluid.clone());
    targetStream.setPressure(10.0, "bara");
    targetStream.run();

    SetPoint offsetSet = new SetPoint("Offset Set");
    offsetSet.setSourceVariable(sourceStream, "pressure");
    offsetSet.setTargetVariable(targetStream, "pressure");
    offsetSet.setOffset(-0.30);
    assertEquals(1.0, offsetSet.getMultiplier(), 1e-12);
    assertEquals(-0.30, offsetSet.getOffset(), 1e-12);
    offsetSet.run();
    targetStream.run();
    assertEquals(59.70, targetStream.getPressure("bara"), 0.01);

    // Multiplier link: target = 0.5 * source
    Stream targetStream2 = new Stream("Target Stream 2", testFluid.clone());
    targetStream2.setPressure(10.0, "bara");
    targetStream2.run();

    SetPoint multSet = new SetPoint("Mult Set");
    multSet.setSourceVariable(sourceStream, "pressure");
    multSet.setTargetVariable(targetStream2, "pressure");
    multSet.setMultiplier(0.5);
    multSet.run();
    targetStream2.run();
    assertEquals(30.0, targetStream2.getPressure("bara"), 0.01);

    // Default (no multiplier/offset) is unchanged: target = source
    Stream targetStream3 = new Stream("Target Stream 3", testFluid.clone());
    targetStream3.setPressure(10.0, "bara");
    targetStream3.run();

    SetPoint plainSet = new SetPoint("Plain Set");
    plainSet.setSourceVariable(sourceStream, "pressure");
    plainSet.setTargetVariable(targetStream3, "pressure");
    plainSet.run();
    targetStream3.run();
    assertEquals(60.0, targetStream3.getPressure("bara"), 0.01);
  }
}
