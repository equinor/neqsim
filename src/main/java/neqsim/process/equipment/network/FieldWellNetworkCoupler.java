package neqsim.process.equipment.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.network.FieldNetworkTopology.FieldNode;
import neqsim.process.equipment.network.FieldNetworkTopology.NodeRole;
import neqsim.process.equipment.network.FieldNetworkTopology.Service;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.reservoir.WellSystem;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Couples live NeqSim well deliverability or injectivity calculations to a canonical field network.
 *
 * <p>
 * The coupler does not create another graph or hydraulic model. It iterates the pressure-rate boundary condition of
 * existing {@link WellSystem} and {@link WellFlow} equipment against the nodes of the same {@link LoopedPipeNetwork}
 * wrapped by {@link FieldNetworkTopology}. The network continues to own conservative junction balances and edge
 * hydraulics. Production wells enter the graph as negative node demand; injection wells leave the graph as positive
 * node demand.
 * </p>
 *
 * <p>
 * Runtime well bindings are deliberately not serialized with the topology definition. Replayed field definitions must
 * rebind their live wells and fluids before execution, consistent with
 * {@link FieldNetworkTopology#bindEquipment(String, ProcessEquipmentInterface)}.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FieldWellNetworkCoupler {
  /** Operating classification for a coupled well. */
  public enum WellStatus {
    /** The well is flowing and its live calculation satisfies configured limits. */
    FLOWING,
    /** The well has no physically available rate at the solved pressure. */
    SHUT_IN,
    /** The live WellSystem IPR/VLP intersection did not converge. */
    INNER_WELL_NOT_CONVERGED,
    /** A configured production-well BHP or drawdown limit is violated. */
    BHP_LIMIT_EXCEEDED,
    /** One or more injection zones exceed their fracture-pressure limit. */
    FRACTURE_LIMIT_EXCEEDED,
    /** The injection fluid and canonical network fluid use incompatible thermodynamic definitions. */
    FLUID_INCOMPATIBLE,
    /** The bound equipment could not be evaluated at the network pressure. */
    EVALUATION_FAILED
  }

  /** Type-safe runtime binding between a field node and existing well equipment. */
  public static final class WellBinding {
    private final String nodeId;
    private final Service service;
    private final WellSystem wellSystem;
    private final WellFlow wellFlow;

    /**
     * Create a runtime well binding.
     *
     * @param nodeId canonical field/hydraulic node identity
     * @param service production or injection service
     * @param wellSystem bound integrated production well, or null
     * @param wellFlow bound production/injection inflow equipment, or null
     */
    private WellBinding(String nodeId, Service service, WellSystem wellSystem, WellFlow wellFlow) {
      this.nodeId = nodeId;
      this.service = service;
      this.wellSystem = wellSystem;
      this.wellFlow = wellFlow;
    }

    /**
     * Get the canonical node identity.
     *
     * @return node identity
     */
    public String getNodeId() {
      return nodeId;
    }

    /**
     * Get production or injection service.
     *
     * @return well service
     */
    public Service getService() {
      return service;
    }

    /**
     * Get the bound integrated production well.
     *
     * @return WellSystem, or null for a WellFlow binding
     */
    public WellSystem getWellSystem() {
      return wellSystem;
    }

    /**
     * Get the bound inflow/injectivity equipment.
     *
     * @return WellFlow, or null for a WellSystem binding
     */
    public WellFlow getWellFlow() {
      return wellFlow;
    }
  }

  /** Immutable evidence for one well at the end of a coupling iteration. */
  public static final class WellResult {
    private final String nodeId;
    private final Service service;
    private final WellStatus status;
    private final double nodePressureBara;
    private final double targetRateKgS;
    private final double appliedRateKgS;
    private final double rateResidualKgS;
    private final boolean innerConverged;
    private final double innerPressureResidualBar;
    private final int innerIterations;
    private final String message;

    /**
     * Create one immutable well result.
     *
     * @param nodeId canonical well node
     * @param service production or injection service
     * @param status operating classification
     * @param nodePressureBara solved well-node pressure in bara
     * @param targetRateKgS live well target mass rate in kg/s
     * @param appliedRateKgS rate applied to network mass balance in kg/s
     * @param rateResidualKgS absolute target/applied rate residual in kg/s
     * @param innerConverged true when the inner well contract is satisfied
     * @param innerPressureResidualBar signed WellSystem IPR/VLP pressure residual in bar, or zero for direct WellFlow
     * @param innerIterations WellSystem iterations, or one for direct WellFlow
     * @param message diagnostic message
     */
    private WellResult(String nodeId, Service service, WellStatus status, double nodePressureBara, double targetRateKgS,
        double appliedRateKgS, double rateResidualKgS, boolean innerConverged, double innerPressureResidualBar,
        int innerIterations, String message) {
      this.nodeId = nodeId;
      this.service = service;
      this.status = status;
      this.nodePressureBara = nodePressureBara;
      this.targetRateKgS = targetRateKgS;
      this.appliedRateKgS = appliedRateKgS;
      this.rateResidualKgS = rateResidualKgS;
      this.innerConverged = innerConverged;
      this.innerPressureResidualBar = innerPressureResidualBar;
      this.innerIterations = innerIterations;
      this.message = message;
    }

    /** @return canonical well-node identity */
    public String getNodeId() {
      return nodeId;
    }

    /** @return production or injection service */
    public Service getService() {
      return service;
    }

    /** @return operating status */
    public WellStatus getStatus() {
      return status;
    }

    /** @return solved well-node pressure in bara */
    public double getNodePressureBara() {
      return nodePressureBara;
    }

    /** @return live well target mass rate in kg/s */
    public double getTargetRateKgS() {
      return targetRateKgS;
    }

    /** @return mass rate applied to the conservative network balance in kg/s */
    public double getAppliedRateKgS() {
      return appliedRateKgS;
    }

    /** @return absolute target/applied mass-rate residual in kg/s */
    public double getRateResidualKgS() {
      return rateResidualKgS;
    }

    /** @return true when the live inner well calculation is accepted */
    public boolean isInnerConverged() {
      return innerConverged;
    }

    /** @return signed inner IPR/VLP pressure residual in bar */
    public double getInnerPressureResidualBar() {
      return innerPressureResidualBar;
    }

    /** @return iterations used by the live inner well calculation */
    public int getInnerIterations() {
      return innerIterations;
    }

    /** @return diagnostic message */
    public String getMessage() {
      return message;
    }
  }

  /** Immutable report for the coupled well/network solve. */
  public static final class CouplingResult {
    private final boolean converged;
    private final int iterations;
    private final double maximumRateResidualKgS;
    private final double maximumPressureResidualBar;
    private final double networkResidualPa;
    private final double massBalanceResidualKgS;
    private final List<WellResult> wellResults;
    private final String message;

    /**
     * Create a coupled-solve report.
     *
     * @param converged true when network, outer coupling, and every inner well contract converged
     * @param iterations completed outer iterations
     * @param maximumRateResidualKgS maximum target/applied well-rate residual in kg/s
     * @param maximumPressureResidualBar maximum well-node pressure change in bar
     * @param networkResidualPa hydraulic solver residual in Pa
     * @param massBalanceResidualKgS conservative network mass-balance residual in kg/s
     * @param wellResults per-well evidence
     * @param message summary diagnostic
     */
    private CouplingResult(boolean converged, int iterations, double maximumRateResidualKgS,
        double maximumPressureResidualBar, double networkResidualPa, double massBalanceResidualKgS,
        List<WellResult> wellResults, String message) {
      this.converged = converged;
      this.iterations = iterations;
      this.maximumRateResidualKgS = maximumRateResidualKgS;
      this.maximumPressureResidualBar = maximumPressureResidualBar;
      this.networkResidualPa = networkResidualPa;
      this.massBalanceResidualKgS = massBalanceResidualKgS;
      this.wellResults = Collections.unmodifiableList(new ArrayList<WellResult>(wellResults));
      this.message = message;
    }

    /** @return true when the complete coupled solve converged */
    public boolean isConverged() {
      return converged;
    }

    /** @return completed outer iterations */
    public int getIterations() {
      return iterations;
    }

    /** @return maximum well-rate residual in kg/s */
    public double getMaximumRateResidualKgS() {
      return maximumRateResidualKgS;
    }

    /** @return maximum well-node pressure change in bar */
    public double getMaximumPressureResidualBar() {
      return maximumPressureResidualBar;
    }

    /** @return inner hydraulic network residual in Pa */
    public double getNetworkResidualPa() {
      return networkResidualPa;
    }

    /** @return network mass-balance residual in kg/s */
    public double getMassBalanceResidualKgS() {
      return massBalanceResidualKgS;
    }

    /** @return immutable per-well evidence */
    public List<WellResult> getWellResults() {
      return wellResults;
    }

    /** @return summary diagnostic */
    public String getMessage() {
      return message;
    }
  }

  /** Mutable evaluation used only inside one outer iteration. */
  private static final class Evaluation {
    private final double targetRateKgS;
    private final boolean innerConverged;
    private final double innerPressureResidualBar;
    private final int innerIterations;
    private final WellStatus status;
    private final String message;
    private final StreamInterface productionStream;

    /**
     * Create an internal well evaluation.
     *
     * @param targetRateKgS target mass rate in kg/s
     * @param innerConverged accepted inner state
     * @param innerPressureResidualBar inner pressure residual in bar
     * @param innerIterations inner iteration count
     * @param status operating status
     * @param message diagnostic message
     * @param productionStream production stream used for source composition, or null
     */
    private Evaluation(double targetRateKgS, boolean innerConverged, double innerPressureResidualBar,
        int innerIterations, WellStatus status, String message, StreamInterface productionStream) {
      this.targetRateKgS = targetRateKgS;
      this.innerConverged = innerConverged;
      this.innerPressureResidualBar = innerPressureResidualBar;
      this.innerIterations = innerIterations;
      this.status = status;
      this.message = message;
      this.productionStream = productionStream;
    }
  }

  private final FieldNetworkTopology topology;
  private final Map<String, WellBinding> bindings = new LinkedHashMap<String, WellBinding>();
  private int maximumIterations = 50;
  private double rateToleranceKgS = 1.0e-5;
  private double pressureToleranceBar = 1.0e-4;
  private double relaxationFactor = 0.5;
  private double shutInThresholdKgS = 1.0e-8;
  private CouplingResult lastResult;

  /**
   * Create a runtime well/network coupler for a canonical field definition.
   *
   * @param topology typed view over the hydraulic graph
   */
  public FieldWellNetworkCoupler(FieldNetworkTopology topology) {
    if (topology == null) {
      throw new IllegalArgumentException("Field topology cannot be null");
    }
    this.topology = topology;
  }

  /**
   * Get the canonical field topology used by this coupling calculation.
   *
   * @return bound field topology
   */
  public FieldNetworkTopology getTopology() {
    return topology;
  }

  /**
   * Copy the topology definition, live well bindings and coupling settings into an independent runtime calculation.
   *
   * <p>
   * The canonical network JSON intentionally excludes thermodynamic objects, so this method restores a cloned fluid
   * template before copying each bound well. Runtime results are not carried into the copy.
   * </p>
   *
   * @return independent configured coupler
   */
  public FieldWellNetworkCoupler copy() {
    FieldNetworkTopology copiedTopology = topology.copyDefinition();
    SystemInterface fluidTemplate = topology.getHydraulicNetwork().getFluidTemplate();
    if (fluidTemplate != null) {
      copiedTopology.getHydraulicNetwork().setFluidTemplate(fluidTemplate.clone());
    }
    FieldWellNetworkCoupler copied = new FieldWellNetworkCoupler(copiedTopology);
    copied.maximumIterations = maximumIterations;
    copied.rateToleranceKgS = rateToleranceKgS;
    copied.pressureToleranceBar = pressureToleranceBar;
    copied.relaxationFactor = relaxationFactor;
    copied.shutInThresholdKgS = shutInThresholdKgS;
    for (WellBinding binding : bindings.values()) {
      if (binding.wellSystem != null) {
        copied.bindProductionWell(binding.nodeId, (WellSystem) binding.wellSystem.copy());
      } else if (binding.service == Service.PRODUCTION) {
        copied.bindProductionWell(binding.nodeId, (WellFlow) binding.wellFlow.copy());
      } else {
        copied.bindInjectionWell(binding.nodeId, (WellFlow) binding.wellFlow.copy());
      }
    }
    return copied;
  }

  /**
   * Bind an integrated production {@link WellSystem} to a production-well node.
   *
   * @param nodeId canonical production-well node identity
   * @param well live integrated well
   * @return this coupler
   */
  public FieldWellNetworkCoupler bindProductionWell(String nodeId, WellSystem well) {
    if (well == null) {
      throw new IllegalArgumentException("WellSystem cannot be null");
    }
    return putBinding(nodeId, Service.PRODUCTION, well, null);
  }

  /**
   * Bind production {@link WellFlow} equipment to a production-well node.
   *
   * @param nodeId canonical production-well node identity
   * @param well live production inflow equipment
   * @return this coupler
   */
  public FieldWellNetworkCoupler bindProductionWell(String nodeId, WellFlow well) {
    if (well == null) {
      throw new IllegalArgumentException("WellFlow cannot be null");
    }
    if (well.getFlowMode() != WellFlow.FlowMode.PRODUCTION) {
      throw new IllegalArgumentException("Production binding requires WellFlow PRODUCTION mode");
    }
    return putBinding(nodeId, Service.PRODUCTION, null, well);
  }

  /**
   * Bind injection {@link WellFlow} equipment to an injection-well node.
   *
   * @param nodeId canonical injection-well node identity
   * @param well live injection allocation/injectivity equipment
   * @return this coupler
   */
  public FieldWellNetworkCoupler bindInjectionWell(String nodeId, WellFlow well) {
    if (well == null) {
      throw new IllegalArgumentException("WellFlow cannot be null");
    }
    if (well.getFlowMode() != WellFlow.FlowMode.INJECTION) {
      throw new IllegalArgumentException("Injection binding requires WellFlow INJECTION mode");
    }
    return putBinding(nodeId, Service.INJECTION, null, well);
  }

  /**
   * Get immutable runtime bindings in deterministic insertion order.
   *
   * @return well bindings
   */
  public List<WellBinding> getBindings() {
    return Collections.unmodifiableList(new ArrayList<WellBinding>(bindings.values()));
  }

  /**
   * Set the maximum number of outer pressure-rate iterations.
   *
   * @param maximumIterations maximum iterations, at least one
   */
  public void setMaximumIterations(int maximumIterations) {
    if (maximumIterations < 1) {
      throw new IllegalArgumentException("Maximum iterations must be at least one");
    }
    this.maximumIterations = maximumIterations;
  }

  /**
   * Set outer pressure-rate convergence tolerances.
   *
   * @param rateToleranceKgS maximum target/applied mass-rate residual in kg/s
   * @param pressureToleranceBar maximum well-node pressure change in bar
   */
  public void setTolerances(double rateToleranceKgS, double pressureToleranceBar) {
    if (!(rateToleranceKgS > 0.0) || !(pressureToleranceBar > 0.0)) {
      throw new IllegalArgumentException("Coupling tolerances must be positive");
    }
    this.rateToleranceKgS = rateToleranceKgS;
    this.pressureToleranceBar = pressureToleranceBar;
  }

  /**
   * Set rate under-relaxation for the outer pressure-rate iteration.
   *
   * @param relaxationFactor value greater than zero and at most one
   */
  public void setRelaxationFactor(double relaxationFactor) {
    if (!(relaxationFactor > 0.0) || relaxationFactor > 1.0) {
      throw new IllegalArgumentException("Relaxation factor must be in (0, 1]");
    }
    this.relaxationFactor = relaxationFactor;
  }

  /**
   * Set the rate below which a well is reported as physically shut in.
   *
   * @param shutInThresholdKgS non-negative mass rate in kg/s
   */
  public void setShutInThresholdKgS(double shutInThresholdKgS) {
    if (shutInThresholdKgS < 0.0) {
      throw new IllegalArgumentException("Shut-in threshold cannot be negative");
    }
    this.shutInThresholdKgS = shutInThresholdKgS;
  }

  /**
   * Get the latest coupling report.
   *
   * @return last report, or null before execution
   */
  public CouplingResult getLastResult() {
    return lastResult;
  }

  /**
   * Run the complete well/network pressure-rate coupling with a fresh calculation identifier.
   *
   * @return convergence and engineering evidence
   */
  public CouplingResult run() {
    return run(UUID.randomUUID());
  }

  /**
   * Run the complete well/network pressure-rate coupling.
   *
   * @param id calculation identifier propagated to the live wells and hydraulic network
   * @return convergence and engineering evidence
   */
  public CouplingResult run(UUID id) {
    validateConfiguration();
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    Map<String, Double> appliedRates = new LinkedHashMap<String, Double>();
    Map<String, Double> previousPressures = new LinkedHashMap<String, Double>();
    for (WellBinding binding : bindings.values()) {
      LoopedPipeNetwork.NetworkNode node = network.getNode(binding.nodeId);
      appliedRates.put(binding.nodeId, Math.abs(node.getDemand()));
      previousPressures.put(binding.nodeId, node.getPressure() / 1.0e5);
    }

    boolean coupledConverged = false;
    double maximumRateResidual = Double.POSITIVE_INFINITY;
    double maximumPressureResidual = Double.POSITIVE_INFINITY;
    List<WellResult> wellResults = Collections.emptyList();
    int completedIterations = 0;

    for (int iteration = 1; iteration <= maximumIterations; iteration++) {
      for (WellBinding binding : bindings.values()) {
        double pressureBara = network.getNodePressure(binding.nodeId);
        Evaluation evaluation = evaluate(binding, pressureBara, id);
        double oldRate = appliedRates.get(binding.nodeId);
        double appliedRate = oldRate + relaxationFactor * (evaluation.targetRateKgS - oldRate);
        if (evaluation.status == WellStatus.EVALUATION_FAILED || !Double.isFinite(appliedRate)) {
          appliedRate = 0.0;
        }
        appliedRate = Math.max(0.0, appliedRate);
        appliedRates.put(binding.nodeId, appliedRate);
        applyRate(network, binding, appliedRate);
        if (binding.service == Service.PRODUCTION && evaluation.productionStream != null
            && evaluation.productionStream.getFluid() != null) {
          network.setNodeFluid(binding.nodeId, evaluation.productionStream.getFluid());
        }
      }

      network.run(id);
      completedIterations = iteration;
      maximumPressureResidual = 0.0;
      maximumRateResidual = 0.0;
      boolean wellsAccepted = network.isConverged();
      wellResults = new ArrayList<WellResult>();

      for (WellBinding binding : bindings.values()) {
        double pressureBara = network.getNodePressure(binding.nodeId);
        double pressureResidual = Math.abs(pressureBara - previousPressures.get(binding.nodeId));
        maximumPressureResidual = Math.max(maximumPressureResidual, pressureResidual);
        previousPressures.put(binding.nodeId, pressureBara);

        Evaluation evaluation = evaluate(binding, pressureBara, id);
        double appliedRate = appliedRates.get(binding.nodeId);
        double rateResidual = Math.abs(evaluation.targetRateKgS - appliedRate);
        maximumRateResidual = Math.max(maximumRateResidual, rateResidual);
        if (!evaluation.innerConverged || isLimitFailure(evaluation.status)) {
          wellsAccepted = false;
        }
        wellResults.add(new WellResult(binding.nodeId, binding.service, evaluation.status, pressureBara,
            evaluation.targetRateKgS, appliedRate, rateResidual, evaluation.innerConverged,
            evaluation.innerPressureResidualBar, evaluation.innerIterations, evaluation.message));
      }

      coupledConverged = iteration > 1 && wellsAccepted && maximumRateResidual <= rateToleranceKgS
          && maximumPressureResidual <= pressureToleranceBar;
      if (coupledConverged) {
        break;
      }
    }

    String message = coupledConverged ? "Live well and canonical network pressure-rate coupling converged"
        : "Live well/network coupling reached its iteration or physical-limit boundary";
    lastResult = new CouplingResult(coupledConverged, completedIterations, maximumRateResidual, maximumPressureResidual,
        network.getMaxResidual(), network.getMassBalanceError(), wellResults, message);
    return lastResult;
  }

  /**
   * Register and validate a runtime binding.
   *
   * @param nodeId canonical node identity
   * @param service production or injection service
   * @param wellSystem WellSystem binding, or null
   * @param wellFlow WellFlow binding, or null
   * @return this coupler
   */
  private FieldWellNetworkCoupler putBinding(String nodeId, Service service, WellSystem wellSystem, WellFlow wellFlow) {
    FieldNode node = topology.getNode(nodeId);
    if (node == null) {
      throw new IllegalArgumentException("Unknown field node '" + nodeId + "'");
    }
    NodeRole expectedRole = service == Service.PRODUCTION ? NodeRole.PRODUCTION_WELL : NodeRole.INJECTION_WELL;
    if (node.getRole() != expectedRole || node.getService() != service) {
      throw new IllegalArgumentException(
          "Well binding for node '" + nodeId + "' requires role " + expectedRole + " and service " + service);
    }
    if (bindings.containsKey(nodeId)) {
      throw new IllegalArgumentException("Well node '" + nodeId + "' is already bound");
    }
    ProcessEquipmentInterface equipment = wellSystem != null ? wellSystem : wellFlow;
    topology.bindEquipment(nodeId, equipment);
    bindings.put(nodeId, new WellBinding(nodeId, service, wellSystem, wellFlow));
    return this;
  }

  /** Validate topology, solver, bindings, and initial well-node pressures. */
  private void validateConfiguration() {
    topology.validateForExecution();
    if (bindings.isEmpty()) {
      throw new IllegalStateException("At least one live well must be bound before coupling");
    }
    LoopedPipeNetwork network = topology.getHydraulicNetwork();
    if (network.getSolverType() != LoopedPipeNetwork.SolverType.NEWTON_RAPHSON) {
      throw new IllegalStateException("Live well pressure-rate coupling requires NEWTON_RAPHSON");
    }
    for (WellBinding binding : bindings.values()) {
      LoopedPipeNetwork.NetworkNode node = network.getNode(binding.nodeId);
      if (node == null || node.isPressureFixed()) {
        throw new IllegalStateException("Live well node '" + binding.nodeId + "' must have free pressure");
      }
      if (!(node.getPressure() > 0.0)) {
        throw new IllegalStateException("Live well node '" + binding.nodeId + "' requires a positive initial pressure");
      }
    }
  }

  /**
   * Apply a well rate to the canonical node mass balance.
   *
   * @param network canonical hydraulic graph
   * @param binding well binding
   * @param rateKgS non-negative well mass rate in kg/s
   */
  private void applyRate(LoopedPipeNetwork network, WellBinding binding, double rateKgS) {
    network.getNode(binding.nodeId).setDemand(binding.service == Service.PRODUCTION ? -rateKgS : rateKgS);
  }

  /**
   * Evaluate one live well at the current network-node pressure.
   *
   * @param binding well binding
   * @param pressureBara network well-node pressure in bara
   * @param id calculation identifier
   * @return evaluated pressure-rate contract
   */
  private Evaluation evaluate(WellBinding binding, double pressureBara, UUID id) {
    try {
      if (binding.wellSystem != null) {
        return evaluateWellSystem(binding.wellSystem, pressureBara, id);
      }
      return evaluateWellFlow(binding.wellFlow, binding.service, pressureBara, id);
    } catch (RuntimeException ex) {
      return new Evaluation(0.0, false, Double.NaN, 0, WellStatus.EVALUATION_FAILED,
          ex.getClass().getSimpleName() + ": " + ex.getMessage(), null);
    }
  }

  /**
   * Evaluate a production WellSystem at a solved wellhead pressure.
   *
   * @param well integrated production well
   * @param pressureBara solved wellhead pressure in bara
   * @param id calculation identifier
   * @return evaluated production boundary
   */
  private Evaluation evaluateWellSystem(WellSystem well, double pressureBara, UUID id) {
    well.setWellheadPressure(pressureBara, "bara");
    well.run(id);
    StreamInterface stream = well.getOutletStream();
    double rateKgS = positiveFiniteRate(stream.getFlowRate("kg/sec"));
    boolean shutIn = rateKgS <= shutInThresholdKgS;
    if (shutIn) {
      rateKgS = 0.0;
    }
    boolean innerConverged = well.isOperatingPointConverged() || shutIn;
    WellStatus status = shutIn ? WellStatus.SHUT_IN
        : innerConverged ? WellStatus.FLOWING : WellStatus.INNER_WELL_NOT_CONVERGED;
    String message = shutIn ? "No positive IPR/VLP operating rate at the solved wellhead pressure"
        : innerConverged ? "WellSystem IPR/VLP operating point converged"
            : "WellSystem IPR/VLP pressure residual exceeds its inner tolerance";
    return new Evaluation(rateKgS, innerConverged, well.getOperatingPointResidual("bar"),
        well.getOperatingPointIterations(), status, message, stream);
  }

  /**
   * Evaluate production or injection WellFlow equipment at a solved bottom-hole pressure.
   *
   * @param well inflow or injectivity equipment
   * @param service production or injection service
   * @param pressureBara solved bottom-hole pressure in bara
   * @param id calculation identifier
   * @return evaluated production or injection boundary
   */
  private Evaluation evaluateWellFlow(WellFlow well, Service service, double pressureBara, UUID id) {
    if (service == Service.INJECTION) {
      String incompatibility = getInjectionFluidIncompatibility(well);
      if (incompatibility != null) {
        return new Evaluation(0.0, false, 0.0, 0, WellStatus.FLUID_INCOMPATIBLE, incompatibility, null);
      }
    }
    well.setOutletPressure(pressureBara, "bara");
    if (service == Service.PRODUCTION) {
      well.solveFlowFromOutletPressure(true);
    }
    well.run(id);
    StreamInterface stream = well.getOutletStream();
    double rateKgS = positiveFiniteRate(stream.getFlowRate("kg/sec"));
    boolean shutIn = rateKgS <= shutInThresholdKgS;
    if (shutIn) {
      rateKgS = 0.0;
    }
    WellStatus status = shutIn ? WellStatus.SHUT_IN : WellStatus.FLOWING;
    String message = shutIn ? "No positive WellFlow rate at the solved bottom-hole pressure"
        : "WellFlow pressure-rate contract evaluated";

    if (service == Service.PRODUCTION && well.isWellConstraintsEnabled()
        && (well.getDrawdown() > well.getMaxDrawdown() + pressureToleranceBar
            || well.getBottomHolePressure() + pressureToleranceBar < well.getMinBottomHolePressure())) {
      status = WellStatus.BHP_LIMIT_EXCEEDED;
      message = "Production well exceeds its configured drawdown or minimum-BHP limit";
    }
    if (service == Service.INJECTION && hasFractureRisk(well)) {
      status = WellStatus.FRACTURE_LIMIT_EXCEEDED;
      message = "Injection bottom-hole pressure exceeds at least one zone fracture-pressure limit";
    }
    StreamInterface productionStream = service == Service.PRODUCTION ? stream : null;
    return new Evaluation(rateKgS, true, 0.0, 1, status, message, productionStream);
  }

  /**
   * Check injection-zone fracture flags.
   *
   * @param well injection well
   * @return true when any zone reports fracture risk
   */
  private boolean hasFractureRisk(WellFlow well) {
    boolean[] risks = well.getZoneFractureRisk();
    for (boolean risk : risks) {
      if (risk) {
        return true;
      }
    }
    return false;
  }

  /**
   * Compare the injection-well inlet fluid with the canonical network fluid at its bound node.
   *
   * <p>
   * The live injectivity calculation and network hydraulics must use the same thermodynamic model, mixing rule and
   * component identity set. Component amounts may differ because injection composition can be updated operationally,
   * but incompatible component slates or property models fail closed before rate coupling.
   * </p>
   *
   * @param well injection WellFlow
   * @return null when compatible, otherwise a diagnostic
   */
  private String getInjectionFluidIncompatibility(WellFlow well) {
    if (well.getInletStream() == null || well.getInletStream().getFluid() == null) {
      return "Injection WellFlow requires an inlet stream carrying the injected fluid";
    }
    SystemInterface wellFluid = well.getInletStream().getFluid();
    SystemInterface networkFluid = topology.getHydraulicNetwork().getFluidTemplate();
    if (networkFluid == null) {
      return "Canonical network requires a fluid template before injection coupling";
    }
    if (!sameText(wellFluid.getModelName(), networkFluid.getModelName())) {
      return "Injection well and network thermodynamic model names differ";
    }
    if (!sameText(wellFluid.getMixingRuleName(), networkFluid.getMixingRuleName())) {
      return "Injection well and network mixing rules differ";
    }
    Set<String> wellComponents = normalizedComponents(wellFluid);
    Set<String> networkComponents = normalizedComponents(networkFluid);
    if (!wellComponents.equals(networkComponents)) {
      return "Injection well and network component identity sets differ";
    }
    return null;
  }

  /**
   * Normalize component names for order-independent fluid compatibility checks.
   *
   * @param fluid thermodynamic fluid
   * @return sorted lower-case component names
   */
  private Set<String> normalizedComponents(SystemInterface fluid) {
    Set<String> names = new TreeSet<String>();
    for (String name : fluid.getComponentNames()) {
      names.add(name.toLowerCase(java.util.Locale.ROOT));
    }
    return names;
  }

  /**
   * Compare nullable model descriptors.
   *
   * @param first first descriptor
   * @param second second descriptor
   * @return true when both are null or equal ignoring case
   */
  private boolean sameText(String first, String second) {
    return first == null ? second == null : second != null && first.equalsIgnoreCase(second);
  }

  /**
   * Normalize a physical mass rate.
   *
   * @param rateKgS reported mass rate in kg/s
   * @return finite non-negative mass rate
   */
  private double positiveFiniteRate(double rateKgS) {
    if (!Double.isFinite(rateKgS)) {
      throw new IllegalStateException("Well calculation returned a non-finite mass rate");
    }
    return Math.max(0.0, rateKgS);
  }

  /**
   * Check whether a well status represents an active physical-limit failure.
   *
   * @param status operating status
   * @return true for non-convergence or limit failures
   */
  private boolean isLimitFailure(WellStatus status) {
    return status == WellStatus.INNER_WELL_NOT_CONVERGED || status == WellStatus.BHP_LIMIT_EXCEEDED
        || status == WellStatus.FRACTURE_LIMIT_EXCEEDED || status == WellStatus.FLUID_INCOMPATIBLE
        || status == WellStatus.EVALUATION_FAILED;
  }
}
