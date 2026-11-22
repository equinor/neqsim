import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

/** Runs finite-annulus controls against isolated compiled baseline and candidate classes. */

public class IndependentAnnulusReview {
  private static final String PKG = "neqsim.pvtsimulation.reservoirproperties.materialbalance.";
  private static final double[] OBSERVATIONS = {1e-6, 1e-4, .01, 1, 100, 10000};

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  private static void close(double actual, double expected, double absolute, double relative, String message) {
    require(Double.isFinite(actual) && Math.abs(actual - expected) <= absolute + relative * Math.abs(expected),
        message + " actual=" + actual + " expected=" + expected);
  }

  private static double[] evaluate(Method fn, Object model, double t) throws Exception {
    return (double[]) fn.invoke(model, t);
  }

  private static void finite(ClassLoader current) throws Exception {
    Class<?> cls = Class.forName(PKG + "FiniteRadialAquiferInfluence", true, current);
    Constructor<?> make = cls.getConstructor(double.class);
    Method fn = cls.getDeclaredMethod("evaluate", double.class);
    fn.setAccessible(true);
    Method ct = Class.forName(PKG + "VanEverdingenHurstAquifer", true, current).getMethod("cumulativeInfluxCarterTracy",
        double[].class, double[].class, double.class, cls);
    int falls = 0;
    int increases = 0;
    int failedBrackets = 0;
    double worstDrop = 0;
    double worstIncrease = 0;
    double radiusWorstDrop = 0;
    double radiusWorstIncrease = 0;
    double[] maxAbsolute = {0.0, 0.0};
    double[] maxRelative = {0.0, 0.0};
    double[] maxBudgetFraction = {0.0, 0.0};
    int[] violations = {0, 0};
    for (int i = 0; i <= 8192; i++) {
      double radius = 2.0 + i / 1024.0;
      Object model;
      try {
        model = make.newInstance(radius);
      } catch (InvocationTargetException e) {
        failedBrackets++;
        System.err.println("Preparation failed at R=" + radius + " " + e.getCause());
        continue;
      }
      double t = (radius - 1.0) * (radius - 1.0) / 64.0;
      double[] prev = evaluate(fn, model, t);
      double[] next = evaluate(fn, model, Math.nextUp(t));
      for (int j = 0; j < 2; j++) {
        require(Double.isFinite(prev[j]) && prev[j] > 0 && Double.isFinite(next[j]) && next[j] > 0,
            "Invalid junction response at R=" + radius);
        double absolute = Math.abs(next[j] - prev[j]);
        double budgetFraction = absolute / (2e-12 + 2e-11 * prev[j]);
        maxAbsolute[j] = Math.max(maxAbsolute[j], absolute);
        maxRelative[j] = Math.max(maxRelative[j], absolute / prev[j]);
        maxBudgetFraction[j] = Math.max(maxBudgetFraction[j], budgetFraction);
        if (budgetFraction > 1.0) {
          violations[j]++;
        }
      }
      if (next[0] < prev[0]) {
        falls++;
        double drop = (prev[0] - next[0]) / Math.ulp(prev[0]);
        if (drop > worstDrop) {
          worstDrop = drop;
          radiusWorstDrop = radius;
        }
      }
      if (next[1] > prev[1]) {
        increases++;
        double increase = (next[1] - prev[1]) / Math.ulp(prev[1]);
        if (increase > worstIncrease) {
          worstIncrease = increase;
          radiusWorstIncrease = radius;
        }
      }
    }
    System.out.printf(Locale.ROOT, "junction radii=%d bracketFailures=%d pressureFalls=%d worstDropUlps=%s R=%s derivativeRises=%d worstRiseUlps=%s R=%s%n",
        8193, failedBrackets, falls, worstDrop, radiusWorstDrop, increases, worstIncrease, radiusWorstIncrease);
    System.out.println("junction maximum absolute=" + Arrays.toString(maxAbsolute)
        + " relative=" + Arrays.toString(maxRelative) + " budgetFraction=" + Arrays.toString(maxBudgetFraction)
        + " violations=" + Arrays.toString(violations));
    require(failedBrackets == 0 && violations[0] == 0 && violations[1] == 0, "Finite preparation or junction gate failed");
    Object model = make.newInstance(3.5);
    for (double t : new double[] {Double.MIN_VALUE, Math.nextUp(Double.MIN_VALUE), 1e-300, 1e-70, 1, 1e15, Double.MAX_VALUE}) {
      double[] res = evaluate(fn, model, t);
      double[] water = (double[]) ct.invoke(null, new double[] {0, t}, new double[] {0, 1}, 1.0, model);
      for (double value : res) {
        require(Double.isFinite(value) && value > 0.0, "Invalid finite response at t=" + t);
      }
      close(water[1], t / res[0], 0.0, 2e-14, "One-step recurrence at t=" + t);
      System.out.println("one step t=" + Double.toHexString(t) + " influence=" + Arrays.toString(res)
          + " influx=" + water[1] + " direct=" + t / res[0]);
    }
    double first = 1e-300;
    double second = 1e308;
    double[] recovered = (double[]) ct.invoke(null, new double[] {0, first, second, Math.nextUp(second)},
        new double[] {0, 1e200, -1e200, 1e-200}, 1e200, model);
    require(Double.isFinite(recovered[1]) && recovered[1] > 0.0 && recovered[2] == 0.0, "Signed extreme recovery failed");
    close(recovered[3], 5.625, 0, 2e-14, "Late signed recovery");
    System.out.println("signed extremes=" + Arrays.toString(recovered));
    for (double radius : new double[] {2.0485236012510892, 2.1116263964381616, 2.310466835351046,
        2.5723808077592727, 4.872645109967445}) {
      for (double offset : new double[] {-1e-10, -1e-12, -1e-13, -1e-14, 0.0, 1e-14, 1e-13, 1e-12, 1e-10}) {
        double r = radius + offset;
        double t = Math.nextUp((r - 1) * (r - 1) / 64);
        double[] response = evaluate(fn, make.newInstance(r), t);
        for (double value : response) {
          require(Double.isFinite(value) && value > 0.0, "Invalid threshold response at R=" + r);
        }
        System.out.println("threshold=" + r + "," + t + "," + Arrays.toString(response));
      }
    }
    pressureStep(make, fn, ct);
  }

  private static void pressureStep(Constructor<?> make, Method evaluate, Method ct) throws Exception {
    System.out.println("pressureStep:radius,stepsPerDecade,time,zeroOrigin,shortTimeCtStartup");
    for (double radius : new double[] {2.0, 10.0}) {
      Object model = make.newInstance(radius);
      for (int mesh : new int[] {4, 16, 64}) {
        double[] time = new double[10 * mesh + 2];
        double[] drops = new double[time.length];
        for (int i = 0; i <= 10 * mesh; i++) {
          time[i + 1] = Math.pow(10.0, -6.0 + (double) i / mesh);
          if (i % (2 * mesh) == 0) {
            time[i + 1] = OBSERVATIONS[i / (2 * mesh)];
          }
          drops[i + 1] = 1.0;
        }
        double[] water = (double[]) ct.invoke(null, time, drops, 1.0, model);
        double startup = (2.0 * Math.sqrt(Math.PI) / 3.0) * Math.sqrt(time[1]);
        for (int i = 1; i < time.length; i++) {
          if (i > 1) {
            double[] response = evaluate(evaluate, model, time[i]);
            double dt = time[i] - time[i - 1];
            double denominator = response[2] + dt * response[1];
            startup = (response[2] / denominator) * startup + dt / denominator;
          }
          require(Double.isFinite(water[i]) && water[i] > 0.0 && Double.isFinite(startup) && startup > 0.0,
              "Invalid unit pressure-step recurrence");
          if ((i - 1) % (2 * mesh) == 0) {
            System.out.printf(Locale.ROOT, "pressureStep:%s,%d,%s,%s,%s%n", radius, mesh, time[i], water[i], startup);
          }
        }
      }
    }
  }

  private static void legacy(ClassLoader a, ClassLoader b) throws Exception {
    Class<?> baseline = Class.forName(PKG + "VanEverdingenHurstAquifer", true, a);
    Class<?> candidate = Class.forName(PKG + "VanEverdingenHurstAquifer", true, b);
    int tests = 0;
    int differences = 0;
    for (String name : new String[] {"dimensionlessPressure", "dimensionlessPressureDerivative"}) {
      Method left = baseline.getMethod(name, double.class, double.class);
      Method right = candidate.getMethod(name, double.class, double.class);
      for (double radius : new double[] {Double.POSITIVE_INFINITY, -1, 1.5, 2, 3.5, 10, 100}) {
        for (int i = 0; i < 801; i++) {
          double time = Math.pow(10, -320.0 + i * 628.0 / 800.0);
          double l = (double) left.invoke(null, time, radius);
          double r = (double) right.invoke(null, time, radius);
          tests++;
          if (Double.doubleToRawLongBits(l) != Double.doubleToRawLongBits(r)) {
            differences++;
          }
        }
      }
    }
    for (double radius : new double[] {Double.POSITIVE_INFINITY, -1, 1.5, 2, 3.5, 10, 100}) {
      double[] time = {0, 1e-160, 1e-120, 1e-70, .01, 1, 10, 100, 10000};
      double[] pressure = {0, 1, -2, 3, 4, -5, 2, 3, -1};
      Method legacy = baseline.getMethod("cumulativeInfluxCarterTracy", double[].class, double[].class, double.class,
          double.class);
      Method revised = candidate.getMethod("cumulativeInfluxCarterTracy", double[].class, double[].class, double.class,
          double.class);
      tests++;
      if (!Arrays.equals((double[]) legacy.invoke(null, time, pressure, 4.0, radius),
          (double[]) revised.invoke(null, time, pressure, 4.0, radius))) {
        differences++;
      }
      Method before = baseline.getMethod("exportAqutab", double[].class, double.class);
      Method after = candidate.getMethod("exportAqutab", double[].class, double.class);
      tests++;
      if (!before.invoke(null, time, radius).equals(after.invoke(null, time, radius))) {
        differences++;
      }
    }
    System.out.printf(Locale.ROOT, "legacy comparisons=%d differences=%d%n", tests, differences);
    require(differences == 0, "Legacy pressure, slope, recurrence or table output changed");
  }

  public static void main(String[] args) throws Exception {
    require(args.length == 2, "Usage: java IndependentAnnulusReview.java BASELINE_CLASSES CURRENT_CLASSES");
    URL parent = Paths.get(args[0]).toRealPath().toUri().toURL();
    URL current = Paths.get(args[1]).toRealPath().toUri().toURL();
    try (URLClassLoader a = new URLClassLoader(new URL[] {parent}, null);
        URLClassLoader b = new URLClassLoader(new URL[] {current}, null)) {
      legacy(a, b);
      finite(b);
    }
  }
}
