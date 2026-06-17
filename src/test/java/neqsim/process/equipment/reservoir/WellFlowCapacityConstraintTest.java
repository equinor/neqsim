package neqsim.process.equipment.reservoir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Tests for the subsurface capacity constraints on {@link WellFlow} and the multi-area
 * bottleneck/utilization API on {@link ProcessModel}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class WellFlowCapacityConstraintTest {

  /**
   * Builds a reservoir + well subsurface area and adds both to the supplied process.
   *
   * @param pi the well production index (MSm3/day/bar^2)
   * @param process the process system the reservoir and well are added to
   * @return the configured well flow unit (its outlet stream is the area export)
   */
  private WellFlow buildWell(double pi, ProcessSystem process) {
    neqsim.thermo.system.SystemInterface fluid =
        new neqsim.thermo.system.SystemPrEos(373.15, 100.0);
    fluid.addComponent("water", 3.599);
    fluid.addComponent("nitrogen", 0.599);
    fluid.addComponent("CO2", 0.51);
    fluid.addComponent("methane", 62.8);
    fluid.addComponent("n-heptane", 12.8);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);

    SimpleReservoir reservoir = new SimpleReservoir("reservoir");
    reservoir.setReservoirFluid(fluid, 1e9, 10.0, 10.0e7);
    StreamInterface producer = reservoir.addGasProducer("producer");
    producer.setFlowRate(1.0, "MSm3/day");

    WellFlow well = new WellFlow("well");
    well.setInletStream(producer);
    well.setWellProductionIndex(pi);

    process.add(reservoir);
    process.add(well);
    return well;
  }

  /**
   * Verifies that the drawdown constraint is disabled by default and reports a live, non-zero
   * utilization once enabled.
   */
  @Test
  void testWellDrawdownConstraint() {
    ProcessSystem subsurface = new ProcessSystem();
    WellFlow well = buildWell(5.0e-4, subsurface);

    // Disabled by default → no enabled constraints → empty bottleneck.
    subsurface.run();
    assertFalse(well.isWellConstraintsEnabled(), "constraints should be off by default");

    double pr = well.getInletStream().getPressure("bara");
    double pwf = well.getOutletStream().getPressure("bara");
    double drawdown = pr - pwf;
    assertTrue(drawdown > 0.0, "expected positive drawdown, got " + drawdown);

    // Enable with a max drawdown just above the live drawdown → high utilization.
    well.setMaxDrawdown(drawdown * 1.25, "bara");
    well.useWellConstraints();
    subsurface.run();

    double util = well.getMaxUtilization();
    assertTrue(util > 0.5 && util <= 1.0, "drawdown utilization should be ~0.8, got " + util);
    assertEquals(drawdown, well.getDrawdown(), 1.0e-6);

    BottleneckResult bottleneck = subsurface.findBottleneck();
    assertTrue(bottleneck.hasBottleneck(), "well should now be a bottleneck candidate");
    assertEquals("well", bottleneck.getEquipmentName());
    assertEquals("well drawdown", bottleneck.getConstraintName());
  }

  /**
   * Verifies that the minimum-BHP constraint reports rising utilization as Pwf approaches the lower
   * limit.
   */
  @Test
  void testWellMinBhpConstraint() {
    ProcessSystem subsurface = new ProcessSystem();
    WellFlow well = buildWell(5.0e-4, subsurface);
    subsurface.run();

    double pwf = well.getOutletStream().getPressure("bara");
    // Set the minimum BHP to 90% of the live Pwf → utilization = minBHP/Pwf = 0.9.
    well.setMinBottomHolePressure(pwf * 0.9, "bara");
    well.useWellConstraints();
    subsurface.run();

    double util = well.getMaxUtilization();
    assertTrue(util > 0.8 && util <= 1.0, "min BHP utilization should be ~0.9, got " + util);
  }

  /**
   * Verifies the plant-wide bottleneck ranking across two areas (subsurface + topside) using the
   * new {@link ProcessModel} capacity API.
   */
  @Test
  void testProcessModelGlobalBottleneck() {
    // Subsurface area: reservoir + well with an enabled drawdown constraint.
    ProcessSystem subsurface = new ProcessSystem();
    WellFlow well = buildWell(5.0e-4, subsurface);
    subsurface.run();
    double drawdown = well.getDrawdown();
    well.setMaxDrawdown(drawdown * 2.0, "bara"); // utilization ~0.5
    well.useWellConstraints();

    // Topside area: a separator fed by a fresh stream, with Equinor constraints enabled.
    neqsim.thermo.system.SystemInterface gas = new neqsim.thermo.system.SystemPrEos(298.15, 60.0);
    gas.addComponent("methane", 90.0);
    gas.addComponent("ethane", 5.0);
    gas.addComponent("propane", 5.0);
    gas.setMixingRule(2);
    StreamInterface sepFeed = new neqsim.process.equipment.stream.Stream("sep feed", gas);
    sepFeed.setFlowRate(50.0, "MSm3/day");
    Separator separator = new Separator("HP sep", sepFeed);
    separator.setInternalDiameter(1.5);
    ProcessSystem topside = new ProcessSystem();
    topside.add(sepFeed);
    topside.add(separator);
    topside.run();
    separator.useEquinorConstraints();
    topside.run();

    ProcessModel plant = new ProcessModel();
    plant.add("Subsurface", subsurface);
    plant.add("Topside", topside);
    plant.run();

    java.util.Map<String, Double> summary = plant.getCapacityUtilizationSummary();
    assertTrue(summary.containsKey("Subsurface::well"),
        "area-qualified summary should contain the well: " + summary.keySet());

    java.util.List<String> ranking = plant.getBottleneckRanking();
    assertFalse(ranking.isEmpty(), "ranking should not be empty");
    // Ranking is sorted descending; the first entry has the highest utilization.
    assertTrue(ranking.get(0).contains("::"), "ranking entries are area-qualified: " + ranking);

    BottleneckResult global = plant.findBottleneck();
    assertTrue(global.hasBottleneck(), "global bottleneck should exist");
  }
}
