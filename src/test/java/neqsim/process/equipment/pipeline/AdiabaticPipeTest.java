package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

class AdiabaticPipeTest {
  neqsim.thermo.system.SystemInterface testSystem;
  Stream inletStream;

  @BeforeEach
  void setUp() {
    testSystem = new SystemSrkEos(278.15, 100.0);
    testSystem.addComponent("methane", 95.0);
    testSystem.addComponent("ethane", 5.0);
    testSystem.setMixingRule("classic");

    inletStream = new Stream("inlet", testSystem);
    inletStream.setPressure(100.0, "bara");
    inletStream.setTemperature(25.0, "C");
    inletStream.setFlowRate(10.0, "MSm3/day");
    inletStream.run();
  }

  @Test
  void testPressureDropOverPipe() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(1e-5);
    pipe.run();

    double outletP = pipe.getOutletStream().getPressure("bara");
    // Pressure should decrease due to friction
    assertTrue(outletP < 100.0, "Outlet pressure should be less than inlet");
    assertTrue(outletP > 50.0, "Outlet pressure should still be significant for 50 km pipe");
  }

  @Test
  void testPressureDropIncreaseWithLength() {
    AdiabaticPipe shortPipe = new AdiabaticPipe("short", inletStream);
    shortPipe.setLength(10000.0);
    shortPipe.setDiameter(0.5);
    shortPipe.setPipeWallRoughness(1e-5);
    shortPipe.run();

    // Need to re-create inlet stream for second pipe since run modifies system
    neqsim.thermo.system.SystemInterface testSystem2 = new SystemSrkEos(278.15, 100.0);
    testSystem2.addComponent("methane", 95.0);
    testSystem2.addComponent("ethane", 5.0);
    testSystem2.setMixingRule("classic");
    Stream inletStream2 = new Stream("inlet2", testSystem2);
    inletStream2.setPressure(100.0, "bara");
    inletStream2.setTemperature(25.0, "C");
    inletStream2.setFlowRate(10.0, "MSm3/day");
    inletStream2.run();

    AdiabaticPipe longPipe = new AdiabaticPipe("long", inletStream2);
    longPipe.setLength(100000.0);
    longPipe.setDiameter(0.5);
    longPipe.setPipeWallRoughness(1e-5);
    longPipe.run();

    // Longer pipe should have more pressure drop
    assertTrue(longPipe.getPressureDrop() > shortPipe.getPressureDrop(),
        "Longer pipe should have greater pressure drop");
  }

  @Test
  void testSetDiameter() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setDiameter(0.3048);
    assertEquals(0.3048, pipe.getDiameter(), 1e-6);
  }

  @Test
  void testSetLength() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(100000.0);
    assertEquals(100000.0, pipe.getLength(), 1e-6);
  }

  @Test
  void testSetPipeWallRoughness() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setPipeWallRoughness(5e-6);
    assertEquals(5e-6, pipe.getPipeWallRoughness(), 1e-10);
  }

  @Test
  void testSetElevation() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setInletElevation(0.0);
    pipe.setOutletElevation(-300.0);
    assertEquals(0.0, pipe.getInletElevation(), 1e-6);
    assertEquals(-300.0, pipe.getOutletElevation(), 1e-6);
  }

  @Test
  void testMassBalanceOverPipe() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.5);
    pipe.run();

    double inletFlow = inletStream.getThermoSystem().getFlowRate("kg/hr");
    double outletFlow = pipe.getOutletStream().getThermoSystem().getFlowRate("kg/hr");
    assertEquals(inletFlow, outletFlow, inletFlow * 1e-4);
  }

  @Test
  void testFrictionFactorLaminar() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(1e-5);

    // Laminar flow: f = 64/Re
    double re = 1000.0;
    double f = pipe.calcWallFrictionFactor(re);
    assertEquals(64.0 / re, f, 1e-6);
  }

  @Test
  void testFrictionFactorTurbulent() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(1e-5);

    // Turbulent flow: should return non-zero value
    double re = 100000.0;
    double f = pipe.calcWallFrictionFactor(re);
    assertTrue(f > 0.0, "Friction factor should be positive for turbulent flow");
    assertTrue(f < 0.1, "Friction factor should be reasonable for turbulent flow");
  }

  @Test
  void testFrictionFactorZeroReynolds() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(1e-5);

    double f = pipe.calcWallFrictionFactor(0.0);
    assertEquals(0.0, f, 1e-10);
  }

  @Test
  void testOutletStreamNotNull() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.5);
    pipe.run();

    assertNotNull(pipe.getOutletStream());
    assertNotNull(pipe.getOutletStream().getThermoSystem());
  }

  @Test
  void testInProcessSystem() {
    ProcessSystem process = new ProcessSystem();
    process.add(inletStream);

    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.5);
    pipe.setPipeWallRoughness(1e-5);
    process.add(pipe);

    process.run();

    double outletP = pipe.getOutletStream().getPressure("bara");
    assertTrue(outletP > 0, "Outlet pressure should be positive");
    assertTrue(outletP < 100.0, "Outlet pressure should be less than inlet");
  }

  @Test
  void testSetOutletPressureMode() {
    AdiabaticPipe pipe = new AdiabaticPipe("pipeline", inletStream);
    pipe.setLength(50000.0);
    pipe.setDiameter(0.5);
    pipe.setOutletPressure(80.0);
    pipe.run();

    double outletP = pipe.getOutletStream().getPressure("bara");
    assertEquals(80.0, outletP, 0.5);
  }
}
