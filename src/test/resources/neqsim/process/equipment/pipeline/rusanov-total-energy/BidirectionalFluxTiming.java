import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/** Observational timing that consumes every directional output to prevent dead-store elimination. */
public final class BidirectionalFluxTiming {
  /**
   * Time AUSM or Rusanov local queries with the complete two-donor checksum.
   *
   * @param args CSV path followed by {@code ausm} or {@code rusanov}
   * @throws Exception if output fails
   */
  public static void main(String[] args) throws Exception {
    boolean rusanov = "rusanov".equals(args[1]);
    if (!rusanov && !"ausm".equals(args[1])) {
      throw new IllegalArgumentException("Choose ausm or rusanov");
    }
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    PhaseState[] left = states(true);
    PhaseState[] right = states(false);
    double checksum = execute(calculator, left, right, rusanov, 100000);
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("method,round,queries,ns_per_query,checksum");
      for (int round = 0; round < 7; round++) {
        long start = System.nanoTime();
        checksum += execute(calculator, left, right, rusanov, 200000);
        output.println(args[1] + "," + round + ",200000," + (System.nanoTime() - start) / 200000.0 + "," + checksum);
      }
    }
  }

  /** Build four deterministic occupied phase states. */
  private static PhaseState[] states(boolean left) {
    PhaseState[] result = new PhaseState[4];
    for (int i = 0; i < result.length; i++) {
      double velocity = left ? i - 2.0 : 3.0 - i;
      result[i] = new PhaseState(left ? 10.0 : 20.0, velocity, left ? 1.0e6 : 2.0e6, 400.0,
          350000.0 + 0.5 * velocity * velocity, left ? 0.25 : 0.75);
    }
    return result;
  }

  /** Consume every returned conservative and directional field. */
  private static double execute(AUSMPlusFluxCalculator calculator, PhaseState[] left, PhaseState[] right,
      boolean rusanov, int count) {
    double checksum = 0.0;
    for (int i = 0; i < count; i++) {
      PhaseFlux value = rusanov ? calculator.calcRusanovFlux(left[i % 4], right[i % 4], 1.0)
          : calculator.calcPhaseFlux(left[i % 4], right[i % 4], 1.0);
      checksum += value.massFlux + value.momentumFlux + value.energyFlux + value.holdupFlux
          + value.forwardMassFlux + value.reverseMassFlux + value.forwardAdvectiveMomentumFlux
          + value.reverseAdvectiveMomentumFlux + value.forwardAdvectiveEnergyFlux
          + value.reverseAdvectiveEnergyFlux + value.numericalPressureEnergyFlux;
    }
    return checksum;
  }
}
