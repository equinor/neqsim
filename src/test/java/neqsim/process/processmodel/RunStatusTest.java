package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the structured per-unit {@link RunStatus} exposed by {@link ProcessSystem} and
 * {@link ProcessModel}.
 */
class RunStatusTest {

  /** A process unit that always throws during {@link #run(UUID)}. */
  private static class FailingUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1000L;

    FailingUnit(String name) {
      super(name);
    }

    /** {@inheritDoc} */
    @Override
    public void run(UUID id) {
      throw new IllegalStateException("forced process unit failure");
    }
  }

  /**
   * Builds a simple feed/separator/compressor process.
   *
   * @param feedFlowKgHr feed mass flow in kg/hr
   * @return the constructed (not yet run) process system
   */
  private ProcessSystem buildProcess(double feedFlowKgHr) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 70.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.12);
    fluid.addComponent("propane", 0.08);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(feedFlowKgHr, "kg/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(70.0, "bara");

    Separator separator = new Separator("HP Sep", feed);

    Compressor compressor = new Compressor("Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(120.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    return process;
  }

  @Test
  void testRunStatusSuccess() {
    ProcessSystem process = buildProcess(50000.0);
    process.run();

    RunStatus status = process.getRunStatus();
    assertNotNull(status);
    assertTrue(status.isCompleted());
    assertTrue(status.isSuccess(), "a healthy process should report success");
    assertNull(status.getFailedUnitName());
    // All three units should be recorded as successful.
    assertEquals(3, status.getUnits().size());
    for (UnitRunStatus u : status.getUnits()) {
      assertTrue(u.isSuccess());
    }
  }

  @Test
  void testRunStatusJsonSchemaVersioned() {
    ProcessSystem process = buildProcess(50000.0);
    process.run();

    String json = process.getRunStatusJson();
    assertNotNull(json);
    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
    assertEquals(RunStatus.SCHEMA_VERSION, root.get("schemaVersion").getAsString());
    assertTrue(root.get("success").getAsBoolean());
    assertEquals(3, root.get("unitCount").getAsInt());
    assertTrue(root.has("units"));
  }

  @Test
  void testRunStatusRecordsFailedUnit() {
    final ProcessSystem process = new ProcessSystem();
    process.add(new FailingUnit("BrokenUnit"));

    assertThrows(RuntimeException.class, new Executable() {
      @Override
      public void execute() {
        process.run();
      }
    });

    RunStatus status = process.getRunStatus();
    assertNotNull(status);
    assertTrue(status.isCompleted());
    assertFalse(status.isSuccess(), "a failed run must not report success");
    assertEquals("BrokenUnit", status.getFailedUnitName());
    assertNotNull(status.getFailedUnitError());
  }

  @Test
  void testProcessModelAggregatesRunStatus() {
    ProcessSystem area1 = buildProcess(50000.0);
    ProcessSystem area2 = new ProcessSystem();
    area2.add(new FailingUnit("BrokenUnit"));

    ProcessModel plant = new ProcessModel();
    plant.add("area1", area1);
    plant.add("area2", area2);

    // Run each area independently; area2 fails. Tolerate the propagated exception.
    try {
      area1.run();
    } catch (RuntimeException ignored) {
      // not expected for area1
    }
    try {
      area2.run();
    } catch (RuntimeException ignored) {
      // expected for area2
    }

    RunStatus aggregate = plant.getRunStatus();
    assertNotNull(aggregate);
    assertFalse(aggregate.isSuccess(), "aggregate must fail if any area failed");
    assertEquals("BrokenUnit", aggregate.getFailedUnitName());
    // area1 units (3) + area2 unit (1) = 4 entries, each tagged with its area.
    assertEquals(4, aggregate.getUnits().size());
    boolean sawArea = false;
    for (UnitRunStatus u : aggregate.getUnits()) {
      if ("area2".equals(u.getAreaName())) {
        sawArea = true;
      }
    }
    assertTrue(sawArea, "aggregated units must carry their area name");
  }
}
