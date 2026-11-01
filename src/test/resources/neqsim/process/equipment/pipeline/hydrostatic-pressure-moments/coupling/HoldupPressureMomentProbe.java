import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;

/**
 * Read-only native probe for independently specified face holdups.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class HoldupPressureMomentProbe {
  /**
   * Evaluate the supplied fixed matrix without changing a solver state.
   *
   * @param args output CSV path
   * @throws Exception if input, compilation linkage or output fails
   */
  public static void main(String[] args) throws Exception {
    GeometryCalculator geometry = new GeometryCalculator();
    try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("diameter_m,gas_holdup,liquid_holdup,liquid_m3,gas_m3,liquid_m2,gas_m2");
      String line;
      while ((line = input.readLine()) != null) {
        String[] fields = line.split(",");
        double diameter = Double.parseDouble(fields[0]);
        double gas = Double.parseDouble(fields[1]);
        double liquid = Double.parseDouble(fields[2]);
        GeometryCalculator.StratifiedPressureMoments value = geometry
            .calculateStratifiedPressureMomentsFromHoldups(gas, liquid, diameter);
        output.println(diameter + "," + gas + "," + liquid + "," + value.getLiquidMoment() + ","
            + value.getGasMoment() + "," + value.getLiquidArea() + "," + value.getGasArea());
      }
    }
  }
}
