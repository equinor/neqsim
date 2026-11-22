import java.io.FileWriter;
import java.io.PrintWriter;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FetkovichAquifer.State;

/**
 * Isolated warmed mixed-exposure timing control; not an end-to-end reservoir benchmark.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class AquiferTiming {
  /** Retains the checksum so the workload is observable. */
  private static volatile double sink;

  /** Prevents construction. */
  private AquiferTiming() {
  }

  /**
   * Evaluates the same mixed pressure ramps and immutable initial checkpoint repeatedly.
   *
   * @param aquifer fixed physical model
   * @param iterations number of calls
   * @return sum of observed pressure and signed water volume
   */
  private static double run(FetkovichAquifer aquifer, int iterations) {
    double[] durations = {0.005, 0.5, 2.5, 5.0, 5000.0, 50000.0};
    State initial = aquifer.initialState();
    double sum = 0.0;
    for (int i = 0; i < iterations; i++) {
      boolean rising = (i & 1) == 0;
      State result = aquifer.advance(initial, durations[i % durations.length], rising ? 200 : 400,
          rising ? 450 : 150);
      sum += result.getPressureBara() + result.getCumulativeInfluxM3()
          + result.getIntervalMinimumCumulativeInfluxM3() + result.getIntervalMaximumCumulativeInfluxM3();
    }
    return sum;
  }

  /**
   * Warms the complete kernel, then records the same observable workload.
   *
   * @param args output file followed by iteration count
   * @throws Exception if the timing output cannot be written
   */
  public static void main(String[] args) throws Exception {
    FetkovichAquifer aquifer = new FetkovichAquifer(1.0e6, 1.0e-4, 0.02, 300.0);
    sink = run(aquifer, 200000);
    int iterations = Integer.parseInt(args[1]);
    long start = System.nanoTime();
    sink = run(aquifer, iterations);
    long elapsed = System.nanoTime() - start;
    try (PrintWriter writer = new PrintWriter(new FileWriter(args[0]))) {
      writer.println(iterations + "," + elapsed + "," + sink);
    }
  }
}
