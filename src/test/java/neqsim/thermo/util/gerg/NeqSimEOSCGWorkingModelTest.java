package neqsim.thermo.util.gerg;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.netlib.util.doubleW;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent native references qualify ownership and concurrent use of EOS-CG working state.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class NeqSimEOSCGWorkingModelTest {
  /**
   * A warmed model is reused on its own thread and invalidated on every thread after a cache clear.
   *
   * @throws Exception if worker evaluation or reflective model lookup fails
   */
  @Test
  void nativeWorkingModelsAreThreadOwnedAndGloballyInvalidated() throws Exception {
    Method resolve = NeqSimEOSCG.class.getDeclaredMethod("getCachedModel");
    resolve.setAccessible(true);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      Object callingThread = resolve.invoke(null);
      Object otherThread = worker.submit(() -> resolve.invoke(null)).get(30, TimeUnit.SECONDS);
      assertSame(callingThread, resolve.invoke(null));
      assertSame(otherThread, worker.submit(() -> resolve.invoke(null)).get(30, TimeUnit.SECONDS));
      assertNotSame(callingThread, otherThread, "Mutable EOS-CG temperature/composition scratch must be thread owned");
      NeqSimEOSCG.clearCache();
      assertNotSame(callingThread, resolve.invoke(null));
      assertNotSame(otherThread, worker.submit(() -> resolve.invoke(null)).get(30, TimeUnit.SECONDS));
    } finally {
      worker.shutdownNow();
    }
  }

  /**
   * Separate phases and a shared read-only utility agree with independently owned native models under concurrency.
   *
   * @throws Exception if concurrent evaluation fails
   */
  @Test
  void concurrentPressurePropertiesAndResidualsMatchIndependentNativeModels() throws Exception {
    int count = 4;
    NeqSimEOSCG[] utilities = new NeqSimEOSCG[count];
    double[][] expected = new double[count][];
    double[][] sharedExpected = new double[count][];
    for (int i = 0; i < count; i++) {
      SystemSrkEos system = new SystemSrkEos(280.0 + 80.0 * i, 20.0 + 40.0 * i);
      system.addComponent("CO2", 0.7 - 0.1 * i);
      system.addComponent("nitrogen", 0.2 + 0.05 * i);
      system.addComponent("SO2", 0.1 + 0.05 * i);
      system.setMixingRule(2);
      system.init(0);
      utilities[i] = new NeqSimEOSCG(system.getPhase(0));
      expected[i] = nativeProperties(280.0 + 80.0 * i, 1.0 + i, utilities[i].normalizedComposition);
    }
    for (int i = 0; i < count; i++) {
      sharedExpected[i] = nativeProperties(280.0, 1.0 + i, utilities[0].normalizedComposition);
    }
    CyclicBarrier barrier = new CyclicBarrier(count);
    ExecutorService pool = Executors.newFixedThreadPool(count);
    List<Callable<Double>> tasks = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final int index = i;
      tasks.add(() -> {
        double largestError = 0.0;
        for (int iteration = 0; iteration < 200; iteration++) {
          barrier.await(30, TimeUnit.SECONDS);
          if (index == 0 && iteration % 40 == 0) {
            NeqSimEOSCG.clearCache();
          }
          largestError = Math.max(largestError,
              relativeError(expected[index][0], utilities[index].getPressure(1.0 + index)));
          double[] properties = utilities[index].propertiesEOSCG(1.0 + index);
          for (int property = 0; property < properties.length; property++) {
            largestError = Math.max(largestError, relativeError(expected[index][property], properties[property]));
          }
          double[] shared = utilities[0].propertiesEOSCG(1.0 + index);
          for (int property = 0; property < shared.length; property++) {
            largestError = Math.max(largestError, relativeError(sharedExpected[index][property], shared[property]));
          }
          double residual = utilities[0].getResidualHelmholtzEnergy(280.0, 1.0 + index);
          largestError = Math.max(largestError,
              relativeError(utilities[0].getAlphares_EOSCG(1.0 + index)[0][0].val, residual));
        }
        return largestError;
      });
    }
    try {
      for (Future<Double> result : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
        double error = result.get();
        assertTrue(error < 1.0e-11,
            "Concurrent EOS-CG result differs from independently owned native reference: " + error);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Evaluates pressure, derivatives and caloric properties with a freshly initialized private native model.
   *
   * @param temperature temperature in kelvin
   * @param density molar density in mol/L
   * @param composition native EOS-CG component fractions
   * @return native property vector in the wrapper's ordering
   */
  private static double[] nativeProperties(double temperature, double density, double[] composition) {
    EOSCG model = new EOSCG();
    model.setup();
    doubleW[] output = new doubleW[16];
    for (int i = 0; i < output.length; i++) {
      output[i] = new doubleW(0.0);
    }
    model.properties(temperature, density, composition, output[0], output[1], output[2], output[3], output[4],
        output[5], output[6], output[7], output[8], output[9], output[10], output[11], output[12], output[13],
        output[14], output[15]);
    double[] result = new double[15];
    for (int i = 0; i < result.length; i++) {
      result[i] = output[i].val;
    }
    return result;
  }

  /**
   * Measures scaled error, making every nonfinite result fail the comparison.
   *
   * @param expected independently calculated value
   * @param actual observed concurrent value
   * @return finite scaled absolute error or positive infinity
   */
  private static double relativeError(double expected, double actual) {
    return Double.isFinite(actual) && Double.isFinite(expected)
        ? Math.abs(expected - actual) / Math.max(1.0, Math.abs(expected))
        : Double.POSITIVE_INFINITY;
  }
}
