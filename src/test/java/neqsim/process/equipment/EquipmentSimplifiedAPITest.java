package neqsim.process.equipment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for simplified API additions: convenience factory methods,
 * unified outlet property access, and getEquipmentState().
 */
class EquipmentSimplifiedAPITest {

  private static SystemInterface gasFluid;

  @BeforeAll
  static void setup() {
    gasFluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    gasFluid.addComponent("methane", 0.9);
    gasFluid.addComponent("ethane", 0.07);
    gasFluid.addComponent("propane", 0.03);
    gasFluid.setMixingRule("classic");
  }

  // ========== Factory method tests ==========

  @Test
  void testCreateStream() {
    Stream stream = EquipmentFactory.createStream("Feed", gasFluid.clone(),
        10000.0, "kg/hr", 60.0, "bara", 25.0, "C");

    assertEquals("Feed", stream.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(stream);
    process.run();

    assertEquals(25.0, stream.getTemperature("C"), 0.5);
    assertEquals(60.0, stream.getPressure("bara"), 0.1);
    assertTrue(stream.getFlowRate("kg/hr") > 0);
  }

  @Test
  void testCreateCompressor() {
    Stream feed = EquipmentFactory.createStream("CompFeed", gasFluid.clone(),
        5000.0, "kg/hr", 30.0, "bara", 20.0, "C");
    Compressor comp = EquipmentFactory.createCompressor("Comp1", feed, 80.0, 0.85);

    assertEquals("Comp1", comp.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    assertEquals(80.0, comp.getOutletStream().getPressure("bara"), 1.0);
    assertTrue(comp.getPower("kW") > 0);
  }

  @Test
  void testCreateCooler() {
    Stream feed = EquipmentFactory.createStream("CoolFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 80.0, "C");
    Cooler cooler = EquipmentFactory.createCooler("Cooler1", feed, 30.0, "C");

    assertEquals("Cooler1", cooler.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(cooler);
    process.run();

    assertEquals(30.0, cooler.getOutletStream().getTemperature("C"), 1.0);
  }

  @Test
  void testCreateHeater() {
    Stream feed = EquipmentFactory.createStream("HeatFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 20.0, "C");
    Heater heater = EquipmentFactory.createHeater("Heater1", feed, 80.0, "C");

    assertEquals("Heater1", heater.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    assertEquals(80.0, heater.getOutletStream().getTemperature("C"), 1.0);
  }

  @Test
  void testCreateValve() {
    Stream feed = EquipmentFactory.createStream("ValveFeed", gasFluid.clone(),
        5000.0, "kg/hr", 80.0, "bara", 25.0, "C");
    ThrottlingValve valve = EquipmentFactory.createValve("Valve1", feed, 30.0, 50.0);

    assertEquals("Valve1", valve.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.run();

    assertEquals(30.0, valve.getOutletStream().getPressure("bara"), 1.0);
  }

  @Test
  void testCreatePump() {
    SystemInterface liqFluid = new SystemSrkEos(273.15 + 20.0, 5.0);
    liqFluid.addComponent("water", 1.0);
    liqFluid.setMixingRule("classic");

    Stream feed = EquipmentFactory.createStream("PumpFeed", liqFluid,
        2000.0, "kg/hr", 5.0, "bara", 20.0, "C");
    Pump pump = EquipmentFactory.createPump("Pump1", feed, 30.0);

    assertEquals("Pump1", pump.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pump);
    process.run();

    assertEquals(30.0, pump.getOutletStream().getPressure("bara"), 1.0);
  }

  @Test
  void testCreateSeparator() {
    Separator sep = EquipmentFactory.createSeparator("Sep1",
        EquipmentFactory.createStream("SepFeed", gasFluid.clone(),
            5000.0, "kg/hr", 50.0, "bara", 25.0, "C"));
    assertEquals("Sep1", sep.getName());
  }

  @Test
  void testCreateThreePhaseSeparator() {
    ThreePhaseSeparator sep = EquipmentFactory.createThreePhaseSeparator("3PSep",
        EquipmentFactory.createStream("3PSepFeed", gasFluid.clone(),
            5000.0, "kg/hr", 50.0, "bara", 25.0, "C"));
    assertEquals("3PSep", sep.getName());
  }

  @Test
  void testCreateMixer() {
    Stream s1 = EquipmentFactory.createStream("Mix1", gasFluid.clone(),
        3000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Stream s2 = EquipmentFactory.createStream("Mix2", gasFluid.clone(),
        2000.0, "kg/hr", 50.0, "bara", 30.0, "C");
    Mixer mixer = EquipmentFactory.createMixer("Mixer1", s1, s2);

    assertEquals("Mixer1", mixer.getName());

    ProcessSystem process = new ProcessSystem();
    process.add(s1);
    process.add(s2);
    process.add(mixer);
    process.run();

    // Mixer total flow should be sum of inlets
    double outFlow = mixer.getOutletStream().getFlowRate("kg/hr");
    assertTrue(outFlow > 4500.0, "Mixer outlet flow should be ~5000 kg/hr, got " + outFlow);
  }

  // ========== Unified outlet property access tests ==========

  @Test
  void testUnifiedOutletPropertyAccess() {
    Stream feed = EquipmentFactory.createStream("UniFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Compressor comp = EquipmentFactory.createCompressor("UniComp", feed, 80.0, 0.85);
    Cooler cooler = EquipmentFactory.createCooler("UniCool", comp.getOutletStream(), 30.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.add(cooler);
    process.run();

    // Test unified access on Compressor (via getOutletStreams)
    double compTemp = comp.getOutletTemperature("C");
    double compPres = comp.getOutletPressure("bara");
    assertTrue(compTemp > 25.0, "Compressor outlet T should be > inlet");
    assertEquals(80.0, compPres, 1.0);

    // Test unified access on Cooler (via getOutletStreams)
    double coolTemp = cooler.getOutletTemperature("C");
    assertEquals(30.0, coolTemp, 1.0);
  }

  // ========== getEquipmentState tests ==========

  @Test
  void testGetEquipmentStateCompressor() {
    Stream feed = EquipmentFactory.createStream("StateFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Compressor comp = EquipmentFactory.createCompressor("StateComp", feed, 80.0, 0.85);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    Map<String, Map<String, Object>> state = comp.getEquipmentState("C", "bara", "kg/hr");

    assertTrue(state.containsKey("temperature"), "State should have temperature");
    assertTrue(state.containsKey("pressure"), "State should have pressure");
    assertTrue(state.containsKey("flow"), "State should have flow");
    assertTrue(state.containsKey("power"), "State should have power");

    double temp = (Double) state.get("temperature").get("value");
    String tempUnit = (String) state.get("temperature").get("unit");
    assertEquals("C", tempUnit);
    assertTrue(temp > 25.0, "Compressor outlet T should be > 25 C");

    double power = (Double) state.get("power").get("value");
    assertTrue(power > 0, "Compressor power should be positive");
  }

  @Test
  void testGetEquipmentStateHeater() {
    Stream feed = EquipmentFactory.createStream("HStateFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Heater heater = EquipmentFactory.createHeater("HStateHeater", feed, 80.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(heater);
    process.run();

    Map<String, Map<String, Object>> state = heater.getEquipmentState("C", "bara", "kg/hr");

    assertTrue(state.containsKey("duty"), "Heater state should have duty");
    double duty = (Double) state.get("duty").get("value");
    assertTrue(duty != 0, "Heater duty should be non-zero");
  }

  @Test
  void testGetEquipmentStateSeparator() {
    SystemInterface wetGas = new SystemSrkEos(273.15 + 25.0, 50.0);
    wetGas.addComponent("methane", 0.8);
    wetGas.addComponent("ethane", 0.05);
    wetGas.addComponent("n-pentane", 0.05);
    wetGas.addComponent("n-heptane", 0.05);
    wetGas.addComponent("water", 0.05);
    wetGas.setMixingRule("classic");

    Stream feed = EquipmentFactory.createStream("SepStateFeed", wetGas,
        10000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Separator sep = EquipmentFactory.createSeparator("SepState", feed);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.run();

    Map<String, Map<String, Object>> state = sep.getEquipmentState("C", "bara", "kg/hr");

    assertTrue(state.containsKey("gas flow"), "Separator state should have gas flow");
    assertTrue(state.containsKey("liquid flow"), "Separator state should have liquid flow");
    assertTrue(state.containsKey("pressure"), "Separator state should have pressure");
    assertTrue(state.containsKey("temperature"), "Separator state should have temperature");
  }

  @Test
  void testGetEquipmentStateValve() {
    Stream feed = EquipmentFactory.createStream("VStateFeed", gasFluid.clone(),
        5000.0, "kg/hr", 80.0, "bara", 25.0, "C");
    ThrottlingValve valve = EquipmentFactory.createValve("VState", feed, 30.0, 50.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(valve);
    process.run();

    Map<String, Map<String, Object>> state = valve.getEquipmentState("C", "bara", "kg/hr");

    assertTrue(state.containsKey("percent_opening"), "Valve state should have opening");
    double opening = (Double) state.get("percent_opening").get("value");
    assertEquals(50.0, opening, 0.01);
  }

  @Test
  void testGetEquipmentStatePump() {
    SystemInterface liqFluid = new SystemSrkEos(273.15 + 20.0, 5.0);
    liqFluid.addComponent("water", 1.0);
    liqFluid.setMixingRule("classic");

    Stream feed = EquipmentFactory.createStream("PStateFeed", liqFluid,
        2000.0, "kg/hr", 5.0, "bara", 20.0, "C");
    Pump pump = EquipmentFactory.createPump("PState", feed, 30.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(pump);
    process.run();

    Map<String, Map<String, Object>> state = pump.getEquipmentState("C", "bara", "kg/hr");

    assertTrue(state.containsKey("power"), "Pump state should have power");
    assertTrue(state.containsKey("pressure"), "Pump state should have pressure");
  }

  // ========== Default getEquipmentState (fallback) test ==========

  @Test
  void testDefaultGetEquipmentState() {
    // Use equipment that relies on the default implementation of getOutletStreams()
    // from TwoPortEquipment. Heater overrides getEquipmentState, so test via
    // the unified outlet property accessors on a compressor.
    Stream feed = EquipmentFactory.createStream("DefaultFeed", gasFluid.clone(),
        5000.0, "kg/hr", 50.0, "bara", 25.0, "C");
    Compressor comp = EquipmentFactory.createCompressor("DefaultComp", feed, 80.0, 0.85);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    // Verify unified outlet accessors work
    double temp = comp.getOutletTemperature("C");
    double pres = comp.getOutletPressure("bara");
    double flow = comp.getOutletFlowRate("kg/hr");
    assertFalse(Double.isNaN(temp));
    assertEquals(80.0, pres, 1.0);
    assertTrue(flow > 0);
  }
}
