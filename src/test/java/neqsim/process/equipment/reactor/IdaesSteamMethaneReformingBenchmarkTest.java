package neqsim.process.equipment.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Cross-tool benchmark of steam methane reforming equilibrium against the IDAES 2.4.0 Gibbs reactor example.
 *
 * <p>
 * Reference: IDAES Examples, "Flowsheet Gibbs Reactor Simulation and Optimization of Steam Methane Reforming",
 * updated 2023-06-01.
 * </p>
 */
class IdaesSteamMethaneReformingBenchmarkTest {
  private static final String[] COMPONENTS = { "methane", "water", "hydrogen", "CO", "CO2" };
  private static final double MOLE_FRACTION_RMSE_TOLERANCE = 0.005;
  private static final double MOLE_FRACTION_MAX_ERROR_TOLERANCE = 0.010;
  private static final double CONVERSION_TOLERANCE = 0.010;

  @Test
  void reproducesPrimaryAndHoldoutEquilibriumStates() {
    BenchmarkResult primary = runCase(920.80, 2.0,
        new double[] { 0.034965, 0.32532, 0.49984, 0.059609, 0.080265 }, 0.800);
    BenchmarkResult holdout = runCase(1087.385, 10.0,
        new double[] { 0.016892, 0.31609, 0.51498, 0.093140, 0.058900 }, 0.900);

    assertBenchmark(primary);
    assertBenchmark(holdout);

    assertTrue(holdout.actual[0] < primary.actual[0], "methane should decrease at the hotter state");
    assertTrue(holdout.actual[1] < primary.actual[1], "water should decrease at the hotter state");
    assertTrue(holdout.actual[2] > primary.actual[2], "hydrogen should increase at the hotter state");
    assertTrue(holdout.actual[3] > primary.actual[3], "carbon monoxide should increase at the hotter state");
  }

  private static BenchmarkResult runCase(double temperatureK, double pressureBar, double[] reference,
      double referenceConversion) {
    double normalization = 1.0 + 4.0e-5;
    double methaneIn = (75.0 + 234.0e-5) / normalization;

    SystemInterface system = new SystemPrEos(temperatureK, pressureBar);
    system.addComponent("methane", methaneIn);
    system.addComponent("water", (234.0 + 75.0e-5) / normalization);
    system.addComponent("hydrogen", 309.0e-5 / normalization);
    system.addComponent("CO", 309.0e-5 / normalization);
    system.addComponent("CO2", 309.0e-5 / normalization);
    system.setMixingRule(2);

    Stream inlet = new Stream("IDAES SMR feed", system);
    inlet.run();

    GibbsReactor reactor = new GibbsReactor("IDAES SMR Gibbs reactor", inlet);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.setDampingComposition(0.01);
    reactor.setUseAdaptiveStepSize(true);
    reactor.setMinIterations(3);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1e-8);
    reactor.run();

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
    double[] actual = new double[COMPONENTS.length];
    double sumSquaredError = 0.0;
    double maxError = 0.0;
    for (int i = 0; i < COMPONENTS.length; i++) {
      actual[i] = outlet.getComponent(COMPONENTS[i]).getz();
      double error = Math.abs(actual[i] - reference[i]);
      sumSquaredError += error * error;
      maxError = Math.max(maxError, error);
    }
    double conversion = (methaneIn - outlet.getComponent("methane").getNumberOfmoles()) / methaneIn;

    return new BenchmarkResult(actual, Math.sqrt(sumSquaredError / COMPONENTS.length), maxError, conversion,
        referenceConversion, reactor.hasConverged(), reactor.getFinalConvergenceError(),
        reactor.getElementMassBalanceError());
  }

  private static void assertBenchmark(BenchmarkResult result) {
    assertTrue(result.converged, "Gibbs solver should converge");
    assertTrue(result.residual < 1e-6, "final Gibbs residual should be below 1e-6");
    assertTrue(result.massBalanceError < 1e-3, "mass balance error should be below 0.001%");
    assertTrue(result.rmse <= MOLE_FRACTION_RMSE_TOLERANCE, "mole-fraction RMSE exceeds frozen tolerance");
    assertTrue(result.maxError <= MOLE_FRACTION_MAX_ERROR_TOLERANCE,
        "maximum mole-fraction error exceeds frozen tolerance");
    assertEquals(result.referenceConversion, result.conversion, CONVERSION_TOLERANCE,
        "methane conversion differs from the IDAES reference");

    double sum = 0.0;
    for (double moleFraction : result.actual) {
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= 0.0,
          "mole fractions must be finite and non-negative");
      sum += moleFraction;
    }
    assertEquals(1.0, sum, 1e-8, "mole fractions should sum to one");
  }

  private static final class BenchmarkResult {
    private final double[] actual;
    private final double rmse;
    private final double maxError;
    private final double conversion;
    private final double referenceConversion;
    private final boolean converged;
    private final double residual;
    private final double massBalanceError;

    private BenchmarkResult(double[] actual, double rmse, double maxError, double conversion,
        double referenceConversion, boolean converged, double residual, double massBalanceError) {
      this.actual = actual;
      this.rmse = rmse;
      this.maxError = maxError;
      this.conversion = conversion;
      this.referenceConversion = referenceConversion;
      this.converged = converged;
      this.residual = residual;
      this.massBalanceError = massBalanceError;
    }
  }
}
