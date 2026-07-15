package neqsim.process.equipment.compressor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Lumped-parameter thermal network for a compressor.
 *
 * <p>
 * Each non-boundary node obeys {@code C dT/dt = Q + sum(G * (Tneighbour - T))}. The
 * steady-state and implicit-Euler transient solvers use the same network. Conductances and heat
 * capacities are deliberately explicit calibration parameters: local metal temperatures depend on
 * machine geometry, material, oil flow, seal flow and operating history and cannot be inferred from
 * the process-gas outlet temperature alone.
 * </p>
 *
 * <p>
 * API 617 and API 692 define the relevant compressor, bearing and dry-gas-seal system scope. They
 * do not provide a universal rotor thermal network. Template values supplied by
 * {@link CompressorCatalog} are therefore screening values and should be replaced with OEM data or
 * fitted plant measurements before integrity decisions are made.
 * </p>
 */
public class CompressorThermalModel implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Standard node identifier for suction process gas. */
  public static final String SUCTION_GAS = "suction-gas";
  /** Standard node identifier for discharge process gas. */
  public static final String DISCHARGE_GAS = "discharge-gas";
  /** Standard node identifier for the inlet-end shaft. */
  public static final String INLET_SHAFT = "inlet-shaft";
  /** Standard node identifier for the impeller or rotor active section. */
  public static final String IMPELLER = "impeller";
  /** Standard node identifier for the casing. */
  public static final String CASING = "casing";
  /** Standard node identifier for the dry-gas seal. */
  public static final String DRY_GAS_SEAL = "dry-gas-seal";
  /** Standard node identifier for seal-gas supply. */
  public static final String SEAL_GAS = "seal-gas";
  /** Standard node identifier for radial bearings. */
  public static final String RADIAL_BEARING = "radial-bearing";
  /** Standard node identifier for the thrust bearing. */
  public static final String THRUST_BEARING = "thrust-bearing";
  /** Standard node identifier for lube oil. */
  public static final String LUBE_OIL = "lube-oil";
  /** Standard node identifier for ambient air. */
  public static final String AMBIENT = "ambient";

  private String name = "compressor thermal model";
  private final Map<String, CompressorThermalNode> nodes = new LinkedHashMap<String, CompressorThermalNode>();
  private final List<CompressorThermalLink> links = new ArrayList<CompressorThermalLink>();
  private boolean autoSolve = true;
  private boolean useMechanicalLossHeatSources = true;
  private double compressionHeatFractionToImpeller = 0.0;

  /** Create an empty thermal network. */
  public CompressorThermalModel() {
  }

  /** @param name descriptive model name */
  public CompressorThermalModel(String name) {
    setName(name);
  }

  /** @return model name */
  public String getName() {
    return name;
  }

  /** @param name non-empty model name */
  public void setName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("thermal model name must not be empty");
    }
    this.name = name;
  }

  /**
   * Add or replace a node.
   *
   * @param node thermal node
   * @return this model
   */
  public CompressorThermalModel addNode(CompressorThermalNode node) {
    if (node == null) {
      throw new IllegalArgumentException("thermal node must not be null");
    }
    nodes.put(node.getId(), node);
    return this;
  }

  /**
   * Add a thermal link. Both nodes must already be present.
   *
   * @param link thermal link
   * @return this model
   */
  public CompressorThermalModel addLink(CompressorThermalLink link) {
    if (link == null) {
      throw new IllegalArgumentException("thermal link must not be null");
    }
    if (!nodes.containsKey(link.getFromNodeId()) || !nodes.containsKey(link.getToNodeId())) {
      throw new IllegalArgumentException("both thermal link nodes must be added before the link");
    }
    if (link.getFromNodeId().equals(link.getToNodeId())) {
      throw new IllegalArgumentException("thermal link must connect two different nodes");
    }
    links.add(link);
    return this;
  }

  /**
   * Get a node by identifier.
   *
   * @param id node identifier
   * @return node, or null if absent
   */
  public CompressorThermalNode getNode(String id) {
    return nodes.get(id);
  }

  /** @return immutable view of nodes in insertion order */
  public Map<String, CompressorThermalNode> getNodes() {
    return Collections.unmodifiableMap(nodes);
  }

  /** @return immutable view of thermal links */
  public List<CompressorThermalLink> getLinks() {
    return Collections.unmodifiableList(links);
  }

  /** @return true when an attached compressor solves this model after a process run */
  public boolean isAutoSolve() {
    return autoSolve;
  }

  /** @param autoSolve solve automatically after an attached compressor process run */
  public void setAutoSolve(boolean autoSolve) {
    this.autoSolve = autoSolve;
  }

  /** @return true when bearing correlations contribute heat to matching nodes */
  public boolean isUseMechanicalLossHeatSources() {
    return useMechanicalLossHeatSources;
  }

  /** @param useMechanicalLossHeatSources include configured bearing losses as node heat */
  public void setUseMechanicalLossHeatSources(boolean useMechanicalLossHeatSources) {
    this.useMechanicalLossHeatSources = useMechanicalLossHeatSources;
  }

  /** @return fraction of gas-compression power deposited as impeller heat */
  public double getCompressionHeatFractionToImpeller() {
    return compressionHeatFractionToImpeller;
  }

  /**
   * Set the fitted fraction of gas-compression power deposited in the impeller thermal node.
   *
   * @param fraction fraction from 0 to 1
   */
  public void setCompressionHeatFractionToImpeller(double fraction) {
    if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("compression heat fraction must be between zero and one");
    }
    compressionHeatFractionToImpeller = fraction;
  }

  /**
   * Update standard fluid-boundary nodes from a completed compressor calculation.
   *
   * @param compressor compressor supplying process and utility temperatures
   */
  public void updateBoundaryTemperatures(Compressor compressor) {
    if (compressor == null) {
      throw new IllegalArgumentException("compressor must not be null");
    }
    if (compressor.getInletStream() != null) {
      setBoundaryIfPresent(SUCTION_GAS, compressor.getInletStream().getTemperature("K"));
    }
    if (compressor.getOutletStream() != null && compressor.getOutletStream().getThermoSystem() != null) {
      setBoundaryIfPresent(DISCHARGE_GAS, compressor.getOutletStream().getTemperature("K"));
    }
    CompressorMechanicalLosses losses = compressor.getMechanicalLosses();
    if (losses != null) {
      setBoundaryIfPresent(SEAL_GAS, losses.getSealGasSupplyTemperature() + 273.15);
      double oilTemperatureK = 0.5 * (losses.getLubeOilInletTemp() + losses.getLubeOilOutletTemp()) + 273.15;
      setBoundaryIfPresent(LUBE_OIL, oilTemperatureK);
    }
  }

  private void setBoundaryIfPresent(String id, double temperatureK) {
    CompressorThermalNode node = nodes.get(id);
    if (node != null && node.isFixedTemperature()) {
      node.setTemperatureK(temperatureK);
    }
  }

  /** Solve the steady-state thermal network without compressor-generated heat sources. */
  public void solveSteadyState() {
    solve(false, 0.0, null);
  }

  /**
   * Update boundaries and solve the steady-state thermal network.
   *
   * @param compressor attached compressor
   */
  public void solveSteadyState(Compressor compressor) {
    updateBoundaryTemperatures(compressor);
    solve(false, 0.0, compressor);
  }

  /**
   * Advance the thermal network with an unconditionally stable implicit-Euler step.
   *
   * @param timeStepSeconds positive time step in seconds
   */
  public void solveTransient(double timeStepSeconds) {
    solve(true, timeStepSeconds, null);
  }

  /**
   * Update boundaries and advance the thermal network with an implicit-Euler step.
   *
   * @param compressor attached compressor
   * @param timeStepSeconds positive time step in seconds
   */
  public void solveTransient(Compressor compressor, double timeStepSeconds) {
    updateBoundaryTemperatures(compressor);
    solve(true, timeStepSeconds, compressor);
  }

  private void solve(boolean transientStep, double timeStepSeconds, Compressor compressor) {
    if (transientStep && (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0)) {
      throw new IllegalArgumentException("thermal time step must be finite and positive");
    }
    List<CompressorThermalNode> unknowns = new ArrayList<CompressorThermalNode>();
    Map<String, Integer> unknownIndex = new LinkedHashMap<String, Integer>();
    boolean hasBoundary = false;
    for (CompressorThermalNode node : nodes.values()) {
      if (node.isFixedTemperature()) {
        hasBoundary = true;
      } else {
        if (transientStep && node.getHeatCapacityJPerK() <= 0.0) {
          throw new IllegalStateException("transient node '" + node.getId() + "' must have positive heat capacity");
        }
        unknownIndex.put(node.getId(), unknowns.size());
        unknowns.add(node);
      }
    }
    if (!hasBoundary && !unknowns.isEmpty()) {
      throw new IllegalStateException("thermal network needs at least one fixed-temperature node");
    }
    if (unknowns.isEmpty()) {
      return;
    }

    double[][] coefficients = new double[unknowns.size()][unknowns.size()];
    double[] rightHandSide = new double[unknowns.size()];
    for (int i = 0; i < unknowns.size(); i++) {
      CompressorThermalNode node = unknowns.get(i);
      rightHandSide[i] = node.getHeatGenerationW() + getCompressorHeatSource(node.getId(), compressor);
      if (transientStep) {
        double storage = node.getHeatCapacityJPerK() / timeStepSeconds;
        coefficients[i][i] += storage;
        rightHandSide[i] += storage * node.getTemperatureK();
      }
    }

    for (CompressorThermalLink link : links) {
      addLinkToEquations(link.getFromNodeId(), link.getToNodeId(), link.getConductanceWPerK(), unknownIndex,
          coefficients, rightHandSide);
      addLinkToEquations(link.getToNodeId(), link.getFromNodeId(), link.getConductanceWPerK(), unknownIndex,
          coefficients, rightHandSide);
    }
    double[] solution = solveLinearSystem(coefficients, rightHandSide);
    for (int i = 0; i < unknowns.size(); i++) {
      unknowns.get(i).setTemperatureK(solution[i]);
    }
  }

  private void addLinkToEquations(String nodeId, String neighbourId, double conductance,
      Map<String, Integer> unknownIndex, double[][] coefficients, double[] rightHandSide) {
    Integer row = unknownIndex.get(nodeId);
    if (row == null) {
      return;
    }
    coefficients[row][row] += conductance;
    Integer column = unknownIndex.get(neighbourId);
    if (column == null) {
      CompressorThermalNode boundary = nodes.get(neighbourId);
      rightHandSide[row] += conductance * boundary.getTemperatureK();
    } else {
      coefficients[row][column] -= conductance;
    }
  }

  private double getCompressorHeatSource(String nodeId, Compressor compressor) {
    if (compressor == null) {
      return 0.0;
    }
    if (IMPELLER.equals(nodeId)) {
      return compressionHeatFractionToImpeller * Math.abs(compressor.getPower());
    }
    if (!useMechanicalLossHeatSources || compressor.getMechanicalLosses() == null) {
      return 0.0;
    }
    compressor.updateMechanicalLosses();
    if (RADIAL_BEARING.equals(nodeId)) {
      return 1000.0 * compressor.getMechanicalLosses().calculateRadialBearingLoss();
    }
    if (THRUST_BEARING.equals(nodeId)) {
      return 1000.0 * compressor.getMechanicalLosses().calculateThrustBearingLoss();
    }
    return 0.0;
  }

  private static double[] solveLinearSystem(double[][] matrix, double[] vector) {
    int size = vector.length;
    for (int pivot = 0; pivot < size; pivot++) {
      int bestRow = pivot;
      for (int row = pivot + 1; row < size; row++) {
        if (Math.abs(matrix[row][pivot]) > Math.abs(matrix[bestRow][pivot])) {
          bestRow = row;
        }
      }
      if (Math.abs(matrix[bestRow][pivot]) < 1.0e-12) {
        throw new IllegalStateException("thermal network is singular or contains an isolated node");
      }
      double[] rowSwap = matrix[pivot];
      matrix[pivot] = matrix[bestRow];
      matrix[bestRow] = rowSwap;
      double valueSwap = vector[pivot];
      vector[pivot] = vector[bestRow];
      vector[bestRow] = valueSwap;

      for (int row = pivot + 1; row < size; row++) {
        double factor = matrix[row][pivot] / matrix[pivot][pivot];
        for (int column = pivot; column < size; column++) {
          matrix[row][column] -= factor * matrix[pivot][column];
        }
        vector[row] -= factor * vector[pivot];
      }
    }
    double[] result = new double[size];
    for (int row = size - 1; row >= 0; row--) {
      double sum = vector[row];
      for (int column = row + 1; column < size; column++) {
        sum -= matrix[row][column] * result[column];
      }
      result[row] = sum / matrix[row][row];
      if (!Double.isFinite(result[row]) || result[row] <= 0.0) {
        throw new IllegalStateException("thermal solution produced a non-physical temperature");
      }
    }
    return result;
  }

  /**
   * Get one calculated temperature.
   *
   * @param nodeId node identifier
   * @param unit K, C, degC, F or degF
   * @return temperature in requested unit
   */
  public double getTemperature(String nodeId, String unit) {
    CompressorThermalNode node = nodes.get(nodeId);
    if (node == null) {
      throw new IllegalArgumentException("no thermal node named '" + nodeId + "'");
    }
    return convertTemperature(node.getTemperatureK(), unit);
  }

  /**
   * Get all calculated temperatures in insertion order.
   *
   * @param unit K, C, degC, F or degF
   * @return node-to-temperature map
   */
  public Map<String, Double> getTemperatureProfile(String unit) {
    Map<String, Double> profile = new LinkedHashMap<String, Double>();
    for (CompressorThermalNode node : nodes.values()) {
      profile.put(node.getId(), convertTemperature(node.getTemperatureK(), unit));
    }
    return profile;
  }

  private static double convertTemperature(double temperatureK, String unit) {
    if ("K".equalsIgnoreCase(unit)) {
      return temperatureK;
    }
    if ("C".equalsIgnoreCase(unit) || "degC".equalsIgnoreCase(unit)) {
      return temperatureK - 273.15;
    }
    if ("F".equalsIgnoreCase(unit) || "degF".equalsIgnoreCase(unit)) {
      return (temperatureK - 273.15) * 9.0 / 5.0 + 32.0;
    }
    throw new IllegalArgumentException("unsupported temperature unit '" + unit + "'");
  }

  /** @return pretty-printed, round-trippable JSON */
  public String toJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().setPrettyPrinting().create().toJson(this);
  }

  /**
   * Deserialize a thermal model.
   *
   * @param json serialized model
   * @return deserialized thermal model
   */
  public static CompressorThermalModel fromJson(String json) {
    if (json == null || json.trim().isEmpty()) {
      throw new IllegalArgumentException("thermal model JSON must not be empty");
    }
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().fromJson(json,
        CompressorThermalModel.class);
  }

  /** @return independent deep copy */
  public CompressorThermalModel copy() {
    return fromJson(toJson());
  }
}
