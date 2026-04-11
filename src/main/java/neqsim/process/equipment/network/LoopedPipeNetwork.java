package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
   * Network element type for generalized resistance elements.
   *
   * <p>
   * Each "pipe" in the network can represent different physical elements. The NR-GGA solver uses
   * custom &Delta;P(Q) and d&Delta;P/dQ functions for each element type, enabling production
   * network modeling with wells, chokes, tubing, and multiphase pipelines.
   * </p>
   */
  public enum NetworkElementType {
    /**
     * Standard single-phase pipe using Darcy-Weisbach equation (default).
     */
    PIPE,

    /**
     * Reservoir inflow performance relationship (IPR). Models flow from reservoir to wellbore using
     * PI, Vogel, or Fetkovich correlations. The pressure drop across this element is &Delta;P =
     * P_reservoir - P_wellbore = f(Q) per the selected IPR model.
     */
    WELL_IPR,

    /**
     * Production choke or control valve. Uses simplified valve equation: Q = Kv * opening *
     * sqrt(&Delta;P / SG) for subcritical flow.
     */
    CHOKE,

    /**
     * Wellbore tubing using simplified multiphase vertical lift performance. Pressure drop includes
     * gravity (hydrostatic head), friction, and acceleration terms.
     */
    TUBING,

    /**
     * Multiphase pipeline using Beggs-Brill correlation. Wraps NeqSim's PipeBeggsAndBrills for
     * accurate pressure drop in two-phase and three-phase systems.
     */
    MULTIPHASE_PIPE
  }

  /**
   * IPR model type for well inflow performance.
   */
  public enum IPRType {
    /**
     * Constant productivity index: q = PI * (P_r - P_wf) for liquid, q = PI * (P_r^2 - P_wf^2) for
     * gas.
     */
    PRODUCTIVITY_INDEX,

    /**
     * Vogel correlation for solution-gas-drive oil wells: q/q_max = 1 - 0.2*(P_wf/P_r) -
     * 0.8*(P_wf/P_r)^2.
     */
    VOGEL,

    /**
     * Fetkovich correlation for gas wells: q = C * (P_r^2 - P_wf^2)^n.
     */
    FETKOVICH
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

    // Element type and production parameters
    private NetworkElementType elementType = NetworkElementType.PIPE;

    // IPR parameters (for WELL_IPR element type)
    private IPRType iprType = IPRType.PRODUCTIVITY_INDEX;
    private double reservoirPressure = 0.0; // Pa
    private double productivityIndex = 0.0; // kg/s/Pa (for gas: kg/s/Pa^2)
    private double vogelQmax = 0.0; // kg/s (AOF)
    private double fetkovichC = 0.0; // kg/s/Pa^(2n)
    private double fetkovichN = 1.0; // exponent (0.5-1.0)
    private boolean gasIPR = false; // true: use P^2 formulation for gas wells

    // Choke parameters (for CHOKE element type)
    private double chokeKv = 0.0; // m3/hr/sqrt(bar) - valve flow coefficient
    private double chokeOpening = 100.0; // percent (0-100)
    private double chokeCriticalPressureRatio = 0.5; // xt for critical flow

    // Tubing parameters (for TUBING element type)
    private double tubingInclination = 90.0; // degrees from horizontal (90 = vertical)
    private int tubingSegments = 10; // number of discretization segments

    // Multiphase pipe parameters (for MULTIPHASE_PIPE element type)
    private int multiphaseSegments = 10; // number of calculation segments

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

    /**
     * Get the network element type.
     *
     * @return element type
     */
    public NetworkElementType getElementType() {
      return elementType;
    }

    /**
     * Set the network element type.
     *
     * @param type element type
     */
    public void setElementType(NetworkElementType type) {
      this.elementType = type;
    }

    /**
     * Get the IPR model type.
     *
     * @return IPR type
     */
    public IPRType getIprType() {
      return iprType;
    }

    /**
     * Set the IPR model type.
     *
     * @param type IPR model type
     */
    public void setIprType(IPRType type) {
      this.iprType = type;
    }

    /**
     * Get reservoir pressure in Pa.
     *
     * @return reservoir pressure
     */
    public double getReservoirPressure() {
      return reservoirPressure;
    }

    /**
     * Set reservoir pressure in Pa.
     *
     * @param pressure reservoir pressure in Pa
     */
    public void setReservoirPressure(double pressure) {
      this.reservoirPressure = pressure;
    }

    /**
     * Get productivity index in kg/s/Pa (or kg/s/Pa^2 for gas).
     *
     * @return productivity index
     */
    public double getProductivityIndex() {
      return productivityIndex;
    }

    /**
     * Set productivity index in kg/s/Pa (or kg/s/Pa^2 for gas).
     *
     * @param pi productivity index
     */
    public void setProductivityIndex(double pi) {
      this.productivityIndex = pi;
    }

    /**
     * Get Vogel q_max (absolute open flow) in kg/s.
     *
     * @return Vogel q_max
     */
    public double getVogelQmax() {
      return vogelQmax;
    }

    /**
     * Set Vogel q_max (absolute open flow) in kg/s.
     *
     * @param qmax AOF in kg/s
     */
    public void setVogelQmax(double qmax) {
      this.vogelQmax = qmax;
    }

    /**
     * Get Fetkovich flow coefficient C.
     *
     * @return Fetkovich C
     */
    public double getFetkovichC() {
      return fetkovichC;
    }

    /**
     * Set Fetkovich flow coefficient C.
     *
     * @param c Fetkovich C
     */
    public void setFetkovichC(double c) {
      this.fetkovichC = c;
    }

    /**
     * Get Fetkovich exponent n.
     *
     * @return Fetkovich n
     */
    public double getFetkovichN() {
      return fetkovichN;
    }

    /**
     * Set Fetkovich exponent n.
     *
     * @param n Fetkovich exponent (0.5-1.0)
     */
    public void setFetkovichN(double n) {
      this.fetkovichN = n;
    }

    /**
     * Check if gas IPR formulation is used (P^2 drawdown).
     *
     * @return true if gas IPR
     */
    public boolean isGasIPR() {
      return gasIPR;
    }

    /**
     * Set whether to use gas IPR formulation (P^2 drawdown).
     *
     * @param gas true for gas wells
     */
    public void setGasIPR(boolean gas) {
      this.gasIPR = gas;
    }

    /**
     * Get choke valve flow coefficient Kv in m3/hr/sqrt(bar).
     *
     * @return Kv
     */
    public double getChokeKv() {
      return chokeKv;
    }

    /**
     * Set choke valve flow coefficient Kv in m3/hr/sqrt(bar).
     *
     * @param kv flow coefficient
     */
    public void setChokeKv(double kv) {
      this.chokeKv = kv;
    }

    /**
     * Get choke opening percentage (0-100).
     *
     * @return choke opening in percent
     */
    public double getChokeOpening() {
      return chokeOpening;
    }

    /**
     * Set choke opening percentage (0-100).
     *
     * @param opening choke opening in percent
     */
    public void setChokeOpening(double opening) {
      this.chokeOpening = opening;
    }

    /**
     * Get critical pressure ratio for choked flow.
     *
     * @return critical pressure ratio
     */
    public double getChokeCriticalPressureRatio() {
      return chokeCriticalPressureRatio;
    }

    /**
     * Set critical pressure ratio for choked flow.
     *
     * @param ratio critical pressure ratio (typically 0.4-0.6)
     */
    public void setChokeCriticalPressureRatio(double ratio) {
      this.chokeCriticalPressureRatio = ratio;
    }

    /**
     * Get tubing inclination from horizontal in degrees.
     *
     * @return inclination in degrees (90 = vertical)
     */
    public double getTubingInclination() {
      return tubingInclination;
    }

    /**
     * Set tubing inclination from horizontal in degrees.
     *
     * @param degrees inclination (90 = vertical, 0 = horizontal)
     */
    public void setTubingInclination(double degrees) {
      this.tubingInclination = degrees;
    }

    /**
     * Get number of tubing discretization segments.
     *
     * @return number of segments
     */
    public int getTubingSegments() {
      return tubingSegments;
    }

    /**
     * Set number of tubing discretization segments.
     *
     * @param segments number of segments
     */
    public void setTubingSegments(int segments) {
      this.tubingSegments = segments;
    }

    /**
     * Get number of multiphase pipe segments.
     *
     * @return number of segments
     */
    public int getMultiphaseSegments() {
      return multiphaseSegments;
    }

    /**
     * Set number of multiphase pipe segments.
     *
     * @param segments number of segments
     */
    public void setMultiphaseSegments(int segments) {
      this.multiphaseSegments = segments;
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

  // Stream integration for ProcessSystem connectivity
  private final transient Map<String, StreamInterface> feedStreams = new LinkedHashMap<>();
  private final transient Map<String, StreamInterface> outletStreams = new LinkedHashMap<>();

  /**
   * Create a new looped pipe network.
   *
   * @param name network name
   */
  public LoopedPipeNetwork(String name) {
    super(name);
  }

  /**
   * Create a new looped pipe network with a single feed stream.
   *
   * <p>
   * The feed stream's fluid is used as the network fluid template. The stream is associated with
   * the first source node added to the network. Call
   * {@link #setFeedStream(String, StreamInterface)} to explicitly bind a stream to a named source
   * node.
   * </p>
   *
   * @param name network name
   * @param feedStream inlet stream providing fluid composition, temperature and pressure
   */
  public LoopedPipeNetwork(String name, StreamInterface feedStream) {
    super(name);
    if (feedStream != null) {
      this.fluidTemplate = feedStream.getFluid().clone();
      this.feedStreams.put("__default__", feedStream);
    }
  }

  /**
   * Associate a feed stream with a named source node.
   *
   * <p>
   * When {@link #run(UUID)} is called, the stream's pressure and flow rate are read and applied to
   * the named source node. If the fluid template has not been set, it is derived from the first
   * feed stream.
   * </p>
   *
   * @param sourceNodeName name of an existing source node
   * @param stream the feed stream
   */
  public void setFeedStream(String sourceNodeName, StreamInterface stream) {
    if (stream == null) {
      return;
    }
    this.feedStreams.put(sourceNodeName, stream);
    if (this.fluidTemplate == null && stream.getFluid() != null) {
      this.fluidTemplate = stream.getFluid().clone();
    }
  }

  /**
   * Get the outlet stream for a named sink node.
   *
   * <p>
   * The returned stream contains the solved pressure, temperature, flow rate, and fluid composition
   * at the sink node. It is updated each time {@link #run(UUID)} completes and can be connected to
   * downstream process equipment.
   * </p>
   *
   * @param sinkNodeName name of a sink node
   * @return outlet stream, or null if the node does not exist or has not been solved
   */
  public StreamInterface getOutletStream(String sinkNodeName) {
    return outletStreams.get(sinkNodeName);
  }

  /**
   * Get the default outlet stream.
   *
   * <p>
   * Returns the outlet stream of the first sink node in the network. This provides a convenient
   * single-outlet accessor for networks with one delivery point.
   * </p>
   *
   * @return outlet stream of the first sink node, or null if no sinks exist
   */
  public StreamInterface getOutletStream() {
    if (outletStreams.isEmpty()) {
      return null;
    }
    return outletStreams.values().iterator().next();
  }

  /**
   * Get the outlet stream for a named source node (for IPR well sources where flow is computed).
   *
   * @param sourceNodeName name of a source node
   * @return outlet stream at the source node, or null if not yet solved
   */
  public StreamInterface getSourceNodeStream(String sourceNodeName) {
    NetworkNode node = nodes.get(sourceNodeName);
    if (node != null) {
      return node.getStream();
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    if (feedStreams.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(feedStreams.values()));
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    if (outletStreams.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(outletStreams.values()));
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
   * Add a well IPR element using the productivity index model.
   *
   * <p>
   * Creates a network element from reservoir node to wellbore node representing the inflow
   * performance relationship. For gas wells, uses P^2 drawdown: q = PI * (Pr^2 - Pwf^2). For oil
   * wells, uses linear drawdown: q = PI * (Pr - Pwf).
   * </p>
   *
   * @param reservoirNode name of reservoir (source) node (must be fixed-pressure)
   * @param wellboreNode name of wellbore/bottomhole node
   * @param elementName element name
   * @param productivityIndexSI productivity index in kg/s/Pa (oil) or kg/s/Pa^2 (gas)
   * @param isGas true for gas wells (P^2 formulation)
   * @return the created element
   */
  public NetworkPipe addWellIPR(String reservoirNode, String wellboreNode, String elementName,
      double productivityIndexSI, boolean isGas) {
    NetworkPipe element = addPipe(reservoirNode, wellboreNode, elementName, 1.0, 0.1); // dummy L, D
    element.setElementType(NetworkElementType.WELL_IPR);
    element.setIprType(IPRType.PRODUCTIVITY_INDEX);
    element.setProductivityIndex(productivityIndexSI);
    element.setGasIPR(isGas);
    // Store reservoir pressure from node
    NetworkNode resNode = nodes.get(reservoirNode);
    if (resNode != null) {
      element.setReservoirPressure(resNode.getPressure());
    }
    return element;
  }

  /**
   * Add a well IPR element using the Vogel model for solution-gas-drive oil wells.
   *
   * @param reservoirNode name of reservoir (source) node
   * @param wellboreNode name of wellbore node
   * @param elementName element name
   * @param qmaxKgS absolute open flow in kg/s
   * @return the created element
   */
  public NetworkPipe addWellIPRVogel(String reservoirNode, String wellboreNode, String elementName,
      double qmaxKgS) {
    NetworkPipe element = addPipe(reservoirNode, wellboreNode, elementName, 1.0, 0.1); // dummy L, D
    element.setElementType(NetworkElementType.WELL_IPR);
    element.setIprType(IPRType.VOGEL);
    element.setVogelQmax(qmaxKgS);
    NetworkNode resNode = nodes.get(reservoirNode);
    if (resNode != null) {
      element.setReservoirPressure(resNode.getPressure());
    }
    return element;
  }

  /**
   * Add a well IPR element using the Fetkovich model for gas wells.
   *
   * @param reservoirNode name of reservoir (source) node
   * @param wellboreNode name of wellbore node
   * @param elementName element name
   * @param cCoeff Fetkovich coefficient C in kg/s/Pa^(2n)
   * @param nExp Fetkovich exponent n (0.5-1.0)
   * @return the created element
   */
  public NetworkPipe addWellIPRFetkovich(String reservoirNode, String wellboreNode,
      String elementName, double cCoeff, double nExp) {
    NetworkPipe element = addPipe(reservoirNode, wellboreNode, elementName, 1.0, 0.1); // dummy L, D
    element.setElementType(NetworkElementType.WELL_IPR);
    element.setIprType(IPRType.FETKOVICH);
    element.setFetkovichC(cCoeff);
    element.setFetkovichN(nExp);
    element.setGasIPR(true);
    NetworkNode resNode = nodes.get(reservoirNode);
    if (resNode != null) {
      element.setReservoirPressure(resNode.getPressure());
    }
    return element;
  }

  /**
   * Add a production choke element between two nodes.
   *
   * <p>
   * The choke uses a simplified valve equation: Q = Kv * (opening/100) * sqrt(dP * rho). For
   * critical (choked) flow, the pressure drop is limited by the critical pressure ratio.
   * </p>
   *
   * @param fromNode upstream node name
   * @param toNode downstream node name
   * @param elementName element name
   * @param kv valve flow coefficient in m3/hr per sqrt(bar)
   * @param openingPercent valve opening (0-100%)
   * @return the created element
   */
  public NetworkPipe addChoke(String fromNode, String toNode, String elementName, double kv,
      double openingPercent) {
    NetworkPipe element = addPipe(fromNode, toNode, elementName, 0.5, 0.05); // dummy L, D
    element.setElementType(NetworkElementType.CHOKE);
    element.setChokeKv(kv);
    element.setChokeOpening(openingPercent);
    return element;
  }

  /**
   * Add wellbore tubing element (vertical lift performance).
   *
   * <p>
   * Models multiphase flow through wellbore tubing. Pressure drop includes hydrostatic head, wall
   * friction, and flow acceleration. Uses a simplified Beggs-Brill-like approach with gravity and
   * friction components.
   * </p>
   *
   * @param bottomNode bottomhole node name (higher pressure)
   * @param topNode wellhead node name (lower pressure)
   * @param elementName element name
   * @param lengthM tubing measured depth in meters
   * @param diameterM tubing inner diameter in meters
   * @param inclinationDeg inclination from horizontal (90 = vertical)
   * @return the created element
   */
  public NetworkPipe addTubing(String bottomNode, String topNode, String elementName,
      double lengthM, double diameterM, double inclinationDeg) {
    NetworkPipe element = addPipe(bottomNode, topNode, elementName, lengthM, diameterM);
    element.setElementType(NetworkElementType.TUBING);
    element.setTubingInclination(inclinationDeg);
    return element;
  }

  /**
   * Add a multiphase pipeline element using Beggs-Brill correlation.
   *
   * <p>
   * Uses NeqSim's PipeBeggsAndBrills internally for accurate multiphase pressure-drop calculation.
   * This element type is suitable for subsea flowlines and production pipelines carrying gas-oil or
   * gas-oil-water mixtures.
   * </p>
   *
   * @param fromNode upstream node name
   * @param toNode downstream node name
   * @param elementName element name
   * @param lengthM pipe length in meters
   * @param diameterM pipe inner diameter in meters
   * @return the created element
   */
  public NetworkPipe addMultiphasePipe(String fromNode, String toNode, String elementName,
      double lengthM, double diameterM) {
    NetworkPipe element = addPipe(fromNode, toNode, elementName, lengthM, diameterM);
    element.setElementType(NetworkElementType.MULTIPHASE_PIPE);
    return element;
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

        double qEstimate;

        switch (pipe.getElementType()) {
          case WELL_IPR:
            // Use IPR equation for initial flow estimate
            double absDp = Math.abs(dP);
            switch (pipe.getIprType()) {
              case VOGEL:
                // q/qmax = 1 - 0.2*(Pwf/Pr) - 0.8*(Pwf/Pr)^2
                // Rough: assume 50% drawdown ratio -> q ~ 0.65*qmax
                qEstimate = 0.3 * pipe.getVogelQmax() * Math.signum(dP);
                break;
              case FETKOVICH:
                double pRes = pipe.getReservoirPressure();
                if (pRes < 1.0) {
                  pRes = fromNode.getPressure();
                }
                double pWf = Math.max(pRes - absDp, pRes * 0.5);
                double p2d = pRes * pRes - pWf * pWf;
                qEstimate = pipe.getFetkovichC()
                    * Math.pow(Math.max(p2d, 0.0), pipe.getFetkovichN()) * Math.signum(dP);
                break;
              case PRODUCTIVITY_INDEX:
              default:
                if (pipe.isGasIPR()) {
                  double pResPI = pipe.getReservoirPressure();
                  if (pResPI < 1.0) {
                    pResPI = fromNode.getPressure();
                  }
                  double pWfPI = Math.max(pResPI - absDp, pResPI * 0.5);
                  qEstimate = pipe.getProductivityIndex() * (pResPI * pResPI - pWfPI * pWfPI)
                      * Math.signum(dP);
                } else {
                  qEstimate = pipe.getProductivityIndex() * dP;
                }
                break;
            }
            break;

          case CHOKE:
            // Q = Kv * opening * sqrt(dP * rho) -> Use valve equation
            double kvEff = pipe.getChokeKv() * pipe.getChokeOpening() / 100.0;
            if (kvEff > 1e-10) {
              // Kv is in m3/hr per sqrt(bar), dP is in Pa
              double dPbar = Math.abs(dP) / 1e5;
              double qVolM3hr = kvEff * Math.sqrt(dPbar);
              double qMassKgs = qVolM3hr * density / 3600.0;
              qEstimate = qMassKgs * Math.signum(dP);
            } else {
              qEstimate = 0.001 * Math.signum(dP);
            }
            break;

          default:
            // Standard Darcy-Weisbach estimate for pipes and tubing
            double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
            double ff = 0.02;
            double denom =
                ff * pipe.getLength() / (pipe.getDiameter() * 2.0 * density * area * area);
            qEstimate = Math.sqrt(Math.abs(dP) / denom) * Math.signum(dP);
            break;
        }
        pipe.setFlowRate(qEstimate);

        // Cap maximum initial flow to prevent divergence from unrealistic estimates
        double maxInitFlowKgs = 500.0; // kg/s (~1800 t/hr) - generous upper bound
        if (Math.abs(pipe.getFlowRate()) > maxInitFlowKgs) {
          pipe.setFlowRate(maxInitFlowKgs * Math.signum(pipe.getFlowRate()));
        }
      }

      // For serial element paths (source -> IPR -> junction -> choke -> junction -> pipe -> sink),
      // independent estimates often violate mass balance wildly. Equalize flows by setting each
      // junction's connected element flows to the minimum magnitude estimate (most restrictive).
      for (int pass = 0; pass < 3; pass++) {
        for (NetworkNode node : nodes.values()) {
          if (node.getType() != NodeType.JUNCTION) {
            continue;
          }
          // Find all elements connected to this junction
          List<NetworkPipe> connected = new ArrayList<>();
          for (NetworkPipe pp : pipes.values()) {
            if (pp.getFromNode().equals(node.getName()) || pp.getToNode().equals(node.getName())) {
              connected.add(pp);
            }
          }
          if (connected.size() == 2) {
            // Serial connection: equalize flows to the minimum magnitude
            double minMag = Math.min(Math.abs(connected.get(0).getFlowRate()),
                Math.abs(connected.get(1).getFlowRate()));
            if (minMag < 1e-10) {
              minMag = 0.1; // Ensure nonzero
            }
            for (NetworkPipe pp : connected) {
              pp.setFlowRate(minMag * Math.signum(pp.getFlowRate()));
            }
          }
        }
      }
    }
  }

  /**
   * Calculate head loss for a network element based on its type.
   *
   * <p>
   * Dispatches to the appropriate head loss model depending on the element type: Darcy-Weisbach
   * pipe, well IPR, production choke, tubing VLP, or multiphase pipe.
   * </p>
   *
   * @param pipe the network element
   * @param fluid fluid properties
   * @return head loss in Pa (positive = pressure drop from-to direction)
   */
  private double calculateHeadLoss(NetworkPipe pipe, SystemInterface fluid) {
    switch (pipe.getElementType()) {
      case WELL_IPR:
        return calculateHeadLossIPR(pipe, fluid);
      case CHOKE:
        return calculateHeadLossChoke(pipe, fluid);
      case TUBING:
        return calculateHeadLossTubing(pipe, fluid);
      case MULTIPHASE_PIPE:
        return calculateHeadLossMultiphase(pipe, fluid);
      case PIPE:
      default:
        return calculateHeadLossDarcyWeisbach(pipe, fluid);
    }
  }

  /**
   * Calculate head loss for a standard pipe using Darcy-Weisbach equation with elevation.
   *
   * @param pipe the pipe
   * @param fluid fluid properties
   * @return head loss in Pa
   */
  private double calculateHeadLossDarcyWeisbach(NetworkPipe pipe, SystemInterface fluid) {
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
      frictionFactor = 64.0 / reynolds;
    } else {
      double term1 = relRoughness / 3.7;
      double term2 = 5.74 / Math.pow(reynolds, 0.9);
      frictionFactor = 0.25 / Math.pow(Math.log10(term1 + term2), 2);
    }

    // Darcy-Weisbach: dP_friction = f * (L/D) * (rho * v^2 / 2)
    double frictionLoss = frictionFactor * (pipe.getLength() / pipe.getDiameter())
        * (density * velocity * velocity / 2.0);

    // Hydrostatic head
    NetworkNode fromNode = nodes.get(pipe.getFromNode());
    NetworkNode toNode = nodes.get(pipe.getToNode());
    double elevationLoss = density * 9.81 * (toNode.getElevation() - fromNode.getElevation());

    // Store hydraulic parameters
    pipe.setVelocity(velocity);
    pipe.setReynoldsNumber(reynolds);
    pipe.setFrictionFactor(frictionFactor);
    pipe.setFlowRegime(reynolds < 2300 ? "Laminar" : reynolds < 4000 ? "Transition" : "Turbulent");

    double totalLoss = frictionLoss + elevationLoss;
    return Math.signum(pipe.getFlowRate()) * totalLoss;
  }

  /**
   * Calculate head loss for a well IPR element.
   *
   * <p>
   * Models the reservoir-to-wellbore pressure relationship. For a given flow rate Q:
   * </p>
   * <ul>
   * <li>PI model (oil): dP = Q / PI</li>
   * <li>PI model (gas): Pr^2 - Pwf^2 = Q / PI, so dP = Pr - sqrt(Pr^2 - Q/PI)</li>
   * <li>Vogel: q/qmax = 1 - 0.2*(Pwf/Pr) - 0.8*(Pwf/Pr)^2</li>
   * <li>Fetkovich: q = C * (Pr^2 - Pwf^2)^n</li>
   * </ul>
   *
   * @param pipe the IPR element
   * @param fluid fluid properties (not used directly, IPR is empirical)
   * @return head loss in Pa
   */
  private double calculateHeadLossIPR(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    double pRes = pipe.getReservoirPressure(); // Pa

    if (flowKgs < 1e-10) {
      return 0.0; // No flow, no drawdown
    }

    double dP;
    switch (pipe.getIprType()) {
      case VOGEL:
        // Vogel: q/qmax = 1 - 0.2*(Pwf/Pr) - 0.8*(Pwf/Pr)^2
        // Solving for Pwf given q: quadratic in (Pwf/Pr)
        // 0.8*x^2 + 0.2*x + (q/qmax - 1) = 0 where x = Pwf/Pr
        double qRatio = Math.min(flowKgs / pipe.getVogelQmax(), 0.999);
        double disc = 0.04 + 3.2 * (1.0 - qRatio); // 0.2^2 + 4*0.8*(1-qRatio)
        double pRatio = (-0.2 + Math.sqrt(disc)) / 1.6; // Positive root
        pRatio = Math.max(0.0, Math.min(1.0, pRatio));
        dP = pRes * (1.0 - pRatio);
        break;

      case FETKOVICH:
        // q = C * (Pr^2 - Pwf^2)^n => (Pr^2 - Pwf^2) = (q/C)^(1/n)
        double cCoeff = pipe.getFetkovichC();
        double nExp = pipe.getFetkovichN();
        if (cCoeff < 1e-20) {
          return 0.0;
        }
        double p2diff = Math.pow(flowKgs / cCoeff, 1.0 / nExp);
        double pwf2 = pRes * pRes - p2diff;
        if (pwf2 < 0) {
          pwf2 = 0.0; // Beyond AOF
        }
        dP = pRes - Math.sqrt(pwf2);
        break;

      case PRODUCTIVITY_INDEX:
      default:
        if (pipe.isGasIPR()) {
          // Gas: q = PI * (Pr^2 - Pwf^2) => Pwf = sqrt(Pr^2 - q/PI)
          double pi = pipe.getProductivityIndex();
          if (pi < 1e-20) {
            return 0.0;
          }
          double pwf2Gas = pRes * pRes - flowKgs / pi;
          if (pwf2Gas < 0) {
            pwf2Gas = 0.0; // Beyond AOF
          }
          dP = pRes - Math.sqrt(pwf2Gas);
        } else {
          // Oil: q = PI * (Pr - Pwf) => dP = q / PI
          dP = flowKgs / Math.max(pipe.getProductivityIndex(), 1e-20);
        }
        break;
    }

    pipe.setFlowRegime("IPR-" + pipe.getIprType().name());
    return Math.signum(pipe.getFlowRate()) * dP;
  }

  /**
   * Calculate head loss for a production choke element.
   *
   * <p>
   * Uses a simplified valve equation: Q = Kv * (opening/100) * sqrt(dP * rho / SG_ref). Inverting:
   * dP = (Q / (Kv_eff))^2 / rho where Kv_eff = Kv * (opening/100) converted to SI.
   * </p>
   *
   * <p>
   * For critical (choked) flow, the effective dP is limited by the critical pressure ratio.
   * </p>
   *
   * @param pipe the choke element
   * @param fluid fluid properties
   * @return head loss in Pa
   */
  private double calculateHeadLossChoke(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    if (flowKgs < 1e-10) {
      return 0.0;
    }

    double density = fluid.getDensity("kg/m3");
    double kv = pipe.getChokeKv(); // m3/hr per sqrt(bar)
    double opening = pipe.getChokeOpening() / 100.0; // fraction

    // Effective Kv adjusted for opening
    double kvEff = kv * opening;
    if (kvEff < 1e-10) {
      return 1e7; // Nearly closed valve - very high resistance
    }

    // Convert Kv to SI: Q[m3/s] = Kv[m3/hr/sqrt(bar)] * sqrt(dP[bar]) / 3600
    // => Q[m3/s] = (Kv/3600) * sqrt(dP[Pa]/1e5)
    // => Q[kg/s] = density * (Kv/3600) * sqrt(dP[Pa]/1e5)
    // => dP[Pa] = 1e5 * (Q[kg/s] * 3600 / (density * Kv))^2
    double qVolM3s = flowKgs / density; // m3/s
    double qVolM3hr = qVolM3s * 3600.0; // m3/hr
    double dP = 1e5 * Math.pow(qVolM3hr / kvEff, 2); // Pa

    // Check for critical flow
    NetworkNode fromNode = nodes.get(pipe.getFromNode());
    double upstreamP = fromNode.getPressure();
    double xt = pipe.getChokeCriticalPressureRatio();
    double maxDp = upstreamP * xt;
    if (dP > maxDp) {
      dP = maxDp; // Choked flow - limit pressure drop
    }

    pipe.setVelocity(qVolM3s / (Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0));
    pipe.setFlowRegime(dP >= maxDp ? "Choked" : "Subcritical");
    return Math.signum(pipe.getFlowRate()) * dP;
  }

  /**
   * Calculate head loss for wellbore tubing (VLP) element.
   *
   * <p>
   * Simplified multiphase vertical lift model with gravity and friction components: dP = dP_gravity
   * + dP_friction = rho * g * L * sin(theta) + f * (L/D) * (rho * v^2 / 2)
   * </p>
   *
   * @param pipe the tubing element
   * @param fluid fluid properties
   * @return head loss in Pa (positive = pressure drop bottom-to-top)
   */
  private double calculateHeadLossTubing(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    double density = fluid.getDensity("kg/m3");
    double viscosity = fluid.getViscosity("kg/msec");

    // Gravity component
    double sinTheta = Math.sin(Math.toRadians(pipe.getTubingInclination()));
    double gravityLoss = density * 9.81 * pipe.getLength() * sinTheta;

    if (flowKgs < 1e-10) {
      return gravityLoss; // Hydrostatic head only
    }

    // Friction component (using Darcy-Weisbach)
    double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
    double velocity = flowKgs / (density * area);
    double reynolds = density * velocity * pipe.getDiameter() / viscosity;

    double relRoughness = pipe.getRoughness() / pipe.getDiameter();
    double ff;
    if (reynolds < 2300) {
      ff = 64.0 / Math.max(reynolds, 1.0);
    } else {
      double e = relRoughness / 3.7;
      double t = 5.74 / Math.pow(reynolds, 0.9);
      ff = 0.25 / Math.pow(Math.log10(e + t), 2);
    }

    double frictionLoss =
        ff * (pipe.getLength() / pipe.getDiameter()) * (density * velocity * velocity / 2.0);

    pipe.setVelocity(velocity);
    pipe.setReynoldsNumber(reynolds);
    pipe.setFrictionFactor(ff);
    pipe.setFlowRegime("Tubing-" + (reynolds < 2300 ? "Laminar" : "Turbulent"));

    double totalLoss = gravityLoss + frictionLoss;
    return Math.signum(pipe.getFlowRate()) * totalLoss;
  }

  /**
   * Calculate head loss for a multiphase pipe using simplified Beggs-Brill approach.
   *
   * <p>
   * When the fluid template has multiple phases, this uses a mixture density and viscosity for the
   * pressure drop calculation. For single-phase conditions, it degenerates to Darcy-Weisbach.
   * Elevation is incorporated via sin(angle) where angle is calculated from pipe-level elevation
   * difference.
   * </p>
   *
   * @param pipe the multiphase pipe element
   * @param fluid fluid properties
   * @return head loss in Pa
   */
  private double calculateHeadLossMultiphase(NetworkPipe pipe, SystemInterface fluid) {
    // For the network solver, use a simplified approach based on mixture properties
    // The Darcy-Weisbach calculation with mixture density/viscosity is reasonable
    // for single-phase-dominant flow and provides adequate Jacobian for NR convergence
    return calculateHeadLossDarcyWeisbach(pipe, fluid);
  }

  /**
   * Calculate derivative of head loss with respect to flow rate for any element type.
   *
   * @param pipe the network element
   * @param fluid fluid properties
   * @return dh/dQ in Pa/(kg/s)
   */
  private double calculateHeadLossDerivative(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    if (flowKgs < 1e-10) {
      flowKgs = 1e-10;
    }

    switch (pipe.getElementType()) {
      case WELL_IPR:
        return calculateHeadLossDerivativeIPR(pipe);
      case CHOKE:
        return calculateHeadLossDerivativeChoke(pipe, fluid);
      default:
        // For pipes, tubing, and multiphase: h ~ Q^2, so dh/dQ = 2h/Q
        double headLoss = Math.abs(calculateHeadLoss(pipe, fluid));
        return 2.0 * headLoss / flowKgs;
    }
  }

  /**
   * Calculate dh/dQ for IPR element.
   *
   * @param pipe the IPR element
   * @return dh/dQ in Pa/(kg/s)
   */
  private double calculateHeadLossDerivativeIPR(NetworkPipe pipe) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    if (flowKgs < 1e-10) {
      flowKgs = 1e-10;
    }
    double pRes = pipe.getReservoirPressure();

    switch (pipe.getIprType()) {
      case VOGEL:
        // d(dP)/dQ from Vogel: numerical differentiation
        double qmax = pipe.getVogelQmax();
        double qRatio = Math.min(flowKgs / qmax, 0.999);
        // Pwf/Pr = (-0.2 + sqrt(0.04 + 3.2*(1-q/qmax))) / 1.6
        double disc = 0.04 + 3.2 * (1.0 - qRatio);
        // d(Pwf/Pr)/d(q/qmax) = -3.2 / (2*1.6*sqrt(disc)) = -1.0/sqrt(disc)
        return pRes / (qmax * Math.sqrt(disc));

      case FETKOVICH:
        double cCoeff = pipe.getFetkovichC();
        double nExp = pipe.getFetkovichN();
        if (cCoeff < 1e-20) {
          return 1e10;
        }
        // Pwf^2 = Pr^2 - (q/C)^(1/n)
        // dPwf/dq = -(1/(2*n*C)) * (q/C)^(1/n - 1) / sqrt(Pr^2 - (q/C)^(1/n))
        double p2diff = Math.pow(flowKgs / cCoeff, 1.0 / nExp);
        double pwf2 = pRes * pRes - p2diff;
        if (pwf2 < pRes * pRes * 0.01) {
          // Near or beyond AOF: Pwf ~ 0, derivative singular
          // Use secant approximation: d(dP)/dQ ≈ 2*dP/Q
          // dP is approximately pRes when near AOF
          double dPApprox = pRes - Math.sqrt(Math.max(pwf2, 0.0));
          return 2.0 * dPApprox / flowKgs;
        }
        double pwf = Math.sqrt(pwf2);
        double dPwf_dq =
            (1.0 / (2.0 * nExp * cCoeff)) * Math.pow(flowKgs / cCoeff, 1.0 / nExp - 1.0) / pwf;
        return dPwf_dq;

      case PRODUCTIVITY_INDEX:
      default:
        if (pipe.isGasIPR()) {
          // dP = Pr - sqrt(Pr^2 - q/PI)
          double pi = Math.max(pipe.getProductivityIndex(), 1e-20);
          double pwf2Gas = pRes * pRes - flowKgs / pi;
          if (pwf2Gas < pRes * pRes * 0.01) {
            // Near AOF: use secant approximation
            double dPApproxGas = pRes - Math.sqrt(Math.max(pwf2Gas, 0.0));
            return 2.0 * dPApproxGas / flowKgs;
          }
          return 1.0 / (2.0 * pi * Math.sqrt(pwf2Gas));
        } else {
          return 1.0 / Math.max(pipe.getProductivityIndex(), 1e-20);
        }
    }
  }

  /**
   * Calculate dh/dQ for choke element.
   *
   * @param pipe the choke element
   * @param fluid fluid properties
   * @return dh/dQ in Pa/(kg/s)
   */
  private double calculateHeadLossDerivativeChoke(NetworkPipe pipe, SystemInterface fluid) {
    double flowKgs = Math.abs(pipe.getFlowRate());
    if (flowKgs < 1e-10) {
      flowKgs = 1e-10;
    }
    double density = fluid.getDensity("kg/m3");
    double kvEff = pipe.getChokeKv() * pipe.getChokeOpening() / 100.0;
    if (kvEff < 1e-10) {
      return 1e10; // Closed valve
    }

    // Subcritical derivative: dP = 1e5 * (Q*3600/(rho*Kv))^2
    // d(dP)/dQ = 2 * 1e5 * Q * 3600^2 / (rho^2 * Kv^2)
    double subcriticalDeriv =
        2.0 * 1e5 * flowKgs * 3600.0 * 3600.0 / (density * density * kvEff * kvEff);

    // Check if flow is in critical (choked) regime
    // In critical flow, dP is capped at upstream_P * xt, independent of Q
    // The derivative w.r.t. Q is effectively zero (dP depends on P_upstream, not Q)
    // Use a small regularized value to avoid singularity
    NetworkNode fromNode = nodes.get(pipe.getFromNode());
    double upstreamP = fromNode.getPressure();
    double xt = pipe.getChokeCriticalPressureRatio();
    double maxDp = upstreamP * xt;

    double qVolM3hr = (flowKgs / density) * 3600.0;
    double subcriticalDp = 1e5 * Math.pow(qVolM3hr / kvEff, 2);

    if (subcriticalDp > maxDp) {
      // Choked flow: return small regularized derivative
      // Use maxDp / Q as a softer estimate (pressure drop ~ constant)
      return maxDp / flowKgs;
    }

    return subcriticalDeriv;
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
    // Pull conditions from connected feed streams
    applyFeedStreams();

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

    // Populate outlet streams at sink nodes for downstream equipment
    updateOutletStreams();
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
      fluid.initProperties();
    } catch (Exception ex) {
      logger.warn("TP flash failed in property update: " + ex.getMessage());
      return;
    }

    for (NetworkPipe pipe : pipes.values()) {
      double headLoss = calculateHeadLoss(pipe, fluid);
      pipe.setHeadLoss(headLoss);
    }
  }

  /**
   * Initialize pressures for free (non-fixed) nodes using BFS propagation from fixed-pressure
   * nodes. This produces a reasonable pressure gradient across the network, especially important
   * for production networks where junctions sit between high-pressure sources and low-pressure
   * sinks.
   *
   * @param freeNodeList names of free nodes needing pressure initialization
   */
  private void initializeFreeNodePressures(List<String> freeNodeList) {
    // Find pressure range from fixed nodes
    double maxP = Double.MIN_VALUE;
    double minP = Double.MAX_VALUE;
    for (NetworkNode node : nodes.values()) {
      if (node.isPressureFixed()) {
        maxP = Math.max(maxP, node.getPressure());
        minP = Math.min(minP, node.getPressure());
      }
    }
    if (maxP <= minP) {
      // Fallback: all fixed pressures are the same
      for (String name : freeNodeList) {
        NetworkNode node = nodes.get(name);
        if (node.getPressure() < 1.0) {
          node.setPressure(maxP * 0.9);
        }
      }
      return;
    }

    // BFS from fixed-pressure nodes: assign intermediate pressures based on
    // graph distance (hop count) from sources and sinks.
    Map<String, Integer> distFromSource = new HashMap<>();
    Map<String, Integer> distFromSink = new HashMap<>();

    // BFS from sources (high pressure)
    java.util.Queue<String> queue = new java.util.LinkedList<>();
    for (NetworkNode node : nodes.values()) {
      if (node.getType() == NodeType.SOURCE) {
        queue.add(node.getName());
        distFromSource.put(node.getName(), 0);
      }
    }
    bfsDistances(queue, distFromSource);

    // BFS from sinks (low pressure)
    queue.clear();
    for (NetworkNode node : nodes.values()) {
      if (node.getType() == NodeType.SINK) {
        queue.add(node.getName());
        distFromSink.put(node.getName(), 0);
      }
    }
    bfsDistances(queue, distFromSink);

    // Interpolate pressure for free nodes based on relative distance
    for (String name : freeNodeList) {
      NetworkNode node = nodes.get(name);
      if (node.getPressure() >= 1.0) {
        continue; // Already initialized
      }

      int dSrc = distFromSource.getOrDefault(name, 1);
      int dSnk = distFromSink.getOrDefault(name, 1);
      int total = dSrc + dSnk;
      if (total == 0) {
        total = 1;
      }

      // Linear interpolation: closer to source -> higher pressure
      double fraction = (double) dSrc / total; // 0 = at source, 1 = at sink
      double interpP = maxP - fraction * (maxP - minP);

      // Add small perturbation to avoid identical pressures at same depth
      interpP *= (0.98 + 0.02 * dSrc);
      node.setPressure(interpP);
    }
  }

  /**
   * BFS distance computation from seed nodes through pipe connections.
   *
   * @param queue initial queue of seed nodes
   * @param dist map to populate with distances
   */
  private void bfsDistances(java.util.Queue<String> queue, Map<String, Integer> dist) {
    while (!queue.isEmpty()) {
      String current = queue.poll();
      int d = dist.get(current);
      for (NetworkPipe pipe : pipes.values()) {
        String neighbor = null;
        if (pipe.getFromNode().equals(current)) {
          neighbor = pipe.getToNode();
        } else if (pipe.getToNode().equals(current)) {
          neighbor = pipe.getFromNode();
        }
        if (neighbor != null && !dist.containsKey(neighbor)) {
          dist.put(neighbor, d + 1);
          queue.add(neighbor);
        }
      }
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

    int np = pipeList.size(); // number of pipes/elements
    int nn = freeNodeList.size(); // number of free nodes (unknown pressures)

    // When nn == 0 (all pressures fixed), the Schur complement is empty and
    // the back-substitution dQ = D^{-1}*(-f1) determines flows directly from
    // pressure differences. This is the "pressure-pressure" deliverability mode.

    // Initialize pressures for free nodes using propagation from fixed-pressure nodes.
    // For production networks, this creates a reasonable pressure gradient from
    // sources (high P) through junctions to sinks (low P).
    initializeFreeNodePressures(freeNodeList);

    iterationCount = 0;
    converged = false;
    double density = fluid.getDensity("kg/m3");

    while (iterationCount < maxIterations && !converged) {
      iterationCount++;

      // --- Step 1: Calculate h(Q) and dh/dQ for each element ---
      // For standard pipes: h = r*Q*|Q| and dh/dQ = 2*r*|Q|
      // For IPR/choke/tubing: use element-specific models via calculateHeadLoss
      double[] pipeFlowsSI = new double[np]; // kg/s
      double[] elementHeadLoss = new double[np]; // Pa (signed)
      double[] elementDerivative = new double[np]; // dh/d|Q| in Pa/(kg/s)

      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        pipeFlowsSI[i] = pipe.getFlowRate(); // Already in kg/s

        // Calculate head loss using the element-specific model
        elementHeadLoss[i] = calculateHeadLoss(pipe, fluid);
        elementDerivative[i] = calculateHeadLossDerivative(pipe, fluid);
      }

      // --- Step 2: Build and solve the linearized system ---
      // Using Schur complement with generalized A11 = diag(dh/dQ):
      // A11*dQ + A12*dH = -f1 and A21*dQ = -f2
      // Schur: (A21 * A11^{-1} * A12) * dH = -f2 + A21 * A11^{-1} * f1

      // Build A11 diagonal (inverse): 1/derivative for each element
      double[] a11inv = new double[np];
      for (int i = 0; i < np; i++) {
        double deriv = elementDerivative[i];
        if (deriv < 1e-10) {
          deriv = 1e-10; // Avoid singularity
        }
        a11inv[i] = 1.0 / deriv;
      }

      // Build head residuals: f1_i = h_i(Q_i) - (P_from - P_to) + rho*g*(z_to - z_from)
      // Note: calculateHeadLoss already includes elevation for pipes/tubing.
      // For the NR system we need: h_element(Q) = P_from - P_to (at solution)
      // Residual: f1_i = h_element(Q) - (P_from - P_to)
      double[] f1 = new double[np];
      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        double pFromPa = nodes.get(pipe.getFromNode()).getPressure();
        double pToPa = nodes.get(pipe.getToNode()).getPressure();

        // For standard pipes: headLoss already includes sign and elevation
        // Residual = headLoss - (P_from - P_to). When converged, headLoss = P_from - P_to
        if (pipe.getElementType() == NetworkElementType.PIPE
            || pipe.getElementType() == NetworkElementType.MULTIPHASE_PIPE) {
          // calculateHeadLoss returns signed value with elevation included
          double elevDiff = nodes.get(pipe.getToNode()).getElevation()
              - nodes.get(pipe.getFromNode()).getElevation();
          double absFlow = Math.abs(pipeFlowsSI[i]);
          if (absFlow < 1e-10) {
            absFlow = 1e-10;
          }
          // Resistance formulation: r*Q*|Q| + rho*g*dz = P_from - P_to
          double area = Math.PI * pipe.getDiameter() * pipe.getDiameter() / 4.0;
          double vel = absFlow / (density * area);
          double viscosity = fluid.getViscosity("kg/msec");
          double reynolds = density * vel * pipe.getDiameter() / viscosity;
          double ff;
          if (reynolds < 2300) {
            ff = 64.0 / Math.max(reynolds, 1.0);
          } else {
            double e = pipe.getRoughness() / pipe.getDiameter();
            ff = 0.25 / Math.pow(Math.log10(e / 3.7 + 5.74 / Math.pow(reynolds, 0.9)), 2);
          }
          double resistance =
              ff * pipe.getLength() / (pipe.getDiameter() * 2.0 * density * area * area);
          f1[i] = resistance * pipeFlowsSI[i] * Math.abs(pipeFlowsSI[i]) - (pFromPa - pToPa)
              + density * 9.81 * elevDiff;
        } else {
          // For IPR, choke, tubing: elementHeadLoss is the signed ΔP(Q)
          // Residual = |elementHeadLoss| - (P_from - P_to) for positive flow dir
          f1[i] = elementHeadLoss[i] - (pFromPa - pToPa);
        }
      }

      // Build node flow residuals: f2_j = demand_j - sum(Q_i * sign_ij)
      double[] f2 = new double[nn];
      for (int j = 0; j < nn; j++) {
        String nodeName = freeNodeList.get(j);
        NetworkNode node = nodes.get(nodeName);
        double netFlow = node.getDemand();
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
      double[] rhs = new double[nn];

      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        int jFrom = freeNodeList.indexOf(pipe.getFromNode());
        int jTo = freeNodeList.indexOf(pipe.getToNode());

        double val = a11inv[i];

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

      for (int j = 0; j < nn; j++) {
        rhs[j] = -f2[j] + rhs[j];
      }

      double[] dH = solveLinearSystem(schur, rhs, nn);

      // --- Step 3: Adaptive relaxation ---
      // Use under-relaxation when residuals are large (early iterations) to prevent
      // overshooting, especially for mixed element networks (IPR + choke + pipe).
      double stepRelax = relaxationFactor;
      if (iterationCount <= 5 && maxResidual > 1e6) {
        stepRelax = Math.min(relaxationFactor, 0.3);
      } else if (iterationCount <= 15 && maxResidual > 1e4) {
        stepRelax = Math.min(relaxationFactor, 0.6);
      }

      // --- Step 4: Update pressures ---
      for (int j = 0; j < nn; j++) {
        NetworkNode node = nodes.get(freeNodeList.get(j));
        double newP = node.getPressure() + stepRelax * dH[j];
        // Prevent negative pressures
        if (newP < 1e3) {
          newP = 1e3; // Minimum 0.01 bar
        }
        node.setPressure(newP);
      }

      // --- Step 5: Back-substitute for new flows ---
      for (int i = 0; i < np; i++) {
        NetworkPipe pipe = pipes.get(pipeList.get(i));
        int jFrom = freeNodeList.indexOf(pipe.getFromNode());
        int jTo = freeNodeList.indexOf(pipe.getToNode());

        double atDh = 0.0;
        if (jFrom >= 0) {
          atDh += dH[jFrom];
        }
        if (jTo >= 0) {
          atDh -= dH[jTo];
        }

        double dQ = a11inv[i] * (-f1[i] + atDh);
        double newFlowKgs = pipeFlowsSI[i] + stepRelax * dQ;
        pipe.setFlowRate(newFlowKgs);
      }

      // --- Step 5: Check convergence ---
      maxResidual = 0.0;
      for (int i = 0; i < np; i++) {
        maxResidual = Math.max(maxResidual, Math.abs(f1[i]));
      }
      for (int j = 0; j < nn; j++) {
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
   * Apply conditions from connected feed streams to their corresponding source nodes.
   *
   * <p>
   * For each registered feed stream, updates the source node's pressure and supply flow rate from
   * the stream's current state. Also updates the fluid template from the first feed stream if not
   * already set.
   * </p>
   */
  private void applyFeedStreams() {
    for (Map.Entry<String, StreamInterface> entry : feedStreams.entrySet()) {
      String nodeName = entry.getKey();
      StreamInterface stream = entry.getValue();
      if (stream == null) {
        continue;
      }

      // Handle the default single-feed case: bind to first source node
      if ("__default__".equals(nodeName)) {
        for (NetworkNode node : nodes.values()) {
          if (node.getType() == NodeType.SOURCE) {
            nodeName = node.getName();
            break;
          }
        }
        if ("__default__".equals(nodeName)) {
          continue; // No source nodes defined yet
        }
      }

      NetworkNode node = nodes.get(nodeName);
      if (node == null || node.getType() != NodeType.SOURCE) {
        continue;
      }

      // Update source node from stream state
      double streamPressurePa = stream.getPressure("Pa");
      if (streamPressurePa > 0) {
        node.setPressure(streamPressurePa);
      }

      double streamFlowKgs = stream.getFlowRate("kg/sec");
      if (streamFlowKgs > 0) {
        node.setDemand(-streamFlowKgs); // Negative = supply
      }

      node.setTemperature(stream.getTemperature("K"));

      // Update fluid template from first feed stream
      if (fluidTemplate == null && stream.getFluid() != null) {
        fluidTemplate = stream.getFluid().clone();
      }
    }
  }

  /**
   * Create or update outlet streams at sink nodes with the solved network state.
   *
   * <p>
   * For each sink node, creates a {@link Stream} containing the fluid at the solved pressure,
   * temperature, and flow rate. These streams can be connected to downstream process equipment in a
   * {@link neqsim.process.processmodel.ProcessSystem}.
   * </p>
   */
  private void updateOutletStreams() {
    if (fluidTemplate == null) {
      return;
    }

    for (NetworkNode node : nodes.values()) {
      if (node.getType() != NodeType.SINK) {
        continue;
      }

      // Calculate total flow into this sink node from all connected pipes
      double totalFlowKgs = 0.0;
      for (NetworkPipe pipe : pipes.values()) {
        if (pipe.getToNode().equals(node.getName())) {
          totalFlowKgs += Math.abs(pipe.getFlowRate());
        }
        if (pipe.getFromNode().equals(node.getName())) {
          totalFlowKgs -= Math.abs(pipe.getFlowRate());
        }
      }
      if (totalFlowKgs < 0) {
        totalFlowKgs = 0.0;
      }

      try {
        // Create or update outlet stream
        StreamInterface outStream = outletStreams.get(node.getName());
        if (outStream == null) {
          SystemInterface outFluid = fluidTemplate.clone();
          outFluid.setPressure(node.getPressure() / 1e5, "bara");
          outFluid.setTemperature(node.getTemperature(), "K");
          outStream = new Stream(node.getName() + "_outlet", outFluid);
          outStream.setFlowRate(totalFlowKgs * 3600.0, "kg/hr");
          outStream.run();
          outletStreams.put(node.getName(), outStream);
        } else {
          outStream.getFluid().setPressure(node.getPressure() / 1e5, "bara");
          outStream.getFluid().setTemperature(node.getTemperature(), "K");
          outStream.setFlowRate(totalFlowKgs * 3600.0, "kg/hr");
          outStream.run();
        }

        // Also store on the node for direct access
        node.setStream(outStream);
      } catch (Exception ex) {
        logger.warn(
            "Failed to create outlet stream for node " + node.getName() + ": " + ex.getMessage());
      }
    }

    // Also update streams on source nodes (useful for IPR wells)
    for (NetworkNode node : nodes.values()) {
      if (node.getType() != NodeType.SOURCE) {
        continue;
      }
      double totalFlowKgs = 0.0;
      for (NetworkPipe pipe : pipes.values()) {
        if (pipe.getFromNode().equals(node.getName())) {
          totalFlowKgs += Math.abs(pipe.getFlowRate());
        }
      }
      if (totalFlowKgs > 0) {
        try {
          StreamInterface srcStream = node.getStream();
          if (srcStream == null) {
            SystemInterface srcFluid = fluidTemplate.clone();
            srcFluid.setPressure(node.getPressure() / 1e5, "bara");
            srcFluid.setTemperature(node.getTemperature(), "K");
            srcStream = new Stream(node.getName() + "_stream", srcFluid);
            srcStream.setFlowRate(totalFlowKgs * 3600.0, "kg/hr");
            srcStream.run();
            node.setStream(srcStream);
          } else {
            srcStream.getFluid().setPressure(node.getPressure() / 1e5, "bara");
            srcStream.getFluid().setTemperature(node.getTemperature(), "K");
            srcStream.setFlowRate(totalFlowKgs * 3600.0, "kg/hr");
            srcStream.run();
          }
        } catch (Exception ex) {
          logger.warn(
              "Failed to create source stream for node " + node.getName() + ": " + ex.getMessage());
        }
      }
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
