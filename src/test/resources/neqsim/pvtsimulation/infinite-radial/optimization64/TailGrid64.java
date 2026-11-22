package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.lang.reflect.Field;
import java.util.TreeSet;

/**
 * Freezes one common input vector at all representable positive-tail shortcut boundaries.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TailGrid64 {
  /** Prevents construction of the optional input generator. */
  private TailGrid64() {
  }

  /**
   * Reads a private numerical array solely to identify inputs and verify the error-bound certificate.
   *
   * @param name existing private field name
   * @return numerical array, never modified
   * @throws ReflectiveOperationException if the source does not provide the declared numerical grid
   */
  private static double[][] grid(String name) throws ReflectiveOperationException {
    Field field = RadialInfiniteInfluence.class.getDeclaredField(name);
    field.setAccessible(true);
    return (double[][]) field.get(null);
  }

  /**
   * Repeats the implemented floating-point argument evaluation for coverage certification.
   *
   * @param time positive time
   * @param frequency squared middle frequency or unsquared tail frequency
   * @param tail true for square-root-scaled tail arithmetic
   * @return evaluated exponential argument
   */
  private static double argument(double time, double frequency, boolean tail) {
    if (!tail) {
      return time * frequency;
    }
    double scaled = Math.sqrt(time) * frequency;
    return scaled * scaled;
  }

  /**
   * Identifies the lower branch of each actual shortcut.
   *
   * @param argument implemented floating argument
   * @param cutoff shortcut boundary
   * @return true on the implemented lower branch
   */
  private static boolean lower(double argument, double cutoff) {
    return cutoff == 1e-3 ? argument <= cutoff : argument < cutoff;
  }

  /**
   * Adds a certified branch bracket, retaining initial samples and every additional adjacent time.
   *
   * @param times common input vector
   * @param frequency squared middle or unsquared tail frequency
   * @param tail true for tail arithmetic
   * @param cutoff shortcut argument
   * @param statistics underflows, unavailable positive lower side, added steps, largest expansion and verified count
   * @param records complete CSV coverage receipt
   */
  private static void bracket(TreeSet<Double> times, double frequency, boolean tail, double cutoff,
      int[] statistics, StringBuilder records) {
    double center = tail ? Math.pow(Math.sqrt(cutoff) / frequency, 2.0) : cutoff / frequency;
    if (!(center > 0.0) || !Double.isFinite(center)) {
      statistics[0]++;
      records.append(tail).append(',').append(frequency).append(',').append(cutoff)
          .append(",underflow,0,0,0,0\n");
      return;
    }
    double left = Math.max(Double.MIN_VALUE, Math.nextDown(center));
    double right = Math.nextUp(center);
    times.add(left);
    times.add(center);
    times.add(right);
    int steps = 0;
    while (!lower(argument(left, frequency, tail), cutoff) && left > Double.MIN_VALUE) {
      left = Math.nextDown(left);
      times.add(left);
      if (++steps > 64) {
        throw new IllegalStateException("Lower branch not bracketed within64adjacent times");
      }
    }
    while (lower(argument(right, frequency, tail), cutoff)) {
      right = Math.nextUp(right);
      times.add(right);
      if (++steps > 64 || !Double.isFinite(right)) {
        throw new IllegalStateException("Upper branch not bracketed within64adjacent times");
      }
    }
    boolean positiveBracket = lower(argument(left, frequency, tail), cutoff);
    statistics[positiveBracket ? 4 : 1]++;
    statistics[2] += steps;
    statistics[3] = Math.max(statistics[3], steps);
    records.append(tail).append(',').append(frequency).append(',').append(cutoff).append(',')
        .append(positiveBracket ? "bracketed" : "belowMIN").append(',').append(left).append(',')
        .append(right).append(',').append(argument(left, frequency, tail)).append(',')
        .append(argument(right, frequency, tail)).append('\n');
  }

  /**
   * Writes the common time vector and every grid-bound diagnostic used by the proof.
   *
   * @param args prior6278-row CSV, common time output, metadata and compressed coverage receipt
   * @throws Exception if inputs, the frozen private grid or output files cannot be accessed
   */
  public static void main(String[] args) throws Exception {
    TreeSet<Double> times = new TreeSet<Double>();
    try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        times.add(Double.parseDouble(line.substring(0, line.indexOf(','))));
      }
    }
    double[][] tail = grid("TAIL_QUADRATURE");
    double[][] middle = grid("QUADRATURE");
    double combinedWeight = 0.0;
    for (double weight : middle[1]) {
      combinedWeight += weight;
    }
    double minOrdinaryWeight = Double.POSITIVE_INFINITY;
    double minPolynomial = Double.POSITIVE_INFINITY;
    double maxPolynomial = 0.0;
    double maxGap = 0.0;
    double previousLog = Math.log(64.0);
    int[] statistics = new int[5];
    StringBuilder records = new StringBuilder("tail,frequency,cutoff,status,leftTime,rightTime,leftArgument,rightArgument\n");
    for (double frequencySquared : middle[0]) {
      for (double cutoff : new double[] {1e-3, 80.0}) {
        bracket(times, frequencySquared, false, cutoff, statistics, records);
      }
    }
    for (int i = 0; i < tail[0].length; i++) {
      double u = tail[0][i];
      double inverse = (1.0 / u) / u;
      double polynomial = 1.0 + inverse * (-3.0 / 8.0 + inverse * (63.0 / 128.0
          + inverse * (-1899.0 / 1024.0 + inverse * 543483.0 / 32768.0)));
      minPolynomial = Math.min(minPolynomial, polynomial);
      maxPolynomial = Math.max(maxPolynomial, polynomial);
      double ordinaryWeight = tail[1][i] * u * Math.PI / (2.0 * polynomial);
      minOrdinaryWeight = Math.min(minOrdinaryWeight, ordinaryWeight);
      maxGap = Math.max(maxGap, Math.log(u) - previousLog);
      previousLog = Math.log(u);
      combinedWeight += tail[1][i];
      for (double cutoff : new double[] {1e-3, 80.0}) {
        bracket(times, u, true, cutoff, statistics, records);
      }
    }
    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(args[3])), StandardCharsets.UTF_8)) {
      writer.write(records.toString());
    }
    double maximum = 0.0;
    for (int i = tail[0].length - 1; i >= 0; i--) {
      if (!(tail[1][i] > 0)) {
        throw new IllegalStateException("Tail weights must be positive");
      }
      maximum = Math.max(maximum, tail[1][i]);
      if (tail[2][i] != maximum) {
        throw new IllegalStateException("Remaining-weight maximum certificate mismatch");
      }
    }
    if (!(combinedWeight <= 64.0 && minOrdinaryWeight >= .025 && maxGap <= .1
        && minPolynomial >= .999 && maxPolynomial <= 1.0)) {
      throw new IllegalStateException("Frozen tail error-bound certificate failed");
    }
    try (FileWriter writer = new FileWriter(args[1])) {
      for (double time : times) {
        writer.write(Double.toString(time) + "\n");
      }
    }
    try (FileWriter writer = new FileWriter(args[2])) {
      writer.write("rows=" + times.size() + "\ntailNodes=" + tail[0].length + "\ncombinedWeight=" + combinedWeight
          + "\nminOrdinaryWeight=" + minOrdinaryWeight + "\nmaxLogNodeGap=" + maxGap
          + "\nminReciprocalModulusPolynomial=" + minPolynomial + "\nmaxReciprocalModulusPolynomial=" + maxPolynomial
          + "\nunderflowedBoundaryInputs=" + statistics[0] + "\nbelowPositiveMINTransitions=" + statistics[1]
          + "\nextraAdjacentSteps=" + statistics[2] + "\nmaxExtraSteps=" + statistics[3]
          + "\nverifiedPositiveBrackets=" + statistics[4] + "\nsuffixMaximumVerified=true\n");
    }
  }
}
