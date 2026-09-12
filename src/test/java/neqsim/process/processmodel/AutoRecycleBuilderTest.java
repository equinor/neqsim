package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.AccelerationMethod;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests automatic recycle insertion on implicit feedback loops.
 */
public class AutoRecycleBuilderTest {
  /**
   * Builds a three component gas-condensate feed.
   *
   * @return a fluid ready to run
   */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("n-heptane", 0.1);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * A loop wired straight back into a mixer is closed by a generated tear stream and recycle.
   */
  @Test
  void testMakeRecyclesClosesLoopInsideProcessSystem() {
    Stream feed = new Stream("feed", createFluid());
    feed.setFlowRate(1000.0, "kg/hr");

    Mixer mixer = new Mixer("inlet mixer");
    mixer.addStream(feed);

    Separator separator = new Separator("separator", mixer.getOutletStream());
    Splitter splitter = new Splitter("gas splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] { 0.9, 0.1 });

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(mixer);
    process.add(separator);
    process.add(splitter);
    process.run();

    // Close the loop implicitly: the splitter recycle branch goes straight back to the mixer.
    mixer.addStream(splitter.getSplitStream(1));
    process.run();

    List<Recycle> created = process.makeRecycles();
    assertEquals(1, created.size());

    Recycle recycle = created.get(0);
    assertTrue(process.getUnitOperations().contains(recycle));
    assertSame(splitter.getSplitStream(1), recycle.getInletStreams().get(0));
    assertNotSame(splitter.getSplitStream(1), mixer.getInletStreams().get(1));
    assertSame(recycle.getOutletStream(), mixer.getInletStreams().get(1));

    // Tuned for speed and stability: robust start, self-accelerating, absolute flow criterion.
    assertTrue(recycle.isAdaptiveAcceleration());
    assertEquals(AccelerationMethod.DIRECT_SUBSTITUTION, recycle.getAccelerationMethod());
    assertTrue(recycle.getAbsoluteFlowTolerance() > 0.0);

    process.run();
    assertTrue(process.solved());
    assertEquals(1000.0,
        splitter.getSplitStream(0).getFlowRate("kg/hr") + separator.getLiquidOutStream().getFlowRate("kg/hr"), 1.0);

    // Already closed - a second call must not add another recycle.
    assertEquals(0, process.makeRecycles().size());
  }

  /**
   * With auto mode on, a single run() seeds and closes the loop without any extra call from the user.
   */
  @Test
  void testAutoRecyclesClosesLoopOnFirstRun() {
    Stream feed = new Stream("feed", createFluid());
    feed.setFlowRate(1000.0, "kg/hr");

    Mixer mixer = new Mixer("inlet mixer");
    mixer.addStream(feed);
    Separator separator = new Separator("separator", mixer.getOutletStream());
    Splitter splitter = new Splitter("gas splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] { 0.9, 0.1 });

    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(mixer);
    process.add(separator);
    process.add(splitter);
    process.run();
    mixer.addStream(splitter.getSplitStream(1));

    process.setAutoRecycles(true);
    process.run();

    assertTrue(process.hasRecycles());
    assertTrue(process.solved());
    assertNotSame(splitter.getSplitStream(1), mixer.getInletStreams().get(1));
  }

  /**
   * A stream fed back from a later area to an earlier one is closed by a recycle in the producing area.
   */
  @Test
  void testMakeRecyclesClosesCrossAreaLoopInProcessModel() {
    Stream feed = new Stream("feed", createFluid());
    feed.setFlowRate(1000.0, "kg/hr");

    Mixer mixer = new Mixer("inlet mixer");
    mixer.addStream(feed);
    Separator separator = new Separator("separator", mixer.getOutletStream());

    ProcessSystem separation = new ProcessSystem("separation");
    separation.add(feed);
    separation.add(mixer);
    separation.add(separator);
    separation.run();

    Splitter splitter = new Splitter("gas splitter", separator.getGasOutStream());
    splitter.setSplitFactors(new double[] { 0.9, 0.1 });
    ProcessSystem gasHandling = new ProcessSystem("gas handling");
    gasHandling.add(splitter);
    gasHandling.run();

    mixer.addStream(splitter.getSplitStream(1));

    ProcessModel plant = new ProcessModel();
    plant.add("separation", separation);
    plant.add("gas handling", gasHandling);
    plant.run();

    List<Recycle> created = plant.makeRecycles();
    assertEquals(1, created.size());

    Recycle recycle = created.get(0);
    assertTrue(gasHandling.getUnitOperations().contains(recycle));
    assertSame(recycle.getOutletStream(), mixer.getInletStreams().get(1));
    assertTrue(recycle.isAdaptiveAcceleration());

    assertTrue(plant.runUntilConverged(30));
    assertEquals(0, plant.makeRecycles().size());
  }
}
