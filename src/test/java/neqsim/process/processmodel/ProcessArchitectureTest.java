package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.ProcessElementInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the architecture improvements: getInletStreams/getOutletStreams, controller map,
 * ProcessElementInterface, connection model, and MultiPortEquipment.
 */
public class ProcessArchitectureTest {

  private Stream createTestStream(String name) {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 25.0, 50.0);
    gas.addComponent("methane", 0.9);
    gas.addComponent("ethane", 0.1);
    gas.setMixingRule("classic");
    Stream s = new Stream(name, gas);
    s.setFlowRate(100.0, "kg/hr");
    s.run();
    return s;
  }

  // --- Item 1: getInletStreams / getOutletStreams ---

  @Test
  public void testTwoPortEquipmentStreams() {
    Stream feed = createTestStream("feed");
    ThrottlingValve valve = new ThrottlingValve("valve", feed);
    valve.setOutletPressure(20.0);
    valve.run();

    List<StreamInterface> inlets = valve.getInletStreams();
    List<StreamInterface> outlets = valve.getOutletStreams();

    assertEquals(1, inlets.size(), "TwoPort should have 1 inlet");
    assertEquals(1, outlets.size(), "TwoPort should have 1 outlet");
    assertSame(feed, inlets.get(0));
  }

  @Test
  public void testSeparatorStreams() {
    Stream feed = createTestStream("sepFeed");
    Separator sep = new Separator("sep", feed);
    sep.run();

    List<StreamInterface> inlets = sep.getInletStreams();
    List<StreamInterface> outlets = sep.getOutletStreams();

    assertFalse(inlets.isEmpty(), "Separator should have inlets");
    assertEquals(2, outlets.size(), "Separator should have 2 outlets (gas + liquid)");
  }

  @Test
  public void testThreePhaseSeparatorStreams() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.2);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("3pFeed", fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    ThreePhaseSeparator sep = new ThreePhaseSeparator("3pSep", feed);
    sep.run();

    List<StreamInterface> outlets = sep.getOutletStreams();
    assertEquals(3, outlets.size(), "ThreePhaseSeparator should have 3 outlets");
  }

  @Test
  public void testMixerStreams() {
    Stream s1 = createTestStream("mix1");
    Stream s2 = createTestStream("mix2");
    Mixer mixer = new Mixer("mixer");
    mixer.addStream(s1);
    mixer.addStream(s2);
    mixer.run();

    List<StreamInterface> inlets = mixer.getInletStreams();
    List<StreamInterface> outlets = mixer.getOutletStreams();

    assertEquals(2, inlets.size(), "Mixer should have 2 inlets");
    assertEquals(1, outlets.size(), "Mixer should have 1 outlet");
  }

  @Test
  public void testSplitterStreams() {
    Stream feed = createTestStream("splitFeed");
    Splitter splitter = new Splitter("splitter", feed, 3);
    splitter.setSplitFactors(new double[] {0.5, 0.3, 0.2});
    splitter.run();

    List<StreamInterface> inlets = splitter.getInletStreams();
    List<StreamInterface> outlets = splitter.getOutletStreams();

    assertEquals(1, inlets.size(), "Splitter should have 1 inlet");
    assertEquals(3, outlets.size(), "Splitter should have 3 outlets");
  }

  @Test
  public void testDefaultStreamsMethods() {
    // A feed Stream created from a fluid has no inStream/outStream fields set in TwoPortEquipment
    Stream s = createTestStream("defaultTest");
    assertTrue(s.getInletStreams().isEmpty(), "Feed stream has no TwoPort inlet");
    assertTrue(s.getOutletStreams().isEmpty(), "Feed stream has no TwoPort outlet");
  }

  // --- Item 2: Controller map ---

  @Test
  public void testControllerMapBackwardCompat() {
    Stream feed = createTestStream("ctrlFeed");
    ThrottlingValve valve = new ThrottlingValve("ctrlValve", feed);

    ControllerDeviceBaseClass ctrl = new ControllerDeviceBaseClass("PID-1");

    // Old API still works
    valve.setController(ctrl);
    assertSame(ctrl, valve.getController());
    assertTrue(valve.hasController);
  }

  @Test
  public void testMultipleControllers() {
    Stream feed = createTestStream("multiCtrlFeed");
    ThrottlingValve valve = new ThrottlingValve("multiCtrlValve", feed);

    ControllerDeviceBaseClass ctrl1 = new ControllerDeviceBaseClass("PID-1");
    ControllerDeviceBaseClass ctrl2 = new ControllerDeviceBaseClass("PID-2");

    valve.addController("pressure", ctrl1);
    valve.addController("flow", ctrl2);

    assertSame(ctrl1, valve.getController("pressure"));
    assertSame(ctrl2, valve.getController("flow"));

    Collection<ControllerDeviceInterface> all = valve.getControllers();
    assertEquals(2, all.size());
    assertTrue(all.contains(ctrl1));
    assertTrue(all.contains(ctrl2));
  }

  @Test
  public void testSetControllerAlsoAppearsInMap() {
    Stream feed = createTestStream("mapFeed");
    Cooler cooler = new Cooler("cooler", feed);

    ControllerDeviceBaseClass ctrl = new ControllerDeviceBaseClass("TIC-100");
    cooler.setController(ctrl);

    // The controller added via setController should also appear in getControllers
    Collection<ControllerDeviceInterface> all = cooler.getControllers();
    assertFalse(all.isEmpty());
    assertTrue(all.contains(ctrl));
  }

  // --- Item 3: ProcessElementInterface ---

  @Test
  public void testProcessElementInterfaceHierarchy() {
    Stream feed = createTestStream("elemFeed");
    Separator sep = new Separator("elemSep", feed);
    ControllerDeviceBaseClass ctrl = new ControllerDeviceBaseClass("ctrl");

    assertTrue(feed instanceof ProcessElementInterface, "Stream should be a ProcessElement");
    assertTrue(sep instanceof ProcessElementInterface, "Separator should be a ProcessElement");
    assertTrue(ctrl instanceof ProcessElementInterface, "Controller should be a ProcessElement");
  }

  @Test
  public void testGetAllElements() {
    Stream feed = createTestStream("allElemFeed");
    Separator sep = new Separator("allElemSep", feed);
    ControllerDeviceBaseClass ctrl = new ControllerDeviceBaseClass("allElemCtrl");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(sep);
    process.add(ctrl);

    List<ProcessElementInterface> all = process.getAllElements();
    assertTrue(all.size() >= 3, "Should contain at least equipment + controller");
  }

  // --- Item 5: Connection model ---

  @Test
  public void testExplicitConnections() {
    ProcessSystem process = new ProcessSystem();
    process.connect("HP-Sep", "gasOut", "Compressor-1", "inlet",
        ProcessConnection.ConnectionType.MATERIAL);
    process.connect("HP-Sep", "liquidOut", "LP-Sep", "inlet",
        ProcessConnection.ConnectionType.MATERIAL);
    process.connect("TIC-100", "output", "Cooler-1", "signal",
        ProcessConnection.ConnectionType.SIGNAL);

    List<ProcessConnection> connections = process.getConnections();
    assertEquals(3, connections.size());

    ProcessConnection first = connections.get(0);
    assertEquals("HP-Sep", first.getSourceEquipment());
    assertEquals("gasOut", first.getSourcePort());
    assertEquals("Compressor-1", first.getTargetEquipment());
    assertEquals("inlet", first.getTargetPort());
    assertEquals(ProcessConnection.ConnectionType.MATERIAL, first.getType());
  }

  @Test
  public void testSimpleConnect() {
    ProcessSystem process = new ProcessSystem();
    process.connect("Feed", "Separator");

    List<ProcessConnection> connections = process.getConnections();
    assertEquals(1, connections.size());
    assertEquals("Feed", connections.get(0).getSourceEquipment());
    assertEquals("Separator", connections.get(0).getTargetEquipment());
    assertEquals(ProcessConnection.ConnectionType.MATERIAL, connections.get(0).getType());
  }

  @Test
  public void testProcessConnectionEquality() {
    ProcessConnection c1 =
        new ProcessConnection("A", "out", "B", "in", ProcessConnection.ConnectionType.MATERIAL);
    ProcessConnection c2 =
        new ProcessConnection("A", "out", "B", "in", ProcessConnection.ConnectionType.MATERIAL);
    ProcessConnection c3 =
        new ProcessConnection("A", "out", "C", "in", ProcessConnection.ConnectionType.MATERIAL);

    assertEquals(c1, c2);
    assertNotEquals(c1, c3);
    assertEquals(c1.hashCode(), c2.hashCode());
  }
}
