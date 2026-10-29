package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidComponentConservationReport;
import neqsim.process.equipment.pipeline.TwoFluidConservativeEnergyReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Phase-conservative transient hydraulic network of {@link TwoFluidPipe} branches.
 *
 * <p>
 * Every branch is advanced from the pressures at the beginning of the network step. Gas, oil, and water boundary
 * transfers are then accumulated at each compressible node, and all node pressures are advanced simultaneously.
 * Fixed-pressure nodes represent external reservoirs or sinks. This explicit storage-node coupling supports splits,
 * merges, manifolds, and injection branches without making branch execution order part of the pressure solution.
 * </p>
 *
 * <p>
 * This first network layer is intentionally storage based. Algebraic zero-volume junctions, global Newton pressure-flow
 * iteration, and branch subcycling are outside its current scope. Component and total-energy mixing are available as an
 * explicit opt-in on compressible nodes. Signed outlet fallback is supported only when the downstream node or pipe owns
 * an explicit thermodynamic donor.
 * </p>
 */
public final class TwoFluidPipeNetwork implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final int PHASE_COUNT = 3;

  private final String name;
  private final Map<String, NetworkNode> nodes = new LinkedHashMap<String, NetworkNode>();
  private final Map<String, NetworkBranch> branches = new LinkedHashMap<String, NetworkBranch>();
  private boolean initialized;
  private double simulationTimeSeconds;
  private BalanceReport lastBalanceReport;
  private ThermodynamicBalanceReport lastThermodynamicBalanceReport;

  /** Create an empty network. */
  public TwoFluidPipeNetwork(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Network name cannot be blank");
    }
    this.name = name;
  }

  /** Add a storage node whose pressure follows its compressible phase inventories. */
  public void addCompressibleNode(String nodeName, UpstreamCompressibleVolume volume) {
    requireUniqueNodeName(nodeName);
    if (volume == null) {
      throw new IllegalArgumentException("Compressible node volume cannot be null");
    }
    nodes.put(nodeName, NetworkNode.compressible(nodeName, volume));
  }

  /**
   * Add a storage node with conservative named-component and total-energy inventory.
   *
   * <p>
   * The existing {@link UpstreamCompressibleVolume} remains the hydraulic pressure and phase-mass owner. The supplied
   * fluid defines the component slate, composition and caloric state of the same total node mass. During a network
   * step, every outgoing branch sees one frozen start-of-step node state; all accepted branch transfers are then
   * applied simultaneously. This makes merge and split routing independent of branch insertion order.
   * </p>
   *
   * @param nodeName unique node name
   * @param volume existing hydraulic storage model
   * @param initialFluid initial node composition and caloric state
   */
  public void addConservativeCompressibleNode(String nodeName, UpstreamCompressibleVolume volume,
      SystemInterface initialFluid) {
    requireUniqueNodeName(nodeName);
    if (volume == null) {
      throw new IllegalArgumentException("Compressible node volume cannot be null");
    }
    if (initialFluid == null || initialFluid.getNumberOfComponents() == 0) {
      throw new IllegalArgumentException("Conservative node fluid must contain at least one component");
    }
    nodes.put(nodeName, NetworkNode.conservative(nodeName, volume, initialFluid));
  }

  /**
   * Define the thermodynamic state of mass entering a conservative storage node through its hydraulic source terms.
   *
   * <p>
   * The phase-resolved source rates remain owned by {@link UpstreamCompressibleVolume}. This contract supplies the
   * named-component composition and open-system specific enthalpy required to apply the same accepted source mass to
   * the node's component and total-energy inventories. The fluid is defensively copied and later mutations do not
   * change the source definition.
   * </p>
   *
   * @param nodeName existing conservative compressible node
   * @param sourceFluid source composition; its flow rate is ignored
   * @param specificEnthalpyJPerKg source specific enthalpy in J/kg
   */
  public void setConservativeNodeSourceThermodynamics(String nodeName, SystemInterface sourceFluid,
      double specificEnthalpyJPerKg) {
    NetworkNode node = requireNode(nodeName);
    if (node.fixedPressure || node.thermodynamicInventory == null) {
      throw new IllegalArgumentException("Node '" + nodeName + "' is not a conservative compressible node");
    }
    node.sourceThermodynamics = new NodeSourceThermodynamics(sourceFluid, specificEnthalpyJPerKg,
        node.thermodynamicInventory.componentNames);
  }

  /** Add an external fixed-pressure reservoir or sink node. */
  public void addFixedPressureNode(String nodeName, double pressurePa) {
    requireUniqueNodeName(nodeName);
    requirePositiveFinite(pressurePa, "Fixed-node pressure");
    nodes.put(nodeName, NetworkNode.fixed(nodeName, pressurePa));
  }

  /**
   * Add an external fixed-pressure node with an explicit thermodynamic boundary state.
   *
   * <p>
   * The fluid is defensively copied and supplies composition, density and enthalpy only when a connected pipe accepts
   * signed outlet fallback from this node. Pressure remains owned by {@code pressurePa}. Forward pipe outflow is
   * unaffected. Use {@link #setBranchOutletPhaseBackflowAllowed(String, boolean)} to opt a branch into signed fallback.
   * </p>
   *
   * @param nodeName unique node name
   * @param pressurePa fixed mechanical pressure in Pa
   * @param boundaryFluid thermodynamic state of material entering the network from this node
   */
  public void addFixedPressureNode(String nodeName, double pressurePa, SystemInterface boundaryFluid) {
    requireUniqueNodeName(nodeName);
    requirePositiveFinite(pressurePa, "Fixed-node pressure");
    if (boundaryFluid == null || boundaryFluid.getNumberOfComponents() == 0) {
      throw new IllegalArgumentException("Fixed-node boundary fluid must contain at least one component");
    }
    nodes.put(nodeName, NetworkNode.fixed(nodeName, pressurePa, boundaryFluid));
  }

  /** Add a directed two-fluid pipe branch between existing nodes. */
  public void addPipe(String branchName, String fromNodeName, String toNodeName, TwoFluidPipe pipe) {
    requireName(branchName, "Branch");
    if (branches.containsKey(branchName)) {
      throw new IllegalArgumentException("Branch '" + branchName + "' already exists");
    }
    NetworkNode fromNode = requireNode(fromNodeName);
    NetworkNode toNode = requireNode(toNodeName);
    if (fromNode == toNode) {
      throw new IllegalArgumentException("Branch '" + branchName + "' cannot connect a node to itself");
    }
    if (pipe == null) {
      throw new IllegalArgumentException("Network pipe cannot be null");
    }
    if (pipe.getUpstreamCompressibleVolume() != null) {
      throw new IllegalArgumentException(
          "Branch '" + branchName + "' already owns an upstream volume; network nodes must own boundary storage");
    }
    NetworkBranch branch = new NetworkBranch(branchName, fromNode, toNode, pipe);
    branches.put(branchName, branch);
    fromNode.outgoingCount++;
    toNode.incomingCount++;
    initialized = false;
  }

  /**
   * Enable or disable signed outlet fallback on one branch using its downstream node as thermodynamic owner.
   *
   * <p>
   * A conservative storage node publishes its frozen start-of-step inventory. A fixed-pressure node must have been
   * created with an explicit boundary fluid. The donor is installed before the pipe option is enabled, preserving the
   * pipe's fail-closed configuration contract.
   * </p>
   *
   * @param branchName existing branch name
   * @param allow true to permit signed outlet fallback
   */
  public void setBranchOutletPhaseBackflowAllowed(String branchName, boolean allow) {
    NetworkBranch branch = requireBranch(branchName);
    if (allow) {
      publishOutletBoundaryState(branch, true);
    }
    branch.pipe.setAllowOutletPhaseBackflow(allow);
  }

  /**
   * Initialize each branch from its configured stream and the downstream node pressure.
   *
   * <p>
   * The branch stream flow remains the steady initialization rate. Transient execution then switches both ends to node
   * pressures.
   * </p>
   */
  public void initialize(UUID id) {
    validateRunnableNetwork();
    for (NetworkBranch branch : branches.values()) {
      branch.pipe.setOutletPressure(branch.toNode.getPressurePa());
      publishOutletBoundaryState(branch, false);
      branch.pipe.run(id);
      if (hasConservativeNodes() && !branch.pipe.isUseConservativeEnergyRecovery()) {
        branch.pipe.prepareConservativeEnergyTransientFromSteadyState();
      }
      configureTransientPressures(branch);
    }
    if (hasConservativeNodes()) {
      validateConservativeNetwork();
      publishFrozenNodeStates();
    }
    initialized = true;
  }

  /** Advance every branch and all compressible nodes by one common reporting interval. */
  public void runTransient(double timeStepSeconds, UUID id) {
    requirePositiveFinite(timeStepSeconds, "Network time step");
    if (!initialized) {
      initialize(id);
    }

    double[] initialMassKg = new double[PHASE_COUNT];
    double[] finalMassKg = new double[PHASE_COUNT];
    double[] externalInletMassKg = new double[PHASE_COUNT];
    double[] externalOutletMassKg = new double[PHASE_COUNT];
    double[] sourceMassKg = new double[PHASE_COUNT];
    Map<String, double[]> nodeTransferKg = new LinkedHashMap<String, double[]>();

    boolean conservative = hasConservativeNodes();
    String[] componentNames = conservative ? conservativeComponentNames() : new String[0];
    int componentCount = componentNames.length;
    double[] initialComponentKg = new double[componentCount];
    double[] finalComponentKg = new double[componentCount];
    double[] externalInletComponentKg = new double[componentCount];
    double[] externalOutletComponentKg = new double[componentCount];
    double[] sourceComponentKg = new double[componentCount];
    double initialEnergyJ = 0.0;
    double finalEnergyJ = 0.0;
    double externalInletEnergyJ = 0.0;
    double externalOutletEnergyJ = 0.0;
    double sourceEnergyJ = 0.0;
    double gravitationalWorkJ = 0.0;
    double wallHeatJ = 0.0;
    Map<String, double[]> nodeComponentTransferKg = new LinkedHashMap<String, double[]>();
    Map<String, Double> nodeEnergyTransferJ = new LinkedHashMap<String, Double>();

    if (conservative) {
      validateConservativeNetwork();
      publishFrozenNodeStates();
    }

    for (NetworkNode node : nodes.values()) {
      nodeTransferKg.put(node.name, new double[PHASE_COUNT]);
      if (conservative && !node.fixedPressure) {
        nodeComponentTransferKg.put(node.name, new double[componentCount]);
        nodeEnergyTransferJ.put(node.name, 0.0);
        add(initialComponentKg, node.thermodynamicInventory.componentMassKg, 1.0);
        initialEnergyJ += node.thermodynamicInventory.internalEnergyJ;
        double nodeSourceMassKg = node.getTotalSourceMassFlowRateKgS() * timeStepSeconds;
        if (nodeSourceMassKg > 0.0) {
          double[] nodeSourceComponentKg = node.sourceThermodynamics.componentMasses(nodeSourceMassKg);
          add(nodeComponentTransferKg.get(node.name), nodeSourceComponentKg, 1.0);
          add(sourceComponentKg, nodeSourceComponentKg, 1.0);
          double nodeSourceEnergyJ = nodeSourceMassKg * node.sourceThermodynamics.specificEnthalpyJPerKg;
          nodeEnergyTransferJ.put(node.name, nodeSourceEnergyJ);
          sourceEnergyJ += nodeSourceEnergyJ;
        }
      }
      if (!node.fixedPressure) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          initialMassKg[phase] += node.volume.getPhaseMassKg(phase);
          sourceMassKg[phase] += node.volume.getSourceMassFlowRateKgS(phase) * timeStepSeconds;
        }
      }
    }

    for (NetworkBranch branch : branches.values()) {
      configureTransientPressures(branch);
      branch.pipe.runTransient(timeStepSeconds, id);
      TwoFluidMassBalanceReport report = branch.pipe.getLastMassBalanceReport();
      if (report == null) {
        throw new IllegalStateException("Branch '" + branch.name + "' returned no mass-balance report");
      }
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        Phase aggregation = phase(phase);
        initialMassKg[phase] += report.getInitialMassKg(aggregation);
        finalMassKg[phase] += report.getFinalMassKg(aggregation);
        sourceMassKg[phase] += report.getSourceMassKg(aggregation);
        double inletMass = report.getInletMassKg(aggregation);
        double outletMass = report.getOutletMassKg(aggregation);
        if (branch.fromNode.fixedPressure) {
          externalInletMassKg[phase] += inletMass;
        } else {
          nodeTransferKg.get(branch.fromNode.name)[phase] += inletMass;
        }
        if (branch.toNode.fixedPressure) {
          externalOutletMassKg[phase] += outletMass;
        } else {
          nodeTransferKg.get(branch.toNode.name)[phase] -= outletMass;
        }
      }
      if (conservative) {
        TwoFluidComponentConservationReport componentReport = branch.pipe.getLastComponentConservationReport();
        TwoFluidConservativeEnergyReport energyReport = branch.pipe.getLastConservativeEnergyReport();
        validateBranchThermodynamicReports(branch, componentNames, componentReport, energyReport);
        add(initialComponentKg, componentReport.getInitialInventoryKg(), 1.0);
        add(finalComponentKg, componentReport.getFinalInventoryKg(), 1.0);
        initialEnergyJ += energyReport.getInitialEnergyJ();
        finalEnergyJ += energyReport.getFinalEnergyJ();
        gravitationalWorkJ += energyReport.getGravitationalWorkJ();
        wallHeatJ += energyReport.getWallHeatToFluidJ();

        double[] inletComponentKg = componentReport.getInletBoundaryMassKg();
        double[] outletComponentKg = componentReport.getOutletBoundaryMassKg();
        if (branch.fromNode.fixedPressure) {
          add(externalInletComponentKg, inletComponentKg, 1.0);
          externalInletEnergyJ += energyReport.getInletAdvectedEnergyJ();
        } else {
          add(nodeComponentTransferKg.get(branch.fromNode.name), inletComponentKg, -1.0);
          nodeEnergyTransferJ.put(branch.fromNode.name,
              nodeEnergyTransferJ.get(branch.fromNode.name) - energyReport.getInletAdvectedEnergyJ());
        }
        if (branch.toNode.fixedPressure) {
          add(externalOutletComponentKg, outletComponentKg, 1.0);
          externalOutletEnergyJ += energyReport.getOutletAdvectedEnergyJ();
        } else {
          add(nodeComponentTransferKg.get(branch.toNode.name), outletComponentKg, 1.0);
          nodeEnergyTransferJ.put(branch.toNode.name,
              nodeEnergyTransferJ.get(branch.toNode.name) + energyReport.getOutletAdvectedEnergyJ());
        }
      }
    }

    for (NetworkNode node : nodes.values()) {
      if (!node.fixedPressure) {
        node.volume.advance(timeStepSeconds, nodeTransferKg.get(node.name));
        if (conservative) {
          node.thermodynamicInventory.advance(nodeComponentTransferKg.get(node.name),
              nodeEnergyTransferJ.get(node.name), node.getPressurePa());
          add(finalComponentKg, node.thermodynamicInventory.componentMassKg, 1.0);
          finalEnergyJ += node.thermodynamicInventory.internalEnergyJ;
        }
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          finalMassKg[phase] += node.volume.getPhaseMassKg(phase);
        }
      }
    }

    simulationTimeSeconds += timeStepSeconds;
    lastBalanceReport = new BalanceReport(timeStepSeconds, initialMassKg, finalMassKg, externalInletMassKg,
        externalOutletMassKg, sourceMassKg);
    lastThermodynamicBalanceReport = conservative
        ? new ThermodynamicBalanceReport(timeStepSeconds, componentNames, initialComponentKg, finalComponentKg,
            externalInletComponentKg, externalOutletComponentKg, sourceComponentKg, initialEnergyJ, finalEnergyJ,
            externalInletEnergyJ, externalOutletEnergyJ, sourceEnergyJ, gravitationalWorkJ, wallHeatJ)
        : null;
  }

  private boolean hasConservativeNodes() {
    for (NetworkNode node : nodes.values()) {
      if (node.thermodynamicInventory != null) {
        return true;
      }
    }
    return false;
  }

  private String[] conservativeComponentNames() {
    for (NetworkNode node : nodes.values()) {
      if (node.thermodynamicInventory != null) {
        return node.thermodynamicInventory.componentNames.clone();
      }
    }
    return new String[0];
  }

  private void validateConservativeNetwork() {
    String[] names = conservativeComponentNames();
    for (NetworkNode node : nodes.values()) {
      if (!node.fixedPressure) {
        if (node.thermodynamicInventory == null) {
          throw new IllegalStateException(
              "Conservative routing requires thermodynamic inventory on node '" + node.name + "'");
        }
        if (!Arrays.equals(names, node.thermodynamicInventory.componentNames)) {
          throw new IllegalStateException("Conservative node '" + node.name + "' has a different component slate");
        }
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          if (node.volume.getSourceMassFlowRateKgS(phase) != 0.0) {
            if (node.sourceThermodynamics == null) {
              throw new IllegalStateException("Conservative node '" + node.name
                  + "' has hydraulic source terms but no explicit source fluid and enthalpy");
            }
            if (!Arrays.equals(names, node.sourceThermodynamics.componentNames)) {
              throw new IllegalStateException(
                  "Conservative node '" + node.name + "' source has a different component slate");
            }
          }
        }
      }
    }
    for (NetworkBranch branch : branches.values()) {
      if (!Arrays.equals(names, branch.pipe.getInletStream().getFluid().getComponentNames())) {
        throw new IllegalStateException("Branch '" + branch.name + "' has a different component slate");
      }
      if (!branch.pipe.isComponentTransportEnabled() || !branch.pipe.isUseConservativeEnergyRecovery()) {
        throw new IllegalStateException(
            "Branch '" + branch.name + "' must use component transport and conservative energy recovery");
      }
      if (branch.pipe.isOutletPhaseBackflowAllowed() && branch.pipe.getOutletBoundaryFluid() == null) {
        throw new IllegalStateException("Branch '" + branch.name
            + "' allows outlet fallback but its downstream node owns no thermodynamic boundary fluid");
      }
      if (branch.pipe.isOutletPhaseBackflowAllowed()
          && !Arrays.equals(names, branch.pipe.getOutletBoundaryFluid().getComponentNames())) {
        throw new IllegalStateException(
            "Branch '" + branch.name + "' outlet thermodynamic boundary fluid has a different component slate");
      }
    }
  }

  private void publishFrozenNodeStates() {
    for (NetworkBranch branch : branches.values()) {
      if (!branch.fromNode.fixedPressure) {
        SystemInterface boundaryFluid = branch.fromNode.thermodynamicInventory.fluid.clone();
        boundaryFluid.setPressure(branch.fromNode.getPressurePa(), "Pa");
        branch.pipe.getInletStream().setFluid(boundaryFluid);
      }
      publishOutletBoundaryState(branch, branch.pipe.isOutletPhaseBackflowAllowed());
    }
  }

  private void publishOutletBoundaryState(NetworkBranch branch, boolean required) {
    SystemInterface boundaryFluid = branch.toNode.getBoundaryFluid();
    if (boundaryFluid == null) {
      if (required && branch.pipe.getOutletBoundaryFluid() == null) {
        throw new IllegalStateException("Branch '" + branch.name
            + "' requires an explicit downstream thermodynamic boundary fluid before outlet fallback is enabled");
      }
      return;
    }
    boundaryFluid.setPressure(branch.toNode.getPressurePa(), "Pa");
    new ThermodynamicOperations(boundaryFluid).TPflash();
    branch.pipe.setOutletBoundaryFluid(boundaryFluid);
  }

  private static void validateBranchThermodynamicReports(NetworkBranch branch, String[] componentNames,
      TwoFluidComponentConservationReport componentReport, TwoFluidConservativeEnergyReport energyReport) {
    if (componentReport == null || energyReport == null) {
      throw new IllegalStateException("Branch '" + branch.name + "' returned no conservative thermodynamic report");
    }
    if (!componentReport.isConverged()) {
      throw new IllegalStateException(
          "Branch '" + branch.name + "' component report did not converge: " + componentReport.getMessage());
    }
    if (!Arrays.equals(componentNames, componentReport.getComponentNames())) {
      throw new IllegalStateException("Branch '" + branch.name + "' reported a different component slate");
    }
    for (double massKg : componentReport.getInletBoundaryMassKg()) {
      if (!Double.isFinite(massKg)) {
        throw new IllegalStateException("Branch '" + branch.name + "' reported nonfinite inlet component transfer");
      }
    }
    for (double massKg : componentReport.getOutletBoundaryMassKg()) {
      if (!Double.isFinite(massKg)) {
        throw new IllegalStateException("Branch '" + branch.name + "' reported nonfinite outlet component transfer");
      }
    }
  }

  private static void add(double[] target, double[] values, double factor) {
    if (target.length != values.length) {
      throw new IllegalArgumentException("Array dimensions differ");
    }
    for (int index = 0; index < target.length; index++) {
      target[index] += factor * values[index];
    }
  }

  private void configureTransientPressures(NetworkBranch branch) {
    branch.pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    branch.pipe.setInletPressure(branch.fromNode.getPressurePa());
    branch.pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    branch.pipe.setOutletPressure(branch.toNode.getPressurePa());
  }

  private void validateRunnableNetwork() {
    if (nodes.size() < 2 || branches.isEmpty()) {
      throw new IllegalStateException("Two-fluid network requires at least two nodes and one branch");
    }
    for (NetworkNode node : nodes.values()) {
      if (node.incomingCount + node.outgoingCount == 0) {
        throw new IllegalStateException("Node '" + node.name + "' is not connected to a branch");
      }
    }
  }

  private void requireUniqueNodeName(String nodeName) {
    requireName(nodeName, "Node");
    if (nodes.containsKey(nodeName)) {
      throw new IllegalArgumentException("Node '" + nodeName + "' already exists");
    }
  }

  private NetworkNode requireNode(String nodeName) {
    NetworkNode node = nodes.get(nodeName);
    if (node == null) {
      throw new IllegalArgumentException("Node '" + nodeName + "' does not exist");
    }
    return node;
  }

  private NetworkBranch requireBranch(String branchName) {
    NetworkBranch branch = branches.get(branchName);
    if (branch == null) {
      throw new IllegalArgumentException("Branch '" + branchName + "' does not exist");
    }
    return branch;
  }

  private static void requireName(String value, String label) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(label + " name cannot be blank");
    }
  }

  private static void requirePositiveFinite(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be positive and finite");
    }
  }

  private static Phase phase(int index) {
    switch (index) {
    case 0:
      return Phase.GAS;
    case 1:
      return Phase.OIL;
    case 2:
      return Phase.WATER;
    default:
      throw new IllegalArgumentException("Unsupported phase index " + index);
    }
  }

  public String getName() {
    return name;
  }

  public double getSimulationTimeSeconds() {
    return simulationTimeSeconds;
  }

  public BalanceReport getLastBalanceReport() {
    return lastBalanceReport;
  }

  /** @return latest whole-network component and energy balance, or {@code null} when not enabled */
  public ThermodynamicBalanceReport getLastThermodynamicBalanceReport() {
    return lastThermodynamicBalanceReport;
  }

  public TwoFluidPipe getPipe(String branchName) {
    NetworkBranch branch = branches.get(branchName);
    if (branch == null)
      throw new IllegalArgumentException("Branch '" + branchName + "' does not exist");
    return branch.pipe;
  }

  public double getNodePressurePa(String nodeName) {
    return requireNode(nodeName).getPressurePa();
  }

  /** Get the recovered temperature of a conservative storage node. */
  public double getNodeTemperatureK(String nodeName) {
    NetworkNode node = requireNode(nodeName);
    if (node.thermodynamicInventory == null) {
      throw new IllegalStateException("Node '" + nodeName + "' has no thermodynamic inventory");
    }
    return node.thermodynamicInventory.fluid.getTemperature("K");
  }

  /** Get one named-component inventory from a conservative storage node. */
  public double getNodeComponentMassKg(String nodeName, String componentName) {
    NetworkNode node = requireNode(nodeName);
    if (node.thermodynamicInventory == null) {
      throw new IllegalStateException("Node '" + nodeName + "' has no thermodynamic inventory");
    }
    return node.thermodynamicInventory.getComponentMassKg(componentName);
  }

  private static final class NetworkNode implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final boolean fixedPressure;
    private final double fixedPressurePa;
    private final UpstreamCompressibleVolume volume;
    private final ThermodynamicInventory thermodynamicInventory;
    private final SystemInterface fixedBoundaryFluid;
    private NodeSourceThermodynamics sourceThermodynamics;
    private int incomingCount;
    private int outgoingCount;

    private NetworkNode(String name, boolean fixedPressure, double fixedPressurePa, UpstreamCompressibleVolume volume,
        ThermodynamicInventory thermodynamicInventory, SystemInterface fixedBoundaryFluid) {
      this.name = name;
      this.fixedPressure = fixedPressure;
      this.fixedPressurePa = fixedPressurePa;
      this.volume = volume;
      this.thermodynamicInventory = thermodynamicInventory;
      this.fixedBoundaryFluid = fixedBoundaryFluid;
    }

    private static NetworkNode fixed(String name, double pressurePa) {
      return new NetworkNode(name, true, pressurePa, null, null, null);
    }

    private static NetworkNode fixed(String name, double pressurePa, SystemInterface boundaryFluid) {
      return new NetworkNode(name, true, pressurePa, null, null, boundaryFluid.clone());
    }

    private static NetworkNode compressible(String name, UpstreamCompressibleVolume volume) {
      return new NetworkNode(name, false, Double.NaN, volume, null, null);
    }

    private static NetworkNode conservative(String name, UpstreamCompressibleVolume volume,
        SystemInterface initialFluid) {
      return new NetworkNode(name, false, Double.NaN, volume,
          new ThermodynamicInventory(initialFluid, volume.getTotalMassKg(), volume.getPressurePa()), null);
    }

    private double getPressurePa() {
      return fixedPressure ? fixedPressurePa : volume.getPressurePa();
    }

    private double getTotalSourceMassFlowRateKgS() {
      double totalKgS = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        totalKgS += volume.getSourceMassFlowRateKgS(phase);
      }
      return totalKgS;
    }

    private SystemInterface getBoundaryFluid() {
      if (thermodynamicInventory != null) {
        return thermodynamicInventory.fluid.clone();
      }
      return fixedBoundaryFluid == null ? null : fixedBoundaryFluid.clone();
    }
  }

  /** Immutable component and enthalpy definition for one conservative-node source. */
  private static final class NodeSourceThermodynamics implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String[] componentNames;
    private final double[] componentMassFractions;
    private final double specificEnthalpyJPerKg;

    /**
     * Create a defensive source definition.
     *
     * @param sourceFluid source composition
     * @param specificEnthalpyJPerKg source specific enthalpy in J/kg
     * @param requiredComponentNames required conservative-network component slate
     */
    private NodeSourceThermodynamics(SystemInterface sourceFluid, double specificEnthalpyJPerKg,
        String[] requiredComponentNames) {
      if (sourceFluid == null || sourceFluid.getNumberOfComponents() == 0) {
        throw new IllegalArgumentException("Conservative node source fluid must contain at least one component");
      }
      if (!Double.isFinite(specificEnthalpyJPerKg)) {
        throw new IllegalArgumentException("Conservative node source specific enthalpy must be finite");
      }
      SystemInterface source = sourceFluid.clone();
      this.componentNames = source.getComponentNames();
      if (!Arrays.equals(requiredComponentNames, componentNames)) {
        throw new IllegalArgumentException("Conservative node source fluid has a different component slate");
      }
      this.componentMassFractions = new double[componentNames.length];
      double totalMass = 0.0;
      for (int index = 0; index < componentNames.length; index++) {
        componentMassFractions[index] = source.getComponent(index).getNumberOfmoles()
            * source.getComponent(index).getMolarMass();
        totalMass += componentMassFractions[index];
      }
      if (!Double.isFinite(totalMass) || totalMass <= 0.0) {
        throw new IllegalArgumentException("Conservative node source fluid must have positive finite component mass");
      }
      for (int index = 0; index < componentMassFractions.length; index++) {
        componentMassFractions[index] /= totalMass;
      }
      this.specificEnthalpyJPerKg = specificEnthalpyJPerKg;
    }

    /**
     * Allocate one accepted source mass to the stored named-component fractions.
     *
     * @param sourceMassKg accepted source mass in kg
     * @return named-component source masses in kg
     */
    private double[] componentMasses(double sourceMassKg) {
      double[] componentMassKg = new double[componentMassFractions.length];
      for (int index = 0; index < componentMassKg.length; index++) {
        componentMassKg[index] = sourceMassKg * componentMassFractions[index];
      }
      return componentMassKg;
    }
  }

  private static final class ThermodynamicInventory implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String[] componentNames;
    private final double[] molarMassKgPerMol;
    private final double[] componentMassKg;
    private final SystemInterface fluid;
    private double internalEnergyJ;

    private ThermodynamicInventory(SystemInterface initialFluid, double totalMassKg, double pressurePa) {
      this.fluid = initialFluid.clone();
      this.fluid.setPressure(pressurePa, "Pa");
      this.fluid.setTotalFlowRate(totalMassKg, "kg/sec");
      new ThermodynamicOperations(this.fluid).TPflash();
      this.componentNames = this.fluid.getComponentNames();
      this.molarMassKgPerMol = new double[componentNames.length];
      this.componentMassKg = new double[componentNames.length];
      for (int index = 0; index < componentNames.length; index++) {
        molarMassKgPerMol[index] = this.fluid.getComponent(index).getMolarMass();
        componentMassKg[index] = this.fluid.getComponent(index).getNumberOfmoles() * molarMassKgPerMol[index];
      }
      this.internalEnergyJ = this.fluid.getInternalEnergy("J");
    }

    private void advance(double[] transferredComponentKg, double transferredEnergyJ, double pressurePa) {
      double[] molarFlow = new double[componentNames.length];
      for (int index = 0; index < componentNames.length; index++) {
        componentMassKg[index] += transferredComponentKg[index];
        if (!Double.isFinite(componentMassKg[index]) || componentMassKg[index] < -1.0e-10) {
          throw new IllegalStateException(
              "Conservative node component inventory became negative or nonfinite for '" + componentNames[index] + "'");
        }
        componentMassKg[index] = Math.max(0.0, componentMassKg[index]);
        molarFlow[index] = componentMassKg[index] / molarMassKgPerMol[index];
      }
      internalEnergyJ += transferredEnergyJ;
      if (!Double.isFinite(internalEnergyJ)) {
        throw new IllegalStateException("Conservative node energy inventory became nonfinite");
      }
      fluid.setMolarFlowRates(molarFlow);
      fluid.setPressure(pressurePa, "Pa");
      new ThermodynamicOperations(fluid).PUflash(internalEnergyJ, "J");
    }

    private double getComponentMassKg(String componentName) {
      for (int index = 0; index < componentNames.length; index++) {
        if (componentNames[index].equals(componentName)) {
          return componentMassKg[index];
        }
      }
      throw new IllegalArgumentException("Component '" + componentName + "' is not in the node slate");
    }
  }

  private static final class NetworkBranch implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final NetworkNode fromNode;
    private final NetworkNode toNode;
    private final TwoFluidPipe pipe;

    private NetworkBranch(String name, NetworkNode fromNode, NetworkNode toNode, TwoFluidPipe pipe) {
      this.name = name;
      this.fromNode = fromNode;
      this.toNode = toNode;
      this.pipe = pipe;
    }
  }

  /** Whole-network phase and total mass balance for the latest accepted reporting interval. */
  public static final class BalanceReport implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double elapsedTimeSeconds;
    private final double[] initialMassKg;
    private final double[] finalMassKg;
    private final double[] externalInletMassKg;
    private final double[] externalOutletMassKg;
    private final double[] sourceMassKg;

    private BalanceReport(double elapsedTimeSeconds, double[] initialMassKg, double[] finalMassKg,
        double[] externalInletMassKg, double[] externalOutletMassKg, double[] sourceMassKg) {
      this.elapsedTimeSeconds = elapsedTimeSeconds;
      this.initialMassKg = initialMassKg.clone();
      this.finalMassKg = finalMassKg.clone();
      this.externalInletMassKg = externalInletMassKg.clone();
      this.externalOutletMassKg = externalOutletMassKg.clone();
      this.sourceMassKg = sourceMassKg.clone();
    }

    public double getElapsedTimeSeconds() {
      return elapsedTimeSeconds;
    }

    public double getInitialMassKg(Phase aggregation) {
      return aggregate(initialMassKg, aggregation);
    }

    public double getFinalMassKg(Phase aggregation) {
      return aggregate(finalMassKg, aggregation);
    }

    public double getExternalInletMassKg(Phase aggregation) {
      return aggregate(externalInletMassKg, aggregation);
    }

    public double getExternalOutletMassKg(Phase aggregation) {
      return aggregate(externalOutletMassKg, aggregation);
    }

    public double getSourceMassKg(Phase aggregation) {
      return aggregate(sourceMassKg, aggregation);
    }

    public double getResidualKg(Phase aggregation) {
      return getFinalMassKg(aggregation) - getInitialMassKg(aggregation)
          - (getExternalInletMassKg(aggregation) - getExternalOutletMassKg(aggregation) + getSourceMassKg(aggregation));
    }

    public double getRelativeResidual(Phase aggregation) {
      double scale = Math.max(Math.abs(getInitialMassKg(aggregation)), Math.abs(getFinalMassKg(aggregation)));
      scale = Math.max(scale, Math.abs(getExternalInletMassKg(aggregation))
          + Math.abs(getExternalOutletMassKg(aggregation)) + Math.abs(getSourceMassKg(aggregation)));
      return Math.abs(getResidualKg(aggregation)) / Math.max(scale, 1.0e-12);
    }

    private static double aggregate(double[] values, Phase aggregation) {
      switch (aggregation) {
      case GAS:
        return values[0];
      case OIL:
        return values[1];
      case WATER:
        return values[2];
      case LIQUID:
        return values[1] + values[2];
      case TOTAL:
        return values[0] + values[1] + values[2];
      default:
        throw new IllegalArgumentException("Unsupported phase " + aggregation);
      }
    }
  }

  /** Whole-network named-component and total-energy balance for the latest accepted interval. */
  public static final class ThermodynamicBalanceReport implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double elapsedTimeSeconds;
    private final String[] componentNames;
    private final double[] initialComponentKg;
    private final double[] finalComponentKg;
    private final double[] externalInletComponentKg;
    private final double[] externalOutletComponentKg;
    private final double[] sourceComponentKg;
    private final double initialEnergyJ;
    private final double finalEnergyJ;
    private final double externalInletEnergyJ;
    private final double externalOutletEnergyJ;
    private final double sourceEnergyJ;
    private final double gravitationalWorkJ;
    private final double wallHeatJ;

    private ThermodynamicBalanceReport(double elapsedTimeSeconds, String[] componentNames, double[] initialComponentKg,
        double[] finalComponentKg, double[] externalInletComponentKg, double[] externalOutletComponentKg,
        double[] sourceComponentKg, double initialEnergyJ, double finalEnergyJ, double externalInletEnergyJ,
        double externalOutletEnergyJ, double sourceEnergyJ, double gravitationalWorkJ, double wallHeatJ) {
      this.elapsedTimeSeconds = elapsedTimeSeconds;
      this.componentNames = componentNames.clone();
      this.initialComponentKg = initialComponentKg.clone();
      this.finalComponentKg = finalComponentKg.clone();
      this.externalInletComponentKg = externalInletComponentKg.clone();
      this.externalOutletComponentKg = externalOutletComponentKg.clone();
      this.sourceComponentKg = sourceComponentKg.clone();
      this.initialEnergyJ = initialEnergyJ;
      this.finalEnergyJ = finalEnergyJ;
      this.externalInletEnergyJ = externalInletEnergyJ;
      this.externalOutletEnergyJ = externalOutletEnergyJ;
      this.sourceEnergyJ = sourceEnergyJ;
      this.gravitationalWorkJ = gravitationalWorkJ;
      this.wallHeatJ = wallHeatJ;
    }

    /** @return accepted elapsed time in seconds */
    public double getElapsedTimeSeconds() {
      return elapsedTimeSeconds;
    }

    /** @return deterministic named-component order */
    public String[] getComponentNames() {
      return componentNames.clone();
    }

    /** Get initial whole-network inventory of a component. */
    public double getInitialComponentMassKg(String componentName) {
      return initialComponentKg[componentIndex(componentName)];
    }

    /** Get final whole-network inventory of a component. */
    public double getFinalComponentMassKg(String componentName) {
      return finalComponentKg[componentIndex(componentName)];
    }

    /** Get component mass entering through fixed-pressure external boundaries. */
    public double getExternalInletComponentMassKg(String componentName) {
      return externalInletComponentKg[componentIndex(componentName)];
    }

    /** Get component mass leaving through fixed-pressure external boundaries. */
    public double getExternalOutletComponentMassKg(String componentName) {
      return externalOutletComponentKg[componentIndex(componentName)];
    }

    /** Get component mass entering through conservative-node source terms. */
    public double getSourceComponentMassKg(String componentName) {
      return sourceComponentKg[componentIndex(componentName)];
    }

    /** Get component residual {@code final - initial - inlet + outlet - source}. */
    public double getComponentResidualKg(String componentName) {
      int index = componentIndex(componentName);
      return finalComponentKg[index] - initialComponentKg[index] - externalInletComponentKg[index]
          + externalOutletComponentKg[index] - sourceComponentKg[index];
    }

    /** Get a component residual scaled by inventory and external transfer magnitudes. */
    public double getRelativeComponentResidual(String componentName) {
      int index = componentIndex(componentName);
      double scale = Math.max(Math.abs(initialComponentKg[index]), Math.abs(finalComponentKg[index]));
      scale = Math.max(scale, Math.abs(externalInletComponentKg[index]) + Math.abs(externalOutletComponentKg[index]));
      scale = Math.max(scale, Math.abs(sourceComponentKg[index]));
      return Math.abs(getComponentResidualKg(componentName)) / Math.max(scale, 1.0e-12);
    }

    /** @return initial pipe plus node energy in joules */
    public double getInitialEnergyJ() {
      return initialEnergyJ;
    }

    /** @return final pipe plus node energy in joules */
    public double getFinalEnergyJ() {
      return finalEnergyJ;
    }

    /** @return energy entering through fixed-pressure external boundaries in joules */
    public double getExternalInletEnergyJ() {
      return externalInletEnergyJ;
    }

    /** @return energy leaving through fixed-pressure external boundaries in joules */
    public double getExternalOutletEnergyJ() {
      return externalOutletEnergyJ;
    }

    /** @return enthalpy entering through conservative-node source terms in joules */
    public double getSourceEnergyJ() {
      return sourceEnergyJ;
    }

    /** @return branch gravitational work added to the network fluid in joules */
    public double getGravitationalWorkJ() {
      return gravitationalWorkJ;
    }

    /** @return branch wall heat added to the network fluid in joules */
    public double getWallHeatJ() {
      return wallHeatJ;
    }

    /** @return whole-network first-law residual in joules */
    public double getEnergyResidualJ() {
      return finalEnergyJ - initialEnergyJ - externalInletEnergyJ + externalOutletEnergyJ - gravitationalWorkJ
          - wallHeatJ - sourceEnergyJ;
    }

    /** @return whole-network first-law residual scaled by storage and transfer magnitudes */
    public double getRelativeEnergyResidual() {
      double scale = Math.max(Math.abs(finalEnergyJ - initialEnergyJ),
          Math.abs(externalInletEnergyJ - externalOutletEnergyJ));
      scale = Math.max(scale, Math.abs(gravitationalWorkJ));
      scale = Math.max(scale, Math.abs(wallHeatJ));
      scale = Math.max(scale, Math.abs(sourceEnergyJ));
      return Math.abs(getEnergyResidualJ()) / Math.max(scale, 1.0e-12);
    }

    private int componentIndex(String componentName) {
      for (int index = 0; index < componentNames.length; index++) {
        if (componentNames[index].equals(componentName)) {
          return index;
        }
      }
      throw new IllegalArgumentException("Unknown component '" + componentName + "'");
    }
  }
}
