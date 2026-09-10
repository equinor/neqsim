package neqsim.process.util.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.util.optimizer.SQPoptimizer.OptimizationResult;

/** Analytical checks for convergence defects exposed by the SQP documentation examples. */
class SQPDocumentationRegressionTest {
  @Test
  void objectiveOffsetsDoNotChangeTheAnalyticalOptimum() {
    for (double offset : new double[] { 0.0, 1000.0, -1000.0 }) {
      SQPoptimizer solver = new SQPoptimizer();
      solver.setObjectiveFunction(x -> {
        double first = x[0] - 1.25;
        double second = x[1] + 0.75;
        return offset + 3.0 * first * first + second * second + first * second;
      });
      solver.setInitialPoint(new double[] { 4.0, 3.0 });
      solver.setTolerance(1e-6);
      OptimizationResult result = solver.solve();
      assertTrue(result.isConverged(), "Objective offset " + offset + " stalled the line search");
      assertEquals(1.25, result.getOptimalPoint()[0], 1e-5);
      assertEquals(-0.75, result.getOptimalPoint()[1], 1e-5);
      assertEquals(offset, result.getOptimalValue(), 1e-8);
      assertTrue(result.getKktError() < 1e-6);
    }
  }

  @Test
  void releasesAnInitiallyActiveInequalityWhenTheOptimumIsInterior() {
    SQPoptimizer solver = new SQPoptimizer();
    solver.setObjectiveFunction(x -> Math.pow(x[0] - 2.0, 2.0) + Math.pow(x[1] - 3.0, 2.0));
    solver.addEqualityConstraint(x -> x[0] + x[1] - 4.0);
    solver.addInequalityConstraint(x -> x[0] - 1.0);
    solver.setVariableBounds(new double[] { 0.0, 0.0 }, new double[] { 10.0, 10.0 });
    solver.setInitialPoint(new double[] { 1.0, 3.0 });
    solver.setTolerance(1e-8);
    OptimizationResult result = solver.solve();
    assertTrue(result.isConverged());
    assertEquals(1.5, result.getOptimalPoint()[0], 1e-7);
    assertEquals(2.5, result.getOptimalPoint()[1], 1e-7);
    assertEquals(0.5, result.getOptimalValue(), 1e-9);
    assertTrue(result.getKktError() < 1e-8);
  }

  @Test
  void retainsAnInequalityThatIsActiveAtTheOptimum() {
    SQPoptimizer solver = new SQPoptimizer();
    solver.setObjectiveFunction(x -> Math.pow(x[0] + 1.0, 2.0));
    solver.addInequalityConstraint(x -> x[0] - 1.0);
    solver.setInitialPoint(new double[] { 1.0 });
    OptimizationResult result = solver.solve();
    assertTrue(result.isConverged());
    assertEquals(1.0, result.getOptimalPoint()[0], 1e-10);
    assertEquals(4.0, result.getOptimalValue(), 1e-10);
  }
}
