package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.SerializationUtils;
import org.ejml.simple.SimpleMatrix;
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
 * Storage nodes provide explicit pressure inventory. Conservative algebraic zero-volume junctions may instead solve
 * common pressures and mixed thermodynamic donors from the exact signed branch ledgers. Multiple junctions use a global
 * finite-difference Newton pressure solve inside the same atomic branch transaction. Branches may use different integer
 * subcycle factors while retaining frozen outer-interval node states and one atomic publication boundary. Component and
 * total-energy mixing are available as an explicit opt-in on compressible nodes. Signed outlet fallback is supported
 * only when the downstream node or pipe owns an explicit thermodynamic donor.
 * </p>
 */
public final class TwoFluidPipeNetwork implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final int PHASE_COUNT = 3;
  private static final double MASS_CLOSURE_TOLERANCE = 1.0e-8;
  private static final double MASS_CLOSURE_ABSOLUTE_KG = 1.0e-10;
  private static final double COMPONENT_CLOSURE_TOLERANCE = 1.0e-8;
  private static final double COMPONENT_CLOSURE_ABSOLUTE_KG = 1.0e-10;
  private static final double ENERGY_CLOSURE_TOLERANCE = 1.0e-7;
  private static final double ENERGY_CLOSURE_ABSOLUTE_J = 1.0e-4;
  private static final int ALGEBRAIC_MAXIMUM_PRESSURE_ITERATIONS = 16;
  private static final int ALGEBRAIC_MAXIMUM_GLOBAL_PRESSURE_ITERATIONS = 24;
  private static final int ALGEBRAIC_MAXIMUM_THERMODYNAMIC_ITERATIONS = 128;
  private static final double ALGEBRAIC_PRESSURE_TOLERANCE_PA = 1.0e-2;
  private static final double ALGEBRAIC_TRANSFER_TOLERANCE = 1.0e-8;
  private static final double ALGEBRAIC_JACOBIAN_RELATIVE_STEP = 1.0e-6;
  private static final int ALGEBRAIC_MAXIMUM_LINE_SEARCH_ITERATIONS = 10;
  private static final double ALGEBRAIC_DONOR_RELAXATION = 1.0;

  private final String name;
  private final Map<String, NetworkNode> nodes = new LinkedHashMap<String, NetworkNode>();
  private final Map<String, NetworkBranch> branches = new LinkedHashMap<String, NetworkBranch>();
  private boolean initialized;
  private double simulationTimeSeconds;
  private BalanceReport lastBalanceReport;
  private ThermodynamicBalanceReport lastThermodynamicBalanceReport;
  private AlgebraicJunctionReport lastAlgebraicJunctionReport;
  private Map<String, AlgebraicJunctionReport> lastAlgebraicJunctionReports = new LinkedHashMap<String, AlgebraicJunctionReport>();

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

  /**
   * Add a conservative algebraic zero-volume junction.
   *
   * <p>
   * The junction has no stored mass or energy. During each network interval, all incident pipes are evaluated from the
   * same accepted state while the coordinator solves a common pressure and an equilibrium mixed donor state. The
   * accepted phase, named-component, and total-energy boundary ledgers must close before any pipe is published. This
   * Multiple junctions, including branches between junctions, are solved in one coupled global pressure transaction.
   * </p>
   *
   * @param nodeName unique junction name
   * @param initialPressurePa positive initial common pressure in Pa
   * @param initialFluid initial component slate and thermodynamic iterate
   */
  public void addAlgebraicJunction(String nodeName, double initialPressurePa, SystemInterface initialFluid) {
    requireUniqueNodeName(nodeName);
    requirePositiveFinite(initialPressurePa, "Algebraic-junction pressure");
    if (initialFluid == null || initialFluid.getNumberOfComponents() == 0) {
      throw new IllegalArgumentException("Algebraic-junction fluid must contain at least one component");
    }
    nodes.put(nodeName, NetworkNode.algebraic(nodeName, initialPressurePa, initialFluid));
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
   * Set the integer subcycle factor for one branch.
   *
   * <p>
   * Each subcycle advances a detached pipe candidate with the node pressures and thermodynamic donors frozen at the
   * current outer-interval trial. The network consumes one aggregate conservative ledger and publishes every branch and
   * node once at the outer synchronization boundary. This is synchronous branch-local refinement, not asynchronous
   * multirate boundary coupling.
   * </p>
   *
   * @param branchName existing branch name
   * @param subcycles positive number of equal detached subcycles per network interval
   * @throws IllegalArgumentException if the branch does not exist or subcycles is not positive
   */
  public void setBranchSubcycles(String branchName, int subcycles) {
    if (subcycles < 1) {
      throw new IllegalArgumentException("Branch subcycles must be positive");
    }
    requireBranch(branchName).subcycles = subcycles;
  }

  /**
   * Get the configured subcycle factor for one branch.
   *
   * @param branchName existing branch name
   * @return positive number of equal detached subcycles per network interval
   */
  public int getBranchSubcycles(String branchName) {
    return requireBranch(branchName).subcycles;
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
      publishInletBoundaryState(branch);
      publishOutletBoundaryState(branch, false);
      if (branch.toNode.algebraic) {
        branch.pipe.setAllowOutletPhaseBackflow(true);
      }
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
    double[] internalPhaseTransferKg = new double[PHASE_COUNT];
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
    Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates = new LinkedHashMap<NetworkBranch, TwoFluidPipe.TransientCandidate>();
    Map<NetworkNode, UpstreamCompressibleVolume> volumeCandidates = new LinkedHashMap<NetworkNode, UpstreamCompressibleVolume>();
    Map<NetworkNode, ThermodynamicInventory> thermodynamicCandidates = new LinkedHashMap<NetworkNode, ThermodynamicInventory>();

    if (conservative) {
      validateConservativeNetwork();
      publishFrozenNodeStates();
    }

    AlgebraicNetworkSolution algebraicSolution = solveAlgebraicNetwork(timeStepSeconds, id);
    if (algebraicSolution != null) {
      branchCandidates.putAll(algebraicSolution.branchCandidates);
      for (AlgebraicJunctionReport report : algebraicSolution.reports.values()) {
        for (int phaseIndex = 0; phaseIndex < PHASE_COUNT; phaseIndex++) {
          internalPhaseTransferKg[phaseIndex] -= report.rawPhaseResidualKg[phaseIndex];
        }
      }
    }

    for (NetworkNode node : nodes.values()) {
      nodeTransferKg.put(node.name, new double[PHASE_COUNT]);
      if (conservative && !node.fixedPressure) {
        nodeComponentTransferKg.put(node.name, new double[componentCount]);
        nodeEnergyTransferJ.put(node.name, 0.0);
      }
      if (conservative && node.hasStorage()) {
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
      if (node.hasStorage()) {
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          initialMassKg[phase] += node.volume.getPhaseMassKg(phase);
          sourceMassKg[phase] += node.volume.getSourceMassFlowRateKgS(phase) * timeStepSeconds;
        }
      }
    }

    for (NetworkBranch branch : branches.values()) {
      TwoFluidPipe.TransientCandidate candidate = branchCandidates.get(branch);
      if (candidate == null) {
        configureTransientPressures(branch);
        candidate = branch.pipe.prepareTransientCandidate(timeStepSeconds, id, branch.subcycles);
        branchCandidates.put(branch, candidate);
      }
      TwoFluidMassBalanceReport report = candidate.getMassBalanceReport();
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
        TwoFluidComponentConservationReport componentReport = candidate.getComponentConservationReport();
        TwoFluidConservativeEnergyReport energyReport = candidate.getConservativeEnergyReport();
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
      if (node.hasStorage()) {
        UpstreamCompressibleVolume volumeCandidate = SerializationUtils.clone(node.volume);
        volumeCandidate.advance(timeStepSeconds, nodeTransferKg.get(node.name));
        volumeCandidates.put(node, volumeCandidate);
        if (conservative) {
          ThermodynamicInventory thermodynamicCandidate = SerializationUtils.clone(node.thermodynamicInventory);
          thermodynamicCandidate.advance(nodeComponentTransferKg.get(node.name), nodeEnergyTransferJ.get(node.name),
              volumeCandidate.getPressurePa());
          thermodynamicCandidates.put(node, thermodynamicCandidate);
          add(finalComponentKg, thermodynamicCandidate.componentMassKg, 1.0);
          finalEnergyJ += thermodynamicCandidate.internalEnergyJ;
        }
        for (int phase = 0; phase < PHASE_COUNT; phase++) {
          finalMassKg[phase] += volumeCandidate.getPhaseMassKg(phase);
        }
      }
    }

    BalanceReport candidateBalanceReport = new BalanceReport(timeStepSeconds, initialMassKg, finalMassKg,
        externalInletMassKg, externalOutletMassKg, sourceMassKg, internalPhaseTransferKg);
    ThermodynamicBalanceReport candidateThermodynamicBalanceReport = conservative
        ? new ThermodynamicBalanceReport(timeStepSeconds, componentNames, initialComponentKg, finalComponentKg,
            externalInletComponentKg, externalOutletComponentKg, sourceComponentKg, initialEnergyJ, finalEnergyJ,
            externalInletEnergyJ, externalOutletEnergyJ, sourceEnergyJ, gravitationalWorkJ, wallHeatJ)
        : null;
    validateCandidateClosure(candidateBalanceReport, candidateThermodynamicBalanceReport);
    for (Map.Entry<NetworkBranch, TwoFluidPipe.TransientCandidate> entry : branchCandidates.entrySet()) {
      entry.getKey().pipe.validateTransientCandidate(entry.getValue());
    }
    for (Map.Entry<NetworkNode, UpstreamCompressibleVolume> entry : volumeCandidates.entrySet()) {
      entry.getKey().volume.validateCandidateConfiguration(entry.getValue());
    }
    for (Map.Entry<NetworkNode, ThermodynamicInventory> entry : thermodynamicCandidates.entrySet()) {
      entry.getKey().thermodynamicInventory.validateCandidateState(entry.getValue());
    }

    List<Map.Entry<NetworkBranch, TwoFluidPipe.TransientCandidate>> acceptedBranches = new ArrayList<Map.Entry<NetworkBranch, TwoFluidPipe.TransientCandidate>>();
    try {
      for (Map.Entry<NetworkBranch, TwoFluidPipe.TransientCandidate> entry : branchCandidates.entrySet()) {
        entry.getKey().pipe.acceptTransientCandidate(entry.getValue());
        acceptedBranches.add(entry);
      }
    } catch (RuntimeException commitFailure) {
      for (int index = acceptedBranches.size() - 1; index >= 0; index--) {
        Map.Entry<NetworkBranch, TwoFluidPipe.TransientCandidate> accepted = acceptedBranches.get(index);
        try {
          accepted.getKey().pipe.rollbackTransientCandidate(accepted.getValue());
        } catch (RuntimeException rollbackFailure) {
          commitFailure.addSuppressed(rollbackFailure);
        }
      }
      throw commitFailure;
    }
    for (Map.Entry<NetworkNode, UpstreamCompressibleVolume> entry : volumeCandidates.entrySet()) {
      entry.getKey().volume.acceptCandidateState(entry.getValue());
    }
    for (Map.Entry<NetworkNode, ThermodynamicInventory> entry : thermodynamicCandidates.entrySet()) {
      entry.getKey().thermodynamicInventory.acceptCandidateState(entry.getValue());
    }
    simulationTimeSeconds += timeStepSeconds;
    lastBalanceReport = candidateBalanceReport;
    lastThermodynamicBalanceReport = candidateThermodynamicBalanceReport;
    if (algebraicSolution != null) {
      for (Map.Entry<NetworkNode, AlgebraicAcceptedState> entry : algebraicSolution.states.entrySet()) {
        entry.getKey().acceptAlgebraicState(entry.getValue().pressurePa, entry.getValue().fluid);
      }
      lastAlgebraicJunctionReports = new LinkedHashMap<String, AlgebraicJunctionReport>(algebraicSolution.reports);
      lastAlgebraicJunctionReport = lastAlgebraicJunctionReports.values().iterator().next();
    } else {
      lastAlgebraicJunctionReport = null;
      lastAlgebraicJunctionReports = new LinkedHashMap<String, AlgebraicJunctionReport>();
    }
  }

  /** Solve every configured algebraic junction from one frozen network state. */
  private AlgebraicNetworkSolution solveAlgebraicNetwork(double timeStepSeconds, UUID id) {
    List<NetworkNode> junctions = new ArrayList<NetworkNode>();
    for (NetworkNode node : nodes.values()) {
      if (node.algebraic) {
        junctions.add(node);
      }
    }
    if (junctions.isEmpty()) {
      return null;
    }
    if (junctions.size() == 1) {
      AlgebraicSolution solution = solveAlgebraicJunction(timeStepSeconds, id);
      return AlgebraicNetworkSolution.single(solution);
    }
    return solveCoupledAlgebraicJunctions(junctions, timeStepSeconds, id);
  }

  /** Solve coupled zero-volume junction pressures and equilibrium donors without publication. */
  private AlgebraicNetworkSolution solveCoupledAlgebraicJunctions(List<NetworkNode> junctions, double timeStepSeconds,
      UUID id) {
    Map<NetworkNode, SystemInterface> donors = new LinkedHashMap<NetworkNode, SystemInterface>();
    double[] pressuresPa = new double[junctions.size()];
    for (int index = 0; index < junctions.size(); index++) {
      NetworkNode junction = junctions.get(index);
      donors.put(junction, SerializationUtils.clone(junction.algebraicFluid));
      pressuresPa[index] = junction.getPressurePa();
    }

    AlgebraicNetworkTrial trial = null;
    int thermodynamicIterations = 0;
    boolean finalDonorConverged = false;
    boolean finalLedgersClose = false;
    for (; thermodynamicIterations < ALGEBRAIC_MAXIMUM_THERMODYNAMIC_ITERATIONS; thermodynamicIterations++) {
      trial = solveCoupledAlgebraicPressures(junctions, donors, pressuresPa, timeStepSeconds, id);
      Map<NetworkNode, SystemInterface> mixedDonors = new LinkedHashMap<NetworkNode, SystemInterface>();
      boolean donorConverged = true;
      boolean ledgersClose = true;
      for (NetworkNode junction : junctions) {
        SystemInterface recovered = mixAlgebraicDonor(junction, donors.get(junction), trial.nodeTrials.get(junction));
        SystemInterface mixed = relaxAlgebraicDonor(donors.get(junction), recovered);
        donorConverged &= algebraicDonorConverged(donors.get(junction), mixed);
        ledgersClose &= trial.nodeTrials.get(junction).closes();
        mixedDonors.put(junction, mixed);
      }
      donors = mixedDonors;
      pressuresPa = trial.pressuresPa.clone();
      finalDonorConverged = donorConverged;
      finalLedgersClose = ledgersClose;
      if (ledgersClose) {
        break;
      }
    }
    if (trial == null || thermodynamicIterations >= ALGEBRAIC_MAXIMUM_THERMODYNAMIC_ITERATIONS) {
      StringBuilder message = new StringBuilder("Coupled algebraic junctions did not converge their donor states")
          .append("; donorConverged=").append(finalDonorConverged).append(", ledgersClose=").append(finalLedgersClose);
      if (trial != null) {
        for (NetworkNode junction : junctions) {
          AlgebraicTrial nodeTrial = trial.nodeTrials.get(junction);
          message.append("; ").append(junction.name).append(" phase=")
              .append(Arrays.toString(nodeTrial.phaseResidualKg)).append(", component=")
              .append(Arrays.toString(nodeTrial.componentResidualKg)).append(", energy=")
              .append(nodeTrial.energyResidualJ);
        }
      }
      throw new IllegalStateException(message.toString());
    }

    trial = solveCoupledAlgebraicPressures(junctions, donors, pressuresPa, timeStepSeconds, id);
    Map<NetworkNode, AlgebraicAcceptedState> states = new LinkedHashMap<NetworkNode, AlgebraicAcceptedState>();
    Map<String, AlgebraicJunctionReport> reports = new LinkedHashMap<String, AlgebraicJunctionReport>();
    for (int index = 0; index < junctions.size(); index++) {
      NetworkNode junction = junctions.get(index);
      AlgebraicTrial nodeTrial = trial.nodeTrials.get(junction);
      nodeTrial.requireClosure(junction.name);
      SystemInterface acceptedFluid = SerializationUtils.clone(donors.get(junction));
      acceptedFluid.setPressure(trial.pressuresPa[index], "Pa");
      new ThermodynamicOperations(acceptedFluid).TPflash();
      acceptedFluid.initProperties();
      AlgebraicJunctionReport report = new AlgebraicJunctionReport(junction.name, trial.pressuresPa[index],
          thermodynamicIterations + 1, trial.pressureIterations, nodeTrial.phaseResidualKg, nodeTrial.componentNames,
          nodeTrial.componentResidualKg, nodeTrial.energyResidualJ, nodeTrial.transferScaleKg, nodeTrial.energyScaleJ);
      states.put(junction, new AlgebraicAcceptedState(trial.pressuresPa[index], acceptedFluid));
      reports.put(junction.name, report);
    }
    return new AlgebraicNetworkSolution(states, trial.branchCandidates, reports);
  }

  /** Solve the global pressure residual vector for one frozen donor iterate. */
  private AlgebraicNetworkTrial solveCoupledAlgebraicPressures(List<NetworkNode> junctions,
      Map<NetworkNode, SystemInterface> donors, double[] initialPressuresPa, double timeStepSeconds, UUID id) {
    double[] pressuresPa = initialPressuresPa.clone();
    AlgebraicNetworkTrial trial = evaluateCoupledAlgebraicTrial(junctions, donors, pressuresPa, timeStepSeconds, id, 0);
    for (int iteration = 1; iteration <= ALGEBRAIC_MAXIMUM_GLOBAL_PRESSURE_ITERATIONS; iteration++) {
      if (trial.pressureCloses()) {
        return trial;
      }
      double[][] jacobian = new double[junctions.size()][junctions.size()];
      double[] residual = trial.massResidualVector();
      for (int column = 0; column < junctions.size(); column++) {
        double stepPa = Math.max(ALGEBRAIC_PRESSURE_TOLERANCE_PA,
            ALGEBRAIC_JACOBIAN_RELATIVE_STEP * pressuresPa[column]);
        double[] perturbedPressurePa = pressuresPa.clone();
        perturbedPressurePa[column] += stepPa;
        AlgebraicNetworkTrial perturbed = evaluateCoupledAlgebraicTrial(junctions, donors, perturbedPressurePa,
            timeStepSeconds, id, iteration);
        double[] perturbedResidual = perturbed.massResidualVector();
        for (int row = 0; row < junctions.size(); row++) {
          jacobian[row][column] = (perturbedResidual[row] - residual[row]) / stepPa;
        }
      }
      SimpleMatrix rightHandSide = new SimpleMatrix(residual.length, 1, true, residual).scale(-1.0);
      SimpleMatrix pressureUpdate;
      try {
        pressureUpdate = new SimpleMatrix(jacobian).solve(rightHandSide);
      } catch (RuntimeException exception) {
        throw new IllegalStateException("Coupled algebraic pressure Jacobian is singular", exception);
      }
      double initialNorm = trial.pressureResidualNorm();
      boolean accepted = false;
      for (int search = 0; search < ALGEBRAIC_MAXIMUM_LINE_SEARCH_ITERATIONS; search++) {
        double scale = Math.pow(0.5, search);
        double[] candidatePressurePa = pressuresPa.clone();
        boolean positiveFinite = true;
        for (int index = 0; index < candidatePressurePa.length; index++) {
          candidatePressurePa[index] += scale * pressureUpdate.get(index, 0);
          positiveFinite &= Double.isFinite(candidatePressurePa[index]) && candidatePressurePa[index] > 0.0;
        }
        if (!positiveFinite) {
          continue;
        }
        AlgebraicNetworkTrial candidate = evaluateCoupledAlgebraicTrial(junctions, donors, candidatePressurePa,
            timeStepSeconds, id, iteration);
        if (candidate.pressureResidualNorm() < initialNorm) {
          pressuresPa = candidatePressurePa;
          trial = candidate;
          accepted = true;
          break;
        }
      }
      if (!accepted) {
        throw new IllegalStateException("Coupled algebraic pressure line search did not reduce the mass residual");
      }
    }
    throw new IllegalStateException("Coupled algebraic junctions exceeded the global pressure iteration limit");
  }

  /** Evaluate every branch incident to any algebraic junction exactly once. */
  private AlgebraicNetworkTrial evaluateCoupledAlgebraicTrial(List<NetworkNode> junctions,
      Map<NetworkNode, SystemInterface> donors, double[] pressuresPa, double timeStepSeconds, UUID id,
      int pressureIterations) {
    Map<NetworkNode, Integer> junctionIndex = new LinkedHashMap<NetworkNode, Integer>();
    Map<NetworkNode, AlgebraicTrialAccumulator> accumulators = new LinkedHashMap<NetworkNode, AlgebraicTrialAccumulator>();
    String[] componentNames = conservativeComponentNames();
    for (int index = 0; index < junctions.size(); index++) {
      junctionIndex.put(junctions.get(index), index);
      accumulators.put(junctions.get(index), new AlgebraicTrialAccumulator(componentNames));
    }
    Map<NetworkBranch, TwoFluidPipe.TransientCandidate> candidates = new LinkedHashMap<NetworkBranch, TwoFluidPipe.TransientCandidate>();
    Set<NetworkBranch> incident = new LinkedHashSet<NetworkBranch>();
    for (NetworkBranch branch : branches.values()) {
      if (branch.fromNode.algebraic || branch.toNode.algebraic) {
        incident.add(branch);
      }
    }
    for (NetworkBranch branch : incident) {
      double inletPressurePa = branch.fromNode.algebraic ? pressuresPa[junctionIndex.get(branch.fromNode)]
          : branch.fromNode.getPressurePa();
      double outletPressurePa = branch.toNode.algebraic ? pressuresPa[junctionIndex.get(branch.toNode)]
          : branch.toNode.getPressurePa();
      SystemInterface inletDonor = branch.fromNode.algebraic ? donors.get(branch.fromNode) : null;
      SystemInterface outletDonor = branch.toNode.algebraic ? donors.get(branch.toNode) : null;
      TwoFluidPipe.TransientCandidate candidate = branch.pipe.prepareTransientCandidate(timeStepSeconds, id,
          inletPressurePa, outletPressurePa, inletDonor, outletDonor, branch.subcycles);
      candidates.put(branch, candidate);
      TwoFluidComponentConservationReport components = candidate.getComponentConservationReport();
      TwoFluidConservativeEnergyReport energy = candidate.getConservativeEnergyReport();
      validateBranchThermodynamicReports(branch, componentNames, components, energy);
      if (branch.fromNode.algebraic) {
        accumulators.get(branch.fromNode).addBoundary(branch, branch.fromNode, candidate);
      }
      if (branch.toNode.algebraic) {
        accumulators.get(branch.toNode).addBoundary(branch, branch.toNode, candidate);
      }
    }
    Map<NetworkNode, AlgebraicTrial> nodeTrials = new LinkedHashMap<NetworkNode, AlgebraicTrial>();
    for (int index = 0; index < junctions.size(); index++) {
      NetworkNode junction = junctions.get(index);
      nodeTrials.put(junction, accumulators.get(junction).toTrial(pressuresPa[index], pressureIterations, candidates));
    }
    return new AlgebraicNetworkTrial(pressuresPa, pressureIterations, candidates, nodeTrials, junctions);
  }

  /**
   * Solve the bounded algebraic-junction contract without changing any accepted pipe.
   *
   * @param timeStepSeconds reporting interval in seconds
   * @param id calculation identifier
   * @return converged detached solution, or {@code null} when no algebraic junction is configured
   */
  private AlgebraicSolution solveAlgebraicJunction(double timeStepSeconds, UUID id) {
    NetworkNode junction = null;
    for (NetworkNode node : nodes.values()) {
      if (node.algebraic) {
        if (junction != null) {
          throw new IllegalStateException("The scalar algebraic solver received more than one junction");
        }
        junction = node;
      }
    }
    if (junction == null) {
      return null;
    }

    List<NetworkBranch> incident = new ArrayList<NetworkBranch>();
    double minimumAdjacentPressurePa = Double.POSITIVE_INFINITY;
    double maximumAdjacentPressurePa = 0.0;
    for (NetworkBranch branch : branches.values()) {
      NetworkNode adjacent = null;
      if (branch.fromNode == junction) {
        adjacent = branch.toNode;
      } else if (branch.toNode == junction) {
        adjacent = branch.fromNode;
      }
      if (adjacent != null) {
        incident.add(branch);
        minimumAdjacentPressurePa = Math.min(minimumAdjacentPressurePa, adjacent.getPressurePa());
        maximumAdjacentPressurePa = Math.max(maximumAdjacentPressurePa, adjacent.getPressurePa());
      }
    }
    if (incident.size() < 2) {
      throw new IllegalStateException("Algebraic junction '" + junction.name + "' requires at least two branches");
    }

    SystemInterface donor = SerializationUtils.clone(junction.algebraicFluid);
    AlgebraicTrial trial = null;
    int thermodynamicIterations = 0;
    for (; thermodynamicIterations < ALGEBRAIC_MAXIMUM_THERMODYNAMIC_ITERATIONS; thermodynamicIterations++) {
      trial = solveAlgebraicPressure(junction, incident, donor, minimumAdjacentPressurePa, maximumAdjacentPressurePa,
          timeStepSeconds, id);
      SystemInterface mixed = mixAlgebraicDonor(junction, donor, trial);
      boolean donorConverged = algebraicDonorConverged(donor, mixed);
      donor = mixed;
      if (donorConverged && trial.closes()) {
        break;
      }
    }
    if (trial == null) {
      throw new IllegalStateException("Algebraic junction '" + junction.name + "' produced no donor-state trial");
    }
    if (thermodynamicIterations >= ALGEBRAIC_MAXIMUM_THERMODYNAMIC_ITERATIONS) {
      throw new IllegalStateException("Algebraic junction '" + junction.name
          + "' did not converge its donor state; phase=" + Arrays.toString(trial.phaseResidualKg) + " kg, component="
          + Arrays.toString(trial.componentResidualKg) + " kg, energy=" + trial.energyResidualJ + " J");
    }

    trial = solveAlgebraicPressure(junction, incident, donor, minimumAdjacentPressurePa, maximumAdjacentPressurePa,
        timeStepSeconds, id);
    trial.requireClosure(junction.name);
    SystemInterface acceptedFluid = SerializationUtils.clone(donor);
    acceptedFluid.setPressure(trial.pressurePa, "Pa");
    new ThermodynamicOperations(acceptedFluid).TPflash();
    acceptedFluid.initProperties();
    AlgebraicJunctionReport report = new AlgebraicJunctionReport(junction.name, trial.pressurePa,
        thermodynamicIterations + 1, trial.pressureIterations, trial.phaseResidualKg, trial.componentNames,
        trial.componentResidualKg, trial.energyResidualJ, trial.transferScaleKg, trial.energyScaleJ);
    return new AlgebraicSolution(junction, trial.pressurePa, acceptedFluid, trial.branchCandidates, report);
  }

  /** Solve the scalar common pressure for one frozen donor iterate. */
  private AlgebraicTrial solveAlgebraicPressure(NetworkNode junction, List<NetworkBranch> incident,
      SystemInterface donor, double minimumAdjacentPressurePa, double maximumAdjacentPressurePa, double timeStepSeconds,
      UUID id) {
    double lowerPa = Math.min(minimumAdjacentPressurePa, junction.getPressurePa());
    double upperPa = Math.max(maximumAdjacentPressurePa, junction.getPressurePa());
    if (!(upperPa > lowerPa)) {
      lowerPa = Math.max(1.0, lowerPa * 0.95);
      upperPa = upperPa * 1.05;
    }
    AlgebraicTrial lower = evaluateAlgebraicTrial(junction, incident, donor, lowerPa, timeStepSeconds, id, 0);
    AlgebraicTrial upper = evaluateAlgebraicTrial(junction, incident, donor, upperPa, timeStepSeconds, id, 0);
    for (int expansion = 0; lower.totalMassResidualKg * upper.totalMassResidualKg > 0.0 && expansion < 8; expansion++) {
      lowerPa = Math.max(1.0, lowerPa * 0.7);
      upperPa *= 1.3;
      lower = evaluateAlgebraicTrial(junction, incident, donor, lowerPa, timeStepSeconds, id, 0);
      upper = evaluateAlgebraicTrial(junction, incident, donor, upperPa, timeStepSeconds, id, 0);
    }
    if (lower.totalMassResidualKg * upper.totalMassResidualKg > 0.0) {
      throw new IllegalStateException("Algebraic junction '" + junction.name
          + "' could not bracket a common-pressure mass root; endpoint residuals are " + lower.totalMassResidualKg
          + " and " + upper.totalMassResidualKg + " kg");
    }
    if (lower.pressureCloses()) {
      return lower;
    }
    if (upper.pressureCloses()) {
      return upper;
    }

    AlgebraicTrial midpoint = null;
    for (int iteration = 1; iteration <= ALGEBRAIC_MAXIMUM_PRESSURE_ITERATIONS; iteration++) {
      double denominator = upper.totalMassResidualKg - lower.totalMassResidualKg;
      double midpointPa = denominator == 0.0 ? 0.5 * (lowerPa + upperPa)
          : (lowerPa * upper.totalMassResidualKg - upperPa * lower.totalMassResidualKg) / denominator;
      if (!Double.isFinite(midpointPa) || midpointPa <= lowerPa || midpointPa >= upperPa) {
        midpointPa = 0.5 * (lowerPa + upperPa);
      }
      midpoint = evaluateAlgebraicTrial(junction, incident, donor, midpointPa, timeStepSeconds, id, iteration);
      if (midpoint.pressureCloses() || upperPa - lowerPa <= ALGEBRAIC_PRESSURE_TOLERANCE_PA) {
        return midpoint;
      }
      if (lower.totalMassResidualKg * midpoint.totalMassResidualKg <= 0.0) {
        upperPa = midpointPa;
        upper = midpoint;
      } else {
        lowerPa = midpointPa;
        lower = midpoint;
      }
    }
    throw new IllegalStateException(
        "Algebraic junction '" + junction.name + "' exceeded the bounded common-pressure iteration limit");
  }

  /** Evaluate every incident branch at one detached pressure and donor state. */
  private AlgebraicTrial evaluateAlgebraicTrial(NetworkNode junction, List<NetworkBranch> incident,
      SystemInterface donor, double pressurePa, double timeStepSeconds, UUID id, int pressureIterations) {
    Map<NetworkBranch, TwoFluidPipe.TransientCandidate> candidates = new LinkedHashMap<NetworkBranch, TwoFluidPipe.TransientCandidate>();
    String[] componentNames = conservativeComponentNames();
    double[] phaseResidualKg = new double[PHASE_COUNT];
    double[] componentResidualKg = new double[componentNames.length];
    double incomingEnergyJ = 0.0;
    double incomingMassKg = 0.0;
    double energyResidualJ = 0.0;
    double transferScaleKg = 0.0;
    double energyScaleJ = 0.0;

    for (NetworkBranch branch : incident) {
      double inletPressurePa = branch.fromNode == junction ? pressurePa : branch.fromNode.getPressurePa();
      double outletPressurePa = branch.toNode == junction ? pressurePa : branch.toNode.getPressurePa();
      SystemInterface inletDonor = branch.fromNode == junction ? donor : null;
      SystemInterface outletDonor = branch.toNode == junction ? donor : null;
      TwoFluidPipe.TransientCandidate candidate = branch.pipe.prepareTransientCandidate(timeStepSeconds, id,
          inletPressurePa, outletPressurePa, inletDonor, outletDonor, branch.subcycles);
      candidates.put(branch, candidate);
      TwoFluidMassBalanceReport mass = candidate.getMassBalanceReport();
      TwoFluidComponentConservationReport components = candidate.getComponentConservationReport();
      TwoFluidConservativeEnergyReport energy = candidate.getConservativeEnergyReport();
      validateBranchThermodynamicReports(branch, componentNames, components, energy);

      for (int phaseIndex = 0; phaseIndex < PHASE_COUNT; phaseIndex++) {
        Phase aggregation = phase(phaseIndex);
        double branchMassKg = branch.fromNode == junction ? -mass.getInletMassKg(aggregation)
            : mass.getOutletMassKg(aggregation);
        phaseResidualKg[phaseIndex] += branchMassKg;
        transferScaleKg += Math.abs(branchMassKg);
        double[] branchComponentKg = algebraicBoundaryComponents(branch, junction, components, phaseIndex);
        double branchEnergyJ = algebraicBoundaryEnergy(branch, junction, energy, phaseIndex);
        add(componentResidualKg, branchComponentKg, 1.0);
        energyResidualJ += branchEnergyJ;
        energyScaleJ += Math.abs(branchEnergyJ);
        if (branchMassKg > 0.0) {
          incomingMassKg += branchMassKg;
          incomingEnergyJ += branchEnergyJ;
        }
      }
    }
    double totalMassResidualKg = phaseResidualKg[0] + phaseResidualKg[1] + phaseResidualKg[2];
    return new AlgebraicTrial(pressurePa, pressureIterations, candidates, componentNames, phaseResidualKg,
        componentResidualKg, energyResidualJ, totalMassResidualKg, incomingMassKg, incomingEnergyJ, transferScaleKg,
        energyScaleJ);
  }

  /** Return the signed component transfer entering the algebraic node for one phase. */
  private static double[] algebraicBoundaryComponents(NetworkBranch branch, NetworkNode junction,
      TwoFluidComponentConservationReport report, int phaseIndex) {
    TwoFluidComponentConservationReport.Phase phase = componentPhase(phaseIndex);
    double[] values = branch.fromNode == junction ? report.getInletBoundaryMassKg(phase)
        : report.getOutletBoundaryMassKg(phase);
    if (branch.fromNode == junction) {
      for (int index = 0; index < values.length; index++) {
        values[index] = -values[index];
      }
    }
    return values;
  }

  /** Return the signed advected-energy transfer entering the algebraic node for one phase. */
  private static double algebraicBoundaryEnergy(NetworkBranch branch, NetworkNode junction,
      TwoFluidConservativeEnergyReport report, int phaseIndex) {
    TwoFluidConservativeEnergyReport.Phase phase = energyPhase(phaseIndex);
    return branch.fromNode == junction ? -report.getInletAdvectedEnergyJ(phase)
        : report.getOutletAdvectedEnergyJ(phase);
  }

  /** Recover the next equilibrium donor iterate from the exact incoming component and energy ledgers. */
  private static SystemInterface mixAlgebraicDonor(NetworkNode junction, SystemInterface previous,
      AlgebraicTrial trial) {
    if (!(trial.incomingMassKg > 0.0) || !Double.isFinite(trial.incomingEnergyJ)) {
      throw new IllegalStateException("Algebraic junction '" + junction.name
          + "' requires positive incoming mass and finite energy in every donor iteration");
    }
    SystemInterface mixed = SerializationUtils.clone(previous);
    double[] molarFlow = new double[trial.componentNames.length];
    double previousTotalMass = 0.0;
    double[] previousMassFraction = new double[trial.componentNames.length];
    for (int index = 0; index < previousMassFraction.length; index++) {
      previousMassFraction[index] = previous.getComponent(index).getNumberOfmoles()
          * previous.getComponent(index).getMolarMass();
      previousTotalMass += previousMassFraction[index];
    }
    double correctedTotalMass = 0.0;
    for (int index = 0; index < molarFlow.length; index++) {
      double molarMass = mixed.getComponent(index).getMolarMass();
      double correctedMassKg = previousMassFraction[index] / previousTotalMass * trial.incomingMassKg
          + trial.componentResidualKg[index];
      correctedMassKg = Math.max(correctedMassKg, 1.0e-16 * trial.incomingMassKg);
      molarFlow[index] = correctedMassKg / molarMass;
      correctedTotalMass += correctedMassKg;
    }
    if (!(correctedTotalMass > 0.0)) {
      throw new IllegalStateException("Algebraic-junction donor correction removed every component");
    }
    mixed.setMolarFlowRates(molarFlow);
    mixed.setPressure(trial.pressurePa, "Pa");
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(mixed);
      operations.TPflash();
      double correctedSpecificEnthalpyJPerKg = previous.getEnthalpy("J/kg")
          + trial.energyResidualJ / trial.incomingMassKg;
      operations.PHflash(correctedSpecificEnthalpyJPerKg, "J/kg");
      mixed.initProperties();
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Unable to recover algebraic-junction donor state", exception);
    }
    return mixed;
  }

  /** Test whether two donor iterates have the same composition and caloric state. */
  private static boolean algebraicDonorConverged(SystemInterface previous, SystemInterface next) {
    double maximumCompositionDifference = 0.0;
    double previousMass = 0.0;
    double nextMass = 0.0;
    double[] previousComponentMass = new double[previous.getNumberOfComponents()];
    double[] nextComponentMass = new double[next.getNumberOfComponents()];
    for (int index = 0; index < previousComponentMass.length; index++) {
      previousComponentMass[index] = previous.getComponent(index).getNumberOfmoles()
          * previous.getComponent(index).getMolarMass();
      nextComponentMass[index] = next.getComponent(index).getNumberOfmoles() * next.getComponent(index).getMolarMass();
      previousMass += previousComponentMass[index];
      nextMass += nextComponentMass[index];
    }
    for (int index = 0; index < previousComponentMass.length; index++) {
      maximumCompositionDifference = Math.max(maximumCompositionDifference,
          Math.abs(previousComponentMass[index] / previousMass - nextComponentMass[index] / nextMass));
    }
    double enthalpyScale = Math.max(Math.abs(next.getEnthalpy("J/kg")), 1.0);
    double relativeEnthalpyDifference = Math.abs(previous.getEnthalpy("J/kg") - next.getEnthalpy("J/kg"))
        / enthalpyScale;
    return maximumCompositionDifference <= ALGEBRAIC_TRANSFER_TOLERANCE
        && relativeEnthalpyDifference <= ALGEBRAIC_TRANSFER_TOLERANCE;
  }

  /** Under-relax one coupled donor update to avoid composition/enthalpy exchange oscillation. */
  private static SystemInterface relaxAlgebraicDonor(SystemInterface previous, SystemInterface recovered) {
    SystemInterface relaxed = SerializationUtils.clone(previous);
    double previousTotalMass = 0.0;
    double recoveredTotalMass = 0.0;
    double[] previousMass = new double[previous.getNumberOfComponents()];
    double[] recoveredMass = new double[recovered.getNumberOfComponents()];
    for (int index = 0; index < previousMass.length; index++) {
      previousMass[index] = previous.getComponent(index).getNumberOfmoles()
          * previous.getComponent(index).getMolarMass();
      recoveredMass[index] = recovered.getComponent(index).getNumberOfmoles()
          * recovered.getComponent(index).getMolarMass();
      previousTotalMass += previousMass[index];
      recoveredTotalMass += recoveredMass[index];
    }
    double[] molarFlow = new double[previousMass.length];
    for (int index = 0; index < molarFlow.length; index++) {
      double massFraction = (1.0 - ALGEBRAIC_DONOR_RELAXATION) * previousMass[index] / previousTotalMass
          + ALGEBRAIC_DONOR_RELAXATION * recoveredMass[index] / recoveredTotalMass;
      molarFlow[index] = massFraction / relaxed.getComponent(index).getMolarMass();
    }
    relaxed.setMolarFlowRates(molarFlow);
    relaxed.setPressure(recovered.getPressure("Pa"), "Pa");
    ThermodynamicOperations operations = new ThermodynamicOperations(relaxed);
    operations.TPflash();
    double enthalpyJPerKg = (1.0 - ALGEBRAIC_DONOR_RELAXATION) * previous.getEnthalpy("J/kg")
        + ALGEBRAIC_DONOR_RELAXATION * recovered.getEnthalpy("J/kg");
    operations.PHflash(enthalpyJPerKg, "J/kg");
    relaxed.initProperties();
    return relaxed;
  }

  /** Map the network phase index to the component-ledger phase identity. */
  private static TwoFluidComponentConservationReport.Phase componentPhase(int index) {
    switch (index) {
    case 0:
      return TwoFluidComponentConservationReport.Phase.GAS;
    case 1:
      return TwoFluidComponentConservationReport.Phase.OIL;
    case 2:
      return TwoFluidComponentConservationReport.Phase.WATER;
    default:
      throw new IllegalArgumentException("Unsupported phase index " + index);
    }
  }

  /** Map the network phase index to the energy-ledger phase identity. */
  private static TwoFluidConservativeEnergyReport.Phase energyPhase(int index) {
    switch (index) {
    case 0:
      return TwoFluidConservativeEnergyReport.Phase.GAS;
    case 1:
      return TwoFluidConservativeEnergyReport.Phase.OIL;
    case 2:
      return TwoFluidConservativeEnergyReport.Phase.WATER;
    default:
      throw new IllegalArgumentException("Unsupported phase index " + index);
    }
  }

  /** Reject a staged interval before publication when its whole-network ledgers do not close. */
  private static void validateCandidateClosure(BalanceReport balanceReport,
      ThermodynamicBalanceReport thermodynamicBalanceReport) {
    Phase[] independentAggregations = {Phase.GAS, Phase.OIL, Phase.WATER, Phase.TOTAL};
    for (Phase aggregation : independentAggregations) {
      double residual = balanceReport.getRelativeResidual(aggregation);
      if (!Double.isFinite(residual) || (residual > MASS_CLOSURE_TOLERANCE
          && Math.abs(balanceReport.getResidualKg(aggregation)) > MASS_CLOSURE_ABSOLUTE_KG)) {
        throw new IllegalStateException(
            "Staged two-fluid network " + aggregation + " mass residual exceeds tolerance: " + residual);
      }
    }
    if (thermodynamicBalanceReport == null) {
      return;
    }
    for (String componentName : thermodynamicBalanceReport.getComponentNames()) {
      double residual = thermodynamicBalanceReport.getRelativeComponentResidual(componentName);
      if (!Double.isFinite(residual) || (residual > COMPONENT_CLOSURE_TOLERANCE && Math
          .abs(thermodynamicBalanceReport.getComponentResidualKg(componentName)) > COMPONENT_CLOSURE_ABSOLUTE_KG)) {
        throw new IllegalStateException(
            "Staged two-fluid network component residual exceeds tolerance for '" + componentName + "': " + residual);
      }
    }
    double energyResidual = thermodynamicBalanceReport.getRelativeEnergyResidual();
    if (!Double.isFinite(energyResidual) || (energyResidual > ENERGY_CLOSURE_TOLERANCE
        && Math.abs(thermodynamicBalanceReport.getEnergyResidualJ()) > ENERGY_CLOSURE_ABSOLUTE_J)) {
      throw new IllegalStateException("Staged two-fluid network energy residual exceeds tolerance: " + energyResidual);
    }
  }

  private boolean hasConservativeNodes() {
    for (NetworkNode node : nodes.values()) {
      if (node.thermodynamicInventory != null || node.algebraic) {
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
      if (node.algebraic) {
        return node.algebraicFluid.getComponentNames();
      }
    }
    return new String[0];
  }

  private void validateConservativeNetwork() {
    String[] names = conservativeComponentNames();
    for (NetworkNode node : nodes.values()) {
      if (node.algebraic) {
        if (!Arrays.equals(names, node.algebraicFluid.getComponentNames())) {
          throw new IllegalStateException("Algebraic junction '" + node.name + "' has a different component slate");
        }
      } else if (!node.fixedPressure) {
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
        SystemInterface boundaryFluid = branch.fromNode.getBoundaryFluid();
        boundaryFluid.setPressure(branch.fromNode.getPressurePa(), "Pa");
        branch.pipe.getInletStream().setFluid(boundaryFluid);
      }
      publishOutletBoundaryState(branch, branch.pipe.isOutletPhaseBackflowAllowed());
    }
  }

  /** Publish the accepted upstream thermodynamic state before steady initialization. */
  private void publishInletBoundaryState(NetworkBranch branch) {
    if (branch.fromNode.algebraic) {
      SystemInterface boundaryFluid = branch.fromNode.getBoundaryFluid();
      boundaryFluid.setPressure(branch.fromNode.getPressurePa(), "Pa");
      branch.pipe.getInletStream().setFluid(boundaryFluid);
      branch.pipe.setInletBoundaryFluid(boundaryFluid);
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

  /**
   * Get node names in deterministic registration order.
   *
   * @return immutable node-name set
   */
  public Set<String> getNodeNames() {
    return java.util.Collections.unmodifiableSet(new LinkedHashSet<String>(nodes.keySet()));
  }

  /**
   * Get branch names in deterministic registration order.
   *
   * @return immutable branch-name set
   */
  public Set<String> getBranchNames() {
    return java.util.Collections.unmodifiableSet(new LinkedHashSet<String>(branches.keySet()));
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

  /**
   * Get the latest algebraic-junction solve report.
   *
   * @return latest accepted report, or {@code null} when the network has no algebraic junction
   */
  public AlgebraicJunctionReport getLastAlgebraicJunctionReport() {
    return lastAlgebraicJunctionReport;
  }

  /**
   * Get the latest accepted report for one named algebraic junction.
   *
   * @param nodeName algebraic-junction node name
   * @return latest accepted report
   * @throws IllegalStateException if no accepted report exists for the node
   */
  public AlgebraicJunctionReport getLastAlgebraicJunctionReport(String nodeName) {
    AlgebraicJunctionReport report = lastAlgebraicJunctionReports.get(nodeName);
    if (report == null) {
      throw new IllegalStateException("No accepted algebraic-junction report exists for node '" + nodeName + "'");
    }
    return report;
  }

  /**
   * Get the deterministic junction order represented by the latest accepted interval.
   *
   * @return algebraic-junction names in registration order
   */
  public String[] getLastAlgebraicJunctionNames() {
    return lastAlgebraicJunctionReports.keySet().toArray(new String[lastAlgebraicJunctionReports.size()]);
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
    if (node.algebraic) {
      return node.algebraicFluid.getTemperature("K");
    }
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

  /**
   * Get one component mass fraction in the accepted algebraic-junction donor state.
   *
   * @param nodeName algebraic node name
   * @param componentName component name
   * @return accepted overall component mass fraction
   */
  public double getAlgebraicNodeComponentMassFraction(String nodeName, String componentName) {
    NetworkNode node = requireNode(nodeName);
    if (!node.algebraic) {
      throw new IllegalStateException("Node '" + nodeName + "' is not algebraic");
    }
    double totalMass = 0.0;
    double componentMass = 0.0;
    for (int index = 0; index < node.algebraicFluid.getNumberOfComponents(); index++) {
      double mass = node.algebraicFluid.getComponent(index).getNumberOfmoles()
          * node.algebraicFluid.getComponent(index).getMolarMass();
      totalMass += mass;
      if (node.algebraicFluid.getComponent(index).getComponentName().equals(componentName)) {
        componentMass = mass;
      }
    }
    if (!(totalMass > 0.0)) {
      throw new IllegalStateException("Algebraic junction has no positive component mass");
    }
    return componentMass / totalMass;
  }

  /** Private accepted result of one algebraic solve. */
  private static final class AlgebraicSolution {
    private final NetworkNode node;
    private final double pressurePa;
    private final SystemInterface fluid;
    private final Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates;
    private final AlgebraicJunctionReport report;

    private AlgebraicSolution(NetworkNode node, double pressurePa, SystemInterface fluid,
        Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates, AlgebraicJunctionReport report) {
      this.node = node;
      this.pressurePa = pressurePa;
      this.fluid = fluid;
      this.branchCandidates = branchCandidates;
      this.report = report;
    }
  }

  /** Private accepted pressure and donor state for one algebraic junction. */
  private static final class AlgebraicAcceptedState {
    private final double pressurePa;
    private final SystemInterface fluid;

    private AlgebraicAcceptedState(double pressurePa, SystemInterface fluid) {
      this.pressurePa = pressurePa;
      this.fluid = fluid;
    }
  }

  /** Private accepted result of one coupled algebraic-network solve. */
  private static final class AlgebraicNetworkSolution {
    private final Map<NetworkNode, AlgebraicAcceptedState> states;
    private final Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates;
    private final Map<String, AlgebraicJunctionReport> reports;

    private AlgebraicNetworkSolution(Map<NetworkNode, AlgebraicAcceptedState> states,
        Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates,
        Map<String, AlgebraicJunctionReport> reports) {
      this.states = states;
      this.branchCandidates = branchCandidates;
      this.reports = reports;
    }

    private static AlgebraicNetworkSolution single(AlgebraicSolution solution) {
      Map<NetworkNode, AlgebraicAcceptedState> states = new LinkedHashMap<NetworkNode, AlgebraicAcceptedState>();
      states.put(solution.node, new AlgebraicAcceptedState(solution.pressurePa, solution.fluid));
      Map<String, AlgebraicJunctionReport> reports = new LinkedHashMap<String, AlgebraicJunctionReport>();
      reports.put(solution.node.name, solution.report);
      return new AlgebraicNetworkSolution(states, solution.branchCandidates, reports);
    }
  }

  /** Mutable accumulator used only while evaluating one detached coupled pressure vector. */
  private static final class AlgebraicTrialAccumulator {
    private final String[] componentNames;
    private final double[] phaseResidualKg = new double[PHASE_COUNT];
    private final double[] componentResidualKg;
    private double energyResidualJ;
    private double incomingMassKg;
    private double incomingEnergyJ;
    private double transferScaleKg;
    private double energyScaleJ;

    private AlgebraicTrialAccumulator(String[] componentNames) {
      this.componentNames = componentNames.clone();
      this.componentResidualKg = new double[componentNames.length];
    }

    private void addBoundary(NetworkBranch branch, NetworkNode junction, TwoFluidPipe.TransientCandidate candidate) {
      TwoFluidMassBalanceReport mass = candidate.getMassBalanceReport();
      TwoFluidComponentConservationReport components = candidate.getComponentConservationReport();
      TwoFluidConservativeEnergyReport energy = candidate.getConservativeEnergyReport();
      for (int phaseIndex = 0; phaseIndex < PHASE_COUNT; phaseIndex++) {
        Phase aggregation = phase(phaseIndex);
        double branchMassKg = branch.fromNode == junction ? -mass.getInletMassKg(aggregation)
            : mass.getOutletMassKg(aggregation);
        double[] branchComponentKg = algebraicBoundaryComponents(branch, junction, components, phaseIndex);
        double branchEnergyJ = algebraicBoundaryEnergy(branch, junction, energy, phaseIndex);
        phaseResidualKg[phaseIndex] += branchMassKg;
        add(componentResidualKg, branchComponentKg, 1.0);
        energyResidualJ += branchEnergyJ;
        transferScaleKg += Math.abs(branchMassKg);
        energyScaleJ += Math.abs(branchEnergyJ);
        if (branchMassKg > 0.0) {
          incomingMassKg += branchMassKg;
          incomingEnergyJ += branchEnergyJ;
        }
      }
    }

    private AlgebraicTrial toTrial(double pressurePa, int pressureIterations,
        Map<NetworkBranch, TwoFluidPipe.TransientCandidate> candidates) {
      double totalMassResidualKg = phaseResidualKg[0] + phaseResidualKg[1] + phaseResidualKg[2];
      return new AlgebraicTrial(pressurePa, pressureIterations, candidates, componentNames, phaseResidualKg,
          componentResidualKg, energyResidualJ, totalMassResidualKg, incomingMassKg, incomingEnergyJ, transferScaleKg,
          energyScaleJ);
    }
  }

  /** Private residual bundle for one global coupled pressure vector. */
  private static final class AlgebraicNetworkTrial {
    private final double[] pressuresPa;
    private final int pressureIterations;
    private final Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates;
    private final Map<NetworkNode, AlgebraicTrial> nodeTrials;
    private final List<NetworkNode> junctions;

    private AlgebraicNetworkTrial(double[] pressuresPa, int pressureIterations,
        Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates,
        Map<NetworkNode, AlgebraicTrial> nodeTrials, List<NetworkNode> junctions) {
      this.pressuresPa = pressuresPa.clone();
      this.pressureIterations = pressureIterations;
      this.branchCandidates = branchCandidates;
      this.nodeTrials = nodeTrials;
      this.junctions = new ArrayList<NetworkNode>(junctions);
    }

    private double[] massResidualVector() {
      double[] residual = new double[junctions.size()];
      for (int index = 0; index < junctions.size(); index++) {
        residual[index] = nodeTrials.get(junctions.get(index)).totalMassResidualKg;
      }
      return residual;
    }

    private boolean pressureCloses() {
      for (NetworkNode junction : junctions) {
        if (!nodeTrials.get(junction).pressureCloses()) {
          return false;
        }
      }
      return true;
    }

    private double pressureResidualNorm() {
      double maximum = 0.0;
      for (NetworkNode junction : junctions) {
        AlgebraicTrial trial = nodeTrials.get(junction);
        double scale = MASS_CLOSURE_ABSOLUTE_KG + ALGEBRAIC_TRANSFER_TOLERANCE * trial.transferScaleKg;
        maximum = Math.max(maximum, Math.abs(trial.totalMassResidualKg) / scale);
      }
      return maximum;
    }
  }

  /** Private residual bundle for one detached algebraic trial. */
  private static final class AlgebraicTrial {
    private final double pressurePa;
    private final int pressureIterations;
    private final Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates;
    private final String[] componentNames;
    private final double[] phaseResidualKg;
    private final double[] componentResidualKg;
    private final double energyResidualJ;
    private final double totalMassResidualKg;
    private final double incomingMassKg;
    private final double incomingEnergyJ;
    private final double transferScaleKg;
    private final double energyScaleJ;

    private AlgebraicTrial(double pressurePa, int pressureIterations,
        Map<NetworkBranch, TwoFluidPipe.TransientCandidate> branchCandidates, String[] componentNames,
        double[] phaseResidualKg, double[] componentResidualKg, double energyResidualJ, double totalMassResidualKg,
        double incomingMassKg, double incomingEnergyJ, double transferScaleKg, double energyScaleJ) {
      this.pressurePa = pressurePa;
      this.pressureIterations = pressureIterations;
      this.branchCandidates = branchCandidates;
      this.componentNames = componentNames;
      this.phaseResidualKg = phaseResidualKg;
      this.componentResidualKg = componentResidualKg;
      this.energyResidualJ = energyResidualJ;
      this.totalMassResidualKg = totalMassResidualKg;
      this.incomingMassKg = incomingMassKg;
      this.incomingEnergyJ = incomingEnergyJ;
      this.transferScaleKg = transferScaleKg;
      this.energyScaleJ = energyScaleJ;
    }

    private boolean pressureCloses() {
      return Math.abs(totalMassResidualKg) <= MASS_CLOSURE_ABSOLUTE_KG + ALGEBRAIC_TRANSFER_TOLERANCE * transferScaleKg;
    }

    private boolean closes() {
      if (!pressureCloses()) {
        return false;
      }
      for (double residualKg : componentResidualKg) {
        if (Math.abs(residualKg) > COMPONENT_CLOSURE_ABSOLUTE_KG + ALGEBRAIC_TRANSFER_TOLERANCE * transferScaleKg) {
          return false;
        }
      }
      return Math.abs(energyResidualJ) <= ENERGY_CLOSURE_ABSOLUTE_J + ALGEBRAIC_TRANSFER_TOLERANCE * energyScaleJ;
    }

    private void requireClosure(String junctionName) {
      if (!closes()) {
        throw new IllegalStateException("Algebraic junction '" + junctionName
            + "' did not close total/component/energy ledgers: raw phase=" + Arrays.toString(phaseResidualKg)
            + " kg, component=" + Arrays.toString(componentResidualKg) + " kg, energy=" + energyResidualJ + " J");
      }
    }
  }

  /**
   * Immutable convergence and conservation receipt for the latest accepted algebraic-junction interval.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class AlgebraicJunctionReport implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String nodeName;
    private final double pressurePa;
    private final int thermodynamicIterations;
    private final int pressureIterations;
    private final double[] rawPhaseResidualKg;
    private final double[] interphaseTransferKg;
    private final String[] componentNames;
    private final double[] componentResidualKg;
    private final double energyResidualJ;
    private final double transferScaleKg;
    private final double energyScaleJ;

    private AlgebraicJunctionReport(String nodeName, double pressurePa, int thermodynamicIterations,
        int pressureIterations, double[] phaseResidualKg, String[] componentNames, double[] componentResidualKg,
        double energyResidualJ, double transferScaleKg, double energyScaleJ) {
      this.nodeName = nodeName;
      this.pressurePa = pressurePa;
      this.thermodynamicIterations = thermodynamicIterations;
      this.pressureIterations = pressureIterations;
      this.rawPhaseResidualKg = phaseResidualKg.clone();
      this.interphaseTransferKg = new double[phaseResidualKg.length];
      for (int index = 0; index < phaseResidualKg.length; index++) {
        interphaseTransferKg[index] = -phaseResidualKg[index];
      }
      this.componentNames = componentNames.clone();
      this.componentResidualKg = componentResidualKg.clone();
      this.energyResidualJ = energyResidualJ;
      this.transferScaleKg = transferScaleKg;
      this.energyScaleJ = energyScaleJ;
    }

    /** @return algebraic node name */
    public String getNodeName() {
      return nodeName;
    }

    /** @return accepted common pressure in Pa */
    public double getPressurePa() {
      return pressurePa;
    }

    /** @return number of mixed-donor iterations */
    public int getThermodynamicIterations() {
      return thermodynamicIterations;
    }

    /** @return number of safeguarded scalar-pressure iterations in the accepted donor iteration */
    public int getPressureIterations() {
      return pressureIterations;
    }

    /**
     * Get the phase closure residual after the explicitly reported equilibrium interphase transfer.
     *
     * @param aggregation gas, oil, water, or total
     * @return mass entering minus mass leaving in kg
     */
    public double getPhaseResidualKg(Phase aggregation) {
      double[] closureResidualKg = new double[PHASE_COUNT];
      for (int index = 0; index < closureResidualKg.length; index++) {
        closureResidualKg[index] = rawPhaseResidualKg[index] + interphaseTransferKg[index];
      }
      return BalanceReport.aggregate(closureResidualKg, aggregation);
    }

    /**
     * Get the equilibrium phase conversion required by the zero-volume mixer.
     *
     * <p>
     * Positive values add mass to the requested hydrodynamic phase and negative values remove it. The total across gas,
     * oil, and water is zero within the common-pressure mass tolerance; named components and energy remain
     * independently conserved.
     * </p>
     *
     * @param aggregation gas, oil, water, liquid, or total
     * @return interval-integrated interphase transfer in kg
     */
    public double getInterphaseTransferKg(Phase aggregation) {
      return BalanceReport.aggregate(interphaseTransferKg, aggregation);
    }

    /** @return deterministic component order */
    public String[] getComponentNames() {
      return componentNames.clone();
    }

    /**
     * Get one zero-volume named-component residual.
     *
     * @param componentName component name
     * @return mass entering minus mass leaving in kg
     */
    public double getComponentResidualKg(String componentName) {
      for (int index = 0; index < componentNames.length; index++) {
        if (componentNames[index].equals(componentName)) {
          return componentResidualKg[index];
        }
      }
      throw new IllegalArgumentException("Component '" + componentName + "' is not in the junction slate");
    }

    /** @return energy entering minus energy leaving in J */
    public double getEnergyResidualJ() {
      return energyResidualJ;
    }

    /** @return maximum relative phase residual on the accepted transfer scale */
    public double getMaximumRelativePhaseResidual() {
      double maximum = 0.0;
      for (int index = 0; index < rawPhaseResidualKg.length; index++) {
        double closureResidualKg = rawPhaseResidualKg[index] + interphaseTransferKg[index];
        maximum = Math.max(maximum, Math.abs(closureResidualKg) / Math.max(transferScaleKg, 1.0e-30));
      }
      return maximum;
    }

    /** @return maximum relative component residual on the accepted transfer scale */
    public double getMaximumRelativeComponentResidual() {
      double maximum = 0.0;
      for (double value : componentResidualKg) {
        maximum = Math.max(maximum, Math.abs(value) / Math.max(transferScaleKg, 1.0e-30));
      }
      return maximum;
    }

    /** @return absolute relative energy residual on the accepted advected-energy scale */
    public double getRelativeEnergyResidual() {
      return Math.abs(energyResidualJ) / Math.max(energyScaleJ, 1.0e-30);
    }
  }

  private static final class NetworkNode implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final boolean fixedPressure;
    private final double fixedPressurePa;
    private final boolean algebraic;
    private final UpstreamCompressibleVolume volume;
    private final ThermodynamicInventory thermodynamicInventory;
    private final SystemInterface fixedBoundaryFluid;
    private double algebraicPressurePa;
    private SystemInterface algebraicFluid;
    private NodeSourceThermodynamics sourceThermodynamics;
    private int incomingCount;
    private int outgoingCount;

    private NetworkNode(String name, boolean fixedPressure, double fixedPressurePa, boolean algebraic,
        UpstreamCompressibleVolume volume, ThermodynamicInventory thermodynamicInventory,
        SystemInterface fixedBoundaryFluid, double algebraicPressurePa, SystemInterface algebraicFluid) {
      this.name = name;
      this.fixedPressure = fixedPressure;
      this.fixedPressurePa = fixedPressurePa;
      this.algebraic = algebraic;
      this.volume = volume;
      this.thermodynamicInventory = thermodynamicInventory;
      this.fixedBoundaryFluid = fixedBoundaryFluid;
      this.algebraicPressurePa = algebraicPressurePa;
      this.algebraicFluid = algebraicFluid;
    }

    private static NetworkNode fixed(String name, double pressurePa) {
      return new NetworkNode(name, true, pressurePa, false, null, null, null, Double.NaN, null);
    }

    private static NetworkNode fixed(String name, double pressurePa, SystemInterface boundaryFluid) {
      return new NetworkNode(name, true, pressurePa, false, null, null, boundaryFluid.clone(), Double.NaN, null);
    }

    private static NetworkNode compressible(String name, UpstreamCompressibleVolume volume) {
      return new NetworkNode(name, false, Double.NaN, false, volume, null, null, Double.NaN, null);
    }

    private static NetworkNode conservative(String name, UpstreamCompressibleVolume volume,
        SystemInterface initialFluid) {
      return new NetworkNode(name, false, Double.NaN, false, volume,
          new ThermodynamicInventory(initialFluid, volume.getTotalMassKg(), volume.getPressurePa()), null, Double.NaN,
          null);
    }

    private static NetworkNode algebraic(String name, double pressurePa, SystemInterface initialFluid) {
      SystemInterface fluid = SerializationUtils.clone(initialFluid);
      fluid.setPressure(pressurePa, "Pa");
      new ThermodynamicOperations(fluid).TPflash();
      fluid.initProperties();
      return new NetworkNode(name, false, Double.NaN, true, null, null, null, pressurePa, fluid);
    }

    private double getPressurePa() {
      return fixedPressure ? fixedPressurePa : algebraic ? algebraicPressurePa : volume.getPressurePa();
    }

    private boolean hasStorage() {
      return !fixedPressure && !algebraic;
    }

    private double getTotalSourceMassFlowRateKgS() {
      double totalKgS = 0.0;
      for (int phase = 0; phase < PHASE_COUNT; phase++) {
        totalKgS += volume.getSourceMassFlowRateKgS(phase);
      }
      return totalKgS;
    }

    private SystemInterface getBoundaryFluid() {
      if (algebraic) {
        return algebraicFluid.clone();
      }
      if (thermodynamicInventory != null) {
        return thermodynamicInventory.fluid.clone();
      }
      return fixedBoundaryFluid == null ? null : fixedBoundaryFluid.clone();
    }

    private void acceptAlgebraicState(double pressurePa, SystemInterface fluid) {
      algebraicPressurePa = pressurePa;
      algebraicFluid = fluid;
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
    private SystemInterface fluid;
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

    /** Validate one privately advanced inventory before the network commits any live object. */
    private void validateCandidateState(ThermodynamicInventory candidate) {
      if (candidate == null || !Arrays.equals(componentNames, candidate.componentNames)
          || !Arrays.equals(molarMassKgPerMol, candidate.molarMassKgPerMol) || candidate.fluid == null
          || candidate.componentMassKg.length != componentMassKg.length
          || !Double.isFinite(candidate.internalEnergyJ)) {
        throw new IllegalStateException("Thermodynamic-node candidate must retain its component configuration");
      }
      for (double componentMass : candidate.componentMassKg) {
        if (!Double.isFinite(componentMass) || componentMass < 0.0) {
          throw new IllegalStateException("Thermodynamic-node candidate requires finite nonnegative inventory");
        }
      }
      if (!Double.isFinite(candidate.fluid.getPressure("Pa")) || candidate.fluid.getPressure("Pa") <= 0.0
          || !Double.isFinite(candidate.fluid.getTemperature("K")) || candidate.fluid.getTemperature("K") <= 0.0) {
        throw new IllegalStateException("Thermodynamic-node candidate requires finite pressure and temperature");
      }
    }

    /** Install a validated candidate without another flash or user-extensible operation. */
    private void acceptCandidateState(ThermodynamicInventory candidate) {
      System.arraycopy(candidate.componentMassKg, 0, componentMassKg, 0, componentMassKg.length);
      internalEnergyJ = candidate.internalEnergyJ;
      fluid = candidate.fluid;
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
    private int subcycles = 1;

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
    private final double[] internalPhaseTransferKg;

    private BalanceReport(double elapsedTimeSeconds, double[] initialMassKg, double[] finalMassKg,
        double[] externalInletMassKg, double[] externalOutletMassKg, double[] sourceMassKg,
        double[] internalPhaseTransferKg) {
      this.elapsedTimeSeconds = elapsedTimeSeconds;
      this.initialMassKg = initialMassKg.clone();
      this.finalMassKg = finalMassKg.clone();
      this.externalInletMassKg = externalInletMassKg.clone();
      this.externalOutletMassKg = externalOutletMassKg.clone();
      this.sourceMassKg = sourceMassKg.clone();
      this.internalPhaseTransferKg = internalPhaseTransferKg.clone();
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

    /**
     * Get phase conversion at algebraic equilibrium junctions.
     *
     * @param aggregation requested phase aggregation
     * @return signed internal interphase transfer in kg
     */
    public double getInternalPhaseTransferKg(Phase aggregation) {
      return aggregate(internalPhaseTransferKg, aggregation);
    }

    public double getResidualKg(Phase aggregation) {
      return getFinalMassKg(aggregation) - getInitialMassKg(aggregation)
          - (getExternalInletMassKg(aggregation) - getExternalOutletMassKg(aggregation) + getSourceMassKg(aggregation)
              + getInternalPhaseTransferKg(aggregation));
    }

    public double getRelativeResidual(Phase aggregation) {
      double scale = Math.max(Math.abs(getInitialMassKg(aggregation)), Math.abs(getFinalMassKg(aggregation)));
      scale = Math.max(scale,
          Math.abs(getExternalInletMassKg(aggregation)) + Math.abs(getExternalOutletMassKg(aggregation))
              + Math.abs(getSourceMassKg(aggregation)) + Math.abs(getInternalPhaseTransferKg(aggregation)));
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
