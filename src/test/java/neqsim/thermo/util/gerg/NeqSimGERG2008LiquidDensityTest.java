package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import org.netlib.util.StringW;
import org.netlib.util.doubleW;
import org.netlib.util.intW;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verify that GERG2008 density evaluation works for liquid phases.
 */
public class NeqSimGERG2008LiquidDensityTest {
  /** Compare the high-density pure-butane root against the original GERG routine. */
  @Test
  public void testPureButaneLiquidDensity() throws Exception {
    double temperature = 298.15; // K
    double pressure = 10.0; // bar
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    system.addComponent("n-butane", 1.0);
    new ThermodynamicOperations(system).TPflash();

    NeqSimGERG2008 gerg = new NeqSimGERG2008(system.getPhase(0));
    double density = gerg.getDensity();

    // Reference density from direct GERG2008 call
    GERG2008 lib = new GERG2008();
    lib.SetupGERG();
    doubleW D = new doubleW(0.0);
    intW ierr = new intW(0);
    StringW herr = new StringW("");
    lib.DensityGERG(2, temperature, pressure * 100.0, gerg.normalizedGERGComposition, D, ierr, herr);
    double expected = D.val * system.getMolarMass() * 1000.0;

    assertEquals(expected, density, expected * 1e-6);
  }

  /**
   * Qualify liquid-root refinement for methane/ethane, rich natural gas and CO2/hydrogen over different isotherms.
   */
  @Test
  void chebyshevRefinementClosesExactPressureAndPreservesBranch() {
    GERG2008 model = new GERG2008();
    model.SetupGERG();
    double[][] compositions = new double[3][23];
    compositions[0][1] = compositions[0][4] = 0.5;
    compositions[1][1] = 0.9;
    compositions[1][4] = 0.06;
    compositions[1][5] = 0.025;
    compositions[1][7] = 0.01;
    compositions[1][2] = 0.005;
    compositions[2][3] = 0.9677;
    compositions[2][15] = 0.0323;
    doubleW pressure = new doubleW(0.0);
    doubleW compressibility = new doubleW(0.0);
    int[] pressureEvaluations = {0};
    int baselineEvaluations = 0;
    int candidateEvaluations = 0;
    for (int mixture = 0; mixture < compositions.length; mixture++) {
      double[] composition = compositions[mixture];
      for (double offset : new double[] {0.0, 15.0, 35.0}) {
        final double temperature = (mixture == 2 ? 245.0 : 180.0) + offset;
        for (double target : new double[] {500.0, 1800.0, 3800.0, 6800.0}) {
          DoubleUnaryOperator exactPressure = density -> {
            pressureEvaluations[0]++;
            model.PressureGERG(temperature, density, composition, pressure, compressibility);
            return pressure.val;
          };
          pressureEvaluations[0] = 0;
          double expected = ReferenceEosLiquidDensitySolver.solve(target, exactPressure);
          baselineEvaluations += pressureEvaluations[0];
          pressureEvaluations[0] = 0;
          double actual = ReferenceEosLiquidDensitySolver.solve(target, exactPressure, () -> model.dPdDsave);
          candidateEvaluations += pressureEvaluations[0];
          assertEquals(expected, actual, 1e-7);
          assertEquals(target, exactPressure.applyAsDouble(actual), target * 1.1e-11);
          assertTrue(model.dPdDsave > 0.0, "Selected density must lie on a positive-slope branch");
        }
      }
    }
    assertTrue(candidateEvaluations < baselineEvaluations * 0.7, "Refinement should cut exact EOS calls");
  }

  /** Verify that unrelated fluids evaluated on different workers do not share the mutable kernel state. */
  @Test
  void independentWorkersProduceTheSameDensitiesAsSerialEvaluation() {
    double butane = repeatedLiquidDensity("n-butane", 298.15, 10.0);
    double propane = repeatedLiquidDensity("propane", 270.0, 20.0);
    CompletableFuture<Double> first = CompletableFuture
        .supplyAsync(() -> repeatedLiquidDensity("n-butane", 298.15, 10.0));
    CompletableFuture<Double> second = CompletableFuture
        .supplyAsync(() -> repeatedLiquidDensity("propane", 270.0, 20.0));
    assertEquals(butane, first.join(), 1e-11);
    assertEquals(propane, second.join(), 1e-11);
  }

  /**
   * Exercise repeat hits and misses for a phase-specific density.
   *
   * @param component pure fluid name
   * @param temperature temperature in K
   * @param pressure pressure in bara
   * @return sum of densities in mol/L
   */
  private static double repeatedLiquidDensity(String component, double temperature, double pressure) {
    SystemInterface system = new SystemSrkEos(temperature, pressure);
    system.addComponent(component, 1.0);
    new ThermodynamicOperations(system).TPflash();
    NeqSimGERG2008 model = new NeqSimGERG2008(system.getPhase(0));
    double total = 0.0;
    for (int i = 0; i < 40; i++) {
      system.getPhase(0).setPressure(pressure + i * 0.01);
      double density = model.getMolarDensity();
      assertTrue(density > 0.0 && Double.isFinite(density));
      assertEquals(density, model.getMolarDensity(), 0.0);
      total += density;
    }
    return total;
  }
}
