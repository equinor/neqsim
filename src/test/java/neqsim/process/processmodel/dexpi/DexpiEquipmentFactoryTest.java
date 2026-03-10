package neqsim.process.processmodel.dexpi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;
import neqsim.process.equipment.EquipmentEnum;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link DexpiEquipmentFactory}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiEquipmentFactoryTest extends NeqSimTest {

  /**
   * Creates a test fluid and stream.
   *
   * @return a simple gas feed stream
   */
  private Stream createTestStream() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule(2);
    fluid.init(0);
    Stream stream = new Stream("test-feed", fluid);
    stream.setPressure(50.0, "bara");
    stream.setTemperature(30.0, "C");
    stream.setFlowRate(1.0, "MSm3/day");
    return stream;
  }

  /**
   * Tests creation of a Separator from a DexpiProcessUnit.
   */
  @Test
  public void testCreateSeparator() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("HP-Sep", "Separator", EquipmentEnum.Separator, null, null);
    unit.setSizingAttribute(DexpiMetadata.INSIDE_DIAMETER, "2.5");
    unit.setSizingAttribute(DexpiMetadata.TANGENT_TO_TANGENT_LENGTH, "8.0");
    unit.setSizingAttribute(DexpiMetadata.ORIENTATION, "Vertical");

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Separator);
    assertEquals("HP-Sep", result.getName());
  }

  /**
   * Tests creation of a ThreePhaseSeparator.
   */
  @Test
  public void testCreateThreePhaseSeparator() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit = new DexpiProcessUnit("3P-Sep", "ThreePhaseSeparator",
        EquipmentEnum.ThreePhaseSeparator, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof ThreePhaseSeparator);
  }

  /**
   * Tests creation of a Compressor.
   */
  @Test
  public void testCreateCompressor() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit = new DexpiProcessUnit("Comp-1", "CentrifugalCompressor",
        EquipmentEnum.Compressor, null, null);
    unit.setSizingAttribute(DexpiMetadata.DESIGN_PRESSURE, "100.0");

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Compressor);
  }

  /**
   * Tests creation of a Pump.
   */
  @Test
  public void testCreatePump() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Pump-1", "CentrifugalPump", EquipmentEnum.Pump, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Pump);
  }

  /**
   * Tests creation of a HeatExchanger.
   */
  @Test
  public void testCreateHeatExchanger() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit = new DexpiProcessUnit("HX-1", "ShellAndTubeHeatExchanger",
        EquipmentEnum.HeatExchanger, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof HeatExchanger);
  }

  /**
   * Tests creation of a Heater.
   */
  @Test
  public void testCreateHeater() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Heater-1", "FiredHeater", EquipmentEnum.Heater, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Heater);
  }

  /**
   * Tests creation of a Cooler.
   */
  @Test
  public void testCreateCooler() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Cooler-1", "Cooler", EquipmentEnum.Cooler, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Cooler);
  }

  /**
   * Tests creation of a ThrottlingValve with Cv.
   */
  @Test
  public void testCreateValve() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("CV-101", "ControlValve", EquipmentEnum.ThrottlingValve, null, null);
    unit.setSizingAttribute(DexpiMetadata.VALVE_CV, "35.0");

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof ThrottlingValve);
  }

  /**
   * Tests creation of a Mixer (no inlet stream in constructor).
   */
  @Test
  public void testCreateMixer() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Mixer-1", "Agitator", EquipmentEnum.Mixer, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Mixer);
  }

  /**
   * Tests creation of a Splitter.
   */
  @Test
  public void testCreateSplitter() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Tee-1", "PipeTee", EquipmentEnum.Splitter, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Splitter);
  }

  /**
   * Tests that unsupported equipment types produce a pass-through Stream.
   */
  @Test
  public void testUnsupportedEquipmentCreatesPassThrough() {
    Stream feed = createTestStream();
    DexpiProcessUnit unit =
        new DexpiProcessUnit("Reactor-1", "StirredTankReactor", EquipmentEnum.Reactor, null, null);

    ProcessEquipmentInterface result = DexpiEquipmentFactory.create(unit, feed);

    assertNotNull(result);
    assertTrue(result instanceof Stream);
  }

  /**
   * Tests that null unit throws IllegalArgumentException.
   */
  @Test
  public void testNullUnitThrowsException() {
    Stream feed = createTestStream();
    assertThrows(IllegalArgumentException.class, () -> DexpiEquipmentFactory.create(null, feed));
  }

  /**
   * Tests DexpiProcessUnit sizing attribute getters.
   */
  @Test
  public void testSizingAttributes() {
    DexpiProcessUnit unit = new DexpiProcessUnit("Unit-1", "Tank", EquipmentEnum.Tank, null, null);
    unit.setSizingAttribute(DexpiMetadata.INSIDE_DIAMETER, "3.5");
    unit.setSizingAttribute(DexpiMetadata.DESIGN_PRESSURE, "25.0");

    assertEquals("3.5", unit.getSizingAttribute(DexpiMetadata.INSIDE_DIAMETER));
    assertEquals(3.5, unit.getSizingAttributeAsDouble(DexpiMetadata.INSIDE_DIAMETER, -1.0), 1e-9);
    assertEquals(-1.0, unit.getSizingAttributeAsDouble("NonExistent", -1.0), 1e-9);
    assertEquals(2, unit.getSizingAttributes().size());
  }
}
