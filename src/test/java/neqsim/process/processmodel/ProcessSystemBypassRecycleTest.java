package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Verifies the low-flow bypass mechanism interacts safely with {@link Recycle} loops:
 * <ul>
 * <li>{@code deactivateSection} stops descending at a {@link Recycle} that still has another active
 * feed (does not silently kill the parallel branch).</li>
 * <li>A recycle loop still converges when a unit inside the loop is locked inactive.</li>
 * <li>{@code getBypassedUnits} reports the locked unit.</li>
 * </ul>
 */
public class ProcessSystemBypassRecycleTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  @Test
  public void deactivateSectionStopsAtRecycleWithOtherActiveFeed() {
    // Fresh feed enters a mixer together with a recycle return; mixer feeds a separator.
    // Splitter on the separator gas sends part to product and part back through the recycle.
    Stream freshFeed = new Stream("freshFeed", makeGas(1000.0));
    Stream recycleSeed = new Stream("recycleSeed", makeGas(100.0));

    Mixer mix = new Mixer("mix");
    mix.addStream(freshFeed);
    mix.addStream(recycleSeed);

    Separator sep = new Separator("sep", mix.getOutletStream());

    Splitter gasSplit = new Splitter("gasSplit", sep.getGasOutStream(), 2);
    gasSplit.setSplitFactors(new double[] {0.9, 0.1});

    Heater recycleHeater = new Heater("recycleHeater", gasSplit.getSplitStream(1));
    recycleHeater.setOutTemperature(305.0);

    Recycle rec = new Recycle("rec");
    rec.addStream(recycleHeater.getOutletStream());
    rec.setOutletStream(recycleSeed);
    rec.setTolerance(1e-3);

    ProcessSystem ps = new ProcessSystem();
    ps.add(freshFeed);
    ps.add(recycleSeed);
    ps.add(mix);
    ps.add(sep);
    ps.add(gasSplit);
    ps.add(recycleHeater);
    ps.add(rec);

    ps.run();

    // Now deactivate starting at the recycleHeater. The deactivation should stop at the Mixer
    // because freshFeed (an active feed not in the deactivated set) still feeds the mixer.
    int locked = ps.deactivateSection("recycleHeater");
    assertTrue(locked >= 1, "expected at least recycleHeater to be locked, got " + locked);
    assertTrue(recycleHeater.isLockedInactive());
    // freshFeed and the mixer must NOT be locked (other active feed reaches the mixer).
    assertFalse(freshFeed.isLockedInactive(),
        "freshFeed should remain active — mixer has another active feed");
    assertFalse(mix.isLockedInactive(), "mixer should remain active — fresh feed is still live");

    // Process should still run (recycle loop converges with the bypassed unit).
    ps.run();

    List<String> bypassed = ps.getBypassedUnits();
    assertTrue(bypassed.contains("recycleHeater"),
        "getBypassedUnits should list recycleHeater, got " + bypassed);

    // The separator gas outlet should still carry roughly the fresh-feed gas inventory.
    StreamInterface gasProduct = gasSplit.getSplitStream(0);
    assertTrue(gasProduct.getFlowRate("kg/hr") > 500.0,
        "main gas product should still carry meaningful flow, got "
            + gasProduct.getFlowRate("kg/hr"));
  }

  @Test
  public void getBypassedUnitsReturnsEmptyByDefault() {
    Stream feed = new Stream("feed", makeGas(1000.0));
    Heater h = new Heater("h", feed);
    h.setOutTemperature(320.0);
    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(h);
    ps.run();
    assertEquals(0, ps.getBypassedUnits().size());
  }

  @Test
  public void fractionalThresholdMatchesInletFraction() {
    Stream feed = new Stream("feed", makeGas(1000.0));
    Heater h = new Heater("h", feed);
    h.setOutTemperature(320.0);
    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(h);
    ps.run();

    int updated = ps.setSectionLowFlowThresholdFraction(0.01);
    assertTrue(updated >= 1, "expected at least heater to be updated, got " + updated);
    assertEquals(10.0, h.getMinimumFlow(), 1e-6);
  }
}
