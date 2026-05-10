package neqsim.process.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the ProcessAutomation facade and the convenience methods on ProcessSystem.
 */
class ProcessAutomationTest {

  private ProcessSystem process;
  private ProcessAutomation automation;

  @BeforeEach
  void setUp() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler cooler = new Cooler("Cooler", compressor.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");

    ThrottlingValve valve = new ThrottlingValve("Valve", cooler.getOutletStream());
    valve.setOutletPressure(50.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.add(cooler);
    process.add(valve);
    process.run();

    automation = new ProcessAutomation(process);
  }

  @Test
  void testGetUnitList() {
    List<String> units = automation.getUnitList();
    assertNotNull(units);
    assertEquals(5, units.size());
    assertTrue(units.contains("feed"));
    assertTrue(units.contains("HP Sep"));
    assertTrue(units.contains("Compressor"));
    assertTrue(units.contains("Cooler"));
    assertTrue(units.contains("Valve"));
  }

  @Test
  void testGetUnitListViaProcessSystem() {
    List<String> units = process.getUnitNames();
    assertEquals(5, units.size());
    assertTrue(units.contains("HP Sep"));
  }

  @Test
  void testGetVariableListForSeparator() {
    List<SimulationVariable> vars = automation.getVariableList("HP Sep");
    assertFalse(vars.isEmpty());

    // Should have gas and liquid outlet stream variables
    boolean hasGasTemp = false;
    boolean hasLiquidFlow = false;
    for (SimulationVariable v : vars) {
      if (v.getAddress().equals("HP Sep.gasOutStream.temperature")) {
        hasGasTemp = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
      if (v.getAddress().equals("HP Sep.liquidOutStream.flowRate")) {
        hasLiquidFlow = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasGasTemp, "Separator should expose gasOutStream.temperature");
    assertTrue(hasLiquidFlow, "Separator should expose liquidOutStream.flowRate");
  }

  @Test
  void testGetVariableListForCompressor() {
    List<SimulationVariable> vars = automation.getVariableList("Compressor");
    assertFalse(vars.isEmpty());

    boolean hasOutletPressure = false;
    boolean hasPower = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("outletPressure")) {
        hasOutletPressure = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("power")) {
        hasPower = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasOutletPressure, "Compressor should expose outletPressure as INPUT");
    assertTrue(hasPower, "Compressor should expose power as OUTPUT");
  }

  @Test
  void testWaterHammerPipeVariables() {
    SystemInterface water = new SystemSrkEos(298.15, 10.0);
    water.addComponent("water", 1.0);
    water.setMixingRule("classic");

    Stream waterFeed = new Stream("water feed", water);
    waterFeed.setFlowRate(100.0, "kg/hr");
    WaterHammerPipe hammer = new WaterHammerPipe("Hammer Line", waterFeed);
    hammer.setLength(500.0);
    hammer.setDiameter(0.15);
    hammer.setNumberOfNodes(30);

    ProcessSystem waterProcess = new ProcessSystem();
    waterProcess.add(waterFeed);
    waterProcess.add(hammer);
    waterProcess.run();
    ProcessAutomation waterAutomation = waterProcess.getAutomation();

    List<SimulationVariable> variables = waterAutomation.getVariableList("Hammer Line");
    boolean hasValveOpening = false;
    boolean hasMaxPressure = false;
    for (SimulationVariable variable : variables) {
      if ("valveOpeningPercent".equals(variable.getName())) {
        hasValveOpening = true;
        assertEquals(VariableType.INPUT, variable.getType());
      }
      if ("maxPressure".equals(variable.getName())) {
        hasMaxPressure = true;
        assertEquals(VariableType.OUTPUT, variable.getType());
      }
    }
    assertTrue(hasValveOpening, "WaterHammerPipe should expose valveOpeningPercent");
    assertTrue(hasMaxPressure, "WaterHammerPipe should expose maxPressure");

    waterAutomation.setVariableValue("Hammer Line.valveOpeningPercent", 40.0, "%");
    assertEquals(40.0, waterAutomation.getVariableValue("Hammer Line.valveOpeningPercent", "%"),
        1.0e-12);
  }

  @Test
  void testGetVariableListFilteredByType() {
    List<SimulationVariable> inputs = automation.getVariableList("Compressor", VariableType.INPUT);
    for (SimulationVariable v : inputs) {
      assertEquals(VariableType.INPUT, v.getType());
    }

    List<SimulationVariable> outputs =
        automation.getVariableList("Compressor", VariableType.OUTPUT);
    for (SimulationVariable v : outputs) {
      assertEquals(VariableType.OUTPUT, v.getType());
    }
  }

  @Test
  void testGetVariableListForStream() {
    List<SimulationVariable> vars = automation.getVariableList("feed");
    boolean hasTempInput = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("temperature") && v.getType() == VariableType.INPUT) {
        hasTempInput = true;
      }
    }
    assertTrue(hasTempInput, "Stream should have temperature as INPUT");
  }

  @Test
  void testGetVariableValueEquipmentProperty() {
    double power = automation.getVariableValue("Compressor.power", "kW");
    assertTrue(power > 0.0, "Compressor power should be positive after run()");
  }

  @Test
  void testGetVariableValueStreamProperty() {
    double gasTemp = automation.getVariableValue("HP Sep.gasOutStream.temperature", "C");
    assertTrue(gasTemp > -100 && gasTemp < 200,
        "Gas outlet temperature should be in reasonable range");
  }

  @Test
  void testGetVariableValueWithDefaultUnit() {
    double pressure = automation.getVariableValue("HP Sep.pressure", null);
    assertTrue(pressure > 0.0, "Pressure should be positive");
  }

  @Test
  void testGetVariableValueViaProcessSystem() {
    double valveOutP = process.getVariableValue("Valve.outletPressure", "bara");
    assertEquals(50.0, valveOutP, 1.0);
  }

  @Test
  void testSetVariableValue() {
    automation.setVariableValue("Compressor.outletPressure", 150.0, "bara");
    process.run();
    double newPressure = automation.getVariableValue("Compressor.outletPressure", null);
    assertEquals(150.0, newPressure, 0.1);
  }

  @Test
  void testSetVariableValueOnStream() {
    automation.setVariableValue("feed.flowRate", 60000.0, "kg/hr");
    process.run();
    double flow = automation.getVariableValue("feed.flowRate", "kg/hr");
    assertEquals(60000.0, flow, 100.0);
  }

  @Test
  void testSetVariableValueViaProcessSystem() {
    process.setVariableValue("Cooler.outletTemperature", 25.0, "C");
    process.run();
    double temp = process.getVariableValue("Cooler.outletStream.temperature", "C");
    assertEquals(25.0, temp, 1.0);
  }

  @Test
  void testUnknownUnitThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      automation.getVariableList("NonExistent");
    });
  }

  @Test
  void testUnknownPropertyThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      automation.getVariableValue("HP Sep.foobar", "C");
    });
  }

  @Test
  void testInvalidAddressFormatThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      automation.getVariableValue("", "C");
    });
  }

  @Test
  void testSetReadOnlyPropertyThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      automation.setVariableValue("Compressor.power", 100.0, "kW");
    });
  }

  @Test
  void testSimulationVariableToString() {
    SimulationVariable v =
        new SimulationVariable("unit.temp", "temp", VariableType.OUTPUT, "K", "Temperature");
    String s = v.toString();
    assertTrue(s.contains("unit.temp"));
    assertTrue(s.contains("OUTPUT"));
    assertTrue(s.contains("K"));
  }

  @Test
  void testGetAutomation() {
    ProcessAutomation auto = process.getAutomation();
    assertNotNull(auto);
    assertEquals(5, auto.getUnitList().size());
  }

  @Test
  void testHeaterVariables() {
    // Build a simple process with a heater
    SystemInterface fluid = new SystemSrkEos(273.15 + 20.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream s = new Stream("gas", fluid);
    s.setFlowRate(10000.0, "kg/hr");
    Heater h = new Heater("Heater", s);
    h.setOutletTemperature(60.0, "C");

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(h);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    List<SimulationVariable> vars = auto.getVariableList("Heater");

    boolean hasDuty = false;
    boolean hasOutTemp = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("duty")) {
        hasDuty = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
      if (v.getName().equals("outletTemperature")) {
        hasOutTemp = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
    }
    assertTrue(hasDuty, "Heater should expose duty");
    assertTrue(hasOutTemp, "Heater should expose outletTemperature");

    double duty = auto.getVariableValue("Heater.duty", "W");
    assertTrue(duty != 0.0, "Heater duty should be non-zero");
  }

  @Test
  void testValveVariables() {
    List<SimulationVariable> vars = automation.getVariableList("Valve");
    boolean hasCv = false;
    boolean hasOutletPressure = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("Cv")) {
        hasCv = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("outletPressure")) {
        hasOutletPressure = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
    }
    assertTrue(hasCv, "Valve should expose Cv as INPUT");
    assertTrue(hasOutletPressure, "Valve should expose outletPressure as INPUT");
  }

  @Test
  void testNullProcessSystemThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      new ProcessAutomation((ProcessSystem) null);
    });
  }

  // ======================== ProcessModel tests ========================

  private ProcessModel buildTwoAreaModel() {
    // Area 1: Separation
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid1.addComponent("methane", 0.80);
    fluid1.addComponent("ethane", 0.12);
    fluid1.addComponent("propane", 0.05);
    fluid1.addComponent("n-butane", 0.03);
    fluid1.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid1);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed);
    separation.add(separator);
    separation.run();

    // Area 2: Compression
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    Cooler aftercooler = new Cooler("Aftercooler", compressor.getOutletStream());
    aftercooler.setOutletTemperature(30.0, "C");

    ProcessSystem compression = new ProcessSystem();
    compression.add(compressor);
    compression.add(aftercooler);
    compression.run();

    ProcessModel model = new ProcessModel();
    model.add("Separation", separation);
    model.add("Compression", compression);
    return model;
  }

  @Test
  void testProcessModelConstructor() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);
    assertTrue(auto.isMultiArea());
  }

  @Test
  void testProcessModelGetAreaList() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);
    List<String> areas = auto.getAreaList();
    assertEquals(2, areas.size());
    assertTrue(areas.contains("Separation"));
    assertTrue(areas.contains("Compression"));
  }

  @Test
  void testProcessModelGetUnitList() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);
    List<String> units = auto.getUnitList();

    // Should have area-qualified names
    assertTrue(units.contains("Separation::Feed"));
    assertTrue(units.contains("Separation::HP Sep"));
    assertTrue(units.contains("Compression::Export Compressor"));
    assertTrue(units.contains("Compression::Aftercooler"));
    assertEquals(4, units.size());
  }

  @Test
  void testProcessModelGetUnitListByArea() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    List<String> sepUnits = auto.getUnitList("Separation");
    assertEquals(2, sepUnits.size());
    assertTrue(sepUnits.contains("Feed"));
    assertTrue(sepUnits.contains("HP Sep"));

    List<String> compUnits = auto.getUnitList("Compression");
    assertEquals(2, compUnits.size());
    assertTrue(compUnits.contains("Export Compressor"));
    assertTrue(compUnits.contains("Aftercooler"));
  }

  @Test
  void testProcessModelGetVariableListAreaQualified() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    List<SimulationVariable> vars = auto.getVariableList("Compression::Export Compressor");
    assertFalse(vars.isEmpty());

    boolean hasPower = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("power")) {
        hasPower = true;
        assertEquals(VariableType.OUTPUT, v.getType());
        // Address should include area prefix
        assertTrue(v.getAddress().startsWith("Compression::"));
      }
    }
    assertTrue(hasPower, "Compressor should expose power");
  }

  @Test
  void testProcessModelGetVariableListUnqualified() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    // Should find "Feed" by searching all areas
    List<SimulationVariable> vars = auto.getVariableList("Feed");
    assertFalse(vars.isEmpty());
  }

  @Test
  void testProcessModelGetVariableValue() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    double pressure = auto.getVariableValue("Separation::HP Sep.pressure", "bara");
    assertTrue(pressure > 0.0, "Pressure should be positive");
  }

  @Test
  void testProcessModelGetVariableValueStreamPort() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    double gasTemp = auto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
    assertTrue(gasTemp > -100 && gasTemp < 200,
        "Gas outlet temperature should be in reasonable range");
  }

  @Test
  void testProcessModelSetVariableValue() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    auto.setVariableValue("Compression::Export Compressor.outletPressure", 150.0, "bara");
    model.get("Compression").run();

    double newPressure =
        auto.getVariableValue("Compression::Export Compressor.outletPressure", null);
    assertEquals(150.0, newPressure, 0.1);
  }

  @Test
  void testProcessModelDelegateGetUnitNames() {
    ProcessModel model = buildTwoAreaModel();
    List<String> units = model.getUnitNames();
    assertTrue(units.contains("Separation::Feed"));
    assertTrue(units.contains("Compression::Export Compressor"));
  }

  @Test
  void testProcessModelDelegateGetAreaNames() {
    ProcessModel model = buildTwoAreaModel();
    List<String> areas = model.getAreaNames();
    assertEquals(2, areas.size());
    assertTrue(areas.contains("Separation"));
    assertTrue(areas.contains("Compression"));
  }

  @Test
  void testProcessModelDelegateGetUnitNamesByArea() {
    ProcessModel model = buildTwoAreaModel();
    List<String> units = model.getUnitNames("Separation");
    assertTrue(units.contains("Feed"));
    assertTrue(units.contains("HP Sep"));
  }

  @Test
  void testProcessModelDelegateGetVariableValue() {
    ProcessModel model = buildTwoAreaModel();
    double power = model.getVariableValue("Compression::Export Compressor.power", "kW");
    assertTrue(power > 0.0, "Power should be positive");
  }

  @Test
  void testProcessModelDelegateSetVariableValue() {
    ProcessModel model = buildTwoAreaModel();
    model.setVariableValue("Separation::Feed.flowRate", 60000.0, "kg/hr");
    model.get("Separation").run();

    double flow = model.getVariableValue("Separation::Feed.flowRate", "kg/hr");
    assertEquals(60000.0, flow, 100.0);
  }

  @Test
  void testProcessModelUnknownAreaThrows() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    assertThrows(IllegalArgumentException.class, () -> {
      auto.getUnitList("NonExistentArea");
    });
  }

  @Test
  void testProcessModelUnknownUnitInAreaThrows() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = new ProcessAutomation(model);

    assertThrows(IllegalArgumentException.class, () -> {
      auto.getVariableValue("Separation::NonExistent.pressure", "bara");
    });
  }

  @Test
  void testProcessModelGetAutomation() {
    ProcessModel model = buildTwoAreaModel();
    ProcessAutomation auto = model.getAutomation();
    assertNotNull(auto);
    assertTrue(auto.isMultiArea());
  }

  @Test
  void testProcessModelNullThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      new ProcessAutomation((ProcessModel) null);
    });
  }

  @Test
  void testSingleSystemIsNotMultiArea() {
    assertFalse(automation.isMultiArea());
  }

  @Test
  void testSingleSystemGetAreaListThrows() {
    assertThrows(IllegalStateException.class, () -> {
      automation.getAreaList();
    });
  }

  @Test
  void testSingleSystemGetUnitListByAreaThrows() {
    assertThrows(IllegalStateException.class, () -> {
      automation.getUnitList("SomeArea");
    });
  }

  // ======================== Extended equipment tests ========================

  @Test
  void testGetEquipmentType() {
    String type = automation.getEquipmentType("Compressor");
    assertEquals("Compressor", type);

    String sepType = automation.getEquipmentType("HP Sep");
    assertEquals("Separator", sepType);

    String streamType = automation.getEquipmentType("feed");
    assertEquals("Stream", streamType);
  }

  @Test
  void testGetEquipmentTypeUnknownThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      automation.getEquipmentType("UnknownUnit");
    });
  }

  @Test
  void testExpanderVariablesAndValues() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 100.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream s = new Stream("gas", fluid);
    s.setFlowRate(20000.0, "kg/hr");

    Expander expander = new Expander("JT Expander", s);
    expander.setOutletPressure(40.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(expander);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Expander", auto.getEquipmentType("JT Expander"));

    List<SimulationVariable> vars = auto.getVariableList("JT Expander");
    assertFalse(vars.isEmpty());

    boolean hasOutletPressure = false;
    boolean hasPower = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("outletPressure")) {
        hasOutletPressure = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("power")) {
        hasPower = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasOutletPressure, "Expander should expose outletPressure as INPUT");
    assertTrue(hasPower, "Expander should expose power as OUTPUT");

    double power = auto.getVariableValue("JT Expander.power", "kW");
    assertTrue(power != 0.0, "Expander power should be non-zero");
  }

  @Test
  void testPipelineVariablesAndValues() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream s = new Stream("gas", fluid);
    s.setFlowRate(10000.0, "kg/hr");

    AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", s);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.3);
    pipe.setPipeWallRoughness(1.0e-5);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(pipe);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("AdiabaticPipe", auto.getEquipmentType("Pipeline"));

    List<SimulationVariable> vars = auto.getVariableList("Pipeline");
    assertFalse(vars.isEmpty());

    boolean hasLength = false;
    boolean hasDiameter = false;
    boolean hasPressureDrop = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("length")) {
        hasLength = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("diameter")) {
        hasDiameter = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("pressureDrop")) {
        hasPressureDrop = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasLength, "Pipeline should expose length as INPUT");
    assertTrue(hasDiameter, "Pipeline should expose diameter as INPUT");
    assertTrue(hasPressureDrop, "Pipeline should expose pressureDrop as OUTPUT");

    double length = auto.getVariableValue("Pipeline.length", null);
    assertEquals(5000.0, length, 0.1);

    double diameter = auto.getVariableValue("Pipeline.diameter", null);
    assertEquals(0.3, diameter, 0.001);
  }

  @Test
  void testPipelineSetProperties() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 80.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream s = new Stream("gas", fluid);
    s.setFlowRate(10000.0, "kg/hr");

    AdiabaticPipe pipe = new AdiabaticPipe("Pipeline", s);
    pipe.setLength(5000.0);
    pipe.setDiameter(0.3);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(pipe);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);

    // Set length and verify
    auto.setVariableValue("Pipeline.length", 8000.0, null);
    double newLength = auto.getVariableValue("Pipeline.length", null);
    assertEquals(8000.0, newLength, 0.1);

    // Set diameter and verify
    auto.setVariableValue("Pipeline.diameter", 0.4, null);
    double newDiameter = auto.getVariableValue("Pipeline.diameter", null);
    assertEquals(0.4, newDiameter, 0.001);
  }

  @Test
  void testTankVariables() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 5.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("n-heptane", 0.50);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream s = new Stream("feed", fluid);
    s.setFlowRate(10000.0, "kg/hr");

    Tank tank = new Tank("Storage Tank", s);
    tank.setVolume(50.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(tank);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Tank", auto.getEquipmentType("Storage Tank"));

    List<SimulationVariable> vars = auto.getVariableList("Storage Tank");
    assertFalse(vars.isEmpty());

    boolean hasVolume = false;
    boolean hasLiquidLevel = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("volume")) {
        hasVolume = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("liquidLevel")) {
        hasLiquidLevel = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasVolume, "Tank should expose volume as INPUT");
    assertTrue(hasLiquidLevel, "Tank should expose liquidLevel as OUTPUT");
  }

  @Test
  void testTankSetVolume() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 5.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("n-heptane", 0.50);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream s = new Stream("feed", fluid);
    s.setFlowRate(10000.0, "kg/hr");

    Tank tank = new Tank("Storage Tank", s);
    tank.setVolume(50.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(tank);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    auto.setVariableValue("Storage Tank.volume", 100.0, null);
    double vol = auto.getVariableValue("Storage Tank.volume", null);
    assertEquals(100.0, vol, 0.1);
  }

  @Test
  void testRecycleVariables() {
    // Recycle only populates its outlet stream when part of a real recycle loop.
    // We test variable discovery by verifying the variable list is built correctly.
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid1.addComponent("methane", 0.90);
    fluid1.addComponent("ethane", 0.10);
    fluid1.setMixingRule("classic");

    Stream feed = new Stream("Feed", fluid1);
    feed.setFlowRate(50000.0, "kg/hr");
    feed.run();

    Recycle recycle = new Recycle("Recycle");
    recycle.addStream(feed);
    recycle.setOutletStream(feed);

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(recycle);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Recycle", auto.getEquipmentType("Recycle"));

    List<SimulationVariable> vars = auto.getVariableList("Recycle");
    assertFalse(vars.isEmpty());

    boolean hasErrorTemp = false;
    boolean hasErrorFlow = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("errorTemperature")) {
        hasErrorTemp = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
      if (v.getName().equals("errorFlow")) {
        hasErrorFlow = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasErrorTemp, "Recycle should expose errorTemperature as OUTPUT");
    assertTrue(hasErrorFlow, "Recycle should expose errorFlow as OUTPUT");
  }

  @Test
  void testValvePercentValveOpening() {
    // Use existing process from setUp
    automation.setVariableValue("Valve.percentValveOpening", 75.0, null);
    double opening = automation.getVariableValue("Valve.percentValveOpening", null);
    assertEquals(75.0, opening, 0.1);
  }

  @Test
  void testCompressorSpeed() {
    automation.setVariableValue("Compressor.speed", 10000.0, null);
    double speed = automation.getVariableValue("Compressor.speed", null);
    assertEquals(10000.0, speed, 0.1);
  }

  @Test
  void testCompressorIsentropicEfficiency() {
    automation.setVariableValue("Compressor.isentropicEfficiency", 0.85, null);
    double eff = automation.getVariableValue("Compressor.isentropicEfficiency", null);
    assertEquals(0.85, eff, 0.01);
  }

  @Test
  void testHeatExchangerVariables() {
    SystemInterface hotFluid = new SystemSrkEos(273.15 + 100.0, 50.0);
    hotFluid.addComponent("methane", 1.0);
    hotFluid.setMixingRule("classic");

    SystemInterface coldFluid = new SystemSrkEos(273.15 + 20.0, 50.0);
    coldFluid.addComponent("methane", 1.0);
    coldFluid.setMixingRule("classic");

    Stream hot = new Stream("hot", hotFluid);
    hot.setFlowRate(10000.0, "kg/hr");

    Stream cold = new Stream("cold", coldFluid);
    cold.setFlowRate(8000.0, "kg/hr");

    HeatExchanger hx = new HeatExchanger("HX", hot);
    hx.setFeedStream(1, cold);
    hx.setUAvalue(5000.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(hot);
    ps.add(cold);
    ps.add(hx);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("HeatExchanger", auto.getEquipmentType("HX"));

    List<SimulationVariable> vars = auto.getVariableList("HX");
    boolean hasUAvalue = false;
    boolean hasDuty = false;
    boolean hasThermalEffectiveness = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("UAvalue")) {
        hasUAvalue = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("duty")) {
        hasDuty = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
      if (v.getName().equals("thermalEffectiveness")) {
        hasThermalEffectiveness = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasUAvalue, "HeatExchanger should expose UAvalue as INPUT");
    assertTrue(hasDuty, "HeatExchanger should expose duty as OUTPUT");
    assertTrue(hasThermalEffectiveness,
        "HeatExchanger should expose thermalEffectiveness as OUTPUT");
  }

  @Test
  void testThreePhaseSeparatorVariables() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 30.0);
    fluid.addComponent("methane", 0.60);
    fluid.addComponent("n-heptane", 0.30);
    fluid.addComponent("water", 0.10);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream s = new Stream("Feed3P", fluid);
    s.setFlowRate(20000.0, "kg/hr");

    ThreePhaseSeparator tps = new ThreePhaseSeparator("3P Sep", s);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(tps);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("ThreePhaseSeparator", auto.getEquipmentType("3P Sep"));

    List<SimulationVariable> vars = auto.getVariableList("3P Sep");
    assertFalse(vars.isEmpty());

    boolean hasGasOut = false;
    boolean hasOilOut = false;
    boolean hasWaterOut = false;
    for (SimulationVariable v : vars) {
      if (v.getAddress().contains("gasOutStream.temperature")) {
        hasGasOut = true;
      }
      if (v.getAddress().contains("oilOutStream.temperature")) {
        hasOilOut = true;
      }
      if (v.getAddress().contains("waterOutStream.temperature")) {
        hasWaterOut = true;
      }
    }
    assertTrue(hasGasOut, "3P separator should expose gasOutStream");
    assertTrue(hasOilOut, "3P separator should expose oilOutStream");
    assertTrue(hasWaterOut, "3P separator should expose waterOutStream");
  }

  @Test
  void testMixerVariables() {
    SystemInterface fluid1 = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid1.addComponent("methane", 1.0);
    fluid1.setMixingRule("classic");

    SystemInterface fluid2 = new SystemSrkEos(273.15 + 60.0, 50.0);
    fluid2.addComponent("methane", 1.0);
    fluid2.setMixingRule("classic");

    Stream s1 = new Stream("in1", fluid1);
    s1.setFlowRate(5000.0, "kg/hr");
    Stream s2 = new Stream("in2", fluid2);
    s2.setFlowRate(3000.0, "kg/hr");

    Mixer mixer = new Mixer("Mixer");
    mixer.addStream(s1);
    mixer.addStream(s2);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s1);
    ps.add(s2);
    ps.add(mixer);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Mixer", auto.getEquipmentType("Mixer"));

    List<SimulationVariable> vars = auto.getVariableList("Mixer");
    assertFalse(vars.isEmpty());

    boolean hasOutletTemp = false;
    for (SimulationVariable v : vars) {
      if (v.getAddress().contains("outletStream.temperature")) {
        hasOutletTemp = true;
      }
    }
    assertTrue(hasOutletTemp, "Mixer should expose outletStream properties");
  }

  @Test
  void testSplitterVariables() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");

    Stream s = new Stream("feed", fluid);
    s.setFlowRate(10000.0, "kg/hr");

    Splitter splitter = new Splitter("Splitter", s, 2);
    splitter.setSplitFactors(new double[] {0.6, 0.4});

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(splitter);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Splitter", auto.getEquipmentType("Splitter"));

    List<SimulationVariable> vars = auto.getVariableList("Splitter");
    assertFalse(vars.isEmpty());

    boolean hasSplit0 = false;
    boolean hasSplit1 = false;
    for (SimulationVariable v : vars) {
      if (v.getAddress().contains("splitStream_0.")) {
        hasSplit0 = true;
      }
      if (v.getAddress().contains("splitStream_1.")) {
        hasSplit1 = true;
      }
    }
    assertTrue(hasSplit0, "Splitter should expose splitStream_0");
    assertTrue(hasSplit1, "Splitter should expose splitStream_1");
  }

  @Test
  void testPumpVariablesAndSet() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 5.0);
    fluid.addComponent("water", 1.0);
    fluid.setMixingRule("classic");

    Stream s = new Stream("water", fluid);
    s.setFlowRate(5000.0, "kg/hr");

    Pump pump = new Pump("Feed Pump", s);
    pump.setOutletPressure(30.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(s);
    ps.add(pump);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    assertEquals("Pump", auto.getEquipmentType("Feed Pump"));

    List<SimulationVariable> vars = auto.getVariableList("Feed Pump");
    boolean hasOutletPressure = false;
    boolean hasPower = false;
    for (SimulationVariable v : vars) {
      if (v.getName().equals("outletPressure")) {
        hasOutletPressure = true;
        assertEquals(VariableType.INPUT, v.getType());
      }
      if (v.getName().equals("power")) {
        hasPower = true;
        assertEquals(VariableType.OUTPUT, v.getType());
      }
    }
    assertTrue(hasOutletPressure, "Pump should expose outletPressure as INPUT");
    assertTrue(hasPower, "Pump should expose power as OUTPUT");

    // Set outlet pressure
    auto.setVariableValue("Feed Pump.outletPressure", 40.0, "bara");
    ps.run();
    double newP = auto.getVariableValue("Feed Pump.outletPressure", null);
    assertEquals(40.0, newP, 0.5);
  }

  @Test
  void testGenericTwoPortFallback() {
    // The Cooler from setUp should have outlet variables via generic TwoPortEquipment fallback
    // even if the Cooler specific handler is used. We just verify it has outlet stream props.
    List<SimulationVariable> vars = automation.getVariableList("Cooler");
    boolean hasOutletStreamTemp = false;
    for (SimulationVariable v : vars) {
      if (v.getAddress().contains("outletStream.temperature")) {
        hasOutletStreamTemp = true;
      }
    }
    assertTrue(hasOutletStreamTemp, "Cooler should expose outletStream.temperature");
  }

  @Test
  void testLargeProcessAllUnitsDiscoverable() {
    // Build a larger process with many different equipment types
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.03);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(50000.0, "kg/hr");

    Separator hp = new Separator("HP Sep", feed);

    Compressor comp = new Compressor("Comp", hp.getGasOutStream());
    comp.setOutletPressure(120.0);

    Cooler cooler = new Cooler("Aftercooler", comp.getOutletStream());
    cooler.setOutletTemperature(30.0, "C");

    ThrottlingValve valve = new ThrottlingValve("JT Valve", cooler.getOutletStream());
    valve.setOutletPressure(30.0);

    Separator lp = new Separator("LP Sep", valve.getOutletStream());

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(hp);
    ps.add(comp);
    ps.add(cooler);
    ps.add(valve);
    ps.add(lp);
    ps.run();

    ProcessAutomation auto = new ProcessAutomation(ps);
    List<String> units = auto.getUnitList();
    assertEquals(6, units.size());

    // Every unit should have at least one variable
    for (String name : units) {
      List<SimulationVariable> vars = auto.getVariableList(name);
      assertFalse(vars.isEmpty(), name + " should expose at least one variable");
    }

    // Read a value from every unit to confirm full coverage
    for (String name : units) {
      List<SimulationVariable> vars = auto.getVariableList(name);
      // Try to read at least one value per unit; skip if it happens to NPE
      // due to un-populated internal state (e.g., separator without liquid outlet)
      boolean readOne = false;
      for (SimulationVariable v : vars) {
        try {
          double val = auto.getVariableValue(v.getAddress(), null);
          readOne = true;
          break;
        } catch (Exception e) {
          // try next variable
        }
      }
      assertTrue(readOne, name + " should have at least one readable variable");
    }
  }
}
