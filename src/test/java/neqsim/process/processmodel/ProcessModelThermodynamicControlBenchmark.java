package neqsim.process.processmodel;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Four-area thermodynamic control for ProcessModel orchestration changes. */
public final class ProcessModelThermodynamicControlBenchmark {
  private static final class Fixture {
    final ProcessModel model;
    final Stream feed;
    final Separator outlet;

    Fixture(ProcessModel model, Stream feed, Separator outlet) {
      this.model = model;
      this.feed = feed;
      this.outlet = outlet;
    }
  }

  private static SystemInterface createFluid(boolean cpa) {
    SystemInterface fluid = cpa ? new SystemSrkCPAstatoil(303.15, 60.0) : new SystemSrkEos(303.15, 60.0);
    fluid.addComponent("methane", 0.82);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-heptane", 0.04);
    fluid.addComponent(cpa ? "water" : "nC10", 0.01);
    fluid.setMixingRule(cpa ? 10 : 2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static Fixture createFixture(boolean cpa) {
    Stream feed = new Stream("feed", createFluid(cpa));
    feed.setFlowRate(50000.0, "kg/hr");

    Cooler inletCooler = new Cooler("inlet cooler", feed);
    inletCooler.setOutTemperature(300.15);
    Separator inletSeparator = new Separator("inlet separator", inletCooler.getOutletStream());
    ProcessSystem inletArea = new ProcessSystem("inlet area");
    inletArea.add(feed);
    inletArea.add(inletCooler);
    inletArea.add(inletSeparator);

    Compressor firstCompressor = new Compressor("first compressor", inletSeparator.getGasOutStream());
    firstCompressor.setOutletPressure(90.0, "bara");
    Cooler firstAftercooler = new Cooler("first aftercooler", firstCompressor.getOutletStream());
    firstAftercooler.setOutTemperature(310.15);
    Separator firstScrubber = new Separator("first scrubber", firstAftercooler.getOutletStream());
    ProcessSystem firstCompressionArea = new ProcessSystem("first compression area");
    firstCompressionArea.add(firstCompressor);
    firstCompressionArea.add(firstAftercooler);
    firstCompressionArea.add(firstScrubber);

    Compressor secondCompressor = new Compressor("second compressor", firstScrubber.getGasOutStream());
    secondCompressor.setOutletPressure(125.0, "bara");
    Cooler secondAftercooler = new Cooler("second aftercooler", secondCompressor.getOutletStream());
    secondAftercooler.setOutTemperature(305.15);
    Separator secondScrubber = new Separator("second scrubber", secondAftercooler.getOutletStream());
    ProcessSystem secondCompressionArea = new ProcessSystem("second compression area");
    secondCompressionArea.add(secondCompressor);
    secondCompressionArea.add(secondAftercooler);
    secondCompressionArea.add(secondScrubber);

    ThrottlingValve deliveryValve = new ThrottlingValve("delivery valve", secondScrubber.getGasOutStream());
    deliveryValve.setOutletPressure(100.0, "bara");
    Heater deliveryHeater = new Heater("delivery heater", deliveryValve.getOutletStream());
    deliveryHeater.setOutTemperature(315.15);
    Separator deliverySeparator = new Separator("delivery separator", deliveryHeater.getOutletStream());
    ProcessSystem deliveryArea = new ProcessSystem("delivery area");
    deliveryArea.add(deliveryValve);
    deliveryArea.add(deliveryHeater);
    deliveryArea.add(deliverySeparator);

    ProcessModel model = new ProcessModel();
    model.setMaxIterations(6);
    model.add("inlet", inletArea);
    model.add("compression-1", firstCompressionArea);
    model.add("compression-2", secondCompressionArea);
    model.add("delivery", deliveryArea);
    model.run();
    return new Fixture(model, feed, deliverySeparator);
  }

  private static double checksum(Fixture fixture) {
    StreamInterface gas = fixture.outlet.getGasOutStream();
    return gas.getFlowRate("kg/hr") + gas.getTemperature("K") + gas.getPressure("bara")
        + 1000.0 * gas.getThermoSystem().getNumberOfPhases() + fixture.model.getLastIterationCount()
        + fixture.model.getLastMassClosureError() + fixture.model.getTotalFeedFlowRate();
  }

  private static int parseIntOrDefault(String value, int defaultValue) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      return defaultValue;
    }
  }

  public static void main(String[] args) {
    int warmups = args.length > 0 ? parseIntOrDefault(args[0], 8) : 8;
    int measured = args.length > 1 ? parseIntOrDefault(args[1], 20) : 20;
    String mode = args.length > 2 ? args[2] : "stable";
    boolean cpa = "cpa".equals(mode);
    boolean nearby = "nearby".equals(mode);
    Fixture fixture = createFixture(cpa);
    for (int i = 0; i < warmups; i++) {
      if (nearby) {
        fixture.feed.setFlowRate((i & 1) == 0 ? 50000.0 : 50500.0, "kg/hr");
      }
      fixture.model.run();
    }

    com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long allocatedBefore = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    long start = System.nanoTime();
    double checksum = 0.0;
    int nonConverged = 0;
    for (int i = 0; i < measured; i++) {
      if (nearby) {
        fixture.feed.setFlowRate((i & 1) == 0 ? 50000.0 : 50500.0, "kg/hr");
      }
      fixture.model.run();
      checksum += checksum(fixture);
      if (!fixture.model.isModelConverged()) {
        nonConverged++;
      }
    }
    long elapsed = System.nanoTime() - start;
    long allocatedAfter = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    StreamInterface outlet = fixture.outlet.getGasOutStream();
    System.out.printf(Locale.US,
        "mode=%s nsPerRun=%.3f bytesPerRun=%.3f checksum=%.12f iterations=%d boundaries=%d nonConverged=%d massError=%.12g outletFlow=%.12f outletTemperature=%.12f outletPressure=%.12f phases=%d feed=%.12f%n",
        mode, elapsed / (double) measured, (allocatedAfter - allocatedBefore) / (double) measured, checksum,
        fixture.model.getLastIterationCount(), fixture.model.getLastBoundaryStreamErrors().size(), nonConverged,
        fixture.model.getLastMassClosureError(), outlet.getFlowRate("kg/hr"), outlet.getTemperature("K"),
        outlet.getPressure("bara"), outlet.getThermoSystem().getNumberOfPhases(), fixture.model.getTotalFeedFlowRate());
  }
}
