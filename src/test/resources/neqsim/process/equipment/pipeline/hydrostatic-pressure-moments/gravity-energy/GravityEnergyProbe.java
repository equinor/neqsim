import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.closure.GeometryCalculator.StratifiedPressureMoments;

/**
 * Read-only cross-sectional gravity-energy reference replay and optional-query timing.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class GravityEnergyProbe {
  /**
   * Evaluate every supplied identity and retain independent timing samples.
   *
   * @param args native CSV, timing CSV and two-cell energy budget CSV paths
   * @throws Exception if input or output fails
   */
  public static void main(String[] args) throws Exception {
    GeometryCalculator geometry = new GeometryCalculator();
    List<StratifiedPressureMoments> values = new ArrayList<StratifiedPressureMoments>();
    try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("diameter_m,gas_holdup,liquid_holdup,liquid_first_m3,gas_first_m3,energy_J_m");
      String line;
      while ((line = input.readLine()) != null) {
        String[] fields = line.split(",");
        double d = Double.parseDouble(fields[0]);
        double gas = Double.parseDouble(fields[1]);
        double liquid = Double.parseDouble(fields[2]);
        StratifiedPressureMoments value = geometry.calculateStratifiedPressureMomentsFromHoldups(gas, liquid, d);
        values.add(value);
        output.println(d + "," + gas + "," + liquid + "," + value.getLiquidFirstMomentAboutCentre() + ","
            + value.getGasFirstMomentAboutCentre() + "," + value.getGravityPotentialEnergyPerLength(10, 800, 9.81));
      }
    }
    double checksum = 0.0;
    for (int i = 0; i < 100000; i++) {
      checksum += values.get(i % values.size()).getGravityPotentialEnergyPerLength(10, 800, 9.81);
    }
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8))) {
      output.println("round,queries,ns_per_query,checksum");
      for (int round = 0; round < 7; round++) {
        long start = System.nanoTime();
        for (int i = 0; i < 200000; i++) {
          checksum += values.get(i % values.size()).getGravityPotentialEnergyPerLength(10, 800, 9.81);
        }
        output.println(round + ",200000," + (System.nanoTime() - start) / 200000.0 + "," + checksum);
      }
    }
    double initial = 10 * geometry.calculateStratifiedPressureMomentsFromHoldups(0.4, 0.6, 1.0148)
        .getGravityPotentialEnergyPerLength(10, 800, 9.81);
    double redistributed = 20 * geometry.calculateStratifiedPressureMomentsFromHoldups(0.7, 0.3, 1.0148)
        .getGravityPotentialEnergyPerLength(10, 800, 9.81);
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[2]), StandardCharsets.UTF_8))) {
      output.println("initial_J,redistributed_J,available_potential_drop_J");
      output.println(initial + "," + redistributed + "," + (initial - redistributed));
    }
  }
}
