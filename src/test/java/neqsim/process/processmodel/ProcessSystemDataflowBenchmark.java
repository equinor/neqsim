package neqsim.process.processmodel;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/** Compares ProcessSystem automatic, level-parallel, and dataflow dispatch on independent trains. */
public final class ProcessSystemDataflowBenchmark {
  private static final int TRAIN_COUNT = 4;

  private static final class Fixture {
    final ProcessSystem process;
    final List<Stream> feeds;
    final List<Separator> products;

    Fixture(ProcessSystem process, List<Stream> feeds, List<Separator> products) {
      this.process = process;
      this.feeds = feeds;
      this.products = products;
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

  private static Fixture createFixture(boolean cpa, boolean imbalanced, boolean multiInput)
      throws InterruptedException {
    ProcessSystem process = new ProcessSystem("wide independent process");
    List<Stream> feeds = new ArrayList<>();
    List<Separator> products = new ArrayList<>();
    for (int train = 0; train < TRAIN_COUNT; train++) {
      String prefix = "train-" + train + " ";
      Stream feed = new Stream(prefix + "feed", createFluid(cpa));
      feed.setFlowRate(12000.0 + 1000.0 * train, "kg/hr");
      process.add(feed);
      feeds.add(feed);

      StreamInterface current = feed;
      if (multiInput) {
        Stream secondFeed = new Stream(prefix + "second feed", createFluid(cpa));
        secondFeed.setTemperature(295.15 + train, "K");
        secondFeed.setFlowRate(3000.0 + 250.0 * train, "kg/hr");
        process.add(secondFeed);
        feeds.add(secondFeed);
        Mixer mixer = new Mixer(prefix + "mixer");
        mixer.addStream(feed);
        mixer.addStream(secondFeed);
        process.add(mixer);
        current = mixer.getOutletStream();
      }
      int thermalPairs = imbalanced ? train + 1 : 3;
      for (int stage = 0; stage < thermalPairs; stage++) {
        Heater heater = new Heater(prefix + "heater-" + stage, current);
        heater.setOutTemperature(308.15 + 2.0 * stage);
        process.add(heater);
        Cooler cooler = new Cooler(prefix + "cooler-" + stage, heater.getOutletStream());
        cooler.setOutTemperature(300.15 + stage);
        process.add(cooler);
        current = cooler.getOutletStream();
      }

      ThrottlingValve valve = new ThrottlingValve(prefix + "valve", current);
      valve.setOutletPressure(45.0 - train, "bara");
      process.add(valve);
      Separator separator = new Separator(prefix + "separator", valve.getOutletStream());
      process.add(separator);
      products.add(separator);
    }
    process.runParallel(new UUID(0L, 1L));
    return new Fixture(process, feeds, products);
  }

  private static void run(Fixture fixture, String strategy, UUID id) throws Exception {
    if ("parallel".equals(strategy)) {
      fixture.process.runParallel(id);
    } else if ("dataflow".equals(strategy)) {
      fixture.process.runDataflow(id);
    } else {
      fixture.process.runOptimized(id);
    }
  }

  private static double checksum(Fixture fixture) {
    double checksum = 0.0;
    for (Separator product : fixture.products) {
      StreamInterface gas = product.getGasOutStream();
      StreamInterface liquid = product.getLiquidOutStream();
      checksum += gas.getFlowRate("kg/hr") + liquid.getFlowRate("kg/hr");
      checksum += gas.getTemperature("K") + gas.getPressure("bara");
      checksum += 1000.0 * gas.getThermoSystem().getNumberOfPhases();
    }
    return checksum;
  }

  /**
   * Runs one warmed benchmark fork. Alternate fresh JVMs for representative comparisons.
   *
   * @param args warmups, measured runs, strategy, topology, fluid, and optional mixer flag
   * @throws Exception if process execution fails
   */
  public static void main(String[] args) throws Exception {
    int warmups = args.length > 0 ? Integer.parseInt(args[0]) : 40;
    int measured = args.length > 1 ? Integer.parseInt(args[1]) : 160;
    String strategy = args.length > 2 ? args[2] : "optimized";
    boolean imbalanced = args.length > 3 && "imbalanced".equals(args[3]);
    boolean cpa = args.length > 4 && "cpa".equals(args[4]);
    boolean multiInput = args.length > 5 && "mixer".equals(args[5]);
    Fixture fixture = createFixture(cpa, imbalanced, multiInput);

    for (int i = 0; i < warmups; i++) {
      for (int feedIndex = 0; feedIndex < fixture.feeds.size(); feedIndex++) {
        double baseFlow = fixture.feeds.get(feedIndex).getName().contains("second feed")
            ? 3000.0 + 250.0 * (feedIndex / (multiInput ? 2 : 1))
            : 12000.0 + 1000.0 * (feedIndex / (multiInput ? 2 : 1));
        fixture.feeds.get(feedIndex).setFlowRate(baseFlow + ((i & 1) == 0 ? 0.0 : 50.0), "kg/hr");
      }
      run(fixture, strategy, new UUID(1L, i + 2L));
    }

    com.sun.management.ThreadMXBean threadBean =
        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long allocatedBefore = threadBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    long start = System.nanoTime();
    double checksum = 0.0;
    for (int i = 0; i < measured; i++) {
      for (int feedIndex = 0; feedIndex < fixture.feeds.size(); feedIndex++) {
        double baseFlow = fixture.feeds.get(feedIndex).getName().contains("second feed")
            ? 3000.0 + 250.0 * (feedIndex / (multiInput ? 2 : 1))
            : 12000.0 + 1000.0 * (feedIndex / (multiInput ? 2 : 1));
        fixture.feeds.get(feedIndex).setFlowRate(baseFlow + ((i & 1) == 0 ? 0.0 : 50.0), "kg/hr");
      }
      run(fixture, strategy, new UUID(2L, i + 2L));
      checksum += checksum(fixture);
    }
    long elapsed = System.nanoTime() - start;
    long allocatedAfter = threadBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    System.out.printf(Locale.US,
        "strategy=%s topology=%s fluid=%s units=%d nsPerRun=%.3f bytesPerRun=%.3f checksum=%.12f solved=%s%n",
        strategy, (imbalanced ? "imbalanced" : "balanced") + (multiInput ? "-mixer" : ""),
        cpa ? "cpa" : "srk",
        fixture.process.getUnitOperations().size(), elapsed / (double) measured,
        (allocatedAfter - allocatedBefore) / (double) measured, checksum, fixture.process.solved());
  }
}
