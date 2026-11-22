package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Initialization-order, grid-identity and concurrent first-use contracts for the radial spectrum.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class RadialInfiniteInitializationTest {
  /** Binary name of the independently loaded scalar kernel. */
  private static final String KERNEL = RadialInfiniteInfluence.class.getName();

  /**
   * Isolates initialization from other tests while using the actual compiled production class.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class KernelLoader extends URLClassLoader {
    /**
     * Loads only production classes and bootstrap Java dependencies.
     */
    KernelLoader() {
      super(new URL[] {RadialInfiniteInfluence.class.getProtectionDomain().getCodeSource().getLocation()}, null);
    }

    /**
     * Observes whether the fallback class has been requested by this isolated JVM loader.
     *
     * @return true after fallback class loading
     */
    boolean fullTailLoaded() {
      return findLoadedClass(KERNEL + "$FullTail") != null;
    }
  }

  /**
   * Reads private numerical evidence without exposing or modifying production state.
   *
   * @param type class owning the field
   * @param name private numerical field
   * @return private array for read-only checks
   * @throws ReflectiveOperationException if the declared field is unavailable
   */
  private static double[][] grid(Class<?> type, String name) throws ReflectiveOperationException {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return (double[][]) field.get(null);
  }

  /**
   * Obtains the actual kernel method from a fresh class loader.
   *
   * @param loader isolated production-class loader
   * @return accessible scalar evaluator
   * @throws ReflectiveOperationException if the production contract changes
   */
  private static Method evaluator(KernelLoader loader) throws ReflectiveOperationException {
    Method method = Class.forName(KERNEL, true, loader).getDeclaredMethod("evaluate", double.class);
    method.setAccessible(true);
    return method;
  }

  /**
   * Ordinary times do not load the full tail, and subsequent fallback cannot change their bits.
   *
   * @throws Exception if isolated production invocation fails
   */
  @Test
  void ordinaryQueriesRemainIndependentOfFallbackInitialization() throws Exception {
    try (KernelLoader loader = new KernelLoader()) {
      Method method = evaluator(loader);
      double[] before = (double[]) method.invoke(null, 1.0);
      assertFalse(loader.fullTailLoaded());
      double[] tiny = (double[]) method.invoke(null, Double.MIN_VALUE);
      assertTrue(loader.fullTailLoaded());
      assertEquals(2.0 / Math.sqrt(Math.PI), tiny[0] / Math.sqrt(Double.MIN_VALUE), 1e-12);
      assertEquals(1.0 / Math.sqrt(Math.PI), tiny[1] * Math.sqrt(Double.MIN_VALUE), 1e-12);
      double[] after = (double[]) method.invoke(null, 1.0);
      for (int i = 0; i < before.length; i++) {
        assertEquals(Double.doubleToLongBits(before[i]), Double.doubleToLongBits(after[i]));
      }
    }
  }

  /**
   * Checks exact node/weight identity and domination over every deferred weight and suffix maximum.
   *
   * @throws Exception if private numerical evidence cannot be read
   */
  @Test
  void prefixPreservesGlobalGridAndBoundsAllDeferredWeights() throws Exception {
    double[][] prefix = grid(RadialInfiniteInfluence.class, "TAIL_PREFIX");
    double[][] full = grid(Class.forName(KERNEL + "$FullTail"), "QUADRATURE");
    assertEquals(768, prefix[0].length);
    assertEquals(6656, full[0].length);
    for (int i = 0; i < prefix[0].length; i++) {
      for (int j = 0; j < 2; j++) {
        assertEquals(Double.doubleToLongBits(full[j][i]), Double.doubleToLongBits(prefix[j][i]));
      }
      assertTrue(prefix[2][i] >= full[2][i]);
    }
    double bound = prefix[2][prefix[0].length - 1];
    for (int i = prefix[0].length; i < full[0].length; i++) {
      assertTrue(bound >= full[1][i]);
      assertTrue(bound >= full[2][i]);
    }
  }

  /**
   * Concurrent first fallback initializes one immutable full grid and preserves both small-time limits.
   *
   * @throws Exception if a concurrent query fails or times out
   */
  @Test
  void concurrentFirstFallbackRetainsIdenticalPhysicalLimits() throws Exception {
    try (KernelLoader loader = new KernelLoader()) {
      Method method = evaluator(loader);
      assertFalse(loader.fullTailLoaded());
      ExecutorService executor = Executors.newFixedThreadPool(8);
      CountDownLatch ready = new CountDownLatch(8);
      CountDownLatch start = new CountDownLatch(1);
      try {
        List<Future<double[]>> results = new ArrayList<Future<double[]>>();
        for (int i = 0; i < 8; i++) {
          results.add(executor.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Concurrent start timed out");
            }
            return (double[]) method.invoke(null, Double.MIN_VALUE);
          }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        double[] expected = results.get(0).get(10, TimeUnit.SECONDS);
        for (Future<double[]> result : results) {
          double[] actual = result.get(10, TimeUnit.SECONDS);
          for (int i = 0; i < expected.length; i++) {
            assertEquals(Double.doubleToLongBits(expected[i]), Double.doubleToLongBits(actual[i]));
          }
        }
        assertTrue(loader.fullTailLoaded());
        assertEquals(2.0 / Math.sqrt(Math.PI), expected[0] / Math.sqrt(Double.MIN_VALUE), 1e-12);
        assertEquals(1.0 / Math.sqrt(Math.PI), expected[1] * Math.sqrt(Double.MIN_VALUE), 1e-12);
      } finally {
        start.countDown();
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      }
    }
  }
}
