package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.UpstreamCompressibleVolume;

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
 * iteration, component/enthalpy mixing, reverse-flow boundary composition, and branch subcycling are outside its
 * current scope.
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

  /** Add an external fixed-pressure reservoir or sink node. */
  public void addFixedPressureNode(String nodeName, double pressurePa) {
    requireUniqueNodeName(nodeName);
    requirePositiveFinite(pressurePa, "Fixed-node pressure");
    nodes.put(nodeName, NetworkNode.fixed(nodeName, pressurePa));
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
      branch.pipe.run(id);
      configureTransientPressures(branch);
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

    for (NetworkNode node : nodes.values()) {
      nodeTransferKg.put(node.name, new double[PHASE_COUNT]);
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
    }

    for (NetworkNode node : nodes.values()) {
      if (!node.fixedPressure) {
        node.volume.advance(timeStepSeconds, nodeTransferKg.get(node.name));
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          finalMassKg[phase] += node.volume.getPhaseMassKg(phase);
        }
      }
    }

    simulationTimeSeconds += timeStepSeconds;
    lastBalanceReport = new BalanceReport(timeStepSeconds, initialMassKg, finalMassKg, externalInletMassKg,
        externalOutletMassKg, sourceMassKg);
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

  public TwoFluidPipe getPipe(String branchName) {
    NetworkBranch branch = branches.get(branchName);
    if (branch == null)
      throw new IllegalArgumentException("Branch '" + branchName + "' does not exist");
    return branch.pipe;
  }

  public double getNodePressurePa(String nodeName) {
    return requireNode(nodeName).getPressurePa();
  }

  private static final class NetworkNode implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final boolean fixedPressure;
    private final double fixedPressurePa;
    private final UpstreamCompressibleVolume volume;
    private int incomingCount;
    private int outgoingCount;

    private NetworkNode(String name, boolean fixedPressure, double fixedPressurePa, UpstreamCompressibleVolume volume) {
      this.name = name;
      this.fixedPressure = fixedPressure;
      this.fixedPressurePa = fixedPressurePa;
      this.volume = volume;
    }

    private static NetworkNode fixed(String name, double pressurePa) {
      return new NetworkNode(name, true, pressurePa, null);
    }

    private static NetworkNode compressible(String name, UpstreamCompressibleVolume volume) {
      return new NetworkNode(name, false, Double.NaN, volume);
    }

    private double getPressurePa() {
      return fixedPressure ? fixedPressurePa : volume.getPressurePa();
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
}
