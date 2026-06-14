package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the bulk {@code applyMechanicalDesignCapacityConstraints()} helpers on
 * {@link ProcessSystem} and {@link ProcessModel}. These surface the mechanical-design limits of
 * every equipment item in a single call, which is the "out of the box, then fully configurable"
 * enabler for large multi-area plants.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BulkMechanicalDesignConstraintsTest {

  /**
   * Builds a heater with a fixed pressure drop and positive duty inside its own process system.
   *
   * @param name the equipment and stream name prefix
   * @param inletPressure inlet pressure in bara
   * @param outletPressure outlet pressure in bara
   * @return a run process system whose single heater is named {@code name}
   */
  private ProcessSystem buildHeaterProcess(String name, double inletPressure,
      double outletPressure) {
    SystemInterface fluid = new SystemSrkEos(298.15, inletPressure);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");
    feed.setPressure(inletPressure, "bara");
    feed.setTemperature(25.0, "C");

    Heater heater = new Heater(name, feed);
    heater.setOutletPressure(outletPressure);
    heater.setOutTemperature(60.0 + 273.15);

    ProcessSystem process = new ProcessSystem();
    process.setName(name + " area");
    process.add(feed);
    process.add(heater);
    process.run();
    return process;
  }

  /**
   * Without any design limits, the bulk helper registers nothing and utilization stays zero.
   */
  @Test
  void processSystemBulkNoLimits() {
    ProcessSystem process = buildHeaterProcess("H-100", 60.0, 50.0);
    int added = process.applyMechanicalDesignCapacityConstraints();
    assertEquals(0, added, "no derived constraints expected without design limits");
  }

  /**
   * The process-system bulk helper sums derived constraints across all equipment and surfaces them
   * in the utilization snapshot.
   */
  @Test
  void processSystemBulkRegistersAcrossEquipment() {
    ProcessSystem process = buildHeaterProcess("H-100", 60.0, 50.0);
    Heater heater = (Heater) process.getUnit("H-100");

    // dP = 10 bara, limit 20 bara -> 0.5; plus a volume-flow limit at 2x current -> 0.5
    heater.getMechanicalDesign().setMaxDesignPressureDrop(20.0);
    double inletFlow = heater.getInletStreams().get(0).getFlowRate("m3/hr");
    assertTrue(inletFlow > 0.0, "heater inlet volumetric flow should be positive");
    heater.getMechanicalDesign().setMaxDesignVolumeFlow(inletFlow * 2.0);

    int added = process.applyMechanicalDesignCapacityConstraints();
    assertEquals(2, added, "pressure-drop and volume-flow constraints should be registered");
    assertEquals(0.5, heater.getMaxUtilization(), 1.0e-3,
        "max utilization should reflect the 50% loaded limits");

    String snapshot = process.getUtilizationSnapshotJson();
    assertTrue(snapshot.contains("\"schemaVersion\""), "snapshot should be schema-versioned");
    assertTrue(snapshot.contains("H-100"), "snapshot should list the heater");
  }

  /**
   * The bulk helper is idempotent — re-applying does not duplicate constraints.
   */
  @Test
  void processSystemBulkIsIdempotent() {
    ProcessSystem process = buildHeaterProcess("H-100", 60.0, 50.0);
    Heater heater = (Heater) process.getUnit("H-100");
    heater.getMechanicalDesign().setMaxDesignPressureDrop(20.0);

    int first = process.applyMechanicalDesignCapacityConstraints();
    int second = process.applyMechanicalDesignCapacityConstraints();
    assertEquals(first, second, "re-applying should register the same count");
    assertEquals(1L,
        heater.getCapacityConstraints().values().stream()
            .filter(c -> "mechanicalDesign".equals(c.getDataSource())).count(),
        "exactly one mechanical-design constraint should remain after re-apply");
  }

  /**
   * The model-level bulk helper rolls up across every process area and the multi-area snapshot
   * carries area labels and a bottleneck.
   */
  @Test
  void processModelBulkRollsUpAcrossAreas() {
    ProcessSystem areaA = buildHeaterProcess("H-A", 60.0, 50.0); // dP 10
    ProcessSystem areaB = buildHeaterProcess("H-B", 80.0, 50.0); // dP 30

    Heater hA = (Heater) areaA.getUnit("H-A");
    Heater hB = (Heater) areaB.getUnit("H-B");
    hA.getMechanicalDesign().setMaxDesignPressureDrop(40.0); // 10/40 = 0.25
    hB.getMechanicalDesign().setMaxDesignPressureDrop(40.0); // 30/40 = 0.75 -> bottleneck

    ProcessModel model = new ProcessModel();
    model.add("Area A", areaA);
    model.add("Area B", areaB);
    model.run();

    int added = model.applyMechanicalDesignCapacityConstraints();
    assertEquals(2, added, "one derived constraint per area should be registered");

    assertEquals(0.25, hA.getMaxUtilization(), 1.0e-3);
    assertEquals(0.75, hB.getMaxUtilization(), 1.0e-3);

    String snapshot = model.getUtilizationSnapshotJson();
    assertTrue(snapshot.contains("\"area\""), "multi-area snapshot should carry area labels");
    assertTrue(snapshot.contains("H-B"), "bottleneck heater should appear in the snapshot");
    assertTrue(snapshot.contains("bottleneck"), "snapshot should include a bottleneck entry");
  }
}
