package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.MultiStreamHeatExchanger;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests that a low-flow-bypassed section still publishes a usable pressure and composition boundary to the units
 * downstream of it.
 *
 * <p>
 * The failure mode this guards against is a stagnant leg whose valve, pipeline or exchanger is skipped without writing
 * its outlet stream: a downstream mixer then reads a stale pressure from that dead branch and drags the whole live
 * train down with it.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class LowFlowBypassDownstreamEffectTest extends neqsim.NeqSimTest {

  /**
   * Builds a simple two-component gas at the requested mass flow.
   *
   * @param flowKgHr total mass flow in kg/hr
   * @return a ready-to-use thermodynamic system
   */
  private static SystemInterface makeGas(double flowKgHr) {
    SystemInterface fluid = new SystemSrkEos(298.15, 80.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setTotalFlowRate(flowKgHr, "kg/hr");
    return fluid;
  }

  @Test
  public void bypassedValveStillPublishesItsOutletPressureToADownstreamMixer() {
    Stream liveFeed = new Stream("live feed", makeGas(50000.0));
    liveFeed.setPressure(30.0, "bara");
    liveFeed.setTemperature(25.0, "C");

    // A stagnant dead leg at high pressure feeding a valve that lets down to 30 bara.
    Stream deadLeg = new Stream("dead leg", makeGas(0.02));
    deadLeg.setPressure(80.0, "bara");
    deadLeg.setTemperature(25.0, "C");

    ThrottlingValve valve = new ThrottlingValve("dead leg valve", deadLeg);
    valve.setOutletPressure(30.0, "bara");

    Mixer mixer = new Mixer("mixer");
    mixer.addStream(liveFeed);
    mixer.addStream(valve.getOutletStream());

    ProcessSystem process = new ProcessSystem();
    process.add(liveFeed);
    process.add(deadLeg);
    process.add(valve);
    process.add(mixer);
    process.setSectionLowFlowThreshold(1.0);
    process.run();

    // The valve is bypassed but its outlet still carries the specified let-down pressure,
    // so a downstream mixer sees a consistent boundary rather than the 80 bara inlet.
    assertFalse(valve.isActive());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/hr"), 1e-9);
    assertEquals(30.0, valve.getOutletStream().getPressure("bara"), 1e-6);

    // The live train is unaffected: the mixer ignores the dead branch entirely.
    assertEquals(30.0, mixer.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(50000.0, mixer.getOutletStream().getFlowRate("kg/hr"), 1.0);
    assertTrue(process.getBypassedUnits().contains("dead leg valve"));
  }

  @Test
  public void bypassedPipelinePassesInletStateThroughToItsOutlet() {
    Stream deadLeg = new Stream("dead leg", makeGas(0.02));
    deadLeg.setPressure(80.0, "bara");
    deadLeg.setTemperature(25.0, "C");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("dead leg pipe", deadLeg);
    pipe.setLength(500.0);
    pipe.setElevation(0.0);
    pipe.setDiameter(0.2);
    pipe.setPipeWallRoughness(1e-5);
    pipe.setNumberOfIncrements(5);

    ProcessSystem process = new ProcessSystem();
    process.add(deadLeg);
    process.add(pipe);
    process.setSectionLowFlowThreshold(1.0);
    process.run();

    assertFalse(pipe.isActive());
    assertEquals(80.0, pipe.getOutletStream().getPressure("bara"), 1e-6);
    assertEquals(298.15, pipe.getOutletStream().getTemperature("K"), 1e-6);
  }

  @Test
  public void bypassedMultiStreamHeatExchangerPassesEachSideThrough() {
    Stream hot = new Stream("hot", makeGas(0.02));
    hot.setPressure(80.0, "bara");
    hot.setTemperature(90.0, "C");

    Stream cold = new Stream("cold", makeGas(0.02));
    cold.setPressure(60.0, "bara");
    cold.setTemperature(10.0, "C");

    MultiStreamHeatExchanger exchanger = new MultiStreamHeatExchanger("dead leg mshe");
    exchanger.addInStream(hot);
    exchanger.addInStream(cold);

    ProcessSystem process = new ProcessSystem();
    process.add(hot);
    process.add(cold);
    process.add(exchanger);
    process.setSectionLowFlowThreshold(1.0);
    process.run();

    assertFalse(exchanger.isActive());
    assertEquals(90.0, exchanger.getOutStream(0).getTemperature("C"), 1e-6);
    assertEquals(80.0, exchanger.getOutStream(0).getPressure("bara"), 1e-6);
    assertEquals(10.0, exchanger.getOutStream(1).getTemperature("C"), 1e-6);
    assertEquals(60.0, exchanger.getOutStream(1).getPressure("bara"), 1e-6);
  }

  @Test
  public void splitterBypassCascadesThroughAValveWithoutCorruptingTheLiveTrain() {
    Stream feed = new Stream("feed", makeGas(0.05));
    feed.setPressure(80.0, "bara");
    feed.setTemperature(25.0, "C");

    Splitter splitter = new Splitter("dead leg splitter", feed, 2);
    splitter.setSplitFactors(new double[] { 0.5, 0.5 });

    ThrottlingValve valve = new ThrottlingValve("dead leg valve", splitter.getSplitStream(0));
    valve.setOutletPressure(30.0, "bara");

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(splitter);
    process.add(valve);
    process.setSectionLowFlowThreshold(1.0);
    process.run();

    // Splitter zeroes its outlets, and the valve downstream of it still resolves to its
    // specified outlet pressure instead of being left unset.
    assertFalse(splitter.isActive());
    assertFalse(valve.isActive());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/hr"), 1e-9);
    assertEquals(30.0, valve.getOutletStream().getPressure("bara"), 1e-6);
  }

  @Test
  public void lowFlowThresholdCanBeGivenInAnyMassFlowUnit() {
    Stream feed = new Stream("feed", makeGas(1000.0));
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.setSectionLowFlowThreshold(1.2, "tonne/day");

    assertEquals(50.0, feed.getMinimumFlow(), 1e-9);
    assertEquals(1.2, feed.getMinimumFlow("tonne/day"), 1e-9);

    feed.setMinimumFlow(0.5, "kg/min");
    assertEquals(30.0, feed.getMinimumFlow(), 1e-9);

    assertThrows(IllegalArgumentException.class,
        () -> ProcessEquipmentBaseClass.massFlowConversionToKgPerHour("MSm3/day"));
  }
}
