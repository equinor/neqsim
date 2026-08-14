package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for direct electrical heating (DEH) on a Beggs and Brills pipeline.
 */
class PipeBeggsAndBrillsDehTest {
  private static final double LENGTH = 20000.0;
  private static final double DIAMETER = 0.3;
  private static final double SEABED_TEMPERATURE_C = 4.0;

  private PipeBeggsAndBrills buildPipe() {
    SystemInterface fluid = new SystemSrkEos(313.15, 100.0);
    fluid.addComponent("methane", 90.0);
    fluid.addComponent("ethane", 6.0);
    fluid.addComponent("propane", 4.0);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(100000.0, "kg/hr");

    Stream inlet = new Stream("inlet", fluid);
    inlet.setTemperature(40.0, "C");
    inlet.setPressure(100.0, "bara");
    inlet.run();

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("DEH pipe", inlet);
    pipe.setLength(LENGTH);
    pipe.setDiameter(DIAMETER);
    pipe.setElevation(0.0);
    pipe.setNumberOfIncrements(20);
    pipe.setConstantSurfaceTemperature(SEABED_TEMPERATURE_C, "C");
    pipe.setHeatTransferCoefficient(5.0);
    return pipe;
  }

  @Test
  void dehIsOffByDefault() {
    PipeBeggsAndBrills pipe = buildPipe();
    assertEquals(0.0, pipe.getDirectElectricalHeatingPowerPerMeter(), 1e-12);
    assertEquals(0.0, pipe.getDirectElectricalHeatingPower(), 1e-12);
  }

  @Test
  void totalPowerIsDistributedOverTheLength() {
    PipeBeggsAndBrills pipe = buildPipe();
    pipe.setDirectElectricalHeatingPower(2.0e6);
    assertEquals(100.0, pipe.getDirectElectricalHeatingPowerPerMeter(), 1e-9);
    assertEquals(2.0e6, pipe.getDirectElectricalHeatingPower(), 1e-3);
  }

  @Test
  void dehRaisesTheOutletTemperature() {
    PipeBeggsAndBrills cold = buildPipe();
    cold.run();
    double withoutDeh = cold.getOutletStream().getTemperature("C");

    PipeBeggsAndBrills heated = buildPipe();
    heated.setDirectElectricalHeatingPower(4.0e6);
    heated.run();
    double withDeh = heated.getOutletStream().getTemperature("C");

    assertTrue(withoutDeh < 20.0, "Unheated line should cool well below the 40 C inlet, got " + withoutDeh);
    assertTrue(withDeh > withoutDeh + 5.0,
        "DEH should raise the outlet temperature, got " + withDeh + " vs " + withoutDeh);
  }

  @Test
  void steadyStateApproachesTheDehHeatBalance() {
    // With enough DEH the fluid settles where the electrical input equals the wall
    // loss: P/L = U * pi * D * (T - Tsurf).
    double powerPerMeter = 150.0;
    PipeBeggsAndBrills pipe = buildPipe();
    pipe.setDirectElectricalHeatingPowerPerMeter(powerPerMeter);
    pipe.run();

    double expectedApproach = SEABED_TEMPERATURE_C + powerPerMeter / (5.0 * Math.PI * DIAMETER);
    double outlet = pipe.getOutletStream().getTemperature("C");
    assertTrue(outlet > SEABED_TEMPERATURE_C, "DEH must hold the fluid above the seabed temperature, got " + outlet);
    assertTrue(outlet < expectedApproach + 5.0,
        "Outlet " + outlet + " C should not exceed the DEH balance " + expectedApproach + " C");
  }

  @Test
  void negativePowerIsRejected() {
    PipeBeggsAndBrills pipe = buildPipe();
    assertThrows(IllegalArgumentException.class, () -> pipe.setDirectElectricalHeatingPower(-1.0));
    assertThrows(IllegalArgumentException.class, () -> pipe.setDirectElectricalHeatingPowerPerMeter(-1.0));
  }
}
