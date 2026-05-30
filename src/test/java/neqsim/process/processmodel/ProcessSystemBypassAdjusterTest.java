package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.Adjuster;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies that the low-flow bypass mechanism interacts safely with {@link Adjuster}.
 *
 * <p>
 * Scenario: an Adjuster is configured to adjust a feed stream flow so that a downstream heater
 * reaches a target outlet temperature. If the downstream heater is manually locked inactive (or
 * auto-bypassed), the process must still {@code run()} without crashing.
 * </p>
 */
public class ProcessSystemBypassAdjusterTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  @Test
  public void adjusterTargetingBypassedHeaterDoesNotCrash() {
    Stream feed = new Stream("feed", makeGas(1000.0));

    Heater heater = new Heater("heater", feed);
    heater.setOutTemperature(320.0);

    Adjuster adj = new Adjuster("adj");
    adj.setAdjustedVariable(feed, "mass flow", "kg/hr");
    // Target a downstream pressure — exercises a valid Adjuster target type. The exact target
    // value is unimportant; this test only verifies that locking the target equipment inactive
    // does not crash the process run.
    adj.setTargetVariable(heater, "pressure", 50.0, "bara");

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(heater);
    ps.add(adj);

    // Baseline run completes.
    ps.run();
    assertTrue(heater.isActive());

    // Lock the heater inactive; the adjuster's target is now an inactive unit. The run should
    // still complete without throwing.
    heater.setLockedInactive(true);
    assertDoesNotThrow(() -> ps.run(),
        "ProcessSystem.run() must not crash when an Adjuster target is locked inactive");
    assertTrue(heater.isLockedInactive());

    // getBypassedUnits should report the heater.
    assertTrue(ps.getBypassedUnits().contains("heater"),
        "bypassed-units report should include the locked heater, got " + ps.getBypassedUnits());
  }

  @Test
  public void perUnitThresholdSetterUpdatesOnlyNamedUnit() {
    Stream feed = new Stream("feed", makeGas(1000.0));
    Heater h1 = new Heater("h1", feed);
    h1.setOutTemperature(320.0);
    Heater h2 = new Heater("h2", h1.getOutletStream());
    h2.setOutTemperature(330.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(h1);
    ps.add(h2);
    ps.run();

    ps.setSectionLowFlowThreshold("h2", 42.0);
    assertEquals(42.0, h2.getMinimumFlow(), 1e-9);
    // h1 must be unaffected — still at the default.
    assertTrue(h1.getMinimumFlow() < 1.0,
        "h1 minimumFlow should remain at default, got " + h1.getMinimumFlow());
  }
}
