package neqsim.process.fastsimulation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result from a K-value fast process simulation.
 *
 * <p>
 * The result is component-resolved on a molar-flow basis. Convenience methods provide molar and mass totals for any
 * named source, internal outlet, wrapper stream alias or terminal product stream included in the proxy result map.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class KValueProcessResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** JSON schema version. */
  public static final String SCHEMA_VERSION = "1.0";

  /** Component names used by all stream vectors. */
  private final String[] componentNames;

  /** Component molar masses in kg/mol. */
  private final double[] molarMass;

  /** Component flow map keyed by stream/result name. */
  private final Map<String, double[]> streamComponentFlows;

  /** Terminal product stream keys. */
  private final List<String> terminalStreamNames;

  /** Source stream names. */
  private final List<String> sourceStreamNames;

  /** Number of fixed-point iterations used. */
  private final int iterations;

  /** Final maximum relative stream-flow residual. */
  private final double maxResidual;

  /** Wall-clock run time for the proxy in nanoseconds. */
  private final long runTimeNanos;

  /**
   * Creates a process result.
   *
   * @param componentNames component names; must be non-null
   * @param molarMass component molar masses in kg/mol; must match the component count
   * @param streamComponentFlows component flow map keyed by result stream name; values are mole/sec
   * @param terminalStreamNames terminal product stream keys; must be non-null
   * @param sourceStreamNames source stream names; must be non-null
   * @param iterations number of fixed-point iterations used
   * @param maxResidual final maximum relative residual
   * @param runTimeNanos wall-clock proxy runtime in nanoseconds
   */
  KValueProcessResult(String[] componentNames, double[] molarMass, Map<String, double[]> streamComponentFlows,
      List<String> terminalStreamNames, List<String> sourceStreamNames, int iterations, double maxResidual,
      long runTimeNanos) {
    this.componentNames = componentNames.clone();
    this.molarMass = molarMass.clone();
    this.streamComponentFlows = copyFlowMap(streamComponentFlows);
    this.terminalStreamNames = new ArrayList<String>(terminalStreamNames);
    this.sourceStreamNames = new ArrayList<String>(sourceStreamNames);
    this.iterations = iterations;
    this.maxResidual = maxResidual;
    this.runTimeNanos = runTimeNanos;
  }

  /**
   * Gets the component names.
   *
   * @return copy of component names
   */
  public String[] getComponentNames() {
    return componentNames.clone();
  }

  /**
   * Gets all stream names present in the result.
   *
   * @return stream names in insertion order
   */
  public String[] getStreamNames() {
    return streamComponentFlows.keySet().toArray(new String[0]);
  }

  /**
   * Gets terminal stream names.
   *
   * @return unmodifiable terminal stream name list
   */
  public List<String> getTerminalStreamNames() {
    return Collections.unmodifiableList(terminalStreamNames);
  }

  /**
   * Gets source stream names.
   *
   * @return unmodifiable source stream name list
   */
  public List<String> getSourceStreamNames() {
    return Collections.unmodifiableList(sourceStreamNames);
  }

  /**
   * Gets the component flow for a stream.
   *
   * @param streamName result stream name
   * @param componentName component name
   * @param unit requested unit: {@code mole/sec}, {@code mole/hr}, {@code kg/sec}, or {@code kg/hr}
   * @return component flow in the requested unit
   */
  public double getStreamComponentFlow(String streamName, String componentName, String unit) {
    int componentIndex = getComponentIndex(componentName);
    double[] flow = requireStream(streamName);
    return convertComponentFlow(flow[componentIndex], componentIndex, unit);
  }

  /**
   * Gets the total stream flow.
   *
   * @param streamName result stream name
   * @param unit requested unit: {@code mole/sec}, {@code mole/hr}, {@code kg/sec}, or {@code kg/hr}
   * @return total stream flow in the requested unit
   */
  public double getStreamTotalFlow(String streamName, String unit) {
    double[] flow = requireStream(streamName);
    double total = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      total += convertComponentFlow(flow[component], component, unit);
    }
    return total;
  }

  /**
   * Gets the mole fraction of a component in a stream.
   *
   * @param streamName result stream name
   * @param componentName component name
   * @return mole fraction, or {@code 0.0} for an empty stream
   */
  public double getStreamMoleFraction(String streamName, String componentName) {
    int componentIndex = getComponentIndex(componentName);
    double[] flow = requireStream(streamName);
    double total = 0.0;
    for (int component = 0; component < componentNames.length; component++) {
      total += flow[component];
    }
    return total <= 0.0 ? 0.0 : flow[componentIndex] / total;
  }

  /**
   * Gets the total terminal product flow.
   *
   * @param unit requested unit: {@code mole/sec}, {@code mole/hr}, {@code kg/sec}, or {@code kg/hr}
   * @return sum of all terminal stream flows in the requested unit
   */
  public double getTerminalTotalFlow(String unit) {
    double total = 0.0;
    for (String streamName : terminalStreamNames) {
      total += getStreamTotalFlow(streamName, unit);
    }
    return total;
  }

  /**
   * Gets the number of fixed-point iterations used by the proxy.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the final maximum relative residual.
   *
   * @return maximum residual
   */
  public double getMaxResidual() {
    return maxResidual;
  }

  /**
   * Gets the proxy wall-clock runtime.
   *
   * @return runtime in nanoseconds
   */
  public long getRunTimeNanos() {
    return runTimeNanos;
  }

  /**
   * Serializes the result to JSON.
   *
   * @return pretty-printed JSON result
   */
  public String toJson() {
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("method", "cached-k-value-process-simulation");
    root.put("iterations", iterations);
    root.put("maxResidual", maxResidual);
    root.put("runTimeNanos", runTimeNanos);
    root.put("componentNames", componentNames.clone());
    root.put("terminalStreamNames", new ArrayList<String>(terminalStreamNames));
    root.put("sourceStreamNames", new ArrayList<String>(sourceStreamNames));

    Map<String, Object> streams = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, double[]> entry : streamComponentFlows.entrySet()) {
      Map<String, Object> stream = new LinkedHashMap<String, Object>();
      stream.put("totalMolePerSec", getStreamTotalFlow(entry.getKey(), "mole/sec"));
      stream.put("totalKgPerHr", getStreamTotalFlow(entry.getKey(), "kg/hr"));
      Map<String, Double> components = new LinkedHashMap<String, Double>();
      for (int component = 0; component < componentNames.length; component++) {
        components.put(componentNames[component], entry.getValue()[component]);
      }
      stream.put("componentMolePerSec", components);
      streams.put(entry.getKey(), stream);
    }
    root.put("streams", streams);
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  /**
   * Finds a component index.
   *
   * @param componentName component name
   * @return component index
   * @throws IllegalArgumentException if the component is not present
   */
  private int getComponentIndex(String componentName) {
    for (int i = 0; i < componentNames.length; i++) {
      if (componentNames[i].equals(componentName)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unknown component: " + componentName);
  }

  /**
   * Gets a stream vector or throws a useful exception.
   *
   * @param streamName stream name
   * @return component-flow vector in mole/sec
   * @throws IllegalArgumentException if the stream is not present
   */
  private double[] requireStream(String streamName) {
    double[] flow = streamComponentFlows.get(streamName);
    if (flow == null) {
      throw new IllegalArgumentException("Unknown stream: " + streamName);
    }
    return flow;
  }

  /**
   * Converts a component molar flow to the requested unit.
   *
   * @param molePerSec component molar flow in mole/sec
   * @param componentIndex component index
   * @param unit requested unit
   * @return converted flow
   */
  private double convertComponentFlow(double molePerSec, int componentIndex, String unit) {
    String normalizedUnit = unit == null ? "mole/sec" : unit.trim().toLowerCase();
    if (normalizedUnit.equals("mole/sec") || normalizedUnit.equals("mol/sec")) {
      return molePerSec;
    }
    if (normalizedUnit.equals("mole/hr") || normalizedUnit.equals("mol/hr")) {
      return molePerSec * 3600.0;
    }
    if (normalizedUnit.equals("kg/sec")) {
      return molePerSec * molarMass[componentIndex];
    }
    if (normalizedUnit.equals("kg/hr")) {
      return molePerSec * molarMass[componentIndex] * 3600.0;
    }
    throw new IllegalArgumentException("Unsupported unit: " + unit);
  }

  /**
   * Copies a stream flow map defensively.
   *
   * @param source source map
   * @return copied map
   */
  private static Map<String, double[]> copyFlowMap(Map<String, double[]> source) {
    Map<String, double[]> copy = new LinkedHashMap<String, double[]>();
    for (Map.Entry<String, double[]> entry : source.entrySet()) {
      copy.put(entry.getKey(), entry.getValue().clone());
    }
    return copy;
  }
}
