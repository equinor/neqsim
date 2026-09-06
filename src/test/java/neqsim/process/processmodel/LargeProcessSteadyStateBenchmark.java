package neqsim.process.processmodel;

import java.io.BufferedWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Standalone end-to-end steady-state benchmark with raw timing and engineering validation.
 *
 * <p>
 * Arguments: workload (serial, wide, splitter, recycle, cpa, multi-area), mode (cold, unchanged, changed), warmup
 * count, measured count, size, strategy (optimized, sequential, parallel, dataflow), output JSON path. Defaults: serial
 * changed 5 10 8 optimized large-process-benchmark.json. Size means stages for serial/cpa, trains for wide/splitter,
 * loops for recycle, or areas for multi-area. Each train has three thermal stages; each stage has a heater, cooler and
 * valve. Cold measures the first solve of a fresh fixture, excluding construction. Changed alternates feed flow by 1%
 * and temperature by 0.5 K; unchanged reuses an already solved model.
 *
 * <p>
 * Run baseline and candidate in alternating fresh JVM forks, with identical heap, CPU count and arguments. Timings
 * exclude validation and JSON serialization. Per-unit time includes overlapping work in parallel modes and must not be
 * summed as wall time. Main-thread allocation excludes worker allocations; use a JVM profiler for a complete allocation
 * or stack profile. There are no wall-time assertions suitable for CI. Output is written even if an engineering check
 * fails.
 */
public final class LargeProcessSteadyStateBenchmark {
  private static final Logger logger = LogManager.getLogger(LargeProcessSteadyStateBenchmark.class);
  private static final double CONSERVATION_TOLERANCE = 2.0e-6;

  private static final class Fixture {
    final List<ProcessSystem> areas = new ArrayList<>();
    final List<Stream> feeds = new ArrayList<>();
    final List<StreamInterface> products = new ArrayList<>();
    final List<Heater> heaters = new ArrayList<>();
    final List<Compressor> compressors = new ArrayList<>();
    final List<Recycle> recycles = new ArrayList<>();
    ProcessModel model;

    ProcessSystem area(String name) {
      ProcessSystem area = new ProcessSystem(name);
      area.setProfilingEnabled(true);
      areas.add(area);
      return area;
    }

    Stream feed(ProcessSystem area, String name, boolean cpa) {
      Stream feed = new Stream(name, fluid(cpa));
      feed.setFlowRate(12000.0, "kg/hr");
      area.add(feed);
      feeds.add(feed);
      return feed;
    }

    void change(int index) {
      for (Stream feed : feeds) {
        feed.setFlowRate((index & 1) == 0 ? 12000.0 : 12120.0, "kg/hr");
        feed.setTemperature((index & 1) == 0 ? 313.15 : 313.65, "K");
      }
    }

    void run(String strategy) throws InterruptedException {
      if (model != null) {
        model.run();
      } else if ("parallel".equals(strategy)) {
        areas.get(0).runParallel(UUID.randomUUID());
      } else if ("dataflow".equals(strategy)) {
        areas.get(0).runDataflow(UUID.randomUUID());
      } else {
        areas.get(0).run();
      }
    }

    boolean solved() {
      if (model != null && !model.isModelConverged()) {
        return false;
      }
      for (ProcessSystem area : areas) {
        if (!area.solved()) {
          return false;
        }
      }
      return true;
    }

    Map<String, double[]> profile() {
      Map<String, double[]> result = new LinkedHashMap<>();
      for (ProcessSystem area : areas) {
        for (Map.Entry<String, double[]> entry : area.getExecutionProfile().entrySet()) {
          result.put(area.getName() + "::" + entry.getKey(), entry.getValue());
        }
      }
      return result;
    }
  }

  private static SystemInterface fluid(boolean cpa) {
    SystemInterface fluid = cpa ? new SystemSrkCPAstatoil(313.15, 80.0) : new SystemSrkEos(313.15, 80.0);
    String[] names = { "nitrogen", "CO2", "methane", "ethane", "propane", "i-butane", "n-butane", "i-pentane",
        "n-pentane", "n-hexane", "n-heptane", "n-octane" };
    double[] fractions = { 0.01, 0.02, 0.65, 0.10, 0.06, 0.02, 0.03, 0.015, 0.015, 0.03, 0.03, 0.02 };
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[i], fractions[i]);
    }
    if (cpa) {
      fluid.addComponent("water", 0.01);
    }
    fluid.setMixingRule(cpa ? 10 : 2);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  private static StreamInterface thermalStages(Fixture fixture, ProcessSystem area, StreamInterface inlet,
      String prefix, int count, double inletPressure, double pressureDrop) {
    StreamInterface current = inlet;
    for (int stage = 0; stage < count; stage++) {
      Heater heater = new Heater(prefix + " heater " + stage, current);
      heater.setOutTemperature(328.15 + stage % 3);
      fixture.heaters.add(heater);
      area.add(heater);
      Cooler cooler = new Cooler(prefix + " cooler " + stage, heater.getOutletStream());
      cooler.setOutTemperature(298.15 + stage % 3);
      fixture.heaters.add(cooler);
      area.add(cooler);
      ThrottlingValve valve = new ThrottlingValve(prefix + " valve " + stage, cooler.getOutletStream());
      valve.setOutletPressure(inletPressure - pressureDrop * (stage + 1.0) / count, "bara");
      area.add(valve);
      current = valve.getOutletStream();
    }
    return current;
  }

  private static void addProducts(Fixture fixture, ProcessSystem area, StreamInterface inlet, String prefix) {
    Separator separator = new Separator(prefix + " separator", inlet);
    area.add(separator);
    fixture.products.add(separator.getGasOutStream());
    fixture.products.add(separator.getLiquidOutStream());
  }

  private static Fixture create(String workload, int size, String strategy) {
    Fixture fixture = new Fixture();
    if ("multi-area".equals(workload)) {
      fixture.model = new ProcessModel();
      fixture.model.setMaxIterations(8);
      StreamInterface current = null;
      for (int i = 0; i < size; i++) {
        ProcessSystem area = fixture.area("area " + i);
        if (current == null) {
          current = fixture.feed(area, "feed", false);
        }
        // Reset pressure between areas with realistic gas compression after liquid removal.
        Separator separator = new Separator("area " + i + " inlet separator", current);
        area.add(separator);
        fixture.products.add(separator.getLiquidOutStream());
        Compressor compressor = new Compressor("area " + i + " compressor", separator.getGasOutStream());
        compressor.setOutletPressure(90.0, "bara");
        compressor.setIsentropicEfficiency(0.75);
        area.add(compressor);
        fixture.compressors.add(compressor);
        current = thermalStages(fixture, area, compressor.getOutletStream(), "area " + i, 2, 90.0, 10.0);
        if (i == size - 1) {
          addProducts(fixture, area, current, "export");
        }
        fixture.model.add(area.getName(), area);
      }
      fixture.model.setUseOptimizedExecution(!"sequential".equals(strategy));
    } else {
      ProcessSystem area = fixture.area(workload);
      int trains = "wide".equals(workload) || "recycle".equals(workload) || "splitter".equals(workload) ? size : 1;
      Splitter feedSplitter = null;
      if ("splitter".equals(workload)) {
        Stream feed = fixture.feed(area, "common feed", false);
        feedSplitter = new Splitter("feed splitter", feed, trains);
        double[] factors = new double[trains];
        Arrays.fill(factors, 1.0 / trains);
        feedSplitter.setSplitFactors(factors);
        area.add(feedSplitter);
      }
      for (int train = 0; train < trains; train++) {
        String prefix = "train " + train;
        StreamInterface current = feedSplitter == null ? fixture.feed(area, prefix + " feed", "cpa".equals(workload))
            : feedSplitter.getSplitStream(train);
        if ("recycle".equals(workload)) {
          Stream recycleBack = new Stream(prefix + " recycle back", fluid(false));
          recycleBack.setFlowRate(0.0, "kg/hr");
          area.add(recycleBack);
          Mixer mixer = new Mixer(prefix + " mixer");
          mixer.addStream(current);
          mixer.addStream(recycleBack);
          area.add(mixer);
          // An isobaric thermal recycle avoids inventing pressure gain across a heater.
          current = thermalStages(fixture, area, mixer.getOutletStream(), prefix, 3, 80.0, 0.0);
          Splitter splitter = new Splitter(prefix + " splitter", current, 2);
          splitter.setSplitFactors(new double[] { 0.8, 0.2 });
          area.add(splitter);
          Recycle recycle = new Recycle(prefix + " recycle");
          recycle.addStream(splitter.getSplitStream(1));
          recycle.setOutletStream(recycleBack);
          recycle.setTolerance(1.0e-7);
          area.add(recycle);
          fixture.recycles.add(recycle);
          addProducts(fixture, area, splitter.getSplitStream(0), prefix);
        } else {
          int stages = "serial".equals(workload) || "cpa".equals(workload) ? size : 3;
          current = thermalStages(fixture, area, current, prefix, stages, 80.0, 10.0);
          addProducts(fixture, area, current, prefix);
        }
      }
    }
    for (ProcessSystem area : fixture.areas) {
      area.setUseOptimizedExecution(!"sequential".equals(strategy));
    }
    return fixture;
  }

  private static void number(JsonObject object, String name, double value) {
    if (Double.isFinite(value)) {
      object.addProperty(name, value);
    } else {
      object.add(name, null);
    }
  }

  private static JsonObject validate(Fixture fixture) {
    JsonObject result = new JsonObject();
    double inletMass = 0.0;
    double outletMass = 0.0;
    double inletEnthalpy = 0.0;
    double outletEnthalpy = 0.0;
    double duty = 0.0;
    double energyScale = 1.0;
    double checksum = 0.0;
    double[] inletMoles = new double[fixture.feeds.get(0).getThermoSystem().getNumberOfComponents()];
    double[] outletMoles = new double[inletMoles.length];
    JsonArray products = new JsonArray();
    boolean finite = true;
    for (Stream feed : fixture.feeds) {
      inletMass += feed.getFlowRate("kg/hr");
      inletEnthalpy += feed.getThermoSystem().getEnthalpy();
      double[] moles = feed.getThermoSystem().getMolarRate();
      for (int component = 0; component < moles.length; component++) {
        inletMoles[component] += moles[component];
      }
    }
    for (StreamInterface stream : fixture.products) {
      double mass = stream.getFlowRate("kg/hr");
      double temperature = stream.getTemperature("K");
      double pressure = stream.getPressure("bara");
      double enthalpy = stream.getThermoSystem().getEnthalpy();
      finite &= Double.isFinite(mass) && mass >= -1.0e-9 && Double.isFinite(temperature) && temperature > 0.0
          && Double.isFinite(pressure) && pressure > 0.0 && Double.isFinite(enthalpy);
      outletMass += mass;
      outletEnthalpy += enthalpy;
      double[] moles = stream.getThermoSystem().getMolarRate();
      for (int component = 0; component < moles.length; component++) {
        outletMoles[component] += moles[component];
        finite &= Double.isFinite(moles[component]) && moles[component] >= -1.0e-9;
      }
      int phases = stream.getThermoSystem().getNumberOfPhases();
      checksum += mass + temperature + pressure + 1000.0 * phases + enthalpy * 1.0e-6;
      JsonObject product = new JsonObject();
      product.addProperty("name", stream.getName());
      number(product, "massKgHr", mass);
      number(product, "temperatureK", temperature);
      number(product, "pressureBara", pressure);
      number(product, "enthalpyW", enthalpy);
      product.addProperty("phases", phases);
      JsonArray componentMoles = new JsonArray();
      for (double molarRate : moles) {
        componentMoles.add(molarRate);
      }
      product.add("componentMolesPerSecond", componentMoles);
      JsonArray phaseStates = new JsonArray();
      for (int phase = 0; phase < phases; phase++) {
        neqsim.thermo.phase.PhaseInterface phaseState = stream.getThermoSystem().getPhase(phase);
        JsonObject phaseRecord = new JsonObject();
        phaseRecord.addProperty("type", phaseState.getType().toString());
        number(phaseRecord, "beta", stream.getThermoSystem().getBeta(phase));
        number(phaseRecord, "z", phaseState.getZ());
        JsonArray composition = new JsonArray();
        for (int component = 0; component < phaseState.getNumberOfComponents(); component++) {
          composition.add(phaseState.getComponent(component).getx());
        }
        phaseRecord.add("x", composition);
        phaseStates.add(phaseRecord);
      }
      product.add("phaseStates", phaseStates);
      products.add(product);
    }
    for (Heater heater : fixture.heaters) {
      duty += heater.getDuty();
      energyScale += Math.abs(heater.getDuty());
    }
    for (Compressor compressor : fixture.compressors) {
      duty += compressor.getPower();
      energyScale += Math.abs(compressor.getPower());
    }
    double componentError = 0.0;
    for (int i = 0; i < inletMoles.length; i++) {
      componentError = Math.max(componentError,
          Math.abs(inletMoles[i] - outletMoles[i]) / Math.max(1.0e-12, Math.abs(inletMoles[i])));
    }
    double massError = Math.abs(inletMass - outletMass) / Math.max(1.0, inletMass);
    double energyError = Math.abs(outletEnthalpy - inletEnthalpy - duty)
        / Math.max(energyScale, Math.max(Math.abs(inletEnthalpy), Math.abs(outletEnthalpy)));
    boolean solved = fixture.solved();
    result.addProperty("solved", solved);
    result.addProperty("finitePhysicalState", finite);
    number(result, "feedMassKgHr", inletMass);
    number(result, "productMassKgHr", outletMass);
    number(result, "massRelativeError", massError);
    number(result, "maximumComponentRelativeError", componentError);
    number(result, "energyRelativeError", energyError);
    number(result, "netDutyW", duty);
    number(result, "checksum", checksum);
    result.addProperty("modelIterations", fixture.model == null ? 0 : fixture.model.getLastIterationCount());
    int recycleIterations = 0;
    for (Recycle recycle : fixture.recycles) {
      recycleIterations += recycle.getIterations();
    }
    result.addProperty("recycleIterations", recycleIterations);
    result.add("products", products);
    result.addProperty("validationPassed", solved && finite && massError < CONSERVATION_TOLERANCE
        && componentError < CONSERVATION_TOLERANCE && energyError < 1.0e-5);
    return result;
  }

  private static JsonObject profile(Fixture fixture, Map<String, double[]> before) {
    JsonObject result = new JsonObject();
    for (Map.Entry<String, double[]> entry : fixture.profile().entrySet()) {
      double[] previous = before.get(entry.getKey());
      double milliseconds = entry.getValue()[0] - (previous == null ? 0.0 : previous[0]);
      double calls = entry.getValue()[1] - (previous == null ? 0.0 : previous[1]);
      JsonObject value = new JsonObject();
      number(value, "milliseconds", milliseconds);
      number(value, "calls", calls);
      result.add(entry.getKey(), value);
    }
    return result;
  }

  private static long allocatedBytes() {
    java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    if (bean instanceof com.sun.management.ThreadMXBean) {
      com.sun.management.ThreadMXBean allocationBean = (com.sun.management.ThreadMXBean) bean;
      if (allocationBean.isThreadAllocatedMemorySupported() && allocationBean.isThreadAllocatedMemoryEnabled()) {
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
      }
    }
    return -1L;
  }

  /**
   * Runs one benchmark fork and writes machine-readable samples before reporting validation failures.
   *
   * @param args positional benchmark options described in the class Javadoc
   * @throws Exception if execution, validation or output fails
   */
  public static void main(String[] args) throws Exception {
    String workload = args.length > 0 ? args[0] : "serial";
    String mode = args.length > 1 ? args[1] : "changed";
    int warmups = args.length > 2 ? Integer.parseInt(args[2]) : 5;
    int measured = args.length > 3 ? Integer.parseInt(args[3]) : 10;
    int size = args.length > 4 ? Integer.parseInt(args[4]) : 8;
    String strategy = args.length > 5 ? args[5] : "optimized";
    Path output = Paths.get(args.length > 6 ? args[6] : "large-process-benchmark.json");
    if (!Arrays.asList("serial", "wide", "splitter", "recycle", "cpa", "multi-area").contains(workload)
        || !Arrays.asList("cold", "unchanged", "changed").contains(mode)
        || !Arrays.asList("optimized", "sequential", "parallel", "dataflow").contains(strategy) || warmups < 0
        || measured < 1 || size < 1) {
      throw new IllegalArgumentException("Invalid workload, mode, strategy or count; see class Javadoc");
    }
    if (("multi-area".equals(workload) || "recycle".equals(workload))
        && ("parallel".equals(strategy) || "dataflow".equals(strategy))) {
      throw new IllegalArgumentException("Use optimized or sequential for multi-area/recycle workloads");
    }
    boolean cold = "cold".equals(mode);
    Fixture fixture = create(workload, size, strategy);
    if (!cold) {
      fixture.run(strategy);
    }
    JsonArray samples = new JsonArray();
    int invalid = 0;
    int unitCount = 0;
    for (int index = -warmups; index < measured; index++) {
      if (cold) {
        fixture = create(workload, size, strategy);
      } else if ("changed".equals(mode)) {
        fixture.change(index + warmups + 1);
      }
      boolean cumulativeProfile = "parallel".equals(strategy) || "dataflow".equals(strategy);
      Map<String, double[]> before = cumulativeProfile ? fixture.profile() : new LinkedHashMap<>();
      long allocatedBefore = allocatedBytes();
      long start = System.nanoTime();
      fixture.run(strategy);
      long elapsed = System.nanoTime() - start;
      long allocatedAfter = allocatedBytes();
      if (index >= 0) {
        JsonObject sample = validate(fixture);
        sample.addProperty("sample", index);
        sample.addProperty("elapsedNs", elapsed);
        sample.addProperty("mainThreadAllocatedBytes",
            allocatedBefore < 0 || allocatedAfter < 0 ? -1L : allocatedAfter - allocatedBefore);
        sample.add("unitProfile", profile(fixture, before));
        samples.add(sample);
        if (!sample.get("validationPassed").getAsBoolean()) {
          invalid++;
        }
      }
    }
    for (ProcessSystem area : fixture.areas) {
      unitCount += area.getUnitOperations().size();
    }
    JsonObject report = new JsonObject();
    report.addProperty("workload", workload);
    report.addProperty("mode", mode);
    report.addProperty("strategy", strategy);
    report.addProperty("size", size);
    report.addProperty("units", unitCount);
    report.addProperty("areas", fixture.areas.size());
    report.addProperty("warmups", warmups);
    report.addProperty("measured", measured);
    report.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
    report.addProperty("javaVersion", System.getProperty("java.version"));
    report.addProperty("osName", System.getProperty("os.name"));
    report.addProperty("osArch", System.getProperty("os.arch"));
    report.addProperty("vmName", System.getProperty("java.vm.name"));
    report.addProperty("profilingEnabled", true);
    JsonArray collectors = new JsonArray();
    for (java.lang.management.GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      collectors.add(collector.getName());
    }
    report.add("garbageCollectors", collectors);
    report.addProperty("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.addProperty("mainThreadAllocationOnly", true);
    report.addProperty("unitProfileScope",
        fixture.model == null ? "one process solve" : "last call to each area; model iterations are not accumulated");
    report.addProperty("validationFailures", invalid);
    report.add("samples", samples);
    Path parent = output.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
    }
    logger.info("Benchmark {} {}: {} units, {} samples, {} validation failures; output {}", workload, mode, unitCount,
        measured, invalid, output);
    if (invalid != 0) {
      throw new IllegalStateException("Benchmark engineering validation failed for " + invalid + " samples");
    }
  }

  private LargeProcessSteadyStateBenchmark() {
  }
}
