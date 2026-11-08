import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

/**
 * Optional complete physical-input matrix and bounded radial-workflow timing control.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class PhysicalInputs {
  /** Prevents construction. */
  private PhysicalInputs() {
  }

  /**
   * Records a declared input without suppressing any nonfinite output or explicit rejection.
   *
   * @param out output table
   * @param id case identity
   * @param method time or storage
   * @param input physical factors in public API order
   */
  private static void row(PrintWriter out, int id, String method, double[] input) {
    out.print(id + "," + method + ",");
    for (int i = 0; i < input.length; i++) {
      if (i > 0) {
        out.print(';');
      }
      out.print(Double.toString(input[i]));
    }
    try {
      double value = "time".equals(method)
          ? VanEverdingenHurstAquifer.dimensionlessTime(input[0], input[1], input[2], input[3], input[4], input[5])
          : VanEverdingenHurstAquifer.aquiferConstant(input[0], input[1], input[2], input[3], input[4]);
      out.println("," + Double.toString(value) + ",");
    } catch (RuntimeException error) {
      out.println(",," + error.getClass().getSimpleName());
    }
  }

  /**
   * Declares all time or storage inputs independently of candidate results.
   *
   * @param method time or storage
   * @return ordered input matrix, including invalid and zero controls
   */
  private static List<double[]> inputs(String method) {
    boolean time = "time".equals(method);
    List<double[]> result = new ArrayList<double[]>();
    double[] base = time ? new double[] {1e-13, 86400, 0.2, 5e-4, 1e-9, 1000}
        : new double[] {0.2, 1e-4, 30, 1000, 180};
    result.add(base.clone());
    for (int axis = 0; axis < base.length; axis++) {
      for (double value : new double[] {0, -1, Double.MIN_VALUE, Double.MAX_VALUE, Double.NaN,
          Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
        double[] input = base.clone();
        input[axis] = value;
        result.add(input);
      }
      for (int power : new int[] {-320, -250, -200, -160, -100, -50, -10, 0, 10, 50, 100, 160, 200, 250, 300}) {
        double[] input = base.clone();
        input[axis] = Double.parseDouble("1e" + power);
        result.add(input);
      }
    }
    if (time) {
      for (double[] input : new double[][] {{1e-100, 1e100, 1, 1e-200, 1e-200, 1e200},
          {1e200, 1e200, 1, 1e100, 1e100, 1e100}, {1, 1, 1, 1e200, 1e200, 1e-200},
          {1e-200, 1e-200, 1, 1e-200, 1e-200, 1}, {1, 1, 1, 1e-160, 1e-160, 1e160},
          {Double.MIN_VALUE, 1, 1, 1, 1, 1}, {Double.MIN_VALUE, Double.MIN_VALUE, 1, 1, 1, 1},
          {Double.MAX_VALUE, Double.MAX_VALUE, 1, 1, 1, 1}}) {
        result.add(input);
      }
      for (double phi : new double[] {0.7, 0.73, 1}) {
        double center = 2 / phi;
        for (double mu : new double[] {Math.nextAfter(center, 0), center,
            Math.nextAfter(center, Double.POSITIVE_INFINITY)}) {
          result.add(new double[] {Double.MIN_VALUE, 1, phi, mu, 1, 1});
        }
      }
      result.add(new double[] {Double.MIN_VALUE, 1, 0.9171076874610481, 4.892793041582901, 0.44571044916080677, 1});
      result.add(new double[] {Double.MAX_VALUE, 1, 0.8061243467211738, 4.871567827453244, 0.25464151433530974, 1});
      result.add(new double[] {Math.scalb((1L << 27) - 1.0, 485), Math.scalb((1L << 27) + 1.0, 485), 1, 1, 1, 1});
      for (double multiplier : new double[] {3, 5, 7}) {
        result.add(new double[] {Double.MIN_VALUE, multiplier, 1, 2, 1, 1});
      }
      result.add(new double[] {Double.MAX_VALUE, 1, 1, 1, 1, 1});
      result.add(new double[] {Double.MAX_VALUE, 1, 1, 2, 1, 1});
      result.add(new double[] {Math.nextDown(Double.MAX_VALUE), 1, 1, 1, 1, 1});
    } else {
      for (double[] input : new double[][] {{1e-200, 1e-200, 1e200, 1e100, 360},
          {1, 1e200, 1e200, 1e-200, 360}, {1, 1, 1e200, 1e50, Double.MIN_VALUE},
          {1, 1e-160, 1e-160, 1e80, 360}, {1, Double.MIN_VALUE, 1, 1, 360},
          {Double.MIN_VALUE, Double.MIN_VALUE, 1, 1, 360}, {1, Double.MAX_VALUE, Double.MAX_VALUE, 1, 360}}) {
        result.add(input);
      }
      for (double phi : new double[] {0.7, 0.73, 1}) {
        double center = .5 / (2 * Math.PI * phi);
        for (double h : new double[] {Math.nextAfter(center, 0), center,
            Math.nextAfter(center, Double.POSITIVE_INFINITY)}) {
          result.add(new double[] {phi, Double.MIN_VALUE, h, 1, 360});
        }
      }
    }
    for (double radius : new double[] {100, 1000, 3000}) {
      for (double phi : new double[] {0.05, 0.2, 0.8}) {
        for (double angle : new double[] {30, 180, 360}) {
          result.add(time ? new double[] {1e-13, 86400, phi, 5e-4, 1e-9, radius}
              : new double[] {phi, 1e-4, 30, radius, angle});
        }
      }
    }
    return result;
  }

  /**
   * Executes the measured transformation-only control with changing inner radius.
   *
   * @return complete checksum
   */
  private static double transformations() {
    double total = 0;
    for (int i = 0; i < 500000; i++) {
      double radius = 3000 + i % 7;
      total += VanEverdingenHurstAquifer.dimensionlessTime(1e-13, 86400, .2, 5e-4, 1e-9, radius);
      total += VanEverdingenHurstAquifer.aquiferConstant(.2, 1e-4, 30, radius, 180);
    }
    return total;
  }

  /**
   * Executes the complete physical-input conversion through a 1000-interval CT history.
   *
   * @return complete checksum
   */
  private static double histories() {
    double checksum = 0;
    for (int repeat = 0; repeat < 20; repeat++) {
      double[] time = new double[1001];
      double[] drop = new double[1001];
      for (int i = 1; i < time.length; i++) {
        time[i] = VanEverdingenHurstAquifer.dimensionlessTime(1e-13, i * 86400., .2, 5e-4, 1e-9, 1000);
        drop[i] = 10 + i * .01;
      }
      double u = VanEverdingenHurstAquifer.aquiferConstant(.2, 1e-4, 30, 1000, 180);
      double[] water = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, drop, u,
          Double.POSITIVE_INFINITY);
      for (double value : water) {
        checksum += value;
      }
    }
    return checksum;
  }

  /**
   * Records every native case and all three warmups/seven measured samples.
   *
   * @param args native/runtime table paths, or --cold and a cold-call table path
   * @throws IOException if a table cannot be written
   */
  public static void main(String[] args) throws IOException {
    if ("--cold".equals(args[0])) {
      long start = System.nanoTime();
      double checksum = VanEverdingenHurstAquifer.dimensionlessTime(1e-13, 86400, .2, 5e-4, 1e-9, 1000)
          + VanEverdingenHurstAquifer.aquiferConstant(.2, 1e-4, 30, 1000, 180);
      long elapsed = System.nanoTime() - start;
      try (PrintWriter out = new PrintWriter(new FileWriter(args[1]))) {
        out.println("nanos,checksum");
        out.println(elapsed + "," + checksum);
      }
      return;
    }
    try (PrintWriter out = new PrintWriter(new FileWriter(args[0]))) {
      out.println("id,method,inputs,result,error");
      int id = 0;
      for (String method : new String[] {"time", "storage"}) {
        for (double[] input : inputs(method)) {
          row(out, id++, method, input);
        }
      }
    }
    try (PrintWriter out = new PrintWriter(new FileWriter(args[1]))) {
      out.println("workload,sample,nanos,checksum");
      for (String workload : new String[] {"transformations", "histories"}) {
        for (int sample = -3; sample < 7; sample++) {
          long start = System.nanoTime();
          double checksum = "histories".equals(workload) ? histories() : transformations();
          out.println(workload + "," + sample + "," + (System.nanoTime() - start) + "," + checksum);
        }
      }
    }
  }
}
