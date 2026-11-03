import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/**
 * Reproduce conservative total-energy diffusion at an ideal-gas pressure jump.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PressureJump {
  /**
   * Evaluate the existing optional flux without a production solver call.
   *
   * @param args output CSV path
   * @throws Exception if the output cannot be saved
   */
  public static void main(String[] args) throws Exception {
    PhaseState left = new PhaseState(10, 0, 1e6, Math.sqrt(140000), 350000, 0.5);
    PhaseState right = new PhaseState(10, 0, 2e6, Math.sqrt(280000), 700000, 0.5);
    double actual = new AUSMPlusFluxCalculator().calcRusanovFlux(left, right, 1).energyFlux;
    double expected = -0.5 * right.soundSpeed * (0.5 * 2e6 / 0.4 - 0.5 * 1e6 / 0.4);
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("native_W,reference_W,error_W");
      output.println(actual + "," + expected + "," + (actual - expected));
    }
  }
}
