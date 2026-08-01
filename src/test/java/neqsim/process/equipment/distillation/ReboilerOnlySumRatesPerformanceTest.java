package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Deterministic work-reduction gate for native sum-rates routing on reboiler-only columns. */
public class ReboilerOnlySumRatesPerformanceTest {
  private static SystemInterface createFluid(double temperature, double[] moles) {
    String[] components = { "methane", "ethane", "propane", "n-butane", "nC10" };
    SystemInterface fluid = new SystemSrkEos(temperature, 30.0);
    for (int componentIndex = 0; componentIndex < components.length; componentIndex++) {
      fluid.addComponent(components[componentIndex], moles[componentIndex]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Stream createFeed(String name, SystemInterface fluid, double flowRate) {
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(flowRate, "kg/hr");
    feed.setPressure(30.0, "bara");
    feed.run();
    return feed;
  }

  private static DistillationColumn createColumn(String name, double gasTemperature, double solventFlowRate,
      DistillationColumn.SolverType solverType) {
    Stream gasFeed = createFeed(name + " rich gas",
        createFluid(gasTemperature, new double[] { 0.70, 0.15, 0.10, 0.05, 1.0e-10 }), 1000.0);
    Stream solventFeed = createFeed(name + " lean solvent",
        createFluid(298.15, new double[] { 1.0e-10, 1.0e-10, 1.0e-10, 1.0e-10, 1.0 }), solventFlowRate);
    DistillationColumn column = new DistillationColumn(name, 10, true, false);
    column.addFeedStream(gasFeed, 1);
    column.addFeedStream(solventFeed, column.getNumberOfTrays() - 1);
    column.getReboiler().setOutTemperature(330.15);
    column.setTopPressure(30.0);
    column.setBottomPressure(30.0);
    column.setMaxNumberOfIterations(400);
    column.setTemperatureTolerance(1.0e-4);
    column.setMassBalanceTolerance(1.0e-1);
    column.setEnthalpyBalanceTolerance(10.0);
    column.setSolverType(solverType);
    return column;
  }

  private static void assertAtLeastTwentyFivePercentFewerIterations(String caseName, double gasTemperature,
      double solventFlowRate) {
    DistillationColumn damped = createColumn(caseName + " damped", gasTemperature, solventFlowRate,
        DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    DistillationColumn sumRates = createColumn(caseName + " sum-rates", gasTemperature, solventFlowRate,
        DistillationColumn.SolverType.SUM_RATES);
    damped.run();
    sumRates.run();

    assertTrue(damped.solved(), damped.getConvergenceDiagnostics());
    assertTrue(sumRates.solved(), sumRates.getConvergenceDiagnostics());
    assertEquals(DistillationColumn.SolverType.DAMPED_SUBSTITUTION, damped.getLastSolverTypeUsed());
    assertEquals(DistillationColumn.SolverType.SUM_RATES, sumRates.getLastSolverTypeUsed());

    int dampedIterations = damped.getLastIterationCount();
    int sumRatesIterations = sumRates.getLastIterationCount();
    assertTrue(sumRatesIterations * 4 <= dampedIterations * 3,
        caseName + " SUM_RATES must use at least 25% fewer iterations; damped=" + dampedIterations + ", sum-rates="
            + sumRatesIterations);
  }

  /** Representative and nearby operating points must retain deterministic work reduction. */
  @Test
  public void nativeSumRatesReducesIterationsAcrossNearbyOperatingPoints() {
    assertAtLeastTwentyFivePercentFewerIterations("representative", 313.15, 1200.0);
    assertAtLeastTwentyFivePercentFewerIterations("nearby", 318.15, 1300.0);
  }
}
