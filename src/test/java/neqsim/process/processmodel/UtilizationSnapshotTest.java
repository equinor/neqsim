package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

/**
 * Tests for the side-effect-free capacity utilization snapshot observation API
 * ({@link ProcessSystem#getUtilizationSnapshotJson()},
 * {@link ProcessModel#getUtilizationSnapshotJson()} and
 * {@link ProcessAutomation#getUtilizationSnapshot()}) and for the compressor no-chart constraint
 * gating fix.
 *
 * @author NeqSim
 * @version 1.0
 */
public class UtilizationSnapshotTest {

  /**
   * Builds a simple feed gas stream.
   *
   * @return a runnable gas stream
   */
  private Stream buildFeed() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 30.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    return feed;
  }

  /**
   * The snapshot JSON must be well-formed, schema-versioned, and report every unit with its
   * utilization and per-constraint breakdown.
   */
  @Test
  void testSnapshotJsonStructure() {
    Stream feed = buildFeed();
    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(60.0, "bara");
    Cooler cooler = new Cooler("cooler", comp.getOutletStream());
    cooler.setOutTemperature(40.0, "C");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.add(cooler);
    process.run();

    String snap = process.getUtilizationSnapshotJson();
    assertNotNull(snap);
    JsonObject root = JsonParser.parseString(snap).getAsJsonObject();
    assertEquals("1.0", root.get("schemaVersion").getAsString());
    assertTrue(root.has("anyOverloaded"));
    assertTrue(root.has("anyHardLimitExceeded"));
    assertTrue(root.has("bottleneck"));

    JsonArray units = root.getAsJsonArray("units");
    assertEquals(3, units.size());
    for (int i = 0; i < units.size(); i++) {
      JsonObject u = units.get(i).getAsJsonObject();
      assertTrue(u.has("name"));
      assertTrue(u.has("type"));
      assertTrue(u.has("maxUtilization"));
      assertTrue(u.has("feasible"));
      assertTrue(u.has("constraints"));
    }
  }

  /**
   * A compressor without an active performance chart must report smooth, power-driven utilization
   * (not pinned at a degenerate 100%) and its chart-dependent surge/speed constraints must be
   * present but disabled.
   */
  @Test
  void testCompressorWithoutChartHasChartConstraintsDisabled() {
    Stream feed = buildFeed();
    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(60.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    // Not pinned to degenerate 100 % utilization
    assertTrue(comp.getMaxUtilization() < 0.999,
        "Chartless compressor must not be pinned at 100% utilization, was "
            + comp.getMaxUtilization());

    java.util.Map<String, CapacityConstraint> constraints = comp.getCapacityConstraints();
    for (CapacityConstraint c : constraints.values()) {
      String name = c.getName().toLowerCase();
      if (name.contains("surge") || name.contains("speed") || name.contains("stonewall")) {
        assertFalse(c.isEnabled(),
            "Chart-dependent constraint '" + c.getName() + "' must be disabled without a chart");
      }
    }

    // The limiting constraint must be an enabled (power-based) constraint, never the disabled
    // surge/speed metrics — getMaxUtilization() and getBottleneckConstraint() must agree.
    CapacityConstraint bottleneck = comp.getBottleneckConstraint();
    if (bottleneck != null) {
      String bn = bottleneck.getName().toLowerCase();
      assertTrue(bottleneck.isEnabled(), "Bottleneck constraint must be enabled");
      assertFalse(bn.contains("surge") || bn.contains("stonewall"),
          "Chartless compressor bottleneck must not be a disabled chart metric, was "
              + bottleneck.getName());
    }
  }

  /**
   * Without a chart, the compressor utilization must increase monotonically with flow because the
   * power constraint (not a flat surge constraint) drives utilization.
   */
  @Test
  void testCompressorPowerUtilizationIncreasesWithFlow() {
    Stream feed = buildFeed();
    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(60.0, "bara");
    comp.getMechanicalDesign().setMaxDesignPower(500.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);

    feed.setFlowRate(80000.0, "kg/hr");
    process.run();
    double utilLow = comp.getMaxUtilization();

    feed.setFlowRate(120000.0, "kg/hr");
    process.run();
    double utilHigh = comp.getMaxUtilization();

    assertTrue(utilHigh > utilLow,
        "Power-driven utilization should rise with flow: " + utilLow + " -> " + utilHigh);
  }

  /**
   * The multi-area {@link ProcessModel} snapshot must tag every unit with its area name.
   */
  @Test
  void testProcessModelSnapshotTagsArea() {
    Stream feed = buildFeed();
    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(60.0, "bara");
    ProcessSystem area1 = new ProcessSystem();
    area1.add(feed);
    area1.add(comp);

    Cooler cooler = new Cooler("cooler", comp.getOutletStream());
    cooler.setOutTemperature(40.0, "C");
    ProcessSystem area2 = new ProcessSystem();
    area2.add(cooler);

    ProcessModel plant = new ProcessModel();
    plant.add("compression", area1);
    plant.add("cooling", area2);
    plant.run();

    String snap = plant.getUtilizationSnapshotJson();
    JsonObject root = JsonParser.parseString(snap).getAsJsonObject();
    assertEquals("1.0", root.get("schemaVersion").getAsString());
    JsonArray units = root.getAsJsonArray("units");
    assertEquals(3, units.size());
    for (int i = 0; i < units.size(); i++) {
      JsonObject u = units.get(i).getAsJsonObject();
      assertTrue(u.has("area"), "Each ProcessModel unit must carry an 'area' label");
    }
  }

  /**
   * {@link ProcessAutomation#getUtilizationSnapshot()} must delegate to the underlying process and
   * return the same schema-versioned JSON.
   */
  @Test
  void testProcessAutomationDelegation() {
    Stream feed = buildFeed();
    Compressor comp = new Compressor("comp", feed);
    comp.setOutletPressure(60.0, "bara");
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();

    ProcessAutomation auto = process.getAutomation();
    String snap = auto.getUtilizationSnapshot();
    JsonObject root = JsonParser.parseString(snap).getAsJsonObject();
    assertEquals("1.0", root.get("schemaVersion").getAsString());
    assertTrue(root.getAsJsonArray("units").size() >= 2);
  }
}
