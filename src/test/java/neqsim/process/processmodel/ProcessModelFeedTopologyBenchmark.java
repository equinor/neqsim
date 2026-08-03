package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.util.Recycle;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Forked timing harness for the model-level feed-topology scan used by automatic convergence tuning.
 *
 * <p>
 * Run explicitly with
 * {@code ./mvnw test -Dtest=ProcessModelFeedTopologyBenchmark -Dneqsim.benchmark.feedTopology=true}. The model has ten
 * process areas, 500 registered stream units, nine cross-area links, and a real tail recycle. Dormant streams model
 * sections excluded by the optimized low-flow/bypass path and remain registered for topology discovery. Timing starts
 * only after the active process, recycle, and topology plan are warm. Normal test runs skip the timing work.
 * </p>
 */
public class ProcessModelFeedTopologyBenchmark {
  private static final Logger logger = LogManager.getLogger(ProcessModelFeedTopologyBenchmark.class);
  private static final int AREA_COUNT = 10;
  private static final int STREAMS_PER_AREA = 50;
  private static final int WARMUP_RUNS = 10;
  private static final int BATCH_COUNT = 7;
  private static final int RUNS_PER_BATCH = 20;

  /** Creates the small single-phase SRK gas used by every synthetic feed stream. */
  private static SystemInterface createGasFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");
    return fluid;
  }

  /** Creates an initialized stream with a distinct thermodynamic system. */
  private static Stream createStream(String name, double flowKgPerHour) {
    Stream stream = new Stream(name, createGasFluid());
    stream.setFlowRate(flowKgPerHour, "kg/hr");
    stream.setTemperature(25.0, "C");
    stream.setPressure(50.0, "bara");
    return stream;
  }

  /** Creates a registered stream that the optimized runner can skip as a dormant section. */
  private static Stream createDormantStream(String name, double flowKgPerHour) {
    Stream stream = createStream(name, flowKgPerHour);
    stream.setLockedInactive(true);
    return stream;
  }

  /**
   * Builds the multi-area workload required by issue 2776.
   *
   * @return warmed-capable process model
   */
  private static ProcessModel buildModel() {
    ProcessModel model = new ProcessModel();
    StreamInterface crossAreaStream = null;
    for (int areaIndex = 0; areaIndex < AREA_COUNT; areaIndex++) {
      ProcessSystem area = new ProcessSystem("area " + areaIndex);
      int dormantCount;
      if (areaIndex == 0) {
        Stream plantFeed = createStream("plant feed", 100000.0);
        area.add(plantFeed);
        crossAreaStream = plantFeed;
        dormantCount = STREAMS_PER_AREA - 1;
      } else {
        area.add(crossAreaStream);
        dormantCount = STREAMS_PER_AREA - 1;
      }

      Stream recycleSeed = null;
      if (areaIndex == AREA_COUNT - 1) {
        recycleSeed = createStream("recycle seed", 100.0);
        area.add(recycleSeed);
        dormantCount--;
      }
      for (int streamIndex = 0; streamIndex < dormantCount; streamIndex++) {
        area.add(createDormantStream("area " + areaIndex + " dormant " + streamIndex, 1.0 + streamIndex));
      }

      if (areaIndex < AREA_COUNT - 1) {
        Heater transfer = new Heater("area " + areaIndex + " transfer", crossAreaStream);
        transfer.setOutTemperature(298.15);
        area.add(transfer);
        crossAreaStream = transfer.getOutletStream();
      } else {
        Mixer mixer = new Mixer("tail mixer");
        mixer.addStream(crossAreaStream);
        mixer.addStream(recycleSeed);
        area.add(mixer);

        Splitter splitter = new Splitter("tail splitter", mixer.getOutletStream(), 2);
        splitter.setSplitFactors(new double[] { 0.99, 0.01 });
        area.add(splitter);

        Recycle recycle = new Recycle("tail recycle");
        recycle.addStream(splitter.getSplitStream(1));
        recycle.setOutletStream(recycleSeed);
        recycle.setTolerance(1.0e-5);
        area.add(recycle);
      }
      model.add(area.getName(), area);
    }
    model.setUseAdaptiveModelParallelism(false);
    model.setPreventNestedParallelExecution(true);
    model.setMaxIterations(25);
    return model;
  }

  /** Counts registered stream memberships in all child process areas. */
  private static int countRegisteredStreams(ProcessModel model) {
    int count = 0;
    for (ProcessSystem area : model.getAllProcesses()) {
      for (Object unit : area.getUnitOperations()) {
        if (unit instanceof StreamInterface) {
          count++;
        }
      }
    }
    return count;
  }

  /** Runs a stable fork-local median benchmark when explicitly enabled. */
  @Test
  void benchmarkWarmedProcessModelRuns() {
    if (!Boolean.getBoolean("neqsim.benchmark.feedTopology")) {
      return;
    }
    ProcessModel model = buildModel();
    assertEquals(AREA_COUNT, model.getAllProcesses().size());
    assertEquals(AREA_COUNT * STREAMS_PER_AREA, countRegisteredStreams(model));
    assertTrue(model.runUntilConverged(25, 1.0e-6), "benchmark model should converge");
    assertEquals(100000.0, model.getTotalFeedFlowRate(), 1.0e-8);

    for (int warmup = 0; warmup < WARMUP_RUNS; warmup++) {
      model.run();
      assertTrue(model.isModelConverged(), "warm benchmark run should converge");
    }

    double[] batchNanosPerRun = new double[BATCH_COUNT];
    double checksum = 0.0;
    for (int batch = 0; batch < BATCH_COUNT; batch++) {
      long start = System.nanoTime();
      for (int run = 0; run < RUNS_PER_BATCH; run++) {
        model.run();
        checksum += model.getTotalFeedFlowRate();
        checksum += model.getLastIterationCount();
        checksum += model.getLastMassClosureError();
      }
      batchNanosPerRun[batch] = (System.nanoTime() - start) / (double) RUNS_PER_BATCH;
    }

    Arrays.sort(batchNanosPerRun);
    double medianNanos = batchNanosPerRun[BATCH_COUNT / 2];
    assertTrue(Double.isFinite(checksum) && checksum > 0.0, "benchmark checksum should remain finite");
    assertTrue(model.isModelConverged(), "measured benchmark run should converge");
    assertEquals(100000.0, model.getDetectedPlantFlowScale(), 1.0e-8);
    logger.info("FEED_TOPOLOGY_BENCHMARK median_ns_per_model_run={} min_ns_per_model_run={} checksum={}",
        medianNanos, batchNanosPerRun[0], checksum);
  }
}
