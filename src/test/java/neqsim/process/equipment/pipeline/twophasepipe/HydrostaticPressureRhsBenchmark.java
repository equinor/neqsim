package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Repeatable RHS-only cost measurement for the hydrostatic pressure reconstruction.
 *
 * <p>
 * No EOS flash, nonlinear integration, or production throughput is measured.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class HydrostaticPressureRhsBenchmark {
  private static volatile double checksum;

  private HydrostaticPressureRhsBenchmark() {
  }

  /**
   * Write seven warmed batches per terrain, phase mixture and mesh to a CSV file.
   *
   * @param args output CSV path
   * @throws IOException if output cannot be written
   */
  public static void main(String[] args) throws IOException {
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output.println("terrain,gas_holdup,cells,batch,calls,elapsed_ns,ns_per_call,checksum");
      for (String terrain : new String[] {"flat", "original"}) {
        for (double gasHoldup : new double[] {1.0, 0.35}) {
          for (int count : new int[] {12, 24, 96, 192}) {
            TwoFluidSection[] cells = MultiphaseHydrostaticForceQualificationTest.hydrostaticCells(terrain, true, count,
                gasHoldup, 0.0);
            TwoFluidConservationEquations equations = MultiphaseHydrostaticForceQualificationTest.equations();
            evaluate(equations, cells, 10000);
            for (int batch = 0; batch < 7; batch++) {
              long start = System.nanoTime();
              evaluate(equations, cells, 1000);
              long elapsed = System.nanoTime() - start;
              output.printf(Locale.ROOT, "%s,%.12g,%d,%d,1000,%d,%.12g,%.12g%n", terrain, gasHoldup, count, batch,
                  elapsed, elapsed / 1000.0, checksum);
            }
          }
        }
      }
    }
  }

  private static void evaluate(TwoFluidConservationEquations equations, TwoFluidSection[] cells, int calls) {
    for (int call = 0; call < calls; call++) {
      double[][] rhs = equations.calcRHS(cells, 5000.0 / cells.length);
      checksum += rhs[cells.length / 2][TwoFluidConservationEquations.IDX_GAS_MOMENTUM];
    }
  }
}
