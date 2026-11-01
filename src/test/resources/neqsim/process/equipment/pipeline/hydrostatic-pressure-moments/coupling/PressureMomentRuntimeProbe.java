import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Read-only RHS cost measurement using the same qualified 24-cell operator fixture.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class PressureMomentRuntimeProbe {
  /**
   * Measure paired disabled/enabled evaluations after warmup; reflection is confined to fixture setup.
   *
   * @param args CSV output path
   * @throws Exception if fixture construction or output fails
   */
  public static void main(String[] args) throws Exception {
    Class<?> fixture = Class.forName(
        "neqsim.process.equipment.pipeline.twophasepipe.StratifiedPressureMomentCouplingTest");
    Method section = fixture.getDeclaredMethod("section", int.class, double.class, double.class, double.class,
        double.class, double.class, double.class);
    Method equations = fixture.getDeclaredMethod("equations", boolean.class, boolean.class);
    section.setAccessible(true);
    equations.setAccessible(true);
    TwoFluidSection[] cells = new TwoFluidSection[24];
    for (int cell = 0; cell < cells.length; cell++) {
      cells[cell] = (TwoFluidSection) section.invoke(null, cell, 1.0148, 0.4, 0.6, 0.0, 1.0, 0.2);
    }
    TwoFluidConservationEquations[] operators = {
        (TwoFluidConservationEquations) equations.invoke(null, false, false),
        (TwoFluidConservationEquations) equations.invoke(null, true, false)};
    double checksum = 0.0;
    for (int warmup = 0; warmup < 1500; warmup++) {
      for (TwoFluidConservationEquations operator : operators) {
        checksum += operator.calcRHS(cells, 10.0)[0][3];
      }
    }
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("round,enabled,cells,repetitions,ns_per_rhs,checksum");
      for (int round = 0; round < 9; round++) {
        for (int order = 0; order < 2; order++) {
          int mode = (round + order) % 2;
          long start = System.nanoTime();
          for (int repeat = 0; repeat < 1000; repeat++) {
            checksum += operators[mode].calcRHS(cells, 10.0)[0][3];
          }
          double elapsed = (System.nanoTime() - start) / 1000.0;
          output.printf(Locale.ROOT, "%d,%s,24,1000,%.3f,%.17g%n", round, mode == 1, elapsed, checksum);
        }
      }
    }
  }
}
