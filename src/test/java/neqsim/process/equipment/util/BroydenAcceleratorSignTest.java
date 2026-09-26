package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Regression tests for the inverse-Jacobian sign in the recycle Broyden step. */
class BroydenAcceleratorSignTest {

  /** The first inverse-Jacobian update solves a scalar affine fixed point in one step. */
  @Test
  void convergesForScalarAffineFixedPoint() {
    BroydenAccelerator accelerator = new BroydenAccelerator();
    double x = 0.0;
    for (int iteration = 0; iteration < 3; iteration++) {
      x = accelerator.accelerate(new double[] {x}, new double[] {0.5 * x + 1.0})[0];
    }
    assertEquals(2.0, x, 1e-12);
    assertEquals(2.0, accelerator.accelerate(new double[] {x}, new double[] {0.5 * x + 1.0})[0], 1e-12);
  }

  /** A coupled contraction must not turn into a growing residual when acceleration starts. */
  @Test
  void coupledContractionResidualDecreases() {
    BroydenAccelerator accelerator = new BroydenAccelerator();
    double[] x = {0.0, 0.0, 0.0};
    for (int iteration = 0; iteration < 10; iteration++) {
      double[] output = {0.1 * x[0] + 0.05 * x[1] + 1.0, 0.1 * x[1] + 0.05 * x[2] + 2.0,
          0.05 * x[0] + 0.1 * x[2] + 3.0};
      x = accelerator.accelerate(x, output);
    }
    double[] output = {0.1 * x[0] + 0.05 * x[1] + 1.0, 0.1 * x[1] + 0.05 * x[2] + 2.0, 0.05 * x[0] + 0.1 * x[2] + 3.0};
    for (int i = 0; i < x.length; i++) {
      assertTrue(Math.abs(output[i] - x[i]) < 1e-7);
    }
  }
}
