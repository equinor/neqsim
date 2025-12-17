package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.fluidmechanics.flowsolver.AdvectionScheme;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.OnePhasePipeLine;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Network of pipelines connected through manifolds using compositional PipeFlowSystem.
 *
 * &lt;p&gt; This class models pipeline networks where multiple pipelines converge to manifolds
 * (mixers) and pipelines depart from manifolds. Unlike {@link WellFlowlineNetwork} which uses the
 * simpler Beggs-Brill correlation, this class uses the full {@link OnePhasePipeLine} with TDMA
 * solvers supporting: &lt;/p&gt; &lt;ul&gt; &lt;li&gt;Compositional tracking through the
 * network&lt;/li&gt; &lt;li&gt;Steady-state solutions&lt;/li&gt; &lt;li&gt;Transient/dynamic
 * simulations&lt;/li&gt; &lt;li&gt;Energy balance and heat transfer&lt;/li&gt; &lt;/ul&gt;
 *
 * &lt;h2&gt;Architecture&lt;/h2&gt; &lt;p&gt; The network is modeled as a directed graph where:
 * &lt;/p&gt; &lt;ul&gt; &lt;li&gt;Nodes are manifolds (implemented as {@link Mixer})&lt;/li&gt;
 * &lt;li&gt;Edges are pipelines (implemented as {@link OnePhasePipeLine})&lt;/li&gt; &lt;li&gt;Feed
 * streams connect to source nodes&lt;/li&gt; &lt;li&gt;The final node provides the network
 * outlet&lt;/li&gt; &lt;/ul&gt;
 *
 * &lt;h2&gt;Example Usage&lt;/h2&gt;
 * 
 * &lt;pre&gt;{@code
 * // Create feeds
 * Stream feed1 = new Stream("feed1", gas1);
 * feed1.setFlowRate(5.0, "MSm3/day");
 * Stream feed2 = new Stream("feed2", gas2);
 * feed2.setFlowRate(3.0, "MSm3/day");
 *
 * // Create network
 * PipeFlowNetwork network = new PipeFlowNetwork("gathering system");
 *
 * // Create manifolds
 * String manifoldA = network.createManifold("manifold A");
 * String endManifold = network.createManifold("end manifold");
 *
 * // Add inlet pipelines to manifold A
 * network.addInletPipeline("pipe1", feed1, manifoldA, 5000.0, 0.3, 50);
 * network.addInletPipeline("pipe2", feed2, manifoldA, 4500.0, 0.25, 45);
 *
 * // Connect manifold A to end manifold with export pipeline
 * network.connectManifolds(manifoldA, endManifold, "export", 15000.0, 0.5, 100);
 *
 * // Run steady-state
 * network.run();
 *
 * // Access results
 * StreamInterface outlet = network.getOutletStream();
 * }&lt;/pre&gt;
 *
 * @author Even Solbraa
 * @version 1.0
 * @see OnePhasePipeLine
 * @see Mixer
 */
public class PipeFlowNetwork extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;

  /**
   * Represents a pipeline segment in the network.
   */
  public static class PipelineSegment {
    private final String name;
    private final OnePhasePipeLine pipeline;
    private final String fromManifold; // null for inlet pipes
    private final String toManifold;

    PipelineSegment(String name, OnePhasePipeLine pipeline, String fromManifold,
        String toManifold) {
      this.name = name;
      this.pipeline = pipeline;
      this.fromManifold = fromManifold;
      this.toManifold = toManifold;
    }

    public String getName() {
      return name;
    }

    public OnePhasePipeLine getPipeline() {
      return pipeline;
    }

    public String getFromManifold() {
      return fromManifold;
    }

    public String getToManifold() {
      return toManifold;
    }

    public boolean isInletPipeline() {
      return fromManifold == null;
    }
  }

  /**
   * Represents a manifold node in the network.
   */
  public static class ManifoldNode {
    private final String name;
    private final Mixer mixer;
    private final List<PipelineSegment> inboundPipelines = new ArrayList<>();
    private PipelineSegment outboundPipeline;

    ManifoldNode(String name) {
      this.name = name;
      this.mixer = new Mixer(name + " mixer");
    }

    public String getName() {
      return name;
    }

    public Mixer getMixer() {
      return mixer;
    }

    public List<PipelineSegment> getInboundPipelines() {
      return Collections.unmodifiableList(inboundPipelines);
    }

    public PipelineSegment getOutboundPipeline() {
      return outboundPipeline;
    }

    void addInboundPipeline(PipelineSegment segment) {
      inboundPipelines.add(segment);
      mixer.addStream(segment.getPipeline().getOutletStream());
    }

    void setOutboundPipeline(PipelineSegment segment) {
      this.outboundPipeline = segment;
    }

    boolean isTerminal() {
      return outboundPipeline == null;
    }
  }

  private final Map<String, ManifoldNode> manifolds = new HashMap<>();
  private final List<PipelineSegment> allPipelines = new ArrayList<>();
  private final List<StreamInterface> feedStreams = new ArrayList<>();
  private String terminalManifoldName;
  private boolean compositionalTracking = true;
  private AdvectionScheme advectionScheme = AdvectionScheme.TVD_VAN_LEER;
  private double simulationTime = 0.0;
  private boolean initialized = false;

  // Default pipe geometry parameters
  private double defaultWallRoughness = 1e-5;
  private double[] defaultOuterTemperature = {278.0, 278.0};
  private double[] defaultOuterHeatTransfer = {5.0, 5.0};
  private double[] defaultWallHeatTransfer = {15.0, 15.0};

  /**
   * Create a new pipeline flow network.
   *
   * @param name network name
   */
  public PipeFlowNetwork(String name) {
    super(name);
  }

  /**
   * Create a manifold node in the network.
   *
   * @param name manifold name
   * @return the manifold name (for use in connecting pipelines)
   */
  public String createManifold(String name) {
    if (manifolds.containsKey(name)) {
      throw new IllegalArgumentException("Manifold '" + name + "' already exists");
    }
    ManifoldNode node = new ManifoldNode(name);
    manifolds.put(name, node);
    terminalManifoldName = name; // Last created becomes terminal
    return name;
  }

  /**
   * Add an inlet pipeline from a feed stream to a manifold.
   *
   * @param pipeName pipeline name
   * @param feedStream feed stream (source)
   * @param toManifold target manifold name
   * @param length pipeline length in meters
   * @param diameter pipeline diameter in meters
   * @param numberOfNodes number of computational nodes
   * @return the created pipeline segment
   */
  public PipelineSegment addInletPipeline(String pipeName, StreamInterface feedStream,
      String toManifold, double length, double diameter, int numberOfNodes) {
    Objects.requireNonNull(feedStream, "feedStream cannot be null");
    Objects.requireNonNull(toManifold, "toManifold cannot be null");

    ManifoldNode manifold = manifolds.get(toManifold);
    if (manifold == null) {
      throw new IllegalArgumentException("Manifold '" + toManifold + "' not found");
    }

    feedStreams.add(feedStream);

    OnePhasePipeLine pipeline =
        createPipeline(pipeName, feedStream, length, diameter, numberOfNodes);

    PipelineSegment segment = new PipelineSegment(pipeName, pipeline, null, toManifold);
    manifold.addInboundPipeline(segment);
    allPipelines.add(segment);

    return segment;
  }

  /**
   * Connect two manifolds with a pipeline.
   *
   * @param fromManifold source manifold name
   * @param toManifold target manifold name
   * @param pipeName pipeline name
   * @param length pipeline length in meters
   * @param diameter pipeline diameter in meters
   * @param numberOfNodes number of computational nodes
   * @return the created pipeline segment
   */
  public PipelineSegment connectManifolds(String fromManifold, String toManifold, String pipeName,
      double length, double diameter, int numberOfNodes) {
    Objects.requireNonNull(fromManifold, "fromManifold cannot be null");
    Objects.requireNonNull(toManifold, "toManifold cannot be null");

    ManifoldNode sourceManifold = manifolds.get(fromManifold);
    ManifoldNode targetManifold = manifolds.get(toManifold);

    if (sourceManifold == null) {
      throw new IllegalArgumentException("Source manifold '" + fromManifold + "' not found");
    }
    if (targetManifold == null) {
      throw new IllegalArgumentException("Target manifold '" + toManifold + "' not found");
    }
    if (sourceManifold.getOutboundPipeline() != null) {
      throw new IllegalStateException(
          "Manifold '" + fromManifold + "' already has an outbound pipeline");
    }

    // Create pipeline with mixer outlet as inlet stream
    OnePhasePipeLine pipeline = createPipeline(pipeName,
        sourceManifold.getMixer().getOutletStream(), length, diameter, numberOfNodes);

    PipelineSegment segment = new PipelineSegment(pipeName, pipeline, fromManifold, toManifold);
    sourceManifold.setOutboundPipeline(segment);
    targetManifold.addInboundPipeline(segment);
    allPipelines.add(segment);

    return segment;
  }

  /**
   * Create and configure a pipeline.
   */
  private OnePhasePipeLine createPipeline(String name, StreamInterface inletStream, double length,
      double diameter, int numberOfNodes) {
    OnePhasePipeLine pipeline = new OnePhasePipeLine(name, inletStream);
    pipeline.setNumberOfLegs(1);
    pipeline.setNumberOfNodesInLeg(numberOfNodes);
    pipeline.setPipeDiameters(new double[] {diameter, diameter});
    pipeline.setLegPositions(new double[] {0.0, length});
    pipeline.setHeightProfile(new double[] {0.0, 0.0});
    pipeline.setPipeWallRoughness(new double[] {defaultWallRoughness, defaultWallRoughness});
    pipeline.setOuterTemperatures(defaultOuterTemperature);
    pipeline.setPipeOuterHeatTransferCoefficients(defaultOuterHeatTransfer);
    pipeline.setPipeWallHeatTransferCoefficients(defaultWallHeatTransfer);
    pipeline.setCompositionalTracking(compositionalTracking);
    pipeline.setAdvectionScheme(advectionScheme);
    return pipeline;
  }

  /**
   * Set the default wall roughness for new pipelines.
   *
   * @param roughness wall roughness in meters
   */
  public void setDefaultWallRoughness(double roughness) {
    this.defaultWallRoughness = roughness;
  }

  /**
   * Set the default outer temperature for new pipelines.
   *
   * @param temperature outer temperature in Kelvin
   */
  public void setDefaultOuterTemperature(double temperature) {
    this.defaultOuterTemperature = new double[] {temperature, temperature};
  }

  /**
   * Set default heat transfer coefficients for new pipelines.
   *
   * @param outerCoeff outer heat transfer coefficient (W/m2K)
   * @param wallCoeff wall heat transfer coefficient (W/m2K)
   */
  public void setDefaultHeatTransferCoefficients(double outerCoeff, double wallCoeff) {
    this.defaultOuterHeatTransfer = new double[] {outerCoeff, outerCoeff};
    this.defaultWallHeatTransfer = new double[] {wallCoeff, wallCoeff};
  }

  /**
   * Enable or disable compositional tracking.
   *
   * @param enable true to enable compositional tracking
   */
  public void setCompositionalTracking(boolean enable) {
    this.compositionalTracking = enable;
    for (PipelineSegment segment : allPipelines) {
      segment.getPipeline().setCompositionalTracking(enable);
    }
  }

  /**
   * Set the advection scheme for compositional tracking.
   *
   * @param scheme advection scheme
   */
  public void setAdvectionScheme(AdvectionScheme scheme) {
    this.advectionScheme = scheme;
    for (PipelineSegment segment : allPipelines) {
      segment.getPipeline().setAdvectionScheme(scheme);
    }
  }

  /**
   * Get all pipeline segments in the network.
   *
   * @return list of pipeline segments
   */
  public List<PipelineSegment> getPipelines() {
    return Collections.unmodifiableList(allPipelines);
  }

  /**
   * Get all manifold nodes in the network.
   *
   * @return map of manifold names to nodes
   */
  public Map<String, ManifoldNode> getManifolds() {
    return Collections.unmodifiableMap(manifolds);
  }

  /**
   * Get the terminal manifold (network outlet).
   *
   * @return the terminal manifold node
   */
  public ManifoldNode getTerminalManifold() {
    return manifolds.get(terminalManifoldName);
  }

  /**
   * Get the outlet stream from the network.
   *
   * @return the outlet stream
   */
  public StreamInterface getOutletStream() {
    ManifoldNode terminal = getTerminalManifold();
    if (terminal == null) {
      return null;
    }
    return terminal.getMixer().getOutletStream();
  }

  /**
   * Get the current simulation time.
   *
   * @return simulation time in seconds
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Reset simulation time to zero.
   */
  public void resetSimulationTime() {
    this.simulationTime = 0.0;
    for (PipelineSegment segment : allPipelines) {
      segment.getPipeline().resetSimulationTime();
    }
  }

  /**
   * Get execution order for manifolds (topological sort).
   */
  private List<ManifoldNode> getExecutionOrder() {
    List<ManifoldNode> order = new ArrayList<>();
    java.util.Set<String> visited = new java.util.HashSet<>();

    // Find manifolds with only inlet pipelines (sources)
    for (ManifoldNode manifold : manifolds.values()) {
      boolean hasOnlyInlets = true;
      for (PipelineSegment segment : manifold.getInboundPipelines()) {
        if (!segment.isInletPipeline()) {
          hasOnlyInlets = false;
          break;
        }
      }
      if (hasOnlyInlets && !manifold.getInboundPipelines().isEmpty()) {
        addToExecutionOrder(manifold, order, visited);
      }
    }

    // Add any remaining manifolds
    for (ManifoldNode manifold : manifolds.values()) {
      if (!visited.contains(manifold.getName())) {
        addToExecutionOrder(manifold, order, visited);
      }
    }

    return order;
  }

  private void addToExecutionOrder(ManifoldNode node, List<ManifoldNode> order,
      java.util.Set<String> visited) {
    if (visited.contains(node.getName())) {
      return;
    }
    visited.add(node.getName());
    order.add(node);

    if (node.getOutboundPipeline() != null) {
      ManifoldNode next = manifolds.get(node.getOutboundPipeline().getToManifold());
      if (next != null) {
        addToExecutionOrder(next, order, visited);
      }
    }
  }

  /**
   * Run steady-state simulation for the entire network.
   */
  @Override
  public void run(UUID id) {
    if (allPipelines.isEmpty()) {
      return;
    }

    // Run feed streams first
    for (StreamInterface feed : feedStreams) {
      feed.run(id);
    }

    // Process manifolds in execution order
    List<ManifoldNode> executionOrder = getExecutionOrder();

    for (ManifoldNode manifold : executionOrder) {
      // Run all inbound pipelines to this manifold
      for (PipelineSegment segment : manifold.getInboundPipelines()) {
        segment.getPipeline().run(id);
      }

      // Run the mixer
      manifold.getMixer().run(id);

      // Set up outbound pipeline inlet if exists
      if (manifold.getOutboundPipeline() != null) {
        manifold.getOutboundPipeline().getPipeline()
            .setInletStream(manifold.getMixer().getOutletStream());
      }
    }

    initialized = true;
    simulationTime = 0.0;
    setCalculationIdentifier(id);
  }

  /**
   * Run transient simulation for the specified time step.
   *
   * @param dt time step in seconds
   * @param id calculation identifier
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!initialized) {
      run(id);
    }

    // Run feed streams
    for (StreamInterface feed : feedStreams) {
      feed.run(id);
    }

    // Process manifolds in execution order
    List<ManifoldNode> executionOrder = getExecutionOrder();

    for (ManifoldNode manifold : executionOrder) {
      // Run transient for all inbound pipelines
      for (PipelineSegment segment : manifold.getInboundPipelines()) {
        segment.getPipeline().runTransient(dt, id);
      }

      // Run the mixer
      manifold.getMixer().run(id);

      // Update outbound pipeline inlet
      if (manifold.getOutboundPipeline() != null) {
        OnePhasePipeLine outPipe = manifold.getOutboundPipeline().getPipeline();
        outPipe.setInletStream(manifold.getMixer().getOutletStream());
      }
    }

    simulationTime += dt;
    setCalculationIdentifier(id);
  }

  /**
   * Get the pressure profile along a specific pipeline.
   *
   * @param pipeName pipeline name
   * @param unit pressure unit ("bara" or "Pa")
   * @return array of pressures at each node
   */
  public double[] getPressureProfile(String pipeName, String unit) {
    for (PipelineSegment segment : allPipelines) {
      if (segment.getName().equals(pipeName)) {
        return segment.getPipeline().getPressureProfile(unit);
      }
    }
    throw new IllegalArgumentException("Pipeline '" + pipeName + "' not found");
  }

  /**
   * Get the temperature profile along a specific pipeline.
   *
   * @param pipeName pipeline name
   * @param unit temperature unit ("K" or "C")
   * @return array of temperatures at each node
   */
  public double[] getTemperatureProfile(String pipeName, String unit) {
    for (PipelineSegment segment : allPipelines) {
      if (segment.getName().equals(pipeName)) {
        return segment.getPipeline().getTemperatureProfile(unit);
      }
    }
    throw new IllegalArgumentException("Pipeline '" + pipeName + "' not found");
  }

  /**
   * Get the composition profile along a specific pipeline.
   *
   * @param pipeName pipeline name
   * @param componentName component name
   * @return array of mass fractions at each node
   */
  public double[] getCompositionProfile(String pipeName, String componentName) {
    for (PipelineSegment segment : allPipelines) {
      if (segment.getName().equals(pipeName)) {
        return segment.getPipeline().getCompositionProfile(componentName);
      }
    }
    throw new IllegalArgumentException("Pipeline '" + pipeName + "' not found");
  }

  /**
   * Get the velocity profile along a specific pipeline.
   *
   * @param pipeName pipeline name
   * @return array of velocities (m/s) at each node
   */
  public double[] getVelocityProfile(String pipeName) {
    for (PipelineSegment segment : allPipelines) {
      if (segment.getName().equals(pipeName)) {
        return segment.getPipeline().getVelocityProfile();
      }
    }
    throw new IllegalArgumentException("Pipeline '" + pipeName + "' not found");
  }

  /**
   * Get total network pressure drop.
   *
   * @param unit pressure unit
   * @return total pressure drop
   */
  public double getTotalPressureDrop(String unit) {
    double totalDrop = 0.0;
    for (PipelineSegment segment : allPipelines) {
      double[] profile = segment.getPipeline().getPressureProfile(unit);
      if (profile.length >= 2) {
        totalDrop += profile[0] - profile[profile.length - 1];
      }
    }
    return totalDrop;
  }

  @Override
  public String toJson() {
    if (allPipelines.isEmpty()) {
      return null;
    }
    ManifoldNode terminal = getTerminalManifold();
    if (terminal != null) {
      return terminal.getMixer().toJson();
    }
    return null;
  }
}
