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

/** Immutable, time-aligned hydraulic, linepack, and composition history for a transient gas network. */
public final class TransientGasNetworkHistory implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double[] elapsedTimeSeconds;
  private final String[] componentNames;
  private final Map<String, double[]> nodePressureBaraHistory;
  private final Map<String, double[][]> nodeMassFractionHistory;
  private final Map<String, double[]> edgeInletMassFlowKgSHistory;
  private final Map<String, double[]> edgeAverageMassFlowKgSHistory;
  private final Map<String, double[]> edgeOutletMassFlowKgSHistory;
  private final Map<String, double[]> edgeLinepackKgHistory;
  private final List<TransientGasNetworkStepReport> stepReports;
  private final Map<String, List<TransientSpeciesConservationReport>> edgeSpeciesReports;
  private final Map<String, List<TransientSpeciesConservationReport>> junctionSpeciesReports;
  private final List<TransientSpeciesConservationReport> networkSpeciesReports;

  TransientGasNetworkHistory(double[] elapsedTimeSeconds, String[] componentNames,
      Map<String, double[]> nodePressureBaraHistory, Map<String, double[][]> nodeMassFractionHistory,
      Map<String, double[]> edgeInletMassFlowKgSHistory, Map<String, double[]> edgeAverageMassFlowKgSHistory,
      Map<String, double[]> edgeOutletMassFlowKgSHistory, Map<String, double[]> edgeLinepackKgHistory,
      List<TransientGasNetworkStepReport> stepReports,
      Map<String, List<TransientSpeciesConservationReport>> edgeSpeciesReports,
      Map<String, List<TransientSpeciesConservationReport>> junctionSpeciesReports,
      List<TransientSpeciesConservationReport> networkSpeciesReports) {
    this.elapsedTimeSeconds = copy(elapsedTimeSeconds);
    this.componentNames = copy(componentNames);
    this.nodePressureBaraHistory = copySeries(nodePressureBaraHistory);
    this.nodeMassFractionHistory = copyProfiles(nodeMassFractionHistory);
    this.edgeInletMassFlowKgSHistory = copySeries(edgeInletMassFlowKgSHistory);
    this.edgeAverageMassFlowKgSHistory = copySeries(edgeAverageMassFlowKgSHistory);
    this.edgeOutletMassFlowKgSHistory = copySeries(edgeOutletMassFlowKgSHistory);
    this.edgeLinepackKgHistory = copySeries(edgeLinepackKgHistory);
    this.stepReports = Collections.unmodifiableList(new ArrayList<TransientGasNetworkStepReport>(stepReports));
    this.edgeSpeciesReports = copyReports(edgeSpeciesReports);
    this.junctionSpeciesReports = copyReports(junctionSpeciesReports);
    this.networkSpeciesReports = Collections
        .unmodifiableList(new ArrayList<TransientSpeciesConservationReport>(networkSpeciesReports));
  }

  /**
   * Create an empty history before the first run.
   *
   * @return immutable empty history
   */
  public static TransientGasNetworkHistory empty() {
    return new TransientGasNetworkHistory(new double[0], new String[0], Collections.<String, double[]>emptyMap(),
        Collections.<String, double[][]>emptyMap(), Collections.<String, double[]>emptyMap(),
        Collections.<String, double[]>emptyMap(), Collections.<String, double[]>emptyMap(),
        Collections.<String, double[]>emptyMap(), Collections.<TransientGasNetworkStepReport>emptyList(),
        Collections.<String, List<TransientSpeciesConservationReport>>emptyMap(),
        Collections.<String, List<TransientSpeciesConservationReport>>emptyMap(),
        Collections.<TransientSpeciesConservationReport>emptyList());
  }

  /** @return accepted step-end times in s */
  public double[] getElapsedTimeSeconds() {
    return copy(elapsedTimeSeconds);
  }

  /** @return deterministic component-name order used by composition arrays */
  public String[] getComponentNames() {
    return copy(componentNames);
  }

  /**
   * Get a node pressure history.
   *
   * @param nodeName node name
   * @return defensive pressure series in bara
   */
  public double[] getNodePressureBaraHistory(String nodeName) {
    return getSeries(nodePressureBaraHistory, nodeName, "node pressure");
  }

  /**
   * Get a source pressure history. Source pressure is solved, not imposed.
   *
   * @param sourceNodeName source node name
   * @return defensive pressure series in bara
   */
  public double[] getSourcePressureBaraHistory(String sourceNodeName) {
    return getNodePressureBaraHistory(sourceNodeName);
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
      throw new IllegalArgumentException("No composition history exists for node '" + nodeName + "'");
    }
    return copy(values);
  }

  /**
   * Get one named component's node mass-fraction history.
   *
   * @param nodeName node name
   * @param componentName canonical NeqSim component name
   * @return defensive time-aligned series
   */
  public double[] getNodeMassFractionHistory(String nodeName, String componentName) {
    int componentIndex = componentIndex(componentName);
    double[][] values = getNodeMassFractionHistory(nodeName);
    double[] result = new double[values.length];
    for (int timeIndex = 0; timeIndex < values.length; timeIndex++) {
      result[timeIndex] = values[timeIndex][componentIndex];
    }
    return result;
  }

  /**
   * Get edge inlet mass-flow history.
   *
   * @param edgeName edge name
   * @return defensive series in kg/s
   */
  public double[] getEdgeInletMassFlowKgSHistory(String edgeName) {
    return getSeries(edgeInletMassFlowKgSHistory, edgeName, "edge inlet flow");
  }

  /**
   * Get edge average mass-flow history used by the momentum equation.
   *
   * @param edgeName edge name
   * @return defensive series in kg/s
   */
  public double[] getEdgeAverageMassFlowKgSHistory(String edgeName) {
    return getSeries(edgeAverageMassFlowKgSHistory, edgeName, "edge average flow");
  }

  /**
   * Get edge outlet mass-flow history.
   *
   * @param edgeName edge name
   * @return defensive series in kg/s
   */
  public double[] getEdgeOutletMassFlowKgSHistory(String edgeName) {
    return getSeries(edgeOutletMassFlowKgSHistory, edgeName, "edge outlet flow");
  }

  /**
   * Get edge total linepack history.
   *
   * @param edgeName edge name
   * @return defensive series in kg
   */
  public double[] getEdgeLinepackKgHistory(String edgeName) {
    return getSeries(edgeLinepackKgHistory, edgeName, "edge linepack");
  }

  /** @return defensive array of time-aligned hydraulic/conservation reports */
  public TransientGasNetworkStepReport[] getStepReports() {
    return stepReports.toArray(new TransientGasNetworkStepReport[stepReports.size()]);
  }

  /**
   * Get the final hydraulic/conservation report.
   *
   * @return final accepted report
   * @throws IllegalStateException before a run
   */
  public TransientGasNetworkStepReport getFinalStepReport() {
    if (stepReports.isEmpty()) {
      throw new IllegalStateException("Transient gas network has not run");
    }
    return stepReports.get(stepReports.size() - 1);
  }

  /**
   * Get edge component-conservation reports.
   *
   * @param edgeName edge name
   * @return defensive time-aligned array
   */
  public TransientSpeciesConservationReport[] getEdgeSpeciesReports(String edgeName) {
    return reportArray(edgeSpeciesReports, edgeName, "edge");
  }

  /**
   * Get junction component-mixing reports.
   *
   * @param nodeName junction node name
   * @return defensive time-aligned array
   */
  public TransientSpeciesConservationReport[] getJunctionSpeciesReports(String nodeName) {
    return reportArray(junctionSpeciesReports, nodeName, "junction");
  }

  /** @return defensive array of cumulative whole-network component reports */
  public TransientSpeciesConservationReport[] getNetworkSpeciesReports() {
    return networkSpeciesReports.toArray(new TransientSpeciesConservationReport[networkSpeciesReports.size()]);
  }

  /**
   * Serialize all time-aligned histories for Python/JPype capture.
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

  private static double[] getSeries(Map<String, double[]> histories, String name, String kind) {
    double[] values = histories.get(name);
    if (values == null) {
      throw new IllegalArgumentException("No " + kind + " history exists for '" + name + "'");
    }
    return copy(values);
  }

  private static TransientSpeciesConservationReport[] reportArray(
      Map<String, List<TransientSpeciesConservationReport>> reports, String name, String kind) {
    List<TransientSpeciesConservationReport> values = reports.get(name);
    if (values == null) {
      throw new IllegalArgumentException("No " + kind + " species reports exist for '" + name + "'");
    }
    return values.toArray(new TransientSpeciesConservationReport[values.size()]);
  }

  private static Map<String, double[]> copySeries(Map<String, double[]> histories) {
    Map<String, double[]> result = new LinkedHashMap<String, double[]>();
    for (Map.Entry<String, double[]> entry : histories.entrySet()) {
      result.put(entry.getKey(), copy(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
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
    Map<String, List<TransientSpeciesConservationReport>> result = new LinkedHashMap<String, List<TransientSpeciesConservationReport>>();
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
