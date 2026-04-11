package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Pipeline network supporting looped topologies with Hardy Cross solver.
 *
 * <p>
 * This class extends the basic pipeline network concept to support looped configurations commonly
 * found in water distribution systems, oil and gas gathering networks, and gas transmission
 * systems. The Hardy Cross method is used to iteratively solve for flow distribution in networks
 * with loops.
 * </p>
 *
 * <h2>Hardy Cross Method</h2>
 * <p>
 * The Hardy Cross method is an iterative technique for solving networks with loops:
 * </p>
 * <ol>
 * <li>Initial flow estimates are made for each pipe</li>
 * <li>Independent loops are identified using DFS spanning tree algorithm</li>
 * <li>For each loop, calculate head loss imbalance: &Delta;H = &sum;(h&middot;sign)</li>
 * <li>Calculate flow correction: &Delta;Q = -&Delta;H / &sum;(|dh/dQ|)</li>
 * <li>Apply corrections to all pipes in each loop</li>
 * <li>Repeat until convergence (&Delta;H &lt; tolerance for all loops)</li>
 * </ol>
 *
 * <h2>Network Topology</h2>
 * <p>
 * The network supports:
 * </p>
 * <ul>
 * <li>Multiple source nodes (wells, compressor stations)</li>
 * <li>Multiple sink nodes (customers, export terminals)</li>
 * <li>Junction nodes where pipes connect</li>
 * <li>Looped configurations for redundancy</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * {@code
 * // Create a simple ring main network
 * SystemInterface gas = new SystemSrkEos(298.15, 50.0);
 * gas.addComponent("methane", 0.9);
 * gas.addComponent("ethane", 0.1);
 * gas.setMixingRule("classic");
 *
 * LoopedPipeNetwork network = new LoopedPipeNetwork("ring main");
 * network.setFluidTemplate(gas);
 *
 * // Add nodes
 * network.addSourceNode("supply", 50.0, 1000.0); // 50 bar, 1000 kg/hr
 * network.addJunctionNode("A");
 * network.addJunctionNode("B");
 * network.addJunctionNode("C");
 * network.addSinkNode("customer1", 100.0); // 100 kg/hr demand
 * network.addSinkNode("customer2", 200.0);
 *
 * // Connect with pipes (creates loops)
 * network.addPipe("supply", "A", "pipe1", 1000.0, 0.3);
 * network.addPipe("A", "B", "pipe2", 500.0, 0.2);
 * network.addPipe("B", "C", "pipe3", 500.0, 0.2);
 * network.addPipe("C", "A", "pipe4", 500.0, 0.2); // Creates a loop
 * network.addPipe("B", "customer1", "pipe5", 200.0, 0.15);
 * network.addPipe("C", "customer2", "pipe6", 200.0, 0.15);
 *
 * // Solve using Hardy Cross
 * network.setSolverType(SolverType.HARDY_CROSS);
 * network.setTolerance(1e-6);
 * network.run();
 *
 * // Get results
 * System.out.println("Converged in " + network.getIterationCount() + " iterations");
 * for (String pipeName : network.getPipeNames()) {
 *   System.out.println(pipeName + ": " + network.getPipeFlowRate(pipeName) + " kg/hr");
 * }
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see NetworkLoop
 * @see LoopDetector
 */
public class LoopedPipeNetwork extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1001L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(LoopedPipeNetwork.class);

  /**
   * Solver type for network analysis.
   */
  public enum SolverType {
    /**
     * Hardy Cross iterative method for looped networks.
     */
    HARDY_CROSS,

    /**
     * Sequential modular solver for tree networks.
     */
    SEQUENTIAL,

    /**
     * Newton-Raphson Global Gradient Algorithm (Todini-Pilati). Solves nodal pressure and pipe flow
     * simultaneously. Converges faster than Hardy Cross for large networks.
     */
    NEWTON_RAPHSON
  }

  /**
   * Pipe flow model type.
   */
  public enum PipeModelType {
    /**
     * Darcy-Weisbach equation for single-phase flow (default).
     */
    DARCY_WEISBACH,

    /**
     * Beggs-Brill correlation for multiphase oil/gas/water flow.
     */
    BEGGS_BRILL
  }

  /**
   * Node type in the network.
   */
  public enum NodeType {
    /**
     * Source node with fixed pressure and/or flow.
     */
    SOURCE,

    /**
     * Sink node with specified demand.
     */
    SINK,

    /**
     * Junction node where pipes connect.
     */
    JUNCTION
  }

  /**
   * Represents a node in the pipe network.
   */
  public static class NetworkNode {
    private final String name;
    private final NodeType type;
    private double pressure; // Pa
    private double demand; // kg/s (positive = demand/outflow, negative = supply/inflow)
    private double temperature = 288.15; // K
    private double elevation = 0.0; // m
    private boolean pressureFixed = false;
    private StreamInterface stream;

    /**
     * Constructor for network node.
     *
     * @param name node name
     * @param type node type
     */
    public NetworkNode(String name, NodeType type) {
      this.name = name;
      this.type = type;
    }

    /**
     * Get node name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Get node type.
     *
     * @return the type
     */
    public NodeType getType() {
      return type;
    }

    /**
     * Get pressure in Pa.
     *
     * @return pressure
     */
    public double getPressure() {
      return pressure;
    }

    /**
     * Set pressure in Pa.
     *
     * @param pressure pressure in Pa
     */
    public void setPressure(double pressure) {
      this.pressure = pressure;
    }

    /**
     * Get demand (mass flow rate out of node) in kg/s.
     *
     * @return demand
     */
    public double getDemand() {
      return demand;
    }

    /**
     * Set demand in kg/s.
     *
     * @param demand demand (positive = outflow)
     */
    public void setDemand(double demand) {
      this.demand = demand;
    }

    /**
     * Get temperature in K.
     *
     * @return temperature
     */
    public double getTemperature() {
      return temperature;
    }

    /**
     * Set temperature in K.
     *
     * @param temperature temperature in K
     */
    public void setTemperature(double temperature) {
      this.temperature = temperature;
    }

    /**
     * Get elevation in m.
     *
     * @return elevation
     */
    public double getElevation() {
      return elevation;
    }

    /**
     * Set elevation in m.
     *
     * @param elevation elevation in m
     */
    public void setElevation(double elevation) {
      this.elevation = elevation;
    }

    /**
     * Check if pressure is fixed.
     *
     * @return true if pressure is fixed
     */
    public boolean isPressureFixed() {
      return pressureFixed;
    }

    /**
     * Set whether pressure is fixed.
     *
     * @param fixed true to fix pressure
     */
    public void setPressureFixed(boolean fixed) {
      this.pressureFixed = fixed;
    }

    /**
     * Get associated stream.
     *
     * @return stream or null
     */
    public StreamInterface getStream() {
      return stream;
    }

    /**
     * Set associated stream.
     *
     * @param stream the stream
     */
    public void setStream(StreamInterface stream) {
      this.stream = stream;
    }
  }

  /**
   * Represents a pipe in the network.
   */
  public static class NetworkPipe {
    private final String name;
    private final String fromNode;
    private final String toNode;
    private double length; // m
    private double diameter; // m
    private double roughness = 4.5e-5; // m (default: commercial steel)
    private double flowRate = 0.0; // kg/s (positive = from->to)
    private double headLoss = 0.0; // Pa
    private double velocity = 0.0; // m/s
    private double reynoldsNumber = 0.0;
    private double frictionFactor = 0.0;
    private String flowRegime = "";
    private double liquidHoldup = 0.0;
    private double outletTemperature = 288.15; // K
    private double wallThickness = 0.0127; // m (default 0.5 inch)
    private double insulationThickness = 0.0; // m
    private double overallHeatTransferCoeff = 0.0; // W/m2K (0 = adiabatic)
    private double ambientTemperature = 288.15; // K
    private AdiabaticPipe pipeModel;
    private PipeBeggsAndBrills bbModel;

    /**
     * Constructor for network pipe.
     *
     * @param name pipe name
     * @param fromNode source node name
     * @param toNode target node name
     */
    public NetworkPipe(String name, String fromNode, String toNode) {
      this.name = name;
      this.fromNode = fromNode;
      this.toNode = toNode;
    }

    /**
     * Get pipe name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Get source node name.
     *
     * @return from node
     */
    public String getFromNode() {
      return fromNode;
    }

    /**
     * Get target node name.
     *
     * @return to node
     */
    public String getToNode() {
      return toNode;
    }

    /**
     * Get pipe length in m.
     *
     * @return length
     */
    public double getLength() {
      return length;
    }

    /**
     * Set pipe length in m.
     *
     * @param length length in m
     */
    public void setLength(double length) {
      this.length = length;
    }

    /**
     * Get pipe diameter in m.
     *
     * @return diameter
     */
    public double getDiameter() {
      return diameter;
    }

    /**
     * Set pipe diameter in m.
     *
     * @param diameter diameter in m
     */
    public void setDiameter(double diameter) {
      this.diameter = diameter;
    }

    /**
     * Get pipe roughness in m.
     *
     * @return roughness
     */
    public double getRoughness() {
      return roughness;
    }

    /**
     * Set pipe roughness in m.
     *
     * @param roughness roughness in m
     */
    public void setRoughness(double roughness) {
      this.roughness = roughness;
    }

    /**
     * Get mass flow rate in kg/s.
     *
     * @return flow rate (positive = from-&gt;to direction)
     */
    public double getFlowRate() {
      return flowRate;
    }

    /**
     * Set mass flow rate in kg/s.
     *
     * @param flowRate flow rate
     */
    public void setFlowRate(double flowRate) {
      this.flowRate = flowRate;
    }

    /**
     * Get head loss in Pa.
     *
     * @return head loss
     */
    public double getHeadLoss() {
      return headLoss;
    }

    /**
     * Set head loss in Pa.
     *
     * @param headLoss head loss
     */
    public void setHeadLoss(double headLoss) {
      this.headLoss = headLoss;
    }

    /**
     * Get pipe model.
     *
     * @return the AdiabaticPipe model
     */
    public AdiabaticPipe getPipeModel() {
      return pipeModel;
    }

    /**
     * Set pipe model.
     *
     * @param model the pipe model
     */
    public void setPipeModel(AdiabaticPipe model) {
      this.pipeModel = model;
    }

    /**
     * Get Beggs-Brill pipe model.
     *
     * @return the PipeBeggsAndBrills model or null
     */
    public PipeBeggsAndBrills getBBModel() {
      return bbModel;
    }

    /**
     * Set Beggs-Brill pipe model.
     *
     * @param model the pipe model
     */
    public void setBBModel(PipeBeggsAndBrills model) {
      this.bbModel = model;
    }

    /**
     * Get velocity in m/s.
     *
     * @return velocity
     */
    public double getVelocity() {
      return velocity;
    }

    /**
     * Set velocity in m/s.
     *
     * @param velocity velocity in m/s
     */
    public void setVelocity(double velocity) {
      this.velocity = velocity;
    }

    /**
     * Get Reynolds number.
     *
     * @return Reynolds number
     */
    public double getReynoldsNumber() {
      return reynoldsNumber;
    }

    /**
     * Set Reynolds number.
     *
     * @param re Reynolds number
     */
    public void setReynoldsNumber(double re) {
      this.reynoldsNumber = re;
    }

    /**
     * Get friction factor.
     *
     * @return Darcy friction factor
     */
    public double getFrictionFactor() {
      return frictionFactor;
    }

    /**
     * Set friction factor.
     *
     * @param ff friction factor
     */
    public void setFrictionFactor(double ff) {
      this.frictionFactor = ff;
    }

    /**
     * Get flow regime name.
     *
     * @return flow regime
     */
    public String getFlowRegime() {
      return flowRegime;
    }

    /**
     * Set flow regime name.
     *
     * @param regime flow regime
     */
    public void setFlowRegime(String regime) {
      this.flowRegime = regime;
    }

    /**
     * Get liquid holdup (fraction 0-1).
     *
     * @return liquid holdup
     */
    public double getLiquidHoldup() {
      return liquidHoldup;
    }

    /**
     * Set liquid holdup.
     *
     * @param holdup liquid holdup fraction
     */
    public void setLiquidHoldup(double holdup) {
      this.liquidHoldup = holdup;
    }

    /**
     * Get outlet temperature in K.
     *
     * @return outlet temperature
     */
    public double getOutletTemperature() {
      return outletTemperature;
    }

    /**
     * Set outlet temperature in K.
     *
     * @param temperature outlet temperature in K
     */
    public void setOutletTemperature(double temperature) {
      this.outletTemperature = temperature;
    }

    /**
     * Get wall thickness in m.
     *
     * @return wall thickness
     */
    public double getWallThickness() {
      return wallThickness;
    }

    /**
     * Set wall thickness in m.
     *
     * @param thickness wall thickness in m
     */
    public void setWallThickness(double thickness) {
      this.wallThickness = thickness;
    }

    /**
     * Get overall heat transfer coefficient in W/m2K.
     *
     * @return U-value (0 means adiabatic)
     */
    public double getOverallHeatTransferCoeff() {
      return overallHeatTransferCoeff;
    }

    /**
     * Set overall heat transfer coefficient in W/m2K.
     *
     * @param uValue U-value (0 for adiabatic)
     */
    public void setOverallHeatTransferCoeff(double uValue) {
      this.overallHeatTransferCoeff = uValue;
    }

    /**
     * Get ambient temperature in K.
     *
     * @return ambient temperature
     */
    public double getAmbientTemperature() {
      return ambientTemperature;
    }

    /**
     * Set ambient temperature in K.
     *
     * @param temperature ambient temperature in K
     */
    public void setAmbientTemperature(double temperature) {
      this.ambientTemperature = temperature;
    }
  }

  // Network topology
  private final transient Map<String, NetworkNode> nodes = new HashMap<>();
  private final transient Map<String, NetworkPipe> pipes = new HashMap<>();
  private final List<String> pipeNames = new ArrayList<>();

  // Solver settings
  private SolverType solverType = SolverType.HARDY_CROSS;
  private PipeModelType pipeModelType = PipeModelType.DARCY_WEISBACH;
  private double tolerance = 1e-6; // Pa for head loss balance
  private int maxIterations = 100;
  private double relaxationFactor = 1.0;

  // Solution state
  private List<NetworkLoop> loops;
  private int iterationCount = 0;
  private double maxResidual = 0.0;
  private boolean converged = false;
  private SystemInterface fluidTemplate;
  private double massBalanceError = 0.0;

  /**
   * Create a new looped pipe network.
   *
   * @param name network name
   */
  public LoopedPipeNetwork(String name) {
    super(name);
  }

  /**
   * Set the fluid template for the network.
   *
   * @param fluid the fluid system to use as template
   */
  public void setFluidTemplate(SystemInterface fluid) {
    this.fluidTemplate = fluid;
  }

  /**
   * Get the fluid template.
   *
   * @return the fluid template
   */
  public SystemInterface getFluidTemplate() {
    return fluidTemplate;
  }

  /**
   * Set the pipe flow model type.
   *
   * @param type pipe model type (DARCY_WEISBACH or BEGGS_BRILL)
   */
  public void setPipeModelType(PipeModelType type) {
    this.pipeModelType = type;
  }

  /**
   * Get the pipe flow model type.
   *
   * @return pipe model type
   */
  public PipeModelType getPipeModelType() {
    return pipeModelType;
  }

  /**
   * Add a source node to the network.
   *
   * @param name node name
   * @param pressureBar fixed pressure in bara
   * @param flowRateKgHr supply flow rate in kg/hr (optional, for validation)
   */
  public void addSourceNode(String name, double pressureBar, double flowRateKgHr) {
    NetworkNode node = new NetworkNode(name, NodeType.SOURCE);
    node.setPressure(pressureBar * 1e5); // Convert to Pa
    node.setDemand(-flowRateKgHr / 3600.0); // Negative = supply (inflow)
    node.setPressureFixed(true);
    nodes.put(name, node);
  }

  /**
   * Add a source node with specified elevation.
   *
   * @param name node name
   * @param pressureBar fixed pressure in bara
   * @param flowRateKgHr supply flow rate in kg/hr
   * @param elevationM node elevation in meters
   */
  public void addSourceNode(String name, double pressureBar, double flowRateKgHr,
      double elevationM) {
    addSourceNode(name, pressureBar, flowRateKgHr);
    nodes.get(name).setElevation(elevationM);
  }

  /**
   * Add a sink node (demand point) to the network.
   *
   * @param name node name
   * @param demandKgHr demand flow rate in kg/hr
   */
  public void addSinkNode(String name, double demandKgHr) {
    NetworkNode node = new NetworkNode(name, NodeType.SINK);
    node.setDemand(demandKgHr / 3600.0); // Positive = demand (outflow)
    nodes.put(name, node);
  }

  /**
   * Add a sink node with specified elevation.
   *
   * @param name node name
   * @param demandKgHr demand flow rate in kg/hr
   * @param elevationM node elevation in meters
   */
  public void addSinkNode(String name, double demandKgHr, double elevationM) {
    addSinkNode(name, demandKgHr);
    nodes.get(name).setElevation(elevationM);
  }

  /**
   * Add a fixed-pressure sink node (delivery point with specified pressure).
   *
   * <p>
   * In this mode the solver determines the flow rate that the network can deliver to this node
   * given the upstream source pressures and network resistances. This is the "pressure-pressure"
   * operating mode, analogous to PIPESIM's fixed-pressure deliverability calculation.
   * </p>
   *
   * @param name node name
   * @param pressureBar delivery pressure in bara
   */
  public void addFixedPressureSinkNode(String name, double pressureBar) {
    NetworkNode node = new NetworkNode(name, NodeType.SINK);
    node.setPressure(pressureBar * 1e5); // Convert to Pa
    node.setDemand(0.0); // Flow determined by solver
    node.setPressureFixed(true);
    nodes.put(name, node);
  }

  /**
   * Add a fixed-pressure sink node with specified elevation.
   *
   * @param name node name
   * @param pressureBar delivery pressure in bara
   * @param elevationM node elevation in meters
   */
  public void addFixedPressureSinkNode(String name, double pressureBar, double elevationM) {
    addFixedPressureSinkNode(name, pressureBar);
    nodes.get(name).setElevation(elevationM);
  }

  /**
   * Add a junction node to the network.
   *
   * @param name node name
   */
  public void addJunctionNode(String name) {
    NetworkNode node = new NetworkNode(name, NodeType.JUNCTION);
    node.setDemand(0.0);
    nodes.put(name, node);
  }

  /**
   * Add a junction node with specified elevation.
   *
   * @param name node name
   * @param elevationM node elevation in meters
   */
  public void addJunctionNode(String name, double elevationM) {
    addJunctionNode(name);
    nodes.get(name).setElevation(elevationM);
  }

  /**
   * Add a pipe connecting two nodes.
   *
   * @param fromNode source node name
   * @param toNode target node name
   * @param pipeName pipe name
   * @param lengthM pipe length in meters
   * @param diameterM pipe inner diameter in meters
   * @return the created pipe
   */
  public NetworkPipe addPipe(String fromNode, String toNode, String pipeName, double lengthM,
      double diameterM) {
    Objects.requireNonNull(fromNode, "fromNode cannot be null");
    Objects.requireNonNull(toNode, "toNode cannot be null");
    Objects.requireNonNull(pipeName, "pipeName cannot be null");

    if (!nodes.containsKey(fromNode)) {
      throw new IllegalArgumentException("Node '" + fromNode + "' not found");
    }
    if (!nodes.containsKey(toNode)) {
      throw new IllegalArgumentException("Node '" + toNode + "' not found");
    }
    if (pipes.containsKey(pipeName)) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' already exists");
    }

    NetworkPipe pipe = new NetworkPipe(pipeName, fromNode, toNode);
    pipe.setLength(lengthM);
    pipe.setDiameter(diameterM);
    pipes.put(pipeName, pipe);
    pipeNames.add(pipeName);

    return pipe;
  }

  /**
   * Add a pipe with specified roughness.
   *
   * @param fromNode source node name
   * @param toNode target node name
   * @param pipeName pipe name
   * @param lengthM pipe length in meters
   * @param diameterM pipe inner diameter in meters
   * @param roughnessM pipe roughness in meters
   * @return the created pipe
   */
  public NetworkPipe addPipe(String fromNode, String toNode, String pipeName, double lengthM,
      double diameterM, double roughnessM) {
    NetworkPipe pipe = addPipe(fromNode, toNode, pipeName, lengthM, diameterM);
    pipe.setRoughness(roughnessM);
    return pipe;
  }

  /**
   * Get a node by name.
   *
   * @param name node name
   * @return the node
   */
  public NetworkNode getNode(String name) {
    NetworkNode node = nodes.get(name);
    if (node == null) {
      throw new IllegalArgumentException("Node '" + name + "' not found");
    }
    return node;
  }

  /**
   * Get a pipe by name.
   *
   * @param name pipe name
   * @return the pipe
   */
  public NetworkPipe getPipe(String name) {
    NetworkPipe pipe = pipes.get(name);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + name + "' not found");
    }
    return pipe;
  }

  /**
   * Get mass balance error from last solve in kg/s.
   *
   * @return mass balance error
   */
  public double getMassBalanceError() {
    return massBalanceError;
  }

  /**
   * Set the solver type.
   *
   * @param type solver type
   */
  public void setSolverType(SolverType type) {
    this.solverType = type;
  }

  /**
   * Get the solver type.
   *
   * @return solver type
   */
  public SolverType getSolverType() {
    return solverType;
  }

  /**
   * Set convergence tolerance for head loss balance (Pa).
   *
   * @param tol tolerance in Pa
   */
  public void setTolerance(double tol) {
    this.tolerance = tol;
  }

  /**
   * Get convergence tolerance.
   *
   * @return tolerance in Pa
   */
  public double getTolerance() {
    return tolerance;
  }

  /**
   * Set maximum number of iterations.
   *
   * @param max maximum iterations
   */
  public void setMaxIterations(int max) {
    this.maxIterations = max;
  }

  /**
   * Get maximum iterations.
   *
   * @return max iterations
   */
  public int getMaxIterations() {
    return maxIterations;
  }

  /**
   * Set relaxation factor for Hardy Cross (0 &lt; factor &lt;= 1).
   *
   * @param factor relaxation factor
   */
  public void setRelaxationFactor(double factor) {
    if (factor <= 0 || factor > 1.5) {
      throw new IllegalArgumentException("Relaxation factor must be in (0, 1.5]");
    }
    this.relaxationFactor = factor;
  }

  /**
   * Get relaxation factor.
   *
   * @return relaxation factor
   */
  public double getRelaxationFactor() {
    return relaxationFactor;
  }

  /**
   * Get number of iterations in last solve.
   *
   * @return iteration count
   */
  public int getIterationCount() {
    return iterationCount;
  }

  /**
   * Get maximum residual from last iteration.
   *
   * @return max residual in Pa
   */
  public double getMaxResidual() {
    return maxResidual;
  }

  /**
   * Check if solution converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Get detected loops in the network.
   *
   * @return list of loops
   */
  public List<NetworkLoop> getLoops() {
    return loops;
  }

  /**
   * Get number of loops in the network.
   *
   * @return number of loops
   */
  public int getNumberOfLoops() {
    return loops != null ? loops.size() : 0;
  }

  /**
   * Get all pipe names.
   *
   * @return list of pipe names
   */
  public List<String> getPipeNames() {
    return new ArrayList<>(pipeNames);
  }

  /**
   * Get flow rate for a specific pipe in kg/hr.
   *
   * @param pipeName pipe name
   * @return flow rate in kg/hr
   */
  public double getPipeFlowRate(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getFlowRate() * 3600.0; // Convert to kg/hr
  }

  /**
   * Get pressure at a node in bara.
   *
   * @param nodeName node name
   * @return pressure in bara
   */
  public double getNodePressure(String nodeName) {
    NetworkNode node = nodes.get(nodeName);
    if (node == null) {
      throw new IllegalArgumentException("Node '" + nodeName + "' not found");
    }
    return node.getPressure() / 1e5; // Convert to bara
  }

  /**
   * Get the net delivered flow rate at a node in kg/hr.
   *
   * <p>
   * Computes the net inflow to the node: sum of incoming pipe flows minus outgoing. For a source
   * node this is negative (supply). For a sink node this is the delivered flow rate. Useful in
   * pressure-pressure mode where the delivered flow is not specified but computed by the solver.
   * </p>
   *
   * @param nodeName node name
   * @return net delivered flow in kg/hr (positive = net inflow to node)
   */
  public double getNodeFlowRate(String nodeName) {
    NetworkNode node = nodes.get(nodeName);
    if (node == null) {
      throw new IllegalArgumentException("Node '" + nodeName + "' not found");
    }
    double netFlowKgs = 0.0;
    for (NetworkPipe pipe : pipes.values()) {
      if (pipe.getToNode().equals(nodeName)) {
        netFlowKgs += pipe.getFlowRate(); // inflow
      }
      if (pipe.getFromNode().equals(nodeName)) {
        netFlowKgs -= pipe.getFlowRate(); // outflow
      }
    }
    return netFlowKgs * 3600.0; // Convert to kg/hr
  }

  /**
   * Initialize pipe flow estimates using BFS spanning tree to satisfy mass balance.
   *
   * <p>
   * Builds a spanning tree from source nodes using BFS. Tree-edge flows are set to satisfy mass
   * balance at every node (required for Hardy Cross). Non-tree edges (loop closers) are initialized
   * to zero — the loop solver will correct them.
   * </p>
   */
  private void initializeFlowEstimates() {
    // Step 1: BFS spanning tree from source nodes
    java.util.Set<String> visited = new java.util.HashSet<>();
    List<String> bfsOrder = new ArrayList<>();
    Map<String, String> parentPipeName = new HashMap<>(); // child -> tree pipe name
    Map<String, Boolean> parentPipeForward = new HashMap<>(); // child -> true if pipe from->to
    java.util.Set<String> treeEdges = new java.util.HashSet<>();

    java.util.Queue<String> queue = new java.util.LinkedList<>();
    for (NetworkNode node : nodes.values()) {
      if (node.getType() == NodeType.SOURCE) {
        queue.add(node.getName());
        visited.add(node.getName());
      }
    }

    while (!queue.isEmpty()) {
      String current = queue.poll();
      bfsOrder.add(current);

      for (NetworkPipe pipe : pipes.values()) {
        String neighbor = null;
        boolean forward = false;

        if (pipe.getFromNode().equals(current) && !visited.contains(pipe.getToNode())) {
          neighbor = pipe.getToNode();
          forward = true; // parent->child follows pipe from->to
        } else if (pipe.getToNode().equals(current) && !visited.contains(pipe.getFromNode())) {
          neighbor = pipe.getFromNode();
          forward = false; // parent->child opposes pipe direction
        }

        if (neighbor != null) {
          visited.add(neighbor);
          parentPipeName.put(neighbor, pipe.getName());
          parentPipeForward.put(neighbor, forward);
          treeEdges.add(pipe.getName());
          queue.add(neighbor);
        }
      }
    }

    // Step 2: Initialize all pipe flows to zero
    for (NetworkPipe pipe : pipes.values()) {
      pipe.setFlowRate(0.0);
    }

    // Step 3: Bottom-up: compute subtree demand and set tree-pipe flows
    // subtreeDemand[node] = node's local demand + sum of children's subtree demands
    Map<String, Double> subtreeDemand = new HashMap<>();

    for (int i = bfsOrder.size() - 1; i >= 0; i--) {
      String nodeName = bfsOrder.get(i);
      NetworkNode node = nodes.get(nodeName);

      // Start with local demand (kg/s; positive = outflow/demand, negative = supply)
      double demand = node.getDemand();

      // Add subtree demands of all BFS children of this node
      for (String child : bfsOrder) {
        if (parentPipeName.containsKey(child)) {
          // Find the parent of 'child' - it's the other end of the parent pipe
          String pipeName = parentPipeName.get(child);
          boolean fwd = parentPipeForward.get(child);
          NetworkPipe pipe = pipes.get(pipeName);
          String parent = fwd ? pipe.getFromNode() : pipe.getToNode();
          if (parent.equals(nodeName)) {
            demand += subtreeDemand.getOrDefault(child, 0.0);
          }
        }
      }
      subtreeDemand.put(nodeName, demand);

      // Set tree pipe flow from parent to this node
      if (parentPipeName.containsKey(nodeName)) {
        String pipeName = parentPipeName.get(nodeName);
        boolean forward = parentPipeForward.get(nodeName);
        NetworkPipe pipe = pipes.get(pipeName);

        // The pipe must deliver 'demand' kg/s into this node's subtree
        // forward=true: pipe from->to = parent->child, so positive flow = delivery
        // forward=false: pipe to->from = parent->child, so negative flow = delivery
        pipe.setFlowRate(forward ? demand : -demand);
      }
    }

    // Non-tree pipes remain at 0 — loop solver will correct them

    // For pressure-driven mode (all nodes fixed-pressure, all demands zero):
    // BFS gives zero flows everywhere. Use pressure-difference initial estimate instead.
    boolean allFlowsZero = true;
    for (NetworkPipe pipe : pipes.values()) {
      if (Math.abs(pipe.getFlowRate()) > 1e-15) {
        allFlowsZero = false;
        break;
      }
    }
    if (allFlowsZero) {
      // Estimate initial flows from pressure differences using simplified resistance
      SystemInterface fluid = fluidTemplate.clone();
      try {
        neqsim.thermodynamicoperations.ThermodynamicOperations ops =
            new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
        ops.TPflash();
      } catch (Exception ex) {
        logger.warn("TP flash failed during flow init: " + ex.getMessage());
      }
      fluid.initProperties();
      double density = fluid.getDensity("kg/m3");
      double viscosity = fluid.getViscosity("kg/msec");

      for (NetworkPipe pipe : pipes.values()) {
        NetworkNode fromNode = nodes.get(pipe.getFromNode());
        NetworkNode toNode = nodes.get(pipe.getToNode());
        double dP = fromNode.getPressure() - toNode.getPressure()
            - density * 9.81 * (toNode.getElevation() - fromNode.getElevation());
        if (Math.abs(dP) < 1.0) {
          dP = 100.0; // Small default if pressures are equal
        }
        double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
        // Rough estimate: assume f=0.02, Q = sqrt(|dP| * D * 2 * rho * A^2 / (f * L)) * sign
        double ff = 0.02;
        double denom = ff * pipe.getLength() / (pipe.getDiameter() * 2.0 * density * area * area);
        double qEstimate = Math.sqrt(Math.abs(dP) / denom) * Math.signum(dP);
        pipe.setFlowRate(qEstimate);
      }
    }
  }

  /**
   * Calculate head loss for a pipe using Darcy-Weisbach equation with elevation.
   *
   * <p>
   * Total head loss includes friction loss and hydrostatic head change: dP_total = dP_friction +
   * rho * g * (z_to - z_from)
   * </p>
   *
   * @param pipe the pipe
   * @param fluid fluid properties
   * @return head loss in Pa (positive = pressure drop from-to direction)
   */
  private double calculateHeadLoss(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate()); // kg/s (internal unit)
    if (flowKgs < 1e-10) {
      // Still include hydrostatic head even at zero flow
      NetworkNode fromNode = nodes.get(pipe.getFromNode());
      NetworkNode toNode = nodes.get(pipe.getToNode());
      double density = fluid.getDensity("kg/m3");
      return density * 9.81 * (toNode.getElevation() - fromNode.getElevation());
    }

    // Get fluid properties
    double density = fluid.getDensity("kg/m3");
    double viscosity = fluid.getViscosity("kg/msec");

    // Calculate velocity from mass flow (kg/s)
    double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
    double velocity = flowKgs / (density * area);

    // Reynolds number
    double reynolds = density * velocity * pipe.getDiameter() / viscosity;

    // Friction factor (Colebrook-White, explicit approximation by Swamee-Jain)
    double relRoughness = pipe.getRoughness() / pipe.getDiameter();
    double frictionFactor;

    if (reynolds < 2300) {
      // Laminar flow
      frictionFactor = 64.0 / reynolds;
    } else {
      // Turbulent flow - Swamee-Jain approximation
      double term1 = relRoughness / 3.7;
      double term2 = 5.74 / Math.pow(reynolds, 0.9);
      frictionFactor = 0.25 / Math.pow(Math.log10(term1 + term2), 2);
    }

    // Darcy-Weisbach: dP_friction = f * (L/D) * (rho * v^2 / 2)
    double frictionLoss = frictionFactor * (pipe.getLength() / pipe.getDiameter())
        * (density * velocity * velocity / 2.0);

    // Hydrostatic head: dP_elevation = rho * g * (z_out - z_in)
    NetworkNode fromNode = nodes.get(pipe.getFromNode());
    NetworkNode toNode = nodes.get(pipe.getToNode());
    double elevationLoss = density * 9.81 * (toNode.getElevation() - fromNode.getElevation());

    // Store hydraulic parameters on the pipe for reporting
    pipe.setVelocity(velocity);
    pipe.setReynoldsNumber(reynolds);
    pipe.setFrictionFactor(frictionFactor);
    pipe.setFlowRegime(reynolds < 2300 ? "Laminar" : reynolds < 4000 ? "Transition" : "Turbulent");

    // Total head loss (apply sign based on flow direction)
    double totalLoss = frictionLoss + elevationLoss;
    return Math.signum(pipe.getFlowRate()) * totalLoss;
  }

  /**
   * Calculate derivative of head loss with respect to flow rate.
   *
   * @param pipe the pipe
   * @param fluid fluid properties
   * @return dh/dQ in Pa/(kg/s)
   */
  private double calculateHeadLossDerivative(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate()); // kg/s (internal unit)
    if (flowKgs < 1e-10) {
      flowKgs = 1e-10; // Avoid division by zero
    }

    // For turbulent flow, h is approximately proportional to Q^2
    // So dh/dQ ≈ 2h/Q where h is in Pa and Q is in kg/s
    // The Hardy Cross correction: dQ = -imbalance / sum(dh/dQ) gives kg/s
    double headLoss = Math.abs(calculateHeadLoss(pipe, fluid));

    return 2.0 * headLoss / flowKgs;
  }

  /**
   * Detect loops in the network using DFS spanning tree algorithm.
   */
  private void detectLoops() {
    LoopDetector detector = new LoopDetector();

    // Add edges to detector
    for (NetworkPipe pipe : pipes.values()) {
      detector.addEdge(pipe.getFromNode(), pipe.getToNode(), pipe.getName());
    }

    // Find loops
    loops = detector.findLoops();

    if (!loops.isEmpty()) {
      logger.info("Detected " + loops.size() + " independent loop(s) in network");
    }
  }

  /**
   * Run Hardy Cross iterative solver.
   *
   * @param id calculation identifier
   */
  private void runHardyCross(UUID id) {
    if (loops == null || loops.isEmpty()) {
      logger.info("No loops detected - using sequential solver");
      runSequential(id);
      return;
    }

    // Initialize fluid for calculations (must have density and viscosity)
    SystemInterface fluid = fluidTemplate.clone();
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
      ops.TPflash();
    } catch (Exception ex) {
      logger.warn("TP flash failed for fluid template: " + ex.getMessage());
    }
    fluid.initProperties();

    iterationCount = 0;
    converged = false;

    while (iterationCount < maxIterations && !converged) {
      iterationCount++;
      maxResidual = 0.0;

      // Update head losses for all pipes
      for (NetworkPipe pipe : pipes.values()) {
        double headLoss = calculateHeadLoss(pipe, fluid);
        pipe.setHeadLoss(headLoss);
      }

      // Calculate corrections for each loop
      for (NetworkLoop loop : loops) {
        // Calculate head loss imbalance around the loop
        double imbalance = 0.0;
        double derivativeSum = 0.0;

        for (NetworkLoop.LoopMember member : loop.getMembers()) {
          NetworkPipe pipe = pipes.get(member.getPipeName());
          if (pipe != null) {
            // Head loss with sign based on loop direction
            imbalance += member.getDirection() * pipe.getHeadLoss();

            // Derivative magnitude
            derivativeSum += calculateHeadLossDerivative(pipe, fluid);
          }
        }

        // Update max residual
        maxResidual = Math.max(maxResidual, Math.abs(imbalance));

        // Calculate flow correction
        double correction = 0.0;
        if (derivativeSum > 1e-10) {
          correction = -imbalance / derivativeSum;
          correction *= relaxationFactor;
        }

        // Apply correction to all pipes in loop
        for (NetworkLoop.LoopMember member : loop.getMembers()) {
          NetworkPipe pipe = pipes.get(member.getPipeName());
          if (pipe != null) {
            double newFlow = pipe.getFlowRate() + member.getDirection() * correction;
            pipe.setFlowRate(newFlow);
          }
        }
      }

      // Check convergence
      if (maxResidual < tolerance) {
        converged = true;
      }

      if (iterationCount % 10 == 0) {
        logger.debug(
            "Hardy Cross iteration " + iterationCount + ", max residual: " + maxResidual + " Pa");
      }
    }

    if (converged) {
      logger.info("Hardy Cross converged in " + iterationCount + " iterations, residual: "
          + maxResidual + " Pa");
    } else {
      logger.warn("Hardy Cross did not converge after " + iterationCount + " iterations, residual: "
          + maxResidual + " Pa");
    }

    // Update node pressures based on final flow distribution
    updateNodePressures(fluid);
  }

  /**
   * Run sequential solver for tree networks.
   *
   * @param id calculation identifier
   */
  private void runSequential(UUID id) {
    // For tree networks, solve from sources to sinks
    // This is the default behavior for non-looped networks

    if (fluidTemplate == null) {
      throw new IllegalStateException("Fluid template not set");
    }

    // Initialize pipe models if needed
    initializePipeModels();

    // Run each pipe model
    for (String pipeName : pipeNames) {
      NetworkPipe pipe = pipes.get(pipeName);
      if (pipe.getPipeModel() != null) {
        pipe.getPipeModel().run(id);
        // Extract head loss from model
        double inletP = pipe.getPipeModel().getInletStream().getPressure("Pa");
        double outletP = pipe.getPipeModel().getOutletStream().getPressure("Pa");
        pipe.setHeadLoss(inletP - outletP);
      }
    }

    converged = true;
    iterationCount = 1;
  }

  /**
   * Initialize AdiabaticPipe models for each pipe.
   */
  private void initializePipeModels() {
    for (NetworkPipe pipe : pipes.values()) {
      if (pipe.getPipeModel() == null) {
        NetworkNode fromNode = nodes.get(pipe.getFromNode());

        // Create inlet stream
        SystemInterface inletFluid = fluidTemplate.clone();
        inletFluid.setTemperature(fromNode.getTemperature());
        inletFluid.setPressure(fromNode.getPressure() / 1e5); // bara

        Stream inlet = new Stream(pipe.getName() + "_inlet", inletFluid);
        inlet.setFlowRate(Math.abs(pipe.getFlowRate()), "kg/sec");
        inlet.run();

        // Create pipe model
        AdiabaticPipe model = new AdiabaticPipe(pipe.getName(), inlet);
        model.setLength(pipe.getLength());
        model.setDiameter(pipe.getDiameter());

        pipe.setPipeModel(model);
      }
    }
  }

  /**
   * Update node pressures based on calculated head losses.
   *
   * @param fluid fluid for calculations
   */
  private void updateNodePressures(SystemInterface fluid) {
    // Start from source nodes (fixed pressure) and propagate
    for (NetworkNode node : nodes.values()) {
      if (node.isPressureFixed()) {
        propagatePressure(node.getName(), fluid);
      }
    }
  }

  /**
   * Propagate pressure from a node to connected nodes.
   *
   * @param nodeName starting node name
   * @param fluid fluid for calculations
   */
  private void propagatePressure(String nodeName, SystemInterface fluid) {
    java.util.Set<String> visited = new java.util.HashSet<>();
    java.util.Queue<String> queue = new java.util.LinkedList<>();

    queue.add(nodeName);
    visited.add(nodeName);

    while (!queue.isEmpty()) {
      String current = queue.poll();
      NetworkNode currentNode = nodes.get(current);

      // Find all connected pipes
      for (NetworkPipe pipe : pipes.values()) {
        String neighbor = null;
        double pressureDrop = 0.0;

        if (pipe.getFromNode().equals(current)) {
          neighbor = pipe.getToNode();
          pressureDrop = pipe.getHeadLoss(); // Positive = pressure drops from->to
        } else if (pipe.getToNode().equals(current)) {
          neighbor = pipe.getFromNode();
          pressureDrop = -pipe.getHeadLoss(); // Negative = pressure drops to->from
        }

        if (neighbor != null && !visited.contains(neighbor)) {
          NetworkNode neighborNode = nodes.get(neighbor);
          if (!neighborNode.isPressureFixed()) {
            neighborNode.setPressure(currentNode.getPressure() - pressureDrop);
          }
          visited.add(neighbor);
          queue.add(neighbor);
        }
      }
    }
  }

  @Override
  public void run(UUID id) {
    if (pipes.isEmpty()) {
      return;
    }

    if (fluidTemplate == null) {
      throw new IllegalStateException("Fluid template must be set before running");
    }

    // Detect loops if not already done
    if (loops == null) {
      detectLoops();
    }

    // Initialize flow estimates
    initializeFlowEstimates();

    // Run appropriate solver
    switch (solverType) {
      case HARDY_CROSS:
        runHardyCross(id);
        break;
      case SEQUENTIAL:
        runSequential(id);
        break;
      case NEWTON_RAPHSON:
        runNewtonRaphson(id);
        break;
      default:
        runHardyCross(id);
    }

    setCalculationIdentifier(id);

    // Update hydraulic properties for all pipes after solver completes
    updatePipeHydraulicProperties();

    // Calculate mass balance error
    calculateMassBalanceError();
  }

  /**
   * Update hydraulic properties (velocity, Reynolds, friction factor) for all pipes.
   */
  private void updatePipeHydraulicProperties() {
    if (fluidTemplate == null) {
      return;
    }
    SystemInterface fluid = fluidTemplate.clone();
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
      ops.TPflash();
    } catch (Exception ex) {
      logger.warn("TP flash failed in property update: " + ex.getMessage());
    }
    fluid.initProperties();

    for (NetworkPipe pipe : pipes.values()) {
      double headLoss = calculateHeadLoss(pipe, fluid);
      pipe.setHeadLoss(headLoss);
    }
  }

  /**
   * Run Newton-Raphson Global Gradient Algorithm (Todini-Pilati, 1988).
   *
   * <p>
   * Solves the system of nodal mass balance equations + pipe head loss equations simultaneously
   * using a Newton-Raphson scheme. This is the algorithm used by EPANET and PIPESIM Network.
   * </p>
   *
   * <p>
   * The method solves: [A11 A12] [dQ] = [f1] where A11 = diag(dh/dQ), A12 = incidence matrix, [A21
   * 0 ] [dH] = [f2] A21 = A12^T, f1 = head residuals, f2 = flow residuals
   * </p>
   *
   * @param id calculation identifier
   */
  private void runNewtonRaphson(UUID id) {
    // Initialize fluid for property calculation
    SystemInterface fluid = fluidTemplate.clone();
    try {
      neqsim.thermodynamicoperations.ThermodynamicOperations ops =
          new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
      ops.TPflash();
    } catch (Exception ex) {
      logger.warn("TP flash failed for fluid template: " + ex.getMessage());
    }
    fluid.initProperties();

    // Build ordered lists for matrix indexing
    List<String> pipeList = new ArrayList<>(pipeNames);
    List<String> freeNodeList = new ArrayList<>();
    for (NetworkNode node : nodes.values()) {
      if (!node.isPressureFixed()) {
        freeNodeList.add(node.getName());
      }
    }

    int np = pipeList.size(); // number of pipes
    int nn = freeNodeList.size(); // number of free nodes (unknown pressures)

    // When nn == 0 (all pressures fixed), the Schur complement is empty and
    // the back-substitution dQ = D^{-1}*(-f1) determines flows directly from
    // pressure differences. This is the "pressure-pressure" deliverability mode.

    // Initialize pressures for free nodes (average of source pressures)
    double avgSourcePressure = 0.0;
    int sourceCount = 0;
    for (NetworkNode node : nodes.values()) {
      if (node.isPressureFixed()) {
        avgSourcePressure += node.getPressure();
        sourceCount++;
      }
    }
    if (sourceCount > 0) {
      avgSourcePressure /= sourceCount;
    }
    for (String nodeName : freeNodeList) {
      NetworkNode node = nodes.get(nodeName);
      if (node.getPressure() < 1.0) {
        node.setPressure(avgSourcePressure * 0.95); // Start slightly below sources
      }
    }

    iterationCount = 0;
    converged = false;
    double density = fluid.getDensity("kg/m3");
    double viscosity = fluid.getViscosity("kg/msec");

    while (iterationCount < maxIterations && !converged) {
      iterationCount++;

      // --- Work in SI units: Pa for pressure, kg/s for flow ---
      // --- Step 1: Calculate resistance coefficients for each pipe ---
      // h_i = r_i * Q_i * |Q_i| where r_i = f * L / (D * 2 * rho * A^2) [Pa/(kg/s)^2]
      double[] resistance = new double[np];
      double[] pipeFlowsSI = new double[np]; // kg/s

      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        pipeFlowsSI[i] = pipe.getFlowRate(); // Already in kg/s

        double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
        double absFlow = Math.abs(pipeFlowsSI[i]);
        if (absFlow < 1e-10) {
          absFlow = 1e-10;
        }

        double vel = absFlow / (density * area);
        double reynolds = density * vel * pipe.getDiameter() / viscosity;
        double ff;
        if (reynolds < 2300) {
          ff = 64.0 / Math.max(reynolds, 1.0);
        } else {
          double e = pipe.getRoughness() / pipe.getDiameter();
          ff = 0.25 / Math.pow(Math.log10(e / 3.7 + 5.74 / Math.pow(reynolds, 0.9)), 2);
        }
        resistance[i] = ff * pipe.getLength() / (pipe.getDiameter() * 2.0 * density * area * area);
      }

      // --- Step 2: Build and solve the linearized system ---
      // Using Schur complement: solve for dH first, then back-substitute for dQ.
      //
      // From: A11*dQ + A12*dH = -f1 and A21*dQ = -f2
      // Where A11 = diag(2*r_i*|Q_i|), and A12 is incidence matrix (pipe-to-node)
      //
      // Schur complement: (A21 * A11^{-1} * A12) * dH = -f2 + A21 * A11^{-1} * f1
      //
      // This is an nn x nn system (small for typical networks).

      // Build A11 diagonal (inverse)
      double[] a11inv = new double[np];
      for (int i = 0; i < np; i++) {
        double absQ = Math.abs(pipeFlowsSI[i]);
        if (absQ < 1e-10) {
          absQ = 1e-10;
        }
        a11inv[i] = 1.0 / (2.0 * resistance[i] * absQ);
      }

      // Build pipe head residuals (all in Pa):
      // f1_i = r_i * Q_i * |Q_i| - (P_from - P_to) + rho*g*(z_to - z_from)
      double[] f1 = new double[np];
      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        double pFromPa = nodes.get(pipe.getFromNode()).getPressure(); // Already in Pa
        double pToPa = nodes.get(pipe.getToNode()).getPressure(); // Already in Pa
        double elevDiff = nodes.get(pipe.getToNode()).getElevation()
            - nodes.get(pipe.getFromNode()).getElevation();
        f1[i] = resistance[i] * pipeFlowsSI[i] * Math.abs(pipeFlowsSI[i]) - (pFromPa - pToPa)
            + density * 9.81 * elevDiff;
      }

      // Build node flow residuals (in kg/s):
      // f2_j = demand_j - sum(Q_i * sign_ij)
      double[] f2 = new double[nn];
      for (int j = 0; j < nn; j++) {
        String nodeName = freeNodeList.get(j);
        NetworkNode node = nodes.get(nodeName);
        double netFlow = node.getDemand(); // Already in kg/s
        for (int i = 0; i < np; i++) {
          NetworkPipe pipe = pipes.get(pipeList.get(i));
          if (pipe.getToNode().equals(nodeName)) {
            netFlow -= pipeFlowsSI[i]; // inflow
          }
          if (pipe.getFromNode().equals(nodeName)) {
            netFlow += pipeFlowsSI[i]; // outflow
          }
        }
        f2[j] = netFlow;
      }

      // Build Schur complement matrix S = A21 * A11^{-1} * A12 (nn x nn)
      double[][] schur = new double[nn][nn];
      double[] rhs = new double[nn]; // -f2 + A21 * A11^{-1} * f1

      // Process each pipe's contribution to Schur complement
      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        int jFrom = freeNodeList.indexOf(pipe.getFromNode()); // -1 if fixed
        int jTo = freeNodeList.indexOf(pipe.getToNode()); // -1 if fixed

        double val = a11inv[i];

        // Incidence: from-node is +1, to-node is -1 for pipe flow
        // A12[i][jFrom] = +1, A12[i][jTo] = -1
        // A21 = A12^T: A21[jFrom][i] = +1, A21[jTo][i] = -1
        // Contribution to S: A21[:,i] * a11inv[i] * A12[i,:]

        if (jFrom >= 0) {
          schur[jFrom][jFrom] += val;
          rhs[jFrom] += val * f1[i];
        }
        if (jTo >= 0) {
          schur[jTo][jTo] += val;
          rhs[jTo] -= val * f1[i];
        }
        if (jFrom >= 0 && jTo >= 0) {
          schur[jFrom][jTo] -= val;
          schur[jTo][jFrom] -= val;
        }
      }

      // RHS = -f2 + A21*A11^{-1}*f1
      for (int j = 0; j < nn; j++) {
        rhs[j] = -f2[j] + rhs[j];
      }

      // Solve S * dH = rhs using Gaussian elimination (small system)
      // dH is in Pa
      double[] dH = solveLinearSystem(schur, rhs, nn);

      // --- Step 3: Update pressures (convert Pa correction to bara) ---
      for (int j = 0; j < nn; j++) {
        NetworkNode node = nodes.get(freeNodeList.get(j));
        node.setPressure(node.getPressure() + relaxationFactor * dH[j]); // Both in Pa
      }

      // --- Step 4: Back-substitute to get new pipe flows (in kg/s, then convert to kg/hr) ---
      // From D*dQ - A^T*dH = -f1 => dQ = D^{-1}*(-f1 + A^T*dH)
      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        int jFrom = freeNodeList.indexOf(pipe.getFromNode());
        int jTo = freeNodeList.indexOf(pipe.getToNode());

        // A^T*dH for pipe i: +dH[jFrom] - dH[jTo]
        double atDh = 0.0;
        if (jFrom >= 0) {
          atDh += dH[jFrom];
        }
        if (jTo >= 0) {
          atDh -= dH[jTo];
        }

        double dQ = a11inv[i] * (-f1[i] + atDh); // kg/s correction
        double newFlowKgs = pipeFlowsSI[i] + relaxationFactor * dQ;
        pipe.setFlowRate(newFlowKgs); // Already in kg/s
      }

      // --- Step 5: Check convergence ---
      // f1 residuals are in Pa, f2 residuals are in kg/s (scale to Pa equivalent)
      maxResidual = 0.0;
      for (int i = 0; i < np; i++) {
        maxResidual = Math.max(maxResidual, Math.abs(f1[i]));
      }
      for (int j = 0; j < nn; j++) {
        // Scale flow residual: 1 kg/s flow error ~ 1e5 Pa pressure effect
        maxResidual = Math.max(maxResidual, Math.abs(f2[j]) * 1e5);
      }

      if (maxResidual < tolerance) {
        converged = true;
      }

      if (iterationCount % 10 == 0) {
        logger.debug("NR-GGA iteration " + iterationCount + ", max residual: " + maxResidual);
      }
    }

    // Update head losses and hydraulic parameters for reporting
    for (NetworkPipe pipe : pipes.values()) {
      double headLoss = calculateHeadLoss(pipe, fluid);
      pipe.setHeadLoss(headLoss);
    }

    if (converged) {
      logger.info("Newton-Raphson GGA converged in " + iterationCount + " iterations");
    } else {
      logger.warn("Newton-Raphson GGA did not converge after " + iterationCount + " iterations");
    }
  }

  /**
   * Solve a linear system Ax = b using Gaussian elimination with partial pivoting.
   *
   * @param matA coefficient matrix (n x n), modified in place
   * @param vecB right-hand side vector (n), modified in place
   * @param n system size
   * @return solution vector x
   */
  private double[] solveLinearSystem(double[][] matA, double[] vecB, int n) {
    double[] x = new double[n];

    // Forward elimination with partial pivoting
    for (int k = 0; k < n; k++) {
      // Find pivot
      int maxRow = k;
      for (int i = k + 1; i < n; i++) {
        if (Math.abs(matA[i][k]) > Math.abs(matA[maxRow][k])) {
          maxRow = i;
        }
      }
      // Swap rows
      double[] tempRow = matA[k];
      matA[k] = matA[maxRow];
      matA[maxRow] = tempRow;
      double tempB = vecB[k];
      vecB[k] = vecB[maxRow];
      vecB[maxRow] = tempB;

      if (Math.abs(matA[k][k]) < 1e-20) {
        continue; // Skip near-singular row
      }

      // Eliminate
      for (int i = k + 1; i < n; i++) {
        double factor = matA[i][k] / matA[k][k];
        for (int j = k + 1; j < n; j++) {
          matA[i][j] -= factor * matA[k][j];
        }
        vecB[i] -= factor * vecB[k];
      }
    }

    // Back substitution
    for (int i = n - 1; i >= 0; i--) {
      x[i] = vecB[i];
      for (int j = i + 1; j < n; j++) {
        x[i] -= matA[i][j] * x[j];
      }
      if (Math.abs(matA[i][i]) > 1e-20) {
        x[i] /= matA[i][i];
      }
    }

    return x;
  }

  /**
   * Calculate overall mass balance error for the network.
   */
  private void calculateMassBalanceError() {
    massBalanceError = 0.0;
    for (NetworkNode node : nodes.values()) {
      if (node.isPressureFixed()) {
        continue; // Source nodes balance is implicit
      }
      double netFlow = node.getDemand(); // positive = outflow
      for (NetworkPipe pipe : pipes.values()) {
        if (pipe.getToNode().equals(node.getName())) {
          netFlow -= pipe.getFlowRate(); // inflow
        }
        if (pipe.getFromNode().equals(node.getName())) {
          netFlow += pipe.getFlowRate(); // outflow
        }
      }
      massBalanceError = Math.max(massBalanceError, Math.abs(netFlow));
    }
  }

  /**
   * Validate the network topology. Checks connectivity, mass balance feasibility, and required
   * fluid template.
   *
   * @return list of validation messages (empty if valid)
   */
  public List<String> validate() {
    List<String> issues = new ArrayList<>();

    if (fluidTemplate == null) {
      issues.add("ERROR: Fluid template not set");
    }
    if (pipes.isEmpty()) {
      issues.add("ERROR: No pipes in network");
    }
    if (nodes.isEmpty()) {
      issues.add("ERROR: No nodes in network");
    }

    // Check at least one source
    boolean hasSource = false;
    for (NetworkNode node : nodes.values()) {
      if (node.getType() == NodeType.SOURCE) {
        hasSource = true;
        break;
      }
    }
    if (!hasSource) {
      issues.add("ERROR: No source node defined (need at least one fixed-pressure node)");
    }

    // Check mass balance feasibility (total supply >= total demand)
    double totalSupply = 0.0;
    double totalDemand = 0.0;
    for (NetworkNode node : nodes.values()) {
      if (node.getDemand() < 0) {
        totalSupply += Math.abs(node.getDemand());
      } else {
        totalDemand += node.getDemand();
      }
    }
    if (totalDemand > totalSupply * 1.001 && totalSupply > 0) {
      issues.add("WARNING: Total demand (" + String.format("%.2f", totalDemand * 3600)
          + " kg/hr) exceeds supply (" + String.format("%.2f", totalSupply * 3600) + " kg/hr)");
    }

    // Check all pipe endpoints exist
    for (NetworkPipe pipe : pipes.values()) {
      if (!nodes.containsKey(pipe.getFromNode())) {
        issues.add("ERROR: Pipe '" + pipe.getName() + "' references unknown node '"
            + pipe.getFromNode() + "'");
      }
      if (!nodes.containsKey(pipe.getToNode())) {
        issues.add("ERROR: Pipe '" + pipe.getName() + "' references unknown node '"
            + pipe.getToNode() + "'");
      }
    }

    return issues;
  }

  /**
   * Get pipe velocity in m/s.
   *
   * @param pipeName pipe name
   * @return velocity in m/s (after solving)
   */
  public double getPipeVelocity(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getVelocity();
  }

  /**
   * Get head loss for a specific pipe in bara.
   *
   * @param pipeName pipe name
   * @return head loss in bara
   */
  public double getPipeHeadLoss(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getHeadLoss() / 1e5; // Convert Pa to bara
  }

  /**
   * Get Reynolds number for a specific pipe.
   *
   * @param pipeName pipe name
   * @return Reynolds number (dimensionless, after solving)
   */
  public double getPipeReynoldsNumber(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getReynoldsNumber();
  }

  /**
   * Get Darcy friction factor for a specific pipe.
   *
   * @param pipeName pipe name
   * @return friction factor (dimensionless, after solving)
   */
  public double getPipeFrictionFactor(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getFrictionFactor();
  }

  /**
   * Get flow regime description for a specific pipe.
   *
   * @param pipeName pipe name
   * @return flow regime string (e.g. "Turbulent", "Laminar")
   */
  public String getPipeFlowRegime(String pipeName) {
    NetworkPipe pipe = pipes.get(pipeName);
    if (pipe == null) {
      throw new IllegalArgumentException("Pipe '" + pipeName + "' not found");
    }
    return pipe.getFlowRegime();
  }

  /**
   * Get a summary of the network solution.
   *
   * @return summary map
   */
  public Map<String, Object> getSolutionSummary() {
    Map<String, Object> summary = new HashMap<>();

    summary.put("networkName", getName());
    summary.put("numberOfNodes", nodes.size());
    summary.put("numberOfPipes", pipes.size());
    summary.put("numberOfLoops", getNumberOfLoops());
    summary.put("solverType", solverType.name());
    summary.put("converged", converged);
    summary.put("iterations", iterationCount);
    summary.put("maxResidual_Pa", maxResidual);
    summary.put("tolerance_Pa", tolerance);
    summary.put("massBalanceError_kgs", massBalanceError);

    // Node pressures
    Map<String, Double> nodePressures = new HashMap<>();
    for (NetworkNode node : nodes.values()) {
      nodePressures.put(node.getName(), node.getPressure() / 1e5); // bara
    }
    summary.put("nodePressures_bara", nodePressures);

    // Pipe flows
    Map<String, Double> pipeFlows = new HashMap<>();
    for (NetworkPipe pipe : pipes.values()) {
      pipeFlows.put(pipe.getName(), pipe.getFlowRate() * 3600.0); // kg/hr
    }
    summary.put("pipeFlowRates_kghr", pipeFlows);

    return summary;
  }

  @Override
  public String toJson() {
    JsonObject json = new JsonObject();

    json.addProperty("name", getName());
    json.addProperty("numberOfNodes", nodes.size());
    json.addProperty("numberOfPipes", pipes.size());
    json.addProperty("numberOfLoops", getNumberOfLoops());
    json.addProperty("solverType", solverType.name());
    json.addProperty("converged", converged);
    json.addProperty("iterations", iterationCount);
    json.addProperty("maxResidual_Pa", maxResidual);

    // Nodes
    JsonArray nodesArray = new JsonArray();
    for (NetworkNode node : nodes.values()) {
      JsonObject nodeJson = new JsonObject();
      nodeJson.addProperty("name", node.getName());
      nodeJson.addProperty("type", node.getType().name());
      nodeJson.addProperty("pressure_bara", node.getPressure() / 1e5);
      nodeJson.addProperty("demand_kghr", node.getDemand() * 3600.0);
      nodeJson.addProperty("temperature_K", node.getTemperature());
      nodeJson.addProperty("elevation_m", node.getElevation());
      nodesArray.add(nodeJson);
    }
    json.add("nodes", nodesArray);

    // Pipes
    JsonArray pipesArray = new JsonArray();
    for (NetworkPipe pipe : pipes.values()) {
      JsonObject pipeJson = new JsonObject();
      pipeJson.addProperty("name", pipe.getName());
      pipeJson.addProperty("fromNode", pipe.getFromNode());
      pipeJson.addProperty("toNode", pipe.getToNode());
      pipeJson.addProperty("length_m", pipe.getLength());
      pipeJson.addProperty("diameter_m", pipe.getDiameter());
      pipeJson.addProperty("roughness_m", pipe.getRoughness());
      pipeJson.addProperty("flowRate_kghr", pipe.getFlowRate() * 3600.0);
      pipeJson.addProperty("headLoss_Pa", pipe.getHeadLoss());
      pipeJson.addProperty("headLoss_bar", pipe.getHeadLoss() / 1e5);
      pipeJson.addProperty("velocity_ms", pipe.getVelocity());
      pipeJson.addProperty("reynoldsNumber", pipe.getReynoldsNumber());
      pipeJson.addProperty("frictionFactor", pipe.getFrictionFactor());
      pipeJson.addProperty("flowRegime", pipe.getFlowRegime());
      pipesArray.add(pipeJson);
    }
    json.add("pipes", pipesArray);

    // Loops
    if (loops != null && !loops.isEmpty()) {
      JsonArray loopsArray = new JsonArray();
      for (NetworkLoop loop : loops) {
        JsonObject loopJson = new JsonObject();
        loopJson.addProperty("id", loop.getLoopId());

        JsonArray membersArray = new JsonArray();
        for (NetworkLoop.LoopMember member : loop.getMembers()) {
          JsonObject memberJson = new JsonObject();
          memberJson.addProperty("pipeName", member.getPipeName());
          memberJson.addProperty("direction", member.getDirection());
          membersArray.add(memberJson);
        }
        loopJson.add("members", membersArray);
        loopsArray.add(loopJson);
      }
      json.add("loops", loopsArray);
    }

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(json);
  }
}
