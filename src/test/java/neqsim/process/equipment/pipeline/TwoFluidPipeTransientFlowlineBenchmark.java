package neqsim.process.equipment.pipeline;

import java.util.Arrays;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.stream.Stream;

/**
 * Wall-clock benchmark of the legacy transient march on the synthetic three-phase flowline and riser.
 *
 * <p>
 * Uses the feed and geometry of {@link TwoFluidPipeThreePhaseFlowlineBenchmark}: 5.4 km, 8 inch, 60 cells, gas, oil and
 * water, explicit cell-face terrain ending in a 305 m riser. Each sample solves the steady state on a new pipe and then
 * advances 60 one-second intervals; only the transient part is timed. One warm-up sample is followed by three timed
 * samples. Launch it in a separate JVM with this logger at INFO and compare the median with the reported profile sums,
 * which must not change when only run time is being worked on.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class TwoFluidPipeTransientFlowlineBenchmark {
  /** Logger for benchmark output. */
  private static final Logger logger = LogManager.getLogger(TwoFluidPipeTransientFlowlineBenchmark.class);

  /** Number of one-second transient intervals per sample. */
  private static final int INTERVALS = 60;

  /** Utility class. */
  private TwoFluidPipeTransientFlowlineBenchmark() {
  }

  /**
   * Run the benchmark.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    Stream inlet = TwoFluidPipeThreePhaseFlowlineBenchmark.createInlet();
    double[] seconds = new double[3];
    for (int sample = -1; sample < seconds.length; sample++) {
      TwoFluidPipe pipe = TwoFluidPipeThreePhaseFlowlineBenchmark.createPipe(inlet, 1);
      pipe.run();
      if (!pipe.isSteadyStateConverged()) {
        throw new IllegalStateException(pipe.getSteadyStateConvergenceReport().toString());
      }
      UUID id = UUID.randomUUID();
      long start = System.nanoTime();
      for (int interval = 0; interval < INTERVALS; interval++) {
        pipe.runTransient(1.0, id);
      }
      double elapsed = (System.nanoTime() - start) / 1.0e9;
      if (sample >= 0) {
        seconds[sample] = elapsed;
      }
      logger.info("sample={} transientSeconds={} pressureSumPa={} liquidHoldupSum={} gasVelocitySum={}", sample,
          elapsed, Arrays.stream(pipe.getPressureProfile()).sum(),
          Arrays.stream(pipe.getLiquidHoldupProfile()).sum(), Arrays.stream(pipe.getGasVelocityProfile()).sum());
    }
    Arrays.sort(seconds);
    logger.info("cells=60 simulatedSeconds={} medianTransientSeconds={}", INTERVALS, seconds[seconds.length / 2]);
  }
}
