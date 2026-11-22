package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import com.google.gson.GsonBuilder;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Optional fresh-JVM cold, deferred-first-use and concurrent-first-use evidence on actual Java 8.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Startup65 {
  /** Prevents construction of the optional driver. */
  private Startup65() {
  }

  /**
   * Measures first use without initializing the radial class before the clock starts.
   *
   * @param args output JSON and first time, or the literal concurrent
   * @throws Exception if native invocation, thread coordination or output fails
   */
  public static void main(String[] args) throws Exception {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("java", System.getProperty("java.runtime.version"));
    result.put("mode", args[1]);
    List<double[]> values = new ArrayList<double[]>();
    long start = System.nanoTime();
    if ("concurrent".equals(args[1])) {
      ExecutorService executor = Executors.newFixedThreadPool(8);
      CountDownLatch ready = new CountDownLatch(8);
      CountDownLatch release = new CountDownLatch(1);
      try {
        List<Future<double[]>> pending = new ArrayList<Future<double[]>>();
        for (int i = 0; i < 8; i++) {
          pending.add(executor.submit(() -> {
            ready.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Start timeout");
            }
            return RadialInfiniteInfluence.evaluate(Double.MIN_VALUE);
          }));
        }
        if (!ready.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Worker timeout");
        }
        start = System.nanoTime();
        release.countDown();
        for (Future<double[]> future : pending) {
          values.add(future.get(10, TimeUnit.SECONDS));
        }
      } finally {
        release.countDown();
        executor.shutdownNow();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Worker shutdown timeout");
        }
      }
    } else {
      values.add(RadialInfiniteInfluence.evaluate(Double.parseDouble(args[1])));
    }
    result.put("coldNs", System.nanoTime() - start);
    result.put("firstValues", values);
    double[] beforeFallback = RadialInfiniteInfluence.evaluate(1.0);
    start = System.nanoTime();
    result.put("tinyValues", RadialInfiniteInfluence.evaluate(Double.MIN_VALUE));
    result.put("deferredTinyNs", System.nanoTime() - start);
    result.put("ordinaryBeforeFallback", beforeFallback);
    result.put("ordinaryAfterFallback", RadialInfiniteInfluence.evaluate(1.0));
    try (FileWriter writer = new FileWriter(args[0])) {
      new GsonBuilder().setPrettyPrinting().create().toJson(result, writer);
    }
  }
}
