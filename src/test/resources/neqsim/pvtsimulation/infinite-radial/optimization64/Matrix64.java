package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.lang.reflect.Field;
import java.util.TreeSet;

/**
 * Complete before/after numerical matrix at every controlled shortcut boundary.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Matrix64 {
  /** Prevents construction of this optional command-line driver. */
  private Matrix64() {
  }

  /**
   * Adds the adjacent representable times around a positive numerical boundary.
   *
   * @param times sorted set of times
   * @param time finite positive boundary time
   */
  private static void boundary(TreeSet<Double> times, double time) {
    times.add(Math.nextDown(time));
    times.add(time);
    times.add(Math.nextUp(time));
  }

  /**
   * Writes all native values; reflection only identifies the existing private quadrature's input boundaries.
   *
   * @param args output CSV (optionally gzip), metadata, exact revision and optional common input vector
   * @throws Exception if the unchanged quadrature metadata or output files cannot be accessed
   */
  public static void main(String[] args) throws Exception {
    Field field = RadialInfiniteInfluence.class.getDeclaredField("QUADRATURE");
    field.setAccessible(true);
    double[][] quadrature = (double[][]) field.get(null);
    double weight = 0.0;
    TreeSet<Double> times = new TreeSet<Double>();
    for (int i = 0; i <= 2000; i++) {
      times.add(Math.exp(-740.0 + 1400.0 * i / 2000.0));
    }
    times.add(Double.MIN_VALUE);
    times.add(Double.MAX_VALUE);
    for (int i = 0; i < quadrature[0].length; i++) {
      if (!(quadrature[0][i] > 0) || !(quadrature[1][i] > 0)) {
        throw new IllegalStateException("Positive spectral nodes and weights required");
      }
      weight += quadrature[1][i];
      boundary(times, 1e-3 / quadrature[0][i]);
      boundary(times, 80.0 / quadrature[0][i]);
    }
    boundary(times, 1e-3 / Math.exp(-80.0));
    if (!(weight <= 64.0)) {
      throw new IllegalStateException("Frozen positive spectral-weight bound exceeded");
    }
    if (args.length > 3) {
      times.clear();
      try (BufferedReader reader = new BufferedReader(new FileReader(args[3]))) {
        String line;
        while ((line = reader.readLine()) != null) {
          times.add(Double.parseDouble(line));
        }
      }
    }
    try (Writer writer = args[0].endsWith(".gz")
        ? new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(args[0])), StandardCharsets.UTF_8)
        : new FileWriter(args[0])) {
      writer.write("time,pressure,derivative\n");
      for (double time : times) {
        double p = VanEverdingenHurstAquifer.dimensionlessPressure(time, Double.POSITIVE_INFINITY);
        double d = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, Double.POSITIVE_INFINITY);
        writer.write(Double.toString(time) + "," + Double.toString(p) + "," + Double.toString(d) + "\n");
      }
    }
    try (FileWriter writer = new FileWriter(args[1])) {
      writer.write("source=" + args[2] + "\njava=" + System.getProperty("java.runtime.version")
          + "\nrows=" + times.size() + "\nweightSum=" + weight + "\nnodes=" + quadrature[0].length + "\n");
    }
  }
}
