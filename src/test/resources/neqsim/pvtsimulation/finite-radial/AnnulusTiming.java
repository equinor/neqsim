import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.FiniteRadialAquiferInfluence;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

/** Workload for paired scalar radial-query and prepared-model timings. */
public final class AnnulusTiming {
  private static final double RADIUS = 3.5;
  private static final int WARMUP_REPETITIONS = 5;
  private static final int MEASURED_REPETITIONS = 9;
  private static final int TIMED_QUERIES = 800;
  private static volatile double sink;

  private AnnulusTiming() {
  }

  private static double sample(DoubleUnaryOperator pressure, double from, double ratio, int count) {
    double sum = 0.0;
    long start = System.nanoTime();
    for (int i = 0; i < count; i++) {
      double time = from * (1.0 + ratio * (i % 173) / 172.0);
      sum += pressure.applyAsDouble(time);
    }
    double elapsed = (System.nanoTime() - start) / (double) count;
    sink = sum;
    return elapsed;
  }

  private static double median(double[] values) {
    double[] sorted = values.clone();
    Arrays.sort(sorted);
    return sorted[sorted.length / 2];
  }

  private static void paired(String name, DoubleUnaryOperator parent, DoubleUnaryOperator current, double from,
      double ratio, double maximumRatio) {
    double[] baseline = new double[MEASURED_REPETITIONS];
    double[] candidate = new double[MEASURED_REPETITIONS];
    for (int i = -WARMUP_REPETITIONS; i < MEASURED_REPETITIONS; i++) {
      double a;
      double b;
      if (i % 2 == 0) {
        a = sample(parent, from, ratio, TIMED_QUERIES);
        b = sample(current, from, ratio, TIMED_QUERIES);
      } else {
        b = sample(current, from, ratio, TIMED_QUERIES);
        a = sample(parent, from, ratio, TIMED_QUERIES);
      }
      if (i >= 0) {
        baseline[i] = a;
        candidate[i] = b;
        System.out.printf("%s,%d,%.3f,%.3f%n", name, i + 1, a, b);
      }
    }
    double result = median(candidate) / median(baseline);
    System.out.printf("%s,median,%.3f,%.3f,%.6f%n", name, median(baseline), median(candidate), result);
    if (result > maximumRatio) {
      throw new AssertionError(name + " ratio " + result + " exceeds " + maximumRatio);
    }
  }

  private static void preparation() {
    double[] elapsed = new double[MEASURED_REPETITIONS];
    int perRound = 16;
    for (int i = -WARMUP_REPETITIONS; i < MEASURED_REPETITIONS; i++) {
      double sum = 0.0;
      long start = System.nanoTime();
      for (int j = 0; j < perRound; j++) {
        FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(2.0 + 8.0 * j / (perRound - 1.0));
        sum += annulus.dimensionlessPressure(10.0);
      }
      double duration = (System.nanoTime() - start) / (double) perRound;
      sink = sum;
      if (i >= 0) {
        elapsed[i] = duration;
        System.out.printf("prepare,%d,%.3f%n", i + 1, duration);
      }
    }
    double result = median(elapsed);
    System.out.printf("prepare,median,%.3f%n", result);
    if (result > 20.0e6) {
      throw new AssertionError("preparation exceeds 20 ms: " + result);
    }
  }

  private static void verifyLegacy(DoubleUnaryOperator parent, DoubleUnaryOperator current) {
    for (int i = 0; i <= 172; i++) {
      double t = 1.0e-6 * Math.pow(10.0, i / 17.2);
      if (Double.doubleToLongBits(parent.applyAsDouble(t)) != Double.doubleToLongBits(current.applyAsDouble(t))) {
        throw new AssertionError("legacy pressure differs at " + t);
      }
    }
  }

  /** Runs the measured workload; the argument is a directory of isolated baseline classes. */
  public static void main(String[] args) throws Exception {
    URL[] baselinePath = {Paths.get(args[0]).toUri().toURL()};
    try (URLClassLoader loader = new URLClassLoader(baselinePath, null)) {
      Class<?> type = loader.loadClass(VanEverdingenHurstAquifer.class.getName());
      java.lang.reflect.Method method = type.getMethod("dimensionlessPressure", double.class, double.class);
      java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.publicLookup().unreflect(method);
      java.lang.invoke.MethodHandle bound = java.lang.invoke.MethodHandles.insertArguments(handle, 1, RADIUS);
      DoubleUnaryOperator parent = time -> {
        try {
          return (double) bound.invokeExact(time);
        } catch (Throwable failure) {
          throw new IllegalStateException(failure);
        }
      };
      DoubleUnaryOperator current = time -> VanEverdingenHurstAquifer.dimensionlessPressure(time, RADIUS);
      FiniteRadialAquiferInfluence annulus = new FiniteRadialAquiferInfluence(RADIUS);
      verifyLegacy(parent, current);
      paired("legacy", parent, current, 0.001, 10000.0, 1.20);
      paired("early", parent, annulus::dimensionlessPressure, 0.001, 60.0, 1.25);
      paired("late", parent, annulus::dimensionlessPressure, 1.0, 999.0, 0.50);
      preparation();
      if (!Double.isFinite(sink)) {
        throw new AssertionError("invalid workload");
      }
    }
  }
}
