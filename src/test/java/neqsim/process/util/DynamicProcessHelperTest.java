package neqsim.process.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DynamicProcessHelper}.
 */
public class DynamicProcessHelperTest extends neqsim.NeqSimTest {

  private SystemInterface createTestFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.05);
    fluid.setMixingRule(2);
    return fluid;
  }

  /**
   * Build a simple separator process: feed -> inlet valve -> separator -> gas valve + liquid valve.
   */
  private ProcessSystem buildSeparatorProcess() {
    SystemInterface fluid = createTestFluid();

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(50.0, "bara");

    ThrottlingValve inletValve = new ThrottlingValve("inlet valve", feed);
    inletValve.setOutletPressure(30.0);

    Separator separator = new Separator("HP sep");
    separator.addStream(inletValve.getOutletStream());
    separator.setInternalDiameter(1.5);
    separator.setSeparatorLength(4.0);
    separator.setLiquidLevel(0.5);

    ThrottlingValve gasValve = new ThrottlingValve("gas valve", separator.getGasOutStream());
    gasValve.setOutletPressure(10.0);

    ThrottlingValve liqValve = new ThrottlingValve("liq valve", separator.getLiquidOutStream());
    liqValve.setOutletPressure(5.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(inletValve);
    process.add(separator);
    process.add(gasValve);
    process.add(liqValve);
    process.run();

    return process;
  }

  @Test
  void testInstrumentAndControlCreatesTransmitters() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // Should have PT, LT, TT for the separator
    assertNotNull(helper.getTransmitter("PT-HP sep"), "Pressure transmitter should exist");
    assertNotNull(helper.getTransmitter("LT-HP sep"), "Level transmitter should exist");
    assertNotNull(helper.getTransmitter("TT-HP sep"), "Temperature transmitter should exist");
  }

  @Test
  void testInstrumentAndControlCreatesControllers() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // Should have PC and LC for separator
    assertNotNull(helper.getController("PC-HP sep"), "Pressure controller should exist");
    assertNotNull(helper.getController("LC-HP sep"), "Level controller should exist");
  }

  @Test
  void testControllersWiredToValves() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // Gas valve should have pressure controller
    ThrottlingValve gasValve = (ThrottlingValve) process.getUnit("gas valve");
    assertTrue(gasValve.hasController, "Gas valve should have controller attached");

    // Liquid valve should have level controller
    ThrottlingValve liqValve = (ThrottlingValve) process.getUnit("liq valve");
    assertTrue(liqValve.hasController, "Liquid valve should have controller attached");
  }

  @Test
  void testDynamicModeEnabled() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // All equipment should now be in dynamic mode
    for (neqsim.process.equipment.ProcessEquipmentInterface unit : process.getUnitOperations()) {
      assertTrue(!unit.getCalculateSteadyState(), unit.getName() + " should be in dynamic mode");
    }
  }

  @Test
  void testTimeStepIsSet() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.setDefaultTimeStep(0.5);
    helper.instrumentAndControl();

    assertEquals(0.5, process.getTimeStep(), 1e-9, "Time step should be set");
  }

  @Test
  void testTransientSimulationRuns() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.setDefaultTimeStep(5.0);
    helper.instrumentAndControl();

    // Run a few transient steps - should not throw
    for (int i = 0; i < 10; i++) {
      process.runTransient();
    }

    // Pressure transmitter should read a sensible value
    MeasurementDeviceInterface pt = helper.getTransmitter("PT-HP sep");
    double pressure = pt.getMeasuredValue("bara");
    assertTrue(pressure > 0.0 && pressure < 200.0, "Pressure should be sensible, got: " + pressure);
  }

  @Test
  void testCustomTuning() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.setPressureTuning(1.0, 30.0);
    helper.setLevelTuning(3.0, 150.0);
    helper.instrumentAndControl();

    // Controllers should exist - tuning is internal but we verify wiring works
    assertNotNull(helper.getController("PC-HP sep"));
    assertNotNull(helper.getController("LC-HP sep"));
  }

  @Test
  void testAddFlowController() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // Add a flow controller on the inlet valve
    ThrottlingValve inletValve = (ThrottlingValve) process.getUnit("inlet valve");
    Stream feed = (Stream) process.getUnit("feed");

    ControllerDeviceInterface fc =
        helper.addFlowController("101", inletValve, feed, 5000.0, "kg/hr");

    assertNotNull(fc, "Flow controller should be created");
    assertNotNull(helper.getTransmitter("FT-101"), "Flow transmitter should be created");
    assertNotNull(helper.getController("FC-101"), "Flow controller should be registered");
    assertTrue(inletValve.hasController, "Inlet valve should have flow controller");
  }

  @Test
  void testCompressorInstrumentation() {
    SystemInterface fluid = createTestFluid();

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setPressure(10.0, "bara");

    Compressor comp = new Compressor("1st stage", feed);
    comp.setOutletPressure(30.0);

    Cooler cooler = new Cooler("aftercooler", comp.getOutletStream());
    cooler.setOutTemperature(273.15 + 35.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.add(cooler);
    process.run();

    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    // Compressor should have PT and TT on discharge
    assertNotNull(helper.getTransmitter("PT-1st stage"), "Compressor PT should exist");
    assertNotNull(helper.getTransmitter("TT-1st stage"), "Compressor TT should exist");

    // Cooler should have TT on outlet
    assertNotNull(helper.getTransmitter("TT-aftercooler"), "Cooler TT should exist");
  }

  @Test
  void testGetTransmittersMap() {
    ProcessSystem process = buildSeparatorProcess();
    DynamicProcessHelper helper = new DynamicProcessHelper(process);
    helper.instrumentAndControl();

    assertTrue(helper.getTransmitters().size() >= 3,
        "Should have at least 3 transmitters for separator");
    assertTrue(helper.getControllers().size() >= 2,
        "Should have at least 2 controllers for separator");
  }
}
