package neqsim.process.equipment.distillation;

import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Temporary warm-JVM A/B benchmark for the phase-split out-stream init level change. NOT part of
 * the committed test suite - used only to compare branch vs master locally.
 *
 * @author esol
 * @version 1.0
 */
public class PhaseSplitInitBenchmark {

  /**
   * Build the deethanizer feed used by the benchmark cases.
   *
   * @return configured feed fluid
   */
  private SystemInterface createDeethanizerFeed() {
    SystemInterface gas = new SystemSrkEos(216, 30.00);
    gas.addComponent("nitrogen", 1.67366E-3);
    gas.addComponent("CO2", 1.06819E-4);
    gas.addComponent("methane", 5.14168E-1);
    gas.addComponent("ethane", 1.92528E-1);
    gas.addComponent("propane", 1.70001E-1);
    gas.addComponent("i-butane", 3.14561E-2);
    gas.addComponent("n-butane", 5.58678E-2);
    gas.addComponent("i-pentane", 1.29573E-2);
    gas.addComponent("n-pentane", 1.23719E-2);
    gas.addComponent("n-hexane", 5.12878E-3);
    gas.addComponent("n-heptane", 1.0E-2);
    gas.setMixingRule("classic");
    return gas;
  }

  /**
   * Build a fresh deethanizer column for one timed run.
   *
   * @param trayCount number of trays
   * @param solverType solver type
   * @return configured column ready to run
   */
  private DistillationColumn buildColumn(int trayCount, DistillationColumn.SolverType solverType) {
    Stream feed = new Stream("feed", createDeethanizerFeed().clone());
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("bench", trayCount, true, false);
    column.addFeedStream(feed, trayCount);
    column.getReboiler().setOutTemperature(105.0 + 273.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(32.0);
    column.setMaxNumberOfIterations(trayCount <= 5 ? 50 : 80);
    column.setSolverType(solverType);
    if (solverType == DistillationColumn.SolverType.INSIDE_OUT
        || solverType == DistillationColumn.SolverType.MATRIX_INSIDE_OUT) {
      column.setInnerLoopSteps(2);
    }
    return column;
  }

  /**
   * Time one case with warm-up and report distribution statistics.
   *
   * @param label case label
   * @param trayCount number of trays
   * @param solverType solver type
   * @param warmup warm-up iterations (discarded)
   * @param reps measured iterations
   */
  private void timeCase(String label, int trayCount, DistillationColumn.SolverType solverType,
      int warmup, int reps) {
    for (int i = 0; i < warmup; i++) {
      buildColumn(trayCount, solverType).run();
    }
    double[] times = new double[reps];
    for (int i = 0; i < reps; i++) {
      DistillationColumn column = buildColumn(trayCount, solverType);
      long t0 = System.nanoTime();
      column.run();
      times[i] = (System.nanoTime() - t0) / 1.0e6;
    }
    Arrays.sort(times);
    double sum = 0.0;
    for (int i = 0; i < reps; i++) {
      sum += times[i];
    }
    double mean = sum / reps;
    double median = times[reps / 2];
    double min = times[0];
    double variance = 0.0;
    for (int i = 0; i < reps; i++) {
      variance += (times[i] - mean) * (times[i] - mean);
    }
    double sd = Math.sqrt(variance / reps);
    System.out.println(String.format(Locale.ROOT,
        "BENCH %-16s trays=%2d reps=%d  mean=%8.2f ms  median=%8.2f ms  min=%8.2f ms  sd=%7.2f ms",
        label, trayCount, reps, mean, median, min, sd));
  }

  /**
   * Run the A/B benchmark across the fixed out-stream code paths.
   */
  @Test
  public void benchmark() {
    System.out.println("==== PhaseSplitInitBenchmark ====");
    timeCase("INSIDE_OUT_10", 10, DistillationColumn.SolverType.INSIDE_OUT, 12, 40);
    timeCase("MATRIX_IO_14", 14, DistillationColumn.SolverType.MATRIX_INSIDE_OUT, 12, 40);
    timeCase("AUTO_10", 10, DistillationColumn.SolverType.AUTO, 12, 40);
    System.out.println("==== end ====");
  }
}
