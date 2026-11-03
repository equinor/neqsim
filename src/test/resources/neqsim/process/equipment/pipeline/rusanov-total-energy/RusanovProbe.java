import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseFlux;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.AUSMPlusFluxCalculator.PhaseState;

/**
 * Native optional Rusanov and unchanged default AUSM flux replay, with explicit total enthalpy.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class RusanovProbe {
  /**
   * Evaluate every supplied phase-state pair and save complete numerical outputs.
   *
   * @param args Rusanov CSV path and default-AUSM CSV path
   * @throws Exception if input or output fails
   */
  public static void main(String[] args) throws Exception {
    AUSMPlusFluxCalculator calculator = new AUSMPlusFluxCalculator();
    try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8));
        PrintWriter control = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8))) {
      String header = "id,mass_kg_s,momentum_N,energy_W,holdup_flux_m_s,interface_holdup,interface_pressure_Pa";
      output.println(header);
      control.println(header);
      String line;
      while ((line = input.readLine()) != null) {
        String[] fields = line.split(",");
        double[] x = new double[13];
        for (int i = 0; i < x.length; i++) {
          x[i] = Double.parseDouble(fields[i + 1]);
        }
        PhaseState left = new PhaseState(x[0], x[1], x[2], x[3], x[4], x[5]);
        PhaseState right = new PhaseState(x[6], x[7], x[8], x[9], x[10], x[11]);
        write(output, fields[0], calculator.calcRusanovFlux(left, right, x[12]));
        write(control, fields[0], calculator.calcPhaseFlux(left, right, x[12]));
      }
    }
  }

  /**
   * Save a complete phase flux without rounding away trace quantities.
   *
   * @param output CSV writer
   * @param id fixed input identity
   * @param flux evaluated phase flux
   */
  private static void write(PrintWriter output, String id, PhaseFlux flux) {
    output.println(id + "," + flux.massFlux + "," + flux.momentumFlux + "," + flux.energyFlux + "," + flux.holdupFlux
        + "," + flux.interfaceHoldup + "," + flux.interfacePressure);
  }
}
