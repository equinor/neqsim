package neqsim.process.util.optimizer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for SQPoptimizer — Sequential Quadratic Programming solver.
 */
public class SQPoptimizerTest {

  @Test
  public void testUnconstrainedQuadratic() {
    // Minimize f(x) = (x0 - 3)^2 + (x1 - 5)^2
    // Optimum at x = [3, 5], f* = 0
    SQPoptimizer sqp = new SQPoptimizer(2);
    sqp.setObjectiveFunction(new SQPoptimizer.ObjectiveFunc() {
      @Override
      public double evaluate(double[] x) {
        return (x[0] - 3.0) * (x[0] - 3.0) + (x[1] - 5.0) * (x[1] - 5.0);
      }
    });

    sqp.setInitialPoint(new double[] {0.0, 0.0});
    SQPoptimizer.OptimizationResult result = sqp.solve();

    Assertions.assertTrue(result.isConverged(), "Should converge for simple quadratic");
    Assertions.assertEquals(3.0, result.getOptimalPoint()[0], 0.1);
    Assertions.assertEquals(5.0, result.getOptimalPoint()[1], 0.1);
    Assertions.assertTrue(result.getOptimalValue() < 0.1, "Optimal value should be near 0");
  }

  @Test
  public void testWithInequalityConstraints() {
    // Minimize f(x) = x0^2 + x1^2
    // Subject to: x0 + x1 >= 4 (i.e., 4 - x0 - x1 <= 0)
    // Optimum at x0 = x1 = 2, f* = 8
    SQPoptimizer sqp = new SQPoptimizer(2);
    sqp.setObjectiveFunction(new SQPoptimizer.ObjectiveFunc() {
      @Override
      public double evaluate(double[] x) {
        return x[0] * x[0] + x[1] * x[1];
      }
    });

    sqp.addInequalityConstraint(new SQPoptimizer.ConstraintFunc() {
      @Override
      public double evaluate(double[] x) {
        return x[0] + x[1] - 4.0; // h(x) >= 0 convention: x0 + x1 >= 4
      }
    });

    sqp.setInitialPoint(new double[] {3.0, 3.0});
    SQPoptimizer.OptimizationResult result = sqp.solve();

    Assertions.assertTrue(result.isConverged(), "Should converge with inequality constraint");
    Assertions.assertEquals(2.0, result.getOptimalPoint()[0], 0.3);
    Assertions.assertEquals(2.0, result.getOptimalPoint()[1], 0.3);
    Assertions.assertTrue(result.getOptimalValue() < 10.0);
  }

  @Test
  public void testWithEqualityConstraint() {
    // Minimize f(x) = (x0 - 1)^2 + (x1 - 2)^2
    // Subject to: x0 + x1 = 5
    // Using Lagrange: x0 = 2, x1 = 3, f* = 2
    SQPoptimizer sqp = new SQPoptimizer(2);
    sqp.setObjectiveFunction(new SQPoptimizer.ObjectiveFunc() {
      @Override
      public double evaluate(double[] x) {
        return (x[0] - 1.0) * (x[0] - 1.0) + (x[1] - 2.0) * (x[1] - 2.0);
      }
    });

    sqp.addEqualityConstraint(new SQPoptimizer.ConstraintFunc() {
      @Override
      public double evaluate(double[] x) {
        return x[0] + x[1] - 5.0;
      }
    });

    sqp.setInitialPoint(new double[] {0.0, 0.0});
    SQPoptimizer.OptimizationResult result = sqp.solve();

    Assertions.assertTrue(result.isConverged(), "Should converge with equality constraint");
    double sum = result.getOptimalPoint()[0] + result.getOptimalPoint()[1];
    Assertions.assertEquals(5.0, sum, 0.3, "Equality constraint should be satisfied");
  }

  @Test
  public void testWithVariableBounds() {
    // Minimize f(x) = (x0 - 10)^2
    // Bounds: 0 <= x0 <= 5
    // Optimum at x0 = 5 (bound-constrained)
    SQPoptimizer sqp = new SQPoptimizer(1);
    sqp.setObjectiveFunction(new SQPoptimizer.ObjectiveFunc() {
      @Override
      public double evaluate(double[] x) {
        return (x[0] - 10.0) * (x[0] - 10.0);
      }
    });

    sqp.setVariableBounds(new double[] {0.0}, new double[] {5.0});
    sqp.setInitialPoint(new double[] {2.0});
    SQPoptimizer.OptimizationResult result = sqp.solve();

    Assertions.assertTrue(result.isConverged());
    Assertions.assertTrue(result.getOptimalPoint()[0] >= -0.01, "Should respect lower bound");
    Assertions.assertTrue(result.getOptimalPoint()[0] <= 5.01, "Should respect upper bound");
  }

  @Test
  public void testOptimizationResultProperties() {
    SQPoptimizer sqp = new SQPoptimizer(1);
    sqp.setObjectiveFunction(new SQPoptimizer.ObjectiveFunc() {
      @Override
      public double evaluate(double[] x) {
        return x[0] * x[0];
      }
    });
    sqp.setInitialPoint(new double[] {5.0});
    SQPoptimizer.OptimizationResult result = sqp.solve();

    Assertions.assertTrue(result.isConverged());
    Assertions.assertTrue(result.getIterations() > 0);
    Assertions.assertTrue(result.getIterations() <= 200);
    Assertions.assertNotNull(result.getOptimalPoint());
    Assertions.assertEquals(1, result.getOptimalPoint().length);
  }
}
