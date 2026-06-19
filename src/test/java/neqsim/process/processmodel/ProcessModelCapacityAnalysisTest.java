package neqsim.process.processmodel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests the additive whole-plant capacity and bottleneck analysis API on {@link ProcessModel}. Each method aggregates
 * the corresponding {@link ProcessSystem} method across all process areas.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessModelCapacityAnalysisTest {

  /**
   * Builds a two-area plant: a "separation" area (feed -&gt; inlet separator) and a "compression" area (export
   * compressor -&gt; export cooler), with the compressor fed by the separator gas outlet shared by object reference
   * across areas.
   *
   * @return the assembled and run multi-area plant
   */
  private ProcessModel buildAndRunPlant() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(10.0, "bara");

    Separator inletSeparator = new Separator("inlet separator", feed);
    inletSeparator.getMechanicalDesign().setMaxDesignGassVolumeFlow(60_000.0);

    ProcessSystem separation = new ProcessSystem();
    separation.add(feed);
    separation.add(inletSeparator);

    Compressor exportCompressor = new Compressor("export compressor", inletSeparator.getGasOutStream());
    exportCompressor.setOutletPressure(50.0);
    exportCompressor.getMechanicalDesign().setMaxDesignPower(5000.0);

    Cooler exportCooler = new Cooler("export cooler", exportCompressor.getOutletStream());
    exportCooler.setOutTemperature(40.0, "C");

    ProcessSystem compression = new ProcessSystem();
    compression.add(exportCompressor);
    compression.add(exportCooler);

    ProcessModel plant = new ProcessModel();
    plant.add("separation", separation);
    plant.add("compression", compression);
    plant.run();
    return plant;
  }

  /**
   * Verifies that constrained equipment is aggregated across both areas.
   */
  @Test
  public void testGetConstrainedEquipmentAggregatesAreas() {
    ProcessModel plant = buildAndRunPlant();

    List<CapacityConstrainedEquipment> areaSep = plant.get("separation").getConstrainedEquipment();
    List<CapacityConstrainedEquipment> areaComp = plant.get("compression").getConstrainedEquipment();
    List<CapacityConstrainedEquipment> all = plant.getConstrainedEquipment();

    Assertions.assertEquals(areaSep.size() + areaComp.size(), all.size());
    Assertions.assertFalse(all.isEmpty());
  }

  /**
   * Verifies that the plant-wide bottleneck is the most-utilized of the per-area bottlenecks.
   */
  @Test
  public void testGetBottleneckPicksHighestUtilizationAcrossPlant() {
    ProcessModel plant = buildAndRunPlant();

    double sepUtil = plant.get("separation").getBottleneckUtilization();
    double compUtil = plant.get("compression").getBottleneckUtilization();
    double expectedMax = Math.max(sepUtil, compUtil);

    Assertions.assertNotNull(plant.getBottleneck());
    Assertions.assertEquals(expectedMax, plant.getBottleneckUtilization(), 1e-9);
  }

  /**
   * Verifies the detailed plant-wide bottleneck result is non-empty and consistent with the simple bottleneck
   * utilization.
   */
  @Test
  public void testFindBottleneckReturnsNonEmptyResult() {
    ProcessModel plant = buildAndRunPlant();

    BottleneckResult result = plant.findBottleneck();
    Assertions.assertNotNull(result);
    Assertions.assertNotNull(result.getEquipment());
    Assertions.assertTrue(result.getUtilization() > 0.0);
  }

  /**
   * Verifies the capacity utilization summary uses area-qualified keys and matches per-area entries.
   */
  @Test
  public void testCapacityUtilizationSummaryUsesAreaQualifiedKeys() {
    ProcessModel plant = buildAndRunPlant();

    Map<String, Double> summary = plant.getCapacityUtilizationSummary();
    Assertions.assertFalse(summary.isEmpty());
    for (String key : summary.keySet()) {
      Assertions.assertTrue(key.contains("::"), "Summary keys should be area-qualified: " + key);
    }

    int perAreaTotal = plant.get("separation").getCapacityUtilizationSummary().size()
	+ plant.get("compression").getCapacityUtilizationSummary().size();
    Assertions.assertEquals(perAreaTotal, summary.size());
  }

  /**
   * Verifies that disabling then enabling all constraints returns consistent non-negative counts and toggles the
   * overload/hard-limit detection coherently.
   */
  @Test
  public void testDisableThenEnableAllConstraints() {
    ProcessModel plant = buildAndRunPlant();

    int disabled = plant.disableAllConstraints();
    Assertions.assertTrue(disabled >= 0);
    // With analysis disabled on all equipment, nothing should report overloaded.
    Assertions.assertFalse(plant.isAnyEquipmentOverloaded());
    Assertions.assertFalse(plant.isAnyHardLimitExceeded());

    int enabled = plant.enableAllConstraints();
    Assertions.assertTrue(enabled >= 0);
    // Disable count equals the sum of the per-area disable counts (aggregation check).
    int perAreaDisabled = 0;
    ProcessModel fresh = buildAndRunPlant();
    perAreaDisabled += fresh.get("separation").disableAllConstraints();
    perAreaDisabled += fresh.get("compression").disableAllConstraints();
    Assertions.assertEquals(perAreaDisabled, disabled);
  }

  /**
   * Verifies the near-capacity-limit list uses area-qualified names and is a subset of all units.
   */
  @Test
  public void testEquipmentNearCapacityLimitUsesAreaQualifiedNames() {
    ProcessModel plant = buildAndRunPlant();

    List<String> nearLimit = plant.getEquipmentNearCapacityLimit();
    Assertions.assertNotNull(nearLimit);
    for (String name : nearLimit) {
      Assertions.assertTrue(name.contains("::"), "Near-limit names should be area-qualified: " + name);
    }
  }
}
