import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.pvtsimulation.reservoirproperties.materialbalance.VanEverdingenHurstAquifer;

/**
 * Optional output-complete native qualification driver, compiled outside the Maven test tree.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class Qualification {
  /** Observable sink prevents dead-code removal in retained timing samples. */
  private static volatile double sink;

  /** Prevents construction of the command-line utility. */
  private Qualification() {
  }

  /**
   * Constructs a logarithmic time mesh from a bookkeeping zero.
   *
   * @param intervals number of intervals
   * @param last final dimensionless time
   * @return dimensionless time mesh
   */
  private static double[] times(int intervals, double last) {
    double[] time = new double[intervals + 1];
    for (int i = 1; i <= intervals; i++) {
      time[i] = Math.exp(Math.log(1e-16) + (Math.log(last) - Math.log(1e-16)) * i / intervals);
    }
    return time;
  }

  /**
   * Constructs a unit boundary-pressure step.
   *
   * @param intervals number of intervals
   * @return pressure drop in bar
   */
  private static double[] drops(int intervals) {
    double[] result = new double[intervals + 1];
    java.util.Arrays.fill(result, 1.0);
    return result;
  }

  /**
   * Writes all native reference comparisons, CT histories and timing samples.
   *
   * @param args reference JSON input and native JSON output paths
   * @throws java.io.IOException if a qualification file cannot be read or written
   */
  public static void main(String[] args) throws java.io.IOException {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("java", System.getProperty("java.runtime.version"));
    long start = System.nanoTime();
    sink = VanEverdingenHurstAquifer.dimensionlessPressure(1.0, Double.POSITIVE_INFINITY);
    result.put("coldFirstQueryNs", System.nanoTime() - start);
    List<Object> rows = new ArrayList<Object>();
    try (FileReader reader = new FileReader(args[0])) {
      JsonObject reference = JsonParser.parseReader(reader).getAsJsonObject();
      for (JsonElement element : reference.getAsJsonArray("rows")) {
        JsonObject fixture = element.getAsJsonObject();
        double time = fixture.get("time").getAsDouble();
        double p = VanEverdingenHurstAquifer.dimensionlessPressure(time, Double.POSITIVE_INFINITY);
        double d = VanEverdingenHurstAquifer.dimensionlessPressureDerivative(time, Double.POSITIVE_INFINITY);
        double pRef = fixture.getAsJsonObject("pressure").get("digits35").getAsDouble();
        double dRef = fixture.getAsJsonObject("derivative").get("digits35").getAsDouble();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("time", time);
        row.put("pressure", p);
        row.put("derivative", d);
        row.put("pressureAbsoluteError", Math.abs(p - pRef));
        row.put("derivativeAbsoluteError", Math.abs(d - dRef));
        row.put("pressureRelativeError", Math.abs(p / pRef - 1.0));
        row.put("derivativeRelativeError", Math.abs(d / dRef - 1.0));
        row.put("accepted", Math.abs(p - pRef) <= 1e-7 + 1e-6 * pRef
            && Math.abs(d - dRef) <= 1e-6 + 1e-5 * dRef);
        rows.add(row);
      }
    }
    result.put("referenceComparisons", rows);
    List<Object> influx = new ArrayList<Object>();
    for (double last : new double[] {1e-8, 1e-6, 1e-4, .01, 1, 100, 10000}) {
      for (int intervals : new int[] {32, 64, 128, 256, 512, 1024}) {
        double[] time = times(intervals, last);
        double[] water = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, drops(intervals),
            1.0, Double.POSITIVE_INFINITY);
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("lastTime", last);
        row.put("intervals", intervals);
        row.put("influx", water[intervals]);
        row.put("time", time);
        row.put("history", water);
        influx.add(row);
      }
    }
    result.put("unitStepCarterTracy", influx);
    List<Long> queryNs = new ArrayList<Long>();
    List<Long> influxNs = new ArrayList<Long>();
    double[] time = times(1000, 10000);
    double[] delta = drops(1000);
    for (int pass = -3; pass < 7; pass++) {
      start = System.nanoTime();
      double sum = 0.0;
      for (int i = 0; i < 20000; i++) {
        double t = Math.exp(-18.0 + 36.0 * (i % 1000) / 999.0);
        sum += VanEverdingenHurstAquifer.dimensionlessPressure(t, Double.POSITIVE_INFINITY);
        sum += VanEverdingenHurstAquifer.dimensionlessPressureDerivative(t, Double.POSITIVE_INFINITY);
      }
      sink = sum;
      long query = System.nanoTime() - start;
      start = System.nanoTime();
      sink = VanEverdingenHurstAquifer.cumulativeInfluxCarterTracy(time, delta,
          1.0, Double.POSITIVE_INFINITY)[1000];
      long elapsed = System.nanoTime() - start;
      if (pass >= 0) {
        queryNs.add(query);
        influxNs.add(elapsed);
      }
    }
    result.put("separatePressureDerivative20000QueriesNs", queryNs);
    result.put("carterTracy1000IntervalsNs", influxNs);
    result.put("checksum", sink);
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try (FileWriter writer = new FileWriter(args[1])) {
      gson.toJson(result, writer);
    }
  }
}
