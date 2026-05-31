package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the section-level low-flow bypass feature: auto-bypass via {@code minimumFlow}
 * threshold and manual section deactivation via {@code deactivateSection(String)}.
 */
public class ProcessSystemLowFlowBypassTest extends neqsim.NeqSimTest {

  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  @Test
  public void autoBypassZeroFlowInletAllowsActiveBranchToRun() {
    Stream activeFeed = new Stream("activeFeed", makeGas(1000.0));
    Stream lowFlowFeed = new Stream("lowFlowFeed", makeGas(0.0));

    Heater lowHeater = new Heater("lowHeater", lowFlowFeed);
    lowHeater.setOutTemperature(310.0);
    lowHeater.setMinimumFlow(1.0);

    Mixer mix = new Mixer("mix");
    mix.addStream(activeFeed);
    mix.addStream(lowHeater.getOutletStream());

    ProcessSystem ps = new ProcessSystem();
    ps.add(activeFeed);
    ps.add(lowFlowFeed);
    ps.add(lowHeater);
    ps.add(mix);
    ps.run();

    // Mixer outlet should reflect the active feed only.
    StreamInterface out = mix.getOutletStream();
    assertEquals(1000.0, out.getFlowRate("kg/hr"), 1.0);
  }

  @Test
  public void manualDeactivateSectionLocksDownstreamEquipment() {
    Stream feed = new Stream("feed", makeGas(2000.0));

    Splitter split = new Splitter("split", feed, 2);
    split.setSplitFactors(new double[] {0.5, 0.5});

    Heater hA = new Heater("hA", split.getSplitStream(0));
    hA.setOutTemperature(320.0);
    Heater hB = new Heater("hB", split.getSplitStream(1));
    hB.setOutTemperature(340.0);

    ProcessSystem ps = new ProcessSystem();
    ps.add(feed);
    ps.add(split);
    ps.add(hA);
    ps.add(hB);

    ps.run();
    assertTrue(hA.isActive());
    assertTrue(hB.isActive());

    int locked = ps.deactivateSection("hB");
    assertTrue(locked >= 1, "expected at least hB to be locked, got " + locked);
    assertTrue(hB.isLockedInactive());
    assertFalse(hB.isActive());

    ps.run();
    // hA still active across reruns; hB stays inactive due to lock.
    assertTrue(hA.isActive());
    assertFalse(hB.isActive());

    ps.activateAll();
    ps.run();
    assertTrue(hB.isActive());
    assertFalse(hB.isLockedInactive());
  }

  @Test
  public void thresholdAutoBypassesSeparatorAndZeroesOutlets() {
    Stream tinyFeed = new Stream("tiny", makeGas(0.5));
    Separator sep = new Separator("sep", tinyFeed);

    ProcessSystem ps = new ProcessSystem();
    ps.add(tinyFeed);
    ps.add(sep);
    ps.setSectionLowFlowThreshold(10.0);
    ps.run();

    // Separator was auto-bypassed; outlets should be ~0.
    assertEquals(0.0, sep.getGasOutStream().getFlowRate("kg/hr"), 1e-6);
    assertEquals(0.0, sep.getLiquidOutStream().getFlowRate("kg/hr"), 1e-6);
  }

  @Test
  public void processModelDeactivateSectionPropagatesToArea() {
    Stream feed = new Stream("mFeed", makeGas(1500.0));
    Heater heater = new Heater("mHeater", feed);
    heater.setOutTemperature(330.0);

    ProcessSystem area = new ProcessSystem();
    area.add(feed);
    area.add(heater);

    ProcessModel plant = new ProcessModel();
    plant.add("Area1", area);
    plant.run();
    assertTrue(heater.isActive());

    int locked = plant.deactivateSection("Area1", "mHeater");
    assertTrue(locked >= 1);
    assertTrue(heater.isLockedInactive());

    plant.run();
    assertFalse(heater.isActive());

    plant.activateAll();
    plant.run();
    assertTrue(heater.isActive());
  }
}
