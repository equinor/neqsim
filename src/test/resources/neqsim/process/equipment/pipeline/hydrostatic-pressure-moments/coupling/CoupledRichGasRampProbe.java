import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Replay the public pressure ramp with explicit moment opt-in, preserving rejection evidence.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class CoupledRichGasRampProbe {
  /**
   * Execute both historical meshes and retain every accepted interval.
   *
   * @param args accepted-interval CSV and status text output paths
   * @throws Exception if fixture construction fails or a requested interval cannot complete
   */
  public static void main(String[] args) throws Exception {
    Class<?> fixture = Class.forName("neqsim.process.equipment.pipeline.TwoFluidRichGasDragQualificationTest");
    Method factory = fixture.getDeclaredMethod("createRichGasPipe", int.class, boolean.class);
    factory.setAccessible(true);
    boolean complete = true;
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8));
        PrintWriter status = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1]), StandardCharsets.UTF_8))) {
      out.println(
          "cells,accepted_time_s,oil_kg,min_pressure_Pa,min_temperature_K,max_gas_speed_ms,max_oil_speed_ms,mass_residual_kg");
      for (int cells : new int[] {12, 24}) {
        TwoFluidPipe pipe = (TwoFluidPipe) factory.invoke(null, cells, false);
        pipe.setStratifiedHydrostaticPressureMomentsEnabled(true);
        double dt = cells == 12 ? 0.25 : 0.125;
        long start = System.nanoTime();
        for (int step = 1; step <= (int) Math.round(120 / dt); step++) {
          double requested = step * dt;
          pipe.setOutletPressure(150 - 140 * requested / 120, "bara");
          TwoFluidSection[] before = pipe.getSectionSnapshots();
          double timeBefore = pipe.getSimulationTime();
          try {
            pipe.runTransient(dt, null);
          } catch (RuntimeException error) {
            boolean restored = pipe.getSimulationTime() == timeBefore;
            TwoFluidSection[] after = pipe.getSectionSnapshots();
            for (int cell = 0; cell < cells; cell++)
              restored &= Arrays.equals(before[cell].getStateVector(), after[cell].getStateVector());
            status.println("cells=" + cells + ",requested_s=" + requested + ",accepted_s=" + pipe.getSimulationTime()
                + ",rollback_primary_state=" + restored + ",error=" + error.toString() + ",wall_s="
                + (System.nanoTime() - start) / 1e9);
            complete = false;
            break;
          }
          if (Math.abs(pipe.getSimulationTime() - requested) > 1e-9 || pipe.isTransientOutletBackflowClamped()
              || !pipe.getLastComponentConservationReport().isConverged()
              || !pipe.getLastConservativeEnergyReport().isWithinTolerance(1e-3, 1e-7))
            throw new IllegalStateException("Accepted state failed existing interval gates");
          double massError = 0;
          TwoFluidMassBalanceReport mass = pipe.getLastMassBalanceReport();
          for (Phase phase : Phase.values()) {
            if (!mass.isWithinTolerance(phase, 1e-6, 1e-8))
              throw new IllegalStateException("Phase mass gate failed");
            massError = Math.max(massError, Math.abs(mass.getResidualKg(phase)));
          }
          double minP = Double.POSITIVE_INFINITY, minT = Double.POSITIVE_INFINITY, maxG = 0, maxO = 0;
          for (TwoFluidSection section : pipe.getSectionSnapshots()) {
            minP = Math.min(minP, section.getPressure());
            minT = Math.min(minT, section.getTemperature());
            maxG = Math.max(maxG, Math.abs(section.getGasVelocity()));
            maxO = Math.max(maxO, Math.abs(section.getOilVelocity()));
          }
          out.printf(Locale.ROOT, "%d,%.9g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n", cells, requested,
              mass.getFinalMassKg(Phase.OIL), minP, minT, maxG, maxO, massError);
          if (step == (int) Math.round(120 / dt))
            status.println(
                "cells=" + cells + ",accepted_s=120.0,complete=true,wall_s=" + (System.nanoTime() - start) / 1e9);
        }
      }
    }
    if (!complete)
      throw new IllegalStateException("Moment-enabled ramp did not complete; inspect retained status");
  }
}
