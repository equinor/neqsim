package neqsim.process.processmodel;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Large lightweight ProcessModel benchmark for plant feed-boundary discovery. */
public final class ProcessModelFeedBoundaryBenchmark {
  private static final int AREA_COUNT = Integer.getInteger("areas", 10);
  private static final int UNITS_PER_AREA = Integer.getInteger("unitsPerArea", 60);

  private static final class TopologyUnit extends ProcessEquipmentBaseClass {
    private static final long serialVersionUID = 1L;
    private final List<StreamInterface> inlets;
    private final StreamInterface outlet;

    TopologyUnit(String name, List<StreamInterface> inlets, StreamInterface outlet) {
      super(name);
      this.inlets = inlets;
      this.outlet = outlet;
    }

    @Override
    public void run(UUID id) {
      setCalculationIdentifier(id);
    }

    @Override
    public List<StreamInterface> getInletStreams() {
      return inlets;
    }

    @Override
    public List<StreamInterface> getOutletStreams() {
      return Collections.singletonList(outlet);
    }
  }

  private static final class LightweightArea extends ProcessSystem {
    private static final long serialVersionUID = 1L;
    private final AtomicLong runs = new AtomicLong();

    LightweightArea(String name) {
      super(name);
    }

    @Override
    public void runOptimized(UUID id) {
      runs.incrementAndGet();
      setCalculationIdentifier(id);
    }

    @Override
    public boolean solved() {
      return true;
    }
  }

  private static final class Fixture {
    final ProcessModel model;
    final List<Stream> feeds;
    final List<LightweightArea> areas;

    Fixture(ProcessModel model, List<Stream> feeds, List<LightweightArea> areas) {
      this.model = model;
      this.feeds = feeds;
      this.areas = areas;
    }
  }

  private static Stream newStream(String name, double flow) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 60.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.06);
    fluid.addComponent("propane", 0.04);
    fluid.setMixingRule("classic");
    Stream stream = new Stream(name, fluid);
    stream.setFlowRate(flow, "kg/hr");
    return stream;
  }

  private static Fixture createFixture() {
    ProcessModel model = new ProcessModel();
    model.setMaxIterations(4);
    List<Stream> feeds = new ArrayList<>();
    List<LightweightArea> areas = new ArrayList<>();
    StreamInterface upstreamBoundary = null;
    for (int areaIndex = 0; areaIndex < AREA_COUNT; areaIndex++) {
      LightweightArea area = new LightweightArea("area-" + areaIndex);
      Stream feed = newStream("feed-" + areaIndex, 5000.0 + areaIndex);
      feeds.add(feed);
      area.add(feed);
      if (upstreamBoundary != null) {
        area.add(upstreamBoundary);
      }
      StreamInterface current = feed;
      for (int unitIndex = 0; unitIndex < UNITS_PER_AREA; unitIndex++) {
        Stream outlet = newStream("area-" + areaIndex + "-stream-" + unitIndex, feed.getFlowRate("kg/hr"));
        List<StreamInterface> inlets = new ArrayList<>();
        inlets.add(current);
        if (unitIndex == 0 && upstreamBoundary != null) {
          inlets.add(upstreamBoundary);
        }
        area.add(new TopologyUnit("area-" + areaIndex + "-unit-" + unitIndex, inlets, outlet));
        current = outlet;
      }
      upstreamBoundary = current;
      areas.add(area);
      model.add(area.getName(), area);
    }
    model.run();
    return new Fixture(model, feeds, areas);
  }

  private static double checksum(Fixture fixture) {
    double sum = fixture.model.getTotalFeedFlowRate();
    sum += fixture.model.getLastIterationCount();
    sum += fixture.model.getLastBoundaryStreamErrors().size();
    sum += fixture.model.getLastMassClosureError();
    for (LightweightArea area : fixture.areas) {
      sum += area.runs.get() * 1.0e-9;
    }
    return sum;
  }

  public static void main(String[] args) {
    int warmups = args.length > 0 ? Integer.parseInt(args[0]) : 100;
    int measured = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
    String mode = args.length > 2 ? args[2] : "stable";
    Fixture fixture = createFixture();
    boolean nearby = "nearby".equals(mode);
    boolean direct = "direct".equals(mode);
    for (int i = 0; i < warmups; i++) {
      if (nearby) {
        fixture.feeds.get(0).setFlowRate((i & 1) == 0 ? 5000.0 : 5050.0, "kg/hr");
      }
      if (direct) {
        fixture.model.getTotalFeedFlowRate();
      } else {
        fixture.model.run();
      }
    }

    com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    long allocatedBefore = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    long start = System.nanoTime();
    double checksum = 0.0;
    for (int i = 0; i < measured; i++) {
      if (nearby) {
        fixture.feeds.get(0).setFlowRate((i & 1) == 0 ? 5000.0 : 5050.0, "kg/hr");
      }
      if (direct) {
        checksum += fixture.model.getTotalFeedFlowRate();
      } else {
        fixture.model.run();
        checksum += checksum(fixture);
      }
    }
    long elapsed = System.nanoTime() - start;
    long allocatedAfter = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    System.out.printf(Locale.US,
        "mode=%s areas=%d units=%d nsPerRun=%.3f bytesPerRun=%.3f checksum=%.12f feed=%.12f iterations=%d boundaries=%d converged=%s massError=%.12g%n",
        mode, AREA_COUNT, AREA_COUNT * UNITS_PER_AREA, elapsed / (double) measured,
        (allocatedAfter - allocatedBefore) / (double) measured, checksum, fixture.model.getTotalFeedFlowRate(),
        fixture.model.getLastIterationCount(), fixture.model.getLastBoundaryStreamErrors().size(),
        fixture.model.isModelConverged(), fixture.model.getLastMassClosureError());
  }
}
