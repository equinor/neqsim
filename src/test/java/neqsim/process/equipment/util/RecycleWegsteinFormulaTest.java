package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Analytic fixed-point checks for the bounded Wegstein update. */
class RecycleWegsteinFormulaTest extends neqsim.NeqSimTest {
  private static void seed(Recycle recycle, String name, double value) throws Exception {
    Field field = Recycle.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(recycle, new double[] {value});
  }

  private static double accelerate(Recycle recycle, double input, double output) throws Exception {
    Method method = Recycle.class.getDeclaredMethod("applyWegsteinAcceleration", double[].class, double[].class);
    method.setAccessible(true);
    return ((double[]) method.invoke(recycle, new double[] {input}, new double[] {output}))[0];
  }

  @Test
  void affineMapReachesAnalyticalFixedPoint() throws Exception {
    Recycle recycle = new Recycle("affine");
    seed(recycle, "previousInputValues", 0.0);
    seed(recycle, "previousOutputValues", 1.0);
    // g(x) = 0.5*x + 1 has the exact fixed point x = 2 and q = -1.
    assertEquals(2.0, accelerate(recycle, 1.0, 1.5), 1e-12);
    assertEquals(-1.0, recycle.getCompositionWegsteinQFactors()[0], 1e-12);
  }

  @Test
  void zeroSlopeUsesDirectSubstitution() throws Exception {
    Recycle recycle = new Recycle("constant");
    seed(recycle, "previousInputValues", 0.0);
    seed(recycle, "previousOutputValues", 2.0);
    assertEquals(2.0, accelerate(recycle, 1.0, 2.0), 1e-12);
    assertEquals(0.0, recycle.getCompositionWegsteinQFactors()[0], 1e-12);
  }

  /**
   * Positive factors damp an oscillating affine map when explicitly enabled.
   *
   * @throws Exception if reflective access to the accelerator fails
   */
  @Test
  void positiveQBoundsAllowOscillationDamping() throws Exception {
    Recycle recycle = new Recycle("oscillating");
    recycle.setWegsteinQMin(0.0);
    recycle.setWegsteinQMax(0.5);
    seed(recycle, "previousInputValues", 0.0);
    seed(recycle, "previousOutputValues", 1.0);
    // g(x) = 1 - x has slope -1, q = 0.5 and fixed point x = 0.5.
    assertEquals(0.5, accelerate(recycle, 1.0, 0.0), 1e-12);
    assertEquals(0.5, recycle.getCompositionWegsteinQFactors()[0], 1e-12);
    assertEquals(0.5, recycle.getWegsteinQFactors()[3], 1e-12);
    assertEquals(0.0, recycle.getWegsteinQFactors()[0], 1e-12);
  }

}
