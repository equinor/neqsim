package neqsim.process.equipment.expander;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Capacity-constraint tests for {@link Expander}.
 *
 * <p>
 * Verifies that an expander does not report the spurious 150% utilization that the inherited compressor fallback
 * produced, that its simulation is recognised as valid despite negative (recovered) power, that the consumed-power
 * constraints are replaced by a {@code recoveredPower} constraint, and that the recovered-power provenance is surfaced
 * in the utilization snapshot JSON.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class ExpanderCapacityTest {

  /**
   * Builds a simple high-pressure gas stream feeding an expander.
   *
   * @return a run expander operating from 60 bara to 20 bara
   */
  private Expander buildRunExpander() {
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 60.0);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.02);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed gas", gas);
    feed.setFlowRate(200000.0, "kg/hr");
    feed.setTemperature(40.0, "C");
    feed.setPressure(60.0, "bara");
    feed.run();

    Expander expander = new Expander("expander", feed);
    expander.setOutletPressure(20.0);
    expander.run();
    return expander;
  }

  @Test
  void testNoSpuriousFullUtilization() {
    Expander expander = buildRunExpander();

    // Expander extracts work -> negative shaft power.
    Assertions.assertTrue(expander.getPower("kW") < 0.0, "Expander should report negative (recovered) power");

    // The compressor fallback used to return 1.5 (150%) for an expander; the override must not.
    double maxUtil = expander.getMaxUtilization();
    Assertions.assertTrue(maxUtil < 1.2,
	"Expander max utilization should not hit the spurious 150% fallback, was " + maxUtil);
    Assertions.assertNotEquals(1.5, maxUtil, 1.0e-9, "Expander max utilization must not equal the 1.5 fallback value");
  }

  @Test
  void testSimulationValidWithNegativePower() {
    Expander expander = buildRunExpander();
    Assertions.assertTrue(expander.isSimulationValid(),
	"Expander simulation should be valid even though power is negative and gas cools");
  }

  @Test
  void testConsumedPowerConstraintsRemoved() {
    Expander expander = buildRunExpander();
    Assertions.assertFalse(expander.getCapacityConstraints().containsKey("power"),
	"Inherited compressor 'power' constraint should be removed for an expander");
    Assertions.assertFalse(expander.getCapacityConstraints().containsKey("ratedPower"),
	"Inherited compressor 'ratedPower' constraint should be removed for an expander");
  }

  @Test
  void testRecoveredPowerConstraint() {
    Expander expander = buildRunExpander();

    // No recovered-power rating yet -> no recoveredPower constraint.
    Assertions.assertFalse(expander.getCapacityConstraints().containsKey("recoveredPower"),
	"No recoveredPower constraint should exist before a rating is set");

    double recoveredKW = Math.abs(expander.getPower("kW"));
    // Rate the expander above its current recovered power so utilization is below 100%.
    double rated = recoveredKW * 1.25;
    expander.setRatedRecoveredPower(rated);

    CapacityConstraint c = expander.getCapacityConstraints().get("recoveredPower");
    Assertions.assertNotNull(c, "recoveredPower constraint should exist after setting a rating");
    Assertions.assertEquals(rated, c.getDesignValue(), 1.0e-6);
    Assertions.assertEquals("equipment", c.getDataSource());

    double util = c.getUtilization();
    Assertions.assertTrue(util > 0.0 && util < 1.0,
	"recoveredPower utilization should be between 0 and 1, was " + util);
    Assertions.assertEquals(recoveredKW / rated, util, 1.0e-3);
  }

  @Test
  void testProvenanceInSnapshotJson() {
    SystemInterface gas = new SystemSrkEos(273.15 + 40.0, 60.0);
    gas.addComponent("nitrogen", 0.01);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.05);
    gas.addComponent("propane", 0.02);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed gas", gas);
    feed.setFlowRate(200000.0, "kg/hr");
    feed.setTemperature(40.0, "C");
    feed.setPressure(60.0, "bara");

    Expander expander = new Expander("expander", feed);
    expander.setOutletPressure(20.0);

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(expander);
    process.run();

    expander.setRatedRecoveredPower(Math.abs(expander.getPower("kW")) * 1.25);
    process.run();

    String json = process.getUtilizationSnapshotJson();
    Assertions.assertTrue(json.contains("recoveredPower"),
	"Snapshot JSON should contain the recoveredPower constraint");
    Assertions.assertTrue(json.contains("\"dataSource\""),
	"Snapshot JSON should surface constraint dataSource provenance");
    Assertions.assertTrue(json.contains("\"dataSource\":\"equipment\""),
	"recoveredPower constraint dataSource should be reported as 'equipment'");
  }
}
