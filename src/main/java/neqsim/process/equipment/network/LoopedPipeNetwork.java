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
     * Newton-Raphson simultaneous solver.
     */
    NEWTON_RAPHSON
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
    private AdiabaticPipe pipeModel;

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
  }

  // Network topology
  private final Map<String, NetworkNode> nodes = new HashMap<>();
  private final Map<String, NetworkPipe> pipes = new HashMap<>();
  private final List<String> pipeNames = new ArrayList<>();

  // Solver settings
  private SolverType solverType = SolverType.HARDY_CROSS;
  private double tolerance = 1e-6; // Pa for head loss balance
  private int maxIterations = 100;
  private double relaxationFactor = 1.0;

  // Solution state
  private List<NetworkLoop> loops;
  private int iterationCount = 0;
  private double maxResidual = 0.0;
  private boolean converged = false;
  private SystemInterface fluidTemplate;

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
   * Initialize pipe flow estimates using topological analysis.
   */
  private void initializeFlowEstimates() {
    // Simple initialization: distribute supply evenly through shortest paths
    // More sophisticated methods could use spanning tree analysis

    double totalSupply = 0.0;
    for (NetworkNode node : nodes.values()) {
      if (node.getType() == NodeType.SOURCE) {
        totalSupply += Math.abs(node.getDemand());
      }
    }

    // Initial estimate: assign flow based on pipe conductance
    for (NetworkPipe pipe : pipes.values()) {
      // Hydraulic conductance estimate (proportional to D^5/L)
      double conductance =
          Math.pow(pipe.getDiameter(), 5) / (pipe.getLength() * pipe.getRoughness());

      // Initial flow estimate
      pipe.setFlowRate(totalSupply * 0.5 * conductance / getTotalConductance());
    }
  }

  /**
   * Get total conductance of all pipes.
   *
   * @return total conductance
   */
  private double getTotalConductance() {
    double total = 0.0;
    for (NetworkPipe pipe : pipes.values()) {
      total += Math.pow(pipe.getDiameter(), 5) / (pipe.getLength() * pipe.getRoughness());
    }
    return total;
  }

  /**
   * Calculate head loss for a pipe using Darcy-Weisbach equation.
   *
   * @param pipe the pipe
   * @param fluid fluid properties
   * @return head loss in Pa
   */
  private double calculateHeadLoss(NetworkPipe pipe, SystemInterface fluid) {
    double flow = Math.abs(pipe.getFlowRate());
    if (flow < 1e-10) {
      return 0.0;
    }

    // Get fluid properties
    double density = fluid.getDensity("kg/m3");
    double viscosity = fluid.getViscosity("kg/msec");

    // Calculate velocity
    double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
    double velocity = flow / (density * area);

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

    // Darcy-Weisbach equation: dP = f * (L/D) * (rho * v^2 / 2)
    double headLoss = frictionFactor * (pipe.getLength() / pipe.getDiameter())
        * (density * velocity * velocity / 2.0);

    // Apply sign based on flow direction
    return Math.signum(pipe.getFlowRate()) * headLoss;
  }

  /**
   * Calculate derivative of head loss with respect to flow rate.
   *
   * @param pipe the pipe
   * @param fluid fluid properties
   * @return dh/dQ in Pa/(kg/s)
   */
  private double calculateHeadLossDerivative(NetworkPipe pipe, SystemInterface fluid) {
    double flow = Math.abs(pipe.getFlowRate());
    if (flow < 1e-10) {
      flow = 1e-10; // Avoid division by zero
    }

    // For turbulent flow, h is approximately proportional to Q^2
    // So dh/dQ ≈ 2h/Q
    double headLoss = Math.abs(calculateHeadLoss(pipe, fluid));

    return 2.0 * headLoss / flow;
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

    // Initialize fluid for calculations
    SystemInterface fluid = fluidTemplate.clone();

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
        // TODO: Implement Newton-Raphson solver
        runHardyCross(id);
        break;
      default:
        runHardyCross(id);
    }

    setCalculationIdentifier(id);
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
