package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.PrintWriter;

/**
 * Reproducible small RHS timing control, never an assertion on shared-runner speed.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ClosedWallPressureBenchmark {
  /** No instances. */
  private ClosedWallPressureBenchmark() {
  }

  /**
   * Record seven warmed repeated evaluations with closed or ordinary open faces.
   *
   * @param args output CSV path and closed flag
   * @throws Exception if output cannot be written
   */
  public static void main(String[] args) throws Exception {
    TwoFluidSection[] cells = new TwoFluidSection[16];
    for (int i = 0; i < cells.length; i++) {
      TwoFluidSection s = new TwoFluidSection(i + 0.5, 1.0, 1.0148, 0.0);
      s.setPressure(1e6);
      s.setGasHoldup(1.0);
      s.setLiquidHoldup(0.0);
      s.setWaterCut(0.0);
      s.setGasDensity(10.0);
      s.setOilDensity(800.0);
      s.setWaterDensity(1000.0);
      s.setLiquidDensity(800.0);
      s.updateConservativeVariables();
      cells[i] = s;
    }
    TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
    equations.setIncludeEnergyEquation(false);
    equations.setIncludeMassTransfer(false);
    equations.setConsistentPhasePressureEnabled(true);
    boolean closed = Boolean.parseBoolean(args[1]);
    equations.setClosedBoundaries(closed, closed);
    double checksum = 0.0;
    for (int i = 0; i < 3000; i++) {
      checksum += equations.calcRHS(cells, 1.0)[0][3];
    }
    try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
      out.println("repeat,closed,microseconds_per_rhs,checksum");
      for (int repeat = 0; repeat < 7; repeat++) {
        long begin = System.nanoTime();
        for (int i = 0; i < 5000; i++) {
          checksum += equations.calcRHS(cells, 1.0)[0][3];
        }
        double elapsed = (System.nanoTime() - begin) / 5000.0 / 1000.0;
        out.printf(java.util.Locale.ROOT, "%d,%s,%.17g,%.17g%n", repeat, closed, elapsed, checksum);
      }
    }
  }
}
