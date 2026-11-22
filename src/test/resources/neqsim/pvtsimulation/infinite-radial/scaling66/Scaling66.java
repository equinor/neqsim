package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import com.google.gson.GsonBuilder;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Optional complete native matrix for the same Carter-Tracy recurrence across time and volume scales.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Scaling66 {
  /** Prevents construction of the optional qualification driver. */
  private Scaling66() {
  }

  /**
   * Retains every floating value, including failed baseline nonfinite values, as round-trip strings.
   *
   * @param values native array or null after a reported exception
   * @return numerical strings, or null
   */
  private static String[] strings(double[] values) {
    if (values == null) {
      return null;
    }
    String[] result = new String[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = Double.toString(values[i]);
    }
    return result;
  }

  /**
   * Computes one declared input path and retains all responses or its explicit failure.
   *
   * @param identifier ordered case identity
   * @param exponent decimal final-time exponent
   * @param support aquifer constant in reservoir cubic metres per bar
   * @param scale pressure scale in bar
   * @param profile declared boundary pressure protocol
   * @param count positive uniform interval count
   * @return complete native input/output record
   */
  private static Map<String, Object> row(int identifier, int exponent, double support, double scale,
      String profile, int count) {
    return row(identifier, exponent, support, scale, profile, count, Double.POSITIVE_INFINITY);
  }

  /**
   * Constructs a uniform history for either the infinite kernel or retained bounded-policy arithmetic.
   *
   * @param identifier case identity
   * @param exponent decimal final-time exponent, or Integer.MIN_VALUE for smallest positive double
   * @param support aquifer constant in reservoir cubic metres per bar
   * @param scale pressure scale in bar
   * @param profile declared boundary protocol
   * @param count positive interval count
   * @param radius declared radius convention
   * @return complete native input/output record
   */
  private static Map<String, Object> row(int identifier, int exponent, double support, double scale,
      String profile, int count, double radius) {
    double end = exponent == Integer.MIN_VALUE ? Double.MIN_VALUE : Double.parseDouble("1e" + exponent);
    double[] times = new double[count + 1];
    double[] drops = new double[count + 1];
    for (int i = 1; i <= count; i++) {
      double fraction = i / (double) count;
      times[i] = end * fraction;
      double value = "step".equals(profile) ? 1 : fraction;
      if ("recovery".equals(profile)) {
        value = fraction <= 0.25 ? 1 : fraction <= 0.5 ? 0 : fraction < 1 ? -0.25 : 1;
      }
      drops[i] = scale * value;
    }
    return path(identifier, exponent, support, scale, profile, times, drops, radius);
  }

  /**
   * Computes all responses for an explicitly prescribed history, retaining any exception.
   *
   * @param identifier case identity
   * @param exponent declared scale identity
   * @param support aquifer constant
   * @param scale pressure scale
   * @param profile protocol identity
   * @param times exact prescribed times
   * @param drops exact prescribed total pressure drops
   * @param radius declared radius convention
   * @return complete native input/output record
   */
  private static Map<String, Object> path(int identifier, int exponent, double support, double scale,
      String profile, double[] times, double[] drops, double radius) {
    int count = times.length - 1;
    double[] pressure = new double[count + 1];
    double[] derivative = new double[count + 1];
    for (int i = 1; i <= count; i++) {
      pressure[i] = VanEverdingenHurstAquifer.dimensionlessPressure(times[i], radius);
      derivative[i] = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(times[i], radius);
    }
    Map<String, Object> row = new LinkedHashMap<String, Object>();
    row.put("id", identifier);
    row.put("timeExponent", exponent);
    row.put("supportM3PerBar", support);
    row.put("dropScaleBar", scale);
    row.put("profile", profile);
    row.put("intervals", count);
    row.put("radius", Double.toString(radius));
    row.put("time", strings(times));
    row.put("dropBar", strings(drops));
    row.put("pressure", strings(pressure));
    row.put("derivative", strings(derivative));
    try {
      row.put("waterM3", strings(VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(times, drops,
          support, radius)));
    } catch (RuntimeException exception) {
      row.put("exception", exception.getClass().getName());
      row.put("message", exception.getMessage());
    }
    return row;
  }

  /**
   * Writes the entire frozen matrix, including all native failure evidence.
   *
   * @param args compressed output and exact source identity
   * @throws Exception if an output file cannot be written
   */
  public static void main(String[] args) throws Exception {
    List<Object> rows = new ArrayList<Object>();
    int identifier = 0;
    for (int exponent : new int[] {-320, -300, -200, -100, -70, -60, -50, -20, 0, 20, 100, 200, 300}) {
      for (double support : new double[] {0, 1e-200, 1, 1e200}) {
        for (double scale : new double[] {1e-200, 1, 1e200}) {
          for (String profile : new String[] {"step", "ramp", "recovery"}) {
            for (int count : new int[] {1, 4, 16, 64}) {
              rows.add(row(identifier++, exponent, support, scale, profile, count));
            }
          }
        }
      }
    }
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("source", args[1]);
    result.put("java", System.getProperty("java.runtime.version"));
    result.put("claim", "Native recurrence arithmetic/scale matrix; not measured field accuracy");
    result.put("rows", rows);
    List<Object> limits = new ArrayList<Object>();
    identifier = 0;
    for (int exponent : new int[] {-200, -70}) {
      for (String profile : new String[] {"step", "ramp"}) {
        for (int count : new int[] {32, 64, 128, 256, 512, 1024}) {
          limits.add(row(identifier++, exponent, 1, 1, profile, count));
        }
      }
    }
    result.put("analyticLimits", limits);
    List<Object> bounded = new ArrayList<Object>();
    identifier = 0;
    for (int exponent : new int[] {Integer.MIN_VALUE, -320, -200, -70}) {
      for (double radius : new double[] {3, 10, 100}) {
        bounded.add(row(identifier++, exponent, 1e200, 1e200, "step", 1, radius));
      }
    }
    result.put("boundedArithmetic", bounded);
    List<Object> transitions = new ArrayList<Object>();
    transitions.add(path(0, 0, 1e100, 1e100, "later-overflow", new double[] {0, 1e-200, 1e200},
        new double[] {0, 1e100, 1e100}, Double.POSITIVE_INFINITY));
    transitions.add(path(1, 0, 1e200, 1e200, "negative-projection", new double[] {0, 1e-300, 1e100},
        new double[] {0, 1e200, -1e200}, Double.POSITIVE_INFINITY));
    result.put("transitions", transitions);
    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(args[0])),
        StandardCharsets.UTF_8)) {
      new GsonBuilder().setPrettyPrinting().create().toJson(result, writer);
    }
  }
}
