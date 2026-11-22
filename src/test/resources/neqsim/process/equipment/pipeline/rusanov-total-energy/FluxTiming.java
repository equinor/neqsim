import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/**
 * Observational local-flux timing with an explicit checksum and fixed work count.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class FluxTiming {
  /**
   * Time the unchanged AUSM query or optional Rusanov query in an independent JVM.
   *
   * @param args CSV path followed by ausm or rusanov
   * @throws Exception if output fails
   */
  public static void main(String[] args) throws Exception {
    boolean rusanov = "rusanov".equals(args[1]);
    if (!rusanov && !"ausm".equals(args[1])) {
      throw new IllegalArgumentException("Choose ausm or rusanov");
    }
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    PhaseState[] left = new PhaseState[4];
    PhaseState[] right = new PhaseState[4];
    for (int i = 0; i < 4; i++) {
      left[i] = new PhaseState(10, i - 2, 1e6, 400, 350000 + 0.5 * (i - 2) * (i - 2), 0.25);
      right[i] = new PhaseState(20, 3 - i, 2e6, 400, 350000 + 0.5 * (3 - i) * (3 - i), 0.75);
    }
    double checksum = 0.0;
    for (int i = 0; i < 100000; i++) {
      PhaseFlux value = rusanov ? calculator.calcRusanovFlux(left[i % 4], right[i % 4], 1.0)
          : calculator.calcPhaseFlux(left[i % 4], right[i % 4], 1.0);
      checksum += value.massFlux + value.energyFlux;
    }
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("method,round,queries,ns_per_query,checksum");
      for (int round = 0; round < 7; round++) {
        long start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
          PhaseFlux value = rusanov ? calculator.calcRusanovFlux(left[i % 4], right[i % 4], 1.0)
              : calculator.calcPhaseFlux(left[i % 4], right[i % 4], 1.0);
          checksum += value.massFlux + value.energyFlux;
        }
        output.println(args[1] + "," + round + ",200000," + (System.nanoTime() - start) / 200000.0 + "," + checksum);
      }
    }
  }
}
