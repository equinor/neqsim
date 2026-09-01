package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

/** Immutable, time-aligned species history from a transient compositional pipe network run. */
public final class TransientCompositionalPipeNetworkHistory implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double[] elapsedTimeSeconds;
  private final String[] componentNames;
  private final Map<String, double[][]> nodeMassFractionHistory;
  private final Map<String, List<TransientSpeciesConservationReport>> edgeReports;
  private final Map<String, List<TransientSpeciesConservationReport>> junctionReports;
  private final List<TransientSpeciesConservationReport> networkReports;

  TransientCompositionalPipeNetworkHistory(double[] elapsedTimeSeconds, String[] componentNames,
      Map<String, double[][]> nodeMassFractionHistory,
      Map<String, List<TransientSpeciesConservationReport>> edgeReports,
      Map<String, List<TransientSpeciesConservationReport>> junctionReports,
      List<TransientSpeciesConservationReport> networkReports) {
    this.elapsedTimeSeconds = copy(elapsedTimeSeconds);
    this.componentNames = copy(componentNames);
    this.nodeMassFractionHistory = copyProfiles(nodeMassFractionHistory);
    this.edgeReports = copyReports(edgeReports);
    this.junctionReports = copyReports(junctionReports);
    this.networkReports = Collections
        .unmodifiableList(new ArrayList<TransientSpeciesConservationReport>(networkReports));
  }

  /**
   * Create an empty history before the first run.
   *
   * @return immutable empty history
   */
  public static TransientCompositionalPipeNetworkHistory empty() {
    return new TransientCompositionalPipeNetworkHistory(new double[0], new String[0],
        Collections.<String, double[][]>emptyMap(),
        Collections.<String, List<TransientSpeciesConservationReport>>emptyMap(),
        Collections.<String, List<TransientSpeciesConservationReport>>emptyMap(),
        Collections.<TransientSpeciesConservationReport>emptyList());
  }

  /** @return accepted-step end times in seconds */
  public double[] getElapsedTimeSeconds() {
    return copy(elapsedTimeSeconds);
  }

  /** @return deterministic component-name order used by all arrays */
  public String[] getComponentNames() {
    return copy(componentNames);
  }

  /**
   * Get all component mass fractions at a node.
   *
   * @param nodeName node name
   * @return defensive time-by-component array
   */
  public double[][] getNodeMassFractionHistory(String nodeName) {
    double[][] values = nodeMassFractionHistory.get(nodeName);
    if (values == null) {
      throw new IllegalArgumentException("No species history exists for node '" + nodeName + "'");
    }
    return copy(values);
  }

  /**
   * Get one named component's node history.
   *
   * @param nodeName node name
   * @param componentName NeqSim component name
   * @return defensive time-aligned mass-fraction series
   */
  public double[] getNodeMassFractionHistory(String nodeName, String componentName) {
    int componentIndex = componentIndex(componentName);
    double[][] profile = getNodeMassFractionHistory(nodeName);
    double[] result = new double[profile.length];
    for (int timeIndex = 0; timeIndex < profile.length; timeIndex++) {
      result[timeIndex] = profile[timeIndex][componentIndex];
    }
    return result;
  }

  /**
   * Get immutable edge reports as a defensive array.
   *
   * @param edgeName edge name
   * @return reports aligned with {@link #getElapsedTimeSeconds()}
   */
  public TransientSpeciesConservationReport[] getEdgeReports(String edgeName) {
    return reportArray(edgeReports, edgeName, "edge");
  }

  /**
   * Get immutable junction reports as a defensive array.
   *
   * @param nodeName junction name
   * @return reports aligned with {@link #getElapsedTimeSeconds()}
   */
  public TransientSpeciesConservationReport[] getJunctionReports(String nodeName) {
    return reportArray(junctionReports, nodeName, "junction");
  }

  /** @return defensive array of cumulative whole-network reports */
  public TransientSpeciesConservationReport[] getNetworkReports() {
    return networkReports.toArray(new TransientSpeciesConservationReport[networkReports.size()]);
  }

  /**
   * Get the last whole-network report.
   *
   * @return final cumulative report
   * @throws IllegalStateException before a run
   */
  public TransientSpeciesConservationReport getFinalNetworkReport() {
    if (networkReports.isEmpty()) {
      throw new IllegalStateException("Transient compositional network has not run");
    }
    return networkReports.get(networkReports.size() - 1);
  }

  /**
   * Serialize all histories for Python/JPype capture.
   *
   * @return stable, pretty-printed JSON
   */
  public String toJson() {
    JsonSerializer<Double> finiteDoubleSerializer = (value, type,
        context) -> value != null && Double.isFinite(value) ? new JsonPrimitive(value) : JsonNull.INSTANCE;
    GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(Double.class, finiteDoubleSerializer);
    gsonBuilder.registerTypeAdapter(Double.TYPE, finiteDoubleSerializer);
    gsonBuilder.serializeNulls();
    gsonBuilder.setPrettyPrinting();
    return gsonBuilder.create().toJson(this);
  }

  private int componentIndex(String componentName) {
    for (int index = 0; index < componentNames.length; index++) {
      if (componentNames[index].equals(componentName)) {
        return index;
      }
    }
    throw new IllegalArgumentException("Component '" + componentName + "' is not present in this network history");
  }

  private static TransientSpeciesConservationReport[] reportArray(
      Map<String, List<TransientSpeciesConservationReport>> reports, String name, String kind) {
    List<TransientSpeciesConservationReport> values = reports.get(name);
    if (values == null) {
      throw new IllegalArgumentException("No " + kind + " species reports exist for '" + name + "'");
    }
    return values.toArray(new TransientSpeciesConservationReport[values.size()]);
  }

  private static Map<String, double[][]> copyProfiles(Map<String, double[][]> profiles) {
    Map<String, double[][]> result = new LinkedHashMap<String, double[][]>();
    for (Map.Entry<String, double[][]> entry : profiles.entrySet()) {
      result.put(entry.getKey(), copy(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<String, List<TransientSpeciesConservationReport>> copyReports(
      Map<String, List<TransientSpeciesConservationReport>> reports) {
    Map<String, List<TransientSpeciesConservationReport>> result = new LinkedHashMap<>();
    for (Map.Entry<String, List<TransientSpeciesConservationReport>> entry : reports.entrySet()) {
      result.put(entry.getKey(),
          Collections.unmodifiableList(new ArrayList<TransientSpeciesConservationReport>(entry.getValue())));
    }
    return Collections.unmodifiableMap(result);
  }

  private static double[] copy(double[] values) {
    return values == null ? new double[0] : Arrays.copyOf(values, values.length);
  }

  private static String[] copy(String[] values) {
    return values == null ? new String[0] : Arrays.copyOf(values, values.length);
  }

  private static double[][] copy(double[][] values) {
    if (values == null) {
      return new double[0][0];
    }
    double[][] result = new double[values.length][];
    for (int index = 0; index < values.length; index++) {
      result[index] = copy(values[index]);
    }
    return result;
  }
}
