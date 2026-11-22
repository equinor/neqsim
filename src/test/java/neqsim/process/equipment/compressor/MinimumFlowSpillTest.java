package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Material and energy qualification of algebraic compressor spill.
 *
 * @author NeqSim
 * @version 1.0
 */
class MinimumFlowSpillTest {
  /**
   * Creates a synthetic methane/ethane suction feed.
   *
   * @param flow flow in kg/sec
   * @return initialized feed
   */
  private Stream feed(double flow) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 10.0);
    fluid.addComponent("methane", 0.9);
    fluid.addComponent("ethane", 0.1);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(flow, "kg/sec");
    feed.run();
    return feed;
  }

  /** Verifies repeated below/above-minimum runs, component balance and gross-flow power. */
  @Test
  void spillClosesMassAndEnergyWithoutRecycle() {
    Stream feed = feed(0.2);
    MinimumFlowSpill spill = new MinimumFlowSpill("LP compressor", feed);
    spill.setMinimumInletFlow(10.0, "kg/sec");
    spill.getCompressor().setOutletPressure(30.0);
    spill.getCompressor().setIsentropicEfficiency(0.75);
    ProcessSystem process = new ProcessSystem("compression");
    process.add(feed);
    process.add(spill);
    assertTrue(process.runUntilConverged(3));
    assertEquals(10.0, spill.getSuctionStream().getFlowRate("kg/sec"), 1e-8);
    assertEquals(9.8, spill.getSpillFlow("kg/sec"), 1e-8);
    assertEquals(0.2, spill.getNetFlow("kg/sec"), 1e-8);
    assertEquals(0.2, spill.getOutletStream().getFlowRate("kg/sec"), 1e-8);
    assertEquals(0.0, spill.getMassBalance("kg/sec"), 1e-8);
    assertEquals(1, spill.getOutletStreams().size());
    for (int i = 0; i < 2; i++) {
      assertEquals(feed.getFluid().getComponent(i).getNumberOfmoles(),
          spill.getOutletStream().getFluid().getComponent(i).getNumberOfmoles(), 1e-8);
    }
    assertEquals(spill.getPower("kW"), process.getPower("kW"), 1e-8);
    assertEquals(-spill.getSpillCoolingDuty("kW"), process.getCoolerDuty("kW"), 1e-8);
    double externalEnthalpyRise = spill.getOutletStream().getFluid().getEnthalpy() - feed.getFluid().getEnthalpy();
    assertEquals(spill.getPower("W") - spill.getSpillCoolingDuty("W"), externalEnthalpyRise, 1e-4,
        "feed H=" + feed.getFluid().getEnthalpy() + "; suction H=" + spill.getSuctionStream().getFluid().getEnthalpy()
            + "; inlet H=" + spill.getCompressor().inletEnthalpy + "; discharge H="
            + spill.getCompressor().getOutletStream().getFluid().getEnthalpy() + "; forward H="
            + spill.getOutletStream().getFluid().getEnthalpy());
    Compressor reference = new Compressor("reference", feed(10.0));
    reference.setOutletPressure(30.0);
    reference.setIsentropicEfficiency(0.75);
    reference.run();
    assertEquals(reference.getPower("kW"), spill.getPower("kW"), 1e-5);
    feed.setFlowRate(12.0, "kg/sec");
    process.run();
    assertEquals(0.0, spill.getSpillFlow("kg/sec"), 1e-8);
    assertEquals(12.0, spill.getOutletStream().getFlowRate("kg/sec"), 1e-8);
    assertEquals(0.0, spill.getSpillCoolingDuty("W"), 1e-8);
    feed.setFlowRate(0.2, "kg/sec");
    process.run();
    assertEquals(9.8, spill.getSpillFlow("kg/sec"), 1e-8);
  }

  /** Verifies actual-volume minimum specification and suction-basis spill reporting. */
  @Test
  void actualVolumeUsesSuctionState() {
    Stream feed = feed(1.0);
    MinimumFlowSpill spill = new MinimumFlowSpill("compressor", feed);
    spill.setMinimumInletFlow(2.0 * feed.getFlowRate("m3/hr"), "m3/hr");
    spill.getCompressor().setOutletPressure(30.0);
    spill.run();
    assertEquals(2.0, spill.getSuctionStream().getFlowRate("kg/sec"), 1e-6);
    assertEquals(feed.getFlowRate("m3/hr"), spill.getSpillFlow("m3/hr"), 1e-6);
    assertEquals(1.0, spill.getOutletStream().getFlowRate("kg/sec"), 1e-8);
  }

  /**
   * Verifies closed forward flow, reopening and serialization.
   *
   * @throws Exception if serialization fails
   */
  @Test
  void zeroNetAndSerializedAssembly() throws Exception {
    Stream feed = feed(0.2);
    MinimumFlowSpill spill = new MinimumFlowSpill("compressor", feed);
    spill.setMinimumInletFlow(10.0, "kg/sec");
    spill.getCompressor().setOutletPressure(30.0);
    feed.setFlowRate(0.0, "kg/sec");
    spill.run();
    assertEquals(10.0, spill.getSpillFlow("kg/sec"), 1e-6);
    assertEquals(0.0, spill.getOutletStream().getFlowRate("kg/sec"), 1e-10);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(spill);
    }
    MinimumFlowSpill restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (MinimumFlowSpill) input.readObject();
    }
    restored.run();
    assertEquals(10.0, restored.getSpillFlow("kg/sec"), 1e-6);
    assertEquals(0.0, restored.getOutletStream().getFlowRate("kg/sec"), 1e-10);
    assertEquals(0.0, restored.getMassBalance("kg/sec"), 1e-8);
    restored.setMinimumInletFlow(0.0, "kg/sec");
    restored.run();
    assertEquals(0.0, restored.getPower("kW"), 1e-10);
  }

  /** Verifies invalid minimums and competing anti-surge flow modification are rejected. */
  @Test
  void rejectsInvalidInputs() {
    MinimumFlowSpill spill = new MinimumFlowSpill("compressor", feed(1.0));
    assertThrows(IllegalArgumentException.class, () -> spill.setMinimumInletFlow(-1.0, "kg/sec"));
    assertThrows(IllegalArgumentException.class, () -> spill.setMinimumInletFlow(Double.NaN, "kg/sec"));
    spill.getCompressor().getAntiSurge().setActive(true);
    assertThrows(IllegalStateException.class, () -> spill.run());
  }
}
