package neqsim.process.equipment.pipeline.twophasepipe;

import java.io.PrintWriter;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Source-exact constant-density hydrostatic operator audit, not a pool simulation.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class ClosedWallGravityAudit {
  /** No instances. */
  private ClosedWallGravityAudit() {
  }

  /**
   * Run fixed flat, affine and original-terrain controls with both closed boundaries.
   *
   * @param args output CSV path, optionally followed by legacy to audit the unmodified operator
   * @throws Exception if output cannot be written
   */
  public static void main(String[] args) throws Exception {
    boolean legacy = args.length > 1 && "legacy".equals(args[1]);
    try (PrintWriter out = new PrintWriter(args[0], "UTF-8")) {
      out.println("terrain,cells,phase,interior_acceleration,boundary_acceleration,oracle_error,mass_rate,energy_rate");
      for (String terrain : new String[] {"flat", "affine", "original"}) {
        for (int count : new int[] {12, 24, 48, 96, 192}) {
          for (int phase : new int[] {0, 1}) {
            double dx = 5000.0 / count;
            double rho = phase == 0 ? 10.0 : 800.0;
            double[] z = new double[count + 1];
            for (int j = 0; j <= count; j++) {
              double s = (double) j / count;
              z[j] = "flat".equals(terrain) ? -300.0
                  : "affine".equals(terrain) ? -300.0 - 55.0 * s
                      : -300.0 - 55.0 * Math.pow(Math.sin(Math.PI * s), 2)
                          - 12.0 * Math.pow(Math.sin(3.0 * Math.PI * s), 2);
            }
            TwoFluidSection[] sections = new TwoFluidSection[count];
            double[] pressure = new double[count];
            for (int i = 0; i < count; i++) {
              double angle = Math.asin((z[i + 1] - z[i]) / dx);
              TwoFluidSection sec = new TwoFluidSection((i + 0.5) * dx, dx, 1.0148, angle);
              pressure[i] = 1.5e7 - rho * 9.81 * ((z[i] + z[i + 1]) / 2.0 + 300.0);
              sec.setPressure(pressure[i]);
              sec.setGasHoldup(phase == 0 ? 1.0 : 0.0);
              sec.setLiquidHoldup(phase == 1 ? 1.0 : 0.0);
              sec.setWaterCut(0.0);
              sec.setGasDensity(10.0);
              sec.setLiquidDensity(800.0);
              sec.setOilDensity(800.0);
              sec.setWaterDensity(1000.0);
              sec.setGasVelocity(0.0);
              sec.setLiquidVelocity(0.0);
              sec.setOilVelocity(0.0);
              sec.setWaterVelocity(0.0);
              sec.updateConservativeVariables();
              sections[i] = sec;
            }
            TwoFluidConservationEquations equations = new TwoFluidConservationEquations();
            equations.setIncludeEnergyEquation(true);
            equations.setIncludeMassTransfer(false);
            equations.setConsistentPhasePressureEnabled(true);
            equations.setClosedBoundaries(true, true);
            double[][] rhs = equations.calcRHS(sections, dx);
            double interior = 0.0, boundary = 0.0, error = 0.0, mass = 0.0, energy = 0.0;
            for (int i = 0; i < count; i++) {
              double value = rhs[i][3 + phase] / (rho * sections[i].getArea());
              double left = i == 0
                  ? legacy ? pressure[0] - 0.5 * (pressure[1] - pressure[0])
                      : pressure[0] + 0.5 * rho * 9.81 * (z[1] - z[0])
                  : 0.5 * (pressure[i - 1] + pressure[i]);
              double right = i == count - 1 ? legacy ? pressure[i] : pressure[i] - 0.5 * rho * 9.81 * (z[i + 1] - z[i])
                  : 0.5 * (pressure[i + 1] + pressure[i]);
              double expected = -(right - left) / (rho * dx) - 9.81 * (z[i + 1] - z[i]) / dx;
              error = Math.max(error, Math.abs(value - expected));
              if (i == 0 || i == count - 1) {
                boundary = Math.max(boundary, Math.abs(value));
              } else {
                interior = Math.max(interior, Math.abs(value));
              }
              mass += dx * (rhs[i][0] + rhs[i][1] + rhs[i][2]);
              energy += dx * rhs[i][6];
            }
            out.printf(java.util.Locale.ROOT, "%s,%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g%n", terrain, count, phase,
                interior, boundary, error, mass, energy);
          }
        }
      }
    }
  }
}
