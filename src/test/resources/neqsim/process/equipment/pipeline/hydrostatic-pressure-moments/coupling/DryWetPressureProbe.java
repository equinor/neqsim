import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidConservationEquations;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;

/**
 * Isolate pressure traction at an exactly dry cell adjacent to a stationary liquid-bearing cell.
 *
 * @author NeqSim
 * @version 1.0
 */
public final class DryWetPressureProbe {
  /**
   * Retain the unfunded phase impulse without advancing or clipping an inadmissible state.
   *
   * @param args output CSV path
   * @throws Exception if fixture setup or output fails
   */
  public static void main(String[] args) throws Exception {
    Class<?> fixture = Class
        .forName("neqsim.process.equipment.pipeline.twophasepipe.StratifiedPressureMomentCouplingTest");
    Method section = fixture.getDeclaredMethod("section", int.class, double.class, double.class, double.class,
        double.class, double.class, double.class);
    Method factory = fixture.getDeclaredMethod("equations", boolean.class, boolean.class);
    section.setAccessible(true);
    factory.setAccessible(true);
    TwoFluidSection dry = (TwoFluidSection) section.invoke(null, 0, 1.0148, 1.0, 0.0, 0.0, 0.0, 0.0);
    TwoFluidSection wet = (TwoFluidSection) section.invoke(null, 1, 1.0148, 0.4, 0.6, 0.0, 0.0, 0.0);
    try (PrintWriter output = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
      output
          .println("enabled,dry_oil_mass_kg_m,dry_oil_mass_rate_kg_ms,dry_oil_momentum_rate_N_m,internal_oil_force_N");
      for (boolean enabled : new boolean[] {false, true}) {
        TwoFluidConservationEquations equations = (TwoFluidConservationEquations) factory.invoke(null, enabled, false);
        double[][] rate = equations.calcRHS(new TwoFluidSection[] {dry.clone(), wet.clone()}, 10.0);
        double force = enabled ? equations.getLastStratifiedHydrostaticPressureForces()[1][1] : 0.0;
        output.println(enabled + "," + dry.getStateVector()[1] + "," + rate[0][1] + "," + rate[0][4] + "," + force);
        if (rate[0][1] != 0.0 || enabled && !(Math.abs(rate[0][4]) > 0.0) || !enabled && rate[0][4] != 0.0) {
          throw new IllegalStateException("Dry/wet counterexample changed; reassess the model");
        }
      }
    }
  }
}
